# Ledger-first Memory Loop 设计

> 状态：P1 设计 + Phase A 最小实现已落地，真实 case 待验证
> 更新日期：2026-06-21

这份文档定义 dual-orchestrator 的最小记忆闭环。目标不是先引入 RAG 或向量数据库，而是在现有 ledger / artifact / context package 之上补齐一层可验证、可追溯、可压缩的工作记忆。

## 1. 背景

真实 cancer-clinic run 暴露了一个关键问题：

- Codex context package 已经在预算内；
- Claude Code 仍然在 intake 和 design 阶段重复读取大量相同文件；
- token 主要来自 Agent 自主探索和 cached read，而不是 orchestrator 给出的 context package 超预算；
- ledger 记录了过程，但下一个 Agent 没有一份稳定、短小、可引用的“上一阶段到底已经确认了什么”。

这说明系统缺的不是普通日志，而是面向下一步执行和审查的领域记忆：

```text
Claude 需要 Working Memory / Implementation Handoff
Codex 需要 Review Evidence Package / Evidence Brief
Orchestrator 负责从 ledger、typed artifacts、git evidence、自测报告中装配这些包
```

## 2. 目标

P1 最小闭环要解决四件事：

1. 减少跨阶段重复探索，让 Claude 优先消费已确认的工作记忆，而不是从仓库重新摸索。
2. 让 Codex 审查拿到结构化证据包，知道哪些是事实、哪些是 Claude 声明、哪些需要抽查。
3. 支持 context window reset 和新 session 续跑时恢复关键上下文。
4. 保持所有证据可追溯，任何摘要都能指回 ledger 中的原始 artifact。

## 3. 非目标

本阶段不做：

- 不引入 RAG、向量数据库或语义搜索服务；
- 不重写 workflow kernel；
- 不把所有源码读入长期记忆；
- 不把 Agent 的自然语言摘要直接当成 workflow truth；
- 不保证 Claude Code 完全不再读文件，只减少不必要的重复读和无依据探索；
- 不做跨 workstream 全局知识库。

RAG 可以作为后续“候选证据发现”能力，但不能替代 ledger source of truth。

## 4. 领域对象

### 4.1 EvidenceManifest

EvidenceManifest 是 workstream 内的证据索引，不是证据本体。

建议路径：

```text
.agent/workstreams/<workstream_id>/memory/evidence-manifest.json
```

它只记录 ledger 中已经存在的 artifact、sidecar、git evidence、self-test、gate decision 和 human decision 的引用与摘要。

核心字段：

```json
{
  "workstream_id": "ws-...",
  "updated_at": "...",
  "items": [
    {
      "id": "ev-...",
      "type": "task_contract|stage_handoff|review|self_test|git_diff|gate_decision|delivery",
      "stage_id": "design",
      "iteration_id": "iter-002",
      "node_instance_id": "node-...",
      "node_execution_id": "exec-...",
      "source_ref": "artifacts/...",
      "summary": "...",
      "claims": [],
      "files": [],
      "risk_tags": [],
      "status": "available|superseded|stale|invalid",
      "verified_by": [],
      "token_estimate": 0
    }
  ]
}
```

职责边界：

| 责任 | Owner |
| --- | --- |
| 记录证据索引 | Orchestrator |
| 生产原始证据 | Agent / deterministic handler / human gate |
| 校验证据文件是否存在 | Orchestrator |
| 判断产品质量 | Codex / Human |

EvidenceManifest 可以重建。重建来源必须是 ledger 和 artifacts，不能依赖 UI 或 CLI 投影。

### 4.2 StageHandoff

StageHandoff 是 Claude 在一个阶段结束时交给后续阶段的结构化工作交接。

它是 Agent 声明，必须通过 artifact validator 后才能进入 EvidenceManifest。

建议字段：

