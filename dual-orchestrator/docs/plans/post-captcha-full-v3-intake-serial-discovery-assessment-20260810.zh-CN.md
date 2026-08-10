# Captcha-Auth Full v3 Intake 串行发现只读评估

日期：2026-08-10

状态：`IMPLEMENTED / DETERMINISTIC PASS / PROJECTED / PROVIDER COMPLIANCE NOT PROVEN`

## 1. 目标与范围

本评估只回答一个问题：为什么 fresh captcha-auth workstream
`ws-20260810T011244Z-8YVB` 在 `intake.codex_product_review` 连续两次
`NEEDS_REVISION`，以及下一步哪个单一改动最值得做。

范围仅覆盖：

- target `REQUIREMENT.md`；
- initial proposal；
- 第一次 Codex review obligations；
- 第一次 Claude revision；
- 第二次 Codex review obligations；
- 第三次 Claude proposal；
- production prompt、proposal validator、revision coverage/delta validator、compile 和
  change-scope evaluator。

不调用 provider，不修改 production，不 retry/resume workstream，不设计通用需求图、
规则引擎或新的 contract platform。

## 2. Owner 与边界

| 事实 | Authoritative owner | 下游消费者 |
|---|---|---|
| 用户业务语义 | target `REQUIREMENT.md` | intake proposal + product reviewer |
| 候选合同 | verified `TaskContractProposal` | product review / compile |
| 语义充分性 | `intake.codex_product_review` typed decision | `intake.decision` |
| revision obligation | verified rejected review sidecar | revision proposal coverage |
| clause revision delta | `TaskContractProposalValidator.validateRevisionDelta` | artifact validation / compile |
| change authorization | canonical `TaskContract.scope.included` | `RepoScopeGovernance` / `ChangeScopeEvaluator` |
| 实际变更 | typed Git evidence | `ChangeScopeEvaluator` |

边界不变量：

```text
TaskContract.scope.included
  = exhaustive set of permitted exact path + operation changes

actual Git change not declared in scope
  -> UNEXPECTED_ACTUAL_CHANGE
  -> change-scope FAIL
```

任何 acceptance/constraint prose 都不能把未声明路径变成已授权路径。这里的 exhaustive
针对 permitted changes：实际变更必须是该集合的子集；它不表示每个声明路径最终都必须改动，
`missingDeclared` 仍只是 evidence，而不是 scope violation。

## 3. 统一失败分类

本次 real-case 的统一结论是：

```text
Real-case FAIL
  = Claude contract quality failures
  + incomplete first Codex review
  + Harness typed-scope semantics projection gap
```

它不是 `TaskContract.scope` enforcement 失败，也不是 transport/parser/lineage 失败。

| 现象 | 分类 | 责任判断 |
|---|---|---|
| initial proposal 遗漏 explicit UX 和 all-server-input validation | 模型语义质量 | Claude 漏项 |
| initial proposal 增加 CSRF replay、rate-limit keying 等约束 | 模型语义质量 | Claude 擅自收紧 requirement |
| first review 漏掉 all-server-input validation | reviewer completeness | Codex 没有在第一轮找全已有 gap |
| first revision 用 `TR-3` 声称完整解决 `CO-5` | 模型语义质量 | obligation 有映射，但映射不构成语义满足 |
| first revision 新增 asynchronous transport 假设 | 模型 revision drift | Claude 在修订中引入新约束 |
| first review 要求 illustrative/non-exhaustive scope | Harness projection gap + reviewer error | prompt 未投影 exact scope 语义，Codex 给出与 authority 冲突的 obligation |
| second review 拒绝剩余 scope/input/async 问题 | 正常 quality gate | Codex 第二次决定有业务依据 |
| 连续第二次 `NEEDS_REVISION` 后停止 | 正常 stop-loss | Harness 正确限制调用/token 继续增长 |

分层判定：

- **主要运行失败来源**：Claude proposal/revision 没有稳定落实完整 requirement 和 review obligation。
- **次要成本来源**：Codex 第一轮 review 漏掉一个当时已存在的 requirement gap，造成串行发现。
- **唯一已证明的 Harness 缺口**：producer/reviewer prompt 没有明确投影 canonical typed scope
  的 exact authorization 语义，允许生成错误的 prose-widening obligation。
