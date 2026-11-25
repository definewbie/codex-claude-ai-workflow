# Dual 生命周期可靠性：详细设计候选

日期：2026-09-28；2026-09-30 更新。阶段 P0。D01–D10 均为 DESIGN_FROZEN。D04 以 §8.25 的原生权限复用目标与 §8.27 提交协议为准：CC 已给设计层 PASS，用户已明确接受提交后在途语义，D06/D07 增量已并入 §6.5/§7.2 并一并冻结。旧 FAIL/未知事实保留，不恢复沙箱不可绕过、硬链接/内核实验前置。设计冻结不等于实现或能力已证；W01–W09 未完成；B01 已实施/离线验证并获 CC PASS；用户随后授权进入 B02，推进 W05/D08 holder 生命周期互斥，构造/读会话联合迁移仍待实施。当前证据与批末评审状态见主清单 §10–§11。无提交或新的真实运行授权。

上位契约：[R3](20260923-dual-lifecycle-reliability-design.zh-CN.md)。唯一工作索引：[主清单](20260927-dual-lifecycle-worklist.zh-CN.md)。本文展开 D01–D10，不另建工作清单或降低 R3 验收。全文“增加、迁移、必须”描述拟议目标；现状另行标明。未落实的能力不能用于当前运行准入。

2026-10-08 当前补记：D01 原冻结语义保持，首版覆盖按用户选择 C 收为单合同资格校验族完整垂直切片、其余 UNKNOWN；具体输入/接线及覆盖调整已获用户带回的独立设计认可，LOW-1 枚举守卫已纳入，见 §4.3 与主清单 §21.1。不能将首族完成外推为完整 D01/W01；设计认可不代表本族实现或证明完成。

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

### 3.2 B03 接线补记：事前可行性生产入口（2026-10-06）

CC 对 B03 中间状态的独立评审确认：D05 已冻结语义与检查时点，但 operator 可用性声明的具体入口确有设计缺口，不能将其当作普通字段迁移。用户要求进入下一步后，已形成 [D05 接线决定](../../output/lifecycle-batch-03-20260930/d05-readiness-wiring.md)。本补记为设计决定，未实施、未另经独立复审；与 B03 批末合并审查，不新增材料轮次。

选择在现有 review 人工操作面登记事前 readiness 材料，统一 runtime 校验、ledger L04 持久化；不复用依赖既有 EXECUTION 的事后 prepare/submit。程序事实优先复用，但现有 toolUsage 缺逐调用结果/exit，不能直接作为可用性证明。记录绑定 GOAL/条款/subject/环境依赖与可信来源，不依赖未来 EXECUTION；材料保存不产生批准、READY 或执行。具体 schema、入口、正常/缺项/漂移/修订路径、D06/L06 写序及六维验证均由该决定承载。

LOW-1 已纳入：环境身份/期限/撤回与同环境复用；编译、批准、R01/R02 前的显式消费者；缺项可达入口及补齐后的合法续作。LOW-2 已纳入：具名程序生产者的正向候选、可证明范围和当前不足。人工负责的设备/外部服务状态不冒充机器可观测；同授权技术修订仍依 D03 重新复评，不能靠最新材料指针替换资格。若需新环境平台、runner、权限或验收变化，停止提交取舍。

### 3.3 D05 机器观察与退出码：用户确认（2026-10-06）

用户已采纳：不把真实退出码设为所有验收的统一门槛；按每项已批准验收要求检查证据，明确要求实际退出码的仍必须取得。具体事实、正反对照与接线时点见 [D05 决定](../../output/lifecycle-batch-03-20260930/continuation-20261006/d05-evidence-decision.md)。本条覆盖原“一律要求实际 exit”的表述，不追溯放宽已批准任务，也不改变机器执行材料与人工声明的信任区别。

实施状态（2026-10-06）：逐项字段、当前 prompt 的程序工具观察、固定执行证据、自测/验收及准备/调用/发布恢复消费者已有增量，见 [本轮 D05 检查点](../../output/lifecycle-batch-03-20260930/evidence-20261006/checkpoint.md)。实现与验证边界以该材料为准，尤其不能把原生 reported exit、模型断言或人工报告读取等同实测退出码/业务验收通过；B03 和 D05 总体仍未收口。

退出码要求须在执行前随 criterion 的验证方案明确并固定；不将缺少约定默认成无需退出码，不按结果好坏事后改要求。原生结构化状态与真实进程退出码分别保留，前者不合成后者；工具报告成功不能单独证明测试断言满足。相同身份的合法完整材料可复用，错调用、源码漂移、缺必需材料仍拒绝。该语义决定已获用户确认，字段与生产/消费接线、正反序列尚未完成，与 B03 批末受影响范围评审合并，不另开语义确认轮次。

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

### 4.3 首版 C：单合同资格校验族完整切片（2026-10-08）

用户已选择“单族完整垂直切片，其余 UNKNOWN”；不是浅配对后宣布四分类已完成。首族为 `TaskContractProjectionVerifier` 的候选/GOAL/EXECUTION/dispatch/routed acceptance 合同资格检查，选型依据是现有共享检查，不是事故频率或“成本为五分之一”。具体 [接线与输入设计](../../output/lifecycle-batch-05-20261008/scope-c/design.md)、[CC 成本评估处置](../../output/lifecycle-batch-05-20261008/scope-c/review-disposition.md)与主清单 §21 是本次范围依据；用户带回的受影响设计复审已认可本族进入实施的条件，LOW-1 枚举漂移守卫已纳入。守卫及接收处置见主清单 §21.1，不等于本族实际规则接线已证明。

- 原 checker 产出逐规则的实际输入/结果；人工入口在 L04 固定 `decision_checks`，Facts 以封闭构造携带 `continuation_failure` 经 R03/L03；RecoveryAssessment 唯一消费。诊断不授予批准、retry 或恢复 authority。
- 候选/已批准、GOAL/EXECUTION、未来 target/current bindings 分开；整体 events 文件哈希不用于表示有界历史。只在同一适用规则、版本和可比的实际输入下判矛盾；可核差异才判漂移，可信 NOT_CHECKED 才判消费阶段缺项，其余 UNKNOWN。
- 本族委托其他族的拒绝、未调用本族的直接入口、I/O/谱系不可核验不得冒称本族已检。新 covered 入口缺/错快照不能保存假 PASS；其他入口明确未覆盖，不回填旧决定。
- 首版验收包括本族实际生产/强制消费、三类可触发及 UNKNOWN 对照、合法補足/返修→复评→新批准、写入前缀/冷 JVM、两入口/四变体/统一投影与删除式反证。合成诊断机制证据与生产消费者/真实用户流程证明分开。
- 全五族的完整覆盖仍未完成，不以 C 关闭 I1.a/D01/W01。新增全族追踪/规则平台、依赖图/服务或分类只能靠伪造事实时停止提交取舍；不自动平移到第二族，不改变真实 NO-GO。

2026-10-09 实施检查点：上述单族链已落码并完成离线自查与最终单轮回归，见主清单 §21.2 和 [批末材料](../../output/lifecycle-batch-05-20261008/scope-c/implementation/batch.md)。native 准备拒绝在原 R writer 固定 input-rejection 完成后走 L03；本地任务携同一诊断随原 L03，不伪造 native 执行记录。R01 后重核复用原 scoped 会话；固定拒绝与创建 R02 在同一原 R 锁内互斥。C 的原义务与覆盖边界不降低；用户带回的 CC 批末独立 PASS 已接收，单族收口见主清单 §21.3；时钟判别 LOW 维持非驻留证明登记，不关闭全 D01 或解除真实 NO-GO。

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

### 6.5 权限回复提交的受影响增量（2026-09-30，DESIGN_FROZEN）

本条将受审协议 §5 并入 D06；原冻结条款保持，用户已明确确认在途语义，新增部分与 D04 一并冻结；不取得实施授权。

- L02 所属 execution writer 通过既有 L 写会话核验资格，提交 `permission_reply.commits[request_key]` 及确定事件 bytes，完整 D06 提交后为 C。R 请求/决定材料只以不可变 ref/SHA 桥接，不把活跃 run 文件纳入 manifest。
- 使旧 execution 资格失效的 abort/cancel、scope/批准/epoch、stale/retry、节点完成或替换 claim，在相同 L 边界将 `permission_reply.closed` 与可同次提交的资格变更纳入同一 mutation，完整提交为 X。已提交回复保留，closed 对同 execution 不重新打开。
- L03/L06 及完成/失败 writer 保留 C/X 历史，禁止 agent result 覆盖/注入；同 key 同内容仅返回历史，不重新授予发送资格；异内容 INVALID。
- manifest 前、任一目标写入间、event、pending 删除的失败均按 §6.4 解释。finish-forward 只补本地，绝不返回新的 `newly_committed` 或触发发送。原 live 调用须实际获得本次完整提交返回才可进行一次交付。
- 原协议 §6 的 C/X barrier、每个失败前缀、冷启动、所有资格失效入口、历史保留及合法续作反证随 W03/W05/W08 实施；不新增锁类型，不嵌套 L/R，不持锁跨原生调用。

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

§8.27 新增的权限回复提交候选是此处的明确例外：run 仍拥有原请求、人的决定与发送观测，但与业务资格排序的回复提交归 L 的既有 execution 文档；R 仅引用该提交，不再生产另一份 authoritative reply intent。此调整已获本轮 CC 设计认可，已并入 §6.5 与本节；用户已确认在途语义，增量随 D04 冻结；其他外部阶段的 dispatch intent owner 不变。

权限回复外部窗口沿 D07 消费：仅原 live 调用的一次交付，不从 C 的磁盘记录恢复发送资格；IPC/ACP 送达不明保留 UNKNOWN，不补发、不重跑 prompt。X 后禁止新 C，但已有 C 可能仍在途，须释放 L 再请求取消/核验，停止未确认不显示取消完成或启动替代 attempt。合法 deny/取消清理不被 closed 禁止，但仍校验原生请求身份和状态。恢复完成只补本地，旧 epoch 的结果不为新 scope 产生业务成功。单次连接、单次写入及上层禁止自动重试的具体入口见[CC 处置 §2](../../output/d04-permission-wiring-audit-20260930/reply-commit-cc-disposition.md)。

dispatch intent 不能只包最后一次 prompt：session bootstrap/new/ensure、prompt、格式 correction、adapter 内部重试和 stop 各有外部边界，必须绑定同一 attempt 下的具体阶段和已确认结果。当前 `--prompt-retries` 不得成为绕开 UNKNOWN 拒绝的后台重做；只保留已证明未产生需裁定副作用且符合原重试预算的情况，否则停止交既有恢复入口。session 已建立不证明 prompt 已完成；模型最终输出已收到也不证明所有本地发布完成。

人工裁定沿既有 recovery 入口扩展，需绑定 occurrence 与所观察 evidence；不得仅把 retrySafe 改为 true。现有 `retryActiveNode(paths,note)`、`recoverStaleRunningNode(paths,note)`、`abortRecovery(paths,note)` 只有 note，不足以承载机器可验证的裁定，不能声称现成入口已经满足。

推荐给现有 retry/recovery command 增加显式结构化裁定参数，进入同一 ledger validator：旧 attempt ref/SHA、当前 node/input epoch、operator 决定、停止核验引用、外部结果观察引用、note。普通 retry 不带此参数时永不消费 UNKNOWN；绑定旧 attempt 或过期 source 时拒绝。`CONTINUE_LOCAL` 只允许充分完成证据下的本地补齐；`ABANDON_AND_RETRY` 仅在原执行者确认结束、外部修改已核实和新输入资格满足时创建新 attempt；`WAIT/ABORT` 不授权再执行。操作名称为拟议参数值，不是当前 CLI 已支持的命令，也不增加新的执行者身份。

此前结果仍无法判断时保持等待或保留现场后终止；不得用自由 note 伪造“已确认”。具体停止/观测引用能否由现有 transport/reconciler 提供，仍需逐入口冻结，缺生产者就不能先解锁重试。

abort 在同一本地提交中终结可终结的 node/task/current 并保留证据；若仍有运行进程，先请求停止并核验结束，停止不明时不得声称取消成功。终态禁止新 dispatch；观察/导出不被禁止。

### 7.3 LiteFlow

推荐在 LiteFlowPrototypeKernel 内以进程共享互斥保护 load→execute→clean/临时规则清理和 close，覆盖所有实例；单实例 synchronized 不够。异常/finally 也在同一互斥内，close 不得清掉另一实例在用的 holder。锁顺序固定 workstream session→kernel，kernel 不回调获取 workstream 锁。

在完整并发反证通过前沿用 R3 临时约束：同 JVM 只允许一个可推进 workstream，路由与 close 串行；不能仅在 Gate 写一句。若实际装配包含另一个直接访问 holder 的使用者/独立 classloader，局部 static lock 不足，需拒绝该部署拓扑或另行比较方案。当前未实测拓扑，不宣布约束已 ENFORCED。保留默认 LiteFlow，不以切 builtin 掩盖缺陷；同一信号的两 kernel 路由等价及异常清理必须有对照。

### 7.4 2026-10-07 用户决定：原生兼容性与 Dual 冻结身份分开

用户同意继续沿环境变量/既有 acpx 入口使用 Claude Code（现有 GLM 后端）与 Codex，不要求永久固定原生版本；兼容升级不因版本标签或安装内容变化拒绝。该决定覆盖此前“必须在任何调用前证明完整原生安装字节”的未实施要求与主清单 §15 的固定安装建议。Dual binary/definition/schema、业务批准/epoch/材料 SHA 仍严格，受审 acpx policy 接口声明与实际 build-info 核验不因此取消。不宣称任何具体版本区间已获运行证明。

接线复用 R01/每命令 intent/result/R03：transport 程序生成 invocation（acpx 入口、agent、canonical cwd、session、PATH 及该角色的原生路径环境变量），R01 固定 invocation 和 primary/correction 的请求配置。每个命令 intent 由既有 R owner 核对后落盘，再允许命令启动；入口选择从冻结 Dual runtimeBehavior 移到该执行准备，不放入全局版本白名单。新准备可用新入口，旧 UNKNOWN 不借新入口自动重发。已释放/已结束/别的 executor 不获新的普通发送资格；精确 cancel/close 仍可记录，但不授 prompt 权限。

首次 prompt、重建后 prompt、correction 前，transport 读取既有 `--format json <agent> sessions show <name>`。只固定白名单摘要，核对当前 schema、会话/工作区及 open 状态、record/session identity 和必需 ACP 协议1；可选能力未声明不推定支持，也不无条件全部要求。版本有程序来源才记录，否则明确 UNKNOWN；版本未知或不同本身不拒绝。该摘要是最近持久化的握手，**不是 live queue 或升级后实际引擎内容证明**，具体运行失败和逐项 D05 证据继续由原消费者判断。

元数据命令失败是 session bootstrap failure；结构化不兼容是 `ADAPTER_INCOMPATIBLE`，由唯一 outcome/RecoveryAssessment 保留原因，不改写成模型无 JSON。记录写失败不启动后继命令；预 prompt 失败仅引用实际存在的诊断文件，不虚构 transcript。已确认 R03 只补本地，不重新探测/provider 调用；停止旧执行核对 R01 原入口/会话，不明仍走原 D07 裁定。

