package com.omumu.cli.config;

// ABOUTME: Drives the CLI-first signup flow — OAuth against omumu.com, $5 Stripe gate,
// ABOUTME: site provisioning, and hand-off to the existing post-site CLI commands.

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omumu.cli.OmumuCli;
import com.sun.net.httpserver.HttpServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.Console;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Command(
        name = "signup",
        mixinStandardHelpOptions = true,
        description = "Create a new Omumu site (CLI onboarding, includes a $5 anti-bot gate)"
)
public class SignupCommand implements Callable<Integer> {

    /**
     * Static client_id the CLI ships with. Seeded server-side so the dynamic
     * client-registration path doesn't grant the site:provision scope.
     */
    private static final String CLI_CLIENT_ID = "omumu-cli";
    private static final String DEFAULT_HOST = "https://omumu.com";

    @ParentCommand
    OmumuCli parent;

    @Option(
            names = "--host",
            description = "Omumu platform host (default: ${DEFAULT-VALUE}) — override for local dev against http://localhost:8080",
            defaultValue = DEFAULT_HOST
    )
    String host;

    @Option(names = "--subdomain", description = "Skip the prompt and use this subdomain directly")
    String subdomainArg;

    @Option(names = "--name", description = "Profile name to save", defaultValue = "default")
    String profileName;

    @Override
    public Integer call() throws Exception {
        host = host.replaceAll("/+$", "");

        System.out.println("Creating a new Omumu site via the CLI.");
        System.out.println("Host: " + host);
        System.out.println();

        String accessToken = runOAuth();
        if (accessToken == null) {
            return 1;
        }

        String subdomain = subdomainArg != null ? subdomainArg : promptSubdomain();
        if (subdomain == null || subdomain.isBlank()) {
            System.err.println("Subdomain required.");
            return 1;
        }

        Checkout checkout = startCheckout(accessToken, subdomain);
        if (checkout == null) return 1;

        System.out.println();
        System.out.println("Opening Stripe Checkout in your browser ($5 one-time — explained on the page).");
        if (!openBrowser(checkout.url)) {
            System.out.println("If the browser didn't open, visit:");
            System.out.println("  " + checkout.url);
        }
        System.out.println();
        System.out.println("Waiting for payment to complete...");

        String siteUrl = pollUntilProvisioned(accessToken, checkout.sessionId);
        if (siteUrl == null) return 1;

        System.out.println();
        System.out.println("Your site is live: " + siteUrl);
        System.out.println();

        // The signup token carries only site:provision against the platform host — it cannot manage
        // the new site. Log in against the new site itself to save a working, correctly-scoped profile.
        if (loginToNewSite(siteUrl) != 0) {
            System.out.println();
            System.out.println("Couldn't connect the CLI to your new site automatically. Finish with:");
            System.out.println("  omumu login --url " + siteUrl);
            return 1;
        }

        System.out.println("Try it: omumu status");
        return 0;
    }

    // ─── OAuth ──────────────────────────────────────────────────────────

