# D01–D10 冻结评审补充材料

2026-09-28；上位仍为 R3，唯一工作索引仍为主清单 W00–W09。本文件是详细设计 §15 的证据附录，不是新计划或产品 authority。全文“选择/新增/必须”是待 CC 审核的设计决定，非已经实现。CC 可以否决或要求调整；作者不预填 PASS。

**准入状态：冻结候选，不是已冻结版。** D04 的 Claude 实际读取被认证阻塞，不能由文档补齐。当前不具备全案 Implementation Ready，也未获实施授权。本轮只补设计、静态映射、合成字段样例与本机存储原语验证，无产品源码/测试源码改动、无 provider 调用。设计层可以审查以下明确选择，技术可行性阻塞保持单列。

## 1. 材料身份、覆盖与证据等级

- `baseline.json` 固定 HEAD 和本轮前 1319 个工作区文件；`source-index.json` 固定原读写候选及补充关键类内容 SHA。已有 33 个 tracked 改动和两个 untracked 测试不覆盖、不回退。
- `access-inventory.json/.md` 对原 116 个候选逐项给出处置，补充 26 个关键兄弟入口，共 142 文件。方法定位是词法锚点，不能替代编译器调用图，也不意味着这些文件都要修改；已有上下文参数足以传会话的纯解析函数可以保持算法不变。
- `precondition-map.json` 把指定五个现有身份/谱系守卫类的逐点条件、现有错误字面量、消费者和内容身份保存供审查。原始条件不是新 DSL；实施时规则来自现有 typed checker，不能运行时靠错误正文匹配分类。该枚举覆盖声明的类，不冒充整个仓库所有检查的穷尽证明。
- `examples/` 为完整拟议合同与边界事实 JSON，引用 SHA 指向实际样例字节。所有样例是 synthetic design fixture，不是新运行实例、真实批准或环境证明；不能投入执行。`real-producer-samples.json` 另列真实诊断生产者及其证明边界，不把二者混合。
- `storage-probe.json`：JDK 25、本机项目所在文件系统的 ATOMIC_MOVE 替换、同 JVM 重入拒绝、跨 JVM 文件锁阻塞、释放后可重取通过。不是产品提交协议/故障矩阵通过，不证明其他挂载或硬件掉电。

## 2. D01：逐前置绑定与写入失败

规则标识采用 `checker class + semantic rule id`，版本来自冻结 checker 内容身份；当前逐点标识见 precondition-map。实施重构后将词法序号映射为固定语义常量，并在测试中保证一对一，不允许新增校验静默复用旧 rule_id。运行时不读取本设计 JSON 路由。

| 原消费者/当前条件 | 拟议决定时检查 | 何时仍留给消费时 | 失败出口及写集 |
|---|---|---|---|
| HumanGateActionSemantics：合法动作、note、gate purpose/outcome | RoutingEngine 接受动作前调用现有语义；记录实际 gate occurrence、requested/canonical action、规则身份与结果 | 后继运行身份和输入资格不能由动作合法性替代 | 动作非法零决定写入；决定合法后的拒绝由 L04 记录配对 |
| TaskContractRevisionContextResolver：gate/node/event/subject、resolved/completed、READY/BLOCKED、iteration route、proposal/ref/SHA、义务 | 把决定时确实可检查的当前 subject/route 共用 checker，并保存检查快照 | 后继新 occurrence 的输入/目标、实际不可变文件当前 SHA 和执行 epoch 必须重验 | 保留原准备失败 code；禁止调用 provider，配对随失败节点 L03 |
| TaskContractProjectionVerifier：APPROVAL_BINDING_MISSING、GATE_NOT_COMPLETED、GATE_LINEAGE_INVALID、CONTRACT_NOT_APPROVED、GATE_RESULT_MISMATCH 及逐点 compile/hash/scope 条件 | 分别检查 GOAL/EXECUTION subject；批准只针对该 gate subject，不搜索任意 latest | 后续合同失效、当前阶段、scope/权限变化在每次 dispatch 重验 | 原 qualification 失败不转成 JSON 缺失；失效批准零 dispatch |
| AuthoredStageLineageResolver：事件唯一性/顺序、author/reviewer subject、义务、定义拓扑 | decision producer 检查 reviewer 与 author、规则、合同；人门不代替 reviewer | 当前 task 与新的 reviewed_candidate/合同关系 | 缺 reviewed、跨轮、非 READY 均禁止相关自动推进，不能制造 approved gate |
| ScopeRevisionRequestGuard：当前受理范围修订资格、批准与来源 | request_action 接受前核对原 subject 与新请求身份 | 具体新增文件仍由提案、复评、执行批准再判断 | 请求被接受不 resolve gate、不自动扩大写 scope |

每个新决定的 `decision_checks` 必含 coverage：实际 PASS 的规则及输入 ref/SHA；明确推迟的规则记 NOT_CHECKED；缺历史记录一律 UNKNOWN，不推断当时检查过或没检查过。不能把“动作允许”理解为“所有后继前置已通过”。准备时若有合法 resolving decision 谱系，失败 facts 必含 `continuation_failure` 配对；没有该谱系的普通输入错误不得伪造 gate。

配对包括原决定不可变引用、规范动作、source/target occurrence、subject、实际消费者、phase、原 failure code、rule identity、当前观察引用。owner 为既有 AgentExecutionResultFacts，原 primaryReason 不变。R03 保存准备失败结果，L03 将 node/task/execution 与该配对同次提交。配对无法持久化则保持 pending/诊断不足，不能仅写普通 WAITING 再宣称诊断完成。

