# 边界优先工程纪律

> 状态：主线开发强制纪律
> 适用范围：workflow、orchestrator、agent lifecycle、adapter、recovery、artifact contract、human gate、delivery、ledger、projection、UI、CLI、API。

这份文档不是架构愿景，而是实现前必须通过的工程门槛。它的目的只有一个：防止系统在真实 case 暴露问题后继续用局部 `if/else`、字符串判断、UI 补丁或 handler 内部规则来掩盖领域模型缺口。

## 1. 核心判断

当一个问题涉及状态、流程、外部 Agent、结构化产物、人审、恢复、补偿、账本或展示层一致性时，默认先判断它是不是模型边界问题，而不是实现 bug。

如果同类问题第二次出现，默认结论是：

```text
先检查是否缺少一等领域对象，再考虑局部修复。
```

常见信号包括：

- 历史事件被当成当前状态。
- UI、CLI、API、ledger 对同一 workstream 给出不同解释。
- Agent 输出稍有变化就导致流程漂移。
- recovery、retry、repair、iteration 混在一起。
- handler、adapter、projection 中散落 `contains` / `startsWith`。
- terminal 状态还能被继续 run、resume、approve。
- workflow definition 和 Java registry / factory 各自表达同一件事。

## 2. 必须先写 Boundary Review

动代码前必须先写出以下内容。写不清楚，就不能开始实现。

```markdown
## Boundary Review

- Problem type:
- Touched boundary:
- Source of truth:
- Definition:
- Instance:
- Execution:
- Domain state:
- Execution state:
- Artifact ownership:
- Projection surfaces:
- Is this a model gap or implementation bug:
- New domain object needed:
- Decision:
```

判断标准：

- `Definition` 说明“这个流程、节点、契约是什么”。
- `Instance` 说明“这是哪一个长期对象或节点实例”。
- `Execution` 说明“这一次具体运行发生了什么”。
- `Domain state` 说明业务生命周期真相。
- `Execution state` 说明运行时状态、进程、退出码、失败信号。
- `Projection` 只能展示 read model，不能反推生命周期。

## 3. 修改顺序

边界类问题必须按以下顺序处理：

```text
领域语义
  -> source of truth
  -> typed event / artifact / signal
  -> runtime guard / validator
  -> projection
  -> UI / CLI 文案
```

禁止反向处理：

```text
先改 UI
先加 handler if
先扫 stdout/stderr 全文
先靠自然语言正文路由
先让 human gate 兜底流程机械问题
```

## 4. 一等对象准入

新增领域对象前必须回答：

- 它解决了哪个重复出现的问题？
- 为什么现有 Definition / Instance / Execution / Artifact / Gate / Recovery / Projection 不能承载？
- 谁创建它？
- 谁修改它？
- 谁读取它？
- ledger 中的持久化位置是什么？
- 哪些 UI / CLI / API 需要显示它？
- 哪个测试证明边界被守住？

如果回答不清楚，不新增对象；如果仍需止血，必须标记为 stopgap。

## 5. Stopgap 规则

只有系统被阻塞、完整模型修复成本明显过高时才允许 stopgap。

必须写明：

```markdown
Stopgap fix:
- What is contained:
- Why this is not the final model:
- Why it does not corrupt source of truth:
- Follow-up model fix:
- Revisit trigger:
```

Stopgap 不能被描述成“主线闭环完成”。

## 6. 自审闭环

边界类改动完成后，必须做一次自审：

```markdown
## Boundary Self Review

- Did this add scattered string rules:
- Did this confuse historical evidence with current state:
- Did this confuse Definition / Instance / Execution:
- Did this let projection create state:
- Did this sweep sibling concepts:
- Did this add a boundary test:
- Result: PASS / WARN / FAIL
```

如果结果是 `WARN` 或 `FAIL`，不能继续叠功能。必须回到 Boundary Review，或者把明确的残留问题记录为已接受技术债。

## 7. 对 acpx / Claude adapter 的具体约束

acpx / Claude Code / Codex CLI 都属于外部 adapter。adapter 输出不能直接成为 workflow 状态。

正确链路是：

```text
stdout / stderr / acpx stream
  -> AgentRuntimeSignal / AgentLifecycleEvent
  -> AgentExecutionHealth
  -> NodeExecution result
  -> Workflow transition
```

约束：

- stderr 中的一行 `agent needs reconnect` 是 runtime signal，不是当前失败状态。
- `Resource not found` 只能说明 session resume 失败，不能直接说明 agent execution 失败。
- 如果后续出现 `session/new success`、prompt accepted、tool result、agent activity 或 terminal contract，前面的 reconnect signal 必须被视为已恢复的历史事件。
- failure classifier 不能每轮全量重复消费历史 stderr。
- terminal contract 必须优先于 recoverable diagnostics，但只接受完整闭合的 contract。
- `interruptRunningProcess` 只能作用于“当前且未恢复”的 runtime signal。

## 8. 强制验证

主线边界修复至少需要一种程序化证据：

- 单元测试验证 typed signal / event / artifact。
- replay 测试验证历史日志不会重复触发当前失败。
- transition 测试验证 terminal / recovery / retry / repair 边界。
- projection 测试验证 CLI / API / UI 消费同一 read model。
- real-case 验证只作为集成信号，不能替代边界测试。

## 9. 完成定义

一个边界类问题只有同时满足以下条件，才算完成：

- source of truth 被命名并保持唯一。
- Definition / Instance / Execution 没有混用。
- 外部输出先转换成 typed fact，再参与路由。
- terminal/recovery/human gate 边界有核心层 guard。
- UI / CLI / API 不独立推断状态。
- 当前状态和历史证据分离。
- 至少一个回归测试覆盖导致本次问题的边界。
- 文档、测试和实现表达同一个模型。
