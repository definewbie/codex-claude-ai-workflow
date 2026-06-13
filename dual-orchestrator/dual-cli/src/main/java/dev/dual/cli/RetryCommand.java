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
@Command(name = "retry", description = "Mark the current failed or blocked node as ready to run again.", mixinStandardHelpOptions = true)
public final class RetryCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", description = "Short reason for retrying.")
    String note = "";

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "retry"))) {
            CliLedgerFactory.create().retryActiveNode(paths, note);
        }
        System.out.println("Active node marked ready for retry");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
        return 0;
    }
}
