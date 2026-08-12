package dev.dual.runtime.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalDeliveryWorkflowDefinitionTest {
    private static final Set<String> OMITTED_TAIL_NODES = Set.of(
            "stage.claude_handoff",
            "stage.generate_next_prompt",
            "gate.human_next_stage_approval"
    );

    @Test
    void bundledVariantChangesOnlyTheAcceptedDeliveryTail() {
        var full = new LifecycleWorkflowDefinitionFactory().forVariant("full");
        var terminal = new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery");

        assertEquals("agent-lifecycle-terminal-delivery", terminal.workflowId());
        assertEquals(29, terminal.nodes().size());
        assertEquals(48, terminal.edges().size());
        assertEquals(9, terminal.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .count());

        assertTrue(terminal.nodes().stream().noneMatch(node -> OMITTED_TAIL_NODES.contains(node.id())));
        assertTrue(terminal.edges().stream().noneMatch(edge ->
                OMITTED_TAIL_NODES.contains(edge.from()) || OMITTED_TAIL_NODES.contains(edge.to())));
        assertTrue(terminal.edgesFrom("gate.human_acceptance").stream()
                .noneMatch(edge -> "approved".equals(edge.on())));
        assertEquals("context.implement_claude", terminal.edgesFrom("gate.human_acceptance").stream()
                .filter(edge -> "revise".equals(edge.on()))
                .findFirst()
                .orElseThrow()
                .to());
        assertEquals(true, terminal.node("gate.human_acceptance").config().get("terminal_gate"));

        for (var node : full.nodes()) {
            if (OMITTED_TAIL_NODES.contains(node.id()) || "gate.human_acceptance".equals(node.id())) {
                continue;
            }
            assertEquals(node, terminal.node(node.id()), node.id());
        }
        var expectedEdges = full.edges().stream()
                .filter(edge -> !OMITTED_TAIL_NODES.contains(edge.from()))
                .filter(edge -> !OMITTED_TAIL_NODES.contains(edge.to()))
                .filter(edge -> !("gate.human_acceptance".equals(edge.from())
                        && "approved".equals(edge.on())))
                .toList();
        assertEquals(expectedEdges, terminal.edges());

        assertFalse(Boolean.TRUE.equals(full.node("gate.human_acceptance").config().get("terminal_gate")));
        assertEquals("stage.claude_handoff", full.edgesFrom("gate.human_acceptance").stream()
                .filter(edge -> "approved".equals(edge.on()))
                .findFirst()
                .orElseThrow()
                .to());
    }
}
