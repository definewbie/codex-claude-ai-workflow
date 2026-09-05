# Post-Contract Authority Continuity 与 Semantic Revision Convergence Bounded Comparison

状态：DRAFT / AWAITING INDEPENDENT ARCHITECTURE REVIEW  
日期：2026-09-04

## 1. 目标

解决 fresh OKX real-case 暴露的共性缺陷，而不是修补某个业务文件名：

1. canonical TaskContract 一旦由 human gate 批准，必须在后续 design、plan、implementation、self-test、acceptance 的每个真实 Agent occurrence 中保持同一份可验证 authority；
2. design/plan 的人工语义修订必须绑定上一候选、最新 review 和 human decision，不能每轮只凭最新自然语言重新生成并静默回退已关闭约束；
3. 可以机械判断的边界由 harness 在 provider/reviewer 之前拒绝；事务、并发、恢复等业务语义继续由独立 reviewer 和 human 判断；
4. 不引入通用规则引擎、约束 DSL、新 workflow phase/node 或 OKX 特判。

本轮只做架构比较与实施边界设计。当前 `ws-20260904T131724Z-PNBV` 保持在 human design gate，不再 approve/revise，不调用 provider。

## 2. Captured Facts

### 2.1 自然样本中的振荡

同一 frozen TaskContract 下，Claude design revision 出现交替回退：

| Candidate | 事务语义 | 文件 scope | Review |
|---|---|---|---|
| initial | 有设计缺口 | 基本对齐 | `NEEDS_REVISION` |
| iter-003 | 修复解析前写库/单调合并/schema version | 引入 `AppProperties`、`client/*`、`persistence/*` 等未授权路径 | `NEEDS_REVISION` |
| iter-004 | 回退为“取数前独立写 last_attempt_at” | 回到授权路径 | `NEEDS_REVISION` |
| iter-005 | 再次修复事务时序 | 引入 `OkxMarketClient`、`web/*`、`schema/sqlite/*` 等另一组未授权路径 | `NEEDS_REVISION` |

Codex reviewer 每轮都正确阻止了错误设计进入实现；失败发生在收敛能力，不是 human gate 的安全拒绝能力。

### 2.2 TaskContract authority 在 revision 输入中丢失

初始 `design.claude_design` occurrence `run-1788530663596` 的 node input refs 包含：

- `artifacts/task-contract.json`
- `artifacts/task-contract.md`
- `artifacts/contract-resolution.json`
- `artifacts/contract-resolution.md`

其 prompt 因此包含完整 typed TaskContract 和完整 scope。

后三次 design revision occurrence 的 input refs 只包含上一轮 Codex review 的 stderr/transcript/result/binding/sidecar；对应 prompt 对 canonical contract 内容/ref 为零命中，仅剩模板 guardrail 对“task contract”的泛指。`AgentRunGovernanceContext.ensureQualified()` 只从当前 routed input refs 解析 TaskContract；除 acceptance artifact 外，qualified contract 缺失不会 fail closed。

因此 production prompt 同时存在两条互相冲突的事实：

- 模板要求 revision “从 TaskContract、review evidence 和 iteration reasons 重建完整设计”；
- revision occurrence 实际没有收到 canonical TaskContract。

这是 authority routing 缺陷，不是单纯的模型注意力问题。

### 2.3 Design artifact 没有可验证的文件计划

`design.claude_design` 当前使用通用 `agent_output_contract`。`AgentOutputResult` 只有 `checks/evidence/must_fix/changed_files/...`，没有 design-owned planned file entries。

本样本全部 Claude design 输出的 `changed_files=[]`；实际文件布局写在自由文本 `checks/evidence` 中。模型可以同时声称“零未授权文件”并在正文列出越界路径，现有 `AgentResultEnvelopeValidator` 仍会通过，因为它只验证通用 evidence shape。

`changed_files` 表示实际或已声称发生的变更，不应被重解释为 design planned files。Codex review 的 `failure.out_of_scope_files` 是事后发现结果，不是 producer 边界的 deterministic enforcement。

### 2.4 完整替换策略没有 revision baseline owner

`design-claude.md` 明确要求：遇到 prior `NEEDS_REVISION` 时输出 complete replacement design，而不是 delta。该选择可以避免“旧正文 + 补丁”的解释歧义，但当前没有对象拥有：

- 被替换的上一 design candidate ref/SHA；
- 触发 revision 的 review ref/SHA；
- human gate decision/note ref/SHA；
- 本 design stage 至今仍须保持的 repair obligations。

