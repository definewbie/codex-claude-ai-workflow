# 前后对照与验证边界

所有命令在 `dual-orchestrator/` 下离线执行（`mvn -o`），零 provider/native 调用。历史 ledger 只读。

## 1. 改前行为（可独立复现的负例）

改前文本已被本次改动覆盖；以下两份是**改前实际运行**的原始日志，保存在本目录：

- `baseline-behaviour.log`：用当时的公开 API（4 参 `resolveOpenGate`，唯一可用签名）构造 A→B 后提交 A 的批准。关键输出：
  ```
  BASELINE after stale approval: active=after gateA=node-gate-a-002 gateB=node-gate-b-004
  ```
  即 B 被批准、工作流推进到 `after`。该测试断言的就是缺陷本身（`assertEquals("after", activeDef)`），所以 `Tests run: 1, Failures: 0`——它证明缺陷存在，不是"测试通过=正确"。
- `red-ledger.log`：新测试 `GateDecisionSubjectBindingTest` 在改前源码上的编译失败（`找不到合适的方法` ×6）——证明改前 ledger **不存在**任何绑定调用方所指 gate 的入口。

要重现改前行为而不回滚源码：把 `RoutingEngine.resolveOpenGate` 中本次新增的 `if (expectedGate != null && !expectedGate.isBlank()) {...}` 块临时注释，运行下面的负例，它会失败（`active` 变为 `after`、事件行数 +1）。

## 2. 改后：负例必须通过（拒绝 + 零写入）

