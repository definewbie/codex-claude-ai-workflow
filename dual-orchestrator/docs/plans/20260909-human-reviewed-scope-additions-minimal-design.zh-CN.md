# 人工审议新增文件：最小完整设计

状态：IMPLEMENTED / SELF-REVIEW PASS。完整批准续行序列与最终完整构建通过。主 agent 自查，不冒称独立评审；无 provider 或样本恢复。
目标：P0。普通文件范围偏离不取消整轮；人可要求收回，也可发起具体新增路径的合法批准，
批准后 Git 校验与交付消费同一个有效合同。仅“不取消 + 修回原范围”不构成本目标完成。

## 1. 核实事实与必要范围

- ArtifactValidateHandler 已产生 immutable change-scope evaluation，违规沿声明边进入人工修复门。
- ChangeScopeViolationRoutingSemantics 当前强制该门 revise-only，并只回 implementation context。
- HumanReviewGateHandler 验证 evaluation 的 producer、iteration、ref/SHA 后展示差异；不授予 scope。
- 既有 acceptance 合同返工已支持 proposal patch -> product review -> requirement approval -> compile
  -> contract approval -> design/plan -> implementation，不需要新建批准文档或第二合同。
- 该返工目前只接受限定 acceptance trigger，不能把 scope gate 伪装成 acceptance REJECT。
- 既有设计明确排除已落盘的新 addition；当前 CREATE 校验拒绝已存在路径。
  操作类型必须与原 workstream base 的累计 Git 差异一致，不能因当前文件存在就改成 MODIFY。

## 2. 方案选择

不选：全目录默认授权、模型自判合理即放行、修复门 note 直接覆盖合同、另加 scope grant 白名单、
只放宽 Git validator、把 scope mismatch 伪造为 contract gap、逐文件 sandbox。

首选：在现有 scope repair gate 增加显式的“发起范围修订”分支，复用既有合同提案与批准链。
它不是原地 approve，也不是已经授予新 scope。用户最终批准的对象是明确列出新增路径的修订合同。
修回原范围仍走原 revise 分支。新分支只在可信 scope evaluation 的门上可用，不能暴露到
普通 self-test/revision-limit repair 或 autorun；CLI/API/UI 只消费统一 gate action，不能自行推断。

人工分支 action 固定为 `request_scope_revision`，含义是发起审议，不是批准新增文件。
不得为省一次明确 action，解析 note 中“同意保留”等词选择路径。

## 3. 最小批准闭环

```text
implementation 正常结束 -> Git scope FAIL -> scope repair gate
  revise + note -> 原范围修复 -> Git 再校验
  发起范围修订 + note -> 有限 proposal patch -> product review
    -> requirement approve -> compile -> contract approve
    -> 新合同身份下 design/plan -> implementation/verification -> Git 再校验 -> acceptance/candidate
```

为避免再引入批准 owner，当前方案保留既有两道不同 subject 的批准。
不承诺“一次点击立即回实现”；若消除这些步骤需要额外授权模型，本批不做。
最终交付仍批准真实 candidate bytes；合同范围批准不代替代码质量验收。

新增路径候选只来自该门绑定的 scope evaluation 中的普通新增文件，不允许从自由文本猜路径。
本批仅解决原 base 不存在的新增源文件；删除/重命名、保护区、工作区外、symlink 逃逸不得借此授权。
提案不得顺带修改 objective、旧条款、依赖或其它 scope；人审核具体差异，不能靠 agent 自述自证。
是否需要让人选择候选子集：优先复用现有有限 patch 表达与合同批准，无新增 UI selector。

## 4. 同一事实与基线

有效 scope 的唯一 owner 仍是 resolved contract gate + compile lineage，不是 repair gate。
返工源固定原批准提案/合同身份与 scope evaluation ref/SHA；复用既有 machine-owned result/ref 模式，
不新增独立授权 artifact。批准前 scope 不变，设计/计划不允许提前消费候选。

已落盘 addition 的 CREATE 特例必须同时满足：可信 scope-review 返工来源、原 workstream base 下
路径不存在、绑定 evaluation 指认该新增路径、canonical/保护区检查通过；不能全局放宽 CREATE。
文件内容本身可以在后续实现中修复；路径/operation 授权不是批准当时 bytes，最终 bytes 归 candidate。
proposal publication、compile 与实际 Git evaluation 必须同一基线语义；不能只修 merger。
任何后续文件或路径变化均重新评价，旧 evaluation 不能授权未列出的另一文件。

