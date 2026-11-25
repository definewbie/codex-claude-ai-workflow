# W09 有界离线配置与输入核对（2026-10-11）

**案头核对完成 / 新 Gate 为待审提案 / 第四例 NOT AUTHORIZED、NOT RUN。** 推荐保留当前客户端、模型和权限接线，以完整研发验收交付为目标重新设置预算；本轮没有证据支持先修改推理设置并预扣节省收益。建议停止线150万可观察请求token、预警120万，分段派发前保留未完成主链及一次可能格式修正的额度。详见[完整 Gate 提案](full-mainline-gate.md)。不是成功率、成本上界或p95承诺。

本轮只读第三例已保存材料、选定客户端/adapter的现有源码和配置。不重开安全/委派/内核实验，不执行native/provider、安装、CLI探针、业务流程或额外agent；不修改生产代码、全局配置、冻结发行或原三个样本。新增脚本只做材料提取和预算算术，没有调度能力。没有重跑Maven，也没有以离线算术替代已有1779测试凭证或真实能力证明。

## 1. 问题边界与完成口径

本轮推进P0完整主流程证明的准备与P0.5实际token治理，不开启P1/P2/P3节省策略。Definition仍是terminal-delivery@5及原合同；Instance是未来单个fresh workstream，本轮未创建；Execution的原生结果与固定usage是消耗SoT；Gate规则和operator核对决定实验是否继续，不伪造领域失败或新批准。恢复、验收和交付仍消费原owner事实。

核对目标是：给出实际设置/传递路径、输入冗余事实、完整主链预算算术与合法/拒绝对照，明确未知项和下一次批准内容。不能把“存在SDK字段”称为模型已遵守限制，不能把“少了一次目录搜索”称为已省若干token。

## 2. 配置：接口存在与实际采用分开

证据入口：[配置选择项](config-observation.json)、[原生请求/返回](native-config-and-tool-observation.json)、[带SHA源码索引](source-index.json)、[定点摘录](source-excerpts.txt)。导出采用非敏感allowlist，不复制凭据或完整个人配置。当前安装包版本为Claude ACP0.70.0/SDK0.3.232、Codex ACP1.10.0；这是本机测量，不新增精确版本pin，也不声称可复现构建证明了当时native子进程的全部字节。

| 候选接口 | 现有传递与实际事实 | 结论及本次选择 |
|---|---|---|
| Claude `effort` | 原生第三例会话返回选项，currentValue=default；adapter由模型支持列表生成选项，并以SDK applyFlagSettings应用非default选择。当前用户设置无effortLevel | ACP选项存在，不证明GLM后端按该档位节省或质量不退。保留default |
| Claude `MAX_THINKING_TOKENS` | adapter在创建query时读process.env并转SDK thinking；acpx透传父进程env。连接wrapper只从个人settings.env导出provider/model名单，不含此变量 | 未来可用显式子进程env候选；本轮不设置。不能只把变量写入个人settings就认为当前Dual路径已生效，更不能称总请求token硬限 |
| Claude `--max-turns` | acpx flags→SessionAgentOptions→session/new._meta.claudeCode.options.maxTurns→adapter userProvidedOptions→SDK Options，现成路径完整；原三个Claude会话的明确请求均没有maxTurns | 可限制SDK对话轮次，不能直接限制总token，过小会截断合法工具读取/测试。未实证GLM上的停止行为；不设新低限 |
| Claude输出限制 |当前SDK具有CLAUDE_CODE_MAX_OUTPUT_TOKENS环境schema；当前安全选择项中无显式设置 | 存在字段不等于本次模型消费已证；也不能限制累计输入/cache。截断JSON会引入格式修正，不作为预算收益前提 |
| SDK maxBudgetUsd/taskBudget | SDK声明美元预算停止及API侧task_budget；acpx的SessionAgentOptions和明确assign链没有对应通用flag，本次session请求中也没有这些字段 | 不为接入它们改adapter。GLM订阅下SDK美元量不是实付账单；alpha taskBudget也不证明兼容后端的累计硬限 |
| Codex reasoning_effort | 当前配置high，第三例实际返回high；adapter从实际threadStart状态构造model/effort，并在每个turn/start显式传effort，set-config有原通道 | 实际high成立；降低为medium可能改变独立复评质量，无本任务对照。保留high |
| 上下文/缓存 | 当前个人Claude env有MAX_CONTEXT=200000，Codex有context272000/auto_compact240000；第三例usage_update是上下文快照，不是累计请求量 | 不以窗口大小推请求成本，不改变缓存/compaction开关，不以剔除cachedRead改变批准口径 |

