package dev.dual.ledger.file;

import dev.dual.core.workflow.WorkflowStatus;

import java.time.Instant;
import java.util.List;

public record WorkflowCurrentDocument(
        int schemaVersion,
        String workstreamId,
        String workflowInstanceId,
        String workflowId,
        int definitionVersion,
        WorkflowStatus status,
        List<String> activeNodeInstanceIds,
        List<String> openGateIds,
        String lastEventId,
        Instant updatedAt,
        long commitSequence
) {
    /** Domain writers propose state; the local commit protocol assigns the durable sequence. */
    public WorkflowCurrentDocument(int schemaVersion, String workstreamId, String workflowInstanceId,
                                   String workflowId, int definitionVersion, WorkflowStatus status,
                                   List<String> activeNodeInstanceIds, List<String> openGateIds,
                                   String lastEventId, Instant updatedAt) {
        this(schemaVersion, workstreamId, workflowInstanceId, workflowId, definitionVersion, status,
                activeNodeInstanceIds, openGateIds, lastEventId, updatedAt, 0);
    }

    public WorkflowCurrentDocument {
        if (commitSequence < 0) throw new IllegalArgumentException("negative commit sequence");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(workstreamId, "workstreamId");
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
        openGateIds = List.copyOf(openGateIds == null ? List.of() : openGateIds);
        openGateIds.forEach(value -> requireText(value, "openGateIds"));
        requireText(lastEventId, "lastEventId");
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
