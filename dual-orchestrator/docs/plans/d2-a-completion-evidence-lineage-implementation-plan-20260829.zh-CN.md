# D2-A Completion Evidence Lineage Implementation Plan

日期：2026-08-29
状态：INDEPENDENT DIFF REVIEW PASS / CHECKPOINT COMPLETE / NOT REAL-CASE PROVEN

## 1. 目标

修复 final report 与 completion API 无法从最终 acceptance occurrence 可靠回溯 developer self-test evidence 的共性缺陷。

本 checkpoint 只建立一条只读、fail-closed 的 completion evidence lineage：

```text
GoalSatisfactionDocument
  -> acceptance.decision node/outputRefs
  -> validated acceptance sidecar
  -> acceptance producer node/inputRefs
  -> acceptance context package ref
  -> completed acceptance context node/outputRefs
  -> acceptance context node/inputRefs
  -> validated self-test sidecar
```

它不新增 durable artifact、schema、workflow node、workflow edge 或第二个 completion owner。

## 2. 已确认事实

### 2.1 Source of truth

- `GoalSatisfactionDocument` 由 `GoalSatisfactionWriter` 在 `acceptance.decision` 完成时写入，绑定 final domain outcome、
  decision node occurrence、iteration 与 evidence refs。
- acceptance 与 self-test sidecar 的 occurrence lineage 已由 `AgentOutputLineageVerifier` 验证到 producer node、
  node execution、run ref、output refs 与 sidecar SHA。
- `RoutingEngine.completeAndRoute()` 把 predecessor output refs 写入 successor input refs；
  `ReviewDecisionHandler` 保留 decision input refs。因此 acceptance decision output refs 与 final-report routed refs 可与
  goal evidence refs逐项对账。
- acceptance Agent 的 input refs 包含 acceptance context package；该 context node 的 persisted input refs 仍包含 self-test
  sidecar。事实没有被删除，只是未继续投影。
- `ContextBuildPackageHandler` 以 context node instance id 生成 `context/<node-instance-id>.md`。这个命名只作为 locator；
  定位后必须以 node document、workflow definition 与 output refs 反向验证，不能仅凭文件名信任 lineage。

### 2.2 当前缺陷

- `ReportGenerateFinalHandler` 只按 routed ref 文件名找 sidecar，并扫描 `runs/outputs/*.md` 后按 mtime 排序；它不验证完整
  lineage，且真实 E2E 中 self-test 不在 report node input refs，所以报告显示 `not available`。
- `WorkstreamQueryService.finalAcceptance()` 优先读取 real-case summary，随后扫描 `runs/` 猜最后一个 acceptance sidecar；
  `artifactContracts()` 也按 mtime 排序。这些 projection 可能选择未路由 sibling occurrence。
- `CompletionAuditWriter` 只检查报告文件与 heading 是否存在。它拥有 artifact completeness，不拥有 acceptance/self-test
  semantic truth；本 checkpoint 不把它升级为 judge。

## 3. Ownership

| Fact | Owner | D2-A 行为 |
|---|---|---|
| final goal outcome | `GoalSatisfactionDocument` | resolver 的入口锚点 |
| acceptance decision occurrence | `acceptance.decision` node | 必须与 goal node、outcome、iteration、evidence refs 一致 |
| acceptance/self-test typed evidence | validated sidecars + producer node execution | 复用既有 lineage verifier |
| context package membership | context node input/output refs + workflow definition config | 只读回溯，不新建 selector |
| final report | `ReportGenerateFinalHandler` | pre-human-delivery snapshot projection |
| completion API | `WorkstreamQueryService` | 消费 resolver，不扫描 runs 猜事实 |

新概念判断：不需要新的领域或 durable 概念。只新增一个 read-only resolver，返回既有 authoritative facts 的验证结果。

## 4. Resolver 设计

### 4.1 类型与入口

在 `dual-runtime` 的 handler package 新增 public、无状态 `CompletionEvidenceLineageResolver`，供 runtime report 与已依赖
`dual-runtime` 的 server 复用。它不写文件。

