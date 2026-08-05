package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalDeliveryWorkflowExperimentTest {
    private static final Set<String> OMITTED_TAIL_NODES = Set.of(
            "stage.claude_handoff",
            "stage.generate_next_prompt",
            "gate.human_next_stage_approval"
    );

    @Test
    void experimentChangesOnlyTheAcceptedDeliveryTail() throws Exception {
        var full = new LifecycleWorkflowDefinitionFactory().forVariant("full");
        var experiment = loadExperiment();

        assertEquals("agent-lifecycle-terminal-delivery-experiment", experiment.workflowId());
        assertEquals(29, experiment.nodes().size());
        assertEquals(45, experiment.edges().size());
        assertEquals(9, experiment.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .count());

        assertTrue(experiment.nodes().stream().noneMatch(node -> OMITTED_TAIL_NODES.contains(node.id())));
        assertTrue(experiment.edges().stream().noneMatch(edge ->
                OMITTED_TAIL_NODES.contains(edge.from()) || OMITTED_TAIL_NODES.contains(edge.to())));
        assertTrue(experiment.edgesFrom("gate.human_acceptance").stream()
                .noneMatch(edge -> "approved".equals(edge.on())));
        assertEquals("context.implement_claude", experiment.edgesFrom("gate.human_acceptance").stream()
                .filter(edge -> "revise".equals(edge.on()))
                .findFirst()
                .orElseThrow()
                .to());
        assertEquals(true, experiment.node("gate.human_acceptance").config().get("terminal_gate"));

        for (var node : full.nodes()) {
            if (OMITTED_TAIL_NODES.contains(node.id()) || "gate.human_acceptance".equals(node.id())) {
                continue;
            }
            assertEquals(node, experiment.node(node.id()), node.id());
        }
        var expectedEdges = full.edges().stream()
                .filter(edge -> !OMITTED_TAIL_NODES.contains(edge.from()))
                .filter(edge -> !OMITTED_TAIL_NODES.contains(edge.to()))
                .filter(edge -> !("gate.human_acceptance".equals(edge.from())
                        && "approved".equals(edge.on())))
                .toList();
        assertEquals(expectedEdges, experiment.edges());

        assertFalse(Boolean.TRUE.equals(full.node("gate.human_acceptance").config().get("terminal_gate")));
        assertEquals("stage.claude_handoff", full.edgesFrom("gate.human_acceptance").stream()
                .filter(edge -> "approved".equals(edge.on()))
                .findFirst()
                .orElseThrow()
                .to());
    }

    private static WorkflowDefinition loadExperiment() throws Exception {
        var resource = TerminalDeliveryWorkflowExperimentTest.class.getResource(
                "/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery-experiment.json");
        if (resource == null) {
            throw new IllegalStateException("terminal-delivery experiment definition is missing");
        }
        return new WorkflowDefinitionLoader().load(Path.of(resource.toURI()));
    }
}
