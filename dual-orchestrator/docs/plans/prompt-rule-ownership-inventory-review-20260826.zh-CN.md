# Prompt / Rule Ownership Inventory 独立架构评审

日期：2026-08-27
评审基线：`f1663cbd`
评审对象：`prompt-rule-ownership-inventory-20260826.zh-CN.md` + active plan 增补
性质：只读代码级评审；production/test 零修改

## 1. Findings

**P0：无。P1：无。**

### P2-1 sibling sweep 遗漏 `WorkstreamPaths.currentHumanGateJson()`

该 path helper 的全部 production 消费者是 `FileLedgerStore` 的三个 dead 方法：
`approveDesign`（`FileLedgerStore.java:354-355`）、`requestDesignRevision`（`:377-381`）、
`completeCodexDesignReviewRun` 的 `openHumanDesignGate` helper（`:438`）。`initializeWorkstream` 不写
human gate；`loadStatus`（`:76-86`）不读它。删除 11 个方法后该 helper 零消费者，应并入同一删除
checkpoint（或在清单中显式声明保留理由）。这是边界唯一的不完整点。

### P2-2（轻微）R-P1 的 profile 级可删子集未标注

intake/correction（denyAll）节点的 `.agent` 写禁令句子今天即被平台机械覆盖，理论上可按 profile
先行删除；但它与同一文本块中未被强制的 stdout-protocol 句子绑定，清单选择全局保留是保守但可辩护的。
R-P1 判定已写明"未来按 profile 删除已机械覆盖的句子"，无需修改；仅记录该子集存在。

## 2. Boundary Review：**PASS**

## 3. 十五个必答问题核实

1. **428 行精确复算 ✓** — 逐文件 `wc -l`：42/42/43/46/51/43/44/43/34/28 = 416（10 prompts）+ 12
   （template）= 428，与清单表格逐项一致。
2. **Parser 仅为 rejection owner ✓** — `AgentResultEnvelopeParser`（duplicate/unknown/trailing/coercion/EOF）
   只拒绝错误结果，不向模型声明应产出什么；ACP 无 native structured-output（assistant-message-only 采集，
   `AcpxAgentResultAcquirer.java:290`），producer interface 只能由 prompt 承担。清单的 D3 判定成立。
3. **`--allowed-tools` 为免确认集合 ✓** — `AcpxPermissionProfile.java:12-13` 注释与实现一致；
   observe/review 的 ACP 权限 `defaultAction: deny`（`:121`）。清单 §2.2 的"not a tool-visibility
   whitelist"准确。
4. **无完整 `.agent` 写保护 ✓** — implement profile approveAll（`:85-87`）；
   `ChangeBoundaryAuthoritySnapshot` 是 `AcpxAgentTaskHandler.java:349-360` 的 private record，
   dispatch 前 capture（`:229`）仅指纹选定 facts——清单"ENFORCED for the selected facts only /
   整树 NOT MODELED"精确。
5. **R-P1~R-P7 无错误分类，无现成可删规则组 ✓** — 与本评审早期独立抽查一致："keep scoped"类句子
   投影 TaskContract/change-scope 的 L2 边界；`.agent` 文本块绑定未强制 stdout 协议；review discipline
   是 advisory 目标说明而非 validator 可替代物。R-P7"未找到整组 PUBLIC_KNOWLEDGE"与事实相符。
6. **D3 无免费删除段 ✓** — prose 与 schema example 的信息分工（何时/为何用每个 enum、revision base
   identity）未被 example 完整替代；字段级 diff + counterfactual 的前置要求正确。
7. **11 个方法 production-unreachable ✓** — 全仓 Java 扫描：11 个方法名在 `FileLedgerStore.java` 与
   `FileLedgerStoreTest.java` 之外**零命中**；scripts 零命中；docs 命中仅为 retrospective/active
   plan/inventory 自身的文字记录，非代码入口。无反射/序列化面（方法名不经序列化）。
8. **五个类型确无正式 consumer ✓** — `PreparedRunView`/`RunDocument`/`ArtifactManifest`/
   `CodexReviewControl`/`HumanGateDocument` 的全部引用 = `FileLedgerStore` + 各自专属测试；
   与现代类（`EvidenceManifest`、workflow 侧 `WorkflowIterationDocument`）无名称/类型冲突。
