# Full Workflow 复杂度价值证明

日期：2026-07-28
状态：`T1-T5 COMPLETE / ONE A3 OBSERVED BENEFIT PROVEN / T6 REAL-CASE FAIL`

## 1. 决策问题

本文只回答一个问题：

> 在保留已经证明必要的可靠性内核后，Dual 当前 full workflow 的额外节点、
> Agent 调用、人工 gate、context build 和 revision 成本，是否产生了足以覆盖
> 这些成本的质量与可靠性收益？

这不是新的 phase，也不是新的架构建设计划。它是
`post-phase-d-integrated-verification-and-efficiency-plan.zh-CN.md` 中 T1-T5 的
裁决口径。

当前不能预设以下任一结论：

- 不能因为 full workflow 最终 `ACCEPT`，就断言全部复杂度合理；
- 不能因为节点和调用数量较多，就断言当前架构错误；
- 不能把一次 provider 波动解释为 workflow 设计问题；
- 不能把删除 gate、review 或 validation 后更快，解释为优化成功；
- 不能用不同 case、不同 provider/model 或不同验收标准比较前后收益。

## 2. 已确认基线

### 2.1 静态 workflow 构成

当前 `agent-lifecycle.full.json`：

| 类型 | 数量 | 直接消耗 provider token |
| --- | ---: | ---: |
| `acpx.agent_run` | 10 | 是 |
| `context.build_package` | 6 | 否 |
| `DECISION` | 4 | 否 |
| `GATE` | 6 | 否，但产生人工等待和操作成本 |
| 其他 system task | 6 | 否 |
| 合计 | 32 | - |

当前 definition 有 56 条 edge。Edge 数量表达 PASS、revision、recovery、human action
和 terminal route；它不等于运行次数或 Agent 调用次数。

### 2.2 已知真实运行

| Case | Duration | Agent runs | Gate rounds | Revisions | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| README | 13m19s | 14 | 6 | 2 | `COMPLETED / ACCEPT` |
| A3 API | 22m22s | 20 | 6 | 5 | `COMPLETED / ACCEPT` |

静态 happy path 已包含 10 个 Agent 节点。14/20 次实际调用中的额外调用必须从
ledger、run metadata 和 iteration/revision evidence 归因，不能直接归因给
“32 个节点”。

### 2.3 已证明值得保留的可靠性内核

以下边界已经由 deterministic test、cannot-bypass test、replay 或 controlled
real-case 证明，不进入“为了减少节点而删除”的默认候选：

- `WorkflowDefinition` 作为流程结构 SoT；
- workflow/node/execution durable ledger；
- strict `AgentResultEnvelope` 与 schema validation；
- typed artifact publication、lineage 和 routed `inputRefs`；
- `ArtifactContractValidator` 与 typed domain decision；
- `RoutingEngine` 作为 transition owner；
- human gate durable state 与 canonical action；
- `RecoveryAssessment` 与 runtime/semantic failure 分离；
- `TaskContract.scope`、Git actual changes 与 `ChangeScopeEvaluation`；
- authoritative lifecycle state 必须来自 ledger/read model；本文不独立证明所有
  CLI/API/report projection surface 已完成统一消费。

这些边界可以在证明有重复 owner 时继续做删除式收敛，但不能退回 Markdown/prose
routing、handler-local authority 或 projection 自造状态。

## 3. 尚未证明的价值假设

### 3.1 十个静态 Agent 调用

当前 happy path 的 Agent 调用包括：

```text
Claude intake
Codex product review
Claude design
Codex architecture review
Claude plan
Codex plan review
Claude implementation
Claude self-test report
Codex acceptance review
Claude handoff
```

需要证明：

- 每个 review 是否发现了上游未发现、且会影响最终质量的事实；
- 每个 producer 是否产生下游必需、不能由既有 typed artifact 派生的结果；
- implementation 与 self-test 分开调用是否改善验证质量；
- handoff 是否被真实下游消费，而不是只生成报告；
- 某一调用删除或合并后，哪个明确的质量 invariant 会丢失。

当前裁决：`UNPROVEN`。这是优先级最高的成本区域，但 T2 baseline 前不得决定删除
哪个调用。

### 3.2 六个 Human Gate

当前 gate：

```text
requirement
contract
design
plan
acceptance
next stage
```

需要证明：

- 每个 gate 的独特、不可替代的人类责任；
- approve/revise/reject 的真实分布；
- 人工反馈是否改变 typed artifact、scope、implementation 或 acceptance；
- gate 是否位于不可逆副作用之前；
- 对简单任务固定启用是否比减少 gate 更安全。

