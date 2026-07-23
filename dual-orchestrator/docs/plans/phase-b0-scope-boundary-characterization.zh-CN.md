# Phase B0 Scope Boundary Characterization

日期：2026-07-23

状态：`COMPLETE / B1 NOT ACTIVATED`

## 1. 目标与边界

本 checkpoint 只回答四个问题：

1. 当前 scope 规则来自哪里；
2. 哪些规则只是观察或提示，哪些会影响节点成功；
3. 哪些规则应保留，哪些规则应删除；
4. B1 最小实现范围是什么。

本轮没有修改 production，没有运行 provider 或 real-case，也没有新增 Scope Engine、policy DSL、evidence registry 或状态机。

## 2. 当前 authoritative fact 与真实执行链

业务范围的 authoritative fact 是 canonical `TaskContract.scope.included`，每个条目由：

```text
ScopeEntry(path, operation=CREATE|MODIFY)
```

组成。

但它目前不是 effective read scope 的唯一来源。真实调用链是：

```text
TaskContract.scope.included
NodeInstance.inputRefs
EvidenceManifest.files
git changed-files
requirement.md tokenization
repo-map filename scoring
        |
        v
RepoScopeGovernance.buildFileRelevancePlan()
        |
        v
readCandidates (混合来源，最多 24 个)
        |
        v
RepoScopeGovernance.buildStageScopePolicy()
        |
        v
stage_scope_policy.readScope
        |
        v
ExplorationPlan.fromScope()
        |
        v
agent_exploration_plan.allowedDirectReadFiles
        |
        v
ACP agent 已经执行并产生 tool usage
        |
        v
ContextGovernanceActualReportWriter
        |
        v
ExplorationPolicyEvaluator
        |
        v
AcpxAgentTaskHandler:
failed policy result -> NodeResult(FAILED, EXPLORATION_POLICY_VIOLATION)
```

因此当前边界是**运行后节点验收边界**：

- 它能够阻止违规 agent run 成为成功节点；
- 它不能在 tool call 发生前物理阻止读取；
- cannot-bypass 的准确含义是“违规读取不能产生成功节点”，不是“违规读取不可能发生”。

## 3. 规则分类

### 3.1 来自 typed `TaskContract.scope`

| 规则 | 当前实现 | 是否参与节点验收 | 结论 |
| --- | --- | --- | --- |
| included path | `addContractPaths()` 读取 `artifacts/task-contract.json` | 是，加入混合 `readCandidates` 后进入 `allowedDirectReadFiles` | 保留，但必须成为唯一 base scope |
| CREATE / MODIFY | proposal/canonical contract 已类型化 | 否，`RepoScopeGovernance` 只取 path，忽略 operation | 保留 typed intent；B1 不扩大到写入生命周期重构 |
| acceptance criteria | `addContractPaths()` 将自然语言交给 `addPathCandidate()` 猜路径 | 是，猜中的 token 可进入 effective read scope | 删除 |
| typed scope expansion | validated `scope_expansion_request.requestedFiles` 进入 evaluator | 是，只豁免精确请求的路径 | 保留；继续明确它是运行后验收事实 |

### 3.2 通用路径安全规则

| 规则 | 当前实现 | 结论 |
| --- | --- | --- |
| workspace containment | proposal validator `root.resolve(path).normalize()` + `startsWith(root)` | 保留 |
| absolute / `.agent` / traversal 防护 | proposal/agent contract validators 与路径 normalization | 保留并集中复用 |
| exact path 或 declared directory descendant | `ExplorationPolicyEvaluator.allowed()` | 保留 |
| blank、duplicate path 拒绝 | typed contract validators | 保留 |
| CREATE 已存在 / MODIFY 不存在 | proposal validator 对 workspace evidence 的验证 | 保留 |
| 固定扩展名白名单 | `TaskContractProposalValidator.SUPPORTED_FILE_REF`、`TaskContractValidator.FILE_REF` | 删除；它不是安全规则，并把 harness 绑定到 md/vue/ts/js/java/py 等技术栈 |

### 3.3 语言、框架、文件名和自然语言猜测

