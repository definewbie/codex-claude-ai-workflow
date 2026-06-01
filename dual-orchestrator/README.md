# dual-orchestrator

`dual-orchestrator` 是 `codex-claude-ai-workflow` 的 Java 版 Agent 生命周期编排器原型。

Bash 版 `dual-agent-runner` 证明了 `acpx`、worktree、run ledger、报告这些基础机制可行；Java 版不再围绕固定双 Agent 脚本扩展，而是验证一个更长期的工作流模型：

```text
需求澄清 -> 设计审视 -> 人工卡点 -> 计划 -> 实现 -> 自测 -> 验收 -> 下一阶段提示词
```

当前仍是 MVP。它已经有 workflow runtime 骨架和一条薄的 lifecycle mainline，但还不是完整自动化产品。

## Workflow Engine Model

`dualctl` 运行的是 Agent lifecycle workflow，不是单次命令脚本。

Runtime 只认识三类节点：

- `Task`：执行有副作用的工作，例如调用 Claude、调用 Codex、生成 artifact。
- `Decision`：读取 artifact 或结果，选择下一条 edge。
- `Gate`：暂停等待人或外部条件。

当前内置 workflow 覆盖：

1. Intake / clarification
2. Design review
3. Planning
4. Implementation and Claude self-test report
5. Codex acceptance review
6. Final report
7. Stage handoff and next-stage prompt

第一版仍是薄 mainline，但几个关键节点已经开始落地：artifact validation 会采集 Git evidence，final report 会把验收、研发自测和 Git evidence 收口成报告，next-stage prompt 会根据 Claude handoff 生成下一阶段提示词，`acpx.agent_run` 能通过 `acpx` 调 Claude / Codex，decision handler 能解析 Agent 输出里的 `dual-control` block。

## 当前能力

已经实现：

- Maven 多模块工程。
- `dualctl` CLI。
- 默认创建 Git worktree。
- `dual-runtime` workflow runtime。
- `Task` / `Decision` / `Gate` / `Edge` domain model。
- `WorkflowFileLedger` 文件账本。
- `events.jsonl` 审计事件流。
- `workflow/current.json` 快照。
- node / gate 文件化状态。
- handler SPI 和 registry。
- thin lifecycle workflow definition。
- `acpx.agent_run` TaskHandler。
- resources-based Agent prompt templates。
- human review gate。
- review packet artifact：human gate 会生成 `reviews/<gate_node_instance_id>.md` / `.json`。
- `dualctl review show`：展示当前人工卡点需要看的 review packet。
- Timeline projection：ledger 自动生成 `timeline.json`，`dualctl timeline` 可展示 workflow / node 进度。
- review decision handler。
- context package handler，会在关键 Agent step 前装配可读上下文。
- artifact validation handler，会落盘 Git status / diff / changed files。
- next-stage prompt handler。
- `dualctl start/status/resume/review approve/review revise` 接入 workflow ledger。
- 节点级幂等保护（T0.2），`resume` 会拒绝正在执行中的节点。
- Prompt 注入防护：`dual-control` block 解析锚定行首，只认 terminal block，不回退旧文件。
- Revision loop 上限 + recovery gate。
- 单元测试覆盖 handler、ledger、control block 解析、contract registry、prompt 一致性、context governance 和 workflow 集成。
- Agent artifact contract registry（`ContractRegistry`），per-node output schema 校验。
- Prompt 模板变量加载时校验，防止引用未知变量。
- Context governance 架构：6 profile 各自拥有 **token-level** budget-controlled 渲染管线，priority-based degradation（7 种策略含 ADAPTIVE 自适应压缩），token-aware budget enforcement。per-profile config overrides（`max_snippet_chars` 等）。
- `TokenEstimator` SPI：默认 ~4 chars/token 估算，可替换为 tiktoken 等真实 tokenizer。
- `handoff.json` sidecar：从 agent handoff markdown 解析生成结构化 JSON，下游 handler 可直接消费 `completedWork`（iteration_history section）和 `openRisks`（human_feedback section）。
- Configurable retry policies：per-failure-type 策略（RATE_LIMIT / SESSION_MISSING / AGENT_RECONNECT / CONTEXT_WINDOW），指数退避 + `Thread.sleep`，node-level config override。
- `WorkflowFileLedger` 重构为 facade + 4 delegates（`RoutingEngine`、`NodeStateWriter`、`RevisionTracker`、`LedgerEventLog`）+ `LedgerUtils` 工具类。公共 API 不变。
- Delivery 管线：`delivery.prepare_pr` → `dualctl delivery commit` → `dualctl delivery create-pr`（自动 commit + push + `gh pr create`）。
- CalibrationCandidate MVP：failure classification 可生成 `calibration/candidates/<id>.json` 和 `.md`。
- Failure summary MVP：节点 `FAILED` / `BLOCKED` 或分类失败时会生成 `recovery/current-failure.md`。
- Watch mode MVP：`dualctl watch` 基于 `timeline.json` 轮询展示 workflow 进度。
- 条件 edge routing：`EdgeCondition` 接口 + `ConfigEqualsCondition` 实现，edge 支持条件判断（`if config.x == y`），`EdgeConditionModule` Jackson 序列化。
- 并行节点：fan-out edge routing 激活多个后续节点 + sequential multi-node resume（单线程语义，一次 `resume` 依次执行所有 active nodes）。
- Ledger schema migration 暂不作为当前主线；schema 仍在快速演进，当前只保留最小兼容能力。

