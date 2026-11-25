# 10. 固定现货网格测试与 Shadow 手册

> 目标：判断一组用户在 OKX App 最终确认的 BTC-USDT 固定现货网格参数，是否值得进入 2,000–5,000 USDT 小额验证。程序不创建、修改或停止 OKX 策略。

## 1. 公共候选不等于 App 最终配置

OKX 公共 `ai-param` API 与手机 App 的 AI 推荐卡可能返回不同参数。公共 API 只用于发现候选：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  sync-product grid-ai-params --instrument BTC-USDT --duration 180D
```

公共候选保存到 `grid_parameter_snapshot`，不能直接进入收益评估。

用户在 App 点击“复制参数至手动创建”后，应以手动创建页最终显示的区间、网格数和高级开关为准，显式保存到 `grid_configuration_snapshot`。只有这个配置 ID 可以进入收益评估。

2026-08-19 的三张 AI 推荐卡说明期限是标签而非精确天数：

| 标签 | 页面展示年化 | 区间 | 网格数 | 单网格收益率 |
|---|---:|---:|---:|---:|
| 长期稳健型 3–6 个月 | 21.47% | 46,781.1–75,680.3 | 50 | 0.61%–1.07% |
| 中期震荡型 1–2 个月 | 14.19% | 59,382.1–72,354.2 | 74 | 0.08%–0.13% |
| 短期波动型 7–20 天 | 12.11% | 59,283.3–72,149.6 | 74 | 0.08%–0.13% |

第一版统一比较 90 天，因此只评估长期候选复制后的最终配置。不得把中期或短期参数机械延长到 90 天，也不得把 `3–6 个月`改写成 `180D`。页面展示年化是外部历史回测报价，不参与准入计算。

## 2. 固定语义

原始配置评估只支持：

- OKX `BTC-USDT` 现货、等差固定网格；
- 初始资金全部为 USDT；
- 精确 90 天、完成的 1 分钟 K 线作为观察窗口；
- 如果观察窗口起点价格在网格外，从首个开盘价进入区间的分钟开始计算历史可运行区间；
- 90 天内不移动区间、不重设网格；
- 利润复投、自动赚币、上下移动、止盈、止损均明确关闭；
- 90 天末把剩余 BTC 按吃单费和显式滑点退出为 USDT。

分钟 K 线无法知道一分钟内先涨还是先跌，因此同时计算：

```text
路径 A：开 → 低 → 高 → 收
路径 B：开 → 高 → 低 → 收
```

准入只使用两条路径中较差的净收益。这不是 OKX 撮合引擎复刻。

参数情景允许临时覆盖网格上下限、网格数，以及在网格区间外设置止盈/止损。覆盖值只属于本次计算，绝不更新 `grid_configuration_snapshot`。复投、自动赚币和移动网格仍不模拟。

## 3. 环境与构建

```bash
docker compose up -d mysql
set -a; source .env; set +a
mvn clean package
```

`source .env` 只影响当前终端；打开新终端后需要重新执行。

## 4. 保存 App 最终人工配置

在 App 将长期 AI 参数复制到“手动创建”后，核对最终页面，再运行：

```bash
CONFIG_OBSERVED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  sync-product grid-ui-config \
  --instrument BTC-USDT \
  --horizon '3-6个月' \
  --lower-price 46781.1 \
  --upper-price 75680.3 \
  --grid-count 50 \
  --grid-type arithmetic \
  --displayed-annualized-rate 0.2147 \
  --displayed-min-per-grid-rate 0.0061 \
  --displayed-max-per-grid-rate 0.0107 \
  --profit-reinvestment false \
  --auto-earn false \
  --trailing-up false \
  --trailing-down false \
  --take-profit disabled \
  --stop-loss disabled \
  --observed-at "$CONFIG_OBSERVED_AT" \
  --source-reference 'OKX手机App复制AI参数后的手动创建页'
```

应在完成页面核对时立即生成 `CONFIG_OBSERVED_AT`；它记录证据采集时刻，不是推荐期限的起止时间。上述价格仅在手动创建页仍与长期推荐卡一致时使用；如果复制后页面发生变化，必须替换成最终页面值。每个功能开关都必须显式提供，程序不会把未知状态默认为关闭。输出的 `config-id` 用于第 6 节。

## 5. 回补 90 天 K 线

选择已经结束的整分钟窗口，且 `to - from` 必须精确等于 90 天。例如：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  grid backfill \
  --from 2026-05-21T00:00:00Z \
  --to 2026-08-19T00:00:00Z \
  --limit 300 --max-pages 1000
```

90 天共有 `129,600` 个分钟槽位。通过条件：

- `coverage=BOUNDARY_REACHED`；
- 按 as-of 选择修订后恰好有 129,600 根连续完成 K 线；
- 任何缺口、重复分钟或未完成 K 线都会阻止收益结论。

