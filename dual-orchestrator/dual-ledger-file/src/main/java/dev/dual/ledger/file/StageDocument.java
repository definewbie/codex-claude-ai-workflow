package dev.dual.ledger.file;

import java.util.List;
import java.util.Map;

public record StageDocument(
        int schemaVersion,
        String stageId,
        String workstreamId,
        String workspaceId,
        String type,
        String status,
        String currentIterationId,
        List<String> inputArtifactRefs,
        List<String> outputArtifactRefs,
        Map<String, Object> gatePolicy
) {
    public static StageDocument initialDesign(String workstreamId, String workspaceId) {
        return new StageDocument(
                1,
                "stage-design",
                workstreamId,
                workspaceId,
                "design",
                "pending",
                "iter-001",
                List.of("artifact-requirement"),
                List.of(),
                Map.of("human_review_after_pass", true)
        );
    }

    StageDocument withStatus(String nextStatus) {
        return new StageDocument(
                schemaVersion,
                stageId,
                workstreamId,
                workspaceId,
                type,
                nextStatus,
                currentIterationId,
                inputArtifactRefs,
                outputArtifactRefs,
                gatePolicy
        );
    }

    StageDocument withStatusAndCurrentIteration(String nextStatus, String nextIterationId) {
        return new StageDocument(
                schemaVersion,
                stageId,
                workstreamId,
                workspaceId,
                type,
                nextStatus,
                nextIterationId,
                inputArtifactRefs,
                outputArtifactRefs,
                gatePolicy
        );
    }
}
