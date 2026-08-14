# ACPX Turn Boundary 与 Permission Evidence 收敛 — 独立只读代码级 Diff Review

日期：2026-08-14
审查对象：当前未提交 diff（16 files，672 insertions / 87 deletions；另有 2 份未跟踪 docs）
对照：plan `acpx-turn-boundary-permission-convergence-plan-20260814.zh-CN.md`、plan review `…-review-20260814.zh-CN.md`、captured `ws-20260813T235458Z-4RQE` / `run-1786671207221`、acpx 0.11.0 dist。
方法：只读逐文件阅读 production diff、tests 与真实调用链；不采信实现报告；独立复跑 deterministic tests。未调用 provider、未运行 real-case、未修改/提交文件。

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 | 0 |
| P1 | 1（baseline 选择失败 → offset 0 全量历史回放为 current observation） |
| P2 | 4 |
| **Boundary Review** | **WARN**（核心设计成立且已被真实 cannot-bypass 测试覆盖；存在一个条件性 fail-open 洞，修复面小） |
| 独立复跑 | full reactor **1237 / 0 / 0 / 0**（140 suites，surefire 聚合）；`git diff --check` clean；reactor exit 0 |
| 选择性提交条件 | **暂不满足**（须先修复 P1-1 并补一条 fail-closed 测试） |

---

## 1. Findings

### P1-1（named-session baseline 选择失败时，从 offset 0 全量回放历史 stream 并当作 current occurrence —— 历史 denial 污染以新形态回归）

- **位置**：`AcpxAgentTransport.java:744/765/770`（`sessionStreamBaseline(request)` 三处失败路径 `return Map.of()`）＋ `AgentLifecycleEventReader.java:125-128/140-143`。
- **机制**：
  1. `sessionStreamBaseline` 在 index.json 缺失（:744）、无匹配 entry（:765，name+`!closed`+cwd 归一化匹配全失败）、或解析异常（:770）时返回**非 null 空 map**；
  2. reader 中 `streamBaselineCaptured=true` 但 `streamStartOffsets.isEmpty()` → `streamPath()` 的 mismatch guard（:125-128，要求 `!isEmpty()`）**不生效**；
  3. `initializeStreamOffset`（:140-143）走 `streamBaselineCaptured` 分支：`getOrDefault(sessionId, 0L)` = **0**，`initialStreamTail=false` → 从字节 0 读整个 stream，且每行 `currentObservation=true`。
- **后果**：历史 `session/request_permission`+reject 被当作当前 occurrence → projector 产出 PERMISSION_DENIED → `AgentExecutionHealth.observePermissionDecision` → **PERMISSION_BLOCKED 阻断信号**，恰好重建了本 checkpoint 要消灭的历史污染。同时历史的 `session/prompt` result 也会回放。
- **可达条件**：index.json 损坏/丢失但 stream 文件残留；或 cwd 归一化不一致（macOS `/tmp` vs `/private/tmp` 类路径别名：Java `toAbsolutePath().normalize()` 不解析 symlink）。正常流程（`sessions ensure` 先行，acpx 会建/复用非 closed entry，closed record 在可用性查找中被过滤 `cli.js:118`）不触发——但该洞恰在与故障 session 相关的异常态下静默打开。
- **对照**：diff 前 `Files.list` 目录扫描为**每个已存在** stream 文件记录 size offset，从不全量回放已有文件。guard 只覆盖「baseline 捕获到了另一个 id」的情形，未覆盖「选择彻底失败」。
- **修复（小）**：选择失败时返回 `null`（uncaptured → 既有安全回退：128KB bounded tail + `initialTail=true`，permission request/result 均被 projector 的 currentObservation 门挡住）或显式跳过命名 stream。
- **Required test**：index.json 缺失（或 cwd 不匹配）+ 历史 denial 在 stream + stderr 暴露 id → 不得产生 PERMISSION_BLOCKED。

### P2-1（未关联的 permission result（request 在 baseline 之前、result 在之后）产生空 evidence 的 DENIED/CANCELLED 权限）

