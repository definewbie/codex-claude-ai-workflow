# Dual 完整研发生命周期可靠性设计修订

日期：2026-09-23。基线：`3362eb42` + 接手时已有未提交实现。
修订：R3（2026-09-27 补记）已获第三轮 CC 设计层 PASS，N1–N4 确认为设计层解决；本次另记录 INFO-1 的语义澄清，未另经独立复审。
状态：`R3 DESIGN REVIEW PASS / INFO CLARIFICATION DOCUMENTED / IMPLEMENTATION NOT AUTHORIZED / REAL E2E NOT AUTHORIZED`。

2026-09-30 当前补记：用户已决定收敛 D04，I3 执行面责任及详设 §8.25 已同步调整。§8.25 目标收敛已获新一轮 CC 设计层 PASS，但该轮明确未复读本文 I3 同步文本，不能将其写为已独立核验；本处同步仍为作者自查，不授权实施或运行。

## 0. 本文的权限与适用范围

用户本轮要求：修正整体设计，生成供用户交给 CC 的只读评审提示词；用户随后明确不直接调用 CC。因此本轮交付仅为设计及入口说明和评审提示词，不代为启动评审。评审 PASS 不授权实现、合入或真实产品运行；WARN/FAIL 返回设计分析，不边评边修 production。

本文是整体设计与验收契约，覆盖多 agent 经 acpx 完成需求、设计、计划、实现、自测、验收、交付与恢复。不是剩余缺陷清零声明，也不是所有子题的逐文件实施方案。

覆盖：三个 bundled workflow、external small、inline/worker、CLI/API/UI/report、现有 acpx 与两种 routing kernel 的选择边界。自定义任意 workflow 不自动获得业务序列完整性承诺。

三个 bundled 与当前 small 定义均未使用 fork。模型存在 `TransitionMetadata.fork` 不等于本设计承诺并行分支的汇合、取消、授权合并或恢复完整性；这些业务语义不在本轮范围，启用前须另行设计与授权。现有通用 fork 能力不在本轮删除或改写。

不做：新增 model 层、DSL、权限/证据/观测平台、替换 LiteFlow、恢复 R4/Phase B、历史样本迁移/救援、产品代码实施或真实 E2E。后续必要 schema/持久化增量仍须先完成详细设计；不能以“复用现有模型”掩盖新增 authority。

已确认的产品要求无需重问：

1. 关键授权与最终交付必须审批；其余已批准范围内的工作有界自动推进。
2. 未来同一冻结运行版本可从最后确认点继续；外部结果不明时保留现场、人工裁定。
3. 需求阶段确认目标、验收和禁止范围；只读设计/计划后、首次实现前批准精确文件及操作范围。

## 1. 依据、当前状态与本次修正

依据：[整体评估](../../output/architecture-audit-20260917/assessment.zh-CN.md)、[证据索引](../../output/architecture-audit-20260917/case-evidence.zh-CN.md)、[9·13 设计](20260913-workflow-first-convergence-redesign.zh-CN.md)、[既有实现检查点](20260913-workflow-first-implementation-checkpoint.zh-CN.md)、[历史交接](../design/20260913-dual-evolution-retrospective-and-handoff.zh-CN.md)。output 是本次本机评估证据，不能假设新 checkout 必然包含；缺失时应依据历史文档及其原始 ledger 链接核验，不能将缺失视为通过。

原账本和代码支持：历史存在完整交付；当前组合的稳定交付未证明。已有未提交的人工 revise、前置失败和序列测试改进，不能继续说“全部尚未实施”。也不能把局部绿色测试写成整链已闭合。

本次接受外部复核提出的完成判据、共享测试基础、kernel 身份及显式止损要求；修正以下推论：

| 原提议 | 本文决定 |
|---|---|
| 全 definition × outcome 静态零死端即可证明 completeness | 静态检查只能证明声明一致性；合法 occurrence 必须由真实后继消费，并走完后续审核/批准 |
| 任一非终态加人工升级即无死端 | 人工等待须有具体缺项、责任人、受支持解决动作及解决后的可执行后继；无新能力时诚实停止，不能冒称可续 |
| 9BZG/FZ8K/KPPZ 都是设计期 scope 拒绝 | 前两者涉及实际实现变更，KPPZ 含非法占位路径；三者不能成为放宽设计校验的共同依据 |
| intake 冻结全部测试方式/环境细节 | intake 定验收目标和已知约束；设计/计划补足，首次实现前证明必需验证路径可行 |
| 直接采用 intent-journal | 先冻结恢复验收要求，再比较现有 R1 候选；本轮不选存储机制 |
| APPLIED 但 commit 为空即缺陷 | 可是合法中间态；缺陷是错误展示、错误续作或无法解释外部结果，而非字段形态本身 |
| kernel 身份能兜底全局 holder 并发风险 | 身份与隔离分别验证；记录选择不能防止并发碰撞 |
| 三类各两个样本证明稳定性；$96.7 是实际成本 | 样本数需实验与预算依据；旧 usage 汇总不是账单，不支持模型优劣或稳定成功率结论 |

不承诺消灭所有未来问题。目标是对声明支持的合法操作、错误拒绝及已定义失败切面建立可执行验收；未知外部能力不由新状态词汇自动解决。

## 2. 保留的架构与唯一 owner

| 边界 | 现有 owner / source of truth | 本轮设计约束 |
|---|---|---|
| Definition | WorkflowDefinition/JSON、FlowKernelProvider、FlowOrchestrationKernel | 声明拓扑与选择的 kernel；领域身份规则仍由代码守卫 |
| Instance/批准 | RoutingEngine、node/gate/iteration/current、HumanGateActionSemantics | 人工动作、当前 subject、授权和业务 revision；不将模型建议等同批准 |
| Execution | runtime/worker、AcpxAgentTransport、ProcessResult/attempt/run facts | 是否启动、进程/turn 是否结束、真实执行失败；准备失败不能伪造 provider run |
| Artifact | 现有 parser、acquirer、validator、qualified contract、authored lineage | 机器身份与模型语义分开；验证通过才允许发布和路由 |
| 测试/验收 | self-test/acceptance typed artifact、ExternalVerificationEvidence | 执行记录、提交者声明、语义评价分别保留；不将材料 hash 当执行证明 |
| 交付 | immutable candidate、DeliveryApplicationService、目标 Git tree/commit | 绑定批准字节；工作流完成、应用、提交不互相冒充 |
| 投影 | RecoveryAssessment 及现有 query/read models | CLI/API/UI/report 消费同一事实，不从正文或 latest 文件自造状态 |

