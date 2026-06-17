# Workflow Execution Model：从顺序流到轻量编排内核

> 状态：LiteFlow candidate kernel 已完成 prototype 与场景验证
> 更新时间：状态机边界复盘 + LiteFlow 场景验证之后

这份文档说明 `dual-orchestrator` 的 workflow execution model 应该如何演进。

它和 `workstream-domain-model.md` 的关系是：

- `Workstream Domain Model` 定义一个 workstream 在业务上什么时候算完整、可交付、可恢复。
- `Workflow Execution Model` 定义流程实例如何启动、执行、暂停、循环、分叉、汇合、结束。

换句话说：

```text
Workstream Domain Model = 生命周期事实
Workflow Execution Model = 执行机制
```

不能再把 workflow graph 当成产品流程本身。Graph 只能描述“下一步可以去哪”，不能单独回答“这个长期任务是否完整完成”。

这次设计判断不写单独 ADR，避免流程过重。本文档直接作为当前设计基线。

## 1. 当前问题

当前实现已经能跑通主线，但它仍然更像一个顺序 workflow runner：

```text
A -> B -> C -> Gate -> D -> E -> Completed
```

真实长期任务需要的是：

```text
Start
  -> 多阶段执行
  -> 人审 / 修订 / 恢复
  -> 可循环
  -> 可分支 / 汇合
  -> completion audit
  -> post-completion actions
  -> End
```

当前暴露的问题包括：

1. `COMPLETED` 同时表达“流程没有下一步”和“业务完成”，语义过重。
2. final gate 曾经能误入下一轮，说明结束边界没有被流程模型守住。
3. revision / repair loop 依赖具体 edge 拼接，没有成为一等能力。
4. `runs/` 更像 Agent 运行记录，而不是通用 `NodeExecution`。
5. workflow instance、workflow execution、node instance、node execution、iteration 的边界不够清楚。
6. fork/join 还没有明确模型，导致未来多视角 review / 并行验证会继续被顺序流绑住。
7. post-completion audit / metrics / cleanup / benchmark grading 这类动作没有合适的生命周期位置。

## 2. 目标模型

目标不是一次性实现复杂 BPMN，也不是引入重量级流程引擎。

目标是建立一套足够小、但语义完整的本地 workflow execution model，并把“节点流转 / 路由表达”从 Workstream 领域模型里剥离出来：

```text
WorkflowDefinition
  -> WorkflowInstance
    -> WorkflowExecution
      -> FlowNodeInstance
        -> NodeExecution
    -> Iteration
    -> Transition
    -> CompletionAudit
```

推荐架构：

```text
dual Workstream Domain
  owns lifecycle / ledger / human gate / completion contract
        |
        v
FlowOrchestrationKernel
  owns execution DSL / routing / step orchestration
        |
        v
Current built-in runtime or LiteFlow adapter
```

也就是说，可以考虑 LiteFlow 这类轻量编排引擎来承担 execution DSL 和 routing engine，但不能让它成为 Workstream 生命周期事实源。

核心设计原则：

- Start / End 是显式节点或显式生命周期边界。
- `COMPLETED` 不再等同于 `ENDED`。
- 节点实例和执行尝试分开。
- loop 是正式 transition，不是散落在业务 handler 里的特殊分支。
- fork/join 先支持模型，执行可以先保持顺序。
- completion audit 是进入 end 前的显式关卡。
- post-completion actions 有独立阶段，不污染业务完成语义。
- 外部编排引擎只负责执行编排，不负责解释领域完成态。

## 2.1 为什么不是继续把 RoutingEngine 长大

当前问题的根部不是某个 edge 判断写错，而是我们把太多职责挤进了自研顺序 runner：

```text
节点选择
状态迁移
人工 gate
retry / recovery
loop / revision
completion 判断
UI/CLI 展示解释
ledger 写入
```

这些职责混在一起后，后续很容易继续出现：

- final gate 之后还能进入下一轮；
- UI 显示 running，但 ledger 已经完成；
- retry 和业务 revision 混用；
- 为了修一个 case 增加一个局部 if-else；
- workflow graph 被误当成产品生命周期。

