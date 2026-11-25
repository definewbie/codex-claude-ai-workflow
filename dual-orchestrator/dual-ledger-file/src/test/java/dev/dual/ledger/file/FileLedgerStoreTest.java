package dev.dual.ledger.file;

import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLedgerStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesAndLoadsWorkstreamLedger() throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var workstream = workstream(workstreamId, workspaceId);
        var workspace = workspace(workstreamId, workspaceId);

        var paths = new WorkstreamPaths(tempDir, workstreamId);
        var ledger = LedgerTestFixture.ledger();
        ledger.initialize(paths, workstream, workspace, "# Requirement\n", WorkstreamSettingsDocument.of("auto"),
                "wf-store", definition());

        assertTrue(Files.exists(paths.workstreamJson()));
        assertTrue(Files.exists(paths.workspaceJson(workspaceId)));
        assertTrue(Files.exists(paths.settingsJson()));
        assertTrue(Files.readString(paths.settingsJson()).contains("\"promptLanguage\" : \"auto\""));
        assertTrue(Files.exists(paths.requirementMarkdown()));
        assertTrue(Files.readString(paths.timelineJsonl()).contains("workstream_created"));
        try (var read = ledger.readSession(paths)) {
            assertEquals(workstreamId.value(), read.store().loadStatus(tempDir, workstreamId.value()).workstream().workstreamId());
        }
    }

    @Test
    void initializesWorkstreamSettingsWithPromptLanguage() throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var paths = new WorkstreamPaths(tempDir, workstreamId);

        LedgerTestFixture.ledger().initialize(
                paths,
                workstream(workstreamId, workspaceId),
                workspace(workstreamId, workspaceId),
                "# Requirement\n",
                WorkstreamSettingsDocument.of("zh"), "wf-store", definition()
        );

        assertTrue(Files.readString(paths.settingsJson()).contains("\"promptLanguage\" : \"zh-CN\""));
    }

    private dev.dual.core.workflow.WorkflowDefinition definition() {
        return new dev.dual.core.workflow.WorkflowDefinition("store", 1, "first", java.util.List.of(
                new dev.dual.core.workflow.NodeDefinition("first", dev.dual.core.workflow.NodeKind.TASK,
                        "noop.task", java.util.Map.of())), java.util.List.of());
    }

    private Workstream workstream(DualId workstreamId, DualId workspaceId) {
        return Workstream.created(
                workstreamId,
                "A6 design",
                new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                workspaceId,
                Instant.parse("2026-05-22T10:15:00Z")
        );
    }

    private Workspace workspace(DualId workstreamId, DualId workspaceId) {
        return Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                tempDir.resolve("repo"),
                tempDir.resolve("repo-agent-runs").resolve(workstreamId.value()),
                "ai/" + workstreamId.value(),
                "main",
                "abc123"
        );
    }
}
