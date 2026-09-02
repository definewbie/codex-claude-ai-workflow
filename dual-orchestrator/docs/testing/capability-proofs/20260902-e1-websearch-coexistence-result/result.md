# E1 WebSearch Coexistence Diagnostic Result

日期：2026-09-02  
结论：`INVALID_PROVIDER_TOOL_BACKEND`

## 执行事实

- 使用冻结 WebSearch prompt、production review profile + exact `WebSearch` auto-approval、retry=0；
- 实际 CLI 包含 `--allowed-tools Read,Grep,Glob,WebSearch` 与
  `{"autoApprove":["read","search"],"escalate":["fetch"],"defaultAction":"deny"}`；
- E1 policy v2、空 Bash `allowed_domains`、strict allowlist 与 policy SHA 均经 reviewed fork 消费；
- 恰一个 provider prompt；WebSearch 与 WebFetch 各调用一次；无 Bash；session 已关闭；
- adapter exit 0 只表示 agent turn 正常结束，不表示两个工具成功。

## 观察结果

| 工具 | 结果 |
|---|---|
| `WebSearch("Apache Maven official website")` | failed: `API Error: 400 [1214][modelCode：不存在]` |
| `WebFetch("https://maven.apache.org/")` | failed: 同一 `API Error: 400 [1214][modelCode：不存在]` |

stream 中两者均显示 `kind: fetch`，均没有成功 tool result，也没有出现预期的 WebFetch permission request/deny。
模型最终 JSON 的两个布尔值均为 false，与 typed tool events 一致，但模型正文不作为判定 authority。

## 分类

本轮无法证明 WebSearch coexistence，也不能把结果归因于 Claude sandbox 网络：两个 provider-native 工具在进入可区分的
权限/成功结果之前，被相同的 provider/tool-backend `modelCode` 配置错误拒绝。因此判
`INVALID_PROVIDER_TOOL_BACKEND`，不是 sandbox `FAIL`。

同时，WebFetch 未出现预期 permission request，说明 exact-tool permission boundary 在此 backend-invalid occurrence 中也没有
获得有效证明；不得仅凭 CLI 参数宣称 `ENFORCED + PROVEN`。

## 下一步边界

只读核实 Claude Agent SDK/当前 provider 对 WebSearch/WebFetch 的 model/tool backend 配置与 ACP permission event 顺序。
在 backend preflight 能机械证明 WebSearch 可用之前，不重复 provider sample；不得通过自动批准 `fetch`、开放 WebFetch、
放宽 parser 或忽略 permission event 来制造 PASS。
