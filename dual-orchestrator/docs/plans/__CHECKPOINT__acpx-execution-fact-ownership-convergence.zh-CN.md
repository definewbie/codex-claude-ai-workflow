# __CHECKPOINT__ ACP Execution Fact Ownership Convergence

状态：`IMPLEMENTATION ENFORCED / INDEPENDENT REVIEW PASS / T6 PROOF PENDING`

目标优先级：`P0 mainline stability`

日期：2026-08-01

## 1. 决策

本 checkpoint 定义一次不可再拆的 ACP execution fact ownership 收敛。

它不是对某个 `SKIPPED_PROVIDER_FAILURE` 条件的局部修补，也不新增通用
execution framework。目标是删除同一次 Agent execution 上的重复控制事实生产者：

```text
一次 transport occurrence 的 process、ACP completion 与 adapter signal
只能由 AcpxAgentTransport + live stream projector 产生一次；
Acquirer、Handler、Metadata、Finalizer、Recovery 只能消费 typed facts，
不能重新读取 stdout、stderr 或 session stream 推断同一控制事实。
```

实施必须作为一个整体 checkpoint 完成。不得拆成多个可独立宣称闭环的小 phase，
也不得在中途运行 T6 证明半成品。

## 2. 已确认的问题

T6 workstream `ws-20260731T202010Z-WW6V` 中，Claude 与 Codex 的真实执行均满足：

```text
exitCode = 0
stopReason = end_turn
finalAssistantMessage 非空
strict AgentResultEnvelope valid
executionAttempt = COMPLETED
executionOutcome = SUCCESS
adapterSignals = []
```

但同一个 `run.json` 又记录：

```text
correctionDisposition = SKIPPED_PROVIDER_FAILURE
```

这不是 provider 给出两个结果，而是 Dual 对同一次 execution 建立了两条事实链：

```text
完整 live stream
-> AgentLifecycleEventReader
-> AcpxSessionStreamProjector
-> AcpxPromptCompletion = completed

stderr session id
-> AcpxSessionStreamFailureEvidenceReader
-> 只读取 session stream 最后 128 KB
-> 缺少早期 session/prompt request
-> 无法证明 stale reconnect 已恢复
-> SKIPPED_PROVIDER_FAILURE
```

本次两个 stream 分别约为：

```text
Claude: 1,439,414 bytes
Codex:    400,859 bytes
```

最后 128 KB 包含 `end_turn` response，但不包含对应的 `session/prompt` request。
tail 截断是触发器，真正根因是 execution fact owner 不唯一。

同类结构性问题此前已表现为：

- `ProcessUsability` 在 handler 与 metadata writer 间重复推断；
- typed 与 legacy reconnect 得到不同 `RecoveryClassification`；
- prompt completion 在 live stream 与结果提取路径间分裂；
- correction disposition 与 execution outcome 使用不同 evidence；
- handler 在 output complete 时抑制 failure，而 acquirer 保留同一误判。

因此问题分类为：

```text
Architecture ownership failure
+ lossy post-hoc replay defect
+ run.json projection contradiction
```

## 3. 当前模型边界

| 边界 | 当前 owner | 本次处理 |
|---|---|---|
| Workflow Definition | workflow JSON / ContractRegistry | 不修改 |
| Workflow/Node Instance | ledger instance documents | 不修改 |
| Transport occurrence | `AcpxRunResult` | 扩充为完整 typed fact carrier |
| ACP prompt completion | live reader/projector | 保留为唯一 owner |
| Strict Agent result | parser/validator | 不修改 |
| Correction decision | `AcpxAgentResultAcquirer` | 改为只消费 result facts |
| Final execution outcome | `AgentRunMetadataFinalizer` 委托 `AgentExecutionFinalizer` | 保持唯一 production 生产/持久化 owner |
| Workflow route | runtime + `NodeResult` | 不修改 |
| Projection | run.json / recovery read model | 只消费，不推断 |

关键区分：

