# Dual 生命周期可靠性：详细设计候选

日期：2026-09-28。阶段：P0。当前状态：**九项设计冻结，D04 技术阻塞；全案未具备实施条件、未获实施授权**。用户带回的冻结候选 CC 评审为 PASS；D01/D02/D03/D05–D10 依该结论及两项 LOW 采纳固定设计基线，见 §13/§15.1。LOW 增量由作者核验，未另经 CC 复审。2026-09-29 已纠正实验路由：本机一直使用的 Claude Code＋GLM 原生读取通过；§8.15 已补原生写入/隐藏工具及配置/scratch 切片；本轮原生补证已获用户带回的 CC 材料层 PASS，见 §8.16；§8.17 现有硬限制接口核验未取得充分证明，两候选仍暴露 collaboration；§8.18 已离线纠正注入探针判据；§8.19 已核实同版 item 协议与关键副作用先后关系，发现尚待证明的原生 PreToolUse 拦截点，未扩展为 Rust 开发；当前无可运行 Gate、不申请预算，D04 不冻结。本轮仅修订材料，无 provider 调用、额外 agent 或产品实施；历史评审和实验保留。

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

推荐路径（按用户 2026-09-28 新决定修订）：复用 AcpxPermissionProfile、provider 的既有隔离限制与 transport 启动校验；design/plan/review 允许必要的受限执行，禁止未授权的产品/Git/control/policy 写入及其他未授权副作用，禁止绕过沙箱与静默提权。禁止 approveAll，不以工具名称白名单代替实际隔离；逐次绑定实际 policy identity，fresh session 起步，correction 同样受限。`--allowed-tools` 只是免确认清单，不能当工具可见性白名单。新 profile 不能自动叠加 implement 的特许配置，也不修改用户全局 home/config。

| 面 | 已确认代码现状 | 实施前必须证明的具体点 |
|---|---|---|
| Claude | policy write 允许 workspace 写入；transport 绑定 ref/SHA；profile 有 contract_discovery 无 terminal 范例 | frozen adapter 对 filesystem deny/allow、原生编辑和受限 terminal 的实际强制；必要读取/搜索可完成，worktree/Git/control 无写权限，模型不能改 policy 或绕过限制 |
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
| 限制只读阶段可选 provider 集合 | 仅在某 provider 的强制只读已获证明时才成立。例如若仅 Claude 可证明，可提议只读作者/评审均使用 Claude；会改变现有 Claude 作者／Codex 评审的跨 provider 分工。同 provider 的独立角色/session 不等于保留异构评审，不能假定质量等价 | Claude 当前也未证明强制只读；须核验允许集合的完整工具/session 面、禁止静默回退，并评估评审质量、独立角色及 provider/model 约束。分工改变须用户明确选择，不能自动替换 Codex 评审来让 D04 通过 |
| 外层 OS 进程隔离 | 可独立约束子进程的本地文件写入；须区分程序控制 writer 与模型进程，新增平台及部署维护成本 | 支持操作系统、进程树/链接/外部 Git 目录、可写运行 scratch 与不可写产品/control、远端工具副作用边界 |
| 拷贝执行工作区 | 限制回写原工作区的风险，但允许模型写副本不满足当前“执行面只读”的承诺；还需隔离宿主与控制文件 | 若选此项须先明确是否改变验收目标；路径/证据身份、回传与误回写风险不能被拷贝动作本身消除 |

用户已同意优先细化第一项，保留 Claude 作者／Codex 评审；授权限于详细设计核验，不是 adapter 修改或真实模型运行。其他选项仅作必要时比较，不自动切换。用户另要求成本收益发生实质偏差时停止等其确认，具体落点见 §8.6。D04 仍不冻结；其他既有枚举工作不因此自动失效，也不自动升级为新的平台任务。

### 8.2 本轮接口核验及约束边界

[绑定接口核验与成本边界](../../output/d04-readonly-binding-design-20260928/binding-assessment.md)记录本轮 13 个源码/声明文件的定点读取与 SHA，以及 pinned Codex CLI 0.153.4 的离线协议导出；没有启动 session/provider。以下为候选设计，不把静态接口存在算作运行时强制通过。

本轮有三个直接影响方案的发现：Claude SDK/adapter 支持 `tools` 可用工具集合，但当前 acpx 只转 `allowedTools` 免确认列表；acpx 新建 session 带 policy 元数据，load/resume 未同样传入；Codex 原生协议支持真正 `readOnly`；此前候选要求无 execute，现按用户决定改为必要受限执行，仍须证明合法读取与未授权副作用被拒绝共同成立。它们分别要求补足参数绑定、恢复一致性和正向可用性证明，不能合并为一个 profile 改名。

边界保持：definition 选择角色要求；已批准 GOAL/EXECUTION 决定目的与可用权限；AcpxPermissionProfile/TaskHandler 生成角色限制；transport/acpx/provider adapter 负责强制和执行事实；程序 writer 保存产物；read model 只显示事实。只读限制不能产生新写授权，也不能替代 scope/批准校验。设计、计划及其复评均适用；实现阶段仍按已批精确范围执行，不把本节变成全阶段工具禁用。

### 8.3 复用现有 request/session/run 的限制绑定

拟议在现有 AcpxRunRequest、session options/record 与 run metadata 中承载一个 adapter 私有的只读绑定，逻辑名 `readOnlyBinding`，由程序生成的不可变文档 ref/SHA 指向具体限制。它是对既有授权的进一步收窄，不是另一个 grant；没有此绑定或版本不支持的只读任务不能默认为 observe。复用既有 request/record 模型，不新增 model 层、通用策略 DSL、远程权限服务或第二授权账本。

| 拟议绑定内容 | 来源、消费及不允许的解释 |
|---|---|
| schema、role purpose、agent=claude/codex、workstream/node/run/attempt identity | Role 由已冻结 definition 决定；TaskHandler/transport 核对实际调用。不能让模型或 session 名称决定角色 |
| 已批准合同 ref/SHA/phase/epoch 与当前输入身份 | 来自 qualification/D07；binding 不能给 GOAL 增加写权限，不能借旧 epoch 续作 |
| canonical workspace、Git/common-dir、control roots、程序 scratch 引用 | 从真实路径推导并验证；scratch 不给模型任意写权限，不能包含或经链接指向产品/control。root/control deny 与程序自身 writer 分别核验 |
| 固定能力语义：必要读取/搜索及受限执行；无未授权产品/Git/control/policy 写或其他副作用；不允许运行中放宽 | 由受审 profile 翻译成两 adapter 的固定参数；不开放用户自编规则语言。ACP 工具和 provider 原生工具都须覆盖 |
| acpx/provider adapter/依赖与限制配置身份 | D07 冻结组合；记录请求值与实际生效值，缺任一不可用缓存 mode 名推定 |
| 更窄的 correction 限制、session 复用身份 | 不能大于原上限；相同名、相同 cwd 或仅相同模型都不足以复用。需要工具全禁时须有实际接口/证据，不把 --deny-all 当原生全禁证明 |