完整写序、失败切面与合法/拒绝/冷恢复义务见 [本次 transition 计划](../../output/lifecycle-batch-03-20260930/compatibility-20261007/transition-plan.md)。不新增包解析器、installer、配置注册表或兼容性矩阵。C/X 完整桥接仍批外；此补记与 B03 合并评审，不额外开材料轮次。原生协议模拟不等于真实客户端兼容证明，零 native/provider 的边界不变。

## 8. D04：复用原生权限与可靠接通审批、拒绝和续作

**当前规范以 §8.25 为准。** 用户确认 Dual 应专注研发编排，继续扩展成安全沙箱 ROI 过低。§8.1–§8.24 保留历史方案、来源及实验事实；其中“原生不可绕过/模型绝对不可写 control”证明和继续硬链接实验的要求已撤出当前冻结前置，不得凭旧表重新加回。§8.21 的内部委派责任、已有审批与恢复义务继续按 §8.25 保留。

当前 D04 的职责：把批准范围对应到两客户端既有权限/审批配置，保持必要的读取和实现能力，把原生需要批准/拒绝/不明结果可靠交给既有执行与人工决定流程。Dual 不新增命令分类 LLM、通用规则引擎、文件系统隔离或控制存储平台。只读是业务阶段和原生配置约束，不再承诺独立验证操作系统对任意对抗行为均不可绕过；实际 scope/源码身份仍由领域消费者校验。

### 8.1 2026-09-28 可行性核验：常规 Codex mode 不能证明只读

本轮只读检查仓库安装/配置脚本、reviewed acpx 的本地源码和已缓存的 Claude 0.70.0 / Codex 1.10.0 adapter 发布代码。源文件 SHA、定位片段及 build-info 保存在 [本轮证据](../../output/detailed-design-review-20260928/d04-feasibility.md)。缓存包与脚本默认版本对应，不证明未来实际运行解析到相同字节；D07 仍须绑定完整启动身份及实际配置。本轮没有启动 session/provider，没有执行写入攻击测试。

已确认：Codex 1.10.0 的 `AgentMode.ReadOnly` 使用 `sandboxMode=workspace-write`、`sandboxPolicy.type=workspaceWrite`；普通 `sendPrompt` 每 turn 显式传入该 policy。三个公开 mode 中未找到真正 readOnly 的主执行模式，不能只改 INITIAL_AGENT_MODE 或会话初始配置就声称满足 D04。内部 `runAgentFileChangeReport` 虽使用真正 readOnly，却是独立审计调用，不能移作正常作者/评审路径的证明。这里否定的是已核验的模式绑定方案，不断言 Codex 引擎或所有外部配置都不可能实现只读。

acpx 的 ACP `fs/write_text_file` 有自身许可检查，并非无条件写入；但这不能证明 provider 原生工具受同一检查。Claude 已有 policy→SDK/managed settings 传递，实际工具隔离仍未证明。传入元数据、拒绝一个 ACP 请求或收尾无差异，都不能替代 native edit、shell、子任务、额外工具及 session/correction 的共同约束（当前委派解释见 §8.21）。

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

拟议在现有 AcpxRunRequest、session options/record 与 run metadata 中承载一个 adapter 私有的只读绑定，逻辑名原为 `readOnlyBinding`（§8.24 统一为 `executionRestrictionBinding` 并覆盖 implement），由程序生成的不可变文档 ref/SHA 指向具体限制。它是对既有授权的进一步收窄，不是另一个 grant；没有此绑定或版本不支持的只读任务不能默认为 observe。复用既有 request/record 模型，不新增 model 层、通用策略 DSL、远程权限服务或第二授权账本。

| 拟议绑定内容 | 来源、消费及不允许的解释 |
|---|---|
| schema、role purpose、agent=claude/codex、workstream/node/run/attempt identity | Role 由已冻结 definition 决定；TaskHandler/transport 核对实际调用。不能让模型或 session 名称决定角色 |
| 已批准合同 ref/SHA/phase/epoch 与当前输入身份 | 来自 qualification/D07；binding 不能给 GOAL 增加写权限，不能借旧 epoch 续作 |
| canonical workspace、Git/common-dir、control roots、程序 scratch 引用 | 从真实路径推导并验证；scratch 不给模型任意写权限，不能包含或经链接指向产品/control。root/control deny 与程序自身 writer 分别核验 |
| 固定能力语义：必要读取/搜索及受限执行；无未授权产品/Git/control/policy 写或其他副作用；允许 §8.21 所定义的原生内部委派，不允许运行中放宽 | 由受审 profile 翻译成两 adapter 的固定参数；不开放用户自编规则语言。ACP 工具和 provider 原生工具都须覆盖 |
| acpx/provider adapter/依赖与限制配置身份 | D07 冻结组合；记录请求值与实际生效值，缺任一不可用缓存 mode 名推定 |
| 更窄的 correction 限制、session 复用身份 | 不能大于原上限；相同名、相同 cwd 或仅相同模型都不足以复用。需要工具全禁时须有实际接口/证据，不把 --deny-all 当原生全禁证明 |

Claude 现有网络 policy 保留独立语义；只读绑定与它共同生效，冲突拒绝，不取并集扩权，也不把 Claude-specific policy 冒充 Codex 绑定。新增严格字段不能塞进 v2 文档后仍声称旧验证器理解；冻结时明确私有字段版本、序列化/合并/解析、未知格式拒绝以及旧 session 不具新资格。网络策略不在本轮扩大，不重开 R4/Phase B。

绑定必须在启动 agent 进程/建立 session 前被程序核验。provider adapter 以实际构造的 query/turn 参数产生程序回执，包含 policy SHA、engine/adapter 身份、session id、适用调用与实际限制摘要；不能转录模型“我遵守了”的正文。回执进入既有执行事件/run 事实通道，由 transport 强制消费，缺失或不匹配不得发后续 prompt。该回执证明绑定传播，不单独证明原生沙箱有效；ENFORCED/PROVEN 仍须 §8.5 的工具反证。

限制必须在可能运行的启动 hook/plugin/自动工具之前生效，不能等到回执时才发现产品已被初始化动作写入；无法在该副作用前核验配置的组合拒绝启动。模型服务连接与工具网络能力分别核对，只读绑定不新增工具网络权限，不借此扩大已有网络策略。

### 8.4 两条 adapter 的落点与正常／恢复路径

| 执行面 | 拟议落点与行为 | 已有依据及尚未证明处 |
|---|---|---|
| Claude 内置工具 | acpx 将受审集合映射到 `_meta.claudeCode.options.tools`，以实际已验证的必要读取/受限执行集合为准；子任务可依 §8.21 在同一上限内使用，不要求为对称性额外开放 Claude Agent；其他能力不因 preset 或免确认列表复活 | SDK 声明和 adapter createSession 有该接口；native build 的读取/搜索可用性与实际工具拒绝未验证 |
| Claude 额外配置和工具 | 独立核验 MCP、hooks/plugins、模式更新、additional roots、query 重建；程序采用受限上限，拒绝可放宽来源 | SettingsManager 自行合并配置；adapter 覆盖 permissionMode；SDK 文档说明父 managedSettings 可能被管理层丢弃。不能只设 settingSources=[] 或 managedSettings 即宣布安全；也不修改用户全局配置来凑前提 |
| Codex 创建与普通 turn | 按 §8.24 改用同一命名 permissions profile，thread/turn 均传递，不能混用互斥 sandbox/sandboxPolicy 或从可变 mode 放宽；回执不伪造原生确认 | 本机协议有输入和创建回执；TurnStartResponse 不保证再次返回 policy，须由 adapter 从真实发送对象及冻结 session 状态记录，结合反证，不能伪造服务端确认 |
| Codex 控制入口与额外工具 | setMode/setConfigOption、load/resume/fork、review/goal/steer 等能触发执行的入口逐一归属；属于 §8.21 原生内部委派的入口允许但必须同样受限，其余不用的入口在该角色拒绝；不把 mcpServers=[] 当已禁用环境 MCP | 现有代码有可变 mode 和配置合并；原生 readOnly 不等于关闭 execute。必要读取路径及原生工具选择仍是冻结缺口，不预选新增工具服务或引擎 fork |
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

### 8.5 成对验证义务（能力冻结与实施后组合按 §8.23 分开）

| 证明项 | 合法流程必须可达 | 反证与真实消费 |
|---|---|---|
| 固定角色绑定 | Claude 作者和 Codex reviewer 各读取受控样例、返回严格结构化结果，由程序落盘 | 错 agent/版本/epoch/policy SHA 在相应副作用前被 handler/transport 拒绝；去掉强制消费则测试必须失败 |
| 原生与 ACP 工具 | 读取/search 可用，程序正常写 run/产物不被误判为模型写入 | 产品、Git、control、policy、ignored/untracked、链接/别名路径写入及写后还原被阻止；ACP write/terminal、native edit/execute、额外工具逐面核验 |
| 原生内部委派（§8.21） | 同一次顶层执行内的必要子任务可读取并贡献产物，不强制 Claude 开放不必要工具 | 按 §8.22 区分只读/implement：原生 policy 禁止的操作写前拒绝；业务 scope 违规采纳前拒绝；身份/批准伪造由领域消费者拒绝。不能以全禁委派代替 |
| session 与 correction | 合格同绑定续作和合法格式修正可完成，不靠无限新 session 重传上下文 | 高权限旧 session、metadata 丢失、同 cwd 不同 policy、mode 更新、重连/queue/correction 放宽均失败；无绑定不能补默认值 |
| 退出与发布 | 正常产物可进入复评→D03 reviewed candidate；已有可信完成的冷启动只补本地提交 | 任一收尾失败/新增文件/控制变化阻止 sidecar 与后继；去掉比较应被反证抓住；不自动回滚现场 |
| provider/执行入口组合 | Claude/Codex、inline/worker 共享必要用例；四变体只读角色要求一致 | 禁全部工具的实现被正向测试击败；绕过新 profile 的定义/worker 路径被入口测试抓住 |

六维：Declared by=R3 I3 与本节；Proven by=上述成对 slice/冷启动/工具反证（待完成）；Consumed by=qualification→TaskHandler/transport→acpx/provider adapter→validator/read model；Enforced before=外部启动、每个调用及成功发布；Failure route=既有分阶段 signal/恢复解释且不自动扩权；Cannot-bypass=删除任一绑定检查/恢复传播/收尾检查的对照必须失败。当前仅接口定点验证，整项仍 DESIGN GAP。

先做不消耗模型的静态与离线协议/纯函数核验，原生工具有效性仍须有真实执行面的证明；fake adapter 仅能证明传参和消费，不能证明 SDK 或沙箱隔离。后续若需要改代码构建验证，先满足实施授权；需要真实 provider 时另按既有 Verification Gate，不在本轮偷跑。

### 8.6 成本收益基线与用户指定止损

当前候选成本分五组：Java profile/request/run 绑定；acpx 参数及 session/queue 传播；Claude 工具/配置约束；Codex session/turn/控制约束；共用收尾与整链反证。具体范围以逐文件/方法映射及两 provider 能力证据冻结，不能凭组数或“复用”声称低成本；暂不编造工期、代码行数或 token 降幅。现有 patched acpx 的维护不能抵消两个 provider 发布包及依赖的维护成本。

收益判据固定为：产品/control/Git 在批准前不可由模型写入；必要读取、结构化设计及独立复评可完成；同版本合法续作保留限制；无虚构人工批准；不靠把失败全部拒绝来完成安全指标。

2026-09-29 当前取舍：用户已同意采用 §8.21 的责任划分，接受原生内部委派，撤销“Dual 必须独占原生子代理数量”的新增承诺，保留严格权限与领域 authority。父策略写入子配置是有利静态证据，仍不能代替子任务实际受限、后续 turn 和模式切换的证明。hook 故障放行结论不变；不再为禁止原生委派继续投入 hook/引擎调查。原生用量归集、现有数量上限与停止行为须诚实核验；不默认接受无限支出或后台残留，具体风险与运行预算由后续 Gate 提交用户。成本未量化时不得宣称低成本，更不新增逐子任务账本或监督平台。

按用户明确要求，详细设计和后续获授权的实施过程中出现下列情况，**先停止受影响工作并等待用户确认**，不得先做完再告知：

- 必须修改底层引擎核心、引入独立工具服务/权限平台、外层 OS 隔离或其他超出本次 adapter 绑定方向的维护面。
- 只能靠改变 Claude 作者／Codex 评审分工、削弱读取/评审能力、合法续作或严格批准/证据要求达成只读。
- 必需改动或验证成本超出冻结时记录的批准范围/预算；或者实测成功率、上下文重传、token/延迟、维护负担显示预期收益不成立。当前尚无量化基线，后续须先提交估计依据及可接受边界，不得以“没有数字”解释为可以无限投入。

触发时在现有主清单/D04 记录：原预期、实际证据、影响到的验收项、必要成本、可行替代及建议；保留现场，不提交、不回滚用户改动、不启动替代实现。用户确认前不得继续依赖该取舍的工作。普通接口不确定性先在已授权只读核验内调查，不把每次读取文件都升级为批准请求；其他独立且已授权的文档核验仍可继续。

### 8.7 推进顺序（2026-09-30 重评）

旧顺序撤销，以 §8.23 为准：先回答剩余原生权限/停止能力问题，再补实际绑定缺项和成本依据，随后冻结方案独立评审。§8.3–§8.4 的接线与写入边界仍须落实，但产品接入测试归实施后，不再以零实现时的全链通过作为可行性前置。旧逐轮材料记录可在历史证据包查阅，不构成新增待办。

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

### 8.20 hook 自身失败会放行：停止当前候选（2026-09-29）

用户带回 §8.19 的 CC 材料 PASS，随后授权继续必要只读核验。见 [hook 核验报告](../../output/d04-hook-boundary-audit-20260929/assessment.md)及 [CC prompt](../../output/d04-hook-boundary-audit-20260929/cc-review.txt)。新增同版源码经 Git blob/大小/SHA256 核验；快照仅覆盖取用文件，不是全树。运行器跨多个模块，不能把“补一个模块”当成本或充分性承诺。

已确认链：command_runner 启动失败/超时 → HandlerRunResult.error → PreToolUse 仅记 Failed、should_block=false → core hook_runtime Continue → registry 可继续调用目标 handler。退出码 2 缺少拒绝原因同样不阻止；明确的同步拒绝才产生 Blocked。异步/executor-scoped hook 不能作为同步强制门。多 hook 只在有人明确拒绝时阻止，不能消除全部失败的反例。

配置存在 managed/trusted/hash 等装配规则，并非“没有信任机制”；required_load_errors 是加载阶段事实。已核验结果传递链不把运行失败转换为拒绝，未找到改变此行为的现有配置字段；不宣称穷尽所有引擎/管理员接口。即使配置可信，仍有独立的故障放行反例。MCP/custom 的完整覆盖及 core 配置绑定不继续扩展取证；其未决不能挽救已在 collaboration 路径失败的候选。hook 自身由 MCP 执行不等于所有 MCP 目标工具被拦截。

**停止“单靠现有 PreToolUse hook 强制禁止委派”的候选，不设计或运行正常拒绝探针充抵此缺口。** 本轮无 native/provider/hook/注入/agent 或产品/Rust 改动，未新增预算。不自动补监督器、换引擎或接受原生委派；需要这些改变时按 §8.6 交用户。此次为作者静态核验，未经新增 CC 复审；D04 仍 DESIGN GAP，其他九项冻结及实施未授权状态不变。§8.19 是上一轮候选状态，不追溯篡改其证据包。