RecoveryAssessment 唯一消费：相同已 PASS 条件却拒绝→设计/实现矛盾；有实际身份差异→漂移；coverage 明确推迟→消费阶段缺项；缺记录→诊断不足。三种有据分类及 UNKNOWN 都须可实际触发，不能只验证兜底分支。移除配对写入时，序列反证必须响亮失败；补足合法缺项后仍须走作者→复评→新批准，不只验证拒绝。

## 3. D02：permission 关联的具体选择

当前 AdapterSignal 只有 type/message/observedSequence/source/sessionId/superseded，不能定位被拒动作；AcpxStreamWireLine.Request 已能取 toolCallId/kind/title/resource，Notification 能取 toolCallId/toolName，但尚无完整 typed 工具执行结果。故当前“完整产物即证明非必需”的逻辑不能保留为新协议默认。

选择扩展**现有** AdapterSignal/AgentExecutionEvidence 的可选 typed 关联，而非另建权限平台：

| 字段 | 生产者与证据 | 消费规则 |
|---|---|---|
| signal 的 `operation_ref`：run/attempt/session/tool_call_id、原始事件 ref/SHA/sequence、kind、canonical resource/操作参数 hash | transport 从真实 ACP request/update/permission decision 关联，不解析 message 猜路径 | 无法唯一关联即 UNKNOWN；跨 session、缺结果、重复异 payload 均不可容纳 |
| evidence 的 `permission_assessments[]`：signal identity、合同 ref/SHA、被检查的 criterion ids、satisfied evidence refs、rule identity、disposition | 现有 validator 根据 D05 已批准义务和 program-owned 工具事实生成；模型只提供语义解释，不能填 verified=true | resolver 对**同一 signal**消费。缺关联保持原阻断，不以其他 signal 的 assessment 替代 |
| disposition | 固定三类 REQUIRED / PROVEN_NONESSENTIAL / UNKNOWN | 只有 PROVEN_NONESSENTIAL 有资格进入 contained 判断；还须过程可信、envelope/必需产物合格 |

PROVEN_NONESSENTIAL 不是“模型说可选”：须有在拒绝动作之前/独立路径上已经取得的每项必需义务完成证据，或固定节点合同声明该动作属于不影响必需输出的额外尝试；两者都不能包含该被拒动作作为必要生产者。调用参数/输出无法精确归属，或者只存在模型自述的 checks_run，一律 UNKNOWN。保护区写入、只读边界违规、身份/权限漂移不可降级为可容纳警告，即使其他义务已经完成。首版不做任意 shell 静态语义分析；只支持现有 typed tool event 能完整关联、D05 明确接受的证据种类。未支持的合法用户流程可走材料补足/人工处置，不伪称完成。

正向对照须构造：必需 A1/A2 已有可信独立完成证据，额外工具动作的真实 permission signal 精确关联，返回 SUCCESS_WITH_WARNINGS；删除任一完成证据/换 tool id 必须退回阻断。另有完整成功、不完整 process、有候选但禁止发布、真实坏 envelope/合同、发布 I/O 失败对照。E02 在 WorkflowFirstLifecycleSequenceTest 的 inline/worker 常驻，最终 node/run/recovery 仍为 process_output_incomplete；不从 sidecar 是否存在重新产生控制原因。

## 4. D03：完整字段和逐消费者迁移

TaskContract 新增独立 `schema_version=2`，原 `version` 保留为合同版本，`epoch` 是该实例授权修订身份；不混为 binary 版本。GOAL 和 EXECUTION 是同一 DTO 的 phase。GOAL 的 scope.included 必空，EXECUTION 必有精确 CREATE/MODIFY 文件清单。外部权限沿既有 grant ref，空 authority_refs 只表示不需要额外外部 grant，不授予网络/工具通行。

完整 GOAL/EXECUTION、proposal/目标批准、design/plan 的作者—复评—decision facts 和执行批准样例在 examples。GOAL 编译引用已批准 proposal；EXECUTION 引用 GOAL、两 reviewed candidates 与验证出口；执行批准在合同外部 gate 中绑定合同 SHA，**不把批准 ref 写回被批准合同形成 hash 循环**。样例中的环境是明确 synthetic，不具生产资格。