Claude 现有网络 policy 保留独立语义；只读绑定与它共同生效，冲突拒绝，不取并集扩权，也不把 Claude-specific policy 冒充 Codex 绑定。新增严格字段不能塞进 v2 文档后仍声称旧验证器理解；冻结时明确私有字段版本、序列化/合并/解析、未知格式拒绝以及旧 session 不具新资格。网络策略不在本轮扩大，不重开 R4/Phase B。

绑定必须在启动 agent 进程/建立 session 前被程序核验。provider adapter 以实际构造的 query/turn 参数产生程序回执，包含 policy SHA、engine/adapter 身份、session id、适用调用与实际限制摘要；不能转录模型“我遵守了”的正文。回执进入既有执行事件/run 事实通道，由 transport 强制消费，缺失或不匹配不得发后续 prompt。该回执证明绑定传播，不单独证明原生沙箱有效；ENFORCED/PROVEN 仍须 §8.5 的工具反证。

限制必须在可能运行的启动 hook/plugin/自动工具之前生效，不能等到回执时才发现产品已被初始化动作写入；无法在该副作用前核验配置的组合拒绝启动。模型服务连接与工具网络能力分别核对，只读绑定不新增工具网络权限，不借此扩大已有网络策略。

### 8.4 两条 adapter 的落点与正常／恢复路径

| 执行面 | 拟议落点与行为 | 已有依据及尚未证明处 |
|---|---|---|
| Claude 内置工具 | acpx 将受审集合映射到 `_meta.claudeCode.options.tools`，候选集合为 Read/Grep/Glob；其他 edit/execute/子任务能力不因 preset 或免确认列表复活 | SDK 声明和 adapter createSession 有该接口；native build 的读取/搜索可用性与实际工具拒绝未验证 |
| Claude 额外配置和工具 | 独立核验 MCP、hooks/plugins、模式更新、additional roots、query 重建；程序采用受限上限，拒绝可放宽来源 | SettingsManager 自行合并配置；adapter 覆盖 permissionMode；SDK 文档说明父 managedSettings 可能被管理层丢弃。不能只设 settingSources=[] 或 managedSettings 即宣布安全；也不修改用户全局配置来凑前提 |
| Codex 创建与普通 turn | `threadStart` 使用真正只读 sandbox；每个 `runTurn` 固定 readOnly policy、禁止提权，不从可变公开 mode 推导；核验返回的实际策略 | 本机协议有输入和创建回执；TurnStartResponse 不保证再次返回 policy，须由 adapter 从真实发送对象及冻结 session 状态记录，结合反证，不能伪造服务端确认 |
| Codex 控制入口与额外工具 | setMode/setConfigOption、load/resume/fork、review/goal/steer 等能触发执行的入口逐一归属；不用的入口在该角色拒绝，有用的入口必须同样受限；不把 mcpServers=[] 当已禁用环境 MCP | 现有代码有可变 mode 和配置合并；原生 readOnly 不等于关闭 execute。必要读取路径及原生工具选择仍是冻结缺口，不预选新增工具服务或引擎 fork |
| acpx 公共 ACP 工具 | 分开声明 fs/read 与 fs/write 能力，write/terminal 在实际 handler 再拒绝，policy 更新不得提权 | 当前 fs capability 共用布尔，文件写有许可检查；仅不展示能力不等于恶意请求无法执行；不得用 deny-all 同时破坏合法读 |
| acpx session/queue | session options 持久化完整绑定；新建、加载、恢复、queue owner 和 mode 控制均核对同一绑定并传给 adapter | 当前仅有 Claude ref/SHA 冲突检查，load/resume 元数据及两 provider 复用判断仍需补全 |

不把全部工具禁掉来替代可行性：设计作者须能读取必要代码并输出结构化方案，评审者须能独立核对必要材料；工具读取能力不足时不能只增大固定 prompt 就声称保持同等审查能力。用户已明确接受经过证明的必要受限执行，取代此前“无 execute”候选；这不授权任意命令副作用、未约束的原生编辑/MCP/外部工具或沙箱外重试。写入权限由实际限制守住，不能由 prompt 自述“只读命令”授予。必要临时写入若存在，须冻结与产品/Git/control/policy 分离的 scratch、生命周期及链接边界，不以整个 /tmp 或 .agent 为隐含豁免。

| pre-state / trigger | 必经检查、写序与成功消费 | 失败/重启/兄弟路径 |
|---|---|---|
| GOAL/当前合同合格，准备只读 occurrence | qualification→固定 binding/baseline/run PREPARED→D07 dispatch intent→创建受限 session→核对程序回执→允许 prompt | 准备写失败零外部启动；bootstrap 后才失败不得倒记“未发生任何外部活动”；禁止退回普通 observe |
| 活跃只读 session，普通 prompt 或格式 correction | 核对相同角色/输入/策略上限；更窄限制确实生效→记录具体调用 intent→发送→固定 process/tool/outcome 事实 | 不合格零该次 prompt；correction 不得继承实现权限；缺少更窄工具限制时先拒绝该 correction 路径，不假称已符合 |
| acpx 断开后重连同一受支持 session | 读取固定绑定→核验原调用结果与 D07 续作资格→load/resume 重新传递并核对限制→才允许后续合法调用 | cwd 相同不构成 policy 相同；高权限/旧无绑定 session 拒绝；结果未知仍由 D07 人工裁定，不因只读推导可盲重放 |
| 请求 mode/config/provider/fork 变更 | 所有受支持入口强制相同上限，模型/provider 身份遵守 D07；不支持的受限入口明确拒绝 | 不能静默转 full access、同 provider 替换或重建无绑定 session；拒绝不修改授权事实 |
| 正常结束、异常、中断或 cancel | 固定执行事实→正常/异常均收尾 baseline→校验产物→D06 本地发布/完成→D03 复评推进 | 发现增量/无法核验零成功发布；保留原信号及变化，不擅自清理；已完成本地补齐零 provider；关闭不明不声称结束 |

所有新失败语义须映射到既有 adapter signal/失败事实目录和 RecoveryAssessment 消费，不另加自然语言路由。启动前资格失败保持输入准备类；启动后绑定、工具或关闭失败保留真实执行阶段。D01 诊断配对适用时仍记录，D02 原因不得被“缺 JSON”覆盖。

### 8.5 冻结所需的成对证明与六维