## 5. Transition 与失败切面

| 前态 / 触发 | 写入与后态 | 失败、重启与拒绝 |
|---|---|---|
| implementation 完成 | 既有 Git evaluation 发布 -> route -> open gate | evaluation 缺失/损坏不得批准；不靠 console 内容补建 |
| open scope gate / revise | note 与 gate 决定 -> 新 implementation iteration | 现有语义保持；无 scope 增量 |
| open scope gate / 范围修订 | 先核实来源和基线 -> 既有 gate resolution/iteration -> proposal context | 验证失败在 mutation 前拒绝；持久写之间失败沿既有 fail-closed，不宣称自动恢复 |
| proposal 待批 | 既有 review -> requirement approval -> compile | 未批准、旧 proposal、缺引用不可编译；冷读只沿固定 lineage |
| compile 待批 | contract approval -> 新合同 epoch 的设计/计划 | 不复用旧 epoch 的批准；中途失败不得跳回旧 plan 继续 |
| 新实现完成 | 再次 Git 校验 -> 验收 -> candidate | 未批准路径仍拒绝；候选 bytes 仍独立绑定 |

每个 durable-write 间切片须在实施测试中明确验证残留状态与禁止副作用；不借此重写 ledger 原子性。
旧 FZ8K 已取消且缺最终输出，不作为新分支的合法 open scope gate；不手改、不原地 rescue。

## 6. 实施前锁定与验收

下一步一次只读 sweep 锁定：gate action 的实例适用性与三端/autorun；四 definition 与 loader；
现有返工 baseline 捕获/ancestry resolver 的最小复用；scope operation resolver/publication/compile
的共享规则；新合同 epoch 下 authored consumer 与 Git/candidate 的保持性测试。
若必须新增平行批准、任意图跳转或整套 repair 平台，则停止该方案，不隐性扩张。

必须通过的序列：
1. 普通额外文件 -> 人工要求收回 -> 再校验 PASS。
2. 普通额外文件 -> 范围修订 -> 实际已存在的文件以 CREATE 批准 -> 新设计/计划 -> Git PASS -> candidate。
3. 未批准/拒绝/候选新加第三路径/篡改 evaluation/错基线均不得推进。
4. 初始 CREATE 已存在仍拒绝；旧 inherited CREATE 保持；保护区和其它 operation 不获本例外。
5. contract 待批不能使用旧 plan；批准变更、冷 JVM 重建后所有消费者同一 identity。
6. 普通 repair/四 workflow/custom definition/CLI/API/UI/autorun sibling，无隐式放权。

Declared by 合同与明确人工 action；Proven by evaluation + proposal/approval lineage；Consumed by
唯一 qualified contract resolver；Enforced before 下游设计/执行/交付；Failure route 为既有拒绝及
人工修复；Cannot-bypass 由上述完整序列证明。当前仅设计，不宣称 ENFORCED 或 REAL-CASE PROVEN。

## 7. 接线 sweep（2026-09-09）

本节为代码核对结果，非实现完成声明；这些离线核对无需再次向用户申请。

| 实际接线 | 必要改动与禁止项 |
|---|---|
| HumanGateActionSemantics 与 RoutingEngine.resolveOpenGate 目前只按 definition 判断动作 | 同一实例动作规则供 packet/ledger 消费：新 action 仅限 implementation repair + scope violation 来源；ledger 重验，不能只隐藏按钮 |
| ChangeScopeViolationRoutingSemantics 强制 revise-only | 增加唯一明确新边到已有 intake context，原 revise 回实现边保持 |
| WorkflowDefinitionLoader.validateContractRepairEdges 只允许 acceptance 两类返工 | 有界加入新的人工作用源；不全局允许 post-contract 回任意 negotiation 节点 |
| CLI decide 接受字符串 action；server resolveGate 是共享私有入口 | CLI 复用；server 新操作委托同一 ledger，不直接写合同或重映射 approve |
| console.allowedActions 对 WAITING_FOR_HUMAN 固定 approve/revise | 消费真实 gate 动作；新动作显示“申请保留新增文件”，不显示为已经批准；同时核对 mgt |
| AutoRunCommand.gateActionFor 仅 approve/complete | 保持不能自动发起范围修订，测试钉住 |
| LateReader 绑定 acceptance_rework_baseline 与 acceptance obligations | 复用批准基线验证，增加有限 scope gate 根；不能伪造 acceptance sidecar/category |
| Context.lateRoot 被 prompt/context/packet/publication/compile 消费 | 同批处理，scope 来源展示为 scope review，不保留 acceptance 错误标签或漏校验 |
| ScopeResolver、ProposalValidator、Merger 与 publication/compile | 仅对有来源证据的新路径允许已落盘 CREATE；原 base 决定 operation，测试走到 Git evaluation |