仍未完成：

- deterministic p6 reject fixture：真实 acpx smoke 保持 `PASS-WARN`，不为了边界分支引入 fake acpx。
- 实时推送：还没有 SSE / file watch / WebSocket，当前 `dualctl watch` 是轮询式 CLI。
- API server / scheduler。
- Web UI / dashboard。
- Notification 集成。
- 正式安装脚本。

## Modules

```text
dual-core
  domain model
  workflow model

dual-ledger-file
  WorkflowFileLedger (facade)
  RoutingEngine · NodeStateWriter · RevisionTracker · LedgerEventLog · LedgerUtils
  workflow/current/nodes/gates/events
  local lock

dual-runtime
  WorkflowRuntime
  HandlerRegistry
  built-in handlers
  context governance pipeline
  handoff.json sidecar
  lifecycle workflow definition

dual-adapter-acpx
  acpx transport
  acpx.agent_run TaskHandler
  Agent prompt templates

dual-adapter-git
  git repo and worktree support

dual-cli
  dualctl start/status/resume/review
```

## Dependencies

本地需要：

- JDK 21+
- Maven 3.9+
- Git
- acpx
- Claude Code / Codex 对应的 acpx agent runtime 可用

检查：

```bash
java -version
mvn -version
git --version
acpx --help
```

## Build

```bash
cd dual-orchestrator
mvn test
mvn -pl dual-cli -am package
```

CLI jar：

```text
dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar
```

临时 alias：

```bash
alias dualctl='java -jar /absolute/path/to/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar'
```

建议先确认 CLI 本身能启动：

```bash
dualctl --help
dualctl start --help
dualctl resume --help
dualctl review approve --help
dualctl review show --help
dualctl timeline --help
```

## acpx Sessions

Agent 调用统一走 acpx：

```bash
acpx claude -s claude-main -f <prompt_file>
acpx codex -s codex-reviewer -f <prompt_file>
```

这里的 `<prompt_file>` 不是你手写的需求文件，而是 `dualctl resume` 在 run ledger 里生成的 Agent 指令文件，例如：

```text
<worktree>/.agent/workstreams/<workstream_id>/runs/<run_id>/prompts/design-claude.md
```

如果 session 不存在，`AcpxAgentTransport` 会尝试自动创建：

```bash
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

默认 Agent 调用会加上保守的非交互边界：

```bash
acpx --timeout 600 --approve-reads --non-interactive-permissions fail ...
```

含义是：读操作可以自动批准；如果 agent 需要无法自动处理的权限，直接失败并把 stderr 留在 ledger 里，避免 CLI 无限挂住。

内置 lifecycle 只在 `implement.claude_implement` 节点启用写权限：

```bash
acpx --timeout 600 --approve-all ...
```

这是因为 Claude implementation 是唯一应该修改业务代码的主控节点；Codex review / acceptance 仍然保持只读审视。

如果 acpx runtime 返回 reconnect、额度不足、登录失效等错误，ledger 会保留 stdout/stderr 和节点状态，但不会替你修复 agent runtime。

## Prompt Templates

Agent prompt 不再写在 Java 字符串里。`AcpxAgentTaskHandler` 会根据 workflow node 的 `prompt_template` 读取 resources：

```text
dual-adapter-acpx/src/main/resources/dev/dual/adapter/acpx/prompts/
  intake-claude-brainstorm.md
  intake-codex-product-review.md
  design-claude.md
  design-codex-review.md
  plan-claude.md
  plan-codex-review.md
  implement-claude.md
  implement-claude-self-test.md
  acceptance-codex-review.md
  stage-claude-handoff.md
```

渲染时会注入：

```text
{{workstream_id}}
{{node_id}}
{{prompt_template}}
{{ledger_path}}
{{requirement}}
```

模板第一版遵守几个原则：

- Claude 是主控 developer / architect，只有 Claude 可以修改业务代码。
- Codex 是外部 reviewer / verifier，不修改业务代码。
- 每个阶段都有明确 Markdown section。
- Codex review 和 acceptance 都必须输出 `Decision`。
- 每个 prompt 末尾都有 `dual-control` block，decision handler 会从这里解析结构化结果。

`dual-control` 格式：

````text
```dual-control
decision: PASS
summary: one short sentence
```
````

当前解析策略：

- Decision node 只读取最新的 `inputRefs`，不会回退到更早文件。
- 找不到 fenced `dual-control` block，会以 `MISSING_DUAL_CONTROL` 停到 `control.repair` recovery gate。
- **Terminal block rule**：如果 markdown 中存在多个 `dual-control` block，只有最后一个（terminal block）被当作控制指令，前面的被视为 prose（例如 agent 引用的用户需求原文）。
- Terminal block 之后不能有非空白内容，否则以 `NON_TERMINAL_CONTROL_BLOCK` 停到 `control.repair` recovery gate；acpx 运行时追加的 `[done] end_turn` sentinel 会被忽略。
- Blockquote `> ```dual-control` 和嵌套 code fence 中的 `dual-control` 不会被匹配（regex 锚定行首 `^`）。
- 找到 `dual-control` 但缺少 `decision`，会以 `INVALID_DUAL_CONTROL` 停到 `control.repair` recovery gate。
- `decision` 不在当前阶段允许集合内，会以 `CONTRACT_VIOLATION`（映射为 `INVALID_DUAL_CONTROL`）停到 `control.repair` recovery gate。
- `BLOCKED` 会把 workflow 标记为 `BLOCKED`，不会继续路由。
- `NEEDS_REVISION` / `REJECT` 会沿 workflow definition 中配置的 revision edge 返回对应 Claude 节点。

