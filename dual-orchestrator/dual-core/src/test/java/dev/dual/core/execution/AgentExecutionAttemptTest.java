package dev.dual.core.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionAttemptTest {
    @Test
    void reconnectAttemptIsRecoverableRuntimeFailure() {
        var attempt = new AgentExecutionAttempt(
                "att-1",
                "run-1",
                "ws-1",
                "implement.claude_implement",
                "claude",
                AgentExecutionAttemptStatus.RECOVERABLE_FAILED,
                AgentExecutionProcessStatus.RECONNECT_REQUIRED,
                AdapterSignalType.AGENT_RECONNECT_REQUIRED,
                true,
                true,
                "acpx reported agent needs reconnect",
                "outputs/implement.stderr.log",
                "outputs/implement.transcript.log",
                "claude-main-ws-1",
                "session-1",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:30Z")
        );

        assertTrue(attempt.terminal());
        assertTrue(attempt.recoverable());
        assertTrue(attempt.retrySafe());
        assertEquals(AdapterSignalType.AGENT_RECONNECT_REQUIRED, attempt.failureSignal());
    }

    @Test
    void completedAttemptCannotCarryFailureSignal() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentExecutionAttempt(
                "att-1",
                "run-1",
                "ws-1",
                "plan.claude_plan",
                "claude",
                AgentExecutionAttemptStatus.COMPLETED,
                AgentExecutionProcessStatus.EXITED,
                AdapterSignalType.AGENT_RECONNECT_REQUIRED,
                false,
                false,
                "done",
                "",
                "",
                "claude-main-ws-1",
                "",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:01Z")
        ));

        assertEquals("completed attempt cannot carry failureSignal", error.getMessage());
    }

    @Test
    void reconnectProcessRequiresReconnectFailureSignal() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentExecutionAttempt(
                "att-1",
                "run-1",
                "ws-1",
                "intake.claude_brainstorm",
                "claude",
                AgentExecutionAttemptStatus.RECOVERABLE_FAILED,
                AgentExecutionProcessStatus.RECONNECT_REQUIRED,
                AdapterSignalType.RATE_LIMITED,
                true,
                false,
                "wrong signal",
                "",
                "",
                "claude-main-ws-1",
                "",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:01Z")
        ));

        assertEquals("reconnect process requires AGENT_RECONNECT_REQUIRED failureSignal", error.getMessage());
    }

    @Test
    void terminalAttemptRequiresFinishedAt() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentExecutionAttempt(
                "att-1",
                "run-1",
                "ws-1",
                "intake.claude_brainstorm",
                "claude",
                AgentExecutionAttemptStatus.TIMED_OUT,
                AgentExecutionProcessStatus.TIMED_OUT,
                AdapterSignalType.PROCESS_STALLED,
                true,
                false,
                "timed out",
                "",
                "",
                "claude-main-ws-1",
                "",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-28T00:00:00Z"),
                null
        ));

        assertEquals("terminal attempt requires finishedAt", error.getMessage());
    }

    @Test
    void timedOutProcessRequiresTimedOutAttemptStatus() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentExecutionAttempt(
                "att-1",
                "run-1",
                "ws-1",
                "intake.claude_brainstorm",
                "claude",
                AgentExecutionAttemptStatus.RECOVERABLE_FAILED,
                AgentExecutionProcessStatus.TIMED_OUT,
                AdapterSignalType.PROCESS_STALLED,
                true,
                false,
                "timed out",
                "",
                "",
                "claude-main-ws-1",
                "",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:01Z")
        ));

        assertEquals("timed out process must use TIMED_OUT attempt status", error.getMessage());
    }

    @Test
    void stalledProcessRequiresStalledAttemptStatus() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentExecutionAttempt(
                "att-1",
                "run-1",
                "ws-1",
                "intake.claude_brainstorm",
                "claude",
                AgentExecutionAttemptStatus.RECOVERABLE_FAILED,
                AgentExecutionProcessStatus.STALLED,
                AdapterSignalType.PROCESS_STALLED,
                true,
                false,
                "stalled",
                "",
                "",
                "claude-main-ws-1",
                "",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-28T00:00:00Z"),
                Instant.parse("2026-06-28T00:00:01Z")
        ));

        assertEquals("stalled process must use STALLED attempt status", error.getMessage());
    }
}
