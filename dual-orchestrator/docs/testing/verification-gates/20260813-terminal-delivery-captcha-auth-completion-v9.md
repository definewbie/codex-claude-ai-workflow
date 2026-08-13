# Verification Gate: Terminal-Delivery Captcha-Auth Completion v9

日期：2026-08-13

状态：`CONSUMED / FAIL / PRODUCT NOT ACHIEVED`

本 Gate 的唯一主目标是完成 captcha-auth 产品。checkpoint `b2306882` 修复的 self-test semantic repair
routing 只在真实 self-test 自然产生 `SELF_TEST_INCOMPLETE` 或 `BLOCKED` 时形成条件性技术结论；不得为了
证明该边界而制造测试失败、修改目标代码、复用旧 workstream/session 或增加 Agent 调用。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@3` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。业务范围保持 `REQUIREMENT.md` 原文：Java 21、
  Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；手机号或邮箱加密码登录；登录前 image Captcha；注册时
  完成短信或邮件 OTP；完成测试、README、change-scope、lineage、completion audit 与 terminal acceptance。
- Source of truth: target `REQUIREMENT.md`、Git baseline/final diff、persisted workflow definition/instance、
  validated self-test contract sidecar、`implement.self_test_decision` node outcome、iteration documents、human
  gate document、每个 `run.json`、change-scope evaluation、completion audit、terminal acceptance、
  PostgreSQL-backed verification 和 `mvn test`。Markdown、UI 文案、模型自述或单独 process exit code 不能
  单独证明 routing 或产品成功。
- Trigger precondition: Dual 必须处于 clean SHA `901c04324e1ab0c92b40df9602d6215213879768`，且包含
  implementation checkpoint `b2306882d16d8b9d31b511dbea8d1571c9a3e5b3`。target 必须保持 clean HEAD
  `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`。fresh package、workflow identity、
  隔离 PostgreSQL 和双 provider probes 必须全部 PASS。
- How precondition is checked before run: 在创建 workstream 前记录 Dual/target Git identity 与 clean status、
  checkpoint ancestry、requirement hash、workflow source hash/shape、CLI/server JAR hash/mtime、PostgreSQL
  image/container/endpoint/health/query，以及 Claude/Codex minimal probe 的 session、exit code 和结果。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed tests、`mvn test`、README、
  change-scope、lineage 和 completion audit 全部通过；所有 Agent node terminal outcome 与 artifact contracts
  一致，没有未经解释的 recovery/terminal contradiction。
- FAIL: workflow 未完成、terminal acceptance 拒绝或留下 must-fix、业务功能/测试/README/scope/lineage/
  completion 任一失败；strict parser/schema/semantic/artifact validation 失败；self-test 的 typed decision 被
  绕过；revision budget 错误共享或未受限；permission/auth/process/correction/timeout/interruption 形成 primary
  failure；human gate 被自动越过；或者 ledger 与目标副作用矛盾。
- INVALID: Dual/target dirty，identity/hash/workflow/JAR/probe 不符，复用旧 session/workstream，operator 在
  运行中修改代码、prompt、parser、workflow、revision limit 或 provider 配置，或证据不能证明本轮使用
  clean execution SHA。self-test 一次返回 `SELF_TEST_COMPLETE` 不会使产品样本无效，只意味着 repair 子边界
  保持 `ENFORCED / NOT PROVEN`。
- Stop-loss: freshness/identity/probe 不符时在 provider 前停止；strict contract、scope/lineage、failed
  correction、timeout/interruption 或 terminal contradiction 出现后停止，不 retry、不创建第二 workstream。
  `WAITING_FOR_HUMAN` 是合法暂停点；wall-clock 达 90 分钟、typed revision budget 耗尽进入 recovery，或
  同类技术失败重复两次时终止并执行既有 terminate/reconcile/session-reap。
- Run artifact freshness: clean SHA `901c0432` 上的 fresh package 已成功；CLI JAR SHA-256
  `f72850e2f47f3ee9b0165a301c7e7d6d8db5d4a499a8590027a69fca7761d45b`，server JAR SHA-256
  `e928b20877c76873c51e6bad3e51c9a75d3ead41d9b0dd22008b33246ea3cd8f`。Gate review/commit 后必须重新
  核对 Git clean、JAR mtime/hash 与 checkpoint ancestry；若文档提交不改 production JAR，可复用本次 fresh
  package，但必须保存 hash 一致证据。

## Conditional Self-Test Repair Verdict

该子结论不改变产品 Gate 的主结论，也不是必须触发的目标。

### `SELF_TEST_COMPLETE`

- `implement.claude_self_test_report` 的 validated sidecar 决定为 `SELF_TEST_COMPLETE`；
- `implement.self_test_decision` 必须消费同一 lineage ref 并路由到 `implement.artifact_validation`；
- 不允许创建 implementation repair iteration 或 human repair gate。

### `SELF_TEST_INCOMPLETE`

- decision 必须在 artifact validation 和 acceptance context 之前路由回 `context.implement_claude`；
- iteration 的 trigger owner 必须是 `implement.self_test_decision`，target 必须是
  `context.implement_claude`；
- 该 owner/target 的 `max_revisions=1`，重复 incomplete 必须进入 revision-limit recovery；
- 之后的 human acceptance repair 即使 target 相同，也必须拥有独立预算。

### `BLOCKED`

- decision node 执行必须为 `status=COMPLETED` 且 `outcome=BLOCKED`，workflow 必须进入
  `WAITING_FOR_HUMAN`；
- `gate.human_implementation_repair` 必须保存 `source_outcome=BLOCKED` 与 self-test evidence ref；
- 非空 human note + `revise` 后才允许进入 `context.implement_claude`；autorun 不得自动越过。

若自然触发任一 repair 分支并完整满足上述条件，可将该分支标记 `PROVEN`；若没有触发，checkpoint
`b2306882` 保持 `ENFORCED / NOT PROVEN`。

## Frozen Workflow And Artifacts

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@3
source: dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json
source sha256: 0984ee517851c508e0042c1e40f4e592fc8fda4581a798b919b0671f718ec44d
nodes: 31
edges: 52
nominal acpx.agent_run nodes: 9
handoff nodes: 0
```

