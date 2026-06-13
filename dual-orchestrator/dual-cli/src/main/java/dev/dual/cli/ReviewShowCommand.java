package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.ledger.file.WorkflowFileLedger;
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
@Command(name = "show", description = "Show the current human review packet.", mixinStandardHelpOptions = true)
public final class ReviewShowCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var ledger = CliLedgerFactory.create();
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            System.err.println("No active node for workstream: " + workstreamId);
            return 1;
        }

        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.kind() != NodeKind.GATE) {
            System.err.println("Active node is not a human gate: " + node.nodeDefinitionId());
            return 1;
        }

        var packetRef = ".agent/workstreams/" + workstreamId
                + "/reviews/" + node.nodeInstanceId() + ".md";
        var packetPath = workspaceRoot.resolve(packetRef);
        if (!Files.exists(packetPath)) {
            System.err.println("Review packet not found: " + packetRef);
            System.err.println("Run or resume the workflow until it opens the current gate.");
            return 1;
        }

        System.out.println("Current Review Gate");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Gate: " + node.nodeDefinitionId());
        System.out.println("Packet: " + packetRef);
        System.out.println();
        System.out.println(Files.readString(packetPath, StandardCharsets.UTF_8));
        return 0;
    }
}
