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
| `cases/cancer-clinic-rejection-prone.md` | Rejection-prone case | 验证 Codex `REJECT` 后的 failure classification 和 repair route | 待运行 |

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
- final report / handoff / delivery artifact 是否存在
- `summary.json` 是否存在，可用于后续趋势比较
- 人工观察：误修、漏修、过度实现、证据是否足够

## 最近通过记录

| Date | Case | Workstream | Result | Notes |
|------|------|------------|--------|-------|
| 2026-06-03 | `cancer-clinic-feature-slice` | `ws-20260603T045849Z-RGEL` | `PASS-WARN` | Artifact-driven contract proposal 修复后完整跑到 `COMPLETED`；Codex acceptance 为 `ACCEPT_WITH_NOTES`，仍建议做 WeChat DevTools/device 验证。 |
| 2026-06-02 | `cancer-clinic-small-ui` | `ws-20260602T121712Z-V7WM` | `COMPLETED` | Codex acceptance 两次 `REJECT`，系统两次进入 failure classification + Claude repair，第三次验收通过。原始 repo 保持干净。 |

详细原始报告：

```text
dual-orchestrator/docs/testing/real-case-reports/20260602T121711Z/report.md
dual-orchestrator/docs/testing/real-case-reports/20260603T045848Z/report.md
```

## 何时运行

建议运行 real-case benchmark 的时机：

- 修改 workflow 主链路。
- 修改 acpx adapter、context package、artifact contract、failure arbiter。
- 修改 human gate / review packet / operator API。
- 准备宣布一段主功能“已验证”。

不建议每个小文档改动都跑真实 case。它会消耗时间和 Agent quota，应该服务于主功能质量，而不是制造仪式感。
