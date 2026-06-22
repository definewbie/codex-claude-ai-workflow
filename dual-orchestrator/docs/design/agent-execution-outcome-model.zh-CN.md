# AgentExecution Outcome Model

## 背景

`dual-orchestrator` 的现有骨架基本成立：

- `WorkflowDefinition / NodeDefinition` 负责定义；
- `Workstream / WorkflowInstance` 负责用户目标和流程实例；
- `NodeInstance / NodeExecution` 负责节点实例和节点执行；
- `AgentTask` 负责派工：把某个节点交给哪个 agent、用什么 prompt、什么权限和什么 session；
- ledger 负责记录事实，UI / CLI / report 只做投影。

当前缺口不在整体骨架，而在 `AgentTask -> NodeExecution` 中间缺少一个一等领域对象。

现在 acpx / Claude / Codex 的执行事实散落在：

- stdout / stderr；
- process exit code；
- `agent needs reconnect` 等 adapter 文本；
- terminal `dual-control`；
- required `<dual-artifact>` 校验；
- `run.json.status`；
- recovery classifier；
- NodeExecution 状态；
- trace / operator projection。

这导致同一次真实运行可能同时出现：

```text
run.status = completed
exitCode = 5
stderr = agent needs reconnect
terminal dual-control = present
required artifact = missing
node.status = FAILED
recovery.category = artifact_contract_failure
```

这不是单个 prompt 或 if-else 能修好的问题，而是 `AgentExecution` 没有成为聚合根。

## Final Decision

保留现有系统骨架，但补齐 `AgentExecution` 聚合根和 `AgentExecutionOutcome`。

```text
AgentTask
  -> AgentExecution
  -> AgentExecutionOutcome
  -> NodeExecution transition
  -> WorkflowInstance transition
```

状态机不再消费半原始 adapter 信号。`NodeExecution` 只能消费 `AgentExecutionOutcome`。

## 模型边界

### Definition

定义层仍由 workflow JSON / node definition / contract definition 表达。

```text
WorkflowDefinition
NodeDefinition
ContractDefinition
```

定义层只说明“应该做什么、期待什么 contract”，不记录一次运行发生了什么。

### Instance

实例层表达一次用户目标的生命周期。

```text
Workstream
WorkflowInstance
NodeInstance
```

实例层不应该知道 acpx stderr、provider reconnect、tool transcript 细节。

### Execution

执行层表达一次具体推进。

```text
WorkflowExecution
NodeExecution
AgentTask
AgentExecution
```

`AgentExecution` 是外部 agent 调用的聚合根。它拥有并守住以下不变量：

1. 一次 agent 调用只有一个最终 outcome。
2. stdout / stderr / exit code / reconnect / timeout 都只是 raw facts。
3. terminal `dual-control` 只是 `TerminalControlResult`，不是成功。
4. required `<dual-artifact>` 校验必须进入 outcome。
5. reconnect 是 adapter signal，不是 node status。
6. `run.json.status` 必须由 outcome 派生，不能独立判断。
7. `NodeExecution` 不能直接读取 stdout / stderr / exit code 决定状态。

## AgentExecution 聚合

建议模型：

```text
AgentExecution
  id
  workstreamId
  workflowExecutionId
  nodeExecutionId
  agentTaskId
  agent
  session
  promptRef
  outputRef
  startedAt
  finishedAt
  processResult
  adapterSignals[]
  terminalControlResult
  artifactContractResults[]
  toolUsage
  tokenUsage
  outcome
```

### ProcessResult

进程层事实。

```text
ProcessResult
  command
  pid
  exitCode
  usability
  durationMs
  stdoutPath
  stderrPath
  interrupted
  timedOut
```

它只回答“进程发生了什么”，不回答节点是否成功。

#### ProcessUsability

`ProcessUsability` 是 resolver 判断 outcome 的进程层输入。它不能由
`NodeExecution`、recovery 或 UI 自行推断。

```text
ProcessUsability
  EXITED_ZERO
  EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT
  EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT
  TIMED_OUT
  INTERRUPTED
  STALLED
  KILLED_AFTER_RECOVERABLE_SIGNAL
```

