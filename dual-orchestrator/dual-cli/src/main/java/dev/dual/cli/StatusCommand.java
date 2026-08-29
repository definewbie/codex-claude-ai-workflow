package dev.dual.cli;

import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "status", description = "Show workstream status.", mixinStandardHelpOptions = true)
public final class StatusCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--show-legacy", description = "Also print the compatibility checkpoint snapshot.")
    boolean showLegacy;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var status = new FileLedgerStore().loadStatus(workdir.toAbsolutePath().normalize(), workstreamId);

        System.out.println("Workstream: " + status.workstream().workstreamId());
        System.out.println("Workspace: " + status.workspace().effectiveWorkdir());
        System.out.println("Branch: " + status.workspace().branch());
        System.out.println("Ledger: " + status.workstreamDir());

        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        var hasWorkflowCurrent = Files.exists(paths.workflowCurrentJson());
        if (hasWorkflowCurrent) {
            var workflowLedger = CliLedgerFactory.create();
            var current = workflowLedger.loadCurrent(paths);
            System.out.println();
            System.out.println("Workflow:");
            System.out.println("  Definition: " + current.workflowId() + "@" + current.definitionVersion());
            System.out.println("  Status: " + current.status());
            System.out.println("  Active nodes: " + current.activeNodeInstanceIds());
            System.out.println("  Open gates: " + current.openGateIds());
            if (!current.activeNodeInstanceIds().isEmpty()) {
                var node = workflowLedger.loadNode(paths, current.activeNodeInstanceIds().get(0));
                System.out.println("  Active node definition: " + node.nodeDefinitionId());
                System.out.println("  Active node status: " + node.status());
                System.out.println("  Active iteration: " + node.iterationId());
                if (node.status() == dev.dual.core.workflow.NodeStatus.RUNNING) {
                    System.out.println("  Running PID: " + runningPidStatus(node.result().get("running_pid")));
                }
                if (current.status() == dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_RECOVERY) {
                    var attention = WorkstreamAttentionEvaluator.evaluate(paths);
                    System.out.println("  Recovery: " + attention.title());
                    System.out.println("  Recovery detail: " + attention.detail());
                    System.out.println("  Next action: " + attention.nextAction());
                    if (!attention.command().isBlank()) {
                        System.out.println("  Command: " + attention.command());
                    }
                }
            }
        }

        var delivery = new DeliveryApplicationService().view(workspaceRoot, workstreamId, null);
        System.out.println();
        System.out.println("Delivery:");
        System.out.println("  Status: " + delivery.status());
        if ("WORKFLOW_NOT_DELIVERABLE".equals(delivery.reasonCode())) {
            System.out.println("  Eligibility: Current workflow is not yet deliverable.");
        }
        if (!delivery.reasonCode().isBlank() || !delivery.reason().isBlank()) {
            System.out.println("  Reason: " + delivery.reasonCode() + " - " + delivery.reason());
        }
        if (!delivery.targetRepo().isBlank()) System.out.println("  Target repo: " + delivery.targetRepo());
        if (!delivery.candidateRef().isBlank()) System.out.println("  Candidate: " + delivery.candidateRef());
        if (!delivery.candidateSha256().isBlank()) System.out.println("  Candidate SHA-256: " + delivery.candidateSha256());
        System.out.println("  Changed files: " + delivery.changedFiles().size());
        if (!delivery.commit().isBlank()) System.out.println("  Commit: " + delivery.commit());
        System.out.println("  Can merge: " + delivery.canMerge());
        System.out.println("  Can finalize: " + delivery.canFinalize());

        if (showLegacy || !hasWorkflowCurrent) {
            System.out.println();
            System.out.println("Compatibility snapshot (legacy, may be stale):");
            System.out.println("  Status: " + status.workstream().status());
            System.out.println("  Stage: " + status.stage().type() + " (" + status.stage().status() + ")");
            System.out.println("  Iteration: " + status.iteration().iterationId() + " (" + status.iteration().status() + ")");
            System.out.println("  Checkpoint: " + status.checkpoint().checkpointId());
            System.out.println("  Pending action: " + status.checkpoint().pendingAction());
            System.out.println("  Resume command: " + status.checkpoint().resumeCommand());
        }
        return 0;
    }

    private static String runningPidStatus(Object value) {
        if (!(value instanceof Number number)) {
            return "(missing)";
        }
        var pid = number.longValue();
        var alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        return pid + (alive ? " (alive)" : " (stale)");
    }
}
