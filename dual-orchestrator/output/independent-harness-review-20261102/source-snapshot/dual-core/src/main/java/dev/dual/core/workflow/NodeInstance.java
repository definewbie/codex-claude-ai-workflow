package dev.dual.core.workflow;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NodeInstance(
        String nodeInstanceId,
        String nodeDefinitionId,
        NodeKind kind,
        String handlerType,
        NodeStatus status,
        String stageId,
        String iterationId,
        List<String> inputRefs,
        List<String> outputRefs,
        Map<String, Object> result,
        String runRef,
        Instant startedAt,
        Instant finishedAt
) {
    public NodeInstance {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            throw new IllegalArgumentException("node instance id is required");
        }
        if (nodeDefinitionId == null || nodeDefinitionId.isBlank()) {
            throw new IllegalArgumentException("node definition id is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("node kind is required");
        }
        if (handlerType == null || handlerType.isBlank()) {
            throw new IllegalArgumentException("handler type is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("node status is required");
        }
        if (stageId == null || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId is required");
        }
        if (iterationId == null || iterationId.isBlank()) {
            throw new IllegalArgumentException("iterationId is required");
        }
        inputRefs = List.copyOf(inputRefs == null ? List.of() : inputRefs);
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        result = copyMapAllowingNullValues("result", result);
    }

    private static Map<String, Object> copyMapAllowingNullValues(String fieldName, Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            var key = entry.getKey();
            if (key == null) {
                throw new IllegalArgumentException(fieldName + " key is required");
            }
            if (!(key instanceof String stringKey)) {
                throw new IllegalArgumentException(fieldName + " key must be a string: " + key);
            }
            copy.put(stringKey, entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
