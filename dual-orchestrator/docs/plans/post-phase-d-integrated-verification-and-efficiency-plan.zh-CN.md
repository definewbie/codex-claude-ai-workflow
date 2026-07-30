# Phase D 后集成验证与效率优化计划

日期：2026-07-28
状态：`T1-T5 PASS / T6 REAL-CASE FAIL / STOP-LOSS ENFORCED`

## 1. 保护的结论

本计划只接受以下问题定义：

> Dual 的结构化控制主链已经基本稳定；当前最大的未完成目标不是继续建模，而是
> 证明最新组合版本可运行，并开始用数据减少 Agent 调用、token、revision 和总耗时。

当前 full workflow 复杂度的价值裁决口径见：
`docs/plans/full-workflow-complexity-value-proof.zh-CN.md`。该文档不新增 phase 或任务，
只把本文 T1-T5 转换成“哪些复杂度值得保留、哪些应被删除”的可证伪问题。

因此：

- 不创建 Phase E 平台；
- 不建设新的 Scope/Context/Policy/Artifact/Evidence/Capability Engine；
- 不新增通用 registry、graph、DSL、state machine、event store 或 metrics store；
- 不重开 Phase A-D 已关闭的模型设计；
- 不因某个 handler 较大就拆分；
- 不在非主目标问题上连续打补丁；
- 每次只允许一个可测、可删除、可回滚的小任务。

## 2. 当前基线

### 2.1 正确性基线

- Phase A-D：`PASS / ENFORCED + PROVEN`；
- full reactor：9/9 modules、1,137 tests、0 failures；
- historical acceptance REJECT replay 已覆盖 classification/revision route；
- 当前工作树在文档 checkpoint 前为 clean。

### 2.2 运行基线

| Case | Duration | Agent runs | Gate rounds | Revisions | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| README（历史 revision path） | 13m19s | 14 | 6 | 2 | COMPLETED / ACCEPT |
| A3 API（历史 revision path） | 22m22s | 20 | 6 | 5 | COMPLETED / ACCEPT |
| A3 API（最新 clean SHA） | 10m42s | 10 | 6 | 0 | COMPLETED / ACCEPT |

最新可信 baseline：

- clean SHA：`0e0d77ee3a7a1b78cca69e64e1c46623c4f6cd5d`；
- workstream：`ws-20260728T153012Z-JH74`；
- report：`docs/testing/real-case-reports/20260728T152730Z/report.md`；
- baseline：`docs/plans/post-phase-d-t2-efficiency-baseline-20260728.zh-CN.md`。

历史 20 次调用和 5 次 revision 不能再描述为健康路径固定成本。当前结构性 baseline 是
10 次 Agent 调用、0 revision。

### 2.3 已有 metrics source

优化数据必须来自：

```text
runs/*/run.json
  -> MetricsProjectionWriter
  -> metrics/workstream-metrics.json
```

可用字段已经包括：

- per-run provider/model/usage/duration；
- input/output/cached read/cached write tokens；
- measured/estimated/session-snapshot usage；
- per-agent/per-node token totals；
- provider cost attribution 与 reliability；
- longest nodes；
- context/tool attribution；
- workflow agent/gate/revision counts。

real-case `summary.json` 缺少这些字段是报告交接缺口，不是新建 metrics model 的理由。

## 3. 总体执行顺序

### 3.1 Mainline promotion 对齐

| Task | Mainline phase | 当前许可 |
| --- | --- | --- |
| T1 | P0：最新主链稳定性 | `PASS` |
| T2 | P0.5：token/cache/cost/latency attribution | `COMPLETE；token/cache/latency promoted；money deferred` |
| T3 | P1 readiness assessment | `PASS / ONE CANDIDATE SELECTED` |
| T4 | P1 单点优化 | `DETERMINISTIC PASS` |
| T5 | P1 同 case 收益证明 | V3 `PASS`；单次 A3 observed benefit proven |
| T6 | P2 中等复杂度验证 | `FAIL`；第二次 semantic revision 触发 stop-loss，禁止自动重跑 |

本文只定义后续依赖，不提前解除
`mainline-stability-and-token-governance-plan.zh-CN.md` 的 phase lock。T1/T2 未
promoted 前，T3 只能登记观察，不得形成 production 任务；T4-T6 不得启动。