建议提供两个明确入口，共用同一内部解析链：

1. `resolveForReport(workspaceRoot, workstreamId, reportNode)`：除 canonical goal 链外，要求 report node 的 iteration 与
   input refs 分别等于 goal iteration 与 goal evidence refs；
2. `resolveCurrent(workspaceRoot, workstreamId)`：由 `goal-satisfaction.json` 作为 canonical anchor，供 completion API 使用。

所有入口返回同一个 typed `Resolution`；它以 `AVAILABLE / NOT_CONFIGURED / NOT_FINAL / INVALID` 明确区分
可用证据、历史未配置、合法未终态与损坏。`AVAILABLE` 才包含：

- goal document；
- acceptance decision node；
- acceptance context node；
- acceptance/self-test sidecar ref、path 与 typed document。

其他状态携带稳定 `failureCode` 与 detail，且 evidence 字段为空。unexpected parse/I/O failure 统一映射为 `INVALID`；
调用方不得各自解释异常、返回半份 evidence 或 silently fallback。

### 4.2 Goal 与 decision 校验

resolver 先区分 goal 文件是否存在；文件存在后先验证 workstream/workflow identity，再分类 finality。该顺序意味着
“identity 已损坏且同时非 final”的文档按 corruption 优先返回 `INVALID`，避免把伪造的中间态包装成合法 `NOT_FINAL`：

1. `goal-satisfaction.json` 不存在时返回 `COMPLETION_GOAL_NOT_CONFIGURED`；这是 capability/历史状态，不是 corruption；
2. goal 文件存在即要求 workstream id、workflow instance identity 匹配 ledger current/instance；不匹配返回
   `COMPLETION_GOAL_INVALID`，不进入 finality 或 evidence 解释；
3. identity 有效但 node definition 不是 `acceptance.decision`，或 domain outcome 不是 `SATISFIED` /
   `SATISFIED_WITH_NOTES` 时返回 `COMPLETION_GOAL_NOT_FINAL`；这是 redesign/repair/block/尚未到 acceptance 的合法
   workflow 状态，不是 corruption；
4. 加载 final goal 指向的 decision node，要求 `COMPLETED`、同 iteration，node result outcome/decision 与 goal outcome一致；
5. 要求 goal evidence refs 与 decision node output refs逐项一致；report 入口还要求 report node input refs 与其逐项一致。

不允许从 summary、Markdown、timeline 或目录顺序补 goal。

### 4.3 Acceptance evidence 选择

只从 goal evidence refs 读取 sidecar：

1. 使用 `AgentOutputLineageVerifier` 重验每个候选；
2. 由 acceptance decision 的 workflow predecessor/contract 语义选出恰一个 final acceptance sidecar；实施时优先复用
   workflow definition 的 routed predecessor 与 `ContractRegistry` artifact contract，不按固定文件名选择；
3. 要求 sidecar producer node `COMPLETED`、同 iteration，decision 与 goal outcome 一致；
4. 零份、多份、不可解析、SHA/lineage/outcome 不一致均失败。

不得按 mtime、run id 字符串排序或“最后一个文件”选择。

可以复用 `AgentEvidenceIndex` 的 exact-ref 读取与 lineage validation，但不得调用它的 `latest()/recent()` 来选 owner；即使
内部集合为了展示排序，resolver 也必须先按 definition/contract 语义过滤并要求 multiplicity 恰为一，顺序不能影响结果。

### 4.4 Context node 回溯与防御

从 acceptance producer node input refs 中要求恰一个 canonical context package Markdown ref：

```text
.agent/workstreams/<workstream-id>/context/<context-node-instance-id>.md
```

文件名只用于得到 candidate node instance id。随后必须全部验证：

