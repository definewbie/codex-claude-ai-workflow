package dev.dual.core.execution;

/**
 * Separates adapter observations that only warn from observations that block a
 * successful node transition.
 */
public enum AdapterSignalSeverity {
    INFO,
    RECOVERABLE,
    BLOCKING
}
