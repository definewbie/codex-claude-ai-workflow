package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NodeInstanceDocument(
        int schemaVersion,
        String nodeInstanceId,
        String nodeDefinitionId,
        NodeKind kind,
        String handlerType,
        NodeStatus status,
        String stageId,
        String iterationId,
        List<String> inputRefs,
        Map<String, Object> inputTransition,
        List<String> outputRefs,
        Map<String, Object> result,
        String runRef,
        String latestExecutionId,
        Instant startedAt,
        Instant finishedAt
) {
    public NodeInstanceDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(nodeInstanceId, "nodeInstanceId");
        requireText(nodeDefinitionId, "nodeDefinitionId");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        requireText(handlerType, "handlerType");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        requireText(stageId, "stageId");
        requireText(iterationId, "iterationId");
        inputRefs = List.copyOf(inputRefs == null ? List.of() : inputRefs);
        inputRefs.forEach(value -> requireText(value, "inputRefs"));
        inputTransition = copyMapAllowingNullValues(inputTransition, "inputTransition");
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        outputRefs.forEach(value -> requireText(value, "outputRefs"));
        result = copyMapAllowingNullValues(result, "result");
        latestExecutionId = latestExecutionId == null ? "" : latestExecutionId;
    }

    private static Map<String, Object> copyMapAllowingNullValues(Map<String, Object> source, String field) {
        if (source == null) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException(field + " key is required");
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
