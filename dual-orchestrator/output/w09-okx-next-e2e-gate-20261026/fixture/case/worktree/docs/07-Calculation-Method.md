# 07. 收益、风险与组合计算口径

> 本文定义系统如何从事实得到 Income Source 评估和 Portfolio 建议。所有公式均为确定性规则；AI 不参与计算。首版允许不完美数据，但不允许把未知当成零或把估计当成实际收益。

## 1. 计算要回答什么

计算不是为了预测币价，也不是为了给每个产品制造一个漂亮 APR。它只回答：

1. 在统一持有期 `H` 内，这个 Income Source 预计净赚多少 USDT？
2. 最坏的合理情景可能损失多少，哪些风险会阻止投入？
3. 相比当前组合和稳定币现金管理，调整后预计多赚多少？
4. 这个增量是否足以覆盖切换成本和估计误差？

## 2. 通用输入

每次评估至少包含：

| 输入 | 含义 |
|---|---|
| `asOf` | 本次评估可使用事实的截止时间，防止前视 |
| `H` | 统一持有期，例如 30 天 |
| `capital` | 本次候选分配的实际资金 |
| `currentPortfolio` | 当前真实配置，而不是建议配置 |
| `scenario` | 保守、基准或乐观 |
| `fees` | 账户实际费率或明确的保守估计 |
| `liquidityReserve` | 不参与锁定策略的必要流动资金 |
| `evidence` | 数据来源、时间范围、缺失项和可信度 |

所有产品必须先计算持有期金额，再从相同 `H` 年化。不得直接比较不同期限的宣传 APR。

## 3. 通用经营净收益

```text
Expected Operating Net Income(H)
= Realizable Income(H)
+ Realizable Spread PnL(H)
+ Other Attributable Income(H)
- Holding Cost(H)
- Holding-period Slippage(H)
- Hedge Error
- Financing Cost(H)
- Terminal Liquidation Cost(H)  # 仅在两侧统一清算回 USDT 时
```

其中：

- 只计算从 `asOf` 到持有期 `H` 末、属于当前或候选配置各自持有路径的经营现金流；
- `asOf` 之前的历史建仓费用是沉没成本，两侧都不计；
- `asOf` 时从当前配置切换到候选配置产生的开仓、平仓、停止、赎回和滑点统一归入第 9 节的 `Switch Cost`，不得在本公式重复；
- 期末清算成本只有在 current/new 两侧都假设于 `H` 末清算回 USDT 时才对称计入，否则两侧都不计；
- 只计算能够归因到该 Income Source 的现金流；
- BTC 价格上涨不能归因于网格能力；
- Grid Profit 不能代替现货网格总盈亏；
- 预计资金费率不能代替历史实际结算费率；
- 关键退出成本未知时，结果只能是区间、未知或小额验证。

持有期收益率和简单年化：

```text
Holding Return(H) = Expected Operating Net Income(H) / Allocated Capital

Simple Net APR(H) = Holding Return(H) × 365 / Actual Days(H)
```

短期年化只用于同口径比较，不能被表述为未来一年一定可持续。

## 4. 稳定币利息

### 4.1 收益来源

稳定币简单赚币的收益来自实际成功出借或匹配的本金。活期利率可能变化，定期可能存在等待匹配、锁定和无法提前赎回。

### 4.2 计算

当使用确定的净 APR 时：

```text
Interest Income(H)
= Matched Principal
× Net APR
× Actual Interest Days / 365
```

更完整的净收益：

```text
Stablecoin Net Income(H)
= Interest Income(H)
- Subscription/Redemption Cost
- Early Exit or Liquidity Cost
```

上式用于完整生命周期归因。用于第 9 节的 Portfolio 比较时，发生在 `asOf` 切换时点的申购、赎回或提前退出成本归入 `Switch Cost`；只有持有期间发生的持续成本和对称的期末清算成本留在经营净收益中。

未匹配本金不产生该 Income Source 的利息，应作为 Portfolio 中仍在等待的现金单独计算，不能既扣一次机会成本、又在组合比较中重复计入。首版允许用户从 OKX 登录页面人工录入：实际可用 APR、匹配比例、期限、是否可赎回和产品上限。人工快照必须保存 `asOf`，不能长期当作固定利率。

