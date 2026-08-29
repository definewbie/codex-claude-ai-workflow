# D2-B Delivery Projection Convergence Implementation Plan

状态：`IMPLEMENTED / DETERMINISTIC VERIFICATION PASSED / INDEPENDENT DIFF REVIEW PASSED / READY FOR SELECTIVE COMMIT`

基线：Dual `83d840ed`（D2-A clean checkpoint）

## 1. 目标

D1 已让 `DeliveryApplicationService.View` 成为 accepted candidate、merge plan 与 target side effect 的唯一
delivery read model。D2-B 不再设计 delivery lifecycle，而是删除仍存在于 CLI、API、UI、attention 与 smoke test
中的旧解释路径，使 operator surface 只投影两个既有 owner：

1. workflow lifecycle：`workflow/current.json`；
2. delivery lifecycle：`DeliveryApplicationService.View`。

本 checkpoint 的可验证目标是：当 legacy `delivery.json`、raw `merge-plan.json`、timeline/workstream 初始状态与
authoritative facts 冲突时，CLI、API、UI 与 next-action command exposure 都只能显示 authoritative 结果，不能回退
旧文件或自行重算 delivery 状态。

## 2. 已确认的 production 事实

### 2.1 Delivery authority 已存在

`dual-runtime/.../DeliveryApplicationService.java` 已完成：

- 从 D1 accepted-candidate resolver 读取人工验收绑定的 immutable bytes；
- 读取并校验 `DeliveryMergePlan`；
- 返回 `View(status, reasonCode, reason, acceptanceDecision, candidateRef, candidateSha256, changedFiles, excludedFiles,
  targetRepo, commit, canMerge, canFinalize)`；
- legacy workstream 缺 candidate binding 时返回 `BLOCKED / LEGACY_NOT_BOUND`；
- workflow 尚未完成或 completion audit 尚未 ready 时返回 `BLOCKED / WORKFLOW_NOT_DELIVERABLE`；
- READY/APPLIED/COMMITTED/FAILED 的 capability 由同一 view 产生。

因此 D2-B 不新增 `DeliveryStatus`、schema、projection artifact 或通用 read-model service。

### 2.2 重复解释仍存在

1. `StatusCommand` 直接读取 legacy `delivery/delivery.json` 与 raw `merge-plan.json`。
2. `SummaryCommand` 同时展示 Delivery/Delivery Merge，并按 raw merge status 生成下一步命令；其 action-bearing
   workflow status 来自 timeline projection。
3. `WorkstreamQueryService.status/overview` 同时返回 legacy `delivery`、raw `deliveryMerge` 与 `deliveryView`。
4. `console.js` 在 `deliveryView` 缺失时回退 raw `deliveryMerge`；management UI 直接渲染 raw merge plan。
5. `WorkstreamAttentionEvaluator.completed()` 直接解释 APPLIED/FAILED，但 ledger module 无法验证 accepted candidate，
   也不识别 D1 的 READY/COMMITTED/LEGACY_NOT_BOUND 全集。
6. list API 同时暴露含糊的 `status`（workstream 创建时初值）与 `workflowStatus`（current）；两个 UI 再自行 fallback。
7. smoke test 仍以“legacy `delivery.json` 不存在”为成功条件，未证明当前 authority。

### 2.3 模块依赖允许最小复用

`dual-cli` 与 `dual-server` 均已依赖 `dual-runtime`，可以直接消费 `DeliveryApplicationService.View`；
`dual-ledger-file` 不依赖 runtime，因此不能把 D1 delivery 解释复制到 `WorkstreamAttentionEvaluator`。

## 3. 语义所有权

