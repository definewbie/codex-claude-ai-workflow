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
        writeRunWithoutUsage(paths, "run-3", "claude", "plan.claude_plan");

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);

        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var tokenMetrics = map(metrics.get("tokenMetrics"));
        assertEquals(3, number(tokenMetrics.get("runCount")));
        assertEquals(2, number(tokenMetrics.get("measuredRunCount")));

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

        var markdown = Files.readString(paths.workstreamMetricsMarkdown());
        assertTrue(markdown.contains("## Token Metrics"));
        assertTrue(markdown.contains("| intake.claude_brainstorm | claude | completed | 100 | 20 | 10 | 0 | 130 |"));
        assertTrue(markdown.contains("| plan.claude_plan | claude | completed | 0 | 0 | 0 | 0 | 0 |"));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
