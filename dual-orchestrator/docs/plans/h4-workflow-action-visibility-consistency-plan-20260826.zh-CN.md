# H4 Workflow Action Visibility Consistency Implementation Plan

日期：2026-08-26
状态：INDEPENDENT DIFF REVIEW PASS / SELECTIVE COMMIT AUTHORIZED

## 1. 唯一目标

在 workflow definition 被接受、冻结或用于 gate mutation 前，证明每条 human gate outgoing edge 都能由该 gate
对 operator 公布的 effective `allowed_actions` 触发。这样 backend 不再接受 review packet 未展示的 hidden edge
action；显式列入 `allowed_actions` 但没有 outgoing edge 的 terminal command 继续保持合法。

本 checkpoint 只收敛 Definition action visibility，不修改 UI、CLI/API command surface、workflow graph、gate schema、
revision budget、autorun policy 或 provider 行为，也不调用 provider。

主线分类：`P0 correctness residual`。它修复的不是某个按钮，而是 workflow edge 与 gate-visible action 两个
Definition 声明面之间的 SoT 漂移；删除的是 ledger/handler/private mapper 三条重复解释路径。证明边界的是
hidden-edge definition rejection、directional canonical parity 与 terminal no-edge tests，不以 prompt、UI 文案或
synthetic provider 样本代替。H4 完成后仍未完成的主线是 R1 multi-file mutation crash consistency 与后续 subtraction，
不得在收口中一并声称完成。

## 2. 边界与 owner

| 事实 | 唯一 owner | 消费方 |
|---|---|---|
| gate 的原始 action/edge 声明 | `WorkflowDefinition` 中 gate config + outgoing edges | definition validator、ledger routing、review packet |
| 缺省 `allowed_actions` | 新的 core `HumanGateActionSemantics` | ledger、`HumanReviewGateHandler`、definition validator |
| `approve -> approved` 兼容语义 | 同一 `HumanGateActionSemantics` | definition validator、`RoutingEngine.resolveOpenGate` |
| 当前 open gate 与已公布 actions | durable `GateDocument` + review packet | CLI/UI/API/operator read paths |
| canonical human decision 与 successor | `RoutingEngine.resolveOpenGate` | node/gate/event/workflow ledger |

Definition / Instance / Execution：action visibility 是 Definition 事实；open `GateDocument` 是 one-workstream instance
projection；一次 CLI/API 调用是 command execution。projection 不得发明 action，command producer 不得选择 successor。

不新增第二 action mapper。新 semantic owner 是把 `RoutingEngine.canonicalGateAction()`、
`LedgerUtils.allowedActions()` 与 `HumanReviewGateHandler.allowedActions()` 的现有规则集中到 core，三处旧实现删除。

## 3. 已确认的代码事实

1. `RoutingEngine.resolveOpenGate()` 当前以 `hasEdge || actionAllowed` 接受 command：edge action 即使未出现在
   `allowed_actions` 也可执行；allowed-only action 即使没有 edge 也可完成 workflow。
2. `approve -> approved` 只在当前 gate 没有 `approve` edge、但存在 `approved` edge 时发生，是方向性兼容，不是
   `approve/approved` 无条件同义。
3. `LedgerUtils.allowedActions()` 对缺失、空 list 或非-list 使用 `approve/revise` fallback；过滤 blank。
   `HumanReviewGateHandler` 另有一份近似 fallback，空 list 与 blank 的行为不同，形成 projection drift。
4. `GateDocument` 打开时使用 ledger fallback；review packet 使用 handler fallback，因此 definition 特殊值可让
   两个 operator-facing projection 不一致。
5. `WorkflowDefinition` 构造器是 bundled JSON、custom file、durable frozen definition reload 和 programmatic
   definitions 的共同 validation boundary。
6. 三个 bundled workflow 当前所有 routed gate edge 都可由 allowed action 到达。`approved` edge 由可见的
   `approve` 经既有 canonicalization 触发。

## 4. 为什么不能比较集合完全相等

三个现有 workflow 有三类合法 no-edge terminal command：

