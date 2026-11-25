package dev.dual.runtime.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.git.GitPatchMergeService;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.DeliveryCandidateDocument;
import dev.dual.ledger.file.DeliveryCandidateWriter;
import dev.dual.ledger.file.DeliveryMergePlan;
import dev.dual.ledger.file.DeliveryMergeStatus;
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

public class DeliveryApplicationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    public static Fixture deliveryFixture(Path root, String ws) throws Exception {
        var test = new DeliveryApplicationServiceTest(); test.tempDir = root; return test.fixture(ws);
    }

    @Test
    void appliesOnlyTheHumanAcceptedCandidateAndFinalizesItsExactCommitTree() throws Exception {
        var fixture = fixture("ws-delivery");
        var service = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger());

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

        var service = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger());
        var result = service.apply(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", result.status());
        assertEquals("SOURCE_CANDIDATE_DRIFT", result.reasonCode());
        assertEquals("SOURCE_CANDIDATE_DRIFT",
                new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).view(fixture.source(), fixture.workstreamId(), fixture.target()).reasonCode());
        assertEquals("base\n", Files.readString(fixture.target().resolve("README.md")));
        assertTrue(git(fixture.target(), "status", "--porcelain").isBlank());
    }

    @Test
    void refusesToFinalizeACommitWhoseTreeExceedsTheAcceptedCandidate() throws Exception {
        var fixture = fixture("ws-finalize-drift");
        var service = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger());
        assertEquals("APPLIED", service.apply(fixture.source(), fixture.workstreamId(), fixture.target()).status());
        Files.writeString(fixture.target().resolve("extra.txt"), "not approved\n");
        git(fixture.target(), "add", "extra.txt");
        git(fixture.target(), "commit", "-m", "contains extra bytes");
        var commit = git(fixture.target(), "rev-parse", "HEAD").trim();

        var result = service.finalizeCommit(fixture.source(), fixture.workstreamId(), commit);

        assertEquals("BLOCKED", result.status());
        assertEquals("TARGET_NOT_ELIGIBLE", result.reasonCode());
        var persisted = mapper.readValue(fixture.paths().deliveryMergePlanJson().toFile(), DeliveryMergePlan.class);
        assertEquals(DeliveryMergeStatus.APPLIED, persisted.status(), "a rejected finalize must not rewrite the application fact");
        assertTrue(persisted.commit().isBlank());
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

        var result = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).view(fixture.source(), "ws-legacy", null);

        assertEquals("BLOCKED", result.status());
        assertEquals("LEGACY_NOT_BOUND", result.reasonCode());
    }

    @Test
    void appliedPlanWithoutAcceptedCandidateBindingCannotBecomeAuthority() throws Exception {
        var fixture = fixture("ws-unbound-applied-plan");
        mapper.writeValue(fixture.paths().deliveryMergePlanJson().toFile(), DeliveryMergePlan.create(
                fixture.workstreamId(), fixture.source().toString(), fixture.target().toString(), "approved",
                "", "", "", "", fixture.expectedTree(), List.of("README.md"), List.of(),
                DeliveryMergeStatus.APPLIED, "", ""
        ));

        var result = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).view(
                fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", result.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", result.reasonCode());
        assertTrue(result.reason().contains("missing its accepted candidate binding"));
        assertEquals(false, result.canMerge());
        assertEquals(false, result.canFinalize());
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

        var result = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).view(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", result.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", result.reasonCode());
        assertTrue(result.reason().contains("not approved"));
    }

    @Test
    void tamperedCandidateIsRejectedBeforeTargetWrite() throws Exception {
        var fixture = fixture("ws-candidate-tamper");
        var candidate = fixture.source().resolve(fixture.candidateRef());
        Files.writeString(candidate, Files.readString(candidate) + "\n");

        var result = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).apply(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", result.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", result.reasonCode());
        assertEquals("base\n", Files.readString(fixture.target().resolve("README.md")));
        assertTrue(git(fixture.target(), "status", "--porcelain").isBlank());
    }

    @Test
    void deletingCandidateAfterApplyBlocksViewAndFinalize() throws Exception {
        var fixture = fixture("ws-candidate-delete");
        var service = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger());
        assertEquals("APPLIED", service.apply(fixture.source(), fixture.workstreamId(), fixture.target()).status());
        Files.delete(fixture.source().resolve(fixture.candidateRef()));

        var view = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).view(fixture.source(), fixture.workstreamId(), fixture.target());

        assertEquals("BLOCKED", view.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", view.reasonCode());
        var finalized = service.finalizeCommit(fixture.source(), fixture.workstreamId(), "deadbeef");
        assertEquals("BLOCKED", finalized.status());
        assertEquals("ACCEPTED_CANDIDATE_INVALID", finalized.reasonCode());
        assertEquals("APPLIED", mapper.readTree(fixture.paths().deliveryMergePlanJson().toFile()).path("status").asText());
    }

    @Test
    void excludedContentMayChangeButExcludedPathSetMayNot() throws Exception {
        var contentOnly = fixture("ws-excluded-content");
        Files.writeString(contentOnly.source().resolve("local.log"), "changed again but excluded\n");

        var applied = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).apply(
                contentOnly.source(), contentOnly.workstreamId(), contentOnly.target());

        assertEquals("APPLIED", applied.status(), applied.reason());
        assertEquals("base excluded\n", Files.readString(contentOnly.target().resolve("local.log")));

        var pathDrift = fixture("ws-excluded-path");
        Files.writeString(pathDrift.source().resolve("second.log"), "new excluded path\n");

        var rejected = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()).apply(
                pathDrift.source(), pathDrift.workstreamId(), pathDrift.target());

        assertEquals("BLOCKED", rejected.status());
        assertEquals("SOURCE_CANDIDATE_DRIFT", rejected.reasonCode());
        assertEquals("base\n", Files.readString(pathDrift.target().resolve("README.md")));
    }

    @Test
    void exactCommitCannotReplaceMissingEvidenceThatTheOldApplyStopped() throws Exception {
        var fixture = fixture("ws-exit-missing");
        var service = new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger());
        assertEquals("APPLIED", service.apply(fixture.source(), fixture.workstreamId(), null).status());
        var plan = mapper.readValue(fixture.paths().deliveryMergePlanJson().toFile(), DeliveryMergePlan.class);
        var operation = new java.util.LinkedHashMap<>(plan.operation());
        operation.put("process_exit", null);
        operation.put("process", null);
        mapper.writeValue(fixture.paths().deliveryMergePlanJson().toFile(), plan.withOperation(operation));
        git(fixture.target(), "commit", "-m", "exact candidate but lost process receipt");
        var commit = git(fixture.target(), "rev-parse", "HEAD").trim();
        assertEquals(false, service.view(fixture.source(), fixture.workstreamId(), null).canFinalize(),
                "a matching commit does not prove the old invocation stopped");
        assertEquals("BLOCKED", service.finalizeCommit(fixture.source(), fixture.workstreamId(), commit).status());
        assertEquals(commit, git(fixture.target(), "rev-parse", "HEAD").trim(), "refusal must preserve the actual commit");
    }

    @Test
    void humanStopRemainsADeclarationAndCloseStillAllowsAnExactExistingCommit() throws Exception {
        var f = fixture("ws-human-stop"); var service = service();
        assertEquals("APPLIED", service.apply(f.source(), f.workstreamId(), null).status());
        loseProcessReceipt(f);
        git(f.target(), "commit", "-m", "actual exact candidate");
        var commit = git(f.target(), "rev-parse", "HEAD").trim();
        var before = service.view(f.source(), f.workstreamId(), null);
        assertEquals("UNKNOWN", before.stopBasis()); assertEquals(false, before.canFinalize());
        byte[] request = decision(before, "CLOSE_APPLICATION", true);
        var closed = service.resolve(f.source(), f.workstreamId(), request);
        assertEquals("OPERATOR_CONFIRMED_STOPPED", closed.stopBasis(), closed.reason());
        assertTrue(closed.canFinalize(), closed.reason());
        assertEquals(null, plan(f).operation().get("process_exit"), "a declaration never manufactures a process exit");
        var fixedPlan = Files.readAllBytes(f.paths().deliveryMergePlanJson());
        assertEquals(closed.operationId(), service.resolve(f.source(), f.workstreamId(), request).operationId());
        org.junit.jupiter.api.Assertions.assertArrayEquals(fixedPlan, Files.readAllBytes(f.paths().deliveryMergePlanJson()));
        var changed = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(request);
        changed.put("operator_note", "same id but different decision");
        assertEquals("BLOCKED", service.resolve(f.source(), f.workstreamId(), mapper.writeValueAsBytes(changed)).status());
        assertEquals("COMMITTED", service.finalizeCommit(f.source(), f.workstreamId(), commit).status());
        assertEquals("COMMITTED", service.resolve(f.source(), f.workstreamId(), request).status(), "old receipt survives obsolete plan SHA");
        git(f.target(), "commit", "--allow-empty", "-m", "target later advanced");
        var historical = service.view(f.source(), f.workstreamId(), null);
        assertEquals("COMMITTED", historical.historicalStatus()); assertEquals(false, historical.canMerge());
        assertEquals(false, historical.canFinalize()); assertTrue(historical.reason().contains("historical"));
    }

    @Test
    void unknownAtBaseNeedsANewExplicitAuthorizationAndDuplicateDoesNotReapply() throws Exception {
        var f = fixture("ws-new-attempt");
        var interrupted = new DeliveryApplicationService(dev.dual.ledger.file.InterruptedTestLedger.at("after_pending_delete"));
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> interrupted.apply(f.source(), f.workstreamId(), null));
        var service = service(); var before = service.view(f.source(), f.workstreamId(), null);
        assertEquals("UNKNOWN", before.status()); assertEquals("base\n", Files.readString(f.target().resolve("README.md")));
        var oldId = before.operationId();
        assertEquals(oldId, service.apply(f.source(), f.workstreamId(), null).operationId());
        assertEquals("base\n", Files.readString(f.target().resolve("README.md")), "merge replay cannot restore fresh authority");
        assertEquals("BLOCKED", service.resolve(f.source(), f.workstreamId(), decision(before, "AUTHORIZE_NEW_APPLY", false)).status());
        byte[] request = decision(before, "AUTHORIZE_NEW_APPLY", true);
        var applied = service.resolve(f.source(), f.workstreamId(), request);
        assertEquals("APPLIED", applied.status(), applied.reason());
        org.junit.jupiter.api.Assertions.assertNotEquals(oldId, applied.operationId());
        var stable = Files.readAllBytes(f.paths().deliveryMergePlanJson());
        var replay = service.resolve(f.source(), f.workstreamId(), request);
        assertEquals(applied.operationId(), replay.operationId());
        org.junit.jupiter.api.Assertions.assertArrayEquals(stable, Files.readAllBytes(f.paths().deliveryMergePlanJson()));
        assertEquals("accepted change\n", Files.readString(f.target().resolve("README.md")));
        assertTrue(plan(f).operation().get("predecessor") instanceof Map);
    }

    @Test
    void closedApplicationCannotBeReopenedButPureCloseDoesNotInventStop() throws Exception {
        var f = fixture("ws-close-only");
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                new DeliveryApplicationService(dev.dual.ledger.file.InterruptedTestLedger.at("after_pending_delete")).apply(f.source(), f.workstreamId(), null));
        var service = service();
        var closed = service.resolve(f.source(), f.workstreamId(), decision(service.view(f.source(), f.workstreamId(), null), "CLOSE_APPLICATION", false));
        assertEquals("UNKNOWN", closed.stopBasis()); assertTrue(Boolean.TRUE.equals(plan(f).operation().get("application_closed")));
        var denied = service.resolve(f.source(), f.workstreamId(), decision(closed, "AUTHORIZE_NEW_APPLY", true));
        assertEquals("DELIVERY_CLOSED", denied.reasonCode());
        assertEquals("base\n", Files.readString(f.target().resolve("README.md")));
    }

    @Test
    void targetAliasesAreLegalButLinkedWorktreeIsNotTheApprovedTarget() throws Exception {
        var f = fixture("ws-target-alias"); var alias = tempDir.resolve("target-alias");
        Files.createSymbolicLink(alias, f.target());
        assertEquals("READY", service().view(f.source(), f.workstreamId(), alias).status());
        var other = tempDir.resolve("different-target");
        git(f.target(), "worktree", "add", "--detach", other.toString(), "HEAD");
        assertEquals("BLOCKED", service().apply(f.source(), f.workstreamId(), other).status());
        assertEquals("base\n", Files.readString(other.resolve("README.md")));
        assertEquals("APPLIED", service().apply(f.source(), f.workstreamId(), alias).status());
    }

    @Test
    void canonicalObservationIgnoresOnlyItsTopLevelClockAndPreservesEveryIdentityField() throws Exception {
        var f = fixture("ws-canonical");
        var original = new GitPatchMergeService().applicationObservation(f.target());
        var shuffled = new java.util.LinkedHashMap<String,Object>();
        var keys = new java.util.ArrayList<>(original.keySet()); java.util.Collections.reverse(keys);
        for (var key : keys) shuffled.put(key, original.get(key));
        shuffled.put("observed_at", "different sampling time");
        String identity = DeliveryApplicationService.observationSha256(original);
        assertEquals(identity, DeliveryApplicationService.observationSha256(shuffled));
        for (var key : List.of("repo", "common_dir", "head", "head_tree", "index_tree", "worktree_matches_index", "clean", "status_entries", "untracked_entries")) {
            var changed = new java.util.LinkedHashMap<>(original); changed.put(key, "different-" + key);
            org.junit.jupiter.api.Assertions.assertNotEquals(identity, DeliveryApplicationService.observationSha256(changed), key);
        }
        var a = mapper.readTree("{\"z\":1,\"nested\":{\"observed_at\":\"a\",\"x\":2}}");
        var b = mapper.readTree("{\"nested\":{\"x\":2,\"observed_at\":\"a\"},\"z\":1}");
        assertEquals(DeliveryApplicationService.canonicalSha256(a), DeliveryApplicationService.canonicalSha256(b));
        ((com.fasterxml.jackson.databind.node.ObjectNode) b.get("nested")).put("observed_at", "b");
        org.junit.jupiter.api.Assertions.assertNotEquals(DeliveryApplicationService.canonicalSha256(a), DeliveryApplicationService.canonicalSha256(b));
    }

    @Test
    void wrongDecisionIdentityAndMalformedDeclarationsProduceNoApplicationOrReceipt() throws Exception {
        var f = fixture("ws-decision-invalid");
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                new DeliveryApplicationService(dev.dual.ledger.file.InterruptedTestLedger.at("after_pending_delete")).apply(f.source(), f.workstreamId(), null));
        var before = service().view(f.source(), f.workstreamId(), null);
        var valid = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(decision(before, "AUTHORIZE_NEW_APPLY", true));
        for (var key : List.of("operation_id", "plan_sha256", "target_observation_sha256", "workstream_id")) {
            var wrong = valid.deepCopy(); wrong.put(key, "wrong");
            assertEquals("BLOCKED", service().resolve(f.source(), f.workstreamId(), mapper.writeValueAsBytes(wrong)).status(), key);
        }
        var stale = valid.deepCopy(); stale.put("ledger_commit_sequence", before.ledgerCommitSequence()-1);
        assertEquals("BLOCKED", service().resolve(f.source(), f.workstreamId(), mapper.writeValueAsBytes(stale)).status());
        var unsafe = valid.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) unsafe.get("stop_basis")).put("old_operation_stopped", false);
        assertEquals("BLOCKED", service().resolve(f.source(), f.workstreamId(), mapper.writeValueAsBytes(unsafe)).status());
        assertEquals("BLOCKED", service().resolve(f.source(), f.workstreamId(), "{\"schema\":1,\"schema\":2}".getBytes(StandardCharsets.UTF_8)).status());
        assertEquals("BLOCKED", service().resolve(f.source(), f.workstreamId(), new byte[65537]).status());
        assertEquals("base\n", Files.readString(f.target().resolve("README.md")));
        assertEquals(0, Files.readAllLines(f.paths().eventsJsonl()).stream().filter(x -> x.contains("\"event_type\":\"delivery_resolution\"")).count());
    }

    @Test
    void foreignProcessCannotBeUsedAsAProgramTerminationReceipt() throws Exception {
        var f = fixture("ws-process-origin"); service().apply(f.source(), f.workstreamId(), null);
        var old = plan(f); var operation = new java.util.LinkedHashMap<>(old.operation());
        operation.put("process", Map.of("pid", Long.MAX_VALUE, "started_at", Instant.now().toString(), "command_kind", "GIT_APPLY_INDEX"));
        operation.put("process_exit", null);
        mapper.writeValue(f.paths().deliveryMergePlanJson().toFile(), old.withOperation(operation));
        assertEquals("DELIVERY_INVALID", service().view(f.source(), f.workstreamId(), null).reasonCode());
    }

    @Test
    void eachApplicationWritePrefixCanBeRepairedInAColdJvmWithoutStartingGitAgain() throws Exception {
        int count = 0;
        for (int phase = 1; phase <= 4; phase++) {
            for (var point : List.of("before_manifest", "after_manifest", "before_replace", "after_replace", "before_append", "after_append", "before_pending_delete", "after_pending_delete")) {
                var f = fixture("ws-prefix-" + (++count));
                final long wanted = phase + (java.util.Set.of("before_pending_delete", "after_pending_delete").contains(point) ? 1 : 0);
                var faultLedger = dev.dual.ledger.file.InterruptedTestLedger.atWhen(point, () -> {
                    try { return mapper.readTree(f.paths().workflowCurrentJson().toFile()).path("commitSequence").asLong() == wanted; }
                    catch (Exception bad) { throw new RuntimeException(bad); }
                }, dev.dual.runtime.RuntimeTestLedger.ledger().frozenRuntimeIdentity());
                org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                        new DeliveryApplicationService(faultLedger).apply(f.source(), f.workstreamId(), null), phase + ":" + point);
                var sourceBefore = Files.readString(f.source().resolve("README.md"));
                var targetBefore = Files.readString(f.target().resolve("README.md"));
                var treeBefore = git(f.target(), "write-tree").trim(); var headBefore = git(f.target(), "rev-parse", "HEAD").trim();
                var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                var child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), ColdReader.class.getName(),
                        f.source().toString(), f.workstreamId()).redirectErrorStream(true).start();
                var output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(0, child.waitFor(), phase + ":" + point + "\n" + output);
                assertEquals(sourceBefore, Files.readString(f.source().resolve("README.md")));
                assertEquals(targetBefore, Files.readString(f.target().resolve("README.md")), "cold read/reconcile must not apply bytes");
                assertEquals(treeBefore, git(f.target(), "write-tree").trim()); assertEquals(headBefore, git(f.target(), "rev-parse", "HEAD").trim());
            }
        }
    }

    @Test
    void programStopUsesAnExactEventEntryNotTheMutableWholeLogHash() throws Exception {
        var f=fixture("ws-program-stop"); service().apply(f.source(),f.workstreamId(),null);
        String line=Files.readAllLines(f.paths().eventsJsonl()).stream().filter(x->x.contains("\"event_type\":\"delivery_process_exited\"")).findFirst().orElseThrow();
        var entry=mapper.readTree(line);
        var request=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(decision(service().view(f.source(),f.workstreamId(),null),"CLOSE_APPLICATION",false));
        request.set("stop_basis",mapper.valueToTree(Map.of("type","PROGRAM_OBSERVED_TERMINATION","event_ref",f.source().relativize(f.paths().eventsJsonl()).toString(),
                "event_id",entry.path("event_id").asText(),"entry_sha256",DeliveryCandidateWriter.sha256(line.getBytes(StandardCharsets.UTF_8)))));
        var wrong=request.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)wrong.get("stop_basis")).put("entry_sha256","wrong");
        assertEquals("DELIVERY_INVALID",service().resolve(f.source(),f.workstreamId(),mapper.writeValueAsBytes(wrong)).reasonCode());
        var bytes=mapper.writeValueAsBytes(request);
        assertEquals("PROGRAM_OBSERVED_TERMINATION",service().resolve(f.source(),f.workstreamId(),bytes).stopBasis());
        Files.writeString(f.paths().eventsJsonl(),"{\"event_id\":\"unrelated-later-record\",\"event_type\":\"observation\",\"payload\":{}}\n",java.nio.file.StandardOpenOption.APPEND);
        assertEquals("PROGRAM_OBSERVED_TERMINATION",service().resolve(f.source(),f.workstreamId(),bytes).stopBasis());
    }

    @Test
    void aPreparedNewAuthorizationRepairsLocallyWithoutRegainingFreshStartAuthority() throws Exception {
        int n=0;
        for (var point : List.of("before_manifest", "after_manifest", "before_replace", "after_replace", "before_append", "after_append", "before_pending_delete", "after_pending_delete")) {
            var f=fixture("ws-new-prefix-"+(++n));
            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                new DeliveryApplicationService(dev.dual.ledger.file.InterruptedTestLedger.at("after_pending_delete")).apply(f.source(),f.workstreamId(),null));
            var request=decision(service().view(f.source(),f.workstreamId(),null),"AUTHORIZE_NEW_APPLY",true);
            var faulted=new DeliveryApplicationService(dev.dual.ledger.file.InterruptedTestLedger.at(point));
            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> faulted.resolve(f.source(),f.workstreamId(),request),point);
            ColdReader.main(new String[]{f.source().toString(),f.workstreamId()}); // Same-process repair complements the separate cold JVM apply-prefix proof.
            assertEquals("base\n",Files.readString(f.target().resolve("README.md")));
            if (!point.equals("before_manifest")) {
                var operation=service().view(f.source(),f.workstreamId(),null).operationId();
                assertEquals(operation,service().resolve(f.source(),f.workstreamId(),request).operationId());
                assertEquals("base\n",Files.readString(f.target().resolve("README.md")),"fixed-byte new intent is not a live permit");
            } else assertEquals("APPLIED",service().resolve(f.source(),f.workstreamId(),request).status(),"no durable decision means the explicit first request can still commit normally");
        }
    }

    @Test
    void activeProgramObservationCannotBeOverriddenByAnOperatorDeclaration() throws Exception {
        var f = fixture("ws-active-stop"); service().apply(f.source(), f.workstreamId(), null);
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), ColdReader.class.getName(), "sleep").start();
        try {
            // Synthetic delivery-start record backed by a real live local process: observer counterexample,
            // not evidence that this process was a real Git child. Actual Git callbacks are separately tested.
            var current = plan(f); var facts = new java.util.LinkedHashMap<>(current.operation());
            facts.put("process", Map.of("pid", child.pid(), "started_at", child.info().startInstant().orElseThrow().toString(), "command_kind", "GIT_APPLY_INDEX"));
            facts.put("process_exit", null);
            var active = current.withOperation(facts);
            try (var write = dev.dual.runtime.RuntimeTestLedger.ledger().writeSession(f.paths(), "synthetic live observer counterexample", "observer")) {
                new dev.dual.ledger.file.DeliveryMergePlanWriter(write.ledger().files()).write(f.paths(), active);
                write.ledger().appendEvent(f.paths(), "test-live-start", "delivery_process_started", Map.of("operation", active.operation()));
                write.commit();
            }
            var observed = service().view(f.source(), f.workstreamId(), null);
            assertEquals("ACTIVE", observed.stopBasis()); assertTrue(observed.currentActions().isEmpty());
            // Live views intentionally expose no request template. Reconstruct a stale, formerly valid template
            // from the current binding to prove the command owner also checks activity, rather than trusting UI.
            var receiptless = active.withOperation(new java.util.LinkedHashMap<>(active.operation()));
            var temporarilyUnknown = new java.util.LinkedHashMap<>(receiptless.operation()); temporarilyUnknown.put("process", null);
            mapper.writeValue(f.paths().deliveryMergePlanJson().toFile(), receiptless.withOperation(temporarilyUnknown));
            byte[] request = decision(service().view(f.source(), f.workstreamId(), null), "AUTHORIZE_NEW_APPLY", true);
            mapper.writeValue(f.paths().deliveryMergePlanJson().toFile(), active);
            var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(request);
            json.put("plan_sha256", DeliveryCandidateWriter.sha256(Files.readAllBytes(f.paths().deliveryMergePlanJson())));
            assertEquals("DELIVERY_ACTIVE", service().resolve(f.source(), f.workstreamId(), mapper.writeValueAsBytes(json)).reasonCode());
            assertEquals("BLOCKED", service().finalizeCommit(f.source(), f.workstreamId(), git(f.target(), "rev-parse", "HEAD").trim()).status());
        } finally { child.destroyForcibly(); assertTrue(child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); }
    }

    public static final class ColdReader {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("sleep")) { Thread.sleep(30000); return; }
            var root = Path.of(args[0]); var paths = new WorkstreamPaths(root, new DualId(args[1]));
            var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
            try (var repair = ledger.writeSession(paths, "cold fixed-byte repair", "cold")) { repair.commit(); }
            var service = new DeliveryApplicationService(ledger); var before = service.view(root, args[1], null);
            if (Files.exists(paths.deliveryMergePlanJson())) {
                var after = service.apply(root, args[1], null);
                if (!before.operationId().equals(after.operationId())) throw new AssertionError("replay created a new invocation");
                if (java.util.Set.of("BLOCKED", "INVALID", "PENDING").contains(after.status())) throw new AssertionError(after);
            } else if (!"READY".equals(before.status())) throw new AssertionError(before);
        }
    }

    @Test
    void targetLockIsSharedAcrossWorkstreamsAndReadCompositionNeverTakesAnInvertedLock() throws Exception {
        var first = fixture("ws-lock-a"); var second = fixture("ws-lock-b", first.target());
        var reached = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        var paused = dev.dual.ledger.file.InterruptedTestLedger.pauseAt("before_manifest",
                dev.dual.runtime.RuntimeTestLedger.ledger().frozenRuntimeIdentity(), reached, resume);
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var application = pool.submit(() -> new DeliveryApplicationService(paused).apply(first.source(), first.workstreamId(), null));
            assertTrue(reached.await(10, java.util.concurrent.TimeUnit.SECONDS));
            try {
                var other = pool.submit(() -> service().view(second.source(), second.workstreamId(), null));
                assertEquals("BUSY", other.get(3, java.util.concurrent.TimeUnit.SECONDS).status());
                org.junit.jupiter.api.Assertions.assertThrows(dev.dual.ledger.file.WorkstreamLockedException.class,
                        () -> service().apply(second.source(), second.workstreamId(), null));
                assertEquals("base\n", Files.readString(first.target().resolve("README.md")));
            } finally { resume.countDown(); }
            assertEquals("APPLIED", application.get(10, java.util.concurrent.TimeUnit.SECONDS).status());
        }
        var sampled = service().view(first.source(), first.workstreamId(), null);
        var oldCurrent = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(first.paths().workflowCurrentJson().toFile());
        oldCurrent.put("commitSequence", sampled.ledgerCommitSequence() + 1);
        mapper.writeValue(first.paths().workflowCurrentJson().toFile(), oldCurrent);
        try (var read = dev.dual.runtime.RuntimeTestLedger.ledger().readSession(first.paths())) {
            var stale = DeliveryApplicationService.inSnapshot(sampled, read.ledger(), first.paths());
            assertEquals("DELIVERY_OBSERVATION_STALE", stale.reasonCode()); assertTrue(stale.currentActions().isEmpty());
            assertTrue(stale.resolutionTemplate().isEmpty());
        }
        assertEquals("BLOCKED", service().apply(second.source(), second.workstreamId(), null).status(), "changed shared target cannot accept another candidate");
    }

    @Test
    void closeAndFinalizeWritePrefixesRetainTheirDecisionAndNeverReapply() throws Exception {
        int number = 0;
        for (var mode : List.of("close", "finalize")) for (var point : List.of("before_manifest", "after_manifest", "before_replace", "after_replace", "before_append", "after_append", "before_pending_delete", "after_pending_delete")) {
            var f = fixture("ws-resolution-prefix-" + (++number)); var live = service();
            live.apply(f.source(), f.workstreamId(), null);
            git(f.target(), "commit", "-m", "actual approved delivery");
            String commit = git(f.target(), "rev-parse", "HEAD").trim();
            var decision = decision(live.view(f.source(), f.workstreamId(), null), "CLOSE_APPLICATION", false);
            var faulted = new DeliveryApplicationService(dev.dual.ledger.file.InterruptedTestLedger.at(point));
            org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> {
                if (mode.equals("close")) faulted.resolve(f.source(), f.workstreamId(), decision);
                else faulted.finalizeCommit(f.source(), f.workstreamId(), commit);
            }, mode + ":" + point);
            try (var repair = dev.dual.runtime.RuntimeTestLedger.ledger().writeSession(f.paths(), "fixed delivery mutation", "repair")) { repair.commit(); }
            if (mode.equals("close")) {
                var repeated = live.resolve(f.source(), f.workstreamId(), decision);
                assertEquals("PROGRAM_OBSERVED_TERMINATION", repeated.stopBasis());
                assertTrue(Boolean.TRUE.equals(plan(f).operation().get("application_closed")));
            }
            assertEquals("COMMITTED", live.finalizeCommit(f.source(), f.workstreamId(), commit).status());
            assertEquals(commit, git(f.target(), "rev-parse", "HEAD").trim());
            assertTrue(git(f.target(), "status", "--porcelain").isBlank());
        }
    }

    @Test
    void resolutionMaterialsAreFixedAsHumanDataAndNeverTreatedAsExecutableEvidence() throws Exception {
        var f = fixture("ws-human-material"); service().apply(f.source(), f.workstreamId(), null); loseProcessReceipt(f);
        var material = f.paths().workstreamDir().resolve("artifacts/stop-check.md"); Files.createDirectories(material.getParent());
        Files.writeString(material, "Operator inspected the old terminal and process list.\n");
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(decision(service().view(f.source(), f.workstreamId(), null), "CLOSE_APPLICATION", true));
        var stop = (com.fasterxml.jackson.databind.node.ObjectNode) request.get("stop_basis");
        stop.set("material_refs", mapper.valueToTree(List.of(Map.of("ref", f.source().relativize(material).toString(), "sha256", DeliveryCandidateWriter.sha256(Files.readAllBytes(material))))));
        byte[] bytes = mapper.writeValueAsBytes(request);
        assertEquals("OPERATOR_CONFIRMED_STOPPED", service().resolve(f.source(), f.workstreamId(), bytes).stopBasis());
        try (var artifacts = Files.list(material.getParent())) {
            var fixed = artifacts.filter(x -> x.getFileName().toString().startsWith("delivery-stop-")).findFirst().orElseThrow();
            assertEquals("OPERATOR_DECLARATION", mapper.readTree(fixed.toFile()).path("type").asText());
        }
        Files.delete(material);
        assertEquals("OPERATOR_CONFIRMED_STOPPED", service().resolve(f.source(), f.workstreamId(), bytes).stopBasis(), "lost mutable original does not invalidate the old decision receipt");
    }

    private DeliveryApplicationService service() { return new DeliveryApplicationService(dev.dual.runtime.RuntimeTestLedger.ledger()); }
    private DeliveryMergePlan plan(Fixture f) throws Exception { return mapper.readValue(f.paths().deliveryMergePlanJson().toFile(), DeliveryMergePlan.class); }
    private void loseProcessReceipt(Fixture f) throws Exception {
        var old = plan(f); var facts = new java.util.LinkedHashMap<>(old.operation());
        facts.put("process", null); facts.put("process_exit", null);
        mapper.writeValue(f.paths().deliveryMergePlanJson().toFile(), old.withOperation(facts));
    }
    private byte[] decision(DeliveryApplicationService.View view, String disposition, boolean confirmStopped) throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(view.resolutionTemplate());
        request.put("disposition", disposition); request.put("operator_note", "Inspected this exact target and old operation");
        if (confirmStopped) request.set("stop_basis", mapper.valueToTree(Map.of("type", "OPERATOR_CONFIRMED_STOPPED",
                "checked_at", Instant.now().toString(), "checks_performed", List.of("inspected old invocation and target"),
                "old_operation_stopped", true, "accept_unknown_result_and_repeat_risk", true, "material_refs", List.of())));
        return mapper.writeValueAsBytes(request);
    }

    private Fixture fixture(String workstreamId) throws Exception { return fixture(workstreamId, tempDir.resolve(workstreamId + "-target")); }

    private Fixture fixture(String workstreamId, Path target) throws Exception {
        var source = tempDir.resolve(workstreamId + "-source");
        if (!Files.exists(target.resolve(".git"))) {
        Files.createDirectories(target);
        git(target, "init");
        git(target, "config", "user.email", "dual@example.test");
        git(target, "config", "user.name", "Dual Test");
        Files.writeString(target.resolve("README.md"), "base\n");
        Files.writeString(target.resolve("local.log"), "base excluded\n");
        Files.writeString(target.resolve("second.log"), "second excluded\n");
        git(target, "add", "-f", "README.md", "local.log", "second.log");
        git(target, "commit", "-m", "base");
        }
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
        var frozen = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(paths.workstreamJson().toFile());
        frozen.set("frozenRuntimeIdentity", mapper.valueToTree(dev.dual.runtime.RuntimeTestLedger.ledger().frozenRuntimeIdentity()));
        mapper.writeValue(paths.workstreamJson().toFile(), frozen);
        var current = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(paths.workflowCurrentJson().toFile());
        current.put("commitSequence", 1);
        mapper.writeValue(paths.workflowCurrentJson().toFile(), current);
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
                {"event_id":"agent-task-finished-001","event_type":"agent_task_completed","payload":{}}
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

    public record Fixture(
            String workstreamId,
            Path source,
            Path target,
            String candidateRef,
            String expectedTree,
            WorkstreamPaths paths
    ) {
    }
}
