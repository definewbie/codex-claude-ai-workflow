package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractReviewGateTest {
    @TempDir
    Path tempDir;

    @Test
    void humanReviewGateSupportsContractReviewActions() throws Exception {
        var handler = new HumanReviewGateHandler();
        var f = new AcceptanceRepairSequenceTest.Fixture(tempDir, "CONTRACT_GAP", "tool.py");
        f.until("gate.human_requirement_review"); f.approve(); f.until("gate.contract_review");
        var node = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        var result = handler.openOrInspect(new GateHandler.GateContext(
                tempDir,
                f.ws,
                f.ledger.loadDefinition(f.paths).node(node.nodeDefinitionId()),
                new NodeInstance(node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(),
                        node.status(), node.stageId(), node.iterationId(), node.inputRefs(), node.outputRefs(), node.result(),
                        node.runRef(), node.startedAt(), node.finishedAt()),
                f.ledger.loadOpenGateForNode(f.paths, node.nodeInstanceId()).result().get("source_outcome").toString()
        ));

        assertEquals(NodeStatus.WAITING, result.status());
        assertEquals("open", result.outcome());
        var actions = (List<?>) result.result().get("allowed_actions");
        assertEquals(3, actions.size());
        assertTrue(actions.contains("approve"));
        assertTrue(actions.contains("clarify"));
        assertTrue(actions.contains("reject"));
    }

    @Test
    void contractCompileRegisteredInContractRegistry() {
        assertTrue(ContractRegistry.hasContract("contract.compile"));

        var contract = ContractRegistry.forNode("contract.compile");
        var decisions = contract.allowedDecisions();
        assertTrue(decisions.contains("READY_FOR_REVIEW"));
        assertTrue(decisions.contains("NEEDS_CLARIFICATION"));
        assertTrue(decisions.contains("NEEDS_SCOPE_REDUCTION"));
        assertTrue(decisions.contains("REJECTED"));
    }

    @Test
    void contractCompileNotUseApprovedDraft() {
        var contract = ContractRegistry.forNode("contract.compile");
        assertFalse(contract.allowedDecisions().contains("APPROVED_DRAFT"));
    }

}
