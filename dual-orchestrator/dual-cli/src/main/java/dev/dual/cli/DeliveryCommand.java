package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "delivery",
        mixinStandardHelpOptions = true,
        description = "Prepare or create delivery artifacts.",
        subcommands = {
                DeliveryCommitCommand.class
        }
)
public final class DeliveryCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use delivery commit.");
    }
}