**关键实际绑定**：第三例三个Claude session/new都明确 `settingSources=[]`。acpx policy先于adapter query固定这个值，覆盖adapter默认的user/project/local sources；provider连接env仍经既有wrapper allowlist进入。个人Claude Code的整套设置不是Dual运行设置的同义词。adapter的SettingsManager还会独立读取settings以生成模型/effort信息，因此也不能反过来说“所有用户设置绝不参与任何逻辑”。本轮只证所列明确路径；第三例未完整记录的任意继承环境不补造为“当时一定不存在”。

**ROI选择**：保持当前已实际工作过的组合，不以未经实证的思考/输出限制开启一轮优化实验；本次完整链预算不折算任何假定节省。若将来真实成本持续阻塞，再以实际阻塞决定是否做一个有对照目标的配置实验。

## 3. 输入：发现了一项低价值读取，不据此重写输入系统

[input-observation](input-observation.json)绑定原设计prompt/manifest；[工具原值提取](native-config-and-tool-observation.json)绑定原stream。

- Dual设计prompt10334bytes、估计2584token。原manifest的section统计只是描述性元数据，不假设彼此互斥，也不是native累计输入归因。
- 产品tracked inventory为0。原Find `**/*`展示100个路径，全部是`.git`或`.agent`程序材料；末尾明确“100 of 287”，另有187条未展示。程序footer不是第101个产品文件，也不能把这100条解释为287条全量内容。
- 随后的两次Read分别读取当前GOAL合同与实际批准门。合同原文未在prompt内展开，且包含批准后AC-4/AC-5和授权边界，**读取合同有必要，不能因原需求已在prompt就删掉**。批准门读取用于核对authority，本轮不凭成本删其证据义务。
- prompt同时给出4个合同/解析引用和明确的typed合同身份，模型还做了全目录搜索。该搜索与空产品inventory不匹配，是具体的低价值探索；但没有反事实对照证明它贡献了多少token。
- 原请求实际130731（含72832cachedRead）成立。归档ACP stream没有可直接计算的SDK num_turns，3个工具不等于3个模型轮次；思考文本字符也不能转换为已测thinking token。未证明唯一根因或提示词优化收益小。

下一Gate不修改渲染器、不内联全部历史、不删除批准/合同检查，也不增加文件读取裁判。可在后续另行评估“明确优先指定合同、无证据缺口不全目录搜”这种简单指令，本次不把它作为必要实现或折扣条件。当前已知字段义务必须保持，预算先容纳现状。

## 4. 完整主链预算与观察器约束

九个模型节点由本仓terminal-delivery定义核对，见[budget-proposal](budget-proposal.json)：正常主链暂定940000token。intake作者40k/复评50k、设计150k有对应阶段实测；其余六阶段是**未测临时规划额度**，不是估出的p95。实现200k、自测150k和独立验收100k，为尚未跑过的读写/证据生产保留空间，收益目标是买到真实完整链证明，而非单位成本下降。

示例完整路径：主链940k + readiness计划作者/复评重入175k + 一次intake返修/复评90k + 一次格式修正150k = **1355k、14次顶层请求**。提案总停止1500k，余145k；18次总池不保证第二返修或多个格式修正都装得下，是否可走由实际余量和剩余主链准入决定。默认不额外安排两个连接试探，原生创建/握手仍在正常节点前置执行，新错误照常拒绝。

