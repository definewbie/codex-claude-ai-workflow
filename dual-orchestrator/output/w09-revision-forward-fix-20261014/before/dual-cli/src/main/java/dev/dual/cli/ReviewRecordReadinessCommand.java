package dev.dual.cli;

import dev.dual.runtime.handler.VerificationReadiness;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "record-readiness", mixinStandardHelpOptions = true,
        description = "Record verification availability without approving or running the task.")
public final class ReviewRecordReadinessCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Option(names = "--subject", required = true) String subject;
    @Option(names = "--request-id", required = true) String requestId;
    @Option(names = "--file", required = true) Path file;
    @Parameters(index = "0") String workstreamId;

    @Override public Integer call() throws Exception {
        var request = VerificationReadiness.readRequest(file);
        if (!subject.equals(request.subject()) || !requestId.equals(request.requestId()))
            throw new IllegalArgumentException("request file must match --subject and --request-id");
        var ref = VerificationReadiness.record(CliLedgerFactory.create(), workdir.toAbsolutePath().normalize(), workstreamId, request);
        System.out.println("Readiness saved: " + ref.ref());
        System.out.println("No review decision or execution was performed.");
        return 0;
    }
}
