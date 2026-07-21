package dev.dual.adapter.acpx;

import java.time.Duration;

/**
 * Per-run health tracker for an acpx Agent execution.
 *
 * This object is deliberately stateful and must be created for one run only.
 * It consumes ordered runtime observations and turns them into the current
 * execution health. Post-run failure classification remains a separate concern:
 * completed runs can still be classified from final stdout/stderr, but mid-run
 * polling must not repeatedly consume historical diagnostics as current state.
 */
final class AgentExecutionHealth {
    private static final AcpxFailureClassifier.AcpxFailure NO_FAILURE =
            new AcpxFailureClassifier.AcpxFailure("NONE", "", false, false);
    private static final AcpxFailureClassifier.AcpxFailure STALLED_AGENT_OUTPUT =
            new AcpxFailureClassifier.AcpxFailure(
                    AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED.code(),
                    "agent accepted the prompt but produced no trusted progress before the progress timeout",
                    true,
                    true
            );

    private final AcpxFailureSignalCatalog signalCatalog;
    private final Duration recoverableSignalGrace;
    private final Duration progressStallTimeout;

    private long lastConsumedSequence = -1;
    private long lastProgressSequence = -1;
    private Duration lastProgressAt = Duration.ZERO;
    private AcpxFailureSignal currentSignal;
    private Duration currentFailureSince = Duration.ZERO;
    private boolean terminalSuccess;

    AgentExecutionHealth(AcpxFailureSignalCatalog signalCatalog, Duration recoverableSignalGrace) {
        this(signalCatalog, recoverableSignalGrace, recoverableSignalGrace);
    }

    AgentExecutionHealth(
            AcpxFailureSignalCatalog signalCatalog,
            Duration recoverableSignalGrace,
            Duration progressStallTimeout
    ) {
        this.signalCatalog = signalCatalog;
        this.recoverableSignalGrace = recoverableSignalGrace;
        this.progressStallTimeout = progressStallTimeout;
    }

    void observe(AgentLifecycleEvent event) {
        observe(event, Duration.ZERO);
    }

    void observe(AgentLifecycleEvent event, Duration elapsed) {
        if (event.sequence() <= lastConsumedSequence) {
            return;
        }
        lastConsumedSequence = event.sequence();
        switch (event.kind()) {
            case TERMINAL_CONTRACT_SEEN -> {
                terminalSuccess = true;
                markProgress(event, elapsed);
                currentSignal = null;
            }
            case SESSION_NEW_CREATED, PROMPT_ACCEPTED, TOOL_RESULT, USAGE_UPDATE -> {
                markProgress(event, elapsed);
                if (isReconnect(currentSignal)) {
                    currentSignal = null;
                }
            }
            case AGENT_ACTIVITY -> {
                markProgress(event, elapsed);
                // Plain stdout growth proves that bytes are still arriving, but it
                // is not a trusted lifecycle signal. A reconnect diagnostic is
                // superseded only by structured acpx stream progress or a
                // complete terminal contract.
                if (event.source() == AgentLifecycleEvent.Source.ACPX_STREAM && isReconnect(currentSignal)) {
                    currentSignal = null;
                }
            }
            case SESSION_NEW_REQUESTED -> markProgress(event, elapsed);
            case SESSION_RESUME_FAILED, RECONNECT_SIGNAL, PROVIDER_FAILURE -> observeFailure(event, elapsed);
            case OTHER -> {
                // Other text is evidence, not lifecycle state.
            }
        }
    }

    Assessment assess(Duration elapsed) {
        if (terminalSuccess) {
            return new Assessment(Status.TERMINAL_SUCCESS, NO_FAILURE);
        }
        if (currentSignal != null && currentSignal.recoverable()) {
            var failure = currentSignal.toFailure();
            if (immediateFailure(currentSignal)
                    || elapsed.minus(currentFailureSince).compareTo(recoverableSignalGrace) >= 0) {
                return new Assessment(Status.RECOVERABLE_FAILURE, failure);
            }
            return new Assessment(Status.RECOVERING, failure);
        }
        if (lastProgressSequence >= 0
                && elapsed.minus(lastProgressAt).compareTo(progressStallTimeout) >= 0) {
            return new Assessment(Status.RECOVERABLE_FAILURE, STALLED_AGENT_OUTPUT);
        }
        if (lastProgressSequence >= 0) {
            return new Assessment(Status.HEALTHY, NO_FAILURE);
        }
        return new Assessment(Status.UNKNOWN, NO_FAILURE);
    }

    private void observeFailure(AgentLifecycleEvent event, Duration elapsed) {
        var evidenceText = event.source() == AgentLifecycleEvent.Source.ACPX_STREAM
                ? typedStreamErrorText(event.streamError())
                : event.text();
        if (evidenceText.isBlank()) {
            return;
        }
        var signal = signalCatalog.match(new AcpxFailureEvidence("", evidenceText, 4));
        if (signal.isEmpty()) {
            return;
        }
        var nextSignal = signal.get();
        if (currentSignal == null
                || nextSignal.priority() >= currentSignal.priority()
                || !sameReason(nextSignal, currentSignal)) {
            currentSignal = nextSignal;
            currentFailureSince = elapsed;
        }
    }

    private String typedStreamErrorText(AcpxStreamEvidence.TypedError error) {
        return error == null
                ? ""
                : new AcpxStreamEvidence(java.util.List.of(error)).normalizedErrorText();
    }

    private void markProgress(AgentLifecycleEvent event, Duration elapsed) {
        lastProgressSequence = event.sequence();
        lastProgressAt = elapsed;
    }

    private boolean isReconnect(AcpxFailureSignal signal) {
        return signal != null && signal.reason() == AcpxFailureReason.AGENT_RECONNECT;
    }

    private boolean immediateFailure(AcpxFailureSignal signal) {
        return signal.reason() == AcpxFailureReason.AGENT_AUTH_FAILED
                || signal.reason() == AcpxFailureReason.AGENT_MODEL_MISMATCH
                || signal.reason() == AcpxFailureReason.AGENT_OVERLOADED;
    }

    private boolean sameReason(AcpxFailureSignal left, AcpxFailureSignal right) {
        return left.reason() == right.reason();
    }

    Duration lastProgressAt() {
        return lastProgressAt;
    }

    enum Status {
        UNKNOWN,
        HEALTHY,
        RECOVERING,
        RECOVERABLE_FAILURE,
        TERMINAL_SUCCESS
    }

    record Assessment(Status status, AcpxFailureClassifier.AcpxFailure failure) {
    }
}