| gate | visible no-edge action | 语义 |
|---|---|---|
| `gate.contract_review` | `reject` | 人工拒绝合同并终止当前 workflow |
| terminal-delivery `gate.human_acceptance` | `approve` | 接受最终交付并完成 workflow |
| full/contract `gate.human_next_stage_approval` | `complete` | 显式结束当前 workstream |

这些 action 已由 ledger tests 与真实 CLI 路径证明，不能因没有 edge 被误判为 drift。`terminal_gate` 也不能作为
唯一判定：`gate.contract_review` 明确不是 terminal gate，但 `reject` 是合法 terminal command。

因此唯一 consistency rule 是：

```text
for every outgoing edge E of gate G:
  exists visible action A in effective allowed_actions(G)
  such that canonicalize(G, A) == E.on
```

反向不成立：visible action 没有 matching edge 时沿用当前 terminal completion 语义。

## 5. 原子设计

### 5.1 集中 effective actions 与 canonicalization

在 `dual-core` 增加 `HumanGateActionSemantics`：

1. `allowedActions(NodeDefinition)` 忠实保留当前 ledger compatibility：仅非空 list 作为显式 actions，元素
   `String.valueOf` 后过滤 blank；缺失、空 list 或非-list 回落 `approve/revise`。
2. `canonicalAction(List<EdgeDefinition> outgoingEdges, requestedAction)` 搬迁现有方向性逻辑：只有 requested
   `approve`、无 `approve` edge 且有 `approved` edge时返回 `approved`；其他 action 原样返回。definition validator
   与 runtime 必须调用这一个 primitive，不得在 validator 内复制等价判断。
3. `validateVisibility(nodes, edges)` 只检查 `NodeKind.GATE` 的 outgoing edges；每条 edge 必须存在一个 effective
   visible action，经同一 canonicalization 后等于 edge outcome，否则抛出包含 gate id、hidden edge action 与
   effective actions 的 `IllegalArgumentException`。
4. conditional edges、同 action 多 edge 均按 definition 声明检查；一个 visible action 可覆盖同 outcome 的多个条件 edge。
5. 不判断 allowed-only action是否“应该 terminal”，不读取 node id/gate purpose/`terminal_gate` 猜业务语义。

### 5.2 在共同 Definition boundary 强制

`WorkflowDefinition` 构造器完成 node/edge 引用校验后调用 `HumanGateActionSemantics.validateVisibility(...)`。
这使以下入口共享同一 guard：

- bundled full/contract/terminal-delivery JSON；
- `WorkflowDefinitionLoader.load(customPath)`；
- `WorkflowFileLedger.loadDefinition()` 对 frozen definition 的恢复读取；
- tests 和其他 programmatic definition builders。

invalid definition 在 workstream 创建、恢复 routing 或 gate side effect 前失败。不得只在 CLI `workflow validate`
增加检查，因为 start/runtime/server 可绕过该 projection command。

### 5.3 删除旧解释路径

1. `RoutingEngine.resolveOpenGate()` 改用 core semantic owner 的 `canonicalAction` 与 `allowedActions`；删除 private
   `canonicalGateAction()`。
2. gate 创建时使用同一 `allowedActions` 写 `GateDocument`；删除 `LedgerUtils.allowedActions()`。
3. `HumanReviewGateHandler` 使用同一 `allowedActions` 写 waiting result、JSON/Markdown review packet 与 operator
   guidance；删除 handler-local fallback。
4. `RoutingEngine` 仍保留 mutation-time `hasEdge || actionAllowed` guard。Definition validation 阻止 hidden edge；
   runtime guard 继续拒绝 unsupported command，并保留 allowed-only terminal command。

### 5.4 兼容性裁决

1. missing/empty/non-list `allowed_actions` 的 ledger fallback 保留，不在 H4 删除 custom/legacy compatibility。
2. 由于 packet 改为消费同一 fallback，显式 empty list 将显示并接受 `approve/revise`，与当前 backend truth 对齐；
   这是 projection drift 修复，不是新增 backend action。
3. 不迁移或重写已 resolved historical `GateDocument`。若旧 frozen definition 本身含 hidden edge，重新构造
   `WorkflowDefinition` 会 fail-closed；因此依赖 definition 重载的历史 query 也可能失败。该行为作为 invalid-definition
   compatibility break 明确接受，不伪装成“所有历史读取不受影响”；原始 gate/node/event 文件仍保留为审计 evidence。
