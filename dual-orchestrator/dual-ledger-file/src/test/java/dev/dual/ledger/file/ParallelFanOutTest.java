package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelFanOutTest {
    @TempDir
    Path tempDir;
    private WorkflowFileLedger ledger;

    @BeforeEach
    void setup() {
        ledger = new WorkflowFileLedger();
    }

    @Test
    void parallelFanOut_activatesMultipleNodes() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-fanout"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithParallelEdges();
        ledger.initialize(paths, "inst-001", def);

        // Complete the entry node — should fan out to both node_b and node_c
        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());

        var current = ledger.loadCurrent(paths);
        assertEquals(2, current.activeNodeInstanceIds().size(),
                "fan-out should activate 2 successor nodes");

        var nodeIds = current.activeNodeInstanceIds();
        var defIds = nodeIds.stream()
                .map(id -> {
                    try { return ledger.loadNode(paths, id).nodeDefinitionId(); }
                    catch (IOException e) { throw new RuntimeException(e); }
                })
                .toList();
        assertTrue(defIds.contains("node_b"), "node_b should be active");
        assertTrue(defIds.contains("node_c"), "node_c should be active");
    }

    @Test
    void parallelFanOut_completesOneNode_otherRemainsActive() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-fanout2"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithParallelEdges();
        ledger.initialize(paths, "inst-002", def);

        // Fan out
        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());
        var current = ledger.loadCurrent(paths);
        var firstActive = current.activeNodeInstanceIds().get(0);

        // Complete one of the parallel nodes — other should remain active
        ledger.completeAndRoute(paths, def, firstActive, NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());

        current = ledger.loadCurrent(paths);
        assertEquals(1, current.activeNodeInstanceIds().size(),
                "one parallel node remains active after completing the other");
    }

    @Test
    void parallelFanOut_allComplete_workflowCompleted() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-fanout3"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithParallelEdges();
        ledger.initialize(paths, "inst-003", def);

        // Fan out
        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());
        var current = ledger.loadCurrent(paths);

        // Complete first parallel node
        ledger.completeAndRoute(paths, def, current.activeNodeInstanceIds().get(0),
                NodeStatus.COMPLETED, "DONE", List.of(), Map.of());
        current = ledger.loadCurrent(paths);

        // Complete second parallel node (no outgoing edges → workflow completes)
        ledger.completeAndRoute(paths, def, current.activeNodeInstanceIds().get(0),
                NodeStatus.COMPLETED, "DONE", List.of(), Map.of());

        current = ledger.loadCurrent(paths);
        assertEquals(dev.dual.core.workflow.WorkflowStatus.COMPLETED, current.status());
        assertTrue(current.activeNodeInstanceIds().isEmpty());
    }

    private String firstActiveNodeId(WorkstreamPaths paths) throws IOException {
        return ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
    }

    private static WorkflowDefinition definitionWithParallelEdges() {
        return new WorkflowDefinition("parallel-test", 1, "node_a",
                List.of(
                        new NodeDefinition("node_a", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_b", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_c", NodeKind.TASK, "test", Map.of())
                ),
                // Two edges from node_a → fan-out to both node_b and node_c
                List.of(
                        new EdgeDefinition("node_a", "DONE", "node_b"),
                        new EdgeDefinition("node_a", "DONE", "node_c")
                )
        );
    }
}
