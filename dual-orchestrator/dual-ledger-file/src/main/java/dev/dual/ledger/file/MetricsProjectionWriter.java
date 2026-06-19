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
import java.util.List;
import java.util.Map;
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
            }
        }
        var result = new LinkedHashMap<String, Object>();
        for (var entry : byAgent.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toMap());
        }
        return result;
    }

    private Map<String, Object> tokenMetrics(WorkstreamPaths paths) throws IOException {
        if (!Files.isDirectory(paths.runsDir())) {
            return Map.of(
                    "runCount", 0,
                    "measuredRunCount", 0,
                    "total", new UsageMetric().toMap(),
                    "byAgent", Map.of(),
                    "byNode", Map.of(),
                    "runs", List.of()
            );
        }
        var total = new UsageMetric();
        var byAgent = new LinkedHashMap<String, UsageMetric>();
        var byNode = new LinkedHashMap<String, UsageMetric>();
        var runs = new ArrayList<Map<String, Object>>();
        long runCount = 0;
        long measuredRunCount = 0;
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
                item.put("durationMs", longValue(run.get("durationMs")));
                if (usage.isEmpty()) {
                    item.put("measured", false);
                } else {
                    measuredRunCount++;
                    var metric = UsageMetric.from(usage);
                    total.add(metric);
                    byAgent.computeIfAbsent(agent, ignored -> new UsageMetric()).add(metric);
                    byNode.computeIfAbsent(nodeId, ignored -> new UsageMetric()).add(metric);
                    item.put("measured", true);
                    item.put("usage", metric.toMap());
                }
                runs.add(item);
            }
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("runCount", runCount);
        result.put("measuredRunCount", measuredRunCount);
        result.put("total", total.toMap());
        result.put("byAgent", usageMap(byAgent));
        result.put("byNode", usageMap(byNode));
        result.put("runs", runs);
        return result;
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
        builder.append("- Input Tokens: ").append(longValue(total.get("inputTokens"))).append("\n");
        builder.append("- Output Tokens: ").append(longValue(total.get("outputTokens"))).append("\n");
        builder.append("- Cached Read Tokens: ").append(longValue(total.get("cachedReadTokens"))).append("\n");
        builder.append("- Cached Write Tokens: ").append(longValue(total.get("cachedWriteTokens"))).append("\n");
        builder.append("- Total Tokens: ").append(longValue(total.get("totalTokens"))).append("\n\n");
        builder.append("### Token Usage By Step\n\n");
        builder.append("| Node | Agent | Status | Input | Output | Cached Read | Cached Write | Total |\n");
        builder.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        var runs = tokenMetrics == null ? List.<Map<String, Object>>of() : mapList(tokenMetrics.get("runs"));
        if (runs.isEmpty()) {
            builder.append("| N/A | N/A | N/A | 0 | 0 | 0 | 0 | 0 |\n");
        } else {
            for (var run : runs) {
                var usage = map(run.get("usage"));
                builder.append("| ").append(run.get("nodeId"))
                        .append(" | ").append(run.get("agent"))
                        .append(" | ").append(run.get("status"))
                        .append(" | ").append(longValue(usage.get("inputTokens")))
                        .append(" | ").append(longValue(usage.get("outputTokens")))
                        .append(" | ").append(longValue(usage.get("cachedReadTokens")))
                        .append(" | ").append(longValue(usage.get("cachedWriteTokens")))
                        .append(" | ").append(longValue(usage.get("totalTokens")))
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

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
            return metric;
        }

        void add(UsageMetric other) {
            inputTokens += other.inputTokens;
            outputTokens += other.outputTokens;
            cachedReadTokens += other.cachedReadTokens;
            cachedWriteTokens += other.cachedWriteTokens;
            totalTokens += other.totalTokens;
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
            map.put("costByCurrency", new LinkedHashMap<>(costByCurrency));
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