| 事实 | 唯一 owner | 允许的下游行为 |
|---|---|---|
| workflow 当前状态、active nodes、open gates | `WorkflowCurrentDocument` | CLI/API/UI 原样投影；timeline 仅展示历史 |
| accepted delivery bytes 与 binding | D1 candidate + resolver | 只由 `DeliveryApplicationService` 验证 |
| delivery 状态、失败、capability | `DeliveryApplicationService.View` | CLI/server 格式化，不重读 plan、不重算 capability |
| operator 文案与 shell 命令格式 | 各 presentation adapter | 只能由 `canMerge/canFinalize/status/commit` 格式化，不改变 authority |
| legacy workstream 初始状态 | `WorkstreamDocument.status` | 仅作为 `legacyInitialStatus` 展示，不能决定当前动作 |
| stable commit/PR Markdown | candidate 派生的 presentation artifact | 可展示，存在性不得推导 delivery status |

`WorkstreamAttentionEvaluator` 继续拥有 workflow/goal/recovery attention，但不再拥有 delivery lifecycle。

## 4. Production 变更

### 4.1 Runtime view 保持机器语义唯一

`DeliveryApplicationService.View` 维持现有字段与状态集合，不新增状态。D2-B 只在发现无法通过现有
`status/reasonCode/reason/canMerge/canFinalize/commit` 安全投影时停止，而不是扩 schema。

Command exposure 必须按 capability 判定：

- `canMerge=true` 才展示/生成 merge 命令；
- `canFinalize=true` 才展示/生成 finalize 命令；
- COMMITTED 只允许 inspection；
- FAILED/BLOCKED/LEGACY_NOT_BOUND 不展示 mutation command。

展示文案可以按 `status` 格式化，但不得成为 command eligibility owner。

### 4.2 CLI 收敛

#### `StatusCommand`

- 删除 `delivery/delivery.json` 与 raw merge-plan 读取；
- 调用 `DeliveryApplicationService.view(workspaceRoot, workstreamId, null)`；
- 只打印一个 `Delivery` section，包含 view status、reason、candidate、target、commit 与 capability；
- workflow current 存在时继续以其为当前状态；legacy snapshot 仅在 `--show-legacy` 或无 current 时显示。

#### `SummaryCommand`

- 删除 legacy Delivery 与 raw Delivery Merge 两套 map/summary；
- 只生成一个由 runtime view 驱动的 Delivery section；
- Recommended Next Action 的 workflow 分支改为读取 `WorkflowFileLedger.loadCurrent()`，timeline 仅保留 Recent Timeline；
- COMPLETED 后的 delivery 命令只由 `canMerge/canFinalize` 决定，FAILED/BLOCKED 只展示 view reason；
- `writeDefaultSummary()` 与交互调用必须共用同一 render 路径。

不把 server 的中文/英文文案或 shell command builder 下沉到 runtime；runtime 只拥有机器 capability。

### 4.3 Server/API 收敛

#### `DeliveryViewService`

- 继续作为 server presentation facade；
- `nextCommands` 改为优先消费 `canMerge/canFinalize`，不得仅凭 status 开放 mutation command；
- 保持 `deliveryView` 的现有 API shape，FAILED/BLOCKED 显式携带 reason。

`WORKFLOW_NOT_DELIVERABLE` 是 delivery eligibility，而不是 workflow failure。所有详细 surface 必须显示
`BLOCKED / WORKFLOW_NOT_DELIVERABLE` 及其 reason，并以“当前尚不可交付”呈现；不得隐藏 delivery section、降级为
unavailable、改写成运行失败，或从 workflow/timeline/legacy 文件另行推断 delivery 状态。该状态始终
`canMerge=false / canFinalize=false`，不产生任何 mutation command。

#### `WorkstreamQueryService`

- `status()` 删除 `delivery` 与 `deliveryMerge`，只返回 `deliveryView`；
- `overview()` 删除 raw `deliveryMerge`，只返回 `deliveryView`；
- completed workflow 的通用 `whyStopped` 仍来自 workflow attention；delivery next action 只从 `deliveryView`
  暴露，不把 delivery 状态重新塞回 ledger attention；
