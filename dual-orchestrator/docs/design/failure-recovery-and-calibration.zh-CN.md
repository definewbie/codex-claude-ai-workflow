# Failure Recovery 与 Calibration

这份文档说明 dual-orchestrator 如何处理 Codex 验收后的失败、Agent 执行错误，以及可复用经验沉淀。

## 问题

Agent lifecycle 不应该把所有失败都处理成“让 Claude 再试一次”。不同失败需要不同路由：

- 实现错误：回到 Claude implementation；
- contract 缺口：回到 contract 澄清；
- 上下文不足：重建上下文并重新验收；
- review noise：重新验收或让人检查 verifier 输出；
- 工具或 quota 问题：等待恢复或稍后 retry。

盲目 retry 会浪费 quota，也可能破坏任务边界。

## Runtime Recovery

Runtime failure 是外部或机械问题：

- `acpx` session 缺失；
- rate limit 或 quota；
- Agent reconnect；
- Agent tool permission 被非交互权限策略阻塞；
- context window 溢出；
- structured output 非法；
- 命令退出失败。

这类失败会尽量让 workflow 停在可恢复状态。Ledger 写入：

```text
recovery/current-failure.md
recovery/current-failure.json
```

`dualctl recovery show`、`dualctl trace`、API 和 operator panel 都读取同一个 recovery model。

Runtime 分类会同时读取 Agent stdout 和 stderr。原因是不同 ACP adapter 和模型供应商不一定把运行时诊断写到同一个流里：quota 或 HTTP 429 可能出现在 stdout，而 acpx 连接信息仍在 stderr。因此一次 Agent run 失败时，recovery evidence 应保留两个流。

Adapter 会把 runtime failure matching 收敛在一个很小的 signal catalog 后面：

- `AcpxFailureEvidence` 负责执行证据边界：stdout、stderr、exit code。
- `AcpxFailureSignalCatalog` 负责 provider / adapter 的信号模式，例如 reconnect、session missing、rate limit、context window。
- `AcpxFailureClassifier` 只负责把 evidence 映射到 signal；如果没有匹配，才退回原始 `ACPX_EXIT_<code>`。

以后新增 provider 特定文案时，应添加到 catalog 的 signal matcher，不应散落到 workflow routing、ledger projection 或 UI 代码里。

有些 ACP adapter 会在 stderr 中写出明确的可恢复信号，但外层进程不会立即退出。例如 `agent needs reconnect` 可能导致 Agent run 长时间停在 `RUNNING`。这是 adapter 执行边界的问题，不应交给 UI 或人工判断。

`AcpxAgentTransport` 会轮询 stderr 中的 catalog 信号。这里的运行边界必须很窄：进程退出、完整 terminal contract、或 typed recoverable failure。普通 stdout 进度不是 lifecycle state，因为挂住的 ACP 进程仍然可能继续吐 client frame、心跳或半截 tool output。

当 recoverable signal 持续超过有界 grace period 后，adapter 会终止本次 acpx 进程树，并返回可分类的 exit result，让 workflow 进入 `WAITING_FOR_RECOVERY`。默认 grace 是 30 秒，可通过 `DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS` 配置。如果 Java 进程环境里有 `API_TIMEOUT_MS`，默认 grace 会按该 timeout 加一个小 buffer 推导。如果 timeout 只在 Claude ACP 子进程里注入，就需要显式设置 dual 的 grace，避免两个 timeout 互相打架。

terminal contract 优先于 reconnect 诊断，但必须是完整契约：闭合的 `dual-control` fence、必需字段，以及后续 `[done] end_turn`。半截或看起来像历史残留的 contract fragment 不能被当成完成。

### acpx 生命周期信号模型

`agent needs reconnect`、`Resource not found`、HTTP 401/429/529、tool
permission blocked 这类文本都只是外部 adapter 观察到的运行时信号。它们不是
workflow 状态，也不是业务失败。尤其是 `Resource not found`：它只能说明某次
`session/resume` 失败，不能直接说明当前 Agent execution 已失败；如果后续
`session/new` 成功并继续出现 prompt、tool result 或 Agent output，前面的
reconnect 只能作为已恢复的历史事件保留。

因此 adapter 层需要把原始日志转换为更明确的模型：

```text
stdout / stderr / acpx stream
  -> AgentRuntimeSignal
  -> AgentLifecycleEvent
  -> AgentExecutionHealth
  -> NodeExecution result
```

建议语义：

