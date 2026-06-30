package dev.dual.core.execution;

import java.time.Instant;

/**
 * Owns the terminal consistency boundary for one external agent attempt.
 *
 * <p>Adapters and validators provide typed facts. This class produces the only
 * terminal attempt/outcome/legacy-field combination that may be persisted.</p>
 */
public final class AgentExecutionFinalizer {
    private final AgentExecutionOutcomeResolver resolver;

    public AgentExecutionFinalizer() {
        this(new AgentExecutionOutcomeResolver());
    }

    public AgentExecutionFinalizer(AgentExecutionOutcomeResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver is required");
        }
        this.resolver = resolver;
    }

    public FinalizedAgentExecution finalize(AgentExecutionAttempt baseAttempt, AgentExecutionEvidence evidence) {
        if (baseAttempt == null) {
            throw new IllegalArgumentException("baseAttempt is required");
        }
        if (evidence == null) {
            throw new IllegalArgumentException("evidence is required");
        }
        var outcome = resolver.resolve(evidence);
        var attempt = terminalAttempt(baseAttempt, evidence, outcome);
        return new FinalizedAgentExecution(
                attempt,
                outcome,
                outcome.nodeMaySucceed() ? "completed" : "failed",
                outcome.nodeMaySucceed() ? null : outcome.primaryReason(),
                !outcome.nodeMaySucceed() && outcome.retryPolicy() != AgentExecutionRetryPolicy.NONE
        );
    }

    private AgentExecutionAttempt terminalAttempt(
            AgentExecutionAttempt base,
            AgentExecutionEvidence evidence,
            AgentExecutionOutcome outcome
    ) {
        var status = attemptStatus(outcome);
        var processStatus = processStatus(evidence.processResult(), outcome);
        var failureSignal = status == AgentExecutionAttemptStatus.COMPLETED ? null : primaryFailureSignal(outcome);
        var recoverable = status == AgentExecutionAttemptStatus.RECOVERABLE_FAILED;
        return new AgentExecutionAttempt(
                base.attemptId(),
                base.runId(),
                base.workstreamId(),
                base.nodeId(),
                base.agent(),
                status,
                processStatus,
                failureSignal,
                recoverable,
                outcome.retryPolicy() == AgentExecutionRetryPolicy.AUTO_ALLOWED,
                outcome.operatorMessage(),
                base.stderrPath(),
                base.transcriptPath(),
                base.sessionName(),
                base.sessionId(),
                base.sessionScope(),
                base.sessionSegmentKey(),
                base.startedAt(),
                finishedAt(base)
        );
    }

    private static AgentExecutionAttemptStatus attemptStatus(AgentExecutionOutcome outcome) {
        if (outcome.nodeMaySucceed()) {
            return AgentExecutionAttemptStatus.COMPLETED;
        }
        if (outcome.status() == AgentExecutionOutcomeStatus.CONTRACT_FAILED) {
            return AgentExecutionAttemptStatus.CONTRACT_FAILED;
        }
        if (outcome.retryPolicy() != AgentExecutionRetryPolicy.NONE) {
            return AgentExecutionAttemptStatus.RECOVERABLE_FAILED;
        }
        return AgentExecutionAttemptStatus.RUNTIME_FAILED;
    }

    private static AgentExecutionProcessStatus processStatus(
            ProcessResult process,
            AgentExecutionOutcome outcome
    ) {
        if (outcome.nodeMaySucceed() || outcome.status() == AgentExecutionOutcomeStatus.CONTRACT_FAILED) {
            return AgentExecutionProcessStatus.EXITED;
        }
        return switch (process.usability()) {
            case TIMED_OUT -> AgentExecutionProcessStatus.TIMED_OUT;
            case INTERRUPTED -> AgentExecutionProcessStatus.INTERRUPTED;
            case STALLED -> AgentExecutionProcessStatus.STALLED;
            case KILLED_AFTER_RECOVERABLE_SIGNAL -> AgentExecutionProcessStatus.RECONNECT_REQUIRED;
            case EXITED_ZERO, EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT, EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT ->
                    AgentExecutionProcessStatus.FAILED;
        };
    }

    private static AdapterSignalType primaryFailureSignal(AgentExecutionOutcome outcome) {
        return outcome.secondarySignals().isEmpty() ? null : outcome.secondarySignals().get(0).type();
    }

    private static Instant finishedAt(AgentExecutionAttempt attempt) {
        return attempt.finishedAt() == null ? Instant.now() : attempt.finishedAt();
    }
}
