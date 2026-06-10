package dev.dual.cli;

import dev.dual.core.id.DualId;
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
@Command(
        name = "close-mistaken-iteration",
        description = "Close an accidentally opened next iteration after the final gate.",
        mixinStandardHelpOptions = true
)
public final class RecoveryCloseMistakenIterationCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", description = "Short reason for closing the mistaken iteration.")
    String note = "";

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "recovery close mistaken iteration"))) {
            new WorkflowFileLedger().closeMistakenIteration(paths, note);
        }
        System.out.println("Mistaken iteration closed");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Ledger: " + paths.workstreamDir());
        return 0;
    }
}
