package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexReviewControlTest {

    @Test
    void validPassControlValidatesSuccessfully() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "runs/run-001/outputs/review.md",
                List.of(), List.of(), "human_design_review", true, "prompts/next.md"
        );
        control.validateFor("ws-001", "stage-design", "iter-001");
    }

    @Test
    void validNeedsRevisionControlValidatesSuccessfully() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "NEEDS_REVISION", "revise",
                "runs/run-001/outputs/review.md",
                List.of("narrow scope"), List.of("risk1"), "claude_design_revision", false, "prompts/next.md"
        );
        control.validateFor("ws-001", "stage-design", "iter-001");
    }

    @Test
    void validBlockedControlValidatesSuccessfully() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "BLOCKED", "blocked",
                "runs/run-001/outputs/review.md",
                List.of(), List.of(), "human_intervention", true, "prompts/next.md"
        );
        control.validateFor("ws-001", "stage-design", "iter-001");
    }

    @Test
    void rejectsWrongSchemaVersion() {
        var control = new CodexReviewControl(
                2, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "review.md", List.of(), List.of(), "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("schema_version must be 1"));
    }

    @Test
    void rejectsWrongArtifactType() {
        var control = new CodexReviewControl(
                1, "wrong_type", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "review.md", List.of(), List.of(), "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("artifact_type must be codex_design_review"));
    }

    @Test
    void rejectsMissingArtifactId() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "review.md", List.of(), List.of(), "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("artifact_id is required"));
    }

    @Test
    void rejectsWrongWorkstreamId() {
        var control = controlWithDecision("PASS");
        var error = assertThrows(IOException.class,
                () -> control.validateFor("wrong-ws", "stage-design", "iter-001")
        ).getMessage();
        assertTrue(error.contains("workstream_id must be wrong-ws"));
    }

    @Test
    void rejectsWrongStageId() {
        var control = controlWithDecision("PASS");
        var error = assertThrows(IOException.class,
                () -> control.validateFor("ws-001", "wrong-stage", "iter-001")
        ).getMessage();
        assertTrue(error.contains("stage_id must be wrong-stage"));
    }

    @Test
    void rejectsWrongIterationId() {
        var control = controlWithDecision("PASS");
        var error = assertThrows(IOException.class,
                () -> control.validateFor("ws-001", "stage-design", "wrong-iter")
        ).getMessage();
        assertTrue(error.contains("iteration_id must be wrong-iter"));
    }

    @Test
    void rejectsMissingDecision() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", null, "pause_for_human",
                "review.md", List.of(), List.of(), "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("decision is required"));
    }

    @Test
    void rejectsUnsupportedDecision() {
        var control = controlWithDecision("UNKNOWN");
        var error = validateAndGetError(control);
        assertTrue(error.contains("decision must be PASS, NEEDS_REVISION, or BLOCKED"));
    }

    @Test
    void rejectsPassWithWrongSystemDecision() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "wrong_system",
                "review.md", List.of(), List.of(), "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("system_decision must be pause_for_human"));
    }

    @Test
    void rejectsPassWithRequiresHumanReviewFalse() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "review.md", List.of(), List.of(), "human_design_review", false, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("requires_human_review must be true for PASS"));
    }

    @Test
    void rejectsNeedsRevisionWithWrongRecommendedNextAction() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "NEEDS_REVISION", "revise",
                "review.md", List.of(), List.of(), "wrong_action", false, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("recommended_next_action must be claude_design_revision"));
    }

    @Test
    void rejectsBlockedWithWrongSystemDecision() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "BLOCKED", "wrong",
                "review.md", List.of(), List.of(), "human_intervention", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("system_decision must be blocked"));
    }

    @Test
    void rejectsNullMustFix() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "review.md", null, List.of(), "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("must_fix is required"));
    }

    @Test
    void rejectsNullRisks() {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", "PASS", "pause_for_human",
                "review.md", List.of(), null, "human_design_review", true, "next.md"
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("risks is required"));
    }

    @Test
    void collectsMultipleErrorsAtOnce() {
        var control = new CodexReviewControl(
                2, "wrong_type", "", "ws-001",
                "stage-design", "iter-001", null, null,
                null, null, null, null, false, null
        );
        var error = validateAndGetError(control);
        assertTrue(error.contains("schema_version must be 1"));
        assertTrue(error.contains("artifact_type must be codex_design_review"));
        assertTrue(error.contains("artifact_id is required"));
        assertTrue(error.contains("decision is required"));
    }

    @Test
    void normalizedDecisionHandlesNull() {
        var control = controlWithDecision(null);
        assertEquals("", control.normalizedDecision());
    }

    @Test
    void normalizedDecisionTrimsAndUppercases() {
        var control = controlWithDecision(" pass ");
        assertEquals("PASS", control.normalizedDecision());
    }

    @Test
    void normalizedDecisionLowercaseInput() {
        var control = controlWithDecision("needs_revision");
        assertEquals("NEEDS_REVISION", control.normalizedDecision());
    }

    private static CodexReviewControl controlWithDecision(String decision) {
        return new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-001",
                "stage-design", "iter-001", decision, systemDecisionFor(decision),
                "runs/run-001/outputs/review.md",
                List.of(), List.of(), nextActionFor(decision),
                "BLOCKED".equals(decision) || "PASS".equals(decision),
                "prompts/next.md"
        );
    }

    private static String systemDecisionFor(String decision) {
        if (decision == null) return "";
        return switch (decision.toUpperCase().trim()) {
            case "PASS" -> "pause_for_human";
            case "NEEDS_REVISION" -> "revise";
            case "BLOCKED" -> "blocked";
            default -> "unknown";
        };
    }

    private static String nextActionFor(String decision) {
        if (decision == null) return "";
        return switch (decision.toUpperCase().trim()) {
            case "PASS" -> "human_design_review";
            case "NEEDS_REVISION" -> "claude_design_revision";
            case "BLOCKED" -> "human_intervention";
            default -> "unknown";
        };
    }

    private static String validateAndGetError(CodexReviewControl control) {
        return assertThrows(IOException.class,
                () -> control.validateFor("ws-001", "stage-design", "iter-001")
        ).getMessage();
    }
}
