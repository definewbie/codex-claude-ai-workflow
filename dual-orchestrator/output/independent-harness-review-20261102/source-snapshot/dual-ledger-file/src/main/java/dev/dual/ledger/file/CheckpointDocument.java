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
}
