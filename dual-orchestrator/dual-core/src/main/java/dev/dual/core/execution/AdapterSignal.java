package dev.dual.core.execution;

public record AdapterSignal(
        AdapterSignalType type,
        String message,
        long observedSequence,
        String source,
        String sessionId,
        boolean superseded
) {
    public AdapterSignal {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        message = message == null ? "" : message;
        source = source == null ? "" : source;
        sessionId = sessionId == null ? "" : sessionId;
        if (observedSequence < 0) {
            throw new IllegalArgumentException("observedSequence must be non-negative");
        }
    }

    public AdapterSignal(AdapterSignalType type, String message, long observedSequence) {
        this(type, message, observedSequence, "", "", false);
    }

    public static AdapterSignal of(AdapterSignalType type) {
        return new AdapterSignal(type, "", 0);
    }
}
