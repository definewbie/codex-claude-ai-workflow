package dev.dual.core.workflow;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    WAITING_FOR_HUMAN,
    WAITING_FOR_RECOVERY,
    BLOCKED,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED;
    }
}