```text
T1 最新 SHA A3 integration proof
  -> T2 现有 metrics baseline 提取
  -> T3 只读 bottleneck selection
  -> T4 一个 subtraction optimization
  -> T5 同一 A3 before/after 验证
  -> T6 中等复杂度 case readiness + run
```

每个任务独立 review 和 checkpoint。前一任务未 PASS，后一任务不激活。

## 4. T1：最新组合版本完整验证

状态：`PASS`

### 目标

在包含 Phase A-D 全部改动的 clean SHA 上，运行一次 A3 full workflow，证明组合版本
仍能完整工作。

### 允许

- 固化 Verification Gate；
- fresh Maven package；
- 分别探测 Claude Code 与 Codex acpx 路径；
- 运行一次现有 A3 case；
- 读取 ledger、metrics、target Git diff 和测试结果；
- 写 real-case report。

### 禁止

- 修改 production；
- 调 prompt、模型、timeout 或预算来追求 PASS；
- 自动 retry 一个 INVALID/FAIL case；
- 顺手修复非 blocker；
- 更换 case 后宣称当前 case 通过。

### Verification Gate

```text
Target:
  最新 clean SHA 完成 A3 full workflow，Phase A-D 组合边界无回归。

Source of truth:
  preflight.json；
  workflow/node/execution ledger；
  strict sidecars + publication/lineage；
  task-contract.json；
  change-scope-evaluation.json；
  workstream-metrics.json；
  final acceptance；
  target Git diff + npm test。

Trigger precondition:
  Dual 和目标 repo SHA/cleanliness 可证明；
  fresh package；
  Claude 与 Codex 最小 acpx probe 均通过；
  A3 初始 API 缺失；
  只允许既定三个目标文件。

PASS:
  COMPLETED；
  final acceptance=ACCEPT，或 ACCEPT_WITH_NOTES 且 notes 不得表示 Phase A-D
  boundary、target acceptance、change scope、lineage、routing 或 required
  verification 退化；
  Claude/Codex 均有 execution 与 usage evidence；
  所有 decision 来自 ledger-published strict sidecar；
  无 Markdown/prose control fallback；
  change scope PASS；
  target tests PASS；
  只修改声明文件；
  metrics read model 可读取。

FAIL:
  Dual 自有 contract、lineage、context、routing、scope、handler、recovery、
  acceptance 或 delivery 边界失败。

INVALID:
  provider/model/config、artifact freshness、probe、旧 jar 或目标初始状态错误，
  导致无法判断 Dual。

Stop-loss:
  首个 Dual boundary failure、unexpected file、sidecar bypass 或 provider-invalid
  立即停止，不重试、不调参。

Run artifact freshness:
  报告记录 clean SHA；Maven package 完成时间晚于 JVM source；preflight
  artifactFreshnessProven=true。
```

### 交付

- 一份新 real-case report；
- 完整 Verification Gate；
- PASS/FAIL/INVALID 结论；
- 运行的 `workstream-metrics.json` 路径；
- 不修改 production。

### 结果

- `COMPLETED / ACCEPT`；
- 10 Agent runs，0 revision，0 protocol correction；
- 10/10 strict sidecar valid；
- 4/4 decision node 使用 `source=contract-sidecar`；
- change scope PASS；
- target tests 9/9 PASS；
- Dual 与原始 target repo 均 clean。

## 5. T2：效率 baseline 提取

状态：`COMPLETE / TOKEN ATTRIBUTION PROMOTED / MONEY DEFERRED`

### 目标

从 T1 的现有 metrics read model 生成一个可比较 baseline。只做 projection/report
交接，不改变 runtime state。

### 必须输出

```text
overall:
  wall-clock duration
  agent run count
  gate rounds
  revision count
  protocol correction count

usage:
  measured/estimated run count
  input/output/cached-read/cached-write tokens
  cache ratio（仅 provider usage 可比较时）
  provider/model/billing/reliability

hotspots:
  top nodes by duration
  top nodes by measured tokens
  repeated semantic stages
  largest context sections
```

### 实现约束

优先用只读脚本或 report 生成逻辑消费
`metrics/workstream-metrics.json`。如果需要修改 `run-real-case.sh` 的
`summary.json/report.md`，只能复制现有 read model 字段，不能重新扫描 runs 并实现
第二套 token 计算。

### Cannot-bypass

- report 与 `workstream-metrics.json` totals 必须一致；
- measured、estimated、session snapshot 不得混成同一可信度；
- cache 不可计算时必须保留 typed reason，不能写 0%；
- pricing unknown/subscription token-only 不能伪装为金额 0。

