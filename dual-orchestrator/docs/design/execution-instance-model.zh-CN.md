# Definition / Instance / Execution 模型

> 状态：P0 领域模型基线
> 更新时间：2026-06-19

这份文档定义 `dual-orchestrator` 的执行实例模型。它的目的不是引入更复杂的工作流框架，而是把已经反复出问题的状态边界说清楚：什么是流程定义，什么是流程实例，什么是一次具体执行。

这个模型借鉴 Spring Batch 的领域分层：`Job`、`JobInstance`、`JobExecution`、`Step`、`StepExecution` 和 `ExecutionContext`。但 `dual-orchestrator` 不直接采用 Spring Batch，因为本项目的核心不是批处理数据，而是长生命周期的 Agent 工程协作。

## 1. 为什么需要这层模型

过去的实现里，几个概念混在一起：

- workflow graph 既像流程定义，又像产品生命周期；
- `nodes/*.json` 既承载节点实例，又承载进程 PID 和失败细节；
- `runs/` 既像 Agent 运行账本，又被拿来解释节点执行状态；
- retry、resume、repair、iteration 容易互相污染；
- UI / CLI / API 有时从不同位置解释同一个状态。

这导致同类问题反复出现：workflow 已经完成，界面又显示 `RUNNING`；死掉的 Agent 进程不知道是重跑、恢复还是失败；误点按钮后开出新的 iteration；修一个边界 case 时容易再加一段局部 if-else。

正确方向不是继续补丁，而是把模型补完整。

## 2. 核心分层

```text
WorkflowDefinition
  -> WorkflowInstance
       -> WorkflowExecution
            -> NodeInstance
                 -> AgentTask
                 -> NodeExecution
            -> ExecutionContext

Workstream
  -> Goal
  -> Iteration
  -> Artifact
  -> Gate
  -> CompletionContract
```

两条线必须分开：

| 线 | 负责 | 不负责 |
| --- | --- | --- |
| Execution Model | 流程定义、实例、执行、节点执行、进程证据、恢复。 | 目标是否满足、PR 是否可交付。 |
| Workstream Domain | 用户目标、语义 iteration、artifact contract、human gate、completion contract。 | 进程 PID、stdout/stderr、命令行退出码。 |

`WorkflowExecution` 和 `NodeExecution` 是本次模型的关键。不要再引入 `Attempt`、`Run`、`Job` 这类同义词来表达执行尝试。

## 3. 对象定义

| 对象 | 定义 | 主要账本位置 | 不负责 |
| --- | --- | --- | --- |
| `WorkflowDefinition` | 流程模板，说明节点、边、gate、loop、completion path。 | `workflow/definition.json` | 某次执行是否完成。 |
| `WorkflowInstance` | 某个 workstream 上启动的一次 workflow 实例。 | `workflow/instance.json` | 单次命令执行细节。 |
| `WorkflowExecution` | 一次推动 workflow instance 前进的执行会话。 | `executions/workflow/<id>.json` | 业务目标是否满足。 |
| `NodeInstance` | workflow instance 中某个节点的一次逻辑出现。 | `nodes/<node_instance_id>.json` | 完整进程历史。 |
| `AgentTask` | 将某个 node instance 的待执行工作派给具体 agent / tool / session 的本地派工单。 | `queue/agent-tasks/<id>.json` | 节点路由、Agent 内部 ReAct、MCP 调用编排。 |
| `NodeExecution` | 某个 node instance 的一次具体执行。 | `executions/nodes/<id>.json` | Goal 级状态。 |
| `ExecutionContext` | workflow instance 的可恢复上下文。 | `workflow/execution-context.json` | 隐藏业务状态。 |
| `Iteration` | 语义修订或修复轮次。 | `iterations/<iteration_id>.json` | 普通 runtime retry。 |

### WorkflowExecution

`WorkflowExecution` 是一次推进会话，触发来源可以是：

- `dualctl start --run`
- `dualctl run`
- `dualctl resume`
- `dualctl review approve --run`
- scheduler tick
- recovery retry

它记录谁触发、起止时间、最多推进多少步、期间创建了哪些 `NodeExecution`、最终停在什么边界。一个命令结束不等于 workflow 完成；同一个 `WorkflowInstance` 可以有很多 `WorkflowExecution`。

### NodeExecution

`NodeExecution` 记录一次具体节点执行：handler、agent/session/command、PID、stdout/stderr/output refs、exit code、failure classification、recoverable、stale、开始/结束时间和 duration。

