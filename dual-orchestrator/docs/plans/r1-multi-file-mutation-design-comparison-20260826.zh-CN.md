# R1 Multi-File Mutation Design Comparison

日期：2026-08-26  
状态：`INDEPENDENT ARCHITECTURE REVIEW PASS / IMPLEMENTATION DEFERRED`  
依据 checkpoint：`3ecef0c4`

## 1. 决策问题

test-only assessment 已证明 14 个写序前缀中 12 个不能在 restart 后安全重放，且 S1-S4 四个 family 都存在
stuck window。本文件只回答：现在是否值得为 file ledger 引入共享 crash-consistency protocol；若值得，prepared
mutation manifest 与 generation pointer 哪个更小、更诚实。

不讨论并发 writer、provider、模型、PostgreSQL 迁移或 power-loss/fsync durability。

## 2. 当前问题用具体流程表达

### 2.1 Gate resolution

```text
写 gate=resolved
-> 写 gate node=COMPLETED
-> 写 successor
-> append event
-> 写 current
-> 写 instance
```

进程在任意箭头处退出时，磁盘上会同时出现旧代和新代文件。下一次 human action 看到 current 仍指向 gate，但 gate
已经 resolved，因此不能重放。

### 2.2 Stale recovery

```text
写 node=FAILED
-> 写 recovery gate
-> append event
-> 写 current=WAITING_FOR_RECOVERY
-> 写 instance
```

进程在 node 写入后退出，下一次 recovery 看到 current=RUNNING，却因 node 已不是 RUNNING 而拒绝。

### 2.3 Normal routing

event append 前可以覆盖同 identity successor 并安全完成；event append 后重放会追加相同 event id。说明单文件 atomic
write 本身没有问题，缺的是“这组文件属于同一提交代际”的事实。

## 3. 方案 A：Prepared-Mutation Manifest

### 3.1 语义

```text
计算完整 after-state
-> 原子写 pending/<mutation-id>.json
-> 幂等写每个 after-state document
-> 幂等 append event（event_id 已存在则不重复）
-> 写 current / instance
-> 标记 manifest applied 或删除
```

restart 看见 pending manifest 时，按 manifest 中的完整 after-state finish-forward。manifest 必须包含 mutation identity、
before generation、目标文件及其完整 typed payload、event identity；只有 `in_progress=true` 而没有恢复 payload 不成立。

### 3.2 能解决什么

- S1：initialize manifest 可补齐 definition/iteration/instance/current/entry；
- S2：human action、gate after-state、successor、event/current/instance 可从同一 payload 完成；
- S3：FAILED node、recovery gate、event/current/instance 可完成；
- S4：event append 通过 event-id 幂等避免重复；
- `ExecutionLedgerWriter` / `AgentTaskLedgerWriter` 可使用同一 module 内协议，但必须分别声明 execution 与 dispatch owner。

`AgentRunMetadataWriter` 不应被强塞入 workflow manifest。它属于 ACP occurrence owner，并已有 run.json 原子更新、typed
execution attempt 与 reconciler；后续只能明确桥接 identity，不能让 workflow journal 接管 provider facts。

### 3.3 必需改动

1. 在 `dual-ledger-file` 内增加一个小型 mutation document 与 writer/recovery owner；
2. 把 M1-M5 的“计算 + 立即写”拆成“计算完整 after-state + apply”；
3. `LedgerEventLog` 增加基于 event id 的幂等 append；
4. 所有 mutation 入口必须在副作用前 recover pending mutation；
5. 所有 authoritative reader 必须在 pending 存在时读取已提交代际或 fail-visible，不能静默混读；
6. M6 workflow execution/agent-task 写入必须纳入或明确隔离。

### 3.4 主要问题

