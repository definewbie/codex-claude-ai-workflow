# T2 A3 Full Workflow 效率基线

日期：2026-07-29
运行日期：2026-07-28
任务状态：`PASS`
P0.5 token/cache/latency attribution：`PROMOTED FOR EFFICIENCY WORK`
Monetary cost attribution：`DEFERRED / NOT_PROVEN`
Production 修改：`0`

## 1. 目标与边界

T2 只完成一件事：

> 从最新 clean SHA 的 A3 full workflow 中，提取可复现的调用、token、cache、
> duration、context、revision 与 gate baseline。

本任务不选择优化对象，不修改 runtime，不新增统计模型，也不通过扫描 transcript、
prompt 或 provider 自然语言重新计算 usage。

### Source of truth

```text
runs/*/run.json
  -> MetricsProjectionWriter
  -> metrics/workstream-metrics.json
  -> 本 baseline 文档
```

- `runs/*/run.json`：每次 provider execution 与 usage 的权威事实；
- `workstream-metrics.json`：统一 metrics read model；
- `summary.json` / `report.md`：real-case 结果投影；
- 本文：只读交接，不产生新事实。

## 2. 运行身份与正确性前提

| 项目 | 值 |
| --- | --- |
| Dual SHA | `0e0d77ee3a7a1b78cca69e64e1c46623c4f6cd5d` |
| Workstream | `ws-20260728T153012Z-JH74` |
| Workflow | `agent-lifecycle-full@6` |
| Result | `COMPLETED / ACCEPT` |
| Fresh build | 9/9 modules，1,137 tests，0 failures |
| Target tests | 9/9 PASS |
| Change scope | `PASS`，1 MODIFY + 2 CREATE，0 violations |
| Strict agent-result | 10/10 valid |
| Contract correction | 0 |
| Revision | 0 |

证据：

- `docs/testing/real-case-reports/20260728T152730Z/report.md`
- `/private/tmp/dual-public-issue-flow-20260728T152521Z-agent-runs/`
  `ws-20260728T153012Z-JH74/.agent/workstreams/ws-20260728T153012Z-JH74/`
  `metrics/workstream-metrics.json`

## 3. Overall Baseline

| 指标 | 值 | 口径 |
| --- | ---: | --- |
| Wall-clock | 10m42s | report startedAt 到 finishedAt |
| Fresh Maven build | 2m28s | preflight build start 到 finish |
| Build 后 wall-clock | 8m14s | wall-clock 减 fresh build |
| Total node duration | 472.447s | metrics 累计 node duration |
| Agent duration | 462.467s | Claude + Codex run duration |
| Runtime node instances | 31 | 31/31 COMPLETED |
| Static workflow nodes | 32 | workflow definition；不等于本次必经节点 |
| Agent runs | 10 | 6 Claude + 4 Codex |
| Gate rounds | 6 | autorun 执行，不代表真实人工等待时间 |
| Decision nodes | 4 | 全部消费 `contract-sidecar` |
| Revisions | 0 | 无 semantic revision |
| Protocol corrections | 0 | 10 次均首次 strict parse 成功 |
| Tool calls | 19 | 7 reads、1 write、11 bash |

历史 A3 的 20 次 Agent 调用、5 次 revision 是旧运行的 revision path，不是当前健康
路径的固定成本。当前可复现结构性 baseline 是 10 次 Agent 调用、0 revision。

## 4. Token 与 Cache Baseline

### 4.1 全局

| 指标 | 值 |
| --- | ---: |
| Measured runs | 10 / 10 |
| Estimated runs | 0 |
| Session snapshots | 0 |
| Input tokens | 77,000 |
| Output tokens | 22,768 |
| Cached-read tokens | 477,760 |
| Cached-write tokens | 0 |
| Provider-reported total | 577,528 |

本文的 descriptive cache ratio 定义为：

```text
cachedRead / (input + cachedRead)
```

结果为 `86.12%`。该比例只描述 provider usage 中的 cache signal，不等价于账单折扣，
也不证明减少了 86.12% 的成本。

### 4.2 Provider / Model

| Provider | Model | Runs | Input | Output | Cached read | Total | Cache ratio |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Zhipu | GLM-5.2 | 6 | 41,844 | 18,912 | 305,472 | 366,228 | 87.95% |
| OpenAI | GPT-5.6-Sol | 4 | 35,156 | 3,856 | 172,288 | 211,300 | 83.05% |

所有 10 次 usage 都是：

```text
measurementType = PROVIDER_RUN_USAGE
source = acpx_stream
```

## 5. Per-Node Baseline

按 duration 降序：

