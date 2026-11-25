package dev.dual.core.flow;

import java.util.List;

public record FlowValidationResult(boolean valid, List<String> errors) {
    public FlowValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        if (valid && !errors.isEmpty()) {
            throw new IllegalArgumentException("valid result cannot contain errors");
        }
    }

    public static FlowValidationResult ok() {
        return new FlowValidationResult(true, List.of());
    }

    public static FlowValidationResult invalid(List<String> errors) {
        return new FlowValidationResult(false, errors);
    }
}