| 模型 | 含义 | 不负责 |
|---|---|---|
| `AgentRuntimeSignal` | 从 stdout、stderr 或 acpx stream 中观察到的原始运行时信号，例如 reconnect、auth failed、rate limit。 | 直接推进 workflow。 |
| `AgentLifecycleEvent` | acpx / Agent 生命周期事件，例如 process started、session resume failed、session new created、prompt accepted、tool result、terminal contract seen。 | 判断业务是否完成。 |
| `AgentExecutionHealth` | 当前 execution 的健康判断，例如 healthy、recovering、stalled、recoverable failure、terminal success。 | 保存原始完整日志或替代 NodeExecution。 |

这个模型的收益：

- 避免每轮轮询都全量扫描 stderr，把历史 reconnect warning 当成当前失败。
- 允许慢模型继续运行，只要仍有当前生命周期活动或最终 terminal contract。
- 允许真正无恢复迹象的 reconnect / permission blocked / auth failed 快速进入
  recovery，不再无限挂住。
- 保留 provider 真实错误，例如 401、429、529、context window，而不是只暴露
  acpx 包装层的 reconnect。
- 让 `interruptRunningProcess` 成为真实语义：只对“当前且未恢复”的 runtime
  signal 生效，而不是日志标签。

边界：

- Adapter 只判断 execution health，不判断 workstream goal 是否满足。
- Adapter 不做 semantic repair，不创建新的业务 iteration。
- Adapter 不自动重放 `implement` profile 的 prompt，因为进程可能已经半写文件。
- Recovery model 可以引用 runtime signal 和 lifecycle event，但 UI / CLI 不能直接
  从 stderr 推断状态。

风险：

- 如果 lifecycle event 抽取过窄，可能漏掉真实恢复证据，导致仍然误杀慢 Agent。
- 如果抽取过宽，可能把无意义心跳当成进展，延迟 recovery。
- 如果只从 acpx stderr 建模，会继续看不到 provider stream 中更深层的 401/529。
- 如果没有 offset / timestamp，历史 signal 仍会被重复消费。

为降低风险，第一版应保持模型小而可测：

- 每条 signal / event 必须有 source、offset 或 observedAt。
- 同一条历史 signal 只能消费一次。
- `session/new success`、prompt accepted、tool result、完整 terminal contract 可以
  supersede 较早的 reconnect signal。
- provider stream 中的明确失败信号可以提升为当前 failure。
- 完整 terminal contract 始终优先于 recoverable diagnostics。

这套模型能 cover 的问题：

| 问题 | 是否覆盖 | 说明 |
|---|---|---|
| `session/resume -> Resource not found -> session/new success` 被误判失败 | 是 | resume 失败会被后续 session new / agent activity 标记为 recovered。 |
| GLM / DeepSeek 慢但仍在工作 | 部分覆盖 | 只要有可识别生命周期活动或 terminal contract，不会因旧 stderr 被误杀；但真正长时间无活动仍会进入 recovery。 |
| acpx 外层进程挂住且无恢复事件 | 是 | 当前且未恢复的 recoverable signal 超过 grace 后进入 recovery。 |
| provider 401 / quota / overload | 是 | 通过 stdout/stderr + acpx stream signal 分类为明确 runtime failure。 |
| implementation 阶段半写文件后 reconnect | 部分覆盖 | 不自动重放；会进入可见 recovery，需要 operator 或后续 semantic repair 判断。 |
| OS 进程树杀不干净 | 不完全覆盖 | 这是 process isolation 问题，需要后续进程组/cleanup 策略。 |

验收标准：

- replay 测试证明历史 reconnect 不会重复触发当前失败。
- replay 测试证明 `Resource not found` 后 `session/new success` 不进入 recovery。
- replay 测试证明无恢复事件的 reconnect 最终进入 `RECOVERABLE_AGENT_RECONNECT`。
- terminal contract 完整出现时优先成功。
- provider stream 中的 401/529 能覆盖包装层 reconnect。
- real-case 中慢模型不会因为旧 stderr 被误杀。

对于 read-only 的 `observe` / `review` profile，adapter 可以重建命名 acpx session 一次，并用同一个 prompt 重试。`implement` profile 不会在 reconnect 后自动重放，因为上一轮进程可能已经半写文件；写代码阶段的恢复必须保持可见，要么作为 execution recovery，要么进入后续有边界的 semantic repair route。

在 session rebuild 之前，还有一层更低级的短重试：`observe` / `review`
profile 会给 acpx 传 `--prompt-retries 1`。这只用于只读观察或外部审视类
Agent 调用，因为重放同一个 prompt 不应该产生重复业务写入。`implement`
profile 不使用 prompt-level retry；写代码阶段的恢复必须保持可见，要么作为
execution failure 暴露出来，要么进入有边界的 semantic repair route。

工具权限失败同样属于 adapter execution 边界。dual 在非实现阶段不会使用
`approve-all`，而是通过 workflow node 的 `permission_profile` 声明可用工具：

