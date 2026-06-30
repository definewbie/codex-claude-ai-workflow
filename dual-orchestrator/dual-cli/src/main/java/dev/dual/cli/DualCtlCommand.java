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
                DeliveryCommand.class,
                DoctorCommand.class,
                StatusCommand.class,
                SummaryCommand.class,
                TraceCommand.class,
                TimelineCommand.class,
                WatchCommand.class,
                EventsCommand.class,
                RunsCommand.class,
                InspectCommand.class,
                ResumeCommand.class,
                RunCommand.class,
                AutoRunCommand.class,
                WorkerCommand.class,
                ApproveCommand.class,
                ReviseCommand.class,
                CompleteCommand.class,
                RetryCommand.class,
                RecoveryCommand.class,
                SessionsReapCommand.class,
                CalibrationCommand.class,
                ProjectionsCommand.class,
                ReviewCommand.class,
                WorkflowCommand.class
        }
)
public final class DualCtlCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use a subcommand. Try: dualctl --help");
    }
}
