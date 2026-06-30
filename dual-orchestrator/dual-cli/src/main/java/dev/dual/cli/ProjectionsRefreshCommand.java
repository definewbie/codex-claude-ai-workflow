package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "refresh", description = "Regenerate timeline and metrics projections from the ledger.", mixinStandardHelpOptions = true)
public final class ProjectionsRefreshCommand implements Callable<Integer> {
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

        new WorkflowFileLedger().refreshProjections(paths);
        System.out.println("Projections refreshed");
        System.out.println("Timeline: " + paths.timelineJson());
        System.out.println("Metrics: " + paths.workstreamMetricsJson());
        return 0;
    }
}
