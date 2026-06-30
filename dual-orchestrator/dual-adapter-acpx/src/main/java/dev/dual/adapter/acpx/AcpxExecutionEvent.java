package dev.dual.adapter.acpx;

import dev.dual.core.execution.AgentExecutionTimelineEventKind;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record AcpxExecutionEvent(
        AgentExecutionTimelineEventKind kind,
        String source,
        long sequence,
        Instant observedAt,
        long elapsedMs
) {
    public AcpxExecutionEvent {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        source = source == null ? "" : source;
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs must be non-negative");
        }
    }

    static AcpxExecutionEvent now(AgentExecutionTimelineEventKind kind, String source, long sequence, Instant startedAt) {
        var now = Instant.now();
        return new AcpxExecutionEvent(kind, source, sequence, now, Duration.between(startedAt, now).toMillis());
    }

    Map<String, Object> toMap() {
        return Map.of(
                "kind", kind.wireName(),
                "source", source,
                "sequence", sequence,
                "observedAt", observedAt.toString(),
                "elapsedMs", elapsedMs
        );
    }
}
