package dev.dual.ledger.file;

import java.time.Instant;

public record WorkflowIterationDocument(
        int schemaVersion,
        String iterationId,
        String parentIterationId,
        int number,
        String stageId,
        String status,
        String triggerNodeInstanceId,
        String triggerAction,
        String targetNodeDefinitionId,
        String reason,
        Instant createdAt
) {
    public WorkflowIterationDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(iterationId, "iterationId");
        if (number <= 0) {
            throw new IllegalArgumentException("number must be positive");
        }
        requireText(stageId, "stageId");
        requireText(status, "status");
        requireText(triggerAction, "triggerAction");
        requireText(targetNodeDefinitionId, "targetNodeDefinitionId");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        parentIterationId = parentIterationId == null ? "" : parentIterationId;
        triggerNodeInstanceId = triggerNodeInstanceId == null ? "" : triggerNodeInstanceId;
        reason = reason == null ? "" : reason;
    }

    public static WorkflowIterationDocument initial(String entryNodeId, Instant createdAt) {
        return new WorkflowIterationDocument(
                1,
                "iter-001",
                "",
                1,
                stageId(entryNodeId),
                "active",
                "",
                "workflow_started",
                entryNodeId,
                "Initial workflow iteration.",
                createdAt
        );
    }

    public static WorkflowIterationDocument revision(
            String iterationId,
            int number,
            String parentIterationId,
            String triggerNodeInstanceId,
            String triggerAction,
            String targetNodeDefinitionId,
            String reason,
            Instant createdAt
    ) {
        return new WorkflowIterationDocument(
                1,
                iterationId,
                parentIterationId,
                number,
                stageId(targetNodeDefinitionId),
                "active",
                triggerNodeInstanceId,
                triggerAction,
                targetNodeDefinitionId,
                reason,
                createdAt
        );
    }

    private static String stageId(String nodeDefinitionId) {
        var separator = nodeDefinitionId.indexOf('.');
        var prefix = separator > 0 ? nodeDefinitionId.substring(0, separator) : "workflow";
        return "stage-" + prefix;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