| Node | Agent | Duration | Input | Output | Cached read | Total | Decision |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| `acceptance.codex_verifier_review` | Codex | 85.6s | 1,151 | 1,211 | 81,664 | 84,026 | ACCEPT |
| `implement.claude_implement` | Claude | 65.5s | 12,295 | 5,558 | 111,552 | 129,405 | IMPLEMENTED |
| `intake.claude_brainstorm` | Claude | 60.7s | 3,700 | 4,739 | 896 | 9,335 | READY_FOR_PRODUCT_REVIEW |
| `plan.codex_plan_review` | Codex | 55.9s | 3,217 | 889 | 50,944 | 55,050 | READY_TO_IMPLEMENT |
| `design.codex_arch_review` | Codex | 51.3s | 1,615 | 972 | 39,680 | 42,267 | READY_FOR_ARCH_REVIEW |
| `design.claude_design` | Claude | 42.9s | 10,670 | 3,235 | 31,936 | 45,841 | READY_FOR_ARCH_REVIEW |
| `intake.codex_product_review` | Codex | 33.8s | 29,173 | 784 | 0 | 29,957 | READY_FOR_PRODUCT_REVIEW |
| `plan.claude_plan` | Claude | 31.0s | 5,534 | 2,714 | 15,168 | 23,416 | READY_FOR_PLAN_REVIEW |
| `implement.claude_self_test_report` | Claude | 21.1s | 5,424 | 1,519 | 107,776 | 114,719 | SELF_TEST_COMPLETE |
| `stage.claude_handoff` | Claude | 14.8s | 4,221 | 1,147 | 38,144 | 43,512 | HANDOFF_READY |

这个表只定位成本，不证明某个节点可删除。尤其：

- 最大 duration 节点承担 independent acceptance；
- 最大 token 节点是 implementation；
- self-test 与 handoff 看起来具有评估价值，但其 authoritative fact 和全部 consumer
  尚未完成 counterfactual 审查。

## 6. Context Baseline

本次生成 6 个 context package：

| Target | Profile | Estimated tokens | Budget |
| --- | --- | ---: | ---: |
| `plan.codex_plan_review` | plan_review | 4,044 | 5,500 |
| `acceptance.codex_verifier_review` | acceptance | 3,872 | 6,500 |
| `design.codex_arch_review` | architecture_review | 2,891 | 5,000 |
| `implement.claude_implement` | implementation | 2,821 | 6,000 |
| `intake.codex_product_review` | product_review | 2,346 | 4,000 |
| `intake.claude_brainstorm` | intake | 1,374 | 3,000 |

合计 estimated context 为 17,348 tokens，6/6 within budget。最大 section 是
`plan.codex_plan_review.input_artifacts=944`，其次是
`recent_agent_outputs=743`。

`requirement=467` 在 6 个 profile 中重复出现，是 T3 可检查的重复输入，但当前没有
证据表明删除它不会损害独立 reviewer 的任务语义，因此 T2 不把它登记为删除项。

## 7. Cost Reliability Gap

金额不能作为本次可信 baseline：

1. OpenAI 4 次运行：
   - `billingMode=metered`
   - `costAmount=null`
   - `unavailableReason=""`
2. Zhipu 6 次运行：
   - `billingMode=subscription_token_only`
   - metrics 同时投影 USD 0.834756
   - 当前 read model 没有说明该 USD 是估算、目录价还是实际账单

因此：

```text
whole-run monetary cost = NOT_PROVEN
```

`USD 0.834756` 只能描述 metrics 当前对 Zhipu runs 的 price projection，不能称为
全流程实际成本；OpenAI 也不能按 0 计费。

该问题不阻止调用数、token、cache 和 latency baseline。根据 2026-07-29 的优先级
决策，金额计算延后，不再阻塞主流程效率 assessment；但在金额边界闭合前，不得声称
美元成本下降或 ROI 已证明。

## 8. Cannot-Bypass 验收

| 检查 | 结果 |
| --- | --- |
| metrics run count = 实际 `run.json` 数量 | PASS，10 = 10 |
| metrics total = 10 个 authoritative usage 之和 | PASS |
| measured / estimated / snapshot 未混合 | PASS，10 / 0 / 0 |
| provider/model/billing/source 保留 | PASS |
| cache ratio 标注公式和限制 | PASS |
| 缺失金额未伪装为 0 | PASS |
| baseline 未扫描自然语言重算 token | PASS |
| production 修改 | PASS，0 |

## 9. T2 结论与后续门

### T2 task

`PASS`

已建立可复现的结构、token、cache、context 和 latency baseline。

### P0.5 promotion

调用、token、cache 与 latency attribution：

`PROMOTED FOR EFFICIENCY WORK`

该 promotion 只允许基于结构性调用数、真实 token、cache signal 和 duration 选择优化。

金额归因：

`DEFERRED / NOT_PROVEN`

仍缺 GLM 金额可靠性语义、OpenAI unavailable reason 和完整金额校准。金额缺口必须
保持可见，但不再作为主流程优化的前置阻塞。

### T3

`READY`

T3 首轮只读候选可优先比较：

1. `implement.claude_self_test_report`
2. `stage.claude_handoff`
3. repeated requirement/context sections

候选必须先回答 producer、全部 consumer、authoritative fact、blocked side effect、
counterfactual 和 cannot-bypass test。不能仅按 token 或 duration 排名删除节点。

## 10. Boundary Review

```text
Boundary Review: PASS for T2 extraction and token/cache/latency promotion;
WARN for deferred monetary attribution

Touched boundary:
  Metrics projection / report handoff only.

Declared by:
  runs/*/run.json provider usage and execution facts.

Projected by:
  metrics/workstream-metrics.json.

Consumed by:
  This baseline and future T3 read-only assessment.

Enforced before:
  T3/T4 may use calls/tokens/cache/duration only;
  monetary claims remain blocked while cost is NOT_PROVEN.

Failure route:
  Missing or contradictory monetary attribution remains visible;
  it does not become zero cost and cannot support cost-savings claims.

Cannot-bypass:
  10 authoritative runs reconcile with metrics totals;
  monetary gaps remain explicit.

Residual raw path:
  None introduced by T2.
```