因此正确方向不是继续扩展一个越来越复杂的 `RoutingEngine`，而是拆成两层：

| 层 | 负责 | 不负责 |
|---|---|---|
| `FlowOrchestrationKernel` | 节点编排、条件路由、loop/fork/join 表达、step 执行顺序。 | Workstream 是否完成、gate 是否可批准、artifact 是否满足契约。 |
| `Workstream Domain` | 生命周期、账本、human gate、completion contract、artifact ownership、projection。 | 具体 DSL 怎么表达 THEN/IF/WHEN/FOR。 |

## 2.2 LiteFlow 的候选定位

LiteFlow 适合被评估为轻量 execution DSL / routing engine，原因是它比 BPMN 引擎轻，也更接近当前问题：本地节点编排、条件路由、串行/并行表达、重试、步骤信息。

根据 prototype 验证，LiteFlow 在非 Spring 场景可以作为独立模块引入，chain 可以用 XML 描述，组件可以通过 context bean 拿到 dual 的 `WorkflowDefinition` / snapshot / signal，并把执行步骤回传给 dual。

但它只能放在这个位置：

```text
WorkflowRuntime
  -> FlowOrchestrationKernel
       -> LiteFlow adapter or built-in adapter
  -> WorkflowFileLedger
  -> WorkstreamDomainService
```

LiteFlow 可以做：

- 表达 THEN / IF / SWITCH / WHEN / FOR 等执行结构；
- 选择下一批要执行的 component；
- 提供 step 执行结果和错误；
- 帮助未来支持 loop、fork、join；
- 降低自研 routing if-else 的增长速度。
- 承载可替换的 execution DSL，使 workflow definition 到执行链的转换可以独立测试。

LiteFlow 不应该做：

- 判断 Workstream 是否可以交付；
- 直接修改 ledger；
- 直接决定 human gate 是否通过；
- 直接解析 Agent artifact 的业务含义；
- 成为 UI/CLI 的状态源；
- 隐式吞掉 completion audit / recovery / revision 的领域语义。

这个边界很重要：如果把 Workstream lifecycle 交给 LiteFlow，问题只是从自研 runner 迁移到 DSL 里，复杂度没有消失。

prototype 第一版采用 `prepare -> route -> emit` 小链路：

```text
LiteFlow chain
  prepare: 校验并记录 step
  route: 调用 dual 的 FlowOrchestrationKernel 计算 transition candidate
  emit: 回传 transition candidate 和 executed steps
```

这个结果说明：

- LiteFlow 和 ledger 可以解耦：adapter 只返回 `FlowAdvanceResult`，不直接写文件。
- LiteFlow 适合作为执行链外壳和步骤观测来源。
- 第一阶段不应让 LiteFlow 直接替代 completion / gate / artifact contract。
- 如果后续要让 LiteFlow 承担更多 routing，需要再做 `WorkflowDefinition -> LiteFlow EL` 的编译 spike。

后续 spike 已验证 `WorkflowDefinition -> LiteFlow EL` 的编译能力。这里的“完整编译”不是把整个长期 workflow 编译成一条一次性执行到底的大 chain，而是按路由点编译：

```text
WorkflowDefinition.edges
  group by from + outcome
    -> LiteFlow route chain
       THEN(prepare, edge_0001, edge_0002, emit)
```

原因是 dual 的长期任务每个节点之间都需要：

- 写 ledger；
- 生成或读取 artifact；
- 允许 human gate 停住；
- 支持 recovery / retry；
- 允许进程退出后恢复。

如果把整个 workflow 编译成一条 LiteFlow chain，反而会绕开这些领域边界。因此当前正确形态是：

```text
Node handler completed
  -> dual asks LiteFlow route chain for transition candidates
  -> dual ledger creates next node / gate / iteration
  -> runtime stops or continues
```

这个模型已经用中等复杂度的私有生产型 real-case 验证过：

- kernel：`DUAL_FLOW_KERNEL=liteflow`
- workflow variant：`full`
- timeline nodes：31
- agent runs：10
- human gates：6
- final status：`COMPLETED`
- final acceptance：`ACCEPT_WITH_NOTES`
- key artifacts：final report、handoff.json、next-stage prompt、delivery body、workstream summary 均存在
- original repo clean：true

