package dev.dual.flow.liteflow;

import com.yomahub.liteflow.core.NodeComponent;
import dev.dual.core.flow.FlowAdvanceResult;
import dev.dual.core.flow.FlowTransitionCandidate;

import java.util.ArrayList;
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
            transitions.add(new FlowTransitionCandidate(edge.from(), edge.on(), edge.to(), Map.of(
                    "condition", edge.condition().type()
            )));
        }
        context.setAdvanceResult(new FlowAdvanceResult(transitions, context.executedStepIds()));
    }
}
