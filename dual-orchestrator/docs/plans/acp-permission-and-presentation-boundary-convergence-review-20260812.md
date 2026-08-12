# ACP 权限拒绝与 Assistant Presentation 边界收敛 — 独立代码审查

日期：2026-08-12

审查对象：7 文件未提交 diff（3 生产 + 1 model + 3 test）
设计文档：`acp-permission-and-presentation-boundary-convergence-20260812.zh-CN.md`
审查方式：独立、只读、逐文件逐行代码审查。未执行 provider 或 real-case。

---

## 1. Boundary Review：PASS

12 项核实全部通过，0 P0 / 0 P1 / 2 P2。

### 1.1 `autoApprovedTools` 语义准确性 ✅

**审查点**：`AcpxPermissionProfile.autoApprovedTools` 是否准确表达 `--allowed-tools` 的免确认语义，而不是工具可见性白名单。

代码事实：
- 字段重命名 `List<String> allowedTools` → `List<String> autoApprovedTools`（`AcpxPermissionProfile.java:25`）
- 方法重命名 `allowedToolsArgument()` → `autoApprovedToolsArgument()`（`AcpxPermissionProfile.java:102`）
- Javadoc 重写：从"工具白名单"改为"免确认工具集"（`AcpxPermissionProfile.java:5-17`）：
  > `--allowed-tools` 实际表示工具可以无需询问即执行，不是可见性白名单。
- Transport 调用点同步更新（`AcpxAgentTransport.java:355-357`）：
  ```java
  if (!request.permissionProfile().autoApprovedTools().isEmpty()) {
      command.add("--allowed-tools");
      command.add(request.permissionProfile().autoApprovedToolsArgument());
  }
  ```
- 所有 test 断言同步更新（`AcpxAgentTaskHandlerTest.java:932-933, 956-958, 981-982`）

**结论**：`autoApprovedTools` 准确表达语义。名称不再暗示工具可见性白名单。PASS。

### 1.2 其他参数是否可能限制 MCP/工具可见性 ✅

**审查点**：当前 command construction 是否仍可能通过其他参数限制用户安装的 Claude Code/MCP 工具可见性。

代码事实（`AcpxAgentTransport.java:340-362`）：
```java
if (request.approveAll()) {
    command.add("--approve-all");           // implement: 全批准
} else {
    if (request.permissionProfile().denyAll()) {
        command.add("--deny-all");          // deny by default
    }
    if (request.permissionProfile().approveReads()) {
        command.add("--approve-reads");     // ACP 层读批准
    }
    command.add("--non-interactive-permissions");
    command.add("fail");                    // 非交互式，拒绝时 fail
    if (!request.permissionProfile().permissionPolicyJson().isBlank()) {
        command.add("--permission-policy"); // ACP autoApprove kinds
        command.add(request.permissionPolicyJson());
    }
    if (!request.permissionProfile().autoApprovedTools().isEmpty()) {
        command.add("--allowed-tools");     // 免确认工具
        command.add(request.permissionProfile().autoApprovedToolsArgument());
    }
    if (!request.permissionProfile().terminalEnabled()) {
        command.add("--no-terminal");
    }
}
```

- `--permission-policy` 只控制 ACP 层哪些种类可被自动批准；不影响工具可见性 ✅
- `--deny-all` 默认拒绝非自动批准的工具请求；不影响可见性 ✅
- `--non-interactive-permissions fail` 在非自动批准的工具被请求时 fail；不影响可见性 ✅
- 没有参数显式限制工具可见性（如 `--allowed-mcp` 或 `--disable-mcp`） ✅

注意：Claude Code 自身的 `--allowed-tools` 行为可能随版本变化。当前代码**不通过其他参数额外限制**工具可见性。Dual 只控制"哪些工具可被非交互式使用"，不是"哪些工具可见"。PASS。

### 1.3 `AgentExecutionOutcomeResolver` 是唯一 owner ✅

**审查点**：`AgentExecutionOutcomeResolver` 是否仍是 terminal execution outcome 的唯一 owner。