对于 OKX 简单赚币活期，若页面 APR 尚未扣除平台收取的收益分成，应先换成用户实际净 APR；OKX 当前公开说明中的活期小时收益公式包含 `× 85%`。这只是外部规则在某个时点的快照：计算必须使用评估时实际确认的规则并保存 `asOf`/版本；规则未知时不得默认使用 85% 或 0%。产品规则可能变化，不能把 85% 硬编码为永久常量。

### 4.3 Portfolio 角色

稳定币利息通常同时承担：

- 流动资金收益；
- 其他 Income Source 的机会成本基准；
- 没有更好机会时的默认配置。

它不是无风险利率，仍包含稳定币、平台、借贷和赎回风险。

## 5. BTC 资金费率套利

### 5.1 收益来源

典型实现是 BTC 现货多头与 BTC 永续空头对冲。正资金费率时空头收取资金费用，负资金费率时空头支付。

必须区分：

```text
Allocated Capital：投入策略的全部资金
Hedged Notional：永续空头的实际名义价值
```

资金费用按名义价值而不是投入总资金计算。

### 5.2 历史事实选择

历史复盘使用 `[from,to)` 内 `realized_rate`：

1. 只使用 `first_observed_at <= asOf` 的事实；
2. 同一交易所、标的和 `funding_time` 若有修订，选择 `asOf` 前最新观察版本，并以 id 作为稳定并列顺序；
3. `predicted_rate` 只能用于当时的前瞻估计，不能替代已经发生的现金流；
4. observation 数量不是收益次数，不能重复计算同一结算事实；
5. 不假设固定 8 小时，以真实 `funding_time` 为准。

### 5.3 资金费现金流

如果历史情景假设名义价值恒定：

```text
Funding Income(H)
= Σ (Hedged Notional × Signed Realized Rate_i)
```

如果有实际仓位快照：

```text
Funding Income(H)
= Σ (Actual Hedged Notional_i × Signed Realized Rate_i)
```

对“现货多头 + 永续空头”而言，正费率计为收入，负费率计为支出。

### 5.4 智能套利完整净收益

```text
Funding Arbitrage Net Income(H)
= Funding Income(H)
+ Spot PnL(H)
+ Perpetual PnL(H)
+ Optional Staking/Interest Income(H)
- Spot Entry/Exit Fees
- Perpetual Entry/Exit Fees
- Slippage
- Rebalance Cost
- Financing Cost
```

上式描述从建仓到退出的完整生命周期归因。用于第 9 节的 Portfolio 比较时，`asOf` 切换产生的两腿开平仓费用和滑点归入 `Switch Cost`；持有期间再平衡、融资和对称期末清算成本才进入经营净收益。

`Spot PnL + Perpetual PnL` 已经包含持有期间的基差变化，不能再额外加一笔 `Basis Change PnL`，否则会重复计算。可以把两腿合计盈亏解释为“基差变化/对冲误差贡献”，但它只是归因视图，不是第三笔现金流。理想情况下两腿方向盈亏接近抵消，但系统不能默认完全抵消。只有资金费率事实时，输出名称必须是“资金费率收入估计”，不能称为“智能套利总收益”。

### 5.5 机会评估

未来持有期使用历史分布形成场景，不选择单一最高点：

- 保守：较低分位资金费率，并计入完整成本缓冲；
- 基准：近期中位数或经过验证的稳健估计；
- 乐观：只用于展示上行，不参与核心资金分配。

至少同时展示正费率占比、负费率占比、最差结算、连续负费率区间和数据范围。数据缺口只降低证据等级；在没有证明会改变结论前，不建设复杂完整性平台。

## 6. BTC 基差收敛

### 6.1 收益来源

基差收益来自现货与交割/衍生品之间的价差在退出或到期时收敛，而不是永续资金费用。

### 6.2 计算

简化情景：

```text
Gross Basis Income (settlement currency)
= (Entry Locked Spread per unit - Exit Residual Spread per unit)
× Common Effective Quantity
```

