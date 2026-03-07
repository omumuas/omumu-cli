package com.omumu.cli.config;

import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.Console;
import java.util.concurrent.Callable;

@Command(name = "login", description = "Configure API key and site URL")
public class LoginCommand implements Callable<Integer> {

    @ParentCommand
    OmumuCli parent;

    @Option(names = "--url", description = "Omumu site URL (e.g. https://mysite.myomumu.com)")
    String url;

    @Option(names = "--api-key", description = "API key (omumu_xxx_SITEID)")
    String apiKey;

    @Option(names = "--name", description = "Profile name (default: 'default')", defaultValue = "default")
    String profileName;

    @Override
    public Integer call() throws Exception {
        Console console = System.console();

        if (url == null) {
            if (console != null) {
                url = console.readLine("Site URL: ");
            } else {
                System.err.println("Error: --url required in non-interactive mode");
                return 1;
            }
        }

        if (apiKey == null) {
            if (console != null) {
                char[] key = console.readPassword("API key: ");
                apiKey = new String(key);
            } else {
                System.err.println("Error: --api-key required in non-interactive mode");
                return 1;
            }
        }

        // Normalize URL
        url = url.replaceAll("/+$", "");

        // Validate connection
        System.out.print("Validating connection... ");
        OmumuClient client = new OmumuClient(url, apiKey);
        try {
            var health = client.healthCheck();
            if (health == null) {
                System.out.println("FAILED");
                System.err.println("Could not connect to " + url + "/mcp/health");
                return 1;
            }
            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("FAILED");
            System.err.println("Connection error: " + e.getMessage());
            return 1;
        }

        // Save config
        ConfigManager configManager = new ConfigManager();
        OmumuConfig config = configManager.load();

        OmumuConfig.Profile profile = new OmumuConfig.Profile(url, apiKey);
        if ("default".equals(profileName)) {
            config.setDefaultProfile(profile);
        } else {
            config.getSites().put(profileName, profile);
        }

        configManager.save(config);
        System.out.println("Configuration saved to " + configManager.getConfigFile());
        return 0;
    }
}
