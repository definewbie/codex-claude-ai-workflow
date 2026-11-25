package dev.dual.core.id;

import java.util.Objects;

public record DualId(String value) {
    public DualId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("id value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