| 消费者/生产者 | 迁移决定 | 拒绝对照 |
|---|---|---|
| TaskContract/TaskContractValidator、proposal validator/merger | 稳定 clause `{id,text}`，显式 phase、schema、epoch；CREATE/MODIFY 严格路径不放宽 | v1 混入 v2、新字段冒充旧 schema、空 EXECUTION scope、GOAL 有写 scope 拒绝 |
| DesignScopeValidator / PlanArtifactDocument / AuthoredRevisionValidator | GOAL 的 planned_files 是待审提案，核验路径/禁区/操作/目标，不与空 included 做已批准子集匹配；EXECUTION 后重规划须落在批准范围内，越界重批；义务仍严格逐项回应 | GOAL 空 scope 不得让所有设计提案死端，也不得因此允许产品写入 |
| EvidenceCollectionBaseline / EvidenceCollection.verifySource / ScopeRevisionRequestGuard | 删除 stable task-contract.json 精确字符串依赖，统一核验不可变 EXECUTION occurrence ref/SHA/epoch；展示 latest 不获资格 | 旧稳定路径即使同名有 JSON 也不能替代当前批准 |
| ContractCompileHandler / TaskContractArtifactWriter | goal 与 execution 编译入口使用同一 handler 的固定 phase 配置；按 occurrence 输出不可变合同及 resolution | 不覆盖旧批准 subject；复制 Markdown 不产生 authority |
| TaskContractProjectionVerifier | `resolve(requiredPhase, occurrence/input refs)` 从 routed subject 解析，返回已核验快照；latest 只作展示 | 未批准/跨 phase/旧 epoch/文件漂移拒绝；不能退回稳定 task-contract.json |
| ReviewDecisionHandler / AuthoredStageLineageResolver | READY 固化 reviewed_candidate，普通 READY 无人门；读取路由指向的 decision occurrence，保留作者/复评身份 | 假 human_action=approved、任意最近 READY、pending facts 不可推进 |
| AuthoredExecutionContext / AgentPromptRenderer / ContextPackageSectionRenderer | 同时提供当前 reviewed refs 与独立 execution authority；prompt 只投影 | 缺授权不因上下文含 approved 文本通过 |
| AcpxAgentTaskHandler / ArtifactContractValidator / qualification | 按 definition 的 required_contract_phase 强制；design/plan 只 GOAL，implement/self-test/acceptance/report/delivery 使用 EXECUTION | 缺 phase 配置非默认放行；合法新 plan 在原范围内复评后可继续，越范围须新执行批准 |
| CompletionEvidenceLineageResolver / AcceptedDeliveryCandidateResolver / CompletionAuditWriter | acceptance、report、candidate 与实际执行合同/current reviewed 输入精确绑定 | 源码/合同改变后旧 ACCEPT 及旧交付批准不能继续使用 |
| 四份 definition / registry / loader / CLI/API/UI/report | 所有 requires_approved_task_contract 与 gate/purpose 硬编码同步迁移；各投影分别显示 reviewed 和 authorized | 四变体×inline/worker 同一准入；投影 approved 布尔值不能授执行权 |

变体 F/C/T 的 design/plan 既有 reviewer 保留，正常 READY 改为有界自动推进；阻塞/预算/重大取舍才进入实际人工门。**S small-plan-human-review-experiment 当前 plan 作者直接进人门，缺 plan reviewer/decision**：本候选选择补入同一既有 Codex plan reviewer/decision 节点与合同配置后再自动推进，不能删除唯一审核就声称保留独立复评；它仍是实验资源，不作为新增 bundled variant。目标门、精确执行门、最终交付门全部保留。fork 未被当前四变体使用，不承诺本次并行分叉研发；新定义启用 fork 须另有完整写集/汇合证据，否则新协议资格拒绝。

旧 v1 ledger/artifact 仅诊断观察，不自动迁移/恢复；新的 schema 由固定新 binary 创建，严格读旧文档失败不得 fallback。旧模型输出多余身份/字段也不靠宽松解析接受。以上应通过新旧格式/正向续作成对用例冻结，当前不是已实现兼容逻辑。

### 4.1 D04：绑定字段、传播入口与程序写入归属

沿主设计 §8 使用既有 request/session/run，选择 adapter-private `readOnlyBinding.schema_version=1` 的不可变文档 ref/SHA。字段固定为 workstream/node/task/attempt identity、role、GOAL/EXECUTION ref/SHA/epoch、canonical product/Git/common-dir/control roots、可读取的冻结输入 refs、程序 scratch 精确目录、provider/model、acpx/adapter/native identity、profile revision、capability upper bound、request policy hash。binding 是对已有权限的收窄，不是 grant；缺 binding 不默认 observe。第一次准备也不能先运行 hook/plugin 再补验。

| 入口 | 必须固定/核验的内容 | 缺项处理 |
|---|---|---|
| TaskHandler 准备 | 资格 snapshot → canonical roots/baseline/输入 → 原子发布只读 binding；run R01 和 claim 引用它 | 引用未提交无 dispatch；孤立文档只诊断 |
| AcpxAgentTransport / acpx agent-command/session options | 两 provider 均传相同 ref/SHA；session 记录包括角色/策略/版本/输入身份，不能以 cwd/session 名替代 | 未识别 schema/provider 组合明确拒绝；不落回原高权限 profile |
| Claude create/query/recreate | 有效内置工具集合 Read/Grep/Glob；禁额外 MCP/agent/tool/config 放宽；固定 SDK/native/profile。候选参数的 effective tools 及 query 参数由 adapter 程序回执提供 | 回执证明传播，不证明工具强制；native 实际读取和负例仍未通过，保持技术阻塞 |
| Codex threadStart/runTurn | 真正 readOnly policy、never approval，必要受限 shell；每 turn 从不可变上限构建参数，不能使用公开 mode 的 workspaceWrite 映射 | native 工具宿主和额外面未证明的组合不启用；不是把 mode 改个名 |
| correction/queue owner/load/resume | 当前 policy 与冻结 session identity 相同或更窄；恢复先 D07 外部结果核验，重建仍传固定参数 | 不支持的入口拒绝；不能因为缺回执重新发 prompt 探测 |
| setMode/setConfigOption/provider/fork/goal/steer | 逐入口：受限角色不需要的控制面直接拒绝，需要的只允许同上限；本轮不支持 fork | 参数拒绝必须发生在外部请求前，不能靠事后 baseline |
| ACP fs/read/write/terminal | 读正向可用；写和 terminal 的 handler 强制拒绝，能力展示同时收窄 | 不用 denyAll 一并破坏读取，不以 terminal=false 单独证明强制 |

