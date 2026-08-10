# Verification Gate: Terminal-Delivery Captcha-Auth Full v3

日期：2026-08-10

状态：`READY / CC REVIEWED / NOT RUN`。

本 Gate 授权一个全新 captcha-auth workstream。所有历史 captcha-auth workstream、
revision probe 和 candidate 只作为不可变证据，不 retry、resume 或作为本轮执行目标。

## Upstream Evidence

- presentation-normalization implementation：`8992824d68800ca1c1e1ecfae236b3d7b55a8a68`，
  deterministic `ENFORCED`。
- revision presentation probe v3：`INVALID / CONSUMED`；只记录验证 runner lineage path
  错误，不作为 PASS 证据。
- captured offline replay v3.1：`PASS / CONSUMED`；同一 fresh captured candidate 通过
  production strict parser、schema、mechanical、CO-1/CO-2 coverage、revision-delta、
  sidecar publication 与 verified proposal lineage。
- 上述证据不保证 provider 每次都会输出合法 envelope；本 full run 仍必须
  由 production boundary fail closed。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@1` 在一个 fresh workstream 中，
  从 intake 到 terminal human acceptance 完成
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。验证业务交付、strict contract、
  typed lineage、change scope、tests、completion audit 与 terminal-delivery 9-call nominal
  profile 均不退化。

- Source of truth: target `REQUIREMENT.md`、Git baseline/final diff；persisted workflow
  definition/instance、iterations/nodes/runs；verified proposal/reference、compiled contract、
  design/plan/implementation/self-test/acceptance artifacts；change-scope evaluation、completion audit、
  metrics；最终 `mvn test`、PostgreSQL-backed verification 和 README。

- Trigger precondition: Dual worktree clean 且 HEAD 包含
  `8992824d68800ca1c1e1ecfae236b3d7b55a8a68`、v3.1 replay PASS checkpoint 和本 Gate；
  target clean，HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`，tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`，且只有已提交的
  `REQUIREMENT.md`/`VERIFICATION-GATE.md`；requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`；
  target gate SHA-256
  `0dc1ee67b5979fdc43d0d6e250ab2eb825516fa216e75ea25dab5a1761d7a9d7`；
  workflow source SHA-256
  `5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479`，
  29 nodes / 45 edges / 9 `acpx.agent_run` / no handoff；fresh package 与 Claude/Codex
  minimal probes PASS。model 名称只记录，不作为 orchestration precondition。

- How precondition is checked before run: 记录 clean Dual/Gate SHA 与 ancestry，target
  HEAD/tree/status/file hashes，workflow path/id/version/hash/shape，package start/end 与 CLI jar
  hash/mtime，runner kinds 和两个 probe 结果。使用 fresh CLI 启动后，在第一个
  Agent call 前核对 persisted workflow identity；任一不符立即停止。执行命令必须
  显式使用 `--variant terminal-delivery` 和
  `--expected-runner-kind acpx_claude_session`。bundled variant 的 source SHA-256 目前
  不由 `run-real-case.sh` 自动计算，因此必须在启动前重算 source hash，并在
  workstream 创建后核对 persisted id/version 与 29/45/9/no-handoff 语义形状。

- PASS: workflow 达到 `COMPLETED`；terminal acceptance 为 `ACCEPT`，或无
  unresolved must-fix/质量/scope 退化的 `ACCEPT_WITH_NOTES`；所有 required
  artifacts、contract、lineage、change-scope 和 completion audit 通过；实现完整
  手机号/邮箱注册登录、注册 OTP、登录前 image Captcha、安全与可访问性要求；
  PostgreSQL-backed verification 与 `mvn test` 通过；README 可复现；无
  handoff artifact/Agent。实际 Agent executions 超过 9 时必须全部来自 persisted
  semantic revision；provider occurrences 超过 executions 时必须全部来自同一 run
  的唯一 bounded correction，且全部 token/耗时计入结果。

- FAIL: fresh profile 正确运行但 workflow 未完成；acceptance reject 或留下
  must-fix；业务功能、tests、contract、lineage、scope、audit、PostgreSQL 或 README
  任一失败；handoff 被调用；出现无法解释的 Agent/provider 调用、runtime retry、
  permission denial、non-zero provider process、failed correction 或 terminal contradiction。

- INVALID: Dual/target dirty；checkpoint ancestry、target baseline/hash、workflow identity/shape、
  fresh jar、runner kind、provider probes 或 ledger source 不符；运行了旧 jar/server/session/
  workstream；使用 `full`/external workflow；target 已有实现。`INVALID` 不得解释为
  部分成功，也不授权替代样本。

- Stop-loss: profile/freshness mismatch 必须在 provider 前停止。运行中遇到
  permission denial、non-zero provider process、failed correction、scope/lineage bypass、
  terminal contradiction 或 unexplained call 立即 quiesce。单次 typed `NEEDS_REVISION`
  是正常 semantic iteration；同一 review stage 连续第二次 `NEEDS_REVISION` 或总耗时
  超过 90 分钟时停止。运行中不改 prompt/parser/model/workflow，不 retry/resume，
  不创建第二 workstream 救场。`run-real-case.sh` 当前没有 wall-clock timeout
  参数；本轮由外部 runner 记录开始时间并在 90 分钟达到时向脚本发送中断，
  必须经脚本现有 interrupt trap 终止 provider、reconcile run 并关闭 sessions。

- Run artifact freshness: 报告必须记录 clean Dual/Gate SHA、package times、CLI jar
  path/mtime/hash、target HEAD/tree/task hashes、persisted workflow identity/hash、runner kinds、
  actual provider/model identities、probe results、fresh workstream id 和 cutover time。缺任一则
  `INVALID`。

## Call Accounting

```text
nominal Agent definitions = 9
actual Agent executions = 9 + typed semantic-revision executions
actual provider occurrences = actual Agent executions + bounded-correction occurrences
```

只有 revision/correction 都为零时，本样本才额外证明实际 `10 -> 9`。否则只声明
terminal-delivery 固定 A10 subtraction 成立，不声称总调用恰好为 9。

## Independent Review

CC 独立只读、代码级同行评审结论为 `PASS`：无 P0；一项 P1 为脚本未内建
90 分钟 wall-clock timeout，已在本 Gate 明确为外部监督的 stop-loss；两项 P2
分别是 expected runner kind 和 bundled workflow hash 检查隐式，已在 precondition 中显式
固定。评审确认 Gate 可从 `DRAFT` 提升为 `READY`。

## Result

尚未运行。
