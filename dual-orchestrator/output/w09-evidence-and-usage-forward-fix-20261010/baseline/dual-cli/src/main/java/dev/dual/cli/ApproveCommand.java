package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "approve", description = "Shortcut for review approve.", mixinStandardHelpOptions = true)
public final class ApproveCommand extends ReviewApproveCommand {
}
