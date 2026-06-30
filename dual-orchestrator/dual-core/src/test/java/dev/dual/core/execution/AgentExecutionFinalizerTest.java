package dev.dual.core.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutionFinalizerTest {
    private final AgentExecutionFinalizer finalizer = new AgentExecutionFinalizer();

    @Test
    void validEvidenceProducesConsistentCompletedAttemptAndLegacyFields() {
        var finalized = finalizer.finalize(baseAttempt(), evidence(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PLAN_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        ));

        assertEquals(AgentExecutionAttemptStatus.COMPLETED, finalized.attempt().status());
        assertEquals(AgentExecutionProcessStatus.EXITED, finalized.attempt().processStatus());
        assertEquals(AgentExecutionOutcomeStatus.SUCCESS, finalized.outcome().status());
        assertEquals("completed", finalized.legacyStatus());
        assertNull(finalized.legacyFailureReason());
        assertFalse(finalized.legacyRecoverable());
    }

    @Test
    void contractFailureProducesContractFailedAttemptAndLegacyFailure() {
        var finalized = finalizer.finalize(baseAttempt(), evidence(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PLAN_REVIEW"),
                List.of(ArtifactContractResult.requiredMissing("plan_artifact")),
                List.of()
        ));

        assertEquals(AgentExecutionAttemptStatus.CONTRACT_FAILED, finalized.attempt().status());
        assertEquals(AgentExecutionProcessStatus.EXITED, finalized.attempt().processStatus());
        assertEquals(AgentExecutionOutcomeStatus.CONTRACT_FAILED, finalized.outcome().status());
        assertEquals("failed", finalized.legacyStatus());
        assertEquals("required_artifact_contract_failed", finalized.legacyFailureReason());
        assertTrue(finalized.legacyRecoverable());
    }

    @Test
    void reconnectWarningWithCompleteEvidenceSucceedsWithCompletedAttempt() {
        var finalized = finalizer.finalize(baseAttempt(), evidence(
                ProcessResult.exitedZero(),
                TerminalControlResult.valid("READY_FOR_PLAN_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of(AdapterSignal.of(AdapterSignalType.AGENT_RECONNECT_REQUIRED))
        ));

        assertEquals(AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS, finalized.outcome().status());
        assertEquals(AgentExecutionAttemptStatus.COMPLETED, finalized.attempt().status());
        assertNull(finalized.attempt().failureSignal());
        assertEquals("completed", finalized.legacyStatus());
    }

    @Test
    void timeoutProducesTimedOutAttemptAndNoSuccessContradiction() {
        var finalized = finalizer.finalize(baseAttempt(), evidence(
                ProcessResult.timedOutProcess(),
                TerminalControlResult.valid("READY_FOR_PLAN_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        ));

        assertEquals(AgentExecutionOutcomeStatus.TIMEOUT, finalized.outcome().status());
        assertEquals(AgentExecutionAttemptStatus.TIMED_OUT, finalized.attempt().status());
        assertEquals(AgentExecutionProcessStatus.TIMED_OUT, finalized.attempt().processStatus());
        assertEquals("failed", finalized.legacyStatus());
        assertEquals("process_timed_out", finalized.legacyFailureReason());
        assertTrue(finalized.legacyRecoverable());
    }

    @Test
    void stalledProducesStalledAttemptAndNoSuccessContradiction() {
        var stalled = finalizer.finalize(baseAttempt(), evidence(
                new ProcessResult("", null, null, ProcessUsability.STALLED, 0, "", "", false, false),
                TerminalControlResult.valid("READY_FOR_PLAN_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        ));

        assertEquals(AgentExecutionOutcomeStatus.TIMEOUT, stalled.outcome().status());
        assertEquals("process_stalled", stalled.outcome().primaryReason());
        assertEquals(AgentExecutionAttemptStatus.STALLED, stalled.attempt().status());
        assertEquals(AgentExecutionProcessStatus.STALLED, stalled.attempt().processStatus());
        assertTrue(stalled.legacyRecoverable());
    }

    @Test
    void interruptedProducesInterruptedAttemptAndNoSuccessContradiction() {
        var finalized = finalizer.finalize(baseAttempt(), evidence(
                ProcessResult.interruptedProcess(),
                TerminalControlResult.valid("READY_FOR_PLAN_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")),
                List.of()
        ));

        assertEquals(AgentExecutionOutcomeStatus.INTERRUPTED, finalized.outcome().status());
        assertEquals(AgentExecutionAttemptStatus.INTERRUPTED, finalized.attempt().status());
        assertEquals(AgentExecutionProcessStatus.INTERRUPTED, finalized.attempt().processStatus());
        assertEquals("failed", finalized.legacyStatus());
        assertEquals("process_interrupted", finalized.legacyFailureReason());
        assertTrue(finalized.legacyRecoverable());
    }

    private static AgentExecutionAttempt baseAttempt() {
        return new AgentExecutionAttempt(
                "att-run-1",
                "run-1",
                "ws-1",
                "plan.claude_plan",
                "claude",
                AgentExecutionAttemptStatus.PROCESS_EXITED,
                AgentExecutionProcessStatus.EXITED,
                null,
                false,
                false,
                "process exited",
                "runs/run-1/stderr.log",
                "runs/run-1/transcript.ndjson",
                "claude-main-ws-1",
                "session-1",
                "workstream",
                "ws:ws-1",
                Instant.parse("2026-06-30T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:10Z")
        );
    }

    private static AgentExecutionEvidence evidence(
            ProcessResult process,
            TerminalControlResult terminal,
            List<ArtifactContractResult> artifacts,
            List<AdapterSignal> signals
    ) {
        return new AgentExecutionEvidence(process, terminal, artifacts, signals);
    }
}
