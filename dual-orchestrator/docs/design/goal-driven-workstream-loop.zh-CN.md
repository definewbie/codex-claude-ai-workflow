# Goal-Driven Workstream Loop

> 状态：P0 设计契约
> 更新日期：2026-06-15

这份文档定义无人值守工作的主流程模型。Workstream 不是“一条流水线跑到最后”，而是围绕一个用户目标持续推进、修复、验收，直到目标进入明确结果。

## 1. Goal

系统应该能接收一个工程目标，并持续迭代，直到进入以下领域结果之一：

| 结果 | 含义 |
| --- | --- |
| `SATISFIED` | verifier 接受结果，且必要完成产物存在。 |
| `SATISFIED_WITH_NOTES` | 目标可接受，但仍有非阻塞备注。 |
| `REJECTED` | verifier 或 human 拒绝结果，且不应继续自动修复。 |
| `BLOCKED` | 需要外部恢复、上下文、额度、重连或人工决策。 |
| `ABANDONED` | 操作者明确停止目标。 |

`WorkflowStatus.COMPLETED` 是执行事实。Goal 是否满足是领域事实。最终它们应该一致，但不能混为一谈。

### 1.1 Goal Loop 不是 Pipeline

本系统不能退化成简单 pipeline：

```text
intake -> design -> plan -> implement -> self-test -> acceptance -> done
```

这个顺序只是一轮 workflow execution 的主干。真正的无人值守能力来自 goal loop：

```text
Goal
  -> 建立任务契约
  -> 执行当前 lifecycle iteration
  -> 收集 artifacts / 自测 / verifier review / human gate decision
  -> 评价 goal satisfaction
      -> SATISFIED: 进入 completion / handoff / delivery
      -> SATISFIED_WITH_NOTES: 进入 completion，但保留非阻塞 notes
      -> NEEDS_REPAIR: 基于 evidence 创建 repair iteration，回到实现或验证前置阶段
      -> NEEDS_REDESIGN: 基于 evidence 创建 revision iteration，回到 design / plan
      -> BLOCKED: 进入 recovery 或 human decision
      -> REJECTED: 带证据停止，不再自动推进
```

换句话说，workflow graph 负责“这一轮怎么走”；goal loop 负责“目标是否已经满足，以及是否应该带着证据再来一轮”。

这条边界必须由 `Goal / Workstream / CompletionContract / Iteration` 守住。`WorkflowExecution`、LiteFlow 或任何 routing kernel 都不能自己判断 goal 是否满足。

### 1.2 两种 Loop

| Loop | 触发 | 创建什么 | 不创建什么 |
| --- | --- | --- | --- |
| Runtime loop | quota、reconnect、dead PID、context window、外部工具瞬时失败 | 新 `WorkflowExecution` + 新 `NodeExecution` | 新 `Iteration` |
| Goal loop | verifier reject、人审 revise、验收证据不足、需求或设计不满足 | 新语义 `Iteration` + 对应 repair/revision 节点 | 新 Workstream |

Runtime loop 解决“这次执行有没有可靠跑完”。Goal loop 解决“用户目标有没有真正满足”。

## 2. 领域模型

```text
Goal
  -> Workstream
       -> WorkflowInstance
            -> WorkflowExecution
                 -> NodeInstance
                      -> NodeExecution
       -> Iteration
       -> Artifact
       -> Gate
       -> Decision
```

| 对象 | 负责 | 不负责 |
| --- | --- | --- |
| Goal | 用户意图、验收依据、最终领域结果。 | 进程 PID 或边路由。 |
| Workstream | 一个 goal 的 workspace、ledger 和生命周期历史。 | 低层工具调用细节。 |
| WorkflowInstance | workstream 内一次 workflow definition 的实例。 | 单次命令执行细节。 |
| WorkflowExecution | 一次 `run` / `resume` / scheduler tick 推进会话。 | 业务验收。 |
| NodeInstance | workflow instance 中某个逻辑节点的实例。 | 进程历史。 |
| NodeExecution | 某个 node instance 的一次具体执行，包含 PID、stdout、stderr、exit code。 | Goal 级状态。 |
| Iteration | 带触发原因的修订或修复轮次。 | 普通 runtime retry。 |
| Artifact | 结构化证据和人类可读输出。 | 隐藏状态。 |
| Gate | 带证据和允许动作的人审或恢复点。 | 临时流程校验。 |

## 3. 当前 P0 契约

P0 不是扩展功能。P0 的含义是：单 workstream 主生命周期足够闭合，可以托付真实任务。

必须满足：

