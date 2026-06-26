# Provider Cache Aware 成本模型与会话压缩策略设计

> 状态：最小决策层已实现。已支持 provider pricing 复算、cache signal 可信度、provider cache decision 投影，以及 `DUAL_SESSION_COMPACTION_EXPERIMENT=provider_cache_aware` 的保守执行策略。
> 触发：simple public benchmark 显示 naive session compaction 让 `totalTokens` 和 `cachedReadTokens` 下降，但 `inputTokens` 与美元成本上升。说明“少 token”不等于“低成本”。
> 范围：统一 Claude / ChatGPT(OpenAI) / DeepSeek / GLM / Anthropic-compatible proxy 的 usage 与成本归因；建立 cache hit 可信度验证；为 session transcript compaction 提供经济账决策。
> 非目标：不切默认 session 策略；不引入 RAG/GraphRAG；不把 provider session transcript 当长期 memory；不在代码里写死某一天的价格。

## 0. 一句话结论

**dual 不能把 `cachedReadTokens` 简单当成坏 token，也不能把 `costAmount` 简单当成真账单。**

Provider cache aware 的目标是把每次 agent run 拆成：

- cache hit input：便宜但仍占 context window。
- cache miss input：昂贵，通常是真实账单压力。
- output：昂贵，且常由重复探索 / 低质量 prompt 放大。
- client reported cost：acpx / Claude Code / 兼容层回报的客户端估算成本，不一定等于 provider 真实账单。
- official calculated cost：dual 按 provider/model 官方价格表复算的成本。
- billing observed cost：provider 控制台或账单导出的真实扣费，用来校验 official calculated cost。
- client cost drift：客户端估算与官方复算的差异，只能说明客户端估算口径是否一致，不能反向否定 cache token usage。

只有在 cache hit 可信、计费模型可信之后，session compaction 才能用来做成本优化；否则 compaction 只是在改变统计形态。

## 0.1 当前最小实现

- `ProviderCachePolicy` 是共享决策点：按 `inputCacheHitPer1M / inputCacheMissPer1M` 输出 `preserve_session`、`consider_compaction` 或 `measure_only`。
- `MetricsProjectionWriter` 在 `tokenAttribution.perRun[].costEconomics` 输出 `providerCacheDecision`、`cacheHitToMissRatio`、`officialCalculatedCost`、`clientReportedCost`，并在 `tokenAttribution.costEconomics.byDecision` 汇总。
- `TraceCommand` 展示 provider cache decision 汇总。
- `SessionCompactionPolicy` 新增 `provider_cache_aware` 实验模式：
  - cache hit 很便宜时保留原 session，避免 naive compaction 把便宜 cache hit 换成昂贵 cache miss。
  - cache hit 昂贵时，才对既有 Claude implement / Codex review 目标节点启用 compaction。
  - pricing 缺失或未知时只观测，不自动 compaction。
- 当前价格配置使用扁平 `models[]` 文件：默认读取 `<workdir>/.agent/provider-pricing.json`，也可用 `DUAL_PROVIDER_PRICING_PATH` 指定。
- DeepSeek 国内账单已用 2026-06 CSV 验证：`input_cache_hit_tokens=0.000000025 CNY/token`、`input_cache_miss_tokens=0.000003 CNY/token`、`output_tokens=0.000006 CNY/token`，三次 simple benchmark 复算为 `1.3907498 CNY`，与当天账单增量量级一致。

---

## 1. 问题边界

### 1.1 当前实验事实

simple public benchmark A/B：

| 指标 | baseline | naive compaction | 结论 |
|---|---:|---:|---|
| `totalTokens` | 902,262 | 771,878 | 下降 |
| `cachedReadTokens` | 807,168 | 599,168 | 下降 |
| `inputTokens` | 75,408 | 149,166 | 上升 |
| `client costAmount` | 1.296 USD | 1.789 USD | 上升 |
| `toolCalls/read` | 47 / 36 | 50 / 40 | 略升 |
| 质量 | ACCEPT / 0 repair | ACCEPT / 0 repair | 未退化 |