`WorkflowIterationDocument` 只保存 trigger、target 和自然语言 `reason`。所以“完整替换”不是在 typed baseline 上重建，而是在不完整上下文上重新采样。

## 3. Boundary Ownership

| Fact | Owner | 非 owner |
|---|---|---|
| 产品目标、允许修改路径、验收与测试约束 | approved TaskContract | prompt、review summary、repo-map |
| TaskContract 已获批准及其 ref/SHA lineage | approved-contract binding | 当前 node 的偶然 input refs、stable filename 本身 |
| 一次语义 revision 的 parent candidate/review/human decision | Iteration / revision baseline | LLM session memory、mtime/latest scan |
| design 计划使用的文件 | typed design artifact | `changed_files`、自然语言 checks |
| plan 实际计划编辑的文件 | typed plan artifact `edit_files` | plan prose |
| 事务/并发/恢复设计是否正确 | architecture reviewer + human gate | path validator |
| 实际代码改了什么 | Git evidence / change-scope evaluation | design/plan 自述 |

## 4. 全局 Sibling Sweep

| Lifecycle slice | 当前 typed 能力 | 风险与结论 |
|---|---|---|
| intake contract revision | 已有 proposal patch、review obligations、revision responses 与 validator | 保持，不重复实现 |
| design initial/revision | generic agent output；revision TaskContract authority 丢失；文件计划在 prose | 本次主缺陷 |
| plan initial/revision | 已有 `PlanResult.plan.edit_files`；仍需核实 revision 中 approved contract 与 prior plan lineage 是否持续、edit files 是否对 contract scope 强制 | 纳入 producer/consumer sweep |
| implementation / implementation repair | 实际 Git change scope 最终已有 authority；仍需核实每次 implement occurrence 是否持续收到 approved contract 与 approved plan | 只补 authority continuity，不复制 design revision schema |
| self-test | 需要持续消费 contract test requirements 与 approved plan；不拥有 scope | 纳入 authority continuity |
| acceptance | 已要求 routed TaskContract 缺失即 fail closed | 作为正确参考实现，不降级 |
| completion/delivery | 消费已验证结果，不创作 semantic candidate | 排除，不扩张 |

三个 bundled lifecycle（contract/full/terminal-delivery）以及本次 real-case 实际使用的
`agent-lifecycle-small-plan-human-review-experiment` 的 design 链与 revise edge 同构，不能只修 terminal-delivery variant，
也不能把 test-resource experiment 排除在 sibling 一致性之外。

## 5. 候选比较

### Candidate A：只加强 prompt

做法：再加“不得越界”“保留所有旧决定”等文字。

判定：REJECT。

原因：captured prompt 已有这些要求，模型仍产生自相矛盾声明；prompt 是 guidance，不是 enforcement，也不修复 TaskContract 缺席。

### Candidate B：design revision 直接读取 stable `artifacts/task-contract.json`

判定：REJECT。

原因：会让 stable filename 成为未验证的 global fallback，绕开批准 gate、ref/SHA 和 lineage；只修 design，不覆盖 plan/implementation/self-test sibling。

### Candidate C：把所有历史 input refs 无条件传递到所有后继节点

判定：REJECT。

原因：authority 与普通 evidence 混合，导致 context 膨胀、stale ref 累积和不明确的覆盖顺序；不能表达哪个 ref 是必须保留的 authority。

### Candidate D：新增独立 Approved Contract Binding

做法：在 contract gate 批准后另写一份 immutable binding artifact，再由后继阶段消费。

判定：DEFER / ONLY IF REQUIRED BY SWEEP。

原因：它能提供 byte-stable identity，但当前 gate document、compile node result 和
`TaskContractProjectionVerifier` 已拥有大部分所需事实。若扩展现有资格链即可唯一绑定批准的 compile occurrence，新增 artifact
会形成额外 durable-write 点和第二资格文档。只有 producer/consumer sweep 证明现有 gate result 无法原子承载批准 identity 时才允许采用。

### Candidate E：泛化既有 Contract Qualification + Typed Authored Artifact + Revision Baseline

判定：RECOMMENDED。

它复用 `gate.contract_review`、compile result 和现有 `TaskContractProjectionVerifier`，只补三个已有边界缺少的最小事实，
不增加 workflow phase或独立 authority artifact：

1. 一条 approved TaskContract qualification，保证 post-contract authority continuity；
2. design-owned typed planned files，使 scope 可机械验证；
3. occurrence-scoped revision baseline，使完整替换有确定 parent、review、human note 和 obligation coverage。

