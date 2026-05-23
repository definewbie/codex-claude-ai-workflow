package dev.dual.runtime;

import dev.dual.core.workflow.WorkflowStatus;

public record WorkflowRuntimeResult(
        String workstreamId,
        WorkflowStatus status,
        String activeNode,
        String message
) {
    public WorkflowRuntimeResult {
        if (workstreamId == null || workstreamId.isBlank()) {
            throw new IllegalArgumentException("workstreamId is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        activeNode = activeNode == null ? "" : activeNode;
        message = message == null ? "" : message;
    }
}
