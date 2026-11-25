package dev.dual.adapter.acpx;

/**
 * 一次 agent run 的 session 决策元数据，写入 run.json，供 benchmark 解释 token 变化。
 *
 * @param sessionName       实际传给 acpx 的 session 名（含 workstreamId/runId，永不裸名）
 * @param scope             {@link SessionScopePolicy.Scope#wire()}：workstream/stage/stage_iteration/run
 * @param segmentKey        人类可读的 segment 标识（如 ws:..  /stage:..  /iter:..）
 * @param reason            为何用此 scope（default/env/fallback，含非法 env 或缺失 stage/iter 的原因）
 * @param stage             语义 stage（NodeInstance.stageId）
 * @param semanticIteration 语义修复轮（NodeInstance.iterationId）
 */
record SessionMeta(
        String sessionName,
        String scope,
        String segmentKey,
        String reason,
        String stage,
        String semanticIteration
) {
    static SessionMeta from(SessionScopePolicy.Decision decision, String stageId, String iterationId) {
        return new SessionMeta(
                decision.sessionName(),
                decision.scope().wire(),
                decision.segmentKey(),
                decision.reason(),
                stageId,
                iterationId
        );
    }
}
