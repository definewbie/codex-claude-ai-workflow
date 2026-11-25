# 快速开始

English version: [quickstart.md](quickstart.md)

这份指南帮助你从 fresh checkout 跑起本地 `dual-orchestrator` CLI。它聚焦最短开源路径，不覆盖完整生产部署。

## 1. 前置依赖

确认本机已有：

```bash
java -version       # JDK 21+
mvn -version        # Maven 3.9+
git --version
acpx --version
```

真实 lifecycle run 需要 `acpx` 能调用 Claude 和 Codex。Fixture/smoke 路径可以先验证 CLI，不必碰自己的仓库。

真实运行前要固定 adapter 版本。否则 `acpx` 可能解析到浮动的 Claude/Codex ACP adapter，导致同一个流程在不同时间表现不同：

```bash
DUAL_NODE_BIN_DIR=/path/to/node-22.13-or-newer/bin ./scripts/install-acpx-e1.sh
export DUAL_ACPX_EXECUTABLE="$PWD/.tools/acpx-e1/bin/acpx"
./scripts/configure-acpx.sh
```

如果 Claude Code 使用 GLM、DeepSeek、one-api、claude-code-router 等
Anthropic-compatible 后端，把 provider 变量放在 `~/.claude/settings.json`
的 `env` 字段里即可。`configure-acpx.sh` 会安装一个很薄的 Claude
adapter wrapper，在运行时只读取固定的 Anthropic provider allowlist，
不会把 token 写进 `~/.acpx/config.json`。当前只证明了 policy transport
不可绕过；真实 sandbox/network 效果仍需单独 capability gate 验证。

## 2. 安装

```bash
cd dual-orchestrator
./scripts/install.sh
```

默认安装到 `$HOME/.local`。

如果希望安装 wrapper 的同时写入固定的 acpx Claude/Codex adapter 配置：

```bash
./scripts/install.sh --configure-acpx
```

如有需要，把安装目录加入 `PATH`：

```bash
export PATH="$HOME/.local/bin:$PATH"
```

验证：

```bash
dualctl --help
```

## 3. 检查目标仓库

真实运行前，先检查目标 Git repo：

```bash
dualctl doctor --workdir /path/to/git/repo
```

`doctor` 会检查基础依赖。真实 Agent run 还需要可用的 acpx Claude/Codex session；缺失 session 通常由 acpx 按需创建。
它也会检查 `acpx` 版本和两个 ACP adapter 是否固定，避免使用浮动 npm range。
如果 Claude settings 中配置了 `ANTHROPIC_BASE_URL` 和 token，它还会检查
Claude ACP command 是否能拿到这些变量。

如果 `doctor`、acpx 或后续 Agent run 失败，先看
[troubleshooting.zh-CN.md](troubleshooting.zh-CN.md)，不要直接修改 workflow 逻辑。

## 4. 运行最小 Smoke

在 `dual-orchestrator` 目录下运行：

```bash
rtk proxy mvn -q clean test
```

这是离线全模块回归，包含使用程序观察的工作流序列。旧 smoke 已退役；全绿不证明真实原生能力或目标 Git 交付。

## 5. 运行真实 Lifecycle Case

最容易复用的真实 run 是公开 benchmark：

```bash
./scripts/run-public-benchmark.sh
```

如果想先查看公开可复用 case：

```bash
./scripts/run-public-benchmark.sh --list-cases
```

它会在 `/tmp` 下创建一次性 issue-flow sample repo，用真实 `acpx` Claude/Codex 跑完整 lifecycle，自动 approve benchmark 中间 gate，并把报告写到：

```text
docs/testing/real-case-reports/<timestamp>/
```

这个目录是本地 ignored evidence，不应该提交。

如果要指定 sample repo 位置：

```bash
./scripts/run-public-benchmark.sh --dest /tmp/dual-issue-flow-benchmark
```

如果要在自己的仓库跑，直接使用 `run-real-case.sh`：

```bash
cat > /tmp/dual-task.md <<'EOF'
Update the README with a short "Local Development" section.
Keep the change documentation-only. Do not modify source code.
EOF

./scripts/run-real-case.sh \
  --repo /path/to/your/repo \
  --task-file /tmp/dual-task.md
```

