# ACPX Turn Boundary 与 Permission Evidence 收敛计划 — 独立只读代码级架构评审

日期：2026-08-14
审查对象：`docs/plans/acpx-turn-boundary-permission-convergence-plan-20260814.zh-CN.md`（未提交，DRAFT）
对照：captured workstream `ws-20260813T235458Z-4RQE` / run `run-1786671207221`、ACP stream `c41799ab-…-stream.ndjson`（76626 行）、`run.json`、acpx 0.11.0 本地 dist、Dual production 代码（adapter / runtime / core / ledger）。
方法：只读。逐文件阅读 plan、Dual production、acpx dist 与 captured 证据；独立核实 14 个验证点；未调用 provider、未修改/提交文件、未启动 real-case。

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 | 0 |
| P1 | 0 |
| P2 | 4（均非阻塞，calibration / 精确化） |
| **Boundary Review** | **PASS** |
| 两项根因诊断 | **独立核实属实**（605s = caller timeout + 未取消 persistent turn；PERMISSION_BLOCKED = 累计 permissionStats 污染） |
| 是否过度设计 | **否**（复用既有 stream baseline / outcome resolver；SessionClosure 限为有限 disposition） |
| 是否 Implementation READY | **设计就绪可作为一个 bounded checkpoint 实施**（仍是 DRAFT/无代码；P2 calibration 后实施） |
| 是否单一原子 checkpoint | **是**（4.1–4.4 互相强化，拆分不产生可绕过的危险中间态，但会留下残余缺陷） |

> 说明：本评审只判定 *plan 的设计是否成立*。production 现存缺陷（裸 exit=5 → PERMISSION_BLOCKED 合成）是 plan 要修复的对象，不作为 plan 的 finding 计入。

---

## 1. Findings（P0 / P1 / P2，附真实 file:line）

### P0 — 无

### P1 — 无

### P2-1（§2.1 第三条把「wrapper 退出后 worktree 继续写入」表述为已证明，实际是合理推断）

- **位置**：`acpx-turn-boundary-permission-convergence-plan-20260814.zh-CN.md:30`。
- **现象**：plan 将「本轮 wrapper 退出后，目标 worktree 仍继续出现文件写入」列为已确认事实，用以证明 caller 超时不等于底层 ACP turn 已停。但 `run-1786671207221/run.json:135-149` 显示本次 run 的 `toolUsage.toolCallCount/writeCount/bashCount` 全为 0，implementation turn 在被截断窗口内只产生 `agent_thought_chunk`（stream line 44240 附近），无可观察 tool 副作用；而 66 个被改动文件的 mtime 分布在 09:51–10:17，进程于 01:43:33 退出（`run.json:31`），二者相差约 8 小时，且 worktree 被后续 run 复用（10:17 为后续 mvn），无法干净归因到「01:43:33 退出后的未取消 turn」。
- **判定**：底层机制（未取消的 persistent turn 仍可继续执行）由 acpx 源码独立证实（见 §2 已确认事实），结论方向正确；但 *本 run 的文件证据* 不能干净证明该具体副作用。建议把该条降级为「合理推断（机制成立，本 run 证据受 worktree 复用污染）」，避免 review/gate 阶段把推断当硬证据。
- **为何非阻塞**：不改变 plan 的修复方向；只影响 §2.1 的措辞强度。

### P2-2（§4.1 的 occurrence boundary 机制应读「命名 session 的 stream 文件」，且需先核验既有 baseline 是否已足够，再决定是否启用「fresh session segment」回退）

