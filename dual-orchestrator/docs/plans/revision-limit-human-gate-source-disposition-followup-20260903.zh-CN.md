# Revision Limit Human Gate Source Disposition Follow-up

日期：2026-09-03  
状态：`INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT`

## 问题

`a44532d` 已让 revision limit 消费 workflow 声明的唯一 `BLOCKED -> human gate` 路径。OKX real-case
随后证明下游仍有一个消费缺口：`HumanReviewGateHandler` 只接受“latest Agent decision 本身等于 BLOCKED”，
因此拒绝了“latest decision 保持 SELF_TEST_INCOMPLETE、runtime 因 revision limit 将路由结果提升为 BLOCKED”的合法 Gate。

这不是新的恢复机制，也不是放宽 BLOCKED。原始业务结论与运行时处置是两个不同事实：

- Agent sidecar 拥有原始结论，例如 `SELF_TEST_INCOMPLETE`；
- RoutingEngine 拥有 `revision_limit_reached`、原始 outcome、上限和当前计数；
- HumanReviewGateHandler 只有在两者严格匹配时才允许生成 Gate packet。

## 最小变更

1. `RoutingEngine` 把四个 machine-owned revision-limit 字段写入新建 Gate node 已有的 `result`；不新增
   GateContext、ledger schema、workflow edge 或 action。
2. `HumanReviewGateHandler` 保留普通 `BLOCKED == latest decision` 校验；仅增加严格的 revision-limit
   分支，要求 reason、原始 outcome、整数计数和 `current >= max >= 0` 全部成立。
3. review packet 投影该 runtime disposition，使人工能看到“为什么从原始结论升级为 BLOCKED”。

## Boundary Closure

- Declared by：workflow 的唯一 `BLOCKED -> human gate` edge 与 node `max_revisions`。
- Proven by：Gate node `result` 的四个 runtime-owned 字段和原 Agent sidecar。
- Consumed by：`HumanReviewGateHandler`，是 review packet 写入前唯一校验点。
- Enforced before：Gate packet 发布与后续人工 action。
- Failure route：字段缺失、伪造、类型错误、outcome 不匹配或计数未达上限时抛错，packet 不写入。
- Cannot bypass：ordinary BLOCKED、合法提升、无字段、outcome 伪造、计数不足、字符串计数测试。

## 在途实例边界

OKX 的 `node-gate-human_implementation_repair-046` 由 `a44532d` JAR 创建，早于本 follow-up，因此其
Gate node 没有新增字段。不得修改历史 JSON 或声称该实例已被新 consumer 验证。该 Gate 已由 authoritative
ledger 证明来源：前驱 decision node 的 `reason=revision_limit_reached`、原始 outcome、计数和
`evt-000045`（或对应 persisted event）共同存在；operator 可据此直接执行既有 `revise` action。新代码防止后续
occurrence 再出现 packet 生成失败。

## 验证

- focused：`115 tests / 0 failures / 0 errors / 0 skipped`。
- full reactor：`1304 / 0 / 0 / 0`，`BUILD SUCCESS`。
- `git diff --check`：clean。
- 独立 diff review：`PASS / P0=0 / P1=0 / P2=3`；P2 均为非阻塞知会项。

## 成熟度

- declared revision-limit routing：`ENFORCED`。
- revision-limit Gate source disposition：`ENFORCED BY DETERMINISTIC TEST / NOT REAL-CASE PROVEN`。
- S2 implement local binding：`REAL-CASE PROVEN`。
- OKX product completion：`NOT PROVEN`。
