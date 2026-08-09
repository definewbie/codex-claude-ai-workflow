# Verification Gate: Captcha Revision Verbatim Preservation Probe v2

日期：2026-08-09

状态：`FAIL / CONSUMED`。

- Target: 使用 clean SHA `bc08027` 的 production `AgentResultPromptContract` 重新渲染
  captured captcha-auth revision prompt，证明一个 fresh Claude session 能产生逐字保留未声明
  clauses 的最终 `TaskContractProposal`。允许因 strict JSON parse failure 在同一 session 执行
  最多一次既有 bounded syntax correction；不判断、不比较模型名称。

- Source of truth: generated prompt path/hash、ACP process results、session stream、
  `AcpxAgentResultAcquirer` 的 typed correction reason/disposition、
  `AgentResultEnvelopeParser`、`AgentResultEnvelopeValidator`、
  `TaskContractRevisionContextResolver`、`TaskContractProposalValidator`，以及原 workstream 的
  verified previous proposal 和 CO-1/CO-2 obligations。

- Trigger precondition: implementation baseline 必须包含 `bc08027` 且 worktree clean；generated
  prompt 必须由 production `AgentResultPromptContract.render` 替换 captured prompt 的唯一
  `Output Contract` 区段，路径为
  `/private/tmp/captcha-revision-probe-v2-bc08027.prompt.md`，SHA-256 必须为
  `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`；prompt 必须只含一个
  Output Contract、包含 verified baseline marker 和 character-for-character instruction；原
  `ws-20260807T150938Z-2D5Y` 保持只读；使用独立 fresh session。

- How precondition is checked before run: 记录 clean Dual SHA、Gate SHA、prompt absolute
  path/hash/bytes、production-render checks、fresh session name 和开始时间。执行入口直接使用
  production `AcpxAgentResultAcquirer`、`AcpxAgentTransport`、`contractDiscovery` initial
  permission 和 `contractCorrection` correction permission；不得手工拼 correction prompt，不读取
  provider model metadata。

- PASS: initial occurrence exit 0、completed 且无 active blocking adapter signal；correction
  disposition 只能为 `NOT_NEEDED`，或在 initial `PARSE_FAILURE` 时为 `ATTEMPTED`；最多两个
  occurrence，且 correction occurrence exit 0、completed、无 tool/permission request。最终
  candidate 必须无 prose/fence/trailing token，通过 production strict parser、envelope
  identity/schema、proposal mechanical validation、真实 CO-1/CO-2 coverage 和 revision delta；
  `AC-12`、`TR-16` 等未声明 clauses 必须与 verified baseline 逐字一致。记录所有 occurrence
  的调用、token 和耗时，不因 correction 剔除成本。

- FAIL: 非零 process、timeout、tool/permission request、active blocking signal、第二次之后仍
  parse/schema invalid、correction reason 为 `SCHEMA_FAILURE`、任何 mechanical/coverage/delta
  error、未声明 clause 改写/新增/删除，或出现第三次 occurrence。FAIL 阻止 fresh captcha-auth
  full workstream。

- INVALID: SHA/路径/hash 不符、prompt 不是 production renderer 生成、worktree dirty、旧
  workstream 被修改、session 非 fresh、真实 baseline/obligations 无法解析、transcript/usage
  缺失或执行未走 production acquirer，导致目标不可判定。

- Stop-loss: 只允许一次 initial occurrence；只有 production acquirer 判定
  `PARSE_FAILURE + ATTEMPTED` 才允许同 session 一次 correction。任何 process/tool/permission
  failure 立即停止；不 retry、不 resume、不启动第三次 prompt、不修改 parser/validator/prompt，
  不启动 full workflow。

- Run artifact freshness: Gate 先作为独立 checkpoint 提交；运行报告记录 implementation SHA
  `bc08027`、Gate SHA、prompt hash、session/stream identity、每个 occurrence 的 process/parse/
  correction/usage facts、最终四类 validator verdict。Probe PASS 后才允许生成 fresh package 并
  启动一个新的 captcha-auth full workstream；Probe 本身不等于 terminal-delivery PROVEN。

## Preflight Evidence

- generated prompt bytes：`34870`。
- generated prompt SHA-256：
  `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`。
- Output Contract marker count：`1`。
- verified baseline marker：present。
- character-for-character instruction：present。
- production implementation checkpoint：`bc08027`。

## Result

- Gate checkpoint：`c145c00`；worktree clean；generated prompt path/hash/bytes 与 preflight
  完全一致。
- fresh session：`dual-captcha-revision-v2-c145c00`；ACP stream：
  `f8996e0e-7770-475f-a142-2c8909bb01c6.stream.ndjson`；恰好两个
  `session/prompt` request（initial `id=3`、correction `id=4`），无第三次 occurrence。
- initial：exit `0`、`end_turn`、108,222 ms、`PARSE_FAILURE`；usage 为 input 10,051、
  output 9,859、total 19,910 tokens。
- production `AcpxAgentResultAcquirer` 按 Gate 在同一 session 执行唯一 bounded syntax
  correction：disposition=`ATTEMPTED`。correction exit `0`、`end_turn`、51,194 ms；usage 为
  input 5,416、cached read 9,984、output 5,643、total 21,043 tokens。
- 两次 occurrence 合计 40,953 total tokens、159,416 ms。stream/transcript 未出现
  `session/request_permission` 或 tool call；没有 process、permission 或 transport failure。
- correction 后唯一 final candidate 为 17,252 bytes，SHA-256
  `1409b9450bc3269103b79cae1b381da30f5299824d12c97417e49e8521c1a58d`，仍以
  Markdown `json` fence 开始并以 fence 结束。production strict parser 在 line 1 column 1
  返回 `AGENT_RESULT_PARSE_FAILED / INVALID_JSON`；final envelope validation 因而未成立。
