# Post-Contract Authority Continuity Producer/Consumer Sweep

状态：SWEEP REVIEW PASS / IMPLEMENTATION READY
日期：2026-09-05
基于：`post-contract-authority-continuity-and-semantic-revision-convergence-bounded-comparison-20260904.zh-CN.md`

## 1. 结论

Candidate E 可行且比新增独立 binding artifact 更小，但实施边界必须覆盖三条连续 authority 链，而不是只给 revision prompt 补合同：

1. `contract.compile` → resolved `gate.contract_review` → approved TaskContract qualification；
2. typed design candidate → architecture review/decision → resolved human design gate → approved design identity；
3. typed plan candidate → plan review/decision → resolved human plan gate → approved plan identity。

随后：

- 所有 post-contract control consumer 只消费 qualified TaskContract；
- plan 初始与 revision 都消费 approved contract，初始 plan 还消费 approved design；
- implementation/self-test 消费 approved contract + approved plan；
- acceptance/failure classification/change-scope/final report 消费同一 qualified contract；
- design/plan human revision 通过 occurrence lineage 重建 parent candidate、review、human note 与累计 obligations。

这不是 OKX 特判。四个 workflow definition 的 contract/design/plan/implementation/acceptance 主链同构，缺陷来自共享 runtime/ledger/adapter。

## 2. Approved Contract Transition

### 2.1 现有事实

- `ContractCompileHandler` 写 stable `task-contract.json`、`contract-resolution.json`，并在 compile node result 写
  `task_contract_sha256/contract_id/contract_status/contract_version`。
- `contract-resolution.json` 描述的是**requirement review 的协商结果**，其 `approved_by/gate_ref` 指向
  `gate.human_requirement_review`，不是 compiled TaskContract 的批准事实。
- `RoutingEngine` 创建 human gate 时持久化 `subject_input_refs/source_outcome`，但没有 source node instance identity。
- resolved `gate.contract_review` 的 `human_action=approved` 是真正 contract approval；当前 verifier 没有消费它。
- `TaskContractProjectionVerifier.resolve()` 当前按 compile `finishedAt` 选择 latest completed compile，未证明该 compile 被 contract gate 批准。

### 2.2 最小 authority

不新增 approved-contract 文件。使用现有 durable facts，并补两个缺失 identity：

1. `RoutingEngine` 创建任何 human gate 时，在 gate result 与 `node_routed` event 中加入
   `source_node_instance_id`。这是通用 source lineage，不包含业务规则；gate resolution 沿用原 result，因此批准动作自然绑定 source occurrence。
2. `ContractCompileHandler` 在 result 中加入 `contract_resolution_sha256`；TaskContract SHA 已存在。

统一扩展现有 `TaskContractProjectionVerifier`：

1. 按 `events.jsonl` event id 顺序定位最后一个 routed `gate.contract_review` occurrence；禁止目录顺序、mtime、finishedAt 选 owner。
2. 要求对应 gate resolved 且 action=`approved`，gate node也必须 COMPLETED，二者 action/note/result 一致。
3. 通过 `source_node_instance_id` 读取唯一 completed `contract.compile` source。
4. 验证 gate subject refs、gate node input refs、compile output refs 一致。
5. 验证 stable contract/resolution bytes 的 SHA 与 compile result 一致，并验证 contract identity。
6. 最新 gate open、损坏、半解析、source 缺失或 SHA 不符时失败；不得回退较早 approval。

`latest compile by finishedAt` 从 owner 退役。它可用于发现“批准后又出现未批准 recompile”冲突，但不能选择 authority。

### 2.3 Crash / restart

本 checkpoint 不修复既有 file-ledger 多文件事务或自动恢复：

- gate file 已写但 `node_routed` event/current 未落：不是可见 qualification，fail closed；
- gate resolved 但 gate node completion/routing 未落：不是完整 approval qualification，fail closed，进入既有 operator recovery/abort 域；
- gate node和 resolved gate 完整、successor routing 完整：冷启动解析同一 qualification；
- stable contract 被 recompile 覆写但未 re-approve：SHA/source 冲突，fail closed；
- 最新 gate 损坏或未 resolved：不得回退较早 approval。

因此本能力保证安全拒绝，不宣称顺带解决 R1 ledger crash liveness。

