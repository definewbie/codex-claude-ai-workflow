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

class CliNextActionHintsTest {
    @TempDir
    Path tempDir;

    @Test
    void printsHumanGatePacketAndApproveReviseCommands() throws Exception {
        var workstreamId = "ws-human-hints";
        writeHumanGateLedger(workstreamId);

        var printed = capture(status -> CliNextActionHints.print(
                status,
                WorkflowStatus.WAITING_FOR_HUMAN,
                tempDir,
                workstreamId
        ));

        assertTrue(printed.contains("Next actions:"));
        assertTrue(printed.contains("dualctl review show --workdir " + tempDir + " " + workstreamId));
        assertTrue(printed.contains("packet: " + tempDir.resolve(".agent/workstreams/" + workstreamId + "/reviews/node-gate-human_design_approval-002.md")));
        assertTrue(printed.contains("dualctl approve --workdir " + tempDir + " --run " + workstreamId));
        assertTrue(printed.contains("dualctl revise --workdir " + tempDir + " --run --feedback-file feedback.md " + workstreamId));
    }

    @Test
    void printsRecoverySummaryAndRetryCommand() throws Exception {
        var workstreamId = "ws-recovery-hints";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.currentFailureMarkdown().getParent());
        Files.writeString(paths.currentFailureMarkdown(), "# Failure\n", StandardCharsets.UTF_8);

        var printed = capture(out -> CliNextActionHints.print(
                out,
                WorkflowStatus.WAITING_FOR_RECOVERY,
                tempDir,
                workstreamId
        ));

        assertTrue(printed.contains("dualctl recovery show --workdir " + tempDir + " " + workstreamId));
        assertTrue(printed.contains("summary: " + paths.currentFailureMarkdown()));
        assertTrue(printed.contains("dualctl retry --workdir " + tempDir + " " + workstreamId));
    }

    @Test
    void printsFinalReportAndSummaryCommandWhenCompleted() throws Exception {
        var workstreamId = "ws-completed-hints";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var report = paths.workstreamDir().resolve("reports").resolve("latest-final-report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "# Final\n", StandardCharsets.UTF_8);

        var printed = capture(out -> CliNextActionHints.print(
                out,
                WorkflowStatus.COMPLETED,
                tempDir,
                workstreamId
        ));

        assertTrue(printed.contains("final report: " + report));
        assertTrue(printed.contains("dualctl summary --workdir " + tempDir + " " + workstreamId));
    }

    private void writeHumanGateLedger(String workstreamId) throws Exception {
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
    }

    private String capture(Printer printer) {
        var out = new ByteArrayOutputStream();
        printer.print(new PrintStream(out, true, StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface Printer {
        void print(PrintStream out);
    }
}