复用现有 NodeResult/Attempt/TaskContract 等模型。通用 result map 的允许字段和阶段适用性须由现有事实入口约束，不能再在每个 handler、finalizer、CLI 各写一套推断。只有详细设计证明现有模型不足时才提出最小增量，不在本轮新增平行 authority。

## 3. 六项不变量及完成判据

### I1：合法操作与真实消费者闭合

适用前提：支持的 definition、合法 pre-state、正确身份与材料、所需 authority 已取得；不把损坏账本或外部不可用纳入“保证成功”。

要求：一次合法 revise、clarify、repair、collect/submit evidence、scope revision、retry、final approval 必须产生满足真实下一消费者的输入，并在有界路径回到相应审核/批准或明确等待。纯补证据不得要求无必要源码写入；人批准也不能跳过事实校验。

落点：HumanGateActionSemantics、RoutingEngine、现有 revision/lineage resolver、definition 边与领域验证器。已有 `allowedActions(definition, sourceOutcome)` 继续使用；不把扩一个同名接口当新能力。

验收：

- 定义检查覆盖可见 resolving action 与边的一致性、required refs 与实际 producer 的连接；`request_action` 是非 resolving 记录动作，不强迫它有边。不建设第二个 workflow DSL/全局模型检查器。
- 真实 gate→作者→复评→再批准序列覆盖 READY/BLOCKED 人工 revise，以及 contract clarify、设计/计划返修和批准失效；不能在 successor READY 停止断言。
- 身份错误、跨轮、错 subject/SHA、缺 note/材料与合法续作配对；非法操作在 authority 变更/provider/业务产物发布前被阻止。
- 补证据经过 self-test 与 acceptance；实现改变则重新自测；合同改变则重走新批准。声明未支持的请求只记录，不动态执行。
- 每个支持变体及 inline/worker 的差异有测试清单；终态拒绝新执行，观察/导出仍可用。CLI/API/UI 显示相同资格与真实下一步。

成熟度要求：静态一致性为 VALIDATED；真实消费者前强制为 ENFORCED；完整序列/反证为相应范围 PROVEN。静态 checker 零错误不升级为整链 PROVEN。

#### I1.a：声明允许与实际续作前置冲突的运行时证据

**首版覆盖补记（2026-10-08）：**用户在 B05 成本取舍评估后明确选择 C：先对合同资格校验族完成实际决定检查、后继拒绝配对、四分类及合法续作的垂直切片，其余守卫族明确 UNKNOWN。具体候选见 [C 接线设计](../../output/lifecycle-batch-05-20261008/scope-c/design.md)，该接线已获用户带回的独立设计认可，LOW-1 枚举守卫已纳入；与详设 §4.3、主清单 §21.1 同步。认可不等于本族实施或整链证明。 2026-10-09：本族已实施并完成主 agent 自查及内容稳定的单轮离线回归，当前证据见主清单 §21.2–§21.3；用户带回 CC 批末独立 PASS，本族 CLOSED / PASS。时钟判别非驻留证明的 LOW 已登记，不能把此单族状态外推为完整 I1.a/D01/W01 或真实运行已闭合。下文仍是完整 I1.a 目标；未覆盖族保留为未完成，不能用首族证明关闭 I1.a/D01/W01。不会削减合法流程、严格身份/scope/批准/证据、独立复评或安全恢复，不自动开展第二族、通用追踪平台或历史回填。

仅检查静态边和已知序列不足。目标要求：当前 occurrence 的 resolving action 被接受后，真实下一消费者若因前置不满足而拒绝，失败必须保留“本次已允许的动作—被拒绝的前置”配对。它是现有失败事实的结构化补充，不是新增 workflow 状态或通用死端求解器。

职责与写序：RoutingEngine 负责权威 gate 决定及其 occurrence/subject 引用；执行实际前置检查的现有 resolver/validator 负责产生具体失败事实；runtime 的既有结果入口（执行准备失败走 AgentExecutionResultFacts）将二者绑定，现有 ledger 结果写入路径在发布失败节点时一并持久化。配对至少包含 gate/decision 引用、action、source/target occurrence、subject/合同修订引用、拒绝的 consumer、阶段、稳定前置代码及证据引用。不能从错误正文猜 action，也不能由 RecoveryAssessment 补造事实。字段形式与 validator 落点须在 I1 详细设计中冻结。

配对字段冻结时，必须能从 gate 决定引用追溯当时实际核验的 occurrence、subject、材料引用及 SHA、适用校验规则与结果；优先引用已有不可变证据，只补足确实缺失的事实。不把“当时允许 action”推导成“所有后继前置都已通过”。恢复解释须比较同一前置在决定时和消费时的证据，区分内容漂移、未检查和契约矛盾；缺少证据仍归诊断不足。以可实际触发设计矛盾、真实漂移及未检查三类对照验证分类，而非仅验证兜底可用。现有引用的完整性和增量成本须在详细设计核实，不预称近零。

前向记录与历史缺证据分开：未来按冻结新契约产生的决定必须提供所要求的规则身份及核验结果，不满足时按该契约拒绝，不降级兼容；旧决定不回填或猜造这些字段。历史记录缺失只说明无法核验，不能直接断言当时“未检查”；只有可信证据明确该前置未检查时，才可如此分类。历史诊断不授权恢复，旧版本组合仍按 I5 的支持边界处理。

“随失败节点一并持久化”是要求，不代表已选择或证明同文件原子提交协议，更不代表 gate/node/event 跨文件原子性。具体保存形式及失败前缀仍按 I5 详细设计与证据判定。

RecoveryAssessment 是唯一对外恢复解释入口，消费该配对及已验证来源：已满足声明前提却被消费者拒绝，显示为设计/实现契约矛盾；材料缺失、过期或真实环境能力不足，显示为具体操作缺项及合法解决动作。篡改/错身份仍是非法拒绝，不能升级为可 retry。来源不完整或无法区分时明确诊断不足，保留原始阶段原因，不猜为设计缺口，不默认普通重试。以上是解释类别，不是新持久化 authority；HHZL 这类输出枚举拒绝仍由 I2 的 artifact 边界解释，不能仅凭家族相似就归为 gate 死端。