- **已证明正常的 Harness 边界**：ACPX transport、presentation normalization、strict parser、
  schema、revision delta、lineage、change-scope enforcement、interruption closure 和 stop-loss。

因此本轮不能通过“放宽 scope enforcement”或“降低 reviewer 严格度”修复。也不能把所有模型
漏项都归为 Harness bug。Harness 只修它 owns 的 scope-semantics projection；模型能否稳定一次
生成完整合同，仍属于后续自然样本中的质量观测。

## 4. Captured Evidence

### 4.1 第一次 review

第一次 review 正确发现：

1. explicit UX requirements 未进入 binding clauses；
2. login error 条款自相矛盾；
3. CSRF replay rejection 是未要求的加码；
4. rate-limit keying 是未要求的实现约束；
5. Captcha test 使用“distinct”而不是 requirement 的 randomized lifecycle。

但 `CO-5` 同时要求：

```text
The binding scope must permit necessary supporting files and semantically
equivalent implementations ...
```

该要求把 typed exact scope 当成可由自然语言放宽的 implementation inventory，和
Phase B 的 change-boundary owner 冲突。

### 4.2 第一次 revision

Claude 将 `CO-5` 只映射到 `TR-3`。这修复了 randomized Captcha test，但没有修改
typed scope，也没有真正解决 obligation 的 scope 部分。现有 coverage validator 只证明：

```text
CO-5 appears exactly once
TR-3 is an existing clause id
```

它不证明 `TR-3` 的语义蕴含 scope flexibility。第二次 Codex review 因而合理地再次拒绝。

同一 revision 还把 loading-state requirement 收紧为 asynchronous transport；这是 revision
新引入的 semantic drift，第二次 review 才可能发现，不能归因为第一次 review 漏项。

### 4.3 第二次 review 与第三次 proposal

第二次 review 提出：

- `CO-5`：scope flexibility 尚未解决；
- `CO-6`：原 requirement 的 all server inputs validation 在第一次 review 中漏检；
- `CO-7`：移除 revision 新增的 asynchronous transport 假设。

第三次 proposal 用 `C-11` 声称 scope list 是 illustrative/non-exhaustive，并用 `AC-21`、
`AC-19` 回应另外两项。机械、coverage、clause delta 和 lineage 均通过，但 `C-11` 不会被
`ChangeScopeEvaluator` 消费：compile 仍逐项复制 exact `scope.included`，实际未声明文件仍会
成为 `UNEXPECTED_ACTUAL_CHANGE`。

因此第三版中的 `C-11` 不是 change authorization，只是与 authoritative scope 冲突的文本。
该 proposal 未经第三次 review，不得声明 READY。

## 5. 为什么现有 Gate 没有提前挡住

现有 v3 设计明确把 deterministic revision delta 限定在：

- acceptance criteria；
- test requirements；
- constraints；
- non-goals；
- risk areas。

`objective`、`scope`、`evidence_refs` 和 `open_questions` 没有 revision target identity。
`revision_responses` 又只能填写 `resolved_by_clause_ids`。因此 scope-related obligation 必须选择
一个 narrative clause landing point，scope 的 semantic adequacy 仍由 Codex review 判断。

这个边界本身是已知、刻意保留的 bounded closure，不应在本轮偷偷升级 schema。但当前
review prompt 没有告诉 reviewer：

- typed scope 穷尽 permitted exact path+operation authorization；
- prose 不能放宽它；
- 不得要求 illustrative/non-exhaustive scope；
- scope gap 必须描述为具体 path + operation 修订，而不是 generic supporting-files permission。

所以 reviewer 生成了结构上可表达、语义上不可执行的 obligation。

## 6. 其他观察为何不选为本轮实现

### 6.1 “一次 review 必须找全所有 requirement gaps”

第一次 review 确实漏掉了 server-input validation。prompt 已包含完整 requirement，并已明确要求
explicit-requirement coverage；不存在 context truncation 或 projection 丢失。再加一句
“一次找全”仍是 soft instruction，不能形成确定性保证。

