# Dual 生命周期：主工作清单、改动对账与证据地图

日期：2026-09-27；2026-09-28 补记评审处置。目标阶段：P0 完整研发生命周期可靠性。状态：CC 材料层 PASS，2 项 LOW 已修订；W00 材料任务收口，产品工作项未 Promoted；实施、提交、真实运行均未授权。

本文是唯一跨批次工作索引，不重新定义验收。唯一设计依据为 [R3 设计](20260923-dual-lifecycle-reliability-design.zh-CN.md) §0/§3–§7.1；证据层次和状态沿用该文 §5 与 [engineering-checklist](../engineering-checklist.md)。后续更新本文，不再按日期复制一份新主清单。§3 的文件处置都是建议，不授权删除、还原或提交既有改动。

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

“部分 Implemented”仅描述现有实现，不是整项 Verified。所有关闭判定必须回指 R3 条款；下表的下一步是待办，不是实施授权。执行者为主 agent，独立判断由用户发起 CC；最终交付由用户或其显式授权者批准。

| ID / 完整用户流程 | 契约依据 / 领域 owner | 现有事实与证据 | 剩余工作与必要设计决策 | 依赖 / 状态 |
|---|---|---|---|---|
| W00 对账与建立完整覆盖地图 | R3 §7.1；文档索引不持有运行 authority | 本文 §3 覆盖所有相关 dirty 文件；§4 映射测试与缺口；冻结身份可复核；CC 材料层 PASS | 2 LOW 处置及限制见 §8；后续材料实质变化重核受影响结论，不以本次抽样代替产品证明 | 材料任务收口；独立评审与作者修订核验分列，不是产品能力完成 |
| W01 人工决定→作者修订→复评→重新批准；失败原因→合法补足后续作 | I1/I1.a，§4 人工决定行；core 动作、RoutingEngine、revision/lineage resolver、RecoveryAssessment | A01–A04/A09；T01/T02/T15；READY/BLOCKED 续作与非法身份有现成序列 | D01 冻结 occurrence 决定—被拒前置配对、决定时证据、恢复消费及不能记录时的出口；修订额度耗尽仍须有可解释且真实可执行的处置；与 W05 明确本地提交边界 | W00；准备失败表达依赖 W02，耐久续作依赖 W05；部分 Implemented，I1.a DESIGN GAP |
| W02 输入准备→provider 执行→候选验证→发布→node/run/recovery | I2；adapter、execution resolver、validator/finalizer、统一恢复 read model | A05–A08/A10；T03–T06；前置失败已封闭建模，旧反例 E02 仍指向当前判断顺序 | D02 冻结阶段事实和冲突优先级，修复执行原因被后处理改写；counterproof 必须常驻；保留成功/警告对照和 CLI/API/worker 同源消费，不能只修一个 outcome 分支 | W00；与 W01 共享事实、与 W05 共享发布窗口；部分 Implemented，确定缺口未解决 |
| W03 目标批准→只读设计/计划/复评→精确范围与必要权限批准→首次实现 | I3、I1；proposal/TaskContract/qualification、四种 definition、adapter/transport | A11–A13；T07/T08；现有严格批准和 scope 可复用，阶段顺序仍是旧合同先行 | D03 冻结分期批准表示、compile/qualification/逐门迁移及旧批准失效；D04 冻结只读 profile/tool/session、可信 baseline 与程序 writer 豁免；D05 同批绑定 W04 的验证出口，防止互相要求对方先完成 | W00；W01/W02 安全网；与 W04 联合设计/验收；DESIGN GAP |
| W04 必需验证可行→实现→自测→独立验收；仅缺材料时补证据→再自测/验收 | I4、I1、I2；plan/contract、EvidenceCollection、ExternalVerificationEvidence、自测与验收 | A10–A13；T08/T09；工具/上下文缺口改走现有人门，补证据和代码返修已有区别 | D05 冻结必需验收项与方法/环境/权限/责任/证据绑定及检查时点；环境失效必须保留原因与解决动作；不可把上传声明当机器执行证明 | W03 联合；W02 失败事实；W05 部分写恢复；部分 Implemented，I4 DESIGN GAP |
| W05 进程中断→核对冻结身份/耐久事实→本地幂等续作；外部未知→人工裁定；终态不重开 | I5、I1；ledger writers/readers、runtime/worker、kernel | T02/T10/T11/T15；已有拒绝孤立后继与终态保护；R1 测试直接断言重放缺口 | D06 比较既有 R1 候选后选本地提交/reader/event 幂等协议；覆盖 queue/task/execution/run/node 桥接；D07 冻结运行身份和 provider 未知窗口；D08 证明默认 LiteFlow 临时隔离或完成等效保障 | W00；与 W01/W02 写序协同，与 W06 外部窗口协同；DESIGN GAP |
| W06 验收满足→候选批准→目标 Git 应用→实际提交→交付确认/中断处置 | I6，§4 交付行；candidate、DeliveryApplicationService、Git、query | T12；已有 candidate/base/tree/parent/HEAD 严格绑定和正常 apply/finalize | D09 列清每个外部变更与 ledger 写点前/间/后状态、可观察事实、允许动作和不明结果处置；不能把 APPLIED 空 commit 判错，也不能盲目重做 Git apply | W04 真实验收；W05 版本/本地记录边界；部分 Implemented，恢复详细设计缺口 |
| W07 机器身份与语义义务分离，修订能回应真实意见且不丢约束 | I1/I2 的 GCTW 承接；既有 context/contract owner、有限 correction | T13 与 E04；程序示例生成 fixture 已暴露此类测试不能证明模型复制负担 | D10 逐字段列机器绑定与模型语义，核实现有有限 correction 足够否；需要改动时选已有 context/validator 最小增量，保留错身份/漏义务拒绝；不提前承诺 token 节省 | W00；关联 W01–W04；详细设计未定，不自动升级为成本优化平台 |
| W08 四变体、两种执行入口与各投影组合回归→冻结可审查运行 Gate | §4/§5/§6；以上全部消费者，CLI/API/UI/report | T06/T14；部分组合有受控断言，small 不是第三个 bundled；脚本 fixture 有独立局限 | 验证 W01–W07 所声明的必经合法/拒绝/中断路径；补遗漏而非追求测试数量；核验 UI/report 与 CLI/API 对状态、资格、原因和真实下一步同源消费，不各自推导 authority；确认实际 JVM 拓扑、kernel、工件/配置、预算监测与停止方式；清单/地图纳入同次独立评审 | 前项所需能力与证据，未达成；技术 NO-GO 与未授权分列 |
| W09 fresh 合法任务真实研发→测试/验收→批准交付→按声明级别确认结果 | §0、§6；批准的需求、真实 evidence、candidate、目标 Git | E04 历史调查与旧 Gate 仅作输入，当前组合真实产品交付 NOT PROVEN | 当具体 Gate 技术前提满足且取得授权后，执行有效 fresh case；FAIL/INVALID 止损不拼接样本；自然分支未触发记 NOT EXERCISED；不恢复旧样本、不以一次成功证明所有场景 | W08；预算/目标/外部权限依 Gate 决定；本轮不运行 |

