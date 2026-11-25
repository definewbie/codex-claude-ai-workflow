# 第一期测试手册

> 适用范围：简单赚币与 BTC 智能套利的 90 天收益评估。现货网格尚未实现，不在本手册验收范围内。

## 1. 测试目标与边界

本轮只验证四件事：

1. 程序能正确读取 OKX 已实现资金费率事实；
2. 简单赚币与智能套利能在相同 90 天、相同资金口径下比较；
3. 未知成本保持未知，不被错误当成 0；
4. 收益评估严格只读，不创建 OKX 策略，也不修改同步、回补或 Portfolio 状态。

测试通过只代表“计算链路可以用于 Shadow 观察和小额实验决策”，不代表策略未来能赚钱，更不代表已经达到 10%–20% 年化目标。

| 层级 | 验证内容 | 能证明 | 不能证明 |
|---|---|---|---|
| 自动化测试 | 公式、修订选择、参数约束、数据库行为 | 实现符合计算契约 | OKX 当前产品值得购买 |
| 历史情景分析 | 过去 90 天资金费与人工成本假设 | 该窗口的可比结果 | 无前视偏差的真实回测 |
| Shadow 观察 | 连续记录当时可见的费率、成本和建议 | 建议是否稳定、输入是否可获得 | 真实成交和退出成本 |
| 2,000–5,000 USDT 小额验证 | 实际成交、资金费、再平衡、退出和净收益 | 模型与真实结果的偏差 | 可直接扩大到 200,000 USDT |

必须按顺序推进。历史结果即使很好，也只能进入小额验证。

## 2. 固定测试口径

| 项目 | 值 | 说明 |
|---|---:|---|
| 历史窗口 | `[2026-05-20T00:00:00Z, 2026-08-18T00:00:00Z)` | 恰好 90 天 |
| 候选资金 | 20,000 USDT | 情景资金，不是实盘授权 |
| 简单赚币 APR | 3.5% | 用户提供；实盘前重新核对 OKX 确认页 |
| 智能套利空头名义 | 19,047.61 USDT | OKX 策略预览截图 |
| 聚合开仓成本 | 36.001 USDT | OKX 策略预览截图 |
| 期末策略 | 全部退出回 USDT | 与简单赚币同口径 |

简单赚币 90 天基准收益：

```text
20,000 × 3.5% × 90 / 365 = 172.602739... USDT
```

显示值应为 `172.60 USDT`。

## 3. 准备环境并运行自动化测试

```bash
cd /Users/kriswang/work/projects/ai/ai-crypto-arbitrage
cp .env.example .env                 # 仅首次运行；填写本地 MySQL 密码
docker compose --env-file .env up -d mysql
docker compose --env-file .env ps    # 等待 mysql 变为 healthy（业务账户已执行 SELECT 1）

set -a
source .env
set +a

mvn test
mvn -DskipTests package
```

当前基线预期 `68/68` 通过，并生成 `target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar`。测试使用 Testcontainers MySQL 8.4，不会清空本地开发数据库。

如果自动化测试失败，停止后续步骤；不要用真实数据掩盖代码回归。`.env`、数据库密码和未来的 OKX API Key 都不能提交到 Git。

## 4. 回补 90 天资金费率

公共资金费率回补不需要 OKX API Key：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  sync-market backfill \
  --instrument BTC-USDT-SWAP \
  --from 2026-05-20T00:00:00Z \
  --to 2026-08-18T00:00:00Z \
  --limit 400 \
  --max-pages 30 \
  --request-key backfill-phase1-shadow-20260819-01
