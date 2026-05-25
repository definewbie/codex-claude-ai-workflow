package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.NodeKind;

import org.junit.jupiter.api.Test;

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
}
