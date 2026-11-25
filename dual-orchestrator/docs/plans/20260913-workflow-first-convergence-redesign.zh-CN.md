# Dual 工作流优先：复盘、重设计与收口计划

> 2026-09-23 入口修订：整体设计与完成判据以 [完整生命周期可靠性设计](20260923-dual-lifecycle-reliability-design.zh-CN.md) 为准。
> 本文保留为第一批控制链收敛设计；已有未提交实现，不能把下文 9·13 的“尚未实施”当当前工作区状态。
> 用户当前仅要求修订设计和生成 CC 只读评审提示词，不直接调用 CC；未授权继续实施或真实 E2E。下文开工/实施顺序不产生当前授权。

日期：2026-09-13。基线：`3362eb4`。主agent只读核对后的设计，不是独立评审或实施完成声明。
本轮不改production、不启动额外agent、不调用provider、不恢复6NX5或任何历史样本。

新 session 入口：[架构演进、失误复盘与交接](../design/20260913-dual-evolution-retrospective-and-handoff.zh-CN.md)。
本文是当前前向设计；前者解释5月至今的模型迁移、外部经验、保留/暂停决策和证据。
两者替代旧 active plan 的“当前下一步”口径，不改写历史 checkpoint。本文A–D不是7月Phase A–D，
后合同E1–E4也不是网络E1；术语对照见交接文档§0.2。

## 0. 决定与唯一目标

**不推倒Dual，不换LiteFlow，不新建统一治理平台。重设计既有控制边界及开发验证顺序。**

用户需要的是：确定的开发workflow调用LLM完成动作；合法结果能推进，合法人工决定能续作，
不能完成时停在正确的位置并解释正确原因。不是自动接纳任何模型输出，也不是无限自我修复。

本轮目标口径：在已支持的开发生命周期中，动作声明、执行资格、实际后继和恢复说明一致。
原需求的完整产品交付仍是最终目标；确定性流程通过不等于LLM能完成任何任务。

已确认：AHBV存在历史交付；当前6NX5暴露两项harness缺陷。不能从这些观察断言整个架构无效，
也不能用历史成功或1508项测试通过宣称当前完整流程可靠。不同样本配置与版本不同，不归因于单一模型/复杂度。

## 1. 反思：错在哪里，如何改变决策

| 失误 | 具体证据 | 从本批开始的执行约束 |
|---|---|---|
| 以事故输出补规则，未先枚举合法动作 | 6NX5的gate允许revise，resolver却只接受BLOCKED review | 先写角色/动作/结果矩阵，再写反证；正向合法路径与拒绝路径成对 |
| 从实现推导测试预期 | `humanRequirementRevisionCannotUseNonBlockedReviewAsBaseline`把合法路径拒绝写成绿灯 | 测试预期引用用户动作与定义语义，不能以当前分支条件为理由 |
| 把“强拒绝”当作“闭环” | handler拒绝正确，但runtime把错误改写；未证明合法后继 | 验证直达最终账本、下一消费者和操作提示，不止于方法返回值 |
| 人工审批被当成模型结论盖章 | 模型通过后，人要求缩回范围反而不能继续 | 人工决定独立于模型建议；批准不是免检，修订不是模型必须先否决 |
| 扩张防御目标 | TR-9把离线stub测试扩大为JVM全局网络拦截 | 每个新增要求必须对应原目标；不因更严格就默认值得做 |
| 又想靠新增原则消除执行问题 | 仓库已反复写完整链原则，事故仍复发 | 不再新增一套口号；把现有要求落到测试入口及真实运行前置 |

责任在架构落地、测试预期和开工/停止决策，不归咎于用户未提醒。主agent与同行评审均不能
以“看到了字段/防护/UT”替代对完整用户操作的验证。上述判断不代表已全仓查明所有潜在缺陷。

## 2. 代码核对与保留边界

本轮核对：README、工程清单、既有整体评审及6NX5一手报告；small workflow全部51条边；
LiteFlow kernel/provider、RoutingEngine、HumanGateActionSemantics、NodeResult、AgentExecutionAttempt、
AcpxAgentTaskHandler、ArtifactContractValidator、AgentRunMetadataFinalizer、RecoveryAssessment、
ReviewDecisionHandler、FailureClassifyHandler，以及既有序列测试入口。
其余variant完整行为矩阵、所有durable-write故障注入和所有projection端点本轮未复跑，列为交付门槛，不能声称已验证。

