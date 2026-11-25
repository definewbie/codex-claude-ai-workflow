# AI Crypto Income Assistant（ai-crypto-arbitrage）

一个**本地运行、只读**的个人数字资产收益管理工具：从 OKX 公共接口采集资金费率事实，帮助回答"这个收益来源还值得继续持有吗"。AI 只做解释和研究草案，**绝不自动交易**；所有资金操作由本人在 OKX 手工完成。

详细产品规范与完整上手手册在 [`docs/`](docs/README.md)，本文是程序功能速览。

## 程序当前能做什么

| 能力 | 说明 |
|---|---|
| 资金费率采集 | `sync-market latest` 手动拉取单个永续合约最近一页资金费率（公共接口，**不需要任何 API Key**） |
| 历史游标回补 | `sync-market backfill` 按 OKX `after` 语义回补 `[from,to)` 区间；页账本与游标持久化、断点续传、统计区分全局事实/观察关系/窗口内外；`COMPLETE` 仅代表已到达目标边界，区间完整性尚未检查 |
| 强类型校验 | 预计/实际费率分列、真实结算时间、未知枚举拒绝入库；内容哈希去重，上游修订追加新事实而不覆盖历史 |
| 幂等与重试 | 逻辑请求（同步/回补各一张表）与执行（`analysis_run` attempt）分离；同 `request-key` 成功后回放不重发请求，失败后同键重试自动开新 attempt |
| 原子提交 | 单页同步一个事务；回补每页一个短事务提交“事实+观察关系+checkpoint”，OKX HTTP 永不在事务内 |
| 卡死恢复 | `stale-list` 只读发现超时 RUNNING（含回补）；`stale-recover` 人工确认后恢复，旧执行被 fencing，终态不可复活 |
| 第一期收益评估 | `assess-income phase-one` 用 90 天已实现资金费率、简单赚币报价和人工成本，输出“简单赚币优先 / 继续观察 / 仅小额验证”；未知成本不会按 0 处理 |
| 网格候选与确认配置 | `sync-product grid-ai-params` 只保存公共 API 候选；`sync-product grid-ui-config` 保存 App 复制 AI 后的最终手动配置，只有后者能进入评估 |
| 固定现货网格评估 | `grid backfill` 有界回补 90 天 BTC-USDT 1 分钟 K 线；`grid assess` 评估确认配置；`grid scenario/sweep` 只读测试区间、网格数及区间外止盈止损，不覆盖确认配置 |
| 收益实验台同屏评估 | 页面以同一候选资金展示固定网格、BTC 智能套利、USDT 简单赚币活期与 90 天定期；网格明确使用实际可运行窗口，不伪装成完整 90 天结果；活期读取 OKX 公共市场预估利率，定期 APR 由用户按确认页录入 |
| OKX 分钟行情 | 页面打开后从 OKX 公共接口读取 BTC-USDT 最新成交价，并按 60 秒节奏追加行情事实与最近已完成的 1 分钟 K 线；不需要 API Key |
| K 线覆盖与同步 | 数据库按滚动 90 天窗口核对 129,600 个分钟槽位；不完整时自动启动持久化全量回补并显示页数、分钟数、游标和失败原因；只有数据库覆盖校验通过才允许同步任务成功 |
| 多情景组合评估 API | `POST /api/scenario-evaluations` 在下行/震荡/上行各两条共享路径上，按 5% 步长枚举产品比例，过滤额度、流动性、BTC 敞口和下行亏损约束，返回去重后的保守/平衡/收益优先方案；纯计算，不请求 OKX、不写数据库 |

## 程序不做什么