每次模型派发前，不只比较“已用<T”，还要求已用 + 未完成正常主链额度 + 当次新增重入/返修额度 + 一次可能格式修正150k ≤1500k；调用池同理保留尾链及一次可能格式修正。每次完成后重核原请求身份、完整可加总usage与本次实际调用数。cachedRead仍计入原口径，SDK美元与订阅账单分立。第三例两个Codex复评共用workstream会话，而Claude调用为run scope；后续复评的历史可能继续增长，不把首次复评成本当所有后续阶段的已证上界，也不借此改session策略。

旧run-one-step.py的 `len(runs)*2+2` 会把已完成且没有correction的run也按两次占池，不宜带到新Gate；其160k常量也不能原样复用。新提案按固定acquisition/原stream的主调用及实际correction分别计数，再为**将来当前调用**留一次修正；不是从toolCallCount或RPC序号计模型调用。不为修正计数改变原三例observer或事实。

本目录[纯预算核对器](check-budget-offline.py)只做可复核算术，不访问native、不调度、不造批准。13个[离线对照](budget-dry-run.json)包括新主链、第三例数字的预算反事实、9个run不自动算18次、保护尾链/修正、缺usage、未知外部结果、时间不足和未知阶段。它们证明算术及拒绝义务有判别力，**不证明运行时已接入、不授权第三例恢复、不证明第四例能成功**。后续获运行批准时才准备独立新observer并离线核对这些规则，不能直接换常量跑旧脚本。

## 5. 边界自查、未竟项与下一步

Boundary Review：**PASS（本次只读材料范围）/ 新Gate等待独立复审与用户预算、运行批准**。没有production闭环或W09完成声明。第三例487材料、288停止账本、654冻结输入及发行身份保持；复核见identity-before/final。材料提取首遍把Find截断提示误当路径，已修正为100个程序路径+独立footer；一次文档patch因锚点不匹配未写入，随后用实际锚点修改。本轮还有几次不存在候选文件名的搜索，均转实际路径后核实。首次校验第一例manifest时误以目录相对路径解释实际repo相对路径，校正后只核其116个不可变事故材料；其中活动worklist的历史SHA不作当前文档不变断言。它们是本次案头操作纠正，不是产品先红或Gate故障。

| 六维 | 当前范围 |
|---|---|
| Declared by | 用户本轮只读核对授权、原完整主流程/严格批准、第三例成本事实 |
| Proven by | 本机文本/配置SHA、真实归档请求选项、输入/工具提取与预算算术；没有配置效果、未测阶段成本或第四例实证 |
| Consumed by | 原native clients/typed runtime消费实际配置；未来operator消费新预算，不把本目录变为ledger authority |
| Enforced before | 未来派发前预算/身份/批准重核是Gate义务，尚未运行；原qualification/validator/C-X仍原样，不称本核对器已自动拦dispatch |
| Failure route | 前提不齐不启动；已知有效运行止损FAIL、外部UNKNOWN依D07、领域正常状态不伪造失败、不自动重发 |
| Cannot-bypass test | 离线正反预算对照及新observer验收义务；原权限/证据/交付反证保留，未触发真实分支不填PASS |

F2工具计数、F3 RUNNING恢复误导仍为具名已知缺口；本轮没修。Gate operator不得用toolUsage重复计数或recovery通用retry作派发依据。它们不改真实request token源、领域状态或验收规则；若实际影响必要验收/安全动作，停止并上交，不默认增加实验状态模型。

未核验：GLM对effort/thinking/output/turn限额的原生执行效果；内部逐轮成本；后六节点实际成本/耗时；第四例实际readiness/工具/验收/交付；任意原生子任务的全部用量与物理停止。当前配置文件测量不补造第三例未记录的环境。

**下一步动作**：将本核对与[完整Gate候选](full-mainline-gate.md)合并交CC只读复审，判断事实/假设、完整链可执行性、储备规则与ROI。通过且必要项处置后交用户新预算与fresh运行授权；当前不启动第四例，不恢复旧样本、不沿用13/16/17余额。
