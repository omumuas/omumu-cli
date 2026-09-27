package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The {@code omumu call} command line that runs a tool, as advertised by {@code omumu schema}:
 * the tool name followed by one {@code --arg} placeholder per required argument.
 */
public final class CallInvocation {

    private CallInvocation() {
    }

    public static String forTool(String toolName, JsonNode inputSchema) {
        final StringBuilder cli = new StringBuilder("omumu call ").append(toolName);
        if (inputSchema == null) {
            return cli.toString();
        }
        final JsonNode properties = inputSchema.path("properties");
        for (JsonNode required : inputSchema.path("required")) {
            final String name = required.asText();
            final String type = properties.path(name).path("type").asText("string");
            cli.append(" --arg ").append(name).append("=<").append(type).append('>');
        }
        return cli.toString();
    }
}
