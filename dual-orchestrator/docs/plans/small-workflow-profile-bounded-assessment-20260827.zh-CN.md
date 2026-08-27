# Small Workflow Profile Bounded Assessment

日期：2026-08-27
状态：`DETERMINISTIC CHECKPOINT PASS / INDEPENDENT DIFF REVIEW PASS / READY FOR SELECTIVE COMMIT`

## 1. 目标

为用户显式选择的自然小任务寻找一个最小 workflow delta：减少完整 Agent node，同时保留 typed contract、human authority、
implementation、自测、独立 acceptance、ledger/recovery 和 completion audit。

本 assessment 不让模型或 harness 自动判断任务复杂度，不新增 profile engine/DSL/registry，也不授权 production variant、
provider 或 real-case。

## 2. Production baseline

当前 `terminal-delivery@5`：31 nodes / 51 edges / 9 `acpx.agent_run` nodes / 6 human gates。

9 次 Agent 调用为 A1-A9：intake producer/reviewer、design producer/reviewer、plan producer/reviewer、implementation、self-test、
acceptance。A10 handoff 已由 terminal-delivery profile 合法删除并经过 same-case proof。

small profile 必须继续复用这些 production owners；不能通过降低 parser、validator、lineage、permission、recovery 或 human gate
来减少调用。

## 3. 候选清点

### A2 intake product review：KEEP

`ContractCompileHandler` 明确只接受 `nodeId=intake.codex_product_review` 且带 `reviewSubject` 的 validated review sidecar
（`ContractCompileHandler.java:327,366`）。它还用 review decision、gate source outcome、lineage 与 BLOCKED questions 生成
contract resolution。跳过 A2 会迫使修改 contract owner，不是纯 profile subtraction。

### A4 architecture review：KEEP FOR FIRST EXPERIMENT

captcha-auth real-case 中 A4 多轮发现 OTP 事务、SecurityContext、限流容量和并发竞态等真实缺陷。即使简单任务未来可能
省略 A4，当前最强自然证据支持它在复杂任务中的独特价值；不把它作为第一个 profile 实验。

### A6 plan review：ONE EXPERIMENT CANDIDATE

A6 在中等 T6 曾发现会进入实现的计划偏差，因此不能从 full/terminal-delivery 全局删除。但对用户显式选择的小任务：

- A5 `plan.claude_plan` 已产出 strict typed `plan_result`；
- `gate.human_plan_approval` 已是最终人工 authority，允许 approve/revise；
- `HumanReviewGateHandler` 从 routed input refs 读取 latest validated sidecar 并投影 input artifacts，不要求 sidecar 必须来自
  `plan.codex_plan_review`；其 BLOCKED 专用一致性检查不影响正常 producer completion；
- gate 的 revise edge 已返回 `plan.claude_plan`，无需新 revision handler；
- approval 后仍进入原 `context.implement_claude`，implementation/self-test/acceptance 全部不变。

因此 A6 是唯一同时满足“完整 Agent node 可删除、人工 authority 已存在、graph-only delta、无新 owner”的候选。

## 4. 单一 graph delta

以 `terminal-delivery@5` 为基线，只做以下外部 test definition delta：

删除 3 个 nodes：

1. `context.plan_codex_plan_review`
2. `plan.codex_plan_review`（A6）
3. `plan.decision`

删除这些 nodes 的 6 条 incident edges，新增：

```text
plan.claude_plan --completed--> gate.human_plan_approval
```

保留 gate 原边：

```text
gate.human_plan_approval --approved--> context.implement_claude
gate.human_plan_approval --revise--> plan.claude_plan
```

预期 definition：28 nodes / 46 edges / 8 Agent nodes / 6 human gates。

不修改任何 bundled definition、loader、CLI、handler、contract 或 prompt。实验阶段仅使用现有 `--workflow <path>` 显式
加载外部 definition；不得提前新增 bundled `small` variant。

## 5. 语义变化

该 profile 明确把 plan 的第二意见从 Codex advisory review 转移给现有 human plan gate：

- 不再自动产生 A6 must-fix 或 `NEEDS_REVISION`；
- operator 直接审阅 A5 typed plan 和完整 input artifacts，选择 approve 或带 note revise；
- 删除 `plan.decision` 同时删除其 agent-driven revision loop 与 `max_revisions=3` 预算；delta 只保留 human gate 的
  revise loop，revision 次数由 `gate.human_plan_approval` 自己的 `max_revisions=3` 记账；
- human workload 增加，这是 profile 的公开 trade-off，不能在报告中描述为零成本；
- implementation 不能因缺少 A6 而获得额外 scope，仍受 TaskContract/change-scope enforcement；
- acceptance reviewer 仍独立存在，不能用最终验收为前置 plan 缺口提供伪保障。

