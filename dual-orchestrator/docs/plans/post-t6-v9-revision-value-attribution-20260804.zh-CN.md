# T6 v9 Revision 价值归因

日期：2026-08-04
状态：`READ-ONLY PASS / NO_SAFE_SUBTRACTION`

## 1. 唯一问题

本次只回答：最新 T6 的 14 个 Agent runs 和 2 次 semantic revision 中，是否存在一个
能够在不降低最终质量和边界可靠性的前提下直接删除的最大成本项。

不修改 workflow、prompt、parser、contract、provider/model 或 production；不把
bounded correction 误记为 semantic revision；不为了形成优化任务而选择第二候选。

## 2. Source of truth

- workstream：`ws-20260804T092758Z-73XJ`；
- ledger：`/private/tmp/dual-t6-cancer-clinic-v5-run-target-agent-runs/`
  `ws-20260804T092758Z-73XJ/.agent/workstreams/ws-20260804T092758Z-73XJ`；
- report：`docs/testing/real-case-reports/20260804T092743Z/report.md`；
- revision decision：validated producer/reviewer contract sidecars + iteration events；
- token/duration：`runs/*/run.json` 与 `metrics/workstream-metrics.json`。

## 3. 总成本与 revision 增量

完整运行：

| 指标 | 值 |
| --- | ---: |
| Agent runs | 14 |
| Provider occurrences | 17（含 3 次 bounded correction） |
| Semantic revisions | 2 |
| Agent duration | 1,710,586 ms |
| Input tokens | 138,122 |
| Output tokens | 66,082 |
| Cached-read tokens | 4,332,224 |
| Provider-reported total tokens | 4,536,428 |

两次 revision 额外产生 4 个 Agent runs：

| Revision run | Duration | Input | Output | Cached read | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| revised intake Claude | 178,957 ms | 4,361 | 11,390 | 896 | 16,647 |
| second intake Codex review | 29,528 ms | 8,882 | 1,195 | 30,464 | 40,541 |
| revised plan Claude | 169,718 ms | 22,159 | 10,100 | 181,440 | 213,699 |
| second plan Codex review | 24,643 ms | 9,907 | 1,033 | 54,016 | 64,956 |
| **合计** | **402,846 ms** | **45,309** | **23,718** | **266,816** | **335,843** |

revision 增量占：

- Agent runs：28.57%；
- Agent duration：23.55%；
- 非 cache input + output：33.80%；
- provider-reported total tokens（含 cache）：7.40%。

因此最大单一成本候选是 plan revision pair：278,655 total tokens、194,361 ms。

## 4. Revision 1：intake contract

### 初稿缺口

第一次 Codex product review 返回 `NEEDS_REVISION`，typed obligations 为 `CO-1/CO-2`：

- 排序没有明确 cycle number、stable id 的方向和日期缺失语义；
- 没有不可绕过地证明前端只能在 `found` 时消费后端 cycleId，`none/error` 不得跳转。

这些不是措辞偏好。缺少它们时，不同实现都可能“满足最近记录”但产生不同业务结果。

### 修订贡献

修订 proposal 把排序方向、NULLS 语义、stable-id 决胜和后端权威 cycleId 消费写入
clauses/acceptance/test requirements。第二次 review 确认 obligations 已逐项落实，随后
canonical TaskContract 保真编译。

最终实现与 acceptance evidence 实际包含这些行为和测试，因此该 revision 改变了
最终产品契约。

裁决：`VALUABLE / KEEP`。

## 5. Revision 2：implementation plan

### 初稿缺口

第一次 plan review 返回 `NEEDS_REVISION`，指出：

- `cycle_number.desc()` 没有落实 contract 的 `NULLS LAST`；
- response status 仍是普通 `str`，未限制为 `found/none`；
- 前端可能把 unknown status 或 `found` without cycle 错误降级为 `none`。

这些缺口会分别造成排序漂移、后端响应不变量缺失和错误用户提示，并可能触发错误
导航副作用。

### 修订贡献

修订计划加入 `nullslast(ChemoCycle.cycle_number.desc())`、受限 status、合法
status/cycle 组合和 invalid-response error 分支。第二次 review 验证三项 Must Fix；
最终代码、8 个新增 backend tests 和 acceptance evidence 均落实这些内容。

裁决：`VALUABLE / KEEP`。

## 6. Bounded correction 不属于删除候选

三个 Agent run 各发生一次 bounded correction。它们是同 run、最多一次、重新经过
strict parser/validator 的可靠性机制，不是 semantic revision。删除 correction 会把
本次可恢复的协议偏差直接变成 workflow failure，且不会减少静态 Agent node。

裁决：`RELIABILITY MECHANISM / KEEP`。

## 7. 唯一候选裁决

按成本，唯一选中的候选是 **plan revision pair**。但 end-to-end evidence 证明它阻止
了三个真实的 contract-to-plan 漂移，并直接改变最终实现和测试，因此不能进入删除式
implementation。

```text
Candidate: plan revision pair
Cost rank: highest
Defect caught: yes
Final artifact changed: yes
Downstream implementation changed: yes
Safe to delete: no
Verdict: KEEP
```

本轮最终结果：`NO_SAFE_SUBTRACTION`。

这不是优化失败。它防止系统为了减少 4 次调用而删除已经证明有质量收益的 review
闭环。根据“一次只选择一个候选”的约束，本轮不继续审查 stage handoff、self-test、
human gate 或 context node，也不修改 production。

## 8. Boundary Review

`PASS`

- Declared by：producer/reviewer strict sidecar 与 workflow iteration event；
- Proven by：revision 前后 typed artifact 差异和最终 acceptance evidence；
- Consumed by：TaskContract compile、implementation plan、implementation 与 acceptance；
- Enforced before：contract approval 与 implementation activation；
- Failure route：删除任一 revision 会放行已证实的 contract/plan gap；
- Cannot-bypass：现有 revision routing、lineage、contract consistency tests；
- Production change：无。
