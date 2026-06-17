package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

public record WorkflowExecutionDocument(
        int schemaVersion,
        String workflowExecutionId,
        String workflowInstanceId,
        String workstreamId,
        String trigger,
        WorkflowExecutionStatus status,
        List<String> nodeExecutionIds,
        String stopReason,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs
) {
    public WorkflowExecutionDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(workflowExecutionId, "workflowExecutionId");
        requireText(workflowInstanceId, "workflowInstanceId");
        requireText(workstreamId, "workstreamId");
        requireText(trigger, "trigger");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        nodeExecutionIds = List.copyOf(nodeExecutionIds == null ? List.of() : nodeExecutionIds);
        nodeExecutionIds.forEach(value -> requireText(value, "nodeExecutionIds"));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
