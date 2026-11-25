# 独立 harness 架构与交付评审

评审日期：2026-11-02。独立作者：fresh_harness_review。只读评审，没有修改生产代码或旧 ledger，没有启动 provider、恢复案例或委派其他 agent。首版判断形成前没有阅读 cross-month-ledger-review 的 review.zh-CN.md、evidence-index 或其他审计叙事；最终稿仍未读取这些报告。

## 结论先行

“系统不能产出合格交付”这个绝对前提不成立。W09 第八例已经从真实 provider 调用走到独立验收、精确批准、目标仓库提交，我复核了 Git 对象与文件字节，并在独立副本复跑测试通过。它是实质反例，不能因为目录名含 fixtures 就排除。

更准确的问题是：系统已经较强地约束不合格状态，却尚未证明能稳定、低干预地把合法工作推进到交付。最有行动价值的结构性解释是：**模型同时承担业务推理和机械接线；下游严格验证能发现接线错误，但部分错误到 reviewer 已同意后才暴露，接着由操作者重写 JSON、绑定证据或修复运行环境。** 流程不是没有状态模型，而是严格拒绝与顺畅推进的成熟度不同。

这不是对全部历史的单一根因定论。运行版本、任务用途、环境和支持边界不断变化；证据足以证明下面的机制存在，不足以估计它们各自造成多少失败或人工时间。模型能力、上下文长度、层数和 token 数也不能仅凭这些 ledger 判定为主因。

Boundary Review：**WARN**。治理建议：**DESIGN_GAP / PROCEED_WITH_GUARDS**。保留已有强制边界，先完成一个“机械接线零人工修正”的最小贯通检验；不建议重写编排器。

## 开工前收紧的问题

顶尖专家还会做四件用户通常没明确要求的事：把“完成”追到目标 Git 对象而非流程绿色；寻找成功、安全拒绝和已修缺陷的反例；把业务决定与机械救火分别计数；核对运行工件身份，不拿当前源码解释所有旧事故。

最原始素材是逐行 events.jsonl、workflow/current.json、不可变 definition、节点和 gate 字节、run.json、原始模型最终消息及 tool updates、execution prepared/intent/outcome、批准对象和 SHA、Git commit/tree/patch。README 和已有清点只负责定位、声明用途，不能替代这些事实。

方法与工具：boundary-first-engineering 的所有权和完整转换检查；architecture-cartographer 的模块/数据流映射；architecture-governance-review 的最小改动检验；RTK 包装的只读文件搜索、Python JSON 解码与 SHA-256、Git 对象核对、独立源码副本 Maven 离线测试，以及交付文件副本的 unittest/doctest。Serena 手册已读，Java 语言服务启动失败后使用只读文本定位，没有调整用户环境。

除了你我，能验证结论的是：原始 event 行号、原始文件哈希、Git 对象、可重跑的测试及其 XML、失败时不会启动外部动作的反例测试。独立 reviewer 的身份本身不是证据。

改写后的题目：**冻结版本、区分实验与产品案例，从授权到目标仓库交付重建完整生命周期。哪些原始事实证明某个机制会造成非业务人工干预，哪些停止其实是正确保护？在不放松批准、身份、范围和终态约束的前提下，哪一个最小改变能让相同合法输入无需人工修 JSON 地到达下一执行消费者？若不能判主因，先做什么决定性检验？**

## 范围、版本与证据限制

以既有 inventory 的路径作检索入口，重新解码 169 个 ledger 条目的 9,349 行事件、990 个 run metadata；不是接受索引的根因标签。5—10 月条目分别为 1、80、49、8、14、17。13 个案例另做链路细读，原始文件哈希保存于 evidence-sha256.json。完整结果和可复跑清点脚本见同目录 raw-census.json、inspect_original_ledgers.py、raw-chain-extracts.json。

