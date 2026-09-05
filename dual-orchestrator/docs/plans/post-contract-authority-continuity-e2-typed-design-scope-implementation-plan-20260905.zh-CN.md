# E2 Typed Design Scope Implementation Plan

状态：ARCHITECTURE REVIEW PASS / PRE-IMPLEMENTATION GENERALITY ASSESSED
基线：E1 `306a555d`

## 1. 目标与整体位置

本轮主目标属于主线正确性修复：设计候选必须显式声明计划修改的文件与操作，并在发布可路由的有效 sidecar、启动 architecture reviewer 前，
机械验证其属于当前已批准 TaskContract。任一越界或资格缺失均不得作为成功设计进入后续阶段。

整体目标仍是 contract approval → design/revision → plan/revision → implementation/self-test → acceptance 的权威连续性与修订收敛。
E1 已提供 contract qualification；E2 提供可校验的设计范围；E3 将绑定修订历史与批准的 design/plan；E4 收敛 plan scope 与下游旧入口。
本轮不以 E2 的通过代替后三段的证明。

## 2. 自身通用性评估（设计前）

判断：设计可用于不同业务，但目前适用面是既有注册的 design authored stage。实施后必须用行为测试证明，不能只靠名称扫描。

| 检查 | 代码事实与设计裁定 | 实施前/后证据 |
|---|---|---|
| 业务无关输入 | 只接收 typed design 文件清单与 E1 qualified contract；不输入项目名、数据库规则、网络域名 | 文档转换工具与静态站点两组契约替换测试 |
| 判断规则 | canonical path、唯一性、contract subset、operation equality | 相同违规在不同业务中产生相同拒绝类型 |
| 现有 owner | ContractRegistry 声明类型；envelope parser 解析；validator 校验；sidecar 承载已验证候选 | 追踪 render → parse → validate → publish → review context |
| 能力限制 | registry 当前按已知 node id 注册；不是任意自定义 agent node 自动支持 typed design | 四 workflow parity；明确不外推未注册节点 |
| 不拥有业务语义 | responsibility、架构说明用于 reviewer 判断，不解析为事务或产品规则 | 改写业务 prose 不改变 path/operation 校验结果 |
| 不推断实际变更 | planned files 不等于 Git changed files；CREATE 不要求设计时已存在 | 正常 CREATE 计划通过，实际 Git scope owner 保持原样 |

禁止 OKX、BTC、特定产品类名、固定项目目录进入 production 判断。测试可以包含业务实例，但不能只用一个业务证明通用性。
路径重新命名后，只要同时更新契约和设计，结论必须不变；仅修改设计而未获契约授权，结论必须变为拒绝。

实施开始前重新核对本表与当前代码；实现完成后给出实际测试名、结果和限制。独立评审用于复核该评估，不替代实现者责任。

### 2.1 实现者复核结论（收到独立评审后）

独立评审 PASS 之后重新读码裁定：E2 的规则具有跨业务通用性；实现通用性尚待行为测试，不由 READY 代证。

- 业务维度：校验只比较 qualified contract 与 planned_files 的路径/操作，不依赖语言、框架、网络 API 或业务术语。
  一致替换契约和设计中的规范路径应保持结果；只改变设计而越过批准集合必须失败。
- 产品维度：适用于仓库文件交付任务。数据库运行数据、云资源和其他不以文件声明的副作用不在此规则的覆盖内，不宣称通用副作用授权。
- workflow 维度：现有 registry 按 node id，architecture subject 入口也按 profile/target node 识别；这是既有 stage 协议耦合，
  不是 OKX 耦合。四 workflow 可验证，任意重命名的 custom node 不自动继承能力。本轮不为消除此限制引入注册平台。
- 内容维度：路径集合与操作能机械检查；responsibility、业务正确性、文件清单完整性和 prose 与清单一致性仍需 reviewer。
  E2 不能单独阻止“形式回应但语义未修复”，该限制在 E3 中同样必须如实保留。
- 消费维度：EvidenceManifest 的 default 分支会把新类型误标为 generic；memory 按字段读取而不受 sealed exhaustiveness 保护。
  两者必须纳入接线测试，compiler 通过不能代替 reader sweep。
- 额外发现：`resolveRequiredArchitectureReviewSubject` 当前按 producer node 过滤后 `findFirst()`；E2 typed subject 必须要求
  唯一 design occurrence，重复 ref 可按同一 occurrence 去重，不同候选为歧义并拒绝。不能以列表顺序决定评审对象。

以上为基于当前 production 的设计评估。实施后的双业务全链测试、consumer 保真与突变反证尚未执行，当前不标 ENFORCED/PROVEN。

## 3. 已核实的挂钩点

