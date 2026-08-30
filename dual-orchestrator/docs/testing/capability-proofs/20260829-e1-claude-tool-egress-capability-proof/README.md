# E1 Claude Tool Egress Capability Proof

## 结论

`FAIL`。截至 2026-08-29，当前 frozen execution stack 无法把 Dual 生成的 occurrence-bound sandbox policy 注入 Claude session；因此没有启动 provider tool turn，也没有修改 production。

这不是 Claude Code 缺少 sandbox。缺口位于 ACP client 边界：

- `claude-agent-acp@0.46.0` 可从 `session/new._meta.claudeCode.options` 接收 `settings`、`managedSettings`、`sandbox` 与 `settingSources`；
- Claude Agent SDK `0.3.178` 也声明这些 options；
- `acpx@0.11.0` 只把 `model`、`allowedTools`、`maxTurns`、`systemPrompt` 转入该 `_meta`，没有 sandbox/settings 的 CLI、config 或 session-option 入口；
- `acpx` 默认排除 user settings，但仍加载 project/local settings，所以现状不是 Dual 独占 policy；
- Dual 的 adapter wrapper 会把用户 `settings.json.env` 的全部变量 export 给 adapter，尚未分开 provider env 与 tool subprocess env。

按照 E1 Gate 的 fail-closed 条款，问题 1 与 2 已失败，其余需要真实 session 的问题不得继续执行。

## Frozen Stack

| 项目 | 冻结值 |
|---|---|
| Dual HEAD | `f1c4e9255d2f59db1091a0a3b5c86fa98e2c0c44` |
| macOS | `15.6.1 (24G90)`, arm64 |
| acpx | `0.11.0` |
| claude-agent-acp | `0.46.0` |
| Claude Agent SDK | `0.3.178` |
| Claude Code | `2.1.178` |
| 取证日期 | `2026-08-29` |

官方能力依据：

- <https://code.claude.com/docs/en/sandboxing>
- <https://code.claude.com/docs/en/settings>
- <https://code.claude.com/docs/en/permissions>

官方文档确认 macOS sandbox 使用 Seatbelt，并覆盖 Bash 及其子进程；`failIfUnavailable=true` 可把不可用变成硬失败，`allowUnsandboxedCommands=false` 可关闭 unsandboxed retry，worktree 与 session `$TMPDIR` 默认可写。文档还说明 settings source 中的 filesystem 数组会合并，因此仅增加一份普通 project/user settings 不能证明 restrictive policy 独占。

## 逐问题判定

| # | 判定 | 证据与结论 |
|---|---|---|
| 1 | **FAIL** | 底层 adapter/SDK 支持 options；frozen `acpx` 不转发 sandbox/settings/managedSettings，且仍加载 project/local sources。Dual 无法为 named session 注入独占 policy。 |
| 2 | **FAIL** | wrapper 从用户 settings 读取并 export 全部 `env`。凭据不写 ledger，但 provider env 与 tool subprocess ambient env 尚未隔离。 |
| 3 | BLOCKED | SDK/Claude Code 静态上支持 sandbox、fail-if-unavailable 和关闭 escape；因问题 1 无可信注入入口，不能证明当前 Dual execution 生效。 |
| 4 | BLOCKED | 未启动真实 tool turn；不能用独立 Claude CLI 或文档替代 Dual execution proof。 |
| 5 | BLOCKED | 同上；loopback、LAN 与 sandbox 内部 proxy/socket 仍需有效 policy 下的真实验证。 |
| 6 | BLOCKED | 已证明 ambient env 会进入 adapter；是否能在不破坏 provider 的情况下从 tool subprocess 移除，依赖新的注入边界。 |
| 7 | BLOCKED | 官方文档支持 worktree + session temp；当前 Dual policy 未生效，不能声明 execution 行为。 |
| 8 | BLOCKED | 官方文档将 WebSearch/WebFetch 视为 in-process permission tools，但仍需 Dual-generated sandbox 下的实际共存 Gate。 |
| 9 | BLOCKED | 没有启动 network-denial occurrence，故没有可关联的 structured domain evidence。 |
| 10 | BLOCKED | 没有可注入的 per-execution policy，无法证明一次性 grant。 |
| 11 | **PASS（静态）** | frozen `acpx` 只在 `session/new` 构造 `_meta`；已有 session 不会重新应用 session options。policy 改变必须 close/rebuild。 |
| 12 | BLOCKED | 注入入口缺失，无法证明 sandbox failure 在 network I/O 前形成 typed failure。 |
| 13 | **FAIL（现状）** | `AcpxRunRequest` 仍有 `approveAll` fallback，且当前没有 `NETWORK_AUTHORITY_NOT_CONFIGURED` enforcement。它是实施要求，尚非现有能力。 |
| 14 | NOT MODELED | `PUBLIC_WEBSEARCH` 已是 manual-only；新的 network permission wait 尚不存在，不能外推。 |
| 15 | NOT MODELED | 本 proof 只覆盖 Claude stack；Codex adapter 未证明等价 enforcement。 |

## 关键命令与输出摘要

```text
acpx --version
=> 0.11.0

<bundled claude> --version
=> 2.1.178 (Claude Code)

sw_vers; uname -m
=> macOS 15.6.1 (24G90), arm64

npm view acpx version dist-tags --json
=> latest 0.13.2

npm view @agentclientprotocol/claude-agent-acp version dist-tags --json
=> latest 0.70.0
```

源码核验：

```text
acpx 0.11.0 buildClaudeCodeOptionsMeta()
  settingSources = ["project", "local"]
  assignClaudeCodeOptions() 仅处理 model/allowedTools/maxTurns

claude-agent-acp 0.46.0
  userProvidedOptions = sessionMeta?.claudeCode?.options
  options = { settingSources, ...userProvidedOptions, env, ... }

Dual scripts/acpx-claude-agent-env.sh
  读取 settings.json.env 后逐项 export，再 exec adapter
```

## 新版只读比较

从 npm 官方注册表下载到临时目录的 `acpx@0.13.2` 仍只增加了 `env` session option；`buildClaudeCodeOptionsMeta()` 仍未转发 sandbox/settings/managedSettings。`claude-agent-acp@0.70.0` 仍可消费完整 `userProvidedOptions`，但仍依赖 ACP client 发送。

因此，“只升级 frozen package”不足以关闭问题 1。下一步必须先比较 client 扩展、受控 shim 与等待/推动上游能力，不能直接开始 E1 production checkpoint。

## Stop-Loss

- 未调用 Claude/Codex provider；
- 未恢复 `ws-20260829T122452Z-9BZG`；
- 未修改 production；
- 不把底层 SDK `MODELED` 能力描述为 Dual `ENFORCED`；
- 下一步仅允许 bounded enablement comparison 与独立架构评审。

