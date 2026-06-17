package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.AgentTaskDispatchMode;
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
        return run(workspaceRoot, workstreamId, maxSteps, AgentTaskDispatchMode.INLINE);
    }

    int run(Path workspaceRoot, String workstreamId, int maxSteps, AgentTaskDispatchMode dispatchMode) throws Exception {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("--max-steps must be positive");
        }
        var runtime = new CliWorkflowRuntimeFactory().create(dispatchMode);
        var command = new WorkflowCommand(workspaceRoot, workstreamId);
        for (int step = 1; step <= maxSteps; step++) {
            var result = runtime.resume(command);
            System.out.println("Step " + step + ": " + result.activeNode() + " -> " + result.status() + " (" + result.message() + ")");
            if (TERMINAL_OR_PAUSED.contains(result.status())) {
                refreshSummary(workspaceRoot, workstreamId);
                printNextAction(result.status(), workspaceRoot, workstreamId);
                return exitCode(result.status());
            }
        }
        System.out.println("Stopped after --max-steps=" + maxSteps + ". Run again to continue.");
        refreshSummary(workspaceRoot, workstreamId);
        CliNextActionHints.print(System.out, WorkflowStatus.RUNNING, workspaceRoot, workstreamId);
        return 1;
    }

    private void refreshSummary(Path workspaceRoot, String workstreamId) {
        try {
            SummaryCommand.writeDefaultSummary(new WorkstreamPaths(workspaceRoot, new DualId(workstreamId)));
        } catch (Exception e) {
            System.err.println("Warning: failed to refresh workstream summary: " + e.getMessage());
        }
    }

    private void printNextAction(WorkflowStatus status, Path workspaceRoot, String workstreamId) {
        CliNextActionHints.print(System.out, status, workspaceRoot, workstreamId);
    }

    private int exitCode(WorkflowStatus status) {
        return status == WorkflowStatus.FAILED
                || status == WorkflowStatus.WAITING_FOR_RECOVERY
                || status == WorkflowStatus.BLOCKED ? 1 : 0;
    }
}