## 6. 推荐方案

### E1. Approved TaskContract Qualification

以真实 resolved `gate.contract_review` 为批准事实 owner，不新增独立 binding 文件。gate 在既有 human approval result 中原子记录：

- canonical TaskContract ref + SHA-256；
- contract resolution ref + SHA-256；
- 被批准的 `contract.compile` node ref；
- human action、note、resolved event identity/time（沿用既有 gate 事实）。

统一 `TaskContractProjectionVerifier` 从 ledger event order 定位最后一次 contract-gate resolution，验证 gate、compile node output refs、
stable artifact bytes、contract identity 与 SHA 全链一致。`latest completed compile by finishedAt` 不再选择 authority，只能作为冲突检测；
acceptance 当前 routed `AcceptanceContextBinding` 折叠为同一 resolver 的 occurrence projection，不再拥有另一套 qualification 规则。

clarify/re-compile/re-approve 时，只有 event order 中最后一次**完整且有效**的 approved contract gate 是 authority；若最后一次 gate
未解析、binding 字段缺失、损坏或出现歧义，必须失败，不得回退较早 approval。stable `task-contract.json` 若被后续 compile 覆写但尚未
重新批准，也必须失败。

contract approval 之后，任何使用 TaskContract 语义影响路由、分类、验证、scope 或副作用资格的控制路径都必须通过该唯一 resolver
消费 qualification；不限于 `context.build_package` 与 `acpx.agent_run`。展示、memory、manifest、report 等 projection 不得直接把 stable
artifact 当 authority，只能消费 resolver 已验证的结果或明确标注 unavailable。当前 routed refs 可继续承载阶段输入，但不再决定
TaskContract 是否可见。identity 缺失、SHA 不符、gate 非 approved、workstream/compile lineage 不符时，必须在对应控制决策或 provider
之前 typed fail closed。

`contract-resolution.json` 中现有 `approved_by/gate_ref/decision` 只允许作为 contract negotiation 的历史 projection；contract 是否已获批准、
批准了哪个 compile occurrence 以及当前 bytes 是否合格，唯一由 resolved contract gate qualification 判定。其任何消费者不得凭这些字段绕过
resolver 或在 gate qualification 失败时继续执行。

不得：扫描 mtime/latest filename、裸读 stable TaskContract 后跳过 gate qualification、只对 acceptance 强制。只有 sweep 证明 gate
result 无法在既有 resolution write 中原子承载上述 identity 时，才回到 Candidate D，并记录不能复用的代码证据。

### E2. Typed Design Artifact

为 `design.claude_design` 使用独立 `design_result`，不要复用 `changed_files`，也不要把 design 冒充 `plan_result`。最小新增结构：

```text
design.planned_files[]:
  path
  operation
  responsibility

design.revision_responses[]:
  obligation_id
  disposition
  evidence
```

deterministic validator 在 Codex review 前执行：

- path 必须 canonical、唯一；
- 每个 planned path 必须存在于 approved TaskContract scope；
- operation 必须与 TaskContract entry 一致；
- scope 外路径、伪造 operation、重复/逃逸路径立即 typed reject；
- 本 checkpoint 只做 subset enforcement；设计是否遗漏必要文件继续由 reviewer 判断。除非后续出现独立真实需求，不新增“穷尽 scope”字段或 exact-set 模式。
- `responsibility` 只是给 reviewer 的说明字段，不参与 deterministic enforcement，不得解析其自然语言产生 scope 或路由结论。

Codex reviewer 继续评审事务、并发、恢复、数据一致性与测试质量，不再浪费 provider turn 发现可由集合比较拒绝的路径错误。

### E3. Semantic Revision Baseline

只覆盖真正重新生成 design/plan authored artifact 的 declared semantic revision：human gate `revise`，以及三个 bundled workflow 已声明的
`plan.decision NEEDS_REVISION → plan.claude_plan`。不要把它扩成通用 workflow platform，也不要改变 runtime retry/implementation repair 语义。

resolver 必须从当前 `WorkflowIterationDocument.triggerNodeInstanceId` 出发，经真实 gate lineage 确定：

