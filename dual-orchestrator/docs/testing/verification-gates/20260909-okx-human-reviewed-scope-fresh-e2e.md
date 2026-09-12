# OKX Fresh E2E With Human-Reviewed Scope Additions

状态：FAIL / EVIDENCE-COLLECTION CONTINUATION GAP（2026-09-10）。继续执行后验收已无源码 must-fix，但补证据失败第二跳重新进入实现，已在零工具调用时取消止损；workflow 为 WAITING_FOR_RECOVERY，非 E2E 完成。此前设计阶段原 FAIL 裁定的撤回保持有效，本次为新观察到的路由缺口。唯一样本 BXWV；完整事实及最终 §21 详见
../real-case-reports/20260909-okx-bxwv-e2e.md。不启动第二样本，不改变冻结工件或验收标准。
主 agent 自查；用户已授权执行到 E2E 完成或缺陷止损，不启动额外 agent。

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

## 2026-09-10 复用执行：人工补证据源码基准

本节为用户再次授权后的新执行记录，复用上述产品目标、PASS/FAIL/INVALID、权限和止损规则。
开头 FAIL 与原冻结身份只属于 BXWV 历史样本，不恢复、不覆盖其证据；本轮允许一个新的 fresh 样本。
不重做 Gate，不新增验证平台、不启动额外 agent。状态：FAIL / REQUIRED EXTERNAL EVIDENCE CONTINUATION NOT IMPLEMENTED（2026-09-11）。唯一样本245D停在human repair gate-050，must_fix为空，但沙箱无法启动Chromium，必需的submit_evidence链未实现，按本节约定止损；workflow仍WAITING_FOR_HUMAN，非COMPLETED。详见[执行记录](../real-case-reports/20260910-okx-evidence-continuation-e2e.md)。源码基准分支NOT EXERCISED，不提升其自然验证成熟度。

唯一新增分支判据：若自然进入 collect_evidence，人工批准时必须持久化源码基准；
续作前后与发布前均强制复验，漂移不得进入验收。再次补采继承原基准；
未触发记 NOT EXERCISED，不人为制造。submit_evidence 未实现，若必须使用则停止，不手改账本。

本轮身份替代旧运行值：
- code checkpoint：88107d4；全 reactor 1465/0/0/0，随后离线 package 成功。
- JAR SHA：3000796d67b9ee64675dbe834b269a9612b664515cd6877cc0198823e419d878。
- external small workflow：dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json；SHA f9a29ddc9967b89d25e26f4311dfca39679dd5baab4bc336d7e21985f25b88d7，28 nodes / 50 edges，CLI validation PASS。不切换为 34 节点 bundled full/contract。
- target/requirement/check 与上次保持一致，原 source check 本次 PASS；effective workspace 仍须新 capture/verify。
- reviewed fork/schema、Claude GLM-5.3 与 Codex gpt-6-astra 配置未变，Doctor OK；本次重新运行最小 health 并关闭 session，不扩大探针。
- 三环境变量沿用；timeout=0，不以耗时作为失败。只提交本轮代码和相关文档，其他未提交文档/输出不参与执行身份。
- 本轮证据目录：output/real-cases/20260910-okx-evidence-continuation-e2e。启动前后记录实际 workstream 与结论，不预填成功。

## 2026-09-12 复用执行：外部材料完整续作

状态：FAIL / AUTHORED_RESPONSE_ID_INVALID。全部前置通过后启动唯一样本 `ws-20260911T231749Z-GCTW`（UTC命名，本地2026-09-12），第三次设计修订中模型将human-note身份的64位SHA抄为65位，发布前正确拒绝；账本WAITING_FOR_RECOVERY / evt-000038，未进入实现，无自动correction或retry。材料自然分支NOT EXERCISED，NATURAL E2E NOT PROVEN；完整证据与归因见[本轮报告](../real-case-reports/20260912-okx-external-evidence-e2e.md)。不创建第二样本，245D、BXWV及更早样本保持只读。复用本页主目标、产品验收、授权和止损规则；不新建 Gate，不启动额外 agent。之前 FAIL 与“submit_evidence 未实现”的文字仅属于对应历史运行。

本次代码 checkpoint 为 `b197ce90`，独立 diff review 无 P0/P1；全模块1476/0/0/0，加强后的完整链聚焦15/0/0/0。JAR已重新打包、两个新CLI入口检查通过。

- JAR SHA：`c8584379a5115b7e2249da4ac57d4452084162349ed72fbb5aaee961e8aebfe1`。
- external small workflow仍为原路径，SHA `3ea9ea6c567608f4593bfef65f5868d7cbbd1be0925315c650abd8ec3f1896d5`；28 nodes / 51 edges / 8 agent_run / 6 gates，新增边仅为submit_evidence到既有self-test。
- target HEAD/tree、REQUIREMENT/check SHA沿上次冻结值，当前只读复算一致且target clean。原verify-workspace是空项目启动前检查（含pom/src不存在），只在source/effective workspace开工前运行，不拿它拦截实现后正常脏工作树，也不要求为它提前提交产品。
- reviewed fork `4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e` / v2；Doctor OK；adapter固定Claude ACP0.70.0、Codex ACP1.10.0。当前配置为Claude GLM-5.3、Codex gpt-6-astra，仍从配置读取，不修改或写死模型。
- 三环境变量同上，timeout=0；运行前各一次既有最小health并关闭session，不扩展探针。配置身份与运行完成事实分别记录，不输出凭据。
- 执行证据目录：`output/real-cases/20260912-okx-external-evidence-e2e`。文档后代不构成代码漂移；执行前核对ancestry、零production diff和JAR字节。

必需出口逐项核对：默认测试由agent执行；若浏览器/公网只在宿主可用，使用已经实现的prepare/submit路径；依赖获取仍使用既有Phase A；源码/合同缺陷走既有typed修复。宿主能力检查仅限需求必需且尚未证明的启动/连通性，不建立新矩阵。开始前各出口必须可执行，否则保留前置失败而不消耗fresh样本。

自然进入材料分支时：读当前typed gate -> prepare并保存源码/合同基准 -> 在同一工作区做真实验证（日志/XML/截图写非源码目录）-> submit复制材料并绑定ref/SHA -> self-test核验 -> context -> acceptance引用同一manifest及材料ref。外部声明不自动变成测试通过；失败材料如实处理，缺证据且无must_fix不得回实现。任何源码或材料漂移必须拒绝，不能重抓基准洗掉变化。未触发的分支记NOT EXERCISED，不人为制造。

执行顺序：源端身份/check、CLI定义验证、必要环境可用性及最小health全通过 -> start不带run -> 新workspace capture/verify -> 分段run及逐门审阅 -> 按原PASS/FAIL/INVALID归档。quota按用户要求暂停并提示切换，不自动重试；其余缺陷止损后不改输入重跑、不创建第二样本。本节不授权原target apply/commit/push，交付仍止于accepted candidate。
