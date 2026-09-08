# 验收返工最小设计与通用性评估

日期：2026-09-07。基线：6784b662 + 当前工作树（Phase A/transport 增量保留，不混入本修复）。
状态：DESIGN DELTA REVIEW PASS / IMPLEMENTATION ENTRY READY（2026-09-08）。当前仅设计；production、SPM9、provider 均未触碰。
关联：`harness-review-blind-spots-assessment-20260907.zh-CN.md`，F1–F6。
主线目标：P0 主生命周期稳定性，不是网络 E1，也不是新的权限平台。

## 1. 目标与停止条件

目标：有效验收拒绝必须进入能处理该问题的真实生产者；合同变更必须重新提案、评审、批准；
矛盾的通过结论不能进入成功链（该守卫已存在，F3误报撤回，只验证保持）。纯补证据不要求改代码，混合修复不能跳过代码/文档修复。

非目标：保证模型永不误判、自动批准合同、自动判断自然语言含义、网络 Phase B、
通用修复任务平台、新事件存储、永久修复 SPM9 的旧070状态。

停止条件：若需要第二批准机制、独立 repair artifact、任意图迁移器、或绕过既有批准/SHA守卫，
停止实现并重做更小方案比较。本文件不是实现完成声明；独立评审的阻塞项须先关闭。

## 2. 实际边界与更小方案比较

| Owner | 唯一事实 | 下一消费者 |
|---|---|---|
| AcceptanceReviewResult + validator | 已验证的验收判断及结构化缺陷信息 | FailureClassifyHandler/ReviewDecisionHandler |
| FailureCategory + FailureArbiter | 唯一分类规则和值域 | definition 的 REJECT_* 边 |
| workflow definition | 合法下一节点、loop、预算 | RoutingEngine |
| iteration/node/inputRefs | 此次返工的触发及来源 | revision context resolver |
| 已批准 proposal reference + requirement gate | 待编译提案的合法批准 | ContractCompileHandler |
| compile + resolved contract gate | 下游获准使用的合同身份 | TaskContractProjectionVerifier、authored consumers |
| report/packet/CLI/UI | 上述事实的展示，不产生分类或授权 | operator |

已核实：FailureArbiter:81起由 TEST_CODE_CHANGE 特例及列表非空决定路由；FailureClassifyHandler
不传 mustFix。四definition把合同类直接接compile；compile:255要求当前获批proposal。
直接改接intake仍不够：WorkflowDefinitionLoader:198禁止重新进入非compile的谈判节点；
TaskContractRevisionContextResolver:65仅接受既有三个修订来源，拒绝REJECT_CONTRACT_*。

候选比较：
- 仅改优先级：拒绝。不能表达混合修复，且真实合同变更仍缺批准生产者。
- 新repair枚举/文档/审批gate：不选。既有category、proposal patch、两道gate足以承载。
- 收紧既有分类字段 + 扩展现有修订解析器的有界入口：当前首选。没有新增领域文档/节点。
- 复用contract gate提前问人：不选为默认。该gate的subject是compile，不是拒绝报告；为支持
  提前询问而修改其approve/clarify语义，会混合“允许起草”和“批准合同”。

## 3. F1：一项分类决定下一步，证据列表不再暗中覆盖

首选使用既有 failure.category_hint 的规范 FailureCategory 值，作为经过校验的显式分类输入。
这是该字段在 acceptance REJECT 中的语义收紧，不把历史自由字符串当新协议合法值。
七个值仍由 FailureCategory 单一定义；不另造 repair_kind，verification_remediation仍只描述验证。
非acceptance类型的适用规则不扩张。parser保持语法职责；validator拒绝缺失/未知/矛盾组合，
不做大小写/近义词猜测后悄悄路由。prompt与correction投影同一允许值表。

| 实际修复意图 | 显式category | 必经后续 |
|---|---|---|
| 已有合同内生产代码/测试/文档不符，包括同时缺证据 | IMPLEMENTATION_BUG | implement → selftest → acceptance |
| 仅需执行既有测试/收集证据，无代码修改 | VERIFICATION_INSUFFICIENT | selftest → acceptance |
| 已有合同缺必要约定，或约定歧义；可同时有实现/证据问题 | CONTRACT_GAP / CONTRACT_AMBIGUITY | 修订proposal → 新批准 → compile → 合同批准 → 设计/计划 → 实现/验证 |
| 上下文不足 / 工具前提不足 / reviewer需复核 | 其余三个既有值 | 保持既有出口，受累计预算；不宣称路由会自动解决外部前提 |

