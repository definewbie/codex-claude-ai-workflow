package dev.dual.adapter.acpx;

/**
 * 集中的 acpx session 命名 + per-agent scope 解析（P1 A/B 能力）。
 *
 * <p>这是 session name 的唯一拼装点：handler / CLI / UI 都不应再自己拼字符串。</p>
 *
 * <p>关键约束：</p>
 * <ul>
 *     <li>默认 scope 是 {@link Scope#WORKSTREAM}，且其 session name 与 P1 前的实现
 *     <b>逐字节一致</b>（{@code base + "-" + safe(workstreamId)}），不配置任何 env 时行为不变。</li>
 *     <li>session name 恒包含 workstreamId（RUN scope 用 runId），<b>永不</b>出现裸 base 名
 *     （如 {@code claude-main}），杜绝跨 task 泄漏。</li>
 *     <li>{@code stage} 来自 workflow 的语义 {@code stageId}，{@code iteration} 来自语义
 *     {@code iterationId}（语义修复轮），不靠 node id 子串猜。</li>
 *     <li>stage / iteration 无法可靠获取时，<b>显式</b> fallback 到 workstream 并给出 reason，
 *     不静默乱拼。</li>
 *     <li>非法 env 值：fallback 到 workstream + 明确 reason（不静默变成 workstream）。</li>
 * </ul>
 */
public final class SessionScopePolicy {

    public enum Scope {
        WORKSTREAM("workstream"),
        STAGE("stage"),
        STAGE_ITERATION("stage_iteration"),
        RUN("run");

        private final String wire;

        Scope(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }

        static Scope fromWire(String value) {
            return switch (value == null ? "" : value.trim()) {
                case "workstream" -> WORKSTREAM;
                case "stage" -> STAGE;
                case "stage_iteration" -> STAGE_ITERATION;
                case "run" -> RUN;
                default -> throw new IllegalArgumentException("unsupported session scope: " + value);
            };
        }
    }

    /** 一次 session 命名决策：实际 name + 用的 scope + 人类可读 segment key + reason。 */
    public record Decision(String sessionName, Scope scope, String segmentKey, String reason) {
    }

    private static final String ENV_CLAUDE = "DUAL_SESSION_SCOPE_CLAUDE";
    private static final String ENV_CODEX = "DUAL_SESSION_SCOPE_CODEX";

    private SessionScopePolicy() {
    }

    /**
     * 按 agent 解析 session scope 并生成 session name（env 来自 {@link System#getenv}，无 node-config fallback）。
     */
    public static Decision resolve(
            String agent,
            String baseSessionName,
            String workstreamId,
            String stageId,
            String iterationId,
            String runId
    ) {
        return resolve(agent, baseSessionName, workstreamId, stageId, iterationId, runId, System::getenv, null);
    }

    /**
     * 同上，但 env 来源可注入（便于单测）。
     */
    public static Decision resolve(
            String agent,
            String baseSessionName,
            String workstreamId,
            String stageId,
            String iterationId,
            String runId,
            java.util.function.Function<String, String> envLookup
    ) {
        return resolve(agent, baseSessionName, workstreamId, stageId, iterationId, runId, envLookup, null);
    }

    /**
     * 同上，但允许传入 node-config 的 {@code session_scope}（legacy opt-in，workstream/run）作为
     * <b>env 之下</b>的 fallback：env 设了用 env，否则用 node-config，否则默认 workstream。
     * 这保证 P1 前 node-config {@code session_scope=run} 的既有行为不变。
     */
    public static Decision resolve(
            String agent,
            String baseSessionName,
            String workstreamId,
            String stageId,
            String iterationId,
            String runId,
            String nodeConfigScope
    ) {
        return resolve(agent, baseSessionName, workstreamId, stageId, iterationId, runId, System::getenv, nodeConfigScope);
    }

    public static Decision resolve(
            String agent,
            String baseSessionName,
            String workstreamId,
            String stageId,
            String iterationId,
            String runId,
            java.util.function.Function<String, String> envLookup,
            String nodeConfigScope
    ) {
        var envName = "codex".equals(agent) ? ENV_CODEX : ENV_CLAUDE;
        var raw = envLookup.apply(envName);
        Scope configured;
        String reason;
        if (raw != null && !raw.isBlank()) {
            try {
                configured = Scope.fromWire(raw);
                reason = "env:" + envName + "=" + configured.wire();
            } catch (IllegalArgumentException e) {
                configured = Scope.WORKSTREAM;
                reason = "invalid " + envName + "=" + raw.trim() + "; fallback:workstream";
            }
        } else if (nodeConfigScope != null && !nodeConfigScope.isBlank()) {
            // legacy node-config opt-in；非法值按旧逻辑抛错（明确错误，不静默）
            configured = Scope.fromWire(nodeConfigScope);
            reason = "node-config:session_scope=" + configured.wire();
        } else {
            configured = Scope.WORKSTREAM;
            reason = "default:workstream";
        }
        return build(baseSessionName, workstreamId, stageId, iterationId, runId, configured, reason);
    }

    private static Decision build(
            String base, String workstreamId, String stageId, String iterationId,
            String runId, Scope scope, String reason
    ) {
        var wsPart = safe(workstreamId);
        return switch (scope) {
            case RUN -> new Decision(base + "-" + wsPart + "-" + safe(runId), Scope.RUN,
                    "ws:" + workstreamId + "/run:" + runId, reason);
            case STAGE_ITERATION -> {
                if (isBlank(stageId) || isBlank(iterationId)) {
                    yield workstreamFallback(base, workstreamId,
                            "stage_iteration unavailable (stage=" + stageId + ", iter=" + iterationId + ")");
                }
                yield new Decision(base + "-" + wsPart + "-" + safe(stageId) + "-" + safe(iterationId),
                        Scope.STAGE_ITERATION,
                        "ws:" + workstreamId + "/stage:" + stageId + "/iter:" + iterationId,
                        reason);
            }
            case STAGE -> {
                if (isBlank(stageId)) {
                    yield workstreamFallback(base, workstreamId, "stage unavailable (stage=" + stageId + ")");
                }
                yield new Decision(base + "-" + wsPart + "-" + safe(stageId),
                        Scope.STAGE,
                        "ws:" + workstreamId + "/stage:" + stageId,
                        reason);
            }
            default -> new Decision(base + "-" + wsPart, Scope.WORKSTREAM, "ws:" + workstreamId, reason);
        };
    }

    private static Decision workstreamFallback(String base, String workstreamId, String why) {
        return new Decision(base + "-" + safe(workstreamId), Scope.WORKSTREAM,
                "ws:" + workstreamId, "fallback:" + why);
    }

    /**
     * 与 legacy safeSessionPart 逐字节一致，保证默认 workstream 行为不变。
     */
    static String safe(String value) {
        var safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9_.-]", "-");
        if (safe.isBlank()) {
            return "workstream";
        }
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
