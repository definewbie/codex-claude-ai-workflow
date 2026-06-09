package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractWorkflowDefinitionTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void contractMainlineContainsContractNodes() {
        WorkflowDefinition wf = factory.contractMainline();

        assertDoesNotThrow(() -> wf.node("contract.compile"),
                "contractMainline should contain node contract.compile");
        assertDoesNotThrow(() -> wf.node("gate.contract_review"),
                "contractMainline should contain node gate.contract_review");
    }

    @Test
    void contractMainlineRoutesContractReviewToDesign() {
        WorkflowDefinition wf = factory.contractMainline();

        var approvedEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "approved".equals(e.on()))
                .toList();

        assertEquals(1, approvedEdges.size(),
                "Expected exactly one edge from gate.contract_review on 'approved'");
        assertEquals("design.claude_design", approvedEdges.get(0).to());
    }

    @Test
    void contractMainlineRoutesClarifyBackToCompile() {
        WorkflowDefinition wf = factory.contractMainline();

        var clarifyEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "clarify".equals(e.on()))
                .toList();

        assertEquals(1, clarifyEdges.size(),
                "Expected exactly one edge from gate.contract_review on 'clarify'");
        assertEquals("contract.compile", clarifyEdges.get(0).to());

        var reduceScopeEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "reduce_scope".equals(e.on()))
                .toList();

        assertEquals(1, reduceScopeEdges.size(),
                "Expected exactly one edge from gate.contract_review on 'reduce_scope'");
        assertEquals("contract.compile", reduceScopeEdges.get(0).to());
    }

    @Test
    void baselineMainlineDoesNotContainContractNodes() {
        WorkflowDefinition wf = factory.baselineMainline();

        assertThrows(IllegalArgumentException.class, () -> wf.node("contract.compile"),
                "baselineMainline should NOT contain node contract.compile");
        assertThrows(IllegalArgumentException.class, () -> wf.node("gate.contract_review"),
                "baselineMainline should NOT contain node gate.contract_review");
    }
}
