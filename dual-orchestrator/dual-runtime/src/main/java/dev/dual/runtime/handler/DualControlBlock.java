package dev.dual.runtime.handler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record DualControlBlock(Map<String, String> values) {
    DualControlBlock {
        values = copy(values);
    }

    String required(String key) {
        var value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("dual-control missing required key: " + key);
        }
        return value;
    }

    private static Map<String, String> copy(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                copy.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
