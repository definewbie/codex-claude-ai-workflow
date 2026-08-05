# Verification Gate: A10 Terminal-Delivery Same-Case A3

日期：2026-08-05

状态：`READY / NOT RUN`。`run-real-case.sh` 已能互斥透传 external
`--workflow <path>`，并在 provider probe 前验证和持久化 resolved path、SHA-256、
workflow id/version。real-case 结果仍未知，不得提前标记 `PASS` 或 `PROVEN`。

## Fixed comparison inputs

- Deterministic checkpoint：`e25cd9f4`。
- Baseline workstream：T5 v3 `ws-20260729T131421Z-BX54`。
- Baseline result：`COMPLETED / ACCEPT / 10 Agent runs / 10 provider occurrences /
  0 revision / 0 correction / npm test PASS / change scope PASS`。
- Task：`docs/testing/real-case-reports/20260726T153645Z/task.md`。
- Task SHA-256：`84274bfee69ca3e2cead7ba429cf1e29280490381ac69574c68e147cace77be8`。
- Target source：`examples/issue-flow-app`。
- Target source Git tree：`4d7a6c46617d6274bb6f7eb5c0417ad21a3c753e`。
  该 tree 在 T1 baseline `0e0d77ee` 与 T5 v3 baseline `1b235288` 中一致。
- Experiment definition：
  `dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery-experiment.json`。
- Experiment definition SHA-256：
  `30522bd4c1af535b879fb0f50443edc13c0cd07ea3b2f7dbe40b3e7c92345e2b`。

## Verification Gate

- Target: 使用与 T5 v3 相同的 A3 task 和 target baseline，证明 terminal-delivery
  external definition 将实际 Agent node runs 与 provider occurrences 从 `10` 同时降为
  `9`，workflow 仍 `COMPLETED`，final acceptance 仍为 `ACCEPT`，target tests、typed
  change scope、strict sidecar lineage 均不退化，并证明 handoff/next-stage 对该 definition
  为 `NOT_APPLICABLE`。

- Source of truth: persisted `workflow/definition.json` 及其 hash；`nodes/*.json`；
  `runs/*/run.json` 的 node id、`agentResultAcquisition.occurrences`、correction disposition
  和 execution outcome；`metrics/workstream-metrics.json.agentRunCount`；
  `metrics/workstream-metrics.json.revisionCount` 及 `iterations/*.json`；final acceptance strict sidecar；
  `artifacts/change-scope-evaluation.json`；lineage validator 结果；self-test artifact；
  `domain/completion-audit.json`；target worktree `git diff` 与 `npm test` 输出。T5 v3 的
  baseline 数字只来自归档 workstream/report，不重新计算。

- Trigger precondition: HEAD 包含 `e25cd9f4`；worktree 除已提交 Gate/架构文档外
  干净；A3 task hash、target source tree、experiment definition hash 与上方固定值一致；
  fresh package、target baseline `npm test`、Claude/Codex runner-kind 与最小 probes 全部
  PASS；real-case harness 能将 external workflow path 原样传给 `dualctl start --workflow`
  并在 preflight 中持久化 resolved path、SHA-256 和 workflow id；不得同时传
  `--workflow-variant`。

- How precondition is checked before run: 先运行 focused deterministic tests 和 full
  Maven reactor；用 `git write-tree` 核对 disposable target baseline；用 SHA-256 核对 task
  与 workflow 文件；运行 `dualctl workflow validate <experiment-definition>`；执行 harness
  自测证明 start command 恰有一个 `--workflow <absolute-path>` 且没有
  `--workflow-variant`；fresh package 后运行两个 provider probes。任一字段缺失、hash
  不符或 harness 仍只支持 variant，结果立即为 `INVALID`，不得创建 workstream。