合法 decision 值由 `ContractRegistry` 定义（单一真相源）：

| Decision Node | Allowed Decisions |
| --- | --- |
| `intake.decision` | `READY_FOR_PRODUCT_REVIEW`, `NEEDS_REVISION`, `BLOCKED` |
| `design.decision` | `READY_FOR_ARCH_REVIEW`, `NEEDS_REVISION`, `BLOCKED` |
| `plan.decision` | `READY_TO_IMPLEMENT`, `NEEDS_REVISION`, `BLOCKED` |
| `acceptance.decision` | `ACCEPT`, `ACCEPT_WITH_NOTES`, `REJECT`, `BLOCKED` |

每个 agent task node 也有对应的 output contract，定义其合法的 `decision` 值域和 required keys（`decision` + `summary`）。`ContractRegistry` 保证 decision node 的 `allowedDecisions` 与 workflow edge `on` 值和 agent prompt 模板声明的值域一致。

Prompt 模板变量在加载时校验：如果模板引用了非 `KNOWN_VARIABLES` 集合（`workstream_id`, `node_id`, `prompt_template`, `ledger_path`, `requirement`, `context_package`）中的 `{{variable}}`，会立即报错。

## Commands

### Doctor

检查本机和 worktree 是否具备运行条件：

```bash
dualctl doctor --workdir <effective_worktree>
dualctl doctor --workdir <effective_worktree> <workstream_id>
```

`doctor` 会检查 Git、当前目录是否是 Git repo、acpx 是否可用。传入 `workstream_id` 时，还会检查 workflow current、events、runs、context、Git evidence 等 ledger 文件是否存在。`context` / `git evidence` 这类阶段性产物没生成时只会显示 `WARN`，不会当成阻断错误。

### Start

在目标 Git 仓库里执行：

```bash
dualctl start "给 README 增加一段项目说明"
```

从文件读取需求：

```bash
dualctl start --task-file ./requirement.md
```

启动后直接推进到第一个人工卡点：

```bash
dualctl start --run --task-file ./requirement.md
```

指定目标仓库：

```bash
dualctl start --workdir /path/to/repo --task-file ./requirement.md
```

指定分支或 worktree 目录：

```bash
dualctl start --branch ai/my-task --worktree-dir ../repo-my-task "任务描述"
```

指定 workflow 定义文件：

```bash
dualctl start --workflow ./workflow.json "任务描述"
```

使用仓库内置示例 workflow：

```bash
dualctl start --workflow dual-orchestrator/workflows/agent-lifecycle.v1.json "任务描述"
```

启动前校验 workflow 文件：

```bash
dualctl workflow validate dual-orchestrator/workflows/agent-lifecycle.v1.json
```

查看当前 CLI 内置支持的 handler：

```bash
dualctl workflow handlers
```

`workflow validate` 会检查结构合法性，也会检查 handler type 是否属于当前 CLI 内置能力。未来如果使用外部 runtime 提供的 handler，可以显式放行：

```bash
dualctl workflow validate --allow-unknown-handlers ./workflow.json
```

`dualctl start --workflow` 也会做同样的 handler 检查，避免创建 worktree 后才在 `resume` 阶段发现 handler 拼错。如果确实要使用外部 runtime handler，启动时也需要显式放行：

```bash
dualctl start --workflow ./workflow.json --allow-unknown-handlers "任务描述"
```

导出内置默认 workflow 作为自定义起点：

```bash
dualctl workflow print-default --output ./workflow.json
```

如果不传 `--workflow`，默认使用内置的 `agent-lifecycle@1`。如果传入 workflow 文件，启动时会严格校验 nodes / edges / entry node，并把当时的 workflow definition 固化到 `.agent/workstreams/<workstream_id>/workflow/definition.json`。后续即使原始配置文件变化，这次 run 仍按账本中的 definition 复盘。

最小 workflow 示例：

```json
{
  "workflowId": "custom-lifecycle",
  "version": 1,
  "entryNodeId": "intake.claude_brainstorm",
  "defaults": {
    "sessions": {
      "claude": "claude-main",
      "codex": "codex-reviewer"
    },
    "delivery": {
      "base": "main",
      "draft": true
    },
    "revision": {
      "max_revisions": 1
    }
  },
  "nodes": [
    {
      "id": "intake.claude_brainstorm",
      "kind": "TASK",
      "handlerType": "acpx.agent_run",
      "config": {
        "agent": "claude",
        "prompt_template": "intake-claude-brainstorm"
      }
    },
    {
      "id": "gate.human_requirement_review",
      "kind": "GATE",
      "handlerType": "human.review",
      "config": {
        "allowed_actions": ["approve", "revise"]
      }
    }
  ],
  "edges": [
    {
      "from": "intake.claude_brainstorm",
      "on": "completed",
      "to": "gate.human_requirement_review"
    }
  ]
}
```

`defaults` 是可选的，用来减少 workflow 文件重复配置：

