package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
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
        if (write || output != null) {
            var target = output == null
                    ? paths.workstreamDir().resolve("reports").resolve("workstream-summary.md")
                    : output.toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, markdown, StandardCharsets.UTF_8);
            System.out.println("Workstream summary written");
            System.out.println("Path: " + target);
            return 0;
        }

        System.out.print(markdown);
        return 0;
    }

    private String render(WorkstreamPaths paths) throws Exception {
        var timeline = readMap(paths.timelineJson());
        var delivery = readMap(paths.workstreamDir().resolve("delivery").resolve("delivery.json"));
        var activeNodes = listOfText(timeline.get("activeNodeInstanceIds"));
        var openGates = listOfText(timeline.get("openGateIds"));
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
        lines.add("- Status: " + text(timeline.get("workflowStatus"), "(timeline unavailable)"));
        lines.add("- Active Nodes: " + emptyDash(activeNodes));
        lines.add("- Open Gates: " + emptyDash(openGates));
        lines.add("- Updated At: " + text(timeline.get("updatedAt")));
        lines.add("");
        lines.add("## Recommended Next Action");
        lines.add("");
        lines.add(recommendedAction(paths, timeline, activeNodes, openGates));
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
        lines.add("- Current Failure Summary: " + presence(paths.currentFailureMarkdown()));
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

    private String recommendedAction(WorkstreamPaths paths, Map<String, Object> timeline, List<String> activeNodes,
                                     List<String> openGates) {
        var status = text(timeline.get("workflowStatus"));
        if ("WAITING_FOR_HUMAN".equals(status)) {
            return "Review the current packet, then approve or revise:\n\n```bash\n"
                    + "dualctl review show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value() + "\n"
                    + "dualctl approve --workdir " + quote(paths.workspaceRoot()) + " --run " + paths.workstreamId().value() + "\n"
                    + "```";
        }
        if ("WAITING_FOR_RECOVERY".equals(status) || "FAILED".equals(status) || "BLOCKED".equals(status)) {
            return "Inspect the failure summary, fix the external cause or ledger issue, then retry when appropriate:\n\n```bash\n"
                    + "dualctl recovery show --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value() + "\n"
                    + "dualctl retry --workdir " + quote(paths.workspaceRoot()) + " " + paths.workstreamId().value() + "\n"
                    + "```";
        }
        if ("COMPLETED".equals(status)) {
            return "Review final report and delivery artifacts. If needed, prepare commit or PR from delivery commands.";
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
                    .toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> recent(List<Map<String, Object>> nodes, int limit) {
        return nodes.stream()
                .skip(Math.max(0, nodes.size() - limit))
                .toList();
    }

    private String calibrationSummary(Path dir) throws Exception {
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

    private String countJson(Path dir) throws Exception {
        return String.valueOf(jsonFiles(dir).size());
    }

    private List<Path> jsonFiles(Path dir) throws Exception {
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

    private String deliverySummary(Map<String, Object> delivery) {
        if (delivery.isEmpty()) {
            return "unavailable";
        }
        var parts = new ArrayList<String>();
        parts.add(text(delivery.get("status"), "unknown"));
        if (Boolean.TRUE.equals(delivery.get("commitCreated"))) {
            parts.add("commit=" + text(delivery.get("commit")));
        }
        if (Boolean.TRUE.equals(delivery.get("prCreated"))) {
            parts.add("pr=" + text(delivery.get("prUrl")));
        }
        return String.join(", ", parts);
    }

    private String presence(Path path) {
        return Files.isRegularFile(path) ? path.toString() : "missing";
    }

    @SuppressWarnings("unchecked")
    private List<String> listOfText(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String emptyDash(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    private String stripJsonSuffix(Path path) {
        var name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
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

    private String md(String value) {
        return value == null || value.isBlank() ? "" : value.replace("|", "\\|");
    }

    private String quote(Path path) {
        return "'" + path.toString().replace("'", "'\\''") + "'";
    }
}