| 事实 | 锚点及含义 |
|---|---|
| LiteFlow已选边 | `FlowKernelProvider`默认liteflow；`LiteFlowPrototypeKernel.advance`执行编译后的链并返回transitions |
| 账本写入不归LiteFlow | `RoutingEngine.completeAndRoute`写node/iteration/gate/events/current，FAILED先走stop分支 |
| 条件边已能消费typed事实 | self-test两条INCOMPLETE边按`continuation_kind`分流；不需要新DSL |
| 人工决定入口已统一 | `resolveOpenGate`归一化action并落note；特殊材料/范围动作仍有独立资格守卫 |
| 最新实质冲突 | `TaskContractRevisionContextResolver:123-161`要求BLOCKED；JSON明确READY也入requirement gate并可revise |
| 阶段混淆可重建 | handler在proposalEmissionContext失败即返回；runtime/worker继续产物校验，最终生成AGENT_RESULT_MISSING |
| 存在同边界sibling | handler还会因qualified contract、authored context、retry context、manifest拒绝；不能只豁免一个字符串 |
| 多文件提交不是单事务 | gate resolved先写，之后node、iteration、后继、events和current逐步写入；不冒称原子提交 |

旧结论勘误：不能说“所有规则都交给workflow”。以下检查必须保留在代码领域边界：批准身份、
内容SHA、scope、外部材料归属、权限grant、候选bytes、终态守卫。它们证明动作输入/结果是否合法，
并不拥有业务下一节点。LiteFlow也不能替代这些证明或文件账本的恢复语义。

## 3. 目标结构：四个明确职责，不增加四个新模块

| 职责 | 现有owner | 能决定什么 | 不能决定什么 |
|---|---|---|---|
| 流程定义及选边 | WorkflowDefinition/JSON + FlowOrchestrationKernel/LiteFlow | 有效结果/人工动作到哪个后继，终止/等待的声明 | 模型内容是否正确、凭据是否可用 |
| 决定及状态提交 | RoutingEngine/ledger + HumanGateActionSemantics | 当前动作是否合法、谁触发、note、iteration、终态及持久化 | 替模型写方案、凭正文猜结果 |
| 动作执行与边界验证 | runtime/adapter/已有resolver和validator | 输入身份、是否调用、执行结果、产物是否可发布 | 以另一套业务规则否决已合法的人工决定、私自选择修复节点 |
| 展示与建议 | RecoveryAssessment/现有read model -> CLI/API/UI/report | 展示同一停止原因、合法动作及前置 | 改写原始失败、将“建议retry”当作授权 |

Definition、Instance、Execution继续分开：JSON是定义；current/node/gate/iteration是实例事实；
node execution及真实provider run/attempt是执行事实。准备失败可以有node execution，不能伪造一个已调用模型的run。
LLM返回的decision只是经过校验的动作结果，不是工作流控制命令；人工note是意见，不是跳过身份或权限的指令。

## 4. 完整开发路径及结果处置

```text
intake作者 -> 产品review -> requirement人工门
  -> contract.compile -> contract人工门
  -> design作者 -> 架构review -> design人工门
  -> plan作者 [按variant可含review] -> plan人工门
  -> implement -> self-test -> Git/scope验证 -> acceptance
  -> final report -> immutable delivery candidate -> 最终人工门 -> COMPLETED

任一合法返工，回到有职责的作者/补采动作，再走原审核批准路径。
执行失败进入恢复，不假扮业务返工；上传材料不直接变成ACCEPT；未知请求不动态执行。
```

下表是行为规范，不新增“统一六分类枚举”或替换现有结果schema。

| 已验证事实 | 后续语义 | 不允许 |
|---|---|---|
| 工作和证据完整 | 按声明边进入下一阶段，最终仍验需求/audit/candidate | 以exit0或正文“完成”替代验收 |
| 有具体产品修改项 | 对应作者/实现修复，再自测和复评 | 仅缺环境却给实现任务扩大写权限 |
| 仅缺验证证据 | 既有collect或prepare/submit -> 自测核验 -> acceptance | 上传即批准、补采改源码后沿旧批准进入验收 |
| 需要环境/权限/人工 | 既有human/recovery入口处理，有效grant或材料后合法续作 | 自动同意、从报错文本推导授权 |
| 动作执行失败 | 保留执行原因，同节点受控retry或停止 | 自动增加semantic iteration，反复盲retry |
| 未知/相互矛盾 | 停止自动副作用，使用既有人工请求或恢复出口 | 模糊匹配目的地、现场创建新动作类型并执行 |