判定规则：

| Usability | 触发条件 | 是否允许成功 | 说明 |
| --- | --- | --- | --- |
| `EXITED_ZERO` | process exit code 为 0。 | 可以 | 仍需 terminal control 和 required artifact 都合法。 |
| `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` | process exit code 非 0，但 terminal control 和 required artifact 都完整合法。 | 可以，但只能是 `SUCCESS_WITH_WARNINGS` | 用于 acpx / ACP 已报 reconnect，但 agent 输出已经完整落账的情况。 |
| `EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT` | process exit code 非 0，且 terminal control 或 required artifact 不完整。 | 否 | 不能被 terminal `dual-control` 单独放行。 |
| `TIMED_OUT` | adapter 或 runtime 超时。 | 否 | 进入 timeout outcome。 |
| `INTERRUPTED` | 用户或系统主动中断。 | 否 | 不自动 retry，除非用户显式恢复。 |
| `STALLED` | health tracker 判断长时间无有效 progress。 | 否 | 属于 adapter execution failure。 |
| `KILLED_AFTER_RECOVERABLE_SIGNAL` | adapter signal 触发后被 watchdog / transport 有界杀进程。 | 否 | recovery 需要保留触发 signal。 |

注意：

- 非零 exit code 不能直接等于失败；
- 非零 exit code 也不能因为 terminal `dual-control` 存在而直接成功；
- 是否可成功必须由 `ProcessUsability + TerminalControlResult + ArtifactContractResult`
  一起决定。

### AdapterSignalResult

adapter 生命周期事件。

```text
AdapterSignal
  type
  severity
  source
  observedAt
  message
  sessionId
  superseded
```

典型类型：

```text
AGENT_RECONNECT_REQUIRED
SESSION_RESUME_FAILED
SESSION_RECREATED
AUTH_FAILED
RATE_LIMITED
CONTEXT_WINDOW_EXCEEDED
PERMISSION_BLOCKED
PROCESS_STALLED
```

`agent needs reconnect` 必须进入 `adapterSignals[]`，不能直接成为 node status。

### TerminalControlResult

terminal `dual-control` 的结构化结果。

```text
TerminalControlResult
  present
  valid
  decision
  summary
  parseError
```

它只说明 agent 给出了终端控制信号。它不是成功条件的全部。

### ArtifactContractResult

typed artifact contract 的校验结果。

```text
ArtifactContractResult
  contractType
  required
  present
  valid
  path
  errorCode
  errorMessage
```

例如 `agent_output_contract` 缺失时：

```text
required = true
present = false
valid = false
errorCode = STRUCTURED_ARTIFACT_MISSING
```

### AgentExecutionOutcome

`AgentExecution` 的唯一出口。

```text
AgentExecutionOutcome
  status
  primaryReason
  secondarySignals[]
  nodeMaySucceed
  retrySafe
  recoveryCategory
  operatorMessage
```

建议状态：

```text
SUCCESS
SUCCESS_WITH_WARNINGS
CONTRACT_FAILED
OUTPUT_INCOMPLETE
RECOVERABLE_ADAPTER_FAILURE
NON_RECOVERABLE_ADAPTER_FAILURE
TIMEOUT
INTERRUPTED
```

## Outcome 判定矩阵

| Process / Adapter | Terminal Control | Required Artifact | Outcome | Node 可成功 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 正常退出 | valid | valid | `SUCCESS` | 是 | 标准成功。 |
| reconnect signal，但最终输出完整 | valid | valid | `SUCCESS_WITH_WARNINGS` | 是 | 记录 adapter warning，但不阻塞状态机。 |
| 正常退出 | valid | missing / invalid | `CONTRACT_FAILED` | 否 | agent 输出不满足 contract。 |
| reconnect signal | valid | missing / invalid | `CONTRACT_FAILED` | 否 | primary reason 是 contract；reconnect 作为 secondary signal。 |
| 正常退出 | missing / invalid | 任意 | `OUTPUT_INCOMPLETE` | 否 | 没有可信 terminal control。 |
| auth / permission / context window | 任意 | 任意 | `RECOVERABLE_ADAPTER_FAILURE` 或 `NON_RECOVERABLE_ADAPTER_FAILURE` | 否 | 根据 failure signal 决定 retry / recovery。 |
| timeout / stalled | 任意 | 任意 | `TIMEOUT` | 否 | 进程层未完成。 |
| interrupted | 任意 | 任意 | `INTERRUPTED` | 否 | 外部中断。 |

