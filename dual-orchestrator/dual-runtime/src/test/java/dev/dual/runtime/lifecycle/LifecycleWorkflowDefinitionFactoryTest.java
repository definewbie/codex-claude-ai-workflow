package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeCondition;
import dev.dual.core.workflow.NodeKind;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleWorkflowDefinitionFactoryTest {
    @Test
    void includesThinMainlineAcrossLifecycleStages() {
        var definition = new LifecycleWorkflowDefinitionFactory().thinMainline();

        assertEquals("agent-lifecycle", definition.workflowId());
        assertEquals(1, definition.version());
        assertEquals("intake.claude_brainstorm", definition.entryNodeId());
        assertEquals(NodeKind.TASK, definition.node("intake.claude_brainstorm").kind());
        assertEquals(NodeKind.TASK, definition.node("design.codex_arch_review").kind());
        assertEquals(NodeKind.TASK, definition.node("plan.claude_plan").kind());
        assertEquals(NodeKind.TASK, definition.node("context.implement_claude").kind());
        assertEquals(NodeKind.TASK, definition.node("implement.claude_implement").kind());
        assertEquals(NodeKind.TASK, definition.node("context.acceptance_codex_verifier_review").kind());
        assertEquals(NodeKind.TASK, definition.node("acceptance.codex_verifier_review").kind());
        assertEquals(NodeKind.TASK, definition.node("delivery.prepare_pr").kind());
        assertEquals(NodeKind.TASK, definition.node("stage.generate_next_prompt").kind());
        assertEquals(NodeKind.GATE, definition.node("gate.human_design_approval").kind());
        assertEquals(1, definition.node("design.decision").config().get("max_revisions"));
        assertEquals(1, definition.node("gate.human_design_approval").config().get("max_revisions"));
        assertEquals("context.build_package", definition.node("context.implement_claude").handlerType());
        assertTrue(definition.edgesFrom("intake.claude_brainstorm").stream()
                .anyMatch(edge -> edge.to().equals("context.intake_codex_product_review")));
        assertTrue(definition.edgesFrom("context.intake_codex_product_review").stream()
                .anyMatch(edge -> edge.to().equals("intake.codex_product_review")));
        assertTrue(definition.edgesFrom("acceptance.generate_final_report").stream()
                .anyMatch(edge -> edge.to().equals("delivery.prepare_pr")));
        assertTrue(definition.edgesFrom("delivery.prepare_pr").stream()
                .anyMatch(edge -> edge.to().equals("gate.human_acceptance")));
    }

    @Test
    void allEdgesPointToDeclaredNodes() {
        var definition = new LifecycleWorkflowDefinitionFactory().thinMainline();

        for (var edge : definition.edges()) {
            assertEquals(edge.from(), definition.node(edge.from()).id());
            assertEquals(edge.to(), definition.node(edge.to()).id());
        }
    }

    @Test
    void conditionalEdge_onNextStageApproval() {
        var definition = new LifecycleWorkflowDefinitionFactory().thinMainline();

        // The "complete" edge from human_next_stage_approval has a configEquals condition
        var completeEdges = definition.edgesFrom("gate.human_next_stage_approval").stream()
                .filter(e -> e.on().equals("complete"))
                .toList();
        assertEquals(1, completeEdges.size());
        var conditionalEdge = completeEdges.get(0);
        assertEquals("acceptance.generate_final_report", conditionalEdge.to());

        // Condition matches when completion_mode=final_report
        assertTrue(conditionalEdge.condition().evaluate(Map.of("completion_mode", "final_report")));
        // Condition does not match otherwise
        assertTrue(!conditionalEdge.condition().evaluate(Map.of("completion_mode", "something_else")));
        assertTrue(!conditionalEdge.condition().evaluate(Map.of()));

        // Unconditional edges still work (e.g., "approved" has no condition)
        var approvedEdges = definition.edgesFrom("gate.human_next_stage_approval").stream()
                .filter(e -> e.on().equals("approved"))
                .toList();
        assertEquals(1, approvedEdges.size());
        assertEquals(EdgeCondition.unconditional().type(), approvedEdges.get(0).condition().type());
    }
}
