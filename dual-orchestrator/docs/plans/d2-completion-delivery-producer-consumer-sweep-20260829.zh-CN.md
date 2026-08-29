# D2 Completion / Delivery Projection Producer-Consumer Sweep

日期：2026-08-29
状态：READ-ONLY SWEEP COMPLETE / PROCEED WITH TWO BOUNDED CHECKPOINTS / NOT IMPLEMENTED

## 1. 目的与边界

本轮只回答三个问题：

1. completion、self-test、acceptance 与 delivery 的 authoritative fact 分别由谁产生；
2. CLI、API、UI、final report 与 scripts 当前是否消费同一事实；
3. D2 应实施什么最小改动，哪些原计划设想应删除。

本轮不修改 production、workflow definition、schema、provider 配置或 real-case。基线为 clean checkpoint
`d04d0a58`（D1 Accepted Delivery Candidate）。

## 2. 结论先行

D2 的问题不是“缺一个包揽所有状态的大 read model”，而是两个独立边界：

1. **verification lineage 在 acceptance agent 后没有继续投影**：self-test sidecar 曾经作为
   `context.acceptance_codex_verifier_review` 的 routed input，被该 context node 验证并渲染；但 context node 与
   acceptance agent 的 output refs 都没有把这条 machine lineage 继续带到 final-report node。final report 因此只看到
   acceptance sidecar，真实 E2E 会把 self-test 显示为 `not available`。
2. **completion/delivery 展示存在多个解释者**：D1 已提供唯一 `DeliveryApplicationService.View`，但 CLI、server、UI 与
   `WorkstreamAttentionEvaluator` 仍直接解释 `delivery.json` 或 `merge-plan.json`；server 还扫描 `runs/` 猜最终 acceptance，
   list API 同时暴露 legacy `workstream.json.status` 与 current workflow status。

现有 ledger refs **足以**重建最终 verification lineage，不需要新增 durable
`VerificationEvidenceSelection`、schema 或 workflow node。D2 应拆成两个有序 checkpoint：

- **D2-A Completion Evidence Lineage**：新增一个只读、fail-closed 的 lineage resolver；final report 与 completion
  projection 消费它；删除 report 的 mtime/文件名猜测。
- **D2-B Projection Subtraction**：复用 D1 的 `DeliveryApplicationService.View`，删除 legacy/raw delivery 解释路径，
  统一 current status 与 source labels。

原计划中的通用 `CompletionDeliveryReadModel` 不实施。它把 workflow current、verification evidence、completion audit 与
delivery lifecycle 四个不同 owner 压进一个对象，长期会形成第二个状态 owner。

## 3. Authoritative producer map

| Fact | Authoritative producer / owner | Durable fact | 生命周期 |
|---|---|---|---|
| workflow 当前状态 | `RoutingEngine` | `workflow/current.json` | mutable current |
| workstream 创建身份 | `FileLedgerStore.initializeWorkstream` | `workstream.json` | immutable identity；其中旧 status/stage/iteration 只是 initial metadata |
| self-test 结论 | self-test Agent result + `ArtifactContractValidator` | validated self-test contract sidecar、producer node/result/execution refs+SHA | immutable occurrence |
| Codex acceptance 结论 | acceptance Agent result + `ReviewDecisionHandler` | validated acceptance sidecar；`GoalSatisfactionDocument` 绑定 acceptance decision 与 evidence refs | immutable occurrence + current goal projection |
| final report | `ReportGenerateFinalHandler` | iteration report + `latest-final-report.md` | pre-human-delivery snapshot projection |
| completion artifact audit | `CompletionAuditWriter` | `domain/completion-audit.json` | terminal artifact-completeness projection |
| 人工最终 acceptance | resolved `gate.human_acceptance` | gate document + gate node result | immutable human decision occurrence |
| 被人工批准的 bytes | D1 `DeliveryCandidateWriter` / `AcceptedDeliveryCandidateResolver` | content-addressed candidate + gate subject ref | immutable delivery occurrence |
| delivery apply/commit 状态 | `DeliveryApplicationService` | `delivery/merge-plan.json` | mutable delivery lifecycle |
| delivery operator view | `DeliveryApplicationService.View` | derived from accepted candidate + merge plan | read model，不是新 SoT |

关键区分：`latest-final-report.md` 在 `acceptance.generate_final_report -> delivery.prepare_pr ->
gate.human_acceptance` 之前生成。它不可能包含后续人工 action、accepted candidate apply 或 commit 状态；它只能是
**pre-human-delivery verification snapshot**。终态后的 live completion view 必须组合 ledger current、completion audit、
resolved human gate/candidate 与 delivery view，不能回写 Markdown 冒充实时状态。

## 4. Existing routed lineage 是否足够

### 4.1 已存在的完整链

