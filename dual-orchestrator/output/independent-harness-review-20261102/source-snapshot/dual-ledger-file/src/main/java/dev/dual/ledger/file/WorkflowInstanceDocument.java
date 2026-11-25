package dev.dual.ledger.file;

import dev.dual.core.workflow.WorkflowStatus;

import java.time.Instant;
import java.util.List;

public record WorkflowInstanceDocument(
        int schemaVersion,
        String workflowInstanceId,
        String workflowId,
        int definitionVersion,
        WorkflowStatus status,
        List<String> activeNodeInstanceIds,
        String lastEventId,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowInstanceDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(workflowInstanceId, "workflowInstanceId");
        requireText(workflowId, "workflowId");
        if (definitionVersion <= 0) {
            throw new IllegalArgumentException("definitionVersion must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        activeNodeInstanceIds = List.copyOf(activeNodeInstanceIds == null ? List.of() : activeNodeInstanceIds);
        activeNodeInstanceIds.forEach(value -> requireText(value, "activeNodeInstanceIds"));
        requireText(lastEventId, "lastEventId");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
