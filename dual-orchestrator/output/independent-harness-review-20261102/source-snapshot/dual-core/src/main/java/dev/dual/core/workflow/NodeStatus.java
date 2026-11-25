package dev.dual.core.workflow;

public enum NodeStatus {
    CREATED,
    READY,
    RUNNING,
    COMPLETED,
    FAILED,
    BLOCKED,
    SKIPPED,
    WAITING
}