生产 graph 与 ledger 写法形成以下可机械追踪链：

```text
self-test validated sidecar
  -> implement.self_test_decision input/output refs
  -> implement.artifact_validation input/output refs
  -> context.acceptance_codex_verifier_review.inputRefs
  -> acceptance context package ref（其文件名绑定 context node instance）
  -> acceptance.codex_verifier_review.inputRefs
  -> acceptance validated sidecar
  -> acceptance.decision input/output refs
  -> GoalSatisfactionDocument.evidenceRefs
  -> acceptance.generate_final_report.inputRefs
```

代码证据：

- `RoutingEngine.completeAndRoute()` 把 predecessor `outputRefs` 原样写入 successor `inputRefs`；
- `ReviewDecisionHandler.evaluate()` 成功时原样保留 `context.instance().inputRefs()`；
- `ArtifactValidateHandler` 把 validated sidecar refs 继续放入 output refs；
- acceptance context node 的 `inputRefs` 仍持久化 self-test sidecar，且其 context package ref 被写入 output refs；
- acceptance Agent node 持久化自己的 `inputRefs`，因此可以从 context package ref 精确回到 producer context node；
- `AgentOutputLineageVerifier` 已能验证 sidecar -> node -> execution -> SHA 的 occurrence lineage；
- `GoalSatisfactionWriter` 在 `acceptance.decision` 写入 outcome、domain outcome 与 evidence refs。

### 4.2 当前丢失发生在哪里

`ContextBuildPackageHandler.buildLegacyResult()` 只继续路由 context package、acceptance metadata 与已验证
TaskContract proposal ref；`AcpxAgentTaskHandler.evidenceOutputRefs()` 只发布本 run 的 stderr/transcript/output 与 repository
fact binding。self-test ref 因而没有直接到达 final-report node。

但这不是事实永久丢失：acceptance Agent node 的 input refs 仍指向 context package，而对应 context node 的 input refs 仍指向
self-test sidecar。因此新增 durable selector 会复制已有 ledger lineage，没有必要。

### 4.3 最小 resolver

新增 package-level `CompletionEvidenceLineageResolver`（名称可在实施计划中校准），只做以下机械工作：

1. 从 `GoalSatisfactionDocument.evidenceRefs` 或 report node routed refs 解析唯一 final acceptance sidecar；
2. 使用 `AgentOutputLineageVerifier` 重验 acceptance sidecar；
3. 读取 acceptance producer node，要求其 input refs 中恰有一个 canonical acceptance context package ref；
4. 由 context package filename 定位 context node instance，并验证该 ref 确实由 completed
   `context.acceptance_codex_verifier_review` node 输出，target/profile 与 acceptance producer 匹配；
5. 只从该 context node 的 persisted input refs 解析 self-test sidecar，并用既有 lineage verifier 重验；
6. 返回 typed `ResolvedCompletionEvidence(acceptance, selfTest, sourceRefs)`；缺失、多份、lineage/SHA/iteration 不符均
   fail closed，不扫描 `runs/`、不读 global evidence manifest、不看 mtime。

这是 read model，不产生 durable artifact，也不拥有 review decision。

## 5. Consumer sweep

### 5.1 Final report

| 当前行为 | 代码位置 | 问题 | D2 处理 |
|---|---|---|---|
| 按 routed ref 的文件名找 acceptance/self-test | `ReportGenerateFinalHandler:206-218,345-357` | 只校验文件存在，不做 sidecar lineage；真实链只到 acceptance | 改为消费 resolver |
| recent outputs 扫 `runs/` 下 `.md`，按 mtime 排序 | `ReportGenerateFinalHandler:320-343` | JSON Agent output 不显示；mtime 不是 occurrence order | 删除该扫描；只列 selected typed evidence 的 source refs |
| 报告名暗示 terminal/live | `ReportGenerateFinalHandler:55-93` | 实际生成在 human gate 与 delivery 之前 | 明示 snapshot phase/generated-at；不回写 live delivery |
| completion audit 只检查 Markdown heading | `CompletionAuditWriter:75-87` | `not available` 仍可通过 heading 检查 | report resolver 缺失时 report node fail；audit 保持 artifact completeness owner，不升级为 semantic judge |

### 5.2 Server / API