### 结果

`docs/plans/post-phase-d-t2-efficiency-baseline-20260728.zh-CN.md` 已记录：

- 10/10 measured provider runs；
- input 77,000、output 22,768、cached read 477,760、total 577,528；
- descriptive cache ratio 86.12%；
- 6 context packages，estimated context 17,348 tokens；
- per-node duration/token baseline；
- 10 个 `run.json` 与 metrics totals 一致。

金额 baseline 保持 `NOT_PROVEN`：

- OpenAI `costAmount=null` 且 typed unavailable reason 为空；
- GLM `subscription_token_only` 同时存在 USD projection，但可靠性语义不完整。

这不否定 T2 的结构/token/cache/latency baseline。根据 2026-07-29 的优先级决策，
金额计算延后，不再阻塞 T3 只读瓶颈选择；但 monetary cost 继续保持
`DEFERRED / NOT_PROVEN`，不得支持美元节省或 ROI 声明。

## 6. T3：只读瓶颈选择

状态：`PASS / ONE CANDIDATE SELECTED`

### 目标

只选择一个真实最大瓶颈。不得先决定要拆哪个 handler。

### 候选判定顺序

1. 重复 Agent run 或 revision；
2. 同一 semantic stage 的重复 context；
3. token 最大节点；
4. duration 最大节点；
5. gate 等待或 fixed orchestration overhead。

### Selection Gate

候选必须同时满足：

- baseline 中可量化；
- 对总调用、token、revision 或耗时有实质贡献；
- 存在明确旧路径可以删除或减少；
- 不改变业务质量、strict contract、lineage 或 gate semantics；
- 不需要新 module/framework/platform。

本次选择结果：

```text
6 个 active ContextProfiles 的 requirement section
  -> 删除候选
  -> requirement.md 仍由现有 AgentPromptRenderer 唯一注入
```

`implement.claude_self_test_report`、`stage.claude_handoff` 和 6 个 context-build
节点本身保留。完整 producer、consumer、counterfactual 和 cannot-bypass gate 见
`docs/plans/post-phase-d-t3-bottleneck-selection-20260729.zh-CN.md`。

候选进入 T4 前，还必须完成
`full-workflow-complexity-value-proof.zh-CN.md` 定义的 producer、全部 consumer、
subtraction 后 fact owner、blocked side effect、counterfactual 与候选专属
cannot-bypass test。任一项无法回答，不得修改 production。

## 7. T4：单个 subtraction optimization

状态：`DETERMINISTIC PASS`

### 目标

只实现 T3 选中的一个优化，不搭车修改其他问题。

### 开工模板

```text
Target metric:
Baseline:
Existing source of truth:
Repeated/deletable path:
Expected reduction:
Quality invariants:
Cannot-bypass test:
Out of scope:
```

### 完成条件

- 旧路径被删除或调用被明确减少；
- production owner 不增加；
- 无新 module/engine/registry；
- deterministic tests + full reactor PASS；
- commit clean；
- 在 T5 同 case 完成前不宣称实际收益。

### 实施结果

- 从 `intake`、`product_review`、`architecture_review`、`plan_review`、
  `implementation`、`acceptance` 六个 active profile 删除默认 `requirement`
  section；
- `requirement.md` 的 full-workflow 注入 owner 保持为 `AgentPromptRenderer`；
- `full` profile 与显式/custom section 仍可投影 requirement；
- active context package 缺少 requirement 不再失败；
- populated implementation context 不再因重复 requirement 预算压力而无谓降级
  typed recent-agent output；
- focused tests、sibling integration tests 与 9/9 module full reactor 通过；
- T4 实施 checkpoint 本身未运行 provider；后续 T5 v3 已独立完成实际收益验证。

详细证据：
`docs/plans/post-phase-d-t4-context-requirement-subtraction-20260729.zh-CN.md`。

## 8. T5：同 case 前后对比

状态：`V3 PASS / OBSERVED BENEFIT PROVEN FOR ONE A3 SAMPLE`

使用与 T1 完全相同的 A3 task、target baseline、provider/model 和 Verification Gate。

PASS 必须同时满足：

- workflow/acceptance/target tests/change scope 不退化；
- selected metric 实际下降；
- 其他主要指标没有不可解释的大幅退化；
- usage measurement reliability 与 baseline 可比较；
- 未通过减少 required review、放宽 contract 或跳过 gate 换取速度。

