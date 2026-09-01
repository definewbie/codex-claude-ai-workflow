# E1 Proxy Transport Ownership Comparison Evidence

日期：2026-09-01

## 目的

复现 `claude-agent-sdk 0.3.232` 在 `resolveSettings({settingSources: []})` 下的 managed env 行为，
只判断八个 proxy key 是否存在，不读取或保存其值。

## 输入

- SDK：本机已缓存的 `@anthropic-ai/claude-agent-sdk@0.3.232`
- setting sources：空数组
- 查询键：`ALL_PROXY`、`HTTP_PROXY`、`HTTPS_PROXY`、`NO_PROXY` 及其小写形式

## 结果

八个键全部 absent。该证据只排除当前 managed settings resolution 路径，不证明 Claude embedded runtime
之后不会创建自身的 sandbox proxy transport，也不承担网络安全结论。

原始 presence-only 输出见 `managed-settings-proxy-key-presence.tsv`；`SHA256SUMS` 绑定本目录两个证据文件。
