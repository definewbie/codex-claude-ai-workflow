# W09 身份减负与目标级返修：合并接线设计候选

日期：2026-10-14。状态：**DESIGN_CANDIDATE / AWAITING_CC / IMPLEMENTATION_NOT_AUTHORIZED / SIXTH_NOT_RUN**。

用户授权只产设计与验证材料。本轮不修改生产代码、测试代码、客户端或配置，不重跑产品测试、不打包，不运行 native/provider/探针，不恢复第五例、不启动第六例、不增加 agent。旧发行仍包含旧契约，不能用本候选文档解释为已经修复。

## 1. 目标、事实与取舍

主目标是 P0 合法需求→作者返修→独立复评→当前 GOAL 人批→后续 EXECUTION 准入。两项改动分别去掉一次真实的无意义 SHA 转录负担、补足有限补丁无法表达的目标/禁区变更。不是 P1 token 优化，不承诺成功率或节省比例。

已确认事实（行号与 SHA 见 source-index.json；不是全仓审计）：

- 第五例正确拒绝模型的一字符 SHA 错误；程序输入、示例与基准无误，三个请求固定用量 62,136。错误发生在合并前；目标级修订后的第二次复评没有发生，不能声称已经证明死端。
- `AgentResultEnvelopeValidator:283–293` 要求模型填写 SHA；`TaskContractProposalRevisionMerger:24–30` 先查 ID 再查完整 SHA。只改文案或删除后一检查都不足以完成减负。
- `TaskContractProposalReference:77/102` 的 ID 含 node occurrence 与 SHA 的 16 位前缀；它不是无条件唯一证明，也不能代替完整内容身份。
- `ArtifactContractValidator:239–244` 明确拒绝 revision context 的 full proposal；`TaskContractProposalRevisionMerger:125–126` 保留旧 objective/excluded。有限 patch 的表达限制真实存在。
- 现有 contract registry 已允许 proposal/patch 两个 artifact type；现有 intake 复评/decision/需求门/compile 链可以承载新候选，但**接受目标级新提案的生产与消费分支尚未实施**。
- `AgentRunCommitProtocol` 已有 R01 `binding`、输入内容身份、当前 claim、R02 intent、R03 与 L03 桥接。它可以承载程序固定基准；当前并没有本文定义的 `contract_revision` 绑定，不能以既有容器代替新消费义务。

候选选型：模型只引用 base proposal ID；程序固定本次基准并核完整 SHA。普通条款返修保留 patch；**初次 GOAL 形成前**确需改 objective/excluded 时，复用 full proposal，加同一个 base ID，走既有复评和新需求门。无新节点、路由动作、锁、服务、存储或通用 patch DSL。

范围有意有界：本候选不新增已批准 GOAL/EXECUTION 的在线重写能力，不扩 acceptance/scope 的 late-rework。后者保持原规则；确需重新定义已批准目标时，停止旧流程，由用户经既有 start 入口开新 workstream、重新复评与批准。该人工新流程不是原 workstream 的自动迁移，不宣称已补齐所有 D03 目标变更路径。这个范围取舍交本次 CC 一并判断，不把“首次 GOAL 前可达”外推到全生命周期。

## 2. 模型与 owner

| 概念 | 既有 owner / SoT | 本次职责 |
|---|---|---|
| Definition | lifecycle JSON、ContractRegistry、strict result contract | 节点/决策/边不变；结果字段规则有局部变更 |
| Instance / Iteration | ledger 的 intake occurrence、trigger、parent iteration、route | 指定本轮作者与确切被复评基准，不能读 latest 代替 |
| Execution | 原执行 claim + R01/R02/R03 | 本次完整基准由 R01 固定，真实输出按原协议固定；重启不授新外发 |
| Artifact | 原 envelope parser/validator、proposal validator/reference | 模型回应是候选；程序验证后派生 sidecar/full ref，原始输出不改写 |
| Human decision | 当前需求门、L04、GOAL compiler | 用户批准当前新 subject；旧批准不转移，新候选不产生写权限 |
| Projection | 原 RecoveryAssessment / read models | 消费统一失败与当前 subject，不能自行推断“自动升级/已批准” |

