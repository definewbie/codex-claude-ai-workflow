package dev.dual.core.execution;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Durable runtime fact for one external agent process attempt.
 *
 * <p>This model sits before artifact validation and workflow routing. It records
 * process/session/runtime facts so adapters do not have to encode reconnect,
 * timeout, interrupted, or usage-unavailable states as natural-language output.</p>
 */
public record AgentExecutionAttempt(
        String attemptId,
        String runId,
        String workstreamId,
        String nodeId,
        String agent,
        AgentExecutionAttemptStatus status,
        AgentExecutionProcessStatus processStatus,
        AdapterSignalType failureSignal,
        boolean recoverable,
        boolean retrySafe,
        String message,
        String stderrPath,
        String transcriptPath,
        String sessionName,
        String sessionId,
        String sessionScope,
        String sessionSegmentKey,
        Instant startedAt,
        Instant finishedAt
) {
    private static final Set<AgentExecutionAttemptStatus> TERMINAL_STATUSES = EnumSet.of(
            AgentExecutionAttemptStatus.COMPLETED,
            AgentExecutionAttemptStatus.CONTRACT_FAILED,
            AgentExecutionAttemptStatus.RECOVERABLE_FAILED,
            AgentExecutionAttemptStatus.RUNTIME_FAILED
    );

    public AgentExecutionAttempt {
        attemptId = requireText("attemptId", attemptId);
        runId = requireText("runId", runId);
        workstreamId = blankToEmpty(workstreamId);
        nodeId = blankToEmpty(nodeId);
        agent = blankToEmpty(agent);
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (processStatus == null) {
            throw new IllegalArgumentException("processStatus is required");
        }
        message = blankToEmpty(message);
        stderrPath = blankToEmpty(stderrPath);
        transcriptPath = blankToEmpty(transcriptPath);
        sessionName = blankToEmpty(sessionName);
        sessionId = blankToEmpty(sessionId);
        sessionScope = blankToEmpty(sessionScope);
        sessionSegmentKey = blankToEmpty(sessionSegmentKey);
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt cannot be before startedAt");
        }
        if (terminal(status) && finishedAt == null) {
            throw new IllegalArgumentException("terminal attempt requires finishedAt");
        }
        if (status == AgentExecutionAttemptStatus.COMPLETED && failureSignal != null) {
            throw new IllegalArgumentException("completed attempt cannot carry failureSignal");
        }
        if (status == AgentExecutionAttemptStatus.COMPLETED && recoverable) {
            throw new IllegalArgumentException("completed attempt cannot be recoverable");
        }
        if (status == AgentExecutionAttemptStatus.RECOVERABLE_FAILED && !recoverable) {
            throw new IllegalArgumentException("recoverable failure attempt must be recoverable");
        }
        if (processStatus == AgentExecutionProcessStatus.RECONNECT_REQUIRED
                && failureSignal != AdapterSignalType.AGENT_RECONNECT_REQUIRED) {
            throw new IllegalArgumentException("reconnect process requires AGENT_RECONNECT_REQUIRED failureSignal");
        }
        if (processStatus == AgentExecutionProcessStatus.TIMED_OUT
                && status != AgentExecutionAttemptStatus.RECOVERABLE_FAILED) {
            throw new IllegalArgumentException("timed out process must be recoverable failed");
        }
        if (processStatus == AgentExecutionProcessStatus.INTERRUPTED
                && status != AgentExecutionAttemptStatus.RECOVERABLE_FAILED) {
            throw new IllegalArgumentException("interrupted process must be recoverable failed");
        }
    }

    public boolean terminal() {
        return terminal(status);
    }

    private static boolean terminal(AgentExecutionAttemptStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