- 当前 `WorkstreamLock` 在 CLI/server 外层获取，ledger 本身不知道 lock 是否已持有，不能自然成为统一 recovery hook；
- `RoutingEngine`、`NodeStateWriter`、`RevisionTracker` 把计算和写入交织，形成 after-state 需要实质重构；
- query/status 路径不全持锁，pending 期间仍可能读到半代状态；
- 目前约 31 个文件、84 个 `loadCurrent/loadDefinition/direct path` 读取点，不能只修 mutation writer 后声称闭合；
- 没有自然事故频率数据，收益无法量化。

reader 数量采用以下口径复算：在 `dual-ledger-file/src/main`、`dual-runtime/src/main`、`dual-cli/src/main`、
`dual-server/src/main` 中搜索 `loadCurrent(`、`loadDefinition(`、`workflowCurrentJson(`、`workflowInstanceJson(`；
`rg -l` 为 31 个文件，`rg -n` 为 84 行。该口径包含少量声明/写入引用，不等同于 84 个独立 read method，但足以证明
reader 不集中在单一边界。

结论：技术上可行，是两个候选中更小的一个；但它仍不是“小补丁”。

## 4. 方案 B：Generation Pointer

### 4.1 语义

```text
读取 generation N
-> 把完整 after-state 写到 immutable generation N+1
-> fsync/验证 N+1
-> 原子切换 committed-generation pointer
-> readers 只读 pointer 指向的 generation
```

pointer 切换前永远看到 N，切换后永远看到完整 N+1，天然解决 reader 混代。

### 4.2 优点

- commit point 清晰；
- 崩溃前缀只产生未引用 generation；
- read path 不需要按 transition family 推断恢复；
- S1-S4 可统一解释。

### 4.3 必需改动与副作用

1. `WorkstreamPaths` 的 definition/current/node/gate/instance/event 路径全部变成 generation-relative；
2. 约 31 个文件、84 个读取点需要迁移到 generation-aware resolver；直接 `Files.read...` 路径必须删除；
3. events 需复制、分段或改成 generation 内不可变 segment，不能继续原地 append；
4. projection、artifact ref、历史 workstream migration、CLI/server read model 均需兼容；
5. 每次 transition 复制整个 authoritative generation 会增加 I/O；若只写 delta，又需要额外 resolver/compaction；
6. M6 run/execution/task 与 workflow generation 的边界仍需单独定义。

结论：语义最干净，但相对当前 file layout 是存储模型迁移，不符合 bounded checkpoint，也与 harness subtraction 方向
冲突。当前明确拒绝。

## 5. 方案 C：Transition-Specific Idempotent Reconciliation

### 5.1 最小组成

1. `LedgerEventLog.appendEvent` 按 event id 幂等，已存在时不重复 append；
2. instance 明确降级为 current 的派生镜像，在 restart/load boundary 发现 lastEventId 或 active ids 漂移时重派生；
3. `requireOpenGate` 只接受一个额外精确形态：current 仍列出该 gate，gate 已 resolved，node identity 与 action
   evidence 完整，然后 finish-forward；
4. `recoverStaleRunningNode` 只接受一个额外精确形态：current 仍 RUNNING、同一 active node 已 FAILED 且带完整
   `STALE_RUNNING_PROCESS` typed result，然后 finish-forward。

### 5.2 覆盖与限制

- 可闭合 S2、S3、S4 的 11 个 FAIL 形态；
- 不能闭合 S1 initialize partial prefix，因为 event-only 或 current-without-entry 没有足够 after-state；
- 改动点比 prepared manifest 少，不需要存储布局迁移；
- 代价是扩大两个守卫的接受状态空间。任一 identity/result/action 条件写宽，都会把真实损坏误判成可恢复状态；
- 它不是第二 mapper 或平行状态机，但必须由 cannot-bypass tests 锁死精确形态，不能演变为通用“看起来差不多就继续”。

结论：这是未来 incident 仅命中 S2/S3/S4 时的优先候选；在零自然事故条件下同样暂缓。原比较遗漏该候选形成了
虚假二分，现已关闭。

## 6. 对比

