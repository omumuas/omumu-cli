package com.omumu.cli;

import com.omumu.cli.commands.SchemaCommand;
import com.omumu.cli.commands.StatusCommand;
import com.omumu.cli.commands.course.CourseCommand;
import com.omumu.cli.commands.skill.SkillCommand;
import com.omumu.cli.config.LoginCommand;
import com.omumu.cli.config.SignupCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "omumu",
    description = "CLI for the Omumu customer education platform.",
    version = "omumu 0.4.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        SignupCommand.class,
        LoginCommand.class,
        StatusCommand.class,
        SchemaCommand.class,
        CourseCommand.class,
        SkillCommand.class
    }
)
public class OmumuCli implements Runnable {

    @Mixin
    GlobalOptions globalOptions = new GlobalOptions();

    public boolean isJson() {
        return globalOptions.json || "json".equalsIgnoreCase(System.getenv("OMUMU_OUTPUT"));
    }

    public boolean isVerbose() {
        return globalOptions.verbose;
    }

    public String getSiteUrl() {
        return globalOptions.siteUrl;
    }

    public String getApiKey() {
        return globalOptions.apiKey;
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OmumuCli())
                .setColorScheme(createColorScheme())
                .execute(args);
        System.exit(exitCode);
    }

    private static CommandLine.Help.ColorScheme createColorScheme() {
        boolean noColor = System.getenv("NO_COLOR") != null;
        CommandLine.Help.ColorScheme.Builder builder = new CommandLine.Help.ColorScheme.Builder();
        if (!noColor) {
            builder.commands(CommandLine.Help.Ansi.Style.bold)
                   .options(CommandLine.Help.Ansi.Style.fg_yellow)
                   .parameters(CommandLine.Help.Ansi.Style.fg_yellow)
                   .optionParams(CommandLine.Help.Ansi.Style.italic);
        }
        return builder.build();
    }
}
