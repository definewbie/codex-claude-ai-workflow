# Verification Gate: Terminal-Delivery Captcha-Auth Completion v13

日期：2026-08-14

状态：`INDEPENDENT REVIEW PASS / AUTHORIZED FOR NON-PROVIDER PREFLIGHT / PROVIDER NOT AUTHORIZED`

本 Gate 的唯一主目标仍是完成 captcha-auth 产品。它不为证明 adapter 修复而人为制造 permission denial、timeout、
session failure 或第二个样本。checkpoint `1db62088` 只收敛 current-turn permission evidence 与 failed-turn named
session closure；checkpoint `b6bc8e79` 负责 interrupted execution 的 durable terminal closure。两者不修改模型、
prompt、strict parser、业务 contract、workflow、revision budget 或 human gate。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@4` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`：Java 21、Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；
  手机号或邮箱加密码登录；登录前 image Captcha；注册时短信或邮件 OTP；测试、README、change-scope、lineage、
  completion audit 与 terminal acceptance 全部闭合。
- Source of truth: target `REQUIREMENT.md`；persisted workflow definition/instance；validated proposal、review、
  proposal reference 与 compiled `TaskContract`；gate/iteration/node documents；每个 `run.json` 中的
  `processResult`、`executionTimeline`、`adapterSignals`、`agentResultAcquisition`、`executionOutcome`；self-test、
  change-scope、completion audit、terminal acceptance、PostgreSQL verification、`mvn test` 与最终 Git diff。
  Markdown、UI 文案、模型自述、裸 exit code 和累计 session permission statistics 均不是控制事实。
- Trigger precondition: clean Dual SHA 必须包含 `e64bf7c6`、`b6bc8e79` 与 `1db62088`；target 必须保持 clean
  HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且仅有
  `REQUIREMENT.md`、`VERIFICATION-GATE.md` 两个 tracked files。workstream 创建前，fresh package、workflow
  identity 与隔离 PostgreSQL 必须 PASS；provider probes 属于文档提交和 non-provider preflight PASS 后的独立
  授权条件。provider/model identity 只记录为执行证据，不作为 Dual workflow 合法性的前置判断，也不因用户
  切换 Claude Code 后端模型而把样本判为 INVALID。
- How precondition is checked before run: provider 前记录 Dual/target Git identity、checkpoint ancestry、target
  tracked files、workflow source SHA/shape、CLI/server JAR SHA-256 与 mtime；核对 clean ancestry 中已提交的
  independent review 与 full reactor `1239/0/0/0` 证据，不因纯文档 Gate 变更重复运行单元测试；清理旧
  captcha-auth probe container 与 `127.0.0.1:55432` 占用，并关闭 v12 遗留 open record
  `codex-reviewer-ws-20260813T235458Z-4RQE`（记录清理前后状态）；随后记录 PostgreSQL
  image/container/endpoint/health/query。Gate review PASS 且用户授权后，才运行一次 Claude/Codex minimal
  probe；两者 PASS 后才创建 workstream。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed verification、`mvn test`、
  README、scope、lineage 与 completion audit 全部通过；所有 Agent node terminal outcome 与 required artifacts
  一致；无 raw exit 5、stderr 文本或历史 permission event 覆盖 current occurrence 的 typed outcome。
- FAIL: workflow 未完成；acceptance 拒绝或保留 must-fix；业务、测试、README、scope、lineage、completion
  任一失败；strict parser/schema/semantic/artifact validation 失败；human gate 被自动越过；typed execution facts
  与 ledger/副作用矛盾；当前 permission denial、abnormal turn 或 closure failure 不满足下节条件边界；或任何
  session closure failure 后仍 scheduler retry/resume。
- INVALID: Dual/target dirty，identity/hash/workflow/JAR/probe 不符；复用 v12 workstream
  `ws-20260813T235458Z-4RQE`、其 worktree 或其 named session；operator 在运行中修改 Dual、prompt、workflow、
  provider 配置或 target；运行工件不能证明包含本 Gate clean SHA；或 preflight 在 workstream 创建前没有形成
  可核对证据。自然未触发 permission/abnormal closure 不使产品样本无效，只表示对应 real-case 条件边界仍为
  `ENFORCED / NOT PROVEN`。
- Stop-loss: freshness/identity/probe 不符时在 provider/workstream 前停止；strict contract、lineage、failed
  correction、terminal contradiction 或 session closure=`FAILED` 时停止，不 retry、不创建第二 workstream。
  `WAITING_FOR_HUMAN` 是合法暂停点且不计入 90 分钟预算；workflow 实际活跃执行时间累计达到 90 分钟、typed
  revision budget 耗尽或同类技术失败重复两次时由 operator 中断，并走既有
  terminate/reconcile/session-reap。该 operator stop-loss 不冒充本 checkpoint 的 transport-owned named-session
  closure proof。
