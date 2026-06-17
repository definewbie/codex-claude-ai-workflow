package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Component
@Command(name = "runs", description = "List agent runs for a workstream.", mixinStandardHelpOptions = true)
public final class RunsCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--agent", description = "Filter by agent name, for example claude or codex.")
    String agent;

    @Option(names = "--status", description = "Filter by run status.")
    String status;

    @Option(names = "--failed", description = "Show only failed runs.")
    boolean failed;

    @Option(names = "--latest", description = "Only show the latest matching run.")
    boolean latest;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var paths = new WorkstreamPaths(workdir.toAbsolutePath().normalize(), new DualId(workstreamId));
        var listing = new RunMetadataReader().listWithWarnings(paths);
        var runs = filter(listing.runs().stream()).toList();
        if (latest && !runs.isEmpty()) {
            runs = runs.subList(runs.size() - 1, runs.size());
        }
        if (runs.isEmpty()) {
            System.out.println("No matching agent runs found.");
            System.out.println("Runs dir: " + paths.runsDir());
            printWarnings(listing.warnings());
            return 0;
        }
        System.out.println("Run ID\tStatus\tAgent\tNode\tExit\tDuration(ms)\tFailure");
        for (var run : runs) {
            System.out.println(String.join("\t",
                    text(run.runId()),
                    text(run.status()),
                    text(run.agent()),
                    text(run.nodeId()),
                    run.exitCode() == null ? "" : run.exitCode().toString(),
                    Long.toString(run.durationMs()),
                    text(run.failureReason())
            ));
        }
        printWarnings(listing.warnings());
        return 0;
    }

    private void printWarnings(java.util.List<RunMetadataReader.RunMetadataWarning> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println("Run metadata warnings:");
        for (var warning : warnings) {
            System.out.println("- " + warning.runId()
                    + ": metadata unavailable: " + text(warning.reason())
                    + " (" + warning.path() + ")");
        }
    }

    private Stream<RunMetadata> filter(Stream<RunMetadata> runs) {
        var filtered = runs;
        if (agent != null && !agent.isBlank()) {
            filtered = filtered.filter(run -> equalsIgnoreCase(run.agent(), agent));
        }
        if (status != null && !status.isBlank()) {
            filtered = filtered.filter(run -> equalsIgnoreCase(run.status(), status));
        }
        if (failed) {
            filtered = filtered.filter(run ->
                    equalsIgnoreCase(run.status(), "failed")
                            || (run.exitCode() != null && run.exitCode() != 0)
                            || (run.failureReason() != null && !run.failureReason().isBlank())
            );
        }
        return filtered;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null
                && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }
}
