# Benchmark Case: cancer-clinic-feature-slice

## 类型

Medium feature slice

## 目标

验证 dual-orchestrator 能否在真实业务功能切片中保持 contract、context、自测证据和 Codex acceptance 的一致性。

## 任务

在 `cancer-clinic` 项目中选择一个中等规模但边界清楚的功能切片完成。建议选择已经有文档或已有页面基础的任务，例如：

- 治疗安排详情页的一个明确 UI / 数据展示补充。
- 检查/检验资料相关的一个小流程补齐。
- README 或 specs 中已定义、但还未完全落地的一项前端交互。

## 约束

1. 必须先读取相关 specs / API / 页面文件，形成 TaskContract。
2. 不修改数据库 schema。
3. 不改后端核心接口，除非 TaskContract 明确说明且经过 human gate。
4. 不做无关重构。
5. 不扩大到完整事件链展示、完整报告上传流程或多页面重构。
6. Claude 必须写清楚自测方式和结果。
7. Codex acceptance 必须对照 TaskContract、diff、Git evidence、自测报告判断。

## 验收重点

- TaskContract 是否足够清楚：scope、non-goals、acceptance criteria、risk areas。
- Context package 是否给到了关键 specs / API / changed files。
- Claude 是否保留现有行为，不覆盖已有状态或字段。
- Codex 是否能发现 scope creep、证据不足或边界遗漏。
- 如果出现 `REJECT`，FailureArbiter 是否能给出有用分类。
- final report / handoff / next-stage prompt 是否能支持下一阶段继续。

## 通过标准

- workflow 最终 `COMPLETED`。
- 原始 repo 保持干净。
- worktree diff 与 TaskContract 范围一致。
- final report、handoff.json、delivery artifacts 存在。
- 没有明显误修、漏修或过度实现。
