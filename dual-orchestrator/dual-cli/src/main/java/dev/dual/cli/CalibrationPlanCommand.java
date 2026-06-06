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
        var templateProposal = templateProposal(candidate, targetArea, action, evidenceRefs);

        mapper.writerWithDefaultPrettyPrinter().writeValue(planJsonPath.toFile(),
                planJson(candidate, normalizedCandidateId, targetArea, action, evidenceRefs, candidatePath, templateProposal));
        Files.writeString(planMdPath,
                planMarkdown(candidate, normalizedCandidateId, targetArea, action, evidenceRefs,
                        candidatePath, planJsonPath, templateProposal),
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
            Path candidatePath,
            ObjectNode templateProposal
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
        plan.set("templateProposal", templateProposal);
        return plan;
    }

    private String planMarkdown(
            JsonNode candidate,
            String normalizedCandidateId,
            String targetArea,
            String action,
            List<String> evidenceRefs,
            Path candidatePath,
            Path planJsonPath,
            ObjectNode templateProposal
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
        lines.add("## Template Proposal");
        lines.add("");
        lines.add("- Target Area: " + templateProposal.path("targetArea").asText(""));
        lines.add("- Proposal Type: " + templateProposal.path("proposalType").asText(""));
        lines.add("- Status: " + templateProposal.path("status").asText(""));
        lines.add("");
        lines.add("### Proposed Change");
        lines.add("");
        lines.add(templateProposal.path("proposedChange").asText(""));
        lines.add("");
        lines.add("### Checklist Items");
        lines.add("");
        appendArrayLines(lines, templateProposal.path("checklistItems"));
        lines.add("");
        lines.add("### Regression Suggestion");
        lines.add("");
        lines.add(templateProposal.path("regressionSuggestion").asText(""));
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

    private ObjectNode templateProposal(
            JsonNode candidate,
            String targetArea,
            String action,
            List<String> evidenceRefs
    ) {
        var proposal = mapper.createObjectNode();
        var updateType = candidate.path("proposedUpdateType").asText("");
        proposal.put("status", "REVIEW_REQUIRED");
        proposal.put("targetArea", targetArea);
        proposal.put("proposalType", updateType.isBlank() ? "HUMAN_TRIAGE" : updateType);
        proposal.put("sourceFailureCategory", candidate.path("failureCategory").asText(""));
        proposal.put("proposedChange", proposedTemplateChange(candidate, action));
        proposal.put("regressionSuggestion", regressionSuggestion(updateType, evidenceRefs));
        var checklist = proposal.putArray("checklistItems");
        checklistItems(updateType, candidate).forEach(checklist::add);
        return proposal;
    }

    private String proposedTemplateChange(JsonNode candidate, String action) {
        var reasoning = nonBlank(candidate.path("reasoning").asText(""), "No reasoning supplied.");
        var suggestedAction = nonBlank(candidate.path("suggestedAction").asText(""), action);
        return "Turn this accepted learning into a principle-level template/checklist update. "
                + "Reasoning: " + reasoning + " Suggested action: " + suggestedAction;
    }

    private List<String> checklistItems(String updateType, JsonNode candidate) {
        var category = candidate.path("failureCategory").asText("");
        return switch (updateType) {
            case "CONTRACT_TEMPLATE" -> List.of(
                    "Add one reusable contract principle for " + nonBlank(category, "the observed failure category"),
                    "Keep the template domain-neutral; do not copy private project details",
                    "Add or update a contract/artifact regression fixture before promoting"
            );
            case "CONTEXT_RULE" -> List.of(
                    "Identify the smallest context section/profile that would have supplied the missing evidence",
                    "Prefer profile or section selection changes over broad context expansion",
                    "Add a context package regression proving the evidence is available"
            );
            case "REGRESSION_TEST" -> List.of(
                    "Create a focused regression sample that reproduces the failure shape",
                    "Keep the sample public or sanitize private details",
                    "Verify the regression fails before the fix and passes after"
            );
            default -> List.of(
                    "Decide whether the pattern is repeated or high-cost enough to keep",
                    "Avoid promoting one-off tool/runtime noise",
                    "Record the human decision before changing templates"
            );
        };
    }

    private String regressionSuggestion(String updateType, List<String> evidenceRefs) {
        var evidence = evidenceRefs.isEmpty() ? "the source candidate evidence" : String.join(", ", evidenceRefs);
        return switch (updateType) {
            case "CONTRACT_TEMPLATE" ->
                    "Add a contract compiler or artifact fixture regression using " + evidence + " as the source pattern.";
            case "CONTEXT_RULE" ->
                    "Add a context package regression showing the missing evidence appears in the target profile.";
            case "REGRESSION_TEST" ->
                    "Add this as a real-case benchmark or focused fixture before changing orchestration behavior.";
            default ->
                    "No automatic regression target. Keep this as a human-reviewed note unless the pattern repeats.";
        };
    }

    private void appendArrayLines(List<String> lines, JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            lines.add("- N/A");
            return;
        }
        for (var item : node) {
            lines.add("- " + item.asText(""));
        }
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
