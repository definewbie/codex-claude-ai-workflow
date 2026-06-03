# Benchmark Case: cancer-clinic-rejection-prone

## 类型

Rejection-prone case

## 目标

用一个真实、边界明确、但很容易漏细节的小功能，验证 dual-orchestrator 的 contract、context、Codex acceptance、failure classification 和 repair route 是否可靠。

这个 case 不是为了让 Claude 犯错，而是覆盖真实工作里常见的失败：需求边界不清、验收证据不足、实现遗漏、过度扩展、上下文不够。

## 任务

在 `cancer-clinic` 项目中实现 B2：补录既往治疗保存后，引导用户去最近一次治疗记录页补充检查报告。

背景文档：

- `docs/superpowers/specs/2026-05-20-treatment-module-flow-redesign.md`
- `docs/superpowers/specs/2026-05-20-treatment-module-wireframes.md`

重点代码：

- `miniprogram/src/pages/retro/index.vue`
- `miniprogram/src/pages/record/index.vue`
- `miniprogram/src/api/chemo.ts`

具体目标：

1. 用户在补录既往治疗页点击“保存修改”后，如果保存成功，页面不要只显示 toast。
2. 成功后展示一个轻量引导，文案为：
   - `已保存 X 条治疗记录。`
   - `建议先补录最近一次治疗的检查报告，后续 AI 整理和复诊准备会更完整。`
3. 引导里有主按钮：`去最近一次治疗记录`。
4. 引导里有次操作：`暂时不了`。
5. 点击 `去最近一次治疗记录` 后，跳转到最近一次已完成治疗记录详情页。
6. 点击 `暂时不了` 后，返回治疗页。

## 约束

1. 只实现 B2 保存后引导，不做 B1 / B3 / B4 / C 系列能力。
2. 不修改后端接口、数据库模型、schema、迁移文件。
3. 不创建检查报告，不上传报告，不自动生成摘要。
4. 不自动修改治疗事件状态。
5. 不改治疗方案、治疗周期的数据结构。
6. 最近一次治疗记录应优先选择当前选中方案内 `status === "completed"` 且有 `actual_date` 的记录，按实际日期最新排序。
7. 如果没有已完成记录，可以退化到当前选中方案里最新一条有日期的记录；如果没有可跳转记录，主按钮应不可用或给出清晰提示，不应跳错页。
8. 跳转治疗记录详情页时，应使用项目现有路由方式，不引入新页面。
9. 不要扩大为复杂弹窗系统、全局 notification、引导向导、多步流程。
10. 不要引入新的第三方依赖。

TaskContract 必须显式列出 included / excluded scope、acceptance criteria 和 must-not-change。Codex review / acceptance 必须重点检查 scope creep 和验收证据是否充分。

## 验收重点

- 页面是否从“保存后只有 toast”变成“保存成功后有可操作引导”。
- 引导文案是否贴合 wireframe，不写成医疗建议。
- `去最近一次治疗记录` 是否能跳到正确记录详情页。
- `暂时不了` 是否能回到治疗页。
- 没有已完成治疗记录时是否有合理降级，不会跳错。
- 是否没有修改后端、schema、数据库、无关页面。
- Claude self-test report 是否说明执行过的命令和人工检查过的边界。
- 如果 Codex `REJECT`，系统是否进入 failure classification 并路由到正确 repair 节点。
- FailureArbiter 是否能区分 `IMPLEMENTATION_BUG`、`CONTRACT_GAP`、`CONTRACT_AMBIGUITY`、`REVIEW_NOISE`、`CONTEXT_INSUFFICIENT`。

## 通过标准

- 理想结果：workflow 最终 `COMPLETED`，且至少经历一次 rejection repair loop。
- 可接受结果：workflow `COMPLETED` 但 Codex 直接 `ACCEPT`，记录为 coverage gap，不视为失败。
- 失败结果：workflow 卡在 recovery / failure，或 repair 后出现明显过度实现、误修、证据缺失。
