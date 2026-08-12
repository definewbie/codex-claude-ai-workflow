# Human Decision Gate Convergence — Peer Review

日期：2026-08-12

审查对象：`human-decision-gate-convergence-20260812.zh-CN.md`
审查人：独立、只读、代码事实驱动的同行评审
状态：`WARN / CORRECTIONS REQUIRED / NOT YET IMPLEMENTATION-READY`

## 0. 审查方法

本次审查完全基于已提交的生产代码和 v6 captured ledger artifacts，不调用 provider、不启动 PostgreSQL、不修改文件。

核验代码范围：
- `ReviewDecisionHandler.java` (79 行)
- `RoutingEngine.java` (行 54-203, 310-409)
- `ContractCompileHandler.java` (551 行)
- `GateDocument.java` (72 行)
- `ContractRegistry.java` (167 行)
- `WorkstreamActionService.java` (行 100-155)
- `ReviewApproveCommand.java` (49 行)
- `WorkstreamApiController.java` (行 164-193)
- `AgentOutputContractDocument.java` (463 行)
- 三个 bundled workflow definitions
- v6 captured ledger: `ws-20260811T143704Z-RZQL`

---

## 1. Boundary Review：WARN

| 维度 | 判定 | 依据 |
|------|------|------|
| 根因诊断 | PASS | `ReviewDecisionHandler:52-53` → `RoutingEngine:91` 精确 |
| 缺失 edge 扫描 | PASS | 三个 workflow 0 条 BLOCKED edge，`ContractRegistry` 三决策节点均有 BLOCKED |
| compile blocker 识别 | PASS | `ContractCompileHandler:315-316` 只接受 READY_FOR_PRODUCT_REVIEW |
| GateDocument 来源缺口 | PASS | `RoutingEngine:190` 只存 `subject_input_refs`，无 `source_outcome` |
| UI/API/CLI 不对称 | PASS | approve API 和 CLI 均硬编码 note |
| contract-resolution preservation | **WARN** | 设计正确但缺少 `subject_input_refs` 包含 reviewer-sidecar ref 的代码级约束 |
| 设计一致性 | **WARN** | 4 处设计声明与代码事实的细节偏差（见 Findings） |

---

## 2. Findings（按 P0/P1/P2，逐条附 file:line）

### P0-1：resolveGateDocument 无 source_outcome persistence
- **声明**：§6.3 "RoutingEngine 创建 GateDocument 时持久化 source_outcome"
- **代码事实**：`RoutingEngine.java:181-191` 当前只写入 `Map.of("subject_input_refs", List.copyOf(outputRefs))`
- **风险**：如果新增 BLOCKED edge 后仍按现有方式创建 gate，`source_outcome` 会丢失；下游 compiler 和 note enforcement 无法区分 BLOCKED gate 和 READY gate
- **修正**：必须在此处将 outcome 写入 gate result：`Map.of("subject_input_refs", ..., "source_outcome", outcome)`

### P0-2：resolveContractNegotiation 不读取 reviewer sidecar
- **声明**：§6.5 "从已验证 reviewer sidecar 的 must_fix / missing_evidence 生成问题"
- **代码事实**：`ContractCompileHandler.java:364-396` 只从 `proposal.openQuestions()` 生成 resolved questions。reviewer sidecar 的 `must_fix` 和 `missing_evidence` 字段存在但从未被编译路径消费
- **风险**：即使 gate 打开、人工批准，编译后的 `contract-resolution.json` 仍然只有 proposal 层面的 open_questions（v6 中为空），reviewer 的 BLOCKED 问题完全丢失
- **修正**：需要新增代码读 reviewer sidecar 并生成 resolution；不能只依赖 proposal 的 open_questions

