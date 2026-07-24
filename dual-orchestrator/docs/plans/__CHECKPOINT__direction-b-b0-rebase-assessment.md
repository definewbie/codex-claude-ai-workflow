# __CHECKPOINT__ Direction B / B0 Rebase Assessment

> 标记：`__CHECKPOINT__`
>
> 状态：只读审计完成，不含 production 修改。
>
> 基线：branch `codex/recovery-origin-requirements`，HEAD `3fe084cc`。
>
> 审计日期：2026-07-24。
>
> Pre-existing dirty diff：8 modified + 3 untracked。
> - `docs/__REFERENCE__dual-cli-to-ledger-and-direction-b.zh-CN.md`（Direction B 架构参考文档）。
> - `dual-runtime/.../TaskContractProposalValidatorTest.java`（contract validator 测试）。
> - `docs/plans/__CHECKPOINT__direction-b-b0-rebase-assessment.md`（本文档，B0 checkpoint）。
> B0 新增：本文档。
>
> 目的：在 Direction B 实施前，从当前 worktree 重新追踪 read observation 如何影响
> NodeResult、contract violation、recovery；给出 P1 处置矩阵和 B1-first 判定。

---

## 1. 威胁模型决策

**决定：** 默认开发 profile 控制最终代码变更（write boundary），不控制读取。
post-run read policy 不构成安全隔离——Agent 在 worktree 中已经能看到所有文件，
事后让节点 FAILED 不能"取消"Agent 已读取的内容。

**含义：**
- read enforcement（ExplorationPolicyEvaluator 让节点因越界读取 FAILED）是优化治理，
  不是安全控制。
- 如果目标仓库含敏感信息，应由独立 execution profile 解决（worktree 隔离、OS/container
  sandbox、ACP/provider capability），不能把"事后节点失败"描述为读取安全。
- Direction B 的正确边界是：TaskContract.scope → GitEvidence → ChangeScopeEvaluation
  → acceptance/delivery 前阻断越界**修改**。

---

## 2. 当前快照 Authority Inventory

**所有结论从当前 worktree（HEAD `3fe084cc` + dirty diff）验证，不引用其他分支。**

### 2.1 Producer：read observation 如何变成 failure semantics

| 角色 | 文件:行 | 机制 |
|---|---|---|
| **Producer** | `AgentRunMetadataWriter.java:580` `explorationPolicyFailure(...)` | 读 `explorationPolicyResult.status=="failed"` → 构造 `AcpxFailure("EXPLORATION_POLICY_VIOLATION", ...)` |
| **Producer** | `AgentRunMetadataWriter.java:301` `topLevelFailure = failure == null ? policyFailure : failure` | 如果没有其他 failure，exploration policy failure 成为顶层 failure |
| **Producer** | `AgentRunMetadataWriter.java:424` `status = topLevelFailure == null ? "completed" : "failed"` | run.json status 变成 `"failed"` |
| **Producer** | `AgentRunMetadataWriter.java:446` `CompletionWriteResult.explorationPolicyFailed = policyFailure != null` | completion flag 传给 handler |

**关键：** `explorationPolicyFailure()` 使 read observation 直接影响 run.json `status=failed`。
在 Direction B 下，read observation 不得让 run.json status 变成 failed。

### 2.2 Authority consumer 1：AcpxAgentTaskHandler → NodeResult.FAILED

| # | 路径 | 文件:行 | 机制 |
|---|---|---|---|
| A1 | handler `completion.explorationPolicyFailed()` → FAILED | `AcpxAgentTaskHandler.java:267` | `if (completion.explorationPolicyFailed())` → `EXPLORATION_POLICY_VIOLATION` / `NodeStatus.FAILED` |
| A2 | handler `harness_failure.reason` | `AcpxAgentTaskHandler.java:438` | `facts.put("harness_failure.reason", "EXPLORATION_POLICY_VIOLATION")` → recovery facts |

### 2.3 Authority consumer 2：ArtifactContractValidator → contractViolation → NodeResult.FAILED

| # | 路径 | 文件:行 | 机制 |
|---|---|---|---|
| A3 | validator `refreshExplorationPolicyProjection` 调用 | `ArtifactContractValidator.java:211` | handler 返回候选 NodeResult → runtime `validate()` → 此方法 |
| A4 | validator → `EXPLORATION_POLICY_DENIED` | `ArtifactContractValidator.java:621` | `if (explorationPolicyFailed(refreshed))` → `contractViolation(...)` → FAILED |
| — | validator `explorationPolicyFailed` helper | `ArtifactContractValidator.java:635` | `return "failed".equals(text(result.get("status")))` — **不是独立 authority path**，是 A4 的辅助判定 |

