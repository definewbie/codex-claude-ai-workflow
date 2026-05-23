package dev.dual.core.workflow;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    WAITING_FOR_HUMAN,
    BLOCKED,
    COMPLETED,
    FAILED
}