- `workstreamSummary()` 将 top-level `status` 改为 canonical current status，并新增：
  - `statusSource=workflow_current`（存在 current 时）；
  - `legacyInitialStatus=<workstream.status>`；
  - 无 current 的 frozen/legacy workstream 使用 `status=<legacy value>`、`statusSource=legacy_initial`；
- 删除 `workflowStatus` 双字段，避免 client 再做 fallback；
- status/overview 的 workflow block 同样携带 `statusSource`；timeline fallback 仅用于无 current 的 legacy 展示，
  必须明确标为 `timeline_projection`，不得开放 action command。

本 checkpoint 不处理 `artifactContracts` 或 `artifacts()` 的其他 mtime presentation scan；它们不决定
completion/delivery authority，登记为后续 presentation residual。

### 4.4 UI 收敛

#### `console.js`

- 删除 `overview.deliveryMerge` state 与 `deliveryView -> deliveryMerge` fallback；
- delivery 缺失时显示 unavailable，不读取 raw plan；
- list/current status 只读 API `status`，并展示 `statusSource`；不再使用 `workflowStatus || status`；
- merge/finalize 控件只读 `deliveryView.canMerge/canFinalize`。
- 在途 workstream 显示 `WORKFLOW_NOT_DELIVERABLE` 为“当前尚不可交付”，不隐藏 delivery view，也不把它渲染成
  workflow failure。

#### `mgt/app.js`

- 把 raw `deliveryMerge` renderer 改为 `deliveryView` renderer；
- 删除 raw plan 字段解释；
- list status 只读 canonical `status`，legacy source 明示，不做 fallback。
- 与 console 使用同一在途语义：显示但弱化 delivery eligibility，mutation controls 保持关闭。

### 4.5 Ledger attention subtraction

`WorkstreamAttentionEvaluator.completed()` 删除所有 `deliveryMergePlanJson()` 读取与 APPLIED/FAILED 分支。
COMPLETED attention 只说明 workflow/goal 事实；delivery 操作由 runtime view 单独投影。

同步修改：

- `WorkstreamAttentionEvaluatorTest` 删除 delivery-plan fixture 语义，增加“即使存在 conflicting raw plan，ledger
  attention 也不解释 delivery”的反证；
- `FailureSummaryWriter`、`TraceCommand`、`CliNextActionHints` 等既有 workflow/recovery consumers 不改职责。

### 4.6 Script 与文档 subtraction

- `scripts/smoke-test.sh` 删除 legacy `delivery.json` 不存在断言，改为通过 CLI/API fixture 证明
  candidate/merge-plan 经 shared view 投影；
- `docs/reference.md` 与当前架构文档删除“legacy delivery.json 仍作为现行只读兼容投影”的表述，改为 frozen
  artifact 仅可人工审计、所有当前 surface 明示 `LEGACY_NOT_BOUND`；
- stable commit-message/PR-body 明示 `presentation only / source=candidate`，不新增 metadata 字段。

## 5. Frozen/legacy compatibility

1. 不删除旧 workstream 磁盘上的 `delivery.json`，只删除 production reader。
2. 无 D1 candidate binding 的历史 workstream 由 runtime view 投影 `BLOCKED / LEGACY_NOT_BOUND`。
3. 无 `workflow/current.json` 的 legacy workstream 可显示初始状态，但 `statusSource=legacy_initial`，且不产生
   run/merge/finalize 建议。
4. 有 current 的 workstream 无论 `workstream.json.status` 为何，都以 current 为 canonical status。
5. 不做 `delivery.json -> candidate/merge-plan` 迁移，不用 compatibility fallback 伪造 authority。
6. 收敛后不再展示 legacy `delivery.json` 独有的 `prCreated/prUrl/updatedAt`，也不展示 raw merge plan 独有的
   `updatedAt`。这些字段没有 D1 View authority；旧文件仍可由 operator 作为 frozen artifact 人工审计，不得为保留
   显示而扩 View schema 或恢复 reader。

