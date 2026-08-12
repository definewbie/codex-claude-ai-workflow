package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.acpx.WorkstreamSessionCloser;
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
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstreamActionServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
        var response = service.approve(tempDir, "ws-approve", "approved by test", false, 1);

        assertEquals("review.approve", response.get("action"));
        var current = ledger.loadCurrent(paths);
        assertEquals("after.approve", ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId());
    }

    @Test
    void completeResolvesCurrentHumanGateAndCompletesWorkflow() throws Exception {
        var paths = createWorkstream("ws-complete", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"));
        var response = service.complete(tempDir, "ws-complete", "done");

        assertEquals("review.complete", response.get("action"));
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, current.status());
        assertEquals(List.of(), current.activeNodeInstanceIds());
    }

    @Test
    void resumeRejectsCompletedWorkflow() throws Exception {
        var paths = createWorkstream("ws-completed-boundary", simpleDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.RUNNING, "should-not-run", "unused"));

        var error = assertThrows(IllegalStateException.class,
                () -> service.resume(tempDir, "ws-completed-boundary", 1));

        assertEquals(true, error.getMessage().contains("already completed"));
    }

    @Test
    void recoverStaleRunningMovesActiveNodeToRecovery() throws Exception {
        var paths = createWorkstream("ws-stale-api", simpleDefinition());
        var ledger = new WorkflowFileLedger();
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.markRunning(paths, entry, Long.MAX_VALUE);
        var runJson = writeDeadRun(paths);

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"));
        var response = service.recoverStaleRunning(tempDir, "ws-stale-api", "process disappeared");

        assertEquals("recovery.stale", response.get("action"));
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(List.of("recovery-" + entry), current.openGateIds());
        var node = ledger.loadNode(paths, entry);
        assertEquals(NodeStatus.FAILED, node.status());
        assertEquals("STALE_RUNNING_PROCESS", node.result().get("reason"));
        assertEquals("INTERRUPTED", MAPPER.readTree(runJson.toFile())
                .path("executionOutcome").path("status").asText());
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

    @Test
    void closesAcpxSessionsWhenActionReachesCompleted() throws Exception {
        var paths = createWorkstream("ws-close-completed", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var closed = new ArrayList<String>();
        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"),
                recordingCloser(closed));

        service.complete(tempDir, "ws-close-completed", "done");

        assertEquals(List.of("ws-close-completed"), closed);
    }

    @Test
    void doesNotCloseSessionsWhenWorkflowNotTerminal() throws Exception {
        createWorkstream("ws-not-terminal", simpleDefinition());
        var closed = new ArrayList<String>();
        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused"),
                recordingCloser(closed));

        service.resume(tempDir, "ws-not-terminal", 3);

        assertTrue(closed.isEmpty());
    }

    @Test
    void sessionCloseFailureDoesNotChangeWorkflowTerminalStatus() throws Exception {
        var paths = createWorkstream("ws-close-failure", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"),
                throwingCloser());

        var response = service.complete(tempDir, "ws-close-failure", "done");

        assertEquals("review.complete", response.get("action"));
        assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
    }

    private static WorkstreamSessionCloser recordingCloser(List<String> sink) {
        return (root, workstreamId) -> {
            sink.add(workstreamId);
            return new WorkstreamSessionCloser.CloseSummary(workstreamId, 0, 0, 0);
        };
    }

    private static WorkstreamSessionCloser throwingCloser() {
        return (root, workstreamId) -> {
            throw new RuntimeException("simulated acpx close failure");
        };
    }

    private WorkstreamPaths createWorkstream(String workstreamId, WorkflowDefinition definition) throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        new WorkflowFileLedger().initialize(paths, "wf-" + workstreamId, definition);
        return paths;
    }

    private Path writeDeadRun(WorkstreamPaths paths) throws Exception {
        var runJson = paths.runsDir().resolve("run-dead/run.json");
        Files.createDirectories(runJson.getParent());
        var startedAt = Instant.now().minusSeconds(30);
        var run = MAPPER.createObjectNode();
        run.put("schemaVersion", 3);
        run.put("status", "running");
        run.put("runId", "run-dead");
        run.put("workstreamId", paths.workstreamId().value());
        run.put("nodeId", "first");
        run.put("agent", "claude");
        run.put("startedAt", startedAt.toString());
        var attempt = run.putObject("executionAttempt");
        attempt.put("attemptId", "att-dead");
        attempt.put("runId", "run-dead");
        attempt.put("workstreamId", paths.workstreamId().value());
        attempt.put("nodeId", "first");
        attempt.put("agent", "claude");
        attempt.put("status", "RUNNING");
        attempt.put("processStatus", "RUNNING");
        attempt.put("startedAt", startedAt.toString());
        var event = MAPPER.createObjectNode();
        event.put("kind", "process_started");
        event.put("source", "adapter");
        event.put("sequence", 1);
        event.put("observedAt", startedAt.toString());
        event.put("elapsedMs", 0);
        event.putObject("details").put("pid", Long.MAX_VALUE);
        run.putArray("executionTimeline").add(event);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), run);
        return runJson;
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

    private static WorkflowDefinition completeGateDefinition() {
        return new WorkflowDefinition(
                "api-action-workflow",
                1,
                "before.gate",
                List.of(
                        new NodeDefinition("before.gate", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review",
                                Map.of("allowed_actions", List.of("approve", "revise", "complete")))
                ),
                List.of(
                        new EdgeDefinition("before.gate", "completed", "gate.review")
                )
        );
    }
}
