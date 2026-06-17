package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeKind;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentTaskDocument(
        int schemaVersion,
        String agentTaskId,
        String workflowExecutionId,
        String nodeInstanceId,
        String nodeDefinitionId,
        NodeKind nodeKind,
        String handlerType,
        AgentTaskStatus status,
        String agent,
        String session,
        String promptTemplate,
        String contextPackageRef,
        String expectedContract,
        Map<String, Object> dispatch,
        String nodeExecutionId,
        String errorMessage,
        Instant createdAt,
        Instant claimedAt,
        Instant completedAt
) {
    public AgentTaskDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(agentTaskId, "agentTaskId");
        requireText(workflowExecutionId, "workflowExecutionId");
        requireText(nodeInstanceId, "nodeInstanceId");
        requireText(nodeDefinitionId, "nodeDefinitionId");
        if (nodeKind == null) {
            throw new IllegalArgumentException("nodeKind is required");
        }
        requireText(handlerType, "handlerType");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        agent = agent == null ? "" : agent;
        session = session == null ? "" : session;
        promptTemplate = promptTemplate == null ? "" : promptTemplate;
        contextPackageRef = contextPackageRef == null ? "" : contextPackageRef;
        expectedContract = expectedContract == null ? "" : expectedContract;
        dispatch = copyMapAllowingNullValues(dispatch);
        nodeExecutionId = nodeExecutionId == null ? "" : nodeExecutionId;
        errorMessage = errorMessage == null ? "" : errorMessage;
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }

    private static Map<String, Object> copyMapAllowingNullValues(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("dispatch key is required");
            }
            copy.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