关键规则：

```text
SUCCESS =
  process usable
  + terminal control valid
  + all required artifacts valid
```

只看到 terminal `dual-control` 不能让 `AgentExecution` 成功。

## Outcome 到 NodeExecution / Recovery 的映射

`AgentExecutionOutcome` 是 `NodeExecution` 的唯一输入。NodeExecution 不再直接读取
stderr、exit code、terminal control 或 artifact JSON。

| Outcome | NodeExecution 状态 | Workflow 停止形态 | RecoveryAssessment | 默认操作 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `SUCCESS` | `COMPLETED` | 继续推进 | 无 | next node | 标准成功。 |
| `SUCCESS_WITH_WARNINGS` | `COMPLETED` | 继续推进 | warning event | next node + trace warning | 例如 reconnect 出现过，但完整 contract 已验证通过。 |
| `CONTRACT_FAILED` | `FAILED` | `WAITING_FOR_RECOVERY` | `artifact_contract_failure` | inspect output / retry after contract fix | required artifact 缺失或非法。retry 不能隐藏 contract failure。 |
| `OUTPUT_INCOMPLETE` | `FAILED` | `WAITING_FOR_RECOVERY` | `output_incomplete` | inspect stdout/stderr / retry | 没有可信 terminal control。 |
| `RECOVERABLE_ADAPTER_FAILURE` | `FAILED` | `WAITING_FOR_RECOVERY` | `recoverable_adapter_failure` | fix external cause / retry | rate limit、session missing、context window 等可恢复失败。 |
| `NON_RECOVERABLE_ADAPTER_FAILURE` | `FAILED` | `FAILED` 或 `BLOCKED` | `non_recoverable_adapter_failure` | human intervention | auth/config/model mismatch 等不能靠 retry 自愈。 |
| `TIMEOUT` | `FAILED` | `WAITING_FOR_RECOVERY` | `timeout_or_stalled` | inspect process / retry | timeout 和 stalled 需要有事件和耗时证据。 |
| `INTERRUPTED` | `FAILED` 或 `CANCELLED` | `WAITING_FOR_RECOVERY` 或 `CANCELLED` | `interrupted` | resume/retry only by explicit user action | 用户中断不能被 autorun 静默恢复。 |

映射规则：

1. `SUCCESS` 和 `SUCCESS_WITH_WARNINGS` 是唯一能让 NodeExecution 完成的 outcome。
2. `CONTRACT_FAILED` 不是 adapter retry 的成功路径；必须保留 contract diagnostic。
3. adapter failure 和 artifact failure 同时存在时：
   - `primaryReason` 使用阻止 contract 成立的直接原因；
   - adapter signal 放入 `secondarySignals[]`；
   - recovery 文案必须同时展示二者。
4. `RecoveryAssessment` 应由 outcome mapper 派生，不允许 handler、CLI、UI 各自拼文案。
5. 历史 ledger 没有 outcome 时可以 legacy fallback，但新 run 必须写 outcome。

## 状态迁移职责

### AgentExecution 负责

- 记录 acpx / agent 调用事实；
- 解析 adapter signal；
- 解析 terminal control；
- 收集 artifact contract validation result；
- 生成唯一 outcome；
- 将 token / tool usage 作为观测事实写入。

### NodeExecution 负责

- 消费 `AgentExecutionOutcome`；
- 映射为节点执行状态；
- 生成 node-level event；
- 打开 recovery / gate / next node。

NodeExecution 不允许直接解释：

- `stderr`；
- `exitCode`；
- `agent needs reconnect`；
- terminal `dual-control`；
- artifact JSON 正文。

### WorkflowInstance 负责

