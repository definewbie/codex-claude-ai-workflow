package dev.dual.core.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NodeResult(
        String outcome,
        NodeStatus status,
        List<String> outputRefs,
        Map<String, Object> result,
        String errorMessage
) {
    public NodeResult {
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        result = copyMapAllowingNullValues("result", result);
    }

    public static NodeResult completed(String outcome, List<String> outputRefs, Map<String, Object> result) {
        return new NodeResult(outcome, NodeStatus.COMPLETED, outputRefs, result, null);
    }

    public static NodeResult waiting(String outcome, Map<String, Object> result) {
        return new NodeResult(outcome, NodeStatus.WAITING, List.of(), result, null);
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