Target baseline 允许且只允许原有 tracked baseline 文件；`REQUIREMENT.md` 是业务需求 SoT，历史
`VERIFICATION-GATE.md` 不是实现文件，也不参与 workflow routing。

## Human Interaction

当 workflow 为 `WAITING_FOR_HUMAN` 时，优先通过 UI 阅读 review packet、核对 `sourceOutcome` 与 evidence，
填写明确非空 note 后选择允许的 action。CLI 必须使用本轮 fresh JAR，禁止调用可能仍指向旧 JAR 的裸
`dualctl`：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action revise --note "<明确修复决定>" \
  --workdir <workspace> <workstream-id>
```

UI/CLI 只是 command 入口；最终判定只读取 ledger 的 open/resolved gate、note、lineage 与后续 routing。

## Preflight And Execution Order

1. 独立 review 本 Gate；修正 blocking findings后提交 Gate + active plan，获得 clean execution SHA。
2. 核对 fresh package JAR hash/mtime、workflow source hash/shape、Dual/target identity 与 clean status。
3. 清理旧 captcha-auth probe container/端口，启动隔离 `postgres:16-alpine`，保存 `pg_isready` 与
   `SELECT 1` 证据。
4. 非 provider preflight PASS 后，运行且仅运行短生命周期 Codex 与 Claude minimal probes。
5. 双 probes PASS 后创建且仅创建一个 fresh terminal-delivery@3 workstream；不 resume v8/v2，不复用
   Agent session。
6. 按 Stop-loss 和 Human Interaction 执行到 terminal；保存 report、external preflight hashes、调用/token/
   duration/revision/correction 统计。

## Maturity

- checkpoint `b2306882`：`ENFORCED / NOT PROVEN`；
- deterministic evidence：focused `179/0`（`WorkflowFileLedgerTest`、`BuiltinHandlerTest`、
  `ContractRegistryTest`、`TerminalDeliveryWorkflowDefinitionTest`、`WorkflowDefinitionLoaderTest`、
  `LifecycleWorkflowDefinitionFactoryTest`、`WorkflowVariantSelectorTest`），full reactor `1218/0/0/0`，
  两轮独立 implementation review 与本 Gate 独立 review PASS；
- captcha-auth：`NOT ACHIEVED`，只有本 Gate 的产品 PASS 条件全部成立后才可升级；
- 本 Gate 不新增 runner monitor、parser fallback、retry、module、registry、engine 或平台能力。

## Real-Case Evidence

- Workstream：`ws-20260813T115256Z-8G74`；workflow identity 为
  `agent-lifecycle-terminal-delivery@3`（31 nodes / 52 edges / 9 nominal Agent nodes）。
- Fresh package、target identity、PostgreSQL 和双 provider probes 均 PASS；本轮不是 `INVALID`。
- 首个 Agent run `run-1786621976773` 正常 `end_turn`，process exit code 为 `0`；strict JSON、schema 和
  Agent result contract 均有效，未触发 presentation normalization 或 bounded correction。
- required artifact contract 在 reviewer 与实现副作用之前 fail closed：proposal 将不存在的 `README.md`
  声明为 `MODIFY`，产生 `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`。workflow 进入
  `WAITING_FOR_RECOVERY`，target 与 fresh worktree 均无业务代码改动。
- 失败同时暴露 Dual-owned 信息闭环缺口：`TaskContractProposalValidator` 用 workspace filesystem 判定
  `CREATE/MODIFY`，但 seed-only intake prompt 禁止工具调用，context 中的 `repo-map.json` 又因尚无
  task contract / Git evidence 而为空，未向 Agent 投影任何 authoritative existing-path fact。Dual 因而要求
  Agent 声明一个它无法从输入确定、但 validator 自己掌握的事实。
- 本轮没有到达 self-test routing；checkpoint `b2306882` 保持 `ENFORCED / NOT PROVEN`。

## Result

`FAIL / CONSUMED / PRODUCT NOT ACHIEVED`。不得 retry 本 workstream，也不得在修复 scope-operation 事实投影
边界前创建第二个样本。
