package dev.dual.ledger.file;

/**
 * Durable status for one runtime invocation of a workflow instance.
 *
 * This is intentionally separate from WorkflowStatus: a workflow can be
 * WAITING_FOR_HUMAN while the execution that reached the gate is STOPPED.
 */
public enum WorkflowExecutionStatus {
    CREATED,
    RUNNING,
    STOPPED,
    WAITING_FOR_HUMAN,
    WAITING_FOR_RECOVERY,
    BLOCKED,
    ENDED,
    FAILED
}
