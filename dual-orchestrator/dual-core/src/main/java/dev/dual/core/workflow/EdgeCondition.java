package dev.dual.core.workflow;

import java.util.Map;

/**
 * Evaluates a condition against workflow/node context.
 * Implementations must be immutable and thread-safe.
 */
public interface EdgeCondition {

    /** Evaluate the condition against the given context. */
    boolean evaluate(Map<String, Object> context);

    /** No-op condition — always matches. Used for unconditional edges. */
    static EdgeCondition unconditional() {
        return UnconditionalCondition.INSTANCE;
    }

    /** Create a config-equals condition: config.get(key) == expectedValue. */
    static EdgeCondition configEquals(String key, String expectedValue) {
        return new ConfigEqualsCondition(key, expectedValue);
    }
}
