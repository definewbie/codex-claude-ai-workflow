package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.EdgeCondition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.TransitionMetadata;
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
        ledger = LedgerTestFixture.ledger();
    }

    @Test
    void parallelFanOut_activatesMultipleNodes() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-fanout"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithParallelEdges();
        LedgerTestFixture.initialize(ledger, paths, "inst-001", def);

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
        var firstBranch = ledger.loadNode(paths, nodeIds.get(0));
        assertTrue(firstBranch.inputTransition().toString().contains("branch_group_id=parallel-review"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("parallel-review"));
    }

    @Test
    void parallelFanOut_completesOneNode_otherRemainsActive() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-fanout2"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithParallelEdges();
        LedgerTestFixture.initialize(ledger, paths, "inst-002", def);

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
        LedgerTestFixture.initialize(ledger, paths, "inst-003", def);

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

    @Test
    void joinAllTerminalWaitsForAllBranchNodes() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-join"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithJoin();
        LedgerTestFixture.initialize(ledger, paths, "inst-join", def);

        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());
        var current = ledger.loadCurrent(paths);
        var nodeB = nodeInstanceIdByDefinition(paths, current.activeNodeInstanceIds(), "node_b");
        var nodeC = nodeInstanceIdByDefinition(paths, current.activeNodeInstanceIds(), "node_c");

        ledger.completeAndRoute(paths, def, nodeB, NodeStatus.COMPLETED, "DONE", List.of(), Map.of());

        current = ledger.loadCurrent(paths);
        assertEquals(List.of(nodeC), current.activeNodeInstanceIds(), "join should wait for the remaining branch");
        assertTrue(Files.readString(paths.eventsJsonl()).contains("join_deferred"));

        ledger.completeAndRoute(paths, def, nodeC, NodeStatus.COMPLETED, "DONE", List.of(), Map.of());

        current = ledger.loadCurrent(paths);
        assertEquals(1, current.activeNodeInstanceIds().size());
        assertEquals("node_join", ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId());
    }

    @Test
    void branchGroupCarriesAcrossIntermediateBranchNodesBeforeJoin() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-join-chain"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithBranchChainJoin();
        LedgerTestFixture.initialize(ledger, paths, "inst-join-chain", def);

        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());
        var current = ledger.loadCurrent(paths);
        var nodeB = nodeInstanceIdByDefinition(paths, current.activeNodeInstanceIds(), "node_b");
        var nodeC = nodeInstanceIdByDefinition(paths, current.activeNodeInstanceIds(), "node_c");

        ledger.completeAndRoute(paths, def, nodeB, NodeStatus.COMPLETED, "DONE", List.of(), Map.of());

        current = ledger.loadCurrent(paths);
        var nodeB2 = nodeInstanceIdByDefinition(paths, current.activeNodeInstanceIds(), "node_b2");
        assertTrue(ledger.loadNode(paths, nodeB2).inputTransition().toString().contains("branch_group_id=parallel-review"));

        ledger.completeAndRoute(paths, def, nodeB2, NodeStatus.COMPLETED, "DONE", List.of(), Map.of());

        current = ledger.loadCurrent(paths);
        assertEquals(List.of(nodeC), current.activeNodeInstanceIds(),
                "join should still wait for node_c even when node_b passed through an intermediate node");
    }

    private String firstActiveNodeId(WorkstreamPaths paths) throws IOException {
        return ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
    }

    private String nodeInstanceIdByDefinition(WorkstreamPaths paths, List<String> nodeIds, String nodeDefinitionId) {
        return nodeIds.stream()
                .filter(id -> {
                    try {
                        return nodeDefinitionId.equals(ledger.loadNode(paths, id).nodeDefinitionId());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .findFirst()
                .orElseThrow();
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
                        new EdgeDefinition("node_a", "DONE", "node_b", EdgeCondition.unconditional(),
                                TransitionMetadata.fork("parallel-review", true, "first review branch")),
                        new EdgeDefinition("node_a", "DONE", "node_c", EdgeCondition.unconditional(),
                                TransitionMetadata.fork("parallel-review", true, "second review branch"))
                )
        );
    }

    private static WorkflowDefinition definitionWithJoin() {
        return new WorkflowDefinition("join-test", 1, "node_a",
                List.of(
                        new NodeDefinition("node_a", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_b", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_c", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_join", NodeKind.TASK, "test", Map.of())
                ),
                List.of(
                        new EdgeDefinition("node_a", "DONE", "node_b", EdgeCondition.unconditional(),
                                TransitionMetadata.fork("parallel-review", true, "first review branch")),
                        new EdgeDefinition("node_a", "DONE", "node_c", EdgeCondition.unconditional(),
                                TransitionMetadata.fork("parallel-review", true, "second review branch")),
                        new EdgeDefinition("node_b", "DONE", "node_join", EdgeCondition.unconditional(),
                                TransitionMetadata.join("parallel-review", "all_terminal", "join review evidence")),
                        new EdgeDefinition("node_c", "DONE", "node_join", EdgeCondition.unconditional(),
                                TransitionMetadata.join("parallel-review", "all_terminal", "join review evidence"))
                )
        );
    }

    private static WorkflowDefinition definitionWithBranchChainJoin() {
        return new WorkflowDefinition("join-chain-test", 1, "node_a",
                List.of(
                        new NodeDefinition("node_a", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_b", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_b2", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_c", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_join", NodeKind.TASK, "test", Map.of())
                ),
                List.of(
                        new EdgeDefinition("node_a", "DONE", "node_b", EdgeCondition.unconditional(),
                                TransitionMetadata.fork("parallel-review", true, "first review branch")),
                        new EdgeDefinition("node_a", "DONE", "node_c", EdgeCondition.unconditional(),
                                TransitionMetadata.fork("parallel-review", true, "second review branch")),
                        new EdgeDefinition("node_b", "DONE", "node_b2"),
                        new EdgeDefinition("node_b2", "DONE", "node_join", EdgeCondition.unconditional(),
                                TransitionMetadata.join("parallel-review", "all_terminal", "join review evidence")),
                        new EdgeDefinition("node_c", "DONE", "node_join", EdgeCondition.unconditional(),
                                TransitionMetadata.join("parallel-review", "all_terminal", "join review evidence"))
                )
        );
    }
}
