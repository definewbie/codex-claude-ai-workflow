package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void humanGateReturnsWaitingResult() throws Exception {
        var handler = new HumanReviewGateHandler();
        var result = handler.openOrInspect(new GateHandler.GateContext(
                tempDir,
                "ws",
                gateDefinition(),
                node("gate.human_design_approval", NodeKind.GATE, "human.review")
        ));

        assertEquals(NodeStatus.WAITING, result.status());
        assertEquals("open", result.outcome());
        assertEquals(List.of("approve", "revise"), result.result().get("allowed_actions"));
    }

    @Test
    void reviewDecisionUsesStageSpecificDefaultOutcome() throws Exception {
        var handler = new ReviewDecisionHandler();
        var plan = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("plan.decision"),
                node("plan.decision", NodeKind.DECISION, "review.decision")
        ));
        var acceptance = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("acceptance.decision"),
                node("acceptance.decision", NodeKind.DECISION, "review.decision")
        ));

        assertEquals("READY_TO_IMPLEMENT", plan.outcome());
        assertEquals("ACCEPT", acceptance.outcome());
    }

    @Test
    void artifactValidationCompletesForMilestoneOne() throws Exception {
        var handler = new ArtifactValidateHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("implement.artifact_validation", "artifact.validate"),
                node("implement.artifact_validation", NodeKind.TASK, "artifact.validate")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
    }

    @Test
    void nextStagePromptHandlerReturnsArtifactReference() throws Exception {
        var handler = new PromptGenerateNextStageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("stage.generate_next_prompt", "prompt.generate_next_stage"),
                node("stage.generate_next_prompt", NodeKind.TASK, "prompt.generate_next_stage")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(result.outputRefs().contains("artifact://next-stage-prompt"));
    }

    private static NodeDefinition gateDefinition() {
        return new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review", Map.of(
                "allowed_actions", List.of("approve", "revise")
        ));
    }

    private static NodeDefinition decisionDefinition(String id) {
        return new NodeDefinition(id, NodeKind.DECISION, "review.decision", Map.of());
    }

    private static NodeDefinition taskDefinition(String id, String handler) {
        return new NodeDefinition(id, NodeKind.TASK, handler, Map.of());
    }

    private static NodeInstance node(String id, NodeKind kind, String handlerType) {
        return new NodeInstance(
                "node-" + id.replace('.', '-'),
                id,
                kind,
                handlerType,
                NodeStatus.READY,
                "stage-test",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );
    }
}
