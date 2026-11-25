package dev.dual.core.execution;

public enum AdapterSignalType {
    AGENT_RECONNECT_REQUIRED(AdapterSignalSeverity.RECOVERABLE),
    MODEL_MISMATCH(AdapterSignalSeverity.RECOVERABLE),
    SESSION_BOOTSTRAP_FAILED(AdapterSignalSeverity.RECOVERABLE),
    ADAPTER_INCOMPATIBLE(AdapterSignalSeverity.BLOCKING),
    SESSION_RESUME_FAILED(AdapterSignalSeverity.RECOVERABLE),
    SESSION_RECREATED(AdapterSignalSeverity.INFO),
    AUTH_FAILED(AdapterSignalSeverity.BLOCKING),
    PROVIDER_CONNECTION_FAILED(AdapterSignalSeverity.RECOVERABLE),
    RATE_LIMITED(AdapterSignalSeverity.RECOVERABLE),
    CONTEXT_WINDOW_EXCEEDED(AdapterSignalSeverity.BLOCKING),
    NATIVE_PERMISSION_INTERRUPTED(AdapterSignalSeverity.BLOCKING),
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
        return severity != AdapterSignalSeverity.INFO;
    }
}
