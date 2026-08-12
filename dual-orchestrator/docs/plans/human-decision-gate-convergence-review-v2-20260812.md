# Human Decision Gate Convergence v2 — Delta Peer Review

日期：2026-08-12

审查对象：`human-decision-gate-convergence-20260812.zh-CN.md` (v2)
基准：`human-decision-gate-convergence-review-20260812.md` (v1 review)
证据：production 代码 + v6 captured ledger `ws-20260811T143704Z-RZQL`
审查方式：独立、只读、代码事实驱动

---

## 0. v1→v2 Delta 摘要

| v1 Finding | 严重度 | v2 状态 | v2 位置 |
|------------|--------|---------|---------|
| P0-1: source_outcome missing | P0 | **CLOSED** | §6.3 |
| P0-2: resolveContractNegotiation no reviewer read | P0 | **CLOSED** | §6.6 |
| P0-3: subject_input_refs implicit | P0 | **CLOSED** | §6.3 explicit ref chain |
| P1-1: outputRefs chain undocumented | P1 | **CLOSED** | §6.3 |
| P1-2: review packet no source_outcome | P1 | **CLOSED** | §6.4 GateContext→packet projection |
| P2-1: BLOCKED prompt SoT | P2 | **CLOSED** | §3.2 ContractRegistry→AgentPromptRenderer 链 |
| P2-2: stopOnNonCompletedNode retention | P2 | **CLOSED** | §10 explicit disclaimer |
| C-1..C-5 | all | **ALL CLOSED** | §6.3 — §6.6 |

---

## 1. Boundary Review：PASS

| 维度 | 判定 | 依据 |
|------|------|------|
| C-1: source_outcome 持久化 | PASS | §6.3 明确 `source_outcome=outcome`，`GateDocument.resolved()` 逐字段保留 |
| C-2: reviewer sidecar → resolution | PASS | §6.6 逐项映射 must_fix/missing_evidence → ResolvedQuestion，空集 fail closed |
| C-3: ref 传递链文档化 | PASS | §6.3 完整记录 `inputRefs→outputRefs→subject_input_refs`，强调非偶然 |
| C-4: GateContext→packet 投影 | PASS | §6.4 禁止 handler 重推断，source_outcome 唯一 owner=GateDocument |
| C-5: UI/API/CLI parity | PASS | §6.5 5 条路径等价，approve API 改为 ReviewApproveRequest |
| note enforcement | PASS | §6.3, §7.5 空 note → fail closed，gate open，WAITING_FOR_HUMAN |
| compiler eligibility | PASS | §3.5, §6.6 严格双路径，lineage 不匹配仍拒 |
| 3-workflow sweep | PASS | §6.2, §7.11 完整覆盖 terminal-delivery/contract/full |
| acceptance 排除 | PASS | §3.2 明确 exclusion，review contract 不含 BLOCKED |
| 12 cannot-bypass tests | PASS | §7 覆盖从 replay 到 historical gate readonly 的全路径 |
| stopOnNonCompletedNode 保留 | PASS | §10 explicit："不删除恢复能力" |
| no overengineering | PASS | phase/module/registry/engine 均不新增 |

**唯一 WARN**：acceptance.decision 的 BLOCKED 契约后门（见 P1-1）

---

## 2. C-1 到 C-5：逐项状态

### C-1 (source_outcome persistence)：CLOSED ✅

v2 §6.3：
> `RoutingEngine` 创建 `GateDocument` 时在同一个 result map 中同时持久化
> `subject_input_refs=outputRefs` 与 `source_outcome=outcome`；
> resolved gate 保留该字段

**代码验证**：
- 创建点：`RoutingEngine.java:190` — 当前 `Map.of("subject_input_refs", List.copyOf(outputRefs))`，需改为同时写入 `"source_outcome", outcome`。`outcome` 参数在 scope 内可用（line 59 传入）。
- 保留：`GateDocument.java:36-51` — `resolved()` 将 `result` 复制到新 map，再叠加 `human_action` 和 `note`。`source_outcome` 自然保留。✅

### C-2 (reviewer sidecar → contract resolution)：CLOSED ✅

v2 §6.6：
> BLOCKED 来源 gate：compiler 通过 `subject_input_refs` 解析并验证唯一 matching reviewer sidecar；
> 将 sidecar 中每个非空 `must_fix` 和每个非空 `missing_evidence` 分别映射成一个
> `ContractResolutionDocument.ResolvedQuestion`，保持原顺序并去重；每项的 resolution 都逐字使用
> human note，source=`human`

