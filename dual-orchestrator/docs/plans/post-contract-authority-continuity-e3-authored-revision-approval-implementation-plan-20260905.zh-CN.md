# E3 Authored Revision And Approval Implementation Plan

状态：CC DELTA DIFF REVIEW PASS / GENERALITY TEST REQUIREMENTS SATISFIED / CHECKPOINT COMPLETE。
基线：E2 `a4e38ffe`，已评审并提交。2026-09-05 用户要求收缩 E3；当前执行范围以 §14 为准，§13 是收缩前进度，不是继续扩展历史审计的授权。未调用 provider，未恢复 PNBV/其他 real-case。

## 1. 主目标与必要性

目标属于主线 P0 正确性：让 design/plan 的每次修订有确定的上一版、评审和人工决定；让下游消费人实际批准的版本，而不是 session 记忆或某个最新文件。
验收是以下整条链，不是新增字段或某个 resolver 能读到数据：

`qualified contract → design → revision/review → human design approval → plan → revision/review → human plan approval → implementation/self-test（含全部再入）`

E1 已保证合同资格，E2 已拒绝设计声明越界。它们不能保证历史修订意见没有丢失、人工批准对应哪版候选、实现输入是否来自该批准。E3 填这三处断点。
E4 仍负责 plan.edit_files 对合同的 subset 校验、canonical-plan writer/metrics reader 减法及最终 downstream parity，不在 E3 偷渡。

收益：机械消除丢失历史回应、用错父候选、未批准版本被下游使用。不能保证 LLM 语义一定正确、零 revision 或产品一次完成；逐条回答不等于正确解决。

## 2. 本轮代码核查与边界图

| 已读代码 | 当前事实 / E3 挂钩 |
| --- | --- |
| `WorkflowIterationDocument`、`RevisionTracker.nextRevisionIteration` | 已有 parentIterationId、triggerNodeInstanceId、triggerAction、targetNodeDefinitionId；reason 为呈现文本，不能代替 gate note/source |
| `RoutingEngine` :244/:315/:331、resolveOpenGate | gate node、GateDocument、node_routed 已含 source_node_instance_id；subject_input_refs 存在；非 gate 路由事件不含 source occurrence id，不能假定每条事件都有它 |
| `RoutingEngine.resolveOpenGate` | design revise 要求非空 note；plan revise 尚无对称要求，须在同一 ledger owner 补齐，不能只在 CLI 检查 |
| `ReviewDecisionHandler` | decision 消费 sidecar，沿 inputRefs 路由；human gate 常拿到 reviewer outputs，而非 author candidate |
| `AgentOutputLineageVerifier` | 已验证 sidecar/source bytes、node/run、COMPLETED、成功 execution 与 ledger SHA；继续复用，不新建一套成功判据 |
| `CompletionEvidenceLineageResolver` | 已有 reviewer→context→上游 evidence 的回溯先例，但其函数服务 completion；不把 E3 混入 completion owner |
| `ContextBuildPackageHandler` | E2 架构 subject 唯一；metadata 对非 acceptance 不保证进入 outputRefs。因此 E3 不照抄“必须同时路由 md/json”的 acceptance 专属假设 |
| `AgentRunGovernanceContext.ensureQualified`、`AcpxAgentTaskHandler` | 每个 execution 都经过前置，即使没有 context node；适合统一把关 plan/implementation/self-test，不能只挂在 context.implement_claude |
| `PromptManifestWriter`、`AgentRunDocument.promptManifestPath` | 已有 run-scoped 输入来源载体；当前只有 section hashes，不含 E3 身份；优先扩充该载体，不另造 baseline/binding 文件体系 |
| `ArtifactContractValidator.withResultSidecars` | machine-owned sidecar 由 validator 发布并由 node/execution SHA 绑定；E3 在此前验证 revision 和实际输入身份 |
| `TaskContractRevisionContextResolver` | intake 专用，不能直接套用其上一轮 obligations 语义；E3 累计 design/plan 历史，保留 intake 实现不动 |
| `HumanReviewGateHandler`、ReviewShowCommand、WorkstreamQueryService、console、mgt | handler 产 MD/JSON packet；CLI 原样打印 MD、API 透传、UI 选择字段，必须覆盖结构化卡片，不能只让隐藏 JSON 有字段 |

职责：definition 拥有合法拓扑；ledger 拥有 iteration/gate/action；runtime 拥有既有 artifact contract 的资格解析与校验；adapter 只装配执行输入及留痕；CLI/API/UI 只呈现 packet。
编辑决策：可进入计划评审，不能直接实施。无需新模块、workflow 节点、domain approval、事件总线或通用 evidence 平台。

### 四个 definition 实测

| Definition | 节点/边 | design 修订 | plan 修订 |
| --- | --- | --- | --- |
| contract | 34/56 | human revise | reviewer-driven + human revise |
| full | 34/56 | human revise | reviewer-driven + human revise |
| terminal-delivery | 31/52 | human revise | reviewer-driven + human revise |
| small-plan-human-review-experiment | 28/47 | human revise | direct candidate → human gate → revise，无 plan reviewer |

四者 implementation context 的入口恰为七种：plan approved、SELF_TEST_INCOMPLETE、human_implementation_repair revise、REJECT_IMPLEMENTATION_BUG、REJECT_CONTEXT_INSUFFICIENT、REJECT_TOOL_LIMITATION、human_acceptance revise。
self-test 另有 implementation completed 与 REJECT_VERIFICATION_INSUFFICIENT 直接入口，后者不经过 context.implement_claude。

## 3. 最小方案选择与通用性判断

选择既有 ledger/artifact 上的有界 resolver + typed response coverage + execution 强制消费。不只加强 prompt，也不增建独立 approved-design/approved-plan/baseline 权威文件。

| 备选 | 裁定 |
| --- | --- |
| 只把最近 human note 放回 prompt | 不保累计意见和版本身份，不能解决根因 |
| 保留 session 或读取 latest design/plan | runtime retry/session 与 semantic iteration 不等价；未批准版本可能混入 |
| 现有 lineage 重建 + 现有 manifest/sidecar 留痕 | 选用；source 仍为 gate/iteration/candidate/review，投影不拥有批准 |
| obligations 自动语义评分/逐项 reviewer assessment schema | 不做；会增加第二语义判断 owner，仍不能机械证明业务正确 |

自身判断：在已注册的 design/plan 文件交付阶段具有跨业务通用性；不是 OKX 专用实现。输入只含 workflow/occurrence/refs/SHA、typed must_fix、human note、已批准候选；不识别业务类名、API、数据库、目录语义。
适用限制：不承诺任意改名的 custom agent 节点自动具备此协议，不扩成任意 artifact DAG/并行审批框架。同一 stage 多候选或多来源无法唯一解析时拒绝，不排序猜测。

实施前与实施后各做一次通用性核验：
- 文档转换工具与静态站点两类任务，替换 objective、文件名、review/note 文本，跑同一完整批准/修订/消费链；仅更新业务内容不应改变路由规则。
- 同步重命名 contract/candidate 文件仍通过；仅替换候选、打断 lineage 或漏掉任一历史回应，两类业务都必须同样失败。
- reviewer-driven、human-reviewed、direct-human 三拓扑分别正反验证；直接人工路径不虚构 reviewer。
- registered stage 的 config 缺失/错配不得静默跳过；未知 custom 形态拒绝或明确不适用，不冒充 ENFORCED。

## 4. 唯一身份与有界解析

新增 `AuthoredStageLineageResolver`，限定 DESIGN/PLAN 两阶段；内部 record 承载 candidate、approval、revision context。复用现有 registry 类型识别和 gate_purpose，集中映射，禁止在 handler/UI 各自写 node-id 判断。
若需辅助 validator，限 `AuthoredRevisionValidator`；不增加通用 stage registry 或一组 opt-in flags，使现有注册节点无法因漏配置而绕过。

### 4.1 Candidate / review / gate

candidate identity = workstream、node occurrence、run、source type、sidecar ref/SHA，并复验既有 source output/node/execution 链。
gate identity = gate ref、source_node_instance_id、subject_input_refs、动作、关联 node 与事件顺序；只有完整 resolved approved + node COMPLETED + 已完成路由事实才有批准效力。