- **位置**：plan `:86`（fresh session segment 回退）。
- **现象**：Dual transport 已存在 occurrence 级别的事件读取——`AcpxAgentTransport.java:564-568` 构造 `AgentLifecycleEventReader` 时传入 `sessionStreamBaseline()`（`:681-703`）记录各 `.stream.ndjson` 文件大小作为起始 offset，`waitForExitOrRecoverableSignal`（`:570-640`）只读 baseline 之后的事件。因此 *occurrence-scoped permission event 在当前 stream boundary 下原则上可获得*，plan §4.1 的最小 typed evidence 不必默认落到「fresh session segment」。
- 同时，`sessionStreamBaseline()`（`:687`）`Files.list(~/.acpx/sessions)` 列举全部 stream 文件——其用途是取 size offset（事件观察），不是「扫描猜当前 permission request」，不违反 §7 硬约束；但为与 plan §4.1/§7「production 不得扫描全部 session 目录猜测当前 request」表述完全一致，occurrence boundary 应直接定位 *本次 run 的命名 session stream 文件*（`run.json` 的 `session`/`sessionId` 字段已知），只读该文件的 baseline-后内容，而不是枚举目录。
- **判定**：(a) 先用既有 stream baseline 实现 occurrence boundary；只有当 acpx CLI 结构化输出确实无法给出 occurrence-scoped 事件时，才回退到 fresh session segment（更重，且与 `requiresFreshSession`/compaction 交互需评估）。(b) occurrence 证据读取应针对命名 session 文件，避免目录级扫描。
- **为何非阻塞**：plan 已把 fresh segment 列为回退而非首选；只需在实施时明确顺序与文件定位方式。

### P2-3（§4.2 的 `acpx <agent> cancel --session <name>` 需钉死真实调用签名，并在 cannot-bypass 测试的 fake wrapper 契约里固化）

- **位置**：plan `:103`（`-> acpx <agent> cancel --session <name>`）。
- **现象**：acpx 0.11.0 dist 实测存在该动词：`cli.js:2108` `parent.command("cancel")`，经 `registerSharedAgentSubcommands`（`cli.js:36-42`）注册为 `<agent> cancel`，描述「Cooperatively cancel current in-flight prompt」，通过 `addSessionNameOption`（`cli.js:2109`）接受 session 选项，最终调用 ACP `connection.cancel({ sessionId })`（`live-checkpoint-BZrk9Mjz.js:4233-4237`）与进程内 `client.cancelActivePrompt`（`output-D_BGt1YI.js:2905/3003`）。故 plan 的调用方向成立。
- 但 plan 写的是 `--session <name>`；真实 session 选项 flag 名需以 `addSessionNameOption` 实际定义为准（与 prompt/exec 用的 `-s/--session` 一致即可）。§5 test #4/#5 的 fake acpx wrapper 必须按真实签名解析 cancel，否则 deterministic 测试与 production 调用会错位。
- **判定**：实施前确认 cancel 的精确 flag（`-s, --session <name>`），并在 fake wrapper 契约里钉死。
- **为何非阻塞**：动词存在、方向正确；只是 flag 字面需对齐。

### P2-4（SessionClosure 必须保持为「有限 disposition on 既有 execution timeline/adapterSignals」，且「exit=5 + 无 occurrence 证据」应映射既有 OUTPUT_INCOMPLETE，不得新增状态）

- **位置**：plan `:104-116`（SessionClosure disposition）与 `:80-83`（exit=5 无证据时 fail closed）。
- **现象**：
  - outcome resolver `AgentExecutionOutcomeResolver.java:128-139` 的 `isContainedPermissionDenial` 已实现 plan §4.1 的 contained 规则（PERMISSION_BLOCKED 仅在 `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` + 合同 present/valid + 全 artifact 满足时被降级为 secondary）。即 §4.1 的 contained 分支 **production 已具备**，plan 不必新建。
  - 「exit=5 + 当前 occurrence 无 permission 证据 → fail closed 为 adapter result inconsistent」可由既有 `OUTPUT_INCOMPLETE`（`AgentExecutionOutcomeResolver.java:58-68`，exit 非 0 + 无 blocking signal → `:83-99` 的 `process_output_incomplete`）表达，无需新状态。
  - `SessionClosure` 的四个 disposition（NOT_NEEDED/CANCELLED/CLOSED_AFTER_CANCEL/FAILED）必须作为投影到 *既有* `executionTimeline`/`adapterSignals` 的有限标记，与 §4.4 配合：reconciler 只对 *没有* terminal outcome 的 run 补齐（`AgentExecutionAttemptReconciler.java:75` `hasTerminalOutcome` 早返回），durable 分支（`:238-249`）保留既有 `adapterSignals`（`AgentRunMetadataFinalizer.adapterSignalsFromRunMetadata`）。若 SessionClosure 变成一条 *新的持久化 entity/并行 store*，反而会绕过 §4.4 的「同源保留」。
