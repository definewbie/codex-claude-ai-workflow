# 首个完整 E2E：Dual Harness 复盘与简化决策

日期：2026-08-18  
文档性质：架构决策说明，不是代码修改授权
Dual 基线：`68c4f95`  
Captured workstream：`ws-20260818T011340Z-WA53`
验证状态：完整 E2E 已跑通；仍有已识别但未修复的账本风险

## 0. 如何阅读本文

这份文档回答四个问题：

1. Dual 是否真的跑通过一个完整开发流程？
2. 这次运行暴露了哪些 Dual 自身的问题？
3. 哪些复杂机制仍有价值，哪些可以删除或实验性简化？
4. 下一步应该先做什么，哪些事情暂时不要做？

如果只需要做决策，阅读第 1、6、9、10 节即可。第 2 至第 5 节是证据和归因，
第 7、8 节解释剩余风险，第 11 节集中解释术语。

本文有意区分三类陈述：

- **事实**：可由 ledger、typed artifact、测试结果或代码直接复核；
- **判断**：基于事实做出的架构取舍；
- **候选**：尚未获准实施，必须先经过清单、测试或 Verification Gate。

## 1. 一页结论

### 1.1 这次到底证明了什么

Dual 首次完成了一个真实、完整、包含多轮人工决策的开发流程：

```text
需求和任务合同
-> 设计、审查与人工修改决定
-> 实现
-> PostgreSQL 支撑的自动测试
-> 产物范围校验
-> 独立验收
-> 人工最终验收
-> workflow COMPLETED
-> completion audit 确认没有缺失步骤
```

最终结果不是由模型自述判定，而是由 workflow ledger 和结构化验收产物共同证明。

### 1.2 这次发现了什么

真实运行暴露了 5 个 Dual harness 缺陷。`68c4f95` 关闭了本次 captured case 的阻塞点，
但进一步复核发现 H1 只完成了样本级缓解，尚未形成通用边界：

| 编号 | 人能观察到的问题 | 本质原因 |
|---|---|---|
| H1 | 合法 task contract 超过固定阈值时，验收上下文构建会失败 | 必达合同与普通 section 共用固定 soft budget 规则 |
| H2 | 代码缺陷被错误归类为合同缺陷 | 自由文本覆盖了结构化结论 |
| H3 | 错误分类写入后无法安全重算 | recovery 缺少确定性的重分类动作 |
| H4 | 用户输入 `approve`，系统却因 `approved`/`approve` 不一致拒绝 | CLI、API、ledger 都在解释同一个 action |
| H5 | 最终审批已完成，API 仍尝试 resume，随后报错 | 命令组合没有识别 terminal workflow |

### 1.3 当前最重要的三个方向

H1/H4 是已经存在的正确性残余，R1 是高影响低频风险，subtraction 是优化。三者不能混为一谈，
也不能把优化排在已知正确性缺口之前：

| 方向 | 目的 | 当前优先级含义 |
|---|---|---|
| **已知边界收敛 H1/H4** | 保证合法完整合同必达，并消除 gate definition 的 action 声明漂移 | 先完成 deterministic architecture checkpoint；不调用 provider |
| **账本一致性 R1** | 防止一次状态转换写多个文件时，进程崩溃留下互相矛盾的状态 | 正确性影响最大，但应先评估，不直接建设事务框架 |
| **Harness subtraction** | 删除强模型不再需要的通用 prompt、重复规则和无消费者调用 | 成本低、回报快，但不能抢在已知正确性残余之前 |

### 1.4 当前决策

1. Dual 已证明能够完成完整 E2E，不应再以“整个架构不可用”为前提继续加机制。
2. 不再为同类问题重复制造 captcha-auth provider 样本。
3. H1 需要单独收敛为“合法合同完整必达 + soft budget 只告警 + hard capacity 独立守卫”；
   在此之前不得继续用 `2500` 宣称通用闭环。
4. H4 不再增加 mapper；只允许增加 workflow-definition visibility rule，保证 outgoing edge action
   一定出现在 `allowed_actions`，同时保留显式 no-edge terminal action。
5. H2 已 `ENFORCED + PROVEN`，不再继续修改分类 prompt、schema 或 routing；只保留回归测试。
6. H1/H4 两个独立 checkpoint 完成后，再做 R1 bounded assessment 和 Prompt/Rule Ownership Inventory。
7. Human gate、typed contract、validator、ledger、recovery、测试证据和 completion audit 不因模型增强而删除。

## 2. E2E 成功的证据

以下是 captured ledger 中可直接复核的终态事实：

| 要回答的问题 | 权威证据 | 结果 |
|---|---|---|
| Workflow 是否结束？ | `workflow/current.json.status` | `COMPLETED` |
| 是否还有待执行节点？ | `activeNodeInstanceIds` | `[]` |
| 完成审计是否通过？ | `domain/completion-audit.json.status` | `ready` |
| 生命周期是否真正结束？ | `lifecycleState` | `ENDED` |
| 最终停在哪个节点？ | `finalNodeDefinitionId` | `gate.human_acceptance` |
| 人做了什么决定？ | `finalOutcome` | `approve` |
| 是否缺少必需产物？ | `gaps` | `[]` |
| 自动测试结果？ | self-test 与 acceptance evidence | `99 / 0 failures / 0 errors / 0 skipped` |
| 变更范围是否合规？ | artifact validation | `PASS`，违规项为 `0` |
| 独立验收是否阻塞？ | acceptance typed result | `ACCEPT_WITH_NOTES`，`blocking=false` |
| 是否仍有必修项？ | `must_fix`、`missing_evidence` | 均为空 |
| 外部 session 是否遗留？ | ACP session closure evidence | Claude/DeepSeek 与 Codex session 均关闭 |

这里的“权威”含义很简单：如果最终报告的自然语言与这些字段冲突，以这些字段为准。

## 3. 五个 Harness 缺陷如何发生、如何关闭

### 3.1 H1：合法合同仍可能被固定预算阻断

**用户看到的现象**

验收 reviewer 需要完整 task contract。Captured case 中，原 `2000` token 阈值不足，因此
context build 无法继续。

**为什么是 Harness 缺陷**

Task contract 已经存在且已经通过编译期机械校验。问题不是模型没有生成，而是 Dual 把
“必达合同是否完整”与“普通 section 的预算治理”混成了同一个固定阈值判断。

**当前代码的真实行为**

- 合同不超过 `2500` tokens：完整投影；
- 合同超过 `2500` tokens：因为 `OverflowPolicy.FAIL`，context node 直接失败；
- 总 package 超过 `6500` tokens：先删除可降级的低优先级 section；task contract 不会被截断；
- 若删除其他 section 后仍超预算：context node 失败；
- 现有 “required typed evidence is never dropped” 豁免只覆盖 required review subject，
  没有覆盖 `task_contract` section。

因此当前实现不会把半份合同静默交给 reviewer，这是正确的 fail-closed 行为；但它也不能保证
任意合法合同都能进入验收。

**修复**

将 acceptance profile 的 `task_contract` soft ceiling 从 `2000` 调整为 `2500`。这使 captured
contract 完整进入验收，且没有放宽 parser 或 validator。

