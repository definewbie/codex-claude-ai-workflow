package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Component
@Command(name = "refresh", description = "Regenerate timeline and metrics projections from the ledger.", mixinStandardHelpOptions = true)
public final class ProjectionsRefreshCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        if (!Files.isRegularFile(paths.workflowCurrentJson())) {
            System.err.println("Workstream ledger not found: " + paths.workflowCurrentJson());
            return 1;
        }

        CliLedgerFactory.create().refreshProjections(paths);
        System.out.println("Projections refreshed");
        System.out.println("Timeline: " + paths.timelineJson());
        System.out.println("Metrics: " + paths.workstreamMetricsJson());
        executionConsistencySummary(paths).ifPresent(summary ->
                System.out.println("Execution consistency: " + summary));
        return 0;
    }

    private java.util.Optional<String> executionConsistencySummary(WorkstreamPaths paths) throws Exception {
        var metrics = CliLedgerFactory.create().metricsSnapshot(paths);
        if ("STALE".equals(metrics.get("status"))) return java.util.Optional.of("STALE: " + metrics.get("reason"));
        var providerMetrics = map(metrics.get("providerMetrics"));
        var counts = map(providerMetrics.get("executionConsistencyCounts"));
        var summary = counts.entrySet().stream()
                .filter(entry -> !"consistent".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return summary.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(summary);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
