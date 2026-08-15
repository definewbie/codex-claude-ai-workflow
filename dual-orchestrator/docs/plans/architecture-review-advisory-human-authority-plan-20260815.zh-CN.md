# Architecture Review Advisory / Human Authority Convergence Plan

日期：2026-08-15  
状态：`IMPLEMENTED / DETERMINISTIC ENFORCED / INDEPENDENT DIFF REVIEW PASS / READY FOR SELECTIVE COMMIT / NOT PROVEN`  
主线：`P0` workflow 正确性；同时删除由自动 semantic revision 带来的无效 Agent 调用  
输入证据：captured replay result checkpoint `f413cb44`

## 1. 结论先行

本轮 captured replay 已经否定了一个关键假设：即使把当前 typed design、完整 checks/evidence、bounded iteration history 和严格输出纪律全部投影给单次 architecture-review Agent，也不能保证它一次性发现完整 blocker 集。

因此下一步不应继续增强 judge prompt、增加 judge、增加 retry，或放宽 strict parser。应删除 LLM review verdict 对业务路由的直接控制权：

- Codex architecture review 继续提供严格、可追溯的 typed advisory artifact；
- `READY_FOR_ARCH_REVIEW / NEEDS_REVISION / BLOCKED` 都只作为人工审批 packet 的建议和证据；
- 只有 human `approve` 可以进入 plan；
- 只有 human `revise` 可以启动 design revision、消耗 revision budget 并回到 Claude；
- malformed review 仍是执行/contract failure，不伪装为有效建议，也不放宽 parser。

这不是降低质量标准，而是把业务裁决权从概率性 evaluator 收回到既有 human gate。

## 2. 已确认事实

### 2.1 Captured replay

- production prompt 含完整、lineage-validated current design、4 个 checks、5 个 evidence 和 iter-003..005 reasons；
- replay 为 1 次 prompt、0 correction/retry、0 permission/tool、exit 0、`end_turn`；
- strict parser 正确拒绝自由文本 `failure.verification_remediation`，不是 transport 截断；
- reviewer 发现了新的有效 B5，但遗漏 Gate B1-B4；
- 因此 subject projection 与 strict enforcement 已被证明有效，单 turn semantic completeness 未被证明且本样本明确失败。

### 2.2 当前 production 控制链

三个 bundled workflow 当前均为：

```text
design.codex_arch_review
  -> design.decision
     READY_FOR_ARCH_REVIEW -> gate.human_design_approval
     BLOCKED               -> gate.human_design_approval
     NEEDS_REVISION         -> design.claude_design

gate.human_design_approval
     approved -> plan.claude_plan
     revise   -> design.claude_design
```

`ReviewDecisionHandler` 从 validated sidecar 读取 LLM decision，并把它直接作为 routing outcome。`RoutingEngine` 在路由 `NEEDS_REVISION -> design.claude_design` 前启动 iteration、检查 `design.decision.max_revisions` 并消耗 revision budget。因此当前 LLM verdict 不是 advisory，而是 authoritative control fact。

`HumanReviewGateHandler` 已经投影 `sourceOutcome`、`latestAgentDecision`、review subject、must-fix/open-risk evidence、allowed actions 和 operator guidance。UI/API/CLI 最终均由 `RoutingEngine.resolveOpenGate()` 消费人工 action。无需新 UI schema、API、模块或平台能力。

但当前 `dualctl autorun` 仍有一条控制旁路：`AutoRunCommand.requiresHumanDecision()` 只把 `BLOCKED` 视为必须人工处理，其余 WAITING_FOR_HUMAN gate 由 `gateActionFor()` 自动写入 `approve`。若只修改 workflow，`NEEDS_REVISION` 会被 autorun 吞掉；即使 reviewer 返回 `READY_FOR_ARCH_REVIEW`，也仍不是“人最终裁决”。因此 autorun policy 必须作为同一 checkpoint 的一部分收敛。

## 3. 外部研究带来的约束

