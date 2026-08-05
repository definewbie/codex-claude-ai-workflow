# dual-orchestrator 架构与执行流程

> Status: current architecture guide
> Last updated: 2026-06-19

`dual-orchestrator` 是一个本地 AI engineering harness。它通过 `acpx`
驱动 Claude 和 Codex，把一个研发任务放进可追踪、可暂停、可恢复的
workstream 中执行。

本文只说明当前架构和执行流程。设计演进故事见
`docs/design/design-evolution-notes.zh-CN.md`。

## 1. 系统边界

`dual-orchestrator` 不直接替代 IDE、CI 或 GitHub。它负责把 Agent 工作组织成一个
可审计的生命周期：

```text
user task
  -> workstream
  -> workflow runtime
  -> agent runs
  -> typed artifacts
  -> human gates
  -> final report / delivery artifacts
```

角色边界：

| 角色 | 职责 | 边界 |
| --- | --- | --- |
| Claude | developer / architect，负责需求澄清、设计、计划、实现、自测、handoff | 可以修改目标仓库代码 |
| Codex | reviewer / verifier，负责产品、架构、计划和验收视角审视 | 不直接修改目标仓库业务代码 |
| Human | gate owner，负责关键节点 approve、revise、complete 或 stop | 保留最终责任 |
| Orchestrator | 状态机、账本、上下文、契约、恢复和投影 | 不猜自然语言结论 |

## 2. 核心模型

### Workstream

一个 workstream 表示一次完整任务生命周期。默认会在 Git worktree 中运行，避免污染原仓库。

### Workflow

workflow 是 workstream 内的执行图，由 `WorkflowDefinition`、`NodeInstance`、
`EdgeDefinition` 和 `WorkflowCurrent` 组成。

runtime 只识别三类节点：

| Node kind | 作用 | 例子 |
| --- | --- | --- |
| `TASK` | 执行有副作用的工作 | `acpx.agent_run`, `context.build_package`, `report.generate_final` |
| `DECISION` | 读取结构化结果并选择路由 | `review.decision`, `acceptance.decision` |
| `GATE` | 暂停等待人或外部动作 | `human.review` |

### Ledger

ledger 是系统事实源，位于：

```text
.agent/workstreams/<workstream_id>/
```

关键文件：

```text
workflow/current.json       当前 workflow 状态
workflow/definition.json    workflow 定义
workflow/instance.json      workflow 实例元数据
nodes/*.json                每个节点实例状态
gates/*.json                human / recovery gate 状态
iterations/*.json           修订和修复迭代
events.jsonl                append-only 审计事件
runs/*/run.json             Agent run metadata
timeline.json               timeline read model
metrics/workstream-metrics.json
```

CLI、API、Operator Panel 和报告都只能读取或投影 ledger，不应各自发明状态。

### Workflow Definition Source

默认 full lifecycle 的定义来源是 classpath JSON：

```text
dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json
```

`WorkflowDefinitionLoader` 是内置定义入口。启动 workstream 时，loader 会把当前 definition 固化到 ledger 的 `workflow/definition.json`，后续恢复、追踪和复盘都以 ledger 中的副本为准。内置 `full`、`contract` 和 `terminal-delivery` variant 都从 classpath JSON resource 加载，Java 不再手写 lifecycle DAG。`full` 仍是默认值；`terminal-delivery` 由用户显式选择，并在最终验收后直接完成。

### Agent Permission Boundary

不是所有 Agent step 都允许改代码。默认 full workflow 中：

- intake / design / plan / review / acceptance / handoff 阶段是只读 agent run；
- 只有 `implement.claude_implement` 和 `implement.claude_self_test_report` 会启用写权限；
- token / cost 作为 acpx adapter 的观测事实写入 `runs/*/run.json`，不参与业务路由。

### Typed Artifacts

Agent 输出分成两层：

| 层 | 读者 | runtime 作用 |
| --- | --- | --- |
| Markdown report | 人 | 解释推理、取舍和证据 |
| typed artifact | orchestrator / downstream Agent | 提供机器可验证事实 |

关键 artifact：

| Artifact | 生产者 | 用途 |
| --- | --- | --- |
| `task_contract_proposal` | Claude intake | 提出范围、非目标、约束、验收标准 |
| `task-contract.json` | orchestrator | canonical task contract |
| `self_test_report` | Claude self-test | 记录自测方法、证据、缺口和风险 |
| `acceptance_review` | Codex verifier | 记录验收结论、must-fix、notes 和 confidence |
| `stage_handoff` | Claude handoff | 记录完成内容、开放风险和下一阶段建议 |

orchestrator 不从 Markdown 标题、summary keyword 或自然语言段落中推断关键状态。

## 3. 模块架构

