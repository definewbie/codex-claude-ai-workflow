# Human Decision Gate Convergence

日期：2026-08-12

状态：`IMPLEMENTED / DETERMINISTIC ENFORCED / INDEPENDENT DIFF REVIEW PASS / NOT PROVEN`

主线分类：`P0`。它直接修复产品 workflow 在应由人类作出领域决策时错误进入 recovery 的主生命周期
边界；不是 UI feature、token 优化或新平台能力。

## 1. 目标

用一个不可再拆的 checkpoint 修复 captcha-auth v6 暴露的 human-decision 边界：

```text
strict review sidecar: decision=BLOCKED
  + blocking product question
-> decision node execution COMPLETED, business outcome BLOCKED
-> existing human review gate opens
-> human resolves through UI or CLI
-> one canonical GateDocument records action + note
-> contract-resolution preserves the reviewed question and human answer
-> compile / revision continues from the same ledger truth
```

不新增 phase、module、registry、engine、专用手机号表单或第二套 human state machine。

## 2. v6 证明的事实

workstream `ws-20260811T143704Z-RZQL` 在 3 次合法 revision 后，由最终 Codex review 返回：

- `decision=BLOCKED`；
- `failure.category_hint=HUMAN_DECISION_REQUIRED`；
- 唯一 `must_fix` 是由人类确认手机号输入范围；
- 当前 canonical proposal 已选择 `E.164-only`；
- proposal `open_questions=[]`。

所有 provider process、strict envelope、revision patch、coverage、delta、lineage 和 reviewer
消费均正常。失败不是 parser/provider/compiler，而是 review business outcome 无法到达已经存在的
`gate.human_requirement_review`。

## 3. 当前边界缺口

### 3.1 Business outcome 与 execution status 混在一起

`ReviewDecisionHandler` 将合法 typed `BLOCKED` 直接映射为 `NodeStatus.BLOCKED`。`RoutingEngine`
只为 `NodeStatus.COMPLETED` 执行 workflow edge，因此 `BLOCKED` 无法作为业务结果被路由。

正确语义：review decision 节点已经成功读取并验证 sidecar；它的 execution 应为
`COMPLETED`，`BLOCKED` 是业务 outcome。

### 3.2 Workflow definition 缺少 human-decision edge

`intake.decision` 只有：

- `READY_FOR_PRODUCT_REVIEW -> gate.human_requirement_review`；
- `NEEDS_REVISION -> intake.claude_brainstorm`。

但 review prompt 明确允许在 agent 不应自行决定真实产品歧义时返回 `BLOCKED`，并要求把具体问题
交给下一 human gate。definition 与 contract/prompt 因此发生漂移。

精确的声明链是：`ContractRegistry` 定义 review node 的 allowed decision 包含 `BLOCKED`，
`AgentPromptRenderer` 将 output contract 投影进 agent prompt；intake review template 另外说明真实产品
歧义应交给下一 human gate。template 不是 `BLOCKED` 合法性的 owner。

同一缺口存在于三个 bundled workflow 的 intake/design/plan review boundary；必须一并修复。
acceptance 不纳入本 checkpoint：虽然 `ContractRegistry` 的 acceptance decision contract 仍列出
`BLOCKED`，但 acceptance review output contract 不允许产出它，因此当前生产链不可达；acceptance 的
rejection/repair 属于另一条已存在语义，不在本次借机扩张。

### 3.3 UI approve 丢失人类说明

CLI `review decide` 已支持 `--note`，但 server `review/approve` 和两个 UI 的批准动作不提交 note。
对于 `BLOCKED` 来源的 gate，只有 action 没有答案，不能证明人类解决了什么。

### 3.4 Contract resolution 没有保留 reviewer question

`ContractCompileHandler` 只从 proposal `open_questions` 生成 `resolved_questions`。v6 的问题来自
reviewer sidecar，而最终 proposal 的 `open_questions=[]`；即使人工批准，当前实现也会丢掉问题和答案。

### 3.5 Compiler 只接受 READY review

`ContractCompileHandler.validateCurrentApprovedLineage()` 当前只接受 review decision
`READY_FOR_PRODUCT_REVIEW`。因此仅增加 human gate edge 仍会在 compile 被第二次拒绝。

