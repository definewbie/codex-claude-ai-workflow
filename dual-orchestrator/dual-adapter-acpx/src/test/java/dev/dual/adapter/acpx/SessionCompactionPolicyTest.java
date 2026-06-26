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
}
