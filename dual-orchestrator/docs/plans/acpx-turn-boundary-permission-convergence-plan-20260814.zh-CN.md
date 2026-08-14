# ACPX Turn Boundary 与 Permission Evidence 收敛计划

日期：2026-08-14  
状态：INDEPENDENT REVIEW PASS / ENFORCED / NOT PROVEN  
目标优先级：P0 主流程稳定性  
范围：单一 adapter/session convergence checkpoint；不新增 phase、module、registry、engine 或通用权限平台

## 1. 唯一目标

保证一个 Dual Agent execution 只由该次 ACP prompt occurrence 的事实决定，并在返回 workflow runtime 前完成外部 session 的静默副作用收敛：

1. 历史 turn 的 permission denial 不得污染当前 turn；
2. 当前 turn 超时、非零退出或缺少可信 completion 时，named acpx session 必须先 cancel/close 并确认不再执行，Dual 才能持久化 terminal outcome；
3. 只有当前 turn 的具体 permission request 可以形成 operator-facing authorization evidence；不得仅凭累计 `exit=5` 创建 human gate 或 retry。

本 checkpoint 不修改模型、prompt、strict parser、artifact contract、semantic revision、bounded correction 或业务 workflow。

## 2. Captured Failure 的已确认事实

Workstream：`ws-20260813T235458Z-4RQE`  
Run：`run-1786671207221`  
Node：`implement.claude_implement`

### 2.1 605 秒不是已证明的协议兼容失败

- Dual 持久化的命令明确包含 `acpx --timeout 600`；本地 wrapper duration 为 `605118ms`。
- ACP session 已接受 prompt，并持续产生 typed session updates、tool calls 和 token usage；没有 initialize、JSON-RPC shape、method-not-found 或 protocol-version 错误。
- acpx 0.11.x 的 `runPromptTurn` 对 `client.prompt(...)` 只调用 `withTimeout(..., timeoutMs)`；超时 catch 只等待 session updates idle，未调用 `cancelActivePrompt()`。
- `cancelActivePrompt()` 只出现在 OS interrupt cleanup；普通 timeout failure 不走该 cleanup。
- acpx 源码证明普通 timeout 不会取消 persistent turn，因此底层 turn 在 wrapper 返回后仍可能继续产生副作用。本 run 的后续文件时间受 worktree 复用污染，不能作为该具体副作用的硬证据，只保留为机制推断。

因此当前结论是：**600 秒 caller timeout + 未取消 persistent prompt 的 session lifecycle 缺口**。现有证据不支持把它归因于 ACP 协议不兼容，也不支持把 605 秒解释为模型一直没有响应；额外约 5 秒来自 timeout 后的 drain、queue/error propagation 与进程退出开销。

### 2.2 当前 implementation 的 `PERMISSION_BLOCKED` 是历史事实污染

本地 acpx stream 提供了精确顺序：

- 旧 design prompt 从 stream line 7435 开始；
- 唯一 `session/request_permission` 位于 line 13358，请求执行：
  `cat .agent/.../task-contract.json | python3 ...`；
- 该请求被记录为 `User refused permission to run tool`；
- 当前 implementation prompt 到 line 32385 才开始；implementation turn 中没有新的 `session/request_permission`。

acpx CLI 的 `applyPermissionExitCode(result)` 使用聚合 `permissionStats`：只要历史上 `requested>0`、`approved=0` 且 `denied+cancelled>0`，便把当前 CLI 退出码设为 `PERMISSION_DENIED(5)`。同一持久 session 因而把 design turn 的拒绝重复投影到 implementation run。

这不是“当前 implementation 被权限阻断”，也不是“模型需要人来批准后才能继续”。原始 design 请求本身还是可选的重复读取：prompt 已投影 typed contract，Agent 在拒绝后仍成功完成 design。

## 3. 边界与 Owner

### 3.1 Definition

`AcpxPermissionProfile` 继续声明每类 Agent node 的 permission intent。它只定义当前 occurrence 的自动批准策略，不拥有累计 session 结果，也不决定 workflow 状态。

### 3.2 Execution

`AcpxAgentTransport` 拥有当前 process、named session、prompt occurrence、timeout、permission event 和 quiescence facts。它必须返回 occurrence-scoped typed facts，不能把 acpx session 的累计统计冒充当前 run 事实。

### 3.3 Terminal outcome

`AgentExecutionFinalizer` / `AgentExecutionOutcomeResolver` 继续是 terminal attempt/outcome 的唯一 owner。它们只消费 transport 已界定到当前 occurrence 的 typed facts，不扫描 acpx session history，也不从 exit code 重新猜权限事实。