价差必须声明“每单位名义”的单位、结算币种和合约乘数；例如合约以“张”报价时，先按当时产品规格换算成两腿共同的 BTC 或 USDT 名义数量。`Common Effective Quantity` 只取两腿实际匹配的有效数量，未匹配部分属于方向敞口或对冲误差，不能计入锁定基差收益。不得把任何示例合约乘数硬编码为永久规则。

完整净收益：

```text
Basis Net Income(H)
= Gross Basis Income
- Two-leg Entry Cost
- Two-leg Exit Cost
- Financing Cost(H)
- Slippage
- Roll or Early Exit Cost
```

上式描述完整生命周期归因。用于第 9 节的 Portfolio 比较时，切换时点的两腿进入、退出、展期或提前退出成本归入 `Switch Cost`；持有期间融资成本和对称期末清算成本留在经营净收益，任何一项不得重复。

若持有到交割，必须使用实际到期与结算规则；若提前退出，必须使用当时可成交价差，不能假设一定收敛到零。年化必须与剩余期限一致，不能把一次到期收益直接当成可无限复投的长期 APR。

## 7. BTC 现货网格

### 7.1 收益来源

网格通过区间内反复低买高卖获得已完成网格价差，同时动态改变 BTC 库存。它承担 BTC 方向风险，不属于 Delta 中性收益。

### 7.2 历史或实际总收益

```text
Spot Grid Total PnL
= Current Strategy Equity
+ Withdrawn Amount
- Added Amount
- Initial Invested Capital
```

收益归因可以拆分：

```text
Total PnL
= Completed Grid Profit
+ Unpaired / Inventory PnL
+ Optional Earn Income
- Unattributed Costs
```

决策和 Portfolio 必须使用 Total PnL。Grid Profit 只解释已完成买卖循环，不能单独证明策略赚钱。

### 7.3 进入评估

预测网格收益必须至少包含：

- 价格区间和网格数量；
- 每格净价差是否覆盖双边费用；
- BTC 跌到下限及跌破下限时的库存和亏损；
- BTC 上涨突破上限时的卖出和机会成本；
- 与纯持有 USDT、纯持有 BTC 的对照；
- 参数小幅变化是否使结论反转。

第一期只实现固定等差网格，不实现移动网格、自动调参或自动赚币叠加。历史评估同时比较相同资金、相同起止时间和相同期末清算口径下的：

```text
固定网格 vs USDT 简单赚币
固定网格 vs BTC 买入持有
```

K 线无法证明单根 K 线内的先后路径，因此至少计算 `Open→Low→High→Close` 和 `Open→High→Low→Close` 两种确定路径，输出两种结果，并要求二者均通过准入且差异在显式容忍范围内。这两条路径是敏感性分析，不是覆盖所有逐笔成交顺序的统计置信区间。回测还必须展示最大回撤、期末 BTC 库存、区间外时长和参数扰动结果。

如果缺少价格历史、成交模型、风险上限或双基准，网格只能标记为“继续观察”，不能用 OKX 展示年化或资金费率模型代替。保守路径通过后也只允许 2,000–5,000 USDT 小额验证。

## 8. 现金与“不行动”基准

当前组合必须始终与“不调整”比较：

```text
Keep Income(H)
= 当前各 Income Source 在 H 内的预计净收益
```

未投入机会策略的稳定币并非零收益，通常应计入可获得的现金管理收益。只有当候选组合相对 Keep 明显更好，才建议切换。

## 9. Portfolio 增量比较

比较的是两个都从 `asOf` 出发并持有到 `H` 末的完整组合状态：`current` 表示保持当前配置，`new` 表示在 `asOf` 完成必要切换后持有候选配置。统一公式：

```text
Incremental Net Benefit(H)
= Expected Operating Net Income_new(H)
- Expected Operating Net Income_current(H)
- Switch Cost
- Additional Risk Cost(H)
```

其中：

