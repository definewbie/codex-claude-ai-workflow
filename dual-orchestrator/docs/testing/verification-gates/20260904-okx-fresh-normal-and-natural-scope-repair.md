# OKX Fresh Normal Path And Natural Scope Repair Verification Gate

日期：2026-09-04  
状态：`USER AUTHORIZED / PROVIDER NOT STARTED`

## Verification Gate

- Target: 使用当前 Dual checkpoint 和唯一 fresh workstream，从干净的 OKX 需求基线完成一次真实 small-workflow 运行，优先证明正常路径无回归。只有运行自然产生 `CHANGE_SCOPE_VIOLATION` 时，才继续证明 occurrence evidence -> human implementation repair -> 带 note 的 `revise` -> 新 iteration -> scope validation PASS 的完整闭环；不得人为制造越界。
- Source of truth: 持久化 workflow definition、`workflow/current.json`、node/gate documents、`events.jsonl`、run metadata、typed Agent contracts、occurrence-scoped change-scope evaluation ref/SHA、human gate decision、completion audit 和 accepted delivery candidate。Markdown 正文、console 文案、stable latest scope projection 和 operator 推测均不作为路由事实。
- Trigger precondition: Dual HEAD 是 `5fc8caa0` 的后代并包含 scope-repair checkpoint `22ae064c`；external workflow identity 为 `agent-lifecycle-small-plan-human-review-experiment@1`，SHA-256 为 `1ce188bc2ffd937bf5faba1dd72ae862eff68dfc98b26b2da4a748a2fec9cdc3`，形状为 `28 nodes / 47 edges / 8 agent runs / 6 human gates`；目标仓库 HEAD/tree 为 `c84807946493a603c789ee40c6a4c615aa846c3b / 81520ac179f17c35c7c358c2a02102007cbe7cba` 且 tracked clean；`REQUIREMENT.md` SHA-256 为 `9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a`；workspace check SHA-256 为 `c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23`；本样本通过 isolated `CODEX_HOME` 将 Codex model 固定为 `gpt-5.4`，不读取全局默认 `gpt-5.6-sol`。
- How precondition is checked before run: fresh package；external workflow validation；Dual/target/requirement/check identity 复算；在 source checkout 和创建后的 persisted effective workspace 各执行一次 target-owned workspace check；Claude minimal probe 与使用 isolated `CODEX_HOME / gpt-5.4` 的 Codex minimal probe 均通过并关闭；`start` 不带 `--run` 创建唯一 workstream，随后在首次业务 Agent 前复核 persisted workspace identity。任一项失败不得启动 provider。
- PASS: 以下 A 或 B 分支成立，且对应的终态质量条件全部满足。
  - **A - 正常路径**：没有自然 scope violation；workflow 经真实 human gates 到达 `COMPLETED`；typed contract、repository-fact binding、scope evaluation、self-test、acceptance、completion audit 与 delivery candidate 全部有效；目标 `mvn test` 通过。此分支只证明正常路径无回归，scope-repair 能力保持 `ENFORCED / NOT REAL-CASE PROVEN`。
  - **B - 自然人工修复路径**：自然产生的 `CHANGE_SCOPE_VIOLATION` 来自 `implement.artifact_validation`，其 Gate 只消费同 occurrence 的 canonical evaluation ref/SHA，并展示 unexpected/missing paths；current 进入 `WAITING_FOR_HUMAN`。用户本人以非空 note 执行 `revise`，新 iteration 重新实现并重新验证；后续 scope evaluation 为 PASS，workflow 最终满足 PASS A 的终态条件。此分支可把 scope-repair 标为 `REAL-CASE PROVEN`。
- FAIL: 正常路径中出现未闭合的 contract、lineage、scope、self-test、acceptance、completion 或 delivery failure；自然 scope violation 未进入声明的 repair gate、Gate 使用 stable/global fallback、无需 note 即可 revise、revise 未产生新 iteration、repair 后仍无法通过 scope validation；或 provider/adapter fatal 导致样本不能继续。
- INVALID: 复用或迁移旧 workstream；人为编辑 TaskContract、workspace、scope evidence 或 ledger 来触发/消除 violation；使用 autorun 自动批准 gate；旧 JAR、identity/check 漂移、workspace preflight 失败、人工绕过 persisted transition、同一 Gate 失败后修改 fixture 原地重跑，或创建第二个样本。INVALID 不是部分成功。
- Stop-loss: 前置失败立即停止；运行后遇到 repository-fact/binding sentinel 失败、provider fatal、同一 review blocker 在无新证据下重复两次、同一 scope repair 后仍以相同 path set 失败、或 120 分钟 active provider runtime 时停止。正常 human review/revise 不因追求固定调用数而被压制。
- Run artifact freshness: 记录 fresh JAR SHA/build time、workflow/target/task/check identities、新 workstream/workspace ids、每个 run/session id、human packet/decision、scope evaluation ref/SHA、terminal completion/delivery evidence和最终 target test evidence。

## 边界说明

本 Gate 不以“必须触发 scope violation”为成功条件。Agent 没有自然越界时，禁止通过改 TaskContract、增加无关文件、篡改声明 scope 或提示模型越界来测试 repair route。网络安全、E1、调用数优化和旧 OKX workstream 恢复均不属于本 Gate。

## Frozen Inputs

```text
Dual HEAD: 5fc8caa0 (must contain 22ae064c)
Fresh JAR SHA-256: c38d212e4bf7e169fe91e6f287339cc154694db307f0102c5a80487fc77b4717
Codex model: gpt-5.4 via isolated CODEX_HOME
Workflow: dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json
Workflow SHA-256: 1ce188bc2ffd937bf5faba1dd72ae862eff68dfc98b26b2da4a748a2fec9cdc3
Target: /Users/kriswang/work/projects/ai/okx-btc-k-line-demo
Target HEAD: c84807946493a603c789ee40c6a4c615aa846c3b
Target tree: 81520ac179f17c35c7c358c2a02102007cbe7cba
Task: REQUIREMENT.md
Task SHA-256: 9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a
Workspace check: scripts/verify-workspace.sh
Workspace check SHA-256: c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23
```
