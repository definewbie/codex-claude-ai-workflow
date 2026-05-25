package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
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
        if (Files.exists(paths.workflowCurrentJson())) {
            var workflowLedger = new WorkflowFileLedger();
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
                System.out.println("  Active iteration: " + node.iterationId());
                if (current.status() == dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_RECOVERY) {
                    System.out.println("  Recovery: fix the external cause, then run dualctl retry --workdir " + workspaceRoot + " " + workstreamId);
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

        System.out.println();
        System.out.println("Compatibility snapshot (legacy, may be stale):");
        System.out.println("  Status: " + status.workstream().status());
        System.out.println("  Stage: " + status.stage().type() + " (" + status.stage().status() + ")");
        System.out.println("  Iteration: " + status.iteration().iterationId() + " (" + status.iteration().status() + ")");
        System.out.println("  Checkpoint: " + status.checkpoint().checkpointId());
        System.out.println("  Pending action: " + status.checkpoint().pendingAction());
        System.out.println("  Resume command: " + status.checkpoint().resumeCommand());
        return 0;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