9. **factory/mutator 删留边界正确 ✓** — 保留：`CheckpointDocument.initial`（`:23`）、
   `StageDocument.initialDesign`（`:18`）、`IterationDocument.initialDesign`（`:17`）与基础
   workstream construction（均被 `initializeWorkstream:52-66` 消费）；删除：
   `CheckpointDocument.claudeDesignPrepared/codexDesignReview*`（`:43-225`，消费者=dead methods +
   CheckpointDocumentTest legacy cases）、`IterationDocument.designRevision`（`:32`，唯一消费者
   `FileLedgerStore:156` dead）、`StageDocument/WorkstreamDocument/IterationDocument` 的 transition
   mutators（`withStatus` 消费者仅 `:356-358/:382` dead、`withStatusAndCurrentIteration` 仅 `:168` dead）。
   record shape 全部保留。
10. **sibling sweep 基本完整，缺一处**（P2-1 `currentHumanGateJson`）；其余已覆盖：store 内 private
    prompt builders/`openHumanDesignGate`、`FileLedgerStoreTest` legacy cases、
    `CodexReviewControlTest/RegressionTest`、`CheckpointDocumentTest` legacy cases；
    `WorkstreamStatusView`/`TimelineEvent`/`initializeWorkstream/loadStatus/appendTimeline` 正确保留。
11. **删除后 human action 唯一 owner ✓** — `RoutingEngine.resolveOpenGate`（经
    `WorkflowFileLedger.resolveOpenGate:377-379`）是唯一 transition writer；删除的 11 个方法是仅有的
    旧 writer；无 fallback 路径。
12. **scope 最小且完整（除 P2-1）** — 不宽：initialize/status/timeline 与四个 document 的 record shape
    均有真实 production 消费（`StartCommand:102`、`StatusCommand`、`WorkstreamQueryService`、
    `OperatorWorkspaceRegistry`）；不窄：state machine 的全部 writer/DTO/control/gate 类型覆盖。
13. **PASS/FAIL、回滚、focused 范围足够 ✓** — §6.2 的零命中检查 + 初始化/状态/gate focused + reactor +
    "diff 仅删除"约束；回滚条款明确不为删除加 adapter。建议 focused 集合显式包含
    `FileLedgerStoreTest`（保留用例）、StartCommand/StatusCommand 初始化状态测试与
    `WorkflowFileLedgerTest`/`WorkflowGateResolutionTest`。
14. **frozen workstream 兼容 ✓** — `LedgerMigrator` 对受影响类型零引用；`loadStatus` 不读
    `currentHumanGate.json`；record shape 保留使旧文档反序列化不变；删除的是 factory/mutator
    （代码），不是读路径。历史 design-loop workstream 的 status 读取不受影响。
15. **纯删除式 ✓** — 保留方法（initialize/loadStatus/appendTimeline）不引用任何 dead 类型/factory；
    无需 replacement/adapter（`initializeWorkstream:44-74` 只用保留的 initial* factories）。

## 4. 结论

- **D2/D3 暂不删除：成立。** 没有满足直接删除条件的规则组；validator 强制是 rejection 边界，
  不替代 producer interface；`.agent` 整树写保护 NOT MODELED。
- **D1 删除边界：完整（补 P2-1 一项后）。** 11 方法 + 5 类型 + legacy factory/mutator + 对应测试
  构成最小完整 family；只删 `approveDesign` 会遗留同 state machine 的 revise/review writer。
- **Implementation READY：YES（把 `WorkstreamPaths.currentHumanGateJson()` 并入 scope 后）。**

## 5. 建议的精确 production/test 文件清单

Production（删除）：`FileLedgerStore.java`（11 methods + private helpers/prompt strings/imports）、
`PreparedRunView.java`、`RunDocument.java`、`ArtifactManifest.java`、`CodexReviewControl.java`、
`HumanGateDocument.java`（整文件）、`CheckpointDocument.java`（legacy factories）、
`StageDocument.java`/`IterationDocument.java`/`WorkstreamDocument.java`（legacy mutators）、
`WorkstreamPaths.java`（`currentHumanGateJson()`，随 P2-1）。

Tests（删 legacy-only 用例）：`FileLedgerStoreTest.java`、`CodexReviewControlTest.java`、
`CodexReviewControlRegressionTest.java`、`CheckpointDocumentTest.java`（legacy factory cases）。

不修改：10 prompts、template、`AgentResultPromptContract`、parser/validator/registry、
`WorkflowFileLedger`/`RoutingEngine`、CLI/server entry points、H4 测试。

## 6. 提交条件与授权

- 工作树确为两份文档（本评审文件为授权产物）✓。
- **下一步授权**：按 §6 执行单一纯删除 checkpoint（含 P2-1 项）→ focused + full reactor +
  `git diff --check` → 独立 diff review → 选择性提交。不加 adapter/fallback/module；D1 完成前
  不做 D2/D3 prompt 删除；不调用 provider/real-case。
