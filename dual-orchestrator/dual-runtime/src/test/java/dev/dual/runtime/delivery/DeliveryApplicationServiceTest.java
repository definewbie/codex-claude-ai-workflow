package dev.dual.runtime.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.git.GitPatchMergeService;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.DeliveryCandidateDocument;
import dev.dual.ledger.file.DeliveryCandidateWriter;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryApplicationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void appliesOnlyTheHumanAcceptedCandidateAndFinalizesItsExactCommitTree() throws Exception {
        var fixture = fixture("ws-delivery");
        var service = new DeliveryApplicationService();

        var ready = service.view(fixture.source(), fixture.workstreamId(), fixture.target());
        assertEquals("READY", ready.status());
        assertEquals(fixture.candidateRef(), ready.candidateRef());

        var applied = service.apply(fixture.source(), fixture.workstreamId(), fixture.target());
        assertEquals("APPLIED", applied.status(), applied.reasonCode() + ": " + applied.reason());
        assertEquals("accepted change\n", Files.readString(fixture.target().resolve("README.md")));
        assertEquals(fixture.expectedTree(), git(fixture.target(), "write-tree").trim());

        git(fixture.target(), "commit", "-m", "accepted delivery");
        var commit = git(fixture.target(), "rev-parse", "HEAD").trim();
        var committed = service.finalizeCommit(fixture.source(), fixture.workstreamId(), commit);
        assertEquals("COMMITTED", committed.status());
        assertEquals(commit, committed.commit());
    }

    @Test
    void rejectsSourceMutationAfterHumanAcceptanceWithoutTouchingTarget() throws Exception {
        var fixture = fixture("ws-drift");
        Files.writeString(fixture.source().resolve("README.md"), "mutated after approval\n");

        var service = new DeliveryApplicationService();
        var result = service.apply(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("FAILED", result.status());
        assertEquals("SOURCE_CANDIDATE_DRIFT", result.reasonCode());
        assertEquals("SOURCE_CANDIDATE_DRIFT",
                new DeliveryApplicationService().view(fixture.source(), fixture.workstreamId(), fixture.target()).reasonCode());
        assertEquals("base\n", Files.readString(fixture.target().resolve("README.md")));
        assertTrue(git(fixture.target(), "status", "--porcelain").isBlank());
    }

    @Test
    void refusesToFinalizeACommitWhoseTreeExceedsTheAcceptedCandidate() throws Exception {
        var fixture = fixture("ws-finalize-drift");
        var service = new DeliveryApplicationService();
        assertEquals("APPLIED", service.apply(fixture.source(), fixture.workstreamId(), fixture.target()).status());
        Files.writeString(fixture.target().resolve("extra.txt"), "not approved\n");
        git(fixture.target(), "add", "extra.txt");
        git(fixture.target(), "commit", "-m", "contains extra bytes");
        var commit = git(fixture.target(), "rev-parse", "HEAD").trim();

        var result = service.finalizeCommit(fixture.source(), fixture.workstreamId(), commit);

        assertEquals("APPLIED", result.status());
        assertEquals("TARGET_NOT_ELIGIBLE", result.reasonCode());
        assertTrue(result.reason().contains("tree does not match"));
        assertEquals("TARGET_NOT_ELIGIBLE",
                new DeliveryApplicationService().view(fixture.source(), fixture.workstreamId(), fixture.target()).reasonCode());
    }

    @Test
    void legacyCompletedWorkstreamWithoutCandidateIsExplicitlyBlocked() throws Exception {
        var fixture = fixture("ws-legacy");
        var paths = new WorkstreamPaths(fixture.source(), new DualId("ws-legacy"));
        var gateNodePath = paths.nodeJson("node-gate-acceptance-001");
        var gateNode = mapper.readValue(gateNodePath.toFile(), NodeInstanceDocument.class);
        mapper.writeValue(gateNodePath.toFile(), new NodeInstanceDocument(
                gateNode.schemaVersion(), gateNode.nodeInstanceId(), gateNode.nodeDefinitionId(), gateNode.kind(),
                gateNode.handlerType(), gateNode.status(), gateNode.stageId(), gateNode.iterationId(), List.of(),
                gateNode.inputTransition(), gateNode.outputRefs(), gateNode.result(), gateNode.runRef(),
                gateNode.latestExecutionId(), gateNode.startedAt(), gateNode.finishedAt()
        ));
        var gatePath = paths.gateJson("gate-001");
        var gate = mapper.readValue(gatePath.toFile(), GateDocument.class);
        mapper.writeValue(gatePath.toFile(), new GateDocument(
                gate.schemaVersion(), gate.gateId(), gate.nodeInstanceId(), gate.gateType(), gate.status(),
                gate.allowedActions(), gate.openedAt(), gate.resolvedAt(),
                Map.of("human_action", "approved", "subject_input_refs", List.of())
        ));

        var result = new DeliveryApplicationService().view(fixture.source(), "ws-legacy", null);

        assertEquals("BLOCKED", result.status());
        assertEquals("LEGACY_NOT_BOUND", result.reasonCode());
    }

    @Test
    void lastRevisedAcceptanceCannotFallBackToEarlierApprovedCandidate() throws Exception {
        var fixture = fixture("ws-revised");
        var paths = fixture.paths();
        var revisedNodeId = "node-gate-acceptance-002";
        mapper.writeValue(paths.nodeJson(revisedNodeId).toFile(), new NodeInstanceDocument(
                1, revisedNodeId, "gate.human_acceptance", NodeKind.GATE, "human.review", NodeStatus.COMPLETED,
                "stage-acceptance", "iter-002", List.of(fixture.candidateRef()), Map.of(), List.of(),
                Map.of("human_action", "revise"), "", "", Instant.parse("2026-08-28T00:00:03Z"),
                Instant.parse("2026-08-28T00:00:04Z")
        ));
        mapper.writeValue(paths.gateJson("gate-002").toFile(), new GateDocument(
                1, "gate-002", revisedNodeId, "human.review", "resolved", List.of("approve", "revise"),
                Instant.parse("2026-08-28T00:00:03Z"), Instant.parse("2026-08-28T00:00:04Z"),
                Map.of("human_action", "revise", "subject_input_refs", List.of(fixture.candidateRef()))
        ));
        Files.writeString(paths.eventsJsonl(), """
                {"event_id":"evt-000003","event_type":"node_routed","payload":{"to":"gate.human_acceptance","gate_id":"gate-002","node_instance_id":"%s"}}
                """.formatted(revisedNodeId), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        var result = new DeliveryApplicationService().view(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", result.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", result.reasonCode());
        assertTrue(result.reason().contains("not approved"));
    }

    @Test
    void tamperedCandidateIsRejectedBeforeTargetWrite() throws Exception {
        var fixture = fixture("ws-candidate-tamper");
        var candidate = fixture.source().resolve(fixture.candidateRef());
        Files.writeString(candidate, Files.readString(candidate) + "\n");

        var result = new DeliveryApplicationService().apply(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("FAILED", result.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", result.reasonCode());
        assertEquals("base\n", Files.readString(fixture.target().resolve("README.md")));
        assertTrue(git(fixture.target(), "status", "--porcelain").isBlank());
    }

    @Test
    void deletingCandidateAfterApplyBlocksViewAndFinalize() throws Exception {
        var fixture = fixture("ws-candidate-delete");
        var service = new DeliveryApplicationService();
        assertEquals("APPLIED", service.apply(fixture.source(), fixture.workstreamId(), fixture.target()).status());
        Files.delete(fixture.source().resolve(fixture.candidateRef()));

        var view = new DeliveryApplicationService().view(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", view.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", view.reasonCode());
        var finalized = service.finalizeCommit(fixture.source(), fixture.workstreamId(), "deadbeef");
        assertEquals("APPLIED", finalized.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", finalized.reasonCode());
        assertEquals("APPLIED", mapper.readTree(fixture.paths().deliveryMergePlanJson().toFile()).path("status").asText());
    }

    @Test
    void excludedContentMayChangeButExcludedPathSetMayNot() throws Exception {
        var contentOnly = fixture("ws-excluded-content");
        Files.writeString(contentOnly.source().resolve("local.log"), "changed again but excluded\n");

        var applied = new DeliveryApplicationService().apply(
                contentOnly.source(), contentOnly.workstreamId(), contentOnly.target());

        assertEquals("APPLIED", applied.status(), applied.reason());
        assertEquals("base excluded\n", Files.readString(contentOnly.target().resolve("local.log")));

        var pathDrift = fixture("ws-excluded-path");
        Files.writeString(pathDrift.source().resolve("second.log"), "new excluded path\n");

        var rejected = new DeliveryApplicationService().apply(
                pathDrift.source(), pathDrift.workstreamId(), pathDrift.target());

        assertEquals("FAILED", rejected.status());
        assertEquals("SOURCE_CANDIDATE_DRIFT", rejected.reasonCode());
        assertEquals("base\n", Files.readString(pathDrift.target().resolve("README.md")));
    }

    private Fixture fixture(String workstreamId) throws Exception {
        var target = tempDir.resolve(workstreamId + "-target");
        var source = tempDir.resolve(workstreamId + "-source");
        Files.createDirectories(target);
        git(target, "init");
        git(target, "config", "user.email", "dual@example.test");
        git(target, "config", "user.name", "Dual Test");
        Files.writeString(target.resolve("README.md"), "base\n");
        Files.writeString(target.resolve("local.log"), "base excluded\n");
        Files.writeString(target.resolve("second.log"), "second excluded\n");
        git(target, "add", "-f", "README.md", "local.log", "second.log");
        git(target, "commit", "-m", "base");
        git(target, "worktree", "add", source.toString(), "-b", "ai/" + workstreamId);
        Files.writeString(source.resolve("README.md"), "accepted change\n");
        Files.writeString(source.resolve("local.log"), "excluded\n");

        var paths = new WorkstreamPaths(source, new DualId(workstreamId));
        var capture = new GitPatchMergeService().capture(source);
        var occurrence = "node-delivery-prepare-001";
        var occurrenceDir = paths.deliveryCandidateDir(occurrence);
        var patch = capture.patch().getBytes(StandardCharsets.UTF_8);
        var commitMessage = "accepted delivery\n".getBytes(StandardCharsets.UTF_8);
        var prBody = "# accepted delivery\n".getBytes(StandardCharsets.UTF_8);
        var candidate = new DeliveryCandidateDocument(
                1, workstreamId, occurrence, "iter-001", "wsp-001", source.toString(),
                capture.baseCommit(), capture.baseTree(), capture.acceptedFiles(), capture.excludedFiles(),
                artifactRef(source, occurrenceDir.resolve("merge.patch"), patch),
                capture.expectedResultTree(),
                artifactRef(source, occurrenceDir.resolve("commit-message.txt"), commitMessage),
                artifactRef(source, occurrenceDir.resolve("pr-body.md"), prBody),
                Instant.parse("2026-08-28T00:00:00Z")
        );
        var published = new DeliveryCandidateWriter().publish(paths, candidate, Map.of(
                "merge.patch", patch,
                "commit-message.txt", commitMessage,
                "pr-body.md", prBody
        ));
        writeLedger(paths, target, source, published.ref(), occurrence);
        return new Fixture(workstreamId, source, target, published.ref(), capture.expectedResultTree(), paths);
    }

    private void writeLedger(WorkstreamPaths paths, Path target, Path source, String candidateRef, String producerId)
            throws Exception {
        Files.createDirectories(paths.workflowDir());
        Files.createDirectories(paths.domainDir());
        Files.createDirectories(paths.nodesDir());
        Files.createDirectories(paths.gatesDir());
        Files.writeString(paths.workstreamJson(), """
                {"schemaVersion":1,"workstreamId":"%s","repository":{"originalWorkdir":"%s","gitRoot":"%s"},"primaryWorkspaceId":"wsp-001"}
                """.formatted(paths.workstreamId().value(), json(target), json(target)));
        Files.writeString(paths.workflowCurrentJson(), """
                {"schemaVersion":1,"workstreamId":"%s","workflowInstanceId":"wf-1","workflowId":"test","definitionVersion":1,"status":"COMPLETED","activeNodeInstanceIds":[],"openGateIds":[],"lastEventId":"evt-000002","updatedAt":"2026-08-28T00:00:00Z"}
                """.formatted(paths.workstreamId().value()));
        Files.writeString(paths.completionAuditJson(), "{\"status\":\"ready\"}\n");
        var producer = new NodeInstanceDocument(
                1, producerId, "delivery.prepare_pr", NodeKind.TASK, "delivery.prepare_pr", NodeStatus.COMPLETED,
                "stage-acceptance", "iter-001", List.of(), Map.of(), List.of(candidateRef), Map.of(), "", "",
                Instant.parse("2026-08-28T00:00:00Z"), Instant.parse("2026-08-28T00:00:01Z")
        );
        var gateNodeId = "node-gate-acceptance-001";
        var gate = new NodeInstanceDocument(
                1, gateNodeId, "gate.human_acceptance", NodeKind.GATE, "human.review", NodeStatus.COMPLETED,
                "stage-acceptance", "iter-001", List.of(candidateRef), Map.of(), List.of(),
                Map.of("human_action", "approved"), "", "", Instant.parse("2026-08-28T00:00:01Z"),
                Instant.parse("2026-08-28T00:00:02Z")
        );
        mapper.writeValue(paths.nodeJson(producerId).toFile(), producer);
        mapper.writeValue(paths.nodeJson(gateNodeId).toFile(), gate);
        mapper.writeValue(paths.gateJson("gate-001").toFile(), new GateDocument(
                1, "gate-001", gateNodeId, "human.review", "resolved", List.of("approve", "revise"),
                Instant.parse("2026-08-28T00:00:01Z"), Instant.parse("2026-08-28T00:00:02Z"),
                Map.of("human_action", "approved", "subject_input_refs", List.of(candidateRef))
        ));
        Files.writeString(paths.eventsJsonl(), """
                {"event_id":"evt-000001","event_type":"node_completed","payload":{}}
                {"event_id":"evt-000002","event_type":"node_routed","payload":{"to":"gate.human_acceptance","gate_id":"gate-001","node_instance_id":"%s"}}
                """.formatted(gateNodeId));
    }

    private static DeliveryCandidateDocument.ArtifactReference artifactRef(Path root, Path path, byte[] bytes) {
        return new DeliveryCandidateDocument.ArtifactReference(
                root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString(),
                DeliveryCandidateWriter.sha256(bytes)
        );
    }

    private static String json(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String git(Path workdir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(workdir.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new AssertionError("git failed: " + command + "\n" + output);
        return output;
    }

    private record Fixture(
            String workstreamId,
            Path source,
            Path target,
            String candidateRef,
            String expectedTree,
            WorkstreamPaths paths
    ) {
    }
}
