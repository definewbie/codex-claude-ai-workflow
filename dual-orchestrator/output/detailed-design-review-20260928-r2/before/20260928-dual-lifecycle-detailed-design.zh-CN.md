# Dual 生命周期可靠性：详细设计候选

日期：2026-09-28。阶段：P0。状态：候选版获用户带回的 CC 设计层 PASS（1 WARN、3 LOW）；本轮修订及 D04 静态核验未经独立复审，处置见 §14。仍不是 Implementation Ready，不授权代码实施、提交或真实运行。

上位契约：[R3](20260923-dual-lifecycle-reliability-design.zh-CN.md)。唯一工作索引：[主清单](20260927-dual-lifecycle-worklist.zh-CN.md)。本文展开 D01–D10，不另建工作清单或降低 R3 验收。全文“增加、迁移、必须”描述拟议目标；现状另行标明。未落实的能力不能用于当前运行准入。

## 1. 本次结论与现状地图

推荐继续沿现有 core→ledger/runtime→acpx/Git→统一 read model 的边界实施未来改进，不更换调度平台、领域模型层或 workflow DSL。改动的实质是：分开两种批准、固定失败事实的产生阶段、补齐本地提交与外部未知结果的边界。

| 现有事实（本轮读码） | 直接设计后果 |
|---|---|
| TaskContract 用一份精确 Scope 和字符串验收清单；compile 在设计前，qualification 专认 contract_review | 需要合同阶段及条款身份；不能只后移 gate 或把空 scope 当全范围 |
| AuthoredStageLineageResolver.approval 从实际人门事件认定 design/plan 批准 | 自动评审通过与人类授权必须分别表示，不能生成虚假的 approved gate |
| AgentExecutionOutcomeResolver 在不完整 process 前检查 envelope/artifact；validator 与 finalizer 再次判定 | 同一次执行的阶段事实须固定；发布受阻不是新的 artifact 语义失败 |
| RoutingEngine 先写 node，再做其他写入/路由；AgentTask 与 execution 有独立 writer | 本地多文件提交必须覆盖这些桥接，不能只包一个 resolveGate 方法 |
| observe/review 开 terminal；Claude allow_write 含工作区；transport 的 policy binding 仅适用 Claude | profile 名字与生成策略文件均不是只读证明；D04 保留具体可行性阻塞 |
| DeliveryMergePlan 有 READY/APPLIED/COMMITTED/FAILED；apply 后日志和 plan 可能失败 | 可复用状态与 failureCode，但须增加明确的外部操作意图/观测，不能从 FAILED 推导未应用 |
| LiteFlow 全局 holder 的 load/execute/clean/close 没有进程内共同互斥 | 冻结 kernel 身份不能解决并发，必须单独覆盖完整 holder 生命周期 |

Definition 继续是 JSON/loader；Instance 与人决定继续是 ledger；Execution 继续是 task/execution/run；Artifact 继续是 parser/validator 和不可变来源；Git 是实际应用/提交事实；RecoveryAssessment/WorkstreamAttention/query 只解释和展示。本文没有给 UI、prompt、报告或 proposed schema 新增授权权力。

本轮从这些消费者回溯了主要契约，但未审计全部既有代码。读取范围与内容指纹见 [source-index.json](../../output/detailed-design-20260928/source-index.json)，开始时 [baseline.json](../../output/detailed-design-20260928/baseline.json) 绑定 HEAD 与 1318 文件。新配置、adapter 实际工具隔离、全部 reader 迁移和故障测试尚未执行；不能因下述设计完整表述就宣称 PROVEN。

## 2. D03：分期批准及有界推进

### 2.1 采用同一 TaskContract 的两个用途，保留独立批准 occurrence

推荐扩展现有 TaskContract/validator，不新增平行 GoalContract 平台。新版本合同明确 `phase=GOAL|EXECUTION`，不以 status 或 scope 是否为空猜阶段。旧版本不按新语义运行。

| 字段/关系（拟议） | GOAL | EXECUTION | 校验 owner |
|---|---|---|---|
| id/version/phase/source | 身份、原始需求、已批 proposal 引用及 SHA | 新合同 occurrence，绑定已批 GOAL 的 ref/SHA/decision | ContractCompileHandler + TaskContractProjectionVerifier |
| objective、acceptance/test 条款、constraints/nonGoals | 保留 proposal 的稳定 clause id 与正文；编译不得改变已批含义 | 原目标条款不可静默删减或重编号 | proposal/compiler/TaskContractValidator |
| scope.included | 空列表，明确表示无产品写授权；read context 不是写 scope | 精确 canonical 文件 + CREATE/MODIFY；仍不引入 DELETE/任意目录授权 | TaskContractValidator / scope 校验 |
| scope.excluded | 已确认禁区 | 原禁区及合法进一步收窄 | 同上 |
| design/plan refs | 不要求 | 当前已完成语义复评的两个 candidate/ref/SHA/reviewer occurrence | AuthoredStageLineageResolver |
| verification bindings | 已知约束可记录，不假定环境已证明 | §3 的必需验收出口及证据/责任引用 | 现有 plan/contract validators |
| authority refs | 目标人门的决定引用 | 精确 scope/必要权限人门的决定引用；已有 grant 只引用，不在合同造 grant | RoutingEngine + qualification |

字符串验收清单需迁移为保留 id 的条款表示，或直接引用不可变 proposal 条款；推荐在 TaskContract 中保留 `{id,text}`，删除同义平行字符串清单作为 authority 的路径，Markdown 由它派生。所有消费端同步迁移；不兼容读取新合同为旧 `List<String>`。这里是现有模型的字段调整，不是新增 model 层。

首次流程：proposal 与产品复评→`gate.human_requirement_review` 批目标→编译 GOAL→只读 design/review→只读 plan/review→编译 EXECUTION→`gate.contract_review` 批精确范围和必要权限→implementation。GOAL 编译是已批准 proposal 的确定性投影，不增设第二次相同目标审批；编译失败停止，不偷加范围。EXECUTION 编译用已批准 GOAL 和复评候选，不从 Markdown 猜命令/路径。

合同、resolution 和编译结果改为 occurrence 固定路径；可保留 `task-contract.json` 作为明确的派生展示，但 qualification 不得再只读被覆盖的 latest 文件。新资格接口要求调用者声明 GOAL 或 EXECUTION，严格校验 phase、原始批准、全部 ref/SHA、当前有效 epoch；实现、自测和交付永远不能以 GOAL 资格通过。现有 `requires_approved_task_contract` 迁移为明确的阶段要求；四份定义与 handler/contract registry 一起迁移，不让缺配置默认为已授权。

### 2.2 四变体逐门对照

F/C/T/S 分别为 full、contract、terminal-delivery、small 实验定义；small 仍是测试资源，不冒充第四个 bundled。

| 当前 gate | 当前变体 | 目标处理 | 接续与不能绕过的条件 |
|---|---|---|---|
| human_requirement_review | F/C/T/S | 保留，批准目标/验收/禁区 | revise→原作者→产品复评→同类新 occurrence；正确身份与 note |
| contract_review | F/C/T/S | 后移到 design/plan 复评和 EXECUTION 编译之后 | approve 才给精确写权限；clarify 回到相应提案/复评/编译；reject 终止当前申请 |
| human_design_approval | F/C/T/S | 默认正常通过路径不再强制等待 | 保留 reviewer；有效 READY 固化 reviewed candidate；BLOCKED、重大取舍或预算耗尽仍用现有人门；人工 revise 有 occurrence 与 subject，不伪造 approve |
| human_plan_approval | F/C/T/S | 同上 | 无精确范围批准前只能提出建议；通过后进入 execution contract compile |
| human_implementation_repair | F/C/T/S | 保留需要人解决的缺项/范围/权限处置 | 可自动处理的已批范围内错误沿原有有界 loop；collect/submit 不默认改代码；request_action 不 resolve |
| human_acceptance | F/C/T/S | 保留唯一具体交付候选的最终批准 | 绑定最新验收/报告/candidate；revise 回相应返工再自测/验收；目标 Git 尚未应用 |
| human_next_stage_approval | F/C | 移除正常交付中的重复强制停顿 | handoff/next prompt 作为信息产物在最终批准前完成；不自动启动下一阶段，新目标仍须审批 |
| 无 next-stage gate | T/S | 保持无此停顿 | 与 F/C 一样终态只读；差异只保留既有交接材料是否生成 |

