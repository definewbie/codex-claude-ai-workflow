package dev.dual.adapter.acpx;

/**
 * The external runner path an acpx agent node actually uses, derived from the
 * node's agent + permission_profile. This is the execution-boundary semantic a
 * preflight probe must match: a native {@code claude --print} probe proves only
 * the native runner; an acpx session probe proves the session runner.
 *
 * <p>dual does not own Claude Code / Codex model selection; it owns matching the
 * probe to the actual runner and attributing usage from whatever the runner
 * returns. The map from (agent, permission_profile) to runner kind lives here
 * (delegating to {@link AcpxPermissionProfile#requiresNativeClaudeRunner()}),
 * not in CLI if-else or bash.
 */
public enum AgentRunnerKind {
    NATIVE_CLAUDE_PRINT("native_claude_print"),
    ACPX_CLAUDE_SESSION("acpx_claude_session"),
    ACPX_CODEX_SESSION("acpx_codex_session"),
    UNKNOWN("unknown");

    private final String wire;

    AgentRunnerKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /**
     * Resolve the runner kind from a node's agent + permission_profile.
     *
     * <p>{@code codex} → acpx codex session; {@code claude} + a native-required
     * profile ({@code contract_discovery}) → native claude print; {@code claude}
     * + a session profile ({@code observe}/{@code review}/{@code implement}/blank)
     * → acpx claude session; anything unresolved (unknown agent, unsupported
     * profile) → {@link #UNKNOWN}. Never throws.
     */
    public static AgentRunnerKind from(String agent, String permissionProfile) {
        if ("codex".equals(agent)) {
            return ACPX_CODEX_SESSION;
        }
        if ("claude".equals(agent)) {
            try {
                var profile = AcpxPermissionProfile.fromName(permissionProfile, false);
                return profile.requiresNativeClaudeRunner() ? NATIVE_CLAUDE_PRINT : ACPX_CLAUDE_SESSION;
            } catch (RuntimeException ignored) {
                return UNKNOWN;
            }
        }
        return UNKNOWN;
    }
}
