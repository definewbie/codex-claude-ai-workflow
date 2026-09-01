# E1 Sandbox Proxy Provenance R2 Capability Result

日期：2026-09-01  
Source checkpoint：`05f864baabfa5a107fcc6f6fdace3c9a1d196a75`  
结论：`INVALID_OBSERVATION_MODEL / STOP-LOSS EXECUTED`

## 决定性结果

唯一 Bash capability prompt 精确调用一次 `./e1-network-probe.sh`。fixture 完成 workspace 与 system tmpdir 写读后，得到：

```text
ANTHROPIC_API_KEY      absent
ANTHROPIC_AUTH_TOKEN   absent
ANTHROPIC_BASE_URL     absent
HTTP_PROXY             external
```

严格 URL 分类器把 `HTTP_PROXY` 判为 external，即该值不满足 Gate 自行规定的 loopback URL 形态；脚本随即以
`exit 74 / E1_UNSAFE_PROXY_PROVENANCE HTTP_PROXY external` fail-closed。证据只保存变量名和类别，不保存原始 proxy value。

事后对照 Anthropic 官方文档发现，该 URL 形态不是 Claude Code sandbox 的公开安全契约。官方契约是：Bash 网络经沙箱外代理，
直接网络连接由 OS 级 sandbox 阻断，代理按域名策略控制；未配置 `httpProxyPort` / `socksProxyPort` 时 Claude Code 可运行其
内建代理。文档没有承诺子进程中代理变量必须指向 `localhost`、`127/8` 或 `::1`。因此 `external` 只证明 Gate 的私有分类条件
未满足，不能证明宿主代理泄漏或 sandbox 不安全。

本轮必须改判为 `INVALID_OBSERVATION_MODEL`：runtime、fixture 与 occurrence 均有效，但 Gate 测量了未被官方契约保证的实现
投影，并在真正的网络效果矩阵开始前停止。stop-loss 执行本身仍正确；不得把未运行解释为网络允许或网络拒绝，也不得消耗
第二样本弥补错误的观测模型。

官方依据（取证日期 2026-09-01）：

- [Claude Code sandboxing](https://code.claude.com/docs/en/sandboxing)
- [Claude Code settings](https://code.claude.com/docs/en/settings)
- [Agent SDK secure deployment](https://code.claude.com/docs/en/agent-sdk/secure-deployment)

## 执行事实

- clean source：`05f864ba`；fresh package、Doctor、launcher compile、fixture SHA 与离线测试 PASS；
- runtime：reviewed acpx `0.13.2` fork；Claude ACP `0.70.0`；Doctor 记录于 `preflight/doctor.log`；
- minimal health：fresh session `e1-r2-health-05f864b`，一个 prompt、零工具、返回 `OK`，随后 `closed: yes`；
- sentinel：从空目录监听 `0.0.0.0:18765`；宿主 `127.0.0.1` 与 `198.18.0.1` 均实际返回 HTTP 200；stop-loss 后停止；
- Bash session：`e1-sandbox-bash-05f864b-r2` / record `c3e21228-cdbf-4cba-96b6-2f84f7a1edcd`；
- Bash：一个 prompt、一个 logical Bash tool call，input 精确为 `./e1-network-probe.sh`；无 retry、WebSearch 或 WebFetch；
- Bash launcher：transport exit `0` 表示 ACP turn 正常完成；内部唯一 tool result 为 `exit 74`，不是成功；
- closure：Bash session `closed: yes`，sentinel 已停止；
- WebSearch 与 session-conflict：`NOT_RUN_AFTER_INVALID`；没有创建对应 session。

## Q3-Q15

| 问题 | 结论 | 证据边界 |
|---|---|---|
| Q3 sandbox deny effect | `NOT_REACHED_AFTER_INVALID` | 非契约性 proxy URL 形态检查在首个网络命令前触发 stop-loss |
| Q4 Bash/子进程 egress | `NOT_REACHED_AFTER_INVALID` | `results.tsv` 仅含 filesystem |
| Q5 loopback/LAN | `NOT_REACHED_AFTER_INVALID` | sentinel 宿主预检 PASS；sandbox 侧未执行 |
| Q6 proxy env | `INVALID_OBSERVATION_MODEL` | `environment.tsv` 只证明 `HTTP_PROXY` 不符合 Gate 私有 loopback 形态，不能证明不安全 provenance |
| Q7 workspace/tmp | `PASS` | `results.tsv`: `filesystem 0` |
| Q8 WebSearch coexistence | `NOT_RUN_AFTER_INVALID` | Gate 顺序禁止继续 |
| Q9 structured domain evidence | `NOT_RUN_AFTER_INVALID` | 无 WebSearch/domain request occurrence |
| Q10 one-execution domain grant | `DEFERRED / NOT IMPLEMENTED` | 不作为 capability 前提 |
| Q11 session conflict | `NOT_RUN_AFTER_INVALID` | 未执行 conflict ensure |
| Q12 sandbox availability/escape | `NOT PROVEN` | 网络效果未到达 |
| Q13 approveAll/legacy | `ENFORCED / NOT RE-EVALUATED` | 既有 deterministic checkpoint；本轮未新增 provider 消耗 |
| Q14 network wait | `DEFERRED / NOT IMPLEMENTED` | 不冒充 domain wait |
| Q15 Codex | `NOT MODELED` | Claude 结果不外推 |

## 成熟度与下一步

- E1 policy transport：`ENFORCED`；
- credential stripping：本轮再次观察到三个 provider credential `absent`，仍不外推完整 E1；
- proxy URL shape：`INVALID OBSERVATION`，不是 Claude sandbox 的公开安全契约；
- sandbox network deny、structured denial、WebSearch coexistence：`NOT PROVEN`；
- `PUBLIC_NETWORK_DOMAIN`：禁止实施；
- OKX/new real-case：未授权且未运行。

下一步只能先修订 capability Gate，使判定对准官方契约：`sandbox.failIfUnavailable=true`、
`allowUnsandboxedCommands=false`、有效 policy binding、清除 provider credential、直接连接受 OS sandbox 阻断、代理路径按
`allowedDomains/deniedDomains` 生效，并区分内建代理与显式 `httpProxyPort/socksProxyPort` 配置。不得再以子进程 proxy URL
的 hostname 形态充当 authority；禁止消耗第二样本，直到修订后的 Gate 完成独立评审。

## 证据卫生

`bash-session-record/stream/export`、policy、launcher JSON、stdout/stderr、环境分类、输入 fixture 与 preflight identity 均已归档。
原始 proxy value 不在证据中。宿主 sentinel HTTP 200 与进程关闭输出由本轮执行 transcript 捕获，但未单独写入原始日志文件；
该缺口不影响本轮 stop-loss 与 `INVALID_OBSERVATION_MODEL` 判定，后续 Gate 应把 sentinel stdout 直接重定向进 case report。
