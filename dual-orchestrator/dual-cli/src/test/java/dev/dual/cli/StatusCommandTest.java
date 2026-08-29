package dev.dual.cli;

import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void statusUsesWorkflowCurrentAndHidesLegacySnapshotByDefault() throws Exception {
        var workstreamId = new DualId("ws-status");
        var workspaceId = new DualId("wsp-status");
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstreamId,
                        "status test",
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspaceId,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspaceId,
                        workstreamId,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs/ws-status"),
                        "ai/ws-status",
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
        var definition = new WorkflowDefinition(
                "status-workflow",
                1,
                "implementation.claude",
                List.of(new NodeDefinition("implementation.claude", NodeKind.TASK, "agent.acpx", Map.of())),
                List.of()
        );
        new WorkflowFileLedger().initialize(paths, "wf-status", definition);

        var command = new StatusCommand();
        command.workdir = tempDir;
        command.workstreamId = workstreamId.value();

        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        var printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Workflow:"));
        assertTrue(printed.contains("Status: RUNNING"));
        assertTrue(printed.contains("Active node definition: implementation.claude"));
        assertTrue(printed.contains("Active node status: READY"));
        assertTrue(printed.contains("Delivery:"));
        assertTrue(printed.contains("Reason: WORKFLOW_NOT_DELIVERABLE"));
        assertTrue(printed.contains("Eligibility: Current workflow is not yet deliverable."));
        assertTrue(printed.contains("Can merge: false"));
        assertTrue(printed.contains("Can finalize: false"));
        assertFalse(printed.contains("Compatibility snapshot"));
        assertFalse(printed.contains("Stage: design (pending)"));
    }

    @Test
    void statusShowsStaleRunningPid() throws Exception {
        var workstreamId = new DualId("ws-status-stale");
        var workspaceId = new DualId("wsp-status-stale");
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstreamId,
                        "status stale test",
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspaceId,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspaceId,
                        workstreamId,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs/ws-status-stale"),
                        "ai/ws-status-stale",
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
        var definition = new WorkflowDefinition(
                "status-workflow",
                1,
                "implementation.claude",
                List.of(new NodeDefinition("implementation.claude", NodeKind.TASK, "agent.acpx", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-status-stale", definition);
        ledger.markRunning(paths, "node-implementation-claude-001", 999999L);

        var command = new StatusCommand();
        command.workdir = tempDir;
        command.workstreamId = workstreamId.value();

        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        var printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Active node status: RUNNING"));
        assertTrue(printed.contains("Running PID: 999999 (stale)"));
    }
}
