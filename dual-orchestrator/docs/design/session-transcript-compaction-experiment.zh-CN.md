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

- `DUAL_SESSION_COMPACTION_EXPERIMENT=off|implement_boundary|codex_incremental|both`
- `DUAL_SESSION_COMPACTION_MAX_SEED_TOKENS=<n>`

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
- 不把 session snapshot 加总进 provider token total。
- 不在没有 benchmark 数据前切默认。

## 进入实现前置条件

1. Codex provider usage gap 在 metrics/trace 中显式可见。
2. WorkstreamMemory 的 handoff/review brief 已能稳定生成。
3. prompt provenance manifest 能显示 seed section token 体量。
4. 至少先用 simple public benchmark 证明不会增加 toolCalls/readCount，再进入 cancer-clinic 中等 case。