两条解析路径必须与冻结 definition 一致：
1. reviewed：gate source decision → 唯一 reviewer sidecar → reviewer node.inputRefs 中的 package ref → 唯一 completed context publisher → context inputRefs 中唯一 author candidate；reviewer decision 与 decision node 要一致。
2. direct：gate source 是 declared author，subject refs 直接包含唯一 candidate；该 definition 确实声明 direct edge 才允许。

定位 context publisher 使用 persisted node_routed 中的 occurrence ids，再按实际 outputRefs 精确匹配 package ref、handler/target/profile 与 declared edge；不从文件名拆 node id，不扫描 runs/nodes 目录取 latest/mtime。不存在或多匹配均失败。
每跳检查 canonical/realpath、workstream、合法 iteration 关系、node/type、completed/execution、input/output refs 与 SHA。相邻 review/candidate 同 iteration；跨 semantic revision 只可通过明确 parent/trigger 链，不要求所有下游都与批准 gate 同 iteration。

### 4.2 Approval 与当前合同

批准不由正文或 reviewer READY 推断。当前消费者沿自己可见的 persisted history 定位当前合同资格下的阶段 approval，按事件数值顺序检查最后相关 gate；open/revise/损坏的后续相关 gate不得回落较早 approval。
新生成但未批准的 candidate 不替换已批准版本；若新一轮阶段审批已经开始，则不沿用早先批准偷跑。历史审计使用 occurrence 的固定身份，不拿未来事件倒推过去输入。
合同资格身份必须包括 compile occurrence、approval gate/event、contract/resolution refs 与 SHA，不能只比合同 bytes；recompile/reapprove 即使 bytes 相同，也是新的授权期。
approved design 与 plan 的输入身份需匹配该合同授权期；plan 还须匹配所依赖的 approved design。更换合同或 design 后不得把旧 plan 解释为新批准。
本轮不把旧 stable contract 的历史 bytes 变成永久归档保证；原有历史工件已不可重建时明确 fail-closed，不补写旧证据。

## 5. 修订基线和回应协议

从当前 author occurrence 的 iteration/trigger 与实际入边确定 initial/revision，不用全局 iteration number、stageId 前缀或 reason 文本推断。
尤其：plan 初次执行可能位于 design revision 后的 iteration，不得误判为 plan revision；implementation repair 不开启新的 design/plan 修订链。

revision baseline 包含 parent candidate、触发 reviewer/decision 或 resolved human gate、human note（仅 human route），以及本阶段本合同授权期内累计 active obligations。
- design human revise：验证 gate/source/subject、非空 note，收集该 reviewer 的 must_fix 与 note。
- plan reviewer-driven：验证 completed plan.decision NEEDS_REVISION、reviewer 与 parent；无 human gate/note，不伪造。
- plan human revise：同 design；direct experiment 没有 reviewer，只产生 human-note obligation。
- 沿 parent iteration 与 candidate lineage 回溯到阶段初始边界/前次合法关闭点；重复节点/cycle/缺失父节点/错 stage/错合同身份立即拒绝。

obligation id 沿已评审 sweep：`sha256:<review-sidecar-bytes>#must_fix:<zero-based-index>` 或 `sha256:<resolved-gate-bytes>#human-note:0`，同时保留独立 source ref 与原文。
按发生顺序呈现，不按文本排序/相似度合并。相同文本来自不同 source 仍是不同事实；相同 source/id 只计一次。

DesignResult 与 PlanResult 均新增顶层 `revision_responses[] = {obligation_id, disposition, evidence}`；design 不嵌套一份同义 responses。
复用不了 intake 的 `RevisionResponse(reviewObligationId,resolvedByClauseIds)`，因为 design/plan 没有 clause-id owner；新增同形共享 `AuthoredRevisionResponse`，只供这两种 result。
`disposition` 固定两值 `ADDRESSED` / `NOT_ADDRESSED`；`evidence` 为非空说明字符串，供评审而非机器认证，不解析自然语言或文件名。NOT_ADDRESSED 允许如实表达未解决，不新增 BLOCKED decision、不强迫模型撒谎。
initial 必须显式 `[]`；revision 必须 exact-cover active IDs，禁止遗漏、重复、未知 ID、空说明。shape/parser/initial/correction/example/projector/reader 同批更新；其他 result 不接受此字段。

所有历史 obligation 在 human approve 前保持 active，每版都需重新回应；ADDRESSED 不关闭义务。reviewer 继续既有 decision/must_fix，不新增逐项评分状态。
human approve 是关闭动作，但只证明人接受了该候选，不证明每个语义问题已经消失；revise/note 不可修改合同授权，矛盾意见应显式留给人工裁定。
本轮 reviewer 新产生的 must_fix 不可能已在当前 candidate responses 中：gate 分别显示“该候选应覆盖的历史项/其回应”和“本轮新发现/尚未回应”；若人 revise，新项进入下一版 baseline；若人 approve，表示知情接受。不得把这种时间差误判为 candidate 格式错误。

## 6. Execution 消费闭环与落盘位置

### 6.1 单一输入快照

`AgentRunGovernanceContext.PreparedContext` 携带 resolver 生成的 immutable authored context：当前合同资格、所需 approved design/plan、revision parent/trigger/obligations 或 reviewer 的唯一候选。
不直接让 adapter 选择版本。初始 plan 和每次 plan revision 都需 approved design；每次 implementation/self-test 都需 approved plan，并验证其上游 design/contract 身份。
不仅注入 ref/SHA：plan 必须收到被批准的 design 内容，实现/自测必须收到被批准的 plan 内容；revision/reviewer 必须收到 parent/候选内容和完整 active obligations。均以已验证来源生成 required typed section，不经普通摘要或 session memory 替代。
manifest 的 required section/sourceRef/hash 必须与 PreparedContext 的确定性渲染匹配；删掉正文输入却只留下身份字段也必须被测试拒绝。

选择扩充既有 prompt manifest 的 `authoredContext` 字段作为 run-scoped 输入来源快照；只存结构化 refs/SHA/obligation source identity，不重复存大段全文。baseline prompt 正文从已验证 sources 重建。
manifest 写入完成并读回校验后才允许 provider；记录 workstream/node occurrence/run 与 required section hashes，canonical ref 限于该 run prompts 目录。以既有 run.json.promptManifestPath 定位，不能让模型提供任意文件路径。
manifest 是派生证据，不是第二批准 owner；没有 gate/iteration/source 验证，仅有一个看似完整的 manifest 不能获权。
正常执行期间 manifest 不覆盖；写入失败/半写不启动 provider。发布成功 sidecar 前再重算 SoT，确认与该 execution 冻结快照完全一致，不能在 run 结束后给输出绑定一个更新的 approval。

### 6.2 输出、冷启动与 correction

`ArtifactContractValidator` 对 design/plan 校验 response coverage，对所有 E3 消费角色验证实际输入快照，然后才发布 sidecar。
sidecar 增加 machine-owned `authored_context` reference（manifest ref/SHA）；该字段不由 LLM envelope 控制。sidecar 本身继续由 node/execution 的 SHA 绑定。reader 校验直接候选的 sidecar/manifest SHA、workstream/node/run 身份与当前合同/批准设计的匹配；不递归重演祖先 prompt 或依赖历史 run.json 的 manifest locator。当前 execution 仍在发送前和发布前校验完整输入。详见 §14。
引用方向固定为本次 sidecar → 本次输入 manifest → 前序 authoritative sources；manifest 不引用本次输出 sidecar，不存在相互包含对方 SHA 的循环。评审中的“双向引用”不作为新增实现要求。
为避免 adapter/runtime 反向依赖，manifest 中 E3 payload 的类型/校验归 runtime，adapter writer 只序列化；runtime 不导入 adapter AgentRunDocument 类型，按已有 JSON reader 读取指定引用并校验身份。
initial 与同 run correction 使用同一个 PreparedContext；当前 correction prompt 主要含 schema 契约，E3 必须补上同一基线所需 IDs/parent 引用及内容，不依靠 session 记住，不改变 correction 次数/资格。
execution retry 重建输入快照但不得偷偷选新语义版本；若该 node 的上游授权已变则 fail-closed。retry 不增 obligation，不作为 semantic revision。
删除/损坏未完成 run 的派生快照：可从 SoT 为新的合法 execution 重建相同来源 IDs；成功 sidecar 已绑定的 manifest 缺失/篡改：审计拒绝，不重写历史文件让它重新通过。