- `defaults.sessions.claude` 会填充 `agent=claude` 且没有显式 `session` 的 `acpx.agent_run` 节点。
- `defaults.sessions.codex` 会填充 `agent=codex` 且没有显式 `session` 的 `acpx.agent_run` 节点。
- `defaults.delivery` 会填充 `delivery.create_pr` 节点缺失的 `base`、`draft` 等配置。
- `defaults.revision.max_revisions` 会填充 `review.decision` 和 `human.review` 节点缺失的 revision 上限。达到上限后，workflow 会停在 recovery gate，不会无限开新 iteration。
- 节点自己的 `config` 优先级更高；一旦节点显式写了 `session`、`base`、`draft` 或 `max_revisions`，不会被 defaults 覆盖。

启动后会打印：

```text
Workstream: ws-...
Workspace: <effective worktree>
Branch: ai/ws-...
Ledger: <worktree>/.agent/workstreams/<workstream_id>
Workflow: agent-lifecycle@1
Next: dualctl resume --workdir <effective worktree> <workstream_id>
```

创建 worktree 时，`dualctl` 会把 `.agent/` 追加到本地 Git exclude（`git rev-parse --git-path info/exclude` 指向的位置），避免 run ledger 出现在业务仓库的 `git status` 里。它不会修改项目自己的 `.gitignore`。

### Status

```bash
dualctl status --workdir <effective_worktree> <workstream_id>
```

输出分两块：

- `Workflow`：新的 workflow current state，是当前 `resume` / `review` 真正使用的状态。
- `Compatibility snapshot (legacy, may be stale)`：旧 design-loop ledger 的兼容快照，字段可能不会随着新 workflow 每一步同步更新。

过渡期两套文件会同时存在；判断当前真实 active node 时，以 `Workflow` 区块和 `.agent/workstreams/<workstream_id>/workflow/current.json` 为准。

### Runs

列出当前 workstream 的 Agent run：

```bash
dualctl runs --workdir <effective_worktree> <workstream_id>
dualctl runs --workdir <effective_worktree> --failed <workstream_id>
dualctl runs --workdir <effective_worktree> --agent codex --latest <workstream_id>
```

输出包含 run id、状态、agent、node、exit code、duration 和 failure reason。排障时可以用 `--failed` 只看失败 run，用 `--agent claude|codex` 只看某个 Agent，用 `--status` 按状态过滤，用 `--latest` 只取最后一条匹配结果。它读取：

```text
.agent/workstreams/<workstream_id>/runs/<run_id>/run.json
```

### Events

查看最近 workflow events：

```bash
dualctl events --workdir <effective_worktree> <workstream_id>
dualctl events --workdir <effective_worktree> --limit 50 <workstream_id>
dualctl events --workdir <effective_worktree> --type node_completed <workstream_id>
dualctl events --workdir <effective_worktree> --contains rate_limit <workstream_id>
```

输出原始 JSONL，方便脚本处理：

```bash
dualctl events --workdir <effective_worktree> --raw <workstream_id>
```

### Inspect

查看当前 active node：

```bash
dualctl inspect --workdir <effective_worktree> <workstream_id>
```

查看指定 node：

```bash
dualctl inspect --workdir <effective_worktree> --node <node_instance_id> <workstream_id>
```

直接查看当前 active node 或指定 node 关联的 context package：

```bash
dualctl inspect --workdir <effective_worktree> --show context <workstream_id>
dualctl inspect --workdir <effective_worktree> --node <node_instance_id> --show context <workstream_id>
```

查看指定 run：

```bash
dualctl inspect --workdir <effective_worktree> --run <run_id> <workstream_id>
```

直接查看 run 的 prompt / output / stderr：

```bash
dualctl inspect --workdir <effective_worktree> --run <run_id> --show prompt <workstream_id>
dualctl inspect --workdir <effective_worktree> --run <run_id> --show output <workstream_id>
dualctl inspect --workdir <effective_worktree> --run <run_id> --show stderr <workstream_id>
dualctl inspect --workdir <effective_worktree> --run <run_id> --show output-meta <workstream_id>
```

这是只读命令，不会修改 ledger。

### Resume

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

`resume` 会读取：

```text
.agent/workstreams/<workstream_id>/workflow/current.json
```

然后执行当前 active node：

- Task node：调用对应 TaskHandler。
- Decision node：调用 DecisionHandler 并选择 edge。
- Gate node：停住，等待 `review approve` 或 `review revise`。

### Run

`run` 会连续执行 active node，直到遇到人工卡点、恢复卡点、失败或 workflow 完成：

```bash
dualctl run --workdir <effective_worktree> <workstream_id>
dualctl run --workdir <effective_worktree> --max-steps 50 <workstream_id>
```

这是真实使用时的主推进命令。`resume` 仍保留用于单步排查。

### Retry

如果当前 active node 因为 agent runtime、quota、context window 或 prompt contract 问题进入 `FAILED` / `BLOCKED`，workflow 会停在 `WAITING_FOR_RECOVERY`，并打开一个 recovery gate。先修复外部问题，再显式 retry：

失败现场会写入：

```text
.agent/workstreams/<workstream_id>/recovery/current-failure.md
```

这份摘要会列出当前失败节点、handler、失败原因、acpx/session 信息、exit code、output refs，以及建议的 `dualctl retry` 命令。它是给人工恢复使用的入口，不替代 `nodes/*.json`、`events.jsonl` 和具体 run outputs。

