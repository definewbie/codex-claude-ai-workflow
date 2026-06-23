# Workstream Memory 与 Read Reuse 设计

> 状态：Phase A 已实现；Phase B/C 待推进
> 更新日期：2026-06-23

这份文档定义 dual-orchestrator 下一阶段的记忆机制。目标不是先引入
GraphRAG 或 AgenticRAG，而是把现有 ledger / artifact / context package
升级成一套可审计、可共享、可约束 Agent 行为的工程记忆。

一句话：

```text
Claude 和 Codex 不共享隐藏 session 记忆；Orchestrator 必须拥有显式 Workstream Memory。
```

## 1. 背景

当前系统默认使用 workstream-scoped acpx session：

```text
acpx claude -s claude-main-<workstream_id> -f <prompt>
acpx codex  -s codex-reviewer-<workstream_id> -f <prompt>
```

这个选择让同一 workstream 内的 Agent 能复用 ACP session 上下文，减少
`session/new` 和重复探索。早期 run-scoped 策略虽然隔离更强，但带来明显副作用：

- Claude 每个阶段像是“新来的开发者”，会重新探索仓库；
- Codex 每次审查也会重新确认上下文；
- Claude 和 Codex 之间没有共享长期记忆，只能通过 prompt / artifact 传递；
- simple case 可以闭环，但 cancer-clinic 中等 case 暴露了大量重复 `Read`；
- token 主要消耗在 Agent 自主探索、transcript 和 cached read，而不是 context package 本身。

已有 `ledger-first-memory-loop` 和 `context-governance-and-repo-scope` 解决了
一部分问题：系统已经能记录 evidence、repo map、scope policy 和 repeated read。
但当前 memory 仍然偏“提示”，还没有成为 Agent 执行前必须消费的工作边界。

## 2. 要解决的问题

### 2.1 跨 Agent / 跨阶段记忆丢失

Claude 的设计、计划、自测结论，Codex 的打回理由，人审的确认，都会进入 ledger。
但下一轮 Agent 仍可能不复用这些事实，而是重新读取和重新判断。

这不是单纯 prompt 文案问题，而是缺少一等领域对象来回答：

```text
当前 workstream 已经知道什么？
哪些文件已经读过？
这些文件和任务的关系是什么？
哪些事实已经被 Codex 或 human 确认？
下一次 Agent run 应该复用哪些事实，避免重复读哪些文件？
```

### 2.2 Orchestrator 只能事后统计，不能事前约束

现在 metrics 能显示 repeated reads，但那是 run 之后的事实。
Agent run 之前缺少一个明确契约：

```text
本阶段优先使用哪些记忆？
哪些文件不应重复读取？
哪些文件允许重复读取？
如果必须重复读取，应该说明什么理由？
```

### 2.3 不能只依赖长生命周期 acpx session

直接复用全局 `claude-main` 这类长 session 可能减少重复读，但会重新引入：

- 上下文不可控增长；
- session reconnect 后语义不稳定；
- 上一阶段错误上下文污染下一阶段；
- Claude 和 Codex 仍然不能共享同一份可信记忆；
- UI / CLI / ledger 无法审计模型隐藏记忆。

因此，session 只在 workstream 内复用；长期记忆仍必须由 Orchestrator 管理，
不能只依赖 Claude Code CLI 的隐式对话上下文。

## 3. 目标

1. 让 Claude / Codex 共享同一份 Workstream Memory。
2. 降低跨阶段、跨 Agent 的重复仓库读取。
3. 让 Agent 在读取已知文件前看到已有摘要和证据引用。
4. 把重复读取从“事后统计”升级为“事前契约 + 事后核对”。
5. 保持所有记忆可追溯、可重建、可压缩。
6. 为未来 GraphRAG / AgenticRAG 预留后端扩展点，但不把它们作为 MVP 前提。

## 4. 非目标

本阶段不做：

