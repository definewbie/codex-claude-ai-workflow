# B03 continuation: obligation ledger (working notes, not completion)

Existing user authorization only. No native/provider runs, commits, additional agents or new platform. This inventory supplements the existing master list; it does not shrink B03 acceptance or close W03/W04/W05/W08.

| Obligation | Current evidence / remaining |
|---|---|
| Runtime identity + complete L00 | Explicit factory identity and full initialization implemented; CLI/server factory tests and packaged factory/L00 cold-JVM probe exist. Final fresh regression is recorded in checkpoint/verification. Java identity is measured; declared acpx constants are not installed-native/config measurements. W03 native identity/config binding remains open. |
| R01/R02/R03 and unique L02/L03 | Protocol and native-command offline slices pass. Fixed outcome/publishing and cold JVM publication recovery pass. No live native evidence. |
| Unknown execution and stop evidence | No auto retry. Typed retry/abort + original PID/start/session and command intent/result comparison; forged stop receipt with recomputed SHA rejects. ADOPT original R03, pre-GOAL epoch=0 retry/abort, same-decision idempotency and before/after-manifest interruptions implemented and covered by AgentRunCommitProtocolTest. All UNKNOWN dispositions keep the original observation and never reopen old dispatch. |
| Failed readiness → plan author/reviewer → same authority or fresh approval | PhasedContractContinuationTest (10) passes, including cold JVM and operator decision cases. Revision flow, same-authority vs renewed approval, and four-variant repair obligations are in current regression. Program evidence reuse remains dependent on the D05 evidence decision; do not conflate these two items. |
| Acceptance/scope → requirement rework → GOAL/design/plan/EXECUTION → acceptance | AcceptanceRepairSequenceTest (7), LateContractRevisionContextTest (8), AcceptanceRepairInterruptionTest (4), FailureClassifyHandlerTest (8) passed in repair-sequence-06.log. No model calls; deterministic agent output with real handlers/ledger, approvals, publication and local Git fixtures. |
| Scope baseline migration | ScopeRevisionRequestGuard consumes immutable EXECUTION refs/SHA and the original GOAL compiler instead of mutable stable projections. Tampering rejected before ledger writes. |
| Requirement lineage through design/plan repairs | Authored-stage iterations retain their own obligations; late requirement resolver verifies declared routes without recasting plan notes as GOAL obligations. Original goal/compile/reviewer linkage rechecked. |
| Inconsistent active/current state | Execution owner rejects current→completed node and current→resolved gate before creating an execution. This is not a global tamper-detection guarantee. |
| Evidence collection, external submission, delivery, query/report | Coherent-session consumers and fixtures migrated; reports consume approved EXECUTION rather than stable display copies. Local Git apply/finalize retains intent/observation windows. Current full-regression result is recorded in verification.json; not browser/native proof. |
| Old production consumers/tests | Unused mutable-run reconciliation implementation removed with explicit replacement-obligation mapping below; contract/context/report/CLI/API and four-variant fixtures migrated. No fallback to old schema. Old malformed ledgers intentionally reject. |
| D05 machine evidence and program-fact readiness reuse | Still incomplete; tool counts/outer acpx exit/operator assertion do not satisfy it. Existing frozen producer/consumer obligations remain. |
| Full regression / batch independent review | Current all-module offline coverage is recorded in verification.json: 1,604 unique tests via reactor-55 plus consumer-56, 0 failures/errors/skips in the selected latest reports; not a single green reactor. Earlier 1,550/1,559 reports are not current evidence. B03 remains IN_PROGRESS regardless of test counts because D05 evidence and the remaining native-binding boundary are open; CC independent review remains required. |

## Evidence discipline

`repair-sequence-06.log`: one successful Maven invocation; 37 runtime cases plus selected ledger suites. Preserve reports/logs per invocation for final aggregation. Do not aggregate stale mixed target/surefire reports as a single run.

The test launcher assembly issue is distinct from product identity: Surefire 3.5.2 ClasspathConfiguration.trickClassPathWhenManifestOnlyClasspath replaces java.class.path (confirmed from local bytecode). Parent POM uses an actual classpath launch so production capture reads the real value. No production exemption or dropped classpath entry is introduced.

## 删除旧 mutable-run 恢复入口的义务对照（2026-10-06）

AgentExecutionAttemptReconciler/TimeoutPolicy 已无生产调用；删除实现及其 16+4 个旧机制测试，不把删测称回归转绿。旧测试要求从 mutable run 的 PID/timeout/exit 猜出完成，已被 D07 明确撤销。承接：AgentRunCommitProtocolTest 验证原执行者存活不接管、未 intent 可重试、有 intent 为 UNKNOWN、原固定完成可人工采纳/同请求幂等、prompt/output 漂移拒绝；RunningStateGuardTest 证明未绑定 raw run 不授资格或重写历史。timeout 不能推导未执行，也不再按 mutable PID 自动杀进程；显式停止证据由 ExecutionRecoveryAdjudication 消费。原始过程日志保留。

局部异常写序：L02 claim → handler；本地计算或 manifest 前失败 → 原 claim 下 task/execution/node 共同失败；manifest 后失败 → reader PENDING，错误处理不得覆盖固定完成；补齐成功后重复异常零状态变化。inline/worker 共用 AgentExecutionCompletion.recordHandlerFailure；acpx 仍按 R01/R02/R03 释放执行者并恢复，不按普通异常推断外部副作用。
