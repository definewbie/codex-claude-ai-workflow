# Verification Gate: Terminal-Delivery Captcha-Auth Completion v15

日期：2026-08-15  
状态：`INDEPENDENT REVIEW PASS / PROVIDER PREFLIGHT PASS / FRESH WORKSTREAM AUTHORIZED`

## Verification Gate

- Target: 使用一个且仅一个 fresh `agent-lifecycle-terminal-delivery@5` workstream 完成
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。产品必须满足 Java 21、Spring Boot 3、Maven、Thymeleaf、
  PostgreSQL/Flyway、手机号或邮箱加密码登录、登录前 image Captcha、注册短信或邮件 OTP、测试与 README。条件子结论：
  若自然发生 intake revision，checkpoint `0c77ce0c61a4699a608e4322781da4d604df9d5b` 必须在 Agent boundary 与 durable
  merge boundary 守住 patch-wide target uniqueness；若自然进入 architecture review，任意合法 advisory verdict 必须先进入
  human design gate，由 operator 决定 approve/revise。
- Source of truth: target `REQUIREMENT.md`；persisted workflow definition/instance；typed proposal/revision/review sidecars 与
  references；human gate/action ledger；每个 `run.json`；compiled contract、self-test、change-scope、completion audit、terminal
  acceptance；PostgreSQL verification、`mvn test` 与最终 Git diff。Markdown、模型自述和控制台文本不是控制事实。
- Trigger precondition: Dual 必须是包含 `0c77ce0c61a4699a608e4322781da4d604df9d5b` 的 clean Gate SHA；target 必须保持
  clean HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且只有 `REQUIREMENT.md`、
  `VERIFICATION-GATE.md` 两个 tracked files。v14 workstream/worktree/sessions 不得复用。fresh package、隔离 PostgreSQL 与
  non-provider preflight 必须先 PASS；provider probes 和 fresh workstream 需要本 Gate 独立 review PASS 后另行授权。
- How precondition is checked before run: 记录 Dual/target Git identity、checkpoint ancestry、target files/hashes、workflow
  source SHA/shape、JAR SHA/mtime。删除 v14 worktree 前，必须把整个
  `.agent/workstreams/ws-20260815T033043Z-Z95C/` 复制到独立 archive
  `/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/archive/captcha-auth-v14-ws-20260815T033043Z-Z95C/`，生成逐文件
  SHA-256 manifest，复算 v14 result 引用的 prompt/output/review/diagnostic SHA，全部一致后把 archive 设为只读。核对 scoped
  sessions 的 `open/closed` 状态；历史 session record 存在不等于仍 open。之后才删除 v14 Git worktree，不删除 branch、archive 或历史
  result。fresh package 后创建 Git ignored 的临时 preflight evidence，清理旧 PostgreSQL 资源并验证 health/`SELECT 1`；该 evidence
  必须在任何后续 Maven clean 前复制到本轮 durable case report 并复算 SHA。独立运行 Claude/Codex minimal probes；全部 PASS 后才创建
  fresh workstream，并立即核对 persisted workflow identity。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；PostgreSQL-backed verification、`mvn test`、README、scope、lineage、
  completion audit 与 final diff 全部通过。若自然发生 revision patch：合法唯一 target patch 必须进入 reviewer；重复 target 必须在
  reviewer 调用前以精确 `DUPLICATE_*_PATCH_TARGET`、`NOT_REPAIRABLE` 失败，且不发布 proposal reference、不增加 correction/Agent
  调用。若自然到达 design review：必须停在唯一 human design gate，operator action 前无 successor/iteration/Agent run；approve
  只进入 plan，带 note revise 只创建一个 design iteration。
- FAIL: 产品未完成；acceptance blocking/must-fix；业务、数据库、测试、scope、lineage、completion 任一失败；revision target
  uniqueness 被绕过、合法唯一 patch 被误拒、重复 target 发布 durable reference、错误进入 correction；architecture verdict 自动路由、
  human gate 被 autorun 解决；或 typed ledger facts 与外部副作用矛盾。
- INVALID: identity/hash/JAR/workflow/probe 不符；复用 v14 worktree/workstream/session；人为诱导 reviewer/revision target；运行中修改
  Dual、target、workflow、prompt 或 provider 配置；旧 server/JAR；缺少 preflight evidence；手工编辑 ledger；或 observation fields
  缺失而无法判定。条件子目标未自然触发时记 `NOT_APPLICABLE`，不得伪造样本，但产品主结论仍可 PASS/FAIL。
- Stop-loss: 任何 freshness/identity/probe 错误在 provider 前停止。同一 technical failure 第二次出现、strict/lineage failure、failed
  correction、session closure failure、terminal contradiction 或 typed revision budget 耗尽时终止并 quiesce；不 retry/resume、不创建
  第二样本。`WAITING_FOR_HUMAN` 是合法暂停，不计活跃运行时长；每次 gate 必须由用户本人决定。