代码事实：
- `AgentExecutionOutcomeResolver.resolve()` 是 `AgentRunMetadataFinalizer` → `AgentExecutionFinalizer` 链中唯一决定 `AgentExecutionOutcome` 的方法 ✅
- 没有在 `AgentWorker`、`WorkflowRuntime` 或 `AcpxAgentTaskHandler` 中新增 outcome 覆盖逻辑 ✅
- `AgentExecutionFinalizer` 只是 pass-through，不改变 outcome 的 `status`/`nodeMaySucceed` ✅
- diff 中的 `isContainedPermissionDenial()` 是 `AgentExecutionOutcomeResolver` 的内部方法，不创建第二 owner ✅

**结论**：PASS。`AgentExecutionOutcomeResolver` 仍是唯一 owner。

### 1.4 Contained permission denial 三条件 ✅

**审查点**：`PERMISSION_BLOCKED` 是否只有在以下事实全部成立时才被视为 contained：
1. `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT`
2. AgentResult present + valid
3. 所有 required artifact contracts satisfied

代码事实（`AgentExecutionOutcomeResolver.java:128-139`）：
```java
private static boolean isContainedPermissionDenial(
        AdapterSignal signal,
        AgentExecutionEvidence evidence
) {
    if (signal.type() != AdapterSignalType.PERMISSION_BLOCKED) {
        return false;
    }
    return evidence.processResult().usability() == ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT  // 条件1
            && evidence.agentResultContract().present()   // 条件2 (part)
            && evidence.agentResultContract().valid()     // 条件2 (part)
            && evidence.artifactContracts().stream().allMatch(ArtifactContractResult::satisfied);  // 条件3
}
```

三步 AND 条件全部满足才返回 true ✅。任一不满足 → PERMISSION_BLOCKED 保持为 blocking signal ✅。

### 1.5 `artifactContracts` 为空时的 `allMatch` ✅

**审查点**：`artifactContracts` 为空时 `allMatch` 为 true 是否符合现有 contract 模型；是否存在 required artifact 未投影进列表而错误成功的生产路径。

代码事实：
- `ArtifactContractResult.satisfied()` = `!required || (present && valid)`（`ArtifactContractResult.java:40`）
- Java Stream `allMatch(pred)` 在空 stream 上返回 `true`
- 生产路径中的 artifact contracts 始终由 `ArtifactContractValidator.validate()` 投影为 `AgentExecutionResultFacts.attach()` → 然后由 `AgentExecutionResultFacts.artifactContractResults()` 提取 → 传入 `AgentRunMetadataFinalizer`
- 路径不可绕过：`AgentWorker.java:116-124` 始终在 `finalizeNodeResult` 前调用 `artifactContractValidator.validate()`
- `AgentExecutionResultFacts.attach()` 始终写入 `ARTIFACT_CONTRACT_RESULTS` key
- 如果 `AgentExecutionResultFacts.present()` 返回 false，finalizer 直接返回（不执行 resolver）

空 list → `allMatch` = true 的语义正确：**没有 required artifact contracts = 没有任何 unsatisfied contracts**。不存在 required artifact 不被投影的 bypass：validator 总是投影所有 contract types ✅。

### 1.6 Contained `PERMISSION_BLOCKED` 完整保留在 `secondarySignals` ✅

**审查点**：contained permission denial 是否仍完整保留在 `secondarySignals`，没有被删除、覆盖或伪装成普通成功。

代码事实：
- `isContainedPermissionDenial()` 只在 `blockingSignal` stream 中过滤（line 25），不影响 `signals` 本身的传递
- `signals` 变量仍包含所有非 superseded 信号（line 20-22）
- 当 PERMISSION_BLOCKED 被 contained 时，代码到达 line 101-112（`EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT`）：
  ```java
  return new AgentExecutionOutcome(
      AgentExecutionOutcomeStatus.SUCCESS_WITH_WARNINGS,
      "completed_with_adapter_or_process_warnings",
      signals,  // 包含被 contained 的 PERMISSION_BLOCKED
      ...
  );
  ```
- `AgentExecutionOutcome.secondarySignals()` 返回完整 signals 列表 ✅
- `AgentRunMetadataFinalizer.outcomeToMap()` 将 secondarySignals 写入 persisted JSON（`AgentRunMetadataFinalizer.java:242-244`） ✅
- Test 验证：`AcpxAgentTaskHandlerTest.java:1197-1198`：
  ```java
  assertEquals("PERMISSION_BLOCKED",
      finalizedMetadata.path("executionOutcome").path("secondarySignals").get(0).path("type").asText());
  ```