这说明 LiteFlow route-chain adapter 可以和 ledger 结合跑完整生命周期；但它仍只是 routing kernel，不是 Workstream lifecycle owner。

补充的 controlled lifecycle smoke 也已通过：

- kernel：`DUAL_FLOW_KERNEL=liteflow`
- smoke case：`lifecycle`
- workflow：`agent-lifecycle-full@4`
- final status：`COMPLETED`
- nodes：28
- human gates：5
- events：29
- agent runs：11
- 验证点：ledger 结构、events、node instances、agent run metadata、final report、git evidence、handoff.json、next-stage prompt 全部存在

这条 controlled smoke 的价值是：它不依赖真实 LLM 的随机输出，能稳定验证 `WorkflowDefinition -> LiteFlow route chain -> ledger transition -> gate pause/resume -> completion` 的主路径。

进一步场景验证结果：

| 场景 | 类型 | 结果 | 说明 |
|---|---|---|---|
| `lifecycle` | controlled smoke | PASS | 28 nodes、5 gates、29 events，完整生成 final report、handoff、next-stage prompt。 |
| completed boundary | controlled CLI | PASS | 已完成 workstream 上再次执行 `run` / `resume` 不创建新节点，`review approve --run` 被拒绝为无 active gate。 |
| `contract-happy-path` | real acpx smoke | PASS | `agent-lifecycle-contract@2` 完成，30 nodes、6 gates、32 events、0 warnings，TaskContract 生成并通过人工 gate。 |
| `full-reject-classify-path` | real acpx smoke | PARTIAL / EXPECTED RECOVERY | 真实 Codex 两次返回 `NEEDS_REVISION`，达到 revision limit 后进入 `WAITING_FOR_RECOVERY`。这验证了 revision/recovery 边界能停住，但没有完成 full reject/classify happy path。 |
| `reject-repair-reaccept` | controlled smoke | PASS | `agent-lifecycle-full@4` 完成，先由 acceptance 输出 `REJECT`，再进入 failure classification，创建 repair iteration，第二次 acceptance 输出 `ACCEPT` 后完成。38 nodes、6 gates、42 events、14 agent runs、0 warnings。 |
| private medium case | real acpx + real repo | PASS-WARN | `agent-lifecycle-full@4` 完成，31 timeline nodes、6 gates、10 agent runs，最终 `ACCEPT_WITH_NOTES`，verification confidence `MEDIUM`，原仓库干净。 |

这些场景说明 LiteFlow route-chain adapter 已经能承接主干、contract variant、真实 worktree、terminal boundary、revision-limit recovery 边界，以及“REJECT -> classify -> repair loop -> re-accept”的闭环。真实 `full-reject-classify-path` 仍会受 LLM 输出影响，但这个不再是 LiteFlow 提升的阻塞项：不可控模型输出用真实 case 暴露，关键路由闭环用 controlled scenario 固化。

因此当前判断是：

```text
LiteFlow 已具备提升为默认 routing kernel 的验证基础。
提升时必须保留 built-in kernel 作为 fallback。
```

原因：

- 正向证据已经足够：边界单测、controlled lifecycle smoke、contract real smoke、中等复杂度 real-case 都证明 LiteFlow adapter 能承接主线 routing。
- 关键负向路径已有可控验证：acceptance reject 后能分类、进入修复 iteration，并在再次验收通过后完成。
- 领域边界仍需保持：ledger、human gate、artifact contract、completion contract 必须继续由 dual 拥有。
- 风险仍然存在：当前 adapter 每次 route 都动态加载 LiteFlow chain，尚未做长期运行性能与资源复用评估；所以提升默认不等于删除 built-in。

当前发布策略：

1. LiteFlow 作为默认 routing kernel。
2. 保留 `DUAL_FLOW_KERNEL=builtin` 或等价配置作为 fallback。
3. 保留 `DUAL_FLOW_KERNEL=liteflow` / `-Ddual.flow.kernel=liteflow` 的显式启用方式，方便排查和对比默认行为。
4. 不把 Workstream lifecycle、ledger、human gate、artifact contract、completion contract 下放给 LiteFlow。
5. 提升默认后持续保留最小回归：controlled lifecycle、controlled reject-repair-reaccept、real acpx contract smoke、terminal completed boundary。