若要 deterministic 保证，需要给原始 requirement 建立 typed IDs 和逐项 mapping。这会扩大为
新的 requirement-contract 模型，当前只有一个真实样本，成本和证据不足，本轮不做。

### 6.2 自动验证 obligation text 是否被 clause 语义满足

Java validator 无法在不引入 NL/embedding/LLM secondary judge 的情况下证明自然语言蕴含。
使用关键词或文件名规则会重演 heuristic authority。本轮不做。

### 6.3 立即把 scope 加入 revision target schema

这是合理的长期候选，但需要 schema revision、baseline delta、coverage target 和 migration
决策。当前 captured failure 可以先通过纠正 reviewer 的既有 scope owner 语义避免，不足以授权
schema v4。

## 7. 唯一选择的候选

选择：`Product Review Typed Scope Semantics`。

最小目标：

```text
reviewer cannot require prose-based scope widening
reviewer treats scope.included as the exhaustive typed authorization set
scope gaps request concrete path + operation changes
```

建议实施边界仅为既有 prompt owner：

1. 在 `contract-review-principles.md` 明确 typed scope 穷尽 permitted exact path+operation
   authorization，但不要求每个 declared path 必须实际修改。
2. 在 intake Claude prompt/`AgentResultPromptContract` 的 scope instruction 中保持同一语义：
   不得用 acceptance/constraint prose 声称 scope non-exhaustive；若 scope 改动是当前 obligation
   的一部分，必须直接修改 typed entries，并保留一个真实相关 clause landing point供现有 v3
   coverage 使用。
3. 不修改 `TaskContractProposal` schema、coverage/delta validator、compile、workflow 或
   `ChangeScopeEvaluator`。

这一改动不是让 prompt 成为 authority。authority 已经是 canonical typed scope；prompt 只是
停止教 reviewer/producer 生成与 authority 冲突的合同。

## 8. Deterministic 证明

已实施并通过以下 prompt-level tests：

1. product review prompt 明确包含 exact/exhaustive typed scope 语义；
2. prompt 明确禁止 illustrative/non-exhaustive/prose-widening；
3. intake revision prompt 要求 scope gap 修改 typed path+operation；
4. existing strict parser、schema、coverage/delta 和 change-scope tests 全部保持绿；
5. 既有 change-scope cannot-bypass tests 保持绿：captured third proposal 的 `C-11`
   仍不能改变 `ChangeScopeEvaluator`，未声明文件必须继续
   `UNEXPECTED_ACTUAL_CHANGE`。

实现范围只覆盖既有 owner：

- `contract-review-principles.md`：审查者的 typed scope authority 语义；
- `intake-claude-brainstorm.md`：生产者直接修改 typed path-and-operation entries；
- `AgentResultPromptContract`：revision correction 路径使用相同语义；
- `AgentPromptRendererTest` 与 `AgentResultPromptContractTest`：最终渲染 prompt 精确字符串证明。

定向验证：`AgentResultPromptContractTest` 7/0，`AgentPromptRendererTest` 34/0。
完整 Maven reactor：9/9 modules `BUILD SUCCESS`。首次沙箱内执行因 macOS
`ProcessHandle.descendants()` 的 `sysctl` 权限被拒绝而产生两项既有 process-tree
测试环境错误；在允许进程树读取的环境重跑后全部通过，未修改生产或测试语义。

这些测试只能达到 `PROJECTED`，不能证明模型必然遵守。未来自然 scope revision 样本才能证明
provider compliance；不为此人工制造第二个 captcha-auth workstream。

## 9. Boundary Review

```text
Boundary Review: PASS
Problem classification: artifact-contract semantic projection gap
Source of truth: canonical TaskContract.scope.included
Existing enforcement: ChangeScopeEvaluator exact path + operation comparison
Selected change: align reviewer/producer prompt with existing owner
New domain concept: No
Schema change: No
Production implementation status: IMPLEMENTED / PROJECTED
Provider compliance: NOT PROVEN
```

本评估不宣称完整 workflow 已证明，也不宣称 reviewer completeness 已解决。它只证明 captured
`CO-5 -> TR-3 -> C-11` 链偏离了既有 typed scope owner，并选择了一个最小、非平台化的修复候选。
