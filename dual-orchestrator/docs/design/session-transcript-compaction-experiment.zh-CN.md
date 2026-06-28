# Session Transcript Compaction 实验设计

## 目标

降低 workstream 级 acpx session transcript 带来的 cachedRead/token 体量和执行延迟风险，同时不降低任务准确性、不增加重复探索。

本设计只定义实验，不切换默认 `session_scope`，不引入 RAG，不改变 workflow 语义。

## 背景判断

当前 workstream scoped session 会让同一个 workstream 内的 Claude/Codex run 续接同一段会话历史。provider 会把大量历史前缀作为 `cachedReadTokens` 返回，因此账面 token 量很大，尤其集中在 `implement.claude_implement`、`implement.claude_self_test_report`、`stage.claude_handoff`。

此前直接缩短 session 的 A/B 结果说明：只切短 session 会消除 Codex `contextUsed` 膨胀，但会让 agent 丢失隐式上下文，导致重新探索、Read/toolCalls 上升，成本不一定下降。因此 compaction 的关键不是“新开 session”，而是“新 session 必须带 orchestrator 管理的 typed memory seed”。

## Source Of Truth 边界

- acpx session transcript：Execution 副产物，只能作为运行上下文缓存，不能作为长期记忆 SoT。
- ledger / run artifacts：发生过什么的 SoT。
- WorkstreamMemory：agent 应该跨 run 记住什么的 typed semantic SoT。
- AgentHandoff / ReviewEvidenceBrief：从 WorkstreamMemory 派生的 projection，用于 seed 新 session。
- tokenAttribution：观测 read model，只用于评估实验，不参与路由。

## 实验组

### A. Baseline

保持当前默认：`claude=workstream`，`codex=workstream`。

用途：作为成本、质量、toolCalls、duration 的基线。

### B. Implement Boundary Compaction

在进入 implement 阶段前，关闭或切换到新的 Claude session，并把以下 typed seed 注入首轮 prompt：

- task contract 的 scope / non-goals / acceptance criteria
- WorkstreamMemory 中的 trusted read summaries
- prior decisions / prior verdicts / open issues
- 当前 git diff / changed files
- 明确的 read reuse contract

适用节点：

- `implement.claude_implement`
- `implement.claude_self_test_report`
- `stage.claude_handoff`

不适用 intake/design/plan 的首轮探索，因为这些阶段本来承担发现和建模。

### C. Codex Incremental Review Session

Codex review 每次使用 run scoped 或 compacted session，但必须以 ReviewEvidenceBrief 作为 seed：

- 上轮 verdict 和 unresolved must-fix
- 本轮 diff / changed files
- acceptance criteria
- 已可信的 read summaries
- 本轮要求：增量审计，不全量重审；需要重读时写 `reread_reasons`

适用节点：

- product review
- architecture review
- plan review
- acceptance verifier

## 实验 Flag 建议

新增显式实验开关，不改变默认值：

- `DUAL_SESSION_COMPACTION_EXPERIMENT=off|implement_boundary|codex_incremental|both|provider_cache_aware`
- `DUAL_SESSION_COMPACTION_MAX_SEED_TOKENS=<n>`
- `DUAL_SESSION_COMPACTION_STABILITY_CACHED_READ_TOKENS=<n>`：hard stability threshold，默认 `1000000`。
- `DUAL_SESSION_COMPACTION_IMPLEMENT_STABILITY_CACHED_READ_TOKENS=<n>`：Claude implement family soft stability threshold，默认 `500000`。

`provider_cache_aware` 有两条独立决策轴：

- 经济轴：provider/model pricing 可用时，按 cache hit 与 cache miss 的价格比决定保留 session 还是 compaction。
- 稳定性轴：无论 pricing 是否可用，只要当前 agent 的 cachedRead 达到 hard threshold，或 Claude implement family 达到 soft threshold，就允许使用 typed seed 创建 compact session。

稳定性判断优先读取 `tokenMetrics.byAgent[agent].cachedReadTokens`。只有旧 metrics 缺少 agent 聚合时才回退到 workstream total，避免其它 agent 的 cachedRead 推高当前 agent 的 compaction 决策。

