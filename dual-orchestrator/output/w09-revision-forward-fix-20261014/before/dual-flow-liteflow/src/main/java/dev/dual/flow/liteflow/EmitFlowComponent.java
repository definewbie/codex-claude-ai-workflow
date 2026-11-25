package dev.dual.flow.liteflow;

import com.yomahub.liteflow.core.NodeComponent;
import dev.dual.core.flow.FlowAdvanceResult;
import dev.dual.core.flow.FlowTransitionCandidate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EmitFlowComponent extends NodeComponent {
    @Override
    public void process() {
        var context = getContextBean(LiteFlowKernelContext.class);
        context.recordStep(getNodeId());
        var result = context.advanceResult();
        if (result != null) {
            context.setAdvanceResult(new FlowAdvanceResult(result.transitions(), context.executedStepIds()));
            return;
        }
        var transitions = new ArrayList<FlowTransitionCandidate>();
        for (var componentId : context.acceptedCandidateComponentIds()) {
            var edge = context.candidateEdge(componentId);
            transitions.add(new FlowTransitionCandidate(edge.from(), edge.on(), edge.to(), edgeAttributes(edge)));
        }
        context.setAdvanceResult(new FlowAdvanceResult(transitions, context.executedStepIds()));
    }

    private static Map<String, Object> edgeAttributes(dev.dual.core.workflow.EdgeDefinition edge) {
        var attributes = new LinkedHashMap<String, Object>(edge.metadata().toAttributes());
        attributes.put("condition", edge.condition().type());
        return Map.copyOf(attributes);
    }
}