当前裁决：`UNPROVEN FOR ALL TASK SIZES`。Human gate 状态模型保留，但六个 gate
作为所有 full case 的固定默认形状尚未证明。

### 3.3 六个 Context Build 节点

`ContextPackage` 的 typed lineage 和 bounded context 边界已经证明必要。尚未证明的
是每次 context projection 是否必须作为独立 workflow node。

需要证明：

- 每个 context node 实际减少了多少输入 token；
- cache、summary、pointer-only 是否真正生效；
- 它是否阻止缺失或错误 lineage 的下游 Agent 启动；
- 独立 node occurrence 是否提供了不可替代的恢复、审计或 cannot-bypass 边界；
- 若内联到现有 execution orchestration，是否会丢失 authoritative fact。

当前裁决：`BOUNDARY PROVEN / NODE SHAPE UNPROVEN`。

### 3.4 Revision

Revision 必须分为：

```text
valuable revision
  = 发现真实业务、设计、计划、实现或验证缺陷，并改善最终结果

orchestrator-induced revision
  = context 缺失、lineage 丢失、schema/prompt 冲突、错误分类或重复审查
```

需要逐次记录：

- producer、reviewer、decision、reason；
- revision 前后 artifact 的语义差异；
- 是否修复最终验收会发现的缺陷；
- 是否重跑了与修复无关的 Agent 节点；
- 是否源于 Dual 自有边界缺陷。

当前 A3 的 5 次 revision 不能整体计为“质量收益”或“浪费”，必须逐次归因。

### 3.5 32 个节点与 56 条边

节点/边价值主要是可理解性、可恢复性和路由正确性，不直接等价于 token 成本。

需要证明：

- 每个 system/decision node 是否拥有独立 typed input/output 或 side effect；
- 哪些 edge 有 deterministic、replay 或 real-case 覆盖；
- 是否存在多个 edge 表达同一个 recovery/revision 语义；
- 修改一个 contract 是否需要在多个位置同步相同规则；
- 是否存在纯机械 projection node，可以不改变 SoT 地合并。

当前裁决：`MAINTENANCE VALUE UNPROVEN`。不能以 node count 为单独删除理由。

### 3.6 Handler 规模

Phase D 已删除四个已证明的重复 owner。剩余 handler 较大不是独立缺陷。

只有满足以下条件之一，handler 才能重新成为 subtraction 候选：

- 同一控制事实存在两个 producer/reader/decision owner；
- handler 内存在可删除的自然语言或 filesystem 推断；
- metrics 证明其重复执行造成主要 token/latency 成本；
- 变更一个规则需要同步修改多个 handler；
- cannot-bypass test 证明旧路径可绕过 authoritative owner。

## 4. “值得”的裁决模型

### 4.1 单个 workflow 环节的价值记录

每个被评估环节必须回答：

```text
Element:
Type:
Authoritative input:
Authoritative output:
Unique invariant protected:
Observed activation count:
Token/duration/wait cost:
Decision or artifact changed:
Defect caught:
Downstream consumer:
Counterfactual if removed:
Evidence reliability:
Verdict:
```

字段来源与最低可靠性：

| 字段 | 允许来源 | 可靠性 |
| --- | --- | --- |
| Agent occurrence / traversal | node/execution ledger + `runs/*/run.json` | `VALID` |
| token/cache/provider/model | `runs/*/run.json` 原始 usage；metrics 仅作一致性 projection | measured 且 identity 一致为 `VALID`；estimated/session snapshot 为 `WEAK`；缺失或配置不一致为 `INVALID` |
| decision/artifact changed | strict sidecar、publication/lineage、前后 typed artifact hash/字段差异 | lineage 和 occurrence 可验证为 `VALID`；仅 Markdown/prose 为 `INVALID` |
| defect caught | strict review decision/must-fix + 后续 revision 中对应 typed artifact 或目标 diff 变化 | 两端可关联为 `VALID`；只有 reviewer 叙述为 `WEAK` |
| gate action | gate ledger canonical action | `VALID` |
| gate wait | gate opened 到 resolved 的 ledger wall-clock interval | 只表示等待时长，不表示人工工作量 |
| revision reason | iteration/event + routed strict decision | 可关联为 `VALID`；仅最终报告归因为 `WEAK` |

`Verdict` 只能是：

- `KEEP_PROVEN`：存在唯一边界或真实增量质量证据；
- `KEEP_PENDING_MORE_DATA`：有合理职责，但样本不足；
- `SUBTRACTION_CANDIDATE`：成本可量化且没有独特价值；
- `INVALID_EVIDENCE`：provider、freshness、metrics 或 case 不可比较。

### 4.2 质量收益

质量收益必须来自至少一种可追溯事实：

