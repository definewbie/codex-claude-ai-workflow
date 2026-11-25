package dev.dual.core.execution;

/**
 * Describes whether the external agent process produced output that can be
 * trusted by the orchestrator. This is intentionally separate from exit code:
 * a non-zero process may still leave a complete terminal contract, while a
 * killed or stalled process must not be treated as a successful node.
 */
public enum ProcessUsability {
    EXITED_ZERO,
    EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT,
    EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
    TIMED_OUT,
    INTERRUPTED,
    STALLED,
    KILLED_AFTER_RECOVERABLE_SIGNAL
}