## 3. TaskContract Consumer Classification

### 3.1 Authority/control consumers

| Consumer | 当前行为 | 收敛要求 |
|---|---|---|
| `TaskContractProjectionVerifier` | latest compile + stable bytes | 改为唯一 approved-gate qualification resolver |
| `AgentRunGovernanceContext` | 只对 routed acceptance binding 强制 | intake 前允许 absent；design 及以后全部 require qualified，直接把 qualified payload 提供给 agent |
| `ContextBuildPackageHandler` | 只对 acceptance 调 verifier；其他 profile 可读 stable fallback | architecture/plan/implementation/acceptance 等 post-contract profile 全部 require qualified；删除 post-contract fallback |
| `RepoScopeGovernance` | qualified absent 时直接读 stable contract | post-contract 只接收 qualified contract；contract-discovery 前置阶段允许 absent，不再直接读取 stable contract |
| `ArtifactValidateHandler` | 直接读 stable contract；缺失时返回 unavailable | change-scope evaluation 必须使用 qualified contract；qualification 失败为 typed failure，不得降级 unavailable |
| `FailureClassifyHandler` | stable contract 可缺失并以 null 分类 | acceptance failure classification 必须使用 qualified contract；缺失/漂移先失败，不得继续分类 |
| `WorkstreamMemoryUpdater` | 每次 agent 完成后直接读取 stable contract | 由本 occurrence 的 prepared qualified context 投影 decided scope/non-goals/acceptance；不得自行读 stable 文件 |
| `WorkstreamMemoryProjection` | review brief 再读 stable contract acceptance criteria | 只读已带 qualified contract ref/SHA 的 memory projection；identity 缺失时显示 unavailable，不重读 stable 文件 |
| `HumanReviewGateHandler` | 所有 gate packet 直接读 stable contract | contract gate 可展示当前 unapproved candidate并标注 candidate；design/plan/implementation/acceptance gate 必须展示 qualified contract |

`AcpxAgentTaskHandler.ChangeBoundaryAuthoritySnapshot` 只保护 contract bytes 不被 agent 修改，不解释 contract 语义，保留为 integrity guard，
但不能替代 qualification。

### 3.2 Derived projections

| Consumer | 当前行为 | 收敛要求 |
|---|---|---|
| `EvidenceManifest` | qualified 缺失时把 stable contract/resolution 加入 manifest | post-contract 只投影 qualified contract/resolution identity；前置阶段可 absent |
| `ContextStateEvidenceBoundaryRenderer` | qualified 缺失时把 stable contract 标为 Trusted Fact | 删除 fallback；只有 resolver 输出可称 Trusted Fact |
| `ReportGenerateFinalHandler` | final report 直接读取 stable contract/resolution | 从 completion lineage 对应的 qualified identity渲染；失败不得生成伪完整报告 |
| `AgentPromptRenderer` | routed stable contract/resolution 可作为 canonical direct input | post-contract prompt 的 contract payload 只来自 prepared qualification；普通 routed ref 不授予 authority |
| `WorkstreamMemoryProjection` / session compaction | stable path 被写成 scope source | 输出 qualified ref/SHA；不得只给裸 stable path |

### 3.3 Other direct readers found by sweep

上一轮评审列出的 reader 之外，生产代码还存在：

- `ArtifactValidateHandler`：实际 Git scope 的控制 consumer；
- `HumanReviewGateHandler`：human decision packet 的输入；
- `ReportGenerateFinalHandler`：terminal projection。

三者必须纳入同一 checkpoint，不允许以“不是 agent/context node”为由遗漏。

### 3.4 Plan projection sibling

`ArtifactContractValidator` 还会把每次 plan result latest-wins 写到 stable
`artifacts/plan/plan_artifact.json`；`MetricsProjectionWriter.planAuthorizedFiles()` 读取它并影响重复读取治理指标。该链不是 Agent
`ReadReusePolicy` 的运行时授权输入，但仍会让未批准 candidate 获得 `plan_authorized` 投影效果，因此必须按第 5.1 节删除 canonical writer
与 metrics 豁免。`AgentOutputContractDocument` / `AgentResultDocumentProjector` 对 plan result 类型的正常 occurrence sidecar 投影保留，它们不是
stable-file reader。

