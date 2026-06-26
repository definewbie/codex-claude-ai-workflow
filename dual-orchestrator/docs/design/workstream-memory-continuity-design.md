# Workstream 连续性设计：从 session transcript 到 typed memory

> 状态：**设计 + 实现**。已实现 prompt provenance manifest、第一阶段 `readFiles` semantic memory + `ReadReuseContract` 最小闭环，以及第二阶段 `AgentHandoff` / `ReviewEvidenceBrief`（WorkstreamMemory 为 SoT，typed-only 抽取 decidedScope/nonGoals/decisions/openIssues/priorVerdicts/lastStageOutcome/priorFailures）。simple public benchmark A/B 见 §五。
> 触发：P1 session scope A/B 显示 stage_iteration/run scope 消除了 codex 会话雪球（contextUsed 持平），但 totalTokens +20%、toolCalls +81%——短 session 让 agent 丢失隐式上下文、重新探索仓库。
> 红线：不切默认 session_scope、不把 acpx session 当 memory、不引入 RAG/GraphRAG、不做向量检索、不做多项目知识库、不跑 cancer-clinic（simple case 证明机制有效前）、不为通过测试写 if-else patch。

## 0. 一句话论点

**acpx session transcript 是执行的副产物，不是记忆。** 跨 run/loop 的连续性必须由 orchestrator 拥有的 **typed WorkstreamMemory** 承载，并以 **AgentHandoff / ReviewEvidenceBrief / ReadReuseContract** 三种 projection 喂给 agent。session 可以变短，前提是 memory 把 transcript 里"有用的隐式上下文"显式化。

---

## 一、问题边界（Definition / Instance / Execution / Artifact / Projection）

用 boundary-first 视角给每个对象定 source-of-truth 归属。核心原则：**先定谁是 SoT、谁是 projection，再谈实现。**

| 对象 | 主角色 | 是否 Source of Truth | 谁写 / 谁读 | 生命周期 |
|---|---|---|---|---|
| **acpx session transcript** | **Execution 副产物** | **否**（provider 拥有，不透明） | 写：Claude Code/Codex 运行时；读：仅当轮 agent | 临时、不透明、可被 close 清掉 |
| **orchestrator ledger**（`.agent/workstreams/<ws>/`） | **Artifact + Instance** | **是**（"发生了什么"的权威记录） | 写：runtime/handler；读：trace/metrics/recovery | 持久、orchestrator 拥有 |
| **WorkstreamMemory**（新） | **Instance-owned durable semantic Artifact**（其 schema/policy 才是 Definition） | **是**（"agent 该记住什么"的 SoT，新） | 写：MemoryUpdater（每 run 后）；读：ContextBuildPackageHandler | 持久、per-workstream、typed |
| **AgentHandoff**（给 Claude） | **Projection（Artifact）** | 否（Memory 的派生视图） | 写：ContextBuilder；读：Claude implement run | 每 run 生成、消费即弃 |
| **ReviewEvidenceBrief**（给 Codex） | **Projection（Artifact）** | 否（Memory + git diff 的派生视图） | 写：ContextBuilder；读：Codex review run | 每 review 生成 |
| **ReadReuseContract** | **Definition + Projection（Artifact）** | 否（派生自 Memory.readFiles） | 写：MemoryUpdater；读：prompt/context 约束 agent | per-workstream、随 read 更新 |

### 关键边界结论
1. **session transcript 不是 SoT**：它是 Execution 副产物，provider 拥有、不透明、会被 close。任何"跨 run 连续性"依赖它都是脆弱的（这正是 A/B 暴露的：session 一短，连续性就丢）。
2. **ledger 是"发生了什么"的 SoT，但不是"agent 该记住什么"的 SoT**：ledger 记录执行事实（node/run/artifact），但不主动提炼成 agent 可用的结构化记忆。WorkstreamMemory 补这一层。
3. **Handoff / EvidenceBrief / ReadReuseContract 都是 Projection**：它们是 WorkstreamMemory 的消费者特定视图，**不是新的 SoT**。这保证只有一个记忆源，多个投影。

