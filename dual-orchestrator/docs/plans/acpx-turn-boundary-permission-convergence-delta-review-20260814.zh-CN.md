# ACPX Turn Boundary 与 Permission Evidence Convergence — P1-1 修复 Delta 复审

日期：2026-08-14
审查对象：当前未提交 diff 中相对 WARN 评审（`…-diff-review-20260814.zh-CN.md`）的 delta（17 files，745 insertions / 90 deletions；delta 本身 ≈ +73/−3 + 测试改写）。
范围：严格限定原 P1-1 与同一边界的两个小修正（未关联 permission result、closure session-id）。不含 mid-run session rollover、runtime stop-loss closure（见 §5）。
方法：只读逐行核对 production、测试、reader/projector 全链；不采信计划或实施结论；独立复跑。未修改/提交文件，未调用 provider/real-case。

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 / P1 / P2（delta 范围内） | 0 / 0 / 0 |
| 原 P1-1 | **CLOSED** |
| 未关联 permission result | **CLOSED** |
| closure session-id | **CLOSED** |
| **Boundary Review** | **PASS** |
| 选择性提交条件 | **满足** |
| 成熟度 | 只能标 **ENFORCED / NOT PROVEN** |
| 独立复跑 | reactor **1239 / 0 / 0 / 0**（140 suites 聚合）；`git diff --check` clean；exit 0 |

---

## 1. 三项修复逐点核实

### 1.1 P1-1（baseline 选择失败 → offset 0 历史回放）— **CLOSED**

**Production（`AcpxAgentTransport.java`，`sessionStreamBaseline(request)` 方法体）：**
- `index.json` 缺失：`if (!Files.isRegularFile(indexPath)) return null;` ✓
- 无 name + normalized cwd + open 匹配：`if (selectedId.isBlank()) return null;`（循环过滤 `name==sessionName && !closed && cwd==workdir归一化`，取最新 `lastUsedAt`）✓
- 读取/解析异常：`catch (IOException | RuntimeException ignored) { return null; }` ✓

三条失败路径全部返回 `null`；方法仅剩的其余返回是单条目 `Map.of(selectedId, size)` —— **不再存在空 map 返回**。

**Reader 链路（`AgentLifecycleEventReader.java`）：** `null` → `streamBaselineCaptured=false` → `initializeStreamOffset` 走既有 bounded-tail 分支：`streamOffset = max(0, size − 128KB)`、`initialStreamTail=true` → 首读内容 `currentObservation=false`；此后 run 内追加的字节才为 current。projector 的 `currentObservation` 门对 `session/request_permission` 的 request 与 result 双侧生效 → 历史 denial 无法成为 current facts。

**offset-0-as-current 路径清点（穷尽）：**
1. captured 且命中：offset=baseline size，`Math.min(size, offset)` 防文件缩小 → 无回放；
2. captured 且 live id 不在 baseline：`streamPath()` 返回 empty，不读 ✓；
3. uncaptured（本修复后的失败回退）：offset=0 仅当文件 <128KB 或空文件——<128KB 时 `initialStreamTail=true`（非 current），空文件无历史内容 ✓；
4. `initializeStreamOffset` 的 IOException → offset=0、`initialTail=false`：仅在 `isRegularFile` 与 `size()` 之间文件消失的竞态下可达，且随后 `readNewBytes` 对同一文件的读取同样失败 → 无数据；属既有行为，非本 delta 引入。

**Cannot-bypass 测试（真实边界）：** `AcpxAgentTransportTest.missingSessionIndexCannotReplayHistoricalPermissionDenialAsCurrent` —— 真实 stream 文件含历史 request+reject（<128KB）、**无 index.json**、stderr 暴露 record id、exit 5 → 断言 **无 PERMISSION_BLOCKED**。该文件小于 128KB，恰好证明 tail 回退把全部历史标为非 current（若仍是 offset-0/current 语义，此测试必失败）。✓

### 1.2 未关联 permission result — **CLOSED**

**Production（`AcpxSessionStreamProjector.java:123-127`）：**
```java
if (currentObservation && "session/request_permission".equals(method)) {
    var evidence = permissionDetails.remove(wire.requestId());
    if (evidence == null) {
        return Optional.empty();    // 未匹配 result 被忽略，不生成空 evidence
    }
    ...
```
由 `getOrDefault(EMPTY)` 改为 `remove` + null 检查：result 只有匹配到**同一 current occurrence 内观察到的 request-id evidence** 才产出 APPROVED（`selected`+`allow|allow_always`）/ DENIED（`selected`+`reject*`）/ CANCELLED；未匹配 result 一律忽略。

