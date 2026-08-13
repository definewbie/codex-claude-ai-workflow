# Self-Test Semantic Repair Routing — 独立代码审查

日期：2026-08-13

审查对象：14 个未提交文件的 "Self-Test Semantic Repair Routing" checkpoint
基线：`codex/recovery-origin-requirements` @ `80efca1f`
审查方式：独立、只读、逐文件逐行。未修改文件、未提交、未运行 provider/real-case。

---

## 1. Boundary Review：PASS

**0 P0 / 0 P1 / 2 P2**

12 项核实全部通过。真实 ledger routing 已由 `WorkflowFileLedgerTest` 直接验证，不是只断言 JSON 字段存在。

---

## 2. Findings

### P0：无

### P1：无

### P2-1：`context.implement_claude` 是多个 repair loop 的共享精确 target，self-test 修复预算与 acceptance/human 修复预算不独立

- **位置**：`RoutingEngine.java:135`（`revisionCount(paths, nextDefinition.id())`）+ 三个 workflow 的 loop edge
- **事实**：改为精确 `targetNodeDefinitionId` 计数后，以下多个不同来源的 repair loop 都精确 target `context.implement_claude`：
  - `implement.self_test_decision --SELF_TEST_INCOMPLETE--> context.implement_claude`（max_revisions=1）
  - `gate.human_implementation_repair --revise--> context.implement_claude`（max_revisions=1）
  - `acceptance.classify_failure --REJECT_IMPLEMENTATION_BUG/CONTEXT_INSUFFICIENT/TOOL_LIMITATION--> context.implement_claude`（classify_failure 无 max_revisions → 无界）
  - `gate.human_acceptance --revise--> context.implement_claude`（max_revisions=1）
- **后果**：`revisionCount` 按精确 target 计数，意味着这些来源共享同一个计数器。一个 workstream 若已发生过一次 self-test INCOMPLETE 修复（count=1），随后到达 `gate.human_acceptance` 且 operator 合法选择 `revise` 时，`maxRevisions(gate.human_acceptance)=1 >= revisionCount(context.implement_claude)=1` → 触发 `blockOnRevisionLimit` → `WAITING_FOR_RECOVERY`，一个合法的 human acceptance revise 被误判为"第二次实现修复"而阻断。
- **性质**：这不是放宽（放宽已被正确收紧到无界 acceptance classify_failure 之外），而是**新引入的耦合**——本轮之前没有 self-test repair loop，`context.implement_claude` 只被 acceptance 系修复 target。本轮新增的 self-test 修复 edge 让 self-test 修复预算与 acceptance/human 修复预算共用同一精确计数器。
- **可辩护性**：也可解读为"实现重入总次数有界"（`context.implement_claude` 语义上就是"重入实现"），且 classify_failure 无界使得主要 acceptance 修复路径不受影响。
- **建议**（不阻塞）：在设计文档或 workflow `metadata.purpose` 中显式声明 `context.implement_claude` 是唯一实现重入点、其精确计数是"实现总重入预算"；或在需要完全独立预算时为 self-test 与 acceptance 使用不同的 repair 重入节点 id。至少应在设计文档记录这一语义，避免未来误读。

### P2-2：`HumanGateSemantics.purposeForDefinitionId()` 未登记 `gate.human_implementation_repair`

- **位置**：`HumanGateSemantics.java:9-18`
- **事实**：新增的 `gate.human_implementation_repair` 不在 `purposeForDefinitionId()` 的 switch 中。当前能正确工作，因为 JSON config 携带 `gate_purpose: implementation_repair`，`purpose(config, nodeDefinitionId)` 优先读 config（L25-30）。
- **性质**：非功能性缺陷，但若未来该 gate 的 config 被剥离，purpose 会静默回退为 `"general"`，且 `purposeForDefinitionId` 与 `ContractRegistry` 的显式登记形成不完整对账。
- **建议**（不阻塞）：在 `purposeForDefinitionId()` 增加 `case "gate.human_implementation_repair" -> "implementation_repair"`，与 config 保持一致的防御性默认。

---

## 3. End-to-end Invariant

