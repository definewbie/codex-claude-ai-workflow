# 架构诊断独立复审：四条判断的裁决与一项修复

日期：2026-11-24。对象：`output/cross-month-ledger-review-20261102/review.zh-CN.md`（Codex 诊断）。方法：每条判断按"待证伪"处理，先对码、再用离线负例/历史 ledger 核验，不采信作者结论；历史 ledger 只读。

本次修复：**gate 决定绑定到决定者所见的 gate occurrence**（判断 1 前半）。改动清单见 `CHANGES.md`；改前/改后证据见 `evidence.md`。

---

## 0. 总体裁决

| 判断 | 裁决 | 一句话 |
|---|---||
| 1 审批记录混淆"本人批准"与"自动代决"；Web 批准缺版本绑定 | **成立**（两半都成立，性质不同） | 版本绑定缺失是**无政策争议的缺陷，已修**；"自动代决"是**已发生的政策问题，须用户决定** |
| 2 "只补证据"在源码被写后才發现越界 | **成立**（Codex 判 FAIL 正确） | 取证节点与实现节点同权限；"不得改源码"仅为提示词。未修——需 BOUNDARY 全套仪式且触及 D04 收敛边界 |
| 3 计划审批页建议与停因不符 | **部分成立** | 两案矛盾事实成立且需不同动作；但 9YYF 已 COMPLETED、7SNA 用户已手动选对动作——是**投影缺口**，非已致错 |
| 4 程序自动生成待审计划 | **被反例推翻**（作为本轮开发候选） | 9YYF 的 method 是需人确认的长自然语言，不是机械复制；该路径无独立可验证收益 |

---

## 1. 判断 1：审批记录与版本绑定 —— 成立

### 1a. 旧 gate 请求被应用到错误的门（已修）

**确认了什么**（对码）：`RoutingEngine.resolveOpenGate` 的全部 4 个重载都不接收调用方所指的 gate，固定取 `activeNodeInstanceIds().get(0)`。三个入口都无 subject 通道：
- Web：`WorkstreamActionService.approve(root, ws, note, run, maxSteps)` —— 无 subject；`ReviewApproveRequest(note)` —— 无 subject；`console.js approveAndRun` 发 `{ note }`。
- CLI：`ReviewDecideCommand` 无 `--subject`。
- autorun：`resolveGate` 只传 action+note。
- 决议写入的 result 只有 `human_action/requested_action/note/decision_checks`。

**最强反例检查**：同文件内 `requestAction`（RoutingEngine:597-605）绑定 `gateNodeInstanceId` 并强制 `sourceRefs` 相等；`ReviewGateActions.revise` 绑定 `expectedSubject` 并要求它是唯一活动节点。**兄弟路径做对了，主批准路径是唯一缺口**——这不是设计选择，是遗漏。`WorkstreamQueryService.reviewPacket:347` 已把 `gateNodeInstanceId` 返回给前端，UI 只是不回传。

**可复现负例**（离线，当前代码）：两个先后开放、都允许 `approved` 的门 A/B；推进到 B 后提交为 A 组成的批准。**改前**：`BASELINE after stale approval: active=after gateA=node-gate-a-002 gateB=node-gate-b-004` —— B 被批准、工作流推进到 `after`、ledger 无任何拒绝。**改后**：`IOException("gate decision subject is no longer the open gate: requested node-gate-a-002 but the open gate is node-gate-b-004")`，`current` 与事件行数不变，B 仍 `open`。

**与 Codex 的差异**：Codex 把它列为"可能混淆"待查；我的核验把它升级为**已证实缺陷 + 已修**。Codex 未指出 `requestAction`/`revise` 已有正确先例。

**若判断错的后果**：若 ledger 其实已有拒绝机制而我漏看，本次改动是冗余的双重检查——无害但不必要。基线测试的 `active=after` 输出排除了这种可能。

### 1b. 自动代决记为 `human_action: approved`（政策问题，未修）

