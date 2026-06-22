package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextGovernanceActualReportWriterTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesActualReportWithOutsideScopeEvidence() throws Exception {
        var workspace = tempDir.resolve("repo");
        var workstreamId = "ws-001";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("evidence-manifest.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "items", List.of(Map.of(
                                "id", "task_contract:1",
                                "type", "task_contract",
                                "files", List.of("src/main.py")
                        ))
                )
        );
        var previousRun = workstreamDir.resolve("runs/run-000/run.json");
        Files.createDirectories(previousRun.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                previousRun.toFile(),
                Map.of(
                        "runId", "run-000",
                        "toolUsage", Map.of(
                                "readFiles", List.of("src/main.py", "src/old.py")
                        )
                )
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("file-relevance-plan-node-001.json").toFile(),
                Map.of(
                        "nodeDefinitionId", "implement.claude_implement",
                        "readCandidates", List.of("src/main.py"),
                        "writeCandidates", List.of("src/main.py")
                )
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("stage-scope-policy-node-001.json").toFile(),
                Map.of(
                        "nodeDefinitionId", "implement.claude_implement",
                        "nodeInstanceId", "node-001",
                        "iterationId", "iter-001",
                        "stageId", "implement",
                        "readScope", List.of("src/main.py"),
                        "writeScope", List.of("src/main.py")
                )
        );
        var toolUsage = new AcpxToolUsage(
                1,
                4,
                2,
                1,
                1,
                0,
                Map.of("Read", 2, "Edit", 1, "Bash", 1),
                List.of("src/main.py", "src/extra.py"),
                List.of("src/extra.py"),
                List.of("npm test")
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/implement.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, "transcript");

        var output = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "implement.claude_implement",
                "run-001",
                transcript,
                toolUsage
        );

        assertTrue(output.isPresent());
        var json = objectMapper.readTree(output.get().toFile());
        assertEquals("actual", json.get("reportPhase").asText());
        assertEquals(false, json.get("telemetryPending").asBoolean());
        assertEquals("src/extra.py", json.get("outsideScopeReads").get(0).asText());
        assertEquals("src/extra.py", json.get("outsideScopeWrites").get(0).asText());
        assertEquals("src/main.py", json.get("repeatedReadFiles").get(0).get("path").asText());
        assertEquals(1, json.get("repeatedReadFiles").get(0).get("previousCount").asInt());
        assertEquals(true, json.get("repeatedReadFiles").get(0).get("evidenceKnown").asBoolean());
        assertEquals(4, json.get("toolUsage").get("toolCallCount").asInt());
    }

    @Test
    void doesNotFallbackToAnotherNodePlan() throws Exception {
        var workspace = tempDir.resolve("repo-no-fallback");
        var workstreamId = "ws-002";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("stage-scope-policy-node-other.json").toFile(),
                Map.of(
                        "nodeDefinitionId", "context.intake_codex_product_review",
                        "nodeInstanceId", "node-context-001",
                        "iterationId", "iter-001",
                        "stageId", "intake",
                        "readScope", List.of("README.md"),
                        "writeScope", List.of()
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/design.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, "transcript");

        var output = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "design.claude_design",
                "run-001",
                transcript,
                new AcpxToolUsage(
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        Map.of("Read", 1),
                        List.of("README.md"),
                        List.of(),
                        List.of()
                )
        );

        assertTrue(output.isPresent());
        var json = objectMapper.readTree(output.get().toFile());
        assertEquals("design.claude_design", json.get("nodeDefinitionId").asText());
        assertEquals(false, json.get("planningAvailable").asBoolean());
        assertEquals("", json.get("nodeInstanceId").asText());
        assertEquals("", json.get("stageId").asText());
        assertEquals(0, json.get("plannedReadFiles").size());
        assertEquals("README.md", json.get("actualReadFiles").get(0).asText());
    }
}