- 不引入向量数据库；
- 不引入 GraphRAG；
- 不做 AgenticRAG 自主检索 loop；
- 不把所有 transcript 塞进长期记忆；
- 不把 memory 变成 workflow route 的 source of truth；
- 不依赖 Claude / Codex 长 session 记忆；
- 不用自然语言摘要直接决定状态流转；
- 不做 hard allowlist 阻断所有越界读取。

## 5. 设计原则

### 5.1 Memory 是领域对象，RAG 是实现后端

Memory 的领域语义先确定：

```text
任务事实、文件职责、已读摘要、阶段结论、审查意见、验证证据、读取复用约束
```

GraphRAG / AgenticRAG 只是未来的检索实现，不能反过来定义领域模型。

### 5.2 Orchestrator 拥有长期记忆

Claude 和 Codex 可以生产记忆候选，但不能拥有长期记忆。
长期记忆必须写入 ledger 下的 typed artifact，并能被 UI / CLI / report 审计。

### 5.3 记忆必须能约束下一次执行

如果 memory 只是“多一段摘要”，Agent 仍会重复读仓库。
因此 memory 必须进入 `ReadReuseContract`，并在 run 后被核对。

### 5.4 软约束优先，硬阻断后置

当前阶段先要求 Agent 解释重复读取理由，不直接阻断。
等 telemetry 和误报率稳定后，再考虑 gate 或 block。

## 6. 领域模型

### 6.1 WorkstreamMemory

WorkstreamMemory 是当前 workstream 的长期工程记忆索引。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/workstream-memory.json
```

核心字段：

```json
{
  "schema_version": 1,
  "workstream_id": "ws-...",
  "base_commit": "...",
  "updated_at": "...",
  "goal_summary": "...",
  "task_contract_ref": "...",
  "known_facts": [],
  "file_summaries_ref": "memory/file-summaries.json",
  "working_set_ref": "memory/working-set.json",
  "latest_read_reuse_contract_ref": "memory/read-reuse-contract.json"
}
```

它不是事实本体，只是索引和摘要。事实本体仍然是 ledger events、typed artifacts、
run metadata、git evidence 和 human decisions。

### 6.2 WorkingSet

WorkingSet 描述当前任务真正相关的文件集合。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/working-set.json
```

示例：

```json
{
  "artifact_type": "working_set",
  "stage_id": "plan",
  "iteration_id": "iter-003",
  "core_files": [
    {
      "path": "miniprogram/src/pages/follow-up-summary/index.vue",
      "reason": "任务要求调整复诊准备清单和缺失资料提醒",
      "confidence": "high",
      "last_read_by": ["claude", "codex"],
      "last_read_stage": "design"
    }
  ],
  "supporting_files": [],
  "excluded_files": [
    {
      "path": "backend/**",
      "reason": "当前任务不涉及后端接口变更"
    }
  ]
}
```

WorkingSet 和已有 `FileRelevancePlan` 的关系：

- `FileRelevancePlan` 是某阶段的候选计划；
- `WorkingSet` 是跨阶段收敛后的任务相关文件集合；
- `FileRelevancePlan` 可以更新 WorkingSet；
- WorkingSet 不直接决定 workflow route。

### 6.3 FileSummary

FileSummary 是“文件级可复用记忆”。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/file-summaries.json
```

示例：

```json
{
  "artifact_type": "file_summaries",
  "files": [
    {
      "path": "miniprogram/src/pages/follow-up-summary/index.vue",
      "summary": "复诊准备清单页面，负责展示复诊相关提醒和资料入口。",
      "relevant_symbols": ["followUpList", "uploadEntry"],
      "task_relevance": "核心页面，需要在此处补齐缺失资料提醒。",
      "last_observed_at": "2026-06-23T00:00:00Z",
      "source_refs": [
        "runs/run-.../run.json",
        "runs/run-.../outputs/intake-claude-brainstorm.contract.json"
      ],
      "staleness": "fresh"
    }
  ]
}
```

FileSummary 的更新来源：

- Agent tool usage 中的 read files；
- Agent 输出中的结构化 artifact；
- git diff / touched files；
- Codex review 对文件职责的确认；
- human gate 的明确反馈。

第一版不要求 LLM 自动精炼每个文件摘要。可以先用 deterministic 摘要和 Agent 输出中的短声明，
但必须保留 source refs。

### 6.4 ReadReuseContract

ReadReuseContract 是给下一次 Agent run 的读取复用契约。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/read-reuse-contract.json
```