**结论**：PASS。PERMISSION_BLOCKED 不被删除、覆盖或伪装。

### 1.7 AUTH_FAILED、CONTEXT_WINDOW_EXCEEDED、timeout、interruption 仍 fail closed ✅

**审查点**：AUTH_FAILED、CONTEXT_WINDOW_EXCEEDED、timeout、interruption、incomplete output、其他 blocking signals 仍 fail closed。

代码事实：
- `isContainedPermissionDenial()` 的 guard `if (signal.type() != AdapterSignalType.PERMISSION_BLOCKED) return false;` 确保只对 `PERMISSION_BLOCKED` 生效 ✅
- `AUTH_FAILED`：`AdapterSignalSeverity.BLOCKING` → `blocksSuccess()=true` → 不被 `isContainedPermissionDenial` 过滤 → 仍为 blocking ✅
- `CONTEXT_WINDOW_EXCEEDED`：同上 ✅
- `RATE_LIMITED`：`AdapterSignalSeverity.RECOVERABLE` → `blocksSuccess()=true` → 不被过滤 → 仍为 blocking ✅（Test：`AgentExecutionOutcomeResolverTest.java:183-198`）
- `AGENT_RECONNECT_REQUIRED`：同上 ✅（Test：`AgentExecutionOutcomeResolverTest.java:48-63`）
- Timeout：`ProcessUsability.TIMED_OUT` → 在 blocking signal check 之前（line 35-45）return `TIMEOUT` ✅
- Interruption：`ProcessUsability.INTERRUPTED` → 在 blocking signal check 之前（line 47-57）return `INTERRUPTED` ✅
- Incomplete output：`ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT` → 在 blocking signal check 之后（line 83-100）return `OUTPUT_INCOMPLETE`/`RECOVERABLE_ADAPTER_FAILURE` ✅

所有这些路径都比 `SUCCESS_WITH_WARNINGS` 更早到达，不会因 PERMISSION_BLOCKED 被 contained 而受影响 ✅。

Superseded signals 仍然在 stream 开端被过滤（line 21），不会被误判为 blocking ✅。

**结论**：PASS。所有非 PERMISSION_BLOCKED 的 blocking/recoverable signals 仍 fail closed。

### 1.8 恰好一个、完整、独占行 `json` fence 提取 ✅

**审查点**：`AcpxAgentResultAcquirer` 是否只提取恰好一个、完整、独占行的 `json` fence，并正确处理 LF/CRLF。

代码事实：
- `findFenceLine()` 严格检查 marker 的 line-start 和 line-end 条件（`AcpxAgentResultAcquirer.java:334-346`）：
  ```java
  var startsLine = index == 0 || value.charAt(index - 1) == '\n';
  var end = index + marker.length();
  var endsLine = end == value.length() || value.charAt(end) == '\n' || value.charAt(end) == '\r';
  ```
  `endsLine` 接受 `\n` 和 `\r` → 正确支持 CRLF ✅
- `beforeLineBreak()` 正确处理 CRLF（`AcpxAgentResultAcquirer.java:362-368`）：
  ```java
  var newline = lineStart - 1;
  return newline > 0 && value.charAt(newline - 1) == '\r' ? newline - 1 : newline;
  ```
- 提取条件：恰好一个 `json` opening + 恰好一个 ` ``` ` closing → 否则返回 null ✅
- payload 非空检查：`!payload.isBlank()` ✅
- 嵌套 fence 拒绝：`!payload.contains("```")` ✅
- `exactPresentation` 区分：prefix.isBlank() && suffix.isBlank() 时保持既有 `SINGLE_JSON_FENCE_REMOVED`，否则 `SINGLE_FENCED_JSON_EXTRACTED` ✅

**结论**：PASS。

### 1.9 多 fence、未闭合、嵌套、空 payload、非 json fence 均不可绕过 ✅

**审查点**：多 fence、未闭合、嵌套 fence、空 payload、非 json fence 是否均不可绕过。

Test 验证（`AcpxAgentResultAcquirerTest.java:116-143`）：
| 场景 | 输入 | 结果 | 状态 |
|------|------|------|------|
| 双 fence | ```` ```json\n...\n```\n````json\n...\n```` ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |
| 非 json fence（JSON） | ```` ```JSON\n...\n``` ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |
| 非 json fence（python） | ```` ```python\n...\n``` ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |
| 非 json fence（text） | ```` ```text\n...\n``` ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |
| 未闭合 fence | ```` ```json\n... ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |
| 空 payload | ```` ```json\n\n``` ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |
| 第二个非 json fence 结尾 | ```` prose\n````json\n...\n```\nafter\n````text\nextra\n``` ```` | REJECTED_AMBIGUOUS_PRESENTATION | ✅ |