默认内置 workflow 的定义来源是 classpath JSON 资源，由 `WorkflowDefinitionLoader` 统一加载：`agent-lifecycle.full.json` 对应默认 full workflow，`agent-lifecycle.contract.json` 对应 contract workflow。Java 不再手写 lifecycle DAG，这样可以避免“LiteFlow 是执行内核，但默认流程仍藏在 Java factory 里”的双重心智模型。

## 2.3 编排内核边界

建议新增一个内部端口，而不是让业务代码直接依赖 LiteFlow：

```java
interface FlowOrchestrationKernel {
    FlowPlan compile(WorkflowDefinition definition);
    FlowAdvanceResult advance(FlowExecutionSnapshot snapshot, FlowSignal signal);
    FlowValidationResult validate(WorkflowDefinition definition);
}
```

其中：

- `FlowPlan` 是编排图或 DSL 编译结果。
- `FlowExecutionSnapshot` 是 dual 从 ledger 投影出来的当前执行状态。
- `FlowSignal` 是 approve、revise、retry、complete、agent result 等外部输入。
- `FlowAdvanceResult` 只描述“应该执行哪些节点 / 产生哪些 transition 候选”。
- `WorkflowRuntime` 仍负责写 ledger、创建 gate、生成 `NodeExecution`、校验 completion contract。

短期可以保留当前 runtime 作为 `BuiltInFlowKernel`。LiteFlow 作为第二个 adapter 做 spike，不要一上来重写主线。

## 3. 核心对象

| 对象 | 含义 | 当前状态 | 目标 |
|---|---|---|---|
| `WorkflowDefinition` | 流程模板，包含节点和 transition 定义。 | 已有。 | 加强 start/end、loop、fork/join 语义。 |
| `WorkflowInstance` | 某个 workstream 启动一次 workflow 后形成的实例。 | 有 `workflow/instance.json`，语义偏弱。 | 成为流程执行的实例边界。 |
| `FlowNodeDefinition` | 流程节点定义。 | 当前是 `NodeDefinition`。 | 增加 node type / control type 区分。 |
| `FlowNodeInstance` | 某个 workflow instance 中的节点实例。 | 当前是 `nodes/*.json`。 | 保持稳定，不因 retry 重建。 |
| `WorkflowExecution` | 一次 `run` / `resume` / scheduler tick 推进会话。 | 当前主要隐含在命令和事件中。 | 独立记录一次推进会话的边界、触发者、stop reason。 |
| `NodeExecution` | 某个节点实例的一次具体执行。 | 当前 `runs/` 主要是 Agent run，`nodes/*.json` 混有进程细节。 | 独立记录 PID、stdout/stderr、exit code、stale/recovery evidence；Agent run 是其中一种工具细节。 |
| `AgentTask` | 把某个节点实例派给具体 agent / tool / session 的本地派工单。 | 已落地为 `queue/agent-tasks/*.json`。 | 后续可接本地 queue / worker，但不负责 Agent 内部 ReAct 或 MCP/function call 调度。 |
| `Transition` | 从一个节点到另一个节点的可追溯跳转。 | `EdgeDefinition` + `TransitionMetadata` + `node_routed` event。 | 后续可继续加强 task queue / join artifact。 |
| `Iteration` | 业务修订/修复轮次。 | 已有 `iterations/*.json`。 | 只表示语义 revision/repair，不表示技术 retry。 |
| `CompletionAudit` | 判断是否满足 Workstream Completion Contract。 | `domain/completion-audit.json`。 | 后续可扩展为更完整的 artifact registry。 |

## 4. Start / End 语义

### 4.1 为什么需要 Start

现在 workflow 初始化后直接出现第一个 active node。技术上可用，但缺少显式起点语义。

显式 Start 的价值：

- 记录 workflow instance 是如何被启动的。
- 记录 task source：inline task、task file、next-stage prompt、resume handoff。
- 记录 initial context、workflow variant、base repo、worktree。
- 未来支持从不同入口进入不同流程。