- **判定**：(a) SessionClosure 限定为既有 timeline/signal 上的 disposition，不新增持久化类型；(b) 「无证据 exit=5」显式映射既有 `OUTPUT_INCOMPLETE`/`process_output_incomplete`，不引入新 status；(c) §5 test #8 应断言 closure disposition 经由既有 `adapterSignals`/timeline 被 reconciler 保留，而非旁路。
- **为何非阻塞**：plan 本身（`:109`）已声明「不需要新 domain module」；该 P2 是把声明钉成测试与实施约束。

---

## 2. 14 项验证点（逐条核实）

### 1. 605118ms 来自 `--timeout 600` 还是协议错误 — **确认：caller timeout + 进程退出开销**
`run.json:29` 命令含 `--timeout 600`；`:32` durationMs 605118；`executionTimeline` `process_wait_completed` elapsedMs 605110 exitCode 5（`:240-247`）。唯一 JSON-RPC error 是 stream line 4 的 `-32002 Resource not found`（session/resume），随即由 line 5-6 `session/new` 恢复，属良性。`timedOut:false, interrupted:true`（`run.json:115-116`）。stream 持续产出 typed updates，无 initialize/shape/version 协议错误。≈5s 差额为 timeout 后 drain + error propagation + 进程退出。

### 2. runPromptTurn timeout 是否只 reject caller 而未 cancelActivePrompt — **确认：未取消**
acpx dist：`withTimeout`（`live-checkpoint-BZrk9Mjz.js:611`）用 `Promise.race`，`finally` 仅 `clearTimeout`；`cancelActivePrompt`（`:4256`）只出现在 `withInterrupt` 的 signal-handler arg（`output-D_BGt1YI.js:2905/3003`），由 `rejectInterrupted` 经 SIGINT/SIGTERM/SIGHUP（`:625-700`）触发，**不在 timeout reject 路径**。timeout 只 reject caller，persistent `client.prompt` 仍 pending。

### 3. persistent ACP turn 在 wrapper 退出后是否仍产生 tool/file 副作用 — **合理推断，非干净证据**
机制成立（见 #2）。但 `run.json:135-149` 本次 `toolUsage` 全 0；implementation turn 仅 thought_chunk。66 文件 mtime 09:51–10:17 vs 进程退出 01:43:33，相隔约 8 小时且 worktree 复用（10:17 = 后续 mvn），无法干净归因。→ 见 P2-1。

### 4. 实际 captured session model — **确认：DeepSeek-V4-Pro / provider deepseek**
`run.json:52-53`、`:128-134`、`providerMetadataSource:acpx_session_json`。「claude」是 agent/session 名，非模型。未与 GLM/Claude 混淆。

### 5. 唯一 request_permission 是否在 design(7435) 与 implement(32385) 之间、位于 13358 — **确认**
stream：design prompt line 7435；唯一 `session/request_permission` line 13358；implementation prompt line 32385。design revisions 15172/20239/23840、plan 25827 证实 13358 之后 design 仍在推进。

### 6. permission 命令 / disposition / 是否阻断 design 完成 — **确认**
stream line 13358：`cat .agent/.../task-contract.json | python3 …`（标题「Inspect full task contract scope and acceptance criteria」），disposition `outcome=selected, optionId=reject`（`User refused permission to run tool`）。发生在 design turn 内；design 后续多轮 revision + plan + implement 启动，证明拒绝被 contained，未阻断 design 推进。

### 7. applyPermissionExitCode 是否用累计 permissionStats 污染后续 CLI — **确认**
acpx dist `cli.js:425`：`stats.denied + stats.cancelled > 0 && stats.requested > 0 && stats.approved === 0 → process.exitCode = PERMISSION_DENIED(5)`。累计统计跨同一 persistent session 投影 design turn 的拒绝到 implementation run。

