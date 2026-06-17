package dev.dual.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorCommandTest {
    @Test
    void acceptsExactPinnedCodexAcpPackage() {
        var result = DoctorCommand.evaluateCodexAdapterPin("""
                {
                  "agents": {
                    "codex": {
                      "command": "npx -y @zed-industries/codex-acp@0.12.0"
                    }
                  }
                }
                """);

        assertEquals(DoctorCommand.PinLevel.OK, result.level());
    }

    @Test
    void rejectsMissingCodexAdapterCommand() {
        var result = DoctorCommand.evaluateCodexAdapterPin("""
                {
                  "agents": {
                    "codex": {}
                  }
                }
                """);

        assertEquals(DoctorCommand.PinLevel.FAIL, result.level());
        assertTrue(result.message().contains("missing agents.codex.command"));
    }

    @Test
    void rejectsFloatingCodexAcpPackage() {
        var result = DoctorCommand.evaluateCodexAdapterPin("""
                {
                  "agents": {
                    "codex": {
                      "command": "npx -y @zed-industries/codex-acp@^0.12.0"
                    }
                  }
                }
                """);

        assertEquals(DoctorCommand.PinLevel.FAIL, result.level());
        assertTrue(result.message().contains("@zed-industries/codex-acp@0.12.0"));
    }

    @Test
    void warnsForLocalCodexAcpBinaryBecauseItDoesNotPullLatest() {
        var result = DoctorCommand.evaluateCodexAdapterPin("""
                {
                  "agents": {
                    "codex": {
                      "command": "/Users/me/.npm/_npx/cache/node_modules/.bin/codex-acp"
                    }
                  }
                }
                """);

        assertEquals(DoctorCommand.PinLevel.WARN, result.level());
        assertTrue(result.message().contains("local codex-acp command"));
    }
}
