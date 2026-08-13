# Self-Test Semantic Repair Routing — P2-1 Delta 只读复审

- **审查对象**：上一轮 `self-test-semantic-repair-routing-review-20260813.md` 中 P2-1 的修复 delta（仅限该 delta 涉及的 3 个文件）
- **仓库**：`/Users/kriswang/work/projects/ai/codex-claude-ai-workflow`
- **分支 / 基线**：`codex/recovery-origin-requirements`，HEAD `80efca1f`，当前实现仍未提交
- **上一轮报告**：`dual-orchestrator/docs/plans/self-test-semantic-repair-routing-review-20260813.md`（CC 上一轮生成的独立审查证据，**不属于** production implementation diff）
- **约束**：只读复审，未修改/提交任何文件，未运行 provider/probe/real-case
- **方法**：逐文件阅读 git diff 与生产调用链，独立复跑 focused module / full reactor / `git diff --check`

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 | 0 |
| P1 | 0 |
| P2 | 2（均为非阻塞防御性观察） |
| **P2-1** | **CLOSED** |
| P2-2 | 仍为非阻塞（已核实三个 workflow 均显式提供 `gate_purpose`） |
| 选择性提交条件 | 保持（delta 自包含于 3 个文件） |
| fresh-workstream Gate 条件 | 具备 |

独立复跑结果（与用户声明的基线一致）：

- focused（`dual-ledger-file` 模块全量）：**149/0/0/0**，其中 `WorkflowFileLedgerTest` **38/0/0/0**（含新增 `revisionBudgetSeparatesDifferentOwnersOfTheSameRepairTarget`）
- full reactor `mvn clean test`：**1218/0/0/0**
- `git diff --check`：**clean**

---

## 1. Findings（P0 / P1 / P2，含真实 file:line）

### P0 — 无

### P1 — 无

### P2-A — `revisionCount` 的 trigger-node 读取为非守卫 `readValue`，与 `readIterationOrNull` 的静默跳过不对称（fail-closed，非阻塞）

- **位置**：`dual-ledger-file/src/main/java/dev/dual/ledger/file/RevisionTracker.java:167-170`
- **现象**：`revisionCount` 在循环内对 `paths.nodeJson(iteration.triggerNodeInstanceId())` 执行**未捕获**的 `objectMapper.readValue(...)`；而同一方法内的迭代文件读取 `readIterationOrNull`（`RevisionTracker.java:179-185`）对畸形迭代文件是**静默跳过**（返回 `null`）。二者不对称：畸形迭代 → 被静默漏计（预算被低估，可能多放行一次 revision）；缺失/损坏的 trigger node 文档 → 抛 `IOException` 并向上传播至 `completeAndRoute`（fail-closed，整个路由中止）。
- **为何非阻塞**：(1) node 文档永不被删除——grep 全仓库确认无 `Files.delete`/`deleteIfExists` 作用于 node 文档，`RoutingEngine.completeAndRoute` 第 219 行只从内存 `activeNodeInstanceIds` 移除 node id，磁盘 `nodeJson(...)` 持久存在；(2) 唯一生产调用点 `nextRevisionIteration`（`RoutingEngine.java:144`）始终传入真实 node 实例，`triggerNodeInstanceId` 在生产路径恒非空；(3) fail-closed 与项目「确定性控制失败必须显式停下」的哲学一致，是可辩护的防御性行为。
- **建议**：可后续（非阻塞）统一为对缺失 trigger node 文档显式跳过并记录，消除与 `readIterationOrNull` 的语义不对称；当前无需在本 delta 内处理。

### P2-B — `WorkflowIterationDocument` 紧凑构造器未对 `triggerNodeInstanceId` 施加 `requireText`（模型边界不校验，非阻塞）

- **位置**：`dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowIterationDocument.java:18-36`
- **现象**：紧凑构造器对 `iterationId`/`stageId`/`status`/`triggerAction`/`targetNodeDefinitionId` 施加 `requireText`（第 22/26/27/28/29 行），但对 `triggerNodeInstanceId` 仅做 `null → ""` 归一化（第 34 行），**不要求非空**。因此「每个 revision iteration 都携带可解析的 trigger 引用」这一不变量只在唯一调用点 `nextRevisionIteration`（`RoutingEngine.java:144`）被隐式保证，而非在模型边界被强制。
- **为何非阻塞**：(1) 生产上该不变量成立——`nextRevisionIteration` 的 `triggerNode` 参数来自 `loadNode` 加载的真实 node，其 `nodeInstanceId` 非空且 `nodeDefinitionId` 非空（`NodeInstanceDocument.java:35` 已 `requireText`）；(2) `initial()` 工厂（`WorkflowIterationDocument.java:38-52`）产出的 `triggerAction="workflow_started"` 迭代在 `revisionCount` 第 163 行被**先于** trigger-node 读取过滤掉，不会命中 `paths.nodeJson("")`；(3) 即便理论出现空引用，结合 P2-A 表现为 fail-closed 的 `IOException`，而非静默误计。
- **建议**：可后续（非阻塞）在构造器对 `triggerNodeInstanceId` 增加「空串仅在 `triggerAction="workflow_started"` 时允许」的边界校验，使不变量在模型层落地。

