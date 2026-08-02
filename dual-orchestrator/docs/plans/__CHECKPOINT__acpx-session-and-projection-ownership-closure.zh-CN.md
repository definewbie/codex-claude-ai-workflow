# __CHECKPOINT__ ACPX Session 与 Projection Ownership 收敛设计

状态：`IMPLEMENTATION PASS / ENFORCED + DETERMINISTICALLY PROVEN / T6 REAL-CASE PROOF PENDING`

目标优先级：`P0 mainline stability`

日期：2026-08-02

## 1. 目标与边界

T6 v3 已证明 execution fact 主链内部一致，但同时暴露了一个 transport lifecycle
缺口和三个 projection 缺口。本 checkpoint 的目标是一次收敛这些已证明的问题：

```text
阻塞主链：Codex named session 首次运行前没有 bootstrap owner

投影漂移：
1. executionOutcome.retrySafe 与 operator retrySafe 同名不同义
2. RecoveryAssessment 已分类，但 WorkstreamAttention 再次维护不完整分支
3. Codex 未连接 provider 时错误回退到 Claude provider/model 配置
```

本 checkpoint 不重新设计 execution model。`ProcessResult`、`AdapterSignal`、
`AgentExecutionOutcome`、`RecoveryAssessment` 和 `WorkstreamAttention` 继续作为现有
边界对象。实施目标是补全 owner、删除重复派生字段和错误 fallback，而不是增加平台层。

明确不做：

- 不修改 prompt、strict parser、bounded correction 或 workflow definition；
- 不从 stderr 文本识别 `No acpx session found`；
- 不新增 session registry、projection framework、policy engine、event bus 或新 module；
- 不把 provider/model 用于 workflow routing；
- 不运行 real-case，直到 deterministic gates、full reactor 与独立 review 全部 PASS。

## 2. T6 已确认的事实

T6 workstream：`ws-20260802T002001Z-36TY`。

失败 run：`run-1785630089016`。

```text
agent = codex
duration = 117 ms
exitCode = 4
transcript = empty
tokens = 0
stderr = No acpx session found ...
adapterSignals = []
processResult.usability = EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT
correctionDisposition = SKIPPED_PROCESS_NON_ZERO
executionOutcome = OUTPUT_INCOMPLETE
workflow = WAITING_FOR_RECOVERY
```

该样本应判定为 `FAIL`，不是 `INVALID`：provider 没有被调用，但 Dual-owned process、
correction、outcome 与 workflow facts 已经产生并可判断。execution fact ownership 没有
出现成功/失败矛盾，因此前一 checkpoint 保持 `ENFORCED / NOT YET PROVEN`。

## 3. 根因与当前调用链

### 3.1 Session lifecycle 缺口

Claude 路径：

```text
runClaudeDesign(request)
  -> permissionProfile.requiresFreshSession()
  -> ensureSession("claude", request)
  -> run("claude", request)
```

Codex 路径：

```text
runCodexDesignReview(request)
  -> run("codex", request)
  -> exit 4 before ACP session/wire exists
  -> isMissingSession(request, result)
       requires active typed SESSION_RESUME_FAILED
  -> adapterSignals is empty
  -> ensureSession("codex", request) is never called
```

安装版本的本地 CLI contract 已确认：

```text
acpx codex sessions ensure --help
  Ensure a session exists for current cwd or ancestor

acpx codex sessions new --help
  Create a fresh session for current cwd
```

上述 help 只形成待验证的外部 contract 假设：`ensure` 应为 create-if-missing 且对已存在
session 幂等；`new` 应创建 fresh session。实现前必须读取当前安装 acpx 0.11.0 的实际
command implementation/type contract 证明该语义；fake-acpx process test 只证明 Dual 的
命令顺序与分支，不得被描述为对外部 acpx 行为的证明。

### 3.2 Projection 漂移

当前存在三条不同性质的派生路径：

