package dev.dual.adapter.acpx;

import java.util.List;
import java.util.Comparator;
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
                .max(Comparator.comparingInt(AcpxFailureSignal::priority));
    }

    private static List<AcpxFailureSignal> defaultSignals() {
        return List.of(
                signal(
                        "model_mismatch",
                        AcpxFailureReason.AGENT_MODEL_MISMATCH,
                        100,
                        "acpx agent session model state is stale or incompatible; rebuild the acpx session, then run dualctl retry and dualctl resume",
                        true,
                        false,
                        "supported api model names",
                        "cannot apply --model",
                        "did not advertise that model"
                ),
                signal(
                        "model_overloaded",
                        AcpxFailureReason.AGENT_OVERLOADED,
                        90,
                        "agent model service is overloaded (API 529 / provider capacity); wait and run dualctl retry, then dualctl resume",
                        true,
                        false,
                        "api error: 529",
                        "访问量过大"
                ),
                signal(
                        "agent_reconnect",
                        AcpxFailureReason.AGENT_RECONNECT,
                        10,
                        "acpx agent session needs reconnect; fix the session and run dualctl retry, then dualctl resume",
                        true,
                        false,
                        "agent needs reconnect"
                ),
                signal(
                        "agent_permission_blocked",
                        AcpxFailureReason.AGENT_PERMISSION_BLOCKED,
                        85,
                        "agent tool permission was blocked in non-interactive execution; inspect the prompt/output, then retry with a fixed session or workflow permission policy",
                        true,
                        true,
                        "tool permission request failed",
                        "tool use aborted",
                        "tool_use_error>cancelled"
                ),
                signal(
                        "session_missing",
                        AcpxFailureReason.SESSION_MISSING,
                        80,
                        "acpx session is missing; create or reconnect the session and run dualctl retry, then dualctl resume",
                        true,
                        false,
                        "no acpx session found"
                ),
                signal(
                        "rate_limit",
                        AcpxFailureReason.RATE_LIMIT,
                        70,
                        "agent quota or rate limit was hit; wait for quota recovery and run dualctl retry, then dualctl resume",
                        true,
                        false,
                        "rate limit",
                        "quota",
                        "request rejected (429)",
                        "usage limit",
                        "使用上限"
                ),
                signal(
                        "context_window",
                        AcpxFailureReason.CONTEXT_WINDOW,
                        60,
                        "agent context window appears exhausted; start or reconnect a fresh session and run dualctl retry, then dualctl resume",
                        true,
                        false,
                        "context window"
                )
        );
    }

    private static AcpxFailureSignal signal(
            String id,
            AcpxFailureReason reason,
            int priority,
            String message,
            boolean recoverable,
            boolean interruptRunningProcess,
            String... contains
    ) {
        return new AcpxFailureSignal(
                id,
                reason,
                priority,
                message,
                recoverable,
                interruptRunningProcess,
                java.util.Arrays.stream(contains)
                        .map(AcpxFailureSignal::contains)
                        .toList()
        );
    }
}
