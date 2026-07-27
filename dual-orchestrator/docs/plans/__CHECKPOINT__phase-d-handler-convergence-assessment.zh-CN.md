# Phase D Handler Convergence Assessment

日期：2026-07-27

状态：`PASS / TWO SUBTRACTION SLICES ENFORCED + PROVEN`

## 1. 目标和边界

Phase D 只推进两个总体目标：

1. node executor/handler 只负责编排；
2. 已存在的领域规则由唯一 owner 消费，删除 handler 内的重复判断。

本 assessment 不预设 handler 数量目标，不新增 module、engine、registry、event bus
或通用 framework。只有代码证明存在重复 owner 时才允许修改 production。

## 2. 当前规模

`dual-runtime` 与 `dual-adapter-acpx` 当前共有 12 个 concrete handler，另有
`TaskHandler`、`DecisionHandler`、`GateHandler` 三个边界接口。所有
`*Handler.java` 合计 4,942 行。

主要体量集中在：

| Handler | LOC | 当前职责 | Assessment |
| --- | ---: | --- | --- |
| `ContextBuildPackageHandler` | 1,015 | context 装配、section availability、budget、metadata、rendering | `WARN`：职责较多，但需逐条证明重复 owner |
| `HumanReviewGateHandler` | 647 | gate 打开、review packet projection、operator guidance | `FAIL`：本地重复维护 decision 正负语义 |
| `AcpxAgentTaskHandler` | 644 | ACP execution 编排、typed result acquisition、metadata/timeline | `WARN`：较大；`ProcessUsability` 当前是明确 authoritative calculation，不在首切片移动 |
| `ArtifactValidateHandler` | 551 | Git evidence 收集、调用 `ChangeScopeEvaluator`、写 evaluation | `WARN`：Git I/O 和 orchestration 混合，但 change decision 已有 owner |
| `ContractCompileHandler` | 425 | proposal/gate resolution、TaskContract 编译与工件写入 | `WARN`：编译职责集中，但尚未证明第二 owner |
| `ReportGenerateFinalHandler` | 391 | final report projection | `PASS-WARN`：体量大但主要是 projection，不因 LOC 单独拆分 |
| `FailureClassifyHandler` | 308 | 收集 failure facts、委托 `FailureArbiter`、写 typed result | `WARN`：需后续核对 fact reading 是否重复 |
| `PromptGenerateNextStageHandler` | 243 | next-stage prompt projection | `PASS-WARN`：暂无控制 authority 证据 |
| delivery handlers | 481 合计 | prepare、commit、PR creation | `PASS-WARN`：职责按外部副作用分开，不能机械合并 |
| `ReviewDecisionHandler` | 132 | 读取 sidecar、lineage 检查、decision routing | `WARN`：与 `AgentEvidenceIndex` 有读取重叠，但错误语义尚不等价 |

## 3. 已证明的重复 owner

### Human review decision polarity

旧路径：

```text
AgentOutputContractDocument.decision
  -> HumanReviewGateHandler.POSITIVE_DECISIONS / NEGATIVE_DECISIONS
  -> operatorGuidance.recommendedAction
```

同一 decision 在 runtime 已由 `StageOutcome.fromDecision` 归一化，并被
`StageOutcomeCompiler`、failure classification 和 artifact semantic validation 消费。
因此 handler 本地集合是第二套 decision vocabulary。

目标路径：

```text
AgentOutputContractDocument.decision
  -> StageOutcome.fromDecision
  -> HumanReviewGateHandler operator guidance
```

`StageOutcome` 是 decision polarity 的 authoritative owner；
`HumanReviewGateHandler` 只把 canonical outcome 投影为允许的 human action 建议。
gate allowed actions 和真正的 gate transition 仍由 workflow/ledger boundary 拥有。

## 4. 第一个不可再拆的 subtraction slice

允许的 production 修改只有：

1. `StageOutcome` 补齐已有 handler 支持的 `PASS -> READY`；
2. 删除 `HumanReviewGateHandler` 的 positive/negative 字符串集合；
3. handler 通过 `StageOutcome.fromDecision` 判断正负；
4. cannot-bypass test 证明详细拒绝
   `REJECT_CONTEXT_INSUFFICIENT` 不能落入默认 `approve`；
5. regression test 证明 `PASS` 仍归一化为 `READY`。

验收：

