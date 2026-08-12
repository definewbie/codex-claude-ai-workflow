# Human Decision Gate Convergence — Implementation Diff Review

日期：2026-08-12

审查对象：未提交的 38 文件变更（7 核心 + 5 CLI/API + 3 UI + 3 workflow + 20 test）
参照物：design v2 + review v2
审查方式：独立、只读、逐文件逐行代码审查

---

## 1. Boundary Review：PASS

8 个核实维度全部通过。

## 2. Findings

### P1-1：autorun resolveGate 默认 note 在 READY gate 上可绕过语义记录

- **文件**：`AutoRunCommand.java:165`
- **代码**：`ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), action, "auto-resolved from autorun")`
- **事实**：BLOCKED gate 由 `requiresHumanDecision()` (line 87-91) 在 resolveGate 前拦截，autorun 不会对 BLOCKED gate 调用 resolveGate。但对 READY gate，autorun 仍写入 `"auto-resolved from autorun"` 作为 note
- **判定**：这不属于 BLOCKED 边界问题。READY gate 的既有行为未改变。但设计 §6.5 提及 "不能用默认说明绕过 BLOCKED gate" — autorun 在 BLOCKED 时根本不到达 resolveGate，已满足
- **建议**：不阻塞接受。如果未来 READY gate 也需要人类可追溯的批准说明，可单独处理

### P2-1：WorkstreamQueryService 不暴露 source_outcome 给 review/current API

- **文件**：`WorkstreamQueryService.java` (未变更)
- **代码事实**：UI 通过 `/api/workstreams/{id}/review/current` 获取 review packet。`WorkstreamQueryService` 读取的是 `HumanReviewGateHandler` 写入的 review packet JSON，该 JSON 现在包含 `sourceOutcome`（`HumanReviewGateHandler.java:86`）
- **验证**：`console.js:1175` `state.review?.packet?.sourceOutcome` + `mgt/app.js:1296` `state.currentReview?.packet?.sourceOutcome` — 两个 UI 都从 packet 读取，而 packet 由 `openOrInspect` 生成时写入 `sourceOutcome`
- **判定**：消费路径完整，无需改 QueryService ✅

### P2-2：RunningStateGuardTest 仍使用 NodeStatus.BLOCKED（正确保留）

- **文件**：`RunningStateGuardTest.java:499`
- **代码**：`writeNodeStatus(ledger, paths, node, NodeStatus.BLOCKED, Map.of("reason", "revision_limit_reached"))`
- **事实**：这是 revision-limit 路径的 test fixture，不是 review BLOCKED。`RoutingEngine.stopOnNonCompletedNode()` 仍对 revision-limit BLOCKED 保留。设计 §10 明确"不删除恢复能力"
- **判定**：正确保留 ✅

---

## 3. 8 项核实结果

### 1. typed review BLOCKED → COMPLETED execution → routing

**对照点**：BLOCKED 不再映射为 NodeStatus.BLOCKED，不走 recovery。

- `ReviewDecisionHandler.java:52-56` 删除了 4 行 BLOCKED→BLOCKED 特例。所有通过 contract validation 的 decision 统一走 `NodeResult.completed()` ✅
- `BuiltinHandlerTest.java:960-962`：`reviewDecisionTreatsBlockedAsCompletedBusinessOutcome` 断言 `NodeStatus.COMPLETED` + outcome=`BLOCKED` + outputRefs 包含 reviewer sidecar ✅
- `WorkflowIntegrationTest.java:421`：同一断言 ✅
- `RoutingEngine.java:91`：`completedStatus != NodeStatus.COMPLETED` → 修复后 BLOCKED 不再触发此分支 ✅

**结论**：PASS。BLOCKED 作为 business outcome 由 COMPLETED 执行状态承载，不进入 recovery。

### 2. GateDocument.source_outcome 一致消费链

**对照点**：RoutingEngine 唯一持久化 → runtime/worker/packet/UI/API/CLI/autorun/compiler 一致消费。

写入链：
- `RoutingEngine.java:190-192`：`Map.of("subject_input_refs", ..., "source_outcome", outcome)` ✅

