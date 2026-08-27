# Verification Gate: Small Workflow / Captcha-Auth Mailpit

日期：2026-08-27  
状态：`INDEPENDENT REVIEW PASS / NON-PROVIDER PREFLIGHT AUTHORIZED / PROVIDER NOT AUTHORIZED`

## Verification Gate

- Target: 使用一个 fresh external workflow
  `agent-lifecycle-small-plan-human-review-experiment@1`，在 captcha-auth-app 的 clean baseline 上完成
  `MAILPIT-LOCAL-EMAIL-OTP-TASK.md`。必须同时证明产品功能完成、workflow `COMPLETED`、A5 plan 由用户直接审核、
  Agent runs 与 provider occurrences 均恰好为 `8`，且 correction、semantic revision、scope、lineage、self-test、
  acceptance 与 completion audit 不退化。
- Source of truth: frozen Dual/target/workflow/task/check identity；persisted workflow definition/instance；human gate ledger；
  typed proposal/design/plan/self-test/acceptance sidecars及 verified references；全部 `runs/*/run.json` 与 metrics；
  workspace-preflight.json；change-scope evaluation；completion audit；target Git diff；`mvn test`、Mailpit-backed test 与
  `docker compose config` 的退出码和完整日志。Markdown summary、console 叙述和模型自述不是控制事实。
- Trigger precondition: Dual HEAD 必须包含 execution-workspace checkpoint `212176b4`；external workflow identity 必须与
  Frozen Baseline 一致。target 必须保持 clean HEAD/tree，task 和 workspace check SHA 不变。fresh package、workflow
  validation、target-owned workspace check、Claude probe 与 Codex probe 必须全部 PASS。不得复用旧 workstream、worktree
  或 provider session。
- How precondition is checked before run: 复算所有 frozen SHA 与 Git cleanliness；执行 fresh Maven package 和 external
  workflow validation；provider probes 获得单独授权后各运行一次并闭合 session。使用 fresh JAR 仅创建 workstream，随后从
  persisted ledger 解析 effective workspace；调用 `real-case-workspace-preflight.sh capture` 在该 workspace 执行冻结的
  `scripts/verify-workspace.sh`，紧邻首次 `dualctl run` 前执行 `verify`。只有 evidence 同时为
  `status=passed / verificationStatus=passed` 才可开始第一个业务 Agent。
- PASS: 以下条件必须同时成立：
  1. workflow=`COMPLETED`，completion audit=`ready` 且 `gaps=[]`；
  2. Agent runs 和 provider occurrences 均恰好为 8，correction=0、semantic revision=0；
  3. persisted graph 不含 A6 plan-review 三节点，A5 typed plan 与 lineage 直接进入 human plan gate，用户本人批准后才实现；
  4. `docker compose up -d` 提供 PostgreSQL 与 pinned Mailpit，SMTP=`1025`、inbox UI=`8025`；dev profile 默认把 email OTP
     投递到 Mailpit，生产中立配置和外部 SMTP override 保持可用；
  5. 自动化测试通过真实 Mailpit container/API 取得投递邮件中的 OTP，并用该 OTP 完成邮箱注册；不得新增应用 OTP 泄漏
     endpoint、日志或 parser fallback；
  6. `mvn test` 全绿，既有 99 个测试不退化；`docker compose config` PASS；phone registration、Captcha、OTP lifecycle、
     session、CSRF、normalization、rate limit 与 fake adapters 不退化；
  7. README 完整记录本地启动、Mailpit UI/SMTP、人工邮箱注册步骤、override 与 development-only 边界；
  8. change scope=`PASS`、lineage 有效，terminal acceptance=`ACCEPT`，或 typed
     `ACCEPT_WITH_NOTES + blocking=false + must_fix=[] + missing_evidence=[]`。
