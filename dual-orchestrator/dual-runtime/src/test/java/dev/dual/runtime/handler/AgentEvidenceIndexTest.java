package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvidenceIndexTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void latestFromRefsConsumesOnlyLedgerBoundSidecar() throws Exception {
        var workspace = tempDir.resolve("workspace");
        var output = workspace.resolve(".agent/workstreams/ws-index/runs/run-001/outputs/acceptance.json");
        var sidecar = workspace.resolve(".agent/workstreams/ws-index/runs/run-001/outputs/acceptance.contract.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        writeSidecar(workspace, sidecar, "run-001", "node-acceptance-001",
                "acceptance.codex_verifier_review", "REJECT");
        AgentOutputLineageFixture.publish(workspace, "ws-index", sidecar);

        var latest = AgentEvidenceIndex.latestFromRefs(
                workspace,
                "ws-index",
                List.of(".agent/workstreams/ws-index/runs/run-001/outputs/acceptance.contract.json"),
                objectMapper
        );

        assertEquals("REJECT", latest.orElseThrow().document().decision());
    }

    @Test
    void latestDecisionDoesNotFallBackWhenLastRoutedSidecarIsInvalid() throws Exception {
        var workspace = tempDir.resolve("decision-workspace");
        var older = workspace.resolve(
                ".agent/workstreams/ws-index/runs/run-older/outputs/acceptance.contract.json");
        var latest = workspace.resolve(
                ".agent/workstreams/ws-index/runs/run-latest/outputs/acceptance.contract.json");
        writeSidecar(workspace, older, "run-older", "node-older",
                "design.codex_arch_review", "READY_FOR_ARCH_REVIEW");
        AgentOutputLineageFixture.publish(workspace, "ws-index", older);
        writeSidecar(workspace, latest, "run-latest", "node-latest",
                "design.codex_arch_review", "READY_FOR_ARCH_REVIEW");
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(latest.toFile());
        json.remove("decision");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(latest.toFile(), json);
        AgentOutputLineageFixture.publish(workspace, "ws-index", latest);

        var read = AgentEvidenceIndex.latestDecisionFromRefs(
                workspace,
                "ws-index",
                List.of(
                        workspace.relativize(older).toString(),
                        workspace.relativize(latest).toString()
                ),
                objectMapper
        );

        assertTrue(!read.missing());
        assertEquals("CONTRACT_SIDECAR_MISSING_DECISION", read.errorCode());
        assertTrue(read.entry() == null);
    }

    @Test
    void forgedSelfConsistentSidecarCannotBypassLedgerOccurrence() throws Exception {
        var workspace = tempDir.resolve("forged-workspace");
        var sidecar = workspace.resolve(".agent/workstreams/ws-index/runs/run-forged/outputs/acceptance.contract.json");
        writeSidecar(workspace, sidecar, "run-forged", "node-forged",
                "acceptance.codex_verifier_review", "REJECT");

        assertThrows(IllegalStateException.class, () -> AgentEvidenceIndex.latestFromRefs(
                workspace,
                "ws-index",
                List.of(workspace.relativize(sidecar).toString()),
                objectMapper));
    }

    @Test
    void publishedSidecarDecisionCannotBeChangedAfterLedgerCompletion() throws Exception {
        var workspace = tempDir.resolve("tampered-workspace");
        var sidecar = workspace.resolve(
                ".agent/workstreams/ws-index/runs/run-tampered/outputs/self-test.contract.json");
        writeSidecar(workspace, sidecar, "run-tampered", "node-self-test-001",
                "implement.claude_self_test_report", "SELF_TEST_INCOMPLETE");
        AgentOutputLineageFixture.publish(workspace, "ws-index", sidecar);

        var json = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(sidecar.toFile());
        json.put("decision", "SELF_TEST_COMPLETE");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(sidecar.toFile(), json);

        assertThrows(IllegalStateException.class, () -> AgentEvidenceIndex.latestFromRefs(
                workspace,
                "ws-index",
                List.of(workspace.relativize(sidecar).toString()),
                objectMapper));
    }

    @Test
    void publishedSidecarSummaryCannotBeChangedAfterLedgerCompletion() throws Exception {
        var workspace = tempDir.resolve("summary-tampered-workspace");
        var sidecar = workspace.resolve(
                ".agent/workstreams/ws-index/runs/run-summary/outputs/acceptance.contract.json");
        writeSidecar(workspace, sidecar, "run-summary", "node-summary",
                "acceptance.codex_verifier_review", "ACCEPT");
        AgentOutputLineageFixture.publish(workspace, "ws-index", sidecar);

        var json = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(sidecar.toFile());
        json.put("summary", "PROMPT_INJECTION");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(sidecar.toFile(), json);

        assertThrows(IllegalStateException.class, () -> AgentEvidenceIndex.latestFromRefs(
                workspace,
                "ws-index",
                List.of(workspace.relativize(sidecar).toString()),
                objectMapper));
    }

    @Test
    void validatedDocumentDropsUnknownRawFieldsFromTypedProjection() throws Exception {
        var workspace = tempDir.resolve("projection-workspace");
        var sidecar = workspace.resolve(
                ".agent/workstreams/ws-index/runs/run-projection/outputs/acceptance.contract.json");
        writeSidecar(workspace, sidecar, "run-projection", "node-projection",
                "acceptance.codex_verifier_review", "ACCEPT");
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(sidecar.toFile());
        json.put("untyped_payload", "PROMPT_INJECTION");
        json.withObject("evidence_state").put("untyped_payload", "EVIDENCE_STATE_INJECTION");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(sidecar.toFile(), json);
        AgentOutputLineageFixture.publish(workspace, "ws-index", sidecar);

        var document = AgentEvidenceIndex.validatedDocument(
                workspace,
                "ws-index",
                workspace.relativize(sidecar).toString(),
                objectMapper
        ).orElseThrow();
        var projection = objectMapper.writeValueAsString(document);

        assertTrue(projection.contains("summary"));
        assertFalse(projection.contains("PROMPT_INJECTION"));
        assertFalse(projection.contains("untyped_payload"));
        var index = AgentEvidenceIndex.fromRefs(
                workspace,
                "ws-index",
                List.of(workspace.relativize(sidecar).toString()),
                objectMapper);
        assertFalse(index.recent(1).getFirst().evidenceState().containsKey("untyped_payload"));
        assertFalse(index.recent(1).getFirst().evidenceState().containsValue("EVIDENCE_STATE_INJECTION"));
    }

    @Test
    void sidecarSymlinkCannotEnterValidatedEvidence() throws Exception {
        var workspace = tempDir.resolve("symlink-workspace");
        var sidecar = workspace.resolve(
                ".agent/workstreams/ws-index/runs/run-link/outputs/acceptance.contract.json");
        var target = workspace.resolve("sidecar-target.json");
        writeSidecar(workspace, target, "run-link", "node-link",
                "acceptance.codex_verifier_review", "ACCEPT");
        Files.createDirectories(sidecar.getParent());
        Files.createSymbolicLink(sidecar, target);
        AgentOutputLineageFixture.publish(workspace, "ws-index", sidecar);

        var latest = AgentEvidenceIndex.latestFromRefs(
                workspace,
                "ws-index",
                List.of(workspace.relativize(sidecar).toString()),
                objectMapper);

        assertTrue(latest.isEmpty());
    }

    private static void writeSidecar(
            Path workspace,
            Path path,
            String runId,
            String nodeInstanceId,
            String nodeId,
            String decision
    ) throws Exception {
        Files.createDirectories(path.getParent());
        var source = path.resolveSibling("acceptance.json");
        Files.writeString(source, "{}", StandardCharsets.UTF_8);
        Files.writeString(path, """
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "workstream_id": "ws-index",
                  "run_id": "%s",
                  "node_id": "%s",
                  "node_instance_id": "%s",
                  "source_output_ref": "%s",
                  "decision": "%s",
                  "summary": "summary",
                  "source_artifact_type": "acceptance_review",
                  "blocking": false,
                  "must_fix": [],
                  "missing_evidence": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "",
                  "control": {
                    "decision": "%s",
                    "summary": "summary"
                  },
                  "evidence_state": {
                    "blocking": false,
                    "must_fix_count": 0,
                    "missing_evidence_count": 0
                  },
                  "failure": {
                    "category_hint": "%s",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": "%s"
                  }
                }
                """.formatted(
                runId,
                nodeId,
                nodeInstanceId,
                workspace.relativize(source).toString().replace("\\", "\\\\"),
                decision,
                decision,
                "REJECT".equals(decision) ? "REVIEW_NOISE" : "",
                "REJECT".equals(decision) ? "Reevaluate the review evidence" : ""), StandardCharsets.UTF_8);
    }
}