### P0-3：readResolvedGate 的 subject_input_refs 匹配语义不足
- **声明**：§6.5 "compiler eligibility 只增加 BLOCKED review 被同一 matching human gate 带 note 批准"
- **代码事实**：`ContractCompileHandler.java:448` 检查 `subjectRefs.contains(reviewRef)` — 其中 `subjectRefs` 来自 `GateDocument.result.subject_input_refs`。当前创建 gate 时 `subject_input_refs` = `outputRefs`（即 decision node 的 outputRefs = inputRefs = 原始 run output files）。如果 gate 的 `source_outcome` 是 BLOCKED，reviewRef 需要匹配的是 reviewer sidecar 文件路径，但 `subject_input_refs` 指向的是 decision node 的 input refs（已包含 `.contract.json`）
- **事实**：目前的 `subject_input_refs` 已经包含 review sidecar 的 `.contract.json` 路径（因为 decision node 的 inputRefs=outputRefs），所以这个检查实际上是 work 的。但设计文档没有明确指出这一点。
- **风险**：低 — 当前输入流恰好使检查通过，但语义依赖是隐式的。如果 decision node 的 inputRefs/outRefs 未来变更，匹配会静默失败
- **修正**：设计应显式记录"gate 的 subject_input_refs 来自 decision node inputRefs，其中已包含 reviewer sidecar .contract.json"

### P1-1：设计未记录 RoutingEngine 行 190 的 outputRefs 来源
- **声明**：§6.3 "RoutingEngine 创建 GateDocument 时持久化 source_outcome"
- **代码事实**：`RoutingEngine.java:165` 处 outputRefs 是 decision node 的 input refs（line 61-83 中 `outputRefs`=上游节点的 `inputRefs`），而 decision node 的 input refs 通过 `NodeResult` 的第二个参数传入（`ReviewDecisionHandler.java:53` 传了 `context.instance().inputRefs()`）
- **影响**：修改时容易破坏现有 `subject_input_refs` 语义，导致 readResolvedGate 无法匹配
- **修正**：在设计 §6.3 中显式记录 outputRefs 传递链

### P1-2：HumanReviewGateHandler 的 allowed_actions 不感知 source_outcome
- **声明**：§6.4 "management UI 与 operator console 在当前 review decision 为 BLOCKED 时显示 decision-note 输入"
- **代码事实**：`HumanReviewGateHandler.java:44-49` 从 `context.definition().config().getOrDefault("allowed_actions", ...)` 读取允许动作 — 这是 definition 级配置，不感知当前 gate 的 `source_outcome`。UI 需要额外查询 gate 的 result 来判断是否强制 note
- **影响**：UI 需要读取 gate document 的 `result.source_outcome` 来决定是否强制 note input。当前 handler 生成的 review packet 不包含此信息
- **修正**：review packet 或 UI 查询需要包含 source_outcome 信息以驱动 note 输入框

### P2-1：AgentResultPromptContract 中 BLOCKED 的 prompt 级描述
- **声明**：§3.2 "review prompt 明确允许在 agent 不应自行决定真实产品歧义时返回 BLOCKED"
- **代码事实**：`ContractRegistry.java` 将 BLOCKED 定义为 `intake.decision` 的 `allowedDecisions` 之一，但 prompt 文本 不由 `ContractRegistry` 管理 — prompt 在 `AgentPromptRenderer.java` 中根据 contract 动态生成。BLOCKED 作为 allowed decision 出现在 contract 中，prompt renderer 会将其传递给 agent
- **结论**：设计声明基本准确，但 SoT 链为 `ContractRegistry → AgentPromptRenderer → agent prompt`，不是独立的 prompt 模板

### P2-2：stopOnNonCompletedNode → recovery gate 中的 BLOCKED 分支
- **代码事实**：`RoutingEngine:91` 在 `completedStatus != COMPLETED` 时调用 `stopOnNonCompletedNode()` 创建 recovery gate
- **事实**：v6 ledger 中 `recovery-node-intake-decision-020.json` 正是此路径产物
- **设计相关性**：修复后此路径不再对 BLOCKED 触发，但 `stopOnNonCompletedNode` 仍保留用于 FAILED/其他非 COMPLETED 状态。设计不需要描述此细节

---

## 3. Problem Classification

| 问题 | 分类 | 说明 |
|------|------|------|
| ReviewDecisionHandler BLOCKED→NodeStatus.BLOCKED | **Execution/Business conflation** | execution status 不应替代 typed business decision |
| 0 BLOCKED edges in 3 workflows | **Definition/Contract drift** | definition 落后于 contract (ContractRegistry) |
| approve hardcoded note | **UI/API/CLI asymmetry** | 不同入口的人类决策可解释性不等价 |
| contract-resolution only from open_questions | **Evidence blind spot** | reviewer BLOCKED 的原因不进入 contract 记录 |
| compile only accepts READY_FOR_PRODUCT_REVIEW | **Compile eligibility gap** | BLOCKED 路径即使 gate 被批准也无法编译 |

