package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.GoalDomainOutcome;
import dev.dual.ledger.file.GoalSatisfactionDocument;
import dev.dual.ledger.file.AgentTaskStatus;
import dev.dual.ledger.file.NodeExecutionStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunningStateGuardTest {
    @TempDir
    Path tempDir;

    private static final WorkflowDefinition SINGLE_TASK = new WorkflowDefinition(
            "test-lifecycle", 1, "task.under_test",
            List.of(new NodeDefinition("task.under_test", NodeKind.TASK, "test.handler", Map.of())),
            List.of()
    );

    private static final WorkflowDefinition ACCEPTANCE_DECISION = new WorkflowDefinition(
            "test-lifecycle", 1, "acceptance.decision",
            List.of(new NodeDefinition("acceptance.decision", NodeKind.DECISION, "test.decision", Map.of())),
            List.of()
    );

    @Test
    void readyNodeMarksRunningAndExecutes() throws Exception {
        var paths = setup("ws-ready");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-ready", SINGLE_TASK);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-ready"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
    }

    @Test
    void executedNodeWritesWorkflowAndNodeExecutionLedger() throws Exception {
        var paths = setup("ws-execution-ledger");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-execution-ledger", SINGLE_TASK);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-execution-ledger"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        try (var workflowExecutions = Files.list(paths.workflowExecutionsDir())) {
            assertEquals(1, workflowExecutions.filter(path -> path.getFileName().toString().endsWith(".json")).count());
        }
        var nodeExecutions = ledger.readNodeExecutions(paths);
        assertEquals(1, nodeExecutions.size());
        var nodeExecution = nodeExecutions.get(0);
        assertEquals("node-task-under_test-001", nodeExecution.nodeInstanceId());
        assertTrue(nodeExecution.workflowExecutionId().startsWith("wfe-"));
        assertEquals(NodeExecutionStatus.SUCCEEDED, nodeExecution.status());
        assertEquals(NodeStatus.COMPLETED, nodeExecution.nodeStatus());
        assertEquals(true, nodeExecution.result().get("ok"));
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        assertEquals(nodeExecution.nodeExecutionId(), node.latestExecutionId());
    }

    @Test
    void executedNodeWritesAgentTaskDispatchLedger() throws Exception {
        var definition = new WorkflowDefinition(
                "test-lifecycle", 1, "task.under_test",
                List.of(new NodeDefinition("task.under_test", NodeKind.TASK, "test.handler", Map.of(
                        "agent", "claude",
                        "session", "claude-main",
                        "prompt_template", "test-template",
                        "expected_contract", "test_contract"
                ))),
                List.of()
        );
        var paths = setup("ws-agent-task");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-agent-task", definition);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-agent-task"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        var tasks = ledger.readAgentTasks(paths);
        assertEquals(1, tasks.size());
        var task = tasks.get(0);
        assertEquals(AgentTaskStatus.SUCCEEDED, task.status());
        assertEquals("task.under_test", task.nodeDefinitionId());
        assertEquals("test.handler", task.handlerType());
        assertEquals("claude", task.agent());
        assertEquals("claude-main", task.session());
        assertEquals("test-template", task.promptTemplate());
        assertEquals("test_contract", task.expectedContract());
        assertTrue(task.nodeExecutionId().startsWith("nxe-"));

        var nodeExecution = ledger.readNodeExecutions(paths).get(0);
        assertEquals(task.agentTaskId(), nodeExecution.agentTaskId());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("agent_task_queued"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("agent_task_started"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("agent_task_completed"));
    }

    @Test
    void failedNodeMarksAgentTaskFailedAndKeepsExecutionLink() throws Exception {
        var paths = setup("ws-agent-task-failed");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-agent-task-failed", SINGLE_TASK);

        var runtime = new WorkflowRuntime(ledger, registry(new FailingTask()));
        try {
            runtime.resume(new WorkflowCommand(tempDir, "ws-agent-task-failed"));
        } catch (IllegalStateException expected) {
            // expected
        }

        var tasks = ledger.readAgentTasks(paths);
        assertEquals(1, tasks.size());
        var task = tasks.get(0);
        assertEquals(AgentTaskStatus.FAILED, task.status());
        assertTrue(task.errorMessage().contains("boom"));
        assertTrue(task.nodeExecutionId().startsWith("nxe-"));

        var nodeExecution = ledger.readNodeExecutions(paths).get(0);
        assertEquals(NodeExecutionStatus.FAILED, nodeExecution.status());
        assertEquals(task.agentTaskId(), nodeExecution.agentTaskId());
        assertEquals(task.nodeExecutionId(), nodeExecution.nodeExecutionId());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("agent_task_failed"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void handlerExceptionClosesLocalAndQueuedClaimsWithoutNativeSuccess(boolean local) throws Exception {
        var paths = setup("ws-handler-exception-" + local);
        var ledger = RuntimeTestLedger.ledger();
        RuntimeTestLedger.initialize(ledger, paths, "wf-exception", SINGLE_TASK);
        TaskHandler failing = new TaskHandler() {
            public String type() { return "test.handler"; }
            public boolean localLedgerMutation() { return local; }
            public NodeResult execute(TaskContext context) { throw new IllegalStateException("boom"); }
        };
        var handlers = registry(failing);
        var runtime = new WorkflowRuntime(ledger, handlers,
                local ? AgentTaskDispatchMode.INLINE : AgentTaskDispatchMode.WORKER);
        var command = new WorkflowCommand(tempDir, paths.workstreamId().value());
        if (local) org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> runtime.resume(command));
        else {
            // Queue a real task through the ledger; product queued mode selects native tasks only.
            try (var write = ledger.writeSession(paths, "queue handler test", "request")) {
                var scoped = write.ledger();
                var current = scoped.loadCurrent(paths);
                var workflow = scoped.startWorkflowExecution(paths, current, "test queue");
                var node = scoped.loadNode(paths, current.activeNodeInstanceIds().get(0));
                scoped.createAgentTask(paths, workflow.workflowExecutionId(), SINGLE_TASK.node(node.nodeDefinitionId()), node);
                write.commit();
            }
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> new AgentWorker(ledger, handlers).runOne(command));
        }
        assertEquals(AgentTaskStatus.FAILED, ledger.readAgentTasks(paths).get(0).status());
        var failed = ledger.readNodeExecutions(paths).get(0);
        assertEquals(NodeExecutionStatus.FAILED, failed.status());
        assertEquals("exception", failed.outcome());
        assertEquals(NodeStatus.FAILED, ledger.loadNode(paths, failed.nodeInstanceId()).status());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
        assertFalse(failed.result().containsKey("completion_ref"));
    }

    @Test
    void completedWorkflowResumeIsRejectedBeforeCreatingNewExecution() throws Exception {
        var paths = setup("ws-terminal-refusal");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-terminal-refusal", SINGLE_TASK);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        assertEquals(WorkflowStatus.COMPLETED,
                runtime.resume(new WorkflowCommand(tempDir, "ws-terminal-refusal")).status());
        assertEquals(1, ledger.readNodeExecutions(paths).size());

        var terminalResult = runtime.resume(new WorkflowCommand(tempDir, "ws-terminal-refusal"));

        assertEquals(WorkflowStatus.COMPLETED, terminalResult.status());
        assertTrue(terminalResult.message().contains("workflow is terminal"));
        assertEquals(1, ledger.readNodeExecutions(paths).size());
        try (var workflowExecutions = Files.list(paths.workflowExecutionsDir())) {
            assertEquals(1, workflowExecutions.filter(path -> path.getFileName().toString().endsWith(".json")).count());
        }
    }

    @Test
    void acceptanceRejectWritesGoalSatisfactionNeedsRepair() throws Exception {
        var paths = setup("ws-goal-repair");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-goal-repair", ACCEPTANCE_DECISION);

        var runtime = new WorkflowRuntime(ledger, decisionRegistry(new RejectDecision()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-goal-repair"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        var document = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .readValue(paths.goalSatisfactionJson().toFile(), GoalSatisfactionDocument.class);
        assertEquals(GoalDomainOutcome.NEEDS_REPAIR, document.domainOutcome());
        assertEquals("acceptance.decision", document.nodeDefinitionId());
        assertEquals("REJECT", document.outcome());
        assertEquals("iter-001", document.iterationId());
    }

    @Test
    void runningNodeWithDeadPidMovesToRecoveryWithoutReexecuting() throws Exception {
        var paths = setup("ws-dead-pid");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-dead-pid", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.RUNNING, Map.of("running_pid", 999999L));

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-dead-pid"));

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        var current = ledger.loadCurrent(paths);
        var recoveredNode = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals(NodeStatus.FAILED, recoveredNode.status());
        assertEquals("STALE_RUNNING_PROCESS", recoveredNode.result().get("reason"));
        assertEquals(true, recoveredNode.result().get("recoverable"));
        assertEquals(List.of("recovery-" + recoveredNode.nodeInstanceId()), current.openGateIds());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_stale_running_detected"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_stale_running_recovered"));
    }

    @Test
    void staleLocalNodeDoesNotRewriteAnUnboundMutableRun() throws Exception {
        var paths = setup("ws-reconcile");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-reconcile", SINGLE_TASK);
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.RUNNING, Map.of("running_pid", 999999L));

        // The in-flight agent run that never returned: prepared, no terminal outcome, dead pid.
        var runJson = paths.runsDir().resolve("run-stale").resolve("run.json");
        Files.createDirectories(runJson.getParent());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var runNode = mapper.createObjectNode();
        runNode.put("schemaVersion", 3);
        runNode.put("status", "prepared");
        runNode.put("runId", "run-stale");
        runNode.put("workstreamId", "ws-reconcile");
        runNode.put("nodeId", "task.under_test");
        runNode.put("agent", "claude");
        runNode.put("promptPath", ".agent/runs/run-stale/prompts/task.md");
        runNode.put("stdoutPath", ".agent/runs/run-stale/outputs/task.md");
        runNode.put("stderrPath", ".agent/runs/run-stale/outputs/task.stderr.log");
        runNode.put("startedAt", java.time.Instant.now().toString());
        var attempt = mapper.createObjectNode();
        attempt.put("attemptId", "att-run-stale");
        attempt.put("runId", "run-stale");
        attempt.put("status", "RUNNING");
        attempt.put("processStatus", "RUNNING");
        attempt.put("startedAt", java.time.Instant.now().toString());
        runNode.set("executionAttempt", attempt);
        var processResult = mapper.createObjectNode();
        processResult.put("pid", 999999L);
        runNode.set("processResult", processResult);
        mapper.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), runNode);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-reconcile"));

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        var current = ledger.loadCurrent(paths);
        var recoveredNode = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals(NodeStatus.FAILED, recoveredNode.status());
        var reconciled = mapper.readTree(runJson.toFile());
        assertEquals(mapper.readTree(mapper.writeValueAsString(runNode)), reconciled, "mutable unbound run is preserved, not promoted into a completion");
        assertEquals("STALE_RUNNING_PROCESS", recoveredNode.result().get("reason"));

    }

    @Test
    void unboundMutableRunCannotBlockOrImpersonateTheCurrentReadyClaim() throws Exception {
        var paths = setup("ws-reconcile-before-run");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-reconcile-before-run", SINGLE_TASK);

        // A leftover in-flight agent run from a previous attempt that never returned.
        var runJson = paths.runsDir().resolve("run-leftover").resolve("run.json");
        Files.createDirectories(runJson.getParent());
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var runNode = mapper.createObjectNode();
        runNode.put("schemaVersion", 3);
        runNode.put("status", "prepared");
        runNode.put("runId", "run-leftover");
        runNode.put("workstreamId", "ws-reconcile-before-run");
        runNode.put("nodeId", "task.under_test");
        runNode.put("agent", "claude");
        runNode.put("startedAt", java.time.Instant.now().toString());
        var attempt = mapper.createObjectNode();
        attempt.put("attemptId", "att-run-leftover");
        attempt.put("runId", "run-leftover");
        attempt.put("status", "RUNNING");
        attempt.put("processStatus", "RUNNING");
        attempt.put("startedAt", java.time.Instant.now().toString());
        runNode.set("executionAttempt", attempt);
        var processResult = mapper.createObjectNode();
        processResult.put("pid", 999999L);
        runNode.set("processResult", processResult);
        mapper.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), runNode);

        var executed = new int[] {0};
        var runtime = new WorkflowRuntime(ledger, registry(new CountingTask(executed)));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-reconcile-before-run"));

        var reconciled = mapper.readTree(runJson.toFile());
        assertEquals(mapper.readTree(mapper.writeValueAsString(runNode)), reconciled, "historical unbound bytes are neither recovered nor adopted");
        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertEquals(1, executed[0], "a valid unrelated ready task remains executable");
        assertEquals(1, ledger.readNodeExecutions(paths).size());
        assertFalse(ledger.readNodeExecutions(paths).getFirst().result().containsKey("completion_ref"));

    }

    @Test
    void deadPidMarksLatestNodeExecutionStale() throws Exception {
        var paths = setup("ws-dead-pid-execution");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-dead-pid-execution", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        var executor = new ProcessBuilder("sh", "-c", "read input").start();
        final dev.dual.ledger.file.NodeExecutionDocument nodeExecution;
        try { nodeExecution = claim(ledger, paths, executor.pid()); }
        finally { executor.destroyForcibly(); executor.waitFor(); }

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-dead-pid-execution"));

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        var executions = ledger.readNodeExecutions(paths);
        var staleExecution = executions.stream()
                .filter(execution -> execution.nodeExecutionId().equals(nodeExecution.nodeExecutionId()))
                .findFirst()
                .orElseThrow();
        assertEquals(NodeExecutionStatus.STALE, staleExecution.status());
        assertEquals("STALE_RUNNING_PROCESS", staleExecution.result().get("reason"));
    }

    @Test
    void runningNodeWithLivePidIsRejected() throws Exception {
        var paths = setup("ws-live-pid");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-live-pid", SINGLE_TASK);

        ledger.markRunning(paths, "node-task-under_test-001", 999999L);
        writeNodeStatus(ledger, paths, ledger.loadNode(paths, "node-task-under_test-001"),
                NodeStatus.RUNNING, Map.of("running_pid", ProcessHandle.current().pid()));

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-live-pid"));

        assertTrue(result.message().contains("RUNNING"));
        assertTrue(result.message().contains("agent process still alive"));
    }

    @Test
    void failedRecoverableAutoRetriesThroughWaitingForRecovery() throws Exception {
        var paths = setup("ws-auto-retry");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-auto-retry", SINGLE_TASK);

        // Simulate a FAILED node that was routed through stopOnNonCompletedNode
        // (workflow is WAITING_FOR_RECOVERY, node is FAILED with recoverable=true)
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        var previousExecution = claim(ledger, paths, ProcessHandle.current().pid());
        AgentExecutionCompletion.recordHandlerFailure(ledger, paths, previousExecution, new RuntimeException("rate limit"));
        node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT", "auto_retry_count", 0));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-auto-retry"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertTrue(result.message().contains("resumed"));
        var executions = ledger.readNodeExecutions(paths);
        assertEquals(2, executions.size());
        var latestNode = ledger.loadNode(paths, "node-task-under_test-001");
        assertTrue(executions.stream().anyMatch(execution -> execution.nodeExecutionId().equals(previousExecution.nodeExecutionId())
                && execution.status() == NodeExecutionStatus.FAILED));
        assertTrue(executions.stream().anyMatch(execution -> execution.nodeExecutionId().equals(latestNode.latestExecutionId())
                && execution.status() == NodeExecutionStatus.SUCCEEDED));
        assertEquals("iter-001", latestNode.iterationId());
    }

    @Test
    void outcomeManualOnlyPreventsLegacyRecoverableAutoRetry() throws Exception {
        var paths = setup("ws-outcome-manual");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-outcome-manual", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED, Map.of(
                "acpx_failure.recoverable", true,
                "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT",
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "rate_limited",
                        "retryPolicy", "MANUAL_ONLY",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE"
                ),
                "auto_retry_count", 0
        ));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-outcome-manual"));

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        assertTrue(result.message().contains("manual recovery or retry required"));
        assertEquals(0, ledger.readNodeExecutions(paths).size());
    }

    @Test
    void outcomeAutoAllowedIsTheNewAutoRetrySourceOfTruth() throws Exception {
        var paths = setup("ws-outcome-auto");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-outcome-auto", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED, Map.of(
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "rate_limited",
                        "retryPolicy", "AUTO_ALLOWED",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE"
                ),
                "auto_retry_count", 0
        ));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-outcome-auto"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
    }

    @Test
    void failedRecoverableRateLimitAllowsFiveRetries() throws Exception {
        var paths = setup("ws-rate-limit");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-rate-limit", SINGLE_TASK);

        // 4 retries still allowed (policy allows 5)
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT", "auto_retry_count", 4));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-rate-limit"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
    }

    @Test
    void failedRecoverableOverThresholdIsRejected() throws Exception {
        var paths = setup("ws-over-threshold");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-over-threshold", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT", "auto_retry_count", 5));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-over-threshold"));

        assertTrue(result.message().contains("auto-retry limit reached"));
        assertTrue(result.message().contains("5/5"));
        assertTrue(result.message().contains("RECOVERABLE_RATE_LIMIT"));
    }

    @Test
    void failedNotRecoverableIsRejected() throws Exception {
        var paths = setup("ws-not-recoverable");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-not-recoverable", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", false, "acpx_failure.reason", "fatal_error"));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-not-recoverable"));

        assertTrue(result.message().contains("not recoverable"));
    }

    @Test
    void blockedNodeIsRejected() throws Exception {
        var paths = setup("ws-blocked");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-blocked", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.BLOCKED,
                Map.of("reason", "revision_limit_reached"));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-blocked"));

        assertTrue(result.message().contains("BLOCKED"));
    }

    @Test
    void contextWindowAllowsFewerRetriesThanRateLimit() throws Exception {
        var paths = setup("ws-ctx-window");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-ctx-window", SINGLE_TASK);

        // CONTEXT_WINDOW policy allows max 2 retries, so count=2 should be rejected
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "RECOVERABLE_CONTEXT_WINDOW", "auto_retry_count", 2));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-ctx-window"));

        assertTrue(result.message().contains("auto-retry limit reached"));
        assertTrue(result.message().contains("2/2"));
    }

    @Test
    void interruptedAgentOutputAllowsTwoRetries() throws Exception {
        var paths = setup("ws-interrupted-output");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-interrupted-output", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true,
                        "acpx_failure.reason", "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED",
                        "auto_retry_count", 2));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-interrupted-output"));

        assertTrue(result.message().contains("auto-retry limit reached"));
        assertTrue(result.message().contains("2/2"));
        assertTrue(result.message().contains("RECOVERABLE_AGENT_OUTPUT_INTERRUPTED"));
    }

    @Test
    void unknownRecoverableFailureUsesDefaultPolicy() throws Exception {
        var paths = setup("ws-unknown");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-unknown", SINGLE_TASK);

        // Unknown reason gets default policy (max 3)
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "UNKNOWN_TRANSIENT", "auto_retry_count", 2));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-unknown"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
    }

    @Test
    void configOverrideTakesPrecedenceOverPolicy() throws Exception {
        var definition = new WorkflowDefinition(
                "test-lifecycle", 1, "task.under_test",
                List.of(new NodeDefinition("task.under_test", NodeKind.TASK, "test.handler",
                        Map.of("retry.max_retries", 1))),
                List.of()
        );
        var paths = setup("ws-config-override");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-config-override", definition);

        // Default policy for UNKNOWN_TRANSIENT is max 3, but config says max 1.
        // count=1 should hit the config limit even though default policy would allow more.
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "UNKNOWN_TRANSIENT", "auto_retry_count", 1));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-config-override"));

        assertTrue(result.message().contains("auto-retry limit reached"));
    }

    @Test
    void retryDelayRecordedInEvent() throws Exception {
        var paths = setup("ws-delay-event");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-delay-event", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT", "auto_retry_count", 0));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-delay-event"));
        assertEquals(WorkflowStatus.COMPLETED, result.status());

        // Verify the event log contains retry_delay_ms
        var eventsPath = paths.eventsJsonl();
        var events = Files.readAllLines(eventsPath).stream()
                .map(line -> {
                    try { return new com.fasterxml.jackson.databind.ObjectMapper().readValue(line, java.util.Map.class); }
                    catch (Exception e) { return java.util.Map.of(); }
                })
                .filter(e -> "node_auto_retry".equals(e.get("event_type")))
                .toList();
        assertEquals(1, events.size());
        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) events.get(0).get("payload");
        assertEquals(3000L, ((Number) payload.get("retry_delay_ms")).longValue());
    }

    // --- Helpers ---

    private WorkstreamPaths setup(String workstreamId) throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        return paths;
    }

    private HandlerRegistry registry(TaskHandler task) {
        return new HandlerRegistry(List.of(task), List.of(), List.of());
    }

    private HandlerRegistry decisionRegistry(DecisionHandler decision) {
        return new HandlerRegistry(List.of(), List.of(decision), List.of());
    }

    private void writeNodeStatus(WorkflowFileLedger ledger, WorkstreamPaths paths,
                                  NodeInstanceDocument node, NodeStatus status,
                                  Map<String, Object> extraResult) throws Exception {
        var merged = new LinkedHashMap<String, Object>();
        if (node.result() != null) merged.putAll(node.result());
        merged.putAll(extraResult);
        var updated = new NodeInstanceDocument(
                node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(),
                node.kind(), node.handlerType(), status,
                node.stageId(), node.iterationId(), node.inputRefs(), node.inputTransition(), node.outputRefs(),
                merged, node.runRef(), node.latestExecutionId(), node.startedAt(), null
        );
        var field = WorkflowFileLedger.class.getDeclaredField("jsonWriter");
        field.setAccessible(true);
        var writer = (dev.dual.ledger.file.AtomicJsonWriter) field.get(ledger);
        writer.write(paths.nodeJson(node.nodeInstanceId()), updated);
    }

    private void setWorkflowStatus(WorkflowFileLedger ledger, WorkstreamPaths paths, WorkflowStatus status) throws Exception {
        var current = ledger.loadCurrent(paths);
        var updated = new dev.dual.ledger.file.WorkflowCurrentDocument(
                current.schemaVersion(), current.workstreamId(), current.workflowInstanceId(),
                current.workflowId(), current.definitionVersion(), status,
                current.activeNodeInstanceIds(), current.openGateIds(), current.lastEventId(),
                java.time.Instant.now(), current.commitSequence()
        );
        var field = WorkflowFileLedger.class.getDeclaredField("jsonWriter");
        field.setAccessible(true);
        var writer = (dev.dual.ledger.file.AtomicJsonWriter) field.get(ledger);
        writer.write(paths.workflowCurrentJson(), updated);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"before_manifest", "after_replace"})
    void exceptionClosureCannotReplaceAPreparedSuccessfulLocalResult(String cut) throws Exception {
        var paths = setup("ws-local-prefix-" + cut);
        var ledger = RuntimeTestLedger.ledger();
        RuntimeTestLedger.initialize(ledger, paths, "wf-prefix", SINGLE_TASK);
        var execution = claim(ledger, paths, ProcessHandle.current().pid());
        var interrupted = dev.dual.ledger.file.InterruptedTestLedger.at(cut);
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> {
            try (var write = interrupted.writeSession(paths, "local result", execution.nodeExecutionId())) {
                var scoped = write.ledger();
                var result = NodeResult.completed("completed", List.of(), Map.of("local_result", "fixed"));
                scoped.markAgentTaskFinished(paths, scoped.readAgentTask(paths, execution.agentTaskId()).orElseThrow(), result);
                scoped.finishNodeExecution(paths, execution, result);
                scoped.completeAndRoute(paths, SINGLE_TASK, execution.nodeInstanceId(), result.status(), result.outcome(), result.outputRefs(), result.result());
                write.commit();
            }
        });
        if (cut.equals("before_manifest")) {
            AgentExecutionCompletion.recordHandlerFailure(ledger, paths, execution, new IllegalStateException("local write failed"));
            assertEquals(NodeExecutionStatus.FAILED, ledger.readNodeExecutions(paths).getFirst().status());
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
        } else {
            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                    AgentExecutionCompletion.recordHandlerFailure(ledger, paths, execution, new IllegalStateException("late exception")));
            try (var finish = ledger.writeSession(paths, "finish fixed bytes", "recover")) { finish.commit(); }
            assertEquals(NodeExecutionStatus.SUCCEEDED, ledger.readNodeExecutions(paths).getFirst().status());
            assertEquals("fixed", ledger.readNodeExecutions(paths).getFirst().result().get("local_result"));
            assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
            AgentExecutionCompletion.recordHandlerFailure(ledger, paths, execution, new IllegalStateException("duplicate exception"));
            assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
        }
    }

    private dev.dual.ledger.file.NodeExecutionDocument claim(WorkflowFileLedger ledger, WorkstreamPaths paths, long pid) throws Exception {
        try (var write = ledger.writeSession(paths, "claim test execution", "claim")) {
            var scoped = write.ledger();
            var current = scoped.loadCurrent(paths);
            var wf = scoped.startWorkflowExecution(paths, current, "test");
            var node = scoped.loadNode(paths, current.activeNodeInstanceIds().getFirst());
            var task = scoped.createAgentTask(paths, wf.workflowExecutionId(), SINGLE_TASK.node(node.nodeDefinitionId()), node);
            var execution = scoped.startNodeExecution(paths, wf.workflowExecutionId(), node, task, pid);
            scoped.markAgentTaskRunning(paths, task, execution.nodeExecutionId());
            scoped.markRunning(paths, node.nodeInstanceId(), pid);
            write.commit(); return execution;
        }
    }

    private static final class OkTask implements TaskHandler {
        @Override
        public String type() { return "test.handler"; }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of(), Map.of("ok", true));
        }
    }

    private record CountingTask(int[] count) implements TaskHandler {
        @Override
        public String type() { return "test.handler"; }

        @Override
        public NodeResult execute(TaskContext context) {
            count[0]++;
            return NodeResult.completed("completed", List.of(), Map.of("ok", true));
        }
    }

    private static final class FailingTask implements TaskHandler {
        @Override
        public String type() { return "test.handler"; }

        @Override
        public NodeResult execute(TaskContext context) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class RejectDecision implements DecisionHandler {
        @Override
        public String type() { return "test.decision"; }

        @Override
        public NodeResult evaluate(DecisionContext context) {
            return NodeResult.completed("REJECT", List.of("runs/reject.md"), Map.of(
                    "review_decision", "REJECT",
                    "reason", "implementation gap"
            ));
        }
    }
}
