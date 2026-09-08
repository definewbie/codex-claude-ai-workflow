# Harness 漏评复盘与同边界缺口评估

日期：2026-09-07。基线：6784b662851eb82ff7b0397ba656ee70089a854a + 当前未提交工作树。
性质：评估，不是实施计划；未修改 production、workflow 或 SPM9 ledger，未调用 provider。
SPM9 保持 evt71 / contract.compile070 READY，未执行该节点。本轮不开发 B、不建立新样本。

勘误（同日设计复核）：F3撤回。此前只调用envelope validator，漏掉了publication前
AgentContractSemantics → StageOutcomeCompiler 的拒绝；不能据此断言正常成功链可绕过。
以下F3保留误判与纠正记录。当前为四类主流程缺口F1/F2/F4/F5，加F6投影错误。

## 1. 结论与范围

这次并非不可预见的新问题：旧 H2 复盘已认识“实现不符合合同”被误填 contract_gaps，
但修复只对 TEST_CODE_CHANGE 生效；其余形态和下一消费者前置没有合并验证。
责任在评估组织与实施决策，不应归咎于“模型总会犯错”或“原则还不够多”。

本轮覆盖输入证据/上下文 → typed review → classification → 四个 definition 的拒绝分支
→ 修复 producer/批准 → completion/candidate → human acceptance 的控制链；同时核对
反复拒绝的止损及旧评审证据。不是全仓逐行安全审计，不能报告“剩余缺陷总数”。

确认四类主流程缺口（F1/F2 含本次已发现的问题，F4/F5 为本轮新增确认；F3已撤回），
另有一处修复建议投影错误 F6。已知残余、操作失误和待验证风险另列，不混入计数。
两名独立只读 agent 分别核对拒绝 sibling/下游，以及外部证据/人工反馈入口；主线程
自行复核代码、历史文档、captured 数据并执行离线反例，不以 agent 结论代替证据。

## 2. 为什么会漏评

### 2.1 只封住事故字段组合，未审查完整问题空间

`docs/design/e2e-harness-boundary-retrospective.zh-CN.md` §3.2 及提交68c4f959
明确承认该误分类，最终把 TEST_CODE_CHANGE 提到规则1前。
当前 FailureArbiter:78-94 的注释仍写明这种风险，但一般生产代码、文档与混合补证据不在
该分支内。旧文档“该错误形态 PROVEN”有范围限定，不能扩大解释为所有验收分类闭合。

### 2.2 测试复述当前接线，未执行下一消费者

FullWorkflowDefinitionTest:107 的测试直接断言 CONTRACT_GAP → contract.compile。
ContractCompileHandlerTest 又独立证明没有批准proposal就拒绝，两类测试同时绿，
却没有测试把真实分类输出交给真实compile。
WorkflowRuntimeTest:77 的历史replay在 self-test 后继建立后结束，后继是 replay.stop，
也不能证明补证据、复审、最终交付会完成。

### 2.3 将安全拒绝与可继续完成混为一谈

E1 qualification正确阻止未经批准的合同被使用，但没有因此保证所有返工路径具备新批准。
producer-consumer sweep:130称 acceptance 回流“合法”，:320列clarify/recompile/reapprove，
然而入口缺批准producer没有被实跑。防绕过证明的是 safety，合法路径可达是另一项证明。
没有必要把scope扩大为平台；但不能拿小范围safety PASS代替整条返工路径的可达性。

### 2.4 六维表没有替代实际反例和差异检查

旧sweep:331-334覆盖多条implementation再入，重点是携带approved plan，不是分类是否选对
producer；分类→compile这条影响面没有作为未闭合项持续保留。评审范围跟着实现文件和
预填目标走，没有独立检验“产物能否满足下一个真实handler的输入要求”。

### 2.5 证据交接与范围核对也有 operator 责任

宿主43项测试及live1项已经归档在主仓库，但没有进入context066 input refs或后续human note。
gate059记录的是“由operator后续复核”，不是后来已完成的结果。让reviewer自行找到主仓库
另一目录的证据不合理。这是我的交接遗漏，不能直接推导“必须新增证据平台”。

也纠正上一条答复：当前REQUIREMENT.md:75与compiled contract确实写有移动端要求。
此次reviewer不是临时扩项。用户最新取消移动端应影响后续目标，但旧合同尚未被合法修订；
不能把聊天决定冒称已持久化的新批准，也不能篡改历史评审。此前需求过重与我未核实即归因
都需要承担责任。

## 3. 确认的缺口

### F1 / P1：缺陷类别与所需修复动作不完整，混合问题误分流

锚点：FailureClassifyHandler:90-96、FailureArbiter:81-129、AgentResultEnvelopeValidator:411-431。

- 真实SPM9：README错误 + missing_tests + EVIDENCE_COLLECTION + contract_gaps非空
  → CONTRACT_GAP。任意非空列表即触发，不验证“缺的是合同条款”还是“实现违反既有条款”。