- 只读归因再次精确移除单一首尾 fence 后，candidate 通过 strict JSON、envelope、mechanical、
  verified CO-1/CO-2 coverage 和 revision delta，且 `AC-12`、`TR-16` 不再漂移。这证明
  `bc08027` 的字符级保真 prompt closure 对本样本有效，但该诊断不得进入 production
  acceptance，也不能把本 Gate 改判 PASS。
- 最终结论：`FAIL / SAMPLE CONSUMED`。session 已关闭且无残留 provider 进程；没有启动
  fresh captcha-auth full workstream。

## Post-run Official Protocol Clarification

2026-08-09 对 ACP v1、acpx 与 Claude ACP adapter 的官方文档和公开源码完成只读核对。
本节只修正失败归因，不追溯修改本 Gate 的 `FAIL / CONSUMED` 历史判定。

### Confirmed protocol boundary

- ACP v1 将模型输出作为 `session/update` 的 `agent_message_chunk` 发送，正文是
  `content.type=text` 的任意字符串；`session/prompt` 的终态响应只声明 `stopReason`，不携带
  业务 output schema。官方依据：
  [ACP Prompt Turn](https://agentclientprotocol.com/protocol/v1/prompt-turn) 和
  [ACP Content](https://agentclientprotocol.com/protocol/v1/content)。
- acpx `--format json` 输出 raw ACP NDJSON；`--json-strict` 只抑制非 JSON stderr；`quiet`
  模式输出拼接后的 assistant text。它们都不约束 `content.text` 必须是 bare JSON。官方依据：
  [acpx Output Formats](https://github.com/openclaw/acpx/blob/main/docs/output-formats.md)。
- 因此完整 Markdown `json` fence 是 ACP/acpx 合法的 assistant text presentation。它不满足
  Dual 当前 bare `AgentResultEnvelope` 应用合同，但不能再归因为 ACP/provider 协议违规。

### Published extension scan

只读源码扫描固定在：

- `openclaw/acpx@5ef9b5849e137310a1c6f6e06d82ca606c2d8fb3`；
- `agentclientprotocol/claude-agent-acp@1afa940a2c8c0f4c610f4f64d30c0961642907b0`。

扫描结果：

- acpx 的 `structured output` 公开措辞指 `text/json/quiet` CLI 输出模式；源码仍把
  `agent_message_chunk.content.text` 作为 assistant output。其 flow 架构文档把 ACP node 的
  dedicated output schema 明确列为未来可能能力，而非当前能力。
- Claude ACP adapter 允许在 session `_meta.claudeCode.options` 中透传 Claude Agent SDK
  `Options`，并识别 SDK 的 `error_max_structured_output_retries`；但公开 README/ACP capability
  没有声明结构化最终结果扩展，production code 也没有把 SDK `structured_output` 投影为可供
  ACP client 消费的 terminal result。`ToolUpdateMeta.toolResponse` 只属于 tool-call output，
  不能作为 Agent 最终结果通道。
- Claude ACP adapter 另有 opt-in `_meta.claudeCode.emitRawSDKMessages`，可通过
  `_claude/sdkMessage` vendor extension 暴露未经抽象的 SDK message。它不是 initialize 时协商的
  structured-output capability，也没有为 terminal result 提供 provider-neutral schema、唯一
  owner 或稳定消费合同；本 checkpoint 明确不把该调试/扩展通道升级为 Dual workflow authority。
- 所以当前可依赖的公共集成合同仍是 assistant text。不能把底层 SDK 可能拥有的 structured
  output 能力推断为 acpx/ACP 已公开并端到端暴露的能力。

### Bounded closure decision

下一步只允许设计一个 adapter/acquisition-boundary presentation normalization checkpoint，
不得立即修改 production：

1. 原始 assistant text 必须继续持久化，ACP transport fact 不被改写；
2. 只有整条消息精确由一个 `json` fenced block 构成时，才可产生 normalization candidate；
3. fence 外 prose、多个 fence、非 `json` fence、不完整 fence 一律不 normalize；
4. candidate 必须重新进入现有 strict parser、schema、coverage 和 revision-delta validators；
5. 禁止 substring 搜索、brace matching、自动补括号/字段、宽松 parser、第三次 correction 或
   provider-specific fallback；
6. normalization 只能处理 presentation，不能改变任何 JSON 字节、业务 clause 或 routing fact。

顺序固定为：每个 initial/correction occurrence 都先持久化 raw assistant text，再执行同一个
presentation check，然后把 bare/normalized candidate 交给同一个 strict parser。只有该 parser
失败时，既有 policy 才能允许同 session 最多一次 bounded correction；correction occurrence 不得
使用第二套 extraction 或 parser。最小审计 disposition 为 `NOT_NEEDED`、
`SINGLE_JSON_FENCE_REMOVED` 或 `REJECTED_AMBIGUOUS_PRESENTATION`，并记录 raw/candidate hash。

该能力已按上述边界实现，当前成熟度为
`IMPLEMENTED / DETERMINISTIC PASS / ENFORCED`：

- initial/correction occurrence 共用 `AcpxAgentResultAcquirer` 的 exact single-json-fence pipeline；
- normalized candidate 仍进入未修改的 strict parser 与既有下游 validators；
- raw ACP presentation 保留在 transcript，occurrence schema v2 记录 disposition 与 raw/candidate
  SHA-256；
- cannot-bypass matrix 与完整 Maven reactor 通过（`1199 tests / 0 failures / 0 errors`）。

独立代码级 diff review 已 `PASS`。在新的受控真实 workflow 通过前不得标记为 `PROVEN`，也不得
把 presentation normalization 描述成模型输出质量已被修复。
