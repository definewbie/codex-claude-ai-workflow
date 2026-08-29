# D2-B Delivery Projection Convergence — 独立代码级 Diff Review

日期：2026-08-29
基线：`83d840ed`（D2-A clean checkpoint）
评审范围：工作树相对基线的全部未提交变更（22 files，+438/−222；production 13、tests 8、docs 3、smoke 1）
声明基线复核：full reactor `1286/0/0/0` 已由本评审独立复现（surefire 聚合 151 files）；controlled lifecycle smoke
`COMPLETED`（6 gate rounds / 112 events / 32 nodes / 11 runs / 0 warnings，delivery view `READY`/`canMerge=true`）为实施方声明，
本评审对其 harness 做了静态可信度核验（见 Boundary Review），未复跑完整 lifecycle。
评审方式：只读。未修改 production 代码、未调用 provider、未启动 real-case；仅执行 Maven 测试与 grep/git 只读命令。

## 1. Findings

### P0

无。

### P1

无。

### P2

**P2-1 实施文件范围超出计划 §7 清单，且未回填实施计划。** 三个 production 文件不在 D2-B 计划 §7 的预期清单内：
`AcceptedDeliveryCandidateResolver.java`（+13/−3）、`DeliveryApplicationService.java`（+6/−1）、`SchedulerService.java`（+5/−1）。
逐项核实后三者均必要且有对偶测试（见下），active plan 文档也完整记录了发现路径，但实施计划 §7 本身未回填。属文档一致性
知会项：建议在选择性提交信息中显式声明这三个文件及理由，或回填计划。

**P2-2 `NotificationService.java:171` 仍写出同名 `workflowStatus` key。** 该 key 由
`effectiveStatus(result)` 从自有 `WorkflowRuntimeResult` 计算，是 notification payload 的自有产出，不是 list-item
`workflowStatus` 的 reader，不违反 T8 零命中判据。知会：若后续 sweep 以"全仓 workflowStatus 零命中"为口径会误报，
sweep 判据应保持"list-item `workflowStatus` 读取零命中"的精确措辞。

### P0/P1 均无的依据（关键验证点逐项）

1. **单一 authority**：workflow 状态唯一来自 `WorkflowCurrentDocument`（CLI summary 经
   `CliLedgerFactory.create().loadCurrent()`，server 直接 `loadCurrent`）；delivery 唯一来自
   `DeliveryApplicationService.view()`（CLI status/summary 直连 `new DeliveryApplicationService().view(root, ws, null)`
   ——`targetRepo` 从 `workstream.json.repository().originalWorkdir()` 派生，null target 可行）。View record 未加任何
   字段/状态（`ACCEPTED_CANDIDATE_INVALID` 为既有 reasonCode），无第二 read model。
2. **legacy/raw 状态解释清除**（三 sweep 独立执行）：
   - `delivery/delivery.json` production reader 零命中（唯一残留命中是 workflow variant 资源文件名
     `agent-lifecycle.terminal-delivery.json`，非 artifact reader）；
   - server response/UI 对 raw `deliveryMerge` 消费零命中（唯一命中是 `WorkstreamApiController.deliveryMerge(` mutation
     endpoint 方法名，委托 service）；
   - list/task-row `workflowStatus` 读取零命中（console.js:332/:393 与 mgt:215 的 fallback 已删；残留命中均为合法：
     console:269/282 读 workflow block 自身 status、mgt:535 是局部变量、`WorkstreamQueryService.java:173` 是计划明示的
     `timeline_projection` legacy fallback、SchedulerPolicy 是参数名）。
3. **attention 减法**：`WorkstreamAttentionEvaluator.completed()` 删除全部 merge-plan 读取与 APPLIED/FAILED 分支
   （−42 行）；对偶测试改为"存在 APPLIED raw plan 时 attention 仍只报 workflow 事实"（T7 反证）。
4. **mutation command 不可绕过**：CLI summary COMPLETED 分支——`canFinalize()` 才给 finalize、`canMerge()` 才给
   merge、COMMITTED 只给 inspection 文案、其余输出 `not actionable: reasonCode - reason`；server
   `nextCommands` 由 status 字符串 switch 改为 `canMerge/canFinalize` 门控；console 控件本就 capability 门控
   （保持性断言）；mgt renderer 纯展示不生成命令；**scheduler 不再从 list-item `workflowStatus`（可能是 legacy 初值）
   触发 run/resume 副作用**——`statusSource != workflow_current` 一律 skip（`tickDoesNotScheduleFromLegacyInitialStatus`
   证明零 runtime action）。unbound APPLIED raw plan 无法产生 finalize（`rawAppliedPlanCannotProduceFinalizeWithoutAcceptedCandidateBinding`）。
