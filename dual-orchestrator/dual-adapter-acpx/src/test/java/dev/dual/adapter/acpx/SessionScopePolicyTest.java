package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionScopePolicyTest {

    private static final String BASE = "claude-main";
    private static final String CODEX_BASE = "codex-reviewer";
    private static final String WS = "ws-20260623T215939Z-RFCM";

    private static Function<String, String> env(Map<String, String> map) {
        return key -> map.get(key);
    }

    private static SessionScopePolicy.Decision resolve(String agent, String base, String scopeWire) {
        return SessionScopePolicy.resolve(agent, base, WS, "stage-design", "iter-001", "run-1",
                env(scopeWire == null ? Map.of() : Map.of(
                        "DUAL_SESSION_SCOPE_CLAUDE", scopeWire,
                        "DUAL_SESSION_SCOPE_CODEX", scopeWire)));
    }

    @Test
    void defaultIsWorkstreamAndProducesLegacyName() {
        var d = resolve("claude", BASE, null);
        assertEquals(SessionScopePolicy.Scope.WORKSTREAM, d.scope());
        // 旧行为：base + "-" + workstreamId，逐字节一致
        assertEquals(BASE + "-" + WS, d.sessionName());
        assertTrue(d.reason().contains("default"), d.reason());
    }

    @Test
    void claudeAndCodexScopesAreIndependent() {
        var lookup = env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "stage"));
        var claude = SessionScopePolicy.resolve("claude", BASE, WS, "stage-design", "iter-001", "run-1", lookup);
        var codex = SessionScopePolicy.resolve("codex", CODEX_BASE, WS, "stage-design", "iter-001", "run-1", lookup);
        assertEquals(SessionScopePolicy.Scope.STAGE, claude.scope());
        assertEquals(SessionScopePolicy.Scope.WORKSTREAM, codex.scope()); // codex unset -> default
    }

    @Test
    void stageScopeIncludesWorkstreamIdAndStage() {
        var d = resolve("claude", BASE, "stage");
        assertEquals(SessionScopePolicy.Scope.STAGE, d.scope());
        assertTrue(d.sessionName().contains(WS), d.sessionName());
        assertTrue(d.sessionName().contains("stage-design"), d.sessionName());
        assertTrue(d.segmentKey().contains("stage-design"), d.segmentKey());
    }

    @Test
    void stageIterationScopeIncludesWorkstreamStageIteration() {
        var d = SessionScopePolicy.resolve("claude", BASE, WS, "stage-plan", "iter-005", "run-9",
                env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "stage_iteration")));
        assertEquals(SessionScopePolicy.Scope.STAGE_ITERATION, d.scope());
        assertTrue(d.sessionName().contains(WS), d.sessionName());
        assertTrue(d.sessionName().contains("stage-plan"), d.sessionName());
        assertTrue(d.sessionName().contains("iter-005"), d.sessionName());
    }

    @Test
    void runScopeIncludesWorkstreamIdAndRunId() {
        var d = SessionScopePolicy.resolve("claude", BASE, WS, "stage-design", "iter-001", "run-42",
                env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "run")));
        assertEquals(SessionScopePolicy.Scope.RUN, d.scope());
        // run scope 必须同时含 workstreamId 和 runId：workstreamId 是领域边界（不是 runId 的概率唯一）
        assertTrue(d.sessionName().contains(WS), d.sessionName());
        assertTrue(d.sessionName().contains("run-42"), d.sessionName());
        assertEquals(BASE + "-" + WS + "-run-42", d.sessionName());
        assertTrue(d.segmentKey().contains(WS), d.segmentKey());
        assertTrue(d.segmentKey().contains("run-42"), d.segmentKey());
    }

    @Test
    void occurrenceBoundPolicyAlwaysUsesRunScope() {
        var first = SessionScopePolicy.occurrenceBound(
                BASE, WS, "stage-implement", "iter-003", "run-41");
        var second = SessionScopePolicy.occurrenceBound(
                BASE, WS, "stage-implement", "iter-003", "run-42");

        assertEquals(SessionScopePolicy.Scope.RUN, first.scope());
        assertEquals(BASE + "-" + WS + "-run-41", first.sessionName());
        assertEquals(BASE + "-" + WS + "-run-42", second.sessionName());
        assertNotEquals(first.sessionName(), second.sessionName());
        assertEquals("execution-policy:occurrence-bound", first.reason());
    }

    @Test
    void occurrenceBoundPolicyIgnoresLegacyWorkstreamEnvironment() {
        var legacy = SessionScopePolicy.resolve(
                "claude", BASE, WS, "stage-implement", "iter-003", "run-41",
                env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "workstream")));
        var occurrence = SessionScopePolicy.occurrenceBound(
                BASE, WS, "stage-implement", "iter-003", "run-41");

        assertEquals(SessionScopePolicy.Scope.WORKSTREAM, legacy.scope());
        assertEquals(BASE + "-" + WS, legacy.sessionName());
        assertEquals(SessionScopePolicy.Scope.RUN, occurrence.scope());
        assertEquals(BASE + "-" + WS + "-run-41", occurrence.sessionName());
    }

    @Test
    void neverEmitsBareBaseSessionName() {
        for (String wire : List.of("workstream", "stage", "stage_iteration", "run")) {
            var d = resolve("claude", BASE, wire);
            assertNotEquals(BASE, d.sessionName(), "bare base leaked for scope=" + wire);
            assertTrue(d.sessionName().startsWith(BASE + "-"), d.sessionName());
        }
    }

    @Test
    void stageScopeFallsBackWithReasonWhenStageMissing() {
        var d = SessionScopePolicy.resolve("claude", BASE, WS, "", "iter-001", "run-1",
                env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "stage")));
        assertEquals(SessionScopePolicy.Scope.WORKSTREAM, d.scope());
        assertTrue(d.reason().contains("fallback"), d.reason());
        assertTrue(d.reason().contains("stage unavailable"), d.reason());
    }

    @Test
    void stageIterationFallsBackWithReasonWhenIterationMissing() {
        var d = SessionScopePolicy.resolve("claude", BASE, WS, "stage-design", "", "run-1",
                env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "stage_iteration")));
        assertEquals(SessionScopePolicy.Scope.WORKSTREAM, d.scope());
        assertTrue(d.reason().contains("fallback"), d.reason());
    }

    @Test
    void invalidEnvFallsBackWithReasonNotSilent() {
        var d = SessionScopePolicy.resolve("claude", BASE, WS, "stage-design", "iter-001", "run-1",
                env(Map.of("DUAL_SESSION_SCOPE_CLAUDE", "bogus")));
        // 非法 env：fallback 到 workstream，但必须给出明确 reason（不能静默变成 workstream）
        assertEquals(SessionScopePolicy.Scope.WORKSTREAM, d.scope());
        assertEquals(BASE + "-" + WS, d.sessionName());
        assertTrue(d.reason().contains("invalid"), d.reason());
        assertTrue(d.reason().contains("bogus"), d.reason());
    }
}
