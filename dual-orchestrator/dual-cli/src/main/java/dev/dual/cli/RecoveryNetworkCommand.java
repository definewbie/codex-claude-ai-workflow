package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.AtomicJsonWriter;
import dev.dual.ledger.file.ExecutionNetworkGrantStore;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "network", mixinStandardHelpOptions = true,
        description = "Show, approve or cancel exact domains for one Claude execution. Does not resolve gates or run tasks.")
public final class RecoveryNetworkCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Parameters(index = "0") String workstreamId;
    @Option(names = "--target", description = "Target node definition from the network view.") String target;
    @Option(names = "--domains", split = ",", description = "Exact DNS names. Grants domain access, not HTTP-method or read-only restrictions.") List<String> domains;
    @Option(names = "--note") String note;
    @Option(names = "--cancel") boolean cancel;

    @Override public Integer call() throws Exception {
        var paths = new WorkstreamPaths(workdir.toRealPath(), new DualId(workstreamId));
        var store = new ExecutionNetworkGrantStore();
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "network authority"))) {
            if (cancel) {
                if (target != null || domains != null || note != null) throw new IllegalArgumentException("cancel cannot include approval options");
                store.cancel(paths);
            } else if (target != null || domains != null || note != null) {
                if (target == null || domains == null || note == null) throw new IllegalArgumentException("approval requires --target, --domains and --note");
                store.approve(paths, target, domains, note);
            }
            System.out.println(new AtomicJsonWriter().objectMapper().writeValueAsString(store.view(paths)));
        }
        return 0;
    }
}
