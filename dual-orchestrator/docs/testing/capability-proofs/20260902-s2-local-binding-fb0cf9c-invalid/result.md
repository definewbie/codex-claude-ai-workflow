# S2 Local Binding Production-Chain Diagnostic Result

日期：2026-09-02  
Gate checkpoint：`fb0cf9c`  
结论：`INVALID_PRE_PROVIDER / CLOSED / NO RETRY`

## 结果

non-provider preflight 全部通过：offline fixture、5/5 SHA、fresh package、Doctor、reviewed acpx identity、embedded Claude
`2.1.232` binary SHA 与 launcher compile 均匹配冻结 Gate。

唯一 execution attempt 没有创建 ACP session，也没有发起 provider prompt。`sessions ensure` 在 bootstrap 阶段返回：

```text
allow_local_binding must equal false
```

`launcher.json` 记录 transport `exitCode=4` 与 `SESSION_BOOTSTRAP_FAILED`；scoped session 名在 `~/.acpx/sessions` 零命中，
workspace 内没有 `s2-loopback-result.tsv` 或 probe stdout/stderr。host sentinel 前后检查和 fixture SHA 前后检查均通过。

## 根因

Gate 假设“由 fixture 把 production policy 的 `allow_local_binding` 翻为 `true` 后，可继续通过未经修改的 reviewed fork”不成立。
reviewed fork 的 `claude-execution-policy.ts` 当前同时：

- 以 `assertBoolean(network, "allow_local_binding", false)` 固定只接受 `false`；
- 向 SDK 固定投影 `allowLocalBinding: false`。

因此 allowlist differential 的既有方法不能直接复用于该字段：`allowed_domains` 本来就是 fork 允许变化的输入，而
`allow_local_binding` 不是。

Gate runner 另有一个判定缺口：Java launcher 自身正常结束并写出 transport result，所以进程 exit 为 `0`；runner 只检查 launcher
进程 exit，没有解析 `launcher.json.exitCode`，未能自行把 transport `exitCode=4` 标记为 INVALID。缺少 probe TSV 最终仍使 Gate
无法判为 PASS，但 fail-fast 不完整。

## 边界结论

- S2 sandbox local-binding behavior：`NOT MEASURED`；
- provider prompts/tool calls：`0 / 0`；
- E1 fork enforcement：按当前 production 设计正确 fail-closed；
- 本次不构成 `HOST_DENIED`、`HOST_REACHABLE` 或 S2 `FAIL`；
- 禁止修改原 fixture 后原地重跑，禁止消费第二样本。

下一步必须重新选择最小测量边界，不能继续声称“未经修改的 production 链可接收临时 true”。候选应优先比较：直接使用本地
SDK `0.3.232` 的 managed settings 做一次 capability measurement，或先实施并评审 fork/policy 的最小 prospective change 后再测量。
在完成这个选择前，不恢复 OKX real-case。
