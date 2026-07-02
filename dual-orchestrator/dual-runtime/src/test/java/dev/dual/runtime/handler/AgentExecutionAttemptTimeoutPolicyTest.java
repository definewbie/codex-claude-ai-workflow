package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentExecutionAttemptTimeoutPolicyTest {

    @Test
    void defaultTimeoutIsEightMinutesWhenEnvAbsent() {
        assertEquals(Duration.ofMinutes(8), new AgentExecutionAttemptTimeoutPolicy(null).timeout());
        assertEquals(Duration.ofMinutes(8), new AgentExecutionAttemptTimeoutPolicy("  ").timeout());
    }

    @Test
    void envOverrideIsParsedAsMilliseconds() {
        assertEquals(Duration.ofMillis(120_000L), new AgentExecutionAttemptTimeoutPolicy("120000").timeout());
    }

    @Test
    void nonNumericEnvFallsBackToDefault() {
        assertEquals(Duration.ofMinutes(8), new AgentExecutionAttemptTimeoutPolicy("not-a-number").timeout());
    }

    @Test
    void nonPositiveEnvFallsBackToDefault() {
        assertEquals(Duration.ofMinutes(8), new AgentExecutionAttemptTimeoutPolicy("0").timeout());
        assertEquals(Duration.ofMinutes(8), new AgentExecutionAttemptTimeoutPolicy("-1000").timeout());
    }
}
