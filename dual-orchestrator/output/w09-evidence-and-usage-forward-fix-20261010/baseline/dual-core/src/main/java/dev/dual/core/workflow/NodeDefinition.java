package dev.dual.core.workflow;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record NodeDefinition(
        String id,
        NodeKind kind,
        String handlerType,
        Map<String, Object> config
) {
    public NodeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("node kind is required");
        }
        if (handlerType == null || handlerType.isBlank()) {
            throw new IllegalArgumentException("handler type is required");
        }
        config = copyMapAllowingNullValues("config", config);
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
