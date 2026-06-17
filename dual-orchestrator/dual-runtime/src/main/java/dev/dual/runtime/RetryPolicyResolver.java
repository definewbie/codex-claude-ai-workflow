package dev.dual.runtime;

import java.util.Map;

/**
 * Resolves retry policy from the default failure catalog plus node-level
 * overrides.
 *
 * Keeping this outside WorkflowRuntime prevents retry limits from drifting
 * between auto-retry execution and operator-facing rejection messages.
 */
final class RetryPolicyResolver {
    private RetryPolicyResolver() {
    }

    static RetryPolicy resolve(String failureReason, Map<String, Object> config) {
        var defaultPolicy = RetryStrategies.forReason(failureReason);
        var maxRetries = config.get("retry.max_retries");
        var baseDelay = config.get("retry.base_delay_ms");
        var multiplier = config.get("retry.backoff_multiplier");
        if (maxRetries == null && baseDelay == null && multiplier == null) {
            return defaultPolicy;
        }
        return new RetryPolicy(
                maxRetries instanceof Number n ? n.intValue() : defaultPolicy.maxRetries(),
                baseDelay instanceof Number n ? n.longValue() : defaultPolicy.baseDelayMs(),
                multiplier instanceof Number n ? n.doubleValue() : defaultPolicy.backoffMultiplier()
        );
    }
}
