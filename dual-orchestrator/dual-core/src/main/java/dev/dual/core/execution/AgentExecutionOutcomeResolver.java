package dev.dual.core.execution;

import java.util.List;

/**
 * Resolves one agent execution into a workflow-consumable outcome.
 *
 * <p>The rule order is deliberate: blocking adapter failures and unusable
 * process states cannot be hidden by a partial terminal control block; artifact
 * contract failures remain primary when the process is otherwise usable.</p>
 */
public final class AgentExecutionOutcomeResolver {
    public AgentExecutionOutcome resolve(AgentExecutionEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("evidence is required");
        }
        var process = evidence.processResult();
        var signals = evidence.adapterSignals();
        var blockingSignal = signals.stream()
                .filter(signal -> signal.type().blocksSuccess())
                .findFirst();
        if (blockingSignal.isPresent()) {
            return adapterFailure(
                    AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE,
                    blockingSignal.get(),
                    "Blocking adapter signal: " + blockingSignal.get().type(),
                    AgentExecutionRetryPolicy.MANUAL_ONLY
            );
        }
        if (process.usability() == ProcessUsability.TIMED_OUT
                || process.usability() == ProcessUsability.STALLED) {
            return new AgentExecutionOutcome(
                    AgentExecutionOutcomeStatus.TIMEOUT,
                    "process_" + process.usability().name().toLowerCase(),
                    signals,
                    false,
                    AgentExecutionRetryPolicy.MANUAL_ONLY,
                    RecoveryCategory.TIMEOUT_OR_STALLED,
                    "The agent process did not complete within the allowed runtime boundary."
            );
        }
        if (process.usability() == ProcessUsability.INTERRUPTED) {
            return new AgentExecutionOutcome(
                    AgentExecutionOutcomeStatus.INTERRUPTED,
                    "process_interrupted",
                    signals,
                    false,
                    AgentExecutionRetryPolicy.MANUAL_ONLY,
                    RecoveryCategory.INTERRUPTED,
                    "The agent process was interrupted before a trustworthy completion boundary."
            );
        }
        if (!evidence.terminalControl().present() || !evidence.terminalControl().valid()) {
            return new AgentExecutionOutcome(
                    AgentExecutionOutcomeStatus.OUTPUT_INCOMPLETE,
                    "terminal_control_missing_or_invalid",
                    signals,
                    false,
                    AgentExecutionRetryPolicy.MANUAL_ONLY,
                    RecoveryCategory.OUTPUT_INCOMPLETE,
                    "The agent output did not contain a valid terminal control block."
            );
        }
        var unsatisfiedArtifacts = evidence.artifactContracts().stream()
                .filter(result -> !result.satisfied())
                .toList();
        if (!unsatisfiedArtifacts.isEmpty()) {
            return new AgentExecutionOutcome(
                    AgentExecutionOutcomeStatus.CONTRACT_FAILED,
                    "required_artifact_contract_failed",
                    signals,
                    false,
                    AgentExecutionRetryPolicy.MANUAL_ONLY,
                    RecoveryCategory.ARTIFACT_CONTRACT_FAILURE,
                    "The agent terminal control was present, but one or more required structured artifacts failed validation."
            );
        }
        if (process.usability() == ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT
                || process.usability() == ProcessUsability.KILLED_AFTER_RECOVERABLE_SIGNAL) {
            var status = signals.isEmpty()
                    ? AgentExecutionOutcomeStatus.OUTPUT_INCOMPLETE
                    : AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE;
            var category = signals.isEmpty()
                    ? RecoveryCategory.OUTPUT_INCOMPLETE
                    : RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE;
            return new AgentExecutionOutcome(
                    status,
                    "process_output_incomplete",
                    signals,
                    false,
                    AgentExecutionRetryPolicy.MANUAL_ONLY,
                    category,
                    "The agent process ended before the process boundary was trustworthy."
            );
        }
        if (process.usability() == ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT
                || hasRecoverableWarning(signals)) {
            return new AgentExecutionOutcome(
                    AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS,
                    "completed_with_adapter_or_process_warnings",
                    signals,
                    true,
                    AgentExecutionRetryPolicy.NONE,
                    RecoveryCategory.NONE,
                    "The agent produced valid terminal and artifact evidence, but adapter/process warnings were recorded."
            );
        }
        return new AgentExecutionOutcome(
                AgentExecutionOutcomeStatus.SUCCESS,
                "completed",
                List.of(),
                true,
                AgentExecutionRetryPolicy.NONE,
                RecoveryCategory.NONE,
                "The agent produced valid terminal and artifact evidence."
        );
    }

    private static boolean hasRecoverableWarning(List<AdapterSignal> signals) {
        return signals.stream().anyMatch(signal -> signal.type().severity() == AdapterSignalSeverity.RECOVERABLE);
    }

    private static AgentExecutionOutcome adapterFailure(
            AgentExecutionOutcomeStatus status,
            AdapterSignal primarySignal,
            String message,
            AgentExecutionRetryPolicy retryPolicy
    ) {
        return new AgentExecutionOutcome(
                status,
                primarySignal.type().name().toLowerCase(),
                List.of(primarySignal),
                false,
                retryPolicy,
                status == AgentExecutionOutcomeStatus.NON_RECOVERABLE_ADAPTER_FAILURE
                        ? RecoveryCategory.NON_RECOVERABLE_ADAPTER_FAILURE
                        : RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE,
                message
        );
    }
}