如果 provider 导致数据不可比，结论为 `INVALID`，不允许解释为优化成功。

Agent occurrence、node traversal、gate/revision count 属于可直接归因的结构性指标。
Token、duration、cache 属于 provider-sensitive 指标；单次前后结果只有在 usage
reliability 可比且结构性变化能够解释差异时才可作为支持证据。质量结论只覆盖该
A3 case，不外推为整个 full workflow。

### 运行结果

- 报告：
  `docs/testing/real-case-reports/20260729T062639Z/report.md`；
- workstream：`ws-20260729T062937Z-G2XF`；
- fresh Maven package、runner-kind check、Claude Code/GLM-5.2 probe 与
  Codex/OpenAI probe 均通过；
- `design.claude_design` 第一次 occurrence 返回“自然语言前缀 + JSON”，strict
  parser 以 `AGENT_RESULT_PARSE_FAILED` 拒绝；
- 同 session bounded correction 成功，节点最终为 `COMPLETED / SUCCESS`；
- Verification Gate 要求 protocol corrections 保持 0，因此首次 correction
  触发 stop-loss；
- workflow 在第 5 个 Agent run 期间主动中断，没有继续消耗 provider token；
- T5 分类为 `FAIL`，不是 provider/configuration `INVALID`；
- 因 full comparison 未完成，T4 的真实 context/input-token/cache/latency 收益
  仍为 `NOT PROVEN`。

完整分类与边界：
`docs/plans/post-phase-d-t5-a3-comparison-20260729.zh-CN.md`。

### Gate 复核

只读复核将原 gate 评为 `WARN`：bounded correction 是现有可靠性机制，不是功能
质量失败；但它产生的额外 occurrence 必须完整计入效率指标。修订 gate 因此：

- 允许最多一次 correction，最终结果仍须通过 unchanged strict contract；
- Agent node runs 与 prompt occurrences 分开统计；
- correction 的全部 provider usage 与 duration 必须计入；
- primary metric 是 generated context-package estimated tokens；
- provider input/cache/duration 是 supporting measured metrics；
- 第二次 correction、语义漂移或 usage 无法按 occurrence 归因仍触发 stop-loss。

修订 gate 位于：
`/tmp/dual-t5-a3-verification-gate-v2.md`。

### Attempt 2

- 报告目录：
  `docs/testing/real-case-reports/20260729T072220Z/`；
- workstream：`ws-20260729T072508Z-NHNK`；
- fresh build 与 Claude/Codex probes PASS；
- correction=0；
- Codex 对 Claude proposal 的额外数组拒绝语义返回 typed `NEEDS_REVISION`；
- `revisionCount=1` 触发 v2 stop-loss；
- Attempt 2=`FAIL / STOP-LOSS ENFORCED`，不是 provider `INVALID`；
- full comparison 未完成，不能声明 context/input/cache/duration 实际收益。

中断发生在 start 子进程返回前，real-case report 未捕获 workstream id；报告里的
0 runs 是 projection gap，真实 execution facts 位于上述 workstream ledger。
该 gap 已由 `f04cdedb` 修复：中断路径在收集 evidence 前复用既有
`parse_started_workstream()`，synthetic test 证明 report/summary 可恢复真实
workstream/workspace/ledger identity 与 run count。

V3 已按上述口径完成一次且仅一次 A3 full real-case：

- report：`docs/testing/real-case-reports/20260729T131141Z/report.md`；
- workstream：`ws-20260729T131421Z-BX54`；
- workflow `COMPLETED`，acceptance `ACCEPT`，change scope `PASS`；
- target tests 9/9 PASS，strict results 10/10 valid；
- 0 correction，0 semantic revision，usage 10/10 reconciled；
- estimated context `17,348 -> 15,582`（-10.18%）；
- provider-reported total `577,528 -> 533,404`（-7.64%）；
- Agent duration `462.467s -> 398.284s`（-13.88%）。

因此 T5 v3=`PASS`。该收益只覆盖这一份同 baseline A3 样本，不外推为普遍因果
收益；金额继续 `DEFERRED / NOT_PROVEN`。一次性授权已消耗，禁止自动重跑。

## 9. T6：中等复杂度 real-case

状态：`REAL-CASE FAIL / STOP-LOSS ENFORCED / NO RETRY AUTHORIZED`

### 目标

验证优化后的主链是否能扩展到中等复杂度项目，而不是建设复杂 case 平台。