批准基线须在原 qualified contract 可验证时冻结，复用既有 machine-owned result；后续沿固定 refs
冷重建，不等新 compile 覆盖稳定文件后再找旧合同。冻结落点与写入次序在实施中唯一化并测试，
不新增双写 SoT。新 action 不隐式获得无限 revision budget；须验证拒绝时不留下已 resolved gate
却无合法 successor 的混合状态，不借机重写 ledger 原子性。

生产范围为 core action/definition guard、ledger gate guard、runtime 根引用与 scope 校验、
四 definition、CLI/server/UI 同一入口和测试。不动 provider/fork/sandbox、合同 schema、candidate
格式或产品代码。这个范围服务一个完整目标，不能只放宽 validator 冒称完成。

本轮独立于 provider 的基线复跑：10 个测试类，ledger 49 + runtime 40 = 89 项，
0 failure / 0 error / 0 skipped，BUILD SUCCESS。覆盖既有返工序列、写入中断、冷读上下文、
晚期 scope、merger、Git evaluation、evidence 与 definition；这些是既有能力的保持基线，
不是新人工分支通过的证据。新分支尚未实施。

## 8. 第一批实现：实例动作守卫

已开始 production 修改，尚不修改 definition、不暴露新按钮或启动入口。
HumanGateActionSemantics 新增 source-outcome 约束重载；RoutingEngine 创建 GateDocument、
HumanReviewGateHandler 生成 packet 与 ledger 接收 request_scope_revision 时消费同一规则。
普通 self-test/BLOCKED 等修复不提供该动作；ledger 对不适用来源、缺声明边及空 note 在任何写入前拒绝。
原 approve/revise 行为保持，不扩大普通 gate action 校验的历史语义。

反证先运行：新增 ledger 测试在旧实现上失败（应抛 IOException 实际未抛），接入守卫后通过。
测试对拒绝前后整个 workstream 文件内容与文件集合做相等断言，覆盖 gate/node/current/events/iteration，
不是仅验证异常消息。GateDocument 的动作列表同时验证。

此批只证明动作适用性守卫，不证明 scope evaluation 的新增批准基线、CREATE 例外或合法续行。
后续必须接完第 7 节后才能启用 bundled definitions；不得把本批作为用户目标完成或 real-case 恢复依据。
尚未提交；最终完整 checkpoint 再统一自查与收口。

验证：聚焦四类 124 项通过；最终完整 reactor 在 22:18–22:23 运行，171 份本次 XML 聚合
1428/0/0/0，BUILD SUCCESS，git diff --check clean。首次受限环境运行因进程恢复测试
`Operation not permitted (sysctl failed)` 失败；未改代码/测试，允许测试枚举自身进程后完整重跑通过。
生产修改仅 core semantics、ledger routing、runtime packet 三处；两个测试文件新增反证。
无 workflow/权限/产品修改。自查不冒称独立评审；完整批准续行尚未实现，不能发布新能力。

## 9. 第二批实现：证据限定的 CREATE 候选

在现有 TaskContractScopeResolver 内增加候选提取，不增加 artifact、schema 或第二批准来源。
输入是 occurrence evaluation 与原 Git base；消费时重新读取并核对 ref/SHA，不信任先前缓存的 Verified。
仅接受 UNEXPECTED_ACTUAL_CHANGE + CREATE，且 actualChanges 恰有同路径同操作记录；
原 base 已有路径、重复/混合其它违规、缺文件、目录、保护路径与任意路径层级的 symlink 均拒绝。
返回的候选保持 CREATE；后来工作区多出的其它文件不进入候选集。

