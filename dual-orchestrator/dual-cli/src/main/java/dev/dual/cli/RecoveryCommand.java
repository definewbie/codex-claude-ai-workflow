package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "recovery",
        mixinStandardHelpOptions = true,
        description = "Resolve workflow recovery states.",
        subcommands = {
                RecoveryShowCommand.class,
                RecoveryAbortCommand.class,
                RecoveryStaleCommand.class,
                RecoveryReplayClassificationCommand.class,
                RecoveryCloseMistakenIterationCommand.class,
                RecoveryApproveWebSearchCommand.class
        }
)
public final class RecoveryCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use recovery show, recovery stale, recovery abort, recovery replay-classification, "
                + "recovery close-mistaken-iteration, or recovery approve-websearch.");
    }
}