回补可安全重跑，事实按内容哈希去重；上游修订同一分钟时追加新版本，不覆盖旧值。Web 入口已有持久化同步任务状态机；CLI 仍可用于独立核验。系统没有常驻后台调度器，因为当前只需要在使用收益实验台时维持滚动 90 天只读数据切片。

### 5.1 在收益实验台点击同步

启动 Web 程序并打开 `http://127.0.0.1:8080/`，进入 `Shadow`：

1. 打开页面后查看顶部“BTC 实时行情与分钟 K 线”；页面会自动请求真实 OKX 公共行情；
2. 若覆盖不足，等待自动全量回补完成。页面显示实际页数、游标、覆盖分钟数和失败原因，不要重复启动；
3. 覆盖显示 `129,600 / 129,600` 后，可点击“用于回测的最新 90 天窗口”自动填写窗口；
4. 进入 Shadow，读取已有观察并点击“刷新样本外日报”。

自动刷新调用 `POST /api/workbench/market-data/refresh`。它先保存真实 OKX ticker，再补最近已完成的分钟线；数据库覆盖不足时启动与 CLI 共用的 `SpotCandleBackfillService`。全量执行只有在数据库确认 90 天恰好覆盖 129,600 个分钟槽位后才能写入 `SUCCEEDED`，否则写入 `FAILED/INCOMPLETE_COVERAGE`。Shadow 刷新仍会独立检查自身窗口在 as-of 时点是否可见。

此操作只访问 OKX 公共行情接口并写入 `spot_candle_fact`，不需要 API Key，不创建、修改或停止任何 OKX 策略。当前实现仍会核对完整 90 天窗口，不是后台调度器。

## 6. 运行收益评估

回补结束后取得评估时间：

```bash
EVAL_AS_OF="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
```

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  grid assess \
  --as-of "$EVAL_AS_OF" \
  --from 2026-05-21T00:00:00Z --to 2026-08-19T00:00:00Z \
  --config-id <第4节得到的config-id> \
  --capital 20000 --simple-apr 0.035 \
  --maker-fee 0.0006 --taker-fee 0.0007 \
  --exit-slippage-bps 10 \
  --max-drawdown 0.15 \
  --max-path-divergence 0.02 \
  --max-ending-btc-ratio 0.60
```

现货挂单 `0.06%`、吃单 `0.07%` 来自用户当前 VIP2 信息。退出滑点 10 bps、最大回撤 15%、路径分歧 2%、期末 BTC 价值占比 60% 只是测试示例，不是系统替用户确定的风险偏好；正式 Shadow 前必须由用户确认或修改。

## 7. 计算与准入规则

程序先验证 90 天观察窗口完整，再定位首个开盘价位于人工确认网格区间的分钟。网格、简单赚币与 BTC 买入持有均使用从该分钟到观察窗口结束的同一实际运行区间；进入区间之前的时间只展示为“前置不具备运行条件”，不计入任何一方收益。随后计算两条路径的网格总净收益、已完成网格利润、库存盈亏、模拟交易费、最大回撤、完成循环数、区间外分钟和期末 BTC 暴露，并把网格数上下约 10% 扰动。

这只是“今天生成的绝对价格参数在过去何时具备运行条件”的历史情景，不等于 OKX 在历史每个时点都会生成同样参数，也不模拟未开启的预设开始条件。输出必须同时展示 90 天观察窗口、历史可运行起点和实际运行时长。

只有以下条件全部成立，才输出“通过 Shadow 后仅小额验证”：

1. 两条路径中较差净收益高于简单赚币；
2. 较差净收益高于 BTC 买入持有，避免把 BTC 上涨误记成网格能力；
3. 最大回撤、路径分歧和期末 BTC 暴露不超过用户阈值；
4. 网格数上下扰动后仍同时超过两个基准；
5. 首次实盘不超过 `min(候选资金, 5,000 USDT)`。

任一条件失败时，正确结果是“优先简单赚币”或“继续观察现货网格”，不是调参直到历史数据变好。

## 8. 参数情景、矩阵与止盈止损

单组情景用于回答“如果缩窄区间、把网格改成 30–50，历史结果如何”：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  grid scenario \
  --as-of "$EVAL_AS_OF" \
  --from 2026-05-21T00:00:00Z --to 2026-08-19T00:00:00Z \
  --config-id <config-id> --capital 20000 --simple-apr 0.035 \
  --maker-fee 0.0006 --taker-fee 0.0007 --exit-slippage-bps 10 \
  --max-drawdown 0.15 --max-path-divergence 0.02 --max-ending-btc-ratio 0.60 \
  --lower-price 50000 --upper-price 73000 --grid-count 40
```

以 `63,146.6` 为锚点，把原区间分别保留 100%、90%、80%，并测试 30/40/50 个网格：

