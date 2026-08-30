# E1 Policy Transport Offline Replay

日期：2026-08-30

## 结论

离线重放完成。Q1 为 `PASS`；Q2 的 transport/env separation 为 `PASS`，真实 provider 连通性仍未验证；
Q3 的 effective option assembly 为 `VALIDATED`，OS sandbox behavior 为 `NOT PROVEN`。

因此 checkpoint 成熟度维持：

`E1 POLICY TRANSPORT ENFORCED / SANDBOX EFFECT NOT PROVEN`

下一次 Verification Gate 必须覆盖 Q3 的 behavioral effect 与 Q4-Q15。不得把本次离线结果解释为网络阻断、
WebSearch 共存、scratch 可用或 provider connection 已证明。

## Frozen Baseline

| 项 | 值 |
|---|---|
| Dual checkpoint | `e8cacb7f3256b80a30f06bdddffb0fb7eb2cfca0` |
| acpx upstream | `fd173f04aa1b56f9e3f5ca5190c034ddcae28792` |
| patch SHA-256 | `304577dc136c87b055b5873b0562929cd5c16b1b0d18321e27f851209b3ab811` |
| policy schema | `claude_execution_policy.v1` |
| adapter | `@agentclientprotocol/claude-agent-acp@0.70.0` |
| provider called | `false` |
| OKX workstream resumed | `false` |

## Q1-Q3

### Q1: named session 能否注入独立 settings

`PASS`。

- Dual 为 occurrence 原子写入固定 policy，并以 ref/SHA/workstream/node/run lineage 绑定；
- transport 在 ensure/new、prompt 与 correction 前重验同一 binding；
- fork 只接受 exact-key `claude_execution_policy.v1`，生成 `settingSources=[]`；
- 同时把同一 sandbox 写入 SDK `sandbox` 与 `managedSettings.sandbox`；
- 非 Claude agent、半绑定、SHA/path/lineage 漂移与 session binding 冲突均 fail closed；
- invalid binding 测试断言 acpx 调用日志不存在，证明 provider side effect 为 0。

这关闭了旧 proof 的“acpx 不转发 settings/sandbox”缺口。它不允许用户 project/local settings 成为 sandbox owner。

### Q2: provider env 与 tool subprocess env 能否分层

`PASS`（transport boundary）；真实 provider connection `NOT PROVEN`。

- wrapper 只从 Claude settings 导出固定 8 项 Anthropic-compatible provider 变量；
- proxy 与任意用户变量不透传；
- policy 本身不含 secret value；
- fork 把固定 11 项 credential/proxy 名映射为 sandbox credential deny；
- session `_meta` 不包含自由 `env` passthrough；
- shell test 证明 adapter command 能看到 allowlisted provider 值，而随机变量与 proxy 不可见。

上述证据证明两个输入通道已分离，但没有调用 provider，因此不能证明某个实际后端凭据与 base URL 可成功完成 turn。

### Q3: sandbox fail-closed options 是否在当前版本生效

`VALIDATED`（option consumption）；OS behavior `NOT PROVEN`。

- reviewed fork 真实构造并传入 `enabled=true`、`failIfUnavailable=true`、
  `allowUnsandboxedCommands=false`、`strictAllowlist=true` 与空 domain allowlist；
- fork tests 对 `session/new._meta.claudeCode.options` 的这些值做精确断言；
- installer 在 pinned source/patch 上复跑 `973/0/0/0` 并注入可复核 build identity；
- Doctor 对 official/missing/mismatch identity fail closed。

离线测试没有启动 Claude tool subprocess，因而不能证明 Seatbelt 已建立、不可用时确实先于网络 I/O 失败，
也不能证明 curl/npm/Maven/IP/DNS/loopback 被阻断。把这些行为称为 Q3 `PASS` 会把 option assembly 冒充
runtime effect，因此保留 `NOT PROVEN`。

## 验证结果

- Dual focused：`95/0/0`（45 handler + 3 policy + 36 transport + 11 Doctor）；
- adapter env shell test：`PASS`；
- installer upstream suite：`973/0/0/0`；
- build-info 与 manifest 的 upstream commit、patch SHA、policy schema 一致；
- 一次裸 `npm test` 尝试因未走 installer identity 注入且当前 sandbox 限制进程/队列测试，被判 `INVALID`；
  已终止全部进程并重新运行正式 installer，未将该尝试计入 PASS 证据。

## Stop-Loss

- 未调用 Claude/Codex provider；
- 未恢复 `ws-20260829T122452Z-9BZG`；
- 未创建新 real-case；
- 未把 Q1/Q2 transport 事实外推成 sandbox effect；
- Q3 behavioral effect 与 Q4-Q15 必须先写独立 Verification Gate。
