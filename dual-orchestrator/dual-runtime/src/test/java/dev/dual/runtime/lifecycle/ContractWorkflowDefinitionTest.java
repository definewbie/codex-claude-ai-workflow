package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractWorkflowDefinitionTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void p5MainlineContainsContractNodes() {
        WorkflowDefinition wf = factory.p5Mainline();

        assertDoesNotThrow(() -> wf.node("contract.compile"),
                "p5Mainline should contain node contract.compile");
        assertDoesNotThrow(() -> wf.node("gate.contract_review"),
                "p5Mainline should contain node gate.contract_review");
    }

    @Test
    void p5MainlineRoutesContractReviewToDesign() {
        WorkflowDefinition wf = factory.p5Mainline();

        var approvedEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "approved".equals(e.on()))
                .toList();

        assertEquals(1, approvedEdges.size(),
                "Expected exactly one edge from gate.contract_review on 'approved'");
        assertEquals("design.claude_design", approvedEdges.get(0).to());
    }

    @Test
    void p5MainlineRoutesClarifyBackToCompile() {
        WorkflowDefinition wf = factory.p5Mainline();

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
    void thinMainlineDoesNotContainContractNodes() {
        WorkflowDefinition wf = factory.thinMainline();

        assertThrows(IllegalArgumentException.class, () -> wf.node("contract.compile"),
                "thinMainline should NOT contain node contract.compile");
        assertThrows(IllegalArgumentException.class, () -> wf.node("gate.contract_review"),
                "thinMainline should NOT contain node gate.contract_review");
    }
}