**测试：** `AcpxSessionStreamProjectorTest.currentPermissionResultWithoutCurrentRequestCannotCreateEvidence` —— 历史 request（`currentObservation=false`，被门挡住、未入 `permissionDetails`）+ current result（`true`）→ `isEmpty()`。✓

### 1.3 closure failure signal 保留 named session id — **CLOSED**

**Production（`AcpxAgentTransport.java:843-855`）：** `sessionClosureFailureSignal(closure, sequence, sessionName)` 签名增加 `sessionName` 并填入 AdapterSignal 的 sessionId 字段；两处调用（`:342` 异常路径、`:791` `withSessionClosure`）均传入 `request.sessionName()`。

**测试：** `failedCancelAndCloseRemainFailClosedAsProcessStalled` 断言 `signal.type()==PROCESS_STALLED && "session_closure".equals(source()) && "claude-closure-fails".equals(signal.sessionId())`。✓

---

## 2. 三条 current-stream 测试的改写方式（非放宽 production）

| 测试 | 改写 |
|---|---|
| `returnsCurrentPromptFinalAssistantJsonFromTypedSessionStream`（transport :375） | 显式写 `index.json`（name=`codex-reviewer`、cwd=tempDir、recordId 与 stream 文件一致）；run 内追加内容即 post-baseline current ✓ |
| `processStartBaselineRejectsStalePromptResponseUntilCurrentRequestCompletes`（transport :485） | 预置**陈旧** prompt 内容 + 显式 `index.json` → baseline 落在陈旧内容之后，陈旧 response 被拒 ✓ |
| `capturedLargeStreamKeepsOccurrenceFactsConsistentThroughFinalization`（`AcpxAgentTaskHandlerTest:1566`，+4 行） | 补建 `sessions` 目录 + 显式 `index.json`（name=`claude-main-ws-large-stream`、cwd、recordId `123e4567-…`）✓ |

方向正确：production 变严（`Map.of()`→`null`，fail-closed），测试变显式（建立可信 index 而非依赖被删除的宽松回退）。无任何通过放宽 production 恢复绿测的迹象。另：两条断言已删除权限 authority（exit-5/文本）的旧测试（`acpxPermissionDeniedExitProjectsTypedBlockingSignal`、`stopsWhenPermissionBlockedSignalPersistsEvenIfOutputKeepsGrowing`）被改写为 cannot-pass 形态——与删除的 producer 一致，合法。

---

## 3. Sibling-path sweep（delta 范围内）

- **空 map / offset-0 current replay 旁路**：`sessionStreamBaseline` 仅返回 `null` 或单条目 map；reader 侧 §1.1 穷尽清点无残余。✓
- **raw exit 5 / stderr 权限文本 / 累计 stats**：delta 未触及 catalog（−11 不变）、health（+16 不变）、lifecycle 分类、wireline；上一轮已清点的 producer 集合不变（唯一 current-occurrence stream 来源 + metadata round-trip 映射）。✓
- **current typed request/result 正常路径**：`correlatesCurrentPermissionRequestAndDeniedDecision`、`currentPermissionDenialIsTypedAndAbnormalReturnCancelsExactNamedSession`（含精确 `claude cancel -s claude-current` 命令断言与全字段 evidence）仍在并通过。✓
- **strict completion / finalizer / recovery owner 未改写**：delta 后的 resolver diff 仍为 +2/−1（仅 operatorMessage 拼接）、RecoveryAssessment +2/−1（仅 suggested command 注释）、`AgentRunMetadataFinalizer`/reconciler 不在 diff 中。✓
- **delta 面核对**：相对 WARN 评审的 production 变更仅 transport（null 返回 + sessionName 参数）与 projector（remove+null 检查）两处；其余模块 diff stat 与上轮完全一致 → 无未声明的生产改动。✓

---

## 4. End-to-end Invariant（occurrence-scoped permission authority + failed-turn closure）

