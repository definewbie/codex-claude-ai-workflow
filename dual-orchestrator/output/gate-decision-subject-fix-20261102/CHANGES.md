# 本次改动清单：gate 决定绑定到决定者所见的 gate occurrence

日期：2026-11-24。工作区在本任务前已有大量未提交改动（HEAD `3362eb42` 之外）；本文列出的 hunk 是**仅本次任务**的改动块，供 Codex 独立 review。

`HEAD-diff-touched-production-files.patch` 是触碰文件相对 HEAD 的全量 diff，**混有既有未提交改动，不是本次范围**。本次范围以下表为准。范围核查的**证明边界**：Codex 改前快照 `source-sha256.json` 只覆盖 14 个 src 文件；其中仅 `EvidenceCollection.java` 变化（在声明集合内），其余 13 个未变，集合外 0 个被改。**快照不覆盖的 src 文件没有独立改前哈希**（11-02 的 `git-status-before.txt` 是 rtk 截断显示，不可用），其范围只能以本清单的声明为准——Codex 可用 `HEAD-diff-touched-production-files.patch` 逐 hunk 核对哪些属于本清单。

## 为什么要改（见 review.md 裁决 1）

`resolveOpenGate` 的所有重载都不接收调用方所指的 gate，总是取 `activeNodeInstanceIds().get(0)`。决定请求无法表达"我批准的是哪个门"，因此旧视图的请求会被应用到此刻恰好打开的任何门。同文件内的兄弟路径 `requestAction` 已正确绑定 `gateNodeInstanceId`，`ReviewGateActions.revise` 已正确绑定 `expectedSubject`——主批准路径是唯一缺此绑定的入口。

## 生产改动（9 处 hunk，7 个文件）

| # | 文件 | 位置 | 改动 | 性质 |
|---|---|---|---|---|
| 1 | `dual-ledger-file/.../RoutingEngine.java` | `resolveOpenGate` 7 参重载 | 原 7 参方法体改为委托 `resolveOpenGate(..., null)`；新增 8 参重载承载原方法体 | 重载拆分，原行为经 `null` 完整保留 |
| 2 | 同上 | 紧随 `node.kind() != GATE` 检查之后、任何校验/写入之前 | 新增 `if (expectedGate != null && !isBlank) { if (!expectedGate.equals(node.nodeInstanceId())) throw IOException("gate decision subject is no longer the open gate: requested X but the open gate is Y") }` | ledger 层强制点（SoT 守卫，对所有调用方生效）；在任何 qualification/subject/baseline 工作前拒绝。与 #5 互为冗余，见 evidence.md 删除反证 |
| 3 | `dual-ledger-file/.../WorkflowFileLedger.java` | 4 参 `resolveOpenGate` 之后 | 新增公开 5 参重载 `(paths, definition, action, note, String expectedGate)`，无会话时开写会话、有会话时直达 engine | 门面透传 |
| 4 | 同上 | `(…, Decision checks)` 重载之后 | 新增公开重载 `(…, Decision checks, String expectedGate)`，保留原 COVERED 前置校验 | 合同门路径透传 |
| 5 | `dual-runtime/.../ReviewGateActions.java` | `resolve` | 原 4 参改为委托 `resolve(..., null)`；新增 5 参：进入写会话后**首先**比对 `List.of(expectedGate).equals(active)`，不等即抛 | 应用入口早退检查（与 #2 同判据）；Mutant B 证明单独去掉它缺陷仍被 #2 拦住 |
| 6 | 同上 | 同方法内两处分派 | `EvidenceCollection.resolveGate(..., expectedGate)`；contract 分支按 `expectedGate==null` 选 6 参或 7 参 `resolveOpenGate` | 两条内部分支都携带 subject |
| 7 | `dual-runtime/.../EvidenceCollection.java` | `resolveGate` | 原 5 参改为委托 `resolveGate(..., null)`；新增 6 参：非 collect 分支按 `expectedGate==null` 选 4 参或 5 参 `resolveOpenGate`；collect/submit 分支不变（其 `captureBaseline` 已绑定单一活动 gate） | 透传；collect 分支零改动 |
| 8 | `dual-server/.../WorkstreamActionService.java` | `approve` | 原 5 参改为委托 `approve(..., null)`；新增 6 参直接调 `ReviewGateActions.resolve(ledger, paths, "approve", note, expectedGate)`。**未加**显式 `WorkstreamLock`（与同文件 `revise` 一致，`ReviewGateActions` 自管会话锁；加外层锁会嵌套冲突） | Web 入口 |
| 9 | `dual-server/.../WorkstreamApiController.java` | `ReviewApproveRequest` record / `approve` 端点 | record 增 `String gateNodeInstanceId` 字段；端点把它传给 service 6 参 | 请求体多一个可选字段 |
| 10 | `dual-server/.../static/console.js` | `approveAndRun` | 请求体由 `{ note }` 改为 `{ note, gateNodeInstanceId: state.review?.gateNodeInstanceId ?? null }`（`WorkstreamQueryService.reviewPacket:347` 已在响应顶层返回该字段） | UI 回传它渲染的门 |
| 11 | `dual-cli/.../ReviewDecideCommand.java` | 选项与调用 | 新增 `@Option --subject`；调用改为 5 参 `resolve(..., subject)` | CLI 入口 |
| 12 | `dual-cli/.../AutoRunCommand.java` | `resolveGate` 私有方法 | 调用改为传 `node.nodeInstanceId()`（autorun 刚检查过的 snapshot 节点） | autorun 入口；同时封住 snapshot→resolve 之间门被换的窗口 |