> 注：上一轮审查摘要中「`requireText` 强制 `triggerNodeInstanceId` 非空」的表述经核实**不准确**——实际为 `null → ""` 归一化，非 `requireText`。本报告以代码为准。

---

## 2. P2-1 判定：CLOSED

P2-1 根因是「多个 repair loop 都指向 `context.implement_claude`，按 target 单独计数导致 self-test repair 消耗 acceptance / human repair 的预算」。修复 delta 将预算所有权从 **target** 迁移到 **(trigger, target)** 二元组：

1. `RevisionTracker.revisionCount` 签名由 `(paths, targetNodeDefinitionId)` 改为 `(paths, triggerNodeDefinitionId, targetNodeDefinitionId)`（`RevisionTracker.java:148-152`），先按 `targetNodeDefinitionId` 精确过滤（第 162 行），再逐迭代把 `triggerNodeInstanceId` 解析到 `NodeInstanceDocument.nodeDefinitionId` 与 trigger 参数匹配（第 166-174 行）。
2. `RoutingEngine.completeAndRoute` 以**源 node**（触发 loop 边的 decision/gate）作为 trigger 传入：`maxRevisions` 读 `definition.node(node.nodeDefinitionId())`（第 134 行），`revisionCount` 传 `node.nodeDefinitionId()` 与 `nextDefinition.id()`（第 135-139 行）。`node` 是 SOURCE，`nextDefinition` 是 target。
3. 新增测试 `revisionBudgetSeparatesDifferentOwnersOfTheSameRepairTarget`（`WorkflowFileLedgerTest.java:598-620`）证明：同一 target `context.implement_claude`，不同 owner `implement.self_test_decision` 与 `gate.human_acceptance` 各自独立计数为 1，互不扣减。

**判定理由**：预算所有权正确迁移到 trigger node，且 `max_revisions` 与 `revisionCount` 的 owner 来源一致（均取源 node 的 `nodeDefinitionId`），消除了 self-test 与 acceptance/human 修复预算之间的污染。`revisionCount` 全仓库仅此一处控制调用（`RoutingEngine.java:135`），`MetricsProjectionWriter.java:62` 的 `revisionCount` 为纯展示指标、不参与控制，无 stage-id / target-only / triggerAction 残留控制路径。**CLOSED**。

---

## 3. End-to-end invariant（7 维）

| 维度 | 结论 | 锚点 |
|---|---|---|
| **Declared by** | 不变量由 `revisionCount(paths, triggerNodeDefinitionId, targetNodeDefinitionId)` 签名与测试名 `revisionBudgetSeparatesDifferentOwnersOfTheSameRepairTarget` 声明：预算归 (trigger, target) 二元组所有 | `RevisionTracker.java:148-152`；`WorkflowFileLedgerTest.java:598` |
| **Proven by** | 新测试写入同一 target 两个不同 trigger 的 revision，断言各自 `revisionCount == 1`；另一测试断言 `acceptance.decision` 对 self-test 触发的 repair 计 0 | `WorkflowFileLedgerTest.java:616-619`、`593-594` |
| **Consumed by** | `RoutingEngine.completeAndRoute` 读源 node 的 `maxRevisions`（第 134 行）与 `revisionCount(node.nodeDefinitionId(), nextDefinition.id())`（第 135-139 行）并比较（第 140-143 行）；`node`=SOURCE，`nextDefinition`=target | `RoutingEngine.java:133-143` |
| **Enforced before** | 限额校验（第 140-143 行）在 `nextRevisionIteration` 写入新迭代（第 144 行）与 successor node 创建（第 160 行）**之前**执行 | `RoutingEngine.java:140-144` |
| **Failure route** | 超限时 `blockOnRevisionLimit` 将源 node 置 `BLOCKED`、写 `recovery.review` gate（`retry`/`abort`）、置 `WAITING_FOR_RECOVERY`、写 `revision_limit_reached` 事件——显式停下，不静默循环 | `RevisionTracker.java:50-131` |
| **Cannot-bypass test** | `selfTestDecisionRoutesCompleteIncompleteAndBlockedWithoutBypass` 通过真实 `completeAndRoute` 连续两次 `SELF_TEST_INCOMPLETE`，断言第二次触发 `WAITING_FOR_RECOVERY` + `revision_limit_reached`，证明限额经真实路由引擎端到端强制 | `WorkflowFileLedgerTest.java:552-554` |
| **Residual legacy path** | `workflow_started` 过滤（第 163 行）在 trigger-node 读取前排除初始迭代；`MetricsProjectionWriter` 的 `revisionCount` 纯展示不控制；`revisionCount` 仅 `RoutingEngine.java:135` 一处控制调用 | `RevisionTracker.java:163`；`MetricsProjectionWriter.java:62` |