### 3.5 Mechanical qualification declaration

不得通过 node id、stage 名或 handler 名字符串推测“post-contract”。四个 workflow definition 的语义消费者显式声明
`requires_approved_task_contract=true`：design、plan、implementation/self-test、acceptance、change-scope/failure classification、后续 human gate
与 terminal report 节点均声明；contract discovery、requirement negotiation、contract compile 与 contract review gate 不声明。

definition loader/validator 必须机械验证：

1. 声明为 true 的节点在 provider、review、业务写入或 terminal projection 前经唯一 qualification resolver；
2. 四个 workflow 的同类节点声明一致，合法 topology 差异单独列出；
3. pre-contract 节点不得误声明；
4. config 只声明“需要 qualification”，不选择 compile、不复制 contract SHA，也不成为第二 authority。

对任意 custom definition 还必须验证区域感知的图不变量：临时移除 contract-purpose gate 的唯一 approved edge，从 workflow entry 计算
pre-approval negotiation region；再从 approved successor 遍历 post-contract region，重新进入 negotiation region 时停止该分支传播。只有
post-contract region 节点必须声明该 flag。这样 acceptance failure 合法回流到 contract compile/clarify/intake 时会重置 qualification 需求，
下一次完整 approval 才重新进入 post-contract region。该规则按 gate purpose、edge outcome 与图结构判定，不按 node id、stage 名或 handler 名猜测。

## 4. Contract Resolution Semantics

`contract-resolution.json` 保留，但明确降级为 requirement-negotiation history：

- owner：`ContractCompileHandler` 对 approved requirement-review lineage 的编译结果；
- `approved_by/gate_ref` 只说明 open questions 由哪个 requirement gate 解决；
- 它不证明 compiled TaskContract 已通过 `gate.contract_review`；
- compile result 必须绑定其 SHA，approved contract resolver 同时验证 contract 与 resolution bytes；
- `TaskContractRevisionContextResolver` 在 contract clarify/recompile 的 pre-approval 路径继续消费它；
- prompt/context/manifest/human packet/final report 只能通过 qualified projection 展示，标签改为 Requirement Resolution，禁止显示成 contract approval authority。

直接消费者分类：

| Consumer | 用途 | 处置 |
|---|---|---|
| `TaskContractRevisionContextResolver` | pre-approval clarify lineage | 保留 control 语义；验证 requirement gate |
| `ContextBuildPackageHandler` | agent context | post-contract 从 qualification 投影 |
| `ContextStateEvidenceBoundaryRenderer` | prompt 状态说明 | 改称 requirement resolution，非 approval |
| `EvidenceManifest` | evidence index | 记录 qualified resolution ref/SHA |
| `HumanReviewGateHandler` | human packet | contract gate 前为 negotiation history；后续 gate 从 qualification 投影 |
| `ReportGenerateFinalHandler` | terminal report | 从 completion-bound qualification 投影 |
| `AgentPromptRenderer` | routed artifact | 不因路径存在而赋予 authority |

## 5. Authored Artifact Authority

### 5.1 新发现的 sibling

四个 workflow 中：

- design review/decision → human design gate 的 refs 是 reviewer outputs，不是 design candidate；
- human design approve → `plan.claude_plan` 继续携 reviewer outputs，approved design 没有显式 authority；
- plan review/decision → human plan gate 同样如此；
- human plan approve → implementation context 没有显式 approved plan identity；
- stable `artifacts/plan/plan_artifact.json` 由 `ArtifactContractValidator` 在每次 plan occurrence 后 latest-wins 改写；
  `MetricsProjectionWriter.planAuthorizedFiles()` 会读取其中的 `edit_files/reference_files`，并把重复读取指标标为
  `plan_authorized`，从而压掉缺失 `reread_reason` 的治理信号。它不直接授予 Agent 工具权限，但确实是未批准 plan
  影响治理投影的第二事实路径。

因此只补 TaskContract 仍不闭合完整链。

该 sibling 的最小收敛不是让 metrics 再消费一份 qualified plan，而是删除跨边界豁免：

- 删除 `MetricsProjectionWriter.planAuthorizedFiles()` 对 canonical stable plan 的读取；重复读取是否缺理由只由 occurrence 的实际读取事实、
  read-reuse contract 与显式 `reread_reasons` 判定；