- review 捕获真实 must-fix，并在下一 revision 中被修复；
- gate 产生改变 scope、contract、plan、implementation 或 acceptance 的人工决定；
- context node 阻止缺少 required typed evidence 的 Agent 启动；
- validator 阻止错误 artifact、lineage、change scope 或 delivery side effect；
- recovery owner 阻止错误 scheduler retry/resume；
- subtraction 前后 target tests、acceptance 和 change scope 均保持，且成本下降。

“输出内容更详细”“看起来更安全”“最终通过”不构成独立收益证据。

### 4.3 成本

必须复用现有：

```text
runs/*/run.json
  -> MetricsProjectionWriter
  -> metrics/workstream-metrics.json
```

最少比较：

- wall-clock duration；
- Agent run count；
- measured input/output token；
- cached read/write token 和可计算时的 cache ratio；
- gate rounds 与人工等待；
- revision count；
- protocol correction count；
- top nodes by duration/token；
- repeated semantic stages/context sections。

不得新建第二套 token、cache、cost 或 duration 计算。

## 5. 执行计划

本计划不新增任务编号，直接使用既有 T1-T5。

### T1：证明最新组合版本可运行

只证明：

```text
latest clean SHA
  -> A3 full workflow
  -> COMPLETED + acceptance PASS
  -> Phase A-D 组合边界无回归
```

T1 不证明 full workflow 值得，也不允许在运行中调 prompt、删节点或改 gate。

T1 acceptance PASS 统一为：

```text
ACCEPT
或
ACCEPT_WITH_NOTES，且 notes 不得表示 Phase A-D boundary、target acceptance、
change scope、lineage、routing 或 required verification 退化
```

### T2：建立价值 baseline

在既有 efficiency baseline 之外，必须增加：

- 32 个 definition node 的实际 traversal；
- 10 个静态 Agent node 的 occurrence、token、duration；
- 额外 Agent occurrence 的 revision/correction 原因；
- 6 个 gate 的 action 和等待；
- 6 个 context node 的 output size、strategy 和下游消费；
- 每次 review/gate/revision 的 artifact/decision delta。

这是 read-only evidence handoff，不改变 runtime state。

### T3：只选择一个最大、可证伪的候选

候选选择顺序：

1. orchestrator-induced revision；
2. 无独特 defect/gate yield 的重复 Agent 调用；
3. 重复 context projection；
4. 无独特人工责任的 gate；
5. 只增加维护成本的机械 node/edge。

候选必须同时满足：

- 成本在 T2 中可量化；
- 没有已证明的唯一 boundary owner；
- 存在明确旧路径可删除或调用可减少；
- 不需要新 module、engine、registry、DSL 或 state machine；
- 能在同一 A3 case 中做前后对照。

进入 T4 前，每个候选还必须填写：

```text
Current producer:
All downstream consumers:
Authoritative fact protected:
Fact owner after subtraction:
Counterfactual when removed:
Routing / Agent start / side effect that must still fail closed:
Candidate-specific cannot-bypass test:
```

任一项无法回答，候选不得进入 production 修改。一次样本中没有观察到 defect，
不能单独证明该环节没有低频边界价值。

没有候选时结论必须是 `NO_SAFE_OPTIMIZATION`。

### T4：一个不可再拆的 subtraction

只允许一个候选：

```text
一个 Agent 调用
或一个重复 context occurrence
或一个 gate
或一个纯机械 node/edge
```

禁止把多个层面一起改动。完成时必须：

- 旧路径物理删除；
- authoritative owner 不增加；
- deterministic/cannot-bypass/full reactor 通过；
- 不宣称收益，等待 T5。

### T5：同一 A3 前后对比

PASS 必须同时满足：

- 相同 task、target baseline、provider/model 和 Verification Gate；
- workflow `COMPLETED`；
- acceptance、target tests、change scope 和 typed lineage 不退化；
- 选定指标真实下降；
- measurement reliability 可比较；
- 没有通过跳过 required review、放宽 parser/contract 或绕过 gate 换取速度。

若 provider 或 usage evidence 不可比，结论为 `INVALID`。

归因边界：

- Agent occurrence、node traversal、gate count、revision count 等结构性指标可直接
  归因于 definition/subtraction；
- token、duration、cache 等 provider-sensitive 指标，单次前后运行只能作为观察，
  只有 usage reliability 可比且结构性变化能够解释差异时才可作为支持证据；
- 质量结论只能表述为“该 A3 case 未退化”，不能外推为整个 full workflow 或所有
  项目都不会退化。

## 6. 阶段性决策

在 T1/T2 完成前，当前判断固定为：