| 当前行为 | 代码位置 | 问题 | D2 处理 |
|---|---|---|---|
| status 同时返回 `delivery`、raw `deliveryMerge`、`deliveryView` | `WorkstreamQueryService:81-88` | 三个解释面；`delivery.json` writer 已删除 | 只保留 `deliveryView` |
| overview 同时返回 raw `deliveryMerge` 与 `deliveryView` | `WorkstreamQueryService:181-224` | UI 可选择不同 owner | 删除 raw field，统一 `deliveryView` |
| final acceptance 优先读 real-case summary，随后扫描 runs | `WorkstreamQueryService:490-511` | summary/目录顺序是 projection guess | 从 verified `GoalSatisfactionDocument` + resolver 读取；source 显式返回 |
| artifact contracts 扫 runs 并按 mtime 排序 | `WorkstreamQueryService:394-405` | 最近 occurrence 可能漂移 | D2-A 后按 ledger node/execution 顺序投影 validated refs；不按 mtime |
| list item 的 `status` 来自 workstream identity，另加 `workflowStatus` | `WorkstreamQueryService:314-331` | API 同时暴露 stale/current 且名称含糊 | `status` 以 workflow current 为主；旧值改名 `legacyInitialStatus`，并加 `statusSource` |
| artifacts() 仍有多个 mtime-latest 辅助展示 | `WorkstreamQueryService:689-735` | 不属于 completion/delivery authority，但容易被误读 | 本 checkpoint 只清理 completion evidence；其余标为 presentation residual，不扩 scope |

### 5.3 CLI

| 当前行为 | 代码位置 | 问题 | D2 处理 |
|---|---|---|---|
| status 直接读 legacy `delivery.json` 和 raw merge plan | `StatusCommand:76-108` | 与 D1 shared view 并行解释 | 消费 `DeliveryApplicationService.View`；legacy reader 删除 |
| summary 同时展示 `Delivery` 与 `Delivery Merge` | `SummaryCommand:68-72,132-138` | 新 workstream 第一项永远 unavailable | 合成一个 Delivery section，消费 shared view |
| summary 的 next action 自行解释 APPLIED/FAILED | `SummaryCommand:153-191` | 与 server `DeliveryViewService` 重复 | 共享 view 提供 capability/reason；CLI 只格式化命令 |
| trace/watch/timeline 使用 timeline status | 多处 | timeline 是 current 派生 projection | action-bearing status 优先 `workflow/current.json`；timeline 继续作为 history display |

### 5.4 UI

| 当前行为 | 代码位置 | 问题 | D2 处理 |
|---|---|---|---|
| console delivery fallback：view -> raw merge | `console.js:1770-1775` | 允许 shared owner 缺失时静默回退第二解释者 | 只消费 `deliveryView`；缺失显式 unavailable |
| management UI 渲染 raw `deliveryMerge` | `mgt/app.js:660-689` | 与 console 不同 owner | 改为 shared `deliveryView` |
| list UI `workflowStatus || status` | 两个 UI list renderer | fallback 没有 source label | API 提供 canonical status/source；legacy-only workstream 明示 legacy |

### 5.5 Ledger attention 与 scripts

| 当前行为 | 代码位置 | 问题 | D2 处理 |
|---|---|---|---|
| `WorkstreamAttentionEvaluator` 直接解释 APPLIED/FAILED | `WorkstreamAttentionEvaluator:160-210` | 不识别 READY/COMMITTED，且 ledger module 无法解析 accepted candidate | 删除 delivery-specific branch；它只拥有 workflow/goal attention，delivery next action 由 runtime view 提供 |
| smoke test 检查 `delivery.json` 不存在 | `scripts/smoke-test.sh:1153-1157` | 已删除 writer 的负向历史断言，没有当前 delivery 语义 | 改为 candidate/merge-plan/shared API 的 deterministic assertion |
| stable commit/pr files 被 runner/report展示 | 多处 | 只证明 presentation 文件存在 | 保留，但明确 `presentationOnly=true/source=candidate` 或文档标签；不能决定 acceptance/delivery status |

## 6. 候选比较

| 候选 | 判定 | 理由 |
|---|---|---|
| A. 新 durable `VerificationEvidenceSelection` | REJECT | persisted node input/output refs 已能精确重建同一链；新增 artifact 会复制 owner |
| B. 扫 `runs/` 找 latest sidecar/Markdown | REJECT | mtime/path order 不是 routed occurrence authority，已在多个 real-case 证明会漂移 |
| C. 只读 acceptance context metadata 的 `evidenceRefs` | REJECT AS AUTHORITY | metadata 是 machine projection，但当前没有自身内容 SHA 与完整 producer-lineage guard；可作交叉核对，不能单独选事实 |
| D. 一个通用 `CompletionDeliveryReadModel` | REJECT AS TOO BROAD | 混合四个 owner，且 D1 已有 delivery view；会制造第二状态模型 |
| E. ledger-lineage resolver + 复用 D1 delivery view + 删除 fallback | **PROCEED** | 无 schema/workflow/新 artifact；同时关闭 selection 缺口与多解释者 |

## 7. 推荐实施顺序

### D2-A：Completion Evidence Lineage

最小 production 范围：

