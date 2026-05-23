package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class HumanReviewGateHandler implements GateHandler {
    @Override
    public String type() {
        return "human.review";
    }

    @Override
    public NodeResult openOrInspect(GateContext context) {
        return NodeResult.waiting("open", Map.of(
                "allowed_actions", context.definition().config().getOrDefault("allowed_actions", List.of("approve", "revise"))
        ));
    }
}