**为什么这只是样本级缓解**

`2500` 仍是经验阈值。`TaskContractValidator` 当前检查结构、必要字段、scope 和条款关系，
但不限制条款数量或总字符数。下一份合法合同仍可能超过 `2500` 并阻断验收。

**目标边界**

更可靠的判断应分为四层：

1. `artifacts/task-contract.json` 必须存在、可解析、通过 `TaskContractValidator`，并能通过
   identity/hash/lineage 证明它仍是当前 `contract.compile` 产出的那一版；这是启动验收的资格，
   不由 token 阈值代替；
2. 合法 task contract 必须完整投影，不能 summary、截断或 drop；
3. 超过 section soft budget 时记录 warning、实际 tokens 和 `withinBudget=false`，但 soft budget
   本身不阻断验收；
4. provider 的真实硬 context capacity 必须是独立的 pre-dispatch guard。只有“完整输入 + 输出
   预留”确实超过 provider 能力时才 fail，不能用 `2500` 这样的治理阈值冒充硬上限。

当前代码尚没有 provider hard context capacity 的前置判定，因此也不能简单删除所有容量保护
并无限制继续发送。当前 `EvidenceManifest` 是可重建索引，只记录 task contract 路径而不记录
其 SHA，不能单独证明合同未被后续改写；“正确合同”的绑定证据也仍需补齐。

**当前状态**

本次 captured contract 已完整投影：`SAMPLE MITIGATED / PROVEN FOR CAPTURED SIZE`。

“不发送半份合同”已 fail-closed；“任意合法完整合同都能进入验收”仍为
`GENERAL BOUNDARY NOT ENFORCED`。

### 3.2 H2：结构化结论被自由文本覆盖

**用户看到的现象**

真实的代码/测试缺陷被路由为 `CONTRACT_GAP`，导致系统走错修复方向。

**为什么发生**

验收结果已经给出结构化字段：

```text
verification_remediation = TEST_CODE_CHANGE
```

但分类逻辑又读取了自由文本 `contract_gaps`，并让后者覆盖了前者。系统等于同时维护两套
事实来源，而且让更模糊的一套获胜。

**修复后的完整调用链**

下面的行号对应当前 source tree。行号可能随未来编辑移动，类名和方法名才是稳定锚点。

| 步骤 | 文件与行号 | 调用/产物 | 边界作用 |
|---|---|---|---|
| 1. 定义 typed 字段 | `dual-runtime/.../AgentResultEnvelope.java:204-219,237-251` | `AcceptanceReviewResult.failure.verificationRemediation`，closed enum 为 `NONE/EVIDENCE_COLLECTION/TEST_CODE_CHANGE` | 模型不能输出任意 remediation 字符串 |
| 2. ACP 首次解析 | `dual-adapter-acpx/.../AcpxAgentResultAcquirer.java:43-48,137-146` | `parser.parse(...) -> validator.validate(...)` | 在 adapter acquisition 阶段拒绝非法 envelope；这一步不是下游路由 SoT |
| 3. 校验 remediation 组合 | `dual-runtime/.../AgentResultEnvelopeValidator.java:172-178,394-412` | `validateVerificationRemediation(review, errors)` | 只有 `REJECT + missing_tests` 才允许非 `NONE` remediation，缺失或乱填均拒绝 |
| 4. Runtime 再做 artifact boundary 校验 | `dual-runtime/.../WorkflowRuntime.java:304-333` | `executeNode(...) -> artifactContractValidator.validate(...)` | Agent handler 返回后、node/route 持久化前的统一强制点 |
| 5. 重解析候选并生成 sidecar | `dual-runtime/.../ArtifactContractValidator.java:70-118,227-318` | strict parse + contract validate；调用 `AgentResultDocumentProjector.outputContract(...)`；写 `*.contract.json` 和 SHA-256 | 只有通过 grammar/schema/semantic 的结果才能成为 durable evidence |
| 6. 保留 remediation 与自由文本 | `dual-runtime/.../AgentResultDocumentProjector.java:91-94,263-276` | sidecar 同时写 `verification_remediation`、`contract_gaps`、`missing_tests` 等字段 | 两类信息都保留，但不在 projector 中决定路由 |
| 7. Acceptance decision 只读 validated sidecar | `dual-runtime/.../ReviewDecisionHandler.java:21-52` | `AgentEvidenceIndex.latestDecisionFromRefs(...) -> decision=REJECT` | 决策节点不解析 raw model text |
| 8. 验证 sidecar 与 ledger occurrence 的绑定 | `dual-runtime/.../AgentEvidenceIndex.java:62-87,186-206`、`AgentOutputLineageVerifier.java:17-75` | 校验 source refs、node、run、decision、SHA 和 successful execution | 伪造、篡改或陈旧 sidecar 不能进入 classifier |
| 9. Workflow 进入 classifier | `agent-lifecycle.terminal-delivery.json:930-941` | `acceptance.decision --REJECT--> acceptance.classify_failure` | Workflow definition 是该 transition 的 SoT |
| 10. Classifier 读取 typed failure | `dual-runtime/.../FailureClassifyHandler.java:39-82` | 再次通过 `latestDecisionFromRefs` 读取 sidecar，调用 `arbiter.classify(..., failure.verificationRemediation())` | classifier 不读取 acceptance Markdown 或 raw ACP stream |
| 11. Typed remediation 优先仲裁 | `dual-runtime/.../FailureArbiter.java:76-94` | `TEST_CODE_CHANGE -> FailureCategory.IMPLEMENTATION_BUG`，该分支位于 `contractGaps` 之前 | 即使 `contract_gaps` 非空，也不能覆盖“需要改测试代码”的 typed 结论 |
| 12. 持久化分类事实 | `dual-runtime/.../FailureClassifyHandler.java:84-118`、`dual-core/.../FailureClassification.java:20-52` | 写 `artifacts/failure-classification.json`；返回 `REJECT_IMPLEMENTATION_BUG` | RoutingEngine 消费 closed outcome，不消费解释文本 |
| 13. Routing 创建 repair iteration | `dual-runtime/.../WorkflowRuntime.java:258-260`、`dual-ledger-file/.../RoutingEngine.java:54-179` | `ledger.completeAndRoute(...) -> flowKernel.advance(...) -> nextRevisionIteration(...) -> context.implement_claude` | 由正式 ledger transition 创建下一 iteration，并受 revision limit 约束 |

表中的省略路径分别展开为：

```text
dual-runtime/src/main/java/dev/dual/runtime/handler/
dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/
dual-ledger-file/src/main/java/dev/dual/ledger/file/
dual-core/src/main/java/dev/dual/core/arbitration/
```

**Sequence**