1. 新增只读 `CompletionEvidenceLineageResolver`；
2. `ReportGenerateFinalHandler` 消费 resolver，删除 filename-only 与 runs/mtime Markdown scan；
3. `WorkstreamQueryService` 的 acceptance/artifact-contract completion 路径消费 resolver 与 goal satisfaction，删除 runs scan；
4. final report 明示 pre-human-delivery snapshot；
5. 缺失/篡改/跨 iteration/错误 context producer/多份 sidecar 必须 fail closed。

Cannot-bypass tests：

1. 完整真实 ledger sequence：self-test -> artifact validation -> acceptance context -> acceptance -> decision -> report，报告同时包含两份 verified typed evidence；
2. 删除 context node input self-test ref 后，目录中即使仍有完整 sidecar也失败；
3. 放入 mtime 更新的 sibling self-test/acceptance 文件，不得被选中；
4. context ref 指向错误 node、跨 iteration、SHA 篡改、两份 candidate 均失败；
5. JSON-only Agent output 仍能通过 typed sidecar/source ref 出现在 selected outputs；
6. `GoalSatisfactionDocument` 与 acceptance sidecar outcome 不一致时失败。

### D2-B：Projection Subtraction

最小 production 范围：

1. CLI status/summary 与 server status/overview 只消费 `DeliveryApplicationService.View`；
2. 删除 `delivery.json` readers、raw `deliveryMerge` API/UI fallback 与重复 status switch；
3. `WorkstreamAttentionEvaluator` 删除 delivery lifecycle 解释，只保留 workflow/goal attention；
4. list/current surfaces 使用 workflow current，legacy initial fields 改名并带 source；
5. stable commit/pr files继续作为 presentation，但 API/文档不得把其存在解释为 PREPARED/APPLIED/COMMITTED；
6. smoke/API/CLI/UI parity tests 使用同一 fixture 断言 READY/APPLIED/COMMITTED/FAILED/LEGACY_NOT_BOUND。

Cannot-bypass tests：

1. legacy `delivery.json=committed` 与 authoritative `merge-plan=FAILED` 冲突时，全部 surface 只显示 FAILED；
2. accepted candidate 已存在、merge plan 尚未创建时，全部 surface 显示 READY；
3. APPLIED 与 COMMITTED 的 canMerge/canFinalize/commit 完全一致；
4. `workstream.json.status=created`、`workflow/current=COMPLETED` 时 list/status/overview/UI 均显示 COMPLETED，并将 created 仅标为 legacy initial；
5. 删除 `deliveryView` projection 时 UI 明示 unavailable，不回退 raw plan；
6. main/test/scripts 全仓 `delivery/delivery.json` current-reader 零命中。

## 8. End-to-end invariant

| 维度 | D2-A Completion evidence | D2-B Delivery/current projection |
|---|---|---|
| Declared by | final acceptance goal fact + routed node refs | workflow current + accepted candidate + merge plan |
| Proven by | sidecar lineage verifier + context-node input/output chain | ledger current + D1 candidate resolver + `DeliveryApplicationService.View` |
| Consumed by | final report + completion overview | CLI/API/UI/summary/next-action |
| Enforced before | report publication；缺失不得生成伪完整报告 | operator 展示/merge/finalize command exposure |
| Failure route | typed report node failure / explicit unavailable for legacy | shared BLOCKED/FAILED/LEGACY_NOT_BOUND，不回退旧文件 |
| Cannot bypass | sibling mtime、deleted routed ref、tamper、cross-iteration | conflicting legacy/raw fixtures + parity tests |
| Residual | final report仍是 pre-human snapshot；不承载 live delivery | old workstream 无 D1 binding 明示 legacy；R1 crash consistency仍 deferred |

## 9. 停止条件与成熟度

停止条件：

- resolver 若必须扫描 `runs/`、按 mtime/path 猜 latest，停止并回到 routed lineage；
- 若 D2-B 需要新增 delivery status/schema，停止；D1 的 view/plan 已是 owner；
- 不为其他 context-governance mtime projection 扩建通用 framework；它们不参与本轮 completion/delivery authority；
- 不调用 provider、不启动 real-case。D2-A/B deterministic review PASS 后，下一次自然 real-case 再共同验证多端一致性。

当前成熟度：

| 能力 | 成熟度 |
|---|---|
| accepted bytes / delivery application authority | `ENFORCED / NOT REAL-CASE PROVEN`（D1） |
| completion evidence routed lineage | `VALIDATED DEFECT / NOT ENFORCED` |
| delivery/current multi-surface consistency | `VALIDATED DEFECT / NOT ENFORCED` |
| D2 推荐设计 | `ASSESSED / NOT IMPLEMENTED` |

下一步仅允许对本 sweep 做一次独立架构评审。评审 PASS 后先制定并实施 D2-A；D2-A clean checkpoint 后再实施
D2-B。不得把两者与 E1、R1 或 provider checkpoint 叠写。
