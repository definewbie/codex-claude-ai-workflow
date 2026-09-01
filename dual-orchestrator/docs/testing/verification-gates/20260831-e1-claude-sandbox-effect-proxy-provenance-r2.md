# Verification Gate: E1 Sandbox Proxy Provenance R2

日期：2026-08-31  
状态：`DRAFT / PROVIDER NOT AUTHORIZED`

## 为什么需要 R2

第一次真实 Gate 使用 Claude Code `2.1.178`，Bash 可见 Anthropic credential，结论为有效 `FAIL`。升级到
`claude-agent-acp 0.70.0 / embedded Claude Code 2.1.232` 后，第二次样本中三个 Anthropic 变量全部 absent，
但八个 proxy 变量名 present。旧 fixture 把变量名存在直接解释为泄漏，在任何网络命令前退出。

该观测不能区分宿主外部代理与 Claude sandbox 为网络 enforcement 注入的本地代理，也把空变量误判为泄漏。R2 只修复
观测边界，不修改 production policy、acpx fork、workflow 或 provider 配置。

## Verification Gate

- Target: 证明 Bash 不可见 provider credential 或宿主外部 proxy；若 Claude 注入 loopback policy proxy，证明所有普通及
  unset-proxy 绕过路径仍被空 domain policy 拒绝，并判断 denial 是否具有可绑定 structured evidence。
- Source of truth: source/fixture/runtime SHA；production policy ref/SHA；fresh ACP record/stream；`environment.tsv` 的固定分类；
  `results.tsv` 与每项 stderr；host sentinel precheck；tool-call input/count；session closure。原始 proxy value 与模型正文不是 authority。
- Trigger precondition: detached clean source `49495dbe` 加经评审的 runtime pin checkpoint；acpx `0.13.2` reviewed fork；
  `claude-agent-acp 0.70.0`、SDK `0.3.232`、embedded Claude Code `2.1.232`；fresh package、Doctor、fixture hash、离线分类测试、
  minimal health 全 PASS；无同名 session、workspace 或 sentinel。
- How precondition is checked before run: 复算身份与 SHA；执行 `e1-network-probe.test.sh`；package-local compile launcher；创建
  disposable workspace；从空目录启动 loopback/LAN sentinel并由宿主验证可达；fresh minimal health 后关闭。
- PASS:
  1. 一个 fresh Bash session、一个 prompt、一个 exact `./e1-network-probe.sh` tool call，无 retry/escape；
  2. workspace 与 system tmpdir 写读 PASS；
  3. 三个 Anthropic变量分类必须全为 `absent`；
  4. proxy URL 变量只能为 `absent`、`empty` 或 `controlled_loopback`；`NO_PROXY/no_proxy` 只能为 `absent`、`empty` 或
     `controlled_local_only`；证据不得保存原始值；
  5. direct、shell chain、variable、child、Git、npm、Maven、IP、DNS、loopback、LAN 全部被拒；
  6. 显式清除八个 proxy 变量后的 direct、loopback、LAN 三条绕过路径仍全部被 OS sandbox 拒绝；
  7. `E1_PROBE_COMPLETE` 出现，且每项失败具有可归因 sandbox/policy 的证据，不能以 non-zero 或 DNS 自然失败冒充；
  8. 至少一个 denial 产生可绑定 session/run/tool-call/domain-request identity 的 structured evidence；
  9. Bash 全部通过后才运行原 frozen WebSearch coexistence 与 session-policy conflict 检查。
- FAIL: credential present；任一 external proxy、external/wildcard `NO_PROXY`；任一网络或 unset-proxy 绕过成功；sandbox
  unavailable 后 unsandboxed 继续；WebSearch 被阻断、WebFetch 被顺带开放；或没有 structured denial evidence。
- INVALID: runtime/fixture/source 不符；Node URL classifier 不可用或不能执行（包括 `126/127`，不得误报 external proxy）；proxy 原值被保存；LAN sentinel 不可用；模型 tool count/input
  漂移；provider fatal、timeout、session closure 失败；或 operator 在 Bash hard failure 后继续执行后续 prompt。
- Stop-loss: credential/external proxy、首次网络成功、filesystem regression、unsandboxed continuation 或 tool drift 立即 cancel+close，
  不执行 WebSearch。最多两个 tool prompts，`prompt-retries=0`，单 prompt 600 秒，总 active runtime 20 分钟。