这个结果说明：

1. naive compaction 可以降低表面 token volume。
2. 但它把部分便宜的 cache hit 上下文换成了昂贵的 cache miss input。
3. 如果目标是成本，不能按 `totalTokens` 决策。
4. 这个 `costAmount` 不能直接当 provider 真实账单；DeepSeek 国内账单应按官方 CNY 价格表复算。
5. 如果目标是 context window 风险，`cachedReadTokens` 仍然有意义，因为它占窗口。

### 1.2 Source of Truth 划分

| 对象 | 角色 | 是否 SoT | 说明 |
|---|---|---|---|
| provider raw usage event | Execution 外部事实 | 是 | 原始账单/usage 字段必须落盘，不能只保留归一化结果 |
| dual normalized usage | Execution typed fact | 是 | provider 字段归一化后的每 run usage |
| provider pricing registry | Definition | 是 | provider/model/billing policy 的权威配置 |
| official cost calculation | Projection / read model | 否 | 按官方价格表可重算，不改 workflow 状态 |
| client reported cost | Adapter evidence | 否 | acpx/Claude Code 估算成本，只能展示和对账，不能作为 provider billing SoT |
| session compaction decision | Execution policy decision | 是 | 影响下一次 agent run 的 session 选择，必须记录理由 |
| UI/CLI token panel | Projection | 否 | 只能展示统一 read model，不得自己计算状态 |

### 1.3 关键边界原则

- `cachedReadTokens` 是 provider usage 事实，不是优化目标。
- `costAmount` 是客户端回报，必须与 official calculation / billing export 分开。
- pricing 是 Definition，不是 handler 里的常量。
- compaction 是 Execution policy，不是 Memory 本身。
- WorkstreamMemory 是长期语义连续性的 SoT；provider session 是短期 execution memory。

---

## 2. Provider / Model 统一模型

### 2.1 为什么必须覆盖 Claude 和 ChatGPT

dual 的 agent 后端不是单一 provider：

- Claude Code CLI 可能直连 Anthropic，也可能走 DeepSeek/GLM 的 Anthropic-compatible endpoint。
- Codex/ChatGPT 路径可能走 OpenAI API，也可能由 acpx 以其它 provider 适配。
- 同一“Claude agent”并不等于同一 billing provider；同一“OpenAI/Codex agent”也不等于同一 pricing policy。

因此模型必须拆成四层：

```text
Agent Role      : claude / codex
Runtime Adapter : acpx-claude / acpx-codex / openai-api / anthropic-api / proxy
Provider        : anthropic / openai / deepseek / zhipu / custom-proxy
Model           : claude-sonnet-* / gpt-* / deepseek-* / glm-* / proxy-alias
```

`Agent Role` 只描述工作职责；`Provider + Model` 才能决定账单。

### 2.2 ProviderPricingRegistry

落地建议：`dual-runtime` 或 `dual-ledger-file` 中定义只读 pricing registry，配置文件在：

```text
dual-orchestrator/config/provider-pricing.json
```