**代码验证**：
- Reviewer sidecar 字段：`AgentOutputContractDocument.java:81` — `mustFix`，line 83 — `missingEvidence`，均为 `List<String>`
- 当前 resolution 路径：`ContractCompileHandler.java:364-396` — 只消费 `proposal.openQuestions()`
- BLOCKED 路径需要新增读取：从 `subject_input_refs` 定位 sidecar → 解析 must_fix/missing_evidence → 逐项映射 ResolvedQuestion
- Fail-safe：若 sidecar 两组均为空 → 编译 fail closed。`StageOutcomeCompiler.java:93-104` 已经在 contract 验证层强制 BLOCKED 必须有 blocking evidence，设计 §6.6 的空集 guard 是防御性的。✅

### C-3 (ref chain documentation)：CLOSED ✅

v2 §6.3：
> `RoutingEngine.completeAndRoute()` 中的 `outputRefs` 来自 decision node 的 completed
> `NodeResult.outputRefs`；`ReviewDecisionHandler` 必须继续把 `context.instance().inputRefs()`
> 原样作为 output refs。因此 gate 的 `subject_input_refs` 明确包含触发 decision 的 validated
> reviewer sidecar `.contract.json`，**不是偶然的文件传递**

**代码验证**（trace 与设计一致）：
1. `ReviewDecisionHandler.java:53/56` → `NodeResult(decision, inputRefs, facts)` + `NodeResult.completed(decision, inputRefs, facts)`
2. `WorkflowRuntime.java:258` → `completeAndRoute(paths, ..., result.outputRefs(), ...)` — outputRefs = inputRefs
3. `RoutingEngine.java:165` → outputRefs 作为 transition input ⇒ 下一节点 inputRefs
4. `RoutingEngine.java:190` → `GateDocument.subject_input_refs = outputRefs`

修复后的 ReviewDecisionHandler 把 BLOCKED 也走 `NodeResult.completed("BLOCKED", inputRefs, facts)` → ref 链对 BLOCKED 也对齐。✅

### C-4 (GateContext → packet typed projection)：CLOSED ✅

v2 §6.4：
> - runtime/worker 打开 gate 时必须读取当前 node 对应的 open `GateDocument`，把 typed
>   `source_outcome` 作为 `GateContext` 输入传给 `HumanReviewGateHandler`
> - `HumanReviewGateHandler` 在 persisted JSON/Markdown review packet 中投影 `sourceOutcome`
> - handler 不扫描 gate 目录、不从 reviewer正文或 decision 再推断该值
> - `WorkstreamQueryService.currentReview/review` 和两个 UI 只消费 packet projection
> - packet 缺少来源、来源与 validated sidecar decision 不一致，属于 projection/lineage violation

**代码验证**：
- `GateContext` 当前不含 `sourceOutcome` 字段：`GateHandler.java:14-18` — 只有 `workspaceRoot, workstreamId, definition, instance`
- 构造点 1：`WorkflowRuntime.java:240` — `new GateHandler.GateContext(workspaceRoot, workstreamId, nodeDefinition, node)`
- 构造点 2：`WorkflowRuntime.java:388` — 同上，在 `openWaitingGates()` 中
- Packet 写入：`HumanReviewGateHandler.java:57-88` — `writeReviewPacket()` 当前不含 `sourceOutcome`
- 消费端：`WorkstreamQueryService.java` 的 `currentReview/review` 读 packet JSON

设计明确指定了投影路径，仅需实现时新增 `GateContext.sourceOutcome` 字段。设计 §6.4 禁止重推断的措辞精确覆盖了所有旁路。✅

### C-5 (approve API + CLI parity)：CLOSED ✅

v2 §6.5：
> - `POST /review/approve` 接受 `ReviewApproveRequest { note }`；不再固定写入 `approved from API`
> - `WorkstreamActionService.approve(...)` 接收并透传 note
> - 专用 CLI `review approve` 增加 `--note`，通用 `review decide --action approve --note ...` 保持不变
> - 两个 UI 在 BLOCKED 时显示 decision-note 输入
> - API/UI/CLI 不各自 canonicalize action