```mermaid
sequenceDiagram
    participant M as "Acceptance model"
    participant A as "AcpxAgentResultAcquirer"
    participant W as "WorkflowRuntime"
    participant V as "ArtifactContractValidator"
    participant S as "Validated sidecar + ledger"
    participant D as "ReviewDecisionHandler"
    participant C as "FailureClassifyHandler"
    participant F as "FailureArbiter"
    participant R as "RoutingEngine"
    participant I as "Implementation repair iteration"

    M-->>A: "AcceptanceReviewResult(REJECT, TEST_CODE_CHANGE, contract_gaps, missing_tests)"
    A->>A: "Strict parse + envelope validation"
    A-->>W: "NodeResult with candidate output ref"
    W->>V: "artifactContractValidator.validate(...)"
    V->>V: "Re-parse + schema/semantic validation"
    V->>S: "Write acceptance *.contract.json + SHA; publish output refs"
    W->>R: "completeAndRoute(acceptance review, completed)"
    R-->>D: "Create acceptance.decision"
    D->>S: "Read decision through AgentEvidenceIndex"
    S-->>D: "Lineage-bound decision=REJECT"
    D->>R: "completeAndRoute(REJECT)"
    R-->>C: "Create acceptance.classify_failure"
    C->>S: "Read latest routed sidecar + verify lineage"
    S-->>C: "Typed FailureFields"
    C->>F: "classify(..., TEST_CODE_CHANGE)"
    F-->>C: "FailureCategory.IMPLEMENTATION_BUG"
    C->>S: "Write failure-classification.json"
    C->>R: "NodeResult(REJECT_IMPLEMENTATION_BUG)"
    R->>R: "Match loop edge + revision limit + create iteration"
    R-->>I: "context.implement_claude READY with classification refs"
```

Workflow 中决定最终目标节点的是 declarative edge：

- terminal-delivery：`agent-lifecycle.terminal-delivery.json:972-983`；
- full：`agent-lifecycle.full.json:872-883`；
- contract：`agent-lifecycle.contract.json:1009-1020`。

三者都声明：

```text
acceptance.classify_failure
  --REJECT_IMPLEMENTATION_BUG / transitionType=loop-->
context.implement_claude
```

**为何自由文本不再能覆盖 typed 结论**

`FailureArbiter.classify(...)` 的第一条分支先判断
`verificationRemediation == TEST_CODE_CHANGE`，随后才检查 `contractGaps`。因此：

```text
verification_remediation = TEST_CODE_CHANGE
contract_gaps = [任意非空说明]
missing_tests = [需要新增或修改的测试]

=> IMPLEMENTATION_BUG
=> REJECT_IMPLEMENTATION_BUG
=> implementation repair iteration
```

`contract_gaps` 仍写入 sidecar，作为解释和审计 evidence；它只是在控制优先级上不能覆盖 closed
typed remediation。

**Cannot-bypass tests**

- `FailureArbiterTest.java:173-193`：`TEST_CODE_CHANGE` 战胜 verification/category 文本，得到
  `IMPLEMENTATION_BUG`；
- `FailureClassifyHandlerTest.java:340-374`：同时提供非空 `contract_gaps` 和
  `TEST_CODE_CHANGE`，断言 outcome 为 `REJECT_IMPLEMENTATION_BUG`，并保留 validated review sidecar；
- `WorkflowFileLedgerTest.java:372-422`：真实 ledger `completeAndRoute` 后创建
  `context.implement_claude`、新 `iter-002` 和 `iteration_started` event，不允许 workflow 伪完成。

**失败时如何 fail-closed**

- envelope grammar/schema/semantic 非法：不发布 validated sidecar；
- sidecar 缺失、不可解析、SHA 或 lineage 不匹配：`FailureClassifyHandler` 返回 FAILED，不路由；
- acceptance decision 不是 `REJECT`：classifier 返回 `ACCEPTANCE_CLASSIFICATION_NOT_REQUIRED`，
  不制造 repair iteration；
- revision budget 已耗尽：`RoutingEngine` 进入 revision-limit blocked path，不继续调用实现 Agent。

**当前状态**

该错误形态已经过真实 case 证明：`ENFORCED + PROVEN`。

### 3.3 H3：错误分类写入后不能安全重算

**用户看到的现象**

H2 的错误分类已经持久化。修正代码后，旧 workstream 仍停在 recovery，不能简单“再跑一次”。

**为什么普通 retry 不合适**

原来的 classifier 已成功执行，只是结论错误。把它当作执行失败 retry，会篡改历史语义。

**修复**

新增严格 lineage-bound 的 `recovery replay-classification`：

1. 保留原 classifier execution；
2. 把误路由节点记录为有证据的 `SKIPPED`，而不是删除；
3. 将 classifier 重新置为 `READY`；
4. 解决 recovery gate；
5. 由同一个正式 `RoutingEngine` 根据新 typed decision 创建下一轮修复。

这属于“确定性重算错误决定”，不是“重试失败进程”。

**当前状态**

针对该精确错误形态：`ENFORCED + PROVEN`。通用崩溃一致性仍未解决，见 R1。

### 3.4 H4：人工 action 有多个解释者

**用户看到的现象**

用户提交 `approve`，CLI 或 server 先改写为 `approved`，terminal gate 随后拒绝该 action。

**为什么发生**

CLI、API 和 ledger 都在解释 action。任何一层的词汇差异都会导致同一个人工决定在不同入口
表现不同。

**修复后的责任边界**

这里需要区分“action 生产者”和“action 执行解释者”：

- CLI、UI/API 和 autorun 仍然可以生产不同的人机命令，例如 `approve`、`revise`、`clarify`、
  `reject` 或 `complete`；
- 它们不得自行决定该命令是否适用于当前 gate，也不得自行选择 successor；
- `WorkflowFileLedger.resolveOpenGate(...) -> RoutingEngine.resolveOpenGate(...)` 是唯一执行解释器，
  负责规范化、校验、持久化和路由。

所以修复目标不是让所有入口只剩一个按钮，而是让所有入口提交同一种 command，并让一个
authoritative owner 决定它的领域含义。

**完整代码调用链**

