package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A decision request carries the gate occurrence the decider actually looked at. When that occurrence is no longer
 * the open gate, the request is refused before any write; it must never resolve whichever gate happens to be open now.
 */
class GateDecisionSubjectBindingTest {
    @TempDir
    Path tempDir;

    @Test
    void requestNamingAGateThatIsNoLongerOpenIsRefusedAndWritesNothing() throws Exception {
        var fixture = twoGatesAdvancedToSecondGate("ws-stale");
        var ledger = fixture.ledger();
        var before = ledger.loadCurrent(fixture.paths());

        var error = assertThrows(IOException.class, () -> ledger.resolveOpenGate(fixture.paths(), fixture.definition(),
                "approved", "approve the gate I was looking at", fixture.firstGate()));

        assertTrue(error.getMessage().contains("no longer the open gate"), error.getMessage());
        assertTrue(error.getMessage().contains(fixture.firstGate()), error.getMessage());
        var after = ledger.loadCurrent(fixture.paths());
        assertEquals(before, after, "a refused decision must not change current");
        assertEquals("open", ledger.loadOpenGateForNode(fixture.paths(), fixture.secondGate()).status());
    }

    @Test
    void requestNamingTheOpenGateResolvesIt() throws Exception {
        var fixture = twoGatesAdvancedToSecondGate("ws-current");
        var ledger = fixture.ledger();

        ledger.resolveOpenGate(fixture.paths(), fixture.definition(), "approved", "approve the gate I am looking at",
                fixture.secondGate());

        var after = ledger.loadCurrent(fixture.paths());
        assertEquals("after", ledger.loadNode(fixture.paths(), after.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
    }

    @Test
    void requestWithoutASubjectKeepsItsPreviousBehaviour() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-legacy"));
        Files.createDirectories(paths.workstreamDir());
        var definition = definition();
        var ledger = LedgerTestFixture.ledger();
        LedgerTestFixture.initialize(ledger, paths, "wf-legacy", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        ledger.resolveOpenGate(paths, definition, "approved", "legacy caller");

        var after = ledger.loadCurrent(paths);
        assertEquals("between", ledger.loadNode(paths, after.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
    }

    @Test
    void blankSubjectIsTreatedAsUnboundRatherThanMatchingNothing() throws Exception {
        var fixture = twoGatesAdvancedToSecondGate("ws-blank");
        var ledger = fixture.ledger();

        ledger.resolveOpenGate(fixture.paths(), fixture.definition(), "approved", "blank subject", " ");

        var after = ledger.loadCurrent(fixture.paths());
        assertEquals("after", ledger.loadNode(fixture.paths(), after.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
    }

    private Fixture twoGatesAdvancedToSecondGate(String workstream) throws IOException {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstream));
        Files.createDirectories(paths.workstreamDir());
        var definition = definition();
        var ledger = LedgerTestFixture.ledger();
        LedgerTestFixture.initialize(ledger, paths, "wf-" + workstream, definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var firstGate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.resolveOpenGate(paths, definition, "approved", "another session approved the first gate");
        var between = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, between, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var secondGate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        assertEquals("gate.b", ledger.loadNode(paths, secondGate).nodeDefinitionId());
        return new Fixture(ledger, paths, definition, firstGate, secondGate);
    }

    private record Fixture(WorkflowFileLedger ledger, WorkstreamPaths paths, WorkflowDefinition definition,
                           String firstGate, String secondGate) {}

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
