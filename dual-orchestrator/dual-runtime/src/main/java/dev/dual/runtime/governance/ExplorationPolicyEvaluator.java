package dev.dual.runtime.governance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Compares execution facts against an exploration plan.
 *
 * P1 emits WARN/PASS read-model evidence only. It must not route workflow
 * state or reinterpret agent prose.
 */
public final class ExplorationPolicyEvaluator {
    private ExplorationPolicyEvaluator() {
    }

    public static Map<String, Object> evaluate(Map<String, Object> explorationPlan, List<String> actualReadFiles) {
        var actualReads = normalizedList(actualReadFiles);
        var allowedReads = normalizedList(list(explorationPlan == null ? null : explorationPlan.get("allowedDirectReadFiles")));
        var maxDirectReads = intValue(explorationPlan == null ? null : explorationPlan.get("maxDirectReadFiles"));
        var violations = new ArrayList<Map<String, Object>>();

        if (maxDirectReads > 0 && actualReads.size() > maxDirectReads) {
            violations.add(violation(
                    "DIRECT_READ_BUDGET_EXCEEDED",
                    "Actual direct Read count exceeds the exploration plan budget.",
                    Map.of("maxDirectReadFiles", maxDirectReads, "actualDirectReadFiles", actualReads.size())
            ));
        }

        if (!allowedReads.isEmpty()) {
            var outside = actualReads.stream()
                    .filter(path -> !allowed(path, allowedReads))
                    .toList();
            if (!outside.isEmpty()) {
                violations.add(violation(
                        "READ_OUTSIDE_EXPLORATION_SCOPE",
                        "Direct Read used files outside the exploration plan allowed set.",
                        Map.of("outsideReadFiles", outside)
                ));
            }
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("artifactType", "exploration_policy_result");
        result.put("mode", "warn");
        result.put("status", violations.isEmpty() ? "pass" : "warn");
        result.put("violations", List.copyOf(violations));
        result.put("actualDirectReadFiles", actualReads);
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private static Map<String, Object> violation(String type, String message, Map<String, Object> details) {
        var item = new LinkedHashMap<String, Object>();
        item.put("type", type);
        item.put("severity", "warn");
        item.put("message", message);
        item.put("details", details);
        return item;
    }

    private static boolean allowed(String path, List<String> allowedReads) {
        for (var allowed : allowedReads) {
            if (path.equals(allowed) || path.startsWith(allowed + "/")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object value) {
        if (value instanceof List<?> raw) {
            return raw.stream().map(ExplorationPolicyEvaluator::text).toList();
        }
        return List.of();
    }

    private static List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new LinkedHashSet<String>();
        for (var value : values) {
            var normalized = normalize(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        var text = text(value);
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