1. package ref 在 workspace/workstream 内、不是 symlink、文件真实存在；
2. 加载的 node instance id 与文件 stem 一致；
3. context node 为 `COMPLETED`、同 iteration、handler type 为 `context.build_package`；
4. context node `outputRefs` 精确包含该 package ref；这是阻止命名约定成为 authority 的必要反向验证；
5. persisted workflow definition 中该 node 的 `profile=acceptance`，`target_node` 等于 acceptance producer node definition；
6. acceptance producer node input refs 还应包含同 stem metadata ref时，仅作同 occurrence 交叉核对；metadata 不单独拥有
   lineage。

错误 node、跨 iteration、ref 未由 node 输出、多 package candidate 或 config 不匹配均失败。

### 4.5 Self-test evidence 选择

只从已验证 context node 的 persisted input refs 选择 self-test：

1. 从 context node definition 的 `required_agent_evidence` 读取 `node_id/source_artifact_type/decision`；
2. 对 input refs 中的 sidecar 复用 `AgentOutputLineageVerifier`；
3. 要求恰一个 typed document 同时匹配上述三字段、同 workstream 与同 iteration；
4. 删除 routed ref 后，即使 `runs/` 中仍有完整或更新的 sidecar也必须失败；
5. 不允许 fallback 到 global evidence、context metadata、文件名或 mtime。

这使 workflow definition 继续拥有“acceptance context 需要哪类 evidence”的声明，resolver 只执行声明，不复制 node id
规则。

### 4.6 Failure semantics

稳定 failure codes 最少包括：

- `COMPLETION_GOAL_NOT_CONFIGURED`
- `COMPLETION_GOAL_NOT_FINAL`
- `COMPLETION_GOAL_INVALID`
- `COMPLETION_ACCEPTANCE_LINEAGE_INVALID`
- `COMPLETION_CONTEXT_LINEAGE_INVALID`
- `COMPLETION_SELF_TEST_LINEAGE_INVALID`

前两个 code 是 resolver 的非 final/non-configured 状态，不属于 lineage corruption。`resolveCurrent()` 将其原样交给 API：

- `NOT_CONFIGURED`：旧 workstream 或 capability 缺失；
- `NOT_FINAL`：workflow 尚未产生 final satisfied acceptance；
- `AVAILABLE`：完整 lineage 已验证；
- `INVALID`：四类 corruption code 之一。

`resolveForReport()` 的调用语义更严格：bundled workflow 只有 final satisfied acceptance 才能路由到 report node，因此
`NOT_CONFIGURED` 或 `NOT_FINAL` 都表示 report execution precondition 不成立。handler 必须以对应原始 code 返回
`NodeStatus.FAILED`，但不得把它改写为 corruption。真正 invalid lineage 同样 fail closed。

`ReportGenerateFinalHandler` 失败时在 result 中写 `failure_code`、`error` 和已验证到的 anchor ref；
不得写入或发布本轮 report refs。为避免旧成功报告冒充本轮输出，handler 应先完成 lineage resolution
与 Markdown 渲染，再沿用现有写入语义更新两个 report projection；本 checkpoint 不扩建通用事务协议，也不宣称解决两个
Markdown 文件之间的 crash consistency。若此前已有 `latest-final-report.md`，本轮 lineage 失败时旧文件可以作为历史
projection 保留，但失败 node 不得发布该 ref，任何 current projection 也不得把它解释为本轮成功报告。

`WorkstreamQueryService` 将 resolver 状态逐一投影为 `available/not_configured/not_final/unavailable`；仅 `INVALID` 使用
`unavailable` 并携带 corruption `failureCode` 与 detail。不得 500，也不得 fallback 到 summary/runs。

旧 frozen workstream 若没有该 lineage，API 明确显示 `not_configured`；合法中间态显示 `not_final`；损坏显示
`unavailable/invalid`。不得用 legacy scan 伪造 current evidence。当前 bundled workflow 的 report node遇到任一非
`AVAILABLE` 状态均 fail closed，但保留原始分类。

## 5. Consumer 修改

### 5.1 Final report

修改 `ReportGenerateFinalHandler`：

