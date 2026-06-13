package dev.dual.core.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record FlowTransitionCandidate(
        String fromNodeDefinitionId,
        String outcome,
        String toNodeDefinitionId,
        Map<String, Object> attributes
) {
    public FlowTransitionCandidate {
        if (fromNodeDefinitionId == null || fromNodeDefinitionId.isBlank()) {
            throw new IllegalArgumentException("from node definition id is required");
        }
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (toNodeDefinitionId == null || toNodeDefinitionId.isBlank()) {
            throw new IllegalArgumentException("to node definition id is required");
        }
        attributes = copyAttributes(attributes);
    }

    public static FlowTransitionCandidate of(String from, String outcome, String to) {
        return new FlowTransitionCandidate(from, outcome, to, Map.of());
    }

    private static Map<String, Object> copyAttributes(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("attribute key is required");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
