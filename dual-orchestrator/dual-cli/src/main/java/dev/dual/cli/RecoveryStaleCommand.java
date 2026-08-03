package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.StaleRunningRecovery;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(
        name = "stale",
        description = "Move a stale RUNNING node with no live process into recovery.",
        mixinStandardHelpOptions = true
)
public final class RecoveryStaleCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", description = "Short reason for stale-running recovery.")
    String note = "";

    @Option(names = "--terminate-process", description = "Terminate durable in-flight provider process trees before recovery.")
    boolean terminateProcess;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(),
                LockOwner.currentProcess(workstreamId, "recovery stale"))) {
            var recovery = new StaleRunningRecovery(CliLedgerFactory.create());
            if (terminateProcess) {
                recovery.terminateAndRecover(paths, note);
            } else {
                recovery.recover(paths, note);
            }
        }
        System.out.println("Stale running node moved to recovery");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Next: dualctl retry --workdir " + workspaceRoot + " " + workstreamId);
        return 0;
    }
}