1. `AcpxRunResult` 表示一次 transport occurrence。
2. initial prompt 与 bounded correction 是两个 occurrence，不能伪装成一个合并进程。
3. Transport 只能声明 process、ACP completion 和 adapter signal。
4. Transport 不能声明最终 `AgentExecutionOutcome`，因为它不知道 strict result 和
   artifact contract 是否有效。
5. 最终 outcome 由 production owner `AgentRunMetadataFinalizer` 基于
   `AgentExecutionEvidence` 唯一生产并持久化；它委托现有
   `AgentExecutionFinalizer`/`AgentExecutionOutcomeResolver` 做 typed 解析。
6. `AgentExecutionAttemptReconciler` 只处理 transport 未返回的 stale execution，
   不属于正常 transport occurrence 的第二 outcome owner，本 checkpoint 不修改。

## 4. 目标数据流

```text
acpx process + current execution stream
                 |
                 v
AcpxAgentTransport
                 |
                 v
AcpxRunResult (one occurrence)
  - ProcessResult
  - AcpxPromptCompletion
  - List<AdapterSignal>
  - executionTimeline
  - usage
  - startedAt / finishedAt
                 |
        +--------+--------+
        |                 |
        v                 v
AcpxAgentResultAcquirer   AcpxAgentTaskHandler
  - strict parse          - persistence orchestration
  - strict validation     - no raw reclassification
  - correction decision
        |                 |
        +--------+--------+
                 v
AgentExecutionEvidence
                 |
                 v
AgentRunMetadataFinalizer
  -> AgentExecutionFinalizer
                 |
                 v
AgentExecutionOutcome
                 |
                 v
NodeResult / run.json / RecoveryAssessment
```

## 5. Authoritative Fact Owners

| Fact | Declared by | Consumed by | Enforced before |
|---|---|---|---|
| process exit/timeout/interruption | Transport -> `ProcessResult` | acquirer/finalizer | correction、NodeResult |
| prompt completion | live reader/projector | acquirer/handler | artifact persistence |
| adapter/provider signal | Transport live classification -> `AdapterSignal` | acquirer/finalizer | correction、retry、recovery |
| strict JSON validity | parser/validator | acquirer/finalizer | workflow success |
| correction disposition | acquirer | handler/ledger | correction call |
| terminal execution outcome | `AgentRunMetadataFinalizer` 委托 execution finalizer | runtime/recovery | NodeResult/route |

`AdapterSignal.superseded=true` 可以保存已恢复的 reconnect 历史，但：

```text
active signals = signals where superseded == false
```

只有 active signal 可以影响 correction、outcome 或 recovery。

`ProcessResult.stdoutPath/stderrPath` 是 evidence/projection 引用，不是读取授权。
这些字段可以保留；禁止的是下游通过这些 path 重新读取内容并生成控制事实。

## 6. 最小模型修改

不新增平行 assessment 模型。扩充现有 `AcpxRunResult`，组合已有 core 类型：

```text
AcpxRunResult
- startedAt
- finishedAt
- ProcessResult
- executionTimeline
- AcpxUsage
- AcpxPromptCompletion
- List<AdapterSignal>
```

删除或停止并列存储可漂移事实：

```text
独立 exitCode
handler-local ProcessUsability
AcpxFailureClassifier.AcpxFailure 的重复副本
```

`ProcessResult.usability=EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` 仅表示 ACP prompt
completion 完整，不表示 strict result 或 artifact contract 已通过。

## 7. 整体实施清单

以下步骤属于同一个 implementation checkpoint，不单独 promotion。

### 7.1 先固定当前矛盾

增加 deterministic characterization：

- 使用本次 T6 脱敏后的真实控制行；
- 用 deterministic notification padding 使 stream 大于 128 KB；
- 包含 stale `agent needs reconnect`、structured progress、`end_turn` 和 final JSON；
- 从 transport result 一直断言到 acquirer、handler 与 run.json；
- 修改前必须稳定复现 `SUCCESS + SKIPPED_PROVIDER_FAILURE` 矛盾。