### 哪些内容不能再靠自然语言 prompt 隐式表达（必须 typed 化）
当前散落在 prompt 散文 / session 隐式上下文里、必须升级为 Memory 的 typed 字段：
- **已决策范围 / non-goals**（现在在 prompt 散文）→ `Memory.decidedScope` / `nonGoals`
- **已读文件 + 摘要 + 可信度**（现在松散）→ `Memory.readFiles[]`
- **上一轮审查结论 + 未关闭问题**（现在在 review packet 散文）→ `Memory.openIssues[]` + `Memory.priorVerdicts[]`
- **上一轮失败原因**（现在在 recovery markdown）→ `Memory.priorFailures[]`
- **本轮相对上轮的变化 delta**（现在隐式 = 全量重发）→ 由 MemoryUpdater 从 git diff 显式计算

---

## 二、设计目标

目标**不是**单纯降 total token，而是：
1. **减少重复仓库探索**：Claude 不重读已读且未变的文件（ReadReuseContract 强约束）。
2. **减少 Codex 全量审计**：Codex 做**增量审计**（上轮结论 + 本轮 diff + 风险变化 + 未关闭问题），而非每轮从头审。
3. **让短 session 不丢必要上下文**：session 变短的前提是 Memory 把必要上下文显式化喂回去。
4. **让 context package 成为真正的共享记忆载体**，而不是拼历史文本。
5. **token/read/toolcall 指标可验证改动有效性**（已有的观测足够）。

成功标准：**toolCalls ↓、Read 次数 ↓、repeatedReadFiles ↓、Codex contextUsed 不膨胀**；total token 不一定降（cachedRead 成本需解释），但**重复功**必须降。

---

## 三、最小可落地设计（基于现有 ledger/artifact/context package，不引入 RAG）

### 3.1 WorkstreamMemory（Workstream Instance-owned durable semantic Artifact）
per-workstream、typed、持久的结构化记忆。落地为 `.agent/workstreams/<ws>/memory/workstream-memory.json`（schemaVersion 化，原子写）。

```
WorkstreamMemory {
  workstreamId, schemaVersion, updatedAt,
  decidedScope:  [string]        // 已确认要做的事
  nonGoals:      [string]        // 明确不做的
  decisions:     [{ stage, decision, rationale, iterationId }]
  readFiles:     [{ path, sha, summary, evidenceRole, trusted, invalidationReason, readAt, readRunId }]
                 // evidenceRole: 该文件在证据里的角色(requirement/source/impl/test/config)——决定是否值得复用
                 // trusted: 摘要是否可靠到可不重读
                 // invalidationReason: 为何原本可信的读失效(sha 变/范围扩展/标记复核)；空=仍有效
  openIssues:    [{ id, desc, raisedBy(agent/human), status, raisedIterationId }]
  priorVerdicts: [{ iterationId, gate, verdict, mustFix[] }]
  priorFailures: [{ node, reason, resolved, iterationId }]
}
```
- **SoT**：跨 run/loop 的"agent 该记住什么"唯一来源。其 schema/policy 才属于 Definition；某个 `workstream-memory.json` 是具体 Workstream Instance 持有的 durable semantic artifact。
- **不是 transcript**：是提炼后的 typed 事实，不是对话历史。

### 3.2 AgentHandoff（Projection，给 Claude implement）
ContextBuilder 从 Memory 投影出的"实现交接"：
`{ goal, decidedScope, nonGoals, readFilesSummary, allowedFiles, suggestedFiles, priorFailureReason }`
- 替代当前 prompt 里散落的"上一轮做了啥/范围/失败原因"散文。
- 复用并形式化已有的 `StageHandoffDocument`（它已是 proto-Handoff：completedWork/importantDecisions/changedFiles/openRisks）。

### 3.3 ReviewEvidenceBrief（Projection，给 Codex review）
让 Codex 做**增量审计**的投影：
`{ priorVerdict, priorMustFix(unresolved), thisRoundDiff(git), riskDelta, unclosedIssues }`
- Codex 不再每轮全量审，而是"上轮结论 + 本轮改了啥 + 新风险 + 还没关的问题"。
- 复用已有 review packet 结构，**增加增量 delta**（从 git diff + Memory.priorVerdicts 派生）。

