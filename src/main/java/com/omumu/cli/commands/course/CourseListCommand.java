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
            JsonNode data = extractData(result);

            if (courseCommand.getParent().isJson()) {
                out.printResult(data);
                return 0;
            }

            if (!data.isArray() || data.isEmpty()) {
                out.printMessage("No courses found.");
                return 0;
            }

            String[] headers = {"ID", "Title", "Modules", "Lessons"};
            List<String[]> rows = new ArrayList<>();
            for (JsonNode course : data) {
                rows.add(new String[]{
                    course.path("id").asText(""),
                    course.path("title").asText(""),
                    course.path("stats").path("moduleCount").asText(""),
                    course.path("stats").path("lessonCount").asText("")
                });
            }
            out.printTable(headers, rows.toArray(new String[0][]));
            return 0;

        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }
}
