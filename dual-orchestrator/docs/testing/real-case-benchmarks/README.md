# Real-case Benchmarks

Real-case benchmark 用来验证 dual-orchestrator 在真实仓库里的生命周期质量，不是用来替代单元测试或 fixture smoke。

## 目标

每个 benchmark case 要回答四个问题：

1. workflow 能不能跑到 `COMPLETED`。
2. 如果 Codex 拒绝，系统能不能分类失败并回到正确修复路径。
3. 人工 gate 的 review packet / timeline / artifact 是否足够判断 approve 或 revise。
4. 原始仓库是否保持干净，业务改动是否只发生在 worktree。

## 当前 Case Set

| Case | 类型 | 目的 | 状态 |
|------|------|------|------|
| `cases/cancer-clinic-small-ui.md` | Small UI change | 验证 p6 主生命周期和真实 repair loop | 2026-06-02 已通过 |
| `cases/cancer-clinic-feature-slice.md` | Medium feature slice | 验证 contract / context / self-test / acceptance 在中等功能切片上的质量 | 2026-06-03 修复后通过，`PASS-WARN` |
| `cases/cancer-clinic-rejection-prone.md` | Rejection-prone case | 验证 Codex `REJECT` 后的 failure classification 和 repair route | 2026-06-03 已通过，`PASS-WARN`；本轮未触发 REJECT，但暴露并修复 contract source bug |
| `cases/cancer-clinic-state-model-complex.md` | Complex bounded frontend case | 带着状态机文档验证 P6 gate / active node / completion 语义 | 2026-06-03 已通过，`PASS-WARN`；暴露 handoff JSON 抽取偏弱 |

## Benchmark Index

真实 case 跑完后，`run-real-case.sh` 会在 workstream ledger 里生成 `reports/workstream-summary.md`，并在 `docs/testing/real-case-reports/<timestamp>/summary.json` 写入机器可读摘要。用下面的脚本把所有摘要聚合成一个对比索引：

```bash
./scripts/real-case-benchmark-summary.sh
```

也可以在运行真实 case 时自动刷新索引：

```bash
./scripts/run-real-case.sh \
  --repo /Users/kriswang/work/projects/ai/cancer-clinic \
  --task-file dual-orchestrator/docs/testing/real-case-benchmarks/cases/cancer-clinic-small-ui.md \
  --variant p6 \
  --update-benchmark-index
```

输出：

```text
docs/testing/real-case-benchmarks/benchmark-index.md
```

这个索引用来快速观察：

- 每条 run 对应哪个 task/case
- 每条 run 的轻量分级：`PASS` / `PASS-WARN` / `FAIL`
- run 是否都到达 `COMPLETED`
- gate 轮数和 Agent run 次数是否异常增加
- Codex acceptance 是否出现 `REJECT`
- 原始 repo 是否保持干净
- final report / handoff / next-stage prompt / delivery PR body 是否齐全

它只统计包含 `summary.json` 的 run。早期没有 summary 的历史报告保留为人工参考，不做迁移补录。

### Result Grading

Benchmark index 的 `Grade` 是轻量体检结果，不替代人工读详细报告：

| Grade | 含义 |
|-------|------|
| `PASS` | workflow 完成、原始 repo 干净、关键 artifact 齐全、Codex 没有 notes/reject。 |
| `PASS-WARN` | workflow 完成且关键证据齐全，但存在注意事项，例如 `ACCEPT_WITH_NOTES` 或经历过 reject/repair。 |
| `FAIL` | workflow 未完成、原始 repo 被污染，或 final report / handoff / next-stage prompt / delivery body 等关键 artifact 缺失。 |

`Reason` 列给出触发分级的第一条原因。这个规则故意保持简单，目标是帮助比较真实 run 的健康度，不做复杂规则引擎。

`run-real-case.sh` 也会把同一套 grade 写入单次 `summary.json` 和 `report.md`。聚合脚本仍会按同一规则重新计算一次，所以早期没有 grade 字段的历史 summary 也能进入 index。

`--update-benchmark-index` 默认关闭，避免临时调试 run 自动改文档。需要把一次真实 run 纳入 benchmark set 时再显式开启。

## 运行方式

真实 case 使用现有脚本，不额外引入 benchmark runner：

```bash
./scripts/run-real-case.sh \
  --repo /Users/kriswang/work/projects/ai/cancer-clinic \
  --task-file dual-orchestrator/docs/testing/real-case-benchmarks/cases/cancer-clinic-small-ui.md \
  --variant p6
```

运行前确认：