推荐模型：

```text
START
  -> intake.claude_brainstorm
```

Start 可以先不是可执行 node，而是 workflow instance 的标准事件：

```json
{
  "event_type": "workflow_started",
  "start_node": "START",
  "entry_node": "intake.claude_brainstorm",
  "task_source": "task_file",
  "workflow_variant": "full"
}
```

### 4.2 为什么需要 End

`COMPLETED` 表示当前 workflow 执行完了，但不应该承载所有结束后动作。

未来可能有：

- completion audit
- workstream summary generation
- metrics aggregation
- benchmark grading
- cleanup suggestion
- notification
- archive / retention policy

这些动作发生在业务完成之后，但流程完全结束之前。

推荐模型：

```text
business lifecycle complete
  -> completion.audit
  -> post_completion.summary
  -> post_completion.metrics
  -> END
```

因此要区分：

| 概念 | 含义 |
|---|---|
| `domain completed` | Workstream 满足业务完成契约。 |
| `workflow completed` | 工作流主业务路径完成。 |
| `workflow ended` | post-completion actions 也完成，不再有下一步。 |

## 5. Workflow Status 重新分层

当前 `WorkflowStatus.COMPLETED` 过重。建议后续分层：

### 5.1 Execution Status

用于 runtime：

| Status | 含义 |
|---|---|
| `CREATED` | workflow instance 已创建，尚未启动。 |
| `STARTED` | start event 已写入，准备进入 entry node。 |
| `RUNNING` | 有可执行 active node。 |
| `WAITING_FOR_HUMAN` | 等待人工 gate。 |
| `WAITING_FOR_RECOVERY` | 等待恢复。 |
| `ENDING` | 主业务路径完成，正在执行 completion / post-completion actions。 |
| `ENDED` | 流程实例彻底结束。 |
| `FAILED` | 流程实例失败或被中止。 |

### 5.2 Domain Status

用于 Workstream：

| Status | 含义 |
|---|---|
| `in_progress` | 生命周期仍在推进。 |
| `waiting_for_human` | 需要人类领域决策。 |
| `waiting_for_recovery` | 需要恢复动作。 |
| `completed` | 满足完成契约。 |
| `completed_with_notes` | 可完成，但有非阻塞注意事项。 |
| `incomplete` | 流程到达终点，但完成契约不满足。 |
| `failed` | 生命周期失败。 |
| `aborted` | 人工终止。 |

短期实现可以不立刻改 enum，但设计和新代码必须使用这两个概念，避免继续把 `COMPLETED` 当成万能状态。

## 6. Definition / Instance / Execution

当前容易混淆：

```text
workstream
workflow instance
node instance
run folder
agent run
retry
iteration
```

推荐边界：

```text
Workstream
  一个长期业务任务。

WorkflowInstance
  Workstream 上某一次 workflow definition 的执行实例。

WorkflowExecution
  一次 run/resume/scheduler tick 推进会话。

FlowNodeInstance
  WorkflowInstance 中某个节点的一次逻辑出现。

NodeExecution
  FlowNodeInstance 的一次具体执行。

AgentRun
  NodeExecution 的一种工具细节，专门对应 acpx/Claude/Codex 调用。

Iteration
  业务修订或修复轮次，不等于 runtime retry。
```

### 6.1 为什么需要 NodeExecution

失败后不应该总是新建 worktree 或新建节点。很多情况只是同一节点再次执行：

- acpx rate limit
- Claude session reconnect
- context window error
- stdout contract 格式错误
- transient process failure

这应该表达为：

```text
same Workstream
same WorkflowInstance
same FlowNodeInstance
new WorkflowExecution
new NodeExecution
```

只有当业务语义进入修订/修复，才创建：

```text
new Iteration
```

只有当人明确开始另一个任务或下阶段，才创建：

```text
new Workstream or new WorkflowInstance
```

### 6.2 建议账本结构

长期目标：