**确认了什么**：
- `AutoRunCommand.resolveGate` 以固定 note `"auto-resolved from autorun"` 调**同一个** `ReviewGateActions.resolve` → 写入同一 `human_action=approved` 字段，决议记录**无来源标识**。
- `HumanGateSemantics.requiresOperatorDecision` 只对 `design` 用途门与 BLOCKED/NEEDS_REVISION 返回 true。因此当前代码下 autorun 可自动批准：`requirement_review`（GOAL）、`contract_review`（EXECUTION 精确范围）、`human_acceptance`（**最终交付**）、`plan_approval`。
- **P837 历史实证**：9 个门的 `human_action=approved` 来自 autorun——含 requirement、两次 contract、acceptance，以及 design/plan（后者当前代码已要求人工；P837 为 6 月版本，不能外推当前 autorun 会批 design 门）。

**最强反例（也是最重要的边界）**：这不是遗漏，是**被测试固定的既有设计意图**。`AutoRunCommandTest:42-43` 名为 `autorunApprovesIntermediateHumanGates`，断言 `gateActionFor("gate.human_requirement_review") == "approve"`；`:38-39` **显式断言** `requiresHumanDecision(contract, contract_review, READY_FOR_CONTRACT_REVIEW) == false`。即"autorun 自动批准 READY 的 contract 门"是写入测试的预期行为。因此 1b 的准确定性是：**既有设计意图与 AGENTS "Standing Authorization Envelope" 契约文字（"不自动代替需求、scope、产品验收或交付等业务 human gate"）之间的冲突**，而非实现偏离设计。用户决定时须知道：选"收紧"意味着改掉一个有测试守护的既有意图；选"保留"意味着应改 AGENTS 文字并补来源标识。`AutoRunCommand` 另有 `--max-gates` 上限与重复门检测——并非无界。冲突的另一方是 AGENTS "Standing Authorization Envelope" 的明文："该授权不自动代替需求、scope、产品验收或交付等业务 human gate 的决定；agent 代决必须有用户显式授予的范围"。

**若判断错的后果**：若用户意图就是 autorun 可代批一切非 design 门（测试暗示如此），则这是契约文字过严而非代码缺陷——改 AGENTS 并补来源标识，而非改 autorun。这正是需要用户决定、不能由我代选的原因。

**需用户决定的具体选项**（影响调用方：`AutoRunCommand`、`OperatorWorkspaceRegistry` 的 autorun 登记、所有以 `dualctl autorun` 驱动的 E2E 脚本）：
1. **收紧 autorun**：`requiresOperatorDecision` 对 `requirement/contract/acceptance` 用途返回 true（autorun 在这三类门停机）。与 AGENTS 契约文字一致。**我的建议**。
2. **保留自动批准但标识来源**：决议记录增 `decision_origin: HUMAN|AUTORUN`，UI/投影区分显示；并要求 autorun 启动时显式 `--delegation <包>` 指向用户签发的授权包。
3. **两者都做**。
4. **改 AGENTS 契约**：明确 autorun 可代批非 design 门（则无需改代码，但 P837 式记录将无法区分人机）。

未选 1–3 直接实施的原因：这是"谁能批准"的产品政策，且涉及 ledger 写入字段（选项 2），按 AGENTS "拿不准先向用户确认"。

---

## 2. 判断 2：取证节点的写权限 —— 成立（Codex FAIL 正确），本次未修

**确认了什么**（对码，与 Codex §4.3 一致并补充）：
- `EvidenceCollection.promptInstruction` javadoc 自declared "**Advisory projection only; execute() and publication validation own enforcement**"。`AcpxAgentTaskHandler:259` 把它追加进 prompt。
- `AcpxAgentTaskHandler.permissionProfile(context)` **只读 workflow definition 的 `permission_profile`**，不知道 occurrence 是否取证。
- `implement.claude_self_test_report` 在 full/contract/terminal-delivery **三个变体**均为 `permission_profile: implement` → `AcpxPermissionProfile.implement()`：`Write/Edit/MultiEdit/Bash` 自动允许、`autoApproveKinds` 含 `edit/execute`。**与真正实现节点 `implement.claude_implement` 完全同权限**。
- `Guard.verify()` 在 `execute()` **之后**（:103）与发布时（:108）比对源码——这是事后拒收，不是写前阻止。