程序回执含 binding SHA、实际 adapter/native identity、session/turn/task、真正发送的 policy 参数 digest 和生效工具/创建响应可提供的字段。Codex turn 无完整生效 policy 响应时明确标记“程序发送对象”，不能编造 engine 确认；由 transport 核验后才给下一调用资格。

程序工件统一归属清单放在现有 run 准备记录，不另建 manifest 平台：binding/prompt/manifest 归 TaskHandler 准备；raw logs/run completion 归 transport/finalizer；immutable sidecar 归 validator/publisher；node/gate/events/grant/delivery plan 归 ledger 提交者；scratch 归创建该 run 的程序。每项记录 canonical ref、预期字节或程序写入事件/结束 SHA、时间窗口和消费者。准备、收尾、恢复、测试消费同一清单；路径属于 .agent 或 tmp 不足以豁免，模型写入程序路径仍拒绝。active log 只有其程序 writer 可更新，不把其字节变化自动洗成模型合法写入。

读取范围分开描述：业务读取是项目及固定输入；工具运行时加载是引擎/依赖及认证所需内容，不能被声明成项目业务证据。上轮 Codex 读 RTK.md 的来源未核实，故不宣称宿主配置已隔离；原生 sandbox 是否允许超出精确 scratch 的写入、工具宿主是否全部受约束也不由产品文件两次拒绝证明。后续能力验证仍须针对这些具体风险，不能用“模型遵守了 prompt”关闭。

D04 目前仍有两个层次的阻塞：原生 Claude 认证恢复后必要读取/限制的真实证据，以及原生工具/配置/scratch 组合是否满足候选上限；adapter/session 的新实现则在获准编码后按上表验收。两者不得混称“全部只是接线”或“只差登录”。如果已有原生参数不能守住明确的必要上限，按用户成本收益止损，不自行转 OS 隔离/拷贝 workspace/引擎 fork。

## 5. D05：证据生产者、完整样本与可信边界

2026-10-06 用户确认的后续修订：真实退出码不再是所有机器验收的统一必填项；是否必需由执行前批准的 criterion 验证要求决定，明确要求时仍必须取得。原生结构化状态是可用的执行观察，不能冒充真实 exit 或单独证明业务断言。以下历史表和样本中的“缺 exit”统一按“缺该项验收要求的证据”解释；只有工具名而无实际结果仍不能通过。具体正反对照与拒绝时点见 [已确认决定](../lifecycle-batch-03-20260930/continuation-20261006/d05-evidence-decision.md)。本修订不降低已有批准标准，生产/消费接线和运行证明仍未完成。

2026-10-06 接线补记：本节原冻结语义保留。B03 发现“operator 在合同前认领”的具体生产入口尚未指定；现由 [D05 接线决定](../lifecycle-batch-03-20260930/d05-readiness-wiring.md)补齐入口、环境身份、复用/失效、消费和失败续作。该新增设计尚未实施、未独立复审，随 B03 批末合并审查；不能用原 DESIGN_FROZEN 状态声称新增入口已获运行证明。以下历史样本和能力声明不改写。

不新增测试执行器。冻结两种首版出口：`PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS`、`OPERATOR_DECLARATION_WITH_MATERIAL`。前者需要可验证的实际工具执行记录与断言内容；后者只有目标明确接受人工声明时可用。全局“agent 进程 exit 0”不是某条测试执行成功，哈希不是业务正确性证明。

| 现有生产者 | 当前确实提供什么 | 本轮明确的实施增量/限制 |
|---|---|---|
| AcpxAgentTransport / AgentRunMetadataWriter | acpx 进程 command/exit、raw stdout/stderr、attempt/session 身份 | process command 不能冒充内部 pytest 命令；在既有 raw event 采集/Notification 中补 typed tool_call input/result/exit/状态引用，逐版本缺字段拒绝该机器证据种类 |
| AgentResultEnvelope.SelfTestResult / AcceptanceReviewResult | checks/evidence 等模型声明 | 引用 program 工具记录和当前源码 hash，校验引用真实；语义断言由独立验收核查，不将字符串自动认证为工具事实 |
| ExternalVerificationEvidence.prepare/submit/verify | Preparation、Declaration(command/environment/times/exit/purpose)、材料字节/hash、source baseline | 明确保留 OPERATOR_ATTESTED，prepare 只绑定基准，不证明设备/环境可用；不把 declaredExecution 的 exitCode 升成 program-executed |
| EvidenceCollection.captureBaseline/verifySource/execute | 现有源码/合同/节点基准与只补材料约束 | 多文件状态从一个 committed snapshot 捕获；提交源漂移拒绝并重 prepare；源码变化后回适用测试/验收 |
| native CLI 日志/本地 Java probe | 本轮 real-producer-samples 中的真实工具/进程数据 | 仅说明此类生产者与字段确实可取得；不能直接拿诊断 session 充产品 task 的完成证据 |

代表性样本 A（CSV）：examples 的 A1/A2 对应 CREATE 两文件。环境可行性在 EXECUTION 前由 operator 可用性声明或已有程序环境事实落实，引用必须说明真实生产者，不能用 synthetic fixture 准入。实现后 self-test 的每项引用 `run/attempt/session/tool_call_id + input/result refs/SHA + source capture + assertion refs`；验收确认正向合计与非法值错误两条断言分别存在。若 adapter 只有工具名没有实际结果/exit，则机器证据缺口，不宣布通过；先使用同一已批准证据要求下的已有可信日志补足，否则该计划不得开跑。

