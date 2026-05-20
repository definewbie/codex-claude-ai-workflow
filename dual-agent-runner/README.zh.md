# dual-agent-runner 中文说明

[English README](README.md)

`dual-agent-runner` 是一个最小化 Bash 双 Agent 自动化工作流工具，可在代码仓库中运行。

- Claude 是主控 Agent：负责 PRD 分析、架构设计、实现、测试和变更报告。
- Codex 是外部审视者：负责产品、架构、最终用户验收和质量门禁。
- `acpx` 是统一 Agent 调用入口。
- `.agent/runs/<run_id>/` 是运行账本。
- Git diff、测试退出码、日志、PR 信息和通知日志都会作为证据保存。

工具保持小而清晰：不提供 Web UI、不引入数据库、不自动 merge PR、不接入 Slack/飞书/Notion 专用 API、不做多轮自动修复。

## 安装

依赖：

- Bash
- 默认 worktree 模式需要 Git
- `jq`
- `acpx`
- 仅在使用 `--create-pr` 时需要 `gh`

macOS：

```bash
brew install jq gh
chmod +x dual-agent-runner/bin/dual
export PATH="$PWD/dual-agent-runner/bin:$PATH"
```

## 初始化

```bash
dual init
```

在目标仓库中运行一次。它会生成项目侧的 Agent 规则和配置文件：

```text
CLAUDE.md
AGENTS.md
.agent/config.json
```

`dual init` 不会创建 run ledger，也不会调用 Agent。它会把默认 acpx session 名写入 `.agent/config.json`；后续执行 `dual "任务描述"` 时，主流程会读取这些 session 名。

默认配置：

```json
{
  "claude_session": "claude-main",
  "codex_session": "codex-reviewer",
  "max_review_rounds": 1,
  "default_test_command": "",
  "artifact_retention": "keep",
  "worktree": {
    "enabled": true,
    "base_dir": "..",
    "branch_prefix": "ai"
  },
  "pr": {
    "enabled": false,
    "base": "main",
    "draft": true
  },
  "notification": {
    "enabled": false,
    "command": "",
    "on": "failure"
  }
}
```

主命令会读取 `.agent/config.json`，但命令行参数优先级更高。现在默认是安全 worktree 模式；只有传 `--in-place` 才会原地执行。

## acpx 准备

运行真实任务前，需要确认 `acpx` 已安装、已登录或授权，并且能启动 Claude 和 Codex。默认 session 名是：

```text
claude-main
codex-reviewer
```

正常使用 `dual` 时，用户不需要自己准备 prompt 文件。`dual` 会在每次运行时自动渲染 prompt 到：

```text
.agent/runs/<run_id>/prompts/
```

然后通过以下命令调用 Agent：

```bash
acpx claude -s <claude_session> -f <prompt_file>
acpx codex -s <codex_session> -f <prompt_file>
```

可选的手工健康检查：

```bash
printf 'Say OK and exit.\n' > /tmp/dual-acpx-check.md
acpx claude -s claude-main -f /tmp/dual-acpx-check.md
acpx codex -s codex-reviewer -f /tmp/dual-acpx-check.md
```

这里的 `/tmp/dual-acpx-check.md` 只是一个临时测试 prompt，不是 `dual` 的运行产物。它的作用只是确认 `acpx` 能用指定 session 成功调用两个 Agent。

## 默认安全模式

默认执行会自动创建 git worktree，并在 worktree 中运行完整流程：

```bash
dual "给 README 增加一段项目说明"
```

默认分支：

```text
ai/<run_id-slug>
```

默认 worktree 目录：

```text
<repo-parent>/<repo-name>-agent-runs/<run_id-slug>
```

所有 Agent 调用、研发自测报告、可选 runner 验证、Git 快照和 run ledger 都写入 effective workdir。原始仓库不应产生业务代码修改。

`--worktree` 仍然保留用于兼容旧脚本；worktree 模式现在已经是默认行为：

```bash
dual --worktree "给 README 增加一段项目说明"
```

## 基本用法

常规运行：

```bash
dual "实现这里描述的任务"
```

额外指定 runner 级强制验证命令：

```bash
dual --test "npm test" "实现这里描述的任务"
```

不传 `--test` 时，Claude 仍需要作为 developer 自主选择测试或检查方式，并在实现输出中提交 Developer Self-Test Report。Codex 最终验收会像 verifier 一样审查这份自测报告是否足够。

指定其他仓库：

