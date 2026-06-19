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

### P0 Closure Protocol

P0 必须一次性收尾，退出标准固定。Real-case 是验收门，不是无限探索循环。

退出标准：

| 边界 | 必须提供的证据 |
|---|---|
| Terminal guard | completed workstream 拒绝误触发 resume、retry、approve-run，同时允许 trace、summary 和 delivery inspection。 |
| Goal loop | acceptance reject 或 evidence 不足进入有边界的 semantic repair，而不是靠手工重跑。 |
| Runtime recovery | 外部 Agent/acpx 失败进入 typed recovery evidence 和 next action，不被误判为业务 rejection。 |
| Artifact contract | 路由优先消费结构化 artifact 和 sidecar，不猜自然语言正文。 |
| Delivery merge | accepted worktree changes 可以显式 merge 到干净 original repo，且不自动 commit。 |
| Projection | CLI trace/status/summary 和 API/operator overview 对同三个问题回答一致：跑到哪、为什么停、下一步做什么。 |

收尾方式：

1. 跑确定性检查：targeted Maven tests、workflow/contract consistency tests、lifecycle smoke。
2. 跑一次中等复杂度 real case，必须触达业务逻辑，不能只停留在 UI 文案或单页面小改。
3. 所有失败只分两类：
   - `P0 blocker`：收尾前必须修，修完只重跑相关检查和 closure real case。
   - `Post-P0`：记录到 P1/P2，不再重新打开 P0。
4. 写 closure report，包含命令证据、workstream id、final status、artifact 状态、delivery merge 状态和已接受的 Post-P0 项。

当前 closure report 记录在 [p0-closure-report.md](testing/p0-closure-report.md)。P0 现在是 `Done with known limits`：私有业务逻辑 real-case 已到达 `COMPLETED`，最终验收为 `ACCEPT_WITH_NOTES`，过程中完成两次语义 repair loop，original repo 保持干净。之后的 real-case 是回归检查或 P1/P2 验证，不再作为 P0 探索循环。

| 项目 | 状态 | 目的 | 设计 |
|---|---|---|---|
| Delivery merge | Done for MVP | 把验收通过的 worktree diff 安全合回 original repo。 | [delivery-merge.md](design/delivery-merge.md) |
| Goal-driven repair loop | MVP 已完成，继续回归 | Codex reject 或 evidence 不足时进入有限 repair，而不是随手重跑。 | [goal-driven-workstream-loop.zh-CN.md](design/goal-driven-workstream-loop.zh-CN.md) |
| Human gate 降噪 | MVP 可接受，P2 继续优化 | 只在需求、设计、验收、交付这类真正需要判断的位置停。 | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md) |
| Terminal-state guards | Implemented, keep testing | completed workstream 只能观测和显式交付，不能误触发 resume/retry。 | [workflow-state-machine.zh-CN.md](design/workflow-state-machine.zh-CN.md) |
| Harness enforcement | MVP 已完成，继续加固 | 不依赖 LLM 自觉遵守纪律；由 contract、runtime guard、consistency tests 强制边界。 | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

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
| 真实 case 验证 | Done | `ws-20260619T051122Z-P837` 已完成；过程中一次 contract/design repair、一次 implementation/evidence repair，均未靠手工重跑。 |

### P0 Harness Enforcement Checklist

LLM 可以建议、生成和解释，但主流程不能依赖 LLM “自觉遵守 discipline”。P0 必须把纪律变成 harness 层的硬约束。

| 维度 | 状态 | 证据 / 下一步 |
|---|---|---|
| Terminal guard | Done, keep testing | Runtime 已阻止 completed workstream 继续 resume/retry/approve；继续用 smoke 和 real-case 覆盖。 |
| Human gate action boundary | Done, keep testing | Gate action 由 ledger 归一化，CLI/API/UI 不应再翻译 `approve/approved`。 |
| Recovery typed action | Done, keep testing | `RecoveryAssessment` 按 typed runtime reason 输出明确 operator action；`StatusCommand`、`SummaryCommand` 和 next-action hints 复用 shared attention/recovery read model。 |
| Definition/Contract consistency | Done, keep testing | `ContractRegistryTest` 加载 bundled workflow JSON，校验节点/contract 存在性以及 decision/classifier routing outcomes。 |
| Definition/Handler consistency | Done, keep testing | `AcpxWorkflowHandlerRegistryFactoryTest` 通过集中的 handler capability catalog 解析每个 bundled workflow 节点。 |
| Projection read model | CLI/API MVP 已完成，P2 继续优化 UI | `trace`、`status` 和 overview read model 已基于 ledger facts 回答“跑到哪、为什么停、下一步做什么”。 |
| Real-case boundary loop | P0 closure 已验证，继续作为纪律 | closure real-case 产生两次 verifier rejection，workflow 均通过 semantic repair 自动收敛。 |

## P1：长任务可靠性

P1 让长时间、多 Agent 的真实运行可恢复、可控成本。

