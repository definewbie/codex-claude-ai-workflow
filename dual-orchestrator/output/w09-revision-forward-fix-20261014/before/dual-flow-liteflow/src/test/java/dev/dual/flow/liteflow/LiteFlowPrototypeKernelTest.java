package dev.dual.flow.liteflow;

import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.flow.FlowExecutionSnapshot;
import dev.dual.core.flow.FlowSignal;
import dev.dual.core.workflow.EdgeCondition;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.TransitionMetadata;
import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteFlowPrototypeKernelTest {
    @Test
    void compilesWorkflowDefinitionIntoLiteFlowChains() {
        var compiled = new LiteFlowElCompiler().compile(conditionalDefinition());

        assertTrue(compiled.xml().contains("<chain name=\"route_0001\">"));
        assertTrue(compiled.xml().contains("THEN(prepare, edge_0001, edge_0002, emit);"));
        assertEquals("route_0001", compiled.chainName("intake", "DONE"));
        assertEquals(List.of("edge_0001", "edge_0002"),
                compiled.componentIdsByRouteKey().get(LiteFlowCompiledDefinition.routeKey("intake", "DONE")));
    }

    @Test
    void routesTheSameCandidateAsBuiltInKernel() {
        var definition = conditionalDefinition();
        var snapshot = FlowExecutionSnapshot.singleActiveNode("wf-001", "intake");
        var signal = FlowSignal.completed("intake", "DONE", Map.of("mode", "strict"));
        var builtIn = new BuiltInFlowOrchestrationKernel().advance(definition, snapshot, signal);

        try (var liteFlow = new LiteFlowPrototypeKernel()) {
            var result = liteFlow.advance(definition, snapshot, signal);

            assertEquals(builtIn.transitions(), result.transitions());
            assertEquals(List.of("prepare", "edge_0001", "edge_0002", "emit"), result.executedStepIds());
        }
    }

    @Test
    void returnsHumanGateAsTransitionCandidateWithoutCreatingAGate() {
        var definition = humanGateDefinition();
        var snapshot = FlowExecutionSnapshot.singleActiveNode("wf-002", "contract");
        var signal = FlowSignal.completed("contract", "PASS", Map.of());

        try (var liteFlow = new LiteFlowPrototypeKernel()) {
            var result = liteFlow.advance(definition, snapshot, signal);

            assertEquals(1, result.transitions().size());
            assertEquals("gate.human_design_approval", result.transitions().get(0).toNodeDefinitionId());
            assertTrue(result.executedStepIds().contains("edge_0001"));
        }
    }

    @Test
    void preservesTransitionMetadataInCandidates() {
        var definition = new WorkflowDefinition("liteflow-metadata", 1, "fork",
                List.of(
                        new NodeDefinition("fork", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("review.arch", NodeKind.TASK, "test", Map.of())
                ),
                List.of(new EdgeDefinition("fork", "DONE", "review.arch",
                        EdgeCondition.unconditional(),
                        TransitionMetadata.fork("review-branches", true, "architecture review")))
        );
        var snapshot = FlowExecutionSnapshot.singleActiveNode("wf-003", "fork");
        var signal = FlowSignal.completed("fork", "DONE", Map.of());

        try (var liteFlow = new LiteFlowPrototypeKernel()) {
            var result = liteFlow.advance(definition, snapshot, signal);

            var attributes = result.transitions().get(0).attributes();
            assertEquals("fork", attributes.get("transition_type"));
            assertEquals("review-branches", attributes.get("branch_group_id"));
            assertEquals("architecture review", attributes.get("purpose"));
        }
    }

    private static WorkflowDefinition conditionalDefinition() {
        return new WorkflowDefinition("liteflow-conditional", 1, "intake",
                List.of(
                        new NodeDefinition("intake", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("design.strict", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("design.relaxed", NodeKind.TASK, "test", Map.of())
                ),
                List.of(
                        new EdgeDefinition("intake", "DONE", "design.strict", EdgeCondition.configEquals("mode", "strict")),
                        new EdgeDefinition("intake", "DONE", "design.relaxed", EdgeCondition.configEquals("mode", "relaxed"))
                )
        );
    }

    private static WorkflowDefinition humanGateDefinition() {
        return new WorkflowDefinition("liteflow-gate", 1, "contract",
                List.of(
                        new NodeDefinition("contract", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(
                        new EdgeDefinition("contract", "PASS", "gate.human_design_approval")
                )
        );
    }
}