正确 eligibility 不是放宽为“任何 BLOCKED 都可编译”，而是：

```text
READY_FOR_PRODUCT_REVIEW + matching approved gate
OR
BLOCKED + matching gate source_outcome=BLOCKED + approved + non-empty human note
```

review ref、proposal ref、iteration lineage 或 gate subject 任一不匹配仍须 fail closed。

## 4. Owner 与 SoT

| 事实 | 唯一 owner / SoT |
|---|---|
| review 是否要求 human decision | validated agent-output sidecar 的 business decision=`BLOCKED` |
| decision node 是否执行成功 | `ReviewDecisionHandler` 产生的 `NodeResult` |
| 应进入哪个 gate | bundled workflow definition edge |
| 当前 gate、允许动作、来源 outcome | open `GateDocument` |
| 人类选择与说明 | resolved `GateDocument.result.human_action/note` |
| contract 中的人类决策证据 | `contract-resolution.json` |
| 当前状态、问题、可用动作 | shared review/attention read model |
| UI / CLI | command projection；不得自行推导或改写 action |

## 5. 交互决策：UI + CLI，共用一个 command boundary

### 5.1 UI 是主要入口

UI 适合展示：

- 当前 canonical proposal；
- reviewer 的 blocking summary、`must_fix`、missing evidence；
- 当前建议方案（本例为 `E.164-only`）；
- 两个既有动作：`approve` 与 `revise`；
- 必填 decision note。

通用交互保持克制：

- **接受当前方案并继续**：提交 `approve + note`；
- **选择另一方案并要求修订**：提交 `revise + feedback`。

不为 E.164、本地号码或未来产品问题建立专用 radio/schema。问题和候选方案来自 review packet。

### 5.2 CLI 是等价入口，不是第二 owner

CLI 用于 headless、调试、自动化和 UI 不可用时：

```text
dualctl review decide --action approve --note "接受仅 E.164 国际格式" <workstream>
dualctl review decide --action revise --note "必须支持本地号码，并由配置提供默认地区" --run <workstream>
```

UI、API、CLI 最终都调用 `WorkflowFileLedger.resolveOpenGate()`。action canonicalization、note
要求、gate 状态验证和路由只在 ledger boundary 执行。

## 6. 单一 implementation checkpoint

### 6.1 Review decision 语义

- 删除 `ReviewDecisionHandler` 中 `BLOCKED -> NodeStatus.BLOCKED` 特例；
- 所有通过 contract validation 的 review decisions 都以 execution `COMPLETED` 返回；
- `BLOCKED` 保留为 business outcome，不改 parser，不解析自然语言正文。

### 6.2 Bundled workflow sibling sweep

在 `terminal-delivery`、`contract`、`full` 三个 definition 中增加：

- `intake.decision / BLOCKED -> gate.human_requirement_review`；
- `design.decision / BLOCKED -> gate.human_design_approval`；
- `plan.decision / BLOCKED -> gate.human_plan_approval`。

不修改 `NEEDS_REVISION`、revision budget、acceptance repair 或 runtime retry。

### 6.3 Gate 来源与 note enforcement

- `RoutingEngine.completeAndRoute()` 中的 `outputRefs` 来自 decision node 的 completed
  `NodeResult.outputRefs`；`ReviewDecisionHandler` 必须继续把
  `context.instance().inputRefs()` 原样作为 output refs。因此 gate 的 `subject_input_refs` 明确包含
  触发 decision 的 validated reviewer sidecar `.contract.json`，不是偶然的文件传递；
- `RoutingEngine` 创建 `GateDocument` 时在同一个 result map 中同时持久化
  `subject_input_refs=outputRefs` 与 `source_outcome=outcome`；
- resolved gate 保留该字段；
- 当 `source_outcome=BLOCKED` 时，`RoutingEngine.resolveOpenGate()` 在产生任何后续路由前要求非空 note；
- note 缺失时 fail closed，gate 保持 open，workflow 保持 `WAITING_FOR_HUMAN`；
- normal READY gate 的既有行为保持兼容。

`source_outcome` 的唯一 durable owner 是当前/历史 `GateDocument`。不得由 UI、node id、review summary
或 allowed actions 重推断。

### 6.4 Review packet typed projection