| 项目 | 状态 | 目的 | 设计 |
|---|---|---|---|
| Execution / AgentTask 边界 | Partial, worker MVP added | 区分 node instance、具体 execution、agent dispatch facts。 | [execution-instance-model.zh-CN.md](design/execution-instance-model.zh-CN.md) |
| Recovery 分类 | MVP 已完成，继续补 provider evidence | 区分 runtime retry、semantic repair、验收拒绝、quota/session 外部失败。 | [failure-recovery-and-calibration.zh-CN.md](design/failure-recovery-and-calibration.zh-CN.md) |
| Context budget 治理 | MVP 已完成，继续度量 | 防止 context package 膨胀到系统不可用。 | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| Artifact contract 稳定 | 当前 MVP 边界 Done, keep testing | 基于 typed sidecar 和编译后的 stage outcome 路由，而不是猜自然语言正文。 | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| Boundary drift prevention | Partial | 防止 workflow definition、contract registry、handler registry、projection 互相漂移。 | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

### P1 Artifact / Context Checklist

| 维度 | 状态 | 证据 |
|---|---|---|
| Typed artifact contracts | Done, keep testing | `ArtifactContractValidator`、`ContractRegistry` 和 node output contracts 会在路由前校验 Agent sidecar。 |
| 中间阶段 contracts | Done, keep testing | Design、plan、implementation 和 Codex review 节点都在 workflow JSON 和 prompt 中声明 `agent_output_contract`。 |
| 决策路由 | Done, keep testing | `ReviewDecisionHandler` 只消费最新 `*.contract.json` sidecar；Markdown 和 `dual-control` 不再作为路由 fallback。 |
| Report 投影 | Done, keep testing | Final report summary 消费 self-test 和 acceptance sidecar，不嵌入原始 Agent Markdown 或 transcript。 |
| Context budgets | Done, keep testing | `ContextProfiles` 定义 profile budgets；`ContextBuildPackageHandler` 执行 token 估算上限，并对 section 降级或丢弃。 |
| Context package metadata | Done | 每个 context package 现在都会写 JSON sidecar，记录 profile、token budget、估算 tokens 和每个 section 的降级事实。 |
| Prompt 语言策略 | Done | Workstream `settings.json` 保存 `promptLanguage`；Agent prompt 会本地化人类可读说明，同时保持 contract keys、enums 和 markers 的机器可读格式。 |
| Projection | Partial | API overview 已暴露 latest context package metadata artifact；更丰富的 CLI/UI budget summary 后续再补。 |
| 测试 | Done | `ContextBuildPackageHandlerTest` 覆盖 budget enforcement、degradation 和 metadata sidecar。 |
| 真实 case 验证 | closure case 已完成 | 2026-06-19 私有业务逻辑 real-case 已完成，未出现 context-window recovery 或 prompt 异常膨胀。 |

### P1 AgentTask / Worker Checklist

| 维度 | 状态 | 证据 |
|---|---|---|
| AgentTask 派工账本 | Done | `queue/agent-tasks/<id>.json` 记录 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`。 |
| Inline 兼容 | Done | 默认 `dualctl run` 仍然内联执行节点，现有 lifecycle 行为不变。 |
| Worker dispatch mode | MVP done | `dualctl run --dispatch-mode WORKER` enqueue 一个 AgentTask，并让节点保持 `RUNNING`。 |
| 本机 worker | MVP done | `dualctl worker run-once --workdir <effective_workdir> <workstream_id>` claim 一个 queued task，创建一个 `NodeExecution`，执行 handler，并通过 ledger routing 推进流程。 |
| 多 worker 平台 | MVP 不做 | 暂不做 lease、heartbeat、分布式 scheduler 或多 task fan-out；只有真实无人值守运行需要时再补。 |
| 测试 | Done | `WorkflowRuntimeTest` 覆盖 queue-only dispatch 和 worker run-once routing。 |

## P2：Operator 易用性

P2 让系统足够容易理解，能支撑真实使用和开源评估。

| 项目 | 状态 | 目的 | 设计 |
|---|---|---|---|
| Operator Console / Management Diagnostics | MVP implemented | 默认任务视图回答“跑到哪、为什么停、下一步做什么”；管理诊断保留 RCA、CLI、raw facts 和 token/cost。默认入口为 `/`，兼容入口为 `/console.html`，旧管理面板迁到 `/mgt/index.html`。 | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md)、[operator-console-implementation-plan.zh-CN.md](design/operator-console-implementation-plan.zh-CN.md) |
| Delivery view | Partial | 在 completed workstream 上展示 delivery merge 的准备状态和结果。 | [delivery-merge.md](design/delivery-merge.md) |
| Public benchmark | Partial | 提供不依赖私有项目的可重复证据。 | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) |
| 开源文档清理 | Partial | 文档要当前、可读、不绑定内部运行历史。 | [release-checklist.zh-CN.md](release-checklist.zh-CN.md) |

## 当前下一步

P0 lifecycle closure 在 MVP 边界内已经完成。不要继续开放式私有 P0 real-case 探索。
下一步是围绕公开可重复 benchmark 和 operator 易用性的 P1/P2 收口。

建议顺序：

1. 用 tracked case registry、稳定默认 case 和脱敏 aggregate index，让 public benchmark 可重复。
2. 实现 Operator Console / Management Diagnostics 双视图：默认任务视图面向最终用户，管理诊断保留旧 UI 的排障能力，并按 RCA 组织证据、命令、验证和预防。
3. 基于 benchmark evidence 继续强化 context/token governance 和 provider metrics。
4. 私有 real case 只作为特定缺陷的 regression check，不再作为 P0 discovery loop。