对`REJECT_TOOL_LIMITATION`、`REJECT_CONTEXT_INSUFFICIENT`等目前指向implement的边，
必须结合合法分类输入检查，不能仅凭边名宣布又有缺陷，也不能假定全部合理。
若存在无需产品修改却实际回实现的可达反例，应修该分类/声明边组合；没有反例不重写。

### 4.1 人工动作矩阵

| 入口与事实 | 决定 / 必需材料 | 合法后继 |
|---|---|---|
| requirement，review READY或BLOCKED | revise +非空note +当前verified subject | 同proposal的revision patch -> 产品复评 -> requirement再审 |
| requirement | approve +当前subject，BLOCKED时保留现有说明要求 | compile -> contract gate；不是直接进入实现 |
| contract gate | clarify +非空note +compile绑定 | 回proposal，重新走两次批准，不继承旧epoch批准 |
| design/plan gate，不论模型建议正负 | revise +非空note +当前作者基准 | 对应作者 -> variant既有复评/人工门 |
| implementation repair，有具体修改项 | revise +note | implement -> self-test，旧验证不当作新证据 |
| implementation repair，仅缺证据 | collect_evidence / typed submit_evidence +源码/合同基准 | self-test ->验证/acceptance，材料精确ref可追溯 |
| scope deviation，具备限定CREATE证据 | request_scope_revision +note | 申请 -> 新proposal及两门批准，不是直接扩权 |
| request_action | typed请求id/内容，非解决动作 | 仍等待人工；新类型只能走后续设计/代码/审查，不能当场变DSL |
| 最终gate | complete/approve按variant声明；或revise | 前者验完整交付再终态，后者声明返工；终态不重开 |
| recovery | retry/abort/既有权限动作及其前置 | 合法同节点执行或停止，不冒充业务approve |

统一的是角色及动作语义，不是取消各动作必要的不同材料。当前支持范围为三个bundled variant及
external small；自定义definition只能承诺结构校验和相同边界守卫，不能宣称任意图都已业务验证。

## 5. 重设计决策A：人工决定与资格一致

保留`HumanGateActionSemantics`为动作语义owner，RoutingEngine为决定提交owner，
既有revision resolver只做subject/lineage/基准和义务构造。不开一个新的ActionRegistry。

实施规则：

1. requirement的合法人工revise接受经验证的READY或BLOCKED评审基准；不是接受任意decision，
   也不是只放宽一个Set然后结束。必须核验resolved gate、当前source subject与同iteration/完整SHA。
2. 所有会生成修订意见的revise/clarify在决定提交前统一检查note；模型建议不成为人能否修订的条件。
   公共语义检查归现有core类，不能让ledger依赖runtime resolver形成模块环。
3. resolver不得另行定义note/动作适用性的不同版本；重新核验已持久化事实仍必要，
   但核验的是同一不变量，不是第二个业务决策者。
4. 自动NEEDS_REVISION仍消费模型义务；人工revise消费人工note，模型不能伪造human权限。
   已有autorun不产生revise的边界要有测试，不能依赖偶然映射。
5. 删掉“READY后人工revise必须失败”的错误测试，替换为从真实gate决定走到复评/再批准/compile的序列；
   BLOCKED、缺note、错误subject、错轮、篡改、重复提交分别验证。

审批资格检查可以拒绝篡改/缺失证据；“合法动作一定可续作”指前提均成立的状态，
不是保证磁盘/外部系统永不失败，更不是人工可以免除合同要求。

## 6. 重设计决策B：执行阶段不能被下游猜测

保留现有NodeResult和AgentExecutionAttempt，不新建并行ExecutionDocument。
在已有动作结束管线中明确区分如下四种事实；这些是处理规则，不新增四个workflow节点。

| 动作实际状态 | 处理责任与对外结果 |
|---|---|
| 输入/资格拒绝，未调用provider | 保留harness typed失败reason/details；无产物校验伪报、无虚构provider run；MANUAL_ONLY |
| provider已启动但执行失败 | 已有attempt/process/signal为原因来源，原始输出可留诊断，不发布业务sidecar |
| provider结束但产物不合法 | 既有acquisition/有限correction/publication校验；无合法结果不选正常边 |
| 执行完成且产物合法 | 发布sidecar，decision只消费它，再由JSON/LiteFlow选边 |