- **位置**：`AcpxSessionStreamProjector.java`（result 路径 `permissionDetails.getOrDefault(wire.requestId(), EMPTY)` → 按 outcome/optionId 产出 DENIED/CANCELLED）。
- **场景**：前一个未取消 turn 的 pending permission 在本 run 的 baseline 之后落盘 result（部署过渡期的已污染 session 正是这种状态）。request 未被观察 → evidence 为 EMPTY（toolCallId/title 全空）却带 DENIED disposition → PERMISSION_BLOCKED，消息只剩 "permission request"。
- **应为**：request 未在本 occurrence 内观察到的 result 不得形成 current-run 权限（忽略或 UNAVAILABLE diagnostic）。现有测试未覆盖此变体。
- **非阻塞理由**：§4.2 closure 使 pending request 通常不跨 run 存活；触发面窄且 evidence 可见为空。

### P2-2（run 中 session id 变化（resume -32002 → session/new，captured 证据 stream line 4-6 的真实模式）导致本 run 全部 stream 观察丢失）

- **位置**：`AgentLifecycleEventReader.java:125-128`（live id 不在单条目 baseline → 整个 stream 被跳过）。
- **后果**：无 promptCompletion → `requiresSessionClosure` 触发 cancel（对无 in-flight prompt 的 session 为良性 no-op，session 不被 close，会话连续性保留）+ 丢失本 run 的 usage/timeline 证据 + 记录一次并非必需的 SESSION_CLOSURE。exit 0 + 合法 stdout 结果仍可 SUCCESS（`exitedUsability`: exit 0 → EXITED_ZERO）。fail-closed、代价有限，但 diff 前目录扫描会从 0 读新 stream（事实上的 occurrence scope），此处是行为回退。
- **非阻塞理由**：正确性保持（不伪成功、不误关健康 session）；仅证据完备性受损。

### P2-3（runner stop-loss / runtime terminate 路径仍只杀进程树，不做 named-session cancel）

- **位置**：`AgentExecutionAttemptReconciler.terminateInFlightProcesses`（本次 diff 未触及）。
- **现象**：stop-loss 杀 wrapper 进程树后，persistent ACP turn 仍可能存活；该路径不经过 transport 的 `quiesceSession`。plan §6 sibling sweep 明确列出 "runner stop-loss/session reap"，本 diff 未覆盖。
- **非阻塞理由**：在 transport 边界内本 checkpoint 已闭环；stop-loss 是 runtime 侧入口，属残余路径，应在下一 bounded 修复或明确记录为已知残余。

### P2-4（evidence 小疵）

- `sessionClosureFailureSignal` 的 `sessionId` 字段为空串（operator 侧信号不携带 session 名；timeline details 有 disposition 但无 sessionName）。
- baseline 选择用 `lastUsedAt` 字符串字典序比较：仅当 acpx 恒写同格式 ISO 时间戳才安全（无法在本环境完全验证 acpx 写入格式；仅影响同名多 entry 时选哪条）。

---

## 2. 14 项重点核实

### 1. request id 关联 request/result，仅当前 occurrence 产生 PERMISSION_BLOCKED — **成立（含 P2-1 例外）**
projector：request 存 `permissionDetails[requestId]`；result 按**同一 requestId** 取出并移除，disposition 由 `outcome/optionId` 推导（`selected`+`allow|allow_always`→APPROVED；`selected`+`reject*`→DENIED；否则 CANCELLED）；request 与 result 均被 `currentObservation` 门控。测试：`correlatesCurrentPermissionRequestAndDeniedDecision`（id 0/0 关联）、`historicalPermissionDecisionCannotBecomeCurrentEvidence`（currentObservation=false → 空）。

### 2. baseline 由 index.json name+cwd+active/latest 确定，无目录扫描、无历史污染 — **成立（含 P1-1 例外）**
`sessionStreamBaseline(request)`：读 `~/.acpx/sessions/index.json`，过滤 `name==sessionName && !closed && cwd==workdir(归一化)`，取 `lastUsedAt` 最新 entry，返回单条目 `{acpxRecordId: fileSize}`。`Files.list` 目录扫描已删除。正常路径下历史行全部位于 offset 之前（测试 `historicalPermissionDenialBeforeNamedSessionBaselineCannotPolluteCurrentTurn` 用真实文件+index 证明）。

### 3. record id 与 stderr id 不一致时 fail closed — **部分成立（P1-1）**
baseline 非空且 live id ∉ baseline → `streamPath()` 返回 empty，不读任何 stream（无权限 authority，fail closed）✓。但 baseline **为空**（选择失败）时 guard 失效 → offset 0 全量回放为 current observation ✗（P1-1）。