```bash
dualctl retry --workdir <effective_worktree> --note "claude session reconnected" <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

`retry` 不会直接执行 Agent。它只会：

- 校验当前 active node 是 `FAILED` 或 `BLOCKED`。
- 把该 node 标记回 `READY`。
- 清空该 node 的旧 output refs。
- 记录 `node_retry_requested` event。
- 清空当前 recovery gate，使 workflow 回到 `RUNNING`。

这样可以保留失败现场，同时让下一次 `resume` 重新执行同一个 node。

在 `WAITING_FOR_RECOVERY` 状态下直接执行 `dualctl resume` 不会重新跑 Agent；它只会提示先执行 `dualctl retry`。这避免了 rate limit、context window、输出契约错误还没处理时被重复触发。

如果确认这个 workstream 不再继续，可以终止 recovery：

```bash
dualctl recovery abort \
  --workdir <effective_worktree> \
  --note "scope changed; stop this workstream" \
  <workstream_id>
```

`recovery abort` 会把 workflow 标记为 `FAILED`，清空 active node / open gate，并写入 `workflow_aborted` event。它不会删除 worktree、代码改动或 ledger。

### Human Review

查看当前 gate 的 review packet：

```bash
dualctl review show --workdir <effective_worktree> <workstream_id>
```

`review show` 会读取当前 active gate 对应的：

```text
.agent/workstreams/<workstream_id>/reviews/<gate_node_instance_id>.md
```

这个 packet 是后续 CLI watch / Web UI 共用的人工审查读模型，里面会列出 gate、允许动作、输入 artifacts 和关键内容摘录。

### Timeline

ledger 会自动维护当前 workstream 的进度读模型：

```text
.agent/workstreams/<workstream_id>/timeline.json
```

查看节点概览：

```bash
dualctl timeline --workdir <effective_worktree> <workstream_id>
dualctl timeline --workdir <effective_worktree> --raw <workstream_id>
```

持续观察进度：

```bash
dualctl watch --workdir <effective_worktree> <workstream_id>
dualctl watch --workdir <effective_worktree> --once <workstream_id>
dualctl watch --workdir <effective_worktree> --interval-ms 1000 <workstream_id>
```

`timeline.json` 聚合 workflow current 和 nodes 快照，包含 workflow 状态、active nodes、open gates、每个 node 的状态、iteration、artifact refs 和 duration。`watch` / Web UI 会优先消费这个读模型，而不是直接解析 ledger 原始文件。

批准当前 gate：

```bash
dualctl review approve --workdir <effective_worktree> <workstream_id>
dualctl review approve --workdir <effective_worktree> --run <workstream_id>
```

要求 revision：

```bash
dualctl review revise \
  --workdir <effective_worktree> \
  --feedback-file ./human-feedback.md \
  <workstream_id>
dualctl review revise \
  --workdir <effective_worktree> \
  --feedback-file ./human-feedback.md \
  --run \
  <workstream_id>
```

完成当前 workstream：

```bash
dualctl review complete --workdir <effective_worktree> <workstream_id>
```

`approve` / `revise` / `complete` 都会写 workflow event，并按 workflow edge 进入下一个节点或结束 workflow。传 `--run` 时，CLI 会在 gate 处理完成后继续推进到下一个人工卡点、恢复卡点、失败或完成。Gate 会把自己的输入 artifact 继续传给下一个节点，所以人工批准不会切断上一阶段生成的上下文。

`revise` 表示“上一轮产出需要按反馈修改”，因此会开启新的 workflow iteration。`gate.human_next_stage_approval` 的 `approve` 会开启新的 workflow iteration，并把 `next-stage-prompt.md` 传回 `intake.claude_brainstorm`，让同一个 workstream 继续下一阶段生命周期；`complete` 则表示当前 workstream 已经收口，不再进入下一轮。`retry` 不会开启新 iteration，因为它只是恢复同一个节点的执行尝试。

## Ledger

每个 workstream 在 effective worktree 中写入：

```text
.agent/workstreams/<workstream_id>/
  workflow/
    definition.json
    instance.json
    current.json
  nodes/
    <node_instance_id>.json
  iterations/
    iter-001.json
    iter-002.json
  gates/
    <gate_id>.json
  context/
    <context_node_instance_id>.md
  runs/
    <run_id>/
      run.json
      prompts/
      outputs/
  artifacts/
    git-evidence/
      git-evidence.json
      base-commit.txt
      branch.txt
      git-root.txt
      git-status-short.txt
      diff.stat
      diff.patch
      changed-files.txt
  reports/
    iter-001-final-report.md
    latest-final-report.md
  events.jsonl