代表性样本 B（设备触控）：已批目标明确允许 OPERATOR_DECLARATION_WITH_MATERIAL，operator 在合同前认领设备/责任/可用性；实现后 prepare 固定源码与执行合同，submit 的 Declaration 含设备环境、开始结束、结果及目的，材料为明确提交文件。只有人声明，没有机器执行证明；与 A 的机器证据不能互换。错误 baseline、prepare 前时间、材料 SHA 漂移、同 request_id 不同 payload 均拒绝；相同 payload 且已完成 L05 返回同一结果。

为避免“有字段无生产者”，附录不把上述新增 typed tool result 采集写成现状。其 owner、取数点和验收已选定，属于既有 adapter 记录增量；实现若发现 provider 完全不提供必要字段且无可信替代，阻塞对应机器证据出口并按成本收益止损，不能悄悄引入 runner 或把目标降成 operator 声明。

## 6. D06：写集、读取会话、持久化协议

冻结 prepared mutation 方向：只完成已经校验并固定的本地 after-state；不执行 provider/Git、不重算路由、不产生新批准。每 workstream 同时最多一个 pending；manifest 在专用 `pending/<id>.json`，禁止任意路径或软链接。payload 固定 request id、operation、base sequence/identity、每个目标 before/after hash 与 bytes、每个 append 文件的已确认前缀长度/hash 和确定事件 bytes。跨文件不是文件系统原子事务，reader 一致性由共同会话边界保证。

| 写集 | 真实入口与 owner | 必须同次规划的 authoritative writes / 不能混入的内容 |
|---|---|---|
| L00 初始化 | FileLedgerStore.initializeWorkstream + WorkflowFileLedger.initialize，由 Start/application 组装一个工作区初始化计划 | workstream/settings/workspace、requirement、旧 current stage/iteration/checkpoint 兼容文档、workflow definition/instance/current、首 node/iteration、启动 events 与 legacy timeline append；目录/锁文件不授 authority；registry 最后登记，仅索引不跨库原子化 |
| L01 排队 | WorkflowRuntime.enqueueAgentTask → AgentTaskLedgerWriter + NodeStateWriter | AgentTask QUEUED、node queued task ref、workflow execution 关联及相应事件/current；不能先有 node RUNNING 再补 task |
| L02 claim | AgentWorker.executeAndRecord / WorkflowRuntime.executeAndRecordNode → ExecutionLedgerWriter + AgentTaskLedgerWriter | 唯一 claim、node execution、task RUNNING、node pid/execution refs；epoch/version 同会话重验；无 provider 副作用 |
| L03 完成/路由 | runtime/worker 统一 completion entry → RoutingEngine.completeAndRoute | task/node execution terminal、node result+refs、后继 node/gate/iteration、revision tracker、events、current/instance、适用 goal/completion audit；包含 I1 failure 配对或 reviewed_candidate；已完成 run/artifact 以固定引用消费，不在此重写运行日志 |
| L04 人工决定/请求 | WorkflowFileLedger.resolveOpenGate/requestAction → RoutingEngine / ScopeRevisionRequestGuard | gate result+decision_checks、gate node、适用后继和 iteration、events/current/instance；request_action 只写请求/事件，gate 保持 open；request id 相同异 payload 拒绝 |
| L05 材料/修订处置 | ExternalVerificationEvidence.submit / EvidenceCollection.resolveGate | 不可变 preparation/material/manifest 先发布；核验引用后 gate/node/submission refs/后继/event 同次提交。孤立材料不授资格；重放 L05 后同 request 查询成功，不靠“看到 gate 已解决”猜补路由 |
| L06 retry/stale/abort/close/reclassification/network grant | NodeStateWriter、WorkflowFileLedger 相应入口，外部终止先由 StaleRunningRecovery 执行 | 受影响 node/旧 task/execution、iteration/gate、event/current/instance；已有 grant 若变更同次提交引用和程序 grant 文档，语义仍沿现有权限 owner，不扩网络策略。外部终止不在 manifest 重放 |
| L07 交付计划 | DeliveryMergePlanWriter，经 DeliveryApplicationService | ready/apply-intent/observation/applied/committed 的本地 plan 与相应事件；实际 Git 每步在 L07 之外，不能在 pending replay 调用 |
| P 派生投影 | Timeline/Metrics/FailureSummary、memory/evidence manifest、展示用 latest | committed snapshot + source sequence 重建；失败标投影滞后，不反转 authority、不冒称提交失败；绝不能供 qualification 代替不可变来源 |
| R01–R03 run owner | AgentRunMetadataWriter / TaskHandler / Finalizer | R01 prompts/manifest/readonlyBinding/PREPARED；R02 durable dispatch intent→外部启动；R03 raw completion+唯一 outcome+不可变完成证据/产物。完成证据选择既有 run outputs 下 `outputs/<attempt-id>-completion.json`，同名异字节拒绝，node 引用 ref/SHA；活跃 run.json 不作为不可变完成证明。run 有独立 owner/写锁；不把活跃日志塞进 ledger prepared |

同一事件族的完整 event bytes 在准备时固定；events.jsonl 与 legacy timeline 的 append 分别记录前缀，不把 timeline 投影误当 event authority。同 id 同 bytes 重放一次；同 id 异 bytes INVALID。仅 manifest 绑定的未提交尾部可截断补齐，已提交前缀变化/损坏停止。

ArtifactContractValidator/TaskContractArtifactWriter/DeliveryCandidateWriter 等 producer 先生成验证后的不可变 bytes，由对应 owner 原子发布；R03/L03 的固定引用必须能重验 bytes。发布一部分中断时，只有已持久化验证结果和完整 after-bytes 能完成本地发布；否则保留未完成，不从残留文件编造成功，不再调用 provider 来补写。本地 mutation 与 run 之间没有跨 owner 原子事务：依固定完成引用和 consumer 重验桥接。

