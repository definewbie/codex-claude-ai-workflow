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
        long elapsedMs,
        Map<String, Object> details
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
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public AcpxExecutionEvent(
            AgentExecutionTimelineEventKind kind,
            String source,
            long sequence,
            Instant observedAt,
            long elapsedMs
    ) {
        this(kind, source, sequence, observedAt, elapsedMs, Map.of());
    }

    static AcpxExecutionEvent now(AgentExecutionTimelineEventKind kind, String source, long sequence, Instant startedAt) {
        return now(kind, source, sequence, startedAt, Map.of());
    }

    static AcpxExecutionEvent now(
            AgentExecutionTimelineEventKind kind,
            String source,
            long sequence,
            Instant startedAt,
            Map<String, Object> details
    ) {
        var now = Instant.now();
        return new AcpxExecutionEvent(kind, source, sequence, now, Duration.between(startedAt, now).toMillis(), details);
    }

    Map<String, Object> toMap() {
        if (details.isEmpty()) {
            return Map.of(
                "kind", kind.wireName(),
                "source", source,
                "sequence", sequence,
                "observedAt", observedAt.toString(),
                "elapsedMs", elapsedMs
            );
        }
        return Map.of(
                "kind", kind.wireName(),
                "source", source,
                "sequence", sequence,
                "observedAt", observedAt.toString(),
                "elapsedMs", elapsedMs,
                "details", details
        );
    }
}
