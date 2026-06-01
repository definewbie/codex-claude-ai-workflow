package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "show", description = "Show the current workflow recovery summary.", mixinStandardHelpOptions = true)
public final class RecoveryShowCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var summaryPath = paths.currentFailureMarkdown();
        if (!Files.exists(summaryPath)) {
            System.err.println("No current failure summary found: " + workspaceRoot.relativize(summaryPath));
            return 1;
        }

        System.out.println("Current Failure");
        System.out.println("Path: " + workspaceRoot.relativize(summaryPath));
        System.out.println();
        System.out.print(Files.readString(summaryPath, StandardCharsets.UTF_8));
        return 0;
    }
}