## 6. Cannot-bypass tests

### T1 CLI parity

同一真实 ledger fixture 分别处于 READY/APPLIED/COMMITTED/FAILED、BLOCKED/LEGACY_NOT_BOUND，以及
BLOCKED/WORKFLOW_NOT_DELIVERABLE，`status` 与 `summary` 必须与 runtime view 完全一致；mutation command 只在
canMerge/canFinalize 为 true 时出现。

WORKFLOW_NOT_DELIVERABLE fixture 必须使用真实 RUNNING workflow current，断言 CLI、status API、overview API、
console 与 mgt 均显示“当前尚不可交付”及原始 reason，零 mutation command，且不读取 legacy/raw fallback。completion
audit 非 ready 的 sibling fixture 具有相同边界。

### T2 Conflicting legacy fact

写入 `delivery.json.status=committed`，同时 authoritative merge plan 为 FAILED。CLI、status API、overview API、
console/mgt projection 全部只能显示 FAILED，且 production main/test/scripts 中 current-reader 零命中。

### T3 READY before plan

accepted candidate 已由 resolved human gate 绑定但 merge plan 尚未创建时，所有 surface 显示 READY/canMerge=true；
不得因 raw plan 缺失显示 unavailable。

### T4 APPLIED/COMMITTED capability

- APPLIED：canMerge=false、canFinalize=true、只展示 finalize；
- COMMITTED：两者均 false、commit SHA 一致、只展示 inspection；
- wrong/forged plan 由 runtime view BLOCKED/FAILED，presentation 不得改写。

### T5 Canonical workflow status

`workstream.json.status=created`、`workflow/current.status=COMPLETED`：list/status/overview/两个 UI 均显示
COMPLETED，`legacyInitialStatus=created`、`statusSource=workflow_current`。

无 current 的 frozen fixture：显示 legacy 状态与 `statusSource=legacy_initial`，且无 action-bearing command。

### T6 No projection fallback

API fixture 缺 `deliveryView` 或 projection failure 时，UI 显示 unavailable；即使 response 中人为注入 raw
`deliveryMerge` 也不得读取。console 与 mgt 都要有静态/行为断言。

UI 断言应聚焦真实 delta：删除 `overview.deliveryMerge` state、`deliveryView -> deliveryMerge` fallback、list/task-row
的 `workflowStatus` fallback，以及 mgt raw renderer。console 已按 `canMerge/canFinalize` 门控的部分作为保持性测试，
不得冒充本 checkpoint 的新增证明。

### T7 Attention ownership

COMPLETED fixture 带 APPLIED/FAILED raw plan 时，`WorkstreamAttentionEvaluator` 的结果不变，证明 ledger module
不再拥有 delivery lifecycle；server delivery view 仍正确显示对应状态。

### T8 Sibling and smoke sweep

- `StatusCommandTest`、`SummaryCommandTest`、`DeliveryViewServiceTest`、`WorkstreamApiControllerTest`、
  `StaticConsoleProjectionBoundaryTest`、management UI test、`WorkstreamAttentionEvaluatorTest` 与 smoke case 全绿；
- main/test/scripts 全仓 `delivery/delivery.json` current-reader 零命中；
- server response/UI production code 对 raw `deliveryMerge` 消费零命中；
- server/console/mgt production code 对 list-item `workflowStatus` 的读取零命中；agent-task row 也只能读取其自身
  task status 或 canonical workflow status，不得保留 `task?.workflowStatus` fallback；
- scheduler 只在 `statusSource=workflow_current` 时消费 canonical `status`；`legacy_initial` 必须明确 skip，不能触发
  resume 或 recovery；
- `TraceCommand` 对 merge-plan 的 artifact inspection 可保留，但必须证明它不决定 action/status。

## 7. 预期文件范围

Production：

