# Verification Gate: Terminal-Delivery Captcha-Auth Completion v5

日期：2026-08-11

状态：`INDEPENDENT REVIEW PASS / AUTHORIZED FOR PREFLIGHT / NOT YET RUN`

本 Gate 的唯一业务目标是完成尚未交付的 captcha-auth 项目。它同时验证 clean checkpoint
`edbb37f3` 的 revision-patch 边界，但不会人为诱导 `NEEDS_REVISION`：产品交付可以在无需
revision 时通过；只有真实 review 自然触发 revision 且完整链路通过，TaskContract Revision
Patch 才能从 `ENFORCED` 升级为 `PROVEN`。

旧 workstream `ws-20260810T072433Z-J75J` 仅作为历史失败证据，不 retry、resume 或复用 session。
本轮只能创建一个 fresh production `terminal-delivery` workstream。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@1` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`：Java 21、Spring Boot 3、Thymeleaf、
  PostgreSQL/Flyway；手机号或邮箱加密码登录；登录前 image Captcha；注册时完成短信或邮件
  OTP；同时完成测试、README、change-scope、lineage、completion audit 与 terminal acceptance。
- Source of truth: target `REQUIREMENT.md`、target baseline/final Git diff、persisted workflow
  definition/instance、typed proposal/review obligation/revision patch/canonical proposal/compiled
  contract artifacts、proposal references、run.json、change-scope evaluation、completion audit、
  terminal acceptance、PostgreSQL-backed verification、`mvn test` 和 README。Markdown 正文、模型
  自述和 workflow completion 单独均不能证明业务完成或 revision-patch 正确。
- Trigger precondition: Dual execution commit 必须为 clean SHA 且 ancestry 包含 `edbb37f3` 和
  本 Gate 的已审查提交；target 必须保持 clean baseline HEAD
  `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且没有实现文件；
  workflow identity/shape、fresh Dual package、target 空实现基线、隔离 PostgreSQL 可用性与
  Claude/Codex minimal probes 全部 PASS。Codex probe 是独立外部 preflight；runner 内建 probe
  只验证 terminal-delivery 的首个 runner（Claude），二者不得混称为 runner 自动双 probe。
  目标项目尚无 backend/frontend 工程，因此启动前不伪造不存在的应用测试；应用构建与页面验证
  属于运行后的业务 PASS 条件。
- How precondition is checked before run: 记录 Dual HEAD/status/checkpoint ancestry、target
  HEAD/tree/status/file list/requirement hash、workflow source hash/id/version/node/edge/Agent/handoff
  shape、package start/end、CLI JAR path/hash/mtime、runner kind、数据库可用性和两个 ACPX probe 结果。
  独立 Codex probe 与 PostgreSQL probe 必须先写入
  `dual-orchestrator/target/verification-preflight/captcha-auth-v5/`；`run-real-case.sh` 随后在创建
  workstream 前再次校验 fresh artifact 并执行 Claude probe。persisted workflow identity 是创建后的
  完整性核对项：若与 frozen definition 不符，本轮立即 `INVALID` 并 quiesce，不把它伪装成启动前
  已知事实。任一启动前检查无法形成证据时不得创建 workstream。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed acceptance review 同时满足
  `decision=ACCEPT_WITH_NOTES`、`blocking=false`、`must_fix=[]`，且 completion audit 无适用 artifact
  缺失/无效、change-scope PASS、lineage references 全部有效；手机/邮箱注册登录、OTP、Captcha、安全与可访问性
  要求完整；PostgreSQL-backed verification、`mvn test`、README、change-scope、lineage、completion
  audit 全部 PASS；handoff 不存在；所有 Agent execution、semantic revision 与 bounded correction
  均可由 typed ledger facts 解释并完整计入 token/耗时。
- FAIL: fresh profile 正确执行但 workflow 未完成，terminal acceptance 拒绝或留下 must-fix，业务
  功能、tests、PostgreSQL、README、contract、lineage、change-scope 或 completion audit 任一失败，
  handoff 被执行，或出现无法解释的 Agent/provider 调用、runtime retry、permission denial、non-zero
  provider process、failed correction、scope/lineage bypass 或 terminal contradiction。若 revision
  自然触发，输出完整 proposal、错误 base id/hash、遗漏或新增 obligation、重复/非法 patch target、
  merge 后 canonical validation 失败、raw patch 到达 reviewer/compile，均立即 FAIL。
- INVALID: Dual/target dirty，checkpoint ancestry、target baseline/hash、workflow identity/shape、fresh
  JAR、runner kind、任一 provider probe 或 ledger source 不符，使用旧 JAR/server/session/workstream，
  运行 `full`/external workflow，target 在启动前已有实现，或 preflight 不能证明运行工件包含本 Gate
  checkpoint；这些情况不得解释为部分成功。
- Stop-loss: profile/freshness mismatch 必须在 provider 前停止；operator 必须在运行中监控并强制
  执行以下止损：出现 permission denial、
  non-zero provider process、failed correction、contract/scope/lineage bypass、terminal contradiction
  或 unexplained call 立即 quiesce；同一 review stage 连续第二次 `NEEDS_REVISION` 或 wall-clock 达到
  90 分钟时中断并走 runner 的 terminate/reconcile/session-reap 路径；这些条件不是 runner 的自动
  revision/time monitor。运行中不改 prompt/parser/
  model/workflow，不 retry/resume，不创建第二 workstream 救场。
- Run artifact freshness: Gate review PASS 后先选择性提交本文和 active plan，得到新的 clean execution
  SHA；从该 SHA 执行 fresh Maven package，记录 build times、CLI JAR path/mtime/SHA-256、workflow
  source SHA-256、Dual/target Git identity、数据库 probe、两个 provider probe 证据和唯一 fresh workstream id。
  工件或证据不新鲜则本轮 `INVALID`。

## External Preflight Evidence

本节是一次性 verification procedure，不是 runner 新能力。Gate 提交且 fresh package PASS 后，
再创建 Git ignored 目录（避免后续 clean build 删除启动前证据）：

```text
dual-orchestrator/target/verification-preflight/captcha-auth-v5/
```

在启动 workstream 前必须完成并保存：

1. Codex minimal probe：使用新的短生命周期 session，执行与 runner 相同的 `--deny-all`、
   `--non-interactive-permissions fail`、180 秒 timeout 探测；保存 prompt、stdout、stderr、exit code
   和 session close 结果。输出必须形成 completed ACP response，exit code 必须为 0。
2. PostgreSQL probe：启动隔离 `postgres:16-alpine` 容器
   `dual-captcha-auth-v5-postgres`，绑定 `127.0.0.1:55432`，数据库为 `captcha_auth_test`；使用容器内
   `pg_isready` 等待就绪，再用容器内 `psql` 执行 `SELECT 1`。保存 image identity、container id、
   endpoint（不记录密码）、health output、query output 和 exit code；query 必须精确返回 `1`。
3. 将上述证据及 Dual/target/workflow identity 汇总为 `external-preflight.json`。任一字段缺失、
   probe 非零、数据库 query 不为 `1`，均为 `INVALID`，不得启动 `run-real-case.sh`。

runner 创建 case report 后，operator 将这份 preflight 目录复制到该 report 的 `external-preflight/`
目录，作为 Gate 的持久证据；复制前后逐文件 SHA-256 必须一致。runner 内建 Claude probe 继续由
case report 的 `preflight.json`、`provider-probe-result.json` 和 stderr 证明。

## Frozen Workflow

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@1
source: dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json
source sha256: 5f8dce015e20199aceac1e541087a5099effa0ade38e504f2708b74e76d91479
nodes: 29
edges: 45
nominal acpx.agent_run nodes: 9
handoff nodes: 0
```

