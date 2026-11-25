# 14. 情景组合评估 API 使用说明

## 1. 能做什么

```http
POST /api/scenario-evaluations
Content-Type: application/json
```

接口接收一次已经人工确认的 90 天情景与产品证据，按 5% 步长计算可行组合，返回最多三个去重方案：`CONSERVATIVE`、`BALANCED`、`RETURN_SEEKING`。同一组合可以同时带多个标签。

这是无状态、只读的条件推演：不请求 OKX、不读取账户、不写数据库、不创建或调整策略。`/scenario-lab.html` 已接入本接口；自定义分析与固定示例都由后端计算，固定示例只用于验证功能，不是当前市场结论。

## 2. 请求结构

请求体直接对应：

```text
ScenarioEvaluationCommand
  evaluation                 冻结的三类行情、共享路径、总资金和约束
  productAssumptions         五类产品的可选计算假设
  productEvidence[]          原始证据字段；不能提交资格状态
```

完整、可序列化的开发样例见：

- [`ScenarioEvaluationFixtures.java`](../src/test/java/app/cryptoincome/scenario/ScenarioEvaluationFixtures.java)
- [`ScenarioEvaluationControllerTest.java`](../src/test/java/app/cryptoincome/scenario/ScenarioEvaluationControllerTest.java)

关键要求：

- `horizon` 必须为 `P90D`，资金语义必须为 `NEW_MONEY_USDT`；
- 必须且只能提供 `BEAR`、`RANGE`、`BULL`；
- 每类行情至少包含 `REPRESENTATIVE` 与 `STRESS`，路径代码不可重复；
- 六条路径必须使用相同起点、相同时间轴和相同终点时间；
- 三类概率必须全部不填，或全部填写且合计为 1；
- `allocationStep` 第一版使用 `0.05`；
- 至少两个产品必须经后端裁决为 `ELIGIBLE`。

## 3. 后端产品资格规则

调用方只能提交 `knownFields`、`unknownFields`、证据时间和来源，不能提交 `ELIGIBLE`。后端固定规则如下：

| 产品 | 最长证据年龄 | 必要字段代码 |
|---|---:|---|
| 90 天定期 | 2 天 | `NET_APR`、`MAX_ALLOCATION`、`TERMS_CONFIRMED`、`RULE_VERSION` |
| 活期 / 流动 USDT | 2 天 | `NET_APR`、`MAX_ALLOCATION`、`REDEMPTION_ASSUMPTION`、`RULE_VERSION` |
| BTC 智能套利 | 2 天 | `HEDGED_NOTIONAL`、`FUNDING_SCENARIOS`、`ALL_KNOWN_COSTS` |
| BTC 固定现货网格 | 7 天 | `GRID_PARAMETERS`、`TRADING_FEES`、`EXIT_SLIPPAGE`、`SHADOW_STATUS` |
| BTC 现货 | 2 天 | `ENTRY_FEE`、`EXIT_FEE`、`EXIT_SLIPPAGE` |

存在未知必要字段时为 `INELIGIBLE`；证据过期但字段完整时为 `RESEARCH_ONLY`。两者都不会进入当前组合枚举。

## 4. 组合约束和计算口径

枚举顺序固定为：资格过滤 → 金额与比例守恒 → 最低活期比例 → 产品比例和定期额度 → 实际金额产品计算 → BTC 敞口 → 下行情景最大亏损 → 帕累托过滤 → 三标签选择。

智能套利和网格的人工成本快照带 `referenceCapitalUsdt`，系统按候选的实际分配金额同比缩放已知绝对成本；交易费仍由各产品计算器按实际金额计算。

当前组合 BTC 硬约束使用 `CONSERVATIVE_SUM_OF_PRODUCT_PEAKS`：将各产品在路径内的峰值 BTC 方向金额相加。它不会低估风险，但可能比真实同一时点峰值更保守。组合级同步权益序列尚未实现，因此 `maximumDrawdownRate` 返回空值，下行压力路径亏损承担硬风险约束。

## 5. 返回结果

```text
ScenarioDecisionReadModel
  evaluation
  optionSet
    feasibleCandidateCount
    paretoCandidateCount
    options[1..3]
      labels[]
      allocations[]          只含比例大于 0 的产品
      pathOutcomes[6]
      targetAttainment
      limitations[]
  qualifiedProducts[]
  excludedProducts[]
  limitations[]
```

每条路径都返回 USDT 净收益、持有期收益率、简单年化、产品结果和带符号贡献。产品贡献、组合贡献、分配金额与期末权益均由领域对象执行守恒校验。

概率为空时，目标结果只有“3 个代表情景中多少个达到 10% / 20%”，概率字段为空。只有三项概率完整且合计为 1 时，才返回目标达成概率；概率不参与默认排序。

## 6. 错误码

| HTTP | code | 说明 |
|---:|---|---|
| 400 | `INVALID_SCENARIO_REQUEST` | JSON、路径、时间、概率、金额或产品假设不合法 |
| 422 | `INSUFFICIENT_PRODUCT_EVIDENCE` | 少于两个产品具备可比较证据 |
| 422 | `NO_FEASIBLE_PORTFOLIO` | 不放宽约束时没有可行组合 |
| 500 | `SCENARIO_CALCULATION_FAILED` | 内部守恒或计算不变量失败，不返回部分方案 |

手工调用时可把请求保存为 `scenario-request.json`：

```bash
curl -sS -X POST http://127.0.0.1:8080/api/scenario-evaluations \
  -H 'Content-Type: application/json' \
  --data-binary @scenario-request.json
```