示例：

```json
{
  "artifact_type": "read_reuse_contract",
  "target_node": "plan.claude_plan",
  "stage_id": "plan",
  "iteration_id": "iter-003",
  "must_reuse_facts": [
    {
      "fact": "follow-up-summary 页面是当前任务核心页面，已由 Claude 和 Codex 多次读取。",
      "evidence_refs": ["memory/file-summaries.json#/files/0"]
    }
  ],
  "avoid_repeating_reads": [
    {
      "path": "miniprogram/src/pages/follow-up-summary/index.vue",
      "reason": "最近两轮已读取；先使用 FileSummary，只有需要确认具体实现细节时才重读。"
    }
  ],
  "allowed_repeated_reads": [
    {
      "path": "miniprogram/src/pages/follow-up-summary/index.vue",
      "allowed_when": "准备修改该文件或需要确认精确代码位置。"
    }
  ],
  "required_explanation_when_repeated": true
}
```

ReadReuseContract 是本设计的核心。
它不只是提示 Agent “少读文件”，而是让 Orchestrator 能在 run 后核对：

```text
哪些重复读取符合 contract？
哪些重复读取没有解释？
哪些文件被重复读但没有进入 WorkingSet？
```

### 6.5 MemoryMetrics

MemoryMetrics 是 Workstream Memory 的效果观测。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/memory-metrics.json
```

字段：

```json
{
  "artifact_type": "memory_metrics",
  "run_id": "run-...",
  "node_id": "plan.claude_plan",
  "read_count": 12,
  "unique_read_count": 7,
  "repeated_read_count": 5,
  "contract_allowed_repeated_read_count": 3,
  "unexplained_repeated_read_count": 2,
  "working_set_hit_count": 6,
  "outside_working_set_read_count": 1,
  "estimated_reused_context_tokens": 0,
  "notes": []
}
```

第一版不需要精确计算“节省了多少 token”，但必须能解释 token 为什么没有下降。

## 7. Source of Truth

| 对象 | 是否 source of truth | 说明 |
|---|---|---|
| Ledger events / nodes / executions | 是 | 执行事实。 |
| Typed artifacts / sidecars | 是 | Agent 输出和系统证据。 |
| Tool usage telemetry | 是 | Agent 实际读取、写入和执行事实。 |
| WorkstreamMemory | 半事实索引 | 可重建，引用事实，不替代事实。 |
| WorkingSet | 策略输入 | 可由 facts 重建和修正。 |
| FileSummary | 压缩投影 | 必须带 source refs。 |
| ReadReuseContract | 执行约束 | 不决定 workflow route，但用于 run 后核对。 |
| UI / CLI | 否 | 只读投影。 |
| RAG index | 否 | 未来只作为候选检索。 |

## 8. 执行流程

### 8.1 Agent run 前

```text
ledger + artifacts + run metadata
  -> MemoryBuilder
  -> WorkingSet update
  -> FileSummary update
  -> ReadReuseContract
  -> Context Package
  -> Agent Prompt
```

Prompt 中应出现短小的 Memory section：

```text
## Workstream Memory

- 已确认事实：
  ...
- 已读核心文件：
  ...
- 本阶段优先复用：
  ...
- 避免重复读取：
  ...
- 如果必须重复读取，请在输出 artifact 中说明原因。
```

### 8.2 Agent run 后

```text
transcript + run.json.toolUsage + output artifacts
  -> MemoryUpdater
  -> MemoryMetrics
  -> ContextGovernanceReport update
  -> EvidenceManifest update