1. `dual-runtime/.../DeliveryApplicationService.java`（实施期发现并关闭无 candidate binding 的非 FAILED plan bypass）
2. `dual-runtime/.../AcceptedDeliveryCandidateResolver.java`（真实混合事件流只校验 workflow `evt-*` 序列）
3. `dual-cli/.../StatusCommand.java`
4. `dual-cli/.../SummaryCommand.java`
5. `dual-server/.../DeliveryViewService.java`
6. `dual-server/.../WorkstreamQueryService.java`
7. `dual-server/.../SchedulerService.java`
8. `dual-server/src/main/resources/static/console.js`
9. `dual-server/src/main/resources/static/mgt/app.js`
10. `dual-ledger-file/.../WorkstreamAttentionEvaluator.java`
11. `scripts/smoke-test.sh`
12. `docs/reference.md`
13. `docs/design/dual-orchestrator-current-architecture.zh-CN.md`

Direct tests：

1. `DeliveryApplicationServiceTest.java`
2. `StatusCommandTest.java`
3. `SummaryCommandTest.java`
4. `DeliveryViewServiceTest.java`
5. `WorkstreamApiControllerTest.java`
6. `SchedulerServiceTest.java`
7. `StaticConsoleProjectionBoundaryTest.java`（及既有 mgt UI projection test，如 sweep 后名称不同则使用现有载体）
8. `WorkstreamAttentionEvaluatorTest.java`
9. `scripts/smoke-test.sh` 的现有 deterministic case

若实现需要新增 module、schema、workflow JSON、durable projection、generic read-model service 或 delivery status，停止并
回到架构评审。

## 8. End-to-end invariant

| 维度 | D2-B closure |
|---|---|
| Declared by | workflow current + D1 accepted candidate/merge-plan contract |
| Proven by | `WorkflowFileLedger.loadCurrent()` + `DeliveryApplicationService.View` |
| Consumed by | CLI status/summary、server status/overview、console/mgt、operator command exposure |
| Enforced before | operator 看见或执行 merge/finalize command 之前 |
| Failure route | BLOCKED（含 WORKFLOW_NOT_DELIVERABLE/LEGACY_NOT_BOUND）、FAILED、unavailable；不得回退 legacy/raw projection |
| Cannot-bypass | conflicting legacy/raw fixtures、capability parity、missing-view UI、canonical-status tests |
| Residual | legacy bytes 仍留作历史审计；timeline/artifact mtime 仅 presentation；R1 crash consistency deferred |

## 9. 实施与验证顺序

1. 先补 runtime-view/CLI/server 冲突 fixtures，让旧实现失败；
2. 收敛 CLI status/summary；
3. 收敛 server status/overview/list 与 DeliveryViewService command exposure；
4. 删除 ledger attention delivery 分支；
5. 删除 console/mgt fallback；
6. 更新 smoke 与现行架构/reference 文档；
7. 执行 focused tests；
8. 执行 full Maven reactor 与 `git diff --check`；
9. 执行全仓 sibling sweep，确认仅保留 authority owner、writer、artifact inspection 与历史文档引用；
10. 独立代码级 diff review PASS 后才允许选择性提交。

不调用 provider、不启动 real-case。D2-B deterministic PASS 后标记
`DELIVERY/CURRENT PROJECTION CONSISTENCY ENFORCED / NOT REAL-CASE PROVEN`；下一次自然 real-case 才验证多端实际表现。

## 10. 停止条件

- 任何 surface 需要直接解析 merge plan 才能给出当前状态或 mutation command：停止，先补 runtime view；
- 为旧 `delivery.json` 添加 fallback 或迁移推断：停止；
- 为统一文案而把 UI/CLI 命令字符串下沉到 domain/runtime：停止；
- 把 artifact-contract、context metadata 等无关 mtime residual 扩入本 checkpoint：停止；
- 计划独立架构评审出现 P0/P1：先修计划，不编码。

## 11. 实施期边界发现

