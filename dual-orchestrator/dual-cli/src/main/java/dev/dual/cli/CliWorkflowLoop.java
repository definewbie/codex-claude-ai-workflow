package dev.dual.cli;

import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.runtime.WorkflowCommand;

import java.nio.file.Path;
import java.util.Set;

final class CliWorkflowLoop {
    private static final Set<WorkflowStatus> TERMINAL_OR_PAUSED = Set.of(
            WorkflowStatus.WAITING_FOR_HUMAN,
            WorkflowStatus.WAITING_FOR_RECOVERY,
            WorkflowStatus.BLOCKED,
            WorkflowStatus.COMPLETED,
            WorkflowStatus.FAILED
    );

    int run(Path workspaceRoot, String workstreamId, int maxSteps) throws Exception {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("--max-steps must be positive");
        }
        var runtime = new CliWorkflowRuntimeFactory().create();
        var command = new WorkflowCommand(workspaceRoot, workstreamId);
        for (int step = 1; step <= maxSteps; step++) {
            var result = runtime.resume(command);
            System.out.println("Step " + step + ": " + result.activeNode() + " -> " + result.status() + " (" + result.message() + ")");
            if (TERMINAL_OR_PAUSED.contains(result.status())) {
                printNextAction(result.status(), workspaceRoot, workstreamId);
                return exitCode(result.status());
            }
        }
        System.out.println("Stopped after --max-steps=" + maxSteps + ". Run again to continue.");
        return 1;
    }

    private void printNextAction(WorkflowStatus status, Path workspaceRoot, String workstreamId) {
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
