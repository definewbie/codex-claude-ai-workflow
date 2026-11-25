package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NodeExecutionDocument(
        int schemaVersion,
        String nodeExecutionId,
        String agentTaskId,
        String workflowExecutionId,
        String nodeInstanceId,
        String nodeDefinitionId,
        String handlerType,
        NodeExecutionStatus status,
        Long pid,
        String outcome,
        NodeStatus nodeStatus,
        List<String> outputRefs,
        Map<String, Object> result,
        String errorMessage,
        String runRef,
        String agentRunRef,
        String agent,
        String session,
        Integer exitCode,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs
) {
    public NodeExecutionDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(nodeExecutionId, "nodeExecutionId");
        agentTaskId = agentTaskId == null ? "" : agentTaskId;
        requireText(workflowExecutionId, "workflowExecutionId");
        requireText(nodeInstanceId, "nodeInstanceId");
        requireText(nodeDefinitionId, "nodeDefinitionId");
        requireText(handlerType, "handlerType");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        outputRefs.forEach(value -> requireText(value, "outputRefs"));
        result = copyMapAllowingNullValues(result);
    }

    private static Map<String, Object> copyMapAllowingNullValues(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("result key is required");
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
