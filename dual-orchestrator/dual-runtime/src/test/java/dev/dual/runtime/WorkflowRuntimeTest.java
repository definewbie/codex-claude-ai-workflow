package dev.dual.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
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
import dev.dual.runtime.handler.AgentOutputLineageFixture;
import dev.dual.runtime.handler.AgentResultEnvelope;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.ArtifactValidateHandler;
import dev.dual.runtime.handler.FailureClassifyHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.ReviewDecisionHandler;
import dev.dual.runtime.handler.TaskHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRuntimeTest {
    @TempDir
    Path tempDir;
    private int fakeTaskExecutions;
    private int acceptanceContextExecutions;

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
    void historicalRejectWithNoncanonicalCategoryCannotEnterCurrentClassification() throws Exception {
        var workstreamId = "ws-replay-acceptance-reject";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        var sidecar = paths.workstreamDir()
                .resolve("runs/run-historical-reject/outputs/acceptance-codex-review.contract.json");
        Files.createDirectories(sidecar.getParent());
        try (var source = WorkflowRuntimeTest.class.getResourceAsStream(
                "/fixtures/real-case/acceptance-reject-20260709.contract.json")) {
            if (source == null) {
                throw new IllegalStateException("historical acceptance REJECT fixture is missing");
            }
            Files.copy(source, sidecar);
        }
        AgentOutputLineageFixture.completeAndPublish(tempDir, workstreamId, sidecar);
        var sidecarRef = tempDir.relativize(sidecar).toString();

        var artifactsDir = paths.workstreamDir().resolve("artifacts");
        Files.createDirectories(artifactsDir);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                artifactsDir.resolve("task-contract.json").toFile(),
                new TaskContract(
                        "tc-replay", 1, Instant.parse("2026-07-09T00:00:00Z"),
                        "Verify structured preparation items",
                        new TaskContract.Scope(
                                List.of(new TaskContract.ScopeEntry(
                                        "backend/app/api/summaries.py",
                                        TaskContract.ScopeOperation.MODIFY)),
                                List.of()),
                        List.of("Backend tests pass", "Frontend type check passes"),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        ContractStatus.READY_FOR_REVIEW, 1,
                        new TaskContract.Source("historical-real-case", "replay")
                ));

        var definition = new WorkflowDefinition(
                "acceptance-reject-replay", 1, "replay.seed",
                List.of(
                        new NodeDefinition("replay.seed", NodeKind.TASK, "replay.seed", Map.of()),
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION,
                                "review.decision", Map.of()),
                        new NodeDefinition("acceptance.classify_failure", NodeKind.TASK,
                                "acceptance.failure_classify", Map.of()),
                        new NodeDefinition("implement.claude_self_test_report", NodeKind.TASK,
                                "replay.stop", Map.of())
                ),
                List.of(
                        new EdgeDefinition("replay.seed", "completed", "acceptance.decision"),
                        new EdgeDefinition("acceptance.decision", "REJECT",
                                "acceptance.classify_failure"),
                        new EdgeDefinition("acceptance.classify_failure",
                                "REJECT_VERIFICATION_INSUFFICIENT",
                                "implement.claude_self_test_report")
                )
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-acceptance-reject-replay", definition);
        var seed = new TaskHandler() {
            @Override
            public String type() {
                return "replay.seed";
            }

            @Override
            public NodeResult execute(TaskContext context) {
                return NodeResult.completed("completed", List.of(sidecarRef),
                        Map.of("fixture", "historical-real-case"));
            }
        };
        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(
                        List.of(seed, new FailureClassifyHandler()),
                        List.of(new ReviewDecisionHandler()),
                        List.of())
        );

        runtime.resume(new WorkflowCommand(tempDir, workstreamId));
        var decisionCurrent = ledger.loadCurrent(paths);
        assertEquals("acceptance.decision", ledger.loadNode(
                paths, decisionCurrent.activeNodeInstanceIds().get(0)).nodeDefinitionId());

        runtime.resume(new WorkflowCommand(tempDir, workstreamId));
        var classifierCurrent = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, classifierCurrent.status());
        assertEquals("acceptance.decision", ledger.loadNode(
                paths, classifierCurrent.activeNodeInstanceIds().get(0)).nodeDefinitionId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));
        assertFalse(Files.exists(artifactsDir.resolve("failure-classification.json")));
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
                "before.gate",
                List.of(
                        new NodeDefinition("before.gate", NodeKind.TASK, "fake.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of())),
                List.of(new EdgeDefinition("before.gate", "completed", "gate.review"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(List.of(new FakeTask()), List.of(), List.of(new FakeGate()))
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
    void changeScopeViolationStopsBeforeAcceptanceContext() throws Exception {
        var workstreamId = "ws-change-scope-runtime";
        git("init");
        git("config", "user.email", "dual@example.test");
        git("config", "user.name", "Dual Test");
        Files.writeString(tempDir.resolve("README.md"), "before\n");
        git("add", "README.md");
        git("commit", "-m", "init");
        var baseCommit = gitOutput("rev-parse", "HEAD").trim();
        writeChangeScopeControlFacts(workstreamId, baseCommit);
        Files.writeString(tempDir.resolve("unexpected.txt"), "outside contract\n");

        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = new WorkflowDefinition(
                "change-scope-cannot-bypass",
                1,
                "implement.artifact_validation",
                List.of(
                        new NodeDefinition(
                                "implement.artifact_validation",
                                NodeKind.TASK,
                                "artifact.validate",
                                Map.of()
                        ),
                        new NodeDefinition(
                                "context.acceptance_codex_verifier_review",
                                NodeKind.TASK,
                                "acceptance.context",
                                Map.of()
                        )
                ),
                List.of(new EdgeDefinition(
                        "implement.artifact_validation",
                        "completed",
                        "context.acceptance_codex_verifier_review"
                ))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-change-scope-runtime", definition);
        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(
                        List.of(new ArtifactValidateHandler(), new AcceptanceContextTask()),
                        List.of(),
                        List.of()
                )
        );

        var result = runtime.resume(new WorkflowCommand(tempDir, workstreamId));

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        assertEquals("implement.artifact_validation", result.activeNode());
        assertEquals(0, acceptanceContextExecutions);
        var node = ledger.loadNode(paths, "node-implement-artifact_validation-001");
        assertEquals(NodeStatus.FAILED, node.status());
        assertEquals("FAIL", node.result().get("change_scope_status"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("agent_task_failed"));
    }

    @Test
    void decisionRoutesUsingContractSidecarOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-decision-runtime"));
        Files.createDirectories(paths.workstreamDir());
        var source = writeOutput("ws-decision-runtime", "review.json", "{}");
        var output = writeOutput("ws-decision-runtime", "review.contract.json", """
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "workstream_id": "ws-decision-runtime",
                  "run_id": "run-001",
                  "node_id": "acceptance.codex_verifier_review",
                  "node_instance_id": "node-acceptance-review-001",
                  "source_output_ref": "%s",
                  "source_artifact_type": "acceptance_review",
                  "decision": "REJECT",
                  "summary": "acceptance failed",
                  "must_fix": ["Repair the implementation"],
                  "failure": {"category_hint": "IMPLEMENTATION_BUG"},
                  "control": {
                    "decision": "REJECT",
                    "summary": "acceptance failed"
                  }
                }
                """.formatted(source.replace("\\", "\\\\")));
        dev.dual.runtime.handler.AgentOutputLineageFixture.publish(
                tempDir, "ws-decision-runtime", tempDir.resolve(output));
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
    void contradictoryAcceptanceCannotPublishOrReachSuccessConsumersEvenAfterColdResume() throws Exception {
        for (var decision : List.of("ACCEPT", "ACCEPT_WITH_NOTES")) {
            for (boolean blocking : List.of(true, false)) {
                var workstreamId = "ws-contradiction-" + decision.toLowerCase() + "-" + blocking;
                var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
                Files.createDirectories(paths.workstreamDir());
                var review = new AgentResultEnvelope.AcceptanceReviewResult(
                        "acceptance_review", decision, "claimed success",
                        decision.equals("ACCEPT") ? "ACCEPTED" : "ACCEPTED_WITH_NOTES", blocking,
                        List.of("reviewed diff"), List.of("tests passed"), List.of(),
                        blocking ? List.of() : List.of("repair the implementation"), List.of(), List.of(),
                        List.of(), "verified", new AgentResultEnvelope.Failure("",
                        AgentResultEnvelope.VerificationRemediation.NONE, List.of(), List.of(), List.of(), List.of(), ""));
                var mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                var output = writeOutput(workstreamId, "acceptance.json", mapper.writeValueAsString(
                        new AgentResultEnvelope(1, workstreamId, "run-001", "acceptance.codex_verifier_review", review)));
                var definition = new WorkflowDefinition("acceptance-guard", 1, "acceptance.codex_verifier_review",
                        List.of(
                                new NodeDefinition("acceptance.codex_verifier_review", NodeKind.TASK, "acpx.agent_run", Map.of("output_ref", output)),
                                new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of()),
                                new NodeDefinition("report.generate_final", NodeKind.TASK, "fake.task", Map.of()),
                                new NodeDefinition("delivery.prepare_pr", NodeKind.TASK, "fake.task", Map.of())),
                        List.of(
                                new EdgeDefinition("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                                new EdgeDefinition("acceptance.decision", decision, "report.generate_final"),
                                new EdgeDefinition("report.generate_final", "completed", "delivery.prepare_pr")));
                var ledger = new WorkflowFileLedger();
                ledger.initialize(paths, "wf-acceptance-guard", definition);
                var registry = new HandlerRegistry(List.of(new AgentOutputTask(), new FakeTask()),
                        List.of(new ReviewDecisionHandler()), List.of());
                var result = new WorkflowRuntime(ledger, registry).resume(new WorkflowCommand(tempDir, workstreamId));
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
                var failed = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
                assertEquals(NodeStatus.FAILED, failed.status());
                assertTrue(failed.result().toString().contains(decision.equals("ACCEPT")
                        ? "FINAL_ACCEPTANCE_EVIDENCE_CONFLICT" : "NOTES_STATUS_BLOCKING_CONFLICT"), failed.result().toString());
                assertFalse(Files.exists(tempDir.resolve(output.replace(".json", ".contract.json"))));
                assertFalse(Files.exists(paths.goalSatisfactionJson()));
                try (var nodes = Files.list(paths.nodesDir())) {
                    assertEquals(1, nodes.count(), "no decision/report/delivery successor may be created");
                }
                var coldResult = new WorkflowRuntime(new WorkflowFileLedger(), registry)
                        .resume(new WorkflowCommand(tempDir, workstreamId));
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, coldResult.status());
                assertEquals(0, fakeTaskExecutions, "report/delivery consumers must never execute");
                assertEquals(1, ledger.readAgentTasks(paths).size(), "cold resume must not retry invalid acceptance");
            }
        }
    }

    @Test
    void invalidAgentOutputContractStopsBeforeRouting() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-agent-contract"));
        Files.createDirectories(paths.workstreamDir());
        var invalidOutput = writeOutput("ws-agent-contract", "design-codex-review.json", """
                {
                  "schema_version": 1,
                  "workstream_id": "ws-agent-contract",
                  "run_id": "run-001",
                  "node_id": "design.codex_arch_review",
                  "result": {
                    "artifact_type": "agent_output_contract",
                    "decision": "READY_FOR_ARCH_REVIEW"
                  }
                }
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
        var agentTasks = ledger.readAgentTasks(paths);
        assertEquals(1, agentTasks.size());
        assertEquals(dev.dual.ledger.file.AgentTaskStatus.FAILED, agentTasks.get(0).status());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("agent_task_failed"));
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
                    "exit_code", 0,
                    "run_id", "run-001"
            ));
        }
    }

    private final class AcceptanceContextTask implements TaskHandler {
        @Override
        public String type() {
            return "acceptance.context";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            acceptanceContextExecutions++;
            return NodeResult.completed("completed", List.of(), Map.of());
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

    private void writeChangeScopeControlFacts(String workstreamId, String baseCommit) throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams").resolve(workstreamId);
        var artifactsDir = workstreamDir.resolve("artifacts");
        var workspacesDir = workstreamDir.resolve("workspaces");
        Files.createDirectories(artifactsDir);
        Files.createDirectories(workspacesDir);
        var contract = new TaskContract(
                "task-" + workstreamId,
                1,
                Instant.parse("2026-07-26T00:00:00Z"),
                "Modify README only",
                new TaskContract.Scope(
                        List.of(new TaskContract.ScopeEntry(
                                "README.md",
                                TaskContract.ScopeOperation.MODIFY
                        )),
                        List.of()
                ),
                List.of("README is updated", "No undeclared files are changed"),
                List.of("Run focused tests"),
                List.of("Keep the change bounded"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "test")
        );
        new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(artifactsDir.resolve("task-contract.json").toFile(), contract);
        Files.writeString(workstreamDir.resolve("workstream.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "%s",
                  "primaryWorkspaceId": "wsp-test"
                }
                """.formatted(workstreamId));
        Files.writeString(workspacesDir.resolve("wsp-test.json"), """
                {
                  "schemaVersion": 1,
                  "workspaceId": "wsp-test",
                  "workstreamId": "%s",
                  "branch": "test",
                  "baseBranch": "main",
                  "baseCommit": "%s"
                }
                """.formatted(workstreamId, baseCommit));
    }

    private void git(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private String gitOutput(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
