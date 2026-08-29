# Review: Accepted Delivery Candidate Implementation Plan (D1)

日期：2026-08-29
评审性质：独立、只读、代码级；未修改 production；未调用 provider/real-case
取证范围：production delivery 全链（prepare/merge/commit/create-pr/handlers/writers/loader）、
GateDocument 与 RoutingEngine subject 机制、AHBV gate 实证、runtime pom 依赖

## Findings

**P0：无。P1：无。**

### P2-1（知会）resolver 的"最后 occurrence 必须 approved"与 current=COMPLETED 守卫近乎冗余——保留双层是正确的
apply 的守卫 2（workflow COMPLETED）在 terminal-delivery 语义下已蕴含最后 acceptance gate 为
approve（revise 会回到 implementation 而非完成）；resolver 的 no-fallback 规则因此主要防
ledger 损坏/手工编辑场景——双层 fail-closed 是正确冗余，无需修改。

### P2-2（知会）apply→commit 的 operator 窗口以检测而非锁定关闭
finalize 验证 parent/tree/index 一致性是事后检测；窗口内 operator 可 theoretically 制造
tree-consistent 但语义不同的 commit 吗——不能：commit tree 必须等于 candidate 的
`expectedResultTree`，任何内容差异都会改变 tree SHA。检测在此等价于锁定。Residual 声明诚实。

### P2-3（正面记录）前轮 P2-1（excluded-files 语义）已被 §5.2 完整吸收
path-set 参与 identity、temp index 中 excluded 保持 base 状态、excluded 内容变化不交付不阻塞、
target clean 独立于 source excluded——四处歧义全部钉死。

## Sweep 声明独立核实（全部属实）

| 计划声明 | 实测 |
|---|---|
| prepare 仅产 Markdown、gate 仅绑两 Markdown ref | `DeliveryPrepareHandler`（6.8K，写 commit-message/pr-body）；**AHBV `gate-node-gate-human_acceptance-047.json` 的 `result.subject_input_refs` 实证恰为这两个 Markdown ref** ✓ |
| CLI/server 各自目录排序扫描 acceptance sidecar | `DeliveryMergeCommand:145-146` 与 `DeliveryViewService:207-208` 均 `.filter(filename).sorted()` ✓（双实现 + 非 lineage 选择） |
| legacy bypass 家族 | `DeliveryCommitHandler:48-55` 实测 `git add -A -- . :!.agent` + `git commit -F` **无任何 gate/candidate 读取**；写 `DeliveryLedgerWriter`（delivery.json 平行 lifecycle）；`DeliveryPrCreateHandler`（7.8K）同族；`WorkflowDefinitionLoader:66` 有 `delivery.create_pr` 特判 ✓ |
| `DeliveryLedgerWriter`/`DeliveryReportUpdater` 仅被 legacy 消费 | 全仓引用 = DeliveryCommitHandler + DeliveryPrCreateHandler ✓ |
| subject ref 传递机制既有、RoutingEngine 无需改 | `RoutingEngine:197` 在 gate 创建时把 routed outputRefs 写入 `result.subject_input_refs` ✓——candidate ref 经 prepare→outputRefs→edge→acceptance node→gate 的传递零 graph 变更 |
| runtime 当前不依赖 adapter-git | `dual-runtime/pom.xml` 中 `dual-adapter-git` 零命中——新增单向依赖无环 ✓ |

## 六个评审问题结论

**1. Human acceptance 真正绑定 immutable candidate bytes ✓** — 机制链经代码+实证双重验证：
content-addressed candidate（occurrence 目录以 producer node instance 命名、文件名含自身 SHA、
无自引用循环）→ prepare outputRefs → `RoutingEngine:197` 既有传递写入 gate
`result.subject_input_refs` → resolver 从 **resolved GateDocument** 重验 candidate 文件名 SHA、
bytes、patch/artifact SHA、base identity、lineage。今天绑定的只有 Markdown（AHBV 实证），
candidate 替换是纯内容升级、零 workflow graph 变更——计划的"不改 RoutingEngine，仅 integration
test 证明传递"判断准确。

**2. Resolver 唯一、确定、fail-closed ✓** — 事件顺序（`events.jsonl` node_routed）取最后
acceptance occurrence，非文件名/mtime/目录序；最后为 revise/open/缺失/损坏**不回退**较早
approval；恰一个 canonical candidate ref；全链 SHA 重验；node/gate/workstream/iteration lineage
互检。不新增 accepted-candidate.ref 文件（避免给 human transition 增加多文件写入与 R1 窗口）——
正确的取舍。canonical approval action 复用 H4 语义。

**3. CLI/server/commit/push/PR 无残留旁路 ✓** — 删除面覆盖实测的 bypass 家族（commands、
handlers、独占 writers、loader defaults、catalog、docs）；custom workflow 声明旧 handler 时
definition validation fail-visible（不留静默兼容）；merge/finalize 收敛到唯一
`DeliveryApplicationService`；push/PR 不在本轮重实现且未来必须消费同一 accepted candidate
（正确的约束方向）。删除后 target 写入仅剩 service.apply（在七步守卫后）。

**4. Legacy 删除必要且边界完整 ✓** — 必要性由实测证明（未绑定 gate 的直接 `git add -A`+commit
+push+PR，平行 delivery.json lifecycle，且仍被 catalog/loader/docs 声明为现行能力——同一外部
副作用边界的 sibling bypass，非独立功能）；边界完整（含 subtraction sweep 测试 §7.2.8 的零残留
断言）；保留 commit-message/pr-body 作为 candidate presentation 是正确区分（展示 vs authority）。

**5. 模块归属、文件范围、tests 最小充分 ✓** — runtime→adapter-git 单向新依赖（实证无环）；
adapter 只做 Git plumbing、ledger 只做 documents/writers、runtime 拥 guard 顺序、CLI/server 仅
参数与投影且被禁止扫 runs/直调 Git；四个 workflow JSON 零修改（止损条款明确）。测试 1-9 覆盖：
capture 全边缘（untracked/binary/spaces/rename/mode + source index 不变）、resolver 序列
（approve/revise/伪造/删除 + no-fallback）、apply 全 drift（accepted mutation/excluded path-set
drift/target dirty/base drift/patch tamper/删除 candidate）+ excluded-only 内容不污染、finalize
精确性（wrong parent/tree/SHA/head drift）、CLI/server parity、UI 状态机、subtraction 零残留。

**6. Invariant 成立 ✓** — Declared by content-addressed candidate + gate subject binding；Proven
by patch/artifact SHA + base commit/tree + expected result tree + resolved GateDocument；Consumed
by 唯一 service；Enforced before target apply、finalize 与任何保留的 delivery 外部副作用；
Failure route typed FAILED 零写入 + `LEGACY_NOT_BOUND`（旧 workstream 显式降级不伪造 plan）；
Cannot-bypass 六类测试；Residual（apply→commit operator 窗口以 finalize 检测关闭——因 tree
SHA 等价于锁定，见 P2-2）。

## 判定

- **Boundary Review: PASS**
- **Implementation READY: YES** —— sweep 已完成且本评审独立证实其全部事实声明；前轮遗留的
  excluded-semantics 缺口已钉死；candidate 原子发布（temp 目录→rename）顺带关闭了创建窗口的
  R1 面。按 §9 顺序实施（capture → gate binding/resolver → apply/finalize → CLI/server →
  删除 legacy → focused/reactor/diff-check → 独立 diff review → 选择性提交）。
