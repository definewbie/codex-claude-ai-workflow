# E1 Sandbox Effect After Transport Owner — Capability Result

日期：2026-09-02  
Gate checkpoint：`3831b6d4`  
结论：`INVALID_OBSERVATION_SIGNATURE`

## 1. Stop-Loss

Bash occurrence 完成后，三个 inherited-proxy probe 只产生普通 CONNECT 403：

```text
proxy_curl   56   curl: (56) CONNECT tunnel failed, response 403
proxy_child  56   curl: (56) CONNECT tunnel failed, response 403
proxy_git   128   CONNECT tunnel failed, response 403
```

对应 stderr、tool result 与 ACP stream 均未出现 Gate 冻结的三类 domain-policy 信号：

- `Connection blocked by network allowlist`
- `[sandbox] Blocked network request to `
- `network path not allowed: `

Gate 明确禁止以普通 403 解释 domain-policy authority，因此 Bash 不满足 PASS，判
`INVALID_OBSERVATION_SIGNATURE`。按 stop-loss 未创建 WebSearch session，未运行第二样本。

## 2. Mechanically Observed Facts

- fresh package 与 Doctor PASS；source=`3831b6d4`；acpx identity=
  `fd173f04 / fc2783a8b5aa2a450007b9e5516ca473d9a5e5785d8760b342e230ea180c60cc / claude_execution_policy.v2`；
- adapter=`0.70.0`、SDK=`0.3.232`、embedded Claude Code=`2.1.232`，binary SHA=
  `7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7`；
- fixture `5/5` SHA、净化环境 offline test、bash syntax、frozen launcher compile 全部 PASS；
- minimal health：一个 prompt、零工具、最终 `OK`，随后 closed；
- Bash：一个 prompt、一个 logical Bash call，command 精确为 `./e1-sandbox-contract-probe.sh`，无 retry/correction，
  permission request 为零，session closed；
- policy v2 owner=`claude_sandbox`，三个 provider credential deny、空 allowed domains、strict allowlist、
  `failIfUnavailable=true`、`allowUnsandboxedCommands=false` 均进入 `session/new._meta`；
- workspace 与 system tempdir 写读 PASS；三个 Anthropic credential absent；八个 proxy 名仅记录 present；无 proxy 值落盘；
- direct external/loopback/LAN 均 exit 97，stderr 均为 `PermissionError: [Errno 1] Operation not permitted`；
- 宿主 loopback/LAN/external HTTP 在前后均成功，宿主 raw TCP 1.1.1.1:443 在前后观察均成功；这些只证明
  目标经当时宿主网络路径可达，不证明宿主绕过代理直连；
- WebSearch prompt count=`0`，符合 Bash 未 PASS 时不得继续的 stop-loss。

### 2.1 Host Network Topology Qualification

Operator 确认本次运行期间宿主开启 Shadowsocks VPN。运行后只读核对进一步显示：macOS 显式 system proxy 字典为空，
但 IPv4 外部路由由 `utun4` 与 `198.18.0/15` 路径接管，符合 TUN/透明路由形态。原 Gate 中的 `host direct`
因此命名不准确，应解释为 `host-path reachability control`：它证明目标不是全局不可达，却不能证明 host traffic 未经代理。

这不会推翻 sandbox 内 raw socket 的 `EPERM` 观察：该错误发生在进程 socket connect 边界，早于宿主后续 TUN/代理路由。
但它会进一步扩大 inherited-proxy `CONNECT 403` 的 owner 歧义：403 可能来自 Claude sandbox proxy、宿主代理链或其上游，
现有工件无法机械定位产生层。事后采集的 `posthoc-scutil-proxy.txt` 与 `posthoc-ipv4-routes.txt` 仅作环境说明，
不得冒充运行前冻结证据。

## 3. Evidence Hygiene Finding

宿主 raw TCP 的 before 结果在执行时显示 `Connection ... succeeded`，但没有同步写入 durable preflight 文件；after 结果已保存。
sentinel 的四次 HTTP 200 有对应 before/after response 文件，server console 原始日志只存在于执行会话输出，未进入本 evidence
目录。即使 domain-policy 信号问题不存在，这也会使 Gate 因 precheck 原始证据不完整而 INVALID。不得事后手工重建为原始证据。

## 4. Classification

| 能力 | 本轮判定 |
|---|---|
| Network transport ownership | `ENFORCED`（沿用 checkpoint 结论） |
| Provider credential absence in sandboxed Bash | 本 occurrence `OBSERVED PASS` |
| Workspace/tmp filesystem compatibility | 本 occurrence `OBSERVED PASS` |
| OS no-direct-connect | sandbox 内三目标均在 socket connect 返回 `EPERM`，支持本 occurrence `OBSERVED PASS`；host 对照仅证明经 Shadowsocks/TUN 路径可达 |
| Domain-policy denial | `NOT PROVEN`：CONNECT 403 无冻结归因信号 |
| WebSearch coexistence | `NOT RUN / NOT PROVEN` |
| Overall Gate | `INVALID_OBSERVATION_SIGNATURE` |

## 5. Next Boundary

不得修改 fixture 后复跑，也不得用当前 403 推断安全 owner。下一步只能做只读归因：先把宿主 Shadowsocks/TUN、Claude
sandbox proxy 与上游网络区分为三个 transport layer，再核实当前 embedded runtime 对 HTTPS
CONNECT deny 的公开/实现级可观测契约，比较以下最小选择：

1. 是否存在无需第二 provider sample 即可机械取得的 proxy-side structured denial evidence；
2. 是否应由 launcher/runtime 保存 sandbox policy decision，而不是让 curl stderr承担 authority；
3. 若 runtime 只承诺 deny、没有稳定归因信号，Gate 应如何以 host control + OS/direct evidence + immutable policy binding 建立
   不依赖自然语言/错误字符串的验证边界。

归因与新 Gate 均需独立评审；当前不授权第二样本、OKX resume、real-case、domain approval 或 custom proxy。
