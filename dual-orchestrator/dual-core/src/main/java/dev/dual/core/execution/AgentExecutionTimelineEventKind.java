package dev.dual.core.execution;

public enum AgentExecutionTimelineEventKind {
    COMMAND_INTENT("command_intent"),
    COMMAND_RESULT("command_result"),
    PROCESS_STARTED("process_started"),
    PROCESS_WAIT_STARTED("process_wait_started"),
    PROMPT_ACCEPTED("prompt_accepted"),
    PROMPT_COMPLETED("prompt_completed"),
    NATIVE_PERMISSION_REQUEST("native_permission_request"),
    NATIVE_PERMISSION_REPLY("native_permission_reply"),
    PERMISSION_DECISION("permission_decision"),
    SESSION_CLOSURE("session_closure"),
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
