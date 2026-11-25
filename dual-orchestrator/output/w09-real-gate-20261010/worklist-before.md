# Dual 生命周期：主工作清单、改动对账与证据地图

日期：2026-09-27；2026-09-30 更新。目标阶段 P0 完整研发生命周期可靠性。W00 材料收口，W01–W09 未完成；D01–D10 均 DESIGN_FROZEN。D04 已获 CC 设计 PASS，用户明确接受提交后在途语义，详设 §6.5/§7.2/§8.25/§8.27 为当前依据。撤销底层不可绕过、硬链接/内核实验前置，旧 FAIL 原样保留。冻结仅固定设计与验收义务。用户随后授权进入下一步，B01 已完成实施/离线验证并获 CC PASS，按 §7.1 收口；用户随后授权进入下一步，B02 holder 生命周期互斥已获 CC PASS 并收口；后续批次未自动启动；无提交或新真实运行授权。

本文是唯一跨批次工作索引，不重新定义验收。唯一设计依据为 [R3 设计](20260923-dual-lifecycle-reliability-design.zh-CN.md) §0/§3–§7.1；证据层次和状态沿用该文 §5 与 [engineering-checklist](../engineering-checklist.md)。后续更新本文，不再按日期复制一份新主清单。§3 的文件处置都是建议，不授权删除、还原或提交既有改动。

2026-10-09 当前补记：B01–B04 以各批末记录为准；B05 首版 C 已获用户带回的 CC 批末独立 PASS，按合同资格单族收口（CLOSED / PASS），见 §21.2–§21.3。时钟判别尚未常驻的 LOW 已登记。B06 W06/D09 具体接线已获 CC 合并设计 PASS，并获用户明确授权；实施与离线验证完成，随后用户带回 CC 批末独立 PASS，本批 CLOSED / PASS（离线范围），见 §22.4。两设计 LOW 的理由和常驻反证已落实；原预检/候选/授权前状态作为历史保留。未覆盖族 UNKNOWN，D01/I1.a/W01 与其他 W 项、真实 NO-GO 均不因此关闭，不自动启动下一批。

2026-10-09 W08 历史检查点（当前见 §23.2）：用户授权离线对账与真实 Gate 草案；本轮材料完成，作者自查 WARN / 技术 NO-GO。查询锁序 LOW 已常驻并有隔离反证；W07 确认设计提示要求复制已批精确 scope 与 GOAL 分期冲突，尚未纠正。见 §23。真实 Gate、预算、安装/提交未授权；历史待办须以本次对账重新解释，不能据 B01–B06 PASS 自动开跑。

## 1. 当前结论、工作区身份和核验范围

整体设计与执行规则已获 CC 评审通过；本清单及其覆盖地图已获用户带回的 CC 材料层 PASS（2 LOW，无 WARN/FAIL），处置见 §8。W00 收口只表示工作载体形成并经审查，不能据此宣布 W01–W09 的产品跨层批次完成或准入。

本次冻结 HEAD：`3362eb421514e75fdc4332f570df5bf274b3725e`。HEAD 不代表工作区内容。冻结材料在 [baseline.json](../../output/worklist-reconciliation-20260927/baseline.json)、[原始状态](../../output/worklist-reconciliation-20260927/status.txt)、[tracked 完整补丁](../../output/worklist-reconciliation-20260927/tracked-before.patch)。baseline 含 1317 个非生成文件的 SHA-256，包括未跟踪测试及设计；文件变化后重核相关条目。输出目录是本机证据，复制或提交本文时须一并提供所引证据，缺失不得算通过。

冻结时：33 个已跟踪修改文件（12 个生产源码/工作流资源、14 个测试/测试资源、1 个构建文件、3 个脚本、3 个文档），另有 2 个未跟踪测试源码。其余未跟踪文档/证据与生成目录分类见 §3.3，保持原样。

本轮实际工作：阅读全部 30 个非文档 tracked 改动的 diff（adapter 大段缩进另用忽略空白 diff 核对语义）、2 个新增测试全文、3 个 tracked 文档 diff；针对改动追到 runtime/worker、结果解析、资格、恢复、交付等现有消费者；读取已有关键测试断言和历史 XML。不是重新审计所有旧实现，也没有重新遍历所有 worktree 的全部日志。历史全量调查沿用 [评估](../../output/architecture-audit-20260917/assessment.zh-CN.md) 与 [案例索引](../../output/architecture-audit-20260917/case-evidence.zh-CN.md)。

对整体目标最重要的现状：

1. 合法人工 revise、严格身份和前置失败保真已有实质改动及序列断言，不能从零重做。
2. 执行失败原因仍存在已记录反例：不完整 process 在发布被阻止后，被第二次判定覆盖成 artifact failure；不是仅缺一份文档。
3. 当前四个 workflow 的 design/plan 都要求 `requires_approved_task_contract=true`，且保留设计/计划人工门。R3 的分期批准和有界自动推进尚未实现；不能简单删除守卫来迁移。
4. 中断测试大量证明“拒绝误推进”，并未证明“未来同冻结版本从最后确认点继续”。现有正常交付测试也不证明所有 apply/finalize 中断窗口。
5. 本次受控测试替代外部 provider，只证明实际覆盖的程序链。生成示例作为输出 fixture、全绿测试、workflow COMPLETED 都不能证明真实产品交付或模型语义质量。

## 2. 主工作清单

“部分 Implemented”仅描述现有实现，不是整项 Verified。所有关闭判定必须回指 R3 条款；下表索引待办；当前实施依据为用户设计冻结后“如果不需要 [CC review]，可以进行下一步动作了”的授权，B01 边界和结果见 §10，不能外推为提交或真实运行授权。执行者为主 agent，独立判断由用户发起 CC；最终交付由用户或其显式授权者批准。

| ID / 完整用户流程 | 契约依据 / 领域 owner | 现有事实与证据 | 剩余工作与必要设计决策 | 依赖 / 状态 |
|---|---|---|---|---|
| W00 对账与建立完整覆盖地图 | R3 §7.1；文档索引不持有运行 authority | 本文 §3 覆盖所有相关 dirty 文件；§4 映射测试与缺口；冻结身份可复核；CC 材料层 PASS | 2 LOW 处置及限制见 §8；后续材料实质变化重核受影响结论，不以本次抽样代替产品证明 | 材料任务收口；独立评审与作者修订核验分列，不是产品能力完成 |
| W01 人工决定→作者修订→复评→重新批准；失败原因→合法补足后续作 | I1/I1.a；core/ledger/revision/recovery | B03 合法续作与返修/重批，B05 C 单族独立 PASS，最新受控序列见 §23 | 其余诊断族 UNKNOWN，按用户 C/真实反复阻塞触发，不自动第二族；单族及合法离线流程不外推 I1.a 全体 | 本族 CLOSED/PASS；完整 W01 未关闭，真实能力按 Gate |
| W02 输入准备→provider→验证→发布→node/run/recovery | I2；adapter、唯一 outcome/finalizer、恢复 read model | B01/B03/B04 离线与独立 PASS；E02 常驻、权限双通道/固定完成/失败保真，见 §23 | 真实 native/工具能力待 Gate；首版全量声明已接受，具体运行中补声明入口未证，真实反复阻塞才重评 | 实施/具名离线链有据；总体真实能力未证 |
| W03 目标批准→只读设计/计划/复评→精确执行批准→首次实现 | I3/I1；TaskContract/proposal/qualification/adapter | B03 分期合同、B04 原生 C/X 接线与独立 PASS，见 §23 | G01 分期提示已纠正并配对验证，见 §23.1；实际选定 native 装配/合法请求续作按 Gate 核验 | 编译/控制链与提示修正离线有据；CC 受影响独立 PASS 已接收，真实 NO-GO |
| W04 必需验证可行→实现→自测→独立验收；缺材料补证据 | I4/I1/I2；D05、EvidenceCollection、ExternalVerificationEvidence | B03 D05 程序/模型/人工分立、实际退出码按批准条款、漂移与合法复用、返修/补证据离线有据，见 §23 | fresh 各验收项绑定实际方法/环境/权限/责任/证据出口，未知工具不假装可用，不默认通用环境认证 | D05 接线离线有据；具体 fresh 出口 Gate 前核齐 |
| W05 同冻结身份中断恢复；外部未知人工裁定；终态不重开 | I5/I1；D06/D07 ledger/runtime/worker/kernel | B02 holder、B03 本地协议/构造与身份、B04/B05/B06 写点/冷 JVM 独立 PASS，见 §23 | kernel 内部 I/O 专项与部署拓扑未全证，保留；首 Gate 限单 JVM 路由/ClassLoader，实际身份/停止核验不能以测试 JVM 代替 | 具名协议离线有据；I5 全故障/部署普适性未关闭 |
| W06 验收满足→候选批准→目标 Git 应用→实际提交→交付确认/中断处置 | I6，§4 交付行；candidate、DeliveryApplicationService、Git、query | T12；B06 四变体×inline/worker 受控完整批准链接临时真实 Git apply/人工实际 commit/finalize；写点、冷 JVM、拒绝/续作与同源投影证据见 §22.3；CC 批末独立 PASS 见 §22.4 | 本批实施/离线接线范围收口；LOW-1 查询锁序常驻化已登记，现有一次性反证仍有效；真实目标 Git、浏览器视觉与真实客户端未证，归组合准入/Gate；不自动 commit 或盲目重 apply | W04 验收/批准；W05 本地边界；B06/W06/D09 批次 CLOSED / PASS（离线范围）；真实交付证明未关闭，NO-GO 保持 |
| W07 机器身份与语义义务分离、有效修订与提示负担 | I1/I2 GCTW；原 prompt/context/contract/correction owner | 单一示例/typed 去重/受限 correction 已有；本轮逐字段案头表完成，见 §23 | G01 已在既有 prompt/template owner 修正并扫 intake/design/plan/reviewer/implement，共同保留严格身份；实际模型负担待真实 Gate，不承诺 token 节省 | 案头审计和 G01 离线修正已获 CC 合并独立 PASS；真实回应负担 NOT PROVEN |
| W08 组合回归/各投影→可审查运行 Gate | R3 §4/§5/§6；全部消费者/CLI/API/UI/report | §23 历史查询 LOW 常驻 6/0 与隔离反证；§23.1 本轮单轮 1755/206/0、654 输入同一性及新发行核验；CC PASS 接收见 §23.2 | 本轮 G01/G02–G04 见 §23.1；G05 本地批准/实际初始化前提见 §23.3；真实握手、业务 occurrence/工具证据在获准运行内核验；browser/真实 worker 未证，首个 CLI/INLINE 不关闭整体 W08 | 准备批次 CLOSED/PASS（离线）；CC 合并独立 PASS 见 §23.2；G05 本地准备批准及前提核验见 §23.3；真实运行仍未授权 |
| W09 fresh 合法任务真实研发/测试验收/批准交付 | R3 §0/§6；真实 evidence/candidate/目标 Git | §23 fresh Python 任务与 Gate 已受审；§23.3 G05 本地准备已批准并建立新实例，真实流程未执行 | CC 已合并核对本轮 G01/发行/Gate；G05 当次前提/需求/目标/预算及真实运行明确批准后才运行；自然未触发分支 NOT EXERCISED、不恢复旧样本 | 本地准备 PASS / 新实例已建立；真实流程 NOT RUN / NOT AUTHORIZED，见 §23.3 |

### 2.1 次序、依赖与独立评审触发

W00 的实际载体已获材料层评审，随后确定 W01/W02 的字段/阶段契约与 W03/W04 的联合详细设计，随后将相关写序纳入 W05/W06；W07 与前述语义一同评估，避免孤立优化。详细设计可交叉推演，不意味着串行等待全部设计才能思考下一项，也不授权代码实施。未来按完整用户流程组织批次；W01–W07 不能分别关闭后就假定 W08 组合成立。

W01–W06 均跨 R3 §2 多个 owner 且涉及 §4 transition，W07 改变 context/contract 消费边界时同样触发；W08/W09 使用这些组合及完成结论，必须接受对应独立判断。W00 的条目结构/覆盖地图已在本次材料评审核验，范围与 LOW 处置见 §8；后续实质变化仍纳入对应批次评审。纯文档指纹修正可不触发产品跨层评审，但不得改变验收条款或 silently 重用失效证据。

### 2.2 必要决策与本轮边界

| 决策 | 已有可复用位置 / 需比较的内容 | 详细设计结束条件；目前没有替用户选择的事项 |
|---|---|---|
| D01 续作诊断配对 | gate/decision、NodeResult 与现有失败 writer；不另建诊断 authority | 明确字段、原始证据、消费/分类条件、写入失败与冷读，不靠旧决定回填；真实合法续作与错误拒绝成对 |
| D02 执行失败保真 | AgentExecutionOutcomeResolver/AgentExecutionResultFacts/validator/finalizer | 定义未调用、过程不可信、输出不合法、产物不合法的适用条件及冲突矩阵；前后处理不会把衍生缺产物盖过决定性执行失败；同一语义由唯一规则消费。详细设计须明确 E02 常驻回归的源码落点、两执行入口及成功/contained-warning 对照的场景和断言；对应 W02 关闭须以这些常驻测试已在源码存在且实际通过为证据，不能只以矩阵或 resolver 改动关闭 |
| D03 分期批准与自动推进 | proposal/TaskContract、qualified input、四份 definition 的 gate/required refs | 目标批准与执行批准的 subject/schema、四变体逐门保留/迁移表、重大取舍和批准失效、预算耗尽出口；READY 的 decision 固化、无人工门路由、D06 写集及 reviewed/人工授权投影须冻结；不得先拆掉资格校验再补批准 |
| D04 原生权限与审批接入 | AcpxPermissionProfile/transport/adapter、既有人工决定与 Execution/Run/恢复消费者 | **当前唯一范围详设 §8.25**：正确配置既有原生能力，核对真实 allow/ask/deny 请求及回传，明确批准后续作、拒绝/过期/重复/跨调用决定和外部未知结果处置。撤销底层不可绕过、control 绝对隔离与硬链接/内核证明前置；不新增 LLM 权限裁判/安全平台。旧 FAIL 保留，D04 设计已冻结、接线尚未实施；scope、证据与最终批准仍由原 owner 守住 |
| D05 验收出口 | 现有 contract/plan/execution policy 与外部材料流程 | 必需项标识、可信方法/环境证明、检查者/时点、细化责任、失败续作；新增能力必须证明无既有替代，存在重大取舍则交用户决定 |
| D06 本地一致性 | R1 的 reconciliation / prepared mutation / generation pointer 比较与现有 writers | 选择方案并列出所有受影响写点、reader 可见性、event 幂等、冷启动与 queue/execution 桥接；不默认 journal，不以安全停止满足可续作 |
| D07 运行身份/未知执行 | 既有 workstream/run/attempt/输入批准引用 | 固定版本与合法修订输入分开、唯一 owner/写序/恢复核验点；spawn/产物写入是否发生未知时禁止盲重放；未知历史版本 INVALID |
| D08 LiteFlow 装配与隔离 | FlowKernelProvider、holder 生命周期、server/API/worker/CLI 入口 | 删除无参及 writer-only 隐式 builtin 默认；上层显式注入，下游沿统一 ledger/会话消费；记录实际配置/JVM 拓扑并证明 holder 约束有效；一次快照或 WorkstreamLock 不足；不凭怀疑换引擎 |
| D09 交付窗口 | DeliveryApplicationService/merge plan、immutable candidate、目标 Git | 每个 apply/日志/tree/plan/实际 commit/finalize 窗口均有已知/不明结果处置、原批准有效性与拒绝重做证明；不新增部署能力 |
| D10 身份回显负担 | 既有 machine context/manifest、AgentResult 契约、有限 correction | 明确哪些重复字段有可移除收益、哪些语义回应不可代填；依据 GCTW/新证据决定必要变化，不能用宽松匹配换成功率 |

这些决策落实的是 R3 已登记缺口，不增加用户目标。若事实表明某项无需代码变化，应以证据关闭该项决策并记录理由，而不是为填表硬造机制。详细设计未定时不能提供虚假的逐文件实施承诺。

## 3. 现有未提交改动对账

详细设计进展（2026-09-28）：用户已授权进入下一步，本轮形成 [D01–D10 详细设计候选](20260928-dual-lifecycle-detailed-design.zh-CN.md)。其中 §2/§3 联合展开分期批准与验证出口，§4/§5 展开续作诊断和执行事实，§6/§7/§9 联动本地恢复、执行未知与交付，§8/§10 承接只读面和身份回显；§13 逐项列未冻结证据。候选未独立评审，D01–D10 及 W01–W09 均不据此关闭，不把以前对本清单的材料 PASS 外推到新方案。本文仍是唯一工作索引。

### 3.1 行为组处置