| 证明项 | 合法流程必须可达 | 反证与真实消费 |
|---|---|---|
| 固定角色绑定 | Claude 作者和 Codex reviewer 各读取受控样例、返回严格结构化结果，由程序落盘 | 错 agent/版本/epoch/policy SHA 在相应副作用前被 handler/transport 拒绝；去掉强制消费则测试必须失败 |
| 原生与 ACP 工具 | 读取/search 可用，程序正常写 run/产物不被误判为模型写入 | 产品、Git、control、policy、ignored/untracked、链接/别名路径写入及写后还原被阻止；ACP write/terminal、native edit/execute、额外工具逐面核验 |
| session 与 correction | 合格同绑定续作和合法格式修正可完成，不靠无限新 session 重传上下文 | 高权限旧 session、metadata 丢失、同 cwd 不同 policy、mode 更新、重连/queue/correction 放宽均失败；无绑定不能补默认值 |
| 退出与发布 | 正常产物可进入复评→D03 reviewed candidate；已有可信完成的冷启动只补本地提交 | 任一收尾失败/新增文件/控制变化阻止 sidecar 与后继；去掉比较应被反证抓住；不自动回滚现场 |
| provider/执行入口组合 | Claude/Codex、inline/worker 共享必要用例；四变体只读角色要求一致 | 禁全部工具的实现被正向测试击败；绕过新 profile 的定义/worker 路径被入口测试抓住 |

六维：Declared by=R3 I3 与本节；Proven by=上述成对 slice/冷启动/工具反证（待完成）；Consumed by=qualification→TaskHandler/transport→acpx/provider adapter→validator/read model；Enforced before=外部启动、每个调用及成功发布；Failure route=既有分阶段 signal/恢复解释且不自动扩权；Cannot-bypass=删除任一绑定检查/恢复传播/收尾检查的对照必须失败。当前仅接口定点验证，整项仍 DESIGN GAP。

先做不消耗模型的静态与离线协议/纯函数核验，原生工具有效性仍须有真实执行面的证明；fake adapter 仅能证明传参和消费，不能证明 SDK 或沙箱隔离。后续若需要改代码构建验证，先满足实施授权；需要真实 provider 时另按既有 Verification Gate，不在本轮偷跑。

### 8.6 成本收益基线与用户指定止损

当前候选成本分五组：Java profile/request/run 绑定；acpx 参数及 session/queue 传播；Claude 工具/配置约束；Codex session/turn/控制约束；共用收尾与整链反证。具体范围以逐文件/方法映射及两 provider 能力证据冻结，不能凭组数或“复用”声称低成本；暂不编造工期、代码行数或 token 降幅。现有 patched acpx 的维护不能抵消两个 provider 发布包及依赖的维护成本。

收益判据固定为：产品/control/Git 在批准前不可由模型写入；必要读取、结构化设计及独立复评可完成；同版本合法续作保留限制；无虚构人工批准；不靠把失败全部拒绝来完成安全指标。

按用户明确要求，详细设计和后续获授权的实施过程中出现下列情况，**先停止受影响工作并等待用户确认**，不得先做完再告知：

- 必须修改底层引擎核心、引入独立工具服务/权限平台、外层 OS 隔离或其他超出本次 adapter 绑定方向的维护面。
- 只能靠改变 Claude 作者／Codex 评审分工、削弱读取/评审能力、合法续作或严格批准/证据要求达成只读。
- 必需改动或验证成本超出冻结时记录的批准范围/预算；或者实测成功率、上下文重传、token/延迟、维护负担显示预期收益不成立。当前尚无量化基线，后续须先提交估计依据及可接受边界，不得以“没有数字”解释为可以无限投入。

触发时在现有主清单/D04 记录：原预期、实际证据、影响到的验收项、必要成本、可行替代及建议；保留现场，不提交、不回滚用户改动、不启动替代实现。用户确认前不得继续依赖该取舍的工作。普通接口不确定性先在已授权只读核验内调查，不把每次读取文件都升级为批准请求；其他独立且已授权的文档核验仍可继续。

### 8.7 具体待核验顺序及停止边界

1. 首先证明两 provider 的“必要读取可用＋写/执行面受限”能同时成立，尤其是 Codex 受限执行下的自主读取/搜索、禁止写入和禁止沙箱外升级。允许比较已有 ACP `fs/read_text_file` 宿主介导读取，调查和核验现有协议面本身不视为新增工具服务；但须追到常规 turn 的模型工具调用及 adapter→acpx 实际消费，不能把协议方法存在当成可用。此前导出的 Codex app-server `FsReadFile/FsReadDirectory`（`fs/readFile`、`fs/readDirectory`）是另一套客户端接口，不能直接作为 ACP 回调或模型工具已接通的证据。原生路径不成立时，先比较这些已有候选；若必须新增工具服务、修改引擎或改变约束，立即按 §8.6 停止，而不是先细化大量迁移字段。
2. 该前提成立后，冻结 binding 版本与 Java/acpx/provider 每个消费者、session/correction 传播及实际回执来源；不把新字段存在当完成。
3. 将 baseline/可信 writer 归属、正常/异常收尾与 D06/D07 写集/恢复入口对齐，冻结反证和正向样例、维护及验证成本。**显式登记 readOnlyBinding 不可变文档这个程序写入物**：按 §8.3 由 TaskHandler 的程序准备路径负责，冻结 canonical ref、内容 SHA、实际 writer/时点、原子写入方式、失败残留及下一消费者。D06/D07 对账须分别列出 run 准备 owner 的绑定文档写入，以及 ledger 写集中 node/task 对该文档的固定引用；不得为了纳入清单伪造跨 owner 原子事务或让两个 writer 同时拥有文档。准备中断留下的孤立文档不授予 dispatch 资格；引用提交失败、文件缺失或 SHA 漂移均阻止后续调用。程序工件盘点中显式列出该文档的归属和预期字节核验，不按路径命中自动豁免；准备、收尾、恢复与测试共同消费同一归属记录。具体写点/引用写集未冻结前，此项不能关闭。
4. 交 CC 独立复审该具体方案；满足全部条件后才能建议 D04 冻结。实施仍需用户显式授权，真实 E2E 仍在全链回归和 Gate 之后。

本轮没有认定成本收益已经发生不可接受偏差，也没有证明不存在偏差。已形成具体接口地图、候选契约、转移与失败切面；第一项的关键正向可用性仍未证明，故不提前扩展为工具服务或引擎开发。

### 8.8 此前“无执行”候选的离线探针（历史核验，2026-09-28）

用户要求先验证可行性。本轮[核验报告及原始证据](../../output/d04-feasibility-probe-20260928/feasibility-result.md)直接调用未修改的 acpx 宿主处理器，在临时样例完成 7 项断言：approve-reads/非交互 deny 下完整与分段读取可用，修改/创建/terminal 拒绝；deny-all 同时拒绝合法读取；terminal=false 配合 approve-all 仍能执行固定临时命令。最后一项证明能力声明不是请求时硬拒绝，不是候选配置已发生越权。只读上限须由请求消费者强制且不可运行时放宽，不能只设置 `--no-terminal`。