```text
AgentExecutionOutcome.retryPolicy
  -> run.json executionOutcome.retrySafe = retryPolicy != NONE

RecoveryAssessment.retryReadiness
  -> WorkstreamAttentionEvaluator 按 classification 再次决定 retrySafe

AcpxUsage provider/model 为空
  -> runtimeDefault()
  -> Claude settings/env
  -> Codex run 被投影为 claude_settings_env / zhipu / glm
```

第一条产生冗余且含混的 boolean；第二条使已有 classification 可以落入 generic
`FAILED`；第三条跨 agent 猜测未观测到的事实。

## 4. 收敛后的 owner 模型

| Fact | 唯一 owner | 合法消费者 | 禁止行为 |
|---|---|---|---|
| named session 是否在 prompt 前可用 | `AcpxAgentTransport` | transport run/rebuild | handler、recovery 或 stderr classifier 补建 session |
| process/completion/signals | `AcpxRunResult` | acquirer/finalizer | projection 重读 raw logs |
| terminal execution outcome | `AgentRunMetadataFinalizer` 委托 core resolver | runtime/recovery/projection | writer 或 UI 重算 |
| retry policy | `AgentExecutionOutcome.retryPolicy` | recovery assessment、run.json | 再派生含混的 outcome `retrySafe` |
| operator recovery classification/readiness/retry safety | `RecoveryAssessment` | `WorkstreamAttentionEvaluator`、CLI/API/report | projection 根据 reason/node id 或 classification branch 重新判断 |
| operator attention | `WorkstreamAttentionEvaluator` | CLI/API/report | 各端独立计算 next action/retry safety |
| provider/model identity | 当前 agent occurrence 的 session/usage evidence | metadata/metrics projection | Codex 使用 Claude 配置作身份 fallback |

关键不变量：

```text
Transport 先保证 named session 可用，再启动 prompt process。
Session bootstrap failure 必须形成 typed transport failure。
RecoveryAssessment 决定 recovery semantics；WorkstreamAttention 只渲染。
没有当前 agent 的 provider evidence 时，identity 必须保持 unknown。
```

## 5. 单一 implementation checkpoint

实施按以下顺序完成，但只形成一个 checkpoint，不拆成新 phase。

### 5.1 Transport session ownership

在 `AcpxAgentTransport` 内建立一个私有、agent-neutral 的 session-aware run 路径：

```text
runWithSession(agent, request)
  -> ensureSession(agent, request)
  -> bootstrap failure: return SessionCommandResult.toRunResult()
  -> success: run(agent, request)
  -> typed reconnect: rebuildSession(agent, request), then run once
```

约束：

- Claude 与 Codex 首次 prompt 均经过该路径；
- `requiresFreshSession=true` 使用 `sessions new`；否则使用幂等 `sessions ensure`；
- 不通过 stderr、exit code 文案或目录扫描判断 session 是否缺失；
- reactive `isMissingSession()` 本身是 typed 判断
  (`exitCode=4 + SESSION_RESUME_FAILED`)，不是 stderr classifier。它必须保留为 proactive
  ensure 之后的 typed reconnect/rebuild 分支；只删除“首次创建依赖 reactive signal”这一
  隐含前提；
- correction turn 继续复用已建立的同一 session，不重新 bootstrap；
- bootstrap failure 返回 typed `SESSION_BOOTSTRAP_FAILED`（沿用现有
  `SessionCommandResult.toRunResult()`），不得降级成 `OUTPUT_INCOMPLETE`。

不新建 public lifecycle 类型。必要的私有 helper 只用于去除 Claude/Codex 重复流程。

### 5.2 Retry projection 去重

`AgentExecutionOutcome.retryPolicy` 是执行结果的权威策略：

```text
NONE | MANUAL_ONLY | AUTO_ALLOWED
```

从 current-run 与 stale reconciliation 的 `executionOutcome` JSON projection 同时删除
派生字段：

```text
retrySafe = retryPolicy != NONE
```

精确删除点：

- `AgentRunMetadataFinalizer.outcomeToMap()`；
- `AgentExecutionAttemptReconciler.outcomeToMap()`。