```text
.agent/workstreams/<id>/
  workflow/
    instance.json
    current.json
  nodes/
    <node_instance_id>.json
  executions/
    workflow/
      <workflow_execution_id>.json
    nodes/
      <node_execution_id>.json
  queue/
    agent-tasks/
      <agent_task_id>.json
  runs/
    <agent_run_id>/
      run.json
      prompts/
      outputs/
  iterations/
    <iteration_id>.json
```

`runs/` 保留 Agent 细节；`executions/` 统一记录所有类型节点的执行尝试。

## 7. Loop 是一等 transition

当前已有一些 revision edge，但语义不够统一。

Loop 应该记录：

```json
{
  "transition_type": "loop",
  "from_node": "acceptance.decision",
  "to_node": "context.implement_claude",
  "trigger": "REJECT_IMPLEMENTATION_BUG",
  "reason": "Acceptance found implementation gap",
  "iteration_id": "iter-004",
  "parent_iteration_id": "iter-003",
  "carried_artifacts": ["..."],
  "feedback_ref": "..."
}
```

Loop 的关键不是“跳到前面的节点”，而是：

- 为什么跳回去；
- 是谁要求的；
- 带着什么证据回去；
- 是否开启新 iteration；
- 是否达到 revision limit；
- 修复完成后如何重新进入主线。

### 7.1 Loop 类型

| 类型 | 触发 | 是否新 iteration |
|---|---|---|
| human revise loop | 人审请求修改 | 是 |
| codex revision loop | Codex 判断需要 revision | 是 |
| acceptance repair loop | 最终验收 reject 后分类修复 | 是 |
| recovery retry loop | 工具失败后重试同节点 | 否 |
| context repair loop | 上下文不足导致补上下文 | 视语义而定 |

### 7.2 设计约束

- Loop 必须有 reason。
- Loop 必须记录 trigger action / decision。
- Loop 不能偷偷跨过 human gate。
- Loop 不能用 retry 伪装业务修订。
- Retry 不能新建业务 iteration。

## 8. Fork / Join

不支持 fork/join 会限制未来的主功能。

典型场景：

```text
实现完成后并行：
  -> self-test evidence
  -> static analysis
  -> docs check
  -> security review

join:
  -> acceptance package
```

或者：

```text
Codex 多视角 review：
  -> product review
  -> architecture review
  -> user acceptance review

join:
  -> aggregate review decision
```

## 8.1 设计目标

短期不需要真正并发执行，但模型要支持：

- 多个 active node；
- join node 等待多个 predecessor；
- join strategy；
- 部分失败如何处理；
- 汇合后的 evidence package。

## 8.2 Fork 模型

Fork 可以通过多条同时匹配的 outgoing transition 表达：

```text
implement.claude_implement
  -> implement.self_test
  -> implement.static_analysis
  -> implement.docs_check
```

当前 `RoutingEngine` 已经有 fan-out 雏形：多个 edge 匹配时可激活多个 node。

但这还不是完整 fork，因为缺少：

- fork group id；
- branch purpose；
- branch requiredness；
- join strategy。

## 8.3 Join 模型

Join node 应有明确策略：

| Join strategy | 含义 |
|---|---|
| `all_success` | 所有 required branch 成功才继续。 |
| `all_terminal` | 所有 branch 到达 terminal 状态后继续，由 join handler 判断结果。 |
| `quorum` | 达到指定数量即可继续。 |
| `best_effort` | 非阻塞 branch 失败也可继续，但写入风险。 |

MVP 建议只支持：

```text
all_terminal + required branch status check
```

执行仍可顺序进行，不需要一开始做真正并发。

## 9. Post-completion Actions

完成后动作不能混在业务主流程里，也不能让 UI/CLI 临时补。

建议增加 post-completion phase：

```text
acceptance.generate_final_report
  -> delivery.prepare_pr
  -> gate.human_acceptance
  -> stage.claude_handoff
  -> stage.generate_next_prompt
  -> gate.human_next_stage_approval
  -> completion.audit
  -> post_completion.workstream_summary
  -> post_completion.metrics_snapshot
  -> END
```

其中：

- `completion.audit` 判断 Workstream Completion Contract。
- `post_completion.workstream_summary` 生成 deterministic summary。
- `post_completion.metrics_snapshot` 固化 metrics。
- `END` 表示流程实例彻底结束。

