package dev.dual.core.workflow;

import java.time.Instant;
import java.util.List;

public record WorkflowInstance(
        String workflowInstanceId,
        String workflowId,
        int definitionVersion,
        WorkflowStatus status,
        List<String> activeNodeInstanceIds,
        String lastEventId,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowInstance {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            throw new IllegalArgumentException("workflow instance id is required");
        }
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflow id is required");
        }
        if (definitionVersion <= 0) {
            throw new IllegalArgumentException("definition version must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("workflow status is required");
        }
        if (lastEventId == null || lastEventId.isBlank()) {
            throw new IllegalArgumentException("lastEventId is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
        activeNodeInstanceIds = List.copyOf(activeNodeInstanceIds == null ? List.of() : activeNodeInstanceIds);
    }
}
