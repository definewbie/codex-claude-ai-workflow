package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionHealthTest {
    private final AcpxFailureSignalCatalog catalog = new AcpxFailureSignalCatalog();

    @Test
    void recoveredReconnectDoesNotBecomeFailure() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        health.observe(AgentLifecycleEvent.streamLine(1, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\"}"));
        health.observe(AgentLifecycleEvent.streamLine(2, "{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"abc\"},\"id\":1}"));
        health.observe(AgentLifecycleEvent.streamLine(3, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/prompt\"}"));
        health.observe(AgentLifecycleEvent.stdoutLine(4, "[thinking] continuing after reconnect"));

        var assessment = health.assess(Duration.ofSeconds(5));

        assertEquals(AgentExecutionHealth.Status.HEALTHY, assessment.status());
        assertFalse(assessment.failure().recoverable());
    }

    @Test
    void staleReconnectIsNotConsumedAgainWithoutNewSignal() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        assertEquals(AgentExecutionHealth.Status.RECOVERING, health.assess(Duration.ofSeconds(1)).status());
        assertEquals(AgentExecutionHealth.Status.RECOVERABLE_FAILURE, health.assess(Duration.ofSeconds(31)).status());
        health.observe(AgentLifecycleEvent.streamLine(1, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"session/new\"}"));
        health.observe(AgentLifecycleEvent.streamLine(2, "{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"new\"},\"id\":4}"));

        var assessment = health.assess(Duration.ofSeconds(60));

        assertEquals(AgentExecutionHealth.Status.HEALTHY, assessment.status());
        assertFalse(assessment.failure().recoverable());
    }

    @Test
    void unrecoveredReconnectBecomesRecoverableFailureAfterGrace() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(10));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));

        var assessment = health.assess(Duration.ofSeconds(11));

        assertEquals(AgentExecutionHealth.Status.RECOVERABLE_FAILURE, assessment.status());
        assertEquals(AcpxFailureReason.AGENT_RECONNECT.code(), assessment.failure().reason());
        assertTrue(assessment.failure().interruptRunningProcess());
    }

    @Test
    void currentAgentActivitySupersedesReconnect() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(10));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        health.observe(AgentLifecycleEvent.streamLine(1, "{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"update\":{\"sessionUpdate\":\"agent_thought_chunk\",\"content\":{\"text\":\"working\"}}}}"));

        var assessment = health.assess(Duration.ofSeconds(20));

        assertEquals(AgentExecutionHealth.Status.HEALTHY, assessment.status());
        assertFalse(assessment.failure().recoverable());
    }

    @Test
    void providerFailureOverridesReconnectAndActivity() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        health.observe(AgentLifecycleEvent.streamLine(1, "{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"new\"},\"id\":1}"));
        health.observe(AgentLifecycleEvent.streamLine(2, "{\"jsonrpc\":\"2.0\",\"method\":\"session/prompt/failed\",\"error\":\"Internal error: API Error: 529 [1305][该模型当前访问量过大，请您稍后再试]\"}"));

        var assessment = health.assess(Duration.ZERO);

        assertEquals(AgentExecutionHealth.Status.RECOVERABLE_FAILURE, assessment.status());
        assertEquals(AcpxFailureReason.AGENT_OVERLOADED.code(), assessment.failure().reason());
        assertFalse(assessment.failure().interruptRunningProcess());
    }

    @Test
    void completeTerminalContractWinsOverReconnect() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(1));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        health.observe(AgentLifecycleEvent.terminalContractSeen(1));

        var assessment = health.assess(Duration.ofSeconds(10));

        assertEquals(AgentExecutionHealth.Status.TERMINAL_SUCCESS, assessment.status());
    }
}
