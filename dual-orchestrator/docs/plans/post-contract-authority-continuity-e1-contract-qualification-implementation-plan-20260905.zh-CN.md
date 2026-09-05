# E1 Approved TaskContract Qualification Implementation Plan

状态：REVIEW PASS / IMPLEMENTATION READY
日期：2026-09-05
输入：

- `post-contract-authority-continuity-and-semantic-revision-convergence-bounded-comparison-20260904.zh-CN.md`
- `post-contract-authority-continuity-producer-consumer-sweep-20260905.zh-CN.md`

## 1. Objective

让 resolved `gate.contract_review` 对唯一 `contract.compile` occurrence 的批准成为 post-contract 全链唯一 TaskContract authority。

完成口径：

1. gate 未批准、批准链不完整、stable bytes 漂移或 recompile 未 re-approve 时，任何声明需要 approved contract 的节点均在 provider、review、
   change-scope、failure classification 或 terminal report 前 fail closed；
2. clarify → recompile → re-approve 后，全链只消费最后一个完整 approval；
3. control consumer 不再直接从 stable filename 推断 authority；projection 只消费 qualified snapshot 或显式 unavailable；
4. 四个已知 workflow 与包含 contract gate 的 custom workflow 由图结构机械保证声明完整。

本 checkpoint 不实现 typed design/plan scope、revision obligations、approved design/plan resolver、canonical plan 删除或 R1 ledger 自动恢复。

## 2. Ownership

| Fact | Owner | Consumer |
|---|---|---|
| contract candidate bytes | `contract.compile` output + stable artifact bytes | qualification resolver 校验输入 |
| contract approval | resolved `gate.contract_review` occurrence | qualification resolver |
| source compile identity | gate creation result/event 的 `source_node_instance_id` | qualification resolver |
| contract/resolution identity | compile result 的 SHA/contract identity | qualification resolver |
| requires qualification | workflow node config | loader validator + runtime consumers |
| qualified snapshot | existing `TaskContractProjectionVerifier` resolution | context/control/projection consumers |

workflow config 只声明需要 qualification；它不选择 compile、不保存 SHA、不拥有批准事实。

## 3. Producer Changes

### 3.1 Gate source lineage

在 `RoutingEngine` 创建任何 human gate 时，把当前 completed source node instance 写入：

- gate node result：`source_node_instance_id`；
- `GateDocument.result`：同值；
- `node_routed` event：同值。

resolver 必须要求三处一致。该字段是通用 occurrence lineage，不包含 contract 特判。非 gate route 不新增字段。

### 3.2 Compile identity

`ContractCompileHandler` 在写完 `contract-resolution.json` 后计算 SHA-256，并在 compile result 增加
`contract_resolution_sha256`。既有 `task_contract_sha256` 与 contract id/status/version 保持。

不得新增 approved-contract/binding 文件，也不得改变 `contract-resolution.json` 的 requirement-negotiation history 语义。

## 4. Unique Qualification Resolver

扩展现有 `TaskContractProjectionVerifier`，替换 latest `finishedAt` 选择：

1. 严格读取 `events.jsonl` 中 workflow `evt-N`，按数值递增验证；忽略非 workflow event，但不得接受乱序、重复或损坏记录；
2. 找出最后一个 routed `gate.contract_review` occurrence；最后 occurrence open/revised/损坏时不得回退较早 approval；
3. gate 必须 resolved、node 必须 COMPLETED、action 必须 `approved`，gate/node action、note、input refs 一致；
4. event、gate result 与 gate node result 的 `source_node_instance_id` 必须一致，并指向唯一 completed `contract.compile`；
5. gate subject refs、gate node input refs、compile output refs 必须绑定同一 stable contract/resolution refs；
6. 重算 contract 与 resolution SHA，对照 compile result；验证 contract schema及 id/status/version；
7. 若 stable contract 对应一个更新的 completed compile 但没有后续完整 re-approval，返回 typed qualification failure；
8. 返回一个 immutable in-process qualified snapshot，包含 contract payload/document、contract/ref/SHA、resolution/ref/SHA、compile node ref、gate ref
   与 approval event id。

