package com.omumu.cli.output;

import com.fasterxml.jackson.databind.JsonNode;

public interface OutputFormatter {
    void printResult(JsonNode data);
    void printError(String message);
    void printMessage(String message);
    void printTable(String[] headers, String[][] rows);
}
