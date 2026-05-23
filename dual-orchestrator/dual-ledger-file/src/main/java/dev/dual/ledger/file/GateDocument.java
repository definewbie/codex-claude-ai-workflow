package dev.dual.ledger.file;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GateDocument(
        int schemaVersion,
        String gateId,
        String nodeInstanceId,
        String gateType,
        String status,
        List<String> allowedActions,
        Instant openedAt,
        Instant resolvedAt,
        Map<String, Object> result
) {
    public GateDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(gateId, "gateId");
        requireText(nodeInstanceId, "nodeInstanceId");
        requireText(gateType, "gateType");
        requireText(status, "status");
        allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        allowedActions.forEach(value -> requireText(value, "allowedActions"));
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt is required");
        }
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
