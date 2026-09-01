# E1 Claude Sandbox Effect Capability Rerun Result

日期：2026-08-31  
Source checkpoint：`49495dbec001dd4e804f9d89a070b8db2be921a4`  
受控变化：`claude-agent-acp 0.46.0 -> 0.70.0`，内嵌 Claude Code `2.1.178 -> 2.1.232`  
冻结 Gate 程序结论：`FAIL / STOP-LOSS EXECUTED`  
能力语义结论：`INVALID_OBSERVATION_MODEL`（proxy/network 未被有效测量）

## 决定性结果

升级后的真实 Bash tool subprocess 已看不到：

- `ANTHROPIC_API_KEY`
- `ANTHROPIC_AUTH_TOKEN`
- `ANTHROPIC_BASE_URL`

这证明 Claude Code `2.1.232` 已真实消费 `sandbox.credentials.envVars`，provider process 与 Bash tool process
之间的 credential stripping 对这三个变量生效。普通 provider health turn 同时成功，说明 provider connection 未因清洗退化。

但 Bash subprocess 仍存在以下八个变量名：

- `HTTP_PROXY` / `HTTPS_PROXY` / `ALL_PROXY` / `NO_PROXY`
- `http_proxy` / `https_proxy` / `all_proxy` / `no_proxy`

冻结 Gate 要求 11 个变量全部 absent，因此脚本在首个网络命令之前以
`exit 73 / E1_TOOL_ENV_LEAK` fail closed。网络矩阵、WebSearch 与 session-conflict 均未执行。

## 边界解释

本轮不能把代理变量的存在直接解释为宿主 proxy 泄漏。Claude Code `2.1.232` 的冻结 runtime binary 明确包含 sandbox
启动时注入 `HTTP_PROXY=http://...`、`HTTPS_PROXY=http://...`、大小写同名变量、`NO_PROXY` 与 Git/JVM 等代理配置的逻辑；
这些变量可能正是 network sandbox 的 enforcement transport。当前 fixture 只记录 presence，不记录或分类值，因而不能区分：

1. 不应进入 tool process 的宿主/provider proxy；
2. 由 Claude sandbox 注入、指向本地 policy-enforcing proxy 的受控变量。

因此 frozen Gate 按预先承诺的规则仍记录 `FAIL`；但该规则后来被证明不能区分受控 proxy 与泄漏，不能据此判定 sandbox
能力失败。面向 capability 的结论必须是 `INVALID_OBSERVATION_MODEL`，等待修订后的行为探针；这不是部分 PASS，也不提升完整 E1
成熟度。

## 执行事实

- clean detached source worktree：`49495dbe`；fixture 四个 SHA 与 Gate 一致；fresh adapter package 与 launcher compile PASS；
- runtime pin 来源：归档的 `runtime/acpx-config.json` 在 clean source 上显式调用 wrapper 并传入
  `npx -y @agentclientprotocol/claude-agent-acp@0.70.0`；不依赖 `49495dbe` 内默认的 `0.46.0`；
- runtime：`claude-agent-acp 0.70.0`、`claude-agent-sdk 0.3.232`、embedded Claude Code `2.1.232`；
- fresh minimal health：一个 prompt、零工具、回答 `OK`、session closed；
- Bash：一个 prompt、一个 logical tool call，input 精确为 `./e1-network-probe.sh`；filesystem 与 system tmpdir 写读 PASS；
- Bash tool result：`exit 73 / E1_TOOL_ENV_LEAK`；session closed；sentinel host loopback/LAN precheck PASS 后已停止；
- 未运行 WebSearch、第二个 Bash、retry、OKX resume 或新的 real-case。

## Maturity

- E1 policy transport：`ENFORCED`；
- Bash tool 中三个 Anthropic 变量不可见：`CAPTURED FACT`；具体由 credentials deny 还是新版默认 scrub 产生，未做消融对照；
- proxy provenance/isolation：`NOT VALIDATED`；
- network deny effect、structured denial evidence、WebSearch coexistence：`NOT PROVEN`；
- `PUBLIC_NETWORK_DOMAIN`：仍禁止实施。

下一步只允许修订 capability observation：在不保存 proxy value 的前提下记录 scheme、host class、是否 loopback、上下游值摘要是否一致，
并以真实 network denial 作为 authority。不得删除 Claude sandbox 的受控 proxy、不得用变量名 absence 冒充安全证明，也不得恢复 OKX。
