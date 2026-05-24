package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowFileLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesWorkflowLedgerAndCurrentSnapshot() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-test"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-test", definition);

        assertTrue(Files.exists(paths.workflowDefinitionJson()));
        assertTrue(Files.exists(paths.workflowInstanceJson()));
        assertTrue(Files.exists(paths.workflowCurrentJson()));
        assertEquals(1, Files.list(paths.nodesDir()).count());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("workflow_started"));

        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.RUNNING, current.status());
        assertEquals(List.of("node-intake-claude_brainstorm-001"), current.activeNodeInstanceIds());
        assertTrue(current.openGateIds().isEmpty());

        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals(NodeStatus.READY, node.status());
        assertEquals("intake.claude_brainstorm", node.nodeDefinitionId());
        assertEquals("iter-001", node.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-001")).contains("\"triggerAction\" : \"workflow_started\""));
    }

    @Test
    void completesNodeAndActivatesNextNodeByOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-route"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-route", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of("first-output.md"), Map.of("ok", true));

        var next = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, next.status());
        assertEquals(1, next.activeNodeInstanceIds().size());
        assertEquals(List.of("gate-" + next.activeNodeInstanceIds().get(0)), next.openGateIds());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_routed"));

        var completed = ledger.loadNode(paths, node.nodeInstanceId());
        assertEquals(NodeStatus.COMPLETED, completed.status());
        assertEquals(List.of("first-output.md"), completed.outputRefs());
        assertEquals(Map.of("ok", true), completed.result());
        assertTrue(completed.finishedAt() != null);

        var nextNode = ledger.loadNode(paths, next.activeNodeInstanceIds().get(0));
        assertEquals("second", nextNode.nodeDefinitionId());
        assertEquals(NodeKind.GATE, nextNode.kind());
        assertEquals(NodeStatus.WAITING, nextNode.status());
        assertEquals("iter-001", nextNode.iterationId());
        assertEquals(List.of("first-output.md"), nextNode.inputRefs());
        assertTrue(Files.exists(paths.gateJson("gate-" + nextNode.nodeInstanceId())));
    }

    @Test
    void revisionOutcomeStartsNextWorkflowIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-revision"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "gate.review",
                List.of(
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("gate.review", "revise", "design.claude_design"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-revision", definition);
        var current = ledger.loadCurrent(paths);
        var gate = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, gate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "human_action", "revise",
                "note", "Narrow the design before implementation."
        ));

        var routed = ledger.loadCurrent(paths);
        var next = ledger.loadNode(paths, routed.activeNodeInstanceIds().get(0));
        assertEquals("design.claude_design", next.nodeDefinitionId());
        assertEquals("iter-002", next.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"parentIterationId\" : \"iter-001\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"triggerAction\" : \"revise\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"targetNodeDefinitionId\" : \"design.claude_design\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("Narrow the design before implementation."));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("iteration_started"));
    }

    @Test
    void rejectDecisionStartsNextWorkflowIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-reject"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "acceptance.decision",
                List.of(
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of()),
                        new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of())
                ),
                List.of(new EdgeDefinition("acceptance.decision", "REJECT", "context.implement_claude"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-reject", definition);
        var current = ledger.loadCurrent(paths);
        var decision = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.COMPLETED, "REJECT", List.of(), Map.of(
                "decision", "REJECT",
                "summary", "Acceptance evidence is incomplete."
        ));

        var routed = ledger.loadCurrent(paths);
        var next = ledger.loadNode(paths, routed.activeNodeInstanceIds().get(0));
        assertEquals("context.implement_claude", next.nodeDefinitionId());
        assertEquals("iter-002", next.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"triggerAction\" : \"REJECT\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("Acceptance evidence is incomplete."));
    }

    @Test
    void completesWorkflowWhenOutcomeHasNoEdge() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-complete"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "only",
                List.of(new NodeDefinition("only", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-complete", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var completed = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, completed.status());
        assertTrue(completed.activeNodeInstanceIds().isEmpty());
        assertTrue(completed.openGateIds().isEmpty());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("workflow_completed"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains(completed.lastEventId()));
    }

    @Test
    void failedNodeStopsWorkflowWithoutRoutingNextNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-failed"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-failed", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "completed", List.of(), Map.of("exit_code", 4));

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.FAILED, stopped.status());
        assertEquals(List.of(node.nodeInstanceId()), stopped.activeNodeInstanceIds());
        assertTrue(stopped.openGateIds().isEmpty());
        assertFalse(Files.exists(paths.nodeJson("node-second-002")));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_failed"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains(stopped.lastEventId()));
    }

    @Test
    void retryActiveFailedNodeSetsItReadyAgain() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-retry"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-retry", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "RECOVERABLE_AGENT_RECONNECT", List.of("stderr.log"), Map.of("recoverable", true));

        ledger.retryActiveNode(paths, "session reconnected");

        var retryCurrent = ledger.loadCurrent(paths);
        var retryNode = ledger.loadNode(paths, retryCurrent.activeNodeInstanceIds().get(0));
        assertEquals(WorkflowStatus.RUNNING, retryCurrent.status());
        assertEquals(NodeStatus.READY, retryNode.status());
        assertEquals("iter-001", retryNode.iterationId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));
        assertTrue(retryNode.outputRefs().isEmpty());
        assertEquals("FAILED", retryNode.result().get("retry_of_status"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_retry_requested"));
    }

    @Test
    void retryRejectsNonRetryableActiveNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-retry-ready"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-retry-ready", definition);

        var error = assertThrows(java.io.IOException.class, () -> ledger.retryActiveNode(paths, "not failed"));

        assertTrue(error.getMessage().contains("not retryable"));
    }

    @Test
    void blockedNodeStopsWorkflowWithoutRoutingNextNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-blocked"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-blocked", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.BLOCKED, "completed", List.of(), Map.of());

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.BLOCKED, stopped.status());
        assertEquals(List.of(node.nodeInstanceId()), stopped.activeNodeInstanceIds());
        assertFalse(Files.exists(paths.nodeJson("node-second-002")));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_blocked"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains(stopped.lastEventId()));
    }

    @Test
    void rejectsDuplicateInitialization() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-duplicate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-duplicate", definition);

        var error = assertThrows(java.io.IOException.class, () -> ledger.initialize(paths, "wf-duplicate", definition));
        assertTrue(error.getMessage().contains("already initialized"));
        var events = Files.readString(paths.eventsJsonl());
        assertEquals(events.indexOf("evt-000001"), events.lastIndexOf("evt-000001"));
    }

    @Test
    void documentRecordsRejectInvalidIdentityAndStatus() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowCurrentDocument(
                0,
                "ws",
                "wf",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of("node"),
                List.of(),
                "evt-000001",
                java.time.Instant.now()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstanceDocument(
                1,
                "wf",
                "agent-lifecycle",
                1,
                null,
                List.of("node"),
                "evt-000001",
                java.time.Instant.now(),
                java.time.Instant.now()
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstanceDocument(
                1,
                "node",
                "first",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.READY,
                "",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new GateDocument(
                1,
                "gate",
                "node",
                "human.review",
                "open",
                List.of("approve"),
                null,
                null,
                Map.of()
        ));
    }
}
