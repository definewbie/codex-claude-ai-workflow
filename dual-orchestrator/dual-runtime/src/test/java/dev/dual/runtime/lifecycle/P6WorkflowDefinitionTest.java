package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class P6WorkflowDefinitionTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void p6MainlineContainsClassifyFailureNode() {
        WorkflowDefinition wf = factory.p6Mainline();

        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "p6Mainline should contain node acceptance.classify_failure");
    }

    @Test
    void p6AgentNodesCanRunNonInteractiveToolsInsideWorktree() {
        WorkflowDefinition wf = factory.p6Mainline();

        var agentNodes = wf.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .toList();

        assertFalse(agentNodes.isEmpty(), "p6 should contain acpx agent nodes");
        for (var node : agentNodes) {
            assertEquals(true, node.config().get("approve_all"),
                    "p6 agent node should be able to run tools non-interactively: " + node.id());
        }
    }

    @Test
    void p6MainlineAcceptRoutesToReport() {
        WorkflowDefinition wf = factory.p6Mainline();

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "ACCEPT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'ACCEPT'");
        assertEquals("acceptance.generate_final_report", edges.get(0).to());
    }

    @Test
    void p6MainlineAcceptWithNotesRoutesToClassify() {
        WorkflowDefinition wf = factory.p6Mainline();

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "ACCEPT_WITH_NOTES".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'ACCEPT_WITH_NOTES'");
        assertEquals("acceptance.classify_failure", edges.get(0).to());
    }

    @Test
    void p6MainlineRejectRoutesToClassify() {
        WorkflowDefinition wf = factory.p6Mainline();

        var edges = wf.edgesFrom("acceptance.decision").stream()
                .filter(e -> "REJECT".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.decision on 'REJECT'");
        assertEquals("acceptance.classify_failure", edges.get(0).to());
    }

    @Test
    void p6MainlineRejectContractGapRoutesToContractCompile() {
        WorkflowDefinition wf = factory.p6Mainline();

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_CONTRACT_GAP".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_CONTRACT_GAP'");
        assertEquals("contract.compile", edges.get(0).to());
    }

    @Test
    void p6MainlineRejectReviewNoiseRoutesToReReview() {
        WorkflowDefinition wf = factory.p6Mainline();

        var edges = wf.edgesFrom("acceptance.classify_failure").stream()
                .filter(e -> "REJECT_REVIEW_NOISE".equals(e.on()))
                .toList();

        assertEquals(1, edges.size(),
                "Expected exactly one edge from acceptance.classify_failure on 'REJECT_REVIEW_NOISE'");
        assertEquals("context.acceptance_codex_verifier_review", edges.get(0).to());
    }

    @Test
    void p6MainlineNotedEdgesRouteToReport() {
        WorkflowDefinition wf = factory.p6Mainline();

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
    void p6MainlineRejectImplementationBugRoutesToImplement() {
        WorkflowDefinition wf = factory.p6Mainline();

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
    void thinMainlineDoesNotContainClassifyFailureNode() {
        WorkflowDefinition wf = factory.thinMainline();

        assertThrows(IllegalArgumentException.class, () -> wf.node("acceptance.classify_failure"),
                "thinMainline should NOT contain node acceptance.classify_failure");
    }
}
