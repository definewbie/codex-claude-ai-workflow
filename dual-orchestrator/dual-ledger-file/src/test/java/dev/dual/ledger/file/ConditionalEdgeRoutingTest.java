package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeCondition;
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

class ConditionalEdgeRoutingTest {
    @TempDir
    Path tempDir;
    private WorkflowFileLedger ledger;
    private WorkstreamPaths paths;

    @BeforeEach
    void setup() throws IOException {
        ledger = LedgerTestFixture.ledger();
    }

    @Test
    void routesToMatchingConditionalEdge() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-cond"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithConditionalEdges();
        LedgerTestFixture.initialize(ledger, paths, "inst-001", def);

        // Complete the task node — result contains mode=strict
        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of("mode", "strict"));

        var current = ledger.loadCurrent(paths);
        var nextNode = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("node_strict", nextNode.nodeDefinitionId());
    }

    @Test
    void skipsEdgeWhenConditionFails() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-cond2"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithConditionalEdges();
        LedgerTestFixture.initialize(ledger, paths, "inst-002", def);

        // Complete with mode=relaxed — should route to node_relaxed
        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of("mode", "relaxed"));

        var current = ledger.loadCurrent(paths);
        var nextNode = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("node_relaxed", nextNode.nodeDefinitionId());
    }

    @Test
    void unconditionalEdgeMatchesAlways() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-uncond"));
        Files.createDirectories(paths.workstreamDir());
        var def = definitionWithUnconditionalEdge();
        LedgerTestFixture.initialize(ledger, paths, "inst-003", def);

        // No condition — should always route to node_b
        ledger.completeAndRoute(paths, def, firstActiveNodeId(paths), NodeStatus.COMPLETED, "DONE",
                List.of(), Map.of());

        var current = ledger.loadCurrent(paths);
        var nextNode = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("node_b", nextNode.nodeDefinitionId());
    }

    private String firstActiveNodeId(WorkstreamPaths paths) throws IOException {
        return ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
    }

    private static WorkflowDefinition definitionWithConditionalEdges() {
        return new WorkflowDefinition("conditional-test", 1, "node_a",
                List.of(
                        new NodeDefinition("node_a", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_strict", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_relaxed", NodeKind.TASK, "test", Map.of())
                ),
                List.of(
                        new EdgeDefinition("node_a", "DONE", "node_strict", EdgeCondition.configEquals("mode", "strict")),
                        new EdgeDefinition("node_a", "DONE", "node_relaxed", EdgeCondition.configEquals("mode", "relaxed"))
                )
        );
    }

    private static WorkflowDefinition definitionWithUnconditionalEdge() {
        return new WorkflowDefinition("unconditional-test", 1, "node_a",
                List.of(
                        new NodeDefinition("node_a", NodeKind.TASK, "test", Map.of()),
                        new NodeDefinition("node_b", NodeKind.TASK, "test", Map.of())
                ),
                List.of(
                        new EdgeDefinition("node_a", "DONE", "node_b")
                )
        );
    }
}
