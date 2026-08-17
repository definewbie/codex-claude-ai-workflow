# Verification Gate: Terminal-Delivery Captcha-Auth Completion v16

日期：2026-08-17  
状态：`INDEPENDENT REVIEW FINDINGS CLOSED / READY FOR SELECTIVE COMMIT / PREFLIGHT NOT RUN`

## Verification Gate

- Target: 使用一个且仅一个 fresh `agent-lifecycle-terminal-delivery@5` workstream 完成
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。产品必须满足 Java 21、Spring Boot 3、Maven、Thymeleaf、
  PostgreSQL/Flyway、手机号或邮箱加密码登录、登录前 image Captcha、注册短信或邮件 OTP、测试与 README。
  provider 是否稳定输出合法 JSON 不是启动前可保证条件；它是运行期风险，由现有 strict parser、最多一次 bounded correction 和
  fail-closed recovery 守住。
- Source of truth: target `REQUIREMENT.md`；persisted workflow definition/instance；typed proposal/revision/review sidecars 与
  references；human gate/action ledger；每个 `run.json` 及 occurrence raw/candidate SHA；compiled contract、self-test、change-scope、
  completion audit、terminal acceptance；PostgreSQL verification、`mvn test` 与最终 Git diff。Markdown、模型自述、console transcript 和
  acpx record history 不是控制事实；ACP wire stream terminal message 是 provider raw-output 调查依据。
- Trigger precondition: Dual 必须是包含 `0c77ce0c61a4699a608e4322781da4d604df9d5b`、`d5a36483`、`1db62088` 与本 Gate
  commit 的 clean SHA；target 必须保持 clean HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且只有 `REQUIREMENT.md`、
  `VERIFICATION-GATE.md` 两个 tracked files。v15 worktree/workstream/session 不得复用。fresh package、隔离 PostgreSQL 与
  non-provider preflight 必须先 PASS；provider probes 和 fresh workstream 必须在本 Gate 独立 review PASS 后分别获得授权。
- How precondition is checked before run: 记录 Dual/target Git identity、checkpoint ancestry、target files/hashes、workflow source
  SHA/shape、JAR SHA/mtime。删除 v15 worktree 前，完整归档其 workstream ledger，分别为 source/archive 生成按相对路径排序的 SHA-256
  manifest 并要求逐行一致；复算 v15 result 已记录的两个 occurrence candidate SHA，并在归档时现算、登记 `run.json` 与 scoped ACP
  session record/stream SHA。把与 HUAX worktree cwd 匹配的三个 scoped session record 及各自 `.stream.ndjson` 一并复制进 archive；
  归档对账失败时不得删除。核对 scoped sessions 均 closed，保存 target `git worktree list` 后才删除 v15 worktree；不得删除 branch、
  archive、wire stream 或历史 result。fresh package
  后建立 ignored preflight evidence，验证隔离 PostgreSQL health 与精确 `SELECT 1`；双 provider probes PASS 后才创建 fresh workstream，
  并立即核对 persisted workflow identity。
- PASS: workflow=`COMPLETED`；terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；PostgreSQL-backed verification、`mvn test`、README、scope、lineage、
  completion audit 与 final diff 全部通过。若自然发生 revision patch，target uniqueness 必须在 Agent boundary 与 durable merge boundary
  同时成立；若自然到达 architecture review，任意 advisory verdict 必须先停 human design gate，由用户本人 approve/revise。
- FAIL: 产品未完成；strict parser/correction、artifact contract、lineage、revision budget、acceptance、数据库、测试、scope、completion 或
  session closure 任一失败；human gate 被自动解决；typed ledger facts 与外部副作用矛盾；或 exact root-only EOF 在现有完整 pretty-printed
  contract 与 typed correction 下自然复现。root-only EOF 复现时本轮仍判 FAIL，不得自动补 `}`；另记
  `ROOT_CLOSURE_REVISIT_TRIGGERED`，保存原始 wire/candidate 供后续离线设计重评。