- 根据 NodeExecution 事件推进流程；
- 管理 human gate、loop、repair、completion；
- 不关心 acpx / provider / tool transcript。

## Ledger / JSON 结构

短期不做旧 ledger migration。新运行写新字段，旧字段保留但从 outcome 派生。

`run.json` 建议新增：

```json
{
  "schemaVersion": 3,
  "status": "failed",
  "exitCode": 5,
  "executionOutcome": {
    "status": "CONTRACT_FAILED",
    "primaryReason": "STRUCTURED_ARTIFACT_MISSING",
    "secondarySignals": ["AGENT_RECONNECT_REQUIRED"],
    "nodeMaySucceed": false,
    "retrySafe": false,
    "recoveryCategory": "artifact_contract_failure",
    "operatorMessage": "Required structured artifact missing; adapter also reported reconnect."
  },
  "processResult": {
    "exitCode": 5,
    "usability": "EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT",
    "durationMs": 107427,
    "timedOut": false,
    "interrupted": false
  },
  "adapterSignals": [
    {
      "type": "AGENT_RECONNECT_REQUIRED",
      "severity": "warning",
      "source": "stderr",
      "message": "agent needs reconnect"
    }
  ],
  "terminalControlResult": {
    "present": true,
    "valid": true,
    "decision": "READY_FOR_ARCH_REVIEW"
  },
  "artifactContractResults": [
    {
      "contractType": "agent_output_contract",
      "required": true,
      "present": false,
      "valid": false,
      "errorCode": "STRUCTURED_ARTIFACT_MISSING"
    }
  ]
}
```

旧字段兼容规则：

- `run.status` 由 `executionOutcome.status` 派生；
- `failureReason` 由 `executionOutcome.primaryReason` 派生；
- `recoverable` 由 `executionOutcome.retrySafe` 或 recovery category 派生；
- projection 优先读 `executionOutcome`，旧 ledger 没有时才使用 legacy fallback。

## Recovery 语义

Recovery 不再直接读取 stderr / validator 文本来拼结论。

```text
AgentExecutionOutcome
  -> RecoveryAssessment
  -> Operator next action
```

示例：

```text
outcome.status = CONTRACT_FAILED
primaryReason = STRUCTURED_ARTIFACT_MISSING
secondarySignals = [AGENT_RECONNECT_REQUIRED]
```

用户可见文案应该是：

```text
Agent 输出没有满足结构化 contract；本次运行过程中也出现了 reconnect 信号。
先修复/重试 contract 输出，不把 reconnect 当成唯一根因。
```

这样不会丢失 reconnect，也不会让 reconnect 覆盖 contract failure。

## 对现有系统的影响

### 必须改

| 模块 | 改动 |
| --- | --- |
| `dual-adapter-acpx` | 新增 outcome 模型；transport / health / terminal control / metadata 写入 outcome。 |
| `dual-runtime` | `AcpxAgentTaskHandler` 组合 artifact contract result，NodeExecution 只消费 outcome。 |
| `dual-ledger-file` | `run.json` 写 schema v3 字段；metrics / reports 读取 outcome。 |
| `dual-cli` | trace / recovery show 优先显示 outcome。 |
| `dual-server` | operator overview / diagnostics 优先显示 outcome。 |

### 暂不改

| 模块 | 原因 |
| --- | --- |
| Workflow JSON / LiteFlow | 编排骨架仍然合理。 |
| Workstream / WorkflowInstance 主模型 | 不是本次问题根因。 |
| Memory / EvidenceManifest | 依赖干净 execution outcome，但不需要重写。 |
| Delivery merge | 与 adapter execution outcome 无直接关系。 |
| Operator Console 大布局 | 只改状态来源，不改产品布局。 |

## 实施计划

### Step 1：模型和单元测试

新增：

- `AgentExecutionOutcome`
- `ProcessResult`
- `AdapterSignal`
- `TerminalControlResult`
- `ArtifactContractResult`
- `AgentExecutionOutcomeResolver`

测试矩阵：

