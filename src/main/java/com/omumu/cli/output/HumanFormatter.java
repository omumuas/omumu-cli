package com.omumu.cli.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class HumanFormatter implements OutputFormatter {

    private final ObjectMapper mapper;

    public HumanFormatter() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void printResult(JsonNode data) {
        // For complex JSON, pretty-print it
        try {
            System.out.println(mapper.writeValueAsString(data));
        } catch (Exception e) {
            System.out.println(data.toPrettyString());
        }
    }

    @Override
    public void printError(String message) {
        System.err.println("Error: " + message);
    }

    @Override
    public void printMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void printTable(String[] headers, String[][] rows) {
        if (rows.length == 0) {
            System.out.println("(no results)");
            return;
        }

        // Calculate column widths
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < headers.length && i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i] != null ? row[i].length() : 0);
            }
        }

        // Cap column widths at 60 chars
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.min(widths[i], 60);
        }

        String separator = buildSeparator(widths);
        String headerLine = buildRow(headers, widths);

        System.out.println(separator);
        System.out.println(headerLine);
        System.out.println(separator);
        for (String[] row : rows) {
            System.out.println(buildRow(row, widths));
        }
        System.out.println(separator);
        System.out.println(rows.length + " result" + (rows.length != 1 ? "s" : ""));
    }

    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append("+");
        }
        return sb.toString();
    }

    private String buildRow(String[] values, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String val = (i < values.length && values[i] != null) ? values[i] : "";
            if (val.length() > widths[i]) {
                val = val.substring(0, widths[i] - 2) + "..";
            }
            sb.append(" ").append(String.format("%-" + widths[i] + "s", val)).append(" |");
        }
        return sb.toString();
    }
}
