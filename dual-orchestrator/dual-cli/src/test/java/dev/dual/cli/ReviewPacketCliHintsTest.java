package dev.dual.cli;

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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewPacketCliHintsTest {
    @TempDir
    Path tempDir;

    @Test
    void printsReviewShowCommandAndPacketPathWhenWaitingForHumanGate() throws Exception {
        var workstreamId = "ws-review-hints";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "review-hints",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "gate.human_design_approval"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-review-hints", definition);
        var first = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("runs/run-1/outputs/codex-review.md"), Map.of());

        var out = new ByteArrayOutputStream();
        ReviewPacketCliHints.print(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                WorkflowStatus.WAITING_FOR_HUMAN,
                tempDir,
                workstreamId
        );

        var printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Review packet: " + tempDir.resolve(".agent/workstreams/" + workstreamId + "/reviews/node-gate-human_design_approval-002.md")));
        assertTrue(printed.contains("Next: dualctl review show --workdir " + tempDir + " " + workstreamId));
        assertTrue(printed.contains("Then: dualctl review approve|revise --workdir " + tempDir + " " + workstreamId));
    }
}
