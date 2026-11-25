package dev.dual.ledger.file;

/**
 * Durable dispatch status for one AgentTask.
 *
 * AgentTask is the dispatch envelope for asking an external agent/tool to run
 * a node. It must not own workflow routing, but its lifecycle still needs a
 * typed boundary because runtime recovery and observability read it.
 */
public enum AgentTaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