消费链：
- `WorkflowFileLedger.loadOpenGateForNode()` (line 165-176)：读取 open gate ✅
- `WorkflowRuntime.sourceOutcome()` (line 396-401) / `AgentWorker.sourceOutcome()` (line 169-174)：读取 → 传入 GateContext ✅
- `GateHandler.GateContext` (line 16)：`String sourceOutcome` 字段，非空 enforced ✅
- `HumanReviewGateHandler.openOrInspect()` (line 86)：写入 `packet.put("sourceOutcome", context.sourceOutcome())` ✅
- Markdown (line 112)：`builder.append("- Source Outcome: ").append(context.sourceOutcome())` ✅
- `console.js:1175`：`state.review?.packet?.sourceOutcome` ✅
- `mgt/app.js:1296`：`state.currentReview?.packet?.sourceOutcome` ✅
- `AutoRunCommand.snapshot()` (line 147-152)：`loadOpenGateForNode().result().get("source_outcome")` ✅

**结论**：PASS。没有 UI/node-id/review-summary/allowed-actions 重推断。

### 3. BLOCKED gate 空 note → fail closed

**对照点**：resolveOpenGate 产生副作用前强制要求非空 note。

- `RoutingEngine.java:342-347`：
  ```java
  var gate = requireOpenGate(paths, current, node);
  var sourceOutcome = text(gate.result().get("source_outcome"));
  if ("BLOCKED".equals(sourceOutcome) && (note == null || note.isBlank())) {
      throw new IOException("a decision note is required for a BLOCKED human gate");
  }
  ```
  gate 检查 → note enforcement → resolveGateDocument → completeAndRoute。note 为空时抛异常，gate 未变。✅
- `WorkflowGateResolutionTest.java:235-254`：`blockedSourceCannotAdvanceWithoutHumanDecisionNote` 断言 IOException + gate 保持 open + workflow-current 不变 ✅
- `resolveGateDocument` 签名从 `(paths, current, node, action, note)` 改为 `(paths, gate, action, note)` — 不再重复读 gate。拆分为 `requireOpenGate` + `resolveGateDocument` 两步，确保 enforcement 在 resolve 之前 ✅

**结论**：PASS。gate 未变、node 未变、workflow 未变、副作用未产生。

### 4. subject_input_refs → reviewer sidecar → must_fix/missing_evidence → ResolvedQuestion

**对照点**：编译器从 reviewer sidecar 逐项生成 ResolvedQuestion，保存 human note。

编译路径：
- `ContractCompileHandler.java:65`：`resolveRevisionContext(context, approvedReview, gate)` — gate 传入 ✅
- `ContractCompileHandler.java:313-320`：`validateCurrentApprovedLineage()` 新增
  ```java
  var eligibleBlocked = "BLOCKED".equals(reviewDecision)
          && "BLOCKED".equals(gate.sourceOutcome())
          && !gate.note().isBlank();
  ```
  三条 AND 条件全部满足才通过 ✅
- `ContractCompileHandler.java:375-377`：`resolveContractNegotiation()` 新增 blocked 分支
  ```java
  var blockedReview = "BLOCKED".equals(approvedReview.document().decision())
          && "BLOCKED".equals(gateResolution.sourceOutcome());
  ```
- `ContractCompileHandler.java:378-383`：BLOCKED 时 `openQuestions` = `blockedReviewQuestions()` 而非 `proposal.openQuestions()`
- `ContractCompileHandler.java:420-424`：`blockedReviewQuestions()` 从 `review.mustFix()` + `review.missingEvidence()` 合并，LinkedHashSet 去重保序 ✅
- `ContractCompileHandler.java:388`：每项的 `resolution` 逐字使用 `gateResolution.note()`，`source="human"` ✅
- `ContractCompileHandler.java:383-388`：空 questions + 空 note → fail closed (`TASK_CONTRACT_PROPOSAL_INVALID`) ✅

Test 验证：
- `ContractCompileHandlerTest.java:474-502`：`blockedReviewApprovedWithHumanNotePreservesTypedQuestions` — 2 个 must_fix + 1 个 missing_evidence → 2 ResolvedQuestions（去重），每项 question/resolution/source 逐字验证 ✅
- `ContractCompileHandlerTest.java:505-520`：`blockedReviewCannotUseReadyGateAsApproval` — BLOCKED review + READY gate → FAILED ✅

**结论**：PASS。

### 5. compiler 拒绝 lineage/ref/outcome 不一致

**对照点**：错误 gate、错误 ref、错误 iteration、空 note 均在 compile 被拒。

Gate 匹配：`ContractCompileHandler.java:448-449` — `subjectRefs.contains(reviewRef)` 不变 ✅
Iteration 匹配：`ContractCompileHandler.java:319-326` — nodeBelongsToIteration 不变 ✅
Decision + source_outcome 匹配：新增 `eligibleBlocked` 三条件 ✅
Note 非空：`eligibleBlocked` 中包含 `!gate.note().isBlank()` ✅
review/proposal 节点迭代归属：不变 ✅