| 组 | 已有改动的真实作用 | 建议处置 / 与 R3 的差距 | 对应工作与证据 |
|---|---|---|---|
| A01 core note 与 ledger 入口 | revise/clarify 都要求 note，保留 BLOCKED note；去掉多个 gate-purpose 重复条件 | 采纳方向；现成统一规则有价值，不是完整 I1.a | W01；T01/T02 |
| A02 人工 revision/clarify resolver | READY/BLOCKED 同等可修订；校验 resolved gate/route/subject；clarify 改读 compiled occurrence 而非被覆盖的稳定 resolution | 采纳方向；未来分期合同变化后须重核，不支持历史孤立 gate rescue | W01/W03；T01 |
| A03 原单测预期修正 | completed node 不再冒充 resolved gate；旧“正向评审不得 revise”断言移除 | 采纳；合法正例必须由新增实际序列承接，不能只剩拒绝 | W01；T01/T02 |
| A04 新人工续作序列 | 真实 adapter/runtime/ledger/LiteFlow，替代 transport，验证 note/身份/完整复评批准与写失败拒绝 | 保留；本轮完整读过；冷 ledger 是新对象读取，不一概称冷 JVM；不证明可恢复继续 | W01/W05；T01/T02 |
| A05 封闭输入准备失败 | 资格/manifest/retry/proposal 异常统一进入 machine-owned result；marker 与候选/成功/metadata 矛盾会被拒绝 | 采纳方向；需保留未标记真实缺输出拒绝，不把 reason 字符串当 bypass；未覆盖所有 dispatch 边界异常组合 | W02；T03 |
| A06 执行前发布资格与 finalizer | 发布前调用只读 assessPublication，阻止有效 JSON 掩盖 rate-limit；最终再次 resolve | **需调整**：未消除 E02 所示二次判定改写；不能把“调用了同一 resolver”当事实不变证明 | W02；T04/T05 |
| A07 恢复 read model 与 CLI/API 测试 | input_preparation_failure 显示原 reason/code/details、人工检查、retrySafe=false | 保留；投影消费不构成 I1 配对 authority；UI/report 同一失败的展示尚需核验 | W01/W02/W08；T06 |
| A08 manifest/retry/存储失败测试 | 从 handler 单返回升级为 inline/worker 到最终 ledger；保留零 transport、原异常与原因 | 保留；不是断电/任意 I/O 原子性证明 | W02/W05；T03 |
| A09 四变体正常链/终态测试 | 逐门到 COMPLETED；resume/worker/retry/approve 不产生新调用或改写 | 保留并扩展目标断言；仍按旧批准拓扑、fixture 自己提供 model 成功，未执行目标 Git apply/commit | W03/W06/W08；T14 |
| A10 failure 分类建议 | TOOL_LIMITATION/CONTEXT_INSUFFICIENT 提示人工补前置/补证据或明确返工 | 采纳方向；不能靠自然语言建议决定路由，实际 owner 仍是已验证类别及 definition | W04；T08 |
| A11 四份 workflow 边 | 两类缺前置从自动实现 loop 改到现有 implementation repair gate、normal transition | 保留该分流意图；**后续需随 D03/D05 重核**：类别本身不证明等待可解决，人门必须有真实续作；不能因此继续保留所有中间审批 | W03/W04；T08 |
| A12 定义/预算/sibling 测试 | 同步新路由；预算 slice 只取 loop；AuthoredBoundary entry 数从 8 改为 6 | 保留变更意图；精确计数/hash 仅漂移检查；新的非 loop 人工补前置循环的有界性不能由旧 loop 预算测试证明 | W01/W04/W08；T07/T08 |
| A13 受控 smoke fixture | 取实际 run identity，不再从可能含旧 envelope 的 prompt 取末次匹配；补 typed 字段、fake build-info/session、独立 HOME | 保留用于受控 CLI 验证；不证明真实 adapter policy、模型理解或权限隔离；脚本本轮未执行 | W08；T14 |
| A14 文档/旧 Gate | 明示历史快照、当前入口、FZ8K 已执行并边界停止 | 保留历史事实；旧授权、旧 JAR、旧 Gate 不能充当本轮准入 | W00/W09；E04 |

上述无一建议直接整批丢弃。需调整项在既有方向上补足，不在本轮回退或覆盖任何代码。

### 3.2 全部相关文件逐项映射

下表路径相对 `dual-orchestrator/`。每行指向 §3.1 处置，不另建一份行为定义。`M` 为 tracked dirty，`?` 为未跟踪源码；完整字节身份见 baseline。

| 状态 | 文件 | 行为组与处置引用 |
|---|---|---|
| M | [docs/design/dual-orchestrator-current-architecture.zh-CN.md](../../docs/design/dual-orchestrator-current-architecture.zh-CN.md) | A14 |
| M | [docs/plans/__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md](../../docs/plans/__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md) | A14 |
| M | [docs/testing/verification-gates/20260909-okx-profile-aligned-fresh-e2e.md](../../docs/testing/verification-gates/20260909-okx-profile-aligned-fresh-e2e.md) | A14 |
| M | [dual-adapter-acpx/pom.xml](../../dual-adapter-acpx/pom.xml) | A04（仅增加 test scope 的 LiteFlow 依赖） |
| M | [dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java](../../dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java) | A05 |
| M | [dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentTaskHandlerTest.java](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentTaskHandlerTest.java) | A05 |
| M | [dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AuthoredManifestDispatchTest.java](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AuthoredManifestDispatchTest.java) | A08 |
| M | [dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/RetryFailureContextSequenceTest.java](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/RetryFailureContextSequenceTest.java) | A08 |
| M | [dual-cli/src/test/java/dev/dual/cli/RecoveryShowCommandTest.java](../../dual-cli/src/test/java/dev/dual/cli/RecoveryShowCommandTest.java) | A07 |
| M | [dual-core/src/main/java/dev/dual/core/workflow/HumanGateActionSemantics.java](../../dual-core/src/main/java/dev/dual/core/workflow/HumanGateActionSemantics.java) | A01 |
| M | [dual-ledger-file/src/main/java/dev/dual/ledger/file/RecoveryAssessment.java](../../dual-ledger-file/src/main/java/dev/dual/ledger/file/RecoveryAssessment.java) | A07 |
| M | [dual-ledger-file/src/main/java/dev/dual/ledger/file/RoutingEngine.java](../../dual-ledger-file/src/main/java/dev/dual/ledger/file/RoutingEngine.java) | A01 |
| M | [dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowGateResolutionTest.java](../../dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowGateResolutionTest.java) | A01 |
| M | [dual-runtime/src/main/java/dev/dual/runtime/handler/AgentExecutionResultFacts.java](../../dual-runtime/src/main/java/dev/dual/runtime/handler/AgentExecutionResultFacts.java) | A05 |
| M | [dual-runtime/src/main/java/dev/dual/runtime/handler/AgentRunMetadataFinalizer.java](../../dual-runtime/src/main/java/dev/dual/runtime/handler/AgentRunMetadataFinalizer.java) | A06 |
| M | [dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactContractValidator.java](../../dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactContractValidator.java) | A05 / A06 |
| M | [dual-runtime/src/main/java/dev/dual/runtime/handler/FailureArbiter.java](../../dual-runtime/src/main/java/dev/dual/runtime/handler/FailureArbiter.java) | A10 |
| M | [dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractRevisionContextResolver.java](../../dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractRevisionContextResolver.java) | A02 |
| M | [dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json](../../dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json) | A11 |
| M | [dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json](../../dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json) | A11 |
| M | [dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json](../../dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json) | A11 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/handler/ArtifactContractValidatorTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/handler/ArtifactContractValidatorTest.java) | A05 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/handler/AuthoredBoundarySequenceTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/handler/AuthoredBoundarySequenceTest.java) | A12 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java) | A03 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/lifecycle/AcceptanceRepairWorkflowDefinitionTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/lifecycle/AcceptanceRepairWorkflowDefinitionTest.java) | A12 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/lifecycle/AcceptanceRevisionBudgetTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/lifecycle/AcceptanceRevisionBudgetTest.java) | A12 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/lifecycle/FullWorkflowDefinitionTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/lifecycle/FullWorkflowDefinitionTest.java) | A12 |
| M | [dual-runtime/src/test/java/dev/dual/runtime/lifecycle/SmallWorkflowProfileDefinitionTest.java](../../dual-runtime/src/test/java/dev/dual/runtime/lifecycle/SmallWorkflowProfileDefinitionTest.java) | A12 |
| M | [dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json](../../dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json) | A11 |
| M | [dual-server/src/test/java/dev/dual/server/WorkstreamApiControllerTest.java](../../dual-server/src/test/java/dev/dual/server/WorkstreamApiControllerTest.java) | A07 |
| M | [scripts/fixtures/emit-strict-agent-result.py](../../scripts/fixtures/emit-strict-agent-result.py) | A13 |
| M | [scripts/semantic-boundary-smoke.sh](../../scripts/semantic-boundary-smoke.sh) | A13 |
| M | [scripts/smoke-test.sh](../../scripts/smoke-test.sh) | A13 |
| ? | [dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/HumanRequirementContinuationSequenceTest.java](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/HumanRequirementContinuationSequenceTest.java) | A04 |
| ? | [dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/WorkflowFirstLifecycleSequenceTest.java](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/WorkflowFirstLifecycleSequenceTest.java) | A06 / A09 / A11 |

### 3.3 其他未跟踪材料

冻结状态中还有 59 个未跟踪文档/证据文件：8 份设计/计划、3 份 verification-gate 文档及 48 份 Phase A 能力证据。另有 627 个 output/playwright 生成文件（计数为冻结时 Git 可见文件，不含被忽略内容）。逐文件路径见 status.txt。这里只分类保护，不声称本轮重审了其全部内容。

- 历史设计、交接、checkpoint：保留为来源，当前有效设计入口只有 R3；checkpoint 的自查/PROVEN 主张需按下文证据边界理解。
- Phase A 证据和旧网络 Gate：保留，不恢复 R4/Phase B，也不重新采集以凑本轮证据。
- output/playwright：只引用所需报告和反证，不恢复旧 workstream，不统一清理或提交。

## 4. 测试覆盖与证据地图

`源码核验`表示读过指定断言/fixture；`本轮复跑`只对 E01 确认的具体类生效；`历史报告`不自动绑定当前工作区。案例与文件存在均不等于整个义务已 PROVEN。T01–T15 是证据索引，不是新的验收定义。

| ID / 用户流程与契约引用 | 已核对的测试及实际断言 | 层次 / 覆盖边界 | 尚缺的证明 |
|---|---|---|---|
| T01 正向评审后人工 revise、自动修订及 clarify；I1 | HumanRequirementContinuationSequenceTest：`humanRevisionReachesSameAuthorReReviewAndBothApprovals`、`automaticRevisionAndContractClarificationReenterBothApprovalGates` | 真实 pipeline + transport 替身；inline/worker；该主 fixture 默认 contract variant；到两次批准和下一作者 qualified input | 不能外推所有 variant 的每条 revise；I1.a 配对缺失；新批准时序须更新期望来源 |
| T02 非法决定/身份、写间失败；I1/I5 | 同类 `missingRevisionAndClarificationNotesAreRejectedBeforeAnyWrite`、`persistedHumanDecisionCannotBeForgedOrDetachedFromItsSubject`、`interruptedHumanDecisionCannotDispatchAnOrphanOrRepeatApproval` | 两模式；missing_gate/action/subject/note/iteration/proposal_sha；gate/node/iteration/successor/event/current failpoint，零非法 provider | 证明安全拒绝，不证明 I5 幂等续完；新建 ledger 不等于进程重启；没有穷举所有写窗口 |
| T03 输入准备失败；I2 | AuthoredManifestDispatchTest 的 identity/body/partial/I/O/sibling；RetryFailureContextSequenceTest 损坏诊断；ArtifactContractValidatorTest `inputFailureCannotCarryCandidatesSuccessOrForgedExecutionFacts`；新 Lifecycle 的存储/合同缺失后人工恢复 | validator 单测 + 实际 runtime/worker 序列；marker 正例和矛盾反例；恢复原已批合同字节后到作者/评审 | bootstrap/claim/prepared metadata/dispatch 不明边界仍需完整阶段矩阵；对后续基准漂移不能据此放宽 |
| T04 执行失败有有效候选但不能发布；I2 | WorkflowFirstLifecycleSequenceTest `providerFailureCannotPublishEvenWhenItReturnsAValidCandidate`；B01 新增 incomplete 对照 | inline/worker，阻断 RATE_LIMITED 与无 signal 的 incomplete 均核验无 sidecar；最终结果见 E05 | 不证明 D05 非必需 permission 关联或 W05 多文件耐久恢复 |
| T05 执行/产物冲突原因保真；I2 | WorkflowFirstLifecycleSequenceTest 常驻 `incompleteProcessCannotBeReclassifiedAsMissingArtifactAfterPublicationIsBlocked`；core process/envelope/artifact 交叉矩阵 | B01 两入口先红；修正后 node/run/recovery 保持 process_output_incomplete，真实有效候选仍记 valid；成功及完整过程 warning 到达独立复评，见 E05 | 已证明的 warning 是可信完整过程的非零退出；不能当 D05 permission 精确非必需证明，W02 整项未关闭 |
| T06 原因→恢复提示→CLI/API；I1/I2 | RecoveryShowCommandTest、WorkstreamApiControllerTest、RecoveryAssessmentTest；保留 preparation 对照，B01 增加 process incomplete / missing envelope 对照 | 真实 ledger fixture 经统一 RecoveryAssessment/readiness 到 CLI/API/attention；执行原因与输出合同修复不混装，见 E05 | 非真实 provider 或浏览器验收；UI/report 组合、D01 配对及恢复动作可用性仍待 W01/W05/W08 |
| T07 设计/计划义务与批准基准；I1/I3 | AuthoredBoundarySequenceTest：缺回应拒绝、损坏批准输入/部分发布、同 bytes 新批准使旧链失效、implementation/self-test 入口消费批准 plan；ContractCompileHandlerTest 的孤立 gate 拒绝；AuthoredStageSequenceTest 的自动/人工 plan 复评、两次 design revise 留存 note、两业务域批准 design→plan→消费 | 既有受控序列、冷 JVM 与字段合同；本轮读相关源码与 dirty diff | 目前仍是设计前完整 scope 批准；不证明两阶段授权，也不证明只读工具面 ；补详设 §8.21 合法内部委派、子任务越权/借权、伪造独立复评与批准的正反证据，原生能力与 Dual 消费者分别验证 ；验收分层按清单 N1–N3/S1–S2，区分两角色与写前/变更后拒绝，不隐增逐文件 native 拦截 |
| T08 REJECT→返工/补前置→自测→再验收；I1/I4 | WorkflowFirstLifecycleSequenceTest `unavailablePrerequisitesRequireHumanDispositionThenEvidenceReverification`；AcceptanceRepairSequenceTest `evidenceOnlyAndMixedRepairUseDifferentRealProducers`；SelfTestContinuationSequenceTest 三变体条件分流；AcceptanceRevisionBudgetTest | 新测试四变体×两模式×两类别×collect/revise，断言实现次数、自测/验收次数；旧序列使用受控 handler；预算测试仅 loop | 新测试不替代外部 submit 材料链；同内容重复写不能证明所有返工质量；非 loop 人门预算耗尽的完整可继续/终止出口仍需核验 |
| T09 材料准备/提交/自测/验收；I4/I5 | ExternalVerificationEvidenceTest：实际 runtime sidecar 精确 refs、幂等 submit、基准/材料篡改、partial gate writes、cold JVM 重验；EvidenceCollectionSequenceTest 作为兄弟来源 | 程序 ledger/runtime + 受控 handler/材料，inline/worker；有真正子 JVM 读取；本轮读取指定方法 | 证明材料绑定与拒绝，不证明 operator 声明的外部测试实际运行；partial writes 仍只拒绝，验证出口前置 schema 未有 ；增加 D1–D3 的迟到写入、证据基准漂移与精确复用；现有 EvidenceCollection 仅是适用路径证据 |
| T10 本地写前缀和执行桥接；I5 | MultiFileMutationRestartAssessmentTest 四组测试：initialize、gate resolve、stale recover、normal routing | 源码直接断言部分前缀无法重放、current/instance 不同代、event 重复；历史 4/0 是“缺陷评估通过”，不是恢复实现通过 | R1 14 前缀续完、reader 一致性、M6 queue/task/execution 桥接及运行身份均未证明；B02 LOW-1 的 kernel 临时文件/load/clean 故障并入 D06 同族（未运行），不得用业务文件写失败替代 |
| T11 发布后节点落盘失败；I2/I5 | WorkflowFirstLifecycleSequenceTest `publishedCandidateWithoutDurableCompletionCannotReachTheReviewerOnColdResume` | transport 写 failpoint，确认 sidecar 已存在，resume/worker 没有新增调用；同 JVM 内重建 runtime，允许异常被捕获 | 不证明新进程恢复、不证明可续完、不证明异常分类提示完整；不能命名“冷恢复成功” |
| T12 候选批准→应用→实际提交核验；I6 | DeliveryApplicationServiceTest：真实临时 Git apply、expected tree、finalize；source/candidate/parent/tree/已批引用异常拒绝；APPLIED 错 commit 保留 | 真实本地 Git + 人工构造已批 ledger，独立于实际研发链；历史 9/0，当前未复跑 | apply 后日志/plan 写失败、commit 已发生 finalize 失败等 failpoint 缺完整覆盖；与 W08 研发到交付组合未证明 |
| T13 真实语义意见与机器身份负担；I1/I2 | AuthoredBoundarySequenceTest 缺 response 不可发布；新增人工修订 fixture 根据当前 context 构造 obligation_patches；E04/GCTW | 程序严守义务有证据；新 fixture 使用 AgentResultPromptContract.render 的示例，自动配齐机器字段 | 未量化真实模型复制负担；成功 fixture 不能证明 schema 易用或独立语义正确；D10 不能靠放宽身份解决 |
| T14 全链、终态、variant 与 kernel；§4/§5/§6 | B01 全生命周期序列；B02 LiteFlowLifecycleIsolationTest、CliLedgerFactoryTest、ServerLedgerFactoryTest；原四变体/ledger 测试 | B02 同/异 chain id、同/异实例的路由/close 互斥，异常后合法路由，双 workstream ledger 正确后继，实际 CLI/server 工厂装配均通过；全模块证据见 §11 | 只证明被测 JVM/ClassLoader；构造/D06 会话迁移、D07 身份及发行部署拓扑、UI/report、真实产品仍未完成；不关闭 D08/W05/W08 |
| T15 请求补动作与主动终止；I1/I5，W01/W05 | HumanActionRequestTest：请求幂等且不 resolve，后续 revise 保留请求，旧 gate/伪造来源零写入拒绝；WorkflowFileLedgerTest 的 recovery abort 成功及非 recovery 拒绝 | ledger slice，源码核验，未在本轮复跑；请求 event append 失败后 durable request 保留，但重试不补事件 | 不证明 request 能取得所需外部能力；不证明请求事件补齐、abort 多写中断或运行中 provider 取消；不能把 request_action 当 resolving action，也不能把终态无调用外推成进程取消能力 |

