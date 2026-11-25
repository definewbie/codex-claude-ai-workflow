package dev.dual.runtime.handler;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.RuntimeTestLedger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared application entry used by Web, CLI and autorun. A decision names the gate occurrence the decider saw;
 * if the open gate has moved on, the request is refused with no ledger write and nothing is scheduled.
 */
class ReviewGateActionsSubjectBindingTest {
    @TempDir
    Path tempDir;

    @Test
    void staleDecisionThroughApplicationEntryIsRefusedAndLeavesTheLedgerUntouched() throws Exception {
        var f = advancedToSecondGate("ws-app-stale");
        var before = Events.snapshot(f.paths);

        var error = assertThrows(IOException.class, () -> ReviewGateActions.resolve(f.ledger, f.paths, "approved",
                "approve the gate I was looking at", f.firstGate));

        assertTrue(error.getMessage().contains("no longer the open gate"), error.getMessage());
        assertEquals(before, Events.snapshot(f.paths), "a refused decision must append no event and leave gate B open");
        var current = f.ledger.loadCurrent(f.paths);
        assertEquals(List.of(f.secondGate), current.activeNodeInstanceIds());
        assertEquals("open", f.ledger.loadOpenGateForNode(f.paths, f.secondGate).status());
    }

    @Test
    void decisionThroughApplicationEntryNamingTheOpenGateResolvesIt() throws Exception {
        var f = advancedToSecondGate("ws-app-current");

        ReviewGateActions.resolve(f.ledger, f.paths, "approved", "approve the gate I am looking at", f.secondGate);

        var current = f.ledger.loadCurrent(f.paths);
        assertEquals("after", f.ledger.loadNode(f.paths, current.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
        var resolved = f.ledger.loadNode(f.paths, f.secondGate);
        assertEquals(NodeStatus.COMPLETED, resolved.status());
        assertEquals("approved", resolved.result().get("human_action"));
    }

    private Fixture advancedToSecondGate(String workstream) throws IOException {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstream));
        Files.createDirectories(paths.workstreamDir());
        var definition = definition();
        var ledger = RuntimeTestLedger.ledger();
        var workspaceId = new DualId("workspace-" + workstream);
        var root = paths.workspaceRoot();
        ledger.initialize(paths, dev.dual.core.domain.Workstream.created(paths.workstreamId(), "subject binding",
                        new dev.dual.core.domain.Workstream.RepositoryRef(root, root), workspaceId, Instant.now()),
                dev.dual.core.domain.Workspace.primaryGitWorktree(workspaceId, paths.workstreamId(), root, root, "test", "main", "test-base"),
                "Exercise the declared workflow", dev.dual.ledger.file.WorkstreamSettingsDocument.of("auto"),
                "wf-" + workstream, definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var firstGate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ReviewGateActions.resolve(ledger, paths, "approved", "another session approved the first gate", firstGate);
        var between = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, between, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var secondGate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        assertEquals("gate.b", ledger.loadNode(paths, secondGate).nodeDefinitionId());
        return new Fixture(ledger, paths, firstGate, secondGate);
    }

    /** What a wrong decision would change: the appended events and the current pointer. */
    private record Events(long eventCount, Object current) {
        static Events snapshot(WorkstreamPaths paths) throws IOException {
            long count;
            try (var lines = Files.lines(paths.eventsJsonl())) { count = lines.filter(l -> !l.isBlank()).count(); }
            var ledger = RuntimeTestLedger.ledger();
            try (var read = ledger.readSession(paths)) { return new Events(count, read.ledger().loadCurrent(paths)); }
        }
    }

    private record Fixture(dev.dual.ledger.file.WorkflowFileLedger ledger, WorkstreamPaths paths, String firstGate,
                           String secondGate) {}

    private static WorkflowDefinition definition() {
        return new WorkflowDefinition(
                "two-gates",
                1,
                "before",
                List.of(
                        new NodeDefinition("before", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.a", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("between", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.b", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("after", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("before", "completed", "gate.a"),
                        new EdgeDefinition("gate.a", "approved", "between"),
                        new EdgeDefinition("between", "completed", "gate.b"),
                        new EdgeDefinition("gate.b", "approved", "after")
                )
        );
    }
}
