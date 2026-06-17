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

有些 ACP adapter 会在 stderr 中写出明确的可恢复信号，但外层进程不会立即退出。例如 `agent needs reconnect` 可能导致 Agent run 长时间停在 `RUNNING`。这是 adapter 执行边界的问题，不应交给 UI 或人工判断。`AcpxAgentTransport` 会轮询 stderr 中的 catalog 信号；一旦匹配到可恢复 runtime failure，就主动终止本次 acpx 进程并返回可分类的 exit result，让 workflow 进入 `WAITING_FOR_RECOVERY`。

工具权限失败同样属于 adapter execution 边界。dual 在非实现阶段不会使用
`approve-all`，而是通过 workflow node 的 `permission_profile` 声明可用工具：

| Profile | 用途 | 权限语义 |
| --- | --- | --- |
| `observe` | Claude intake / design / plan / handoff | 允许 Read/Grep/Glob 以及安全仓库观察命令，例如 `git`、`ls`、`find`、`rg`。 |
| `review` | Codex review / acceptance | 与 `observe` 类似，只用于审视和验收。 |
| `implement` | Claude implementation / self-test | 显式允许实现阶段写代码和运行测试，当前映射到 acpx `approve-all`。 |

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