对 Agent 调用来说：

```text
NodeExecution
  -> tool_type: acpx
  -> agent_run_ref: runs/<agent_run_id>
```

因此：

```text
runs/ 是工具账本。
executions/nodes/ 是执行模型账本。
```

### AgentTask

`AgentTask` 是 AI 场景在 Spring Batch `StepExecution` 之外额外需要的一层。传统批处理 step 通常由稳定的 Java tasklet 或 reader/writer 执行；而 agent step 还需要冻结以下派工事实：

- 交给哪个 agent：Claude、Codex 或未来的其他工具；
- 使用哪个 session；
- 使用哪个 prompt template；
- 携带哪个 context package；
- 期待哪个 artifact contract；
- 使用哪个 permission profile / tool allowlist；
- 这张派工单当前是 `QUEUED`、`RUNNING`、`SUCCEEDED` 还是 `FAILED`。

它的边界是：

```text
NodeInstance
  -> AgentTask        现在把这个节点交给谁、怎么执行
  -> NodeExecution    这次执行实际发生了什么
```

权限 profile 属于 `AgentTask` 的派工事实，而不是 `WorkflowExecution` 的状态、
也不是 UI 层的显示逻辑。Workflow definition 声明稳定的
`permission_profile`，adapter 负责把它翻译成具体工具的参数，例如 acpx 的
`--allowed-tools` 或 `--approve-all`。这样 intake/design/review 可以安全地观察
仓库，implementation 才能修改代码。

MVP 实现保持一对一：

```text
one NodeInstance
  -> one AgentTask
  -> one NodeExecution
```

当前最小 worker 是本机单任务形态：

```text
dualctl run --dispatch-mode WORKER
  -> enqueue 一个 AgentTask，并让 NodeInstance 保持 RUNNING

dualctl worker run-once
  -> claim 一个 QUEUED AgentTask
  -> 创建一个 NodeExecution
  -> 执行 handler
  -> 把 NodeResult 交还 workflow routing
```

这不是多 worker 平台。后续可以扩展 lease、heartbeat、多 worker、fork/join
分支，或一个 node instance 对多个 agent tasks，但这不是默认语义。默认情况下，
一个 workflow node 仍然只代表一个清晰阶段。如果需要 Claude 和 Codex 多轮协作，
应通过 workflow graph、loop 和 gate 表达，而不是把多个 agent 塞进同一个 node 里。

`AgentTask` 明确不负责：

- 不编排 Agent 内部 ReAct；
- 不调度每一次 MCP / function call；
- 不决定 workflow 下一步；
- 不判断 goal 是否满足；
- 不替代 `NodeExecution` 的运行证据。

因此，`AgentTask` 的价值不是把 dual 做成 Agent runtime，而是让长期任务具备稳定的本地派工边界：可以排队、可观测、可恢复、可重放，并能为未来 fork/join、gist summary、worker 化执行提供落点。

## 4. Spring Batch 借鉴关系

| Spring Batch | dual-orchestrator | 借鉴点 | 不照搬原因 |
| --- | --- | --- | --- |
| `Job` | `WorkflowDefinition` | 流程模板和参数化定义。 | dual 需要 human gate、artifact contract、Agent 角色。 |
| `JobInstance` | `WorkflowInstance` | 同一业务实例绑定一次 definition。 | dual 的业务根是 Workstream，不是 Job。 |
| `JobExecution` | `WorkflowExecution` | 每次启动/恢复都有独立执行记录。 | dual 一次 execution 可能停在人审或恢复。 |
| `Step` | `NodeDefinition` | 节点模板。 | dual 节点包含 Agent、gate、decision、artifact handler。 |
| `StepExecution` | `NodeExecution` | 单节点一次具体执行，含状态和指标。 | dual 还要记录 acpx session、PID、stdout/stderr、contract refs。 |
| `ExecutionContext` | `ExecutionContext` | 可恢复上下文。 | dual 必须可由文件账本复盘。 |

重点不是框架选择，而是语义纪律：

```text
Definition 是“是什么”
Instance 是“这一个”
Execution 是“这一次怎么跑”
```

## 4.1 Spring Batch 不拥有 Goal Loop

Spring Batch 的模型只能启发 execution 分层，不能替代本项目的 goal loop。

