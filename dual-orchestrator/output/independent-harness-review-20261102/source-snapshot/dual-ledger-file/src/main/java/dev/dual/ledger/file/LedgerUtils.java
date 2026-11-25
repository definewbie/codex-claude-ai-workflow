package dev.dual.ledger.file;

import java.util.LinkedHashMap;
import java.util.Map;

final class LedgerUtils {
    private LedgerUtils() {}

    static boolean isControlRepairFailure(String outcome, Map<String, Object> result) {
        var reason = text(firstNonBlank(resultValue(result, "reason"), outcome));
        return "MISSING_DUAL_CONTROL".equals(reason)
                || "INVALID_DUAL_CONTROL".equals(reason)
                || "INVALID_DECISION".equals(reason);
    }

    static Object resultValue(Map<String, Object> result, String key) {
        return result == null ? null : result.get(key);
    }

    static Object resultValueOrDefault(Map<String, Object> result, String key, Object fallback) {
        var value = resultValue(result, key);
        return value == null ? fallback : value;
    }

    static Object firstNonBlank(Object first, Object second) {
        return text(first).isBlank() ? second : first;
    }

    static String revisionReason(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        var note = text(result.get("note"));
        if (!note.isBlank()) {
            return note;
        }
        var summary = text(result.get("summary"));
        if (!summary.isBlank()) {
            return summary;
        }
        var reason = text(result.get("reason"));
        if (!reason.isBlank()) {
            return reason;
        }
        return "";
    }

    static Map<String, Object> mergedResult(Map<String, Object> existing, Map<String, Object> overrides) {
        var merged = new LinkedHashMap<String, Object>();
        if (existing != null) {
            merged.putAll(existing);
        }
        merged.putAll(overrides);
        return merged;
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static String stageId(String nodeDefinitionId) {
        var separator = nodeDefinitionId.indexOf('.');
        var prefix = separator > 0 ? nodeDefinitionId.substring(0, separator) : "workflow";
        return "stage-" + prefix;
    }

    static String nodeInstanceId(String nodeDefinitionId, int sequence) {
        return "node-" + nodeDefinitionId.replace('.', '-') + "-" + String.format("%03d", sequence);
    }

    static String nextEventId(String lastEventId) {
        return "evt-" + String.format("%06d", currentEventNumber(lastEventId) + 1);
    }

    static int currentEventNumber(String eventId) {
        if (eventId == null || !eventId.startsWith("evt-")) {
            return 0;
        }
        try {
            return Integer.parseInt(eventId.substring(4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