最终 candidate 准备仍由 `delivery.prepare_pr` 等现有 producer 完成，移门时同步检查 CompletionAuditWriter、候选批准 resolver、终态判定与 CLI hints 的硬编码。任何 handoff 后续会改变产品字节的路径都不能放在候选批准之后。删除冗余人门不删除 Codex 语义评审，不把 reviewer READY 记录为 human_action=approved。

AuthoredExecutionContext 中当前 `Approval` 与 `approvedDesign/approvedPlan` 的消费改为区分 reviewed candidate 与 execution authorization 引用。可在现有 snapshot 中调整字段，不设第二套审查账本。设计/计划 reviewer 必须精确绑定作者 occurrence；执行批准绑定这两个被评候选。执行批准的作用对象是合同中的目标/精确范围/权限/必需验收；合同保存初始 reviewed design/plan refs 作为编制来源，不把后续所有技术实现细节都变成人工权限变更。实际每个 task 同时引用不可变执行合同与当前有效 reviewed candidate，资格验证二者关系。新 plan 必须经过当前语义复评，并由程序核验仍在同一目标、文件/操作、权限及必需验收内；满足时可在原执行授权内继续，不生成新的人类批准。超出任一批准边界则进入新的执行批准。

### 2.3 READY 自动推进的正向账本表示（WARN-1 修订）

选择正常 READY 路径不实例化人工门：`design.decision` 完成后进入 plan 的上下文/作者链，`plan.decision` 完成后进入 EXECUTION compile。gate definition 仍保留给必须人工处理的分支；不创建自动 resolved 的 gate，不写虚构的人决定。READY 只是必要条件，资格、预算及待处理的重大取舍检查仍可阻止自动推进。

`ReviewDecisionHandler` 核验实际 reviewer 产物与作者谱系后，在既有 decision `NodeResult.facts` 中形成拟议 `reviewed_candidate`；ledger 是完成节点的持久化 owner。该事实包含 stage、author/reviewer/decision occurrence、作者产物与 review sidecar 的不可变 ref/SHA、实际 review decision、适用合同 phase/epoch/ref/SHA、核验规则身份与结果。它是程序核验后的复评事实，不是模型自报的 approved 标记，也不产生 scope/权限 authority。已完成 occurrence 的事实不得被后续复评覆盖。

| 路径 | 持久事实与下一消费者 | 投影及失败处置 |
|---|---|---|
| READY 且自动推进资格成立 | decision result 固化 reviewed_candidate；AuthoredStageLineageResolver 从实际路由谱系解析它，向 planner/compiler/后继 task 提供精确引用 | 展示“复评通过／程序固化”，人工批准另由真实 gate 引用显示；无 gate 不得推导已批准 |
| READY 但重大取舍、预算或明确人工介入要求等待 | 可保存真实复评事实，但实际后继为现有人工门 occurrence；保留触发理由及 subject | 展示“复评通过／等待人工处置”，不展示可实施；人门决定不替代 GOAL/EXECUTION 批准 |
| NEEDS_REVISION 或 BLOCKED | 保存真实 review 与义务，经有界返工或相应人门处置 | 不生成通过的 reviewed_candidate；人同意解决办法也不能伪造 reviewer READY，须回适用作者/复评路径 |
| 提交未完成或来源不一致 | §6 pending 或校验失败，后继不得取得可用 reviewed 引用 | BUSY/pending/失败分别显示，不能凭已落盘的一部分 facts 显示推进成功 |

decision 完成（含 reviewed_candidate）、确定的后继节点/input refs、route event 和 current/instance 纳入 D06 同一次 prepared 写集；已在前序提交完成的作者/reviewer 产物以固定 ref/SHA 引用，不重新发布。后继 input refs 明确绑定该 decision occurrence；不能搜索任意“最近一次 READY”替代当前谱系。读写会话内复核本次源身份，缺事实、跨轮引用、相同路径字节漂移或 pending 均不授予推进资格。重启仅补完已准备的同次提交，不重新评审、重新路由或生成批准。

人工门路径保留真正 action/subject/决定证据，与上述 reviewed 事实分字段消费。人 revise 后必须走作者修订→复评→新的 decision occurrence；READY 后进入人工处置也不能借旧 candidate 为改过的字节背书。AuthoredExecutionContext、qualification、CLI/API/UI/report 从统一 read model 分别表达 `reviewed` 与 `execution_authorized`，不以一个笼统 approved 布尔值合并。反证须覆盖正常 READY 零人工 gate、人门合法 revise 后续作、伪造 human_action 无效、各提交前缀冷读与冷启动，以及 design/plan、四变体与 inline/worker 的共享消费者。

上述为设计选择，当前 ReviewDecisionHandler 仅记录 decision/source/summary 等事实，AuthoredStageLineageResolver 仍从人工门解析 Approval；没有宣称新字段、写集或投影已经实现。完整字段实例和逐消费者迁移仍按 §13 冻结。

### 2.4 失效与预算

| 变化 | 仍可保留 | 必须重做 |
|---|---|---|
| 目标/必需验收/禁区改变 | 历史记录可观察 | GOAL 新批准、受影响 design/plan 复评、EXECUTION 新批准、后续验证 |
| 文件/操作或权限改变 | 目标未变时 GOAL 可保留 | 相关 design/plan 与出口核验、EXECUTION 新批准；旧授权不得覆盖新增范围 |
| 设计/计划字节改变但目标/文件操作/权限/必需验收均未变 | GOAL/EXECUTION | 当前 candidate 重新复评，程序逐项验证仍满足原执行合同，新的 task 绑定新 reviewed ref；不能冒充旧 reviewed candidate，也不强制重复人工批准 |
| 仅源码在已批计划范围内返工 | GOAL/EXECUTION（先核验仍适用） | 自测、acceptance、candidate；旧 ACCEPT 失效 |
| 只补证据、源码及合同不变 | 同一执行基准 | 补证据→self-test→acceptance，不能复用旧缺证据判定为成功 |

自动语义修订沿用 definition 的 max_revisions，不静默增加次数。把同一流程中原 normal 人工补证据/返工边也计入有界处置记录，不能绕过原预算无限往返。预算耗尽进入既有 gate/recovery，保留原始 author/review、note 和义务；人可批准一次明确续作或终止，不能伪造 BLOCKED sidecar 来制造修订基准。数值沿用现有配置，改变预算上限属于明确的新决定。

## 3. D05：验收出口与执行批准一起冻结

推荐扩展既有 Plan 和 EXECUTION TaskContract，使用同一组稳定验收 id，不引入任意命令 DSL。Plan 提议，compiler 规范化，用户批准合同，已有验证器检查。实际执行仍在既有 agent 或 external prepare/submit 路径内，不新增自动测试执行器。

每个必需项的拟议绑定：`criterion_id`、`method`（描述方法，不是 shell authority）、`executor=AGENT|OPERATOR`、`environment_refs[]`、`authority_refs[]`、`evidence_kind`、`feasibility_refs[]`、`detail_owner`。必需性由已批目标条款决定，不能由 reviewer 添加一个 required=true 自行扩大验收；可选检查独立列出，不成为隐藏准入门。