结构：

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-06-26",
  "providers": [
    {
      "providerId": "openai",
      "sourceUrl": "https://platform.openai.com/docs/pricing",
      "models": [
        {
          "modelPattern": "gpt-*",
          "currency": "USD",
          "unit": "per_1m_tokens",
          "inputCacheHit": 0.0,
          "inputCacheMiss": 0.0,
          "output": 0.0,
          "supportsPromptCache": true,
          "billingNotes": "Fill from official OpenAI pricing snapshot."
        }
      ]
    }
  ]
}
```

说明：

- 价格必须可更新，不能散落在 Java handler。
- `sourceUrl` 和 `updatedAt` 必须随价格快照保存。
- `modelPattern` 支持代理模型名，如 `deepseek-*`、`glm-*`、`claude-*`。
- 若 provider 不返回 cache hit/cached read 字段，`supportsPromptCache=false` 或 `cacheSignalReliability=unknown`。

### 2.3 Provider 覆盖

| provider | 常见 runtime | cache 字段可信度 | 设计要求 |
|---|---|---|---|
| `anthropic` | Claude Code / Anthropic API | 需按 Anthropic prompt caching 字段映射 | 支持 cache read/write 价格与 TTL 语义 |
| `openai` | ChatGPT/Codex/OpenAI API | 需按 OpenAI cached input 字段映射 | 区分 input cached / uncached / output |
| `deepseek` | Claude Code 走 Anthropic-compatible proxy | 需验证 proxy 是否透传真实 cache usage | DeepSeek 明确有 cache hit/miss 价格，必须复算 |
| `zhipu` / GLM | Claude Code 走 Anthropic-compatible proxy | 依赖 endpoint 字段 | 当前包月/订阅通道无可见 per-token 价格时，按 token-only 观测，不计算成本 |
| `custom-proxy` | one-api / claude-code-router / internal gateway | 默认 unknown | 必须通过 cost reconciliation 或 micro benchmark 建立可信度 |

---

## 3. Unified Usage Fact

每次 agent run 的 `run.json` 应保留：

```json
{
  "usage": {
    "providerId": "deepseek",
    "model": "deepseek-chat",
    "runtimeAdapter": "acpx-claude",
    "rawUsagePath": ".../usage.raw.json",
    "measurementType": "PROVIDER_RUN_USAGE",
    "inputCacheMissTokens": 149166,
    "inputCacheHitTokens": 599168,
    "outputTokens": 23544,
    "cacheWriteTokens": 0,
    "contextWindowTokens": 771878,
    "clientReportedCost": {
      "amount": 1.789,
      "currency": "USD"
    },
    "officialCalculatedCost": {
      "amount": 0.5934,
      "currency": "CNY"
    },
    "clientCostDriftRatio": 0.0,
    "cacheSignalReliability": "provider_cache_tokens_reported|provider_usage_tokens_reported|pricing_unknown|not_applicable",
    "costEstimateReliability": "official_pricing|pricing_unknown|not_applicable"
  }
}
```

字段语义：

- `inputCacheHitTokens`：只在 provider raw usage 明确可映射时填。
- `cachedReadTokens` 可作为 legacy alias，但新 read model 应用 `inputCacheHitTokens`。
- `contextWindowTokens`：用于窗口风险，不等于成本。
- `officialCalculatedCost`：按 pricing registry 复算。
- `clientCostDriftRatio`：`abs(clientReported - officialCalculated) / max(clientReported, calculated)`。
- `cacheSignalReliability`：比 token 数更重要，用于策略是否允许信任 cache hit。

---

## 4. CachedRead Truth Verification

### 4.1 为什么必须验证

DeepSeek/Anthropic/OpenAI 都可能有 cache hit 语义，但 dual 当前看到的是 acpx stream 里的归一化字段。中间可能经过：

- Claude Code CLI；
- acpx adapter；
- Anthropic-compatible proxy；
- provider gateway；
- 自定义模型路由。

任一层都可能：

- 不透传真实 billing cache 字段；
- 把 session transcript replay 估算为 cachedRead；
- 只返回 cumulative snapshot；
- 返回 provider usage 但价格不是目标模型价格；
- costAmount 使用客户端默认价、平台折扣价或代理加价。

所以 `cachedReadTokens` 必须经过验证，不能靠名字判断。

### 4.2 三层验证机制

#### A. Raw Usage Provenance

每个 provider run 保存原始 usage 片段：

```text
runs/<runId>/usage/provider-usage.raw.json
```

并记录字段映射：

```json
{
  "inputCacheHitTokens": {
    "rawPath": "$.usage.cache_read_input_tokens",
    "mapping": "provider_native",
    "confidence": "high"
  }
}
```

如果字段来自 acpx 推断或 session snapshot，confidence 不能是 high。

#### B. Cost Reconciliation

用 pricing registry 复算 official calculated cost，并与 billing export 对账。client reported cost 只作为辅助诊断，不能决定 cache signal 是否可信：

```text
calculated =
  inputCacheHitTokens  * price.cacheHit