```

如果 Agent 重复读取了 contract 中要求避免读取的文件：

- 第一版记录为 `unexplained_repeated_read`；
- Codex review 可以把它作为 scope adherence 风险；
- 后续可以升级为 warning / gate。

### 8.3 Codex review

Codex 不应该重新自由探索所有文件。
Codex 应该优先看到：

- ReadReuseContract；
- WorkingSet；
- FileSummary；
- Claude 的重复读取说明；
- planned vs actual scope；
- git evidence / self-test evidence。

Codex 的职责是判断：

```text
Claude 是否充分复用了已有事实？
有没有因为不重复读而漏掉关键证据？
重复读取是否有合理理由？
```

## 9. 与 GraphRAG / AgenticRAG 的关系

当前不引入 GraphRAG / AgenticRAG，原因不是否定它们，而是时机不对。

### 9.1 当前主要缺口不是召回

真实 run 里 Agent 能找到相关文件，问题是反复找、反复读、反复确认。
GraphRAG 更擅长“从大量知识中找相关候选”，但不能天然解决：

- 哪些事实已经被确认；
- 哪些文件刚刚读过；
- 本阶段是否应该复用而不是重读；
- Agent 是否遵守读取复用契约。

### 9.2 RAG 不能做 source of truth

RAG 召回结果可能过期、遗漏、误召回。
它只能提供候选，必须经过 Orchestrator / Agent 验证后进入 typed artifact。

未来位置：

```text
GraphRAG candidate retrieval
  -> Candidate evidence
  -> Agent / Orchestrator validates
  -> Typed artifact / FileSummary
  -> WorkstreamMemory
  -> ReadReuseContract
