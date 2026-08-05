# Full Workflow 十次固定 Agent 调用价值验证计划

日期：2026-08-05
状态：`READ-ONLY BASELINE COMPLETE / ONE PROFILE EXPERIMENT CANDIDATE`

## 1. 唯一目标

本计划只回答：

> full workflow happy path 固定执行的 10 次 Agent 调用，分别保护了什么独特事实、
> 产生了什么可观测质量收益、消耗了多少资源，以及是否必须对所有任务固定启用。

这不是“必须删调用”的计划。每个调用都必须得到独立裁决；合法结论包括保留、按任务
profile 可选、可合并、需要更多证据。没有证据时不得为了减少数字删除节点。

可定制 workflow 是后续验证候选的手段，不是本计划的主目标。

## 2. 验证对象

| 编号 | Node | 角色 | 当前已知证据 |
| --- | --- | --- | --- |
| A1 | `intake.claude_brainstorm` | 需求澄清与合同提案 | T6 revision 补齐排序、NULL 语义和权威 cycleId 义务 |
| A2 | `intake.codex_product_review` | 产品/合同独立审查 | T6 发现上述真实合同缺口 |
| A3 | `design.claude_design` | 技术设计 | 能完成真实流程，独特价值尚未单独归因 |
| A4 | `design.codex_arch_review` | 架构独立审查 | 独特 defect yield 尚未单独归因 |
| A5 | `plan.claude_plan` | 实施计划 | T6 revision 后落实 NULLS LAST、受限状态和错误分支 |
| A6 | `plan.codex_plan_review` | 计划独立审查 | T6 发现三个会进入实现的真实偏差 |
| A7 | `implement.claude_implement` | 修改代码与运行实现检查 | 核心 side-effect producer |
| A8 | `implement.claude_self_test_report` | 运行并声明自测证据 | 已确认拥有 acceptance 前 required typed evidence；替代 owner 不存在 |
| A9 | `acceptance.codex_verifier_review` | 独立最终验收 | acceptance decision owner；按任务规模的固定必要性未证明 |
| A10 | `stage.claude_handoff` | 下一阶段交接 | 与确定性 projection 存在部分重叠；完整 owner 和 revise feedback 尚未闭合 |

## 3. 当前不能混淆的结论

最新 T6 已证明两次 semantic revision 有价值：A1/A2 和 A5/A6 的额外 revision
调用修复了最终实现实际依赖的缺口。这证明这些 review loop 在该中等复杂度任务中有
价值，但没有证明 A1-A10 对 README、小 Bug、配置修改和复杂跨栈任务都应采用同一
固定形状。

同样：

- 一个节点有 typed output，不等于它产生独特价值；
- 一个节点未触发 revision，不等于它没有低频保护价值；
- 一个节点消耗 token 很高，不等于它应该删除；
- workflow 最终完成，不等于每个调用都值得固定存在。

## 4. Source of truth

只使用现有事实，不新增 metrics 或评估平台：

```text
workflow/definition.json
nodes/*.json
executions/nodes/*.json
runs/*/run.json
strict output sidecars + lineage
iterations/*.json + events.jsonl
gates/*.json
artifacts/task-contract.json
artifacts/change-scope-evaluation.json
metrics/workstream-metrics.json
target tests / acceptance evidence
```

优先使用三类已有样本：

1. 无 revision 的 A3/README happy path；
2. 存在多次 revision 的历史 A3；
3. 最新 T6 中等复杂度 `ws-20260804T092758Z-73XJ`。

如果某个调用在这些样本中均无足够证据，裁决为 `NEEDS_MORE_EVIDENCE`，不自动启动
provider real-case。

## 5. 每个调用的统一价值记录

A1-A10 必须逐一填写，字段不得省略：

```text
Node:
Task/sample coverage:
Authoritative input:
Authoritative output:
All downstream consumers:
Unique invariant protected:
Agent duration:
Input/output/cache tokens:
Review decision or artifact delta:
Defect caught:
Final implementation/test impact:
Human decision impact:
Counterfactual if omitted:
Existing alternative owner:
Evidence reliability:
Verdict:
```

`Verdict` 只能是：

- `KEEP_PROVEN`：已有独特事实、缺陷或不可替代 side effect 证据；
- `PROFILE_REQUIRED`：只对某类任务证明必要；
- `PROFILE_OPTIONAL_CANDIDATE`：部分任务可能省略，值得做同 case 验证；
- `MERGE_CANDIDATE`：事实有价值，但可能由相邻调用或确定性 owner 产生；
- `NEEDS_MORE_EVIDENCE`：样本不足，不能修改 production；
- `INVALID_EVIDENCE`：lineage、provider、freshness 或指标不可比较。

