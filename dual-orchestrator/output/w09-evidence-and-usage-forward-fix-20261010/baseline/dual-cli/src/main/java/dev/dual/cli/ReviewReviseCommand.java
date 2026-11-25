package dev.dual.cli;

import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Component
@Command(name = "revise", description = "Request another design revision.", mixinStandardHelpOptions = true)
public class ReviewReviseCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Option(names = "--subject", description = "Expected current occurrence; required for readiness failure repair.")
    String subject;

    @Option(names = "--feedback-file", required = true)
    Path feedbackFile;

    @Option(names = "--run", description = "After requesting revision, immediately run until the next gate, recovery, failure, or completion.")
    boolean runImmediately;

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute when --run is used. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 20;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        var ledger = CliLedgerFactory.create();
        var feedback = Files.readString(feedbackFile.toAbsolutePath().normalize());
        dev.dual.runtime.handler.ReviewGateActions.revise(ledger, paths, subject, feedback);
        System.out.println("Human gate revision requested");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Feedback: " + feedbackFile.toAbsolutePath().normalize());
        if (!runImmediately) {
            System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
            return 0;
        }
        System.out.println("Running workflow until next gate...");
        return new CliWorkflowLoop().run(workspaceRoot, workstreamId, maxSteps);
    }
}