### 2.1 次序、依赖与独立评审触发

W00 的实际载体已获材料层评审，随后确定 W01/W02 的字段/阶段契约与 W03/W04 的联合详细设计，随后将相关写序纳入 W05/W06；W07 与前述语义一同评估，避免孤立优化。详细设计可交叉推演，不意味着串行等待全部设计才能思考下一项，也不授权代码实施。未来按完整用户流程组织批次；W01–W07 不能分别关闭后就假定 W08 组合成立。

W01–W06 均跨 R3 §2 多个 owner 且涉及 §4 transition，W07 改变 context/contract 消费边界时同样触发；W08/W09 使用这些组合及完成结论，必须接受对应独立判断。W00 的条目结构/覆盖地图已在本次材料评审核验，范围与 LOW 处置见 §8；后续实质变化仍纳入对应批次评审。纯文档指纹修正可不触发产品跨层评审，但不得改变验收条款或 silently 重用失效证据。

### 2.2 必要决策与本轮边界

| 决策 | 已有可复用位置 / 需比较的内容 | 详细设计结束条件；目前没有替用户选择的事项 |
|---|---|---|
| D01 续作诊断配对 | gate/decision、NodeResult 与现有失败 writer；不另建诊断 authority | 明确字段、原始证据、消费/分类条件、写入失败与冷读，不靠旧决定回填；真实合法续作与错误拒绝成对 |
| D02 执行失败保真 | AgentExecutionOutcomeResolver/AgentExecutionResultFacts/validator/finalizer | 定义未调用、过程不可信、输出不合法、产物不合法的适用条件及冲突矩阵；前后处理不会把衍生缺产物盖过决定性执行失败；同一语义由唯一规则消费。详细设计须明确 E02 常驻回归的源码落点、两执行入口及成功/contained-warning 对照的场景和断言；对应 W02 关闭须以这些常驻测试已在源码存在且实际通过为证据，不能只以矩阵或 resolver 改动关闭 |
| D03 分期批准与自动推进 | proposal/TaskContract、qualified input、四份 definition 的 gate/required refs | 目标批准与执行批准的 subject/schema、四变体逐门保留/迁移表、重大取舍和批准失效、预算耗尽出口；READY 的 decision 固化、无人工门路由、D06 写集及 reviewed/人工授权投影须冻结；不得先拆掉资格校验再补批准 |
| D04 只读执行面 | AcpxPermissionProfile、ClaudeExecutionPolicy、transport、既有准备/发布入口 | 用户已同意优先细化 adapter 强制只读绑定，保留 Claude 作者／Codex 评审；先证明必要读取与无写/execute 共同可行，再冻结 Claude/Codex、inline/worker、correction/session 绑定及 baseline/writer 归属。成本收益偏离须停止等用户确认，具体见详细设计 §8.6；不重开 R4/Phase B，不等于实施授权 |
| D05 验收出口 | 现有 contract/plan/execution policy 与外部材料流程 | 必需项标识、可信方法/环境证明、检查者/时点、细化责任、失败续作；新增能力必须证明无既有替代，存在重大取舍则交用户决定 |
| D06 本地一致性 | R1 的 reconciliation / prepared mutation / generation pointer 比较与现有 writers | 选择方案并列出所有受影响写点、reader 可见性、event 幂等、冷启动与 queue/execution 桥接；不默认 journal，不以安全停止满足可续作 |
| D07 运行身份/未知执行 | 既有 workstream/run/attempt/输入批准引用 | 固定版本与合法修订输入分开、唯一 owner/写序/恢复核验点；spawn/产物写入是否发生未知时禁止盲重放；未知历史版本 INVALID |
| D08 LiteFlow 临时隔离 | FlowKernelProvider、holder 生命周期、server/API/worker/CLI 入口 | 记录实际配置/JVM 拓扑并证明约束有效；一次快照或 WorkstreamLock 不足；不凭怀疑换引擎 |
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
| T04 执行失败有有效候选但不能发布；I2 | WorkflowFirstLifecycleSequenceTest `providerFailureCannotPublishEvenWhenItReturnsAValidCandidate` | inline/worker，阻断 RATE_LIMITED，扫描无 sidecar | 只覆盖带阻断 signal 的一类；无阻断 signal 的 incomplete process 仍有 E02 反例 |
| T05 执行/产物冲突原因保真；I2 | 历史隔离 `incompleteProcessCannotBeReclassifiedAsMissingArtifactAfterPublicationIsBlocked` XML，两模式均 expected process_output_incomplete / actual required_artifact_contract_failed；当前 resolver 行序一致 | E02 历史反证 + 当前代码追踪；该测试方法不在当前常驻源码 | 必须常驻并增加成功/contained warning/组合对照，不能只凭 rate-limit 用例通过关闭 |
| T06 原因→恢复提示→CLI/API；I1/I2 | RecoveryShowCommandTest `preparationFailureExplainsHarnessInputsInsteadOfAskingForModelJson`；WorkstreamApiControllerTest `preparationFailureKeepsTheSameReasonInRecoveryAndOverview` | 构造失败 ledger/共享 RecoveryAssessment 的投影测试；本轮读 diff，未运行 CLI/server 测试 | 不是从真实 provider 一次走到 UI/report 的证明；配对诊断和恢复动作可用性待补 |
| T07 设计/计划义务与批准基准；I1/I3 | AuthoredBoundarySequenceTest：缺回应拒绝、损坏批准输入/部分发布、同 bytes 新批准使旧链失效、implementation/self-test 入口消费批准 plan；ContractCompileHandlerTest 的孤立 gate 拒绝；AuthoredStageSequenceTest 的自动/人工 plan 复评、两次 design revise 留存 note、两业务域批准 design→plan→消费 | 既有受控序列、冷 JVM 与字段合同；本轮读相关源码与 dirty diff | 目前仍是设计前完整 scope 批准；不证明两阶段授权，也不证明只读工具面 |
| T08 REJECT→返工/补前置→自测→再验收；I1/I4 | WorkflowFirstLifecycleSequenceTest `unavailablePrerequisitesRequireHumanDispositionThenEvidenceReverification`；AcceptanceRepairSequenceTest `evidenceOnlyAndMixedRepairUseDifferentRealProducers`；SelfTestContinuationSequenceTest 三变体条件分流；AcceptanceRevisionBudgetTest | 新测试四变体×两模式×两类别×collect/revise，断言实现次数、自测/验收次数；旧序列使用受控 handler；预算测试仅 loop | 新测试不替代外部 submit 材料链；同内容重复写不能证明所有返工质量；非 loop 人门预算耗尽的完整可继续/终止出口仍需核验 |
| T09 材料准备/提交/自测/验收；I4/I5 | ExternalVerificationEvidenceTest：实际 runtime sidecar 精确 refs、幂等 submit、基准/材料篡改、partial gate writes、cold JVM 重验；EvidenceCollectionSequenceTest 作为兄弟来源 | 程序 ledger/runtime + 受控 handler/材料，inline/worker；有真正子 JVM 读取；本轮读取指定方法 | 证明材料绑定与拒绝，不证明 operator 声明的外部测试实际运行；partial writes 仍只拒绝，验证出口前置 schema 未有 |
| T10 本地写前缀和执行桥接；I5 | MultiFileMutationRestartAssessmentTest 四组测试：initialize、gate resolve、stale recover、normal routing | 源码直接断言部分前缀无法重放、current/instance 不同代、event 重复；历史 4/0 是“缺陷评估通过”，不是恢复实现通过 | R1 14 前缀续完、reader 一致性、M6 queue/task/execution 桥接及运行身份均未证明 |
| T11 发布后节点落盘失败；I2/I5 | WorkflowFirstLifecycleSequenceTest `publishedCandidateWithoutDurableCompletionCannotReachTheReviewerOnColdResume` | transport 写 failpoint，确认 sidecar 已存在，resume/worker 没有新增调用；同 JVM 内重建 runtime，允许异常被捕获 | 不证明新进程恢复、不证明可续完、不证明异常分类提示完整；不能命名“冷恢复成功” |
| T12 候选批准→应用→实际提交核验；I6 | DeliveryApplicationServiceTest：真实临时 Git apply、expected tree、finalize；source/candidate/parent/tree/已批引用异常拒绝；APPLIED 错 commit 保留 | 真实本地 Git + 人工构造已批 ledger，独立于实际研发链；历史 9/0，当前未复跑 | apply 后日志/plan 写失败、commit 已发生 finalize 失败等 failpoint 缺完整覆盖；与 W08 研发到交付组合未证明 |
| T13 真实语义意见与机器身份负担；I1/I2 | AuthoredBoundarySequenceTest 缺 response 不可发布；新增人工修订 fixture 根据当前 context 构造 obligation_patches；E04/GCTW | 程序严守义务有证据；新 fixture 使用 AgentResultPromptContract.render 的示例，自动配齐机器字段 | 未量化真实模型复制负担；成功 fixture 不能证明 schema 易用或独立语义正确；D10 不能靠放宽身份解决 |
| T14 全链、终态、variant 与 kernel；§4/§5/§6 | WorkflowFirstLifecycleSequenceTest `acceptedProductTraversesEveryGateAndTerminalPreventsNewExecution`；四种 definition tests、脚本 smoke 修订；LiteFlowPrototypeKernelTest 的小型条件路由与 built-in 候选比较 | 四变体×inline/worker 到 COMPLETED，终态状态/文件零改写；主序列用 LiteFlow；脚本为 fake acpx | 没有四变体×两模式×两 kernel×全部失败的全矩阵证明；不必盲目笛卡尔积，但每个共享点/差异/拒绝出口须说明。未证明默认 LiteFlow 跨 JVM 拓扑约束、UI/report 和真实产品 |
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
| artifact 发布；ArtifactContractValidator/finalizer | 读取 typed process + envelope/领域校验 → assessPublication → sidecar 写入 → runtime 再 finalizer → ledger 路由 | rate-limit 可在 sidecar 前阻止；incomplete process 在第二次判定可能被新 artifact failure 覆盖；run.json finalizer 当前直接 writeValue，异常抛 IllegalStateException。W02 与 W05 共同处理原因与持久化，不能相互代替 |
| 外部材料；ExternalVerificationEvidence | prepare 基准 → 固化材料/manifest → resolved gate/node/event → self-test 消费 → acceptance 消费 | T09 证明部分重复提交幂等与损坏拒绝，但 gate/node 中间失败拒绝重放；材料 hash 不证明外部行为执行 |
| Git 交付；DeliveryApplicationService | 完成/候选/源目标基准核验 → compatibility artifacts → plan READY → Git apply → stdout/stderr → staged tree → plan APPLIED；外部实际 commit 后 finalize 校验 → COMMITTED | apply 已变更目标后日志/plan 写入可失败；现有 catch 可能记录 FAILED，不能据此推导 Git 未变更。D09 必须以目标 Git 事实解释是否可重试；正常服务单测不覆盖全部窗口 |