### 6.1 锁装配及 reader

WorkflowFileLedger 提供显式 `readSession`/`writeSession`，session 仅持同一 canonical workstream 的锁和 snapshot；不新增领域 model 层。公共 query 一次会话读取所需多文件；内部方法接已有 session，禁止再次 tryLock。CLI/API 的外层 WorkstreamLock 迁入这些短会话，不能仍覆盖完整模型执行；worker 的 queue 查找+claim 必在同一写会话。独立 run snapshots 通过不可变完成引用消费；活跃 run 日志可以单独观察，但不得拼成完成资格。

现有 WorkstreamLock 同 JVM 重叠会抛 OverlappingFileLockException；新会话应在异常/null 两种失败下关闭 channel，并统一返回 BUSY。owner 文件只是诊断，释放锁前按本次 owner 清除，避免旧 close 在 release 后误删新 owner。进程崩溃后 OS 释放锁，残留 owner 文件不等于仍持锁。

reader 获得锁后：无 pending 才提供 snapshot；有 pending 返回 PENDING_LOCAL_COMMIT（含有限 operation/identity，不混读业务结果），不自动执行修复；锁不可得返回 BUSY。写入口先核实 frozen identity，再验证/补完 pending；跨版本只诊断 INVALID。坏 manifest 或目标既非 before/after 禁止覆盖。view/finalize/evidence/各投影同样不能绕过，142 文件逐项对照在 access-inventory。

AtomicJsonWriter 当前捕获任意 IOException 并退回非原子 move，**新协议禁止该 fallback**。同目录唯一 temp，ATOMIC_MOVE+REPLACE_EXISTING；启动时在实际 ledger 所在 FileStore 验证支持性，不支持拒绝新协议。manifest/业务写/移除 pending 均有故障注入点；进程崩溃可续，不承诺断电/fsync。单机 probe 只支持本机方案可行性，不替代部署准入测试。

### 6.2 失败前缀与跨边界次序

manifest 完整持久化前：原状态不变；初始化孤立目录不登记。manifest 后任意业务文件/append/current 之间：普通读 PENDING，重启固定版本 finish-forward。全部 after 已落盘但 pending 未删：仅验后移除，不重算 UUID/时间/路由。manifest 损坏、未知版本、外部修改：INVALID/人工处置，零自动覆盖。

固定锁顺序：同流程先 target Git/common-dir 锁（仅 delivery），再 workstream 会话；run 的写锁不与 workstream 锁嵌套持有。长 provider 调用没有 workstream 锁。run 完成后释放 run 锁，持 workstream 会话重核固定完成引用及 claim/epoch；若已被取消/变更则保留历史完成证据但不能路由成功。同 occurrence 的第二 completion 对相同 completion ref 幂等，不同 ref 拒绝。

## 7. D07：M6 桥接与人工裁定 payload

当前实际顺序：inline 的 executeAndRecordNode 分别创建 task/execution、标 task/node running、执行并 finalize、分别结束 task/execution；调用者随后 completeAndRoute。worker 的 executeAndRecord 相同，enqueue 的 task/node 又是分写。新 L01/L02/L03 正是合并这些真实断点，不另建队列。

| 当前方法 | 新边界/顺序 | 中断解释 |
|---|---|---|
| enqueueAgentTask / runOne.firstQueuedAgentTask | L01 完成后才能 claim；查找和 L02 同锁核验，CLAIM identity 不只 PID | 未有 claim 可安全本地继续，双 worker 至多一个获得 claim |
| TaskHandler 准备与 transport.run | L02 唯一 claim→R01 完整 binding/prompt/input 身份→同 L02 入口的独立短提交绑定准备 ref（不同 operation id）→R02 intent→spawn | 只有可证明 R02 未发生且旧执行者已被排除时可重做准备；intent 存在而缺完成即未知 |
| transport + AgentRunMetadataFinalizer | R03 固定真实 process/signals/唯一 outcome 与 bytes；不从候选是否发布重判 | R03 可信时只补本地产物/L03，不再调用模型；缺结果不凭 PID 消失认定未执行 |
| runtime/worker.completeAndRoute | 读不可变完成 ref，重核 claim/epoch/frozen identity→L03 | 原子本地完成及后继；提交中断走 pending，不重复模型/事件 |
| AgentExecutionAttemptReconciler / StaleRunningRecovery | 先观察/必要时终止确属本 attempt 的执行者，确认不再运行；再按完成证据分类；最后 L06 | 终止失败保留 in-flight；terminated 也不代表外部未发生；旧二进制无新资格 |

冻结版本身份归 WorkstreamDocument 初始化：executable SHA 或完整 classpath manifest、definitions/schema/checkers/prompts、acpx/adapter/policy identities、影响行为的 config、configured/effective kernel。实现改变 scope/输入产生新 epoch，不改 frozen binary。secret 只记录配置来源或非敏感身份，禁止存凭据。

恢复入口复用现有 CLI/API 的 recovery/stale/retry/abort 与相同操作权限，不新增角色授权系统。需要的结构化裁定字段为：schema、request_id、workstream/node/task/execution/attempt、frozen identity、input epoch、观察到的 last event/operation refs+SHA、disposition、evidence refs、operator note；记录接受时的当前执行者检查，不把提交者姓名当授权校验。