权威顺序：ledger 指定本轮基准 → R01 固定这次使用的完整引用/输入 → 原 validator 强制核验 → sidecar 与 ref → R03/L03 → reviewer → decision → 当前人门 → GOAL compile。R01 是执行输入身份，不是第二份业务批准。

## 3. F1：身份减负的字段契约与接线

### 3.1 字段逐项处置

| 字段 | 模型义务 | 程序义务 / 消费者 |
|---|---|---|
| envelope schema/workstream/run/node | 保留原精确字段、原不匹配拒绝 | 当前 invocation/claim 与唯一 parser/validator |
| patch `base_proposal_id` | 必填非空且精确匹配 | 与本次固定 reference 比较，不按 ID 搜全仓或选择最近产物 |
| patch `base_proposal_sha256` | 从新模型结果契约移除 | 完整 SHA 保留在 R01 binding、派生记录与 immutable reference，程序检查 |
| full proposal `base_proposal_id` | 初始无基准时省略或 null；目标级返修时必填 | 初始显式空串/错 ID 不当缺席；revision 与固定基准精确比较 |
| obligation IDs / response / clause changes | 保留真实回应及覆盖义务 | 原覆盖、有限 delta、target 去重与独立 reviewer；不填造回应 |
| review subject / approved refs / verification requirements | 本候选不改现有契约 | 原完整身份与权限/证据消费者继续守住 |

这是一项**现有 wire contract 的局部变更**，不是“零字段调整”。保留 envelope schema_version=1；新二进制的 frozenRuntimeIdentity 与 strict 字段集合决定该运行的契约。patch 出现旧 `base_proposal_sha256` 键由 strict parser 当未知字段拒绝，不能静默忽略错误 SHA。既有一次有界格式 correction 可按新契约重新输出，但不自动删键、改 ID、补语义或改原始输出。新鲜实例不支持混用旧 binary/旧 prepared contract。

### 3.2 dispatch-time 基准固定

1. `TaskContractRevisionContextResolver` 在现有 L 读会话中核 current intake instance、trigger/parent route、确切 review/human subject、唯一 previous reference 和义务。输出本轮基准及实际用到的不可变 proposal/review/人决定引用。`TaskContractProposalReference.resolveFromRefs` 的去歧义也须比较完整reference相等，而非当前仅比较proposalId：重复同一reference可幂等，同ID但不同fullref/SHA不得择后一个。这是同owner的身份守卫，纳入兄弟与反证，不假设短ID无碰撞。
2. `AcpxAgentTaskHandler.proposalEmissionContext` 使用该同一解析结果生成 ID 与义务；不能先后两次解析不同基准再拼接。`preparedBinding.contract_revision` 放入：`iteration_id`、完整 `base_proposal_reference`、实际 routed obligations、`allowed_forms`。allowed_forms 由 resolver 的领域资格生成，模型不能生成或改变。
3. R01 `inputs` 除既有 prompt/manifest/repository binding 外，加入所用 base sidecar 和已核 reviewer/human 材料的内容身份；binding 记录当前 iteration/trigger 身份。不把整份追加 events.jsonl 的 SHA 当 route 身份；仍由既有 route reader 核验准确条目。
4. `AgentRunCommitProtocol.prepare` 先固定 R01，再按原 L 会话绑定 claim。`qualifyDispatch` 在现有会话中验证固定 binding 与当前 resolver 的基准/义务/资格一致、所有输入完整 SHA 一致。任一不符零新的 prompt dispatch。
5. 返回后 `ArtifactContractValidator` 通过当前 run/node execution 的 exact prepared ref/SHA 读取**同一 R01**；再次核当前 lineage/资格、模型 ID 与固定 base，读取 base bytes 并核完整 SHA。不是从“完成时最近 context”加盖身份；没有 R01 或基准变了不得发布。
6. `TaskContractProposalRevisionMerger` 消费已验证的程序基准：保留 ID 精确比较及完整 byte/ref/SHA 检查，去掉的只是模型 SHA 参数的比较。原 raw model JSON 仍照原字节保存；程序完整身份只写既有 `ProposalDerivation` 和发布记录。

不创建额外基准文件：新增 typed 字段放在已有 R01 binding；局部 typed reader 可置于现有 resolver/protocol 附近，不移 checker、不新建索引服务。当前 prepared API 是可承载点，不代表上述步骤已落地；具体字段见 examples.json，均为合成候选。