- 终态 workflow 不能再通过 CLI、API、UI 或 scheduler 创建新节点。
- 死掉的 `RUNNING` 进程是 recovery，不是隐式重跑。
- `retry` 复用同一个 active node，不创建新 iteration。
- `revise` 和语义修复才创建带原因的新 iteration。
- human gate 必须展示证据和允许动作；历史 gate 只读。
- CLI trace 和 Operator overview 必须回答同一组三个问题：
  - 现在跑到哪；
  - 为什么停住或继续；
  - 下一步安全动作是什么。
- Agent 决策必须进入 typed artifact。Orchestrator 不能从正文自然语言猜生命周期状态。

## 4. Recovery 与 Repair

| 类型 | 示例 | 正确行为 |
| --- | --- | --- |
| Runtime recovery | quota、reconnect、session 丢失、context window、dead process | 保留同一 active node，打开 recovery，外部原因修复后 retry |
| Semantic repair | verifier reject、需求缺失、scope violation、自测证据弱 | 基于输入 artifact 创建 repair/revision iteration |

不要用一个笼统 retry 路径处理两者。Runtime recovery 解决 `NodeExecution` 可靠性；Semantic repair 解决 goal 是否满足。

### 4.1 WorkstreamAttention

`WorkstreamAttention` 是 operator-facing 的统一解释层。它不拥有状态，只从 ledger 中读取权威事实：

- `workflow/current.json`
- active `nodes/*.json`
- `domain/goal-satisfaction.json`
- `recovery/current-failure.md` 和 recovery assessment
- 必要的 evidence refs

它回答同一个问题：

```text
现在为什么停住或继续？
retry 是否安全？
下一步应该看什么证据、做什么动作？
```

CLI trace、Operator Panel overview、API overview、`current-failure.md` 都应该消费同一个 attention 结果，而不是分别写自己的状态判断。

关键分类：

| Category | 语义 | retry |
| --- | --- | --- |
| `RUNTIME_RECOVERY` | 外部工具、额度、进程、session 等运行可靠性问题。 | 修复外部原因后可 retry。 |
| `ARTIFACT_CONTRACT_FAILURE` | Agent 输出没有满足 typed artifact contract。 | 修 prompt / contract 后 retry。 |
| `SEMANTIC_REVISION_LIMIT` | 设计/计划修订耗尽，目标没有收敛。 | 不建议盲目 retry。 |
| `GOAL_REPAIR_REQUIRED` | 验收或 verifier 证据表明实现需要修复。 | 进入 goal repair loop。 |
| `GOAL_REDESIGN_REQUIRED` | 设计或计划证据表明目标需要重设。 | 进入 design / plan loop。 |

`revision_limit_reached` 不能只显示成普通 recovery。它表示 goal/design loop 没有收敛，应该提示操作者检查 scope、test policy、task contract 或设计证据。

## 5. LiteFlow 边界

LiteFlow 负责 execution graph：

```text
下一步哪个 node 应该 active
```

LiteFlow 不负责：

- goal 是否满足；
- artifact 校验；
- human approval 语义；
- recovery 分类；
- workstream completion contract；
- UI/CLI 投影。

这个边界允许后续扩展 graph，而不把 graph routing 当成领域模型。

## 6. Context Hierarchy

长任务 goal loop 需要压缩上下文。

计划中的上下文层级：

| 层级 | 作用 |
| --- | --- |
| Raw artifacts | 完整 stdout、报告、diff、review packet。 |
| Stage summaries | 单阶段摘要。 |
| Goal gist | 跨阶段的目标级压缩记忆。 |
| Current packet | 下一次 agent task 的最小输入。 |

第一版应该新增 `goal-gist.md` 和 `goal-gist.json` typed artifacts。它们引用 raw evidence，而不是复制所有内容。

## 7. 后续扩展

这些能力重要，但必须建立在 P0 契约之上。

| 能力 | 前置 |
| --- | --- |
| Task Queue | `NodeInstance` / `NodeExecution` ownership 清晰。 |
| Fork / Join | 第一版已有 transition metadata 和 `all_terminal` ledger join；真正并行 worker 执行仍需要 Task Queue。 |
| Compensation | Recovery 和 Repair 语义分离。 |
| 并行 Agent | Queue ownership 和 artifact 冲突策略。 |

## 8. 验证案例

2026-06-15 的 cancer-clinic 真实运行暴露了 P0 缺口：

```text
node-intake-claude_brainstorm-001
  status: RUNNING
  result.running_pid: 11243
  stderr: agent needs reconnect
  process: gone
```

修复前，workflow 仍停在 `RUNNING`，UI 容易误判。正确行为是：

```text
RUNNING + dead pid
  -> node_stale_running_detected
  -> node_stale_running_recovered
  -> node FAILED
  -> workflow WAITING_FOR_RECOVERY
  -> recovery/current-failure.md
```

这个案例现在属于 P0 回归边界。
