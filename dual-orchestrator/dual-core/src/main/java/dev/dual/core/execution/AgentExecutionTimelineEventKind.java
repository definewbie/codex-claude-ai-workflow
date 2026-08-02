package dev.dual.core.execution;

public enum AgentExecutionTimelineEventKind {
    PROCESS_STARTED("process_started"),
    PROCESS_WAIT_STARTED("process_wait_started"),
    PROMPT_ACCEPTED("prompt_accepted"),
    PROMPT_COMPLETED("prompt_completed"),
    PROCESS_WAIT_COMPLETED("process_wait_completed"),
    RECOVERABLE_SIGNAL_DETECTED("recoverable_signal_detected"),
    PROCESS_EXITED("process_exited"),
    ARTIFACT_EXTRACTED("artifact_extracted"),
    CONTRACT_VALIDATED("contract_validated"),
    PROCESS_INTERRUPTED("process_interrupted"),
    ATTEMPT_RECONCILED("attempt_reconciled");

    private final String wireName;

    AgentExecutionTimelineEventKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
