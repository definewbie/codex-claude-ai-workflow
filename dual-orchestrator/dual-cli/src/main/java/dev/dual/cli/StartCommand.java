package dev.dual.cli;

import dev.dual.adapter.git.GitWorkspaceService;
import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualIdGenerator;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Callable;

@Component
@Command(name = "start", description = "Start an agent lifecycle workstream.", mixinStandardHelpOptions = true)
public final class StartCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Repository directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--task-file", description = "Requirement file path.")
    Path taskFile;

    @Option(names = "--branch", description = "Worktree branch name.")
    String branch;

    @Option(names = "--worktree-dir", description = "Worktree directory.")
    Path worktreeDir;

    @Option(names = "--workflow", description = "Workflow definition JSON file. Defaults to built-in agent lifecycle.")
    Path workflowPath;

    @Option(names = "--workflow-variant", description = "Built-in workflow variant: full (default), contract.")
    String workflowVariant;

    @Option(names = "--allow-unknown-handlers", description = "Allow workflow handler types not built into this CLI.")
    boolean allowUnknownHandlers;

    @Option(names = "--run", description = "Start and immediately run until the next gate, recovery, failure, or completion.")
    boolean runImmediately;

    @Option(names = "--max-steps", description = "Maximum workflow nodes to execute when --run is used. Defaults to ${DEFAULT-VALUE}.")
    int maxSteps = 20;

    @Parameters(index = "0", arity = "0..1", description = "Inline requirement text.")
    String inlineRequirement;

    @Override
    public Integer call() throws Exception {
        var requirement = readRequirement();
        var git = new GitWorkspaceService();
        var repo = git.inspect(workdir.toAbsolutePath().normalize());
        var workflowDefinition = new WorkflowDefinitionLoader().loadOrDefault(
                workflowPath == null ? null : workflowPath.toAbsolutePath().normalize(),
                workflowVariant
        );
        validateHandlers(workflowDefinition);

        var ids = new DualIdGenerator(Clock.systemUTC());
        var workstreamId = ids.workstreamId();
        var workspaceId = ids.workspaceId();
        var selectedBranch = branch == null || branch.isBlank() ? "ai/" + workstreamId.value() : branch;
        var selectedWorktree = worktreeDir == null
                ? repo.gitRoot().getParent().resolve(repo.gitRoot().getFileName() + "-agent-runs").resolve(workstreamId.value())
                : worktreeDir.toAbsolutePath().normalize();

        git.createWorktree(repo.gitRoot(), selectedWorktree, selectedBranch);

        var now = Instant.now();
        var workstream = Workstream.created(
                workstreamId,
                firstLine(requirement),
                new Workstream.RepositoryRef(repo.gitRoot(), repo.gitRoot()),
                workspaceId,
                now
        );
        var workspace = Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                repo.gitRoot(),
                selectedWorktree,
                selectedBranch,
                repo.baseBranch(),
                repo.baseCommit()
        );

        var paths = new WorkstreamPaths(selectedWorktree, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId.value(), "start"))) {
            new FileLedgerStore().initializeWorkstream(paths, workstream, workspace, requirement);
            CliLedgerFactory.create().initialize(paths, "wf-" + workstreamId.value(), workflowDefinition);
        }

        System.out.println("Dual workstream started");
        System.out.println("Workstream: " + workstreamId.value());
        System.out.println("Stage: design");
        System.out.println("Mode: worktree");
        System.out.println("Original repo: " + repo.gitRoot());
        System.out.println("Workspace: " + selectedWorktree);
        System.out.println("Branch: " + selectedBranch);
        System.out.println();
        System.out.println("Ledger: " + paths.workstreamDir());
        System.out.println("Workflow: " + workflowDefinition.workflowId() + "@" + workflowDefinition.version());
        if (workflowVariant != null && !workflowVariant.isBlank()) {
            System.out.println("Variant: " + workflowVariant);
        }
        if (workflowPath != null) {
            System.out.println("Workflow source: " + workflowPath.toAbsolutePath().normalize());
        }
        if (!runImmediately) {
            System.out.println("Next: dualctl run --workdir " + selectedWorktree + " " + workstreamId.value());
            return 0;
        }

        System.out.println();
        System.out.println("Running workflow until next gate...");
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId.value(), "run"))) {
            return new CliWorkflowLoop().run(selectedWorktree, workstreamId.value(), maxSteps);
        }
    }

    private String readRequirement() throws Exception {
        if (taskFile != null && inlineRequirement != null) {
            throw new IllegalArgumentException("Use either --task-file or inline requirement text, not both.");
        }
        if (taskFile != null) {
            return Files.readString(taskFile);
        }
        if (inlineRequirement != null && !inlineRequirement.isBlank()) {
            return inlineRequirement + System.lineSeparator();
        }
        throw new IllegalArgumentException("Requirement is required. Use --task-file or inline text.");
    }

    private String firstLine(String requirement) {
        return requirement.lines()
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("Untitled workstream");
    }

    private void validateHandlers(dev.dual.core.workflow.WorkflowDefinition definition) {
        var unknownHandlers = definition.nodes().stream()
                .map(node -> node.handlerType())
                .distinct()
                .sorted()
                .filter(handler -> !CliSupportedHandlers.TYPES.contains(handler))
                .toList();
        if (!unknownHandlers.isEmpty() && !allowUnknownHandlers) {
            throw new IllegalArgumentException(
                    "workflow contains handler types not built into this CLI: " + unknownHandlers
                            + ". Use --allow-unknown-handlers if these handlers are provided by an external runtime."
            );
        }
    }
}
