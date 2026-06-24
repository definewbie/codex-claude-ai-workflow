# Session 治理设计方案（Session Governance）

> 状态：**设计稿，未实现**。本文件只描述方案与取舍，不含代码改动。
> 前置：token 观测链路已修复（`measuredRunCount` 从 0 → 6，`cachedRead`/`contextUsed` 可见）。
> 红线：**不引入 RAG / GraphRAG**；连续性靠 orchestrator 已有的结构化 context package，不靠向量检索。

## 0. TL;DR

- **问题**：默认 `session_scope=workstream` 让一个 workstream 内所有 Claude/Codex run 续接同一个 acpx session，上下文滚雪球。
- **实测证据**（benchmark `ws-20260623T215939Z-RFCM`，10 个 run）：
  - Codex `contextUsed`（会话累计快照）4 轮单调膨胀：`38235 → 50449 → 63990 → 77784`。
  - Claude `cachedReadTokens` 合计 `1,014,848`，占总 token 的 **87%**；单 `implement` 轮 `cachedRead=472,832`。
- **核心论点**：orchestrator 的 **context package（`context.build_package`）已经是跨 run 连续性的真源**——每个 run 都被显式装配了上一阶段产出 / 契约 / 证据 / git diff；另有 `RepoScopeGovernance` 的 read-reuse-contract 防重复读。因此 acpx session 里累积的"历史 turn"**大部分是冗余**，可以做成短命会话而不丢必要上下文。
- **治理四件套**：① scope（会话粒度）② boundary close（边界关闭）③ budget 触发分段 ④ orphan cleanup；全部用已修好的观测驱动。

## 1. 现状（代码事实）

| 事实 | 位置 | 说明 |
|---|---|---|
| session 名 = `<base>-<workstreamId>` | `AcpxAgentTaskHandler.sessionName()` | 默认 `session_scope=workstream`，同 ws 全程复用 |
| 续接 | `AcpxAgentTransport.run()` `acpx -s <session>` | 每次续接既有会话，历史全量带入 |
| 关闭 | `closeSession()` 仅在 `rebuildSession`（reconnect 失败）调用 | **workstream 终态从不关 session** → `~/.acpx/sessions/` 无限堆积；resume 同 ws 继承全部历史 |
| 另一档 | `session_scope=run` | 每 run 新会话，零雪球但**零跨 run 记忆**——两极，无中间档 |
| 裸名隐患 | `ResumeCommand` `claudeSession="claude-main"` / `codexSession="codex-reviewer"` | 不带 workstreamId 后缀，若该路径跑 prompt 会**跨 task 共享 session** |
| 已有连续性机制 | `context.build_package`、`RepoScopeGovernance.readReuseContract` | 每 run 显式装配上下文 + 防重复读——使短命 session 可行 |

## 2. 设计原则

1. **dual ledger / context package / handoff 是长期连续性的真源**（跨 run、跨阶段、跨修复轮）；acpx session 只是**短期执行记忆**，只承载单个 scope 内的工作记忆。
2. **不引入 RAG**：连续性 = 结构化 context package（已有）+ 显式 handoff，不做向量检索。
3. **角色感知**：Claude（产出）与 Codex（审查）会话需求不同，scope 可分别配。
4. **可观测驱动**：用 `cachedRead`/`contextUsed` 趋势 + per-session budget 决定何时分段，不盲猜。

## 3. Scope 模型（核心）

### 3.0 术语澄清（重要，避免误解）

- **`iteration` 指 dual workflow 的"语义修复轮"（semantic revision iteration）**，即 ledger 里的
  `iter-001 / iter-002 / …`——一次 Codex/人闸打回后回到 Claude 重做所形成的一轮。**它不是单次
  agent run。** 一个 `(workstream, stage, iteration)` 元组内**可能包含多个 agent run**（例如 design
  阶段同一修复轮里 Claude 跑了多个 turn，或先 context.build 再 agent.run）。
- 因此 **`stage_iteration` ≠ "每个 run 一个 session"**。它的语义是：**同一个 workstream、同一个阶段、
  同一个语义修复轮内的所有 agent run，共享同一个 provider session**；只要三者任一变化（换阶段、或
  进入下一修复轮），才开新 session。