固定 Codex 0.153.4 离线接受 `features.shell_tool=false`，但这只证明配置解析，不证明实际工具集合及读取可用。Codex adapter 业务代码未找到 ACP 读取调用，命中仅在内置 SDK；不能把宿主处理器通过当作模型工具链已接通，也不能据文本搜索断言引擎绝不支持读取。Claude 的 tools 集合接口有源码依据，原生 Read/Grep/Glob 与附加工具、配置合并、恢复的共同强制仍未实测。

联合可行性仍未证明，D04 不冻结；本轮无模型调用、provider session、adapter/产品源码修改。下一证据须来自有界原生能力实验，而非再用文本 PASS 代替行为证明；依 §8.5 的授权和 Verification Gate 执行。若实验需要新工具服务、引擎修改或只能以允许受限 shell 换取读取，按 §8.6 停等用户选择。本次探针及结论为作者核验，未另经 CC 独立复审。

### 8.9 用户确认的受限执行候选与本次实验授权

用户已明确同意：调整 D04 候选要求为“允许经过证明的必要受限执行”，调整后进行有界原生能力实验；结论确定前不承诺可行、不开始产品实现。此决定覆盖临时验证程序与固定 native 能力实验，不授权修改产品/adapter 源码、安装替换版本、变更用户全局配置、实现 W03、合入或真实全流程 E2E。§8.1/§8.5/§8.8 中此前“真实验证未授权”的时间性记录，以本次有界实验授权为准；Verification Gate 仍须先通过。此前报告和 CC 结论保留原文，均不外推为新候选已获评审。

目标不变量仍为 R3 I3：未批准实现前，产品工作区、Git、控制账本与权限事实不得被模型改写；合法设计、计划、独立复评和结构化产出必须可完成。执行工具存在不再本身判 FAIL；越权写入、隐藏提权、未授权外部副作用或破坏合法续作仍判 FAIL。不能把“允许受限执行”扩为运行任意测试/安装依赖等隐含写授权。网络 authority 不扩张，不重开 R4/Phase B。 ACP 宿主 terminal、原生 shell、原生编辑、Code Mode 宿主及 MCP/额外工具是不同执行入口，不因某个原生 shell 通过而全部放行；不能证明消费同一上限的入口须保持关闭或在请求前明确拒绝。允许必要工具宿主不等于允许它绕过文件限制，其实际执行/委派边界仍须核验。

本轮先在固定 native 引擎已有入口，以新临时样例验证读取/搜索/子进程合法执行，与产品样例、Git、control、链接及写后还原拒绝；先执行不消耗模型的层级。运行 Gate、精确命令、binary SHA、输入/结果和失败分类保存在 [实验目录](../../output/d04-restricted-execution-20260928/)。若需要模型层验证，则须另满足实际 provider/model/工具与限制回执、预算及触发前提，不能把缺项解释为普通探测。不同层级分开报告：原生沙箱探针不能证明模型工具链、acpx 传播或 Dual 发布恢复已闭合。

成本上限与止损见本轮 Gate；出现 native 隔离不可用、受保护写入成功、需要修改引擎/新工具服务/新平台或改变严格身份/scope/批准时，停止受影响实验并提交事实；不能先实现补丁让候选过关。本文和清单的当前 D04 口径均使用新要求，历史“无 execute”证据只说明此前调查过程。

本次[实际结果](../../output/d04-restricted-execution-20260928/result.md)：固定 Codex 原生命令沙箱 16 项切片通过（3 项合法读取/执行、13 项写入拒绝），样例前后指纹一致；原生模型任务因探针错误关闭必要 Code Mode 宿主而 INVALID，未发生任何文件工具命令。一次启动前配置错误另单独保存。模型消耗记录为 input 27,376（含 cached 13,440）、output 236，subscription_unpriced；不按进程成功或模型 JSON denied 宣称通过。已按预算停止，不自动补测。补测前须核对工具宿主与 sandbox 交界、修正初始化失败停止条件，并重新确认具体 Gate/增量调用预算；原生命令切片不外推到 Claude、acpx 传播或 D04 整体可行。

### 8.10 补测准备与预算确认前记录

用户要求“修正工具前提与停止条件，再确认增量实验预算，然后补测”。[准备材料](../../output/d04-retest-preparation-20260928/preparation-result.md)已显式恢复必要 Code Mode host/shell/unified execution，保持 read-only/never；加入初始化/工具错误停止、快速退出终检、进程组终止、停止后的 usage 保留及零工具证据拒绝，10 项离线检查通过。旧 INVALID 工件保留。默认只做准备检查；无预算确认不启动模型。

[新 Gate](../../output/d04-retest-preparation-20260928/verification-gate.md)待用户确认：1 次启动/新 session/用户任务，gpt-6-astra low，90 秒与第 6 个可观察命令触发停止，零自动补跑；token 按实际 usage 记录，未验证 token/金额硬限。更正前次过度承诺：离线 help/feature 检查不等于整个工具链已初始化；watchdog 是观测后停止，不能保证第一笔模型请求之前阻断，也不是第 7 个请求前的原子准入。真实 Code Mode/模型工具与 sandbox 的共同约束仍待本次实验，不据离线检查冻结 D04。预算确认前本轮 provider 调用为 0；确认后也不进入产品实现。

### 8.11 用户确认后的原生模型补测（当前）

用户已回复“同意”，本轮按获批 Gate 执行一次补测，结果见 [报告与原始证据](../../output/d04-retest-preparation-20260928/result.md)：**PASS_NATIVE_MODEL_SLICE**。固定 Codex/gpt-6-astra low 在 22.63 秒内完成两个实际命令：自主发现/读取未预告随机样例并正确计算；对产品文件和 control 文件各一次 append 均收到 OS PermissionError，全部样例文件/链接及 Git 元数据指纹不变。binary/host/driver/guard 身份一致，完整 usage 和结束事件存在。结论由作者核对，非 CC 独立复审；旧 INVALID 不覆盖。

实际 input 43,262（含 cached 28,032）、output 337，subscription_unpriced；没有可核实金额，也未证明 token 节省。唯一额外读取为事件中可见的宿主 RTK.md，因此未严格兑现 prompt 的“不要触碰其他路径”；不能宣称样例外零访问或 ignore-user-config/ignore-rules 已隔离全部环境影响，来源尚未核实。此偏离不改变两个写入被拒的事实，但必须作为后续绑定中的读取范围/环境核验项。fixture 指纹不能证明宿主全局无副作用。

