package com.omumu.cli.commands.course;

import com.omumu.cli.OmumuCli;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "course",
    description = "Manage courses",
    mixinStandardHelpOptions = true,
    subcommands = {
        CourseListCommand.class,
        CourseGetCommand.class,
        CourseCreateCommand.class
    }
)
public class CourseCommand implements Runnable {

    @ParentCommand
    OmumuCli parent;

    public OmumuCli getParent() {
        return parent;
    }

    @Override
    public void run() {
        new picocli.CommandLine(this).usage(System.out);
    }
}