**结论**：PASS。所有不一致组合均 fail closed。

### 6. 三个 bundled workflow BLOCKED edges

- `agent-lifecycle.terminal-delivery.json`：v1→v2，edges 45→48 (+3 BLOCKED) ✅
- `agent-lifecycle.contract.json`：v5→v6，+3 BLOCKED ✅
- `agent-lifecycle.full.json`：v6→v7，+3 BLOCKED ✅

版本同步验证：
- `LifecycleWorkflowDefinitionFactoryTest`：full v7、contract v6、terminal-delivery v2 ✅
- `StartCommandVariantTest`：同上 ✅
- `WorkflowDefinitionLoaderTest`：同上 ✅
- `WorkflowPrintDefaultCommandTest`：full v7 ✅
- `TerminalDeliveryWorkflowDefinitionTest`：edges 45→48 ✅

**结论**：PASS。sibling sweep 完整，所有 loader/CLI 测试同步。

### 7. acceptance exclusion

**对照点**：acceptance.review contract 不产生 BLOCKED；acceptance.decision catalog 中 BLOCKED 不激活路径。

- acceptance review contract（`ContractRegistry.java:70`）：decision=`ACCEPT|ACCEPT_WITH_NOTES|REJECT`，**不含 BLOCKED** ✅
- acceptance decision contract（`ContractRegistry.java:124`）：含 BLOCKED（前向预留），但无 workflow edge ✅
- `StageOutcomeCompiler.java`：BLOCKED 需要 blocking evidence；acceptance review 不允许 blocking=true（line 85-91）— 双重阻断 ✅
- 设计 v2 review 的 P1-1 建议在文档中显式记录此 invariant，但实现层面不可达 ✅

**结论**：PASS。acceptance 路径不可达。

### 8. UI + API + CLI + autorun 等价

| 入口 | note 来源 | BLOCKED enforcement | 默认 note 绕过？ |
|------|-----------|---------------------|-----------------|
| CLI `review approve --note` | `ReviewApproveCommand.note` (新) | resolveOpenGate | 否（空 → fail） |
| CLI `review decide --action approve --note` | `ReviewDecideCommand.note` (已有) | resolveOpenGate | 否（默认从 "decided from CLI" 改为 ""） |
| API `POST /review/approve` | `ReviewApproveRequest.note` (新) | resolveOpenGate | 否 |
| Management UI approve | `els.feedback.value` | resolveOpenGate + UI 前置检查 `sourceOutcome==="BLOCKED" && !note` | 否（UI BLOCKED 检查 + resolveOpenGate 双重） |
| Operator console approve | `els.revisionFeedback.value` | resolveOpenGate + UI 前置检查 | 同上 |
| Autorun | BLOCKED 时停止，不调用 resolveGate | `requiresHumanDecision()` 在 resolveGate 前拦截 | BLOCKED 不到达 resolveGate ✅ |

统一 enforce point：`RoutingEngine.resolveOpenGate()` (line 342-346) — 所有 5 条路径最终经过同一个 BLOCKED note check ✅

`ReviewDecideCommand.java:26`：默认 note 从 `"decided from CLI"` 改为 `""` — 确保 BLOCKED gate 上不写 note 会 fail ✅

**结论**：PASS。

---

## 4. Sibling-Path Sweep

| 路径 | 检查 | 状态 |
|------|------|------|
| 历史/已解析 gate 编辑 | `requireOpenGate` 只匹配 `status="open"` gate（line 380） | ✅ |
| 旧 output ref 绕过 | subject_input_refs 来自 `RoutingEngine:165` = 当前 decision node 的 inputRefs | ✅ |
| direct ledger mutation | resolveOpenGate 读 gate→验 note→resolve→completeAndRoute，无裸 JSON 写旁路 | ✅ |
| raw text/node-id/decision 推断 | `HumanReviewGateHandler` 只从 `GateContext.sourceOutcome` 读，有 `assertBlockedSourceMatchesReview` cross-check | ✅ |
| decision node id 推断 | `assertBlockedSourceMatchesReview()` (line 44-51)：源为 BLOCKED 时验证 reviewer sidecar decision 也是 BLOCKED | ✅ |
| acceptance path | 无 BLOCKED edge，review contract 不含 BLOCKED | ✅ |
| revision limit → NodeStatus.BLOCKED | `RunningStateGuardTest.java:499` 保留，`stopOnNonCompletedNode` 保留 | ✅ |
| `ReviewReviseCommand` + `WorkstreamActionService.revise` | 未变更，仍传 feedback 作为 note | ✅ |

---

## 5. End-to-End Invariant