- parent authored candidate ref/SHA；
- latest reviewer sidecar ref/SHA；
- trigger decision 或 human gate document/event ref/SHA；human route 必须带 nonblank note，review-driven route 不伪造 human note；
- 本 stage 从第一次 revision 起累计的 typed review `must_fix` 与 human notes。experiment 的 direct plan-gate route 没有 reviewer时只产生
  human-note obligation。obligation id 固定为
  `sha256:<source-artifact-bytes>#must_fix:<zero-based-index>` 或
  `sha256:<gate-document-bytes>#human-note:0`；source ref 作为独立字段保留，不允许按文本排序或由 sibling 自定义 id。

不得使用 node-id filename 猜测、mtime/latest 或模型 session memory。baseline 作为 occurrence context 写入 prompt manifest；缺任一 identity 即在 provider 前失败。

revision candidate 的 `revision_responses` 必须与 active obligation ids 精确覆盖。该校验只证明“没有遗漏回应”，不声称自然语言回应正确。
reviewer继续使用现有 decision/must_fix 判断整体语义，不新增逐 obligation assessment schema；在对应 stage 最终 human approve 前，历史
obligations 保持 active，防止“上一轮已修复、下一轮静默回退”。

完整 replacement design 可以保留，但其输入必须固定为：approved contract + parent candidate + latest review + human decision + active obligations。

### E4. Plan 与 downstream 守卫

完成 E1-E3 sweep 后同步检查：

- `PlanResult.plan.edit_files` 对 approved TaskContract scope 做 canonical subset/operation validation；
- implement occurrence 必须同时绑定 approved contract 与 approved plan；
- self-test 必须同时绑定 approved contract test requirements 与实际 changed-files evidence；
- acceptance 现有 routed-contract fail-closed 行为保持，并改为消费同一 approved qualification resolver；
- actual Git change scope 继续由 Git evidence/change-scope owner 判定，不被 design/plan planned files 替代。

## 7. Failure Routing

| Failure | Route |
|---|---|
| approved contract qualification 缺失/篡改/lineage 错 | provider 前 typed execution failure；不得 fallback |
| design/plan path 越权或 operation 不符 | artifact validation failure；不得消耗 reviewer turn |
| revision baseline 缺 parent/review/human identity | provider 前 typed failure |
| revision responses 漏 obligation | artifact validation failure |
| obligation 回应存在但语义仍错误 | Codex `NEEDS_REVISION` → human gate |
| 同一 obligation 无新证据重复、或 A/B 振荡 | stop-loss；不靠更多 prompt 继续消耗 |

## 8. Cannot-Bypass Tests

1. 初始 design 与连续三次 human revise 的 prompt manifest 均绑定字节一致的 approved TaskContract ref/SHA 与 approving gate/compile ref。
2. 删除/篡改 TaskContract、resolution、compile node 或 approving gate，必须在 fake provider 调用计数仍为 0 时失败；不得回退较早 approval。
3. contract/full/terminal-delivery 三个 bundled workflow 与 small-plan-human-review experiment 的所有 post-contract agent/context sibling 一致。
4. design planned path 越界、`../` 逃逸、重复、operation 伪造均在 Codex review 前拒绝。
5. 删除 typed planned files 后，即使 prose 声称“scope clean”也必须失败。
6. revision baseline 精确绑定 parent candidate、review sidecar 与 human decision；删除任一 ref/SHA 必须失败，不得回退 stable/global artifact。
7. revision candidate 漏掉任一历史 active obligation 必须失败；只回应最新 must-fix、遗漏更早 human decision 也必须失败。
8. sequence replay：事务 obligation 修复后再出现 scope 修订，下一 candidate 必须仍携事务 obligation；反向顺序同样成立。
   design human revision，以及 plan 的 reviewer-driven/human-reviewed/direct-human 三种 declared route，都必须有 baseline、obligation coverage
   与冷启动 parity tests。
9. plan `edit_files` 越界在 implementation 前拒绝；implementation 的实际 Git 越界仍由既有 change-scope tests 拒绝。
10. acceptance routed-contract 既有 tests 全绿，并新增同一 qualification resolver parity test。
11. 所有 failure 都断言 typed reason、零错误路由、零未授权下游 provider/业务写入。
12. contract gate resolution 的 durable-write failure slices：gate 决定落盘前失败不得产生 qualification；gate 已完成但 qualification 字段缺失/半写
    必须冷启动后 fail closed；gate 完整落盘而 successor routing 中断时，冷启动必须解析同一 approval 并只路由一次。
13. re-compile 覆写 stable contract 但尚未 re-approve 时，冷启动 qualification 必须失败；re-approve 后只认 event order 中最后一个完整 approval，
    最新损坏/未解析不得回退较早 approval。
