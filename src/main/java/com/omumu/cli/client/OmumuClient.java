package com.omumu.cli.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class OmumuClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final AtomicInteger requestId;
    private boolean initialized;

    public OmumuClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
        this.requestId = new AtomicInteger(1);
        this.initialized = false;
    }

    public JsonNode healthCheck() throws IOException, InterruptedException {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/mcp/health"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL: " + baseUrl);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new IOException("Could not connect to " + baseUrl + " (connection refused)");
        } catch (java.net.http.HttpConnectTimeoutException e) {
            throw new IOException("Connection timed out: " + baseUrl);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.getClass().getSimpleName();
            throw new IOException("Could not reach " + baseUrl + ": " + msg);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Health check failed: HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    public JsonNode callTool(String toolName, Map<String, Object> arguments) throws IOException, InterruptedException {
        ensureInitialized();

        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", mapper.valueToTree(arguments));

        JsonNode response = sendJsonRpc("tools/call", params);

        if (response.has("error")) {
            String errorMsg = response.get("error").has("message")
                    ? response.get("error").get("message").asText()
                    : response.get("error").toString();
            throw new IOException("Tool call failed: " + errorMsg);
        }

        return response.get("result");
    }

    public JsonNode listTools() throws IOException, InterruptedException {
        ensureInitialized();
        JsonNode response = sendJsonRpc("tools/list", null);
        if (response.has("error")) {
            throw new IOException("tools/list failed: " + response.get("error"));
        }
        return response.get("result");
    }

    private void ensureInitialized() throws IOException, InterruptedException {
        if (initialized) return;

        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2025-03-26");
        ObjectNode capabilities = params.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "omumu-cli");
        clientInfo.put("version", "0.2.3");

        JsonNode response = sendJsonRpc("initialize", params);
        if (response.has("error")) {
            throw new IOException("Initialize failed: " + response.get("error"));
        }
        initialized = true;
    }

    private JsonNode sendJsonRpc(String method, JsonNode params) throws IOException, InterruptedException {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId.getAndIncrement());
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        } else {
            request.putObject("params");
        }

        String body = mapper.writeValueAsString(request);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> httpResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() == 401) {
            throw new IOException("Authentication failed. Check your API key with 'omumu login'.");
        }

        if (httpResponse.statusCode() != 200) {
            throw new IOException("HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
        }

        return mapper.readTree(httpResponse.body());
    }
}