### 3.3 程序派生与冷读

patch 保持 derivation.kind=`revision_patch_merge`；base ID/full SHA/full reference 从**已验证 R01**取得。existing `patch_source_output_ref` 继续指向本轮 raw output，避免为名称单独迁移历史格式。完整 lineage reader 必须从 sidecar 的 run/node → 固定 claim/prepared/completion 重建，确认 derivation 等于该 R01 的基准；只核 map 自洽不够。

R03 固定 sidecar、ref、源输出的内容身份；L03 只消费固定完成，不在冷启动时重新生成 base binding、重新调用 provider 或追加诊断以洗成已完整。已准备但未完成的前缀依原 D06/D07 处理。

## 4. F2：目标级新提案的合法出口

### 4.1 两种形态，只有一个普通路径

| 当前上下文与实际变化 | 允许形态 | 处理 |
|---|---|---|
| 初次 intake，无 previous reference/义务 | 原 full proposal，无 base ID | 原初始校验 |
| 普通返修，仅条款 ADD/REPLACE、scope/evidence ADD | patch + base ID | 原 merger/覆盖/delta/target 守卫 |
| 初次 GOAL 形成前，objective 或 excluded 确需改变 | full proposal + base ID + 原 revision_responses | 本节目标级候选校验→原复评→新的当前需求门 |
| 已存在 GOAL compile/EXECUTION 授权历史，或 lateRoot/scope-rework | 本次不开放 full re-proposal | 原 patch/late 规则；不可表达的新目标交用户新 workstream，不隐式放权 |
| full proposal 但 objective/excluded 没变 | 不允许用它逃过 patch 规则 | 形态拒绝，原有限返修仍可用 |

`TaskContractRevisionContextResolver` 从已验证初始 intake ancestry 和精确触发来源推导 `allowed_forms`。不能把 `lateRoot==null` 或“现在没有 qualified GOAL”单独当作尚未批准：必须核**历史从未产生 GOAL compile/EXECUTION authority**，否则坏/缺旧合同可能假装首次流程。人门 approve 后即便 compile 尚未完成，也不能借 pending/故障撤回批准 subject 去走本出口；只有原门明确 revise 产生的新 iteration 才可返修。

这里不引入 reviewer 分类器、LLM 越权裁判或自由文本路由。作者根据显式规则选择结果形态，validator 根据可信 allowed_forms 与实际 typed 字段变化接受/拒绝；reviewer仍判断改动是否满足产品意图。模型声称 READY 不是程序的升级许可或用户批准。

### 4.2 bounded full proposal 的精确规则

- 复用 `TaskContractProposalResult`，只加 `base_proposal_id`；不新增 artifact type、result.mode、gate action、状态机或图边。
- `objective`、`scope.excluded` 可替换。比较用现有文本/条目规范化（trim；excluded 顺序按集合语义比较，不以单纯调序触发目标级出口），不做自然语言相似匹配。程序保护路径/禁区硬规则继续有效，模型删掉相关 prose 也不解除它们。
- 其余字段保持有限返修：五类 clause 只 ADD/REPLACE，既有 ID/section 不删除、不移位；所有变化仍须属于本轮 `revision_responses.resolved_by_clause_ids`。scope.included/evidence_refs 只能保留或增补，open_questions 保留；不新增 DELETE/通用全量替换。普通范围移除不由本候选授权。
- `revision_responses` 精确覆盖本轮所有 authoritative obligation IDs，不能新造/漏掉/重复。纯 objective/excluded 修改对应的 obligation 可引用候选中实际存在的关联条款；这证明显式回应存在，不机器证明语义已解决。既有相关 clause 无法表达时，允许按原 ADD 规则新增一个有意义的 clause，而非程序补造。
- 确定有 objective/excluded 的实际变化，且全文满足 scope canonical/control、evidence traceability、三类证据、验收要求；full proposal 不是格式 correction 自动选择的替代形态。correction 保持原语义形态与 base ID，不修改目标/义务。
- `ArtifactContractValidator` 对这种候选直接投影 full proposal，并写 kind=`goal_reproposal` 的原 `ProposalDerivation`：完整 base ref/SHA 来自 R01，`patch_source_output_ref` 在此历史字段名下表示本轮 authored revision output。原源 JSON不回填。
- `TaskContractProposalReference.validProposalSource` 明确区分初始 full proposal（无 derivation）与 revision full proposal（合法 `goal_reproposal` + exact R01/lineage）；patch 仍只认可 `revision_patch_merge`。unknown kind/missing derivation 一律拒绝，不能以“sourceArtifactType 是 proposal”绕过基准。
- `AgentResultDocumentProjector` 的 full-proposal 分支必须保留 derivation；`TaskContractRevisionContextResolver.resolveForProposal`、review subject reader、`ContractCompileHandler` 必须重核同一形态资格与 bounded delta。不仅在最初 publisher 检查一次，也不能由 compile 无条件跳过 revision delta。