测试源码定位（范围以 T 表的方法及断言描述为准）：

- [HumanRequirementContinuationSequenceTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/HumanRequirementContinuationSequenceTest.java)
- [WorkflowFirstLifecycleSequenceTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/WorkflowFirstLifecycleSequenceTest.java)
- [AuthoredManifestDispatchTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AuthoredManifestDispatchTest.java)
- [RetryFailureContextSequenceTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/RetryFailureContextSequenceTest.java)
- [ArtifactContractValidatorTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/ArtifactContractValidatorTest.java)
- [RecoveryShowCommandTest](../../dual-cli/src/test/java/dev/dual/cli/RecoveryShowCommandTest.java)
- [WorkstreamApiControllerTest](../../dual-server/src/test/java/dev/dual/server/WorkstreamApiControllerTest.java)
- [AuthoredBoundarySequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/AuthoredBoundarySequenceTest.java)
- [ContractCompileHandlerTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java)
- [AcceptanceRepairSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/AcceptanceRepairSequenceTest.java)
- [SelfTestContinuationSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/SelfTestContinuationSequenceTest.java)
- [AcceptanceRevisionBudgetTest](../../dual-runtime/src/test/java/dev/dual/runtime/lifecycle/AcceptanceRevisionBudgetTest.java)
- [ExternalVerificationEvidenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/ExternalVerificationEvidenceTest.java)
- [EvidenceCollectionSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java)
- [MultiFileMutationRestartAssessmentTest](../../dual-ledger-file/src/test/java/dev/dual/ledger/file/MultiFileMutationRestartAssessmentTest.java)
- [DeliveryApplicationServiceTest](../../dual-runtime/src/test/java/dev/dual/runtime/delivery/DeliveryApplicationServiceTest.java)

- [AuthoredStageSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/AuthoredStageSequenceTest.java)
- [LiteFlowPrototypeKernelTest](../../dual-flow-liteflow/src/test/java/dev/dual/flow/liteflow/LiteFlowPrototypeKernelTest.java)
- [HumanActionRequestTest](../../dual-ledger-file/src/test/java/dev/dual/ledger/file/HumanActionRequestTest.java)
- [WorkflowFileLedgerTest](../../dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowFileLedgerTest.java)

### 4.1 本轮与历史执行记录

| 证据 | 读取/执行结果 | 可支持和不可支持的结论 |
|---|---|---|
| E01 本轮离线聚焦复跑 | 10 个测试类，97 tests / 0 failures / 0 errors / 0 skipped；BUILD SUCCESS，退出码 0；[逐类结果及报告 SHA](../../output/worklist-reconciliation-20260927/focused-results.json) | 只对被选测试及冻结代码有效，不是全 reactor / 真实模型 / 整体交付证明；CLI/server、交付和 R1 评估测试未在本轮复跑 |
| E02 [process-failure-counterproof.xml](../../output/architecture-audit-20260917/process-failure-counterproof.xml) | 已解析：2 tests / 2 failures / 0 errors；预期失败的反证 | 原因覆盖已有直接证据；未在本轮重建隔离修改，不谎称今日复现；当前同一代码链仍在，必须回归 |
| E03 [历史测试索引](../../output/worklist-reconciliation-20260927/historical-test-inventory.json) | 已解析 186 个 suite 记录，包含不同轮次及重叠捕获，不相加成总通过数。历史新人工序列 12/0、新全链 12/0、manifest 11/0、R1 4/0、交付 9/0 | XML 缺源码身份绑定，且部分测试以证明已知坏状态为成功，不能整包标为当前 Verified；旧 92 tests 的叙述不作为本轮执行数 |
| E04 历史 case 评估与交接 | 沿用 §1 链接，不重放、不恢复；旧 Gate 与运行配置不同 | 提供问题族与用户流程来源，不提供当前组合成功率或成本收益；不把未完成样本全部归因 Dual |
| E05 [B01 结果与源码身份](../../output/lifecycle-batch-01-20260930/test-results.json) | 2026-09-30 全模块离线回归：183 suites，1,550 tests / 0 failures / 0 errors / 0 skipped；源码指纹测试后无漂移；E02 双入口先红后绿，已常驻 | 作者自查通过、CC PASS（处置见 §10）；受控 fixture 不证明真实模型质量/交付，D05 permission 义务、W05 耐久恢复及其他 W 项不因此关闭 |

E01 只运行 transport/handler 替身等既有离线测试，不调用真实 provider。命令：