机械一致性至少钉死：
1. contract_gaps非空只允许合同两类；非合同类只能将“违反现有条款”的具体问题写入must_fix等
   既有字段。合同类必须提供非空contract_gaps。矛盾则correction/typed invalid，不选先匹配规则。
2. TEST_CODE_CHANGE 与纯VERIFICATION_INSUFFICIENT不兼容；可为IMPLEMENTATION_BUG或
   合同类（后者先改约定，之后仍需实现/测试）。EVIDENCE_COLLECTION不覆盖IMPLEMENTATION_BUG。
3. out_of_scope_files非空不可归纯补证据或review noise；允许实现修复或经批准修改合同。
4. 保持missing_tests与verification_remediation的双向既有约束。must_fix可包含证据事项，
   不能仅因must_fix非空一律判实现修复；禁止解析其文字决定代码是否需改。
5. accepted结果保持既有StageOutcomeCompiler语义，不扩大ACCEPT_WITH_NOTES的可选证据约定。
   blocking/must_fix矛盾原本已拒绝；新增分类字段若与成功结论冲突由同一语义owner拒绝，
   不复制一套成功规则到envelope validator。

FailureArbiter保留唯一分类入口，但新验收路径不再以contract.ambiguityNotes、changedFileCount、
自由文本或列表优先级覆写已校验分类；分类读不到合法字段即拒绝，不回退旧启发式。
分类结果仍由machine写入；模型不选择node id，不授予scope或批准。
新分类一致性规则必须被publication和sidecar重读共同消费：AgentContractSemantics维护同一
规则入口，envelope校验可委托，不得两份列表。FailureClassifyHandler在调用arbiter前验证
重读的document，不仅依赖历史曾经valid；旧sidecar缺新必填category在新执行中明确拒绝。
更早的共享读取点是AgentEvidenceIndex.readValidatedEntry：lineage通过后、返回entry之前调用
同一语义规则；未知remediation不得被解析为null后绕过。边界解析成FailureCategory再传arbiter。

Acceptance REJECT组合补表（空/非空均为结构检查，不解释句子）：
| category | 最少支持事实 | 禁止组合 |
|---|---|---|
| 合同两类 | contract_gaps非空 | 不因其它修复/证据项存在拒绝，全部保留到新提案链 |
| IMPLEMENTATION_BUG | must_fix或out_of_scope_files非空，或TEST_CODE_CHANGE | contract_gaps非空 |
| VERIFICATION_INSUFFICIENT | missing_tests或missing_evidence非空 | contract_gaps/out_of_scope_files/context_gaps非空；TEST_CODE_CHANGE |
| CONTEXT_INSUFFICIENT | context_gaps非空 | contract_gaps/out_of_scope_files非空；TEST_CODE_CHANGE |
| TOOL_LIMITATION | failure.reason非空 | contract_gaps/out_of_scope_files非空；TEST_CODE_CHANGE |
| REVIEW_NOISE | failure.reason非空以说明需复核 | 四failure列表、missing_evidence、must_fix非空；非NONE remediation |

所有行仍执行既有missing_tests/remediation双向约束；CONTEXT/TOOL允许同时缺证据，但不
承诺运行一次即可修好环境，维持原出口和止损。非acceptance REJECT不套本表。

残余：模型仍可能语义上把文档错误写成合同缺口，结构化字段不能证明该语义为真。
机械收益是矛盾显式拒绝、路由确定、真正改变合同仍须人工判断；不声称根除语义误判。

## 4. F2：复用提案修订与批准链，不直接重编译

两条合同类边改为loop → context.intake_claude_brainstorm → intake.claude_brainstorm，
随后完整复用 product review → intake.decision → human_requirement_review → compile →
contract_review → design → plan → implementation/selftest → acceptance。不删两次不同subject的批准。

