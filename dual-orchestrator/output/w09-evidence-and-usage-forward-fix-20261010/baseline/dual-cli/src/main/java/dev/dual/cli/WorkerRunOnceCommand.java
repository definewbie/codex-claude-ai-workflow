package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.AgentWorker;
import dev.dual.runtime.WorkflowCommand;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "run-once", description = "Claim and execute one queued AgentTask.", mixinStandardHelpOptions = true)
public final class WorkerRunOnceCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var worker = new AgentWorker(CliLedgerFactory.create(), dev.dual.adapter.acpx.AcpxWorkflowHandlerRegistryFactory.create());
        var result = worker.runOne(new WorkflowCommand(workspaceRoot, workstreamId));
        System.out.println("Worker: " + result.agentTaskId() + " " + result.nodeDefinitionId()
                + " -> " + result.status() + " (" + result.message() + ")");
        SummaryCommand.writeDefaultSummary(paths);
        return result.taskExecuted() ? 0 : 1;

    }
}
