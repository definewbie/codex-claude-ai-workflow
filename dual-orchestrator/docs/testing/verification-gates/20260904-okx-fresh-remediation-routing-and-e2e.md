# OKX Fresh Remediation Routing And E2E Verification Gate

日期：2026-09-04  
状态：`DRAFT / PROVIDER NOT STARTED`

## Verification Gate

- Target: 使用包含 checkpoint `509248e4` 的 fresh Dual JAR，在唯一 fresh workstream 中完整实现 OKX K 线需求并到达 `COMPLETED`；同时确认非 acceptance 节点不会再把业务 revision 写入 `verification_remediation`。
- Source of truth: persisted workflow definition、`workflow/current.json`、node/gate documents、`events.jsonl`、run metadata、validated AgentResult、TaskContract、occurrence-scoped repository/scope evidence、human decisions、completion audit、accepted delivery candidate，以及目标仓库测试结果。Markdown 正文和 console 输出不是路由事实。
- Trigger precondition: Dual production checkpoint=`509248e4d7e742d367ba73eb8ba4a1be97ba0e67`；当前 HEAD 必须是其后代，且从该 checkpoint 到当前 HEAD 的 production diff 为空；fresh JAR SHA-256=`c11b8a1477bb46d45c4b9cbafbaeabfc239a91b358c34e053854e943f386406b`；workflow SHA-256=`1ce188bc2ffd937bf5faba1dd72ae862eff68dfc98b26b2da4a748a2fec9cdc3`，形状为 `28 nodes / 47 edges / 8 acpx.agent_run nodes / 6 gates`；目标 HEAD/tree=`c84807946493a603c789ee40c6a4c615aa846c3b / 81520ac179f17c35c7c358c2a02102007cbe7cba` 且 tracked clean；`REQUIREMENT.md` SHA-256=`9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a`；`scripts/verify-workspace.sh` SHA-256=`c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23`；Codex 使用 isolated `CODEX_HOME` 固定 `gpt-5.4`。
- How precondition is checked before run: 验证上述 Git/SHA/工作流形状，并从 fresh JAR 内嵌 `dual-runtime` class 机械确认同时含节点允许值投影与 `NONE outside acceptance review` validator sentinel；source checkout 与创建后的 persisted effective workspace 分别运行 target-owned workspace check；Claude/Codex minimal probes 通过并关闭；以 `start` 不带 `--run` 创建唯一 workstream，在首次业务 Agent 前再次核对 workspace identity。任一失败不得启动业务 provider。
- PASS: workflow 最终为 `COMPLETED`；所有 human gate 由用户本人裁定；typed contract、repository-fact binding、scope evaluation、self-test、acceptance、completion audit、accepted delivery candidate 均有效；目标仓库 `mvn test` 通过；并满足下面的 remediation sentinel。
- FAIL: remediation sentinel 失败；或 contract、lineage、scope、self-test、acceptance、completion、delivery、产品测试任一边界未闭合；provider/adapter fatal 导致样本不能继续。
- INVALID: 使用旧 JAR、identity/workspace preflight 失败、复用旧 workstream、使用 autorun、手改 ledger/evidence/TaskContract、为了触发 revision 或 scope violation 而修改输入，或失败后修改 fixture 原地重跑。INVALID 不计部分成功。
- Stop-loss: remediation 再次出现同类错误或错误路由时立即停止；同一 review blocker 在无新证据下重复两次、同一 scope repair 以相同 path set 再次失败、provider fatal、repository-fact sentinel 失败或 active provider runtime 达 120 分钟时停止。
- Run artifact freshness: 记录 Dual/JAR/workflow/target/task/check identity、新 workstream/workspace id、每个 run/session id、validated AgentResult、human packet/decision、scope evidence、completion/delivery evidence和最终测试结果。

## Remediation Sentinel

1. `intake.codex_product_review` 与其他非 acceptance failure-bearing result 的 `failure.verification_remediation` 必须为 `NONE`。
2. 若 product review 自然返回 `NEEDS_REVISION`，业务修订必须由 `decision`、`contract_obligations` 和声明的 intake revision edge 表达；不得进入 failure classification、implementation repair 或 `WAITING_FOR_RECOVERY`。
3. 如果自然触发 correction，其 prompt 只允许该节点使用 `[NONE]`；是否触发 correction 不是 PASS 前提。
4. 只有 acceptance review 可按既有 validator 规则使用 `EVIDENCE_COLLECTION` 或 `TEST_CODE_CHANGE`；本 Gate 不要求自然触发这两个值。

## 边界与非目标

- 主判定是完整 E2E，不是单独证明一个 enum 字段。
- 允许模型自然提出 contract revision 或产生 scope violation；不得人为制造。自然 scope violation 仍按既有 human repair Gate 处理。
- 不重新验证 E1/S2 网络安全，不修改网络权限、模型适配、workflow、schema 或产品需求。
- 旧失败 workstream `ws-20260903T225544Z-HHZL` 仅保留为缺陷证据，不 resume；本 Gate 只允许一个新样本。

## End-to-End Invariant

| 维度 | 本 Gate 的机械边界 |
|---|---|
| Declared by | 冻结的 workflow、需求、JAR 和 workspace-check identity |
| Proven by | persisted node/gate/events/run documents、validated AgentResult、completion/delivery evidence 与目标测试 |
| Consumed by | workflow 的既有 contract、decision、routing、acceptance、completion 和 delivery 链 |
| Enforced before | identity/workspace 条件在首个业务 provider 前；typed remediation 在 artifact publication 与 routing 前 |
| Failure route | 前置漂移为 `INVALID`；typed/E2E 边界失败为 `FAIL`；重复 blocker 触发 stop-loss |
| Cannot-bypass | fresh 单样本、禁 autorun/手改/人为制造缺陷；所有 human gate 由用户本人裁定 |
| Residual | 单个 real-case 不能证明所有模型输出形态，也不外推为 E1/S2 网络安全证明 |

## Frozen Inputs

```text
Dual production checkpoint: 509248e4d7e742d367ba73eb8ba4a1be97ba0e67
Dual tree: af8fba838a7160615fea78016ceaa4622b6f1847
Fresh JAR: dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar
Fresh JAR SHA-256: c11b8a1477bb46d45c4b9cbafbaeabfc239a91b358c34e053854e943f386406b
Fresh package: BUILD SUCCESS at 2026-09-04T11:13:33+08:00
Codex model: gpt-5.4 via isolated CODEX_HOME
Codex pin basis: inherited gpt-5.6-sol minimal probe failed; isolated gpt-5.4 minimal probe passed. This is an observation, not a claimed incompatibility cause.
Workflow: dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json
Workflow SHA-256: 1ce188bc2ffd937bf5faba1dd72ae862eff68dfc98b26b2da4a748a2fec9cdc3
Workflow shape: 28 nodes / 47 edges / 8 acpx.agent_run nodes / 6 gates
Target: /Users/kriswang/work/projects/ai/okx-btc-k-line-demo
Target HEAD: c84807946493a603c789ee40c6a4c615aa846c3b
Target tree: 81520ac179f17c35c7c358c2a02102007cbe7cba
Task SHA-256: 9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a
Workspace check: scripts/verify-workspace.sh
Workspace check SHA-256: c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23
```
