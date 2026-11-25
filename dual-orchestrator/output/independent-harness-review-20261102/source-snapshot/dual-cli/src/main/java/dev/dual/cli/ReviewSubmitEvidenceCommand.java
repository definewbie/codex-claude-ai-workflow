package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.ExternalVerificationEvidence;
import org.springframework.stereotype.Component;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "submit-evidence", mixinStandardHelpOptions = true,
        description = "Bind external materials to a prepared source and continue to self-test, not directly to acceptance.")
public final class ReviewSubmitEvidenceCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Option(names = "--preparation-ref", required = true) String preparation;
    @Option(names = "--preparation-sha256", required = true) String sha;
    @Option(names = "--request-id", required = true) String requestId;
    @Option(names = "--file", required = true) List<Path> files;
    @Option(names = "--command", required = true) String command;
    @Option(names = "--environment", required = true) String environment;
    @Option(names = "--started-at", required = true, description = "Operator-declared ISO instant, not machine attestation.") String started;
    @Option(names = "--finished-at", required = true) String finished;
    @Option(names = "--exit-code", required = true) int exitCode;
    @Option(names = "--purpose", required = true) String purpose;
    @Option(names = "--note", required = true) String note;
    @Parameters(index = "0") String workstreamId;
    @Override public Integer call() throws Exception {
        var paths = new WorkstreamPaths(workdir.toAbsolutePath().normalize(), new DualId(workstreamId));
        var result = ExternalVerificationEvidence.submit(CliLedgerFactory.create(), paths,
                new ExternalVerificationEvidence.Reference(preparation, sha), requestId, files,
                new ExternalVerificationEvidence.Declaration(command, environment, Instant.parse(started),
                        Instant.parse(finished), exitCode, purpose), note);
        System.out.println("Submitted material: " + result.manifestRef());
        System.out.println("SHA256: " + result.manifestSha256());
        System.out.println("Submission is not a verification result or acceptance decision.");
        System.out.println("Continue: dualctl run --workdir " + paths.workspaceRoot() + " " + workstreamId);
        return 0;
    }
}
