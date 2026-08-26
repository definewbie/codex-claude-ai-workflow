# R1 Multi-File Mutation Bounded Assessment Result

日期：2026-08-26  
状态：`ASSESSMENT FAIL / SHARED DESIGN COMPARISON TRIGGERED / PRODUCTION UNCHANGED`

## 1. 结论先行

S1-S4 共 14 个 production 写序前缀中，2 个可由原命令安全完成，12 个无法在 restart 后得到唯一且可重放的状态。
S1 initialize、S2 gate resolution、S3 stale recovery、S4 normal routing 四个 sibling family 都至少存在一个真实
stuck prefix，因此满足计划中“至少三个 sibling family 同类 stuck”的共享设计比较触发条件。

这不等于已经证明应该实现事务框架：

- 已证明的是 restart consistency 缺口和影响；
- 未知的是 SIGKILL/JVM 退出/I/O failure 恰好落入毫秒级窗口的现实频率；
- production mutation protocol 未修改，成熟度仍是 `VALIDATED / NOT ENFORCED / NOT PROVEN`；
- 下一步只能比较 bounded prepared-mutation manifest 与 generation pointer，不能直接实施。

## 2. 取证方式

测试：`MultiFileMutationRestartAssessmentTest`

1. 用 production `WorkflowFileLedger` 建立合法 pre-state；
2. 预占目标 `<file>.tmp`，让真实 `AtomicJsonWriter` 在指定 write 前失败；
3. 对 event 边界，临时把 `events.jsonl` 替换为不可 append 的目录，失败后恢复原 event bytes；
4. 创建全新的 `WorkflowFileLedger` 模拟 restart；
5. 从 authoritative definition/current/node/gate/instance/events 读取，不消费 timeline/metrics projection；
6. 重放原命令并检查 event id、active ids、gate、current/instance。

评审建议的 `AtomicJsonWriter` decorator 未采用：该类是 `final` 且 `write()` 不可覆写，当前 package-private 构造器
不构成可用 failpoint seam。为了 assessment 新增 production interface 成本高于文件级 failpoint，且会违反本轮
test-only 边界。

## 3. Prefix Matrix

| Slice | durable prefix | restart / replay 结果 | 判定 |
|---|---|---|---|
| S1 initialize | event only | definition 不存在；initialize 因 events 已存在拒绝 | FAIL |
| S1 initialize | event + definition + iteration + instance + current，无 entry node | current 指向不存在 node；initialize 拒绝 | FAIL |
| S2 gate | gate resolved only | current 仍声明 open gate，但 `requireOpenGate` 找不到 open document | FAIL |
| S2 gate | gate resolved + gate node completed | 同上；原 human action 失去重放资格 | FAIL |
| S2 gate | successor written，event/current 未写 | successor 成为 orphan；原 human action仍失去资格 | FAIL |
| S2 gate | current written，instance 未写 | current 已进 successor，instance 仍在 gate；原 action 不再适用 | FAIL |
| S3 stale | node FAILED only | current 仍 RUNNING；recovery 要求 node RUNNING，重放被拒绝 | FAIL |
| S3 stale | node FAILED + recovery gate | 同上；gate 是 current 不可见 orphan | FAIL |
| S3 stale | event written，current 未写 | future event 存在，current 仍旧；recovery 重放被拒绝 | FAIL |
| S3 stale | current written，instance 未写 | current WAITING_FOR_RECOVERY，instance RUNNING；重放被拒绝 | FAIL |
| S4 routing | source terminal，successor 未写 | 原命令重放可创建唯一 successor/gate/event/current/instance | PASS |
| S4 routing | successor/gate written，event 未写 | 原命令覆盖同 identity successor/gate并完成；无重复 event | PASS |
| S4 routing | event written，current 未写 | 重放再次 append `evt-000002`，相同 event id 出现两次 | FAIL |
| S4 routing | current written，instance 未写 | current/instance lastEventId 与 active ids 漂移；原命令不再 active | FAIL |

汇总：`PASS=2 / FAIL=12 / INVALID=0`。

## 4. 每个 FAIL 的现实价值