```sh
rtk proxy mvn -o -pl dual-adapter-acpx -am \
  -Dtest=HumanRequirementContinuationSequenceTest,WorkflowFirstLifecycleSequenceTest,AuthoredManifestDispatchTest,RetryFailureContextSequenceTest,ArtifactContractValidatorTest,WorkflowGateResolutionTest,AcceptanceRepairWorkflowDefinitionTest,AcceptanceRevisionBudgetTest,SmallWorkflowProfileDefinitionTest,FullWorkflowDefinitionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

命令在 `dual-orchestrator/` 运行，于 2026-09-27 23:01:55 +08:00 完成，耗时 1 分 39 秒。输出：[focused-tests.log](../../output/worklist-reconciliation-20260927/focused-tests.log)。未修改测试源码；仅将本轮实际执行的 10 份 XML 保存到 `output/worklist-reconciliation-20260927/focused-reports/`，逐份引用和指纹见 E01，不混入历史 target 中未重跑文件。

## 5. 已追到的写入与消费切面

本节记录当前代码事实，供后续详细设计展开 R3 §4；不是已经选择的恢复协议。T15 的非 resolving 请求与 abort 同样须纳入 W01/W05 的写序及失败切面，不能因不在成功主线而豁免。每项具体落点须在实施方案冻结时写齐 pre/post 与每个写点前/间/后；本表不能提前授予实施准入。

| 链路 / 当前 owner | 已核对的顺序与下一消费者 | 当前失败后果 / 后续必须决定 |
|---|---|---|
| 人工 revise/clarify；RoutingEngine | 核验 gate/note/subject → resolved gate → node 完成/iteration/后继及 event/current 等分支写入 → TaskContractRevisionContextResolver/LateReader | 原子文件不是原子 transition；T02 在若干前缀拒绝重批/重试。D01/D06 须给未来合法本地中断的续完出口 |
| inline / worker 执行；WorkflowRuntime/AgentWorker | task/node execution/running 写入 → handler → EvidenceCollection 约束 → ArtifactContractValidator → AgentRunMetadataFinalizer → task finished → node execution finished → completeAndRoute | handler 前置拒绝已保真；validator/finalizer 异常和完成写失败会留跨记录状态。两个入口都存在，不能只覆盖 inline 或只看 node.result |
| artifact 发布；ArtifactContractValidator/finalizer | B01：typed process + envelope/领域校验 → 唯一 outcome/run 记录 → 合格时 sidecar/派生文件 → ledger 路由；inline/worker 同入口 | run 写失败零新 sidecar；发布 I/O 失败保留执行事实、不伪造合同失败；run.json 仍直接写入，D06 多文件协议与冷启动续完尚待 W05，不把阶段保真当耐久完成 |
| 外部材料；ExternalVerificationEvidence | prepare 基准 → 固化材料/manifest → resolved gate/node/event → self-test 消费 → acceptance 消费 | T09 证明部分重复提交幂等与损坏拒绝，但 gate/node 中间失败拒绝重放；材料 hash 不证明外部行为执行 |
| Git 交付；DeliveryApplicationService | 完成/候选/源目标基准核验 → compatibility artifacts → plan READY → Git apply → stdout/stderr → staged tree → plan APPLIED；外部实际 commit 后 finalize 校验 → COMMITTED | apply 已变更目标后日志/plan 写入可失败；现有 catch 可能记录 FAILED，不能据此推导 Git 未变更。D09 必须以目标 Git 事实解释是否可重试；正常服务单测不覆盖全部窗口 |

## 6. 当前到真实交付 Gate 的距离

本节保留原材料形成时的评估快照；B01–B06 后的当前距离以 §23 为准，不将本节旧“待实施”当作重新开工指令。

按 R3 §6 逐项记录。本轮推进的是“明确阻塞与证据”，没有把未实现功能写成技术 GO，也没有把未授权变成技术问题。

| Gate 项 | 本次变化 | 剩余阻塞 / 对应工作 |
|---|---|---|
| 冻结组合 | 更近：已冻结源码/文档 baseline 与本轮测试命令 | binary/schema/template/acpx/config/runtime identity 尚未成套冻结；W05/W08 |
| 冻结运行清单 | 持平：本次是评估索引，不是产品运行清单 | 需实际 run/occurrence/批准/干预/交付引用及导出可用性核验；W08/W09 |
| LiteFlow 隔离 | B02：实际 holder monitor 覆盖完整生命周期，原代码并发反证先红后绿；产品缺省/显式 kernel 装配已核验 | D08 整项仍待构造/D06 读会话联合迁移与 D07 身份；实际发行部署拓扑未由测试 JVM 证明；B02 CC PASS 已收口 |
| 离线基础 | 更近：逐文件对账、既有断言地图和聚焦复跑；E02 缺口显式化 | W01–W07 所需详细设计与反证尚未完成；不能以 E01 绿关闭 |
| 验证出口 | 持平：已有外部材料链可复用，不等于必需项准入已实现 | D03/D05 需联合确定；不预先要求任意浏览器/DB 能力，按批准任务决定 |
| 成本与运行限额 | 持平 | 当次 Gate 才落实上限、监测及实际停止方式；W08，不先跑模型探索 |
| 金额 | 持平 | 保留 billing/token-only 等既有语义，不拿旧估价当账单；W08 |
| 交付 | 持平 | 正常 Git 测试与研发链证据分离，未知外部窗口待 D09，当前产品未交付 |
| 新缺陷止损 | 更近：E02 和恢复/测试主张差距已登记 | 后续失败按 R3 §7.1 重开对应项及依赖，不另加补丁任务掩盖 |

技术状态：NO-GO。授权状态：只允许本轮材料与普通离线核验；实施、提交、新 authority、真实运行/历史恢复未授权。不存在以清单百分比替代 Gate 的总完成率。

## 7. 自查、残余与下一交付

Boundary Review：产品仍为 DESIGN GAP；材料独立评审 PASS 及本轮处置见 §8，不是产品 PASS。边界 owner 继续沿用 R3 §2；本文及清单只投影证据，不产生批准、运行或恢复 authority。本次无产品规则/生产代码/测试源码变更。

材料校验见 [material-verification.json](../../output/worklist-reconciliation-20260927/material-verification.json)：对比冻结文件指纹、核对 35 行文件映射与文内链接、校验本轮报告指纹和测试计数。既有文件仅更新 R3 与 ACTIVE 的材料状态/入口，新主清单和本轮证据独立新增；这项机械校验不代替内容独立评审。

独立评审应检查本清单是否遗漏用户流程、把测试层次说高、错误地采纳/放弃既有改动，以及 D01–D10 是否确属已承诺目标所必需。已记录的四项执行规则残余，结合本清单逐项复核，不再展开新治理工程。

尚未完成：全部未改动代码逐行审计、完整 UI/report 行为验证、真实 provider 行为、M6 全故障切面、默认部署拓扑、冻结运行清单导出可用性、只读隔离与豁免方案、I1/I3–I6 详细写序。已读过方法不等于已验证整项。本轮按实际结果记录 E01，不承诺补跑无关全套以凑覆盖。

下一份实质交付应是本清单所指向的必要详细设计决策和相关完整 transition，而不是新增一套整体架构或把代码立即提交。具体代码范围由所选方案及本次对账确定；任何改变用户目标或新增 authority 的选择需明确交用户决定。


## 8. 2026-09-28 材料评审处置

用户带回的 [CC 原始评审](../../output/worklist-reconciliation-20260928-review/cc-review.txt) 结论为材料层 PASS，2 LOW，无 WARN/FAIL；同意两项修正后 W00 材料任务收口。其方法为独立抽样，不是 35 文件全量复审，尚未核验部分以原报告为准。本次增量由主 agent 核验，未另经 CC 复审，不将作者自查记为独立通过。

- LOW-1：采纳“常驻反证不得漏出关闭证据”的要求，补入 D02，并区分两个阶段。D02 详细设计必须确定常驻回归落点、场景及断言；W02 实施关闭必须提交源码中实际存在且通过的反证及成功/contained-warning 对照。未照抄“测试源码存在作为详细设计结束条件”，因为那会把实施成果倒置为设计前置。本次不新增测试、不修改 resolver，E02 仍未解决。
- LOW-2：W08 剩余工作显式加入 UI/report 与 CLI/API 同源消费核验，覆盖状态、资格、原因和真实下一步；T06 的缺证据标记保持，补一行清单不是完成投影验证。

修订前材料已随评审保留；本次 [材料校验](../../output/worklist-reconciliation-20260928-review/material-verification.json) 单独记录，不覆盖 9·27 的指纹或将其 97 项离线结果冒充今日复跑。只修改三份文档及新增本次证据，未改变产品事实 owner、运行语义、授权范围或验收标准。问题来源是必要义务未同时写入决策结束条件和执行项；本次通过原有 D/W 引用补齐，不新增流程或治理层。

W00 材料任务收口不解除 D01–D10 的详细设计缺口，也不授权实施、提交、真实运行或历史恢复。下一步仍按 §2.1 形成必要详细设计，不能从材料 PASS 直接跳到修代码。

### 8.1 详细设计候选评审后的进度（2026-09-28）

[详细设计 §14](20260928-dual-lifecycle-detailed-design.zh-CN.md#14-本次评审处置与核验边界)登记候选版及后续修订版的 CC PASS。最新复审确认 READY 正向账本表示和 BUSY/pending 投影义务解决，LOW-3 守线维持；D03 独立复核项已移到完成侧，但 JSON/写集/消费者迁移仍待冻结。本轮新增 LOW-1 的 provider 集合限制选项已由作者补入 §8.1，增量未经独立复审。首轮 LOW-1 的 signal/非必需义务关联仍待 D02 冻结。本清单原有 D02 常驻回归及 W08 UI/report 两项文字修订已经存在，本次未重复计作新增成果。

D04 的[本机静态核验与选项](../../output/detailed-design-review-20260928/d04-feasibility.md)发现：pinned Codex adapter 常规 read-only mode 实际传 workspaceWrite；现有模式名不能证明执行面只读。已向用户提交 adapter 强制绑定、限制只读 provider 集合、OS 隔离和拷贝工作区的差异与成本（新增选项以详细设计 §8.1 为准），待方向决定，不擅自变更 adapter authority、评审分工或降低验收。D01–D10 仍未整体冻结，W01–W09 均未 Promoted；本次仅改三份文档及保存核验证据。

### 8.2 D04 方向确认与成本收益止损

用户随后同意优先细化现有 adapter 的强制只读绑定，保留 Claude 作者／Codex 评审。该决定覆盖详细设计和既有普通核验；实施、adapter 修改、真实 provider 与其他替代方向仍未授权。§8.1 的“待方向决定”是此前状态，以本段及详细设计 §8.2–§8.7 为当前入口。

本轮已交付[绑定接口核验及成本边界](../../output/d04-readonly-binding-design-20260928/binding-assessment.md)、候选字段/消费者、完整 session 与失败路径、成对反证和核验顺序。静态接口存在不等于只读已强制；Codex 在无 execute 条件下的必要读取、Claude 有效工具/配置、恢复传播与 writer 归属仍未证明，D04 未冻结，W03/W08 未完成。

随后用户带回该绑定候选的 CC PASS（2 LOW，无 WARN/FAIL），处置见详细设计 §14.3。§8.7 已补现有 ACP 读取比较及 readOnlyBinding 程序写点/归属盘点；同时纠正复审中 app-server FsReadFile/FsReadDirectory 与 ACP fs/read_text_file 的混同，不将现有客户端接口视为已接通模型工具。两项文字修订完成不等于可行性或写集已冻结；成本收益止损覆盖所有受影响边界，不仅 Codex 读取。

用户明确要求：后续相关实施若发现成本与收益偏差，停止等其确认。此要求也约束设计阶段发现的扩围。按详细设计 §8.6 对照预期与实际证据，记录在当前 D04/W03 条目；需要引擎核心、额外工具服务/隔离平台，或损失读取/评审/合法续作收益等情况不得先做后报。停止受影响工作并提交成本、目标影响与选项，不能擅自切到同 provider、降低验收或覆写用户改动。其他独立且已授权的核验不受影响。

2026-09-28 可行性探针：[离线报告](../../output/d04-feasibility-probe-20260928/feasibility-result.md)记录 7 项真实宿主 handler 断言及 pinned Codex 关闭 shell 开关的解析结果。宿主读可用、写/terminal 可拒绝，但 terminal=false 单独不强制；Codex 常规模型→ACP 读取未找到接通证据，原生无 execute 下读取仍未证明。D04/W03/W08 不据此关闭；下一证据是有界原生能力实验，授权和 Gate 沿详细设计 §8.5/§8.8，不自动进入产品实施或真实 provider 运行。

### 8.3 D04 受限执行候选与首轮有界实验

用户已同意以“经过证明的必要受限执行”替代绝对禁止 execute，并授权有界原生能力实验；结论确定前不承诺可行、不进入产品实现。业务不变量、Claude 作者／Codex 评审、身份/scope/批准/证据和恢复义务不变。当前候选及授权边界见详细设计 §8.9，[实验目录](../../output/d04-restricted-execution-20260928/)保存 Gate 与实际证据；§8.2 的无 execute 调查及先前未授权表述作为历史记录保留。D04/W03/W08 不因要求调整而关闭。

实验结果：[报告](../../output/d04-restricted-execution-20260928/result.md)。Codex 原生命令沙箱 16 项切片通过；真实模型层因作者探针关闭必要工具宿主而 INVALID（零命令），输入 27,376 token、输出 236 token。初始化失败没有在模型调用前阻断，是本轮验证链缺口；已停止，不自动补测。D04/W03/W08 仍未完成；补测前修正工具前提/停止条件并确认增量预算，不进入产品实现。

### 8.4 D04 补测准备（确认前记录）

按用户要求完成[工具前提与停止条件修正](../../output/d04-retest-preparation-20260928/preparation-result.md)，10 项离线检查通过，未新增 provider 调用。Gate 建议 1 次启动/新 session/任务，gpt-6-astra low；90 秒及第 6 个可观察命令触发停止，零自动补跑，不承诺 token/金额硬限。等待用户确认增量预算后才补测，详见详细设计 §8.10。D04/W03/W08 均保持未完成，旧 INVALID 不覆盖；无产品/adapter 实现。

### 8.5 D04 获批补测结果（当前）

用户确认增量预算后，仅启动一次 Codex 原生模型任务，获 **PASS_NATIVE_MODEL_SLICE**：[报告](../../output/d04-retest-preparation-20260928/result.md)。22.63 秒、两个实际命令，未知随机数据读取/计算正确，产品/control 两次 append 均遭 OS 拒绝，样例指纹不变。input 43,262（含 cached 28,032）、output 337，subscription_unpriced；不宣称成本最优。模型额外读取宿主 RTK.md，未证明样例外零访问或配置/指令完全隔离，须纳入 D04 环境与读取范围核验。

作者完成证据自查，尚未经 CC 独立复审；旧 INVALID 不改。原生命令/模型的窄切片不关闭 D04/W03/W08，Claude、acpx/adapter/session/其他工具/可信 writer 仍待证，详见详细设计 §8.11/§13。本次预算耗尽，无自动补跑、产品/adapter 实现或新增授权。

### 8.6 Claude 原生实验结果与停止

用户授权有价值的后续实验；本轮选择尚缺的 Claude 读取能力，结果为 [INVALID_AUTHENTICATION](../../output/d04-claude-native-20260928/result.md)。有效工具表仅 Read/Grep/Glob，但 OAuth 服务端认证失败；零工具执行，样例不变。SDK 报告 usage/估算费用 0，不等同独立账单。探针漏识别两条 api_retry，最终错误时才停止，后续补测前必须先修正并以原始事件反证；本轮不补跑、不改凭据。

D04 尚缺 Claude 实际可用性，认证恢复前真实调用不再有价值；其余 D01–D10 无模型的字段/写序/入口核验仍可推进。不能由“不继续调用”推导“可以跳过设计直接实施”。最新状态见详细设计 §8.12，旧 Codex PASS 与旧 INVALID 均保留。

## 9. 从当前证据到完整实施的组织建议

本节回应用户“是否值得继续实验、如何实施”。它组织现有 W01–W09，不新增工作项/权限平台，不取代 §2 主清单。当前没有开始产品实施，也不将用户的条件式提问解释为无条件开工授权。作者建议尚未独立复审。

### 9.1 实验终点与开工前实际材料（2026-09-30）

按详设 §8.23 只补影响可行性选择的原生权限/停止证据；不重复有效切片，不把 D2-P 和实施后全链测试前移。Claude 已有 GLM 路由的有效读取证据，旧“只被认证挡住”判断已撤销；不重新申请登录或更换模型品牌。补测须有具体 Gate/预算，当前没有运行授权。

其余九项设计已冻结，不重开逐项材料工程；实际开工仍须完成受影响文件/既有改动对账并取得实施授权。D04 可行性不足不得冒称全案可实施，实施后的完整验收也不得凭设计冻结免除。

### 9.2 实施顺序与完整退出条件

以下是依赖顺序，不是已经冻结的逐文件任务表。批次触发与独立评审遵守 R3 §7.1；同一用户流程的依赖不能拆薄以规避评审。W05/W07 的跨批归属明确记录，不能在只完成一部分时关闭整项。

| 实施段 / 主清单归属 | 实际实现目标 | 批次出口：合法流程与拒绝/失败同验 |
|---|---|---|
| A：W01/W02 + W05 所需耐久与恢复基础 | 以既有未提交实现为基础，固定准备/执行/验证/发布原因，修订配对与恢复消费；按冻结 D06/D07/D08 实现本地提交、读一致性、event 幂等、运行身份和并发约束 | 人工决定→作者修订→复评→重新批准能走通；E02 原因覆盖反例常驻并通过；关键写点中断后同版本可续作，未知外部结果不盲重放；错误身份和终态动作拒绝。缺 W05 支撑不能把 W01/W02 的耐久链宣告完成 |
| B：W03/W04 + 相关 W07 | 联合实现目标/执行分期批准、READY 自动推进、Claude/Codex 限制绑定与真正消费者、验收项与证据出口；必要的机器字段由程序绑定，模型仍须回应语义义务 | 目标批准→安全设计/计划/复评→精确范围批准→实现→自测/验收全序列；四变体和 inline/worker 都强制；普通调用/correction/load/resume/mode 逐入口同上限，无法支持的入口明确拒绝；补证据与代码返工均能合法续作 |
| C：W06 + W05 交付外部窗口，核对 W07 剩余项 | 复用现有交付服务实现逐窗观察、批准身份和中断处置；不新建交付平台 | 验收满足→候选批准→Git 应用/提交→实际交付确认；apply/finalize 每个关键窗口能按可观察事实续作或交人工裁定，拒绝漂移批准、重复外部副作用；合法 no-change 不误判 |
| D：W08，覆盖 W01–W07 完整组合 | 整链正反序列、故障注入与冷启动；CLI/API/UI/report 统一消费；固定实际 kernel/JVM、binary/config/provider/预算和 Gate | 正常完成、revise/REJECT 返工、材料补足、预算耗尽、写入中断、未知执行、恢复和终态路径均有对应证据；不能用 test count 或 fixture COMPLETED 替代真实交付 |
| E：W09 | 实现及整链回归通过、独立评审与具体 Gate 满足后，运行新的合法真实任务 | 保留真实研发→测试/验收→人工最终批准→交付证据；未触发分支标 NOT EXERCISED，FAIL/INVALID 止损；不恢复旧样本，不重开 R4/Phase B |

### 9.3 D04 接入方式与纠偏

拟沿现有 TaskHandler/AcpxRunRequest/session/run 记录保存不可变限制 ref/SHA，与批准合同/occurrence 身份绑定；patched acpx 传递且校验 session 复用；Claude 候选采用固定工具集合与配置上限，Codex 每次调用使用真正 readOnly policy 与 never approval。mode/correction/resume 等入口不能从可变显示名继承权限；实际发出参数的程序回执进入现有 run 事实，由 transport 强制消费。回执本身不证明底层有效，仍须工具反证。准备/收尾/恢复共同核验 baseline 与可信 writer 归属；不以 .agent 路径自动豁免。

每批按主清单记录条款→修改→合法/拒绝/失败/冷读证据→剩余项，实质变化同步重核受影响清单/覆盖地图。发现同一批准范围内的实现错误，回到 owner/transition 分析、修正并重跑受影响整链；若发现原设计错误，先更新决策/关联工作项和验收，再重核受影响评审结论，不为保住进度改 PASS 标准。需要新增 authority、改变目标/范围/跨 provider 分工、引擎核心或新隔离/工具服务，或成本收益明显偏离时，停止受影响工作，提交事实/替代路径/目标影响由用户决定。普通必要验证不反复询问。

### 9.4 冻结候选材料已补，待独立判断

本轮按用户要求补齐可由代码和已有证据确定的 D01–D10 材料，入口为 [详细设计 §13/§15](20260928-dual-lifecycle-detailed-design.zh-CN.md#13-冻结候选对账与剩余准入条件当前) 与 [具体附录](../../output/freeze-review-20260928/freeze-details.md)。原 116 读写候选逐项处置并补 26 关键兄弟类，142 是审查候选数而非批准修改数；前置映射、完整合同 JSON、证据生产者、M6/恢复/交付窗口与字段保留均有具体载体。

D04 仍有原生技术阻塞，不能提交一份自称全案已冻结/可开工的报告。本次交付是冻结候选复审包，其他 D 项的作者补充亦待 CC 判断。主清单 W01–W09 不因设计材料齐备变成 Verified；普通材料/存储原语校验不替代产品测试。既有改动与历史证据保持，未实施、未发 provider、未直接调用 CC。

### 9.5 冻结候选复审已回，九项设计冻结

CC PASS，D01/D02/D03/D05–D10 依详细设计 §13/§15.1 和 [两项 LOW 补记](../../output/freeze-review-20260928-r2/review-disposition.md)记为 DESIGN_FROZEN。D08 明确删除隐式内核默认，包含内部便捷构造/读会话与测试兄弟路径；D02 的第二条非必需判定通道须使用执行批准可见、SHA 绑定的合同声明。补记由作者核验，未另经独立复审。

W01–W09 未因设计冻结而 Verified/Promoted，E02 等实施义务保持。D04 仍有原生可行性缺口，不只缺认证；全案未 Implementation Ready。接下来仍按既定路径处理 D04，之后完成整版实施范围及既有改动对账，并取得用户实施授权。此次不启动补测、不修改产品、不调用 CC；旧证据与受审包保持。

### 9.6 D04 下一步已进入，认证前置待确认（2026-09-29）

用户要求进入可行性补测；[本轮报告](../../output/d04-feasibility-followup-20260929/result.md)记录共享首错消费者 18 项离线序列通过、语法/dry-run/缺确认拒绝通过。本机 loggedIn=true 不代表服务端已接受，恢复认证的确认仍待用户回复，因此本轮未发模型请求。旧证据不改，产品工作项未完成，九项设计冻结与 D04 DESIGN GAP 状态维持；后续证据齐备才交 CC 冻结复审及实施范围对账，不自动实施。

### 9.7 纠正实验对象：既有 GLM 路径读取通过（2026-09-29）

用户明确 Claude 一直连接 GLM、Dual 不依赖模型品牌，因此 §9.6 的认证恢复前置属于错误推断，现撤销，非用户改方案。按详设 §8.14 使用本机实际 Claude Code 与既有路由，单次原生读取 PASS，10.19 秒/4 工具；[原始结果及边界](../../output/d04-claude-code-glm-20260929/result.md)。纯 JSON 提示未满足，实际写拒绝/隐藏工具/配置/scratch 未证明，不升级为 D04 冻结。运行配置保留实际模型以追溯/成本/恢复，核心流程不增加模型品牌分支；产品实现与 CC 调用均未发生。

### 9.8 剩余原生补证已交付，D04 保留具体阻塞（2026-09-29）

见 [D04 复审包](../../output/d04-native-boundary-review-20260929/review-package.md)与详设 §8.15：Claude 11 负例/合法读取/开放写对照，Codex Code Mode/patch/提权/配置与 scratch 切片已有原生证据；6 个离线 native session、零真实模型调用，无额外 agent/产品实现。原诊断汇总错误及离线重判全部保留。

D04 仍未冻结：multi_agent 开关关闭后 Codex collaboration 控制面仍暴露，强制限制未证；不得用“只读 shell 已通过”关闭该项。已补现有 adapter 接线落点与程序 writer 设计，并形成简洁 CC prompt，请其独立判断证据与必要前置，不预填 PASS。若必须改变引擎/新增平台或接受 native 自行委派，仍走用户方向决定；W01–W09 未因此完成/获准开工。

### 9.9 原生补证材料复审已回写，D04 未冻结（2026-09-29）

CC 材料层 PASS、2 LOW；详见详设 §8.16 与 [处置补记](../../output/d04-native-boundary-review-20260929-r2/review-disposition.md)。本次将网络工具上限并入 Codex collaboration 的既有能力调查，并要求后续 native 补证从修正 guard 副本派生、先完成动作对应的 typed 判定与离线止损测试，再核对新 Gate/预算；不使用旧 driver 继续试错。既有 `web_search="disabled"` 与未暴露事实已核实，隐藏调用硬拒绝仍未证明。

同时收窄评审推断：子代理拒绝文案不等于子会话实测；可选 `is_error` 不是单独成功判据；顶层 collaboration 与 exec 嵌套工具不能混同。Claude/Codex 已有切片证据不因此作废，也不外推任意能力。两项 LOW 的约束文本已吸收，剩余能力证明未完成；D04 DESIGN GAP、九项已冻结状态及 W01–W09 开工条件不变。本次仅文档与证据核对，零新增 native/模型/agent，旧评审包保留；该增量未经再次独立评审。

### 9.10 既有硬限制接口已核验，等待独立判断（2026-09-29）

见详设 §8.17 与 [接口核验报告](../../output/d04-hard-limit-interfaces-20260929/assessment.md)。两个候选配置在真实 native 请求中仍暴露 collaboration；网络工具未暴露但硬拒绝未证；零并发/根级工具白名单/ignored multiAgentMode 不可作替代。两次本地捕获预算已结束，零模型、工具或 agent 调用；未进入实现。报告保留无效命令/本地 listener 启动失败及纠正记录，不把配置读取或工具隐藏充抵强制证明。

D04 仍 DESIGN GAP，九项已冻结与 W01–W09 状态不变。已提供简短 CC prompt，请其独立核对是否漏掉现有强制接口及冻结条件；不预填 PASS。不穷尽原生源码便宣称不可能、或未经决定增加外层机制，均不允许。成本收益偏离/新 authority 仍按 §8.6 交用户决定。

### 9.11 分派探针仅完成离线设计修订（2026-09-29）

依用户授权，完成 [离线探针设计](../../output/d04-dispatch-probe-design-20260929/probe-design.md)，详设 §8.18 同步。接受 CC 材料 PASS；纠正其从 list/畸形 spawn 拒绝推导权限闭环及“零副作用”的主张。实际声明的 6 个协作工具均为 function；响应 wire 与 native 拒绝阶段仍待追溯，不猜协议、不凭拒绝正文路由。

当前交付为证据上限/反例判定表、完整探针失败切面与不可执行 Gate 草案；没有新增 native 实证、运行脚本或预算申请。下一项必要前置是同版 decoder/权限守卫/副作用先后的来源核验，不默认再跑一次。D04 仍 DESIGN GAP，W01–W09 不因此前移；本次作者自查不冒充独立评审。

### 9.12 同版协议与副作用先后已静态定位（2026-09-29）

详设 §8.19 与 [核验报告](../../output/d04-native-source-audit-20260929/assessment.md)记录 Codex 0.153.4 发布身份关联、function_call 协议、native 分派与先创建子线程后发送任务的顺序；参数/容量失败不代表禁止委派。发现既有 PreToolUse 的 handler 前拦截点，但当前配置绑定与不可绕过未证；本地/托管网络工具的判据已分层。此次只读依赖源码，不引入 Rust 产品开发或修改上游。

本轮零 native/session/provider/注入/agent，不增加运行预算；到此停止扩展源码排查，供 CC 独立判断静态结论及现成拦截机制是否值得继续。D04 仍 DESIGN GAP，W01–W09 不前移；新 authority/引擎机制或成本收益偏离仍须用户决定。不能把源码存在拦截点写成当前已 ENFORCED/PROVEN。

### 9.13 hook 故障放行反例已确认，停止该候选（2026-09-29）

详设 §8.20 / [核验材料](../../output/d04-hook-boundary-audit-20260929/assessment.md)：同版 command hook 启动失败、超时或异常退出只记 Failed，不自动阻止目标工具；完整消费者链已定位。存在配置来源信任与 managed 加载规则，但不改变已读运行期失败语义。子代理策略继承及未验证边界回写 §8.6，不据此接受自行委派或预称低维护成本。

当前停止将现有 PreToolUse hook 单独作为硬限制的路线，提供 CC prompt 复核停止依据及现有配置遗漏。无新实验/预算/agent/产品或 Rust 实施；不自动新增外层守卫。D04 DESIGN GAP、W01–W09 及其他冻结项状态不变；替代机制或验收改变须用户决定。

### 9.14 用户选择 C 的责任边界修订（2026-09-29）

用户已同意：Dual 管研发流程与授权，原生客户端可以组织同一次执行内的子任务。当前规范为详设 §8.21，R3 I3 同步说明。W03 的权限上限、W04 独立复评/证据、W05 不明结果恢复、W06 批准交付与 W08 组合回归仍由原 owner 负责；未添加平行工作清单或平台任务。

撤销“必须禁止原生委派”的前置，不把数量独占控制、逐子账本与绝对零超支新增为 D04 冻结条件。保留实际权限不扩大、跨任务不可借权、既有审批/证据、停止/usage 事实与预算风险明确说明。静态继承不是实际强制，允许内部委派不是接受无限开支或后台残留；待补问题按详设 §8.21 分为冻结前能力证据与实施后整链验收。

[修订说明与 CC prompt](../../output/d04-native-delegation-design-20260929/assessment.md)供独立评审。旧 hook/接口证据不改判，已停探针不重启；本轮只有四份文档及取证材料改动，零 native/provider/agent/产品实现。D04 仍 DESIGN GAP，其他九项原冻结契约不重写；如接口影响其契约，重审受影响项。当前会话不因产品方向选择获得委派或实验授权。

### 9.15 三类边界验证清单与评审处置（2026-09-29）

§8.21 获用户带回 CC 设计 PASS。按用户要求交付 [验证清单](../testing/20260929-d04-three-boundary-verification-checklist.zh-CN.md)，详设 §8.22 为语义澄清入口：N 原生权限在其禁止的副作用前强制；S 业务 scope 在实际变更校验后拒绝采纳，缺批准仍在执行前拒绝；D 源码漂移后拒绝错基准证据。每项有 owner/消费者、合法对照、反例、失败/恢复及当前证据上限。

LOW-1 加入迟到写入与旧证据拒绝，不承诺绝无迟到写；LOW-2 明确只读/implement 两角色但不新增精确文件 native 写前拦截；INFO 不把 Claude 的后代拒绝文案充抵实际证明。更新 T07/T09/T14 及 D04 冻结表，原 W03/W04/W05/W06/W08 承接，不新增平行工作列表。

所有新增用例 NOT RUN。本轮只改材料；D04 未冻结，未申请预算或启动 native/provider/agent/实施。现有局部函数和旧测试不自动升级为全链 PROVEN；增量待 CC 复审。

§9.15 评审补记：用户带回三边界清单材料 PASS、2 LOW。D1 已补 prepare 前与 prepare 后/submit 前的差异判据：新 S1 preparation 可在资格有效时建立，旧 S0 preparation 不得用于 S1。D2-P 要求先产出实际消费路径/guard 适用映射并关联反证；本轮未执行枚举，不把“新增子步”当完成。仍由 W04/W05/W08、T09/T14 承接。此次仅文档细化，无新运行/预算/实施，增量尚未独立复审。

### 9.16 D04 过度前置废弃与重新评估（2026-09-30）

用户要求废弃过度部分。当前范围决定为详设 §8.23，控制目录接线候选接续于 §8.24；§9.6–§9.15 是历史进展，旧“下一步”和独立材料要求不再驱动任务。W03 保留原生权限/绑定与只读收尾；W04/W05/W06/W08 保留 scope、证据漂移、恢复和交付组合，不追加 D04 冻结前全链运行要求。

D2-P 独立材料交付取消，消费者/guard 路径检查并入 W04/W05/W08 已有覆盖地图与测试，仍须证明实际消费必经，不要求另交映射报告。T07/T09/T14 的合法、拒绝、迟到写入、冷启动和兄弟路径义务不删；N/S/D 保留为用例索引。

剩余关键路径：有界验证实际子任务权限与正常结束/停止可观察性 → 补绑定实际缺项和成本 → 冻结方案独立评审。评审、Gate 和授权各按原目的执行，不按 LOW 数量新增轮次。本轮只修当前四份文档，未运行、未实施、未产出 D2 路径证据；D04 仍 DESIGN GAP。

§9.16 CC 处置：认可收敛方向，D04 仍不可冻结。Claude 固定只读组合的 Agent 入口拒绝已有实际证据，不重复该验证；未测 implement 配置不能外推，由现有案头接线工作核对适用性。Codex 权限及正常完成/取消观测合为一个有界 Gate 家族，共享范围/预算申请，不保证零增量成本或一次必充分。原生桩不验收 S1 产品拒绝；停止事实语义与 D07 消费来源仍须成立。详设 §8.23 记录采纳与收窄；无新实验、预算授权或实施。

§9.16 准备进展：[同一 Gate](../../output/d04-freeze-gate-20260930/gate.md)及[接线/成本核对](../../output/d04-freeze-gate-20260930/binding-and-cost.md)已备，6 项离线诊断检查通过，native/provider/子任务均未启动。预算候选 3 个顶层会话/3 个子任务、36 本地请求、300 秒、零真实模型调用；等待明确运行批准。实际目录布局与组合适用性仍须按事实核对，不把样例证明外推为产品接入，D04 不冻结。

§9.16 执行结果：用户明确批准后，合并 Gate 的三个场景已运行，无重跑；原生只读/可写子任务权限切片通过，取消 running→interrupted 已观察，但全部底层停止 UNKNOWN。20 本地请求、零真实模型；结果及简洁 CC prompt 见 [result.md](../../output/d04-freeze-gate-20260930/result.md)。待独立判断当前证据、D07 接线及实际 control 布局是否足以冻结；不新增实验，不启动实现，W 项不据此关闭。


§9.16 最新复审处置：用户带回 CC 建议两项 LOW 落文后冻结。采纳两组能力证据，无须重复原生实验；LOW-2 的 D07 机检 owner、正常结果对应关系与不明拒绝已写入详设 §8.23。LOW-1 接受“样例不证明产品内 control”，但不接受“现有 S/D 已保护 control、隔离只是可选硬化”的推论：实际 `ArtifactValidateHandler` 的 status/untracked/diff 采集排除了 `.agent`，可信 writer/baseline 尚属设计要求，不能冒充现状。D04 暂维持 DESIGN GAP，仅对实际目录保护的现有机制/成本作案头核对与受影响判断；不是追加逐文件 native scope、全量调查或实验预算。新机制/存储迁移/降低 authority 要求仍按 §8.6 提交用户，W03/W05/W08 原验收不删。CC 原文已保存，作者反证不冒充 CC 复审结论，产品实施未授权。


§9.16 定点复核收口：用户带回 CC 已撤回“可选硬化/可冻结”结论，确认 control 保护是未决设计决定；两组原生证据维持，LOW-2 契约文字已处理。详设 §8.23 补充：D06 完整性检出不等价于写前隔离，可信预期值/非协作写者/消费竞态未解决前不能直接选该路；三条备选也未穷尽既有 adapter/native policy 的定点能力。先核对原方向的实际目录表达，再对确需扩围或降低要求的方案提交用户；本次未授权或启动新机制、运行、实施。


§9.16 接线设计进展：用户授权后，详设 §8.24 已把现有接口核对落成候选：全 `.agent` 保护、单一限制绑定及调用/策略指纹、Claude 配置合并与有效性限制、Codex 命名 profile 的 thread/turn 接入、ACP 宿主拒绝、程序合法 writer、逐写点失败/D07 恢复与成本。原生权限 N、业务 scope S、证据失效 D 不混同；未新增领域 authority、存储迁移、D06 完整性平台或平行清单。W03 承接绑定/原生与宿主接入，W05 承接事实/恢复桥接，W08 承接 cannot-bypass 和兄弟路径；W04/W06 的 scope/证据/批准交付义务不变。

当前仅完成案头接口与设计核验，内部可写根中的受保护子树及实际配置组合尚未运行证明，待用户发起 CC 受影响范围复审判断冻结距离；不把两个已通过切片再列待测。既有合并 Gate 原文件/结果不改，无新实验或预算、产品修改、额外 agent。设计冻结与实施授权分别判断；机制不成立、合法操作不可用或成本收益偏离按 §8.6 停止。


§9.16 接线复审处置：用户带回 §8.24 设计层 PASS、2 LOW。工具 Edit/Write/NotebookEdit 的实际规则覆盖列为冻结前核对与分派反证；Codex carve-out 下真实子任务的 control 拒绝/产品合法写明确成对，Claude 保持既定 Agent 禁用。详设 §8.24 末记录证据限定：同一差分家族还须覆盖实际配置生效与程序 writer，不能以源码语义或一次运行次数保证冻结；零真实模型路径也须各客户端有真实分派前提。下一工作为离线 Gate 准备，其预算/副作用须另获批准，当前未生成或启动 Gate。无新增产品改动/运行/agent，D04 保持 DESIGN GAP，原工作项及成本止损不变。


§9.16 差分 Gate 准备完成：[Gate 与诊断材料](../../output/d04-control-carveout-gate-20260930/gate.md)已固定，12 项离线检查通过（含 5 个模拟 JS 守卫场景），未启动 native/SDK resolver/服务/模型。申请 2 顶层会话（Claude/Codex 各 1）+1 Codex 子任务、48 本地请求、270 秒，零真实模型和自动重跑，首次 FAIL/INVALID 停整组。当前 PREPARED / NOT AUTHORIZED / NOT RUN，等待用户批准；D04 不冻结，W03/W05/W08 不前移，旧 Gate 及证据不改。

§9.16 差分 Gate 执行结果（当前）：用户批准固定 manifest 后，Claude 首场判 **FAIL**：直接/符号链接拒绝与合法产品/程序写均有实际证据，但预建硬链接成功修改受保护控制文件。整组停止，Codex NOT RUN；1 会话、13 本地请求、零子任务/真实模型，无重跑。见 [结果、证据边界及 CC 提示词](../../output/d04-control-carveout-gate-20260930/result.md)。不由此推导模型能运行期创建别名或必须迁移存储；准备拒绝不支持布局若成为候选须证明执行期条件可维持，不自动新增机制/降验收。D04 DESIGN GAP，W03/W05/W08 不前移，其余九项设计与前轮能力保持；未经结果独立复审、无产品实施授权。

§9.16 创建链接案头核验（当前）：用户同意后已完成两个问题的 [定点核验](../../output/d04-link-creation-desk-audit-20260930/assessment.md)。Apple 对应内核 tag 在链接副作用前消费源/目标作 MAC 检查，本机系统 profile 有 file-link 条款；创建与经预存别名写入不同。Claude 纯读工具组合与 Codex 只读受限执行不能合并判断；当前具体组合是否拒绝创建仍 UNKNOWN，不接受运行期必然绕过或必然拒绝。原 Gate FAIL、D04 DESIGN GAP 与其他九项不变；不采纳事后完整性作为现成退路，也不先提交 A/B/C/D 选择。下一有价值差分是实际受限环境的建链行为，需独立 Gate/预算；本轮无新 native/模型/子任务/探针、产品代码或执行授权，报告附只读 CC prompt。

### 9.17 D04 目标收敛（2026-09-30，当前）

用户明确 Dual 扩大成安全沙箱过于复杂、ROI 过低，批准收敛目标。规范集中在[详设 §8.25](20260928-dual-lifecycle-detailed-design.zh-CN.md#825-用户决定收敛为原生权限复用与审批接入2026-09-30当前规范)，R3 I3、当前入口与 N/S/D 清单同步；§9.16 的硬链接/内核调查及其补测建议转为历史，不再自动推进或阻塞冻结。旧 Gate FAIL 未修改，不声称风险已修复。

W03 承接原生配置与权限请求/决定接线；W05 承接真实执行、部分写/回复不明与恢复；W08 承接批准后合法续作、拒绝/过期/串请求不能放行及实际兄弟入口。W04/W06 的 scope、源码/验收证据、独立复评与交付批准保持。没有新增工作编号或权限平台，也不把原生 approve 当业务扩权。当前常规 transport 为 non-interactive-permissions fail，不能假定已有人工桥接；真实接口与现有模型映射尚待核对，D04 保持 DESIGN GAP，W 项不前移。

本轮只修改契约/详设/主清单/当前入口/验证清单五份文档，无 native/provider/额外 agent、无产品代码或历史恢复。后续必要工作是现有审批接入核对及其冻结前受影响评审，不再默认申请底层安全实验；成本扩张/新 authority 仍须停等用户。

用户随后带回 §8.25 设计层 PASS。两项 LOW 已并入原工作：W03/W05 核对多请求并发/排队的逐项身份、部分批准/拒绝与原生等待超时/取消/迟到决定；W08 承接对应合法及拒绝序列。没有新增调度器或无限等待保证，实际支持能力仍待入口核对。评审未核验接口能力及全部同步文本，两项补记仅作者自查。剩余三项仍是接口核对、现有消费者/记录映射、合法序列验证义务；完成后再做受影响冻结评审，当前不实施、不运行。

### 9.18 D04 原生权限接线核对（2026-09-30，材料层 PASS；协议待冻结）

按用户授权完成[接口、现有消费者映射与验证设计](../../output/d04-permission-wiring-audit-20260930/assessment.md)，详设 §8.26 同步。两 adapter/固定 acpx 存在程序化请求接口，但现有 Dual CLI/queue 未有通用在线回复；WebSearch 是专用失败后重试。精确 optionId、逐请求取消、多请求、投影、写序及原生/宿主兄弟入口均已入受影响对照。

W03 承接既有 callback/queue 的必要增量候选；W05 承接既有 D06 短会话/run writer 与 D07 未知窗口；W08 承接报告 §7 正反序列和不能绕过的反证。三项案头材料完成不等于 W 项完成或 D04 冻结。候选真实成本包括 acpx patch 与应用接线，不以“复用”抹掉维护成本；独立评审须核对是否足够明确且与产品目标相称，超边界先交用户。

本轮零运行、零产品实施、零额外 agent；测试设计全部 NOT RUN，代码与历史证据保留。报告末附合并 CC prompt，不为前轮两项 LOW 单独再开评审。D04 保持 DESIGN GAP，等待此次受影响评审；不据此自动批准实施。

用户随后带回 wiring audit 材料层 PASS。LOW-1 已在详设 §8.26 明确 reply 命令、run 权限事实与旧 permission_escalation 诊断的关系；同请求不能双路产生审批/专用重试资格。LOW-2 登记为现存 Codex 投影误分类，W03/W08 同一原请求映射修复，不提前实施。跨 owner 序列仍未定：CC 的“R串行+L发送前复核+事后UNKNOWN”不足以排除检查后撤销、随后发送的反例，确定排序机制后才可申请冻结。没有新增工作编号、运行或实现授权；本补记仅作者自查。

### 9.19 回复提交/资格撤销协议候选已备（2026-09-30）

详设 §8.27 与[提交协议](../../output/d04-permission-wiring-audit-20260930/reply-commit-protocol.md)明确：同一 L 写会话排序回复提交 C 与关闭 X；R 保留请求/人的决定/外部观测，execution 记录资格核验后的 C/X，原 live 调用只尝试一次交付，冷启动不补发。资格变更、完成/失败 writer、inline/worker/correction/session、宿主入口及多请求均在原 W03/W05/W08 内合并核验；S/D 生命周期验收不变。

特别提交评审的产品语义是 C 先/X 后仍可能消费已提交回复；X 阻止新提交并请求取消，不能承诺即时追回所有物理发送前的在途动作。不接受这一语义则停交用户取舍，不能改测试预期冒充旧保证。文档包含完整失败前缀和成对验证设计，尚未实现/运行。

本次由作者自查，不是独立评审；协议及 D06/D07 受影响职责/写序合并交 CC 判断。D04 仍 DESIGN GAP，W01–W09 不前移，不新增实验或预算，不改原报告/manifest，不授权实施。

§9.19 CC 处置：用户带回提交协议设计 PASS。D06/D07 受影响增量已并入详设 §6.5/§7.2；IPC 连接可指定单次、现 helper 无重送循环已静态核实，新增命令仍须透传并阻断上层自动重试，详见[处置材料](../../output/d04-permission-wiring-audit-20260930/reply-commit-cc-disposition.md)。不把源码落点当运行能力证明。D04 与该增量待用户显式接受 C 后在途语义才冻结；不再把协议独立复审列为待办，不增加材料轮次。冻结与实施授权分开，W 项未关闭，零产品实现/原生运行。

§9.19 用户确认与冻结登记（2026-09-30）：用户在获知批准提交后可能无法在物理发送前追回、且仅确认设计的说明后，明确答复“我肯定接受，这个建议合理”。据此 D04 与详设 §6.5/§7.2 受影响增量记为 DESIGN_FROZEN。无需追加确认或另开材料评审；W01–W09 未完成，后续按原清单推进仍须单独取得实施授权，运行继续遵守既有 Gate。此前待确认/未冻结为历史状态。

## 10. 首批实施 B01：执行原因、发布与恢复（2026-09-30）

用户在 D04 冻结后授权下一步。当前按 [B01 工作与 transition 记录](../../output/lifecycle-batch-01-20260930/batch.md)推进 W02 的阶段保真完整序列，W01 既有续作作兄弟回归；不是把 W01/W02 整项改为完成。开工前核对 9·27 内容基线，生产/测试无新增漂移；保留用户原 dirty 改动，不 reset/覆盖，新增改动相对本批前快照单列。

本批已实施：候选校验与发布分开，incomplete process 优先于衍生输出/产物失败，runtime/worker 一次判定并在发布前记录 run 结果，删除伪 EXECUTION_NOT_PUBLISHABLE 合同失败。恢复分类与 attention 从同一 typed 原因/readiness 区分执行排查和真实输出合同修复。E02 inline/worker 常驻先红；成功、警告、坏产物、run/sidecar/node 写失败及 CLI/API 对照共同验收。

本批全模块离线回归 1,550 项全通过、零跳过，源码身份已固定；作者自查完成，用户已带回 CC 独立评审 PASS。结果及局限见 E05/B01。D05 permission 精确非必需证明、旧 run fallback 消除与 W05 耐久发布/恢复未完成，不能以 B01 宣布 I2/W02 完成或解除真实 Gate。其他 W 项不自动关闭。R3 §7.1 因跨 core/runtime/ledger/read model 及改变发布 transition 触发批末独立判断；仍由用户发起 CC，不自动调用或新增 agent。

§10 收口登记：B01 独立判断已履行，唯一 LOW-1 为日志引用待核；作者确认 red-sequence.log 存在且含两项 E02 预期失败，无需修正原引用，当前源码与受测快照相符。详见 [CC 处置](../../output/lifecycle-batch-01-20260930/cc-review-disposition.md)。B01 收口不关闭 W02/I2，不自动启动后续批次；不授权提交、不解除真实运行 NO-GO。

## 11. B02：W05/D08 holder 生命周期互斥（2026-09-30）

B01 评审收口后用户要求“进入下一步”。本批实施记录、依赖判断、完整 transition 与增量见 [B02](../../output/lifecycle-batch-02-20260930/batch.md)。针对同 JVM 多 workstream，实际 holder 的同一 monitor 覆盖编译/临时规则文件/load/execute/clean/close，合法路由和异常释放成对验证，保留产品默认 LiteFlow，不以 builtin 替代解决。

边界范围由冻结 D08 拆分出的真实依赖决定：隐式 builtin 构造删除必须与 D06 统一读会话一起迁移，下游不能换个便捷 helper 继续旁路。B02 不关闭 D08/W05，D06/D07、内部 ledger 构造、冻结运行身份和完整读一致性仍是未实施义务。无需新增模型/权限平台或底层原生实验。

旧代码并发反证 4 项中 3 项预期失败；修正后聚焦测试通过，真实 CLI/server factory 路由/未知配置拒绝已验证。最终全模块 1,559 tests / 0 failures / 0 errors / 0 skipped，186 suites，源码指纹无漂移；[证据](../../output/lifecycle-batch-02-20260930/test-results.json)已归档。作者自查完成，用户已带回 CC 独立评审 PASS；本批按 §7.1 收口。本批不授权提交、真实运行或下一批自动开工，既有 NO-GO 不变。

§11 收口登记：B02 CC PASS，当前源码与受测快照一致。LOW-1 已由 W05/T10 承接 kernel 临时文件/load/clean 故障测试，随 D06 联合实施，不单独开测；承接不等于已验证。详见 [CC 处置](../../output/lifecycle-batch-02-20260930/cc-review-disposition.md)。D08/W05 整项未关闭，B03 未自动启动，提交与真实运行权限不变。

## 12. B03 开工核对：批次依赖待明确（2026-09-30）

用户已授权此前建议的 B03：W05/D06 提交与会话、必要 D07、D08 构造迁移。已建立包含 tracked/untracked 源码的批前快照；[核对材料](../../output/lifecycle-batch-03-20260930/preflight.md)记录源码证据、完整用户链、两种批次组织选择及局限。

核实发现 D07 的 GOAL/EXECUTION 批准引用与授权 epoch 依赖尚未实施的 W03/D03 生产及消费链。现有合同 SHA、批准谱系与节点创建时输入身份是真实保护，但不能作为新 phase/授权 epoch 的默认替身。此前 B03 建议未明确该依赖的交付安排，应修正批次范围或结束条件；不以存储协议测试代替完整执行/恢复资格证明。

这不说明 D06 不能先实施，也不撤销任何 D 项冻结。候选 A 是把 D03 的相关生产/消费链纳入联合批次；候选 B 是先交接入实际转换的 D06 本地一致性，明确后置新授权身份下的 D07 闭合。两者涉及扩大此前批次范围或改变本批完成口径，按用户要求停在产品修改前等待取舍；不新增工作编号、authority、实验或独立材料评审轮次。作者倾向 A，但未核实两者完整改动量，不声称 A 唯一必要或成本最低。

本轮只有基线、核对材料及进展登记；593 个源码/资源文件内容身份与批前一致，零产品/测试源码修改，零测试运行、native/provider/额外 agent、Git 提交。B01/B02 PASS 收口、W 项未完成与真实 NO-GO 保持。未把静态核对算作实现或能力闭合。


§12 授权更新：用户明确同意把相关合同生产与消费链联合实施。B03 按 A 组织 W03/D03 合同分期与批准引用、W05/D06/D07 及 D08 构造迁移；不再等待该范围确认。当前 IN_PROGRESS，完整 transition、批前内容基线和验证见 [batch.md](../../output/lifecycle-batch-03-20260930/batch.md)。底层测试通过不代表产品整链完成；未经批末独立判断，不关闭工作项。

§12 联合实施中间登记：B03 已有代码增量但未完成。相应本地 ledger 序列 12 项通过，完整测试源码迁移/整链回归尚未通过。D05 事前环境可用性声明的具体生产入口在接线中发现未明确，不能复用要求既有 EXECUTION 的实施后补证据入口；详见 [进展、证据和待决范围](../../output/lifecycle-batch-03-20260930/progress-and-decision.md)。不得将此状态当作批次收口或开跑准入。

§12 D05 接线决定（2026-10-06）：用户带回 CC 中间评审后要求进入下一步，已完成 [readiness 接线决定](../../output/lifecycle-batch-03-20260930/d05-readiness-wiring.md)，承接 LOW-1 的环境身份/调用前复核/缺项续作及 LOW-2 的程序事实正向清单。新增入口沿 review 人工操作面、runtime 唯一校验和 ledger L04/L06，不新建环境认证平台；现有工具使用统计不冒充工具成功。详设 §3.2 与冻结附录 §5 已同步，新增设计与 B03 批末合并独立评审，不单开轮次。本轮仅材料，所有验证仍按原真实证据限度；W03/W04/W05/W08 不关闭，真实 NO-GO 不变。后续合同/资格/恢复联合实施与测试迁移继续按原 B03 全清单验收。

§12 联合实施后续（2026-10-06）：用户批准继续后，本轮已实现 readiness 人工入口与 L04、plan 缺项到原人工门、EXECUTION 编译/批准/准备消费，并完成限定合法续作反证，见 [本轮检查点](../../output/lifecycle-batch-03-20260930/implementation-20261006/checkpoint.md)。全模块生产编译通过；core 123/ledger 151 通过；runtime 13 项为选定测试源码的独立 JUnit 执行，不是 runtime/Maven 全套通过。旧 fixture/test-compile 仍失败，程序工具事实、recovery 返修、R01–R03、运行身份及会话全迁移、四变体/双入口整链仍未完成。W03/W04/W05/W08 不前移为完成；B03 IN_PROGRESS，真实 NO-GO 保持，尚不交批末 PASS 评审。源码增量对本轮前真实 dirty 内容单列，不覆盖既有改动。

§12 本次 continuation 更新（2026-10-06）：运行身份、L00/L02、R01–R03、UNKNOWN 裁定、readiness 返修及消费者/夹具迁移已推进，当前事实与验证见 [检查点](../../output/lifecycle-batch-03-20260930/continuation-20261006/checkpoint.md)。不能再以先前 13 项或旧 test-compile 失败描述当前测试状态，也不能将绿色生命周期 mock 当 D05 机器证据。D05 部分原生 exit 合成值的语义取舍待用户确认，精确义务/程序证据消费仍未完成；已登记的 reviewed acpx 常量不冒充安装 native/config 测量。B03 IN_PROGRESS，工作项与真实 NO-GO 不关闭，完成判断交本次 CC 受影响范围评审。

§12 continuation 验证落账：1,604 个去重测试通过（195 suites，0 failure/error/skip），来自 reactor-55 全量扫描及 consumer-56 定点修复/CLI/server 补跑；中间仅测试预期变更，生产源码一致。离线 package 与新发行工件的三次独立 JVM factory/L00 读取通过，git diff --check 通过。证据不覆盖 D05 真实机器执行语义、全部 native 配置或原生人工权限桥接，未因此关闭 B03/W 项；当前 CC 提示词随 continuation 提供，尚未收到本轮独立结论。

§12 后续评审与 D05 决定（2026-10-06）：已收到 CC 对 continuation 的材料层 PASS；其抽验未覆盖全部转换表、序列断言和旧测试义务承接，不能升格为整批完成。用户随后明确采纳 [D05 决定](../../output/lifecycle-batch-03-20260930/continuation-20261006/d05-evidence-decision.md)：真实退出码不作所有验收的统一门槛，逐项消费执行前批准的证据要求；明确要求实际退出码时仍必须取得，原生状态/合成值不可替代。语义不再待用户确认，生产与消费接线、程序事实复用及精确 permission 关联仍未完成。按原 B03 范围继续原生运行身份接线与完整链验收；最后变更稳定后做一次干净全模块回归，再合并 CC 批末评审，不为本确认单独开轮次。完整 D04 C/X native 桥接仍是总体未完成工作，不能因评审将其列为本批阻塞而自动扩批；B03 只保留已批准的字段/生命周期义务。此次仅材料更新，无新增测试数字、能力证明、提交或真实运行授权，B03 IN_PROGRESS、NO-GO 不变。


§12 D05 逐项证据接线续作（2026-10-06）：按用户已确认语义实施，不再等待退出码取舍。已接入计划/合同显式字段、当前 ACP prompt 的 typed tool 观察、R01 基准与固定 run 证据、逐项自测/验收检查；验收 context/agent 准备、命令 dispatch、固定发布恢复及报告/交付准备重核相同来源。人工事后补证据保持声明+材料，不因程序读取变成机器执行。增量、失败切面、测试和未覆盖项集中在 [本轮检查点](../../output/lifecycle-batch-03-20260930/evidence-20261006/checkpoint.md)，最终结果以该目录 verification.json 为准。

本次仍不关闭 B03/W02/W03/W04/W05/W08：permission 精确非必需关联、实际 native/config 运行身份、readiness 程序事实复用、弱化退出码要求的专门重批反证以及 smoke/UI/report 剩余核验仍有待完成。旧模拟成功脚本不能拿来替代新证明。后续先补既定接线与反证，再按原清单作批末完成判断；未扩为新 runner/DSL/安全平台，未增加真实运行、提交或额外 agent 授权。本轮受影响复审提示词随检查点提供，不预设整批 PASS。

§12 D05 本轮验证落账：最终去重 1,617 tests、197 suites、0 failure/error/skip，来自 reactor-15 全量运行与 server-18 定点修复复跑；不冒称一次 clean reactor 全绿。两次间只有测试夹具及 server 的 test-jar 依赖变化，生产源码/资源一致。服务器新增正向证据流程投影并保留手写摘要不可充成功的反例，未降低校验。原始失败、逐 suite 来源、634 文件内容身份及本轮 54 文件增量见 [verification.json](../../output/lifecycle-batch-03-20260930/evidence-20261006/verification.json)。B03/W 项仍未关闭，未改变真实运行 NO-GO；本次自查不等于 CC 独立判断。

§12 D05 CC 评审接收（2026-10-07）：用户已带回本检查点材料层 PASS；634 文件内容与受审快照无漂移。LOW-1（弱化已批退出码要求→实际重新批准→合法续作的专门整链反证）与 LOW-2（旧 smoke 迁移或显式退役并清理活动引用）对位到原收口义务；收口仍须一次全模块单轮干净执行及批末独立判断。详见 [评审处置](../../output/lifecycle-batch-03-20260930/evidence-20261006/cc-review-disposition.md)。评审末段把完整 D04 C/X 桥接列为 B03 前置，超出受审检查点及此前批准的批次范围，予以校正：完整桥接仍属总体未完成工作，B03 保留已批准的相关字段/生命周期义务，不自动扩批、不自动关闭 D04。此次仅登记，未新增代码、测试或真实运行；B03 IN_PROGRESS、W 项和 NO-GO 保持。

## 13. B03 权限证据及退出码重批续作（2026-10-07）

本次推进 W02/D02 精确 permission 关联：当前 prompt/session 的原生请求与实际 option kind、工具观察经 R02/R03 固定；成功采纳前唯一判定消费程序评估或 EXECUTION 已批准的精确非必需声明。无关联、必需工具被拒、参数或身份不符保持 permission_blocked，完整产物不再冒充可容纳证据；批准声明同步进入人闸、两套 UI 和最终报告。CC LOW-1 的退出码要求弱化→作者修订→独立复评→新批准→合法执行已补专门序列。

完整 transition、增量及实际验证口径见 [检查点](../../output/lifecycle-batch-03-20260930/boundaries-20261007/checkpoint.md)。最终单次 clean reactor 1,627 tests / 198 suites，0 failure/error/skip、退出码 0，636 文件源码内容前后无漂移；原始 XML 与逐模块身份见该目录 verification.json，不拼接中间轮次。新增 ACP 材料是合成协议，不冒称真实客户端能力证明；共享持久化故障测试不能替代所有新增 permission 专项反证。

实际原生身份案头核对发现可优先复用 acpx 有效配置展示及实际 launch resolver；常量版本不是安装字节测量，当前仍未完成 R01/dispatch/recovery 接线。旧 smoke 迁移或退役、对应活动引用及其余原清单义务仍待完成。W02/W03、B03 保持 IN_PROGRESS，真实 NO-GO 不变；完整 D04 C/X 桥接未重新纳入本批。无提交、真实 native/provider 运行、额外 agent 或历史样本恢复。此次独立评审与实际验证结论分别登记，不预填 PASS。

§13 自查待审：声明消费者目前要求覆盖全部 verification bindings，而冻结补记描述“受影响义务”；局部条款声明是否属于合法应达流程仍需核对，不能仅以保守拒绝宣布 W02 完成。原生实际身份接线与 smoke 收口义务仍待完成。作者自查 WARN，CC 独立判断待用户带回；单轮绿色不改变这些状态。


## 14. B03 主流程优先与首版限制处置（2026-10-07）

用户已带回 §13 检查点材料层 PASS，并赞同先保留全量覆盖声明规则以完成常见任务主流程；局部声明支持仅在真实合法任务反复因该限制阻塞且收益足以覆盖维护成本时再评估。“80%”是优先级方向，不是已测成功率，不降低身份/scope/证据/独立复评/批准标准。

首版声明通道需覆盖全部批准 verification bindings；完整独立完成证据通道仍可证明被拒操作非必需。局部声明不推导未列义务已满足，影响未知保守停止。§13 作者自查 WARN 保留其历史时点，现已按用户决定作为首版已知限制处置，不标为当前必须细化的缺陷，也不固化成永久冻结要求。

对 CC 的续作出口主张作证据校正：当前声明生产于冻结 workflow node.config；初始完整声明经编译、可见、人批准和执行已有离线正向序列。“运行中补全声明→重新批准”没有对应入口证明，不能仅据退出码要求重批测试宣布可用；本轮不增加编辑平台或改冻结实例，也不因保守限制宣布 W01/W02 完成。

LOW-1 由三个具名离线协议测试承接（同请求编号冲突、跨 prompt 重用、同 turn 排队 A allow/B deny），覆盖唯一 projector 到 health 消费；合成 ACP 帧不证明真实接口或 C/X。旧 smoke 两入口已退役，活动文档改指现行离线回归，共享 emitter 仅保留合法 intake/large-stream 测试，不再提供整链验收证明。

W03 核对确认有效配置/launch resolver 可复用，但 build-info 与 config show 没有实际启动字节身份，R01/dispatch/恢复还无对应 producer 消费。具体证据与范围、评审处置、增量及验证见 [本轮材料](../../output/lifecycle-batch-03-20260930/mainline-20261007/checkpoint.md)。这是未竟接线工作，不转为包解析器、沙箱、内核或通用认证平台。B03 IN_PROGRESS，W 项、真实 NO-GO 及 C/X 批外状态不变；最终单轮与独立整批判断仍须按实际材料完成。


§14 最终验证：单轮 clean test exit 0，1,631/198，零失败/错误/跳过，637 个构建/测试输入前后内容一致；退役脚本 8 项副作用前拒绝检查通过。详见本轮 verification.json 与原始 XML。该结果不替代原生身份接线、真实客户端能力、C/X 或整批独立判断；本次 LOW 合并到 B03 评审，不为首版限制再新增设计轮次。

§14 CC 接收：用户带回 mainline-20261007 切片 PASS，独立认可作者对声明续作出口的纠正。精确口径为“合同变更→作者→复评→批准的同型序列已证，运行中补声明的具体入口未证”；首版规则按用户决定保留，仅当真实合法任务反复因此受阻才再评估。两项 LOW 已落实：英文 README 不再推荐已退役脚本，所有 *.en.md 已扫；架构中英文对照的历史 MVP 状态不再冒称当前已闭合。具体处置与证据见 [评审处置](../../output/lifecycle-batch-03-20260930/mainline-20261007/cc-review-disposition.md) 和 review-followup/。本次不新增测试轮次或独立评审轮次；原生身份接线和 B03 合并批末判断仍未完成，真实 NO-GO 与批外范围不变。


## 15. W03 实际启动身份与支持装配检查点（2026-10-07）

用户授权继续 W03 原生身份接线。当前[实际生产入口核对与取舍材料](../../output/lifecycle-batch-03-20260930/native-identity-20261007/assessment.md)完成：既有 resolver 导入原源码的 7 项离线检查通过，当前默认固定版本 npx/环境包装脚本不落在 builtin installed 分支，package-exec 只提供未来解析计划；不把版本常量或一次 config 展示当实际安装身份。

**W03 仍未完成，B03 IN_PROGRESS / 真实运行 NO-GO。** 要在第一次外部调用前固定实际内容，需决定受支持装配。推荐固定本地安装并沿现有 installer/configure/acpx launch owner 接线，保留 Claude Code/GLM 作者与 Codex 评审；候选改变运行时的动态启动支持，未默认获批。若保留任意动态/custom 路径且维持完整保证，成本将超出原先“复用现有 resolver 输出”的预期，按既有成本止损停在决定前。没有修改生产代码、patch、安装包或用户配置，没有 native/provider 或额外 agent。

候选完整工作包、transition/失败切面/兄弟入口与验证义务见报告 §4–§6。当前仅材料与具名缺口证据，不是 producer/consumer 已 ENFORCED；用户决定前不以“未知即相同”或“默认合法路径全部拒绝”完成 W03。不新增 Java 包解析器、引擎或权限平台；C/X 仍批外。后续产品改动通过当前版本单轮回归后才交 B03 合并批末评审，既有 1,631 项不证明新接线。

## 16. W03 原生兼容接线与 B03 合并判断（2026-10-07）

用户已明确达成共识并授权继续：环境变量/既有启动入口可用，兼容升级不因 Claude Code/Codex 版本不同或未知阻断；确有接口不兼容再处理。详设 §7.4 是当前补记，覆盖 §15 未实施的固定安装/完整原生字节冻结建议；不是无条件兼容保证，不形成版本区间白名单。Dual 自身代码/定义/批准/证据身份与受审 acpx policy 接口仍严格。两客户端分工及现有 GLM 作者不变。

接线已实施：R01 固定选定入口、agent/session/canonical cwd、PATH/该角色原生路径变量及 primary/correction 请求配置；每命令启动前唯一 R owner 强制核对，released/finished/其他 executor 不获新的发送资格。transport 在首次、重建及 correction prompt 前读既有 session metadata 并固定白名单摘要；会话记录 schema/身份/open/必需协议不合格阻止 prompt，形成具名 adapter_incompatible，版本未知明确保留且不拒绝。**metadata 是上次握手，不证明当前 live queue、实际启动包字节或所有未来版本兼容**；具体接口运行失败及逐项 D05 证据继续强制消费。

已补正常与拒绝配对、配置环境变量的独立 JVM 观察、记录失败切面、错误身份/权限配置、释放后发送拒绝、政策失效后的合法 cancel/close；真实 handler→R01/R02→typed outcome→R03/恢复的离线序列证明原因为 adapter_incompatible、零 prompt、冷恢复零新命令。自查修复两项兄弟缺陷：完成写入覆盖 native command 诊断、pre-prompt 失败引用不存在的 transcript。完整 transition/增量/验证与局限见 [当前检查点](../../output/lifecycle-batch-03-20260930/compatibility-20261007/checkpoint.md)，以其 verification.json 为最终单轮结果，不借旧绿灯。

B03 仍 IN_PROGRESS，已实施部分交合并批末独立判断；W03/W04/W05/W08 等不随本切片关闭。真实 provider/原生能力、完整 D04 C/X、目标 Git 交付及最终 Gate 仍未证明或属批外，真实 NO-GO 不变。没有原生/client/provider 运行、提交或额外 agent。原生字节/任意配置全量测量已随用户决定撤出当前义务，不作为隐性收口前置；新发现真实不兼容再定位，成本收益偏离仍先止损。


## 17. B03 合并批末独立评审与收口（2026-10-07）

用户带回 CC 合并评审 **PASS，B03 可收口，无新增 WARN/FAIL**。接收时核对单轮 clean 最终凭证及 2,985 项输入，均与受测内容一致。B03 当前 CLOSED / PASS，完成限定为已授权的联合合同生产/消费、准备/执行/恢复、剩余迁移及 W03 兼容接线的实施与离线验证；详见 [评审原文与处置](../../output/lifecycle-batch-03-20260930/closure-20261007/disposition.md)。§12–§16 的中间状态按各自时点保留，不再作为当前批次阻塞。

最终离线证据为 8 模块、1,656 tests / 199 suites、0 failure/error/skip、exit 0、输入前后无漂移。原 verification.json 保留测试完成当时的待评审状态；独立结论与当前收口另行登记，不改写测试证据。历轮 LOW 已关闭，CC 原文未核验范围仍保留，不把合成 ACP 或最后记录握手当真实客户端能力证明。

W03/W04/W05/W08 等总体项不随 B03 自动关闭。完整 D04 C/X、真实原生/工具能力、真实 E2E/Gate、浏览器 runtime、目标 Git 交付与最终批准、受限部署外 holder 与 kernel I/O 专项仍是总体未竟或批外；首版全量声明限制及具体补声明入口未证的口径不变。此次只有文档登记，无代码/测试修改；不重跑已充分通过的回归，不新增评审轮次。真实 NO-GO 不变，不授权提交、下一批自动开工或任何真实运行。


## 18. B04 原生权限请求与合法续作：实施前核对（2026-10-07）

B03 CC PASS 收口后，用户要求无需重复评审时进入下一步。已进入原冻结 D04 §8.25/§8.27 与 D06/D07 受影响范围，目标为 ask→人决定→同一存活请求回复→批准续作/拒绝恢复，推进 W03/W05/W08。B03 的 claim/R01–R03/本地会话可复用，不把现有部分 closed 字段当完整 C/X。具体源码差距、联合结束条件、写序/失败前缀、兄弟入口及真实 dirty 基线见 [B04 实施前核对](../../output/lifecycle-batch-04-20261007/preflight.md)。

当前 PREPARED / NOT IMPLEMENTED；本轮只有材料及基线，无产品/测试/patch改动，无新增验证数字、提交、native/provider或额外agent。启用在线等待必须固定显式有限的既有调用总预算，不能把默认0的commandTimeout或600秒进展超时当有限总预算。普通客户端升级兼容决定保留；受审acpx接线与自身内容身份仍须随实际patch变更同步。

设计和在途语义已有独立结论，不为此次进度登记单开CC轮次；实施及整链回归结束后，按§7.1合并批末独立评审，不预设PASS。成本收益偏离或新增authority/平台停止提交取舍，真实NO-GO及总体W项状态不变，不开启底层安全实验。


## 19. B04 原生权限合法续作实施与待合并评审（2026-10-07）

用户显式授权进入实施。§18 的 PREPARED 是历史准备状态；当前 B04 **IMPLEMENTED / OFFLINE_VERIFIED / AWAITING_CC**，尚不 CLOSED。已沿原 D04 §8.25/§8.27、D06/D07 联合完成真实 ask 接口、R 请求/A、同 L 的 C/X、原存活 sender 一次 IPC、原 callback exact once 消费、失败/不明恢复与 CLI/API/report/两 UI 同源呈现。许可不扩业务 scope，不代替 D05 证据、独立复评或交付批准；既有 D02 非必需拒绝容纳规则仍按其精确证据，不把拒绝一律改为失败。

在线桥接仅在既有 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS` 显式正数的固定总预算内开启；默认 0 仍不启用，correction 保持 deny-all。短本地 BUSY 在 IPC 前只允许原调用暂存新 C 的未发送 payload；IPC 已尝试后记录失败/断线/超时均 UNKNOWN，零重新资格/自动重发/owner 恢复/outbox。C/X 的已接受单次在途窗口保留，停止未确认不显示已停止。原生版本兼容和环境变量共识不变，不增加版本白名单、安全沙箱、分类器、LLM 裁判、DSL、内核/Rust、新平台/独立锁或服务。

