package com.omumu.cli.commands.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.commands.BaseCommand;
import com.omumu.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "get", description = "Get course details")
public class CourseGetCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    CourseCommand courseCommand;

    @Parameters(index = "0", description = "Course ID")
    String courseId;

    @Override
    public Integer call() {
        OutputFormatter out = resolveFormatter(courseCommand.getParent());
        try {
            OmumuClient client = resolveClient(courseCommand.getParent());
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("courseId", courseId);
            JsonNode result = client.callTool("omumu_course_get", args);
            out.printResult(extractData(result));
            return 0;
        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }
}
