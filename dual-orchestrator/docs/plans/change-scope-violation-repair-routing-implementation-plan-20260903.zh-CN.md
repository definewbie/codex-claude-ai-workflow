# Change-Scope Violation Repair Routing Implementation Plan

日期：2026-09-03  
状态：`IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS`

## 1. 目标与非目标

目标：当 deterministic artifact validation 证明实际 Git 变化不满足 compiled TaskContract.scope 时，
把不可变的 occurrence evidence 路由到现有 human implementation repair gate；人明确 `revise + note`
后才允许新的 implementation iteration。

不做：不修改/放宽 TaskContract，不从 human note 推导 scope，不提前 validation，不新增 action/node family/schema，
不恢复当前 OKX 样本，不加入业务路径特判。

## 2. Authoritative Chain

```text
compiled TaskContract.scope
  -> ArtifactValidateHandler deterministic evaluation
  -> content-addressed occurrence evaluation ref + SHA
  -> declared CHANGE_SCOPE_VIOLATION edge
  -> existing gate.human_implementation_repair
  -> revise(note required)
  -> existing context.implement_claude loop
```

稳定路径 `artifacts/git-evidence/*` 继续作为 latest projection 服务既有 context/report/doctor reader，
但 repair Gate 只认 routed occurrence evaluation，禁止 stable fallback。

## 3. Production Changes

### 3.1 Occurrence evidence

在 `dual-runtime` 增加 package-private `ChangeScopeEvaluationArtifact`：

- canonical ref：`artifacts/git-evidence/occurrences/<node-instance>/change-scope-evaluation-<sha256>.json`；
- publish 使用 canonical JSON bytes、临时文件 + 原子 move；文件名 SHA 必须等于 bytes SHA；
- verify 要求 ref 位于当前 workstream、expected source node instance 目录，文件名/bytes SHA 一致；
- 不扫描目录、不接受 stable path、不按 mtime 选择 latest。

`ArtifactValidateHandler` 保留现有 stable projection，同时发布 occurrence evaluation；将 ref/SHA 写入 result
并加入 outputRefs。stable 写失败与 occurrence 写失败都保持 fail-closed。

### 3.2 Typed route

`artifact.validate` 新增 definition-owned boolean `route_scope_violation`，默认 false：

- `Status.FAIL && flag=true` -> `NodeStatus.COMPLETED / CHANGE_SCOPE_VIOLATION`；
- `Status.FAIL && flag=false` -> 既有 FAILED/recovery；
- `Status.UNAVAILABLE` -> 无论 flag 均为 FAILED/recovery；
- PASS -> 既有 completed route。

三个 bundled JSON 与 small-plan experiment 同时设置 flag，并增加
`CHANGE_SCOPE_VIOLATION -> gate.human_implementation_repair`。该 repair edge 的 loop reason 统一改为
“Human authorized implementation repair”，不再限定 blocked self-test。

在 `dual-core` 增加 named definition invariant（不放进 handler）：`route_scope_violation` 缺失/false
保持 legacy；存在时必须是 Boolean，true 时必须恰有一条 `CHANGE_SCOPE_VIOLATION` edge，目标必须是
固定 `gate.human_implementation_repair`，且 edge 必须 unconditional。该 Gate 必须是
`NodeKind.GATE + human.review + gate_purpose=implementation_repair`，allowed actions 恰为 `revise`；它必须恰有一条
unconditional `revise -> context.implement_claude` loop edge。缺边、多边、条件边、错误目标、错误 action、
缺失 repair loop 或字符串 coercion 均在 definition 构造/加载时拒绝，
防止 custom workflow 把 violation 误判为无后继并完成整个 workflow。

### 3.3 Gate consumption and note

`HumanReviewGateHandler` 在 source outcome=`CHANGE_SCOPE_VIOLATION` 时：

