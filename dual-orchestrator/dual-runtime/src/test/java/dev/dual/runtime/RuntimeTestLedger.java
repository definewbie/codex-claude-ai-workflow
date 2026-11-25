package dev.dual.runtime;

import dev.dual.core.domain.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.*;
import java.time.Instant;
import java.io.IOException;
import java.util.Map;

/** Explicit runtime identity and complete L00 for offline fixtures; no production compatibility path. */
public final class RuntimeTestLedger {
    private RuntimeTestLedger() {}
    public static WorkflowFileLedger ledger() {
        return new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "runtime-tests-v3"));
    }
    public static dev.dual.runtime.handler.TaskHandler.TaskContext claim(dev.dual.runtime.handler.TaskHandler.TaskContext context) throws IOException {
        var paths = new WorkstreamPaths(context.workspaceRoot(), new DualId(context.workstreamId()));
        try (var write = context.ledger().writeSession(paths, "offline runtime claim", java.util.UUID.randomUUID().toString())) {
            var scoped = write.ledger();
            var node = scoped.loadNode(paths, context.instance().nodeInstanceId());
            if (node.status() != dev.dual.core.workflow.NodeStatus.READY)
                throw new IOException("test must complete/retry the previous execution before requesting another claim");
            var execution = scoped.startWorkflowExecution(paths, scoped.loadCurrent(paths), "offline runtime fixture");
            var task = scoped.createAgentTask(paths, execution.workflowExecutionId(), context.definition(), node);
            var claim = scoped.startNodeExecution(paths, execution.workflowExecutionId(), node, task, ProcessHandle.current().pid());
            scoped.markAgentTaskRunning(paths, task, claim.nodeExecutionId());
            scoped.markRunning(paths, node.nodeInstanceId(), ProcessHandle.current().pid());
            write.commit();
        }
        return new dev.dual.runtime.handler.TaskHandler.TaskContext(context.workspaceRoot(), context.workstreamId(),
                context.definition(), context.ledger().loadCoreNode(paths, context.instance().nodeInstanceId()), context.ledger());
    }

    public static java.util.List<dev.dual.core.contract.TaskContract.Clause> clauses(String prefix, java.util.List<String> texts) {
        return java.util.stream.IntStream.range(0, texts.size()).mapToObj(i ->
                new dev.dual.core.contract.TaskContract.Clause(prefix + (i + 1), texts.get(i))).toList();
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
