# 边界漂移走查

日期：2026-06-17

这份文档记录一次 real-case 暴露问题后的架构走查。目的不是列 TODO，而是识别哪些规则正在从领域边界漂移到 CLI、UI、handler 或 ad hoc registry 里。

## 背景

真实 cancer-clinic case 暴露了一个典型问题：

```text
Claude intake 节点失败
-> adapter 产出 typed reason: RECOVERABLE_AGENT_RECONNECT
-> workflow 正确停在 WAITING_FOR_RECOVERY
-> recovery read model 却只提示“修复外部原因”
```

底层分类已经存在，但 operator 得到的下一步动作不够具体。问题不在 workflow transition，而在 recovery read model 把不同 typed reason 又合并成了泛化文案。

这类问题反复出现，说明项目里还有几类边界漂移风险。

## 边界结论

### 1. Recovery reason 不能在投影层重新泛化

Source of truth：

- Agent adapter 输出 `acpx_failure.reason`
- `RecoveryAssessment` 负责把 typed failure 转成 operator action
- `WorkstreamAttention` / CLI / UI 只能消费 assessment

风险：

- 如果 read model 把 `RECOVERABLE_AGENT_RECONNECT`、`RECOVERABLE_RATE_LIMIT`、`RECOVERABLE_CONTEXT_WINDOW` 都显示成同一句“外部原因”，用户无法判断该重建 session、等待 quota，还是直接 retry。

处理：

- `RecoveryAssessment` 必须按 typed reason 生成稳定分类：
  - `agent_session_recovery`
  - `agent_capacity_recovery`
  - `agent_model_mismatch`
  - `artifact_contract_failure`
  - `semantic_revision_limit`
- CLI/API/UI 只显示这些分类和建议命令。

当前状态（2026-07-01）：

- `RecoveryAssessment` 已作为 operator-facing recovery read model，优先消费 typed `agent_execution_outcome`，再兼容 legacy `acpx_failure.reason`。
- `RecoveryAssessmentTest` 已覆盖 artifact contract failure 优先级、revision limit、output interrupted、auth failure、provider connection failure 和 execution outcome 驱动的 provider connection failure。
- `RecoveryShowCommandTest` 已覆盖 CLI 展示 `agent_session_recovery` 等 typed classification。

因此 recovery typed reason 到 operator action 的 P0 主路径已实现；后续风险在于新增 provider failure signal 时必须同步补 recovery assessment 测试，不能退回泛化文案。

### 2. Human gate action 只能由 ledger boundary 归一化

Source of truth：

- Workflow edge outcome
- Gate allowed actions
- `WorkflowFileLedger.resolveOpenGate`

风险：

- CLI、UI、autorun 各自把 `approve` 翻译成 `approved`，会导致相同动作在不同入口表现不同。

处理：

- CLI/API/UI 发送用户动作，例如 `approve`。
- Ledger 记录 `requested_action` 和 canonical `human_action`。
- 只有 ledger 能根据当前 gate 和 workflow edge 做动作归一化。

当前状态（2026-07-01）：

- `RoutingEngine.resolveOpenGate` 负责 gate action canonicalization，并在 node result 中同时写入 `requested_action` 和 canonical `human_action`。
- `WorkflowGateResolutionTest.approvingOpenGateRoutesByHumanAction` 已覆盖 ledger 将 open gate resolution 路由到 canonical edge。
- `ReviewDecideCommandTest` 已覆盖 CLI 入口提交 action 后由 ledger 边界完成归一化。

因此 human gate action normalization 的 P0 主路径已实现；CLI/API/UI 不应再各自翻译 `approve/approved`。

### 3. Workflow definition 和 artifact contract 不能长期双写

Source of truth：

- Workflow definition JSON：声明 node、edge、handler、transition。
- Artifact contract：声明每个 agent/decision node 的结构化输出。

当前风险：

- `ContractRegistry` 仍在 Java 里用 node id 注册合同。
- workflow JSON 和 Java registry 可能漂移，例如新增 node 后忘记补 contract，或 contract decision 与 edge outcome 不一致。

MVP 允许的短期状态：

- Java registry 可以暂时保留，因为它是集中式 validator，不是散落 if-else。

必须保留的保护：

- 增加 definition-contract consistency test：
  - 所有 agent / decision / classifier node 必须有 contract。
  - contract allowed decisions 必须覆盖该 node 的 outgoing edge outcomes。
  - contract 里不存在 workflow definition 没有的 node。

当前状态（2026-07-01）：