- FAIL: identity/freshness 正确的有效样本中出现任一 correction 或 revision；runs/occurrences 不为 8；A6 节点出现；A5 plan
  未经用户 gate 进入实现；workflow 未完成；provider/adapter fatal；strict contract、scope、lineage、self-test、acceptance、
  completion audit 或 session closure 失败；Mailpit 只在文档中存在而无真实 SMTP-backed test；OTP 泄漏；既有测试或安全行为
  退化。首次 correction/revision 即使业务上可修复，也已使本轮 `9 -> 8` 目标失败。
- INVALID: 任一 frozen identity/hash 不符；旧 JAR/server；workspace preflight 未配置、缺失或未 verify PASS；workspace check
  因 Docker、Maven、Java、Testcontainers 或环境问题失败；probe 未通过；使用 bundled variant、autorun、`--skip-build`、旧
  workstream/worktree/session；运行中修改 Gate/task/workflow/provider/model/timeout/Dual；手工编辑 ledger；target 在创建
  worktree 前不 clean。INVALID 不得解释为部分成功，也不得启动第二个样本。
- Stop-loss: 任一 precondition 失败时不得开始业务 Agent。运行后首次 correction、revision、unexpected node、contract/scope/
  lineage failure、provider fatal、session closure failure或 60 分钟 active runtime 到达时终止并闭合 process/session，保存
  ledger 与 wire evidence。`WAITING_FOR_HUMAN` 不计 active runtime；human gate 必须由用户通过 UI 或 fresh JAR CLI 决定。
- Run artifact freshness: Gate review PASS 并提交 clean SHA 后 fresh package；记录 package/JAR/workflow/task/check/target SHA、
  workspace-preflight evidence 和 probes。workstream 创建后核对 persisted graph；最终保存完整 ledger、target diff、测试日志、
  metrics、session closure 与 completion evidence。

## Frozen Baseline

```text
Dual HEAD:
  212176b4927e70a102f71ca34bc5caac6d8b8be2
workflow:
  dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/
  agent-lifecycle.small-plan-human-review-experiment.json
workflow identity:
  agent-lifecycle-small-plan-human-review-experiment@1
workflow SHA-256:
  d41b835e4d8a8347b64984e085385030246976bbc485a866f215dfafc97faaec
workflow shape:
  28 nodes / 46 edges / 8 acpx.agent_run / 6 human.review
target:
  /Users/kriswang/work/projects/ai/captcha-auth-app
target HEAD:
  dc0ea5925f06bdfc30a8656148d6d21468ca12b6
target tree:
  6b71d91d572a30bae8e26083074094d8a35c84e3
task SHA-256:
  32a6f679c1db24f2475edf50c9b5935faa7e059f63f718c049056d7853972a0c
workspace check SHA-256:
  a1902c10e7a9fb1a9abda6c6650814cdf1fdd7273a2676878ff2313b238466c8
target baseline verification:
  scripts/verify-workspace.sh -> 99 tests / 0 failures / 0 errors / 0 skipped
  scoped Docker API workaround: -Dapi.version=1.44
```

## Procedure And Authorization Boundary

1. 本 Gate 与 active plan 独立 review，关闭 P0/P1 后选择性提交。
2. 提交后只执行 fresh package、workflow validation 与 source-side frozen identity/hash 复算；本步不创建 workstream，
   也不执行 workspace capture/verify。
3. provider probes 与 fresh workstream 仍需分别授权；workspace capture/verify 归 fresh-workstream 授权，且只能在
   `start(no-run)` 创建 workstream 后、首次业务 `run` 前执行。
4. 本轮手工逐段执行，不使用 autorun；每个 human gate 展示 typed packet、allowed actions 与后果，由用户本人决定。
5. 无论 PASS/FAIL/INVALID 都只消费一个样本，不 retry、不换模型、不创建第二个 workstream。

## Maturity Boundary

- execution-workspace eligibility：`ENFORCED WHEN CONFIGURED / NOT PROVEN`；
- small workflow graph：`VALIDATED / PRODUCTION PROFILE NOT MODELED`；
- small-profile `9 -> 8`、Mailpit 产品完成与 workspace check real-case value：`NOT PROVEN`。