| Profile | 用途 | 权限语义 |
| --- | --- | --- |
| `observe` | Claude intake / design / plan / handoff | 只允许 Read/Grep/Glob。不会自动批准 ACP `execute`；计划和设计阶段不能运行验证命令。 |
| `review` | Codex review / acceptance | 与 `observe` 类似，只用于审视和验收，不拥有 shell 执行权限。 |
| `implement` | Claude implementation / self-test | 显式允许实现阶段写代码和运行测试，当前映射到 acpx `approve-all`。 |

这个限制是有意的。ACP `execute` 是宽权限类别，不能只靠 Claude Code
工具名里的 “安全 Bash 命令” 作为可靠边界。build、typecheck、pytest、npm、
npx 等命令属于 implementation/self-test execution，不属于 intake、design、
plan 或 review。

如果 Agent 在非交互模式下请求了 profile 之外的工具，adapter 会把输出中的
`Tool permission request failed` / `Tool use aborted` 归一化为
`RECOVERABLE_AGENT_PERMISSION_BLOCKED`。这不是业务失败，也不应该触发语义
repair loop；operator 需要检查该节点的 permission profile 是否过窄，或者
Agent 是否试图越过当前阶段边界。

## 自动恢复调度

自动恢复的范围必须很窄。

Scheduler 读取 `RecoveryAssessment`。只有当前失败被明确分类为下面这种情况时，才允许自动执行 `retry + resume`：

```text
classification = recoverable_agent_runtime
retryReadiness = RETRY_AFTER_EXTERNAL_FIX
```

这类情况对应外部运行时问题，例如 quota 窗口、reconnect、session 状态恢复。Scheduler 不会自动 retry：

- artifact contract failure；
- 语义修订上限；
- manual recovery；
- human gate；
- 已完成或已失败的 workstream。

这样边界会比较清楚：failure classifier 解释失败，scheduler 只推进安全的 runtime case，产品判断和 contract 判断仍然由 human / verifier 负责。

## Semantic Failure

Semantic failure 发生在 verifier 检查实现和证据之后。Codex 输出结构化 acceptance review。Orchestrator 读取已校验字段，并把失败归类为稳定类别：

| Category | Route |
|---|---|
| `IMPLEMENTATION_BUG` | 回到 Claude implementation。 |
| `CONTRACT_GAP` | 回到 contract compile / clarification。 |
| `CONTRACT_AMBIGUITY` | 回到 contract compile / clarification。 |
| `CONTEXT_INSUFFICIENT` | 重建上下文，并回到实现或 verifier。 |
| `TOOL_LIMITATION` | 保留证据并展示恢复建议。 |
| `REVIEW_NOISE` | 重建 verifier context，或让 human 检查。 |

`REJECT` 会创建 repair route，并在观测层显示为 failure classification。`ACCEPT_WITH_NOTES` 会记录 notes classification data；如果 notes 非阻塞，仍可以进入报告阶段。

## Repair Loop

Full lifecycle 包含有边界的 repair loop：

```text
Codex acceptance
  -> acceptance decision
  -> failure classification
  -> route to contract / implementation / verifier
  -> self-test
  -> acceptance again
```

这个 loop 受 revision limit 和 human gate 约束。目标不是无限自治修复，而是在保留证据的前提下，让系统走下一条明显路径，并允许人随时介入。

## Calibration Candidate

有些失败是可复用经验。分类后的失败如果像一种模式，运行时可以写入 calibration candidate：

```text
calibration/candidates/<candidate_id>.json
calibration/candidates/<candidate_id>.md
```

第一版 promotion 保持保守：

1. 人列出并阅读 candidate。
2. 人把 candidate 标记为 accepted、rejected 或 deferred。
3. 系统生成可审阅的 apply plan。
4. 只有 accepted 的 contract-template candidate 可以 promote。
5. Promotion 只追加到项目级 `.agent/templates/` 原则文件。

运行时不会自动修改内置 prompt、workflow definition、context rule 或测试。

## 为什么不是规则引擎

当前设计是规则集中化，而不是引入通用规则引擎。这是有意的。

Workflow 仍然需要简单、可测试的 Java 组件来表达：

- allowed decisions；
- required artifact fields；
- lifecycle status consistency；
- failure categories；
- routing outcomes。

在产品形态稳定前，通用规则引擎会先带来配置复杂度。近期更合适的边界是 typed artifacts 加 centralized validators。

## Operator 视角

每个失败都应该回答三个问题：

1. 什么失败了？
2. 系统为什么停在这里？
3. 下一步该运行什么命令，或需要人做什么决定？

这个答案必须在 CLI、API 和 UI 中一致。Recovery model、timeline、metrics 和 review packet 就是支撑这个一致性的 read models。