- `mvn -pl dual-cli -am package` 可以通过。
- `acpx` 已安装并可用。
- Claude / Codex session 可创建或已存在。
- 目标仓库没有不相关的未提交改动。

## 报告要求

每次运行完成后，按 `templates/report-template.md` 补一份报告，放到：

```text
dual-orchestrator/docs/testing/real-case-benchmarks/reports/
```

报告至少记录：

- repo / workstream / effective workdir / ledger
- workflow variant 和 final status
- 总耗时
- gate rounds
- Agent run 次数
- Codex acceptance 决策过程
- reject / repair loop 次数
- failure classification 类型
- final diff stat
- 原始 repo 是否干净
- final report / workstream summary / handoff / delivery artifact 是否存在
- `summary.json` 是否存在，可用于后续趋势比较
- 人工观察：误修、漏修、过度实现、证据是否足够

## 最近通过记录

| Date | Case | Workstream | Result | Notes |
|------|------|------------|--------|-------|
| 2026-06-04 | `cancer-clinic-feature-slice` | `ws-20260604T102338Z-X9E4` | `PASS-WARN` | P6 主线完整跑通；经历 1 次 Codex `REJECT` 后进入 failure classification + Claude repair，第二次验收 `ACCEPT_WITH_NOTES`；暴露并修复 `run-real-case.sh` 报告收尾统计 bug。 |
| 2026-06-04 | `cancer-clinic-doc-constraints-smoke` | `ws-20260604T080522Z-LQ87` | `COMPLETED` | 文档边界约束小任务完整跑通；6 个 human gate、10 次 Agent run、Codex `ACCEPT_WITH_NOTES`；验证 acpx reconnect 完整 stdout 修复后真实生命周期仍可完成。 |
| 2026-06-04 | reconnect diagnostic run | `ws-20260604T063104Z-H878` | `START_FAILED` | 诊断 run 保留在 benchmark index 中作为失败样本；根因是 acpx 返回 reconnect exit 但 stdout 已完整，后续已修复并用 `cancer-clinic-doc-constraints-smoke` 复验通过。 |
| 2026-06-03 | `cancer-clinic-state-model-complex` re-run | `ws-20260603T101635Z-VT82` | `PASS-WARN` | 修复 handoff parser 后真实 acpx 路径复跑通过；`handoff.json` 已能提取 `completedWork` / `changedFiles` / `openRisks` / `verificationState`；本轮触发 1 次 intake revision iteration；发现 `.serena/` 未跟踪目录污染 worktree status，后续已在 adapter 层修复。 |
| 2026-06-03 | `cancer-clinic-state-model-complex` | `ws-20260603T080441Z-NDK2` | `PASS-WARN` | B4 新用户治疗记录引导完成；6 个 human gate、10 次 Agent run、31 个 timeline nodes，最终 `COMPLETED`；状态模型线性主链路表现正常，未触发 recovery/revision；`handoff.json` 存在但结构化字段为空，需要后续强化。 |
| 2026-06-03 | `cancer-clinic-rejection-prone` | `ws-20260603T065831Z-R255` | `PASS-WARN` | B2 保存后引导完成；Codex `ACCEPT_WITH_NOTES`，未触发 REJECT；real-case 暴露 `contract.compile` 错读 Codex review 中引用 artifact 的 bug，修复后同一 workstream retry 到 `COMPLETED`。 |
| 2026-06-03 | `cancer-clinic-feature-slice` | `ws-20260603T045849Z-RGEL` | `PASS-WARN` | Artifact-driven contract proposal 修复后完整跑到 `COMPLETED`；Codex acceptance 为 `ACCEPT_WITH_NOTES`，仍建议做 WeChat DevTools/device 验证。 |
| 2026-06-02 | `cancer-clinic-small-ui` | `ws-20260602T121712Z-V7WM` | `COMPLETED` | Codex acceptance 两次 `REJECT`，系统两次进入 failure classification + Claude repair，第三次验收通过。原始 repo 保持干净。 |

详细原始报告：

```text
dual-orchestrator/docs/testing/real-case-reports/20260602T121711Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260603T045848Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260603T065830Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260603T080441Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260603T101634Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260604T063104Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260604T080521Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260604T102337Z/report.md
```

## 何时运行

建议运行 real-case benchmark 的时机：

- 修改 workflow 主链路。
- 修改 acpx adapter、context package、artifact contract、failure arbiter。
- 修改 human gate / review packet / operator API。
- 准备宣布一段主功能“已验证”。

不建议每个小文档改动都跑真实 case。它会消耗时间和 Agent quota，应该服务于主功能质量，而不是制造仪式感。
