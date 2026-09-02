# E1 Sandbox Allowlist Differential Diagnostic

日期：2026-09-02  
性质：用户明确要求的 shell/provider 一次性诊断；不修改 production，不恢复 real-case。

## 结论

`PASS / BOUNDED CAPABILITY DIAGNOSTIC`

同一 Claude sandbox、同一 E1 policy binding、同一个 Bash tool call 和同一宿主 Shadowsocks/TUN 路径中：

| 请求 | policy | exit | 结果 |
|---|---|---:|---|
| `https://example.com/` | `allowed_domains=["example.com"]` | `0` | 成功 |
| `https://www.cloudflare.com/cdn-cgi/trace` | 未列入 allowlist | `22` | HTTP 403 |

这组差分证明：

1. Claude sandbox 内的 Bash 可以通过受控网络 transport 访问 allowlisted domain；
2. 先前全拒绝 policy 下的 CONNECT 403 不是 Shadowsocks 导致的普遍连接失败；
3. strict allowlist 对同一网络路径产生了可观察的 allow/deny 差分；
4. Shadowsocks/TUN 是两条请求共同的宿主下游，不影响这组差分归因。

## 执行边界

- production `ClaudeExecutionPolicy.write` 先生成 v2 policy；一次性 launcher 仅在 `/private/tmp` 将
  `allowed_domains` 改为 `["example.com"]`，重算 SHA 并继续走 production `AcpxAgentTransport`；
- 使用 Doctor 已验签的 E1 acpx fork；普通 PATH `acpx` 的首次尝试因不认识 binding flags 在 provider 前 fail-closed；
- 第二次尝试因一次性 Java classpath 缺 `dual-core` 在 prompt 前失败；两次均未产生 provider prompt；
- 有效样本恰一个 prompt、一个 Bash tool call，adapter exit 0，无 permission request，session 已关闭；
- probe 脚本自身 exit 0，两个 curl exit 单独写入 `results.tsv`，避免 denied request 阻断同 occurrence 的差分采集。

## 证明范围

本诊断证明 `sandbox connectivity + domain allowlist differential effect`，不证明：

- HTTP 403 的具体内部组件或稳定错误文本；
- TLS 内容检查、DNS owner、IP literal/redirect/subdomain 等完整绕过矩阵；
- Codex、容器或其他 adapter 的网络控制；
- Dual 的 `PUBLIC_NETWORK_DOMAIN` human-authority 功能已经实现。

原 Gate 仍保持 `INVALID_OBSERVATION_SIGNATURE`：它冻结的错误字符串判据与 evidence hygiene 没有被本诊断追溯改写。
本结果是新的独立能力证据，说明下一版 Gate 应采用 allow/deny 差分，而不是依赖 CONNECT stderr 文案。
