package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import static dev.dual.adapter.acpx.AgentRunnerKind.ACPX_CLAUDE_SESSION;
import static dev.dual.adapter.acpx.AgentRunnerKind.ACPX_CODEX_SESSION;
import static dev.dual.adapter.acpx.AgentRunnerKind.NATIVE_CLAUDE_PRINT;
import static dev.dual.adapter.acpx.AgentRunnerKind.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRunnerKindTest {

    @Test
    void claudeContractDiscoveryIsNativePrint() {
        assertEquals(NATIVE_CLAUDE_PRINT, AgentRunnerKind.from("claude", "contract_discovery"));
    }

    @Test
    void claudeSessionProfilesAreAcpxClaudeSession() {
        assertEquals(ACPX_CLAUDE_SESSION, AgentRunnerKind.from("claude", "observe"));
        assertEquals(ACPX_CLAUDE_SESSION, AgentRunnerKind.from("claude", "review"));
        assertEquals(ACPX_CLAUDE_SESSION, AgentRunnerKind.from("claude", "implement"));
    }

    @Test
    void claudeWithoutProfileDefaultsToSession() {
        assertEquals(ACPX_CLAUDE_SESSION, AgentRunnerKind.from("claude", ""));
        assertEquals(ACPX_CLAUDE_SESSION, AgentRunnerKind.from("claude", null));
    }

    @Test
    void codexIsAcpxCodexSessionRegardlessOfProfile() {
        assertEquals(ACPX_CODEX_SESSION, AgentRunnerKind.from("codex", "implement"));
        assertEquals(ACPX_CODEX_SESSION, AgentRunnerKind.from("codex", "contract_discovery"));
    }

    @Test
    void unknownAgentIsUnknown() {
        assertEquals(UNKNOWN, AgentRunnerKind.from("other-agent", "contract_discovery"));
        assertEquals(UNKNOWN, AgentRunnerKind.from(null, "contract_discovery"));
    }

    @Test
    void unsupportedProfileIsUnknownNotException() {
        assertEquals(UNKNOWN, AgentRunnerKind.from("claude", "bogus-profile"));
    }

    @Test
    void wireNamesAreStableForBashAndJson() {
        assertEquals("native_claude_print", NATIVE_CLAUDE_PRINT.wire());
        assertEquals("acpx_claude_session", ACPX_CLAUDE_SESSION.wire());
        assertEquals("acpx_codex_session", ACPX_CODEX_SESSION.wire());
        assertEquals("unknown", UNKNOWN.wire());
    }
}
