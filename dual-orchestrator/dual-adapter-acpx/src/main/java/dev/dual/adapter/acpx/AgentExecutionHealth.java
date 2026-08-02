package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    private static final String STALLED_AGENT_OUTPUT =
            "agent accepted the prompt but produced no trusted progress before the progress timeout";

    private final AcpxFailureSignalCatalog signalCatalog;
    private final Duration recoverableSignalGrace;
    private final Duration progressStallTimeout;

    private long lastConsumedSequence = -1;
    private long lastProgressSequence = -1;
    private Duration lastProgressAt = Duration.ZERO;
    private AcpxFailureSignal currentSignal;
    private AdapterSignal currentAdapterSignal;
    private Duration currentFailureSince = Duration.ZERO;
    private final List<AdapterSignal> observedSignals = new ArrayList<>();

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
            case SESSION_NEW_CREATED, PROMPT_ACCEPTED, PROMPT_COMPLETED, TOOL_RESULT, USAGE_UPDATE -> {
                markProgress(event, elapsed);
                if (isReconnect(currentSignal)) {
                    supersedeCurrentSignal();
                }
            }
            case AGENT_ACTIVITY -> {
                markProgress(event, elapsed);
                // Plain stdout growth proves that bytes are still arriving, but it
                // is not a trusted lifecycle signal. A reconnect diagnostic is
                // superseded only by structured acpx stream progress or a
                // completed ACP prompt.
                if (event.source() == AgentLifecycleEvent.Source.ACPX_STREAM && isReconnect(currentSignal)) {
                    supersedeCurrentSignal();
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
        if (currentSignal != null && currentSignal.recoverable()) {
            if (immediateFailure(currentSignal)
                    || elapsed.minus(currentFailureSince).compareTo(recoverableSignalGrace) >= 0) {
                return new Assessment(Status.RECOVERABLE_FAILURE, currentAdapterSignal, signals());
            }
            return new Assessment(Status.RECOVERING, currentAdapterSignal, signals());
        }
        if (lastProgressSequence >= 0
                && elapsed.minus(lastProgressAt).compareTo(progressStallTimeout) >= 0) {
            var stalled = new AdapterSignal(
                    AdapterSignalType.PROCESS_STALLED,
                    STALLED_AGENT_OUTPUT,
                    Math.max(0, lastConsumedSequence + 1),
                    "health",
                    "",
                    false
            );
            return new Assessment(Status.RECOVERABLE_FAILURE, stalled, appendSignal(stalled));
        }
        if (lastProgressSequence >= 0) {
            return new Assessment(Status.HEALTHY, null, signals());
        }
        return new Assessment(Status.UNKNOWN, null, signals());
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
            currentAdapterSignal = nextSignal.toAdapterSignal(event.sequence(), event.source().name().toLowerCase());
            observedSignals.add(currentAdapterSignal);
            currentFailureSince = elapsed;
        }
    }

    private void supersedeCurrentSignal() {
        if (currentAdapterSignal != null) {
            var superseded = new AdapterSignal(
                    currentAdapterSignal.type(),
                    currentAdapterSignal.message(),
                    currentAdapterSignal.observedSequence(),
                    currentAdapterSignal.source(),
                    currentAdapterSignal.sessionId(),
                    true
            );
            for (int index = observedSignals.size() - 1; index >= 0; index--) {
                if (observedSignals.get(index) == currentAdapterSignal) {
                    observedSignals.set(index, superseded);
                    break;
                }
            }
        }
        currentSignal = null;
        currentAdapterSignal = null;
    }

    private List<AdapterSignal> signals() {
        return List.copyOf(observedSignals);
    }

    private List<AdapterSignal> appendSignal(AdapterSignal signal) {
        var signals = new ArrayList<>(observedSignals);
        if (signals.stream().noneMatch(existing -> existing.type() == signal.type()
                && existing.observedSequence() == signal.observedSequence())) {
            signals.add(signal);
        }
        return List.copyOf(signals);
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
        RECOVERABLE_FAILURE
    }

    record Assessment(Status status, AdapterSignal signal, List<AdapterSignal> signals) {
        Assessment {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }
}