- 这与 `run`（每 run 一个 session，零跨 run 记忆）和 `workstream`（全程一个 session）都不同，是
  介于二者之间的中间档。

### 3.1 四档 scope

session key 构成如下（**始终含 workstreamId**，杜绝跨 task 共享）：

| scope | session key | 共享 session 的范围 | 雪球上限 |
|---|---|---|---|
| `run` | `<base>-<workstreamId>-<runId>` | 仅单次 run | 单 run |
| `stage_iteration`（新） | `<base>-<workstreamId>-<stage>-<iteration>` | 同 ws + 同 stage + 同语义修复轮内的多个 run | 一个 stage-pass |
| `stage`（新） | `<base>-<workstreamId>-<stage>` | 同 ws + 同 stage，跨所有修复轮 | 一个阶段全部轮次 |
| `workstream`（**现状默认**） | `<base>-<workstreamId>` | 整个 ws 全程 | 整个 ws |

### 3.2 A/B 主假设（**不是结论，A/B 验证通过前绝不切默认**）

> 本节是待验证的假设，不是推荐默认。**在 §8 的 A/B 拿到"成本下降且质量不退化"的证据前，默认
> 仍是 `workstream`，不得切换。**

- `claude` = `stage_iteration` —— 同一修复轮内 Claude 多 turn 可共享 session 自忆；换阶段/换轮靠
  context package。
- `codex` = `run` —— 审查每次独立；上一轮审查结论已在 context package 里，无需会话自忆。

> 假设依据（非结论）：实测 codex 的雪球最干净（contextUsed 单调翻倍），而审查的"上轮结论"本就在
> context package；claude 同一修复轮内的自忆偶尔有用，但跨轮的上一版产出也已在 package。是否成立
> 必须由 A/B 验证。

## 4. Boundary Close（边界关闭）

- **scope 边界关闭**：进入新 scope 段前 `closeSession` 上一段（如 `stage_iteration` 下进入新 stage/iter）。
- **workstream 终态关闭**：进入 `COMPLETED` / `FAILED` / 长时 `WAITING_FOR_RECOVERY`（abandoned）时关闭该 ws 的 claude/codex session。
- **实现挂点**：在 flow 推进点（节点路由 / iteration 边界 / 终态判定）调用 closeSession；不动 session 复用语义本身。
- **兜底 SessionReaper**：周期任务扫描 `~/.acpx/sessions/`，回收终态 ws 的残留 session（治"永不关闭"的磁盘泄漏）。

## 5. Budget 触发分段（软上限，可观测驱动）

- 每个 session 一个 token 软预算，取自观测字段：codex 看 `contextUsed`，claude 看 `inputTokens`（或阶段预算）。
- **超阈值 → 主动分段**：orchestrator 在下一次 run 前，若该 session 累计已超预算，则 `closeSession` + 开新段（key 追加 segment 序号），不等 provider 自动 compaction。
- 反应式即可：run N 超预算 → run N+1 起新段，增长被约束在"约 1 run 超出量"。
- 阈值可配（如 codex 60k、claude 按阶段），超阈值事件计入 metrics（新增 `segmentedSessionCount`），保持可观测。
- 这是"雪球可见→达阈值就切"的闭环，不依赖 RAG。

## 6. Resume 行为

- resume 同一 workstream：当前 scope session **仍在预算内 → 续接**；**超预算或已关闭 → 开新段**（带 continuation 标记，不继承全部历史）。
- 取消"无条件继承全部历史"的隐式行为。

## 7. 配置与回退

- `session_scope` 扩展为 per-agent：`session_scope.claude` / `session_scope.codex`（旧单值视为两者同值，向后兼容）。
- 保留 `workstream` 作为 opt-in，不破坏存量 workstream。
- 默认变更走 **env / feature flag 灰度**（如 `DUAL_SESSION_SCOPE_DEFAULT_CLAUDE=stage_iteration`），可一键回退 `workstream`。
- **P0 不改默认**（见 §10），先把止损项落地。

## 8. 验证计划（用刚修好的观测做 A/B）

在 public benchmark 上跑 4 档 scope，对比：

- **成本**：`total.totalTokens`、`total.cachedReadTokens` 占比、codex `contextUsed` 趋势（是否仍单调膨胀）、`costByCurrency`。
- **质量**：Codex 审查结论、acceptance 是否通过、must-fix 数、revision 轮数、是否出现重复读文件（`toolMetrics.repeatedReadFiles`）。

