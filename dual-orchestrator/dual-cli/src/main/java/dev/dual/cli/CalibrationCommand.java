package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "calibration",
        mixinStandardHelpOptions = true,
        description = "Inspect and decide calibration candidates.",
        subcommands = {
                CalibrationListCommand.class,
                CalibrationShowCommand.class,
                CalibrationDecideCommand.class,
                CalibrationPlanCommand.class
        }
)
public final class CalibrationCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Use calibration list, calibration show, calibration decide, or calibration plan.");
    }
}