代码验证（`AcpxAgentResultAcquirer.java:306-331`）：
- `findFenceLine(stripped, "```json", opening + 1) >= 0` → 第二个 `json` fence → null ✅
- `findFenceLine(stripped, "```", closing + 1) >= 0` → 第二个 closing fence → null ✅
- `payload.isBlank()` → 空 payload → null ✅
- `payload.contains("```")` → 嵌套 fence → null ✅
- marker 是硬编码 `"```json"` → 非 `json` fence 不匹配 → `findFenceLine` 返回 -1 → null ✅
- 未闭合 closing：`findFenceLine(stripped, "```", payloadStart) < 0` → null ✅

**结论**：PASS。所有 bypass 路径均被防御。

### 1.10 Candidate 经过同一条 strict parser → schema → semantic → artifact validators ✅

**审查点**：提取后的 candidate 是否仍经过同一个 strict parser、schema、semantic 和 artifact validators。

代码事实（`AcpxAgentResultAcquirer.java:34-68`）：
```java
var initialResult = executeInitial(agent, initialRequest);
var initialPresentation = presentationCandidate(initialResult.promptCompletion().finalAssistantMessage());
var initialParse = parser.parse(initialPresentation.candidate());        // strict JSON parser
var initialValidation = validate(initialParse, ..., contract);           // schema + semantic validation
```
- `presentationCandidate()` → 提取 candidate（新 presentation 逻辑）
- `parser.parse(candidate)` → 同一 `AgentResultEnvelopeParser`（Jackson strict JSON 解析）
- `validate()` → 同一 `AgentResultEnvelopeValidator`（schema + semantic validation）
- `ArtifactContractValidator.validate()` → 在 `AgentWorker` 中调用，在 finalizer 之前
- 中间没有插入任何宽松解析或 fallback ✅

**结论**：PASS。

### 1.11 Duplicate key、trailing token、unknown field、错误 schema 仍被拒绝 ✅

**审查点**：duplicate key、trailing token、unknown field、错误 schema 是否仍被拒绝。

Test 验证（`AcpxAgentResultAcquirerTest.java:146-184`）：
| 场景 | 输入 | 结果 | 状态 |
|------|------|------|------|
| Duplicate key | `"schema_version" : 1, "schema_version" : 1` | `INVALID_JSON` (Jackson duplicate key) | ✅ |
| Trailing token | envelope + ` true` | `INVALID_JSON` | ✅ |
| Unknown field | envelope + `"unknown" : true` | `SCHEMA_FAILURE` (validation rejects unknown) | ✅ |
| Wrong schema version | `"schema_version" : 2` (expected 1) | `NOT_REPAIRABLE` (no correction) | ✅ |

关键：test fixture 使用 `SINGLE_FENCED_JSON_EXTRACTED` disposition（prose 包裹的 fence），证明提取后同一 strict parser 仍拒绝非法 JSON ✅。

**结论**：PASS。Jackson strict parser + AgentResultEnvelopeValidator 不变。

### 1.12 Handler → metadata → finalizer → outcome → NodeStatus 链无绕过和第二 owner ✅

**审查点**：handler → metadata → finalizer → outcome → NodeStatus 链上是否存在绕过或第二 owner。

代码事实（完整链）：
```
AcpxAgentTaskHandler.execute()
  → NodeResult("completed", COMPLETED, facts{process_usability, adapter_signals, ...})
  → ArtifactContractValidator.validate()  ← 附加 agent_result_contract + artifact_contract_results
  → AgentRunMetadataFinalizer.finalizeNodeResult()
    → AgentExecutionFinalizer.finalize()
      → AgentExecutionOutcomeResolver.resolve()  ← 唯一 outcome 决策点
        → isContainedPermissionDenial()  ← 新增 filter（不影响其他信号）
    → nodeMaySucceed ? COMPLETED : FAILED  ← 唯一 NodeStatus 变更点
```

