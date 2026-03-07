package com.omumu.cli.commands.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.commands.BaseCommand;
import com.omumu.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "create", description = "Create a new course")
public class CourseCreateCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    CourseCommand courseCommand;

    @Option(names = "--title", required = true, description = "Course title")
    String title;

    @Option(names = "--tagline", description = "Course tagline")
    String tagline;

    @Override
    public Integer call() {
        OutputFormatter out = resolveFormatter(courseCommand.getParent());
        try {
            OmumuClient client = resolveClient(courseCommand.getParent());
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("title", title);
            if (tagline != null) {
                args.put("tagline", tagline);
            }
            JsonNode result = client.callTool("omumu_course_create", args);

            if (courseCommand.getParent().isJson()) {
                out.printResult(result);
            } else {
                out.printMessage("Course created successfully.");
                out.printResult(result);
            }
            return 0;
        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }
}