| 规则 | 位置 | 当前影响 | 结论 |
| --- | --- | --- | --- |
| requirement tokenization | `requirementTerms()` | 作为 repo path 评分输入，间接扩大 read admission | 从控制链删除 |
| filename substring scoring | `repoPathScore()` | 命中文件进入 `readCandidates` | 从控制链删除 |
| `docs/` 加分 | `repoPathScore()` | 改变候选优先级，可能挤出 contract path | 删除 |
| editable filename guess | `looksEditable()` | 将混合 read candidates 转成所谓 `writeScope` | 删除；不能把猜测称为 allowed write |
| TS test naming | `fileKind()` 的 `.spec.ts` / `.test.ts` | repo-map kind 可参与 intake implementation-level violation | 删除控制语义；file-kind 若保留只能是观察字段 |
| Python/Node/framework ignore dirs | `IGNORED_DIRS` | 影响 repo-map 可见集合，当前又间接影响 admission | 与 admission 解耦；是否保留为被动索引优化留给 Phase C |
| acceptance prose path extraction | `addPathCandidate()` | 自然语言中“像路径”的 token 可进入 admission | 删除 |
| seed digest keyword line selection | `seedDigest()` | 只影响 prompt/context，但依赖相同自然语言猜测 | 不进入 B1 控制链；Phase C 决定删除或替换 |

### 3.4 其他 typed/observed facts

| 来源 | 当前用途 | 是否应成为 base scope |
| --- | --- | --- |
| `NodeInstance.inputRefs` | 上游 artifact 引用 | 否；artifact evidence 不等于 repo read authorization |
| `EvidenceManifest.files` | 已有证据与 changed-file claim | 否；观察事实不能自行扩大授权 |
| git changed-files | 已发生的 workspace 变化 | 否；事后事实不能自行扩大授权 |
| repo map | bounded repository observation | 否；它可以帮助 context，但不能决定 policy |

## 4. 当前重复或漂移的 policy owner

当前同时存在两套 stage policy：

- `RepoScopeGovernance.scopeExceedPolicy()`：design/plan=`warn`；
- `ExplorationPlan.failureMode()`：design/plan=`block`。

真正被 `ExplorationPolicyEvaluator` 消费的是 `ExplorationPlan.failureMode`。前者只进入 prompt/projection，却向 agent 显示不同语义。

另外：

- `stage_scope_policy.explorationBudget` 不被 evaluator 消费；
- `stage_scope_policy.forbiddenScope` 主要是 prompt 文案；
- `writeScope` / `expectedEditFiles` 当前只有提示和观测，没有 scope evaluator enforcement；
- `outsideScopeWrites` 只是 report 字段。

这些字段不能被描述为 enforced policy。

## 5. Cannot-Bypass 基线

本轮固化并执行以下 deterministic tests：

```text
mvn -pl dual-runtime -am \
  -Dtest=ExplorationPlanTest,ExplorationPolicyEvaluatorTest,ArtifactContractValidatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

结果：
ExplorationPlanTest                 4/0
ExplorationPolicyEvaluatorTest    11/0
ArtifactContractValidatorTest     15/0
```

```text
mvn -pl dual-adapter-acpx -am \
  -Dtest=ContextGovernanceActualReportWriterTest,AcpxAgentTaskHandlerTest#completedRunEmbedsExplorationPolicyResultInRunMetadata \
  -Dsurefire.failIfNoSpecifiedTests=false test

结果：
ContextGovernanceActualReportWriterTest  8/0
AcpxAgentTaskHandlerTest                 1/0
```

这些测试当前证明：

- intake direct read 会产生 typed failure；
- block stage 的 scope 外读取不能成为成功结果；
- scope expansion 只覆盖精确请求文件；
- provider process 成功不等于 workflow node 成功；
- policy failure 不会伪装成 adapter stall；
- typed scope expansion 能进入 validated sidecar。

它们没有证明：

- tool call 前物理阻止读取；
- `writeScope` 能阻止越界修改；
- heuristic candidate 不会扩大 effective scope。

现有 characterization：

```text
ContextBuildPackageHandlerTest#
governedIntakePackageDerivesConcreteScopeCandidatesFromRepoMap  1/0
```

该测试明确证明 requirement 关键词和 repo-map 文件名匹配会生成 `contractScopeCandidates`。B1 应替换该测试，而不是继续把这一行为当成正确 contract 语义。

## 6. B1 删除清单

B1 必须删除或断开以下控制路径：