```bash
dual --workdir /path/to/repo --test "npm test" "实现这里描述的任务"
```

Dry-run 只生成账本和 prompts，不调用 `acpx`：

```bash
dual --dry-run "实现这里描述的任务"
```

原地执行：

```bash
dual --in-place "给 README 增加一段项目说明"
```

在 `main` 或 `master` 上原地执行默认会被拒绝；如确实需要，必须显式允许：

```bash
dual --in-place --allow-main-in-place "给 README 增加一段项目说明"
```

指定分支：

```bash
dual --branch ai/my-feature "任务描述"
```

指定 worktree 目录：

```bash
dual --worktree-dir ../my-feature-worktree "任务描述"
```

复用已有 worktree：

```bash
dual --attach-existing --worktree-dir ../my-feature-worktree "任务描述"
dual --attach-existing --branch ai/my-feature "任务描述"
```

非 Git 目录不能使用默认 worktree 模式。传 `--in-place` 可以运行，但 Git evidence 会标记为 unavailable。

## 清理

`dual` 不会自动删除 worktree。

```bash
dual cleanup --worktree-dir <path>
dual cleanup --worktree-dir <path> --delete-branch
```

也可以手动清理：

```bash
git worktree list
git worktree remove <worktree_dir>
git branch -D <branch>
```

## PR 创建

默认不会创建 PR。只有传入 `--create-pr` 才会创建：

```bash
dual --create-pr "任务描述"
```

因为默认就是 worktree 模式，所以这会自然运行在自动创建的 worktree 中。

可选参数：

```bash
dual --create-pr --pr-base main "任务描述"
dual --create-pr --pr-title "PR 标题" "任务描述"
dual --create-pr --pr-draft "任务描述"
```

创建 PR 前会检查：

- effective workdir 是 git repo
- 存在 `gh`
- 当前分支不是 `main` 或 `master`
- 存在业务代码变更或已有分支提交
- 存在可识别的 `origin` remote

如果存在未提交业务变更，会自动创建 commit：

```text
feat: dual agent task <run_id>

<task summary>

Run report: .agent/runs/<run_id>/final-report.md
```

PR 创建失败不会丢失 run ledger，`final-report.md` 会记录失败状态和 stderr 尾部。

## 通知

默认不通知。传入 `--notify-command` 后才执行通知命令：

```bash
dual \
  --notify-command 'echo "$DUAL_STATUS $DUAL_RUN_ID $DUAL_REPORT_PATH"' \
  --notify-on always \
  "任务描述"
```

通知命令可读取：

```bash
DUAL_RUN_ID
DUAL_STATUS
DUAL_TASK
DUAL_WORKDIR
DUAL_ORIGINAL_WORKDIR
DUAL_EXECUTION_MODE
DUAL_WORKTREE_DIR
DUAL_BRANCH
DUAL_REPORT_PATH
DUAL_FINAL_DECISION
DUAL_TEST_EXIT_CODE
DUAL_PR_URL
```

通知失败不会改变主流程退出码。

## Run Ledger

每次运行都会在 effective workdir 生成：

```text
.agent/runs/<run_id>/
  run.json
  events.jsonl
  00-user-request.md
  prompts/
  outputs/
  steps/
  snapshots/
  verification/
    developer-self-test.md
    runner-test-command.txt
    runner-test-output.log
    verification.json
  pr/
  notification/
  final-report.md
```

`final-report.md` 包含执行环境、review 命令、cleanup 命令、timeline、Agent step summary、Developer Self-Test、可选 runner verification、PR 状态、notification 状态和 Codex final acceptance 摘要。

每个事件都有稳定顶层 schema：

```json
{
  "ts": "2026-05-19T00:00:00Z",
  "run_id": "run-id",
  "actor": "dual",
  "event": "event_name",
  "step": null,
  "session": null,
  "prompt_path": null,
  "output_path": null,
  "exit_code": null,
  "duration_ms": null,
  "data": null
}
```

## Prompt 人格与设计原则

`dual-agent-runner/prompts/` 中的 4 个模板不是聊天风格设定，而是工作流合约。它们用 persona 来约束每个阶段的关注点、边界和输出格式。

默认分工：