用户显式选择 profile 即为 applicability owner。当前不设计自动复杂度评分，也不考虑用户误选保护。

## 6. Deterministic checkpoint

在 provider 前必须用 test-only external definition 证明：

1. definition 恰为 28/46/8/6，除第 4 节 delta 外与 terminal-delivery@5 逐对象相等；
2. full、contract、terminal-delivery bundled definitions byte/semantic identity 不变；
3. A5 strict sidecar 通过 direct edge 成为 human plan gate input ref；
4. review packet 显示 A5 decision、plan artifact ref、allowed actions=approve/revise，不伪造 A6 review；
5. approve 后只启动原 `context.implement_claude`；
6. revise + non-blank note 返回 A5，并由 gate-owned revision budget 计数；
7. 缺失、非法或 lineage-invalid A5 sidecar不能被 gate packet/后续 implementation 当作有效计划；
8. A6 prompt、run、sidecar 在 external definition replay 中均不存在；
9. implementation、自测、artifact validation、acceptance、delivery 与 completion audit 的 owner/required evidence 不变；
10. A5 产生 BLOCKED stage outcome 时，external delta 与 terminal-delivery baseline 的路由/失败行为完全一致；
11. human revise 只扣减 gate-owned `max_revisions=3`，不存在已删除 `plan.decision` 的 agent-driven budget；
12. focused tests、full reactor、`git diff --check` 通过。

graph identity 测试必须以“terminal-delivery baseline + 程序化 delta == external definition 文件”实现，不能只对两份手写
JSON 做计数比较。

如果第 3、4 或 7 项需要 production 代码特殊判断 node id，实验 FAIL；不得补 small-profile if/else。

## 7. Real-case Verification Gate 前置

只有 deterministic checkpoint 与独立 diff review PASS 后，才制定一次 fresh natural small-task Gate。Gate 必须冻结：

- explicit external workflow path/hash/id 与 28/46/8/6；
- 用户明确选择 small profile，不由模型/harness 猜复杂度；
- fresh Dual package、target baseline、provider probes；
- PASS：实际 Agent calls=8、workflow COMPLETED、human plan approval 有 typed action/note、A6 artifacts absent、target tests、
  change scope、lineage、self-test、acceptance 与 completion audit 全部不退化；
- FAIL：A6 缺失导致 plan 信息无法进入 implementation、人类无法判定、额外 revision 抵消收益或质量失败；
- INVALID：误跑 bundled workflow、旧 package、workflow hash 漂移、provider/config 不符；
- stop-loss：第二次同 stage revision、90 分钟 active runtime、任何 required evidence/lineage failure。

只运行一个 fresh workstream，不先跑第二个 full-profile 样本。结论只能是“该自然小任务 profile 完整执行且质量验收通过”，
不能声称 A6 对所有小任务无价值。

## 8. End-to-end invariant

| 维度 | Small profile A6 subtraction |
|---|---|
| Declared by | explicit external WorkflowDefinition；用户显式 profile 选择 |
| Proven by | graph identity delta + direct A5-sidecar-to-gate tests |
| Consumed by | human plan gate -> unchanged implementation/self-test/acceptance chain |
| Enforced before | human approve/revise 仍先于 implementation dispatch；TaskContract/scope 仍 fail-closed |
| Failure route | invalid plan evidence -> deterministic FAIL；human revise -> A5；runtime failure -> existing recovery |
| Cannot-bypass test | A6 artifacts absent、A5 ref present、approve/revise parity、bundled definitions unchanged |
| Residual risk | 无 independent plan advisory；质量依赖 A5 + human gate + unchanged downstream verification |

## 9. 决策与下一步

`ONE EXPERIMENT CANDIDATE: A6 PLAN REVIEW OPTIONAL FOR EXPLICIT SMALL PROFILE`。

独立架构评审已 `PASS（P0=0 / P1=0）`，两项 P2 已纳入测试与语义说明。test-only external definition 与
deterministic checkpoint 已实施：外部图为 28/46/8/6，程序化 delta、A5 evidence -> human gate、approve/revise、
gate-owned revision budget、lineage fail-closed、A5 BLOCKED parity 与 bundled resource SHA 均有测试。focused 为
`7/0/0/0`，full reactor 为 `1243/0/0/0`，`git diff --check` clean。

独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=0）`，并独立复现 focused `7/0/0/0`、full reactor
`1243/0/0/0` 与 clean diff-check。下一步选择性提交 checkpoint，并制定一次完整 natural small-task real-case
Verification Gate；此时仍不新增 production variant、不调用 provider。
