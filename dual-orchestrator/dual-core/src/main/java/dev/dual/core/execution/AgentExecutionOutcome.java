package dev.dual.core.execution;

import java.util.List;

/**
 * The single semantic outcome for one agent execution.
 *
 * <p>Workflow runtime and projections should consume this object instead of
 * reinterpreting stderr, exit codes, terminal markdown, or artifact files.</p>
 */
public record AgentExecutionOutcome(
        AgentExecutionOutcomeStatus status,
        String primaryReason,
        List<AdapterSignal> secondarySignals,
        boolean nodeMaySucceed,
        AgentExecutionRetryPolicy retryPolicy,
        RecoveryCategory recoveryCategory,
        String operatorMessage
) {
    public AgentExecutionOutcome {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        primaryReason = requireText("primaryReason", primaryReason);
        secondarySignals = secondarySignals == null ? List.of() : List.copyOf(secondarySignals);
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retryPolicy is required");
        }
        if (recoveryCategory == null) {
            throw new IllegalArgumentException("recoveryCategory is required");
        }
        operatorMessage = requireText("operatorMessage", operatorMessage);
        if (nodeMaySucceed != (status == AgentExecutionOutcomeStatus.SUCCESS
                || status == AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS)) {
            throw new IllegalArgumentException("nodeMaySucceed must match success outcome status");
        }
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
