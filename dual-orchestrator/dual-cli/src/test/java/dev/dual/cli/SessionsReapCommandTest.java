package dev.dual.cli;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionsReapCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void reapsTerminalWorkstreamsAndSkipsNonTerminal() throws Exception {
        // terminal workstream: drive it to COMPLETED
        var donePaths = createWorkstream("ws-done", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(donePaths);
        var entry = ledger.loadCurrent(donePaths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(donePaths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        ledger.resolveOpenGate(donePaths, definition, "complete", "done");
        assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(donePaths).status());

        // non-terminal workstream: left RUNNING after initialize
        createWorkstream("ws-running", simpleDefinition());
        assertEquals(WorkflowStatus.RUNNING,
                new WorkflowFileLedger().loadCurrent(paths("ws-running")).status());

        var reaped = new ArrayList<String>();
        var cmd = new SessionsReapCommand();
        cmd.workdir = tempDir;
        cmd.reap((root, workstreamId) -> {
            reaped.add(workstreamId);
            return new WorkstreamSessionCloser.CloseSummary(workstreamId, 0, 0, 0);
        });

        assertTrue(reaped.contains("ws-done"), "terminal workstream should be reaped");
        assertFalse(reaped.contains("ws-running"), "running workstream must be skipped");
    }

    @Test
    void reapToleratesEmptyWorkdir() throws Exception {
        var cmd = new SessionsReapCommand();
        cmd.workdir = tempDir;
        var reaped = new ArrayList<String>();
        cmd.reap((root, workstreamId) -> {
            reaped.add(workstreamId);
            return new WorkstreamSessionCloser.CloseSummary(workstreamId, 0, 0, 0);
        });
        assertTrue(reaped.isEmpty());
    }

    @Test
    void closeFailuresAggregatedIntoTotal() throws Exception {
        // acpx close failures (summary.failed()) must surface in the reap total as
        // closeFailed=, not be hidden behind a failed=0 total.
        var donePaths = createWorkstream("ws-closefail", completeGateDefinition());
        var ledger = new WorkflowFileLedger();
        var definition = ledger.loadDefinition(donePaths);
        var entry = ledger.loadCurrent(donePaths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(donePaths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        ledger.resolveOpenGate(donePaths, definition, "complete", "done");

        var cmd = new SessionsReapCommand();
        cmd.workdir = tempDir;
        var captured = new java.io.ByteArrayOutputStream();
        var previous = System.out;
        System.setOut(new java.io.PrintStream(captured));
        try {
            cmd.reap((root, workstreamId) ->
                    new WorkstreamSessionCloser.CloseSummary(workstreamId, 1, 0, 3));
        } finally {
            System.setOut(previous);
        }
        var printed = captured.toString();

        assertTrue(printed.contains("reaped=1"), printed);
        assertTrue(printed.contains("closeFailed=3"), printed);
        assertTrue(printed.contains("statusFailed=0"), printed);
    }

    private WorkstreamPaths paths(String workstreamId) {
        return new WorkstreamPaths(tempDir, new DualId(workstreamId));
    }

    private WorkstreamPaths createWorkstream(String workstreamId, WorkflowDefinition definition) throws Exception {
        var paths = paths(workstreamId);
        Files.createDirectories(paths.workstreamDir());
        new WorkflowFileLedger().initialize(paths, "wf-" + workstreamId, definition);
        return paths;
    }

    private static WorkflowDefinition simpleDefinition() {
        return new WorkflowDefinition(
                "reap-workflow",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
    }

    private static WorkflowDefinition completeGateDefinition() {
        return new WorkflowDefinition(
                "reap-workflow",
                1,
                "before.gate",
                List.of(
                        new NodeDefinition("before.gate", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review",
                                Map.of("allowed_actions", List.of("approve", "revise", "complete")))
                ),
                List.of(new EdgeDefinition("before.gate", "completed", "gate.review"))
        );
    }
}
