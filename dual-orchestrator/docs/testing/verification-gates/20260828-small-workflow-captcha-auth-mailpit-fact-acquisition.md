# Verification Gate: Small Workflow / Captcha-Auth Mailpit Fact Acquisition

日期：2026-08-28  
状态：`INDEPENDENT REVIEW PASS / NON-PROVIDER PREFLIGHT READY / NOT YET RUN / PROVIDER NOT AUTHORIZED`

## Verification Gate

- Target: 从 clean checkpoint `cca00827` 创建唯一 fresh external small-workflow workstream，在 captcha-auth-app 的 frozen
  baseline 上完成 `MAILPIT-LOCAL-EMAIL-OTP-TASK.md`。本轮必须同时证明：initial contract discovery 基于真实仓库事实生成
  reviewer 接受的 typed TaskContract proposal；Agent runs 与 provider occurrences 恰好为 8、correction/revision 为 0；
  workflow `COMPLETED`；Mailpit 产品功能、安全边界与既有行为不退化。
- Source of truth: frozen Dual/target/workflow/task/workspace-check identity；persisted workflow definition/instance；initial
  repo-map、file-relevance plan、context-governance actual report；initial proposal envelope/sidecar/reference；typed Codex product
  review；human gate ledger；全部 `runs/*/run.json`、metrics 与 provider occurrence records；workspace-preflight evidence；
  change-scope evaluation；self-test、acceptance、completion audit；target Git diff、`mvn test`、Mailpit-backed test 与
  `docker compose config` 的退出码和完整日志。Markdown summary、console 叙述、模型自述和文件名推断不是控制事实。
- Trigger precondition: Dual clean HEAD 必须等于 Gate commit 且包含 `cca00827`；external workflow、target HEAD/tree、task 与
  workspace-check SHA 必须与 Frozen Baseline 一致。fresh package、workflow validation、source-side identity、target-owned
  workspace capture/verify、Claude minimal probe 与 Codex minimal probe 必须全部 PASS。旧 workstream
  `ws-20260827T142330Z-FX2Y`、其 worktree、proposal、session 与 provider output 只作为历史失败证据，不得 resume 或复用。
- How precondition is checked before run: Gate 独立评审 PASS 并提交 clean SHA 后复算 frozen identities，执行 fresh Maven
  package 与 external workflow validation。provider probes 另获授权后各运行一次并闭合 fresh session。fresh-workstream 另获
  授权后，以 fresh JAR 执行 `start` 且不带 `--run`，从 persisted workstream/workspace documents 解析 effective workspace；
  在该 workspace 执行 `real-case-workspace-preflight.sh capture`，并紧邻首次业务 `dualctl run` 前执行 `verify`。只有 typed
  evidence 同时为 `status=passed`、`verificationStatus=passed`，且 persisted graph identity 与 Frozen Baseline 一致，才可
  开始 initial Agent execution。
- PASS: 以下条件必须同时成立：
  1. initial repo-map 的 `source=git_tracked_inventory`，`inventoryHead` 等于 frozen target HEAD，path count 大于 0，保存的
     inventory SHA 与 frozen execution workspace 内重新执行同一 tracked-path 算法所得 SHA 一致；readCandidates 不超过既有
     ceiling 24，inventory 不被当作最终 scope。强制判定以 `TaskContractProposalValidator` 在 proposal validation 时执行的
     inventory re-capture 比对为准；operator 侧等价命令只用于报告交叉核对，不是第二 owner；
  2. initial Claude business occurrence 只有一次，`run.json.toolUsage` 证明至少一次 Read 或 Search 类工具调用；
     context-governance actual report 的 `actualReadEvidence` 非空，每项 path 都属于 repo-map、SHA-256/size 与 execution
     workspace 文件一致；不得使用 terminal、Bash、execute、write、edit 或 create；
  3. initial TaskContract proposal strict parser/schema/semantic validation PASS；当 inventory 非空时，typed
     `evidence_refs` 至少一项与 repo-map path 相交且解析为现存 tracked file；proposal scope 仍由 Agent 选择，inventory 不得
     自动生成或扩张 scope；
  4. initial Codex product review typed decision=`READY_FOR_PRODUCT_REVIEW`，blocking=false，must_fix=[]，且 reviewed subject
     lineage 指向上述 validated proposal；本条件以 typed decision/fields 判定，不扫描自然语言来猜拒绝原因；
  5. persisted graph 不含 A6 plan-review 三节点；A5 typed plan 与 lineage 直接进入 human plan gate，并在用户本人批准后才
     进入实现；全程 Agent runs 与 provider occurrences 均恰好为 8，correction=0、semantic revision=0；
  6. workflow=`COMPLETED`，completion audit=`ready` 且 `gaps=[]`；change scope=`PASS`、lineage 全部有效；terminal
     acceptance=`ACCEPT`，或 typed `ACCEPT_WITH_NOTES + blocking=false + must_fix=[] + missing_evidence=[]`；
  7. `docker compose up -d` 提供 PostgreSQL 与 pinned Mailpit，SMTP=`1025`、inbox UI=`8025`；dev profile 默认投递 email
     OTP 到 Mailpit，生产中立配置和外部 SMTP override 保持可用；
  8. 自动化测试通过真实 Mailpit container/API 取得投递邮件中的 OTP，并用该 OTP 完成邮箱注册；不得新增应用 OTP 泄漏
     endpoint、日志、console adapter 或 parser fallback；
  9. `mvn test` 全绿且既有 99 个测试不退化；`docker compose config` PASS；phone registration、Captcha、OTP lifecycle、
     session、CSRF、normalization、rate limit 与 fake adapters 不退化；README 完整记录本地使用与 development-only 边界。
