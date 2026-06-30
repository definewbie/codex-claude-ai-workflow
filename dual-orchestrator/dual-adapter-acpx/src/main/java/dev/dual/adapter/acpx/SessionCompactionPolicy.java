package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.provider.ProviderCachePolicy;
import dev.dual.core.provider.ProviderPricingCatalog;

import java.nio.file.Path;
import java.util.Locale;
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
    static final String STABILITY_CACHED_READ_THRESHOLD_ENV = "DUAL_SESSION_COMPACTION_STABILITY_CACHED_READ_TOKENS";
    static final String IMPLEMENT_STABILITY_CACHED_READ_THRESHOLD_ENV =
            "DUAL_SESSION_COMPACTION_IMPLEMENT_STABILITY_CACHED_READ_TOKENS";
    private static final long DEFAULT_STABILITY_CACHED_READ_THRESHOLD = 1_000_000L;
    private static final long DEFAULT_IMPLEMENT_STABILITY_CACHED_READ_THRESHOLD = 500_000L;
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
            var stabilityDecision = stabilityDecision(agent, nodeId, workstreamId, workspaceRoot, lookup);
            if (stabilityDecision.shouldCompact()) {
                return compactDecision(agent, nodeId, workstreamId, runId, baseSessionName, configured)
                        .map(decision -> new Decision(
                                decision.mode(),
                                true,
                                decision.sessionName(),
                                decision.segmentKey(),
                                decision.seedKind(),
                                "env:" + ENV + "=provider_cache_aware; stability_compaction; " + stabilityDecision.reason()
                        ))
                        .orElseGet(() -> notApplicable(baseDecision, "stability_compaction; " + stabilityDecision.reason()));
            }
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
                    .orElseGet(() -> notApplicable(baseDecision, cacheDecision.recommendation() + "; " + cacheDecision.reason()));
        }
        return compactDecision(agent, nodeId, workstreamId, runId, baseSessionName, configured)
                .orElseGet(() -> Decision.off(baseDecision.sessionName(), baseDecision.segmentKey()));
    }

    private static StabilityDecision stabilityDecision(
            String agent,
            String nodeId,
            String workstreamId,
            Path workspaceRoot,
            Function<String, String> lookup
    ) {
        if (workspaceRoot == null || workstreamId == null || workstreamId.isBlank()) {
            return StabilityDecision.off("metrics unavailable");
        }
        var threshold = longValue(
                lookup.apply(STABILITY_CACHED_READ_THRESHOLD_ENV),
                DEFAULT_STABILITY_CACHED_READ_THRESHOLD
        );
        var signal = SessionCompactionSignal.load(workspaceRoot, workstreamId);
        if (!signal.available()) {
            return StabilityDecision.off("metrics unavailable");
        }
        var cachedRead = signal.cachedReadTokensFor(agent);
        if (threshold > 0 && cachedRead >= threshold) {
            return new StabilityDecision(
                    true,
                    "cachedReadTokens=" + cachedRead + " >= " + threshold
            );
        }
        var implementThreshold = longValue(
                lookup.apply(IMPLEMENT_STABILITY_CACHED_READ_THRESHOLD_ENV),
                DEFAULT_IMPLEMENT_STABILITY_CACHED_READ_THRESHOLD
        );
        if (implementThreshold > 0 && isClaudeImplementBoundary(agent, nodeId) && cachedRead >= implementThreshold) {
            return new StabilityDecision(
                    true,
                    "implement boundary cachedReadTokens=" + cachedRead + " >= " + implementThreshold
            );
        }
        if (threshold <= 0 && implementThreshold <= 0) {
            return StabilityDecision.off("stability threshold disabled");
        }
        return StabilityDecision.off("cachedReadTokens=" + cachedRead + " < " + threshold);
    }

    private static Decision notApplicable(SessionScopePolicy.Decision baseDecision, String reason) {
        return new Decision(
                Mode.PROVIDER_CACHE_AWARE,
                false,
                baseDecision.sessionName(),
                baseDecision.segmentKey(),
                "",
                "env:" + ENV + "=provider_cache_aware; not_applicable_for_node; " + reason
        );
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
        var price = ProviderPricingCatalog.load(workspaceRoot, OBJECT_MAPPER, lookup)
                .priceFor(providerId, model)
                .orElse(null);
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

    private record StabilityDecision(boolean shouldCompact, String reason) {
        static StabilityDecision off(String reason) {
            return new StabilityDecision(false, reason);
        }
    }

}