- Run artifact freshness: 从本 Gate 独立 review 后的 clean Gate SHA 执行 fresh package，记录 CLI/server JAR SHA-256/mtime；不得
  `--skip-build`。workstream 创建后保存 persisted definition hash、version、node/edge counts、worktree path、branch 和 scoped session
  names。

## Frozen Baseline

```text
implementation checkpoint: 0c77ce0c61a4699a608e4322781da4d604df9d5b
implementation maturity: ENFORCED / NOT PROVEN
workflow: agent-lifecycle-terminal-delivery@5
workflow source sha256: bcf1763a75cc03a118c636590441d5e7f447dd71d157e388934de1f6ce54fcdb
nodes / edges / nominal agent nodes / handoff: 31 / 51 / 9 / 0

target HEAD: 7c7e35e89a66b14c29a335958ad90c8a92779fa0
target tree: bab065a1ff9fe4d7032d1d573b7d1b6c1181a574
requirement sha256: 42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0
target tracked files: REQUIREMENT.md, VERIFICATION-GATE.md
```

## Preflight Procedure

1. 独立 review 本 Gate 与 active plan；PASS 后只提交两份文档，获得 clean Gate SHA。
2. 确认 v14 已 `FAIL / CONSUMED`。将其完整 workstream ledger 从
   `/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/captcha-auth-v14-20260815T1124Z/.agent/workstreams/ws-20260815T033043Z-Z95C/`
   复制到
   `/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/archive/captcha-auth-v14-ws-20260815T033043Z-Z95C/`；生成按相对路径排序的
   SHA-256 manifest，并逐项对账 v14 result 中记录的四个工件 SHA。source workstream 与 archive 必须分别生成同规格、按相对路径
   排序的 manifest；两份 manifest 逐行一致后才可进入只读/删除步骤。manifest 或任一 SHA 不符时 preflight `FAIL`，不得删除 source
   worktree。对账 PASS 后将 archive 设为只读；核对 Claude/Codex scoped session 的 open 状态均为 closed（允许 closed historical
   record 继续存在）；保存 `git worktree list`，然后删除 v14 worktree。不得删除历史 branch、archive 或 result 文档。
3. 在 Dual 根目录执行 fresh Maven package；保存 reactor 结果、JAR SHA-256 与 mtime。
4. 创建 ignored 的临时 evidence 目录 `dual-orchestrator/target/verification-preflight/captcha-auth-v15/`。该目录不是 durable SoT；在任何
   后续 Maven clean 前，必须复制到本轮 `docs/testing/real-case-reports/<case-id>/external-preflight/` 并验证复制前后逐文件 SHA 一致。
5. 清理同名 `dual-captcha-auth-v15-postgres` 与旧 v14 probe container；确认 `127.0.0.1:55432` 未占用。
6. 启动 `postgres:16-alpine`：container `dual-captcha-auth-v15-postgres`、endpoint `127.0.0.1:55432`、database
   `captcha_auth_test`、user `captcha_auth`；保存 image/container identity、`pg_isready` 与精确 `SELECT 1` 结果。
7. 用户授权后分别执行一个最小 Claude probe 和一个最小 Codex probe；保存 command、session、exit、stop reason、transcript SHA、usage 与
   closure。probe 只证明 provider 可用，不证明模型遵守业务 contract。
8. 两个 probe PASS 后创建一个 fresh worktree/workstream；persisted identity 不符立即 `INVALID` 并 quiesce。

## Human Interaction

每次 workflow 到达 `WAITING_FOR_HUMAN`：

1. 先报告 gate purpose、source outcome、allowed actions、review evidence、must-fix/missing-evidence、revision budget 与每个动作后果。
2. UI：在当前 workstream 的 Attention/Review 打开唯一 open gate，核对证据后选择 packet 允许的 action；revise/clarify 必须填写具体 note。
3. CLI 必须使用本轮 fresh JAR，不使用 PATH 中旧 `dualctl`：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <packet-allowed-action> --note "<human decision>" \
  --workdir <fresh-worktree> <workstream-id>
```

4. CLI 不使用 `--run`。提交 action 后先重新读取 ledger，确认只激活预期 successor/iteration，再显式继续同一 workstream。
5. operator 不替用户选择；autorun 不得解决 design/human-repair gate。

## Maturity Boundary

- revision target uniqueness `0c77ce0`：`ENFORCED / NOT PROVEN`；
- advisory/human-authority `d5a36483`：`ENFORCED / NOT PROVEN`；
- captcha-auth：`NOT ACHIEVED`。

本 Gate 不新增 workflow、schema、parser fallback、correction/retry、Agent 调用、module、registry、engine 或平台能力。Gate PASS 前不得
升级以上成熟度；条件子目标未自然触发时保持原成熟度，不为标签制造样本。
