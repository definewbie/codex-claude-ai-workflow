package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GoalDomainOutcome;
import dev.dual.ledger.file.GoalSatisfactionDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionEvidenceLineageResolverTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @TempDir
    Path tempDir;

    @Test
    void resolvesAcceptanceAndSelfTestThroughPersistedContextLineage() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepareApproved(tempDir, "ws-lineage");

        var resolution = CompletionEvidenceLineageResolver.resolveForReport(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-lineage",
                fixture.reportNode());

        assertEquals(CompletionEvidenceLineageResolver.Status.AVAILABLE, resolution.status());
        assertEquals("acceptance.codex_verifier_review", resolution.acceptance().document().nodeId());
        assertEquals("implement.claude_self_test_report", resolution.selfTest().document().nodeId());
        assertEquals(fixture.contextNodeId(), resolution.contextNode().nodeInstanceId());
        assertTrue(resolution.acceptance().document().sourceOutputRef().endsWith(".json"));
        assertTrue(resolution.selfTest().document().sourceOutputRef().endsWith(".json"));
    }

    @Test
    void distinguishesMissingAndNonFinalGoalsFromCorruption() throws Exception {
        assertEquals(CompletionEvidenceLineageResolver.Status.NOT_CONFIGURED,
                CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-missing").status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_NOT_CONFIGURED,
                CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-missing").failureCode());

        CompletionEvidenceLineageFixture.prepare(tempDir, "ws-not-final");
        var goalPath = workstreamDir("ws-not-final").resolve("domain/goal-satisfaction.json");
        var goal = MAPPER.readValue(goalPath.toFile(), GoalSatisfactionDocument.class);
        MAPPER.writeValue(goalPath.toFile(), new GoalSatisfactionDocument(
                goal.schemaVersion(), goal.workstreamId(), goal.workflowInstanceId(), goal.nodeInstanceId(),
                "design.decision", goal.iterationId(), "NEEDS_REVISION", GoalDomainOutcome.NEEDS_REDESIGN,
                "design needs revision", goal.evidenceRefs(), goal.sourceResult(), goal.generatedAt()));

        var resolution = CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-not-final");
        assertEquals(CompletionEvidenceLineageResolver.Status.NOT_FINAL, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_NOT_FINAL, resolution.failureCode());
    }

    @Test
    void rejectsPackageRefThatIsNotPublishedByTheLocatedContextNode() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-forged-package");
        var nodePath = workstreamDir("ws-forged-package").resolve("nodes/" + fixture.contextNodeId() + ".json");
        var node = MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
        MAPPER.writeValue(nodePath.toFile(), copy(node, node.inputRefs(), List.of(fixture.metadataRef()), node.iterationId()));

        var resolution = CompletionEvidenceLineageResolver.resolveForReport(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-forged-package",
                fixture.reportNode());

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.CONTEXT_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsMissingRoutedSelfTestEvenWhenSidecarStillExists() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-missing-self-test");
        var nodePath = workstreamDir("ws-missing-self-test").resolve("nodes/" + fixture.contextNodeId() + ".json");
        var node = MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
        MAPPER.writeValue(nodePath.toFile(), copy(node, List.of(), node.outputRefs(), node.iterationId()));
        assertTrue(Files.isRegularFile(tempDir.resolve(fixture.selfTestRef())));
        Files.setLastModifiedTime(tempDir.resolve(fixture.selfTestRef()),
                java.nio.file.attribute.FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));

        var resolution = CompletionEvidenceLineageResolver.resolveForReport(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-missing-self-test",
                fixture.reportNode());

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.SELF_TEST_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsCrossIterationAcceptanceOccurrence() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-cross-acceptance");
        var sidecar = MAPPER.readTree(tempDir.resolve(fixture.acceptanceRef()).toFile());
        var nodePath = workstreamDir("ws-cross-acceptance")
                .resolve("nodes/" + sidecar.path("node_instance_id").asText() + ".json");
        var node = MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
        MAPPER.writeValue(nodePath.toFile(), copy(node, node.inputRefs(), node.outputRefs(), "iter-000"));

        var resolution = CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-cross-acceptance");

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.ACCEPTANCE_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsCrossIterationAcceptanceContextOccurrence() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-cross-context");
        var nodePath = workstreamDir("ws-cross-context").resolve("nodes/" + fixture.contextNodeId() + ".json");
        var node = MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
        MAPPER.writeValue(nodePath.toFile(), copy(node, node.inputRefs(), node.outputRefs(), "iter-000"));

        var resolution = CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-cross-context");

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.CONTEXT_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsCrossIterationSelfTestOccurrence() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-cross-iteration");
        var sidecar = MAPPER.readTree(tempDir.resolve(fixture.selfTestRef()).toFile());
        var nodeId = sidecar.path("node_instance_id").asText();
        var nodePath = workstreamDir("ws-cross-iteration").resolve("nodes/" + nodeId + ".json");
        var node = MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
        MAPPER.writeValue(nodePath.toFile(), copy(node, node.inputRefs(), node.outputRefs(), "iter-000"));

        var resolution = CompletionEvidenceLineageResolver.resolveForReport(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-cross-iteration",
                fixture.reportNode());

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.SELF_TEST_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsAcceptanceSidecarWhosePublishedShaWasTampered() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-tampered-acceptance");
        Files.writeString(tempDir.resolve(fixture.acceptanceRef()), "\n", java.nio.file.StandardOpenOption.APPEND);

        var resolution = CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-tampered-acceptance");

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.ACCEPTANCE_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsGoalEvidenceRefsThatDoNotEqualDecisionOutputs() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-goal-ref-drift");
        var goalPath = workstreamDir("ws-goal-ref-drift").resolve("domain/goal-satisfaction.json");
        var goal = MAPPER.readValue(goalPath.toFile(), GoalSatisfactionDocument.class);
        MAPPER.writeValue(goalPath.toFile(), new GoalSatisfactionDocument(
                goal.schemaVersion(), goal.workstreamId(), goal.workflowInstanceId(), goal.nodeInstanceId(),
                goal.nodeDefinitionId(), goal.iterationId(), goal.outcome(), goal.domainOutcome(), goal.reason(),
                List.of(fixture.acceptanceRef()), goal.sourceResult(), goal.generatedAt()));

        var resolution = CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-goal-ref-drift");

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_INVALID, resolution.failureCode());
    }

    @Test
    void rejectsTwoMatchingAcceptanceOccurrencesInsteadOfChoosingByOrder() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-duplicate-acceptance");
        var sidecar = workstreamDir("ws-duplicate-acceptance")
                .resolve("runs/run-acceptance-duplicate/outputs/acceptance-codex-review.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "ACCEPT",
                  "stage_outcome": "ACCEPTED",
                  "source_artifact_type": "acceptance_review",
                  "summary": "Duplicate acceptance.",
                  "verification_summary": "All checks green.",
                  "evidence": ["duplicate evidence"]
                }
                """);
        AgentOutputLineageFixture.completeAndPublish(
                tempDir, "ws-duplicate-acceptance", sidecar, "iter-001",
                List.of(fixture.packageRef(), fixture.metadataRef()));
        var duplicateDocument = MAPPER.readTree(sidecar.toFile());
        var duplicateNode = MAPPER.readValue(
                workstreamDir("ws-duplicate-acceptance").resolve(
                        "nodes/" + duplicateDocument.path("node_instance_id").asText() + ".json").toFile(),
                NodeInstanceDocument.class);

        var goalPath = workstreamDir("ws-duplicate-acceptance").resolve("domain/goal-satisfaction.json");
        var goal = MAPPER.readValue(goalPath.toFile(), GoalSatisfactionDocument.class);
        var decisionPath = workstreamDir("ws-duplicate-acceptance").resolve("nodes/" + goal.nodeInstanceId() + ".json");
        var decision = MAPPER.readValue(decisionPath.toFile(), NodeInstanceDocument.class);
        var combinedRefs = new java.util.ArrayList<>(decision.outputRefs());
        combinedRefs.addAll(duplicateNode.outputRefs());
        MAPPER.writeValue(decisionPath.toFile(), new NodeInstanceDocument(
                decision.schemaVersion(), decision.nodeInstanceId(), decision.nodeDefinitionId(), decision.kind(),
                decision.handlerType(), decision.status(), decision.stageId(), decision.iterationId(),
                combinedRefs, decision.inputTransition(), combinedRefs, decision.result(), decision.runRef(),
                decision.latestExecutionId(), decision.startedAt(), decision.finishedAt()));
        MAPPER.writeValue(goalPath.toFile(), new GoalSatisfactionDocument(
                goal.schemaVersion(), goal.workstreamId(), goal.workflowInstanceId(), goal.nodeInstanceId(),
                goal.nodeDefinitionId(), goal.iterationId(), goal.outcome(), goal.domainOutcome(), goal.reason(),
                combinedRefs, goal.sourceResult(), goal.generatedAt()));

        var resolution = CompletionEvidenceLineageResolver.resolveCurrent(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-duplicate-acceptance");

        assertEquals(CompletionEvidenceLineageResolver.Status.INVALID, resolution.status());
        assertEquals(CompletionEvidenceLineageResolver.ACCEPTANCE_INVALID, resolution.failureCode());
        assertTrue(resolution.detail().contains("found 2"));
    }

    @Test
    void reportEntryPreservesNotConfiguredAndNotFinalFailureCodes() throws Exception {
        var missingReport = reportNode("ws-report-missing", List.of());
        var missing = CompletionEvidenceLineageResolver.resolveForReport(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-report-missing",
                missingReport);
        assertEquals(CompletionEvidenceLineageResolver.Status.NOT_CONFIGURED, missing.status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_NOT_CONFIGURED, missing.failureCode());

        var fixture = CompletionEvidenceLineageFixture.prepare(tempDir, "ws-report-not-final");
        var goalPath = workstreamDir("ws-report-not-final").resolve("domain/goal-satisfaction.json");
        var goal = MAPPER.readValue(goalPath.toFile(), GoalSatisfactionDocument.class);
        MAPPER.writeValue(goalPath.toFile(), new GoalSatisfactionDocument(
                goal.schemaVersion(), goal.workstreamId(), goal.workflowInstanceId(), goal.nodeInstanceId(),
                "plan.decision", goal.iterationId(), "NEEDS_REVISION", GoalDomainOutcome.NEEDS_REDESIGN,
                "plan needs revision", goal.evidenceRefs(), goal.sourceResult(), Instant.now()));
        var notFinal = CompletionEvidenceLineageResolver.resolveForReport(dev.dual.runtime.RuntimeTestLedger.ledger(),
                tempDir,
                "ws-report-not-final",
                fixture.reportNode());
        assertEquals(CompletionEvidenceLineageResolver.Status.NOT_FINAL, notFinal.status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_NOT_FINAL, notFinal.failureCode());
    }

    private Path workstreamDir(String workstreamId) {
        return tempDir.resolve(".agent/workstreams").resolve(workstreamId);
    }

    private static NodeInstance reportNode(String workstreamId, List<String> refs) {
        return new NodeInstance(
                "node-report-" + workstreamId,
                "acceptance.generate_final_report",
                dev.dual.core.workflow.NodeKind.TASK,
                "report.generate_final",
                NodeStatus.RUNNING,
                "stage-acceptance",
                "iter-001",
                refs,
                List.of(),
                java.util.Map.of(),
                "",
                Instant.now(),
                null);
    }

    private static NodeInstanceDocument copy(
            NodeInstanceDocument node,
            List<String> inputRefs,
            List<String> outputRefs,
            String iterationId
    ) {
        return new NodeInstanceDocument(
                node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(),
                node.status(), node.stageId(), iterationId, inputRefs, node.inputTransition(), outputRefs, node.result(),
                node.runRef(), node.latestExecutionId(), node.startedAt(), node.finishedAt());
    }
}