## 6. 当前到真实交付 Gate 的距离

按 R3 §6 逐项记录。本轮推进的是“明确阻塞与证据”，没有把未实现功能写成技术 GO，也没有把未授权变成技术问题。

| Gate 项 | 本次变化 | 剩余阻塞 / 对应工作 |
|---|---|---|
| 冻结组合 | 更近：已冻结源码/文档 baseline 与本轮测试命令 | binary/schema/template/acpx/config/runtime identity 尚未成套冻结；W05/W08 |
| 冻结运行清单 | 持平：本次是评估索引，不是产品运行清单 | 需实际 run/occurrence/批准/干预/交付引用及导出可用性核验；W08/W09 |
| LiteFlow 隔离 | 持平：代码默认 LiteFlow 已核实 | 实际 server/worker/CLI JVM 拓扑与全 holder 生命周期保障未验证；D08 |
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

用户明确要求：后续相关实施若发现成本与收益偏差，停止等其确认。此要求也约束设计阶段发现的扩围。按详细设计 §8.6 对照预期与实际证据，记录在当前 D04/W03 条目；需要引擎核心、额外工具服务/隔离平台，或损失读取/评审/合法续作收益等情况不得先做后报。停止受影响工作并提交成本、目标影响与选项，不能擅自切到同 provider、降低验收或覆写用户改动。其他独立且已授权的核验不受影响。