## 6. 实施顺序

### Step 1：冻结历史样本与口径

只读确认每个样本的：

- clean SHA / workflow definition hash；
- provider/model 与 usage reliability；
- Agent occurrence、revision 和 correction 区分；
- acceptance、target tests、change scope 和 final artifacts；
- 是否能用于质量判断、效率判断，或只能用于控制链 replay。

输出：`Sample Evidence Matrix`。不可比较样本保留，但标为 `INVALID`，不参与成本结论。

### Step 2：提取 A1-A10 成本

从现有 ledger 和 metrics 提取：

- happy-path occurrence count；
- revision 增量 occurrence；
- duration；
- measured input/output/cache token；
- bounded correction 次数；
- gate 前后等待只作流程成本，不算 Agent 成本。

输出：`Fixed Call Cost Matrix`。禁止另写一套 token 计算。

### Step 3：建立 producer-consumer 图

逐个读取 production 代码和 workflow definition，证明：

```text
Agent output
  -> strict parser/validator
  -> typed sidecar + lineage
  -> concrete downstream consumer
  -> blocked route / Agent start / side effect
```

如果一个输出只有 report 展示、没有控制或下游质量消费，必须明确记录；如果它阻止
下游启动，也必须指出 cannot-bypass owner，不能只写“看起来有用”。

输出：`Fixed Call Ownership And Consumption Matrix`。

### Step 4：归因真实质量增量

对 producer/reviewer pair 分别比较 revision 前后：

- typed obligations、design、plan、self-test 或 acceptance 字段变化；
- reviewer must-fix 是否在下一 artifact 中被落实；
- 最终代码或测试是否实际依赖该变化；
- revision 是否由 Dual 自有 context/projection/prompt 缺陷诱发。

没有 artifact delta 或最终影响时，不能把 reviewer 的自然语言评价记为质量收益。

输出：`Defect Yield And Artifact Delta Matrix`。

### Step 5：逐一做 counterfactual

对 A1-A10 分别回答：

```text
若省略该调用：
- 哪个 authoritative fact 消失？
- 是否已有 owner 能产生同一事实？
- 哪个下游 side effect 仍须 fail closed？
- 是所有任务都受影响，还是只影响某个 task profile？
- 最小同 case 实验需要改变什么？
```

此步骤必须覆盖全部 10 次调用；不能因为先发现 handoff 候选就停止审查其余节点。

输出：`A1-A10 Value Verdict`。

### Step 6：全局裁决

完成 A1-A10 后，将调用分为：

```text
Always required kernel calls
Task-profile-required calls
Optional/merge candidates
Insufficient-evidence calls
```

这里才决定 full workflow 是否继续作为默认严格流程，以及是否值得增加一个更短的
workflow definition。禁止预先设计 lean/standard/strict 三套产品矩阵。

### Step 7：只选择一个实验候选

候选选择顺序不是预设删除顺序，而是证据排序：

1. 没有独特 downstream consumer；
2. 事实已有确定性 owner；
3. 多个样本中 defect/artifact delta 始终为零；
4. 成本高；
5. 能用一个 definition delta 做同 case 对照。

一次只允许一个候选。可定制 workflow 仅在这里作为实验工具：复用 canonical node、
handler、prompt 和 contract，生成一个只相差该候选的 definition。

### Step 8：Deterministic Gate 与同 Case Real-Case

先证明：

- custom definition 可执行；
- authoritative owner 没有丢失；
- 原应阻止的 Agent start、route 或 side effect 仍 fail closed；
- full workflow 不受修改；
- focused/cannot-bypass/full reactor 通过。

随后才允许一次同 task、同 target、同 provider/model、同 acceptance 标准的前后对照。

PASS 必须同时满足：

- Agent occurrence 明确减少或合并；
- acceptance、target tests、change scope、lineage 不退化；
- 没有新增 semantic revision 抵消收益；
- token/duration evidence 可比；
- 缺失的调用没有把责任转嫁给 human gate。

否则为 `FAIL` 或 `INVALID`，不得继续选择第二候选。

## 7. 初始假设，不是预设结论