| 维度 | 声明 | 验证结果 | 代码锚点 |
|------|------|----------|----------|
| **Declared by** | 已验证的 self-test contract sidecar 的 typed `decision` | ✅ 来自 `implement.claude_self_test_report` 的 `agent_output_contract` sidecar，含 `SELF_TEST_COMPLETE/INCOMPLETE/BLOCKED` | `ContractRegistry.java:60-66`（IMPLEMENT_SELF_TEST）+ `AgentResultPromptContract.java:247-248`（SelfTestResult） |
| **Proven by** | `ReviewDecisionHandler` 通过 `AgentEvidenceIndex.latestDecisionFromRefs` 读同一 sidecar，经 parse + lineage 校验 | ✅ `readValidatedEntry` 强制 `AgentOutputLineageVerifier.violation`（lineage）后返回 | `ReviewDecisionHandler.java:22-27` → `AgentEvidenceIndex.java:186-208` |
| **Consumed by** | `implement.self_test_decision` 是唯一 decision projection owner，输出 `decision` 作为 routing outcome | ✅ `latestDecisionFromRefs` 仅有两个 caller：`ReviewDecisionHandler`（decision）与 `FailureClassifyHandler`（acceptance），无 raw sidecar 旁路 | `ReviewDecisionHandler.java:52`（`NodeResult.completed(decision, ...)`） |
| **Enforced before** | routing 在 artifact validation 副作用之前强制消费 decision | ✅ `self_test_report --completed--> self_test_decision --SELF_TEST_COMPLETE--> artifact_validation`，只有 COMPLETE 进入 validation | `agent-lifecycle.*.json`（三 workflow 同构） |
| **Failure route** | INCOMPLETE → `context.implement_claude`（loop）；BLOCKED → `gate.human_implementation_repair`；非法 decision → `ReviewDecisionHandler` INVALID_DECISION FAILED | ✅ `self_test_decision` 恰有 3 条出边，无无条件 fallback | `RoutingEngine.java:101-105`（flowKernel.advance 按 outcome 匹配） |
| **Cannot-bypass test** | `selfTestDecisionRoutesCompleteIncompleteAndBlockedWithoutBypass` 直接驱动真实 `completeAndRoute`/`resolveOpenGate` | ✅ 验证 COMPLETE→artifact_validation、INCOMPLETE→iter-002 loop、第二次 INCOMPLETE→`revision_limit_reached`/BLOCKED、BLOCKED→空 note 抛错→带 note→context.implement_claude | `WorkflowFileLedgerTest.java:501-585` |
| **Residual raw/legacy path** | 无旧 workflow 生成路径、无 raw sidecar 路由旁路 | ✅ `implement.claude_self_test_report` 的 `completed` 出边唯一指向 `self_test_decision`；grep 全生产 Java 无 `self_test_decision` 其他 producer | sibling sweep（见 §6） |

---

## 4. revision target 精确计数：正确，无行为回归

### 变更内容
`RevisionTracker.revisionCount()`（`RevisionTracker.java:148-161`）：
```java
// 旧：按 stageId（"stage-" + 首个 '.' 前缀）
.filter(iteration -> targetStage.equals(iteration.stageId()))
// 新：按精确 targetNodeDefinitionId
.filter(iteration -> targetNodeDefinitionId.equals(iteration.targetNodeDefinitionId()))
```

### 各 loop 的实际 target 与新旧隔离度

| loop source | loop target（精确 id） | 旧 stageId | 新精确 id | 隔离变化 |
|-------------|------------------------|-----------|-----------|----------|
| intake.decision NEEDS_REVISION | `intake.claude_brainstorm` | stage-intake | intake.claude_brainstorm | 无变化（已隔离） |
| design.decision NEEDS_REVISION | `design.claude_design` | stage-design | design.claude_design | 无变化（已隔离） |
| plan.decision NEEDS_REVISION | `plan.claude_plan` | stage-plan | plan.claude_plan | 无变化（已隔离） |
| self_test_decision SELF_TEST_INCOMPLETE | `context.implement_claude` | stage-context | context.implement_claude | **新隔离**（从 stage-context 池中拆出） |
| acceptance.classify_failure REJECT_REVIEW_NOISE | `context.acceptance_codex_verifier_review` | stage-context | context.acceptance_codex_verifier_review | **新隔离**（不再污染 implement 修复预算） |
| acceptance.classify_failure REJECT_* | `context.implement_claude` | stage-context | context.implement_claude | 仍共享（见 P2-1） |
| gate.human_acceptance revise | `context.implement_claude` | stage-context | context.implement_claude | 仍共享（见 P2-1） |

