package dev.dual.cli;

import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "approve", description = "Approve the current human review gate.", mixinStandardHelpOptions = true)
public final class ReviewApproveCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--run", description = "After approval, immediately run until the next gate, recovery, failure, or completion.")
    boolean runImmediately;

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute when --run is used. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 20;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "review approve"))) {
            var ledger = new WorkflowFileLedger();
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approved", "approved from CLI");
        }
        System.out.println("Human gate approved");
        System.out.println("Workstream: " + workstreamId);
        if (!runImmediately) {
            System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
            return 0;
        }
        System.out.println("Running workflow until next gate...");
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "run"))) {
            return new CliWorkflowLoop().run(workspaceRoot, workstreamId, maxSteps);
        }
    }
}
