package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCompactionPolicyTest {
    @TempDir
    Path tempDir;

    private final SessionCompactionPolicy policy = new SessionCompactionPolicy();
    private final SessionScopePolicy.Decision base =
            new SessionScopePolicy.Decision("claude-main-ws-1", SessionScopePolicy.Scope.WORKSTREAM, "ws:ws-1", "default:workstream");

    @Test
    void defaultsOffAndPreservesBaseSession() {
        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, ignored -> "");

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertEquals("ws:ws-1", decision.segmentKey());
        assertEquals("off", decision.mode().wire());
    }

    @Test
    void implementBoundaryCompactsOnlyClaudeImplementFamily() {
        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, name -> "implement_boundary");

        assertTrue(decision.enabled());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertEquals("ws:ws-1/compact:implement.claude_implement", decision.segmentKey());
        assertEquals("agent_handoff", decision.seedKind());

        var skipped = policy.decide("codex", "acceptance.codex_verifier_review", "ws-1", "run-2",
                "codex-reviewer", base, name -> "implement_boundary");
        assertFalse(skipped.enabled());
    }

    @Test
    void implementBoundaryIsDeterministicAndDoesNotDependOnPricingOrPriorMetrics() {
        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "implement_boundary";
                    default -> "";
                });

        assertTrue(decision.enabled());
        assertEquals("implement_boundary", decision.mode().wire());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertEquals("agent_handoff", decision.seedKind());
        assertTrue(decision.reason().contains("implement boundary compaction"));
    }

    @Test
    void codexIncrementalUsesRunScopedCompactSession() {
        var decision = policy.decide("codex", "acceptance.codex_verifier_review", "ws-1", "run-2",
                "codex-reviewer", base, name -> "codex_incremental");

        assertTrue(decision.enabled());
        assertEquals("codex-reviewer-ws-1-compact-run-2", decision.sessionName());
        assertEquals("ws:ws-1/compact-run:run-2", decision.segmentKey());
        assertEquals("review_evidence_brief", decision.seedKind());
    }

    @Test
    void providerCacheAwarePreservesSessionWhenCacheHitIsCheap() throws Exception {
        writePricing("deepseek", "deepseek-pro-v4", "0.003625", "0.435");
        writeMetrics("ws-1", 1000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    case "DUAL_PROVIDER_ID_CLAUDE" -> "deepseek";
                    case "DUAL_MODEL_CLAUDE" -> "deepseek-pro-v4";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("preserve_session"));
    }

    @Test
    void providerCacheAwareCompactsForStabilityWhenCachedTranscriptIsLarge() throws Exception {
        writePricing("zhipu", "glm-5.2", "0", "0");
        writeMetrics("ws-1", 1_500_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    case "DUAL_PROVIDER_ID_CLAUDE" -> "zhipu";
                    case "DUAL_MODEL_CLAUDE" -> "glm-5.2";
                    default -> "";
                });

        assertTrue(decision.enabled());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertEquals("agent_handoff", decision.seedKind());
        assertTrue(decision.reason().contains("stability_compaction"));
        assertTrue(decision.reason().contains("cachedReadTokens=1500000"));
    }

    @Test
    void providerCacheAwareCompactsForStabilityEvenWhenPricingIsUnavailable() throws Exception {
        writeMetrics("ws-1", 1_500_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertTrue(decision.enabled());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertEquals("agent_handoff", decision.seedKind());
        assertTrue(decision.reason().contains("stability_compaction"));
        assertTrue(decision.reason().contains("cachedReadTokens=1500000"));
    }

    @Test
    void providerCacheAwareCompactsClaudeImplementAtSoftStabilityThreshold() throws Exception {
        writeMetrics("ws-1", 600_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertTrue(decision.enabled());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertEquals("agent_handoff", decision.seedKind());
        assertTrue(decision.reason().contains("implement boundary"));
        assertTrue(decision.reason().contains("cachedReadTokens=600000"));
    }

    @Test
    void providerCacheAwareDoesNotApplyImplementSoftThresholdToOtherNodes() throws Exception {
        writeMetrics("ws-1", 600_000);

        var decision = policy.decide("claude", "design.claude_design", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("measure_only"));
    }

    @Test
    void providerCacheAwareKeepsObservableReasonWhenHardThresholdHitsNonTargetNode() throws Exception {
        writeAgentMetrics("ws-1", 1_500_000, 0);

        var decision = policy.decide("claude", "design.claude_design", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("provider_cache_aware", decision.mode().wire());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("not_applicable_for_node"));
        assertTrue(decision.reason().contains("stability_compaction"));
    }

    @Test
    void providerCacheAwareUsesCurrentAgentCachedReadNotWorkstreamTotal() throws Exception {
        writeAgentMetrics("ws-1", 100_000, 1_500_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("measure_only"));
    }

    @Test
    void providerCacheAwareDoesNotFallbackToTotalWhenCurrentAgentMetricIsMissing() throws Exception {
        writeCodexOnlyMetrics("ws-1", 1_500_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("measure_only"));
    }

    @Test
    void providerCacheAwareImplementSoftThresholdWorksWhenHardThresholdDisabled() throws Exception {
        writeMetrics("ws-1", 600_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    case SessionCompactionPolicy.STABILITY_CACHED_READ_THRESHOLD_ENV -> "0";
                    default -> "";
                });

        assertTrue(decision.enabled());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertTrue(decision.reason().contains("implement boundary"));
    }

    @Test
    void providerCacheAwareStillMeasuresTokenOnlyWhenStabilityThresholdNotReached() throws Exception {
        writePricing("zhipu", "glm-5.2", "0", "0");
        writeMetrics("ws-1", 400_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    case "DUAL_PROVIDER_ID_CLAUDE" -> "zhipu";
                    case "DUAL_MODEL_CLAUDE" -> "glm-5.2";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("measure_only"));
    }

    @Test
    void providerCacheAwareUsesClaudeCompatibleRuntimeMetadataWhenExplicitProviderIsUnset() throws Exception {
        writePricing("zhipu", "glm-5.2", "0", "0");
        writeMetrics("ws-1", 400_000);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    case "ANTHROPIC_BASE_URL" -> "https://open.bigmodel.cn/api/anthropic";
                    case "ANTHROPIC_DEFAULT_SONNET_MODEL" -> "glm-5.2";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertTrue(decision.reason().contains("subscription_token_only"));
        assertFalse(decision.reason().contains("provider/model pricing unavailable"));
    }

    @Test
    void providerCacheAwareIsNotADeterministicCompactionVerificationMode() throws Exception {
        writeMetrics("ws-1", 488_320);

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    default -> "";
                });

        assertFalse(decision.enabled());
        assertEquals("provider_cache_aware", decision.mode().wire());
        assertEquals("claude-main-ws-1", decision.sessionName());
        assertEquals("", decision.seedKind());
        assertTrue(decision.reason().contains("measure_only"));
    }

    @Test
    void providerCacheAwareCompactsWhenCacheHitIsExpensive() throws Exception {
        writePricing("expensive", "model-x", "0.75", "1.00");

        var decision = policy.decide("claude", "implement.claude_implement", "ws-1", "run-1",
                "claude-main", base, tempDir, name -> switch (name) {
                    case SessionCompactionPolicy.ENV -> "provider_cache_aware";
                    case "DUAL_PROVIDER_ID_CLAUDE" -> "expensive";
                    case "DUAL_MODEL_CLAUDE" -> "model-x";
                    default -> "";
                });

        assertTrue(decision.enabled());
        assertEquals("claude-main-ws-1-compact-implement.claude_implement", decision.sessionName());
        assertEquals("agent_handoff", decision.seedKind());
    }

    private void writePricing(String providerId, String model, String hit, String miss) throws Exception {
        var agentDir = tempDir.resolve(".agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("provider-pricing.json"), """
                {
                  "schemaVersion": 1,
                  "models": [{
                    "providerId": "%s",
                    "model": "%s",
                    "currency": "USD",
                    "inputCacheHitPer1M": "%s",
                    "inputCacheMissPer1M": "%s",
                    "outputPer1M": "1.00"
                  }]
                }
                """.formatted(providerId, model, hit, miss));
    }

    private void writeMetrics(String workstreamId, long cachedReadTokens) throws Exception {
        var metricsDir = tempDir.resolve(".agent")
                .resolve("workstreams")
                .resolve(workstreamId)
                .resolve("metrics");
        Files.createDirectories(metricsDir);
        Files.writeString(metricsDir.resolve("workstream-metrics.json"), """
                {
                  "schemaVersion": 1,
                  "tokenMetrics": {
                    "total": {
                      "cachedReadTokens": %d
                    }
                  }
                }
                """.formatted(cachedReadTokens));
    }

    private void writeAgentMetrics(String workstreamId, long claudeCachedReadTokens, long codexCachedReadTokens) throws Exception {
        var metricsDir = tempDir.resolve(".agent")
                .resolve("workstreams")
                .resolve(workstreamId)
                .resolve("metrics");
        Files.createDirectories(metricsDir);
        Files.writeString(metricsDir.resolve("workstream-metrics.json"), """
                {
                  "schemaVersion": 1,
                  "tokenMetrics": {
                    "total": {
                      "cachedReadTokens": %d
                    },
                    "byAgent": {
                      "claude": {
                        "cachedReadTokens": %d
                      },
                      "codex": {
                        "cachedReadTokens": %d
                      }
                    }
                  }
                }
                """.formatted(claudeCachedReadTokens + codexCachedReadTokens, claudeCachedReadTokens, codexCachedReadTokens));
    }

    private void writeCodexOnlyMetrics(String workstreamId, long codexCachedReadTokens) throws Exception {
        var metricsDir = tempDir.resolve(".agent")
                .resolve("workstreams")
                .resolve(workstreamId)
                .resolve("metrics");
        Files.createDirectories(metricsDir);
        Files.writeString(metricsDir.resolve("workstream-metrics.json"), """
                {
                  "schemaVersion": 1,
                  "tokenMetrics": {
                    "total": {
                      "cachedReadTokens": %d
                    },
                    "byAgent": {
                      "codex": {
                        "cachedReadTokens": %d
                      }
                    }
                  }
                }
                """.formatted(codexCachedReadTokens, codexCachedReadTokens));
    }
}
