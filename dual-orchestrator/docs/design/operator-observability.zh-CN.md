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

本地 server 还提供一个轻量 Server-Sent Events 进度流：

```text
GET /api/workstreams/<workstream_id>/stream?workdir=<effective_workdir>
```

这个流发送 `overview` 事件，数据来自同一套 API overview read model；没有变化时发送 heartbeat。它只是投影通道，不拥有 workflow 状态，也不从原始 log 猜进度。

## CLI First

`dualctl trace` 是主要 operator 命令。它把 status、timeline、attention items、final acceptance、repair loops、key artifacts 和 next actions 折到一屏。

Trace 和 runs 是观测投影，不是状态机执行器。如果历史 `runs/<run_id>/run.json`
缺失或损坏，它们必须把问题显示为 metadata warning，而不是让命令失败、也不是把
已经完成的 workstream 重新解释成失败。只有显式 `inspect --run <run_id>` 才严格读取
单次 run metadata。

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

P2 起，Operator Panel 分成两个视图：

| 视图 | 用户 | 目的 |
|---|---|---|
| 任务视图 / Operator Console | 最终用户、reviewer、operator | 看任务进度、在人闸审批、处理恢复、确认交付。 |
| 管理诊断 / Management Diagnostics | dual 使用者、开发者、排障者 | 查看 ledger、raw facts、node id、RCA、CLI 命令、token/cost 和恢复证据。 |

设计原型：[operator-console-prototype.html](operator-console-prototype.html)。
实现计划：[operator-console-implementation-plan.zh-CN.md](operator-console-implementation-plan.zh-CN.md)。

首屏优先展示：

1. lifecycle progress；
2. 当前状态和停止原因；
3. 下一步动作；
4. attention items；
5. human gate 打开时展示 review packet；
6. failure active 时展示 recovery summary；
7. grouped timeline 和 metrics。

Action 应该有上下文。Human gate 应把 review evidence 和 decision buttons 放在一起。Debug details、raw packet 和 raw ledger path 应该是次级信息。

任务视图默认不展示 raw JSON、内部 node id、调度器术语和完整 ledger path。这些信息进入管理诊断视图。管理诊断不是阉割版 UI，而是按 RCA 方式组织：

- 症状；
- 影响范围；
- 时间线；
- 当前判断；
- 证据链；
- 立即处置命令；
- 修复后的验证步骤；
- 预防复发提示；
- raw facts。

这样最终用户不用理解内部实现，dual 使用者仍然能定位问题并着手修复。

Scheduler Tick 有一个显式 recovery 选项：

- 未勾选：只推进 `RUNNING` workstreams；
- 勾选：会检查 `WAITING_FOR_RECOVERY` workstreams，但只自动 retry/resume retry-ready 的运行时故障。

这个选项消费 `RecoveryAssessment`。它不会自动修 artifact contract、不会解决语义争议，也不会替人做 human gate 决策。

后台 scheduler 开启时，还可以配置一个可选的本地通知命令，在 gate、failure、completion 边界触发。默认关闭。命令会收到 `DUAL_WORKSTREAM_ID`、`DUAL_STATUS`、`DUAL_EVENT`、`DUAL_WORKDIR`、`DUAL_ACTIVE_NODE` 等环境变量。通知失败只记录到 scheduler status，不改变 workflow 状态。

## Human Gate 语义

Human gate 是真实暂停点。Smoke script 可以为了 benchmark 自动 approve gate，但正常使用时，人应该先读 review packet，再选择：

- approve and continue；
- request revision with feedback；
- complete the workstream；
- stop and inspect。

Packet 应展示足以支持决策的最小证据：TaskContract、verifier decision、must-fix、相关 Git evidence、已知风险和推荐检查项。

Packet 不是 raw Agent log dump。`reviews/*.md` / `.json` 应优先展示结构化
sidecar 的决策摘要、风险、缺失证据、Must Fix 和相关 artifact 路径。完整 stdout /
stderr 只作为调试入口引用，默认不嵌入到人审首屏，避免 operator 在工具日志里找结论。

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

当 acpx adapter 能把当前 prompt 匹配到 session stream usage event 时，会记录 token 和 cost。它们是最佳努力的观测数据，不参与 workflow 路由，也不等同于精确 provider 账单对账。

任务视图应展示轻量 token / cost 卡片，让用户知道当前任务是否存在上下文膨胀或成本异常。管理诊断视图可以展示更细粒度的 prompt tokens、completion tokens、预算比例、估算成本和数据来源。

这些指标只用于观测和调优，不参与 workflow 路由，也不能替代 artifact contract 或 recovery decision。

## Loop 语义

dual 的生命周期不是一次性线性流水线。一个 goal 可能经历多轮语义修复：

```text
goal -> design/implement/acceptance -> verifier reject -> bounded repair -> acceptance
```

UI 需要保留线性生命周期进度条，因为它最容易理解；同时必须补充轻量 loop 表达：

- 当前轮次，例如 `实现修复轮 2/3`；
- loop history，说明每一轮由谁触发、为什么回到前面阶段、当前结果是什么；
- semantic repair、runtime retry、human revision 必须分开。

管理诊断视图可以展示完整 iteration id、trigger artifact、previous decision、loop reason 和 max rounds。任务视图只展示用户判断所需摘要。

## 已暴露的易用性问题

一次私有生产型仓库 real-case run 暴露了一个问题：operator panel 仍会展示英文且过于笼统的 stopped/retry 文案，例如：

```text
Workflow is stopped. Fix the cause if needed, then Retry marks the active node ready to run again.
```

这对普通使用者不友好，也容易把不同状态混在一起：已中止的旧 workstream、等待人审的新 workstream、真正需要 recovery 的失败，都不应该用同一类提示表达。

后续改 UI/CLI 文案时应遵守：

- 所有 operator-facing 提示优先提供中文表达；
- `WAITING_FOR_HUMAN` 显示“等待人工审查”，突出 review packet 和 approve/revise；
- `WAITING_FOR_RECOVERY` 显示“需要恢复”，给出失败节点、原因、建议动作和是否符合自动恢复条件；
- `FAILED` / `ABORTED` 显示“已失败” / “已中止”，不要默认引导 Retry；
- 如果同一任务已经启动了新的 workstream，提示用户打开最新 workstream。

## 未来方向

下一步有价值的不是更重的 UI 框架，而是更强的一致性：

- CLI trace、API overview 和 operator panel 渲染同一组 attention items。
- 已完成的 workstream 要展示 completion audit，并拒绝误触发 resume / retry / approve。
- Stale running 应该是显式 recovery action，不能藏在按钮里，也不能静默重跑。
- Failure / recovery summary 保持结构化。
- Review packet layout 围绕真实人工决策继续改进。
- 实时推送已经通过 overview SSE stream 接入。只有当这个 read-model stream 不够用时，再考虑 file watching。

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