### 8. 当前实现是否有新 permission request — **确认：无**
implementation turn（32385–end）0 个 `session/request_permission`、0 tool call、无 stopReason，44240 thought_chunk 被截断。exit=5 / PERMISSION_BLOCKED 非 current-run permission 事实。

### 9. occurrence-scoped permission evidence 能否从 acpx 当前 output/event boundary 获得；fresh segment 是否最小可靠隔离 — **确认：既有 stream boundary 可提供 occurrence scope；fresh segment 非首选**
transport 已用 `sessionStreamBaseline()` + `AgentLifecycleEventReader`（`AcpxAgentTransport.java:564-568/681-703`）只读 baseline-后事件。最小修复点：`waitForExitOrRecoverableSignal:581-593` 仅在 *本次 occurrence 内* 观察到 request_permission/reject 时才产出 PERMISSION_BLOCKED；无 occurrence 证据时不从裸 exit=5 合成。fresh session segment 为更重回退，应后置（见 P2-2）。

### 10. closure 顺序（cancel→bounded wait→close→typed disposition→finalizer）是否足够 quiescence — **确认：成立且必要**
现状 `run()` finally（`AcpxAgentTransport.java:268-273`）仅 `destroyForcibly()` + `cleanupManagedToolsStartedByRun`，timeout/exit-5 路径 **不** cancel/close 命名 session；`closeSession`（`:438-474`）只在 `rebuildSession`（exit-4 reconnect，`:433-436`）与公共 lifecycle closer 调用。故 §4.2 的 cancel（`acpx <agent> cancel`，#见下）→ bounded wait → `sessions close` → typed disposition 是填这一缺口的最小序列。`rebuildSession`/公共 closer 已证 `sessions close` 可用且 best-effort 不抛（`WorkstreamSessionCloser.java`、`AcpxSessionLifecycle.java`）。

### 11. 是否仍存在 handler catch / stale recovery / runner stop-loss / bounded correction / one-shot exec / Codex adapter 旁路 — **部分存在，需 sibling sweep 覆盖**
- timeout/exit-5 路径无 session closure（见 #10）——主缺口。
- `runAgentResultCorrection`（`:175-182`）bounded correction 复用已建 session，需在 sibling sweep 中确认它同样走 occurrence boundary + closure（plan §6 已列）。
- one-shot `exec`（acpx `cli.js:2097` execCommand）与 Codex agent 走同一 `registerSharedAgentSubcommands`，cancel/sessions 接口同构；需 sweep 确认 Dual 对 codex 调用同样不裸 exit=5 合成。
- handler catch / runner stop-loss：reconciler `terminateInFlightProcesses`（`AgentExecutionAttemptReconciler.java:130-156`）只杀进程树，**不** cancel 命名 session——与 transport 同一缺口，§6/§4.4 须覆盖。
plan §6 已显式列举以上 sibling，方向完备；实施时须逐条落实。

### 12. 人工授权是否可执行（occurrence-scoped tool/action；post-hoc approve API；显式 runtime retry 非 mid-turn resume）— **确认：不建 mid-turn gate，无 post-hoc approve API**
acpx dist `approve` 全部为 prompt-time policy flag（`--approve-all`/`--approve-reads`，`flags-BKjjl3tF.js`、`cli.js`），**无** 事后批准同一 pending request 的控制面；`cancel` 之外无可对 in-flight prompt 的事后批准。故 §4.3「不建 mid-turn human gate、operator 显式 retry」与 acpx 能力一致，可执行。

### 13. stale reconciler 是否会把 typed outcome 覆盖成 STALE_RUNNING_PROCESS — **确认：当前不会，§4.4 为防御性硬化**
`hasTerminalOutcome`（`AgentExecutionAttemptReconciler.java:75`）早返回 → 不动已有 terminal outcome。本 run 的 outcome 实为 reconciler 经 `durable_process_result_without_terminal_outcome`（`:238-249`）从 durable processResult + **既有 adapterSignals（PERMISSION_BLOCKED）** 投影而来，**保留** signal 而非降级 STALE。`stale_running_attempt`（`:300-306`）只在「无 durable process result 且无 dead pid」时触发且 signals=`List.of()`，被 durable 分支优先（`:238`）。故当前不存在覆盖；§4.4 的价值在于：一旦 §4.2 写入 typed SessionClosure signal，确保它同样经 durable 分支被保留、不被任何旁路覆盖。