| 步骤 | 文件与行号 | 调用/事实 | 责任 |
|---|---|---|---|
| 1. CLI shortcut 提交 | `dual-cli/.../ReviewApproveCommand.java:33-49`、`ReviewReviseCommand.java:35-51`、`ReviewCompleteCommand.java:28-39` | 分别提交原始 `approve/revise/complete` | 只负责参数与 workstream lock，不规范化 outcome |
| 2. 通用 CLI 提交 | `dual-cli/.../ReviewDecideCommand.java:37-65` | `requireAction()` 仅做 required/trim，然后原样调用 ledger | `approved/clarify/reject` 等也不在 CLI 内解释 |
| 3. UI/API 提交 | `dual-server/.../static/mgt/app.js:1294-1317`、`static/console.js:1173-1194` -> `WorkstreamApiController.java:165-194` -> `WorkstreamActionService.java:115-168` | UI 调专用 approve/revise endpoint；service 向 ledger 提交 `approve/revise` | UI 的空 note 检查只是 UX；server 的必需反馈检查不是路由 authority |
| 4. Autorun 选择是否自动处理 | `dual-cli/.../AutoRunCommand.java:90-123,167-176,271-287` | BLOCKED/advisory gate 停止等待人；可自动 gate 提交 `approve`，final-next-stage 提交 `complete` | 它是 action producer；最终合法性仍由 ledger 判断 |
| 5. 唯一 mutation 入口 | `dual-ledger-file/.../WorkflowFileLedger.java:377-379` | 委托 `RoutingEngine.resolveOpenGate(...)` | 所有 production gate mutation 的公共边界 |
| 6. 锁定当前 open gate | `dual-ledger-file/.../RoutingEngine.java:324-336,380-395` | 要求 action 非空、存在 active node、active node 是 GATE、存在与该 node 匹配的 open `GateDocument` | 历史 gate、非 active gate 和伪 gate 不可被操作 |
| 7. 唯一规范化 | `RoutingEngine.java:337,366-378` | 只有 `approve` 且当前无 `approve` edge、只有 legacy `approved` edge 时，才映射为 `approved` | 兼容映射集中在 ledger；入口不能预先猜测 |
| 8. 校验 action 与人工 note | `RoutingEngine.java:338-357` | final-next-stage 禁止 approve；检查 edge/config；BLOCKED 与 design revise 强制非空 note | 不合法 command 在产生持久化副作用前 fail-closed |
| 9. 写入审计事实 | `RoutingEngine.java:358-363,398-405` | resolved gate 保存 canonical action/note；gate node result 同时保存 `human_action`、`requested_action`、`note` | 保留“用户提交什么”和“系统实际执行什么”两种事实，便于审计 |
| 10. 正式路由 | `RoutingEngine.java:54-220` | `completeAndRoute(...) -> FlowOrchestrationKernel.advance(...)`；写 completed gate node、匹配 edge、创建 successor/gate/iteration/event | 只有 workflow definition 决定下一节点；revision 受 trigger-target budget 约束 |
| 11. Terminal completion | `RoutingEngine.java:218-242,407-435` | 没有 successor 时清空 active/open gate，并完成 workflow、completion audit 和相关投影 | terminal `approve` 或 `complete` 是真实状态变化，不只是 UI 文案 |
| 12. 可选继续运行 | `ReviewApproveCommand.java:43-49`、`ReviewDecideCommand.java:51-57`、`WorkstreamActionService.java:120-136,154-160` | `--run/run=true` 才在 gate resolution 后继续执行；server 会先检查是否已 terminal | 后续 Agent 调用是 command composition 的第二个副作用，不属于 action 规范化 |

表中的省略路径分别展开为：

```text
dual-cli/src/main/java/dev/dual/cli/
dual-server/src/main/java/dev/dual/server/
dual-server/src/main/resources/
dual-ledger-file/src/main/java/dev/dual/ledger/file/
```

**Sequence**

```mermaid
sequenceDiagram
    actor H as "Human / autorun policy"
    participant E as "CLI or UI/API endpoint"
    participant L as "WorkflowFileLedger"
    participant R as "RoutingEngine"
    participant G as "GateDocument + node ledger"
    participant F as "FlowOrchestrationKernel"
    participant N as "Successor / iteration / terminal workflow"

    H->>E: "requested action + note"
    E->>E: "Validate transport parameters only"
    E->>L: "resolveOpenGate(action, note)"
    L->>R: "Delegate under WorkstreamLock"
    R->>G: "Load current active node + matching open gate"
    R->>R: "Canonicalize approve only when legacy approved edge requires it"
    R->>R: "Validate action and required note"
    alt invalid action, stale gate, or missing note
        R-->>E: "IOException; gate and workflow stay unchanged"
    else valid action
        R->>G: "Resolve gate: canonical action + note"
        R->>G: "Complete gate node: human_action + requested_action + note"
        R->>F: "advance(gate definition id, canonical action)"
        alt matching transition
            F-->>R: "successor transition"
            R->>N: "Create successor; optionally create bounded revision iteration"
        else no transition and terminal action is allowed
            F-->>R: "no successor"
            R->>N: "Complete workflow + completion audit"
        end
        R-->>E: "durable result"
        opt --run or run=true and workflow is non-terminal
            E->>N: "Resume until next boundary"
        end
    end
```

**哪些已经 ENFORCED**

| 不变量 | 成熟度 | 证据 |
|---|---|---|
| 所有 production gate mutation 最终经过 `RoutingEngine.resolveOpenGate()` | `ENFORCED` | 全仓 production `resolveOpenGate(` 调用只从 CLI、autorun、server service 委托到同一 ledger 方法 |
| `approve -> approved` 兼容映射只有一个 owner | `ENFORCED` | `canonicalGateAction()` 是唯一规范化点；CLI/server 均提交 `approve` |
| 只能操作 current active open gate | `ENFORCED` | active-node kind 与 matching open `GateDocument` 在写入前检查 |
| action 合法性、BLOCKED note、design revise note、final-next-stage complete 语义 | `ENFORCED` | `RoutingEngine.java:338-357` 在 gate resolution 前 fail-closed |
| canonical action 与 requested action 都进入 durable ledger | `ENFORCED` | node result 保存 `human_action` 与 `requested_action`；gate document保存 resolution/note |
| action 引发的 revision 受正式 iteration/revision-limit 约束 | `ENFORCED` | `completeAndRoute()` 统一创建 iteration；不能由 UI/CLI 绕开 |
| terminal approve 后 server 不再 resume 已完成 workflow | `ENFORCED` | `WorkstreamActionService.java:120-130` 在 resume 前检查 `WorkflowStatus.COMPLETED` |

**哪些已经 PROVEN**

| 路径/行为 | 成熟度 | 判定理由 |
|---|---|---|
| CLI `review decide --action revise --note ... --run` 创建真实 design/implementation revision | `PROVEN` | captcha-auth WA53 中多次人工 revise 真实创建新 iteration 并继续 workflow |
| CLI `approve` 经过 ledger 完成 terminal `gate.human_acceptance` | `PROVEN` | 同一 WA53 最终以用户提交的 `approve` 达到 workflow `COMPLETED`，不是合成测试 |
| `approve` 对 legacy `approved` edge 的规范化 | `VALIDATED` | `WorkflowFileLedgerTest.java:147-182` 与 `ReviewDecideCommandTest.java:61-94` 覆盖；未单独依赖 provider 证明该兼容分支 |
| terminal approve-without-edge | `PROVEN + VALIDATED` | WA53 是真实样本；`WorkflowFileLedgerTest.java:944-972`、`ReviewDecideCommandTest.java:97-125` 是 cannot-bypass |
| server/UI approve/revise 与 terminal `approve + run` | `ENFORCED + VALIDATED / NOT PROVEN after convergence` | `WorkstreamActionServiceTest.java:55-125` 覆盖，包括 terminal 时不得 resume；收敛后未为 UI/API 专门制造新 real-case |
| `clarify`、`reject`、`complete` 的通用 action 处理 | `ENFORCED + VALIDATED` | `ReviewDecideCommandTest.java:24-58`、`WorkflowFileLedgerTest.java:184-226,760-799,1020-1048`；不是每种 action 都有自然 E2E 样本 |
| autorun 的 action 选择策略 | `VALIDATED / NOT generally PROVEN` | 单测证明 BLOCKED/advisory 停止与普通 gate action；它不应替代真实 human authority，因此没有必要追求所有 gate 的 real-case PROVEN |

