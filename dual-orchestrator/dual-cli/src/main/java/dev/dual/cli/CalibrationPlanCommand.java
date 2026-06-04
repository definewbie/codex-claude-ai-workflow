package dev.dual.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "plan", description = "Create a human-reviewable calibration apply plan.", mixinStandardHelpOptions = true)
public final class CalibrationPlanCommand implements Callable<Integer> {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Parameters(index = "1")
    String candidateId;

    @Override
    public Integer call() throws Exception {
        var normalizedCandidateId = CalibrationCandidateFiles.normalizeCandidateId(candidateId);
        var candidatePath = CalibrationCandidateFiles.candidateJson(workdir, workstreamId, normalizedCandidateId);
        if (!Files.isRegularFile(candidatePath)) {
            System.err.println("Calibration candidate not found: " + candidatePath);
            return 1;
        }

        var candidate = mapper.readTree(candidatePath.toFile());
        var planJsonPath = CalibrationCandidateFiles.applyPlanJson(workdir, workstreamId, normalizedCandidateId);
        var planMdPath = CalibrationCandidateFiles.applyPlanMarkdown(planJsonPath);
        Files.createDirectories(planJsonPath.getParent());

        var targetArea = targetArea(candidate.path("proposedUpdateType").asText(""));
        var action = recommendedAction(candidate.path("proposedUpdateType").asText(""));
        var evidenceRefs = evidenceRefs(candidate.path("evidenceRefs"));

        mapper.writerWithDefaultPrettyPrinter().writeValue(planJsonPath.toFile(),
                planJson(candidate, normalizedCandidateId, targetArea, action, evidenceRefs, candidatePath));
        Files.writeString(planMdPath,
                planMarkdown(candidate, normalizedCandidateId, targetArea, action, evidenceRefs, candidatePath, planJsonPath),
                StandardCharsets.UTF_8);

        System.out.println("Calibration apply plan created");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Candidate: " + candidate.path("candidateId").asText(normalizedCandidateId));
        System.out.println("Plan: " + planMdPath);
        System.out.println("Plan JSON: " + planJsonPath);
        System.out.println("Note: plan files do not modify prompts, contract templates, context rules, workflow definitions, or tests.");
        return 0;
    }

    private ObjectNode planJson(
            JsonNode candidate,
            String normalizedCandidateId,
            String targetArea,
            String action,
            List<String> evidenceRefs,
            Path candidatePath
    ) {
        var plan = mapper.createObjectNode();
        plan.put("schemaVersion", 1);
        plan.put("candidateId", candidate.path("candidateId").asText(normalizedCandidateId));
        plan.put("sourceWorkstreamId", candidate.path("sourceWorkstreamId").asText(workstreamId));
        plan.put("sourceNodeId", candidate.path("sourceNodeId").asText(""));
        plan.put("candidateStatus", candidate.path("status").asText("UNKNOWN"));
        plan.put("failureCategory", candidate.path("failureCategory").asText(""));
        plan.put("reviewDecision", candidate.path("reviewDecision").asText(""));
        plan.put("proposedUpdateType", candidate.path("proposedUpdateType").asText(""));
        plan.put("proposedTargetArea", targetArea);
        plan.put("recommendedHumanAction", action);
        plan.put("sourceCandidateRef", candidatePath.toString());
        plan.put("status", "PROPOSED");
        plan.put("createdAt", Instant.now().toString());
        var refs = plan.putArray("evidenceRefs");
        evidenceRefs.forEach(refs::add);
        return plan;
    }

    private String planMarkdown(
            JsonNode candidate,
            String normalizedCandidateId,
            String targetArea,
            String action,
            List<String> evidenceRefs,
            Path candidatePath,
            Path planJsonPath
    ) {
        var lines = new ArrayList<String>();
        lines.add("# Calibration Apply Plan");
        lines.add("");
        lines.add("- Candidate: " + candidate.path("candidateId").asText(normalizedCandidateId));
        lines.add("- Candidate Status: " + candidate.path("status").asText("UNKNOWN"));
        lines.add("- Failure Category: " + candidate.path("failureCategory").asText(""));
        lines.add("- Review Decision: " + candidate.path("reviewDecision").asText(""));
        lines.add("- Proposed Update Type: " + candidate.path("proposedUpdateType").asText(""));
        lines.add("- Proposed Target Area: " + targetArea);
        lines.add("- Apply Plan Status: PROPOSED");
        lines.add("- Source Candidate: " + candidatePath);
        lines.add("- Plan JSON: " + planJsonPath);
        lines.add("");
        lines.add("## Why This Exists");
        lines.add("");
        lines.add(nonBlank(candidate.path("reasoning").asText(""), "The source failure was classified as a reusable learning candidate."));
        lines.add("");
        lines.add("## Recommended Human Action");
        lines.add("");
        lines.add(action);
        lines.add("");
        lines.add("## Suggested Action From Candidate");
        lines.add("");
        lines.add(nonBlank(candidate.path("suggestedAction").asText(""), "N/A"));
        lines.add("");
        lines.add("## Evidence Refs");
        lines.add("");
        if (evidenceRefs.isEmpty()) {
            lines.add("- N/A");
        } else {
            evidenceRefs.forEach(ref -> lines.add("- " + ref));
        }
        lines.add("");
        lines.add("## Not Applied Automatically");
        lines.add("");
        lines.add("This command does not modify prompts, contract templates, context rules, workflow definitions, or tests.");
        lines.add("Review this plan first, then apply the smallest durable change only if the failure pattern is worth keeping.");
        lines.add("");
        return String.join("\n", lines);
    }

    private List<String> evidenceRefs(JsonNode node) {
        var refs = new ArrayList<String>();
        if (node != null && node.isArray()) {
            for (var item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    refs.add(item.asText());
                }
            }
        }
        return refs;
    }

    private String targetArea(String updateType) {
        return switch (updateType) {
            case "CONTRACT_TEMPLATE" -> "Task contract template or contract compiler checklist";
            case "CONTEXT_RULE" -> "Context package profile or context section selection rule";
            case "REGRESSION_TEST" -> "Real-case benchmark, regression fixture, or prompt regression sample";
            default -> "Human triage";
        };
    }

    private String recommendedAction(String updateType) {
        return switch (updateType) {
            case "CONTRACT_TEMPLATE" ->
                    "Review whether the failure exposes a reusable contract gap. If yes, update the relevant contract template or compiler checklist with one principle-level requirement, then add a focused regression sample.";
            case "CONTEXT_RULE" ->
                    "Review whether the verifier missed necessary evidence because the context package was incomplete. If yes, adjust the smallest context profile or section rule that would have supplied the evidence.";
            case "REGRESSION_TEST" ->
                    "Review whether the implementation failure is repeatable and costly. If yes, add it to the real-case benchmark or a focused regression fixture before changing orchestration behavior.";
            default ->
                    "Review the candidate manually. Do not promote it unless it represents a repeated or high-cost failure pattern.";
        };
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
