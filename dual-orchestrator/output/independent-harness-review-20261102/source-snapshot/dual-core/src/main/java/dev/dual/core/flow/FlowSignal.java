package dev.dual.core.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record FlowSignal(
        String nodeDefinitionId,
        String outcome,
        Map<String, Object> context
) {
    public FlowSignal {
        if (nodeDefinitionId == null || nodeDefinitionId.isBlank()) {
            throw new IllegalArgumentException("node definition id is required");
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("outcome is required");
        }
        context = copyContext(context);
    }

    public static FlowSignal completed(String nodeDefinitionId, String outcome, Map<String, Object> context) {
        return new FlowSignal(nodeDefinitionId, outcome, context);
    }

    private static Map<String, Object> copyContext(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("context key is required");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
