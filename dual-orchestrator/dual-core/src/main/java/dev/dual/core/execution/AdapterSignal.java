package dev.dual.core.execution;

public record AdapterSignal(
        AdapterSignalType type,
        String message,
        long observedSequence
) {
    public AdapterSignal {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        message = message == null ? "" : message;
        if (observedSequence < 0) {
            throw new IllegalArgumentException("observedSequence must be non-negative");
        }
    }

    public static AdapterSignal of(AdapterSignalType type) {
        return new AdapterSignal(type, "", 0);
    }
}