| 检查时点 | 必须成立 | 失败出口 |
|---|---|---|
| GOAL 批准 | 目标和必需验收 id 清楚，未知环境约束明确 | 需求澄清，不冻结虚构命令 |
| plan 复评/EXECUTION 批准前 | 每个必需项有可行方法、实际环境/权限证据或已落实 operator 路径；责任明确 | 指明缺项和解决者，不能批准一个无出口的实施计划 |
| 首次及后继实现 dispatch | 原绑定/批准仍有效；外部能力证据身份未漂移 | 输入准备失败，零 provider；不因可选工具不可用阻断 |
| self-test/acceptance | 当前源码/合同的实际证据，跳过/缺项不能成功 | 缺能力→处置 gate；缺材料→补证据；代码错误→返工 |
| 最终交付 | 每个必需项完成且有对应 evidence；独立 acceptance 的结论与基准一致 | 不得以模型 summary/exit 0 代替缺失证明 |

可信边界：program-owned execution record 可证明命令、环境、退出和执行身份，但仍不能证明业务断言充分；operator 的 Declaration 只证明“提交者声明”，绝不转成 machine-executed。材料哈希只证明字节。若已批验收要求机器执行证据，operator 声明不能满足它；需要新的采集能力时，先比较既有日志/程序记录能否满足，重大取舍交用户。不得为了统一格式默认新建 runner。

晚绑定细节允许：实现前方法/环境/权限/责任已经可行，具体测试名称可随实现细化；细化不得改变目标、扩大权限或把真实执行推迟到交付后。同来源 identity 未变的环境证明可复用，不每次做任意浏览器/数据库全矩阵探测。

### 3.1 两个合法流程样例及其反例

以下为设计样例，不是已运行 case，也不授予示例文件写权限。用途是检验字段能承载用户流程，而非按当前测试 fixture 倒推需求。

| 样例 | 目标批准 | 计划后执行批准 | 实际完成证据及对照 |
|---|---|---|---|
| 新增 CSV 转换器 | A1 输出字段正确；A2 非法输入给明确错误；禁改其他产品功能。GOAL included=[] | plan 提议 CREATE `src/convert.py`、CREATE `tests/test_convert.py`，每条 method 对应 A1/A2；本地 Python/测试环境来源可核验，才批准 EXECUTION | 当前源码的真实命令记录+断言结果→self-test→acceptance→candidate；只返回 exit 0 而缺 A2 断言不能通过；多改一个配置文件必须重新申请 |
| 已批网页修复需人工设备验证 | A1 桌面功能；A2 指定设备上的触控行为，允许 operator evidence | AGENT 负责 A1；OPERATOR 负责 A2；设备可用性与负责人已落实、prepare/submit 入口可用；不先索要任意外部网络权限 | 实现后固定 source baseline，operator 提交声明与明确材料，自测/验收按已批证据标准判断；材料 hash 不变不证明设备操作发生；源码改变后旧准备拒绝 |

若第二例的目标要求机器可审计执行记录，而现有外部材料只有人工声明，设计应在 EXECUTION 批准前判缺出口；可以提出补采既有可信日志或更换方法，但不能改名“operator evidence”绕过原验收。若第一例 Python 环境已证明且身份不变，不新增浏览器/容器前置。

新增绑定的 JSON 形状（仅局部字段示例，非可投入运行的完整合同）：

```json
{
  "phase": "EXECUTION",
  "acceptance_criteria": [{"id": "A1", "text": "转换结果符合已批准字段规则"}],
  "verification_bindings": [{
    "criterion_id": "A1",
    "method": "以固定输入和预期结果比较转换输出",
    "executor": "AGENT",
    "environment_refs": [],
    "authority_refs": [],
    "evidence_kind": "PROGRAM_EXECUTION_AND_ASSERTIONS",
    "feasibility_refs": [],
    "detail_owner": "self-test"
  }]
}
```

示例故意没有填造环境事实；因此它可用于讨论字段，**不能通过执行准入**。完整实例必须引用实际核验过的不可变证据。禁止把这些空列表默认为“环境已经可用”。

## 4. D01：决定—被拒前置的字段与消费

### 4.1 在既有 gate.result / NodeResult 中补事实

RoutingEngine 在接受决定时记录 `decision_checks`，由实际校验函数报告，不能由 UI/模型填写。既有 gate/事件仍是批准 authority；这些字段是其核验依据。

| 拟议字段 | 约束 |
|---|---|
| decision occurrence / requested_action / canonical action | 复用 gateId/nodeInstanceId/eventId；用户 action 与规范结果都保留，不由下游重新翻译 |
| source occurrence / subject refs+SHA / contract phase+epoch | 来自已核验来源，绑定决定时的字节；不能引用会改变语义的 latest |
| checks[] | 每项 rule_id、rule_version、consumer、input_refs+SHA、result=PASSED\|NOT_CHECKED、理由；仅可信 checker 可写 |
| check coverage | 明确该决定验证了哪些前置、哪些留到消费时检查；缺记录是 UNKNOWN，不是 NOT_CHECKED |

下一实际 resolver/validator 拒绝时，经 AgentExecutionResultFacts 产生 `continuation_failure`：`decision_ref+SHA`、`action`、`source_occurrence`、`target_occurrence`、`subject_refs`、`consumer`、`phase`、`precondition_code`、`rule_version`、`observed_refs+SHA`。字段必须与当前 target 的输入谱系一致。普通输入准备失败如无 resolving decision 来源，不强行填一个伪 gate；该配对只在适用时必需。

现有 inputPreparationFailed 使用封闭对象相等校验；扩展应由同一入口提供合法构造及验证，不允许任意 map 附加字段绕开它。沿用原 failure_code/primaryReason，配对仅解释冲突，不产生第二控制原因。

### 4.2 RecoveryAssessment 唯一解释

| 决定时证据 vs 消费时证据 | 对外解释 | 副作用/动作 |
|---|---|---|
| 同一规则/身份/输入，已 PASS，却被同一适用前置拒绝 | 契约/实现矛盾 | 保留原失败，禁止普通自动 retry；修复规则后仍须版本政策 |
| 决定时与当前内容/环境身份确实不同 | 漂移或操作缺项，列实际差异 | 不复用失效批准；按漂移对象补材料或重新申请 |
| 可信 coverage 明确当时未检查 | 消费阶段缺项 | 列解决者与合法动作；补足后重新核验，可按适用流程续作 |
| 决定/规则记录缺失、哈希不可信或配对缺失 | 诊断不足/记录未完成 | 不猜矛盾，不静默 WAITING，不展示可安全 retry |
| 错 workstream、跨轮、伪造 subject | 非法输入 | 零批准、零后继 provider、零成功发布 |

失败节点与配对放进 §6 同一 prepared 本地变更；写入未完成时 reader 不能宣称“失败已完整记录”。新的 gate 决定强制记录规则身份；不回填旧历史。对记录不完整的观测不会授予恢复旧样本的权限。

## 5. D02：阶段事实、原因保真与发布

### 5.1 拟议执行序列

`qualified input → prepared dispatch → external execution → typed process/signals → envelope acquisition → domain validation（仅内存）→ outcome resolution → run completion evidence → sidecar publication → node/task/execution 完成并路由`。

运行 facts 的 owner 仍是 transport/attempt，artifact facts 的 owner 仍是 validator，最终 outcome 的唯一语义 owner 仍是 AgentExecutionOutcomeResolver。AgentRunMetadataFinalizer 持久化同一已决事实，不从当前 sidecar 是否存在重新构造另一份语义失败。run 元数据缺失不能在新运行组合中落回 fabricated exited-zero/legacy facts。

建议把 ArtifactContractValidator 的“领域校验”和“文件发布”在同一现有类内拆开调用时点，不新建 pipeline 平台：先验证候选获得真实合同结果，再统一判断发布资格；被 execution 阻止时保留合同的真实校验结果，不产生 EXECUTION_NOT_PUBLISHABLE 的伪 artifact failure。sidecar 写入 I/O 是 persistence failure，不能冒充模型格式失败；节点不成功，并按 §6/§7 恢复已验证本地结果。

### 5.2 决定性原因矩阵

