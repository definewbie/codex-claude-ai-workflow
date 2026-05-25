package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DeliveryCommitHandler;
import dev.dual.runtime.handler.TaskHandler;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "commit", description = "Commit business changes using the generated delivery commit message.", mixinStandardHelpOptions = true)
public final class DeliveryCommitCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "delivery commit"))) {
            var result = new DeliveryCommitHandler().execute(new TaskHandler.TaskContext(
                    workspaceRoot,
                    workstreamId,
                    new NodeDefinition("delivery.git_commit", NodeKind.TASK, "delivery.git_commit", Map.of()),
                    new NodeInstance(
                            "node-delivery-git_commit-cli",
                            "delivery.git_commit",
                            NodeKind.TASK,
                            "delivery.git_commit",
                            NodeStatus.READY,
                            "stage-delivery",
                            currentIteration(paths),
                            List.of(),
                            List.of(),
                            Map.of(),
                            null,
                            null,
                            null
                    )
            ));
            if (result.status() != NodeStatus.COMPLETED) {
                System.err.println("Delivery commit failed: " + result.outcome());
                System.err.println(result.errorMessage() == null ? "" : result.errorMessage());
                return 1;
            }
            if (Boolean.TRUE.equals(result.result().get("commit_created"))) {
                System.out.println("Delivery commit created");
                System.out.println("Commit: " + result.result().get("commit"));
            } else {
                System.out.println("Delivery commit skipped");
                System.out.println("Reason: " + result.result().getOrDefault("reason", result.outcome()));
            }
            return 0;
        }
    }

    private static String currentIteration(WorkstreamPaths paths) {
        try {
            var current = new WorkflowFileLedger().loadCurrent(paths);
            if (!current.activeNodeInstanceIds().isEmpty()) {
                var node = new WorkflowFileLedger().loadNode(paths, current.activeNodeInstanceIds().get(0));
                return node.iterationId();
            }
        } catch (Exception ignored) {
            return "iter-001";
        }
        return "iter-001";
    }
}
