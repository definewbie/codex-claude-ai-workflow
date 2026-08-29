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
        writeCompletedLedger(source, "ws-merge");

        var command = new DeliveryMergeCommand();
        command.workdir = source;
        command.target = target;
        command.workstreamId = "ws-merge";

        assertEquals(1, command.call());
        assertEquals("base\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
        assertTrue(git(target, "diff", "--cached", "--name-only").isBlank());
        assertTrue(Files.exists(source.resolve(".agent/workstreams/ws-merge/delivery/merge-plan.json")));
        var plan = Files.readString(source.resolve(".agent/workstreams/ws-merge/delivery/merge-plan.json"));
        assertTrue(plan.contains("\"status\" : \"FAILED\""));
        assertTrue(plan.contains("\"failureCode\" : \"LEGACY_NOT_BOUND\""));
    }

    private void writeCompletedLedger(Path source, String workstreamId) throws Exception {
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
                  "openGateIds": [],
                  "lastEventId": "evt-000001",
                  "updatedAt": "2026-08-28T00:00:00Z"
                }
                """.formatted(workstreamId, workstreamId), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("events.jsonl"),
                "{\"event_id\":\"evt-000001\",\"event_type\":\"workflow_completed\",\"payload\":{}}\n",
                StandardCharsets.UTF_8);
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