| disposition / 入口 | 可接受的证据与行为 | 不允许 |
|---|---|---|
| COMPLETE_LOCAL / resume | 合法 pending 或真实 R03 完成；自动只完成固定本地写 | 不发 provider/Git、不推测 route |
| ADOPT_VERIFIED_RESULT / recovery stale | 唯一原 attempt 完成记录，校验输入/输出及 process 身份后 L03 | 单凭模型 pasted JSON 或人工“已完成”声明不能伪造程序完成 |
| AUTHORIZE_NEW_ATTEMPT / retry | 结果未知但旧执行者已停止；用户明确承担重复副作用风险的裁定，加同 scope/权限重核；未知仍记录 UNKNOWN，不改成“证明从未执行” | 不能由普通 note 或静默 auto retry 授予；不能扩大权限/抹掉旧 attempt |
| ABORT / recovery abort | 先确认执行者停止；L06 终态，外部效果不明标 unresolved | 不声称外部回滚；终态不重新执行 |
| existing deterministic classification / close mistaken iteration | 同冻结身份、现有 action 前置与证明，L06 | 不能充当任意旧 ledger 修复工具 |
| network approval / grant update | 沿现有独立网络 authority；引用和提交一致性纳入 L06 | 不借本轮开启 R4/Phase B 或扩大 provider 能力 |

人工 AUTHORIZE_NEW_ATTEMPT 是用户已要求的“不明时人工裁定”的具体表示，不是系统断言原动作未发生。若裁定者不接受该风险，只有继续调查或 abort；不提供假安全按钮。CLI/API 必须明确风险并核验同一 payload，重复同 request 幂等，异 payload 拒绝。具体新参数名可调整，以上 typed 语义和前置不得在实施时省略。

## 8. D08：LiteFlow 全局 holder

仓库生产代码的 holder 直接使用者只有 LiteFlowPrototypeKernel：advance 的 load/execute/finally.clean、loadExecutor 的 clean/load、close 的 clean；factory 入口为 FlowKernelProvider，CLI/ServerLedgerFactory 分别配置，空配置默认 LiteFlow，WorkflowFileLedger 无参构造却使用 builtin。测试中两种路径都有，不能把直接构造默认和产品默认混同。

选择对 `FlowExecutorHolder.class` 同一个 monitor 覆盖 load/execute/clean、临时规则文件生命周期与 close；不要在各 kernel instance 上各加一把锁。使用 holder 的实际 Class 对象避免 adapter 不同实例锁漂移；若运行时加载出多份 holder class，属于不同 static holder，必须记录 classloader/code-source，禁止把未验证装配拓扑算作同一证明。无须引入新路由服务或更换引擎。

生产 JVM 场景：server 同 JVM 多 workstream 必须串行 holder 生命周期；CLI/worker 是否同 JVM由实际启动方式记录，不假设天然隔离；不同 JVM 各自 holder，仍遵守每 workstream 文件锁。验证前临时禁止同进程并发路由多个 workstream。

待实施测试：两定义同/异 chain id、两个 kernel instance，以 barrier 把 A 停在 load 后并触发 B.advance/B.close；B 不得清理 A，A/B 的选择分别匹配 builtin 对照；异常路径后可再次调用；记录实际 factory/config/effective kernel/ClassLoader。不能仅用“启动了两个进程”证明这些条件。

## 9. D09：Git 逐窗事实与 no-change

真实落点：DeliveryApplicationService.apply 目前核验 candidate/base，写兼容产物和 READY，再 git.apply，写两日志，核 stagedTree，写 APPLIED；finalizeCommit 查实际 clean/HEAD/parent/tree，再 COMMITTED。GitPatchMergeService.apply 是 `git apply --index`，capture 使用临时 index、无 accepted files 时明确抛 no business changes to capture。

| 窗口 | 冻结的 observation / consumer | 恢复动作 |
|---|---|---|
| candidate/approved gate/target 基准核验前 | current snapshot + immutable candidate/patch/批准 SHA；目标 canonical repo/common-dir | 无资格只读拒绝，不能以 COMPLETED 推导已批准 |
| 兼容 patch/delivery 摘要写前后 | 都由相同 candidate 重建，非 authority | 失败仅本地重建，无 Git 调用 |
| L07 READY+apply_intent 前后 | operation id、candidate、批准、target、base HEAD/tree、clean/index/worktree 指纹、expected tree | 无 intent 才可新准备；intent 未完整提交零 Git |
| intent 后、Git 退出记录前 | 实际 HEAD、index tree、worktree 与 index 差异、untracked 状态、原批准 | base+clean 且原执行者确已停止，可人工确认未应用再行动；仅 index=expected 不够，还须工作区与 index 相符；不能从日志缺失判未执行 |
| Git 后、stdout/stderr 或 APPLIED 前 | 若 HEAD=base、index=expected、无额外 dirty/untracked、worktree 与 index 相符 | 补同一 operation 的观测和 APPLIED，零二次 apply；其他状态 UNKNOWN/人工，不先 reset |
| APPLIED、用户尚未 commit | 保留 target 实际 staged diff 与原候选 | 展示待提交，不作 COMMITTED、不自动生成 commit |
| 用户已 commit、finalize 前后 | actual commit 单一 parent=base、tree=expected、HEAD=commit、clean；candidate/批准仍一致 | L07 补 COMMITTED；相同 commit finalize 幂等，不同 commit 拒绝 |
| 外部编辑/其他 workstream/锁丢失 | 对比固定 operation baseline 和实际 Git | 停止并保留现场，未承诺撤销其他程序修改 |

