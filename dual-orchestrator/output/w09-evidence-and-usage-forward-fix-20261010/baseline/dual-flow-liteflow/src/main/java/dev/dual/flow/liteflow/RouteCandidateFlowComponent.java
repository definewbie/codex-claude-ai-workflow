package dev.dual.flow.liteflow;

import com.yomahub.liteflow.core.NodeComponent;

import java.util.LinkedHashMap;

public final class RouteCandidateFlowComponent extends NodeComponent {
    @Override
    public void process() {
        var context = getContextBean(LiteFlowKernelContext.class);
        var componentId = getNodeId();
        context.recordStep(componentId);
        var edge = context.candidateEdge(componentId);
        if (edge == null) {
            throw new IllegalStateException("no edge bound to LiteFlow component: " + componentId);
        }
        var routingContext = new LinkedHashMap<String, Object>();
        routingContext.put("outcome", context.signal().outcome());
        routingContext.putAll(context.signal().context());
        if (edge.condition().evaluate(routingContext)) {
            context.acceptCandidate(componentId);
        }
    }
}
