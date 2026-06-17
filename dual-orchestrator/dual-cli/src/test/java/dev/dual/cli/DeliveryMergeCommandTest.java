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
    void mergesAcceptedWorktreeDiffIntoOriginalRepo() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/ws-merge");
        Files.writeString(source.resolve("README.md"), "accepted change\n", StandardCharsets.UTF_8);
        writeCompletedAcceptedLedger(source, "ws-merge", "ACCEPT_WITH_NOTES");

        var command = new DeliveryMergeCommand();
        command.workdir = source;
        command.target = target;
        command.workstreamId = "ws-merge";

        assertEquals(0, command.call());
        assertEquals("accepted change\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
        assertTrue(git(target, "diff", "--cached", "--name-only").contains("README.md"));
        assertTrue(Files.exists(source.resolve(".agent/workstreams/ws-merge/delivery/merge-plan.json")));
        assertTrue(Files.readString(source.resolve(".agent/workstreams/ws-merge/delivery/merge-plan.json"))
                .contains("\"status\" : \"APPLIED\""));
    }

    @Test
    void refusesRejectedAcceptance() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/ws-reject");
        Files.writeString(source.resolve("README.md"), "rejected change\n", StandardCharsets.UTF_8);
        writeCompletedAcceptedLedger(source, "ws-reject", "REJECT");

        var command = new DeliveryMergeCommand();
        command.workdir = source;
        command.target = target;
        command.workstreamId = "ws-reject";

        assertEquals(1, command.call());
        assertEquals("base\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(source.resolve(".agent/workstreams/ws-reject/delivery/merge-plan.json"))
                .contains("\"status\" : \"FAILED\""));
    }

    private void writeCompletedAcceptedLedger(Path source, String workstreamId, String decision) throws Exception {
        var root = source.resolve(".agent/workstreams").resolve(workstreamId);
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("domain"));
        Files.createDirectories(root.resolve("runs/run-1/outputs"));
        Files.writeString(root.resolve("workflow/current.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "%s",
                  "workflowInstanceId": "wf-%s",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 4,
                  "status": "COMPLETED",
                  "activeNodeInstanceIds": [],
                  "openGateIds": []
                }
                """.formatted(workstreamId, workstreamId), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("domain/completion-audit.json"), """
                {
                  "schemaVersion": 1,
                  "workflowStatus": "COMPLETED",
                  "lifecycleState": "ENDED",
                  "status": "ready"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("runs/run-1/outputs/acceptance-codex-review.contract.json"), """
                {
                  "schemaVersion": 1,
                  "decision": "%s"
                }
                """.formatted(decision), StandardCharsets.UTF_8);
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