**通过条件**：`stage_iteration`/`run` 下成本显著下降（contextUsed 不再单调膨胀）**且**质量不退化（验收通过、revision 数不显著增加、重复读不上升）。否则回退或改用 `stage`。

## 9. 风险与开放问题

- **短 session → agent 重复读文件 / 重复探索？** 由 read-reuse-contract + context package 兜底；A/B 时用 `repeatedReadFiles` 监控。
- **provider 自带 compaction 的取舍**：本方案主动分段更可控可观测；provider compaction 仍作为单 run 内的最后兜底。
- **"开新 session 但带摘要种子"是否可行**：若 acpx/Claude 支持以一段 handoff 摘要作为新 session 的首 turn，可作为 `stage_iteration` 的增强（结构化 handoff，**不是 RAG**）。需确认 acpx 能力。
- **跨 task 隔离**：session key 必须恒含 workstreamId；ResumeCommand 裸名路径必须纳入同一 scope 策略（P0 修）。
- **观测盲区**：若某 provider 只回 `usage_update`（如本次 codex），per-run 输入用量未知，budget 只能用 `contextUsed` 近似——已是当前最佳信号。

## 10. 分阶段落地（每阶段都以观测为安全网）

- **P0（纯止损，低风险，不改默认 scope）**
  - 修 `ResumeCommand` 裸名为 workstream 作用域。
  - workstream 终态 `closeSession`。
  - `SessionReaper` 回收终态残留。
  - 产出：磁盘不再泄漏、跨 task 隔离闭环；默认行为不变。
- **P1（能力，feature flag，默认仍 workstream）**
  - 实现 `stage_iteration` / `stage` scope + per-agent 配置。
  - budget 触发分段 + `segmentedSessionCount` 观测。
- **P2（决策）**
  - benchmark A/B（§8），选定默认 scope。
- **P3（切换）**
  - 切默认 + 清理 flag。

---

**待你确认的决策点**：
1. P0 是否现在就做（止损项，零行为变更风险）？
2. A/B 主假设（claude=`stage_iteration` / codex=`run`）是否采纳作为**验证对象**？（注意：A/B 通过前默认仍是 `workstream`，本设计不主张在取证前切默认）
3. budget 阈值初值（codex 60k？claude 按阶段？）。
4. "新 session 带 handoff 摘要种子"是否值得探（取决于 acpx 能力）——这步我会先确认 acpx 是否支持，再决定。

---

## 11. P0 实现状态（已落地，仅 lifecycle cleanup）

P0 已实现并测试通过。**它只做 acpx session 生命周期止损，不解决 token 雪球本身**（雪球治理留 P1/P2 A/B，默认 `session_scope` 未改动）。

已落地：
- **P0.1** 移除 `ResumeCommand` 的死选项 `--claude-session` / `--codex-session`（裸名 `claude-main` / `codex-reviewer`，从未真正传入 runtime，且易误导成跨 task 共享 session）。CLI resume 路径不再暴露任何裸 session 名；真实 session 名始终在 `AcpxAgentTaskHandler` 内按 workstream 作用域生成。
- **P0.2** `WorkstreamActionService` 在每个 action 之后，若 workstream 处于终态（COMPLETED / FAILED），best-effort 关闭其 acpx session（`AcpxSessionLifecycle`）。close 失败/异常被吞掉，**绝不影响 workflow 主状态**；结果追加写入 `.agent/workstreams/<ws>/session-cleanup.log`。
- **P0.3** 新增 `dualctl sessions-reap --workdir <path>`：扫描 workdir 下所有 workstream，**只对终态的** close 残留 session；RUNNING / WAITING_FOR_HUMAN / WAITING_FOR_RECOVERY 一律跳过。非 daemon、不删 `~/.acpx/sessions` 文件、只调 `acpx sessions close`。

明确不在 P0（留 P1/P2）：
- 默认 `session_scope` **仍是 workstream**，未改。
- `stage_iteration` / `stage` scope、budget 触发分段、handoff 摘要种子——均未实现。
- token 雪球本身未治理（需 A/B 取证后再定）。