```text
dual-cli / dual-server
        |
        v
dual-runtime
        |
        +-- handler SPI
        +-- context package
        +-- artifact contract validation
        |
        v
dual-ledger-file
        |
        +-- workflow/current.json
        +-- nodes / gates / iterations / events
        +-- timeline / metrics projections
        |
        v
dual-core
```

模块职责：

| 模块 | 职责 |
| --- | --- |
| `dual-core` | 领域类型：workflow、node、edge、contract、workspace、flow kernel interface |
| `dual-ledger-file` | 文件账本、路由、node/gate/iteration/event/projection 写入 |
| `dual-runtime` | 执行 active node，调用 handler，校验 artifact，推进 ledger |
| `dual-adapter-acpx` | 调 Claude/Codex，渲染 prompt，处理 acpx 输出和失败分类 |
| `dual-adapter-git` | Git worktree 和 repo 操作 |
| `dual-flow-liteflow` | LiteFlow routing kernel |
| `dual-cli` | `dualctl` 命令行入口 |
| `dual-server` | Operator Panel API、scheduler、SSE overview stream |
| `scripts` | install、smoke、real-case、public benchmark |

## 4. 主生命周期

当前 full lifecycle：

```text
Claude intake / brainstorm
  -> Codex product review
  -> human requirement gate
  -> contract.compile
  -> contract review gate
  -> Claude design
  -> Codex architecture review
  -> human design gate
  -> Claude plan
  -> Codex plan review
  -> human plan gate
  -> Claude implementation
  -> Claude self-test report
  -> artifact validation / Git evidence
  -> Codex acceptance review
  -> failure classification when needed
  -> final report
  -> delivery.prepare_pr
  -> human acceptance gate
  -> Claude handoff
  -> next-stage prompt
  -> human next-stage gate
  -> completed
```

主要分支：

| 结果 | 路由 |
| --- | --- |
| `PASS` / `ACCEPT` | 继续后续阶段 |
| `NEEDS_REVISION` | 回到上游对应阶段，创建新 iteration |
| `REJECT` | 进入 failure classification，再路由到实现、契约或上下文修复 |
| runtime failure | 进入 recovery gate，可手动 retry 或由安全策略自动恢复 |
| human revise | 回到上游对应阶段，创建新 iteration |
| human complete | 在终点 gate 完成 workflow |

## 5. 执行流程

### 5.1 Start

`dualctl start` 创建 workstream、worktree、ledger 和第一个 active node。

```text
dualctl start
  -> create worktree
  -> write workstream metadata
  -> write workflow/definition.json
  -> write workflow/current.json
  -> write nodes/<entry>.json as READY
  -> append workflow_started event
```

`start --run` 会继续执行，直到第一个 human gate、recovery、completion 或 step limit。

### 5.2 Resume / Run

`dualctl resume` 执行当前 active node：

```text
load workflow/current.json
  -> load active node
  -> guard RUNNING / FAILED / WAITING status
  -> mark node RUNNING with current PID
  -> call handler
  -> validate required artifacts
  -> write node result
  -> route edge
  -> refresh timeline / metrics projections
```

`dualctl run --max-steps N` 重复调用 resume，直到流程停住。

### 5.3 Human Gate

human gate 会生成 review packet：

```text
reviews/<gate_node_instance_id>.md
reviews/<gate_node_instance_id>.json
```

操作命令：

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl review approve --run --workdir <effective_workdir> <workstream_id>
dualctl review revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl review complete --workdir <effective_workdir> <workstream_id>
```

human gate 是显式卡点。scheduler 和 autorun 不能在真实交付语义下绕过人的责任。

### 5.4 Recovery

失败节点不会被替换。recovery 会保留同一个 active node：

```text
node FAILED / BLOCKED
  -> workflow WAITING_FOR_RECOVERY
  -> recovery/current-failure.md
  -> dualctl retry
  -> same node READY
  -> resume again
```

runtime recovery 和 semantic repair 是两件事：

| 类型 | 例子 | 行为 |
| --- | --- | --- |
| runtime recovery | quota、session、reconnect、context window、timeout | 可 retry，同一 node 再尝试 |
| semantic repair | 实现不满足契约、契约缺口、review noise | 进入分类和修复路由，通常创建新 iteration |

### 5.5 Scheduler

Scheduler 只读取 ledger 并调用已有 action。它不是第二套状态机。

默认策略：

- `RUNNING`：可继续推进。
- `WAITING_FOR_HUMAN`：停住。
- `WAITING_FOR_RECOVERY`：默认停住。
- `COMPLETED` / `FAILED` / `BLOCKED`：不推进。

开启 `autoRecover` 时，也只自动处理明确可恢复的 runtime failure。

### 5.6 Operator Panel

Operator Panel 通过 `dual-server` 读取同一套 ledger read model：

```text
GET /api/workstreams
GET /api/workstreams/{id}/overview
GET /api/workstreams/{id}/status
GET /api/workstreams/{id}/timeline
GET /api/workstreams/{id}/review/current
GET /api/workstreams/{id}/recovery/current
GET /api/workstreams/{id}/stream
```

SSE stream 只推送 overview，不拥有 workflow 状态。浏览器刷新、断开连接和 server shutdown
都应由 `WorkstreamStreamService` 负责清理。

## 6. Context Package

关键 Agent step 前会通过 `context.build_package` 生成上下文包。

Context package 按 profile 装配：

| Profile | 用途 |
| --- | --- |
| `product_review` | Codex 产品需求审视 |
| `architecture_review` | Codex 架构审视 |
| `plan_review` | Codex 计划审视 |
| `implementation` | Claude 实现 |
| `acceptance` | Codex 验收 |
| `handoff` | Claude handoff |

每个 section 有优先级和 token budget。超出预算时按策略降级，比如 summary、decision only、diff stat 或 pointer only。

## 7. Delivery

Delivery 当前保持保守：

```text
final report
  -> delivery.prepare_pr
  -> PR body
  -> commit message
  -> optional create-pr