### 14. 4.1–4.4 是否应作为单一 bounded execution-closure checkpoint — **确认：是**
耦合关系：4.1 产出 4.3 要展示的 evidence；4.2 提供 4.3 授权前必需的 quiescence；4.4 保证 4.1/4.2 的 typed outcome 经 reconciler 保留。拆分不会产生 *可绕过的危险中间态*（4.3 是 display-only + 显式 retry，非新 gate），但会留下残余缺陷（如仅 4.1 修了污染却仍漏未取消 session）。故作为单一 checkpoint 绑定正确；若必须分阶段，至少 4.1+4.2 先于 4.3/4.4 产生价值，四项齐备方可声明 checkpoint 完成。

---

## 3. 已确认事实 / 合理推断 / 无法验证

**已确认事实（file:line 背书）**
- 605118ms = caller `--timeout 600` + 进程退出开销；唯一 -32002 已恢复；无协议兼容失败（`run.json:29/32/240-247`；stream line 4-6）。
- runPromptTurn timeout 只 reject caller，不 cancelActivePrompt；cancel 仅 OS-signal 触发（`live-checkpoint-BZrk9Mjz.js:611/625-700/4256`；`output-D_BGt1YI.js:2905/3003`）。
- applyPermissionExitCode 用累计 permissionStats → design turn 拒绝污染 implementation run（`cli.js:425`）。
- design(7435) 唯一 request_permission(13358, reject) implement(32385)；implementation turn 0 permission/0 tool（stream；`run.json:135-149`）。
- model = DeepSeek-V4-Pro/deepseek（`run.json:52-53/128-134`）。
- 裸 exit=5 → PERMISSION_BLOCKED 合成在 transport（`AcpxAgentTransport.java:26-30/581-593`，source `acpx_exit`）；这是污染的直接投影点。
- timeout/exit-5 路径 **不** cancel/close 命名 session（`AcpxAgentTransport.java:268-273`；close 仅 `:433-436/459-474`）。
- acpx `<agent> cancel` 动词存在、协作取消 in-flight prompt（`cli.js:2108/36-42`；`live-checkpoint-BZrk9Mjz.js:4233-4237`）。
- 无 post-hoc approve API（`approve` 均为 prompt-time flag）。
- resolver `isContainedPermissionDenial` 已实现 §4.1 contained 规则（`AgentExecutionOutcomeResolver.java:128-139`）；PERMISSION_BLOCKED → RECOVERABLE_ADAPTER_FAILURE + MANUAL_ONLY（`:23-33`），无自动 retry。
- reconciler 不覆盖 typed outcome（`AgentExecutionAttemptReconciler.java:75/238-249`）；本 run outcome 由 reconciler 经 durable 分支保留 PERMISSION_BLOCKED 投影（`run.json:194-210/257-267`）。

**合理推断**
- persistent uncancelled ACP turn 在 wrapper 退出后 *可* 继续产生副作用（机制由 #2 证实）；但本 run 文件证据受 worktree 复用污染，未干净归因（见 P2-1）。
- 「fresh session segment」为更重隔离方案；既有 stream baseline 多半已足够 occurrence scope（见 P2-2）。

**无法验证（留实施/real-case）**
- §4.1–4.4 实施后的运行时 quiescence（cancel→close 后观察窗口内无副作用）——需 cannot-bypass 测试 fake wrapper + 后续 fresh real-case。
- 各 sibling path（one-shot exec / Codex / bounded correction / stop-loss）在 *实施后* 是否同样不裸 exit=5 合成、同样走 closure——plan §6 已列，需 diff review 核实。

---

## 4. End-to-end Invariant（proposed checkpoint，7 维）

