# Context Governance 与 Repo Scope 设计

> 状态：Phase A 被动治理已落地到 planned + actual report，真实 A/B case 待验证
> 更新日期：2026-06-22

这份文档定义 dual-orchestrator 的上下文治理和仓库范围控制方案。它延续
`ledger-first-memory-loop` 的方向，但进一步明确：memory 只负责保存可追溯事实；
orchestrator 才负责把事实、任务契约、阶段目标和执行约束装配成可执行的上下文治理策略。

## 1. 背景

近期 simple case 和 cancer-clinic real-case 的 token breakdown 暴露了一个更深的问题：

- context package 本身没有明显超预算；
- memory 开关能减少一部分 context package 估算 token，但对总 token 的改善有限；
- Claude Code 仍会在 intake / design / implement 阶段做大量自主仓库探索；
- 工具调用、重复 `Read`、cached read 和 transcript 才是 token / 时延的主要来源；
- Codex 也需要更短、更聚焦的 review evidence brief，而不是继续消费大段历史输出。

因此，下一步不是继续把更多摘要塞进 memory，而是让 orchestrator 明确回答：

```text
这个阶段应该读哪些文件？
这个阶段允许改哪些文件？
哪些事实已经确认，不应该反复探索？
如果 Agent 需要越界探索，如何记录理由、风险和结果？
如何证明治理有效，而不是只靠主观感觉？
```

## 2. 问题定义

当前系统有三类边界缺口。

### 2.1 Memory 只能减少遗忘，不能约束探索

`EvidenceManifest`、`WorkingMemoryPackage`、`ReviewEvidencePackage` 可以帮助 Agent 继承上一阶段结论，但它们没有表达“本阶段的仓库范围”。Agent 仍可能重新读大量文件来建立自己的上下文。

### 2.2 Orchestrator 缺少阶段级 Repo Scope

workflow definition 知道当前是 design、plan、implement 或 acceptance，但没有给出阶段级的：

- recommended read set；
- allowed write set；
- forbidden scope；
- exploration budget；
- scope exceed evidence。

这会导致 Agent 自主探索和 runtime telemetry 之间没有可对照的计划。

### 2.3 验证口径不稳定

如果只看最终是否完成，无法判断 token 下降来自真实治理，还是来自任务变简单、模型偶然少读文件、或 provider cache 命中。必须建立可重复的 A/B 指标和失败归因。

## 3. 目标

1. 降低不必要的仓库重复读取和无关上下文进入 prompt 的概率。
2. 保留 Agent 必要的探索自由，避免把复杂工程任务误伤成死板脚本。
3. 让 Claude / Codex 的上下文不同：
   - Claude 需要 working memory + repo scope + implementation handoff；
   - Codex 需要 review evidence brief + scope adherence evidence。
4. 让 UI / CLI / trace 能解释 token 和文件范围：
   - 计划读什么；
   - 实际读了什么；
   - 实际改了什么；
   - 哪些越界被允许、警告或阻断。
5. 用实验验证有效性，而不是把某个固定 token 下降比例当作拍脑袋目标。

## 4. 非目标

本阶段不做：

- 不引入 RAG / 向量数据库；
- 不把 repo map 做成全局知识库；
- 不替换 Claude Code 的工具系统；
- 不默认硬阻止所有越界读取；
- 不用自然语言正文做 workflow route；
- 不引入通用规则引擎；
- 不做跨 workstream 的组织级代码知识库。

Repo map 和 scope plan 都是 ledger artifact / projection，不是新的 workflow source of truth。

## 5. 外部依据

这个方向不是凭空设计，和当前主流 agent harness 的经验一致：

- Aider 的 repo map 用有限 token 表达仓库结构和关键符号，避免把整仓源码塞进上下文；它也建议只把真正需要改的文件加入对话，减少无关上下文干扰。
- Agent harness engineering 的核心职责通常包括 context selection、tool access、project memory、permissioning、observability 和 verification。也就是说，降低 token 不只是 prompt 问题，而是 harness 应该管理“给什么上下文”和“允许怎样使用工具”。
- AHE 相关论文结论表明，工具、中间件和长期记忆的迁移价值往往高于纯 prompt 迁移；这支持把 memory / context / tool telemetry 做成 harness 能力，而不是写进单个 prompt。
- agentic coding token 研究显示，input token 通常主导成本，不同 run 的 token 消耗可能相差很大，更多 token 不必然带来更高准确率。因此必须度量仓库读取和上下文装配，而不是只看最终 outcome。
- HERMES 的具体领域不是代码仓库 harness，但其“记忆 + 中间验证 + 自适应改进”的思想支持分阶段保留可验证中间状态，避免长链路漂移。