- INVALID: identity/hash/JAR/workflow/probe 不符；复用 v15 worktree/workstream/session；人为诱导 revision、review verdict 或 JSON failure；
  运行中修改 Dual、target、workflow、prompt、provider 配置或 timeout；旧 server/JAR；缺少 preflight/captured evidence；手工编辑 ledger；
  或核心 observation fields 缺失而无法判定。provider probe PASS 只证明可调用，不证明 JSON compliance，也不能把后续 strict failure改判
  `INVALID`。
- Stop-loss: 任何 freshness/identity/probe 错误在 workstream 创建前停止。运行后首次 failed bounded correction、strict/lineage/artifact
  failure、session closure failure、terminal contradiction、typed revision budget 耗尽或 90 分钟 active runtime 到达时立即 quiesce；
  active runtime 由 operator 根据 ledger 中各运行区间的 started/finished 时间戳计算，不是 runner 自动计时器；
  `WAITING_FOR_HUMAN` 暂停不计 active runtime。失败后不 retry/resume、不更换 prompt/model/timeout、不创建第二样本。每个 human gate
  必须由用户本人决定。
- Run artifact freshness: 从本 Gate 独立 review 后的 clean Gate SHA 执行 fresh Maven package，记录 reactor 结果、CLI/server JAR
  SHA-256 与 mtime；不得 `--skip-build`。workstream 创建后保存 persisted definition hash/version/node-edge counts、worktree path、branch 和
  scoped session names。preflight 临时目录必须在任何后续 clean 前复制到 durable case report 并逐文件核对 SHA。

## Frozen Baseline

```text
Dual planning baseline: e7939f1d
revision target uniqueness: 0c77ce0c / ENFORCED / NOT PROVEN
advisory human authority: d5a36483 / ENFORCED / NOT PROVEN
ACP turn/permission boundary: 1db62088 / ENFORCED / NOT PROVEN
prompt projection: VALIDATED / NO IMPLEMENTATION
strict rejection + single correction: ENFORCED
provider JSON compliance: NOT PROVEN

workflow: agent-lifecycle-terminal-delivery@5
workflow source sha256: bcf1763a75cc03a118c636590441d5e7f447dd71d157e388934de1f6ce54fcdb
nodes / edges / nominal agent nodes / handoff: 31 / 51 / 9 / 0

target HEAD: 7c7e35e89a66b14c29a335958ad90c8a92779fa0
target tree: bab065a1ff9fe4d7032d1d573b7d1b6c1181a574
requirement sha256: 42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0
target tracked files: REQUIREMENT.md, VERIFICATION-GATE.md
```

## Preflight Procedure

1. 本 Gate 与 active plan 的独立 review findings 关闭后只提交两份文档，获得 clean Gate SHA。
2. 确认 v15 为 `FAIL / CONSUMED`，完整复制
   `/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260817T141747Z-HUAX/.agent/workstreams/ws-20260817T141747Z-HUAX/`
   到独立 archive `captcha-auth-app-agent-runs/archive/captcha-auth-v15-ws-20260817T141747Z-HUAX/`。source/archive 生成同规格排序
   manifest 并逐行一致；复算 v15 result 已记录的 occurrence candidate SHA
   `a21143f6c4eb27c8f5665ccbfc10d3e0ed14dabb1fcbb018ba027a4e8bdac5ff` 与
   `b520d585693da459398c6be52256d4572c48a0ff03580f7f8bef3c6f0c5271d7`。同时现算并登记失败
   `run.json` SHA 与以下三个 HUAX scoped session record/stream SHA，将六个 session 文件复制到 archive 的 `acpx-sessions/`：
   `eeac7643-2d08-4e12-8e61-4da30bdf0197`、`01a01019-ded5-7dc2-b234-21906b2ab671`、
   `381aa06f-bc69-4017-91f4-93cba6c8f251`。ledger source/archive 两份 manifest 只覆盖 ledger 且必须逐行一致；另生成 archive evidence
   manifest 覆盖归档后的 ledger 与 `acpx-sessions/`。session 文件复制前后 SHA 必须一致。
   对账 PASS 后设 archive 只读，核对 sessions closed，保存 target worktree list，再删除 v15 worktree。任何不符均 `INVALID` 且不得删除
   source worktree 或 session files。
