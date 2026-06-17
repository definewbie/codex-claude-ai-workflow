package dev.dual.server;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerPolicyTest {
    private final SchedulerPolicy policy = new SchedulerPolicy();

    @Test
    void runningWorkstreamCanResume() {
        var decision = policy.decide("RUNNING", false, Map.of());

        assertEquals(SchedulerDecision.Action.RESUME, decision.action());
    }

    @Test
    void recoveryWorkstreamIsSkippedWhenAutoRecoverDisabled() {
        var decision = policy.decide("WAITING_FOR_RECOVERY", false, Map.of(
                "classification", "recoverable_agent_runtime",
                "retryReadiness", "RETRY_AFTER_EXTERNAL_FIX"
        ));

        assertEquals(SchedulerDecision.Action.SKIP, decision.action());
        assertEquals("WAITING_FOR_RECOVERY", decision.reason());
    }

    @Test
    void retryReadyRuntimeRecoveryCanAutoRecoverWhenEnabled() {
        var decision = policy.decide("WAITING_FOR_RECOVERY", true, Map.of(
                "classification", "recoverable_agent_runtime",
                "retryReadiness", "RETRY_AFTER_EXTERNAL_FIX"
        ));

        assertEquals(SchedulerDecision.Action.AUTO_RECOVER, decision.action());
        assertEquals("recoverable_agent_runtime", decision.recoveryClassification());
        assertEquals("RETRY_AFTER_EXTERNAL_FIX", decision.retryReadiness());
    }

    @Test
    void artifactContractFailureCannotAutoRecover() {
        var decision = policy.decide("WAITING_FOR_RECOVERY", true, Map.of(
                "classification", "artifact_contract_failure",
                "retryReadiness", "RETRY_AFTER_OUTPUT_CONTRACT_FIX"
        ));

        assertEquals(SchedulerDecision.Action.SKIP, decision.action());
        assertEquals("recovery:artifact_contract_failure/RETRY_AFTER_OUTPUT_CONTRACT_FIX", decision.reason());
    }

    @Test
    void semanticRevisionLimitCannotAutoRecover() {
        var decision = policy.decide("WAITING_FOR_RECOVERY", true, Map.of(
                "classification", "semantic_revision_limit",
                "retryReadiness", "NEEDS_HUMAN_DECISION"
        ));

        assertEquals(SchedulerDecision.Action.SKIP, decision.action());
        assertEquals("recovery:semantic_revision_limit/NEEDS_HUMAN_DECISION", decision.reason());
    }
}
