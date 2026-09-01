# E1 Claude Sandbox Effect Capability Result

日期：2026-08-31  
Gate checkpoint：`49495dbec001dd4e804f9d89a070b8db2be921a4`  
结论：`FAIL / STOP-LOSS EXECUTED`

## 决定性结论

E1 policy transport 已把 production `claude_execution_policy.v1` 注入 fork，policy 中也明确声明 11 个
`subprocess_denied_env_names`。但真实 Claude Bash tool subprocess 仍看到了其中 10 个：

- `ANTHROPIC_AUTH_TOKEN`
- `ANTHROPIC_BASE_URL`
- `HTTP_PROXY` / `HTTPS_PROXY` / `ALL_PROXY` / `NO_PROXY`
- `http_proxy` / `https_proxy` / `all_proxy` / `no_proxy`

只有 `ANTHROPIC_API_KEY` 为 absent。冻结脚本因此在完成 workspace 与 system tmpdir 写读后，以
`exit 73 / E1_TOOL_ENV_LEAK` fail closed。网络矩阵尚未开始，`E1_PROBE_COMPLETE` 未出现。

这是有效样本中的 capability failure，不是 `INVALID`：fixture、policy、fork identity、session lineage、tool count 和 frozen
command 均满足 Gate；Claude session 正常完成，tool 恰执行一次。失败发生在 Gate 明确要求的 provider-env/tool-env 隔离边界。

## 执行摘要

1. non-provider preflight：fresh reactor `1294/0/0/0`；E1 focused `95/0/0`；fork installer
   `973/0/0`；Doctor、build identity、四 fixture SHA 与 launcher compile 全部通过。
2. minimal health：fresh session `e1-health-49495db`，无工具，最终回答 `OK`，随后 closed。
3. sentinel：从空专用目录启动；宿主 `127.0.0.1:18765` 与 `198.18.0.1:18765` 均返回 200；probe 后停止。
4. Bash probe：fresh session `e1-sandbox-bash-49495db`；一个 prompt、一个逻辑 Bash tool call，input 精确为
   `./e1-network-probe.sh`；tool 以 73 失败，session 随后 closed。
5. stop-loss：未运行 WebSearch/WebFetch probe，未运行 session-conflict cross-check，未创建第三个 tool session，未恢复 OKX。

## Q3-Q15

| 问题 | 结果 | 说明 |
|---|---|---|
| Q3 sandbox deny effect | `NOT_REACHED_AFTER_FAIL` | env hard failure 发生在首个网络命令前，不能外推网络 sandbox 已生效 |
| Q4 network matrix | `NOT_REACHED_AFTER_FAIL` | direct/chain/child/git/npm/Maven/IP/DNS/loopback/LAN 均未执行 |
| Q5 loopback/LAN | `NOT_REACHED_AFTER_FAIL` | sentinel 本身已由宿主证明可达，但 tool 未进入对应命令 |
| Q6 provider env / tool env separation | `FAIL` | 11 个 denied names 中 10 个在 tool subprocess 为 present |
| Q7 filesystem compatibility | `PASS` | workspace 与 `${TMPDIR}` 写读均成功，`results.tsv` 含 `filesystem\t0` |
| Q8 WebSearch coexistence | `NOT_RUN_STOP_LOSS` | Gate 禁止在 Bash 首个 hard failure 后继续 |
| Q9 structured network denial evidence | `NOT_REACHED_AFTER_FAIL` | 没有网络 request occurrence，不能判断 upstream evidence 能力 |
| Q10 domain grant | `DEFERRED` | 尚未实现，且 Gate FAIL 禁止继续实施 |
| Q11 session policy conflict | `NOT_RUN_STOP_LOSS` | 未以独立 ensure 掩盖首个 hard failure；deterministic enforcement 基线不变 |
| Q12 sandbox unavailable branch | `NOT_PROVEN` | 未做破坏性故障注入 |
| Q13 approveAll / Doctor | `PASS_DETERMINISTIC` | focused tests 与 Doctor 通过 |
| Q14 network wait | `DEFERRED` | 尚未实现 |
| Q15 Codex | `NOT_MODELED` | Claude 结论不外推 |

## Boundary Closure

- Declared by：production `ClaudeExecutionPolicy` 的 fixed policy 与 11-name deny 集合。
- Proven by：policy ref/SHA、closed scoped session、真实 Bash tool result、`environment.tsv`。
- Consumed by：fork session/new 的 policy binding；该 binding 在 record 中与 run policy SHA 一致。
- Enforced before：脚本在首个网络命令前检查 tool env，并以 exit 73 阻止后续网络副作用。
- Failure route：Gate `FAIL`，停止 WebSearch 与 `PUBLIC_NETWORK_DOMAIN` 实施，返回 bounded comparison。
- Cannot-bypass evidence：一个 prompt、一个 exact Bash call；无 retry、无第二 Bash、无 WebSearch、session closed。
- Residual：本样本不能回答实际 network sandbox deny、structured denial evidence 或 WebSearch coexistence；不得解释为部分 PASS。

## 下一步边界

只允许对 env propagation owner 做 bounded comparison：区分 provider process 必需 env、ACP server env 与 tool subprocess env，
核实 Claude Code sandbox/settings 是否具备真实的 subprocess env stripping owner。若 upstream 无该能力，应停止在 fork 中继续堆
字段，并比较 adapter 启动时的最小 env 分层或隔离 broker；不得用 prompt、Bash wrapper/parser、secret 名字黑名单的第二实现，
也不得恢复 OKX real-case。

