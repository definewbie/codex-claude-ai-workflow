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

第一版很多节点还是薄实现：next-stage prompt 仍是占位式 handler；artifact validation 已经会采集 Git evidence，供后续 Codex acceptance 使用。`acpx.agent_run` 已经能通过 `acpx` 调 Claude / Codex，decision handler 已经能解析 Agent 输出里的 `dual-control` block。

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
- review decision handler。
- context package handler，会在关键 Agent step 前装配可读上下文。
- artifact validation handler，会落盘 Git status / diff / changed files。
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

当前 Java 版调用 acpx 时会加上保守的非交互边界：

```bash
acpx --timeout 600 --approve-reads --non-interactive-permissions fail ...
```

含义是：读操作可以自动批准；如果 agent 需要无法自动处理的权限，直接失败并把 stderr 留在 ledger 里，避免 CLI 无限挂住。后续实现 implementation 阶段前，需要把权限策略做成可配置项。

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

当前解析策略是严格失败：

- Decision node 只读取上一个节点传入的 `inputRefs`。
- 找不到 fenced `dual-control` block，会把 workflow 标记为 `FAILED`。
- 找到 `dual-control` 但缺少 `decision`，会以 `INVALID_DUAL_CONTROL` 标记为 `FAILED`，并在 node result 中记录已解析字段和允许值。
- `decision` 不在当前阶段允许集合内，会把 workflow 标记为 `FAILED`。
- `BLOCKED` 会把 workflow 标记为 `BLOCKED`，不会继续路由。
- `NEEDS_REVISION` / `REJECT` 会沿 workflow definition 中配置的 revision edge 返回对应 Claude 节点。

当前允许值：

| Decision Node | Allowed Decisions |
| --- | --- |
| `intake.decision` | `PASS`, `NEEDS_REVISION`, `BLOCKED` |
| `design.decision` | `PASS`, `NEEDS_REVISION`, `BLOCKED` |
| `plan.decision` | `READY_TO_IMPLEMENT`, `NEEDS_REVISION`, `BLOCKED` |
| `acceptance.decision` | `ACCEPT`, `ACCEPT_WITH_NOTES`, `REJECT`, `BLOCKED` |

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

输出分两块：

- `Legacy snapshot`：旧 design-loop ledger 的兼容快照，字段可能不会随着新 workflow 每一步同步更新。
- `Workflow`：新的 workflow current state，是当前 `resume` / `review` 真正使用的状态。

过渡期两套文件会同时存在；判断当前真实 active node 时，以 `Workflow` 区块和 `.agent/workstreams/<workstream_id>/workflow/current.json` 为准。

### Runs

列出当前 workstream 的 Agent run：

```bash
dualctl runs --workdir <effective_worktree> <workstream_id>
```

输出包含 run id、状态、agent、node、exit code、duration 和 failure reason。它读取：

```text
.agent/workstreams/<workstream_id>/runs/<run_id>/run.json
```

### Events

查看最近 workflow events：

```bash
dualctl events --workdir <effective_worktree> <workstream_id>
dualctl events --workdir <effective_worktree> --limit 50 <workstream_id>
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

### Retry

如果当前 active node 因为 agent runtime、quota、context window 或 prompt contract 问题进入 `FAILED` / `BLOCKED`，workflow 会停在 `WAITING_FOR_RECOVERY`，并打开一个 recovery gate。先修复外部问题，再显式 retry：

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

`revise` 表示“上一轮产出需要按反馈修改”，因此会开启新的 workflow iteration。`retry` 不会开启新 iteration，因为它只是恢复同一个节点的执行尝试。

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
- `command`
- `startedAt`
- `finishedAt`
- `durationMs`
- `exitCode`
- `status`
- `failureReason`
- `recoverable`

这是后续 `inspect/tail` 和只读 UI 的基础数据源。

`context.build_package` 会在关键 Agent step 前生成上下文包，例如 Codex review、Claude implementation、Codex final acceptance。上下文包不是新事实源，而是从 ledger 里把本轮 Agent 最该读的东西装到一个 Markdown 文件：

- user requirement。
- 上一个节点传来的 input artifacts。
- 最近 workflow iteration history，包括 `revise` / `NEEDS_REVISION` / `REJECT` 的触发原因。
- 最近 human gate feedback，例如 `review revise --feedback-file` 写入的 Must Fix / 修改意见。
- Git evidence 指针和 `git-evidence.json` 摘要。
- events、workflow current、nodes、runs 等 ledger 指针。

Agent prompt 会出现 `## Context Package`，包含上下文包路径和一个短预览。这样 Agent 可以优先读同一份上下文，减少人工 copy/paste，也给后续 token budget、summary、session handoff 留出统一入口。

`implement.artifact_validation` 会在 Claude self-test 之后采集 Git evidence。它不会替 Agent 做测试，也不会判定业务验收是否通过；它只把可追溯证据固定到账本里，让 Codex final acceptance 能基于同一组事实材料判断。采集时会排除 `.agent` 运行账本，避免把账本文件误当成业务改动：

- `git-status-short.txt`：当前 worktree 的 `git status --short`。
- `diff.stat`：`git diff --stat`。
- `diff.patch`：`git diff`，不包含未跟踪文件内容。
- `changed-files.txt`：从 porcelain status 提取的变更文件列表，包含未跟踪文件名。
- `git-evidence.json`：稳定索引，包含 `available`、`workdir`、`gitRoot`、`branch`、`baseCommit`、`hasChanges`、`changedFileCount`、`generatedAt`、`error`。

如果当前目录不是 Git repo，handler 不会把 workflow 打崩；它会写入 `available=false` 的 `git-evidence.json`，让后续验收报告明确说明 Git evidence 不可用。

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
start -> status -> resume(Claude) -> resume(Codex) -> resume(decision) -> review approve
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
- artifact validation 仍是薄实现。
- context package 已经有薄实现，但还没有 token budget、摘要缓存、重要性排序。
- recovery gate 已有最小实现，但还没有交互式修复向导或自动分类。
- PR / notification 还没接入 Java 版。