```

每次 `acpx.agent_run` 都会写一个结构化 run metadata：

```text
.agent/workstreams/<workstream_id>/runs/<run_id>/run.json
```

当前字段包括：

- `runId`
- `workstreamId`
- `nodeId`
- `agent`
- `session`
- `promptTemplate`
- `workdir`
- `promptPath`
- `outputPath`
- `stderrPath`
- `promptMetadataPath`
- `outputMetadataPath`
- `stderrMetadataPath`
- `command`
- `startedAt`
- `finishedAt`
- `durationMs`
- `exitCode`
- `status`
- `failureReason`
- `recoverable`

这是后续 `inspect/tail` 和只读 UI 的基础数据源。

`promptPath` / `outputPath` / `stderrPath` 旁边会生成对应的 `*.meta.json` sidecar，记录 artifact type、路径、字节数、行数、SHA-256、run id、node id、agent、session 等信息。Markdown 仍然是人读的主要材料，sidecar 用于审计、去重、UI 展示和后续自动校验。

`context.build_package` 会在关键 Agent step 前生成上下文包，例如 Codex review、Claude implementation、Codex final acceptance。上下文包不是新事实源，而是从 ledger 里把本轮 Agent 最该读的东西装到一个 Markdown 文件：

- user requirement。
- 上一个节点传来的 input artifacts。
- 最近 workflow iteration history，包括 `revise` / `NEEDS_REVISION` / `REJECT` 的触发原因。
- 最近 human gate feedback，例如 `review revise --feedback-file` 写入的 Must Fix / 修改意见。
- Git evidence 指针和 `git-evidence.json` 摘要。
- events、workflow current、nodes、runs 等 ledger 指针。

上下文包通过 context governance 架构渲染，每个 profile 有独立的 budget 和 section degradation 策略：

| Profile | Budget | Sections | 特点 |
| --- | --- | --- | --- |
| `product_review` | 16k | requirement, input_artifacts, recent_agent_outputs, ledger_pointers | 轻量，聚焦需求+输出 |
| `architecture_review` | 20k | +human_feedback | 需要人工反馈 |
| `plan_review` | 22k | +iteration_history | 需要完整迭代历史 |
| `implementation` | 24k | 全部 (含 git_evidence) | 最完整，budget 最严 |
| `acceptance` | 26k | 全部 + git_evidence HIGH priority | 验收需要 git evidence |
| `full` (default) | 30k | 全部 | 宽松预算，所有 section FULL |

Governance pipeline 分 6 阶段：加载 profile → 检查 section 可用性 → 按 priority 分配 budget → 按 degradation 策略渲染 → 验证总 budget → 输出 governance summary table。每个 section 有自己的 `MissingPolicy`（FAIL/SKIP/PLACEHOLDER）、`Priority`（CRITICAL/HIGH/MEDIUM/LOW）、`Degradation` 策略（FULL/SUMMARY/LATEST/DECISION_ONLY/DIFF_STAT/POINTER_ONLY）和 `OverflowPolicy`（FAIL/DEGRADE/DROP_OPTIONAL）。超预算时从最低 priority section 开始降级，requirement section 不参与降级。

自定义 workflow 可以在 `context.build_package` 节点里配置：

```json
{
  "profile": "acceptance",
  "include_sections": ["requirement", "recent_agent_outputs", "git_evidence"],
  "max_snippet_chars": 4000,
  "max_recent_agent_outputs": 8
}
```

`include_sections` 会覆盖 profile 的默认章节，适合临时做更窄的验收包或补证据包。

Agent prompt 会出现 `## Context Package`，包含上下文包路径和一个短预览。这样 Agent 可以优先读同一份上下文，减少人工 copy/paste，也给后续 token budget、summary、session handoff 留出统一入口。

`stage.generate_next_prompt` 会读取 Claude handoff 输出，优先提取 `## Suggested Next Prompt` 章节，并生成：

```text
.agent/workstreams/<workstream_id>/artifacts/next-stage/next-stage-prompt.md
.agent/workstreams/<workstream_id>/artifacts/next-stage/handoff.json
```

`next-stage-prompt.md` 是下一阶段的人类 review 入口之一。它会保留 handoff 原文作为上下文，但把建议提示词单独放在 `## Prompt`。

`handoff.json` 是从 agent handoff markdown 解析生成的结构化 sidecar，包含 `completedWork`、`changedFiles`、`openRisks`、`verificationState` 等字段。下游 `context.build_package` handler 在渲染 git_evidence DIFF_STAT 时会优先使用 `changedFiles` 数组作为数据源，避免重新解析 markdown。如果 handoff.json 不存在，handler 回退到文件读取。

当 `gate.human_next_stage_approval` 被批准后，workflow 会回到 `intake.claude_brainstorm` 开启下一轮 iteration，并把这个文件作为 direct input artifact 展开到 Claude prompt 中。

`implement.artifact_validation` 会在 Claude self-test 之后采集 Git evidence。它不会替 Agent 做测试，也不会判定业务验收是否通过；它只把可追溯证据固定到账本里，让 Codex final acceptance 能基于同一组事实材料判断。采集时会排除 `.agent` 运行账本，避免把账本文件误当成业务改动：

- `git-status-short.txt`：当前 worktree 的 `git status --short`。
- `diff.stat`：`git diff --stat`。
- `diff.patch`：`git diff`，不包含未跟踪文件内容。
- `changed-files.txt`：从 porcelain status 提取的变更文件列表，包含未跟踪文件名。
- `git-evidence.json`：稳定索引，包含 `available`、`workdir`、`gitRoot`、`branch`、`baseCommit`、`hasChanges`、`changedFileCount`、`generatedAt`、`error`。

如果当前目录不是 Git repo，handler 不会把 workflow 打崩；它会写入 `available=false` 的 `git-evidence.json`，让后续验收报告明确说明 Git evidence 不可用。

`acceptance.generate_final_report` 会在 Codex acceptance decision 为 `ACCEPT` 或 `ACCEPT_WITH_NOTES` 后生成：

```text
.agent/workstreams/<workstream_id>/reports/<iteration_id>-final-report.md
.agent/workstreams/<workstream_id>/reports/latest-final-report.md
```