---

## 4. Owner / SoT Analysis（对照设计 §4）

| 设计声明 | 代码验证 | 判定 |
|----------|----------|------|
| review 是否要求 human decision → sidecar decision=BLOCKED | `AgentOutputContractDocument.decision()` + `ContractRegistry.allowedDecisions()` | ✅ 准确 |
| decision node 是否执行成功 → ReviewDecisionHandler | `ReviewDecisionHandler.java:38-56` | ✅ 准确（修复后） |
| 应进入哪个 gate → workflow definition edge | workflow JSON edges | ✅ 准确（修复后） |
| 当前 gate、允许动作、来源 outcome → open GateDocument | `GateDocument.java` + `RoutingEngine.java:181-191` | ⚠️ 需要添加 source_outcome |
| 人类选择与说明 → resolved GateDocument.result | `GateDocument.java:36-51` | ✅ 准确 |
| contract 中的人类决策证据 → contract-resolution.json | `ContractCompileHandler.java:364-396` | ⚠️ 需要扩展读取 reviewer sidecar |
| UI / CLI → command projection | `WorkstreamActionService.java` | ✅ 准确 |

---

## 5. UI / CLI / UI+CLI Comparison

| 入口 | 当前 note 支持 | 当前 BLOCKED 感知 | 修复后 |
|------|---------------|-------------------|--------|
| CLI `review approve` | `"approved from CLI"` 硬编码 | 无 | 需新增 `--note` option |
| CLI `review decide` | `--note` 已有 | 无 | 已完备 |
| API `POST /review/approve` | 无 request body，硬编码 `"approved from API"` | 无 | 需新增 `@RequestBody ReviewApproveRequest` |
| Management UI | approve button 无 note 输入 | 无 | 需新增 note input + source_outcome 驱动 |
| CLI `review revise` | `--note` (作为 feedback) | 无 | 已完备 |
| API `POST /review/revise` | `@RequestBody` 含 feedback | 无 | 已完备 |

**关键观察**：`review complete`（API + CLI）已正确支持 note，`review revise` 已正确支持 feedback。唯一缺失的是 `review approve`。设计在 §6.4 正确识别了这个问题。

**UI+CLI 统一**：设计 §5 的 `WorkflowFileLedger.resolveOpenGate()` 作为 single enforcement point 是正确的。所有入口最终调用同一个方法，note enforcement 在此处实施。

---

## 6. End-to-End Invariant

| 环节 | 状态 | 说明 |
|------|------|------|
| **Declared by** | strict sidecar decision=BLOCKED + workflow BLOCKED edge | 设计 §11 |
| **Proven by** | captured v6 deterministic replay | `node-intake-decision-020.json` decision=BLOCKED |
| **Consumed by** | RoutingEngine 创建 human gate（带 source_outcome） | 当前缺失，需在 line 190 添加 |
| **Enforced before** | ledger → gate.note 非空 → compile eligibility | resolveOpenGate + validateCurrentApprovedLineage |
| **Failure route** | 无 note 保持 WAITING_FOR_HUMAN，不进入 recovery | 设计 §6.3 正确 |
| **Cannot-bypass** | UI/API/CLI parity + historical gate guard + definition consistency + compile lineage tests | 设计 §7 列 11 项 |
| **Residual path** | READY gate 既有附议不回归 | 设计 §7.9 正确 |
| **⚠️ Residual gap** | `resolveContractNegotiation` 扩展为读取 reviewer sidecar 时，reviewer sidecar 的 JSON schema 中 `must_fix` 和 `missing_evidence` 是 `List<String>`，而 `ContractResolutionDocument.ResolvedQuestion` 的构造接受 `String question` — 每个 reviewer 的 must_fix/missing_evidence 项需要单独映射为 question | 设计未覆盖此映射细节 |

---

## 7. Overengineering Assessment