理由：`MANUAL_ONLY` 不等于“safe”，该 boolean 不增加信息且与 operator read model
同名。run.json 保留 `retryPolicy`。`AgentExecutionAttempt.retrySafe` 属于具体 attempt
schema 中的 scheduler 预留事实（当前 production 构造为 false），本 checkpoint 不修改；
它不能被 operator projection 当作 recovery readiness。

`WorkstreamAttention.retrySafe` 只表示“当前 operator 是否可按 assessment 给出的命令
继续恢复”。该事实由 `RecoveryAssessment` 直接拥有。为避免 String readiness 到 boolean
再次产生映射表，现有 `RecoveryAssessment` record 增加 `boolean retrySafe`；
`WorkstreamAttentionEvaluator` 只能复制该字段，不能按 classification 分支手填。

### 5.3 Recovery projection 收敛

`RecoveryAssessment` 继续作为唯一 recovery semantics owner，不新增第二 assessment。

要求：

- `WorkstreamAttentionEvaluator` 只消费 `classification`、`retryReadiness`、`retrySafe`、
  `reason`、`suggestedCommand`；
- `RecoveryAssessment` 构造每个 recovery result 时同时声明 typed `retrySafe`，不允许
  evaluator 的 classification branch 再次决定；
- `agent_output_incomplete` 必须有显式 operator projection，不能落入 generic FAILED；
- `RecoveryAssessment.adapterClassification()` 必须增加
  `session_bootstrap_failed -> agent_session_recovery`；
- `RecoveryAssessment.adapterRetryReadiness()` 必须增加
  `session_bootstrap_failed -> REBUILD_ACPX_SESSION_THEN_RETRY`；
- session bootstrap failure 不得映射成 `agent_output_incomplete` 或
  `manual_recovery_required`；
- unknown classification fail closed：显示 inspection、retry=false，不升级为自动恢复；
- CLI、markdown summary 与 API 继续消费同一个 `WorkstreamAttention` / `RecoveryAssessment`，
  不增加各自 switch。

### 5.4 Provider identity 边界

provider/model identity 必须绑定当前 occurrence 的 agent。真实 fallback owner 是
`AcpxUsageReader`，不是已经 fail-closed 的
`AgentRunMetadataWriter.providerModelIdentity()`：

- session/stream/usage 有当前 agent 的 metadata：按现有来源投影；
- Claude 在 session metadata 不可用时，可使用明确标注的 Claude config fallback；
- `AcpxUsageReader.read()` 必须接收当前 `agent` 或显式 agent-specific fallback metadata；
- `AcpxUsageReader` 不得在 `fallbackMetadata == null` 时无条件调用
  `AcpxProviderMetadata.runtimeDefault()`；
- Codex 在 session metadata 不可用时，不得调用 Claude settings fallback；
- 没有 Codex-specific evidence 时输出：

```text
provider = unknown
model = ""
billingMode = pricing_unknown
source = ""
confidence = low
```

这只影响 metrics/metadata projection，不影响 workflow route。不得为了填满 identity 去读取
`~/.codex` 或新增 provider registry；Codex-specific 配置读取需要独立价值证明，不进入本次
checkpoint。

`SessionCompactionPolicy` 对 `runtimeDefault()` 的调用属于 compaction planning，不是 run
identity projection；本次 sibling sweep 必须把它明确分类，但不顺带修改。

usage reconciliation 边界：

- provider 根本未调用，且 `measurementType=UNAVAILABLE`：unknown identity 是准确事实，
  不因 unknown 单独判 INVALID；
- provider 已调用或产生 provider usage，但 identity 仍 unknown：属于 projection gap，
  Verification Gate 判 INVALID。

## 6. Deterministic cannot-bypass tests

### Session lifecycle

1. 不存在 named Codex session：fake acpx 记录命令顺序，必须先
   `codex sessions ensure --name ...`，再执行 prompt。
2. 已存在 session：在 acpx source/type contract 已证明 ensure 幂等的前提下，fake acpx
   断言 Dual 不执行 `new`、不进入 rebuild、prompt 只运行一次。
3. fresh-session profile：先 `sessions new`，再执行 prompt。
4. bootstrap 失败：prompt 进程不得启动；result 包含 typed
   `SESSION_BOOTSTRAP_FAILED`，outcome/recovery 为 session recovery。
