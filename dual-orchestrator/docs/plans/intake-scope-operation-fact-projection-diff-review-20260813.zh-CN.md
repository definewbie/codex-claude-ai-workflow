# Intake ScopeOperation Fact Projection — 独立只读 diff review

日期：2026-08-13
分支 / HEAD：`codex/recovery-origin-requirements` @ `86c78cc2`（实现未提交）
审查对象：未提交的 working-tree diff（production + tests + 共享 fixture + 计划文档）
方法：逐文件读 diff 与真实调用链，独立复跑 `mvn clean test`、解析 surefire reports、`git diff --check`。未修改/提交文件，未运行 provider/probe/real-case。

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 | 0 |
| P1 | 0 |
| P2 | 3（均非阻塞，见 Findings） |
| **Boundary Review** | **PASS** |
| 选择性提交条件 | **满足** |
| 成熟度 | **可标 `ENFORCED / NOT PROVEN`** |
| 下一版 captcha-auth Gate 条件 | **具备** |

---

## 1. Findings（P0 / P1 / P2，附真实 file:line）

### P0 — 无

### P1 — 无

### P2-1（新错误码 `TASK_CONTRACT_SCOPE_RESOLUTION_FAILED` 改变了 initial scope-shape 失败的可观察表面）

- **位置**：`ArtifactContractValidator.java:164-168`（`TASK_CONTRACT_SCOPE_RESOLUTION_FAILED`）。
- **现象**：v9 之前，所有 scope 失败（空、blank、duplicate、`new:` 前缀、non-canonical、workspace 越界、directory）都经 durable validator 产出 `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`。现在 resolver 先拦截 shape 失败并产出新码 `TASK_CONTRACT_SCOPE_RESOLUTION_FAILED`，`TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED` 收缩为「operation 与 filesystem 相反」的篡改守卫专属。
- **为何非阻塞**：全仓 grep 确认该新码与旧码只在 `ArtifactContractValidator.java:165/181/198/207/215` 与 `ContractCompileHandler.java:184` 这些生产现场被引用，无任何 classification / recovery / arbiter / routing 按具体 code 字符串分支（recovery 由 node `FAILED` 状态触发，非 error code）。二者均为 fail-closed typed artifact contract violation。这是良性的表面变化，建议在 active plan 的 failure-route 描述中记录这一拆分，便于后续 gate 证据比对。

### P2-2（resolver 的空/null 守卫在两条生产路径上均不可达，属防御性死分支）

- **位置**：`TaskContractScopeResolver.java:20-22`（`declaredPaths == null || isEmpty()` → `scope.included must not be empty`）。
- **现象**：initial 路径上，`AgentResultEnvelopeValidator` 的 `requiredList(proposal.scope().included(), "result.scope.included", true, errors)`（`AgentResultEnvelopeValidator.java:120`）先于 resolver 拒绝空 scope（产出 `AGENT_RESULT_CONTRACT_VIOLATION`）；revision 路径上，merger 的 `if (!scopeAdditions.isEmpty())`（`TaskContractProposalRevisionMerger.java:117`）保证 resolver 只对非空集合调用。因此该空守卫在两条生产链上都不会触发。
- **为何非阻塞**：作为 package-private 类的公开 `resolve` 方法，空守卫是面向未来调用方/鲁棒性的防御，无副作用、无错误行为。可保留，也可注释标注「当前两个生产调用点均在外部保证非空」。

### P2-3（revision 的 scope 错误字段名仍报 `result.scope.included.path`，属 pre-existing 未变）

- **位置**：`AgentResultEnvelopeValidator.java:357`（`requiredText(entry.path(), "result.scope.included.path", errors)`），被 initial（`:122`）与 revision（`:242`）共享。
- **现象**：revision 的 `scope_additions` blank path 会报 `result.scope.included.path`，而非更准确的 `result.obligation_patches[].scope_additions.path`。本次 diff 把方法签名从 `List<ScopeEntry>` 收窄为 `List<ProposalScopePath>`，但该字段名字符串未动。
- **为何非阻塞**：pre-existing（旧代码同字符串），纯诊断文案问题，不改变 fail-closed 语义。可后续机会性把字段名按调用上下文参数化。

---

## 2. 12 项核实 + 特别边界

### 1. initial 与 revision 均只接受 exact path — **确认**
- `AgentResultEnvelope.ProposalScopePath(String path)` 仅 `path`（`AgentResultEnvelope.java:66`）；`ProposalScope.included → List<ProposalScopePath>`（`:63`）；`ObligationPatch.scopeAdditions → List<ProposalScopePath>`（`:92`）。
- prompt 文案改为「Dual derives CREATE/MODIFY from the effective worktree」（`AgentResultPromptContract.java:78/90`），示例只用 `ProposalScopePath`（`:188/198-199`）。

### 2. `TaskContractScopeResolver` 是唯一 live production producer — **确认**
- 全仓 `new TaskContract.ScopeEntry` 生产现场仅两处：resolver（`TaskContractScopeResolver.java:50-54`，派生 operation）与 `AgentOutputContractDocument.java:231`（durable sidecar 反序列化，read-model，非事实 owner）。
- 生产 `Files.exists → ScopeOperation` 仅 resolver（`:52`）；`TaskContractProposalValidator.java:372/375` 读 operation 与 filesystem 比对，是 guard 非 producer。