这些条目**不是 169 次同质独立实验**：包含受控协议样本、不同 workflow variant、不同 schema/definition、遗留未收尾 worktree、真实 provider 运行与冻结副本。当前索引已选取副本，评审没有重新穷尽发现所有已删除路径。152 个条目没有当前形式的 frozenRuntimeIdentity，17 个有；因此不能把当前代码等同历史运行 binary，也不能从字面 WAITING_FOR_RECOVERY 或 COMPLETED 算当前成功率。没有 delivery_committed 事件也不能证明历史从未通过外部方式合并；只能说 ledger 没有该证据。

评审源码取自当前未提交工作区，复制了 662 个源文件到 source-snapshot，全部记录 SHA。Git HEAD 是 3362eb42；这只是基线信息，不代表复制源码等于 HEAD。没有以复制源码升级任何旧实例。细读样本覆盖 June 的 P837/HCUM，July 的 PBRD/G73K，August 的 HUAX/AHBV，September 的 3B2P/6NX5，以及 October 的 MBC2/RN9X/Y2KB/9YYF/7SNA。

## 当前系统地图与权威事实

系统用途是减少人来回复制 Claude/Codex 产物和记忆流程状态，同时得到经审查、可追踪的研发交付。单纯多跑 review 或多生成报告不等于完成这个用途。

| 层 | 应拥有的事实 | 实际主入口/消费者 |
|---|---|---|
| dual-core | workflow/domain 状态、TaskContract、typed execution outcome | runtime 与 ledger 消费 |
| runtime + lifecycle JSON | 声明图、契约资格、作者/评审绑定、执行调度与恢复语义 | WorkflowRuntime、ReviewDecisionHandler、ContractCompileHandler |
| adapter-acpx | 实际进程、session、provider 输出、typed result 和 tool 观察 | 输出先解析/校验，再交 runtime；不直接以正文路由 |
| ledger-file | instance、node/gate/execution 的持久事实与事件、事务序列 | LedgerSession、WorkflowFileLedger；current 是受提交约束的状态索引 |
| adapter-git + delivery service | 目标仓库观察、接受候选、应用意图/进程与 commit 证据 | DeliveryApplicationService 与候选 resolver |
| CLI/server/read models | 解释“停在哪、为何停、可做什么” | WorkstreamAttention、query/delivery views；不创造批准 |

Definition 是冻结 workflow/contract 规则；Instance 是此工作流、节点、gate 的 durable occurrence；Execution 是具体进程和输出尝试。产品目标接受事实、workflow 终态与目标 Git 已提交是三种事实，不能相互代替。Registry 仅为入口索引，workstream.json 的旧 created 字段也不能当运行状态。目标仓库 Git 是交付外部结果的独立事实来源。

主链：需求提案→产品审查/需求门→GOAL 编译→设计和独立审查→计划和独立审查→readiness 检查→EXECUTION 编译和精确批准→实施→自测/必要 repair→验收→固定交付候选→人批准→目标仓库应用→人或显式委派 commit→finalize。当前源码明确存在 typed 失败、终态守卫、固定执行输入、不可盲重放外部动作、transaction commit 与共享 delivery view；不能把它描述成“只有 prompt 的脚本”。

## 最重要的四项发现

### 1. 模型在手工复制权威事实，且可执行性检查晚于付费 reviewer

**已证实、强因果（对应具体链）；泛化为主要设计风险有中等把握。**

7SNA 首计划把 verification_bindings 绑定到 TR-1..TR-4，environment_refs 为空，feasibility_refs 指向提示词 manifest。独立计划 reviewer 返回 READY_TO_IMPLEMENT。随后 plan.decision 正确拦住，readiness_detail 是 unexpected or duplicate criterion。原始证据：

- /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/runs/run-1793030521008/outputs/plan-claude.plan.json:26、29（后续三条同类）；
- /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/nodes/node-plan-decision-020.json:58—59；
- /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/events.jsonl:72、76—79。

操作者必须写长反馈，指明 AC 编号、确切 ref/SHA、method、executor、evidence_kind、detail_owner，再让作者和 reviewer 重新跑。它不是新的业务决定，而是原有事实连接失败。

