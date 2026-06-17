package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowCurrentDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;

@Component
@Command(name = "autorun", description = "Auto-drive a workstream across approval gates until completion or recovery.", mixinStandardHelpOptions = true)
public final class AutoRunCommand implements Callable<Integer> {
    private static final String FINAL_NEXT_STAGE_GATE = "gate.human_next_stage_approval";

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute per run segment. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 80;

    @Option(names = "--max-gates", description = "Maximum human gates to auto-resolve. Defaults to ${DEFAULT-VALUE}.")
    int maxGates = 12;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        if (maxGates <= 0) {
            throw new IllegalArgumentException("--max-gates must be positive");
        }
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("--max-steps must be positive");
        }

        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var gateCount = 0;
        var resolvedGateKeys = new LinkedHashSet<String>();

        System.out.println("Autorun started");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Workspace: " + workspaceRoot);

        while (true) {
            var snapshot = loadSnapshot(paths);
            printSnapshot(snapshot);

            if (snapshot.current().status() == WorkflowStatus.COMPLETED) {
                refreshSummary(paths);
                System.out.println("Autorun completed.");
                return 0;
            }
            if (isStopped(snapshot.current().status())) {
                refreshSummary(paths);
                printStopped(snapshot.current().status(), workspaceRoot);
                return 1;
            }

            if (snapshot.current().status() == WorkflowStatus.WAITING_FOR_HUMAN) {
                gateCount++;
                if (gateCount > maxGates) {
                    refreshSummary(paths);
                    System.out.println("Stopped after --max-gates=" + maxGates + ". Review the workflow before continuing.");
                    return 1;
                }
                if (isRepeatedGate(snapshot.node(), resolvedGateKeys)) {
                    refreshSummary(paths);
                    System.out.println("Autorun stopped: repeated gate detected in the same iteration: "
                            + snapshot.node().nodeDefinitionId() + " / " + snapshot.node().iterationId());
                    System.out.println("This usually means workflow routing returned to the same gate without opening a new repair iteration.");
                    System.out.println("Review the latest reports, then approve/revise/retry manually.");
                    return 1;
                }
                resolveGate(paths, snapshot.node(), gateCount);
                resolvedGateKeys.add(gateKey(snapshot.node()));
                continue;
            }

            var exitCode = runUntilPause(workspaceRoot, paths);
            if (exitCode != 0) {
                return exitCode;
            }
        }
    }

    private Snapshot loadSnapshot(WorkstreamPaths paths) throws Exception {
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "autorun inspect"))) {
            var ledger = CliLedgerFactory.create();
            var current = ledger.loadCurrent(paths);
            NodeInstanceDocument node = null;
            if (!current.activeNodeInstanceIds().isEmpty()) {
                node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            }
            return new Snapshot(current, node);
        }
    }

    private void resolveGate(WorkstreamPaths paths, NodeInstanceDocument node, int gateCount) throws Exception {
        if (node == null) {
            throw new IllegalStateException("workflow is waiting for human but has no active gate node");
        }
        var action = gateActionFor(node.nodeDefinitionId());
        System.out.println("Gate " + gateCount + ": " + node.nodeDefinitionId() + " -> " + action);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "autorun gate"))) {
            var ledger = CliLedgerFactory.create();
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), action, "auto-resolved from autorun");
        }
    }

    private int runUntilPause(Path workspaceRoot, WorkstreamPaths paths) throws Exception {
        System.out.println("Running workflow until next pause...");
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "autorun run"))) {
            return new CliWorkflowLoop().run(workspaceRoot, workstreamId, maxSteps);
        }
    }

    private static void printSnapshot(Snapshot snapshot) {
        var active = snapshot.node() == null ? "(none)" : snapshot.node().nodeDefinitionId();
        System.out.println("Status: " + snapshot.current().status() + "; active=" + active
                + "; open gates=" + snapshot.current().openGateIds());
    }

    private void printStopped(WorkflowStatus status, Path workspaceRoot) {
        System.out.println("Autorun stopped: " + status);
        if (status == WorkflowStatus.WAITING_FOR_RECOVERY) {
            System.out.println("Next: dualctl recovery show --workdir " + workspaceRoot + " " + workstreamId);
            System.out.println("Then: dualctl retry --workdir " + workspaceRoot + " " + workstreamId);
        }
    }

    private void refreshSummary(WorkstreamPaths paths) {
        try {
            SummaryCommand.writeDefaultSummary(paths);
        } catch (Exception e) {
            System.err.println("Warning: failed to refresh workstream summary: " + e.getMessage());
        }
    }

    private static boolean isStopped(WorkflowStatus status) {
        return status == WorkflowStatus.WAITING_FOR_RECOVERY
                || status == WorkflowStatus.BLOCKED
                || status == WorkflowStatus.FAILED;
    }

    static String gateActionFor(String nodeDefinitionId) {
        if (FINAL_NEXT_STAGE_GATE.equals(nodeDefinitionId)) {
            return "complete";
        }
        return "approve";
    }

    static boolean isRepeatedGate(NodeInstanceDocument node, LinkedHashSet<String> resolvedGateKeys) {
        return node != null && resolvedGateKeys.contains(gateKey(node));
    }

    static String gateKey(NodeInstanceDocument node) {
        return node.nodeDefinitionId() + "@" + node.iterationId();
    }

    private record Snapshot(WorkflowCurrentDocument current, NodeInstanceDocument node) {
    }
}