报告会汇总 user request、Codex acceptance 摘要、Claude self-test 摘要、Git evidence 指针和最近 Agent 输出。它不替代 Codex 验收，只负责把本轮结果固定成一个人和后续自动化都能引用的交付物。

`delivery.prepare_pr` 会在 final report 后生成交付材料：

```text
.agent/workstreams/<workstream_id>/delivery/commit-message.txt
.agent/workstreams/<workstream_id>/delivery/pr-body.md
```

`commit-message.txt` 用于提交 worktree 中的业务改动，`pr-body.md` 汇总 user request、verification、Codex final acceptance、changed files 和 run artifacts。默认 workflow 只准备材料，不自动提交、不自动 push。

需要提交业务改动时执行：

```bash
dualctl delivery commit --workdir <effective_worktree> <workstream_id>
```

`delivery commit` 会用生成的 commit message 执行 `git commit`，并排除 `.agent` 运行账本；如果没有 `.agent` 之外的业务改动，会跳过提交。
执行结果会写入 `.agent/workstreams/<workstream_id>/delivery/delivery.json`，并刷新 `reports/latest-final-report.md` 里的 `## Delivery` 段。

需要创建 PR 时执行：

```bash
dualctl delivery create-pr --workdir <effective_worktree> <workstream_id>
dualctl delivery create-pr --workdir <effective_worktree> --base main --draft <workstream_id>
dualctl delivery create-pr --workdir <effective_worktree> --title "feat: my task" <workstream_id>
```

`delivery create-pr` 会先确保业务改动已提交，然后执行 `git push -u origin <branch>` 和 `gh pr create`。它要求当前分支不是 `main` / `master`，并且 worktree 有可识别的 `origin` remote。gh 输出会保存到：

```text
.agent/workstreams/<workstream_id>/delivery/gh-pr-create.stdout.log
.agent/workstreams/<workstream_id>/delivery/gh-pr-create.stderr.log
.agent/workstreams/<workstream_id>/delivery/pr-url.txt
```

`dualctl status` 会读取 `delivery.json` 并显示 delivery status、commit hash 和 PR URL。

过渡期仍会看到旧 design-loop ledger：

```text
.agent/workstreams/<workstream_id>/
  workstream.json
  requirement.md
  timeline.json
  timeline.jsonl
  current/
  stages/
  checkpoints/
  workspaces/
```

事实流是 `events.jsonl`，`workflow/current.json` 是为了快速 `status` / `resume` 的快照。

### Workflow Iteration

workflow iteration 表示一次有业务意义的尝试，不是命令执行次数。

- `approve` / `completed`：沿用当前 iteration。
- `retry`：沿用当前 iteration，只重新执行当前失败或阻塞的 node。
- `revise` / `NEEDS_REVISION` / `REJECT`：开启新 iteration，例如 `iter-002`。

每个 iteration 会写到：

```text
.agent/workstreams/<workstream_id>/iterations/<iteration_id>.json
```

revision iteration 会记录：

- `parentIterationId`：上一轮。
- `triggerNodeInstanceId`：哪个 gate / decision 触发。
- `triggerAction`：`revise`、`NEEDS_REVISION` 或 `REJECT`。
- `targetNodeDefinitionId`：回到哪个节点继续。
- `reason`：来自 human feedback、Codex summary 或错误原因的摘要。

这个模型能区分两类情况：工具失败、quota、context window、格式错误属于执行层恢复，应该 `retry`；Codex 或人明确要求修改产出，才进入下一轮 iteration。这样账本能表达“业务版本在收敛”，也不会把 acpx 断线误记成新一版。

当前仍有一个风险：iteration 只能说明“为什么进入下一轮”，不能保证 Agent 一定完整处理了反馈。业务满足性仍需要看 context package、Agent output、Git evidence、Codex acceptance 和 human gate 的组合证据。后续可以继续补强 output contract validation 和 acceptance checklist。

## Smoke Tests

### Build Smoke

```bash
cd dual-orchestrator
mvn test
mvn -pl dual-cli -am package
```

### Workflow Ledger Smoke

```bash
cd /path/to/repo
dualctl start "给 README 增加一段项目说明"
dualctl status --workdir <effective_worktree> <workstream_id>
```

预期：

- worktree 已创建。
- `.agent/workstreams/<workstream_id>/workflow/current.json` 存在。
- `.agent/workstreams/<workstream_id>/events.jsonl` 包含 `workflow_started`。
- `status` 能看到 `Workflow: agent-lifecycle@1`。

### Real acpx Smoke

完整生命周期和多 workflow variant 的脚本化 E2E 用法见 [Smoke Test E2E 使用说明](docs/testing/smoke-test-e2e.md)。其中 `scripts/smoke-test.sh --case all` 会顺序运行 thin、p5、p6 三个 case。

推荐使用一个明确的临时 worktree 目录，方便跑完清理：

```bash
cd /path/to/repo
dualctl start \
  --branch ai/smoke-dualctl \
  --worktree-dir /tmp/dualctl-smoke \
  "smoke: update README one line"
```

启动后记下输出里的：

```text
Workstream: <workstream_id>
Workspace: <effective_worktree>
Branch: <branch>
```

确认或创建 session。通常 `resume` 会自动创建缺失 session；如果你想提前验证，也可以手工执行：

