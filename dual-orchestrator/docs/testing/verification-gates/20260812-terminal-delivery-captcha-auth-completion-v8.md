# Verification Gate: Terminal-Delivery Captcha-Auth Completion v8

日期：2026-08-12

状态：`INDEPENDENT REVIEW PASS / NON-PROVIDER PREFLIGHT AUTHORIZED / PROVIDER NOT AUTHORIZED / NOT YET RUN`

本 Gate 的唯一主目标是完成 captcha-auth 产品。checkpoint `023924b6` 修复的 assistant presentation
与 contained permission denial 只在运行中自然触发时形成条件性技术结论；不得人为诱发工具拒绝、
Markdown fence、`BLOCKED` 或 revision，也不得为了证明修复而复用旧 workstream/session。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@2` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。业务范围保持 `REQUIREMENT.md` 原文：Java 21、
  Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；手机号或邮箱加密码登录；登录前 image Captcha；
  注册时完成短信或邮件 OTP；完成测试、README、change-scope、lineage、completion audit 与 terminal
  acceptance。若 review 自然产生 `BLOCKED`，使用既有 human gate 在同一 workstream 中作出决定并继续。
- Source of truth: target `REQUIREMENT.md`、Git baseline/final diff、persisted workflow definition/instance、
  typed review/gate/contract artifacts、每个 `run.json` 的 `processResult`、`agentResultAcquisition`、
  `artifactContractResults`、`executionOutcome`、change-scope evaluation、completion audit、terminal
  acceptance、PostgreSQL-backed verification、`mvn test` 与 README。Markdown 正文、UI 文案、模型自述、
  单独的 process exit code 或 permission signal 不能单独证明成功或失败。
- Trigger precondition: Dual execution ancestry 必须包含 `023924b6` 和本 Gate 的 review/commit；Dual 与
  target 均 clean。target 必须保持 HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且无实现文件。fresh package、
  frozen workflow identity、隔离 PostgreSQL、Claude/Codex minimal probes 全部 PASS。
- How precondition is checked before run: 在创建 workstream 前记录 Dual/target Git identity、clean status、
  checkpoint ancestry、requirement hash、workflow source hash/shape、build 起止、CLI JAR path/hash/mtime、
  PostgreSQL image/container/endpoint/health/query，以及两个 provider probe 的 session、exit code 和结果。
  任一证据缺失或不匹配时不得创建 workstream。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed tests、`mvn test`、
  README、change-scope、lineage 和 completion audit 全部通过，handoff 不存在；所有 Agent node 的
  terminal outcome 与 artifact contracts 一致，没有未经解释的 recovery/terminal contradiction。
- FAIL: workflow 未完成、terminal acceptance 拒绝或留下 must-fix、业务功能/测试/README/scope/lineage/
  completion 任一失败；strict parser/schema/semantic/artifact validation 失败；permission denial 仍是
  primary outcome、process output 不完整、failed correction、timeout/interruption、human gate 被绕过，
  或 ledger 与目标副作用矛盾。
- INVALID: Dual/target dirty，baseline/hash/workflow/JAR/probe 不符，复用旧 session/workstream，启动前
  target 已有实现，baseline 文件不是精确的 `REQUIREMENT.md` 与 `VERIFICATION-GATE.md`，operator 在运行中修改代码、prompt、parser、workflow、revision limit 或 provider
  配置，或者运行证据无法证明使用了本轮 clean SHA。没有自然触发 presentation extraction、contained
  permission denial 或 human `BLOCKED` 不影响产品 PASS/FAIL，但相应技术边界保持 `ENFORCED / NOT PROVEN`。
- Stop-loss: freshness/identity/probe 不符时在 provider 前停止；strict contract、scope/lineage、primary
  permission failure、failed correction、timeout/interruption 或 terminal contradiction 出现后停止，不
  retry、不创建第二 workstream。`WAITING_FOR_HUMAN` 是合法暂停点；semantic revision 只由当前
  decision node 的 typed `max_revisions=3` 管理，不设置更小的 operator revision limit。wall-clock 达
  90 分钟、typed revision budget 耗尽进入 recovery，或同类技术失败重复两次时终止并执行既有
  terminate/reconcile/session-reap。
- Run artifact freshness: Gate review PASS 后提交 Gate 与 active plan，得到 clean execution SHA；从该
  SHA fresh package，记录 JAR/workflow hashes 和 mtime。persisted definition 创建后必须与 frozen
  identity 精确一致。

## Conditional Boundary Verdicts

这些判定不改变产品 Gate 的主结论，也不是必须触发的目标。

### A. Assistant presentation

若自然出现“可选 prose + 单个完整 `json` fence + 可选 prose”：

- PASS/PROVEN 条件：`presentationDisposition=SINGLE_FENCED_JSON_EXTRACTED`；candidate hash 与 fence 内
  payload 一致；同一 candidate 通过 strict parser、schema、semantic 与 required artifact validation；
  不发生 correction；节点 terminal outcome 可成功。
- FAIL 条件：提取多个/未闭合/嵌套/空/non-json fence，绕过 strict validation，自动补 JSON，或 raw
  Markdown 直接参与 routing。
- 未自然触发：checkpoint `023924b6` 保持 `ENFORCED / NOT PROVEN`，不得人为构造样本。

### B. Contained permission denial

若自然出现 `PERMISSION_BLOCKED` 与 non-zero complete process：

- PASS/PROVEN 条件：`ProcessUsability=EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT`；AgentResult present+valid；
  所有 required artifact contracts satisfied；terminal outcome=`SUCCESS_WITH_WARNINGS`；
  `PERMISSION_BLOCKED` 原样保留在 `secondarySignals`；节点可继续且无 retry/correction。
- FAIL 条件：上述任一条件不满足却允许节点成功，或 signal 被删除；反之，全部条件满足却仍被旧路径
  覆盖为 `PERMISSION_BLOCKED/FAILED` 也为 FAIL。
- 未自然触发：checkpoint `023924b6` 保持 `ENFORCED / NOT PROVEN`。

### C. Human decision

若 review 自然产生 `BLOCKED`：decision execution 必须为 `COMPLETED/BLOCKED`，workflow 必须进入
`WAITING_FOR_HUMAN`；GateDocument 必须保存 `source_outcome=BLOCKED` 和当前 reviewer ref。人类通过
UI 或 CLI 提交非空 note 后，resolved gate、contract-resolution 与 compile 必须使用同一 lineage。
未自然触发时 checkpoint `99f827d5` 保持 `ENFORCED / NOT PROVEN`。

## Human Interaction Procedure

1. operator 使用既有 CLI 创建唯一 fresh workstream，并以 `autorun` 驱动到稳定边界。
2. 若状态为 `WAITING_FOR_HUMAN`，不得将其归类为 runtime failure，也不得运行 recovery。
3. UI 路径：打开 management UI 的 workstream gate/review packet，核对 `sourceOutcome`、review subject、
   `must_fix/missing_evidence`，填写明确的非空 decision note，再选择 approve 或 revise。
4. CLI 等价路径：
   `dualctl review decide --action approve --note <decision> --workdir <workspace> <workstream>`；若产品决定
   要求重做 proposal，使用 `--action revise` 并提供同样明确的 note。
5. 决定后只 resume/autorun 同一 workstream，直至下一个 human gate、typed failure 或 terminal completion。
6. UI/CLI 只是 command 入口；最终判定只读取 ledger 中 open/resolved gate、note、lineage 和后续 contract。

## Frozen Workflow

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@2
source: dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json
source sha256: 3b13d6c64a42870cc0a4c9c921a8315a6cc879b83527de20f8ed16a79906717e
nodes: 29
edges: 48
nominal acpx.agent_run nodes: 9
handoff nodes: 0
terminal acceptance approve: gate.human_acceptance.allowed_actions permits approve; no outgoing approve edge is
defined, so RoutingEngine completes the gate and the workflow terminates implicitly after approval
```

