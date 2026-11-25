package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "worker",
        description = "Run local queued agent tasks.",
        subcommands = {
                WorkerRunOnceCommand.class
        },
        mixinStandardHelpOptions = true
)
public final class WorkerCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use worker run-once.");
    }
}