- `new` 和 `current` 必须使用相同持有期和场景；
- 历史建仓费用发生在 `asOf` 之前，是沉没成本，两侧都不计；
- `Switch Cost` 是从完整 current 状态变化到完整 new 状态的边际一次性过渡成本：旧仓停止、赎回、减量平仓的费用与滑点，新仓或增量仓位的建仓费用与滑点，以及切换时点的一次性重建成本；两状态共有且不变的仓位不产生过渡成本；
- 增仓只计增量开仓成本，减仓只计减量平仓成本，部分切换只计实际变化部分；
- 切换时点的一次性重建归 `Switch Cost`，持有期间的周期性再平衡归各自的经营净收益；
- 资金空窗损失只能使用带场景标签的保守估计，并且必须二选一：经营窗口从切换完成后开始时显式计入 `Switch Cost`；经营窗口已按实际起止时间缩短时不得再次扣除；
- 期末清算成本只有在两侧都假设于 `H` 末清算回 USDT 时才进入各自经营净收益，否则两侧都不计；
- 任一费用只能出现在经营净收益或 `Switch Cost` 其中一处；
- Additional Risk Cost 只有在能够用明确经济缓冲表示时才进入金额公式；
- 无法可靠货币化的风险使用阻断条件、仓位上限或更高安全边际，不伪造精确分数。

实现必须证明两条恒等性质：

```text
INB(current → current) = 0
任一费用在完整分录中出现且仅出现一次
```

### 9.1 动作规则

| 动作 | 条件 |
|---|---|
| 保持 | 增量净收益不显著、证据不足或切换不划算 |
| 小额验证 | 机制可能成立，但真实费用、退出或收益归因尚未闭环 |
| 增加 | 增量净收益超过误差缓冲，且风险/容量约束全部通过 |
| 减少 | 当前收益恶化、风险超限或真实结果持续低于估计 |
| 不投入 | 保守净收益不优于基准，或存在阻断风险 |

“显著”不能使用拍脑袋总分。首版可使用显式安全边际，例如：

```text
Incremental Net Benefit(H)
> Estimation Error Buffer
```

误差缓冲来自手续费区间、滑点区间、历史收益分布和未知成本，不来自 AI 自信度。

## 10. 资金分配

资金分配是评估输出，不是固定比例。V1 使用透明约束，而不是优化器：

1. 先保留必要流动资金；
2. 未验证来源只能得到小额实验资金；
3. 保守净收益不足的来源分配为零；
4. 已验证来源受容量、集中度、退出和方向风险上限约束；
5. 剩余资金回到稳定币现金管理；
6. 相比当前组合没有明显增量价值时保持不动。

目标年化 10%–20% 只用于显示目标差距，不能反向迫使 Portfolio 选择高风险产品。

## 11. 证据与未知值

每个数字都必须标记：

- 来源和获取时间；
- 历史、Shadow 还是真实账户；
- 保守/基准/乐观场景；
- 是否为账户真实值或人工估计；
- 数据范围和关键缺失项。

聚合规则：

- 历史 NULL 表示 UNKNOWN，不能用 `received` 或其他字段近似；
- 任一关键成本未知时，净收益不得显示为精确单值；
- 可以输出 `[low, high]` 区间或“无法判断”；
- 页面、CLI、周报和 AI 必须消费同一结构化结果。

## 12. Backtest、Shadow、Live

三种结果必须完全分开：

| 模式 | 含义 | 可以证明什么 |
|---|---|---|
| Backtest | 历史事实上的假想持有/操作 | 公式和历史情景是否成立 |
| Shadow | 实时数据上的模拟建议 | 建议稳定性和前瞻误差 |
| Live | OKX 真实持仓、现金流和权益 | 用户实际赚了或亏了多少 |

Backtest 和 Shadow 不得更新 Live Portfolio，也不得显示为真实收益。

## 13. 最小实现数据要求

第一批资金费率纵向切片只需要：

- 现有 BTC `realized_rate` 历史事实；
- 用户输入的评估持有期和候选金额；
- 人工输入的实际/保守手续费、滑点和稳定币基准；
- 当前 Portfolio 的人工快照；
- 明确列出的未知基差和对冲误差；
- 结构化输出保持与小额验证两个候选结果。

它不要求先完成自动调度、完美缺口检测、私有 API 或 AI。紧随其后的固定网格切片只增加 90 天 BTC-USDT 公共完成 K 线、确定性成交模型和双基准，不扩张成通用行情/机器人平台。缺失项影响扩大资金时，系统应阻止扩大并要求小额验证，而不是伪造结果。