5. stderr 包含 `No acpx session found` 但没有 typed signal：不得由文本触发 session
   transition，证明无 log classifier 旁路。
6. correction occurrence：不得再次执行 ensure/new，仍使用同一 session。
7. Claude 原有 fresh-session 行为不回归。

### Retry/recovery projection

8. `MANUAL_ONLY`：run.json 只写 `retryPolicy=MANUAL_ONLY`，不写 outcome
   `retrySafe=true`。
9. `session_bootstrap_failed` 必须先由 `RecoveryAssessment` 形成
   `agent_session_recovery + REBUILD_ACPX_SESSION_THEN_RETRY + retrySafe=true`，operator
   只复制该 retrySafe，next action 为 rebuild session。
10. `agent_output_incomplete + RETRY_AFTER_OUTPUT_CONTRACT_FIX`：显示明确 output
    contract action，不落 generic FAILED；operator retrySafe 只复制 assessment 的 typed
    boolean。
11. unknown typed classification：operator `retrySafe=false`，无 resume/auto-retry 建议。
12. CLI、failure summary 与 API/read-model fixture 对相同 assessment 给出相同 category、
    readiness 和 next action。

### Provider identity

13. Codex 无 session/usage metadata：identity 为 unknown，不能出现
    `source=claude_settings_env`。
14. Claude 无 session metadata但有明确 Claude config：fallback 保持标注且仅用于 Claude。
15. 当前 agent 的 session metadata 存在：优先于 fallback，provider/model/usage 对齐。

### End-to-end replay

16. 使用 T6 captured failure shape 做 deterministic replay：bootstrap failure 与 bootstrap
    success 两条路径都必须从 transport 贯穿到 run.json、outcome、RecoveryAssessment 和
    WorkstreamAttention，且不存在 raw stderr control path。

## 7. Sibling sweep

实施后必须搜索并分类：

```text
sessions ensure
sessions new
isMissingSession
SESSION_BOOTSTRAP_FAILED
retrySafe
retryPolicy
retryReadiness
agent_output_incomplete
session_bootstrap_failed
providerModelIdentity
runtimeDefault
claude_settings_env
AgentExecutionAttemptReconciler
```

每处只能属于：

```text
authoritative owner
typed consumer
projection renderer
test/fixture/doc
```

不得存在：

- handler/recovery 从 stderr 判断 session lifecycle；
- current-run 或 stale-reconciled outcome 同时持久化 `retryPolicy` 和含混的派生
  `retrySafe`；
- CLI/API/report 独立计算 retry readiness；
- Codex occurrence 使用 Claude-specific provider fallback。

## 8. 验收顺序

```text
CC design review PASS
-> deterministic characterization
-> single implementation checkpoint
-> focused adapter/runtime/ledger/CLI tests
-> captured T6 replay
-> sibling source sweep
-> full reactor
-> independent diff architecture review PASS
-> clean checkpoint commit
-> new T6 Verification Gate
-> explicit user authorization
-> one T6 real-case
```

implementation 期间不运行 provider 或 real-case。任何 deterministic test 连续两次暴露
同类 owner 分歧，停止局部修补并回到本设计修订。

## 9. 完成标准

只有同时满足以下条件才能标记 `PASS / ENFORCED`：

1. Claude/Codex 首次 prompt 前的 named session 可用性都由 transport 保证。
2. bootstrap failure 在 prompt 副作用前阻断，并形成 typed session failure。
3. production 不存在 pre-wire stderr session classifier。
4. execution outcome 只持久化 `retryPolicy`，不再派生含混的 outcome `retrySafe`。
5. operator retry safety 只从 `RecoveryAssessment.retrySafe` 投影，classification renderer
   不再拥有该判断。
6. `agent_output_incomplete` 与 session recovery 均有准确且不同的 operator projection。
7. Codex 无 provider evidence 时保持 unknown，不跨 agent fallback。
8. captured replay、cannot-bypass、full reactor 与独立 review 全部 PASS。