- FAIL: frozen identity/freshness 正确且 workspace preflight PASS 的有效样本中，出现任一 correction/revision；runs 或
  occurrences 不为 8；initial occurrence 未产生 Read/Search 事实、`actualReadEvidence` 为空/伪造/越界、proposal 无 traced
  inventory evidence、typed product review 非 READY、A6 出现、A5 绕过 human gate；strict contract、scope、lineage、
  self-test、acceptance、completion audit 或 session closure 失败；workflow 未完成；provider/adapter fatal；Mailpit 仅有文档
  无真实 SMTP-backed test；OTP 泄漏或既有安全行为退化。首次 correction/revision 即使业务可修复，本轮也立即 FAIL。
- INVALID: 任一 frozen identity/hash 不符；Gate 未经评审或未由 clean commit 执行；旧 JAR/server；workspace preflight 未
  配置、缺失或 verify 非 PASS；workspace check 因 Docker、Maven、Java、Testcontainers 或环境失败；probe 未通过；使用
  bundled variant、autorun、`--skip-build`、旧 workstream/worktree/session；运行中修改 Gate/task/workflow/Dual、provider/model/
  timeout；手工编辑 ledger；target 在创建 worktree 前不 clean。若 persisted inventory head/path identity 在首次业务调用前
  已漂移，本轮 INVALID；若在有效 execution 内被 validator 拒绝，则按 FAIL 记录 enforcement。INVALID 不得解释为部分成功，
  也不得启动第二个样本。
- Stop-loss: 任一 precondition 失败时不得开始业务 Agent。运行后首次 correction、semantic revision、unexpected node、
  initial fact-acquisition assertion 失败、contract/scope/lineage failure、provider fatal、session closure failure，或 60 分钟
  active runtime 达到时，立即终止并闭合 process/session，保存 ledger、wire stream、prompt、raw output 与 typed evidence。
  `WAITING_FOR_HUMAN` 不计 active runtime；human gate 必须由用户通过 UI 或 fresh JAR CLI 决定。
- Run artifact freshness: Gate review PASS 并提交 clean SHA 后执行 fresh package，记录 source commit、package/JAR、workflow、
  target、task、workspace-check SHA 与构建时间。保存 non-provider preflight、两次 probe、workspace capture/verify、persisted
  graph identity。最终无论 PASS/FAIL/INVALID，都保存完整 ledger、initial fact chain、target diff、测试日志、metrics、session
  closure 与 completion evidence。

## Boundary Ownership

| Fact | Owner | Gate 如何验证 |
|---|---|---|
| tracked path 存在性与 identity | Git + `GitTrackedPathInventory` | repo-map identity + execution workspace 重算 |
| 实际读取事实 | ACP execution/tool usage | run.json + actualReadEvidence path/SHA/size |
| 文件业务相关性与 proposal scope | Claude initial proposal | strict typed proposal；inventory 不自动生成 scope |
| scope 完整性 | Codex product review + human authority | typed READY/must_fix/blocking + verified lineage |
| 可实施授权边界 | compiled TaskContract | downstream scope/change-scope enforcement |
| workflow 与完成事实 | persisted ledger | graph/node/run/gate/audit typed documents |

## Frozen Baseline

```text
Dual fact-acquisition checkpoint:
  cca00827c54b06fef0429103acadd061d8ac9d93
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
task:
  MAILPIT-LOCAL-EMAIL-OTP-TASK.md
task SHA-256:
  32a6f679c1db24f2475edf50c9b5935faa7e059f63f718c049056d7853972a0c
workspace check:
  scripts/verify-workspace.sh
workspace check SHA-256:
  a1902c10e7a9fb1a9abda6c6650814cdf1fdd7273a2676878ff2313b238466c8
target baseline verification:
  scripts/verify-workspace.sh -> 99 tests / 0 failures / 0 errors / 0 skipped
  scoped Docker API workaround: -Dapi.version=1.44
historical defect evidence only:
  ws-20260827T142330Z-FX2Y (WAITING_FOR_RECOVERY; MUST NOT RESUME)
```

## Procedure And Authorization Boundary

1. 本 Gate 与 active plan 先做一次独立只读同行评审；关闭 P0/P1 后仅提交文档 checkpoint。
2. Gate clean SHA 后只允许 fresh package、workflow validation 与 source-side frozen identity/hash 复算；不创建 workstream，
   不调用 provider。
3. Claude/Codex minimal probes 需单独授权；每个只运行一次并闭合 fresh session。
4. probes PASS 后，唯一 fresh workstream 与 workspace capture/verify 仍需单独授权；不得 resume `FX2Y`。
5. 使用 fresh JAR 分段执行，禁 autorun；每个 human gate 展示 typed packet、allowed actions 与后果，由用户本人决定。
6. 无论 PASS/FAIL/INVALID 都只消费一个 fresh 样本；不 retry、不换模型、不修改 Gate、不创建第二个 workstream。

## Maturity Boundary

- initial contract fact acquisition：`ENFORCED / REAL-CASE NOT PROVEN`；
- execution-workspace eligibility：`ENFORCED WHEN CONFIGURED / NOT PROVEN`；
- small workflow graph：`VALIDATED / PRODUCTION PROFILE NOT MODELED`；
- small-profile `9 -> 8`、Mailpit 产品完成与完整复杂 real-case 支持：`NOT PROVEN`。

## Independent Review

独立只读评审：`PASS（P0=0 / P1=0 / P2=2 知会）`。P2-1 已通过上述 re-capture owner 说明闭合；P2-2 记录单样本同时
验证事实链、8-call 与产品验收的合法试错风险，不改变 single-sample、no-retry 纪律。Gate 可执行，满足仅提交 Gate、active
plan 与 review report 的条件；提交后仍不得调用 provider 或创建 workstream。