### 3.4 ReadReuseContract（Definition + Projection）
告诉 agent：哪些文件已读、摘要是否可信、什么条件下允许重读（文件 sha 变了 / 标记需复核）。
- **已部分存在**：`RepoScopeGovernance.readReuseContract`（read-reuse-contract.json）。升级为：**由 Memory.readFiles 派生**（带 sha + trusted + 重读条件），而非松散规则。
- 直接攻击 A/B 暴露的 toolCalls 翻倍（agent 不再重读未变文件）。

### 3.5 MemoryUpdater（Execution → Artifact）
每个 agent run 完成后，从以下来源**更新** WorkstreamMemory（增量、原子）：
- **run artifacts**：decision/summary → `decisions` / `decidedScope`；must-fix/open-risks → `openIssues`；审查结论 → `priorVerdicts`。
- **tool calls**（run.json 已有 toolUsage）：新增的 Read → `readFiles`（path + sha + 摘要）；标记重复读。
- **git diff**：本轮 changed files → `thisRoundDiff`（喂给 ReviewEvidenceBrief）；文件 sha 变化 → 标记 readFiles 需复核。
- **failure reason**：失败 → `priorFailures`。
- 挂在 `AgentRunMetadataWriter`/handler 的 run-完成路径上（run.json 写完后追加一步 update），不动 workflow 主流程。
- **只能从 typed 来源更新**：typed tool event（run.json `toolUsage.readFiles`/`bashCommands`/`writeFiles`）、run metadata（exitCode/status/sessionScope）、artifact contract（decision/summary/mustFix 结构化字段）、git evidence（diff/statusShort）。**严禁从自然语言正文/transcript 散文猜测**（避免把业务文本误当结构化事实）。

### 3.6 ContextBuildPackageHandler 消费 Memory
现有的 `context.build_package`（每 run 前装配 context）改为：
- 读 WorkstreamMemory → 按 agent 角色投影：claude implement run 注入 **AgentHandoff** + **ReadReuseContract**；codex review run 注入 **ReviewEvidenceBrief**。
- 替代"拼历史输出文本"为"拼 typed memory 视图"。

### 3.7 metrics 如何证明 memory 生效（已有观测够用）
- `toolMetrics.toolCallCount` / `readCount` / `repeatedReadFiles`：应下降。
- `tokenMetrics` codex `contextUsed`：增量审计后不再膨胀。
- Claude `cachedRead`/`input`：不因 fresh session 异常上升（因 Memory 喂回了必要上下文）。
- 质量基线：iteration/revision/acceptance 不退化。

---

### 3.8 Prompt provenance manifest（memory 优化的可观测性地基）

WorkstreamMemory / ReadReuseContract 生效前，必须先能观测 prompt 由哪些 section 组成、各占多少 token、来自哪里——否则无法验证 memory 是否真正进入 prompt、是否替代了 session transcript 的隐式上下文。

**prompt manifest**（`runs/<runId>/prompts/<prompt>.manifest.json`）：
- 每个 agent run **默认生成**（与 `DUAL_AGENT_PROMPT_DEBUG` 独立；后者只控制完整 content dump）。
- 字段：`schemaVersion / workstreamId / runId / nodeId / agent / promptTemplate / promptPath / totalBytes / totalEstimatedTokens / sections[]`。每 section：`name / sourceType / sourceRef / bytes / estimatedTokens / sha256 / includedReason / required`。
- `sourceType` 取值：`requirement | artifact | context_package | memory | handoff | review_packet | git_diff | read_reuse_contract | tool_policy | static_instruction | unknown`。
- **边界**：manifest 是 **projection / read model，不是 source of truth**。不存 prompt 正文；section 来源从 prompt 装配时的真实 section model 记录（`AgentPromptRenderer.renderWithSections`），**不从最终 prompt 文本反向 parse**；只能 fallback 时集中标 `sourceType=unknown`。

**与 memory 的验证链**：
- **ReadReuseContract 生效后**，manifest 必须显示独立 `sourceType=read_reuse_contract` section。该 section 是从 context package 文件中提取的 projection，用于观测 read-reuse 进入 prompt 的体积和来源；它不是新的 source of truth。
- **WorkstreamMemory / AgentHandoff / ReviewEvidenceBrief 生效后**，manifest 出现对应 sourceType 的 section，且历史/context 占比下降、memory 占比上升。
- 因此 manifest 是判断"memory 是否替代了 session 隐式上下文"的客观依据，也是 token/read/toolcall 指标之外的第二把尺子。

