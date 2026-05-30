# Agent Workflow Engine Architecture

## 1. 背景

现有 Bash `dual-agent-runner` 证明了 `acpx` 可以作为 Claude / Codex 的统一通讯入口，也证明了 run ledger、stdout/stderr、worktree、报告这些基础机制可行。

但它本质是一个 demo runner，不应该继续约束下一代系统设计。新的 Java 方向不再围绕“固定双 Agent 流水线”扩展，而是把 dual 定义为：

```text
面向 Agent 开发生命周期的 workflow engine
```

它要解决的问题不是“调用两个 Agent”，而是托管一个需求从设计、计划、实现、验收到交付的长期过程，并支持：

- Agent 自动交接。
- 人工 review 卡点。
- 上下文分层装配。
- 失败后暂停和恢复。
- 多轮 decision / revision。
- 后续 API、scheduler、可视化。

## 2. 目标工作场景

这套系统要覆盖的不是一次性 coding task，而是当前人工在 Claude Code 和 Codex / ChatGPT 之间反复 copy/paste 的完整研发节奏。

目标场景如下：

```text
1. 用户输入一个需求
2. Claude 用 Superpowers / brainstorm 做需求澄清和设计
3. Codex 从产品经理、架构师、最终用户视角审视设计
4. Claude 根据 review 修改设计
5. 2-4 多轮循环，直到设计可以进入下一步
6. Codex review 最终设计，并生成下一阶段提示词
7. Claude 根据提示词做计划、实现、自测、交付摘要
8. Codex review Claude 的产出、测试证据和用户验收风险
9. Claude 根据 review 修正
10. 7-9 多轮循环，直到可以进入下一阶段或完成
```

人的角色不是搬运文本，而是处理关键卡点：

- 批准设计进入计划。
- 批准计划进入实现。
- 判断 Codex 的阻塞意见是否接受。
- 在 Agent 上下文爆掉、额度耗尽、工具异常时决定恢复方式。
- 对阶段完成结果做最终业务判断。

因此 workflow engine 的核心能力不是“自动跑完一串命令”，而是：

- 把每一轮 Claude 产出、Codex 审视、人类决定保存成可追溯 artifact。
- 根据结构化 decision 自动进入下一节点。
- 在需要人判断时暂停，而不是继续猜。
- 在 context window、rate limit、session 挂掉之后，可以靠 handoff 和 ledger 恢复。
- 在一个阶段完成后，自动生成下一阶段 prompt 或 work item。

## 3. 端到端生命周期

第一版设计要能表达完整生命周期，即使实现分阶段落地。

### 3.1 Intake / Clarification

目标：把用户的原始需求变成可讨论的产品问题。

典型节点：

- Claude brainstorm。
- Claude 输出 clarification summary。
- Codex 做产品完整性审视。
- Human gate 确认需求边界。

输出 artifact：

- `requirement-brief.md`
- `clarification-summary.md`
- `codex-product-review.md`
- `human-decision.json`

### 3.2 Design Loop

目标：形成可实现的设计，而不是马上写代码。

典型节点：

- Claude architecture / flow design。
- Codex design review。
- Decision: `PASS` / `NEEDS_REVISION` / `BLOCKED`。
- Claude design revision。
- Human gate: approve to planning。

输出 artifact：

- `design.md`
- `architecture-notes.md`
- `codex-design-review.md`
- `design-handoff.json`

### 3.3 Planning Loop

目标：把设计拆成可执行阶段，并生成下一阶段提示词。

典型节点：

- Claude implementation plan。
- Codex plan review。
- Decision: `READY_TO_IMPLEMENT` / `NEEDS_REVISION` / `BLOCKED`。
- Human gate: approve to implementation。

输出 artifact：

- `implementation-plan.md`
- `stage-prompts/<stage-id>.md`
- `codex-plan-review.md`

### 3.4 Implementation Loop

目标：Claude 像 developer 一样实现，并自己决定如何自测。

典型节点：