```json
{
  "artifact_type": "stage_handoff",
  "stage_id": "design",
  "iteration_id": "iter-002",
  "status": "READY|NEEDS_REVISION|BLOCKED",
  "objective": "...",
  "decisions": [],
  "touched_files": [],
  "evidence_refs": [],
  "open_questions": [],
  "risk_areas": [],
  "self_test_summary": "",
  "recommended_next_actions": [],
  "do_not_repeat_reads": [],
  "review_focus": []
}
```

边界：

- StageHandoff 不拥有 workflow route；
- StageHandoff 不替代 task contract；
- StageHandoff 不替代 Codex review；
- StageHandoff 只是一份可验证的 Agent 交接材料。

### 4.3 WorkingMemoryPackage

WorkingMemoryPackage 是 Orchestrator 给 Claude 的下一步工作记忆包。

建议路径：

```text
.agent/workstreams/<workstream_id>/context/working-memory-<node_instance_id>.md
.agent/workstreams/<workstream_id>/context/working-memory-<node_instance_id>.json
```

它从 EvidenceManifest、task contract、最近的 StageHandoff、human decision、必要 git evidence 中装配。它是 projection，可以重建。

内容原则：

- 先给 canonical task contract；
- 再给本阶段相关的已确认结论；
- 给出必须复用的文件和证据引用；
- 明确哪些文件已读过、为什么仍可能需要重读；
- 明确风险、开放问题和禁止扩大范围事项；
- 不展开 transcript，不复制大量历史 Markdown。

### 4.4 ReviewEvidencePackage

ReviewEvidencePackage 是 Orchestrator 给 Codex 的审查证据包。

建议路径：

```text
.agent/workstreams/<workstream_id>/context/review-evidence-<node_instance_id>.md
.agent/workstreams/<workstream_id>/context/review-evidence-<node_instance_id>.json
```

它不是 Claude 自述的复制，而是面向 verifier 的证据 brief。

内容原则：

- task contract 和验收标准；
- Claude 本轮声明的结论；
- 关键 artifact sidecar；
- touched files / diff stat / git evidence；
- self-test report；
- missing evidence / must-fix / risk areas；
- 建议抽查点；
- 原始证据 refs。

Codex 应该优先审查这个 package。如果证据不足，Codex 应输出结构化 `MISSING_EVIDENCE`，而不是盲目扩大仓库扫描。

## 5. Source of Truth

| 对象 | 是否 source of truth | 说明 |
| --- | --- | --- |
| Raw artifacts / typed sidecars | 是 | 原始证据和机器事实。 |
| Ledger node / execution / event | 是 | 执行事实。 |
| EvidenceManifest | 半事实索引 | 可重建，引用事实，不替代事实。 |
| WorkingMemoryPackage | 否 | 面向 Claude 的上下文投影。 |
| ReviewEvidencePackage | 否 | 面向 Codex 的审查投影。 |
| UI / CLI trace | 否 | 只读投影。 |
| RAG index | 否 | 未来可作为候选发现，不可作为最终事实。 |

## 6. 最小流程

```text
Agent run
  -> outputs/<node>.md
  -> typed artifact sidecar
  -> optional stage_handoff
  -> Artifact validator
  -> EvidenceManifest update
  -> Context builder
       -> WorkingMemoryPackage for Claude
       -> ReviewEvidencePackage for Codex
  -> Next Agent consumes package first
```

如果 StageHandoff 缺失或非法：

- validator 记录 contract failure；
- 当前节点按 artifact contract 失败，或在明确允许的节点降级为 `handoff_missing`；
- Orchestrator 不能从 Markdown 自然语言拼一个假 handoff 继续路由。

如果 EvidenceManifest 中引用的文件不存在：

- manifest item 标记 `invalid`；
- package builder 不展开该证据；
- review package 必须显示 evidence gap。

## 6.1 当前实现状态

截至 2026-06-21，Phase A 已落地的最小闭环是：