| 调用组 | 初始假设 | 证据纪律 |
| --- | --- | --- |
| A1/A2 intake | 中等复杂度已证明，简单任务待验证 | 不因 T6 一次成功外推所有任务 |
| A3/A4 design | 可能与 contract/plan 重叠 | 必须查真实 defect yield 和 artifact delta |
| A5/A6 plan | 中等复杂度已证明，简单任务待验证 | 保留已证明 must-fix 链 |
| A7 implementation | 核心 side effect，预计 KEEP | 仍记录成本和 consumers，不做形式性删除实验 |
| A8 self-test | fact 有价值，Agent owner 可能可替代 | 先证明 deterministic runner owner，不直接跳过验证 |
| A9 acceptance | 独立验收预计 KEEP | 评估 task-profile，而非取消独立审查 |
| A10 handoff | 最可能 profile-optional | 必须检查完整 handoff consumers 和 revise 语义 |

## 8. 明确禁止

- 不把十个调用拆成十个 implementation phase；
- 不在只读矩阵完成前修改 workflow；
- 不为了证明“可优化”强行选候选；
- 不用不同 case 做 before/after；
- 不把 bounded correction 计为 semantic revision；
- 不把模型名称变化当作 workflow 价值证据；
- 不新增 metrics store、value engine、risk engine、registry、DSL 或 graph platform；
- 不让 LLM 自己决定跳过自己的 review/gate；
- 不削弱 strict parser、lineage、ledger、recovery、change scope 或 human gate state。

## 9. 完成定义

本计划完成要求：

1. A1-A10 全部具有成本、owner、consumer、质量增量和 counterfactual 记录；
2. 每个调用得到受证据约束的 verdict；
3. 明确哪些是全局必要，哪些仅对特定 task profile 必要；
4. 选择一个候选，或者诚实得到 `NO_SAFE_CANDIDATE`；
5. 只有存在候选时才执行一个同 case subtraction experiment；
6. 没有新增平台能力或第二 source of truth。

## 10. Boundary Review

```text
Boundary Review: PASS / READ-ONLY BASELINE COMPLETE

Declared by:
  canonical WorkflowDefinition and strict Agent sidecars.

Proven by:
  ledger-backed cost, artifact delta, downstream consumption and final implementation evidence.

Consumed by:
  A1-A10 verdict and one later candidate-selection decision.

Enforced before:
  any workflow subtraction or provider comparison.

Failure route:
  missing/non-comparable evidence -> NEEDS_MORE_EVIDENCE or INVALID_EVIDENCE;
  no safe candidate -> NO_SAFE_CANDIDATE.

Cannot-bypass:
  all ten calls must be assessed before candidate selection;
  only one definition delta may enter implementation/real-case.

Residual risk:
  existing samples cannot prove every task class;
  profile-level claims remain bounded to the tested task class.
```

## 11. Sample Evidence Matrix

2026-08-05 已按 Step 1 完成只读样本固定。证据等级必须区分：

| Sample | Workflow result | Agent shape | Evidence availability | Allowed use |
| --- | --- | --- | --- | --- |
| T1 A3 `ws-20260728T153012Z-JH74` | `COMPLETED / ACCEPT` | 10 runs, 0 revision, 0 correction | reconciled baseline 文档保留；原始 ledger 已不在 | 历史成本与最终质量参考；不做新的 raw replay |
| T5 v3 A3 `ws-20260729T131421Z-BX54` | `COMPLETED / ACCEPT` | 10 runs, 0 revision, 0 correction | report、summary、runs log 已归档；原始 ledger 已不在 | 第二份 happy-path 成本和质量对照 |
| T6 `ws-20260804T092758Z-73XJ` | `COMPLETED / ACCEPT_WITH_NOTES` | 14 runs, 2 semantic revisions, 3 bounded corrections | raw ledger、run.json、sidecar、lineage、iterations 均在 | A1-A10 owner/consumer、artifact delta 和 final impact 的主证据 |

T6 persisted workflow definition SHA-256 为
`2e4214a889218822654ae8d9b0a432dec33ae120fcfd6fe39f3ab3959d4e1ac3`。T1/T5
的 provider 成本只与各自归档报告绑定；不与 T6 做模型因果比较。

Evidence verdict：`VALID WITH ARCHIVAL LIMITS`。T6 可重放；T1/T5 只能使用已归档的
reconciled facts。

## 12. Fixed Call Cost Matrix

T5 v3 是简单 A3 happy path；T6 取每个 node 最后一次成功的 occurrence，
排除前一轮 A1/A2 和 A5/A6 的 4 次 semantic revision 增量。bounded
correction 仍计入所属 Agent run，不剥离。

