package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxProcessCleanupPolicyTest {
    @Test
    void selectsOnlyNewCodexSerenaProcessesForCleanup() {
        var knownBefore = Set.of(101L);

        assertFalse(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(101L, "uvx serena start-mcp-server --context codex"),
                knownBefore
        ));
        assertTrue(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(202L, "uvx --from git+https://github.com/oraios/serena serena start-mcp-server --project-from-cwd --context codex"),
                knownBefore
        ));
        assertTrue(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(203L, "python -m serena start-mcp-server --context codex"),
                knownBefore
        ));
        assertFalse(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(204L, "uvx serena start-mcp-server --context claude"),
                knownBefore
        ));
        assertFalse(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(205L, "pyright-langserver --stdio"),
                knownBefore
        ));
        assertFalse(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(206L, "/usr/local/bin/node /Users/me/.nvm/versions/node/v22/lib/node_modules/acpx/dist/cli.js __queue-owner"),
                knownBefore
        ));
        assertFalse(AcpxProcessCleanupPolicy.shouldCleanup(
                new AcpxProcessCleanupPolicy.ProcessInfo(101L, "/usr/local/bin/node /Users/me/.nvm/versions/node/v22/lib/node_modules/acpx/dist/cli.js __queue-owner"),
                knownBefore
        ));
    }
}
