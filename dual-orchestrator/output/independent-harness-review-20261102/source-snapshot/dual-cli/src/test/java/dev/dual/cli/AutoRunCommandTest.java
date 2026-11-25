package dev.dual.cli;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRunCommandTest {
    @Test
    void designReviewAlwaysRequiresOperatorDecision() {
        var config = Map.<String, Object>of("gate_purpose", "design");

        assertTrue(AutoRunCommand.requiresHumanDecision(
                config, "gate.human_design_approval", "READY_FOR_ARCH_REVIEW"));
        assertTrue(AutoRunCommand.requiresHumanDecision(
                config, "gate.human_design_approval", "NEEDS_REVISION"));
        assertTrue(AutoRunCommand.requiresHumanDecision(
                config, "gate.human_design_approval", "BLOCKED"));
    }

    @Test
    void negativeAdvisoryCannotBeAutoApprovedAtOtherGates() {
        var config = Map.<String, Object>of("gate_purpose", "contract");

        assertTrue(AutoRunCommand.requiresHumanDecision(
                config, "gate.contract_review", "NEEDS_REVISION"));
        assertTrue(AutoRunCommand.requiresHumanDecision(
                config, "gate.contract_review", "BLOCKED"));
        assertFalse(AutoRunCommand.requiresHumanDecision(
                config, "gate.contract_review", "READY_FOR_CONTRACT_REVIEW"));
    }
    @Test
    void autorunApprovesIntermediateHumanGates() {
        assertEquals("approve", AutoRunCommand.gateActionFor("gate.human_requirement_review"));
        assertEquals("approve", AutoRunCommand.gateActionFor("gate.contract_review"));
        assertEquals("approve", AutoRunCommand.gateActionFor("gate.human_acceptance"));
    }

    @Test
    void autorunCompletesFinalNextStageGate() {
        assertEquals("complete", AutoRunCommand.gateActionFor("gate.human_next_stage_approval"));
    }

    @Test
    void autorunGateActionsCannotAuthorizeRevision() {
        var automaticActions = List.of(
                AutoRunCommand.gateActionFor("gate.human_requirement_review"),
                AutoRunCommand.gateActionFor("gate.contract_review"),
                AutoRunCommand.gateActionFor("gate.human_design_approval"),
                AutoRunCommand.gateActionFor("gate.human_plan_approval"),
                AutoRunCommand.gateActionFor("gate.human_acceptance"),
                AutoRunCommand.gateActionFor("gate.human_next_stage_approval"),
                AutoRunCommand.gateActionFor("gate.unknown_future_gate")
        );

        assertTrue(automaticActions.stream().allMatch(Set.of("approve", "complete")::contains));
        assertFalse(automaticActions.contains("revise"));
    }

    @Test
    void autorunStopsWhenAGateRepeatsInTheSameRun() {
        var seen = new LinkedHashSet<String>();
        seen.add("gate.contract_review@iter-001");

        assertTrue(AutoRunCommand.isRepeatedGate(gate("gate.contract_review", "iter-001"), seen));
        assertFalse(AutoRunCommand.isRepeatedGate(gate("gate.human_design_approval", "iter-001"), seen));
    }

    @Test
    void autorunAllowsSameGateDefinitionInANewRepairIteration() {
        var seen = new LinkedHashSet<String>();
        seen.add("gate.contract_review@iter-001");

        assertFalse(AutoRunCommand.isRepeatedGate(gate("gate.contract_review", "iter-002"), seen));
    }

    @Test
    void autorunLetsRuntimeHandleWaitingForRecoveryBeforeStopping() {
        assertTrue(AutoRunCommand.shouldRunBeforeStop(dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_RECOVERY));
        assertFalse(AutoRunCommand.shouldRunBeforeStop(dev.dual.core.workflow.WorkflowStatus.BLOCKED));
        assertFalse(AutoRunCommand.shouldRunBeforeStop(dev.dual.core.workflow.WorkflowStatus.FAILED));
    }

    @Test
    void autorunDoesNotRepeatTheSameRecoveryNode() {
        var seen = new LinkedHashSet<String>();
        var failed = failedRecoveryNode("nxe-001", 0);

        assertFalse(AutoRunCommand.isRepeatedRecovery(failed, seen));
        assertTrue(AutoRunCommand.isRepeatedRecovery(failed, seen));
        assertTrue(AutoRunCommand.isRepeatedRecovery(failedRecoveryNode("nxe-002", 1), seen));
        assertFalse(AutoRunCommand.isRepeatedRecovery(
                failedRecoveryNode("node-design-codex_arch_review-021", "nxe-003", 0),
                seen
        ));
    }

    @Test
    void autorunContinuesWhenRunSegmentEntersANewRecoverableSnapshot() {
        var seen = new LinkedHashSet<String>();
        var failed = failedRecoveryNode("nxe-001", 0);

        assertTrue(AutoRunCommand.shouldContinueAfterRunFailure(
                dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_RECOVERY,
                failed,
                seen
        ));
        assertFalse(seen.contains(AutoRunCommand.recoveryKey(failed)),
                "post-run recovery check must not mark the snapshot as attempted before the next loop executes it");
        assertFalse(AutoRunCommand.isRepeatedRecovery(failed, seen));
        assertFalse(AutoRunCommand.shouldContinueAfterRunFailure(
                dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_RECOVERY,
                failed,
                seen
        ));
        assertFalse(AutoRunCommand.shouldContinueAfterRunFailure(
                dev.dual.core.workflow.WorkflowStatus.FAILED,
                failedRecoveryNode("nxe-002", 1),
                seen
        ));
    }

    private static NodeInstanceDocument gate(String nodeDefinitionId) {
        return gate(nodeDefinitionId, "iter-001");
    }

    private static NodeInstanceDocument gate(String nodeDefinitionId, String iterationId) {
        return new NodeInstanceDocument(
                1,
                "node-" + nodeDefinitionId.replace('.', '-'),
                nodeDefinitionId,
                NodeKind.GATE,
                "human.review",
                NodeStatus.WAITING,
                "gate",
                iterationId,
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                "",
                "",
                null,
                null
        );
    }

    private static NodeInstanceDocument failedRecoveryNode(String latestExecutionId, int autoRetryCount) {
        return failedRecoveryNode("node-intake-codex_product_review-003", latestExecutionId, autoRetryCount);
    }

    private static NodeInstanceDocument failedRecoveryNode(String nodeInstanceId, String latestExecutionId, int autoRetryCount) {
        return new NodeInstanceDocument(
                1,
                nodeInstanceId,
                "intake.codex_product_review",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.FAILED,
                "intake",
                "iter-001",
                List.of(),
                Map.of(),
                List.of(),
                Map.of(
                        "acpx_failure.recoverable", true,
                        "acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT",
                        "auto_retry_count", autoRetryCount
                ),
                "",
                latestExecutionId,
                null,
                null
        );
    }
}