```sh
cd dual-orchestrator
mvn -o -pl dual-ledger-file -am -Dtest=GateDecisionSubjectBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -pl dual-runtime   -am -Dtest=ReviewGateActionsSubjectBindingTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -pl dual-cli       -am -Dtest=ReviewDecideCommandTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期（三层各一）：
- ledger：`requestNamingAGateThatIsNoLongerOpenIsRefusedAndWritesNothing` 通过——`IOException` 含 `no longer the open gate` 与 `node-gate-a-002`；`current` 前后相等；B 的 gate 仍 `open`。
- 应用入口（Web/CLI/autorun 共用）：`staleDecisionThroughApplicationEntryIsRefusedAndLeavesTheLedgerUntouched` 通过——额外断言 **`events.jsonl` 行数不变**（零事件追加）。
- CLI 命令：`decideWithStaleSubjectIsRefusedAndLeavesTheOpenGateUntouched` 通过——`--subject node-gate-a-002` 在 B 打开时被拒，事件行数不变。

**删除式反证**（已实际执行，隔离后恢复；日志 `counterproof-*.log`，不计入回归数）：
- **Mutant A** 去掉 `RoutingEngine` 的 subject 比对（应用层前置比对保留）：`GateDecisionSubjectBindingTest` **1 项失败**（ledger 层负例）；应用层与 CLI 层负例仍通过——应用层前置比对兜底。
- **Mutant B** 去掉 `ReviewGateActions` 的前置比对（ledger 层保留）：三层负例**全部通过**——ledger 层兜底。
- 结论：两层互为冗余；ledger 层是该边界的唯一 SoT 守卫（它守的是所有调用方，包括未来不经 `ReviewGateActions` 的入口），应用层前置比对是"在 qualification 工作前就拒绝"的早退优化。任一层单独存在都能阻止本缺陷；两层都在时无单一故障点。源码已恢复（`grep -c "Refuse before any gate" RoutingEngine.java` = 1）。

## 3. 改后：合法用例必须继续成功

- `requestNamingTheOpenGateResolvesIt` / `decisionThroughApplicationEntryNamingTheOpenGateResolvesIt` / `decideWithCurrentSubjectResolvesTheGate`：带正确 subject 的批准到达 `after`，gate 节点 `COMPLETED`、`human_action=approved`。
- `requestWithoutASubjectKeepsItsPreviousBehaviour` / `blankSubjectIsTreatedAsUnboundRatherThanMatchingNothing`：无 subject / 空白 subject 走原路径（兼容既有调用方）。
- 既有回归：`ContractQualificationSequenceTest`（29 项，含 contract 门批准、错身份拒绝、C/X 序列）、`WorkstreamApiControllerTest`（31）、`AutoRunCommandTest`（10）、`ReviewDecideCommandTest` 原 3 项——全部仍绿。

## 4. 全模块离线回归

见 `reactor-full.log`（单次 `mvn -o clean test`，期间未改动任何源码）。**BUILD SUCCESS，8 模块全部 SUCCESS；1,834 tests / 212 suites / 0 failures / 0 errors / 0 skipped，exit 0。**

与上一可信基线（10-28 批，1,819/210）逐 suite 对账：
- +2 suites / +8 tests = 本次新增：`GateDecisionSubjectBindingTest`(4)、`ReviewGateActionsSubjectBindingTest`(2)、`ReviewDecideCommandTest` 3→5。
- 另 +7 tests（`AgentResultEnvelopeParserTest` 14→17、`AcpxAgentResultAcquirerTest` 33→37）**不属于本次**：两文件 mtime 为 2026-11-01，是工作区既有未提交改动（F1/F2 修复批），10-28 基线日志早于它们。列出以避免把这 7 项记到本次头上。

**被作废的一次运行**：`reactor-INVALID-contaminated-by-mutants.log`——第一次全模块回归进行中，我执行了两个删除式 mutant 实验（各自触发 mvn 重编译同一 target），该次结果不可信，已停止并重跑。不计入任何凭证。

## 5. 验证边界

**覆盖了哪些入口**（离线）：
- ledger 层 `resolveOpenGate` 全部分支（普通门、contract 门经 `ReviewGateActions` 的 `executionCandidate` 路径）。
- 应用入口 `ReviewGateActions.resolve`（Web service、CLI `review decide`、autorun 三者共用）。
- CLI 命令对象级（`ReviewDecideCommand.call()`）。

**只做了源码核查、未实测**：
- Web 端到端：`console.js approveAndRun` 的请求体与 `WorkstreamApiController.approve` 的 record 反序列化——只核对了字段名匹配（`gateNodeInstanceId`）与 `WorkstreamQueryService.reviewPacket:347` 的来源；未启动 server、未在浏览器点击。
- autorun 的 TOCTOU 防护：`AutoRunCommand.resolveGate` 传 `node.nodeInstanceId()`，但"snapshot 读取后、resolve 前门被换"的并发场景未构造（单测 `AutoRunCommandTest` 10 项证明既有行为不变，不证新窗口）。
- `EvidenceCollection` 的 collect/submit 分支：零改动，靠既有 `captureBaseline` 的单活动门绑定；未新增测试。

**仍需实测**（真实运行才能回答）：
- 真实 Web 页面在另一会话推进后点击旧页面的"批准"——预期收到 4xx/IOException 文案而非静默成功。
- 旧 UI（不传 subject）对新 server：走未绑定路径，行为与改前相同——这是有意的兼容，也意味着**旧 UI 缓存未刷新时不受保护**。

**历史案例重放**：未做。P837/9YYF/7SNA 只做只读读取（gate node JSON、events、plan contract）。P837 是 6 月版本 ledger，其 autorun 行为不可外推到当前代码；9YYF/7SNA 的 `plan-decision-020` 用于确认投影矛盾事实，不用当前代码重放（当前代码的 `readiness` 校验已随 10-28 批次改变，重放属反事实对照，未执行）。

**三种证据的区分**（判断 2）：现有 `EvidenceCollectionSequenceTest` 证明"实际适配器配置下写入发生且结果被拒发布"；D04 原生实验证明"OS 对工作区外目标拒绝写入"；**不存在**"OS 对工作区内产品文件拒绝取证节点写入"的任何证据。
