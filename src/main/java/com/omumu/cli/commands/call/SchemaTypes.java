package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.JsonNode;

/** Reads a JSON Schema property's type, taking the non-null member of a nullable union. */
final class SchemaTypes {

    private SchemaTypes() {
    }

    static String typeOf(JsonNode property) {
        final JsonNode type = property == null ? null : property.path("type");
        if (type != null && type.isArray()) {
            for (JsonNode member : type) {
                if (!"null".equals(member.asText())) {
                    return member.asText();
                }
            }
        }
        return type != null && type.isTextual() ? type.asText() : "string";
    }
}
