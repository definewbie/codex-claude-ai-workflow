# E1 acpx Persistent Policy Binding Replacement Guard Diff Review

日期：2026-09-02  
方式：独立只读代码级评审  
结论：`PASS / READY TO COMMIT`，`P0=0 / P1=0 / P2=4`

## 边界结论

四条已知绕过路径均真实关闭：

1. manager replacement 在 reuse/replacement 判断前调用唯一 binding comparator。
2. direct prompt 在 prompt 记录、record 写入与 client 创建前拒绝不兼容 binding。
3. queue owner 在 lease 与 shared client 创建前拒绝，per-task 路径仍二次校验。
4. fresh-session fallback 完整替换 effective options，调用方从新 record 同步 checkpoint，不再回写旧快照。

binding ref/SHA 不一致或单侧缺失时，在 client factory、record write、prompt write 和 queue lease 前以
`CLAUDE_EXECUTION_POLICY_CONFLICT` fail closed。fork delta 仅涉及 5 个 production 文件与 4 个测试文件，
未改动 workflow、ledger、permission、WebSearch、policy schema、provider 或 real-case。

## 独立验证

- fork 行为测试覆盖 replacement、单侧缺失、direct、queue owner 与 fresh fallback。
- patch、manifest、Doctor、configure、generated stamp 与 installed build-info 的 identity 一致：
  `81acbb7491a17aedfb48b1a73f077b59e06f1d5eceb7cd617e59df86aa5c60ab`。
- clean upstream reverse-apply 精确一致；fresh CLI Doctor 返回 `Result: OK`。
- 评审时两次直接套件均为 `985/0`；提交前在受支持 Node `22.21.0` 与正常 socket 权限下再复跑为 `985/0/0`。

## Findings

P0：无。P1：无。

P2：

1. 高负载整套测试曾单次出现 `984/1`，随后无法复现。提交需要一次完整 clean pass；同一失败若可连续复现则单独治理。
2. replacement 触发矩阵以 reset-on-ensure 为最锐反例；`cwd/argv/resume-id` 结构等价，但未各自参数化。
3. 独立评审未复跑 Dual full reactor，已列为提交前程序。
4. `mergeConnectedSessionOptions` 在 connected state 无 options 时会删除本地副本；当前 fresh 必写、resume 必保留，建议下次触及该路径时加显式回归断言。

这些 P2 不改变已证明的唯一 guard 和副作前 fail-closed 边界，不阻断提交。

## 成熟度与授权

- persistent policy binding replacement guard：`ENFORCED`（deterministic）。
- captured differential record durability：`INVALID`（已勘误）。
- E1 完整 closure 与 WebSearch provider-side cannot-bypass：`NOT PROVEN`。

选择性提交条件满足。clean commit 后的下一 provider 阶段只能是先评审新版 E1 closure Gate，再按 Gate 执行。
