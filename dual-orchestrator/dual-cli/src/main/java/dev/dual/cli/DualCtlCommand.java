package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "dualctl",
        mixinStandardHelpOptions = true,
        description = "Lifecycle orchestrator for dual-agent design loops.",
        subcommands = {
                StartCommand.class,
                StatusCommand.class,
                ResumeCommand.class,
                RetryCommand.class,
                ReviewCommand.class
        }
)
public final class DualCtlCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use a subcommand. Try: dualctl --help");
    }
}