### 3.4 Operator projection

`RecoveryAssessment` / `WorkstreamAttention` 只消费当前 execution outcome。人工授权不是新的业务 decision gate；它属于 runtime recovery：operator 查看具体 permission evidence，决定是否调整本次 execution 的批准策略并显式 retry。

## 4. 单一实施 Checkpoint

### 4.1 Current-turn permission evidence

在 transport 启动 prompt 前建立 occurrence boundary，并只持久化该边界之后观察到的 permission events。优先复用既有 `sessionStreamBaseline()` 与 `AgentLifecycleEventReader`，但 baseline 与增量读取必须直接绑定本次 run 的命名 session stream，不能枚举目录后猜测 request owner。最小 typed evidence 至少包含：

- session name / current run id；
- tool call id；
- inferred tool kind；
- tool title 或安全裁剪后的 command/resource；
- requested options；
- disposition：`APPROVED`、`DENIED`、`CANCELLED` 或 `UNAVAILABLE`；
- occurrence sequence/time。

命名 session baseline 只有在 `index.json` 中按 name、normalized cwd、open state 选中唯一 record 时才可赋予增量 stream `currentObservation=true`。索引缺失、无匹配项或解析失败时，不得用空 baseline 从 offset 0 回放；只能走既有 bounded tail 且将其标记为非当前观察。permission result 若没有同一 current occurrence 的 request-id evidence，也不得单独生成 DENIED/CANCELLED 控制事实。

不得把 acpx 返回的累计 `permissionStats` 或裸 `exit=5` 单独映射为当前 `PERMISSION_BLOCKED`。兼容规则为：

- 当前 occurrence 有 DENIED/CANCELLED evidence，且 required artifact/completion 未满足：产生 `PERMISSION_BLOCKED`；
- 当前 occurrence 无 permission evidence：`exit=5` 只能记录为 adapter diagnostic，不能授权 current-run permission failure；由既有 `OUTPUT_INCOMPLETE / process_output_incomplete` fail closed，不新增状态；
- 当前 occurrence 的 denial 已 contained，且 strict result 与 required artifacts 完整：保留 secondary telemetry，不覆盖成功 outcome。

若核验后证明命名 stream 无法可靠输出 occurrence-scoped permission event，才允许评估 permission-profile privilege boundary 的独立 session segment；它是较重回退，不是首选实现。不得扫描全部 session 目录猜测当前 request。

### 4.2 Failed-turn quiescent closure

`AcpxAgentTransport` 在以下任一条件成立时，返回 terminal result 前必须调用当前 named session 的 cancel，并在有界等待后确认 turn 已停止：

- process timeout；
- wrapper 非零退出且无可信 prompt completion；
- wrapper 已退出但 ACP prompt completion 未被观察；
- event persistence 或 result acquisition 异常。

最小顺序：

```text
wrapper failure / timeout
-> acpx <agent> cancel -s <name>
-> bounded wait for prompt/session quiescence
-> cancel 无法证明时 close named session
-> persist typed SessionClosure disposition
-> AgentExecutionFinalizer
-> workflow recovery routing
```

`SessionClosure` 不是新 entity、schema 或并行 store；仅使用现有 execution timeline / adapter signals 表达有限 disposition：

- `NOT_NEEDED`
- `CANCELLED`
- `CLOSED_AFTER_CANCEL`
- `FAILED`

`FAILED` 必须 fail closed：不得声明 execution 已 quiescent，不得允许自动 retry。wrapper PID 被杀死不等于 session 已停止；process-tree cleanup 也不能替代 named-session closure。

### 4.3 Permission 的人工授权边界

人工授权只在以下条件全部满足时可显示：

1. permission evidence 属于当前 occurrence；
2. evidence 含具体 tool/action/resource，而不是只有 `exit=5`；
3. denial 确实阻止 required completion，而不是被完整结果 contained；
4. session 已 quiescent，旧 turn 不会继续产生副作用；
5. operator action 明确为“批准该权限策略并创建一次显式 runtime retry”，不是 resume 已结束/失控的 prompt。

当前 acpx escalation 在 non-interactive path 中已经选定 reject/cancel，没有公开的“事后批准同一 pending request”控制。因此本 checkpoint **不建设 mid-turn human gate**。UI/CLI 可在既有 recovery attention 中展示：

- 被拒 tool/action/resource；
- 当前 profile 与拒绝原因；
- 建议的最小 permission change；
- retry safety；
- operator 显式 retry 命令。

AUTH failure、context-window、provider failure、timeout、unknown permission evidence 不得伪装成人工授权请求。

### 4.4 Recovery projection preservation

