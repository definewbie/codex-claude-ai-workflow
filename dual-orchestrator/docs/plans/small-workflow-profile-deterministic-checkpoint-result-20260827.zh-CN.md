# Small Workflow Profile Deterministic Checkpoint Result

日期：2026-08-27
基线：Dual `db73f31f0c78f6ad23dfd05f74c9d889bd5b2f34`
状态：`DETERMINISTIC PASS / INDEPENDENT DIFF REVIEW PASS / READY FOR SELECTIVE COMMIT`

## 1. 本次唯一目标

验证一个仅由用户显式选择的 test-only small workflow：删除 A6 plan reviewer 及其 context/decision 节点，让 A5 typed
plan 直接进入既有 human plan gate，同时不修改 production、bundled workflow、loader、CLI、handler、contract 或 prompt。

## 2. 产物

- 外部 definition：
  `dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json`
- SHA-256：`d41b835e4d8a8347b64984e085385030246976bbc485a866f215dfafc97faaec`
- 图规模：28 nodes / 46 edges / 8 Agent nodes / 6 human gates。
- production diff：无。

## 3. 已证明的边界

1. 程序化执行 `terminal-delivery@5 - 3 nodes - 6 incident edges + 1 direct edge` 后，与外部 definition 的 nodes/edges
   逐对象相等；不是两份手写 JSON 的计数比较。
2. full、contract、terminal-delivery 三份 bundled resource 的 SHA-256 保持冻结值。
3. A5 raw plan 与 validated sidecar 经真实 `WorkflowFileLedger.completeAndRoute` 直接成为 human plan gate input refs；
   `HumanReviewGateHandler` packet 投影 `READY_FOR_PLAN_REVIEW` 与 approve/revise，不生成 A6 review 事实。
4. approve 只进入原 `context.implement_claude`；带 note 的前三次 revise 返回 A5 并创建新 iteration，第四次由
   `gate.human_plan_approval.max_revisions=3` fail-closed。event 中无 `plan.decision` budget owner。
5. 未绑定 ledger lineage 的 forged plan sidecar 在 packet 写入前被拒绝。
6. A5 `BLOCKED` 在 baseline 与 experiment 上均真实落到 `WAITING_FOR_RECOVERY`，没有因 delta 改变失败行为。
7. retained nodes/edges 逐对象相等，因此 implementation、self-test、artifact validation、acceptance、delivery 与
   completion audit owner 未变；A6 三节点在实验图中不可达且不存在。

## 4. 验证结果

- focused：`SmallWorkflowProfileDefinitionTest` + `SmallWorkflowProfileGateIntegrationTest` = `7/0/0/0`。
- full Maven reactor：9/9 modules，`1243/0/0/0`，BUILD SUCCESS。
- `git diff --check`：clean。
- provider / real-case：未调用。

## 5. 成熟度与残余风险

- test-only graph 与 ledger transition：`VALIDATED`。
- human gate、lineage、revision-limit 等复用的 production owners：维持既有 `ENFORCED`。
- production small variant：`NOT MODELED`。
- small-profile 真实质量与 9 -> 8 Agent 调用收益：`NOT PROVEN`。

残余风险是删除 independent plan advisory 后，计划质量更多依赖 A5 与 human reviewer。deterministic tests 只能证明
路由和证据边界不退化，不能证明真实小任务质量不退化。

## 6. 下一步

独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=0）`，并独立复现全部声明数字。下一步选择性提交该
test-only checkpoint，再基于 clean SHA 制定一次 natural small-task real-case Verification Gate；Gate 定稿前不调用
provider。
