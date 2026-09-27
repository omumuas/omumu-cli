package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolArgumentsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode schema() throws Exception {
        return MAPPER.readTree("""
                {"type":"object","properties":{
                  "title":{"type":"string"},
                  "totalAmountCents":{"type":"integer"},
                  "vatRate":{"type":"number"},
                  "published":{"type":"boolean"},
                  "paymentProviders":{"type":"array"},
                  "settings":{"type":"object"}
                }}""");
    }

    @Test
    void typesEachArgByTheSchema() throws Exception {
        Map<String, Object> args = ToolArguments.build(schema(), null, List.of(
                "title=123 ways",
                "totalAmountCents=4900",
                "vatRate=0.25",
                "published=true",
                "paymentProviders=[\"STRIPE\",\"INVOICE_REQUEST\"]",
                "settings={\"a\":1}"));

        assertEquals("123 ways", args.get("title"));
        assertEquals(4900L, ((Number) args.get("totalAmountCents")).longValue());
        assertEquals(0.25, ((Number) args.get("vatRate")).doubleValue());
        assertEquals(Boolean.TRUE, args.get("published"));
        assertEquals(List.of("STRIPE", "INVOICE_REQUEST"), args.get("paymentProviders"));
        assertEquals(Map.of("a", 1), args.get("settings"));
    }

    @Test
    void keepsEverythingAfterTheFirstEqualsSign() throws Exception {
        Map<String, Object> args = ToolArguments.build(schema(), null, List.of("title=a=b"));
        assertEquals("a=b", args.get("title"));
    }

    @Test
    void argsOverrideInputJson() throws Exception {
        Map<String, Object> args = ToolArguments.build(schema(),
                "{\"title\":\"from input\",\"totalAmountCents\":100}", List.of("title=from arg"));
        assertEquals("from arg", args.get("title"));
        assertEquals(100, ((Number) args.get("totalAmountCents")).intValue());
    }

    @Test
    void refusesAPairWithoutEquals() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), null, List.of("title")));
        assertTrue(e.getMessage().contains("key=value"), e.getMessage());
    }

    @Test
    void refusesAnUnknownArgumentNamingTheKnownOnes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), null, List.of("titel=x")));
        assertTrue(e.getMessage().contains("titel"), e.getMessage());
        assertTrue(e.getMessage().contains("title"), e.getMessage());
    }

    @Test
    void refusesAValueThatDoesNotMatchItsType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), null, List.of("totalAmountCents=lots")));
        assertTrue(e.getMessage().contains("totalAmountCents"), e.getMessage());
    }

    @Test
    void refusesInputThatIsNotAJsonObject() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), "[1,2]", List.of()));
    }
}
