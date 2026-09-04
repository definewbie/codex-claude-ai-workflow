# OKX Fresh E2E After Human Revision Authority Verification Gate

日期：2026-09-04  
状态：`DRAFT / PROVIDER NOT STARTED`

## 1. 唯一目标

使用当前已评审 Dual JAR 创建一个 fresh OKX workstream，完整运行到 `COMPLETED`，并通过目标仓库测试。此前的 remediation 投影与 human revise authority 修复只作为运行中哨兵，不替代完整 E2E 主判定。

## 2. Verification Gate

- Target: 唯一 fresh workstream 从初始节点运行到 terminal `COMPLETED`，contract、design、implementation、self-test、acceptance、completion audit 与 accepted delivery candidate 全链闭合，目标仓库 `mvn test` 通过。
- Source of truth: frozen workflow、`workflow/current.json`、node/gate documents、`events.jsonl`、run metadata、validated AgentResult、TaskContract、occurrence-scoped repository/scope evidence、human decisions、completion audit、accepted delivery candidate 与目标测试结果。Markdown 正文和 console 输出不参与状态判定。
- Trigger precondition: §5 的 Dual code checkpoint/tree 必须是当前 clean HEAD 的祖先，二者之间只能有本 Gate、active plan、评审补录及其 review 等 docs-only 变更；fresh JAR SHA 必须保持与该 code checkpoint 绑定。workflow、target HEAD/tree、需求、workspace check 与 reviewed acpx identity 均与 §5 一致；target tracked clean；source checkout 与 persisted effective workspace 的 target-owned check 均通过；Claude/Codex minimal probes 通过并关闭。Codex probe 与业务 run 必须使用同一 isolated `CODEX_HOME` 和 `gpt-5.4`。
- How precondition is checked before run: 复算全部 Git/SHA/工作流形状；用当前 HEAD fresh package；执行 workflow validation、Doctor 与 source-side check；以 `start` 且不带 `--run` 创建唯一 workstream，再从持久化 workspace identity 定位 effective workspace 并执行同一 check。任一项失败都不得启动业务 provider。
- PASS: workflow=`COMPLETED`；全部 human gate 由用户本人裁定；所有 typed contract、lineage、scope、self-test、acceptance、completion 与 accepted delivery candidate 证据有效；在最终 effective workspace 原样执行目标仓库 `mvn test` 通过；运行中未违反 §3 哨兵。本 Gate 不要求把 candidate apply/commit/finalize 到 target checkout。
- FAIL: 任一业务或边界验收失败；provider/adapter fatal；typed authority、occurrence binding、scope、completion 或 delivery 不一致；按当前语义应产生新 iteration 的带非空 note human `revise` 被 revision budget 错误送入 recovery；业务执行开始后自然发生 ledger 持久化失败。
- INVALID: identity/workspace preflight 失败、使用旧 JAR、复用旧 workstream、使用 autorun、Codex probe 与业务 run 的 isolated home/model 不一致、手改 ledger/evidence/TaskContract、人为制造 revision/scope violation，或失败后修改输入原地重跑。`INVALID` 不算部分成功。
- Stop-loss: provider fatal；authority/binding 漂移；同一 blocker 无新证据重复两次；同一 scope repair 以相同 path set 再次失败；累计 active provider runtime 达 120 分钟。operator 依据 ledger 的 execution 时间戳累计，human gate 等待时间不计。触发后停止当前样本，不用局部修补继续消耗。
- Run artifact freshness: 记录 Dual/JAR/workflow/target/task/check/acpx identity、新 workstream/workspace id、每个 run/session id、Codex run metadata 的 model identity、human packet/decision、validated artifacts、completion/delivery evidence与最终测试结果。

## 3. 运行中哨兵

这些哨兵只观察自然路径，不是 PASS 的额外触发要求：

1. 非 acceptance failure-bearing result 的 `verification_remediation` 必须为 `NONE`；业务 revision 只能经 typed decision、obligation 与 workflow edge 路由。
2. 若自然出现 scope violation，只能经 occurrence-scoped evidence 进入既有 implementation-repair Gate；不得人为制造。
3. 若用户在任何允许 `revise` 的 human gate 提交非空 note：Gate 必须先保持 `WAITING_FOR_HUMAN`；决定与 note 必须持久化；下一语义 iteration 必须被创建，即使自动 revision budget 已耗尽；该人工作用不得产生 `revision_limit_reached` 或 `WAITING_FOR_RECOVERY`。
4. 如果本次没有自然 revision 或 scope violation，只报告“未触发”，不得据此宣称相应 real-case 能力已证明，也不得为了触发它而改输入。

