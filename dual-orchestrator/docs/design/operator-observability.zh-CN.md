# Operator Observability

这份文档说明 CLI、API 和 operator panel 共用的可观测模型。

## 目标

Operator 体验要快速回答一个问题：

> 这个 workstream 现在跑到哪，为什么停，下一步该做什么？

无论用户看 CLI、调用本地 API，还是打开 operator panel，答案都应该一致。

## Read Models

UI 不直接解析原始 Agent logs。它消费稳定的 ledger read models：

| Read model | 作用 |
|---|---|
| `workflow/current.json` | 当前 workflow 状态和 active nodes。 |
| `timeline.json` | 按顺序展示 node/gate 进度。 |
| `metrics/workstream-metrics.json` | Node、gate、iteration、duration 和 provider run 统计。 |
| `reviews/*.json` / `.md` | Human gate packet 和推荐 review action。 |
| `recovery/current-failure.json` / `.md` | 当前失败说明和 recovery hint。 |
| `reports/workstream-summary.md` | 人类可读的 lifecycle 复盘。 |
| `runs/*/outputs/*.contract.json` | 已校验的 Agent artifact sidecar。 |

这些 read models 由 runtime 和 ledger 生成。Operator panel 故意保持很薄。

## CLI First

`dualctl trace` 是主要 operator 命令。它把 status、timeline、attention items、final acceptance、repair loops、key artifacts 和 next actions 折到一屏。

更深入的命令包括：

```bash
dualctl status --workdir <effective_workdir> <workstream_id>
dualctl timeline --workdir <effective_workdir> <workstream_id>
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl recovery show --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
```

CLI 和 UI 应使用同一套概念和标签。UI 不应该发明第二套 workflow 语言。

## Operator Panel

Operator panel 是本地单用户工具，不是多用户 Web 产品或托管 dashboard。

首屏优先展示：

1. lifecycle progress；
2. 当前状态和停止原因；
3. 下一步动作；
4. attention items；
5. human gate 打开时展示 review packet；
6. failure active 时展示 recovery summary；
7. grouped timeline 和 metrics。

Action 应该有上下文。Human gate 应把 review evidence 和 decision buttons 放在一起。Debug details、raw packet 和 raw ledger path 应该是次级信息。

## Human Gate 语义

Human gate 是真实暂停点。Smoke script 可以为了 benchmark 自动 approve gate，但正常使用时，人应该先读 review packet，再选择：

- approve and continue；
- request revision with feedback；
- complete the workstream；
- stop and inspect。

Packet 应展示足以支持决策的最小证据：TaskContract、verifier decision、must-fix、相关 Git evidence、已知风险和推荐检查项。

## Timeline 语义

Timeline 是按时间排序、按 lifecycle stage 分组的进度视图，不是 log dump。它应该让进度一眼可见：

```text
intake -> contract -> design -> plan -> implementation -> self-test -> acceptance -> delivery -> handoff
```

每个 group 内保留 node 时间戳和状态。Failed 或 waiting node 应该明显可见。

## Metrics

当前 metrics 保持轻量：

- node counts by status；
- agent run counts by provider；
- completed / failed / prepared agent runs；
- recoverable failures；
- total and average provider duration；
- gate and iteration counts；
- longest node duration。

Token 和 cost metrics 延后，直到 adapter 能提供可信 usage data。系统不猜成本。

## 已暴露的易用性问题

真实 `cancer-clinic` run 暴露了一个问题：operator panel 仍会展示英文且过于笼统的 stopped/retry 文案，例如：

```text
Workflow is stopped. Fix the cause if needed, then Retry marks the active node ready to run again.
```

这对普通使用者不友好，也容易把不同状态混在一起：已中止的旧 workstream、等待人审的新 workstream、真正需要 recovery 的失败，都不应该用同一类提示表达。

后续改 UI/CLI 文案时应遵守：

- 所有 operator-facing 提示优先提供中文表达；
- `WAITING_FOR_HUMAN` 显示“等待人工审查”，突出 review packet 和 approve/revise；
- `WAITING_FOR_RECOVERY` 显示“需要恢复”，给出失败节点、原因和建议动作；
- `FAILED` / `ABORTED` 显示“已失败” / “已中止”，不要默认引导 Retry；
- 如果同一任务已经启动了新的 workstream，提示用户打开最新 workstream。

## 未来方向

下一步有价值的不是更重的 UI 框架，而是更强的一致性：

- CLI trace、API overview 和 operator panel 渲染同一组 attention items。
- 已完成的 workstream 要展示 completion audit，并拒绝误触发 resume / retry / approve。
- Stale running 应该是显式 recovery action，不能藏在按钮里，也不能静默重跑。
- Failure / recovery summary 保持结构化。
- Review packet layout 围绕真实人工决策继续改进。
- 等 read models 稳定后，再通过 SSE 或 file watching 增加实时推送。

## TODO：流程交互顺滑度

真实运行暴露了另一个更大的体验问题：当前 workflow 过于频繁地停在人审点，operator 需要不断点击 approve / retry / run，流程像“人工驱动的分段脚本”，还没有达到“系统自动跑，人在关键点把关”的形态。

后续需要专门反思和优化：

- 哪些 gate 是必须人工确认的产品 / 架构 / 交付卡点；
- 哪些 gate 只是流程校验或格式校验，应该由系统自动处理；
- 哪些 verifier 反馈可以进入有限自动 loop，而不是每次都让人手工接管；
- 自动 loop 的退出条件是什么，例如最大轮次、语义冲突、证据不足、外部工具失败；
- operator panel 应该把“为什么停”和“下一步建议”讲清楚，而不是只暴露按钮；
- CLI 和 UI 都应该支持一键 run-to-next-human-decision，而不是让用户理解每个内部节点。

目标不是取消人工 review，而是把人工 review 放在真正需要判断的位置：需求边界、设计取舍、验收争议和交付确认。