- `ContractRegistryTest.registryEntriesPointToBundledWorkflowNodes` 已防止 Java registry 中出现 workflow definition 不存在的 contract node。
- `ContractRegistryTest.bundledWorkflowNodesThatNeedContractsHaveContracts` 已防止 bundled workflow 中需要结构化输出契约的节点漏配 contract。
- `ContractRegistryTest.structuredArtifactNodesDeclareExpectedArtifactAndCompilerInWorkflowDefinition` 已校验 workflow definition 的 `expected_artifact` 与 `ContractRegistry.outputArtifactTypes` 对齐。
- `ContractRegistryTest.decisionNodeRoutingOutcomesMatchAllowedContractDecisions` 和 `failureClassifierRoutingOutcomesMatchAllowedContractDecisions` 已校验 decision / classifier 的 contract decisions 与 routing edges 对齐。

因此该保护项不再是待做项；它仍是必须持续运行的防漂移测试。

长期方向：

- 把 node output contract 移到 workflow resource 或独立 contract resource，由 loader 一次加载并校验。

### 4. Handler registry 可以是装配点，不能成为隐藏流程定义

Source of truth：

- Handler type 是 workflow definition 的引用。
- Handler implementation 是 runtime capability。

当前风险：

- `CliWorkflowRuntimeFactory` 手写 handler list。
- `CliSupportedHandlers` / workflow validate / runtime registry 可能出现支持范围不一致。

MVP 允许的短期状态：

- 单机 CLI 第一版可以手写装配，但必须有测试证明 bundled workflow 中的 handler type 都能被 runtime resolve。

当前状态（2026-07-01）：

- `AcpxWorkflowHandlerRegistryFactoryTest.resolvesEveryBundledWorkflowHandler` 已加载 bundled `full` / `contract` workflow，并验证每个 node 的 `kind` / `handlerType` 都能由当前 acpx runtime registry resolve。
- `HandlerRegistryTest` 已覆盖 handler lookup、missing handler fast-fail 和 duplicate handler type 拒绝。

因此 handler registry 已有最小防漂移测试；长期方向仍是 capability catalog 继续作为唯一装配边界，不能让 CLI validate、runtime registry 和文档各自维护不同支持范围。

长期方向：

- handler capability catalog 统一输出 supported handler types。
- CLI validate、runtime registry、workflow print/default 共用同一 capability source。

### 5. 投影不能扫描文件后自造生命周期语义

Source of truth：

- `workflow/current.json`
- node instance documents
- recovery assessment
- goal satisfaction
- delivery merge plan/status
- artifacts metadata

当前风险：

- `SummaryCommand`、`TraceCommand`、`WorkstreamQueryService` 仍有部分字符串状态判断和文件扫描。

MVP 允许的短期状态：

- 文件扫描可以用于查找 artifact 文件，但不能推导 workflow status、gate action、retry safety 或 completion truth。

必须补的保护：

- UI、CLI、API 对 “现在跑到哪、为什么停、下一步做什么” 必须来自同一个 read model。
- 如果新增投影字段，优先加到 centralized read model，再由各端消费。

## 需要立即执行的防漂移检查

每次修改 workflow / lifecycle / artifact / recovery / human gate / projection 前，必须回答：

```text
1. 这个规则的 source of truth 是什么？
2. 它是 Definition、Instance、Execution、Artifact、Gate、Goal 还是 Projection？
3. 是否已经存在同类规则？如果存在，为什么不复用？
4. 是否有 CLI / API / UI / report 多端投影？是否共用同一个 read model？
5. 是否需要 consistency test 防止 workflow JSON、contract registry、handler registry 漂移？
```

如果回答不出来，不能继续写 handler-local if-else。

## 优先修复项

| 优先级 | 项目 | 原因 | 状态 |
|---|---|---|---|
| P0 | Recovery typed reason 到 operator action 的映射 | real-case 已暴露，直接影响恢复能力 | 已覆盖，新增 signal 需补测试 |
| P0 | Human gate action normalization 去重 | 防止 CLI/UI/API 行为漂移 | 已覆盖，继续运行 |
| P0 | Workflow JSON 和 ContractRegistry 一致性测试 | 防止新增节点后 contract 漏配 | 已覆盖，继续运行 |
| P1 | Handler capability catalog | 防止 runtime/validate/docs 支持范围不一致 | 已有最小测试，继续收口 |
| P1 | Projection read model 收口 | 防止 trace/status/operator panel 各说各话 | 待做 |

## 不做的事

- 暂不引入通用规则引擎。
- 暂不把所有 registry 立即配置化。
- 暂不为了“消灭所有字符串”重写 domain model。

原因：当前目标是主生命周期闭环和边界稳定。过早引入规则引擎会增加配置复杂度，反而让边界更难守住。