### 7.2 收敛 transport occurrence

修改：

- `AcpxRunResult`；
- `AcpxAgentTransport.run()`；
- `waitForExitOrRecoverableSignal()`；
- `isMissingSession()`；
- `isReconnectRequired()`；
- `SessionCommandResult.toRunResult()`；
- 必要时为 live reader/projector 增加 typed evidence accessor/event。

要求：

- 自然退出前读取最后一批完整 framed events；
- 自然退出分支在完成最后一次 `health.observe()` 后，必须执行一次最终
  `health.assess()`，不能只依赖轮询期间的 assessment；
- 最终 assessment 必须把 `ProcessResult` 与 active `AdapterSignal` 装载进
  `AcpxRunResult`，包括“进程以可恢复码自然退出，但轮询期尚未触发
  RECOVERABLE_FAILURE”的情况；
- `AgentExecutionHealth` 的 live typed 输出必须收敛为现有 `AdapterSignal`，
  不再向下游暴露 `AcpxFailureClassifier.AcpxFailure`；
- transport 返回最终 `ProcessResult`、completion 和 signals；
- session rebuild/retry 只消费 result 中的 active signals；
- health-triggered termination 必须把触发它的 typed signal 带入 result；
- bootstrap timeout/failure 也必须产生完整 typed result。

### 7.3 收敛 correction ownership

从 `AcpxAgentResultAcquirer` 删除：

- `failureClassifier` 字段和构造参数；
- `classify(stdoutPath, stderrPath, exitCode)`；
- 对 request output paths 的控制依赖。

`correctionDisposition()` 只消费：

```text
ProcessResult
AcpxPromptCompletion
active AdapterSignals
CorrectionReason
correction prompt availability
```

同时删除 `Acquisition.executionResult()` 对 initial/correction process facts 的伪合并。
两次 occurrence 分别保留；usage/timeline 可在逻辑 run 层聚合，但不能覆盖 process facts。

删除伪合并后：

- `AgentRunMetadataWriter.writeCompleted()` 的 per-occurrence process facts 必须来自
  `agentResultAcquisition.occurrences` 已保存的 initial/correction occurrence；
- selected/final occurrence 只表示最终 strict result 来源，不得覆盖 initial occurrence；
- run-level timeline 与 usage 可以显式聚合，但必须与 occurrence process facts 分栏持久化。

### 7.4 收敛 handler 与 metadata

从 `AcpxAgentTaskHandler` 删除：

- 第二次 failure classification；
- `metadataFailure()`；
- handler-local `processUsability()`；
- `interruptedOutputFailure()`；
- 任何从 raw path 生成 control fact 的逻辑。

`AgentRunMetadataWriter`：

- 删除 failure/usability 入参；
- 直接持久化 result 的 `ProcessResult` 与 `AdapterSignal`；
- 删除 failure-to-signal 二次映射；
- 删除 provisional outcome 调用点及方法集：
  `executionOutcome()`、`processResultEvidence()`、`adapterSignalEvidence()`；
- 删除 `failureSignal()` 的 current-run reason-to-signal 映射；
- 不再充当 provisional outcome owner。

`AgentRunMetadataFinalizer` 是唯一 terminal outcome production/persistence owner，
并委托 `AgentExecutionFinalizer` 解析 typed evidence；它不得读取 raw logs。
其 `adapterSignals(NodeResult)` reason-to-signal switch 必须退出 current-run 主路径：

- current run 只消费 transport 持久化的 typed `adapterSignals`；
- 若历史 ledger 兼容确有必要，legacy `acpx_failure.reason` 映射必须隔离为明确
  compatibility fallback，并证明 typed signals 存在时绝不调用、绝不覆盖；
- 若没有真实兼容价值，则删除该 switch。

`RecoveryAssessment` 保持 outcome-first，legacy reason 只允许服务上述受控旧 schema。

### 7.5 删除旧控制路径