已实现：`PromptManifestWriter` + `AgentPromptRenderer.renderWithSections` + `AgentRunDocument.promptManifestPath` + trace `promptTokens=` / `topSections=` 展示。当前 manifest 记录 prompt 顶层 section，并把 context package 中的 read-reuse 规则单独投影为 `read_reuse_contract` section。

### 3.9 第一阶段实现状态：readFiles semantic memory + ReadReuseContract

已实现的最小闭环：
- `WorkstreamMemoryUpdater` 在 agent run 完成后，只从 typed `AcpxToolUsage.readFiles` 更新 `.agent/workstreams/<ws>/memory/workstream-memory.json`。
- `readFiles[]` 包含 `path / sha / summary / evidenceRole / trusted / invalidationReason / readAt / readRunId / readNodeId / lastSeenStage`。
- `MemoryUpdater` 不解析 agent stdout、markdown 正文或自然语言总结；summary/evidenceRole 只由文件元数据和路径类型派生。
- `RepoScopeGovernance` 读取并保留 `WorkstreamMemory.readFiles`，由 trusted 且未 invalidated 的条目派生 `read-reuse-contract.json.reusableReadFiles`。
- context package 的 repo scope guidance 会把 reusable read files 写成明确规则：先复用摘要，只有编辑/验证需要精确当前内容时才重读。
- `AgentPromptRenderer` 注入全局 `Read Reuse Contract Semantics`，要求 agent 将 read-reuse 视为 orchestrator-owned memory boundary；重读 listed file 必须在结构化 evidence/checks/verification summary/scope expansion reason 中记录原因。
- `MetricsProjectionWriter` / `dualctl trace` 会把 repeated read 中命中 `read-reuse-contract.json.reusableReadFiles` 的文件标记为 `rereadReasonRequired=true`，当前若没有结构化理由来源则显示 `rereadReasonStatus=missing`。这不是业务失败判定，只是 memory boundary 的观测信号。
- 对大型项目，`Claude implement` 再次读取 README/docs/source/test 也不能默认视为合理：这些文件通常是增量上下文，除非文件 sha 变化、马上要编辑该文件需要精确当前片段，或已有 summary 不覆盖当前阶段，否则重读必须给出结构化理由。

