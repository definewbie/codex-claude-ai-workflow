# S2 SDK Sandbox Option Local Binding Result

日期：2026-09-03  
Gate checkpoint：`a88a53b3`  
结论：`PASS / HOST_REACHABLE / OPERATOR DECISION REQUIRED`

## 1. 机械判定

- SDK `0.3.232` 与 embedded Claude Code `2.1.232` 身份和冻结 SHA 一致。
- 只有一次 SDK query、一个 Bash tool use，命令精确为 `./s2-loopback-probe.sh`。
- 对应 tool result `is_error=false`；最终 SDK result 为 `success`，`permission_denials=[]`。
- `python3_available=true`。
- sandbox 内 listener 创建成功，同 sandbox loopback 连接成功。
- sandbox 可连接宿主机预先创建的 loopback sentinel：`host_connect=pass`。
- 未授权外网直连被拒绝：`external_connect=denied`。
- host sentinel 运行前后均可用，fixture SHA 运行前后一致，SDK exit code 为 `0`。

## 2. 语义

`Options.sandbox.network.allowLocalBinding=true` 在本次冻结 runtime 上不只允许 sandbox 内启动本地测试服务，
也允许 sandbox 中的 Bash 访问宿主机已监听的 loopback 服务。因此该开关不能被表述为
“只允许自建 listener”。

本次运行同时证明空 domain allowlist 下的外网直连仍被拒绝，但不证明完整 E1 网络安全。

## 3. 边界与下一步

- S2 production 仍为 `NOT IMPLEMENTED`。
- 本结果不授权恢复 OKX real-case、开放 external domain 或增加通用网络平台。
- 必须由 operator 决定：是否接受 self-test/implementation profile 在开启 local binding 时可访问宿主
  loopback 服务。
- 若接受，下一步只是起草“将该 option 投影到指定 profile”的最小 S2 实施计划；若拒绝，
  停止 S2，回到 harness-owned deterministic executor 候选比较。

Operator 已于 2026-09-03 接受 `HOST_REACHABLE` 语义。授权边界仅为现有 `implement` permission profile；
该 profile 同时覆盖实现与自测节点。不对 intake、observe、review 或 contract-discovery profile 开放。

## 4. 权威工件

原始 SDK JSONL、options summary、TSV、host pre/postcheck、fixture SHA 和 stderr/stdout 均保存在同目录
`runtime/`；`SHA256SUMS` 是该目录的排序逐文件清单。
