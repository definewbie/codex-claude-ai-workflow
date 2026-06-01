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
@Command(name = "timeline", description = "Show the workflow timeline projection.", mixinStandardHelpOptions = true)
public final class TimelineCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--raw", description = "Print raw timeline.json.")
    boolean raw;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            System.err.println("Timeline projection not found: " + paths.timelineJson());
            System.err.println("Run or resume the workflow once to generate timeline.json.");
            return 1;
        }

        if (raw) {
            System.out.println(Files.readString(paths.timelineJson()));
            return 0;
        }

        var timeline = OBJECT_MAPPER.readValue(paths.timelineJson().toFile(), MAP_TYPE);
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
                    .toList();
        }
        return List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
