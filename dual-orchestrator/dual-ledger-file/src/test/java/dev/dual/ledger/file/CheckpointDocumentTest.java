package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointDocumentTest {

    @Test
    void initialHasCorrectDefaults() {
        var cp = CheckpointDocument.initial("ws-001", "workspace-001");

        assertEquals(1, cp.schemaVersion());
        assertEquals("checkpoint-initial", cp.checkpointId());
        assertEquals("ws-001", cp.workstreamId());
        assertEquals("workspace-001", cp.workspaceId());
        assertEquals("stage-design", cp.stageId());
        assertEquals("iter-001", cp.iterationId());
        assertEquals("stable", cp.kind());
        assertEquals("workstream_created", cp.lastCompletedAction());
        assertEquals("claude_design", cp.pendingAction());
        assertEquals("start_created_design_workstream", cp.resumeReason());
        assertTrue(cp.requiredArtifactRefs().contains("artifact-requirement"));
        assertEquals("reuse_or_recreate_by_transport_health", cp.sessionStrategy());
        assertTrue(cp.resumeCommand().contains("ws-001"));
        assertNotNull(cp.createdAt());
    }

    @Test
    void claudeDesignPreparedSetsCheckpointAndPrompt() {
        var cp = CheckpointDocument.claudeDesignPrepared("ws-002", "workspace-002", "run-001", "prompts/design.md");

        assertEquals("checkpoint-claude-design-prepared", cp.checkpointId());
        assertEquals("claude_design_prompt_prepared", cp.lastCompletedAction());
        assertEquals("claude_design_acpx_run", cp.pendingAction());
        assertEquals("prompts/design.md", cp.recommendedContextPackageRef());
    }

    @Test
    void claudeDesignRevisionPreparedIncludesIteration() {
        var cp = CheckpointDocument.claudeDesignRevisionPrepared("ws-003", "workspace-003", "iter-002", "run-005", "prompts/revision.md");

        assertEquals("checkpoint-claude-design-revision-prepared-iter-002", cp.checkpointId());
        assertEquals("iter-002", cp.iterationId());
        assertEquals("claude_design_revision_prompt_prepared", cp.lastCompletedAction());
        assertEquals("prompts/revision.md", cp.recommendedContextPackageRef());
        assertTrue(cp.requiredArtifactRefs().contains("artifact-codex-review-001"));
        assertTrue(cp.requiredArtifactRefs().contains("artifact-human-feedback-001"));
    }

    @Test
    void claudeDesignCompletedDefaultIteration() {
        var cp = CheckpointDocument.claudeDesignCompleted("ws-004", "workspace-004", "run-002");

        assertEquals("checkpoint-claude-design-completed-iter-001", cp.checkpointId());
        assertEquals("iter-001", cp.iterationId());
        assertEquals("claude_design_completed", cp.lastCompletedAction());
        assertEquals("codex_design_review", cp.pendingAction());
    }

    @Test
    void claudeDesignCompletedCustomIteration() {
        var cp = CheckpointDocument.claudeDesignCompleted("ws-005", "workspace-005", "iter-003", "run-010");

        assertEquals("checkpoint-claude-design-completed-iter-003", cp.checkpointId());
        assertEquals("iter-003", cp.iterationId());
    }

    @Test
    void codexDesignReviewPreparedSetsPromptRef() {
        var cp = CheckpointDocument.codexDesignReviewPrepared("ws-006", "workspace-006", "run-003", "prompts/codex-review.md");

        assertEquals("checkpoint-codex-design-review-prepared", cp.checkpointId());
        assertEquals("codex_design_review_prompt_prepared", cp.lastCompletedAction());
        assertEquals("codex_design_review_acpx_run", cp.pendingAction());
        assertEquals("prompts/codex-review.md", cp.recommendedContextPackageRef());
    }

    @Test
    void codexDesignReviewFailedIsPartialWithExitCode() {
        var cp = CheckpointDocument.codexDesignReviewFailed("ws-007", "workspace-007", "run-004", 137);

        assertEquals("checkpoint-codex-design-review-failed", cp.checkpointId());
        assertEquals("partial_or_failed", cp.kind());
        assertEquals("codex_design_review_acpx_run_failed", cp.lastCompletedAction());
        assertEquals("codex_design_review_acpx_run", cp.pendingAction());
        assertTrue(cp.resumeReason().contains("137"));
    }

    @Test
    void codexDesignReviewPassedTransitionsToHumanReview() {
        var cp = CheckpointDocument.codexDesignReviewPassed("ws-008", "workspace-008", "run-005");

        assertEquals("checkpoint-design-human-review", cp.checkpointId());
        assertEquals("codex_design_review_passed", cp.lastCompletedAction());
        assertEquals("human_design_review", cp.pendingAction());
        assertTrue(cp.resumeCommand().contains("review approve"));
    }

    @Test
    void codexDesignReviewNeedsRevisionTriggersRevision() {
        var cp = CheckpointDocument.codexDesignReviewNeedsRevision("ws-009", "workspace-009", "run-006");

        assertEquals("checkpoint-codex-design-needs-revision", cp.checkpointId());
        assertEquals("codex_design_review_needs_revision", cp.lastCompletedAction());
        assertEquals("claude_design_revision", cp.pendingAction());
        assertEquals("codex_requested_revision", cp.resumeReason());
        assertEquals("stages/01-design/iterations/001/next-claude-prompt.md", cp.recommendedContextPackageRef());
    }

    @Test
    void codexDesignReviewBlockedRequiresHumanIntervention() {
        var cp = CheckpointDocument.codexDesignReviewBlocked("ws-010", "workspace-010", "run-007");

        assertEquals("checkpoint-codex-design-blocked", cp.checkpointId());
        assertEquals("codex_design_review_blocked", cp.lastCompletedAction());
        assertEquals("human_intervention", cp.pendingAction());
        assertTrue(cp.resumeCommand().contains("status"));
    }

    @Test
    void designApprovedMovesPastHumanReview() {
        var cp = CheckpointDocument.designApproved("ws-011", "workspace-011");

        assertEquals("checkpoint-design-approved", cp.checkpointId());
        assertEquals("human_design_review_approved", cp.lastCompletedAction());
        assertEquals("plan_not_implemented", cp.pendingAction());
    }

    @Test
    void humanRequestedDesignRevisionPreservesFeedbackRef() {
        var cp = CheckpointDocument.humanRequestedDesignRevision("ws-012", "workspace-012");

        assertEquals("checkpoint-human-design-revision-requested", cp.checkpointId());
        assertEquals("human_design_review_requested_revision", cp.lastCompletedAction());
        assertEquals("claude_design_revision", cp.pendingAction());
        assertEquals("human_requested_design_revision", cp.resumeReason());
        assertEquals("stages/01-design/iterations/001/human-feedback.md", cp.recommendedContextPackageRef());
    }

    @Test
    void claudeDesignFailedIsPartialWithExitCode() {
        var cp = CheckpointDocument.claudeDesignFailed("ws-013", "workspace-013", "run-020", 1);

        assertEquals("checkpoint-claude-design-failed", cp.checkpointId());
        assertEquals("partial_or_failed", cp.kind());
        assertEquals("claude_design_acpx_run_failed", cp.lastCompletedAction());
        assertTrue(cp.resumeReason().contains("1"));
    }

    @Test
    void allCheckpointsIncludeWorkstreamIdInResumeCommand() {
        var wsId = "ws-resume-check";
        assertTrue(CheckpointDocument.initial(wsId, "wp").resumeCommand().contains(wsId));
        assertTrue(CheckpointDocument.claudeDesignPrepared(wsId, "wp", "r", "p").resumeCommand().contains(wsId));
        assertTrue(CheckpointDocument.claudeDesignCompleted(wsId, "wp", "r").resumeCommand().contains(wsId));
        assertTrue(CheckpointDocument.codexDesignReviewPrepared(wsId, "wp", "r", "p").resumeCommand().contains(wsId));
        assertTrue(CheckpointDocument.codexDesignReviewFailed(wsId, "wp", "r", 1).resumeCommand().contains(wsId));
        assertTrue(CheckpointDocument.codexDesignReviewNeedsRevision(wsId, "wp", "r").resumeCommand().contains(wsId));
    }

    @Test
    void stableCheckpointsAreStableFailedOnesAreNot() {
        assertEquals("stable", CheckpointDocument.initial("ws", "wp").kind());
        assertEquals("stable", CheckpointDocument.codexDesignReviewPassed("ws", "wp", "r").kind());
        assertEquals("partial_or_failed", CheckpointDocument.codexDesignReviewFailed("ws", "wp", "r", 1).kind());
        assertEquals("partial_or_failed", CheckpointDocument.claudeDesignFailed("ws", "wp", "r", 1).kind());
    }
}