必须同批处理的接线：
- loader只允许明确声明的合同类返工进入这个已有proposal context入口；不得普遍放宽
  post-contract→任意谈判节点。核对真实handler/target/后继以及四definition parity。
- TaskContractRevisionContextResolver增加两个合同类trigger的解析。必须核对iteration parent、
  completed classifier、其唯一validated REJECT sidecar、规范category及实际declared edge；
  不从全局failure-classification.json、最近mtime或任意旧proposal猜baseline。
- classifier执行时（旧稳定投影尚未覆盖）baseline沿当次qualified contract的compile/resolution SHA → approved requirement gate →
  subject review → proposal reference/SHA回溯。旧批准所在iteration不必等于最新acceptance
  iteration，但每跳必须验证本身的历史关系。复用现有reference/verifier，不复制新的SoT。
- 在classifier既有machine-owned node result固定previous proposal reference及SHA、source review
  ref/SHA和已验证的compile/gate identity。后续resolver验证completed trigger与这些固定引用，
  不再重新查“当前qualified contract/latest resolution”决定baseline。原始义务由唯一review
  ref重建，不维护另一套可变义务文档。缺少固定引用即失败；compile覆盖旧稳定文件后也不能换基线。
- 合同类contract_gaps按该review引用与列表序号构造现有ReviewObligation，传给既有有限patch
  merger。ID只在该revision context中使用，非新持久文档。其余must_fix/missing evidence必须
  随原sidecar refs进入修订上下文，不因选了合同类就丢弃。
- 仍输出TaskContractRevisionPatchResult，base proposal id/SHA及obligation exact-cover不放宽。
  proposal/product review/批准均是新occurrence；compile现有批准guard保持原样。
- 晚期scope有一个必须同批解决的真实前置：原批准CREATE文件实现后已经存在，merger原样
  继承CREATE，TaskContractProposalValidator:454却按当前磁盘拒绝。Git scope是相对原任务
  workspace baseCommit的累计差异（ArtifactValidateHandler:326；ChangeScopeEvaluation:91），
  因此不能为通过校验把它变为MODIFY。仅在固定的已批准基线中逐字存在的path+CREATE允许
  已存在；其余canonical/escape/metadata/目录检查保持。未获批revision proposal不能自己成为
  豁免依据，初始proposal与新addition仍严格。publication与compile共用同一基线感知校验。
  原workspace/base身份不得变化。scope验证测试必须执行到最终Git差异评价，不停在proposal。
- 新contract gate批准后设计/计划重新产生；AuthoredExecutionContext的contract epoch守卫
  应拒旧design/plan冒充新批准。不能在新合同后直接复用旧plan跳到implementation。

边界：只承诺既有patch支持的条款替换/添加与scope additions。删除scope、任意替换objective、
已完成workstream重开不在本批承诺中；用户最新收缩需求应单独按合法支持面处理，不手改旧合同。
此外，“先越界创建再将该文件作为新addition补批准”不在本批支持面：当前resolver会将其识别
为MODIFY、累计Git差异却为A。必须在新addition校验时显式拒绝该原base不存在但当前存在的
路径，不允许走到最终scope才失败。不得把这个拒绝称为完成任意晚期scope修订。

### 4.1 再次修订：草稿基线与批准基线分开

限定于本次新增的验收后合同返工链，不改初始提案协商的义务协议。现有Context中的
previousProposal/previousReference继续表示**本次patch的直接父提案**；另在同一内存Context中
承载已核验的批准scope基线及返工根来源。它们由既有持久引用重建，不新增binding文档。

| 时点 | patch直接基线 | CREATE已存在豁免来源 | 必须回应的义务 |
|---|---|---|---|
| 已批准A被验收拒绝，起草B | A | A | 根验收contract_gaps |
| B被product review拒绝，起草C | B | 仍为A，不能用未批准B | 根义务 + 本协商链各轮review义务 |
| 人工requirement revise，或compile后contract clarify | 当前gate所绑定的提案 | 仍为A，单独requirement批准不切换执行authority | 上述集合 + 本协商链人工note |
| 新contract gate最终批准C | 此协商结束，不再生成patch | C成为后续执行合同 | 原集合保留在证据中；未来验收拒绝另起根 |