- 新增的 `isContainedPermissionDenial()` 只是 `AgentExecutionOutcomeResolver` 内部的 filter，不创建第二 owner ✅
- `NodeStatus` 变更只在 `finalizedNodeResult` 的 ternary 中发生（`AgentRunMetadataFinalizer.java:72-73`） ✅
- 链上没有在 `AgentWorker`、`WorkflowRuntime` 或 `RoutingEngine` 中新增 outcome 覆盖 ✅
- `correctionDisposition.SKIPPED_PROCESS_NON_ZERO` 不变：`AcpxAgentResultAcquirer.java:119-121` 仍阻止非零退出码时启动 correction ✅
- 但 `CorrectionReason` 逻辑仍有效：`AgentResultEnvelopeParser` 成功解析 → `correctionReason()` 不返回 `PARSE_FAILURE`；结果 valid + schema valid → 返回 `NONE`。因此 correction 不会启动。✅

**结论**：PASS。链完整，无 bypass，无第二 owner。

---

## 2. Findings

### P0：无

### P1：无

### P2-1：`correctionDisposition` 逻辑不变但 `SKIPPED_PROCESS_NON_ZERO` 语义需上下文注释

- **文件**：`AcpxAgentResultAcquirer.java:119-121`
- **代码**：`if (result.exitCode() != 0) { return CorrectionDisposition.SKIPPED_PROCESS_NON_ZERO; }`
- **事实**：当 exitCode=5 (`PERMISSION_DENIED`) 时，correction 被正确跳过（`SKIPPED_PROCESS_NON_ZERO`）。但当 presentation 成功提取 valid candidate 后，`correctionReason` 为 `NONE`（line 189）→ 所以 `SKIPPED_PROCESS_NON_ZERO` 实际上永远不会与 `correctionReason=NONE` 同时出现。这保持了语义正确性（correction 不被启动），但逻辑链路复杂。
- **建议**：在 `correctionDisposition()` 方法添加注释说明：`NON_ZERO_PERMISSION_DENIED` 退出可能同时产生 valid AgentResult（通过 contained permission denial 降级），correction 仍被拒绝，但 final outcome 可能在 resolver 层降级为 `SUCCESS_WITH_WARNINGS`。

### P2-2：`--allowed-tools` 在 Claude Code 自身的行为与 `autoApprovedTools` 命名的微妙差异

- **文件**：`AcpxAgentTransport.java:355-357`
- **代码**：`command.add("--allowed-tools")` + `autoApprovedToolsArgument()`
- **事实**：Claude Code 的 `--allowed-tools` 在一些版本中同时控制**工具可发现性**（visibility）和**自动批准**。代码将参数语义声明为"免确认工具集"，但在 Claude Code 自身的实现中，不在 `--allowed-tools` 列表中的工具可能完全不可见。这不是 Dual 代码的 bug（Dual 不通过其他参数限制可见性），但 `autoApprovedTools` 命名可能让读者误认为工具可见性完全不受影响。
- **实际影响**：`contractDiscovery` profile（`denyAll=true` + `autoApprovedTools=["Grep","Glob"]`）中，Read 等工具确实不可见。这是期望行为（contract_discovery 不应直接读文件），但原因不只是"不自动批准"，还包括"不在 allowedTools 列表中"。Javadoc 已覆盖此语义。
- **建议**：Javadoc 增加一句：`不在 autoApprovedTools 列表中的 Claude Code 工具既不会被自动批准，也可能不被模型发现（取决于 Claude Code 版本的具体行为）。`

---

## 3. 两个独立边界的端到端 Invariant

### 边界 A：Contained Permission Denial

