# Revision Limit Declared Human Repair Routing Checkpoint Result

日期：2026-09-03  
状态：`INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT`

## 结果

当 semantic loop 达到 revision limit 时，runtime 现在先消费 workflow definition 已声明的 `BLOCKED` route。
若该 route 唯一指向 human gate，则进入该 gate；若不存在，则保持原 synthetic `retry/abort` recovery；若定义
存在多个 target 或 target 不是 gate，则 fail closed。

这关闭了 OKX real-case 中的共性缺陷：workflow 已声明 `gate.human_implementation_repair`，但通用 revision-limit
拦截器此前在路由前遮蔽它，导致 `retrySafe=false` 且 operator 只能看到必然重复的 retry。

## 变更

- `RoutingEngine`：预算检查前置；在 limit 命中时通过既有 flow kernel 解析 `BLOCKED` route；保留
  `revision_limit_reached` event 与 decision result；路由 gate 时继续使用 definition 的 handler/actions。
- `WorkflowFileLedgerTest`：升级 self-test sequence，证明第二次 incomplete 进入 declared repair gate，空 note
  被拒，人工 revise 后创建 `iter-003` 并保留最新 self-test ref；新增歧义、唯一非 gate 与 loop-gate route
  fail-closed 测试。
- 文档：修正 OKX Gate，将 S2 能力 PASS 与真实产品 FAIL 分层记录。

## 验证

- focused：`WorkflowFileLedgerTest + WorkflowGateResolutionTest = 53/0/0/0`。
- full reactor：`1301 tests / 0 failures / 0 errors / 0 skipped`，`BUILD SUCCESS`。
- `git diff --check`：clean。

独立 diff review 为 `PASS / P0=0 / P1=0`。其中 P2-1 的 loop-gate 混合状态风险与 P2-3 的唯一非 gate
测试已随 checkpoint 关闭；P2-2 的重复计算未重构，避免非必要扩大。

## 成熟度

- declared human repair routing：`ENFORCED BY DETERMINISTIC TEST / NOT REAL-CASE PROVEN`。
- S2 implement local binding：`ENFORCED + REAL-CASE PROVEN`。
- OKX product completion：`NOT PROVEN`；当前仍停在原 recovery gate，尚未使用本 checkpoint 迁移。

## 下一步

独立 diff review PASS 后选择性提交并构建 fresh JAR。随后只执行一次 `dualctl retry` 重跑当前 deterministic
`implement.self_test_decision`；预期进入 frozen workflow 的 `gate.human_implementation_repair`，不调用 provider。
用户明确 `revise` 后，才继续下一次 implementation occurrence 修复 `CandleSyncService`。