### 4.3 复评、批准与后续

新 sidecar/ref → 本轮 Codex 以 exact new subject 复评 → 原 intake.decision。NEEDS_REVISION 仍产生下一次有界 iteration、对**新提案**返修；READY/BLOCKED 仍到原需求人门，BLOCKED 的人处置保持原语义。人 approve 只绑定当前新 subject，旧 ID/gate/READY 不得复用。

`ContractCompileHandler` 校验当前 gate、review、iteration、source/fullSHA/derivation/R01 后，原机制生成新的 GOAL 合同/epoch：included=[]，不授写。后续设计/计划→独立复评→EXECUTION 人批→实现/证据/独立验收→交付人门不变。模型/程序不能把 full proposal直接转成 GOAL 或 EXECUTION 批准。

迟到旧执行/旧 subject 不授推进；终态节点不复活。人工 revise、预算耗尽、普通 retry/恢复仍走既有 owner。没有新“自动重提案”action，也不凭 prose 或通用 retry 将失败样本升级。

## 5. 完整 transition 与失败前缀

R=run owner，L=workflow ledger owner；沿原协议不持 R 取 L，不新加锁。下表同时适用于 patch/full goal re-proposal；初始 full proposal 无 revision binding。

| 前态 / 动作 / owner | 写序与副作用 | 后态 / 下一消费者 | 失败、重试与重启解释 |
|---|---|---|---|
| 已固定 review NEEDS_REVISION 或人 revise / 原 decision、L | 按现有 D06 mutation 完成 decision/gate→iteration+route→author claim | 本轮唯一作者可准备；resolver 消费 exact parent | pending 只补既定字节；不造第二 iteration；错误触发或旧 subject 零 provider |
| 作者准备 / resolver+R、L claim绑定 | 同一次基准解析→prompt/manifest→R01（基准+inputs）→L bindPrepared | 固定输入可由 dispatch资格消费 | 解析/文件/R01前失败零 provider；R01存在但 L未绑定是 orphan，零资格；L pending按D06补齐，不重新选择基准 |
| live派发 / protocol+transport | 当前 claim/基准/inputs重核→R02 intent→原外部调用 | 仅本次调用在途 | 重核失败零新prompt；R02后崩溃可能已调用，UNKNOWN按D07，冷启动零自动重发 |
| 外部完成 / 原 adapter、validator | 原始输出/观察固定→typed解析→读取exactR01/基准→形态/覆盖/领域核验 | 合格候选或唯一typed失败 | 错ID/旧字段/坏SHA/失效上下文/非法形态拒绝；无新sidecar，不把失败改写为缺模型响应 |
| 合格派生 / 原publication、R | 先原唯一outcome→固定publication plan→sidecar（derivation）→proposal ref→R03 | 固定完成可由L03消费 | 沿原发布写序；每个前缀只补原固定字节；无R03不宣称完成；不能仅凭残留ref推进reviewer |
| 本地采纳 / L03、D06 | 核固定完成与claim→node result+后继/route/current同mutation | 新reviewer唯一消费指定新ref | mutation中断只续固定内容，不重复provider；外部UNKNOWN优先于本地解释 |
| 独立复评 / 原reviewer+decision | reviewer结果按同R协议固定→原decision→下一iteration或需求门 | NEEDS_REVISION继续有界返修；READY/BLOCKED供人决定 | reviewer未固定无决定；模型自评/旧READY/内部子任务不得替代；额度耗尽沿原门 |
| 人工批准 / 原gate、L04 | 当前subject+决定证据核验→同L持久化决定与route | 原compile消费新subject | 旧/错gate拒绝，pending无新dispatch；重复同决定只原幂等，无复活终态 |
| GOAL compile / 原compiler、L | 重核新proposal/derivation/本轮批准→原合同写集/epoch→设计后继 | 新GOAL included=[]；后续EXECUTION门才授写 | prepare/commit中断沿D06；历史批准保留为只读事实，不能绕新subject；不缓存modelSHA或latest |

