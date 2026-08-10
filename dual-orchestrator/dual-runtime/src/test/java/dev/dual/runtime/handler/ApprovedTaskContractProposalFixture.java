package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Builds the minimum real proposal -> review -> human approval chain for integration tests. */
public final class ApprovedTaskContractProposalFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private ApprovedTaskContractProposalFixture() {
    }

    public static String publish(Path workspaceRoot, String workstreamId, String proposalJson) throws Exception {
        var workstreamDir = workspaceRoot.resolve(".agent/workstreams").resolve(workstreamId);
        var iterationId = "iter-001";
        var iterationsDir = workstreamDir.resolve("iterations");
        Files.createDirectories(iterationsDir);
        MAPPER.writeValue(iterationsDir.resolve(iterationId + ".json").toFile(),
                WorkflowIterationDocument.initial("intake.claude_brainstorm", Instant.now()));
        var proposalOutput = outputDir(workstreamDir, "run-proposal").resolve("intake-claude-brainstorm.json");
        Files.writeString(proposalOutput, "{}");
        var proposalSidecar = AgentOutputContractSidecar.pathFor(proposalOutput);
        var proposal = MAPPER.readValue(proposalJson, Map.class);
        MAPPER.writeValue(proposalSidecar.toFile(), Map.ofEntries(
                Map.entry("artifact_type", "agent_output_contract"),
                Map.entry("schema_version", 1),
                Map.entry("workstream_id", workstreamId),
                Map.entry("run_id", "run-proposal"),
                Map.entry("node_id", "intake.claude_brainstorm"),
                Map.entry("node_instance_id", "node-intake-proposal"),
                Map.entry("source_output_ref", relative(workspaceRoot, proposalOutput)),
                Map.entry("source_artifact_type", "task_contract_proposal"),
                Map.entry("decision", "READY_FOR_PRODUCT_REVIEW"),
                Map.entry("summary", "proposal ready"),
                Map.entry("task_contract_proposal", proposal)
        ));
        AgentOutputLineageFixture.publish(workspaceRoot, workstreamId, proposalSidecar, iterationId);
        var proposalDocument = AgentOutputContractDocument.read(proposalSidecar, MAPPER).orElseThrow();
        var proposalReference = TaskContractProposalReference.publishInitial(
                workspaceRoot, workstreamId, proposalSidecar, proposalDocument, MAPPER).reference();

        var reviewOutput = outputDir(workstreamDir, "run-review").resolve("intake-codex-product-review.json");
        Files.writeString(reviewOutput, "{}");
        var reviewSidecar = AgentOutputContractSidecar.pathFor(reviewOutput);
        MAPPER.writeValue(reviewSidecar.toFile(), Map.ofEntries(
                Map.entry("artifact_type", "agent_output_contract"),
                Map.entry("schema_version", 1),
                Map.entry("workstream_id", workstreamId),
                Map.entry("run_id", "run-review"),
                Map.entry("node_id", "intake.codex_product_review"),
                Map.entry("node_instance_id", "node-review-proposal"),
                Map.entry("source_output_ref", relative(workspaceRoot, reviewOutput)),
                Map.entry("source_artifact_type", "agent_output_contract"),
                Map.entry("decision", "READY_FOR_PRODUCT_REVIEW"),
                Map.entry("summary", "review ready"),
                Map.entry("review_subject", proposalReference.toMap())
        ));
        AgentOutputLineageFixture.publish(workspaceRoot, workstreamId, reviewSidecar, iterationId);
        var reviewRef = relative(workspaceRoot, reviewSidecar);

        var nodeInstanceId = "node-gate-human_requirement_review-fixture";
        var nodesDir = workstreamDir.resolve("nodes");
        var gatesDir = workstreamDir.resolve("gates");
        Files.createDirectories(nodesDir);
        Files.createDirectories(gatesDir);
        var nowInstant = Instant.parse("2026-07-21T00:00:00Z");
        MAPPER.writeValue(nodesDir.resolve(nodeInstanceId + ".json").toFile(), new NodeInstanceDocument(
                1, nodeInstanceId, "gate.human_requirement_review", NodeKind.GATE, "human.review",
                NodeStatus.COMPLETED, "stage-requirement", iterationId, List.of(reviewRef), Map.of(),
                List.of(reviewRef), Map.of("human_action", "approved"), "", "", nowInstant, nowInstant));
        var now = nowInstant.toString();
        MAPPER.writeValue(gatesDir.resolve("gate-requirement-fixture.json").toFile(), Map.of(
                "schemaVersion", 1,
                "gateId", "gate-requirement-fixture",
                "nodeInstanceId", nodeInstanceId,
                "gateType", "human.review",
                "status", "resolved",
                "allowedActions", List.of("approve", "revise"),
                "openedAt", now,
                "resolvedAt", now,
                "result", Map.of(
                        "human_action", "approved",
                        "note", "approved integration fixture",
                        "subject_input_refs", List.of(reviewRef))));
        return reviewRef;
    }

    private static Path outputDir(Path workstreamDir, String runId) throws Exception {
        var outputDir = workstreamDir.resolve("runs").resolve(runId).resolve("outputs");
        Files.createDirectories(outputDir);
        return outputDir;
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