```text
WorkflowExecution
  负责一次推进会话怎么跑、怎么停、怎么恢复。

Goal Loop
  负责目标是否满足、是否需要 repair/revision、什么时候停止。
```

因此，不能因为引入 `WorkflowExecution` 就把流程理解成一次 pipeline：

```text
run workflow once -> completed
```

正确语义是：

```text
run one workflow execution
  -> produce artifacts and decisions
  -> CompletionContract evaluates goal satisfaction
  -> SATISFIED / SATISFIED_WITH_NOTES / NEEDS_REPAIR / NEEDS_REDESIGN / BLOCKED / REJECTED
  -> maybe create a new semantic Iteration
```

`WorkflowExecution` 只记录“这一次怎么跑”。`CompletionContract` 和 verifier/human artifacts 才能决定 goal 是否继续循环。LiteFlow 也一样，只能返回 transition candidates，不能拥有 goal satisfaction。

## 5. 状态边界

### WorkflowInstance 状态

| 状态 | 含义 |
| --- | --- |
| `CREATED` | 实例已创建，尚未进入 entry node。 |
| `RUNNING` | 有 active node 正在执行或可以继续推进。 |
| `WAITING_FOR_HUMAN` | 停在人审 gate。 |
| `WAITING_FOR_RECOVERY` | 停在可恢复或待诊断 failure。 |
| `ENDING` | 业务路径完成，正在执行 completion / post-completion。 |
| `ENDED` | workflow instance 已结束，不能再推进。 |
| `FAILED` | 实例失败或被终止。 |

短期可以兼容旧 `WorkflowStatus.COMPLETED`，但新代码必须把“已完成业务路径”和“实例彻底结束”区分开。

### NodeInstance 状态

| 状态 | 含义 |
| --- | --- |
| `READY` | 可执行。 |
| `RUNNING` | 最新 node execution 正在执行。 |
| `COMPLETED` | 节点逻辑完成。 |
| `FAILED` | 节点失败，可能打开 recovery。 |
| `BLOCKED` | 节点被业务或外部条件阻塞。 |
| `SKIPPED` | 节点被明确跳过。 |

### NodeExecution 状态

| 状态 | 含义 |
| --- | --- |
| `STARTING` | 已创建执行记录，尚未拿到 PID 或工具结果。 |
| `RUNNING` | 外部进程或 handler 正在运行。 |
| `SUCCEEDED` | 本次执行成功。 |
| `FAILED` | 本次执行失败。 |
| `STALE` | 记录显示 running，但进程已消失。 |
| `INTERRUPTED` | 被用户或系统中断。 |
| `CANCELLED` | 被明确取消。 |

关键规则：

```text
RUNNING + dead pid 是 NodeExecution.STALE。
对应的 NodeInstance 转为 FAILED，并打开 recovery。
不能静默重新执行同一个 NodeInstance。
```

## 6. Runtime 行为规则

### start

```text
create Workstream
create WorkflowInstance
create initial WorkflowExecution
create entry NodeInstance
create first NodeExecution only when node is actually executed
```

### run / resume

```text
create WorkflowExecution
load WorkflowInstance + ExecutionContext
for each active READY node:
  create NodeExecution
  mark NodeInstance RUNNING
  execute handler
  complete NodeExecution
  update NodeInstance
  route transitions
stop on human gate / recovery / end / max steps
complete WorkflowExecution with stop reason
```

### retry

Runtime recovery retry means:

```text
same Workstream
same WorkflowInstance
same NodeInstance
new WorkflowExecution
new NodeExecution
same Iteration
```

它不能创建新 worktree、新 workstream、新语义 iteration，除非 workflow definition 明确把 retry 建模成新节点。

### revise / repair

Semantic revision means:

```text
same Workstream
same WorkflowInstance
new Iteration
new NodeInstance(s) for the repair path
new WorkflowExecution when execution resumes
new NodeExecution for each concrete run
```

这和 retry 不同，因为 goal interpretation 已经变化。

### terminal boundary

当 `WorkflowInstance` 是 `ENDED`，或 legacy `WorkflowStatus.COMPLETED` 且 completion audit 已完成：

- `run` 必须拒绝；
- `resume` 必须拒绝；
- `review approve --run` 必须拒绝；
- scheduler 必须跳过；
- UI 必须隐藏或禁用 mutating actions；
- trace 必须显示 final evidence 和 safe next action，而不是 runnable next steps。

## 7. Ledger 规划

目标结构：