修订后五条 AC 已齐，引用也存在，但 method 全部在 operator 声明原文后追加各 AC 的具体断言。原始事实位于 /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/runs/run-1793048679201/outputs/plan-claude.plan.json:27、50、73、96、119。当前 VerificationReadiness.java:162 要求完整字符串 equals，复制源码的 usefulSuffixAndWhitespaceStillCannotChangeTheRegisteredMethod 测试也通过。

**必须区分：原 ledger 最后只记录到事件 87 的 context→plan reviewer 路由，没有第二次 plan.decision 失败事件。** “五条 method 均不相等”是文件事实；“按当前规则会被拒绝”是静态预测，不是已发生事故；现实例也可能因版本身份先被拒绝，不能直接拿当前 binary 重放它。具体比较保存在 readiness-comparison.json。

最强反例：9YYF 完成过相同 readiness→执行契约→批准→实施链；当前保守拒绝阻止了不支持的验证声明，保护本身正确。10 月 28 日当前代码已加强“method 原样复制、细节另放”的说明；本次不能宣称这次提示词修改已通过新的真实链验证。

最小改进：在既有 artifact 入界时先做 cheap deterministic coverage/ref/type 检查，把结果交作者修正后再花费 reviewer；权威 method、ref、SHA、owner 由程序从已登记对象装配，模型只选合法引用并写业务验证细节，细节进入已有 validation_plan 等内容字段。不存在的声明仍必须停在原人工门，不能程序猜造。不要靠语义相似放松 equals，不新增第二份批准来源。

决定性检验：同一冻结源码，原 7SNA 两版计划作输入，重建到 EXECUTION compile、批准与 dispatch 的完整 fixture；仅改变装配/校验位置，业务内容、人工批准和拒绝规则保持不变。PASS 是零人工改 JSON、无伪造事实、正确进入允许的实现；对错误 AC/外来 ref/过期声明/改 method 仍拒绝。只证明一个 parser 返回 true 不算完成。

### 2. 接口/运行失败与业务修复曾混用；当前严格安全优先，但低风险恢复仍主要落给操作者

**历史混用已证实；当前缺少精细自动恢复能力已证实；其全局影响比例未知。**

AHBV 实施 run 的 result 为 BLOCKED，两个 Edit 权限被拒、changed_files 为空，却被记录为 SUCCESS_WITH_WARNINGS 并进入自测。自测才认定无实施对象，随后以 SELF_TEST_INCOMPLETE 进入 implementation repair。原始证据：

- /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV/.agent/workstreams/ws-20260828T054556Z-AHBV/runs/run-1787910664384/run.json:119、191、226；
- 同 run 的 outputs/implement-claude.json:1（完整 typed BLOCKED 与无变更）；
- /Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV/.agent/workstreams/ws-20260828T054556Z-AHBV/events.jsonl:71—72、79—85。

事件 79—80 的 operator note 提到 stale JAR 与 edit permission mapping；这证明操作者作出该诊断，不能单独当作已独立证明的底层 bug。更强原始事实是 run 中确实存在当前权限拒绝而仍以 execution success 前进。之后明确 stale-recovery 和两次 retry，最后仍走到 ACCEPT_WITH_NOTES 与 workflow_completed。这说明安全停住、业务 repair 与执行恢复需要各自正确消费事实，否则人会替系统判断“到底该改产品还是修执行环境”。

另一个月份的 G73K：/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260704T172337Z-G73K/.agent/workstreams/ws-20260704T172337Z-G73K/runs/run-1783186346990/run.json:35、183，退出 0 却为 terminal_control_missing_or_invalid；输出 implement-claude.md 末尾实际含 dual-artifact 和 dual-control 文本。可证明“有大段业务产物≠协议被接受”，不能据此断定旧 parser 为何拒绝或实现质量合格。它与当前严格 AgentResultEnvelope 不是同一个协议版本。

