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
                """.formatted("@agentclientprotocol/codex-acp@1.10.0"));

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
    void rejectsLegacyEmbeddedCodexAndKeepsInstallerPinAligned() throws Exception {
        var result = DoctorCommand.evaluateCodexAdapterPin("""
                {"agents":{"codex":{"command":"npx -y @zed-industries/codex-acp-darwin-arm64@0.16.0"}}}
                """);
        assertEquals(DoctorCommand.PinLevel.FAIL, result.level());
        var script = java.nio.file.Files.readString(java.nio.file.Path.of("../scripts/configure-acpx.sh"));
        assertTrue(script.contains("npx -y " + DoctorCommand.expectedCodexAcpPackage()));
        assertTrue(!script.contains("--model") && !script.contains("CODEX_HOME="));
    }

    @Test
    void acceptsExactPinnedClaudeAcpPackage() {
        var result = DoctorCommand.evaluateAdapterPin(
                """
                        {
                          "agents": {
                            "claude": {
                              "command": "npx -y @agentclientprotocol/claude-agent-acp@0.70.0"
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
                              "command": "/repo/scripts/acpx-claude-agent-env.sh npx -y @agentclientprotocol/claude-agent-acp@0.70.0"
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
                              "command": "npx -y @agentclientprotocol/claude-agent-acp@0.70.0"
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
                              "command": "npx -y @agentclientprotocol/claude-agent-acp@0.70.0"
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

    @Test
    void acceptsExactPatchedAcpxBuildIdentity() {
        var result = DoctorCommand.evaluateAcpxBuildIdentity("""
                {
                  "upstream_commit": "%s",
                  "patch_sha256": "%s",
                  "policy_schema": "%s"
                }
                """.formatted(
                DoctorCommand.EXPECTED_ACPX_UPSTREAM_COMMIT,
                DoctorCommand.EXPECTED_ACPX_PATCH_SHA256,
                DoctorCommand.EXPECTED_ACPX_POLICY_SCHEMA));

        assertEquals(DoctorCommand.PinLevel.OK, result.level());
    }

    @Test
    void rejectsOfficialOrMismatchedAcpxBuildIdentity() {
        var missing = DoctorCommand.evaluateAcpxBuildIdentity("{}");
        var wrongPatch = DoctorCommand.evaluateAcpxBuildIdentity("""
                {
                  "upstream_commit": "%s",
                  "patch_sha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                  "policy_schema": "%s"
                }
                """.formatted(DoctorCommand.EXPECTED_ACPX_UPSTREAM_COMMIT,
                DoctorCommand.EXPECTED_ACPX_POLICY_SCHEMA));

        assertEquals(DoctorCommand.PinLevel.FAIL, missing.level());
        assertEquals(DoctorCommand.PinLevel.FAIL, wrongPatch.level());
        assertTrue(wrongPatch.message().contains("NETWORK_AUTHORITY_NOT_CONFIGURED"));
    }
}
