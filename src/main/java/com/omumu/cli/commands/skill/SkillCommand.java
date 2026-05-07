package com.omumu.cli.commands.skill;

import com.omumu.cli.OmumuCli;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "skill",
    description = "Manage skills (workflows for AI assistants)",
    mixinStandardHelpOptions = true,
    subcommands = {
        SkillUploadCommand.class
    }
)
public class SkillCommand implements Runnable {

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
