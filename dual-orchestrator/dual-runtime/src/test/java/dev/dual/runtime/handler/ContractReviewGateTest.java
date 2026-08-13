package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractReviewGateTest {
    @TempDir
    Path tempDir;

    @Test
    void humanReviewGateSupportsContractReviewActions() throws Exception {
        var handler = new HumanReviewGateHandler();
        var result = handler.openOrInspect(new GateHandler.GateContext(
                tempDir,
                "ws",
                new NodeDefinition("gate.contract_review", NodeKind.GATE, "human.review", Map.of(
                        "allowed_actions", List.of("approve", "clarify", "reject")
                )),
                node("gate.contract_review", NodeKind.GATE, "human.review"),
                "READY"
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

    private static NodeInstance node(String id, NodeKind kind, String handlerType) {
        return new NodeInstance(
                "node-" + id.replace('.', '-'),
                id,
                kind,
                handlerType,
                NodeStatus.READY,
                "stage-test",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );
    }
}