1. execute 开始时调用 `resolveForReport`；失败时不写报告；
2. `Verification Evidence`、`Acceptance Summary`、`Developer Self-Test Summary` 全部消费同一个 resolution；
3. 删除 `latestInputContractPath()`、`appendRecentOutputs()`、mtime/Markdown outputs scan 及其常量/import；
4. `Recent Agent Outputs` 改为 `Selected Typed Outputs`，只列 acceptance/self-test source output ref 与 sidecar ref；
5. 保留 completion audit 所需 headings；在 header 明示：
   `Report Scope: pre-human-delivery verification snapshot`，并说明不包含后续 human acceptance/delivery apply/commit；
6. 不改变 report 文件名与 D1 `DeliveryPrepareHandler` 的读取路径。

### 5.2 Server completion projection

修改 `WorkstreamQueryService` 的 D2-A 范围：

1. `overview.acceptance.finalDecision` 在 resolver 成功时来自 verified acceptance sidecar；若 current goal 仍是 redesign、repair、
   blocked 或尚未到 acceptance，则返回 `none/not_final` 并继续单独投影 typed goal，而不是扫描 runs 猜一个“最终”结论；
2. 新增 `completionEvidence` projection，包含 status、goal source、acceptance/self-test ref、node/run/decision/summary；
3. generic `artifactContracts()` 与 `artifacts()` 的 mtime/history 展示不参与 final acceptance 或 completion evidence authority，
   本 checkpoint 不修改；明确登记为 presentation residual，不宣称已收口；
4. `verificationConfidence` 若仍来自 real-case summary，必须标记 source，且不得改变 typed acceptance decision；本 checkpoint
   不把经验性 confidence 升级为 authority；
5. resolver 为 `NOT_CONFIGURED/NOT_FINAL/INVALID` 任一状态时，attention 不得生成 “No obvious follow-up”。

本 checkpoint 不修改 delivery fields、CLI、UI 或 `WorkstreamAttentionEvaluator`；这些属于 D2-B。

## 6. 文件范围

预期 production：

1. `dual-runtime/.../handler/CompletionEvidenceLineageResolver.java`（新增）
2. `dual-runtime/.../handler/ReportGenerateFinalHandler.java`
3. `dual-server/.../WorkstreamQueryService.java`

预期 tests：

4. `dual-runtime/.../handler/CompletionEvidenceLineageResolverTest.java`（新增）
5. `dual-runtime/.../handler/ReportGenerateFinalHandlerTest.java`
6. `dual-runtime/.../handler/AgentOutputLineageFixture.java`（仅在真实 sequence fixture 需要 input/output refs 配置时扩展）
7. `dual-server/.../WorkstreamApiControllerTest.java`
8. `dual-runtime/.../handler/CompletionEvidenceLineageFixture.java`（新增完整 sequence fixture）
9. `dual-runtime/.../handler/ReportTaskContractTest.java`
10. `dual-runtime/.../handler/ReportFailureClassificationTest.java`
11. `dual-runtime/.../handler/BuiltinHandlerTest.java`
12. `dual-runtime/.../lifecycle/SmallWorkflowProfileDefinitionTest.java`（bundled + test-only sibling consistency）

文档：本计划与 active plan。`dual-server` 已依赖 `dual-runtime`，不改 POM；不改 workflow JSON、ledger schema、contract
schema、handler registry、provider、CLI/UI 或 scripts。

## 7. Cannot-bypass tests

测试必须用真实 `NodeInstanceDocument`、`NodeExecutionDocument`、goal document 与 routed refs 组成完整 sequence，不只断言 JSON
字段存在。

1. happy path：self-test -> artifact validation -> acceptance context -> acceptance -> decision -> report，报告与 API 使用同一
   acceptance/self-test refs；