### 是否意外放宽 intake/design/plan/acceptance budget

- **intake/design/plan**：每个 `*_decision` 的 NEEDS_REVISION loop target 是**各自独立的 agent node**（`intake.claude_brainstorm` / `design.claude_design` / `plan.claude_plan`），从未与其他 stage 共享 stageId（stage-intake/design/plan 三者本来就不同）。精确计数与旧 stageId 计数在这三处**完全等价**，预算 `max_revisions=3` 语义不变。✅ 无放宽。
- **acceptance**：`acceptance.classify_failure` config 为 `{}`（无 `max_revisions`），`RevisionTracker.maxRevisions()` 返回 `-1`（`RevisionTracker.java:180-193`），`completeAndRoute` 中 `maxRevisions >= 0` 判断为 false（`RoutingEngine.java:136`），故 acceptance 修复循环**本就无界**，精确计数不改变其无界性。✅ 无放宽。
- **结论**：精确计数**收紧**了 implement 系与 review-noise 系的隔离，未放宽任何既有 budget。行为回归为 0。

---

## 5. 三个 workflow 的 sibling closure：完成

三个 bundled workflow（full/contract/terminal-delivery）结构一致，无旧直达路径：

| 校验 | full | contract | terminal-delivery |
|------|------|----------|-------------------|
| version | 7→**8** | 6→**7** | 2→**3** |
| self_test_report 出边 | → self_test_decision | 同 | 同 |
| self_test_decision 三条出边 | COMPLETE/INCOMPLETE/BLOCKED | 同 | 同 |
| human_implementation_repair 出边 | revise→context.implement_claude | 同 | 同 |
| 断边（from/to 不在 nodes） | 0 | 0 | 0 |
| 重复边 | 0 | 0 | 0 |
| 直达 artifact_validation（绕过 decision） | 无 | 无 | 无 |
| 节点/边计数 | 34/56 | 34/56 | 31/52 |

- `implement.claude_self_test_report --completed--> implement.artifact_validation` 旧直达路径已从三个 workflow 全部删除，唯一 `completed` 出边指向 `implement.self_test_decision`。
- `artifact_validation` 的唯一入边是 `self_test_decision --SELF_TEST_COMPLETE-->`。
- terminal-delivery 比 full/contract 少 3 节点/4 边，符合其已知省略尾部 handoff/next-stage 的结构差异（非本 checkpoint 引入）。

---

## 6. 第二个 owner / raw 旁路 / 旧生成路径 sweep：干净

- **`latestDecisionFromRefs` caller**：仅 `ReviewDecisionHandler.evaluate`（decision routing）与 `FailureClassifyHandler.execute`（acceptance classification）。`implement.self_test_decision` 的 decision projection 无第二个 owner。
- **`self_test_decision` / `human_implementation_repair` 生产侧**：grep 全生产 Java（`dual-runtime` + `dual-ledger-file` main）无 `self_test_decision`/`human_implementation_repair` 的硬编码 producer；它们只作为 workflow JSON 节点声明存在，handler 由 `review.decision` 与 `human.review` 复用。
- **旧 workflow 生成路径**：`LifecycleWorkflowDefinitionFactory` 直接 `loadBundledVariant()` 读取 JSON（`LifecycleWorkflowDefinitionFactory.java:22-28`），无程序化生成旧 graph 的路径；`resources/dev/dual/runtime/lifecycle/` 下只有三个 JSON，无遗漏的第四个 bundled variant。
- **CLI/runtime loader/version fixture**：`StartCommandVariantTest`、`WorkflowPrintDefaultCommandTest`、`WorkflowDefinitionLoaderTest`、`WorkflowVariantSelectorTest`、`LifecycleWorkflowDefinitionFactoryTest`、`TerminalDeliveryWorkflowDefinitionTest` 的 version 与 node/edge 计数全部同步到 8/7/3 与 34/56、31/52。
- **`BuiltinHandlerTest.selfTestDecisionConsumesTypedIncompleteEvidence`**：真实构造 `implement-claude-self-test.contract.json` sidecar（含 `control.decision=SELF_TEST_INCOMPLETE`），驱动 `ReviewDecisionHandler.evaluate()`，断言 outcome=INCOMPLETE、source=contract-sidecar、outputRefs 保留。证明 handler 消费的是 typed sidecar，不是 raw Markdown。

