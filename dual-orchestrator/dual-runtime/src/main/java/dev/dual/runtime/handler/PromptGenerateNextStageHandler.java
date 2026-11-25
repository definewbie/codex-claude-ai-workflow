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
        var structured = structuredHandoff(context, sourceRefs);
        if (structured.isEmpty()) {
            return new NodeResult(
                    "STAGE_HANDOFF_CONTRACT_MISSING",
                    dev.dual.core.workflow.NodeStatus.FAILED,
                    sourceRefs,
                    java.util.Map.of("reason", "validated stage_handoff sidecar is required"),
                    "validated stage_handoff sidecar is required; Markdown fallback is not a workflow fact"
            );
        }
        var handoffSource = new HandoffSource(structured.get(), renderStructuredHandoffContext(structured.get()));
        var handoff = withLatestVerificationState(sourceRefs, context, handoffSource.document());
        var handoffContextText = renderStructuredHandoffContext(handoff);
        var suggestedPrompt = handoff.suggestedNextPrompt();
        var promptPath = outputDir.resolve("next-stage-prompt.md");
        Files.writeString(promptPath, renderPrompt(context, sourceRefs, suggestedPrompt, handoffContextText), StandardCharsets.UTF_8);

        var handoffJsonPath = outputDir.resolve("handoff.json");
        Files.writeString(handoffJsonPath, renderHandoffJson(handoff), StandardCharsets.UTF_8);

        var promptRef = toWorkspaceRef(context.workspaceRoot(), promptPath);
        var handoffJsonRef = toWorkspaceRef(context.workspaceRoot(), handoffJsonPath);
        var result = new LinkedHashMap<String, Object>();
        result.put("generated", true);
        result.put("mode", "structured-handoff");
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

    private static String renderStructuredHandoffContext(StageHandoffDocument handoff) {
        var builder = new StringBuilder();
        builder.append("### Source\n\n");
        builder.append(handoff.sourceHandoffPath().isBlank() ? "(unknown)" : handoff.sourceHandoffPath()).append("\n\n");
        appendList(builder, "Completed Work", handoff.completedWork());
        appendList(builder, "Important Decisions", handoff.importantDecisions());
        appendList(builder, "Changed Files", handoff.changedFiles());
        if (!handoff.verificationState().isBlank()) {
            builder.append("### Verification State\n\n").append(handoff.verificationState()).append("\n\n");
        }
        appendList(builder, "Open Risks", handoff.openRisks());
        if (!handoff.recommendedNextStage().isBlank()) {
            builder.append("### Recommended Next Stage\n\n").append(handoff.recommendedNextStage()).append("\n\n");
        }
        return builder.toString().stripTrailing();
    }

    private static void appendList(StringBuilder builder, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        builder.append("### ").append(title).append("\n\n");
        for (var item : items) {
            builder.append("- ").append(item).append("\n");
        }
        builder.append("\n");
    }

    private static List<String> readableInputRefs(TaskContext context) {
        return context.instance().inputRefs().stream()
                .filter(ref -> !ref.startsWith("artifact://"))
                .filter(ref -> Files.isRegularFile(context.workspaceRoot().resolve(ref).normalize()))
                .toList();
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String renderHandoffJson(StageHandoffDocument handoff) throws IOException {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(handoff.toMap());
    }

    private static StageHandoffDocument withLatestVerificationState(List<String> sourceRefs, TaskContext context, StageHandoffDocument handoff)
            throws IOException {
        var verificationState = latestVerificationState(sourceRefs, context);
        if (verificationState.isBlank()) {
            return handoff;
        }
        return new StageHandoffDocument(
                handoff.schemaVersion(),
                handoff.workstreamId(),
                handoff.stageId(),
                handoff.iterationId(),
                handoff.sourceHandoffPath(),
                handoff.completedWork(),
                handoff.importantDecisions(),
                handoff.changedFiles(),
                handoff.openRisks(),
                verificationState,
                handoff.recommendedNextStage(),
                handoff.suggestedNextPrompt(),
                handoff.generatedAt()
        );
    }

    private static String latestVerificationState(List<String> sourceRefs, TaskContext context) {
        var parts = new java.util.ArrayList<String>();
        latestInputContract(sourceRefs, context, "implement-claude-self-test.contract.json")
                .map(AgentOutputContractDocument::verificationSummary)
                .filter(summary -> !summary.isBlank())
                .ifPresent(summary -> parts.add("Self-test: " + summary));
        latestInputContract(sourceRefs, context, "acceptance-codex-review.contract.json")
                .filter(document -> !document.verificationSummary().isBlank())
                .map(document -> "Acceptance (" + document.decision() + "): " + document.verificationSummary())
                .filter(summary -> !summary.isBlank())
                .ifPresent(parts::add);
        return String.join("\n", parts);
    }

    private static java.util.Optional<AgentOutputContractDocument> latestInputContract(
            List<String> sourceRefs,
            TaskContext context,
            String fileName
    ) {
        for (int i = sourceRefs.size() - 1; i >= 0; i--) {
            var ref = sourceRefs.get(i);
            if (!Path.of(ref).getFileName().toString().equals(fileName)) {
                continue;
            }
            var path = context.workspaceRoot().resolve(ref).normalize();
            var document = AgentOutputContractDocument.read(path, OBJECT_MAPPER);
            if (document.isPresent() && AgentOutputLineageVerifier.violation(
                    context.ledger(), context.workspaceRoot(), context.workstreamId(), path, document.get()).isEmpty()) {
                return document;
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<StageHandoffDocument> structuredHandoff(TaskContext context, List<String> sourceRefs) throws IOException {
        for (String sourceRef : sourceRefs) {
            var outputPath = context.workspaceRoot().resolve(sourceRef).normalize();
            var sidecarPath = AgentOutputContractSidecar.pathFor(outputPath);
            var sidecar = AgentOutputContractDocument.read(sidecarPath, OBJECT_MAPPER);
            if (sidecar.isEmpty()
                    || AgentOutputLineageVerifier.violation(
                            context.ledger(), context.workspaceRoot(), context.workstreamId(), sidecarPath, sidecar.get()).isPresent()
                    || !sidecar.get().isStageHandoffArtifact()) {
                continue;
            }
            var changedFiles = sidecar.get().changedFiles();
            if (changedFiles.isEmpty()) {
                changedFiles = changedFilesFromGitEvidence(context);
            }
            return java.util.Optional.of(new StageHandoffDocument(
                    1,
                    context.workstreamId(),
                    context.instance().stageId(),
                    context.instance().iterationId(),
                    toWorkspaceRef(context.workspaceRoot(), sidecarPath),
                    sidecar.get().completedWork(),
                    sidecar.get().importantDecisions(),
                    changedFiles,
                    sidecar.get().openRisks(),
                    sidecar.get().verificationSummary(),
                    sidecar.get().recommendedNextStage(),
                    sidecar.get().suggestedNextPrompt(),
                    Instant.now().toString()
            ));
        }
        return java.util.Optional.empty();
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

    private record HandoffSource(StageHandoffDocument document, String contextText) {
    }

}