2. 删除 context node input 中的 self-test ref：目录中保留完整且 mtime 更新的 sidecar，resolver 仍失败；
3. acceptance producer input 的 package ref 指向错误 node，或 context node output refs 不含该 ref：失败；
4. package/context/self-test/acceptance 任一跨 iteration：失败；
5. sidecar SHA、producer execution、goal evidence refs、decision output refs任一篡改：失败；
6. 两份 acceptance 或两份匹配 required evidence：失败，不按顺序选择；
7. JSON-only source output 仍在 `Selected Typed Outputs` 中投影，不依赖 `.md`；
8. goal outcome 与 acceptance sidecar decision 不一致：失败；
9. report failure 前已有旧 `latest-final-report.md` 时，旧 bytes 保持历史 projection，但失败 node output refs 为空且 current
   completion projection 不得把旧文件当成本次成功报告；
10. server resolver `NOT_CONFIGURED/NOT_FINAL/INVALID` 分别返回 typed `not_configured/not_final/unavailable`，不扫描
    runs、不生成成功 attention，且非 final goal 不得误报 lineage corruption；
11. sibling sweep：三个 production bundled variants（`full/contract/terminal-delivery`）的 acceptance context definition
    均有 `profile/target_node/required_agent_evidence`；另单独验证 test-only
    `small-plan-human-review-experiment` resource，但不得把它计入 bundled 集合。resolver 不依赖 Mailpit 或固定 workstream；
12. 反证：移除 context-node `outputRefs contains packageRef` 检查时，对应 forged-package test 必须失败。
13. report 入口分别构造 `NOT_CONFIGURED` 与 `NOT_FINAL` goal：node 必须 `FAILED`、output refs 为空、failure code 保留
    `COMPLETION_GOAL_NOT_CONFIGURED/COMPLETION_GOAL_NOT_FINAL`，不得改写为 corruption。

## 8. 实施顺序

1. 先补真实 sequence fixture 与 resolver tests，证明旧 filename/mtime 路径会失败；
2. 实现 resolver 与 stable failure codes；
3. 切换 report 三个 evidence section，删除 scan；
4. 切换 server final acceptance/completion evidence；
5. 完成三个 bundled workflow definition consistency test，并单列 test-only small-plan experiment fixture；
6. focused runtime/server tests；
7. full Maven reactor；
8. `git diff --check` 与全仓 sweep，确认 `ReportGenerateFinalHandler` 和 `WorkstreamQueryService` 的 final
   acceptance/completion-evidence authority 无 `runs`/mtime/latest fallback；§9 End-to-end invariant 的 Residual 行已登记的
   presentation/CLI 路径不计作 D2-A 实施失败，但不得被描述为已修；
9. 独立代码级 diff review；PASS 后选择性提交；
10. clean checkpoint 后才制定 D2-B 实施计划。

## 9. End-to-end invariant

| 维度 | 约束 |
|---|---|
| Declared by | goal satisfaction + workflow definition required evidence |
| Proven by | goal/decision refs equality + sidecar/node/execution SHA lineage + context input/output chain |
| Consumed by | final report 与 server completion projection |
| Enforced before | report publication；失败不得发布新的 report refs |
| Failure route | report 对非 AVAILABLE 状态 fail closed并保留原分类；API 精确投影 available/not_configured/not_final/unavailable；无 directory fallback |
| Cannot-bypass | deleted routed ref、forged package、mtime sibling、cross-iteration、tamper、multi-candidate tests |
| Residual | report 仍是 pre-human-delivery snapshot；`WorkstreamQueryService.artifactContracts()` / `artifacts()` history scan、`artifacts().contextPackageMetadata` 的 mtime max 与 `TraceCommand.acceptance()` 的 summary/runs fallback 均未修；delivery/current/CLI surfaces 留给 D2-B |

## 10. 停止条件与成熟度

停止条件：

- 如果实现需要新增 durable selector、workflow node/edge 或 schema，停止并回到现有 routed lineage；
- 如果 resolver 需要扫描 `runs/`、mtime 或 global latest，停止；
- 如果 server 需要重新解释 delivery lifecycle，停止，交给 D2-B；
- 如果 context package 命名无法通过 node output refs + definition config 反向验证，停止，不增加猜测 fallback；
- 不调用 provider、不启动 real-case。