保留现有 failure outcome `TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED`，细分 reason code；不得扫描 mtime、latest filename、模型正文或目录排序。

## 5. Definition Enforcement

### 5.1 Explicit declaration

在四个 workflow definition 中，对 `gate.contract_review --approved-->` 后继图上的全部节点设置
`requires_approved_task_contract=true`。`gate.contract_review` 自身及 clarify 回到的 contract negotiation 节点不设置。

### 5.2 Loader graph invariant

`WorkflowDefinitionLoader` 完成 defaults 后执行区域感知校验：

1. 以 `gate_purpose=contract` 定位唯一 contract gate；没有 contract-purpose gate 的 legacy/custom workflow 保持 not configured；
2. contract gate 必须恰有一条 declared `approved` edge；
3. 临时从图中移除该 approved edge，从 workflow entry 计算 pre-approval negotiation region；它自然包含 initial
   intake/requirement/compile/contract-review 链，不依赖这些节点的名字；
4. 从 approved successor 遍历 post-contract region；某条边重新进入 negotiation region 时记录为合法 authority reset，并停止该分支继续传播；
   acceptance contract-gap/ambiguity 回到 contract compile 因此不会把 pre-contract 节点误标为 qualified；
5. post-contract region 的每个节点必须显式为 true，negotiation region 节点不得为 true；
6. post-contract 区的值缺失/非 boolean、negotiation 区误标、多个 contract gate、多个 approved successor 或 sibling 声明不一致均拒绝加载；
   两个区域之外的节点不由 E1 赋予新语义，继续由既有 workflow validation 管理。
7. approved successor 或任一尚未发生 authority reset 的 post-contract 节点若同时属于 negotiation region，视为区域重叠并拒绝加载；
   不得把重叠节点静默当成 reset 入口而跳过 qualification。

规则只使用 gate purpose、approved edge 与 definition graph，不匹配 node id、stage、profile 或 handler 字符串。recompile 后是否已有新 approval
仍由第 4 节 resolver 的 occurrence facts 判定，definition graph 不代替运行时 authority。

## 6. Consumer Convergence

所有 consumer 通过同一 resolver 获得 immutable snapshot；不得各自重新解释 stable 文件。

### 6.1 Agent/context path

- `AgentRunGovernanceContext`：当 definition flag=true 时必须取得 qualification；不再只对 acceptance 特判。
- `ContextBuildPackageHandler`：flag=true 时先 resolve，所有 `task_contract` section 从 snapshot 渲染；删除 stable fallback；
  `contract-resolution` 在 context/prompt 中统一标记为 **Requirement Resolution**，不得展示为 contract approval。
- `RepoScopeGovernance`、`EvidenceManifest`、`ContextStateEvidenceBoundaryRenderer`、`ContextPackageSectionRenderer`：只接收调用方传入的 snapshot。
- `AgentPromptRenderer`：普通 routed stable ref 不产生 trusted contract；Agent 只收到 prepared snapshot payload。

### 6.2 Control path

- `ArtifactValidateHandler`：change-scope 直接使用 snapshot contract；qualification 失败返回 typed FAILED，不降级 unavailable。
- `FailureClassifyHandler`：classification 前 require snapshot；不再允许 null contract 继续分类。
- `HumanReviewGateHandler`：contract review gate 展示当前 candidate并标注 unapproved；flag=true 的后续 gate 只展示 snapshot；两类 packet 中
  `contract-resolution` 均标记为 **Requirement Resolution**，不把其 `approved_by/gate_ref` 投影成 compiled-contract approval。

### 6.3 Memory/report projection

- `WorkstreamMemoryUpdater`：不读 stable contract；由当前 Agent occurrence 的 `PreparedContext` 经 `AgentRunMetadataWriter` 传入 snapshot，
  只写 derived decided scope/non-goals 摘要与 qualified refs/SHA。禁止复制 contract 全文、wrapped payload 或 requirement-resolution 正文。
