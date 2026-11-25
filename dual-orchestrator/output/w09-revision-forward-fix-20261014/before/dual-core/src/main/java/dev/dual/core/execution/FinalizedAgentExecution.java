package dev.dual.core.execution;

/**
 * Consistent terminal read model for one agent execution.
 *
 * <p>The attempt, semantic outcome, and legacy run fields must be written
 * together so projections do not observe contradictory states.</p>
 */
public record FinalizedAgentExecution(
        AgentExecutionAttempt attempt,
        AgentExecutionOutcome outcome,
        String legacyStatus,
        String legacyFailureReason,
        boolean legacyRecoverable
) {
    public FinalizedAgentExecution {
        if (attempt == null) {
            throw new IllegalArgumentException("attempt is required");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (legacyStatus == null || legacyStatus.isBlank()) {
            throw new IllegalArgumentException("legacyStatus is required");
        }
        if (outcome.nodeMaySucceed() && !"completed".equals(legacyStatus)) {
            throw new IllegalArgumentException("successful outcome must map to completed legacy status");
        }
        if (!outcome.nodeMaySucceed() && !"failed".equals(legacyStatus)) {
            throw new IllegalArgumentException("failed outcome must map to failed legacy status");
        }
        if (outcome.nodeMaySucceed() && legacyFailureReason != null) {
            throw new IllegalArgumentException("successful outcome cannot carry legacyFailureReason");
        }
        if (outcome.nodeMaySucceed() && legacyRecoverable) {
            throw new IllegalArgumentException("successful outcome cannot be legacy recoverable");
        }
    }
}
