package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryViewServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void legacyCompletedWorkstreamIsBlockedWithoutCandidateBinding() throws Exception {
        var fixture = createDeliveryFixture("ws-delivery-ready", "ACCEPT_WITH_NOTES");
        var service = new DeliveryViewService(objectMapper);

        var view = service.view(fixture.sourceWorktree(), fixture.workstreamId());

        assertEquals("BLOCKED", view.get("status"));
        assertEquals(false, view.get("canMerge"));
        assertEquals("LEGACY_NOT_BOUND", view.get("reasonCode"));
        assertTrue(String.valueOf(view.get("targetRepo")).endsWith("target-repo"));
        assertTrue(String.valueOf(view.get("operatorNextAction")).contains("边界错误"));
    }

    @Test
    void legacyMarkdownRejectionCannotBecomeDeliveryAuthority() throws Exception {
        var fixture = createDeliveryFixture("ws-delivery-rejected", "REJECT");
        var service = new DeliveryViewService(objectMapper);

        var view = service.view(fixture.sourceWorktree(), fixture.workstreamId());

        assertEquals("BLOCKED", view.get("status"));
        assertEquals(false, view.get("canMerge"));
        assertEquals("LEGACY_NOT_BOUND", view.get("reasonCode"));
    }

    @Test
    void mergeCannotBypassMissingCandidateBinding() throws Exception {
        var fixture = createDeliveryFixture("ws-delivery-merge", "ACCEPT");
        var service = new DeliveryViewService(objectMapper);

        var view = service.merge(fixture.sourceWorktree(), fixture.workstreamId());

        assertEquals("FAILED", view.get("status"));
        assertEquals(false, view.get("canMerge"));
        assertTrue(Files.isRegularFile(fixture.paths().deliveryMergePlanJson()));
        assertTrue(git(fixture.targetRepo(), "diff", "--cached", "--", "README.md").isBlank());
    }

    private Fixture createDeliveryFixture(String workstreamId, String decision) throws Exception {
        var targetRepo = tempDir.resolve(workstreamId + "-target-repo");
        var sourceWorktree = tempDir.resolve(workstreamId + "-source");
        Files.createDirectories(targetRepo);
        git(targetRepo, "init");
        git(targetRepo, "config", "user.email", "dual@example.com");
        git(targetRepo, "config", "user.name", "Dual Test");
        Files.writeString(targetRepo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        git(targetRepo, "add", "README.md");
        git(targetRepo, "commit", "-m", "init");
        git(tempDir, "clone", targetRepo.toString(), sourceWorktree.toString());
        Files.writeString(sourceWorktree.resolve("README.md"), "base\nchanged by agent\n", StandardCharsets.UTF_8);

        var paths = new WorkstreamPaths(sourceWorktree, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        Files.writeString(paths.workstreamJson(), """
                {
                  "workstreamId": "%s",
                  "title": "delivery test",
                  "status": "COMPLETED",
                  "repository": {
                    "originalWorkdir": "%s",
                    "gitRoot": "%s"
                  }
                }
                """.formatted(workstreamId, jsonPath(targetRepo), jsonPath(targetRepo)), StandardCharsets.UTF_8);
        Files.createDirectories(paths.workflowDir());
        Files.writeString(paths.workflowCurrentJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "%s",
                  "workflowInstanceId": "wf-test",
                  "workflowId": "delivery-test",
                  "definitionVersion": 1,
                  "status": "COMPLETED",
                  "activeNodeInstanceIds": [],
                  "openGateIds": [],
                  "lastEventId": "evt-000001",
                  "updatedAt": "2026-06-01T00:00:00Z"
                }
                """.formatted(workstreamId), StandardCharsets.UTF_8);
        Files.writeString(paths.eventsJsonl(), """
                {"event_id":"evt-000001","event_type":"workflow_completed","payload":{}}
                """, StandardCharsets.UTF_8);
        Files.createDirectories(paths.domainDir());
        Files.writeString(paths.completionAuditJson(), """
                {"status": "ready", "gaps": []}
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.goalSatisfactionJson(), """
                {"domainOutcome": "SATISFIED_WITH_NOTES"}
                """, StandardCharsets.UTF_8);
        var contract = paths.runsDir().resolve("run-1/outputs/acceptance-codex-review.contract.json");
        Files.createDirectories(contract.getParent());
        Files.writeString(contract, """
                {
                  "decision": "%s",
                  "summary": "final acceptance"
                }
                """.formatted(decision), StandardCharsets.UTF_8);
        return new Fixture(workstreamId, sourceWorktree, targetRepo, paths);
    }

    private static String jsonPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String git(Path workdir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .start();
        process.getOutputStream().close();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor();
        if (exit != 0) {
            throw new AssertionError("git failed: " + String.join(" ", command) + "\n" + stdout + stderr);
        }
        return stdout;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private record Fixture(String workstreamId, Path sourceWorktree, Path targetRepo, WorkstreamPaths paths) {
    }
}