5. **runtime resolver 混合账本确定性与 fail-closed**：旧实现按 event id 全局字典序强校验，真实 ledger 中交错的
   `agent-task-*` 事件（字典序小于 `evt-*`）会被误判乱序而拒绝解析。修复后仅对 workflow-owned
   `evt-NNNNNN` 做数值递增校验（regex `evt-(\d{6,})` 与 `LedgerUtils.nextEventId` 的 `%06d` 生成契约一致，
   `Long.parseLong` 数值比较在超 6 位时仍正确，严格强于旧字典序），非 workflow event 跳过不参与排序，
   `node_routed` 缺 workflow event id 仍抛错。对偶 fixture 已加入 interleaved
   `agent-task-finished-001` 事件（`DeliveryApplicationServiceTest`）。
6. **view() 无绑定 plan fail-closed**：旧代码对 `candidateRef` 为空的 plan 完全跳过 candidate 校验、直接
   `fromPlan` 投影——raw plan 可在无 accepted binding 时成为状态 authority。修复后：非 FAILED 的无绑定 plan 先
   `accepted(paths)`（缺失即 `LEGACY_NOT_BOUND`/`ACCEPTED_CANDIDATE_INVALID`）再抛
   `ACCEPTED_CANDIDATE_INVALID "missing its accepted candidate binding"`；FAILED 豁免保持（暴露零 mutation command，
   writer 是特权方）。对偶测试 `appliedPlanWithoutAcceptedCandidateBindingCannotBecomeAuthority`。
7. **canonical status**：status/overview/list 三处 `status = current.status`（`statusSource=workflow_current`），
  无 current 时 `status = legacy 初值`（`legacy_initial`）+ `legacyInitialStatus` 双投影；`workflowStatus` 双字段删除，
  API 测试断言 key 不存在；SummaryCommand 非 workflow_current 时"no action command is projected"（fail-closed）。
8. **测试独立复现**：full reactor `1286/0/0/0`（151 files，本评审聚合复现，与声明基线一致）；`git diff --check` clean；
  聚焦增量测试均为真断言（StatusCommandTest 的 WORKFLOW_NOT_DELIVERABLE 四行断言、
  DeliveryViewServiceTest 的 RUNNING→BLOCKED/零 nextCommands/"当前尚不可交付"、
  WorkstreamApiControllerTest 的 canonical status/delivery key 缺席/deliveryView reasonCode、
  StaticConsoleProjectionBoundaryTest 的六个静态反证断言、SchedulerServiceTest 的 legacy skip）。

## 2. Boundary Review

| 维度 | 判定 |
|---|---|
| **Source of truth** | ✅ 双 owner 收口成立：`workflow/current.json` 与 `DeliveryApplicationService.View`。CLI/API/UI/scheduler/attention 无第三解释者；attention 回归纯 workflow/goal/recovery；`WorkstreamAttentionEvaluator` 所在 ledger-file 模块无 runtime 依赖，减法与模块边界一致 |
| **Mutation command 绕过** | ✅ 四个暴露面（CLI summary、server nextCommands、console 控件、scheduler 副作用触发）全部 capability/statusSource 门控；smoke 控制流不再从 CLI 文本 grep 状态（旧逻辑会把 delivery eligibility 的 "BLOCKED" 误当 workflow 状态——实施期发现并修复，改为直接读 `current.json.status`） |
| **Controlled smoke 可信度** | ✅ 静态核验：acpx 为纯 bash stub（canned `--help/--version`、本地 python 写 session index，无任何真实 claude/codex 调用与网络）；HOME/JAVA_TOOL_OPTIONS 隔离到 `/tmp/dual-smoke-home-*` 并入 cleanup 白名单；终态判定读 `workflow/current.json`；delivery 断言改为 `dualctl status` 必须投影 `Status: READY`（authority 断言，替代旧"legacy 文件不存在"）。确定性成立；实施方声明到达 COMPLETED 与该 harness 设计自洽 |
| **变更最小性** | ✅（带 P2-1 知会）production 13 文件中 10 个与计划 §7 一一对应；3 个计划外文件均有必要性（scheduler 是删字段的必然连带；两个 runtime 是 fail-closed 收紧，分别由旧 fixture 反转与 controlled smoke 发现），无 schema/status/字段/read-model 扩张，不触发计划 §10 停止条件 |
| **Frozen/legacy compatibility** | ✅ 旧 `delivery.json` 磁盘保留、reader 全删（无 writer 存在）；无 current 的 workstream 以 `legacy_initial` 展示且零 action command（scheduler/summary 双证）；无迁移、无 fallback 伪造 authority；`WORKFLOW_NOT_DELIVERABLE` 在五 surface 均为可见的 eligibility 状态、双 capability false、零 mutation command |
| **Sibling 遗漏** | ✅ 无第四个 reader。保留项定性正确：`TraceCommand` merge-plan artifact inspection、`CliNextActionHints` 静态文案、`DeliveryMergePlanWriter`（authority writer）、NotificationService 自有 payload（P2-2 知会） |

