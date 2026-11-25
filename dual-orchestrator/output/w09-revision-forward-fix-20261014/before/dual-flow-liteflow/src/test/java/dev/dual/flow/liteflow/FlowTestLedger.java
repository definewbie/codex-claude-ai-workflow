package dev.dual.flow.liteflow;

import dev.dual.core.domain.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.*;
import java.time.Instant;
import java.io.IOException;
import java.util.Map;

/** Explicit runtime identity and complete L00 for offline fixtures; no production compatibility path. */
final class FlowTestLedger {
    private FlowTestLedger() {}
    public static WorkflowFileLedger ledger() {
        return new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "liteflow-tests-v3"));
    }
    public static void initialize(WorkflowFileLedger ledger, WorkstreamPaths paths, String id, WorkflowDefinition definition) throws IOException {
        var workspace = new DualId("workspace-" + paths.workstreamId().value());
        var root = paths.workspaceRoot();
        ledger.initialize(paths, Workstream.created(paths.workstreamId(), "runtime lifecycle test",
                        new Workstream.RepositoryRef(root, root), workspace, Instant.now()),
                Workspace.primaryGitWorktree(workspace, paths.workstreamId(), root, root, "test", "main", "test-base"),
                "Run the declared test workflow", WorkstreamSettingsDocument.of("auto"), id, definition);
    }
}
