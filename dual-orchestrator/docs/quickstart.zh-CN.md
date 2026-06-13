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

## 2. 安装

```bash
cd dual-orchestrator
./scripts/install.sh
```

默认安装到 `$HOME/.local`。如有需要，把它加入 `PATH`：

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

## 4. 运行最小 Smoke

在 `dual-orchestrator` 目录下运行：

```bash
./scripts/smoke-test.sh --case baseline-basic --cleanup
```

这是最快的 CLI/workflow sanity check。

## 5. 运行真实 Lifecycle Case

最容易复用的真实 run 是公开 benchmark：

```bash
./scripts/run-public-benchmark.sh
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
  --task-file /tmp/dual-task.md \
  --variant full
```

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

如果 workflow 等待人工 review：

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
```

`dualctl approve`、`dualctl revise`、`dualctl complete` 是较长 `dualctl review ...` 命令的快捷方式。

## 8. Calibration

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

如果 workflow 进入 recovery：

```bash
dualctl recovery show --workdir <effective_workdir> <workstream_id>
```

ledger 位于：

```text
<effective_workdir>/.agent/workstreams/<workstream_id>/
```
