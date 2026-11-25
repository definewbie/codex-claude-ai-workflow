package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "complete", description = "Shortcut for review complete.", mixinStandardHelpOptions = true)
public final class CompleteCommand extends ReviewCompleteCommand {
}