### 6.3 真正强制的挂钩

- context build：design reviewer、plan reviewer、implementation 的 required typed sources 无缺失/歧义，完整投影，普通预算裁剪不能删掉这些事实。
- 每个 agent execution：ensureQualified 之后、provider 之前统一检查；覆盖无 context 的初始 plan、direct self-test、prepare-only、cold start。已有 memory/compaction 只能补充，不能代替 required authority。
- output publication：重验 execution 所用来源与 response IDs，失败无有效 sidecar、无下游 reviewer/业务节点。
- reviewer context：投影 parent/active obligations/current responses，plan reviewer 与 design reviewer 对称；候选 claim 不自动消除义务。
- human gate：同一个 resolver 生成 packet 中唯一 candidate identity 和历史视图；下游仍从 gate/source/sidecar 复验，不能从 packet Markdown 获权。

旧 authored sidecar 缺 E3 输入身份时，不能自动“补成”当前批准；新协议下执行失败并保留历史只读证据。冻结旧 workstream 不自动迁移、不恢复。其他非 E3 角色和 intake 协议不被全局收紧。

## 7. Human Authority 与呈现 sibling

RoutingEngine 在任何持久化动作前对 gate_purpose=plan 的 revise 增加非空 note 检查，与 design 对称；批准、预算、自动 reviewer revision、human-noted budget override 原语不变。
不新增 approval API 参数、不自动替用户决策、不借 packet 发明新的审批 token。完整性在 downstream provider 前由同一 resolver fail-closed，不能声称已经获得跨文件原子事务或消灭 R1 liveness。
E3 验证的是既有 gate resolution 的动作与来源，不新增真人身份认证；不能仅凭 human gate 名称证明某次 approve 一定由真人操作。既有 autorun 能力不扩张，后续 real-case 仍要求用户本人裁定并禁止 autorun。

design/plan packet 的 MD 与 JSON 同源呈现：candidate ref/SHA/run、design planned_files 或 plan edit_files、上游批准身份、历史 active obligations 原文/来源、当前 responses、本轮新 must_fix，清楚标注 NOT_ADDRESSED 与缺回应。
同一 handler 内 latestAgentDecision/输入摘要/建议动作对这两个 gate 改消费唯一解析结果，不能主卡片仍选另一份 latest review。
CLI review show 和 API 继续消费 packet；console 与 mgt 的结构化卡片同步展示上述关键信息，正确 escape，历史只读。两份不同 revision packet 必须能明确区分，不隐藏在原始 JSON 后面。

## 8. Failure / Lifecycle / 范围禁令

| 情形 | 处置与被阻止的行为 |
| --- | --- |
| 无所需批准、wrong epoch、缺 parent/review/gate、歧义或 hash 不符 | provider 前 FAILED；沿既有 REQUIRED_AGENT_EVIDENCE_MISSING 形态带 authored reason/details，不能误标 session bootstrap/network |
| responses 不完整/非法、输出时输入 identity 漂移 | 既有 AGENT_OUTPUT_CONTRACT_VIOLATION + 具体 parse_error code；无有效 sidecar、无下游路由 |
| gate lineage 不可重建 | 不发布伪完整 packet；下游不认可该 approval，不回退较早 gate |
| human revise note 为空 | ledger 在写入前拒绝，原 gate/current 不变 |
| reviewer 正常提出新意见、NOT_ADDRESSED | 保持既有 semantic decision/human 裁定，不误作 execution failure |
| runtime retry / abort / completed | 沿既有生命周期；retry 不改语义历史，abort/terminal 不启动新调用 |

错误详情限定缺失身份/引用，复用当前失败处置与人工检查入口；不新增自动 repair、recovery action 或重试策略。实现测试须确认 CLI/API 不把 authored failure 显示为“重建 session 即可解决”。
禁止业务字面量、语义 parser、通用 evaluator、自动 obligation 去重/评分、独立 binding 文件、新节点/新模型 provider turn、network/permission/fork 改动。
不提升 revision cap，不削弱 human authority，不默认引入新 approval gate；对图不支持的拓扑直接拒绝，不加“试着继续”的 fallback。

## 9. 文件范围与删除/保持清单

| Owner | 预期修改面 |
| --- | --- |
| Runtime 语义 | 新 bounded AuthoredStageLineageResolver、AuthoredRevisionValidator；既有 envelope/validator/prompt/projector/sidecar reader；AgentOutputLineageVerifier 对 E3 引用的验证 |
| Runtime 消费 | AgentRunGovernanceContext、ContextBuildPackageHandler、ContextPackageSectionRenderer、HumanReviewGateHandler；EvidenceManifest 的 E3 来源投影（不作为选择器） |
| Adapter 装配 | AcpxAgentTaskHandler、AgentPromptRenderer、PromptManifestWriter、必要的 correction 输入装配、四个 design/plan prompt；不改 transport/fork/permission |
| Metadata 传递 | AgentRunMetadataWriter 明确纳入 sweep：prepared/completed 两路继续写既有 promptManifestPath；新 authored context 留在该 manifest，不在 run.json 复制第二份 payload，不传入 memory。若 PreparedContext 签名变动，仅作必要接线调整 |
| Memory 保持 | WorkstreamMemoryUpdater 不改变既有投影语义；保留 E1 qualified contract 与现有 decision/summary/open-issue 投影，不新增 active obligations、revision responses 或 approved design/plan 的权威副本 |
| Ledger | RoutingEngine 的 plan revise note 前置，仅此；不改 iteration schema/event 模型/预算/动作归一化 |
| Presentation | console/mgt 卡片；CLI/API 原样 packet 路径以测试钉住，只有发现字段丢失才改透传 |
| Definition/registry | 复用现有 registered role + gate purpose；loader 对支持的三种拓扑与 required producer/consumer 关系做一致性校验，不新增图或第二定义 |
| Tests | 相应 runtime/adapter/ledger/UI/parity tests + 一个完整 authored lifecycle sequence fixture |

E3 移除这些角色的 latest/按输入顺序择一与只靠 session 的输入路径。canonical plan writer/metrics reader 暂留明确归 E4，E3 resolver 不读取它们。
不得为一次 hash 校验提取通用 artifact framework。若既有 manifest 不能承担单次输入留痕而必须新增独立权威文档，停止回比较，不在实施中临时扩大。

## 10. 必须通过的全链测试

1. 双业务正常路径：E1 approval→E2 design→review→human approve→plan→approve→implementation→self-test；fake 只替 provider，真实 runtime/ledger/validator/packet 运行，所有调用记录同一上游身份。
2. design 连续两次 human revise：历史 must_fix/note 全部仍 active；交换两类问题出现顺序，一样要求累计 exact coverage；prose 声称合规不能绕过漏 ID。
3. plan 三拓扑独立 sequence：reviewer-driven、reviewed human、direct human；不存在 reviewer 的直接链仍可完成，且不能从别的 occurrence 捡 reviewer。
4. initial plan 位于 design revision 的 iteration，仍正确判 initial；implementation 修复 iteration 不被当作 authored revision。stage/contract 边界不串义务。
5. approved design→每个 plan occurrence、approved plan→七种 implementation 入口及 direct self-test；删除/换成未批准候选后 provider=0；不是只测 context method 返回失败。
6. 新旧 candidate、open/revise/损坏 gate、多个 ref、跨 workstream/iteration、篡改 source/sidecar/manifest、伪造 completed、缺执行成功证据全部拒绝；较早 approval 不回退。
7. 合同 recompile/reapprove（含同 bytes 新 occurrence）使旧 authored approvals 不适用；新设计/plan 重新合法批准后能正常完成，不能只测失败。
8. initial/correction 同 baseline；执行 retry 不改变 parent/obligation、缺身份不走 session 修复；prepare-only 不算 provider 成功证明。删除治理输入挂钩→零调用反例必须红。
9. 冷 JVM 重建来源 identity/obligation IDs 相同；未完成 projection 半写不启动 provider；当前直接消费的成功 sidecar 所绑定的 manifest 删除必须失败，即使 latest/stable 文件齐全也不能替代。已被替代的祖先 manifest 不再是下一 execution 的递归准入条件。
10. gate MD/JSON/CLI/API/console/mgt 同源；两次 revision 的 refs、文件、历史回应、新发现清楚区分；本轮新 must_fix 没有回应不误作格式失败，下一轮必须进入 baseline。
11. plan 空 note 写入前零状态变化；design 现有规则、ordinary BLOCKED、human budget override、reviewer budget/abort/terminal 全回归，autorun 不新增 revise 能力。
12. 两类业务替换/一致路径重命名/同类违规拒绝；四 workflow parity + 错配 custom registered 形态 fail-closed；无业务字符串 production 判断。
13. 突变：删 coverage 校验、删 approved-plan guard、恢复 first/latest fallback、删 packet/required context 投影，各自必须有失败测试；尤其只留 ref/SHA 而不把批准正文送入实际 prompt 时消费者测试必须失败。另设删除 manifest 读回校验的反例：模拟 writer 返回成功但落下完整 JSON 的错误 run/source identity，provider 必须为零；此项与半写/异常测试分开，不用纯 grep 冒充行为证明。
14. full reactor 含 server、fresh package、四 workflow validate、diff-check；不以局部绿色或 resolver 直接调用替代上述 lifecycle sequence。