| 维度 | 证据 |
|---|---|
| **Declared by** | plan §4.1–4.4；transport 为 occurrence-scoped typed facts 唯一 owner |
| **Proven by** | deterministic cannot-bypass（本 delta 新增 2：missing-index 不回放、未匹配 result 无 evidence；连同上一轮 10 项共 12 项真实边界测试）+ 独立复跑 reactor 1239/0/0/0。fresh real-case 未运行 → **NOT PROVEN** |
| **Consumed by** | `AgentExecutionOutcomeResolver`（blocking/contained + operatorMessage 含 tool action/resource）、`AgentRunMetadataFinalizer`、`RecoveryAssessment`（classification/retrySafe=false/rebuild-first 命令）、reconciler durable 分支（保留 typed signals，含带 session id 的 closure PROCESS_STALLED） |
| **Enforced before** | transport 返回 terminal result 前：baseline 选择（失败即 null→bounded tail 非 current）→ 异常返回 `quiesceSession`（cancel `-s`→bounded→close→typed disposition）→ finalizer |
| **Failure route** | index 缺失/无匹配/解析失败 → bounded tail、历史内容一律非 current、无权限 authority；裸 exit=5/权限文本/累计 stats → 无 authority；当前 occurrence DENIED/CANCELLED + 缺 completion/artifact → PERMISSION_BLOCKED/MANUAL_ONLY；closure 双失败 → PROCESS_STALLED（含 session id）/MANUAL_ONLY |
| **Cannot-bypass test** | `missingSessionIndexCannotReplayHistoricalPermissionDenialAsCurrent`、`currentPermissionResultWithoutCurrentRequestCannotCreateEvidence`、`failedCancelAndCloseRemainFailClosedAsProcessStalled`（sessionId 断言）+ 前轮 10 项 |
| **Residual raw/legacy path** | ① `AgentRunMetadataFinalizer:172` typed-reason round-trip 映射（无裸 producer）；② stop-loss/runtime terminate 无 session cancel（**不在本 delta**）；③ mid-run session-id rollover 观察丢失（**不在本 delta**）；④ `initializeStreamOffset` IOException 竞态 offset-0（既有、实际不可达数据） |

---

## 5. 范围界定（明确不因未处理而扩大本 delta）

- **mid-run session-id rollover**（resume 失败 → session/new，live id 不在单条目 baseline → 本 run 无 stream 观察、触发一次非必需 cancel）：前轮 P2-2，属 baseline 单条目选择的设计代价，fail-closed，**不属于本 delta**，留待后续独立评估。
- **runtime stop-loss closure**（`AgentExecutionAttemptReconciler.terminateInFlightProcesses` 只杀进程树不做 named-session cancel）：前轮 P2-3，owner 在 runtime 侧，**不属于本 delta**。
- 本 delta 未新增 phase、module、framework、registry、engine，未改模型/prompt/strict parser/bounded correction/业务 workflow。

---

## 6. 输出要求逐项回答

1. **Findings**：P0=0，P1=0，P2=0（delta 范围内）。修复点 file:line 见 §1（`AcpxAgentTransport.sessionStreamBaseline` 三处 `return null`；`AcpxSessionStreamProjector.java:124-127`；`AcpxAgentTransport.java:843-855` + `:342/:791`）。
2. **原 P1-1：CLOSED**（三条失败路径均 `null` → bounded tail 非 current；offset-0-as-current 路径穷尽清点后不存在；真实 cannot-bypass 测试覆盖）。
3. **未关联 permission result：CLOSED**（`remove`+null 检查忽略未匹配 result；测试覆盖）。**closure session-id：CLOSED**（signal 携带 `request.sessionName()`；测试断言精确 session id）。
4. **mid-run rollover 与 stop-loss 不属于本 delta**，不因其未处理扩大范围（§5）。
5. **Boundary Review：PASS**。
6. **选择性提交条件：满足** —— production 与测试一致、12 项 cannot-bypass 全绿、reactor 1239/0/0/0、`git diff --check` clean；可提交（代码+测试+三份 docs）获得 clean execution SHA。
7. **只能标 `ENFORCED / NOT PROVEN`**：deterministic 边界已真实强制；未经 fresh real-case（含 captcha-auth fresh workstream 授权）不得标 `PROVEN`。

---

## 7. 独立复跑记录

- `mvn -q -T 1C test`（dual-orchestrator reactor）：exit 0。
- surefire 聚合：**140 suites / 1239 tests / 0 failures / 0 errors / 0 skipped**（=声明基线；较上轮 +2 恰为两条新 cannot-bypass 测试）。
- `git diff --check`：clean。
- 未调用 provider、未运行 real-case；未修改、未提交任何文件。

---

*本报告为只读同行评审证据。*
