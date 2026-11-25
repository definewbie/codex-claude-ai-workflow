package dev.dual.ledger.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.WorkflowDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CompletionAuditWriter {
    private static final TypeReference<java.util.Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;

    public CompletionAuditWriter(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
    }

    public CompletionAuditDocument write(
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            NodeInstanceDocument finalNode,
            String finalOutcome,
            Instant completedAt
    ) throws IOException {
        var audit = evaluate(paths, definition, finalNode.nodeDefinitionId(), finalOutcome, completedAt);
        jsonWriter.write(paths.completionAuditJson(), audit);
        return audit;
    }

    public CompletionAuditDocument evaluate(
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            String finalNodeDefinitionId,
            String finalOutcome,
            Instant completedAt
    ) {
        var gaps = new ArrayList<String>();
        auditFinalReport(paths, gaps);
        if (requiresArtifact(definition, "stage_handoff")) {
            auditHandoff(paths, gaps);
        }
        if (requiresHandler(definition, "prompt.generate_next_stage")) {
            auditNextStagePrompt(paths, gaps);
        }
        return new CompletionAuditDocument(
                1,
                "ENDED",
                gaps.isEmpty() ? "ready" : "needs_review",
                "COMPLETED",
                blankToEmpty(finalNodeDefinitionId),
                blankToEmpty(finalOutcome),
                List.copyOf(gaps),
                completedAt,
                Instant.now()
        );
    }

    private static boolean requiresArtifact(WorkflowDefinition definition, String artifactType) {
        return definition.nodes().stream()
                .anyMatch(node -> artifactType.equals(text(node.config().get("expected_artifact"))));
    }

    private static boolean requiresHandler(WorkflowDefinition definition, String handlerType) {
        return definition.nodes().stream().anyMatch(node -> handlerType.equals(node.handlerType()));
    }

    private void auditFinalReport(WorkstreamPaths paths, List<String> gaps) {
        var reportPath = paths.workstreamDir().resolve("reports").resolve("latest-final-report.md");
        if (!Files.isRegularFile(reportPath)) {
            gaps.add("final report is missing");
            return;
        }
        var report = readText(reportPath);
        for (var heading : List.of("## User Request", "## Verification Evidence", "## Acceptance Summary", "## Git Evidence")) {
            if (!report.contains(heading)) {
                gaps.add("final report missing " + heading);
            }
        }
    }

    private void auditHandoff(WorkstreamPaths paths, List<String> gaps) {
        var handoffPath = paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("handoff.json");
        if (!Files.isRegularFile(handoffPath)) {
            gaps.add("handoff.json is missing");
            return;
        }
        var handoff = readJson(handoffPath);
        if (text(handoff.get("workstreamId")).isBlank()) {
            gaps.add("handoff.json missing workstreamId");
        }
        if (textList(handoff.get("completedWork")).isEmpty() && textList(handoff.get("changedFiles")).isEmpty()) {
            gaps.add("handoff.json has no completedWork or changedFiles");
        }
        if (text(handoff.get("recommendedNextStage")).isBlank()
                && text(handoff.get("suggestedNextPrompt")).isBlank()) {
            gaps.add("handoff.json has no recommendedNextStage or suggestedNextPrompt");
        }
    }

    private void auditNextStagePrompt(WorkstreamPaths paths, List<String> gaps) {
        var promptPath = paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("next-stage-prompt.md");
        if (!Files.isRegularFile(promptPath)) {
            gaps.add("next-stage prompt is missing");
            return;
        }
        var prompt = readText(promptPath);
        if (!prompt.contains("## Prompt")) {
            gaps.add("next-stage prompt missing ## Prompt section");
        }
        if (!prompt.contains("## Handoff Context")) {
            gaps.add("next-stage prompt missing ## Handoff Context section");
        }
    }

    private java.util.Map<String, Object> readJson(java.nio.file.Path path) {
        try {
            return jsonWriter.read(path, MAP_TYPE);
        } catch (IOException ignored) {
            return java.util.Map.of();
        }
    }

    private static String readText(java.nio.file.Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static List<String> textList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
