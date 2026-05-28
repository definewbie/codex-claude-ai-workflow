package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunningStateGuardTest {
    @TempDir
    Path tempDir;

    private static final WorkflowDefinition SINGLE_TASK = new WorkflowDefinition(
            "test-lifecycle", 1, "task.under_test",
            List.of(new NodeDefinition("task.under_test", NodeKind.TASK, "test.handler", Map.of())),
            List.of()
    );

    @Test
    void readyNodeMarksRunningAndExecutes() throws Exception {
        var paths = setup("ws-ready");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-ready", SINGLE_TASK);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-ready"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
    }

    @Test
    void runningNodeWithDeadPidAutoRecovers() throws Exception {
        var paths = setup("ws-dead-pid");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-dead-pid", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.RUNNING, Map.of("running_pid", 999999L));

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-dead-pid"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
    }

    @Test
    void runningNodeWithLivePidIsRejected() throws Exception {
        var paths = setup("ws-live-pid");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-live-pid", SINGLE_TASK);

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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-auto-retry", SINGLE_TASK);

        // Simulate a FAILED node that was routed through stopOnNonCompletedNode
        // (workflow is WAITING_FOR_RECOVERY, node is FAILED with recoverable=true)
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT", "auto_retry_count", 0));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-auto-retry"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertTrue(result.message().contains("resumed"));
    }

    @Test
    void failedRecoverableRateLimitAllowsFiveRetries() throws Exception {
        var paths = setup("ws-rate-limit");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-rate-limit", SINGLE_TASK);

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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-over-threshold", SINGLE_TASK);

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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-not-recoverable", SINGLE_TASK);

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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-blocked", SINGLE_TASK);

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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-ctx-window", SINGLE_TASK);

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
    void unknownRecoverableFailureUsesDefaultPolicy() throws Exception {
        var paths = setup("ws-unknown");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-unknown", SINGLE_TASK);

        // Unknown reason gets default policy (max 3)
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "UNKNOWN_TRANSIENT", "auto_retry_count", 2));
        setWorkflowStatus(ledger, paths, WorkflowStatus.WAITING_FOR_RECOVERY);

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-unknown"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
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

    private void writeNodeStatus(WorkflowFileLedger ledger, WorkstreamPaths paths,
                                  NodeInstanceDocument node, NodeStatus status,
                                  Map<String, Object> extraResult) throws Exception {
        var merged = new LinkedHashMap<String, Object>();
        if (node.result() != null) merged.putAll(node.result());
        merged.putAll(extraResult);
        var updated = new NodeInstanceDocument(
                node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(),
                node.kind(), node.handlerType(), status,
                node.stageId(), node.iterationId(), node.inputRefs(), node.outputRefs(),
                merged, node.runRef(), node.startedAt(), null
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
                java.time.Instant.now()
        );
        var field = WorkflowFileLedger.class.getDeclaredField("jsonWriter");
        field.setAccessible(true);
        var writer = (dev.dual.ledger.file.AtomicJsonWriter) field.get(ledger);
        writer.write(paths.workflowCurrentJson(), updated);
    }

    private static final class OkTask implements TaskHandler {
        @Override
        public String type() { return "test.handler"; }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of(), Map.of("ok", true));
        }
    }
}