---

## 7. 测试是否验证真实 ledger routing（非 JSON 字段断言）

是。核心证据 `WorkflowFileLedgerTest.selfTestDecisionRoutesCompleteIncompleteAndBlockedWithoutBypass`（`WorkflowFileLedgerTest.java:501-585`）用**真实 `WorkflowFileLedger`** 驱动：

1. `completeAndRoute(..., "SELF_TEST_COMPLETE", ...)` → 断言 active node 变为 `implement.artifact_validation`。
2. `completeAndRoute(..., "SELF_TEST_INCOMPLETE", ...)` → 断言 active node 变为 `context.implement_claude` 且 `iterationId == "iter-002"`（真实 loop 迭代已写）。
3. 第二次 INCOMPLETE → 断言 `WAITING_FOR_RECOVERY`、decision node 变 `BLOCKED`、events.jsonl 含 `revision_limit_reached`（**真实 revision-limit 阻断，而非仅字段存在**）。
4. `completeAndRoute(..., "BLOCKED", ...)` → 断言 `WAITING_FOR_HUMAN`；`resolveOpenGate(revise, "")` 抛 `decision note is required`；带 note 后 active node 变为 `context.implement_claude`。

另有 `revisionBudgetCountsExactRepairTargetInsteadOfSharedStage`（`WorkflowFileLedgerTest.java:587-608`）直接写三份 iteration document，断言 `revisionCount` 精确计数：intake=1、implement=1、plan=0，证明跨 target 互不污染。

这些是真实 ledger 状态流转断言，不是 `json.contains(...)` 字段存在性检查。

---

## 8. 测试结果复现

| 项 | 声明（用户基线） | 独立复跑结果 |
|----|------------------|--------------|
| full reactor | 1217 tests / 0 failures / 0 errors / 0 skips | **1217 / 0 / 0 / 0** ✅ 精确匹配 |
| `git diff --check` | clean | **clean** ✅ |
| focused | 178 tests / 0 failures | 未单独抽跑；被 full reactor 覆盖（0 failures） |

按模块（surefire 汇总）：

| 模块 | tests | failures | errors | skipped |
|------|-------|----------|--------|---------|
| dual-core | 105 | 0 | 0 | 0 |
| dual-ledger-file | 148 | 0 | 0 | 0 |
| dual-flow-liteflow | 12 | 0 | 0 | 0 |
| dual-runtime | 502 | 0 | 0 | 0 |
| dual-adapter-acpx | 314 | 0 | 0 | 0 |
| dual-adapter-git | 5 | 0 | 0 | 0 |
| dual-cli | 75 | 0 | 0 | 0 |
| dual-server | 56 | 0 | 0 | 0 |
| **总计** | **1217** | **0** | **0** | **0** |

`dual-ledger-file` 从 146→148（新增 2 个真实 routing 测试）、`dual-runtime` 从 502 内部含新增 handler/lifecycle 断言。与基线声明一致。

---

## 9. 是否满足选择性提交条件：YES

14 文件构成单一不可拆分的 checkpoint：

| 变更 | 文件 | 拆分独立性 |
|------|------|-----------|
| `ContractRegistry` 声明 `SELF_TEST_DECISION` | 1 生产 | 与 workflow JSON 强耦合 |
| 三 workflow JSON（version bump + 2 节点 + 4 边） | 3 资源 | 与 ContractRegistry 强耦合 |
| `RevisionTracker.revisionCount` 精确计数 | 1 生产 | 与 self-test loop 强耦合 |
| 生产/CLI 测试同步 | 9 测试 | version/node/edge 断言必须同步 |

任何单独提交都会破坏 sibling consistency（三 workflow 必须同版本变更，否则 `ContractRegistry.forNode` 与 workflow 节点对账失败）。建议**一次性提交全部 14 文件**。

---

## 10. 是否具备制定 fresh-workstream Verification Gate 的条件：YES