```

检查：

- `coverage=COMPLETE`，且窗口内接收数大于 0；
- 不应出现 `SOURCE_EXHAUSTED` 或 `CURSOR_STALLED`；
- 若为 `BUDGET_EXHAUSTED`，用完全相同的 `request-key` 重跑以续传。

`COMPLETE` 只说明游标到达目标边界，**不证明区间绝对无缺口**。当前未实现 gap 检测，结论只能写成“基于已取得的 90 天历史事实”。

### 捕获正确的评估时点

回补成功后执行：

```bash
EVAL_AS_OF="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo "$EVAL_AS_OF"
```

后续统一使用 `--as-of "$EVAL_AS_OF"`。这是强制边界：`as-of` 决定本次评估允许看到哪些数据库修订。若使用早于回补完成时间的固定值，刚入库的历史事实会因 `first_observed_at > as-of` 被正确排除。

产品报价和成本快照时间可以保留截图时间；它们与数据库评估时点不是同一个概念。

## 5. Shadow 测试一：未知成本不得按 0 处理

这里只提供截图中已知的开仓成本，故意省略退出、滑点、再平衡和服务成本：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  assess-income phase-one \
  --as-of "$EVAL_AS_OF" \
  --from 2026-05-20T00:00:00Z \
  --to 2026-08-18T00:00:00Z \
  --capital 20000 \
  --simple-apr 0.035 \
  --simple-quote-as-of 2026-08-18T00:00:00Z \
  --simple-source OKX确认页 \
  --simple-terms-confirmed true \
  --hedged-notional 19047.61 \
  --cost-as-of 2026-08-18T00:00:00Z \
  --cost-source OKX策略预览 \
  --opening-cost 36.001 \
  --terminal-policy exit-to-usdt
```

预期：

- 简单赚币 90 天收益 `172.60 USDT`；
- 已知成本 `36.00 USDT`；
- 输出列出未知成本；
- 净收益和相对简单赚币增量均为“无法精确计算”；
- 建议为“继续观察智能套利”；
- 不得建议直接投入 20,000 或 200,000 USDT。

若程序给出精确净收益或把省略项当成 0，本项失败。

## 6. Shadow 测试二：完整人工假设下验算公式

以下值只验证计算链路，不代表真实成本已确认：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  assess-income phase-one \
  --as-of "$EVAL_AS_OF" \
  --from 2026-05-20T00:00:00Z \
  --to 2026-08-18T00:00:00Z \
  --capital 20000 \
  --simple-apr 0.035 \
  --simple-quote-as-of 2026-08-18T00:00:00Z \
  --simple-source OKX确认页 \
  --simple-terms-confirmed true \
  --hedged-notional 19047.61 \
  --cost-as-of 2026-08-18T00:00:00Z \
  --cost-source OKX预览加测试假设 \
  --opening-cost 36.001 \
  --exit-cost 20.192 \
  --slippage-cost 10 \
  --rebalance-cost 0 \
  --service-cost 0 \
  --terminal-policy exit-to-usdt
```

```text
已知成本 = 36.001 + 20.192 + 10 + 0 + 0 = 66.193 USDT
资金费持有收益率 = Σ 90 天窗口内 realized_rate
资金费收入 = 19,047.61 × 资金费持有收益率
智能套利净收益 = 资金费收入 - 66.193
相对简单赚币增量 = 智能套利净收益 - 172.602739...
```

显示成本应为 `66.19 USDT`。增量 `<= 0` 时建议“优先简单赚币”；增量 `> 0` 且输入完整时最多建议“仅小额验证智能套利”，首次实盘上限必须是 `5,000 USDT`。

## 7. 使用 MySQL 独立复核

打开数据库会话：

```bash
docker compose --env-file .env exec mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

将 `@evaluation_as_of` 替换为第 4 节打印的 UTC 时间：

