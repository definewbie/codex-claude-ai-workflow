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
import java.util.ArrayList;
import java.util.List;
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

    @Option(names = "--show", description = "Show prompt, output, stderr, or context.")
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
        try (var snapshot = CliLedgerFactory.create().readSession(paths)) {
        var ledger = snapshot.ledger();
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

        if ("context".equals(show)) {
            System.out.println();
            System.out.println("----- context -----");
            showContext(paths, node.inputRefs(), node.outputRefs());
        } else if (show != null && !show.isBlank()) {
            throw new IllegalArgumentException("--show for node inspect must be context");
        }
        }
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
        System.out.println("Prompt metadata: " + run.promptMetadataPath());
        System.out.println("Output metadata: " + run.outputMetadataPath());
        System.out.println("Stderr metadata: " + run.stderrMetadataPath());
        System.out.println("Command: " + run.command());

        if (show != null && !show.isBlank()) {
            var ref = switch (show) {
                case "prompt" -> run.promptPath();
                case "output" -> run.outputPath();
                case "stderr" -> run.stderrPath();
                case "prompt-meta" -> run.promptMetadataPath();
                case "output-meta" -> run.outputMetadataPath();
                case "stderr-meta" -> run.stderrMetadataPath();
                default -> throw new IllegalArgumentException("--show for --run must be prompt, output, stderr, prompt-meta, output-meta, or stderr-meta");
            };
            System.out.println();
            System.out.println("----- " + show + " -----");
            if (ref == null || ref.isBlank()) {
                System.out.println("(file not available)");
                return;
            }
            var path = workspaceRoot.resolve(ref);
            if (Files.exists(path)) {
                System.out.print(Files.readString(path));
            } else {
                System.out.println("(file not found: " + path + ")");
            }
        }
    }

    private void showContext(WorkstreamPaths paths, List<String> inputRefs, List<String> outputRefs)
            throws Exception {
        var refs = new ArrayList<String>();
        refs.addAll(outputRefs);
        refs.addAll(inputRefs);
        var contextRefs = refs.stream()
                .filter(ref -> ref.contains("/context/") && ref.endsWith(".md"))
                .distinct()
                .toList();
        if (contextRefs.isEmpty()) {
            System.out.println("(no context package ref on this node)");
            return;
        }
        for (String contextRef : contextRefs) {
            var path = paths.workspaceRoot().resolve(contextRef).normalize();
            System.out.println("Path: " + contextRef);
            if (Files.exists(path)) {
                var content = Files.readString(path);
                System.out.print(content);
                if (!content.endsWith(System.lineSeparator())) {
                    System.out.println();
                }
            } else {
                System.out.println("(file not found: " + path + ")");
            }
        }
    }
}