该 acceptance 终止语义是当前 frozen workflow 的既有行为，不由本 Gate 新增 edge。它必须由 persisted
gate action、terminal acceptance artifact 与最终 workflow=`COMPLETED` 共同证明，不能仅凭 CLI exit code 推断。

Target baseline 允许且只允许两个 tracked 文件：`REQUIREMENT.md` 是业务需求 SoT；
`VERIFICATION-GATE.md` 是历史验证参考文档，不是 Java/Spring/Thymeleaf/PostgreSQL 实现文件，也不参与
workflow routing。preflight 必须精确核对该清单，出现任何其他文件时本轮 INVALID。

## Preflight And Execution Order

1. 独立 review 本 Gate；修正 blocking findings 后提交 Gate + active plan，获得 clean execution SHA。
2. fresh Maven package；记录完整结果和 CLI JAR freshness。
3. 在 Git ignored 的 `dual-orchestrator/target/verification-preflight/captcha-auth-v8/` 写入非 provider 证据：
   - Dual/target/workflow/JAR identity；
   - 清理旧 captcha-auth probe containers 和端口占用的结果；
   - 隔离 `postgres:16-alpine` container `dual-captcha-auth-v8-postgres`，endpoint
     `127.0.0.1:55432`，`pg_isready` 与 `SELECT 1` 结果。
4. 非 provider preflight PASS 后报告结果并等待明确授权。获授权后执行且仅执行：新短生命周期 Codex
   minimal probe 与 runner-equivalent Claude minimal probe；保存各自 session、exit code 和完整结果。
5. 双 probes PASS 后，创建且仅创建一个 fresh workstream。不得 resume v7 或复用其 Agent sessions。
6. 运行时按 Stop-loss 和 Human Interaction Procedure 操作；结束后保存完整 report 与 external preflight
   hashes。

## Call Accounting And Maturity

- nominal Agent nodes 为 9；semantic revision 和 bounded correction 均作为额外 execution 计入。
- 不设金额门槛，但必须记录调用数、input/output/cache token、duration、revision 与 correction。
- `023924b6` 当前为 `ENFORCED / NOT PROVEN`；本 Gate 只有在自然触发对应条件时才升级子结论。
- captcha-auth 只有完整业务、验证和 terminal acceptance 同时通过才可标记 `ACHIEVED`。
- 不新增 runner monitor、parser fallback、permission registry、capability engine、workflow 或平台层。

## Result

`INDEPENDENT REVIEW PASS / NON-PROVIDER PREFLIGHT AUTHORIZED`。允许提交本文、active plan 与 review，
然后执行 fresh package、Git/workflow/JAR/target/PostgreSQL preflight。Claude/Codex probes、其他 provider
调用与 real-case 仍未授权。
