# Agent Result Prompt Projection Adequacy Assessment

日期：2026-08-17  
状态：`INDEPENDENT REVIEW PASS / CLOSED / NO IMPLEMENTATION`  
目标阶段：`P0 agent-result contract reliability`  
性质：停止性架构判断；不包含 production 实施授权

## 1. 结论先行

原计划中的 prompt-projection checkpoint 不应实施。

计划拟做的两个核心动作在 captcha-auth v15 运行前已经存在：

1. `AgentResultPromptContract` 使用 Jackson `writerWithDefaultPrettyPrinter()` 输出多行、缩进、完整闭合的 JSON 示例。
2. production prompt 已明确声明 `Every JSON object is closed`；v15 的 bounded correction 又明确给出
   `UNEXPECTED_END_OF_INPUT`、精确位置、`previous response ended before root JSON object was closed`，并要求重新输出完整 envelope、检查所有 object/array 闭合。

因此，“把单行 JSON 示例改为多行”没有真实代码 delta；再增加一句“最后一个非空白字符必须是 `}`”只会形成 soft-prompt 同义重复。它可以被字符串测试证明存在，却不能形成新的 enforced boundary，也没有证据表明能改变 provider 行为。

推荐决定：`NO IMPLEMENTATION / KEEP STRICT REJECTION`。

## 2. 已核实代码与 captured prompt

### 2.1 唯一 projection owner

- `dual-runtime/.../AgentResultPromptContract.java`
  - `OBJECT_MAPPER` 使用 `SNAKE_CASE`；
  - `render(...)` 构造 authoritative `AgentResultEnvelope` example；
  - `writerWithDefaultPrettyPrinter().writeValueAsString(envelope)` 生成多行示例；
  - 所有 Agent node 共用该 owner，没有 revision 专用 JSON renderer。
- `AgentResultPromptContractTest.everyAgentNodePromptExampleIsStrictAndMatchesItsNodeContract` 已遍历所有 Agent nodes，把示例重新交给 production parser 和 validator。
- `promptProjectsClosedStructuredItemFieldsWithoutAddingAnotherJsonObject` 已断言 closed-object 指令和 strict parse。

### 2.2 v15 实际输入

v15 revision prompt 的 Output Contract 实际包含：

- `Return exactly one JSON object as the final assistant message.`
- `Do not use Markdown fences ... or text before/after the JSON.`
- `Every JSON object is closed.`
- 从 `{` 到最终根 `}` 的多行 pretty-printed revision envelope。

唯一 bounded correction 同样包含这份多行完整示例，并额外携带 root EOF typed diagnostic。provider 第二次仍主动 `end_turn`，candidate 仍只缺最终根 `}`。

## 3. Boundary Analysis

### Existing model

- Definition：`AgentResultEnvelope` + `ContractRegistry` 声明 output shape 与 allowed decisions。
- Execution：ACP stream 终消息是 raw assistant fact。
- Artifact：strict parser/validator 接受后才能产生 typed sidecar/reference。
- Projection：`AgentResultPromptContract` 只把 authoritative schema 投影进 prompt，不是 grammar enforcement owner。
- Recovery：parse failure 可触发一次 bounded correction；第二次失败进入 `OUTPUT_INCOMPLETE / MANUAL_ONLY`。

### Source of truth and owners

- Schema/shape owner：`AgentResultEnvelope` 与 node contract。
- Prompt projection owner：`AgentResultPromptContract`。
- Grammar acceptance owner：`AgentResultEnvelopeParser`。
- Provider raw fact owner：ACP stream terminal assistant message。
- Terminal outcome owner：`AgentExecutionFinalizer` / `AgentExecutionOutcomeResolver` 链。

### Boundary conclusion

Prompt projection 当前已经忠实、完整地声明了 root closure。它只能指导 provider，不能在 generation 期间强制最后一个 token。增加近义提示不会新增 source of truth、validator、transition guard 或 side-effect barrier，因此成熟度只能是 prompt discipline，不能称为 `ENFORCED`。

## 4. Explicitly Rejected Changes

### 4.1 再增加一句末字符检查

拒绝在当前 checkpoint 添加：

`Before sending, verify that the last non-whitespace character is }.`

理由：

- 与 `Every JSON object is closed` 和 correction diagnostic 语义重复；
- 只能新增字符串断言，无法形成 cannot-bypass test；
- v15 correction 已提供更精确的 root-closure 信息仍然失败；
- 会把 soft prompt 变化包装成 reliability checkpoint。

### 4.2 修改 pretty-printer

拒绝。production 已经 pretty-print；不存在单行示例问题。

### 4.3 自动补根 `}`

暂不实施。独立评审确认架构上可设计，但当前原始证据只有 1 run / 2 occurrences，且存在可选字段边界处语义截断被接纳的 residual risk。

### 4.4 放宽 parser、增加 correction/retry

拒绝。会削弱 strict owner 或增加 provider 成本，且不能证明解决相同输出倾向。

### 4.5 新 schema/module/registry/engine

拒绝。当前没有缺失的 domain concept。

## 5. Verification Value

当前无需新增 production tests，因为没有 production behavior delta。现有 tests 已证明：

1. 所有 node 的 prompt example 是多行 strict JSON；
2. 示例可被 production parser 与 validator 接受；
3. closed nested records 的字段集合被投影；
4. malformed provider result 在 sidecar/reference/routing 前被拒绝；
5. bounded correction 至多一次。

新增一个只断言末字符提示存在的测试不会证明 provider 遵从，也不会证明一个新的 side-effect barrier，应明确拒绝把它计为 cannot-bypass evidence。

## 6. Revisit Trigger

只有满足以下任一条件，才重启 root-closure normalization 设计：

1. 在现有 pretty-printed prompt 与 typed EOF correction 保持不变的情况下，另一个独立自然 run 再次产生 exact root-only EOF morphology，并保留原始 wire/ledger 工件；或
2. 第三个历史样本由原始 wire/ledger 工件证明为 exact root-only morphology；或
3. ACP/adapter 新增可验证的 provider-native structured-output/constrained-decoding capability。

触发后允许重新评估此前评审过的 `parse(raw) -> typed gate -> append one } -> strict re-parse` 方案；仍不得直接实施通用 JSON repair。

## 7. End-to-End Invariant

| 维度 | 当前结论 |
|---|---|
| Declared by | `AgentResultEnvelope` / `ContractRegistry` |
| Proven by | pretty-printed production example 可被同一 parser/validator 接受 |
| Consumed by | provider 仅把 prompt 当软输入；返回结果由 strict parser 消费 |
| Enforced before | strict parser/validator 在 sidecar、reference 与 routing 前拒绝非法输出 |
| Failure route | typed parse failure -> 一次 correction -> `OUTPUT_INCOMPLETE / MANUAL_ONLY` |
| Cannot-bypass | parser/validator/finalizer tests；prompt 文案本身不构成 cannot-bypass |
| Residual path | provider 可忽略格式指令；这是 text-only ACP 边界的已知能力限制 |

## 8. Recommended Decision

- Independent Boundary Review：`PASS`；`NO IMPLEMENTATION` 成立。
- Implementation READY：`NO`，因为没有有效、非重复的 prompt delta。
- 当前成熟度：prompt projection `VALIDATED`；strict rejection/correction boundary `ENFORCED`；provider compliance `NOT PROVEN`。
- 下一步：该 prompt checkpoint 已关闭，不提交 production 代码；返回 captcha-auth 产品完成主目标，重新制定一个不把 provider compliance 当作启动前保证的 fresh Verification Gate。
