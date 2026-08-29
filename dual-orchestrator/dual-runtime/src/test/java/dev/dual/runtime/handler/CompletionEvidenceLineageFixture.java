package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.GoalDomainOutcome;
import dev.dual.ledger.file.GoalSatisfactionDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class CompletionEvidenceLineageFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    private CompletionEvidenceLineageFixture() {
    }

    static Prepared prepare(Path root, String workstreamId) throws Exception {
        return prepare(root, workstreamId, "ACCEPT", "Codex accepts the implementation.", "iter-001");
    }

    static Prepared prepare(
            Path root,
            String workstreamId,
            String acceptanceDecision,
            String acceptanceSummary
    ) throws Exception {
        return prepare(root, workstreamId, acceptanceDecision, acceptanceSummary, "iter-001");
    }

    static Prepared prepare(
            Path root,
            String workstreamId,
            String acceptanceDecision,
            String acceptanceSummary,
            String iterationId
    ) throws Exception {
        var workflowInstanceId = "wfi-" + workstreamId;
        var workstreamDir = root.resolve(".agent/workstreams").resolve(workstreamId);
        Files.createDirectories(workstreamDir.resolve("workflow"));
        Files.createDirectories(workstreamDir.resolve("nodes"));
        Files.createDirectories(workstreamDir.resolve("domain"));

        var definitions = definitions();
        new WorkflowFileLedger().initialize(
                new WorkstreamPaths(root, new DualId(workstreamId)),
                workflowInstanceId,
                new WorkflowDefinition(
                        "fixture-workflow", 1, "implement.claude_self_test_report", definitions,
                        List.of(
                                new EdgeDefinition("acceptance.codex_verifier_review", acceptanceDecision,
                                        "acceptance.decision"),
                                new EdgeDefinition("acceptance.decision", acceptanceDecision,
                                        "acceptance.generate_final_report"))));

        var selfTestSidecar = workstreamDir.resolve("runs/run-self/outputs/implement-claude-self-test.contract.json");
        Files.createDirectories(selfTestSidecar.getParent());
        Files.writeString(selfTestSidecar, """
                {
                  "node_id": "implement.claude_self_test_report",
                  "decision": "SELF_TEST_COMPLETE",
                  "stage_outcome": "READY",
                  "source_artifact_type": "self_test_report",
                  "summary": "Developer self-test passed.",
                  "verification_summary": "Tests: 42 passed",
                  "checks_run": ["npm test"],
                  "checks_skipped": ["device smoke: simulator unavailable"],
                  "evidence": ["unit tests passed", "Test output showed 24 passing tests."],
                  "missing_evidence": ["No physical device validation."]
                }
                """);
        AgentOutputLineageFixture.completeAndPublish(
                root, workstreamId, selfTestSidecar, iterationId, List.of());
        var selfTestRef = relative(root, selfTestSidecar);

        var contextNodeId = "node-context-acceptance";
        var packageRef = ".agent/workstreams/" + workstreamId + "/context/" + contextNodeId + ".md";
        var metadataRef = ".agent/workstreams/" + workstreamId + "/context/" + contextNodeId + ".json";
        Files.createDirectories(root.resolve(packageRef).getParent());
        Files.writeString(root.resolve(packageRef), "# Acceptance context\n");
        Files.writeString(root.resolve(metadataRef), "{}\n");
        MAPPER.writeValue(workstreamDir.resolve("nodes/" + contextNodeId + ".json").toFile(), new NodeInstanceDocument(
                1, contextNodeId, "context.acceptance_codex_verifier_review", NodeKind.TASK,
                "context.build_package", NodeStatus.COMPLETED, "stage-acceptance", iterationId,
                List.of(selfTestRef), Map.of(), List.of(packageRef, metadataRef), Map.of(), "", "", NOW, NOW));

        var acceptanceSidecar = workstreamDir.resolve("runs/run-acceptance/outputs/acceptance-codex-review.contract.json");
        Files.createDirectories(acceptanceSidecar.getParent());
        Files.writeString(acceptanceSidecar, """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "%s",
                  "stage_outcome": "ACCEPTED",
                  "source_artifact_type": "acceptance_review",
                  "summary": "%s",
                  "verification_summary": "All checks green.",
                  "evidence": ["diff matches contract"],
                  "missing_evidence": ["No physical device validation."],
                  "acceptance_notes": ["Run visual smoke before release."]
                }
                """.formatted(acceptanceDecision, acceptanceSummary));
        AgentOutputLineageFixture.completeAndPublish(
                root, workstreamId, acceptanceSidecar, iterationId, List.of(packageRef, metadataRef));
        var acceptanceNodeId = MAPPER.readTree(acceptanceSidecar.toFile()).path("node_instance_id").asText();
        var acceptanceNode = MAPPER.readValue(
                workstreamDir.resolve("nodes/" + acceptanceNodeId + ".json").toFile(), NodeInstanceDocument.class);
        var acceptanceRefs = acceptanceNode.outputRefs();

        var decisionNode = new NodeInstanceDocument(
                1, "node-acceptance-decision", "acceptance.decision", NodeKind.DECISION,
                "review.decision", NodeStatus.COMPLETED, "stage-acceptance", iterationId,
                acceptanceRefs, Map.of(), acceptanceRefs, Map.of("decision", acceptanceDecision),
                "", "", NOW, NOW);
        MAPPER.writeValue(workstreamDir.resolve("nodes/node-acceptance-decision.json").toFile(), decisionNode);
        MAPPER.writeValue(workstreamDir.resolve("domain/goal-satisfaction.json").toFile(), new GoalSatisfactionDocument(
                1, workstreamId, workflowInstanceId, decisionNode.nodeInstanceId(), decisionNode.nodeDefinitionId(),
                iterationId, acceptanceDecision,
                "ACCEPT_WITH_NOTES".equals(acceptanceDecision)
                        ? GoalDomainOutcome.SATISFIED_WITH_NOTES
                        : GoalDomainOutcome.SATISFIED,
                acceptanceSummary, acceptanceRefs, decisionNode.result(), NOW));

        var reportNode = new NodeInstance(
                "node-report", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                NodeStatus.RUNNING, "stage-acceptance", iterationId, acceptanceRefs, List.of(), Map.of(), "", NOW, null);
        return new Prepared(reportNode, selfTestRef, relative(root, acceptanceSidecar), packageRef, metadataRef,
                contextNodeId, decisionNode.nodeInstanceId());
    }

    private static List<NodeDefinition> definitions() {
        return List.of(
                new NodeDefinition("implement.claude_self_test_report", NodeKind.TASK, "acpx.agent_run", Map.of()),
                new NodeDefinition("context.acceptance_codex_verifier_review", NodeKind.TASK,
                        "context.build_package", Map.of(
                        "profile", "acceptance",
                        "target_node", "acceptance.codex_verifier_review",
                        "required_agent_evidence", Map.of(
                                "node_id", "implement.claude_self_test_report",
                                "source_artifact_type", "self_test_report",
                                "decision", "SELF_TEST_COMPLETE"))),
                new NodeDefinition("acceptance.codex_verifier_review", NodeKind.TASK, "acpx.agent_run", Map.of()),
                new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of()),
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()));
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    record Prepared(
            NodeInstance reportNode,
            String selfTestRef,
            String acceptanceRef,
            String packageRef,
            String metadataRef,
            String contextNodeId,
            String decisionNodeId
    ) {
    }
}
