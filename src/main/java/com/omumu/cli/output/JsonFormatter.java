package com.omumu.cli.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonFormatter implements OutputFormatter {

    private final ObjectMapper mapper;

    public JsonFormatter() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void printResult(JsonNode data) {
        try {
            System.out.println(mapper.writeValueAsString(data));
        } catch (Exception e) {
            System.out.println(data.toString());
        }
    }

    @Override
    public void printError(String message) {
        try {
            var node = mapper.createObjectNode();
            node.put("error", message);
            System.err.println(mapper.writeValueAsString(node));
        } catch (Exception e) {
            System.err.println("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
        }
    }

    @Override
    public void printMessage(String message) {
        try {
            var node = mapper.createObjectNode();
            node.put("message", message);
            System.out.println(mapper.writeValueAsString(node));
        } catch (Exception e) {
            System.out.println("{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
        }
    }

    @Override
    public void printTable(String[] headers, String[][] rows) {
        var array = mapper.createArrayNode();
        for (String[] row : rows) {
            var obj = mapper.createObjectNode();
            for (int i = 0; i < headers.length && i < row.length; i++) {
                obj.put(headers[i], row[i]);
            }
            array.add(obj);
        }
        printResult(array);
    }
}