- 在确认全仓无剩余 production consumer 后，删除 `ArtifactContractValidator` 对 canonical
  `artifacts/plan/plan_artifact.json` 的写入；保留 occurrence-scoped plan sidecar 作为 plan candidate；
- `plan.edit_files` 仅在第 5.2 节 approved-plan resolver 后拥有 implementation context 的计划边界，不反向拥有读取治理指标。

### 5.2 Bounded resolver

新增一个仅服务 design/plan authored stages 的 lineage resolver，不做通用 artifact platform。resolver 必须按 workflow 中已声明的真实
producer chain 验证两种 gate subject，不能假设所有 gate 前都有 reviewer：

1. reviewed route：从 resolved human gate input refs 找到唯一 reviewer sidecar；sidecar → reviewer node → context package → context node
   inputs → parent design/plan candidate occurrence sidecar；
2. direct route：small-plan experiment 的 `plan.claude_plan → gate.human_plan_approval` 由 gate subject 直接绑定唯一 plan candidate sidecar，
   不虚构 reviewer；
3. 两种 route 都必须先与 frozen workflow definition 的 declared predecessor 对照；未声明的 direct/reviewed 形态立即失败；
4. 每跳验证 workstream、iteration、node definition、source output ref、SHA、input/output refs 和 completed status；
5. approved gate action决定该 candidate 是否成为 approved design/plan；revise action只形成 revision baseline，不产生 approval。

用途限定：

- initial plan 必须消费 approved design；
- implementation与 self-test 必须消费 approved plan；
- design/plan revision 必须消费其 parent candidate + reviewer + human revise decision；
- acceptance 继续消费实际 Git/self-test/approved contract，不把 planned files 当实际变更 owner。

不得扫描 canonical plan、runs/latest、mtime 或模型 session。

## 6. Typed Design And Revision Baseline

### 6.1 Design artifact

为 `design.claude_design` 增加专用 `design_result`：

```text
design.planned_files[] = { path, operation, responsibility }
revision_responses[] = { obligation_id, disposition, evidence }
```

- deterministic enforcement 只使用 `path/operation`；`responsibility/evidence` 为 reviewer 输入，不解析为控制规则；
- initial candidate 的 `revision_responses=[]`；revision 必须 exact-cover baseline obligations；
- path canonical/unique，且为 approved TaskContract scope subset；operation 必须匹配 contract；
- 完整性继续由 architecture reviewer 判断，不新增 exact-set contract 字段。

### 6.2 Plan artifact

保留既有 `plan_result.plan.edit_files`，不新增第二 plan schema：

- `edit_files` canonical/unique，并对 approved contract scope 做 path subset 校验；
- plan 没有 operation 字段，因此不伪造 operation enforcement；实际 ADD/MODIFY/DELETE 仍由 Git/change-scope owner 判断；
- 为 existing `PlanResult` 增加与 design 同形的 `revision_responses`，只在 plan human revision 时要求 exact coverage。

### 6.3 Revision baseline

新增 design/plan 专用 resolver，从当前 iteration 的 `triggerNodeInstanceId` 沿第 5 节 lineage重建，并区分两种合法 revision trigger：

- human revise：design gate，以及四 workflow 的 human plan gate；必须绑定 human gate/ref/SHA 与 nonblank note；subject 可按 declared topology
  为 reviewed route 或 experiment direct-plan route；
- review-driven revise：三个 bundled workflow 的 `plan.decision NEEDS_REVISION → plan.claude_plan`；必须绑定 decision node、reviewer sidecar与
  parent plan candidate，不要求或伪造 human note。

baseline 内容为：

- parent candidate ref/SHA；
- reviewer sidecar ref/SHA；
- trigger decision 或 resolved human revise gate ref/SHA；human route 还必须有 nonblank note；
- 同 stage 所有 ancestor semantic revisions 的 reviewer `must_fix`；human revisions 额外累计 human notes。direct-plan route 没有 reviewer时，
  只生成 human-note obligation。

obligation id 固定为：

- `sha256:<review-sidecar-bytes>#must_fix:<zero-based-index>`；
- `sha256:<resolved-gate-bytes>#human-note:0`。