stale reconciliation 不得把已经存在的 typed `permission_blocked`、timeout 或 session-closure failure 覆盖成泛化 `STALE_RUNNING_PROCESS`。reconciler 只补齐缺失 terminal facts；已有 authoritative execution outcome 必须保持。该修正与 4.1/4.2 同属 execution closure，不另开 phase。

## 5. Cannot-Bypass Tests

必须使用可运行边界测试，而不是只断言 JSON 字段：

1. **Historical denial isolation**：同一 session 的旧 turn 有 permission denial；新 turn 无 permission request 且完成有效结果。新 run 不得产生 `PERMISSION_BLOCKED`。
2. **Current denial fail closed**：当前 turn 发出具体 permission request 并被拒，required completion 缺失；outcome 为 permission blocked，evidence 精确保留。
3. **Contained denial**：当前 turn denial 后仍交付 strict-valid result 和全部 required artifacts；node 可成功，denial 仅为 secondary telemetry。
4. **Timeout cancels session**：fake acpx wrapper 在 caller timeout 后仍尝试写文件；transport 必须使用真实签名 `acpx <agent> cancel -s <name>`，必要时 close，返回后观察窗口内文件不再变化。
5. **Closure failure blocks retry**：cancel 与 close 都失败时，session closure=`FAILED`，workflow 进入人工 recovery，不能 scheduler retry/resume。
6. **No generic human authorization**：只有裸 exit=5、无 current permission evidence 时，不生成可批准 permission action。
7. **Typed authorization projection**：当前 permission evidence 完整时，CLI/UI 共用 `WorkstreamAttention` 显示同一 action/resource/retry safety；operator approval只创建一次显式 retry。
8. **Reconciler preservation**：已有 typed permission/timeout/closure disposition 时，stale recovery 从既有 timeline/signals 保留事实，不覆盖为 generic stale reason，也不读取并行 closure entity。

Full reactor 与 `git diff --check` 必须 PASS。实现完成后先做独立代码级 review；不得直接运行 provider/real-case。

## 6. Sibling Boundary Sweep

实施时必须同时检查：

- persistent session prompt、one-shot exec、bounded correction；
- Claude 与 Codex adapter invocation；
- contract-discovery、observe/review、implement profile transition；
- normal completion、timeout、permission denial、provider non-zero、OS interrupt；
- handler catch closure、runtime stale recovery、runner stop-loss/session reap；
- run.json、RecoveryAssessment、WorkstreamAttention、CLI、API、UI、report。

任何 sibling path 不得继续从累计 permission stats 或裸 exit=5 生成 current-run authority。

## 7. 明确拒绝

- 不把 timeout 调大来掩盖未取消 turn；
- 不因本次事件更换模型或修改 prompt；
- 不把所有 permission denial 都变成人工 gate；
- 不自动 `--approve-all` 后 retry；
- 不扫描 `~/.acpx/sessions` 目录作为 production 控制路径；
- 不新增 permission engine、policy DSL、event store、scheduler 或通用 session platform；
- 不放宽 strict parser，不补默认字段，不增加 correction 次数。

## 8. 成熟度与下一步

截至本 checkpoint，证据支持：

- 605 秒根因：`VALIDATED`（source + captured session）；
- historical permission contamination：`VALIDATED`（captured stream sequence）；
- current-turn permission evidence：`ENFORCED`（命名 session baseline、typed request/result correlation、历史 denial isolation）；
- failed-turn quiescent closure：`ENFORCED`（`cancel -s`、bounded close fallback、closure failure fail-closed）；
- operator projection：`ENFORCED`（typed outcome 保留具体 action/resource，`RecoveryAssessment` 复用该事实且 `retrySafe=false`）；
- deterministic verification：full Maven reactor `1239/0/0/0`，`git diff --check` clean，production residual-authority sweep empty；P1 baseline failure 与未关联 permission result 均有 cannot-bypass regression；
- captcha-auth 产品：`NOT ACHIEVED`。

当前明确不在本 checkpoint 扩展：mid-run session-id rollover 的证据连续性，以及 runtime stop-loss 的 named-session closure；二者保留为独立 owner 边界，不影响本次 transport checkpoint 的提交判定。

独立 delta 复审已 PASS（P0/P1/P2=0/0/0，原 P1-1、未关联 permission result、closure session-id 均 CLOSED）。下一步是选择性提交并获得 clean SHA；此后才允许更新 captcha-auth Verification Gate、执行 fresh package/preflight，并评估是否创建 fresh workstream。不得 resume 已污染的 workstream，也不得把 deterministic `ENFORCED` 宣称为 real-case `PROVEN`。
