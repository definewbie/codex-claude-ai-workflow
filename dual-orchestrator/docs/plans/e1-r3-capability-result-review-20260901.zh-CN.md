# E1 R3 Capability Result 独立评审

日期：2026-09-01  
评审方式：只读复算 SHA、git 谱系、policy/launcher/session record/stream、preflight 与 stop-loss；未调用 provider。

## 结论

`FAIL / STOP-LOSS EXECUTED` 成立。`P0=0 / P1=0 / P2=4`，Boundary Review `PASS`。

## 核心核实

- 归档 evidence SHA、fixture SHA、execution source 谱系与 frozen runtime identity 一致；
- production policy ref/SHA、session options 与归档 policy 三点一致，deny 列表包含 `HTTP_PROXY`；
- 恰一个 prompt、一个 logical Bash tool call，`command` 精确为冻结命令；零 permission request、零 unsandboxed escape；
- Bash 返回 `exit 73 / E1_PROXY_CREDENTIAL_LEAK HTTP_PROXY`，网络矩阵与 WebSearch 未执行；两个 session 均 closed；
- evidence 只记录 `present_with_userinfo` 分类，不包含 proxy 原值；
- 证据足以证明 denied-name 契约没有覆盖最终 effective Bash environment，但不能证明宿主 credential 泄漏或
  Claude sandbox 不安全。

## 收窄事实

1. `acpx-claude-agent-env.sh` 只转发固定 Anthropic allowlist，用户 `settings.json` env 也没有 proxy key；
   settings-env bridge 可从本轮候选 owner 中排除。
2. `python-self-test.stderr` 的 EPERM 来自 operator/Codex 语境；该签名不能跨语境归因，必须绑定 Claude session 内 probe。

## P2

1. sentinel 没有独立的关闭 exit-code receipt；
2. Python EPERM 的语境需要在 result 中解释；
3. precheck 时间只由 mtime 间接表达；
4. tool 等值应明确以 command 字段为准，description metadata 不算 drift。

以上均不改变本轮 FAIL。result 已通过文字 delta 如实登记，禁止补造历史 evidence。

## 下一步边界

允许在 clean result checkpoint 后进行一次 bounded proxy provenance comparison。优先比较宿主值与 Bash effective
值的 occurrence-local HMAC/equality；同时离线复验 policy loader 到 SDK 的传递链。不得保存原值、重跑 provider、清空受控
proxy、实施 `PUBLIC_NETWORK_DOMAIN`，也不得扩张为通用 env-provenance 平台。

成熟度保持：policy transport `ENFORCED`；proxy confidentiality `FAILED TO PROVE`；network deny、OS direct-connect
与 WebSearch coexistence `NOT REACHED / NOT PROVEN`。
