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
                  "settings":{"type":"object"},
                  "limit":{"type":["integer","null"]}
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
        assertEquals(new java.math.BigDecimal("0.25"), args.get("vatRate"));
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

    @Test
    void refusesAnUnknownKeyInInputJsonToo() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), "{\"titel\":\"x\"}", List.of()));
        assertTrue(e.getMessage().contains("titel"), e.getMessage());
    }

    @Test
    void typesANullableUnionByItsNonNullType() throws Exception {
        Map<String, Object> args = ToolArguments.build(schema(), null, List.of("limit=42"));
        assertEquals(42L, ((Number) args.get("limit")).longValue());
    }

    @Test
    void doesNotEchoALongValueInFull() {
        String secret = "sk_live_" + "x".repeat(80);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), null, List.of("totalAmountCents=" + secret)));
        assertTrue(!e.getMessage().contains("xxxxx"), e.getMessage());
    }

    @Test
    void doesNotEchoBadInputJson() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArguments.build(schema(), "{\"title\": sk_live_secretvalue}", List.of()));
        assertTrue(!e.getMessage().contains("secretvalue"), e.getMessage());
    }

    @Test
    void keepsDecimalNumbersExact() throws Exception {
        Map<String, Object> args = ToolArguments.build(schema(), null, List.of("vatRate=0.1"));
        assertEquals(new java.math.BigDecimal("0.1"), args.get("vatRate"));
    }
}