14. revision baseline 投影写入前/中失败不得启动 provider；冷启动必须从 authoritative gate/iteration/candidate/review refs 重建同一组 obligation ids，
    不读取半写 projection，不扫描 latest/mtime。

## 9. 实施前 Sweep 与顺序

Implementation READY：NO。先完成以下只读 sweep，并把结果写回实施计划：

1. `contract-resolution.json`、TaskContract、compile node、contract gate/event 的全部 producer/consumer/writer，确认 gate-result qualification 的原子更新顺序；
   只有证明既有 gate write 无法承载时才允许新增 binding artifact。sweep 输出必须逐一分类当前 stable contract 读取者：
   `FailureClassifyHandler`、`WorkstreamMemoryProjection`、`WorkstreamMemoryUpdater`、`RepoScopeGovernance`、`EvidenceManifest`、
   `AgentPromptRenderer`，以及 `AcpxAgentTaskHandler` 的路径保护；控制路径改用 resolver，projection 只能派生或显式 unavailable；
   同时分类 `contract-resolution.json` 在 `AgentPromptRenderer`、`TaskContractRevisionContextResolver`、`ContextBuildPackageHandler`、
   `EvidenceManifest`、`ContextStateEvidenceBoundaryRenderer` 中的用途，禁止其 `approved_by/gate_ref/decision` 成为第二批准事实；
2. `TaskContractProjectionVerifier` / `AgentRunGovernanceContext` 全部调用者，确认 acceptance 现有强制语义如何统一而不回退；
3. design/plan/implementation/self-test/acceptance 在三个 bundled workflow 与 small-plan-human-review experiment 的全部 initial/revision/repair input refs；
4. `AgentResultEnvelope`、projector、sidecar、prompt contract、validator、ContractRegistry 的 artifact-type sibling；
5. plan edit-files 与 actual Git change-scope 的 owner 边界，禁止第二 scope owner；
6. human revise 的 gate → iteration → parent candidate/reviewer lineage，锁定 baseline resolver，不使用 filename/mtime 推断；
7. legacy workstream 行为：新 checkpoint 后缺 approved binding 必须显式 fail closed；不得静默读取 global TaskContract。

实施顺序：

1. E1 authority continuity + durable-write/cold-start sequence tests；
2. E2 typed design artifact + deterministic scope tests；
3. E3 design/plan revision baseline + cold-start/oscillation replay；
4. E4 downstream sibling convergence；
5. focused tests → full reactor → git diff-check → 独立代码级 diff review；
6. 新 fresh real-case Gate。不得恢复 PNBV 样本证明新代码。

每一步必须是 clean checkpoint，前一步 review 为 PASS 后才能进入下一步；不得将三步压成无法归因的大 diff。

## 10. End-to-End Invariant

| Dimension | Required proof |
|---|---|
| Declared by | resolved contract gate qualification + typed design/plan contracts + iteration revision baseline |
| Proven by | exact refs/SHA、gate/iteration lineage、typed planned/edit files、obligation coverage |
| Consumed by | post-contract context/agent resolver、artifact validator、Codex reviewer、implementation/change-scope chain |
| Enforced before | authority 缺失在 provider 前；scope 越权在 reviewer/implementation 前；实际越权在 acceptance/delivery 前 |
| Failure route | typed pre-provider/artifact failure、semantic `NEEDS_REVISION`、重复振荡 stop-loss |
| Cannot-bypass | 四 workflow sequence tests、durable-write/cold-start、tamper/delete/path escape、历史 obligation omission、A/B oscillation replay |

## 11. 成熟度与非目标

当前：

- approved TaskContract initial routing：`VALIDATED`；
- post-contract authority continuity across revisions：`FAILED IN REAL CASE`；
- design scope enforcement：`REVIEW-DETECTED / NOT DETERMINISTICALLY ENFORCED`；
- human gate safety rejection：`REAL-CASE PROVEN`；
- semantic revision convergence：`NOT PROVEN`。

本方案不承诺 LLM 永不产生业务设计错误；它保证 authority 不丢、机械边界先拒绝、人工修订可追踪且不能无声遗漏。事务等语义仍由独立 reviewer/human 负责。

明确不做：

- 不为 OKX 路径、SQLite 或 Kline 事务写特判；
- 不自动理解任意自然语言并生成规则；
- 不新增 workflow node/phase；
- 不把所有 review finding 建成通用政策引擎；
- 不修改当前 real-case ledger 或用旧样本续跑验证。
