package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "review",
        mixinStandardHelpOptions = true,
        description = "Apply human review decisions.",
        subcommands = {
                ReviewApproveCommand.class,
                ReviewReviseCommand.class
        }
)
public final class ReviewCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use review approve or review revise.");
    }
}