- Run artifact freshness: 从最终 clean Gate SHA fresh package，记录 CLI/server JAR SHA-256 与 mtime；不得使用
  旧 server/JAR 或 `--skip-build`。runner 必须创建新的 worktree、workstream id 与 workstream-scoped named
  sessions；persisted workflow identity 在创建后立即与 frozen definition 核对，不符则 INVALID 并 quiesce。

## Conditional ACPX Turn Boundary

只在真实运行自然出现对应事实时升级成熟度，不人为诱发：

1. **Current permission denial**：只有同一 current occurrence 的 typed request-id + result correlation 可以形成
   `PERMISSION_BLOCKED`；evidence 必须含 session、tool/action/resource、requested options 与 disposition。
2. **No current permission evidence**：裸 exit 5、stderr permission 文本或历史 denial 均不得生成
   current-run `PERMISSION_BLOCKED`；required completion 缺失时只能由既有 output/process failure fail closed。
3. **Contained denial**：若 current denial 后 strict result 与 required artifacts 仍完整，node 可成功；denial 只保留
   secondary telemetry，不覆盖 authoritative success。
4. **Abnormal turn closure**：timeout、non-zero 且无可信 completion、interrupt 或 persistence failure 返回 runtime
   前，timeline 必须出现 named-session closure=`CANCELLED` 或 `CLOSED_AFTER_CANCEL`；failure signal 必须保留准确
   session id。
5. **Closure failure**：cancel 与 close 均失败时必须形成 typed `PROCESS_STALLED`、`MANUAL_ONLY`、
   `retrySafe=false`；不得自动 retry/resume，也不得声称 session 已 quiescent。
6. mid-run session-id rollover 若自然发生，只记录为当前已知 evidence-continuity 限制；它不自动否定产品 PASS，
   也不得被用来升级 turn-boundary `PROVEN`。

若本轮正常完成且未触发 1、3、4、5，对应 checkpoint 保持 deterministic `ENFORCED / NOT PROVEN`；不得为升级
标签再运行第二个 captcha-auth 样本。

## Frozen Baseline

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

`WAITING_FOR_HUMAN` 是正常流程暂停，不是失败。必须读取当前 review packet，并且只选择 packet 的
`allowedActions`。

UI 路径：

1. operator 手工启动 fresh Dual server，并确认浏览器连接的是该 fresh JAR；
2. 在当前 workstream 的 Attention/Review 区域打开唯一 open gate；
3. 阅读 source outcome、must-fix、missing evidence 与 referenced artifacts；
4. 选择 `allowedActions` 中的动作，填写非空、可审计 note 后提交；
5. 重新读取 ledger/UI，确认 gate resolved 且只激活预期下一节点。

CLI 路径使用 fresh JAR，避免 shell 中旧 `dualctl` 的 schema 漂移：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <packet-allowed-action> --note "<明确决定>" \
  --workdir <workspace> <workstream-id>
```

UI 与 CLI 必须进入同一个 `resolveOpenGate` ledger boundary；不得手工编辑 gate/node/workflow JSON。

## Execution Order

1. 独立 review 本 Gate、v12 历史结果同步与 active plan；PASS 后只提交三份被审查文档及一份 review evidence，
   获得 clean Gate SHA。
2. fresh package；记录 Dual/target/workflow/JAR identity，复核 deterministic baseline。
3. 清理旧 probe 容器/端口和 v12 遗留 open session record，运行隔离 PostgreSQL non-provider preflight。
4. 用户授权后运行一次 Claude/Codex minimal probes。
5. probes PASS 后创建且仅创建一个 fresh `terminal-delivery@4` workstream，执行到 terminal 或 stop-loss；
   `WAITING_FOR_HUMAN` 时按上一节由 operator 决策后继续。
6. 保存 report、preflight hashes、Agent call/token/duration/revision/correction、turn-boundary 条件结论与产品结果。

## Current Maturity

- canonical scope prompt projection `e64bf7c6`: `ENFORCED / NOT PROVEN`；
- interrupted outcome closure `b6bc8e79`: `ENFORCED / NOT PROVEN`；
- ACPX turn-scoped permission + named-session closure `1db62088`: `ENFORCED / NOT PROVEN`；
- ScopeOperation `c753cfce`: `PROVEN`；
- human BLOCKED decision gate: `PROVEN`；
- captcha-auth: `NOT ACHIEVED`。

本 Gate 不新增 runner、retry、parser fallback、module、registry、engine、permission platform 或 session platform。
