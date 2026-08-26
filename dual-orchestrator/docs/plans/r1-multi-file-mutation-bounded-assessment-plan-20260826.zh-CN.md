# R1 Multi-File Mutation Crash Consistency Bounded Assessment Plan

日期：2026-08-26
状态：INDEPENDENT ARCHITECTURE REVIEW PASS / TEST-ONLY EVIDENCE COMPLETE / AWAITING DIFF REVIEW

## 1. 唯一目标

不用 provider、real-case 或新事务框架，机械证明 Dual file ledger 在一次控制状态转换只完成部分 durable write 后，
重启读取与原命令重放是否仍能得到唯一、可继续的状态。评估结果只回答三个问题：

1. 哪些 partial-write 前缀只是孤儿文件或可重建 projection，不影响控制状态；
2. 哪些前缀会让 `current`、node、gate、event、instance 互相矛盾并阻断 resume/retry；
3. 缺口是单 transition 局部幂等问题，还是多个 sibling transition 需要同一提交协议。

本阶段属于 R1 correctness assessment，不是 R1 implementation。禁止预设 WAL、数据库、event store、generation
filesystem、transaction manager 或通用 recovery engine。

R1 不是并发控制评估。现有 `WorkstreamLock` 主要处理两个活写者的互斥；本计划测量的是单一合法写者在多文件
mutation 中途因 SIGKILL、JVM/机器退出或 I/O failure 停止，下一进程取得已释放的锁后能否从 partial state 继续。
只有发现 production mutation 入口绕过同一 lock 时，才把它记录为独立 sibling finding，不得用该 finding 替代
crash/restart assessment。

## 2. 已确认事实

### 2.1 现有保护只覆盖单文件和并发写者

- `AtomicJsonWriter.write()` 对单个 JSON 使用 temp + atomic move；fallback move 在文件系统不支持 atomic move 时只保证
  replace，不提供跨文件原子性。
- `LedgerEventLog.appendEvent()` 直接 append `events.jsonl`，不是 `AtomicJsonWriter` 写入，也没有 transaction marker。
- `WorkstreamLock` 防止两个活进程同时 mutation；进程崩溃时 OS 释放锁，但锁不描述未完成 mutation。
- 没有 restart-time consistency validator、pending mutation marker 或统一 idempotent completion owner。

### 2.2 控制事实与 projection 必须分开

| 类别 | 文件 | 当前语义 |
|---|---|---|
| operational snapshot | `workflow/current.json` | runtime/CLI/server 判断当前 workflow status、active nodes、open gates 的主要入口 |
| domain instance facts | `nodes/*.json`、`gates/*.json`、`iterations/*.json` | node/gate/iteration 当前与历史状态；不是纯展示缓存 |
| history | `events.jsonl` | CLI/server trace 消费，但 payload 不足以完整重建所有 node/gate 文档，当前不是 event-source SoT |
| lifecycle mirror | `workflow/instance.json` | 从 current 派生写入，但被持久化并可能与 current 漂移 |
| domain completion facts | `domain/goal-satisfaction.json`、`domain/completion-audit.json` | delivery/read model 消费，不能简单归类为 timeline 缓存 |
| rebuildable projection | timeline、metrics、failure summary、legacy current views | 可由 ledger refresh/reconciliation 重建，不应进入最小 commit protocol |

`workflow/current.json` 当前不能单独充当 commit pointer：多个 transition 会在写 current 前原地覆盖 current 仍引用的
active node 或 gate。崩溃后旧 current 因此也可能指向新状态的 node/gate。

## 3. Mutation Inventory

### M1 initialize

`WorkflowFileLedger.initialize()` 顺序：

```text
events(workflow_started)
-> definition
-> iter-001
-> instance
-> current
-> entry node
-> projections
```

任一前缀留下 `events.jsonl` 或 `current.json` 后，第二次 initialize 会直接拒绝；需要验证是否存在“无 active node 但永远
不能重新初始化”的 stuck state。

### M2 normal complete-and-route

`RoutingEngine.completeAndRoute()` 至少包含：

```text
source node terminal
-> optional goal-satisfaction
-> optional iteration
-> successor node(s)
-> successor gate(s)
-> event(s)
-> current
-> instance
-> projections
```

terminal completion 另写 completion audit。fan-out、revision limit 和 non-completed route 会扩展写集合。

### M3 human gate resolution

