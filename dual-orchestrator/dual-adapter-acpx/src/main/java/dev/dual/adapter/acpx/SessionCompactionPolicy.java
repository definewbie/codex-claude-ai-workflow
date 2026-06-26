package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.provider.ProviderCachePolicy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Experimental session transcript compaction policy.
 *
 * <p>Default is off. When enabled, it changes only adapter execution session
 * naming and prompt seed inputs; workflow state and default session scope remain
 * unchanged.</p>
 */
final class SessionCompactionPolicy {
    static final String ENV = "DUAL_SESSION_COMPACTION_EXPERIMENT";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    enum Mode {
        OFF("off"),
        IMPLEMENT_BOUNDARY("implement_boundary"),
        CODEX_INCREMENTAL("codex_incremental"),
        BOTH("both"),
        PROVIDER_CACHE_AWARE("provider_cache_aware");

        private final String wire;

        Mode(String wire) {
            this.wire = wire;
        }

        String wire() {
            return wire;
        }
    }

    record Decision(
            Mode mode,
            boolean enabled,
            String sessionName,
            String segmentKey,
            String seedKind,
            String reason
    ) {
        static Decision off(String sessionName, String segmentKey) {
            return new Decision(Mode.OFF, false, sessionName, segmentKey, "", "default:off");
        }
    }

    Decision decide(
            String agent,
            String nodeId,
            String workstreamId,
            String runId,
            String baseSessionName,
            SessionScopePolicy.Decision baseDecision
    ) {
        return decide(agent, nodeId, workstreamId, runId, baseSessionName, baseDecision, null, SessionCompactionPolicy::lookup);
    }

    Decision decide(
            String agent,
            String nodeId,
            String workstreamId,
            String runId,
            String baseSessionName,
            SessionScopePolicy.Decision baseDecision,
            Path workspaceRoot
    ) {
        return decide(agent, nodeId, workstreamId, runId, baseSessionName, baseDecision, workspaceRoot, SessionCompactionPolicy::lookup);
    }

    Decision decide(
            String agent,
            String nodeId,
            String workstreamId,
            String runId,
            String baseSessionName,
            SessionScopePolicy.Decision baseDecision,
            Function<String, String> lookup
    ) {
        return decide(agent, nodeId, workstreamId, runId, baseSessionName, baseDecision, null, lookup);
    }

    Decision decide(
            String agent,
            String nodeId,
            String workstreamId,
            String runId,
            String baseSessionName,
            SessionScopePolicy.Decision baseDecision,
            Path workspaceRoot,
            Function<String, String> lookup
    ) {
        var configured = mode(lookup.apply(ENV));
        if (configured == Mode.OFF) {
            return Decision.off(baseDecision.sessionName(), baseDecision.segmentKey());
        }
        if (configured == Mode.PROVIDER_CACHE_AWARE) {
            var cacheDecision = providerCacheDecision(agent, workspaceRoot, lookup);
            if (!"consider_compaction".equals(cacheDecision.recommendation())) {
                return new Decision(
                        Mode.PROVIDER_CACHE_AWARE,
                        false,
                        baseDecision.sessionName(),
                        baseDecision.segmentKey(),
                        "",
                        "env:" + ENV + "=provider_cache_aware; " + cacheDecision.recommendation() + "; " + cacheDecision.reason()
                );
            }
            return compactDecision(agent, nodeId, workstreamId, runId, baseSessionName, configured)
                    .orElseGet(() -> Decision.off(baseDecision.sessionName(), baseDecision.segmentKey()));
        }
        return compactDecision(agent, nodeId, workstreamId, runId, baseSessionName, configured)
                .orElseGet(() -> Decision.off(baseDecision.sessionName(), baseDecision.segmentKey()));
    }

    private static java.util.Optional<Decision> compactDecision(
            String agent,
            String nodeId,
            String workstreamId,
            String runId,
            String baseSessionName,
            Mode configured
    ) {
        if (isClaudeImplementBoundary(agent, nodeId) &&
                (configured == Mode.IMPLEMENT_BOUNDARY || configured == Mode.BOTH || configured == Mode.PROVIDER_CACHE_AWARE)) {
            var suffix = SessionScopePolicy.safe(workstreamId) + "-compact-" + SessionScopePolicy.safe(nodeId);
            return java.util.Optional.of(new Decision(
                    configured,
                    true,
                    baseSessionName + "-" + suffix,
                    "ws:" + workstreamId + "/compact:" + nodeId,
                    "agent_handoff",
                    "env:" + ENV + "=" + configured.wire() + "; implement boundary compaction"
            ));
        }
        if (isCodexReview(agent, nodeId) &&
                (configured == Mode.CODEX_INCREMENTAL || configured == Mode.BOTH || configured == Mode.PROVIDER_CACHE_AWARE)) {
            var suffix = SessionScopePolicy.safe(workstreamId) + "-compact-" + SessionScopePolicy.safe(runId);
            return java.util.Optional.of(new Decision(
                    configured,
                    true,
                    baseSessionName + "-" + suffix,
                    "ws:" + workstreamId + "/compact-run:" + runId,
                    "review_evidence_brief",
                    "env:" + ENV + "=" + configured.wire() + "; codex incremental review compaction"
            ));
        }
        return java.util.Optional.empty();
    }

