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
        assertEquals(NodeKind.TASK, definition.node("implement.claude_implement").kind());
        assertEquals(NodeKind.TASK, definition.node("acceptance.codex_verifier_review").kind());
        assertEquals(NodeKind.TASK, definition.node("stage.generate_next_prompt").kind());
        assertEquals(NodeKind.GATE, definition.node("gate.human_design_approval").kind());
        assertTrue(definition.edgesFrom("intake.claude_brainstorm").stream()
                .anyMatch(edge -> edge.to().equals("intake.codex_product_review")));
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
