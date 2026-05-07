package com.omumu.cli.commands.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.omumu.cli.client.OmumuClient;
import com.omumu.cli.commands.BaseCommand;
import com.omumu.cli.output.OutputFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "upload",
    description = "Upload a .skill bundle (admin). Streams the zip as multipart — sidesteps the LLM-emitted base64 limit."
)
public class SkillUploadCommand extends BaseCommand implements Callable<Integer> {

    @ParentCommand
    SkillCommand skillCommand;

    @Parameters(index = "0", paramLabel = "BUNDLE", description = "Path to a .skill zip bundle")
    Path bundle;

    @Option(names = "--publish", description = "Publish the skill on upload (default: keep as DRAFT for new skills, preserve status on update)")
    boolean publish;

    @Override
    public Integer call() {
        OutputFormatter out = resolveFormatter(skillCommand.getParent());
        try {
            if (!Files.isRegularFile(bundle)) {
                out.printError("Bundle file not found: " + bundle);
                return 1;
            }
            OmumuClient client = resolveClient(skillCommand.getParent());
            JsonNode result = client.uploadSkillBundle(bundle, publish);

            if (skillCommand.getParent().isJson()) {
                out.printResult(result);
            } else {
                out.printMessage("Skill uploaded: " + result.path("slug").asText("?")
                        + " (" + result.path("status").asText("?") + ")");
                out.printResult(result);
            }
            return 0;
        } catch (Exception e) {
            out.printError(e.getMessage());
            return 1;
        }
    }
}