- 删除 `AcpxSessionStreamFailureEvidenceReader` 的 production 接线；
- 若无剩余生产调用，删除该 reader；
- 若迁移后 `AcpxFailureClassifier` 无生产价值，也一并删除；
- 保留 `AcpxFailureSignalCatalog`、wire parser、projector、health tracker 作为
  live adapter boundary；
- 将 `AgentExecutionHealth.Assessment` 从 nested `AcpxFailure` 收敛为现有
  `AdapterSignal`，使 transport 能直接把 live signal 带入 result；
- 删除 `AcpxAgentTaskHandler.readStringIfExists()` 无调用 dead code；
- 将 classifier tests 迁移到 projector/health/transport replay tests。

## 8. Deterministic Verification Matrix

| Case | Required result |
|---|---|
| stale reconnect + progress + end_turn | active signal empty；`NOT_NEEDED` |
| stream >128 KB | 与短 stream 的 typed facts 完全一致 |
| genuine typed 429 | `RATE_LIMITED`；不允许 correction |
| permission/auth failure | blocking signal 不被 completion 抹掉 |
| non-zero + completed ACP prompt | process non-zero complete；不 correction |
| non-zero + incomplete | `SKIPPED_PROCESS_NON_ZERO` |
| zero + incomplete/missing result | `SKIPPED_NOT_COMPLETED` |
| timeout/stall/interruption | 三种 usability 不互相替代 |
| nested duplicate request id | final prompt 正确关联 |
| malformed/truncated line | 不制造 completion 或 failure |
| agent prose 含 quota/429 | 不产生 adapter signal |
| session missing/bootstrap failure | result 自带 typed failure |
| 可恢复码自然退出、轮询期未 assess | 捕获不受 grace 门控丢弃的 current signal；终态 `health.assess()` 校验 active 状态，result 自带 typed failure |
| bounded correction | initial/final occurrence facts均保留 |
| run.json | process/signals/disposition/outcome 无矛盾 |

必须增加端到端 slice test：

```text
captured structural stream
-> transport result
-> acquirer
-> handler
-> run.json
-> finalizer/recovery projection
```

不能用只验证 projector 或只验证 metadata 的单类测试替代。

## 9. Cannot-Bypass Gate

增加轻量 source architecture test 或等价 deterministic sweep，拒绝：

```text
唯一 transport owner 之外的 Path-based failure classify
Acquirer/Handler/Metadata/Recovery 从 raw text/path 生成 CorrectionDisposition、ProcessUsability、NodeResult、
AgentExecutionOutcome 或 RecoveryClassification
```

gate 检查的是“是否从 raw path 生成 ACP execution 控制事实”，不是“是否读取或引用
任何文件”。以下路径明确允许，但不能反向生成本 checkpoint 的控制事实：

- `AgentLifecycleEventReader`：唯一 live ACP/process observation owner；
- `ProcessResult.stdoutPath/stderrPath`：evidence/projection reference；
- `AcpxUsageReader`：usage observation；
- `AcpxTranscriptToolUsageReader`：tool-usage telemetry；
- `ArtifactManifest`：artifact extraction；
- `FailureClassifyHandler` / `FailureArbiter`：独立的 workflow semantic classification；
- `AgentExecutionAttemptReconciler`：transport 未返回时的 stale execution recovery。

这些豁免不得调用 Path-based ACP failure classifier，也不得生成本次 execution 的
correction、process usability、adapter signal 或 terminal outcome。

不引入 ArchUnit、registry、engine 或 framework。

验收时必须回答：

```text
Declared by:
Proven by:
Consumed by:
Enforced before:
Failure route:
Cannot-bypass test:
Residual raw control path:
```

`Residual raw control path` 必须为空。

## 10. 验收顺序

```text
characterization reproduces contradiction
-> complete ownership migration
-> focused adapter tests
-> captured large-stream end-to-end replay
-> run.json cannot-bypass
-> production sibling sweep
-> full reactor
-> independent architecture review
-> clean checkpoint commit
-> new T6 Verification Gate review
-> explicit authorization
-> one T6 real-case
```

实现中途不运行 real-case，不允许用 provider 样本代替 deterministic closure。

