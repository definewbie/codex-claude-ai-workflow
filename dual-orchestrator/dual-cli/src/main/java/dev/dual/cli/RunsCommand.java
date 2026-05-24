package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "runs", description = "List agent runs for a workstream.", mixinStandardHelpOptions = true)
public final class RunsCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var paths = new WorkstreamPaths(workdir.toAbsolutePath().normalize(), new DualId(workstreamId));
        var runs = new RunMetadataReader().list(paths);
        if (runs.isEmpty()) {
            System.out.println("No agent runs found.");
            System.out.println("Runs dir: " + paths.runsDir());
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
        return 0;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
