# Benchmark Case: cancer-clinic-small-ui

## 类型

Small UI change

## 目标

在 `cancer-clinic` 项目中完成一个低风险真实任务，用于验证 dual-orchestrator p6 lifecycle 能在真实仓库跑完，并能处理 Codex acceptance rejection 后的修复循环。

## 任务

请阅读当前项目结构和相关文档，选择一个低风险的小改动完成：

- 优先选择 README、文档、或一个不会影响核心业务逻辑的小型前端说明改进。
- 不要修改数据库 schema。
- 不要改动后端核心接口。
- 不要扩大需求。
- 不要做无关重构。

## 验收重点

1. 先澄清需求并形成 TaskContract。
2. 经过设计审视和计划审视。
3. 实现后由 Claude 输出自测报告。
4. 采集 Git evidence。
5. Codex 做最终验收。
6. 如果 Codex `REJECT`，系统应进入 failure classification，并路由回 Claude repair。
7. 生成 final report、handoff.json、next-stage prompt、delivery prepare artifact。
8. 不要自动提交，不要创建 PR。

## 最近运行结果

| 字段 | 值 |
|------|----|
| Date | 2026-06-02 |
| Workstream | `ws-20260602T121712Z-V7WM` |
| Variant | `p6` / `agent-lifecycle-p6@3` |
| Final status | `COMPLETED` |
| Repair loop | Codex acceptance 两次 `REJECT`，两次 failure classification + Claude repair，第三次验收通过 |
| Diff stat | `miniprogram/src/pages/treatment-event/index.vue | 4 ++++` |
| 原始 repo | 干净 |
| Report | `dual-orchestrator/docs/testing/real-case-reports/20260602T121711Z/report.md` |

## 适用场景

适合在修改 runtime、failure arbiter、artifact contract、context package、acpx adapter 后做一次低风险真实回归。
