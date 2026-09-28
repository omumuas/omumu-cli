package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aResultMarkedIsErrorIsAFailure() throws Exception {
        assertTrue(ToolResults.isError(MAPPER.readTree(
                "{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"Operation failed: nope\"}]}")));
    }

    @Test
    void anOrdinaryResultIsNot() throws Exception {
        assertFalse(ToolResults.isError(MAPPER.readTree(
                "{\"content\":[{\"type\":\"text\",\"text\":\"Operation completed successfully. Data: []\"}]}")));
        assertFalse(ToolResults.isError(null));
    }
}
