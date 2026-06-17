package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.AgentTaskDispatchMode;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "run", description = "Run a workstream until a gate, recovery, failure, or completion.", mixinStandardHelpOptions = true)
public final class RunCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 20;

    @Option(names = "--dispatch-mode", description = "Agent task dispatch mode: ${COMPLETION-CANDIDATES}. Defaults to ${DEFAULT-VALUE}.")
    AgentTaskDispatchMode dispatchMode = AgentTaskDispatchMode.INLINE;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "run"))) {
            return new CliWorkflowLoop().run(workspaceRoot, workstreamId, maxSteps, dispatchMode);
        }
    }
}
