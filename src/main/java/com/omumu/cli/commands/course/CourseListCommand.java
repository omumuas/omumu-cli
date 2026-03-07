package com.omumu.cli.commands.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.commands.BaseCommand;
import com.omumu.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List all courses")
public class CourseListCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    CourseCommand courseCommand;

    @Option(names = "--include-modules", description = "Include module details", defaultValue = "false")
    boolean includeModules;

    @Override
    public Integer call() {
        OutputFormatter out = resolveFormatter(courseCommand.getParent());
        try {
            OmumuClient client = resolveClient(courseCommand.getParent());
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("includeModules", includeModules);
            JsonNode result = client.callTool("omumu_course_list", args);

            if (courseCommand.getParent().isJson()) {
                out.printResult(result);
                return 0;
            }

            // Parse into table
            JsonNode content = extractContent(result);
            if (content == null || !content.isArray() || content.isEmpty()) {
                out.printMessage("No courses found.");
                return 0;
            }

            // For text content, try to extract structured data
            if (content.get(0).has("text")) {
                // MCP returns content as [{type: "text", text: "..."}]
                out.printMessage(content.get(0).get("text").asText());
                return 0;
            }

            // If we get structured array data, render as table
            String[] headers = {"ID", "Title", "Status"};
            List<String[]> rows = new ArrayList<>();
            for (JsonNode course : content) {
                rows.add(new String[]{
                    course.path("id").asText(""),
                    course.path("title").asText(""),
                    course.path("status").asText("")
                });
            }
            out.printTable(headers, rows.toArray(new String[0][]));
            return 0;

        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }

    private JsonNode extractContent(JsonNode result) {
        // MCP tools/call returns {content: [{type: "text", text: "..."}]}
        if (result.has("content")) {
            return result.get("content");
        }
        return result;
    }
}
