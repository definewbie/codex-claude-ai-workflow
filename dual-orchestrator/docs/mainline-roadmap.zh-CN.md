# 主线功能路线图

English version: [mainline-roadmap.md](mainline-roadmap.md)

这份路线图记录 dual-orchestrator 为了跑通完整研发生命周期还需要补齐的主功能。

它刻意保持简短。细节设计放在对应设计文档里。

## 原则

主线不是“把所有有用功能都做掉”。主线是让一个 workstream 从用户目标走到安全交付所需的最小能力集合：

```text
goal -> 安全 worktree 执行 -> 验证过的 artifacts -> 人工关键判断 ->
delivery merge -> 可追踪完成
```

## 设计覆盖

这份 roadmap 是主线计划索引。P0-P2 的每个主功能在实现完成前，都应该能指向稳定的设计文档。功能可以分步实现，但领域边界、source of truth 和投影面必须先写清楚。

| 优先级 | 设计主题 | 设计文档 |
|---|---|---|
| P0 | Goal loop、semantic repair、completion contract | [goal-driven-workstream-loop.zh-CN.md](design/goal-driven-workstream-loop.zh-CN.md) |
| P0 | Workflow 状态和 terminal guard | [workflow-state-machine.zh-CN.md](design/workflow-state-machine.zh-CN.md) |
| P0 | 已验收 worktree 合回 original repo | [delivery-merge.zh-CN.md](design/delivery-merge.zh-CN.md) |
| P1 | Definition / Instance / Execution 和 AgentTask 边界 | [execution-instance-model.zh-CN.md](design/execution-instance-model.zh-CN.md) |
| P1 | Artifact contract 和 context budget 治理 | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| P1 | Recovery 分类和 calibration | [failure-recovery-and-calibration.zh-CN.md](design/failure-recovery-and-calibration.zh-CN.md) |
| P2 | Operator overview、trace、API、UI 投影一致性 | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md) |
| P2 | Public benchmark 和发布证据 | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) |
| P2 | 开源发布准备 | [release-checklist.zh-CN.md](release-checklist.zh-CN.md) |

如果下面表格里的项目没有对应设计文档，那就是计划缺口，不是可以跳过设计直接实现。

## P0：生命周期闭环

P0 是阻塞真实使用的能力。

| 项目 | 状态 | 目的 | 设计 |
|---|---|---|---|
| Delivery merge | Done for MVP | 把验收通过的 worktree diff 安全合回 original repo。 | [delivery-merge.md](design/delivery-merge.md) |
| Goal-driven repair loop | Partial | Codex reject 或 evidence 不足时进入有限 repair，而不是随手重跑。 | [goal-driven-workstream-loop.zh-CN.md](design/goal-driven-workstream-loop.zh-CN.md) |
| Human gate 降噪 | Partial | 只在需求、设计、验收、交付这类真正需要判断的位置停。 | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md) |
| Terminal-state guards | Implemented, keep testing | completed workstream 只能观测和显式交付，不能误触发 resume/retry。 | [workflow-state-machine.zh-CN.md](design/workflow-state-machine.zh-CN.md) |
| Harness enforcement | Partial | 不依赖 LLM 自觉遵守纪律；由 contract、runtime guard、consistency tests 强制边界。 | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

### P0 Delivery Merge Checklist

Delivery Merge 已经实现为 completed workstream 之后的显式交付动作。它不是 workflow `resume` 的一部分，也不会由后台调度自动修改 original repo。

