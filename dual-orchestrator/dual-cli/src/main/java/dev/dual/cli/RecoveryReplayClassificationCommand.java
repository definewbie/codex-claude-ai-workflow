package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.LockOwner;
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
        name = "replay-classification",
        description = "Replay a lineage-bound deterministic acceptance classification after a corrected classifier is installed.",
        mixinStandardHelpOptions = true
)
public final class RecoveryReplayClassificationCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", required = true, description = "Reason the completed classification must be replayed.")
    String note;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(
                paths.workstreamDir(),
                LockOwner.currentProcess(workstreamId, "recovery replay classification")
        )) {
            CliLedgerFactory.create().replayDeterministicClassification(paths, note);
        }
        System.out.println("Deterministic classification marked ready for replay");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
        return 0;
    }
}