重建算法只沿iteration.parentIterationId回到最近的合同类返工trigger：逐跳验证iteration id、
trigger completed状态、declared transition、inputRefs及引用SHA；重复parent/断链/错误trigger
即拒绝。根trigger消费§4冻结的A引用及验收sidecar，不查询当前稳定resolution。
中间仅接受已有NEEDS_REVISION、requirement revise、contract clarify三种合法来源；
直接patch父提案来自最近review/gate的subject，不由祖先A替代。每跳校验用其本身的iteration，
不要求历史批准与当前iteration相等。不能跨另一个已完成合同批准epoch继续累计旧义务。

当前resolver在NEEDS_REVISION分支仅返回最新review义务、human revise仅返回一条note，
故这两个分支及clarify分支在识别到上述晚期根后必须使用同一重建结果，publication与compile
同样消费该结果。requirement revise须核实真实gate的subject/来源decision与非空note；
晚期链允许gate所声明的READY或BLOCKED来源，不以现有仅BLOCKED过滤误拒合法人工revise。
其它初始链行为保持，不通过放宽任意gate种类实现。

clarify的历史路径另钉死：当前resolveContractClarification读取稳定contract-resolution.json，
不能直接复用来遍历祖先。晚期链从该contract gate的source_node_instance_id（gate/node/event
三方一致）定位completed compile；核对同iteration及gate subject=compile outputRefs，再从
compile.inputRefs解析唯一已验证proposal review及其proposal ref。该compile iteration内，
必须恰有一个已完成、resolved/approved的requirement gate与这组输入逐字匹配：
gate subject=node inputRefs，node outputRefs=compile inputRefs，review及proposal本身lineage
合法，并存在该gate到compile的declared/routed边。零个或多个匹配均拒绝，不按目录序挑最新。
这个有限候选核对只重建当次提案批准，不要求旧稳定resolution文件仍保留旧字节，也不产生
新的执行合同authority。原批准A仍只来自返工根固定身份；历史compile SHA留作关联事实，
不能据此跳过最新合同qualification。初始clarify的旧行为不借机迁移，新增晚期路径禁止fallback。

义务按来源发生顺序合并：根验收列表、每轮review的contract_obligations、每个人工note；
ID以来源sidecar SHA或gate instance id加原ID/列表序号命名空间化，同一来源不重复加入。
不按自然语言相似度去重、不由模型的resolved响应或下一轮READY删除义务。直到合同最终批准，
每次patch exact-cover同一累计集合；已在B满足的项可用空变更列表引用现有clause id，
不要求重复改写（既有patch validator允许空changes，merger仍验证exact-cover）。
互相矛盾、不能用既有patch表达的要求应停止协商/交还人工，不自动丢旧note或扩展patch协议。
product-review上下文及两道human packet必须只读呈现此集合、来源和候选revision_responses，
使最终批准有知情依据；这不是新的义务状态存储。原验收其它缺陷继续作为sidecar证据传递，
不把所有must_fix机械改成合同义务。根引用删除或篡改后，即使B完整也必须拒绝。
接线落点明确为ContextBuildPackageHandler的product-review上下文和HumanReviewGateHandler的
requirement/contract packet，均调用同一resolver投影，不在renderer中重新推导集合。
product review绑定其routed候选proposal；requirement packet绑定gate subject的review及候选；
contract packet绑定source compile的上述输入链。响应来自这些候选sidecar，不是根A或latest。
intake既有proposalEmissionContext消费完整义务，publication/compile的exact-cover负责强制，
展示本身不授予批准。缺来源时上下文/packet生成失败，不打印空义务后继续批准。

## 5. 完整状态转换与失败切面

