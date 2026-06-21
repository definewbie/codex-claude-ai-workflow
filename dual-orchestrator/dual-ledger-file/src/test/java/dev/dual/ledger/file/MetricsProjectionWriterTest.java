package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsProjectionWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void projectsTokenUsageByRunAgentAndNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-token"));
        var jsonWriter = new AtomicJsonWriter();
        var mapper = jsonWriter.objectMapper();

        jsonWriter.write(paths.workflowCurrentJson(), new WorkflowCurrentDocument(
                1,
                "ws-token",
                "wf-instance",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of("node-intake-001"),
                List.of(),
                "evt-1",
                Instant.parse("2026-06-19T00:00:00Z")
        ));
        jsonWriter.write(paths.nodeJson("node-intake-001"), node("node-intake-001", "intake.claude_brainstorm"));
        jsonWriter.write(paths.nodeJson("node-review-002"), node("node-review-002", "design.codex_review"));
        writeRun(paths, "run-1", "claude", "intake.claude_brainstorm", 100, 20, 10, 0, 130, "USD", "0.13");
        writeRun(paths, "run-2", "codex", "design.codex_review", 80, 30, 0, 5, 115, "USD", "0.11");
        writeArtifactMetadata(paths, mapper, "run-2", "promptMetadataPath", "prompt.json", 900);
        writeArtifactMetadata(paths, mapper, "run-2", "outputMetadataPath", "output.json", 40);
        writeContextPackageMetadata(paths, mapper, "context-design-review.json", "design.codex_review");
        writeRunWithoutUsage(paths, "run-3", "claude", "plan.claude_plan");

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);

        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var tokenMetrics = map(metrics.get("tokenMetrics"));
        assertEquals(3, number(tokenMetrics.get("runCount")));
        assertEquals(2, number(tokenMetrics.get("measuredRunCount")));
        assertEquals(0, number(tokenMetrics.get("estimatedRunCount")));

        var total = map(tokenMetrics.get("total"));
        assertEquals(180, number(total.get("inputTokens")));
        assertEquals(50, number(total.get("outputTokens")));
        assertEquals(10, number(total.get("cachedReadTokens")));
        assertEquals(5, number(total.get("cachedWriteTokens")));
        assertEquals(245, number(total.get("totalTokens")));
        assertEquals("0.24", map(total.get("costByCurrency")).get("USD").toString());

        var byNode = map(tokenMetrics.get("byNode"));
        assertEquals(130, number(map(byNode.get("intake.claude_brainstorm")).get("totalTokens")));
        assertEquals(115, number(map(byNode.get("design.codex_review")).get("totalTokens")));

        var runs = list(tokenMetrics.get("runs"));
        var codexRun = runs.stream()
                .filter(run -> "design.codex_review".equals(run.get("nodeId")))
                .findFirst()
                .orElseThrow();
        var breakdown = map(codexRun.get("breakdown"));
        assertEquals(900, number(breakdown.get("promptTokens")));
        assertEquals(600, number(breakdown.get("contextTokens")));
        assertEquals(300, number(breakdown.get("promptOverheadTokens")));
        assertEquals(0, number(breakdown.get("adapterOverheadTokens")));
        assertEquals(40, number(breakdown.get("outputArtifactTokens")));
        assertEquals("review", breakdown.get("contextProfile"));
        var topSections = list(breakdown.get("topSections"));
        assertEquals("recent_agent_outputs", topSections.get(0).get("section"));
        assertEquals(420, number(topSections.get(0).get("renderedTokens")));

        var globalTopSections = list(tokenMetrics.get("topContextSections"));
        assertEquals("design.codex_review", globalTopSections.get(0).get("targetNode"));
        assertEquals("recent_agent_outputs", globalTopSections.get(0).get("section"));

        var markdown = Files.readString(paths.workstreamMetricsMarkdown());
        assertTrue(markdown.contains("## Token Metrics"));
        assertTrue(markdown.contains("- Estimated Runs: 0"));
        assertTrue(markdown.contains("| intake.claude_brainstorm | claude | completed | acpx_stream | 100 | 20 | 0 | 0 | 100 | 130 |"));
        assertTrue(markdown.contains("| design.codex_review | codex | completed | acpx_stream | 80 | 30 | 600 | 300 | 0 | 115 |"));
        assertTrue(markdown.contains("| plan.claude_plan | claude | completed | unknown | 0 | 0 | 0 | 0 | 0 | 0 |"));
        assertTrue(markdown.contains("### Top Context Sections"));
        assertTrue(markdown.contains("| design.codex_review | review | recent_agent_outputs | 420 | 900 | true | false |"));
    }

    private NodeInstanceDocument node(String instanceId, String definitionId) {
        return new NodeInstanceDocument(
                1,
                instanceId,
                definitionId,
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.COMPLETED,
                "main",
                "iter-001",
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                "",
                "",
                Instant.parse("2026-06-19T00:00:00Z"),
                Instant.parse("2026-06-19T00:00:01Z")
        );
    }

    private void writeRun(
            WorkstreamPaths paths,
            String runId,
            String agent,
            String nodeId,
            long inputTokens,
            long outputTokens,
            long cachedReadTokens,
            long cachedWriteTokens,
            long totalTokens,
            String currency,
            String cost
    ) throws Exception {
        writeRun(paths, runId, agent, nodeId, Map.of(
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "cachedReadTokens", cachedReadTokens,
                "cachedWriteTokens", cachedWriteTokens,
                "totalTokens", totalTokens,
                "costCurrency", currency,
                "costAmount", new BigDecimal(cost)
        ));
    }

    private void writeRunWithoutUsage(WorkstreamPaths paths, String runId, String agent, String nodeId) throws Exception {
        writeRun(paths, runId, agent, nodeId, null);
    }

    private void writeRun(WorkstreamPaths paths, String runId, String agent, String nodeId, Map<String, Object> usage) throws Exception {
        var run = new java.util.LinkedHashMap<String, Object>();
        run.put("schemaVersion", 1);
        run.put("runId", runId);
        run.put("workstreamId", paths.workstreamId().value());
        run.put("nodeId", nodeId);
        run.put("agent", agent);
        run.put("status", "completed");
        run.put("durationMs", 1000);
        if (usage != null) {
            run.put("usage", usage);
        }
        var runDir = paths.runsDir().resolve(runId);
        Files.createDirectories(runDir);
        new AtomicJsonWriter().write(runDir.resolve("run.json"), run);
    }

    private void writeArtifactMetadata(
            WorkstreamPaths paths,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            String runId,
            String metadataField,
            String metadataFilename,
            long estimatedTokens
    ) throws Exception {
        var runJson = paths.runsDir().resolve(runId).resolve("run.json");
        var run = new java.util.LinkedHashMap<>(mapper.readValue(runJson.toFile(), Map.class));
        var metadataPath = paths.runsDir().resolve(runId).resolve("metadata").resolve(metadataFilename);
        Files.createDirectories(metadataPath.getParent());
        new AtomicJsonWriter().write(metadataPath, Map.of("estimatedTokens", estimatedTokens));
        run.put(metadataField, paths.workspaceRoot().relativize(metadataPath).toString());
        new AtomicJsonWriter().write(runJson, run);
    }

    private void writeContextPackageMetadata(
            WorkstreamPaths paths,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            String filename,
            String targetNode
    ) throws Exception {
        var contextDir = paths.workstreamDir().resolve("context");
        Files.createDirectories(contextDir);
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("schemaVersion", 1);
        metadata.put("workstreamId", paths.workstreamId().value());
        metadata.put("builderNode", "context.design_codex_review");
        metadata.put("targetNode", targetNode);
        metadata.put("profile", "review");
        metadata.put("contextPackage", ".agent/workstreams/ws-token/context/context-design-review.md");
        metadata.put("estimatedTokens", 600);
        metadata.put("tokenBudget", 1200);
        metadata.put("withinBudget", true);
        metadata.put("sections", List.of(
                section("recent_agent_outputs", 420, 900, 500, true, false),
                section("task_contract", 120, 120, 200, false, false),
                section("git_evidence", 60, 60, 200, false, false)
        ));
        mapper.writerWithDefaultPrettyPrinter().writeValue(contextDir.resolve(filename).toFile(), metadata);
    }

    private Map<String, Object> section(String name, long rendered, long original, long softBudget, boolean degraded, boolean dropped) {
        return Map.of(
                "name", name,
                "renderedTokens", rendered,
                "originalTokens", original,
                "softBudgetTokens", softBudget,
                "degraded", degraded,
                "dropped", dropped
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
