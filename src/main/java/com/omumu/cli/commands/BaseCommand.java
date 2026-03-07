package com.omumu.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.config.ConfigManager;
import com.omumu.cli.config.OmumuConfig;
import com.omumu.cli.output.HumanFormatter;
import com.omumu.cli.output.JsonFormatter;
import com.omumu.cli.output.OutputFormatter;

public abstract class BaseCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected OmumuClient resolveClient(OmumuCli parent) {
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

    /**
     * Extract the actual data from an MCP tool response.
     *
     * MCP returns: {content: [{type: "text", text: "Operation completed successfully. Data: [...]"}]}
     * This method extracts and parses the JSON data from that wrapper.
     */
    protected JsonNode extractData(JsonNode mcpResult) {
        JsonNode content = mcpResult.path("content");
        if (!content.isArray() || content.isEmpty()) {
            return mcpResult;
        }

        JsonNode first = content.get(0);
        if (!first.has("text")) {
            return mcpResult;
        }

        String text = first.get("text").asText();

        // Strip "Operation completed successfully. Data: " prefix
        int dataIndex = text.indexOf("Data: ");
        if (dataIndex >= 0) {
            String jsonPart = text.substring(dataIndex + 6).trim();
            try {
                return MAPPER.readTree(jsonPart);
            } catch (Exception e) {
                // Not valid JSON after prefix, return as-is
            }
        }

        // Try parsing the whole text as JSON
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            // Not JSON at all, return the raw MCP result
            return mcpResult;
        }
    }
}
