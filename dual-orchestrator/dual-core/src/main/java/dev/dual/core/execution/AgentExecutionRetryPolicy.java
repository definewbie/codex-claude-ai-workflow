package dev.dual.core.execution;

/**
 * Distinguishes automatic runtime retry from manual operator retry.
 */
public enum AgentExecutionRetryPolicy {
    NONE,
    MANUAL_ONLY,
    AUTO_ALLOWED
}