### 3. initial 链顺序 — **确认**
`ArtifactContractValidator.validateAgentOutput`：`:92` parser → `:99` envelope validator → `:161` resolver → `:169` projector → `:195` durable proposal validator → `:272` sidecar 写盘。顺序与计划完全一致。

### 4. revision 链同 resolver；空 `scopeAdditions` 合法；existing/new → MODIFY/CREATE — **确认**
- `TaskContractProposalRevisionMerger.java:114` 创建同一 `TaskContractScopeResolver`，`:118` 对每个非空 `scopeAdditions` 调 `resolve`。
- `:117` `if (!scopeAdditions.isEmpty())` 保证 clause-only 合法 revision 不触 resolver（特别边界）。
- existing→MODIFY / absent→CREATE 由 `TaskContractScopeResolverTest.resolvesOnlyDeclaredPathsFromTheEffectiveWorktree` 与 `existingRevisionScopeAdditionResolvesToModify` 证明。

### 5. merger 去重键 path-only；previous 不重算 — **确认**
- `:58` `scopeTargets.add(entry.path())`（收敛为 path-only）。
- `:111` `scope = new ArrayList<>(List.copyOf(previous.scope().included()))` 字节语义原样保留；`:123` 仅追加 resolver 产物，历史 operation 不重算。

### 6. projector 旧旁路已删除 — **确认**
- 旧 `taskContractProposalMap(TaskContractProposalResult)` 已删；新 `taskContractProposalMap(TaskContractProposal)` 只吃 durable proposal（`AgentResultDocumentProjector.java:111-134`）。
- `outputContract` 对 proposal/revision 在 `resolvedProposal == null` 时抛 `IllegalStateException`（`:50-52/57-59`），杜绝把 Agent 原始 scope 直接投影。全仓仅 `ArtifactContractValidator.java:240` 一处生产调用且传入 resolved proposal。

### 7. strict parser 继续拒绝旧 shape，无 ignore/fallback/纠错 — **确认**
- `AgentResultEnvelopeParser.java:18` STRICT_DUPLICATE_DETECTION、`:22` FAIL_ON_UNKNOWN_PROPERTIES、`:23` FAIL_ON_TRAILING_TOKENS。
- 新测试 `AgentResultPromptContractTest.intakeOldScopeOperationShapeIsRejectedAsUnknownInput`（`:151-160`）注入 `operation` 后断言 parse 失败。

### 8. durable `ScopeEntry(path, operation)` 不变；validator 独立拒绝篡改 — **确认**
- `TaskContract.ScopeEntry`/`ScopeOperation`（core）未进 diff；`TaskContractProposalValidator.java` 仅加 1 行注释（`:369`），`:372/375` 守卫逻辑原封不动。
- `TaskContractScopeResolverTest.durableValidatorStillRejectsTamperedResolvedOperation`（`:55-70`）证明篡改的 `MODIFY` 在不存在文件上仍被 durable validator 拒绝。

### 9. sibling sweep：无第二个 owner / 无旧 Agent-facing fixture / 无 raw JSON projection / 无绕过 validator 的 sidecar producer — **确认**
- 生产 `new ScopeEntry` 仅 resolver + durable 反序列化（见 2）。
- 全部 `operation` 承载 JSON 均为 durable 形态（`artifacts/task-contract.json`、`memory/workstream-memory.json`、context-package 渲染），无 Agent-facing 旧 shape 残留。唯一「旧 shape」Agent-facing 样本是刻意的拒绝测试（见 7）。
- durable deserialization（`AgentOutputContractDocument.scopeEntries`）未被误判为新事实 owner，符合计划豁免。

### 10. 无 inventory / repo-map 扫描 / retry / bounded correction / workflow / module / registry / engine 扩张 — **确认**
- diff 仅新增一个 package-private 小类 `TaskContractScopeResolver`，收窄 Agent-facing 类型，wiring 两条链，更新 prompt/tests/1 个 fixture 脚本。`git diff --stat` 无任何 workflow JSON、无新 module、无 registry/engine 变更。

### 11. `emit-strict-agent-result.py` path-only 正确；durable fixtures 正确保留 — **确认**
- `scripts/fixtures/emit-strict-agent-result.py:59-62` `task_proposal()` 的 `included` 只余 `{"path":...}`，去掉了 operation。
- durable `operation` fixtures 正确保留：Java 测试里的 `artifacts/task-contract.json`、`workstream-memory.json`、context 渲染，以及 `scripts/smoke-test.sh:431-434/509` 对 durable `task-contract.json` 的 `path+operation` 断言均未改。`smoke-test.sh:716-740` 先 `git init` 并 seed `main.py`/`README.md` 再 intake，resolver 会派生 MODIFY，故其 durable 断言在 path-only 化后仍成立。

