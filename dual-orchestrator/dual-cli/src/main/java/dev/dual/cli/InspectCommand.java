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
@Command(name = "inspect", description = "Inspect active node, a node, or an agent run.", mixinStandardHelpOptions = true)
public final class InspectCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--node", description = "Node instance id to inspect. Defaults to active node.")
    String nodeInstanceId;

    @Option(names = "--run", description = "Run id to inspect.")
    String runId;

    @Option(names = "--show", description = "For --run, show prompt, output, or stderr.")
    String show;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        if (runId != null && !runId.isBlank()) {
            inspectRun(workspaceRoot, paths);
            return 0;
        }
        inspectNode(paths);
        return 0;
    }

    private void inspectNode(WorkstreamPaths paths) throws Exception {
        var ledger = new WorkflowFileLedger();
        var id = nodeInstanceId;
        if (id == null || id.isBlank()) {
            var current = ledger.loadCurrent(paths);
            if (current.activeNodeInstanceIds().isEmpty()) {
                System.out.println("No active node.");
                return;
            }
            id = current.activeNodeInstanceIds().get(0);
        }
        var node = ledger.loadNode(paths, id);
        System.out.println("Node: " + node.nodeInstanceId());
        System.out.println("Definition: " + node.nodeDefinitionId());
        System.out.println("Kind: " + node.kind());
        System.out.println("Handler: " + node.handlerType());
        System.out.println("Status: " + node.status());
        System.out.println("Stage: " + node.stageId());
        System.out.println("Iteration: " + node.iterationId());
        System.out.println("Input refs: " + node.inputRefs());
        System.out.println("Output refs: " + node.outputRefs());
        System.out.println("Result: " + node.result());
        System.out.println("Started: " + node.startedAt());
        System.out.println("Finished: " + node.finishedAt());
    }

    private void inspectRun(Path workspaceRoot, WorkstreamPaths paths) throws Exception {
        var run = new RunMetadataReader().read(paths, runId);
        System.out.println("Run: " + run.runId());
        System.out.println("Status: " + run.status());
        System.out.println("Agent: " + run.agent());
        System.out.println("Session: " + run.session());
        System.out.println("Node: " + run.nodeId());
        System.out.println("Prompt template: " + run.promptTemplate());
        System.out.println("Exit code: " + run.exitCode());
        System.out.println("Duration(ms): " + run.durationMs());
        System.out.println("Failure: " + run.failureReason());
        System.out.println("Recoverable: " + run.recoverable());
        System.out.println("Prompt: " + run.promptPath());
        System.out.println("Output: " + run.outputPath());
        System.out.println("Stderr: " + run.stderrPath());
        System.out.println("Command: " + run.command());

        if (show != null && !show.isBlank()) {
            var path = switch (show) {
                case "prompt" -> workspaceRoot.resolve(run.promptPath());
                case "output" -> workspaceRoot.resolve(run.outputPath());
                case "stderr" -> workspaceRoot.resolve(run.stderrPath());
                default -> throw new IllegalArgumentException("--show must be prompt, output, or stderr");
            };
            System.out.println();
            System.out.println("----- " + show + " -----");
            if (Files.exists(path)) {
                System.out.print(Files.readString(path));
            } else {
                System.out.println("(file not found: " + path + ")");
            }
        }
    }
}
