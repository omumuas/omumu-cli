package com.omumu.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omumu.cli.OmumuCli;
import com.omumu.cli.client.OmumuClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "schema", description = "Show all available commands and their parameters (for LLMs and automation)")
public class SchemaCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    OmumuCli parent;

    @Override
    public Integer call() {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        try {
            OmumuClient client = resolveClient(parent);
            JsonNode toolsResult = client.listTools();
            JsonNode tools = toolsResult.path("tools");

            if (!tools.isArray()) {
                System.err.println("Error: Could not fetch tool definitions");
                return 1;
            }

            // Build a clean schema document
            ObjectNode schema = mapper.createObjectNode();
            schema.put("name", "omumu");
            schema.put("version", "0.3.0");
            schema.put("description", "CLI for the Omumu customer education platform. Every command maps to an MCP tool call.");
            schema.put("usage", "omumu <group> <action> [options]");
            schema.put("global_options", "--json (machine output) | --verbose | --site-url <url> | --api-key <key>");

            ArrayNode commands = schema.putArray("commands");

            for (JsonNode tool : tools) {
                String name = tool.path("name").asText();
                if (!name.startsWith("omumu_")) continue;

                ObjectNode cmd = commands.addObject();
                cmd.put("tool", name);

                // Convert omumu_course_list -> "omumu course list"
                String cliCommand = name.replace("omumu_", "omumu ").replace("_", " ");
                cmd.put("cli", cliCommand);
                cmd.put("description", tool.path("description").asText(""));

                // Include input schema if present
                JsonNode inputSchema = tool.path("inputSchema");
                if (!inputSchema.isMissingNode() && inputSchema.has("properties")) {
                    ObjectNode params = cmd.putObject("parameters");
                    JsonNode properties = inputSchema.get("properties");
                    properties.fieldNames().forEachRemaining(fieldName -> {
                        JsonNode prop = properties.get(fieldName);
                        ObjectNode param = params.putObject(fieldName);
                        param.put("type", prop.path("type").asText("string"));
                        if (prop.has("description")) {
                            param.put("description", prop.get("description").asText());
                        }
                    });

                    // Mark required fields
                    JsonNode required = inputSchema.path("required");
                    if (required.isArray()) {
                        ArrayNode reqArray = cmd.putArray("required");
                        for (JsonNode r : required) {
                            reqArray.add(r.asText());
                        }
                    }
                }
            }

            System.out.println(mapper.writeValueAsString(schema));
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Tip: Run 'omumu login' first to connect to a site.");
            return 1;
        }
    }
}
