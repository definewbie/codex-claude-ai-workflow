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
        writeRun(paths, "run-snapshot", "codex", "acceptance.codex_review", Map.of(
                "inputTokens", 0,
                "outputTokens", 0,
                "cachedReadTokens", 0,
                "cachedWriteTokens", 0,
                "totalTokens", 0,
                "contextUsedTokens", 50000,
                "source", "acpx_usage_update",
                "measurementType", "SESSION_USED_SNAPSHOT",
                "cumulative", true
        ));
        writeArtifactMetadata(paths, mapper, "run-2", "promptMetadataPath", "prompt.json", 900);
        writeArtifactMetadata(paths, mapper, "run-2", "outputMetadataPath", "output.json", 40);
        writeContextPackageMetadata(paths, mapper, "context-design-review.json", "design.codex_review");
        writeRunWithoutUsage(paths, "run-3", "claude", "plan.claude_plan");
        writeArtifactMetadata(paths, mapper, "run-3", "promptMetadataPath", "prompt.json", 1000);
        writeArtifactMetadata(paths, mapper, "run-3", "outputMetadataPath", "output.json", 200);
        writeArtifactMetadata(paths, mapper, "run-3", "transcriptMetadataPath", "transcript.json", 5000);
        appendToolUsage(paths, mapper, "run-1", 3, 2, 0, 1, List.of("README.md", "src/main.js"), List.of("git status --short"));
        appendToolUsage(paths, mapper, "run-2", 2, 2, 0, 0, List.of("README.md", "docs/spec.md"), List.of());
        writeEvidenceManifest(paths, mapper, List.of("README.md", "docs/spec.md"));
        writeReadReuseContract(paths, mapper, List.of("README.md"));

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);

        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var tokenMetrics = map(metrics.get("tokenMetrics"));
        assertEquals(4, number(tokenMetrics.get("runCount")));
        assertEquals(2, number(tokenMetrics.get("measuredRunCount")));
        assertEquals(1, number(tokenMetrics.get("sessionSnapshotRunCount")));
        assertEquals(1, number(tokenMetrics.get("estimatedRunCount")));

        var total = map(tokenMetrics.get("total"));
        assertEquals(180, number(total.get("inputTokens")));
        assertEquals(50, number(total.get("outputTokens")));
        assertEquals(10, number(total.get("cachedReadTokens")));
        assertEquals(5, number(total.get("cachedWriteTokens")));
        assertEquals(245, number(total.get("totalTokens")));
        assertEquals("0.24", map(total.get("costByCurrency")).get("USD").toString());

        var estimatedTotal = map(tokenMetrics.get("estimatedTotal"));
        assertEquals(1200, number(estimatedTotal.get("totalTokens")));

        var byNode = map(tokenMetrics.get("byNode"));
        assertEquals(130, number(map(byNode.get("intake.claude_brainstorm")).get("totalTokens")));
        assertEquals(115, number(map(byNode.get("design.codex_review")).get("totalTokens")));
        assertTrue(!byNode.containsKey("plan.claude_plan"));

        var runs = list(tokenMetrics.get("runs"));
        var snapshotRun = runs.stream()
                .filter(run -> "acceptance.codex_review".equals(run.get("nodeId")))
                .findFirst()
                .orElseThrow();
        assertEquals(false, snapshotRun.get("aggregated"));
        assertEquals("SESSION_USED_SNAPSHOT", map(snapshotRun.get("usage")).get("measurementType"));
        assertEquals(50000, number(map(snapshotRun.get("usage")).get("contextUsedTokens")));
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

        var toolMetrics = map(metrics.get("toolMetrics"));
        assertEquals(5, number(toolMetrics.get("toolCallCount")));
        assertEquals(4, number(toolMetrics.get("readCount")));
        assertEquals(1, number(toolMetrics.get("bashCount")));
        var repeatedReads = list(toolMetrics.get("repeatedReadFiles"));
        assertEquals("README.md", repeatedReads.get(0).get("path"));
        assertEquals(2, number(repeatedReads.get(0).get("count")));
        assertEquals(true, repeatedReads.get(0).get("evidenceKnown"));
        assertEquals(true, repeatedReads.get(0).get("rereadReasonRequired"));
        assertEquals("missing", repeatedReads.get(0).get("rereadReasonStatus"));
        assertEquals(1, number(toolMetrics.get("repeatedReadReasonRequiredCount")));
        assertEquals(1, number(toolMetrics.get("missingRereadReasonCount")));

        var markdown = Files.readString(paths.workstreamMetricsMarkdown());
        assertTrue(markdown.contains("## Token Metrics"));
        assertTrue(markdown.contains("- Estimated Runs: 1"));
        assertTrue(markdown.contains("- Session Snapshots: 1"));
        assertTrue(markdown.contains("| intake.claude_brainstorm | claude | completed | - | acpx_stream | 100 | 20 | 0 | 0 | 0 | 100 | 130 |"));
        assertTrue(markdown.contains("| design.codex_review | codex | completed | - | acpx_stream | 80 | 30 | 0 | 600 | 300 | 0 | 115 |"));
        assertTrue(markdown.contains("| plan.claude_plan | claude | completed | - | artifact_estimate | 1000 | 200 | 5000 | 0 | 1000 | 0 | 1200 |"));
        assertTrue(markdown.contains("### Top Context Sections"));
        assertTrue(markdown.contains("| design.codex_review | review | recent_agent_outputs | 420 | 900 | true | false |"));
        assertTrue(markdown.contains("## Tool Metrics"));
        assertTrue(markdown.contains("| README.md | 2 | claude, codex | intake.claude_brainstorm, design.codex_review | true | missing |"));
    }

    @Test
    void repeatedReadWithMatchingRereadReasonIsRecorded() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-reread"));
        var jsonWriter = new AtomicJsonWriter();
        var mapper = jsonWriter.objectMapper();
        setupRereadFixture(paths, jsonWriter, mapper, List.of(Map.of(
                "path", "README.md",
                "reason", "exact current content is needed before editing")));

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);
        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var repeated = list(map(metrics.get("toolMetrics")).get("repeatedReadFiles"));
        assertEquals("README.md", repeated.get(0).get("path"));
        assertEquals("recorded", repeated.get(0).get("rereadReasonStatus"));
    }

    @Test
    void repeatedReadWithoutRereadReasonIsMissing() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-reread-missing"));
        var jsonWriter = new AtomicJsonWriter();
        var mapper = jsonWriter.objectMapper();
        setupRereadFixture(paths, jsonWriter, mapper, null);

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);
        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var repeated = list(map(metrics.get("toolMetrics")).get("repeatedReadFiles"));
        assertEquals("missing", repeated.get(0).get("rereadReasonStatus"));
    }

    @Test
    void repeatedReadWithNonMatchingPathReasonIsMissing() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-reread-other"));
        var jsonWriter = new AtomicJsonWriter();
        var mapper = jsonWriter.objectMapper();
        setupRereadFixture(paths, jsonWriter, mapper, List.of(Map.of(
                "path", "src/other.js",
                "reason", "exact current content is needed before editing")));

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);
        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var repeated = list(map(metrics.get("toolMetrics")).get("repeatedReadFiles"));
        assertEquals("README.md", repeated.get(0).get("path"));
        assertEquals("missing", repeated.get(0).get("rereadReasonStatus"));
    }

    private void setupRereadFixture(
            WorkstreamPaths paths,
            AtomicJsonWriter jsonWriter,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            List<Map<String, Object>> secondRunRereadReasons
    ) throws Exception {
        jsonWriter.write(paths.workflowCurrentJson(), new WorkflowCurrentDocument(
                1, paths.workstreamId().value(), "wf-instance", "agent-lifecycle", 1,
                WorkflowStatus.RUNNING, List.of("n1", "n2"), List.of(), "evt-1",
                Instant.parse("2026-06-19T00:00:00Z")));
        jsonWriter.write(paths.nodeJson("n1"), node("n1", "intake.claude_brainstorm"));
        jsonWriter.write(paths.nodeJson("n2"), node("n2", "implement.claude_implement"));
        writeRun(paths, "run-1", "claude", "intake.claude_brainstorm", null);
        appendToolUsage(paths, mapper, "run-1", 1, 1, 0, 0, List.of("README.md"), List.of());
        writeRun(paths, "run-2", "claude", "implement.claude_implement", null);
        appendToolUsage(paths, mapper, "run-2", 1, 1, 0, 0, List.of("README.md"), List.of());
        if (secondRunRereadReasons != null) {
            writeRereadReasons(paths, mapper, "run-2", secondRunRereadReasons);
        }
        writeReadReuseContract(paths, mapper, List.of("README.md"));
    }

    private void writeRereadReasons(
            WorkstreamPaths paths,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            String runId,
            List<Map<String, Object>> reasons
    ) throws Exception {
        var runJson = paths.runsDir().resolve(runId).resolve("run.json");
        var run = new java.util.LinkedHashMap<>(mapper.readValue(runJson.toFile(), Map.class));
        run.put("reread_reasons", reasons);
        new AtomicJsonWriter().write(runJson, run);
    }

    @Test
    void tokenAttributionJoinsTokensToolsTranscriptAndFileCost() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-attr"));
        var jsonWriter = new AtomicJsonWriter();
        var mapper = jsonWriter.objectMapper();

        jsonWriter.write(paths.workflowCurrentJson(), new WorkflowCurrentDocument(
                1, "ws-attr", "wf", "agent-lifecycle", 1, WorkflowStatus.RUNNING,
                List.of("n1", "n2"), List.of(), "e1", Instant.parse("2026-06-19T00:00:00Z")));
        jsonWriter.write(paths.nodeJson("n1"), node("n1", "intake.claude_brainstorm"));
        jsonWriter.write(paths.nodeJson("n2"), node("n2", "implement.claude_implement"));

        Files.writeString(tempDir.resolve("README.md"), "R".repeat(400));
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/a.js"), "export function a() {}\n");
        Files.createDirectories(tempDir.resolve("transcripts"));
        Files.writeString(tempDir.resolve("transcripts/run-1.log"), "x".repeat(4000));
        Files.writeString(tempDir.resolve("transcripts/run-2.log"), "x".repeat(12000));

        writeAttributionRun(paths, "run-1", "claude", "intake.claude_brainstorm", "seg-1",
                Instant.parse("2026-06-19T00:00:00Z"), usage(100, 20, 500, "0.10"),
                "transcripts/run-1.log", 1, List.of("README.md"));
        writeAttributionRun(paths, "run-2", "claude", "implement.claude_implement", "seg-1",
                Instant.parse("2026-06-19T00:01:00Z"), usage(200, 40, 5000, "0.20"),
                "transcripts/run-2.log", 2, List.of("README.md", "src/a.js"));
        writeAttributionRun(paths, "run-codex", "codex", "acceptance.codex_verifier_review", "seg-2",
                Instant.parse("2026-06-19T00:02:00Z"), snapshotUsage(64000),
                "", 0, List.of());
        writeReadReuseContract(paths, mapper, List.of("README.md"));

        new MetricsProjectionWriter(jsonWriter, mapper).write(paths);
        var metrics = mapper.readValue(paths.workstreamMetricsJson().toFile(), Map.class);
        var attr = map(metrics.get("tokenAttribution"));
        assertEquals(1, number(attr.get("schemaVersion")));

        var perRun = list(attr.get("perRun"));
        assertEquals(3, perRun.size());
        var r2 = map(perRun.stream().filter(r -> "run-2".equals(map(r).get("runId"))).findFirst().orElseThrow());
        assertEquals(200, number(r2.get("inputTokens")));
        assertEquals(5000, number(r2.get("cachedReadTokens")));
        assertEquals(2, number(r2.get("reads")));
        assertEquals(12000, number(r2.get("transcriptBytes")));
        assertEquals("seg-1", r2.get("sessionSegmentKey"));
        assertEquals("provider_run_usage", r2.get("providerUsageVisibility"));

        var codex = map(perRun.stream().filter(r -> "run-codex".equals(map(r).get("runId"))).findFirst().orElseThrow());
        assertEquals("session_snapshot_only", codex.get("providerUsageVisibility"));
        assertEquals(64000, number(codex.get("contextUsedTokens")));
        assertTrue(String.valueOf(codex.get("providerUsageUnavailableReason")).contains("usage_update"));

        var visibility = map(attr.get("usageVisibility"));
        assertEquals(2, number(map(visibility.get("byVisibility")).get("provider_run_usage")));
        assertEquals(1, number(map(visibility.get("byVisibility")).get("session_snapshot_only")));
        var codexVisibility = map(map(visibility.get("byAgent")).get("codex"));
        assertEquals(1, number(codexVisibility.get("session_snapshot_only")));
        assertEquals(1, list(visibility.get("gaps")).size());

        var hotspots = list(attr.get("nodeHotspots"));
        var impl = map(hotspots.stream()
                .filter(n -> "implement.claude_implement".equals(map(n).get("nodeId"))).findFirst().orElseThrow());
        assertEquals(5000, number(impl.get("cachedReadTokens")));
        assertTrue(((List<?>) impl.get("heavy")).toString().toLowerCase().contains("cachedread"),
                "implement node should be cachedRead-heavy: " + impl.get("heavy"));

        var files = list(attr.get("fileReadCost"));
        var readme = map(files.stream()
                .filter(f -> "README.md".equals(map(f).get("path"))).findFirst().orElseThrow());
        assertEquals(2, number(readme.get("readCount")));
        assertEquals(400, number(readme.get("fileBytes")));
        assertTrue(number(readme.get("estimatedReadTokens")) > 0);
        assertEquals(true, readme.get("reusable"));
        assertEquals("missing", readme.get("rereadReasonStatus"));

        var sessions = list(attr.get("sessionSnowball"));
        assertEquals(2, sessions.size());
        var seg = map(sessions.stream()
                .filter(s -> "seg-1".equals(map(s).get("sessionSegmentKey")))
                .findFirst()
                .orElseThrow());
        assertEquals("seg-1", seg.get("sessionSegmentKey"));
        assertEquals("run-2", seg.get("maxTranscriptGrowthRun"));
        assertEquals(2, list(seg.get("runs")).size());
    }

    private void writeAttributionRun(WorkstreamPaths paths, String runId, String agent, String nodeId,
                                     String sessionSegmentKey, Instant startedAt, Map<String, Object> usageMap,
                                     String transcriptPath, int toolCalls, List<String> readFiles) throws Exception {
        var run = new java.util.LinkedHashMap<String, Object>();
        run.put("schemaVersion", 1);
        run.put("runId", runId);
        run.put("workstreamId", paths.workstreamId().value());
        run.put("nodeId", nodeId);
        run.put("agent", agent);
        run.put("status", "completed");
        run.put("stage", nodeId.split("\\.")[0]);
        run.put("sessionScope", "workstream");
        run.put("sessionSegmentKey", sessionSegmentKey);
        run.put("startedAt", startedAt.toString());
        run.put("durationMs", 1000);
        run.put("usage", usageMap);
        run.put("transcriptPath", transcriptPath);
        run.put("toolUsage", Map.of(
                "schemaVersion", 1, "toolCallCount", toolCalls, "readCount", readFiles.size(),
                "writeCount", 0, "bashCount", 0, "otherCount", 0,
                "readFiles", readFiles, "writeFiles", List.of(), "bashCommands", List.of()));
        var runDir = paths.runsDir().resolve(runId);
        Files.createDirectories(runDir);
        new AtomicJsonWriter().write(runDir.resolve("run.json"), run);
    }

    private Map<String, Object> usage(long input, long output, long cachedRead, String cost) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("inputTokens", input);
        m.put("outputTokens", output);
        m.put("cachedReadTokens", cachedRead);
        m.put("cachedWriteTokens", 0);
        m.put("totalTokens", input + output + cachedRead);
        m.put("costAmount", new BigDecimal(cost));
        m.put("costCurrency", "USD");
        m.put("source", "acpx_stream");
        m.put("measurementType", "PROVIDER_RUN_USAGE");
        m.put("attribution", "LATEST_PROMPT_IN_SESSION");
        m.put("cumulative", false);
        return m;
    }

    private Map<String, Object> snapshotUsage(long contextUsed) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("inputTokens", 0);
        m.put("outputTokens", 0);
        m.put("cachedReadTokens", 0);
        m.put("cachedWriteTokens", 0);
        m.put("totalTokens", 0);
        m.put("contextUsedTokens", contextUsed);
        m.put("source", "acpx_usage_update");
        m.put("measurementType", "SESSION_USED_SNAPSHOT");
        m.put("attribution", "SESSION_USAGE_UPDATE");
        m.put("cumulative", true);
        return m;
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
                "costAmount", new BigDecimal(cost),
                "source", "acpx_stream",
                "measurementType", "PROVIDER_RUN_USAGE",
                "cumulative", false
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

    private void appendToolUsage(
            WorkstreamPaths paths,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            String runId,
            long toolCallCount,
            long readCount,
            long writeCount,
            long bashCount,
            List<String> readFiles,
            List<String> bashCommands
    ) throws Exception {
        var runJson = paths.runsDir().resolve(runId).resolve("run.json");
        var run = new java.util.LinkedHashMap<>(mapper.readValue(runJson.toFile(), Map.class));
        run.put("toolUsage", Map.of(
                "schemaVersion", 1,
                "toolCallCount", toolCallCount,
                "readCount", readCount,
                "writeCount", writeCount,
                "bashCount", bashCount,
                "otherCount", Math.max(0, toolCallCount - readCount - writeCount - bashCount),
                "readFiles", readFiles,
                "writeFiles", List.of(),
                "bashCommands", bashCommands
        ));
        new AtomicJsonWriter().write(runJson, run);
    }

    private void writeEvidenceManifest(
            WorkstreamPaths paths,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            List<String> files
    ) throws Exception {
        var memoryDir = paths.workstreamDir().resolve("memory");
        Files.createDirectories(memoryDir);
        var item = new java.util.LinkedHashMap<String, Object>();
        item.put("id", "task_contract:1");
        item.put("type", "task_contract");
        item.put("files", files);
        mapper.writerWithDefaultPrettyPrinter().writeValue(memoryDir.resolve("evidence-manifest.json").toFile(), Map.of(
                "schemaVersion", 1,
                "artifactType", "evidence_manifest",
                "items", List.of(item)
        ));
    }

    private void writeReadReuseContract(
            WorkstreamPaths paths,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            List<String> files
    ) throws Exception {
        var memoryDir = paths.workstreamDir().resolve("memory");
        Files.createDirectories(memoryDir);
        var reusable = new java.util.ArrayList<Map<String, Object>>();
        for (var file : files) {
            reusable.add(Map.of(
                    "path", file,
                    "recommendation", "reuse_existing_summary"
            ));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(memoryDir.resolve("read-reuse-contract.json").toFile(), Map.of(
                "schemaVersion", 1,
                "artifactType", "read_reuse_contract",
                "requireReasonWhenRereading", true,
                "allowedRepeatedReadReasons", List.of(
                        "file changed after prior read",
                        "exact current content is needed before editing",
                        "previous evidence does not cover the selected stage"
                ),
                "reusableReadFiles", reusable
        ));
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
