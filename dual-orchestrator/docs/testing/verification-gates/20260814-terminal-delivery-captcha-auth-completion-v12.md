# Verification Gate: Terminal-Delivery Captcha-Auth Completion v12

日期：2026-08-14

状态：`FAIL / CONSUMED / SUPERSEDED BY v13`

## Result

- 唯一 fresh workstream：`ws-20260813T235458Z-4RQE`；workflow 冻结为
  `agent-lifecycle-terminal-delivery@4`，最终停在 `WAITING_FOR_RECOVERY` / `implement.claude_implement`。
- scope、contract、design、plan 与 human gates 已通过，目标 worktree 已生成 Java/Spring/PostgreSQL implementation
  files；
  产品未进入 self-test、acceptance 与 delivery，不能声明完成。
- implementation run `run-1786671207221` 使用 `acpx --timeout 600`，持续 `605118ms` 后 exit 5；ACP stream
  已接受 prompt 并持续产生 typed updates，不是已证明的 ACP 协议兼容失败。
- 当前 implementation occurrence 没有 permission request；旧 design turn 的拒绝经 acpx 累计
  `permissionStats` 把当前 CLI exit 投影为 5，旧 Dual 又从裸 exit 5 合成 `PERMISSION_BLOCKED`。该结论是历史
  permission evidence 污染，不是当前 implementation 等待人工授权。
- caller timeout 没有自动取消 persistent prompt。checkpoint `1db62088` 已删除裸 exit 5/累计统计的权限
  authority，并要求 abnormal return 前 cancel/close exact named session；checkpoint `b6bc8e79` 已收敛 interrupted
  execution 的 durable terminal facts。deterministic reactor `1239/0/0/0` 与独立 diff/delta review PASS。
- 本 workstream、worktree 与 named session 均已消费，不得 retry/resume。captcha-auth 仍为 `NOT ACHIEVED`。

本 Gate 的唯一主目标是完成 captcha-auth 产品。checkpoint `e64bf7c` 只修复 Agent-facing scope path
契约投影缺失，不放宽 canonical path validator，不自动展开目录，也不增加 correction、retry、workflow、
module 或平台能力。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@4` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`：Java 21、Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；
  手机号或邮箱加密码登录；登录前 image Captcha；注册时短信或邮件 OTP；测试、README、change-scope、
  lineage、completion audit 与 terminal acceptance 全部闭合。
- Source of truth: target `REQUIREMENT.md`；persisted workflow definition/instance；validated proposal、review、
  proposal reference 与 compiled `TaskContract`；gate/iteration/node documents；每个 `run.json`；self-test、
  change-scope、completion audit、terminal acceptance、PostgreSQL verification、`mvn test` 与最终 Git diff。
  Markdown、UI 文案和模型自述不是控制事实。
- Trigger precondition: clean Dual SHA 必须包含 `e64bf7c`；target 必须保持 clean HEAD
  `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`。fresh package、workflow identity、
  isolated PostgreSQL 与双 provider probes 必须 PASS。fresh rendered intake prompt 必须逐字包含
  `Each scope path must name one exact file, not a directory`、`must not end with /` 的等价 backtick 形式及
  canonical-path exclusions。
- How precondition is checked before run: 在 provider 前记录 Dual/target Git identity、checkpoint ancestry、
  target tracked files、workflow source SHA/shape、CLI/server JAR SHA/mtime；执行 focused
  `AgentResultPromptContractTest`，让已编译的 production renderer 生成 initial/revision contract 并断言上述
  canonical-file-path 文案；随后记录 PostgreSQL image/container/endpoint/
  health/query。仅在这些检查通过后运行一次 Claude/Codex minimal probe。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed verification、`mvn test`、
  README、scope、lineage 与 completion audit 全部通过；所有 Agent node terminal outcome 与 required artifacts
  一致。若首个或 revision proposal 声明 scope，每一项必须是 canonical exact file path，并由 resolver 派生
  durable CREATE/MODIFY。
- FAIL: workflow 未完成；acceptance 拒绝或保留 must-fix；业务、测试、README、scope、lineage、completion
  任一失败；strict parser/schema/semantic/artifact validation 失败；human gate 被自动越过；或 ledger 与副作用
  矛盾。
- INVALID: Dual/target dirty，identity/hash/workflow/JAR/probe 不符，复用 v11 workstream
  `ws-20260813T232431Z-5XZZ` 或其 session，fresh prompt 未包含 canonical-file-path 规则，operator 运行中修改
  代码、prompt、workflow、provider 配置或 target，或证据不能证明本轮使用包含 `e64bf7c` 的 clean SHA。
- Stop-loss: freshness/identity/prompt/probe 不符时在 provider 或 workstream 前停止；strict contract、lineage、
  failed correction、timeout/interruption 或 terminal contradiction出现后停止，不 retry、不创建第二 workstream。
  `WAITING_FOR_HUMAN` 是合法暂停点；wall-clock 达 90 分钟、typed revision budget 耗尽或同类技术失败重复两次
  时终止并执行既有 terminate/reconcile/session-reap。
- Run artifact freshness: 从最终 clean Gate SHA fresh package，记录 CLI/server JAR SHA-256 与 mtime；不得使用
  旧 server/JAR 或 `--skip-build`。prompt preflight 必须执行已编译的 production renderer，不能只搜索 source
  或复制测试字符串。

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

`WAITING_FOR_HUMAN` 时读取当前 review packet，只能选择 packet 的 `allowedActions`。UI 与 fresh-JAR CLI
进入同一 ledger boundary：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <allowed-action> --note "<明确决定>" \
  --workdir <workspace> <workstream-id>
```

## Execution Order

1. 提交本 Gate 与 active plan，获得 clean SHA。
2. fresh package；记录 workflow/JAR/Dual/target identity，并通过 focused test 执行 production renderer 验证
   prompt projection。
3. 清理旧 captcha-auth probe container/端口，运行隔离 PostgreSQL preflight。
4. 独立 Gate review PASS 且用户授权后，运行短生命周期 Claude/Codex probes。
5. probes PASS 后创建且仅创建一个 fresh terminal-delivery@4 workstream，执行到 terminal 或 stop-loss。
6. 保存 report、preflight hashes、Agent call/token/duration/revision/correction 与 scope projection 结论。

## Current Maturity

- canonical scope prompt projection `e64bf7c`: `ENFORCED / NOT PROVEN`；
- contract clarification `4afd15e2`: `ENFORCED / NOT PROVEN`；
- ScopeOperation `c753cfc`: `PROVEN`；
- human BLOCKED decision gate: `PROVEN`；
- captcha-auth: `NOT ACHIEVED`。