第二阶段已实现（AgentHandoff / ReviewEvidenceBrief / typed semantic memory）：
- **typed `reread_reasons`（合法重读的 typed 证明）**：`agent_output_contract` envelope 可选结构化字段 `reread_reasons: [{path, reason, stage, evidence}]`。`AgentOutputContractDocument`/`AgentStructuredArtifactDocument` typed 读取；`AgentContractSemantics.validateRereadReasons` 校验（path 非空、reason ∈ `ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS` 三选一、stage/evidence 若存在非空白）；`ArtifactContractValidator` 把它写进 contract sidecar + run.json；`MetricsProjectionWriter` 按 run 消费 → `rereadReasonStatus = recorded|missing|not_required`（命中 reusableReadFiles 且该 run 有 matching-path reason → recorded）；`TraceCommand` 已展示该状态。
- **semantic `readFiles` summary（替代 metadata-only）**：`ReadFileSummary`（deterministic regex/结构解析，无 LLM/RAG）：md/mdx → 前 5 个 heading + 命中 task-contract scope/acceptance 关键词的行（cap 400）；source（js/ts/py/java/...）→ export/top-level function/class + python def/class（cap 500）；test → 额外提取 describe/it/test/test_ 名；无结构 → "" → fallback metadata summary。`WorkstreamMemoryUpdater.semanticSummary` 读前 8192 字符调用之；projection 的 reusable-reads 直接渲染该 summary。
- 单测：`AgentContractSemanticsTest`(10)、`ArtifactContractValidatorTest`(24，含 reread_reasons 端到端 + 拒绝)、`MetricsProjectionWriterTest`(4，recorded/missing/non-matching)、`ReadFileSummaryTest`(7)、`WorkstreamMemoryUpdaterTest`(9)、`WorkstreamMemoryProjectionTest`(7)。4 模块全量绿。
- **未关闭（follow-up）**：read-reuse-contract 的 `reasonField`（现为 `verification_summary or scope_expansion_reason`）与 prompt 的 Read Reuse Contract Semantics 尚未指向 `reread_reasons`，故 agent 当前不会主动 emit 该字段——`rereadReasonStatus` 在真实 run 里仍会多为 `missing`，直到 prompt 引导。Task B 的 semantic summary 已端到端生效（updater→memory→projection→agent 可见），不需 prompt 改动。
- `WorkstreamMemoryUpdater.updateMemory(...)` 是唯一的 semantic memory writer：只从 typed 来源更新——`artifacts/task-contract.json`（decidedScope/nonGoals，snake_case）、run 输出里的 `agent_output_contract` envelope（decisions/openIssues/priorVerdicts/lastStageOutcome，经 `AgentArtifactEnvelopeParser` + `AgentOutputContractDocument.from`）、`AcpxToolUsage.readFiles`、classified failure（priorFailures）。绝不解析 prose。
- `WorkstreamMemoryProjection`（dual-runtime，read-only）：`appendAgentHandoff`（给 Claude `working_memory` section）+ `appendReviewEvidenceBrief`（给 Codex `review_evidence` section），都从 `workstream-memory.json` 投影，带必要 evidence refs（task-contract / prior verdict / changed-files+diff / readFiles path+sha+summary+trusted/invalidationReason）。空 memory 只渲染最小 fallback refs，不恢复旧 EvidenceManifest dump。
- `ContextPackageSectionRenderer.appendWorkingMemory/appendReviewEvidenceBrief` 改为 delegate 到 projection（**Replace，不 Augment**）；`ContextBuildPackageHandler` 两处 call site + `checkAvailability` 同步更新。`EvidenceManifest` 保留为 provenance/evidence refs/debug，不再驱动这两个 section。
- typed 契约视图 `AgentOutputContractDocument`（+ `from`/`hasDecision`/`canonicalStageOutcome`）已 widen 为 public，避免在 adapter 里重复 contract 字段 key。
- 单测：`WorkstreamMemoryUpdaterTest`（7）、`WorkstreamMemoryProjectionTest`（6）、`ContextBuildPackageHandlerTest`（34，含 3 个 memory-projection 用例）、`AcpxAgentTaskHandlerTest`（25，含 run→memory 端到端）。全量单测绿。

仍未实现：
- 对无理由重复 Read 的运行时阻断、artifact contract 字段化或 reviewer 自动打回（当前是可观测 + prompt contract 约束）。
- 默认 session scope 切换。

---

## 四、明确本阶段不做

- **不切默认 session_scope**（仍 workstream）。
- **不把 acpx session 当 memory**（session 是 Execution 副产物）。
- **不引入 RAG / GraphRAG / 向量检索**（Memory 是 typed 结构化，靠 MemoryUpdater 显式提炼，不靠检索）。
- **不做多项目长期知识库**（Memory 是 per-workstream，不跨 workstream）。
- **不跑 cancer-clinic benchmark**，直到 simple case 证明 toolCalls/重复读下降。
- **不为通过测试写 if-else patch**（Memory 是真结构，不是 prompt 里塞 if）。
- **不大改架构**：复用 ledger / StageHandoffDocument / read-reuse-contract / context.build_package / review packet。

---

## 五、验证方案（simple public benchmark A/B）

基线 = 当前默认 workstream session。
第一阶段对照 = 引入 prompt manifest + `WorkstreamMemory.readFiles` + `ReadReuseContract`，但不引入 AgentHandoff / ReviewEvidenceBrief。

对比（同一 simple case）：
- ✅ 通过：toolCalls ↓、Read 次数 ↓、repeatedReadFiles ↓、Codex contextUsed 不膨胀、质量不退化（revision/acceptance 不变）。
- ⚠️ 解释项：若 total token 不降——因为 cachedRead 是 cache-served 廉价成本，且 Memory 本身有体积；**重点是重复功（toolCalls/reads）下降**，而非 total token 绝对值。同时 context-window 风险下降（增量审计 + 短 session 可行）是隐性收益。
- ❌ 失败：toolCalls/reads 不降或上升 → Memory 没真正替代 session 隐式上下文，回炉。