| ID | Node | T5 v3 duration / total tokens | T6 fixed duration / total tokens | T6 fixed share |
| --- | --- | ---: | ---: | ---: |
| A1 | `intake.claude_brainstorm` | 43.8s / 7,076 | 179.0s / 16,647 | 0.40% |
| A2 | `intake.codex_product_review` | 38.8s / 29,836 | 29.5s / 40,541 | 0.98% |
| A3 | `design.claude_design` | 73.9s / 53,208 | 382.0s / 156,452 | 3.77% |
| A4 | `design.codex_arch_review` | 33.8s / 36,978 | 29.4s / 47,678 | 1.15% |
| A5 | `plan.claude_plan` | 39.9s / 22,802 | 169.7s / 213,699 | 5.16% |
| A6 | `plan.codex_plan_review` | 23.1s / 43,882 | 24.6s / 64,956 | 1.57% |
| A7 | `implement.claude_implement` | 67.5s / 129,043 | 238.1s / 3,097,824 | 74.74% |
| A8 | `implement.claude_self_test_report` | 27.5s / 114,205 | 204.8s / 284,450 | 6.86% |
| A9 | `acceptance.codex_verifier_review` | 34.7s / 52,994 | 41.9s / 75,412 | 1.82% |
| A10 | `stage.claude_handoff` | 15.4s / 43,380 | 20.7s / 147,349 | 3.55% |

T6 fixed 10 合计 `1,319,689ms / 4,145,008 tokens`。表中数字定位成本，
不是删除排名：A7 的高 token 来自实现 side effect 和长 session context；A4 成本低
也不证明它必然值得固定执行。

## 13. Ownership, Consumption And Quality Delta

| ID | Authoritative output / owner | Concrete consumer and blocked effect | Observed quality delta |
| --- | --- | --- | --- |
| A1 | validated `task_contract_proposal` + lineage | A2 required input；compile 只在 review/human gate 后进入 | T6 revision 后补齐排序方向、NULLS LAST、typed none/error、cycleId 不可绕过条款 |
| A2 | typed product-review decision + `contract_obligations` | `intake.decision` 路由 READY/NEEDS_REVISION；未满足时阻止 contract compile | T6 CO-1/CO-2 被 A1 下一轮用 clause ids 显式闭合，且最终代码/测试实际依赖 |
| A3 | validated design result，含代码证据、owner、failure route 和实施方向 | A4 review subject；A4 之后的 typed review 被 A5 context 消费 | T6 产生了实现就绪的 backend-authoritative 设计；不是单纯 requirement 重述 |
| A4 | typed architecture-review decision | `design.decision` + human design gate；A5 context 当前消费 A4 artifact | T1/T5/T6 可见完整样本均未触发 must-fix；T6 仅增加 acceptance notes，低频缺陷收益未证明 |
| A5 | typed `plan_result` / `plan_artifact` | A6 review subject；A6 后的决策进入 human plan gate | T6 修订后增加 8 项测试、Literal+validator、显式三态分支 |
| A6 | typed plan-review decision + must-fix | `plan.decision` 路由 READY/NEEDS_REVISION；未通过时阻止 implementation | T6 抓到 cycle_number nullslast、status 组合约束、unknown response 错误分支三个实现级偏差 |
| A7 | repository changes + typed implementation result | A8、Git evidence、change-scope evaluation 和 acceptance context | 唯一核心代码 side-effect producer；T6 修改 5 个允许文件 |
| A8 | typed `self_test_report` | artifact validation；acceptance preflight 缺失时禁止 A9 启动 | T6 独立运行 backend 199 tests + frontend build；A7 明确把 full tests 留给 A8 |
| A9 | typed acceptance decision | `acceptance.decision`、final report、delivery 与 human acceptance gate | T5 `ACCEPT`；T6 基于 Git/test/change-scope 给出 `ACCEPT_WITH_NOTES`，是独立验收 owner |
| A10 | typed `stage_handoff` -> canonical `handoff.json` | next-stage prompt、completion audit、memory/context consumers、human next-stage gate | T5/T6 产生交接信息，但都没有真实下一个 Agent stage 消费并证明业务收益 |

## 14. Counterfactual And A1-A10 Verdict

