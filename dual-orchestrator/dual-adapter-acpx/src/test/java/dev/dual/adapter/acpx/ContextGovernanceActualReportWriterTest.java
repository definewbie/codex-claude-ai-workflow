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
    void writesActualReadWriteTelemetryWithoutPolicyJudgment() throws Exception {
        var workspace = tempDir.resolve("repo");
        var workstreamId = "ws-001";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/main.py"), "print('main')\n");
        Files.writeString(workspace.resolve("src/extra.py"), "print('extra')\n");
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
                List.of("Read", "Edit", "Bash"),
                List.of("src/main.py", "src/extra.py"),
                List.of("src/extra.py"),
                List.of("npm test"),
                List.of("npm")
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
        assertEquals("src/main.py", json.get("recommendedReadFiles").get(0).asText());
        assertEquals("src/main.py", json.get("expectedWriteFiles").get(0).asText());
        assertTrue(json.get("actualDirectReadFiles").toString().contains("src/extra.py"));
        assertEquals("src/extra.py", json.get("actualWriteFiles").get(0).asText());
        assertEquals("src/main.py", json.get("repeatedReadFiles").get(0).get("path").asText());
        assertEquals(1, json.get("repeatedReadFiles").get(0).get("previousCount").asInt());
        assertEquals(true, json.get("repeatedReadFiles").get(0).get("evidenceKnown").asBoolean());
        assertEquals(false, json.has("explorationPolicyResult"));
        assertEquals(false, json.has("outsideScopeReads"));
        assertEquals(false, json.has("outsideScopeWrites"));
        assertEquals(4, json.get("toolUsage").get("toolCallCount").asInt());
        assertEquals("src/main.py", json.get("actualReadEvidence").get(0).get("path").asText());
        assertEquals(64, json.get("actualReadEvidence").get(0).get("sha256").asText().length());

        var metrics = objectMapper.readTree(memoryDir.resolve("memory-metrics.json").toFile());
        assertEquals("memory_metrics", metrics.get("artifactType").asText());
        assertEquals(2, metrics.get("readCount").asInt());
        assertEquals(1, metrics.get("repeatedReadCount").asInt());
        assertEquals(1, metrics.get("allowedRepeatedReadCount").asInt());
        assertEquals(1, metrics.get("workingSetHitCount").asInt());
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
                        List.of("Read"),
                        List.of("README.md"),
                        List.of(),
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
        assertEquals(0, json.get("recommendedReadFiles").size());
        assertEquals("README.md", json.get("actualReadFiles").get(0).asText());
    }

    @Test
    void matchesContextBuilderPolicyByTargetNodeDefinitionId() throws Exception {
        var workspace = tempDir.resolve("repo-target");
        var workstreamId = "ws-003";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("file-relevance-plan-node-context-001.json").toFile(),
                Map.of(
                        "nodeDefinitionId", "context.intake_claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "readCandidates", List.of("README.md", "main.py"),
                        "writeCandidates", List.of()
                )
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("stage-scope-policy-node-context-001.json").toFile(),
                Map.of(
                        "nodeDefinitionId", "context.intake_claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "nodeInstanceId", "node-context-001",
                        "iterationId", "iter-001",
                        "stageId", "intake",
                        "writeScope", List.of()
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/intake.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, "transcript");

        var output = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "intake.claude_brainstorm",
                "run-001",
                transcript,
                new AcpxToolUsage(
                        1,
                        2,
                        2,
                        0,
                        0,
                        0,
                        Map.of("Read", 2),
                        List.of("Read"),
                        List.of("README.md", "main.py"),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertTrue(output.isPresent());
        var json = objectMapper.readTree(output.get().toFile());
        assertEquals(true, json.get("planningAvailable").asBoolean());
        assertEquals("context.intake_claude_brainstorm", json.get("policyNodeDefinitionId").asText());
        assertEquals("intake.claude_brainstorm", json.get("targetNodeDefinitionId").asText());
        assertEquals(2, json.get("recommendedReadFiles").size());
        assertEquals(false, json.has("outsideScopeReads"));
    }

    @Test
    void rtkReadReportsReadFileAndRtkCapabilityWithoutNativeReadProvider() throws Exception {
        var workspace = tempDir.resolve("repo-rtk-read-capability");
        var workstreamId = "ws-007";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("agent-capability-catalog.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "nodeDefinitionId", "other.node",
                        "capabilities", List.of(
                                Map.of("kind", "direct_file_read", "provider", "Read")
                        )
                )
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("agent-capability-catalog-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "capabilities", List.of(
                                Map.of("kind", "direct_file_read", "provider", "Read"),
                                Map.of("kind", "token_filtered_shell", "provider", "rtk")
                        )
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/intake.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, "transcript");

        var report = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "intake.claude_brainstorm",
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
                        List.of("Bash"),
                        List.of("docs/product.md"),
                        List.of(),
                        List.of("rtk read docs/product.md"),
                        List.of("rtk")
                )
        );

        assertTrue(report.isPresent());
        var json = objectMapper.readTree(report.get().toFile());
        assertEquals("docs/product.md", json.get("actualReadFiles").get(0).asText());
        assertEquals(1, json.get("actualCapabilityUsages").size());
        assertEquals("token_filtered_shell", json.get("actualCapabilityUsages").get(0).get("kind").asText());
        assertEquals("rtk", json.get("actualCapabilityUsages").get(0).get("provider").asText());
        assertEquals("command_executable", json.get("actualCapabilityUsages").get(0).get("source").asText());
    }

    @Test
    void tokenFilteredSearchIsReportedWithoutReadPolicyJudgment() throws Exception {
        var workspace = tempDir.resolve("repo-rtk-grep-capability");
        var workstreamId = "ws-008";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("agent-capability-catalog-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "capabilities", List.of(
                                Map.of("kind", "token_filtered_shell", "provider", "rtk")
                        )
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/intake.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, "transcript");

        var report = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "intake.claude_brainstorm",
                "run-001",
                transcript,
                new AcpxToolUsage(
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        Map.of("Grep", 1),
                        List.of("Bash"),
                        List.of("src"),
                        List.of(),
                        List.of(),
                        List.of("rtk grep issueWorkflow src"),
                        List.of("rtk")
                )
        );

        assertTrue(report.isPresent());
        var json = objectMapper.readTree(report.get().toFile());
        assertEquals("src", json.get("actualReadFiles").get(0).asText());
        assertEquals(0, json.get("actualDirectReadFiles").size());
        assertEquals(false, json.has("explorationPolicyResult"));
        assertEquals("token_filtered_shell", json.get("actualCapabilityUsages").get(0).get("kind").asText());
    }
}