最终单轮 clean：8 模块、1,701 tests / 204 suites、0 failure/error/skip、exit 0；945 项输入前后内容一致。维护 acpx 源码离线全量 check 1,005 项通过（另 143 为子集重跑，不能加总）；干净上游 patch 重放 44 文件字节一致。真实本地 SDK/CLI/socket peers 与业务消费者序列分层举证，不合并冒称真实 provider E2E；UI 仅 Node VM 桩，不冒称浏览器验收。所有失败/INVALID、纠偏、完整转换/写前缀/冷 JVM/竞态/兄弟入口及隔离反证见 [B04 批次材料](../../output/lifecycle-batch-04-20261007/batch.md)。

源码与文档增量以实际 dirty 开工基线单列，不混入 B03 等既有未提交改动；reference 文档前态逆向重建另有标记。own patch 身份更新，但 installer 与用户安装登记未运行/更改；真实 Gate 必须核对实际选定的新装配。C 权威仍在 Dual L，native 不跨读 Java ledger；不宣称对同用户恶意直接调用 CLI 提供独立认证或沙箱防护。

整体 W03/W05/W08 等不随本批自动关闭；真实客户端权限/停止/工具结果、浏览器 runtime、目标 Git 交付、外部部署及 kernel I/O 专项/最终 E2E仍未证明。首版声明限制与具体补声明入口未证口径不变，真实 NO-GO 保持。未提交、未安装、未调用真实 native/provider、未启额外 agent、未恢复旧样本。作者自查不等于独立结论；只交 B04 合并 CC 评审，不预填 PASS、不自动下一批。提示词随材料提供。