完成 deterministic tests 与独立 diff review 后，最多声明：

- completion evidence lineage：`ENFORCED / NOT REAL-CASE PROVEN`；
- final report：`VERIFIED PRE-HUMAN SNAPSHOT`；
- delivery/current multi-surface consistency：仍为 `VALIDATED DEFECT / NOT ENFORCED`，等待 D2-B。

## 11. 实施证据（2026-08-29）

- 新增只读 `CompletionEvidenceLineageResolver`，以 `AVAILABLE / NOT_CONFIGURED / NOT_FINAL / INVALID`
  四态和六个稳定 failure code 返回完整或空证据，不引入 durable selector/schema；
- final report 与 server completion projection 统一消费 goal -> decision -> acceptance sidecar -> context package
  producer -> required self-test sidecar 的同一 verified lineage；旧 report filename/mtime recent-output 与 server
  summary/runs acceptance fallback 已删除；
- report 非 AVAILABLE 时在任何新 report 写入前 fail closed；历史 `latest-final-report.md` bytes 保持不变且失败 node
  output refs 为空；server 对四态给出 typed projection，只有 AVAILABLE+ACCEPT 才能显示成功 attention；
- cannot-bypass 覆盖 package producer outputRefs、缺 routed self-test、cross-iteration、sidecar SHA 篡改、goal/decision
  refs 漂移、双 acceptance 不按顺序选择、NOT_CONFIGURED/NOT_FINAL 分类保留，以及三个 bundled + test-only
  small-plan definition consistency；
- focused resolver/report `19/0/0/0`，相关 report fixture slice `77/0/0/0`，server controller
  `26/0/0/0`；非沙箱 full reactor `1283/0/0/0`；`git diff --check` clean；
- 当前仅可标记 implementation candidate `ENFORCED / NOT REAL-CASE PROVEN`；独立 diff review PASS 前不提交，
  D2-B 不启动。

### 11.1 首轮 diff review delta

首轮独立 diff review 为 `WARN（P0=0 / P1=1 / P2=4）`。唯一 P1 是 server 映射层只覆盖 AVAILABLE，未证明
NOT_CONFIGURED/NOT_FINAL/INVALID、`finalDecision=none`、critical/warning attention、成功 attention 门控与 decoy runs
不可成为 authority。delta 新增四条 controller sequence tests，分别覆盖带 decoy acceptance 的 NOT_CONFIGURED、合法
NOT_FINAL、tampered sidecar 的 INVALID，以及 COMPLETED+证据不可用；四条均验证不得出现 “No obvious follow-up”。

同时吸收两项非阻塞精确化：§4.2 明确 goal 文件存在后 identity corruption 优先于 finality；resolver tests 新增
acceptance/context 跨 iteration，并把未路由 self-test sidecar 的 mtime 推到未来，证明排序仍不能替代 routed lineage。
硬编码 acceptance artifact/report node id 与 status 高频完整 resolution 仅登记为现有耦合/性能注记，本 delta 不扩张
definition schema 或缓存协议。

### 11.2 第二轮 delta review

独立 delta review 为 `PASS（P0=0 / P1=0 / P2=2 知会）`，并独立复现 resolver/report
`19/0/0/0`、server controller `26/0/0/0`、full reactor `1283/0/0/0` 与 clean diff-check。原 P1-1
确认 CLOSED；identity/finality 顺序与跨 iteration/mtime coverage 均确认 CLOSED。

残留 P2 仅为既有耦合与性能观察：acceptance artifact/report node id 仍为明确硬编码，status 高频查询每次执行完整只读
resolution。两者不形成 authority fallback 或正确性绕过；前者只有在 workflow contract 泛化时才需派生化，后者只有在真实
轮询成本证据出现时才进入 bounded caching assessment。当前成熟度为 completion evidence lineage
`ENFORCED / NOT REAL-CASE PROVEN`、final report `VERIFIED PRE-HUMAN SNAPSHOT`；D2-B 仍为独立后续 checkpoint。