### 4. typed permission evidence 完整性 — **成立**
timeline `permission_decision` details：`sessionName / toolCallId / toolKind / title / resource(命令，512 裁剪+空白归一) / requestedOptions / disposition(APPROVED|DENIED|CANCELLED；request 时 REQUESTED；evidence 缺失 UNAVAILABLE) / lifecycleSequence + observedAt`。测试逐字段断言（transport test :1417-1425：toolCallId=`call-current-1`、toolKind=`execute`、requestedOptions=`[allow,reject]`、disposition=`DENIED`）。signal message 携带 `title: resource`。

### 5. 裸 exit=5 / 累计 permissionStats / stdout/stderr 文本全部失去 current-run authority — **成立**
- transport 的 exit==5 → PERMISSION_BLOCKED 合成块**已删除**（原 :581-593）；全仓无 `exitCode==5` 残留。
- catalog 的 `agent_permission_blocked`（匹配 "tool permission request failed"/"tool use aborted"/"tool_use_error>cancelled"）**已删除**；`AgentLifecycleEvent` stderr 分类同步移除这些文本。
- Dual 从不读取累计 permissionStats。
- 剩余 PERMISSION_BLOCKED producers 全量清点：`AgentExecutionHealth:138`（仅当前 occurrence stream 事件）＋ `AgentRunMetadataFinalizer:172`（已 typed reason 字符串的 round-trip 映射，无文本/退出码 producer）。sibling 无例外。
- 测试：`bareAcpxExitFiveCannotFabricatePermissionAuthority`（exit 5 → 无权限信号 + SESSION_CLOSURE）、`wrapperPermissionTextAloneCannotCreatePermissionAuthority`、`rawPermissionWordingCannotCreateCurrentTurnPermissionAuthority`。

### 6. contained denial 仍由 resolver 唯一判定 — **成立**
`AgentExecutionOutcomeResolver` 仅改 message 拼接；`isContainedPermissionDenial`（:128-139）未动：PERMISSION_BLOCKED 仅在 `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` + 合同 present/valid + 全 artifact 满足时降级 secondary；缺结果/工件必阻断。测试：`deniedOptionalToolCannotOverrideCompleteValidatedEvidence`（→SUCCESS_WITH_WARNINGS）、`deniedToolStillBlocksWhenAnyRequiredArtifactIsInvalid`、`permissionBlockedRemainsPrimaryWhenAgentResultIsInvalid`（operatorMessage 含 "mvn test"）。denial 不提前杀 turn（health 测试：HEALTHY + signal 记录）。

### 7. 五类异常返回均进入 quiescent closure — **成立**
`requiresSessionClosure = terminatedByHealth() || !promptCompletion().completed()` 覆盖 timeout/health 终止、非零退出+缺 completion、exit 0+缺 typed completion；`InterruptedException` catch → `Thread.interrupted()` 后 `quiesceSession`（finally 恢复 interrupt 标志）；`ExecutionEventPersistenceException` catch → `quiesceSession`；bounded correction（`runAgentResultCorrection` → 同一 `run()`）与 codex（同一 `runWithSession("codex")`）同路径。

### 8. closure 真实签名 / bounded timeout / cancel 失败 close / 双失败 typed PROCESS_STALLED / 无自动 retry — **成立**
`acpx <agent> cancel -s <session>`（与 acpx `addSessionOption` 实际签名一致；测试断言 command log 精确含 `claude cancel -s claude-current`）；`closureCommandTimeout()=min(sessionCommandTimeout,5s)`；cancel 失败 → `acpx <agent> sessions close <session>`；双失败 → `PROCESS_STALLED`（source `session_closure`）+ SESSION_CLOSURE FAILED disposition。PROCESS_STALLED severity=RECOVERABLE≠INFO → `blocksSuccess()=true` → resolver RECOVERABLE_ADAPTER_FAILURE + **MANUAL_ONLY**（无自动 retry）。测试 `failedCancelAndCloseRemainFailClosedAsProcessStalled`（cancel exit 8 / close exit 9）。