- runtime/worker 打开 gate 时必须读取当前 node 对应的 open `GateDocument`，把 typed
  `source_outcome` 写入新增的 `GateContext.sourceOutcome` 字段并传给 `HumanReviewGateHandler`；
- `HumanReviewGateHandler` 在 persisted JSON/Markdown review packet 中投影 `sourceOutcome`；
- handler 不扫描 gate 目录、不从 reviewer正文或 decision 再推断该值；
- `WorkstreamQueryService.currentReview/review` 和两个 UI 只消费 packet projection；
- packet 缺少来源、来源与 validated sidecar decision 不一致，属于 projection/lineage violation，
  BLOCKED gate 不得显示成可无说明批准。

### 6.5 UI / API / CLI parity

- `POST /review/approve` 接受 `ReviewApproveRequest { note }`；不再固定写入
  `approved from API`；
- `WorkstreamActionService.approve(...)` 接收并透传 note；
- 专用 CLI `review approve` 增加 `--note`，通用 `review decide --action approve --note ...`
  保持不变；两者均不能用默认说明绕过 BLOCKED gate；
- management UI 与 operator console 在当前 review decision 为 `BLOCKED` 时显示 decision-note 输入；
- 两个 UI 的 approve/revise 都提交用户输入；
- CLI 在 `BLOCKED` 来源 gate 上由 ledger 强制 note，不能用默认字符串绕过；
- autorun 遇到 `BLOCKED` 来源 gate 必须停止并等待真实人工决定，不能用固定自动说明绕过；
- API/UI/CLI 不各自 canonicalize action。

### 6.6 Contract-resolution preservation

- normal READY gate：继续从 proposal `open_questions` 生成 resolved questions；
- BLOCKED 来源 gate：compiler 通过 `subject_input_refs` 解析并验证唯一 matching reviewer sidecar；
  将 sidecar 中每个非空 `must_fix` 和每个非空 `missing_evidence` 分别映射成一个
  `ContractResolutionDocument.ResolvedQuestion`，保持原顺序并去重；每项的 resolution 都逐字使用
  human note，source=`human`；
- 若 BLOCKED sidecar 的两组问题均为空，compiler fail closed，不制造泛化问题；
- compiler eligibility 只增加“BLOCKED review 被同一 matching human gate 带 note 批准”这一条；
  无 gate、错误 gate/ref/iteration、空 note 或未批准的 BLOCKED review 仍拒绝；
- contract compile 必须继续绑定 approved review ref、canonical proposal ref、resolved gate ref；
- 不把 raw Markdown 或 UI text 作为 contract source。

## 7. Cannot-bypass tests

1. **Captured v6 replay**：最终 proposal + final review sidecar 进入真实 decision/routing chain，结果必须是
   `WAITING_FOR_HUMAN`，open gate 为 `gate.human_requirement_review`，不得成为 revision-limit recovery。
2. decision node 必须是 `COMPLETED`，outcome=`BLOCKED`；不能伪装 execution failure。
3. `GateDocument.result.source_outcome=BLOCKED`，review packet 原样包含 blocking question、must-fix、proposal ref。
4. `GateContext`/review packet 的 source outcome 必须来自 matching open GateDocument；缺失或不一致不能
   被 UI、sidecar summary 或 allowed actions 补推断。
5. BLOCKED gate 上 approve/revise 缺少 note 必须拒绝；gate/workflow/node 均不得前进。
6. 两个 UI、approve API、专用 `review approve --note` 与通用 `review decide --note` 必须产生相同
   canonical `human_action` 和 ledger result。
7. approve + note 后，compiler 产生 `contract-resolution.json`；每个 typed `must_fix` /
   `missing_evidence` 独立成为 resolved question，答案逐字等于 human note。
8. BLOCKED review 缺少 matching approved gate、gate source outcome 不符、review/proposal/iteration ref 不符
   或 note 为空时，compiler 必须 fail closed。
9. revise + note 后，只产生一次 semantic revision transition；不得成为 runtime retry。
10. READY gate 现有 approve/revise 行为不回归。
11. definition-contract consistency test 覆盖三个 bundled workflow 的 BLOCKED edge；不得只修 terminal-delivery。
12. historical/resolved gate 只读，不能再次提交 action。