| Family | 现实触发源 | 频率 | 最坏损失 | 当前恢复成本 | 自动修复复杂度 |
|---|---|---|---|---|---|
| S1 | initialize 期间进程退出或文件写失败 | 未观测、预计低 | workstream 既不能 load 也不能 initialize | 删除/重建多个 authoritative files | 中 |
| S2 | human action 后、current commit 前退出 | 未观测、预计低 | 人工决定已落 gate，却不能继续或重放 | 人工判断 gate/node/current/instance 代际 | 中高 |
| S3 | stale recovery 多文件写中退出 | 未观测、预计低 | node/current 相互否定，恢复命令失去资格 | 人工修改 node/gate/current/instance/events | 中高 |
| S4 | routing event append 后、current/instance 前退出 | 未观测、预计低 | 重复 event id 或 current/instance 漂移 | 去重 event 或同步 instance，需人工判定 commit | 中高 |

影响高但频率未知。当前没有 crash-window telemetry，也没有已确认 real-case 恰好命中这些前缀，因此不能把 R1 称为
项目最常见故障；它是已机械证明的高影响完整性风险。

## 5. M6 Sibling Inventory

M6 不在 S1-S4 成熟度声明内，但后续共享设计必须覆盖或明确隔离以下写序：

### 5.1 AgentTaskLedgerWriter / WorkflowFileLedger

- `createAgentTask`：agent-task document -> `agent_task_queued` event -> projections；
- `markAgentTaskRunning`：agent-task document -> `agent_task_started` event -> projections；
- `markAgentTaskFinished` / `markAgentTaskFailed`：agent-task terminal document -> terminal event -> projections。

owner：agent-task dispatch ledger。风险形态是 task status 与 event/projection 不同代；它不拥有 workflow routing。

### 5.2 ExecutionLedgerWriter / WorkflowFileLedger

- `startNodeExecution`：node-execution document -> node `latestExecutionId` -> projections；
- node/workflow execution finish 先写 execution document，projection 随后刷新；
- workflow execution 与 node execution 没有共享 commit generation。

owner：execution runtime facts。风险形态是 execution 已存在但 node pointer 未写，或反向 pointer 指向缺失 execution。

### 5.3 AgentRunMetadataWriter

- `writePrepared`：prompt metadata sidecar -> run.json；
- `writeCompleted`：memory/governance report -> prompt/output/transcript/stderr metadata sidecars -> run.json；
- `appendExecutionEvent`：原子改写 run.json 内 execution timeline；它与 workflow node/execution pointer 不在同一提交边界。

owner：ACP adapter run/occurrence facts。该 writer 自有原子单文件更新，但 surrounding sidecars 与 workflow ledger
仍是多文件组合；R1 方案不能以“routing 已修”冒充全 ledger closure。

## 6. Boundary Review

| 维度 | 结果 |
|---|---|
| Declared by | production initialize/routing/gate/recovery 写序 |
| Proven by | 14 个文件级 failpoint prefix + fresh ledger restart/replay |
| Consumed by | authoritative definition/current/node/gate/instance/events readers |
| Enforced before | 当前未统一 enforced；12 个 prefix 证明存在 bypass/stuck |
| Failure route | IOException、duplicate event id、missing node、current/instance divergence |
| Cannot-bypass test | `MultiFileMutationRestartAssessmentTest` |
| Residual | M5 classification、M6、power-loss/fsync、legacy `FileLedgerStore` 未测 |

Boundary Review：`FAIL`，这里的 FAIL 是被测 production restart consistency 未闭合，不是 assessment 无效。

验证结果：focused assessment `4/0/0/0`；full Maven reactor 9/9 modules、`1284/0/0/0`；
`git diff --check` clean。

## 7. 下一步

先对本 test-only evidence checkpoint 做独立代码级 diff review。评审 PASS 后只制定一份 bounded design comparison：

1. 比较 prepared-mutation manifest 与 generation pointer 如何覆盖 S1-S4 和 M6；
2. 以 writer/read-path 改动数、恢复确定性、event 去重、迁移成本和可删除旧路径为准；
3. 如果两者成本都明显高于“低频 + 人工恢复”的可观察损失，允许明确关闭 R1，不实施；
4. 未经 design review，不修改 production protocol，不新增 module/framework，不调用 provider/real-case。
