package dev.dual.runtime.handler;

import java.time.Duration;

/**
 * Resolves the stale-attempt timeout policy for {@link AgentExecutionAttemptReconciler}.
 *
 * <p>Centralizes environment parsing so {@code WorkflowRuntime} injects a concrete
 * {@link Duration} and the reconciler never reads configuration directly. A
 * live-but-no-progress run is reconciled to TIMEOUT once it exceeds this boundary;
 * a dead pid is reconciled to INTERRUPTED immediately and is not gated by this
 * timeout.
 *
 * <p>The default (8 minutes) matches the real-case stop-loss semantics already used
 * for "single run unexpected long wait". Operators may override it with the
 * {@code DUAL_STALE_ATTEMPT_TIMEOUT_MS} environment variable (positive milliseconds).
 */
public final class AgentExecutionAttemptTimeoutPolicy {
    public static final String ENV = "DUAL_STALE_ATTEMPT_TIMEOUT_MS";
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(8);

    private final Duration timeout;
    private final String source;

    public AgentExecutionAttemptTimeoutPolicy() {
        this(System.getenv(ENV));
    }

    AgentExecutionAttemptTimeoutPolicy(String raw) {
        Duration parsed = parse(raw);
        if (parsed == null) {
            this.timeout = DEFAULT_TIMEOUT;
            this.source = "default";
        } else {
            this.timeout = parsed;
            this.source = "env:" + ENV;
        }
    }

    public Duration timeout() {
        return timeout;
    }

    public String source() {
        return source;
    }

    private static Duration parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long ms = Long.parseLong(raw.trim());
            if (ms <= 0) {
                return null;
            }
            return Duration.ofMillis(ms);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
