package dev.dual.ledger.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CodexReviewControl(
        @JsonProperty("schema_version")
        int schemaVersion,
        @JsonProperty("artifact_type")
        String artifactType,
        @JsonProperty("artifact_id")
        String artifactId,
        @JsonProperty("workstream_id")
        String workstreamId,
        @JsonProperty("stage_id")
        String stageId,
        @JsonProperty("iteration_id")
        String iterationId,
        String decision,
        @JsonProperty("system_decision")
        String systemDecision,
        @JsonProperty("markdown_path")
        String markdownPath,
        @JsonProperty("must_fix")
        List<String> mustFix,
        List<String> risks,
        @JsonProperty("recommended_next_action")
        String recommendedNextAction,
        @JsonProperty("requires_human_review")
        boolean requiresHumanReview,
        @JsonProperty("next_prompt_ref")
        String nextPromptRef
) {
    public String normalizedDecision() {
        return decision == null ? "" : decision.trim().toUpperCase();
    }

    void validateFor(String expectedWorkstreamId, String expectedStageId, String expectedIterationId) throws IOException {
        var errors = new ArrayList<String>();
        require(errors, schemaVersion == 1, "schema_version must be 1");
        requireEquals(errors, "artifact_type", artifactType, "codex_design_review");
        requirePresent(errors, "artifact_id", artifactId);
        requireEquals(errors, "workstream_id", workstreamId, expectedWorkstreamId);
        requireEquals(errors, "stage_id", stageId, expectedStageId);
        requireEquals(errors, "iteration_id", iterationId, expectedIterationId);
        requirePresent(errors, "decision", decision);
        require(errors, Set.of("PASS", "NEEDS_REVISION", "BLOCKED").contains(normalizedDecision()),
                "decision must be PASS, NEEDS_REVISION, or BLOCKED");
        requirePresent(errors, "system_decision", systemDecision);
        requirePresent(errors, "markdown_path", markdownPath);
        requirePresent(errors, "recommended_next_action", recommendedNextAction);
        requirePresent(errors, "next_prompt_ref", nextPromptRef);
        require(errors, mustFix != null, "must_fix is required");
        require(errors, risks != null, "risks is required");
        requireDecisionMapping(errors);

        if (!errors.isEmpty()) {
            throw new IOException("invalid Codex review control: " + String.join("; ", errors));
        }
    }

    private void requireDecisionMapping(List<String> errors) {
        switch (normalizedDecision()) {
            case "PASS" -> {
                requireEquals(errors, "system_decision", systemDecision, "pause_for_human");
                requireEquals(errors, "recommended_next_action", recommendedNextAction, "human_design_review");
                require(errors, requiresHumanReview, "requires_human_review must be true for PASS");
            }
            case "NEEDS_REVISION" -> {
                requireEquals(errors, "system_decision", systemDecision, "revise");
                requireEquals(errors, "recommended_next_action", recommendedNextAction, "claude_design_revision");
            }
            case "BLOCKED" -> {
                requireEquals(errors, "system_decision", systemDecision, "blocked");
                requireEquals(errors, "recommended_next_action", recommendedNextAction, "human_intervention");
                require(errors, requiresHumanReview, "requires_human_review must be true for BLOCKED");
            }
            default -> {
            }
        }
    }

    private static void requirePresent(List<String> errors, String field, String value) {
        require(errors, value != null && !value.isBlank(), field + " is required");
    }

    private static void requireEquals(List<String> errors, String field, String actual, String expected) {
        requirePresent(errors, field, actual);
        if (actual != null && !actual.equals(expected)) {
            errors.add(field + " must be " + expected + " but was " + actual);
        }
    }

    private static void require(List<String> errors, boolean condition, String message) {
        if (!condition) {
            errors.add(message);
        }
    }
}
