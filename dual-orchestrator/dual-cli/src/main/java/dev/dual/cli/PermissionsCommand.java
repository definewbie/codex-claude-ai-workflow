package dev.dual.cli;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.NativePermissionRequests;
import org.springframework.stereotype.Component;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "permissions", description = "Show native asks or save an exact once choice; this is not business approval.", mixinStandardHelpOptions = true)
public final class PermissionsCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Parameters(index = "0") String workstreamId;
    @Option(names = "--execution") String execution;
    @Option(names = "--request") String requestKey;
    @Option(names = "--option", description = "Exact offered once optionId; saves a decision, never directly sends it.") String option;
    @Option(names = "--cancel", description = "Close new submissions for the current execution; stop remains unconfirmed.") boolean cancel;
    @Option(names = "--note") String note = "";
    @Override public Integer call() throws Exception {
        var root = workdir.toAbsolutePath().normalize();
        var ledger = CliLedgerFactory.create(); var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        Object result;
        if (cancel && (option != null || requestKey != null)) throw new IllegalArgumentException("cancel cannot include a per-request choice");
        if (cancel) {
            if (execution == null) throw new IllegalArgumentException("cancel requires exact execution id");
            result = NativePermissionRequests.cancel(ledger, paths, execution, note);
        } else if (option != null) {
            if (execution == null || requestKey == null) throw new IllegalArgumentException("choice requires exact execution and request ids");
            result = NativePermissionRequests.decide(ledger, paths, execution, requestKey, option, note);
        } else {
            if (execution != null || requestKey != null || !note.isBlank()) throw new IllegalArgumentException("show does not accept decision fields");
            result = NativePermissionRequests.view(ledger, paths);
        }
        System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
        return 0;
    }
}
