package dev.dual.ledger.file;

import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Legal local continuations after every durable-write interruption, not historical partial-state rescue. */
class MultiFileMutationRestartAssessmentTest {
    @TempDir Path root;
    private enum Operation { INITIALIZE, ROUTE, DECIDE, RECOVER }
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition("restart", 1, "first", List.of(
            new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
            new NodeDefinition("review", NodeKind.GATE, "human.review", Map.of()),
            new NodeDefinition("after", NodeKind.TASK, "noop.task", Map.of())), List.of(
            new EdgeDefinition("first", "done", "review"), new EdgeDefinition("review", "approved", "after")));

    @Test void initializationPrefixesRecoverOneConsistentGeneration() throws Exception { verify(Operation.INITIALIZE); }
    @Test void routingPrefixesDoNotDuplicateEventsOrExposeMixedGenerations() throws Exception { verify(Operation.ROUTE); }
    @Test void decisionPrefixesFinishWithoutReplayingAHumanDecision() throws Exception { verify(Operation.DECIDE); }
    @Test void staleRecoveryPrefixesFinishWithoutRepeatingRecovery() throws Exception { verify(Operation.RECOVER); }

    private void verify(Operation operation) throws Exception {
        var observed = new ArrayList<String>();
        var baseline = prepare(operation, "baseline-" + operation);
        execute(ledger((point, target) -> observed.add(point)), baseline, operation);
        assertTrue(observed.size() > 5);
        for (int prefix = 0; prefix < observed.size(); prefix++) {
            var paths = prepare(operation, operation + "-" + prefix);
            var calls = new AtomicInteger();
            int failAt = prefix;
            var failing = ledger((point, target) -> {
                if (calls.getAndIncrement() == failAt) throw new IOException("stop at " + point);
            });
            assertThrows(IOException.class, () -> execute(failing, paths, operation));
            var cold = LedgerTestFixture.ledger();
            if (prefix == 0) {
                // No durable intent: only an explicit repeat of the original operation may create it.
                execute(cold, paths, operation);
            } else {
                // A reader may not combine the partially applied before/after generation.
                if (hasPending(paths)) assertThrows(LedgerSession.PendingCommitException.class, () -> cold.loadCurrent(paths));
                try (var completion = cold.writeSession(paths, "complete fixed local write set", "recovery")) {
                    completion.commit();
                }
            }
            assertPostState(cold, paths, operation);
            var events = Files.readAllBytes(paths.eventsJsonl());
            try (var noop = cold.writeSession(paths, "repeat local completion", "recovery-again")) { noop.commit(); }
            assertArrayEquals(events, Files.readAllBytes(paths.eventsJsonl()));
            if (operation == Operation.DECIDE || operation == Operation.RECOVER)
                assertThrows(IOException.class, () -> execute(cold, paths, operation));
        }
    }

    private WorkstreamPaths prepare(Operation operation, String name) throws Exception {
        var paths = new WorkstreamPaths(root.resolve(name), new DualId("ws-" + name));
        var ledger = LedgerTestFixture.ledger();
        if (operation != Operation.INITIALIZE) LedgerTestFixture.initialize(ledger, paths, "wf-" + name, DEFINITION);
        if (operation == Operation.DECIDE) execute(ledger, paths, Operation.ROUTE);
        if (operation == Operation.RECOVER) ledger.markRunning(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(), Long.MAX_VALUE);
        return paths;
    }

    private void execute(WorkflowFileLedger ledger, WorkstreamPaths paths, Operation operation) throws Exception {
        switch (operation) {
            case INITIALIZE -> LedgerTestFixture.initialize(ledger, paths, "wf-" + paths.workstreamId().value(), DEFINITION);
            case ROUTE -> ledger.completeAndRoute(paths, DEFINITION, LedgerUtils.nodeInstanceId("first", 1),
                    NodeStatus.COMPLETED, "done", List.of(), Map.of());
            case DECIDE -> ledger.resolveOpenGate(paths, DEFINITION, "approved", "Continue with this reviewed result");
            case RECOVER -> ledger.recoverStaleRunningNode(paths, "previous process is no longer alive");
        }
    }

    private void assertPostState(WorkflowFileLedger ledger, WorkstreamPaths paths, Operation operation) throws Exception {
        try (var read = ledger.readSession(paths)) {
            var current = read.ledger().loadCurrent(paths);
            var instance = read.files().read(paths.workflowInstanceJson(), WorkflowInstanceDocument.class);
            assertEquals(current.status(), instance.status());
            assertEquals(current.lastEventId(), instance.lastEventId());
            assertEquals(current.activeNodeInstanceIds(), instance.activeNodeInstanceIds());
            var node = read.ledger().loadNode(paths, current.activeNodeInstanceIds().getFirst());
            switch (operation) {
                case INITIALIZE -> { assertEquals("first", node.nodeDefinitionId()); assertEquals(NodeStatus.READY, node.status()); }
                case ROUTE -> { assertEquals("review", node.nodeDefinitionId()); assertEquals("open", read.ledger().loadOpenGateForNode(paths, node.nodeInstanceId()).status()); }
                case DECIDE -> { assertEquals("after", node.nodeDefinitionId()); assertEquals(NodeStatus.READY, node.status()); }
                case RECOVER -> { assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status()); assertEquals(NodeStatus.FAILED, node.status()); }
            }
            var ids = new HashSet<String>();
            for (var line : read.files().readLines(paths.eventsJsonl()))
                assertTrue(ids.add(new AtomicJsonWriter().objectMapper().readTree(line).path("event_id").asText()), "duplicate event");
        }
    }

    private boolean hasPending(WorkstreamPaths paths) throws IOException {
        var directory = paths.workstreamDir().resolve("pending");
        if (!Files.isDirectory(directory)) return false;
        try (var children = Files.list(directory)) { return children.anyMatch(p -> p.toString().endsWith(".json")); }
    }

    private WorkflowFileLedger ledger(LedgerSession.FaultPoint point) {
        return new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), LedgerTestFixture.IDENTITY, point);
    }
}
