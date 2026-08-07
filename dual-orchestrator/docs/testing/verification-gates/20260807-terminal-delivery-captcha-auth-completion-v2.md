# Verification Gate: Terminal-Delivery Captcha-Auth Completion v2

日期：2026-08-07

状态：`READY / NOT RUN`。

本 Gate 完成同一项尚未交付的 captcha-auth 业务任务。前一 workstream
`ws-20260807T134039Z-R47H` 是不可变失败证据，不 retry、不 resume；本轮使用 fresh
workstream 验证 `08417221` 修复后的完整 production workflow。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@1` 从 intake 到 terminal
  acceptance 完成 `/Users/kriswang/work/projects/ai/captcha-auth-app`。主结论是业务交付和
  workflow 是否 `COMPLETED`；若自然发生 intake revision，同时验证 verified previous
  proposal baseline 在 provider dispatch 前被完整投影，合法 delta 能进入后续 Codex
  review。不得为了证明 revision 人为制造 revision。

- Source of truth: target 的 `REQUIREMENT.md`、Git baseline 和最终 diff；persisted
  workflow definition/instance；`iterations/*.json`、`nodes/*.json`、每个
  `runs/*/run.json`；verified task-contract proposal/reference、compiled contract、design、
  plan、implementation、acceptance、change-scope evaluation、lineage、completion audit、
  workstream metrics；最终 PostgreSQL-backed verification、`mvn test` 和 README。

- Trigger precondition: Dual worktree clean，HEAD 包含 `08417221`；fresh CLI package
  在本轮构建并记录 hash；workflow 必须为 bundled
  `agent-lifecycle-terminal-delivery@1`，source SHA-256 必须为
  `5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479`，结构必须为
  29 nodes / 45 edges / 9 nominal Agent nodes / no handoff。Target 必须 clean，HEAD
  `7c7e35e89a66b14c29a335958ad90c8a92779fa0`，tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`，且只含已提交的
  `REQUIREMENT.md` 和 `VERIFICATION-GATE.md`。Claude 与 Codex minimal probes 必须 PASS。

- How precondition is checked before run: 记录 Dual SHA/status、package 时间、JAR
  path/mtime/SHA-256、workflow path/id/version/source hash/graph shape、target HEAD/tree/status、
  requirement/gate hash和双 provider probe 结果。使用 fresh JAR 在首个 Agent call 前解析并
  持久化 workflow identity；任一字段不符立即停止，不创建 workstream 或不进入 provider
  execution。

- PASS: workflow 达到 `COMPLETED`；terminal acceptance 为 `ACCEPT`，或无 unresolved
  must-fix、质量和 scope 不退化的 `ACCEPT_WITH_NOTES`；strict contract、lineage、change
  scope、completion audit 和所有 required artifacts 通过；最终实现覆盖手机号/邮箱注册登录、
  注册 OTP、登录前 image Captcha、安全和可访问性要求；PostgreSQL-backed verification 与
  `mvn test` 通过；README 可复现；无 handoff Agent。9 个 nominal execution 之外的每次 Agent
  execution 必须来自 persisted semantic revision，每个额外 provider occurrence 必须来自同一
  run 的 bounded correction disposition。若自然发生 intake revision，其 persisted prompt
  必须包含 exact verified baseline，revision 必须经 unchanged strict delta validator 后才能
  dispatch Codex review。

- FAIL: fresh profile 正确运行但 workflow 未完成；acceptance reject 或留下 must-fix；
  功能、测试、contract、lineage、scope、audit 或 artifact 失败；revision prompt 缺 verified
  baseline，非法 delta 绕过 validator，handoff 被调用，存在 unexplained Agent occurrence/runtime
  retry，或发生 permission denial、non-zero provider process、failed correction、terminal-state
  contradiction。

- INVALID: Dual/target dirty；SHA、target baseline、workflow identity/shape、fresh JAR、
  provider probes 或 required ledger source 不匹配；使用旧 JAR/server/session/workstream；运行
  `full`/external workflow；target 已有实现。`INVALID` 不得解释为部分成功，也不授权替代样本。

- Stop-loss: profile/freshness mismatch 必须在 provider 前停止。运行后遇到 permission
  denial、non-zero provider process、failed bounded correction、scope/lineage bypass、terminal
  contradiction 或 unexplained call 时立即 quiesce。单次 typed `NEEDS_REVISION` 是正常语义
  iteration；同一 review stage 第二次连续 `NEEDS_REVISION` 或总耗时超过 90 分钟时停止。不得在
  运行中改 prompt/parser/model、使用 runtime retry，或创建第二个 workstream 救场。

- Run artifact freshness: 报告必须记录 clean Dual SHA、Gate SHA、package start/end、JAR
  path/mtime/hash、target HEAD/tree/task hashes、persisted workflow identity/hash、runner kinds、
  actual provider/model identities、probe results、fresh workstream id 和 cutover time。缺任一字段
  则本轮 `INVALID`。

## Call Accounting

```text
nominal Agent definitions = 9
actual Agent executions = 9 + typed semantic-revision executions
actual provider occurrences = actual Agent executions + bounded-correction occurrences
```

所有 revision/correction token 和耗时必须计入，不得用 nominal count 掩盖实际成本。若 revision
和 correction 都为零，可额外声明本样本实际 `10 -> 9`；否则只能声明 fixed A10 subtraction
仍成立，不能声称总调用恰好为 9。

## Historical Boundary

前一 Gate
`20260807-terminal-delivery-captcha-auth-production.md` 保持 `FAIL / SAMPLE CONSUMED`。
`08417221` 只把 verified previous proposal baseline 收敛为 provider 前的唯一 typed projection；
它不放宽 delta validator，也不保证模型一定产生合法 revision。Human requirement
`revise -> intake.claude_brainstorm` 不属于本 Gate 的 Codex `NEEDS_REVISION` 路径，不在本轮
声称范围内。