当前 AgentExecutionOutcomeResolver.java:25—101 将 blocking signal、timeout、incomplete output、invalid envelope、required artifact failure 分开，但这些失败分支均给 MANUAL_ONLY；WorkflowRuntime.java:456—468 只对 AUTO_ALLOWED 自动 retry。RN9X 的 response 附加字段在一次 correction 后仍被拒，原始 run.json:60、221、257 明确记录 correctionAttempted、unknown field 与 MANUAL_ONLY。当前 parser 已有更细诊断，acquirer 也有有限 presentation normalization 和一次 correction；不能说完全没有鲁棒性。

最强反例：当前 permission denial 只有存在独立 PermissionAssessment 且所有契约满足才能降 warning，已不同于旧 AHBV；当前选定 22 个 outcome resolver 测试通过。对身份/SHA/批准/外部副作用未知停止自动 retry 是必要保护，不能为提高通过率删除。

最小改进：沿现有 typed primaryReason 和 contract 定义分出可证明无危险副作用的格式噪声/会话前置瞬断，保留解析诊断并给明确有界恢复；对 BOUNDARY 或 unknown 外部结果仍保留人工。不能把“所有 failed 都重试两次”当实现，也不能临时豁免必填字段或凭正文猜批准。先测现有 correction 是否已足够；本次不证明增加重试次数一定改善质量。

### 3. “能够执行所需验证”常在长链之后才被人工接上；声明可用不等于执行可达

**具体前置/执行断点已证实；它是否是跨月主因尚不能量化。**

3B2P 在已经完成 intake/contract 后，设计启动才遇到 acpx build-info --json 不支持，typed SESSION_BOOTSTRAP_FAILED 停住；操作者修复 reviewed acpx 环境后 retry 才继续。原始 /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260904T040052Z-3B2P/.agent/workstreams/ws-20260904T040052Z-3B2P/events.jsonl:48—50。Y2KB 则在第一 intake 的 session ensure 超时，/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-seventh-gate-calibrated-20261022/stopped-ledger-snapshot/runs/run-1792682394836/outputs/intake-claude-brainstorm.stderr.log:2，run 的 typed reason 是 session_bootstrap_failed；没有证据说明这个 timeout 是授权边界问题，不能把所有 bootstrap 都归为一类重构。

9YYF 即使业务很小，计划仍先进入 VERIFICATION_READINESS_REQUIRED，人登记三个 operator readiness，再发起计划 revision，重走作者和 reviewer 后才能进 EXECUTION。/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/events.jsonl:76—103。7SNA 首计划 reviewer 同意的验证使用空环境 refs 和提示词作为 feasibility，又一次说明“有验证文字”与“有合法执行出口”不是同一件事。

最强反例：当前 DoctorCommand.java:37—115 已检查 Git、固定 acpx 版本、patch/build identity 和 adapter pin；当前 runtime 启动前还重复检查固定执行权威，避免把过期检查当继续授权。9YYF 的原始 tool observations 有真实测试输出，我复跑交付文件也通过。不能据此声称缺少任何环境检查，也不能把 operator 声明伪装成机器已经执行过测试。

最小改进：把当前任务**必要**的验证出口、实际执行者环境与证据种类，在启动该任务长链之前或最迟计划作者之前明确；统一消费已有 readiness，不另建全面环境能力矩阵。能在宿主执行不证明 native agent 可执行；确需人工验证则必须有已经实现的 prepare/submit/接受出口。启动判定以实际可达性为准。不能要求每个环境都有独立 actual exit，更不能为了容易通过而删掉产品真正要求的证据。

### 4. 现有“完成”和历史 ledger 不能支持一个总体可靠率；这会把改进选错靶

**证据限制已证实；这是评价与决策风险，不是新的生产阻断缺陷。**

P837/HCUM 的 workflow_completed 是 human_next_stage_approval 的 complete，ledger 只有 PR body/commit message；AHBV 的终点是 acceptance approve，样本里没有目标提交收据。9YYF 才明确有 delivery_committed，目标 commit/tree 与候选一致。它们不能作为同一个交付指标的分子。

