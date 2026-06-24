package dev.dual.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ResumeCommandTest {

    @Test
    void doesNotExposeBareSessionNameOptions() {
        // P0: the dead --claude-session / --codex-session options (bare "claude-main" /
        // "codex-reviewer" defaults that were never wired to the runtime) must be gone,
        // so the CLI resume path cannot leak a bare, non-workstream-scoped session name.
        var spec = new CommandLine(new ResumeCommand()).getCommandSpec();
        var names = new HashSet<String>();
        for (var option : spec.options()) {
            names.addAll(Set.of(option.names()));
        }

        assertFalse(names.contains("--claude-session"), "bare --claude-session option must be removed");
        assertFalse(names.contains("--codex-session"), "bare --codex-session option must be removed");
    }
}
