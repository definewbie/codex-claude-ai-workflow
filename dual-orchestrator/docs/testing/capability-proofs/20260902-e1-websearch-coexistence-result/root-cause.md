# WebSearch Coexistence Root Cause

## 结论

根因由两个独立边界组成：

1. **Provider capability mismatch**：当前 ACP runtime 实际连接 `open.bigmodel.cn`，默认模型为 `glm-5.3`。
   Claude 原生 `WebSearch`/`WebFetch` tool call 均被该 provider backend 以
   `400 [1214][modelCode：不存在]` 拒绝。文本模型可用不代表 Anthropic server-tool backend 兼容。
2. **Tool visibility authority 未建模**：Dual 的 `--allowed-tools` 只表示“免确认工具”，不是工具可见性白名单。
   Claude Agent SDK 明确要求用 `tools` 限制可见工具、用 `disallowedTools` 移除工具；当前 Dual/acpx E1 binding
   未传递这两个字段。因此 `withAutoApprovedTool("WebSearch")` 只能表示批准后的免确认，不能独自证明
   “批准前不可调用 WebSearch / WebFetch 永远不可调用”。

## 证据链

- session record：`current_model_id=default`，实际描述为 `glm-5.3[1m]`；
- 本机 Claude settings：`ANTHROPIC_BASE_URL` host=`open.bigmodel.cn`，Sonnet/Opus 映射为 `glm-5.3`；
- stream：WebSearch 与 WebFetch 各一次，均返回同一个 1214/modelCode 错误，permission request 为零；
- SDK `sdk.d.ts`：`allowedTools` 是 auto-allow；限制可用工具应使用 `tools`，`disallowedTools` 会从模型上下文移除；
- claude-agent-acp：默认 `tools={type:"preset",preset:"claude_code"}`，只有
  `_meta.claudeCode.options.tools` 才会覆盖；WebSearch/WebFetch 在 ACP projection 中都呈现 `kind:"fetch"`；
- Dual：`AcpxPermissionProfile` 已正确注释 `--allowed-tools` 不是 visibility owner，但
  `approvedPermissionProfile()` 仍只用 `withAutoApprovedTool("WebSearch")` 表达批准后的状态；
- 智谱官方文档为 Coding Plan 提供 Search MCP / Reader MCP；没有声明 GLM Anthropic-compatible endpoint
  支持 Anthropic 原生 WebSearch/WebFetch server tool。

## 为什么此前没有发现

1. 设计与 review 已知道 `allowed-tools != visibility`，但 WebSearch human-authority checkpoint 只验证了
   “批准后追加 WebSearch auto-allow”，没有验证“批准前 WebSearch 从模型上下文消失”和“WebFetch 永远不可见”。
2. Gate 没有 provider capability preflight；把“文本模型可用”误当成“native WebSearch backend 可用”。
3. fixture 把 WebSearch 成功与 WebFetch permission deny 放在同一 provider sample；backend 先失败后，两条 authority
   路径都不可观察，直到本次真实 stream 才暴露。
4. 既有 deterministic tests 只钉 CLI 参数，没有消费 SDK `tools/disallowedTools` 的最终 effective options。

## 最小处理顺序

### 1. E1 网络边界独立收口

allow/deny Bash 差分已证明 Claude sandbox connectivity 与 strict domain allowlist 生效。E1 不再以 WebSearch 成功作为
sandbox network 的必要条件，避免把 provider server-tool 兼容性混入 OS/Bash egress owner。

### 2. WebSearch authority 独立建模

- pre-approval：WebSearch 与 WebFetch 必须进入 SDK `disallowedTools`（或不进入 explicit `tools`）；
- approved occurrence：fresh session 只解除 WebSearch，WebFetch 继续 disallowed；`allowedTools=WebSearch` 仅负责免确认；
- session/new effective options 必须持久化并由 test 断言；删除 visibility binding 后 test 必须失败；
- provider side effect 前无法证明 visibility 时 fail closed，不以 permission request 的缺失或出现作猜测。

### 3. Provider capability 分流

- Anthropic native provider：验证 native WebSearch backend 后才允许选择内建 `WebSearch`；
- 当前 GLM provider：native WebSearch 判 `UNSUPPORTED/INVALID`；若确有需求，另行评审智谱 Search MCP 的 exact-tool
  authority，不把 MCP、native server tool 与 Bash fetch 合并成一个抽象；
- 不通过自动批准 `fetch`、开放 WebFetch 或切换错误解析来绕过 backend incompatibility。

## 成熟度

| 能力 | 状态 |
|---|---|
| E1 policy transport | `ENFORCED` |
| Claude sandbox connectivity + Bash domain allowlist effect | `PROVEN`（bounded differential occurrence） |
| GLM native WebSearch backend | `UNSUPPORTED / INVALID`（当前配置） |
| WebSearch visibility authority | `NOT MODELED` |
| WebSearch human approval transport | ledger/approval `ENFORCED`，provider-side cannot-bypass `NOT PROVEN` |
| WebFetch exclusion | `NOT PROVEN` |

## 一手资料

- Anthropic WebSearch：provider-side/server tool：<https://docs.anthropic.com/zh-CN/docs/agents-and-tools/tool-use/web-search-tool>
- Anthropic Claude Code gateway：自定义 gateway 仍要求 provider/model 支持相应能力：
  <https://docs.anthropic.com/en/docs/claude-code/llm-gateway>
- 智谱 Claude Code 接入：<https://docs.bigmodel.cn/cn/coding-plan/tool/claude>
- 智谱联网搜索 MCP：<https://docs.bigmodel.cn/cn/coding-plan/mcp/search-mcp-server>
- 智谱网页读取 MCP：<https://docs.bigmodel.cn/cn/coding-plan/mcp/reader-mcp-server>