最小落点：既有`AgentExecutionResultFacts`集中承载/判定由程序产生的前置失败，
`ArtifactContractValidator`只对适用阶段验证产物，`AgentRunMetadataFinalizer`不将前置失败重算成provider失败，
`RecoveryAssessment`展示原owner及真正前置。inline/worker必须消费相同策略，不各加一套判断。

前置失败producer清单已找到：proposal revision context、qualified contract、authored context的准备/渲染/manifest、
retry diagnostic context；EvidenceCollection已有专门失败保留路径；权限/存储抛异常路径也要作为sibling验证。
run目录可能在资格检查前已创建，**目录存在、缺runRef或没有输出文件都不能单独决定执行阶段**。

不预先升级整个schema。先用现有typed领域失败及封闭的程序写入入口表达；若实现确需阶段标记，
只能作为本结果的machine-owned事实，不接受LLM字段、无任意map passthrough，并在提交前把字段/消费者补入本设计。
不得用不断增长的错误字符串豁免表替代阶段语义；若既有事实不能充分判定，停止该部分实现并做最小类型delta核对。

强制保持：`FAILED`不是跳过一切检查的通行证；前置拒绝不能携候选变成COMPLETED；
acquisition拒绝、语义偷改、缺sidecar以及错误SHA仍不得发布。真实空输出仍必须报告输出缺失。

## 7. 持久化顺序及失败解释

不为本次重设计引入WAL、事务平台或通用补偿引擎。先核验现有顺序的安全性，不能用文档假设它原子。

| 转换 | 顺序 | 中途失败/冷启动要求 |
|---|---|---|
| 人工决定 | 锁内核验current/gate/action/note/必要材料 -> resolved gate ->完成source node -> iteration/后继/gate -> events -> current | 第一写之前的可预测非法动作零写；之后不完整组合不得被resume当作授权完成；不自动重发决定 |
| 动作运行 | 建node execution ->准备输入 ->调用provider（若到达）->保存候选 ->验证发布 ->finalize ->完成并路由 | 准备失败可有node execution但无provider；候选存在但未发布不能成为review输入 |
| 人工retry | 核验当前失败及前置 ->已有READY/诊断snapshot ->新execution | 不生成semantic iteration，不复用过期grant；写间失败不启动provider，不能用snapshot洗掉批准变化 |
| 补证据 | prepare保存源码基准 ->外部验证 ->submit副本/ref/SHA ->自测 ->acceptance | 源码/材料变化拒绝；孤立文件不消费；重启读固化副本，不重抓基准 |
| 完成交付 | 合法acceptance ->report/audit/candidate ->最终人工决定 ->terminal | current terminal不能单独替代质量事实；后续run/retry/approve不得启动副作用 |

现有partial-write路径若不能做到安全拒绝，视为真实阻塞，必须先修根因；
本批不承诺所有crash都能自动恢复。安全停止与自动恢复是两种能力，明确区分。
旧样本继续只读。未来是否支持历史状态须另做兼容判断，不默认编写single-case rescue或手术式改账本。

## 8. 验证顺序：先工作过程，后模型能力

复用已有sequence fixture和真实runtime/ledger/LiteFlow，不建立通用场景DSL或第二workflow。
替身只替外部transport/SDK输出；handler、输入准备、校验、发布、路由和人门必须真实执行。
不能用自动生成出来的边表作为期望答案；期望来自第4节用户流程，与实际路径比较。

必须覆盖的固定场景：

1. 正常需求 ->设计/计划 ->实现/自测 ->验收 ->candidate/最终门 ->COMPLETED；终态操作零新执行。
2. READY review后人工revise ->作者收到同subject及note ->有效patch ->复评 ->两门批准 ->compile。
3. BLOCKED review后同样人工revise；缺note、伪造/错轮/损坏subject零provider，决定前非法输入零写。
4. 自动需求修订、contract clarify、design/plan人工返工及支持的晚期合同返工，新的批准代替旧epoch。
5. 具体产品mustFix回实现；仅missingEvidence进入人工补采，不再回代码修改；补采后再次验收。
6. prepare/submit ->自测必须引用同材料 ->acceptance追溯；源码/材料篡改、摘要冒充引用均拒绝。
7. scope申请是申请而非授权；request_action仍等待，不动态执行；合法扩scope必须重走批准链。
8. 第6节各前置拒绝到最终ledger、RecoveryAssessment及CLI/API摘要保留原因；无provider调用、无sidecar。
9. 真实执行失败、quota/permission、缺输出、纠错失败/偷改分别保留原分类，不自动无限retry。
10. 合法人工retry带诊断到同节点的新execution，校验与后续review不减；恢复所需外部条件未变化则不能承诺推进。
11. 关键写入间故障及新进程冷读：不重复批准/调用，不把孤立工件当完成。复用现有故障注入点，缺口只加测试钩子。
12. 未知/矛盾typed事实停止自动动作；人工可提出请求，但无新动作的设计/实现时仍不得偷偷续作。

