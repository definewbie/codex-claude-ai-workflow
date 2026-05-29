package dev.dual.core.workflow;

import java.util.Map;

final class UnconditionalCondition implements EdgeCondition {
    static final UnconditionalCondition INSTANCE = new UnconditionalCondition();

    private UnconditionalCondition() {}

    @Override
    public boolean evaluate(Map<String, Object> context) {
        return true;
    }

    @Override
    public String toString() {
        return "*";
    }
}