```sql
SET time_zone = '+00:00';
SET @window_from = '2026-05-20 00:00:00.000';
SET @window_to = '2026-08-18 00:00:00.000';
SET @evaluation_as_of = '请替换为 EVAL_AS_OF，例如 2026-08-19 08:00:00';
SET @hedged_notional = 19047.61;
SET @known_cost = 66.193;
SET @simple_income = 20000 * 0.035 * 90 / 365;

WITH ranked AS (
    SELECT id, funding_time, realized_rate,
           ROW_NUMBER() OVER (
               PARTITION BY exchange, instrument_id, funding_time
               ORDER BY first_observed_at DESC, id DESC
           ) AS revision_rank
    FROM funding_rate_fact
    WHERE exchange = 'OKX'
      AND instrument_id = 'BTC-USDT-SWAP'
      AND funding_time >= @window_from
      AND funding_time < @window_to
      AND first_observed_at <= @evaluation_as_of
), evidence AS (
    SELECT COUNT(*) AS settlement_count,
           SUM(realized_rate > 0) AS positive_count,
           SUM(realized_rate < 0) AS negative_count,
           COALESCE(SUM(realized_rate), 0) AS holding_return
    FROM ranked
    WHERE revision_rank = 1
)
SELECT settlement_count, positive_count, negative_count, holding_return,
       @hedged_notional * holding_return AS funding_income,
       @hedged_notional * holding_return - @known_cost AS net_income,
       @hedged_notional * holding_return - @known_cost - @simple_income AS incremental_vs_simple
FROM evidence;
```
```sql
WITH ranked AS (
    SELECT id,
           funding_time,
           realized_rate,
           ROW_NUMBER() OVER (
               PARTITION BY exchange, instrument_id, funding_time
               ORDER BY first_observed_at DESC, id DESC
           ) AS revision_rank
    FROM funding_rate_fact
    WHERE exchange = 'OKX'
      AND instrument_id = 'BTC-USDT-SWAP'
      AND funding_time >= @window_from
      AND funding_time < @window_to
      AND first_observed_at <= @evaluation_as_of
),
evidence AS (
    SELECT COUNT(*) AS settlement_count,
           COALESCE(SUM(realized_rate > 0), 0) AS positive_count,
           COALESCE(SUM(realized_rate < 0), 0) AS negative_count,
           COALESCE(SUM(realized_rate), 0) AS holding_return
    FROM ranked
    WHERE revision_rank = 1
),
calculation AS (
    SELECT settlement_count,
           positive_count,
           negative_count,
           holding_return,
           @hedged_notional * holding_return AS funding_income,
           @hedged_notional * holding_return - @known_cost AS net_income,
           @hedged_notional * holding_return
               - @known_cost
               - @simple_income AS incremental_vs_simple
    FROM evidence
)
SELECT '资金费结算次数' AS metric,
       CAST(settlement_count AS CHAR) AS value,
       '次' AS unit
FROM calculation

UNION ALL
SELECT '正资金费次数', CAST(positive_count AS CHAR), '次'
FROM calculation

UNION ALL
SELECT '负资金费次数', CAST(negative_count AS CHAR), '次'
FROM calculation

UNION ALL
SELECT '累计资金费率',
       CAST(ROUND(holding_return * 100, 6) AS CHAR),
       '%'
FROM calculation

UNION ALL
SELECT '资金费收入',
       CAST(ROUND(funding_income, 2) AS CHAR),
       'USDT'
FROM calculation

UNION ALL
SELECT '扣除成本后净收益',
       CAST(ROUND(net_income, 2) AS CHAR),
       'USDT'
FROM calculation

UNION ALL
SELECT '相对简单赚币增量',
       CAST(ROUND(incremental_vs_simple, 2) AS CHAR),
       'USDT'
FROM calculation;
```

逐项对比 CLI 的结算次数、正负次数、持有收益率、资金费收入、净收益和增量收益。金额显示允许因四舍五入相差不超过 `0.01 USDT`。

SQL 必须保持与程序相同的修订规则：同一 `funding_time` 选择评估时点前 `first_observed_at` 最新的一条；并列时选 `id` 最大的一条。不得使用 `predicted_rate`，也不得用 observation 数量代替收益事实。

## 8. 参数与风险边界测试

### 开仓成本不能重复计算

聚合成本 `--opening-cost 36.001` 与三个明细：

```text
--opening-spread-cost 15.810 --spot-entry-fee 13.334 --perp-entry-fee 6.858
```

只能二选一。三个明细合计 `36.002 USDT`，与聚合值的 `0.001 USDT` 差异可视为页面精度差异。同时提供两种口径时，预期退出码 `2`，不得相加。