## 11. 完成标准

只有同时满足以下条件，implementation 才能标记 `PASS / ENFORCED`：

1. `AcpxRunResult` 完整承载一次 occurrence 的 authoritative typed facts。
2. initial/correction occurrence 没有被压成一个伪 process result。
3. production 中不存在 transport owner 之外的 Path-based post-hoc ACP execution
   control classifier；合法 path reference、live observation、usage 和 telemetry 不误伤。
4. stale/superseded reconnect 不能阻止 correction 或 success。
5. genuine provider/permission/process failure 不能被 completion 抹掉。
6. run.json 的 process、signals、disposition、attempt、outcome 相互一致。
7. recovery 只消费 terminal outcome 或受控 legacy compatibility；typed signals 存在时
   legacy reason-to-signal mapping 不能执行或覆盖。
8. deterministic matrix、architecture gate 和 full reactor 全部 PASS。
9. 独立 review 对 end-to-end invariant 给出 PASS。

T6 通过后才能标记 `PROVEN`。deterministic PASS 不能提前宣称真实 full workflow 已证明。

## 12. 明确不做

本 checkpoint 不处理：

- prompt、strict parser 或 AgentResultEnvelope schema；
- correction 次数、token budget 或 semantic revision policy；
- workflow definition、LiteFlow routing 或 gate；
- usage/pricing/session compaction；
- UI/CLI broader projection convergence；
- stale RUNNING reconciliation；
- 通用 evidence registry、event bus、policy engine 或状态机；
- 新 module 或新 phase。

## 13. 预期收益与收敛边界

该设计预计一次收敛：

- `ProcessUsability` 重复推断；
- completion 与 failure classification 分裂；
- correction disposition 与 execution outcome 矛盾；
- handler 的 metadata failure suppression；
- run.json attempt/outcome/signals 不一致；
- recovery 被错误 legacy reason 污染；
- stream 长度改变控制结论；
- 依赖 real-case 人工翻日志发现同类 owner 漂移。

它不承诺解决其它 bounded context 的所有 SoT 问题。acceptance、gate、usage、UI
projection 仍按各自 owner 管理。此次只收敛 ACP execution control facts。

预计修改 7-9 个 production 文件、6-10 个测试文件。生产 LOC 应接近持平或净减少，
因为会删除两个 post-hoc owner、handler 推断、metadata 映射、tail reader 和伪 occurrence
聚合。若实现需要新 module、通用 registry 或 production LOC 明显膨胀，必须停止并重新评审。

## 14. Stop Conditions

出现任一项立即停止实现：

- 新模型重复保存 `ProcessResult` / `AdapterSignal` 已有事实；
- interruption/IOException 仍由 handler 伪造 execution facts；
- 任一 production Path classifier 残留；
- superseded reconnect 仍能阻断 outcome；
- correction occurrences 被压成一个不可解释的 process result；
- 只有局部 UT 通过，没有 transport-to-run.json replay；
- architecture sweep 或 full reactor 非 PASS；
- 范围扩展到 parser、prompt、routing 或 correction budget；
- 同类 deterministic replay 连续失败两次。

## 15. CC 同行评审门

CC 只做只读评审，不修改 production。必须回答：

1. 当前四个 Path-based classifier 是否已完整列出？
2. `AcpxRunResult + ProcessResult + AdapterSignal` 是否是最小模型，是否仍有重复事实？
3. Transport 是否拥有形成 final process/completion/signal 所需的全部 live evidence？
4. `AgentExecutionFinalizer` 是否保持唯一 terminal outcome owner？
5. initial/correction occurrence 是否保持独立？
6. 删除 tail reader 是否影响 session bootstrap/reconnect 的真实 typed failure？
7. deterministic matrix 是否覆盖所有现有控制分支，而不仅是本次 T6？
8. 是否存在遗漏的 sibling raw control path？
9. 实施能否一次完成而不拆出新的 phase/module/framework？
10. 自然退出终态是否在最后一次 observe 后执行 `health.assess()`？
11. cannot-bypass 是否精确禁止控制事实重算，而未误伤 live owner、path reference、
    usage、telemetry、semantic classifier 与 stale reconciler？