## 8. Deterministic verification

先使用 v6 captured artifacts 在临时 ledger 中 replay，不调用 provider，不 resume 旧 workstream：

```text
final validated proposal
+ final BLOCKED review sidecar
-> decision COMPLETED/BLOCKED
-> open human requirement gate
-> approve with note
-> compiled contract + contract-resolution
```

随后运行 focused tests、definition consistency tests、server/CLI action tests、full reactor。

## 9. 成熟度与下一次 real-case

- deterministic PASS：该边界可标 `ENFORCED`；
- 只有自然 real-case 真实到达 human gate、用户通过 UI 或 CLI 作出决定、并继续到 compile 后，才可标
  `PROVEN`；
- v6 旧 workstream 不迁移、不 resume；其 definition/instance 已按旧语义进入 recovery；
- 实施完成后，captcha-auth 仍需一个 fresh completion workstream 才能真正交付产品。

## 10. 明确不做

- 不调高 revision limit；
- 不把 `BLOCKED` 当 recovery/retry；
- 不增加专用 product-choice schema、question registry 或通用规则引擎；
- 不让 UI/CLI 直接编辑 contract；
- 不从 Markdown、failure reason 文本或 node id 猜 human decision；
- 不在本 checkpoint 修改 provider、model、strict parser、revision patch 或 prompt 内容。
- `stopOnNonCompletedNode()` 继续服务真实 execution `FAILED/BLOCKED` 等非完成状态；本 checkpoint
  只阻止合法 review business `BLOCKED` 被错误送入该恢复路径，不删除恢复能力。

设计取舍：

- 不选 UI-only：会失去 headless、测试与故障场景下的等价入口；
- 不选 CLI-only：复杂 human evidence 不适合只靠命令参数阅读和判断；
- 不新增 `HUMAN_DECISION_REQUIRED` workflow outcome：现有 strict contract 与 prompt 已把
  `BLOCKED` 定义为需要 human intervention 的 business outcome，本次应先消除 execution/business
  conflation 和缺失 edge；
- 不让 `NodeStatus.BLOCKED` 继续参与业务路由：execution status 不能替代 typed business decision。

## 11. Boundary Closure

- Declared by：strict sidecar business decision + workflow definition edge；
- Proven by：captured v6 deterministic replay；
- Consumed by：`RoutingEngine` 创建 existing human gate；
- Enforced before：ledger 在 gate resolution 和 downstream compile 前验证 action/note；
- Failure route：无 note 保持 `WAITING_FOR_HUMAN`，不进入 recovery 或下游副作用；
- Cannot-bypass：UI/API/CLI parity、historical gate guard、definition consistency 与 compile lineage tests。

## 12. Implementation Checkpoint

2026-08-12 的单一实现 checkpoint 已完成：

- `BLOCKED` 保持 typed business outcome，decision node 以 execution `COMPLETED` 进入既有 human gate；
- `GateDocument` 持久化 `source_outcome` 与完整 subject refs，resolution 在任何后续路由前要求
  `BLOCKED` 必须带非空 human note；
- runtime/worker、review packet、UI、API、CLI 与 autorun 均消费同一个 gate source outcome；
- compiler 仅接受 outcome 一致的 current approved lineage，并把 reviewer sidecar 的
  `must_fix` / `missing_evidence` 与 human note 投影为 contract resolution；
- full、contract、terminal-delivery 三个 bundled workflow 均补齐 requirement/design/plan 的
  `BLOCKED -> existing human gate` edge；acceptance review 仍不产生 `BLOCKED`，因此不在本次扩展范围；
- focused tests 与完整 Maven reactor 通过：`1210 tests / 0 failures / 0 errors / 0 skipped`；
  `git diff --check` clean。

当前证据只支撑 `ENFORCED`。未经 fresh real-case 自然到达 human gate、由用户通过 UI 或 CLI
提交决定并继续到 compile，不得标记 `PROVEN`，也不得声称 captcha-auth 已完成。

独立代码级审查见
`docs/plans/human-decision-gate-convergence-implementation-review-20260812.md`，结论为 `PASS`；
其中关于 full reactor “尚未运行”的描述是审查时点事实，审查后已完成非沙箱完整验证，最终结果为
`1210/0/0/0`。
