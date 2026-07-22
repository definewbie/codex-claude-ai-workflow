package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FullWorkflowDefinitionTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void fullVariantContainsClassifyFailureNode() {
        WorkflowDefinition wf = factory.forVariant("full");

        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "full variant should contain node acceptance.classify_failure");
    }

    @Test
    void fullVariantDeclaresTypedProposalAsProductReviewContextRequirement() {
        var contextNode = factory.forVariant("full").node("context.intake_codex_product_review");

        assertEquals("task_contract_proposal", contextNode.config().get("required_input_artifact"));
    }

    @Test
    void fullVariantLimitsWriteAccessToImplementationOwnedAgentNodes() {
        WorkflowDefinition wf = factory.forVariant("full");

        var writableNodes = wf.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .filter(node -> Boolean.TRUE.equals(node.config().get("approve_all")))
                .map(node -> node.id())
                .toList();

        assertEquals(List.of(
                "implement.claude_implement",
                "implement.claude_self_test_report"
        ), writableNodes);
    }

    @Test
    void fullVariantKeepsReviewAndPlanningAgentNodesReadOnly() {
        WorkflowDefinition wf = factory.forVariant("full");

        var readOnlyNodes = List.of(
                "intake.claude_brainstorm",
                "intake.codex_product_review",
                "design.claude_design",
                "design.codex_arch_review",
                "plan.claude_plan",
                "plan.codex_plan_review",
                "acceptance.codex_verifier_review",
                "stage.claude_handoff"
        );

        for (var nodeId : readOnlyNodes) {
            assertNotEquals(true, wf.node(nodeId).config().get("approve_all"),
                    nodeId + " must not have write permissions");
        }
    }

    @Test
    void fullVariantAcceptRoutesToReport() {
        WorkflowDefinition wf = factory.forVariant("full");

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "ACCEPT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'ACCEPT'");
        assertEquals("acceptance.generate_final_report", edges.get(0).to());
    }

    @Test
    void fullVariantAcceptWithNotesRoutesToClassify() {
        WorkflowDefinition wf = factory.forVariant("full");

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "ACCEPT_WITH_NOTES".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'ACCEPT_WITH_NOTES'");
        assertEquals("acceptance.classify_failure", edges.get(0).to());
    }

    @Test
    void fullVariantRejectRoutesToClassify() {
        WorkflowDefinition wf = factory.forVariant("full");

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "REJECT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'REJECT'");
        assertEquals("acceptance.classify_failure", edges.get(0).to());
    }

    @Test
    void fullVariantRejectContractGapRoutesToContractCompile() {
        WorkflowDefinition wf = factory.forVariant("full");

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_CONTRACT_GAP".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_CONTRACT_GAP'");
        assertEquals("contract.compile", edges.get(0).to());
    }

    @Test
    void fullVariantRejectReviewNoiseRoutesToReReview() {
        WorkflowDefinition wf = factory.forVariant("full");

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_REVIEW_NOISE".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_REVIEW_NOISE'");
        assertEquals("context.acceptance_codex_verifier_review", edges.get(0).to());
    }

    @Test
    void fullVariantNotedEdgesRouteToReport() {
        WorkflowDefinition wf = factory.forVariant("full");

        List<String> notedOutcomes = List.of(
                "NOTED_CONTRACT_GAP",
                "NOTED_CONTRACT_AMBIGUITY",
                "NOTED_IMPLEMENTATION_BUG",
                "NOTED_CONTEXT_INSUFFICIENT",
                "NOTED_VERIFICATION_INSUFFICIENT",
                "NOTED_TOOL_LIMITATION",
                "NOTED_REVIEW_NOISE"
        );

        for (String outcome : notedOutcomes) {
            var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                    .filter(e -> outcome.equals(e.on()))
                    .toList();

            assertEquals(1, edges.size(),
                    "Expected exactly one edge from acceptance.classify_failure on '" + outcome + "'");
            assertEquals("acceptance.generate_final_report", edges.get(0).to(),
                    "NOTED edge '" + outcome + "' should route to acceptance.generate_final_report");
        }
    }

    @Test
    void fullVariantRejectImplementationBugRoutesToImplement() {
        WorkflowDefinition wf = factory.forVariant("full");

        List<String> rejectOutcomes = List.of(
                "REJECT_IMPLEMENTATION_BUG",
                "REJECT_CONTEXT_INSUFFICIENT",
                "REJECT_TOOL_LIMITATION"
        );

        for (String outcome : rejectOutcomes) {
            var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                    .filter(e -> outcome.equals(e.on()))
                    .toList();

            assertEquals(1, edges.size(),
                    "Expected exactly one edge from acceptance.classify_failure on '" + outcome + "'");
            assertEquals("context.implement_claude", edges.get(0).to(),
                    "REJECT edge '" + outcome + "' should route to context.implement_claude");
            assertEquals("loop", edges.get(0).metadata().transitionType(),
                    "REJECT edge '" + outcome + "' should be an explicit semantic repair loop");
            assertFalse(edges.get(0).metadata().loopReason().isBlank(),
                    "REJECT edge '" + outcome + "' should explain why a new iteration starts");
        }
    }

    @Test
    void fullVariantRejectVerificationInsufficientRoutesToSelfTestEvidenceCollection() {
        WorkflowDefinition wf = factory.forVariant("full");

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_VERIFICATION_INSUFFICIENT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_VERIFICATION_INSUFFICIENT'");
        assertEquals("implement.claude_self_test_report", edges.get(0).to(),
                "Verification evidence gaps should rerun self-test evidence collection, not implementation repair");
        assertEquals("loop", edges.get(0).metadata().transitionType());
    }

    @Test
    void acceptanceContextRequiresCompletedSelfTestEvidence() {
        WorkflowDefinition wf = factory.forVariant("full");

        var contextNode = wf.node("context.acceptance_codex_verifier_review");
        assertEquals(Map.of(
                "node_id", "implement.claude_self_test_report",
                "source_artifact_type", "self_test_report",
                "decision", "SELF_TEST_COMPLETE"), contextNode.config().get("required_agent_evidence"));
    }

}
