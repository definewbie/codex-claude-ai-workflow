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

    @Option(names = "--terminate-process", description = "Request stop of the exact recorded native session, then inspect recovery.")
    boolean terminateProcess;

    @Option(names = "--decision", description = "Explicit ADOPT_VERIFIED_RESULT payload for the current unknown attempt.")
    Path decision;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));

        var ledger = CliLedgerFactory.create();
        if (decision != null) {
            if (terminateProcess) throw new IllegalArgumentException("adoption and stop are separate operations");
            var request = new dev.dual.ledger.file.AtomicJsonWriter().objectMapper().readValue(decision.toFile(),
                    dev.dual.runtime.handler.ExecutionRecoveryAdjudication.Request.class);
            if (!"ADOPT_VERIFIED_RESULT".equals(request.disposition())) throw new IllegalArgumentException("stale adoption requires ADOPT_VERIFIED_RESULT");
            dev.dual.runtime.handler.ExecutionRecoveryAdjudication.apply(ledger, paths, request);
            System.out.println("Original fixed result adopted; no provider was invoked.");
            return 0;
        }
        var recovery = new StaleRunningRecovery(ledger);
        if (terminateProcess) {
            if (dev.dual.runtime.RecordedExecutionRecovery.active(ledger, paths).isPresent()) {
                var refs = new dev.dual.adapter.acpx.AcpxExecutionRecovery().stop(ledger, paths);
                System.out.println("Native stop observations: " + new dev.dual.ledger.file.AtomicJsonWriter().objectMapper().writeValueAsString(refs));
                var execution = dev.dual.runtime.RecordedExecutionRecovery.active(ledger, paths).orElseThrow();
                if (execution.status() == dev.dual.ledger.file.NodeExecutionStatus.RUNNING) recovery.recover(paths, note);
            } else recovery.terminateAndRecover(paths, note);
        } else {
            recovery.recover(paths, note);
        }

        System.out.println("Recovery state: " + ledger.loadCurrent(paths).status());
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Next: inspect recovery; UNKNOWN requires a structured decision and does not permit ordinary retry.");
        return 0;
    }
}