| 事实组合 | 主原因/结果 | 必须保留的诊断与禁止行为 |
|---|---|---|
| 未 dispatch，准备校验拒绝 | input_preparation_failure 原 code | providerInvoked=false；无伪造 provider run/process；D01 适用时含配对 |
| bootstrap/session/permission 启动失败 | transport 的 typed signal/启动事实 | 与完全未调用区分；未取得进程句柄不自动证明没有任何外部活动 |
| 活跃阻断 signal + 任意候选 | 原 signal，除经规则证明可容纳的权限警告 | JSON 有效不使阻断消失；保留 timeout/中断等同时发生事实 |
| timeout/stall/interruption，无优先阻断 signal | process 原因 | 不被缺 envelope/artifact 覆盖 |
| incomplete process / killed，无优先阻断 signal | process_output_incomplete | 即使 envelope 完整、artifact 发布受阻也不改写 |
| process 完整可信，envelope 缺失/非法 | agent_result_missing_or_invalid | 不是输入准备失败；不改标记规避真正坏输出 |
| process/envelope 合格，领域校验失败 | required_artifact_contract_failed | 显示真实 failed contract 及原因 |
| 全部合格、允许的 completed warning | SUCCESS_WITH_WARNINGS | 所有必需义务仍须满足，保留警告事实 |
| 全部合格且无 warning | SUCCESS | 业务 decision 仍由已验证 artifact 决定，SUCCESS 不等于 acceptance ACCEPT |
| outcome 成功但本地发布/提交未完 | 运行结果可保留，workflow 不成功 | 显示持久化未完成，不能重新调用 provider 来补本地写 |

保留阻断 signal 的既有目录顺序作为当前优先政策，详细测试绑定其适用语义；不把因果关系简化为交换两行代码。contained permission warning 必须同时满足可信完成、有效 envelope、全部必需产物，并能从已批准义务/验证出口证明被拒动作不是必需项；缺该关联证据不容纳。模型说“不需要”本身无效。若需改变现有 contained 判据，连同成功对照审查，不能全部拒绝来逃避合法成功义务。

### 5.3 必须进入源码的反证

在现有 WorkflowFirstLifecycleSequenceTest 常驻 E02 的 inline/worker 情形：不完整 process + 有效候选→无 sidecar、最终 node/run/recovery 主因仍为 process_output_incomplete；再加完整成功、可容纳权限警告、不可容纳权限拒绝、完整 process 下真坏 artifact 对照。常驻测试不得运行时临时改生产源码。

AgentExecutionOutcomeResolverTest 覆盖矩阵组合；AuthoredManifestDispatchTest/RetryFailureContextSequenceTest 保留零 dispatch 的合法输入修复序列；RecoveryShowCommandTest/WorkstreamApiControllerTest 及 report/UI 对同一事实保持一致。恢复旧 precedence 或重新将未发布当合同失败时，E02 必须失败；去掉所有候选发布也必须被成功对照抓住。测试 oracle 来自上表用户流程，不能读 resolver 输出生成期待值。

## 6. D06：本地提交、reader 一致性与 event 幂等

### 6.1 方案选择

推荐 R1 的 prepared mutation，限于现有 file ledger 内的物理写入协议。现在重审的依据是用户明确要求未来同冻结版本续作（R3 I5），而不是新增一个 synthetic 事故来重开历史恢复。

| 方案 | 对用户要求的适合性 | 判断 |
|---|---|---|
| transition-specific reconciliation | 缺初始化 after-state，新增形态继续扩大守卫接受空间 | 不作为统一未来恢复协议；不为旧样本补特判 |
| prepared mutation + 统一读写边界 | 保留现有路径、覆盖初始化及多文件写、可限定前向新实例 | 推荐；真实成本是 writer planning、reader 入口与 M6 桥接，不称小补丁 |
| generation pointer | 一致快照清晰，但重做文件布局/历史 refs/event 存储 | 此目标下不选，避免把本轮变为存储迁移 |

manifest 是本地 after-state 的耐久载体，不决定新业务动作：合法 domain transition 先计算并校验，manifest 只负责完成同一次提交。恢复不调用 flow kernel 重算后继，不再次批准，也不执行 provider/Git。允许增加 module-private 数据记录和提交 helper，不新增领域状态层。

### 6.2 字段和写序

拟议 `pending/<mutation_id>.json`：schema、冻结 runtime identity 引用、workstream/operation/request identity、base sequence、完整目标文档列表 `{relative_path,before_sha_or_absent,after_bytes,after_sha}`、event 的确定 id/sequence/完整 bytes、预期 event 前缀长度与 hash。只允许 WorkstreamPaths 已定义的本地控制文档及当前 artifact 发布目标；拒绝任意路径、链接逃逸和执行命令。after-state 在落第一个业务文件前完整且可独立验证。

1. 在统一 workstream 写入会话中持锁，核验运行身份、无未处理 pending、pre-state/批准、当前基准。
2. 计算所有 after-state、后继/iteration/event identity；固定 UUID、时间戳、route 和 I1 checks，不在恢复时再生成。
3. 原子提交完整 manifest。此前失败无业务状态变化；允许无 authority 的临时文件，不能被 reader/dispatcher 采纳。
4. 逐个幂等写 after-state；每个目标只接受 before hash 或 after hash，其他状态停止并报告损坏，不能“尽力覆盖”。
5. 幂等写 event，再写 current/instance 等派生/索引。单个文件要求同文件系统原子替换；不支持时拒绝启动新协议，不静默退为非原子 move。
6. 核验全部 after hash/event，然后移除 pending，释放锁。删除 pending 失败仍是待核验的同次提交；重启验后再完成，不重复事件。

event 同 id/同 bytes 视为已写；同 id 不同 bytes 为 INVALID。若 append 中断留下尾部碎片，只在 manifest 绑定的已确认前缀未变且尾部精确匹配本次 event 前缀时，截断该未提交尾部并重写本次 bytes；既有提交事件中间损坏不得修复。完整 payload 缺失或 checksum 不符时，不猜路由。

### 6.3 reader 与锁的强制边界

现有入口外层持锁、内部散落直接读取的方式不能满足目标。推荐由 WorkflowFileLedger 提供显式读/写会话；保留 WorkstreamLock 文件锁能力，移除同一路径的嵌套二次获取；同进程与跨进程读写均遵循同一排他边界。一个 query 的多个文件读必须在一个会话中完成，不是每个 load 单独检查 pending。

普通 query 只读：锁不可得返回 BUSY，看到 pending 返回持久化未完成及有限身份信息，不组装混代状态，不自动修复。resume/write 会话在新 domain 动作前完成合法 pending。reader 持锁期间 writer 不进入；不能靠“读前后各看一次 pending”防止中间发生完整提交的竞态。

必须迁移：WorkflowFileLedger/内部 writers、TaskContractProjectionVerifier、AuthoredStageLineageResolver、EvidenceCollection/ExternalVerificationEvidence、DeliveryApplicationService、runtime/worker claim 与 reconciler、CLI/API/query/report 的直接文件读取。静态搜索只是候选清单，不能证明绕过不存在；使用阻塞中间写点的并发 reader 测试，证明每个公开入口只返回一致快照或明确未完成。本轮生成的 [读写入口候选索引](../../output/detailed-design-20260928/boundary-access-inventory.json) 含 116 个文件、565 个匹配行，包含无关配置读取、重复调用等，绝不等于需改 116 个文件或已完成分类。完整逐方法迁移清单仍须逐条归属，不由旧 R1 的 31/84 数字替代。冻结对账须为候选文件逐项记录“改／不改＋理由”，相关方法绑定 owner、写集/读会话、消费者及证明；无关配置读取说明为何不受本协议影响。新发现入口补入同一清单，不以搜索命中数自动扩大批准范围，也不以未命中排除应迁移路径。

长模型调用期间不持多文件 mutation 的写锁；用现有 task/node execution 绑定活动执行者和 input epoch。进入完成会话重新核验，同 occurrence 的 competing claim 必须被 ledger 拒绝。不得为了无锁时能读就放宽当前 gate 或 ledger 完整性。

### 6.4 写入前缀的统一解释