| 维度 | Transition-specific | Prepared manifest | Generation pointer |
|---|---|---|---|
| S1-S4 recovery | S2-S4；S1 不可恢复 | 可 finish-forward | 可按 pointer 原子可见 |
| event 去重 | 单点 event-id 幂等 | 同样需要 event-id 幂等 | 需重做 immutable event segment |
| reader 一致性 | 精确 stuck-shape reconcile | 需 pending-aware guard/recovery | pointer 天然统一，但所有 reader 要迁移 |
| writer 改动 | 约 4 个集中语义点 | M1-M5 需拆 plan/apply | 所有 authoritative writer 改路径 |
| M6 | 不覆盖，必须诚实保留 | 同 module 可扩展；adapter run 隔离 | 仍需定义跨 generation 边界 |
| legacy workstream | 原地兼容 | 可原地兼容 | 需要 migration/双读 |
| 主要风险 | 守卫接受空间被写宽 | pending reader 绕过 | 全布局迁移和双路径 |
| 实施风险 | 中 | 中高 | 高 |
| 与当前收益匹配 | 尚无事故证据 | 尚无频率证据 | 明显不匹配 |

## 7. 决定

### 7.1 当前不实施 production protocol

原因不是缺口不真实，而是价值证据不足：

- correctness 影响已由 12 个 FAIL 证明；
- 现实 occurrence 仍是 `未观测、预计低`；
- prepared manifest 至少涉及 mutation planning、event idempotency、pending-aware readers 与 M6 ownership；
- generation pointer 是全存储布局迁移；
- 在已有完整 E2E 成功、没有自然 partial-mutation incident 的条件下，立即实现会把 harness 再次推向复杂化。

因此 R1 记录为：`RISK VALIDATED / IMPLEMENTATION DEFERRED / NOT ENFORCED / NOT PROVEN`。

### 7.2 保留候选与选择顺序

若自然 incident 只命中 S2/S3/S4，先评估 transition-specific reconciliation；若命中 S1、多个未知 after-state
family，或明确要求统一 crash recovery，再评估 prepared manifest。generation pointer 除非另有“必须支持强
crash-consistent file ledger”的产品需求，否则不再评估。两种保留候选都必须重新通过独立设计评审。

### 7.3 Revisit triggers

满足任一条件才重开 R1：

1. 自然 workstream 通过 authoritative files 证明命中 S1-S4/M6 partial mutation；
2. operator 因 node/gate/current/instance/event 混代执行过人工 JSON 修复；
3. file ledger 被明确升级为需要进程崩溃原子恢复的生产存储，而非单机开发 ledger；
4. 项目因其他必要工作建立了统一 ledger session/read boundary，使 prepared manifest 成本显著下降。

不以“理论上可能 crash”或新增 synthetic prefix 作为重开理由。

## 8. End-to-End Invariant

| 维度 | 当前结论 |
|---|---|
| Declared by | S1-S4 production write order |
| Proven by | checkpoint `3ecef0c4` 的 14-prefix assessment |
| Consumed by | 当前 readers 仍可能混读，未闭合 |
| Enforced before | 无统一 pending/generation guard |
| Failure route | 当前为 fail/stuck；本比较不冒充修复 |
| Cannot-bypass test | assessment tests 保留，作为 revisit baseline |
| Residual | M5、M6、fsync、legacy store 明确保留 |

## 9. 下一步

独立架构评审已 `PASS（P0=0 / P1=0 / P2=2 非阻塞）`。P2-1 已补入 transition-specific 第三候选，P2-2 已补
reader 计数口径。后续执行：

1. 选择性提交本文件与 active plan；
2. 关闭当前 R1 implementation，不写 production code；
3. 返回 Prompt/Rule Ownership Inventory，优先寻找“已有 typed/code enforcement 后仍重复存在的 prompt 规则”，只做
   可证明的删除式 checkpoint；
4. 普通文档同步与机械提交不再追加同行评审。
