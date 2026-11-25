# D04 补测准备：首错停止已离线验证，认证恢复待确认

日期：2026-09-29。用户要求进入可行性验证下一步。本轮没有模型调用、产品/adapter 实现、CC 或额外 agent；D04 仍 DESIGN GAP。不是已取得新的原生读取或安全证明。

## 已完成

- 对固定 Claude binary 只运行本地 auth status；仅保存 loggedIn/authMethod/apiProvider，未读取或记录凭据。显示 loggedIn=true、oauth_token、firstParty；这与上次 401 时一致，服务端认证状态仍 UNVERIFIED。
- 原探针缺陷修在本目录新副本，旧 driver/事件不改。新增共享 typed SDK event consumer，实际 model-probe 与离线 replay 使用同一消费循环；第一个 api_retry/authentication_failed/401 触发 query close，外层观测 probe_stop 并终止进程组。后续 error 不覆盖首次原因。
- [离线序列](offline-validation.json) 18 项通过：旧完整事件在第 2 行（首次 api_retry）即停止，不消费第 3 行；覆盖限流 retry、assistant/result/SDK 错误、工具/MCP/plugins/未知 skills、model/permission 漂移、违规工具、第六次工具调用；合法读取、重复同一 tool id、含 error 字样的正常文件内容不误停。
- [前置校验](preflight-checks.json)通过：脚本语法、默认 dry-run、缺认证恢复确认时实际 --run 拒绝且未创建 model-start。新 [Gate](verification-gate.md)保留单次任务、90 秒、5 turns、SDK 0.50 美元估算阈值及零自动重跑；不得将 SDK 阈值当真实账单硬限。

## 当前停止点

已向用户询问是否恢复实验所使用的 Claude 认证。回复前不重复发送已知曾因 401 无效的请求，不自行刷新/替换凭据或改变计费。即使用户确认恢复，服务端接受仍需首次请求验证；若再次出现认证错误，立即停止，不开展其他模型尝试。当前没有该确认，也没有 run-authorization/model-start。

以上是验证链修正，不是 Claude/D04 可行性结论。没有把原生工具未暴露当作写入拒绝，更没有从文件未变化推断无隐藏副作用。Code Mode/额外工具、配置/读取范围与 scratch 的证据缺口仍在；既有 Codex 合格切片保持，不重复消耗模型以重证相同结论。

## 后续与 CC

有效认证恢复后，先做这一项 Claude 读取补测；通过只关闭其正向读取缺证据项。其余负例按判别力和既定成本上限逐项决定，不自动扩成多轮探索。达到原生可行性证明要求后，整理真实证据与具体绑定设计，给用户简洁 CC 冻结复审提示词；现在不能凭诊断测试 PASS 让 CC 冻结 D04，也不进入实现。

Boundary Review：诊断事件消费序列 PASS_OFFLINE；活体停止时延/服务端认证/原生完整限制 UNPROVEN。SoT=typed SDK event+文件快照；consumer=共享 guard/driver；enforced before=后续诊断消费和后续外层任务启动，不能声称在引擎首个请求/内部重试之前强制。cannot-bypass=历史流首次错误与合法读取对照。根因是旧探针只消费终局 assistant/result，漏了 system/api_retry；同源循环和反证守住该遗漏。无产品技术债豁免。