| ID | If omitted | Existing alternative owner | Verdict |
| --- | --- | --- | --- |
| A1 | 没有 typed task contract proposal，后续 compile 无输入 | 无 | `PROFILE_REQUIRED`：中等/复杂自然语言任务已证明 |
| A2 | 未完整合同可直接进入 human gate/compile | human 可审查，但会把系统责任转嫁给 human | `PROFILE_REQUIRED`：T6 中等任务已证明 |
| A3 | plan 缺少对代码事实、owner 和 failure route 的设计转换 | A5 可能在简单任务合并设计+计划，尚未验证 | `PROFILE_REQUIRED`：中等跨栈任务需要；简单任务待证 |
| A4 | 丢失 implementation 前的独立架构审查，且当前 A5 context 需重连 A3 | human design gate 不产生同等 typed review | `NEEDS_MORE_EVIDENCE`：无 must-fix 不等于无价值 |
| A5 | implementation 没有文件级顺序、测试和 rollback 计划 | A7 可能对小任务边做边计划，尚未验证 | `PROFILE_REQUIRED`：中等任务已证明 |
| A6 | 三个会进入代码的偏差未在 implementation 前阻断 | human plan gate 可查，但不是同等 typed owner | `PROFILE_REQUIRED`：T6 已证明 |
| A7 | 没有代码 side effect | 无 | `KEEP_PROVEN` |
| A8 | A9 没有 full test/build typed evidence | 当前无 deterministic test-execution owner | `KEEP_PROVEN` |
| A9 | 没有独立 acceptance decision，实现方可自证通过 | human gate 是最终责任者，但需要结构化证据和建议 | `KEEP_PROVEN` for governed full workflow |
| A10 | 没有 canonical next-stage handoff/prompt | final report、Git evidence、acceptance 可覆盖部分字段，不是完整替代 | `PROFILE_OPTIONAL_CANDIDATE`，仅限不需要后续 Agent stage 的终止型交付任务 |

## 15. Global Verdict

```text
Always-required governed kernel:
  A7 implementation
  A8 self-test evidence
  A9 independent acceptance

Medium/complex task profile required:
  A1/A2 contract production and review
  A3 design
  A5/A6 plan production and review

Insufficient evidence:
  A4 architecture review

One profile experiment candidate:
  A10 stage handoff
```

这不是说 A4 可以删；相反，它缺少足够的 defect-yield 样本，所以不进入
production 修改。A10 也不做全局 subtraction。唯一合法实验是定义一个语义上
明确“验收后任务结束，不存在下一 Agent stage”的 task profile，在该 profile 中让
workflow 终止于 human acceptance，而不是伪造 deterministic handoff。

## 16. Next Gate

当前只完成只读价值验证，没有 production 修改，也不需要 provider real-case。
若后续激活 A10 实验，必须先用 deterministic definition test 证明：

1. profile 明确声明 `next_agent_stage_required=false`；
2. human acceptance 是该 profile 的 terminal responsibility boundary；
3. 不产生 `handoff.json` 是 `NOT_APPLICABLE`，不是 missing artifact；
4. full workflow 仍保留 A10 与 human next-stage gate；
5. completion audit/final report 不从缺失 handoff 推断伪状态；
6. 同 case 对照只证明终止型 profile，不外推到需要交接的任务。

Boundary maturity：`PRODUCTION OPTIONAL VARIANT ENFORCED / ONE A3 SAME-CASE PROVEN`。

## 17. A10 Terminal-Delivery Deterministic Experiment Result

日期：2026-08-05
结果：`PASS / ROUTING DETERMINISTIC ENFORCED / AUDIT APPLICABILITY PROJECTED + CANNOT-BYPASS / REAL-CASE NOT PROVEN`

实验没有新增内置 variant、profile engine 或 DSL。它使用现有
`WorkflowDefinitionLoader.load(Path)` 的外部 definition 能力，新增一份 test-only
definition：

```text
agent-lifecycle-terminal-delivery-experiment
  29 nodes
  45 edges
  9 acpx.agent_run nodes
  terminal owner = gate.human_acceptance
```

相对 full 的唯一 graph delta：

- 删除 `gate.human_acceptance --approved--> stage.claude_handoff`；
- 删除 `stage.claude_handoff`、`stage.generate_next_prompt`、
  `gate.human_next_stage_approval` 及其边；
- 保留 `gate.human_acceptance --revise--> context.implement_claude`；
- 将 experiment 内 `gate.human_acceptance.terminal_gate=true`；
- A1-A9 node 定义和所有其他 edges 与 full 逐对象相等。

实验同时暴露并收敛了一条旧的 projection drift：
`CompletionAuditWriter` 原来无条件要求 `handoff.json` 和 next-stage prompt，实际上在
projection 里重写了 full workflow 语义。修正后：