| 中断点 | 磁盘状态 | 新进程解释/允许动作 |
|---|---|---|
| manifest 完整持久化前 | 原已提交状态，或未注册初始化临时目录 | 不能显示批准成功；同请求可重新计算；无足够身份目录只诊断，不猜成旧实例 |
| manifest 后、任意文档前/之间 | 完整 after-state + 部分目标文件 | 同版本写会话 finish-forward；reader 明确未完成；外部调用零次 |
| event append 中/后 | 完整 after-state + 事件尾部/完整事件 | 按上述前缀约束去重/补齐，不多记批准 |
| current/instance 写之间 | 同次 pending 仍在 | 不能混读；补齐后再公布一致状态 |
| 全部文件完成、pending 未删 | 文件均匹配 after-state | 验证后仅完成提交尾部，不重算 transition |
| 任一文件既非 before 也非 after / manifest 损坏 | 不明或外部篡改 | INVALID/需裁定，零自动覆盖；保留现场 |

覆盖所有 R1 14 前缀、新 gate checks/request_action/submit_evidence/abort、goal/completion audit，以及 queue/task/execution 完成。范围仅进程中断和可检测 I/O 失败；不承诺硬件掉电/fsync、任意外部编辑可自动修好或新 binary 兼容旧 ledger。

## 7. D07/D08：运行身份、外部执行桥接与 kernel

### 7.1 两层身份

冻结身份归 WorkstreamDocument 的创建者，初始化 manifest 一并写：Dual executable 内容 SHA（开发 classpath 则全运行输入清单 hash）、definitions、schema/validator、prompt 模板、reviewed acpx/adapter identity、kernel configured/effective、影响执行语义的配置。当前 WorkstreamDocument 无此字段，属于待实现增量；不只记 HEAD，不保存凭据。

每个 AgentTask/attempt 再绑定 GOAL/EXECUTION refs、批准 occurrence、node/iteration、prompt bytes SHA、实际权限 policy、provider/model/session 及输入 epoch。合法新 scope 修订产生新 occurrence 身份，不改变 frozen binary；配置/model/session 与预期不符时明确 INVALID 或准备失败，不能暗换模型继续。

resume、worker claim、gate action、delivery mutation 均在新副作用前核验冻结身份。历史只读诊断可以显示缺项，但旧实例无字段时不能猜默认版本取得新协议续作资格。new binary + old ledger 不自动迁移。

### 7.2 M6 与 acpx 的桥接

AgentTask 只持 dispatch facts，Execution 只持执行事实，run owner 不被 ledger manifest 接管。使用既有 PREPARED/PROCESS_STARTED/PROCESS_EXITED 等 attempt 状态，并增加必要的显式 dispatch-intent 事实；“不知道是否启动”作为恢复解释，不造第二套 workflow 状态。

| 边界 | 写序/权威事实 | 重启或重试 |
|---|---|---|
| queue 建立 | 同一本地提交创建 task、execution 关联与 node 引用 | 无 provider 意图时可完成本地排队，不重复生成 task |
| claim/prepare | 持锁唯一 claim，准备 prompt/manifest/policy；原子 run PREPARED，绑定 task+execution+node | PREPARED 且明确尚无 dispatch intent 时，可继续准备；仅见 task RUNNING 不足以判断 |
| spawn 前 | run owner 耐久写 dispatch intent，再触碰外部进程 | intent 后未有可信完成即有不明窗口，禁止自动再调用；先识别仍存活执行者，不能靠 PID 不存在推导从未发生 |
| 进程结束 | transport 持久化真实 process/signals/原始输出及身份 | 未有可信结束证据需人工裁定；不能把超时后被杀等同业务未写 |
| 领域验证/发布 | 固定 outcome/候选 bytes+hash，再按本地协议发布 sidecar/完成 task/execution/node | 已有充分完成证据只补本地写、零 provider；材料缺失不能凭 exit 0 合成结果 |
| run 完成、workflow 未完成 | run owner 的可信完成引用进入 ledger mutation | 不同文件 owner 采用固定引用桥接，不建立跨 owner 伪原子事务；缺链显示未完成 |

dispatch intent 不能只包最后一次 prompt：session bootstrap/new/ensure、prompt、格式 correction、adapter 内部重试和 stop 各有外部边界，必须绑定同一 attempt 下的具体阶段和已确认结果。当前 `--prompt-retries` 不得成为绕开 UNKNOWN 拒绝的后台重做；只保留已证明未产生需裁定副作用且符合原重试预算的情况，否则停止交既有恢复入口。session 已建立不证明 prompt 已完成；模型最终输出已收到也不证明所有本地发布完成。

人工裁定沿既有 recovery 入口扩展，需绑定 occurrence 与所观察 evidence；不得仅把 retrySafe 改为 true。现有 `retryActiveNode(paths,note)`、`recoverStaleRunningNode(paths,note)`、`abortRecovery(paths,note)` 只有 note，不足以承载机器可验证的裁定，不能声称现成入口已经满足。

推荐给现有 retry/recovery command 增加显式结构化裁定参数，进入同一 ledger validator：旧 attempt ref/SHA、当前 node/input epoch、operator 决定、停止核验引用、外部结果观察引用、note。普通 retry 不带此参数时永不消费 UNKNOWN；绑定旧 attempt 或过期 source 时拒绝。`CONTINUE_LOCAL` 只允许充分完成证据下的本地补齐；`ABANDON_AND_RETRY` 仅在原执行者确认结束、外部修改已核实和新输入资格满足时创建新 attempt；`WAIT/ABORT` 不授权再执行。操作名称为拟议参数值，不是当前 CLI 已支持的命令，也不增加新的执行者身份。

此前结果仍无法判断时保持等待或保留现场后终止；不得用自由 note 伪造“已确认”。具体停止/观测引用能否由现有 transport/reconciler 提供，仍需逐入口冻结，缺生产者就不能先解锁重试。

abort 在同一本地提交中终结可终结的 node/task/current 并保留证据；若仍有运行进程，先请求停止并核验结束，停止不明时不得声称取消成功。终态禁止新 dispatch；观察/导出不被禁止。

### 7.3 LiteFlow

推荐在 LiteFlowPrototypeKernel 内以进程共享互斥保护 load→execute→clean/临时规则清理和 close，覆盖所有实例；单实例 synchronized 不够。异常/finally 也在同一互斥内，close 不得清掉另一实例在用的 holder。锁顺序固定 workstream session→kernel，kernel 不回调获取 workstream 锁。

在完整并发反证通过前沿用 R3 临时约束：同 JVM 只允许一个可推进 workstream，路由与 close 串行；不能仅在 Gate 写一句。若实际装配包含另一个直接访问 holder 的使用者/独立 classloader，局部 static lock 不足，需拒绝该部署拓扑或另行比较方案。当前未实测拓扑，不宣布约束已 ENFORCED。保留默认 LiteFlow，不以切 builtin 掩盖缺陷；同一信号的两 kernel 路由等价及异常清理必须有对照。

## 8. D04：只读设计/计划的执行面与收尾核验

此项是本轮最明确的可行性阻塞，不能借用 R4/Phase B 的网络结果证明文件隔离。

推荐路径：复用 AcpxPermissionProfile、provider 的既有只读限制与 transport 启动校验；design/plan/review 使用无 terminal、无 edit/execute、禁止 approveAll 的 profile，逐次绑定实际 policy identity，fresh session 起步，correction 同样受限。`--allowed-tools` 只是免确认清单，不能当工具可见性白名单。新 profile 不能自动叠加 implement 的特许配置，也不修改用户全局 home/config。

| 面 | 已确认代码现状 | 实施前必须证明的具体点 |
|---|---|---|
| Claude | policy write 允许 workspace 写入；transport 绑定 ref/SHA；profile 有 contract_discovery 无 terminal 范例 | frozen adapter 对 filesystem deny/allow、原生编辑工具、terminal 的实际强制；worktree/Git/control 无写权限；模型不能改 policy 自我提权 |
| Codex | 走同一 ACP permission 参数，Claude binding 明确不适用；本机 1.10.0 adapter 的常规 read-only mode 实际传 workspaceWrite（见 §8.1） | 现有 mode 不足以冻结；须决定是否调整 adapter 绑定，并证明每 session/turn 的有效只读限制及高权限 session 不可继承 |
| correction/session | correction 可复用既有 session；fresh 当前仅 simpleClaudeCode 控制 | 高权限旧 session 不可复用到只读阶段；降权限必须有实际有效配置证明，否则 fresh 或拒绝 |
| ledger/program artifacts | 运行目录在工作区内，程序要写控制文件 | adapter 与程序 writer 的实际可写面分离；只按路径豁免会容许模型冒充程序，必须证明模型不可写受保护路径 |

