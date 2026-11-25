package dev.dual.adapter.acpx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Classifies observed tool usage into capability kinds declared by the
 * orchestrator capability catalog. Provider names come from the catalog; this
 * class does not know about concrete tools such as rtk/codegraph/gitnexus.
 */
final class ToolUsageCapabilityClassifier {
    private ToolUsageCapabilityClassifier() {
    }

    static List<String> classify(AcpxToolUsage toolUsage, Map<String, Object> capabilityCatalog) {
        return classifyUsages(toolUsage, capabilityCatalog).stream()
                .map(item -> text(item.get("kind")))
                .filter(kind -> !kind.isBlank())
                .distinct()
                .toList();
    }

    static List<Map<String, Object>> classifyUsages(AcpxToolUsage toolUsage, Map<String, Object> capabilityCatalog) {
        if (toolUsage == null) {
            return List.of();
        }
        var usages = new ArrayList<Map<String, Object>>();
        for (var capability : capabilities(capabilityCatalog)) {
            var kind = text(capability.get("kind"));
            var provider = text(capability.get("provider"));
            if (kind.isBlank() || provider.isBlank()) {
                continue;
            }
            var source = providerUsed(toolUsage, provider);
            if (!source.isBlank()) {
                usages.add(Map.of(
                        "kind", kind,
                        "provider", provider,
                        "source", source
                ));
                continue;
            }
        }
        return List.copyOf(usages);
    }

    private static String providerUsed(AcpxToolUsage toolUsage, String provider) {
        var normalizedProvider = normalizeToken(provider);
        if (normalizedProvider.isBlank()) {
            return "";
        }
        for (var toolId : toolUsage.toolIds()) {
            if (normalizeToken(toolId).equals(normalizedProvider)) {
                return "tool_id";
            }
        }
        for (var executable : toolUsage.commandExecutables()) {
            if (normalizeToken(executable).equals(normalizedProvider)) {
                return "command_executable";
            }
        }
        return "";
    }

    private static String normalizeToken(Object value) {
        var text = text(value).toLowerCase(Locale.ROOT);
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        var slash = text.lastIndexOf('/');
        return slash >= 0 ? text.substring(slash + 1) : text;
    }

    private static List<Map<String, Object>> capabilities(Map<String, Object> catalog) {
        if (catalog == null || !(catalog.get("capabilities") instanceof List<?> raw)) {
            return List.of();
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var item : raw) {
            if (item instanceof Map<?, ?> map) {
                var copy = new java.util.LinkedHashMap<String, Object>();
                for (var entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        copy.put(key, entry.getValue());
                    }
                }
                result.add(copy);
            }
        }
        return List.copyOf(result);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