Troubleshooting：
- 终态 workstream 的 session 仍残留在 `~/.acpx/sessions/`：跑 `dualctl sessions-reap --workdir <agent-worktree>`；查 `.agent/workstreams/<ws>/session-cleanup.log` 的 closed/failed。
- `failed > 0`：通常是 acpx 不可用或 session 已不存在；不影响 workflow 终态，可幂等重跑 reap。
- WAITING_FOR_RECOVERY 长期 abandoned 的 workstream：P0 暂不自动 close（"abandoned" 难定义）；`sessions-reap` 只在它转为 FAILED 时处理。如需清理，先 retry / close-mistaken-iteration 转终态再 reap。

---

## 12. P1 实现状态（已落地：可配置 + 可观测 + 可 A/B 的 session scope）

P1 已实现并测试通过。**它只建立 A/B 能力和观测基础，不直接优化 token、不改默认行为、不引入 RAG、不做摘要种子。**

### 已实现
- **`SessionScopePolicy`（集中命名）**：session name 的唯一拼装点，handler/CLI/UI 不再自拼。4 档 scope：
  - `workstream`（默认）→ `base-<workstreamId>`
  - `stage` → `base-<workstreamId>-<stageId>`
  - `stage_iteration` → `base-<workstreamId>-<stageId>-<iterationId>`
  - `run` → `base-<workstreamId>-<runId>`（仍带 workstreamId，避免跨 task 泄漏）
  - **恒含 workstreamId**（所有 scope，含 run 档），**永不裸名**（不会退化成 `claude-main`）。
- **per-agent env A/B**：`DUAL_SESSION_SCOPE_CLAUDE` / `DUAL_SESSION_SCOPE_CODEX` ∈ {workstream,stage,stage_iteration,run}。Claude/Codex 可分别配置；未配置 → workstream。
- **scope 优先级**：env（per-agent）> node-config `session_scope`（legacy opt-in，workstream/run）> 默认 workstream。保留 P1 前 node-config `session_scope=run` 的既有行为（`runScopedSessionRemainsOptIn` 守护）。
- **stage/iteration 来源**：`NodeInstance.stageId()` / `iterationId()`（workflow 语义字段，非 node-id 子串猜）。缺失时**显式 fallback 到 workstream + reason**；非法 env **fallback + reason**（不静默）。
- **run.json 记录决策**：每个 agent run 写入 `sessionScope / sessionSegmentKey / sessionScopeReason / stage / semanticIteration`，benchmark 可解释 token 变化。
- **观测暴露**：metrics `tokenMetrics.runs[]` 含上述字段；`dualctl trace` 每 run 显示 `scope=… seg=…` 与 token 同行；console breakdown 含 `scope/stage/iter`。

### 默认行为不变
- 未配置 env + node-config（或均为 workstream）时 → scope=workstream，session name = `base-<workstreamId>`，与 P1 前**逐字节一致**。

### 如何做 A/B
1. 基线：不设 env（默认 workstream）跑一组。
2. 对照：`DUAL_SESSION_SCOPE_CLAUDE=stage_iteration DUAL_SESSION_SCOPE_CODEX=run` 跑同 case。
3. 两组都用 P0 已修好的 token 观测对比。

### A/B 应比较的指标
- 成本/雪球：`total.totalTokens`、`total.cachedReadTokens` 占比、codex `contextUsed` 趋势（是否仍单调膨胀）、`costByCurrency`。
- 质量（不能为 token 牺牲质量）：Codex 审查结论、acceptance 是否通过、must-fix 数、revision 轮数。
- 副作用：`toolMetrics.repeatedReadFiles`（短 session 是否导致 agent 重复读文件）、duration。
- 通过条件：对照组成本显著下降且质量不退化（验收通过、revision 数不升、重复读不升），才考虑改默认。

### 明确未做（留后续）
- **scope 边界自动 close 上一段 session**：P1 未做（留 P1.5/P2）。当前由 P0 的 terminal close + `sessions-reap` 兜底清理；stage/stage_iteration 段切换时不主动 close 上一段。实现时需保证只 close 不再被复用的段、失败不影响 workflow。
- **"新 session 带 handoff 摘要种子"**：仍未做（开放问题，取决于 acpx 能力；本次按红线不研究/不实现）。
- **默认 scope 切换**：未切，仍 workstream；要等 A/B 取证后才决定。
- **不引入 RAG / GraphRAG**。