### 9. PID/process-tree cleanup 不冒充 session 已停 — **成立**
closure authority 是独立的 `quiesceSession`（typed disposition），finally 的 `destroyForcibly`+`cleanupManagedToolsStartedByRun` 保持原样且正交。测试 `abnormalReturnCannotLeavePersistentTurnWritingAfterTransportReturns`：后台进程每 100ms 写文件（非 wrapper 进程树成员），transport 返回后 500ms 文件大小不变——证明停止来自 cancel 而非进程树清理。

### 10. operator projection 消费 typed outcome — **成立**
`RecoveryAssessment`：classification=`agent_permission_blocked`；`retrySafe=false`（`branchRetrySafe = !"agent_permission_blocked".equals(classification)`）；suggested=`acpx <agent> sessions close <s> && acpx <agent> sessions new --name <s> && dualctl retry`；本 diff 为 legacy reason `permission_blocked` 增补 `# only after changing the permission policy and rebuilding the acpx session`；具体 action/resource 经 operatorMessage（resolver 现拼接 signal message，含 `title: command`）保留。全 diff 无 mid-turn gate、无 post-hoc approve 能力。测试 `typedPermissionFailurePreservesCurrentActionForOperatorWithoutBlindRetry`（含 reason 含 "mvn test"、retrySafe=false）。

### 11. stale reconciler 不覆盖 typed facts — **成立**
reconciler 未改动：`hasTerminalOutcome` 早返回 + durable 分支经 `adapterSignalsFromRunMetadata` 保留既有 signals；closure PROCESS_STALLED 与 permission 信号均落在 adapterSignals → 被保留；新 timeline kind（PERMISSION_DECISION/SESSION_CLOSURE）不影响 `readProcessExit` 扫描。

### 12. 测试为真实 cannot-bypass — **成立**
逐项：historical denial isolation（transport：真实 index+stream 文件，历史行在 baseline 前）✓；current denial（run 中 append 真实 request/result 行，断言 typed signal+全字段+精确 cancel 命令）✓；bare exit 5 ✓；raw permission wording（wrapper 文本+catalog 双测）✓；contained denial（resolver 双分支）✓；exact cancel command（command log 字符串断言）✓；transport 返回后后台写入停止（真实文件大小观察 500ms）✓；cancel+close failure fail closed ✓。全部使用 fake wrapper + 真实文件边界，非 JSON 字段断言。未覆盖：P1-1 变体与 P2-1 变体。

### 13. 672/87 是否可缩小 / 重复实现 / 第二 owner — **否（无过度设计）**
单一权限 authority（health←projector←wireline）；单一 closure owner（transport `quiesceSession`）；单一 baseline owner（`sessionStreamBaseline`）。SessionClosure 是 transport **私有** enum+record，disposition 经既有 timeline kind 表达，无新 domain module/持久化类型；PermissionEvidence 是 `AgentLifecycleEvent` 嵌套 record；RecoveryAssessment 仅 2 行。transport +213 中约 120 行即 closure 本体、40 行 baseline 选择、50 行 permission timeline——与原子边界相当，无明显可砍重复。

### 14. sibling paths — **成立（P2-3 残余）**
Claude/Codex 同一 `runWithSession`/`run()`；normal prompt 与 bounded correction 同一 `run()`；permission profiles 经未改动的 `addPermissionArguments`；interrupt/recovery 均 quiesce。残余：stop-loss `terminateInFlightProcesses` 无 session cancel（P2-3）。

---

## 3. End-to-end Invariant

