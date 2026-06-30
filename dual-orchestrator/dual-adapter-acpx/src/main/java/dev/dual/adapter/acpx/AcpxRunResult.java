package dev.dual.adapter.acpx;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record AcpxRunResult(
        int exitCode,
        Instant startedAt,
        Instant finishedAt,
        List<String> command,
        List<AcpxExecutionEvent> executionTimeline,
        AcpxUsage usage
) {
    public AcpxRunResult(int exitCode, Instant startedAt, Instant finishedAt, List<String> command) {
        this(exitCode, startedAt, finishedAt, command, List.of(), null);
    }

    public AcpxRunResult(
            int exitCode,
            Instant startedAt,
            Instant finishedAt,
            List<String> command,
            List<AcpxExecutionEvent> executionTimeline
    ) {
        this(exitCode, startedAt, finishedAt, command, executionTimeline, null);
    }

    public AcpxRunResult {
        command = command == null ? List.of() : List.copyOf(command);
        executionTimeline = executionTimeline == null ? List.of() : List.copyOf(executionTimeline);
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
