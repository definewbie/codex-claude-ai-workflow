# OKX Fresh E2E With Human-Reviewed Scope Additions

状态：PREFLIGHT PASS / AUTHORIZED TO START。主 agent 自查；用户已授权执行到 E2E 完成或缺陷止损，不启动额外 agent。

## Verification Gate

- Target: 一个 fresh 样本完成原 OKX 产品需求、workflow COMPLETED、completion audit ready 与 accepted candidate；不是仅证明 scope validator。
- Source of truth: REQUIREMENT.md、qualified contract/approved authored refs、run.json/validated sidecar、gate/iteration/events、Git evaluation、测试 XML、实际 API/浏览器与 candidate bytes。
- Trigger precondition: 本 checkpoint 完整 package 通过、生产源码选择性提交、新 JAR/四 workflow 身份核对、Doctor OK、当前模型配置未漂移、source 与 effective workspace 原检查通过。
- How precondition is checked before run: 记录 code/JAR/workflow/task/check/fork 身份；同日已通过的 unchanged transport/model health 可复用并记录来源，不重复烧 probe；配置变化时按已有 minimal health 门重验。start 不带 run，workspace capture/verify 完成后才业务调用。
- PASS: 需求全文验收、COMPLETED、audit ready、有效 accepted candidate 合取；每个审批消费当前 typed 证据与正确身份。产品测试与运行证据不可由模型正文替代。
- FAIL: 有效运行的 authority 绕过、批准/状态不一致、产品验收不能完成或下列缺陷止损；自然合法 revise 不自动失败。
- INVALID: 工件/配置漂移、前置失败、手改 ledger、运行中修改 harness/任务、旧样本或人为制造故障；保留证据，不原地换输入重跑。
- Stop-loss: 同 blocker 无新证据连续两轮、部分 durable 状态或错误副作用立即停；耗时本身不是失败，观察模型/进程是否有进展。quota 单列等待用户切换后合法恢复，不归因产品缺陷。
- Run artifact freshness: 执行前补齐本页冻结值；仅文档后代允许，JAR 字节不得改变；同一 fresh workstream，不恢复 FZ8K/6KVE/SPM9，不创建第二样本。

## 完整消费链与处置

| 触发 | 后续必须消费 / 失败出口 |
|---|---|
| proposal 到两次合同批准 | resolved gate + compile lineage；候选或历史 draft 不成为批准 |
| 新合同到设计/计划/实现 | 当前合同 epoch 与已批准作者版本；不能复用旧批准跳过 |
| 普通新增文件 | 不提前取消模型；由 Git evaluation 进入 human repair，基于职责/代码决定修回或 request_scope_revision |
| request_scope_revision | 只是申请；绑定 evaluation 的 exact CREATE 候选 -> proposal/review -> requirement approve -> compile -> contract approve -> 新设计/计划/实现 |
| self-test/acceptance 修复 | typed 原因路由真实修复节点；产品文档问题不伪造合同缺口 |
| acceptance 到 terminal | Git scope、测试与需求质量、candidate bytes、completion audit 同时成立 |

Declared by 需求/定义/明确批准；Proven by immutable refs/SHA/typed facts/Git；Consumed by
既有唯一 resolver；Enforced before 发布/路由/执行/交付；Failure route 见上表；Cannot-bypass
由完整 scope repair/reapprove/candidate 序列与拒绝测试支撑，真实未触发路径记 NOT EXERCISED。

只读 profile 成功写入或成功 Bash、保护区/工作区外写入、授权身份漂移仍立即停止。
新增文件合理性不是仅凭文件名裁定，但未批准文件不得偷偷进入交付；人通过完整合同链批准。
删除/重命名/软链接等不适用本批普通 CREATE 分支，不隐性放权。

## 产品与权限边界

产品沿 REQUIREMENT 全文：Java25/Spring Boot4.1.0/MyBatis Plus3.5.17/SQLite、分钟 K 线、图表、
刷新、数据与错误行为、默认离线测试及 opt-in 真实 OKX smoke。README 中文沿既有用户要求。
用户已取消手机图标：不新增 favicon/PWA/专门移动端图标要求；不让它阻塞验收。
用户委托主 agent 逐门阅读后做 approve/revise/request_scope_revision，不使用 autorun。
Phase A 网络授权只按既有明确操作与需要的域名进行，不实现自动捕获、不改模型层。
交付止于 effective workspace 和 accepted candidate；不向原 target apply/commit/finalize，不 push/PR。

## 冻结身份

- code checkpoint：6a86817；JAR SHA：05a4b1f60008cfc37e2c19f607eedf44b6f0539955f3b0861f73f1b163886e1d。完整 package 1440/0/0/0、Doctor OK、workflow validation 28/48通过。
- small workflow SHA：9cb72f006be03d541abef4e259e55ca30a7e6973ea6c40dbf9e175ae1a9fbd4b（28 nodes，48 edges；agent/gate 数未改）。
- target HEAD/tree：c84807946493a603c789ee40c6a4c615aa846c3b / 81520ac179f17c35c7c358c2a02102007cbe7cba。
- REQUIREMENT SHA：9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a。
- check SHA：c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23；source check 本轮已 PASS。
- Claude 当前配置：open.bigmodel.cn，Sonnet/Opus glm-5.3，Haiku glm-4.7-air；不记录凭据。
- Codex 从 /Users/kriswang/.codex 配置读取，不固定模型；配置身份前置重核。
- reviewed acpx patch：4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e，schema v2；未改 fork。
- 每次调用统一 CODEX_HOME=/Users/kriswang/.codex、DUAL_ACPX_EXECUTABLE=本仓 .tools/acpx-e1/bin/acpx、DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=0。

执行证据写入 output/real-cases/20260909-okx-human-reviewed-scope-e2e；最终结论另归档报告。
code HEAD/tree 只记录实施 checkpoint；后续仅文档提交不视为漂移，以 ancestry + production diff + JAR SHA 判定。

同日健康证据复核：output/real-cases/20260909-okx-profile-aligned-e2e/glm-read/result.json
为 exit0/end_turn、adapterSignals空、nonce往返，session已关闭；codex-health.jsonl为
end_turn/gpt-6-astra。当前 settings模型映射与Codex config仍相同，adapter/profile本批零改动，
复用该证据不另消耗重复探针。workspace capture/verify仍必须在新workstream上执行，不能复用旧样本。
