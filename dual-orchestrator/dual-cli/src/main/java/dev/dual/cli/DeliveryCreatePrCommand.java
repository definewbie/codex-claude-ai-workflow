package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DeliveryPrCreateHandler;
import dev.dual.runtime.handler.TaskHandler;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "create-pr", description = "Create a GitHub PR using generated delivery artifacts.", mixinStandardHelpOptions = true)
public final class DeliveryCreatePrCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--base", description = "PR base branch. Defaults to ${DEFAULT-VALUE}.")
    String base = "main";

    @Option(names = "--title", description = "PR title. Defaults to the workstream requirement summary.")
    String title;

    @Option(names = "--draft", description = "Create a draft PR.")
    boolean draft;

    @Option(names = "--gh-command", hidden = true, description = "GitHub CLI command path. Defaults to gh.")
    String ghCommand = "gh";

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "delivery create-pr"))) {
            var config = new LinkedHashMap<String, Object>();
            config.put("base", base);
            config.put("draft", draft);
            config.put("gh_command", ghCommand);
            if (title != null && !title.isBlank()) {
                config.put("title", title);
            }
            var result = new DeliveryPrCreateHandler().execute(new TaskHandler.TaskContext(
                    workspaceRoot,
                    workstreamId,
                    new NodeDefinition("delivery.create_pr", NodeKind.TASK, "delivery.create_pr", config),
                    new NodeInstance(
                            "node-delivery-create_pr-cli",
                            "delivery.create_pr",
                            NodeKind.TASK,
                            "delivery.create_pr",
                            NodeStatus.READY,
                            "stage-delivery",
                            currentIteration(paths),
                            List.of(),
                            List.of(),
                            Map.of(),
                            null,
                            null,
                            null
                    )
            ));
            if (result.status() != NodeStatus.COMPLETED) {
                System.err.println("PR creation failed: " + result.outcome());
                System.err.println(result.errorMessage() == null ? "" : result.errorMessage());
                return 1;
            }
            System.out.println("PR created");
            System.out.println("URL: " + result.result().get("pr_url"));
            return 0;
        }
    }

    private static String currentIteration(WorkstreamPaths paths) {
        try {
            var ledger = CliLedgerFactory.create();
            var current = ledger.loadCurrent(paths);
            if (!current.activeNodeInstanceIds().isEmpty()) {
                var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
                return node.iterationId();
            }
        } catch (Exception ignored) {
            return "iter-001";
        }
        return "iter-001";
    }
}