Agent 提示词默认使用 `--prompt-language auto`，会根据任务文本选择人类可读输出语言。需要强制指定时：

```bash
./scripts/run-real-case.sh \
  --repo /path/to/your/repo \
  --task-file /tmp/dual-task.md \
  --prompt-language zh-CN
```

该选项只影响 Agent 输出中的 Markdown/说明语言；JSON keys、decision values、文件路径和 contract markers 保持不变。

`full` lifecycle 是默认流程。只有在明确要跑更小的 contract-only 流程时，才使用
`--variant contract`。

建议先用小任务。公开可复用 benchmark case 使用 `public-*.md` 命名。

## 6. 查看结果

运行结束后：

```bash
cat docs/testing/real-case-reports/<timestamp>/report.md
jq . docs/testing/real-case-reports/<timestamp>/summary.json
cat docs/testing/real-case-benchmarks/benchmark-index.md
```

结果语义：

- `PASS`：lifecycle 完成，并有强证据。
- `PASS-WARN`：lifecycle 完成，但仍有注意事项，例如 Codex `ACCEPT_WITH_NOTES` 或 verification confidence 不高。
- `FAIL`：workflow 停止、原始 repo 被污染，或关键 artifacts 缺失。

## 7. Trace 一个 Workstream

使用 start 或 benchmark 输出的 effective workdir 和 workstream id：

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

`trace` 是最推荐的第一入口。它会显示当前状态、active node、为什么停止、attention items、最近 timeline、final acceptance、repair loops、关键 artifacts 和下一步命令。

如果某个历史 Agent run 的 `run.json` 缺失或损坏，`trace` 会显示
`Run Metadata Warnings`。这表示观测数据不完整，不代表 workflow 终态被改成失败；
需要排查单次 run 时，再使用 `dualctl inspect --run <run_id>`。

如果 workflow 等待人工 review：

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
```

`dualctl approve`、`dualctl revise`、`dualctl complete` 是较长 `dualctl review ...` 命令的快捷方式。

## 8. 打开本地控制台

启动 operator server：

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

默认任务控制台：

```text
http://localhost:8080/
```

任务控制台面向最终用户和 reviewer，显示生命周期进度、当前停止原因、下一步动作、人审证据、
token 摘要和恢复建议。

控制台使用本机多项目 registry。首页输入本地 Git 仓库根目录后，点击“打开任务池”，
server 会校验路径并写入：

```text
~/.dual/operator-workspaces.json
```

之后首页会记住该项目，并发现它的默认 worktree 池：

```text
<repo-parent>/<repo-name>-agent-runs/
```

这样 CLI 或自动流程已经启动的 workstream 也会出现在任务列表中。每个任务的状态、动作和
trace 链接仍然以自己的 effective workdir / ledger 为准。

管理诊断入口：

```text
http://localhost:8080/mgt/index.html
```

管理诊断面向 dual 使用者和开发者，用于查看 raw ledger、node id、RCA、CLI 命令和恢复证据。

Token 指标有三种来源：

- `acpx_stream`：真实 provider usage，包含 input/output 拆分。
- `acpx_usage_update`：只读到 ACP total used/size，能展示总量，不能拆 provider input/output。
- `artifact_estimate`：缺少 acpx usage 或旧 run 时，根据 prompt/output/transcript metadata
  估算的观察值。

两者都只用于成本和上下文观察；workflow 路由仍然依赖结构化 contract、typed event 和 recovery signal。

如果要检查本机旧 worktree，但暂时不删除任何内容：

```bash
./scripts/agent-runs-cleanup-plan.sh --repo /path/to/repo
```

先检查输出里的 terminal rows，再按需执行打印出的 `git worktree remove ...` 命令。

## 9. 可选本机 Worker 模式

默认情况下，`run` 会内联执行当前 active node：

```bash
dualctl run --workdir <effective_workdir> <workstream_id>
```

AgentTask/worker MVP 可以先把一个 active Agent task 放入队列，再由本机 worker
执行一个 queued task：

```bash
dualctl run --dispatch-mode WORKER --workdir <effective_workdir> <workstream_id>
dualctl worker run-once --workdir <effective_workdir> <workstream_id>
```

这个模式仍然由 runtime ledger 负责 workflow definition 和 routing。worker 只是一个本机执行器，一次只处理一个 queued AgentTask；它还不是多 worker 平台。

## 10. 合并已验收的 Worktree 改动

workstream 完成并验收通过后，显式把已审查的 worktree diff 合回原始仓库：

```bash
dualctl delivery merge \
  --workdir <source_worktree> \
  --target <original_repo> \
  <workstream_id>