- `EvidenceManifest` 会从 workstream ledger 和 typed artifacts 重建证据索引，并写入 `.agent/workstreams/<id>/memory/evidence-manifest.json`；
- implementation context profile 会渲染 `Working Memory` section，优先给 Claude 使用上一阶段 handoff、决策、变更文件和风险；
- Codex review profiles 会渲染 `Review Evidence Brief` section，优先给 Codex 使用 task contract、Agent sidecar、自测、验收、git evidence 和 human decision；
- context package metadata 会记录 evidence manifest 路径、evidence refs、working memory evidence count 和 review evidence count；
- `DUAL_CONTEXT_MEMORY_ENABLED=false` 可用于 A/B 实验，禁用 Agent-facing package 中的 `working_memory` / `review_evidence` section；它不改变 ledger / artifact / manifest 的事实来源。
- Claude / Codex prompt 已增加约束：先消费对应 package，证据不足时才扩展读取，并说明缺口；
- 单元测试覆盖 governed profile 和显式 `include_sections` 两条 context build 路径。

仍未完成的部分：

- `stage_handoff` 还没有作为独立 artifact contract 全量接入 validator；
- repeated read / cached read 的自动统计和治理还没有完成；
- 真实 cancer-clinic 中等复杂 case 还没有用于验证 token 与重复探索改善幅度；
- RAG / 长期知识库仍不进入当前 MVP。

## 7. Token 与读取治理

新增观测指标：

| 指标 | 作用 |
| --- | --- |
| `working_memory_tokens` | Claude 工作记忆包大小。 |
| `review_evidence_tokens` | Codex 审查证据包大小。 |
| `reused_evidence_refs` | 本阶段复用的旧证据数量。 |
| `new_evidence_refs` | 本阶段新增证据数量。 |
| `repeated_read_files` | Agent 再次读取的已知文件。 |
| `read_budget_warning` | 重复读超过阈值。 |
| `manifest_coverage` | 当前 package 中有多少结论能指回 evidence ref。 |

第一版不阻止 Agent 读文件，只做两层约束：

1. prompt 明确要求优先使用 package，只有证据不足时才读仓库；
2. metrics 标记重复读和缺失证据，供后续收敛。

后续如果 repeated read 仍过高，再考虑把 read budget 变成硬 gate。

## 8. 与 RAG 的关系

最小闭环坚持 ledger-first，原因：

- 这是工程任务，不是开放知识问答；
- 关键问题是“哪个证据支持哪个结论”，不是“相似文本在哪里”；
- RAG 的召回结果不天然可信，仍需要 ledger evidence ref；
- 引入向量库会增加部署、同步、过期、权限和调试成本。

未来 RAG 的合理位置：

```text
RAG candidate retrieval
  -> Evidence candidate
  -> Orchestrator / Agent 验证
  -> typed artifact / evidence ref
  -> EvidenceManifest
```

RAG 只能提供候选，不能直接驱动 workflow route、completion contract 或 human gate。

## 9. 实施计划

### Phase A：最小闭环

1. 定义 `stage_handoff` contract schema。
2. 为 Claude 阶段 prompt 增加 stage handoff 输出要求。
3. 新增 EvidenceManifest writer，可从最新 typed artifacts 增量更新。
4. Context builder 增加：
   - Claude profile 的 WorkingMemory section；
   - Codex profile 的 ReviewEvidence section。
5. package JSON sidecar 记录 evidence refs、token estimate 和缺失证据。
6. Metrics projection 增加 package token 和 evidence reuse 字段。

当前 Phase A 状态：

