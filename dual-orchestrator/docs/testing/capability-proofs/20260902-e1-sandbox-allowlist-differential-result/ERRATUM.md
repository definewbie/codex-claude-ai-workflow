# Evidence Erratum

日期：2026-09-02

本勘误不修改任何原始 stream、session record、launcher 或 probe 输出。

## 修正

1. `runtime/session.json` 中持久化的 execution policy SHA
   `ea47a3edc47e4eee0fb21a90b080015a021402756eea6a221567b490ef9a49e2` 属于
   `2026-09-02T01:41:23Z` 开始的较早 reviewed-acpx invocation，不是有效 differential occurrence 的 binding。
2. 有效 occurrence 使用的 policy SHA 是
   `ce796a1bfb1880830735f843da809a548b339df443f3d1d97769d689cfb7e443`。其依据为
   `runtime/launcher.json`、归档 policy 文件及 `runtime/session.stream.ndjson` 中
   `session/new` `_meta.claudeCode.options.sandbox` 的一致内容。
3. 原 `result.md` 的失败尝试计数不完整：除其中记录的两次 prompt 前失败外，还存在上述
   `01:41:23Z` invocation。它创建了后续被复用的 persistent record，但其完整产物未归档，因此不能补写为完整样本。
4. allowlisted `example.com` 成功、未列入 allowlist 的 Cloudflare 请求返回 403 的同 occurrence 差分仍成立；
   但本目录不能证明 persistent record 的 policy binding durability。该项应视为已发现的 acpx lifecycle 缺陷，
   在独立 deterministic checkpoint 中修复。

## 判读边界

- 本目录可用于证明 bounded allow/deny behavior observation。
- `runtime/session.json.acpx.session_options` 不得作为该有效 occurrence 的 policy identity 证据。
- 在 fork 修复前，不得用本目录把 persistent policy binding durability 标为 `ENFORCED` 或 `PROVEN`。