这里最重要的限制是：**一个真实 CLI 样本只能证明该 CLI/action/gate 组合，不能自动证明
server、两个 UI、autorun 和所有 action 词汇。** 它们共享 ledger enforcement，所以不需要重复付出
provider 成本；但成熟度只能诚实写成 `VALIDATED`，不能借用 CLI 样本统一标成 `PROVEN`。

**Side effects**

一个成功的人工作用不是只改一个 action 字段。它会按顺序产生以下 durable 或外部副作用：

1. open `GateDocument` 从 `open` 变为 `resolved`，写 canonical action、note 和 resolved time；
2. gate node 从 `WAITING` 变为 `COMPLETED`，写 `human_action/requested_action/note`；
3. workflow event log 追加 route、iteration 或 completion 事件；
4. 有 edge 时创建 successor node；loop edge 还会创建新 iteration 并消耗该 trigger-target 的 revision budget；
5. 无 successor 的 terminal gate 会清空 active/open gate，写 workflow `COMPLETED`、completion audit 与相关投影；
6. `--run` 或 `run=true` 可能继续启动后续 context/Agent/verification 节点，产生新的 provider 调用、文件修改和 token 成本；
7. terminal 后 server 的 `maybeCloseTerminalSessions()` 或独立 session-reap 路径可以关闭 session，
   但 session closure 不能反过来改变 workflow truth；CLI shortcut 本身不应被描述成 session owner。

失败校验发生在第 1 项持久化之前，因此 unsupported action、缺 note、历史 gate 或非 gate active node
不会产生上述业务副作用。

成功 action 则是一次多文件 mutation：gate、node、events、workflow current/instance 等写入目前不构成
跨文件事务。进程若在中间崩溃，仍可能留下部分 side effect。这个风险属于 R1 崩溃一致性，不否定
H4 的单一 action owner，但也不能因为 H4 已 `ENFORCED` 就声称整次 mutation 已具备原子性。

**仍然存在的边界残余**

执行解释器已经唯一，但 declarative action 目前仍由两个定义面共同表达：outgoing edge 的 `on` 与
gate config 的 `allowed_actions`。`RoutingEngine.java:342-345` 当前接受二者并集：有 matching edge
即使未列入 `allowed_actions` 也可执行；列入 `allowed_actions` 但没有 edge 则可作为 terminal action。

这对 terminal gate 是有意能力，对普通 gate 则存在 definition drift 风险：UI packet 可能不展示某个
backend 实际接受的 edge action。因而当前准确结论是：

```text
action execution ownership: ENFORCED + CLI paths PROVEN
cross-entry mutation boundary: ENFORCED + VALIDATED
outgoing edge visibility in allowed_actions: VALIDATED, NOT FULLY ENFORCED
```

若后续要继续收敛 H4，优先项不是再加 action mapper，而是增加 definition consistency rule：非 terminal
gate 的 outgoing action 必须与 `allowed_actions` 一致；terminal no-edge action 必须显式声明。该工作不阻塞
当前 E2E 正确性，但在完成前不应把 H4 描述成“所有 action 定义完全单一来源”。

### 3.5 H5：最终审批后仍继续运行

**用户看到的现象**

`approve + run=true` 已经让 workflow 完成，但 API 继续调用 resume，最终呈现为“审批成功后报错”。

**为什么发生**

命令组合假设所有批准动作后面都有下一节点，没有先判断 workflow 是否已经 terminal。

**修复**

terminal approve 直接返回完成结果并关闭 session，不再 resume 已结束的 workflow。

**当前状态**

`ENFORCED / VALIDATED`。尚未单独制造 server real-case，因为没有必要为已覆盖的边界追加 provider 成本。

## 4. 哪些问题不是 Harness 缺陷

归因必须保留边界，否则每次模型或产品出错都会继续膨胀 orchestrator。

| 现象 | 正确归因 | 判断依据 |
|---|---|---|
| OTP 首次发送被 resend interval 拒绝 | captcha-auth 实现缺陷 | 业务代码行为错误，Dual 已正确执行和验收 |
| 设计经过多轮人工 revise | 不自动是 workflow 缺陷 | gate 阻止了不完整设计进入实现，属于预期作用 |
| 某个 provider 的业务方案质量不稳定 | 模型行为 | 只有 Dual 丢失上下文、事实或错误路由时才是 harness 缺陷 |
| captcha-auth 是否立即提交 | 产品仓库决策 | 不改变 captured workflow 已完成的事实 |

一个实用判断方法：

> 如果输入事实完整、边界正确执行、结果也被忠实记录，只是模型或业务实现不好，这不是
> harness 缺陷。只有 Dual 改变、丢失、误解或错误消费了事实，才应修改 harness。

## 5. 哪些 Harness 机制已经证明有价值

本次 E2E 不是只暴露问题，也证明了以下机制确实产生独特价值：

1. 模型输出必须通过 strict parser、schema、semantic 和 artifact validator 才能路由；
2. 人工决定必须由 ledger 依据 gate definition 的 transition/action 语义唯一执行；当前
   `allowed_actions` 与 outgoing edge 的声明一致性仍有下述 H4 residual；
3. acceptance 能发现真实实现缺陷，修复后仍需重新经过测试和验收；
4. recovery 必须保留历史，不能靠删除失败节点制造绿色结果；
5. workflow completion 与 completion audit 共同证明流程真正结束；
6. session cleanup 负责进程资源，不得改变 terminal business truth。

因此，正确结论不是“Dual 太复杂，全部删掉”，也不是“再加更多机制”。更准确的结论是：

> Dual 的核心边界有效，但长期积累的重复规则、旧 owner 和固定 ceremony 需要逐项证明后删除。

## 6. 剩余风险与人类决策

### 6.1 风险排序

| 风险 | 通俗解释 | 发生概率 | 影响 | 当前建议 |
|---|---|---:|---:|---|
| H1 residual | 合法合同超过 `2500` 时不会被截断，但验收 context node 会失败 | 中低，随合同规模增加 | 中 | 将必达完整性与 soft/hard capacity 分层 |
| H4 residual | outgoing edge action 未列入 `allowed_actions` 时，backend 可能接受 UI packet 未展示的 action | 低，内置 workflow 当前受测试约束 | 中 | 增加 edge visibility rule；保留显式 no-edge terminal action，不新增 mapper |
| R1 多文件写入崩溃一致性 | 一次状态变化要改多个文件，中途崩溃可能只改了一半 | 低 | 很高 | 先做 bounded assessment 和故障注入 |
| R2 replay 守卫覆盖不完整 | 部分“不允许重放”的条件只有代码，没有逐项回归测试 | 中低 | 中 | R1 协议明确后补 guard matrix |
| R3 旧 `approveDesign` owner | 生产已不用，但旧方法仍能写旧语义 | 低 | 中 | 适合做第一个纯删除 checkpoint |
| R4 replay 只有 operator CLI | UI/API 不能执行高风险补偿 | 当前是有意限制 | 低 | 保持，直到出现真实 API 需求 |
| R5 `finishedAt` 语义不够精确 | 快照时间可能变成补偿时间，原失败时间只在 event 中 | 中 | 低至中 | 与 R1 一起定义时间语义 |

### 6.2 为什么 R1 是“正确性风险最高”，但不一定先编码

