package dev.dual.flow.liteflow;

import com.yomahub.liteflow.core.NodeComponent;

public final class PrepareFlowComponent extends NodeComponent {
    @Override
    public void process() {
        var context = getContextBean(LiteFlowKernelContext.class);
        context.recordStep(getNodeId());
    }
}
