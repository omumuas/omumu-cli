package com.omumu.cli.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

@Command(name = "login", mixinStandardHelpOptions = true, description = "Log in to your Omumu site (opens browser)")
public class LoginCommand implements Callable<Integer> {

    @ParentCommand
    OmumuCli parent;

    @Option(names = "--url", description = "Omumu site URL (e.g. https://mysite.myomumu.com)", required = true)
    String url;

    @Option(names = "--token", description = "Use API key or token instead of browser login")
    String apiKey;

    @Option(names = "--name", description = "Profile name", defaultValue = "default")
    String profileName;

    @Override
    public Integer call() throws Exception {
        url = url.replaceAll("/+$", "");

        // Verify the site is reachable
        System.out.print("Connecting to " + url + "... ");
        OmumuClient client = new OmumuClient(url, null);
        try {
            client.healthCheck();
            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("FAILED");
            System.err.println("Could not connect: " + e.getMessage());
            return 1;
        }

        // If API key provided, use it directly (for CI/automation)
        if (apiKey != null) {
            return loginWithApiKey();
        }

        // OAuth browser flow
        return loginWithBrowser();
    }

    private int loginWithApiKey() throws Exception {
        System.out.print("Validating API key... ");
        OmumuClient client = new OmumuClient(url, apiKey);
        try {
            client.listTools();
            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("FAILED");
            System.err.println("API key rejected: " + e.getMessage());
            return 1;
        }
        saveConfig(apiKey);
        return 0;
    }

    private int loginWithBrowser() throws Exception {
        // Step 1: Start local callback server first (to get the port)
        CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
        CompletableFuture<String> stateFuture = new CompletableFuture<>();
        int port = startCallbackServer(authCodeFuture, stateFuture);
        String redirectUri = "http://localhost:" + port + "/callback";

        // Step 2: Register this CLI as an OAuth client with the actual redirect URI
        System.out.println("Registering CLI client...");
        String clientId = registerOAuthClient(redirectUri);
        if (clientId == null) {
            System.err.println("Failed to register OAuth client.");
            return 1;
        }

        // Step 3: Generate PKCE challenge
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);
        String state = generateState();

        // Step 4: Open browser
        String authorizeUrl = url + "/mcp/oauth/authorize"
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc("mcp:read mcp:write")
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256";

        System.out.println();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            System.out.println("Opening browser to authorize...");
            Desktop.getDesktop().browse(URI.create(authorizeUrl));
        } else {
            System.out.println("Open this URL in your browser:");
            System.out.println(authorizeUrl);
        }
        System.out.println();
        System.out.println("Waiting for authorization...");

        // Step 5: Wait for callback
        String authCode;
        try {
            authCode = authCodeFuture.get(120, TimeUnit.SECONDS);
            String returnedState = stateFuture.get(1, TimeUnit.SECONDS);
            if (!state.equals(returnedState)) {
                System.err.println("State mismatch — possible CSRF attack. Aborting.");
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Timed out waiting for authorization. Try again.");
            return 1;
        }

        // Step 6: Exchange code for token
        System.out.print("Exchanging code for token... ");
        String accessToken = exchangeCodeForToken(clientId, authCode, redirectUri, codeVerifier);
        if (accessToken == null) {
            System.out.println("FAILED");
            return 1;
        }
        System.out.println("OK");

        saveConfig(accessToken);
        return 0;
    }

    private String registerOAuthClient(String redirectUri) {
        try {
            HttpClient http = HttpClient.newHttpClient();
            String body = "{\"client_name\":\"Omumu CLI\",\"redirect_uris\":[\"" + redirectUri + "\"]}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/mcp/oauth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = new ObjectMapper().readTree(response.body());
            return node.path("client_id").asText(null);
        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            return null;
        }
    }

    private String exchangeCodeForToken(String clientId, String code, String redirectUri, String codeVerifier) {
        try {
            HttpClient http = HttpClient.newHttpClient();
            String formBody = "grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&client_id=" + enc(clientId)
                    + "&code_verifier=" + enc(codeVerifier);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/mcp/oauth/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = new ObjectMapper().readTree(response.body());
            return node.path("access_token").asText(null);
        } catch (Exception e) {
            System.err.println("Token exchange error: " + e.getMessage());
            return null;
        }
    }

    private int startCallbackServer(CompletableFuture<String> codeFuture, CompletableFuture<String> stateFuture) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String code = null;
            String state = null;

            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if ("code".equals(kv[0])) code = kv[1];
                        if ("state".equals(kv[0])) state = kv[1];
                    }
                }
            }

            String html;
            if (code != null) {
                html = "<html><body><h1>Authorized!</h1><p>You can close this tab and return to the terminal.</p></body></html>";
                codeFuture.complete(code);
                if (state != null) stateFuture.complete(state);
            } else {
                String error = "unknown";
                if (query != null && query.contains("error=")) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if ("error_description".equals(kv[0]) && kv.length == 2) error = kv[1];
                    }
                }
                html = "<html><body><h1>Authorization failed</h1><p>" + error + "</p></body></html>";
                codeFuture.completeExceptionally(new RuntimeException(error));
            }

            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            // Shut down server after handling callback
            server.stop(1);
        });

        server.start();
        return port;
    }

    private void saveConfig(String token) throws IOException {
        ConfigManager configManager = new ConfigManager();
        OmumuConfig config = configManager.load();
        OmumuConfig.Profile profile = new OmumuConfig.Profile(url, token);
        if ("default".equals(profileName)) {
            config.setDefaultProfile(profile);
        } else {
            config.getSites().put(profileName, profile);
        }
        configManager.save(config);
        System.out.println("Logged in! Configuration saved to " + configManager.getConfigFile());
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static String generateState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