### 2.4 Downstream consequences（不是独立 authority）

| 项 | 来源 | 性质 |
|---|---|---|
| recovery/current-failure.md | 从 handler facts 生成 | downstream consequence of A1/A2 |
| CLI/status projection | 从 run.json 读取 | downstream consequence of producer status=failed |
| report/gate failure | 从 run.json 读取 | downstream consequence |

### 2.5 当前分支不存在的类（MISSING — 不假设存在于 production）

| 类 | 状态 |
|---|---|
| `ExplorationEnforcementPolicy` | **MISSING** |
| `PythonAdjacentTestCandidateSelector` | **MISSING** |
| `AgentExecutionBudgetPolicy` | **MISSING** |
| `ExplorationReadCandidateSelector` | **MISSING** |

这些类来自其他分支的提交，不属于当前 production baseline。处置矩阵中不包含它们。

### 2.6 ArtifactValidateHandler 当前行为

- **EXISTS**：`dual-runtime/.../handler/ArtifactValidateHandler.java`
- **始终返回 COMPLETED**（`:76`），无论 Git 变更是否越界。
- 只收集 Git evidence；**没有** ChangeScopeEvaluation。

### 2.7 ArtifactContractValidator 的职责混淆

当前同时做三件事：
1. 验证 AgentResultEnvelope contract（合法职责）。
2. 刷新 exploration telemetry projection（`refreshExplorationPolicyProjection`）。
3. 把 exploration observation 升级成 contract violation（`EXPLORATION_POLICY_DENIED`）。

第 3 项：Agent 输出 contract 合法 + Agent 多读了一个文件 = `AGENT_OUTPUT_CONTRACT_VIOLATION`。
混淆了 output-contract-validity 和 read-governance-policy。

---

## 3. P1 处置矩阵

### 3.1 已提交代码（HEAD `3fe084cc`）

| 类/方法 | 当前职责 | 处置 | 理由 |
|---|---|---|---|
| `AcpxAgentTaskHandler:267` explorationPolicyFailed → FAILED | read authority | **DELETE** | Direction B: reads 不影响 node outcome |
| `AcpxAgentTaskHandler:438` harness_failure.reason = EXPLORATION_POLICY_VIOLATION | recovery facts | **DELETE** | 不再因 read violation 触发 recovery |
| **`AgentRunMetadataWriter:580` explorationPolicyFailure()** | **producer: read observation → run.json status=failed** | **DELETE failure projection** | read observation 不得让 run.json status 变成 failed |
| `AgentRunMetadataWriter` explorationPolicyResult 写入 | telemetry | **KEEP** | 保留 explorationPolicyResult 作为 observation |
| `ArtifactContractValidator:211/566-635` refreshExplorationPolicyProjection | read authority via contract violation | **DELETE 整段** | validator 只应验证 output contract |
| `ExplorationPolicyEvaluator` | produces status=failed/severity=block for reads | **DEMOTE** | 保留 observation 字段；B4 删 control 语义 |
| `RepoScopeGovernance` read-scope projection | produces allowedDirectReadFiles | **DEMOTE** | 保留 context recommendation；不再作为 read authorization |
| `ExplorationPlan` (allowedDirectReadFiles, failureMode) | read authorization | **DEMOTE** | 保留为 context guidance / telemetry |
| `TaskContract.ScopeEntry` / `ScopeOperation` | typed change intent | **KEEP** | Direction B change intent SoT |
| `ArtifactValidateHandler` | collects Git evidence, COMPLETED | **KEEP + EXTEND** (B2/B3) | B2 加 ChangeScopeEvaluation |
| provider/token/usage observation | telemetry | **KEEP** | 不受 Direction B 影响 |
| path normalization + traversal guards | project-neutral safety | **KEEP** | 不受 Direction B 影响 |

### 3.2 未提交 dirty diff（8 modified + 2 untracked）

