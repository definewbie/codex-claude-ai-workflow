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
                      "command": "npx -y %s"
                    }
                  }
                }
                """.formatted(DoctorCommand.expectedCodexAcpPackage()));

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
    void acceptsExactPinnedClaudeAcpPackage() {
        var result = DoctorCommand.evaluateAdapterPin(
                """
                        {
                          "agents": {
                            "claude": {
                              "command": "npx -y @agentclientprotocol/claude-agent-acp@0.46.0"
                            }
                          }
                        }
                        """,
                "claude",
                "claude-agent-acp",
                DoctorCommand.EXPECTED_CLAUDE_ACP_PACKAGE
        );

        assertEquals(DoctorCommand.PinLevel.OK, result.level());
    }

    @Test
    void acceptsClaudeEnvBridgeWrapperWhenClaudeSettingsUseThirdPartyEndpoint() {
        var result = DoctorCommand.evaluateClaudeEnvironmentBridge(
                """
                        {
                          "agents": {
                            "claude": {
                              "command": "/repo/scripts/acpx-claude-agent-env.sh npx -y @agentclientprotocol/claude-agent-acp@0.46.0"
                            }
                          }
                        }
                        """,
                """
                        {
                          "env": {
                            "ANTHROPIC_BASE_URL": "https://example.test",
                            "ANTHROPIC_AUTH_TOKEN": "secret"
                          }
                        }
                        """,
                java.util.Map.of()
        );

        assertEquals(DoctorCommand.PinLevel.OK, result.level());
        assertTrue(result.message().contains("settings.json env"));
    }

    @Test
    void rejectsMissingClaudeEnvBridgeWhenClaudeSettingsUseThirdPartyEndpoint() {
        var result = DoctorCommand.evaluateClaudeEnvironmentBridge(
                """
                        {
                          "agents": {
                            "claude": {
                              "command": "npx -y @agentclientprotocol/claude-agent-acp@0.46.0"
                            }
                          }
                        }
                        """,
                """
                        {
                          "env": {
                            "ANTHROPIC_BASE_URL": "https://example.test",
                            "ANTHROPIC_AUTH_TOKEN": "secret"
                          }
                        }
                        """,
                java.util.Map.of()
        );

        assertEquals(DoctorCommand.PinLevel.FAIL, result.level());
        assertTrue(result.message().contains("configure-acpx.sh"));
    }

    @Test
    void acceptsProcessAnthropicEnvWithoutWrapper() {
        var result = DoctorCommand.evaluateClaudeEnvironmentBridge(
                """
                        {
                          "agents": {
                            "claude": {
                              "command": "npx -y @agentclientprotocol/claude-agent-acp@0.46.0"
                            }
                          }
                        }
                        """,
                """
                        {
                          "env": {
                            "ANTHROPIC_BASE_URL": "https://example.test",
                            "ANTHROPIC_AUTH_TOKEN": "secret"
                          }
                        }
                        """,
                java.util.Map.of(
                        "ANTHROPIC_BASE_URL", "https://example.test",
                        "ANTHROPIC_AUTH_TOKEN", "secret"
                )
        );

        assertEquals(DoctorCommand.PinLevel.OK, result.level());
        assertTrue(result.message().contains("current process environment"));
    }

    @Test
    void rejectsMissingClaudeAdapterCommand() {
        var result = DoctorCommand.evaluateAdapterPin(
                """
                        {
                          "agents": {
                            "claude": {}
                          }
                        }
                        """,
                "claude",
                "claude-agent-acp",
                DoctorCommand.EXPECTED_CLAUDE_ACP_PACKAGE
        );

        assertEquals(DoctorCommand.PinLevel.FAIL, result.level());
        assertTrue(result.message().contains("missing agents.claude.command"));
    }

    @Test
    void rejectsFloatingCodexAcpPackage() {
        var result = DoctorCommand.evaluateCodexAdapterPin("""
                {
                  "agents": {
                    "codex": {
                      "command": "npx -y @zed-industries/codex-acp@^0.16.0"
                    }
                  }
                }
                """);

        assertEquals(DoctorCommand.PinLevel.FAIL, result.level());
        assertTrue(result.message().contains(DoctorCommand.expectedCodexAcpPackage()));
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