这是候选资格检查（VALIDATED），不是合同批准（非 ENFORCED）。生产调用链尚未接入该方法；
必须由后续可信 scope gate 根验证 producer/iteration、原批准合同和 workspace base 后调用，
不能拿任意已校验 SHA 的 artifact 或任意 Git commit 当作 gate authority。
初始提案、原 late-revision resolver 与 proposal validator 没有放宽，候选不能自行成为有效 scope。
scope.excluded 与继承合同限制仍须由后续 publication/compile 原校验链守住，不由候选取代。

新增五项测试覆盖三类无业务特判文件、候选不等于批准、基线变更、证据删除/篡改、
目录/软链接/保护区、actual/violation 不一致、后加第三文件与混合违规。
本批聚焦复跑七类 35/0/0/0（含既有 acceptance repair 正常序列、写入中断及冷读上下文），
BUILD SUCCESS；diff-check clean。未对本批重跑全 reactor，不复用上一批 1428 作为当前完整证据。

下一处接线是 scope gate 的原合同 baseline 与来源固定，然后同时处理 merger/publication/compile；
不能只给 resolver 传路径集合后启用 definition。完整第 6 节序列通过前，不提交为完成能力、不启动 real-case。

## 10. 完整接线与自查（覆盖前两批未完成状态）

基线冻结在 ArtifactValidateHandler 的 completed CHANGE_SCOPE_VIOLATION result 中，与 occurrence
evaluation 同源；不是 gate handler 的临时返回值。核对发现 runtime 对 gate open 的 NodeResult
不持久化，故不能把事实落在那里。新 scope_rework_baseline 复用原批准 proposal/review/两 gate/
compile/workspace base 的 ref/SHA，不增加独立 artifact 或授权白名单。

完整链为：validation result -> source-aware gate action -> ledger 变更前完整性守卫 -> 原合同
返工 resolver -> evaluation 限定 CREATE candidates -> merger/publication/compile 同一限制 ->
两 gate 批准 -> 新合同 epoch 的设计/计划/实现 -> Git PASS -> 实际 delivery candidate。
CLI复用 review decide；server 与两个 UI 委托同一 ledger。四 definition 声明有限入口，loader
拒绝错源/错目标；autorun原 approve/complete 规则保持，不能发起新动作。

序列测试实际走到 human acceptance，subject 含 DeliveryPrepareHandler 生成的 candidate，
不是伪造文件名：批准前旧合同不变，compile 后新合同未批准不可解析，批准后 helper.py 保持
相对于原 base 的 CREATE，重新设计/计划/实施才进入交付。普通 revise 删除额外文件路线同测。
篡改 evaluation 时整目录字节/文件集合零变化，冷 ledger 仍为 WAITING_FOR_HUMAN；提案增加
第三路径或改旧条款时在 publication 前拒绝，没有新实现副作用。空字段返回校验错误而非空指针。
旧 acceptance 合同返工、初始 CREATE、非执行 profile 与 candidate bytes authority 不变。

失败切面：validation artifacts 先于 source node 发布，中断只留下无合法 completed source 的工件，
不能请求授权；ledger 在 gate writes 前重验来源/哈希/预算，失败保持 open；进入新 iteration 后
解析失败走既有 recovery，不改旧合同；compile 到 contract approval 之间 qualified resolver 拒绝。
不声明 ledger 多文件事务的 R1 crash liveness，不加历史 FZ8K rescue。

通用性自查：新增 production 无 OKX、Kline、异常类文件名特判；候选测试含 Markdown/Java/Python。
文件职责是否合理由 reviewer/人工在完整合同批准中判断，路径限定只是本次审议的边界。
没有第二批准者；模型 proposal 仍是待批意图，只有既有 contract gate + compile lineage 授权。
UI 的同门/过期门/终态能力投影由 scripts/scope-review-ui.test.mjs 执行真实函数证明，已 PASS。
截至本节，完整 reactor 正在重跑；不得引用前两批测试计数作为最终构建结果。

最终构建于 2026-09-09 23:09 完成：mvn package BUILD SUCCESS，171 份 XML 聚合
1440/0/0/0；两端 JavaScript 语法检查、UI 动作脚本、diff-check 均通过。Doctor OK。
JAR SHA：05a4b1f60008cfc37e2c19f607eedf44b6f0539955f3b0861f73f1b163886e1d。
人工审议新增文件完整链 DETERMINISTIC ENFORCED；NATURAL E2E NOT PROVEN。
下一步由新 fresh Gate 实测整体产品交付，不要求人为触发范围修订。