设计通过以下选择避免了过度工程：
- 不新增 phase/module/registry/engine
- 不新增 `HUMAN_DECISION_REQUIRED` 作为独立 workflow outcome
- 不让 UI/CLI 直接编辑 contract
- 不从 Markdown 猜测 human decision
- 不在本 checkpoint 修改 provider/model/prompt
- 不新增专用 product-choice schema

**判定**：设计保持在一个原子 checkpoint 的范围内，无 overengineering。

---

## 8. Required Corrections（实施前必须修正）

### C-1 (P0)：将 source_outcome 写入 GateDocument 创建
在 `RoutingEngine.java:181-191` 添加 `source_outcome` 到 gate result：
```java
Map.of(
    "subject_input_refs", List.copyOf(outputRefs),
    "source_outcome", outcome  // 新增
)
```

### C-2 (P0)：扩展 resolveContractNegotiation 读取 reviewer sidecar
在 `ContractCompileHandler.java` 的 `resolveContractNegotiation` 方法中，当 gate source_outcome=BLOCKED 时，从 reviewer sidecar 的 `must_fix`/`missing_evidence` 字段生成 resolved questions，而不是仅从 `proposal.openQuestions()`。

### C-3 (P1)：在设计 §6.3 中记录 outputRefs 传递链
显式记录：gate 的 `subject_input_refs` 来自 decision node 的 `outputRefs`，后者等于 decision node 的 `inputRefs`（即 reviewer sidecar 的 output files）。

### C-4 (P2)：在 review packet 中包含 source_outcome
`HumanReviewGateHandler.writeReviewPacket()` 应包含当前 gate 的 `source_outcome` 字段，使 UI 能够据此决定是否强制 note 输入。

### C-5 (spec)：approve API 签名变更
`POST /review/approve` 从 `@RequestParam` 改为接受 `@RequestBody ReviewApproveRequest`（含 optional note）。

---

## 9. Implementation READY Verdict

**NOT YET — 需完成 C-1 到 C-5 后重新审查。**

核心方向正确，所有 P0/P1/P2 发现均可在同一 checkpoint 内修正。修正后的 checkpoint 保持原子性，不扩大 scope。

建议流程：
1. 设计文档修订（incorporate C-1 到 C-5）
2. 代码实现
3. deterministic replay（使用 v6 captured artifacts）
4. focused tests + full reactor
5. 再次独立 peer review
6. 标记 ENFORCED
7. fresh completion workstream → PROVEN

---

## Appendix A: Verified Code Facts Matrix

| 设计声明 | 代码位置 | 验证结果 |
|----------|----------|----------|
| BLOCKED → NodeStatus.BLOCKED | `ReviewDecisionHandler.java:52-53` | ✅ |
| RoutingEngine 只路由 COMPLETED | `RoutingEngine.java:91` | ✅ |
| 0 BLOCKED edge in 3 workflows | `agent-lifecycle.*.json` | ✅ |
| compile 只接受 READY_FOR_PRODUCT_REVIEW | `ContractCompileHandler.java:315-316` | ✅ |
| resolution 只从 open_questions | `ContractCompileHandler.java:364-396` | ✅ |
| GateDocument 无 source_outcome | `RoutingEngine.java:190` | ✅ |
| approve 硬编码 note | `WorkstreamActionService.java:116`, `ReviewApproveCommand.java:36` | ✅ |
| readResolvedGate 只接受 approved | `ContractCompileHandler.java:453` | ✅ |
| v6 final state：BLOCKED, no gate | `node-intake-decision-020.json` | ✅ |
| ContractRegistry 有 BLOCKED in allowedDecisions | `ContractRegistry.java:106-118` | ✅ |
| resolveGateDocument 无 note enforcement | `RoutingEngine.java:361-379` | ✅ |
| resolveOpenGate action canonicalization | `RoutingEngine.java:347-359` | ✅ |

---

## Appendix B: 设计成熟度 vs 代码成熟度

| 标的 | 当前状态 | 设计 target |
|------|----------|------------|
| `edbb37f3` (revision-patch) | ENFORCED | 不变 |
| `fa1f4818` (compiler fix) | ENFORCED / NOT PROVEN | 不变 |
| Human Decision Gate | NOT YET IMPLEMENTED | ENFORCED (post-replay) |
| End-to-end BLOCKED→COMPLETE | BROKEN (v6 proof) | PROVEN (after fresh completion) |