| 前态 / 触发owner | 写入与下一消费者顺序 | 后态 / 失败解释 |
|---|---|---|
| reviewer运行，LLM提交结果 | parse → semantic validator → sidecar发布 → decision | invalid不得先发布可路由成功结果；既有correction/recovery，非猜测分类 |
| validated REJECT，classifier执行 | 校验sidecar/qualified contract → machine classification → completed result → ledger route/iteration | 非合同修复保持原authority；失败不派发next provider |
| 合同类新iteration | resolver核验trigger及批准proposal baseline → context → 新patch验证/发布 | 无baseline/篡改即失败，不退回initial proposal或旧全局文件 |
| 新proposal/product review完成 | requirement gate打开 → 人工决定绑定subject → 路由compile | open/revise不得编译；人只批准当前proposal，不自动继承旧批准 |
| current proposal已批准 | compile先核验 → contract JSON/MD → resolution JSON/MD → result SHA → ledger完成/路由contract gate | 中间崩溃可能留半更新；必须停止且拒qualified使用，不以重试自动解释为批准 |
| contract gate已批准 | resolver产生新epoch → authored design/plan新批准 → implementation | old SHA/旧epoch拒绝；未批准新合同不可启动后合同provider |
| 验证通过 | validated accept → goal/report/candidate → human acceptance | contradictory accept在第一步拒绝；candidate不是target已写入 |
| 自动拒绝达到预算 | ledger既有revision limit → synthetic recovery | 不自动续行；runtime retry不是新增semantic预算 |

进程重启：仅消费已落盘ledger/ref/SHA，不读取内存分类。每次node创建固定输入epoch；
retry只重试同execution边界，不能换提案/批准。abort不产生批准，既有terminal守卫不变。
R1多文件原子性未由本批解决：写入间断裂要求fail-closed与可诊断停止，不承诺透明resume。
旧binary/旧schema语义或旧frozen graph不自动迁移。旧SPM9只保留证据；实际恢复另评。

### 5.1 中断后的真实入口，不把所有停止都写成recovery

以下是现有代码的支持界限，不引入自动修复器。RoutingEngine先写resolved gate，再写完成node、
新iteration/后继、events，最后写current和instance投影；这些不是一个原子事务。

| 磁盘切片 | 冷启动实际行为/权威读取者 | 本批允许的处置 |
|---|---|---|
| 决定前状态完整，gate仍open | resolveOpenGate核对当前active及open gate | 正常人工决定；非空note等既有条件不变 |
| gate已resolved，node/current仍指旧gate | loadOpenGateForNode与requireOpenGate找不到open gate；run/approve失败；retry仅接受FAILED/BLOCKED节点 | 停止操作并保留证据。不是WAITING_FOR_RECOVERY；不能建议retry或再approve |
| 非gate节点已COMPLETED，current尚未前进；后继/event可能已写 | WorkflowRuntime.resolveNodeForExecution对COMPLETED返回null，不执行provider；retry拒绝COMPLETED。孤立后继不在current active内 | 停止，不手工派发孤立后继，不重写current或补event |
| compile多文件已部分替换，尚无一致完成result/新gate | 新qualified consumer的ref/SHA验证不能建立新批准；旧稳定文件不能作为救援authority | 本切片不支持透明续行；不把stale RUNNING的运行时retry当作安全重编译保证。保留现场，禁止本Gate自动resume |
| current已合法前进，仅instance展示未更新 | current是调度authority；新active及其完整refs/gate可正常验证 | 可按current继续，旧instance/status展示不撤销或补造批准 |
| 正常失败已完整进入WAITING_FOR_RECOVERY | recovery assessment读取该状态；abortRecovery仅在该状态可用；retry还需节点FAILED/BLOCKED及适用恢复条件 | 按既有assessment处置；abort不产生批准，不宣称retry可修复缺失授权 |

前四类不一致切片的“停止”是操作纪律和具体消费者拒绝，不是承诺ledger自动改写为FAILED。
特别是compile的stale运行与全局自动恢复仍为R1未解决面，本设计不宣称通用crash恢复闭环。
若实际测试发现半状态还能绕过批准进入provider，则是本批阻塞，不能以R1名义接受越权。
若只是不支持续行，保留工件并结束该样本，另评fresh/一次性迁移，禁止永久兼容解释。

冷读核对current的active/openGateIds、对应node/gate、相关events及ref/SHA；旧review show
可能仍展示先前Markdown选项，它不验证gate仍open，不能用其提示授权续行。本轮测试直接
调用真实ledger/runtime/qualified reader验证上述拒绝，不增加通用审计命令。测试允许既有
execution诊断记录，不误称所有磁盘零写入；禁止的是新批准、业务provider和交付副作用。