## Revision-Patch Conditional Proof

只有真实 review 自然产生 `NEEDS_REVISION` 时才评估本节：

```text
verified canonical Proposal v3
  + persisted typed review obligations
  -> revision prompt requires task_contract_revision_patch only
  -> patch exact base proposal_id/sha256
  -> obligation set exact match
  -> deterministic merge preserves untouched facts
  -> canonical Proposal v3 passes mechanical/coverage/delta validation
  -> immutable revision-derived reference is published
  -> next reviewer and compiler consume canonical proposal, never raw patch
```

本节 PASS 的 source of truth 是 revision run 的 strict AgentResultEnvelope、derived sidecar、
`proposal_derivation`、base/current proposal references、reviewer input refs 和 compiled contract refs。
如果没有自然 revision，本节记为 `NOT_APPLICABLE`，TaskContract Revision Patch 保持 `ENFORCED`，
不得因为产品 Gate PASS 而声称 `PROVEN`。

## Call Accounting

- nominal Agent nodes：9；
- 每个真实 `NEEDS_REVISION` 可增加对应的 semantic revision Agent execution；
- 每个 Agent run 最多一次既有 bounded correction，必须作为独立 occurrence 全量计入；
- provider/model 名称不是 Gate 的业务判断来源，运行时不切换模型、不比较模型；
- 不设 token 金额门槛，但必须保留实际调用数、input/output/cache token 和 wall-clock 证据。

## Maturity Discipline

- captcha-auth 只有业务、测试和 terminal acceptance 全部通过时才可声明完成；
- deterministic checkpoint `edbb37f3` 当前为 `ENFORCED`，不是 `PROVEN`；
- 没有自然 revision 时不制造样本、不降低产品 Gate 结论，也不升级 patch 成熟度；
- 本 Gate 不授权 schema 扩张、workflow 修改、parser fallback、额外 retry、registry/engine/module
  或新的平台能力。

## Result

`PENDING`。Gate 独立同行评审已 `PASS`；当前授权按本文顺序执行 fresh package、external
preflight 和 runner preflight。只有全部前置条件 PASS，才能创建唯一 fresh workstream。
