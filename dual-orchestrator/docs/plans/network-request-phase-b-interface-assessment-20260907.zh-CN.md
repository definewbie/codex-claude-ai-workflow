# Phase B：自动网络请求入口核实

状态：静态核实完成，生产自动捕获不实施。Phase A 独立 deterministic 复审 PASS 后进入。
目标仅回答：当前已启用的 strict allowlist 拒绝能否通过可信 typed 入口触发人工授权？
不把通用工具权限请求当成沙箱网络拒绝，不解析 Bash 正文，不弱化网络 policy。

## 本轮事实

- 冻结本地 ACP 0.70.0：`dist/acp-agent.js:4213` 的 requestPermissionFromClient 位于 canUseTool 路径，
  传递 toolUseID、toolInput 和 Allow/Deny 选择。该函数存在不能证明代理拒绝会调用它。
- 同一依赖的 SDK 0.3.232 `sdk.d.ts:6965` 明确说明 strictAllowlist=true 时，
  沙箱直接拒绝不在名单中的 host，而不是提示；这是现有 production policy 的固定值。
- `sdk.d.ts:4426` 的 permission-denial 事件针对工具权限判断，且标注 advisory，
  result.permission_denials 是对应权限记录。HTTP 代理在已获准 Bash 内返回 403 不等于工具被拒。
- HOOK_EVENTS、PermissionRequest、PostToolUseFailure 没有声明“沙箱拒绝域名”的专用 typed 事实。
  这是所核版本的公开类型面结论，不外推为所有版本都不存在内部能力。
- Phase A 仍可由操作者依据报告主动输入域名；模型建议是 advisory，不是自动 grant。

校验身份（2026-09-07 本机复算；不冒充 SPM9 当时加载版本）：

| 文件 | SHA-256 |
|---|---|
| ACP package.json | dc85a06d256602b6ad40429b69bc7920ee4297839136b0e722199e4c7daf8567 |
| ACP dist/acp-agent.js | f0cbbe408bb758cc4bacdae9a244bcac6efbdb6413f680195d017648abc6d816 |
| SDK sdk.d.ts | 65ec4f17eb95dc6fa85d964249d4d27e31a150e4bfbaf94c531090862b7a1fd7 |

本机核验根：`/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/`。

## 决策与停止条件

当前入口没有闭合，不能实现“自动拦截→可信域名请求→人工授权”并称 ENFORCED。
不开 provider 试探已经被契约明确禁止的提示路径，不将 Phase A 再阻塞于 Phase B。
不关闭 strictAllowlist、不新增 proxy、不用正则解析 curl/Bash 输出，不新增通用网络平台。
后续只有上游提供 typed 沙箱拒绝事件，或一个独立有界实验能证明原生请求通道且仍默认拒绝，才重开。

六维判定：Declared=当前 strict policy；Proven=版本化类型/adapter代码（静态）；
Consumed=现有 canUseTool 仅消费工具权限；Enforced before=无域名请求消费入口；
Failure route=Phase A 操作者发起；Cannot-bypass=未具备自动捕获验证，不宣称闭环。

本轮无 provider、无 SPM9 恢复、无 Phase B production 变更。

独立 agent 复核：停止 B 生产实现的判断 PASS；未发现冻结公开类型面遗漏的可信沙箱域名入口。
限定：不提示不等于没有任何内部遥测，未来公开接口变化可以重开；本轮不以内部猜测开工。