- Claude implement。
- Claude self-test and evidence report。
- Artifact validation。
- Codex verifier review。
- Decision: `ACCEPT` / `ACCEPT_WITH_NOTES` / `REJECT` / `NEEDS_EVIDENCE`。
- Claude fix or evidence supplement。

关键约束：

- Runner 不强制指定测试命令。
- Claude 必须产出自测报告和证据。
- Codex 判断自测是否足够，并可以要求补证据。
- 自动化测试命令可以作为可选 policy，不是主流程前提。

输出 artifact：

- `implementation-summary.md`
- `self-test-report.md`
- `changed-files.md`
- `verification-evidence.md`
- `codex-acceptance-review.md`

### 3.5 Stage Completion / Next Prompt

目标：阶段完成后不要靠人重新整理上下文。

典型节点：

- Codex final stage review。
- Claude stage handoff。
- Decision: `COMPLETE` / `NEXT_STAGE` / `BLOCKED`。
- Generate next-stage prompt。
- Human gate: approve next stage。

输出 artifact：

- `stage-summary.md`
- `handoff.md`
- `handoff.json` — **已实现（T1.3）**。由 `PromptGenerateNextStageHandler` 从 agent handoff markdown 解析生成，包含 `completedWork`、`changedFiles`、`openRisks`、`verificationState` 等结构化字段。下游 `ContextBuildPackageHandler` 可直接消费，避免重新解析 markdown。
- `next-stage-prompt.md`

### 3.6 Recovery / Continuation

目标：解决 context window 爆掉、模型额度耗尽、工具进程挂掉之后的续跑问题。

典型节点：

- Detect failed / blocked Agent run。
- Build recovery context package。
- Open recovery gate。
- Resume in same or new acpx session。

输出 artifact：

- `recovery-context.md`
- `session-state.json`
- `resume-instruction.md`

## 4. 设计原则

### 4.1 完整设计，分步实现

架构要能覆盖完整生命周期，但第一版实现只落最小闭环。

设计必须预留：

- 多阶段 workflow。
- 多种 Task 类型。
- 多种 Decision 类型。
- 多种 Gate 类型。
- 多 workspace。
- 长时间暂停和恢复。
- 后续 API / scheduler / UI。

第一版不实现：

- 分布式 worker。
- 数据库作为唯一事实源。
- BPMN 引擎。
- 并行节点。
- Timer 节点。
- 自动 merge。
- Web UI。

### 4.2 Engine 不认识具体工具

Workflow engine 只认识三类节点：

```text
Task
Decision
Gate
```

Engine 不直接认识：

- Claude。
- Codex。
- GitHub。
- Shell test。
- PR。
- Notification。

这些都通过 handler 扩展。

### 4.3 文件账本优先

第一版继续使用 `.agent/` 文件账本。

原因：

- worktree 内自包含。
- 人和 Agent 都能直接查看。
- 方便恢复和审计。
- 不引入部署复杂度。
- 后续数据库可以作为索引层，而不是第一事实源。

### 4.4 事件可审计

任何状态变化必须有 event。

`current.json` 只是快照，`events.jsonl` 才是事实流。系统可以为了性能读取快照，但不能只保存快照。

### 4.5 MVP 阶段不承诺旧账本兼容

当前设计仍在快速变化，且尚未进入真实长期运行。MVP 阶段可以破坏性调整 ledger schema。

要求是：

- 新建 workstream 正常。
- 新 schema 测试覆盖。
- README 明确当前不承诺向后兼容。

等模型稳定后再引入 migration。

## 5. 总体分层

```text
CLI / API
  start / status / resume / review

Workflow Runtime
  load instance
  execute current node
  evaluate decision
  open or resolve gate
  persist events and snapshots

Workflow Definition
  node definitions
  edge definitions
  handler types
  input/output contracts

Ledger
  workflow instance
  current snapshot
  node instances
  artifacts
  runs
  events

Handlers
  TaskHandler
  DecisionHandler
  GateHandler

Policy
  naming
  directory layout
  artifact id
  retry/failure defaults
```

## 6. 核心模型

