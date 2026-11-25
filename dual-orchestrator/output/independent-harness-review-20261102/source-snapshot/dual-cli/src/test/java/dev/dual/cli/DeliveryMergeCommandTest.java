package dev.dual.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryMergeCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void refusesLegacyAcceptanceThatIsNotBoundToCandidateBytes() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/ws-merge");
        Files.writeString(source.resolve("README.md"), "accepted change\n", StandardCharsets.UTF_8);
        writeCompletedLedger(source, target, "ws-merge");

        var command = new DeliveryMergeCommand();
        command.workdir = source;
        command.target = target;
        command.workstreamId = "ws-merge";

        var view = new dev.dual.runtime.delivery.DeliveryApplicationService(CliLedgerFactory.create())
                .view(source, "ws-merge", target);
        assertEquals("LEGACY_NOT_BOUND", view.reasonCode(), view.toString());
        assertEquals(1, command.call());
        assertEquals("base\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
        assertTrue(git(target, "diff", "--cached", "--name-only").isBlank());
        assertTrue(!Files.exists(source.resolve(".agent/workstreams/ws-merge/delivery/merge-plan.json")),
                "no accepted candidate means no Git intent or durable delivery plan");
    }

    @Test
    void resolveIsRegisteredAndMalformedDecisionCannotCreateAnyOperation() throws Exception {
        var cli = new picocli.CommandLine(new DeliveryCommand());
        assertTrue(cli.getSubcommands().containsKey("resolve"));
        var file = tempDir.resolve("decision.json"); Files.writeString(file, "{\"schema\":1,\"schema\":2}");
        var command = new DeliveryResolveCommand(); command.workdir = tempDir;
        command.workstreamId = "ws-malformed"; command.decision = file;
        assertEquals(1, command.call());
        assertTrue(!Files.exists(tempDir.resolve(".agent/workstreams/ws-malformed/delivery/merge-plan.json")));
    }

    private void writeCompletedLedger(Path source, Path target, String workstreamId) throws Exception {
        var root = source.resolve(".agent/workstreams").resolve(workstreamId);
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("domain"));
        Files.createDirectories(root.resolve("runs/run-1/outputs"));
        var paths = new dev.dual.ledger.file.WorkstreamPaths(source, new dev.dual.core.id.DualId(workstreamId));
        var ledger = CliLedgerFactory.create();
        var definition = new dev.dual.core.workflow.WorkflowDefinition("delivery-fixture", 1, "finish",
                java.util.List.of(new dev.dual.core.workflow.NodeDefinition("finish", dev.dual.core.workflow.NodeKind.TASK,
                        "noop.task", java.util.Map.of())), java.util.List.of());
        var workspace = new dev.dual.core.id.DualId("workspace-" + workstreamId);
        ledger.initialize(paths, dev.dual.core.domain.Workstream.created(paths.workstreamId(), "delivery fixture",
                        new dev.dual.core.domain.Workstream.RepositoryRef(target, target), workspace, java.time.Instant.now()),
                dev.dual.core.domain.Workspace.primaryGitWorktree(workspace, paths.workstreamId(), target, source,
                        "ai/" + workstreamId, "main", git(target, "rev-parse", "HEAD").trim()),
                "deliver the accepted candidate", dev.dual.ledger.file.WorkstreamSettingsDocument.of("auto"),
                "wf-" + workstreamId, definition);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", java.util.List.of(), java.util.Map.of());
        Files.writeString(root.resolve("domain/completion-audit.json"), """
                {
                  "schemaVersion": 1,
                  "workflowStatus": "COMPLETED",
                  "lifecycleState": "ENDED",
                  "status": "ready"
                }
                """, StandardCharsets.UTF_8);
    }

    private Path createRepo() throws Exception {
        var repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "dual@example.test");
        git(repo, "config", "user.name", "Dual Test");
        Files.writeString(repo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        git(repo, "add", "README.md");
        git(repo, "commit", "-m", "init");
        return repo;
    }

    private String git(Path workdir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor();
        if (exit != 0) {
            throw new AssertionError("git failed: git " + String.join(" ", args) + "\n" + output);
        }
        return output;
    }
}
