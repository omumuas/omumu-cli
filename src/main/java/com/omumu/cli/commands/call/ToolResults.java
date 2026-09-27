package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.JsonNode;

/** Reads an MCP {@code tools/call} result. */
public final class ToolResults {

    private ToolResults() {
    }

    /** True when the tool reported a failure in its result ({@code isError}), not as a JSON-RPC error. */
    public static boolean isError(JsonNode result) {
        return result != null && result.path("isError").asBoolean(false);
    }
}
