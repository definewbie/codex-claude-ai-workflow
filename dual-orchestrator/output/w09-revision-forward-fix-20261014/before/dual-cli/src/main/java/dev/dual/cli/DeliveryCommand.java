package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "delivery",
        mixinStandardHelpOptions = true,
        description = "Apply and finalize an accepted delivery candidate.",
        subcommands = {
                DeliveryMergeCommand.class,
                DeliveryFinalizeCommand.class,
                DeliveryResolveCommand.class
        }
)
public final class DeliveryCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use delivery merge, delivery finalize, or delivery resolve.");
    }
}