## 6. 其余缺口与依赖

- F3撤回，不安排生产修复。ArtifactContractValidator:306发布前调用AgentContractSemantics，
  StageOutcomeCompiler:85拒绝notes+blocking/must_fix。主线程已实际调用真实publication，
  返回FAILED / NOTES_STATUS_BLOCKING_CONFLICT。只补真实链路回归，保留现有可选notes语义。
- F4只修裁剪长度更新与停止条件，复用既有渲染；保留优先级/overflow policy，不添加摘要系统。
  能保留反馈的预算必须保留；若硬必需内容仍超预算，走既有失败，不偷删authority。
- F6按真实序列化键恢复展示，保持同一classification来源。
- F5在四classifier配置既有max_revisions=3。现revisionCount按source+target累计，不是一个
  全局三次限额；文档/测试必须逐目标及交替目标说明实际有限上限，不宣称全局3次。
  达限复用synthetic recovery，不把不匹配的BLOCKED硬塞给现有gate。明确人工revise原规则不变。

## 7. 最小变更面与通用性

批次A：ContextBuildPackageHandler及内容保留测试；ContextStateEvidenceBoundaryRenderer
及序列化展示测试。修F4/F6，F3只补既有publication保持性测试。

批次B：FailureArbiter、FailureClassifyHandler、validator、AgentResultPromptContract及correction
投影测试；AgentContractSemantics/AgentEvidenceIndex重读发布共享规则；TaskContractRevisionContextResolver、WorkflowDefinitionLoader、四definition及真实
handler序列测试。TaskContractProposalReference等只允许抽取现有核验复用，不新增权威文档。
ContextBuildPackageHandler及HumanReviewGateHandler补§4.1的同源义务/候选响应展示，不新增read model；
intake的既有adapter装配继续传递resolver结果，不改其权限或provider接口。
TaskContractProposalValidator及publication/compile调用面加入同一可信基线校验，
TaskContractProposalRevisionMerger/ScopeResolver仅补上述新addition拒绝，不建设迁移框架。
FailureCategory值域不扩；core注释/文档若描述旧优先级须同步。删不再消费的分类启发式及
changed-file计数读取，先sweep所有调用者，测试不是保留旧production分支的理由。

不改：provider/fork/network grant/CLI审批动作/ledger schema/交付apply算法/产品需求与源代码。
F5优先只用definition配置与已有ledger测试；若既有计数不能强制有限上限，停止而非散加计数器。

通用性检验：fixtures使用通用静态站点README、Java工具与小型Python服务；交换目录/技术栈
不改变分类与批准链。production禁止交易所、BTC、K线、特定README短语或样本ID判断。
真实SPM9只作为一个回归输入，不作为唯一证明。custom definition非法返工必须loader拒绝，
不是用四个bundled文件名白名单放行。

## 8. 必须运行的测试，不以READY代替完成

1. 分类矩阵：纯产品/文档、纯测试代码、纯补证据、修复+补证据、真合同gap/ambiguity+混合问题。
   render → parse → validate → sidecar → classifier；注入冲突category/list须拒绝，不能自动猜。
2. 矛盾accept反例执行到decision/goal/report/candidate边界，证明未生成成功副作用；正常accept保留。
3. 合同返工真实序列：旧批准A → acceptance拒绝 → 新proposal patch B → product review → 人工批准
   → compile → contract gate批准 → 新design/plan → implementation/selftest → 再验收。
   只允许fake agent给typed输入；关键runtime/ledger/validators/compile/解析器必须真实，不用replay.stop。
4. 纯补证据不调用implement；混合修复先implement再selftest；合同修改前后原始missing evidence不丢。
5. 四definition与custom同拓扑；直接compile/错误trigger/任意negotiation跳转必须拒绝。
6. 删除proposal ref、伪造批准、错parent/SHA、revision obligations不全、旧plan跨epoch，均失败。
7. 分类发布前后、proposal发布前后、人工决定与路由之间、compile多文件写入之间失败；
   冷JVM读盘要么恢复同一合法事实，要么明确失败，绝不能跳过批准或用旧artifact rescue。