    private String runOAuth() throws Exception {
        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        CompletableFuture<String> stateFuture = new CompletableFuture<>();
        int port = startCallbackServer(codeFuture, stateFuture);
        String redirectUri = "http://127.0.0.1:" + port + "/callback";

        String codeVerifier = pkceVerifier();
        String codeChallenge = pkceChallenge(codeVerifier);
        String state = newState();

        String authorizeUrl = host + "/mcp/oauth/authorize"
                + "?client_id=" + enc(CLI_CLIENT_ID)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc("site:provision")
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256";

        System.out.println("Opening browser to authorize the CLI...");
        if (!openBrowser(authorizeUrl)) {
            System.out.println("If the browser didn't open, visit:");
            System.out.println("  " + authorizeUrl);
        }

        String code;
        try {
            code = codeFuture.get(120, TimeUnit.SECONDS);
            String returnedState = stateFuture.get(1, TimeUnit.SECONDS);
            if (!state.equals(returnedState)) {
                System.err.println("State mismatch — possible CSRF. Aborting.");
                return null;
            }
        } catch (Exception e) {
            System.err.println("Timed out waiting for authorization.");
            return null;
        }

        return exchangeCodeForToken(code, redirectUri, codeVerifier);
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
                html = "<html><body><h1>Authorization failed</h1></body></html>";
                RuntimeException failure = new RuntimeException("no code");
                codeFuture.completeExceptionally(failure);
                stateFuture.completeExceptionally(failure);
            }
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            server.stop(1);
        });
        server.start();
        return port;
    }

    private String exchangeCodeForToken(String code, String redirectUri, String codeVerifier) {
        try {
            String formBody = "grant_type=authorization_code"
                    + "&code=" + enc(code)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&client_id=" + enc(CLI_CLIENT_ID)
                    + "&code_verifier=" + enc(codeVerifier);
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(host + "/mcp/oauth/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(formBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            JsonNode node = new ObjectMapper().readTree(response.body());
            String token = node.path("access_token").asText(null);
            if (token == null) {
                System.err.println("Token exchange failed: " + response.body());
            }
            return token;
        } catch (Exception e) {
            System.err.println("Token exchange error: " + e.getMessage());
            return null;
        }
    }

    // ─── Subdomain prompt ────────────────────────────────────────────────

    private String promptSubdomain() {
        Console console = System.console();
        System.out.println("Choose a subdomain for your site (letters, digits, hyphens; 3-63 chars):");
        System.out.print("  subdomain: ");
        String line;
        if (console != null) {
            line = console.readLine();
        } else {
            java.util.Scanner scanner = new java.util.Scanner(System.in);
            line = scanner.hasNextLine() ? scanner.nextLine() : null;
        }
        return line == null ? null : line.trim().toLowerCase();
    }

    // ─── Checkout + provision calls ─────────────────────────────────────

    private record Checkout(String sessionId, String url) {}

    private Checkout startCheckout(String accessToken, String subdomain) {
        try {
            String body = new ObjectMapper().createObjectNode().put("subdomain", subdomain).toString();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(host + "/api/cli/site/checkout"))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) {
                System.err.println("Checkout request failed: " + response.statusCode() + " " + response.body());
                return null;
            }
            JsonNode node = new ObjectMapper().readTree(response.body());
            return new Checkout(
                    node.path("sessionId").asText(null),
                    node.path("checkoutUrl").asText(null)
            );
        } catch (Exception e) {
            System.err.println("Checkout call failed: " + e.getMessage());
            return null;
        }
    }

    /** Polls the provision endpoint until it returns 200 or times out after ~10 minutes. */
    private String pollUntilProvisioned(String accessToken, String sessionId) {
        String body = new ObjectMapper().createObjectNode().put("sessionId", sessionId).toString();
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + "/api/cli/site/provision"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode node = new ObjectMapper().readTree(response.body());
                    return node.path("siteUrl").asText(null);
                }
                if (response.statusCode() == 409) {
                    JsonNode node = new ObjectMapper().readTree(response.body());
                    String error = node.path("error").asText("");
                    // 409 on "not paid / not complete" is normal while we wait
                    if (!"session.error.notPaid".equals(error) && !"session.error.notComplete".equals(error)) {
                        System.err.println("Provisioning failed: " + error);
                        return null;
                    }
                } else if (response.statusCode() >= 500) {
                    // Transient server error — keep polling until success or timeout.
                    System.err.println("Provision call transient error " + response.statusCode() + ", retrying...");
                } else {
                    System.err.println("Provision call failed: " + response.statusCode() + " " + response.body());
                    return null;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                // Transient network/parse error — keep polling until success or timeout.
                System.err.println("Provision call error: " + e.getMessage() + ", retrying...");
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        System.err.println("Timed out waiting for payment (10 min).");
        return null;
    }

    /**
     * Authorizes the CLI against the freshly provisioned site to mint a site-scoped token and save it
     * as the profile. The signup OAuth ran against the platform host with only the site:provision
     * scope, so it cannot manage the new site — this is a fresh browser login against the new site,
     * which dynamically registers a client and requests the full set of content scopes. Returns the
     * login command's exit code (0 on success).
     */
    private int loginToNewSite(String siteUrl) {
        System.out.println("Connecting the CLI to your new site (this opens the browser once more)...");
        LoginCommand login = new LoginCommand();
        login.url = siteUrl;
        login.profileName = profileName;
        try {
            return login.call();
        } catch (Exception e) {
            System.err.println("Login failed: " + e.getMessage());
            return 1;
        }
    }

    // ─── PKCE / helpers ─────────────────────────────────────────────────

    private static String pkceVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String pkceChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static String newState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else if (os.contains("linux")) {
                new ProcessBuilder("xdg-open", url).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
