package com.omumu.cli.commands.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the arguments of an MCP tool call from {@code --input} JSON and {@code --arg key=value}
 * pairs, typing each value by the tool's input schema.
 */
public final class ToolArguments {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolArguments() {
    }

    /**
     * @param inputSchema the tool's JSON Schema ({@code inputSchema} from {@code tools/list})
     * @param inputJson   a JSON object with arguments, or null
     * @param pairs       {@code key=value} pairs; they override keys from {@code inputJson}
     */
    public static Map<String, Object> build(JsonNode inputSchema, String inputJson, List<String> pairs) {
        final Map<String, Object> args = new LinkedHashMap<>(parseInput(inputJson));
        final JsonNode properties = inputSchema == null ? null : inputSchema.path("properties");

        for (String pair : pairs) {
            final int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Expected key=value, got '" + pair + "'");
            }
            final String key = pair.substring(0, eq);
            final String value = pair.substring(eq + 1);

            if (properties == null || !properties.has(key)) {
                throw new IllegalArgumentException("Unknown argument '" + key + "'. Known: " + knownNames(properties));
            }
            args.put(key, typed(key, value, properties.get(key).path("type").asText("string")));
        }
        return args;
    }

    private static Map<String, Object> parseInput(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            return Map.of();
        }
        try {
            final JsonNode node = MAPPER.readTree(inputJson);
            if (!node.isObject()) {
                throw new IllegalArgumentException("--input must be a JSON object");
            }
            return MAPPER.convertValue(node, new TypeReference<>() {
            });
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("--input is not valid JSON: " + e.getMessage());
        }
    }

    private static Object typed(String key, String value, String type) {
        try {
            return switch (type) {
                case "integer" -> Long.parseLong(value.trim());
                case "number" -> Double.parseDouble(value.trim());
                case "boolean" -> parseBoolean(value);
                case "array" -> jsonOf(value, JsonNode::isArray, List.class);
                case "object" -> jsonOf(value, JsonNode::isObject, Map.class);
                default -> value;
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Argument '" + key + "' must be " + article(type) + " " + type
                    + ", got '" + value + "'");
        }
    }

    private static Boolean parseBoolean(String value) {
        return switch (value.trim().toLowerCase()) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException(value);
        };
    }

    private static Object jsonOf(String value, java.util.function.Predicate<JsonNode> shape, Class<?> as) {
        try {
            final JsonNode node = MAPPER.readTree(value);
            if (!shape.test(node)) {
                throw new IllegalArgumentException(value);
            }
            return MAPPER.convertValue(node, as);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(value, e);
        }
    }

    private static String article(String type) {
        return type.startsWith("a") || type.startsWith("i") || type.startsWith("o") ? "an" : "a";
    }

    private static String knownNames(JsonNode properties) {
        final List<String> names = new ArrayList<>();
        if (properties != null) {
            properties.fieldNames().forEachRemaining(names::add);
        }
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }
}