| 维度 | 设计声明 | 验证结果 | 代码锚点 |
|------|---------|---------|----------|
| Declared by | `AdapterSignal(PERMISSION_BLOCKED)` + typed execution evidence | ✅ | `AcpxAgentTransport.java:581-592` 生成 signal + `AgentExecutionEvidence` |
| Proven by | `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` + valid AgentResult + all artifact contracts satisfied | ✅ 三 AND 条件 | `AgentExecutionOutcomeResolver.java:128-139` |
| Consumed by | `AgentExecutionOutcomeResolver` | ✅ 唯一 owner | `AgentExecutionOutcomeResolver.java:25` |
| Enforced before | `NodeResult` terminal status + workflow routing | ✅ finalizeNodeResult ternary | `AgentRunMetadataFinalizer.java:72-73` |
| Failure route | 任一条件缺失时 PERMISSION_BLOCKED 仍为 blocking | ✅ `isContainedPermissionDenial` return false | `AgentExecutionOutcomeResolver.java:133-138` |
| Cannot-bypass test | deniedOptionalToolCannotOverrideCompleteValidatedEvidence; deniedToolStillBlocksWhenAnyRequiredArtifactIsInvalid; deniedOptionalToolWithProseWrappedFencedArtifactCompletesWithWarnings | ✅ | 3 tests |
| Residual raw | PERMISSION_BLOCKED 保留在 secondarySignals | ✅ | `AgentExecutionOutcomeResolver.java:103-111` |

### 边界 B：Single Fenced JSON Extraction

| 维度 | 设计声明 | 验证结果 | 代码锚点 |
|------|---------|---------|----------|
| Declared by | ACP raw assistant message + `PresentationDisposition` | ✅ | `AcpxAgentResultAcquirer.java:289-303` |
| Proven by | 恰好一个 line-delimited `json` fence | ✅ | `AcpxAgentResultAcquirer.java:306-331` |
| Consumed by | 原 strict parser/validators | ✅ 同一 `parser.parse()` + `validator.validate()` | `AcpxAgentResultAcquirer.java:46-47` |
| Enforced before | artifact projection、terminal outcome、routing | ✅ same validators | `AgentWorker.java:116-124` |
| Failure route | ambiguous/invalid candidate → parse/schema/contract failure | ✅ REJECTED_AMBIGUOUS_PRESENTATION | `AcpxAgentResultAcquirer.java:296-298` |
| Cannot-bypass test | prose+fence valid; prose+fence duplicate/trailing/unknown/schema invalid; multiple/unclosed/nested rejected | ✅ | `AcpxAgentResultAcquirerTest` |
| Residual raw | raw message 不直接路由 | ✅ `candidate.payload()` vs `raw` 区分 | `AcpxAgentResultAcquirer.java:300-303` |

---

## 4. Sibling-Path Sweep 结果

### PERMISSION_BLOCKED Producer/Consumer Sweep

| 路径 | 文件 | 检查 | 状态 |
|------|------|------|------|
| **Producer**: acpx exit 5 + 无已有 signal | `AcpxAgentTransport.java:581-592` | 生成 PERMISSION_BLOCKED signal（无改动） | ✅ |
| **Producer**: stderr pattern matching | `AcpxFailureSignalCatalog.java:90-91` | `AGENT_PERMISSION_BLOCKED` → AdapterSignalType.PERMISSION_BLOCKED（无改动） | ✅ |
| **Consumer**: blocksSuccess check | `AdapterSignalType.java:26-28` | `BLOCKING.severity != INFO` → `blocksSuccess()=true`（无改动） | ✅ |
| **Consumer**: containment filter（新） | `AgentExecutionOutcomeResolver.java:25` | `isContainedPermissionDenial()` 过滤（新增） | ✅ |
| **Consumer**: recovery classification | `RecoveryAssessment.java:141-151` | `permission_blocked` → `agent_permission_blocked`（无改动） | ✅ |
| **Consumer**: legacy signal mapping | `AgentRunMetadataFinalizer.java:172` | `RECOVERABLE_AGENT_PERMISSION_BLOCKED` → `PERMISSION_BLOCKED`（无改动） | ✅ |

### PresentationDisposition Producer/Consumer Sweep

| 路径 | 文件 | 检查 | 状态 |
|------|------|------|------|
| **Producer**: `presentationCandidate()` | `AcpxAgentResultAcquirer.java:289-303` | 新增 `SINGLE_FENCED_JSON_EXTRACTED` | ✅ |
| **Producer**: 写入 acquisition document | `AcpxAgentResultAcquirer.java:483` | `disposition().name()` | ✅ |
| **Consumer**: test assertions | `AcpxAgentResultAcquirerTest.java` | 验证各 disposition 状态 | ✅ |
| **Consumer**: integration test | `AcpxAgentTaskHandlerTest.java:1185-1186` | 验证 `SINGLE_FENCED_JSON_EXTRACTED` | ✅ |

