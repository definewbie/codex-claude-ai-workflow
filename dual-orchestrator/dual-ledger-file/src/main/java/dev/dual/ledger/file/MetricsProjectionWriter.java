package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MetricsProjectionWriter {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;

    MetricsProjectionWriter(AtomicJsonWriter jsonWriter, ObjectMapper objectMapper) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
    }

    void write(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.workflowCurrentJson())) {
            return;
        }
        var current = objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
        var nodes = readNodes(paths);
        var iterations = readIterations(paths);
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("schemaVersion", 1);
        metrics.put("workstreamId", current.workstreamId());
        metrics.put("workflowInstanceId", current.workflowInstanceId());
        metrics.put("workflowId", current.workflowId());
        metrics.put("definitionVersion", current.definitionVersion());
        metrics.put("workflowStatus", current.status());
        metrics.put("generatedAt", Instant.now().toString());
        metrics.put("updatedAt", current.updatedAt());
        metrics.put("nodeCount", nodes.size());
        metrics.put("nodeStatusCounts", counts(nodes, node -> node.status().name()));
        metrics.put("nodeKindCounts", counts(nodes, node -> node.kind().name()));
        metrics.put("agentRunCount", nodes.stream().filter(this::isAgentRun).count());
        metrics.put("gateCount", nodes.stream().filter(node -> node.kind() == NodeKind.GATE).count());
        metrics.put("decisionCount", nodes.stream().filter(node -> node.kind() == NodeKind.DECISION).count());
        metrics.put("iterationCount", iterations.size());
        metrics.put("revisionCount", iterations.stream().filter(this::isRevisionIteration).count());
        metrics.put("completedCount", nodes.stream().filter(node -> node.status() == NodeStatus.COMPLETED).count());
        metrics.put("failedCount", nodes.stream().filter(node -> node.status() == NodeStatus.FAILED).count());
        metrics.put("blockedCount", nodes.stream().filter(node -> node.status() == NodeStatus.BLOCKED).count());
        metrics.put("waitingCount", nodes.stream().filter(node -> node.status() == NodeStatus.WAITING).count());
        metrics.put("runningCount", nodes.stream().filter(node -> node.status() == NodeStatus.RUNNING).count());
        metrics.put("totalNodeDurationMs", totalDurationMs(nodes));
        metrics.put("longestNodes", longestNodes(nodes, 5));
        metrics.put("providerMetrics", providerMetrics(paths));
        metrics.put("tokenMetrics", tokenMetrics(paths));
        metrics.put("toolMetrics", toolMetrics(paths));

        Files.createDirectories(paths.metricsDir());
        jsonWriter.write(paths.workstreamMetricsJson(), metrics);
        Files.writeString(paths.workstreamMetricsMarkdown(), renderMarkdown(paths, metrics), StandardCharsets.UTF_8);
    }

    private List<NodeInstanceDocument> readNodes(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.nodesDir())) {
            return List.of();
        }
        try (var stream = Files.list(paths.nodesDir())) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> {
                        try {
                            return objectMapper.readValue(path.toFile(), NodeInstanceDocument.class);
                        } catch (IOException e) {
                            throw new MetricsProjectionException(e);
                        }
                    })
                    .toList();
        } catch (MetricsProjectionException e) {
            throw e.unwrap();
        }
    }

    private List<WorkflowIterationDocument> readIterations(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.iterationsDir())) {
            return List.of();
        }
        try (var stream = Files.list(paths.iterationsDir())) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> {
                        try {
                            return objectMapper.readValue(path.toFile(), WorkflowIterationDocument.class);
                        } catch (IOException e) {
                            throw new MetricsProjectionException(e);
                        }
                    })
                    .toList();
        } catch (MetricsProjectionException e) {
            throw e.unwrap();
        }
    }

    private Map<String, Object> providerMetrics(WorkstreamPaths paths) throws IOException {
        if (!Files.isDirectory(paths.runsDir())) {
            return Map.of();
        }
        var byAgent = new LinkedHashMap<String, ProviderMetric>();
        var outcomeStatusCounts = new LinkedHashMap<String, Long>();
        try (var stream = Files.list(paths.runsDir())) {
            for (var runDir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                var run = objectMapper.readValue(runJson.toFile(), MAP_TYPE);
                var agent = text(run.get("agent"));
                if (agent.isBlank()) {
                    agent = "unknown";
                }
                var metric = byAgent.computeIfAbsent(agent, ignored -> new ProviderMetric());
                metric.accept(text(run.get("status")), longValue(run.get("durationMs")), Boolean.TRUE.equals(run.get("recoverable")));
                var outcomeStatus = text(map(run.get("executionOutcome")).get("status"));
                if (!outcomeStatus.isBlank()) {
                    outcomeStatusCounts.merge(outcomeStatus, 1L, Long::sum);
                }
            }
        }
        var result = new LinkedHashMap<String, Object>();
        for (var entry : byAgent.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMap());
        }
        result.put("outcomeStatusCounts", outcomeStatusCounts);
        return result;
    }

    private Map<String, Object> tokenMetrics(WorkstreamPaths paths) throws IOException {
        if (!Files.isDirectory(paths.runsDir())) {
            return Map.of(
                    "runCount", 0,
                    "measuredRunCount", 0,
                    "sessionSnapshotRunCount", 0,
                    "total", new UsageMetric().toMap(),
                    "byAgent", Map.of(),
                    "byNode", Map.of(),
                    "runs", List.of()
            );
        }
        var total = new UsageMetric();
        var estimatedTotal = new UsageMetric();
        var byAgent = new LinkedHashMap<String, UsageMetric>();
        var byNode = new LinkedHashMap<String, UsageMetric>();
        var runs = new ArrayList<Map<String, Object>>();
        var contextPackages = contextPackagesByTargetNode(paths);
        long runCount = 0;
        long measuredRunCount = 0;
        long estimatedRunCount = 0;
        long sessionSnapshotRunCount = 0;
        try (var stream = Files.list(paths.runsDir())) {
            for (var runDir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                runCount++;
                var run = objectMapper.readValue(runJson.toFile(), MAP_TYPE);
                var usage = map(run.get("usage"));
                var agent = nonBlank(text(run.get("agent")), "unknown");
                var nodeId = nonBlank(text(run.get("nodeId")), "unknown");
                var item = new LinkedHashMap<String, Object>();
                item.put("runId", nonBlank(text(run.get("runId")), runDir.getFileName().toString()));
                item.put("nodeId", nodeId);
                item.put("agent", agent);
                item.put("status", text(run.get("status")));
                item.put("outcomeStatus", text(map(run.get("executionOutcome")).get("status")));
                item.put("outcomePrimaryReason", text(map(run.get("executionOutcome")).get("primaryReason")));
                item.put("durationMs", longValue(run.get("durationMs")));
                var promptTokens = estimatedTokens(paths, text(run.get("promptMetadataPath")));
                var outputArtifactTokens = estimatedTokens(paths, text(run.get("outputMetadataPath")));
                var transcriptTokens = estimatedTokens(paths, text(run.get("transcriptMetadataPath")));
                var estimated = false;
                if (usage.isEmpty()) {
                    usage = estimatedUsage(paths, run);
                    estimated = !usage.isEmpty();
                } else {
                    if (isSessionSnapshot(usage)) {
                        sessionSnapshotRunCount++;
                    } else {
                        measuredRunCount++;
                    }
                }
                if (usage.isEmpty()) {
                    item.put("measured", false);
                    item.put("estimated", false);
                } else {
                    if (estimated) {
                        estimatedRunCount++;
                    }
                    var metric = UsageMetric.from(usage);
                    var aggregatable = isAggregatableUsage(usage);
                    if (aggregatable) {
                        total.add(metric);
                        byAgent.computeIfAbsent(agent, ignored -> new UsageMetric()).add(metric);
                        byNode.computeIfAbsent(nodeId, ignored -> new UsageMetric()).add(metric);
                    } else if (estimated) {
                        estimatedTotal.add(metric);
                    }
                    item.put("measured", !estimated);
                    item.put("estimated", estimated);
                    item.put("aggregated", aggregatable);
                    var usageMap = metric.toMap();
                    usageMap.put("source", nonBlank(text(usage.get("source")), estimated ? "artifact_estimate" : "acpx_stream"));
                    usageMap.put("measurementType", measurementType(usage, estimated));
                    usageMap.put("attribution", nonBlank(text(usage.get("attribution")), estimated ? "ARTIFACT_ESTIMATE" : ""));
                    usageMap.put("cumulative", Boolean.TRUE.equals(usage.get("cumulative")));
                    item.put("usage", usageMap);
                    item.put("breakdown", promptBreakdown(
                            promptTokens,
                            outputArtifactTokens,
                            transcriptTokens,
                            usage,
                            contextPackages.get(nodeId)
                    ));
                }
                runs.add(item);
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("runCount", runCount);
        result.put("measuredRunCount", measuredRunCount);
        result.put("estimatedRunCount", estimatedRunCount);
        result.put("sessionSnapshotRunCount", sessionSnapshotRunCount);
        result.put("total", total.toMap());
        result.put("estimatedTotal", estimatedTotal.toMap());
        result.put("byAgent", usageMap(byAgent));
        result.put("byNode", usageMap(byNode));
        result.put("runs", runs);
        result.put("contextPackages", summarizeContextPackages(contextPackages));
        result.put("topContextSections", topContextSections(contextPackages, 10));
        return result;
    }

    private Map<String, Object> toolMetrics(WorkstreamPaths paths) throws IOException {
        if (!Files.isDirectory(paths.runsDir())) {
            return Map.of(
                    "runCount", 0,
                    "toolCallCount", 0,
                    "readCount", 0,
                    "writeCount", 0,
                    "bashCount", 0,
                    "repeatedReadFiles", List.of(),
                    "topReadFiles", List.of(),
                    "runs", List.of()
            );
        }
        var total = new ToolMetric();
        var readFiles = new LinkedHashMap<String, ReadFileMetric>();
        var runs = new ArrayList<Map<String, Object>>();
        var evidenceFiles = evidenceFiles(paths);
        long runCount = 0;
        try (var stream = Files.list(paths.runsDir())) {
            for (var runDir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                runCount++;
                var run = objectMapper.readValue(runJson.toFile(), MAP_TYPE);
                var toolUsage = map(run.get("toolUsage"));
                var runId = nonBlank(text(run.get("runId")), runDir.getFileName().toString());
                var nodeId = nonBlank(text(run.get("nodeId")), "unknown");
                var agent = nonBlank(text(run.get("agent")), "unknown");
                var metric = ToolMetric.from(toolUsage);
                total.add(metric);
                for (var file : textList(toolUsage.get("readFiles"))) {
                    var normalized = normalizePath(file);
                    if (normalized.isBlank() || ignoredTelemetryFile(normalized)) {
                        continue;
                    }
                    readFiles.computeIfAbsent(normalized, ReadFileMetric::new)
                            .accept(runId, nodeId, agent, evidenceFiles.contains(normalized));
                }
                var item = new LinkedHashMap<String, Object>();
                item.put("runId", runId);
                item.put("nodeId", nodeId);
                item.put("agent", agent);
                item.put("status", text(run.get("status")));
                item.put("toolUsage", metric.toMap());
                item.put("readFiles", textList(toolUsage.get("readFiles")));
                runs.add(item);
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("runCount", runCount);
        result.putAll(total.toMap());
        result.put("repeatedReadFiles", readFiles.values().stream()
                .filter(ReadFileMetric::isRepeated)
                .sorted(Comparator
                        .comparingInt(ReadFileMetric::count).reversed()
                        .thenComparing(ReadFileMetric::path))
                .limit(20)
                .map(ReadFileMetric::toMap)
                .toList());
        result.put("topReadFiles", readFiles.values().stream()
                .sorted(Comparator
                        .comparingInt(ReadFileMetric::count).reversed()
                        .thenComparing(ReadFileMetric::path))
                .limit(20)
                .map(ReadFileMetric::toMap)
                .toList());
        result.put("evidenceFileCount", evidenceFiles.size());
        result.put("runs", runs);
        return result;
    }

    private Set<String> evidenceFiles(WorkstreamPaths paths) throws IOException {
        var manifest = paths.workstreamDir().resolve("memory").resolve("evidence-manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return Set.of();
        }
        var document = objectMapper.readValue(manifest.toFile(), MAP_TYPE);
        var files = new LinkedHashSet<String>();
        for (var item : mapList(document.get("items"))) {
            for (var file : textList(item.get("files"))) {
                var normalized = normalizePath(file);
                if (!normalized.isBlank()) {
                    files.add(normalized);
                }
            }
        }
        return files;
    }

    private Map<String, Map<String, Object>> contextPackagesByTargetNode(WorkstreamPaths paths) throws IOException {
        var contextDir = paths.workstreamDir().resolve("context");
        if (!Files.isDirectory(contextDir)) {
            return Map.of();
        }
        var byTarget = new LinkedHashMap<String, Map<String, Object>>();
        try (var stream = Files.list(contextDir)) {
            for (var path : stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .toList()) {
                var metadata = objectMapper.readValue(path.toFile(), MAP_TYPE);
                var targetNode = text(metadata.get("targetNode"));
                if (targetNode.isBlank()) {
                    continue;
                }
                metadata.put("metadataPath", paths.workspaceRoot().relativize(path).toString());
                byTarget.put(targetNode, metadata);
            }
        }
        return byTarget;
    }

    private Map<String, Object> promptBreakdown(
            long promptTokens,
            long outputArtifactTokens,
            long transcriptTokens,
            Map<String, Object> usage,
            Map<String, Object> contextPackage
    ) {
        var contextTokens = longValue(contextPackage == null ? null : contextPackage.get("estimatedTokens"));
        var inputTokens = longValue(usage.get("inputTokens"));
        var breakdown = new LinkedHashMap<String, Object>();
        breakdown.put("promptTokens", promptTokens);
        breakdown.put("contextTokens", contextTokens);
        breakdown.put("promptOverheadTokens", Math.max(0, promptTokens - contextTokens));
        breakdown.put("adapterOverheadTokens", Math.max(0, inputTokens - promptTokens));
        breakdown.put("outputArtifactTokens", outputArtifactTokens);
        breakdown.put("transcriptTokens", transcriptTokens);
        if (contextPackage != null && !contextPackage.isEmpty()) {
            breakdown.put("contextPackage", contextPackage.get("contextPackage"));
            breakdown.put("contextPackageMetadata", contextPackage.get("metadataPath"));
            breakdown.put("contextProfile", text(contextPackage.get("profile")));
            breakdown.put("contextBudget", longValue(contextPackage.get("tokenBudget")));
            breakdown.put("contextWithinBudget", Boolean.TRUE.equals(contextPackage.get("withinBudget")));
            breakdown.put("topSections", topSections(contextPackage, 5));
        } else {
            breakdown.put("contextPackage", "");
            breakdown.put("contextPackageMetadata", "");
            breakdown.put("contextProfile", "");
            breakdown.put("contextBudget", 0L);
            breakdown.put("contextWithinBudget", false);
            breakdown.put("topSections", List.of());
        }
        return breakdown;
    }

    private List<Map<String, Object>> summarizeContextPackages(Map<String, Map<String, Object>> packagesByTargetNode) {
        return packagesByTargetNode.entrySet().stream()
                .<Map<String, Object>>map(entry -> {
                    var metadata = entry.getValue();
                    var item = new LinkedHashMap<String, Object>();
                    item.put("targetNode", entry.getKey());
                    item.put("builderNode", text(metadata.get("builderNode")));
                    item.put("profile", text(metadata.get("profile")));
                    item.put("estimatedTokens", longValue(metadata.get("estimatedTokens")));
                    item.put("tokenBudget", longValue(metadata.get("tokenBudget")));
                    item.put("withinBudget", Boolean.TRUE.equals(metadata.get("withinBudget")));
                    item.put("metadataPath", text(metadata.get("metadataPath")));
                    item.put("topSections", topSections(metadata, 5));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> topContextSections(Map<String, Map<String, Object>> packagesByTargetNode, int limit) {
        var sections = new ArrayList<Map<String, Object>>();
        for (var entry : packagesByTargetNode.entrySet()) {
            var metadata = entry.getValue();
            for (var section : mapList(metadata.get("sections"))) {
                var item = new LinkedHashMap<String, Object>();
                item.put("targetNode", entry.getKey());
                item.put("builderNode", text(metadata.get("builderNode")));
                item.put("profile", text(metadata.get("profile")));
                item.put("section", text(section.get("name")));
                item.put("renderedTokens", longValue(section.get("renderedTokens")));
                item.put("originalTokens", longValue(section.get("originalTokens")));
                item.put("degraded", Boolean.TRUE.equals(section.get("degraded")));
                item.put("dropped", Boolean.TRUE.equals(section.get("dropped")));
                sections.add(item);
            }
        }
        sections.sort(Comparator.comparingLong((Map<String, Object> item) -> longValue(item.get("renderedTokens"))).reversed());
        return sections.stream().limit(limit).toList();
    }

    private List<Map<String, Object>> topSections(Map<String, Object> contextPackage, int limit) {
        return mapList(contextPackage.get("sections")).stream()
                .sorted(Comparator.comparingLong((Map<String, Object> section) -> longValue(section.get("renderedTokens"))).reversed())
                .limit(limit)
                .<Map<String, Object>>map(section -> {
                    var item = new LinkedHashMap<String, Object>();
                    item.put("section", text(section.get("name")));
                    item.put("renderedTokens", longValue(section.get("renderedTokens")));
                    item.put("originalTokens", longValue(section.get("originalTokens")));
                    item.put("softBudgetTokens", longValue(section.get("softBudgetTokens")));
                    item.put("degraded", Boolean.TRUE.equals(section.get("degraded")));
                    item.put("dropped", Boolean.TRUE.equals(section.get("dropped")));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> estimatedUsage(WorkstreamPaths paths, Map<String, Object> run) throws IOException {
        var promptTokens = estimatedTokens(paths, text(run.get("promptMetadataPath")));
        var outputTokens = estimatedTokens(paths, text(run.get("outputMetadataPath")));
        if (promptTokens == 0 && outputTokens == 0) {
            return Map.of();
        }
        var usage = new LinkedHashMap<String, Object>();
        usage.put("inputTokens", promptTokens);
        usage.put("outputTokens", outputTokens);
        usage.put("cachedReadTokens", 0L);
        usage.put("cachedWriteTokens", 0L);
        usage.put("totalTokens", promptTokens + outputTokens);
        usage.put("source", "artifact_estimate");
        usage.put("measurementType", "ARTIFACT_ESTIMATE");
        usage.put("cumulative", false);
        usage.put("contextUsedTokens", 0L);
        return usage;
    }

    private boolean isAggregatableUsage(Map<String, Object> usage) {
        // 只有 provider run usage 可以加总。session snapshot 是会话累计值、artifact
        // estimate 是文件大小估算，二者都不能混进 total/byAgent/byNode。
        return "PROVIDER_RUN_USAGE".equals(measurementType(usage, false));
    }

    private boolean isSessionSnapshot(Map<String, Object> usage) {
        var type = text(usage.get("measurementType"));
        return "SESSION_USED_SNAPSHOT".equals(type) || "acpx_usage_update".equals(text(usage.get("source")));
    }

    private String measurementType(Map<String, Object> usage, boolean estimated) {
        var type = text(usage.get("measurementType"));
        if (!type.isBlank()) {
            return type;
        }
        if (estimated) {
            return "ARTIFACT_ESTIMATE";
        }
        if ("acpx_usage_update".equals(text(usage.get("source")))) {
            return "SESSION_USED_SNAPSHOT";
        }
        return "PROVIDER_RUN_USAGE";
    }

    private long estimatedTokens(WorkstreamPaths paths, String relativeMetadataPath) throws IOException {
        if (relativeMetadataPath.isBlank()) {
            return 0;
        }
        var metadataPath = paths.workspaceRoot().resolve(relativeMetadataPath).normalize();
        if (!metadataPath.startsWith(paths.workspaceRoot()) || !Files.isRegularFile(metadataPath)) {
            return 0;
        }
        return longValue(objectMapper.readValue(metadataPath.toFile(), MAP_TYPE).get("estimatedTokens"));
    }

    private Map<String, Object> usageMap(Map<String, UsageMetric> source) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMap());
        }
        return result;
    }

    private Map<String, Long> counts(List<NodeInstanceDocument> nodes, Function<NodeInstanceDocument, String> classifier) {
        var counts = nodes.stream()
                .map(classifier)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return new LinkedHashMap<>(counts);
    }

    private boolean isAgentRun(NodeInstanceDocument node) {
        return "acpx.agent_run".equals(node.handlerType());
    }

    private boolean isRevisionIteration(WorkflowIterationDocument iteration) {
        return IterationSemantics.isRevisionTrigger(iteration.triggerAction());
    }

    private long totalDurationMs(List<NodeInstanceDocument> nodes) {
        return nodes.stream()
                .map(this::durationMs)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private List<Map<String, Object>> longestNodes(List<NodeInstanceDocument> nodes, int limit) {
        return nodes.stream()
                .map(node -> new NodeDuration(node, durationMs(node)))
                .filter(entry -> entry.durationMs() != null)
                .sorted(Comparator.comparing(NodeDuration::durationMs).reversed())
                .limit(limit)
                .map(entry -> {
                    var node = entry.node();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("nodeInstanceId", node.nodeInstanceId());
                    item.put("nodeDefinitionId", node.nodeDefinitionId());
                    item.put("kind", node.kind());
                    item.put("status", node.status());
                    item.put("iterationId", node.iterationId());
                    item.put("durationMs", entry.durationMs());
                    return item;
                })
                .toList();
    }

    private Long durationMs(NodeInstanceDocument node) {
        if (node.startedAt() == null || node.finishedAt() == null) {
            return null;
        }
        return Duration.between(node.startedAt(), node.finishedAt()).toMillis();
    }

    private record NodeDuration(NodeInstanceDocument node, Long durationMs) {
    }

    @SuppressWarnings("unchecked")
    private String renderMarkdown(WorkstreamPaths paths, Map<String, Object> metrics) {
        var longestNodes = (List<Map<String, Object>>) metrics.get("longestNodes");
        var providerMetrics = (Map<String, Object>) metrics.get("providerMetrics");
        var tokenMetrics = (Map<String, Object>) metrics.get("tokenMetrics");
        var builder = new StringBuilder();
        builder.append("# Workstream Metrics\n\n");
        builder.append("- Workstream: ").append(paths.workstreamId().value()).append("\n");
        builder.append("- Workflow: ").append(metrics.get("workflowId")).append("@").append(metrics.get("definitionVersion")).append("\n");
        builder.append("- Status: ").append(metrics.get("workflowStatus")).append("\n");
        builder.append("- Generated At: ").append(metrics.get("generatedAt")).append("\n\n");
        builder.append("## Counts\n\n");
        builder.append("- Nodes: ").append(metrics.get("nodeCount")).append("\n");
        builder.append("- Agent Runs: ").append(metrics.get("agentRunCount")).append("\n");
        builder.append("- Gates: ").append(metrics.get("gateCount")).append("\n");
        builder.append("- Decisions: ").append(metrics.get("decisionCount")).append("\n");
        builder.append("- Iterations: ").append(metrics.get("iterationCount")).append("\n");
        builder.append("- Revisions: ").append(metrics.get("revisionCount")).append("\n");
        builder.append("- Total Node Duration(ms): ").append(metrics.get("totalNodeDurationMs")).append("\n\n");
        builder.append("## Longest Nodes\n\n");
        builder.append("| Node | Status | Iteration | Duration(ms) |\n");
        builder.append("| --- | --- | --- | --- |\n");
        if (longestNodes.isEmpty()) {
            builder.append("| N/A | N/A | N/A | N/A |\n");
        } else {
            for (var node : longestNodes) {
                builder.append("| ").append(node.get("nodeDefinitionId"))
                        .append(" | ").append(node.get("status"))
                        .append(" | ").append(node.get("iterationId"))
                        .append(" | ").append(node.get("durationMs"))
                        .append(" |\n");
            }
        }
        builder.append("\n## Provider Metrics\n\n");
        builder.append("| Provider | Runs | Completed | Failed | Recoverable | Total Duration(ms) | Average Duration(ms) |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        if (providerMetrics == null || providerMetrics.isEmpty()) {
            builder.append("| N/A | 0 | 0 | 0 | 0 | 0 | 0 |\n");
        } else {
            for (var entry : providerMetrics.entrySet()) {
                var value = (Map<String, Object>) entry.getValue();
                builder.append("| ").append(entry.getKey())
                        .append(" | ").append(value.get("runCount"))
                        .append(" | ").append(value.get("completedCount"))
                        .append(" | ").append(value.get("failedCount"))
                        .append(" | ").append(value.get("recoverableFailureCount"))
                        .append(" | ").append(value.get("totalDurationMs"))
                        .append(" | ").append(value.get("averageDurationMs"))
                        .append(" |\n");
            }
        }
        builder.append("\n## Token Metrics\n\n");
        var total = tokenMetrics == null ? Map.<String, Object>of() : map(tokenMetrics.get("total"));
        builder.append("- Measured Runs: ").append(tokenMetrics == null ? 0 : tokenMetrics.get("measuredRunCount"))
                .append(" / ").append(tokenMetrics == null ? 0 : tokenMetrics.get("runCount")).append("\n");
        builder.append("- Session Snapshots: ").append(tokenMetrics == null ? 0 : tokenMetrics.get("sessionSnapshotRunCount")).append("\n");
        builder.append("- Estimated Runs: ").append(tokenMetrics == null ? 0 : tokenMetrics.get("estimatedRunCount")).append("\n");
        builder.append("- Input Tokens: ").append(longValue(total.get("inputTokens"))).append("\n");
        builder.append("- Output Tokens: ").append(longValue(total.get("outputTokens"))).append("\n");
        builder.append("- Cached Read Tokens: ").append(longValue(total.get("cachedReadTokens"))).append("\n");
        builder.append("- Cached Write Tokens: ").append(longValue(total.get("cachedWriteTokens"))).append("\n");
        builder.append("- Total Tokens: ").append(longValue(total.get("totalTokens"))).append("\n");
        var estimatedTotal = tokenMetrics == null ? Map.<String, Object>of() : map(tokenMetrics.get("estimatedTotal"));
        builder.append("- Estimated Tokens (fallback, not in total): ").append(longValue(estimatedTotal.get("totalTokens"))).append("\n\n");
        builder.append("### Token Usage By Step\n\n");
        builder.append("| Node | Agent | Status | Outcome | Source | Input | Output | Transcript | Context | Prompt Overhead | Adapter Overhead | Total |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        var runs = tokenMetrics == null ? List.<Map<String, Object>>of() : mapList(tokenMetrics.get("runs"));
        if (runs.isEmpty()) {
            builder.append("| N/A | N/A | N/A | N/A | N/A | 0 | 0 | 0 | 0 | 0 | 0 | 0 |\n");
        } else {
            for (var run : runs) {
                var usage = map(run.get("usage"));
                var breakdown = map(run.get("breakdown"));
                builder.append("| ").append(run.get("nodeId"))
                        .append(" | ").append(run.get("agent"))
                        .append(" | ").append(run.get("status"))
                        .append(" | ").append(nonBlank(text(run.get("outcomeStatus")), "-"))
                        .append(" | ").append(nonBlank(text(usage.get("source")), Boolean.TRUE.equals(run.get("measured")) ? "acpx_stream" : "unknown"))
                        .append(" | ").append(longValue(usage.get("inputTokens")))
                        .append(" | ").append(longValue(usage.get("outputTokens")))
                        .append(" | ").append(longValue(breakdown.get("transcriptTokens")))
                        .append(" | ").append(longValue(breakdown.get("contextTokens")))
                        .append(" | ").append(longValue(breakdown.get("promptOverheadTokens")))
                        .append(" | ").append(longValue(breakdown.get("adapterOverheadTokens")))
                        .append(" | ").append(longValue(usage.get("totalTokens")))
                        .append(" |\n");
            }
        }
        builder.append("\n### Top Context Sections\n\n");
        builder.append("| Target Node | Profile | Section | Rendered | Original | Degraded | Dropped |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        var topSections = tokenMetrics == null ? List.<Map<String, Object>>of() : mapList(tokenMetrics.get("topContextSections"));
        if (topSections.isEmpty()) {
            builder.append("| N/A | N/A | N/A | 0 | 0 | false | false |\n");
        } else {
            for (var section : topSections) {
                builder.append("| ").append(section.get("targetNode"))
                        .append(" | ").append(section.get("profile"))
                        .append(" | ").append(section.get("section"))
                        .append(" | ").append(longValue(section.get("renderedTokens")))
                        .append(" | ").append(longValue(section.get("originalTokens")))
                        .append(" | ").append(section.get("degraded"))
                        .append(" | ").append(section.get("dropped"))
                        .append(" |\n");
            }
        }
        builder.append("\n## Tool Metrics\n\n");
        var toolMetrics = map(metrics.get("toolMetrics"));
        builder.append("- Tool Calls: ").append(longValue(toolMetrics.get("toolCallCount"))).append("\n");
        builder.append("- Reads: ").append(longValue(toolMetrics.get("readCount"))).append("\n");
        builder.append("- Writes: ").append(longValue(toolMetrics.get("writeCount"))).append("\n");
        builder.append("- Bash Commands: ").append(longValue(toolMetrics.get("bashCount"))).append("\n");
        builder.append("- Evidence Files: ").append(longValue(toolMetrics.get("evidenceFileCount"))).append("\n\n");
        builder.append("### Repeated Read Files\n\n");
        builder.append("| File | Reads | Agents | Nodes | Evidence Known |\n");
        builder.append("| --- | --- | --- | --- | --- |\n");
        var repeatedReads = mapList(toolMetrics.get("repeatedReadFiles"));
        if (repeatedReads.isEmpty()) {
            builder.append("| N/A | 0 | N/A | N/A | false |\n");
        } else {
            for (var item : repeatedReads) {
                builder.append("| ").append(item.get("path"))
                        .append(" | ").append(item.get("count"))
                        .append(" | ").append(String.join(", ", textList(item.get("agents"))))
                        .append(" | ").append(String.join(", ", textList(item.get("nodes"))))
                        .append(" | ").append(item.get("evidenceKnown"))
                        .append(" |\n");
            }
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> textList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(MetricsProjectionWriter::text)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizePath(String value) {
        var text = text(value);
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static boolean ignoredTelemetryFile(String value) {
        return value.startsWith(".agent/") || value.startsWith(".git/") || value.startsWith("/Users/") || value.startsWith("/private/");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : source.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private static final class UsageMetric {
        private long inputTokens;
        private long outputTokens;
        private long cachedReadTokens;
        private long cachedWriteTokens;
        private long totalTokens;
        private long contextUsedTokens;
        private final LinkedHashMap<String, BigDecimal> costByCurrency = new LinkedHashMap<>();

        static UsageMetric from(Map<String, Object> usage) {
            var metric = new UsageMetric();
            metric.inputTokens = longValue(usage.get("inputTokens"));
            metric.outputTokens = longValue(usage.get("outputTokens"));
            metric.cachedReadTokens = longValue(usage.get("cachedReadTokens"));
            metric.cachedWriteTokens = longValue(usage.get("cachedWriteTokens"));
            metric.totalTokens = longValue(usage.get("totalTokens"));
            if (metric.totalTokens == 0) {
                metric.totalTokens = metric.inputTokens + metric.outputTokens + metric.cachedReadTokens + metric.cachedWriteTokens;
            }
            var currency = text(usage.get("costCurrency"));
            var amount = decimalValue(usage.get("costAmount"));
            if (!currency.isBlank() && amount.signum() != 0) {
                metric.costByCurrency.put(currency, amount);
            }
            metric.contextUsedTokens = longValue(usage.get("contextUsedTokens"));
            return metric;
        }

        void add(UsageMetric other) {
            inputTokens += other.inputTokens;
            outputTokens += other.outputTokens;
            cachedReadTokens += other.cachedReadTokens;
            cachedWriteTokens += other.cachedWriteTokens;
            totalTokens += other.totalTokens;
            contextUsedTokens = Math.max(contextUsedTokens, other.contextUsedTokens);
            for (var entry : other.costByCurrency.entrySet()) {
                costByCurrency.merge(entry.getKey(), entry.getValue(), BigDecimal::add);
            }
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("inputTokens", inputTokens);
            map.put("outputTokens", outputTokens);
            map.put("cachedReadTokens", cachedReadTokens);
            map.put("cachedWriteTokens", cachedWriteTokens);
            map.put("totalTokens", totalTokens);
            map.put("contextUsedTokens", contextUsedTokens);
            map.put("costByCurrency", new LinkedHashMap<>(costByCurrency));
            return map;
        }
    }

    private static final class ToolMetric {
        private long toolCallCount;
        private long readCount;
        private long writeCount;
        private long bashCount;
        private long otherCount;

        static ToolMetric from(Map<String, Object> toolUsage) {
            var metric = new ToolMetric();
            metric.toolCallCount = longValue(toolUsage.get("toolCallCount"));
            metric.readCount = longValue(toolUsage.get("readCount"));
            metric.writeCount = longValue(toolUsage.get("writeCount"));
            metric.bashCount = longValue(toolUsage.get("bashCount"));
            metric.otherCount = longValue(toolUsage.get("otherCount"));
            return metric;
        }

        void add(ToolMetric other) {
            toolCallCount += other.toolCallCount;
            readCount += other.readCount;
            writeCount += other.writeCount;
            bashCount += other.bashCount;
            otherCount += other.otherCount;
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("toolCallCount", toolCallCount);
            map.put("readCount", readCount);
            map.put("writeCount", writeCount);
            map.put("bashCount", bashCount);
            map.put("otherCount", otherCount);
            return map;
        }
    }

    private static final class ReadFileMetric {
        private final String path;
        private final Set<String> runIds = new LinkedHashSet<>();
        private final Set<String> nodes = new LinkedHashSet<>();
        private final Set<String> agents = new LinkedHashSet<>();
        private boolean evidenceKnown;

        private ReadFileMetric(String path) {
            this.path = path;
        }

        void accept(String runId, String nodeId, String agent, boolean evidenceKnown) {
            runIds.add(runId);
            nodes.add(nodeId);
            agents.add(agent);
            this.evidenceKnown = this.evidenceKnown || evidenceKnown;
        }

        boolean isRepeated() {
            return runIds.size() > 1;
        }

        int count() {
            return runIds.size();
        }

        String path() {
            return path;
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("path", path);
            map.put("count", count());
            map.put("runIds", List.copyOf(runIds));
            map.put("nodes", List.copyOf(nodes));
            map.put("agents", List.copyOf(agents));
            map.put("evidenceKnown", evidenceKnown);
            return map;
        }
    }

    private static final class ProviderMetric {
        private long runCount;
        private long completedCount;
        private long failedCount;
        private long preparedCount;
        private long recoverableFailureCount;
        private long totalDurationMs;

        void accept(String status, long durationMs, boolean recoverable) {
            runCount++;
            totalDurationMs += Math.max(0, durationMs);
            switch (status) {
                case "completed" -> completedCount++;
                case "failed" -> {
                    failedCount++;
                    if (recoverable) {
                        recoverableFailureCount++;
                    }
                }
                case "prepared" -> preparedCount++;
                case "recoverable_failed" -> {
                    failedCount++;
                    recoverableFailureCount++;
                }
                default -> {
                    if (status.contains("recoverable")) {
                        recoverableFailureCount++;
                    }
                }
            }
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("runCount", runCount);
            map.put("completedCount", completedCount);
            map.put("failedCount", failedCount);
            map.put("preparedCount", preparedCount);
            map.put("recoverableFailureCount", recoverableFailureCount);
            map.put("totalDurationMs", totalDurationMs);
            map.put("averageDurationMs", runCount == 0 ? 0 : totalDurationMs / runCount);
            return map;
        }
    }

    private static final class MetricsProjectionException extends RuntimeException {
        MetricsProjectionException(IOException cause) {
            super(cause);
        }

        IOException unwrap() {
            return (IOException) getCause();
        }
    }
}