### Readiness assessment 先回答

- 是否有可重复、干净、离线测试可运行的目标 repo；
- 任务是否有 5-10 个明确相关文件，而不是无限 scope；
- 是否存在可执行 acceptance；
- 是否能在运行前证明 provider、freshness 和 stop-loss；
- 是否能区分 Dual failure 与目标项目自身失败。

不满足时不运行。满足后只跑一次。

Readiness assessment 已完成：
`docs/plans/post-phase-d-t6-medium-case-readiness-20260729.zh-CN.md`。

候选 pinned cancer-clinic baseline 已在 ephemeral Python 3.13.1 环境与独立
`cancer_clinic_test` PostgreSQL 上通过 full backend pytest（191 passed），前端
build 也再次通过，target 保持 clean。Target requirements 漏声明
`python-multipart`；readiness 只接受显式、固定并记录的 `0.0.32` supplemental
pin。

一次性 real-case 已执行，报告为
`docs/testing/real-case-reports/20260730T000136Z/report.md`，workstream 为
`ws-20260730T000417Z-5PXU`。Preflight、probes、freshness、usage 与 target
cleanliness 均通过；workflow 在 7 Agent runs、2 次 semantic revision、1 次已消费
的 bounded correction 后进入 `WAITING_FOR_RECOVERY`。第二次 revision 已命中
stop-loss，结果为有效 `FAIL`，不是 `INVALID`。没有 target 写入，禁止自动重跑或
直接修 prompt/parser。

## 10. 任务拆分与依赖

| Task | 类型 | Production 修改 | Provider 消耗 | 解锁条件 |
| --- | --- | ---: | ---: | --- |
| T1 latest integration | 验证 | 否 | 是，一次 | 当前文档 checkpoint |
| T2 efficiency baseline | projection/report | 默认否 | 否 | T1 PASS |
| T3 bottleneck selection | 只读 assessment | 否 | 否 | T2 token/cache/latency baseline PASS |
| T4 subtraction | 实现 | 可能，单 slice | 否 | T3 选出安全候选 |
| T5 same-case comparison | 验证 | 否 | 三次授权样本；v3 PASS | T4 deterministic PASS |
| T6 medium case | readiness + 验证 | 否 | 一次授权已消耗 | FAIL：stop-loss enforced；需只读归因后另行决策 |

## 11. 非主问题处理规则

运行中发现问题时：

```text
是否阻断当前 Verification Gate？
  否 -> 记录 evidence，不改 production，继续主目标
  是 -> 是否破坏既有 source of truth / boundary？
         是 -> 停止当前 task，独立最小修复
         否 -> 按 FAIL/INVALID 收口，不顺手重构
```

连续两次同类失败必须回到 owner/SoT 分析，不能继续调 prompt、阈值、timeout 或补
handler-local if/else。

## 12. 计划完成定义

本计划不是以“新增了性能功能”完成，而是同时满足：

1. 最新组合 SHA 有一个完整 A3 PASS；
2. 有一个可复现、可信度明确的效率 baseline；
3. 至少一个由数据选择的 subtraction 优化被同 case 证明，或 T3 得到有证据的
   `NO_SAFE_OPTIMIZATION`；
4. token/cache/cost attribution 没有被第二套计算污染；
5. 中等复杂度 case 有 PASS，或有证据充分的 `NOT_READY`；
6. 全程没有新增多余平台能力。

## 13. Boundary Review

```text
Boundary Review: PASS for T1/T2 and T3 read-only activation;
WARN for deferred monetary attribution

Touched boundaries:
  文档、Verification Gate、metrics projection handoff。

Authoritative source:
  workflow/node/execution ledger；
  strict sidecars；
  runs/*/run.json。

Projection surfaces:
  metrics/workstream-metrics.json；
  real-case summary/report 与后续 baseline 文档。

New concept needed:
  No。T1-T6 是有序任务，不是新的 domain phase 或 runtime model。

Residual risks:
  whole-run monetary cost 尚未证明；
  OpenAI cost unavailable reason 缺失；
  GLM subscription token-only 金额可靠性尚未校准；
  中等复杂度 case 尚未完成 readiness。

Edit decision:
  T1 已 PASS，T2 已完成；
  T3 只读 assessment 已激活；
  T4-T6 仍受各自 selection/deterministic/real-case gate 约束；
  monetary cost savings claim 保持 blocked。
```
