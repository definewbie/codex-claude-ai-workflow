package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public final class PromptGenerateNextStageHandler implements TaskHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "prompt.generate_next_stage";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var outputDir = workstreamDir.resolve("artifacts").resolve("next-stage");
        Files.createDirectories(outputDir);

        var sourceRefs = readableInputRefs(context);
        var handoffText = combinedInputText(context, sourceRefs);
        var handoff = StageHandoffMarkdownParser.parse(handoffText);
        var suggestedPrompt = handoff.suggestedPrompt();
        var promptPath = outputDir.resolve("next-stage-prompt.md");
        Files.writeString(promptPath, renderPrompt(context, sourceRefs, suggestedPrompt, handoffText), StandardCharsets.UTF_8);

        var handoffJsonPath = outputDir.resolve("handoff.json");
        Files.writeString(handoffJsonPath, renderHandoffJson(context, sourceRefs, handoff), StandardCharsets.UTF_8);

        var promptRef = toWorkspaceRef(context.workspaceRoot(), promptPath);
        var handoffJsonRef = toWorkspaceRef(context.workspaceRoot(), handoffJsonPath);
        var result = new LinkedHashMap<String, Object>();
        result.put("generated", true);
        result.put("mode", "handoff-derived");
        result.put("prompt_path", promptRef);
        result.put("handoff_json_path", handoffJsonRef);
        result.put("source_refs", sourceRefs);
        result.put("used_suggested_prompt_section", !suggestedPrompt.isBlank());
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(promptRef, handoffJsonRef), result);
    }

    private static String renderPrompt(TaskContext context, List<String> sourceRefs, String suggestedPrompt, String handoffText) {
        var builder = new StringBuilder();
        builder.append("# Next Stage Prompt\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Source Handoff Refs: ").append(sourceRefs.isEmpty() ? "(none)" : String.join(", ", sourceRefs)).append("\n\n");

        builder.append("## Prompt\n\n");
        if (!suggestedPrompt.isBlank()) {
            builder.append(suggestedPrompt.stripTrailing()).append("\n\n");
        } else {
            builder.append("Continue the next stage using the handoff below. Preserve the existing scope, review open risks first, and do not invent unrelated requirements.\n\n");
        }

        builder.append("## Handoff Context\n\n");
        if (handoffText.isBlank()) {
            builder.append("(no handoff artifact was available)\n");
        } else {
            builder.append(handoffText.stripTrailing()).append("\n");
        }
        return builder.toString();
    }

    private static List<String> readableInputRefs(TaskContext context) {
        return context.instance().inputRefs().stream()
                .filter(ref -> !ref.startsWith("artifact://"))
                .filter(ref -> Files.isRegularFile(context.workspaceRoot().resolve(ref).normalize()))
                .toList();
    }

    private static String combinedInputText(TaskContext context, List<String> refs) throws IOException {
        var builder = new StringBuilder();
        for (String ref : refs) {
            var path = context.workspaceRoot().resolve(ref).normalize();
            builder.append("### ").append(ref).append("\n\n");
            builder.append(Files.readString(path, StandardCharsets.UTF_8).stripTrailing()).append("\n\n");
        }
        return builder.toString().stripTrailing();
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String renderHandoffJson(TaskContext context, List<String> sourceRefs,
                                            StageHandoffMarkdownParser handoff) throws IOException {
        var completedWork = handoff.completedWork();
        var changedFiles = handoff.changedFiles();
        if (changedFiles.isEmpty()) {
            changedFiles = changedFilesFromGitEvidence(context);
        }
        var document = new StageHandoffDocument(
                1,
                context.workstreamId(),
                context.instance().stageId(),
                context.instance().iterationId(),
                sourceRefs.isEmpty() ? "" : sourceRefs.get(0),
                completedWork,
                changedFiles,
                handoff.openRisks(),
                handoff.verificationState(),
                Instant.now().toString()
        );
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document.toMap());
    }

    private static List<String> changedFilesFromGitEvidence(TaskContext context) throws IOException {
        var path = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("artifacts")
                .resolve("git-evidence")
                .resolve("changed-files.txt");
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
    }

}