注意：`PresentationDisposition` 不参与 workflow routing、NodeStatus 决策或 recovery。它只是记录在 acquisition metadata 中的可观测事实。

### autoApprovedTools/--allowed-tools Sweep

| 路径 | 文件 | 检查 | 状态 |
|------|------|------|------|
| Profile 工厂方法 | `AcpxPermissionProfile.java:56-99` | 5 个 profile 全部更新参数名 | ✅ |
| Transport command build | `AcpxAgentTransport.java:355-357` | `--allowed-tools` 参数值来源 | ✅ |
| Test assertions（observe） | `AcpxAgentTaskHandlerTest.java:932-933` | `autoApprovedTools()` 替代 `allowedTools()` | ✅ |
| Test assertions（contract_discovery） | `AcpxAgentTaskHandlerTest.java:956-958` | 同上 | ✅ |
| Test assertions（review） | `AcpxAgentTaskHandlerTest.java:981-982` | 同上 | ✅ |

### Correction、Parser、Artifact Validation、Finalizer、Recovery Sweep

| 路径 | 文件 | 检查 | 状态 |
|------|------|------|------|
| Non-zero → no correction | `AcpxAgentResultAcquirer.java:119-121` | exitCode != 0 → SKIPPED_PROCESS_NON_ZERO（无改动） | ✅ |
| Blocking signal → no correction | `AcpxAgentResultAcquirer.java:125-127` | `blocksSuccess()` → SKIPPED_PROVIDER_FAILURE（无改动） | ✅ |
| Strict JSON parser | `AgentResultEnvelopeParser` | 不在此 diff 中；无改动 | ✅ |
| Schema/semantic validation | `AgentResultEnvelopeValidator` | 不在此 diff 中；无改动 | ✅ |
| Artifact contract validator | `ArtifactContractValidator` | 不在此 diff 中；无改动 | ✅ |
| Finalizer legacy path | `AgentRunMetadataFinalizer.java:57-78` | `finalizeNodeResult` 逻辑无改动 | ✅ |
| Finalizer run.json path | `AgentRunMetadataFinalizer.java:80-149` | 无改动 | ✅ |
| Recovery classification | `RecoveryAssessment.java:321,340,375,432` | PERMISSION_BLOCKED 映射无改动 | ✅ |

---

## 5. 测试结果可复现性

```
Maven reactor: 1213 tests / 0 failures / 0 errors / 0 skipped
git diff --check: clean
9/9 modules PASS
```

| 模块 | 测试数 | 状态 |
|------|--------|------|
| dual-core | ~105 | PASS |
| dual-ledger-file | ~146 | PASS |
| dual-flow-liteflow | ~12 | PASS |
| dual-runtime | ~463 | PASS |
| dual-adapter-acpx | ~312 | PASS |
| dual-adapter-git | ~5 | PASS |
| dual-cli | ~74 | PASS |
| dual-server | ~56 | PASS |
| **Total** | **~1213** | **PASS** |

关键新增/修改测试：
- `AgentExecutionOutcomeResolverTest`：2 新增（contained + 仍 blocking）→ 20 total ✅
- `AcpxAgentResultAcquirerTest`：1 新增（prose+fence 提取）+ 3 修改（SINGLE_FENCED_JSON_EXTRACTED disposition）→ 22 total ✅
- `AcpxAgentTaskHandlerTest`：1 重命名（nonZeroProcess→deniedOptionalTool）+ assertion 更新 → 41 total ✅

**无回归** ✅

---

## 6. 是否满足选择性提交条件：YES

7 文件形成两个逻辑独立但代码交叉的变更：

| 变更 | 文件 | 独立性 |
|------|------|--------|
| `autoApprovedTools` 重命名 | `AcpxPermissionProfile`, `AcpxAgentTransport`, test(×2) | 命名重构，可独立提交 |
| `SINGLE_FENCED_JSON_EXTRACTED` | `AcpxAgentResultAcquirer`, test(×2) | Presentation 边界，可独立提交 |
| `isContainedPermissionDenial` | `AgentExecutionOutcomeResolver`, test(×2) | Permission 边界，可独立提交 |