- memory 中 contract-derived 文本永远不是 authority；prompt 的 contract section 唯一来源是当前 prepared snapshot。re-approve 后到下一 occurrence
  完成前，旧 memory identity 只能以明确 `stale_projection` 标签展示，不能回流到 context、scope、gate 或 report 控制路径。
- `WorkstreamMemoryProjection`：只消费 memory 中的 qualified identity；缺失或与当前 qualification 不一致时显示 unavailable/stale，不回读 stable contract。
- `ReportGenerateFinalHandler`：terminal node require qualification，并从 snapshot 渲染 TaskContract 与 Requirement Resolution；失败不得发布伪完整报告。

若一个 consumer 只需要展示，可使用 snapshot 的只读 projection；不得因此新增第二 read model 或 durable copy。

## 7. Failure And Compatibility

- pre-contract：qualification absent 合法；不得误报 failure。
- configured post-contract：missing/open/revised/ambiguous/tampered/stale/unapproved 一律 typed failure，且在该 consumer 的外部副作用前发生。
- legacy workflow 无 contract gate：明确 `NOT_CONFIGURED`，保持现有流程；不得伪称 qualified。
- legacy workstream 若已有 resolved contract gate，但 gate/node/event 缺少新 `source_node_instance_id`，明确 qualification failure；不得按目录、时间或
  contract SHA 猜测旧 source，也不得增加 compatibility fallback。旧样本只保留审计，不 resume。
- resolved gate 多文件写入的 partial state：fail closed 到既有 recovery/abort；本 checkpoint 不承诺自动修复或原地续写。
- recompile without re-approve：拒绝；re-approve 后 authority 原子切到新完整链，不保留旧 snapshot fallback。

## 8. Planned Production Surface

Ledger/definition：

- `dual-ledger-file/.../RoutingEngine.java`
- `dual-runtime/.../lifecycle/WorkflowDefinitionLoader.java`
- 四个 workflow JSON（含 test resource experiment）

Qualification producer/resolver：

- `ContractCompileHandler.java`
- `TaskContractProjectionVerifier.java`
- 现有 qualified snapshot/`AgentRunGovernanceContext.PreparedContext` 的最小字段扩展

Consumers：

- `AgentRunGovernanceContext.java`
- `ContextBuildPackageHandler.java`
- `RepoScopeGovernance.java`
- `EvidenceManifest.java`
- `ContextStateEvidenceBoundaryRenderer.java`
- `ContextPackageSectionRenderer.java`
- `ArtifactValidateHandler.java`
- `FailureClassifyHandler.java`
- `HumanReviewGateHandler.java`
- `ReportGenerateFinalHandler.java`
- `WorkstreamMemoryProjection.java`
- adapter 的 `AgentPromptRenderer.java`、`WorkstreamMemoryUpdater.java`、`AgentRunMetadataWriter.java` 与唯一调用接线

实施前以 symbol sweep 复核路径；若发现需要新增 durable binding、修改 E2/E3 schema 或重构通用 evidence framework，立即停止回 comparison。

## 9. Tests

1. Routing sequence：gate/node/event source identity 三处一致；非 gate route 不受影响。
2. Compile：contract/resolution 双 SHA 与 output refs 完整。
3. Resolver：最后完整 approval 正例；open、resolved `clarify/reject`、revised、corrupt、乱序、重复、source mismatch、ref mismatch、SHA drift
   全部拒绝且不回退。
4. Recompile：未 re-approve 拒绝；clarify→recompile→re-approve 后只认新 approval。
5. Crash slices：gate、node、event/current 任一缺失均 qualification 不可见；冷启动完整链得到同一 snapshot。
6. Definition：四 workflow parity；initial negotiation region 不误标；acceptance contract-gap/ambiguity 回流进入 negotiation region 后停止
   qualification 传播；再次 contract approval 后 post-contract 区重新要求 qualification；custom workflow 漏标、误标、非 boolean、多个
   contract gate/approved successor 均 load failure；无 contract gate legacy 合法。
