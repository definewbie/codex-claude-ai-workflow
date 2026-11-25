package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "complete", description = "Complete the current human review gate and finish the workflow.", mixinStandardHelpOptions = true)
public class ReviewCompleteCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--note", description = "Optional completion note.")
    String note = "completed from CLI";

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var ledger = CliLedgerFactory.create();
        dev.dual.runtime.handler.ReviewGateActions.resolve(ledger, paths, "complete", note);
        System.out.println("Human gate completed");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Status: workflow completed");
        return 0;
    }
}