- PASS: 以下条件必须全部满足：
  1. external definition source hash 与固定值一致；persisted workflow
     id=`agent-lifecycle-terminal-delivery-experiment`，且 persisted definition 与 external
  source 语义相等；两份实际 hash 均被记录，不假设重新序列化后的字节 hash 相等；
  2. workflow=`COMPLETED`，final acceptance=`ACCEPT`；
  3. 恰好 9 个 `acpx.agent_run` node executions，节点集合严格等于 A1-A9；
  4. 所有 run 的 provider occurrence 合计恰好 9，semantic revision=0，bounded
     correction=0；
  5. strict results 全部 valid，lineage 无缺口；
  6. change scope=`PASS`，仅修改 `src/issueWorkflow.js`、`src/issueApi.js`、
     `test/issueApi.test.js`；
  7. self-test 记录 `npm test` PASS，并由 Gate 后独立重跑再次 PASS；
  8. definition 不含 `stage.claude_handoff`、`stage.generate_next_prompt`、
     `gate.human_next_stage_approval`，也不声明 `stage_handoff` 或
     `prompt.generate_next_stage`；`handoff.json` 与 `next-stage-prompt.md` 均不存在；
     persisted completion audit=`ready`、`gaps=[]`，server projection 与其一致，不产生
     missing-handoff warning。以上共同将 handoff 判定为 `NOT_APPLICABLE`。

  计数公式固定如下，不允许报告脚本另行推断：Agent node runs = persisted
  `workstream-metrics.json.agentRunCount`，并与 9 个 `acpx.agent_run` node instances
  逐项核对；provider occurrences = 对全部 `runs/*/run.json` 中
  `agentResultAcquisition.occurrences` 的数组长度求和；semantic revisions =
  `workstream-metrics.json.revisionCount`，并与 `iterations/*.json` 中由
  `IterationSemantics` 判定为 revision trigger 的文档数量核对；bounded corrections =
  `run.json.correctionAttempted=true` 的 run 数量，并与 occurrence 数量增量核对。

- FAIL: 使用正确且新鲜的 experiment definition 完成有效运行，但 Agent node runs
  或 provider occurrences 不是 9；出现任何 revision/correction；A10 或 next-stage node
  被执行；workflow 未完成；acceptance 不是 `ACCEPT`；target tests、change scope、strict
  result、lineage 任一失败；或 handoff/prompt 被生成、被要求或产生 warning。

- INVALID: 实际运行的是 full/contract/bundled variant；workflow path/hash/id 未持久化
  或不匹配；task/target baseline 不同；旧 jar、`--skip-build`、旧 server/worker；provider
  probe 或 runner-kind 不匹配；run/occurrence/lineage/change-scope/completion-audit 任一
  source of truth 缺失；测试 harness 未证明 external workflow 被透传。`INVALID` 不能解释
  为部分成功，也不能触发第二个样本。

- Stop-loss: workstream 创建后第一时间核对 persisted workflow id/hash；不匹配立即
  quiesce 并标 `INVALID`。首次 strict correction、semantic revision、scope violation、
  typed provider fatal/reconnect/rate-limit failure或 unexpected A10 node 出现时立即停止，
  通过既有 stale/session closure 闭合运行，并将本样本标 `FAIL`；不得 retry、切换模型、
  调 prompt 或启动第二个样本。

- Run artifact freshness: 必须执行 fresh package。preflight 必须记录 clean HEAD、
  checkpoint ancestry、Maven package start/end、jar mtime、latest JVM input mtime、
  `artifactFreshnessProven=true`、external workflow resolved path/hash/id、task hash、target
  tree、provider probe results 和 runner-kind。缺少任一项不得创建 workstream。

## Current readiness decision

```text
Gate definition: COMPLETE
Deterministic A10 boundary: PASS
External workflow harness boundary: PASS
Real-case execution: READY / NOT RUN
```

Readiness 证据：`scripts/run-real-case.test.sh` 已证明 `--workflow` 与显式 `--variant`
互斥、空 workflow path fail closed、validated path 不一致 fail closed、preflight 精确记录
path/hash/id/version、start command 恰有一个 `--workflow <absolute-path>` 且不存在
`--workflow-variant`，workflow validation 失败时 provider probe 不会执行。脚本语法、
`git diff --check` 与 full Maven reactor `9/9 / 1187 tests / 0 failures / 0 errors` 通过。

下一动作只能是在 clean SHA 上执行本 Gate 的 fresh package、真实 workflow validate、
target baseline 与双 provider probes；全部 PASS 后运行一次 A3。不得回退到 bundled full
workflow，也不得启动第二个样本。