选择标准：若现有 provider/adapter 能强制这些约束，使用其既有接口；若做不到，D04 不能冻结，不能降为 prompt+收尾检查。需要新 OS 沙箱、拷贝执行工作区或改变 adapter authority 时，比较成本与读功能限制，向用户提交具体选项；本轮不默认建立权限平台，也不启动真实模型探测。

baseline 由 AcpxAgentTaskHandler 的程序准备阶段保存到 agent 不可写的控制位置：路径类型、内容 hash、链接目标、删除/新增（含 tracked/untracked/ignored）、Git HEAD/index/关键元数据。先确定工作区及外部 gitdir 的真实边界，不能跟随链接把外部路径默认加入可写范围。进入时允许已有脏内容，收尾比较增量；不可验证的设备文件/链接/并发外部改动明确阻止成功，不自动回滚。

准备、收尾、恢复引用同一程序工件规则：WorkstreamPaths 定位路径，各现有 writer 声明本次预期输出 ref+SHA，已有 run/task 记录这些引用；不另造全局豁免 manifest。只有受保护 writer 的身份与内容均可验证才可豁免，路径命中或“位于 .agent”不足。是否现有 writer 声明已完整须逐项盘点，不能假定全部可派生。

AcpxAgentTaskHandler 在正常/异常退出都核验 baseline，再把结果交 ArtifactContractValidator；无法核验、发现增量或权限绑定失效时，零成功 sidecar、零后继 dispatch。执行面反证（写后还原也应被拒绝）与收尾反证（刻意移除比较）各自独立，二者互不替代。

### 8.1 2026-09-28 可行性核验：常规 Codex mode 不能证明只读

本轮只读检查仓库安装/配置脚本、reviewed acpx 的本地源码和已缓存的 Claude 0.70.0 / Codex 1.10.0 adapter 发布代码。源文件 SHA、定位片段及 build-info 保存在 [本轮证据](../../output/detailed-design-review-20260928/d04-feasibility.md)。缓存包与脚本默认版本对应，不证明未来实际运行解析到相同字节；D07 仍须绑定完整启动身份及实际配置。本轮没有启动 session/provider，没有执行写入攻击测试。

已确认：Codex 1.10.0 的 `AgentMode.ReadOnly` 使用 `sandboxMode=workspace-write`、`sandboxPolicy.type=workspaceWrite`；普通 `sendPrompt` 每 turn 显式传入该 policy。三个公开 mode 中未找到真正 readOnly 的主执行模式，不能只改 INITIAL_AGENT_MODE 或会话初始配置就声称满足 D04。内部 `runAgentFileChangeReport` 虽使用真正 readOnly，却是独立审计调用，不能移作正常作者/评审路径的证明。这里否定的是已核验的模式绑定方案，不断言 Codex 引擎或所有外部配置都不可能实现只读。

acpx 的 ACP `fs/write_text_file` 有自身许可检查，并非无条件写入；但这不能证明 provider 原生工具受同一检查。Claude 已有 policy→SDK/managed settings 传递，实际工具隔离仍未证明。传入元数据、拒绝一个 ACP 请求或收尾无差异，都不能替代 native edit、shell、子任务、额外工具及 session/correction 的共同约束。

| 调整选择（均未授权实施） | 目标适合性与成本 | 尚须核验 |
|---|---|---|
| 优先细化现有 adapter 的强制只读绑定 | 保留现有多 agent/acpx 主流程；让角色约束绑定真正执行 policy，而非依赖 mode 名称。需维护受审 adapter 增量及版本身份，不新建权限平台 | Claude/Codex 各自的强制接口、逐 turn/恢复/模式变更不可放宽、原生与 ACP 工具面、必要只读功能仍可用；不能只改 ReadOnly 常量就称完成 |
| 外层 OS 进程隔离 | 可独立约束子进程的本地文件写入；须区分程序控制 writer 与模型进程，新增平台及部署维护成本 | 支持操作系统、进程树/链接/外部 Git 目录、可写运行 scratch 与不可写产品/control、远端工具副作用边界 |
| 拷贝执行工作区 | 限制回写原工作区的风险，但允许模型写副本不满足当前“执行面只读”的承诺；还需隔离宿主与控制文件 | 若选此项须先明确是否改变验收目标；路径/证据身份、回传与误回写风险不能被拷贝动作本身消除 |

建议先允许第一项进入详细设计比较，仍不改 adapter、不启动模型；若现有接口不足再提交具体替代。此处涉及 adapter authority 绑定调整，按本节既定边界交用户决定。D04 不冻结，依赖该选择的实施准入保持阻塞；其他既有枚举工作不因此自动失效，也不自动升级为新的平台任务。

## 9. D09：交付外部窗口与处置

复用 DeliveryMergePlan 四状态；新增操作意图与观察字段，不新建交付状态机。建议 `apply_intent` 包含 operation id、candidate/ref/SHA、批准引用、canonical target Git identity、base/index/worktree 基准、expected tree；`observation` 保存检查到的 HEAD/index/worktree/commit 引用与检查时点。恢复的“已知/不明”由这些事实派生，failureCode 可显示未知，不能仅凭 status=FAILED 再 apply。

仅 source workstream 锁不能阻止两个 workstream 写同一 target。DeliveryApplicationService 必须对 canonical target Git/common-dir 使用同一排他锁，固定锁顺序；它只协调 Dual 自身入口，不能证明用户/其他程序无并发。每次副作用前再验目标，外部变化停止，不擅自覆盖。用户并发编辑不在自动恢复保证内。

| 写点/副作用前后 | 可知事实 | 允许恢复动作 | 拒绝的捷径 |
|---|---|---|---|
| candidate/批准核验前 | 无合法 delivery authority | 只读显示缺项 | 从 workflow COMPLETED 推导已获交付批准 |
| compatibility artifacts 前/后 | 派生文件，不是批准 | 从同一 immutable candidate 重建并验 hash | 以这些可覆盖文件替代 candidate |
| READY + apply intent 持久化前 | 未授权本次 Git 操作启动 | 在原批准仍有效时重新准备 | 没有 durable intent 就先改 Git |
| intent 后、Git apply 前/中/后 | 是否执行可能不明 | 先只读核对真实 target；不明则人工 | status=READY/FAILED 自动重做 |
| Git apply 成功、日志写失败 | 目标可能已有 expected tree | 若 HEAD/base、index、worktree、非候选路径均精确符合且无活跃操作，记录 APPLIED；日志缺失单独呈现 | 因日志失败将实际应用解释为没发生 |
| staged tree 核验或 APPLIED 写失败 | 目标事实优先 | 同上；不符则保留现场人工裁定 | 只检查 index tree 忽略未暂存/未跟踪变化 |
| APPLIED、尚无 commit | 已应用未提交 | 展示真实状态，等待显式 commit/finalize | 报错为“交付失败”或虚构已提交 |
| commit 已发生、finalize 未写完 | 目标 HEAD/parent/tree 可核验 | 绑定同一 candidate 的精确 commit、clean target，幂等写 COMMITTED | 普通 retry 再 apply 或再 commit |
| finalize 已完成但响应丢失 | plan 与 Git 仍匹配 | 返回同一 COMMITTED，不新造提交 | 重复触发外部修改 |
| 目标漂移/额外文件/错误 commit/活跃外部进程 | 结果不能归属本次合法操作 | 拒绝自动处置，明确差异，人工决定 | reset/clean/回滚用户修改来让验证通过 |

