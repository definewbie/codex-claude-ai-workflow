package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.runtime.handler.AgentOutputContractSidecar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstreamMemoryUpdaterTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updatesReadFilesFromTypedToolUsageOnly() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/main.py"), "print('hi')\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("README.md"), "# Demo\n", StandardCharsets.UTF_8);

        var updater = new WorkstreamMemoryUpdater();
        updater.updateReadFiles(
                tempDir,
                "ws-memory",
                "run-001",
                "intake.claude_brainstorm",
                "intake",
                new AcpxToolUsage(1, 2, 2, 0, 0, 0, Map.of("Read", 2),
                        List.of("Read"),
                        List.of("src/main.py", "./README.md", ".agent/workstreams/ws-memory/runs/run-001/run.json"),
                        List.of(), List.of(), List.of())
        );

        var memory = objectMapper.readValue(updater.memoryPath(tempDir, "ws-memory").toFile(), Map.class);
        assertEquals("workstream_memory", memory.get("artifactType"));
        assertTrue(memory.get("readFiles") instanceof List<?> files && files.size() == 2);
        var source = readFile(memory, "src/main.py");
        assertEquals("run-001", source.get("readRunId"));
        assertEquals("intake", source.get("lastSeenStage"));
        assertEquals("source", source.get("evidenceRole"));
        assertEquals(true, source.get("trusted"));
        assertEquals("", source.get("invalidationReason"));
        assertTrue(source.get("summary").toString().contains("Previously read source file"));
        assertFalse(memory.toString().contains(".agent/workstreams/ws-memory/runs"),
                "memory must not record orchestrator-internal reads");
    }

    @Test
    void marksPriorReadInvalidWhenFileChangesAndKeepsUpdatesIdempotent() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        var file = tempDir.resolve("src/main.py");
        Files.writeString(file, "print('v1')\n", StandardCharsets.UTF_8);

        var updater = new WorkstreamMemoryUpdater();
        var usage = new AcpxToolUsage(1, 1, 1, 0, 0, 0, Map.of("Read", 1),
                List.of("Read"),
                List.of("src/main.py"), List.of(), List.of(), List.of());
        updater.updateReadFiles(tempDir, "ws-memory", "run-001", "intake.claude_brainstorm", "intake", usage);
        updater.updateReadFiles(tempDir, "ws-memory", "run-001", "intake.claude_brainstorm", "intake", usage);

        var memory = objectMapper.readValue(updater.memoryPath(tempDir, "ws-memory").toFile(), Map.class);
        assertTrue(memory.get("readFiles") instanceof List<?> files && files.size() == 1,
                "same run update should upsert rather than duplicate read memory");

        Files.writeString(file, "print('v2')\n", StandardCharsets.UTF_8);
        updater.updateReadFiles(tempDir, "ws-memory", "run-002", "plan.claude_plan", "plan",
                AcpxToolUsage.empty());

        memory = objectMapper.readValue(updater.memoryPath(tempDir, "ws-memory").toFile(), Map.class);
        var source = readFile(memory, "src/main.py");
        assertEquals(false, source.get("trusted"));
        assertEquals("file changed since prior read", source.get("invalidationReason"));
    }

    @Test
    void corruptedMemoryFallsBackToFreshDocument() throws Exception {
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/main.py"), "print('hi')\n", StandardCharsets.UTF_8);
        var updater = new WorkstreamMemoryUpdater();
        var memoryPath = updater.memoryPath(tempDir, "ws-memory");
        Files.createDirectories(memoryPath.getParent());
        Files.writeString(memoryPath, "{not-json", StandardCharsets.UTF_8);

        updater.updateReadFiles(tempDir, "ws-memory", "run-001", "intake.claude_brainstorm", "intake",
                new AcpxToolUsage(1, 1, 1, 0, 0, 0, Map.of("Read", 1),
                        List.of("Read"),
                        List.of("src/main.py"), List.of(), List.of(), List.of()));

        var memory = objectMapper.readValue(memoryPath.toFile(), Map.class);
        assertEquals("workstream_memory", memory.get("artifactType"));
        assertTrue(memory.get("readFiles") instanceof List<?> files && files.size() == 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateMemoryPopulatesDecidedScopeAndNonGoalsFromTaskContract() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-scope");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {
                  "id":"task-ws-scope","version":1,"created_at":"2026-07-23T00:00:00Z",
                  "objective":"o","status":"READY_FOR_REVIEW",
                  "scope":{"included":[
                    {"path":"src/login.js","operation":"MODIFY"},
                    {"path":"src/refreshToken.js","operation":"CREATE"}
                  ],"excluded":["billing"]},
                  "non_goals":["SSO"],"acceptance_criteria":[],"test_requirements":[],
                  "constraints":[],"risk_areas":[],"ambiguity_notes":[],"human_questions":[],
                  "compilation_pass":1
                }
                """, StandardCharsets.UTF_8);

        var updater = new WorkstreamMemoryUpdater();
        updater.updateMemory(tempDir, "ws-scope", "run-1", "implement.claude_implement",
                "implement", "claude", null, AcpxToolUsage.empty(), null);

        var memory = objectMapper.readValue(updater.memoryPath(tempDir, "ws-scope").toFile(), Map.class);
        assertEquals(List.of("src/login.js", "src/refreshToken.js"), memory.get("decidedScope"));
        assertEquals(List.of("SSO"), memory.get("nonGoals"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateMemoryExtractsVerdictAndOpenIssuesFromValidatedSidecar() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-verdict");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "nonGoals":[],"acceptanceCriteria":[],"constraints":[],"riskAreas":[]}
                """, StandardCharsets.UTF_8);
        var output = workstreamDir.resolve("runs/run-1/outputs/verifier.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        Files.writeString(AgentOutputContractSidecar.pathFor(output), """
                {"artifact_type":"agent_output_contract",
                 "node_id":"acceptance.codex_verifier_review","decision":"REJECT",
                 "summary":"token refresh missing","stage_outcome":"needs_revision",
                 "must_fix":["refresh token on expiry"],"open_risks":["race on logout"],
                 "changed_files":["src/auth.py"],"verification_summary":"tests red"}
                """, StandardCharsets.UTF_8);

        var updater = new WorkstreamMemoryUpdater();
        updater.updateMemory(tempDir, "ws-verdict", "run-1", "acceptance.codex_verifier_review",
                "acceptance", "codex", output, AcpxToolUsage.empty(), null);

        var memory = objectMapper.readValue(updater.memoryPath(tempDir, "ws-verdict").toFile(), Map.class);
        var verdicts = (List<?>) memory.get("priorVerdicts");
        assertFalse(verdicts.isEmpty(), "REJECT verdict must be recorded as prior verdict");
        assertEquals("REJECT", ((Map<?, ?>) verdicts.get(0)).get("decision"));
        var openIssues = (List<?>) memory.get("openIssues");
        var descs = new java.util.ArrayList<String>();
        var sourceFields = new java.util.ArrayList<String>();
        for (var item : openIssues) {
            if (item instanceof Map<?, ?> m) {
                descs.add(String.valueOf(m.get("desc")));
                sourceFields.add(String.valueOf(m.get("sourceField")));
            }
        }
        assertTrue(descs.contains("refresh token on expiry"), descs.toString());
        assertTrue(descs.contains("race on logout"), descs.toString());
        assertTrue(sourceFields.contains("must_fix"), sourceFields.toString());
        assertTrue(sourceFields.contains("open_risks"), sourceFields.toString());
        var fieldSemantics = (Map<?, ?>) memory.get("fieldSemantics");
        assertTrue(String.valueOf(fieldSemantics.get("projectionOnlyFields")).contains("desc"));
        assertTrue(String.valueOf(fieldSemantics.get("machineDecisionFields")).contains("sourceField"));
        assertFalse(objectMapper.writeValueAsString(memory).contains("Some narrative prose that must NOT become memory"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateMemoryIgnoresProseOnlyOutput() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-prose");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "nonGoals":[],"acceptanceCriteria":[],"constraints":[],"riskAreas":[]}
                """, StandardCharsets.UTF_8);
        var output = workstreamDir.resolve("runs/run-1/outputs/out.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "I think we should REJECTED everything and must_fix the token.\n",
                StandardCharsets.UTF_8);

        var updater = new WorkstreamMemoryUpdater();
        updater.updateMemory(tempDir, "ws-prose", "run-1", "implement.claude_implement",
                "implement", "claude", output, AcpxToolUsage.empty(), null);

        var memory = objectMapper.readValue(updater.memoryPath(tempDir, "ws-prose").toFile(), Map.class);
        var text = objectMapper.writeValueAsString(memory).toLowerCase(java.util.Locale.ROOT);
        assertFalse(text.contains("rejected"));
        assertFalse(text.contains("must_fix"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateMemoryMergesIdempotentlyAndStaysPerWorkstream() throws Exception {
        var updater = new WorkstreamMemoryUpdater();
        for (String ws : new String[]{"ws-a", "ws-b"}) {
            var dir = tempDir.resolve(".agent/workstreams/" + ws);
            Files.createDirectories(dir.resolve("artifacts"));
            Files.writeString(dir.resolve("artifacts/task-contract.json"), """
                    {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                     "nonGoals":[],"acceptanceCriteria":[],"constraints":[],"riskAreas":[]}
                    """, StandardCharsets.UTF_8);
            for (int i = 0; i < 2; i++) {
                updater.updateMemory(tempDir, ws, "run-1", "implement.claude_implement",
                        "implement", "claude", null, AcpxToolUsage.empty(), null);
            }
        }
        var memA = objectMapper.readValue(updater.memoryPath(tempDir, "ws-a").toFile(), Map.class);
        var memB = objectMapper.readValue(updater.memoryPath(tempDir, "ws-b").toFile(), Map.class);
        assertEquals("ws-a", memA.get("workstreamId"));
        assertEquals("ws-b", memB.get("workstreamId"));
        assertEquals(List.of(), memA.get("decidedScope"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readFilesSummaryIsSemanticFromStructureNotMetadata() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-summary");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {
                  "id":"task-ws-summary","version":1,"created_at":"2026-07-23T00:00:00Z",
                  "objective":"o","status":"READY_FOR_REVIEW",
                  "scope":{"included":[{"path":"src/auth.js","operation":"MODIFY"}],"excluded":[]},
                  "non_goals":[],"acceptance_criteria":["refresh token on expiry"],
                  "test_requirements":[],"constraints":[],"risk_areas":[],
                  "ambiguity_notes":[],"human_questions":[],"compilation_pass":1
                }
                """);
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("docs"));
        Files.createDirectories(tempDir.resolve("test"));
        Files.writeString(tempDir.resolve("src/auth.js"), "export function refreshToken() {}\nexport class AuthClient {}\n");
        Files.writeString(tempDir.resolve("docs/auth.md"), "# Auth\n\nThe refresh token on expiry runs here.\n");
        Files.writeString(tempDir.resolve("test/auth.test.js"), "describe('Auth', () => { it('refreshes token', () => {}); });\n");

        new WorkstreamMemoryUpdater().updateMemory(tempDir, "ws-summary", "run-1", "intake.claude_brainstorm",
                "intake", "claude", null,
                new AcpxToolUsage(1, 3, 3, 0, 0, 0, Map.of("Read", 3),
                        List.of("Read"),
                        List.of("src/auth.js", "docs/auth.md", "test/auth.test.js"), List.of(), List.of(), List.of()),
                null);

        var memory = objectMapper.readValue(new WorkstreamMemoryUpdater().memoryPath(tempDir, "ws-summary").toFile(), Map.class);
        var summaries = new java.util.HashMap<String, String>();
        for (var item : (List<?>) memory.get("readFiles")) {
            if (item instanceof Map<?, ?> m) {
                summaries.put(String.valueOf(m.get("path")), String.valueOf(m.get("summary")));
            }
        }
        assertTrue(summaries.get("src/auth.js").contains("refreshToken"), summaries.toString());
        assertTrue(summaries.get("docs/auth.md").contains("Auth"), summaries.toString());
        assertTrue(summaries.get("docs/auth.md").contains("refresh"), summaries.toString());
        assertTrue(summaries.get("test/auth.test.js").contains("refreshes token"), summaries.toString());
        for (var s : summaries.values()) {
            assertFalse(s.contains("Previously read"), "semantic summary must not fall back to metadata: " + s);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void readFilesSummaryFallsBackToMetadataWhenNoStructure() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-fallback");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"),
                "{\"objective\":\"o\",\"status\":\"READY_FOR_REVIEW\",\"scope\":{\"included\":[]},"
                        + "\"non_goals\":[],\"acceptance_criteria\":[],\"constraints\":[],\"risk_areas\":[]}");
        Files.createDirectories(tempDir.resolve("bin"));
        Files.writeString(tempDir.resolve("bin/data.json"), "{\"a\":1}\n");

        new WorkstreamMemoryUpdater().updateMemory(tempDir, "ws-fallback", "run-1", "n",
                "intake", "claude", null,
                new AcpxToolUsage(1, 1, 1, 0, 0, 0, Map.of("Read", 1),
                        List.of("Read"),
                        List.of("bin/data.json"), List.of(), List.of(), List.of()),
                null);

        var memory = objectMapper.readValue(new WorkstreamMemoryUpdater().memoryPath(tempDir, "ws-fallback").toFile(), Map.class);
        var summary = ((List<?>) memory.get("readFiles")).stream()
                .filter(Map.class::isInstance).map(Map.class::cast)
                .map(m -> String.valueOf(m.get("summary"))).findFirst().orElse("");
        assertTrue(summary.contains("Previously read"), "no-structure file should fall back to metadata: " + summary);
    }

    private static Map<?, ?> readFile(Map<?, ?> memory, String path) {
        var files = (List<?>) memory.get("readFiles");
        return files.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> path.equals(item.get("path")))
                .findFirst()
                .orElseThrow();
    }
}
