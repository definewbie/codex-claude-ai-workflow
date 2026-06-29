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
 * P1 emits WARN/PASS read-model evidence. P2 may mark hard exploration
 * boundary violations as BLOCK when the typed plan opts into block mode.
 */
public final class ExplorationPolicyEvaluator {
    private ExplorationPolicyEvaluator() {
    }

    public static Map<String, Object> evaluate(Map<String, Object> explorationPlan, List<String> actualReadFiles) {
        var actualCapabilities = actualReadFiles == null || actualReadFiles.isEmpty()
                ? List.<String>of()
                : List.of("direct_file_read");
        return evaluate(explorationPlan, actualReadFiles, actualCapabilities, List.of());
    }

    public static Map<String, Object> evaluate(
            Map<String, Object> explorationPlan,
            List<String> actualReadFiles,
            List<String> actualCapabilityKinds,
            List<String> scopeExpansionFiles
    ) {
        return evaluate(explorationPlan, actualReadFiles, actualCapabilityKinds, scopeExpansionFiles, Map.of());
    }

    public static Map<String, Object> evaluate(
            Map<String, Object> explorationPlan,
            List<String> actualReadFiles,
            List<String> actualCapabilityKinds,
            List<String> scopeExpansionFiles,
            Map<String, String> actualReadFileKinds
    ) {
        return evaluate(explorationPlan, actualReadFiles, actualCapabilityKinds, scopeExpansionFiles,
                actualReadFileKinds, List.of());
    }

