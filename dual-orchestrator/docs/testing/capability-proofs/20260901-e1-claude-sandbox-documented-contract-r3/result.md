# E1 Claude Sandbox Documented Contract R3 Result

日期：2026-09-01  
Execution source：`0d70d3f5`  
Fixture checkpoint：`5c26c0df`  
Gate 结论：`FAIL / STOP-LOSS EXECUTED`

## 决定性结果

唯一 Bash capability occurrence 按冻结 prompt 恰好执行一次
`./e1-sandbox-contract-probe.sh`。脚本在任何网络请求之前返回：

```text
exit 73
E1_PROXY_CREDENTIAL_LEAK HTTP_PROXY
```

`environment.tsv` 只记录分类，不保存值、hostname 或端口：

```text
ANTHROPIC_API_KEY absent
ANTHROPIC_AUTH_TOKEN absent
ANTHROPIC_BASE_URL absent
HTTP_PROXY present_with_userinfo
```

因此，按冻结 Gate 的明确 FAIL 条件，本轮必须失败并立即停止。WebSearch、网络矩阵、第二个
Bash command、retry 和 correction 均未执行。

## 已证明的事实

1. fresh minimal health 成功：一个 prompt、零工具、回答 `OK`，session 已关闭；provider/runtime
   可用，不是 provider fatal 或 timeout。
2. Bash occurrence 为一个 prompt、一个 logical tool call；tool input 与冻结命令一致，session 已关闭。
3. production policy ref/SHA 通过 transport，policy 声明 `sandbox.enabled=true`、
   `fail_if_unavailable=true`、`allow_unsandboxed_commands=false`、`strict_allowlist=true`、
   `allowed_domains=[]`，并把 `HTTP_PROXY` 列入 `subprocess_denied_env_names`。
4. 三个 Anthropic credential 名称在 Bash 子进程中均 absent。
5. Bash 子进程仍看到带 URL userinfo 形态的 `HTTP_PROXY`。这与 Dual policy 声明的 denied-name
   结果不一致，说明当前“subprocess denied env”契约没有覆盖最终 effective Bash environment。
6. 宿主在 occurrence 前后的 loopback、LAN、HTTPS 与 direct socket 检查均成功；sentinel 已关闭。
7. 原始 proxy 值未写入任何 evidence；止损后没有网络请求。

## 证据偏差与解释

本轮有三项不影响 FAIL 判定、但不满足冻结 Gate 的完整证据卫生要求：

1. sentinel 的 PID、运行中 HTTP 200 与关闭操作已发生，但归档没有独立的 closure exit-code receipt；
   `sentinel.log` 也不能单独证明关闭。结论只依赖前后宿主检查与本轮未进入网络矩阵，不把“已关闭”提升为
   可由归档独立复算的能力事实。下一 Gate 必须在停止动作发生时同步落盘 PID、时间、signal、wait exit code 与 stdout/stderr。
2. precheck 主要依赖文件 mtime，未把每一步的 wall-clock 时间显式写入记录。下一 Gate 必须生成命令级
   timestamp/exit-code 表，不能再以 mtime 代替。
3. `python-self-test.stderr` 中的 `PermissionError: [Errno 1] Operation not permitted` 来自 operator/Codex
   执行语境，不是 Claude ACP session。它只验证 Python exception 文本格式，不能证明 Claude sandbox engaged；
   后续正向归因只能接受同一 Claude Bash occurrence 内对应 probe 的 evidence。

Tool 等值判定以 ACP `rawInput.command` / parsed `input.command` 逐字等于冻结命令为准；模型附加的
`description` 只是同一 tool call 的 metadata，不构成第二命令或 tool drift。

## 不能据此证明的结论

本轮没有证明宿主 proxy credential 泄漏，也没有证明 Claude sandbox 不安全。URL userinfo 可能是：

1. 从宿主环境继承的代理凭据；
2. Claude 内建 policy-enforcing proxy 注入的 session-scoped capability token；
3. adapter 或 SDK 在 provider/tool 环境转换时生成的其他受控值。

当前无值探针只能看见 `present_with_userinfo`，无法区分三者。Gate 对 userinfo 的 fail-closed 规则有效地
阻止了继续执行，但它只能证明“Dual 期望的 confidentiality contract 未被证明满足”，不能把来源归因给
Claude、宿主或 adapter。

## 架构含义

R3 暴露的共性缺口不是某个 proxy hostname，而是环境变量 authority 仍只有字符串黑名单：

- Dual 把 provider secret、宿主 proxy credential 和 sandbox 内部 network capability 都表达为
  `subprocess_denied_env_names`；
- Claude sandbox 可能在 credential scrub 之后注入其自身网络 transport；
- policy 没有表达“哪类值必须消失、哪类受控 capability 可以存在、由谁生成、作用域与生命周期是什么”；
- 最终 effective Bash environment 没有 occurrence-bound provenance 可供 Dual 重验。

所以不能用继续追加变量名或直接删除 proxy 的方式修复。那可能破坏 Claude 已公开说明的 sandbox proxy
enforcement transport。

## 下一步边界

下一步只允许做 bounded provenance comparison，不运行第二 provider 样本：

1. 比较“继承宿主 proxy”与“Claude 注入 session-scoped proxy capability”两种 owner 模型；
2. 设计不保存原值的同源判定，例如 occurrence-local HMAC/equality、endpoint class、注入阶段与生命周期；
3. 明确 effective environment 的唯一验证点必须在 Bash exec 前，不能只验证输入 policy JSON；
4. 若无法证明 capability token 的 scope、不可复用性和不含宿主秘密，则 E1 保持 FAIL；
5. 不得通过清空 proxy、放宽 Gate、增加 retry 或开放 `PUBLIC_NETWORK_DOMAIN` 绕过。

本轮只比较该 occurrence 的 proxy provenance，不建设通用 environment-provenance schema、服务或平台。

## Maturity

- E1 policy transport：`ENFORCED`；
- Anthropic credential name absence：`CAPTURED FACT`；
- subprocess proxy confidentiality：`FAILED TO PROVE`；
- sandbox domain deny、OS no-direct-connect、WebSearch coexistence：`NOT REACHED / NOT PROVEN`；
- `PUBLIC_NETWORK_DOMAIN`：`NOT IMPLEMENTED`。
