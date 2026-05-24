package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "workflow",
        mixinStandardHelpOptions = true,
        description = "Inspect and validate workflow definition files.",
        subcommands = {
                WorkflowValidateCommand.class,
                WorkflowPrintDefaultCommand.class
        }
)
public final class WorkflowCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use workflow validate or workflow print-default.");
    }
}