## 3. 选择性提交判定

**满足，可选择性提交。** 对照 D2-B 计划 §9 顺序：冲突/in-flight fixtures 先行（含令旧实现失败的 unbound-APPLIED 反转）✓；
CLI/server/attention/UI 收敛 ✓；smoke 与 reference/architecture 文档更新 ✓（reference.md 删除"legacy 只读兼容投影"表述，
改为 frozen artifact + LEGACY_NOT_BOUND 明示）；focused tests ✓；full reactor `1286/0/0/0` ✓（独立复现）；
`git diff --check` clean ✓；sibling sweep 三项零命中 ✓；本 diff review 即第 10 步。建议提交信息涵盖：双 owner 收口、
scheduler/`workflowStatus` 连带、view 无绑定 fail-closed、resolver 混合账本数值序校验、smoke 控制流 authority 化。

## 4. End-to-end invariant

| 维度 | D2-B closure（经代码与测试验证） |
|---|---|
| Declared by | `WorkflowCurrentDocument`（status/active/open gates）+ D1 accepted candidate/merge-plan contract（candidateRef/SHA/expectedResultTree 绑定） |
| Proven by | `WorkflowFileLedger.loadCurrent()`；`DeliveryApplicationService.view()`——`requireCompleted` → accepted resolver（数值序事件账本）→ `requirePlanCandidate`/无绑定 fail-closed → `fromPlan` capability |
| Consumed by | CLI status/summary、server status/overview/list、console/mgt、`DeliveryViewService.nextCommands`、scheduler tick（经 `statusSource`） |
| Enforced before | operator 在任一 surface 看见或执行 merge/finalize/commit 命令之前；scheduler 触发任何 runtime action 之前（仅 `workflow_current`） |
| Failure route | `BLOCKED + reasonCode`（WORKFLOW_NOT_DELIVERABLE/LEGACY_NOT_BOUND/ACCEPTED_CANDIDATE_INVALID/TARGET_NOT_ELIGIBLE…）与 `FAILED` 经 view 显式投影；UI 无 view 显示 unavailable；CLI 非 canonical current 显示 "no action command"；无 legacy/raw/文本回退 |
| Cannot-bypass test | conflicting legacy（delivery.json=committed vs plan FAILED 家族）、unbound APPLIED plan 反证、capability parity、attention 对 raw plan 不变、scheduler legacy skip、mixed-event resolver fixture、missing-view UI 静态反证、canonical-status/`workflowStatus` key 缺席断言 |
| Residual | legacy bytes 留作人工审计；timeline/artifact mtime 仅 presentation（已登记）；R1 crash consistency deferred；NOT REAL-CASE PROVEN |

**成熟度**：`DELIVERY/CURRENT PROJECTION CONSISTENCY ENFORCED / NOT REAL-CASE PROVEN` —— deterministic 全绿 + 三 sweep
零命中 + smoke COMPLETED（受控 stub）；多端真实表现待下一次自然 real-case。

## 5. 残留知会（不阻塞）

1. P2-1：三个计划外 production 文件建议在提交信息或实施计划 §7 回填声明。
2. P2-2：后续 sweep 口径保持"list-item `workflowStatus` 读取零命中"的精确措辞，避免把 NotificationService 自有
   payload 误报为违规。
3. FAILED 无绑定 plan 的豁免分支（`DeliveryApplicationService.view()`）保持最小信任：依赖 merge-plan writer 为特权方、
   FAILED 暴露零 mutation command。若未来出现非特权写入路径，需重新评估。
