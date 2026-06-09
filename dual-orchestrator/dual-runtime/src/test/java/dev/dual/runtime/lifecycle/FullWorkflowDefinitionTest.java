package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FullWorkflowDefinitionTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void fullMainlineContainsClassifyFailureNode() {
        WorkflowDefinition wf = factory.fullMainline();

        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "fullMainline should contain node acceptance.classify_failure");
    }

    @Test
    void fullAgentNodesCanRunNonInteractiveToolsInsideWorktree() {
        WorkflowDefinition wf = factory.fullMainline();

        var agentNodes = wf.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .toList();

        assertFalse(agentNodes.isEmpty(), "full should contain acpx agent nodes");
        for (var node : agentNodes) {
            assertEquals(true, node.config().get("approve_all"),
                    "full agent node should be able to run tools non-interactively: " + node.id());
        }
    }

    @Test
    void fullMainlineAcceptRoutesToReport() {
        WorkflowDefinition wf = factory.fullMainline();

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "ACCEPT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'ACCEPT'");
        assertEquals("acceptance.generate_final_report", edges.get(0).to());
    }

    @Test
    void fullMainlineAcceptWithNotesRoutesToClassify() {
        WorkflowDefinition wf = factory.fullMainline();

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "ACCEPT_WITH_NOTES".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'ACCEPT_WITH_NOTES'");
        assertEquals("acceptance.classify_failure", edges.get(0).to());
    }

    @Test
    void fullMainlineRejectRoutesToClassify() {
        WorkflowDefinition wf = factory.fullMainline();

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "REJECT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'REJECT'");
        assertEquals("acceptance.classify_failure", edges.get(0).to());
    }

    @Test
    void fullMainlineRejectContractGapRoutesToContractCompile() {
        WorkflowDefinition wf = factory.fullMainline();

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_CONTRACT_GAP".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_CONTRACT_GAP'");
        assertEquals("contract.compile", edges.get(0).to());
    }

    @Test
    void fullMainlineRejectReviewNoiseRoutesToReReview() {
        WorkflowDefinition wf = factory.fullMainline();

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_REVIEW_NOISE".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_REVIEW_NOISE'");
        assertEquals("context.acceptance_codex_verifier_review", edges.get(0).to());
    }

    @Test
    void fullMainlineNotedEdgesRouteToReport() {
        WorkflowDefinition wf = factory.fullMainline();

        List<String> notedOutcomes = List.of(
                "NOTED_CONTRACT_GAP",
                "NOTED_CONTRACT_AMBIGUITY",
                "NOTED_IMPLEMENTATION_BUG",
                "NOTED_CONTEXT_INSUFFICIENT",
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
    void fullMainlineRejectImplementationBugRoutesToImplement() {
        WorkflowDefinition wf = factory.fullMainline();

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
        }
    }

    @Test
    void baselineMainlineDoesNotContainClassifyFailureNode() {
        WorkflowDefinition wf = factory.baselineMainline();

        assertThrows(IllegalArgumentException.class, () -> wf.node("acceptance.classify_failure"),
                "baselineMainline should NOT contain node acceptance.classify_failure");
    }
}
