package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallInvocationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listsRequiredArgumentsWithTheirTypes() throws Exception {
        var schema = MAPPER.readTree("""
                {"type":"object","properties":{
                  "title":{"type":"string"},
                  "totalAmountCents":{"type":"integer"},
                  "tagline":{"type":"string"}
                },"required":["title","totalAmountCents"]}""");

        assertEquals("omumu call omumu_offer_create --arg title=<string> --arg totalAmountCents=<integer>",
                CallInvocation.forTool("omumu_offer_create", schema));
    }

    @Test
    void aToolWithoutRequiredArgumentsIsJustTheCall() throws Exception {
        var schema = MAPPER.readTree("{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\"}}}");

        assertEquals("omumu call omumu_offer_list", CallInvocation.forTool("omumu_offer_list", schema));
    }

    @Test
    void aToolWithoutSchemaIsJustTheCall() {
        assertEquals("omumu call omumu_status", CallInvocation.forTool("omumu_status", null));
    }
}