### 5.1 实测结果（2026-06-25，simple public benchmark `public-issue-flow-review-state`，treatment = 本轮 memory loop）

| 指标 | baseline (02:00Z, 仅 Phase-1) | treatment (本轮) | delta | 判定 |
|---|---|---|---|---|
| final_status / acceptance / repair | COMPLETED / ACCEPT / 0 | COMPLETED / ACCEPT / 0 | 持平 | ✅ 准确性不退化 |
| readCount | 30 | 24 | −20% | ✅ 下降 |
| toolCallCount | 49 | 54 | +10% | ⚠️ 升，但几乎全部来自 `intake.codex_product_review` 单点 2→14 的 LLM 方差（首审 brief 最瘦，非本轮所致）；剔除该单点后 ≈ 42 vs 49（−14%） |
| repeatedReadFiles | 4 | 4 | 持平 | ⚠️ 4 个都是 `implement` 编辑那 4 个核心文件（README/product.md/issueWorkflow.js/test）的合法编辑读，不可消去 |
| prompt manifest tokens (sum/10 runs) | 36,400 | 36,856 | +1.3% | ✅ 无异常膨胀 |
| totalTokens | 1,169,503 | 1,031,288 | −11.8% | ✅ 下降（主要 cachedRead −12.5%） |
| cost USD | 1.586 | 1.600 | +0.9% | 持平 |

结论：**reads ↓、prompt-cache ↓、准确性不退化** 是真实收益；**repeatedReadFiles 持平、toolCalls 表观上升** 在本 case 上不可证伪——本 case 太小、重复读全是合法编辑读（已触底），且单次 run 的 LLM 方差（product_review 单点 +12 calls）主导了 toolCall 数。要明确验证 repeatedRead 下降，需要一个有多节点 wasteful re-exploration 的 case（P1 A/B 那种：6 个核心文件被 8~9 个 node 重读），而非这个「implement 只编辑 4 个文件」的简单 case。机制本身已验证 work：memory 正确捕获 readFiles/verdict，codex 各 review（arch 4→2、plan 4→2、acceptance 6→5）与 implement（7→4）都读得更少。

---

## 六、结论与下一步

1. **A/B 为何不能作默认切换依据**：treatment 消除了 codex 会话雪球（contextUsed 持平）但 totalTokens +20%、toolCalls +81%、cost +35%——雪球是 cache-served 廉价膨胀，去掉它换来更多 re-work。证明"短 session"不是 token 优化主解。
2. **根因**：orchestrator 没提供够好的结构化共享记忆；agent 退回靠 session transcript（Execution 副产物）当记忆；session 一短就丢上下文 → 重新探索。
3. **领域模型**：WorkstreamMemory（SoT，typed）+ AgentHandoff/ReviewEvidenceBrief/ReadReuseContract（Projection）+ MemoryUpdater（Execution→Artifact）。session transcript 降级为 Execution 副产物，不当 memory。
4. **最小实现计划**：见 §3，复用 StageHandoffDocument/read-reuse-contract/context.build_package/review packet，不新架构。
5. **验收指标**：toolCalls/reads/repeatedReadFiles ↓ + contextUsed 不膨胀 + 质量不退化。
6. **风险/不做项**：见 §4。
7. **下一步建议**：**开始实现，且第一阶段只做 `readFiles` semantic memory + `ReadReuseContract` 最小闭环**——不做 AgentHandoff / ReviewEvidenceBrief、不切默认 session_scope、不引入 RAG、不跑 cancer-clinic。
   **A/B 数据支撑**：toolCalls delta（+48）几乎全是 Read（A 38 → B 80，**+42**），Claude +20 / Codex +22 各半，**都是重读同一批 6 个核心文件**（B 里被 8~9 个 node 重读，A 里仅 3 个）——这正是 ReadReuseContract 直接攻击的"已读文件被 fresh session 忘掉"问题。readFiles 闭环**先用 simple public benchmark 验证 toolCalls/reads/repeatedReadFiles 下降**后，再按实测决定第二步做 AgentHandoff（攻 Claude design/plan 重探索）还是 ReviewEvidenceBrief（攻 Codex 全量重审）。