    private static boolean isClaudeImplementBoundary(String agent, String nodeId) {
        return "claude".equals(agent) && (
                "implement.claude_implement".equals(nodeId)
                        || "implement.claude_self_test_report".equals(nodeId)
                        || "stage.claude_handoff".equals(nodeId)
        );
    }

    private static boolean isCodexReview(String agent, String nodeId) {
        return "codex".equals(agent) && nodeId != null && nodeId.contains("codex");
    }

    private static Mode mode(String raw) {
        var normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "implement_boundary" -> Mode.IMPLEMENT_BOUNDARY;
            case "codex_incremental" -> Mode.CODEX_INCREMENTAL;
            case "both" -> Mode.BOTH;
            case "provider_cache_aware" -> Mode.PROVIDER_CACHE_AWARE;
            default -> Mode.OFF;
        };
    }

    private static ProviderCachePolicy.Decision providerCacheDecision(
            String agent,
            Path workspaceRoot,
            Function<String, String> lookup
    ) {
        var agentKey = agent == null ? "" : agent.trim().toUpperCase(Locale.ROOT);
        var providerId = nonBlank(lookup.apply("DUAL_PROVIDER_ID_" + agentKey), lookup.apply("DUAL_PROVIDER_ID"));
        var model = nonBlank(lookup.apply("DUAL_MODEL_" + agentKey), lookup.apply("DUAL_MODEL"));
        if (providerId.isBlank() || model.isBlank() || workspaceRoot == null) {
            return new ProviderCachePolicy.Decision("measure_only", "", "provider/model pricing unavailable");
        }
        var price = ProviderPricing.load(workspaceRoot, lookup).priceFor(providerId, model);
        if (price == null) {
            return new ProviderCachePolicy.Decision("measure_only", "", "provider/model pricing unavailable");
        }
        return ProviderCachePolicy.decide(price.inputCacheHitPer1M(), price.inputCacheMissPer1M());
    }

    private static String lookup(String name) {
        var property = System.getProperty(name);
        return property == null || property.isBlank() ? System.getenv(name) : property;
    }

    private static String nonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? (fallback == null ? "" : fallback.trim()) : first.trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private record ProviderPrice(String providerId, String model, BigDecimal inputCacheHitPer1M, BigDecimal inputCacheMissPer1M) {
        boolean matches(String candidateProvider, String candidateModel) {
            return providerId.equalsIgnoreCase(candidateProvider)
                    && (model.equals(candidateModel)
                    || (model.endsWith("*") && candidateModel.startsWith(model.substring(0, model.length() - 1))));
        }
    }

    private record ProviderPricing(List<ProviderPrice> prices) {
        static ProviderPricing load(Path workspaceRoot, Function<String, String> lookup) {
            var configured = lookup.apply("DUAL_PROVIDER_PRICING_PATH");
            var path = configured == null || configured.isBlank()
                    ? workspaceRoot.resolve(".agent").resolve("provider-pricing.json")
                    : Path.of(configured);
            if (!Files.isRegularFile(path)) {
                return new ProviderPricing(List.of());
            }
            try {
                var doc = OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
                var out = new ArrayList<ProviderPrice>();
                for (var item : mapList(doc.get("models"))) {
                    var providerId = text(item.get("providerId"));
                    var model = text(item.get("model"));
                    if (providerId.isBlank() || model.isBlank()) {
                        continue;
                    }
                    out.add(new ProviderPrice(
                            providerId,
                            model,
                            decimal(item.get("inputCacheHitPer1M")),
                            decimal(item.get("inputCacheMissPer1M"))
                    ));
                }
                return new ProviderPricing(out);
            } catch (IOException ignored) {
                return new ProviderPricing(List.of());
            }
        }

        ProviderPrice priceFor(String providerId, String model) {
            for (var price : prices) {
                if (price.matches(providerId, model)) {
                    return price;
                }
            }
            return null;
        }
    }
}