| 文件 | diff 语义 | 处置 |
|---|---|---|
| `RepoScopeGovernance.java` (58 行) | contract scope → writeCandidates（Direction B 对齐）；readScope 改用 contractScopeCandidates | **KEEP write-scope 对齐部分**；read-scope 部分在 B1/B4 降级 |
| `TaskContractValidator.java` (40 行) | ScopeOperation (CREATE/MODIFY) validation | **KEEP** |
| `TaskContractTest.java` (22 行) | ScopeOperation tests | **KEEP** |
| `TaskContractProposalValidator.java` (12 行) | contract proposal validation improvement | **KEEP** |
| `ExplorationPolicyEvaluator.java` (2 行) | 空 allowedReads 从 skip 改为 fail-closed（**加强** read enforcement） | **REVERT** |
| `ContextBuildPackageHandlerTest.java` (129 行) | 混合 — **不整体 KEEP**，逐方法分类见 Section 3.3 | **按 Section 3.3 逐方法结论** |
| `ExplorationPolicyEvaluatorTest.java` (50 行) | 混合 — 见下表按方法分类 | **按方法分类** |
| `__ACTIVE__ plan doc` (22 行) | plan 文档更新 | **KEEP + 本次已更新** |
| `TaskContractProposalValidatorTest.java` (untracked) | proposal validator 测试 | **KEEP** |

### 3.3 ContextBuildPackageHandlerTest 逐方法处置（dirty diff 涉及的方法）

以下方法在当前 dirty diff 中被修改或新增断言（部分方法原本已存在，只是新增了断言）。

| 测试方法 | 处置 | 理由 |
|---|---|---|
| `unroutedHistoricalAgentEvidenceCannotExpandExplorationScope` | **REWRITE** | 断言 readCandidates 不能扩大（read authority）。Direction B: reads 自由；应改为断言不影响 writeCandidates/expectedEditFiles（change boundary）。 |
| `governedPackageWritesRepoScopeGovernanceArtifacts` | **REWRITE** | KEEP governance artifact generation 断言（ExplorationPlan、capability catalog 等存在）；KEEP expectedEditFiles/writeCandidates 断言；删除或改写 canonical contract → allowedDirectReadFiles / read authority 断言。Direction B 下改为验证 canonical contract → change intent projection。 |
| `governedStageScopePoliciesMatchStageBoundaries` | **KEEP** | 断言 `scopeExceedPolicy` 已删除 + `failureMode` 按阶段正确。这是 stage 边界 characterization（telemetry 正确性），不直接断言 read violation → FAILED。B4 可能更新 failureMode 断言。 |
| `governedIntakePackageKeepsRepoMapMatchesAsRecommendationsOnly` | **REWRITE** | 断言 "repo-map recommendations must not masquerade as canonical contract scope"（Direction B 对齐 → KEEP 这部分）但**同时**断言 `allowedDirectReadFiles`（read authority → REWRITE 为 change intent：assert writeCandidates/expectedEditFiles match contractScope）。 |
| `observedAndHeuristicFilesCannotExpandCanonicalReadScope` | **REWRITE** | 断言 observed/heuristic files 不扩大 canonical READ scope。Direction B: reads 自由；应改为断言不扩大 writeCandidates/expectedEditFiles（change boundary），不断言 read scope。 |
| `canonicalScopeIsNotTruncatedByRecommendationLimit` | **REWRITE** | 断言 `allowedDirectReadFiles` 不被 24-file recommendation limit 截断。Direction B: canonical scope 是 change intent；应改为断言 writeCandidates/expectedEditFiles 不被截断，不断言 read authorization。 |

**分类原则：**
- recommendation/context rendering → **KEEP**。
- governance artifact generation（工件存在性、expectedEditFiles/writeCandidates）→ **KEEP**。
- contract scope → `allowedDirectReadFiles` / read authority 的断言 → **REWRITE** 为 `writeCandidates`/`expectedEditFiles`（change intent）。
- heuristic/observed facts 不得扩大 change boundary → 改为断言 write scope，不断言 read scope。
- canonical scope 不截断 → 保留，但断言 change intent，不断言 read authorization。
- stage `failureMode` 对 read violation 的控制 → **KEEP** 作为 telemetry 正确性（B4 可能更新）。

### 3.4 ExplorationPolicyEvaluatorTest 按方法分类

