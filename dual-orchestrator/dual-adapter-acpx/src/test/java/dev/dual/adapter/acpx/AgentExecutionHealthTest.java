package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionHealthTest {
    private final AcpxFailureSignalCatalog catalog = new AcpxFailureSignalCatalog();

    private AgentLifecycleEvent streamEvent(long sequence, String line) {
        return new AcpxSessionStreamProjector()
                .accept(sequence, AcpxStreamWireLine.parse(line))
                .orElseThrow();
    }

    @Test
    void recoveredReconnectDoesNotBecomeFailure() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        var stream = new AcpxSessionStreamProjector();
        health.observe(stream.accept(1, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\"}")).orElseThrow());
        health.observe(stream.accept(2, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"abc\"},\"id\":1}")).orElseThrow());
        health.observe(stream.accept(3, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/prompt\"}")).orElseThrow());
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
        var stream = new AcpxSessionStreamProjector();
        health.observe(stream.accept(1, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"session/new\"}")).orElseThrow(), Duration.ofSeconds(59));
        health.observe(stream.accept(2, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"new\"},\"id\":4}")).orElseThrow(), Duration.ofSeconds(59));

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
        health.observe(streamEvent(1, "{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"update\":{\"sessionUpdate\":\"agent_thought_chunk\",\"content\":{\"text\":\"working\"}}}}"), Duration.ofSeconds(19));

        var assessment = health.assess(Duration.ofSeconds(20));

        assertEquals(AgentExecutionHealth.Status.HEALTHY, assessment.status());
        assertFalse(assessment.failure().recoverable());
    }

    @Test
    void providerFailureOverridesReconnectAndActivity() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30));

        health.observe(AgentLifecycleEvent.stderrLine(0, "[acpx] session claude-main · agent needs reconnect"));
        var stream = new AcpxSessionStreamProjector();
        stream.accept(0, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\"}"));
        health.observe(stream.accept(1, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"new\"},\"id\":1}")).orElseThrow());
        health.observe(stream.accept(2, AcpxStreamWireLine.parse("{\"jsonrpc\":\"2.0\",\"method\":\"session/prompt/failed\",\"error\":\"Internal error: API Error: 529 [1305][该模型当前访问量过大，请您稍后再试]\"}")).orElseThrow());

        var assessment = health.assess(Duration.ZERO);

        assertEquals(AgentExecutionHealth.Status.RECOVERABLE_FAILURE, assessment.status());
        assertEquals(AcpxFailureReason.AGENT_OVERLOADED.code(), assessment.failure().reason());
        assertFalse(assessment.failure().interruptRunningProcess());
    }

    @Test
    void promptAcceptedWithoutTrustedProgressBecomesRecoverableOutputInterrupted() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30), Duration.ofSeconds(5));

        health.observe(streamEvent(0, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/prompt\",\"params\":{}}"), Duration.ofSeconds(1));

        var assessment = health.assess(Duration.ofSeconds(7));

        assertEquals(AgentExecutionHealth.Status.RECOVERABLE_FAILURE, assessment.status());
        assertEquals(AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED.code(), assessment.failure().reason());
        assertTrue(assessment.failure().recoverable());
    }

    @Test
    void trustedAgentActivityResetsProgressStallClock() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(30), Duration.ofSeconds(5));

        health.observe(streamEvent(0, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/prompt\",\"params\":{}}"), Duration.ofSeconds(1));
        health.observe(streamEvent(1, "{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"update\":{\"sessionUpdate\":\"agent_thought_chunk\",\"content\":{\"text\":\"working\"}}}}"), Duration.ofSeconds(4));

        var assessment = health.assess(Duration.ofSeconds(8));

        assertEquals(AgentExecutionHealth.Status.HEALTHY, assessment.status());
        assertFalse(assessment.failure().recoverable());
    }

    /** Transport-level cannot-bypass: ordinary ongoing output that mentions
     * quota / rate limit / is_error=false must never raise a recoverable signal
     * (so {@code waitForExitOrRecoverableSignal} does not terminate/cancel) and
     * the run continues to normal completion. */
    @Test
    void ongoingOutputWithQuotaAndRateLimitWordsDoesNotRaiseRecoverableSignal() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(1), Duration.ofSeconds(60));

        health.observe(streamEvent(0, """
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Consider the quota and rate limit policy; the tool returned is_error=false."}}}}
                """));
        health.observe(streamEvent(1, """
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"usage_update","used":100,"size":200000,"cost":{"amount":0,"currency":"USD"}}}}
                """));
        health.observe(streamEvent(2, """
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"tool_call_update","toolCallId":"exec-1","status":"completed","rawOutput":{"is_error":false}}}}
                """));

        // Poll past the recoverable-signal grace: still no recoverable failure.
        for (long t = 0; t <= 5; t++) {
            var assessment = health.assess(Duration.ofSeconds(t));
            assertTrue(
                    assessment.status() == AgentExecutionHealth.Status.HEALTHY
                            || assessment.status() == AgentExecutionHealth.Status.UNKNOWN,
                    "ongoing output must not raise a recoverable failure at t=" + t + "; got " + assessment.status());
            assertFalse(
                    assessment.failure().reason().startsWith("RECOVERABLE_"),
                    "ongoing output must not raise any recoverable signal at t=" + t + "; got " + assessment.failure().reason());
        }
    }

    /** Transport-level cannot-bypass: a real prompt 429 still triggers the
     * correct recoverable RATE_LIMIT failure (the one case a cancel/retry is
     * warranted). */
    @Test
    void realPromptFourTwentyNineTriggersRecoverableRateLimit() {
        var health = new AgentExecutionHealth(catalog, Duration.ofSeconds(1));

        var stream = new AcpxSessionStreamProjector();
        stream.accept(0, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","method":"session/prompt","id":3,"params":{}}
                """));
        health.observe(stream.accept(1, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"Request rejected (429): rate limit exceeded"}}
                """)).orElseThrow());

        var assessment = health.assess(Duration.ofSeconds(2));
        assertEquals(AgentExecutionHealth.Status.RECOVERABLE_FAILURE, assessment.status());
        assertEquals(AcpxFailureReason.RATE_LIMIT.code(), assessment.failure().reason());
    }
}