如果 intent 存在而 target 恰好仍是 base，仍不能只凭相同 tree 证明“从未执行或没有活跃写者”；自动 finish-forward 仅限已知充分本地事实。需要重做 apply 时须先裁定旧操作已停止、目标基准有效并显式发起新操作；原批准漂移则必须重新批准。

commit 继续由既有外部操作完成，finalize 只确认，不获得创建 commit 的新权限。无变化 candidate 不伪造新 commit；若既有合同允许 no-change，必须明确按该既有交付语义报告，否则拒绝不满足的承诺，不在本轮偷偷引入新完成类型。

## 10. D10：机器身份回显与语义义务

推荐本阶段保留严格 wire identity，不以降低校验换成功率；先消除 prompt 中重复来源和过期引用。AgentResultPromptContract 已生成带 machine identity 的完整示例，新增序列 fixture 正是消费该示例，因此它们不能证明真实模型负担已消除。

| 字段/内容 | owner | 候选处置 |
|---|---|---|
| schema/workstream/run/node | 程序本次调用绑定 | 只从当前 manifest 渲染一次，禁止混入历史 envelope；显式不匹配仍拒绝，不模糊修复 |
| baseProposalId/SHA、subject、approved refs | proposal/authored context owner | 单一当前基准区，不从自然语言重建；不自动把模型旧 base 改成当前 base |
| obligation id 列表 | 当前 review/human note 的不可变来源 | 给出唯一机器列表，响应必须逐项匹配且无重复/未知 id |
| disposition、evidence、resolvedByClauseIds、clauseChanges | 模型语义回应 + validator/reviewer | 不由程序自动填 ADDRESSED、不补造正文、不以格式 correction 替代真实返工 |

保留既有受限格式 correction；它只能按当前 strict contract 重新输出，受次数/权限/身份约束。缺义务或实质不满足仍回作者→复评，不被清理为成功。

此处没有证据支持另改 wire schema 或保证 token 降幅，所以推荐不做这项改造。D10 的最终设计关闭仍须完成 prompt/context/acquirer 的重复字段与 correction 原因盘点：列出每项来源、消费者、保留/删除理由，并对 GCTW 的真实负担明确结论；当前仅完成候选处置，不能因“保留”就宣布减负完成。

## 11. 整链 transition 与失败组合

以下是目标序列，所有本地多写点均展开为 §6 的每个前缀；包含外部行为的行再叠加 §7/§9，不能用统一模板跳过外部窗口。角色许可沿现有 operator/agent 边界；模型不得代作人决定。

| pre-state / trigger / owner | 完整写序与成功 post-state / 下一消费者 | retry/resume/abort/replay 和反例 |
|---|---|---|
| 未初始化 / operator 创建 / ledger | 冻结身份+definition+entry/current/instance/event 一次 prepared 提交→唯一初始 node | 初始化全部前缀可本地续完；未知旧格式 INVALID；无 provider |
| 当前目标 gate / 人 approve/revise / RoutingEngine | 校验 subject/checks→固定决定/义务/后继/事件→提交→GOAL compile 或作者→复评→新批准 | 缺 note/跨轮零写；响应丢失不重复批准；本地中断续完；新内容不借旧批准 |
| GOAL 合格 / design/plan / runtime+adapter | readonly 绑定/baseline→dispatch→process→校验→收尾→发布/路由→reviewed candidate→EXECUTION compile | D04 未证明不可开；语义拒绝有界返工；越权不倒签；未知执行不盲重做 |
| reviewer READY / decision handler+ledger | 核验作者/reviewer/合同谱系→reviewed_candidate+后继/event/current 同次 prepared 提交→planner 或 EXECUTION compiler；需要人工则路由实际 gate | 正常路径零人工门、零虚构批准；任意提交前缀 reader 不宣称完成；冷启零重复复评/路由；缺事实或漂移零后继资格 |
| EXECUTION 候选 / 人批准 / RoutingEngine | 验目标/候选/精确 scope/出口/权限→checks+决定+后继提交→qualified implementation | 未批准、漂移、缺必需出口零调用；scope revision 先撤销可执行资格再走新链 |
| 准备/执行结果 / runtime 或 worker | §5 阶段事实→§7 完成证据→§6 node/task/execution/sidecar 提交→真实下一 reviewer | 执行坏不发布、发布失败不改成 JSON 错、已完成不重新调用；I1 配对缺失响亮失败 |
| acceptance REJECT / 分类器或人处置 | 验当前 acceptance→分类和义务→代码返修或补证据→当前自测→独立验收→新候选 | 混合缺陷先真实修订再验证；旧 ACCEPT 不可复用；预算耗尽保留义务及合法基准 |
| 同源码缺证据 / operator prepare/submit | 固定 baseline→不可变材料+manifest→提交时重验→gate/node/event 原子本地提交→self-test→acceptance | request id 同 payload 幂等，异 payload 拒绝；材料孤立不授权；基准漂移重 prepare |
| 缺外部动作 / request_action / ledger | 记录非 resolving 请求与事件→gate 保持 open→后续合法决定携带原请求 | 重复同请求不丢事件；不假定外部已执行；记录失败不得显示已完成 |
| 目标/scope 需改变 / 人请求 / 既有 proposal 链 | 记录新修订意图并使旧执行资格不再可用→只读提案/复评→新批准→新 epoch 后继 | 旧 execution/证据不能串入；自动模型建议无新增写权；审查中不得继续旧实现 |
| 最后已确认点 / operator resume/retry/abort / ledger+runtime | 版本→pending→执行者/外部结果→本地补齐或明确人工→再准备 | 终态只读；新版本 INVALID；外部不明不自动重试；取消不明不报成功 |
| 验收/候选齐备 / 最终人批准 / delivery | 绑定 candidate+scope/evidence→workflow completion；之后 apply→真实 commit→finalize | §9 每窗均核验；COMPLETED/APPLIED/COMMITTED 分开显示；不启动下一任务 |

## 12. 验证义务、真实落点与跨批完整性

### 12.1 六维闭环证明规划（均为待实现/待验证，非 PASS）

| 不变量 / Declared by | Proven by（待新增/扩展） | Consumed by | Enforced before | Failure route | Cannot-bypass test |
|---|---|---|---|---|---|
| I1 / R3+§4 | HumanRequirementContinuationSequenceTest、AuthoredStageSequenceTest、HumanActionRequestTest；BUSY/pending 两个具体 UI 实例及 CLI/API/report 对照 | resolver→runtime facts→RecoveryAssessment；查询会话→统一 read model→各投影 | 后继 provider/批准扩展；一致资格/下一步声明前 | 原失败+配对，缺记录显示诊断不足；锁忙/持久化未完成不冒充已恢复 | 删配对/改 SHA 必失败；合法补足回到复评/批准；锁被占用与锁已释放但 pending 留存分别不得展示可安全续作或混代成功 |
| I2 / R3+§5 | WorkflowFirstLifecycleSequenceTest 常驻 E02、OutcomeResolverTest | finalizer→ledger→CLI/API/UI/report | sidecar/成功路由 | 保留最早决定性阶段原因 | 恢复旧覆盖逻辑失败；禁止全部成功也失败 |
| I3 / R3+§2/§8 | 四定义、qualification/authored 序列，readonly transport+工作区反证 | qualification→handler/transport | 首次产品写与每次后继 dispatch | 缺授权/只读证据失败；新批准闭环 | GOAL 不能冒充 EXECUTION；高权限 session/terminal/Edit/写后还原拒绝 |
| I4 / R3+§3 | EvidenceCollectionSequenceTest、ExternalVerificationEvidenceTest、REJECT 整链 | 实施准入、自测、独立验收、交付 | 首次实现及验收成功 | operator 出口或代码返工，原必需项不降级 | 删出口阻止实现；声明日志不能充机器事实；合法补证据不改源码 |
| I5 / R3+§6/§7 | R1 14 前缀、queue/execution 桥接、真正子 JVM、并发 reader/kernel | 所有 writer/read session/recovery | 新执行/读取一致结论 | 本地续完/未知人工/旧版 INVALID | 绕 session 读混代会失败；重复事件/双 claim 被拒；本地完成零新 provider |
| I6 / R3+§9 | DeliveryApplicationServiceTest + 从研发到 target Git 的受控整链 | candidate resolver→delivery→Git/query | apply 与 COMMITTED 声明 | 实际目标观测与人工裁定 | 各日志/plan 窗口不二次 apply；错误 tree/parent/dirty 拒绝；合法 finalize 幂等 |

