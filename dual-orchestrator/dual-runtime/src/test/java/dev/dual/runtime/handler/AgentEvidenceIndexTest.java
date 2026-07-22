package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                """.formatted(
                runId,
                nodeId,
                nodeInstanceId,
                workspace.relativize(source).toString().replace("\\", "\\\\"),
                decision,
                decision), StandardCharsets.UTF_8);
    }
}