Metadata/memory sibling 保持性测试：prepared/completed 的 promptManifestPath 指向同一 execution 载体；扩展 PreparedContext 后 E1 contract 传递不退化；处理含 E3 字段的有效 sidecar 后，memory 不出现新增的 obligations/responses/approved-artifact 身份集合，原有摘要等行为保持。

以上是完整验收清单，不能将其中已通过的子集等同为整体完成。当前执行记录见文末。E1-E4 完成后的自然 fresh real-case 再验证业务收敛与产品完成，不人为制造 revision。

## 11. 六维闭环与诚实限制

| 维度 | E3 必须达到 |
| --- | --- |
| Declared by | 既有 stage contract + revision_responses；合法 workflow gate/decision/iteration |
| Proven by | candidate/reviewer/source bytes + 成功 execution；gate action/source；execution manifest 与 sidecar 引用 |
| Consumed by | authored resolver 唯一出具视图；author/reviewer、human packet、plan/implementation/self-test 强制消费 |
| Enforced before | provider、有效 sidecar 与 required packet publication；不是事后提示 |
| Failure route | 既有缺 required evidence/contract violation/ledger 拒绝，typed 明示原因，零下游调用 |
| Cannot-bypass | 跨业务完整 sequence + 三修订拓扑 + 全再入 + 冷重建/破坏/突变正反例 |

残余：自然语言证据可能虚假、模型可能重复犯错；human approve 可接受尚存问题；长 human revision 链会增加上下文，不声称零成本或自动终止语义振荡。
沿现有 revision/timeout/人工止损，不新建语义评分器或让历史义务随预算裁剪消失。不可满足或持续反复时显式停下，不能靠调提示词无限重跑。
文件账本非跨文件事务，崩溃只能承诺 fail-closed，不能承诺 R1 自动恢复。E3 不代替 E4 的实际 scope 与交付验证。

## 12. 自身评估、顺序与停止条件

自身设计结论：PROCEED TO IMPLEMENTATION。独立架构评审 PASS（P0=0/P1=0/P2=3）；通用性在规则与已核查三拓扑上成立，实施前已复核 metadata→memory 的既有传递链。实现通用性和闭环尚未验证，不能标 ENFORCED。
比上一轮 sweep 更具体的必要增量：plan note ledger 前置、manifest 输入留痕且消费前再验、current review 新发现的时间语义、两个 UI packet sibling；都是同一事实从产生到消费的缺口，不是新功能目标。

顺序：本计划独立架构评审 → 吸收阻塞项 → failing sequence tests → resolver/manifest/response/publication/消费者同批实现 → UI/ledger 最小 sibling → full tests 与自身通用性复核 → 独立 diff review → 单一选择性提交 → E4。
不先提交“只有 baseline producer”的半闭环供下游使用。若实现必须重写 routing、修改 human 决策权限、造通用 schema/platform 或新独立 binding authority，停止回到比较；不让用户再次被动纠偏。

三项 P2 已吸收：明确 memory 语义保持、点名 metadata writer prepared/completed 传递、增加读回校验独立突变反例。只修订计划，不因此扩展生产职责或请求第二轮计划评审。

Implementation READY 是实施前设计结论，不是本次代码提交许可。provider/real-case 仍未授权于本计划。

## 13. 实施进度（2026-09-05，未收口）

已开始实现：两个 authored stage 的累计义务与批准来源解析、typed responses、既有 prompt manifest 输入快照与读回校验、sidecar 发布前复验、required context、plan revise note 前置、MD/JSON/UI 投影和 loader 三拓扑一致性校验。未新增 workflow 字段、独立批准 artifact、网络能力或业务特判；E4 canonical-plan 减法未动。

已独立执行的聚焦检查：
- AuthoredStageSequenceTest 6 项：两类业务正常链、连续 human design revise、三种 plan 拓扑、删除已批准输入 manifest、plan 空 note 零状态变化。
- AuthoredRevisionContractTest 3 项、AuthoredRevisionCoverageTest 2 项：显式空数组、封闭 schema、未知/遗漏/重复 ID、NOT_ADDRESSED。
- AuthoredManifestDispatchTest 3 项：writer 正常返回但完整 JSON 身份错误、仅保留 refs 而缺 required 正文，两者 provider=0；prepare-only 与 metadata 单一 manifest。
- AuthoredDefinitionTest 2 项：四份 definition 加载、五类错配声明拒绝。
- E2 TypedDesignScopeTest 8 项已补齐新输入证据后全部通过；AgentResultPromptContractTest 12 项与 WorkflowDefinitionLoaderTest 14 项通过。
- console/mgt 的 JavaScript 语法检查通过；这不替代 UI 行为测试。

上述序列使用真实 runtime/ledger/validator/gate，但 provider handler 是测试替身；只有 manifest dispatch 反证进入真实 adapter 装配。不得将它们描述成所有角色均完成真实 adapter 全链测试。

当前仍未满足提交条件：全量回归尚未通过，既有 positive fixtures 需逐项迁移并确认没有真实回归；七类 implementation 再入、同 bytes 新授权期的正反完整链、retry/correction、冷进程、全部 reader/publication 的破坏反证、metadata/memory 与 CLI/API/UI 行为 parity 尚未完成整套验证。recursive source 验证还需专项 cycle/重复遍历审查。不得删除失败测试、放宽生产守卫或用局部绿色声明 ENFORCED。

最新完整命令 `mvn -fae test`（2026-09-05 22:02 +08:00）实际为 BUILD FAILURE：core/ledger/flow/git 通过；runtime `607 tests / 12 failures / 4 errors / 0 skipped`；adapter、CLI、server 因依赖失败未执行。失败类为 ArtifactContractFixtureRegressionTest、ArtifactContractValidatorTest、ContextBuildPackageHandlerTest、BuiltinHandlerTest、WorkflowIntegrationTest、SmallWorkflowProfileGateIntegrationTest。可见缺少 manifest/approved lineage 和旧 sidecar payload；逐项迁移与真实回归排除尚未完成，不能将这些失败一概归为“只是测试问题”。前一次全量的 `603 / 16 / 5 / 0` 是历史失败记录，不是当前统计。已通过的 adapter 三项是独立聚焦运行，不是本次全量包含的结果。

上述为收缩前记录。下一步改按 §14 收缩边界与处理回归；当前不进入 E4，不更新真实样本运行工件，不恢复 real-case。

## 14. 用户纠偏后的执行范围（2026-09-05）

目标没有变化：修订收到正确父候选和累计意见；下游只消费当前有效批准的设计/计划。必须删除的是把“每层都重新证明整个历史”当成实现前提的做法，不是批准身份或发送前守卫。

本次减法限定一个 production 文件 `AuthoredExecutionContext`、既有 `AuthoredStageSequenceTest` 和本计划/active plan。不新增模型、平台、状态、依赖或通用审计器，不调整 workflow/权限/provider。发现其他边界不能在这次减法中顺带实现。

