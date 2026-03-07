package com.omumu.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "status", description = "Check connection and available tools")
public class StatusCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    OmumuCli parent;

    @Override
    public Integer call() {
        OutputFormatter out = resolveFormatter(parent);
        try {
            OmumuClient client = resolveClient(parent);

            // Health check
            JsonNode health = client.healthCheck();
            if (!parent.isJson()) {
                System.out.println("Connected to Omumu MCP server");
                System.out.println("  Protocol: " + health.path("protocolVersion").asText("unknown"));
                System.out.println("  Status:   " + health.path("status").asText("unknown"));
            }

            // List available tools
            JsonNode tools = client.listTools();
            JsonNode toolList = tools.path("tools");

            if (parent.isJson()) {
                out.printResult(tools);
            } else {
                System.out.println("  Tools:    " + toolList.size() + " available");
                if (parent.isVerbose() && toolList.isArray()) {
                    for (JsonNode tool : toolList) {
                        System.out.println("    - " + tool.path("name").asText());
                    }
                }
            }
            return 0;

        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }
}
