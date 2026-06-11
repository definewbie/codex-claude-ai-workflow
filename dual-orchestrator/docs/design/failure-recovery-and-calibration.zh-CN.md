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