`RoutingEngine.resolveOpenGate()` 顺序：

```text
gate resolved
-> M2(source gate node COMPLETED + routing/completion writes)
-> projections
```

已确认的高风险窗口：gate 已 resolved、current 仍列为 open。重启后 `requireOpenGate()` 找不到 open gate，原 command
不能幂等重放。

### M4 failure / stale recovery / revision limit

共同形态：

```text
active node FAILED/BLOCKED
-> recovery gate open
-> event
-> current WAITING_FOR_RECOVERY
-> instance
-> failure/projection files
```

高风险窗口：node 已 FAILED，但 current 仍 RUNNING；`recoverStaleRunningNode()` 重放要求 node 仍 RUNNING，因此可能
失去恢复资格。

### M5 retry / compensation / abort

包括 `autoRetryNode`、`retryActiveNode`、`replayDeterministicClassification`、`abortRecovery`、
`closeMistakenIteration`。其中 classification replay 原地修改两个 node、一个 gate，再 append event、写 current/instance；
它是文件数最多且重放前置最严格的补偿路径。

### M6 execution / agent-task side ledger

agent task、node execution、workflow execution 也存在“文档 + event + node pointer + projection”多文件写。第一轮只做
sibling inventory，不把它们混入 routing crash slice。若 M2-M5 需要共享协议，implementation design 必须回扫 M6；
否则不得用“只修 routing”声称全 ledger `ENFORCED`。

## 4. 第一轮四个 Restart Slices

全部使用临时真实 `WorkstreamPaths` 与 production document classes。每个 slice 先用 production API 构造合法
pre-state；测试通过占用 `AtomicJsonWriter` 的目标 `.tmp` 路径，或暂时阻断 `events.jsonl` append，在 production
方法的真实写序中制造精确中断前缀，然后创建新的 `WorkflowFileLedger` 模拟进程重启。不 mock loader，不手写宽松
JSON，不调用 provider。

独立评审建议利用 `WorkflowFileLedger(AtomicJsonWriter, ...)` 注入计数装饰器。代码复核后不采用：
`AtomicJsonWriter` 是 `final`，`write()` 不可覆写，当前构造器并没有可用的 decorator seam。为本轮取证新增
production writer interface 会扩大范围；文件级 failpoint 能驱动真实 production 写序，且不需要 production 改动。

### S1 initialize prefix

至少覆盖：仅 event；event+definition+iteration+instance+current；current 存在但 entry node 不存在。

### S2 gate resolution prefix

至少覆盖：仅 gate resolved；gate resolved + gate node completed；successor 已写但 current 未切换；current 已切换但
instance 未同步。

### S3 stale recovery prefix

至少覆盖：仅 node FAILED；node+recovery gate；event 已写但 current 未切换；current 已切换但 instance 未同步。

### S4 normal routing prefix

至少覆盖：source node terminal；successor node/gate 已写；event 已写；current commit 后 instance 未同步。使用一个
普通 TASK -> GATE -> TASK definition，避免 provider 与业务 contract 干扰。

classification replay 作为第二轮候选，只有 S1-S4 已证明需要继续时才执行；不一开始枚举所有组合。

## 5. 每个 Slice 的机械判定

重启后必须依次执行：

1. `loadDefinition`、`loadCurrent`、active node 和 current open gate 加载；
2. 检查 current 引用的每个 node/gate 存在且状态与 workflow status 相容；
3. 检查 `current.lastEventId` 在 events 中恰好存在，events 不得领先到一个未提交 transition 而被误当 current fact；
4. 检查 instance 与 current 的 status/active ids/lastEventId 一致；
5. 对原命令执行一次重放；不得产生重复 successor、重复 iteration、重复 event id 或丢失 gate；
6. 再次重启，确认结果稳定。

判定：

- `PASS`：partial prefix 被识别为旧状态、可安全完成，或自动收敛到唯一新状态；命令重放幂等。
- `FAIL`：loader 接受矛盾状态、原命令失去资格、重复产生 facts，或不同 read path 得出不同控制状态。
- `INVALID`：fixture 未忠实复现 production 写序、测试读取了 projection 代替 authoritative files，或没有真正新建
  ledger 实例模拟 restart。

## 6. Commit Point 评估纪律

本计划不预先宣称 current、event 或 marker 是 commit point。候选只有满足以下条件才成立：