所有历史 obligations 在该 stage 获 human approve 前持续要求 candidate exact response。reviewer仍输出现有 decision/must_fix；不新增
逐 obligation assessment schema。human approve 关闭该 stage obligations。

这是对上一版 bounded comparison E3 的有意收缩：reviewer 的语义职责继续由既有 `decision/must_fix` 承担，不新增逐 obligation
`satisfied/unsatisfied` assessment schema。deterministic 层只验证 candidate 对累计 obligation id 的 exact response coverage；是否真正解决
obligation 仍由后续 reviewer decision 判定。这样避免复制 reviewer 判断成为第二 SoT，同时保留遗漏历史义务时的机械拒绝。

baseline JSON 只是 occurrence projection，SoT 仍是 gate/iteration/candidate/review artifacts。写入中断时不得启动 provider；冷启动从 SoT
重建同一 refs/SHA/ids，不读取半写 projection。

## 7. Four-Workflow Sweep

以下四个 definition 必须进入结构和 sequence parity tests：

1. `agent-lifecycle.contract`
2. `agent-lifecycle.full`
3. `agent-lifecycle.terminal-delivery`
4. `agent-lifecycle-small-plan-human-review-experiment`

它们的 contract gate、design revise、design approve→plan、plan approve→implementation 主边同构。plan revise 有两种合法拓扑：三个 bundled
workflow 同时有 reviewer-driven `plan.decision NEEDS_REVISION` 与 human-gate revise；experiment 无 plan Codex review，plan candidate 直接进入 human
gate。两种都必须测试，不能用一条假想 reviewer 链抹平差异。

## 8. Minimal Checkpoints

### E1 Contract qualification

Production surface：

- ledger gate source lineage；
- compile resolution SHA；
- existing verifier；
- 第 3/4 节全部 readers；
- 四 workflow 的 `requires_approved_task_contract` 声明、loader validation 与 parity tests。

### E2 Typed design scope

Production surface：

- `AgentResultEnvelope` / prompt example / projector / sidecar reader / validator / ContractRegistry；
- design scope validator；
- architecture-review context subject读取 typed design candidate。

### E3 Authored revision + approvals

Production surface：

- bounded design/plan lineage resolver；
- design/plan `revision_responses`；
- approved design→plan、approved plan→implementation/self-test injection；
- design/plan human gate packet 只读投影 active obligation ids、来源 refs/SHA 与 candidate `revision_responses`，让 human approve 成为知情裁闭；
- iteration sequence/cold-start/oscillation replay。

### E4 Downstream parity

Production surface：

- plan `edit_files` contract subset；
- 删除 canonical `artifacts/plan/plan_artifact.json` writer 与 metrics `plan_authorized` stable-file 豁免；
- actual Git scope保持现有 owner；
- acceptance/failure/final-report qualification parity；
- legacy/fresh behavior和四 workflow sweep。

每项 clean commit + 独立 diff review；前一项 PASS 后才能进入下一项。若 E1 实施发现必须新增独立 approved binding artifact，立即停止并回到
bounded comparison，不在实现中临时扩张。

## 9. Cannot-Bypass Sequence Tests

1. contract candidate存在但 gate 未 approved：design provider count=0。
2. approved gate source不是唯一 completed compile、subject refs不一致、contract/resolution SHA 漂移：fail closed。
3. recompile 未 re-approve：所有 post-contract control consumer拒绝；latest compile不得获权。
4. clarify → recompile → re-approve：authority 只切换到 event order 中最后一个完整 approval，旧 approval 不得滞留；后续 consumer 全部携新 identity。
5. resolved gate write与node/current/event任一中断：qualification不可见，零下游 provider；不冒充自动恢复。
6. 冷启动读取完整 gate/compile/event链得到同一 qualification。
7. 第 3 节每个 direct reader 的旧 stable fallback 删除突变测试：恢复任一 fallback 必须使测试失败；四 workflow qualification config
   缺失、误置或 sibling 不一致也必须失败。