### 6.1 Workstream

`Workstream` 表示一个长期需求或产品主题。

它不等于一次 run，也不等于一个 Git branch。一个 workstream 可以跨多个阶段、多个 iteration，后续也可以扩展出多个 workspace。

### 6.2 Workspace

`Workspace` 表示具体执行环境。

第一版默认是 Git worktree。

后续允许：

- primary workspace。
- experiment workspace。
- hotfix workspace。
- review-only workspace。

### 6.3 WorkflowDefinition

定义流程骨架：

- 有哪些 node。
- node 使用什么 handler。
- node 的输入输出 contract。
- edge 如何连接。
- decision result 如何选择 edge。

它是模板，不记录运行状态。

### 6.4 WorkflowInstance

一次 workstream 对某个 workflow definition 的执行实例。

记录：

- definition id。
- definition version。
- active node ids。
- status。
- current stage / iteration。
- last event id。

### 6.5 NodeDefinition

节点定义。

最小字段：

```json
{
  "id": "codex-review",
  "kind": "task",
  "handler_type": "acpx.agent_run",
  "input_contract": [],
  "output_contract": [],
  "config": {}
}
```

### 6.6 NodeInstance

节点运行实例。

最小字段：

```json
{
  "schema_version": 1,
  "node_instance_id": "node-codex-review-001",
  "node_definition_id": "codex-review",
  "kind": "task",
  "handler_type": "acpx.agent_run",
  "status": "completed",
  "stage_id": "stage-design",
  "iteration_id": "iter-001",
  "input_refs": [],
  "output_refs": [],
  "result": {},
  "run_ref": "run-...",
  "started_at": "...",
  "finished_at": "..."
}
```

### 6.7 Task

Task 负责做事，有副作用。

例子：

- 创建 worktree。
- 生成 context package。
- 调用 Claude。
- 调用 Codex。
- 校验 artifact。
- 创建 PR。
- 运行 shell command。

Task 可能成功、失败、被阻塞。Task 失败后不应偷偷降级，应写入 event 和 node result。

### 6.8 Decision

Decision 负责判断，不应直接执行副作用。

例子：

- Codex decision。
- Artifact validation result。
- Revision limit check。
- Human action decision。
- Test result decision。

Decision 必须保存：

- 输入证据。
- 判断结果。
- 选中的 edge。
- 简短原因。

示例：

```json
{
  "node_instance_id": "node-decision-codex-001",
  "kind": "decision",
  "handler_type": "codex.review_decision",
  "result": {
    "decision": "NEEDS_REVISION",
    "selected_edge": "create-next-iteration",
    "reason": "Codex returned Must Fix items"
  }
}
```

### 6.9 Gate

Gate 负责等待人或外部条件。

例子：

- Human design review。
- Wait for acpx session reconnect。
- Wait for quota recovery。
- Wait for PR checks。

Gate 可以 open、resolved、cancelled。

第一版只实现 human review gate 的 approve / revise。

### 6.10 Edge

Edge 连接 node。

示例：

```json
{
  "from": "codex-decision",
  "on": "PASS",
  "to": "human-design-review"
}
```

Edge 只在 definition 中定义。运行时由 Decision result 或 Task result 选择。

## 7. 执行模型

### 7.1 start

```text
dualctl start
  -> create workstream
  -> create workspace
  -> load workflow definition
  -> create workflow instance
  -> create first node instance
  -> write events
  -> stop with resume instruction
```

第一版可以只创建设计流程的第一个 Task node。

### 7.2 resume

```text
dualctl resume
  -> acquire workstream lock
  -> load current snapshot
  -> load active node
  -> dispatch by node kind
  -> execute handler
  -> write node result
  -> append events
  -> route next node
  -> update current snapshot
```

如果遇到 open Gate，`resume` 不绕过 gate。

### 7.3 Task execution

```text
TaskNode
  -> resolve TaskHandler by handler_type
  -> build TaskContext
  -> execute
  -> persist run/artifacts/result
  -> route by result.status
```

TaskHandler 返回统一结果：