1. commit point 前的任意 prefix 在 restart 后等价于旧状态，或可安全完成；
2. commit point 后的任意 suffix 缺失都可从已提交事实幂等补齐；
3. 所有 control readers 只消费同一提交代际，不能一部分读 current、一部分把未来 node/gate 当 current；
4. event id 与 instance mirror 不会把未提交 mutation 投影为已完成。

## 7. 结果到方案的决策表

| 证据结果 | 允许的下一步 |
|---|---|
| S1-S4 全部已有确定性恢复/幂等行为 | 不新增协议；只补 consistency validator/cannot-bypass tests，或关闭 R1 |
| 仅一个 transition family stuck，其他 family 有稳定 commit point | 设计 transition-specific idempotent reconciliation，不抽共享框架 |
| 至少三个 sibling family 在同类 prefix 上 stuck | 比较 bounded prepared-mutation manifest 与 generation pointer；先写最小设计，不直接实现 |
| events payload 足以重建全部 authoritative after-state | 才允许评估 event-first rebuild；目前代码事实不支持该前提 |
| 需要跨进程断电 durability 而非 SIGKILL/restart consistency | 另立目标；不得偷换成本范围 |

每个 FAIL 还必须记录现实触发源、预计频率、最坏业务损失、现有人工恢复成本和自动修复复杂度。高影响不等于高频，
“ledger integrity 影响最高”不得被写成“项目当前最常见或最严重的现实故障”。若只能在不可达手工状态复现，或修复
成本明显高于可观察损失，结论应是暂不实施，而不是升级框架。

若选择 prepared-mutation，最低语义也必须是：在 mutation 前持久化完整 after-state/identity，restart 能幂等 finish，
event append 不重复，完成后才清理 marker。只写一个“in progress”布尔 marker而没有恢复 payload，不构成方案。

## 8. Explicitly Rejected

- 增大 `try/catch`、失败时反向覆盖文件；不能覆盖 SIGKILL，也可能制造第二次部分写。
- 把 timeline/metrics 当 authoritative repair source。
- 只修 `replayDeterministicClassification` 后声称整个 ledger 已安全。
- 依赖 operator 手工删除 gate/current 或编辑 JSON。
- 为本评估迁移 PostgreSQL、引入 event-store module 或通用 transaction framework。
- 用 unit test 的单文件 atomic write 代替 restart prefix 测试。

## 9. End-to-End Invariant

| 维度 | 本 assessment 要证明的内容 |
|---|---|
| Declared by | 每个 mutation family 的 typed before/after documents 与 production 写序 |
| Proven by | 每个 durable prefix 后创建新 ledger 实例的 restart slice |
| Consumed by | runtime、CLI、server 统一读取 definition/current/node/gate；projection 只能随后重建 |
| Enforced before | 在 resume/retry/provider dispatch 或 human action 前拒绝/收敛 incomplete mutation |
| Failure route | old state、finish-forward 或 typed recovery；不得静默接受矛盾状态 |
| Cannot-bypass test | S1-S4 prefix matrix + replay + second restart |
| Residual path | M6 side ledgers、power-loss/fsync、legacy `FileLedgerStore` 单独声明，不在第一轮冒充关闭 |

## 10. 输出与退出条件

第一轮只产出：

1. mutation/prefix/result 矩阵；
2. 每个 FAIL 的真实文件差异与重放错误；
3. 最小方案分类和不选其他方案的理由；
4. M6 sibling inventory：列出 `AgentRunMetadataWriter`、`ExecutionLedgerWriter`、`AgentTaskLedgerWriter` 的多文件
   写点、顺序与 owner，明确哪些不在 S1-S4 成熟度声明内；
5. 是否需要 R1 implementation plan 的结论。

assessment 本身不能把 R1 标成 `ENFORCED`。只有后续所有 authoritative mutation/load sibling path 受同一可恢复
协议约束，才可标 `ENFORCED`；真实 subprocess kill/restart slice 通过后才可标 `PROVEN`。

独立架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`。P2-1 经代码复核后采用上述文件级 failpoint 替代不可用的
writer decorator；P2-2 已把 M6 inventory 加入交付。下一步只实施 S1-S4 test-only evidence checkpoint，不修改
production mutation protocol。checkpoint 已完成：focused `4/0/0/0`，full reactor `1284/0/0/0`，
`git diff --check` clean；当前等待独立代码级 diff review。