```text
.agent/workstreams/<id>/
  workflow/
    definition.json
    instance.json
    current.json
    execution-context.json
  queue/
    agent-tasks/
      <agent_task_id>.json
  executions/
    workflow/
      <workflow_execution_id>.json
    nodes/
      <node_execution_id>.json
  nodes/
    <node_instance_id>.json
  gates/
    <gate_id>.json
  iterations/
    <iteration_id>.json
  runs/
    <agent_run_id>/
      run.json
      prompts/
      outputs/
  artifacts/
  reports/
  events.jsonl
  timeline.json
```

兼容规则：

- 现有 `runs/` 不删除，只降级为 tool-specific ledger；
- 现有 `nodes/*.json` 不立刻迁移，只逐步把 PID/进程细节迁到 `NodeExecution`；
- `workflow/current.json` 短期继续存在，但应逐渐只表达 current pointer，不承载全部解释逻辑。

## 7.1 当前 P0 实现状态

P0 是 `Done with known limits`，不是宣称模型已经永远完成。当前实现已经具备主生命周期所需的结构化状态：

- `WorkflowDefinition` 会固化到每个 workstream ledger；
- `WorkflowInstance` 和 `WorkflowCurrent` 承载 durable workflow state；
- `NodeInstance` 承载逻辑节点状态；
- Agent run 目录仍是 tool-specific evidence；
- completion、goal satisfaction、review packet、metrics、timeline 都从 ledger facts 投影。

完全分离的 `WorkflowExecution` / `NodeExecution` 账本仍是后续方向。在它完成之前，新代码不能再引入 attempt/run/job 这类同义词。runtime 事实要么进入现有 node/run metadata，要么进入规划中的 execution documents。

## 8. Projection 规则

UI / CLI / API 只能从以下 read model 解释状态：

| Read model | 来源 |
| --- | --- |
| `ExecutionOverview` | WorkflowInstance + latest WorkflowExecution + active NodeInstance + latest NodeExecution |
| `RecoveryAssessment` | failed NodeInstance + latest failed/stale NodeExecution |
| `ReviewPacket` | Gate + evidence artifacts |
| `CompletionAudit` | completion contract + required artifacts |
| `TimelineProjection` | events + nodes + executions |

禁止：

- UI 直接从 node id 猜 gate 类型；
- CLI 直接从 stderr 文案猜 lifecycle；
- API 自己拼一套状态解释；
- operator panel 为了好看隐藏 terminal boundary。

## 9. 完整实现规划

这不是最小实现路线，而是完整实现蓝图。每一步都要保持系统可运行。

### Phase A：文档和契约收敛

- 将 `Attempt` / `ExecutionAttempt` 全部替换为 `NodeExecution`；
- 明确 `WorkflowExecution` 是一次 run/resume/scheduler 推进会话；
- 更新 workflow state、goal loop、architecture docs；
- 在 engineering checklist 中要求先做 Definition / Instance / Execution ownership check。

验收：

- docs 中没有把 runtime retry 写成 iteration；
- docs 中没有使用 `Attempt` 作为一等模型；
- docs index 指向本模型。

### Phase B：账本结构落地

- 新增 `NodeExecutionDocument`；
- 新增 `WorkflowExecutionDocument`；
- 新增 `executions/nodes/` 和 `executions/workflow/` paths；
- 新增 execution writer/repository；
- NodeInstance 增加 `latestExecutionId` 或等价字段；
- `WorkflowRuntime` 执行节点时创建并完成 `NodeExecution`；
- acpx agent run metadata 通过 `agentRunRef` 关联到 `NodeExecution`。

验收：

- 每个真实执行过的 node 都有 `NodeExecution`；
- stderr/stdout/PID/exit code/stale/recoverable 不再只藏在 node result 或 runs；
- 旧 ledger 仍可读，缺失 execution 时有兼容 projection，但新 run 必须写新结构。

### Phase C：运行边界迁移

- `RUNNING` guard 读取 latest `NodeExecution`；
- dead PID 标记 `NodeExecution.STALE`；
- `NodeInstance` 转为 `FAILED` 并打开 recovery；
- retry 创建新的 `NodeExecution`，不创建新 `Iteration`；
- terminal workflow 拒绝新 `WorkflowExecution` 或立即以 terminal refusal 结束。

验收：

- stale running regression 测试基于 `NodeExecution`；
- retry 后同一 node instance 下有多个 node executions；
- revise 后进入新 iteration；
- completed/ended 后 CLI/API/UI/scheduler 都不能推进。