```json
{
  "status": "completed",
  "output_refs": [],
  "event_refs": [],
  "error": null
}
```

### 7.4 Decision routing

```text
DecisionNode
  -> resolve DecisionHandler
  -> evaluate from ledger/artifacts/events
  -> save decision result
  -> select edge
  -> activate next node
```

Decision 不直接调用 Agent。

### 7.5 Gate handling

```text
GateNode
  -> create gate document
  -> status=open
  -> stop runtime
```

人工动作：

```text
dualctl review approve/revise
  -> load open gate
  -> validate action allowed
  -> write human decision event
  -> resolve gate
  -> activate next node
```

## 8. 状态存储

第一版采用：

```text
Event Log + Current Snapshot
```

不是只靠 checkpoint，也不是每次都重放事件。

### 8.1 Ledger layout

```text
.agent/
  workstreams/
    <workstream_id>/
      workflow/
        definition.json
        instance.json
        current.json
      nodes/
        <node_instance_id>.json
      gates/
        <gate_id>.json
      artifacts/
        <artifact_id>.json
      runs/
        <run_id>/
          run.json
          prompts/
          outputs/
      events.jsonl
```

第一版可以不建独立 `tasks/`、`decisions/` 目录。Task、Decision、Gate 都是 NodeInstance。

### 8.2 events.jsonl

事件是事实来源。

示例：

```json
{
  "event_id": "evt-001",
  "ts": "2026-05-23T10:00:00Z",
  "workstream_id": "ws-...",
  "workflow_instance_id": "wf-...",
  "node_instance_id": "node-...",
  "event_type": "node_completed",
  "actor": "orchestrator",
  "payload": {
    "status": "completed",
    "output_refs": []
  }
}
```

### 8.3 current.json

快照用于快速 status/resume。

示例：

```json
{
  "schema_version": 1,
  "workstream_id": "ws-...",
  "workflow_instance_id": "wf-...",
  "definition_version": "design-loop@1",
  "status": "waiting_for_human",
  "active_node_ids": ["node-human-review-001"],
  "open_gate_ids": ["gate-human-review-001"],
  "last_event_id": "evt-123",
  "updated_at": "2026-05-23T10:10:00Z"
}
```

### 8.4 NodeInstance status

建议状态：

```text
created
ready
running
completed
failed
blocked
skipped
waiting
```

第一版不实现 parallel，所以 active node 通常只有一个。

## 9. Handler 扩展机制

### 9.1 TaskHandler

```java
public interface TaskHandler {
    String type();
    TaskResult execute(TaskContext context);
}
```

第一版 TaskHandler：

- `acpx.agent_run`
- `artifact.validate`
- `workflow.create_next_iteration`
- `context.build_package`

后续可加：

- `git.worktree_create`
- `shell.run_command`
- `github.create_pr`
- `notification.command`

新增 Task 类型不修改 engine，只注册 handler。

### 9.2 DecisionHandler

```java
public interface DecisionHandler {
    String type();
    DecisionResult evaluate(DecisionContext context);
}
```

第一版 DecisionHandler：

- `codex.review_decision`
- `artifact.validation_decision`
- `human.action_decision`
- `revision.limit_check`

### 9.3 GateHandler

```java
public interface GateHandler {
    String type();
    GateResult openOrInspect(GateContext context);
}
```

第一版 GateHandler：

- `human.review`

后续可加：

- `acpx.session_reconnect`
- `quota.wait`
- `github.pr_checks`

### 9.4 HandlerRegistry

Runtime 通过 `handler_type` 解析 handler。

缺失 handler 是配置错误，必须 fail fast。

## 10. Workflow Definition

第一版定义不做万能 DSL，只表达 MVP 需要的节点和边。

### 10.1 Design Loop Example

示例：