**代码验证**：
- 当前 approve API：`WorkstreamApiController.java:165-173` — `@RequestParam` only，无 body
- 当前 approve service：`WorkstreamActionService.java:114-116` — 固定 `"approved from API"`
- CLI approve：`ReviewApproveCommand.java:36` — 固定 `"approved from CLI"`，无 `--note` option
- CLI decide：`ReviewDecideCommand.java:25` — 已有 `--note` option ✅
- Canonicalization：`RoutingEngine.java:347-359` — `canonicalGateAction()` 只做 approve→approved 映射，不写 note

5 条路径全覆盖。`WorkflowFileLedger.resolveOpenGate()` 是唯一 canonical enforcement point。✅

---

## 3. Findings

### P1-1：acceptance.decision 的 BLOCKED 缺少显式 guard

- **代码事实**：`ReviewDecisionHandler.allowedDecisions("acceptance.decision")` 返回 `{"ACCEPT", "ACCEPT_WITH_NOTES", "REJECT", "BLOCKED"}`（`ContractRegistry.java:124`）
- **设计声明**：§3.2 "acceptance 不纳入本 checkpoint：其 agent contract 不允许 BLOCKED"
- **偏差**：设计指的是 **acceptance review contract**（`acceptance.codex_verifier_review`，`ContractRegistry.java:70`：无 BLOCKED），而非 **acceptance decision contract**。`ReviewDecisionHandler` 读取的是 decision node contract，acceptance.decision 的 contract 含 BLOCKED。
- **风险场景**：修复后，如果 acceptance review 未来被扩展为可返回 BLOCKED，`ReviewDecisionHandler` 会把 BLOCKED 当作 COMPLETED 返回 → `RoutingEngine.completeAndRoute()` 尝试 edge routing → 无 BLOCKED edge → 空 transitions → workflow 可能错误完成。
- **缓解**：当前风险不可达 — acceptance review contract 不含 BLOCKED，`StageOutcomeCompiler` 也会拒绝 BLOCKED。但设计应显式记录此 invariant：acceptance.decision contract 含 BLOCKED 是前向预留，实际路由由 acceptance review contract 和 workflow edges 联合限制。当前不添加 acceptance BLOCKED edge 是设计选择，依赖 acceptance review contract 不含 BLOCKED 这个事实。
- **建议**：§3.2 或 §6.2 增加一句："acceptance.decision 的 contract 含 BLOCKED 但当前 acceptance review contract 不含 BLOCKED，因此 review 不会产生 BLOCKED 侧车；acceptance 的 BLOCKED edge 不在此 checkpoint 内添加。"

### P2-1：GateContext 字段变更隐式

- **设计声明**：§6.4 "把 typed `source_outcome` 作为 `GateContext` 输入传给 `HumanReviewGateHandler`"
- **代码事实**：`GateHandler.java:14-18` — `GateContext` record 当前只有 4 个字段，不含 `sourceOutcome`
- **影响**：实现者需自行推断需新增 `sourceOutcome` 字段。设计意图清晰，但缺少显式的 "add `sourceOutcome` field to `GateContext`" 措辞
- **建议**：§6.4 开头增加："在 `GateHandler.GateContext` record 中增加 `String sourceOutcome` 字段"

### P2-2：resolveOpenGate note enforcement 位置未命名

- **设计声明**：§6.3 "当 `source_outcome=BLOCKED` 时，ledger 在产生任何后续路由前要求非空 note"
- **代码事实**：enforcement 应在 `RoutingEngine.resolveOpenGate()`（`RoutingEngine.java:316`）中，在调用 `resolveGateDocument()`（line 339）和 `completeAndRoute()`（line 340）之前，读取 open GateDocument，检查 `source_outcome`，若 BLOCKED 且 note 为空则 throw IOException
- **设计完整性**：语义精确但未命名方法。不影响实现正确性，但命名可防止 enforcement 被放错位置
- **建议**：§6.3 "ledger 在产生任何后续路由前" 改为 "`RoutingEngine.resolveOpenGate()` 在调用 `resolveGateDocument()` 前"

---

## 4. End-to-End Invariant