[LLM-as-a-Judge Field Guide](https://kraghavan.ca/llm-infrastructure/evaluation/2026/07/25/llm-as-a-judge-field-guide.html) 把 judge 定位为可扩展但有偏差的 teaching assistant，而非 professor/ground truth；生产实践要求用人类标注校准、优先二元 rubric，并且不完全移除 human reviewer。文章还总结了 position、verbosity、self-preference、format bias、领域失效和相关 judge ensemble 的共同错误。

对 Dual 的直接启发不是“换一个更强 judge”，而是：

1. LLM review 适合发现候选风险、组织 evidence、降低人工阅读成本；
2. 未经本地 human-gold calibration，不应把单次 verdict 当成自动 revision authority；
3. 多 judge/retry 不能证明独立性，只会增加调用、token 和延迟；
4. trust-or-escalate 在当前产品中已有最小实现：既有 human design approval gate。

## 4. Owner 模型

| Fact | 唯一 owner | 消费方 | 控制权 |
|---|---|---|---|
| 当前 design artifact | validated design sidecar / lineage | reviewer + human packet | 业务事实 |
| architecture review findings | Codex review contract sidecar | `ReviewDecisionHandler`、human packet | advisory |
| review artifact 是否满足 strict contract | parser/schema/semantic/artifact validators | agent execution finalizer | 执行可靠性 |
| design 是否继续 | human gate action ledger | `RoutingEngine.resolveOpenGate()` | 唯一业务裁决 |
| design revision 是否开始 | human `revise` transition | `RoutingEngine` / `RevisionTracker` | 唯一 revision authority |

LLM decision 不能再同时拥有“发现问题”和“启动 revision”两种职责。

## 5. 单一原子 checkpoint

### 5.1 Workflow definition

同步修改三个 bundled workflow：

- `agent-lifecycle.full.json`
- `agent-lifecycle.contract.json`
- `agent-lifecycle.terminal-delivery.json`

保持 `design.codex_arch_review -> design.decision`，以保留显式 typed review trace，并避免在本 checkpoint 内迁移 ContractRegistry 与相关 tests。将 `design.decision` 的三种合法 outcome 全部路由到 `gate.human_design_approval`：

```text
READY_FOR_ARCH_REVIEW -> gate.human_design_approval
NEEDS_REVISION         -> gate.human_design_approval
BLOCKED               -> gate.human_design_approval
```

删除三个 workflow 中 `design.decision.config.max_revisions`。该节点只投影 advisory decision，不再拥有 revision budget。

保留 human gate：

```text
approved -> plan.claude_plan
revise   -> design.claude_design
```

其 `max_revisions=3` 继续作为人工 design revision 的唯一预算 owner。

### 5.2 Iteration semantics

在 `RoutingEngine` 创建 successor 前增加领域规则：**进入 `NodeKind.GATE` 的 transition 不启动 semantic revision**。

原因：进入 gate 只是把建议交给人，不是接受建议。即使 source outcome 为 `NEEDS_REVISION`，也不得预先写 iteration 或消耗预算。只有 gate action `revise -> design.claude_design` 才满足 revision trigger。

该规则应基于 target kind，而不是硬编码 `design.decision` 或某个 gate id。当前 bundled workflow sibling sweep 中不存在其他 revision trigger 直接进入 gate，因此不会改变既有其他路径；新增测试必须守住这一点。

### 5.3 Human packet

不新增字段。复用现有：

- `sourceOutcome`
- `latestAgentDecision`
- `reviewSubject`
- `inputArtifacts`
- `operatorGuidance`
- `allowedActions`

只校正 guidance 语义：`NEEDS_REVISION` 是 reviewer recommendation；operator 仍可根据 design 与 evidence 选择 `approve` 或 `revise`。不得把 recommendation 文案变成隐式强制。

packet 当前只内联 decision/summary/must-fix/open-risks，不内联完整 design checks/evidence；它提供 validated sidecar 与 review subject 路径。operator guidance 必须明确要求人在 approve 前读取这些完整引用，不能只依据 packet summary 或 reviewer verdict。

### 5.4 Autorun policy

`dualctl autorun` 必须遵守相同 authority：

- `gate.human_design_approval` 无论 source outcome 为 `READY_FOR_ARCH_REVIEW`、`NEEDS_REVISION` 或 `BLOCKED`，都必须停止并打印 UI/CLI 人工操作指引；
- 其他 gate 的 `BLOCKED` 或 `NEEDS_REVISION` 同样不得自动 approve；
- 其他既有 positive intermediate gate 的 autorun 行为保持，不扩大为“所有 gate 永远手工”；
- final next-stage gate 的既有 `complete` 语义保持。

实现应复用既有 `HumanGateSemantics` 的 gate purpose/config 语义，避免只靠 source outcome，也避免新建 policy registry。`AutoRunCommand` 必须同时获得当前 gate definition/purpose 和 source outcome；不得用一个新的散落字符串集合重建 gate 分类。

### 5.5 明确不改

- 不放宽 strict parser/schema/semantic/artifact validators；
- 不自动修正 enum；
- 不增加 correction、retry、judge panel 或 Agent 调用；
- 不修改 review contract schema；
- 不新增 confidence、评分、registry、engine、module 或 memory producer；
- 不改变 intake、plan、self-test、acceptance 的 decision semantics；
- 不迁移或 resume 已消费的 captcha-auth workstream。

## 6. 兼容性选择

直接删除 `design.decision` 在概念上更短，但当前不采用。冻结的旧 workstream 自包含 `workflowDefinitionJson`，因此删除 bundled node 本身不会破坏它们；保留节点的真实理由是：

- 当前 `ContractRegistry`、definition consistency tests 和 review trace 都以该 decision node 为显式 typed projection；
- 删除它会扩大本 checkpoint 到 registry/trace/test ownership 迁移，却不减少 Agent 调用或 token；
- 保留它并撤销 routing authority 已足以闭合业务边界，diff 更小、回滚更清晰。

本 checkpoint 保留它但撤销控制权。后续只有在 trace/projection 价值也被证明为零时，才单独评估删除；不得为了视觉上的少一个节点引入兼容框架。

## 7. Cannot-bypass tests

1. 三个 bundled workflow 的 `design.decision` 三种 outcome 均且只进入 human design gate。
2. 三个 workflow 的 `design.decision` 不再声明 `max_revisions`；human gate 仍声明预算。
3. `NEEDS_REVISION -> GATE` 创建 waiting gate，但不创建 iteration、不增加 revision count、不触发 revision-limit。
4. packet 保留 validated `latestAgentDecision=NEEDS_REVISION`、must-fix/evidence，并允许 `approve/revise`。
5. 人工 `approve` 在 advisory `NEEDS_REVISION` 后仍可进入 `plan.claude_plan`，不经过 Claude revision。
6. 人工 `revise` 恰好创建一个新 iteration、消耗 human-gate-owned budget 并进入 `design.claude_design`。
7. `BLOCKED` 同样进入 human gate，且既有 note enforcement 保持。
8. malformed/missing review sidecar 仍 fail closed，不能伪造成 READY 或绕过 strict contract。
9. iteration reason 逐字携带 human revise note，使下一版 Claude 和审计链消费真正的人工意见。
10. 第 4 次 human revise 超过 gate-owned budget 时必须产生 `revision_limit_reached`、进入 recovery gate，并由 attention projection 分类为 `SEMANTIC_REVISION_LIMIT`；不得创建第 4 个 revision Agent run。
11. autorun 遇 design gate 时，无论 source outcome 为 positive/negative/blocking 都停止；遇其他 gate 的 `BLOCKED/NEEDS_REVISION` 也停止；既有其他 positive gate 和 final complete 行为保持。
12. operator guidance 明确要求读取完整 referenced design/review sidecar，不能把 summary 当完整判断材料。
13. sibling workflow/ContractRegistry consistency、UI/API/CLI human action parity 全部保持；同步更新 `AutoRunCommandTest`、workflow definition tests、ledger revision tests、gate resolution tests 与受旧 design edge 影响的 fixtures。
14. full reactor 与 `git diff --check` PASS，随后独立代码级 diff review。

## 8. End-to-end invariant

| 维度 | 证明口径 |
|---|---|
| Declared by | validated review sidecar 只声明 advisory findings；human action ledger 声明业务决定 |
| Proven by | strict review contract + lineage；human gate action 的 canonical validation |
| Consumed by | human packet 消费 advisory；`RoutingEngine.resolveOpenGate()` 唯一消费 approve/revise |
| Enforced before | autorun 必须先停在 design gate；plan 或 design revision successor 创建前必须有 canonical human action |
| Failure route | malformed review 为 execution failure；合法 negative verdict 进入 waiting human gate，不自动 revision |
| Cannot-bypass | §7 deterministic tests |
| Residual path | frozen old definitions 保持其历史行为；intake/plan 的自动 LLM revision 语义不在本 checkpoint；新 bundled definitions 的 design stage 不存在 LLM negative verdict 直达 Claude 的 edge |

## 9. 成熟度和后续验证

当前：

- architecture subject projection：`PROVEN`（真实 replay 证明 prompt 完整）；
- strict review contract enforcement：`PROVEN`（真实 replay 正确拒绝非法 enum）；
- 单 turn semantic completeness：`REJECTED`；
- advisory/human-authority routing：`MODELED`（本计划），尚未实施。

实施、deterministic tests 与独立 diff review PASS 后，可标：

- advisory/human-authority routing：`ENFORCED / NOT PROVEN`。

之后不重放当前样本，不人工制造 negative verdict。等待下一次自然 design review：无论 reviewer 输出 READY、NEEDS_REVISION 或 BLOCKED，都必须先停在 human gate；operator 的 approve/revise 才能决定后续。这一次自然证据通过后方可标 `PROVEN`。

## 10. Stop conditions

出现任一情况立即停止实施并回到设计：

- 为兼容旧 workstream 需要新增 migration framework；
- 必须放宽 strict parser 才能到达人 gate；
- 仍存在新 bundled workflow 的 design stage 中 LLM negative verdict 直达 `design.claude_design` 的 edge；
- 进入 gate 仍写 revision iteration 或消耗 budget；
- UI/API/CLI 任一路径可绕过 canonical human action；
- autorun 可自动 resolve design approval gate；
- 方案开始扩展到通用 judge calibration 平台。

## 11. Implementation result

实现已按本计划作为单一 checkpoint 落入工作树，尚未提交：

- 三个 bundled workflow 升级为 full@10、contract@9、terminal-delivery@5；
- `design.decision` 三种 outcome 全部进入 human design gate，且不再拥有 `max_revisions`；
- `RoutingEngine` 不为进入 gate 的 advisory transition 创建 iteration；
- design gate `revise` 必须有 note，人工 note 成为 iteration reason；
- `HumanGateSemantics` 统一声明 design gate 和 negative advisory 的 operator requirement；
- `dualctl autorun` 对 design gate 三态全部停止，不再自动 approve；
- human 可覆盖 `NEEDS_REVISION` advisory 直接 approve 进入 plan，且不创建 revision；
- human revise 超预算时 fail closed，不创建下一次 Agent run；
- packet guidance 明确要求读取完整 referenced design/review sidecars。

验证：

- focused affected tests：PASS；
- final full Maven reactor：9/9 modules，`1249 tests / 0 failures / 0 errors / 0 skipped`；
- `git diff --check`：PASS；
- provider / replay / real-case：未运行。

独立代码级 diff review 结论为 `PASS`（`P0=0 / P1=0 / P2=4`），并独立复现 full reactor `1249/0/0/0` 与 `git diff --check` PASS。四项非阻塞观察为：`gate_purpose` 在 ledger/runtime 分层中的轻微谓词分叉、packet 未单独断言 latest advisory decision、revision-limit attention 分类依赖既有泛化测试、以及一个旧 evaluator fixture 仍使用历史 edge。它们均不绕过 canonical human authority，也不阻塞选择性提交；不得在本 checkpoint 内扩成新的重构任务。

当前成熟度为 `ENFORCED / NOT PROVEN`。真实证明等待未来自然 architecture review：任意合法 reviewer verdict 必须先停在 human gate，再由 operator `approve` 或带 note 的 `revise` 决定后续；不重放已消费样本，也不人工制造 verdict。