```bash
cd <effective_worktree>
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

推进 Claude intake 节点：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

预期 active node 从 `intake.claude_brainstorm` 推进到 `intake.codex_product_review`。

继续推进 Codex review：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

预期 active node 推进到 `intake.decision`。

再推进 decision 到人工卡点：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
dualctl status --workdir <effective_worktree> <workstream_id>
```

预期：

- `Workflow status: WAITING_FOR_HUMAN` 或 `Workflow -> Status: WAITING_FOR_HUMAN`。
- `Open gates` 出现 `gate-...human_requirement_review...`。

批准卡点：

```bash
dualctl review approve --workdir <effective_worktree> <workstream_id>
dualctl status --workdir <effective_worktree> <workstream_id>
```

预期 active node 进入 `design.claude_design`。到这里就可以停止 smoke，不必继续跑完整生命周期。

预期：

- 当前 active node 被执行。
- Agent run metadata 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/run.json`。
- Agent prompt 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/prompts/`。
- stdout/stderr 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/outputs/`。
- 失败时 workflow current 会停在 `FAILED` 或 `BLOCKED`，现场不丢失。

本仓库已经验证过一次最小真实链路：

```text
start -> run(直到 human gate) -> review approve
```

验证结果：

- worktree 创建成功。
- Claude 和 Codex 都通过 acpx 完成一次真实调用。
- run outputs、prompts、events.jsonl、workflow/current.json 均落盘。
- human gate 能打开并被 `review approve` 推进。

## Worktree Cleanup

当前不会自动删除 worktree。

查看：

```bash
git -C /path/to/original/repo worktree list
```

删除 worktree：

```bash
git -C /path/to/original/repo worktree remove <effective_worktree>
```

删除分支：

```bash
git -C /path/to/original/repo branch -D <branch>
```

## Troubleshooting

### `acpx: command not found`

```bash
which acpx
acpx --help
```

如果没有输出，需要先安装或修正 PATH。

### `No acpx session found`

在 effective worktree 中创建 session：

```bash
cd <effective_worktree>
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

### `agent needs reconnect`

这是 acpx agent runtime 状态问题，常见原因：

- Claude Code / Codex session 断开。
- agent runtime 没启动。
- 登录态或额度状态异常。
- acpx session 绑定目录和当前 worktree 状态不一致。

处理后重新执行：

```bash
dualctl retry --workdir <effective_worktree> --note "agent reconnected" <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

acpx adapter 会把这类错误标记为：

```text
failure_reason: RECOVERABLE_AGENT_RECONNECT
recoverable: true
```

### `git worktree add` 报权限错误

如果在受限 sandbox 中运行，`git worktree add` 需要写原始仓库的 `.git` 目录和目标 worktree 目录。请在正常终端执行，或给当前环境授予创建 worktree 的权限。

### `git worktree add` 报分支目录冲突

如果已有 `ai` 分支，Git 可能无法再创建 `ai/<name>` 这种层级分支。解决方式是指定一个不冲突的分支名：

```bash
dualctl start --branch ai-smoke-dualctl "任务描述"
```

### `workflow ledger already initialized`

同一个 workstream 已经有 workflow ledger。不要重复 `start` 同一个账本；使用 `status` / `resume` 继续。

### `INVALID_DUAL_CONTROL`

Codex review / final acceptance 这类 decision 节点要求 Agent 输出一个严格的 contract block。缺失、格式错误或 decision 取值不合法时，workflow 会停在 `WAITING_FOR_RECOVERY`，并打开 `control.repair` gate；账本会记录 `control_repair_required` 事件。

````markdown
```dual-control
decision: PASS
summary: 一句话说明判断依据
```
````

当前校验规则：

- 只有行首的 ` ```dual-control` 围栏块会被解析，blockquote `> ```dual-control` 和嵌套 code fence 中的不会被匹配。
- 多个 `dual-control` block 时，只有最后一个（terminal block）生效，前面的视为 prose。
- Terminal block 之后不能有非空白内容。
- block 内必须是 `key: value` 行。
- `decision` 必须存在，并且属于当前节点允许的取值。
- 只读最新的 inputRef 文件，不会回退读取更早的旧输出。

修正 Agent 输出后执行：

```bash
dualctl retry --workdir <effective_worktree> --note "fixed dual-control output" <workstream_id>
dualctl resume --workdir <effective_worktree> <workstream_id>
```

### `missing handler`

workflow definition 中的 `handler_type` 没有注册到 `HandlerRegistry`。这通常是开发期配置错误。

### `unsupported gate action`

当前 gate 没有对应 action edge。例如 workflow definition 没有为某个 gate 配置 `revise` 分支。

## Exit Codes

| Exit Code | Meaning |
| --- | --- |
| `0` | 命令完成 |
| `1` | 一般失败或 workflow failed |
| `2` | Picocli 参数错误 |

后续会继续细分 workspace、schema、transport、human gate 等错误码。

## Current Limits

- 仍是 CLI-first，没有 API server / scheduler。
- workflow 已支持 JSON 文件加载、条件表达式（`EdgeCondition`）、并行节点（fan-out + sequential execution）和 schema 版本自动迁移（`LedgerMigrator`）。
- context package 已有 budget-controlled governance pipeline（6 profile，priority-based degradation，per-profile config overrides），但没有 token-level budgeting 或 adaptive compression。
- auto-retry 按 failure type 自动分类并指数退避重试，但还没有交互式修复向导。
- notification 还没接入 Java 版。
