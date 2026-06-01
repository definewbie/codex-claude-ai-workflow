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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "watch", description = "Watch workflow progress using timeline.json.", mixinStandardHelpOptions = true)
public final class WatchCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--interval-ms", description = "Polling interval in milliseconds. Defaults to ${DEFAULT-VALUE}.")
    long intervalMs = 2000;

    @Option(names = "--once", description = "Render once and exit.")
    boolean once;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("--interval-ms must be positive");
        }
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            System.err.println("Timeline projection not found: " + paths.timelineJson());
            System.err.println("Run or resume the workflow once to generate timeline.json.");
            return 1;
        }

        while (true) {
            render(paths);
            if (once) {
                return 0;
            }
            Thread.sleep(intervalMs);
        }
    }

    private void render(WorkstreamPaths paths) throws Exception {
        var timeline = OBJECT_MAPPER.readValue(paths.timelineJson().toFile(), MAP_TYPE);
        var activeIds = listOfText(timeline.get("activeNodeInstanceIds"));
        var openGateIds = listOfText(timeline.get("openGateIds"));

        System.out.println("Dual Watch");
        System.out.println("Workstream: " + text(timeline.get("workstreamId")));
        System.out.println("Workflow: " + text(timeline.get("workflowId")) + "@" + text(timeline.get("definitionVersion")));
        System.out.println("Status: " + text(timeline.get("workflowStatus")));
        System.out.println("Active: " + String.join(", ", activeIds));
        System.out.println("Open gates: " + String.join(", ", openGateIds));
        System.out.println("Updated: " + text(timeline.get("updatedAt")));
        System.out.println();
        System.out.println("Recent Nodes:");
        for (var node : recentNodes(timeline, 8)) {
            System.out.println("  " + text(node.get("status")) + "  "
                    + text(node.get("nodeDefinitionId"))
                    + "  [" + text(node.get("kind")) + "]"
                    + "  " + text(node.get("iterationId")));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recentNodes(Map<String, Object> timeline, int limit) {
        var value = timeline.get("nodes");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var nodes = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        if (nodes.size() <= limit) {
            return nodes;
        }
        return nodes.subList(nodes.size() - limit, nodes.size());
    }

    private List<String> listOfText(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::text).toList();
        }
        return List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
