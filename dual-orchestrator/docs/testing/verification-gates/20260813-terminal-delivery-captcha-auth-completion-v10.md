# Verification Gate: Terminal-Delivery Captcha-Auth Completion v10

日期：2026-08-13

状态：`DRAFT / INDEPENDENT REVIEW REQUIRED / PREFLIGHT NOT AUTHORIZED / REAL-CASE NOT AUTHORIZED`

本 Gate 的唯一主目标是完成 captcha-auth 产品。checkpoint `c753cfc` 的 ScopeOperation fact projection
是进入 reviewer 前必须满足的早期边界，不是独立 benchmark，也不授权为了证明该边界而增加 Agent 调用、
修改 target、复用 v9 workstream/session 或运行第二个样本。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@3` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。业务范围保持 `REQUIREMENT.md` 原文：Java 21、
  Spring Boot 3、Thymeleaf、PostgreSQL/Flyway；手机号或邮箱加密码登录；登录前 image Captcha；注册时
  完成短信或邮件 OTP；完成测试、README、change-scope、lineage、completion audit 与 terminal acceptance。
- Source of truth: target `REQUIREMENT.md`、effective worktree filesystem、validated
  `task-contract-proposal` sidecar、proposal reference、compiled `TaskContract`、persisted workflow
  definition/instance、每个 `run.json`、iteration/gate documents、validated self-test sidecar、change-scope
  evaluation、completion audit、terminal acceptance、PostgreSQL-backed verification、`mvn test` 与 Git final
  diff。Markdown、UI 文案、模型自述或单独 process exit code不能单独证明产品或边界成功。
- Trigger precondition: Dual 必须处于包含 checkpoint
  `c753cfceac3b28799d6524d3f261492b947b4802` 的 clean execution SHA。target 必须保持 clean HEAD
  `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且 tracked baseline 仅含
  `REQUIREMENT.md` 与历史 `VERIFICATION-GATE.md`。fresh package、workflow identity、隔离 PostgreSQL 与双
  provider probes 必须全部 PASS。
- How precondition is checked before run: 在创建 workstream 前记录 Dual/target Git identity 与 clean status、
  checkpoint ancestry、target tracked files、requirement hash、workflow source hash/shape、CLI/server JAR
  hash/mtime、PostgreSQL image/container/endpoint/health/query，以及 Claude/Codex minimal probe 的 session、
  exit code 和 typed result。任何字段缺失都不得靠 operator 解释补齐。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed tests、`mvn test`、README、
  change-scope、lineage 和 completion audit 全部通过；所有 Agent node terminal outcome 与 required artifact
  contracts 一致；initial proposal 的 durable scope operation 与 proposal 生成时 effective worktree 的存在性
  一致，且 reviewer 只消费该 validated sidecar/reference。
- FAIL: initial/revision Agent result 携带已删除的 `operation` 字段；scope resolution 或 durable proposal
  validation 失败；不存在路径未持久化为 `CREATE`、已存在文件未持久化为 `MODIFY`；reviewer 绕过 validated
  proposal reference；workflow 未完成；terminal acceptance 拒绝或留下 must-fix；业务功能、测试、README、
  scope、lineage、completion 任一失败；strict parser/schema/semantic/artifact validation 失败；human gate 被
  自动越过；或者 ledger 与目标副作用矛盾。
- INVALID: Dual/target dirty，identity/hash/workflow/JAR/probe 不符，复用 v9 或其他旧 session/workstream，
  operator 在运行中修改代码、prompt、parser、workflow、revision limit、provider 配置或 target baseline，
  或证据不能证明本轮使用包含 `c753cfc` 的 clean execution SHA。自然未触发 self-test repair、human BLOCKED、
  correction 或 permission warning不会使产品样本无效，只表示对应条件子边界未获得新证明。
- Stop-loss: freshness/identity/probe 不符时在 provider 前停止；initial proposal sidecar 未生成、operation 与
  effective worktree 不一致、reference/lineage 无效时，在 product reviewer 调用前停止；strict contract、
  failed correction、timeout/interruption 或 terminal contradiction 出现后停止，不 retry、不创建第二
  workstream。`WAITING_FOR_HUMAN` 是合法暂停点；wall-clock 达 90 分钟、typed revision budget 耗尽进入
  recovery，或同类技术失败重复两次时终止并执行既有 terminate/reconcile/session-reap。
- Run artifact freshness: Gate review/commit 后必须从最终 clean execution SHA 执行 fresh Maven package，记录
  CLI/server JAR SHA-256 与 mtime；runner 不得使用旧 server/JAR 或 `--skip-build` 规避 freshness。文档提交若不
  修改 production bytecode，仍必须在启动前证明 JAR 对应的 commit ancestry 包含 `c753cfc`。