1. process ok + terminal valid + artifact valid -> `SUCCESS`
2. process ok + terminal valid + artifact missing -> `CONTRACT_FAILED`
3. reconnect + terminal valid + artifact valid -> `SUCCESS_WITH_WARNINGS`
4. reconnect + terminal valid + artifact missing -> `CONTRACT_FAILED` with secondary reconnect
5. no terminal control -> `OUTPUT_INCOMPLETE`
6. auth / rate limit / context window -> adapter failure
7. timeout / interrupted -> timeout / interrupted

额外必须覆盖：

8. `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` + valid terminal + valid artifact -> `SUCCESS_WITH_WARNINGS`
9. `EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT` + valid terminal + missing artifact -> `CONTRACT_FAILED`
10. `KILLED_AFTER_RECOVERABLE_SIGNAL` -> 不允许成功，必须保留触发 signal
11. 每个 outcome 都有确定的 NodeExecution / RecoveryAssessment 映射
12. `RecoveryAssessment` 从 outcome 派生，不从 stderr 或 markdown 重新推断

### Step 2：adapter 接入

- `AcpxAgentTransport` 只产出 process result / adapter signals / terminal control raw result；
- `AgentRunMetadataWriter` 写 outcome；
- `run.json.status` 从 outcome 派生；
- 不再出现 `exitCode=5` 但 `status=completed` 的组合。

### Step 3：runtime 接入

- artifact validator 结果进入 `ArtifactContractResult`；
- `AcpxAgentTaskHandler` 调用 resolver；
- NodeExecution transition 只消费 outcome。

### Step 4：projection 接入

- trace 显示 outcome；
- recovery show 显示 primary reason + secondary signals；
- operator 显示一致的 stop reason / next action。

### Step 5：真实验证

1. simple real case：确认 reconnect + artifact 缺失不会显示为 completed；
2. simple retry case：确认 retry 后 outcome 正常；
3. cancer-clinic 中等复杂 case：确认状态机不被 reconnect / contract failure 污染。

## 验收标准

必须满足：

1. 没有 `run.status=completed` 且 required artifact missing 的新 run；
2. NodeExecution 不直接读取 stderr / exitCode 决策；
3. reconnect 只作为 adapter signal 进入 outcome；
4. terminal `dual-control` 不单独代表成功；
5. recovery / trace / operator 对同一失败给出同一 primary reason；
6. artifact contract failure 和 adapter reconnect 可以同时保留，不互相覆盖；
7. simple real case 可以复现并验证本次边界；
8. 不引入旧 ledger migration；
9. 不重写 workflow engine。

## 风险

| 风险 | 应对 |
| --- | --- |
| outcome 状态过多，变成新一层复杂 if-else | 用 resolver + 表驱动测试保护，不在 handler 散落判断。 |
| 旧 projection 继续读 legacy 字段 | trace / server / metrics 优先读 outcome，并保留测试。 |
| reconnect 和 contract failure 优先级再次漂移 | 使用 primary reason + secondary signals，而不是覆盖式分类。 |
| 为兼容旧 ledger 增加迁移复杂度 | 当前不做 migration；旧 ledger 只 fallback 读取。 |

## Boundary Review

Decision: `PROCEED_WITH_GUARDS`

Why:

- 现有 workflow / ledger / task queue / memory 骨架合理，不需要推倒重来；
- `AgentExecution` 缺失是状态污染的根因；
- reconnect、terminal control、artifact contract 都必须进入统一 outcome；
- 这是主流程稳定的前置条件。

Boundary:

- Owner: `AgentExecution`
- Source of truth: `AgentExecutionOutcome`
- Projections: trace、recovery show、operator overview、metrics、reports

Required guards:

- outcome resolver 单元测试；
- real simple case regression；
- no completed run with missing required artifact；
- no NodeExecution direct stderr / exitCode decision。

Implementation scope:

- Change: adapter outcome、runtime transition、ledger schema、projection read path；
- Do not change: workflow definition、LiteFlow kernel、memory design、delivery lifecycle、operator layout。

Accepted debt:

- 不迁移旧 ledger；
- 旧字段暂时保留，但必须从 outcome 派生；
- 旧 projection fallback 只服务历史数据，不作为新 source of truth。