**三种证据的区分**（按你的要求）：现有 `EvidenceCollectionSequenceTest:93` 序列会真实改 `source.txt` 后断言节点失败且**磁盘修改仍在**——这是"实际适配器配置下写入发生 + 结果被拒发布"的证据，不是"OS 拒绝写入"。OS 拒绝（EPERM）只在 D04 的原生实验里对**工作区外**目标成立；对工作区内产品文件无任何 OS 级拒绝证据。

**最强反例**：历史 io-forensics 中 `EVIDENCE_COLLECTION_SOURCE_INVALID` 计数为 **0**——从未在真实案例中触发过。Codex 也声明"不是声称真实 provider 已复现新事故"。所以这是**设计缺口**（Enforced before 缺失），不是已发生事故。

**若判断错的后果**：若取证节点其实需要写能力（例如生成测试报告到源码树），则"写前收窄"会误拒合法取证——Codex 修复路线第 1 条已指出此点（"构建和测试生成物需有明确出口"）。

**为何不选它开发**：(a) 修复面横跨 adapter profile 选择、E1 `ClaudeExecutionPolicy` 的 `deny_write`、以及"合法测试能否生成指定工件"的正向证明——是 BOUNDARY 全套仪式；(b) 需要一个设计决定：在 D04 收敛（§8.25"Dual 不建沙箱，复用原生权限"）边界内，"写前收窄"用原生 `deny_write`（已在 D04 §8.24 核验过接口）还是新 profile；(c) 无历史复现，负例需从头构造原生工具写入——离线只能证 profile 选择与 policy 文档，证不了原生强制。ROI 低于判断 1。

---

## 3. 判断 3：计划审批页建议与停因不符 —— 部分成立

**确认了什么**：
- 9YYF `node-plan-decision-020`：`decision=READY_TO_IMPLEMENT`、`summary="…可提交精确 EXECUTION 范围审批后实施"`，同一 result 内 `readiness_reason=VERIFICATION_READINESS_REQUIRED`、`readiness_detail="missing environment or feasibility reference"`。
- 7SNA `node-plan-decision-020`（第一轮）：同样 `READY_TO_IMPLEMENT` + `summary="…可进入精确 EXECUTION 范围审批"`，`readiness_detail="unexpected or duplicate criterion"`。
- **两案需要不同动作**：9YYF 缺环境引用→需**登记** readiness；7SNA criterion 不匹配→需**修改声明内容**（用户在 `human_plan_approval-021` 的 revise note 里已正确指出"TR-1..TR-4 是测试要求，不能代替 AC 绑定"）。
- `console.js:812/:905` 直接渲染 `result.summary`；`readiness_reason` 未进审批页。`RecoveryAssessment` 的 `record-readiness` 建议只在 **FAILED** 节点分支（:116-128），而 plan.decision 是 **COMPLETED**——该分支不触发。

**最强反例（为何只是"部分成立"）**：
- **9YYF 当前 `COMPLETED`**——已交付，020 是历史中间态，用户未被误导。
- **7SNA 当前在 `plan.codex_plan_review-025` READY**（第二轮），用户已通过 `revise` 做出正确动作。
- 因此这是**投影缺口**（summary 与 readiness_reason 矛盾、建议动作未区分两种原因），不是**已致错**。且"点击建议动作后去哪"在当前 UI 是 `approve`（`approveAndRun`）——对 `VERIFICATION_READINESS_REQUIRED` 源的门，approve 会进 `contract_review` 编译并在 readiness 校验处拒绝（`ReviewGateActions` contract 路径的 `executionCandidate` 要求 `decisionChecks` 非空且无 FAILED）——即**不会越权放行**，只是多一次失败。

**若判断错的后果**：若 UI 其实在别处展示了 readiness_reason（我 grep 未发现），则缺口比我说的小。