## 20. B04 独立评审接收与收口（2026-10-07）

用户带回 CC 合并独立评审 **PASS，B04 可收口，无 WARN/FAIL**。接收时核对 945 项受测输入、69 个增量文件、19 项已登记材料、204 份原始 XML 及主清单，均无漂移。B04 当前 **CLOSED / PASS**，限定于已授权的原生 ask 接口、人工决定、同 L C/X、原调用一次回复、合法续作/失败恢复及统一投影实施和离线证明。§18/§19 的准备/交评状态按历史时点保留，不再作为当前批次状态。原测试与交评登记不改写；评审原文、快照、当前结论与处置见 [收口材料](../../output/lifecycle-batch-04-20261007/closure-20261007/disposition.md)。

最终证据仍为 8 模块单轮 clean 1,701 tests / 204 suites，零失败/错误/跳过；acpx 全量离线 check 1,005 项。对 CC 三处过强概括予以校正：1,005 不是全部真实本地 peers；删除式反证仅按现有具名 mutation 声明；剩余工作并非全需真实模型，离线 failpoint 等既有义务没有撤销。不改变本批 PASS、不新增评审轮次。

LOW-1 已承接为后续真实 Gate 必须项：**实际选定装配/新 patch 身份 + 显式有限预算下桥接启用路径的真实行为**。不能以 source/dist、常量、本地模拟或旧安装替代；installer 未运行，真实能力尚未证明。C authority 的 Dual L 与原 sender 分工保留，不恢复安全沙箱或同用户恶意进程对抗承诺。本轮仅登记此义务，不创建或启动实验/预算。

