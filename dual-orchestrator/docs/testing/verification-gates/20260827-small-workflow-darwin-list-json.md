# Verification Gate: Small Workflow / Darwin List JSON

日期：2026-08-27
状态：`INDEPENDENT REVIEW FINDINGS CLOSED / READY FOR SELECTIVE COMMIT / PROVIDER NOT AUTHORIZED`

## Verification Gate

- Target: 使用一个且仅一个 fresh external workflow
  `agent-lifecycle-small-plan-human-review-experiment@1`，在 clean target
  `/Users/kriswang/work/projects/ai/darwin-skill-optimizer` 完成冻结任务
  `20260827-darwin-list-json.md`。必须同时证明：产品功能与测试通过；A5 typed plan 直接由用户在 human plan gate
  审核，不存在 A6 plan reviewer；实际 Agent node runs 与 provider occurrences 均恰好为 `8`；quality、lineage、
  change scope、self-test、acceptance 与 completion audit 不退化。
- Source of truth: 冻结 task 文件及 SHA-256；external workflow 文件、persisted definition/instance 与 source hash；
  human gate/action ledger；typed proposal、design、plan、self-test、acceptance sidecars 及 verified references；全部
  `runs/*/run.json`、occurrence 与 workstream metrics；change-scope evaluation、completion audit；target Git diff；
  `python3 -m pytest -q` 结果；对临时 output directory 的 JSON stdout 与前后文件 manifest。Markdown summary、console
  叙述和模型自述不是控制事实。
- Trigger precondition: Dual 必须从包含 deterministic checkpoint `e369a6e7` 与本 Gate commit 的 clean SHA fresh
  package；external workflow 必须保持 id/version、SHA-256 与 `28 nodes / 46 edges / 8 acpx.agent_run / 6 gates`
  冻结值。target 必须保持 clean HEAD `1ad41527cf27c7d9903dad2bea2f447ba90ca074`、tree
  `36d498d1dc2ed5f5edb1e3c45c21c5154bb15c33`。target 的 234 个 runnable baseline tests 必须全部 PASS；完整
  collection 必须只出现下方冻结的 3 个外部资产路径错误。workflow validation、artifact freshness 与 Claude/Codex minimal
  probes 必须全部 PASS。不得复用 workstream、target worktree 或 provider session。
- How precondition is checked before run: 复算 Dual/target HEAD、tree、cleanliness、checkpoint ancestry、task SHA、
  workflow SHA/id/version/shape；运行 external workflow validation；记录 `pytest` executable、Python runtime 与 pytest version；
  执行完整 `pytest -q` 并核对恰好 3 个冻结 collection errors，再执行排除这 3 个文件的 runnable suite 并要求
  `234 passed / 0 failed / 0 errors`。fresh Maven package 后记录 reactor result、CLI/server JAR SHA-256 与 mtime。分别创建
  短生命周期 Claude/Codex probe session，
  保存 command、runner kind、exit、stop reason、usage、stream SHA 与 closed 状态。全部通过后才可创建 disposable target
  worktree，并在第一个 Agent 节点前核对 persisted definition 与 frozen source。由于 `run-real-case.sh` 会自动批准 human
  gate，本 Gate 禁止用其 autorun 驱动执行；必须使用 fresh JAR 的 `start/run/review decide/approve` 命令逐段运行，由用户
  亲自处理 human gates。
- PASS: 下列条件必须同时成立：
  1. workflow=`COMPLETED`，completion audit=`ready` 且 `gaps=[]`；
  2. metrics 与 node instances 共同证明恰好 8 个 Agent runs，节点集合严格为 intake Claude、intake Codex review、design
     Claude、architecture Codex review、plan Claude、implementation Claude、self-test Claude、acceptance Codex review；
  3. 所有 `run.json` occurrence 数量之和恰好为 8，correction=0，semantic revision=0；
  4. persisted definition 中不存在 `context.plan_codex_plan_review`、`plan.codex_plan_review`、`plan.decision`，A5 plan
     sidecar 与 verified lineage 直接进入 `gate.human_plan_approval` packet，用户的 typed action 才能继续实现；
  5. `darwin list --json` 满足冻结任务的精确 JSON shape、排序、空列表、坏条目可见、只读行为；默认文本输出不变；
  6. target runnable suite、任务新增 focused tests 与独立验收均 PASS；完整 collection 仍只有相同的 3 个预登记
     `FileNotFoundError`，无新增 collection error；README 已更新，无新增依赖；
  7. change scope=`PASS`，lineage 全部有效，terminal acceptance=`ACCEPT`，或 typed
     `ACCEPT_WITH_NOTES + blocking=false + must_fix=[] + missing_evidence=[]`；
  8. 最终 diff 只包含完成该小功能所需的 CLI、测试和 README 变化，不引入输出框架、持久化格式或 sibling command 改动。
- FAIL: 在身份与 freshness 正确的有效样本中，任一 Agent correction 或 semantic/human revision 发生；Agent runs 或
  occurrences 不为 8；A6 三节点出现或 A5 plan 未经 human gate 直接进入实现；用户认为 A5 plan 不足以安全实施；workflow
  未完成；有效样本中的 provider/adapter fatal 归为 execution-failure；strict parser、typed contract、lineage、scope、self-test、
  acceptance、completion audit 或 session closure 任一失败；
  默认 CLI 行为退化；JSON 不满足 exact keys/order/no-write；测试失败；或实现扩张到冻结 scope 之外。首次 revision 已经使
  `9 -> 8` 的本轮目标不可成立，因此记录 typed evidence 后立即 FAIL，不继续调用 provider 来追求产品完成。