---

## 4. 选择性提交条件：保持

P2-1 delta 严格自包含于 **3 个文件**：

- `dual-ledger-file/src/main/java/dev/dual/ledger/file/RevisionTracker.java`
- `dual-ledger-file/src/main/java/dev/dual/ledger/file/RoutingEngine.java`
- `dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowFileLedgerTest.java`

不触碰三个 workflow JSON、`ContractRegistry`、其余 runtime/CLI 测试文件。`dual-ledger-file` 模块 149 项测试全绿，delta 可独立编译通过、独立可审查、独立可提交为聚焦的「budget-ownership relocation」提交，无需卷入更广的 self-test-routing checkpoint 变更。**选择性提交条件保持。**

---

## 5. Fresh-workstream Gate 条件：具备

作为下一 workstream 的 gate 基线，本 delta 满足以下条件：

1. P2-1（本轮唯一需要判定关闭的阻塞项）**已 CLOSED**，预算所有权模型现为正确、一致的 (trigger, target) 语义。
2. 端到端不变量 7 维全部闭合（见第 3 节），且由真实路由引擎的 bypass 测试背书。
3. full reactor **1218/0/0/0**、focused **149/0/0/0**、`git diff --check` clean——无任何未解释的失败或差异。
4. 残留项仅为两个非阻塞防御性观察（P2-A / P2-B），可显式结转至下一 workstream 或机会性修复，不构成 gate 阻塞。

**判定：具备提交后制定 fresh-workstream Gate 的条件。**

---

## 6. 独立复跑结果

| 命令 | 结果 |
|---|---|
| `mvn -q -pl dual-ledger-file -am test`（focused 模块） | **149/0/0/0**（`WorkflowFileLedgerTest` 38/0/0/0） |
| `mvn -q clean test`（full reactor） | **1218/0/0/0** |
| `git diff --check` | clean（exit 0） |

模块分解：dual-adapter-acpx 314、dual-adapter-git 5、dual-cli 75、dual-core 105、dual-flow-liteflow 12、dual-ledger-file 149、dual-runtime 502、dual-server 56。

---

## 7. P2-2 复核判断：仍为非阻塞

上一轮 P2-2 指出 `HumanGateSemantics.purposeForDefinitionId()`（`HumanGateSemantics.java:9-18`）未包含 `gate.human_implementation_repair`，其 fallback 会返回 `"general"`。本轮核实处置是否仍非阻塞：

- `purpose(config, nodeDefinitionId)`（`HumanGateSemantics.java:25-31`）**先读 `config.get("gate_purpose")`**，仅在缺省/空白时才回退到 `purposeForDefinitionId`。
- 三个 bundled workflow **均显式**为 `gate.human_implementation_repair` 提供 `gate_purpose: "implementation_repair"`：
  - `agent-lifecycle.full.json`：node id 第 234 行，`gate_purpose` 第 240 行
  - `agent-lifecycle.contract.json`：node id 第 299 行，`gate_purpose` 第 307 行
  - `agent-lifecycle.terminal-delivery.json`：node id 第 299 行，`gate_purpose` 第 307 行

因此 fallback 路径（`purposeForDefinitionId` 返回 `"general"`）在全部三个生产 workflow 中**永远不会被触及**。

**判定：仍为非阻塞。** 残留风险仅是假设性的——未来/自定义 workflow 若定义 `gate.human_implementation_repair` 而漏写 `gate_purpose`，会静默回退为 `"general"`。这属潜在而非现存缺陷，与上一轮 P2 分级一致，可结转后续机会性补齐 `purposeForDefinitionId` 的 `gate.human_implementation_repair` case。

---

*本报告为只读审查证据；未修改、未提交任何 production 文件。*