- 扩展反例：即使不填contract_gaps，产品修复 + missing_tests + EVIDENCE_COLLECTION
  + category_hint=IMPLEMENTATION_BUG仍进入VERIFICATION_INSUFFICIENT。
- classifier不消费must_fix；enum仅描述验证补救，不完整描述产品代码/文档修复。
  因而只提高EVIDENCE_COLLECTION优先级仍不解决混合问题，甚至会跳过必要产品修复。
- 后果是选错返工producer、重复测试/拒绝或误改合同方向；不表示已绕过最终交付批准。

主线程对当前classes直接调用FailureArbiter，两个输出分别为CONTRACT_GAP与
VERIFICATION_INSUFFICIENT。使用通用README任务，无OKX路径/交易所逻辑。

### F2 / P1：合同缺口的声明边缺少提案与批准生产者

四个definition的CONTRACT_GAP与CONTRACT_AMBIGUITY均直接去contract.compile。
分类输出只有validated acceptance sidecar、classification、calibration与摘要；不是获批proposal。
ContractCompileHandler:255起的批准检查会在合同写入前拒绝。

这既影响误分类，也意味着真的需要改合同的拒绝不能靠这条边完成修订。
当前compile guard是正确的，不能放宽它或借旧批准解决。SPM9070实际input refs确认缺失。
CONTRACT_AMBIGUITY共享输入缺口，但本轮未证明当前合法initial proposal能触发该分支，
不额外计为一个可达事故。单验收条目反例已被proposal至少两项的上游守卫排除。

### F3 / 撤回：局部validator放行不等于正常发布成功

主线程离线构造合法AcceptanceReviewResult：ACCEPT_WITH_NOTES、blocking=true、
must_fix=[Required production fix remains]；validator返回valid=true。
锚点：AgentResultEnvelopeValidator:190-194/384-431；ReviewDecisionHandler:36-53。

此前从此局部探针跳到GoalSatisfactionWriter/report/candidate是错误推导，漏了中间守卫：
ArtifactContractValidator.withResultSidecars:306-314在写sidecar之前调用
AgentContractSemantics.validateOutputContract；其调用StageOutcomeCompiler:85-92，
对ACCEPT_WITH_NOTES + blocking/must_fix返回NOTES_STATUS_BLOCKING_CONFLICT。
正常publication不能产生该矛盾的成功结果。F3不计入已确认缺陷，不新增重复production校验；
后续只补/复跑真实发布链回归证明。此处也构成本轮复盘自己的漏评，必须明示而非静默删改。
勘误验证：同日主线程在/private/tmp构造该结果，调用真实ArtifactContractValidator.validate，
得到FAILED / STRUCTURED_ARTIFACT_SEMANTIC_VIOLATION / NOTES_STATUS_BLOCKING_CONFLICT。
既有ArtifactContractFixtureRegressionTest + StageOutcomeCompilerTest独立复跑8/0/0/0。
该临时反例未提交为production代码；后续补永久回归，不把既有8项称为新增反例覆盖。

### F4 / P1：预算裁剪循环不更新实际长度，过度删除人工反馈

ContextBuildPackageHandler:285-297每轮检查同一builder的token数，只修改dropped标志，
到循环结束才重建内容。因此一旦超额，会删除全部可删除section，而不是足额后停止。
FAIL/PRESERVE_FULL部分保留，普通HIGH human_feedback/review_evidence不受该保护。

captured context066：预算6500、最终4165，human_feedback available=true/476 tokens/
dropped=true；review_evidence亦被删。独立复核与代码机制一致，但不能据此声称缺失宿主
报告完全由此造成：那份报告本来就没有经本轮输入交接。
现有ContextBuildPackageHandlerTest:1308只检查完成和总字符上限，不验证必要内容保留。
应先修这个局部裁剪bug，而非再建设摘要/记忆平台。

### F5 / P1：反复acceptance拒绝没有持久累计止损

四个classifier config均只有requires_approved_task_contract，没有max_revisions。
RevisionTracker:195缺省-1；RoutingEngine:252-260据此不触发revision上限。
纯REVIEW_NOISE反复复审即可经过无人工gate的循环；implementation/selftest再入亦需审计。

Cli RunCommand:22-23缺省maxSteps=20是单次执行上限，非跨多次run的累计预算。
当前SPM9采用max-steps=1及operator止损没有失控消耗，不能外推为harness已强制累计限额。
此处记录配置/流程缺口，不主张新增成本平台或以执行时长自动判模型失败。

### F6 / P2：修复建议字段名不一致，prompt丢失建议