8. maxsteps分多次run、不同拒绝出口交替、冷读计数，达到真实累计上限后不再provider；人工动作保持。
9. F4构造只需删一个低优先级section便能达标的预算，断言高优先级反馈还在；保留硬内容失败分支。
10. 合同loop后不批准不能进设计；新批准后真实author preparation通过且旧epoch反例失败。
11. 原批准CREATE现已存在的晚期修订，真实patch publication→compile→scope累计评价通过；
    无批准基线/伪造继承/未获批addition继承不得豁免；旧CREATE改成MODIFY反例不得通过。
12. compile覆盖稳定resolution后重复读取旧返工trigger，固定proposal baseline不变；
    去掉固定node result refs后测试必须失败，不能回退当前全局projection。
13. A批准→B→NEEDS_REVISION→C→human revise→D→compile→clarify→E→合同批准，
    每轮验证直接patch父SHA、授权基线始终A、根义务与中间note不丢、packet可见；
    未批准B新增CREATE不能豁免；根引用删除即失败。E批准后的下一验收返工以E为新根。
    第二次compile覆盖resolution后重读祖先clarify仍得到D；篡改其compile输入/批准gate或制造
    双匹配必须拒绝。product-review、requirement与contract packet三面同时断言当前候选响应，
    任一改读latest/旧A/空集合的突变必败。
14. 对§5.1各切片从冷对象调用真实run/approve/retry/abort适用入口，断言实际拒绝和原ledger
    状态（不假定自动recovery）；完整current/滞后投影正例可继续。compile半写必须执行到
    qualified consumer拒绝，不能只检查文件不齐；不得为通过测试加入救援状态修补。

## 9. 六维不变量与排期

Declared by：既有类型值域、validator约束、definition边与预算。
Proven by：validated sidecar、iteration来源、proposal ref/SHA、两级人工gate/compile identity。
Consumed by：单一classifier、revision resolver、compile、qualified authored消费者。
Enforced before：侧写成功publication、下一provider、合同使用与delivery candidate之前。
Failure route：正常typed invalid/correction或既有recovery；非原子中断按§5.1区分具体拒绝与不支持续行，
不冒称自动进入recovery，不降级成成功、不猜测、不过时限强判模型失败。
Cannot-bypass test：§8混合/矛盾/真实consumer/失败切面/cold/sibling全链。

排期仍暂按5–9工程人日上界管理，不将误报F3继续计入开发：本设计0.5–1；批次A 0.5–1；
批次B 2–4；集成评审1–1.5。F2新增发现的晚期scope基线约束必须先裁定，未裁定不做定额承诺。
不是墙钟承诺；已发现loader与resolver约束纳入批次B，不隐藏为“一行改边”。
本批结束只可声明deterministic测试覆盖范围；fresh E2E另起Gate，未自然触发的路径记NOT EXERCISED。

## 10. 独立评审入口

要求评审者自行重建完整transition，验证§3协议收紧是否最小、§4真实producer是否足够，
以及所有失败切面的副作用守卫。不要按文件清单逐项打勾替代端到端判断；允许提出更小方案。
重点质疑：category_hint提升为显式输入是否有第二owner；晚期baseline是否可由现有事实重建；
新compile覆盖旧projection后冷读如何失败；预算是per-target而非global；哪些成熟度仍不能声称。
首次独立整链评审NOT READY保留于 `acceptance-repair-minimal-design-review-20260907.zh-CN.md`。
2026-09-08仅针对两项P1修订：§4.1区分直接patch父与批准scope基线，限定现有祖先链重建义务；
§5.1按真实入口列明中断切片，明确普通部分状态没有retry/abort出口，不冒充自动recovery。
新增§8测试13/14是实现门槛，当前尚未运行；同一独立评审者已只读复核并给出设计READY，
无剩余P1，记录见 `acceptance-repair-minimal-design-delta-review-20260908.zh-CN.md`。
这不是实施完成或ENFORCED/PROVEN声明；下一步按§7先批次A，再批次B，不增加新设计轮次。
若必须新增持久协议或永久迁移机制才能成立，执行§1停止条件，不扩大设计。
本轮核验：既有publication/outcome测试8/0/0；真实publication临时反例确认F3已被拒绝；
git diff --check通过。上述不构成F1/F2实施验证。
