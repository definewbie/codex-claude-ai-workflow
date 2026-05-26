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
    void failedRecoverableAutoRetries() throws Exception {
        var paths = setup("ws-auto-retry");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-auto-retry", SINGLE_TASK);

        // Simulate a FAILED node with recoverable=true
        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "rate_limit", "auto_retry_count", 0));

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-auto-retry"));

        assertEquals(WorkflowStatus.COMPLETED, result.status());
        assertTrue(result.message().contains("resumed"));
    }

    @Test
    void failedRecoverableOverThresholdIsRejected() throws Exception {
        var paths = setup("ws-over-threshold");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-over-threshold", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", true, "acpx_failure.reason", "rate_limit", "auto_retry_count", 3));

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-over-threshold"));

        assertTrue(result.message().contains("auto-retry limit reached"));
    }

    @Test
    void failedNotRecoverableIsRejected() throws Exception {
        var paths = setup("ws-not-recoverable");
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-not-recoverable", SINGLE_TASK);

        var node = ledger.loadNode(paths, "node-task-under_test-001");
        writeNodeStatus(ledger, paths, node, NodeStatus.FAILED,
                Map.of("acpx_failure.recoverable", false, "acpx_failure.reason", "fatal_error"));

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

        var runtime = new WorkflowRuntime(ledger, registry(new OkTask()));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-blocked"));

        assertTrue(result.message().contains("BLOCKED"));
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
        // Use ledger's internal writer by calling markRunning (which writes via jsonWriter)
        // For direct status manipulation, use a workaround:
        // Write through ledger's jsonWriter via reflection-free approach
        var field = WorkflowFileLedger.class.getDeclaredField("jsonWriter");
        field.setAccessible(true);
        var writer = (dev.dual.ledger.file.AtomicJsonWriter) field.get(ledger);
        writer.write(paths.nodeJson(node.nodeInstanceId()), updated);
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
