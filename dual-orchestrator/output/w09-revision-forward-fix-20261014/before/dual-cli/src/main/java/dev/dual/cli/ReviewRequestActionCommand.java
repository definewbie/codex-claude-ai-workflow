package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.HumanActionRequest;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "request-action", mixinStandardHelpOptions = true,
        description = "Record an unmet action need without resolving the gate or running an agent.")
public final class ReviewRequestActionCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Option(names = "--gate", required = true) String gate;
    @Option(names = "--request-id", required = true) String requestId;
    @Option(names = "--expected-result", required = true) String expectedResult;
    @Option(names = "--reason", required = true) String reason;
    @Parameters(index = "0") String workstreamId;

    @Override public Integer call() throws Exception {
        var paths = new WorkstreamPaths(workdir.toAbsolutePath().normalize(), new DualId(workstreamId));
        var ledger = CliLedgerFactory.create();
        if (!ledger.loadCurrent(paths).activeNodeInstanceIds().equals(java.util.List.of(gate))) {
            throw new IllegalArgumentException("--gate must identify the current gate occurrence");
        }
        var source = ledger.loadNode(paths, gate);
        ledger.requestAction(paths, new HumanActionRequest(requestId, gate, source.inputRefs(), expectedResult, reason));
        System.out.println("Action need recorded. Gate remains waiting; no execution was authorized.");
        return 0;
    }
}