| 维度 | 证据 / 设计 |
|---|---|
| **Declared by** | plan §1 三目标 + §4.1–4.4；transport 是 occurrence-scoped typed facts 的唯一 owner（plan §3.2） |
| **Proven by** | 当前仅 DESIGNED/NOT IMPLEMENTED（plan §8）。deterministic 须由 §5 八项 cannot-bypass 测试（fake acpx wrapper + 隔离 session）证明；real-case 未运行 |
| **Consumed by** | `AgentExecutionOutcomeResolver`（`AgentExecutionOutcomeResolver.java:15-122`，含 `isContainedPermissionDenial:128-139`）、`AgentRunMetadataFinalizer`（`:114-145`）、reconciler durable 分支（`AgentExecutionAttemptReconciler.java:238-249`）、`RecoveryAssessment`（`dual-ledger-file/.../RecoveryAssessment.java:147-149`，permission 不盲 retry） |
| **Enforced before** | occurrence boundary 与 quiescent closure 必须在 transport 返回 terminal result **之前**完成（plan §4.2 序列），并在 finalizer 写 executionOutcome 前写入 typed signal |
| **Failure route** | 当前 occurrence 无证据 + exit=5 → 既有 `OUTPUT_INCOMPLETE`/`process_output_incomplete`（`AgentExecutionOutcomeResolver.java:58-68/83-99`），不伪成功；closure FAILED → fail closed，MANUAL_ONLY，不可 scheduler retry/resume |
| **Cannot-bypass test** | §5 #1–#8（historical denial isolation / current denial fail closed / contained denial / timeout cancels session / closure failure blocks retry / no generic human authorization / typed authorization projection / reconciler preservation） |
| **Residual raw/legacy path** | 现存 `AcpxAgentTransport.java:581-593` 裸 exit=5→PERMISSION_BLOCKED 合成 + timeout/exit-5 路径无 session closure——正是本 checkpoint 要移除/收敛的 raw path；实施后须确认 sibling（exec/codex/correction/stop-loss）无残留 |

---

## 5. Required Fixes（实施前/实施中）

1. **§2.1 第三条降级为合理推断**（P2-1）：措辞改为「机制成立（未取消 persistent turn 可继续执行），本 run 文件证据受 worktree 复用污染」。
2. **occurrence boundary 用命名 session 文件，先核验 baseline**（P2-2）：读 `run.json.session` 对应 stream 文件的 baseline-后事件；fresh session segment 仅在结构化输出确实无法给 occurrence 事件时回退。
3. **钉死 cancel 调用签名 + fake wrapper 契约**（P2-3）：确认 `-s/--session`，§5 测试 fake wrapper 按真实签名解析。
4. **SessionClosure 限为有限 disposition / 无证据 exit=5 映射既有 OUTPUT_INCOMPLETE**（P2-4）：不新增持久化类型；§5 #8 断言 closure 经既有 `adapterSignals`/timeline 被保留。
5. **移除裸 exit=5 合成**（对应 plan §4.1，实施目标点 `AcpxAgentTransport.java:581-593`）：仅 occurrence-scoped request_permission/reject 才产出 PERMISSION_BLOCKED；无证据时记录为 adapter diagnostic + 既有 OUTPUT_INCOMPLETE，**不得**伪成功。
6. **timeout/exit-5（及 stop-loss `terminateInFlightProcesses`）路径补 session cancel→close**（对应 plan §4.2）：覆盖 `run()` finally 与 reconciler stop-loss 两条缺口。

> 以上 5–6 为 plan 已主张的修复，列在此处仅为 *核对 plan 是否指向正确代码点*（指向正确）。

---

## 6. Required Deterministic Tests（对应 §5，每条须可运行边界断言）