abort/cancel：关闭当前 claim/权限回复仍用原 C/X；不能承诺子树已停止。retry：若已有外部 intent/result未知先D07，人决定不等于结果已执行。resume/replay：只消费支持的固定字节、不选择新形态或发新模型调用。历史第五例保持 FAIL/WAITING_FOR_RECOVERY，禁止用新程序给旧 raw output补SHA或重新发布。

## 6. 实施边界、ROI 与止损

拟议生产面集中在两个现有模块：runtime 的 envelope/parser-validator contract、proposal resolver/merger/validator/projector/reference、compile、prompt/context；adapter 的当前基准一次解析与 R01 prepare 接线。`ContractRegistry` 已有两种结果类型，原则上无需扩 artifact 列表；必须做一致性检查。原 lifecycle edges、人门、权限C/X、用量归因、验收标准、交付、内核均不改。源码级实施清单见 source-index.json 的角色；那是接线锚点，不是批准修改文件数。

收益是消除“复制64位机器摘要”这类真实误拒机会、让当前合法目标级返修可回到独立复评/人批。代价是一次 scoped result field change、R01 typed binding/强制消费、full proposal派生的一个有限kind与重复消费者核验。没有工期/1/N成本/token下降承诺，也不因一次样本要求全面减负。

停止条件：需要新增路由节点/人工action、全族仪表化、通用field selector/DSL、运行中跨GOAL自动迁移、独立基准仓库/服务、new lock、永久旧实例兼容；或既有 R01/lineage不能有界承载强制消费、输入依赖导致正常追加事件误漂移。发现即给实际调用点、缺口与替代方案，等待用户取舍，不临场删判据。

本轮不创建 backward recovery。旧 artifact 只在原 frozen runtime 下用于历史只读/审计；新runtime不继续旧实例、不接受旧模型SHA形态用于新发布。与新 runtime identity 不同的执行/prepare/实例进入准入时显式 INVALID/identity mismatch，不能以 field omission fallback 自动兼容。历史 UI/summary 能如实展示原旧固定失败，不把无法由新contract重新解析的raw输出“迁移成功”。

## 7. 六维与本轮证据边界

| 维度 | 本候选落点 | 当前成熟度 |
|---|---|---|
| Declared by | 用户本次设计授权、D03失效规则、D10本补记候选 | 有据；旧冻结验收不由候选自动改写 |
| Proven by | source-index 的现状源码、第五例固定材料；verification-plan 的将来序列 | 现状可核；新能力 NOT_IMPLEMENTED / NOT_RUN |
| Consumed by | 同R01→唯一publisher→reference/lineage→review→compile | 消费义务已具名，尚未ENFORCED |
| Enforced before | 新prompt派发、派生发布、review/GOAL采纳、后续写授权前 | 对象明确；新执行点未落码，不称闭环 |
| Failure route | 原typed failure/D06固定补齐/D07 UNKNOWN/当前人门 | 复用既有机制；新分支验证待做 |
| Cannot-bypass test | ID正反、基准漂移、缺R01、伪派生、旧人批、写点/冷启动、合法整链 | 义务见verification-plan；本轮无产品测试 |

作者自查：设计不引新authority、普通patch不降严、初次GOAL前范围与late处理分列，材料链接/输入身份另作本地检查。此自查不是独立 PASS；合并 CC 审设计完整性、范围是否足够与 ROI。未核验：未来API编译、具体faultpoint/测试实现、新模型输出遵守程度、真实主链后半段、真实新运行成本。历史1790绿灯不作本候选证明。

**下一步动作：把本包、详设§10.1和主清单§23.21合并交CC只读复审；必要问题处置后，再由用户批准有界实施。实施/回归/重冻/受影响Gate与fresh第六例真实运行授权分别办理，本轮不开跑。**
