package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "decide", description = "Apply an explicit action to the current human review gate.", mixinStandardHelpOptions = true)
public final class ReviewDecideCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--action", required = true, description = "Gate action, for example approve, approved, revise, clarify, reduce_scope, reject, or complete.")
    String action;

    @Option(names = "--note", description = "Optional decision note.")
    String note = "decided from CLI";

    @Option(names = "--run", description = "After deciding, immediately run until the next gate, recovery, failure, or completion.")
    boolean runImmediately;

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute when --run is used. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 20;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var resolvedAction = "";
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "review decide"))) {
            var ledger = CliLedgerFactory.create();
            var definition = ledger.loadDefinition(paths);
            resolvedAction = normalizeAction(paths, ledger, definition, action);
            ledger.resolveOpenGate(paths, definition, resolvedAction, note);
        }
        System.out.println("Human gate decided");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Action: " + resolvedAction);
        if (!runImmediately) {
            System.out.println("Resume: dualctl resume --workdir " + workspaceRoot + " " + workstreamId);
            return 0;
        }
        System.out.println("Running workflow until next gate...");
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "run"))) {
            return new CliWorkflowLoop().run(workspaceRoot, workstreamId, maxSteps);
        }
    }

    private static String normalizeAction(
            WorkstreamPaths paths,
            WorkflowFileLedger ledger,
            WorkflowDefinition definition,
            String requestedAction
    ) throws Exception {
        if (requestedAction == null || requestedAction.isBlank()) {
            throw new IllegalArgumentException("--action is required");
        }
        var action = requestedAction.trim();
        if (!"approve".equals(action)) {
            return action;
        }
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            return action;
        }
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        var hasApproveEdge = definition.edgesFrom(node.nodeDefinitionId()).stream()
                .anyMatch(edge -> edge.on().equals("approve"));
        var hasApprovedEdge = definition.edgesFrom(node.nodeDefinitionId()).stream()
                .anyMatch(edge -> edge.on().equals("approved"));
        if (!hasApproveEdge && hasApprovedEdge) {
            return "approved";
        }
        return action;
    }
}