```

命令要求 workstream 已完成、final acceptance 是 `ACCEPT` 或
`ACCEPT_WITH_NOTES`，并且 target repo 工作区干净。它会在 workstream ledger 下写入
`delivery/merge-plan.json`、`delivery/merge.patch`、changed file list 和 apply log。
它只把 patch apply 到 target repo index，不会自动 commit。

人工确认 staged diff 后：

```bash
cd <original_repo>
git diff --cached
git commit -m "feat: dual delivery <workstream_id>"
```

## 10. Calibration

真实失败如果被分类为可复用校准候选，可以先人工审阅：

```bash
dualctl calibration list --workdir <effective_workdir> <workstream_id>
dualctl calibration show --workdir <effective_workdir> <workstream_id> <candidate_id>
dualctl calibration decide --status accepted --workdir <effective_workdir> <workstream_id> <candidate_id>
dualctl calibration plan --workdir <effective_workdir> <workstream_id> <candidate_id>
dualctl calibration promote --workdir <effective_workdir> <workstream_id> <candidate_id>
```

MVP 的 promotion 很窄：只会把 accepted 的 `CONTRACT_TEMPLATE` plan 追加到项目级 `.agent/templates/contract-review-principles.md`。它不会自动修改内置 prompt、workflow、context rule 或测试。

更深入的排查命令：

```bash
dualctl status --workdir <effective_workdir> <workstream_id>
dualctl timeline --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl events --workdir <effective_workdir> --limit 50 <workstream_id>
dualctl summary --workdir <effective_workdir> <workstream_id>
```

`dualctl runs` 也是只读观测命令。它会列出可读取的 Agent run，并把损坏或缺失的
run metadata 显示为 warning，避免一个历史文件问题遮蔽当前 workflow 状态。

如果 workflow 进入 recovery：

```bash
dualctl recovery show --workdir <effective_workdir> <workstream_id>
```

ledger 位于：

```text
<effective_workdir>/.agent/workstreams/<workstream_id>/
```

## 11. P0 已知限制

2026-06-19 的历史 MVP 曾以 `Done with known limits` 收口；详见
[testing/p0-closure-report.md](testing/p0-closure-report.md)。
当前生命周期收敛仍是 B03 `IN_PROGRESS`、真实运行 `NO-GO`，以
[主工作清单](plans/20260927-dual-lifecycle-worklist.zh-CN.md) 为准。
历史 smoke 不能作为当前准入证据，也不授权 P1 工作。

P0 后接受的限制：

- 如果运行时证据足够但没有 live app smoke，final acceptance 可能是
  `ACCEPT_WITH_NOTES`，不一定是干净的 `ACCEPT`；
- `full` lifecycle 仍然有较多 human gates；
- context/provider metrics 已能支持 MVP，但 token/cost 治理还会在 P1 继续强化；
- delivery merge 是显式保守动作：工具把已审查 patch apply 到目标 repo index，
  不自动 commit，也不自动 merge。

### Delivery interruption / 交付中断

正常顺序是批准候选 → `delivery merge` → 人检查目标暂存内容并实际 commit → `delivery finalize`。UNKNOWN 时先查看 `status`/`summary` 的停止依据与下一步，不盲目再次应用；使用当前裁定模板经 `delivery resolve --decision <decision.json>` 显式关闭再次应用，或核实旧操作停止后授权一次新尝试。已知活动不得被人工声明覆盖；声明不等于测量退出码。详见 [reference](reference.md) 的交付裁定入口。