```bash
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  grid sweep \
  --as-of "$EVAL_AS_OF" \
  --from 2026-05-21T00:00:00Z --to 2026-08-19T00:00:00Z \
  --config-id <config-id> --capital 20000 --simple-apr 0.035 \
  --maker-fee 0.0006 --taker-fee 0.0007 --exit-slippage-bps 10 \
  --max-drawdown 0.15 --max-path-divergence 0.02 --max-ending-btc-ratio 0.60 \
  --anchor-price 63146.6 --range-scales 1,0.9,0.8 --grid-counts 30,40,50
```

矩阵输出是敏感度证据，不是“挑历史最高值”的优化器。同一窗口选出的最好参数，必须在新的样本外窗口或 Shadow 中复核。

止盈必须高于网格上限，止损必须低于网格下限。可在 `grid scenario` 命令末尾增加：

```bash
--take-profit 78000 --stop-loss 45000
```

触发后该路径立即停止网格，并按吃单手续费和显式滑点把 BTC 退出为 USDT。分钟 K 线无法证明先触及高点还是低点，所以两条路径分别保存退出原因、触发价和 UTC+8 退出时间；结论继续采用较差路径。简单赚币与买入持有基准使用两条路径各自退出时点/价格计算后较高的一项，属于偏保守口径。

触发时间只能记为该分钟结束时刻，不能声称是 OKX 的真实毫秒成交时间。这里不复刻 OKX 未公开的 AI 回测窗口、撮合排队、数量舍入、初始化库存和年化算法。

## 9. Shadow：先观察，不下单

建议至少连续记录 7–14 天：

```text
日期：
人工确认配置 ID：
当日 BTC 价格：
若按原固定参数运行，累计总盈亏：
同期简单赚币收益：
同期 BTC 买入持有收益：
是否越界：
两路径差异是否扩大：
是否仍满足风险阈值：
人工决定：保持观察 / 放弃 / 进入小额验证
```

当前程序已提供最小 Shadow 账本：页面冻结 80%/30 格候选与确认配置中的原始 50 格基准，手工刷新后追加同一估值时点的两组事实。它没有调度器、没有自动交易，也没有扩展为通用 Portfolio 平台。

## 10. 小额实盘门槛

- 自动化测试全部通过，90 天 K 线连续完整；
- 双路径、双基准和参数扰动全部过关；
- Shadow 期间结论未快速反转；
- 用户重新确认 OKX 参数、费率与风险阈值；
- 只复制固定等差参数，不开启移动网格/自动赚币；
- 首次只投入 2,000–5,000 USDT；
- 预先约定 90 天末退出回 USDT并记录真实费用。

一次真实进入和退出完成对账前，不扩大到 20,000 USDT，更不能直接投入 200,000 USDT。

## 11. 边界证据

| 不变量 | Source of Truth | 唯一消费者 | 失败时阻止什么 | 证明 |
|---|---|---|---|---|
| 公共候选不能冒充最终配置 | `grid_configuration_snapshot` | `FixedGridAssessmentService` | 阻止公共 API 快照直接进入评估 | 类型与 MySQL as-of 测试 |
| 未模拟功能不能静默绕过 | 配置中的六个显式开关/价格 | `FixedGridAssessmentService` | 阻止复投、自动赚币、移动网格或止盈止损配置进入固定模型 | 拒绝测试 |
| K 线完整且当时可见 | `spot_candle_fact` | `SpotCandleQuery` | 阻止网格评估 | 连续 129,600 分钟与修订测试 |
| 网格与两个基准期限一致 | 首个开盘价进入配置区间的分钟 | `FixedGridAssessmentService` | 阻止用90天基准比较较短网格运行期 | 延迟激活共享起点测试 |
| 分钟内路径不确定性可见 | `DualPathGridBacktest` | 决策服务 | 阻止挑选乐观路径 | 双路径分叉测试 |
| BTC 方向收益不冒充网格 | 简单赚币与买入持有双基准 | 决策服务 | 阻止小额准入 | 双基准测试 |
| 建议不等于交易 | typed assessment / CLI 投影 | 用户人工操作 | 系统无交易副作用 | CLI 与依赖边界测试 |
| 情景参数不能污染确认配置 | `GridScenarioParameters`（单次请求） | `FixedGridAssessmentService` | 阻止回测调参覆盖 OKX 证据 | 原配置与有效参数分离测试 |
| 止盈止损是路径级终态 | `GridPathResult.exitReason/exitTime/exitTriggerPrice` | 评估与 CLI 投影 | 阻止终止后继续成交或把一条乐观路径冒充两条路径 | 同分钟同时触及上下阈值测试 |

当前闭环到“只读回测 + 持久化样本外 Shadow”。Shadow 与 Live 已隔离，但 Live 账户事实仍未实现，因此不能把 Shadow 收益称为真实账户收益。
