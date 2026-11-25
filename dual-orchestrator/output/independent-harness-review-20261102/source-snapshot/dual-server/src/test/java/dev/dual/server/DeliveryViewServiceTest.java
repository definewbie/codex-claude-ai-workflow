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

        assertEquals("INVALID", view.get("status"));
        assertEquals(false, view.get("canMerge"));
        assertEquals("LEDGER_INVALID", view.get("reasonCode"));
        assertFalse(Files.exists(fixture.paths().deliveryMergePlanJson()));
    }

    @Test
    void legacyMarkdownRejectionCannotBecomeDeliveryAuthority() throws Exception {
        var fixture = createDeliveryFixture("ws-delivery-rejected", "REJECT");
        var service = new DeliveryViewService(objectMapper);

        var view = service.view(fixture.sourceWorktree(), fixture.workstreamId());

        assertEquals("INVALID", view.get("status"));
        assertEquals(false, view.get("canMerge"));
        assertEquals("LEDGER_INVALID", view.get("reasonCode"));
    }

    @Test
    void mergeCannotBypassMissingCandidateBinding() throws Exception {
        var fixture = createDeliveryFixture("ws-delivery-merge", "ACCEPT");
        var service = new DeliveryViewService(objectMapper);

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> service.merge(fixture.sourceWorktree(), fixture.workstreamId()));
        assertFalse(Files.exists(fixture.paths().deliveryMergePlanJson()), "invalid ledger must fail before a delivery intent or Git effect");
        assertTrue(git(fixture.targetRepo(), "diff", "--cached", "--", "README.md").isBlank());
    }

    @Test
    void runningWorkflowIsVisibleAsNotYetDeliverableWithoutMutationCommands() throws Exception {
        var ws = "ws-delivery-running";
        var paths = new WorkstreamPaths(tempDir, new DualId(ws));
        var node = new dev.dual.core.workflow.NodeDefinition("work", dev.dual.core.workflow.NodeKind.TASK, "noop.task", java.util.Map.of());
        ServerTestLedger.initialize(ServerLedgerFactory.create(), paths, "wf-test",
                new dev.dual.core.workflow.WorkflowDefinition("delivery-test", 1, "work", List.of(node), List.of()));
        var view = new DeliveryViewService(objectMapper).view(tempDir, ws);
        assertEquals("BLOCKED", view.get("status"));
        assertEquals("WORKFLOW_NOT_DELIVERABLE", view.get("reasonCode"));
        assertEquals(false, view.get("canMerge"));
        assertEquals(false, view.get("canFinalize"));
        assertTrue(strings(view.get("nextCommands")).isEmpty());
        assertFalse(Files.exists(paths.deliveryMergePlanJson()));
    }

    @Test
    void ownerActionsAreProjectedForStagingCommitAndHistoryWithoutBeingRecomputed() throws Exception {
        var f=dev.dual.runtime.delivery.DeliveryApplicationServiceTest.deliveryFixture(tempDir,"ws-owner-projection");
        var core=new dev.dual.runtime.delivery.DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger());
        var facade=new DeliveryViewService(core);
        assertEquals(List.of("MERGE"),facade.view(f.source(),f.workstreamId()).get("currentActions"));
        var applied=facade.merge(f.source(),f.workstreamId());
        assertEquals(core.view(f.source(),f.workstreamId(),null).currentActions(),applied.get("currentActions"));
        assertEquals(false,applied.get("canFinalize"));
        assertTrue(strings(applied.get("nextCommands")).stream().anyMatch(x->x.contains("commit -m")));
        git(f.target(),"commit","-m","actual projection target commit");
        String commit=git(f.target(),"rev-parse","HEAD").trim();
        assertEquals(true,facade.view(f.source(),f.workstreamId()).get("canFinalize"));
        var committed=facade.finalizeCommit(f.source(),f.workstreamId(),commit);
        assertEquals("COMMITTED",committed.get("historicalStatus"));
        assertEquals(core.view(f.source(),f.workstreamId(),null).nextCommands(),committed.get("nextCommands"));
        var commandBytes="{\"schema\":1,\"schema\":2}".getBytes(StandardCharsets.UTF_8);
        assertEquals("DELIVERY_INVALID",facade.resolve(f.source(),f.workstreamId(),commandBytes).get("reasonCode"));
    }

    @Test
    void statusAndOverviewObserveBeforeLedgerLockAndComposeWithinTheSameSnapshot() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-query-lock-order"));
        var ledger = ServerLedgerFactory.create();
        var node = new dev.dual.core.workflow.NodeDefinition(
                "work", dev.dual.core.workflow.NodeKind.TASK, "noop.task", java.util.Map.of());
        ServerTestLedger.initialize(ledger, paths, "wf-query-lock-order",
                new dev.dual.core.workflow.WorkflowDefinition("query", 1, "work", List.of(node), List.of()));
        var observed = new java.util.concurrent.atomic.AtomicInteger();
        var composed = new java.util.concurrent.atomic.AtomicInteger();
        var facade = new DeliveryViewService(objectMapper) {
            @Override
            public dev.dual.runtime.delivery.DeliveryApplicationService.View observe(Path root, String id) {
                try (var outside = ledger.readSession(paths)) {
                    observed.incrementAndGet();
                } catch (Exception held) {
                    throw new AssertionError("target observation must precede the ledger read lock", held);
                }
                return super.observe(root, id);
            }

            @Override
            public java.util.Map<String, Object> inSnapshot(
                    Path root, String id, dev.dual.runtime.delivery.DeliveryApplicationService.View sample,
                    dev.dual.ledger.file.WorkflowFileLedger scoped) throws java.io.IOException {
                org.junit.jupiter.api.Assertions.assertThrows(dev.dual.ledger.file.WorkstreamLockedException.class,
                        () -> {
                            try (var unexpected = ledger.readSession(paths)) {
                                throw new AssertionError("composition must use the already-held ledger snapshot");
                            }
                        });
                composed.incrementAndGet();
                return super.inSnapshot(root, id, sample, scoped);
            }
        };
        var query = new WorkstreamQueryService(objectMapper, facade);

        assertEquals("RUNNING", query.status(tempDir, paths.workstreamId().value()).get("status"));
        var overview = query.overview(tempDir, paths.workstreamId().value());
        assertFalse(overview.isEmpty());
        assertEquals(2, observed.get(), "both current query entrances must observe outside L");
        assertEquals(2, composed.get(), "both projections must compose within L");
        assertFalse(Files.exists(paths.deliveryMergePlanJson()), "observation must not create a delivery intent");
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
