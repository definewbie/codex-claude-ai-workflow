# OKX Fresh E2E After Acceptance Repair

状态：SOURCE PREFLIGHT PASS / USER AUTHORIZED，workspace capture/verify后激活业务。2026-09-09。由主agent起草和自查，不冒称独立评审。

## 目标与非目标

唯一fresh样本完成OKX产品需求和accepted candidate；同时观察批次A/B在真实消费者中的组合效果。
不是再造模型兼容层或重新验证完整网络安全。不恢复SPM9等旧样本，不人为制造合同缺口。
正常、修复、再批准中自然未触发的路径记NOT EXERCISED，不靠增加样本凑覆盖。

产品以目标REQUIREMENT.md为准，采纳用户取消手机图标工作的决定：不增加移动端图标、
PWA、favicon或额外跨设备验收；现有需求中的基本布局可用性不扩张为专门设计项目。
README、运行、数据库、真实图表、自动刷新、默认离线测试与opt-in真实OKX smoke是验收主体。
如需求与用户决定仍存在实质冲突，须在初始需求批准前解决，不在运行中偷偷换输入。

## Verification Gate

- Target: 完整产品验收、workflow COMPLETED、completion audit ready、有效accepted candidate同时成立。
- Source of truth: 原需求、成功execution/validated sidecar、直接source lineage、gate/iteration/events、qualified contract、Git bytes、测试XML、实际应用/API/浏览器证据。
- Trigger precondition: 当前生产变更收口为可追溯clean checkpoint；新JAR包含批次A/B；workflow validation/Doctor通过；Claude配置与已通过probe一致；同环境Codex最小健康检查通过；target和新workspace检查通过；初始无旧合同/diff/revision context。
- How precondition is checked before run: code/JAR/workflow/task/check/acpx/config身份归档；新JAR校验；既有workflow validate及workspace capture/verify；source侧只用原有curl检查，不开发网络检测器。未通过不得start业务。
- PASS: 产品需求可运行且测试/实时只读验证通过，实际批准身份被所有下游消费；合法代理审批逐门记录证据与理由；无未处置blocker、越权写入或伪完整报告。
- FAIL: 有效运行出现authority绕过、存储不一致、产品验收失败或停止条件。自然NEEDS_REVISION不自动失败，沿声明路径完成新作者及批准。
- INVALID: 旧JAR/配置漂移/前置失败、旧样本、手改ledger、运行中换需求或harness、伪造故障。quota单列为用户介入暂停，通知切换模型；先保留当前run事实，再评估合法resume/retry，不自动当成产品或harness缺陷。
- Stop-loss: 同blocker无新事实连续两轮、部分durable状态、错误副作用立即停；不因耗时单独判失败，持续观察进程及模型进展；不把semantic revise当execution retry。
- Run artifact freshness: clean source身份和JAR SHA绑定；每次run/session/config/批准/测试结果可追溯；切换模型必须显式分段归档，不能继续声称同一冻结配置PASS。

## 必查的完整转移

|转移|强制消费及失败出口|
|---|---|
|proposal→requirement approve→compile→contract approve|真实批准来源，packet展示的快照与compile SHA一致；不符不得发布审批包或启动下游|
|approved contract→design/plan→implement|qualified contract和已批准作者版本进入实际输入，scope不靠正文猜测|
|self-test→acceptance→分类|基于typed类别选择真实修复节点，产品不符合现有合同不伪装为合同空缺；混合问题按已实现规则处理|
|自然合同返工→新proposal→两次批准→下游|历史批准作为修订基线，不跳过proposal；compile后新合同未批准不得消费；批准后只消费新身份|
|任何修订→下一作者/审批|最新note/证据实际可见；累计预算由既有ledger解释，不能靠新iteration无限重置|
|acceptance→delivery→terminal|scope与测试独立证明，candidate绑定被接受bytes，报告不替代audit|

Declared by需求/定义/批准；Proven bytyped工件与Git；Consumed by真实下游；Enforced before
发布/路由/调用；Failure route按上表与FAIL/INVALID；Cannot-bypass由批次A/B既有序列、损坏、
拒绝、非空条款修订测试支撑，本样本仅报告自然发生的实证。

## 身份快照与待办

- 新JAR SHA: fb6dbcabb5f1d3aa2839d3489ddad1b3c0e03e7289d31a9090623ea63fb8ef3e。
- small workflow SHA: d56663c04dd954d349ac0423f107ab180239cff8d9322bb12fa9c50074c002a7。
- target HEAD/tree: c84807946493a603c789ee40c6a4c615aa846c3b / 81520ac179f17c35c7c358c2a02102007cbe7cba，当前clean。
- Claude endpoint/model配置观测: api.deepseek.com / DeepSeek-V4-Pro；ACP0.70.0，经无工具health和production Read检查通过。
- acpx upstream/patch/schema: fd173f04aa1b56f9e3f5ca5190c034ddcae28792 / 2313fe2d53e573dc0227a453fd1c4cbe6107373472a2027bb7ed3e2952038b63 / claude_execution_policy.v2。
- CODEX_HOME使用/Users/kriswang/.codex，模型从其配置读取，不固定gpt版本；本轮health为exit0/end_turn/精确JSON/零工具，ACP实际回报gpt-6-astra。
- 执行环境/Phase A提交3f1f157；验收返工A/B提交ee72c654e794e16db4d2c75f3c2b6b08dddff904，code tree ad6f066f212b084563d3da4b79eb09452dd2e04d。之后仅允许本文等文档提交；production tracked clean。未跟踪旧capture、output、旧Gate不属于运行源码，不删除或冒充提交。
- 1425/0/0/0为9月8日最终回归；本次package成功和Doctor OK不新增测试证明。
- REQUIREMENT SHA 9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a；check SHA c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23。source check本轮exit0，workflow validate为28nodes/47edges。
- 本轮证据目录output/real-cases/20260909-okx-fresh-acceptance-repair；DeepSeek证据沿用同日preflight目录，不额外消耗第三次Claude probe。

## 顺序与权限

先按既有checkpoint范围收口并冻结source（不把无关文件一并提交），补齐Codex和workspace前置；
Gate通过后start不带run，capture/verify再运行。用户已授权本agent逐门审阅并作合法决定，
不使用autorun，不启动额外agent；新增需求、额外风险或无法合法解释的故障才请用户裁定。
交付止于effective workspace验收及accepted candidate，不apply/commit/finalize原target。
所有历史Gate与事故样本保留，本文不授权修复旧ledger。

统一环境：CODEX_HOME=/Users/kriswang/.codex；DUAL_ACPX_EXECUTABLE为本仓.tools/acpx-e1/bin/acpx；
DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=0（无总时长deadline；既有progress/fatal判据不变）。
每次业务调用前不覆盖模型设置，settings配置变化即暂停并重新绑定事实。两端模型切换无需重复
请求授权，但不允许掩盖配置变化。已授权代理审批不自动授权扩大scope、绕过工具拒绝或事故retry。
