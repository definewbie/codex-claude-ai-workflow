package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.ProcessResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record AcpxRunResult(
        Instant startedAt,
        Instant finishedAt,
        List<String> command,
        List<AcpxExecutionEvent> executionTimeline,
        AcpxUsage usage,
        AcpxPromptCompletion promptCompletion,
        ProcessResult processResult,
        List<AdapterSignal> adapterSignals,
        List<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation> toolObservations
) {
    public AcpxRunResult {
        command = command == null ? List.of() : List.copyOf(command);
        executionTimeline = executionTimeline == null ? List.of() : List.copyOf(executionTimeline);
        promptCompletion = promptCompletion == null ? AcpxPromptCompletion.EMPTY : promptCompletion;
        if (processResult == null) {
            throw new IllegalArgumentException("processResult is required");
        }
        adapterSignals = adapterSignals == null ? List.of() : List.copyOf(adapterSignals);
        toolObservations = toolObservations == null ? List.of() : List.copyOf(toolObservations);
    }

    /** Bootstrap/failed calls have no internal tool evidence; an empty list grants no verification success. */
    public AcpxRunResult(Instant startedAt, Instant finishedAt, List<String> command,
                         List<AcpxExecutionEvent> executionTimeline, AcpxUsage usage,
                         AcpxPromptCompletion promptCompletion, ProcessResult processResult, List<AdapterSignal> adapterSignals) {
        this(startedAt, finishedAt, command, executionTimeline, usage, promptCompletion, processResult, adapterSignals, List.of());
    }

    public int exitCode() {
        return processResult.exitCode() == null ? -1 : processResult.exitCode();
    }

    public List<AdapterSignal> activeAdapterSignals() {
        return adapterSignals.stream().filter(signal -> !signal.superseded()).toList();
    }

    public long durationMs() {
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    public boolean reachedDeclaredTimeout(Duration grace) {
        var timeoutSeconds = declaredTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            return false;
        }
        var thresholdMs = Duration.ofSeconds(timeoutSeconds).minus(grace).toMillis();
        return durationMs() >= Math.max(0, thresholdMs);
    }

    private long declaredTimeoutSeconds() {
        if (command == null) {
            return 0;
        }
        for (int i = 0; i < command.size() - 1; i++) {
            if (!"--timeout".equals(command.get(i))) {
                continue;
            }
            try {
                return Long.parseLong(command.get(i + 1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

}