```

### 9.3 引入条件

满足以下条件后再评估 GraphRAG：

- WorkstreamMemory schema 稳定；
- FileSummary 和 WorkingSet 有真实收益；
- repeated read 仍高，但主要原因是候选发现不准；
- 多 workstream 需要共享项目知识；
- 有明确索引刷新、失效、权限和成本策略。

## 10. 预期收益

### 10.1 直接收益

- Claude / Codex 共享同一份长期记忆；
- 新 session 不再意味着从零开始；
- repeated read 从“不可解释”变成“可度量、可追问”；
- Codex 可以审查 Claude 的 memory / scope adherence；
- UI 能解释 token 为什么高。

### 10.2 量化目标

这些是观察目标，不是拍脑袋硬验收。

| Case | 目标 |
|---|---|
| simple case | 不增加总 token；重复 read 不上升；流程完整闭环。 |
| cancer-clinic 中等 case | repeated read count 下降 40% 左右；如果达不到，必须能定位是 scope seed 不准、实现确需精读，还是 Agent 未遵守 read reuse contract。 |
| 多轮 revision | 后续轮次中核心文件重复读取应下降，除非进入实现或需要精确定位。 |
| Codex review | 不因 memory 压缩造成误判；能指出 evidence missing。 |

如果 repeated read 没下降，但 `unexplained_repeated_read_count` 能准确暴露问题，
也算 Phase A 成功，因为它证明系统从“看不见问题”进化到“能定位问题”。

## 11. 验收标准

### 11.1 P0 验收

1. 每个 workstream 生成 `memory/workstream-memory.json`。
2. 每个 context build 前生成或更新 `memory/working-set.json`。
3. 每个 Agent run 前生成 `memory/read-reuse-contract.json`。
4. 每个 Agent run 后生成或更新 `memory/memory-metrics.json`。
5. Prompt 中能看到简短 `Workstream Memory` / `Read Reuse` section。
6. Workstream metrics 能展示：
   - read count；
   - unique read count；
   - repeated read count；
   - allowed repeated read；
   - unexplained repeated read；
   - working set hit。
7. simple case 完整跑通。
8. cancer-clinic 中等 case 至少跑到 design / plan，并能产出 memory metrics。

### 11.2 P1 验收

1. Agent artifact contract 增加 repeated read explanation 字段。
2. Codex review packet 展示 memory / scope adherence。
3. Operator Console 展示：
   - 当前阶段使用的记忆；
   - 重复读最多的文件；
   - 哪些重复读有解释；
   - 哪些重复读需要 attention。
4. 支持 memory on/off A/B：
   - memory off；
   - memory on；
   - memory + read reuse contract on。

### 11.3 不合格标准

出现以下情况不能算完成：

- 只生成更多 Markdown，但 run 后无法核对；
- 只在 prompt 里说“不要重复读”，metrics 没有对应字段；
- token 下降但 final acceptance 质量下降；
- Codex 因证据不足而误判；
- UI 自己扫描文件推断 memory 状态；
- RAG index 直接参与 workflow route。

## 12. 实施范围

### 12.1 需要修改的边界

- `ContextBuildPackageHandler` 或其拆分后的 memory/context builder；
- Workstream metrics 聚合；
- Agent prompt 模板；
- Artifact contract validator；
- Operator / trace projection。

### 12.2 不应修改的边界

- 不改 workflow graph；
- 不改 LiteFlow kernel；
- 不引入跨 workstream 的全局长期 acpx session；
- 不改 delivery merge 语义；
- 不改 human gate 决策模型；
- 不把 memory 结果直接作为 workflow decision。

## 13. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Memory 变成另一个隐藏状态源 | 所有 memory item 必须带 source refs，并可由 ledger 重建。 |
| Agent 无视 ReadReuseContract | 先记录 unexplained repeated read，再由 Codex / UI 暴露，后续再考虑 gate。 |
| FileSummary 过期 | 绑定 base commit / file hash / last observed run。 |
| 降 token 牺牲质量 | final acceptance 和 missing evidence 是强约束。 |
| 实现过重 | P0 只做 JSON sidecar + prompt section + metrics，不做 RAG。 |
| 和现有 Repo Scope 重叠 | WorkingSet 负责跨阶段记忆，StageScopePolicy 负责阶段执行护栏。 |

## 14. 设计自检

| 检查项 | 结论 |
|---|---|
| 是否新增 source of truth | 否。ledger / typed artifact / tool usage 仍是事实来源。 |
| 是否新增领域对象 | 是。WorkstreamMemory / WorkingSet / FileSummary / ReadReuseContract / MemoryMetrics。 |
| 是否解决当前根因 | 部分解决。它解决跨 session 记忆丢失和重复读取不可见；acpx reconnect 属于 adapter/session lifecycle，由 workstream-scoped session、queue owner TTL 和 recovery signal 模型处理。 |
| 是否依赖 LLM 自觉 | 不完全依赖。Agent 可不遵守，但 metrics 会暴露。 |
| 是否引入过重基础设施 | 否。第一版只用本地 JSON / Markdown。 |
| 是否为 GraphRAG 预留扩展 | 是。GraphRAG 可作为候选发现后端，不替代 memory 领域模型。 |

## 15. 实施计划

### Phase A：可观测记忆（已实现）

1. 写 `workstream-memory.json`。
2. 写 `working-set.json`。
3. 写 `file-summaries.json` 的最小版本。
4. 写 `read-reuse-contract.json`。
5. 写 `memory-metrics.json`。
6. context package 注入短 memory section。
7. metrics 展示 repeated read 与 working set hit。

### Phase B：软约束

1. Agent contract 增加 repeated read explanation。
2. Codex review package 增加 memory adherence。
3. Operator Console 增加 memory / repeated read 视图。
4. 对 unexplained repeated read 给出 attention。

### Phase C：强治理评估

1. 对高重复读取阶段引入 warning gate。
2. 对实现阶段 outside working set write 引入 human confirmation。
3. 评估是否需要 GraphRAG 做候选发现。

## 16. 下一步

先实现 Phase A。

实现完成后必须跑：

1. simple acpx case；
2. cancer-clinic 中等复杂 case；
3. 至少一次 memory on/off 对比。

评估报告必须回答：

```text
重复 Read 是否下降？
如果没有下降，是 Agent 无视契约，还是 WorkingSet 不准？
token 主要来自 prompt、context、transcript、cached read 还是 provider baseline？
Codex 是否能审查 memory adherence？
UI / CLI 是否能解释这些结论？
```

只有这些问题能回答清楚，才进入 Phase B 或讨论 GraphRAG。
