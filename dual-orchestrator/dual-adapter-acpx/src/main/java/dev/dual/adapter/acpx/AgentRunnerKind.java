package dev.dual.adapter.acpx;

/**
 * The external runner path an acpx agent node actually uses, derived from the
 * node's agent + permission_profile. This is the execution-boundary semantic a
 * preflight probe must match: a native {@code claude --print} probe proves only
 * the runner. The workflow runtime uses acpx sessions for Claude and Codex;
 * native Claude is not a workflow runner kind.
 *
 * <p>dual does not own Claude Code / Codex model selection; it owns matching the
 * probe to the actual runner and attributing usage from whatever the runner
 * returns. The map from (agent, permission_profile) to runner kind lives here
 * not in CLI if-else or bash.
 */
public enum AgentRunnerKind {
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
 * <p>{@code codex} → acpx codex session; {@code claude} + a supported profile
 * ({@code contract_discovery}/{@code observe}/{@code review}/{@code implement}/blank)
 * → acpx claude session; anything unresolved (unknown agent, unsupported
 * profile) → {@link #UNKNOWN}. Never throws.
     */
    public static AgentRunnerKind from(String agent, String permissionProfile) {
        if ("codex".equals(agent)) {
            return ACPX_CODEX_SESSION;
        }
        if ("claude".equals(agent)) {
            try {
                AcpxPermissionProfile.fromName(permissionProfile, false);
                return ACPX_CLAUDE_SESSION;
            } catch (RuntimeException ignored) {
                return UNKNOWN;
            }
        }
        return UNKNOWN;
    }
}