## ScopeOperation Early Boundary

该边界只决定是否允许进入第一个 product reviewer，不替代产品 PASS。

1. 首个 intake Agent result 的 `scope.included[]` 只能包含 `path`；出现 `operation` 必须由 strict parser 拒绝。
2. `TaskContractScopeResolver` 根据 proposal 生成时的 effective worktree 派生 operation；不得从模型正文、
   repo-map 猜测、默认字段、correction 或 retry 获得。
3. validated proposal sidecar 中：baseline 已存在的 `REQUIREMENT.md` 如被纳入 scope，必须为 `MODIFY`；
   baseline 不存在的 `README.md`、`pom.xml` 或源码路径如被纳入 scope，必须为 `CREATE`。Gate 不要求模型必须
   选择这些具体路径，只对它实际声明的路径逐项验证。proposal 生成时 effective worktree 的存在性以启动前
   固化的 target tree `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574` 为事实来源；initial intake 前无业务
   实现步骤，且 Agent 为 deny-all，不能在该时点改变这份 baseline。
4. `TaskContractProposalValidator` 必须继续独立复核 durable operation；resolution shape 错误使用
   `TASK_CONTRACT_SCOPE_RESOLUTION_FAILED`，resolver 后的 operation/filesystem 矛盾使用
   `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`。两者均 fail closed，routing/recovery 不按 code 字符串分支。
5. proposal reference 的 hash、lineage、producer identity 必须有效；product reviewer 的 subject/input ref 必须
   指向同一 validated proposal，不能读取 Agent 原始 JSON 作为替代 authority。

满足以上五项可将 ScopeOperation fact projection 从 `ENFORCED / NOT PROVEN` 升级为 `PROVEN`；产品仍须满足
完整 PASS 条件。

## Frozen Workflow And Baseline

```text
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@3
source: dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json
source sha256: 0984ee517851c508e0042c1e40f4e592fc8fda4581a798b919b0671f718ec44d
nodes: 31
edges: 52
nominal acpx.agent_run nodes: 9
handoff nodes: 0

target HEAD: 7c7e35e89a66b14c29a335958ad90c8a92779fa0
target tree: bab065a1ff9fe4d7032d1d573b7d1b6c1181a574
requirement sha256: 42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0
```

历史 `VERIFICATION-GATE.md` 不是实现文件，不参与 workflow routing。v9 workstream
`ws-20260813T115256Z-8G74` 已消费，不得 resume 或作为 v10 PASS 样本。

## Human Interaction

当 workflow 为 `WAITING_FOR_HUMAN` 时，优先通过 UI 阅读 review packet，核对 `sourceOutcome`、evidence refs
与 allowed actions，填写明确非空 note 后选择允许的 action。CLI 必须显式使用本轮 fresh JAR：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <从当前 review packet 的 allowed_actions 中选择> --note "<明确决定>" \
  --workdir <workspace> <workstream-id>
```

UI/CLI 只是同一 command boundary 的入口；最终判定只读取 ledger 的 open/resolved gate、note、lineage 与后续
routing。不得使用此前指向旧 JAR、曾因 frozen workflow schema 不兼容而失败的裸 `dualctl approve` 命令。

## Preflight And Execution Order

1. 独立 review 本 Gate；修正 blocking findings后提交 Gate + active plan，获得 clean execution SHA。
2. 从该 SHA fresh package，核对 JAR hash/mtime、workflow source hash/shape、Dual/target identity 与 clean status。
3. 清理旧 captcha-auth probe container/端口，启动隔离 `postgres:16-alpine`，保存 `pg_isready` 与 `SELECT 1`。
4. 非 provider preflight PASS 后，运行且仅运行短生命周期 Codex 与 Claude minimal probes。
5. 双 probes PASS 后创建且仅创建一个 fresh terminal-delivery@3 workstream，不 resume v9，不复用 Agent session。
6. 首个 intake sidecar/reference 通过 ScopeOperation Early Boundary 后才允许 product reviewer 继续。
7. 按 Stop-loss 与 Human Interaction 执行到 terminal；保存 report、external preflight hashes、调用/token/
   duration/revision/correction 统计。

## Current Maturity

- ScopeOperation checkpoint `c753cfc`：独立 design/diff review PASS，focused `55/0/0/0`、ACP adapter
  `41/0/0/0`、full reactor `1223/0/0/0`，当前 `ENFORCED / NOT PROVEN`；
- self-test repair checkpoint `b2306882`：`ENFORCED / NOT PROVEN`；
- captcha-auth：`NOT ACHIEVED`；
- 本 Gate 不新增 runner monitor、parser fallback、retry、inventory、module、registry、engine 或平台能力。
