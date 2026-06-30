package com.omumu.cli.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
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

    /**
     * Uploads a {@code .skill} bundle to {@code /mcp/skill/upload} as multipart/form-data.
     *
     * <p>The MCP JSON-RPC tool {@code omumu_skill_upload} also exists, but requires the bundle to
     * ride inside a tool-call argument as base64 — a path Claude Desktop and similar LLM clients
     * cannot reliably emit for non-trivial payloads. The CLI is exactly the kind of client the
     * multipart endpoint was built for: it can stream raw bytes without LLM transcription.
     */
    public JsonNode uploadSkillBundle(Path bundlePath, boolean publish) throws IOException, InterruptedException {
        final byte[] zipBytes = Files.readAllBytes(bundlePath);
        final String filename = bundlePath.getFileName() != null ? bundlePath.getFileName().toString() : "bundle.skill";
        final String boundary = "----omumu-cli-" + UUID.randomUUID();

        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"bundle\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/zip\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(zipBytes);
        body.write(("\r\n--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"publish\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(Boolean.toString(publish).getBytes(StandardCharsets.UTF_8));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/mcp/skill/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IOException("Authentication failed. Check your API key with 'omumu login'.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Don't second-guess the meaning of 403 — Cloudflare challenges, WAF blocks, and
            // application-level permission denials all use the same status code, and pretending
            // we know which one is which has historically misled debugging. Surface what the
            // server actually said: a JSON body's error_description if present, the cf-mitigated
            // header for Cloudflare challenges, otherwise a truncated raw body.
            String detail = response.body();
            try {
                JsonNode err = mapper.readTree(detail);
                if (err.has("error_description")) {
                    detail = err.get("error_description").asText();
                } else if (err.has("error")) {
                    detail = err.get("error").asText();
                }
            } catch (IOException ignore) {
                String cfMitigated = response.headers().firstValue("cf-mitigated").orElse(null);
                if (cfMitigated != null) {
                    detail = "Cloudflare " + cfMitigated + " — request was blocked before reaching Omumu";
                } else if (detail != null && detail.length() > 240) {
                    detail = detail.substring(0, 240) + "…";
                }
            }
            throw new IOException("Upload failed: HTTP " + response.statusCode() + " — " + detail);
        }

        return mapper.readTree(response.body());
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
        clientInfo.put("version", "0.3.0");

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