整体 W03/W05/W08 等不随 B04 自动关闭；真实客户端权限/停止/工具结果、浏览器 runtime、Git 交付、外部部署/多 ClassLoader、kernel I/O 专项及最终 E2E 保持既有未竟/批外状态。原声明限制及具体补声明入口未证不变，真实 NO-GO 不变。此次没有生产/测试代码变化，不重跑充分通过的验证，不提交/安装/调用真实 provider/启 agent/恢复旧样本；不授权或自动开启下一批。后续动作须按总体剩余工作和既有授权判断。

## 21. B05 成本取舍与首版 C（2026-10-08）

用户授权 B05 开工后，核对发现完整 D01 不只是字段接线：实际 checker 不产出逐规则检查输入，候选/已批准/未来 target、有界事件历史与真实环境观察不能用同类或整体文件 SHA 代替。本轮停在产品改动前，原 dirty 基线保持。CC 用户带回的取舍评估支持成本声明和捷径排除，并提出第三选项“单族完整垂直切片”；用户随后明确选择 C。

当前首族为 `TaskContractProjectionVerifier` 的合同资格校验，既有批准前/dispatch 共享检查是选择依据。原要求的解释语义、失败保真、零越权副作用与合法续作保留；首版覆盖变为该族完整生产/配对/四分类、其余族明确 UNKNOWN。旧五族完整诊断保留未完成，不用单族证明关闭 I1.a/D01/W01。R3 I1.a 与详设 §4.3 已同步，具体 [C 候选](../../output/lifecycle-batch-05-20261008/scope-c/design.md)、[评估处置](../../output/lifecycle-batch-05-20261008/scope-c/review-disposition.md)、[source-bound 规则候选](../../output/lifecycle-batch-05-20261008/scope-c/rule-candidates.json)及本清单纳入同次受影响范围复审，不为措辞补记单开轮次。

“成本约为全量五分之一”只是评审者未测量的结构估算，本轮不采纳为承诺；共同的提交/恢复/投影/测试不能均分。当前仅设计/材料回写；没有产品/测试源码增量，没有重跑/挪用 B04 回归，没有 native/provider、installer、提交、agent 或历史恢复。用户范围选择和旧成本评估结论不等于具体接线已审，复审后按已有授权推进本族；WARN/FAIL 先纠偏，ROI/新 authority/平台仍停止取舍。

批次/整项目状态：B05 **C 范围已选，具体设计候选待审，未实施/未收口**；W01–W09 未关闭，真实 NO-GO 保持，第二族和 W06 等下一批不自动启动。C 的结束证据必须包含真实程序 producer/consumer、三类与 UNKNOWN 可触发对照、合法补足/返修→复评→批准、L04/R03/L03 前缀/冷 JVM、两入口/四变体/统一投影及反证，不能只交一个分类器。


### 21.1 C 设计复审接收与枚举守卫（2026-10-08）

用户带回独立设计复审：候选具备进入本族实施的条件，1 LOW，零实施与能力证据。此前 §21“待审/未实施”是候选提交时的历史状态，由本段更新。LOW-1 已并入 C 设计 §6，并增加测试 JDK AST 枚举守卫和仅供测试加载的 67 位点索引；新增结果/异常拒绝必须可检出，注释/字符串不误报。包装拒绝标明透传，不把候选映射称为实际 producer 覆盖。此守卫只防枚举遗漏，本族逐规则输入的语义质量仍需实施与批末复核。

设计复审接收时 B05 **IN_PROGRESS**，开工前置守卫已执行；当时本族的实际检查事实、L04 决定、R03/L03 拒绝配对及恢复四分类尚未落码/证明，不能收口；后续状态由 §21.2 更新。见[接收与处置](../../output/lifecycle-batch-05-20261008/scope-c/implementation/cc-review-received.md)。保留完整 C 结束条件；不额外开 LOW 材料轮次，实施及内容稳定的整链离线回归后交 B05 合并独立评审。真实 NO-GO、W 项未完成、其余族 UNKNOWN、ROI/新 authority 停止条件均不变。

### 21.2 C 实施与独立批末判断（2026-10-09）

本族实际 checker 输入/结果生产、真实批准 L04、target-bound 拒绝、native 固定准备失败/R03→L03、本地任务原 L03、唯一四分类已落码；自查补齐四个任务 consumer、covered null、失败码关联、readiness 时间比较、R01 scoped 会话与拒绝后零 R02 资格。原身份/scope/批准/证据、主原因、独立复评、终态和外部 UNKNOWN 均未放宽。真实接口/provider 不在本批。

| 工作列表义务 | 当前证据 / 状态 |
|---|---|
| C producer→决定→拒绝配对→解释 | 原 checker/ReviewGateActions/RoutingEngine/Facts/固定完成/RecoveryAssessment，离线序列已证；本族批末独立 PASS 已接收，见 §21.3 |
| 三类及 UNKNOWN、合法续作 | 真实材料漂移/未来条件拒绝、同输入恢复；测试自身错误 checker 的矛盾机制；缺/错/未采集 UNKNOWN；合成机制与真实业务序列分列 |
| L04/R03/L03、重放与冷 JVM | 两组八类前缀、R03 写失败/删 pair、固定补齐；R01 后重核、R02 已存在不得声称未调用 |
| 两入口/四变体/投影 | 真正 ReviewGateActions 批准的 inline/worker × 四变体→作者/自测/独立验收/人工交付；统一 read model 的原 CLI/server/console 消费与既有回归；未做浏览器视觉验证 |
| LOW-1 枚举机械守卫 | 当前 71 拒绝位点，真实 producer 映射或明确未覆盖；原 67 候选保留历史，测试索引不在 runtime 加载 |
| 最终内容稳定单轮回归 | 1,731 tests / 206 suites / 0 failure / 0 error / 0 skipped，653 source/resource/pom 前后指纹相同，XML 全部新鲜 |
| 独立批末评审 | 用户带回 CC PASS；本族 CLOSED / PASS，原自查和测试凭证不改写。LOW 登记见 §21.3，不自动开启下一批 |

入口：[本批材料](../../output/lifecycle-batch-05-20261008/scope-c/implementation/batch.md)，含实际 dirty 生产基线、24 个 source/resource 增量、消费者边界、最终回归及额外时钟隔离反证。只在本族的已测切片称离线 PROVEN；其余族/委托/I/O 不明依然 UNKNOWN，五族全覆盖未完成。真实 NO-GO、未授权提交/installer/原生运行/第二族、成本偏差停止条款全部保留。

§21.2 收尾注：最终整轮回归后只清除本批 Java 行尾空格并同步测试索引；已有索引守卫复跑通过，1,109 个 class 字节与整轮回归完全相同。整轮源码稳定凭证与格式收尾凭证分列，不隐去这次变化、不把定点数字拼入 1,731。此前 acpx patch 的格式告警在本批之外，未覆盖。

### 21.3 B05 单族独立 PASS 接收与收口（2026-10-09）

当前 B05 首版 C：**CLOSED / PASS（单族、离线）**。用户带回 CC 批末独立评审，原文与作者处置见 [收口材料](../../output/lifecycle-batch-05-20261008/closure-20261009/disposition.md)。接收核验确认 653 项当前源码输入、24 个批内增量、206 份原始 XML 与 474 项交评材料均未漂移；不替代独立代码审查，不重跑测试。1,731 项单轮凭证及格式收尾分列的口径保持；旧交评材料保留其当时状态，文档增量另存。

LOW-1：ClockComparisonProof 尚非常驻回归，登记 OPEN / 非阻塞。owner 为 runtime 合同资格/readiness 回归维护者；下一次涉及有效期、检查时刻、输入比较或四分类的受影响改动必须一并折叠进既有套件。结束条件：不同检查时刻、同实际输入不误判漂移，测试错误 checker 的矛盾机制可触发，真实有效期跨越/依赖变化仍判漂移，并有抓住错误比较的反证。当前仅有一次性补证，不借登记宣称完成，也不为此自动开启一批。

D01 全体/I1.a/W01、其他 W 项、真实 NO-GO 保持；其余族 UNKNOWN，原五族义务保留未完成。第二族按既定真实反复阻塞及可观察损失触发评估；本 PASS 不授权提交、installer、真实 native/provider 或下一批。CC 未核验范围完整保留。

## 22. B06 W06/D09 开工核对（2026-10-09）

用户授权先核对交付链、缺口和中断点，形成下一批开工范围；本轮未获产品实施或真实目标操作授权。材料见 [B06 preflight](../../output/lifecycle-batch-06-20261009/preflight.md)，源码范围/证据等级/transition/失败切面/验证设计/成本止损均在同一入口。当前 **PREFLIGHT / NOT IMPLEMENTED**，作者材料自查 WARN，不能当作实施或真实 Gate PASS。

已核实可复用：B03 在现有 service/plan/adapter 中接入 common-dir→L 锁顺序、durable apply intent、L07 prepared 写集、实际 Git 观察和精确 commit 确认，不从零重做。B05 的 delivery 9 项旧测试用临时真实 Git，四变体两入口旧序列止于 workflow terminal；不能拼成从实际程序批准到目标 Git 的连续交付证明。旧原始 XML 及内容身份只作已有证据，不重跑、不报新验证数字。

剩余工作联合组织：①应用前目标基准与完整 plan/operation 身份核验；②未知交付的人工裁定、旧操作停止证据及合法新操作入口；③当前 Git 观察/历史状态/动作由统一业务读模型提供，各端不只提示刷新或危险重试；④L07/Git 每窗、冷 JVM、共享 target 及完整正常/拒绝交付序列。具体证据和 G01–G07 见 preflight，不扩大 D01 第二族或安全平台。

**先决缺口**：现有 apply 对旧 intent 不匹配时保守零重发，但没有被消费的交付裁定；通用 D07 入口绑定 active node/native preparation，不适合终态后的独立 delivery operation。D09 已声明裁定语义，具体入口/记录/唯一消费尚未指定；不得以手改 plan、删除 intent、自动 retry 或重开 workflow填补。先补该接线的具体协议（含停止/不明事实生产者），与本批其他接线合并受影响范围 CC 复审；本预检不单开材料轮次。若必须新增 authority/监督平台或成本偏差，按既定规则停止交用户取舍。