8. 四 workflow initial design、连续 design revisions、initial/revised plan、implementation/self-test 均携同一 contract identity。
9. typed design path逃逸、重复、越 scope、operation不符在 reviewer前失败；prose 声称 clean不能绕过。
10. design revision遗漏任一历史 obligation失败；事务→scope与scope→事务两种顺序均不得回退。
11. plan revision覆盖 bundled reviewer-driven、bundled human revise、experiment direct human revise 三种 sequence；各自 baseline/coverage
    与 declared predecessor 一致，`edit_files` 越 scope 在 implementation前失败。
12. human design approve 后 plan只消费被批准 candidate；较早/较晚未批准 candidate、stable projection均不能替代。
13. human plan approve 后，初始入口、self-test 修复环、`gate.human_implementation_repair revise`、acceptance
    `REJECT_IMPLEMENTATION_BUG/REJECT_CONTEXT_INSUFFICIENT/REJECT_TOOL_LIMITATION` 再入、`gate.human_acceptance revise` 的每个
    implementation/self-test occurrence，以及 `REJECT_VERIFICATION_INSUFFICIENT → implement.claude_self_test_report` 的直接 self-test 再入，
    都只消费同一 approved plan；删除 lineage或篡改 SHA 使 provider count=0。
14. 删除 canonical plan writer 后不得再产生 stable `plan_artifact.json`；恢复 stable writer 或 metrics reader 必须使 subtraction test 失败；
    未批准 plan candidate 不得把重复读取从 `missing` 改成 `plan_authorized`。
15. actual Git 越界仍由 change-scope失败，planned path不能授权实际越界。
16. acceptance failure classification、human packet、memory/compaction、manifest、final report在 stable contract漂移时不得继续投影旧的“可信”结论。
17. baseline projection半写/删除后冷启动从 ledger重建；若 SoT 缺失则失败，不回退 latest/mtime。

## 10. Implementation Readiness

Sweep 首轮独立评审为 `WARN / NOT READY（P0=0/P1=1/P2=4）`。P1 揭示 canonical plan latest-wins 文件仍被
`MetricsProjectionWriter` 消费；评审把它描述成直接喂给运行时 `ReadReusePolicy` 过强，独立复核确认其真实影响是治理指标
`plan_authorized` 与 missing reread-reason 判定。delta 已选择删除该 stable writer/metrics 豁免，而不是让 metrics 再消费 approved plan。

其余 P2 已吸收：正向 re-approve authority 转移、所有 implementation re-entry、reviewer schema 收缩理由，以及四 workflow
`requires_approved_task_contract` 的机械声明与 parity validation。delta 独立复审结论为
`PASS / IMPLEMENTATION READY（P0=0/P1=0/P2=3）`；三项非阻塞 P2 已归入 E1/E3：custom definition 图可达校验、human gate
obligation/response 投影、`REJECT_VERIFICATION_INSUFFICIENT` direct self-test re-entry。E1 实施计划仍需重点检查：

- gate source lineage 是否足以绑定批准的 compile occurrence；
- crash slice 是否诚实停留在 fail-closed，而未暗中承诺 R1 自动恢复；
- direct reader 是否还有遗漏，删除 plan metrics 豁免是否比改道 approved plan 更小且保持 owner 单一；
- approved design→plan、approved plan→implementation 是否是必要 sibling；
- E2/E3 schema 增量是否还能更小；
- 四 workflow tests与 checkpoint拆分是否可执行。

Sweep 门槛已满足。下一步只制定 E1 Contract qualification implementation plan 并独立评审；评审通过前不修改 production、不调用 provider、
不恢复 PNBV。

## 11. End-to-End Invariant

| Dimension | Proof |
|---|---|
| Declared by | workflow `requires_approved_task_contract` + resolved contract/design/plan human gates + typed contract/design/plan artifacts |
| Proven by | event-ordered gate/source lineage、artifact SHA、iteration/reviewer/candidate refs |
| Consumed by | unique contract qualifier、bounded authored-stage resolver、scope validators、downstream control/projection consumers |
| Enforced before | design/plan/implementation provider、reviewer、change-scope、failure classification、terminal report |
| Failure route | typed pre-provider/validation failure；semantic error回 human gate；ledger partial write fail closed到既有 recovery域 |
| Cannot-bypass | 四 workflow config parity、direct-reader fallback mutation、canonical-plan subtraction、crash/cold-start、recompile/reapprove、obligation oscillation、approved-stage lineage tests |
