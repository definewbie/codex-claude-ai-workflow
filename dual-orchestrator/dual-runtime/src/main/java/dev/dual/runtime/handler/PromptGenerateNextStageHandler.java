package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class PromptGenerateNextStageHandler implements TaskHandler {
    @Override
    public String type() {
        return "prompt.generate_next_stage";
    }

    @Override
    public NodeResult execute(TaskContext context) {
        return NodeResult.completed("completed", List.of("artifact://next-stage-prompt"), Map.of(
                "generated", true,
                "mode", "milestone-1"
        ));
    }
}
