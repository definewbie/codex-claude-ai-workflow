package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.WorkflowCommand;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;

@Component
@Command(name = "run", description = "Run a workstream until a gate, recovery, failure, or completion.", mixinStandardHelpOptions = true)
public final class RunCommand implements Callable<Integer> {
    private static final Set<WorkflowStatus> TERMINAL_OR_PAUSED = Set.of(
            WorkflowStatus.WAITING_FOR_HUMAN,
            WorkflowStatus.WAITING_FOR_RECOVERY,
            WorkflowStatus.BLOCKED,
            WorkflowStatus.COMPLETED,
            WorkflowStatus.FAILED
    );

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 20;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("--max-steps must be positive");
        }
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "run"))) {
            var runtime = new CliWorkflowRuntimeFactory().create();
            var command = new WorkflowCommand(workspaceRoot, workstreamId);
            for (int step = 1; step <= maxSteps; step++) {
                var result = runtime.resume(command);
                System.out.println("Step " + step + ": " + result.activeNode() + " -> " + result.status() + " (" + result.message() + ")");
                if (TERMINAL_OR_PAUSED.contains(result.status())) {
                    printNextAction(result.status(), workspaceRoot);
                    return exitCode(result.status());
                }
            }
            System.out.println("Stopped after --max-steps=" + maxSteps + ". Run again to continue.");
            return 1;
        }
    }

    private void printNextAction(WorkflowStatus status, Path workspaceRoot) {
        if (status == WorkflowStatus.WAITING_FOR_HUMAN) {
            System.out.println("Next: dualctl status --workdir " + workspaceRoot + " " + workstreamId);
            System.out.println("Then: dualctl review approve|revise --workdir " + workspaceRoot + " " + workstreamId);
        } else if (status == WorkflowStatus.WAITING_FOR_RECOVERY) {
            System.out.println("Next: fix the external cause, then dualctl retry --workdir " + workspaceRoot + " " + workstreamId);
        }
    }

    private int exitCode(WorkflowStatus status) {
        return status == WorkflowStatus.FAILED
                || status == WorkflowStatus.WAITING_FOR_RECOVERY
                || status == WorkflowStatus.BLOCKED ? 1 : 0;
    }
}