评审结果只能是：

```text
PASS: design 可按单一 checkpoint 整体实施
WARN: 给出必须先修订的具体边界，不得启动 implementation
FAIL: owner/model 仍错误，需要重新设计
```

在 CC `PASS` 前，implementation 与 T6 均保持未激活。

## 16. CC First Review Disposition

首次 CC 只读同行评审结论：`WARN`。七项 finding 已纳入本版：

1. W1：自然退出分支增加 terminal `health.assess()` 设计要求和 matrix case；
2. W2：cannot-bypass 改为禁止 raw path 生成控制事实，并列出合法豁免；
3. W3：点名删除 metadata writer provisional-outcome 方法集；
4. W4：writer/finalizer reason-to-signal current-run 双映射收敛；
5. W5：health typed 输出改为现有 `AdapterSignal`；
6. W6：点名 `executionResult()` 删除后的 per-occurrence metadata 来源；
7. W7：统一 terminal owner 命名，并隔离 stale reconciler 路径。

首次 review 的 `WARN` 已由 W1-W7 修订闭合；该历史状态不得解释为当前结论。

CC re-review 已于 2026-08-01 给出 `PASS`：W1-W7 全部闭合，可按单一
convergence checkpoint 整体实施。

## 17. Implementation Closure

2026-08-02，单一 convergence change-set 已完成 deterministic 收口：

- `AcpxAgentTransport` 成为 current-run `ProcessResult` 与 `AdapterSignal` 的唯一
  production owner；自然退出在最后一次 stream observation 后执行 terminal health
  assessment；interruption/IOException 不再由 handler 伪造 execution facts。
- initial/correction occurrence 各自持有 process、signals 与 completion；不再生成伪合并
  process result。
- 删除 `AcpxFailureClassifier` 与 `AcpxSessionStreamFailureEvidenceReader` 两个 post-hoc
  control owner；Acquirer、Handler 与 MetadataWriter 只消费 typed facts。
- `AgentRunMetadataFinalizer` 委托 `AgentExecutionFinalizer` 形成唯一 terminal outcome；
  typed fields 只有真正 missing 时才进入 legacy compatibility，present-but-invalid 必须
  fail closed，typed empty signals 不受 legacy reason 覆盖。
- superseded reconnect 在 outcome resolution 前被过滤；真实 provider/process signal 仍会
  阻止不合法 success。
- production 净变化为 `-372 LOC`（`+610/-982`），未新增 module、registry、engine、
  event bus 或新 phase。

验收证据：

1. deterministic execution-fact matrix：`122 tests / 0 failures`；
2. production transport large-stream replay：真实 `ProcessBuilder -> AcpxAgentTransport ->
   stream reader/projector -> acquirer -> handler -> run.json -> finalizer`，PASS；
3. strict typed-ingress / lazy-fallback cannot-bypass：PASS；
4. source architecture gate：`AcpxExecutionFactOwnershipBoundaryTest` `3/0`，自动阻止
   post-hoc control owners 回归、transport 外构造 current-run `ProcessResult`，以及 live
   control/usage owners 之外重新解析 ACP wire grammar；
5. full reactor：`1130 tests / 0 failures`，9/9 modules `BUILD SUCCESS`；
6. 独立架构 review：`PASS`，Declared by / Proven by / Consumed by /
   Enforced before / Failure route / Cannot-bypass 全部 PASS，residual raw control path 为空；
   最新 CC final-diff 复审确认第 4 项 source architecture gate 已闭合 F1，并给出 `PASS`；
7. `git diff --check`：PASS。

因此本 checkpoint 当前可标记 `ENFORCED / NOT YET PROVEN`。本轮未运行 real-case，
也未提交；T6
通过前不得标记 `PROVEN`，不得声称最新 full workflow 组合已由 provider 真实运行证明。
