package dev.dual.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryCommandTest {
    @Test
    void exposesBoundedClassificationReplayCommand() {
        var commandLine = new CommandLine(new RecoveryCommand());

        assertTrue(commandLine.getSubcommands().containsKey("replay-classification"));
    }
}
