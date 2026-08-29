package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "status", description = "Show workstream status.", mixinStandardHelpOptions = true)
public final class StatusCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

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

        var deliveryJson = paths.workstreamDir().resolve("delivery").resolve("delivery.json");
        if (Files.exists(deliveryJson)) {
            var delivery = OBJECT_MAPPER.readValue(deliveryJson.toFile(), MAP_TYPE);
            System.out.println();
            System.out.println("Delivery:");
            System.out.println("  Status: " + text(delivery.get("status")));
            if (Boolean.TRUE.equals(delivery.get("commitCreated"))) {
                System.out.println("  Commit: " + text(delivery.get("commit")));
            }
            if (Boolean.TRUE.equals(delivery.get("prCreated"))) {
                System.out.println("  PR URL: " + text(delivery.get("prUrl")));
            }
            if (delivery.containsKey("updatedAt")) {
                System.out.println("  Updated at: " + text(delivery.get("updatedAt")));
            }
        }
        if (Files.exists(paths.deliveryMergePlanJson())) {
            var merge = OBJECT_MAPPER.readValue(paths.deliveryMergePlanJson().toFile(), MAP_TYPE);
            System.out.println();
            System.out.println("Delivery Merge:");
            System.out.println("  Status: " + text(merge.get("status")));
            System.out.println("  Target repo: " + text(merge.get("targetRepo")));
            System.out.println("  Candidate: " + text(merge.get("candidateRef")));
            System.out.println("  Candidate SHA-256: " + text(merge.get("candidateSha256")));
            System.out.println("  Changed files: " + sizeOf(merge.get("changedFiles")));
            if (!text(merge.get("failureReason")).isBlank()) {
                System.out.println("  Failure: " + text(merge.get("failureCode"))
                        + " - " + text(merge.get("failureReason")));
            }
            if (merge.containsKey("updatedAt")) {
                System.out.println("  Updated at: " + text(merge.get("updatedAt")));
            }
        }

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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int sizeOf(Object value) {
        return value instanceof java.util.List<?> list ? list.size() : 0;
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
