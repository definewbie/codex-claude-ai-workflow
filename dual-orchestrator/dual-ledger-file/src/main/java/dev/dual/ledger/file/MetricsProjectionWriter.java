package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.dual.core.provider.ProviderCostAttribution;
import dev.dual.core.provider.ProviderPricingCatalog;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        metrics.put("providerMetrics", providerMetrics(paths, current, nodes));
        var tokenMetrics = tokenMetrics(paths);
        var toolMetrics = toolMetrics(paths);
        metrics.put("tokenMetrics", tokenMetrics);
        metrics.put("toolMetrics", toolMetrics);
        metrics.put("tokenAttribution", tokenAttribution(tokenMetrics, toolMetrics, paths));

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

    private Map<String, Object> providerMetrics(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            List<NodeInstanceDocument> nodes
    ) throws IOException {
        var byAgent = new LinkedHashMap<String, ProviderMetric>();
        var outcomeStatusCounts = new LinkedHashMap<String, Long>();
        var executionConsistencyCounts = new LinkedHashMap<String, Long>();
        if (Files.isDirectory(paths.runsDir())) {
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
                    executionConsistencyCounts.merge(ExecutionConsistencyClassifier.classify(run), 1L, Long::sum);
                }
            }
        }
        legacyActiveNodeExecutionConsistency(current, nodes)
                .forEach(signal -> executionConsistencyCounts.merge(signal, 1L, Long::sum));
        var result = new LinkedHashMap<String, Object>();
        for (var entry : byAgent.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMap());
        }
        result.put("outcomeStatusCounts", outcomeStatusCounts);
        result.put("executionConsistencyCounts", executionConsistencyCounts);
        return result;
    }

    private List<String> legacyActiveNodeExecutionConsistency(
            WorkflowCurrentDocument current,
            List<NodeInstanceDocument> nodes
    ) {
        if (current.status() != WorkflowStatus.RUNNING || current.activeNodeInstanceIds().isEmpty()) {
            return List.of();
        }
        var byId = nodes.stream().collect(Collectors.toMap(
                NodeInstanceDocument::nodeInstanceId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        var signals = new ArrayList<String>();
        for (var nodeInstanceId : current.activeNodeInstanceIds()) {
            var node = byId.get(nodeInstanceId);
            if (node == null || node.status() != NodeStatus.RUNNING) {
                continue;
            }
            var pid = longValue(node.result().get("running_pid"));
            if (pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
                continue;
            }
            if (pid > 0) {
                signals.add("stale_legacy_running");
            }
        }
        return signals;
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
                item.put("sessionScope", text(run.get("sessionScope")));
                item.put("sessionSegmentKey", text(run.get("sessionSegmentKey")));
                item.put("sessionScopeReason", text(run.get("sessionScopeReason")));
                item.put("stage", text(run.get("stage")));
                item.put("semanticIteration", text(run.get("semanticIteration")));
                item.put("status", text(run.get("status")));
                var attempt = map(run.get("executionAttempt"));
                var outcome = map(run.get("executionOutcome"));
                item.put("attemptStatus", text(attempt.get("status")));
                item.put("attemptProcessStatus", text(attempt.get("processStatus")));
                item.put("attemptFailureSignal", text(attempt.get("failureSignal")));
                item.put("outcomeStatus", text(outcome.get("status")));
                item.put("outcomePrimaryReason", text(outcome.get("primaryReason")));
                item.put("outcomeNodeMaySucceed", Boolean.TRUE.equals(outcome.get("nodeMaySucceed")));
                item.put("executionConsistency", ExecutionConsistencyClassifier.classify(run));
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
                    } else if (isAggregatableUsage(usage)) {
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
                    usageMap.put("providerId", text(usage.get("providerId")));
                    usageMap.put("model", text(usage.get("model")));
                    usageMap.put("billingMode", text(usage.get("billingMode")));
                    usageMap.put("providerMetadataSource", text(usage.get("providerMetadataSource")));
                    usageMap.put("unavailableReason", text(usage.get("unavailableReason")));
                    usageMap.put("costAmount", usage.get("costAmount"));
                    usageMap.put("costCurrency", text(usage.get("costCurrency")));
                    item.put("usage", usageMap);
                    item.put("providerModelIdentity", providerModelIdentity(usageMap));
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
        var readReusePolicy = readReusePolicy(paths);
        var planAuthorizedFiles = planAuthorizedFiles(paths);
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
                var declaredRereadPaths = declaredRereadPaths(run.get("reread_reasons"));
                for (var file : textList(toolUsage.get("readFiles"))) {
                    var normalized = normalizePath(file);
                    if (normalized.isBlank() || ignoredTelemetryFile(normalized)) {
                        continue;
                    }
                    readFiles.computeIfAbsent(normalized, ReadFileMetric::new)
                            .accept(runId, nodeId, agent, evidenceFiles.contains(normalized),
                                    readReusePolicy.requiresReason(normalized), declaredRereadPaths.contains(normalized),
                                    planAuthorizedFiles.contains(normalized));
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
        result.put("repeatedReadReasonRequiredCount", readFiles.values().stream()
                .filter(ReadFileMetric::isRepeated)
                .filter(ReadFileMetric::rereadReasonRequired)
                .count());
        result.put("missingRereadReasonCount", readFiles.values().stream()
                .filter(ReadFileMetric::isRepeated)
                .filter(ReadFileMetric::rereadReasonMissing)
                .count());
        result.put("explorationPolicy", explorationPolicyMetrics(paths));
        result.put("allowedRepeatedReadReasons", readReusePolicy.allowedReasons());
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

    /**
     * Read-only token attribution: joins the already-computed token + tool
     * metrics with transcript bytes and file sizes into four views — per-run,
     * per-node hotspots, per-file read cost, per-session snowball. It is a read
     * model for explaining where tokens go; it is not workflow state.
     */
    private Map<String, Object> tokenAttribution(
            Map<String, Object> tokenMetrics, Map<String, Object> toolMetrics, WorkstreamPaths paths
    ) throws IOException {
        var tokenRuns = mapList(tokenMetrics.get("runs"));
        var toolRunsById = new LinkedHashMap<String, Map<String, Object>>();
        for (var tr : mapList(toolMetrics.get("runs"))) {
            toolRunsById.put(text(tr.get("runId")), tr);
        }
        var runMeta = runTranscriptMeta(paths);
        var readReusePolicy = readReusePolicy(paths);
        var workspaceRoot = paths.workspaceRoot();
        var pricing = ProviderPricingCatalog.load(workspaceRoot, objectMapper);
        var costSummary = new CostEconomicsSummary();

        var perRun = new ArrayList<Map<String, Object>>();
        var byNode = new LinkedHashMap<String, NodeAttribution>();
        var bySession = new LinkedHashMap<String, ArrayList<Map<String, Object>>>();
        for (var tr : tokenRuns) {
            var runId = text(tr.get("runId"));
            var usage = map(tr.get("usage"));
            var breakdown = map(tr.get("breakdown"));
            var toolUsage = map(map(toolRunsById.get(runId)).get("toolUsage"));
            var meta = runMeta.getOrDefault(runId, new RunMeta("", 0L, Map.of()));
            var entry = new LinkedHashMap<String, Object>();
            entry.put("runId", runId);
            entry.put("nodeId", text(tr.get("nodeId")));
            entry.put("agent", text(tr.get("agent")));
            entry.put("stage", text(tr.get("stage")));
            entry.put("sessionSegmentKey", text(tr.get("sessionSegmentKey")));
            entry.put("sessionScope", text(tr.get("sessionScope")));
            entry.put("attemptStatus", text(tr.get("attemptStatus")));
            entry.put("attemptProcessStatus", text(tr.get("attemptProcessStatus")));
            entry.put("outcomeStatus", text(tr.get("outcomeStatus")));
            entry.put("executionConsistency", text(tr.get("executionConsistency")));
            entry.put("promptTokens", longValue(breakdown.get("promptTokens")));
            entry.put("contextPackageTokens", longValue(breakdown.get("contextTokens")));
            entry.put("inputTokens", longValue(usage.get("inputTokens")));
            entry.put("outputTokens", longValue(usage.get("outputTokens")));
            entry.put("cachedReadTokens", longValue(usage.get("cachedReadTokens")));
            entry.put("cachedWriteTokens", longValue(usage.get("cachedWriteTokens")));
            entry.put("totalTokens", longValue(usage.get("totalTokens")));
            entry.put("measurementType", measurementType(usage, Boolean.TRUE.equals(tr.get("estimated"))));
            entry.put("usageSource", text(usage.get("source")));
            entry.put("usageAttribution", text(usage.get("attribution")));
            entry.put("contextUsedTokens", longValue(usage.get("contextUsedTokens")));
            var identity = map(tr.get("providerModelIdentity"));
            var providerId = nonBlank(text(identity.get("provider")), text(usage.get("providerId")));
            var model = nonBlank(text(identity.get("model")), text(usage.get("model")));
            var billingMode = ProviderCostAttribution.normalizeBillingMode(nonBlank(text(identity.get("billingMode")), text(usage.get("billingMode"))));
            var providerSource = nonBlank(text(identity.get("source")), text(usage.get("providerMetadataSource")));
            entry.put("providerId", providerId);
            entry.put("model", model);
            entry.put("billingMode", billingMode);
            entry.put("providerMetadataSource", providerSource);
            entry.put("providerModelIdentity", Map.of(
                    "provider", providerId,
                    "model", model,
                    "billingMode", billingMode,
                    "source", providerSource,
                    "confidence", nonBlank(text(identity.get("confidence")), providerId.isBlank() || model.isBlank() ? "low" : "high")
            ));
            entry.put("providerUsageVisibility", providerUsageVisibility(usage, Boolean.TRUE.equals(tr.get("estimated"))));
            entry.put("providerUsageUnavailableReason", providerUsageUnavailableReason(usage, Boolean.TRUE.equals(tr.get("estimated"))));
            entry.put("cost", usage.get("costAmount"));
            var costInput = new LinkedHashMap<>(usage);
            costInput.put("providerId", providerId);
            costInput.put("model", model);
            costInput.put("billingMode", billingMode);
            costInput.put("providerMetadataSource", providerSource);
            var costEconomics = ProviderCostAttribution.calculate(pricing, costInput, text(tr.get("agent")), System::getenv);
            entry.put("costEconomics", costEconomics);
            entry.put("costAttribution", costAttribution(costEconomics));
            entry.put("providerCachePolicy", providerCachePolicy(costEconomics));
            costSummary.add(costEconomics);
            entry.put("transcriptTokens", longValue(breakdown.get("transcriptTokens")));
            entry.put("transcriptBytes", meta.bytes());
            entry.put("executionLatency", meta.executionLatency());
            entry.put("toolCalls", longValue(toolUsage.get("toolCallCount")));
            entry.put("reads", longValue(toolUsage.get("readCount")));
            entry.put("bash", longValue(toolUsage.get("bashCount")));
            entry.put("writes", longValue(toolUsage.get("writeCount")));
            entry.put("startedAt", meta.startedAt());
            perRun.add(entry);
            byNode.computeIfAbsent(text(tr.get("nodeId")), key -> new NodeAttribution(text(tr.get("nodeId")), text(tr.get("agent")))).add(entry);
            var seg = text(tr.get("sessionSegmentKey"));
            if (!seg.isBlank()) {
                bySession.computeIfAbsent(seg, key -> new ArrayList<>()).add(entry);
            }
        }

        var nodeHotspots = byNode.values().stream()
                .map(NodeAttribution::toMap)
                .sorted((a, b) -> Long.compare(longValue(b.get("totalTokens")), longValue(a.get("totalTokens"))))
                .toList();

        var fileReadCost = new ArrayList<Map<String, Object>>();
        for (var fr : mapList(toolMetrics.get("topReadFiles"))) {
            var path = text(fr.get("path"));
            var bytes = fileSizeSafe(workspaceRoot, path);
            var fc = new LinkedHashMap<String, Object>();
            fc.put("path", path);
            fc.put("readCount", longValue(fr.get("count")));
            fc.put("runIds", fr.get("runIds"));
            fc.put("nodes", fr.get("nodes"));
            fc.put("agents", fr.get("agents"));
            fc.put("fileBytes", bytes);
            fc.put("estimatedReadTokens", bytes / 4);
            fc.put("reusable", readReusePolicy.requiresReason(path));
            fc.put("rereadReasonStatus", text(fr.get("rereadReasonStatus")));
            fileReadCost.add(fc);
        }

        var sessionSnowball = new ArrayList<Map<String, Object>>();
        for (var segEntry : bySession.entrySet()) {
            var segRuns = new ArrayList<>(segEntry.getValue());
            segRuns.sort(Comparator.comparing(m -> text(m.get("startedAt"))));
            var ordered = new ArrayList<Map<String, Object>>();
            long prevBytes = 0;
            long maxGrowth = -1;
            String maxGrowthRun = "";
            for (var r : segRuns) {
                var bytes = longValue(r.get("transcriptBytes"));
                var delta = bytes - prevBytes;
                var sr = new LinkedHashMap<String, Object>();
                sr.put("runId", r.get("runId"));
                sr.put("nodeId", r.get("nodeId"));
                sr.put("seq", ordered.size() + 1);
                sr.put("cachedReadTokens", r.get("cachedReadTokens"));
                sr.put("transcriptBytes", bytes);
                sr.put("deltaTranscriptBytes", delta);
                ordered.add(sr);
                if (delta > maxGrowth) {
                    maxGrowth = delta;
                    maxGrowthRun = text(r.get("runId"));
                }
                prevBytes = bytes;
            }
            var seg = new LinkedHashMap<String, Object>();
            seg.put("sessionSegmentKey", segEntry.getKey());
            seg.put("runs", ordered);
            seg.put("maxTranscriptGrowthRun", maxGrowthRun);
            sessionSnowball.add(seg);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("perRun", perRun);
        result.put("usageVisibility", usageVisibilitySummary(perRun));
        result.put("costEconomics", costSummary.toMap());
        result.put("nodeHotspots", nodeHotspots);
        result.put("fileReadCost", fileReadCost);
        result.put("sessionSnowball", sessionSnowball);
        result.put("executionLatency", executionLatencySummary(perRun));
        return result;
    }

    private Map<String, Object> executionLatencySummary(List<Map<String, Object>> perRun) {
        var topRuns = perRun.stream()
                .map(run -> {
                    var latency = map(run.get("executionLatency"));
                    var item = new LinkedHashMap<String, Object>();
                    item.put("runId", text(run.get("runId")));
                    item.put("nodeId", text(run.get("nodeId")));
                    item.put("agent", text(run.get("agent")));
                    item.put("totalDurationMs", longValue(latency.get("totalDurationMs")));
                    item.put("terminalDetectedMs", longValue(latency.get("terminalDetectedMs")));
                    item.put("processWaitCompletedMs", longValue(latency.get("processWaitCompletedMs")));
                    item.put("artifactExtractedMs", longValue(latency.get("artifactExtractedMs")));
                    item.put("contractValidatedMs", longValue(latency.get("contractValidatedMs")));
                    item.put("terminalToContractMs", longValue(latency.get("terminalToContractMs")));
                    item.put("dominantPhase", text(latency.get("dominantPhase")));
                    return item;
                })
                .filter(item -> longValue(item.get("totalDurationMs")) > 0)
                .sorted((a, b) -> Long.compare(longValue(b.get("totalDurationMs")), longValue(a.get("totalDurationMs"))))
                .limit(10)
                .toList();
        return Map.of(
                "schemaVersion", 1,
                "topRuns", topRuns
        );
    }

    private Map<String, Object> usageVisibilitySummary(List<Map<String, Object>> perRun) {
        var byVisibility = new LinkedHashMap<String, Long>();
        var byAgent = new LinkedHashMap<String, Map<String, Long>>();
        var gaps = new ArrayList<Map<String, Object>>();
        for (var run : perRun) {
            var visibility = nonBlank(text(run.get("providerUsageVisibility")), "unavailable");
            var agent = nonBlank(text(run.get("agent")), "unknown");
            byVisibility.merge(visibility, 1L, Long::sum);
            byAgent.computeIfAbsent(agent, ignored -> new LinkedHashMap<>())
                    .merge(visibility, 1L, Long::sum);
            if (!"provider_run_usage".equals(visibility)) {
                gaps.add(Map.of(
                        "runId", text(run.get("runId")),
                        "nodeId", text(run.get("nodeId")),
                        "agent", agent,
                        "visibility", visibility,
                        "reason", text(run.get("providerUsageUnavailableReason"))
                ));
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("byVisibility", byVisibility);
        result.put("byAgent", byAgent);
        result.put("gaps", gaps);
        return result;
    }

    private Map<String, Object> providerModelIdentity(Map<String, Object> usage) {
        var provider = text(usage.get("providerId"));
        var model = text(usage.get("model"));
        var billingMode = ProviderCostAttribution.normalizeBillingMode(nonBlank(text(usage.get("billingMode")), "pricing_unknown"));
        return Map.of(
                "provider", provider.isBlank() ? "unknown" : provider,
                "model", model,
                "billingMode", billingMode,
                "source", text(usage.get("providerMetadataSource")),
                "confidence", provider.isBlank() || model.isBlank() ? "low" : "high"
        );
    }

    private Map<String, Object> costAttribution(Map<String, Object> costEconomics) {
        var result = new LinkedHashMap<String, Object>();
        result.put("inputCacheMissTokens", longValue(costEconomics.get("inputCacheMissTokens")));
        result.put("inputCacheHitTokens", longValue(costEconomics.get("inputCacheHitTokens")));
        result.put("outputTokens", longValue(costEconomics.get("outputTokens")));
        result.put("cachedWriteTokens", longValue(costEconomics.get("cacheWriteTokens")));
        result.put("billingMode", text(costEconomics.get("billingMode")));
        result.put("clientReportedCost", costEconomics.get("clientReportedCost"));
        result.put("clientReportedCurrency", text(costEconomics.get("clientReportedCurrency")));
        result.put("officialCalculatedCost", costEconomics.get("officialCalculatedCost"));
        result.put("officialCalculatedCurrency", text(costEconomics.get("officialCalculatedCurrency")));
        result.put("reliability", text(costEconomics.get("costEstimateReliability")));
        result.put("cacheSignalSource", cacheSignalSource(costEconomics));
        return result;
    }

    private Map<String, Object> providerCachePolicy(Map<String, Object> costEconomics) {
        var costReliability = text(costEconomics.get("costEstimateReliability"));
        var cacheReliability = text(costEconomics.get("cacheSignalReliability"));
        var mode = text(costEconomics.get("providerCacheDecision"));
        var reason = nonBlank(text(costEconomics.get("providerCacheDecisionReason")), text(costEconomics.get("reason")));
        if (("subscription_unpriced".equals(costReliability) || "token_only".equals(costReliability))
                && ("provider_cache_tokens_reported".equals(cacheReliability)
                || "provider_usage_tokens_reported".equals(cacheReliability))) {
            mode = "token_policy_active";
            reason = "provider/model and cache token signal are known; amount is not metered by visible per-token pricing";
        }
        return Map.of(
                "mode", mode,
                "reason", reason,
                "inputsComplete", !"pricing_unknown".equals(costReliability)
                        && !"pricing_unknown".equals(cacheReliability),
                "cacheSignalReliability", cacheReliability,
                "costEstimateReliability", costReliability
        );
    }

    private String cacheSignalSource(Map<String, Object> costEconomics) {
        var reliability = text(costEconomics.get("cacheSignalReliability"));
        if ("provider_cache_tokens_reported".equals(reliability) || "provider_usage_tokens_reported".equals(reliability)) {
            return "provider_reported";
        }
        if ("not_applicable".equals(reliability)) {
            return "not_applicable";
        }
        return "unavailable";
    }

    private String providerUsageVisibility(Map<String, Object> usage, boolean estimated) {
        if (usage.isEmpty()) {
            return "unavailable";
        }
        var type = measurementType(usage, estimated);
        if ("PROVIDER_RUN_USAGE".equals(type)) {
            return "provider_run_usage";
        }
        if ("SESSION_USED_SNAPSHOT".equals(type)) {
            return "session_snapshot_only";
        }
        if ("ARTIFACT_ESTIMATE".equals(type)) {
            return "artifact_estimate_only";
        }
        return "unavailable";
    }

    private String providerUsageUnavailableReason(Map<String, Object> usage, boolean estimated) {
        if (usage.isEmpty()) {
            return "no usage object was written for this run";
        }
        var type = measurementType(usage, estimated);
        if ("PROVIDER_RUN_USAGE".equals(type)) {
            return "";
        }
        if ("SESSION_USED_SNAPSHOT".equals(type)) {
            return "acpx exposed only cumulative session usage_update; per-run provider result.usage was not attributable";
        }
        if ("ARTIFACT_ESTIMATE".equals(type)) {
            return "adapter provider usage was unavailable; values are estimated from prompt/output artifacts";
        }
        if ("UNAVAILABLE".equals(type)) {
            var reason = text(usage.get("unavailableReason"));
            return reason.isBlank() ? "provider usage unavailable" : reason;
        }
        return "unknown usage measurement type: " + type;
    }

    private Map<String, RunMeta> runTranscriptMeta(WorkstreamPaths paths) throws IOException {
        var out = new LinkedHashMap<String, RunMeta>();
        if (!Files.isDirectory(paths.runsDir())) {
            return out;
        }
        try (var stream = Files.list(paths.runsDir())) {
            for (var runDir : stream.filter(Files::isDirectory).toList()) {
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                Map<String, Object> run;
                try {
                    run = objectMapper.readValue(runJson.toFile(), MAP_TYPE);
                } catch (IOException ignored) {
                    continue;
                }
                var runId = nonBlank(text(run.get("runId")), runDir.getFileName().toString());
                var transcriptPath = text(run.get("transcriptPath"));
                var bytes = transcriptPath.isBlank() ? 0L : fileSizeSafe(paths.workspaceRoot(), transcriptPath);
                out.put(runId, new RunMeta(text(run.get("startedAt")), bytes, executionLatency(run)));
            }
        }
        return out;
    }

    private Map<String, Object> executionLatency(Map<String, Object> run) {
        var timeline = mapList(run.get("executionTimeline"));
        var total = longValue(run.get("durationMs"));
        var processStarted = elapsed(timeline, "process_started");
        var processWaitCompleted = firstPositive(
                elapsed(timeline, "process_wait_completed"),
                elapsed(timeline, "process_exited")
        );
        var terminalDetected = elapsed(timeline, "terminal_control_detected");
        var terminalGraceElapsed = elapsed(timeline, "terminal_control_grace_elapsed");
        var artifactExtracted = elapsed(timeline, "artifact_extracted");
        var contractValidated = elapsed(timeline, "contract_validated");
        var terminalGraceMs = positiveDelta(terminalDetected, terminalGraceElapsed);
        var postProcessMs = positiveDelta(processWaitCompleted, contractValidated);
        var terminalToContractMs = positiveDelta(terminalDetected, contractValidated);
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", 1);
        result.put("totalDurationMs", total);
        result.put("processStartedMs", processStarted);
        result.put("processWaitCompletedMs", processWaitCompleted);
        result.put("terminalDetectedMs", terminalDetected);
        result.put("terminalGraceElapsedMs", terminalGraceElapsed);
        result.put("terminalGraceMs", terminalGraceMs);
        result.put("artifactExtractedMs", artifactExtracted);
        result.put("contractValidatedMs", contractValidated);
        result.put("postProcessMs", postProcessMs);
        result.put("terminalToContractMs", terminalToContractMs);
        result.put("dominantPhase", dominantExecutionPhase(total, processWaitCompleted, terminalGraceMs, postProcessMs));
        return result;
    }

    private long elapsed(List<Map<String, Object>> timeline, String kind) {
        return timeline.stream()
                .filter(event -> kind.equals(text(event.get("kind"))))
                .map(event -> longValue(event.get("elapsedMs")))
                .filter(value -> value > 0)
                .findFirst()
                .orElse(0L);
    }

    private long firstPositive(long first, long second) {
        return first > 0 ? first : second;
    }

    private long positiveDelta(long start, long end) {
        return start > 0 && end >= start ? end - start : 0L;
    }

    private String dominantExecutionPhase(long total, long processWaitCompleted, long terminalGraceMs, long postProcessMs) {
        if (terminalGraceMs > 0 && terminalGraceMs >= postProcessMs) {
            return "terminal_grace";
        }
        if (postProcessMs > 0) {
            return "post_process";
        }
        if (processWaitCompleted > 0 && processWaitCompleted >= total / 2) {
            return "process_wait";
        }
        if (total > 0) {
            return "execution_total";
        }
        return "unknown";
    }

    private static long fileSizeSafe(Path workspaceRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return 0L;
        }
        try {
            var resolved = workspaceRoot.resolve(relativePath).normalize();
            if (!resolved.startsWith(workspaceRoot) || !Files.isRegularFile(resolved)) {
                return 0L;
            }
            return Files.size(resolved);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private record RunMeta(String startedAt, long bytes, Map<String, Object> executionLatency) {
    }

    private static final class NodeAttribution {
        private final String nodeId;
        private final String agent;
        private long runCount;
        private long promptTokens;
        private long contextPackageTokens;
        private long inputTokens;
        private long outputTokens;
        private long cachedReadTokens;
        private long transcriptBytes;
        private long toolCalls;
        private long reads;
        private long bash;
        private long writes;

        NodeAttribution(String nodeId, String agent) {
            this.nodeId = nodeId;
            this.agent = agent;
        }

        void add(Map<String, Object> run) {
            runCount++;
            promptTokens += longValue(run.get("promptTokens"));
            contextPackageTokens += longValue(run.get("contextPackageTokens"));
            inputTokens += longValue(run.get("inputTokens"));
            outputTokens += longValue(run.get("outputTokens"));
            cachedReadTokens += longValue(run.get("cachedReadTokens"));
            transcriptBytes += longValue(run.get("transcriptBytes"));
            toolCalls += longValue(run.get("toolCalls"));
            reads += longValue(run.get("reads"));
            bash += longValue(run.get("bash"));
            writes += longValue(run.get("writes"));
        }

        Map<String, Object> toMap() {
            var m = new LinkedHashMap<String, Object>();
            m.put("nodeId", nodeId);
            m.put("agent", agent);
            m.put("runCount", runCount);
            m.put("promptTokens", promptTokens);
            m.put("contextPackageTokens", contextPackageTokens);
            m.put("inputTokens", inputTokens);
            m.put("outputTokens", outputTokens);
            m.put("cachedReadTokens", cachedReadTokens);
            m.put("totalTokens", inputTokens + outputTokens + cachedReadTokens);
            m.put("transcriptBytes", transcriptBytes);
            m.put("toolCalls", toolCalls);
            m.put("reads", reads);
            m.put("bash", bash);
            m.put("writes", writes);
            m.put("heavy", heavy());
            return m;
        }

        private List<String> heavy() {
            var out = new ArrayList<String>();
            var provider = inputTokens + outputTokens + cachedReadTokens;
            if (provider > 0 && cachedReadTokens * 100 / provider >= 60) {
                out.add("cachedRead");
            }
            if (inputTokens > 0 && outputTokens > inputTokens) {
                out.add("output");
            }
            if (contextPackageTokens > 0 && promptTokens > contextPackageTokens) {
                out.add("promptOverhead");
            }
            if (reads >= 3 || toolCalls >= 5) {
                out.add("tools");
            }
            if (transcriptBytes >= 20000) {
                out.add("transcript");
            }
            return out;
        }
    }

    private Map<String, Object> explorationPolicyMetrics(WorkstreamPaths paths) throws IOException {
        var governanceDir = paths.workstreamDir().resolve("artifacts").resolve("context-governance");
        var warnings = new ArrayList<Map<String, Object>>();
        var failures = new ArrayList<Map<String, Object>>();
        var violations = new ArrayList<Map<String, Object>>();
        long passCount = 0;
        long warnCount = 0;
        long failCount = 0;
        if (Files.isDirectory(governanceDir)) {
            try (var stream = Files.list(governanceDir)) {
                for (var path : stream
                        .filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                        .toList()) {
                    var report = objectMapper.readValue(path.toFile(), MAP_TYPE);
                    if (!"actual".equals(text(report.get("reportPhase")))) {
                        continue;
                    }
                    var result = map(report.get("explorationPolicyResult"));
                    var status = text(result.get("status"));
                    if ("pass".equals(status)) {
                        passCount++;
                    } else if ("warn".equals(status)) {
                        warnCount++;
                        var item = explorationPolicyItem(report, result, path);
                        warnings.add(item);
                        violations.addAll(explorationViolations(item));
                    } else if ("failed".equals(status)) {
                        failCount++;
                        var item = explorationPolicyItem(report, result, path);
                        failures.add(item);
                        violations.addAll(explorationViolations(item));
                    }
                }
            }
        }
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("mode", "warn");
        document.put("passCount", passCount);
        document.put("warnCount", warnCount);
        document.put("failCount", failCount);
        document.put("warnings", warnings);
        document.put("failures", failures);
        document.put("violations", violations);
        return document;
    }

    private Map<String, Object> explorationPolicyItem(Map<String, Object> report, Map<String, Object> result, Path reportPath) {
        var item = new LinkedHashMap<String, Object>();
        item.put("runId", text(report.get("runId")));
        item.put("nodeId", text(report.get("nodeDefinitionId")));
        item.put("targetNodeId", text(report.get("targetNodeDefinitionId")));
        item.put("stageId", text(report.get("stageId")));
        item.put("iterationId", text(report.get("iterationId")));
        item.put("status", text(result.get("status")));
        item.put("mode", text(result.get("mode")));
        item.put("reportPath", reportPath.toString());
        item.put("agentExplorationPlanPath", text(report.get("agentExplorationPlanPath")));
        item.put("explorationPolicyResultPath", text(report.get("explorationPolicyResultPath")));
        item.put("actualReadFiles", textList(report.get("actualReadFiles")));
        item.put("actualCapabilityKinds", textList(result.get("actualCapabilityKinds")));
        item.put("actualCapabilityUsages", mapList(result.get("actualCapabilityUsages")));
        item.put("scopeExpansionFiles", textList(result.get("scopeExpansionFiles")));
        item.put("violations", mapList(result.get("violations")));
        return item;
    }

    private List<Map<String, Object>> explorationViolations(Map<String, Object> item) {
        var out = new ArrayList<Map<String, Object>>();
        for (var violation : mapList(item.get("violations"))) {
            var flattened = new LinkedHashMap<String, Object>();
            flattened.put("runId", text(item.get("runId")));
            flattened.put("nodeId", text(item.get("nodeId")));
            flattened.put("targetNodeId", text(item.get("targetNodeId")));
            flattened.put("stageId", text(item.get("stageId")));
            flattened.put("status", text(item.get("status")));
            flattened.put("type", text(violation.get("type")));
            flattened.put("severity", text(violation.get("severity")));
            flattened.put("message", text(violation.get("message")));
            flattened.put("details", map(violation.get("details")));
            flattened.put("actualCapabilityUsages", mapList(item.get("actualCapabilityUsages")));
            flattened.put("agentExplorationPlanPath", text(item.get("agentExplorationPlanPath")));
            flattened.put("explorationPolicyResultPath", text(item.get("explorationPolicyResultPath")));
            out.add(flattened);
        }
        return out;
    }

    private ReadReusePolicy readReusePolicy(WorkstreamPaths paths) throws IOException {
        var contract = paths.workstreamDir().resolve("memory").resolve("read-reuse-contract.json");
        if (!Files.isRegularFile(contract)) {
            return ReadReusePolicy.empty();
        }
        var document = objectMapper.readValue(contract.toFile(), MAP_TYPE);
        if (Boolean.FALSE.equals(document.get("requireReasonWhenRereading"))) {
            return ReadReusePolicy.empty();
        }
        var reusableFiles = new LinkedHashSet<String>();
        for (var item : mapList(document.get("reusableReadFiles"))) {
            var normalized = normalizePath(text(item.get("path")));
            if (!normalized.isBlank()) {
                reusableFiles.add(normalized);
            }
        }
        return new ReadReusePolicy(reusableFiles, textList(document.get("allowedRepeatedReadReasons")));
    }

    private Set<String> planAuthorizedFiles(WorkstreamPaths paths) throws IOException {
        var plan = paths.workstreamDir().resolve("artifacts").resolve("plan").resolve("plan_artifact.json");
        if (!Files.isRegularFile(plan)) {
            return Set.of();
        }
        var document = objectMapper.readValue(plan.toFile(), MAP_TYPE);
        var files = new LinkedHashSet<String>();
        for (var file : textList(document.get("edit_files"))) {
            var normalized = normalizePath(file);
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        for (var file : textList(document.get("reference_files"))) {
            var normalized = normalizePath(file);
            if (!normalized.isBlank()) {
                files.add(normalized);
            }
        }
        return Set.copyOf(files);
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
        var explorationPolicy = map(toolMetrics.get("explorationPolicy"));
        builder.append("- Exploration Policy Warnings: ")
                .append(longValue(explorationPolicy.get("warnCount")))
                .append("\n");
        builder.append("- Exploration Policy Failures: ")
                .append(longValue(explorationPolicy.get("failCount")))
                .append("\n");
        builder.append("- Evidence Files: ").append(longValue(toolMetrics.get("evidenceFileCount"))).append("\n\n");
        builder.append("### Exploration Policy Violations\n\n");
        builder.append("| Node | Run | Severity | Type | Message |\n");
        builder.append("| --- | --- | --- | --- | --- |\n");
        var explorationViolations = mapList(explorationPolicy.get("violations"));
        if (explorationViolations.isEmpty()) {
            builder.append("| N/A | N/A | N/A | N/A | N/A |\n");
        } else {
            for (var item : explorationViolations) {
                builder.append("| ").append(item.get("nodeId"))
                        .append(" | ").append(item.get("runId"))
                        .append(" | ").append(item.get("severity"))
                        .append(" | ").append(item.get("type"))
                        .append(" | ").append(item.get("message"))
                        .append(" |\n");
            }
        }
        builder.append("\n");
        builder.append("### Repeated Read Files\n\n");
        builder.append("| File | Reads | Agents | Nodes | Evidence Known | Reread Reason |\n");
        builder.append("| --- | --- | --- | --- | --- | --- |\n");
        var repeatedReads = mapList(toolMetrics.get("repeatedReadFiles"));
        if (repeatedReads.isEmpty()) {
            builder.append("| N/A | 0 | N/A | N/A | false | N/A |\n");
        } else {
            for (var item : repeatedReads) {
                builder.append("| ").append(item.get("path"))
                        .append(" | ").append(item.get("count"))
                        .append(" | ").append(String.join(", ", textList(item.get("agents"))))
                        .append(" | ").append(String.join(", ", textList(item.get("nodes"))))
                        .append(" | ").append(item.get("evidenceKnown"))
                        .append(" | ").append(item.get("rereadReasonStatus"))
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

    private static final class CostEconomicsSummary {
        private final LinkedHashMap<String, Long> byReliability = new LinkedHashMap<>();
        private final LinkedHashMap<String, Long> byCostEstimateReliability = new LinkedHashMap<>();
        private final LinkedHashMap<String, Long> byDecision = new LinkedHashMap<>();
        private final LinkedHashMap<String, BigDecimal> clientReportedByCurrency = new LinkedHashMap<>();
        private final LinkedHashMap<String, BigDecimal> officialCalculatedByCurrency = new LinkedHashMap<>();

        void add(Map<String, Object> costEconomics) {
            var reliability = nonBlank(text(costEconomics.get("cacheSignalReliability")), "unknown");
            byReliability.merge(reliability, 1L, Long::sum);
            var costReliability = nonBlank(text(costEconomics.get("costEstimateReliability")), "unknown");
            byCostEstimateReliability.merge(costReliability, 1L, Long::sum);
            var decision = nonBlank(text(costEconomics.get("providerCacheDecision")), "unknown");
            byDecision.merge(decision, 1L, Long::sum);
            var reported = decimalValue(costEconomics.get("clientReportedCost"));
            var reportedCurrency = text(costEconomics.get("clientReportedCurrency"));
            if (!reportedCurrency.isBlank() && reported.signum() != 0) {
                clientReportedByCurrency.merge(reportedCurrency, reported, BigDecimal::add);
            }
            var calculated = decimalValue(costEconomics.get("officialCalculatedCost"));
            var calculatedCurrency = text(costEconomics.get("officialCalculatedCurrency"));
            if (!calculatedCurrency.isBlank() && calculated.signum() != 0) {
                officialCalculatedByCurrency.merge(calculatedCurrency, calculated, BigDecimal::add);
            }
        }

        Map<String, Object> toMap() {
            var out = new LinkedHashMap<String, Object>();
            out.put("byReliability", byReliability);
            out.put("byCostEstimateReliability", byCostEstimateReliability);
            out.put("byDecision", byDecision);
            out.put("clientReportedCostByCurrency", clientReportedByCurrency);
            out.put("officialCalculatedCostByCurrency", officialCalculatedByCurrency);
            out.put("providerReportedCostByCurrency", clientReportedByCurrency);
            out.put("dualCalculatedCostByCurrency", officialCalculatedByCurrency);
            return out;
        }
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
        private boolean rereadReasonRequired;
        private boolean reasonDeclared;
        private boolean planAuthorized;

        private ReadFileMetric(String path) {
            this.path = path;
        }

        void accept(String runId, String nodeId, String agent, boolean evidenceKnown,
                    boolean rereadReasonRequired, boolean reasonDeclared, boolean planAuthorized) {
            runIds.add(runId);
            nodes.add(nodeId);
            agents.add(agent);
            this.evidenceKnown = this.evidenceKnown || evidenceKnown;
            this.rereadReasonRequired = this.rereadReasonRequired || rereadReasonRequired;
            this.reasonDeclared = this.reasonDeclared || reasonDeclared;
            this.planAuthorized = this.planAuthorized || planAuthorized;
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

        boolean rereadReasonRequired() {
            return rereadReasonRequired;
        }

        boolean rereadReasonMissing() {
            return isRepeated() && rereadReasonRequired && !reasonDeclared && !planAuthorized;
        }

        String rereadReasonStatus() {
            if (!isRepeated() || !rereadReasonRequired) {
                return "not_required";
            }
            if (planAuthorized) {
                return "plan_authorized";
            }
            return reasonDeclared ? "recorded" : "missing";
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("path", path);
            map.put("count", count());
            map.put("runIds", List.copyOf(runIds));
            map.put("nodes", List.copyOf(nodes));
            map.put("agents", List.copyOf(agents));
            map.put("evidenceKnown", evidenceKnown);
            map.put("rereadReasonRequired", rereadReasonRequired);
            map.put("rereadReasonStatus", rereadReasonStatus());
            map.put("planAuthorized", planAuthorized);
            return map;
        }
    }

    private static Set<String> declaredRereadPaths(Object value) {
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        var paths = new LinkedHashSet<String>();
        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                var normalized = normalizePath(text(map.get("path")));
                if (!normalized.isBlank()) {
                    paths.add(normalized);
                }
            }
        }
        return paths;
    }

    private record ReadReusePolicy(Set<String> reusableFiles, List<String> allowedReasons) {
        static ReadReusePolicy empty() {
            return new ReadReusePolicy(Set.of(), List.of());
        }

        boolean requiresReason(String path) {
            return reusableFiles.contains(path);
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