| 维度 | 证据 |
|---|---|
| **Declared by** | plan §1/§4.1–4.4；transport 是 occurrence-scoped typed facts 唯一 owner（plan §3.2） |
| **Proven by** | 本 diff 10 个新 deterministic cannot-bypass 测试（6 transport + 2 projector + 1 health + 1 recovery）+ 既有 contained/interrupt/timeout 套件；独立复跑 reactor 1237/0/0/0。real-case 未运行 → 行为级证明仍缺 |
| **Consumed by** | `AgentExecutionOutcomeResolver`（blocking/contained 判定 + operatorMessage 携带 tool action/resource）、`AgentRunMetadataFinalizer`（signals→executionOutcome）、`RecoveryAssessment`（classification/retrySafe/suggested command）、reconciler durable 分支（保留 typed signals） |
| **Enforced before** | transport 返回 terminal result 前：occurrence boundary（index 单条目 baseline+reader mismatch guard）→ 异常返回先 `quiesceSession`（cancel→bounded→close→typed disposition）再 PROCESS_EXITED/finalizer；permission authority 仅来自本 occurrence 的 request/result 对 |
| **Failure route** | 裸 exit=5/权限文本 → 无 authority（诊断+closure）；当前 occurrence DENIED/CANCELLED + 缺 completion/artifact → PERMISSION_BLOCKED/MANUAL_ONLY；closure 双失败 → PROCESS_STALLED/MANUAL_ONLY；baseline id 不匹配 → 不读 stream（fail closed）。**例外：baseline 选择失败 → P1-1 fail-open** |
| **Cannot-bypass test** | §2 第 12 项列举的 8 组真实边界测试（fake wrapper + 真实 stream/index/文件观察） |
| **Residual raw/legacy path** | ① `AgentRunMetadataFinalizer:172` typed-reason round-trip 映射（无裸 producer，保留兼容）；② stop-loss/runtime terminate 无 session cancel（P2-3）；③ P1-1 的 index 失败 offset-0 回放；④ P2-1 未关联 result 的空 evidence DENIED |

---

## 4. 最终回答

### 1. 是否存在 P0/P1
**P0：0。P1：1**（P1-1 baseline 选择失败 → offset 0 历史回放为 current observation，条件性 fail-open，破坏 checkpoint 主不变量；修复面小：选择失败返回 null/跳过 stream + 1 条 fail-closed 测试）。P2：4（P2-1 未关联 result、P2-2 mid-run id 变化观察丢失、P2-3 stop-loss 无 closure、P2-4 evidence 小疵）。

### 2. 是否满足选择性提交条件
**暂不满足。** 代码+文档可拆分提交（production/tests 与 docs 无耦合），但 P1-1 位于本 checkpoint 的核心不变量上，应先修复并复跑（修复+测试估计 <30 行变更），再以 clean 状态提交。P2 项可随后续 bounded 修复或明确记录为已知残余。

### 3. 是否可以标记 `ENFORCED / NOT PROVEN`
**修复 P1-1 并复跑全绿后：可以。** deterministic cannot-bypass 已真实覆盖 8 组边界（ENFORCED）；未运行 provider/real-case（NOT PROVEN）。当前 P1 未修，暂不满足 ENFORCED 门槛。

### 4. 是否具备提交 clean SHA 后制定 fresh captcha-auth Verification Gate 的条件
**结构上具备、时序上未到。** Gate 起草可并行，但其 preflight 依赖的 execution SHA 必须包含本 checkpoint 的**修复后**版本；应顺序为：修 P1-1 → 复跑 reactor →（可选快速 diff 复核）→ 提交得 clean SHA → 制定 Gate → 单独授权 preflight/probes/fresh workstream。不得 resume 已污染的 `ws-20260813T235458Z-4RQE`（与 plan §8 一致）。

### 5. 是否应拆分 checkpoint
**不应拆分。** 4.1（occurrence 权限证据）与 4.2（quiescent closure）共享同一 baseline/reader 边界——P1-1 恰好横跨两者（baseline 选择属于 4.1 的 occurrence boundary，其 fail-closed 语义属于边界完整性）；4.3 消费 4.1 的 evidence 与 4.2 的 quiescence；4.4 保证前两者的 typed facts 经 reconciler 保留。拆开会出现：有 closure 无 occurrence 语义（或反之）的中间态，且 permission authority 与 session closure 将落入两个 owner。P1-1 的修复属于**本 checkpoint 内部**（occurrence boundary 的 fail-closed 补全），不是新 checkpoint。P2-3（stop-loss closure）是唯一合理的后续独立小项，因为它在 transport 边界之外、owner 不同（runtime），拆出后不破坏 transport 内 4.1/4.2 的原子性。

---

## 5. 独立复跑记录

- `mvn -q -T 1C test`：exit 0；surefire 聚合 **140 suites / 1237 tests / 0 failures / 0 errors / 0 skipped**（与声明一致；其中本 diff 新增 10 tests，其余 +4 来自此前已提交的中间 commit）。
- `git diff --check`：clean。
- 未运行 provider / real-case；未修改、未提交任何文件。

---

*本报告为只读同行评审证据。*