参考：

- <https://aider.chat/docs/repomap.html>
- <https://aider.chat/docs/usage/tips.html>
- <https://arxiv.org/abs/2605.13357>
- <https://arxiv.org/abs/2604.25850>
- <https://arxiv.org/abs/2604.22750>
- <https://arxiv.org/abs/2511.18760>

## 6. 领域模型

### 6.1 RepoMap

RepoMap 是某个 workstream effective workdir 的轻量仓库地图。

它描述：

- 文件树的关键路径；
- source / test / doc / config 分类；
- 重要模块、入口、API、页面、schema、workflow 配置；
- 最近变更和 git base commit；
- 每个条目的摘要和 token 预算。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/repo-map.json
.agent/workstreams/<workstream_id>/memory/repo-map.md
```

RepoMap 不是 RAG。它是可重建的 bounded ledger artifact，必须绑定：

- workstream id；
- effective workdir；
- base commit；
- generated_at；
- generator version。

### 6.2 FileRelevancePlan

FileRelevancePlan 是任务级或阶段级文件相关性计划。

它回答：

```text
为完成当前任务，哪些文件最可能相关？
为什么相关？
是只读、可写、测试、还是禁止修改？
如果需要扩展范围，应从哪里开始？
```

建议字段：

```json
{
  "artifact_type": "file_relevance_plan",
  "stage_id": "implement",
  "iteration_id": "iter-002",
  "source": "task_contract+repo_map+git_evidence",
  "read_candidates": [
    {
      "path": "miniprogram/src/pages/treatment-record/index.vue",
      "reason": "acceptance criterion mentions treatment record upload entry",
      "confidence": "high"
    }
  ],
  "write_candidates": [],
  "forbidden_patterns": [
    "backend/db/**"
  ],
  "expansion_rules": [
    "If the candidate files call an API wrapper, read the nearest API file before editing."
  ]
}
```

第一版可以由 deterministic heuristics + task contract + RepoMap 生成。后续可以增加 LLM 辅助，但 LLM 产物必须进入 typed artifact，不直接成为执行真相。

### 6.3 StageScopePolicy

StageScopePolicy 是 orchestrator 给某个阶段的执行护栏。

它不是业务需求，也不是 workflow graph。它只描述执行边界：

| 字段 | 含义 |
|---|---|
| `stage_id` | 当前 lifecycle stage。 |
| `read_scope` | 推荐读取范围。 |
| `write_scope` | 允许修改范围。 |
| `forbidden_scope` | 明确禁止范围。 |
| `exploration_budget` | 允许新增读取的数量或 token 预算。 |
| `scope_exceed_policy` | `record_only` / `warn` / `gate` / `block`。 |

MVP 先使用 `record_only` 和 `warn`，不直接硬阻断 Agent。原因是现阶段 acpx / Claude Code 的工具权限和 transcript 结构还不足以可靠实现跨平台 hard allowlist。

### 6.4 ContextGovernanceReport

ContextGovernanceReport 是阶段结束后的治理报告。

它记录 planned vs actual：

- planned read files；
- actual read files；
- repeated read files；
- actual modified files；
- outside-scope files；
- prompt / context / transcript token estimate；
- provider usage token；
- scope warnings；
- recommended next governance improvement。

建议路径：

```text
.agent/workstreams/<workstream_id>/artifacts/context-governance/<node_execution_id>.json
```

### 6.5 ScopeViolation / ExplorationRequest

如果 Agent 实际读取或修改超出 plan，需要记录 typed event：

```json
{
  "artifact_type": "scope_violation",
  "severity": "info|warn|block",
  "stage_id": "implement",
  "path": "backend/app/api/...",
  "operation": "read|write",
  "reason": "outside write_scope",
  "evidence_ref": "runs/.../transcript.log"
}
```

MVP 不要求 Agent 先申请再读取，但报告必须让 Codex 和 human 能看到“为什么这个阶段读/改了范围外文件”。

## 7. 职责边界

| 组件 | 职责 | 不负责 |
|---|---|---|
| Memory | 保存可追溯事实、阶段 handoff、evidence refs、repo map。 | 不决定当前阶段允许读写什么。 |
| Orchestrator | 生成 FileRelevancePlan、StageScopePolicy，装配 context package，汇总治理报告。 | 不替代 Claude 做实现，不替代 Codex 做产品质量判断。 |
| Agent Adapter | 捕获工具调用、stdout/stderr、usage、transcript，尽可能提供 typed telemetry。 | 不解释业务范围，不直接决定 workflow route。 |
| Claude Code | 在约束内完成设计、实现、自测、handoff。 | 不拥有 workflow 状态和最终验收。 |
| Codex | 审查 evidence brief、范围遵守、产品/架构/用户验收。 | 不修改业务代码，不从 raw transcript 自行重建 source of truth。 |
| Human Gate | 批准、打回、终止关键业务决策。 | 不做底层流程状态修补。 |
| UI / CLI | 展示统一 read model。 | 不重新扫描文件推导 scope 或状态。 |

## 8. 执行流程

```text
Workstream start
  -> build RepoMap
  -> create initial EvidenceManifest

