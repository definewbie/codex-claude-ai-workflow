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
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReviewPacketCliHintsTest {
    @TempDir
    Path tempDir;

    @Test
    void bothHintSurfacesUseCurrentRepairActionsWithoutInventingApprove() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-repair-hints"));
        var definition = new WorkflowDefinition("repair-hints", 1, "first", List.of(
                new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of(
                        "gate_purpose", "implementation_repair", "allowed_actions", List.of("revise", "request_action", "collect_evidence", "submit_evidence"))),
                new NodeDefinition("collect", NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", "self_test_report"))),
                List.of(new EdgeDefinition("first", "completed", "repair"),
                        new EdgeDefinition("repair", "collect_evidence", "collect"), new EdgeDefinition("repair", "submit_evidence", "collect")));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-repair", definition);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        for (boolean nextActions : List.of(false, true)) {
            var bytes = new ByteArrayOutputStream();
            var out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            if (nextActions) CliNextActionHints.print(out, WorkflowStatus.WAITING_FOR_HUMAN, tempDir, "ws-repair-hints");
            else ReviewPacketCliHints.print(out, WorkflowStatus.WAITING_FOR_HUMAN, tempDir, "ws-repair-hints");
            var printed = bytes.toString(StandardCharsets.UTF_8);
            assertFalse(printed.contains("dualctl approve"), printed);
            assertTrue(printed.contains("collect_evidence"), printed);
            assertTrue(printed.contains("request-action"), printed);
            assertTrue(printed.contains("prepare-evidence"), printed);
            assertTrue(printed.contains("submit-evidence --help"), printed);
            assertFalse(printed.contains("decide --action submit_evidence"), printed);
        }
    }

    @Test
    void missingLedgerDoesNotInventMutationCommands() {
        var bytes = new ByteArrayOutputStream();
        var out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ReviewPacketCliHints.print(out, WorkflowStatus.WAITING_FOR_HUMAN, tempDir, "ws-missing");
        CliNextActionHints.print(out, WorkflowStatus.WAITING_FOR_HUMAN, tempDir, "ws-missing");
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("dualctl approve"));
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("dualctl revise"));
    }

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
        assertTrue(printed.contains("Then: dualctl approve --run --workdir " + tempDir + " " + workstreamId));
        assertTrue(printed.contains("Or:   dualctl revise --feedback-file feedback.md --run --workdir " + tempDir + " " + workstreamId));
    }
}