```json
{
  "schema_version": 1,
  "workflow_id": "design-loop",
  "version": 1,
  "entry_node": "claude-design",
  "nodes": [
    {
      "id": "claude-design",
      "kind": "task",
      "handler_type": "acpx.agent_run",
      "config": {
        "agent": "claude",
        "session": "claude-main",
        "prompt_template": "claude-design"
      }
    },
    {
      "id": "validate-claude-handoff",
      "kind": "task",
      "handler_type": "artifact.validate"
    },
    {
      "id": "codex-review",
      "kind": "task",
      "handler_type": "acpx.agent_run",
      "config": {
        "agent": "codex",
        "session": "codex-reviewer",
        "prompt_template": "codex-design-review"
      }
    },
    {
      "id": "codex-decision",
      "kind": "decision",
      "handler_type": "codex.review_decision"
    },
    {
      "id": "human-design-review",
      "kind": "gate",
      "handler_type": "human.review"
    }
  ],
  "edges": [
    {
      "from": "claude-design",
      "on": "completed",
      "to": "validate-claude-handoff"
    },
    {
      "from": "validate-claude-handoff",
      "on": "completed",
      "to": "codex-review"
    },
    {
      "from": "codex-review",
      "on": "completed",
      "to": "codex-decision"
    },
    {
      "from": "codex-decision",
      "on": "PASS",
      "to": "human-design-review"
    },
    {
      "from": "codex-decision",
      "on": "NEEDS_REVISION",
      "to": "create-next-iteration"
    },
    {
      "from": "codex-decision",
      "on": "BLOCKED",
      "to": "human-intervention"
    }
  ]
}
```

### 10.2 Lifecycle Workflow Skeleton

完整生命周期不要求第一版一次实现所有 handler，但 workflow model 必须能表达这条主干：

```text
intake.claude_brainstorm
  -> intake.codex_product_review
  -> intake.decision
  -> gate.human_requirement_review

design.claude_design
  -> design.codex_arch_review
  -> design.decision
  -> design.revision_or_gate
  -> gate.human_design_approval

plan.claude_plan
  -> plan.codex_plan_review
  -> plan.decision
  -> gate.human_plan_approval

implement.claude_implement
  -> implement.claude_self_test_report
  -> implement.artifact_validation
  -> acceptance.codex_verifier_review
  -> acceptance.decision
  -> implement.fix_or_evidence_supplement
  -> gate.human_acceptance

stage.codex_final_review
  -> stage.claude_handoff
  -> stage.generate_next_prompt
  -> gate.human_next_stage_approval
```

这条主干里的每个阶段都允许多轮 revision。revision 不是写死在 Java 分支里，而是由 workflow edge + decision result 驱动。

第一版可以只实现其中一条窄路径，但不能把模型设计成只能表达这条窄路径。

### 10.3 Human Gate Placement

默认人类卡点：

1. 需求边界确认。
2. 设计进入计划。
3. 计划进入实现。
4. 验收进入下一阶段。
5. 阻塞状态恢复。

这些 gate 不是为了让人继续搬运上下文，而是让人做真正需要判断的决定。

### 10.4 Agent Handoff Placement

每次 Agent 交接都必须有 handoff artifact。

最低要求：

- Markdown 给人读。
- JSON sidecar 给 orchestrator 校验和路由。

典型 handoff：

- Claude design handoff。
- Codex review handoff。
- Claude implementation handoff。
- Claude self-test handoff。
- Codex acceptance handoff。
- Stage completion handoff。

## 11. Policy

Policy 控制命名和默认规则，不承载复杂流程。

示例：

```json
{
  "schema_version": 1,
  "ledger": {
    "iteration": {
      "id_prefix": "iter",
      "start_number": 1,
      "number_width": 3,
      "directory_strategy": "padded_number",
      "artifact_suffix_strategy": "padded_number"
    },
    "stages": {
      "design": {
        "stage_id": "stage-design",
        "directory": "01-design",
        "initial_iteration": 1
      }
    },
    "artifacts": {
      "id_pattern": "artifact-{type}-{iteration_suffix}"
    }
  }
}
```

第一版只读内置默认 policy。暂不支持仓库覆盖。

## 12. Artifact Contract

Artifact 是 Agent 之间交接的权威材料。