所有证据必须成对包含合法 continuation 与非法拒绝。四变体×inline/worker 是共享控制链的基本组合；kernel 另做同一信号对照及全局生命周期并发反证，不盲目用笛卡尔积堆测试。真正进程重启测试与同 JVM 新建 ledger 对象分别报告。不同 UI gate 的身份/材料/下一步必须有两个具体实例对照，不只比静态标签。

### 12.2 后续代码边界（尚未批准修改）

| 流程族 | 必需联合落点 | 不能漏的 sibling |
|---|---|---|
| 人决定/执行事实 | HumanGateActionSemantics、RoutingEngine、TaskContractRevisionContextResolver、AgentExecutionResultFacts、OutcomeResolver、ArtifactContractValidator、AgentRunMetadataFinalizer、RecoveryAssessment | inline/worker；request_action/clarify/abort；CLI/API/UI/report |
| 分期合同/复评/出口 | TaskContract/validator、proposal/compiler/projection verifier、AuthoredExecutionContext/LineageResolver、AgentResultEnvelope/validator、四定义/registry、prompt renderer | design/plan 两条复评、scope revision、合同 latest 派生路径、completion/candidate resolver |
| 本地一致性/外部执行 | ledger writers、WorkstreamPaths/Lock、runtime/worker/reconciler、run writer/finalizer、所有读侧 session | 初始化、goal/audit、task claim、事件尾部、材料提交、查询直接 Files 读 |
| readonly/身份 | AcpxPermissionProfile、ClaudeExecutionPolicy、AcpxAgentTransport/TaskHandler、session/correction 与 pinned adapter 的现有能力 | Claude/Codex 两路径；控制目录/ignored/Git；程序工件与用户原脏状态 |
| delivery/kernel | DeliveryApplicationService、DeliveryMergePlan/Writer、GitPatchMergeService、LiteFlowPrototypeKernel、FlowKernelProvider | target 共用锁、两 kernel、close 与异常路径、投影 |

这些是影响范围，不是可直接开工的逐文件批准清单；详细设计冻结、D04 可行性和 §13 缺项完成后，按主清单 §7.1 对实际文件与新增文件进行范围对账。不能把以上各行切成独立“完成”而不交付用户流程；W08 组合准入仍需独立证明。

## 13. 本轮决策成熟度与未冻结项

本轮产出了 schema 方向、消费者、目标写序、失败矩阵、gate 迁移、方案比较和反证规划，尚不是十项全部冻结。下表使剩余工作可定位，避免“详细设计待补”无限泛化。

| 决策 | 本轮形成 | 仍需完成才可冻结 |
|---|---|---|
| D01 | gate checks/continuation_failure 字段及三类诊断、缺证据规则 | 逐个现有前置 code/rule 与消费者的枚举映射；和 D06 exact write set 绑定；独立评审 |
| D02 | 阶段顺序、原因冲突、领域校验与发布分离、常驻回归位置 | contained permission “非必需”证据如何从现有 signal 与义务精确关联；若无关联需明确不能容纳的范围；独立评审 |
| D03 | 两阶段同模型、四变体逐门迁移与批准失效；§2.3 明确 READY 无人门、decision 固化及独立资格投影 | 正向账本表示独立复核，完整 JSON（含 reviewed_candidate）、与 D06 逐写点绑定、compiler/qualification/authored/各投影 consumer 迁移对照及新旧格式/伪批准拒绝测试设计 |
| D04 | 实际代码缺口、所需绑定/收尾；§8.1 静态证据否定 pinned Codex 常规 mode 即只读的假设，并提交选择 | 用户决定调整方向后，继续 pinned Claude/Codex 有效 readonly 绑定与 writer 归属核验；强制能力仍未证明，不以 prompt/收尾或内部审计代替 |
| D05 | 必需项/出口字段、可信边界、四检查点 | 对代表性合法任务填完整样本（含 operator 路径），逐项验证已有 evidence 是否足够，避免 schema 有字段但无可用生产者 |
| D06 | 推荐 prepared、字段/写序/reader/event 规则 | 全部写集/读会话入口清单、M6 函数级桥接；核实 atomic move 与锁装配；方案独立评审 |
| D07 | 两层身份、dispatch intent、未知外部结果边界 | 现有 recovery action 与人工裁定 payload/执行者停止核验的逐入口对照；不能先宣称有合法 retry 出口 |
| D08 | 共享 holder 生命周期互斥推荐及临时约束 | 实际 JVM/类加载/holder 使用者盘点、装配入口与并发测试设计；没有证据不解除临时限制 |
| D09 | 全部已知 apply/finalize 窗口、既有状态扩展、target 锁 | Git 可观察事实与实际方法逐窗绑定、人工裁定复用入口；candidate no-change 现有语义核验，不凭本文新增 |
| D10 | 机器/语义字段分工、严格身份保留方案 | 重复提示/格式 correction 证据逐字段盘点，确定确有价值的删重或有据保留 |

这些剩余项属于同一次详细设计的证据核验与字段冻结，继续更新本文；不另开一套架构评估，也不立刻实施看哪条能过。本轮 D04 已按 R3 提交 adapter 绑定／OS 隔离／拷贝工作区的具体取舍；没有替用户批准该调整。其余未冻结项仍为真实待办，不因文档修订或候选版 PASS 自动关闭。

Boundary Review（作者自查）：DESIGN GAP。Source of truth 沿 R3 owner；投影无新增权力；本地 prepared 是持久化机制，不代作业务决定；外部未知不自动重放。无接受“以后再说”的产品债务，未冻结项明确阻塞对应实施。未运行测试/provider，未启动额外 agent，未修改源代码或既有 ledger；既有 97 项离线结果不能证明本文新方案。后续独立评审须挑战方案与剩余阻塞的必要性，不预设 PASS。

## 14. 本次评审处置与核验边界

用户带回的 [CC 原始评审](../../output/detailed-design-review-20260928/cc-review.txt) 为候选设计 PASS（1 WARN、3 LOW）。这是候选方案的评审结论，不是冻结版通过，更不是实施授权。

- WARN-1：在 §2.3/§11/§13 补正向账本表示、owner/消费者、D06 写集及投影区别；作者已修订，须独立复核，D03 未冻结。
- LOW-1：contained permission 与非必需义务的精确关联仍列 D02 冻结项；本轮未完成，缺关联证据不容纳的默认保持。
- LOW-2：§12.1 I1 补 BUSY/pending 两个实际 UI 场景及 CLI/API/report 同源对照，属于未来测试义务，未声称已测。
- LOW-3：§6.3 补候选入口逐项“改／不改＋理由”及方法/owner 归属；116 文件仍未经逐项分类，不是批准范围。
- 较早工作清单两项 LOW 已在其 D02/W08/§8 完成文字修订，本轮核实已有内容，不重复记为新增完成。对应产品常驻回归与投影证据仍待实施。
- CC 对 Claude-only policy binding 此前仅按命名推断；本轮直接核验 AcpxAgentTransport.validateExecutionPolicyBinding：非 Claude 携该 binding 会拒绝。补证不延伸为只读有效性结论。

本轮文档修订、静态可行性证据与[材料校验](../../output/detailed-design-review-20260928/material-verification.json)独立保存，旧报告/指纹不覆盖。D04 发现常规模式映射不足后，先给出取舍，不继续默认其可行或启动适配器修改。所有产品项仍 DESIGN GAP；本次没有重跑 97 项测试，没有真实调用、历史样本恢复或提交。