- 不自动交易、不接触交易/提现权限、不生成订单；
- 不做常驻后台调度；收益实验台打开时每 60 秒刷新一次 OKX 公共行情，页面关闭后不会继续分钟级采集；
- 冻结调度开发不等于停止归档：首次尽量回补当前可用窗口，之后每周回补最近 14 天，正式评估前再回补到评估时点；连续未成功归档不得超过 4 周，失败使用现有同键续传或人工 stale 恢复；
- 不接入私有账户 API（余额/持仓/手续费核对尚未实现，届时才需要只读 API Key）；
- 不做移动网格、自动 Portfolio 调仓或 AI 直接改数字；情景组合 API 只做条件推演，不代表实盘建议或已执行仓位；固定现货网格仅支持 BTC-USDT、等差、90 天观察窗口和退出回 USDT；
- OKX 公共历史接口只有约三个月窗口；`coverage=COMPLETE` 只代表分页游标已到达目标边界，区间完整性尚未检查，不代表全部历史完整。

## 快速开始

```bash
cp .env.example .env          # 填入本地 MySQL 密码
docker compose up -d          # 启动 MySQL 8.4
mvn test                      # Testcontainers 完整测试

set -a; source .env; set +a
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar \
  sync-market latest --instrument BTC-USDT-SWAP --limit 100 --request-key manual-20260816-01
```

