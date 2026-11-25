package dev.dual.cli;

import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "merge", description = "Apply the accepted delivery candidate to the original repo.", mixinStandardHelpOptions = true)
public final class DeliveryMergeCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Source worktree directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--target", description = "Original repository. Must match the workstream ledger when provided.")
    Path target;

    @Parameters(index = "0")
    String workstreamId;

    private final DeliveryApplicationService delivery;

    public DeliveryMergeCommand() {
        this(new DeliveryApplicationService(CliLedgerFactory.create()));
    }

    DeliveryMergeCommand(DeliveryApplicationService delivery) {
        this.delivery = delivery;
    }

    @Override
    public Integer call() throws Exception {
        var result = delivery.apply(workdir, workstreamId, target);
        System.out.println("Delivery: " + result.status());
        if (!result.candidateRef().isBlank()) System.out.println("Candidate: " + result.candidateRef());
        if (!result.reason().isBlank()) System.out.println("Reason: " + result.reason());
        if (result.currentActions().contains("COMMIT_EXTERNALLY")) {
            System.out.println("Review and commit the staged target diff, then run delivery finalize --commit <sha>.");
            return 0;
        }
        System.out.println("Next action: " + result.nextAction());
        result.nextCommands().forEach(System.out::println);
        return "COMMITTED".equals(result.status()) || result.canFinalize() ? 0 : 1;
    }
}