| 测试方法 | 处置 | 理由 |
|---|---|---|
| `passesWhenIntakeUsesNoDirectReads` | **KEEP** | observation（no reads → pass） |
| `preservesActualCapabilityUsageProviderAndSource` | **KEEP** | observation |
| `warnsForIntakeImplementationLevelReadsBasedOnRepoMapKinds` | **DEMOTE** | warn 不变 authority（B1 后 warn 不驱动 FAILED） |
| `warnsWhenPreferredCapabilityWasAvailableButNotUsedBeforeBroadReads` | **DEMOTE** | 同上 |
| `blocksWhenIntakePerformsAnyDirectRead` | **REWRITE** | 断言 FAILED for reads → 改为 observation |
| `emptyCanonicalScopeAuthorizesNoDesignReads` | **REWRITE** | read authorization → observation |
| `emptyCanonicalScopeExpansion*` / `scopeExpansion*` / `intakeScopeExpansion*` | **REWRITE/DELETE** | read expansion authority |
| `designDirectReadBudgetExceededFailsInsteadOfWarns` | **REWRITE** | read budget → FAILED 改为 observation |
| `intakeDirectReadIsForbiddenEvenWhenRepoMapKindIsDocumentation` | **REWRITE** | read authority → observation |
| `acceptanceOutsideScopeReadFailsInsteadOfWarns` | **REWRITE** | read authority → observation |

---

## 4. 迁移不变量

| 领域事实 | 唯一 SoT | 不可被其他 owner 覆盖 |
|---|---|---|
| 任务意图：允许产生哪些代码变更 | `TaskContract.scope` (ScopeEntry + CREATE/MODIFY) | Agent 不能自我授权扩大 scope |
| 实际变更（Git 原始事实）：Git 仓库状态 | `git status --short` / `git diff` 原始输出 | LLM 自称的 changed_files 不替代 Git evidence |
| 实际变更（typed observation）：当前 git evidence bundle | `ArtifactValidateHandler` 收集的 `GitEvidence` + `changed-files.txt` + `git-status-short.txt` | observation，不是 compliance SoT |
| 范围符合性：intent vs actual 的权威判定 | `ChangeScopeEvaluation` (B2 产出) | 只有此判定可以阻断 acceptance/delivery |
| 流程状态：workstream 走到了哪里 | `Workflow Ledger` (current.json + events.jsonl) | Handler / validator / CLI 不自行判断流程状态 |

---

## 5. B1-first 四个判定门

| 问题 | 回答 | 依据（当前 worktree 验证） | 判定 |
|---|---|---|---|
| Q1: 当前是否存在工具调用前的 read interception？ | **否** | ExplorationPolicyEvaluator 是 post-run 评估。Agent 通过 Grep/Glob 能看到 worktree 内所有文件。 | ✅ PASS |
| Q2: 移除 node-failure mapping 后，read facts 是否仍完整记录？ | **是** | `AgentRunMetadataWriter` 写 `explorationPolicyResult` 到 run.json，独立于 handler 的 FAILED mapping。 | ✅ PASS |
| Q3: provider / typed output / artifact contract failure 是否仍然阻止节点成功？ | **是** | handler 的其他 failure path 独立于 exploration policy。 | ✅ PASS |
| Q4: 是否有 routing、recovery 或 projection 继续把 exploration violation 当成业务失败？ | **是，producer + 2 authority consumers** | Producer: `AgentRunMetadataWriter:580` 使 run.json status=failed。Consumer A1: handler:267。Consumer A3: validator:621。三条都必须在 B1 sweep 中清除。 | ⚠️ WARN（已知残余，B1 sweep 覆盖） |

**总判定：PASS for B1-first。** Q4 的残余是已知的、可清除的。

---

## 6. B2 Readiness 缺口

### GitEvidence 成熟度分层

| 层级 | 当前状态 |
|---|---|
| Git repository state（原始事实） | ✅ 可靠（`git status --short` + `git diff`） |
| 当前 git evidence bundle（observation） | ⚠️ 只收集 file paths，不解析 status codes 为 CREATE/MODIFY |
| typed `ActualChange(path, operation)` | ❌ 尚未实现 |

**B2 前不能称当前 GitEvidence 为完整 compliance SoT。** 当前 GitEvidence 是 observation，
不是 typed compliance result。

### 具体