3. 在 Dual 根目录执行 fresh Maven package；保存 reactor 结果与 CLI/server JAR SHA/mtime。
4. 创建 ignored 临时目录 `dual-orchestrator/target/verification-preflight/captcha-auth-v16/`，记录 Dual/target/workflow identity、ancestry、
   target cleanliness 与文件清单。该目录不是 durable SoT。
5. 清理旧 v15/v16 PostgreSQL probe containers，确认 `127.0.0.1:55432` 未占用；启动 `postgres:16-alpine` 容器
   `dual-captcha-auth-v16-postgres`，database/user=`captcha_auth_test`/`captcha_auth`，保存 image/container identity、`pg_isready` 与
   精确 `SELECT 1`。
6. 获得 provider-probe 授权后，各执行一个最小 Claude 与 Codex probe，保存 command/session/exit/stop reason/stream SHA/usage/closure。
   probes 不携带业务 contract，不预测 JSON compliance。
7. probes PASS 且获得 fresh-workstream 授权后，创建唯一 fresh workstream；立即核对 persisted workflow identity。之后按 ledger 驱动运行，
   到 human gate 时暂停并报告，不代替用户决策。
8. 无论 PASS/FAIL/INVALID，都生成 durable case report；复制 external preflight 并核对逐文件 SHA；关闭 sessions、quiesce processes，记录
   workstream/target 最终状态。

## Human Interaction

每次 workflow 到达 `WAITING_FOR_HUMAN`：

1. 报告 gate purpose、source outcome、allowed actions、review evidence、must-fix/missing-evidence、revision budget 与动作后果。
2. UI：只操作当前唯一 open gate，action 必须来自 packet `allowed_actions`；revise/clarify 必须填写具体 note。
3. CLI 必须使用本轮 fresh JAR，不使用 PATH 中旧 `dualctl`，也不使用 `--run`：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <packet-allowed-action> --note "<human decision>" \
  --workdir <fresh-worktree> <workstream-id>
```

4. action 后先重新读取 ledger，确认只激活预期 successor/iteration，再显式继续同一 workstream。
5. operator 不替用户选择；autorun 不得解决 design/human-repair gate。

## Observation Discipline

- `ROOT_CLOSURE_REVISIT_TRIGGERED` 仅当 Jackson 为 EOF-at-EOF，且离线结构检查证明 string/array/nested object 已闭合、仅 root object
  depth=1 时成立；判定只用于后续设计，不进入本轮 workflow 控制。
- 若触发，保留 raw stream、candidate、correction prompt/output、run.json 与 SHA；不得依据 transcript 或 record history 代替 wire evidence。
- fence、前置 prose、多文档、invalid enum、depth>1 truncation 均是其他 failure class，不计 root-only recurrence。
- 未触发记 `NOT_TRIGGERED`，不得据此声称 provider compliance `PROVEN`。

## Maturity Boundary

- captcha-auth：`NOT ACHIEVED`，只有本 Gate 产品 PASS 才能升级。
- revision target uniqueness、human authority、ACP turn boundary：只有自然触发且相应 typed facts/side effects 通过才能升级各自成熟度。
- strict rejection/correction 已 `ENFORCED`；provider JSON compliance 保持 `NOT PROVEN`，无论本轮是否偶然输出合法 JSON。
- root-closure normalization 仍未实施。其 revisit trigger 自然出现只授权重新做设计评审，不自动授权代码修改。

本 Gate 不新增 workflow、schema、parser fallback、normalization、correction/retry、Agent 调用、module、registry、engine 或平台能力。