- `ContractRegistry.DESIGN_CLAUDE` 目前只接受 `agent_output_contract`，而 plan 已接受 `plan_result`。
- `AgentResultEnvelope` 是 sealed result 类型；新增 design 类型必须同步 parser、validator、prompt example 和 projector 的 exhaustive 分支。
- `ArtifactContractValidator.validateAgentOutput` 在 envelope 校验后进入 `withResultSidecars`，后者负责发布 sidecar；范围校验应位于发布之前。
- `AgentOutputContractDocument` 已有 `sourceArtifactType` 与 typed plan 投影模式，设计可沿用 occurrence sidecar，不新增 stable 文件。
- `ContextPackageSectionRenderer` 已能渲染 typed plan，architecture review 的 design 输入应沿同一 typed reader 模式处理。
- `TaskContractValidator` 已有 canonical path 校验；复用现有规则，不引入另一套 normalization。
- E1 `TaskContractProjectionVerifier` 是唯一 approved contract resolver；禁止设计校验自行读取 stable contract。

## 4. 最小结构

新增 `design_result` 与 `design.planned_files[] = {path, operation, responsibility}`。

- operation 复用 `TaskContract.ScopeOperation`，不新增同义枚举。
- 保留设计所需的 decision、summary 及现有检查/证据/风险呈现字段；不得复制不被设计使用的 plan 或 acceptance 专属结构。
- `changed_files` 如为公共投影兼容所需，继续表示实际变更，不能拿它装 planned files。设计 planned files 只由 design 字段拥有。
- 不增加 `Failure.verification_remediation` 的适用节点；若最终公共字段选择保留 Failure，则必须继承非 acceptance 的 NONE 守卫。
- `revision_responses` 留给 E3 与 baseline 同时落地，E2 不加入没有消费方的空协议。
- sidecar 承载 typed design 内容，复用既有 source output ref/SHA、run/node/workstream lineage。没有 canonical design 文件或独立 binding 文档。

初始与 revision design 都必须提交完整计划文件集合。空集合在 schema 上可表达；是否足够完成需求由 reviewer 判断。
文件集合只能证明“所声明的修改未越界”，不能证明自然语言中不存在隐藏计划，也不能证明设计完整或业务正确。

## 5. 强制链

1. Design registry 仅接受 `design_result`；旧 generic design 输出不能继续作为有效候选，initial/correction prompt 同步。
2. Envelope validator 检查结构、required 字段和条目格式。
3. Artifact validator 在 sidecar publication 前取得 E1 qualified contract。设计输出的范围验证不因缺少 routed stable refs 而跳过。
4. 复用 canonical path 校验，拒绝绝对路径、逃逸、非规范写法和重复 path；不静默 normalize，也不要求 CREATE 目标已存在。
5. 每个 planned file 必须与 contract scope.included 的同一路径、同一 operation 匹配；采用 subset，不强制全部 contract paths 必须出现。
6. 校验失败复用既有 artifact contract violation 路径，附结构化 reason（缺资格、路径非法、重复、越界、operation 不符）。
   不新增恢复动作、workflow edge 或自动业务修复机制。
7. 成功后发布 occurrence sidecar。Review context 从 routed、通过 lineage 校验的 sidecar读取唯一 typed design occurrence；
   缺失、错误类型或多个不同 design candidate 必须拒绝，不得按输入顺序取第一个，也不得从 Markdown 猜文件清单。
8. architecture reviewer 使用 typed 文件清单审查 scope，再结合说明审查完整性与业务语义。Reviewer 的 decision/must_fix 协议不变。

E2 不新增跨 execution 锁。如果输出校验时 contract 已发生不完整 recompile 或 SHA drift，则 E1 resolver 拒绝；
跨阶段“究竟批准了哪版 design”的 durable binding 属 E3，不能在本轮宣称已完成。

## 6. 文件与 sibling 范围

预计 production 面：

- `AgentResultEnvelope`、`AgentResultEnvelopeValidator`、`AgentResultPromptContract`、`AgentResultDocumentProjector`、`ContractRegistry`；
- `ArtifactContractValidator` 与一个 package-private design scope validator（仅在避免混合 shape 与 workspace authority 判断时拆出）；
- `AgentOutputContractDocument`、对应 typed design reader、`ContextPackageSectionRenderer`；
- `ContextBuildPackageHandler` 的 design subject 唯一性和类型校验；`EvidenceManifest` 显式保留 design_result 类型并测试 ref/类型保真；
- `WorkstreamMemoryUpdater` 的字段级读取保真测试，确认 decision/summary 保留、planned_files 不变成另一份 scope authority；
- 既有 design prompt 模板，以及直接消费 design type 的 context 校验分支（先 sweep 确定，禁止 filename fallback）。

实施前扫描全部 result switch、artifact type reader、prompt/correction example、sidecar schema 检查与四 workflow expected_artifact 声明。
如 definition 声明依赖旧类型，只改其类型声明，不改拓扑。Compiler 揭示的 sibling 必须逐项登记；不以穷举分支修完就声称消费闭环。
Reviewer、plan、implementation、self-test、acceptance 的输出类型保持各自语义，新增 design 类型不得扩宽其他节点允许类型。

