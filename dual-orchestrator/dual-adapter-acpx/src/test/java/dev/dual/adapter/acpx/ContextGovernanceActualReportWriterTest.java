package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.runtime.handler.AgentOutputContractSidecar;
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
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("exploration-plan-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "agent_exploration_plan",
                        "nodeDefinitionId", "implement.claude_implement",
                        "targetNodeDefinitionId", "implement.claude_implement",
                        "stageId", "implement",
                        "maxDirectReadFiles", 1,
                        "allowedDirectReadFiles", List.of("src/main.py"),
                        "expectedEditFiles", List.of("src/main.py"),
                        "failureMode", "warn"
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
        assertEquals("src/extra.py", json.get("outsideScopeReads").get(0).asText());
        assertEquals("src/extra.py", json.get("outsideScopeWrites").get(0).asText());
        assertEquals("src/main.py", json.get("repeatedReadFiles").get(0).get("path").asText());
        assertEquals(1, json.get("repeatedReadFiles").get(0).get("previousCount").asInt());
        assertEquals(true, json.get("repeatedReadFiles").get(0).get("evidenceKnown").asBoolean());
        assertEquals("warn", json.get("explorationPolicyResult").get("status").asText());
        assertEquals("DIRECT_READ_BUDGET_EXCEEDED",
                json.get("explorationPolicyResult").get("violations").get(0).get("type").asText());
        assertEquals(4, json.get("toolUsage").get("toolCallCount").asInt());

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
        assertEquals(0, json.get("plannedReadFiles").size());
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
                        "readScope", List.of("README.md", "main.py"),
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
        assertEquals(2, json.get("plannedReadFiles").size());
        assertEquals(0, json.get("outsideScopeReads").size());
    }

    @Test
    void invalidScopeExpansionRequestDoesNotWaiveOutsideReads() throws Exception {
        var workspace = tempDir.resolve("repo-invalid-scope-expansion");
        var workstreamId = "ws-004";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("exploration-plan-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "agent_exploration_plan",
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "stageId", "intake",
                        "maxDirectReadFiles", 5,
                        "allowedDirectReadFiles", List.of("README.md"),
                        "preferredCapabilityKinds", List.of("symbol_index"),
                        "failureMode", "warn"
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/intake.transcript.log");
        var output = workstreamDir.resolve("runs/run-001/outputs/intake.md");
        Files.createDirectories(output.getParent());
        Files.writeString(transcript, "transcript");
        Files.writeString(output, """
                <dual-artifact type="agent_output_contract" version="1">
                {"node_id":"intake.claude_brainstorm","decision":"READY_FOR_PRODUCT_REVIEW",
                 "summary":"request is invalid","scope_expansion_request":{"requested_files":["src/extra.py"],"reason":""}}
                </dual-artifact>
                """);

        var report = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "intake.claude_brainstorm",
                "run-001",
                output,
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
                        List.of("src/extra.py"),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertTrue(report.isPresent());
        var json = objectMapper.readTree(report.get().toFile());
        var result = json.get("explorationPolicyResult");
        assertEquals(0, result.get("scopeExpansionFiles").size());
        var violations = new java.util.ArrayList<String>();
        result.get("violations").forEach(item -> violations.add(item.get("type").asText()));
        assertTrue(violations.contains("READ_OUTSIDE_EXPLORATION_SCOPE"), violations.toString());
        assertTrue(violations.contains("SCOPE_EXPANSION_REQUEST_MISSING"), violations.toString());
    }

    @Test
    void rawScopeExpansionRequestDoesNotWaiveWithoutValidatedSidecar() throws Exception {
        var workspace = tempDir.resolve("repo-raw-scope-expansion");
        var workstreamId = "ws-005";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("exploration-plan-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "agent_exploration_plan",
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "stageId", "intake",
                        "maxDirectReadFiles", 5,
                        "allowedDirectReadFiles", List.of("README.md"),
                        "failureMode", "warn"
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/intake.transcript.log");
        var output = workstreamDir.resolve("runs/run-001/outputs/intake.md");
        Files.createDirectories(output.getParent());
        Files.writeString(transcript, "transcript");
        Files.writeString(output, """
                <dual-artifact type="agent_output_contract" version="1">
                {"node_id":"intake.claude_brainstorm","decision":"READY_FOR_PRODUCT_REVIEW",
                 "summary":"request is raw","scope_expansion_request":{"requested_files":["src/extra.py"],"reason":"need boundary check","evidence":"seed lacks route"}}
                </dual-artifact>
                """);

        var report = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "intake.claude_brainstorm",
                "run-001",
                output,
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
                        List.of("src/extra.py"),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertTrue(report.isPresent());
        var result = objectMapper.readTree(report.get().toFile()).get("explorationPolicyResult");
        assertEquals(0, result.get("scopeExpansionFiles").size());
        var violations = new java.util.ArrayList<String>();
        result.get("violations").forEach(item -> violations.add(item.get("type").asText()));
        assertTrue(violations.contains("READ_OUTSIDE_EXPLORATION_SCOPE"), violations.toString());
        assertTrue(violations.contains("SCOPE_EXPANSION_REQUEST_MISSING"), violations.toString());
    }

    @Test
    void validatedScopeExpansionSidecarWaivesOutsideReads() throws Exception {
        var workspace = tempDir.resolve("repo-sidecar-scope-expansion");
        var workstreamId = "ws-006";
        var workstreamDir = workspace.resolve(".agent/workstreams").resolve(workstreamId);
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("exploration-plan-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "agent_exploration_plan",
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "stageId", "intake",
                        "maxDirectReadFiles", 5,
                        "allowedDirectReadFiles", List.of("README.md"),
                        "failureMode", "warn"
                )
        );
        var transcript = workstreamDir.resolve("runs/run-001/outputs/intake.transcript.log");
        var output = workstreamDir.resolve("runs/run-001/outputs/intake.md");
        Files.createDirectories(output.getParent());
        Files.writeString(transcript, "transcript");
        Files.writeString(output, "agent output already validated elsewhere");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                AgentOutputContractSidecar.pathFor(output).toFile(),
                Map.of(
                        "node_id", "intake.claude_brainstorm",
                        "decision", "READY_FOR_PRODUCT_REVIEW",
                        "summary", "validated request",
                        "scope_expansion_request", Map.of(
                                "requested_files", List.of("src/extra.py"),
                                "reason", "need boundary check",
                                "evidence", "seed lacks route"
                        )
                )
        );

        var report = new ContextGovernanceActualReportWriter().write(
                workspace,
                workstreamId,
                "intake.claude_brainstorm",
                "run-001",
                output,
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
                        List.of("src/extra.py"),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertTrue(report.isPresent());
        var result = objectMapper.readTree(report.get().toFile()).get("explorationPolicyResult");
        assertEquals("src/extra.py", result.get("scopeExpansionFiles").get(0).asText());
        assertEquals(0, result.get("violations").size());
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
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("exploration-plan-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "agent_exploration_plan",
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "stageId", "intake",
                        "maxDirectReadFiles", 5,
                        "allowedDirectReadFiles", List.of("docs/product.md"),
                        "preferredCapabilityKinds", List.of("token_filtered_shell"),
                        "failureMode", "warn"
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
    void tokenFilteredSearchDoesNotCountAgainstDirectReadBudget() throws Exception {
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
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                memoryDir.resolve("exploration-plan-node-001.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "agent_exploration_plan",
                        "nodeDefinitionId", "intake.claude_brainstorm",
                        "targetNodeDefinitionId", "intake.claude_brainstorm",
                        "stageId", "intake",
                        "maxDirectReadFiles", 0,
                        "allowedDirectReadFiles", List.of(),
                        "preferredCapabilityKinds", List.of("token_filtered_shell"),
                        "failureMode", "warn"
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
        assertEquals("pass", json.get("explorationPolicyResult").get("status").asText());
        assertEquals("token_filtered_shell", json.get("actualCapabilityUsages").get(0).get("kind").asText());
    }
}