- `GitEvidence` record (`ArtifactValidateHandler.java:343`)：字段 `changedFileCount`, `hasChanges` — **没有** file list 或 operation type。
- `changed-files.txt`：每行一个文件路径，**不含 operation code**。
- `git-status-short.txt`：原始 `git status --short` 输出，**含 status code**（A/M/D/R/??）— 信息可用但**未 typed**。
- B2 需要解析 status code → typed `ActualChange(path, operation)`。
- **不得**从 `changed-files.txt` 纯路径猜 operation。
- **不得**从 LLM 的 `changed_files` 声明替代 Git evidence。

---

## 7. B1 精确修改范围（不编码）

### 7.1 DELETE（authority mapping + failure producer）

| 位置 | 改动 |
|---|---|
| `AcpxAgentTaskHandler.java:267` | 删除 `if (completion.explorationPolicyFailed())` → EXPLORATION_POLICY_VIOLATION / FAILED 分支 |
| `AcpxAgentTaskHandler.java:438` | 删除 `facts.put("harness_failure.reason", "EXPLORATION_POLICY_VIOLATION")` |
| `AgentRunMetadataWriter.java:580` `explorationPolicyFailure(...)` | 删除 failure projection（不再从 read policy 构造 AcpxFailure） |
| `AgentRunMetadataWriter.java:301` `topLevelFailure = failure == null ? policyFailure : failure` | 删除 policyFailure 作为 topLevelFailure fallback |
| `AgentRunMetadataWriter.java:446` `CompletionWriteResult.explorationPolicyFailed` | 删除或恒 false |
| `ArtifactContractValidator.java:211` | 删除 `refreshExplorationPolicyProjection(...)` 调用 |
| `ArtifactContractValidator.java:566-635` | 删除 `refreshExplorationPolicyProjection` + `explorationPolicyFailed` helper 整段 |
| `ExplorationPolicyEvaluator.java:85` (dirty) | **REVERT** dirty diff（空 allowedReads fail-closed → 恢复原语义） |

### 7.2 DEMOTE（保留 observation，删 control）

| 位置 | 改动 |
|---|---|
| `AgentRunMetadataWriter` | 保留 `explorationPolicyResult` / `explorationPolicyResultPath` 写入 run.json（telemetry） |
| `ExplorationPolicyEvaluator` | B1 暂不改；B4 删 control 语义。B1 后无 consumer 将 status=failed 映射为 FAILED |
| `RepoScopeGovernance` | 保留 read-scope 投影为 context recommendation |
| `ExplorationPlan` | 保留字段为 telemetry |

### 7.3 Cannot-bypass tests（使用当前严格 JSON contract error codes）

```
1. agent reads out-of-scope file + valid agent_result artifact
   → node COMPLETED
   → explorationPolicyResult recorded in run.json (telemetry)
   → run.json status = "completed" (NOT "failed")
   → NOT FAILED for read

2. agent reads out-of-scope file + valid artifact + block-mode stage (design)
   → node COMPLETED
   → policy observation still recorded

3. agent result envelope missing
   → node FAILED (error: AGENT_RESULT_MISSING)

4. agent result envelope parse failure
   → node FAILED (error: AGENT_RESULT_PARSE_FAILED)

5. agent result contract violation (e.g. invalid decision/summary)
   → node FAILED (error: AGENT_RESULT_CONTRACT_VIOLATION)

6. provider/transport failure
   → node FAILED (runtime boundary preserved)

7. ArtifactContractValidator.validate() with valid artifact + exploration policy failed
   → returns valid (no EXPLORATION_POLICY_DENIED)
   → validator only checks output contract

8. grep sweep: no production path maps explorationPolicyResult.status to NodeResult.FAILED
   or to run.json status="failed"
```

### 7.4 Sweep checklist

- [ ] grep `EXPLORATION_POLICY_DENIED` → only in tests/history, not in production authority path
- [ ] grep `explorationPolicyFailed` → not in handler or validator NodeResult mapping
- [ ] grep `explorationPolicyFailure` → not constructing AcpxFailure from read policy
- [ ] grep `harness_failure.reason.*EXPLORATION` → deleted
- [ ] grep `topLevelFailure.*policyFailure` → deleted
- [ ] recovery/current-failure.md → not triggered by exploration violation
- [ ] run.json status → not "failed" solely for exploration read violation
- [ ] full suite green
- [ ] `git diff --check` clean

---

## 8. 下一步

B0 完成（本文档 + active plan 已更新）。等待 review 确认后进入 B1。
**不提交、不进入 B1。**