7. Agent/context：configured design/plan/implementation/self-test/acceptance 在 missing qualification 时 provider count=0；pre-contract 不误拦。
8. Control：change-scope、failure classification、post-contract human packet 在 stable bytes 未批准或漂移时 fail closed。
9. Projection：memory 只含派生摘要 + identity，旧 identity 标 stale 且不可回流；Requirement Resolution 标签一致；final report 只显示 snapshot
   identity；恢复 stable fallback 或复制 contract payload 的 mutation test 必须失败。
10. Sibling sequence：四 workflow 从 initial design 到 terminal report 携同一 contract/ref/SHA；re-approval 后全链切换。
11. Legacy workstream：resolved gate 缺 `source_node_instance_id` 时 fail closed，不回退 latest compile。
12. Full reactor、workflow validation、`git diff --check`。

测试断言 typed reason、refs/SHA、provider/side-effect count 和 graph behavior，不以 Markdown 正文作为 authority。

## 10. Implementation Order

1. 先写 resolver、recompile/reapprove、crash 和 custom-definition failing tests。
2. 写 gate source identity 与 resolution SHA producer。
3. 替换 verifier selection，并完成 cold-start tests。
4. 加四 workflow config 与 loader graph validation。
5. 收敛 agent/context consumers。
6. 收敛 control consumers。
7. 收敛 memory/report projections，删除 stable fallbacks。
8. 运行 direct-reader subtraction sweep，确认 stable contract/resolution 只剩 writer、contract gate candidate 展示、pre-approval
   revision-history 与 resolver 内部读取。
9. focused tests → full reactor → workflow validation → diff-check。
10. 独立代码级 diff review 后才允许选择性提交。

全程不调用 provider、不恢复 PNBV、不开始 E2/E3/E4。

## 11. End-to-End Invariant

| Dimension | Proof |
|---|---|
| Declared by | workflow `requires_approved_task_contract` 与 resolved contract gate action |
| Proven by | event-ordered gate/source compile lineage + contract/resolution refs/SHA |
| Consumed by | unique qualification resolver 输出的 immutable snapshot |
| Enforced before | configured provider、review、change-scope、classification、memory publication、terminal report |
| Failure route | typed qualification failure；partial ledger state进入既有 recovery/abort，不回退旧 approval |
| Cannot-bypass | recompile/reapprove、custom graph、direct-reader subtraction、crash/cold-start、四 workflow sequence tests |

## 12. Readiness

首轮独立评审为 `WARN / NOT READY（P0=1/P1=0/P2=3）`。P0 指出朴素 post-contract 全图可达会经 acceptance contract-gap 回流
重新进入 pre-contract 区，导致四个 bundled workflow 不可加载。delta 已改为：移除 contract approved edge 后计算 negotiation region；
post-contract 遍历重新进入该区域即停止，并补回流/re-approval 测试。三项 P2 同步吸收：旧 ledger 无 source identity 明确 fail closed、memory
只存派生摘要与 identity 且禁止回流、Requirement Resolution 展示重标注。

delta 独立复审结论为 `PASS / IMPLEMENTATION READY（P0=0/P1=0/P2=2）`。两项非阻塞 P2 已吸收：区域重叠明确 load failure；
resolver 测试点名 resolved `clarify/reject` 不得回退。

`Implementation READY: YES`。实施与独立 diff review 仍需检查：

- event/gate/node/source identity 是否足以唯一绑定 approved compile；
- 区域感知 graph rule 是否能同时覆盖 initial negotiation、post-contract 与 acceptance→contract renegotiation 回流；
- qualified snapshot 是否真为唯一 consumer API；
- adapter memory 接线是否会复制 authority；
- planned file surface 是否完整且仍比新增 durable binding 更小。