| 环节 | 状态 | 代码锚点 |
|------|------|----------|
| **Declared by** | ✅ | `AgentOutputContractDocument.decision()="BLOCKED"` + `workflow.json` BLOCKED edge |
| **Proven by** | ✅ | `node-intake-decision-020.json` decision=BLOCKED (v6 ledger) |
| **Consumed by** | ✅ | `RoutingEngine:190` source_outcome → `GateContext:16` → packet:86 → UI JS + compiler:313-320 |
| **Enforced before** | ✅ | `RoutingEngine:342-346` note enforcement → `ContractCompileHandler:316-320` eligibility |
| **Failure route** | ✅ | IOException → gate open → WAITING_FOR_HUMAN → 不进 recovery |
| **Cannot-bypass test** | ✅ | `WorkflowGateResolutionTest:blockedSourceCannotAdvanceWithoutHumanDecisionNote` + `ContractCompileHandlerTest:blockedReviewCannotUseReadyGateAsApproval` + `AutoRunCommandTest:blockedReviewSourceCannotBeAutoApproved` + UI JS client-side guard |
| **Residual raw/legacy** | ✅ | READY gate 不变；stopOnNonCompletedNode 保留 |

---

## 6. Owner 与 SoT 判定

| 事实 | Owner | 实现验证 |
|------|-------|----------|
| review=BLOCKED | `AgentOutputContractDocument.decision()` | `ReviewDecisionHandler.java:52-56` 只读不写 ✅ |
| gate.source_outcome | `GateDocument.result.get("source_outcome")` | `RoutingEngine.java:190` 唯一写入点 ✅ |
| human note | `GateDocument.result.note` → `resolveOpenGate` note param | 5 条路径同一 enforce point ✅ |
| review packet projection | `HumanReviewGateHandler.writeReviewPacket` | 从 GateContext.sourceOutcome 投影，禁止重推断 ✅ |
| contract-resolution | `ContractCompileHandler.blockedReviewQuestions` | 从 sidecar must_fix/missing_evidence 逐项映射 ✅ |

---

## 7. 测试可信度

### 直接验证（cannot-bypass proof）
| 测试 | 证明内容 |
|------|----------|
| `blockedSourceCannotAdvanceWithoutHumanDecisionNote` | 空 note → 拒绝，gate/node/workflow 不变 |
| `blockedReviewApprovedWithHumanNotePreservesTypedQuestions` | must_fix/missing_evidence → ResolvedQuestion + human note |
| `blockedReviewCannotUseReadyGateAsApproval` | BLOCKED review + READY gate → FAILED |
| `reviewDecisionTreatsBlockedAsCompletedBusinessOutcome` | status=COMPLETED, outcome=BLOCKED, outputRefs preserved |
| `blockedReviewSourceCannotBeAutoApproved` | autorun 在 BLOCKED gate 停止 |

### 定义同步验证
- 3 workflow variants: version bump + edge count + loader/CLI 测试 ✅

### Full reactor
- 未运行（实现尚未提交）。设计 §8 要求在提交后运行。

---

## 8. 选择性提交条件

**满足**。38 个文件形成完整原子变更集，不可拆分：

- 单独交 ReviewDecisionHandler 不加 edge → BLOCKED 无处去
- 单独交 edge 不加 source_outcome → note enforcement 和 compiler 盲
- 单独交 compiler 不加 note enforcement → 空 note 可绕过

---

## 9. Fresh Captcha-Auth Completion Gate 条件

**条件满足**，但须等：
1. 本次实现提交并 code review 通过
2. full reactor 通过
3. deterministic replay（v6 artifacts）通过 → 标 `ENFORCED`
4. 然后创建 fresh completion workstream → `PROVEN`

当前 maturity：**ENFORCED（via deterministic tests）/ NOT PROVEN（no fresh human-gate real-case）**

---

## 10. 提交前建议

### S-1：acceptance contract 差异文档化
**文件**：`ContractRegistry.java:124`
`acceptance.decision` catalog 含 BLOCKED 但 workflow 无对应 edge。建议在 `ContractRegistry` 类
Javadoc 或设计文档 §3.2 显式注明此差异及其不可达性。

### S-2：StageOutcome 保留确认
`StageOutcome.java:15` 仍定义 `BLOCKED` 枚举，`StageOutcomeCompiler.java:93-94` 仍检查
BLOCKED 的 evidence 要求。不受本 diff 影响，不必修改。建议在 review 总结中确认已检查。

### S-3：clean build 确认
38 文件全部为 Maven 编译路径内文件；建议提交前 `mvn compile` 确认无引用断裂。
