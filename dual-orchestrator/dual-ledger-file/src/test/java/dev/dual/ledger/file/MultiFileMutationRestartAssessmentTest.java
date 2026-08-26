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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiFileMutationRestartAssessmentTest {
    @TempDir
    Path tempDir;

    @Test
    void initializePrefixesCannotBeReplayedOrLoadedAsOneConsistentGeneration() throws Exception {
        var eventOnly = paths("initialize-event-only");
        blockAtomicWrite(eventOnly.workflowDefinitionJson(), () -> new WorkflowFileLedger().initialize(
                eventOnly, "wf-initialize-event-only", singleTaskDefinition()));

        var eventOnlyRestart = new WorkflowFileLedger();
        assertThrows(IOException.class, () -> eventOnlyRestart.loadDefinition(eventOnly));
        assertThrows(IOException.class, () -> eventOnlyRestart.initialize(
                eventOnly, "wf-initialize-event-only", singleTaskDefinition()));

        var currentWithoutEntry = paths("initialize-current-without-entry");
        var entryNodeId = LedgerUtils.nodeInstanceId("first", 1);
        blockAtomicWrite(currentWithoutEntry.nodeJson(entryNodeId), () -> new WorkflowFileLedger().initialize(
                currentWithoutEntry, "wf-initialize-current-without-entry", singleTaskDefinition()));

        var missingEntryRestart = new WorkflowFileLedger();
        assertEquals(List.of(entryNodeId), missingEntryRestart.loadCurrent(currentWithoutEntry).activeNodeInstanceIds());
        assertThrows(IOException.class, () -> missingEntryRestart.loadNode(currentWithoutEntry, entryNodeId));
        assertThrows(IOException.class, () -> missingEntryRestart.initialize(
                currentWithoutEntry, "wf-initialize-current-without-entry", singleTaskDefinition()));
    }

    @Test
    void resolvedGatePrefixesLoseReplayEligibilityBeforeTheTransitionCommits() throws Exception {
        var resolvedOnly = gateFixture("gate-resolved-only");
        var gateNodeId = resolvedOnly.ledger().loadCurrent(resolvedOnly.paths()).activeNodeInstanceIds().getFirst();
        blockAtomicWrite(resolvedOnly.paths().nodeJson(gateNodeId), () -> resolve(resolvedOnly));
        assertGateReplayRejected(resolvedOnly);

        var nodeCompleted = gateFixture("gate-node-completed");
        blockAtomicWrite(nodeCompleted.paths().nodeJson(LedgerUtils.nodeInstanceId("after", 3)),
                () -> resolve(nodeCompleted));
        assertGateReplayRejected(nodeCompleted);

        var successorWritten = gateFixture("gate-successor-written");
        blockEventAppend(successorWritten.paths(), () -> resolve(successorWritten));
        assertTrue(Files.isRegularFile(successorWritten.paths().nodeJson(LedgerUtils.nodeInstanceId("after", 3))));
        assertGateReplayRejected(successorWritten);

        var currentCommitted = gateFixture("gate-current-without-instance");
        var beforeInstance = readInstance(currentCommitted.paths());
        blockAtomicWrite(currentCommitted.paths().workflowInstanceJson(), () -> resolve(currentCommitted));
        var restart = new WorkflowFileLedger();
        var current = restart.loadCurrent(currentCommitted.paths());
        var instance = readInstance(currentCommitted.paths());
        assertEquals("after", restart.loadNode(currentCommitted.paths(), current.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
        assertEquals(beforeInstance.lastEventId(), instance.lastEventId());
        assertNotEquals(current.lastEventId(), instance.lastEventId());
        assertThrows(IOException.class, () -> resolve(new GateFixture(restart, currentCommitted.paths(), currentCommitted.definition())));
    }

    @Test
    void staleRecoveryPrefixesLoseReplayEligibilityWhenTheNodeWriteWins() throws Exception {
        var failedOnly = staleFixture("stale-failed-only");
        blockAtomicWrite(failedOnly.paths().gateJson("recovery-" + failedOnly.nodeId()), () -> recover(failedOnly));
        assertStaleReplayRejected(failedOnly);

        var gateWritten = staleFixture("stale-gate-written");
        blockEventAppend(gateWritten.paths(), () -> recover(gateWritten));
        assertTrue(Files.isRegularFile(gateWritten.paths().gateJson("recovery-" + gateWritten.nodeId())));
        assertStaleReplayRejected(gateWritten);

        var eventWritten = staleFixture("stale-event-written");
        blockAtomicWrite(eventWritten.paths().workflowCurrentJson(), () -> recover(eventWritten));
        assertTrue(Files.readString(eventWritten.paths().eventsJsonl()).contains("node_stale_running_recovered"));
        assertStaleReplayRejected(eventWritten);

        var currentCommitted = staleFixture("stale-current-without-instance");
        var beforeInstance = readInstance(currentCommitted.paths());
        blockAtomicWrite(currentCommitted.paths().workflowInstanceJson(), () -> recover(currentCommitted));
        var restart = new WorkflowFileLedger();
        var current = restart.loadCurrent(currentCommitted.paths());
        var instance = readInstance(currentCommitted.paths());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(beforeInstance.lastEventId(), instance.lastEventId());
        assertNotEquals(current.lastEventId(), instance.lastEventId());
        assertThrows(IOException.class, () -> restart.recoverStaleRunningNode(currentCommitted.paths(), "stale"));
    }

    @Test
    void normalRoutingRecoversEarlyPrefixesButDuplicatesAnAlreadyAppendedEvent() throws Exception {
        var terminalOnly = routingFixture("route-terminal-only");
        blockAtomicWrite(terminalOnly.paths().nodeJson(LedgerUtils.nodeInstanceId("review", 2)),
                () -> route(terminalOnly));
        route(new RestartFixture(new WorkflowFileLedger(), terminalOnly.paths(), terminalOnly.definition()));
        assertConsistentGateState(terminalOnly.paths());

        var successorWritten = routingFixture("route-successor-written");
        blockEventAppend(successorWritten.paths(), () -> route(successorWritten));
        route(new RestartFixture(new WorkflowFileLedger(), successorWritten.paths(), successorWritten.definition()));
        assertConsistentGateState(successorWritten.paths());

        var eventWritten = routingFixture("route-event-written");
        blockAtomicWrite(eventWritten.paths().workflowCurrentJson(), () -> route(eventWritten));
        route(new RestartFixture(new WorkflowFileLedger(), eventWritten.paths(), eventWritten.definition()));
        assertEquals(2, eventCount(eventWritten.paths(), "evt-000002"));

        var currentCommitted = routingFixture("route-current-without-instance");
        var beforeInstance = readInstance(currentCommitted.paths());
        blockAtomicWrite(currentCommitted.paths().workflowInstanceJson(), () -> route(currentCommitted));
        var restart = new WorkflowFileLedger();
        var current = restart.loadCurrent(currentCommitted.paths());
        var instance = readInstance(currentCommitted.paths());
        assertNotEquals(current.lastEventId(), instance.lastEventId());
        assertEquals(beforeInstance.lastEventId(), instance.lastEventId());
        assertThrows(IOException.class, () -> route(
                new RestartFixture(restart, currentCommitted.paths(), currentCommitted.definition())));
    }

    private void assertGateReplayRejected(GateFixture fixture) throws Exception {
        var restart = new WorkflowFileLedger();
        var current = restart.loadCurrent(fixture.paths());
        assertEquals("review", restart.loadNode(fixture.paths(), current.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
        assertThrows(IOException.class, () -> resolve(new GateFixture(restart, fixture.paths(), fixture.definition())));
    }

    private void assertStaleReplayRejected(StaleFixture fixture) throws Exception {
        var restart = new WorkflowFileLedger();
        assertEquals(NodeStatus.FAILED, restart.loadNode(fixture.paths(), fixture.nodeId()).status());
        assertThrows(IOException.class, () -> restart.recoverStaleRunningNode(fixture.paths(), "stale"));
    }

    private void assertConsistentGateState(WorkstreamPaths paths) throws Exception {
        var restart = new WorkflowFileLedger();
        var current = restart.loadCurrent(paths);
        var active = restart.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var instance = readInstance(paths);
        assertEquals("review", active.nodeDefinitionId());
        assertEquals(current.lastEventId(), instance.lastEventId());
        assertEquals(current.activeNodeInstanceIds(), instance.activeNodeInstanceIds());
        assertEquals("open", restart.loadOpenGateForNode(paths, active.nodeInstanceId()).status());
        assertEquals(1, eventCount(paths, "evt-000002"));
    }

    private void resolve(GateFixture fixture) throws IOException {
        fixture.ledger().resolveOpenGate(fixture.paths(), fixture.definition(), "approve", "ok");
    }

    private void recover(StaleFixture fixture) throws IOException {
        fixture.ledger().recoverStaleRunningNode(fixture.paths(), "stale");
    }

    private void route(RestartFixture fixture) throws IOException {
        fixture.ledger().completeAndRoute(fixture.paths(), fixture.definition(), LedgerUtils.nodeInstanceId("first", 1),
                NodeStatus.COMPLETED, "done", List.of(), Map.of());
    }

    private GateFixture gateFixture(String id) throws Exception {
        var paths = paths(id);
        var definition = new WorkflowDefinition(
                "assessment-gate", 1, "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("review", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("after", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("first", "done", "review"),
                        new EdgeDefinition("review", "approved", "after")
                )
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + id, definition);
        ledger.completeAndRoute(paths, definition, LedgerUtils.nodeInstanceId("first", 1),
                NodeStatus.COMPLETED, "done", List.of(), Map.of());
        return new GateFixture(ledger, paths, definition);
    }

    private StaleFixture staleFixture(String id) throws Exception {
        var paths = paths(id);
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + id, singleTaskDefinition());
        var nodeId = LedgerUtils.nodeInstanceId("first", 1);
        ledger.markRunning(paths, nodeId, Long.MAX_VALUE);
        return new StaleFixture(ledger, paths, nodeId);
    }

    private RestartFixture routingFixture(String id) throws Exception {
        var paths = paths(id);
        var definition = new WorkflowDefinition(
                "assessment-routing", 1, "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("review", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "done", "review"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + id, definition);
        return new RestartFixture(ledger, paths, definition);
    }

    private WorkflowDefinition singleTaskDefinition() {
        return new WorkflowDefinition(
                "assessment-single", 1, "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
    }

    private WorkstreamPaths paths(String id) throws IOException {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-" + id));
        Files.createDirectories(paths.workstreamDir());
        return paths;
    }

    private void blockAtomicWrite(Path target, IoAction action) throws Exception {
        var tempTarget = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(tempTarget);
        try {
            assertThrows(IOException.class, action::run);
        } finally {
            Files.deleteIfExists(tempTarget);
        }
    }

    private void blockEventAppend(WorkstreamPaths paths, IoAction action) throws Exception {
        var eventBytes = Files.readAllBytes(paths.eventsJsonl());
        Files.delete(paths.eventsJsonl());
        Files.createDirectory(paths.eventsJsonl());
        try {
            assertThrows(IOException.class, action::run);
        } finally {
            Files.deleteIfExists(paths.eventsJsonl());
            Files.write(paths.eventsJsonl(), eventBytes);
        }
    }

    private WorkflowInstanceDocument readInstance(WorkstreamPaths paths) throws IOException {
        return new AtomicJsonWriter().objectMapper().readValue(
                paths.workflowInstanceJson().toFile(), WorkflowInstanceDocument.class);
    }

    private int eventCount(WorkstreamPaths paths, String eventId) throws IOException {
        var mapper = new AtomicJsonWriter().objectMapper();
        var count = 0;
        for (var line : Files.readAllLines(paths.eventsJsonl())) {
            if (eventId.equals(mapper.readTree(line).path("event_id").asText())) {
                count++;
            }
        }
        return count;
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws Exception;
    }

    private record RestartFixture(WorkflowFileLedger ledger, WorkstreamPaths paths, WorkflowDefinition definition) {
    }

    private record GateFixture(WorkflowFileLedger ledger, WorkstreamPaths paths, WorkflowDefinition definition) {
    }

    private record StaleFixture(WorkflowFileLedger ledger, WorkstreamPaths paths, String nodeId) {
    }
}
