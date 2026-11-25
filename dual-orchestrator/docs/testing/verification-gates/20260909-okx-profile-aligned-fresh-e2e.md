# OKX Profile-Aligned Fresh E2E

状态：EXECUTED / BOUNDARY STOP（Gate FAIL；workflow WAITING_FOR_RECOVERY）。主 agent 自查，不冒称独立评审，不委派额外 agent。

## 目标与边界

唯一 fresh 样本完成原始 OKX 产品需求至 accepted candidate。沿用
`20260909-okx-fresh-e2e-after-acceptance-repair.md` 的产品验收、完整转移表和审批授权，
本页覆盖其旧身份与执行入口。6KVE/SPM9 等旧样本只保留证据，不 resume。
用户取消的手机图标/PWA/favicon不加入范围；基本视口可用性不扩大为新设计项目。

只读 Bash 探针证明 Read 成功和真实策略送达；没有实际 Bash 调用，拒绝路径仍 NOT EXERCISED。
不要求模型调用已禁用的工具，不以补齐此观测为 E2E 开工门槛，也不把该缺口改写为已证明。
不新增 model 层、权限机制或探针平台。产品交付而非孤立权限证明是本轮目标。

## Verification Gate

- Target: 原始需求验收、workflow COMPLETED、completion audit ready、有效 accepted candidate 同时成立。
- Source of truth: REQUIREMENT、qualified contract、validated sidecar、gate/iteration/events、实际 Git bytes、测试 XML、API/浏览器/live smoke 证据；模型自述不能替代。
- Trigger precondition: 修复代码 e21c95d 已提交；运行前 tracked clean，之后仅文档提交；下列 JAR/workflow/target/输入/fork 身份一致；Doctor/workflow validation/source check 通过；Claude 配置未变且同日 Read probe 已通过；Codex 最小 health 通过；fresh workspace capture/verify 通过后才调用业务 provider。
- How precondition is checked before run: 原有 Doctor/validate/check/capture/verify 命令及 SHA 复算；source 网络检查直接执行原有 curl 脚本，不新增网络检测器。模型从用户配置读取，不固定版本。
- PASS: 完整产品运行、离线测试和 opt-in live 验证通过；每个下游消费已批准身份；审批有证据和理由；无未处置 blocker/越权副作用/伪完整报告。
- FAIL: 有效运行中只读 profile 出现成功 Bash、其他越权副作用、authority 漂移、产品验收失败或止损触发。自然合法 NEEDS_REVISION 不直接失败，按声明路径完成修订。
- INVALID: 身份/环境/前置漂移、旧样本、手改 ledger、运行中修改输入或 harness、人为制造缺陷。quota 单独暂停，通知用户切换后端，保留事实再判断合法恢复，不自动当作产品失败。
- Stop-loss: 每段结束核对真实 tool results 与当前状态再审批；只读成功 Bash 或部分 durable 状态立即停止；同 blocker 无新证据连续两轮停止；不因总耗时单独判失败，观察模型/进程进展。修复不得通过扩 scope、授权 Bash 或暗改旧 ledger 绕过。
- Run artifact freshness: 下列 JAR SHA + 已提交代码祖先绑定；新 session/run/policy identity；每次运行前复核配置；只读节点 policy 必须 false + managed Bash deny，未调用 Bash不要求人为触发。

## Frozen Identity

- Code checkpoint: e21c95d；HEAD/tree 是起草引用，文档提交不使代码无效；按祖先关系、production diff 为空及 JAR SHA 判定。
- JAR: `8755281dc3919d3f54d6c50d552c3d82f4546af4ab31affdda89eb87aabe9a8a`。
- Workflow: `d56663c04dd954d349ac0423f107ab180239cff8d9322bb12fa9c50074c002a7`，28 nodes / 47 edges / 8 agent_run / 6 gates。
- Target HEAD/tree: `c84807946493a603c789ee40c6a4c615aa846c3b` / `81520ac179f17c35c7c358c2a02102007cbe7cba`。
- REQUIREMENT: `9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a`。
- scripts/verify-workspace.sh: `c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23`。
- Fork upstream/patch/schema: fd173f04 / `4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e` / v2；Claude ACP 0.70.0，Codex ACP 1.10.0。
- Claude 配置观测更新：open.bigmodel.cn；Sonnet/Opus=glm-5.3，Haiku=glm-4.7-air，ANTHROPIC_MODEL/CLAUDE_CODE_MODEL 未设置。凭据只记 presence；Codex 使用 `/Users/kriswang/.codex` 的当前配置。
- 已完成离线验证：Dual 1426/0/0/0，fork 991 PASS；不冒充完整自然 E2E。

## 执行与闭环

