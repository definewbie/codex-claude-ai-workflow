# Verification Gate: Terminal-Delivery Captcha-Auth Completion v4

日期：2026-08-10

状态：`READY / NOT RUN`

本 Gate 的唯一目标是完成尚未交付的 captcha-auth 业务任务。旧 workstream
`ws-20260810T011244Z-8YVB` 只是历史证据，不 retry/resume；本轮使用最新
clean Dual 代码创建一个 fresh production `terminal-delivery` workstream。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@1` 完整交付 `/Users/kriswang/work/projects/ai/captcha-auth-app`，实现 Java 21 + Spring Boot 3 + Thymeleaf + PostgreSQL/Flyway 的手机号或邮箱注册登录，注册 OTP，登录前 image Captcha，并完成测试、README、change-scope、lineage 与 terminal acceptance。
- Source of truth: target `REQUIREMENT.md`、target baseline/final Git diff、persisted workflow definition/instance、typed proposal/contract/revision/review artifacts、run.json、change-scope evaluation、completion audit、terminal acceptance、PostgreSQL-backed verification、`mvn test` 和 README；workflow completion 单独不代表业务交付成功。
- Trigger precondition: Dual 必须为 clean SHA，且包含 typed-scope prompt checkpoint `07a11db3` 与 closure checkpoint `6370d9e7`；target 必须保持 clean baseline HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256 `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且没有任何实现文件；fresh package、workflow identity/shape、Claude 与 Codex minimal probes 全部 PASS。
- How precondition is checked before run: 记录 Dual HEAD/status/ancestry、target HEAD/tree/status/file list/hash、workflow source hash/id/version/node/edge/Agent/handoff shape、package start/end、CLI JAR hash/mtime、`runner-kind --variant terminal-delivery`和两个 ACPX provider probe 结果；`run-real-case.sh` 再次在创建 workstream 前强制 fresh artifact 与 first-runner probe，创建后核对 persisted workflow identity。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或无 unresolved must-fix、质量、scope 或 lineage 退化的 `ACCEPT_WITH_NOTES`；手机/邮箱注册登录、OTP、Captcha、安全、可访问性和所有 required tests 完整；PostgreSQL-backed verification 与 `mvn test` PASS；README 可复现；change-scope、lineage、completion audit PASS；handoff 不存在；超过 9 个 Agent executions 的每次调用均由 persisted semantic revision 解释，额外 provider occurrence 均由同一 run 内最多一次 bounded correction 解释并全量计入 token/耗时。
- FAIL: fresh profile 正确执行但 workflow 未完成，terminal acceptance 拒绝或留下 must-fix，业务功能、tests、PostgreSQL、README、contract、lineage、change-scope 或 completion audit 任一失败，handoff 被执行，或出现无法解释的 Agent/provider 调用、runtime retry、permission denial、non-zero provider process、failed correction 或 terminal contradiction。
- INVALID: Dual/target dirty，checkpoint ancestry、target baseline/hash、workflow identity/shape、fresh JAR、runner kind、任一 provider probe 或 ledger source 不符，使用旧 JAR/server/session/workstream，运行 `full`/external workflow，或 target 在启动前已有实现；这些情况不得解释为部分成功。
- Stop-loss: profile/freshness mismatch 必须在 provider 前停止；运行中出现 permission denial、non-zero provider process、failed correction、scope/lineage bypass、terminal contradiction 或 unexplained call 立即 quiesce；同一 review stage 连续第二次 `NEEDS_REVISION` 或 wall-clock 达到 90 分钟时中断并走 runner 的 terminate/reconcile/session-reap 路径；运行中不改 prompt/parser/model/workflow，不 retry/resume，不创建第二 workstream 救场。
- Run artifact freshness: Gate 提交后从该 clean SHA 执行 fresh Maven package，记录 build times、CLI JAR 绝对路径/mtime/SHA-256、workflow source SHA-256、Dual/target Git 身份、两个 probe 证据和 fresh workstream id；工件或证据不新鲜则本轮 `INVALID`。

## Frozen Workflow

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@1
source sha256: 5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479
nodes: 29
edges: 45
nominal acpx.agent_run nodes: 9
handoff nodes: 0
```

## Maturity Discipline

- captcha-auth 产品只有在本 Gate 的业务、测试和 terminal acceptance 全部通过时才可声明完成。
- typed-scope prompt semantics 当前仍为 `PROJECTED`。只有本轮自然出现 scope revision 且 provider 正确直接修改 typed entries 时，才记录额外 `PROVEN` 证据；未触发 revision 不影响产品 Gate PASS。
- 本 Gate 不授权新 schema、workflow、engine、registry、parser fallback 或 prompt 调试。