Sweep 必须逐个检查带 default 分支的字符串 switch 和字段级 reader，它们不会因新增 sealed variant 而编译失败。
Human design gate 的 planned_files 投影明确归 E3：随其 gate → candidate 唯一 lineage resolver 展示文件清单、候选 ref/SHA 和
obligations/responses，不在 E2 为展示另建 candidate resolver。E2 不宣称 human design approval 的版本绑定已闭合。

## 7. 必须通过的行为测试

1. 完整正例：design render → parse → typed validation → qualified scope check → sidecar → architecture context，文件/操作逐字段保真。
2. 无 approved contract、篡改 contract、recompile 未批准：拒绝 design publication，reviewer 调用数为零。
3. 非规范/逃逸/绝对路径、重复、越 scope、operation mismatch 全拒绝；prose 声称合规不影响结论。
4. 合法 CREATE（文件尚不存在）、含空格/中文的规范路径、合法 subset 通过；不从目录名推断 relevance。
5. Initial/correction/revision 均要求新类型；旧 generic design 被拒；其他 result 类型保持既有行为。
6. Reviewer context 缺 typed sidecar、错误 source type、lineage 被篡改或存在两个不同 design candidate 时拒绝，不回退 raw Markdown或顺序选择。
7. 文档转换工具与静态站点两组不同业务输入运行同一生产校验链；一致重命名保持结果，仅越权改名则失败。
8. 四 workflow design producer/reviewer 声明与实际消费链一致；不依赖 experiment 独有路径。
9. 突变反证：删除 scope check 后违规测试必须失败；移除 typed context 消费后正例保真或缺失拒绝测试必须失败。
10. E1 测试回归、相关 focused tests、全模块 `mvn test`（含 server）、四 workflow validation、diff-check。
11. 同一有效 design sidecar 经 EvidenceManifest 保留 design_result 与 source ref，经 memory reader 保留 decision/summary；
    memory 不复制 planned_files 为授权集合。两个 reader 均用实际生产实现验证。

测试不要求真实 provider；fake transport 只替换外部调用，实际 validator、sidecar publication 和 context consumer 必须执行。

## 8. E3/E4 接口与通用性前置

| 后续项 | 必须消费的事实 | 设计/实施前必须再评估 |
|---|---|---|
| E3 | E2 occurrence design + 现有 plan sidecar；gate/iteration/reviewer lineage | human 与 review-driven revision、direct/reviewed gate 两拓扑；gate packet 展示唯一候选 planned_files/ref/SHA 与 obligations/responses；obligation 只管理来源与覆盖，不解释业务语义 |
| E3 | approved design → plan；approved plan → implementation/self-test | 每条初始与修复再入使用批准的版本；无 latest/session fallback；跨业务历史要求替换测试 |
| E4 | approved contract + plan edit_files + 实际 Git evidence | plan 只约束计划范围，不能授权实际越界；canonical writer/metrics reader 删除前逐 consumer 复核 |
| E4 | 全部 consumer 与四 workflow | 全链 sequence、冷启动、legacy/fresh、恢复再入；不能用单个 validator 测试代替整体闭环 |

后续的独立实施计划必须包含自身通用性结论、可复核证据、适用限制、失败路径及删掉守卫必失败的测试。
整体收益需 E1-E4 后以自然 fresh real-case 证明，不人为制造缺陷、不提前恢复 PNBV。

## 9. End-to-End Invariant

| Dimension | E2 验收 |
|---|---|
| Declared by | registry 的 design_result 与 typed planned_files |
| Proven by | E1 qualified contract + 规范路径/操作 subset 校验 + 既有 sidecar lineage |
| Consumed by | architecture review context 读取同一已验证 design occurrence |
| Enforced before | 有效 sidecar 发布与 architecture reviewer 启动 |
| Failure route | 既有 typed artifact contract violation；缺失不回退 prose/旧类型 |
| Cannot-bypass | 越界拒绝、零 reviewer 调用、typed context 保真、跨业务替换与突变测试 |

## 10. 顺序与停止条件

独立架构评审 → 实施前通用性/reader sweep → failing tests → producer/validator/consumer 同批实现 → focused/full tests →
自身全链 review/reflect 与通用性证据 → 独立 diff review → 选择性提交 → E3 计划。

需要新规则引擎、业务语义解析、额外 durable authority、扩大工具权限或改变 workflow 修复路由时停止并回比较。
独立架构评审 PASS（P0=0/P1=0/P2=3）。实现者再次核查通用性后认可进入实施，三项 P2 已登记 manifest、memory 与 E3 gate packet
归属，并补入唯一 design subject 反证。Implementation READY: YES；实现通用性测试仍待执行，ENFORCED/PROVEN 尚不可声明。