Agent stdout 只用于：

- 小型 manifest。
- 简短状态。
- 错误输出。

长文档必须写文件。

规则：

1. Prompt 明确给出输出路径。
2. Agent 必须写文件。
3. Agent stdout 必须包含 marker-delimited manifest。
4. Runtime 校验文件存在和 JSON control schema。
5. 校验失败不推进 workflow。

## 13. Failure Handling

### 13.1 可恢复失败

可恢复失败包括：

- Agent 非 0 退出。
- acpx session missing。
- acpx session needs reconnect。
- quota exhausted。
- context window exceeded。
- artifact validation failed。

处理：

- 保存 stdout/stderr。
- 写 node status failed 或 blocked。
- 写 event。
- 打开 gate 或等待人工处理。

### 13.2 不可恢复失败

不可恢复失败包括：

- ledger 写入失败。
- workflow definition 无效。
- handler 不存在。
- current snapshot 损坏。

处理：

- fail fast。
- 不继续执行后续节点。

### 13.3 幂等性

Task 是否可重复执行必须显式建模。

第一版规则：

- `resume` 看到 running node 时不自动重复执行。
- acpx agent run 一旦启动并写入 run ledger，再次 resume 必须检查 run result。
- 不确定是否完成的 task 进入 human intervention，不盲目重跑。

## 14. 并发与锁

同一个 workstream 的 mutating command 必须持有本地 exclusive lock。

范围：

- start。
- resume。
- review approve/revise。

读操作如 status 不需要锁。

第一版仍使用本地 `FileChannel.tryLock()`。不做跨机器锁。

## 15. Context Package

**已实现（T1.3）。** Context package 通过 governed rendering pipeline 生成，每个 profile 有独立的 budget、section 优先级和 degradation 策略。

设计原则：

- 不把全部历史塞进 prompt。
- 当前 node 只拿需要的 artifact refs。
- 长历史通过 degradation 策略降级（SUMMARY / LATEST / DECISION_ONLY / DIFF_STAT / POINTER_ONLY），不是 substring 截断。
- context package 本身也是 artifact。
- 超出 total budget 时从最低 priority section 开始降级或 DROP。
- requirement section 不参与降级，超出硬上限直接 fail。

Governed pipeline 6 阶段：

1. 加载 profile → ContextBudget（通过 `ContextProfiles` 注册表）
2. 检查每个 section 数据可用性 → MissingPolicy（FAIL / SKIP / PLACEHOLDER）
3. 按 priority 排序分配 softBudget → OverflowPolicy（FAIL / DEGRADE / DROP_OPTIONAL）
4. 按 degradation 策略渲染每个 section
5. 验证总 budget，超标则从最低 priority 降级或 DROP
6. 输出 Context Governance summary table

6 个 profile budget：

| Profile | Budget | 特点 |
| --- | --- | --- |
| product_review | 16k | 轻量，需求+输出 |
| architecture_review | 20k | +human_feedback |
| plan_review | 22k | +iteration_history |
| implementation | 24k | 最严格，全部 section |
| acceptance | 26k | git_evidence HIGH priority |
| full (default) | 30k | 宽松，全部 FULL |

Context package 同时消费 `handoff.json` sidecar：当 git_evidence 的 DIFF_STAT 渲染需要 changed files 但 `changed-files.txt` 不存在时，直接从 handoff.json 的 `changedFiles` 数组读取，避免重新解析 markdown。

Schema 类型：`Degradation`, `DegradationPolicy`, `MissingPolicy`, `OverflowPolicy`, `Priority`, `SectionRequirement`, `ContextBudget`。Profile 注册表：`ContextProfiles`。

详见：`dual-orchestrator/docs/superpowers/specs/2026-05-27-T1.3-context-governance-handoff-design.md`

## 16. 模块设计

建议模块：

```text
dual-core
  domain model
  workflow definition model
  handler SPI

dual-runtime
  WorkflowRuntime
  HandlerRegistry
  Router

dual-ledger-file
  file persistence
  atomic write
  event log
  lock

dual-adapter-acpx
  acpx TaskHandler

dual-adapter-git
  git/worktree handlers

dual-cli
  start/status/resume/review
```