small走真实完整序列；三个bundled variant验证各自声明的不同终态与review拓扑，不能把small的路径强套过去。
条件边用真实LiteFlow核验并与已有builtin对偶检查；builtin不是在测试红灯时静默回退的办法。
inline/worker在本次改动的动作结束管线各走正反例，不靠一个类被共同调用推断行为一致。
CLI/API/UI/report消费同一事实的接口核对是交付条件，不新建第二read model。

## 9. 批次、成本与停止条件

以下是主agent工作量初估，不是已测工时或交付保证；真实模型等待和用户quota不计入实现工时。

| 批次 | 产出 | 开始/结束约束 | 初估 |
|---|---|---|---|
| A：决定与阶段规则同批闭合 | 第5/6节实现 +正反完整序列 +共享投影保真 | 先让真实流程反证红，再实现；不单独提交只扩大READY条件的补丁 | 1–2工程日 |
| B：现有全流程组合回归 | 第8节覆盖清单，每项标已有/本轮补/缺口，修可复现的不一致 | 复用现有大量sequence；无反例不改架构。涉及新authority或机制则回设计 | 1–2工程日 |
| C：整体自查与收口 | diff检查、全reactor、真实runtime lifecycle smoke、文档；可给CC一条独立审查prompt | 不自动开agent；无P0/P1后才进入真实运行 | 0.5–1工程日 |
| D：一次真实E2E | 原产品目标、当前冻结工件、复用Gate | 只在A–C完成后绑定新身份；不为了过关删验收，不为触发分支造错 | 不预估模型时长 |

总实施/离线收口暂估2.5–5工程日。B若发现结构性问题使估计失效，应说明具体控制链与新成本后重新决策，
不能以“整体改造”名义无限追加。开发默认主agent完成；用户如需CC评审只给简短prompt，不自行启动。

确定生产范围：core/HumanGateActionSemantics；ledger/RoutingEngine、RecoveryAssessment；
runtime/TaskContractRevisionContextResolver、AgentExecutionResultFacts、ArtifactContractValidator、AgentRunMetadataFinalizer；
adapter/AcpxAgentTaskHandler；相应sequence与接口测试。runtime/worker主循环只有去重确有必要才改。
若B证明某条现有分类边有错，只改其唯一分类owner/JSON及对偶测试，不顺带配置化整个contract registry。

明确不做：新模型层、全delegate给LiteFlow、网络安全重开、通用权限/恢复框架、动态DSL/插件动作、
放松SHA/scope/证据规则、为OKX写特殊分支、旧case专用兼容、全仓美化重构。

停止/重新设计触发：需要新持久化authority；为了通过测试跳过真实validator；新增永久兼容接近根因修复成本；
无法解释写间失败；必须改变原需求才能通过。反之，有限且owner清晰的缺陷应完成，不为普通小验证反复询问。
真实E2E再失败不自动意味着换引擎：按harness/模型/环境/输入区分，并检查是否同类结构性耦合，而非按次数推倒。

## 10. 交付证据与执行门

本设计的六维：Declared=动作/阶段/处置矩阵；Proven=真实ledger及验证产物；
Consumed=原resolver/validator/runtime/选边/投影；Enforced before=provider、发布、路由及终态副作用前；
Failure route=保留真实owner的既有人工/恢复出口；Cannot-bypass=第8节成对正反序列及写间失败。
目前这是一组实施要求，**不是ENFORCED或PROVEN声明**。

首批可开始条件：本设计经用户接受，前置失败所有return/throw路径落实为测试清单；
若需要新的machine-owned阶段字段须先补最小design delta，不能边写边决定新schema。
本轮已定位producer范围，但完整错误保真矩阵尚待首批测试复现，不能把计划称为修复完成。

