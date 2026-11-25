package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.ExternalVerificationEvidence;
import org.springframework.stereotype.Component;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "prepare-evidence", mixinStandardHelpOptions = true,
        description = "Record the current source baseline before external verification; leave the gate open.")
public final class ReviewPrepareEvidenceCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Parameters(index = "0") String workstreamId;
    @Override public Integer call() throws Exception {
        var paths = new WorkstreamPaths(workdir.toAbsolutePath().normalize(), new DualId(workstreamId));
        var reference = ExternalVerificationEvidence.prepare(CliLedgerFactory.create(), paths);
        System.out.println("Preparation ref: " + reference.ref());
        System.out.println("Preparation SHA256: " + reference.sha256());
        System.out.println("Gate remains open. Run verification without changing product source; then submit explicit material files.");
        return 0;
    }
}
