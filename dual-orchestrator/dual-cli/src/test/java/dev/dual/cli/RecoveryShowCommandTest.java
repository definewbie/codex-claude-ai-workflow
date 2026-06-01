package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryShowCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void showPrintsCurrentFailureSummary() throws Exception {
        var workstreamId = "ws-recovery-show";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "test-recovery-show",
                1,
                "design.claude",
                List.of(new NodeDefinition("design.claude", NodeKind.TASK, "agent.acpx", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-recovery-show", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_RATE_LIMIT",
                List.of(".agent/workstreams/" + workstreamId + "/runs/run-001/outputs/design.stderr.log"),
                Map.of(
                        "exit_code", 4,
                        "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT",
                        "acpx_failure.recoverable", true
                ));

        var command = new RecoveryShowCommand();
        command.workdir = tempDir;
        command.workstreamId = workstreamId;

        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        var printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Current Failure"));
        assertTrue(printed.contains("RECOVERABLE_RATE_LIMIT"));
        assertTrue(printed.contains("design.stderr.log"));
        assertTrue(printed.contains(".agent/workstreams/" + workstreamId + "/recovery/current-failure.md"));
    }
}