```

系统准备交付证据和材料，但不会自动 merge。

## 8. 关键不变量

- ledger 是事实源；CLI、API、UI、报告都是投影。
- workflow terminal state 不允许继续自动推进。
- retry 不创建新 iteration；revision / repair 才创建新 iteration。
- Agent Markdown 不是机器状态。
- typed artifact 缺失或非法时，节点失败并进入 contract diagnostics。
- human gate 必须展示证据和后果，不能只提供按钮。
- scheduler 不通过 human gate，不绕过 artifact contract failure。
- 长生命周期资源必须有 owner：agent process、SSE stream、scheduler、lock、worktree。

## 9. P0 收口状态

P0 主生命周期状态是 `Done with known limits`。当前已经验证：

- workstream 默认在安全 worktree 中运行，原始仓库保持干净；
- Claude/Codex 输出先进入 typed artifact，再由 orchestrator 路由；
- Codex `REJECT` 能进入 semantic repair loop，而不是要求人工重开 workstream；
- runtime reconnect、session、quota 等问题通过 recovery 边界处理，不伪装成业务拒绝；
- `trace`、summary、Operator Panel 使用同一类 ledger read model 回答状态问题；
- completed workflow 只能做观察、summary、delivery merge 和 cleanup，不应继续推进主线节点。

P0 接受的已知限制：

- final acceptance 可以是 `ACCEPT_WITH_NOTES`，尤其是在缺少 live app smoke 证据时；
- `full` lifecycle 的 human gates 仍偏多，体验优化属于后续产品工作；
- context/token 成本已可观测，但治理还要继续收敛；
- delivery merge 是显式命令，不自动 commit、push、merge；
- public benchmark 是可复用验证入口，复杂私有业务 case 仍是维护者本地证据。

## 10. 改动入口

常见改动应该从这些文件开始读：

| 改动类型 | 入口 |
| --- | --- |
| workflow 状态和路由 | `dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java`, `dual-ledger-file/src/main/java/dev/dual/ledger/file/RoutingEngine.java` |
| ledger 写入和 projection | `dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowFileLedger.java`, `TimelineProjectionWriter.java`, `MetricsProjectionWriter.java` |
| artifact contract | `dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactContractValidator.java`, `ContractRegistry.java` |
| context package | `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextBuildPackageHandler.java`, `ContextPackageSectionRenderer.java` |
| acpx 调用和 prompt | `dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/`, `dual-adapter-acpx/src/main/resources/dev/dual/adapter/acpx/prompts/` |
| CLI 状态观察 | `dual-cli/src/main/java/dev/dual/cli/TraceCommand.java`, `StatusCommand.java`, `SummaryCommand.java` |
| Operator Panel | `dual-server/src/main/java/dev/dual/server/WorkstreamQueryService.java`, `WorkstreamApiController.java`, `src/main/resources/static/app.js` |
| scheduler / recovery | `dual-server/src/main/java/dev/dual/server/SchedulerService.java`, `SchedulerPolicy.java`, `dual-ledger-file/src/main/java/dev/dual/ledger/file/RecoveryAssessment.java` |

修改 workflow、artifact、human gate、recovery、delivery 或 projection 前，先阅读
`docs/engineering-checklist.md`。

## 11. 相关文档

- `docs/design/workstream-domain-model.md`
- `docs/design/goal-driven-workstream-loop.zh-CN.md`
- `docs/design/execution-instance-model.zh-CN.md`
- `docs/design/workflow-state-machine.md`
- `docs/design/artifact-contracts-and-context.md`
- `docs/design/failure-recovery-and-calibration.md`
- `docs/design/operator-observability.md`
- `docs/troubleshooting.zh-CN.md`
- `docs/testing/p0-closure-report.md`
- `docs/design/design-evolution-notes.zh-CN.md`
- `docs/engineering-checklist.md`