阈值命中只表示当前 run 进入 compaction 候选；实际创建 compact session 仍要求该 node family 有明确 seed strategy。当前候选族是 Claude implement/self-test/handoff 与 Codex review。非候选节点会保留原 session，并在 run metadata 记录 `provider_cache_aware / not_applicable_for_node`。

`pricing_unknown` / `subscription_unpriced` 只影响美元成本估算和经济型自动压缩，不阻止稳定性压缩。GLM 包月场景属于 token-only 稳定性治理：不计算美元收益，但仍控制 transcript/cachedRead 体量。

### Verification Gate 选择

`provider_cache_aware` 是策略决策模式，不是确定性 compaction 验证模式。它允许输出 `measure_only`、`preserve_session`、`not_applicable_for_node`，因此不能用于证明“本 case 一定会启用 compaction”。

如果本轮真实 case 的目标是验证 compact session 本身是否可用、`seedKind=agent_handoff` 是否足以保持质量、以及 implement token 峰值是否下降，必须使用确定性模式：

```bash
DUAL_SESSION_COMPACTION_EXPERIMENT=implement_boundary
```

这时 `implement.claude_implement` / `implement.claude_self_test_report` / `stage.claude_handoff` 必须创建 compact session；若 run metadata 中 `sessionCompactionEnabled=false`、`seedKind` 为空或 reason 包含 `measure_only`，本轮复测直接判定为 `INVALID`，不能继续用 acceptance 结果补偿。

`provider_cache_aware` 的验证目标只能是策略是否正确选择保留或压缩 session；它的 PASS 条件必须包含当前 pricing/cache/stability 输入，而不是固定要求 compaction 生效。

实现时必须记录每个 run 的：

- `sessionCompactionMode`
- `sessionSeedManifestPath`
- seed section token breakdown
- 是否由 compaction 创建新 session

## 验收指标

主要指标：

- `cachedReadTokens` 下降，尤其 implement/self_test/stage_handoff 节点。
- `inputTokens + outputTokens` 不上升。
- `toolCalls/readCount` 不上升。
- `durationMs` 不上升，最好下降。
- `acceptance/revision/repair loop` 不退化。

次要指标：

- `promptTokens` 不因 seed 膨胀超过 baseline 10%。
- `repeatedReadFiles` 不上升。
- `rereadReasonStatus=missing` 不上升。

止损条件：

- 任一组出现额外 repair loop。
- toolCalls 或 readCount 上升超过 20%。
- non-cached input/output 上升超过 15%。
- agent 因 seed 不足产生明显 scope 漂移。

## 预期收益

如果 seed 足够表达长期记忆，B 组应降低 implement/self-test/stage 的 cachedRead 峰值，同时不触发重新探索。C 组应降低 Codex context snapshot 膨胀，并保持增量审计质量。

如果 cachedRead 下降但 input/output 或 toolCalls 上升，说明 seed 不足，不能切默认。

## 风险

- typed memory 不完整，agent 重新探索。
- seed 过大，promptTokens 增加抵消收益。
- ReviewEvidenceBrief 过窄，Codex 漏审。
- provider cachedRead 计费很低，token 量下降不等于美元成本下降。

## 不做项

- 不引入 GraphRAG / Agentic RAG。
- 不删除 workstream scoped session。
- 不实现 acpx keepalive、长连接 worker 或 `PersistentAgentRuntime`；本实验只处理 session transcript compaction 和 typed memory seed，不改变“每个 agent 节点一次独立 Execution”的进程边界。
- 不把 session snapshot 加总进 provider token total。
- 不在没有 benchmark 数据前切默认。

## 进入实现前置条件

1. Codex provider usage gap 在 metrics/trace 中显式可见。
2. WorkstreamMemory 的 handoff/review brief 已能稳定生成。
3. prompt provenance manifest 能显示 seed section token 体量。
4. 至少先用 simple public benchmark 证明不会增加 toolCalls/readCount，再进入 cancer-clinic 中等 case。
