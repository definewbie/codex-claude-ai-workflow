# dual-orchestrator

English version: [README.en.md](README.en.md)

`dual-orchestrator` 是一个本地 Agent 生命周期编排器。它用 `acpx` 驱动
Claude 和 Codex，把一个研发任务放进可追踪、可暂停、可恢复的流程里：需求澄清、
设计审视、计划、实现、自测、验收、修复、交付和 handoff。

它不是聊天入口，也不是 CI 替代品。它解决的是另一个更日常的问题：人在 Claude
Code 和 Codex 之间反复复制产物、追问状态、整理证据，最后还要靠记忆判断“现在跑到哪了”。

## 核心思想

- Claude 是主控 developer / architect，负责设计、实现、自测和交接。
- Codex 是外部 reviewer / verifier，负责产品、架构和最终验收视角。
- `acpx` 是 Agent 通讯边界，具体工具未来可以替换。
- 默认使用 Git worktree 隔离每个 workstream，避免污染原仓库。
- 所有状态、事件、Agent 输出、review packet、报告和交付材料写入本地 ledger。
- 人只在明确的 human gate 上 approve / revise / complete。
- CLI 和 Operator Panel 都回答同一个问题：现在跑到哪，为什么停，下一步做什么。

## 当前能力

- `dualctl` CLI 和本地 Operator Panel。
- Task / Decision / Gate workflow runtime。
- LiteFlow 默认 routing kernel，内置 kernel 可回退。
- Claude / Codex `acpx` Agent task。
- TaskContract、artifact contract、context package、handoff / next-stage prompt。
- Failure classification、recovery、retry、bounded repair loop。
- Final report、delivery PR body、commit message。
- Public smoke test 和 real-case benchmark。

## 安装

依赖：

```bash
java -version       # JDK 21+
mvn -version        # Maven 3.9+
git --version
acpx --version
```

安装：

```bash
cd dual-orchestrator
./scripts/install.sh
export PATH="$HOME/.local/bin:$PATH"
dualctl --help
```

## 5 分钟验证

先跑 controlled smoke：

```bash
./scripts/smoke-test.sh --case lifecycle --cleanup
```

再跑公开 real-case benchmark：

```bash
./scripts/run-public-benchmark.sh
```

这个命令会复制 `examples/issue-flow-app` 到 `/tmp`，用真实 `acpx` Claude/Codex
跑完整 lifecycle，并把本地报告写到 ignored 目录：

```text
docs/testing/real-case-reports/<timestamp>/
```

公开 benchmark index 只保留可提交的聚合结果：

```text
docs/testing/real-case-benchmarks/benchmark-index.md
```

## 在自己的仓库运行

先检查目标仓库：

```bash
dualctl doctor --workdir /path/to/repo
```

准备一个边界清晰的小任务：

```bash
cat > /tmp/dual-task.md <<'EOF'
Update the README with a short "Local Development" section.
Keep the change documentation-only. Do not modify source code.
EOF

./scripts/run-real-case.sh \
  --repo /path/to/repo \
  --task-file /tmp/dual-task.md
```

Agent 提示词默认根据任务语言自动选择中文或英文。需要强制指定时：

```bash
./scripts/run-real-case.sh \
  --repo /path/to/repo \
  --task-file /tmp/dual-task.md \
  --prompt-language zh-CN
```

该选项只影响 Agent 输出中的人类可读 Markdown/说明语言；JSON keys、decision enum、文件路径和 contract marker 仍保持机器可读格式。

`full` lifecycle is the default. Use `--variant contract` only when intentionally
running the smaller contract-only workflow.

脚本默认创建安全 worktree，并自动通过 benchmark 用途的中间 gate。真实交付前仍应阅读 review packet 和 final report。

## 查看状态

优先使用：

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

`trace` 会显示当前状态、active node、停止原因、attention items、最近 timeline、
final acceptance、repair loops、关键 artifacts 和下一步命令。

如果停在 human gate：

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
dualctl revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl complete --workdir <effective_workdir> <workstream_id>
```

## Operator Console

启动本地 operator server：

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

默认打开任务控制台：

```text
http://localhost:8080/
```

Operator Console 面向最终用户和 reviewer，优先回答“跑到哪、为什么停、下一步做什么”，
并把 human gate 证据、token 摘要和恢复建议放在同一屏。

控制台使用本机多项目 registry 作为入口索引。用户在首页输入本地 Git 仓库根目录后，
server 会校验路径并写入：

```text
~/.dual/operator-workspaces.json
```

之后首页会默认展示最近项目，并发现该项目的默认 worktree 池：

```text
<repo-parent>/<repo-name>-agent-runs/
```

因此 CLI 或自动流程已经启动的 workstream 也会出现在任务列表中；每个任务仍以自己的
effective workdir / ledger 为准。registry 只负责“有哪些项目/任务入口”，不负责 workflow 状态。

旧入口仍保留为管理诊断页面：

```text
http://localhost:8080/mgt/index.html
```

管理诊断面向 dual 使用者和开发者，用于查看 ledger、raw facts、node id、RCA、CLI 命令和恢复证据。
两套页面读取同一组 ledger read model，并通过 SSE 订阅 overview 更新。它们都是本地单用户工具，
不是托管式多用户 Web 产品。

Token 用量有两种来源：`acpx_stream` 表示从 acpx session stream 读到的真实 usage；
`artifact_estimate` 表示旧 run 或缺失 stream usage 时，根据 prompt/output/transcript metadata
估算的观察值。它们都只用于成本和上下文观察，不参与 workflow 路由。

如果需要清理本机历史 worktree，先生成只读计划：

```bash
./scripts/agent-runs-cleanup-plan.sh --repo /path/to/repo
```

脚本只打印候选 worktree 和 `git worktree remove` 命令，不会自动删除。

## 文档入口

- [Quickstart](docs/quickstart.md) / [中文](docs/quickstart.zh-CN.md)
- [Troubleshooting](docs/troubleshooting.md) / [中文](docs/troubleshooting.zh-CN.md)
- [文档索引](docs/README.md) / [中文](docs/README.zh-CN.md)
- [总体架构与执行流程](docs/architecture-and-execution-flow.md)
- [Workflow 状态机](docs/design/workflow-state-machine.md)
- [Artifact Contract 与上下文](docs/design/artifact-contracts-and-context.md)
- [Failure Recovery 与 Calibration](docs/design/failure-recovery-and-calibration.md)
- [Operator Observability](docs/design/operator-observability.md)
- [当前功能状态](docs/features.md)
- [Real-case benchmark](docs/testing/real-case-benchmarks/README.md)
- [发布检查清单](docs/release-checklist.md)
- [完整参考手册](docs/reference.md)

## 当前限制

- P0 主生命周期已闭环，但状态是 `Done with known limits`，详见
  [P0 Closure Report](docs/testing/p0-closure-report.md)。
- 项目仍是 pre-1.0，workflow schema 和 prompt 还会演进。
- 真实运行依赖本地 `acpx`、Claude、Codex 可用性和额度。
- Public benchmark 会消耗真实 Agent quota。
- Delivery 自动化保持保守：准备证据和 PR 材料，但不会自动 merge。
- Operator Console / Management Diagnostics 仍是本地 operator 工具，human gate 体验和 gate 数量还会继续优化。
- 项目使用 MIT License；贡献流程和安全报告入口仍需在公开协作前补齐。
