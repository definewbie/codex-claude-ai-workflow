package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "timeline", description = "Show the workflow timeline projection.", mixinStandardHelpOptions = true)
public final class TimelineCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new dev.dual.ledger.file.AtomicJsonWriter().objectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--raw", description = "Print the current ledger timeline as JSON.")
    boolean raw;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var timeline = CliLedgerFactory.create().timelineSnapshot(paths);
        if (raw) {
            System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(timeline));
            return 0;
        }

        System.out.println("Timeline");
        System.out.println("Workstream: " + text(timeline.get("workstreamId")));
        System.out.println("Workflow: " + text(timeline.get("workflowId")) + "@" + text(timeline.get("definitionVersion")));
        System.out.println("Status: " + text(timeline.get("workflowStatus")));
        System.out.println("Projection: .agent/workstreams/" + workstreamId + "/timeline.json");
        System.out.println();
        System.out.println("Nodes:");

        for (var node : nodes(timeline)) {
            System.out.println("  " + text(node.get("status")) + "  "
                    + text(node.get("nodeDefinitionId"))
                    + "  [" + text(node.get("kind")) + "]"
                    + "  " + text(node.get("iterationId")));
        }
        return 0;
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
