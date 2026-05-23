package dev.dual.cli;

import dev.dual.adapter.git.GitWorkspaceService;
import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualIdGenerator;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
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
@Command(name = "start", description = "Start a design-loop workstream.")
public final class StartCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Repository directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--task-file", description = "Requirement file path.")
    Path taskFile;

    @Option(names = "--branch", description = "Worktree branch name.")
    String branch;

    @Option(names = "--worktree-dir", description = "Worktree directory.")
    Path worktreeDir;

    @Parameters(index = "0", arity = "0..1", description = "Inline requirement text.")
    String inlineRequirement;

    @Override
    public Integer call() throws Exception {
        var requirement = readRequirement();
        var git = new GitWorkspaceService();
        var repo = git.inspect(workdir.toAbsolutePath().normalize());

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
            var workflowDefinition = new dev.dual.runtime.lifecycle.LifecycleWorkflowDefinitionFactory().thinMainline();
            new dev.dual.ledger.file.WorkflowFileLedger().initialize(paths, "wf-" + workstreamId.value(), workflowDefinition);
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
        System.out.println("Workflow: agent-lifecycle@1");
        System.out.println("Next: dualctl resume --workdir " + selectedWorktree + " " + workstreamId.value());
        return 0;
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
}