### 8.21 用户决定：原生内部委派与 Dual authority 分离（2026-09-29）

用户明确同意本节方向并要求继续。授权为**设计调整与必要材料核验**，不是当前会话可启动额外 agent、native/provider 实验或产品实施。保留 Claude Code 作者／Codex 评审客户端分工；模型品牌由实际配置记录，核心流程不增加品牌分支。

#### 责任与验收变化

原生内部委派指：在 Dual 已批准并准备的一次顶层执行中，由该客户端组织服务于当前任务的子任务。它可以产生原生线程与内部调用，但不是新 Dual node、批准、独立 reviewer 或用户指令；不是对任意既有 session、其他 workstream 或高权限执行者的操作授权。

| 决定 | 保留的边界 / 不允许的解释 |
|---|---|
| 允许原生内部 spawn/fork、内部通信及后续执行 | 同一执行目的与原生权限上限，业务 scope 仍适用；两个校验层及拒绝时点见 §8.22，不能借内部通信调用其他已存在的高权限/无关任务。模型不能自行修改限制、批准或扩大工具网络权限 |
| 子任务原始输出由所属顶层执行整合 | Dual 仍按已有作者/评审身份、occurrence、subject/ref/SHA、合同和证据校验采纳产物；内部自评不替代另一客户端的独立复评。子任务声称 approved/ACCEPT 不产生 authority |
| 不要求 Dual 精确独占原生子代理数量 | 优先使用原生已有数量/深度限制；有配置不等于已强制。既有 workflow revision/重试预算不变，不接受无界追加运行 |
| 不新建逐子任务领域账本，也不承诺绝对零超支 | 原生内部事实归当前顶层 Execution/Run；已有通知/日志可作为来源，不成为第二控制账本。usage 归集范围和未知值必须如实表示 |
| 不再以 collaboration 可见、内部委派发生本身判 FAIL | 越权写入/提权、跨执行借权、绕批准/复评/交付或伪造证据仍 FAIL；限制不能生效仍零 dispatch。停止/结果不明沿既有恢复规则处理 |

冻结组合的只读绑定明确包含本节语义，并由 adapter 翻译为实际能力上限；仍复用 §8.3 的版本/role/合同/运行身份/ref+SHA，不为每个原生子任务造一个 grant。旧组合或旧 session 没有证明符合新边界时，不以“现在允许委派”直接复用。Claude 不要求开放不必要子任务；若保持禁用，验证禁用有效即可；开放时适用相同权限义务。

#### 控制链与失败切面

| pre-state / 触发 | owner、顺序及下一消费者 | 失败、终止与恢复 |
|---|---|---|
| 合格 GOAL/当前 scope，准备设计/计划/评审 | 既有 qualification → TaskHandler 固定 binding/baseline → D07 dispatch intent → transport/adapter 建立受限执行；内部委派能力须包含在冻结组合证据中 | 本地准备写失败零外部启动；缺有效限制不回退普通 mode。权限失败仍由执行层产生 typed signal |
| 顶层已启动，原生创建子任务并继续调用 | 原生引擎管理内部线程；每个实际工具执行受同一上限，不能通过 fork/模式变化/其他 session 借权。原生事实由 transport/run owner 关联到当前执行 | 创建后发送失败、通知丢失不等于未创建；不从模型正文补写执行事实，不将其直接转换成 workflow 路由 |
| 顶层收到最终候选 | 先固定可信 process/outcome/材料，再 baseline 检查、ArtifactContractValidator、D06 发布；下一消费者仍是既定复评/资格/人工门 | 收到文本不等于执行已终结；原生“成功”不覆盖权限失败或材料缺失。持久化失败只在证据足够时本地续写，不重跑模型 |
| 超时、取消、adapter/worker 重启或结果不明 | 以整个相关执行的可观察停止/完成证据使用既有 D07 recovery；内部工具不是新的恢复 action | 停止父进程不能推导后台子任务已结束；无法确认时不声称全部取消、不自动重发，按现有未知结果裁定。可用整体执行证据，不要求逐子 Dual 账本 |
| 进入新阶段、修订/批准变更或交付 | 新 occurrence/合同/权限由 Dual 验证；旧原生任务不能随新阶段获得新权限；交付只消费指定批准 candidate | 不复用不匹配 session，不从内部自评推导外部复评通过；终态禁止新的 Dual dispatch，残余外部执行状态不得被终态展示掩盖 |

本表补充现有 D02/D06/D07/D09 的消费边界，不更换 owner 或新建状态机。若具体实现要求改变这些已冻结契约，必须回到受影响工作项与独立评审，不能把它藏成 D04 的局部字段变更。

#### 剩余验证：按必要性分组，不恢复“全部禁委派”的目标

| 需回答的问题 / 所属工作 | 验收与证据复用 | 当前状态 / 冻结位置 |
|---|---|---|
| 合法读取与真实子任务共同可用；子任务不能扩大权限（W03/T07） | 固定版本，分别验证只读与 implement 角色；只读测合法读取/禁止写，implement 测合法写与 native policy 禁止操作的副作用前拒绝。精确业务 scope 违规另按 §8.22/S1 在实际变更校验后拒绝采纳，不新增逐文件 native 拦截。复用父执行切片和共享强制点说明递归/后续 turn，不穷举任意子树 | 子配置静态继承已知；子任务实际强制、后续 turn/模式及跨任务目标隔离未证明。是 D04 技术冻结前的必要能力证据 |
| 原生内部结果不能取得领域 authority（W03/W04/W06/W08；T07/T12/T14） | 合法作者→另一客户端复评→指定人工批准→交付序列；伪造子任务批准、身份/subject/ref、跨 epoch 的产物被同一消费者拒绝 | 设计 owner 与严格校验保留；具体接入后做整链反证，不在此为原生子任务新增模型层，也不把原生层实验冒充 Dual 组合证明 |
| session/correction/queue/inline/worker 与停止恢复（W03/W05/W08） | 冻结实际绑定接口和原生停止语义；验证相关执行存活/完成不明时的超时、冷启动和重复请求，以及顶层收尾后子任务在已批 scope 内迟到写入。后者按 D05 的源码身份/证据消费拒绝，见 §8.22/D1–D3；不宣称收尾可阻止迟到写入 | 同版原生能力/可用观测先核实；adapter 全入口与 D07 的组合证据在实施后验证。不能凭父进程退出推导全部停止 |
| 数量、用量、费用与停止能力到底可见到何种程度（W03/W08；沿既有成本观测） | 优先消费现有 native/acpx usage、运行与通知事实；注明是否包含子调用、是否重复累计。无法拆分时可用有证据的聚合值；缺失/不明不得填零、以顶层值冒充总量或编造金额 | 核实可见范围与已存在限制，不新建逐子完整账本。数量/金额绝对硬限不再是 D04 前置；无法硬控的成本及后台残留风险须在具体 Gate 列明，由用户确认可接受边界，不能默认为已接受 |

技术冻结的当前组织方式以 §8.23 为准；保留的要求为剩余权限能力可行、绑定/实际事实生产者与停止语义明确、成本未知与可接受运行边界已说明、受影响设计独立复审。实现后的全链回归及真实 E2E 仍按原 Gate，不前移也不取消。数量观测暂缺不应被包装成权限失败；权限失败也不能降级为预算警告。若无法在现有机制下证明权限上限或安全恢复，不为 C 新建监督平台，按 §8.6 再提交具体取舍。

#### 证据与状态

§8.20 的 hook 材料获 CC PASS，支持停止 hook 单独硬限制候选；“所有 adapter 候选已穷尽”超出部分源码与接口核验的证据，采用“已查候选不足，继续无定向排查收益低”的口径。此前父进程只读/原生工具拒绝证据保留，但不外推到真实子任务。旧 spawn 禁止探针目标撤销，不把旧未证/FAIL 追溯改判为 PASS。

**本节已获用户带回 CC 设计 PASS；D04 仍 DESIGN GAP，实际能力未证。后续 §8.22 的分层澄清为新修订，待复审。** 不启动新运行，不申请预算，不改产品或上游 Rust，不自动恢复已停探针。后续 Gate 如需受控 native 子任务，必须明确列出允许的实验副作用并取得授权；当前禁止本会话新增 agent 的指令保持有效。

### 8.22 验证分层：原生权限、业务 scope、源码与证据（2026-09-29）

用户要求下一份验证清单明确三类 owner 与拒绝时点。规范性验收细化见 [三类边界验证清单](../testing/20260929-d04-three-boundary-verification-checklist.zh-CN.md)，仍归 W03/W04/W05/W06/W08，不新增工作流、权限平台或平行任务清单。本节与清单细化 §8.21，设计认可不能代替实际能力证明。

| 边界 | 事实 owner / 消费者 | 拒绝时点及证据上限 |
|---|---|---|
| 原生权限强制 | native policy/引擎与沙箱负责实际强制；TaskHandler/transport/adapter 负责有效绑定，执行事实进入当前 run | 缺有效绑定在 dispatch 前拒绝；policy 禁止操作在副作用前拒绝。只读与 implement 各有合法/拒绝对照；implement 原生可写根不自动等同精确业务 scope |
| 业务 scope 校验 | 已批 TaskContract/批准事实；ChangeScopeEvaluator→ArtifactValidateHandler→相应路由/验收/交付消费者 | 缺批准在执行前拒绝；实际 Git 变更违规在校验后阻止作为合规成果继续。当前静态证据不证明精确文件写前拦截；违规 outcome 可伴随节点 COMPLETED，不能只看 status |
| 源码漂移与证据失效 | D05 源码/合同/材料身份及各验证/发布消费者；已有 EvidenceCollection/ExternalVerificationEvidence 只覆盖其适用路径 | 重验后拒绝错基准采纳，包括顶层收尾后迟到写入、验证中及发布间隙漂移。检测变化不是阻止变化；S0 证据不变成 S1 证据，也不因 S1 出现自动销毁 S0 历史 |

CC LOW-1 采纳：增加 implement 收尾后、已批范围内迟到写入的显式反例；只读零增量 baseline 与 implement 完成源码 identity 分别处理。D05 重验为设计兜底，不宣称普通自测/验收/交付现已全量覆盖；现有 EvidenceCollection 在无适用 guard 时直接执行，必须补实际消费者的必经性证明。前后 hash 相同也不能单独排除混合版本执行或写后还原，相关活动未明时沿 D07 处理。

LOW-2 采纳角色覆盖，但拒绝时点须分层：N1/N2/N3 验原生上限，S1/S2 验业务路径/操作范围。扩大为 implement 每次精确路径写前 native 拦截不属于 LOW 自动采纳，若确有必要须按 §8.6 提交独立取舍。严格合同、批准、scope 违规拒绝不撤销。

INFO 的“Claude 拒绝文本已覆盖后代逃逸”不采纳：文案不能代替后代实际执行证据；旧证据只证明当前会话所列隐藏调用被拒。若角色关闭子任务，可验证创建入口确实拒绝，而非启动子任务证明其未启动。本轮未改变原始证据判定。

历史处置（2026-09-29，D2-P 独立前置现已撤销）：三边界清单获用户带回 CC 材料 PASS、2 LOW。补记：D1 增加 prepare 前注入，但符合资格的新 preparation 可以绑定 S1，不能把它误判为必须拒绝；prepare(S0) 后变为 S1 的旧提交则必须拒绝。D2 增加必需的真实消费路径映射 D2-P，区分 guard==null、其他守卫与无守卫路径，并绑定正反用例；映射尚未产出，不能称覆盖完整。细节见清单；本增量为作者自查，未另经独立评审。

清单所有新增用例均 NOT RUN；真实实验仍需单独 Gate/副作用与预算授权，当前会话不启动 agent。D04 未冻结，不进入实施。

### 8.23 重新评估：废弃过度前置，限定 D04 的责任（2026-09-30）

用户要求废弃过度部分并重新评估。本节取代 §8.7 的旧推进顺序、§8.21 的冻结组织方式及 §8.22/D2-P 的独立材料前置；§8.1–§8.20 的实验结论保留为历史事实，不再构成待办集合。§8.21 的内部委派方向、严格权限与领域 authority 保留。当前结论：**现有 adapter 方向有继续核验价值，但 D04 仍 DESIGN GAP；不需要新增设计体系，也没有实施或运行授权。**

#### 废弃和归还的具体内容

区分实际变更与再次确认：全禁委派等已在 §8.21 撤销，平台扩展从未获准，本次确认不再列为候选；全链回归原本已分层，本次废弃其前移解读。此次实际删除的是 D2-P 独立交付要求、旧推进顺序及逐次材料评审的默认安排，不冒称删除了已实现的平台或一批原本不存在的强制要求。

| 原要求或推进方式 | 当前处置及理由 |
|---|---|
| 全禁 native 委派、数量独占、逐子领域账本、绝对零超支，以及为这些目标继续 hook/引擎排查 | 废弃，不能作为备选任务待重启。既有否定证据足够停止该路线；原生内部委派依现有权限执行，不取得 Dual authority |
| 穷举原生工具/任意子树/所有未采用配置，或只为增加覆盖重复已有效切片 | 废弃。只核验选定组合实际开放的执行面；共享强制点可以复用证据，有实际差异才补对照。未采用能力须确认不会被该组合启用；无法限定实际可用面是具体缺口，不能靠“不测”消除 |
| 把 S/D 全链、四变体/inline/worker、发布失败和冷启动测试都当 D04 冻结前的绿色结果 | 废弃此前置关系。这些是 W03–W06/W08 的实施后验收，保留完整义务，不伪称已经完成。D04 冻结前只明确接线、owner、失败出口和可行性，不要求未实施产品先通过 |
| D2-P 独立映射材料及其单独审查/完成门槛 | 废弃。实际证据消费者、guard==null/其他守卫/无守卫检查并入 W04/W05/W08 既有覆盖地图与序列测试；不新增文档交付，不排在 D04 原生能力验证前 |
| 每次 LOW/措辞补充再造证据包、独立评审一轮，并据此延长冻结 | 废弃固定轮次。普通材料修正作者核验即可；改变 authority、验收或跨层行为仍按 R3 §7.1 在受影响批次独立评审，本次语义调整也纳入下一次适用评审，不以拆分规避 |
| 将上游 Rust 修改、权限/工具服务、进程监督或新隔离平台列作默认后备 | 从当前方案排除，不保留为自动后备任务。现有 adapter 路线不足时止损，交用户比较替代方案；不继续堆机制维持原推荐 |

#### 按已有证据重新判断收益与剩余问题

旧原生边界报告已有父执行读取、禁止工具/写入及开放对照的切片；同版源码已有子配置继承依据。它们支持继续验证现有接口，但不证明真实子任务强制，也不证明产品接入。旧 hook 故障放行证据支持停止禁委派路线，不能据此否定整个 adapter 方向。既有报告详见 [原生边界材料](../../output/d04-native-boundary-review-20260929/review-package.md) 与 [委派责任说明](../../output/d04-native-delegation-design-20260929/assessment.md)；其旧禁委派阻塞按本节撤销，不改写原实验 PASS/FAIL/INVALID。

