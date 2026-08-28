package dev.dual.cli;

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
        name = "approve-websearch",
        description = "Approve exact WebSearch for one retry of a public repository task.",
        mixinStandardHelpOptions = true
)
public final class RecoveryApproveWebSearchCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", required = true, description = "Human reason for approving public WebSearch.")
    String note;

    @Option(
            names = "--public-repository",
            required = true,
            description = "Attest that repository content and the requested lookup are public."
    )
    boolean publicRepository;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(
                paths.workstreamDir(),
                LockOwner.currentProcess(workstreamId, "approve public WebSearch"))) {
            CliLedgerFactory.create().approvePublicWebSearch(paths, note, publicRepository);
        }
        System.out.println("Public WebSearch approved for one retry");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
        return 0;
    }
}
