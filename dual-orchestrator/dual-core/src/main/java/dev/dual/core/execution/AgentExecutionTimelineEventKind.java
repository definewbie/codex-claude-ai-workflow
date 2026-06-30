package dev.dual.core.execution;

public enum AgentExecutionTimelineEventKind {
    PROCESS_STARTED("process_started"),
    PROCESS_WAIT_COMPLETED("process_wait_completed"),
    TERMINAL_CONTROL_DETECTED("terminal_control_detected"),
    TERMINAL_CONTROL_GRACE_ELAPSED("terminal_control_grace_elapsed"),
    RECOVERABLE_SIGNAL_DETECTED("recoverable_signal_detected"),
    PROCESS_EXITED("process_exited");

    private final String wireName;

    AgentExecutionTimelineEventKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