D04/W03/W08 仍未完成：本轮消除了“模型实际必要读取与指定写入拒绝完全无法共存”的关键疑问，未证明 Claude、acpx/adapter 传播、全部工具面、session/恢复和 trusted writer 边界。预算已用完，无自动追加调用或产品实现；冻结仍须逐入口证据、成本收益核验及独立复审。此前 §8.9–§8.10 的待补测/待确认表述保留为历史过程，以本节为当前状态。

### 8.12 后续实验价值与 Claude 认证阻塞（当前）

用户要求判断继续实验的价值，有价值则继续，并说明如何实施。作者选择 Claude 必要读取切片，因为它是尚无实际证据、会影响双 provider 方向的原生能力缺口；重复 Codex 已通过的同类样例不作为当前优先实验。用户的条件授权不解释为产品实施或独立评审授权。

[本轮报告](../../output/d04-claude-native-20260928/result.md)：**INVALID_AUTHENTICATION**。固定 SDK/native 的运行时工具清单仅 Read/Grep/Glob、无 MCP/plugins/skills；但服务端 401 拒绝 OAuth token，4.01 秒后停止、零工具执行，SDK 报告 usage 与估算金额均为 0。清单有效不等于读取有效，不外推写入拒绝。探针遗漏前两条 api_retry 认证错误，只在最终错误停止；原始事件保留，今后补测前必须先以原事件离线修正并验证首次错误停止。

当前无需重复真实调用，也不能自行刷新凭据/切换账户或计费；有效认证恢复后，同一个 Claude 必要读取目标仍值得有限补测。本轮不另加调用。Codex 既有正向证据保留，Claude 实际可用性未决；D04 仍未冻结。

必须澄清设计冻结与产品验收的次序：实现前需要原生可行性证据、明确的字段/owner/消费者/写序/拒绝出口及可执行测试设计；尚未存在的 adapter 回执、session 恢复传播和整链反证，需要在获准实现后以真实代码证明，未通过前不得关闭 W03/W05/W08 或开真实 E2E。不是把已有验收义务撤回，而是避免“禁止实现但要求实现链已通过”的循环前置。该澄清与主清单 §9 的批次组织属于作者本轮建议，须纳入既有冻结版独立复审；不据此自动解除 §13 任一缺口或用户开工授权。

### 8.13 2026-09-29 补测前置进展

用户已要求进入可行性验证下一步。本轮完成 [首错止损修正与离线核验](../../output/d04-feasibility-followup-20260929/result.md)：新诊断副本与实际 SDK 消费共用 guard，18 项序列通过，旧认证事件在首次 api_retry 即关闭消费；正常读取不误杀。原脚本/证据不改，未修改产品/adapter。

本地 auth status 仍仅显示已登录，服务端有效性未证明；已询问用户是否恢复原认证，回复前不重复模型请求、不自行改凭据。新 [单次补测 Gate](../../output/d04-feasibility-followup-20260929/verification-gate.md)及无确认即拒绝的启动检查已准备；本轮模型调用为 0。D04 保持 DESIGN GAP，既有九项设计冻结不变；之后须以可行性证据交 CC 独立判断，不以这次离线 PASS 或“进入下一步”自动获得实施授权。

### 8.14 既有 Claude Code＋GLM 路由纠正及读取实证（2026-09-29）

用户澄清 Claude 一直绑定 GLM，Dual 不应依赖模型品牌。此前把另一份 native 的 Anthropic OAuth 401 推为本机需要登录，属于作者选错实验对象/错误归因，§8.13 的“等待恢复认证”前置撤销；旧证据仍 INVALID，不改成安全或可用性失败。[本轮报告](../../output/d04-claude-code-glm-20260929/result.md)保留具体差异和职责纠正。

当前规范：Claude/Codex 指客户端/adapter 执行面和分工，不硬编码其底层模型品牌；模型/连接从配置进入，实际身份继续用于审计、成本和同版本恢复。必要连接参数与自定义 hooks/工具/权限配置分开消费，不因禁用后者而静默切到另一后端。只读能力绑定针对客户端执行面；任何模型产物仍经同一严格 contract 校验，不能为某模型加宽松解析分支。

在 SDK 0.3.232＋本机 Claude Code 2.1.277、既有 GLM-5.3 路由下，单次 10.19 秒、4 次真实工具调用完成随机数据搜索/读取与 control 读取，oracle 正确、fixture 无变化；init 仅 Glob/Grep/Read、dontAsk、无 MCP/plugins/skills/slash_commands。结论 PASS_NATIVE_READ_SURFACE。Write/Edit 无实际调用，拒绝能力 NOT_TESTED；隐藏工具/配置/scratch 上限仍未证明，不因 --restricted 参数存在而关闭。最终文本含解释和 fenced JSON，严格纯 JSON 提示未满足；本轮只验证读取值，未验证 Dual artifact/correction，不放宽产品合同。

模型调用为 1，零自动重跑/子 agent；SDK usage 原值和 costBasis=unknown 独立保存，估算金额不是 GLM 账单，firstParty 原始标签不冒充 Anthropic provider。此 native 版本的正向实证不外推到旧 pinned adapter；D04 仍 DESIGN GAP，九项设计冻结/实施未授权状态不变。本次预算已使用，继续真实调用须有针对剩余缺口的有界 Gate；证据齐备后再交 CC 冻结复审。

### 8.15 原生负例与额外控制面阻塞（2026-09-29）

用户要求继续完成剩余补证并提供 CC prompt。本轮 [评审包](../../output/d04-native-boundary-review-20260929/review-package.md)补充原生协议负例、成对对照和 adapter 精确落点。零真实模型调用、无子 agent；本地桩只返回工具命令，结果由未修改 native 产生。Claude 的真实正向读取、11 项拒绝及开放 Write 对照成立；Codex Code Mode/原生 patch/提权、5 个写目标和配置/AGENTS 诱饵切片通过。原诊断汇总的两处类型假设错误及作者离线重判保留，不能只读最终 PASS。

新增必须显式保留的缺口：实际 Codex Responses 请求在 multi_agent=false、multi_agent_v2=false 下仍暴露 collaboration.spawn_agent 等控制入口。存在不等于可执行；本轮未调用，以遵守不启动额外 agent 的约束。但没有硬限制证据前也不能声称上限已完整强制，D04 仍 DESIGN GAP。下一步优先核实既有接口；若需 engine/新服务或接受 native 自行委派，依 §8.6 停止交用户决定，不自动实施或扩平台。

