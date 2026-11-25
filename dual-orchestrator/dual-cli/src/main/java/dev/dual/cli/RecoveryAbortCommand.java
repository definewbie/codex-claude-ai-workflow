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
@Command(name = "abort", description = "Abort a workflow waiting for recovery.", mixinStandardHelpOptions = true)
public final class RecoveryAbortCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", description = "Short reason for aborting.")
    String note = "";

    @Option(names = "--decision", description = "Structured decision for an UNKNOWN execution; a note alone cannot authorize it.")
    Path decision;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        var ledger = CliLedgerFactory.create();
        if (decision != null) {
            var request = new dev.dual.ledger.file.AtomicJsonWriter().read(decision,
                    dev.dual.runtime.handler.ExecutionRecoveryAdjudication.Request.class);
            if (!"ABORT".equals(request.disposition())) throw new IllegalArgumentException("wrong recovery disposition for this command");
            dev.dual.runtime.handler.ExecutionRecoveryAdjudication.apply(ledger, paths, request);
        } else ledger.abortRecovery(paths, note);

        System.out.println("Recovery aborted");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Ledger: " + paths.workstreamDir());
        return 0;
    }
}