Intake / Contract
  -> compile canonical task contract
  -> create initial FileRelevancePlan

Before each Agent node
  -> load RepoMap slice
  -> load EvidenceManifest
  -> load latest handoff / review evidence
  -> generate StageScopePolicy
  -> render ContextPackage

Agent execution
  -> adapter captures transcript, tool calls, token usage
  -> output artifact + sidecar

After each Agent node
  -> adapter writes execution tool usage into run.json
  -> generate actual ContextGovernanceReport from execution facts
  -> update EvidenceManifest
  -> update RepoMap if modified files changed structure

Codex review
  -> receives ReviewEvidencePackage
  -> sees planned vs actual scope
  -> can flag missing evidence or scope drift
```

## 9. 阶段策略

| Stage | Read Scope | Write Scope | Scope Exceed |
|---|---|---|---|
| intake | README、需求文档、repo map、少量入口文件 | 不允许业务代码写入 | warn |
| contract | task contract、repo map、已确认 evidence | task-contract artifact | warn |
| design | 相关模块、API、schema、页面、测试入口 | 不允许业务代码写入 | warn |
| plan | design evidence、候选文件、测试命令、风险文件 | plan artifact | warn |
| implement | FileRelevancePlan 中的 source/test/doc 文件 | write_candidates | gate 或 warn |
| self_test | touched files、测试配置、build/test output | self-test artifact，必要时测试修复 | warn |
| acceptance | diff、self-test、artifact sidecar、review evidence | 不允许业务代码写入 | warn |
| delivery | final report、merge plan、git evidence | delivery artifacts / target index | block business code edits |

第一版不做强制文件系统 sandbox。实现阶段如果越界修改业务代码，先进入 governance warning；如果影响 delivery 或 final acceptance，再由 Codex / human gate 决定是否接受。

## 10. 与现有 Memory 设计的关系

`ledger-first-memory-loop` 解决的是“事实怎么继承”。本设计解决的是“执行范围怎么治理”。

二者关系：

```text
EvidenceManifest / StageHandoff
  -> 提供已知事实

RepoMap / FileRelevancePlan
  -> 提供仓库候选范围

StageScopePolicy
  -> 形成当前阶段执行护栏

ContextGovernanceReport
  -> 反证护栏是否有效
