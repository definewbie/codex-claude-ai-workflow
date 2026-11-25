package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "projections",
        description = "Refresh or inspect read-model projections.",
        mixinStandardHelpOptions = true,
        subcommands = {
                ProjectionsRefreshCommand.class
        }
)
public final class ProjectionsCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use projections refresh. Try: dualctl projections --help");
    }
}
