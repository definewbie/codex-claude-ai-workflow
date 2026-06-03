# Benchmark Case: cancer-clinic-state-model-complex

## 类型

Complex but bounded frontend feature with state-model validation.

## 目标

验证 dual-orchestrator 在真实业务仓库里能否：

1. 用 P6 生命周期完成一个比小 UI 更复杂的功能切片。
2. 让 `WAITING_FOR_HUMAN`、`WAITING_FOR_RECOVERY`、iteration、final completion 等状态能被文档化解释。
3. 在不扩大范围的前提下生成清晰 contract、handoff、self-test evidence 和 Codex acceptance。

## 任务

在 `cancer-clinic` 项目中实现 B4：治疗页新用户引导。

具体需求：

- 当用户已有治疗方案，但治疗记录为空时，在治疗页的“治疗记录”区域显示新用户引导卡片。
- 卡片文案来自 specs：
  - 标题：`还没有治疗记录`
  - 正文：`如果之前已经在做治疗，可以先把过去的记录补进来。`
  - 主按钮：`补录既往治疗`
- 点击 `补录既往治疗` 时跳转补录页。
- 如果当前已有 `currentPlan`，跳转时带上 `planId=<currentPlan.id>`。
- 保留已有底部 `补录既往治疗` / `管理治疗方案` 链接。
- 保留已有“接下来要做的事”区域和“还没有治疗安排”空状态。

## 必读文件

- `docs/superpowers/specs/2026-05-20-treatment-module-flow-redesign.md`
- `docs/superpowers/specs/2026-05-20-treatment-module-wireframes.md`
- `miniprogram/src/pages/record/index.vue`
- `miniprogram/src/pages/retro/index.vue`
- `miniprogram/src/api/chemo.ts`

## 约束

1. 不改后端。
2. 不改数据库 schema。
3. 不实现 B1、B2、A6、C1、C2、D1、D2。
4. 不创建治疗记录。
5. 不上传报告。
6. 不改 TreatmentEvent / ExamReport / ChemoPlan 后端模型。
7. 不做完整补录向导重构。
8. 不修改既有治疗方案创建流程。
9. 不做无关重构。
10. 如果发现当前页面已经满足 B4，必须输出证据，不要重复实现。

## 验收重点

- Contract 是否明确区分：
  - no patient
  - no plans
  - plans exist but no treatment cycles / no treatment records
  - existing treatment records
- UI 是否只在“有方案但无治疗记录”时展示 B4 引导。
- CTA 是否带上当前方案 `planId`。
- Existing history/current treatment 行为是否被保留。
- Claude 自测报告是否包含 grep/build/typecheck 或合理替代说明。
- Codex acceptance 是否基于 contract、diff、Git evidence、自测报告判断，而不是只做泛泛总结。

## 状态模型验证点

运行后请对照 `docs/design/workflow-state-machine.md` 观察：

1. `start --run` 是否先进入第一个 human gate，而不是静默停在 task 中。
2. 每个 human gate 是否有 open gate id，并且 `review approve --run` / `autorun` 能路由到下一个节点。
3. 如果 Agent 或 contract 节点失败，workflow 是否进入 `WAITING_FOR_RECOVERY`，而不是丢失 active node。
4. `retry` 是否复用同一个 active node，而不是新建无关 iteration。
5. 如果 Codex 要求修订，是否创建新的 revision iteration；普通 retry 不应创建新 iteration。
6. 最终 `COMPLETED` 时，`final-report`、`handoff.json`、`summary.json`、delivery artifacts 是否存在。

## 通过标准

- workflow 最终 `COMPLETED`。
- 原始 repo 保持干净。
- worktree diff 只涉及 B4 必需文件。
- final report 和 benchmark report 能解释关键状态流转。
- 如果出现 recovery 或 revision loop，报告中必须说明原因、恢复动作和最终结果。