```text
WorkflowDefinition.expected_artifact=stage_handoff
  -> completion audit requires handoff.json

WorkflowDefinition contains prompt.generate_next_stage
  -> completion audit requires next-stage-prompt.md

Definition declares neither
  -> both derive as NOT_APPLICABLE and do not produce missing gaps
```

`NOT_APPLICABLE` 当前是由 persisted `WorkflowDefinition` 与 completion audit 的
definition-aware projection 共同证明的判定，不是新增的 completion-audit 字段。该 audit
只负责完成状态的权威读模型，不参与 workflow 成败路由。

查询侧旧的 fallback 已删除：`WorkstreamQueryService` 不再根据文件是否存在重新推断
handoff/prompt applicability，也不再把两者固定列为所有 completed workflow 的 required
artifacts；缺失 persisted completion audit 时只报告 `completion audit is missing`，损坏的
audit 则 fail visible 为 `invalid`。

Cannot-bypass 证明：

1. terminal human acceptance `approve` 后 workflow=`COMPLETED`；
2. 没有 handoff/next-stage artifacts 时 completion audit=`ready`, `gaps=[]`；
3. definition 声明 `stage_handoff`/`prompt.generate_next_stage` 时，缺失仍必须
   `needs_review`；
4. experiment 恢复任何被删尾部节点/边，或改动 A1-A9，graph equality test 失败；
5. full definition 仍为 32 nodes / 49 edges / 10 Agent calls，其 acceptance approval 仍路由
   A10。

验证：

- focused：`WorkflowFileLedgerTest` 35/0；
- focused：`TerminalDeliveryWorkflowExperimentTest` 1/0；
- focused：`WorkstreamApiControllerTest` 22/0；
- full Maven reactor：9/9 modules `BUILD SUCCESS`；
- 首次 sandbox full run 的两个旧 process-tree test 因 macOS `sysctl Operation not
  permitted` 无效；非沙箱重跑后全绿，不记为产品失败。

### Boundary Review

```text
Boundary Review: PASS

Declared by:
  external WorkflowDefinition experiment fixture.

Proven by:
  exact graph-delta test and ledger terminal-completion tests.

Consumed by:
  RoutingEngine for terminal transition;
  CompletionAuditWriter for definition-aware artifact applicability.

Enforced before:
  no approved successor exists, so RoutingEngine terminates before A10 can start.
  The completion audit is persisted before workflow/current.json is written COMPLETED,
  but audit ready/needs_review is a projection and does not control completion.

Failure route:
  a definition that declares next-stage artifacts still reports their absence;
  full workflow remains unchanged.

Cannot-bypass:
  A1-A9 equality, exact omitted-node set, exact edge delta, terminal ledger test,
  query fallback removal, persisted-audit consumer test, and full reactor.

Residual risk:
  no provider real-case has yet proven 9 actual Agent runs or measured savings;
  experiment definition is test-only and is not a bundled product variant.
```

### 唯一下一任务

`A10 terminal-delivery same-case Verification Gate`：

1. 独立 review 并提交 deterministic checkpoint；
2. 选择一个明确“验收后即结束”的已有 A3 case；
3. 用本 test-only external definition 运行，不添加内置 variant；
4. gate 唯一目标：证明 actual Agent runs 从 10 变为 9，workflow
   `COMPLETED`，acceptance/target tests/change scope/lineage 不退化，且缺失
   handoff 被记为 `NOT_APPLICABLE`；
5. 该 real-case 通过前，不得宣称 token/耗时收益，不得把 experiment
   晋升为默认或内置 workflow。

## 18. Production Variant Promotion Result

上述 experiment 与 same-case Gate 已完成。workstream `ws-20260805T084458Z-54Y3`
证明同一 A3 从 10 次 Agent/provider occurrence 降为 9 次，同时保持
`COMPLETED / ACCEPT`、revision=0、correction=0、change scope PASS 和独立测试 PASS。

随后没有把它提升为默认流程，而是提升为显式可选的 bundled variant：

```bash
dualctl start --workflow-variant terminal-delivery ...
./scripts/run-real-case.sh --variant terminal-delivery ...
```

正式 definition 的唯一来源为
`dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json`；
原 test resource 已删除，避免双写。`full` 保持默认，runtime 不自动选择 profile。
production wiring 与三个 bundled variants 的 contract/handler/permission/runner consistency
由 deterministic tests 和 full reactor `1161/0/0` 强制；没有新增 registry、engine、DSL
或自动推荐逻辑。
