# Benchmark Case: cancer-clinic-rejection-prone

## 类型

Rejection-prone case

## 目标

主动选择一个容易被 Codex 挑出问题的真实任务，验证 rejection repair loop 是否可靠。

这个 case 不是为了让 Claude 犯错，而是为了覆盖真实工作里常见的失败：需求边界不清、验收证据不足、实现遗漏、过度扩展、上下文不够。

## 任务

在 `cancer-clinic` 项目中选择一个有明确边界但容易漏细节的任务。建议选择：

- 已有 specs 中有多个约束条件的小功能。
- 需要同时看前端页面、API 类型、后端 schema 的任务。
- 有明确 non-goals 的任务，例如“不创建报告”“不自动修改状态”“不改后端 enum”。

## 约束

1. TaskContract 必须显式列出 included / excluded scope。
2. 必须列出 acceptance criteria 和 must-not-change。
3. Codex review / acceptance 必须重点检查 scope creep。
4. Claude self-test report 必须说明执行过哪些命令、人工检查了哪些边界。
5. 如果 Codex `REJECT`，系统必须进入 failure classification。
6. FailureArbiter 应区分 `IMPLEMENTATION_BUG`、`CONTRACT_GAP`、`CONTRACT_AMBIGUITY`、`REVIEW_NOISE`、`CONTEXT_INSUFFICIENT`。

## 验收重点

- 是否至少触发一次有意义的 Codex `REJECT` 或 `ACCEPT_WITH_NOTES`。
- rejection 后是否路由到正确 repair 节点。
- Claude repair 是否只修 Must Fix，不扩大需求。
- 第二次或后续 acceptance 是否能看到前一轮 rejection 和 repair evidence。
- CalibrationCandidate 是否在可校准分类时生成。

## 通过标准

- 理想结果：workflow 最终 `COMPLETED`，且至少经历一次 rejection repair loop。
- 可接受结果：workflow `COMPLETED` 但 Codex 直接 `ACCEPT`，记录为 coverage gap，不视为失败。
- 失败结果：workflow 卡在 recovery / failure，或 repair 后出现明显过度实现、误修、证据缺失。