+ inputCacheMissTokens * price.cacheMiss
+ outputTokens         * price.output
+ cacheWriteTokens     * price.cacheWrite
```

billing export 判定：

| drift | 状态 | 含义 |
|---:|---|---|
| <= 5% | `verified` | usage/price 基本可信 |
| 5% ~ 15% | `warn` | 可能有四舍五入、折扣、代理加价 |
| > 15% | `contradicted` | 不能用该字段做自动策略 |
| provider 不报 cost | `unverified` | 只能展示计算成本，不能证明真实账单 |

client reported cost 判定：

| drift | 状态 | 含义 |
|---:|---|---|
| <= 15% | `matches_official_pricing` | 客户端估算与官方复算基本一致 |
| > 15% | `differs_from_official_pricing` | 客户端估算口径不同，不能当真实账单 |

#### C. Controlled Cache Probe

新增一个极小 benchmark：

1. 同 provider/model/session 发送同一 prompt 两次。
2. 第二次应出现 cache hit 增加或 cache miss 降低。
3. 如果 cachedRead 上升但 billing export / official calculation 不下降，则 cache 字段不可信或价格映射错误。

输出：

```text
dualctl cache-probe --provider deepseek --model deepseek-chat
```

记录到：

```text
docs/testing/provider-cache-probes/<timestamp>/result.json
```

---

## 5. Provider Cache Aware Compaction Policy

### 5.1 决策目标

不是“减少 cachedRead”，而是：

1. 降低 `calculatedCost` 或至少不升高。
2. 降低 context window 风险。
3. 不增加 toolCalls/readCount/reconnect。
4. 不降低 acceptance / repair 质量。

### 5.2 决策输入

每个 session segment 聚合：

```json
{
  "sessionSegmentKey": "ws:.../claude-main",
  "runs": 6,
  "inputCacheHitTokens": 807168,
  "inputCacheMissTokens": 75408,
  "outputTokens": 19686,
  "calculatedCost": 1.296,
  "costPerRunTrend": [0.25, 0.13, 0.12, 0.42, 0.20, 0.15],
  "contextWindowRisk": "low|medium|high",
  "cacheSignalReliability": "verified",
  "toolCallTrend": "...",
  "reconnectCount": 2
}
```

### 5.3 策略

| 条件 | 动作 | 理由 |
|---|---|---|
| cache hit 高、cache miss 低、cost 低、无窗口风险 | 不压缩 | cache 在工作，压缩会破坏便宜上下文 |
| cache hit 高、窗口接近上限、cost 仍低 | 可压缩但标记为 stability optimization | 目标是稳定性，不是省钱 |
| cache hit 高、cache miss/output/cost 同时上升 | 压缩候选 | 说明上下文雪球已转化为真实成本 |
| cacheSignalReliability != verified | 不自动压缩，只提示 | usage 不可信，不能自动决策 |
| 上次压缩后 cost 上升或 toolCalls 上升 | 回退策略 | 当前 compaction seed 不足以替代 session memory |

### 5.4 Compaction 不是单一开关

当前 `implement_boundary` 实验暴露：切 session 后 cache miss input 上升。Provider-cache-aware 策略应改为：

- 默认保留 workstream session。
- 只在 cost/window/reconnect 信号触发时创建新 segment。
- 新 segment 必须带更强的 `AgentHandoff` / `ReviewEvidenceBrief`，否则 agent 会重建上下文。
- compaction 后必须记录 `before/after` 成本结果，失败则自动标记该策略为 `ineffective_for_case`。

---

## 6. Projection / UI / CLI

### 6.1 Token 面板改成经济账

显示：

- cache hit input tokens
- cache miss input tokens
- output tokens
- context window tokens
- client reported cost
- official calculated cost
- billing observed cost
- client cost drift
- cache signal reliability

不要只显示 `totalTokens`。

### 6.2 Trace 输出

`dualctl trace` 每 run 增加：

```text
cost=reported:$1.789 calculated:$1.301 drift:+37.5%
cache=hit:599k miss:149k reliability:warn
decision=keep-session|compact|observe reason=...
```

### 6.3 Operator UI

用户问题应是：

- 这次贵在哪里？
- 贵的是 cache miss、output，还是只是便宜 cache hit？
- dual 的成本计算和 provider 报告是否一致？
- compaction 是为了省钱，还是为了降低窗口风险？

---

## 7. 实施计划

### Phase 0：设计落地前验证

- 保存 provider raw usage。
- 对最近 simple benchmark 重算 DeepSeek 成本。
- 做一次 controlled cache probe。
- 输出 `cacheSignalReliability`。

验收：

- 能解释为什么 80 万 cachedRead 仍然对应约 1 块多人民币。
- 能判断 cachedRead 是 provider native 还是 adapter/proxy 推断。

### Phase 1：Pricing Registry + Cost Reconciliation

- 新增 provider pricing registry。
- 支持 OpenAI / Anthropic / DeepSeek / GLM / custom-proxy。
- run metrics 增加 calculated cost / drift。

验收：

- `clientReportedCost` 与 `officialCalculatedCost` 差异可见。
- 未配置 provider/model 时，不允许自动 compaction，只能显示 `pricing_unknown`。

### Phase 2：Provider Cache Aware Read Model

- session-level cost aggregation。
- node hotspot 按 `effectiveCost` 排序，而非 total token。
- UI/trace 展示 cache reliability。

验收：

- 能区分“token 很大但便宜”和“token 不大但昂贵”。

### Phase 3：Compaction Policy

- 将当前 `DUAL_SESSION_COMPACTION_EXPERIMENT` 升级为 policy-driven。
- 只有满足经济账/窗口风险条件才压缩。
- 压缩后自动评估收益，不达标回退。

验收：

- simple case 不应再出现 `totalTokens` 下降但成本上升还被误判为成功。
- complex long-run case 目标：成本下降 5%~15%，或 context-window/reconnect 风险显著下降且成本不升。

---

## 8. 预期收益

基于当前 simple benchmark，不能承诺大幅省钱。合理预期：

| 场景 | token volume | 成本 | 主要收益 |
|---|---:|---:|---|
| simple happy path | 0%~15% 下降 | -5%~+5% | 防止错误压缩，解释成本 |
| multi-repair loop | 10%~25% 下降 | 5%~15% 下降 | 避免 cache miss/output 重复成本 |
| context-window pressure | 15%~35% 下降 | 不一定下降 | 降低窗口/reconnect/稳定性风险 |
| provider cache 不可信 | 不自动优化 | 不承诺 | 先修观测/映射 |

当前最确定的收益不是马上省钱，而是：**dual 不再被 total token 和 cachedRead 误导，能做正确的经济账。**

---

## 9. Boundary Review

**Boundary Review：PASS（设计层）**

Touched boundaries：
- External adapter / provider usage boundary
- Execution cost fact
- Pricing definition registry
- Metrics / trace / UI projection
- Session compaction policy

Source of truth：
- provider raw usage event
- normalized run usage
- provider pricing registry
- session compaction decision record

Projection surfaces：
- `workstream-metrics.json`
- `dualctl trace`
- operator console token/cost panel
- benchmark report

Required fixes：
- raw usage 落盘；
- provider/model pricing registry；
- cost reconciliation；
- cache truth probe；
- compaction policy 改为 cost/window/reliability 驱动。

Required tests：
- provider field mapping tests；
- cost calculation table tests；
- drift threshold tests；
- cache probe fixture tests；
- compaction decision policy tests；
- UI/trace projection tests。

Accepted debt：
- 价格配置需要人工更新或未来自动拉取；短期接受手动维护，但必须带 `updatedAt/sourceUrl`。
- GLM/custom proxy 的真实 cache billing 可能不可验证；短期标记 `unknown`，禁止自动 compaction。
