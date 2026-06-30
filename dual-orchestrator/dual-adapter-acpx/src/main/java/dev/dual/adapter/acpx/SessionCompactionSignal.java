package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Typed read model consumed by session compaction policy.
 *
 * <p>The underlying source is the token metrics projection, but policy code
 * must not know the projection layout. This class is the compatibility
 * boundary until token attribution has a first-class ledger read model.</p>
 */
record SessionCompactionSignal(Map<String, Long> cachedReadTokensByAgent, Long totalCachedReadTokens) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static SessionCompactionSignal unavailable() {
        return new SessionCompactionSignal(Map.of(), null);
    }

    static SessionCompactionSignal load(Path workspaceRoot, String workstreamId) {
        if (workspaceRoot == null || workstreamId == null || workstreamId.isBlank()) {
            return unavailable();
        }
        var metricsPath = workspaceRoot.resolve(".agent")
                .resolve("workstreams")
                .resolve(workstreamId)
                .resolve("metrics")
                .resolve("workstream-metrics.json");
        if (!Files.isRegularFile(metricsPath)) {
            return unavailable();
        }
        try {
            return from(OBJECT_MAPPER.readValue(metricsPath.toFile(), MAP_TYPE));
        } catch (IOException | RuntimeException ignored) {
            return unavailable();
        }
    }

    @SuppressWarnings("unchecked")
    static SessionCompactionSignal from(Map<String, Object> doc) {
        var tokenMetrics = doc.get("tokenMetrics") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.<String, Object>of();
        var total = tokenMetrics.get("total") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.<String, Object>of();
        var byAgent = tokenMetrics.get("byAgent") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.<String, Object>of();
        var out = new java.util.LinkedHashMap<String, Long>();
        for (var entry : byAgent.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> agentMetric) {
                out.put(normalizeAgent(entry.getKey()), longValue(((Map<String, Object>) agentMetric).get("cachedReadTokens"), 0));
            }
        }
        var totalCachedRead = total.containsKey("cachedReadTokens")
                ? longValue(total.get("cachedReadTokens"), 0)
                : null;
        return new SessionCompactionSignal(Map.copyOf(out), totalCachedRead);
    }

    long cachedReadTokensFor(String agent) {
        if (!cachedReadTokensByAgent.isEmpty()) {
            return cachedReadTokensByAgent.getOrDefault(normalizeAgent(agent), 0L);
        }
        return totalCachedReadTokens == null ? 0L : totalCachedReadTokens;
    }

    boolean available() {
        return !cachedReadTokensByAgent.isEmpty() || totalCachedReadTokens != null;
    }

    private static String normalizeAgent(String agent) {
        return agent == null ? "" : agent.trim().toLowerCase(Locale.ROOT);
    }

    private static long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