### 其他负向用例

| 操作 | 预期结果 |
|---|---|
| `--simple-terms-confirmed false` | 证据不足，条款未确认成为阻断项 |
| `--terminal-policy keep-strategy-open` | 期末资产形态不同，不能生成精确对比，只能继续观察 |
| 将窗口改成非 90 天 | 退出码 `2`，不读事实、不写状态 |
| 使用空窗口或早于事实观察时间的 `as-of` | 阻断项提示窗口内没有可用事实，建议“证据不足” |

## 9. 只读与可复现性测试

在运行两组评估前后分别记录：

```bash
docker compose --env-file .env exec mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "
   SELECT
     (SELECT COUNT(*) FROM analysis_run) AS analysis_runs,
     (SELECT COUNT(*) FROM funding_rate_fact) AS funding_facts,
     (SELECT COUNT(*) FROM funding_sync_request) AS sync_requests,
     (SELECT COUNT(*) FROM funding_backfill_request) AS backfill_requests,
     (SELECT COUNT(*) FROM funding_backfill_page) AS backfill_pages;"'
```

通过条件：

- 评估前后五个计数完全不变；
- 相同输入连续执行两次，业务数值和建议完全一致；
- 不创建 `analysis_run`，不更新 Portfolio 或 Live 状态；
- 不调用 OKX 私有 API，不创建、修改或停止任何策略。

资金费率事实是 source of truth；`PhaseOneAssessment` 是统一只读结果；CLI 只是投影，不能自行重新判断。

## 10. 记录模板

```text
测试日期：
Git commit / 工作区状态：
历史窗口：
EVAL_AS_OF：
回补 request-key 与 coverage：
简单赚币报价来源、时间、APR、额度和赎回条款：
智能套利预览来源、时间、候选资金和两腿名义：
开仓 / 退出 / 滑点 / 再平衡 / 服务成本：
历史结算次数与资金费持有收益率：
简单赚币 90 天收益：
智能套利净收益：
相对简单赚币增量：
未知项 / 阻断项：
程序建议：
人工决定：保持 / 继续观察 / 小额验证 / 不做：
```

当前评估结果不持久化，应保存终端输出和上述记录作为 Shadow 证据。只有人工记录造成明显负担时，才考虑增加评估快照表。

## 11. 进入小额验证的验收标准

- [ ] 自动化测试全部通过；
- [ ] 90 天回补到达目标边界，没有已知来源错误或游标停滞；
- [ ] `EVAL_AS_OF` 在回补完成后捕获；
- [ ] 简单赚币收益与独立公式一致；
- [ ] 资金费结算数、收益率和收入与独立 SQL 一致；
- [ ] 未知成本不会被当成 0；
- [ ] 聚合开仓成本与明细成本不能重复计入；
- [ ] 期末不退出时不会生成伪精确对比；
- [ ] 相同输入结果可复现，评估前后数据库计数不变；
- [ ] 当前 OKX 页面重新确认 3.5% APR、额度、赎回条款和套利预览；
- [ ] 完整保守成本假设下，相对简单赚币增量仍显著为正；
- [ ] 用户接受首次只投入 2,000–5,000 USDT，并在退出回 USDT 后复盘。

任一项不满足，正确动作是保持简单赚币或继续观察，不是增加模型复杂度。

## 12. 后续固定网格验证

- 固定现货网格双路径回测与 BTC-USDT K 线入库已经实现，其独立测试步骤移至 [10-Fixed-Grid-Test-Runbook.md](./10-Fixed-Grid-Test-Runbook.md)；
- 区间 gap 完整性检查；
- OKX 只读账户 API 和真实费用自动对账；
- 模拟 Portfolio 净值曲线与逐级扩容；
- 自动交易、自动调仓和 AI 下单。

这些不阻断当前只读评估。智能套利比较已经通过后，下一优先级是按网格测试手册完成 Shadow，而不是继续扩建行情或账户平台。