真实E2E前常设检查加到既有Gate复用记录，而非另建Gate体系：
“所有本任务必需出口已实现，合法路径成对测试通过，先前失败不被后续改写，关键写间失败安全停止，
inline/worker与各variant的实际差异已覆盖；测试替身未绕过handler/validator；真实工件及环境身份新鲜。”
未触发的自然分支仍记NOT EXERCISED；只有新版本实际完成需求/audit/candidate才记该组合E2E PASS。

本轮自查：方向为有界收敛，非平台重写；生产零改动；整体动作表包含非续作请求和terminal而非只列happy path；
不把所有校验迁到JSON，不保证未知异常全自动恢复，不把已有历史成功和本轮确定性证据外推。
尚未独立review，也未执行新测试；下一步是A的完整序列反证与最小实现，不是再启动真实样本。

## 11. 历史演进对本设计的约束

本设计不是忘记历史后重新发明四层架构。以下迁移已发生，本批必须利用它们而非再造一遍。
历史来源、提交锚点及外部文档复核见交接文档§2–§4；下面是对本次实施的具体约束。

| 历史经验 | 本次保留/修正决定 | 必须落到哪项整链验收 |
|---|---|---|
| Markdown路由已迁为typed artifact，ACP只提供消息/turn契约 | 不重开自然语言路由；保留presentation ETL与有界correction，不将exit0当成功 | §8场景1/9：无合法产物不发布；合法产物不被无关telemetry拦截 |
| 读治理曾误伤工作，后来降级为telemetry | 预算、重复读取、memory不得变成新的批准资格 | 正常链在同样有效结果但不同读取统计下目的地一致；保留真实usage，不新增治理 |
| Node/Execution/Attempt已经分开，后处理仍会覆盖原因 | 复用原模型补足阶段适用性，不建立第二ExecutionDocument | §8场景8/9：前置拒绝、真实执行失败、产物失败三组到最终read model均保真 |
| 人工门曾只对齐菜单与图，未对齐后继resolver | 同一合法动作在入口、持久化事实和下一个consumer语义一致 | READY/BLOCKED人工revise、缺note零写、伪造human拒绝；完整到复评/批准 |
| E1–E4已经建立批准合同、design/plan、作者修订链 | 不绕过resolver，不复活canonical或latest扫描，不扩义务平台 | §8场景4：初次及晚期返工新批准到下一作者/验收；删除/错轮事实仍拒绝 |
| Scope从一律止损走到人工合理新增 | 既不默认放宽，也不拿合法申请当直接授权 | §8场景7：申请->两级批准->实际Git校验，不能只证明按钮可点 |
| 缺证据返工走错；之后又缺submit出口 | 保留must_fix/missing_evidence区别及完整外部材料链 | §8场景5/6：补采成功与仍失败都不能自动变代码返工；同材料到acceptance |
| D1/D2将交付bytes和完成投影收敛 | 不用workflow终态或报告正文替代candidate/audit；不复活稳定路径authority | §8场景1/11：正常交付、篡改拒绝、冷读、terminal无副作用；产品apply权限另列 |
| 网络E1/S1/S2已有guard；R4曾过度扩张 | 保留策略、grant、session/profile边界；不重做沙箱因果证明 | §8场景9/10保持quota/permission、grant不继承；本任务所需环境按既有Gate检查 |
| fixture曾未真实运行、测错binary/投递路径 | 离线替身只替provider，不替真实handler/validator/ledger；preflight指向实际发布JAR | package/identity与runner离线检查；不以standalone probe代替production链效果 |
| correction曾被最终JSON重解析洗掉拒绝 | 已强制拒绝入口必须保持，即使本批修改phase处理 | 普通schema及authored correction偷改仍无sidecar/后继review，inline/worker成对 |
| 历史多文件原子恢复已做有界评估而延后 | 保持安全停止和明确支持范围，不承诺全自动救援 | §7/§8场景11：故障写间不误批准/发布；发现安全缺口则单列阻塞，不借机引入WAL |

这些是同一产品流程的回归要求，不是十二个独立开发项目。先标记“已有测试且真正到下一消费者”，
只补缺口；不得把表中每行都变成新schema/服务/Gate或额外provider样本。

### 11.1 外部经验的采用边界