后续可加：

```text
dual-api
dual-scheduler
dual-adapter-github
dual-ui
```

Spring Boot 用于 CLI/API 基础。Spring Modulith 可用于模块边界和事件组织。不要用 Spring Statemachine 或 Spring Batch 作为核心 workflow engine。

## 17. 实现路线

实现路线按“先有一条完整主干，再把每个阶段做深”的原则推进。

这里的完整主干不是功能完备，而是从需求进入、设计审视、人类批准、计划、实现、自测、验收、下一阶段提示词，至少每个阶段都有对应节点、artifact 和 gate。这样第一版就不会退化成只能跑 design review 的局部工具。

### Phase 1: Workflow Engine Skeleton

目标：

- 新增 `dual-runtime`。
- 定义 Task / Decision / Gate / Edge。
- 定义 Handler SPI。
- 文件 ledger 支持 workflow definition、instance、current、nodes、events。
- 支持一个最小 workflow 从 Task 跑到 Decision，再停在 Human Gate。
- 不绑定具体生命周期阶段。

不做：

- 完整 Agent prompt。
- 多轮 revision。
- PR / notification。
- Web UI。

### Phase 2: Thin Lifecycle Mainline

目标：

- 用 workflow definition 串起一条薄的端到端主干。
- 包含 intake、design、plan、implementation、acceptance、stage completion。
- 每个阶段先只实现一个最小 happy path。
- 每个阶段都有 artifact contract。
- 每个阶段至少有一个 human gate。
- 把当前 Java MVP 中临时的 `pending_action` 表达收敛到 node instance。
- 不保留旧 checkpoint 兼容。

不做：

- PR。
- notification。
- dashboard。
- 自动多 workspace 编排。

### Phase 3: Review / Revision Loops

目标：

- 需求澄清多轮 review。
- 设计多轮 review。
- 计划多轮 review。
- 实现后补证据 / 修正多轮 review。
- revision limit decision。
- invalid JSON repair gate。

### Phase 4: Context Package And Recovery

目标：

- context package artifact。
- handoff.md + handoff.json。
- context window exceeded 恢复。
- quota exhausted 恢复。
- acpx session reconnect gate。

### Phase 5: API And Scheduler

目标：

- 本地 API server。
- scheduler 扫描 open gate / waiting task。
- 支持 quota 恢复后继续。

### Phase 6: Delivery Integrations

目标：

- PR。
- notification。
- dashboard。

## 18. 非目标

明确不做：

- BPMN 兼容。
- 分布式任务队列。
- 数据库优先。
- Web UI 优先。
- Bash dual ledger 兼容。
- 自动 merge。
- 让 Codex 修改业务代码。
- 让 workflow JSON 承担复杂业务大脑。

## 19. 验收标准

架构设计验收应满足：

1. 能表达从需求澄清、设计、计划、实现、自测、验收到下一阶段提示词生成的完整主干。
2. 能表达 Claude / Codex 多轮交接，而不是只跑一次 Agent。
3. 能表达人类 review gate，并且 gate 是流程的一等节点。
4. 能表达 context window / quota / session 异常后的暂停与恢复。
5. 能表达 Claude 自测报告由 Codex 独立审视，而不是 runner 强制测试命令。
6. 能表达阶段完成后的 handoff 和 next-stage prompt。
7. 旧 Bash runner 不作为兼容约束。

第一阶段 engine skeleton 实现完成后应满足：

1. 新建 workstream 会创建 workflow instance。
2. `resume` 根据 active node 执行 Task / Decision / Gate。
3. 新增 TaskHandler 不需要修改 engine。
4. events.jsonl 可以复盘流程。
5. current.json 可以快速显示状态。
6. human gate 会暂停 workflow。
7. Agent run stdout/stderr 和 artifact 都有 ledger 记录。
8. 测试覆盖 engine routing、handler registry、ledger write、gate resolution。
