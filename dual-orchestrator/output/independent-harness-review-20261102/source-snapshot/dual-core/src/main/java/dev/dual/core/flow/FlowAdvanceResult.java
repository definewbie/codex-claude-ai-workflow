package dev.dual.core.flow;

import java.util.List;

public record FlowAdvanceResult(
        List<FlowTransitionCandidate> transitions,
        List<String> executedStepIds
) {
    public FlowAdvanceResult {
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        executedStepIds = executedStepIds == null ? List.of() : List.copyOf(executedStepIds);
    }

    public static FlowAdvanceResult routed(List<FlowTransitionCandidate> transitions, List<String> executedStepIds) {
        return new FlowAdvanceResult(transitions, executedStepIds);
    }
}