| 项目 | 状态 |
| --- | --- |
| `stage_handoff` contract schema | Partial：已有 next-stage handoff 结构消费，尚未作为独立 contract 强校验。 |
| Claude prompt handoff / memory 约束 | Done for MVP：已要求优先使用 Working Memory。 |
| EvidenceManifest writer | Done for MVP：可从 ledger/artifact 重建，不作为 source of truth。 |
| WorkingMemory / ReviewEvidence section | Done for MVP：已接入 governed profile 和显式 section override。 |
| Context metadata evidence refs | Done for MVP。 |
| Metrics evidence reuse 字段 | Done for MVP：workstream metrics 和 actual governance report 已能统计 repeated read，并标记是否命中 EvidenceManifest。 |

### Phase B：读取治理

1. 从 acpx transcript 提取工具读文件统计。Done for MVP。
2. 将 repeated reads 与 EvidenceManifest 对比。Done for MVP。
3. 在 operator / trace 中展示“重复探索”提示。Done for MVP。
4. 为高重复读阶段给出 prompt / package 改进建议。Not started；需要先用 simple / cancer-clinic case 校准阈值和误报。

### Phase C：更强的记忆压缩

1. 增加 goal-level gist artifact。
2. 支持过期和 superseded evidence。
3. 支持 deterministic candidate selection。
4. 评估是否需要 RAG。

## 10. 验收标准

Phase A 完成后必须满足：

- 每个 Claude 关键阶段能产出或显式缺失 `stage_handoff`；
- EvidenceManifest 能列出 task contract、stage handoff、review、自测、git evidence 的 refs；
- Codex review package 不展开 transcript，只展示 brief + refs；
- Claude working memory package 不复制大段历史输出；
- context package sidecar 能显示 package tokens 和 evidence refs；
- simple real-case 里 package 生成完整；
- cancer-clinic 中等复杂 case 里必须产出可对比的 baseline/report，至少包含重复 `Read` 次数、cached read tokens、package tokens 和 agent total tokens；
- Phase A 不把固定下降比例作为硬验收。重复读和 token 消耗应当有可观察改善；如果没有下降，必须能从 metrics 看出是 package 缺失、Agent 不遵守 package，还是任务本身确实需要重读；
- `30%` 这类比例只能作为后续观察性目标，不能作为本阶段完成条件。

## 11. 风险

| 风险 | 缓解 |
| --- | --- |
| Agent 不按 handoff contract 输出 | validator 失败并写 contract diagnostic，prompt 再修，不靠自然语言兜底。 |
| 摘要压缩丢失关键证据 | package 必须保留 evidence refs，Codex 可要求 `MISSING_EVIDENCE`。 |
| Manifest 过期 | manifest 可从 ledger 重建，item 带 source ref 和 status。 |
| 新 schema 过大 | Phase A 只保留必要字段，避免一次性做知识库。 |
| RAG 过早引入导致事实漂移 | P0/P1 不引入 RAG，未来只作为候选发现。 |

## 12. 设计边界自检

| 检查项 | 结论 |
| --- | --- |
| 是否新增 source of truth | 否。source of truth 仍是 ledger 和 typed artifacts。 |
| 是否新增领域对象 | 是，EvidenceManifest / WorkingMemoryPackage / ReviewEvidencePackage，用于解决重复探索和证据装配缺口。 |
| 是否替代 Artifact Contract | 否。它消费 artifact contract，不绕开 validator。 |
| 是否替代 Goal Loop | 否。它只改善上下文和证据输入，不判断 goal satisfaction。 |
| 是否引入长期高成本依赖 | 否。先用本地 JSON / Markdown sidecar。 |
| 是否可测试 | 是。manifest 更新、package 生成、refs 存在性、token budget、重复读 metrics 都可测试。 |

## 13. 下一步建议

先实现 Phase A，不进入 RAG。完成后跑两个验证：

1. simple real-case：确认 stage handoff、manifest、review package 都生成。
2. cancer-clinic 中等复杂 case：比较重复读和 token breakdown，判断是否真正降低探索成本。

如果 Phase A 后 Claude 仍大量重复读同一批文件，再进入 Phase B，把 read reuse 从软提示升级为更强的 harness 约束。
