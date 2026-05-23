package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class ArtifactValidateHandler implements TaskHandler {
    @Override
    public String type() {
        return "artifact.validate";
    }

    @Override
    public NodeResult execute(TaskContext context) {
        return NodeResult.completed("completed", List.of(), Map.of(
                "validation", "passed",
                "mode", "milestone-1"
        ));
    }
}
