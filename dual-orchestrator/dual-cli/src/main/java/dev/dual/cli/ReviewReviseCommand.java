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
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Component
@Command(name = "revise", description = "Request another design revision.")
public final class ReviewReviseCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Option(names = "--feedback-file", required = true)
    Path feedbackFile;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "review revise"))) {
            var ledger = new WorkflowFileLedger();
            var feedback = Files.readString(feedbackFile.toAbsolutePath().normalize());
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", feedback);
        }
        System.out.println("Human gate revision requested");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Feedback: " + feedbackFile.toAbsolutePath().normalize());
        System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
        return 0;
    }
}