### 12. TOCTOU 声明诚实 — **确认**
- `TaskContractProposalValidator.java:369` 注释为「Resolution and this durable guard run synchronously against the same effective worktree.」措辞是「synchronously + same effective worktree」，未夸大为 filesystem transaction。resolver 与 durable validator 在同一个 `resolveProposalCandidate` 同步调用内消费同一 `root`（`root = workspaceRoot.toAbsolutePath().normalize()`，`:78`；resolver 内部再次 `.toAbsolutePath().normalize()`，`TaskContractScopeResolver.java:18`），单节点、无 Agent 工具并发。理论窗口存在但未夸大。

### 特别边界：clause-only revision 不被 resolver 误拒绝 — **确认**
- merger `:117` 空集合守卫保证 resolver 不接触 clause-only revision。
- 既有测试 `sectionIsAuthoritative...`（空 `scopeAdditions`，断言 `valid()`）与 `unknownReplaceTarget...`、`REVISION_BASE_PROPOSAL_ID_MISMATCH`（均空 scope，只由 clause 逻辑决定 valid/invalid）覆盖该路径；新增 `existingRevisionScopeAdditionResolvesToModify` 覆盖非空 scope 的 revision。

---

## 3. End-to-end invariant（7 维，锚定实现）

| 维度 | 证据 |
|---|---|
| **Declared by** | `ProposalScopePath(String path)`（`AgentResultEnvelope.java:66`）+ 唯一 producer `TaskContractScopeResolver`（`TaskContractScopeResolver.java:13`）；durable `TaskContract.ScopeEntry(path, operation)` 不变 |
| **Proven by** | `TaskContractScopeResolverTest`（3 条）+ `ArtifactContractValidatorTest.capturedV9NewPathsResolveToCreateBeforeSidecarPublication`（sidecar 全 CREATE）+ `existingPathResolvesToModifyBeforeSidecarPublication` + `durableValidatorStillRejectsTamperedResolvedOperation` + `intakeOldScopeOperationShapeIsRejectedAsUnknownInput` |
| **Consumed by** | 仅两处：`AgentResultDocumentProjector.taskContractProposal(result, resolvedScope)`（`:144-162`）与 `TaskContractProposalRevisionMerger.merge(context, patch, root)`（`:114-123`） |
| **Enforced before** | resolver 在 durable projection 前（`ArtifactContractValidator.java:161-169` initial / `:177-185` revision），先于 durable proposal validator（`:194-201`）、reviewer dispatch、compile |
| **Failure route** | resolver invalid → `TASK_CONTRACT_SCOPE_RESOLUTION_FAILED`（`:164-168`）；durable 篡改 → `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`（`:196-200`），均 fail-closed 写 diagnostic |
| **Cannot-bypass test** | `durableValidatorStillRejectsTamperedResolvedOperation` + `intakeOldScopeOperationShapeIsRejectedAsUnknownInput` + sibling sweep（生产 `ScopeEntry` producer 仅 resolver + durable 反序列化） |
| **Residual raw/legacy path** | 仅 `AgentOutputContractDocument.scopeEntries`（`:231`）durable 反序列化与 durable task-contract / workstream-memory test fixtures（豁免，保留）；无 Agent-facing 原始 scope 进入 durable projection（projector `IllegalStateException` 兜底） |

---

## 4. 选择性提交条件 — 满足

diff 严格自包含于单一原子 checkpoint：
- production 7 改 + 1 新：`AgentResultEnvelope`、`AgentResultEnvelopeValidator`、`AgentResultPromptContract`、`AgentResultDocumentProjector`、`ArtifactContractValidator`、`TaskContractProposalRevisionMerger`、`TaskContractProposalValidator`（1 行注释）、`TaskContractScopeResolver`（新）。
- tests 5 改 + 1 新：`AgentResultPromptContractTest`、`ArtifactContractValidatorTest`、`ContractCompileHandlerTest`、`TaskContractProposalRevisionMergerTest`、`TaskContractScopeResolverTest`（新）。
- 共享 fixture 1：`emit-strict-agent-result.py`；文档 2 改 + 2 新（plan / review）。
- 无 workflow JSON、无 module、无 registry/engine 变更；`git diff --check` clean；full reactor green。可作为一个不可再拆的原子提交。

## 5. 成熟度 — `ENFORCED / NOT PROVEN` 可标

- deterministic 证据：full reactor **1223/0/0/0**（独立复跑，模块和 = 105+149+12+507+314+5+75+56）、`git diff --check` clean、本 diff review PASS。
- `NOT PROVEN`：尚未运行 fresh captcha-auth real-case（不主动运行，符合约束）。

## 6. 下一版 captcha-auth completion Gate 条件 — 具备

可基于本 review PASS + 提交后的 clean SHA 制定下一 Gate。其 Preflight And Execution Order 已就绪：commit 得 clean SHA → fresh package 记录 JAR hash/mtime → Git/workflow/JAR/target/PostgreSQL non-provider preflight → 短生命周期双 provider probes → 全部 preconditions PASS 后才创建且仅创建一个 fresh terminal-delivery@3 workstream。fresh workstream 不得在 scope-projection 边界提交 + clean SHA + preflight 全 PASS 前创建。

---

*本报告为只读 diff 审查证据；未修改、未提交任何 production 文件。*
