package dev.dual.core.workflow;

import java.util.Map;

/**
 * Evaluates a condition against workflow/node context.
 * Implementations must be immutable and thread-safe.
 */
public interface EdgeCondition {

    /** Evaluate the condition against the given context. */
    boolean evaluate(Map<String, Object> context);

    /** Discriminator for serialization. "unconditional" or "config_equals". */
    String type();

    /** Serialisation parameters. Empty map for unconditional conditions. */
    Map<String, Object> params();

    /** No-op condition — always matches. Used for unconditional edges. */
    static EdgeCondition unconditional() {
        return UnconditionalCondition.INSTANCE;
    }

    /** Create a config-equals condition: config.get(key) == expectedValue. */
    static EdgeCondition configEquals(String key, String expectedValue) {
        return new ConfigEqualsCondition(key, expectedValue);
    }

    /** Reconstruct from serialised form. */
    static EdgeCondition fromTypeAndParams(String type, Map<String, Object> params) {
        if (type == null || "unconditional".equals(type)) {
            return unconditional();
        }
        if ("config_equals".equals(type)) {
            return configEquals((String) params.get("key"), (String) params.get("value"));
        }
        throw new IllegalArgumentException("unknown EdgeCondition type: " + type);
    }
}
