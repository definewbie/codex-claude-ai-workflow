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
    void processOkAgentResultValidArtifactValidSucceeds() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS, outcome.status());
        assertTrue(outcome.nodeMaySucceed());
        assertEquals(AgentExecutionRetryPolicy.NONE, outcome.retryPolicy());
        assertEquals(RecoveryCategory.NONE, outcome.recoveryCategory());
    }

    @Test
    void processOkAgentResultValidArtifactMissingFailsContract() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
    void reconnectWithValidAgentResultAndArtifactCannotSucceed() {
        var reconnect = AdapterSignal.of(AdapterSignalType.AGENT_RECONNECT_REQUIRED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
    void supersededReconnectCannotBlockAValidOccurrence() {
        var reconnect = new AdapterSignal(
                AdapterSignalType.AGENT_RECONNECT_REQUIRED,
                "stale reconnect",
                1,
                "stderr",
                "session-1",
                true
        );

        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(reconnect)
        );

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS, outcome.status());
        assertTrue(outcome.nodeMaySucceed());
        assertTrue(outcome.secondarySignals().isEmpty());
    }

    @Test
    void reconnectWithValidAgentResultButMissingArtifactKeepsAdapterBoundaryAsPrimary() {
        var reconnect = AdapterSignal.of(AdapterSignalType.AGENT_RECONNECT_REQUIRED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of(reconnect)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("agent_reconnect_required", outcome.primaryReason());
        assertEquals(List.of(reconnect), outcome.secondarySignals());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
    }

    @Test
    void missingAgentResultIsOutputIncomplete() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.missing(),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.OUTPUT_INCOMPLETE, outcome.status());
        assertEquals("agent_result_missing_or_invalid", outcome.primaryReason());
        assertEquals(RecoveryCategory.OUTPUT_INCOMPLETE, outcome.recoveryCategory());
    }

    @Test
    void blockingAdapterSignalPreventsSuccess() {
        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(AdapterSignal.of(AdapterSignalType.AUTH_FAILED))
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("auth_failed", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
    }

    @Test
    void permissionBlockedRemainsPrimaryWhenAgentResultIsInvalid() {
        var permission = new AdapterSignal(
                AdapterSignalType.PERMISSION_BLOCKED,
                "current ACP turn permission denied: Run tests: mvn test",
                7,
                "acpx_stream",
                "claude-main",
                false
        );
        var outcome = resolve(
                ProcessResult.nonZeroComplete(5),
                AgentResultContractResult.invalid("AGENT_RESULT_PARSE_FAILED"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of(permission)
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("permission_blocked", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
        assertEquals(AgentExecutionRetryPolicy.MANUAL_ONLY, outcome.retryPolicy());
        assertEquals(RecoveryCategory.RECOVERABLE_ADAPTER_FAILURE, outcome.recoveryCategory());
        assertTrue(outcome.operatorMessage().contains("mvn test"));
    }

    @Test
    void deniedOptionalToolCannotOverrideCompleteValidatedEvidence() {
        var signal = AdapterSignal.of(AdapterSignalType.PERMISSION_BLOCKED);

        var outcome = resolve(
                ProcessResult.nonZeroComplete(5),
                AgentResultContractResult.valid("READY_FOR_ARCH_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(signal)
        );

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS, outcome.status());
        assertEquals("completed_with_adapter_or_process_warnings", outcome.primaryReason());
        assertTrue(outcome.nodeMaySucceed());
        assertEquals(List.of(signal), outcome.secondarySignals());
        assertEquals(AgentExecutionRetryPolicy.NONE, outcome.retryPolicy());
    }

    @Test
    void deniedToolStillBlocksWhenAnyRequiredArtifactIsInvalid() {
        var outcome = resolve(
                ProcessResult.nonZeroComplete(5),
                AgentResultContractResult.valid("READY_FOR_ARCH_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")),
                List.of(AdapterSignal.of(AdapterSignalType.PERMISSION_BLOCKED))
        );

        assertEquals(AgentExecutionOutcomeStatus.RECOVERABLE_ADAPTER_FAILURE, outcome.status());
        assertEquals("permission_blocked", outcome.primaryReason());
        assertFalse(outcome.nodeMaySucceed());
    }

    @Test
    void rateLimitWithValidAgentResultAndArtifactRemainsRecoverableAdapterFailure() {
        var signal = AdapterSignal.of(AdapterSignalType.RATE_LIMITED);

        var outcome = resolve(
                ProcessResult.exitedZero(),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
    void nonZeroIncompleteWithValidAgentResultAndArtifactDoesNotSucceed() {
        var outcome = resolve(
                ProcessResult.nonZeroIncomplete(5),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        );

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS, outcome.status());
        assertTrue(outcome.nodeMaySucceed());
    }

    @Test
    void nonZeroIncompleteWithValidAgentResultButMissingArtifactFailsContract() {
        var outcome = resolve(
                ProcessResult.nonZeroIncomplete(5),
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
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
                AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(),
                null,
                null,
                null
        ));
    }

    private AgentExecutionOutcome resolve(
            ProcessResult process,
            AgentResultContractResult agentResult,
            List<ArtifactContractResult> artifacts,
            List<AdapterSignal> signals
    ) {
        return resolver.resolve(new AgentExecutionEvidence(process, agentResult, artifacts, signals));
    }
}