保留的责任分工：
- resolver 根据既有 ledger/gate/iteration 与验证过的 sidecar 选择本轮输入；累计意见仍沿真实修订来源重建。
- adapter 发送前校验本轮 manifest 身份与完整 required 正文；validator 发布 sidecar 前复算当前输入与回应覆盖。二者分别拦住 provider 调用与无效输出发布，不能删。
- 消费已发布候选时，从 machine-owned sidecar 的 ref/SHA 直接读取其冻结输入身份，检查直接依赖的合同与批准设计是否匹配。不回读旧 run.json 的 locator，不重渲染旧 prompt，不递归遍历其所有输入 manifest。
- 审计材料仍保留；本次没有删除历史工件的产品功能。历史审计完整性不再冒充所有后续 execution 的准入条件。已批准版本的直接绑定缺失/篡改依然拒绝，不使用 latest/global fallback。

成本与取舍：信任既有 validator 已发布且被 execution SHA 绑定的候选，不在每一 reader 重新实施发布校验。代价是后续启动不再发现所有祖先 prompt 的事后损坏；不声称全历史完整性证明。契约/批准来源、直接 sidecar/manifest、当前 prompt 的校验保留。

执行证据先于实现：新增两个真实 runtime 序列在旧实现下均失败；减法后通过。它们分别删除已替代祖先的 prompt/manifest，以及删除当前批准候选的旧 prompt 与 run.json locator（保留其绑定 manifest），随后走完 plan approval → implement → self-test。既有“删除直接批准候选 manifest → plan provider=0”反证保留；adapter 的错误身份/缺 required 正文仍为 provider=0。聚焦合计 52/0/0/0；不等同 E3 全量通过。

当前停止决策：本次只交付上述减法及回归结果，不因为局部绿色进入 E4、提交、provider 或 real-case。全量若仍红，下一执行单元只能逐项定位合法路径回归；不得用扩大审计、修改验收语义或新增机制来绕过红灯。

收缩后完整回归（2026-09-05 22:20 +08:00）：`mvn -fae test` 仍为 BUILD FAILURE，runtime 609 项 / 12 failure / 4 error，与收缩前同六个失败类；core/ledger/flow/git 通过，adapter/CLI/server 被依赖失败跳过。新增两项序列均通过。`git diff --check` clean。这次减法没有解决原有 16 项回归，不可提交或声称 E3 ENFORCED；未把任何旧 positive 改为 negative，也没有删除失败测试。