## 4. 状态转移闭合

| 项 | 本 Gate 要求 |
|---|---|
| 前置状态 | 当前 gate instance 为 open，workflow=`WAITING_FOR_HUMAN`，允许动作由 definition 声明 |
| 命令 owner | 用户通过 `review decide` 或 `approve` 作出决定；LLM、autorun 与 projection 无权伪造 `revise` |
| 持久化顺序 | gate decision/note → source/gate node completion → 新 iteration/后继 node → current/events；每一步均须能由 ledger 重放 |
| 中途失败 | preflight 前的环境写入失败判 `INVALID`；业务开始后的 ledger 写入失败判 `FAIL` 并立即停止，不能把部分写入解释为成功，也不原地修补或 resume |
| retry/resume | 仅 execution failure 使用 retry；human revise 是 semantic repair，不是 retry；本 Gate 不为旧 ledger 增加兼容恢复 |
| terminal | `COMPLETED` 后只允许读取、审计与 delivery 操作，不得再次 run/revise |
| 历史兼容 | `ws-20260903T225544Z-HHZL` 与 `ws-20260904T040052Z-3B2P` 只作缺陷证据，禁止 resume |

## 5. Frozen Inputs

```text
Dual code checkpoint: a1a70d9de31317902e6b3621a22e2f933773d925
Dual code tree: 61bc6f6b8c7eeb3cfbea67323b6340682ad50ae6
Execution HEAD rule: code checkpoint must be an ancestor of clean HEAD; intervening changes must be docs-only
Remediation checkpoint: 509248e4d7e742d367ba73eb8ba4a1be97ba0e67
Human revision authority checkpoint: 425abf25c3c468c8ff679f36d35faa0638ac699b
Fresh JAR: dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar
Fresh JAR SHA-256: fd6cd3358c03c9ca99e8a77359084e037ffda5f58243ab2fe8dbfa5cd74fe409
Fresh package: BUILD SUCCESS at 2026-09-04T19:56:15+08:00
Workflow: dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json
Workflow SHA-256: 1ce188bc2ffd937bf5faba1dd72ae862eff68dfc98b26b2da4a748a2fec9cdc3
Workflow shape: 28 nodes / 47 edges / 8 acpx.agent_run nodes / 6 gates
Target: /Users/kriswang/work/projects/ai/okx-btc-k-line-demo
Target HEAD: c84807946493a603c789ee40c6a4c615aa846c3b
Target tree: 81520ac179f17c35c7c358c2a02102007cbe7cba
Task SHA-256: 9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a
Workspace check SHA-256: c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23
Codex: isolated CODEX_HOME with gpt-5.4
Reviewed acpx: /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/.tools/acpx-e1/bin/acpx
Reviewed acpx upstream commit: fd173f04aa1b56f9e3f5ca5190c034ddcae28792
Reviewed acpx patch SHA-256: 41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c
Reviewed acpx policy schema: claude_execution_policy.v2
```

Codex pin 只记录既有观测：继承环境中的 `gpt-5.6-sol` probe 失败，隔离的 `gpt-5.4` probe 通过；不声明未经证明的因果。

## 6. End-to-End Invariant

| 维度 | 机械边界 |
|---|---|
| Declared by | 冻结 workflow、需求、JAR 与 workspace-check identity |
| Proven by | persisted node/gate/event/run documents、validated artifacts、completion/delivery evidence 与目标测试 |
| Consumed by | 既有 contract、decision、routing、acceptance、completion 与 delivery 链 |
| Enforced before | identity/workspace 在首个业务 provider 前；typed facts 在路由和外部副作用前 |
| Failure route | 前置失真为 `INVALID`；业务/边界失败为 `FAIL`；重复 blocker 触发 stop-loss |
| Cannot-bypass test | human-authority tests 守住真实 revise、伪造调用与 autorun；fresh 单样本禁手改/人为造错；R1 crash consistency 仍是已声明未闭合残余 |

## 7. 非目标与授权顺序

- 不恢复旧 workstream，不做旧 ledger 兼容修复，不重新验证 E1/S2，不改网络、模型适配、workflow、schema 或产品需求。
- 不执行 accepted delivery candidate 的 target apply/commit/finalize；这些外部副作用不属于本 Gate 的 E2E 完成判定。
- 不以 call 数、是否出现 revision、是否出现 scope violation 作为主 PASS。
- 本 Gate 与 active plan 先独立只读评审；P0/P1 清零后再提交 docs checkpoint。
- 提交后先做 non-provider preflight 与既有预授权 minimal probes；全部通过后，才创建唯一 fresh workstream 并分段运行到每个人工 Gate。