| D04 剩余问题 | 冻结前需要的结论 / owner | 实施后证明，不提前索要 |
|---|---|---|
| 选定组合下必要工作可用、实际权限不扩张 | native policy/引擎负责副作用前强制；只读与 implement 各自合法/拒绝成对。开放子任务的客户端须有实际子任务证据；未开放者证明入口不可用即可。后续 turn/模式/跨任务风险按实际开放路径及共享强制点论证，不重测任意组合 | TaskHandler→transport→acpx→adapter 的所有支持入口确实消费同一绑定；四变体/inline/worker、correction/session 不能绕过 |
| 正常结束、取消及不明状态能否可靠区分 | native/transport 提供整次执行的实际事实，D07 消费。需要可用的正常完成路径与停止/不明对照；父 PID/最终文本不足。不能把一切都判 UNKNOWN 来通过；不要求证明永无后台残留 | D07 未知不重发、D06 本地补写不重调 provider、D05 拒绝迟到写导致的错基准证据，终态与恢复投影一致 |
| 将已证明能力接入现有 adapter 的成本是否值得 | 复用已有版本/配置身份和接口地图，仅补实际缺口：参数生产者、消费者、检查时点、准备/失败事实及预期改动。usage 只核实现有统计覆盖，未知明确保留；具体预算/残留风险由 Gate 决定，不新增逐子计费平台 | 获准后核对实际改动、测试与维护成本；不满足收益或超出批准边界按 §8.6 停止 |

停止与源码漂移存在真实依赖：若不能确认验证输入稳定，必须走 D07 的不明处置，不能把两次 hash 相同当稳定证明。将 D05 回归放回实施后不表示可以预先依赖一项没有可行机制的防护；发现现有接口连正常完成/稳定验证对象都无法支持时，D04 不能冻结，提交具体取舍。

重评时两组关键能力证据仍缺：**开放子任务的实际权限强制，以及正常完成/停止/不明的可观察区分**；其后 Gate 与本节最新 CC 处置已解决这两组能力取证问题，不再列为待测。本轮静态重读了 scope 采集评估链与 EvidenceCollection 的 null guard 路径，只支持 S/D 应归既有消费者的判断；没有运行新用例，也不宣称覆盖完整。剩余工作不能用“再写一份清单”替代这两组证据。

#### CC 重评处置（2026-09-30）

用户带回“D04 过度设计与冻结距离”评估：认可废弃范围，D04 不可冻结，提出两项 LOW。采纳合并补证与复用 Claude 旧证据的方向；以下边界由作者核对，未另经 CC 复审，不形成新评审轮次。

- **Claude 已测固定只读组合不再重复验证 Agent 禁用。** 原始 restricted/events.jsonl 的 init tools 为 Glob/Grep/Read，agent_hidden 实际调用返回工具不存在，最终 subagent_stats.spawned=0；结合原有读取和配置诱饵证据，足以支持该组合入口不可用。依据是实际分派与配置，不是拒绝文本中的 “in subagents as well”。此结论不外推未测 implement 配置、其他版本或已完成 adapter 接入；案头对照实际拟用角色配置及共享拒绝点，能复用即复用，有具体差异才报告缺口，不默认再跑 Claude。
- **将 Codex 两组能力缺口合为一个有界 Gate 家族。** 权限切片同时采集完成/子活动事实，正常与取消可用各自 fresh session 子场景，共用一次明确范围/预算申请。取消必须在子活动实际存续时触发并记录后果；不能拿子任务早已结束的样例证明取消语义。不承诺同一会话包办，也不承诺观测零成本；超限或核心前提失效按 Gate 止损，不自动追加运行。
- **原生实验不验收 S1 产品接线。** implement 中 native 可写根与业务 scope 的差别可用临时样例说明，但不能要求未接入 Dual 的 native 桩现场走 S1，也不能把桩模拟拒绝当业务边界证明。S1 正反与实际后继仍由原 W03/W04/W08 验收；本轮 native 重点为合法操作和 policy 禁止操作的强制对照。
- **权限通过不是充分冻结条件。** 还须有完成/停止/不明事实的实际语义和 D07 可消费来源、角色配置适用性、绑定缺项与成本依据。出现通知/usage 字段本身不证明覆盖所有子活动；原本地桩 usage 为合成值，不能证明真实总用量。一次有界实验是组织方案，不是保证取得充分答案；FAIL/INVALID/观测不足停止并判断原因，不无限补测，也不直接把诊断无效解释为引擎方向失败。

因此，已知新增原生实验集中 Codex；两客户端的角色适用性与接线成本仍由同一案头工作核实。“Claude 所有角色已证明、所有剩余工作都在 Codex”超出当前证据，不采纳。最终冻结仍依完整条件，而不是按完成一组实验、一份材料和一次评审计数。

#### 本次 Gate 与接线核对已准备（2026-09-30）

[合并 Gate](../../output/d04-freeze-gate-20260930/gate.md)与[接线/成本核对](../../output/d04-freeze-gate-20260930/binding-and-cost.md)已完成当前案头准备，脚本及计划在同目录；只维护这一运行对象，不另造评审包。6 项离线守卫/契约检查通过，15 份既有引用身份相符，native/provider/子任务运行均为零。预算候选为 3 个顶层 session / 各 1 子任务、36 个本地请求、总计 300 秒，零外部模型调用；**NOT AUTHORIZED**，须用户明确批准本 Gate 副作用与预算后才执行。

接线核对未把免确认名单当 Claude implement 的工具禁用证明；该角色复用显式 tools 分派的设计依据，组合实证在实施后补。Codex 主路径 workspaceWrite 映射和逐 turn 策略确需改动。实际产品控制目录布局不能从临时 writable-root 样例外推，须在能力结果后与既有 binding/roots 对照；如需要迁移存储或增加隔离平台，按 §8.6 止损。本次案头工作明确了改动与成本来源，未虚构总工期或声称接线已冻结。

#### 已批准 Gate 的执行结果（2026-09-30）

用户随后明确批准，已完成 [合并 Gate 结果](../../output/d04-freeze-gate-20260930/result.md)：3 个 native 顶层会话、3 个实验子任务、20 个本地请求、零真实模型调用，无重跑。只读与可写角色的真实子任务权限切片 PASS；合法操作可达，受保护写、链接绕出与提权拒绝。取消在 running 时触发，状态变为 interrupted，有限观察无迟到写；但根程序随后退出且子工具无终止结果，**全部底层操作停止仍 UNKNOWN**，不能宣称 interrupt 单独保证停干净。

正常同步结果与取消不明已有不同原生事实，D07 的消费边界有观察依据；当前 codex-acp 也有可能由超时收尾推导 failed 的路径，必须保留原始来源，不把 adapter 收尾状态冒充全部停止证明。此为现有接线的定点核对，不新增状态机。实际产品内控制目录与原生可写根的对应仍不能由临时样例外推，结合接线成本交受影响范围 CC 评审；不自动冻结、不增加实验预算、不进入实现。准备段的未授权状态与原 Gate 字节作为历史保留，当前以实际 authorization 和结果为准。

#### 合并 Gate 的 CC 复审处置（2026-09-30，当前）

用户带回的 [CC 原文](../../output/d04-freeze-gate-20260930/cc-freeze-review.txt)认为 D04 具备冻结条件，附两项 LOW。作者采纳两组能力证据结论：所测 pinned 组合下，两角色真实子任务合法操作/原生拒绝成对成立；正常同步完成与 running→interrupted、全部底层停止 UNKNOWN 可以区分。不追加已完成切片的原生实验，不把全部停止证明恢复为前置，也不将材料结论记为产品接线已实现。

**LOW-1 部分采纳、冻结推论暂不采纳。** 应明示 implement 实验保护的是可写根外 control，不证明产品内 `.agent` 隔离。但“今日由 writer 归属+baseline+S/D 保护，因此隔离只是可选硬化”的事实依据不足：

- `WorkstreamPaths.java:7–13,111–140` 将程序控制目录、node、gate、event 放在 workspace 下的 `.agent`；`AcpxAgentTaskHandler.java:170–175` 的 run 目录同样在其中。
- `ArtifactValidateHandler.java:158,184–191,333–345` 的 status、未跟踪文件和 diff 采集明确排除 `.agent`；因此该 scope 链不能用作控制账本防篡改的证明。业务源码漂移校验也不因此成为控制记录 writer 身份证明。
- §8 的受保护 writer/可信 baseline 是待实现设计要求，不能写成当前已有能力；§8.23 Gate 准备段及受审 `binding-and-cost.md` 已明确要求核对实际布局，不能由 workspace 外样例外推。R3 I3 也禁止按路径豁免程序工件。

以上是对冻结依据的反证，不是已经运行攻击或证明全部现有校验均可绕过；本轮未穷尽其他保护入口，也未证明现有原生接口无法表达该限制。保留一个案头决策：对实际支持角色/目录指出可信控制事实如何免于模型篡改、对应现有接口和成本，保留程序合法写入。它不是要求 native 逐文件强制业务 scope。若已有机制满足，冻结机制并把组合 cannot-bypass 留在 W03/W05/W08 实施验收；若需要迁移控制存储、新隔离机制或降低既有 authority 保护，按 §8.6 提交用户取舍，不能默认为可选硬化。此项沿用 Gate 前已登记的布局缺口，不新立平台或实验任务。

**LOW-2 采纳为 D07 消费契约的明确化。** transport/run owner 保存带来源与执行身份的原生结果/工具观察；D07 的既有执行结果与恢复消费者判定正常完成资格，投影不自行判断。拟议机检条件为：当前 attempt/thread/冻结绑定一致；实际开放执行路径的同步工具结果有可核对的对应关系与终止结果；原生正常完成事实在场；不存在未解释的在途或异步工具标识、观察缺口或相互矛盾事实。只有满足这些条件才可作为正常完成候选，仍须通过 I2/I4 的产物和基准校验。模型末句、父进程退出、adapter 超时 `finishOutstanding` 推导的 failed/completed 均不能补齐缺失原生事实；只有 interrupted 或结果不齐时按 UNKNOWN 禁自动重发。并非要求新增逐子任务领域账本或证明任意后台进程绝不存在；开放路径若无法提供上述对应关系，就不得仅凭“未见 pending”推导齐备。具体事件映射与正反反证由 W05/W08 在接线时完成，不在本文宣称已有产品机检器。

当前结论：**两个能力缺口取证完成；实际 control 保护的冻结依据仍有争议，D04 不据本次 PASS 自动冻结。** CC 未审 binding-and-cost 全文、实际 roots 表达能力和完整 implement 请求；作者此次定点读码也不扩充其独立核验范围。仅该受影响结论需要澄清，其他九项和已采纳实验结论不重审。设计冻结、产品实施授权、整链验收与真实 E2E 仍分别判断；本轮无新运行、产品修改或预算申请。

#### LOW-1 定点复核后的结论（2026-09-30，当前）

用户随后带回 CC 定点复核：撤回“已有 writer/baseline/S/D 保护，因此隔离只是可选硬化”以及由此导出的可冻结结论，确认本项是阻止 D04 冻结的未决设计决定。作者与 CC 对阻塞分类已一致，原两组能力证据继续成立；LOW-2 的 owner 与消费契约已在上段补明，具体接线仍是实施验收，不能再记作未处理文字项。CC 新报告未逐项盘点所有 control 消费者、未验证 D06 扩展可行性；其“无任何运行时拦截”不在本文扩大为已穷尽所有配置/入口的证明。

对 CC 提出的出路须保留两个限定，尚未选定或授权任何方案：

1. **扩展 D06 完整性校验不是现有写前隔离要求的等价实现。** D06 的 ledger owner 负责提交一致性；增加收尾/读写会话 hash 校验最多先提供检出能力。需回答可信预期值存在哪里、模型能否连同 manifest 一起改、非协作写者是否遵守锁、检查到消费之间如何保持同一可信字节。不能仅凭“owner 内、非平台”认定成本低或已满足模型不可写受保护路径。若改为容许写入但拒绝采纳，必须显式修订原要求并经用户取舍，不能把它当普通接线。
2. **三条出路不是已证明穷尽的选项。** 当前尚未核实既有 adapter/native policy 是否能在真实布局下表达控制目录禁写、同时保留程序写入和合法产品实现；仍应先做这个定点案头判断。可行则延续已批准的 adapter 方向；不可行再比较存储迁移及明确接受残余风险等实际方案。迁出工作区也须证明位于实际允许写集合之外，不能以路径移动本身冒充隔离。新增机制、范围扩大或保护要求降低均按 §8.6 停止提交用户。

本次只同步评审结论和上述设计判断，不启动 D06 扩展、存储迁移、新实验或产品实施。所需反证仍围绕同一控制事实：合法程序写可用；模型/子任务不能篡改批准和控制记录；异常/重启及后继消费者不能把未知或被改字节当 authority。具体可实施机制未定前不宣布闭环。

#### 唯一推进与退出规则

原有有界 Gate 已完成，两个能力缺口不再补测。当前只核对实际控制目录的可信保护机制、现有接口与成本，并澄清上述受影响冻结结论；不重开其他九项、原生工具全量调查或 D2-P。证据支持现有方向时完成设计冻结，仍须取得产品实施授权；若需新机制或成本收益偏离，停止并提交用户取舍。未来确有新的具体反例需要补测时，须说明既有证据为何不足及结果改变哪个决定，另按 Gate 判断，不能由本次评审自动获得运行预算。

N/S/D 的 owner 与拒绝对象保持分离：N 在禁止副作用前强制，S 在 scope 违规后拒绝合规采纳，D 在证据消费时拒绝错基准。合法读取/实现、作者→独立复评、目标/执行/交付批准、精确 scope、证据与恢复验收没有删除。设计冻结、实施授权、整链回归和真实 E2E 仍分别判断。

用户带回 CC 已独立评估 §8.23 的废弃与冻结距离；本次处置的收窄说明为作者核验。仅更新当前四份文档，不新建材料包或平行主清单，不修改历史证据、产品代码和现有用户改动。

### 8.24 产品控制目录保护：现有接口的具体接线候选（2026-09-30）

用户已授权补齐本节设计并交 CC 作受影响范围复审，未授权实施或新运行。本节接续 §8.23 的最后一个未决决定，不重开其废弃项。选择候选是：**程序 writer 保留控制目录写入，模型原生工具受限；ACP 宿主不代模型写文件或执行命令。** 不迁移控制存储，不以 D06/S/D 的事后检出替代写前限制。当前只完成接口/源码核验及设计，D04 仍 DESIGN GAP；既有两组原生能力证据保留，但不外推为本组合已证。

#### 接口事实与证据上限

