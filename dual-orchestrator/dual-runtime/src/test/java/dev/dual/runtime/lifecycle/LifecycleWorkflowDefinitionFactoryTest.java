package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.NodeKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleWorkflowDefinitionFactoryTest {
    @Test
    void includesFullVariantAcrossLifecycleStages() {
        var definition = new LifecycleWorkflowDefinitionFactory().forVariant("full");

        assertEquals("agent-lifecycle-full", definition.workflowId());
        assertEquals(9, definition.version());
        assertEquals("context.intake_claude_brainstorm", definition.entryNodeId());
        assertEquals(NodeKind.TASK, definition.node("context.intake_claude_brainstorm").kind());
        assertEquals("context.build_package", definition.node("context.intake_claude_brainstorm").handlerType());
        assertEquals(NodeKind.TASK, definition.node("intake.claude_brainstorm").kind());
        assertEquals(NodeKind.TASK, definition.node("contract.compile").kind());
        assertEquals(NodeKind.TASK, definition.node("design.codex_arch_review").kind());
        assertEquals(NodeKind.TASK, definition.node("plan.claude_plan").kind());
        assertEquals(NodeKind.TASK, definition.node("context.implement_claude").kind());
        assertEquals(NodeKind.TASK, definition.node("implement.claude_implement").kind());
        assertEquals(NodeKind.DECISION, definition.node("implement.self_test_decision").kind());
        assertEquals(NodeKind.GATE, definition.node("gate.human_implementation_repair").kind());
        assertEquals(NodeKind.TASK, definition.node("context.acceptance_codex_verifier_review").kind());
        assertEquals(NodeKind.TASK, definition.node("acceptance.codex_verifier_review").kind());
        assertEquals(NodeKind.TASK, definition.node("delivery.prepare_pr").kind());
        assertEquals(NodeKind.TASK, definition.node("stage.generate_next_prompt").kind());
        assertEquals(NodeKind.GATE, definition.node("gate.human_design_approval").kind());
        assertEquals(3, definition.node("intake.decision").config().get("max_revisions"));
        assertEquals(3, definition.node("design.decision").config().get("max_revisions"));
        assertEquals(3, definition.node("gate.human_design_approval").config().get("max_revisions"));
        assertEquals(3, definition.node("plan.decision").config().get("max_revisions"));
        assertEquals(1, definition.node("implement.self_test_decision").config().get("max_revisions"));
        assertEquals(1, definition.node("gate.human_implementation_repair").config().get("max_revisions"));
        assertEquals("context.build_package", definition.node("context.implement_claude").handlerType());
        assertTrue(definition.edgesFrom("context.intake_claude_brainstorm").stream()
                .anyMatch(edge -> edge.to().equals("intake.claude_brainstorm")));
        assertTrue(definition.edgesFrom("intake.claude_brainstorm").stream()
                .anyMatch(edge -> edge.to().equals("context.intake_codex_product_review")));
        assertTrue(definition.edgesFrom("context.intake_codex_product_review").stream()
                .anyMatch(edge -> edge.to().equals("intake.codex_product_review")));
        assertTrue(definition.edgesFrom("acceptance.generate_final_report").stream()
                .anyMatch(edge -> edge.to().equals("delivery.prepare_pr")));
        assertTrue(definition.edgesFrom("implement.claude_self_test_report").stream()
                .anyMatch(edge -> edge.to().equals("implement.self_test_decision")));
        assertTrue(definition.edgesFrom("implement.self_test_decision").stream()
                .anyMatch(edge -> "SELF_TEST_INCOMPLETE".equals(edge.on())
                        && edge.to().equals("context.implement_claude")));
        assertTrue(definition.edgesFrom("implement.self_test_decision").stream()
                .anyMatch(edge -> "BLOCKED".equals(edge.on())
                        && edge.to().equals("gate.human_implementation_repair")));
        assertTrue(definition.edgesFrom("delivery.prepare_pr").stream()
                .anyMatch(edge -> edge.to().equals("gate.human_acceptance")));
    }

    @Test
    void allEdgesPointToDeclaredNodes() {
        var definition = new LifecycleWorkflowDefinitionFactory().forVariant("full");

        for (var edge : definition.edges()) {
            assertEquals(edge.from(), definition.node(edge.from()).id());
            assertEquals(edge.to(), definition.node(edge.to()).id());
        }
    }

    @Test
    void selfTestAgentCanRunVerificationCommands() {
        var factory = new LifecycleWorkflowDefinitionFactory();

        assertEquals(true, factory.forVariant("contract")
                .node("implement.claude_self_test_report")
                .config()
                .get("approve_all"));
        assertEquals(true, factory.forVariant("full")
                .node("implement.claude_self_test_report")
                .config()
                .get("approve_all"));
    }

    @Test
    void builtInVariantsOnlyGrantWriteAccessToImplementationOwnedAgentNodes() {
        var factory = new LifecycleWorkflowDefinitionFactory();

        for (var definition : java.util.List.of(
                factory.forVariant("contract"),
                factory.forVariant("full")
        )) {
            var writableNodes = definition.nodes().stream()
                    .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                    .filter(node -> Boolean.TRUE.equals(node.config().get("approve_all")))
                    .map(node -> node.id())
                    .toList();

            assertEquals(java.util.List.of(
                    "implement.claude_implement",
                    "implement.claude_self_test_report"
            ), writableNodes, definition.workflowId() + " must not grant write access outside implementation");
        }
    }

    @Test
    void finalNextStageGateCompletesWithoutStartingAnotherIteration() {
        var definition = new LifecycleWorkflowDefinitionFactory().forVariant("full");

        assertEquals(java.util.List.of("revise", "complete"),
                definition.node("gate.human_next_stage_approval").config().get("allowed_actions"));
        assertFalse(definition.edgesFrom("gate.human_next_stage_approval").stream()
                .anyMatch(edge -> "approved".equals(edge.on())));
        assertFalse(definition.edgesFrom("gate.human_next_stage_approval").stream()
                .anyMatch(edge -> "complete".equals(edge.on())));
        assertTrue(definition.edgesFrom("gate.human_next_stage_approval").stream()
                .anyMatch(edge -> "revise".equals(edge.on()) && "stage.claude_handoff".equals(edge.to())));
    }
}