原始：/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260619T051122Z-P837/.agent/workstreams/ws-20260619T051122Z-P837/events.jsonl:251；/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260628T033237Z-HCUM/.agent/workstreams/ws-20260628T033237Z-HCUM/events.jsonl:139；/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV/.agent/workstreams/ws-20260828T054556Z-AHBV/events.jsonl:160；/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/events.jsonl:144—149。历史无收据不能反推从未交付；当前 delivery 分离与合并后 finalize 是合理设计，不应为统一绿色而合并状态。

当前 9YYF 从 10 月 23 日开始至 26 日 commit，其中 provider run metadata 13 份，durations 合计约 1,929 秒；三天日历时间不能叫三天模型耗时。token metadata 合计 2,077,707 含缓存读取，且部分 provider/model 值缺失/异常，因此不能当作可比金额、有效上下文或模型质量解释。本报告不以此作根因。

最小改进：在同一冻结版本与任务族下，记录“达目标 Git 交付”“必要业务门次数”“机械接线/运行恢复干预次数”“被正确拒绝的无效输入”，从而验证最初产品用途。先从已有事实派生一次只读表，不新增持续观测工程；不以报表工作阻塞修复上述真实接线边界。

## 完整链与写入失败切面

### 成功链：9YYF，真实 provider 的小任务，交付已核实

需求是换行规范化纯函数，三个文件。事件 1—27：intake、产品审查、需求批准、GOAL 编译；43—60：设计审查发现混合换行期望与 stderr 采集问题，作者修正，独立复审通过；76—103：readiness 人工登记、计划 revision/复评、EXECUTION 编译与精确范围批准；107—135：实施、自测、artifact 验证、独立 acceptance ACCEPT；139—144：固定候选、人批准、workflow 完成；145—149：外部应用意图、进程启动、退出、目标观察、commit finalize。

- 交付批准 gate：/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/gates/gate-node-gate-human_acceptance-039.json:1，明确 candidate SHA、只准应用到隔离 target、commit 仍由用户执行。
- merge-plan：/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/delivery/merge-plan.json:1，COMMITTED；base c442fd02…，expected tree 0233afaf…，commit f8ec3d41cbe1cac554a78c3131372af434440691。
- 独立 Git 核实父提交、tree、当前 HEAD、三个文件字节，均一致。没有把 agent 自述当交付收据。
- 原始 self-test 与 acceptance 的 verification-observations.json 包含工具输入输出。材料与批准候选哈希一致。
- 在本评审 delivery-replay 副本运行 11 unittest 与 README doctest，均 exit 0；副本哈希与 commit 内容一致。结果见 delivery-replay-result.json。

这条链证明：设计返修与独立审查可以产生正确结果；typed safety 没有从原理上阻断交付。它也显示：一次成功不等于已证明低成本、低干预或复杂业务通用性。未把 benchmark 人工批准等同产品用户的自然审批成本。

### 三种不同失败/停止链

1. **协议接入失败 RN9X**：intake 初稿→产品 reviewer 要求修订→作者输出与一次 correction→unknown response 字段仍不可解码→typed OUTPUT_INCOMPLETE/MANUAL_ONLY→recovery gate。下一个产品 reviewer 没有被调用。没有证据表明身份/批准被突破，也不应该继续推进来掩盖失败。
2. **运行能力失败后修复的 AHBV**：合法范围批准→Edit 权限拒绝→旧执行结果 warning 前进→自测无对象→business repair→操作者纠正执行策略/重试→protocol failure→再 retry→实现/自测/独立验收→ACCEPT_WITH_NOTES→人工接受。机械环境恢复和产品修复当时串在同一返修链，最终仍完成，不能只取末态做失败标签。
3. **业务意见未收敛的 3B2P**：修复 adapter 环境后，设计在 migration schema/checksum、文件 scope 与已批准决定之间反复漂移→三次人工 revise→revision_limit_reached。这个停止保护预算与既有决定；证据不能证明“应放开 revision 次数”，也不能证明产品其实已合格。多轮不收敛要先检查保留决策上下文，不能自动放宽范围。

