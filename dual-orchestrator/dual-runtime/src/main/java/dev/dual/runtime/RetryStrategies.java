package dev.dual.runtime;

import java.util.Map;

public final class RetryStrategies {
    private static final RetryPolicy DEFAULT_POLICY = new RetryPolicy(3, 1000, 1.0);

    private static final Map<String, RetryPolicy> POLICIES = Map.of(
            "RECOVERABLE_RATE_LIMIT", new RetryPolicy(5, 3000, 2.0),
            "RECOVERABLE_SESSION_MISSING", new RetryPolicy(3, 2000, 1.5),
            "RECOVERABLE_AGENT_RECONNECT", new RetryPolicy(3, 2000, 1.5),
            "RECOVERABLE_CONTEXT_WINDOW", new RetryPolicy(2, 1000, 1.0)
    );

    public static RetryPolicy forReason(String failureReason) {
        return POLICIES.getOrDefault(failureReason, DEFAULT_POLICY);
    }

    private RetryStrategies() {}
}
