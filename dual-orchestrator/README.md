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
./scripts/smoke-test.sh --case baseline-basic --cleanup
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
  --task-file /tmp/dual-task.md \
  --variant full
```

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

## Operator Panel

启动本地 dashboard：

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

打开：

```text
http://localhost:8080/
```

Operator Panel 使用和 `dualctl trace` 相同的 ledger read model，并通过 SSE 订阅
overview 更新。它是本地单用户 operator 工具，不是托管式多用户 Web 产品。

## 文档入口

- [Quickstart](docs/quickstart.md) / [中文](docs/quickstart.zh-CN.md)
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

- 项目仍是 pre-1.0，workflow schema 和 prompt 还会演进。
- 真实运行依赖本地 `acpx`、Claude、Codex 可用性和额度。
- Public benchmark 会消耗真实 Agent quota。
- Delivery 自动化保持保守：准备证据和 PR 材料，但不会自动 merge。
- 正式公开发布前需要选择并添加 LICENSE。
