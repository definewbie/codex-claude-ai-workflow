package dev.dual.ledger.file;

import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowDefinition;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Explicit frozen test runtime, using the same complete initialization transaction as applications. */
final class LedgerTestFixture {
    static final Map<String, String> IDENTITY = Map.of("test_binary", "ledger-tests-v2");
    private LedgerTestFixture() {}
    static WorkflowFileLedger ledger() { return new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), IDENTITY); }
    static void initialize(WorkflowFileLedger ledger, WorkstreamPaths paths, String workflowId,
                           WorkflowDefinition definition) throws IOException {
        var workspaceId = new DualId("workspace-" + paths.workstreamId().value());
        var root = paths.workspaceRoot();
        ledger.initialize(paths, Workstream.created(paths.workstreamId(), "ledger sequence",
                        new Workstream.RepositoryRef(root, root), workspaceId, Instant.now()),
                Workspace.primaryGitWorktree(workspaceId, paths.workstreamId(), root, root, "test", "main", "test-base"),
                "Exercise the declared workflow", WorkstreamSettingsDocument.of("auto"), workflowId, definition);
    }
}
