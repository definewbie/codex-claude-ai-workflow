package dev.dual.cli;

import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "status", description = "Show workstream status.", mixinStandardHelpOptions = true)
public final class StatusCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var status = new FileLedgerStore().loadStatus(workdir.toAbsolutePath().normalize(), workstreamId);

        System.out.println("Workstream: " + status.workstream().workstreamId());
        System.out.println("Workspace: " + status.workspace().effectiveWorkdir());
        System.out.println("Branch: " + status.workspace().branch());
        System.out.println("Ledger: " + status.workstreamDir());

        System.out.println();
        System.out.println("Legacy snapshot:");
        System.out.println("  Status: " + status.workstream().status());
        System.out.println("  Stage: " + status.stage().type() + " (" + status.stage().status() + ")");
        System.out.println("  Iteration: " + status.iteration().iterationId() + " (" + status.iteration().status() + ")");
        System.out.println("  Checkpoint: " + status.checkpoint().checkpointId());
        System.out.println("  Pending action: " + status.checkpoint().pendingAction());
        System.out.println("  Resume command: " + status.checkpoint().resumeCommand());

        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        if (Files.exists(paths.workflowCurrentJson())) {
            var current = new WorkflowFileLedger().loadCurrent(paths);
            System.out.println();
            System.out.println("Workflow:");
            System.out.println("  Definition: " + current.workflowId() + "@" + current.definitionVersion());
            System.out.println("  Status: " + current.status());
            System.out.println("  Active nodes: " + current.activeNodeInstanceIds());
            System.out.println("  Open gates: " + current.openGateIds());
        }
        return 0;
    }
}