```

如果缺少 Memory，scope policy 会变成静态文件白名单，无法随着阶段结论收敛。
如果缺少 ScopePolicy，memory 会变成更多摘要，不能减少 Agent 的重复探索。

## 11. 有效性验证设计

验证必须区分四个问题：

1. token 是否下降；
2. 工具调用是否减少；
3. 任务质量是否没有下降；
4. 用户是否能理解为什么 token / scope 这样变化。

### 11.1 Baseline

先固定两个基准任务：

| Case | 目的 |
|---|---|
| simple public fixture | 快速验证 usage 提取、scope report、UI 投影是否稳定。 |
| cancer-clinic 中等复杂业务 case | 验证真实业务仓库中的重复读取、跨阶段 handoff 和 scope drift。 |

每个 case 至少保留：

- workstream id；
- acpx version；
- Claude / Codex session name；
- base commit；
- workflow definition version；
- `DUAL_CONTEXT_MEMORY_ENABLED`；
- stage count / iteration count；
- terminal decision。

### 11.2 A/B 实验

实验分三组，不能只比较 memory on/off：

| 组别 | 配置 | 目的 |
|---|---|---|
| A | memory off + no scope governance | 旧基线。 |
| B | memory on + no scope governance | 验证 memory 自身收益。 |
| C | memory on + scope governance on | 验证治理收益。 |

每组至少记录：

- total estimated tokens；
- provider usage tokens；
- context package tokens；
- transcript tokens；
- per-stage tokens；
- `Read` / `Glob` / `Grep` / `Bash` / `Edit` / `Write` 次数；
- unique read files；
- repeated read files；
- outside-scope read/write files；
- wall-clock duration；
- reconnect / recovery count；
- final acceptance decision；
- Codex must-fix 数量。

### 11.3 成功标准

本设计不承诺固定下降比例。原因是 agentic coding 的 token 方差很大，单次 run 下降 30% 不能证明机制有效，单次不下降也不能证明机制无效。

Phase A 成功标准：

- 能稳定生成 RepoMap、FileRelevancePlan、StageScopePolicy、ContextGovernanceReport；
- UI / CLI 能显示 planned vs actual scope；
- 真实 case 中能解释 token 主要来源；
- 不影响 task contract、artifact contract、delivery merge；
- final acceptance 不因上下文不足而退化。

Phase B 成功标准：

- 同类 case 中 repeated read files 有可观察下降；
- unique read files 更接近 FileRelevancePlan；
- Codex review 能基于 scope report 判断证据是否充分；
- 如果 token 没下降，report 能归因到 provider baseline、任务复杂度、Agent 未遵守 scope、或 recovery/reconnect。

观察性目标：

- simple case：token 不应显著上升，tool call 不应增加；
- 中等复杂 case：重复读取和 transcript token 期望下降 10%-20%；
- 若连续 3 次中等复杂 case 无改善，必须回到设计复盘，不继续堆 prompt。

### 11.4 停止条件

遇到以下情况应停止实验并回到模型设计：

- Agent 因 scope policy 缺少关键文件而反复失败；
- Codex 因 evidence brief 缺失证据而频繁误判；
- scope report 和 transcript 统计不一致；
- UI / CLI 展示的 planned vs actual 与 ledger artifact 不一致；
- token 下降来自跳过必要验证，而不是减少无用探索。

### 11.5 失败归因

失败时必须归入以下类型之一：

| 类型 | 说明 | 修复方向 |
|---|---|---|
| `repo_map_incomplete` | RepoMap 缺关键模块。 | 调整 repo map 生成规则。 |
| `file_plan_wrong` | FileRelevancePlan 选错文件。 | 调整计划生成，加入 evidence refs。 |
| `agent_ignored_scope` | Agent 明知 scope 仍大量越界读。 | prompt contract 或 adapter telemetry 升级。 |
| `scope_too_strict` | 必要文件被排除。 | 放宽 stage policy。 |
| `telemetry_gap` | 无法可靠统计 tool calls。 | 修 adapter parser，不改业务规则。 |
| `provider_overhead` | provider baseline 或 cache 导致收益不可见。 | 单独记录 provider usage，不归咎 context。 |
| `quality_regression` | token 降了但质量下降。 | 回退 scope policy，不能作为成功。 |

## 12. 实施计划

### Phase A：Passive Governance

目标：只观测和展示，不改变 Agent 行为。

1. 定义 RepoMap / FileRelevancePlan / StageScopePolicy / ContextGovernanceReport schema。
2. 在 context package sidecar 中记录 scope policy ref。
3. 从 transcript usage 中抽取 read/write/tool 统计。
4. 生成 per-stage ContextGovernanceReport。
5. Operator / trace 展示 planned vs actual。

当前实现状态：

| 项目 | 状态 |
|---|---|
| RepoMap | Done for MVP：context build 时写入 `memory/repo-map.json`，排除 `.agent`、`.git`、`node_modules`、`target` 等目录，绑定 workstream/effective workdir。 |
| FileRelevancePlan | Done for MVP：从 task contract、input refs、EvidenceManifest、git changed files 和 repo map 生成计划文件。 |
| StageScopePolicy | Done for MVP：按 stage/profile 生成 read/write/forbidden scope 和 `scopeExceedPolicy`。 |
| ContextGovernanceReport | Done for MVP：context build 写 `reportPhase=planned`，agent run 后 adapter 写 `reportPhase=actual`，包含 actual read/write、outside-scope、tool usage、repeated read 和 EvidenceManifest 命中。 |
| Context package sidecar | Done for MVP：metadata 记录 `repoMap`、`fileRelevancePlan`、`stageScopePolicy`、`contextGovernanceReport` 引用。 |
| Agent-facing guidance | Done for MVP：Markdown context package 增加短小的 `Repo Scope Guidance`，不展开 repo map。 |
| Execution telemetry | Done for MVP：`run.json` schema v2 增加 `toolUsage` 和 `contextGovernanceActualReportPath`，source of truth 是 adapter 运行事实。 |
| Operator projection | Done for MVP：overview artifacts 能发现最新治理文件；trace 和 Token 区域能展示 repeated read / evidence hit。后续仍需把治理建议做得更产品化。 |
| A/B 开关 | Done for MVP：`DUAL_CONTEXT_GOVERNANCE_ENABLED=false` 或 `-Ddual.context.governance.enabled=false` 可关闭治理 artifact 和 guidance。 |
| Intake seed | Done for MVP：默认 full / contract 生命周期在 `intake.claude_brainstorm` 前生成 `context.intake_claude_brainstorm`，避免首轮完全无 scope seed。 |

### Phase B：Soft Guidance

目标：让 Agent 明确知道推荐范围，但允许带理由扩展。

1. Claude prompt 增加 stage scope section。
2. Codex review packet 增加 scope adherence section。
3. repeated read 和 outside-scope 进入 review evidence。
4. 如果超出范围但有合理证据，允许继续；否则进入 warning / human attention。

当前实现状态：

| 项目 | 状态 |
|---|---|
| 历史已读文件 | Done for first pass：`FileRelevancePlan` 从历史 `run.json.toolUsage.readFiles` 派生 `previouslyReadFiles`。 |
| 重复读避免提示 | Done for first pass：`FileRelevancePlan` 写入 `repeatedReadAvoidance`，`StageScopePolicy` 写入 `readReusePolicy`。 |
| Agent-facing guidance | Done for first pass：`Repo Scope Guidance` 增加 `Already inspected files`、`Read reuse rule` 和 repeated-read risk。 |
| Planned report | Done for first pass：planned `ContextGovernanceReport` 记录 `plannedPreviouslyReadFiles` 和 `plannedRepeatedReadAvoidance`，用于后续 actual report 对照。 |
| Codex scope adherence | Partial：Codex 能通过 context package 看到 scope guidance 和 actual report，但 review packet 还没有独立产品化展示 scope adherence section。 |
| Human attention | Not yet：当前仍是 warning/record 语义，尚未把重复读或越界读升级成人审 attention。 |

### Phase C：Soft Enforcement

目标：对高风险越界行为产生 gate。

1. 实现 `scope_exceed_policy=gate`。
2. delivery 前检查 business code edits 是否在 accepted scope 内。
3. 越界写入需要 human 或 Codex 明确接受。

### Phase D：Hard Enforcement（后续评估）

目标：在 adapter / tool 层具备可靠能力后，再硬限制读写。

前提：

- acpx / Claude Code 工具调用可稳定映射到文件路径；
- 跨平台 kill / recovery / transcript telemetry 已稳定；
- hard block 不会破坏正常工程探索。

## 13. 风险与缓解

| 风险 | 缓解 |
|---|---|
| RepoMap 过期 | 绑定 base commit 和 effective workdir；修改文件后刷新相关 slice。 |
| Scope 过窄 | MVP 先 warn，不 block；允许 Agent 说明扩展理由。 |
| Scope 过宽 | 每阶段有 token/tool budget 和 repeated read report。 |
| LLM 生成的 FileRelevancePlan 不稳定 | 初版优先 deterministic，LLM 辅助必须经 typed artifact 校验。 |
| UI 再次变成 debug dump | planned vs actual 进入统一 read model，UI 不直接扫 artifact。 |
| token 改善不可见 | 按 provider baseline、transcript、context package 分层统计。 |
| 降低 token 牺牲质量 | final acceptance 和 must-fix 不得恶化，否则不是成功。 |

## 14. 边界自检

| 检查项 | 结论 |
|---|---|
| 是否新增 source of truth | 否。ledger / typed artifacts 仍是 source of truth。 |
| 是否新增领域对象 | 是，RepoMap / FileRelevancePlan / StageScopePolicy / ContextGovernanceReport，用于补齐 context governance 边界。 |
| 是否与 Memory 职责冲突 | 否。Memory 保存事实，Orchestrator 生成执行策略。 |
| 是否让 UI 推导状态 | 否。UI 只能消费治理 read model。 |
| 是否依赖自然语言路由 | 否。所有治理结论必须进入 JSON sidecar。 |
| 是否过早引入 RAG | 否。RAG 仍是后续候选发现能力。 |
| 是否可验证 | 是。通过 A/B、tool call、token、scope adherence、final acceptance 共同验证。 |

## 15. 下一步

Phase A 已完成，Phase B 第一版软约束已落地。仍然不要直接进入 hard allowlist。

接下来必须先跑：

1. simple acpx case，确认 usage / transcript / governance report 可用；
2. cancer-clinic 中等复杂 case，确认 planned vs actual 能解释 token 和重复读；
3. 一次 memory off / memory on / governance on 的 A/B，对比是否真的减少重复探索。

如果 Phase B 只能生成提示但 actual repeated read 没有下降，说明约束力度不足，应优先改 scope policy / prompt contract / review packet，而不是继续压缩 context package。