| 已核对来源 | 支持的事实 / 不支持的推论 |
|---|---|
| `ClaudeExecutionPolicy.java:89–118`；acpx `src/acp/claude-execution-policy.ts:57–100` | 现有接口能把 `deny_write` 同时送入命令 sandbox 的 `denyWrite` 和原生编辑的 `permissions.deny`。Java 当前只列当前 workstream 的 `nodes` 与 `execution-authority`，不是整个 `.agent`；而 run 下的 policy 另在 `runs/<run>/execution-authority`。这既否定“完全没有限制配置”，也不能证明全部 control 已受保护 |
| 同版 Claude SDK `sdk.d.ts:1956–1978,2683–2766`，adapter `acp-agent.js:createSession` | `settingSources=[]` 不消除管理员策略；父 `managedSettings` 可能被丢弃。`resolveSettings` 返回 effective/provenance/sources，可作配置诊断；声明明确不是安全判定、不执行 `policyHelper`。不把类型声明、设置快照或内部 `get_settings` 协议名当已可用的强制回执 |
| 已导出的 `ThreadStartParams.json` / `TurnStartParams.json` 的 `permissions` 字段；Codex adapter `newSession` / `sendPrompt` | 两请求均可选择命名 profile，分别与 `sandbox` / `sandboxPolicy` 互斥；当前 adapter 仍从 AgentMode 构造逐 turn sandboxPolicy。只改创建参数或 ReadOnly 常量不足，需改这两个真实发送点 |
| 同版 Codex [profile 定义](https://raw.githubusercontent.com/openai/codex/3d2ee51ca2d5db578f328aa75e20aa22c0197c9a/codex-rs/config/src/permissions_toml.rs)、[解析](https://raw.githubusercontent.com/openai/codex/3d2ee51ca2d5db578f328aa75e20aa22c0197c9a/codex-rs/core/src/config/permissions.rs)、[路径判定](https://raw.githubusercontent.com/openai/codex/3d2ee51ca2d5db578f328aa75e20aa22c0197c9a/codex-rs/protocol/src/permissions.rs)、[macOS 下发](https://raw.githubusercontent.com/openai/codex/3d2ee51ca2d5db578f328aa75e20aa22c0197c9a/codex-rs/sandboxing/src/seatbelt.rs) | 命名 profile 可表达大根可写、子路径只读；路径判定及 Seatbelt 生成包含只读子路径排除。更具体的 write 可重新放开子路径，因此必须核验完整合并结果。默认保护的 `.agents` 不等于 Dual 的 `.agent`。这是现有机制依据，不是已完成内部目录的运行反证，也不授权 Rust 修改 |
| acpx `src/filesystem.ts:writeTextFile/isWriteApproved`、`src/acp/client.ts:handleWriteTextFile/createTerminal`、`src/acp/terminal-manager.ts` | 宿主请求有独立许可判断及文件/进程副作用，不能继承原生工具沙箱的证明。`--no-fs/--no-terminal` 只是不宣告能力；处理器仍须强制拒绝。不据此声称已在当前默认配置下成功绕过 |

本地 npm 核验身份：Claude adapter `/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@agentclientprotocol/claude-agent-acp/dist/acp-agent.js` SHA-256 `f0cbbe408bb758cc4bacdae9a244bcac6efbdb6413f680195d017648abc6d816`；同树 SDK `sdk.d.ts` SHA-256 `65ec4f17eb95dc6fa85d964249d4d27e31a150e4bfbaf94c531090862b7a1fd7`；Codex adapter `/Users/kriswang/.npm/_npx/208e725ef8df0f5d/node_modules/@agentclientprotocol/codex-acp/dist/index.js` SHA-256 `4602784c5896fbf05a7d89b09655bacc768d0bf281e0d03a10333ff81da45268`。协议文件沿用 [已有导出目录](../../output/d04-readonly-binding-design-20260928/protocol/)。上游 commit 沿用同版源码身份链，不宣称已复现二进制构建。这里只读这些材料，没有调用 settings resolver、native 或 provider。

#### 唯一绑定与路径展开

领域 authority 仍是批准 occurrence、合同与 ledger；definition 决定角色，TaskHandler 从已通过 qualification 的当前调用生成限制，transport/acpx/provider adapter 消费，run 保存执行事实，投影不参与批准。将 §8.3 尚未实现的逻辑名 `readOnlyBinding` 统一为 `executionRestrictionBinding`，私有格式 `acpx_execution_restriction.v1`，适用只读与 implement；不是新增并列绑定或领域模型。无字段/旧格式不能默认成宽松 profile，旧 session 只允许诊断，不获得新版本续作资格。

绑定继续采用程序生成的不可变 ref+SHA，位置为既有 `runDir/execution-authority/execution-restriction.json`；它本身也受控制目录禁写保护。字段固定为：schema、purpose、client、workstream/node/run/attempt、contract ref/SHA/phase/epoch、input refs/SHA、canonical workspace、protected roots、scratch、acpx/adapter/native/config 身份、实际工具面、host write/terminal 禁用、native profile 参数及网络 policy 引用。字段集合严格解析：缺项、重复键、未知格式与身份不符拒绝；宿主侧的信任来自当前程序请求及已验证会话内存，不能从模型可提供的 `_meta` 或模型正文重建。不会允许模型提供路径、profile 或豁免。secret 不写入绑定，model/provider 只按既有 D07 观测及身份约束处理，不把 GLM/Claude 品牌写成领域条件。

同一绑定内区分**调用身份**和**可复用策略指纹**：前者随当前 occurrence/attempt/input 更新，后者由 workstream、目的、合同权限 epoch、canonical roots、scratch、工具面及完整策略/版本内容计算。`session-options.ts` 的序列化、合并、持久化、兼容检查一起迁移，不能只比旧 ref/SHA 或只比 session 名。相同策略的合法下一 turn/续作可接受新调用引用，但先验证 D07 续作资格；角色/epoch/根或配置变化不得原地提权，旧高权限 session 不下放复用。fresh session 不能自动重发 UNKNOWN。correction 的更窄限制仍须实际生效，不能因沿用此指纹省掉检查。

| 实际路径 / writer | 模型上限与展开规则 |
|---|---|
| workspace 下整个 `.agent` | 两角色均只读，包括所有 workstream、nodes/gates/events/current、contracts、runs、queue、delivery、policy、baseline、prepared 文件；新建文件自动落在相同限制内，不逐文件维护名单，不对输出 sidecar 打写洞 |
| 工作区 `.git` 路径、真实 gitdir/common-dir，以及实际加载的配置/adapter/策略输入 | 均禁止模型写；记录逻辑路径与真实路径，不把 `.git` 文件当目录而漏掉 worktree 的外部 gitdir。现有 `.codex/.agents` 保护保留，Claude 加载的配置路径同样不得经可写根或别名开放。若批准的产品任务确实必须修改这些执行控制输入，应停止并提交具体取舍，不静默撤销保护或声称任务可完成 |
| 产品工作区其余部分 | 只读角色不得写；implement 可在原生可写上限内修改。精确文件/CREATE/MODIFY 仍由 EXECUTION 与 S 校验，不承诺 native 逐文件 scope 拦截；产品修改后旧证据按 D05 失效 |
| native 工具 scratch | 仅既定、独立且可追溯的临时根；不能包含 control/配置/Git 或经链接指向它们。绑定固定其生命周期，普通重连不任意改根；现有引擎默认临时目录须核对有效上限，不能以默认存在宣布全 `/tmp` 获准 |
| Java ledger/run writer、acpx 自身 session writer、native 引擎内部状态 writer | 属程序执行面，不经模型 ACP 写请求，也不把全体程序进程一起放进模型工具沙箱。输出由 stdout/typed result 进入既有 writer 校验后落盘；原生引擎自身必要状态写不等于模型工具可写这些状态。程序可写性须有正向验证，不能只画进程图推定 |

程序在启动前创建并核验受保护目录，以真实路径、类型与别名检查拒绝不明确布局；profile 展开禁止 control 内的更具体 write、重叠 scratch、额外目录合并放权。不能只做字符串 `startsWith`。删除/重命名目录或祖先、符号/硬链接和写后还原归下面反证；无法用现有机制保持边界时停止，不临场加自制文件系统沙箱。外部可信管理员并发改变配置不在自动可修复范围，检测到变化拒绝新调用并保留现场。

#### 两客户端和 ACP 的具体接线

**Claude：** `AcpxAgentTaskHandler` 对这些角色必产限制，不再依赖 E1/network grant 是否启用。`AcpxAgentTransport` 在创建/ensure、prompt、重连及 correction 前核验 ref/SHA 与当前调用；`agent-command.ts` 传入完整限制，`claude-execution-policy.ts` 负责把既有网络 policy 与角色限制按交集编译，网络权限不扩大。不得给严格 v2 network 文档偷塞字段；新 binding 独立引用既有 network 文档，冲突准备失败。`createSession` 必须在最终 options 合并后检查再 query，不能只在被后续 spread 覆盖的输入处检查。

- implement：保留批准的产品根写能力，将上表 protected roots 全部加入 `filesystem.denyWrite`，并为原生编辑生成对应绝对路径 `Edit(//绝对路径)` 与后代 deny；只读：产品根也禁写，仅保留已核实的必要受限执行与 scratch。编辑 deny 和命令 sandbox 两面都须生效，不能以一面替代另一面。 LOW-1 补充：冻结前先按同版原生规则匹配器确认实际开放的 Edit/Write/NotebookEdit 各由何规则约束，不能从 Edit 名字外推整个修改工具族；不共享该规则的工具须采用本版支持的对应 deny，并纳入实际分派反证。未开放工具证明入口不可用即可，不能为凑矩阵开放。不能把有无覆盖的机制决定留到实施时猜，也不能全禁产品编辑来绕过合法实现对照。
- 明确 `sandbox.enabled=true`、`failIfUnavailable=true`、`allowUnsandboxedCommands=false`，拒绝 bypass/approveAll 和绕过 sandbox 的 fallback；工具集合按受审角色构造，保持既定 Agent 入口处置，不为对称性额外开放。额外 MCP/hooks/plugins 不允许从项目或用户配置悄悄恢复；已冻结的必要读取面保留。
- 在 adapter 所用同版 SDK、同 cwd/环境下，使用 `resolveSettings({cwd,settingSources:[],managedSettings})` 检查 effective 及细粒度 sources；检查被丢弃的父限制、filesystem disabled、额外 writable/excludedCommands、模式/工具放宽。它仅是启动前拒绝不合格配置的辅助手段。存在未覆盖 `policyHelper`、动态来源或配置不能对应实际启动时拒绝这个组合，不修改全局配置、不改用无保护模式。新建/query 重建/恢复前重核；正常 turn 不允许控制入口修改限制。
- **仍须证明**这套参数在拟用固定组合的 native 编辑与命令工具上实际强制，并排除 resolver 与实际加载不一致。拒绝全部有管理员配置的环境不是成果，合法当前环境必须存在可用路径；现有接口若不能同时做到可用与强制，按 §8.6 交用户，而不是把未实现的“核验实际生效”留成万能函数。

**Codex：** 同一 binding 编译一个 adapter 私有命名 profile（名字由策略指纹派生，不复用任意用户同名 profile）。通过 `createSessionConfig` 送入现有 config 的 `permissions.<name>.filesystem` 表；无 `extends`，绝对路径键、值 `read|write`，不添加通用权限配置入口。示意条目为 `workspace=write`、`workspace/.agent=read`、`gitdir/common-dir=read`、`scratch=write`；只读角色将 workspace 改为 read。必要系统读取沿已核实原生读策略明确展开，网络另按既有上限，示意不是可直接运行的完整默认配置。必须检查最终合并配置中不存在更具体 write、环境 MCP 或 additional roots 放宽，不依赖默认 `.agents` 规则保护 `.agent`。

- `newSession` 的真实 `thread/start` 使用 `permissions=<name>`、禁止提权的 approval 设置；省略互斥 `sandbox`。`sendPrompt` 的每个 `turn/start` 使用同名 `permissions`、相同 approval 上限，省略互斥 `sandboxPolicy`，不再从可变 AgentMode 重建旧 workspaceWrite。这同时覆盖普通输入和 correction 的真实发送点；不将完整 profile 再转回会丢失 carve-out 的旧表示。
- load/resume/queue 重连重新验证同一 profile 字节及调用身份，重新传递可用的绑定元数据；在无法重建相同限制的入口拒绝，不能只复用 thread id。mode/config/additional-directory 和其他执行入口在 adapter 共用此检查；原生内部子任务继续由已有共享继承/强制点约束，不增逐子领域账本，control carve-out 的继承也不能只凭旧根外 fixture 宣称已证。
- 回执由实际发送对象和可验证的配置来源构成，分别记录 profile ref/SHA、thread/call identity、实际参数及检查结果。不存在的服务端 effective-policy 字段不得伪造；profile 名回显不能替代配置内容或工具实证。未知 profile、冲突参数或本版不支持时准备/执行失败，禁止 fallback 到普通模式。

**ACP 宿主：** 对绑定执行，在 `client.ts` 的 `handleWriteTextFile` / `createTerminal` 实际分派之前拒绝，且底层 filesystem/terminal 公共入口检查同一绑定语义，先于 mkdir/write/spawn；不是只有 capability 不宣告，也不能被 `approve-all` 或 permission policy 放开。read 请求保留必要能力，capabilities 分别表达 read/write。合法产品编辑和命令通过原生受限工具完成，程序 writer 通过自身 API 保存产物；两者不借该请求通道取得宿主权限。若某客户端依赖 ACP host terminal/write 才能完成合法产品操作，应判方案正向不可用并停止，不能临时加第二套宿主路径权限引擎。

#### 写序、失败切面与恢复

沿用 D06 本地提交与 D07 外部执行桥接，不新建跨进程事务。binding/profile/config 先由程序原子保存，后记 PREPARED 引用；派生临时文件失败不会产生批准。每个外部阶段分别有 intent，回执和执行事实进入既有 run/event 通道，不能只在 prompt 前记一次 intent。

| authoritative pre-state / 触发 | 顺序、post-state 与消费者 | 任一前/中/后失败及冷启动 |
|---|---|---|
| 当前合同/角色合格，无旧执行结果不明；TaskHandler 准备 | 路径/身份校验→保存 binding 与派生 config/baseline→run PREPARED；transport 消费相同 bytes | 写前/半写/引用失败零 agent 启动；没有 PREPARED 的残件不授资格。不完整配置按准备失败，I2 不改写成模型没 JSON |
| PREPARED，程序请求 bootstrap/ensure | 耐久 bootstrap intent→acpx 启动/adapter 最终配置核验→session 建立→程序回执耐久保存并核验；才可准备 prompt | intent 写失败不启动；其后失联/回执写失败不发 prompt，但不能倒记零外部活动。D07 按已发生阶段核验/裁定，不盲目再建 session |
| 已确认绑定且本次调用合格，普通 turn/correction | 新调用引用与相同/更窄有效策略核验→本次 intent→真实发送→native 各工具权限检查→原始终止/工具结果持久化 | 在副作用前拒不合格参数；之后超时/中断/事实写失败保存原阶段，结果不明不重发。原生/ACP 拒绝不得被“最终文本成功”冲掉 |
| 同版本恢复/queue claim，旧调用结果已明确 | 读取旧事实→D07 续作资格→session/options/config 重核→新回执/调用；已完成仅补本地提交 | pending 按 D06 处理；无绑定/版本漂移明确 INVALID；未知保持等待/裁定。旧高权限会话、跨 workstream、模式与额外根均不能借权 |
| 充分正常结果进入收尾/发布 | run owner 固定事实；只读 baseline、implement 源码 identity 与 S/D 各按原职责核验→领域 validator→D06 发布→D03 独立复评 | 发布间失败仅补本地写，不再调 provider。control 异常视为隔离/完整性失败，停止可信采纳；不能以 S 看不见或 D06 恰好匹配而继续。abort 不等于所有底层任务已停 |

实现时同一事实用既有 typed failure/run 机制承载，明确阶段、binding ref/SHA、失败检查及原始工具来源；RecoveryAssessment 统一解释，UI/CLI/report 不另算“已保护”。D06 owner 仍只负责一致性，native/adapter 负责模型副作用前限制。程序落盘失败与模型拒绝分开，不能汇总为一个成功/失败布尔值。

#### 成本、验证归属与本轮收口

预计改动沿五个现有落点：① Java TaskHandler/RunRequest/PermissionProfile/Transport/ClaudeExecutionPolicy 生产与消费绑定；② acpx CLI/types、session-options/connected-session、agent-command/client 传播及拒绝；③ Claude adapter 最终 options/配置核验；④ Codex adapter config/thread/turn/模式控制；⑤ 现有 patch/build 身份与 W03/W05/W08 回归。这是跨组件改动，不称改两行 deny 或近零成本。发布走仓库受审 patch/构建链，不直接修改 npm 缓存作为交付。不扩大 D06 协议，不迁移 control，不新建原生引擎或权限平台；如必须如此或合法编辑因此无法完成，按 §8.6 停止提交成本与选项。

需要判别的**新增差异**只有“可写工作区内的受保护子树”及其真实接线；不重复已通过的根外拒绝/一般子任务/取消切片。W03/W05/W08 原验收承接下表，不新增平行工作表；本轮均 NOT RUN。

| 成对验收与反证 | owner / 拒绝时点 / 证据层次 |
|---|---|
| 程序创建 run、写 node/gate/current 与产物可用；implement 正常创建/修改产品文件可进入后继；只读能完成必要读取 | Java/acpx/native 程序 writer 与工具各自观测；不能只证明模型被拒。产品合法性最终由原合同/S/产物消费者采纳 |
| 同一 native 可写根内 `.agent` 的已有/新文件、policy/manifest、兄弟 workstream；native edit 与命令/开放子任务均不得写。包含删除/重命名、链接/别名、写后还原 | N：native 工具副作用前；需真实拒绝依据与原始文件观测配对，最终无 diff 或自述 denied 不够。根外旧 fixture 不覆盖此差异。LOW-2：Codex 父在本 carve-out profile 下实际创建子任务，子写 `.agent` 被拒与产品内合法写成功须成对。Claude 保持既定 Agent 禁用，不要求为两客户端对称额外开放子任务 |
| 直接调用 ACP write/terminal 即使能力隐藏或 approve-all 也拒；合法 read 和程序 writer 仍可用 | N 的宿主边界：实际 handler 到 filesystem/spawn 前；离线分派/副作用 spy 可证宿主链，不冒充原生隔离 |
| Claude 有效配置丢失 deny、管理员来源不一致；Codex 丢 profile/逐 turn 恢复 sandboxPolicy；旧 session/跨 epoch/追加 root；移除守卫即反证失败 | adapter 在启动/每次调用前；合法同绑定下一 turn/重连是正向对照。独立注明参数消费证明与 native 能力证明 |
| binding/intent/回执/结果/发布各写点中断；可信结果只补本地、UNKNOWN 不自动重发；源码迟到写使旧证据失效 | D06/D07 与 S/D 的原回归，含 inline/worker、四 workflow 变体、correction/queue/resume。写前防护、业务 scope、证据失效分别判定 |

六维结论：**Declared by**=R3 I3、§8.23/本节；**Proven by**=已有两组切片加本节静态接口事实，缺内部子树/实际配置组合证明；**Consumed by**=TaskHandler→transport→acpx/session→两 adapter，领域消费者仍持 authority；**Enforced before**=拟议启动/逐调用/native 工具或 ACP 宿主副作用前，尚未实现；**Failure route**=上表/I2/D07，UNKNOWN 不重发；**Cannot-bypass test**=上表正反与删除守卫对照，尚未运行。作者 Boundary Review：DESIGN GAP，不称已闭环，也不把自查写成独立 PASS。

受影响复审需判断：这条现有机制是否足以冻结可实施规格；若仍缺能力证据，应点名能改变冻结决定的内部子树/配置差异，不重开已废弃的大范围实验。确需原生补证另按 Verification Gate 和预算授权执行，当前无新增预算；设计评审 PASS 也不自动等于能力 PROVEN 或产品实施授权。反思：之前误判来自将事后 S/D 或根外样例外推为产品内控制保护，本节将程序/模型 writer、实际路径和拒绝时点分别绑定，避免重复这种外推。

#### §8.24 的 CC 复审处置（2026-09-30）

用户带回《D04 控制目录保护接线候选（§8.24）· 复审》：设计层 PASS、2 LOW；认可既有接口接线方向，不改变引擎、存储布局或领域 authority。采纳该设计判断，LOW-1 已补工具规则覆盖的冻结前核对及实际分派反证，LOW-2 已明确 Codex carve-out 子任务正反配对；这表示要求已纳入，不表示核验或运行已完成。CC 本轮未逐行核验 TS deny 映射、Claude 最终 options 合并、Seatbelt 生成、SDK resolver 行为及文件级成本；不把作者证据扩写为其独立实证。

对“无其他缺口、一次 Gate 即可冻结”保留证据限定：当前收敛的是一个**内部控制目录保护的差分问题家族**，包括原生修改工具/命令、实际配置合并、开放子任务继承和程序合法 writer。不是仅跑 Codex 路径规则即可替代 Claude 配置有效性；也不按“两客户端×父子×所有入口”笛卡尔积扩大实验。已有共享强制点与固定禁用证据能复用则复用，产品 session/worker/发布整链接线仍归实施后原工作项，不能要求尚未实施的产品通过完整 E2E 来冻结。

下一工作是离线准备这个有界差分 Gate：先固定实际工具规则、配置/版本和能命中真实分派的受控输入，再列明确运行/副作用预算、PASS/FAIL/INVALID 与止损，交用户批准。优先复用本地受控响应与零真实模型路径，但不能将 Codex 桩可用推定成 Claude 同样可用；若无法在该边界提供有效触发，先报具体阻塞，不静默切真实模型或构建新注入平台。通过完整判据后才判断冻结；FAIL/INVALID/缺项不能由“已跑一次”抵消，也不自动追加预算。此处并未生成或批准 Gate，旧 Gate 授权已消费，不可沿用。

本次只更新设计与索引，零 native/provider/额外 agent、零产品实施；未重复已通过的能力切片。作者自查仅确认条款/状态同步，不冒充 CC 已审本次增量。无需为这两项要求的定点回写单独再开材料评审轮次；下一次有判别力的复审应消费差分证据及其适用范围，而非再审同一段承诺。现状仍为 DESIGN GAP，成本收益偏离/新机制按 §8.6 停止交用户。

#### 内部控制目录差分 Gate 已离线准备（2026-09-30）

用户授权离线准备后，已形成 [有界差分 Gate](../../output/d04-control-carveout-gate-20260930/gate.md)、两个实际客户端诊断场景与拒绝无授权启动的 runner。12 项离线检查通过（含 5 个 mock-only JS 首错停止场景），Python/worker 语法与身份已核验；零 native/SDK resolver/本地服务/真实模型运行，不把离线守卫通过当 native 能力证明。原源码及旧 Gate/证据未修改。

申请上限为 Claude 1 个顶层会话、Codex 1 个顶层会话及 1 个原生子任务；每场景至多 24 本地请求/120 秒执行，整组 48 请求/270 秒执行与清理；零真实 provider、零自动重跑。材料 manifest SHA-256：`2f2698cf7fe743d463450feff007346368b15284296888989c7cf41fb64b560f`。当前 **PREPARED / NOT AUTHORIZED / NOT RUN**，不生成授权文件。首次 FAIL/INVALID/残余不明即停止整组；只在临时 fixture 操作，不改真实 control/产品。用户批准后才能进入本 Gate，不沿用旧预算或自动取得实施授权。D04 仍 DESIGN GAP，结果足够后再交受影响范围 CC 判断冻结。

#### 内部控制目录差分 Gate 执行结果（2026-09-30，当前）

用户明确批准上述 manifest 后运行 Claude 场景，结果 **FAIL**，见 [完整证据与 CC 提示词](../../output/d04-control-carveout-gate-20260930/result.md)。合法读取/产品修改、程序 writer 控制写成功；直接控制写和符号链接写被拒，但预先存在于可写根的硬链接写成功，受保护文件同 inode 内容改变。按首错止损停整组，Codex 未运行；实际 1 会话/13 本地请求/0 子任务/0 真实模型调用，无重跑、产品修改或历史恢复。

此反例针对准备阶段已存在的别名，不证明模型能运行期创建别名，也不证明必须迁移存储。§8.24 路径保护候选不能按当前支持布局冻结；可信准备拒绝异常布局若作为后续候选，还须有整个执行期维持条件的依据，不能单靠开跑 nlink 检查或删掉负例。任何降低保护、增加隔离机制或成本收益偏差按 §8.6 交用户取舍，不借剩余预算临场改方案。D04 保留 DESIGN GAP，前轮已证能力与其他九项冻结不变；结果自查不冒充独立评审。

#### 创建链接与实际角色配置的案头核验（2026-09-30，当前）

用户同意先核对两项机制前提后，形成 [案头报告及简洁 CC 提示词](../../output/d04-link-creation-desk-audit-20260930/assessment.md)。采纳上轮 CC 对材料/FAIL/止损的确认；不采纳按角色名一概划分可维持/不可能的结论：Claude 已测纯读组合仅 Glob/Grep/Read，而 Codex 已测只读组合明确开放受限执行，本文只读候选也保留必要 scratch。源码显示与本机内核标识对应的 Apple link/linkat 入口在副作用前把源 vnode 和目标目录交给 MAC 检查；本机系统 profile 显式存在 file-link 条款。固定 Claude 嵌入策略及 Codex 策略生成片段也已核对，但没有得到私有操作映射/本次最终有效 SBPL 的完整证明。

因此，“同用户同设备＋源可读/目标可写即必然能建链”未成立；相反的“有检查点所以当前配置一定拒绝”同样不成立。运行期建链仍 UNKNOWN，预存别名写 FAIL 不变。准备期布局检查只描述当时对象，还须区分整个执行期、程序新建/替换及恢复；不能借 nlink 扫描降验收，也不能将实验监测升级为产品完整性保证。现有方向尚值得做有判别力的建链差分，但本轮仅完成案头核验，不新增 Gate/预算、探针或实现；公开源码下载不完整处与身份核验上限已明示，不继续无定向排查。D04 未冻结，不先让用户在 A/B/C/D 中作基于错误前提的取舍。该增量为作者核验，独立复审不预填结论。

### 8.25 用户决定：收敛为原生权限复用与审批接入（2026-09-30，当前规范）

用户确认“收敛 D04 的目标很有必要，否则 Dual 就扩大成了安全沙箱，太复杂、ROI 太低”。据此调整本阶段产品保证；本节优先于 §8.1–§8.24 的旧冻结条件，不重开其他九项，也不把目标收敛当实施授权。

**目标：** 在受信客户端和本机研发环境中，复用 Claude Code/Codex 的原生工具权限、审批和已有沙箱，正确配置角色与任务范围；需要额外批准时有明确出口，拒绝不会被绕过重试，批准后合法续作，状态不明不盲重发。保留 Claude Code 作者／Codex 评审分工及模型品牌无关原则。原生客户端自身的实现安全性作为依赖边界，Dual 不承诺成为可隔离恶意任意命令的独立安全产品。

#### 保留与撤销

| 保留的产品责任 | 撤销的本阶段额外责任 |
|---|---|
| dispatch 前有效业务批准、角色/配置匹配；不静默 approve-all、提权或改全局设置；客户端已有明确禁止项继续生效 | Dual 自建命令安全分类器/LLM 权限裁判、通用 policy DSL、内核规则或沙箱实现 |
| 原生工具请求/决定/结果身份可关联；人决定的批准范围和有效期明确；必要权限增加必须审批 | 证明每条系统调用、硬链接/符号链接/写后还原及任意原生子树均不能绕过底层限制 |
| 已批准范围内的合法读取、实现和中间修订有界推进；拒绝、取消、断线、未知结果的恢复明确 | 把完整 OS 安全证明、控制目录绝对防篡改、全目录监控或存储迁移列作 D04 冻结条件 |
| S：实际变更与 EXECUTION scope 对照；D：源码/测试/验收/交付证据身份；独立复评与最终交付批准 | 用“原生工具执行获准”替代任务 scope/复评/验收；也不新增每条命令的 Dual 平行批准制度 |

旧硬链接 Gate **FAIL 仍为 FAIL**；新目标没有修复它，也不改写旧评审或证据。收敛意味着不再承诺该对抗边界，停止追加运行期建链 Gate、内核/原生机制调查及其材料轮次。控制目录依然不是 agent 可自行改写的授权对象；使用既有配置表达限制，发现控制事实不可信时停止采纳。但不能声称现有校验能发现全部篡改、不能将事后完整性校验包装成替代写前保护的新方案。已知限制要如实呈现在支持环境/权限配置说明中；没有授权任意部署或默认全权限运行。

#### 责任与两种批准

- **业务授权**仍由合同、人工门、qualification 与交付 owner 持有：GOAL 不授权实现，EXECUTION 批准精确业务 scope，最终批准绑定具体交付 candidate。原生 approve 不扩大这些批准，模型正文/内部子任务不产生 authority。
- **工具执行许可**由原生客户端机制决定 allow/ask/deny。Dual/acpx 只配置既有选项、转交真实请求与人的决定、记录执行事实，不再次让 LLM 推断命令是否安全。并非所有原生模式都“列表外询问”；必须按固定版本及有效模式核对。
- **接入事实**由 transport/adapter 产生，沿现有 Execution/Run、D02 失败保真、D06 写序与 D07 恢复通道持久化；UI/CLI/report 只消费统一投影。不得新增第二授权账本。当前 run/session/调用与请求内容须能对应，旧/错身份或已终止请求的批准不可借给新执行。

现状锚点：`AcpxAgentTransport.addPermissionArguments` 的常规路径传 `--non-interactive-permissions fail`，并另有 approveAll 分支。因此不能假定直接运行客户端时的交互批准已经接通 Dual。这里仅确认参数事实，尚未完成两 adapter 请求/返回路径核对，也未决定全部情况采用在线等待或安全停后续作；不得把选一个参数当整链完成。

#### 完整控制链与失败处置

| 前态 / 触发 | owner、写序与下一消费者 | 失败/恢复与禁止路径 |
|---|---|---|
| 当前业务批准、角色、输入合格，准备启动 | qualification→adapter 配置/调用身份→既有 run intent（按 D06）→启动；客户端消费原生配置 | 准备/持久化失败零启动；身份不明或配置会静默放权则拒绝。不得因缺回执改报无 JSON |
| 原生报告需要批准，且调用确在等待 | adapter 保存可对应当前调用的原始请求与受限投影→既有人工决定入口；没有决定不答 allow | 记录失败、断线/超时/取消不得伪造等待仍可回复；先核对外部状态，不能自动重发整段任务 |
| 用户批准当前具体请求 | 核对有效请求与业务范围→先持久化决定→向原生入口回传一次→保存回执/执行结果→正常原流程 | 批准记录成功但回复/结果是否送达不明按 D07，不把批准等同已执行；范围变化先走合同批准，不借工具许可扩大 |
| 用户拒绝、原生明确禁止或批准过期 | 保存原始拒绝/来源与已知执行情况→D02/既有恢复投影；仅明确仍合法的替代路径可有界继续 | 不重试同一被拒操作、不换工具/模式借权、不把其他工具已发生的副作用说成零执行；LLM 解释不覆盖拒绝事实 |
| 非交互入口无法等待人工，当前调用已拒绝或结束 | 保存真实失败与请求信息；用户决定后按现有恢复重新准备，前提是旧执行结果明确且新批准有效 | 不向已结束 request 假回传；不默认重跑整任务；外部结果未知人工裁定。若现有接口无法实现合法续作，明确接线缺口而不是新建权限平台 |
| 正常结束，准备发布/后继 | 原生执行事实→正常阶段结果校验；只读阶段变更核验、implement scope 与源码身份→产物发布/路由→复评/验收 | 原生执行许可不授业务成功。发布/路由部分写失败沿 D06，旧证据不绑定新源码；控制事实不可信时不得继续 |

本表是结果义务与既有 owner 分配，不是预选新的 schema/状态机。最终采用哪种原生请求通道与既有恢复动作，必须对真实入口核对后确定；不要求两客户端接口形式相同。需要新 authority、通用代理审批服务或显著成本增加时按 §8.6 停止，不能以“桥接”名义扩大目标。

#### 冻结条件与验证范围

D04 仍为 **DESIGN GAP（目标已收敛，原生审批接线待冻结）**。剩余工作并回 W03/W05/W08，不增加平行清单：

1. 核对固定 Claude/Codex＋acpx 的配置、请求和响应入口；明确哪些已批准操作自动执行、哪些原生拒绝、哪些需人决定，以及各自可用的在线或停后续作路径。点名核对原生等待的超时/取消机制、有效期由谁定义、慢速人工响应后请求是否仍可回复，以及迟到决定的处置；不得假设原生请求无限等待。已有失败/批准/run 模型足够则复用，不预增新模型层。
2. 将上表落到现有消费者与记录：有效决定不串 run/session/请求、批准前不擅自放行、拒绝后不绕过、回传/结果不明不盲重试。显式核对多请求并发/排队时的逐项关联与呈现、部分批准/部分拒绝及各请求终止状态；不得把一项决定套用整批，也不预设原生支持并发，不支持的入口须明确其拒绝或串行语义。缺少合法续作出口须如实列出。CLI 的交互演示不能替代 Dual 接线，但也不要求冻结前完成尚未授权的产品实现。
3. 验证义务只围绕合法流程与真实接入边界：自动允许的正常流程；确实需要批准→批准→续作；拒绝→停止/合法替代；过期/重复/跨调用决定、多请求混合决定与原生超时后的迟到回复；决定写入与回传之间失败、冷启动及实际开放的 inline/worker/correction/session sibling。业务 scope 与证据回归保留在原工作项。协议映射可优先离线验证；有真实接口能力未知才提出有界验证及预算，不默认真实模型或新 native Gate。

六维口径：Declared=本节与 R3 I3；Proven=原始权限请求/决定/回执及合法序列（当前待证）；Consumed=原生客户端＋现有执行/恢复消费者；Enforced before=批准回复/相应 dispatch 前身份与决定校验，不宣称每个底层写入均不可绕过；Failure route=D02/D06/D07；Cannot-bypass=错批准/缺批准不得从其他 Dual 入口放行。独立评审应核对收敛后的职责和必要接线，不重新要求已撤销的底层安全证明。设计认可仍不授权实施或真实 E2E。

**本轮 CC 处置：** 用户带回 §8.25 设计层 PASS。LOW-1（多请求关联/呈现）与 LOW-2（原生等待超时）已分别并入冻结条件 2/1 及条件 3 的验证义务，无新增工作编号或独立材料轮次。两项增量仅作者自查；CC 未核对实际 adapter 请求/返回接口、回调可用性，也未复读 R3 I3 与 N/S/D 同步文本。因此此次 PASS 只确认收敛设计，不能据此声称桥接可行、能力已证或 D04 已冻结。


### 8.26 原生权限接线核对与受影响候选（2026-09-30，材料层 PASS；协议待冻结）

用户授权完成 §8.25 的接口、消费者映射、合法续作/失败恢复验证设计。材料合并在[接线核对报告](../../output/d04-permission-wiring-audit-20260930/assessment.md)，同目录提供固定源码身份与行号片段。本轮仅离线读取/文档，零 native/provider、注入探针、额外 agent 或产品改动。

核实的现状：两 adapter 有 ACP 权限请求/回复，acpx 有宿主 callback；但 Dual 的 CLI/queue 尚无通用在线回复通道。callback 缺逐 RPC 身份/取消传递，kind 回复可能扩大许可范围；Dual 现有投影又将部分 Codex optionId 误分类。原生权限存在不等于接线已经存在。现有 WebSearch 批准是 FAILED 后的专用重试，不可充当通用同请求续作。完整映射和受影响范围见报告 §3–§4。

推荐候选是扩充**既有受审 acpx patch 的 callback 与 queue IPC**，保留现有 transport/session 架构；精确 optionId、当前请求身份、决定落盘后回复、逐请求取消/多请求关联与未知窗口归既有 run/D02/D06/D07 owner。不是纯配置，也不选择 Node runtime 替换、TTY 自动输入、新服务或第二授权账本。D06 已冻结的短写入会话与 run 单 writer 是前置；当前长持 workflow 锁不能原样接在线审批。W03/W05/W08 必须合并检查，不拆开声称完成。

报告 §5–§7 列出候选接线语义、全 transition/写入失败切面及成对验证设计。显式禁止 callback 故障退回允许、单次批准扩大到会话/永久、旧请求批准借给新 attempt，以及把 pipe/IPC 发送成功等同执行完成。业务 scope、证据失效、独立复评及最终交付批准不变。

**当前冻结表的具体进展：** §8.25 三项案头核对材料已产出；原生运行与产品序列未执行。待一次受影响独立评审确认推荐通道、精确回复/逐请求取消增量、跨 owner 决定与发送竞态的冻结规格及成本是否足够明确；不能由作者自查把它标成 DESIGN_FROZEN。如仍需新增服务/authority、替换调用架构或显著成本增加，停交用户取舍，不边实施边决定。旧 FAIL 不改，不恢复底层安全实验；实施仍未授权。



#### 接线候选 CC 评审处置（2026-09-30）

用户带回材料层 PASS，确认已抽验的接口事实及候选增量必要性；未授权实施/运行。原报告与源码索引保持不变，本段为后续处置。当前已发现的冻结阻塞仍为跨 owner 资格变化与批准回复的确定提交序列。CC 未核验范围保持原报告所述，不能把“仅此一项”外推为不存在其他未发现缺口。

- **LOW-1，消息与事件关系：** 新 permission reply 是提交决定的命令，不是事实来源；经当前请求 owner 核验和持久化后的 run 事实才可供控制消费者使用。桥接模式下，待决请求/人的决定/发送观测统一归该 run 记录；既有 `permission_escalation` 仅保留为旧非交互拒绝/升级的历史诊断，不再据它产生另一个可回复请求或第二批准入口。同一原生请求不得同时进入在线批准与旧失败后专用重试。旧 WebSearch 历史记录可读，但不迁移成新请求，也不由此重开网络工作。实现切换时由固定入口/版本选定一条消费路径，不能双路 fallback。acpx 该事件本身支持一般 policy escalation，不限 WebSearch；WebSearch 限定在 Dual 当前的专用消费者，不能混同。
- **LOW-2，现存误分类与同源修复：** Codex optionId 被错误投影为 CANCELLED 是现存缺陷，登记进 W03/W08 的既有接线范围；按原请求的精确 optionId→kind/许可范围映射修复，未知映射不猜测。不另加字符串分支，不额外建立映射来源。默认随接线统一实现和序列回归；CC 关于“可独立排期”的建议不是当前实施授权，本轮不抢先修复。
- **对提交序列建议的保留：** R 内串行化＋发送前读 L＋失配后 UNKNOWN，尚不足以排除 `L核验有效→L撤销/改epoch提交→R发送allow`。R 的串行并不串行 L 的变更，事后 UNKNOWN 只解释结果不明，不能把本应禁止的新发送变合法。冻结协议必须确定决定先后的具体提交点、与资格变化共用的排序机制、崩溃前缀和重复消费规则；还要区分“该点之前已失效而不得提交”与“有效提交后发生取消、外部效果不明”。不能暗改为即时撤回所有已发许可，也不能仅靠重复读取满足原要求。此处只列冻结义务，未擅自选择新锁、租约或授权机制。

两项 LOW 已在设计层吸收，补记仅作者自查，不另开 LOW 材料轮次。将上述撤销抢先于回复的交错加入已有 §7 资格竞态反证；确定协议后再合并做受影响复审。在此之前 D04 不冻结、不实施、不申请原生实验预算。


### 8.27 批准回复与资格撤销的确定提交协议（2026-09-30，DESIGN_FROZEN）

用户授权进入下一步，完成[具体协议](../../output/d04-permission-wiring-audit-20260930/reply-commit-protocol.md)。采用既有 D06 workstream 写会话：L 在同一锁内核验业务资格并将一次精确回复提交 C 写入既有 execution 文档；撤销/epoch 或 claim 失效以同一锁提交关闭 X。R 仍持请求、人的决定及外部观测，只引用 C。两锁不嵌套、不跨人工等待或原生调用持锁，不增加服务、租约或独立授权账本。

**候选行为须显式评审：** X 先于 C，零新许可提交；C 先于 X，该次回复已经在途，即便尚未物理发送。X 关闭后续提交并请求取消，但不保证追回先前 C。此语义不同于“点击撤销必然抢占尚未送出的批准”，若产品要求后者，本候选不满足，须停止交用户取舍。不能用 UNKNOWN 将未提交就失效的请求变合法；UNKNOWN 只解释合法提交之后的外部窗口。取消/拒绝清理不受关闭新许可的 guard 阻止，也不得复活已终止请求。

协议列明 execution 的具名提交/关闭事实、不可变请求/决定引用、D06 写集、原 live 调用唯一发送资格、acpx 当前回调身份与消费标记、IPC 不补发及恢复只补本地。全部资格失效兄弟入口须消费同一 guard，完成 writer 不得覆盖提交历史。该 owner 细化取代 §8.26 中“回复控制事实全归 run”的未决表述，其他普通执行 dispatch intent 不变；原受审材料保持历史，不能据其 PASS 宣称本增量已审。

W03/W05/W08 沿原工作项承接，N1–N3 合并新增 C/X 交错、每个写入前缀、冷启动、重复回调和合法拒绝/续作反证。D06/D07 仅上述受影响职责与写序随本次复审，其余冻结项不重开。**D04 仍 DESIGN GAP**：本轮完成协议候选和作者自查，尚无独立冻结结论；零产品实现、native/provider 或额外 agent，无运行预算申请。成本收益偏离或新增 authority/机制仍按 §8.6 停止取舍。

#### 提交协议 CC 处置（2026-09-30）

用户带回设计层 PASS。受影响 D06/D07 条款已并入 §6.5/§7.2；LOW-2 经固定源码核对，底层 `maxAttempts` 可传 1、请求 helper 单次写入，新增命令仍须透传参数并禁止上层根据 retryable 自动重送，详见[处置与证据](../../output/d04-permission-wiring-audit-20260930/reply-commit-cc-disposition.md)。新命令未实现、运行未证，不另开材料轮次。

**剩余冻结条件是用户显式确认：C 先提交的单请求、单范围、期限内一次回复尝试可能在 X 后送达；撤销阻止新提交并请求取消，不保证追回在途回复。** 本轮评审已履行协议设计审查，原段落中“待独立复审”是此前状态，由本段更新；用户确认不自动授权实施。D04 及此次 D06/D07 增量在确认前不标 DESIGN_FROZEN。不能采纳“deny 任何时候都安全”这一过度概括：错身份或已终止请求仍不得回复。

#### 用户确认与冻结生效（2026-09-30）

在解释“批准提交后即使尚未物理送出，撤销也不保证追回”的具体行为、并明确只确认设计不授权实施后，用户答复：“我肯定接受，这个建议合理”。此确认覆盖单请求、精确范围、期限内的一次回复尝试；X 阻止新提交、取消与停止核验、UNKNOWN 不补发、不与未停旧执行启动替代 attempt 的护栏不变。

本轮 CC 设计 PASS、§6.5/§7.2 受影响条款并入及用户语义确认三项已齐，**D04 与对应 D06/D07 增量登记 DESIGN_FROZEN**。本段覆盖此前“待用户确认/未冻结”的进展状态，不改历史证据。设计侧无已登记的剩余冻结阻塞；IPC 参数透传、回调/队列接线与全部合法/拒绝/失败恢复回归仍是原 W03/W05/W08 的实施验收义务，不称已 ENFORCED/PROVEN。无需为本次确认另开 CC 材料轮次；产品实施、合入和新真实运行仍未授权。

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

### 9.1 B06 具体接线候选（2026-10-09，未冻结）

用户授权在开工核对后补齐接线材料、合并做受影响范围复审；本补记不是实施授权。完整候选见 [交付裁定协议](../../output/lifecycle-batch-06-20261009/wiring-candidate/protocol.md)，结合上级 preflight 的 G01–G07/V01–V07；字段例子、source anchors、条件性文件范围与作者自查在同目录。原 §9/冻结附录的结果义务仍有效，新入口和停止依据 **DESIGN_CANDIDATE / NOT IMPLEMENTED / AWAITING_CC**，不得提前称为已冻结或能力已证。

正常路径仍是获批 candidate→merge→人实际 commit→finalize；异常只增加原 delivery owner 的 resolve：确认旧操作停止且精确 base 时，明确授权一次新 apply；或单向关闭该 candidate 的后续 apply，保留现场和历史。已应用/已提交只走原入口核验确认，不重 apply、不自动 commit、不重开终态。原 target→L、L07 写集、同版固定字节续完复用，无 outbox/新账本/监督服务。

停止依据分开：程序观察准确已登记 Git 主进程结束，实际退出码仅来自 waitFor；操作员确认停止始终保留人工声明类型，不回填机器退出或抹去 UNKNOWN。已知原调用仍活动不得由声明覆盖。人工确认在原 D09 人工裁定边界内是否适用，明确交本次 CC 判断；不能因原语义已冻结而宣称此具体信任选择已经获批。父 JVM/PID 消失、目标恰是 base、只取得锁均不独立证明全停。

候选将裁定+旧操作快照+新 intent 放同一次 L07；只有本次完整 commit 返回的 live 调用可启动一次，重复决定/冷启动/本地补齐零发送。实际 Git 启动后、patch 输入前固定准确 process 身份；各前缀未知保留。view 统一核当前目标与历史状态，CLI status/summary、API status/overview 的既有 L 内调用同时迁移，避免 L→target 锁反转；投影不生产资格。全部具体新能力 NOT RUN，成本偏离或需要新增 authority/平台时停止。

下一步：与其他目标/身份/投影接线一次合并 CC 设计复审；通过且获实施授权后才开始 B06 完整工作包、离线回归和批末独立判断。总体 W 项、真实 NO-GO、提交权限保持。

### 9.2 B06 接线冻结补记（2026-10-09）

§9.1 的候选已获用户带回的 CC 合并设计层 PASS、可冻结；评审/作者处置见 [接收记录](../../output/lifecycle-batch-06-20261009/review-acceptance-20261009/disposition.md)。用户随后明确授权 B06 实施和离线验证；当前具体接线 **DESIGN_FROZEN / B06 CLOSED / PASS（离线范围）**，用户带回批末独立 PASS 的接收和 LOW 处置见 [批末接收](../../output/lifecycle-batch-06-20261009/batch-review-acceptance-20261009/disposition.md)。冻结协议与补记共同约束 W06，原候选文字保留其评审前时间点，不再据其 AWAITING_CC 阻塞。实现/完整证据分层见 [B06 batch](../../output/lifecycle-batch-06-20261009/implementation/batch.md)，主清单 §22.3 保留交评凭证，§22.4 为当前收口索引。

两 LOW 已落实：精确 commit 本身不证明旧 apply 已停止；结束证据齐备者正常 finalize，缺依据者先 resolve，人确认仍为人工类型，已知活跃不得覆盖。规范 target/request 字节的等价/差异/错误忽略字段反证已进入常驻回归，非仅样例核验。未新增批准种类/平台，无需单独再审这两处补记。

冻结本身仍不是运行证明；本批实际消费者、callback 输入前持久化、拒绝/续作、写点与冷启动已由分层离线证据支撑，证明范围见 batch。单轮 clean 1752/206/0，前后 654 项源码输入一致；一次性查询/启动窗口/具体写目标补证、Node VM 检查不冒充常驻回归或真实 native/provider/浏览器能力。作者自查不是独立评审，CC 设计结论也不外推为今后不可能再有缺口；原成本止损和新 authority 升级规则继续适用。

批末 LOW-1 查询反向锁序尚属一次性补证，已登记下次 W08 离线准备或相关查询改动时常驻化，不阻本批收口。下一步建议为剩余主清单和 W08 准入前提离线对账及 Gate 草案；W07、必需验证出口与真实能力分别核对，不能从 B01–B06 PASS 推导全部完成。真实 NO-GO 与提交权限保持，本次不自动启动下一批或真实 Gate。

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

2026-10-09 W08 对账补记：[逐字段案头表](../../output/w08-offline-gate-20261009/w07-field-audit.md) 已形成，单一schema/当前typed引用/受限correction与真实负担未证分别登记。确认 design-claude 精确scope复制指令与GOAL文件提案时序冲突 G01，需要在现有 prompt/template owner 内纠正并扫同族；本轮未改生产模板、不以材料形成宣布减负完成。整体 Gate 与下一步见主清单 §23，真实 NO-GO 保持。

2026-10-10 W08 准备补记：用户授权后，G01 在既有 prompt/template owner 完成前向纠正和同族 sweep，GOAL 的 canonical 文件提案不再被当作已批准实现范围，EXECUTION 精确路径/操作要求保留；不改 wire schema、资格、scope 或批准权威。实际 qualified 渲染先红后绿，预算误超以精简重复文案修复而非放宽预算。必要离线单轮验证、新发行、实际 acpx 元数据、逐条验收出口与有界 Gate 候选见 [本轮材料](../../output/w08-preparation-20261010/assessment.md) 及主清单 §23.1。当前待 CC 合并受影响复审和 G05 用户当次授权；不宣称真实模型负担、token 节省或 native 行为已证，真实 NO-GO 保持。

2026-10-10 W08 独立复审接收：G01 提示修正与 G02–G04 材料获用户带回 CC PASS，准备批次按主清单 §23.2 离线收口；原正文/交评材料的待审状态为当时快照。真实 NO-GO 保持，G05 及真实运行授权未取得。接收核验和审批提案见 [记录](../../output/w08-preparation-review-acceptance-20261010/disposition.md)，不扩大设计或验收。


### 10.1 W09 身份减负与初次 GOAL 前目标级返修候选（2026-10-14）

第五例真实一字符SHA转录失败后，用户授权**只补接线设计与验证材料，合并CC复审；暂不编码或启动第六例**。入口为[合并设计](../../output/w09-revision-wiring-design-20261014/assessment.md)、[验证义务](../../output/w09-revision-wiring-design-20261014/verification-plan.md)、[合成字段样例](../../output/w09-revision-wiring-design-20261014/examples.json)。状态DESIGN_CANDIDATE / AWAITING_CC；不是新的冻结、实施或运行批准。

候选修订D10中base字段分工：模型保留当前base proposal ID、移除patch的SHA回显义务；完整reference/SHA由本次R01固定并在dispatch/publisher/reference/compile强制重核。ID不是完整内容身份，不能从完成时latest context补盖。现有wire字段集合需局部调整，旧SHA键不静默忽略，原始模型输出不改写；schema/workstream/run/node、义务回应、review subject与批准引用仍保持各自严格规则。

候选具体化D03目标变更出口：普通条款/范围增补仍用有限patch；初次GOAL形成前、精确review/human revise上下文中objective或excluded实变，允许带base ID的既有full proposal，程序固定goal_reproposal派生，保留条款ADD/REPLACE、义务覆盖与领域校验，再走独立复评→当前需求人门→新GOAL compile（included=[]）。不是直接采纳模型的批准。历史已compile/授EXECUTION、late/scope-rework不由本候选放开；若需重新定义已批准目标，停止旧流程、交用户经现有start新workstream和新批准，不宣称原实例在线迁移已闭合。

R01输入与同基准绑定、唯一派生消费、原R03/L03、复评/人批/compile、每写点/冷启动/旧runtime与四变体兄弟的完整transition见合并设计§3–§5。局部输入字段与派生kind复用现有owner，没有新增artifact type、图节点/边、gate action、锁、服务、通用字段DSL。若实现发现需跨GOAL自动迁移、独立基准平台或广泛重构，按原ROI止损交用户。

§10“先不改wire”的原结论保留为当时冻结基线；新真实代价支持本次有界候选，**未经CC与实施授权不改写原冻结效力**。新能力全部NOT_IMPLEMENTED/NOT_RUN，不借旧1790绿灯或材料JSON检查称ENFORCED/PROVEN；不关闭D10/W07/W08/W09，不恢复第五例、沿用旧余额或修改旧发行。下一步仅一次合并受影响CC设计复审，必要处置后再交用户批准实施。

### 10.2 W09 合并CC设计复审接收与定稿（2026-10-14）

用户带回CC设计层PASS：§10.1及[原受审接线方案](../../output/w09-revision-wiring-design-20261014/assessment.md)的身份分工、初次GOAL前目标级出口和首版范围/ROI获认可，受影响D10/D03接线定稿。§10.1/原包的AWAITING_CC保留为提交时历史状态；当前DESIGN_FINALIZED / AWAITING_IMPLEMENTATION_AUTHORIZATION / SIXTH_NOT_RUN，详见[接收与LOW实施清单](../../output/w09-revision-design-review-acceptance-20261014/cc-review-receipt.md)。不是已实施/ENFORCED/PROVEN，不授新运行。

LOW-1单列：resolveFromRefs当前仍只比较proposalId；完整reference唯一性守卫及相同reference合法/同ID异fullref拒绝的成对、顺序互换与移除反证，作为独立修复保留。LOW-2：实施时在ProposalDerivation处注释patch_source_output_ref表示本轮authored revision原始输出，goal_reproposal同用；不为字段名迁移历史。

“更强/四处核验”是设计消费义务判断，不是已测安全能力；“修复resolver”本轮只识别/定方案，未改代码。目标级表达限制有源码证据，未证明第五例的第二次复评拒绝。原包15/第五例326/A包398与656输入重核一致，零产品测试/native/provider/新agent；旧发行与第五例不变。下一步是用户批准有界实施，完成后回归/重冻/受影响评审；fresh第六例仍需另行运行授权，无须为本接收单开评审。

### 10.3 W09 两项接线有界实施与发行候选（2026-10-14）

用户明确批准有界实施、两项 LOW、回归与重冻，并暂不启动第六例。当前 IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SIXTH_NOT_RUN，入口[本轮 assessment](../../output/w09-revision-forward-fix-20261014/assessment.md)。§10.1/§10.2 原状态与“未改代码”保留为提交时历史，不再代表当前实现。

模型只引用精确 base ID，完整 ref/SHA、义务及允许形态由同一次 capture 固定至既有 R01；dispatch、publisher/merger、reference/lineage/compile 强制消费。首次 GOAL 前可信返修可用已有 full proposal：真实目标/禁区变化、bounded delta、当前义务、新复评/当前人门不省略；已授权/late/pending 人批不借权，不新增跨 GOAL 在线重写。LOW-1 完整 reference 去歧义及成对/移除反证落码；LOW-2 历史字段语义注释落码，不迁移历史。

唯一单轮 clean 1805 项 / 210 suites / 0 failure / 0 error / 0 skipped，8 模块、659 输入运行前后/打包后相等；两形态/双入口关键 R01/L03 写前缀、真实冷 JVM、四变体合法链与拒绝对照分列，未穷举所有旧故障点 × 新形态。CLI/server 冻结副本/内嵌模块/14 资源/6 只读命令核验完成，见[发行身份](../../output/w09-revision-forward-fix-20261014/release-identity.json)。这证明离线接线与发行字节，不证真实客户端遵守新契约、完整后半链或 token 节省；未外推整体 W 项完成。

设计/第五例/A 提交材料逐条不变，不加盖旧 raw、不救第五例；原 target JAR 是授权重建目录，不声称旧 JAR 保持字节。本轮新冻结副本有独立 SHA。下一步合并受影响 CC 复审；第六例当次 Gate 与真实运行另获用户授权。

### 10.4 两项接线实施 CC 认可与第六例待授权（2026-10-15）

用户带回合并复审认可离线实施及发行，无 WARN/FAIL，两 LOW 兑现；接收与未核验范围见[记录](../../output/w09-revision-review-acceptance-20261015/cc-review-receipt.md)。§10.3 与原实施包维持提交时字节，当前状态由本节与主清单 §23.24 更新，不回写历史为已获评审。

作者只读复核659源码输入、1929提交材料和两份发行身份相等；不重演测试或将作者核验冒充独立评审。受影响 D03/D10 接线为 CC_ACCEPTED / OFFLINE_PROVEN；真实模型行为、完整后半链及token收益未证，不关闭整体W项。

[第六例具体Gate](../../output/w09-revision-review-acceptance-20261015/fresh-sixth-gate.md)沿用完整主流程/储备/验收，提议170万token/18请求/60min；只换新发行、659输入和新样本，无新机制/配置实验。下一步由用户批准当次Gate/预算/隔离准备/真实运行，获准后核齐当前装配/出口/observer才开始。当前第六例未创建/未运行，旧例不恢复，三道人门及用户commit分别办理，无代码提交授权。

### 10.5 W09 第六例 F1/F2 有界前向纠偏（2026-10-15）

第六例 CC 确认两处真实错配；用户批准本批实施、回归、旧材料只读对照、重冻及合并复审，不批准恢复第六例或启动第七例。[边界/transition](../../output/w09-sixth-forward-fix-20261015/implementation-boundaries.md)与[唯一消费者](../../output/w09-sixth-forward-fix-20261015/consumer-and-transition-map.md)。

Definition 封闭结构不变：允许 full 的返修分支投影 RevisionResponse 两字段枚举；parser 继续拒未知键并保留 UNKNOWN_FIELD(fieldName,allowedFields)，acquirer 在原唯一一次 deny-all 修正传递具体诊断。没有宽松 parser/代修 raw/新增节点。mixed stdout 中不可解析正文碎片、普通{id,text}条款不算 RPC；成功解析的 RPC 形状结构坏则 INVALID_CURRENT_TRANSCRIPT，错身份/重复/部分量继续拒，只有无当前完成才保留既有严格共享出口。原严格 session NDJSON 入口与权限/工具投影不迁移。

只读重放只证明新诊断/归因面对旧字节成立，不证明新模型会服从提示；旧第六例FAIL及固定UNAVAILABLE保持。R01基准、R03/L03、D06/D07写序和批准权威不改；真实full发布/谱系/新复评/GOAL及后半主链仍未证。未来observer候选同型修复，不创造生产authority。最终单轮clean 1813/210/0失败/0错误/0跳过、659源码输入前后/打包后相等，发行候选已冻结；完整身份以[本包结果](../../output/w09-sixth-forward-fix-20261015/assessment.md)为准，独立CC尚待。无新schema/锁/服务/纠错平台，成本扩围即按用户止损。

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
| I3 / R3+§2/§8.25 | 四定义、qualification/authored 与原生权限接入序列；阶段变更核验 | qualification→handler/transport→原生许可与执行事实→产物消费者 | 业务 dispatch 与工具批准回传前；scope/证据在采纳前 | 缺/错批准、原生拒绝及外部未知走各自出口；新批准合法续作 | GOAL 不冒充 EXECUTION；错身份/过期许可不可放行，拒绝不绕过，合法批准可续作；不要求底层写后还原安全证明 |
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

§15 原附录与 §15.1 规范性补记构成当前设计基线。CC 原冻结评审确认九项可冻结；D04 后续提交协议获 CC 设计 PASS，用户已确认在途语义，§6.5/§7.2/§8.27 一并构成当前冻结基线。DESIGN_FROZEN 只表示方案及验收义务固定，不等于实现已验证或用户已批准开工。

| 决策 | 冻结依据与采纳 | 当前状态及实施后义务 |
|---|---|---|
| D01 | 附录 §2 + precondition-map；首版覆盖增量见 §4.3 / 主清单 §21 | 原语义 DESIGN_FROZEN；单合同资格族 C 已获独立设计认可，LOW-1 守卫已纳入；本族实施/证明仍在进行，不能关闭完整 D01 |
| D02 | 附录 §3 + 补记 LOW-2：精确 signal 关联、EXECUTION 可审计声明、不可降级清单 | DESIGN_FROZEN；typed 字段及两条合法通道、拒绝序列、E02 常驻回归仍待实施 |
| D03 | 附录 §4 + examples；首 GOAL 前目标级返修 §10.1–§10.4：现有 full 形态、固定基准、当前复评/人门 | 原设计冻结；受影响新接线离线实施及发行候选获 CC 认可（§10.4）；不新增 late/跨 GOAL 在线目标重写，真实主链未证 |
| D04 | **当前规范 §8.25/§8.27**；接口材料 §8.26、提交协议及 CC 处置；§8.1–§8.24 为历史 | **DESIGN_FROZEN（2026-09-30）**：CC 设计 PASS，用户显式接受 C 后在途语义，D06/D07 增量并入 §6.5/§7.2。IPC 单次交付入口已静态核对，接线及合法续作/拒绝/失败恢复仍待实施验证。业务 scope/证据/最终批准不变，W03/W05/W08 承接；无产品实施或新运行授权 |
| D05 | 附录 §5：真实生产者与增量、机器/人工出口样本分列 | DESIGN_FROZEN；实现后验证生产者→消费者，不将合成样例/人工声明当机器证明 |
| D06 | 附录 §6 + 142 文件入口对账：写集/会话/锁/原子写/失败前缀 | DESIGN_FROZEN（含 §6.5 受影响增量）；CC 设计 PASS 与用户语义确认已齐；本机存储原语不代替产品冷 JVM/中断/并发反证 |
| D07 | 附录 §7：M6 函数桥接、identity、恢复 payload/入口、未知裁定 | DESIGN_FROZEN（含 §7.2 权限回复增量）；CC 设计 PASS 与用户语义确认已齐；实现后证明恢复/终态及人裁定不冒充机器事实 |
| D08 | 附录 §8 + 补记 LOW-1：删除隐式 builtin 构造、显式装配、holder monitor | DESIGN_FROZEN；实施后证明入口/会话/并发。验证前同 JVM 多 workstream 路由仍禁止并发 |
| D09 | 附录 §9：逐窗 Git 事实、恢复/no-change | DESIGN_FROZEN；实现后证明观察充分性与真实交付，不新增空交付/自动 commit |
| D10 | 附录 §10 与 §10.1–§10.4：模型精确 ID 引用、完整基准 R01 固定及多层强制消费 | 受影响接线实施/离线验证/重冻已完成并获合并 CC 认可（§10.4）；其余字段义务保留，真实模型减负和 token 收益未证，不关闭 W07 |

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