- Aider/研究论文支持相关context和可归因实验，不支持事后read硬阻断，也不证明Dual成本已经下降。
- ACP区分消息与turn；Dual应用合同是自己的责任，不能要求底层模型实现不存在的wire保证。
- LiteFlow已经负责选边；不把long-running ledger、人工批准或Git副作用回滚能力凭空归给它。
- AWS redrive/Temporal replay启发“同定义续作与改定义迁移分开”“新代码检查历史输入”，不引入它们的产品。
- Claude sandbox文档解释网络权限与工具auto-approval的不同层，不作为恢复网络证明工程的理由。
- 早期HERMES数学推理论文不是Hermes Agent工程实现证据；不以类比增加model层。

一手来源及本轮能核证的范围见交接文档§4。以上每项均须落在当前已有owner上；
不能用“行业都这样”来跳过长期收益/短期成本判断。

## 12. 开工顺序、交接与客观完成标准

### 12.1 第一批之前做什么

这不是重新调查全仓数月历史，也不是立即修改READY的Set：

1. 冷读HEAD/dirty状态与6NX5报告，确认新会话没有把旧Gate或另一个E1当本批目标。
2. 把§4.1与现有三个bundled+small定义逐项对应，列出入口允许、提交守卫、后继resolver和真实fixture。
3. 对§6每个前置producer记录“发生在创建目录/claim/provider之前还是之后”，以及validator/finalizer/read model实际读什么。
4. 选现有序列fixture运行两个旧实现反证：合法READY人工revise应到复评；前置失败应保留原typed原因。
5. 写下最少需要改的既有类型/字段和依赖方向。若现有typed facts不够，先把machine-owned最小delta写明；
   未定时状态为DESIGN GAP，不得借一个自由map或字符串白名单跳过设计。

以上保留为原批次的技术准备条件，不代表当前实施授权；当前按 2026-09-23 设计准备只读评审提示词并停下。
提示词要求 CC 自行重建完整 transition，不预填期望结论、不委派实现；由用户自行发起，不直接调用 CC。

### 12.2 每批如何判定完成

| 检查项 | 可核验标准 | 不能替代它的东西 |
|---|---|---|
| 合法动作可达 | §4.1支持的action×source状态有成对序列，走到下一作者及审核/批准 | 菜单存在、edge存在、resolver UT绿色 |
| 原因保真 | 前置失败最终node/current/recovery/CLI/API保留同原因，零provider、零业务sidecar；真实缺输出仍正确分类 | handler返回值正确，或仅error字符串相似 |
| 拒绝有效 | 删除同一强制点导致反证失败；伪造/错误身份不能靠最后JSON合法通过 | 更多校验字段、更多warning |
| 组合覆盖 | 正常链、语义返工、执行retry、人工补证据、scope重批准、terminal、关键写间失败各有实际结果 | 测试总数、只跑一个variant、只跑inline |
| 实验成本控制 | 已有测试复用；没有无目的新provider轮；等待/运行/无响应依据实际事实区分 | 固定时间一到就断言模型失败，或无限等到quota耗尽 |
| 真实产品交付 | 复用Gate的一次有效样本满足需求、测试/证据、scope、acceptance、audit、candidate | 离线替身通过、COMPLETED单字段、历史AHBV成功 |

不设没有依据的成功率阈值，也不把当前2.5–5工程日估计冒充全部潜在缺陷的修复成本。
该估计只覆盖已识别双边界及现有组合回归；新增根本模型或耐久恢复机制会使估计失效，必须重新决策。
如果现有组合测试已足够就直接复用，不为凑批次另造工作。自然分支没触发记NOT EXERCISED，不制造缺陷补覆盖。

### 12.3 新 session 必须继承的止损

当前只是设计可评审状态，不是无条件Implementation READY，更不是Production PROVEN。
本轮完整历史复盘不改变旧sample只读和provider未运行的事实；6NX5不能用无条件retry救援。
不要自动重开网络B/R4、替换LiteFlow、放宽scope/SHA、引入统一模型/权限/证据平台，或把历史所有P2列为本批前置。

当一个有限修复已证明owner清晰且能通过整链测试，应完成它，而不是再次退回空泛反思。
当需要新authority/永久兼容/未支持外部能力，或同类结构性失败再次发生，则停止扩写并说明证据和选择。
这就是把反思落实为开工与停止决定，不再靠更多原则文字承诺“下一次一定注意”。