| 环节 | v1 状态 | v2 状态 | 说明 |
|------|---------|---------|------|
| **Declared by** | ✅ | ✅ | strict sidecar decision=BLOCKED + workflow BLOCKED edge |
| **Proven by** | ✅ | ✅ | v6 ledger `node-intake-decision-020.json` decision=BLOCKED |
| **Consumed by** | ⚠️ 缺 source_outcome | ✅ | RoutingEngine 创建 gate 同时写 source_outcome |
| **Enforced before** | ⚠️ 缺 note check | ✅ | resolveOpenGate 读 gate.source_outcome → 空 note → fail closed |
| **Failure route** | ✅ | ✅ | note 缺失 → gate 保持 open → WAITING_FOR_HUMAN → 不进 recovery |
| **Cannot-bypass test** | 11 项 | **12 项** | +GateContext projection test (#4)，+5-path parity (#6) |
| **Residual raw/legacy path** | ✅ | ✅ | READY gate 不变，stopOnNonCompletedNode 保留 |

---

## 5. UI + CLI 交互方案：合理

- **不选 UI-only**（§10）：headless/自动化场景失去等价入口 ✅
- **不选 CLI-only**（§10）：复杂 human evidence 不适合命令行阅读 ✅
- **不新增专用 schema**（§5.1）：不为手机号/E.164 建 radio/schema ✅
- **统一 enforcement point**（§5.2）：`WorkflowFileLedger.resolveOpenGate()` 单点 canonicalize ✅
- **review packet 投影**（§6.4）：UI 消费同一 typed projection，不自行推断 ✅

---

## 6. 是否存在更小且同样可靠的设计：否

当前 6 个子改动（ReviewDecisionHandler 去特例 + 3 workflow edges + GateDocument.source_outcome + resolveOpenGate note enforcement + compiler eligibility + contract resolution）是不可再拆的原子集：

- 单独修 ReviewDecisionHandler 不加 edge → BLOCKED COMPLETED 但无处可去
- 单独加 edge 不修 ReviewDecisionHandler → NodeStatus.BLOCKED 从不路由
- 修 handler+edge 不加 source_outcome → note enforcement 和 compiler 无法区分 gate 类型
- 修 handler+edge+source_outcome 不加 compiler → gate 开了但编译时被拒
- 修 compiler 不加 contract-resolution → 编译通过但 reviewer 问题丢失

每个修改的必要性都可在代码中验证。不存在更小的等价子集。

---

## 7. Implementation READY：YES（满足条件）

设计层面所有 C-1..C-5 和 P0/P1 发现均已闭合。三条建议（P1-1、P2-1、P2-2）为措辞强化，不改变设计语义，可在实现时同步消解。

**建议在进入实现前**：
1. §3.2 增加 acceptance.decision contract 含 BLOCKED 但 review contract 不含的说明
2. §6.4 显式注明 GateContext 新增 `sourceOutcome` 字段
3. §6.3 将 note enforcement 位置对齐到 `resolveOpenGate()` 方法名

---

## Appendix A：全量代码事实矩阵（v2 增量标注）

| 设计声明 | v1 状态 | v2 状态 | v2 位置 |
|----------|---------|---------|---------|
| BLOCKED→NodeStatus.BLOCKED 删除 | ✅ | ✅ | §6.1 |
| 3 workflow 0→9 BLOCKED edges | ✅ | ✅ | §6.2 |
| source_outcome 写入 GateDocument | ⚠️ P0 | ✅ | §6.3 |
| resolveContractNegotiation 读 sidecar | ⚠️ P0 | ✅ | §6.6 |
| ref 链显式文档化 | ⚠️ P1 | ✅ | §6.3 |
| GateContext→packet 投影 | ⚠️ P1 | ✅ | §6.4 |
| approve API body + CLI --note | ⚠️ P2 | ✅ | §6.5 |
| ContractRegistry→PromptRenderer SoT | ⚠️ P2 | ✅ | §3.2 |
| stopOnNonCompletedNode 保留 | ⚠️ P2 | ✅ | §10 |
| note enforcement fail-closed | — | ✅ | §6.3, §7.5 |
| compiler eligibility 双路径 | — | ✅ | §3.5, §6.6 |
| server-sidecanonicalization | — | ✅ | §6.5, §6.6 |
| 12 cannot-bypass tests | — | ✅ | §7 |

---

## Appendix B：v6 Replay 验证点

| v6 artifact | 修复前行为 | 修复后预期 |
|-------------|-----------|-----------|
| `node-intake-decision-020.json` decision=BLOCKED | NodeStatus.BLOCKED → recovery gate | NodeStatus.COMPLETED, outcome=BLOCKED |
| 对应 review sidecar must_fix=["human confirm phone scope"] | 丢弃 | → GateDocument.source_outcome=BLOCKED → human gate opens |
| 人工 approve + note "E.164 only" | 不存在此路径 | → contract-resolution.json 含 1 resolved question |
| proposal open_questions=[] | resolution 为空 | → BLOCKED 路径从 must_fix 生成 question |
