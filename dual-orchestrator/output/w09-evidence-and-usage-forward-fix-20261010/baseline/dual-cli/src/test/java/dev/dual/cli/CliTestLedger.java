package dev.dual.cli;

import dev.dual.core.domain.*;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.time.Instant;

/** Explicit complete test initialization using the actual application factory identity. */
final class CliTestLedger {
    static void initialize(WorkflowFileLedger ledger, WorkstreamPaths paths, String id, WorkflowDefinition definition) throws IOException {
        var root = paths.workspaceRoot();
        var workspace = new DualId("workspace-" + paths.workstreamId().value());
        ledger.initialize(paths, Workstream.created(paths.workstreamId(), "lifecycle test", new Workstream.RepositoryRef(root, root), workspace, Instant.now()),
                Workspace.primaryGitWorktree(workspace, paths.workstreamId(), root, root, "test", "main", "test-base"),
                "Test the declared workflow", WorkstreamSettingsDocument.of("auto"), id, definition);
    }
}
