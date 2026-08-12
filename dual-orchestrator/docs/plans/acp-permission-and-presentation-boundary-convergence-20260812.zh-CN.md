# ACP 权限拒绝与 Assistant Presentation 边界收敛

日期：2026-08-12  
状态：INDEPENDENT REVIEW PASS / ENFORCED / NOT COMMITTED / NOT PROVEN

## 1. 本轮唯一目标

修复 captcha-auth v7 暴露的两个相互独立的问题，同时保持 strict contract、权限执行边界和 workflow 路由不变：

1. Claude Code 尝试调用未获批准的可选工具后，若该调用没有执行，但 Agent 随后仍交付完整且通过全部 required contract 的结果，`PERMISSION_BLOCKED` 不得覆盖已证明的节点成功。
2. ACP assistant message 为“可选自然语言 + 一个完整的 `json` fence + 可选自然语言”时，Dual 应确定性提取 fence 内候选，再进入原有 strict parser 和 validators，而不是把 presentation 偏差误判为结果缺失。

本轮推进主线稳定性 P0：移除真实 workflow 的错误失败路径。它不新增 phase、workflow、module、registry、engine、retry 或 parser fallback。

## 2. 已确认根因

### 2.1 权限拒绝与最终结果完整性被混为同一个事实

`--allowed-tools` 表达的是无需询问即可执行的工具集合，不是 Claude Code 的全部可见工具集合。用户安装的 MCP/tool 仍可被模型发现；不在自动批准范围内的调用会经过 ACP permission policy，并可被拒绝。

现有实现把任意 `PERMISSION_BLOCKED` 视为 blocking adapter signal。即使拒绝的工具没有执行、模型随后使用获准工具完成任务、AgentResult 与 required artifacts 全部有效，最终 outcome 仍被覆盖为失败。

### 2.2 presentation 与 contract grammar 被错误地绑定

ACP assistant message 是文本载体。现有 normalization 只接受“整条消息恰好是一个 `json` fence”，因此自然语言包裹同一个完整 fence 时，完整 JSON 没有进入 strict parser。

这不是 JSON grammar、schema 或业务 contract 放宽问题；缺失的是 presentation 层的确定性候选提取。

## 3. 设计 A：contained permission denial

### 3.1 事实与 owner

- 工具调用是否获准执行：ACP permission boundary 所有。
- AgentResult 是否存在且 strict-valid：`AgentResultEnvelopeParser` 与 contract validation 链所有。
- required artifact 是否全部满足：各 artifact contract validator 所有。
- 节点最终 execution outcome：`AgentExecutionOutcomeResolver` 唯一所有。

`AcpxPermissionProfile.autoApprovedTools` 只描述免确认集合；名称不再暗示工具可见性白名单。实际传给 acpx 的参数仍是既有 `--allowed-tools`，不改变已批准工具的执行行为。

### 3.2 唯一降级条件

`PERMISSION_BLOCKED` 只有同时满足以下条件时，才从 primary blocking signal 降为 secondary warning：

1. `ProcessUsability == EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT`；
2. AgentResult contract `present && valid`；
3. 所有 required artifact contracts 均 `satisfied`。

此时 outcome 为 `SUCCESS_WITH_WARNINGS`，`PERMISSION_BLOCKED` 原样保留在 `secondarySignals`，不得删除或改写。

任一条件不满足时仍 fail closed。`AUTH_FAILED`、`CONTEXT_WINDOW_EXCEEDED`、timeout、interruption、incomplete output 以及其他 blocking/recoverable adapter signals 不受该规则影响。

这里不根据 tool 名、stderr 或自然语言猜测“可选”。“被拒绝操作不影响本节点交付”的证据，只能来自已经通过的 typed result 与 required artifact contracts。

## 4. 设计 B：single fenced JSON extraction

### 4.1 presentation 边界

`AcpxAgentResultAcquirer` 继续作为 ACP 文本到 contract candidate 的 presentation owner。它允许：

~~~~text
optional prose
```json
one complete JSON candidate
```
optional prose
~~~~

提取条件：

- 恰好一个独占行的 ` ```json ` opening marker；
- 恰好一个独占行的 closing marker；
- payload 非空且不含嵌套 fence；
- 支持 LF/CRLF；
- 不拼接、不补括号、不扫描 brace substring、不选择多个候选。

纯 fence 保留既有 `SINGLE_JSON_FENCE_REMOVED`；含外围自然语言时记录新的 `SINGLE_FENCED_JSON_EXTRACTED`。raw message、raw hash、candidate 和 candidate hash 仍分别持久化。

### 4.2 strict 边界不变

提取后的 candidate 必须经过原有同一条：

```text
strict JSON parser
-> envelope/schema validation
-> semantic contract validation
-> required artifact validation
-> AgentExecutionOutcomeResolver
```

duplicate key、trailing token、unknown field、错误 schema、语义遗漏或 artifact 缺失仍然失败。多个 fence、未闭合 fence、嵌套 fence、空 payload 和非 `json` fence 不提取。

## 5. End-to-end invariant

| 维度 | 权限边界 | Presentation 边界 |
|---|---|---|
| Declared by | ACP typed `AdapterSignal(PERMISSION_BLOCKED)` + typed execution evidence | ACP raw assistant message + `PresentationDisposition` |
| Proven by | complete process usability、valid AgentResult、全部 required artifact contract | 唯一完整 line-delimited `json` fence |
| Consumed by | `AgentExecutionOutcomeResolver` | `AcpxAgentResultAcquirer` 后的原 strict parser/validators |
| Enforced before | `NodeResult` terminal status 与 workflow routing | artifact projection、terminal outcome 与 routing |
| Failure route | 任一完整性条件缺失时 denial 仍为 primary blocking signal | ambiguous/invalid candidate 保持 parse/schema/contract failure |
| Cannot-bypass test | valid evidence -> warning；required artifact missing -> blocked | prose+fence valid；prose+fence duplicate/trailing/unknown/schema invalid；multiple/unclosed/nested rejected |
| Residual raw path | signal 仍保留，不能静默丢失 | raw message 不直接路由，不能绕过 strict validation |

组合测试还证明真实 handler/finalizer 链上：exit 5 + `PERMISSION_BLOCKED` + prose-wrapped fenced valid contract 最终为 `COMPLETED/SUCCESS_WITH_WARNINGS`，不触发 correction；相同链路若 contract 不完整则不能成功。

## 6. 明确不做

- 不把用户安装工具加入硬编码 allowlist，也不建设 capability registry。
- 不全局忽略 acpx exit 5 或 `PERMISSION_BLOCKED`。
- 不从 stderr、工具名或模型说明文字推断权限是否重要。
- 不放宽 Jackson、schema、semantic 或 artifact validators。
- 不自动修 JSON、不引入第三次调用、节点 retry 或新 correction。
- 不把 deterministic PASS 声称为 captcha-auth real-case 已完成。

## 7. 成熟度与后续验证

当前实现完成后，focused tests 与 full reactor 通过可标记为 `ENFORCED`；在 clean SHA 上由新的 Verification Gate 完整运行 captcha-auth workflow，并由 typed ledger facts 证明无质量退化后，才可标记 `PROVEN`。

独立 review 必须直接阅读 production/test diff，并按 Declared by、Proven by、Consumed by、Enforced before、Failure route、Cannot-bypass test 核对；不得只根据本设计或实现报告给结论。
