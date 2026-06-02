package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorkstreamApiControllerTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void statusReturnsWorkflowFirstSnapshot() throws Exception {
        var fixture = createWorkstream("ws-api-status");
        var controller = controller();

        var response = controller.status(fixture.workstreamId(), tempDir);

        assertEquals(fixture.workstreamId(), response.get("workstreamId"));
        assertFalse(response.containsKey("legacy"));
        var workspace = (Map<String, Object>) response.get("workspace");
        assertEquals("ai/" + fixture.workstreamId(), workspace.get("branch"));
        var workflow = (Map<String, Object>) response.get("workflow");
        assertEquals("api-workflow", workflow.get("workflowId"));
        assertEquals("WAITING_FOR_HUMAN", workflow.get("status"));
        var activeNodes = (List<Map<String, Object>>) workflow.get("activeNodes");
        assertEquals("review.gate", activeNodes.get(0).get("nodeDefinitionId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusReturnsArtifactContractSummaries() throws Exception {
        var fixture = createWorkstream("ws-api-contracts");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var sidecar = paths.workstreamDir().resolve("runs/run-001/outputs/design-codex-review.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "design.codex_arch_review",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Architecture is ready.",
                  "must_fix": ["Clarify rollout scope"],
                  "open_risks": ["Migration risk"],
                  "changed_files": ["docs/design.md"],
                  "verification_summary": "No runtime verification needed yet.",
                  "source_output_ref": ".agent/workstreams/ws-api-contracts/runs/run-001/outputs/design-codex-review.md"
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.status(fixture.workstreamId(), tempDir);

        var contracts = (List<Map<String, Object>>) response.get("artifactContracts");
        assertEquals(1, contracts.size());
        assertEquals("design.codex_arch_review", contracts.get(0).get("node_id"));
        assertEquals("READY_FOR_ARCH_REVIEW", contracts.get(0).get("decision"));
        assertEquals(List.of("Clarify rollout scope"), contracts.get(0).get("must_fix"));
        assertEquals(".agent/workstreams/ws-api-contracts/runs/run-001/outputs/design-codex-review.contract.json",
                contracts.get(0).get("sidecar_ref"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsWorkstreamSummaries() throws Exception {
        createWorkstream("ws-api-list-a");
        createWorkstream("ws-api-list-b");
        var controller = controller();

        var response = controller.list(tempDir.toString());

        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals(2, workstreams.size());
        assertEquals("ws-api-list-a", workstreams.get(0).get("workstreamId"));
        assertEquals("WAITING_FOR_HUMAN", workstreams.get(0).get("workflowStatus"));
        assertEquals(1, workstreams.get(0).get("openGateCount"));
        assertFalse(workstreams.get(0).containsKey("legacy"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timelineReturnsProjectionJson() throws Exception {
        var fixture = createWorkstream("ws-api-timeline");
        var controller = controller();

        var timeline = controller.timeline(fixture.workstreamId(), tempDir.toString());

        assertEquals(fixture.workstreamId(), timeline.get("workstreamId"));
        assertEquals("WAITING_FOR_HUMAN", timeline.get("workflowStatus"));
        var nodes = (List<Map<String, Object>>) timeline.get("nodes");
        assertEquals(2, nodes.size());
        assertEquals("first", nodes.get(0).get("nodeDefinitionId"));
    }

    @Test
    void currentReviewReturnsActiveGatePacket() throws Exception {
        var fixture = createWorkstream("ws-api-review");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var ledger = new WorkflowFileLedger();
        var activeNodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        var packetPath = paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".md");
        Files.createDirectories(packetPath.getParent());
        Files.writeString(packetPath, "# Human Review Packet\n\nRead this before approval.\n", StandardCharsets.UTF_8);
        Files.writeString(paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".json"),
                "{\"allowedActions\":[\"approve\",\"revise\"]}", StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.currentReview(fixture.workstreamId(), tempDir.toString());

        assertEquals(fixture.workstreamId(), response.get("workstreamId"));
        assertEquals("review.gate", response.get("gateNodeDefinitionId"));
        assertEquals(".agent/workstreams/" + fixture.workstreamId() + "/reviews/" + activeNodeId + ".md",
                response.get("packetPath"));
        assertEquals(true, response.get("packetMarkdown").toString().contains("Read this before approval."));
    }

    private WorkstreamApiController controller() {
        return new WorkstreamApiController(
                new WorkstreamQueryService(new ObjectMapper()),
                new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                        new dev.dual.runtime.WorkflowRuntimeResult(command.workstreamId(),
                                dev.dual.core.workflow.WorkflowStatus.COMPLETED, "", "unused"))
        );
    }

    private Fixture createWorkstream(String workstreamIdValue) throws Exception {
        var workstreamId = new DualId(workstreamIdValue);
        var workspaceId = new DualId("wsp-" + workstreamIdValue);
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstreamId,
                        "api test",
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspaceId,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspaceId,
                        workstreamId,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs").resolve(workstreamIdValue),
                        "ai/" + workstreamIdValue,
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
        var definition = new WorkflowDefinition(
                "api-workflow",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("review.gate", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "review.gate"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + workstreamIdValue, definition);
        var current = ledger.loadCurrent(paths);
        var first = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("first-output.md"), Map.of("ok", true));
        return new Fixture(workstreamIdValue);
    }

    private record Fixture(String workstreamId) {
    }
}
