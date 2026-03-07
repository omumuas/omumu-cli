package com.omumu.cli;

import com.omumu.cli.commands.StatusCommand;
import com.omumu.cli.commands.course.CourseCommand;
import com.omumu.cli.config.LoginCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "omumu",
    description = "CLI for the Omumu customer education platform.",
    version = "omumu 0.1.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        LoginCommand.class,
        StatusCommand.class,
        CourseCommand.class
    }
)
public class OmumuCli implements Runnable {

    @Option(names = "--json", description = "Output as JSON (for scripts and AI agents)")
    boolean json;

    @Option(names = "--verbose", description = "Verbose output")
    boolean verbose;

    @Option(names = "--site-url", description = "Override site URL", defaultValue = "${OMUMU_URL}")
    String siteUrl;

    @Option(names = "--api-key", description = "Override API key", defaultValue = "${OMUMU_API_KEY}")
    String apiKey;

    public boolean isJson() {
        return json || "json".equalsIgnoreCase(System.getenv("OMUMU_OUTPUT"));
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public String getApiKey() {
        return apiKey;
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
