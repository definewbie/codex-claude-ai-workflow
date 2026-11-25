package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.OperatorWorkspaceRegistry;
import dev.dual.ledger.file.WorkflowCurrentDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.HumanGateSemantics;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;
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
        var attemptedRecoveryKeys = new LinkedHashSet<String>();

        System.out.println("Autorun started");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Workspace: " + workspaceRoot);
        registerOperatorWorkspace(workspaceRoot);

        while (true) {
            var snapshot = loadSnapshot(paths);
            printSnapshot(snapshot);

            if (snapshot.current().status() == WorkflowStatus.COMPLETED) {
                refreshSummary(paths);
                registerOperatorWorkspace(workspaceRoot);
                System.out.println("Autorun completed.");
                return 0;
            }
            if (shouldRunBeforeStop(snapshot.current().status())) {
                if (isRepeatedRecovery(snapshot.node(), attemptedRecoveryKeys)) {
                    refreshSummary(paths);
                    registerOperatorWorkspace(workspaceRoot);
                    printStopped(snapshot.current().status(), workspaceRoot);
                    return 1;
                }
                var exitCode = runUntilPause(workspaceRoot, paths);
                if (shouldStopAfterRun(exitCode, paths, attemptedRecoveryKeys)) {
                    return exitCode;
                }
                continue;
            }
            if (isStopped(snapshot.current().status())) {
                refreshSummary(paths);
                registerOperatorWorkspace(workspaceRoot);
                printStopped(snapshot.current().status(), workspaceRoot);
                return 1;
            }

            if (snapshot.current().status() == WorkflowStatus.WAITING_FOR_HUMAN) {
                if (snapshot.node() == null) {
                    throw new IllegalStateException("workflow is waiting for human but has no active gate node");
                }
                if (requiresHumanDecision(
                        snapshot.gateConfig(),
                        snapshot.node().nodeDefinitionId(),
                        snapshot.sourceOutcome()
                )) {
                    refreshSummary(paths);
                    registerOperatorWorkspace(workspaceRoot);
                    System.out.println("Autorun stopped: the current review requires a human decision.");
                    System.out.println("Use the UI or dualctl review decide --action approve|revise; include --note when revising or resolving a blocker.");
                    return 1;
                }
                gateCount++;
                if (gateCount > maxGates) {
                    refreshSummary(paths);
                    registerOperatorWorkspace(workspaceRoot);
                    System.out.println("Stopped after --max-gates=" + maxGates + ". Review the workflow before continuing.");
                    return 1;
                }
                if (isRepeatedGate(snapshot.node(), resolvedGateKeys)) {
                    refreshSummary(paths);
                    registerOperatorWorkspace(workspaceRoot);
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
            if (shouldStopAfterRun(exitCode, paths, attemptedRecoveryKeys)) {
                return exitCode;
            }
        }
    }

    private boolean shouldStopAfterRun(
            int exitCode,
            WorkstreamPaths paths,
            LinkedHashSet<String> attemptedRecoveryKeys
    ) throws Exception {
        if (exitCode == 0) {
            return false;
        }
        var after = loadSnapshot(paths);
        return !shouldContinueAfterRunFailure(after.current().status(), after.node(), attemptedRecoveryKeys);
    }

    private Snapshot loadSnapshot(WorkstreamPaths paths) throws Exception {
        try (var snapshot = CliLedgerFactory.create().readSession(paths)) {
            var ledger = snapshot.ledger();
            var current = ledger.loadCurrent(paths);
            NodeInstanceDocument node = null;
            if (!current.activeNodeInstanceIds().isEmpty()) {
                node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            }
            var sourceOutcome = "";
            var gateConfig = java.util.Map.<String, Object>of();
            if (node != null && node.kind() == dev.dual.core.workflow.NodeKind.GATE) {
                var value = ledger.loadOpenGateForNode(paths, node.nodeInstanceId()).result().get("source_outcome");
                if (value == null || String.valueOf(value).isBlank()) {
                    throw new IllegalStateException("open gate is missing source_outcome: " + node.nodeInstanceId());
                }
                sourceOutcome = String.valueOf(value);
                gateConfig = ledger.loadDefinition(paths).node(node.nodeDefinitionId()).config();
            }
            return new Snapshot(current, node, sourceOutcome, gateConfig);
        }
    }

    private void resolveGate(WorkstreamPaths paths, NodeInstanceDocument node, int gateCount) throws Exception {
        if (node == null) {
            throw new IllegalStateException("workflow is waiting for human but has no active gate node");
        }
        var action = gateActionFor(node.nodeDefinitionId());
        System.out.println("Gate " + gateCount + ": " + node.nodeDefinitionId() + " -> " + action);
        // Bind to the occurrence autorun inspected; if a person or another session moved the gate meanwhile, refuse.
        dev.dual.runtime.handler.ReviewGateActions.resolve(CliLedgerFactory.create(), paths, action,
                "auto-resolved from autorun", node.nodeInstanceId());
    }

    private int runUntilPause(Path workspaceRoot, WorkstreamPaths paths) throws Exception {
        System.out.println("Running workflow until next pause...");
        var heartbeat = new AtomicBoolean(true);
        var heartbeatThread = progressHeartbeat(workspaceRoot, heartbeat);
        heartbeatThread.start();
        try {
            return new CliWorkflowLoop().run(workspaceRoot, workstreamId, maxSteps);
        } finally {
            heartbeat.set(false);
            heartbeatThread.interrupt();
        }
    }

    private Thread progressHeartbeat(Path workspaceRoot, AtomicBoolean running) {
        var thread = new Thread(() -> {
            var started = System.currentTimeMillis();
            while (running.get()) {
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                if (running.get()) {
                    var elapsedSeconds = Math.max(30, (System.currentTimeMillis() - started) / 1000);
                    System.out.println("Still running (" + elapsedSeconds + "s). Trace: dualctl trace --workdir "
                            + workspaceRoot + " " + workstreamId);
                }
            }
        }, "dual-autorun-heartbeat");
        thread.setDaemon(true);
        return thread;
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

    private void registerOperatorWorkspace(Path workspaceRoot) {
        try {
            new OperatorWorkspaceRegistry().registerFromLedger(CliLedgerFactory.create(), workspaceRoot, workstreamId);
        } catch (Exception e) {
            System.err.println("Warning: failed to update operator workspace registry: " + e.getMessage());
        }
    }

    private static boolean isStopped(WorkflowStatus status) {
        return status == WorkflowStatus.WAITING_FOR_RECOVERY
                || status == WorkflowStatus.BLOCKED
                || status == WorkflowStatus.FAILED;
    }

    static boolean shouldRunBeforeStop(WorkflowStatus status) {
        return status == WorkflowStatus.WAITING_FOR_RECOVERY;
    }

    static boolean isRepeatedRecovery(NodeInstanceDocument node, LinkedHashSet<String> attemptedRecoveryKeys) {
        if (node == null) {
            return true;
        }
        return !attemptedRecoveryKeys.add(recoveryKey(node));
    }

    static boolean shouldContinueAfterRunFailure(
            WorkflowStatus status,
            NodeInstanceDocument node,
            LinkedHashSet<String> attemptedRecoveryKeys
    ) {
        return shouldRunBeforeStop(status) && !hasAttemptedRecovery(node, attemptedRecoveryKeys);
    }

    static String recoveryKey(NodeInstanceDocument node) {
        return node.nodeInstanceId();
    }

    static boolean requiresHumanDecision(
            java.util.Map<String, Object> gateConfig,
            String nodeDefinitionId,
            String sourceOutcome
    ) {
        return HumanGateSemantics.requiresOperatorDecision(gateConfig, nodeDefinitionId, sourceOutcome);
    }

    static boolean hasAttemptedRecovery(NodeInstanceDocument node, LinkedHashSet<String> attemptedRecoveryKeys) {
        return node == null || attemptedRecoveryKeys.contains(recoveryKey(node));
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

    private record Snapshot(
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            String sourceOutcome,
            java.util.Map<String, Object> gateConfig
    ) {
    }
}
