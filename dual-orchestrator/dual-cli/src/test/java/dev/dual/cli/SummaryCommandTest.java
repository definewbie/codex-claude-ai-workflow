package dev.dual.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void printsWorkstreamSummaryFromLedgerFiles() throws Exception {
        writeLedger("WAITING_FOR_HUMAN");

        var command = new SummaryCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-summary";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("# Workstream Summary"));
        assertTrue(printed.contains("- Workstream: ws-summary"));
        assertTrue(printed.contains("- Status: WAITING_FOR_HUMAN"));
        assertTrue(printed.contains("## Metrics"));
        assertTrue(printed.contains("- Nodes: 2"));
        assertTrue(printed.contains("- Agent Runs: 1"));
        assertTrue(printed.contains("dualctl review show"));
        assertTrue(printed.contains("| design.codex_arch_review | TASK | COMPLETED | iter-001 | 42 |"));
        assertTrue(printed.contains("candidate-1=ACCEPTED"));
        assertTrue(printed.contains("- Calibration Apply Plans: 1"));
        assertTrue(printed.contains("- Delivery: BLOCKED"));
        assertTrue(printed.contains("reason=WORKFLOW_NOT_DELIVERABLE"));
        assertTrue(printed.contains("- Current Failure Summary: not current (historical recovery artifact retained)"));

        var summary = tempDir.resolve(".agent/workstreams/ws-summary/reports/workstream-summary.md");
        assertTrue(Files.isRegularFile(summary));
        assertTrue(Files.readString(summary, StandardCharsets.UTF_8).contains("- Status: WAITING_FOR_HUMAN"));
    }

    @Test
    void writesSummaryToReportsWhenRequested() throws Exception {
        writeLedger("COMPLETED");

        var command = new SummaryCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-summary";
        command.write = true;

        var printed = captureStdout(command::call);

        var summary = tempDir.resolve(".agent/workstreams/ws-summary/reports/workstream-summary.md");
        assertTrue(Files.isRegularFile(summary));
        assertTrue(Files.readString(summary, StandardCharsets.UTF_8).contains("- Status: COMPLETED"));
        assertTrue(Files.readString(summary, StandardCharsets.UTF_8)
                .contains("- Current Failure Summary: not current (historical recovery artifact retained)"));
        assertTrue(printed.contains("# Workstream Summary"));
    }

    @Test
    void showsCurrentFailureOnlyForRecoveryStates() throws Exception {
        writeLedger("WAITING_FOR_RECOVERY");

        var command = new SummaryCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-summary";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("- Status: WAITING_FOR_RECOVERY"));
        assertTrue(printed.contains("- Current Failure Summary: "));
        assertTrue(!printed.contains("not current (historical recovery artifact retained)"));
    }

    @Test
    void rawAppliedPlanCannotProduceFinalizeWithoutAcceptedCandidateBinding() throws Exception {
        writeLedger("COMPLETED");
        var deliveryDir = tempDir.resolve(".agent/workstreams/ws-summary/delivery");
        Files.writeString(deliveryDir.resolve("merge-plan.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-summary",
                  "sourceWorktree": "/tmp/source",
                  "targetRepo": "/tmp/target",
                  "acceptanceDecision": "ACCEPT",
                  "changedFiles": ["README.md", "main.py"],
                  "excludedFiles": [],
                  "status": "APPLIED",
                  "commit": "",
                  "failureReason": ""
                }
                """, StandardCharsets.UTF_8);

        var command = new SummaryCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-summary";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("- Delivery: BLOCKED"));
        assertTrue(printed.contains("LEGACY_NOT_BOUND"), printed);
        assertTrue(!printed.contains("Review the staged target diff"));
        assertTrue(!printed.contains("delivery finalize"));
        assertTrue(!printed.contains("dualctl delivery merge --workdir"));
    }

    @Test
    void staleMetricsAndTimelineCannotOverrideTheCurrentLedger() throws Exception {
        writeLedger("WAITING_FOR_HUMAN");
        var root = tempDir.resolve(".agent/workstreams/ws-summary");
        Files.writeString(root.resolve("metrics/workstream-metrics.json"),
                "{\"commitSequence\":0,\"nodeCount\":999,\"workflowStatus\":\"COMPLETED\"}");
        Files.writeString(root.resolve("timeline.json"), "{\"workflowStatus\":\"COMPLETED\",\"nodes\":[]}");
        var command = new SummaryCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-summary";
        var printed = captureStdout(command::call);
        assertTrue(printed.contains("- Status: WAITING_FOR_HUMAN"));
        assertTrue(printed.contains("- Metrics State: STALE"));
        assertTrue(printed.contains("- Nodes: unavailable"));
        assertTrue(printed.contains("design.codex_arch_review"));
        var timeline = new TimelineCommand();
        timeline.workdir = tempDir; timeline.workstreamId = "ws-summary"; timeline.raw = true;
        var timelineJson = new com.fasterxml.jackson.databind.ObjectMapper().readTree(captureStdout(timeline::call));
        assertEquals("WAITING_FOR_HUMAN", timelineJson.path("workflowStatus").asText());
        assertEquals(2, timelineJson.path("nodes").size());
    }

    @Test
    void busyAndPendingDoNotFallBackToPreviouslySuccessfulReports() throws Exception {
        writeLedger("WAITING_FOR_HUMAN");
        var paths = new dev.dual.ledger.file.WorkstreamPaths(tempDir, new dev.dual.core.id.DualId("ws-summary"));
        var before = Files.readString(paths.workflowCurrentJson());
        var summary = new SummaryCommand(); summary.workdir = tempDir; summary.workstreamId = "ws-summary";
        var timeline = new TimelineCommand(); timeline.workdir = tempDir; timeline.workstreamId = "ws-summary";
        try (var lock = dev.dual.ledger.file.WorkstreamLock.acquire(paths.workstreamDir(),
                dev.dual.ledger.file.LockOwner.currentProcess("ws-summary", "concurrent writer"))) {
            org.junit.jupiter.api.Assertions.assertThrows(dev.dual.ledger.file.WorkstreamLockedException.class, summary::call);
            org.junit.jupiter.api.Assertions.assertThrows(dev.dual.ledger.file.WorkstreamLockedException.class, timeline::call);
        }
        Files.createDirectories(paths.workstreamDir().resolve("pending"));
        Files.writeString(paths.workstreamDir().resolve("pending/uncommitted.json"), "{}");
        org.junit.jupiter.api.Assertions.assertThrows(dev.dual.ledger.file.LedgerSession.PendingCommitException.class, summary::call);
        org.junit.jupiter.api.Assertions.assertThrows(dev.dual.ledger.file.LedgerSession.PendingCommitException.class, timeline::call);
        assertEquals(before, Files.readString(paths.workflowCurrentJson()), "readers never replay pending bytes");
    }

    private void writeLedger(String status) throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-summary");
        Files.createDirectories(root.resolve("reports"));
        Files.createDirectories(root.resolve("recovery"));
        Files.createDirectories(root.resolve("delivery"));
        Files.createDirectories(root.resolve("calibration/candidates"));
        Files.createDirectories(root.resolve("calibration/apply-plans"));
        Files.createDirectories(root.resolve("metrics"));
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("domain"));
        var paths = new dev.dual.ledger.file.WorkstreamPaths(tempDir, new dev.dual.core.id.DualId("ws-summary"));
        var ledger = CliLedgerFactory.create();
        var definition = new dev.dual.core.workflow.WorkflowDefinition("summary", 1, "design.codex_arch_review",
                java.util.List.of(new dev.dual.core.workflow.NodeDefinition("design.codex_arch_review",
                        dev.dual.core.workflow.NodeKind.TASK, "acpx.agent_run", java.util.Map.of()),
                        new dev.dual.core.workflow.NodeDefinition("review", dev.dual.core.workflow.NodeKind.GATE,
                                "human.review", java.util.Map.of())),
                java.util.List.of(new dev.dual.core.workflow.EdgeDefinition("design.codex_arch_review", "completed", "review")));
        CliTestLedger.initialize(ledger, paths, "wf-summary", definition);
        var first = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, first, dev.dual.core.workflow.NodeStatus.COMPLETED,
                "completed", java.util.List.of(), java.util.Map.of());
        // Query fixture supplies deterministic timestamps; it never authorizes dispatch or delivery.
        var mapper = new dev.dual.ledger.file.AtomicJsonWriter().objectMapper();
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(paths.nodeJson(first).toFile());
        node.put("startedAt", "2026-06-04T00:00:00Z");
        node.put("finishedAt", "2026-06-04T00:00:00.042Z");
        mapper.writeValue(paths.nodeJson(first).toFile(), node);
        var current = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(paths.workflowCurrentJson().toFile());
        current.put("status", status);
        mapper.writeValue(paths.workflowCurrentJson().toFile(), current);
        ledger.refreshProjections(paths);
        Files.writeString(root.resolve("domain/completion-audit.json"), """
                {"status":"ready","gaps":[]}
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("reports/latest-final-report.md"), "# Final\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("recovery/current-failure.md"), "# Failure\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("delivery/delivery.json"), """
                {
                  "status": "prepared",
                  "commitCreated": true,
                  "commit": "abc123",
                  "prCreated": false
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("calibration/candidates/candidate-1.json"), """
                {
                  "candidateId": "candidate-1",
                  "status": "ACCEPTED"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("calibration/apply-plans/candidate-1.json"), """
                {
                  "candidateId": "candidate-1",
                  "status": "PROPOSED"
                }
                """, StandardCharsets.UTF_8);
    }

    private static String jsonPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String captureStdout(ThrowingCallable callable) throws Exception {
        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, callable.call());
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