| 维度 | 状态 | 证据 |
|---|---|---|
| 设计 | Done | [delivery-merge.zh-CN.md](design/delivery-merge.zh-CN.md) 定义了边界、前置条件、ledger 结构、CLI 和测试要求。 |
| CLI | Done | `dualctl delivery merge --workdir <source_worktree> --target <original_repo> <workstream_id>` |
| Git adapter | Done | `GitPatchMergeService` 负责生成并应用 tracked-file patch，同时排除 ledger/build artifacts。 |
| Ledger model | Done | `delivery/merge-plan.json`、`merge.patch`、changed/excluded file lists 和 apply logs。 |
| Projection | CLI/read model 已完成 | `trace`、`summary`、`status` 已展示 merge 状态，并且在 `APPLIED` 后切换下一步动作；UI 按钮属于 P2 可选增强。 |
| 测试 | Done | `GitPatchMergeServiceTest` 和 `DeliveryMergeCommandTest` 覆盖 accepted merge、rejected acceptance、dirty target、excluded paths、unsupported untracked business files。 |
| Review | Done | 边界结论：delivery 是显式命令；completed workflow 仍是 terminal；target repo 不会被自动修改。 |
| 重构 | MVP 可接受 | 第一版只支持 tracked business files。untracked business files 会清晰失败，不会静默合并。 |
| Delivery 验证 | Done | 2026-06-17：复用一个 completed 的真实 acpx contract smoke workstream（`ws-20260617T005016Z-N8LF`），把 accepted worktree diff apply 到临时 clean target repo。`merge-plan.json` 到达 `APPLIED`，target staged `README.md` 和 `main.py`，原始 smoke repo 保持干净，`status` / `summary` / `trace` 都能投影 applied merge。 |

### P0 Goal Loop / Human Gate Checklist

| 维度 | 状态 | 证据 |
|---|---|---|
| Goal satisfaction | Done, keep testing | `GoalSatisfactionWriter` 记录 `SATISFIED`、`NEEDS_REPAIR`、`NEEDS_REDESIGN` 等领域结果。 |
| Repair loop routing | Done, keep testing | full 和 contract workflow variants 都会把 acceptance `REJECT` 先路由到 `acceptance.classify_failure`，再开始语义 repair iteration。 |
| Runtime retry vs semantic repair | Done, keep testing | runtime recovery 保持同一个 active node；loop transition 会创建带 loop reason 的新语义 iteration。 |
| Human gate action semantics | Done | Ledger 会在需要时把人类动作 `approve` 归一化成 workflow outcome `approved`，CLI/API/UI 不需要各自翻译。 |
| Gate reduction policy | Partial | 真实产品运行仍停在有意义的 gates；benchmark `autorun` 可以自动处理 gates，但 scheduler 不绕过 human decisions。 |
| 测试 | Done | Ledger 和 workflow definition tests 覆盖 classified repair loop、revision limit、final gate completion、terminal guard 和 gate action normalization。 |
| 真实 case 验证 | Pending | 需要跑一个中等复杂度真实 case，确认 rejection 或 evidence 不足会进入 repair，而不是靠手工重跑。 |

### P0 Harness Enforcement Checklist

LLM 可以建议、生成和解释，但主流程不能依赖 LLM “自觉遵守 discipline”。P0 必须把纪律变成 harness 层的硬约束。

| 维度 | 状态 | 证据 / 下一步 |
|---|---|---|
| Terminal guard | Done, keep testing | Runtime 已阻止 completed workstream 继续 resume/retry/approve；继续用 smoke 和 real-case 覆盖。 |
| Human gate action boundary | Done, keep testing | Gate action 由 ledger 归一化，CLI/API/UI 不应再翻译 `approve/approved`。 |
| Recovery typed action | Done, keep testing | `RecoveryAssessment` 按 typed runtime reason 输出明确 operator action；`StatusCommand`、`SummaryCommand` 和 next-action hints 复用 shared attention/recovery read model。 |
| Definition/Contract consistency | Done, keep testing | `ContractRegistryTest` 加载 bundled workflow JSON，校验节点/contract 存在性以及 decision/classifier routing outcomes。 |
| Definition/Handler consistency | Done, keep testing | `AcpxWorkflowHandlerRegistryFactoryTest` 通过集中的 handler capability catalog 解析每个 bundled workflow 节点。 |
| Projection read model | Partial | “跑到哪、为什么停、下一步做什么” 必须来自 shared read model；trace/status/API/UI 不再各自推导。 |
| Real-case boundary loop | Pending | real-case 暴露边界问题后必须进入 Analyze -> Modify -> Review -> Reflect，不允许只补一条字符串规则继续跑。 |

## P1：长任务可靠性

P1 让长时间、多 Agent 的真实运行可恢复、可控成本。

