# dual-orchestrator 文档

English version: [README.md](README.md)

这里是当前产品文档和主题化设计文档入口。公开读者建议从“公开阅读路径”开始。历史 handoff、内部 benchmark 和按日期/阶段编号的过程文档已经从公开分支移除；可复用证据从 public benchmark sample 和短版 P0 closure report 开始。

## 公开阅读路径

| 目的 | 英文 | 中文 |
|---|---|---|
| 安装和第一次运行 | [quickstart.md](quickstart.md) | [quickstart.zh-CN.md](quickstart.zh-CN.md) |
| 停住或状态不一致时排查 | [troubleshooting.md](troubleshooting.md) | [troubleshooting.zh-CN.md](troubleshooting.zh-CN.md) |
| 项目为什么存在 | [whitepaper-mapping.md](whitepaper-mapping.md) | 暂未拆分中文对照 |
| 主线功能路线图 | [mainline-roadmap.md](mainline-roadmap.md) | [mainline-roadmap.zh-CN.md](mainline-roadmap.zh-CN.md) |
| 总体架构和执行流程 | [architecture-and-execution-flow.en.md](architecture-and-execution-flow.en.md) | [architecture-and-execution-flow.md](architecture-and-execution-flow.md) |
| Workflow 状态模型 | [workflow-state-machine.md](design/workflow-state-machine.md) | [workflow-state-machine.zh-CN.md](design/workflow-state-machine.zh-CN.md) |
| Delivery merge | [delivery-merge.md](design/delivery-merge.md) | [delivery-merge.zh-CN.md](design/delivery-merge.zh-CN.md) |
| Artifact contract 与上下文 | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| Failure recovery 与 calibration | [failure-recovery-and-calibration.md](design/failure-recovery-and-calibration.md) | [failure-recovery-and-calibration.zh-CN.md](design/failure-recovery-and-calibration.zh-CN.md) |
| Operator observability | [operator-observability.md](design/operator-observability.md) | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md) |
| 设计演进文章素材 | [design-evolution-notes.md](design/design-evolution-notes.md) | [design-evolution-notes.zh-CN.md](design/design-evolution-notes.zh-CN.md) |
| 当前功能状态 | [features.md](features.md) | 暂未拆分中文对照 |
| Public benchmark | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) | 暂未拆分中文对照 |
| Smoke / E2E | [testing/smoke-test-e2e.md](testing/smoke-test-e2e.md) | 暂未拆分中文对照 |
| 发布检查清单 | [release-checklist.md](release-checklist.md) | [release-checklist.zh-CN.md](release-checklist.zh-CN.md) |
| 完整参考手册 | [reference.md](reference.md) | 详细参考暂不做双语拆分 |

## 新用户路径

1. 读根目录 [README.md](../README.md) 或 [README.en.md](../README.en.md)。
2. 跟着 [quickstart.zh-CN.md](quickstart.zh-CN.md) 或 [quickstart.md](quickstart.md) 安装。
3. 运行：

```bash
./scripts/smoke-test.sh --case lifecycle --cleanup
```

4. 有真实 `acpx` Claude/Codex 可用时，运行：

```bash
./scripts/run-public-benchmark.sh
```

5. 用 `dualctl trace --workdir <effective_workdir> <workstream_id>` 查看结果。

## 设计文档路径

如果你要理解或修改 orchestrator 本身，建议按这个顺序读：

1. [architecture-and-execution-flow.md](architecture-and-execution-flow.md) / [English](architecture-and-execution-flow.en.md)
2. [mainline-roadmap.zh-CN.md](mainline-roadmap.zh-CN.md) / [English](mainline-roadmap.md)
3. [goal-driven-workstream-loop.zh-CN.md](design/goal-driven-workstream-loop.zh-CN.md) / [English](design/goal-driven-workstream-loop.md)
4. [execution-instance-model.zh-CN.md](design/execution-instance-model.zh-CN.md) / [English](design/execution-instance-model.md)
5. [workflow-state-machine.zh-CN.md](design/workflow-state-machine.zh-CN.md) / [English](design/workflow-state-machine.md)
6. [delivery-merge.zh-CN.md](design/delivery-merge.zh-CN.md) / [English](design/delivery-merge.md)
7. [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) / [中文](design/artifact-contracts-and-context.zh-CN.md)
8. [failure-recovery-and-calibration.md](design/failure-recovery-and-calibration.md) / [中文](design/failure-recovery-and-calibration.zh-CN.md)
9. [operator-observability.md](design/operator-observability.md) / [中文](design/operator-observability.zh-CN.md)
10. [features.md](features.md)

这几份文档共同说明当前 lifecycle、Definition / Instance / Execution 模型、状态模型、delivery merge、artifact/context 边界、自动恢复调度、operator 投影，以及剩余 MVP 缺口。

## 私有证据策略

私有真实 case 对工程判断很有价值，但不是公开 onboarding 路径。本公开分支只保留脱敏总结、public benchmark 和短版 P0 closure report。`testing/real-case-reports/` 是本地 ignored 输出，生成后不应提交。

对维护者仍有价值、但不适合放在主阅读路径里的过程文档，放在 `archive/`。
公开发布时 `archive/` 是可选维护者材料；当前文档不应依赖 archive 才能读懂。

## 设计演进笔记

[design-evolution-notes.md](design/design-evolution-notes.md) / [中文](design/design-evolution-notes.zh-CN.md) 保留了项目从 dual-agent script 演进到 engineering harness 的思考过程。它是文章素材，不是 runtime reference。