拒绝之后不得启动后继 provider、发布成功业务产物或扩大批准。配对必须先于“失败已完整记录”的对外结论；记录写入失败时保留 I/O 失败和现场，恢复读侧将不完整前缀明确呈现为诊断/持久化未完成，不显示普通可续等待。其本地写序、重复写及冷启动处理受 I5 协议约束；不能用新增一份旁路日志绕开该依赖。

验收同时覆盖：合法 revise 完整回到复评/批准；声明允许但消费者错误拒绝；合法输入缺项及补足后续作；篡改/跨轮拒绝；配对写前/写中/写后中断及冷读。移除配对记录或篡改其来源时，测试必须因诊断义务缺失而失败，且实际系统不得显示普通可 retry 或静默 WAITING；保留零越权副作用断言。I1 的范围只是发现实际发生的矛盾，不宣称运行时能穷举全部不可达路径。

### I2：执行事实与业务判断分别由唯一 owner 产生

要求：输入准备、外部执行、应用输出、领域验证不能相互冒充；最终失败保留决定性原因及诊断证据。Execution outcome 与验收业务分类不是同一个 owner，FailureArbiter 仍消费已验证的领域类别，不替 transport 决定 process 成败。

落点：AgentExecutionResultFacts、AcpxAgentTransport、acquirer、AgentExecutionOutcomeResolver、ArtifactContractValidator、AgentRunMetadataFinalizer、RecoveryAssessment。复用既有 typed facts，不把 precedence 搬成新的可配置策略平台。

验收矩阵必须明确：未 dispatch 的输入失败；bootstrap/permission/provider failure；timeout/stall/interruption；非零且 turn 不完整；turn 完整但 envelope 不合法；artifact/领域拒绝；有可接受警告且所有成功条件成立。真正成功与 contained permission warning 仍须验证全部前提。

- 每类正例、单故障和关键冲突组合从 inline/worker 走到 node/run/recovery/CLI/API；邻接优先级对不足以替代阶段组合。
- 有效 JSON 不能掩盖不可信 process；artifact 未发布不能反向改写原 process 原因；已有 `process-failure-counterproof.xml` 所证明的场景必须进入常驻回归。保留正常成功对照，并验证恢复旧的原因覆盖行为会使断言失败；归档 XML 本身不代替测试。
- 输入失败为零 provider 调用、零伪造 run、零成功 sidecar；真实输出缺失仍按其真实阶段处理。
- 后处理只校验适用事实，不重新读 stderr/正文产生第二控制原因；保留 raw 证据用于诊断不受限制。

I1/I2 详细设计共同承接 GCTW 的机器身份回显负担：机器已有的 occurrence、subject 和 obligation 身份由既有 context/contract owner 提供，逐字段区分机器绑定信息与必须由模型给出的语义回应，评估减少重复复制和受限 correction 的必要性。具体最小字段调整尚未选择；不得模糊匹配身份、自动补造语义回应或把真实返工伪装成格式修正。验收应包含正常回应、漏义务、错身份、跨轮以及语义仍不满足，证明减负后严格拒绝和合法续作均保留；用实际 prompt/输出字段及 correction 原因核验减负，不先承诺 token 降幅。

### I3：目标批准与精确写入批准在正确时点成立