R1 影响的是 ledger 本身。若 ledger 文件互相矛盾，UI、CLI、API 和 report 即使都正确读取，
也只能展示矛盾状态。

但“影响最大”不等于“现在就建设最大方案”。机器重启、`SIGKILL` 和磁盘错误发生频率低，
而事务框架成本很高。因此合理动作是先证明问题范围和最小协议，再决定是否编码。

## 7. R1：多文件写入为什么危险

### 7.1 当前写入方式

以 `replayDeterministicClassification()` 为例，一次逻辑动作会依次更新：

```text
失败节点
-> classifier 节点
-> recovery gate
-> events.jsonl
-> workflow/current.json
-> workflow instance
```

`AtomicJsonWriter` 能保证“单个 JSON 文件不会写半截”；`WorkstreamLock` 能保证“同一时刻
只有一个进程在写”。它们都不能保证上面六个文件要么全部更新、要么全部不更新。

可以把它理解为一笔转账：扣款和入账各自都能完整写入，但系统仍可能在扣款完成、入账之前
崩溃。

### 7.2 崩溃窗口示例

| 崩溃发生在 | 可能留下的矛盾 |
|---|---|
| 失败节点已改为 `SKIPPED`，classifier 尚未 `READY` | current 仍指向一个已经不再 FAILED 的节点，第二次 replay 也可能失去资格 |
| classifier 已 `READY`，gate 尚未 resolved | classifier 可运行，但 workflow 仍显示等待 recovery |
| gate 已 resolved，current 尚未更新 | gate 文件和 workflow 的 open gate 列表意见不同 |
| event 已追加，current 尚未更新 | 历史说 replay 已发生，当前状态却仍是旧状态 |
| current 已变为 `RUNNING`，instance 尚未同步 | 两个 workflow 投影对生命周期意见不同 |

同类顺序写还存在于 routing、stale recovery、abort 和 completion，因此不能只在 replay 外层
加一个 `try/catch`。

### 7.3 下一步只评估，不预设方案

Bounded assessment 需要：

1. 清点所有一次命令写多个 ledger facts 的 transition；
2. 标出每个 transition 中“从这里开始算提交成功”的 durable commit point；
3. 区分业务事实、历史 event、当前 snapshot 和可重建 projection；
4. 在每个写入边界注入故障并模拟重启；
5. 比较四类最小方案：commit marker、event-first rebuild、generation pointer swap、
   transition-specific idempotent reconciliation；
6. 只有多个 sibling transition 都需要同一机制时，才允许抽取共享实现。

明确不做：

- 不因为 R1 就迁移数据库；
- 不新建通用 event-store module；
- 不用异常捕获冒充 crash safety；
- 不通过反向覆盖文件冒充 rollback；
- 不用人工 runbook 替代可判定的恢复协议。

## 8. 模型变强后，Harness 应该留下什么

