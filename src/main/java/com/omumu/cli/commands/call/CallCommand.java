package com.omumu.cli.commands.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.commands.BaseCommand;
import com.omumu.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "call",
    description = "Call any Omumu tool by name. Run 'omumu schema' to list tools and their arguments.",
    mixinStandardHelpOptions = true
)
public class CallCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    OmumuCli parent;

    @Parameters(index = "0", description = "Tool name, e.g. omumu_offer_list (the omumu_ prefix may be left out)")
    String tool;

    @Option(names = "--arg", paramLabel = "KEY=VALUE",
            description = "A tool argument; repeat for more. Numbers, booleans, arrays and objects are typed by the tool's schema.")
    List<String> pairs = new ArrayList<>();

    @Option(names = "--input", paramLabel = "JSON",
            description = "All arguments as one JSON object, @file to read it from a file, or - for stdin; --arg values override its keys.")
    String inputJson;

    @Override
    public Integer call() {
        OutputFormatter out = resolveFormatter(parent);
        try {
            OmumuClient client = resolveClient(parent);
            String toolName = CallInvocation.toolName(tool);
            JsonNode schema = inputSchemaOf(client, toolName);
            Map<String, Object> args = ToolArguments.build(schema, readInput(inputJson), pairs);
            out.printResult(extractData(client.callTool(toolName, args)));
            return 0;
        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }

    /** Reads {@code --input}: inline JSON, {@code @path} for a file, or {@code -} for stdin (keeps secrets off argv). */
    private static String readInput(String input) throws java.io.IOException {
        if (input == null) {
            return null;
        }
        if (input.equals("-")) {
            return new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (input.startsWith("@")) {
            return java.nio.file.Files.readString(java.nio.file.Path.of(input.substring(1)));
        }
        return input;
    }

    private static JsonNode inputSchemaOf(OmumuClient client, String toolName) throws Exception {
        for (JsonNode t : client.listTools().path("tools")) {
            if (toolName.equals(t.path("name").asText())) {
                return t.path("inputSchema");
            }
        }
        throw new IllegalArgumentException("Unknown tool '" + toolName + "'. Run 'omumu schema' to list the tools this key can use.");
    }
}
