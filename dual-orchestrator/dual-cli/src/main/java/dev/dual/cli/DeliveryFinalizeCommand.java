package dev.dual.cli;

import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "finalize", description = "Verify and record the commit for an applied delivery candidate.", mixinStandardHelpOptions = true)
public final class DeliveryFinalizeCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Source worktree directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--commit", required = true, description = "Commit SHA created in the original repository.")
    String commit;

    @Parameters(index = "0")
    String workstreamId;

    private final DeliveryApplicationService delivery;

    public DeliveryFinalizeCommand() {
        this(new DeliveryApplicationService());
    }

    DeliveryFinalizeCommand(DeliveryApplicationService delivery) {
        this.delivery = delivery;
    }

    @Override
    public Integer call() throws Exception {
        var result = delivery.finalizeCommit(workdir, workstreamId, commit);
        System.out.println("Delivery: " + result.status());
        if (!result.reason().isBlank()) System.out.println("Reason: " + result.reason());
        return "COMMITTED".equals(result.status()) ? 0 : 1;
    }
}