本轮保存 653 项当前 dirty source/resource/pom 内容与前态、条件性文件范围、主清单增量；无生产/测试源码修改、测试/临时 Git 实验、provider/native、额外 agent、installer、提交或真实目标副作用。源输入与 B05 格式收尾身份相同，整体 W 项和真实 NO-GO 保持。下一步明确为**补齐交付未知结果裁定协议→一次合并设计复审→获授权后 B06 实施及离线验证→批末独立判断**，不能从本预检自行跳入编码。

### 22.1 B06 交付裁定与受影响接线候选（2026-10-09）

按用户“进入下一步”补齐 [protocol.md](../../output/lifecycle-batch-06-20261009/wiring-candidate/protocol.md)。当前 **DESIGN_CANDIDATE / NOT IMPLEMENTED / AWAITING_CC**；§22 的原预检与证据不改为已完成。详设 §9.1 同步，候选字段例子/source anchors/文件范围/文档增量单列于 wiring-candidate，不覆盖此前 preflight manifest。

候选只有一个新的人工入口、两项处置：确认停止且目标精确 base 时授权一次新 apply；或关闭后续 apply、保留现场。已应用/提交使用原 merge/finalize 补确认，正常等待人工 commit 可达。新操作绑定原批准/候选/目标/旧 operation；同请求仅重读回执，冷启动/固定补齐不启动 Git。程序主进程结束、实测退出码、操作员停止声明分别保留证据类型；已知活动不能由人声明覆盖。人工声明的适用性显式交 CC 判断，未当作新 authority 已获批准。

合并设计还覆盖发送前目标重核、完整 plan 绑定、标准 apply 有限 process callback/L07 顺序、当前动作与历史状态分离、各 query 锁顺序及逐写点/冷启动/共享 target 反证。优先现有 owner/测试 harness，无全进程树证明、自动提交、后台恢复发送或新平台。24 个既有文件仍为条件候选，另明确一个 CLI 命令 CREATE 候选，不是文件修改许可。

作者自查、材料格式/身份核验不能替代 CC 独立审查。下一步把协议与 preflight 合并交 CC **一次受影响范围设计复审**；有 WARN/FAIL 回到对应边界，PASS 也不自动开工。复审通过且实施获授权后，按 W06 完整清单实施/离线验证→批末 CC 判断。无新测试/Git实验/native/provider/提交，真实 NO-GO 和总体 W 项保持。

### 22.2 B06 接线设计独立 PASS 与冻结（2026-10-09）

本节记录设计 PASS 接收时、实施授权前的历史状态；随后授权与当前实施结果以 §22.3 为准。

用户带回 CC 合并设计层 PASS、可冻结（2 LOW，无 WARN/FAIL）；原文和作者处置见 [接收记录](../../output/lifecycle-batch-06-20261009/review-acceptance-20261009/disposition.md)。接收核验确认 15 项交评材料及 653 项源码输入与交评身份一致，未重跑测试，不替代独立评审。冻结对象为 §22.1 所引协议及接收补记；候选当时状态原样保留，当前 **DESIGN_FROZEN / NOT IMPLEMENTED / AWAITING_IMPLEMENTATION_AUTHORIZATION**。

LOW-1 理由已落文：精确 commit 与旧 apply 停止是独立事实；程序结束依据已齐备者直接 finalize，无依据者先 resolve，已知活跃不能由声明覆盖；人工确认不写成实测退出码。LOW-2 的规范字节正反对照纳入 B06 必测/常驻回归义务，NOT RUN，收口前必须执行，不能用样例格式核验代替。二者未扩大 authority 或平台，无需为补记再开一轮设计评审。

本设计 PASS 不等于运行无死端/误拒/死锁的穷尽证明，也不把 D01 其余族或其他 W 项的未完成消除。实施发现语义缺口或成本偏差仍停下按既有规则取舍。

下一步：等待用户授权 B06 完整 W06 工作包实施及离线验证；授权后按 preflight G01–G07/V01–V07 与冻结协议 §8 完成合法交付、裁定、同源投影和各中断/冷启动/兄弟路径→稳定内容单轮回归→CC 批末判断。实施、提交、installer、真实 native/provider/目标操作均未授权；无新增 agent，真实 NO-GO 和整体 W 项保持。

### 22.3 B06 实施授权与离线结果（2026-10-09）

本节保留交评时状态；随后独立评审接收与当前收口状态见 §22.4。

用户在 §22.2 设计冻结后明确“授权”。本批实施与离线验证完成，作者自查 PASS，当前 **IMPLEMENTED_OFFLINE / PENDING_CC_BATCH_REVIEW**；范围和开工内容身份见 [authorization](../../output/lifecycle-batch-06-20261009/implementation/authorization.json)，完整增量、消费者与证据入口见 [batch](../../output/lifecycle-batch-06-20261009/implementation/batch.md)。21 项源码/资源和 3 项产品文档增量相对已有 dirty 内容单列，不覆盖此前改动。

单轮全模块 clean 为 **1752 tests / 206 suites / 0 failures / 0 errors / 0 skipped，exit 0**，前后 654 项源码输入身份一致，见 [test-results](../../output/lifecycle-batch-06-20261009/implementation/test-results.json)。常驻序列覆盖四变体×inline/worker 完整程序批准链至临时真实 Git 交付、应用四阶段×八写入前缀及真正冷子 JVM、裁定/关闭/确认前缀、合法重复请求与非法身份、停止依据和规范字节反证。5/5 隔离删除式反证、额外 22 个具体写目标/查询锁序/启动前漂移补证与两 UI Node VM 检查分别登记，不计入常驻 Maven 数字，不冒称浏览器视觉或真实 provider 能力。

转移/失败处置遵循已受审协议，未知旧操作零盲目重发、人工停止与程序退出类型分离、历史完成不借当前动作扩权。两设计 LOW 已落实。作者自查不替代独立评审；W06 和其他 W 项未关闭，真实 NO-GO、提交/安装/真实目标操作未授权状态保持。

下一步：用 [CC prompt](../../output/lifecycle-batch-06-20261009/implementation/cc-review-prompt.txt) 做 B06 合并批末独立评审；接收结论并处置发现后再判本批收口。之后核对剩余主清单与 W08 准入前提，不能自动启动下一批或真实运行。

### 22.4 B06 批末独立 PASS 与收口（2026-10-09）

用户带回 CC 独立批末 **PASS**（1 LOW，无 WARN/FAIL），原文及处置见 [接收记录](../../output/lifecycle-batch-06-20261009/batch-review-acceptance-20261009/disposition.md)。接收时核对 654 项回归源码输入及 276 项实施交评材料，内容身份未变；未重跑测试、不扩大独立评审已核验范围。B06/W06/D09 本批 **CLOSED / PASS（实施与分层离线证明范围）**。此前 implementation/batch.md 与 manifest 的 PENDING 状态保留为交评快照，不改写原凭证。

LOW-1：查询反向锁序断言当前为一次性可判别补证，尚未常驻；下次 W08 离线准备或 query/view/锁序改动时纳入既有测试族，owner/理由/触发见接收记录。CC 明确不阻本批收口。其他一次性补证不自动升级成全量常驻工程。

真实客户端、浏览器视觉、真实目标交付、整体 P0/W08/W09 和其他 W 项不随本批通过；真实 NO-GO、未授权提交/安装/真实运行状态保持。评审末段“W08/W09 都需真实运行”须按原 W08 定义理解：W08 仍先做离线/案头组合对账、投影与工件/拓扑/预算/停止前提核验，不能跳过直接启动 W09。

**下一步建议：W08 准入前离线对账与 Gate 草案**。按已有 B01–B06 证据区分已证/待证/明确延期/仅真实 Gate 能证，核对 W07 与主流程必需出口，给出必要工作及有界 fresh Gate/预算/止损；不以全部理论分支完备为准入门槛，也不把单轮全绿当全部准入。本次仅接收并记录评审，未启动下一批或真实运行。

## 23. W08 离线对账与真实 Gate 草案（2026-10-09）

用户明确请求本轮离线对账与 Gate 草案；不授权真实 provider/native、installer、真实目标交付、项目提交或额外 agent。P0 材料与一个既有 LOW 的常驻化完成，当前 **OFFLINE_RECONCILED / AUTHOR_WARN / TECHNICAL_NO_GO / AWAITING_CC**。入口：[assessment](../../output/w08-offline-gate-20261009/assessment.md)，含 W01–W09/必要缺口/六维与链表、逐字段 W07、fresh 候选和 Gate。

B01–B06 历史 PASS/具名序列可复用，不从早期表中“未完成”重做已落地代码；主清单 §2 已按最新证据更新，未统一关闭所有 W 项。D01 其他族/首版声明限制/非驻留时钟/内核 I/O/全部浏览器与真实 worker 能力等有界残余分别登记，未被新 Gate 偷偷移除。

B06 LOW-1 已纳入既有 DeliveryViewServiceTest：实际 status/overview 观察在 L 外、组合同代 view 在 L 内，6 项聚焦回归 PASS；隔离副本把观察移到 L 内，新增常驻用例按预期 1 assertion failure / 0 error，未改主工作区生产类。仅这一测试文件为源码增量；普通必要离线 package 与新 CLI help/三定义 validate PASS，发行内嵌模块字节一致。1752/206/0 仍是 B06 单轮历史数字，本轮不拼成新全模块凭证，不重复已充分通过的生产回归。

**G01 确认缺口**：DesignScopeValidator 在 GOAL 允许 planned_files 提案、GOAL 不授实现 scope，但 design-claude 模板要求复制已批准精确路径/操作；真实模型可能被引向旧顺序。已列入 W07 必需纠正及同族 sweep，本轮未改生产代码/模板。G02–G05 为当次实际 native 装配、证据出口、发行/拓扑、需求/目标/预算批准；静态 audit 与当前生成源码/dist 的 patch 身份不一致不是运行结论，不默认要求重新安装或新增客户端版本锁。

[Gate 草案](../../output/w08-offline-gate-20261009/verification-gate-draft.md) 提案为一 fresh 标准库 Python 小任务、terminal-delivery/INLINE、最多18顶层调用、600s/命令、累计运行60min、可见实际token160000止损；均未获用户预算批准，也不是原生内部绝对调用/token/子树取消保证。关键和最终人批准保持，真实能力未证不能被合成 fixture 填PASS。

下一步：[CC 受影响范围评审](../../output/w08-offline-gate-20261009/cc-review-prompt.txt) 合并核查对账、G01分类和 Gate 可执行性/ROI，避免为两处材料补记另开轮次。之后处理 G01 和当次前提，具体 Gate 齐备再交用户批准；不自动实施、安装或启动真实运行。

### 23.1 W08 准备批次：G01 修正与 G02–G05 候选（2026-10-10）

用户带回前轮 CC 材料层 PASS/WARN/NO-GO，随后明确批准准备批次；仅授权 G01 接线纠正及必要离线验证、G02–G04 案头/元数据/发行核验和具体 Gate。当前 **AUTHOR_SELF_REVIEW / AWAITING_AFFECTED_CC / REAL_RUN_NO_GO**。入口：[本轮 assessment](../../output/w08-preparation-20261010/assessment.md)，增量相对批前 dirty 单列，不覆盖既有改动；前轮输出与测试凭证原样保留。

G01：GOAL 下 design/plan 文件是 canonical 提案，不假定已批准实现路径；只有 EXECUTION 授具体操作。修正 renderer、design/plan 作者/复评、intake/共享原则和 passive guidance；implement 保留精确复制 EXECUTION 及越范围重批要求。未改 validator/compiler/qualification/批准/身份/scope/恢复。CC LOW-1 的 GOAL 旧指令负向和 EXECUTION 精确约束正向均用真实 qualified 输入渲染，三种 plan 拓扑 × 四角色，并保留严格程序拒绝与合法续作对照。

先红两项断言失败；98 项首轮聚焦全绿。首轮全模块发现 acceptance 提示 6555 超过既有 6500 字符预算，按 FAIL 留档；只精简重复 passive guidance 67 字符，不放宽预算或断言。最终单轮 clean：1755 tests / 206 suites / 0 failure/error/skipped，654项输入前后相同；新package及CLI三次help/三个定义validate PASS，CLI/server内嵌模块及资源字节一致。凭证见本轮 test-results.json / release-identity.json / artifact-verification.json，不拼中间绿灯。

G02：本仓 wrapper 实际 build-info 为当前受审 1517f828，config/help/permission-reply 元数据已测；不执行客户端/session/provider/installer。配置默认 approve-all 不作业务授权，原具名 profile 显式覆盖；实际握手/工具能力和最终 workspace 装配仍待授权后的核验。G03：三条验收的方法/证据/生产/消费及合法补齐见 verification-exits.md，真实 GOAL/epoch/occurrence 产生后走原 record-readiness，再作者→复评→EXECUTION 编译/批准；可用性声明不冒充执行实证。G04：固定本轮新发行及 source/resource 内容身份候选，实际启动 PID/loader 不冒填。

[更新 Gate](../../output/w08-preparation-20261010/verification-gate-draft.md) 和纯产品 task-requirement.md 分开；G05 仍待批准。提案为 fresh Python/标准库任务，新临时 target/worktree、terminal-delivery/INLINE，18 顶层调用/600s 单命令/60min 主动窗口/160000 可见实际 token。实际正常主链 9 节点（纠正旧草案误写 8，不增预算）；内部总量不保证绝对硬限；关键/最终人门及实际 commit 责任需本次具名。

下一步：[CC 合并受影响评审提示](../../output/w08-preparation-20261010/cc-review-prompt.txt) 核对修正、证据、Gate 可执行性及 ROI；按发现纠偏后向用户提交 G05。材料 PASS 不授权真实运行、目标创建、安装或项目提交，也不自动关闭 W01–W09。

### 23.2 W08 准备批次独立 PASS 接收与 G05 提交（2026-10-10）

用户带回 CC 独立复审 **PASS，无 WARN/FAIL**；本准备批次 **CLOSED / PASS（离线范围）**。接收核对 439 项原材料、654 项源码输入与 CLI/server 发行 JAR 全部未漂移，未重跑测试。原交评快照保留其当时状态；[接收记录与原文](../../output/w08-preparation-review-acceptance-20261010/disposition.md) 分列 CC 结论、作者身份核对与未核验范围，不能以接收代补独立审查。

G01 与 G02–G04 具名材料范围收口；总体 W08/W09、真实客户端/工具/目标交付能力未证，真实 NO-GO 保持。实际启动/配置/当前 GOAL/readiness 等动态前置仍在合法时点核验；不把材料 PASS 当程序事实或运行许可。

下一步唯一用户决定：[G05 任务/目标/预算/责任审批摘要](../../output/w08-preparation-review-acceptance-20261010/g05-approval-proposal.md)。只摘录现有已评审 Gate，不加额度或改变验收；原样无需追加 CC 轮次，实质变更重核受影响结论。截至本节接收时 G05 与真实运行授权均未取得（最新本地批准见 §23.3）；用户若明确一次授权覆盖当次准备与运行，前置核齐后不重复申请同一许可。关键/最终业务门与原生逐请求批准仍按原 Gate 保留。

### 23.3 G05 本地批准、fresh 初始化与运行前核验（2026-10-10）

用户“批准”按此前明确划分受理为当次 G05 任务/目标/预算/责任及本地准备；**不扩大为真实模型调用或关键门预批准**。本地准备 PASS，真实 Gate NOT RUN / NOT AUTHORIZED；材料见[本地准备报告](../../output/w09-local-preflight-20261010/assessment.md)与[P01–P08 实测核对](../../output/w09-local-preflight-20261010/gate-local-preflight.json)。没有新设计决定，无需为本次状态补记单开 CC 轮次。

新 target `/private/tmp/dual-w09-newlines-20261010/target` 只建立一次空基准提交 `6081fc1888db9a6a61291a7d0822537d8a0f89ff`；原 Dual 仓库未提交。冻结 CLI `start` 未带 --run，由原 owner 创建 linked worktree `/private/tmp/dual-w09-newlines-20261010/worktree` 与分支 `codex/w09-newlines-20261010`，新 workstream `ws-20261010T055644Z-6QCS`，workflow `agent-lifecycle-terminal-delivery@5`；只有 READY context intake 节点，无 agent run/native session/业务批准。RUNNING 是实例状态，不能推成模型正在执行；start 的旧 design 摘要不作当前入口事实。

654 冻结输入与 CLI/server JAR 字节复核一致；原 frozenRuntimeIdentity 确认 liteflow/600s/实际发行 SHA。初始化 JVM 的 entrypoint/kernel code source 与同 loader 有实际日志；后续路由 JVM仍须重核，不冒称 holder 已加载/执行。实际 effective workspace 的 build-info/config 两次 metadata 核 wrapper/dist/patch=1517f828/launch-spec 与受审组合一致；原 global config 未变，无 project override。Python/stdlib 宿主条件实测一致，不能当原生工具或产品测试证据。新 target/worktree 的 canonical/common-dir/base/tree/clean均核对；局部 registry仅索引。

P01/P08 当次提案及限额已批准，GOAL/EXECUTION/最终交付仍等实际门的用户决定；readiness须等真实 GOAL/等待 occurrence，不提前造 ref。P05 native ask、P06 原生工具与 usage、真实独立验收/产品交付均未运行；自然未发生路径不填 PASS。18顶层调用/60min主动窗口/120k预警/160k可见实际token止损/最多两次有证据修订不变，最终产品 commit仍由用户执行。

本地核验脚本一次误用绝对 Path.parts[0]，结果 INVALID；已保留错误及实际目录清单，改成 entry name 后只读重核通过，未修改生产或账本、未重新 init。原回归未重跑，不拼接或增加测试数量。

**下一步**：明确授权真实运行后，在此新实例按原 Gate 单 INLINE、逐段执行，每段前核身份/状态/预算，真实 GOAL 门先交用户；外部 UNKNOWN/新 authority/范围或成本偏差继续按原止损，真实 NO-GO在取得当次运行授权前保持。