Declared by 需求/定义/批准；Proven by typed 工件与 Git；Consumed by 原 Gate 完整转移表的真实下游；
Enforced before 发布/路由/外部副作用；Failure route 为本页 FAIL/INVALID；Cannot-bypass 由既有序列测试
与运行中 sentinel 共同覆盖。未自然触发的修复/拒绝路径必须记 NOT EXERCISED。

统一 CODEX_HOME=/Users/kriswang/.codex、DUAL_ACPX_EXECUTABLE=本仓 .tools/acpx-e1/bin/acpx、
DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=0。start 不带 --run；capture 使用 effective workspace 的绝对 check 路径，
verify 后分段 run。用户已授权主 agent 逐门审阅并作合法决定；不使用 autorun，实质新需求或额外风险才请用户裁定。
交付止于 effective workspace 与 accepted candidate，不 apply/commit/finalize 原 target。
证据目录：output/real-cases/20260909-okx-profile-aligned-e2e。

## 配置变化前置补充（业务 start 前）

前置核验发现用户配置已由 DeepSeek 切到上述 GLM 映射；不沿用旧 Read 结果。
按既有换模型授权，用已冻结的 DeepSeekReadProbe.java（名称为历史命名，代码无模型分支）
在当前 production classes/新 policy/fork 上执行一次纯 Read nonce 检查，不请求 Bash。
该项 Target=Read nonce往返；SoT=实际工具及结果/transport/closure；PASS=恰一Read、nonce匹配、
exit0、零active信号、session关闭；失败停止前置不start，quota通知用户，无自动重跑。
不新增权限探针/模型层。Codex无工具health已退出0/end_turn，响应模型gpt-6-astra，待归档机械核验。

## 执行记录

- 文档 checkpoint：402b829 / 7dc8b33；production checkpoint 仍为 e21c95d，JAR 未再生。
- 前置通过：Doctor、冻结身份复算、source check、当前 GLM 单 Read nonce、Codex 无工具 health、effective workspace capture/verify。
- 唯一 fresh 样本：`ws-20260909T113923Z-FZ8K`；旧样本未恢复。
- intake run `run-1788954058404` 完成，4 Read / 0 Bash / 0 write；产品评审 run `run-1788954332029` 完成，READY_FOR_PRODUCT_REVIEW，无 must_fix。
- 主 agent 阅读完整 proposal/review sidecar 后，按用户委托批准 requirement-006 与 contract-008；未变更范围或需求。proposal 绑定及其八个 artifacts 的 SHA 复算一致。
- 初版设计 run `run-1788954529942` 完成，8 Read + 1 Glob、0 Bash/写入；评审提出数据库失败状态、关闭同步状态、前端行为测试三项 Must Fix，正常路由至 design gate-013。
- 按用户委托 revise 后，替换设计 run `run-1788955265925` 与复审 `run-1788955656723` 完成；四项活动义务（含 human note）均有回应，复审 READY、must_fix 为空；主 agent 阅读完整替换设计及复审后批准 design gate-019。该修订无 Bash/写入，未扩大文件范围。
- 计划 run `run-1788955786549` 完成，3 Read + 2 Glob、0 Bash/写入。主 agent 全文核对后发现 rollback_notes 含分批提交与 reset/clean，已在 plan gate-021 提交唯一范围内 revise：删除实现阶段 Git 写入/清理的隐含授权，回滚仅保留为需 operator 另行授权的人工动作；其余设计/范围/测试不变。
- 计划修订 run `run-1788956508122` 完成，2 Read + 2 Glob、0 Bash/写入。主 agent 比对 typed plan：rollback_notes 已消除 agent 的 Git 写入/清理授权；其他实施步骤、路径、验证要求不变；file_level_changes 仅有“精度”→“精度保持”的同义增字，因此不采信其“其他字段逐字一致”的过强自述，但无实质范围变动。
- 已按委托批准 plan gate-024，启动实现 `node-implement-claude_implement-026` / `run-1788956758608`；本次继续前复核 JAR/fork/需求/check SHA 与 GLM 配置均未变化。
- 实现实际成功写入未批准的 OkxHttpException.java / OkxParseException.java；按本页成功越界副作用项 cancel 并关闭 session，原 runner 已退出。Gate FAIL，workflow WAITING_FOR_RECOVERY / evt-000027，完整 E2E NOT PROVEN；未重开旧样本或创建第二样本。
- 取消后 AGENT_RESULT_MISSING 不另归因模型 JSON 缺陷；后置 scope 校验尚未执行，不冒称其失效。详见 `../real-case-reports/20260909-okx-fz8k-implementation-boundary-stop.md`，证据位于本轮 output 下 boundary-stop/。