Codex 能力边界（2026-09-05 官方文档核验）：[AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md) 是进入 prompt 的指令；[命令规则](https://learn.chatgpt.com/docs/agent-configuration/rules) 控制特定沙箱外命令的 allow/prompt/forbidden；[沙箱/审批](https://learn.chatgpt.com/docs/agent-approvals-security) 限制技术操作与何时申请权限，不判断设计是否必要。当前任务没有配置“E3 最小范围”的独立工具拦截器；这段范围声明也是执行纪律，不能标为机械 ENFORCED。已有测试能机械拒绝边界回归，但不会自动阻止 coding agent 写下一行代码。不为弥补这一限制另造治理平台；本轮以实际缩减改动和停止后续阶段履行决定。

## 15. 合法路径回归迁移（2026-09-05，仍未收口）

本执行单元没有修改 production。复用既有 AuthoredStageSequenceTest 的真实 runtime/ledger 前序流程，为 runtime 旧正例补齐合法合同、设计/计划批准与当前 execution manifest；被测输出仍由原测试控制，原有正反例断言保留。small-workflow 测试改为从真实设计前序推进到 plan，并删除测试中跳过 human plan gate qualification 的配置改写。没有新增 Maven 依赖或测试平台。

已验证：
- 输出校验与序列聚焦合计 `31/0/0/0`；上下文/handler/integration 聚焦合计 `134/0/0/0`。
- 完整 `mvn -fae test` 于 22:36 +08:00 结束：runtime `609/0/0/0`，原六类的 16 项回归全部关闭；core/ledger/flow/git 同样通过。
- 全量继续进入此前被跳过的 adapter，暴露 `AcpxAgentTaskHandlerTest` 的 15 failure / 6 error，以及 `WorkstreamMemoryUpdaterTest` 的 1 failure，共 22 项；CLI/server 被依赖失败跳过。结果仍为 BUILD FAILURE，不可提交，不可宣称 E3 ENFORCED。

adapter 可见诊断包括缺 workflow/definition.json 和缺当前 run.json/manifest，表明多个旧正例尚未构造 E3 执行前提；这只是已确认的失败位置，不足以把全部失败定性为 fixture 问题。下一执行单元逐项区分：测试输入缺失则补合法链；真实调用路径被误拦则回到同一边界定位。不得删除正例、把成功预期反转为失败、降低 publication/provider 守卫，或为消除红灯另造通用测试平台。

本次仅证明 runtime 旧合法路径在新输入约束下仍成立。§10 全链验收、adapter/CLI/server 回归、自身通用性复核及独立 diff review 仍未完成；不进入 E4，不更新 real-case 工件，不调用 provider，不恢复或创建样本。

## 16. Adapter 的 22 项回归明细与处理（2026-09-05）

22 是失败用例数，不是 22 个独立生产缺陷。前 21 项来自 AcpxAgentTaskHandlerTest，最后一项来自 WorkstreamMemoryUpdaterTest；原始统计为 16 failure / 6 error。六项 error 是上游提前返回后测试访问缺失资料的后果，不能据此称为六个生产空指针缺陷。

| 用例 | 被测行为 |
| --- | --- |
| writesPromptAndReturnsPreparedWhenConfiguredForPrepareOnly | prepare-only prompt/metadata |
| normalRunWritesPreparedMetadataBeforeLaunchingAgentProcess | provider 前 prepared metadata |
| prepareOnlyRunWritesSessionCompactionSeedWhenExperimentEnabled | implement-boundary seed |
| prepareOnlyProviderCacheAwareWritesCompactionSeedWhenStabilitySignalTriggers | cache-aware seed |
| runScopedSessionRemainsOptIn | session scope 选择 |
| adjacentE1ExecutionsDoNotReuseAConflictingPolicySession | 相邻 occurrence policy/session 隔离 |
| nodePermissionProfileIsPassedToAcpxTransport | profile 透传 |
| implementationAgentNodeRunsWithApproveAllWhenConfigured | legacy approveAll |
| namedImplementProfileOverridesLegacyApproveAllFlag | named profile 优先 |
| typedPublicWebSearchApprovalAddsOnlyExactToolForOneRetry | exact WebSearch 授权 |
| prosePrefixedFinalMessageUsesOneCorrectionAndOnlyCorrectedEnvelopeRoutes | prose correction |
| schemaMissingFieldUsesOneCorrectionAndOnlyCorrectedEnvelopeRoutes | schema correction |
| schemaCorrectionStillInvalidCannotRouteAndDoesNotStartThirdPrompt | correction 上限 |
| deniedOptionalToolWithProseWrappedFencedArtifactCompletesWithWarnings | 非必需工具拒绝与展示恢复 |
| deniedOptionalToolWithOnlyMissingRootClosureCompletesWithWarnings | root closure 与 warning |
| treatsNonZeroExitAsCompletedWhenStdoutHasCompleteTerminalArtifact | 完整输出与非零退出码 |
| implementationAgentCannotRewriteTaskContractAuthority | 合同防篡改 |
| implementationAgentCannotRewriteWorkspaceBaseAuthority | workspace base 防篡改 |
| implementationAgentCannotCreateWorkspaceAuthority | workspace authority 防注入 |
| recordsTypedTimeoutForZeroExitWithoutPromptCompletionAtDeadline | typed timeout |
| unvalidatedCompletionDoesNotEnterSemanticMemory | 未批准合同/未发布输出不得进入 memory |
| typedDesignRetainsDecisionAndSummaryWithoutCopyingPlannedScope | 有效 design 摘要保留、scope 不复制 |

本轮处理只在测试层：
- AuthoredAdapterFixture 复用 runtime/validator/gate 推进设计与计划批准；provider 替身使用真实 prepare-only 写输入，再返回合成 envelope，经真实 validator 发布。它是 adapter 测试的前序构造，不新增生产机制或 Maven 依赖。被测 occurrence 保留原测试的实例名、权限 facts，继承真实 routed inputs。
- 缺授权负例直接使用未初始化 context，不经正例 fixture。合同篡改三例先补满足既有 TaskContract 验证的基线，再由原 transport 执行同样篡改；原拒绝断言不变。
- completionJson 测试辅助方法改用 JSON parser 读取一个示例对象，不再把其后的 authored 输入说明拼进模型结果。生产 strict parser/ETL/correction 行为零改动。
- cache-aware 测试在前序完成之后设置 metrics，避免被前序 runtime 重算覆盖；WebSearch 测试按本次 result.run_metadata 找 prompt，不扫描第一个 run。
- memory 的“未批准合同可启动设计评审”前提与 E1/E3 冲突：原未批准合同/未发布输出排除断言保留，但移至合法的 pre-contract product review；另增 qualifiedAuthoredReviewStillExcludesUnvalidatedOutputFromMemory，补回真正合法设计评审下的未发布输出排除断言。有效 design 的 memory 正例则先经过真实 manifest 与 publication，不用空输入冒充有效输出。

原 22 项已在聚焦运行中全部通过，包含 manifest 不可绕过三例的合计为 `60/0/0/0`。此后新增上述 qualified-memory 用例并启动完整 reactor；结果以以下最终记录为准，不将聚焦绿色等同为 E3 整体闭合。

最终完整回归（2026-09-05 23:28 +08:00）：`mvn -fae test` 为 BUILD SUCCESS；逐份解析 160 份 surefire XML，合计 `1367/0/0/0`。
模块分别为 core 116、ledger 122、flow 12、git 6、runtime 609、adapter 357、CLI 80、server 65，全部零 failure/error/skipped。
本轮新 qualified-memory 测试通过，handler 为 48/0/0/0；`git diff --check` clean。原 22 项回归关闭，没有修改 production、workflow、permission、fork 或依赖；未调用真实 provider、未更新 real-case JAR、未提交。

自身回看：E3 提高合法 execution 输入要求后，旧孤立节点正例没有同步迁移；把“测试任意输入都能进入 transport”当作保留行为是不正确的，但只看到新守卫拒绝也不足以证明原行为无回归。此次以合法前序让原 transport/ETL/correction/tamper 断言重新实际执行，保留缺事实时零调用的负例，再运行包含 CLI/server 的全量。fixture 中 fake 仅负责合成 provider 输出；它不是 provider 真实行为证明，也不自动证明 §10 尚未覆盖的全部再入/epoch/UI 场景。

当前决定：回归修复完成；下一项仅核对既定 E3 全链验收与当前测试的对应关系，明确真实缺口后再做独立 diff review。不得为补表格扩充历史审计或新机制，不进入 E4/provider/real-case。当前仍未获得 E3 提交许可。

## 17. 全链验收核对（2026-09-06，只读代码核查）

结论：回归通过，但完整边界证据尚不充分，Boundary Review 为 WARN，提交许可仍为 NO。本节只记录现有实现和验收缺口，不增加功能或恢复机制。本轮没有重跑 Maven；1367/0/0/0 是 §16 的上一轮实跑结果。

### 17.1 Transition 与失败切面

| 前态 / 触发 / owner | 当前写入与副作用顺序 | 后态与消费者 | 中断、重试与冷启动边界 |
| --- | --- | --- | --- |
| 已批准合同、合法当前 node；执行 author/reviewer；runtime/adapter | 解析 ledger 输入 → 写本次 prompt/manifest → 读回复验 → prepared metadata → provider → adapter 终态资料 | 候选输出交给 validator；manifest 是输入快照，不授予批准 | prompt/manifest 写坏的零调用反证已测；prepared 前后及 provider 后各中断点的 E3 冷启动组合未测，不承诺自动恢复 |
| provider 候选返回；validator 发布 | envelope/semantic 校验 → 本次 manifest/直接输入与回应覆盖复验 → 写 sidecar → plan 附属投影（如适用）→ 返回带 refs 的 result | runtime 记录 execution，再 completeAndRoute；reader 还验证成功 execution/sidecar，不因文件存在就接受 | sidecar 写到一半、写完但 execution/route 未完成的故障注入未测；不得把局部文件的产生称为发布链完成 |
| completed reviewer/decision 或 direct plan；引擎路由到 human gate | 完成源 node → 后继 node → open gate → node_routed event → current → workflow instance 投影 → 打开 packet | authoritative WAITING_FOR_HUMAN；packet 只投影 candidate、obligations、responses、新 must_fix | node/gate/event/current 之间不是跨文件事务；packet 写失败不等于 gate 被批准，恢复可用性尚未由 E3 专项序列证明 |
| open human gate；用户 approve/revise；ledger | 检查 action/note → resolved gate → 完成 gate node → revise 时新 iteration → 后继 node/event → current/instance | approve 的候选供下游 resolver；revise 的 iteration/note 供下轮 author | 空 plan note 零状态变化已测；resolved gate 写完、后继未建时的 retry/resume 不能假定成功；禁止添加 single-case rescue |
| plan/implement/self-test 待执行；resolver 消费批准 | 从最近 gate occurrence 及 source/execution/sidecar 确定版本 → 比较合同与直接批准身份 → 形成本轮输入 → adapter 复验后调用 | 仅匹配批准来源的本次 execution 可以继续 | 删除直接 manifest 的 plan 零调用已测；所有再入边、同 bytes 新授权期、冷进程及失败后 abort/replay 尚未逐项实证 |

代码锚点：AcpxAgentTaskHandler 的 authored prepare/manifest 读回；ArtifactContractValidator.validatePublication 调用先于 sidecar 写入；WorkflowRuntime.executeActiveNode；RoutingEngine.completeAndRoute/resolveOpenGate；HumanReviewGateHandler.writeReviewPacket；AuthoredStageLineageResolver.approval/gate/gateSubject/prepare。
packet 内 JSON 先写、Markdown 后写，两者不是事务；现有代码不能据此声明任意中断后展示一致。已有 R1 限制继续有效：本轮不修多文件事务或恢复平台。

支持范围：本 checkpoint 是未来合法 execution 的 forward fix，不提供旧 E3 字段缺失工件的兼容回填；不恢复 PNBV/WA53。缺失工件当前走 typed authored/evidence 拒绝，不把它描述成已经存在统一的 INVALID 状态迁移。新旧 binary 混跑及旧工件续跑不作为受支持验证路径；新样本必须使用同一已审核工件。该操作限制不等同机器已全面禁止混跑。

### 17.2 已有证明与必要缺口

| 既定验收组 | 现有证据 | 尚欠的证明（不是新功能） |
| --- | --- | --- |
| 正常链、通用性、三种 plan 拓扑 | AuthoredStageSequenceTest 的两类路径正常完成、三拓扑与连续两轮 human revise；四 definition 测试 | 两业务测试主要替换文件路径，不代表不同业务语义已经 real-case 验证 |
| 回应覆盖与发布/调用守卫 | coverage/schema 单测；错误 manifest 身份、缺正文的 adapter 零调用；直接 manifest 删除的 runtime 零调用 | 未逐一以真实发布/下游调用序列证明遗漏回应、篡改、未批准/较早批准均不可绕过；单测不能全部代替 |
| 批准版本与再入 | 正常 implement/self-test 共用批准 plan；新 revision 被批准后取新设计；现有 correction 回归绿 | 合同 reapprove（含同 bytes）后拒绝旧链并成功消费新链；七类 implementation 入口与 direct self-test 再入；correction/retry 的 E3 输入身份专项断言 |
| 中断、重启、历史减法 | 删除 superseded manifest 与旧 locator 后仍能完成；空 note 无写入 | 这不是冷 JVM 或写入间故障注入；关键 sidecar/execution、gate/event/current 切面及冷重建仍缺专项证明 |
| 人工呈现 | gateView 对新 must_fix/active obligations 的断言；MD/JSON/UI 有消费接线 | actual packet 的正文/refs/回应同源断言，以及 CLI/API/UI 对该新增字段的行为一致性；现有通用 UI 测试不等价 |

### 17.3 六维结论与停止决策

Declared by：注册 stage contract、workflow 与 human action；Proven by：成功 execution、typed sidecar、gate/iteration/event、当前 manifest；Consumed by：authored resolver、provider 前输入、validator publication、human packet；Enforced before：provider 与有效输出发布；Failure route：typed evidence/contract 拒绝及既有 recovery；Cannot-bypass：目前只覆盖上表列出的子集。因此完整 E3 不给 PASS，不用回归绿掩盖失败切面空白。

下一步：让独立 diff review 检查现有完整控制链与上述覆盖判断，优先指出实际旁路和最小必要测试。不得预设 PASS，也不得因审查提出假想风险自动加框架。获确认的缺口只在现有测试/owner 内处理；若需新领域对象、兼容迁移、历史审计或恢复平台，停止并比较必要性。E4、provider、real-case、提交继续不启动。

## 18. CC Diff Review 后的失败分类修复（2026-09-06）

独立评审认为主控制链结构成立，但 D1/D2 与核心序列证据缺口使提交条件不成立。本轮仅处理这两个已复现的实现遗漏；不接受“边界证据部分覆盖即可提交”对 §10/§17 门槛的降低。未接受为技术债的缺口继续阻塞 E3 收口。

### 18.1 必要性与实际变更

- D1：`WorkflowDefinition.node()` 对未知节点抛 IllegalArgumentException，两个直接 resolver 入口只捕获 IOException。ContextBuildPackageHandler 与 HumanReviewGateHandler 同步补齐捕获，与 prepare/publication 的既有边界一致；不新增异常类型、authority 或 recovery 分支。
- D2：authored render/验证与 prompt/manifest 物理写入共用 catch，导致普通写入 IOException 被标为证据违约。物理写入移到两个校验 catch 之外，恢复原 adapter 异常传播；render/读回复验失败继续拒绝 provider。
- 语义精度纠正：context 入口返回 `REQUIRED_AGENT_EVIDENCE_MISSING / AUTHORED_CONTEXT_INVALID`；gate 入口仅抛带 `AUTHORED_GATE_LINEAGE_INVALID` 前缀的 IllegalStateException，并不由此自动持久化 typed recovery 状态。此次不能将 gate 的诊断归一称为恢复闭环。CC 提及的无 participating 节点自定义图支持性也未因 catch 调整而改变。

### 18.2 本 delta 的失败切面与反证

| 触发前态 | 失败点与 owner | 本轮处理后结果 | 反证 |
| --- | --- | --- | --- |
| 已有真实设计/评审链、待显示 human gate | context publisher 指向 definition 中不存在的节点；resolver | 包装既有 gate 异常，拒绝重写 packet；不批准、不路由 | unknownContextPublisherCannotRewriteHumanReviewPacket，旧代码因裸 IAE 失败 |
| 已批准 plan、准备 implementation context | routed event 引用未知节点；resolver | typed evidence failure、零 package output、尚无 implementation provider 调用 | unknownRoutedNodeRejectsImplementationContextBeforePublication，旧代码裸 IAE |
| 合法当前执行输入已完成 render | manifest writer 物理 IOException；adapter IO | 原异常原样传播、provider 零调用；prompt 可能已写，不能冒称存储事务或自动可重试 | manifestWriteFailureRemainsAnIoFailureBeforeProvider，旧代码吞掉异常；nonAuthoredSiblingPreservesTheSameIoFailureBeforeProvider 验证相同故障同路径 |
| writer 返回成功但 identity/正文损坏 | manifest 读回校验；authored validator | typed evidence failure、provider 零调用 | 两条既有 identity/body 反例保持通过 |

写入顺序仍为 render → prompt → manifest → verify → prepared → provider。本轮只纠正错误分类，不改变 §17 已声明的非原子写入、冷启动、retry/resume/abort/replay 与旧工件支持范围；不新增单样本恢复解释。

聚焦实跑：AuthoredStageSequenceTest `10/0/0/0`、AuthoredManifestDispatchTest `5/0/0/0`；先复现 D1 两条红灯，修 D1 后复现 D2 红灯，再修 D2 后全绿。新增 gate 测试第一次运行曾误用测试目录，改用既有 WorkstreamPaths.nodesDir 后才取得 D1 真实红灯，不把 fixture 错误算生产反证。

完整回归（2026-09-06 09:32 +08:00）：`mvn -fae test` BUILD SUCCESS，160 份 surefire XML 以 XML parser 聚合为 `1371/0/0/0`。runtime 611、adapter 359，其余模块与 §16 相同，净增 4 项反例；`git diff --check` clean。没有调用真实 provider、更新 real-case JAR、恢复 workstream 或提交。

回看：缺陷来自校验边界漏接同类异常，以及把 IO 写入误包进证据校验，不是缺少新模型。只统一现有入口并分离副作用与校验；其余优化建议不自动转为开工范围。后续仍须完成 §17.2 的核心序列证据，尤其同 bytes reapprove 正反、授权变化后的 retry、publication 漏 ID 与 bytes 篡改。七类再入/direct self-test、correction 正文、失败切面/冷进程与 packet/UI 不因本次局部修复被取消。

当前结论：D1/D2 最小实现修复通过聚焦测试；完整 E3 仍 WARN / NO COMMIT，下一步只补既定必要证明，不进入 E4/provider/real-case。

## 19. 原验收门槛的全链补证（2026-09-06）

用户明确：已确定不可降低的验收项直接补齐，不再请求以部分证明提交。本轮继续执行 §10，而非重新设计 E3；独立复审仍为提交前置。新增证据复用既有 fixture、runtime、ledger、validator 和真实浏览器，不增加 provider、网络能力、恢复动作或测试平台。

### 19.1 验收与真实测试对应

| §10 项 | 本轮补证或保留的证明 |
| --- | --- |
| 1、3、4、12 | AuthoredStageSequenceTest 保留双业务路径、三种 plan 拓扑、initial plan 与 design revision 隔离；AuthoredDefinitionTest 保留四 definition 与错配拓扑。路径替换仅证明业务无关装配，不冒充两个真实产品 E2E |
| 2 | twoHumanRevisionsKeepBothNotesAndInitialPlanIsNotAPlanRevision 运行两种问题顺序，逐次断言累计义务；missingResponseCannotPublishOrRouteDespiteProseClaim 让模型正文声称完成但漏响应，真实 publication 拒绝且 reviewer/implementation 不调用 |
| 5 | everyDeclaredImplementationAndDirectSelfTestEntryConsumesTheApprovedPlan 从 full definition 提取七条 implementation 与一条 direct self-test 边，正常/缺直接 manifest 两向共 16 场景；真实 routing/context/consumer，断言批准 plan 身份及失败时零调用 |
| 6 | approvedInputDamageAndPartialPublicationBlockTheActualConsumer 覆盖 14 个损坏/缺失切面：sidecar/manifest 字节、半写、gate source/action/open、未完成 source、缺成功 execution、未发布 refs、缺路由、跨 workstream/iteration；twoValidPlanCandidatesInOneApprovalCannotBeChosenByOrder 使用两份真实有效候选，拒绝按顺序择一并阻止实现 |
| 7 | sameBytesReapprovalRejectsOldAuthoredChainAndFreshChainCompletes：相同合同 bytes、新 compile/gate epoch，旧 chain 拒绝且新 design/plan/implementation/self-test 完成 |
| 8 | aLaterOpenApprovalBlocksOldRetryAndAResolvedReplacementDoesNotRebindIt：新 open gate 不回退、批准后旧 occurrence 不重绑，新 occurrence 完成；unchangedRetryAndColdJvmResolveIdenticalObligations 对同 run 重算一致；adapter correction 测试读取真实两次 prompt，断言同一完整 required body |
| 9 | 真正子 JVM 冷读 identity/obligations 一致；半写 manifest 在冷 JVM 中报 AUTHORED_SOURCE_HASH_MISMATCH；stable 副本存在也不能替代删除的直接 manifest；原祖先 manifest/locator 减法正例继续通过 |
| 10 | successiveHumanPacketsExposeTheSameCandidateAndObligationsAsTheirConsumer：两份实际 MD/JSON 与 resolver 同源，NOT_ADDRESSED、新 must_fix、下游 candidate 对应；CLI 整段保留、API 完整 JSON 透传；两端真实浏览器显示实际 packet，历史 mgt 无决策按钮、文本转义不执行 HTML |
| 11 | 缺回应拒绝后真实 abort + resume，FAILED/无 active node/无新调用；正常 terminal replay 同样零调用；既有 note/预算/BLOCKED/autorun 测试由完整 reactor 保持 |
| 13 | 六项独立生产守卫突变均实跑红灯，见下表；初始/正确版本随后恢复并全量复验 |
| 14 | 完整 package、四 definition CLI validation、diff-check 与浏览器复跑结果见 §19.4 |

测试替身界限：再入矩阵以真实声明边驱动 runtime，但上游分类结果由 fixture 提供，不宣称重新证明各 reviewer 的业务分类。reapproval 用同一已验证合同结果建立新的 compile occurrence，专测授权期而非重新证明 compiler。所有 provider 都是替身；未执行 real-case。

### 19.2 独立守卫突变

每项只临时改一个守卫，单独运行对应测试，再恢复；红灯来自行为断言而非编译错误。

| 临时删除/替换 | 实跑反证 |
| --- | --- |
| publication response coverage | missingResponseCannotPublishOrRouteDespiteProseClaim：下游 reviewer 被路由，失败 |
| implementation/self-test approved-plan guard | 每个再入必须有 approvedPlan 的明确非空断言失败 |
| 最近 approval 改取第一个 | 新 gate open 时旧 retry 未拒绝，assertThrows 失败 |
| governed context 的完整 authored body | reviewContextCarriesRequiredBodyAfterHumanRevision：required baseline 缺失 |
| packet JSON authoredStage 投影 | 两份实际 packet 与 resolver 投影比较失败 |
| adapter manifest 读回复验 | aSuccessfulWriterWithWrongIdentityCannotDispatchProvider：命中 forbiddenTransport，证明不只是 writer 单测 |

恢复后五个生产文件 SHA 与实验前逐字一致：AuthoredExecutionContext=e1b78e70…；AuthoredStageLineageResolver=b9e9e799…；HumanReviewGateHandler=48b53892…；ContextBuildPackageHandler=07226341…；AcpxAgentTaskHandler=b61255a3…。无突变代码残留。approved-plan 首跑由空值解引用报错，已改成明确非空断言并再次获得行为红灯；不把第一次 NPE 当作完整反证。

### 19.3 失败切面、呈现与范围复核

现有写入顺序仍以 §17.1 为准。本轮以落盘状态切面覆盖 manifest/sidecar 半写、sidecar 已写但 source 未 completed、execution 未成功发布、gate 未 resolved/路由未齐等状态，随后真正执行消费者；不是注入 SIGKILL 遍历每条 IO 指令。冷 JVM 正反与 runtime 拒绝/abort 证明“不接受未完成 authority”，不承诺自动修复半写、所有展示在崩溃后原子一致或 R1 liveness。

唯一新增生产改动是 console.css 中限定于 #evidence 的列表 overflow-wrap: anywhere。真实截图发现长 obligation ID 越界，浏览器 scrollWidth 断言先红再绿；不截断身份、不改变审批或数据源。该文件是 §9 presentation 面的必要一行规则增量，不是重做 UI。

浏览器测试位于 dual-server/src/test/js/authored-packet-ui.mjs，读取 runtime 测试实际导出的 target/authored-packets/ 两份 packet，加载现有 console/mgt HTML/JS/CSS，所有请求在浏览器内拦截，禁止外部 origin。断言正文/refs/回应、旧候选不残留、HTML 转义、历史只读及证据栏不溢出。output/playwright/e3-authored/ 截图与 target/ 工件仅本地验证输出，不进入 checkpoint 提交。

本轮测试编写错误如跨 workstream 最初误写 node 不存在字段、冷读 reason 拼错、correction 尾部空白比较及浏览器面板未按真实路径打开，均修在 fixture/assertion；不称为生产缺陷、不为其新增生产兼容。

### 19.4 最终复验与提交门

2026-09-06 14:03 +08:00 最终 `mvn -q package` exit 0（不跳测试）；161 份 surefire XML 用 XML parser 聚合为 `1381/0/0/0`：core 116、ledger 122、git 6、runtime 620、flow 12、adapter 360、CLI 80、server 65。比 §18 增加 9 个 runtime sequence test 与 1 个 adapter 半写测试；循环矩阵场景不重复计入 JUnit test 数量。

新 JAR 的 `workflow validate` 对 contract/full/terminal-delivery/small-plan-human-review-experiment 四份 definition 均 exit 0，分别 34/56、34/56、31/52、28/47 节点/边。未修改这四份定义。浏览器复跑输出 E3_AUTHORED_PACKET_UI_PASS；diff-check clean。
CLI JAR SHA256：`f74d5cd7750c0c494eb81ff01d76f829948b429a9fddba55f2027394734b1f33`。这是本地构建身份，不构成 provider 或 real-case 授权。

浏览器复跑命令（Maven 根目录；先运行 AuthoredBoundarySequenceTest 生成两份 packet；使用既有 Playwright，不新增依赖）：

```sh
PLAYWRIGHT_MODULE=/Users/kriswang/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs node dual-server/src/test/js/authored-packet-ui.mjs
```

六维核对：Declared by=注册角色/qualified contract/人工 gate 决定；Proven by=成功 execution、sealed sidecar、manifest、gate/iteration/event；Consumed by=唯一 authored resolver、实际 prompt、publication、human packet；Enforced before=provider 调用和有效候选发布/下游使用；Failure route=现有 evidence/contract 拒绝，存储 IO 不改标；Cannot-bypass=上述正常/拒绝/冷重建/再入/突变测试。事实仍不等于语义正确，未宣称 LLM 零振荡、产品完成或历史 workstream 恢复。

下一项仅是对本 delta 的独立 CC 复审，必须复核全链而非只看测试计数；复审前不提交、不进入 E4/provider/real-case。若复审发现既定范围内的真实缺口，直接补齐，不申请降标。

## 20. CC Delta 通过与通用性验收补齐

用户提交的 E3 Delta 只读评审确认：D1/D2、四个核心证据缺口、再入/correction/冷 JVM/呈现 sibling 已关闭，六维成立，允许选择性提交。评审未执行测试，计数只核对算术，不能称其独立复跑。唯一需裁定的字面缺口是 §3/§10.12 的双业务对称拒绝与一致路径重命名。

本轮裁定：不降低要求，按用户已授权纪律直接补齐。生产文件、workflow、handler/permission/ledger 模型均不再修改；五个生产文件 SHA 与 §19.2 完全一致。改动仅两个现有测试文件及本记录：

- missingResponseCannotPublishOrRouteDespiteProseClaim 参数化两类业务，各先形成两条历史义务，再分别删除第 0/1 条响应。四个独立场景均保持 publication 拒绝、无 sidecar、下游零调用及 abort 后零调用断言，不用“全空响应”代替“漏掉任一条”。
- approvedInputDamageAndPartialPublicationBlockTheActualConsumer 的 14 类失败切面在两类业务各执行一遍，共 28 个场景，包括冷 JVM 拒绝半写。
- twoValidPlanCandidatesInOneApprovalCannotBeChosenByOrder 在两类业务均执行，保留两份候选实际有效、NOT_UNIQUE 与批准后实现仍拒绝的断言。
- consistentPathRenamePreservesTheCompleteAuthoredSequence 对两类业务分别比较原路径和改名路径（含空格路径）：合同 included、design planned_files、plan edit_files、实现/自测批准身份逐项一致；合同 SHA 随路径变化，执行调用序列不变，实际 approved input 不得残留旧路径。这是 fresh fixture 的一致映射，不是篡改已批准工件后绕过 SHA。

聚焦 AuthoredBoundarySequenceTest + AuthoredStageSequenceTest 为 `26/0/0/0`。2026-09-06 15:11 +08:00 完整 `mvn -q package` exit 0，161 份 XML 聚合 `1388/0/0/0`；相对 §19 净增 7 次 JUnit invocation（参数化和两项改名正例），矩阵内循环不重复计数。新 JAR 的四份 workflow validate 全部通过；浏览器复跑 PASS；staged diff-check clean。CLI JAR SHA 与 §19.4 一致，生产代码本轮零变更。

选择性提交范围为评审覆盖的 E3 43 个文件（含本轮两个测试文件与计划记录），排除 output/ 和旧 20260903 OKX Gate。此轮按 CC 提出的廉价参数化条件完成，不采用“无业务分支即可代替测试”的降标方案；不新增依赖或测试平台，不能从路径无关性外推真实业务一定收敛。

停止边界保持：本 E3 checkpoint 提交后停住，不自动进入 E4/provider/real-case；既有 R1 liveness、冻结历史状态支持范围与 reviewer 语义局限未被改写。