2026-09-30 用户收敛决定：D04 以[详设 §8.25](20260928-dual-lifecycle-detailed-design.zh-CN.md#825-用户决定收敛为原生权限复用与审批接入2026-09-30当前规范)为准。Dual 复用原生权限/审批并可靠处理批准、拒绝和续作，不再承担独立安全沙箱、底层不可绕过或控制目录绝对防篡改证明。下列职责同步收窄，业务 scope、批准身份、独立复评、证据及交付验收不变；这是保证范围调整，不是旧反例被修复或产品已实现。

要求：intake 先确认目标、验收与禁止范围；只读 design/plan 可以提出规范的文件/操作建议；第一次写入前必须存在已批准的精确执行范围。提出建议不等于获得权限。

目标流程：

```text
目标/验收/禁区批准
  -> 只读设计与计划、语义复评
  -> 文件/操作范围与必要权限批准
  -> 实现 -> 自测 -> 验收
  -> 批准具体交付候选 -> 应用/提交确认
```

需求变更、范围/权限增加、重大取舍、最终交付保留强制人门；已批准范围内的中间评审与修订按预算推进。人工 revise 仍可介入；不得用自动推进暗中生成 human approval。当前各 variant 多余强制停顿的删除/保留须在详细设计里逐门映射，不直接删 reviewer。

落点候选：现有 proposal、TaskContract、qualified contract、authored design/plan、HumanGateActionSemantics 和实现准入。尚未决定采用哪种 schema 表达分期批准；“DesignScopeValidator 两态”也不是已选实现。

验收：合理只读建议在无文件级写授权时可评审；非法占位、路径逃逸仍拒绝；未冻结精确范围时零实现调用；scope 冻结后越范围拒绝或走明确重新申请；增加范围/改目标导致受影响的旧批准和证据失效，重新评审后才可执行；没有授权的自动人门不成立。

原生权限接入与阶段校验责任：

- 阶段资格仍由 qualified context 与 workflow 语义决定。AcpxAgentTaskHandler/AcpxAgentTransport 在 dispatch 前选择并核对原生角色/权限配置、调用与批准身份，不能只信节点名、提示词或 mode 名；不静默切 implement/approveAll。Claude/Codex、inline/worker、correction/session 复用按实际入口核对，不做工具/子树全排列。
- 原生客户端负责工具许可与已有沙箱；Dual 不增加 LLM 命令安全裁判或通用规则平台。需要额外权限时，将真实请求和人工决定关联到当前调用，按既有执行/恢复通道处理；原生许可不扩大业务 scope，不生成复评或交付批准。无在线批准能力的入口允许明确停止并在旧执行状态核实后走既有续作，不伪造回传或盲重跑。
- 只读阶段不授权 agent 修改产品、Git 或控制事实；保留正确原生配置及发布前阶段变更核验。baseline 用来区分已有改动和本阶段结果、确定证据身份，不宣称检测任意瞬时写后还原或承担恶意进程隔离。实现阶段合法改动按 scope 校验；异常/不可解释变化或控制事实不可信时阻止正常采纳，保留现场，不倒签批准或回滚用户改动。
- 程序工件仍由唯一 writer 产生；路径位于 `.agent` 不等于模型获权，程序输出不能只因位置正确就获得批准/证据 authority。既有身份/ref/SHA/写序检查继续由领域消费者强制。撤销“全量文件系统/ignored 程序目录完整性监控及所有别名安全证明”作为 D04 的额外前置，不用事后检查冒充绝对写前隔离；当前检出覆盖不足如实记录，不声称全面防篡改。
- 测试以正常自动允许、需要批准后合法续作、拒绝后停止/合法替代、错身份/过期决定、回传及写入失败、冷启动和实际 sibling 为准；保留阶段变更与 scope/证据反证。移除配置/批准消费检查须被测试发现。底层沙箱绕过穷举、硬链接/内核实验不再是冻结或实施完成的默认义务，新增此类目标须重新做产品取舍。

2026-09-29 用户决定补记（原生内部委派方向已获用户带回 CC 设计 PASS；下述三类拒绝时点澄清待复审）：允许原生客户端在一次 Dual 执行内部组织子任务，不再把“禁止一切原生委派”作为 I3 的必要条件。Dual 的 workflow occurrence、作者/评审身份、scope/权限批准与交付 authority 不因原生子任务而增加；子任务必须受该次执行的相同权限上限约束，不能替代独立复评或产生人工批准。定义、限制、验证清单与成本风险见详细设计 §8.21；这不授权当前会话启动额外 agent、真实实验或产品实施。原生子代理数量的独占控制、逐子任务 Dual 账本及绝对零超支不新列为 D04 前置，既有预算治理、未知结果恢复和真实 usage 记录义务不撤销。 验证按详设 §8.22 区分：原生权限由客户端依其机制执行，Dual 负责正确配置和批准前接入检查，不独立证明底层不可绕过；业务 scope 对实际变更校验后阻止不合规成果继续；源码身份漂移阻止旧证据用于新源码。后两者不冒充精确文件写前拦截。

详细设计缺口：分期批准表示、现有 compile/qualification 的顺序迁移、三类 bundled 与 small 的 gate 对照表、批准失效规则，以及按 §8.25 收敛后的原生权限配置、审批续作与阶段检查接入方案。缺一则 I3 为 DESIGN GAP，不授予实施准入；本节命名责任不表示现有代码已经 ENFORCED。

### I4：必需验收项在实现前已有可行验证出口

要求：intake 定验收目标与已知环境约束；design/plan 将必需验收项对应到方法、执行环境、权限、证据形态和负责者，首次实现前完成资格检查。可选/探索性检查与必需项区分；不能把 reviewer 新偏好自动升级为必需验收。

复用现有 TaskContract、plan validation 内容、execution policy、自测/验收与 external-evidence 路径。当前 plan 自然语言不是可直接执行的可信命令，不能由文件名猜命令或将任意 plan prose 当 shell。

验收：

- 必需项缺执行出口时，阻止进入实现并明确缺项和有权限的解决者；可记录计划，不能假报环境已验证。
- 可用性依据要与实际能力相符：例如浏览器启动、端口、数据库、容器、外部服务的条件。外部验证可以由 operator 承担，但该责任与材料接收路径须明确，不能仅写一句“以后人工验证”。
- 不要求 intake 时冻结所有命令；若只有实现后才能确认细节，实施前先明确可行验证方法/环境与细化责任，交付前必须补足实际执行证据。首次实现后新发现不可行条件会阻止相关验收成功并走已定义处置，不假装从此永不遇环境失败。
- 同源码/合同基准的补材料到 self-test→acceptance；材料声明与机器执行记录区别展示。来源或源码变化后不能复用旧结论。
- exit 0 不自动生成 SELF_TEST_COMPLETE；保留缺证据、跳过项、业务断言和独立验收。

详细设计缺口：必需验收项标识及验证路径绑定的最小 schema、可行性证据的可信边界与检查时点。自动执行凭据若需新 authority，先比较现有外部提交足够与新增执行记录的收益/成本，不借此启动通用测试执行平台。

### I5：未来冻结版本的有界重启续作

要求：同一冻结运行组合内，纯本地 transition 从已持久化的充分事实幂等续完；可能发生过外部副作用但无法确认结果时，不自动重放。新 binary/旧 ledger 不在本次兼容承诺内；未知或不支持组合明确 INVALID，不能静默读成新语义。

运行身份分两层：

- 创建时冻结 Dual 可执行内容、contract schema/validator 版本、prompt 模板、reviewed acpx 身份、kernel 选择及影响执行语义的配置；不是只记 Git SHA。尚未决定具体存储字段/owner。
- 每个 occurrence 绑定实际合同修订、批准 subject、生成 prompt 内容、权限策略、provider/model/session 输入。合法合同修订改变内容身份，不应被误拒为 binary 变化；已有批准失效规则仍适用。动态内容 hash 不应混成一个永远不能更新的 workstream fingerprint。

恢复入口必须在创建 execution/provider/外部变更前验证身份。kernel 的显式选择进入该身份，不自动切换 fallback；builtin/liteflow 等价测试保留。全局 holder 的跨 workstream 并发隔离独立验证，冻结身份不能替代隔离。

隔离验证完成前的临时运行约束：使用 LiteFlow 的同一 JVM 不并发路由多个 workstream，且 holder 的 load→execute→clean 与其他实例的 close 不得交错。最低操作约束是一个进程只承载一个可推进的 workstream，路由及 close 串行；server/API、worker、gate action 和恢复入口均不得绕过，不以目录级 WorkstreamLock 代替进程内约束。未来运行 Gate 与 I5 恢复准入必须核验该约束的实际保障；不能证明部署/入口已限制并发时 NO-GO，而非仅记录风险。这里是设计和运行准入约束，当前未新增锁或修改服务，不能宣称已有 runtime enforcement。以后若保留同进程并发，须先验证完整 holder 生命周期隔离及兄弟入口，不能仅测两个不同 kernel 身份。

当前 FlowKernelProvider 在配置空缺时默认选择 LiteFlow，因此上述约束影响默认路径。未来 Gate 须记录配置值与实际生效 kernel，以及 server/worker/CLI 的实际 JVM 拓扑、每个进程的路由入口和可推进 workstream；进程数或一次运行快照本身不能证明互斥。拓扑和入口保障无法核实时不得假定 worker 天然进程隔离。

落点：现有 ledger writers/readers、runtime execution/reconciler 与恢复入口。按 [R1 评估](r1-multi-file-mutation-bounded-assessment-result-20260826.zh-CN.md) 和 [方案比较](r1-multi-file-mutation-design-comparison-20260826.zh-CN.md) 重审 transition-specific reconciliation、prepared mutation、generation pointer 的范围/成本；本轮不选择 journal，不迁移历史。

验收：未来新实例覆盖 R1 14 个前缀，纯本地提交冷启动后有唯一可续完结果，event 不重复、孤立后继不被误采纳、reader 不混读。初始化只有 event 的旧事故缺充分 after-state，不能靠猜测救；新协议必须在可恢复边界留下足够事实。M6 queue/execution、run publication、projection reader 和交付外部窗口不能由 R1 四组通过代替。

provider/文件写入/patch 是否发生未知时停在明确人工裁定状态，普通 retry 拒绝；观测到结果不等于允许重做。终态不重开。掉电/fsync 承诺是否包含在目标中需在存储详细设计中明确；现阶段只定义进程中断和已报告 I/O 失败，不声称抗硬件掉电。

详细设计缺口：本地提交协议、reader 一致性、event 幂等、执行边界桥接、运行身份 owner 与完整写序。方案未选，不能把 I5 标为 Implementation Ready。

### I6：完成、批准候选、应用与提交分别核验

要求：workflow COMPLETED、验收满足、具体 candidate 已批准、目标 Git 已应用、commit 已确认分别保持其真实事实；不新增一套同义状态平台。

落点：现有 completion/goal 审计、candidate writer/resolver、DeliveryApplicationService、merge-plan 与 Git adapter、统一查询投影。

验收：批准绑定 candidate bytes/base/tree；apply 校验 source 与 target；commit 校验 tree/parent/HEAD；候选漂移、脏 target、错误 commit 均拒绝。APPLIED 未提交可合法展示，不虚构提交；发生外部写入而 plan 未记录时，核验实际 index/tree/commit，未知结果停人工，普通 retry 不能盲 apply。核验只读本身不生成新批准。

详细设计须列出 apply/finalize 每个外部副作用与 durable-write 窗口，以及哪些已有状态足够表示处置；不足再提出最小增量，不能先引入 crash 状态机。完成本项不代表新增部署/托管发布能力。

## 4. 端到端 transition 与失败切面

此表确定验收义务；未选存储协议的目标写序明确留待详细设计，不冒充逐文件实施设计。

| pre-state / actor | action 与现有 owner | 已知当前写入或副作用顺序 | 成功后真实消费者 | 故障/重启/重复动作验收 |
|---|---|---|---|---|
| 当前 gate、verified subject；人/显式授权代理 | revise/clarify/approve；RoutingEngine | gate→node→successor/iteration→events→current/instance，依实际分支核对 | 作者/compile/下轮复评或批准 | note/身份不足零授权写；中间前缀不得重批/孤立推进；I5 新本地协议续完 |
| 合法作者输入；runtime/worker | prepare→dispatch；adapter | 准备/claim/run metadata 与 process spawn 依分支不同 | transport/acquirer/validator | 准备失败不伪造调用；已准备但是否 spawn 未知不盲调用；排查 queue/execution/run 桥接 |
| typed process 与当前候选；程序 | finalize/validate/publish | process/run facts→候选校验→sidecar/节点发布→route；当前后处理存在再次计算 | review/decision/下个 context | 每个前缀保留阶段原因；孤立 sidecar 不进入 authority；事实冲突拒绝成功 |
| 已有代码、缺证据；人或既有补采动作 | collect/prepare/submit；EvidenceCollection 等 | baseline preparation→材料/manifest→gate 提交→self-test | self-test→acceptance | 准备过期/源码改变/材料错属拒绝；上传不等于批准；不无故进入 implementation |
| 当前基准的有效 acceptance REJECT；既有分类器/路由，必要时人 | FailureArbiter 分类→有界返工/补证据/授权变更→再验收 | 验收 artifact 校验发布→节点结果/路由→返工义务与后继输入；后续源码/证据发布→self-test→新 acceptance，逐分支写点待展开 | 作者或补证据入口→self-test→acceptance；范围变化先经 I3 新批准 | 义务身份/基准失效拒绝；源码变动旧自测/验收不可复用；补证据不强制改代码；预算耗尽不伪造合法返工基准；在义务发布、后继创建、自测发布前后中断均不能漏义务、跳自测或采纳旧 ACCEPT |
| 需要目标/写范围变化；人 | proposal revision→新审批 | authored revision/approval/contract epoch 依 I3 详细设计确定 | qualified 执行输入 | 新建议无写权限；旧批准不能授权新内容；必须有新链完成后再实现 |
| 候选已批且流程可交付；operator | apply/finalize；DeliveryApplicationService | plan READY→Git apply→日志/tree 核验→APPLIED；实际 commit→finalize COMMITTED | target Git / query | 外部成功内部未落盘，先观测真实 tree/commit；不重复应用；错误 candidate 不发布 |
| 最后确认点；operator/runtime | resume/retry/abort/replay | 先核对冻结身份/本地提交/外部事实，再决定能否创建新执行 | 既有 runtime/恢复 read model | 已知本地可续；外部未知人工；旧组合 INVALID；abort 保留证据，终态只读 |

每个实施批必须把自己涉及的行展开为实际写入点前/间/后完整表，包含 retry、resume、abort、replay、冷启动、actor、唯一 owner。详细表未完成即 NO-GO，不凭这里的概要开工。

## 5. 共享验收基础与六维证明

复用现有 HumanRequirementContinuationSequenceTest、WorkflowFirstLifecycleSequenceTest、AuthoredBoundarySequenceTest、RetryFailureContextSequenceTest、WorkflowGateResolutionTest、MultiFileMutationRestartAssessmentTest 及 delivery/qualification 测试。不是新建一套测试平台；具体文件与已有缺口在详细设计核对。

测试分层：领域单测检查语义；契约检查连接和必需字段；真实 runtime/ledger/kernel 序列检查消费者；failpoint/冷启动检查耐久性；真实 E2E 检查实际模型和环境。序列测试不是唯一口径，模拟 transport 也不能证明真实 provider 行为。

每项边界以同一证明表追踪：

| 维度 | 必交证据 |
|---|---|
| Declared by | 合法操作与领域前提，来源是用户要求/批准合同/支持的 workflow，不是当前 if 分支 |
| Proven by | 对应正常、拒绝、冲突和故障序列；指明哪些只离线验证 |
| Consumed by | 唯一真实下一消费者，执行到它接受/拒绝，不只断言节点已创建 |
| Enforced before | 不满足时实际被阻止的 provider、authority 变更、发布、Git 副作用 |
| Failure route | 原因保真；谁能补什么；补足后如何续作；未知结果不得自动重做 |
| Cannot-bypass test | 移除关键守卫或恢复旧错误行为，反证必须失败；合法路径不被误拒 |

对本轮相关边界变更，拒绝能力与合法续作都必须有证据；允许复用已经覆盖的测试，不要求每个微小断言新增一个镜像测试。没有六维证据不得称闭环；测试数量、状态字段存在和自动转人工不替代证明。

## 6. 真实验证准入与止损

当前真实产品验证为 NO-GO：设计/详细方案、实现、整链回归、环境检查、预算和冻结工件尚未同时满足。拟交给 CC 的只读设计评审不属于产品 E2E，不授予任何 workstream 调用/恢复权限；当前只准备提示词。

未来每次运行沿用现有 Verification Gate，不另建 runner/evaluator 平台。至少要有：目标、SoT、可检查的触发前提、PASS/FAIL/INVALID、stop-loss、新鲜工件身份。补充可执行判据：

| 项目 | GO / PASS 条件 | NO-GO / 止损 |
|---|---|---|
| 冻结组合 | 实际 binary/schema/template/acpx/kernel/config 可核验；运行身份与观测一致 | 缺失/不匹配 INVALID；不临时换配置后把结果拼成同一轮 |
| 冻结运行清单 | 在现有验证报告/输出目录保存可复核清单：Gate 与冻结组合的预期值/来源；每次实际 workstream/occurrence/run、输入/批准基准、provider/model/权限、起止与人工干预、验证证据和交付事实引用 | 清单缺失或无法与实际 ledger/run/Git 交叉核验时，本轮验证为 INVALID；不得补写一份预期配置冒充实际记录 |
| LiteFlow 临时隔离 | 记录默认/配置/实际 kernel 与 server/worker/CLI JVM 拓扑；证明同 JVM 单一可推进 workstream 且所有 holder 操作不交错，或已证明等效完整隔离 | 仅目录锁、身份字段或恢复时一次快照不足；不能证明则不开跑 |
| 离线基础 | 必经合法/拒绝/恢复序列通过；无已知阻断目标的设计缺口 | 确定性断链未解决不消耗真实样本 |
| 验证出口 | 必需验收项有已落实的环境/权限/材料路径 | 无可行出口不开工；运行中失效则正确暂停，不能降标 |
| 成本与运行限额 | 在当次 Gate 指定正数调用/语义修订/累计运行时长及可测 token 上限，并说明检测点与中途终止方式 | 任一上限缺少实际监测即 NO-GO；触达限额停止新 dispatch，已有调用按已验证停止方式收束，不伪称无副作用 |
| 金额 | 可计价时声明价格来源/计费模式与金额上限；subscription/token-only 保留明确状态和 token/时间约束 | 旧 usage 汇总不当账单；无可信计价不编造美元节省 |
| 交付 | 需求/测试/证据/scope/acceptance/audit/candidate 通过，且所声明交付级别有目标 Git 事实 | `COMPLETED` 单字段或人工直接盖章不能算产品 PASS |
| 新缺陷 | 模型/环境业务问题按已批准有界路径处理 | authority 绕过、错误成功、确定性合法链断路、未知副作用被重做立即止损，不等样本凑齐 |

冻结清单由当次验证负责人在运行前记录预期身份与 Gate 引用，按实际运行补充引用并在结束或止损时封存；复用现有报告/metrics 导出，不新建账本或 runner。费用沿用明确的 billing mode、token-only/subscription-unpriced/pricing-unknown 状态，不能只记一个估价。清单只是证据索引，实际 authority 仍在 ledger/已验证 run metadata/批准 artifact/目标 Git；I5 运行身份字段的 owner 尚未确定，清单不能替它完成恢复校验，也不补救历史运行。

普通任务、自然需返修任务、自然需外部证据任务是覆盖维度，不预定每类两个，也不据少量样本宣称成功率。用同一个冻结组合按需顺序验证；自然分支未触发记 NOT EXERCISED，不造错误。离线可覆盖的分支不为数量再烧模型。

只有现有证据支持新的诊断假设，且明确剩余预算，才安排后续样本；同类验证两次失败回边界分析。任何更换 provider/model、权限或验收目标都显式成为新的组合/授权判断，不能用配置变更掩盖前轮失败。预算数值在具体可审查 Gate 中由用户确认；当前不请用户凭空选一个美元数。

## 7. 实施前顺序、成本边界与待决项

本节仅说明以后如何组织，不构成本轮启动指令。

2026-09-28 用户授权进入详细设计，已形成[详细设计候选](20260928-dual-lifecycle-detailed-design.zh-CN.md)。它提出本节待决项的具体选择与未冻结证据；未经独立评审，不修改本设计验收、不授予实施准入。

1. 任何实施批次之前，先建立“现有未提交实现 ↔ 本契约”的对账清单，包含 tracked/untracked 的相关代码、定义和测试：按行为/边界记录采纳、需调整或建议撤回、所对应不变量、已有证据与缺口、所属后续批次。记录核对的 HEAD 与文件内容身份，工作区变化后重核受影响项；建议撤回不授权现在覆盖或还原用户改动。随后冻结整体验收契约与测试覆盖地图；I1/I2 是公共安全网。静态检查按已知连接缺口补，不能先建庞大 totality 平台。9·27 已形成上述对账材料，见 §7.1；已读改动与尚未核验部分分别列明，9·28 已获 CC 材料层 PASS，2 LOW 处置见清单 §8，不据此解除详细设计阻塞。
2. I3 授权时序与 I4 验证出口共同完成详细设计，避免一边把设计后移、另一边要求 intake 提前冻结一切。关键审批的逐门映射与自动推进预算同批说明。
3. I5 本地恢复/运行身份与 I6 外部交付窗口做独立详细设计；共享 I1/I2 测试和状态词汇。可在设计层交叉推演，不表示允许多 agent 并行实现。
4. 用户另行批准实施后，按完整用户流程及其跨 owner 依赖组织实施批次；可以分批，但不得缩减整体验收或在后继尚未消费时称流程完成。每批复用共同验收，不允许只修一个 if 后称整体完成。范围清楚的前向实现不背负历史兼容。
5. 实现与必要回归完成后，才形成具体预算/样本 Gate；真实证明后再基于事实减少调用/耗时。

| 尚未决定 | 决策必须提供 | 未决时的约束 |
|---|---|---|
| I1 续作拒绝配对的字段与写入契约 | 按 I1.a 冻结现有事实入口/validator、决定时校验证据、来源绑定、可触发的分类前提、配对随失败结果写入及其 I/O/冷读反证 | 不宣称运行时死端诊断已闭合；依赖 I5 的中断恢复不得由 I1 单独宣称解决 |
| I1/I2 机器身份回显减负 | 分清机器绑定与语义义务、最小字段方案、严格身份/漏义务及合法续作对照 | 沿用现有严格合同及受限 correction，不先放宽校验 |
| 分期授权 schema、gate/qualification 迁移 | 现有模型复用/最小增量、完整 pre/post/write/consumer 表、批准失效与 variant 差异 | I3 不可实施 |
| I3 原生权限接入与阶段检查 | 依详设 §8.25 核对实际配置/请求/决定/续作通道；阶段变更、scope 与身份校验留在既有 owner | 缺批准/拒绝/未知结果的合法出口未明则不可实施；不要求独立底层安全证明 |
| 验收项/验证出口绑定 | 可信输入、检查者、检查时点、外部材料与实际执行证据区分 | I4 不可实施；不能把自由 prose 升格为命令 authority |
| 本地持久化/reader 协议 | R1 各候选成本、14 前缀与 M6/reader 覆盖、event 去重 | I5 不可实施，不默认选择 journal |
| 运行组合身份与恢复校验 | 固定版本/每次输入的区分、owner、hash 范围、配置/kernels、漂移拒绝点 | 不声称同版本恢复已支持 |
| 交付中断处置 | 每个 Git/plan 窗口、只读核验、何时人工裁定、复用现有状态的边界 | I6 crash recovery 不可实施 |
| 跨 workstream LiteFlow 隔离 | 默认/实际 kernel、部署 JVM 拓扑、同进程并发需求、holder 全生命周期与所有入口的实际保障/验证 | 验证前执行 I5 单进程单一可推进 workstream、holder 不交错约束；无法证明则运行 NO-GO，不凭线索换引擎 |
| 真实验证预算/样本 | 冻结组合、目标覆盖、可执行上限和停止方式 | 不运行真实产品验证 |

无法依据当前材料给全部改造承诺固定工程日。9·13 的 2.5–5 日只适用于当时限定批次，不外推为 I3–I6 总成本。

### 7.1 工作清单的执行依据与纠偏

根据用户带回的“执行与纠偏办法”评审，先前对话中的通用办法不作为独立规则来源。后续一份主工作清单只索引本设计的验收条款、依赖和实际证据，不另写一套可独立漂移的完成定义。2026-09-27 经用户授权已形成[主工作清单、改动对账与证据地图](20260927-dual-lifecycle-worklist.zh-CN.md)，核验范围和内容身份以该文为准；载体于 9·28 获 CC 材料层 PASS，2 LOW 处置及作者核验范围见该文 §8，不代表产品能力或实施准入通过。本次只更新材料状态，不改变以下已评审规则。

- **目标出口**：以 §0 的完整研发生命周期及用户已确认要求为目标，§3 定义边界验收，§6 定义真实验证准入和交付判定。保留 [9·13 §0、§12.2](20260913-workflow-first-convergence-redesign.zh-CN.md) 的产品目标与“不能替代”警戒，但冲突以本设计和用户最新要求为准。清单全绿不等于产品完成，一次真实样本也不证明所有支持路径或稳定成功率。每批记录具体解除/新增/未变的 §6 阻塞及证据；新前置必须说明为什么阻止合法流程，不能把可选改进不断追加为 E2E 前提。技术就绪和用户授权分列；未授权不能靠新增技术工作掩盖，也不能擅自开跑。
- **开始与完成**：开始前绑定工作区 HEAD/内容身份、适用契约版本及具体条款，确认依赖；验收预期来自合法用户流程，不由本次实现反推。每项证据标明 §5 的验证层次，状态沿用 [engineering-checklist 的 Phase Promotion](../engineering-checklist.md) 区分实现、验证和准入，不自建同义成熟度。契约或输入版本改变时重核受影响证据，不能修改条款后静默沿用旧通过结论。
- **批末完整性与独立判断**：执行 §4 写入失败表展开、§5 六维证明及 AGENTS.md 的 Sibling Sweep，并对照 9·13 §12.2 的不可替代项；未单独立项的同边界兄弟路径也在核验范围。执行者自查与 CC 独立评审分开记录，跨层批次收口需有针对实际版本、完整流程和证据的独立判断，不能仅审 diff 或由作者自定自评完成条件。沿用用户自行发起 CC 的方式，不自动调用或委派；尚未独立评审就明确未评审，评审 PASS 也不能代替所需实际测试或用户最终验收。
  独立评审触发条件按实际影响判定，任一满足即适用：涉及 §2 owner 表两个及以上边界的职责或事实消费关系；改变任一 §3 验收义务；涉及任一 §4 transition 的行为或失败处置、因而须展开该行。是否满足不取决于文件数、模块数、“单层”标签或作者有没有实际展开表；同一用户流程中互相依赖的改动须合并判断，拆批不能规避。清单记录命中的条款或不适用依据，供评审复核；这些是可核验的语义条件，不宣称已有自动判定器。
  首次适用的独立评审同时审查主清单结构、条目粒度和测试覆盖地图是否覆盖完整目标与兄弟路径，不另加评审轮次。在此之前不得凭未审载体作跨层批次完成或准入结论；之后载体实质改变时，重核受影响结论并纳入对应批次评审。
- **纠偏先定边界**：每次错误按 §3 的 I1–I6 和 §2 owner 记录事实、受影响链路和依赖，允许跨多个不变量，不能只用表面报错命名来规避同边界重复检查。首次出现生命周期/资格/原因冲突就按 boundary-first 分析，不能等第二次。只有证据表明现有契约明确且完整、实际实现偏离它，才按实现错误修复；存疑先暂停受影响修改并查明，不直接推定应补 if，也不直接推定必须重设计。不依赖尚未实现的 I1.a 才能开展分析，可引用现有代码、ledger 和反证并标明证据缺口。
- **重开与传播**：评审发现、反证失败、Gate FAIL/INVALID、新历史事实与当前完成声明发生冲突时，必须重核对应工作项及下游依赖，登记原判断、反证、修订与复验结果。FAIL、INVALID 分别追查产品边界或验证前提；外部环境故障不自动证明代码有错，单个历史事故也不自动推翻无关版本。与当前结论相关的证据不再成立时撤销对应验证/准入结论，不能只在末尾追加补丁项。
- **停止与继续**：范围内明确的实现修复自行完成；设计缺陷先修订受影响契约/工作项及证明义务，经必要独立评审后继续。新增 authority、扩大目标/范围、重大取舍或降低验收要求时，将具体选择交用户决定。新前置的必需性须对照批准的验收项及可行替代路径核验；若仍有争议且影响是否能开跑或交付，按重大取舍提交证据与选项由用户决定，不能仅改“必需/可选”标签来冻结运行或降低验收，也不能因 I4 schema 尚未落地而跳过此要求。已批准边界恢复且必要检查通过后继续对应批次，不因纯文字建议反复开启整体架构评审；同类失败按既有止损纪律回到边界分析，不新增独立计数或治理平台。

本节最新修订已获用户带回的 CC 复审 PASS：WARN-1、LOW-1、LOW-2 全部确认为约束文本层面解决，无新增必须修正项。执行规则评审在此收口；尚无实际批次执行效果证明，不改变实施及真实运行未授权状态。历史执行率仍只记录“未取得对应实际执行证据”，不将未核验写成零；CC 已接受此限定。

以下四项保留为首批实际执行及同次独立评审的观察项，不新增机制或另开整体规则评审：新前置是否被无争议地误标；以“仅日志/无行为变化”作出的不适用判定是否成立；跨流程部件组合是否出现遗漏的边界影响；声称可行的替代路径是否满足原验收。发现实质反证时沿上文既有纠偏规则处理。评审所称记录“非强制”不改变本文“每批记录 §6 阻塞及证据”的要求；剩余不确定性在实际执行质量，而非免除记录义务。

## 8. 本次 CC 评审与交接

评审范围：整体设计的正确性、必要性、owner、状态/权限/证据边界、合法续作、失败切面、兄弟路径，以及完成判据能否区分设计可接受与实现可开工。CC 必须独立重建链路，不以本文答案或六维表存在就通过。

输出：PASS/WARN/FAIL（设计层），问题级别、证据、影响、最小修正、尚未核验部分；另列哪些子项仍 DESIGN GAP。允许整体方向成立同时明确详细设计阻塞，禁止以 PASS 自动推动实现。

首轮有效结果来自用户带回的 CC 评审：设计层 PASS，附 W1–W3、L1–L3，I3/I4/I5/I6 保持 DESIGN GAP。CC 声明未重跑测试、未逐行核验全量 diff/索引，也未深入 M6/worker/small；该 PASS 不能外推为这些范围已证实。此前误启动且停止的 CLI 调用没有有效结论，与此次用户提供的评审无关。

R2 只修订设计义务和入口说明，未实施或重新运行测试。2026-09-27 用户带回第二轮 CC 评审：设计层 PASS，无新增 WARN/FAIL；下表首轮六项问题由 CC 确认为设计层解决，另有 N1–N4 四项 LOW。该结论不表示字段/写序等详细设计完成，不自动进入实现。

| 首轮发现 | R2 处置 | 尚未证明/仍须详细设计 |
|---|---|---|
| W1 声明允许但续作被拒的 owner 缺口 | I1.a 指定原始事实 owner、既有结果持久化、RecoveryAssessment 消费和诊断反证 | 字段/完整写序待冻结；不把所有拒绝归为设计死端，不造恢复 authority |
| W2 只读阶段执行面未绑定 | I3 命名 adapter/transport enforcement 与发布前 baseline 检查责任，覆盖失败与 sibling | 现有 profile 不等于只读；I3 仍 DESIGN GAP |
| W3 holder 并发未有临时限制 | I5、§6、§7 加入单 JVM 操作约束与运行准入 | 当前未实现全局保护，并发安全未证明 |
| L1 REJECT 与 fork 范围缺项 | §4 明列返工到再验收及失败切面；§0 明列 fork 不在业务完整性承诺内 | REJECT 各分支仍须展开真实写序 |
| L2 反证仅候选 | I2 改为必须进入常驻回归，保留成功对照与旧行为突变 | 未新增或复跑测试；XML 不等于常驻执行 |
| L3 GCTW 与运行清单未承接 | I1/I2 待决表承接身份回显减负，§6 明确清单责任、内容及真实事实来源 | 不放宽身份；清单不是 I5 身份 owner 或新 SoT |

第二轮 LOW 的处置：N1 在 §7.1 加入实施前全量相关改动对账；N2 在 I1.a 要求决定时真实校验证据及可触发的分类对照；N3 在 I3 要求豁免的统一来源与 owner，但不预选新 manifest；N4 在 I5/§6/§7 加入默认 LiteFlow 与实际 JVM 拓扑前提。用户带回的第三轮 CC 评审已独立确认四项在设计层解决，无新增 WARN/FAIL。

对第二轮报告的限定：报告将 I1.a 概括为“同文件原子持久化”，超出了本文已选方案，本次已明确其仍待 I5 设计；不采纳“增量近零”的成本预判。报告未核验全量 diff、实际测试覆盖、M6/worker/small、部署拓扑及导出/豁免可行性，这些仍未证明。沿用现有报告/metrics 导出只是优先复用方向，具体可用性需核实，不能当作已存在完整冻结清单能力的事实。

第三轮评审确认上述两处限定成立，仅留 INFO-1：详细设计应区分新决定的规则记录要求与历史缺失。本文接受前向记录、不回填历史的方向，但澄清“历史无记录”不等于“当时未检查”，沿用诊断不足语义。该澄清未另经独立复审，不扩展历史恢复范围。三轮评审均未代替全量未提交 diff 对账、实际测试覆盖核验或 I1/I3–I6 详细设计；整体设计评审在此收口，实施、合入、真实运行仍未授权。
