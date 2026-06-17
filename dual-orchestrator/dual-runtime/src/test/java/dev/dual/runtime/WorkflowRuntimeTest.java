package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.AgentTaskStatus;
import dev.dual.ledger.file.NodeExecutionStatus;
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
    private int fakeTaskExecutions;

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

        var gate = new FakeGate();
        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new FakeTask()), List.of(), List.of(gate))
        );
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-runtime"));

        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, result.status());
        assertEquals("gate.review", result.activeNode());
        assertTrue(result.message().contains("resumed"));
        assertEquals(1, gate.openCount());
    }

    @Test
    void workerDispatchModeQueuesAgentTaskWithoutExecutingHandler() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-worker-queue"));
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
        ledger.initialize(paths, "wf-worker-queue", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new FakeTask()), List.of(), List.of(new FakeGate())),
                AgentTaskDispatchMode.WORKER
        );
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-worker-queue"));

        assertEquals(WorkflowStatus.RUNNING, result.status());
        assertEquals("task.one", result.activeNode());
        assertEquals(0, fakeTaskExecutions);
        var tasks = ledger.readAgentTasks(paths);
        assertEquals(1, tasks.size());
        assertEquals(AgentTaskStatus.QUEUED, tasks.get(0).status());
        assertEquals(0, ledger.readNodeExecutions(paths).size());
        var node = ledger.loadNode(paths, "node-task-one-001");
        assertEquals(NodeStatus.RUNNING, node.status());
        assertEquals(tasks.get(0).agentTaskId(), node.result().get("queued_agent_task_id"));
    }

    @Test
    void workerRunOneExecutesQueuedAgentTaskAndRoutesWorkflow() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-worker-run-once"));
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
        ledger.initialize(paths, "wf-worker-run-once", definition);
        var gate = new FakeGate();
        var handlers = new HandlerRegistry(List.of(new FakeTask()), List.of(), List.of(gate));

        var runtime = new WorkflowRuntime(ledger, handlers, AgentTaskDispatchMode.WORKER);
        runtime.resume(new WorkflowCommand(tempDir, "ws-worker-run-once"));

        var worker = new AgentWorker(ledger, handlers);
        var workerResult = worker.runOne(new WorkflowCommand(tempDir, "ws-worker-run-once"));

        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, workerResult.status());
        assertEquals("gate.review", workerResult.nodeDefinitionId());
        assertEquals(1, fakeTaskExecutions);
        assertEquals(1, gate.openCount());
        var task = ledger.readAgentTasks(paths).get(0);
        assertEquals(AgentTaskStatus.SUCCEEDED, task.status());
        assertTrue(task.nodeExecutionId().startsWith("nxe-"));
        var nodeExecution = ledger.readNodeExecutions(paths).get(0);
        assertEquals(NodeExecutionStatus.SUCCEEDED, nodeExecution.status());
        assertEquals(task.agentTaskId(), nodeExecution.agentTaskId());
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

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        assertEquals("task.one", result.activeNode());
        assertTrue(result.message().contains("FAILED"));

        var recoveryResult = runtime.resume(new WorkflowCommand(tempDir, "ws-failed-runtime"));
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, recoveryResult.status());
        assertEquals("task.one", recoveryResult.activeNode());
        assertTrue(recoveryResult.message().contains("not recoverable"));
    }

    @Test
    void decisionRoutesUsingDualControlOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-decision-runtime"));
        Files.createDirectories(paths.workstreamDir());
        var output = writeOutput("ws-decision-runtime", "review.md", """
                ```dual-control
                decision: REJECT
                summary: acceptance failed
                ```
                """);
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "seed.task",
                List.of(
                        new NodeDefinition("seed.task", NodeKind.TASK, "output.task", Map.of("output_ref", output)),
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of()),
                        new NodeDefinition("implement.claude_implement", NodeKind.TASK, "fake.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("seed.task", "completed", "acceptance.decision"),
                        new EdgeDefinition("acceptance.decision", "REJECT", "implement.claude_implement")
                )
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-decision-runtime", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new FakeTask(), new OutputTask()), List.of(new dev.dual.runtime.handler.ReviewDecisionHandler()), List.of())
        );
        runtime.resume(new WorkflowCommand(tempDir, "ws-decision-runtime"));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-decision-runtime"));

        assertEquals(WorkflowStatus.RUNNING, result.status());
        assertEquals("implement.claude_implement", result.activeNode());
    }

    @Test
    void invalidAgentOutputContractStopsBeforeRouting() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-agent-contract"));
        Files.createDirectories(paths.workstreamDir());
        var invalidOutput = writeOutput("ws-agent-contract", "design-codex-review.md", """
                Looks good.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                ```
                """);
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "design.codex_arch_review",
                List.of(
                        new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "acpx.agent_run", Map.of("output_ref", invalidOutput)),
                        new NodeDefinition("design.decision", NodeKind.DECISION, "review.decision", Map.of())
                ),
                List.of(new EdgeDefinition("design.codex_arch_review", "completed", "design.decision"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-agent-contract", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new AgentOutputTask()), List.of(new dev.dual.runtime.handler.ReviewDecisionHandler()), List.of())
        );
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-agent-contract"));

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        assertEquals("design.codex_arch_review", result.activeNode());
        var node = ledger.loadNode(paths, "node-design-codex_arch_review-001");
        assertEquals(NodeStatus.FAILED, node.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", node.result().get("reason"));
        assertTrue(String.valueOf(node.result().get("message")).contains("summary"));
    }

    private final class FakeTask implements TaskHandler {
        @Override
        public String type() {
            return "fake.task";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            fakeTaskExecutions++;
            return NodeResult.completed("completed", List.of("artifact://fake"), Map.of("ok", true));
        }
    }

    private static final class FakeGate implements GateHandler {
        private int openCount;

        @Override
        public String type() {
            return "human.review";
        }

        @Override
        public NodeResult openOrInspect(GateContext context) {
            openCount++;
            return NodeResult.waiting("open", Map.of("allowed_actions", List.of("approve", "revise")));
        }

        int openCount() {
            return openCount;
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

    private static final class OutputTask implements TaskHandler {
        @Override
        public String type() {
            return "output.task";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of((String) context.definition().config().get("output_ref")), Map.of("ok", true));
        }
    }

    private static final class AgentOutputTask implements TaskHandler {
        @Override
        public String type() {
            return "acpx.agent_run";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of((String) context.definition().config().get("output_ref")), Map.of(
                    "agent", "codex",
                    "session", "codex-reviewer",
                    "exit_code", 0
            ));
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

    private String writeOutput(String workstreamId, String name, String content) throws Exception {
        var path = tempDir.resolve(".agent").resolve("workstreams").resolve(workstreamId).resolve("runs").resolve("run-001").resolve("outputs").resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return tempDir.relativize(path).toString();
    }
}
