package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.IterationSemantics;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "trace", description = "Show a one-screen trace for a workstream.", mixinStandardHelpOptions = true)
public final class TraceCommand implements Callable<Integer> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--max-nodes", description = "Maximum timeline nodes to print. Default: 24.")
    int maxNodes = 24;

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
        if (!Files.isRegularFile(paths.timelineJson())) {
            System.err.println("Timeline projection not found: " + paths.timelineJson());
            System.err.println("Run or resume the workflow once to generate timeline.json.");
            return 1;
        }

        var timeline = readMap(paths.timelineJson());
        var summary = readMap(paths.workstreamDir().resolve("reports").resolve("real-case-summary.json"));
        var nodes = nodes(timeline);
        var activeNodes = listOfText(timeline.get("activeNodeInstanceIds"));
        var openGates = listOfText(timeline.get("openGateIds"));
        var acceptance = acceptance(paths, summary);
        var goalSatisfaction = readMap(paths.goalSatisfactionJson());
        var completionAudit = readMap(paths.completionAuditJson());
        var repairActions = repairActions(paths, summary);
        var repairLoopCount = Math.max(acceptance.repairLoopCount(), repairActions.size());
        var status = text(timeline.get("workflowStatus"), "UNKNOWN");
        var currentNode = currentNode(nodes, activeNodes);
        var artifacts = artifacts(paths, status);
        var workstreamAttention = WorkstreamAttentionEvaluator.evaluate(paths);
        var nextAction = nextAction(workstreamAttention);
        var attention = attention(paths, status, acceptance, repairLoopCount, artifacts);

        System.out.println("Trace");
        System.out.println("Workstream: " + paths.workstreamId().value());
        System.out.println("Workspace: " + paths.workspaceRoot());
        System.out.println("Ledger: " + paths.workstreamDir());
        System.out.println("Workflow: " + text(timeline.get("workflowId")) + "@" + text(timeline.get("definitionVersion")));
        System.out.println("Status: " + status);
        System.out.println("Current Node: " + currentNode);
        System.out.println("Open Gates: " + emptyDash(openGates));
        System.out.println("Final Acceptance: " + acceptance.finalDecision());
        System.out.println("Goal Outcome: " + text(goalSatisfaction.get("domainOutcome"), "unknown"));
        System.out.println("End State: " + text(completionAudit.get("lifecycleState"), "unknown"));
        System.out.println("Completion Audit: " + text(completionAudit.get("status"), "unknown"));
        System.out.println("Repair Loops: " + repairLoopCount);
        System.out.println("Verification Confidence: " + acceptance.verificationConfidence());
        System.out.println();

        System.out.println("Why:");
        System.out.println("  " + workstreamAttention.detail());
        System.out.println();

        System.out.println("Attention:");
        System.out.println("  [" + workstreamAttention.severity() + "] " + workstreamAttention.title());
        System.out.println("    " + workstreamAttention.detail());
        if (!workstreamAttention.command().isBlank()) {
            System.out.println("    Command: " + workstreamAttention.command());
        }
        for (var item : attention) {
            System.out.println("  [" + item.severity() + "] " + item.title());
            System.out.println("    " + item.detail());
            if (!item.command().isBlank()) {
                System.out.println("    Command: " + item.command());
            }
        }
        System.out.println();

        System.out.println("Timeline:");
        for (var node : recent(nodes, Math.max(1, maxNodes))) {
            System.out.println("  " + marker(node) + " "
                    + displayName(node) + suffix(node));
        }
        for (var action : repairActions) {
            System.out.println("  [loop] repair: " + action);
        }
        System.out.println();

        System.out.println("Latest Agent Tasks:");
        var agentTasks = latestAgentTasks(paths, 6);
        if (agentTasks.isEmpty()) {
            System.out.println("  -");
        } else {
            for (var task : agentTasks) {
                System.out.println("  " + text(task.get("agentTaskId")) + "  "
                        + text(task.get("status")) + "  "
                        + text(task.get("agent")) + "  "
                        + text(task.get("nodeDefinitionId")) + "  "
                        + text(task.get("nodeExecutionId")));
            }
        }
        System.out.println();

        System.out.println("Latest Agent Runs:");
        var runListing = new RunMetadataReader().listWithWarnings(paths);
        var runs = runListing.runs();
        if (runs.isEmpty()) {
            System.out.println("  -");
        } else {
            for (var run : recentRuns(runs, 6)) {
                System.out.println("  " + text(run.runId()) + "  "
                        + text(run.agent()) + "  "
                        + text(run.nodeId()) + "  "
                        + text(run.status()) + "  "
                        + run.durationMs() + "ms");
            }
        }
        printRunMetadataWarnings(runListing.warnings());
        System.out.println();

        System.out.println("Artifacts:");
        for (var artifact : artifacts) {
            printArtifact(artifact.label(), artifact.path());
        }
        System.out.println();

        System.out.println("Next:");
        System.out.println(nextAction);
        return 0;
    }

    private Map<String, Object> readMap(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        return MAPPER.readValue(path.toFile(), MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nodes(Map<String, Object> timeline) {
        var value = timeline.get("nodes");
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .sorted(this::compareTimelineNodes)
                    .toList();
        }
        return List.of();
    }

    private int compareTimelineNodes(Map<String, Object> left, Map<String, Object> right) {
        var byTime = timelineInstant(left).compareTo(timelineInstant(right));
        if (byTime != 0) {
            return byTime;
        }
        return Comparator.comparing((Map<String, Object> node) -> text(node.get("nodeInstanceId")))
                .compare(left, right);
    }

    private Instant timelineInstant(Map<String, Object> node) {
        var startedAt = parseInstant(text(node.get("startedAt")));
        if (startedAt != null) {
            return startedAt;
        }
        var finishedAt = parseInstant(text(node.get("finishedAt")));
        if (finishedAt != null) {
            return finishedAt;
        }
        return Instant.MAX;
    }

    private Instant parseInstant(String value) {
        if (value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<String> repairActions(WorkstreamPaths paths, Map<String, Object> summary) throws Exception {
        var events = paths.eventsJsonl();
        var actions = new ArrayList<String>();
        if (!Files.isRegularFile(events)) {
            if (number(summary.get("repair_loop_count")) > 0) {
                actions.add("see-summary");
            }
            return actions;
        }
        for (var line : Files.readAllLines(events)) {
            if (line.isBlank()) {
                continue;
            }
            var event = MAPPER.readValue(line, MAP_TYPE);
            if (!"iteration_started".equals(text(event.get("event_type")))) {
                continue;
            }
            var payload = asMap(event.get("payload"));
            var action = text(payload.get("trigger_action"));
            if (IterationSemantics.isRepairLoopTrigger(action)) {
                actions.add(action);
            }
        }
        if (actions.isEmpty() && number(summary.get("repair_loop_count")) > 0) {
            actions.add("see-summary");
        }
        return actions;
    }

    private List<Map<String, Object>> latestAgentTasks(WorkstreamPaths paths, int limit) throws Exception {
        if (!Files.isDirectory(paths.agentTasksDir())) {
            return List.of();
        }
        var tasks = new ArrayList<Map<String, Object>>();
        try (var stream = Files.list(paths.agentTasksDir())) {
            var files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (var file : files) {
                tasks.add(readMap(file));
            }
        }
        return tasks.stream()
                .sorted(Comparator.comparing((Map<String, Object> task) -> text(task.get("createdAt"))))
                .skip(Math.max(0, tasks.size() - limit))
                .toList();
    }

    private Acceptance acceptance(WorkstreamPaths paths, Map<String, Object> summary) throws Exception {
        var summaryDecision = text(summary.get("final_acceptance_decision"));
        var summaryRepairLoops = number(summary.get("repair_loop_count"));
        var summaryConfidence = text(summary.get("verification_confidence"));
        var decisions = new ArrayList<String>();
        if (Files.isDirectory(paths.runsDir())) {
            try (var stream = Files.walk(paths.runsDir(), 4)) {
                var contracts = stream
                        .filter(path -> path.getFileName().toString().equals("acceptance-codex-review.contract.json"))
                        .sorted()
                        .toList();
                for (var contract : contracts) {
                    var json = readMap(contract);
                    var decision = text(json.get("decision"));
                    if (!decision.isBlank()) {
                        decisions.add(decision);
                    }
                }
            }
        }
        var decision = summaryDecision.isBlank()
                ? decisions.isEmpty() ? "none" : decisions.get(decisions.size() - 1)
                : summaryDecision;
        var confidence = summaryConfidence.isBlank() ? confidence(decision) : summaryConfidence;
        return new Acceptance(decision, summaryRepairLoops, confidence);
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String currentNode(List<Map<String, Object>> nodes, List<String> activeNodes) {
        if (activeNodes.isEmpty()) {
            return "-";
        }
        var active = activeNodes.get(0);
        for (var node : nodes) {
            if (active.equals(text(node.get("nodeInstanceId")))) {
                return text(node.get("nodeDefinitionId"), active);
            }
        }
        return active;
    }

    private String nextAction(dev.dual.ledger.file.WorkstreamAttention attention) {
        if (attention.command().isBlank()) {
            return attention.nextAction();
        }
        return attention.nextAction() + "\n" + attention.command();
    }

    private List<AttentionItem> attention(WorkstreamPaths paths, String status, Acceptance acceptance,
                                          int repairLoopCount, List<ArtifactRef> artifacts) {
        var items = new ArrayList<AttentionItem>();
        var base = "--workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value();
        if ("ACCEPT_WITH_NOTES".equals(acceptance.finalDecision())) {
            items.add(new AttentionItem(
                    "warning",
                    "Accepted with notes",
                    "Codex accepted the run but left notes. Review the final report before delivery.",
                    "dualctl summary " + base
            ));
        } else if ("REJECT".equals(acceptance.finalDecision())) {
            items.add(new AttentionItem(
                    "critical",
                    "Final acceptance rejected",
                    "Do not deliver until the rejection has been classified and repaired.",
                    "dualctl recovery show " + base
            ));
        }
        if (!"none".equals(acceptance.finalDecision())
                && ("LOW".equals(acceptance.verificationConfidence()) || "UNKNOWN".equals(acceptance.verificationConfidence()))) {
            items.add(new AttentionItem(
                    "warning",
                    "Verification confidence is " + acceptance.verificationConfidence(),
                    "Evidence is weak or missing. Read the self-test report, Git evidence, and Codex acceptance output.",
                    "dualctl summary " + base
            ));
        }
        if (repairLoopCount > 0) {
            items.add(new AttentionItem(
                    "info",
                    "Repair loop occurred",
                    "This run required " + repairLoopCount + " repair loop(s). Check the repaired diff and final acceptance notes.",
                    "dualctl timeline " + base
            ));
        }
        if ("COMPLETED".equals(status)) {
            for (var artifact : artifacts) {
                if (artifact.required() && !Files.isRegularFile(artifact.path())) {
                    items.add(new AttentionItem(
                            "warning",
                            "Missing " + artifact.label(),
                            "A key lifecycle artifact is missing from the ledger.",
                            "dualctl summary " + base
                    ));
                }
            }
        }
        if (items.isEmpty() && "COMPLETED".equals(status) && "ACCEPT".equals(acceptance.finalDecision())) {
            items.add(new AttentionItem(
                    "ok",
                    "No obvious follow-up",
                    "The workflow completed with ACCEPT and required artifacts are present.",
                    "dualctl summary " + base
            ));
        }
        return items;
    }

    private List<ArtifactRef> artifacts(WorkstreamPaths paths, String status) {
        var artifacts = new ArrayList<>(List.of(
                new ArtifactRef("Final Report", paths.workstreamDir().resolve("reports").resolve("latest-final-report.md"), true),
                new ArtifactRef("Workstream Summary", paths.workstreamDir().resolve("reports").resolve("workstream-summary.md"), true),
                new ArtifactRef("Handoff JSON", paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("handoff.json"), true),
                new ArtifactRef("Next-stage Prompt", paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("next-stage-prompt.md"), true),
                new ArtifactRef("Delivery PR Body", paths.workstreamDir().resolve("delivery").resolve("pr-body.md"), false),
                new ArtifactRef("Delivery Merge Plan", paths.deliveryMergePlanJson(), false)
        ));
        if ("FAILED".equals(status) || "BLOCKED".equals(status) || "WAITING_FOR_RECOVERY".equals(status)) {
            artifacts.add(new ArtifactRef("Current Failure", paths.currentFailureMarkdown(), false));
        }
        return artifacts;
    }

    private List<Map<String, Object>> recent(List<Map<String, Object>> nodes, int limit) {
        return nodes.stream()
                .skip(Math.max(0, nodes.size() - limit))
                .toList();
    }

    private List<RunMetadata> recentRuns(List<RunMetadata> runs, int limit) {
        return runs.stream()
                .skip(Math.max(0, runs.size() - limit))
                .toList();
    }

    private void printRunMetadataWarnings(List<RunMetadataReader.RunMetadataWarning> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        // Trace 是只读观测入口：历史 run metadata 缺失只能显示为 warning，
        // 不能让已经完成的 workstream 因投影读取问题重新变成失败。
        System.out.println("Run Metadata Warnings:");
        for (var warning : warnings) {
            System.out.println("  " + warning.runId()
                    + "  metadata unavailable: " + text(warning.reason())
                    + "  " + warning.path());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> listOfText(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String marker(Map<String, Object> node) {
        return switch (text(node.get("status"))) {
            case "COMPLETED" -> "[ok]";
            case "RUNNING" -> "[run]";
            case "WAITING" -> "[wait]";
            case "FAILED", "BLOCKED" -> "[fail]";
            case "SKIPPED" -> "[skip]";
            default -> "[ ]";
        };
    }

    private String displayName(Map<String, Object> node) {
        var displayName = text(node.get("displayName"));
        return displayName.isBlank() ? text(node.get("nodeDefinitionId")) : displayName;
    }

    private String suffix(Map<String, Object> node) {
        var parts = new ArrayList<String>();
        var iteration = text(node.get("iterationId"));
        var duration = text(node.get("durationMs"));
        if (!iteration.isBlank()) {
            parts.add(iteration);
        }
        if (!duration.isBlank()) {
            parts.add(duration + "ms");
        }
        return parts.isEmpty() ? "" : "  (" + String.join(", ", parts) + ")";
    }

    private String confidence(String finalDecision) {
        return switch (finalDecision) {
            case "ACCEPT" -> "HIGH";
            case "ACCEPT_WITH_NOTES" -> "MEDIUM";
            case "REJECT" -> "LOW";
            default -> "UNKNOWN";
        };
    }

    private void printArtifact(String label, Path path) {
        System.out.println("  " + label + ": " + (Files.isRegularFile(path) ? path : "missing"));
    }

    private String emptyDash(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private String empty(String value, String fallback) {
        return value == null || value.isBlank() || "-".equals(value) ? fallback : value;
    }

    private String gateSuffix(List<String> openGates) {
        return openGates.isEmpty() ? "" : ": " + String.join(", ", openGates);
    }

    private String quote(Path path) {
        return "\"" + path + "\"";
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private record Acceptance(String finalDecision, int repairLoopCount, String verificationConfidence) {
    }

    private record ArtifactRef(String label, Path path, boolean required) {
    }

    private record AttentionItem(String severity, String title, String detail, String command) {
    }
}
