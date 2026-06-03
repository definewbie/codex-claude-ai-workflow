package dev.dual.cli;

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
import java.util.concurrent.Callable;

@Component
@Command(name = "resume", description = "Resume a paused workstream.", mixinStandardHelpOptions = true)
public final class ResumeCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--claude-session", description = "acpx Claude session name.")
    String claudeSession = "claude-main";

    @Option(names = "--codex-session", description = "acpx Codex session name.")
    String codexSession = "codex-reviewer";

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "resume"))) {
            var result = new CliWorkflowRuntimeFactory().create()
                    .resume(new WorkflowCommand(workspaceRoot, workstreamId));

            System.out.println("Workstream: " + result.workstreamId());
            System.out.println("Workflow status: " + result.status());
            System.out.println("Active node: " + result.activeNode());
            System.out.println(result.message());
            ReviewPacketCliHints.print(System.out, result.status(), workspaceRoot, workstreamId);
            return result.status() == WorkflowStatus.FAILED || result.status() == WorkflowStatus.WAITING_FOR_RECOVERY ? 1 : 0;
        }
    }
}