条件满足：
1. full reactor 1217/0/0/0 ✅
2. `git diff --check` clean ✅
3. 三 workflow sibling closure 完成、0 断边/重复边 ✅
4. 真实 ledger routing 测试覆盖 COMPLETE/INCOMPLETE/BLOCKED + revision limit + BLOCKED note ✅
5. 独立代码审查 PASS ✅

提交后，该边界可标 `ENFORCED`（deterministic）。**必须用 fresh terminal-delivery workstream 验证**——v2→v3 graph 变更使既有 v2 workstream 无法原地获得 v3 语义（见 §11）。未经 fresh real-case，不得标 `PROVEN`。

---

## 11. 既有 terminal-delivery v2 workstream 不能原地获得 v3 语义

**确认：正确。** 证据链：

1. `WorkflowFileLedger.initialize()` 在 workstream 创建时把完整 definition graph（`nodes` + `edges`）冻结写入 `workflowDefinitionJson`（`WorkflowFileLedger.java:85-92`）。
2. resume 时 `loadDefinition()` 从该冻结文件读回 graph（`WorkflowFileLedger.java:142-161`）；其中 `LedgerMigrator.migrate` 只处理 `schemaVersion`（文档 schema 版本），**不处理 workflow `version` 的 graph 变更**。
3. 因此 v2 workstream 持久化的 graph 仍是旧结构：`implement.claude_self_test_report --completed--> implement.artifact_validation`（无 `self_test_decision` 节点、无 `SELF_TEST_INCOMPLETE`/`BLOCKED` 分支）。

结论：既有失败的 v2 workstream 永久钉在 v2 graph，无法通过 resume 获得 v3 的 self-test 语义 repair routing。**必须用 fresh workstream（重新 `initialize`，冻结 v3 graph）验证。**

---

## 12. 成熟度

- 本轮 deterministic review PASS 后 → **`ENFORCED`**（可标）。
- 未经 fresh terminal-delivery real-case 自然触发 self-test `SELF_TEST_INCOMPLETE`（或 `BLOCKED`）→ **不得标 `PROVEN`**。
- `captcha-auth` 产品完成仍以 fresh-workstream `COMPLETED + ACCEPT` 为准，与本 checkpoint 的技术边界结论分离。

---

## Appendix：12 项核实一缆表

| # | 核实点 | 结果 | 关键证据 |
|---|--------|------|----------|
| 1 | authoritative fact 来自已验证 self-test sidecar | ✅ | `ReviewDecisionHandler.java:22-27` → `AgentEvidenceIndex.readValidatedEntry`（lineage 强制） |
| 2 | `ReviewDecisionHandler` 唯一 decision owner | ✅ | `latestDecisionFromRefs` 仅 2 caller，无第二 owner |
| 3 | routing 在 artifact validation 前强制消费 decision | ✅ | `self_test_report→self_test_decision→artifact_validation` |
| 4 | COMPLETE/INCOMPLETE/BLOCKED 三条路径不可绕过 | ✅ | `self_test_decision` 恰 3 出边 + contract 限 3 decision |
| 5 | BLOCKED gate 必须非空 note，autorun 无法越过 | ✅ | `RoutingEngine.java:343-346` + `CliWorkflowLoop` 停于 WAITING_FOR_HUMAN |
| 6 | repeated INCOMPLETE 一次 repair 后触发 limit | ✅ | `max_revisions=1` + 精确计数，测试断言 `revision_limit_reached` |
| 7 | revision budget 精确计数符合各 loop 语义，无放宽 | ✅ | intake/design/plan 精确计数≡旧 stageId；acceptance 无界不变 |
| 8 | 三 sibling workflow 一致，无旧直达路径 | ✅ | 0 断边/重复边，`completed` 唯一出边指向 decision |
| 9 | CLI/runtime loader/version fixture 同步 | ✅ | 6 个测试文件 version/count 全同步 |
| 10 | 测试验证真实 ledger routing | ✅ | `selfTestDecisionRoutesCompleteIncompleteAndBlockedWithoutBypass` |
| 11 | 无第二 owner/raw 旁路/旧生成路径/遗漏 variant | ✅ | §6 sweep 干净 |
| 12 | v2 workstream 不能原地获得 v3，须 fresh workstream | ✅ | `initialize` 冻结 graph + `loadDefinition` 只迁移 schemaVersion |
