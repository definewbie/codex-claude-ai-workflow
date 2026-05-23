package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

public record CheckpointDocument(
        int schemaVersion,
        String checkpointId,
        String workstreamId,
        String workspaceId,
        String stageId,
        String iterationId,
        String kind,
        String lastCompletedAction,
        String pendingAction,
        String resumeReason,
        List<String> requiredArtifactRefs,
        String recommendedContextPackageRef,
        String sessionStrategy,
        String resumeCommand,
        Instant createdAt
) {
    public static CheckpointDocument initial(String workstreamId, String workspaceId) {
        return new CheckpointDocument(
                1,
                "checkpoint-initial",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "workstream_created",
                "claude_design",
                "start_created_design_workstream",
                List.of("artifact-requirement"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument claudeDesignPrepared(
            String workstreamId,
            String workspaceId,
            String runId,
            String promptPath
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-claude-design-prepared",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "claude_design_prompt_prepared",
                "claude_design_acpx_run",
                "resume_prepared_claude_design_prompt",
                java.util.List.of("artifact-requirement"),
                promptPath,
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument claudeDesignRevisionPrepared(
            String workstreamId,
            String workspaceId,
            String iterationId,
            String runId,
            String promptPath
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-claude-design-revision-prepared-" + iterationId,
                workstreamId,
                workspaceId,
                "stage-design",
                iterationId,
                "stable",
                "claude_design_revision_prompt_prepared",
                "claude_design_acpx_run",
                "resume_prepared_claude_design_revision_prompt",
                List.of("artifact-requirement", "artifact-codex-review-001", "artifact-human-feedback-001"),
                promptPath,
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument claudeDesignCompleted(
            String workstreamId,
            String workspaceId,
            String runId
    ) {
        return claudeDesignCompleted(workstreamId, workspaceId, "iter-001", runId);
    }

    public static CheckpointDocument claudeDesignCompleted(
            String workstreamId,
            String workspaceId,
            String iterationId,
            String runId
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-claude-design-completed-" + iterationId,
                workstreamId,
                workspaceId,
                "stage-design",
                iterationId,
                "stable",
                "claude_design_completed",
                "codex_design_review",
                "claude_design_handoff_ready",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument codexDesignReviewPrepared(
            String workstreamId,
            String workspaceId,
            String runId,
            String promptPath
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-codex-design-review-prepared",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "codex_design_review_prompt_prepared",
                "codex_design_review_acpx_run",
                "resume_prepared_codex_design_review_prompt",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001"),
                promptPath,
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument codexDesignReviewFailed(
            String workstreamId,
            String workspaceId,
            String runId,
            int exitCode
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-codex-design-review-failed",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "partial_or_failed",
                "codex_design_review_acpx_run_failed",
                "codex_design_review_acpx_run",
                "agent_exit_nonzero_" + exitCode,
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument codexDesignReviewPassed(
            String workstreamId,
            String workspaceId,
            String runId
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-design-human-review",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "codex_design_review_passed",
                "human_design_review",
                "default_human_gate",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001", "artifact-codex-review-001"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl review approve " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument codexDesignReviewNeedsRevision(
            String workstreamId,
            String workspaceId,
            String runId
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-codex-design-needs-revision",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "codex_design_review_needs_revision",
                "claude_design_revision",
                "codex_requested_revision",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001", "artifact-codex-review-001"),
                "stages/01-design/iterations/001/next-claude-prompt.md",
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument codexDesignReviewBlocked(
            String workstreamId,
            String workspaceId,
            String runId
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-codex-design-blocked",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "codex_design_review_blocked",
                "human_intervention",
                "codex_blocked_design",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001", "artifact-codex-review-001"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl status " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument designApproved(
            String workstreamId,
            String workspaceId
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-design-approved",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "human_design_review_approved",
                "plan_not_implemented",
                "human_approved_design_gate",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001", "artifact-codex-review-001", "artifact-human-decision-001"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl status " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument humanRequestedDesignRevision(
            String workstreamId,
            String workspaceId
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-human-design-revision-requested",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "stable",
                "human_design_review_requested_revision",
                "claude_design_revision",
                "human_requested_design_revision",
                java.util.List.of("artifact-requirement", "artifact-claude-handoff-001", "artifact-codex-review-001", "artifact-human-feedback-001"),
                "stages/01-design/iterations/001/human-feedback.md",
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }

    public static CheckpointDocument claudeDesignFailed(
            String workstreamId,
            String workspaceId,
            String runId,
            int exitCode
    ) {
        return new CheckpointDocument(
                1,
                "checkpoint-claude-design-failed",
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "partial_or_failed",
                "claude_design_acpx_run_failed",
                "claude_design_acpx_run",
                "agent_exit_nonzero_" + exitCode,
                java.util.List.of("artifact-requirement"),
                "current/context-package.json",
                "reuse_or_recreate_by_transport_health",
                "dualctl resume " + workstreamId,
                Instant.now()
        );
    }
}