### 当前转换表（按源码重建，现实例版本不混用）

| 权威 pre-state / action owner | durable 写入与外部动作顺序 | 下一消费者 / post-state | 关键失败点、重启解释 |
|---|---|---|---|
| active authored node；程序调度 | claim/node execution 提交→固定 prepared/输入与 contract refs→外部 intent→adapter command | typed result acquisition | prepared 前失败不等于 provider 已执行；intent 后未知不能恢复为新启动权 |
| adapter 返回；parser/validator owner | 保存原始输出/typed outcome→固定 publication/completion→本地 completeAndRoute transaction | 下游 node 或 recovery | 有 outcome 但路由未提交可固定字节完成；外部结果未知转人工恢复，不能盲重跑 |
| READY_TO_IMPLEMENT reviewer 结果 | 核对作者/审查 lineage→readiness coverage/refs/事实检验 | EXECUTION compile 或 readiness gate | reviewer 结论保留；gate 不把声明当测试通过；旧/外来事实拒绝 |
| readiness gate；operator owner | immutable readiness artifact + 注册 event + 节点 refs 同一提交 | 作者修订及新独立审查；不授予执行批准 | 写入之间异常由 pending local commit 恢复；读端不观察半条批准 |
| reviewed EXECUTION；human gate owner | 精确 contract/epoch/identity 检查→批准 gate/node/route 一致提交 | 实施 context + 固定 dispatch | 不允许新 binary/旧批准静默借用；错误绑定不能启动 provider |
| self-test/acceptance typed results；verifier owner | 绑定源码/工具观察/契约→typed 决策→repair 或固定候选 | 人 acceptance gate | 当前源码变化/身份错不能以文本 ACCEPT 覆盖；notes 不另改判 |
| accepted fixed candidate +人批准 | target lock→基线/候选核验→apply intent 提交→Git 应用→进程观察→APPLIED 记录 | 外部 commit / finalize | intent 后崩溃不再次应用；先观察、必要显式新授权；目标漂移拒绝 |
| 外部 exact commit；人/受托提交者 | 核对 clean、HEAD/parent/tree 与 accepted candidate→delivery_committed | 已提交的交付 read model | 无进程停止证据不能单靠“恰好有 commit”补造安全结论；重复 finalize 不再 apply |

retry 是执行恢复，revision/repair 是语义改动，abort 不重开终态；旧 ledger 的支持边界必须显式 INVALID，不能为恢复单样本扩大 production 解释。

## 不可绕过检查与成熟度

| 不变量 | Declared by / Proven by | Consumed by / Enforced before | Failure route / Cannot-bypass test | 本次可声明程度 |
|---|---|---|---|---|
| 必需 verification 都有合法出口 | TaskContract + registered readiness；GOAL/epoch/ref/SHA/依赖 | ReviewDecisionHandler 与 ContractCompileHandler；实施批准前 | readiness gate；VerificationReadinessTest 的错误引用/过期/后缀/重复等 8 项 | ENFORCED；有当前离线证明，当前最新 prompt 的真实推进未 PROVEN |
| 失败输出不直接成为产品批准 | typed envelope、artifact contracts、execution evidence | AgentExecutionOutcomeResolver；node success/route 前 | typed recovery；22 个 resolver tests | ENFORCED；不证明所有模型实际错误都可恢复 |
| 本地批准/路由不半写 | LedgerSession pending manifest/commit sequence | WorkflowFileLedger readers/writers | 冷读拒绝、固定字节完成；WorkflowCommitSequenceTest 每写前缀与 changed identity 5 项 | 所选切面 PROVEN；未声称全部写者都已本次穷尽测试 |
| accepted candidate 才能产生目标 Git 副作用 | 候选 SHA+批准+base/tree+过程事实 | AcceptedDeliveryCandidateResolver→DeliveryApplicationService；Git apply 前 | 漂移/未知过程/重复授权/写入崩溃；24 delivery tests + 9YYF Git 实证 | 所选 slice PROVEN |
| terminal 不新执行 | 核心 workflow status 与执行 claim | WorkflowRuntime 与 ledger guards；新执行前 | 不合法命令拒绝；本次源码核对，未专门执行全套 terminal tests | ENFORCED 源码已见，本次不追加“全链已证明” |