FailureClassification:22写suggested_action，ContextStateEvidenceBoundaryRenderer:116
读取suggestedAction；readJsonUnchecked直接读Map，不转换字段名。
captured classification确有snake_case键且camelCase不存在，故Suggested Action段落不会产生。
这不改变RoutingEngine的typed outcome，但使修复上下文缺信息；可与同一消费者修正合并，
无需新模型或新模块。

## 4. 各拒绝出口与已守住的边界

| 分类 | 下一链 | 本轮判断 |
|---|---|---|
| CONTRACT_GAP | compile | F2，缺新proposal approval |
| CONTRACT_AMBIGUITY | compile | 同输入缺口；当前合法触发未证明，不另算事故 |
| IMPLEMENTATION_BUG | implementation context → implement | 原合同/approved plan内修复可进入；不授予改合同/设计权限 |
| CONTEXT_INSUFFICIENT | implementation context → implement | 路由可进入，不证明缺的上下文已补齐 |
| TOOL_LIMITATION | implementation context → implement | 不会自动修好工具前提；零changed count不能证明工具原因 |
| VERIFICATION_INSUFFICIENT | selftest | 纯补证据可进入；混合问题见F1 |
| REVIEW_NOISE | acceptance context → review | 不直接通过，但缺累计止损见F5 |

批准合同/设计/计划的SHA与lineage守卫、scope拒绝、terminal禁止新派发、human gate后才
允许apply均未被本轮反例推翻。不能因这些缺口就宣布E1-E4全部无效。

## 5. 待验证、已知残余与非缺陷

- classification仍写全局stable JSON，context/report按固定路径读；changed-file计数也读
  全局changed-files及legacy fallback，不绑定当前review occurrence。删除/重写计数可改变
  TOOL_LIMITATION选择；正常串行最新态不一定错，冷重放/跨occurrence是否错配尚需sequence。
  此为可见风险，不在本轮伪装成已发生事故，也不立即新增binding平台。
- selftest在无typed permission signal的环境阻塞下可返回BLOCKED→implementation repair；
  环境已恢复但不需改代码时，是否存在最小续作出口尚未用slice证明。不能外推为所有quota/
  permission都错路由；本轮没有quota新反例。
- 已批准设计/计划本身需调整、或后期用户缩减合同范围：早期合法修订机制存在，当前晚期
  状态如何到达它未闭合；与F2一起评估，不强行用implementation note覆盖合同批准。
- R1多文件崩溃一致性是旧复盘§7显式记录的残余，不是本轮新发现；本轮未做故障注入。
- 外部证据已有human note → context以及selftest“运行或检查”入口；没有把文件归档到
  主仓库就自动注入产品agent，不是接口违约。先修正确交接和F4，不默认开发证据系统。
- 移动端要求过重属于需求/操作治理；用户最新收缩应记录，不能通过篡改冻结文件实现。

## 6. 验证与未验证项

本轮实际执行：

`mvn -o -pl dual-runtime -am -Dtest=FailureArbiterTest,FailureClassifyHandlerTest,FullWorkflowDefinitionTest,ContractCompileHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`

74/0/0/0，BUILD SUCCESS。这个绿灯与F1/F2同时存在，正说明当前测试口径不够。
另对当前compiled classes执行两个一次性Java探针（/private/tmp），复现F1混合分类及F3
envelope局部矛盾放行（不证明完整publication，F3已勘误）；没有把探针写成production或引入依赖。F4/F5/F6为源码与captured/config核实，
尚未新增其回归测试。没有重新打包JAR、全reactor、provider或real-case。

本轮没有证明所有crash切面、所有自定义workflow或所有provider版本；不宣称“再无遗漏”。

## 7. 如何改变下一次开工与停止决策

不再添加一份泛化原则，而是在本次修复评审提交物里强制包含以下小表/测试：

1. 每种验收类别及可混合情形：什么需要改、谁产出、现有授权是否足够。不能只测纯类型。
2. 每条实际返工边：输入ref → 下一个真实handler消费；测试不能停在后继READY或用
   replay.stop代替关键消费者。真正合同改动要经过新提案与批准；产品文档修复不改合同。
3. success也要负向测试：ACCEPT与blocking/must_fix冲突不得用provenance正确掩盖。
4. 预算测试同时断言“足额裁剪”和“仍须消费的反馈保留”；循环测试跨多次run验证累计止损。
5. 当前目标仍是完成产品验收。优先修F1/F2及会丢信息的F4，F3保留既有守卫并验证；F5使用既有预算机制评估，
   F6同消费者顺手修；不重开网络B、不重构整个平台。此处是优先级判断，尚未授权改代码。
6. 若历史070恢复需要额外永久机制，必须单列成本与一次性/新样本替代，不把救样本混入
   前向修复；在最小方案评审前保持SPM9暂停。

Boundary Review：DESIGN GAP。已证实守卫继续保留；上述缺口未修，不能标记ENFORCED。
交付物为本评估和active plan状态校正，不以文档完成冒充产品验收完成。
