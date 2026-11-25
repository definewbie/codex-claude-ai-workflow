package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
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

class TimelineCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void timelinePrintsProjectionSummary() throws Exception {
        var workstreamId = "ws-timeline-cli";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "test-timeline",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-timeline-cli", definition);
        var first = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("first-output.md"), Map.of("ok", true));

        var command = new TimelineCommand();
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
        assertTrue(printed.contains("Timeline"));
        assertTrue(printed.contains("WAITING_FOR_HUMAN"));
        assertTrue(printed.contains("COMPLETED  first"));
        assertTrue(printed.contains("WAITING  second"));
        assertTrue(printed.contains(".agent/workstreams/" + workstreamId + "/timeline.json"));
    }
}