四个选定测试类共 59 tests，0 failures/0 errors/0 skipped。命令、源码身份、XML 路径见 verification-result.json；源码副本下执行 mvn -o，没有 provider。它们证明了指定不变量，不证明整体产品可靠，也不替代 7SNA 的完整推进检验。

## 最小改进顺序与不要做的事

1. **先验证机械接线假说**：为 7SNA 同类输入建立完整 fixture，严格区分 reviewer judgement 与程序可执行性，保留现有拒绝。先得到可复现失败与零人工修正目标，再动生产代码。
2. **将程序已知事实移出模型抄写职责**：使用既有 contract/readiness/authority 对象完成确定性绑定，静态错误在付费 review 前反馈。回归覆盖正常链、错误 AC/refs/epoch/method、每个 durable write 失败、冷启动、同边界 sibling variants。不得只改一个 node prompt。
3. **依赖同一事实做有界恢复与必要能力准入**：只自动处理 contract 已定义的安全噪声；真实 BOUNDARY 继续人工；必要验证出口在长链之前确认。尊重现有授权预算，不新增真实 case。
4. **以实际交付与非业务人工干预验收**：同版本、同任务、同环境比较；保持质量与拒绝力不下降。成功 Git commit、必要业务批准、机械修正次数都可由外部记录复核。

不应做：删除 typed/SHA/identity/terminal guard；把 notes 当拒绝；无界重试；以共享长 session 或更多 reviewer 作为先验药方；为当前失败样本永久加兼容解释；以降低验收或扩大权限改善完成率；新造全面平台/能力矩阵；仅追加更长 prompt 后宣称边界闭环；把回归绿色或一次小任务成功推广为所有业务可用。

## 尚不能证明的事项

- 不能给全部历史或当前版本的可靠交付成功率，也不能估算三个主要机制各自占比。缺少统一冻结实验、任务难度、人工动作分类、丢失案例与独立目标分支结果。
- 不能证明 7SNA 修订 method 已经触发第二次生产失败；它是明确静态风险。
- 不能证明模型/provider 选择、session 隔离、token 预算或上下文规模是首要因果。部分模型/usage 身份异常使金额和模型归因更不可靠。
- 不能证明当前 10 月 28 日提示词/接线前向修复已在新的真实合法链中成功；本次只验证冻结副本中的不变量与旧成功交付字节。
- 不能仅凭过去权限拒绝判定当前权限机制仍错误；当前代码已加强 PermissionAssessment 与执行权威。
- 没有核查癌症业务临床正确性、全部旧代码变更或所有外部 PR；本评审讨论 harness 的推进与交付证据，不作领域产品验收。

建议先做上述完整离线检验。若它无法减少机械干预，再考虑模型能力或流程层数；在此之前增加案例、agent 数或治理层，只会同时改变太多变量，难以证明改善原因。

## 复核命令与输入

Maven 的完整命令与实际工作目录在 verification-result.json，输入就是 source-snapshot-sha256.json 所标识的 662 个复制源码文件；选定四个测试类，不是全模块回归。生产目录未构建。

产品复核工作目录：本报告同目录 delivery-replay。输入的三个文件逐字节等于已提交 Git 对象 f8ec3d41cbe1cac554a78c3131372af434440691：

```text
rtk proxy python3 -B -m unittest discover -s tests -v
PYTHONPATH=<本报告绝对目录>/delivery-replay/src rtk proxy python3 -B -m doctest README.md
```

运行是通过评审 Python 进程调用等价 argv；-B 禁止生成字节码，输入/标准输出/标准错误/实际退出码、SHA 及 Git parent/tree 均保存于 delivery-replay-result.json。没有新 provider 行为。
