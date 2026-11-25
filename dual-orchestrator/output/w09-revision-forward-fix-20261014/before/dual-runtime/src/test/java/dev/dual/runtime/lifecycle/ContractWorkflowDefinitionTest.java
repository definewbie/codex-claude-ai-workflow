package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractWorkflowDefinitionTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void contractVariantContainsContractNodes() {
        WorkflowDefinition wf = factory.forVariant("contract");

        assertDoesNotThrow(() -> wf.node("contract.compile"),
                "contract variant should contain node contract.compile");
        assertDoesNotThrow(() -> wf.node("gate.contract_review"),
                "contract variant should contain node gate.contract_review");
    }

    @Test
    void contractVariantDeclaresTypedProposalAsProductReviewContextRequirement() {
        var contextNode = factory.forVariant("contract").node("context.intake_codex_product_review");

        assertEquals("task_contract_proposal", contextNode.config().get("required_input_artifact"));
    }

    @Test
    void executionApprovalRoutesToImplementationAfterDesignAndPlan() {
        WorkflowDefinition wf = factory.forVariant("contract");

        var approvedEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "approved".equals(e.on()))
                .toList();

        assertEquals(1, approvedEdges.size(),
                "Expected exactly one edge from gate.contract_review on 'approved'");
        assertEquals("context.implement_claude", approvedEdges.get(0).to());
    }

    @Test
    void contractVariantRoutesClarifyBackToTypedProposalOwner() {
        WorkflowDefinition wf = factory.forVariant("contract");

        var clarifyEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "clarify".equals(e.on()))
                .toList();

        assertEquals(1, clarifyEdges.size(),
                "Expected exactly one edge from gate.contract_review on 'clarify'");
        assertEquals("intake.claude_brainstorm", clarifyEdges.get(0).to());
        assertEquals("loop", clarifyEdges.get(0).metadata().transitionType());

        var reduceScopeEdges = wf.edgesFrom("gate.contract_review").stream()
                .filter(e -> "reduce_scope".equals(e.on()))
                .toList();

        assertTrue(reduceScopeEdges.isEmpty(),
                "Unsupported scope deletion must not be exposed as a working contract-review route");
        assertFalse(((java.util.List<?>) wf.node("gate.contract_review").config().get("allowed_actions"))
                .contains("reduce_scope"));
    }

    @Test
    void contractVariantRoutesAcceptanceRejectThroughFailureClassifier() {
        WorkflowDefinition wf = factory.forVariant("contract");

        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "contract variant should share the same typed acceptance failure classifier boundary");

        var rejectEdges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "REJECT".equals(e.on()))
                .toList();

        assertEquals(1, rejectEdges.size(),
                "Expected exactly one edge from acceptance.decision on 'REJECT'");
        assertEquals("acceptance.classify_failure", rejectEdges.get(0).to(),
                "Acceptance rejection must be classified before starting semantic repair");

        var implementationRepairEdges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_IMPLEMENTATION_BUG".equals(e.on()))
                .toList();

        assertEquals(1, implementationRepairEdges.size(),
                "Expected implementation repair classification to be routed");
        assertEquals("context.implement_claude", implementationRepairEdges.get(0).to());
        assertEquals("loop", implementationRepairEdges.get(0).metadata().transitionType(),
                "classified repair should start a semantic iteration");
    }

    @Test
    void contractVariantAcceptWithNotesBypassesFailureClassifier() {
        WorkflowDefinition wf = factory.forVariant("contract");

        var acceptedEdges = wf.edgesFrom("acceptance.decision").stream()
                .filter(edge -> "ACCEPT_WITH_NOTES".equals(edge.on()))
                .toList();

        assertEquals(1, acceptedEdges.size());
        assertEquals("acceptance.generate_final_report", acceptedEdges.get(0).to());
        assertTrue(wf.edgesFrom("acceptance.classify_failure").stream()
                        .noneMatch(edge -> edge.on().startsWith("NOTED_")),
                "accepted decisions must bypass failure classification");
    }

}
