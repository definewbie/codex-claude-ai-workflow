package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void executesActiveTaskAndRoutesToHumanGate() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-runtime"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "task.one",
                List.of(
                        new NodeDefinition("task.one", NodeKind.TASK, "fake.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("task.one", "completed", "gate.review"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-runtime", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new FakeTask()), List.of(), List.of(new FakeGate()))
        );
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-runtime"));

        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, result.status());
        assertEquals("gate.review", result.activeNode());
        assertTrue(result.message().contains("resumed"));
    }

    @Test
    void inspectsOpenGateWithoutRoutingPastIt() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "gate.review",
                List.of(new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(), List.of(), List.of(new FakeGate()))
        );
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-gate"));

        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, result.status());
        assertEquals("gate.review", result.activeNode());
    }

    @Test
    void failedTaskStopsWorkflowWithoutRouting() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-failed-runtime"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "task.one",
                List.of(
                        new NodeDefinition("task.one", NodeKind.TASK, "failed.task", Map.of()),
                        new NodeDefinition("task.two", NodeKind.TASK, "fake.task", Map.of())
                ),
                List.of(new EdgeDefinition("task.one", "completed", "task.two"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-failed-runtime", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new FailedTask(), new FakeTask()), List.of(), List.of())
        );
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-failed-runtime"));

        assertEquals(WorkflowStatus.FAILED, result.status());
        assertEquals("task.one", result.activeNode());
        assertTrue(result.message().contains("FAILED"));
    }

    private static final class FakeTask implements TaskHandler {
        @Override
        public String type() {
            return "fake.task";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of("artifact://fake"), Map.of("ok", true));
        }
    }

    private static final class FakeGate implements GateHandler {
        @Override
        public String type() {
            return "human.review";
        }

        @Override
        public NodeResult openOrInspect(GateContext context) {
            return NodeResult.waiting("open", Map.of("allowed_actions", List.of("approve", "revise")));
        }
    }

    private static final class FailedTask implements TaskHandler {
        @Override
        public String type() {
            return "failed.task";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return new NodeResult("completed", NodeStatus.FAILED, List.of(), Map.of("exit_code", 4), "failed");
        }
    }

    private static final class UnusedDecision implements DecisionHandler {
        @Override
        public String type() {
            return "unused.decision";
        }

        @Override
        public NodeResult evaluate(DecisionContext context) {
            return NodeResult.completed("PASS", List.of(), Map.of());
        }
    }
}
