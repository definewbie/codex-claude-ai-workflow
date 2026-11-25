package dev.dual.core.execution;

public enum AgentExecutionProcessStatus {
    NOT_STARTED,
    RUNNING,
    EXITED,
    RECONNECT_REQUIRED,
    TIMED_OUT,
    INTERRUPTED,
    STALLED,
    FAILED
}
