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
                        "agent_auth_failed",
                        AcpxFailureReason.AGENT_AUTH_FAILED,
                        88,
                        "agent authentication failed (API 401 / invalid credentials); fix the underlying agent credentials, then run dualctl retry and dualctl resume",
                        true,
                        false,
                        "failed to authenticate",
                        "invalid authentication credentials",
                        "api error: 401"
                ),
                signal(
                        "provider_connection_failed",
                        AcpxFailureReason.PROVIDER_CONNECTION_FAILED,
                        87,
                        "agent provider connection failed; verify the provider network/socket state, then run dualctl retry",
                        true,
                        false,
                        "failedtoopensocket",
                        "failed to open socket",
                        "unable to connect to api"
                ),
                signal(
                        "agent_reconnect",
                        AcpxFailureReason.AGENT_RECONNECT,
                        10,
                        "acpx agent session needs reconnect; fix the session and run dualctl retry, then dualctl resume",
                        true,
                        true,
                        "agent needs reconnect"
                ),
                signal(
                        "agent_output_interrupted",
                        AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED,
                        86,
                        "agent accepted the prompt but stopped producing trusted progress; rebuild or retry the agent session",
                        true,
                        true,
                        "reason=RECOVERABLE_AGENT_OUTPUT_INTERRUPTED",
                        "recoverable_agent_output_interrupted",
                        "agent accepted the prompt but produced no trusted progress"
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
                        "session_bootstrap_failed",
                        AcpxFailureReason.SESSION_BOOTSTRAP_FAILED,
                        82,
                        "acpx session bootstrap failed; inspect the session command evidence, then fix acpx/session environment and retry",
                        true,
                        false,
                        "acpx session ensure failed",
                        "acpx session new failed",
                        "acpx session close failed"
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
                        "agent quota or rate limit was hit (HTTP 429 / provider throttle); wait for quota recovery and run dualctl retry, then dualctl resume",
                        true,
                        false,
                        "rate limit",
                        "rate_limit_exceeded",
                        "request rejected (429)",
                        "http 429",
                        "api error: 429",
                        "status code 429",
                        "status=429",
                        "too many requests",
                        "quota exceeded",
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
