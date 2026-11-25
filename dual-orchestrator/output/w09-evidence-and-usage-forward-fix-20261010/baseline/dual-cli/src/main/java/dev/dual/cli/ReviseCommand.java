package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(name = "revise", description = "Shortcut for review revise.", mixinStandardHelpOptions = true)
public final class ReviseCommand extends ReviewReviseCommand {
}
