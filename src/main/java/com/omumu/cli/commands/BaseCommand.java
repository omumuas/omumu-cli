package com.omumu.cli.commands;

import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.config.ConfigManager;
import com.omumu.cli.config.OmumuConfig;
import com.omumu.cli.output.HumanFormatter;
import com.omumu.cli.output.JsonFormatter;
import com.omumu.cli.output.OutputFormatter;

public abstract class BaseCommand {

    protected OmumuClient resolveClient(OmumuCli parent) {
        // CLI flags override config file, env vars are handled by picocli defaults
        String url = parent.getSiteUrl();
        String key = parent.getApiKey();

        if (url == null || key == null) {
            ConfigManager configManager = new ConfigManager();
            OmumuConfig config = configManager.load();
            String site = System.getenv("OMUMU_SITE");
            OmumuConfig.Profile profile = config.resolveProfile(site);

            if (profile == null) {
                throw new RuntimeException("Not configured. Run 'omumu login' first.");
            }
            if (url == null) url = profile.getUrl();
            if (key == null) key = profile.getApiKey();
        }

        if (url == null || key == null) {
            throw new RuntimeException("Not configured. Run 'omumu login' first.");
        }

        return new OmumuClient(url, key);
    }

    protected OutputFormatter resolveFormatter(OmumuCli parent) {
        if (parent.isJson()) {
            return new JsonFormatter();
        }
        return new HumanFormatter();
    }
}
