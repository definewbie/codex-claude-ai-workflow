# dual-orchestrator

`dual-orchestrator` 是一个本地 Agent 生命周期编排器，用 `acpx` 驱动 Claude
和 Codex，把长周期研发任务拆成需求澄清、设计审视、计划、实现、自测、验收、修复和交付。

它的目标不是再做一个聊天入口，而是减少人在 Claude Code 和 Codex 之间反复复制粘贴产物的负担，让一个真实功能可以在可追踪、可暂停、可恢复的流程里跑完。

## 它解决什么问题

- Claude 负责主控：需求澄清、设计、实现、自测、变更说明。
- Codex 负责外部审视：产品、架构、最终用户视角的验收。
- `acpx` 负责统一调用 Agent，后续可以替换具体工具。
- 每个 workstream 默认在独立 Git worktree 中执行，避免污染原仓库。
- 所有状态、事件、Agent 输出、review packet、报告都写入本地 ledger。
- 人只在关键 gate 上做 approve / revise / complete。
- CLI 和 Operator Panel 都能回答：“现在跑到哪、为什么停、下一步做什么”。

## 当前能力

- Java / Maven 多模块工程。
- `dualctl` CLI。
- 默认 Git worktree 隔离。
- 文件账本：`.agent/workstreams/<workstream_id>/`。
- Workflow runtime：Task / Decision / Gate。
- Claude / Codex `acpx` Agent task。
- Human review gate 和 review packet。
- Failure classification、recovery、retry、repair loop。
- TaskContract / artifact contract。
- Context package 和 handoff / next-stage prompt。
- Real-case benchmark 和 smoke test。
- Operator Panel：首屏 overview、attention items、timeline、review packet。

## 安装

依赖：

```bash
java -version       # JDK 21+
mvn -version        # Maven 3.9+
git --version
acpx --version
```

安装 CLI：

```bash
cd dual-orchestrator
./scripts/install.sh
export PATH="$HOME/.local/bin:$PATH"
dualctl --help
```

## 快速开始

检查目标仓库：

```bash
dualctl doctor --workdir /path/to/repo
```

运行一个有边界的真实任务：

```bash
./scripts/run-real-case.sh \
  --repo /path/to/repo \
  --task-file /path/to/task.md \
  --variant full
```

查看当前跑到哪里：

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

`trace` 是排查和接手 workstream 的第一入口。它会显示：

- 当前状态和当前节点
- 为什么停下或完成
- 需要关注的 attention items
- 最近 timeline
- final acceptance 和 repair loops
- 关键 artifacts
- 下一步建议命令

## Public Benchmark

项目内置了一个公开、可复用的小型 benchmark app，适合验证真实生命周期而不依赖私有仓库：

```bash
./scripts/prepare-public-benchmark-repo.sh
```

脚本会把 `examples/issue-flow-app` 复制到 `/tmp` 下的临时 git repo，并打印可直接运行的 `run-real-case.sh` 命令。

## 人工 Review

当 workflow 停在 human gate：

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
```

常用短命令：

```bash
dualctl approve --run --workdir <effective_workdir> <workstream_id>
dualctl revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl complete --workdir <effective_workdir> <workstream_id>
```

完整命令仍然保留：

```bash
dualctl review approve --run --workdir <effective_workdir> <workstream_id>
dualctl review revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl review complete --workdir <effective_workdir> <workstream_id>
```

## Operator Panel

启动本地 API / dashboard：

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

打开：

```text
http://localhost:8080/
```

Operator Panel 的 overview 和 `dualctl trace` 使用同一套表达：当前节点、停止原因、下一步动作、attention items、最近 timeline、Agent runs 和关键 artifacts。

## 文档入口

- [Quickstart](docs/quickstart.md)
- [文档索引](docs/README.md)
- [总体架构与执行流程](docs/architecture-and-execution-flow.md)
- [Workflow 状态机](docs/design/workflow-state-machine.md)
- [当前功能状态](docs/features.md)
- [Real-case benchmark](docs/testing/real-case-benchmarks/README.md)
- [完整参考手册](docs/reference.md)

## 当前限制

- 项目仍是 pre-1.0，workflow schema 和 prompt 还会演进。
- 真实运行依赖本地 `acpx`、Claude、Codex 可用性。
- Operator Panel 是单机 operator 工具，不是多用户 Web 产品。
- Delivery 自动化保持保守：准备证据和 PR 材料，但不会自动 merge。
