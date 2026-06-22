package dev.dual.core.execution;

public enum AdapterSignalType {
    AGENT_RECONNECT_REQUIRED(AdapterSignalSeverity.RECOVERABLE),
    SESSION_RESUME_FAILED(AdapterSignalSeverity.RECOVERABLE),
    SESSION_RECREATED(AdapterSignalSeverity.INFO),
    AUTH_FAILED(AdapterSignalSeverity.BLOCKING),
    RATE_LIMITED(AdapterSignalSeverity.RECOVERABLE),
    CONTEXT_WINDOW_EXCEEDED(AdapterSignalSeverity.BLOCKING),
    PERMISSION_BLOCKED(AdapterSignalSeverity.BLOCKING),
    PROCESS_STALLED(AdapterSignalSeverity.RECOVERABLE);

    private final AdapterSignalSeverity severity;

    AdapterSignalType(AdapterSignalSeverity severity) {
        this.severity = severity;
    }

    public AdapterSignalSeverity severity() {
        return severity;
    }

    public boolean blocksSuccess() {
        return severity == AdapterSignalSeverity.BLOCKING;
    }
}