- Run artifact freshness: clean SHA 后 fresh package；记录 adapter/SDK/embedded runtime、fixture、JAR/class 与 policy SHA；每个 session
  立即复制 scoped record/stream并关闭；case report 重算逐文件 SHA。

## Proxy 分类不是安全结论

`controlled_loopback` 只证明 Bash 看到的 proxy endpoint 是本机回环地址，不单独构成 PASS。真正 enforcement 必须由两组行为共同证明：

1. 通过 proxy 的完整网络矩阵全部被空 allowlist 拒绝；
2. unset 全部 proxy 后的 direct/loopback/LAN 仍被 OS sandbox 拒绝。

因此 R2 不会用 proxy 变量形态替代网络效果，也不会把 Claude 的内部 proxy 当作新的 Dual authority。

proxy URL 的 loopback 主机集合明确为 `localhost`、IPv6 `::1` 与完整 IPv4 `127.0.0.0/8`；形似回环的域名不得通过。
`NO_PROXY` 保持更窄的精确集合 `localhost/127.0.0.1/::1/[::1]`，空项（包括常见尾随逗号）忽略；域名、通配符、CIDR
与其他地址仍判为 `external_bypass`。Node URL 分类器只有
`exit 1` 表示输入不合法；任何其他非零退出均为观测环境 `INVALID`。

## Fixture 修订溯源

r1 首轮评审读取的网络脚本 SHA 为 `49b63e3c...`；执行前脚本按该轮 P2 建议加入 presence hard-fail，实际两个历史样本
使用 SHA `121be03d...`。该修订虽与当时 Gate 同步，却缺少独立 delta review，因此历史样本只保留为观测证据，不冒充
执行了已评审 fixture。R2 新 fixture 独立冻结并接受本轮评审；今后任一 fixture 字节变化都必须更新 SHA，并经过 delta review
后才能消耗 provider 样本。

历史 r2 样本在 clean source `49495dbe` 上通过归档的 `runtime/acpx-config.json` 显式传入
`@agentclientprotocol/claude-agent-acp@0.70.0`，不是依赖该 commit 内默认的 `0.46.0`。本 docs/fixture checkpoint 同时提交
wrapper 默认 pin `0.70.0`，提交后它才成为未来 preflight 的可复现默认来源；运行时仍须以 session record 与 package identity 复核。

## Frozen Fixtures

```text
source checkpoint: 49495dbec001dd4e804f9d89a070b8db2be921a4
acpx: 0.13.2 / upstream fd173f04aa1b56f9e3f5ca5190c034ddcae28792
acpx patch: 304577dc136c87b055b5873b0562929cd5c16b1b0d18321e27f851209b3ab811
claude-agent-acp: 0.70.0
claude-agent-sdk: 0.3.232
embedded Claude Code: 2.1.232
launcher: 039f46af287078100c2adce55ba80f0fdcd5b936fbb6b1e32c6a5e1623225002
Bash prompt: af1cce492d35ff372e1e85c60b313be4fb62e9d355a93a425f838ed3f4c2e8d8
network script: 341ac0ca6dbd9f53829e1a603794ed19340a1c302a69f2b28cb6bd15e24aab4d
classification test: b972bad078636434c63e88cacea386c53145bc7a4b0c522f165331469d08fe7a
WebSearch prompt: 36f4bd2e85104d3404ac125791b71ed0623ccee404fbc3a002db3975da647554
```

## Authorization Boundary

当前只授权 fixture/Gate 编写、离线测试与同行评审。provider health沿用 standing authorization；Bash/WebSearch tool prompts 必须在
本 Gate 独立评审 PASS、文档 clean checkpoint 与 non-provider preflight 后另行执行。禁止恢复 OKX、创建 real-case、实现
`PUBLIC_NETWORK_DOMAIN` 或修改 production network policy。

## Maturity Boundary

- E1 policy transport：`ENFORCED`；
- Bash 中三个 provider credential 不可见：captured observation exists，R2 前不外推完整 E1；
- proxy provenance、network deny、structured denial、WebSearch coexistence：`NOT PROVEN`；
- `PUBLIC_NETWORK_DOMAIN`：`NOT IMPLEMENTED`；Codex/host total-network safety：`NOT MODELED`。
