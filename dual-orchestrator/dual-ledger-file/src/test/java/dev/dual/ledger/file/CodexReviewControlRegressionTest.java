package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that CodexReviewControl.validateFor() accepts the decision values
 * produced by DualControlBlockParser for each codex review decision type.
 * Ensures the parser output contract stays compatible with the validator.
 */
class CodexReviewControlRegressionTest {

    @Test
    void passDecision_validatesSuccessfully() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-reg",
                "stage-design", "iter-001",
                "PASS", "pause_for_human",
                "runs/run-001/outputs/review.md",
                List.of(), List.of(), "human_design_review", true, "prompts/next.md"
        );
        assertDoesNotThrow(() -> control.validateFor("ws-reg", "stage-design", "iter-001"));
    }

    @Test
    void needsRevisionDecision_validatesSuccessfully() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-reg",
                "stage-design", "iter-001",
                "NEEDS_REVISION", "revise",
                "runs/run-001/outputs/review.md",
                List.of("scope too broad"), List.of("caching complexity"),
                "claude_design_revision", false, "prompts/next.md"
        );
        assertDoesNotThrow(() -> control.validateFor("ws-reg", "stage-design", "iter-001"));
    }

    @Test
    void blockedDecision_validatesSuccessfully() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-reg",
                "stage-design", "iter-001",
                "BLOCKED", "blocked",
                "runs/run-001/outputs/review.md",
                List.of("auth method unclear"), List.of(),
                "human_intervention", true, "prompts/next.md"
        );
        assertDoesNotThrow(() -> control.validateFor("ws-reg", "stage-design", "iter-001"));
    }

    @Test
    void lowercasePass_normalizesAndValidates() throws Exception {
        var control = new CodexReviewControl(
                1, "codex_design_review", "artifact-001", "ws-reg",
                "stage-design", "iter-001",
                "pass", "pause_for_human",
                "runs/run-001/outputs/review.md",
                List.of(), List.of(), "human_design_review", true, "prompts/next.md"
        );
        assertDoesNotThrow(() -> control.validateFor("ws-reg", "stage-design", "iter-001"));
    }
}