- **#1 Historical denial isolation**：同一 session 旧 turn deny（fake wrapper 注入累计 stats），新 turn 完成有效结果且无 request_permission → 新 run 不得 `PERMISSION_BLOCKED`。直接证伪当前 `:581-593` 行为。
- **#2 Current denial fail closed**：当前 turn 发具体 request_permission 且 reject、required completion 缺失 → outcome permission_blocked，evidence（tool/action/seq）精确保留。
- **#3 Contained denial**：当前 turn deny 后仍交 strict-valid envelope + 全 required artifact → node 成功（`isContainedPermissionDenial` 路径），denial 仅 secondary。
- **#4 Timeout cancels session**：fake wrapper 在 caller timeout 后仍尝试写文件；transport 必须 cancel→bounded wait→close，返回后观察窗口内文件无变化。
- **#5 Closure failure blocks retry**：cancel 与 close 均失败 → SessionClosure=FAILED，workflow 入人工 recovery，scheduler 不 retry/resume（断言 `retryPolicy=MANUAL_ONLY` 且无 resume）。
- **#6 No generic human authorization**：仅裸 exit=5、无 current permission evidence → 不生成可批准 permission action（WorkstreamAttention 无 approve action）。
- **#7 Typed authorization projection**：current permission evidence 完整 → CLI/UI 共用 `WorkstreamAttention` 显示同一 action/resource/retry-safety，approval 只创建一次显式 retry。
- **#8 Reconciler preservation**：已有 typed permission/timeout/closure signal → stale recovery（`AgentExecutionAttemptReconciler`）不覆盖为 generic stale reason（断言 durable 分支保留 `adapterSignals`，`hasTerminalOutcome` 早返回）。

补充约束：full reactor 与 `git diff --check` PASS；§5 #1–#8 必须用 fake acpx wrapper（真实 cancel/close 签名），不得依赖真实 provider；实施后先做独立代码级 diff review。

---

## 7. 是否过度设计 — **否**

- §4.1 occurrence boundary 复用既有 `AgentLifecycleEventReader` + `sessionStreamBaseline()`，typed evidence 为最小字段集（session/run id、tool call id、tool kind、title/command、options、disposition、sequence）。
- §4.2 SessionClosure 明确「不需要新 domain module」，四个有限 disposition 投影到既有 execution timeline/adapterSignals（须由 P2-4 钉死）。
- §4.3 显式 **不** 建 mid-turn gate、不自动批准、不失控 resume；与 acpx 无 post-hoc approve API 的事实一致。
- §4.4 为既有 reconciler guard 的防御性硬化，非新平台。
- 唯一需警惕的扩张点：P2-2 的 fresh session segment 回退（更重），应后置于 baseline 核验之后。

---

## 8. 是否 Implementation READY — **设计就绪，作为一个 bounded checkpoint 实施（需 P2 calibration）**

- 两项根因由 file:line 独立核实属实，且 plan 修复点指向正确代码（`AcpxAgentTransport.java:581-593` 合成、`:268-273` 无 closure）。
- 所需外部能力（`acpx <agent> cancel`、`sessions close`）实测存在；outcome/resolver/reconciler 既有路径足以承载 typed 投影与保留。
- 全部硬约束（不新增 phase/module/registry/engine/permission platform；不改模型/prompt/strict parser/bounded correction；不增大 timeout 掩盖未取消 turn；不裸 exit=5 造权限 authority；不建 generic human gate/自动批准/自动 retry/失控 resume；不扫描 session 目录猜 permission）均被 plan §7 显式拒绝，且与代码事实一致。
- 当前为 DRAFT / DESIGNED-NOT-IMPLEMENTED；READY 指「可实施」，非「代码完成」。实施须：先落 P2 calibration → 实现 4.1–4.4 → §5 八项 cannot-bypass + reactor + `git diff --check` PASS → 独立代码级 diff review PASS → 才评估是否对 captcha-auth 建 fresh workstream（不得 resume 已污染 workstream，与 plan §8 一致）。

---

## 9. 是否能作为单一原子 checkpoint — **是**

4.1–4.4 互相强化（evidence↔quiescence↔authorization↔reconciler preservation），拆分不产生可绕过的危险中间态（4.3 display-only、显式 retry），但会留残余缺陷。作为单一 bounded execution-closure checkpoint 绑定正确；checkpoint 完成判定须四项齐备 + §5 全绿 + diff review PASS。

---

*本报告为只读同行评审证据；未调用 provider、未修改、未提交任何文件。*
