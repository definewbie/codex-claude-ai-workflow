package dev.dual.adapter.acpx;

import java.util.List;
import java.util.Optional;

final class AcpxFailureSignalCatalog {
    private final List<AcpxFailureSignal> signals;

    AcpxFailureSignalCatalog() {
        this(defaultSignals());
    }

    AcpxFailureSignalCatalog(List<AcpxFailureSignal> signals) {
        this.signals = List.copyOf(signals);
    }

    Optional<AcpxFailureSignal> match(AcpxFailureEvidence evidence) {
        return signals.stream()
                .filter(signal -> signal.matches(evidence))
                .findFirst();
    }

    private static List<AcpxFailureSignal> defaultSignals() {
        return List.of(
                signal(
                        "agent_reconnect",
                        "RECOVERABLE_AGENT_RECONNECT",
                        "acpx agent session needs reconnect; fix the session and run dualctl retry, then dualctl resume",
                        true,
                        "agent needs reconnect"
                ),
                signal(
                        "session_missing",
                        "RECOVERABLE_SESSION_MISSING",
                        "acpx session is missing; create or reconnect the session and run dualctl retry, then dualctl resume",
                        true,
                        "no acpx session found"
                ),
                signal(
                        "rate_limit",
                        "RECOVERABLE_RATE_LIMIT",
                        "agent quota or rate limit was hit; wait for quota recovery and run dualctl retry, then dualctl resume",
                        true,
                        "rate limit",
                        "quota",
                        "request rejected (429)",
                        "usage limit",
                        "使用上限"
                ),
                signal(
                        "context_window",
                        "RECOVERABLE_CONTEXT_WINDOW",
                        "agent context window appears exhausted; start or reconnect a fresh session and run dualctl retry, then dualctl resume",
                        true,
                        "context window"
                )
        );
    }

    private static AcpxFailureSignal signal(
            String id,
            String reason,
            String message,
            boolean recoverable,
            String... contains
    ) {
        return new AcpxFailureSignal(
                id,
                reason,
                message,
                recoverable,
                java.util.Arrays.stream(contains)
                        .map(AcpxFailureSignal::contains)
                        .toList()
        );
    }
}
