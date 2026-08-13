# Verification Gate: Terminal-Delivery Captcha-Auth Completion v11

日期：2026-08-14

状态：`DETERMINISTIC ENFORCED / PREFLIGHT AUTHORIZED / PROVIDER NOT YET RUN`

本 Gate 的唯一主目标仍是完成 captcha-auth 产品。checkpoint `4afd15e2` 修复的 contract clarification
owner/lineage 边界只在真实业务自然触发 `gate.contract_review=clarify` 时形成条件性证明；不得为了证明该边界
人为填写错误合同、制造 clarification 或增加样本。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@4` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`：Java 21、Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；
  手机号或邮箱加密码登录；登录前 image Captcha；注册时短信或邮件 OTP；测试、README、change-scope、
  lineage、completion audit 与 terminal acceptance 全部闭合。
- Source of truth: target `REQUIREMENT.md`；persisted workflow definition/instance；validated proposal、review、
  proposal reference 与 compiled `TaskContract`；gate/iteration/node documents；每个 `run.json`；self-test、
  change-scope、completion audit、terminal acceptance、PostgreSQL verification、`mvn test` 与最终 Git diff。
  Markdown、UI 文案和模型自述不是控制事实。
- Trigger precondition: Dual clean execution SHA 必须包含 `4afd15e22f6ef8a5ec63de705da1b55cb6542b7a`；
  target 必须保持 clean HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`。fresh package、workflow identity、
  隔离 PostgreSQL 与双 provider probes 必须 PASS。
- How precondition is checked before run: 创建 workstream 前记录 Dual/target Git identity 和 clean status、
  checkpoint ancestry、target tracked files、workflow source SHA/shape、CLI/server JAR SHA/mtime、PostgreSQL
  image/container/endpoint/health/query，以及 Claude/Codex minimal probe session、exit code 与 typed result。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed verification、`mvn test`、
  README、scope、lineage 与 completion audit 全部通过；所有 Agent node terminal outcome 与 required artifacts
  一致。若自然发生 contract clarification，还必须同时满足下节的 Conditional Boundary。
- FAIL: workflow 未完成；acceptance 拒绝或保留 must-fix；业务、测试、README、scope、lineage、completion
  任一失败；strict parser/schema/semantic/artifact validation 失败；human gate 被自动越过；clarify 直达
  compiler、human note 未成为 typed obligation、旧 proposal 未经新 review 即编译，或 ledger 与副作用矛盾。
- INVALID: Dual/target dirty，identity/hash/workflow/JAR/probe 不符，复用 v10/v3 workstream
  `ws-20260813T143229Z-BAS8` 或其 session，operator 运行中修改代码、prompt、workflow、provider 配置或 target，
  或证据不能证明本轮使用包含 `4afd15e2` 的 clean SHA。自然未触发 clarification 不使产品样本无效，只表示
  clarification 边界仍为 `ENFORCED / NOT PROVEN`。
- Stop-loss: freshness/identity/probe 不符时在 provider 前停止；strict contract、lineage、failed correction、
  timeout/interruption 或 terminal contradiction出现后停止，不 retry、不创建第二 workstream。`WAITING_FOR_HUMAN`
  是合法暂停点；wall-clock 达 90 分钟、typed revision budget 耗尽或同类技术失败重复两次时终止并执行既有
  terminate/reconcile/session-reap。
- Run artifact freshness: 从最终 clean Gate SHA fresh package，记录 CLI/server JAR SHA-256 与 mtime；不得使用
  旧 server/JAR 或 `--skip-build`。文档提交不改变 production bytecode，但 JAR ancestry 必须包含 `4afd15e2`。

## Conditional Contract Clarification Boundary

仅当真实 contract reviewer 自然选择 `clarify` 时判定：

1. resolved `gate.contract_review` 必须保存 `human_action=clarify` 与非空 note。
2. 下一 active node 必须是新 iteration 中的 `intake.claude_brainstorm`，不得是 `contract.compile`。
3. iteration 必须记录 trigger node、`trigger_action=clarify`、target proposal owner 与 human note reason。
4. revision context 必须从 verified requirement gate、contract resolution、Codex review、proposal reference 的
   完整 lineage 恢复唯一 baseline；human note 必须成为唯一 typed obligation。
5. Claude 只可返回 lineage-bound revision patch；patch 必须重新经过 strict validation、Codex product review、
   requirement gate 与 compiler，旧 compiled contract 不能被原样重编译冒充修订。
6. 任一 lineage 缺失或篡改必须在新 provider 副作用或 compiler 前 fail closed。

满足六项后，`4afd15e2` 可由 `ENFORCED` 升为 `PROVEN`。未自然触发时不降级产品 PASS，也不升级该边界。

## Frozen Workflow And Baseline

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@4
source sha256: 02c81fe9a106ba8c57873db033607ed036bcf6681cbf30ef12bee48b0091a7e0
nodes: 31
edges: 51
nominal acpx.agent_run nodes: 9
handoff nodes: 0

target HEAD: 7c7e35e89a66b14c29a335958ad90c8a92779fa0
target tree: bab065a1ff9fe4d7032d1d573b7d1b6c1181a574
requirement sha256: 42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0
```

## Human Interaction

`WAITING_FOR_HUMAN` 时必须读取当前 review packet，只能选择 packet 的 `allowedActions`。UI 与 fresh-JAR CLI
进入同一 ledger command boundary：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <allowed-action> --note "<明确决定>" \
  --workdir <workspace> <workstream-id>
```

## Execution Order

1. 提交本 Gate 与 active plan，获得 clean SHA。
2. fresh package；记录 workflow/JAR/Dual/target identity。
3. 清理旧 captcha-auth probe container/端口，运行隔离 PostgreSQL preflight。
4. non-provider preflight PASS 后运行短生命周期 Claude/Codex probes。
5. probes PASS 后创建且仅创建一个 fresh terminal-delivery@4 workstream，并执行到 terminal 或 stop-loss。
6. 保存 report、external preflight hashes、Agent call/token/duration/revision/correction 与条件边界结论。

## Current Maturity

- contract clarification checkpoint `4afd15e2`: full reactor PASS，`ENFORCED / NOT PROVEN`；
- ScopeOperation checkpoint `c753cfc`: v10 首个 proposal/reviewer real-case 已证明，`PROVEN`；
- human BLOCKED decision gate: v10 已由真实 E.164 决策证明，`PROVEN`；
- captcha-auth: `NOT ACHIEVED`；
- 本 Gate 不新增 runner、retry、parser fallback、module、registry、engine 或平台能力。
