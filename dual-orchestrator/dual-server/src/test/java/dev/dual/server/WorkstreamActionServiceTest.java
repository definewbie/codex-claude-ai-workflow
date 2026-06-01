package dev.dual.server;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.WorkflowRuntimeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkstreamActionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void resumeRunsUntilPausedBoundary() throws Exception {
        createWorkstream("ws-run", simpleDefinition());
        var results = new ArrayDeque<>(List.of(
                new WorkflowRuntimeResult("ws-run", WorkflowStatus.RUNNING, "middle", "step one"),
                new WorkflowRuntimeResult("ws-run", WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused")
        ));
        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command -> results.removeFirst());

        var response = service.resume(tempDir, "ws-run", 5);

        assertEquals("resume", response.get("action"));
        assertEquals("WAITING_FOR_HUMAN", response.get("status"));
        var steps = (List<Map<String, Object>>) response.get("steps");
        assertEquals(2, steps.size());
        assertEquals("middle", steps.get(0).get("activeNode"));
        assertEquals("gate.review", response.get("activeNode"));
    }

    @Test
    void approveResolvesCurrentHumanGate() throws Exception {
        var paths = createWorkstream("ws-approve", gateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"));
        var response = service.approve(tempDir, "ws-approve", false, 1);

        assertEquals("review.approve", response.get("action"));
        var current = ledger.loadCurrent(paths);
        assertEquals("after.approve", ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId());
    }

    @Test
    void retryMarksActiveFailedNodeReady() throws Exception {
        var paths = createWorkstream("ws-retry", simpleDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.FAILED, "failed", List.of(), Map.of("reason", "test"));

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"));
        var response = service.retry(tempDir, "ws-retry", "try again");

        assertEquals("retry", response.get("action"));
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.RUNNING, current.status());
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals(NodeStatus.READY, node.status());
        assertEquals("try again", node.result().get("retry_note"));
    }

    private WorkstreamPaths createWorkstream(String workstreamId, WorkflowDefinition definition) throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        new WorkflowFileLedger().initialize(paths, "wf-" + workstreamId, definition);
        return paths;
    }

    private static WorkflowDefinition simpleDefinition() {
        return new WorkflowDefinition(
                "api-action-workflow",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
    }

    private static WorkflowDefinition gateDefinition() {
        return new WorkflowDefinition(
                "api-action-workflow",
                1,
                "before.gate",
                List.of(
                        new NodeDefinition("before.gate", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("after.approve", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("before.gate", "completed", "gate.review"),
                        new EdgeDefinition("gate.review", "approved", "after.approve")
                )
        );
    }
}