### Phase C2：Goal Loop 评价边界

目标：防止实现只完成 execution 分层，却把主流程做成一次性 pipeline。

- 明确 `CompletionContract` 输入：verifier artifact、human gate decision、self-test evidence、required artifact status；
- 新增或强化 goal satisfaction read model；
- 将 `SATISFIED`、`SATISFIED_WITH_NOTES`、`NEEDS_REPAIR`、`NEEDS_REDESIGN`、`BLOCKED`、`REJECTED` 映射为明确的 domain outcome；
- `NEEDS_REPAIR` / `NEEDS_REDESIGN` 必须创建带 reason 和 evidence refs 的新 `Iteration`；
- runtime retry 只能新增 `NodeExecution`，不能伪装成 goal loop。

验收：

- acceptance reject 不会被当成 runtime failure；
- runtime reconnect 不会创建 semantic iteration；
- goal loop 的每次回跳都有 evidence 和 reason；
- trace / API / Operator Panel 能说明“这是 runtime retry，还是 goal repair loop”。

### Phase D：投影统一

- 新增或强化 `ExecutionOverview`；
- overview 聚合 WorkflowInstance、WorkflowExecution、NodeInstance、NodeExecution、CompletionAudit；
- `dualctl trace`、API overview、Operator Panel 读取同一 read model；
- timeline 显示 node instance 和 node execution 的区别。

验收：

- 同一个 workstream，CLI 和 UI 对“为什么停住/下一步做什么”的表述一致；
- historical gate 只读，current gate 才显示动作；
- running/stale/recovery/completed 状态在三端一致。

### Phase E：Completion / End 分层

- 当 routing 到达 workflow completion 时，持久化
  `domain/completion-audit.json`；
- 短期继续兼容 `WorkflowStatus.COMPLETED`，但通过 completion audit read
  model 暴露 `lifecycleState=ENDED`；
- post-completion summary / metrics / delivery evidence 有明确 owner；
- trace、summary、metrics、delivery inspection 这类结束后观测动作只能读
  ledger，不能再创建新的 workflow node。

验收：

- workflow completed 后可以执行只读观测动作，但不能再推进主线节点；
- completion audit 可解释 completed / completed_with_notes / incomplete；
- UI 不再把 ended workstream 显示为 running。

### Phase F：Loop / Fork / Join 扩展

- 给 workflow edge 增加 `TransitionMetadata`，让 loop/fork/join 语义属于
  `WorkflowDefinition`，而不是散落在 node id 命名约定里；
- loop transition 标准化：reason、trigger、source node/artifact、new
  semantic iteration evidence；
- fork branch 增加 group id、requiredness、purpose；successor node 和
  `node_routed` event 都携带 transition metadata；successor node 使用
  `NodeInstance.inputTransition` 存输入 transition evidence，不能塞进 handler
  `result`；
- join node 增加 strategy，第一版支持 `all_terminal`：同一个 branch group
  中仍有 active node 时延迟激活 join node；
- LiteFlow adapter 继续只返回 transition candidates，不拥有 domain lifecycle。

验收：

- reject -> repair -> re-accept 有标准 transition evidence；
- 多 review 分支可以汇合成 acceptance package；
- join deferred / activated evidence 进入 ledger event；后续 artifact contract
  可以引用这份 join evidence。

## 10. 实现时的防漂移机制

每个涉及 workflow/state/recovery/gate/projection 的变更，必须回答：

```text
Definition:
Instance:
Execution:
Iteration:
Artifact:
Gate:
Goal / Workstream:

本次变化属于哪一层？
是否引入新概念？
如果是，为什么现有对象不能承载？
UI/CLI/API 是否都从同一 read model 读取？
是否有 terminal/recovery/loop 边界测试？
```

如果回答不清楚，不应该开始改代码。

## 11. 当前结论

`dual-orchestrator` 不需要因为状态复杂就马上引入重型 workflow 框架。LiteFlow 可以继续作为 routing kernel，但真正缺失的是 execution instance 模型。

下一步应该先实现 `WorkflowExecution` / `NodeExecution` 账本和 projection 边界。这样后续无论继续使用 LiteFlow、自研 kernel，还是未来引入 task queue / fork-join / compensation，都不会再把“流程定义、流程实例、具体执行、业务 iteration”混成一团。
