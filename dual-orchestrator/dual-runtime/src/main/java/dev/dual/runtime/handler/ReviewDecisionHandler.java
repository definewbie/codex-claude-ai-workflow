package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class ReviewDecisionHandler implements DecisionHandler {
    @Override
    public String type() {
        return "review.decision";
    }

    @Override
    public NodeResult evaluate(DecisionContext context) {
        var decision = switch (context.definition().id()) {
            case "plan.decision" -> "READY_TO_IMPLEMENT";
            case "acceptance.decision" -> "ACCEPT";
            default -> "PASS";
        };
        return NodeResult.completed(decision, List.of(), Map.of(
                "decision", decision,
                "source", "milestone-1-default"
        ));
    }
}
