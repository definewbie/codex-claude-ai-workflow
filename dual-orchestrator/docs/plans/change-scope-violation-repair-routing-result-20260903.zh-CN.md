# Change-Scope Violation Repair Routing Checkpoint Result

日期：2026-09-03  
状态：`IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS`

## 1. 结果

本 checkpoint 将已验证的 `CHANGE_SCOPE_VIOLATION` 从不可恢复的 execution recovery，收敛到定义明确的
human implementation repair 路径。没有修改 TaskContract、scope schema、provider、adapter、CLI、server 或 UI。

当前 OKX workstream 不迁移、不 retry，仍是本缺陷的自然 FAIL 证据；本实现只作用于以后加载新 definition 的 fresh case。

## 2. Authoritative Chain

```text
compiled TaskContract.scope
  -> ArtifactValidateHandler deterministic evaluation
  -> occurrence-scoped content-addressed evaluation ref/SHA
  -> definition-validated CHANGE_SCOPE_VIOLATION edge
  -> gate.human_implementation_repair
  -> human revise with required note
  -> context.implement_claude in a new iteration
```

Stable `artifacts/git-evidence/*` 只保留 latest projection 身份。repair Gate 只消费 routed occurrence ref，
并在展示前验证 producer node 与 artifact lineage。

## 3. 实施范围

- 新增 `ChangeScopeEvaluationArtifact`，提供原子、幂等、content-addressed publish/verify。
- 新增 `ChangeScopeViolationRoutingSemantics`，在 definition 构造期拒绝所有可绕过的配置形态。
- `ArtifactValidateHandler` 仅在 `route_scope_violation=true` 且 evaluation=`FAIL` 时产生 typed repair disposition。
- `HumanReviewGateHandler` 验证 occurrence 与 producer node 的 definition、handler、status、iteration、output refs 和 result。
- `RoutingEngine` 对所有 implementation-repair revise 强制非空 note。
- 三个 bundled workflow 与 small-plan experiment 使用同一 route 和 loop 语义。

## 4. Verification

- focused delta suite：126 tests，failures=0，errors=0，skipped=0；
- full reactor：1323 tests，failures=0，errors=0，skipped=0；
- `git diff --check`：clean；
- provider/real-case：未调用。

新增反证覆盖：artifact 删除、篡改、跨 workstream/node、stable ref；sibling occurrence 不覆盖；definition 非布尔 flag、
source id/handler、edge 缺失/重复/条件/错误目标、Gate kind/handler/purpose/actions、repair loop
缺失/重复/条件/错误目标/非 loop；configured/unconfigured/UNAVAILABLE/PASS handler disposition；Gate producer
definition/handler/status/iteration/outputRefs/disposition/ref/SHA、多 ref、stable-only；空 note；四份 definition 一致性。

## 5. Maturity

- scope detection：`ENFORCED / REAL-CASE PROVEN`；
- occurrence evidence + repair route：`ENFORCED / NOT REAL-CASE PROVEN`；
- 当前 OKX real-case：`FAIL`，不得解释为修复后完成。

只有下一次 fresh real-case 真实经历 violation -> human revise -> scope PASS，repair route 才能提升为 `PROVEN`。

## 6. Independent Review

首轮 review 发现 definition/Gate producer id 接受集合不一致，以及 cannot-bypass 测试矩阵不足，判定不可提交。
delta 固定 producer id、补齐负向矩阵并移除 Gate 对 stable latest 摘要的混合展示后，复审结果为
`P0=0 / P1=0 / Boundary Review PASS / ALLOWED TO COMMIT`。

剩余 P2 仅是测试断言精度：当前实现已投影 violation/missingDeclared 并拒绝 status/ref 不一致，但可在后续测试维护时
将这些字段拆成更细的单项断言；不影响本 checkpoint 的 authority 或 cannot-bypass 结论。