首轮 focused test 将旧 Summary fixture 反转后，发现 `DeliveryApplicationService.view()` 对
`candidateRef` 为空但 status 非 FAILED 的 raw plan 会直接 `fromPlan()`，可把无 accepted-candidate binding 的
APPLIED/READY/COMMITTED plan 投影成 authority。该缺口位于本 checkpoint 声明的唯一 runtime owner，并直接违反
T4 forged-plan fail-closed，不是 presentation 特例。

实施因此在同一 owner 增加最小守卫：candidate ref 为空的非 FAILED plan 必须先解析 accepted candidate；即使存在
accepted candidate，plan 仍因缺 binding 被拒绝；不存在 candidate 时保留 `LEGACY_NOT_BOUND` 分类。candidate ref 为空
的 FAILED plan 继续用于展示在 candidate 解析前发生的 typed delivery attempt failure。新增 runtime 反证测试证明
unbound APPLIED plan 只能得到 BLOCKED、零 capability。无 schema/status/workflow 扩张。

首轮 full reactor 还发现 `SchedulerService` 是 list-item `workflowStatus` 的遗漏 sibling。它不是 presentation reader，
而是会触发 resume/recovery 的副作用消费者，因此不能仅把字段改名。实现改为只在
`statusSource=workflow_current` 时消费 canonical `status`；无 current 的 legacy fixture 明确 skip，新增测试证明零
runtime action。该变更关闭同一 canonical-status 边界，不新增状态或 fallback。

controlled lifecycle smoke 首次恢复可信 session occurrence 后，又暴露两项 sequence-only 缺口：

1. gate loop 通过 CLI 文本搜索 `BLOCKED`，把可见的 delivery eligibility 误当成 workflow status。脚本现直接读取
   `workflow/current.json.status` 控制流，CLI 文本仅供展示；
2. D1 resolver 对 `events.jsonl` 所有异构 event id 做全局字典序检查，真实 ledger 中交错的
   `agent-task-*`/`domain-*` 事件会让 accepted candidate 永远被判乱序。resolver 现只对 workflow-owned
   `evt-NNNNNN...` 做数值递增校验（与生成器的最少 6 位契约一致），非 workflow 事件不参与排序；
   `node_routed` 若不带 workflow event id 仍 fail closed。

测试 fixture 加入 interleaved agent-task event，旧 resolver 必败；controlled smoke 使用隔离临时 home 生成最小可信
session index，避免回退或污染 operator 的真实 ACPX session。最终 controlled full lifecycle 到达 COMPLETED，delivery
view 为 READY，accepted candidate ref/SHA 与 capability 均由 runtime view 投影。

最终验证结果：

- controlled lifecycle smoke：`COMPLETED`，6 个 human gate round、112 个 events、32 个 nodes、11 个
  runs、0 warnings；delivery view 为 `READY`且 `canMerge=true`；
- full Maven reactor：151 个 suites file，`1286/0/0/0`；
- `git diff --check` 与 `bash -n scripts/smoke-test.sh`：均通过；
- production sibling sweep：当前状态面对 legacy `delivery/delivery.json` 的 reader 为零；UI/server 不再从
  raw `deliveryMerge` 或 list-item `workflowStatus` 回退推导状态或操作能力。`TraceCommand` 保留 raw merge-plan
  artifact inspection，runtime service/writer 保留 authoritative plan owner，二者均不是 projection fallback。

当前成熟度候选为 `DELIVERY/CURRENT PROJECTION CONSISTENCY ENFORCED / NOT REAL-CASE PROVEN`。
独立代码级 diff review 已 `PASS（P0=0 / P1=0 / P2=2 知会）`。P2-1 指出的三个实施期
production 文件已回填至 §7 范围；P2-2 确认 `NotificationService.workflowStatus` 是自有 payload 字段，
不是 list-item fallback reader。因此成熟度正式记录为
`DELIVERY/CURRENT PROJECTION CONSISTENCY ENFORCED / NOT REAL-CASE PROVEN`，并满足选择性提交条件。
