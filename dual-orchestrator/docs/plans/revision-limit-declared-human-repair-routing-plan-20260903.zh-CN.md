# Revision Limit Declared Human Repair Routing Plan

日期：2026-09-03  
状态：`IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS`

## 目标

当语义 loop 达到 `max_revisions` 时，如果 frozen workflow 已为源 decision 声明唯一的
`BLOCKED -> human gate` 边，运行时必须进入该 gate，让人决定是否授权一次新的 repair；不能另建只含
`retry/abort` 的 synthetic recovery gate，使声明的 human authority 不可达。

## 现有模型与根因

- Definition owner：workflow JSON 的 outgoing edges 与 gate `allowed_actions`。
- Instance owner：node/iteration/gate/current ledger documents。
- Execution retry：`retryActiveNode` 只重跑当前失败或阻塞 node，不创建语义 iteration。
- Semantic repair：loop transition 创建新 iteration，由 `max_revisions` 约束。

`RoutingEngine` 当前在准备 loop successor 时先检查预算；耗尽后直接调用
`RevisionTracker.blockOnRevisionLimit`，不再检查同一 decision 的 `BLOCKED` edge。因此 OKX frozen definition
明明声明了 `gate.human_implementation_repair`，运行时却创建 `recovery.review(retry, abort)`。blind retry 不会改变
预算或证据，必然再次阻塞。

## 最小修改

1. `RoutingEngine` 在 revision limit 命中时查找同一源 node、outcome=`BLOCKED` 的 matching transition。
2. 只有它唯一指向 `NodeKind.GATE` 时，使用该声明边路由；源 decision 保持 terminal，结果补充
   `reason=revision_limit_reached`、原 outcome、预算与计数，事件保留 revision-limit 事实。
3. gate 使用 definition 的 handler、purpose、allowed actions 与既有 subject refs；人工 `revise` 后由现有 loop
   机制创建下一 iteration。该人工作用不是重置预算，也不是 execution retry。
4. 无唯一 declared human gate 时，保持现有 synthetic `retry/abort` recovery 行为。

## 不做

- 不新增 recovery action、CLI、schema、workflow node、预算重置或 OKX 特判。
- 不改变普通 execution retry、acceptance repair、provider/session/network 行为。
- 不自动批准 repair。

## 必测边界

1. self-test 第一次 incomplete 仍自动进入 `iter-002`。
2. 第二次 incomplete 达预算后进入已有 `gate.human_implementation_repair`，而非 synthetic recovery；gate 只允许
   `revise`，必须有 note。
3. 人工 revise 后创建下一 semantic iteration，并携带最新 self-test refs 与 revision-limit 事实。
4. 没有 declared `BLOCKED -> gate` 的 revision-limit fixture 保持 `WAITING_FOR_RECOVERY + retry/abort`。
5. 多个或非 gate 的 `BLOCKED` target 必须 fail closed，不做猜测。
6. `BLOCKED -> gate` 自身若错误声明为 loop，必须在任何 revision-limit 专属写入前 fail closed。

## End-to-End Invariant

- Declared by：workflow `BLOCKED` edge 与 gate allowed actions。
- Proven by：revision-limit event、decision result、gate subject refs 和 iteration lineage。
- Consumed by：`RoutingEngine` 唯一路由与既有 human gate resolution。
- Enforced before：创建任何新 semantic repair iteration 或 provider run。
- Failure route：无唯一合法 gate时维持 synthetic recovery；定义歧义时 fail closed。
- Cannot-bypass：上述五组 ledger sequence tests。

## 成熟度与下一步

实现前：`DEFECT REAL-CASE PROVEN / FIX NOT ENFORCED`。  
实现和独立 diff review 后：先用 fresh JAR 对当前 deterministic decision 执行一次 retry，使其路由到既有
human repair gate；由用户明确 `revise` 后才允许下一次 implementation provider occurrence。

## 实施结果

- production 仅修改 `RoutingEngine`；未修改 `RevisionTracker`、CLI、workflow JSON 或 schema。
- revision-limit 命中后，使用同一 `FlowOrchestrationKernel` 求值 `BLOCKED` transition；只接受唯一 gate target。
- declared gate 路径保留最新 output refs，并在 decision result/event 中记录原 outcome、预算与计数。
- 无 declared gate 时原 synthetic recovery 行为保持不变；多个或非 gate target 直接拒绝。
- focused ledger tests：review delta 后 `53/0/0/0`。
- full reactor：review delta 后 `1301/0/0/0`，`BUILD SUCCESS`。
- `git diff --check`：clean。

独立 diff review：`PASS / P0=0 / P1=0`。评审提出的病态 loop-gate 与唯一非 gate 两项廉价护栏已在
本 checkpoint 内补齐；重复预算计算不做重构，避免扩大范围。
