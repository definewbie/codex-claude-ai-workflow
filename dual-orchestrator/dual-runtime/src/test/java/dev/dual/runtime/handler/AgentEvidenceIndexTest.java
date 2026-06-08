package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvidenceIndexTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void loadsRecentSidecarsByLastModifiedTime() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-index");
        var older = workstreamDir.resolve("runs/run-001/outputs/design.contract.json");
        var newer = workstreamDir.resolve("runs/run-002/outputs/acceptance.contract.json");
        writeSidecar(older, "design.codex_arch_review", "PASS");
        writeSidecar(newer, "acceptance.codex_verifier_review", "ACCEPT_WITH_NOTES");
        Files.setLastModifiedTime(older, FileTime.from(Instant.parse("2026-06-01T00:00:00Z")));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.parse("2026-06-01T00:01:00Z")));

        var index = AgentEvidenceIndex.load(workstreamDir, objectMapper);

        var recent = index.recent(2);
        assertEquals("acceptance.codex_verifier_review", recent.get(0).document().nodeId());
        assertEquals("design.codex_arch_review", recent.get(1).document().nodeId());
        assertEquals("ACCEPT_WITH_NOTES", index.latest().orElseThrow().document().decision());
    }

    @Test
    void latestFromRefsMapsMarkdownOutputToContractSidecar() throws Exception {
        var workspace = tempDir.resolve("workspace");
        var output = workspace.resolve(".agent/workstreams/ws-index/runs/run-001/outputs/acceptance.md");
        var sidecar = workspace.resolve(".agent/workstreams/ws-index/runs/run-001/outputs/acceptance.contract.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "# Acceptance\n", StandardCharsets.UTF_8);
        writeSidecar(sidecar, "acceptance.codex_verifier_review", "REJECT");

        var latest = AgentEvidenceIndex.latestFromRefs(
                workspace,
                List.of(".agent/workstreams/ws-index/runs/run-001/outputs/acceptance.md"),
                objectMapper
        );

        assertTrue(latest.isPresent());
        assertEquals("REJECT", latest.orElseThrow().document().decision());
    }

    private static void writeSidecar(Path path, String nodeId, String decision) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "%s",
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
                """.formatted(nodeId, decision, decision), StandardCharsets.UTF_8);
    }
}