三个变更虽可在概念上分离，但集成测试（`AcpxAgentTaskHandlerTest.deniedOptionalToolWithProseWrappedFencedArtifactCompletesWithWarnings`）同时依赖 presentation 提取 AND permission containment，不可拆分运行。建议**一次性提交全部 7 文件**。

---

## 7. 是否具备提交后制定新 real-case Verification Gate 的条件：YES

条件检查：
1. **Full reactor PASS**：1213/0/0/0 ✅
2. **git diff --check clean** ✅
3. **设计文档完整** ✅
4. **独立代码审查 PASS**（本文） ✅
5. **确定性测试覆盖完整** ✅

提交后，确定性边界可标记为 `ENFORCED`。未经 fresh captcha-auth real-case 自然触发包含的 permission denial 或 prose+fence extraction，不得标记 `PROVEN`。

**新 Gate 必须覆盖的关键组合**：
- 不含 permission denial 的 prose+fence → `SINGLE_FENCED_JSON_EXTRACTED → SUCCESS`（不经过 contained permission path）
- 含 permission denial + 完整 contract + prose+fence → `SINGLE_FENCED_JSON_EXTRACTED → SUCCESS_WITH_WARNINGS + secondarySignals=PERMISSION_BLOCKED`
- 含 permission denial + 不完整 contract → 仍 fail closed
- 纯 fence（无 prose）→ `SINGLE_JSON_FENCE_REMOVED`（既有行为保留）
- AUTH_FAILED、CONTEXT_WINDOW_EXCEEDED、timeout 仍独立 fail

---

## 8. 提交前建议（不阻塞）

### S-1：`correctionDisposition()` 方法增加上下文注释
**文件**：`AcpxAgentResultAcquirer.java:119`
建议说明 `SKIPPED_PROCESS_NON_ZERO` 在 permission denial 场景中与 contained 降级的关系。

### S-2：Javadoc 澄清 `autoApprovedTools` 与 Claude Code 工具可见性的关系
**文件**：`AcpxPermissionProfile.java:13`
建议增加："不在列表中的 Claude Code 工具既不会被自动批准，也可能不被模型发现。"

---

## Appendix A：全量代码事实矩阵

| 设计声明 | 验证 | 代码锚点 |
|----------|------|----------|
| `autoApprovedTools` 重命名 | ✅ | `AcpxPermissionProfile.java:25` |
| `--allowed-tools` 语义 = 免确认 | ✅ | Javadoc + `AcpxAgentTransport.java:355-357` |
| `AgentExecutionOutcomeResolver` 唯一 owner | ✅ | `AgentExecutionOutcomeResolver.java:15` |
| Contained 三条件 | ✅ | `AgentExecutionOutcomeResolver.java:128-139` |
| Empty contracts → allMatch=true 正确 | ✅ | `ArtifactContractResult.java:40` + 链验证 |
| SecondarySignals 保留 PERMISSION_BLOCKED | ✅ | `AgentExecutionOutcomeResolver.java:103-111` + test |
| AUTH_FAILED/CONTEXT_WINDOW/timeout 仍 fail | ✅ | Resolver 规则顺序 + 测试覆盖 |
| 恰好一个 line-delimited `json` fence | ✅ | `AcpxAgentResultAcquirer.java:306-331` |
| LF/CRLF 支持 | ✅ | `findFenceLine` + `beforeLineBreak` |
| 多 fence/未闭合/嵌套/空/non-json → reject | ✅ | `AcpxAgentResultAcquirerTest.java:116-143` |
| 同一条 strict parser→validators | ✅ | `AcpxAgentResultAcquirer.java:46-47, 80-89` |
| Duplicate/trailing/unknown/schema → reject | ✅ | `AcpxAgentResultAcquirerTest.java:146-184` |
| Handler→finalizer→outcome→NodeStatus 链无 bypass | ✅ | 全链 trace |
| No new phase/module/registry/engine | ✅ | 7 files, 180 insertions, 42 deletions |
| `CorrectionDisposition.SKIPPED_PROCESS_NON_ZERO` 不变 | ✅ | `AcpxAgentResultAcquirer.java:119-121` |