| 架构部分 | 当前裁决 |
| --- | --- |
| workflow/ledger/typed control kernel | `KEEP_PROVEN` |
| central validator/routing/recovery/change boundary | `KEEP_PROVEN` |
| 10 个静态 Agent 调用 | `KEEP_PENDING_MORE_DATA` |
| 6 个 gate 的 durable state model | `KEEP_PROVEN` |
| 6 个 gate 的固定 full-workflow 配置 | `KEEP_PENDING_MORE_DATA` |
| bounded ContextPackage boundary | `KEEP_PROVEN` |
| 6 个独立 context node | `KEEP_PENDING_MORE_DATA` |
| 32 node / 56 edge 的完整默认形状 | `KEEP_PENDING_MORE_DATA` |
| 剩余大 handler | `NOT_A_CANDIDATE_WITHOUT_NEW_EVIDENCE` |

这意味着：

> 当前架构不回退，但 full workflow 必须承担价值举证责任。举证失败的具体环节进入
> 单点 subtraction；已经证明的可靠性内核不因效率优化被削弱。

## 7. 明确禁止

- 不创建“Workflow Value Engine”、评估 registry 或新 metrics 平台；
- 不新增 Phase E；
- 不一次设计 lean/standard/strict 三套动态 workflow；
- 不先按直觉把 10 次 Agent 调用改成 4 次；
- 不用 gate approve 比例直接自动删 gate；
- 不用 handler LOC 或 edge count 直接决定重构；
- 不修改 strict artifact、lineage、ledger 或 recovery 边界换取速度；
- 不运行不同 case 后做伪 before/after；
- 不把一次 provider INVALID 当成架构价值证据。

## 8. 完成定义

本文对应的价值证明完成，不要求预设“节点必须降到多少”，而要求：

1. 最新组合 SHA 有一次完整 A3 PASS；
2. 10 个 Agent 节点、6 个 gate、6 个 context 节点和 revision 有可信成本/收益归因；
3. 至少一个高成本低价值候选被选出，或得到有证据的
   `NO_SAFE_OPTIMIZATION`；
4. 仅当 T3 选出候选时，一个 subtraction 经同 case 证明该 A3 case 质量不退化且
   结构性成本下降；`NO_SAFE_OPTIMIZATION` 是合法终点，不得为了满足进度制造 T4；
5. 没有新增第二套 SoT、metrics 计算或平台能力；
6. 最终报告明确被评估候选是 `KEEP_PROVEN` 还是已删除；未被干预的其余环节继续
   `KEEP_PENDING_MORE_DATA`，不得据此宣布整个 32-node/56-edge shape 已被证明合理。

## 9. Boundary Review

```text
Boundary Review: PASS FOR ONE BOUNDED A3 OPTIMIZATION

Declared by:
  WorkflowDefinition；
  workflow/node/execution ledger；
  strict sidecars；
  runs/*/run.json。

Proven by:
  T1 latest-SHA real-case；
  T2 read-only attribution；
  T4 deterministic/cannot-bypass tests；
  T5 v3 same-case comparison。

Consumed by:
  MetricsProjectionWriter -> metrics/workstream-metrics.json（projection）；
  T3 candidate selection；
  T4 single subtraction decision。

Enforced before:
  T4 production change activation；
  T5 provider comparison；
  the bounded claim that T4 preserved quality and reduced observed A3 usage。

Failure route:
  evidence missing/not comparable -> INVALID；
  no unique removable path -> NO_SAFE_OPTIMIZATION；
  quality regression -> revert/reject subtraction。

Cannot-bypass:
  no different-case comparison；
  no second metrics calculation；
  no parser/contract/gate bypass；
  no multi-candidate implementation。

Residual risk:
  当前样本主要是 README 与 A3；
  T5 v3 只证明一个同 baseline A3 样本的 observed benefit；
  provider input/cache 构成存在波动，不能把全部差额归因于 context subtraction；
  T1-T5 只裁决了一个候选在 A3 中的 bounded value，不能一次证明整个 full
  workflow 的普遍经济合理性；
  T6 pinned backend baseline 已在 ephemeral environment 与独立 test database
  上达到 191 tests PASS，frontend build 也通过；target requirements 的
  python-multipart manifest gap 仅通过显式 `0.0.32` supplemental pin 接受；
  T6 real-case 的 preflight、freshness、probes 与 usage reconciliation 均通过，
  但 7 个 Agent runs 内出现 2 次 semantic revision，并在已消费 1 次 bounded
  correction 后再次产生 prose+JSON；stop-loss 正确阻止继续执行。该样本为有效
  FAIL，未证明当前 full workflow 能完成中等复杂度任务。
```