- INVALID: Dual/target/task/workflow identity 或 hash 不符；使用 bundled `--variant`、旧 JAR 或旧 server；pytest runtime
  缺失或与 preflight 记录不符；234 个既有 runnable tests 出现失败、白名单外新增 collection error，或三个冻结错误的文件/异常类型/
  缺失路径签名漂移；probe 未通过；使用 `--skip-build`；复用旧 workstream/worktree/session；autorun 自动批准任一 human gate；运行中修改 Gate、task、
  workflow、provider/model/timeout 或 Dual；手工编辑 ledger；缺失 run/metrics/lineage/completion source of truth；目标仓库在创建
  worktree 前已 dirty。`INVALID` 不得解释为部分成功，也不得启动第二个样本。
- Stop-loss: 任一 precondition 失败时不得创建 workstream。运行后首次 correction、revision、scope/lineage/contract failure、
  provider fatal、session closure failure、unexpected node 或 60 分钟 active runtime 到达时，立即终止并闭合当前 session/process，
  保存 ledger 与 wire evidence；`WAITING_FOR_HUMAN` 不计 active runtime。失败后不 retry/resume、不换模型、不改 prompt/timeout、
  不创建第二个样本。human gate 可以等待用户，不得由 operator、autorun 或模型代替决策。
- Run artifact freshness: Gate 独立 review PASS 并提交后，从该 clean SHA 执行 fresh Maven package；记录 package 起止、
  reactor 状态、JAR SHA/mtime、workflow source SHA、task SHA、target HEAD/tree、provider probe evidence，不得 `--skip-build`。
  workstream 创建后记录 disposable worktree path/branch、persisted workflow id/version/hash/shape 与 scoped session names。最终无论
  PASS/FAIL/INVALID，都将 ledger、preflight、target diff、test output 与 session closure evidence 写入 durable case report。

## Frozen Baseline

```text
Dual deterministic checkpoint:
  e369a6e79a4017edfb1dc33d5aeefab450f882da

workflow source:
  dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/
  agent-lifecycle.small-plan-human-review-experiment.json
workflow id/version:
  agent-lifecycle-small-plan-human-review-experiment@1
workflow sha256:
  d41b835e4d8a8347b64984e085385030246976bbc485a866f215dfafc97faaec
workflow shape:
  28 nodes / 46 edges / 8 acpx.agent_run / 6 human gates

task:
  docs/testing/real-case-tasks/20260827-darwin-list-json.md
task sha256:
  5c0d9f8d9507b75ed72eecd1d4740133ec9e20f67a9420856d9f87ef9e0b2362

target:
  /Users/kriswang/work/projects/ai/darwin-skill-optimizer
target HEAD:
  1ad41527cf27c7d9903dad2bea2f447ba90ca074
target tree:
  36d498d1dc2ed5f5edb1e3c45c21c5154bb15c33
target baseline:
  clean; JSON mode absent; existing list output is human-readable only
target pytest runtime observed during Gate drafting:
  pytest 8.3.4 / Python 3.13.0
target runnable baseline:
  pytest -q --ignore=tests/test_e2e_phase1.py --ignore=tests/test_e2e_phase2.py
    --ignore=tests/test_evaluator.py
  234 passed / 0 failed / 0 errors
pre-existing full-collection boundary:
  tests/test_e2e_phase1.py, tests/test_e2e_phase2.py, tests/test_evaluator.py
  FileNotFoundError: File not found:
    /Users/krisdwang/.hermes/skills/note-taking/obsidian/SKILL.md
  exactly 3 collection errors; no project test executes after collection interruption
```

## Execution Procedure

1. 本 Gate、task 与 active plan 经独立同行评审，关闭 P0/P1 后选择性提交，获得 clean Gate SHA。
2. 执行 fresh package、workflow validation、冻结的 full-collection error-signature 检查、234-test runnable baseline 与
   non-provider identity/freshness preflight。
3. 另行获得 provider-probe 授权后，各执行一次 Claude/Codex minimal probe并关闭 session。
4. probes PASS 且另行获得 real-case 授权后，创建唯一 disposable target worktree 与 fresh workstream；立即核对 persisted
   workflow identity。不得使用 `run-real-case.sh` autorun。
5. 每次到达 human gate，展示 purpose、source outcome、allowed actions、typed evidence、revision budget 与动作后果；用户本人
   通过 UI 或 fresh JAR CLI 选择 action。plan gate 必须展示完整 A5 plan，且 packet 不得包含 A6 review artifact。
6. 任一 revision/correction 立即按 FAIL 止损。否则继续至 terminal，独立复跑 runnable suite、新 focused tests、冻结
   collection-error signature 与 JSON/no-write checks，生成 durable case report并闭合所有 sessions/processes。

## Maturity Boundary

- test-only graph 与 deterministic ledger transition：`VALIDATED`。
- reused contract、lineage、human gate、self-test 与 acceptance owners：维持既有 `ENFORCED`。
- small-profile quality、8-call cost 与真实完整 workflow：`NOT PROVEN`。
- production small profile：`NOT MODELED`。

一次 PASS 只证明这个冻结小任务可安全省去 A6，不足以把 small profile 提升为 production variant，也不足以删除 bundled
workflow 的 plan reviewer。若 FAIL，先按 evidence 判断是 A5 plan quality、模型/adapter execution 或 harness boundary；不得直接
增加 prompt、retry、node 或新 framework。