短期可先不引入所有节点，但必须让 summary / audit 有明确 owner。

## 10. 状态文件建议

### 10.1 workflow/current.json

继续服务 runtime：

```json
{
  "workflowInstanceId": "wf-...",
  "executionStatus": "RUNNING",
  "activeNodeInstanceIds": [],
  "openGateIds": [],
  "updatedAt": "..."
}
```

短期保持旧字段 `status`，但新读模型应区分 `executionStatus` 和 domain status。

### 10.2 domain/completion.json

新增：

```text
.agent/workstreams/<id>/domain/completion-audit.json
```

字段：

```json
{
  "schema_version": 1,
  "workstream_id": "ws-...",
  "domain_status": "completed_with_notes",
  "execution_status": "ENDED",
  "required_artifacts": [],
  "missing_artifacts": [],
  "blocking_gaps": [],
  "non_blocking_notes": [],
  "safe_next_action": "review_final_report",
  "generated_at": "..."
}
```

## 11. 如何防止实现漂移

设计本身不能防止漂移，必须变成 executable guardrails。

### 11.1 Workflow Definition 检查

每次加载 workflow definition 时检查：

- 必须有明确 entry。
- 必须有可到达 end 或 completion path。
- final gate 不允许 `approve` 回到 intake。
- loop transition 必须有 loop reason source。
- fork branch 必须能被 join 或显式 terminal。

### 11.2 Completion Invariant 测试

每个 full lifecycle fixture 必须验证：

- workflow 到达终点后无 active nodes。
- 无 open gates。
- completion audit 存在。
- workstream summary 存在。
- required artifacts 全部 present 或有 explicit skip reason。
- trace / API overview / operator panel 读取同一 completion status。

### 11.3 Transition Event 检查

每次 routing 产生 transition event：

- transition type；
- from / to；
- trigger outcome；
- iteration；
- reason；
- carried artifact refs。

不允许只靠 node status 推断历史。

### 11.4 UI / CLI 禁止解释原始状态

UI / CLI 可以显示 raw current，但主文案必须来自统一 read model：

```text
ExecutionOverview
CompletionAudit
RecoveryAssessment
ReviewPacket
```

否则同一个状态会被不同入口解释成不同含义。

### 11.5 文档同步检查

任何变更如果触及以下内容，必须同步更新设计文档和测试：

- lifecycle phase；
- workflow status；
- final gate；
- loop / retry / repair；
- completion artifact；
- trace / overview 主文案；
- run / workflow execution / node execution 账本结构。

### 11.6 编排内核适配器检查

如果引入 LiteFlow 或其他轻量编排内核，必须增加 adapter contract 测试：

- adapter 不允许直接写 ledger；
- adapter 不允许直接创建或批准 human gate；
- adapter 不允许直接把 workflow 标记为 completed / ended；
- adapter 输出必须是 `FlowAdvanceResult`，由 `WorkflowRuntime` 决定如何持久化；
- 同一个 workflow definition 在 `BuiltInFlowKernel` 和 LiteFlow spike 下，对关键路径应产生等价 transition 候选；
- final gate / completion / recovery / revise loop 必须继续由 dual 的 domain service 校验。

这条规则用于防止“换了引擎之后，领域边界又漂移到 DSL 里”。

## 12. 实现顺序建议

不要一次性重写 runtime，也不要为了引入 LiteFlow 打断主线闭环。

### Step 1：补 Completion Audit 和 Summary Owner

先解决当前真实缺口：

- required artifact registry；
- deterministic workstream summary writer；
- completion audit projection；
- trace / API / UI 复用 audit。

这一步不改大状态机，但先把完成态契约守住。

### Step 2：抽出 FlowOrchestrationKernel 端口

在当前 runtime 外面抽一个小端口：

```text
FlowOrchestrationKernel
  compile(definition)
  validate(definition)
  advance(snapshot, signal)
```

第一版 adapter 仍然调用当前内置 routing 逻辑，行为不变。

目标不是马上替换实现，而是先把边界立起来：