参考文章：[模型越来越强，harness 该留下什么？](https://www.bestblogs.dev/article/4d67f7f025)
（腾讯云开发者，2026-08-06）。

文章中的“根指令减少 61%、skills 减少 40%、Agent 从 10 减到 6”只是一个团队的案例，
不是 Dual 的目标。Dual 采用的是它的判断方法：公开知识交还模型，平台能力交还平台，
项目私有事实和人类责任继续由 harness 守住。

### 8.1 四类内容的通俗映射

| 类别 | 通俗含义 | Dual 示例 | 决策 |
|---|---|---|---|
| L0 公开知识 | 强模型本来就知道的常识 | “保持改动小”“不要无关重构”、角色人格 | 优先删除候选 |
| L1 平台能力 | ACP/agent runtime 已提供的能力 | 工具调用、session、read/search、基础 presentation | 平台已覆盖的删除；未覆盖的留在 adapter |
| L2 项目私有事实 | 模型不可能凭通用知识知道的规则 | TaskContract、scope、lineage、evidence refs、workflow profile | 保留，并尽量结构化和机械验证 |
| L3 人类责任 | 谁能授权、拒绝和承担后果 | human gate、allowed actions、权限、terminal acceptance | 必须保留 |

关键点：模型变强主要减少 L0 和部分 L1，不会自动知道 L2，也不能替人承担 L3。

### 8.2 Dual 已经做对的部分

| 方法 | Dual 现状 | 是否保留 |
|---|---|---|
| 用判据代替路径规定 | strict parser、validators、真实测试、completion audit | 保留 |
| 渐进提供上下文 | 7 个 `ContextProfiles` 按预算和优先级投影 | 保留能力，评估是否需要独立 graph node |
| 使用可验证引用物 | hash/lineage validated sidecar、typed refs | 保留 |
| 人类承担最终责任 | requirement/contract/design/plan/acceptance gates | 保留，但 LLM review 只做 advisory |
| 先测价值再删调用 | A1-A10 value validation、A10 same-case `10 -> 9` | 继续采用 |

A10 是已有的成功例子：terminal profile 没有下游 handoff consumer，因此删除一次调用；
full/contract profile 仍有消费者，所以没有全局删除。

## 9. 删除、实验与保留清单

这一节是候选清单，不是实施授权。

### 9.1 可以直接进入删除评估

#### D1：旧 `FileLedgerStore.approveDesign()`

- **事实**：生产路径不可达，只剩旧测试和旧语义；
- **替代 owner**：正式 gate action 已由 `RoutingEngine.resolveOpenGate()` 和 ledger 负责；
- **决策**：`DELETE NOW CANDIDATE`；
- **开始前证据**：production call graph 为零；
- **完成证据**：迁移/删除旧测试后 full reactor 通过。

#### D2：Prompt 中的通用指导

当前有 10 个 prompt 文件（416 行）和 1 个共享 template（12 行）。逐句删除前必须分类：

| 分类 | 含义 | 动作 |
|---|---|---|
| `PUBLIC_KNOWLEDGE` | 模型已具备的通用常识 | 删除 |
| `PLATFORM_ENFORCED` | 平台已经机械强制 | 从 prompt 删除，保留平台机制 |
| `DUAL_L2` | Dual 私有事实或流程 | 保留或迁移到 typed contract |
| `HUMAN_L3` | 人类授权和责任 | 保留为短而明确的边界 |
| `UNENFORCED_SAFETY` | 目前只靠 prompt 防守 | 不能删，先建立 deterministic owner |
| `EVAL_ONLY` | 只用于评测的说明 | 移入测试，不放 production prompt |

同一句话在不同 profile 中可能属于不同类别。例如，10 个 prompt 都写了“不得修改 `.agent/`”：

- `contractDiscovery` 和 contract correction 使用 `denyAll`，平台已经阻止工具副作用，属于
  `PLATFORM_ENFORCED`；
- implementation 使用 `approveAll`，目前没有文件系统策略或 post-run 扫描保护 `.agent/`，
  Git evidence 也看不到未跟踪 ledger，因此属于 `UNENFORCED_SAFETY`，不能删除。

这说明目标不是机械删重复行，而是让每条规则只有一个可解释 owner。

#### D3：Validator 已强制的重复输出规则

`AgentResultPromptContract` 有 368 行，其中包括 shape 示例、closed enum、scope、revision target
和 remediation 说明。部分规则已由 parser、validator 和 merger 机械强制。

- **目标**：把冗长规则缩成清晰的 typed interface；
- **不删除**：strict parser、single-fence normalization、schema/semantic/artifact validators；
- **原因**：Claude ACP 仍只保证 assistant message，没有可依赖的 native structured output。

### 9.2 只能实验，不能直接删除

| 调用/结构 | 现有证据 | 候选实验 | 当前决策 |
|---|---|---|---|
| A1+A2 intake producer/reviewer | 中等任务中 contract obligations 和 revision 有收益 | small profile 合并，保留 validator + human gate | `PROFILE EXPERIMENT` |
| A3 design + A5 plan | 中等任务已证明分阶段价值 | 仅小任务尝试单份 design-and-plan | `PROFILE EXPERIMENT` |
| A4 architecture review | captcha-auth 单一复杂 case（`n=1`）中三轮发现 OTP、事务、并发和容量问题 | 继续收集 profile 级证据，不做最终 authority | `KEEP FOR COMPLEX` |
| A6 plan review | 历史 T6 发现会进入代码的偏差 | 小任务条件关闭 | `PROFILE EXPERIMENT` |
| A8 self-test Agent | 当前拥有独立测试 evidence，但部分工作只是执行命令 | 仅在有 canonical test 入口的仓库试 deterministic executor；失败/歧义再调用模型 | `HIGH-VALUE EXPERIMENT` |
| 6 个 context-build nodes | context artifact 有价值，独立 node 是否有独特价值未证明 | 评估变成 agent-run 的强制 precondition | `NOT READY` |
| small workflow profile | 复杂任务不代表小 Bug 也需要 9 次调用 | 由用户显式选 profile，不让模型猜复杂度 | `READY FOR ASSESSMENT` |

### 9.3 必须保留的 Kernel

以下机制拥有外部事实、安全、状态、责任或验证，不能因为模型更强而删除：

1. typed TaskContract、scope authority、evidence refs、hash/lineage；
2. strict parser、schema/semantic/artifact validators；
3. permission、workspace、secret、process/session closure；
4. workflow ledger、terminal guard、recovery/compensation；
5. human allowed actions、approval/revision/rejection；
6. deterministic test/build/change-scope evidence；
7. completion audit、usage/token/cost accounting。

## 10. 接下来怎么做

原顺序先做 Prompt inventory 和删除 checkpoint。综合 H1、H2、H4 的代码级复核后，该顺序需要
修改：H2 已完成，不再投入；H1/H4 是仍未完全 `ENFORCED` 的已知边界，必须先于优化。

### 10.1 第一步：H1 必达合同投影计划与原子 checkpoint

先写 implementation-ready plan 并做一次同行评审，不直接从 `2500` 改另一个常量。计划必须钉死：

1. **资格 SoT**：只有当前 `contract.compile` 产出的 task contract，且通过 parse、
   `TaskContractValidator`、artifact identity、SHA 和 lineage，才具备必达资格；
2. **投影语义**：qualified task contract 在 acceptance context 中必须全文出现一次，不得 summary、
   truncate 或 drop；
3. **soft budget 语义**：超过 section ceiling 只写 warning、actual tokens 和
   `withinBudget=false`，不能阻止 context build；总 token 记账必须包含全部合同成本；
4. **hard capacity owner**：先核实 provider/adapter 是否提供可验证的模型 context capacity。
   有 authoritative capacity 才允许 pre-dispatch guard；没有时不得再发明经验硬阈值，超窗必须保留为
   typed adapter failure，而不是伪装成 H1 已完全 `ENFORCED`；
5. **失败边界**：已有成功 `contract.compile` occurrence 却合同缺失，以及 validator 失败、SHA/lineage 不匹配，
   都在 provider dispatch 前 fail-closed；从未声明 compile contract 的 thin/custom workflow 保留显式 placeholder；
6. **不能绕过测试**：构造一个超过 `2500` 的合法合同，证明全文逐字进入 acceptance context、warning
   和 token metadata 存在；篡改/陈旧合同必须拒绝；其他可降级 sections 仍按既有规则处理。

这里的“必达”只属于 acceptance authority boundary，不扩张到同名 section 的所有 profile。
`ContextProfiles.intake()` 中的 `task_contract` 仍承担早期辅助上下文职责，保持
`SUMMARY / DEGRADE / 500`：intake 的 authoritative 输入是 requirement、validated proposal/revision context，
不是最终验收裁决。若未来出现 intake summary 导致合同 revision 漂移的独立证据，再单独重审该 profile；
本 checkpoint 不用全局全文投影换取未经证明的 token 成本。

合同资格绑定不得新建 sidecar 或 binding 文件。`contract.compile` 在既有 node result 中记录
`task_contract_sha256`；acceptance context 从同一份 artifact 字节快照完成 SHA、parse、
`TaskContractValidator`、identity 和 compile-node lineage 校验，再从该快照投影。这样不扩大多文件 transition
写集，也避免“校验后文件被替换、渲染时再次读取”的 TOCTOU 缺口。

H1 实施只允许修改 context profile/projection、validated contract binding、token metadata 及直接测试；
不新增 workflow node、schema、provider 调用、自动 summary、动态删合同或通用预算框架。

H1 的完成口径分开声明：

```text
qualified full projection + soft warning: deterministic tests PASS 后 ENFORCED
provider hard-capacity preflight: 只有存在 authoritative capacity source 才能 ENFORCED
real-case value: 等待自然大合同，NOT PROVEN 不阻塞 deterministic checkpoint
```

### 10.2 第二步：H4 workflow action visibility consistency 计划与 checkpoint

H4 也先形成独立小计划并评审，不再修改 CLI、UI 或增加 action mapper。代码核实表明不能简单要求
edge actions 与 `allowed_actions` 两集合完全相等：三个现有 workflow 的 `gate.contract_review` 都显式
允许无 outgoing edge 的 `reject`，其领域语义就是终止 workflow。最小 consistency rule 应是：

1. 每个 outgoing edge action 在 canonical equivalence 后都必须出现在 `allowed_actions`，防止 backend
   接受 review packet 未展示的 hidden action；
2. `approve/approved` 只复用 ledger 已有 canonical compatibility，不复制第二套 mapper；
3. `allowed_actions` 中无 matching edge 的 action 保持显式 terminal command；必须继续覆盖 contract
   `reject`、terminal acceptance `approve` 和 final-next-stage `complete`，不能误拒；
4. 三个 bundled workflow 与 loader/factory sibling path 必须通过同一 validation；对缺省
   `allowed_actions` 的 legacy/custom definition 是否继续 fallback，须在计划中清点真实生产消费者后决定，
   不能顺手扩大为兼容性删除；清点必须包含 `HumanReviewGateHandler.allowedActions()` 内联的第二份
   `approve/revise` fallback，并与 ledger 使用的 action 语义收敛为单一 owner；
5. invalid fixture 覆盖 hidden edge action；positive fixtures 覆盖 routed canonical action 与三类 no-edge
   terminal action；
6. UI/API/CLI 继续只消费 review packet/提交 command，不新增 projection-side 判断。该 checkpoint 只证明
   workflow definition 与 review packet 的 action visibility 一致；当前两个 UI 的按钮集合仍是硬编码 UX，
   不得把 packet 一致性表述为“UI 已展示全部合法 action”。

这是纯 deterministic checkpoint，不调用 provider。完成后 H4 可声明
`action execution + edge visibility ENFORCED`；现有 CLI real-case 证据仍只证明已发生的 action 组合，
不扩大为所有入口 `PROVEN`。

### 10.3 第三步：冻结 H2，不继续优化

H2 的 typed remediation 优先级、validated sidecar、lineage、classifier 和 repair routing 已
`ENFORCED + PROVEN`。下一步只维护现有三层 cannot-bypass tests：arbiter、classifier handler、真实
ledger routing。除非出现新的 sibling bypass 证据，否则禁止继续调整 prompt、enum、parser 或 routing。

### 10.4 第四步：R1 multi-file mutation assessment

完成 H1/H4 后，执行第 7 节定义的写入清单和 failpoint/restart assessment。在证据出现前不选事务方案，
不授权数据库、event store、新 module 或通用 transaction framework。

### 10.5 第五步：Harness subtraction

1. 做只读 Prompt/Rule Ownership Inventory，覆盖 10 个 prompt、共享 template、
   `AgentResultPromptContract` 和对应 validators；
2. 选择一个纯删除 checkpoint，优先 D1，或一组已确认有 deterministic owner 的 D2/D3 规则；
3. 一次只删一个边界，保留机械 PASS/FAIL 和回滚条件；
4. 随后才制定 A8 counterfactual Gate 或 small-profile natural-task validation；
5. 当前 9-call 结论不是永久真理，但任何调用删除仍需 same-case 或等价反事实证据。

整个顺序不需要新的 captcha-auth 样本。H1/H4 用 deterministic fixtures 达到 `ENFORCED`；R1 用
failpoint/restart slice；只有 subtraction 的质量价值需要自然任务或明确 counterfactual。

执行纪律：

> 没有 owner 映射、机械 PASS/FAIL、反事实验证和回滚条件，不得删除。已经被平台或 validator
> 完整替代的规则，也不得因为历史上有用而永久保留。

## 11. 术语与成熟度

### 11.1 常用术语

| 文档术语 | 通俗解释 |
|---|---|
| Harness | 包围模型的流程、权限、上下文、验证、账本和人工决策机制 |
| Ledger | 保存 workflow 当前状态、历史事件、节点、gate 和产物引用的持久化文件集合 |
| Typed artifact / typed fact | 有固定字段和校验规则的结构化事实，不是自然语言总结 |
| Source of Truth / SoT | 发生冲突时最终以谁为准 |
| Owner | 唯一负责产生或改变某项事实的组件 |
| Projection | 从权威事实生成的 UI、CLI、报告或上下文展示；它不应反过来创造状态 |
| Terminal | 流程已结束，不应再 resume 或创建新副作用 |
| Recovery | 处理执行中断或持久化异常，不等同于业务 revision |
| Compensation | 保留历史的前提下修正已经发生的错误决定或副作用 |
| Fail-closed | 无法证明安全或合法时拒绝继续，而不是猜测通过 |
| Canonical test 入口 | 仓库明确、稳定、可重复的标准测试命令及结果格式 |

### 11.2 成熟度标签

| 标签 | 含义 | 人可以据此声称什么 |
|---|---|---|
| `MODELED` | 代码或文档里有这个概念 | 只能说“已建模” |
| `PROJECTED` | UI/CLI/report 能展示 | 只能说“可见” |
| `VALIDATED` | 可以机械校验 | 可以说“能发现不满足” |
| `ENFORCED` | 在路由或副作用前强制执行 | 可以说“该边界不能被正常路径绕过” |
| `PROVEN` | slice test 或真实 case 已实际触发并通过 | 可以说“在该证据范围内已证明” |

### 11.3 未来方案必须回答的七个问题

对 R1 或其他状态边界，评审时用下面七问，不必依赖英文术语：

1. 谁声明这个事实？
2. 重启后用什么证据证明它成立？
3. 哪个下游唯一使用它？
4. 不满足时，系统会阻止什么副作用？
5. 失败后怎样继续、回到旧状态或进入人工处理？
6. 哪个测试证明不能绕过？
7. 是否还有旧文件、旧入口或旧 projection 能绕过新规则？

R1 当前状态为：风险已由代码确认，但尚未修复。只有所有 mutation/load 路径受同一提交协议
约束，才能标记 `ENFORCED`；真实 kill/restart slice test 成功后，才能标记 `PROVEN`。

## 12. 证据索引

本节用于快速核验，不重复解释结论。代码行号会随修改漂移，因此同时记录稳定的类或方法名。

### 12.1 Captured E2E

完整 ledger 位于：

```text
/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/
ws-20260818T011340Z-WA53/.agent/workstreams/ws-20260818T011340Z-WA53/
```

优先查看：

- `workflow/current.json`：当前 workflow 状态和 active nodes；
- `workstream.json`：workstream 生命周期；
- `domain/completion-audit.json`：最终完成审计；
- `events.jsonl`：状态如何到达终态；
- `runs/`：每次 Agent execution 的输入、输出和结果；
- `session-cleanup.log`：外部 session 关闭证据。

### 12.2 关键代码入口

| 结论 | 代码入口 |
|---|---|
| H2 typed remediation 优先 | `dual-runtime/.../handler/FailureArbiter.java` |
| 模型输出结构校验 | `dual-runtime/.../handler/AgentResultEnvelopeValidator.java` |
| 产物与 scope 校验 | `dual-runtime/.../handler/ArtifactValidateHandler.java` |
| H3 deterministic replay | `dual-ledger-file/.../NodeStateWriter.java#replayDeterministicClassification` |
| H4 gate action 唯一执行边界 | `dual-ledger-file/.../RoutingEngine.java#resolveOpenGate` |
| R3 legacy owner | `dual-ledger-file/.../FileLedgerStore.java#approveDesign` |
| 单文件原子替换 | `dual-ledger-file/.../AtomicJsonWriter.java` |
| completion audit 写入 | `dual-ledger-file/.../CompletionAuditWriter.java` |

上述 `...` 分别展开为模块的标准路径：

```text
src/main/java/dev/dual/runtime/handler/
src/main/java/dev/dual/ledger/file/
```

### 12.3 反证方式

如果要推翻本文的某个判断，应提供对应层级的证据：

- 认为 E2E 未完成：指出 ledger 中与 `COMPLETED`、`ENDED` 或 completion audit 冲突的权威字段；
- 认为某项不是 harness 缺陷：证明 Dual 没有丢失、改写或错误消费输入事实；
- 认为某条 prompt 可以删除：证明平台或 validator 已唯一强制，删除后仍 fail-closed；
- 认为某个 Agent 调用可以删除：提供 same-case 或等价反事实，证明质量、证据和人工责任不退化；
- 认为 R1 已经安全：提供每个多文件写入边界的 restart/failpoint 结果，而不仅是单文件写入成功。