target common-dir 锁协调 Dual 自身；不能证明用户并发 Git 被锁住。操作前/后重验可发现变化，检测到即停止，未知窗口不盲重放。

**no-change 决定：** 当前 capture 不接受零业务改动，因此本轮不新增“空 candidate 直接交付成功”的功能。APPLIED 且 commit 为空是正常待用户提交状态，不报损坏；目标已经变成 expected tree 也不能自动判已获本次批准。无业务改动任务若需要另一种交付语义，单独提交取舍；不得为填测试创建虚构 commit。

## 10. D10：逐字段保留与减负

| 字段组 | 本轮选择与 owner |
|---|---|
| schema_version/workstream_id/run_id/node_id | 保留现有严格 wire identity。示例仅从一次冻结 request 生成，validator 精确比对；不删校验、不代填错身份 |
| review_subject 的 producer occurrence/ref/SHA；proposal baseline id/SHA | 保留语义对象的精确选择。机器上下文提供唯一当前候选，但模型必须回应正确 subject；旧 latest/重复示例移除 |
| obligation_id / revision_responses.disposition/evidence | ID 程序提供，模型逐项回应，不能代填 ADDRESSED；原义务保留到复评确认 |
| planned_files path/CREATE-MODIFY/responsibility、plan steps/validation plan | 模型语义提案，程序严格 canonical/scope 核验；不能将模型建议当授权 |
| decision/summary/failure/category/verification_remediation | 仍须 typed envelope/领域校验。执行失败由程序事实决定，模型 category 不覆盖 transport/process 原因 |
| signature/ref hashes、当前 approved/reviewed 引用在多个 prompt section 重复 | AgentResultPromptContract 与现有 frozen context 是单一来源，其余 section 指向同一来源，不再另写相互矛盾的旧示例 |
| 程序 run/attempt/session/permission/billing/usage | 由程序生成、保留明确未知/不计价状态，绝不向模型索要或让模型回填 |

选择**不在本轮删除机器身份字段**，因未取得其删除收益超过跨层风险的证据；限定为移除实际重复 prompt 来源并保证 correction 使用当前冻结上下文。D10 的设计决定可以收口为保留严格合同，W07 的具体 prompt 差异仍按逐字段对照验证。不声称 token 已下降，也不把 GCTW 的历史负担抹去。

## 11. 冻结与验收的边界、整链反证

开工前要冻结：上文 schema/ownership/consumer/写集/失败处置和每个测试 oracle；需原生能力改变方案的证据（当前 D04 Claude 未具备）。开工后必须完成：实现的 typed 绑定、session 全入口、事务前缀、真实 query 一致性及模型/交付整链测试。后者不倒置成“零实现时先测试通过”，也不因为后移而取消。未知版本不受支持，旧样本不恢复。

| Declared by / invariant | Proven by（实施后） | Consumed by | Enforced before | Failure route | Cannot-bypass |
|---|---|---|---|---|---|
| D01/I1 动作与合法续作 | 决定→作者→复评→批准；三类+UNKNOWN 对照 | runtime facts→RecoveryAssessment | 后继 dispatch/安全续作提示 | 原因与配对/pending | 去配对失败，合法补足成功；只拒绝所有请求也失败 |
| D02/I2 原因保真 | E02 inline/worker + success/contained/真实坏输出 | 唯一 resolver→finalizer→ledger/read model | sidecar/成功路由 | 原执行原因不被缺产物改写 | 恢复旧 precedence 必失败；无条件禁发布被成功对照抓住 |
| D03/D04/I3 授权与只读 | 四变体×inline/worker、工具/模式/恢复成对测试 | qualification/transport/adapter | 第一写及每次 turn/session 变更 | 缺批准/限制回执拒绝 | GOAL 冒充、旧高权 session、链接/写还原/控制事实改写均拒绝；正常读可完成 |
| D05/I4 验收出口 | 合法机器/人工两样本、缺证据补足、REJECT 返工再验收 | compile/qualification/self-test/acceptance/delivery | 首次实施和验收/交付成功 | 环境缺项或返工，必需项不降级 | 人声明冒充机器、进程 exit0 冒充测试通过拒绝 |
| D06/D07/D08/I5 一致恢复 | L00–L07 每写点前/间/后 kill、冷 JVM、双 claim、并发 reader/holder | ledger sessions/runtime/worker/recovery | 新 side effect/一致读结论 | finish-forward / UNKNOWN 人工 / INVALID | 跳会话会混代的反证；重复 event、replay 发 provider/Git 必失败 |
| D09/I6 真实交付 | 原批准→Git 应用→实际 commit→finalize 各窗 | delivery/query/Git facts | apply/COMMITTED 宣称 | 观测可补/未知人工 | 只看 APPLIED/expected index 或旧批准不能成功；正常 finalize 幂等 |

所有批末都回查 §11 transition 与 sibling，不以文件数或“单层”标签规避评审。实施前的文件范围由这份映射与实际冻结选择生成；142 个候选不是自动批准修改清单。任何超出既有目标、authority、跨 provider 分工或需要引擎/权限/工具平台的变化，停止受影响工作交用户决定。

作者结论：**材料可提交冻结候选评审；不是全案冻结 PASS。** D04 原生 Claude 可用性和首错止损仍为显式阻塞；其余 D 项已补具体选择/映射/样例/落点，需 CC 判断是否足以冻结。没有新增产品技术债豁免。若 CC 发现 schema/写集/消费者仍留有实现者必须临场决定的语义，返回对应 D 项修订，不用局部代码试错代替设计。
