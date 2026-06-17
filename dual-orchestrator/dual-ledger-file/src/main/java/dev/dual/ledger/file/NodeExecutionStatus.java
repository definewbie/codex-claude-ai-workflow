package dev.dual.ledger.file;

/**
 * Durable status for one concrete node execution.
 *
 * NodeStatus describes the semantic node result. NodeExecutionStatus describes
 * the runtime execution record: whether the concrete process is running,
 * stale, succeeded, or failed.
 */
public enum NodeExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    STALE,
    WAITING,
    READY,
    CREATED,
    SKIPPED
}
