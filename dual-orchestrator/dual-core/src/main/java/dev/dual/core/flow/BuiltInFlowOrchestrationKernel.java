package dev.dual.core.flow;

import dev.dual.core.workflow.WorkflowDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class BuiltInFlowOrchestrationKernel implements FlowOrchestrationKernel {
    @Override
    public FlowValidationResult validate(WorkflowDefinition definition) {
        if (definition == null) {
            return FlowValidationResult.invalid(java.util.List.of("workflow definition is required"));
        }
        return FlowValidationResult.ok();
    }

    @Override
    public FlowAdvanceResult advance(
            WorkflowDefinition definition,
            FlowExecutionSnapshot snapshot,
            FlowSignal signal
    ) {
        if (definition == null) {
            throw new IllegalArgumentException("workflow definition is required");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("flow execution snapshot is required");
        }
        if (signal == null) {
            throw new IllegalArgumentException("flow signal is required");
        }
        if (!snapshot.activeNodeDefinitionIds().contains(signal.nodeDefinitionId())) {
            throw new IllegalArgumentException("signal node is not active: " + signal.nodeDefinitionId());
        }

        var routingContext = new LinkedHashMap<String, Object>();
        routingContext.put("outcome", signal.outcome());
        routingContext.putAll(signal.context());

        var transitions = new ArrayList<FlowTransitionCandidate>();
        for (var edge : definition.edgesFrom(signal.nodeDefinitionId(), signal.outcome(), routingContext)) {
            transitions.add(new FlowTransitionCandidate(edge.from(), edge.on(), edge.to(), java.util.Map.of(
                    "condition", edge.condition().type()
            )));
        }
        return FlowAdvanceResult.routed(transitions, java.util.List.of("builtin.route"));
    }
}