**修复形态（未实施）**：`ReviewDecisionHandler:109` 已写 `readiness_reason/readiness_detail` 进 facts；最小改动是审批 packet 把它们投影出来、UI 在 summary 旁显示，并按 detail 区分"登记"与"修改声明"两种建议。属第二类技术债（投影），可搭车。不应改为"程序自动判断该登记还是该改"——那是判断 4 的陷阱。

---

## 4. 判断 4：程序自动生成待审计划 —— 作为开发候选被反例推翻

**确认了什么**（9YYF `plan-claude.contract.json`，run-1792724160451）：`verification_bindings` 三条的 `method` 是 150–300 字的自然语言，含具体命令（`python -m unittest discover -s tests -v`）、断言（`Ran 11 tests`、零 failure）、覆盖率要求（九类输入、11 个方法名与 CASES 一一对应）。这些是**需要人确认的事实性内容**，不是机械复制。

**最强反例（推翻"值得做"）**：
- 10-28 那批已把"method 必须逐字复制已登记 readiness 声明"修为程序固定 + 模型引用 ID（`ContractRevisionBinding`/`VerificationReadiness`）——**机械部分已被程序接管**。剩下的 `method` 长文本是人要审的实质内容。
- "送审前检出错误"能消除的：7SNA 类的 criterion 不匹配（程序可比对声明与 AC ID 集合）——**这是校验，已存在**（`unexpected or duplicate criterion` 就是它检出的）。
- "程序生成计划"能消除的：无法确定——9YYF 的 plan 内容本身来自设计文档与任务需求，没有可机械推导的来源。7SNA 作为反例：其错误正是模型**自撰** bindings 时产生的，程序生成不会更好，只会把"模型写错"换成"程序模板不符合任务"。

**若判断错的后果**：若存在一类计划其全部字段都可从 GOAL/设计机械推导，我漏看了。9YYF/7SNA 两案均非此类。

**结论**：不列为开发候选。Codex 路线第 4 条的措辞（"仍需证实冗余后再改契约"）与此一致。

---

## 5. 行动排序

| 序 | 行动 | 影响谁 | 消除什么 | 代价 | 验收观察 |
|---|---|---|---|---|---|
| **1（已做）** | gate 决定绑定 subject，三入口透传 | 所有经 Web/CLI/autorun 批准门的人 | 旧视图请求批准错误的门（可致越权推进：如批准了用户未见的 EXECUTION 范围） | 12 hunk / 7 文件 + 3 测试类；无 schema 变更 | 基线 `active=after` → 改后 IOException；合法用例仍到 `after` |
| 2（用户决定） | autorun 对 requirement/contract/acceptance 的代批政策 | autorun 调用方、E2E 脚本 | 人机决议不可区分 + 与 AGENTS 契约冲突 | 选项 1 约 3 行；选项 2 需 ledger 字段 + UI | autorun 在 contract 门停机 / 决议带 origin |
| 3（下批 BOUNDARY） | 取证 occurrence 写前权限收窄 | 所有走补证据路径的案例 | 源码被写后才發现（设计缺口，历史 0 次） | adapter profile + E1 deny_write + 正向工件生成证明 + 原生强制实验 | 取证节点工具写产品文件→副作用前拒绝；合法测试工件仍可生成 |

**不做**：程序生成待审计划（判断 4）、审批页 readiness 投影以外的 UI 改动、任何新平台。

---

## 6. 对 Codex 诊断的具体异议

1. **"取证写前权限 FAIL"我同意**，但 Codex 未指出：三变体 self_test 节点与 implement 节点 `permission_profile` **字面相同**（非仅"都使用 implement profile"），且 `permissionProfile()` 的选择逻辑完全不读 occurrence 语义——这让修复落点比 Codex 说的更明确（在 `permissionProfile(context)` 按 `inputTransition.containsKey("evidence_collection_gate")` 分支）。
2. **Codex §4.4 称"明确缺口在策略产出与消费的接线"**——同意，但它漏了更前面一层：autorun 的代批范围本身（1b）与 AGENTS 契约冲突。这不是"接线"问题，是政策未定。
3. **Codex 对判断 1 的表述"审批记录可能混淆"过弱**：版本绑定缺失不是"可能"，是基线测试可复现的缺陷。