4. 三个 bundled workflow JSON 无需修改；若 deterministic test 发现某个 edge hidden，先修 definition 声明，不能在
   validator 中加 node-id exception。

### 5.5 明确不修改 UI/CLI/API

两个 UI 当前仍以 WAITING 状态和硬编码 approve/revise 按钮提供基础 UX，并未动态呈现 packet 的全部 actions；CLI
shortcut 也只覆盖部分 action。H4 不声称解决该 projection completeness：

- 通用 `review decide --action ...` 仍可提交 packet 中的任意 command；
- UI/API/CLI 继续把 command 交给 ledger，不自行 canonicalize 或选 edge；
- 不新增按钮、endpoint、action mapper 或共享前端组件；
- H4 完成后只能声明 review packet/GateDocument 与 backend edge visibility 一致，不能声明 UI 展示全部合法 action。

## 6. 修改文件

Production：

1. `dual-core/.../workflow/HumanGateActionSemantics.java`（新增唯一 semantic owner）
2. `dual-core/.../workflow/WorkflowDefinition.java`（共同 definition guard）
3. `dual-ledger-file/.../RoutingEngine.java`（消费 owner，删除 private mapper）
4. `dual-ledger-file/.../LedgerUtils.java`（删除 fallback owner）
5. `dual-runtime/.../handler/HumanReviewGateHandler.java`（消费 owner，删除 packet fallback）

Direct tests：

1. `dual-core/.../workflow/HumanGateActionSemanticsTest.java`
2. `dual-core/.../workflow/WorkflowDefinitionTest.java`
3. `dual-runtime/.../lifecycle/WorkflowDefinitionLoaderTest.java`
4. `dual-runtime/.../lifecycle/LifecycleWorkflowDefinitionFactoryTest.java`
5. `dual-runtime/.../lifecycle/ContractWorkflowDefinitionTest.java`
6. `dual-runtime/.../lifecycle/TerminalDeliveryWorkflowDefinitionTest.java`
7. `dual-runtime/.../lifecycle/FullWorkflowDefinitionTest.java`
8. `dual-runtime/.../handler/BuiltinHandlerTest.java`
9. `dual-ledger-file/.../WorkflowFileLedgerTest.java`
10. `dual-ledger-file/.../WorkflowGateResolutionTest.java`
11. `dual-runtime/.../handler/WorkflowIntegrationTest.java`（同一运行中比较 GateDocument 与 packet actions）

不修改 workflow JSON、`GateDocument` schema、server/UI/CLI、autorun、revision tracking、R1 mutation write sequence、
provider 或 Agent contracts。

## 7. Cannot-bypass tests

T1. gate 有 `approved` edge、effective actions 含 `approve`：definition PASS，且 ledger 仍 canonicalize 为 `approved`。

T2. gate 有 `approve` edge、effective actions只有 `approved`：definition FAIL，证明 compatibility 是方向性的，不能用
字符串集合近似。

T3. gate 有 `revise`/其他 edge，但 effective actions 未暴露该 edge：`WorkflowDefinition` 构造 FAIL，异常包含 gate id
与 hidden action；不得等到 mutation 时才发现。

T4. custom workflow file 携带 hidden edge：`WorkflowDefinitionLoader.load()` 返回 invalid definition；同一 malformed
frozen definition 经 `WorkflowFileLedger.loadDefinition()` 也失败，不能通过恢复路径绕过。

T5. missing、empty、non-list `allowed_actions` 均使用同一 `approve/revise` fallback；`GateDocument`、handler waiting
result、JSON packet 和 Markdown packet 展示一致。

T6. bundled full/contract/terminal-delivery 全部通过统一 guard；逐 gate 断言每条 outgoing edge 都由一个 effective
visible action canonical-reachable。

T7. contract `reject`、terminal-delivery acceptance `approve`、next-stage `complete` 三类 no-edge actions 不被
definition guard 拒绝，并沿既有 ledger 路径完成 workflow。

T8. unsupported action 仍在 gate/node/event/workflow 写入前拒绝；current gate 与 historical gate 语义不变。