- workflow definition 如何被编译；
- 当前 ledger snapshot 如何投影成 execution snapshot；
- approve / revise / retry / agent result 如何成为 signal；
- 下一步 transition 候选如何返回给 runtime；
- runtime 如何继续拥有 ledger 写入和 domain 校验。

### Step 3：LiteFlow spike，只覆盖小闭环

做一个受控 spike，而不是直接迁移全流程。

建议覆盖：

```text
START -> intake -> contract_review_gate -> design -> design_gate
```

验收标准：

- 能表达顺序节点；
- 能表达 human gate 停止；
- 能表达 approve 后继续；
- 能表达 revise loop 的候选跳转；
- 不直接写 ledger；
- 和当前 built-in kernel 产生一致的关键 transition；
- 失败时可以完全回退到 built-in kernel。

如果 spike 不能保持边界清晰，就不引入 LiteFlow；继续强化自研内核。

当前 prototype 结论：

- `dual-flow-liteflow` 独立模块可以编译并运行。
- LiteFlow 2.16.0 非 Spring 模式可用。
- context bean 可承载 dual snapshot / signal。
- adapter 可以返回 transition candidate，不写 ledger。
- `WorkflowDefinition -> LiteFlow EL` 已验证为“route-chain 编译”模式，而不是“全生命周期单 chain”模式。
- CLI / server 默认使用 LiteFlow kernel；可通过 `DUAL_FLOW_KERNEL=builtin` 或 `-Ddual.flow.kernel=builtin` 回退到内置 kernel。
- 中等复杂度 real-case 已验证 LiteFlow kernel 可以跑到 `COMPLETED`。
- controlled reject / classify / repair / re-accept 场景已验证 LiteFlow kernel 能承接关键负向分支。
- 当前建议：LiteFlow 作为默认 routing kernel；built-in kernel 保留为诊断和紧急 fallback。这个选择不改变核心模型抽象，LiteFlow 仍只返回 transition candidate，不拥有 Workstream lifecycle、ledger、human gate、artifact contract 或 completion contract。

### Step 4：引入 WorkflowExecution / NodeExecution 读模型

先不移动 `runs/`，新增 execution projection：

```text
executions/workflow/<workflow_execution_id>.json
executions/nodes/<node_execution_id>.json
```

Agent run 继续存在，但 `NodeExecution` 成为上层抽象。

### Step 5：显式 End / Post-completion

把 final gate complete 后的结束动作建模：

```text
completion.audit -> post_completion.summary -> END
```

短期可以是 runtime 内部自动动作，长期变成 workflow node。

### Step 6：Loop Transition 标准化

把 revise / reject / repair / retry 的 transition event 统一。

### Step 7：Fork / Join 设计先落定义校验

先支持 definition 层和 ledger 层表达 fork/join。

执行仍然可以先顺序，等真实需求出现再做并发调度。如果 LiteFlow spike 证明合适，fork/join 可以优先通过 LiteFlow 的并行表达承载，但 join evidence 和 completion audit 仍由 dual 持久化。

## 13. 当前结论

当前系统能跑完整主线，但它还不是完整 workflow engine。问题主要集中在“节点流转和状态迁移越来越难管理”，这正是轻量编排内核可以发挥价值的地方。

正确演进方向不是引入重量级 BPM / Spring StateMachine，也不是继续让自研 `RoutingEngine` 无限膨胀，而是采用两层模型：

```text
Workstream Domain
  lifecycle / ledger / human gate / completion contract / artifact ownership

FlowOrchestrationKernel
  execution DSL / routing / loop / fork / join / step orchestration
```

LiteFlow 是第一候选，但它只是 `FlowOrchestrationKernel` 的一个实现选项，不是新的领域模型。

短期最重要的仍然是：

1. 完成态契约、summary owner、completion audit；
2. 抽出编排内核端口，防止 routing 逻辑继续腐化；
3. 用小闭环评估 LiteFlow 是否适合承载 execution DSL；
4. 如果适合，再逐步迁移 loop / fork / join 表达；如果不适合，保留端口并继续强化 built-in kernel。

无论是否引入 LiteFlow，Workstream lifecycle、ledger、human gate、completion contract 都必须由 dual 自己守住。
