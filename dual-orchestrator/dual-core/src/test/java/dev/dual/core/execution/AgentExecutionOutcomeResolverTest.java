package dev.dual.core.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionOutcomeResolverTest {
    private final AgentExecutionOutcomeResolver resolver = new AgentExecutionOutcomeResolver();

    @Test
    void processOkTerminalValidArtifactValidSucceeds() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS, outcome.status());
        assertTrue(outcome.nodeMaySucceed());
        assertEquals(AgentExecutionRetryPolicy.NONE, outcome.retryPolicy());
        assertEquals(RecoveryCategory.NONE, outcome.recoveryCategory());
    }

    @Test
    void processOkTerminalValidArtifactMissingFailsContract() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.CONTRACT_FAILED, outcome.status());
        assertEquals("required_artifact_contract_failed", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(AgentExecutionRetryPolicy.MANUAL_ONLY, outcome.retryPolicy());
        assertEquals(RecoveryCategory.ARTIFACT_CONTRACT_FAILURE, outcome.recoveryCategory());
    }

    @Test
    void reconnectWithValidTerminalAndArtifactCannotSucceed() {
        var reconnect = AdapterSignal.of(AdapterSignalType.AGENT_RECONNECT_REQUIRED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(reconnect)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("agent_reconnect_required", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
        assertEquals(List.of(reconnect), outcome.secondarySignals());
    }

    @Test
    void reconnectWithValidTerminalButMissingArtifactKeepsAdapterBoundaryAsPrimary() {
        var reconnect = AdapterSignal.of(AdapterSignalType.AGENT_RECONNECT_REQUIRED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of(reconnect)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("agent_reconnect_required", outcome.primaryReason());
        assertEquals(List.of(reconnect), outcome.secondarySignals());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
    }

    @Test
    void missingTerminalControlIsOutputIncomplete() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.missing(),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.OUTPUT_INCOMPLETE, outcome.status());
        assertEquals("terminal_control_missing_or_invalid", outcome.primaryReason());
        assertEquals(RecoveryCategory.OUTPUT_INCOMPLETE, outcome.recoveryCategory());
    }

    @Test
    void blockingAdapterSignalPreventsSuccess() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(AdapterSignal.of(AdapterSignalType.AUTH_FAILED))
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("auth_failed", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
    }

    @Test
    void rateLimitWithValidTerminalAndArtifactRemainsRecoverableAdapterFailure() {
        var signal = AdapterSignal.of(AdapterSignalType.RATE_LIMITED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(signal)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("rate_limited", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
        assertEquals(List.of(signal), outcome.secondarySignals());
    }

    @Test
    void providerConnectionWithMissingArtifactKeepsAdapterBoundaryPrimary() {
        var signal = AdapterSignal.of(AdapterSignalType.PROVIDER_CONNECTION_FAILED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of(signal)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("provider_connection_failed", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
        assertEquals(List.of(signal), outcome.secondarySignals());
    }

    @Test
    void nonZeroIncompleteWithValidTerminalAndArtifactDoesNotSucceed() {
        var outcome = resolve(
                ProcessResult.nonZeroIncomplete(5),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.OUTPUT_INCOMPLETE, outcome.status());
        assertEquals("process_output_incomplete", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.OUTPUT_INCOMPLETE, outcome.recoveryCategory());
    }

    @Test
    void timeoutCannotSucceed() {
        var outcome = resolve(
                ProcessResult.timedOutProcess(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.TIMEOUT, outcome.status());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.TIMEOUT_OR_STALLED, outcome.recoveryCategory());
    }

    @Test
    void interruptedCannotSucceed() {
        var outcome = resolve(
                ProcessResult.interruptedProcess(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.INTERRUPTED, outcome.status());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.INTERRUPTED, outcome.recoveryCategory());
    }

    @Test
    void nonZeroCompleteWithValidEvidenceSucceedsWithWarnings() {
        var outcome = resolve(
                ProcessResult.nonZeroComplete(5),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS, outcome.status());
        assertTrue(outcome.nodeMaySucceed());
    }

    @Test
    void nonZeroIncompleteWithValidTerminalButMissingArtifactFailsContract() {
        var outcome = resolve(
                ProcessResult.nonZeroIncomplete(5),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.CONTRACT_FAILED, outcome.status());
        assertEquals(RecoveryCategory.ARTIFACT_CONTRACT_FAILURE, outcome.recoveryCategory());
    }

    @Test
    void killedAfterRecoverableSignalCannotSucceedEvenWithValidEvidence() {
        var reconnect = AdapterSignal.of(AdapterSignalType.AGENT_RECONNECT_REQUIRED);

        var outcome = resolve(
                ProcessResult.killedAfterRecoverableSignal(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(reconnect)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(List.of(reconnect), outcome.secondarySignals());
    }

    @Test
    void agentExecutionOwnsOutcomeAndRuntimeFacts() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        var execution = new AgentExecution(
                "agent-exec-1",
                "ws-test",
                "wfe-1",
                "nxe-1",
                "agent-task-1",
                "claude",
                "claude-main",
                "prompts/prompt.md",
                "outputs/output.md",
                Instant.parse("2026-06-22T00:00:00Z"),
                Instant.parse("2026-06-22T00:00:01Z"),
                ProcessResult.exitedZero(),
                List.of(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                null,
                null,
                outcome
        );

        assertEquals("agent-exec-1", execution.id());
        assertEquals(AgentExecutionOutcomeStatus.SUCCESS, execution.outcome().status());
    }

    @Test
    void agentExecutionRejectsMissingOutcome() {
        assertThrows(IllegalArgumentException.class, () -> new AgentExecution(
                "agent-exec-1",
                "ws-test",
                "",
                "",
                "",
                "claude",
                "claude-main",
                "",
                "",
                null,
                null,
                ProcessResult.exitedZero(),
                List.of(),
                TerminalControlResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(),
                null,
                null,
                null
        ));
    }

    private AgentExecutionOutcome resolve(
            ProcessResult process,
            TerminalControlResult terminal,
            List<ArtifactContractResult> artifacts,
            List<AdapterSignal> signals
    ) {
        return resolver.resolve(new AgentExecutionEvidence(process, terminal, artifacts, signals));
    }
}