| 项目 | 状态 | 目的 | 设计 |
|---|---|---|---|
| Execution / AgentTask 边界 | Partial | 区分 node instance、具体 execution、agent dispatch facts。 | [execution-instance-model.zh-CN.md](design/execution-instance-model.zh-CN.md) |
| Recovery 分类 | Partial | 区分 runtime retry、semantic repair、验收拒绝、quota/session 外部失败。 | [failure-recovery-and-calibration.zh-CN.md](design/failure-recovery-and-calibration.zh-CN.md) |
| Context budget 治理 | Partial | 防止 context package 膨胀到系统不可用。 | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| Artifact contract 稳定 | Partial | 基于结构化 artifact 和状态路由，而不是猜自然语言正文。 | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| Boundary drift prevention | Partial | 防止 workflow definition、contract registry、handler registry、projection 互相漂移。 | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

### P1 Artifact / Context Checklist

| 维度 | 状态 | 证据 |
|---|---|---|
| Typed artifact contracts | Done, keep testing | `ArtifactContractValidator`、`ContractRegistry` 和 node output contracts 会在路由前校验 Agent sidecar。 |
| Context budgets | Done, keep testing | `ContextProfiles` 定义 profile budgets；`ContextBuildPackageHandler` 执行 token 估算上限，并对 section 降级或丢弃。 |
| Context package metadata | Done | 每个 context package 现在都会写 JSON sidecar，记录 profile、token budget、估算 tokens 和每个 section 的降级事实。 |
| Projection | Partial | API overview 已暴露 latest context package metadata artifact；更丰富的 CLI/UI budget summary 后续再补。 |
| 测试 | Done | `ContextBuildPackageHandlerTest` 覆盖 budget enforcement、degradation 和 metadata sidecar。 |
| 真实 case 验证 | Pending | 需要重跑一个中等复杂度真实 case，确认生成 prompt 不再异常膨胀。 |

## P2：Operator 易用性

P2 让系统足够容易理解，能支撑真实使用和开源评估。

| 项目 | 状态 | 目的 | 设计 |
|---|---|---|---|
| Operator Panel 一致性 | Partial | UI、API、CLI 回答同一个问题：跑到哪、为什么停、下一步做什么。 | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md) |
| Delivery view | Partial | 在 completed workstream 上展示 delivery merge 的准备状态和结果。 | [delivery-merge.md](design/delivery-merge.md) |
| Public benchmark | Partial | 提供不依赖私有项目的可重复证据。 | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) |
| 开源文档清理 | Partial | 文档要当前、可读、不绑定内部运行历史。 | [release-checklist.zh-CN.md](release-checklist.zh-CN.md) |

## 当前下一步

当前先不要继续盲跑真实 case。下一步先把 P0 harness enforcement 的缺口补上，避免每次 real-case 都撞出同类边界问题。

2026-06-17 的中等复杂度 cancer-clinic real-case 验证结果：

- intake prompt 约 6.3KB，未复现 context budget 膨胀。
- `acpx`/Claude stderr 出现 `agent needs reconnect` 后，workflow 已能从 `RUNNING` 收敛到 `WAITING_FOR_RECOVERY`，不再无限挂起。
- 当前真实业务推进阻塞在外部 Claude/acpx session reconnect，系统侧 recovery 分类、CLI next action 和 ledger evidence 已可复盘。

P0-1 Delivery Merge 在当前 MVP 边界内已闭环。它仍然是 completed workstream 之后的显式交付命令，不会重新打开或 resume 已完成 workflow。

恢复外部 Agent 通道后，继续执行：

1. 再重跑中等复杂度 cancer-clinic real-case，观察 context budget、recovery、artifact contract、goal loop 是否协同。
2. 如果这个私有 case 被验收，并且 original repo 明确准备好接收 staged changes，再执行显式 delivery merge 作为私有生产型证据：

```bash
dualctl delivery merge --workdir <source_worktree> --target <original_repo> <workstream_id>
dualctl trace --workdir <source_worktree> <workstream_id>
```

Delivery Merge 仍然是 completed workstream 之后的显式动作，不进入自动 scheduler。