- 从 gate routed inputRefs 中要求恰一个 canonical occurrence evaluation ref；
- 从 ref 解析 producer node instance 后加载该 node，并验证 definition=`implement.artifact_validation`、
  handler=`artifact.validate`、status=`COMPLETED`、iteration 与 Gate 相同、producer outputRefs 包含 exact ref，
  result 同时声明 `change_scope_status=FAIL` 与
  `change_scope_disposition=CHANGE_SCOPE_VIOLATION`；不得以 ref 自证 expected producer；
- 通过上述 lineage 后调用同一 verifier，投影 violation 与 missing-declared 明细及 ref/SHA；
- ref 缺失、多份、越置、删除、篡改或只有 stable projection时，Gate 打开前 fail-closed。

`RoutingEngine.resolveOpenGate` 对 `gate_purpose=implementation_repair && action=revise` 一律要求 note 非空，
与 source outcome 无关；其他 gate/action 语义不变。

## 4. Files

Production：

- `dual-runtime/.../handler/ChangeScopeEvaluationArtifact.java`（新增）
- `dual-runtime/.../handler/ArtifactValidateHandler.java`
- `dual-runtime/.../handler/HumanReviewGateHandler.java`
- `dual-ledger-file/.../RoutingEngine.java`
- `dual-core/.../workflow/ChangeScopeViolationRoutingSemantics.java`（新增 named invariant）
- `dual-core/.../workflow/WorkflowDefinition.java`
- 三个 bundled workflow JSON + small-plan experiment JSON

Tests：

- `BuiltinHandlerTest`
- `WorkflowFileLedgerTest`
- lifecycle definition/loader consistency tests
- `ChangeScopeEvaluationArtifactTest`（必需）
- `ChangeScopeViolationRoutingSemanticsTest`（必需）

不修改 adapter、permission、provider、TaskContract schema、CLI/server/UI。

## 5. Cannot-Bypass Matrix

1. publish/verify：正确 ref+SHA PASS；删除、改字节、错 node/workstream、stable ref 全拒绝。
2. sibling overwrite：第二次 stable projection 覆盖后，第一个 Gate 仍读取自己的 occurrence bytes。
3. producer lineage：错 definition/status/iteration/outputRefs/disposition 任一项均拒绝，不能由 ref 自证。
4. configured FAIL：validator COMPLETED/CHANGE_SCOPE_VIOLATION，Gate packet 有 16/12 类明细。
5. definition invariant：flag 的字符串值、violation edge 缺失/多条/有条件/目标错误，以及 repair Gate
   非 revise-only、revise loop 缺失/多条/有条件/目标错误，均加载失败。
6. unconfigured FAIL 与 any UNAVAILABLE：仍 FAILED/recovery。
7. PASS：只进 acceptance，不开 repair gate。
8. note：scope violation gate 的空 revise note 拒绝，非空 note创建既有 implement iteration；既有 BLOCKED
   implementation repair、design revise 及其他 gate/action sibling 语义不变。
9. sibling definitions：3 bundled + 1 experiment 的 flag、edge、loop reason 一致；删除任一配置测试失败。
10. full reactor + `git diff --check`。

## 6. Execution Order

1. 先写失败测试；
2. 实现 occurrence artifact；
3. 实现 handler disposition 与 Gate consumption；
4. 实现 note guard；
5. 实现 definition invariant，修改四份 definition 与 consistency tests；
6. focused tests、full reactor、diff-check；
7. 独立代码级 diff review；
8. 选择性提交。

当前 OKX workstream 保持 `WAITING_FOR_RECOVERY / evt-000059` 并记为 FAIL。checkpoint 完成后只允许
为下一 fresh real-case 起草 Gate；不得迁移当前 ledger 或补发 provider。

## 7. Maturity

- scope detection：`ENFORCED / REAL-CASE PROVEN`；
- occurrence evidence + repair route：实施后仅 `ENFORCED / NOT REAL-CASE PROVEN`；
- 新 fresh case 完成一次 violation -> human revise -> scope PASS 后才可标 `PROVEN`。
