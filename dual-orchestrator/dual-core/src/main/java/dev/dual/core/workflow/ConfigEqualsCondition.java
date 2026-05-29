package dev.dual.core.workflow;

import java.util.Map;
import java.util.Objects;

final class ConfigEqualsCondition implements EdgeCondition {
    private final String key;
    private final String expectedValue;

    ConfigEqualsCondition(String key, String expectedValue) {
        this.key = Objects.requireNonNull(key);
        this.expectedValue = Objects.requireNonNull(expectedValue);
    }

    @Override
    public boolean evaluate(Map<String, Object> context) {
        if (context == null) return false;
        var value = context.get(key);
        if (value == null) return false;
        return expectedValue.equals(String.valueOf(value));
    }

    @Override
    public String type() { return "config_equals"; }

    @Override
    public Map<String, Object> params() { return Map.of("key", key, "value", expectedValue); }

    String key() { return key; }
    String expectedValue() { return expectedValue; }

    @Override
    public String toString() {
        return "config." + key + " == " + expectedValue;
    }
}