**未改**：`HumanGateActionSemantics`、gate action 目录、contract schema、决议写入字段（`human_action/requested_action/note/decision_checks`）、状态模型、锁类型、路由边。无新字段/schema/服务/平台。决议记录**未新增**"来源"字段（见 review.md 中对此的政策说明）。

## 测试改动

| 文件 | 改动 |
|---|---|
| `dual-ledger-file/.../GateDecisionSubjectBindingTest.java` | **新增**，4 项：旧门请求被拒且 `current` 不变、B 仍 open；当前门请求放行到 `after`；无 subject 保持旧行为；空白 subject 视为未绑定 |
| `dual-runtime/.../ReviewGateActionsSubjectBindingTest.java` | **新增**，2 项：经 `ReviewGateActions.resolve`（Web/CLI/autorun 共用入口）旧门请求被拒、事件行数与 `current` 均不变；当前门放行且 gate 节点 `COMPLETED/human_action=approved` |
| `dual-cli/.../ReviewDecideCommandTest.java` | **新增** 2 项命令级测试（`--subject` 旧门拒绝+零事件；当前门放行）+ 一个 `twoApprovalGates` 辅助方法；原 3 项未动 |
| `dual-runtime/.../ContractQualificationSequenceTest.java:233` | **既有测试的 1 处适配**：`resolveOpenGate(..., null)` → `resolveOpenGate(..., (Decision) null)`。原因：新增的 5 参 String 重载使裸 `null` 产生重载歧义；该测试意图是"缺 checks"，显式转型保持原意。这是本次改动对既有测试的唯一触碰，**非放宽** |

## 兼容性

- 所有旧签名保留；`expectedGate == null/blank` 走原路径（ledger 测试 `requestWithoutASubjectKeepsItsPreviousBehaviour`、`blankSubjectIsTreatedAsUnboundRatherThanMatchingNothing` 证明）。
- 既有 4 参调用方（`EvidenceCollection.resolveGate` 非 subject 路径、server `resolveGate` 私有助手用于 complete/scope_revision/collect 等、CLI 其他命令）行为不变。
- 历史 ledger：无迁移、无回填；旧决议字节不变。
- `gateNodeInstanceId` 在请求 record 中可为 null：旧 UI（不传该字段）对新 server 走未绑定路径，行为与改前相同——**有意兼容，但意味着未刷新的旧 UI 页面不受本修复保护**。新 UI 对旧 server 的行为（多余字段）未实测。

## 未做（有意）

- **未**给决议记录增加 `decision_origin`（human/autorun）字段：这是判断 1 后半（"混淆本人批准与自动代决"）的修复，涉及 ledger 写入字段与 UI 展示，且其前置政策问题（autorun 是否有权替用户批准 requirement/contract/acceptance 门）须用户决定——见 review.md。本次只修"旧请求应用到错误的门"这一无政策争议的缺陷。
