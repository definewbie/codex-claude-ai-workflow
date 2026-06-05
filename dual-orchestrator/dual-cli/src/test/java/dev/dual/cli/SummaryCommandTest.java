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
        assertTrue(printed.contains("dualctl review show"));
        assertTrue(printed.contains("| design.codex_arch_review | task | COMPLETED | iter-001 | 42 |"));
        assertTrue(printed.contains("candidate-1=ACCEPTED"));
        assertTrue(printed.contains("- Calibration Apply Plans: 1"));
        assertTrue(printed.contains("- Delivery: prepared, commit=abc123"));

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
        assertTrue(printed.contains("# Workstream Summary"));
    }

    private void writeLedger(String status) throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-summary");
        Files.createDirectories(root.resolve("reports"));
        Files.createDirectories(root.resolve("recovery"));
        Files.createDirectories(root.resolve("delivery"));
        Files.createDirectories(root.resolve("calibration/candidates"));
        Files.createDirectories(root.resolve("calibration/apply-plans"));
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
        Files.writeString(root.resolve("timeline.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-summary",
                  "workflowId": "agent-lifecycle-p5",
                  "definitionVersion": 2,
                  "workflowStatus": "%s",
                  "activeNodeInstanceIds": ["node-gate-1"],
                  "openGateIds": ["gate-node-gate-1"],
                  "updatedAt": "2026-06-04T00:00:00Z",
                  "nodes": [
                    {
                      "nodeDefinitionId": "design.codex_arch_review",
                      "kind": "task",
                      "status": "COMPLETED",
                      "iterationId": "iter-001",
                      "durationMs": 42
                    }
                  ]
                }
                """.formatted(status), StandardCharsets.UTF_8);
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