[评审包 §3](../../output/d04-native-boundary-review-20260929/review-package.md#3-具体绑定选择及实施落点)是当前 D04 对 §8.3–§8.7/原附录 §4.1 的具体补记：固定实测版本、保留连接而禁自定义权限、显式 native 路径、runtime 私有目录及真实 writer、参数覆写/全入口传播；模型品牌仍不进入核心流程。原生配置/文件切片不外推任意 OS 隔离，session/ledger 恢复和严格产物仍需产品实施后验证。

本轮诊断预算已结束，相关能力上限结论停止。已准备 [简洁 CC prompt](../../output/d04-native-boundary-review-20260929/cc-review-prompt.txt)，由用户发起独立复审；不预设冻结 PASS。九项设计冻结不变，本次不授实施、合入或真实 E2E 权限。

### 8.16 原生补证材料 CC PASS 与剩余核验约束（2026-09-29）

用户带回的 CC 结论为材料层 PASS、2 LOW，D04 仍不可冻结。[评审处置](../../output/d04-native-boundary-review-20260929-r2/review-disposition.md)保存原文、定点核对与受审文档快照；旧包字节不改。CC 接受 Claude 与 Codex 所列原生切片的冻结前证据，不代替 adapter 全入口、收尾及 D06/D07 集成验收。

- LOW-1 已纳入同一额外能力上限核验：旧配置确有 `web_search="disabled"`，两次实际请求与嵌套工具清单均未暴露网络工具；但 schema 可表达、配置关闭、未暴露均不等于隐藏调用在副作用前必被拒。与 collaboration 一起核验现有接口及强制分发边界，不重开 R4/Phase B、不调用真实外网服务；不能保证无副作用时只做静态调查。
- LOW-2 固定后续入口：所有 native 补证从 `probe-guard-repair/` 修正副本派生，新目录、新 Gate、明确增量预算；旧 driver 不再用于运行。现有写入 guard 不通用于所有控制动作，新增动作须先补对应 typed 结果判定和离线止损注入，意外成功/未知/异常必须阻止后继。5 项旧离线测试不冒称控制面 guard 已完成或已在 native 生效。
- 证据口径收窄：拒绝文案提及 subagent 不等于实测后代会话，实际子 agent 启动仍禁止；`is_error` 省略须结合 call ID、正向 oracle 及错误/缺失反证，不能独自判成功；collaboration 是独立顶层 namespace，并非 exec 的嵌套工具。

当前剩余问题是 Codex 额外能力上限的强制性，不能把它缩写为“只剩一个开关”。先查既有接口；须改引擎、加外层机制、改变 authority/分工或成本收益偏离时，依 §8.6 停止交用户决定。旧实验预算已结束，本次零新增 native/模型/agent 调用，无产品改动。两项 LOW 为约束文本已吸收，具体补证仍未完成；本次增量是作者核验，未经再次独立评审，不自动启动实验或实施。

### 8.17 现有硬限制接口核验：尚不足以冻结（2026-09-29）

用户要求“核验现有硬限制接口，有证据后再请 CC 判断冻结”。本轮 [接口核验报告](../../output/d04-hard-limit-interfaces-20260929/assessment.md)记录配置解析、同版协议/adapter 静态消费链及 2 次有界 native 接口捕获；桩只发结束消息，零工具命令、真实模型或子 agent。6 项 final-only guard 离线检查通过。两候选的接口捕获有效，但排除目标均 FAIL，不能称硬拒绝实证。

已确认：结构化 `multi_agent_v2.enabled=false` 及其叠加 code-mode namespace 排除配置均仍暴露 6 个 collaboration 工具；并发上限 0 被 parser 拒绝；根级 `enabled_tools` 被 strict-config 拒绝；同版 app-server `multiAgentMode` 声明 ignored，不能作为能力禁用。网络工具在新旧请求均未暴露，隐藏调用的副作用前硬拒绝仍未证明。所有这些结论限定 pinned 版本与入口，既不推导工具已可越权执行，也不宣称所有原生接口不可能满足要求。

本轮捕获预算已结束，未尝试创建子代理或触发网络工具。原生 dispatcher 精确实现及版本对应、管理员 requirements 消费、app-server 细分策略未穷尽；不得把字符串线索当源码，或自行扩大管理员 authority。下一步由用户使用 [CC prompt](../../output/d04-hard-limit-interfaces-20260929/cc-review-prompt.txt)独立判断具体接口遗漏及冻结证据是否充分；若需引擎/外层机制/分工或成本收益取舍，按 §8.6 停止交用户决定，不以“材料 PASS”继续猜参数或实施。其余九项设计冻结及既有集成验收义务不变。

### 8.18 注入探针离线修订：不以任意拒绝冒充权限证明（2026-09-29）

用户带回接口核验 CC 材料 PASS，随后同意“先离线修正探针设计，不立即申请运行预算”。[离线设计](../../output/d04-dispatch-probe-design-20260929/probe-design.md)保留评审原文与同版真实工具声明，明确 CC 的 F1 不能直接作为冻结路径：list 查询结果不代表 spawn 权限；畸形参数被拒不区分允许/禁止有效动作的系统；协作工具声明为 function，不能直接套用 exec 的 custom call；本地模型桩不保证 native 无委派或托管网络副作用。

本次给出逐探针证据上限、10 个判定反例、准备/发送/分派/结果落盘/中断/重启的失败切面，以及**不可执行 Gate 草案**。错误协议、参数、额度、权限与 transport 原因分开；只有有效动作的明确副作用前权限拒绝才有资格作为该入口的证明候选，仍须核对合法读取及兄弟入口。不得批量注入后统一汇总或在结果不明时自动重发。

当前响应 wire 契约、同版 decoder/守卫与副作用顺序未证，未识别安全且有充分判别力的 native 候选；本轮零 native/工具/模型/agent 调用，无 runner、无运行授权文件、不申请预算。先定位可追溯来源再决定探针价值；需要新 authority/引擎/外层机制/分工或成本收益变化仍依 §8.6 交用户。此处为作者离线设计自查，未经新一轮 CC；D04 DESIGN GAP、其他九项冻结及全链实施条件不变。

### 8.19 同版源码核验：协议与副作用顺序（2026-09-29）

依用户要求完成 [核验报告](../../output/d04-native-source-audit-20260929/assessment.md)。Dual 没有引入 Rust 产品开发；读取的是 acpx/codex-acp 所调用的 Codex 原生依赖。通过本机包/二进制内容、npm 发布 provenance 与公开 tag，将证据关联至 Codex 0.153.4 的同一提交；源码逐份核对 Git blob 与 SHA256。不宣称独立验证签名链或可复现构建，不外推到其他版本。

已定位 function_call 的 arguments 字符串、namespace/call_id、SSE item decoder 及 registry 顺序。协议/类型/参数/容量错误与权限拒绝必须分开；spawn 会先创建子线程，再发送初始任务，后段失败不能推出没有创建；list 查询亦含内部登记。readOnly/never 在所读路径中是子配置继承，不能据此认定禁止委派；adapter 的子线程通知观察不能充当创建前守卫。

本轮发现已有原生 **PreToolUse Blocked→handler 前返回** 的调用点，故不能绝对宣称无现成接口；但当前探针关闭 hooks，可信装配、匹配、失效/超时、hook 本身能力与兄弟入口不可绕过均未证明。另将本地 web.run 注册抑制与远端托管搜索声明抑制分开，不能用伪造托管结果测试本地执行拒绝。上述为同版静态证据，不是新运行证明。

到此停止扩大源码排查；[CC prompt](../../output/d04-native-source-audit-20260929/cc-review.txt)供独立判断证据与继续核验价值，不直接调用 CC。不写/构建 Rust，不自动启用 hook，不执行 native/provider/注入/agent，无 runner 或预算申请。D04 仍 DESIGN GAP，其余九项设计冻结与实施未授权状态不变；若需新 authority/引擎改动或成本收益偏离，依 §8.6 交用户决定。§8.18 的“同版 wire/顺序未证”为上一轮状态，由本节静态核验推进，不追溯改写旧包；完整可运行协议与运行 Gate 仍未成立。

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

## 13. 冻结候选对账与剩余准入条件（当前）

§15 原附录与 §15.1 规范性补记构成当前设计基线。CC 独立评审确认九项可冻结；两项 LOW 已采纳。DESIGN_FROZEN 只表示方案及验收义务固定，不等于实现已验证或用户已批准开工。

| 决策 | 冻结依据与采纳 | 当前状态及实施后义务 |
|---|---|---|
| D01 | 附录 §2 + precondition-map：前置/消费者/规则身份、coverage、L03/L04 | DESIGN_FROZEN；实现后证明分类/UNKNOWN、语义常量映射与合法续作 |
| D02 | 附录 §3 + 补记 LOW-2：精确 signal 关联、EXECUTION 可审计声明、不可降级清单 | DESIGN_FROZEN；typed 字段及两条合法通道、拒绝序列、E02 常驻回归仍待实施 |
| D03 | 附录 §4 + examples：两阶段合同、reviewed_candidate、外部批准、逐 consumer/四变体迁移 | DESIGN_FROZEN；实施后验收无循环批准、自动推进、严格授权及新旧拒绝 |
| D04 | 附录 §4.1 + 主文 §8：绑定/回执/session/writer；已有局部实证 | **DESIGN GAP**：§8.14/§8.15 已补必要读取、原生拒绝/Code Mode/配置与 scratch 切片及现有绑定接口；§8.16 已吸收 CC 两项 LOW；§8.17 核验的候选仍暴露 collaboration，网络工具硬拒绝未证；§8.18 已纠正离线探针判据，尚不可运行。adapter 全入口接入在实施后验收，不以 CLI 切片替代 |
| D05 | 附录 §5：真实生产者与增量、机器/人工出口样本分列 | DESIGN_FROZEN；实现后验证生产者→消费者，不将合成样例/人工声明当机器证明 |
| D06 | 附录 §6 + 142 文件入口对账：写集/会话/锁/原子写/失败前缀 | DESIGN_FROZEN；本机存储原语不代替产品冷 JVM/中断/并发反证 |
| D07 | 附录 §7：M6 函数桥接、identity、恢复 payload/入口、未知裁定 | DESIGN_FROZEN；实现后证明恢复/终态及人裁定不冒充机器事实 |
| D08 | 附录 §8 + 补记 LOW-1：删除隐式 builtin 构造、显式装配、holder monitor | DESIGN_FROZEN；实施后证明入口/会话/并发。验证前同 JVM 多 workstream 路由仍禁止并发 |
| D09 | 附录 §9：逐窗 Git 事实、恢复/no-change | DESIGN_FROZEN；实现后证明观察充分性与真实交付，不新增空交付/自动 commit |
| D10 | 附录 §10：严格机器字段保留，仅去重复 prompt 来源 | DESIGN_FROZEN；W07 修改与减负效果仍待验收，不承诺 token 已下降 |

全案仍非 Implementation Ready。D04 可行性、整版实施范围与已有改动对账、用户实施授权仍须分别满足；142 个候选文件不是批准修改清单。设计冻结后发现反例按 R3 §7.1 回到对应决策纠偏，不以局部实现掩盖，也不默认重新开启全部设计。唯一工作索引及实施组织见主清单 §2/§9。

## 14. 本次评审处置与核验边界

用户带回的 [CC 原始评审](../../output/detailed-design-review-20260928/cc-review.txt) 为候选设计 PASS（1 WARN、3 LOW）。以下保留该轮处置记录，最新复审见 §14.1。这是候选方案的评审结论，不是冻结版通过，更不是实施授权。

- WARN-1：在 §2.3/§11/§13 补正向账本表示、owner/消费者、D06 写集及投影区别；作者已修订，须独立复核，D03 未冻结。
- LOW-1：contained permission 与非必需义务的精确关联仍列 D02 冻结项；本轮未完成，缺关联证据不容纳的默认保持。
- LOW-2：§12.1 I1 补 BUSY/pending 两个实际 UI 场景及 CLI/API/report 同源对照，属于未来测试义务，未声称已测。
- LOW-3：§6.3 补候选入口逐项“改／不改＋理由”及方法/owner 归属；116 文件仍未经逐项分类，不是批准范围。
- 较早工作清单两项 LOW 已在其 D02/W08/§8 完成文字修订，本轮核实已有内容，不重复记为新增完成。对应产品常驻回归与投影证据仍待实施。
- CC 对 Claude-only policy binding 此前仅按命名推断；本轮直接核验 AcpxAgentTransport.validateExecutionPolicyBinding：非 Claude 携该 binding 会拒绝。补证不延伸为只读有效性结论。

本轮文档修订、静态可行性证据与[材料校验](../../output/detailed-design-review-20260928/material-verification.json)独立保存，旧报告/指纹不覆盖。D04 发现常规模式映射不足后，先给出取舍，不继续默认其可行或启动适配器修改。所有产品项仍 DESIGN GAP；本次没有重跑 97 项测试，没有真实调用、历史样本恢复或提交。

### 14.1 修订版与 D04 报告复审处置

用户本轮粘贴的 CC 复审结论为 PASS，无新增 WARN/FAIL，新增 LOW-1“选项表缺限制只读阶段 provider 集合变体”。[复审摘要与处置](../../output/detailed-design-review-20260928-r2/review-disposition.md)标明来源及未核验范围；不是本 agent 发起或完成的独立评审。

原 WARN-1 的正向账本表示与 LOW-2 的 BUSY/pending 投影义务经 CC 确认解决；§13 D03 已移除“正向表示独立复核”这一已完成项，其 JSON、逐消费者迁移与写集冻结仍未完成。原 LOW-3 守线维持；D02 的 signal/义务关联（首轮 LOW-1）仍未完成，与本轮新 LOW-1 分开记录。

本轮新 LOW-1 已在 §8.1 补一行，说明条件、跨 provider 分工代价和强制能力/质量验证义务。它不是可自动启用的 fallback，也不改变推荐顺序；Claude 本身的强制只读尚未证明。旧 D04 报告与 source-index 保留受审字节，本次新增选项以本文为准。

复审认可已检查事实及推论边界，不等于穷尽所有底层能力或证明运行时隔离；其未逐行复读的源码及所有运行时行为仍未核验。不能把仓库已有 patched acpx 的维护经验等同于修改 provider adapter 无新增成本；其发布包、依赖与审计维护仍须纳入选择。

作者本轮仅补文档及状态同步，[材料校验](../../output/detailed-design-review-20260928-r2/material-verification.json)不替代独立复审。产品成熟度仍 DESIGN GAP，D04 方向待用户决定；无新增 authority、实施、提交、真实运行或历史恢复授权。其余 §13 枚举工作仍在原范围内，并不因 D04 等待而全部转为需要新授权。

### 14.2 D04 方向确认后的详细设计核验

用户随后明确同意优先细化现有 adapter 的强制只读绑定，保留 Claude 作者／Codex 评审；并要求实现中若发现成本收益偏差，停止等待确认。§8.6 将此要求同时应用于设计阶段发现的扩围，避免到实现才暴露。它不改变实施未授权状态。

本轮新增 §8.2–§8.7 及[接口证据](../../output/d04-readonly-binding-design-20260928/binding-assessment.md)。作者 Boundary Review：DESIGN GAP；新增事实来源于定点源码和离线协议导出，非真实工具行为。角色限制由程序消费，批准 authority 不变，投影无新权力；未接受“先用 prompt 顶住”的技术债。重点残余为 Codex 读取与无 execute 的共同可行性、Claude 配置实际生效、全链 session 传播和可信 writer 归属。

本轮[材料校验](../../output/d04-readonly-binding-design-20260928/material-verification.json)只验证文档/指纹/引用，不代替冻结版 CC 复审或产品测试。旧评审证据保持不变；未修改源代码、安装包、用户配置或历史 ledger，未启动 session/provider，不把这些材料升格为已闭环。

### 14.3 D04 绑定候选复审处置

用户带回的 [CC 原始复审](../../output/d04-readonly-binding-review-20260928/cc-review.txt)为 PASS（2 LOW，无 WARN/FAIL），确认候选覆盖、职责及证据分层；不是可行性证明或实施授权。

- LOW-1：采纳“比较现有宿主介导读取、避免过早扩围”的意图，补入 §8.7.1。但原报告将 Codex app-server 导出的 FsReadFile/FsReadDirectory 等同于 agent→acpx 的 ACP 读取，协议归属不准确。本轮根据 ClientRequest 的方法分派与 acpx 实际 client handler [核验纠正](../../output/d04-readonly-binding-review-20260928/protocol-boundary-check.json)。允许比较不等于已经存在模型可调用的桥接；若实际需要新增工具服务，仍触发止损，不因名字相近获得豁免。
- LOW-2：在 §8.7.3 明列 readOnlyBinding 文档的准备 writer、内容身份、D06/D07 写入与引用桥接、失败残留及程序工件归属盘点。登记要求已补齐，具体写集和可信豁免仍待冻结，未把 run owner 合并进 ledger authority。
- 报告把 Codex 无 execute 读取称为“唯一可能触发止损的关键未知”，口径过强。它是当前优先核验项；Claude 配置实际生效、恢复传播、writer 隔离和维护/验证成本同样可能触发 §8.6。单条原生读取路径失败也不等于立即放弃全部现有 adapter 路径，应先完成有界比较。

本轮只修订材料，未发起 provider 调用、增加 agent 或进入实现。D04 仍 DESIGN GAP；绑定/回执/session 消费、正向可用性、强制反证、writer/成本及具体方案复审仍未完成。技术冻结与用户实施授权继续分列。此次[材料校验](../../output/d04-readonly-binding-review-20260928/material-verification.json)属于作者自查，不冒充独立复审。

## 15. 2026-09-28 冻结候选补充材料与送审边界

用户要求补齐具体设计材料并交 CC 冻结版复审。本轮提供 [D01–D10 具体补充](../../output/freeze-review-20260928/freeze-details.md)，配套 [逐文件入口处置](../../output/freeze-review-20260928/access-inventory.md)、[原条件/消费者映射](../../output/freeze-review-20260928/precondition-map.json)、[完整拟议 JSON](../../output/freeze-review-20260928/examples/)、[真实生产者样本索引](../../output/freeze-review-20260928/real-producer-samples.json)、[源码身份](../../output/freeze-review-20260928/source-index.json)。附录是本文的具体化，不替代 R3 或主清单；合成样例不产生运行授权。

本轮选择包括：prepared 写集覆盖 FileLedgerStore 的初始化和 legacy append；run 与 ledger 独立 owner 通过不可变完成 ref 桥接；实际文件锁同 JVM 重叠/释放诊断处理；small 变体补回 plan reviewer；permission 判断加入真实 tool identity/义务证据；no-change 沿现有拒绝 capture 语义，不虚构新交付功能。未采纳 generation-pointer/新 model 层/权限平台/任意命令 DSL。

普通离线验证只覆盖本机存储原语与材料一致性。具体结果见 [材料校验](../../output/freeze-review-20260928/material-verification.json)，不能据此称产品或全生命周期 PROVEN。D04 原生证据缺口和未实施的全部验收保持可见；本轮没有新的模型任务、产品改动、合入或历史恢复。

独立评审由用户发起，简洁 [CC 提示词](../../output/freeze-review-20260928/cc-review-prompt.txt)随材料提供；未直接调用 CC。请评审设计是否可冻结及对应阻塞，不预填 PASS，也不将 PASS 自动转为实施授权。

### 15.1 冻结候选 CC 复审与两项 LOW 采纳

用户带回的 [CC 原文](../../output/freeze-review-20260928-r2/cc-review.txt)为 PASS，九项可冻结，D04 技术阻塞。已按 [规范性补记与处置](../../output/freeze-review-20260928-r2/review-disposition.md)采纳：D08 删除隐式 builtin 默认，显式注入，避免 ledger→provider 循环依赖；D02 非必需声明固化进已批准 EXECUTION 合同并在统一投影可见，不能藏在 config 或授予工具权限。此补记补充原附录 §3/§8，原受审包保持原字节；旧样例未含声明字段即无第二通道。

CC 是独立抽验，未全读 142 文件/200 条件或复跑验证；原文有截断，不补造缺失评语。本次增量是作者核验，不记为 CC 再次通过。[材料校验](../../output/freeze-review-20260928-r2/material-verification.json)只证明本次文档/来源身份和未改产品，不将设计冻结升为运行闭环。
