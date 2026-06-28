package dev.dual.core.execution;

public enum AgentExecutionAttemptStatus {
    PREPARED,
    PROCESS_STARTED,
    STREAMING,
    PROCESS_EXITED,
    ARTIFACT_EXTRACTED,
    CONTRACT_VALIDATED,
    COMPLETED,
    CONTRACT_FAILED,
    RECOVERABLE_FAILED,
    RUNTIME_FAILED
}