只有新的 T6 full workflow 达到 gate 的 `COMPLETED + ACCEPT`，该组合版本才能标记
`PROVEN`。

## 10. Stop conditions

出现以下任一情况立即停止：

- 为 session lifecycle 新增 registry、manager、state machine 或 module；
- 通过匹配 stderr 文案修复首次 session；
- correction turn 创建新 session 或改变语义结果；
- projection 开始修改 workflow state；
- 删除 `retrySafe` 时用另一个同义 boolean 代替；
- Codex identity fallback 继续读取 Claude config；
- 为兼容旧 projection 在 finalizer 或 stale reconciler 保留第二套 outcome retrySafe；
- 范围扩展到 prompt/parser/workflow/correction budget/token optimization。

## 11. CC review gate

CC 只读评审必须回答：

1. `AcpxAgentTransport` 是否是 named session bootstrap 的正确 owner？
2. `sessions ensure` / `sessions new` 的使用是否符合当前 acpx 0.11.0 contract？
3. `runWithSession` 是否会破坏 correction 同 session、typed reconnect 或 Claude fresh
   session 行为？
4. bootstrap failure 是否在 prompt 副作用前被 typed、持久化并进入正确 recovery？
5. 删除 outcome `retrySafe` 是否保留了 scheduler/attempt 所需的真实事实？
6. operator retry safety 是否真正只由 `RecoveryAssessment.retrySafe` 决定，且没有
   classification branch 二次判断？
7. `agent_output_incomplete` 与 session failure 是否不再混淆？
8. provider identity 是否按当前 agent occurrence fail closed，而没有跨 agent 猜测？
9. cannot-bypass matrix 是否覆盖 transport、run.json、recovery、CLI/report 全链？
10. 是否存在遗漏的 sibling owner、兼容旁路或不必要的新抽象？

评审结果：

```text
PASS: 可作为一个 checkpoint 实施
WARN: 必须先修订列出的具体边界
FAIL: owner 或数据流设计错误，不得实施
```

## 12. CC first review disposition

首次 CC 只读评审结论：`WARN`。本版已闭合其全部 finding：

1. 点名 `RecoveryAssessment.adapterClassification/adapterRetryReadiness` 对
   `session_bootstrap_failed` 的映射；
2. outcome `retrySafe` 删除范围覆盖 finalizer 与 stale reconciler 两个写入点；
3. `RecoveryAssessment` 直接拥有 typed `retrySafe`，evaluator 不再从 classification
   二次判断；
4. 点名 `agent_output_incomplete` operator projection；
5. provider fallback owner 修正为 `AcpxUsageReader`，并隔离
   `SessionCompactionPolicy.runtimeDefault()`；
6. `isMissingSession` 明确为合法 typed reconnect path，不再误称 stderr classifier；
7. `ensure` 的 create-if-missing/幂等语义降级为待 acpx source/type contract 证明的外部
   假设，fake test 只证明 Dual 调用顺序；
8. usage reconciliation 明确区分“provider 未调用的 unknown”与“provider 已调用但
   identity unknown”。

## 13. CC re-review disposition

CC 只读复审结论：`PASS`，允许作为一个 checkpoint 开始 implementation。

复审确认：

- session、retry policy、operator recovery 与 provider identity 的 owner 链无残余重复；
- `RecoveryAssessment.retrySafe` 是必要的 typed operator fact，不应从 String readiness
  二次推导；
- `AgentExecutionAttempt.retrySafe` 是异构 attempt/scheduler 字段，不是 operator
  recovery 的第二 owner；
- acpx source/type contract 与 fake command-order test 的证明边界准确；
- 16 项 cannot-bypass matrix 覆盖 transport、run.json、recovery、projection 和 identity。

实施 sibling sweep 还必须同步处理两项陈旧引用：

- 删除/改写 `AgentRunMetadataFinalizerTest` 对 outcome `retrySafe` 的断言；
- 更新 `docs/design/agent-execution-outcome-model.zh-CN.md` 对
  `executionOutcome.retrySafe` 的旧说明。

该 PASS 只激活 implementation readiness，不授权 provider probe、real-case 或 T6 retry。