完整步骤（环境要求、健康检查、常见问题）见 [`docs/README.md` 的 Quick Start](docs/README.md#quick-start)。

启动本地收益实验台：

```bash
set -a; source .env; set +a
java -jar target/ai-crypto-arbitrage-0.0.1-SNAPSHOT.jar
```

浏览器访问 `http://127.0.0.1:8080/`。页面会立即读取真实 OKX BTC-USDT ticker 和最近已完成分钟 K 线；若滚动 90 天不足 129,600 根，会自动启动全量回补并显示数据库覆盖度与真实进度。当前页面可运行固定网格回测，并同屏查看网格、BTC 智能套利、USDT 简单赚币活期与定期；各卡片保留自己的实际观察期，只有周期相同的结果才能直接排名。页面也可创建 7–14 天并行 Shadow（80%/30 格候选 vs 原始 50 格）；Live 仍明确显示不可用。页面和 Controller 都不包含 OKX 下单入口。

## CLI 命令与退出码

```text
sync-market latest --instrument BTC-USDT-SWAP --limit 100 --request-key <key>
sync-market backfill --instrument BTC-USDT-SWAP --from 2026-05-01T00:00:00Z --to 2026-08-01T00:00:00Z --limit 400 --max-pages 30 --request-key <key>
sync-market stale-list --older-than PT15M
sync-market stale-recover --request-key <key> --expected-run-id <runId> --older-than PT15M

EVAL_AS_OF="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"  # 在历史回补完成后执行
assess-income phase-one --as-of "$EVAL_AS_OF" \
  --from 2026-05-20T00:00:00Z --to 2026-08-18T00:00:00Z \
  --capital 20000 --simple-apr 0.035 --simple-quote-as-of 2026-08-18T00:00:00Z \
  --simple-source OKX确认页 --simple-terms-confirmed true --hedged-notional 19047.61 \
  --cost-as-of 2026-08-18T00:00:00Z --cost-source OKX预览+用户假设 \
  --opening-cost 36.001 --exit-cost 20.192 --slippage-cost 10 \
  --rebalance-cost 0 --service-cost 0 --terminal-policy exit-to-usdt

sync-product grid-ui-config --instrument BTC-USDT --horizon '3-6个月' \
  --lower-price 46781.1 --upper-price 75680.3 --grid-count 50 --grid-type arithmetic \
  --displayed-annualized-rate 0.2147 \
  --displayed-min-per-grid-rate 0.0061 --displayed-max-per-grid-rate 0.0107 \
  --profit-reinvestment false --auto-earn false \
  --trailing-up false --trailing-down false \
  --take-profit disabled --stop-loss disabled \
  --observed-at 2026-08-19T08:00:00Z \
  --source-reference 'OKX手机App复制AI参数后的手动创建页'
grid backfill --from 2026-05-21T00:00:00Z --to 2026-08-19T00:00:00Z
grid assess --as-of "$EVAL_AS_OF" \
  --from 2026-05-21T00:00:00Z --to 2026-08-19T00:00:00Z \
  --config-id <上一步ID> --capital 20000 --simple-apr 0.035 \
  --maker-fee 0.0006 --taker-fee 0.0007 --exit-slippage-bps 10 \
  --max-drawdown 0.15 --max-path-divergence 0.02 --max-ending-btc-ratio 0.60
```

- `--request-key`：人工幂等键，仅小写字母/数字/`. _ -`（输入统一转小写）；
- `--older-than`：ISO-8601 时长，允许 **PT1M ～ P30D**，stale 判定统一用 MySQL 时钟；
- 退出码：`0` 成功/回放；`1` 内部错误；`2` 参数错误（不创建执行）；`3` 正在执行；`4` 来源失败；`5` 恢复拒绝。
- `assess-income` 是纯只读计算，退出码只有 `0/1/2`；成本参数省略表示 `UNKNOWN`，不会默认为零。
- 收益评估前应在历史回补完成后捕获当前 UTC 时间作为 `--as-of`；完整操作与独立验算见 [`docs/09-Phase-1-Test-Runbook.md`](docs/09-Phase-1-Test-Runbook.md)。

## 技术栈与结构

JDK 25 · Spring Boot 4.1 · MyBatis-Plus（受限 Mapper）· MySQL 8.4（Flyway 唯一迁移入口）· JUnit 5 + Testcontainers。

```text
OKX 公共 API → 强类型校验 → funding_sync_request(逻辑请求)
                                  └─ analysis_run(执行, attempt 递增)
                                       └─ funding_rate_fact(追加式事实) + observation(观察关系)
```

领域层只依赖自定义 Repository/Store 接口；事实表只增不改；`analysis_run` 状态只允许 `CREATED → RUNNING → SUCCEEDED/FAILED`，终态不可复活。

## 文档

- [`docs/README.md`](docs/README.md) — 目标、原则、Quick Start、阶段计划
- [`docs/01-Income-Source.md`](docs/01-Income-Source.md) — 收益来源定义、事实口径、回测要求
- [`docs/02-Portfolio.md`](docs/02-Portfolio.md) — 组合规则与 Incremental Net Benefit 公式
- [`docs/03-AI-Advisor.md`](docs/03-AI-Advisor.md) — AI 顾问边界（只解释，不决策）
- [`docs/05-Product-Baseline.md`](docs/05-Product-Baseline.md) — 恢复后的产品目标、稳定币/BTC 范围与非目标
- [`docs/06-Functional-Architecture.md`](docs/06-Functional-Architecture.md) — 目标功能架构及当前实现保留/冻结/废弃评估
- [`docs/07-Calculation-Method.md`](docs/07-Calculation-Method.md) — 利息、资金费率、基差、网格和组合的统一计算口径
- [`docs/08-Phase-1-Design.md`](docs/08-Phase-1-Design.md) — 第一期产品与实现设计
- [`docs/09-Phase-1-Test-Runbook.md`](docs/09-Phase-1-Test-Runbook.md) — 第一期开跑、独立验算、只读边界和小额验证验收手册
- [`docs/10-Fixed-Grid-Test-Runbook.md`](docs/10-Fixed-Grid-Test-Runbook.md) — 当日参数采集、90 天 K 线回补、固定网格评估与 Shadow 手册
- [`docs/11-Decision-Workbench.md`](docs/11-Decision-Workbench.md) — 回测 UI、Controller、日报，以及 Shadow/Live 的状态边界
- [`docs/12-AI-Forecast-Design.md`](docs/12-AI-Forecast-Design.md) — AI 多情景收益推演、2–3 种组合建议与决策页面设计（仅设计）
- [`docs/13-Scenario-Engine-Backend-Design.md`](docs/13-Scenario-Engine-Backend-Design.md) — 第一条情景计算后端纵向切片：无状态评估、确定性产品计算、组合约束、统一 Read Model 与测试边界
- [`docs/14-Scenario-Evaluation-API.md`](docs/14-Scenario-Evaluation-API.md) — 情景评估 API 的输入、后端资格规则、输出与稳定错误码

情景决策实验室：启动程序后访问 `http://localhost:8080/scenario-lab.html`。页面调用统一情景评估 API；固定示例模式也由后端计算，不代表当前市场结论。原收益实验台仍为 `http://localhost:8080/`。