- `01-claude-prd-architecture.md`：Claude 是 `Staff Engineer + Product-minded Implementer`，负责把用户请求拆成 PRD、需求 ID、架构计划、测试策略和实现清单。
- `02-codex-review.md`：Codex 是 `Product Manager + Architect + User Proxy`，负责在实现前审视方案完整性、架构一致性、用户验收风险和 Must Fix。
- `03-claude-implement.md`：Claude 是 `Conservative Maintainer`，负责做最小安全实现，优先处理 Codex Must Fix，不做无关重构，并提交 Developer Self-Test Report。
- `04-codex-final-acceptance.md`：Codex 是 `Verifier + Release Gatekeeper + User Acceptance Reviewer`，负责基于用户请求、Git diff、研发自测报告、可选 runner 验证和 Agent 输出做最终验收。

设计原则：

- persona 服务于责任边界，不服务于表演。
- Claude 是唯一允许修改业务代码的 Agent。
- Claude 作为 developer 自主决定如何测试，并说明测试方法、结果、失败和未覆盖区域。
- Codex 只做外部审视和验收，不直接修改业务代码；它需要判断 Claude 自测是否充分。
- prompt 要短而硬：明确输入、约束、输出 contract 和质量门禁。
- 可以调整检查项和语气，但不要打破阶段职责。
- Codex prompt 的第一条 `Decision:` 行是 Bash quality gate 的解析入口，必须保持稳定。

## Developer Self-Test 与 Runner Verification

默认情况下，`dual` 不会替 Agent 猜测试命令。Claude 作为 developer 会根据仓库和改动自己选择测试方式，并在 `outputs/03-claude-implement.md` 中写出 Developer Self-Test Report。runner 会把这份报告归档到：

```text
verification/developer-self-test.md
```

Codex final acceptance 会把它当作研发自测证据来审查：测试是否相关、是否覆盖需求、未测区域是否可接受。

`--test` 是可选的 runner 级强制验证命令。传入后，runner 会在 Claude 实现后额外执行该命令，并保存：

```text
verification/runner-test-command.txt
verification/runner-test-output.log
```

因此：

- 不传 `--test`：使用 Developer Self-Test + Git evidence + Codex verifier。
- 传 `--test`：在上述基础上额外增加 runner-level verification。

## 质量门禁

Codex 设计审视输出：

```text
Decision: PASS
Decision: NEEDS_REVISION
Decision: BLOCKED
```

`BLOCKED` 会在实现前停止，并将 run 标记为 `blocked`。

Codex 最终验收输出：

```text
Decision: ACCEPT
Decision: ACCEPT_WITH_NOTES
Decision: REJECT
```

`REJECT` 会将 run 标记为 `rejected` 并以退出码 `2` 退出。

如果传入 `--test` 且 runner 验证命令失败，除非传 `--skip-final-acceptance`，仍然会执行最终验收。未传 `--allow-test-failure` 时最终退出码为 `3`。

## 退出码

- `0`：成功、验收通过、dry-run 完成，或 runner 验证失败被允许
- `1`：参数、依赖、受保护分支原地执行或未知运行失败
- `2`：Codex 最终验收 `REJECT`
- `3`：runner 验证命令失败且未传 `--allow-test-failure`
- `4`：Codex 设计审视 `BLOCKED`
- `5`：worktree 创建或复用失败
- `6`：PR 创建失败

通知失败不会改变退出码。

## Smoke Test

默认 worktree：

```bash
dual "给 README 增加一段项目说明"
```

显式 worktree 兼容：

```bash
dual --worktree "给 README 增加一段项目说明"
```

原地执行：

```bash
dual --in-place "给 README 增加一段项目说明"
```

受保护分支：

```bash
dual --in-place "给 README 增加一段项目说明"
```

允许 main 原地执行：

```bash
dual --in-place --allow-main-in-place "给 README 增加一段项目说明"
```

非 Git 默认模式：

```bash
dual "任务描述"
```

非 Git 原地执行：

```bash
dual --in-place "任务描述"
```

PR：

```bash
dual --create-pr "任务描述"
```

通知：

```bash
dual \
  --notify-command 'echo "$DUAL_EXECUTION_MODE $DUAL_WORKDIR $DUAL_WORKTREE_DIR"' \
  --notify-on always \
  "任务描述"
```

## 限制

- 不接入 Slack / 飞书 / Notion 专用 API。
- 不引入数据库。
- 不引入 Web UI。
- 不自动 merge PR。
- 不自动删除 worktree。
- 不做多轮自动修复。
- PR 创建不是默认行为。
- 不会静默从 worktree 降级为 in-place。
- 除非显式传 `--allow-main-in-place`，否则拒绝在 `main/master` 上原地执行。
