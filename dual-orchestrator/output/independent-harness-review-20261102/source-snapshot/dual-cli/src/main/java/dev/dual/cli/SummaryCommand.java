package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "summary", description = "Print or write a compact workstream run summary.", mixinStandardHelpOptions = true)
public final class SummaryCommand implements Callable<Integer> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--write", description = "Write the summary to reports/workstream-summary.md.")
    boolean write;

    @Option(names = "--output", description = "Write the summary to a custom path.")
    Path output;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        if (!Files.isDirectory(paths.workstreamDir())) {
            System.err.println("Workstream ledger not found: " + paths.workstreamDir());
            return 1;
        }

        var markdown = render(paths);
        var target = output == null
                ? paths.workstreamDir().resolve("reports").resolve("workstream-summary.md")
                : output.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        System.out.print(markdown);
        return 0;
    }

    static Path writeDefaultSummary(WorkstreamPaths paths) throws Exception {
        var target = paths.workstreamDir().resolve("reports").resolve("workstream-summary.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, render(paths), StandardCharsets.UTF_8);
        return target;
    }

    static String render(WorkstreamPaths paths) throws Exception {
        var ledger = CliLedgerFactory.create();
        var observedDelivery = new DeliveryApplicationService(ledger).view(paths.workspaceRoot(), paths.workstreamId().value(), null);
        String summary;
        try (var snapshot = ledger.readSession(paths)) {
            summary = renderSnapshot(paths, snapshot.ledger(), observedDelivery);
        }
        var permissions = dev.dual.runtime.handler.NativePermissionRequests.view(ledger, paths);
        if (!((List<?>) permissions.get("requests")).isEmpty()) summary += "\n\n## Native permission requests\n\n"
                + "This separate snapshot grants no business approval or resend authority.\n\n```json\n"
                + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(permissions) + "\n```\n";
        return summary;
    }

    private static String renderSnapshot(WorkstreamPaths paths, dev.dual.ledger.file.WorkflowFileLedger ledger, DeliveryApplicationService.View observedDelivery) throws Exception {
        var timeline = ledger.timelineSnapshot(paths);
        var metrics = ledger.metricsSnapshot(paths);
        var current = ledger.loadCurrent(paths);
        var workflowStatus = current.status().name();
        var statusSource = "workflow_current";
        var activeNodes = current.activeNodeInstanceIds();
        var openGates = current.openGateIds();
        var delivery = DeliveryApplicationService.inSnapshot(observedDelivery, ledger, paths);
        var nodes = nodes(timeline);

        var lines = new ArrayList<String>();
        lines.add("# Workstream Summary");
        lines.add("");
        lines.add("- Workstream: " + paths.workstreamId().value());
        lines.add("- Workspace: " + paths.workspaceRoot());
        lines.add("- Ledger: " + paths.workstreamDir());
        lines.add("- Generated At: " + Instant.now());
        lines.add("");
        lines.add("## Workflow");
        lines.add("");
        lines.add("- Definition: " + text(timeline.get("workflowId")) + "@" + text(timeline.get("definitionVersion")));
        lines.add("- Status: " + text(workflowStatus, "(unavailable)"));
        lines.add("- Status Source: " + statusSource);
        lines.add("- Source Commit: " + current.commitSequence());
        lines.add("- Active Nodes: " + emptyDash(activeNodes));
        lines.add("- Open Gates: " + emptyDash(openGates));
        lines.add("- Updated At: " + text(timeline.get("updatedAt")));
        lines.add("");
        lines.add("## Metrics");
        lines.add("");
        lines.add("- Metrics State: " + text(metrics.get("status"), "CURRENT"));
        lines.add("- Nodes: " + text(metrics.get("nodeCount"), "unavailable"));
        lines.add("- Agent Runs: " + text(metrics.get("agentRunCount"), "unavailable"));
        lines.add("- Gates: " + text(metrics.get("gateCount"), "unavailable"));
        lines.add("- Decisions: " + text(metrics.get("decisionCount"), "unavailable"));
        lines.add("- Iterations: " + text(metrics.get("iterationCount"), "unavailable"));
        lines.add("- Revisions: " + text(metrics.get("revisionCount"), "unavailable"));
        lines.add("- Total Node Duration(ms): " + text(metrics.get("totalNodeDurationMs"), "unavailable"));
        var tokenMetrics = map(metrics.get("tokenMetrics"));
        var tokenTotal = map(tokenMetrics.get("total"));
        if (!tokenMetrics.isEmpty()) {
            lines.add("- Token Runs: " + text(tokenMetrics.get("measuredRunCount"), "0")
                    + " / " + text(tokenMetrics.get("runCount"), "0"));
            lines.add("- Total Tokens: " + text(tokenTotal.get("totalTokens"), "0")
                    + " (input " + text(tokenTotal.get("inputTokens"), "0")
                    + ", output " + text(tokenTotal.get("outputTokens"), "0") + ")");
        }
        lines.add("- Metrics: " + presence(paths.workstreamMetricsMarkdown()));
        lines.add("");
        lines.add("## Recommended Next Action");
        lines.add("");
        lines.add(recommendedAction(paths, workflowStatus, statusSource, delivery, activeNodes, openGates));
        lines.add("");
        lines.add("## Recent Timeline");
        lines.add("");
        lines.add("| Node | Kind | Status | Iteration | Duration |");
        lines.add("| --- | --- | --- | --- | --- |");
        for (var node : recent(nodes, 12)) {
            lines.add("| " + md(text(node.get("nodeDefinitionId"))) + " | "
                    + md(text(node.get("kind"))) + " | "
                    + md(text(node.get("status"))) + " | "
                    + md(text(node.get("iterationId"))) + " | "
                    + md(text(node.get("durationMs"))) + " |");
        }
        if (nodes.isEmpty()) {
            lines.add("| N/A | N/A | N/A | N/A | N/A |");
        }
        lines.add("");
        lines.add("## Artifacts");
        lines.add("");
        lines.add("- Final Report: " + presence(paths.workstreamDir().resolve("reports").resolve("latest-final-report.md")));
        lines.add("- Current Failure Summary: " + currentFailurePresence(paths, workflowStatus));
        lines.add("- Delivery: " + deliverySummary(delivery));
        lines.add("- Calibration Candidates: " + calibrationSummary(paths.workstreamDir().resolve("calibration").resolve("candidates")));
        lines.add("- Calibration Apply Plans: " + countJson(paths.workstreamDir().resolve("calibration").resolve("apply-plans")));
        lines.add("");
        lines.add("## Useful Commands");
        lines.add("");
        lines.add("```bash");
        lines.add("dualctl status --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value());
        lines.add("dualctl timeline --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value());
        lines.add("dualctl review show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value());
        lines.add("dualctl recovery show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value());
        lines.add("```");
        lines.add("");
        return String.join("\n", lines);
    }

    private static String recommendedAction(
            WorkstreamPaths paths,
            String status,
            String statusSource,
            DeliveryApplicationService.View delivery,
            List<String> activeNodes,
            List<String> openGates
    ) throws java.io.IOException {
        if (!"workflow_current".equals(statusSource)) {
            return "Current workflow state is unavailable. Inspect the frozen ledger; no action command is projected.";
        }
        if ("WAITING_FOR_HUMAN".equals(status)) {
            return "Review the current packet, then approve or revise:\n\n```bash\n"
                    + "dualctl review show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value() + "\n"
                    + "dualctl approve --workdir " + quote(paths.workspaceRoot()) + " --run " + paths.workstreamId().value() + "\n"
                    + "```";
        }
        if ("WAITING_FOR_RECOVERY".equals(status) || "FAILED".equals(status) || "BLOCKED".equals(status)) {
            var attention = WorkstreamAttentionEvaluator.evaluate(paths);
            var command = attention.command().isBlank()
                    ? "dualctl recovery show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value()
                    : attention.command();
            return attention.title() + "\n\n"
                    + attention.detail() + "\n\n"
                    + attention.nextAction() + "\n\n```bash\n"
                    + "dualctl recovery show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value() + "\n"
                    + command + "\n"
                    + "```";
        }
        if ("COMPLETED".equals(status)) {
            return delivery.nextAction() + (delivery.reasonCode().isBlank() ? "" : "\n\nReason: " + delivery.reasonCode())
                    + (delivery.nextCommands().isEmpty() ? "" : "\n\n```bash\n" + String.join("\n", delivery.nextCommands()) + "\n```")
                    + (delivery.resolutionTemplate().isEmpty() ? "" : "\n\nResolution template (complete the human decision explicitly):\n\n```json\n"
                    + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(delivery.resolutionTemplate()) + "\n```");
        }
        if ("RUNNING".equals(status) && activeNodes.isEmpty() && openGates.isEmpty()) {
            return "Workflow is marked RUNNING but no active node is visible. Run status and inspect ledger consistency.";
        }
        if ("RUNNING".equals(status)) {
            return "Continue the workflow until the next gate, recovery, failure, or completion:\n\n```bash\n"
                    + "dualctl run --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value() + "\n"
                    + "```";
        }
        return "Run status/timeline to inspect the current workflow state.";
    }

    private static String commandBlock(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        return "\n\n```bash\n" + command + "\n```";
    }

    private static String currentFailurePresence(WorkstreamPaths paths, String status) {
        if ("WAITING_FOR_RECOVERY".equals(status) || "FAILED".equals(status) || "BLOCKED".equals(status)) {
            return presence(paths.currentFailureMarkdown());
        }
        if (Files.isRegularFile(paths.currentFailureMarkdown())) {
            return "not current (historical recovery artifact retained)";
        }
        return "missing";
    }

    private static Map<String, Object> readMap(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        return MAPPER.readValue(path.toFile(), MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodes(Map<String, Object> timeline) {
        var value = timeline.get("nodes");
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            var result = new java.util.LinkedHashMap<String, Object>();
            for (var entry : source.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private static List<Map<String, Object>> recent(List<Map<String, Object>> nodes, int limit) {
        return nodes.stream()
                .skip(Math.max(0, nodes.size() - limit))
                .toList();
    }

    private static String calibrationSummary(Path dir) throws Exception {
        var paths = jsonFiles(dir);
        if (paths.isEmpty()) {
            return "0";
        }
        var parts = new ArrayList<String>();
        for (var path : paths) {
            var json = MAPPER.readTree(path.toFile());
            parts.add(json.path("candidateId").asText(stripJsonSuffix(path)) + "=" + json.path("status").asText("UNKNOWN"));
        }
        return paths.size() + " (" + String.join(", ", parts) + ")";
    }

    private static String countJson(Path dir) throws Exception {
        return String.valueOf(jsonFiles(dir).size());
    }

    private static List<Path> jsonFiles(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static String deliverySummary(DeliveryApplicationService.View delivery) {
        var parts = new ArrayList<String>();
        parts.add(delivery.status());
        parts.add("changedFiles=" + delivery.changedFiles().size());
        var candidateSha = delivery.candidateSha256();
        if (!candidateSha.isBlank()) {
            parts.add("candidateSha256=" + candidateSha);
        }
        var targetRepo = delivery.targetRepo();
        if (!targetRepo.isBlank()) {
            parts.add("target=" + targetRepo);
        }
        if (!delivery.commit().isBlank()) {
            parts.add("commit=" + delivery.commit());
        }
        if (!delivery.reason().isBlank()) {
            parts.add("reason=" + delivery.reasonCode() + ":" + delivery.reason());
        }
        return String.join(", ", parts);
    }

    private static String presence(Path path) {
        return Files.isRegularFile(path) ? path.toString() : "missing";
    }

    @SuppressWarnings("unchecked")
    private static List<String> listOfText(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String emptyDash(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private static String stripJsonSuffix(Path path) {
        var name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private static String text(Object value) {
        return text(value, "");
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private static String md(String value) {
        return value == null || value.isBlank() ? "" : value.replace("|", "\\|");
    }

    private static String quote(Path path) {
        return "'" + path.toString().replace("'", "'\\''") + "'";
    }

    private static String quoteString(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