- handler 中不再存在 decision polarity 字符串集合；
- `StageOutcome` 是唯一 polarity owner；
- `REJECT_CONTEXT_INSUFFICIENT` 推荐 `revise`；
- `PASS` 仍是 positive outcome；
- targeted tests、`dual-runtime` full suite 和 full reactor 通过；
- production handler LOC 净减少；
- 不修改 gate transition、allowed actions、ledger 或 workflow definition。

实施结果：

- `HumanReviewGateHandler` 的两套 decision 集合已删除；
- `StageOutcome` 补齐 `PASS -> READY`，所有 polarity 判断委托该 owner；
- `REJECT_CONTEXT_INSUFFICIENT` cannot-bypass test 证明详细拒绝不会回落为默认
  `approve`；
- targeted tests 63/0；
- `dual-runtime` full suite 443/0；
- full reactor 9/9 modules、1,131 tests、0 failures；
- production 净变化 `-4 LOC`；
- production sweep 对 `POSITIVE_DECISIONS` / `NEGATIVE_DECISIONS` 为 0。

## 5. 未授权的后续候选

以下仅登记，不属于首切片：

- `ContextBuildPackageHandler` 继续委托 context section owner：必须先证明具体
  规则在两个以上消费者重复；
- Git evidence collection 从 `ArtifactValidateHandler` 移出：只有出现第二消费者
  或测试隔离收益足够时才考虑；
- `failureReason`、acceptance reader、delivery projection 的 owner 收敛：
  一次只处理一个已证明分歧。

不得因为类较大就拆类，也不得以 Phase D 名义新增 handler framework。

## 6. 第二个不可再拆的 subtraction slice

发现：

`ReviewDecisionHandler` 是 workflow decision routing 的控制 handler，但原实现自己
完成 routed ref 选择、sidecar JSON 解析、lineage 校验和 typed read failure 构造。
这与 Phase C 已确立的 `AgentEvidenceIndex` 重复。

收敛后：

```text
node input refs
  -> AgentEvidenceIndex.latestDecisionFromRefs
  -> validated AgentOutputContractDocument / typed DecisionRead failure
  -> ReviewDecisionHandler validates allowed decision
  -> NodeResult
  -> workflow routing
```

边界证明：

- Declared by：node instance `inputRefs` 与 ledger-bound sidecar；
- Proven by：`AgentOutputLineageVerifier`；
- Consumed by：`ReviewDecisionHandler`；
- Enforced before：decision `NodeResult` 和 outgoing workflow edge；
- Failure route：missing sidecar、unreadable JSON、lineage mismatch、missing decision
  均 fail closed；
- Cannot-bypass：最后一个 routed sidecar 无效时，不得回退较旧的有效 sidecar。

实施结果：

- `ReviewDecisionHandler` 删除私有 sidecar scanner/parser/lineage verifier 和
  `DecisionContractRead`，减少 53 production LOC；
- `AgentEvidenceIndex` 增加有界 `DecisionRead`，保留原有输入 ref 顺序与 typed
  failure code；未新增文件、模块、registry 或 framework；
- handler 中直接 `AgentOutputContractDocument.read` /
  `AgentOutputLineageVerifier.violation` 搜索为 0；
- targeted routing tests 75/0；
- full reactor 9/9 modules、1,132 tests、0 failures；
- committed clean SHA：`2f26cf5`；
- README controlled real-case：
  `docs/testing/real-case-reports/20260727T140143Z/report.md`，
  workstream `ws-20260727T140433Z-K8F9`；
- fresh Maven build、provider probe 与 artifact freshness 均通过；
- workflow 为 `COMPLETED`，final acceptance 为 `ACCEPT`，benchmark 为 `PASS`；
- `design.decision` 从 strict sidecar 路由为 `READY_FOR_ARCH_REVIEW`；
- 三次 `plan.decision` 分别从 strict sidecar 路由为
  `NEEDS_REVISION`、`NEEDS_REVISION`、`READY_TO_IMPLEMENT`，证明 revision 与
  approval 两类 edge 均消费同一个 typed owner；
- `acceptance.decision` 从 strict sidecar 路由为 `ACCEPT`；
- 所有 decision result 均为 `source=contract-sidecar`，ledger 中无
  `CONTRACT_SIDECAR_*` failure；
- target diff 仅 `README.md`，独立复验 `npm test` 通过；
- 第二个 slice 达到 `ENFORCED + PROVEN`，第三个 slice 尚未激活。