1. `requirementTerms()` -> `addRepoMapMatches()` -> `repoPathScore()` -> effective read scope；
2. acceptance criteria -> `addPathCandidate()` -> effective read scope；
3. input refs / evidence files / git changed-files -> effective read scope；
4. `contractScopeCandidates(readCandidates)` 这个错误命名和错误投影；
5. `looksEditable(readCandidates)` 生成所谓 allowed `writeScope`；
6. core/runtime contract validator 的固定文件扩展名白名单；
7. `stage_scope_policy.scopeExceedPolicy` 与 `ExplorationPlan.failureMode` 双 owner；
8. TS/Python/framework-specific classification 对控制判断的影响；
9. 将 passive guidance 字段描述为 enforced policy 的 prompt 文案。

可以暂时保留但必须与控制链断开的内容：

- bounded repo map；
- evidence / git changed-file observation；
- workstream memory 和 repeated-read metrics；
- passive context candidate/digest。

这些残留属于 Phase C 的 context subtraction 输入，不得进入 `allowedDirectReadFiles`。

## 7. 目标 owner

不新增 Scope Engine。复用现有模型，职责如下：

| 职责 | 唯一 owner |
| --- | --- |
| 业务 base scope SoT | canonical `TaskContract.scope` |
| workspace path 与 CREATE/MODIFY evidence 校验 | `TaskContractProposalValidator` |
| project-neutral contract shape/path syntax | `TaskContractValidator` |
| effective read-policy typed projection | `ExplorationPlan` |
| actual direct-read policy decision | `ExplorationPolicyEvaluator` |
| ACP tool usage observation | `ContextGovernanceActualReportWriter` |
| policy failure到 node result | `AcpxAgentTaskHandler` |
| context/memory 文档装配 | `RepoScopeGovernance`，降为薄协调器 |

目标链：

```text
TaskContract.scope
        |
        v
ExplorationPlan.allowedDirectReadFiles
        |
        +---- typed scope expansion request
        |
        v
ExplorationPolicyEvaluator
        |
        v
Node success / EXPLORATION_POLICY_VIOLATION
```

## 8. 建议的 B1 最小实现范围

`PROCEED_WITH_GUARDS`，但 B1 尚未激活。

B1 只做一次 subtraction checkpoint：

1. 从 canonical `TaskContract.scope.included.path` 精确生成 base read scope；
2. candidate、evidence、git 和 repo-map 继续可以用于 context，但不能进入 effective scope；
3. `ExplorationPlan` 成为唯一 stage failure-mode / direct-read-budget projection；
4. `ExplorationPolicyEvaluator` 保持唯一 decision owner；
5. 删除固定文件扩展名白名单，改为 project-neutral relative path 安全校验；
6. 删除或降级重复 `stage_scope_policy` 控制字段；
7. 不新增 policy framework，不修改 ACP transport，不实现运行中 tool broker；
8. 不在 B1 宣称 write-scope enforcement。

B1 cannot-bypass tests 必须新增：

- requirement 文本或 repo 文件名变化不能改变 `allowedDirectReadFiles`；
- evidence/git changed files 不能扩大 `allowedDirectReadFiles`；
- canonical contract scope 精确进入 plan，且不会被 24-file candidate limit 挤出；
- unfamiliar but safe project path（例如 `.rs`、`.go`、`Dockerfile`）不因扩展名被拒绝；
- absolute path、workspace escape、`.agent` 仍 fail closed；
- block stage 的未授权 direct read 仍产生 `EXPLORATION_POLICY_VIOLATION`；
- typed expansion 只允许请求路径，不能豁免 sibling；
- provider exit 0 + policy violation 仍不能成为成功 node。

## 9. Boundary Review

根因反思：

- `readCandidates` 同时承载了“可能相关”“已经观察到”和“允许读取”三种不同语义；
- `contractScopeCandidates(readCandidates)` 通过重命名投影掩盖了来源混合，没有真正证明 candidate 来自 contract；
- `stage_scope_policy` 与 `ExplorationPlan` 重复声明 policy，但只有后者被 evaluator 消费；
- 因此防漂移规则必须是：任何进入 `allowedDirectReadFiles` 的 base path 都必须能追溯到 canonical `TaskContract.scope`，其他 candidate 只能进入 context/observation。

```text
Boundary Review: WARN（当前实现） / PASS（B0 分析）
Touched boundary: Repo scope projection and post-run read admission
Source of truth: canonical TaskContract.scope
Projection surfaces: file relevance plan, stage scope policy, exploration plan,
                     context package, governance report, run.json
Current finding: heuristic and observed facts can expand effective read scope
Required fix: execute the bounded B1 subtraction above
Required tests: section 8 cannot-bypass list
Accepted debt: mid-run physical containment and write-scope enforcement are not
               claimed by Phase B
```
