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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstreamActionServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path tempDir;

    @Test
    void scopeRevisionApiCannotBypassTheLedgerGateGuard() throws Exception {
        var paths = createWorkstream("ws-scope-request", gateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var before = ledger.loadCurrent(paths);
        var service = new WorkstreamActionService(ledger, () -> command -> {
            throw new AssertionError("unauthorized request must not resume a provider");
        });
        assertThrows(Exception.class, () -> service.requestScopeRevision(tempDir, "ws-scope-request", "retain files", true, 5));
        assertEquals(before, ledger.loadCurrent(paths));
    }

    @Test
    void collectionWithoutQualifiedBaselineCannotResolveOrRun() throws Exception {
        var definition = new WorkflowDefinition("collect", 1, "start", List.of(
                new NodeDefinition("start", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of("gate_purpose", "implementation_repair",
                        "allowed_actions", List.of("collect_evidence"))),
                new NodeDefinition("collect", NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", "self_test_report"))),
                List.of(new EdgeDefinition("start", "completed", "repair"), new EdgeDefinition("repair", "collect_evidence", "collect")));
        var paths = createWorkstream("ws-api-collect", definition);
        var ledger = new WorkflowFileLedger();
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var before = ledger.loadCurrent(paths);
        var service = new WorkstreamActionService(ledger, () -> command -> { throw new AssertionError("must not resume"); });
        assertThrows(java.io.IOException.class, () -> service.collectEvidence(tempDir, "ws-api-collect", "Collect evidence", true, 5));
        assertEquals(before, ledger.loadCurrent(paths));
    }

    @Test
    void actionRequestApiRecordsWithoutCallingTheResumer() throws Exception {
        var base = gateDefinition();
        var nodes = base.nodes().stream().map(node -> node.kind() == NodeKind.GATE
                ? new NodeDefinition(node.id(), node.kind(), node.handlerType(),
                Map.of("gate_purpose", "implementation_repair", "allowed_actions", List.of("approve", "revise", "request_action")))
                : node).toList();
        var paths = createWorkstream("ws-action-request", new WorkflowDefinition(base.workflowId(), base.version(),
                base.entryNodeId(), nodes, base.edges()));
        var ledger = new WorkflowFileLedger();
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var before = ledger.loadCurrent(paths);
        var service = new WorkstreamActionService(ledger, () -> command -> {
            throw new AssertionError("registering a need must never execute an agent");
        });
        var request = new dev.dual.ledger.file.HumanActionRequest("r1", before.activeNodeInstanceIds().getFirst(),
                List.of(), "Device evidence", "No available device");
        assertEquals("review.request_action", service.requestAction(tempDir, "ws-action-request", request).get("action"));
        assertEquals(before, ledger.loadCurrent(paths));
        assertEquals(1, ((List<?>) ledger.loadNode(paths, request.gateNodeInstanceId()).result().get("action_requests")).size());
    }

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
    void apiPublicWebSearchApprovalUsesTheLedgerHumanAuthorityBoundary() throws Exception {
        var paths = createWorkstream("ws-websearch", simpleDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var runPath = paths.runsDir().resolve("run-websearch/run.json");
        Files.createDirectories(runPath.getParent());
        Files.writeString(runPath, """
                {"executionTimeline":[{"kind":"permission_decision","details":{
                  "authorityDisposition":"ESCALATION_REQUIRED","disposition":"DENIED",
                  "toolCallId":"call-websearch-1","toolKind":"fetch","toolName":"WebSearch",
                  "title":"Mailpit latest stable Docker tag",
                  "resource":"Mailpit latest stable Docker tag"}}]}
                """);
        var runRef = tempDir.relativize(runPath).toString();
        ledger.completeAndRoute(
                paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "failed",
                List.of(runRef), Map.of(
                        "run_metadata", runRef,
                        "agent_execution_outcome", Map.of(
                                "status", "RECOVERABLE_ADAPTER_FAILURE",
                                "primaryReason", "permission_blocked",
                                "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                                "retryPolicy", "MANUAL_ONLY",
                                "operatorMessage", "WebSearch requires approval."
                        )));
        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.RUNNING, "first", "unused"));

        assertThrows(Exception.class,
                () -> service.approvePublicWebSearch(tempDir, "ws-websearch", "public lookup", false));
        var response = service.approvePublicWebSearch(tempDir, "ws-websearch", "public lookup", true);

        assertEquals("recovery.approve_public_websearch", response.get("action"));
        var approved = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        assertEquals(NodeStatus.READY, approved.status());
        assertTrue(approved.result().containsKey("permission_approval"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("public_websearch_approved"));
    }

    @Test
    void networkApprovalUsesLedgerWithoutRunningOrChangingGateDecisions() throws Exception {
        var definition = new WorkflowDefinition("custom", 1, "verify.tests", List.of(
                new NodeDefinition("verify.tests", NodeKind.TASK, "acpx.agent_run", Map.of("agent", "claude", "permission_profile", "implement"))), List.of());
        var paths = createWorkstream("ws-network", definition);
        var ledger = new WorkflowFileLedger();
        var node = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, node, NodeStatus.FAILED, "failed", List.of(), Map.of());
        var before = Files.readString(paths.workflowCurrentJson());
        var service = new WorkstreamActionService(ledger, () -> command -> { throw new AssertionError("approval must not start execution"); });
        var result = service.networkAuthority(tempDir, "ws-network", "verify.tests", List.of("packages.example.org"), "approved test prerequisites", false);
        assertEquals(true, result.get("canCancel"));
        assertEquals(before, Files.readString(paths.workflowCurrentJson()));
        assertThrows(Exception.class, () -> service.networkAuthority(tempDir, "ws-network", "verify.tests", List.of("*.example.org"), "invalid", false));
        var cancelled = service.networkAuthority(tempDir, "ws-network", null, null, null, true);
        assertEquals(false, cancelled.get("canCancel"));
        assertEquals(before, Files.readString(paths.workflowCurrentJson()));
    }

    @Test
    void approveCompletesTerminalHumanGateWithoutApprovedEdge() throws Exception {
        var paths = createWorkstream("ws-terminal-approve", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.COMPLETED, "", "unused"));
        var response = service.approve(tempDir, "ws-terminal-approve", "approved by test", false, 1);

        assertEquals("review.approve", response.get("action"));
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, current.status());
        assertEquals(List.of(), current.activeNodeInstanceIds());
    }

    @Test
    void approveAndRunReturnsCompletedWithoutResumingTerminalHumanGate() throws Exception {
        var paths = createWorkstream("ws-terminal-approve-run", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(paths);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command -> {
            throw new AssertionError("terminal approval must not resume a completed workflow");
        });
        var response = service.approve(tempDir, "ws-terminal-approve-run", "approved by test", true, 1);

        assertEquals("review.approve.run", response.get("action"));
        assertEquals("COMPLETED", response.get("status"));
        assertEquals("boundary", response.get("stoppedBecause"));
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, current.status());
        assertEquals(List.of(), current.activeNodeInstanceIds());
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
    void retryPreservesContractDiagnosticThroughSharedLedgerWithoutRunningProvider() throws Exception {
        var definition = new WorkflowDefinition("retry-diagnostic", 1, "author",
                List.of(new NodeDefinition("author", NodeKind.TASK, "acpx.agent_run", Map.of())), List.of());
        var paths = createWorkstream("ws-api-retry-diagnostic", definition);
        var ledger = new WorkflowFileLedger();
        var id = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, id, NodeStatus.FAILED, "AGENT_OUTPUT_CONTRACT_VIOLATION", List.of(),
                Map.of("reason", "AGENT_OUTPUT_CONTRACT_VIOLATION", "parse_error",
                        Map.of("code", "SCOPE_INVALID", "message", "Outside scope", "details", Map.of())));
        var before = ledger.loadNode(paths, id);
        var service = new WorkstreamActionService(new WorkflowFileLedger(), () -> command -> {
            throw new AssertionError("retry must not itself launch a provider");
        });
        service.retry(tempDir, paths.workstreamId().value(), "Retry the same task");
        var after = ledger.loadNode(paths, id);
        assertEquals(NodeStatus.READY, after.status());
        assertEquals(before.inputRefs(), after.inputRefs());
        assertEquals(before.iterationId(), after.iterationId());
        assertTrue(((Map<?, ?>) after.result().get("retry_failure_context"))
                .get("diagnostic_text").toString().contains("SCOPE_INVALID"));
        assertFalse(after.result().containsKey("permission_approval"));
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