    public static Map<String, Object> evaluate(
            Map<String, Object> explorationPlan,
            List<String> actualReadFiles,
            List<String> actualCapabilityKinds,
            List<String> scopeExpansionFiles,
            Map<String, String> actualReadFileKinds,
            List<Map<String, Object>> actualCapabilityUsages
    ) {
        var actualReads = normalizedList(actualReadFiles);
        var allowedReads = normalizedList(list(explorationPlan == null ? null : explorationPlan.get("allowedDirectReadFiles")));
        var preferredCapabilities = normalizedList(list(explorationPlan == null ? null : explorationPlan.get("preferredCapabilityKinds")));
        var capabilityUsages = normalizedCapabilityUsages(actualCapabilityUsages);
        var actualCapabilities = capabilityUsages.isEmpty()
                ? normalizedList(actualCapabilityKinds)
                : normalizedList(capabilityUsages.stream().map(item -> text(item.get("kind"))).toList());
        var requestedScopeFiles = normalizedList(scopeExpansionFiles);
        var maxDirectReads = intValue(explorationPlan == null ? null : explorationPlan.get("maxDirectReadFiles"));
        var stageId = text(explorationPlan == null ? null : explorationPlan.get("stageId"));
        var failureMode = text(explorationPlan == null ? null : explorationPlan.get("failureMode"));
        var blockMode = "block".equals(failureMode);
        var violations = new ArrayList<Map<String, Object>>();

        if (maxDirectReads == 0 && !actualReads.isEmpty()) {
            violations.add(violation(
                    "DIRECT_READ_FORBIDDEN",
                    "The exploration plan forbids direct Read in this stage.",
                    Map.of("actualDirectReadFiles", actualReads),
                    severity(blockMode)
            ));
        } else if (maxDirectReads > 0 && actualReads.size() > maxDirectReads && requestedScopeFiles.isEmpty()) {
            violations.add(violation(
                    "DIRECT_READ_BUDGET_EXCEEDED",
                    "Actual direct Read count exceeds the exploration plan budget.",
                    Map.of("maxDirectReadFiles", maxDirectReads, "actualDirectReadFiles", actualReads.size()),
                    severity(blockMode)
            ));
        }

        if (!allowedReads.isEmpty()) {
            var outside = actualReads.stream()
                    .filter(path -> !allowed(path, allowedReads))
                    .filter(path -> !allowed(path, requestedScopeFiles))
                    .toList();
            if (!outside.isEmpty()) {
                violations.add(violation(
                        "READ_OUTSIDE_EXPLORATION_SCOPE",
                        "Direct Read used files outside the exploration plan allowed set.",
                        Map.of("outsideReadFiles", outside),
                        severity(blockMode)
                ));
                if (requestedScopeFiles.isEmpty()) {
                    violations.add(violation(
                            "SCOPE_EXPANSION_REQUEST_MISSING",
                            "Direct Read used files outside the exploration plan without a typed scope_expansion_request.",
                            Map.of("outsideReadFiles", outside),
                            severity(blockMode)
                    ));
                }
            }
        }

        if (shouldWarnPreferredCapability(preferredCapabilities, actualCapabilities, actualReads)) {
            var missing = preferredCapabilities.stream()
                    .filter(kind -> !"direct_file_read".equals(kind))
                    .filter(kind -> !actualCapabilities.contains(kind))
                    .toList();
            violations.add(violation(
                    "PREFERRED_CAPABILITY_NOT_USED",
                    "Preferred exploration capability was available but direct file reads were used without it.",
                    Map.of("missingCapabilityKinds", missing, "actualCapabilityKinds", actualCapabilities),
                    "warn"
            ));
        }

        var readKinds = normalizedMap(actualReadFileKinds);
        if ("intake".equals(stageId)) {
            var implementationFiles = actualReads.stream()
                    .filter(path -> ExplorationReadKindPolicy.isImplementationLevelRead(stageId, readKinds.get(path)))
                    .filter(path -> !allowed(path, requestedScopeFiles))
                    .toList();
            if (!implementationFiles.isEmpty()) {
                violations.add(violation(
                        "IMPLEMENTATION_LEVEL_EXPLORATION_IN_INTAKE",
                        "Intake performed implementation-level repository exploration.",
                        Map.of("implementationLevelReadFiles", implementationFiles),
                        severity(blockMode)
                ));
            }
        }

        var hasBlockViolation = violations.stream().anyMatch(item -> "block".equals(text(item.get("severity"))));
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("artifactType", "exploration_policy_result");
        result.put("mode", blockMode ? "block" : "warn");
        result.put("status", violations.isEmpty() ? "pass" : (hasBlockViolation ? "failed" : "warn"));
        result.put("violations", List.copyOf(violations));
        result.put("actualDirectReadFiles", actualReads);
        result.put("actualReadFileKinds", readKinds);
        result.put("actualCapabilityKinds", actualCapabilities);
        result.put("actualCapabilityUsages", capabilityUsages);
        result.put("scopeExpansionFiles", requestedScopeFiles);
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private static Map<String, Object> violation(String type, String message, Map<String, Object> details, String severity) {
        var item = new LinkedHashMap<String, Object>();
        item.put("type", type);
        item.put("severity", text(severity).isBlank() ? "warn" : text(severity));
        item.put("message", message);
        item.put("details", details);
        return item;
    }

    private static String severity(boolean blockMode) {
        return blockMode ? "block" : "warn";
    }

    private static boolean allowed(String path, List<String> allowedReads) {
        for (var allowed : allowedReads) {
            if (path.equals(allowed) || path.startsWith(allowed + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldWarnPreferredCapability(
            List<String> preferredCapabilities,
            List<String> actualCapabilities,
            List<String> actualReads
    ) {
        if (actualReads.size() <= 1 || preferredCapabilities.isEmpty()) {
            return false;
        }
        var nonDirectPreferred = preferredCapabilities.stream()
                .filter(kind -> !"direct_file_read".equals(kind))
                .toList();
        return !nonDirectPreferred.isEmpty()
                && actualCapabilities.contains("direct_file_read")
                && nonDirectPreferred.stream().noneMatch(actualCapabilities::contains);
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

    private static Map<String, String> normalizedMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, String>();
        for (var entry : values.entrySet()) {
            var key = normalize(entry.getKey());
            var value = text(entry.getValue());
            if (!key.isBlank() && !value.isBlank()) {
                result.put(key, value);
            }
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> normalizedCapabilityUsages(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : values) {
            if (item == null) {
                continue;
            }
            var kind = text(item.get("kind"));
            var provider = text(item.get("provider"));
            var source = text(item.get("source"));
            if (kind.isBlank() || provider.isBlank() || source.isBlank()) {
                continue;
            }
            var normalized = new LinkedHashMap<String, Object>();
            normalized.put("kind", kind);
            normalized.put("provider", provider);
            normalized.put("source", source);
            result.add(normalized);
        }
        return List.copyOf(result);
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
