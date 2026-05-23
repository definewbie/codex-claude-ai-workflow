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
6. Stage handoff and next-stage prompt

第一版很多节点还是薄实现：内部 decision handler 使用默认决策，artifact validation 和 next-stage prompt 还是占位式 handler。`acpx.agent_run` 已经能通过 `acpx` 调 Claude / Codex。

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
- human review gate。
- review decision handler。
- artifact validation handler。
- next-stage prompt handler。
- `dualctl start/status/resume/review approve/review revise` 接入 workflow ledger。

仍未完成：

- 完整多轮 revision 策略。
- context package 分层装配。
- quota / context window / session reconnect 分类恢复。
- API server / scheduler。
- Web UI / dashboard。
- PR / notification。
- 正式安装脚本。

## Modules

```text
dual-core
  domain model
  workflow model

dual-ledger-file
  file ledger
  workflow/current/nodes/gates/events
  local lock

dual-runtime
  WorkflowRuntime
  HandlerRegistry
  built-in handlers
  lifecycle workflow definition

dual-adapter-acpx
  acpx transport
  acpx.agent_run TaskHandler

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

如果 acpx runtime 返回 reconnect、额度不足、登录失效等错误，ledger 会保留 stdout/stderr 和节点状态，但不会替你修复 agent runtime。

## Commands

### Start

在目标 Git 仓库里执行：

```bash
dualctl start "给 README 增加一段项目说明"
```

从文件读取需求：

```bash
dualctl start --task-file ./requirement.md
```

指定目标仓库：

```bash
dualctl start --workdir /path/to/repo --task-file ./requirement.md
```

指定分支或 worktree 目录：

```bash
dualctl start --branch ai/my-task --worktree-dir ../repo-my-task "任务描述"
```

启动后会打印：

```text
Workstream: ws-...
Workspace: <effective worktree>
Branch: ai/ws-...
Ledger: <worktree>/.agent/workstreams/<workstream_id>
Workflow: agent-lifecycle@1
Next: dualctl resume --workdir <effective worktree> <workstream_id>
```

### Status

```bash
dualctl status --workdir <effective_worktree> <workstream_id>
```

输出包含 legacy design-loop ledger 和新的 workflow current state。过渡期两套文件会同时存在；后续会逐步收敛到 workflow ledger。

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

### Human Review

批准当前 gate：

```bash
dualctl review approve --workdir <effective_worktree> <workstream_id>
```

要求 revision：

```bash
dualctl review revise \
  --workdir <effective_worktree> \
  --feedback-file ./human-feedback.md \
  <workstream_id>
```

`approve` / `revise` 都会写 workflow event，并按 workflow edge 进入下一个节点。

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
  gates/
    <gate_id>.json
  runs/
    <run_id>/
      prompts/
      outputs/
  events.jsonl
```

过渡期仍会看到旧 design-loop ledger：

```text
.agent/workstreams/<workstream_id>/
  workstream.json
  requirement.md
  timeline.jsonl
  current/
  stages/
  checkpoints/
  workspaces/
```

事实流是 `events.jsonl`，`workflow/current.json` 是为了快速 `status` / `resume` 的快照。

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

先确认或创建 session：

```bash
cd <effective_worktree>
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

推进一个节点：

```bash
dualctl resume --workdir <effective_worktree> <workstream_id>
```

预期：

- 当前 active node 被执行。
- Agent prompt 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/prompts/`。
- stdout/stderr 写入 `.agent/workstreams/<workstream_id>/runs/<run_id>/outputs/`。
- 失败时 workflow current 会停在 `FAILED` 或 `BLOCKED`，现场不丢失。

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
dualctl resume --workdir <effective_worktree> <workstream_id>
```

### `workflow ledger already initialized`

同一个 workstream 已经有 workflow ledger。不要重复 `start` 同一个账本；使用 `status` / `resume` 继续。

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
- workflow definition 目前是 Java factory，不是外部配置文件。
- decision handler 当前使用默认决策，还没有解析真实 Codex structured review。
- artifact validation 仍是薄实现。
- context package / recovery gate 还没实现。
- PR / notification 还没接入 Java 版。