T9. review packet 的 actions 与新建 `GateDocument.allowedActions` 对同一 definition 完全一致；operator guidance 不得
重新加入隐藏 action。

T10. CLI/server/autorun 现有测试保持绿，证明 command producer 未获得 canonicalization 或 successor authority。

## 8. 实施顺序

1. 先写 core semantic owner 与 T1-T3/T5；
2. 在 `WorkflowDefinition` 接入 guard，修复受影响 programmatic fixtures；
3. 迁移 ledger canonicalization/fallback，保留 runtime union guard与 terminal semantics；
4. 迁移 `HumanReviewGateHandler` fallback；
5. 补 custom/frozen loader、bundled sibling 与三类 terminal tests；
6. focused core + ledger + runtime tests；
7. full Maven reactor + `git diff --check`；
8. 独立代码级 diff review PASS 后才选择性提交。

若实施需要新增 workflow 字段、terminal action registry、node-id exception、UI mapper 或 generic gate engine，停止编码并
回到架构评审。

## 9. End-to-end invariant

| 维度 | 证明 |
|---|---|
| Declared by | workflow gate config 的 effective `allowed_actions` 与 outgoing edge outcomes |
| Proven by | core semantic owner 用 runtime 同款 directional canonicalization 验证每条 edge 可见且可达 |
| Consumed by | `WorkflowDefinition` guard、GateDocument、review packet、RoutingEngine 共用同一 owner |
| Enforced before | custom/bundled/frozen definition 在 workstream creation、reload 或 gate mutation 前构造失败 |
| Failure route | hidden edge -> invalid definition；unsupported command -> runtime IOException；visible no-edge -> terminal completion |
| Cannot-bypass | T1-T10，尤其 directional canonical、custom/frozen load、三类 no-edge terminal actions |
| Residual raw/legacy | missing/empty/non-list fallback 保留；UI 按钮仍不完整；invalid historical definition 重载会 fail-closed；R1 多文件原子性不属 H4 |

## 10. 成熟度与退出条件

deterministic checkpoint 与独立 diff review PASS 后只能声明：

```text
action execution ownership: ENFORCED; existing CLI samples partially PROVEN
workflow edge -> packet/GateDocument visibility: ENFORCED
all UI actions dynamically projected: NOT MODELED
all entry/action combinations: NOT PROVEN and no provider sample required
```

H4 不需要 real-case 或 provider 调用。完成后进入 R1 multi-file mutation bounded assessment，不继续扩张 human gate UI。

## 11. Implementation Result

实现严格保持计划边界：新增一个 core `HumanGateActionSemantics` owner，`WorkflowDefinition` 在共同构造边界
拒绝 hidden gate edge；ledger gate creation、mutation-time canonicalization 与 human review packet 全部消费同一
owner，旧 `LedgerUtils`、`RoutingEngine` private mapper 和 handler-local fallback 已删除。未修改 workflow JSON、
UI、CLI/API、schema、revision budget、autorun 或 provider。

cannot-bypass 覆盖包括 directional `approve -> approved`、custom 与 frozen definition 拒绝、fallback parity、三个
bundled variant 的逐 gate canonical reachability、no-edge terminal command 既有路径、同一 routed gate 的
`GateDocument`/waiting result/JSON packet actions 对账，以及既有 unsupported-action 写入前拒绝测试。

验证结果：focused H4 slice `167/0/0/0`；非沙箱 full Maven reactor 9/9 modules、`1280/0/0/0`；
`git diff --check` clean。沙箱内第一次 full reactor 的两项 error 均来自 macOS 拒绝真实
`ProcessHandle.descendants()` 系统读取，非业务 failure；同一 reactor 在非沙箱环境全绿。

独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=0）`。评审独立复跑 focused 超集
`178/0/0/0`、full Maven reactor 9/9 modules `1280/0/0/0`，并确认 13 个变更文件无第二 action mapper、
fallback 或构造旁路，满足单一原子 checkpoint 的选择性提交条件。成熟度维持 action visibility
`ENFORCED`、existing CLI samples `partially PROVEN`、all UI actions dynamically projected `NOT MODELED`、
all entry/action combinations `NOT PROVEN`。本 checkpoint 不需要 provider/real-case。
