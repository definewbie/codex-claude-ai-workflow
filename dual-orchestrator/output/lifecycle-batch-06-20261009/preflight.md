# B06 开工核对：获批候选到目标 Git 交付与中断处置

2026-10-09；**PREFLIGHT / NOT IMPLEMENTED**。本轮授权是核对 W06/D09 当前链、缺口和中断出口并形成开工材料；未授权据此自动实施。本批推进 P0 完整研发生命周期的最后交付环节，不扩展 D01 第二族、权限平台、自动 commit/push/PR 或真实 provider。

## 1. 结论与总体目标

交付已有实质接线，不能从零重做。B03 已在 `DeliveryApplicationService` 接入 durable intent、canonical Git common-dir 锁、L07 prepared 写集、实际 Git 观测和确认提交；B05 整轮回归中相关旧测试也通过。当前差的是这些能力在完整合法流程、外部不明窗口和投影上的闭合，不是再造交付平台。

下一工作包必须一起解决：

1. 获批候选→精确目标→应用→人工实际 commit→finalize 的完整正向序列，且不能误拒已有合法 APPLIED/COMMITTED。
2. 各 L07 写点与 Git 副作用前/间/后中断，冷启动只补本地或重新观察，不重复 apply/commit。
3. 不明且目标仍是 base 时，明确人工裁定与新操作的生产入口、记录及消费；不能只留下“需裁定”文案。
4. 目标/候选/原批准/当前观察的强制核对以及 CLI/API/两 UI 的当前动作同源；历史完成不能混成今日目标资格。

第 3 项的具体入口是未定设计接线。D09 已批准“旧操作停止＋目标有效＋明确新操作”的结果义务，但没有冻结可执行 payload/入口。应先补这个具体协议，与其余本批范围合并做受影响评审；不临场发明默认 retry，不强行套用 active-node 的 D07 裁定器。不是新增用户产品取舍；若方案需要新 authority、长期监督服务或改变终态/commit 权限，再按既定规则交用户。

贡献：补齐“研发流程显示 COMPLETED”到“批准的成果确实交付到目标 Git”的差距。首版优先覆盖正常交付及明确的合法恢复，未测场景如实保留；不把“80% 主流程优先”当已有成功率数字或降低冻结验收的授权。

## 2. 边界与事实 owner

| 对象/边界 | 当前 SoT / owner | 不能推导的事实 |
|---|---|---|
| Definition | 固定 workflow 与 D09 交付规范 | workflow COMPLETED 不等于目标已应用/提交 |
| Instance / 批准 | 最后一次 human_acceptance occurrence 的 node/gate/route event、subject candidate ref/SHA；AcceptedDeliveryCandidateResolver | 旧批准、可覆盖摘要和模型正文不授权当前 candidate |
| Artifact | occurrence 固定 candidate/patch/commit-message/PR body；DeliveryCandidateWriter | 相同 expected tree 不证明谁批准或谁执行 |
| Execution / 外部操作 | DeliveryMergePlan.operation 的 id、intent、before/observation、退出/输出事实；DeliveryApplicationService + Git adapter | 父 JVM PID 消失、锁可取得、日志缺失、目标仍 base，不单独证明子 Git 已停或从未运行 |
| 本地耐久状态 | merge-plan + delivery event + current commitSequence，经原 L07 LedgerSession | prepared 固定补齐不赋新的 Git 调用资格 |
| 目标 Git | canonical repo/common-dir、实际 HEAD/parent/tree/index/worktree/untracked；GitPatchMergeService | 历史 APPLIED/COMMITTED 不能替代当前观察，也不能证明用户没有并发修改 |
| Projection | DeliveryApplicationService.View 为业务资格读模型；各端只消费 | 状态标签或提示文案不能生成许可/裁定事实 |

不新增领域 model 层。优先沿现有 DeliveryMergePlan.operation、原事件、原 service 和原锁承载必要增量。目标锁顺序维持 target common-dir→L；不能在持有 L 的 query 回调内反向加 target 锁。

## 3. 当前代码与测试核实

源码锚点及内容身份见 [source-checks.json](source-checks.json)。区分静态机制存在与运行证明：

- `applyBound:105–172`：先 target 锁，再 L07 intent；完整 commit 返回后才 `git.apply`。旧 intent 遇到精确 applied 形态可补 APPLIED，不匹配即零二次 apply。
- `finalizeBound:181–217`：target 锁下实际 clean HEAD/单 parent/expected tree 校验，L07 同一 operation/批准重核，确认 COMMITTED；不创建 commit。
- `savePlan:222–226` + `LedgerSession:162–195,380–398`：merge-plan/兼容产物/event/current 在原 prepared mutation 内；Git 不在 replay 写集中。
- `GitPatchMergeService:20–61`：临时 index capture，零业务变化明确拒绝。`:64–73` 用真实 `git apply --index`。`:105–120` common-dir 及 HEAD/index/worktree/untracked 观测。
- `AcceptedDeliveryCandidateResolver:41–103,106–148`：最后 acceptance occurrence、严格 producer/iteration/ref/SHA/patch 绑定，不回退旧 approved occurrence。
- CLI merge/finalize 与 API delivery/merge、delivery/finalize 都调用同一 service；status/summary/API/console/mgt 消费其 view。server 仍额外按 status 自己生成下一步文案，UNKNOWN 落为“刷新”。

现有 B05 证据仅引用，不重跑、不冒称 B06 验证：DeliveryApplicationServiceTest 9 项证明实际临时 Git 正常 apply/commit、源漂移、错误 tree、旧/错 candidate、最新 revise、排除路径边界。其 fixture 人工写入 completed/gate/audit，不证明真实研发/独立验收→实际 Git 的连续链。WorkflowFirstLifecycleSequenceTest 真实程序批准四变体×inline/worker，但止于 workflow 终态，没有调用目标 Git 交付。两者不能拼成已经证完的整链。

相关八个旧 suite 的原始 B05 XML 与 SHA 见 [prior-test-receipts.json](prior-test-receipts.json)。本轮零测试执行，以下缺口属源码/断言核对，不声称已有新红灯或运行反证。

## 4. 缺口和必要工作

| ID | 当前证据与影响 | 所需工作 / 成熟度 |
|---|---|---|
| G01 合法裁定入口未接通 | apply:120–130 对已有 intent、目标 base/其他不匹配状态始终零二次 apply；operation 没有被消费的裁定。通用 ExecutionRecoveryAdjudication 要求当前 active node/execution/AgentRun preparation，不能处理终态后独立 delivery operation | 先指定交付 owner 的具名输入、持久化、幂等、旧操作停止/目标基准核验和新 operation 消费；只补既有 D09 义务。当前 DESIGN ENTRY GAP，不能把 operator 可手工改文件当合法程序入口 |
| G02 操作前目标核验有空隙 | service:133–151 核 before 后写 intent；adapter:64–73 发送前只验 repo/clean，不再对比批准 base HEAD/tree。同样干净但 HEAD 已变的外部变化没有该处具名 guard | 在原 Git 副作用入口核定 candidate/operation 对应基准；以固定 barrier 的干净 HEAD 漂移反例验证，不承诺锁住任意外部程序或 OS 原子隔离 |
| G03 观测采纳与执行活动边界 | service:123–127 冷读可按 tree 补 APPLIED；记录 executor_pid/start 但该分支不消费停止事实。target lock 只协调 Dual JVM，不证明异常退出留下的 Git 子进程停止 | 接入既有“活动明确结束才采纳可续作”的证明/失败处置。父 PID、锁释放不冒充全停；无法证明时保留 UNKNOWN。具体事实生产和消费也并入 G01 协议，不新建监督平台 |
| G04 plan identity 核验不完整 | requirePlanCandidate:279–292 校 candidate ref/SHA/expectedTree/批准；requireSameOperation:229–234 校 id/candidateSHA。未把 plan 的 target/baseCommit/baseTree/workstream/source 逐项与权威 workstream/candidate/固定 operation 核对；finalize 使用 plan.targetRepo/baseCommit | 用现有 owner 执行完整绑定/版本/结构拒绝；不能从宽松 operation Map 或改写 plan 转移目标/父提交。范围是合法意外漂移和错误身份拒绝，不恢复全目录防恶意篡改承诺 |
| G05 当前读模型与动作不足 | viewSnapshot:67–78 有 plan 时仅重核 candidate 后直接 fromPlan；fromPlan:333–339 仅 status 控制动作。UNKNOWN 实际正确 commit 可被 finalize 验证，但 view 不提供该合法动作；APPLIED 后 target 漂移仍可提示提交；UNKNOWN server 默认刷新 | 业务 owner 提供当前观测/历史状态、原因、合法动作与不能动作；当前动作需强制读模型校验，历史完成不抹掉。各端不自行推导；明确 UNKNOWN 的精确 commit 确认可达、不能建议重 apply |
| G06 逐窗与整链证明不足 | 已读 test 和搜索未找到 delivery-specific apply intent/target barrier/冷 JVM/逐 L07 前缀测试；共享 D06 prefix 并非 Git 不重发证明 | 正向、拒绝、failpoint、冷进程、共享 target、投影和删除式反证按 §6 联合完成，不以测试数收口 |
| G07 文档/实际输出有漂移 | reference.md:1290–1295 仍列 apply.stdout/stderr.log；当前 apply:156–160 固定 stdout/stderr 在 operation，未写这两路径；Frozen 文本描述四状态，当前已有 UNKNOWN 第五枚举 | 文档说明实际承载位置及 UNKNOWN 已有语义，不为补文档恢复第二份执行 SoT；旧字段/状态声明按历史标记，准入使用当前数据。status enum 已在 B03 接线，不另造状态机 |

其中 G01/G03 的具体生产入口必须在动代码前说清；其余是既有 D09 对照下的 guard/投影/证明工作，不需要另起平台设计。未核验更多 source-ref 链条之处保留为 §8，不凭预检宣称所有 delivery 安全性。

## 5. 完整 transition 与失败切面

L07 每次 commit 都展开为 before_manifest→after_manifest→各 replacement→event append→current sequence→pending 删除。下表写“补齐”仅指同 frozenRuntimeIdentity 下恢复固定本地字节；未知 binary/格式 INVALID，不支持旧样本救援。

| pre-state / action / owner | durable write / event / external 顺序、post-state / consumer | 中断、resume/retry/abort/replay/冷启动解释 |
|---|---|---|
| 实施及独立验收完成 / delivery.prepare / 原 handler | immutable candidate+材料→producer completion→人工 acceptance 绑定 exact candidate→workflow completion audit | candidate 发布失败不进入最终批准；孤立材料不授权。兼容摘要失败从同 candidate 重建，不代换 authority；不借旧 ACCEPT/GOAL |
| 无合法最终批准 / inspect 或 apply / delivery | 读取最后 occurrence+candidate SHA/producer+completion；缺项零目标 Git 应用 | COMPLETED 单标签、旧批准/改写 candidate 拒绝；不产生 intent；普通 retry 不扩大权限 |
| 候选齐备、无 intent / 人 merge / delivery | 确认 canonical target→target lock→L→当前批准/源捕获/目标基准→兼容产物+intent+event+current 同次 L07→完整提交返回→发送一次 Git apply | manifest 前失败零 Git；任何部分 intent 补齐后仍 UNKNOWN/零发送，不能把 replay 当原 live commit 返回；响应不明先查目标 |
| intent 已提交 / 原 live 调用 / Git adapter | 副作用前再核目标基准→git apply --index→取得真实退出/输出→实际 target 观察 | 发送前/中/退出前异常不从父 PID 或 base tree 推断未执行；保留 intent/UNKNOWN。停止不了旧调用不得并行发新 apply |
| Git 返回、应用精确 / delivery | 同 operation 的结果/observation→L07 plan APPLIED+event+current→view | 观察/本地写失败不解释成未应用；Git 已成功而 ledger 未记时只恢复相同观测/本地，不二次 apply。记录日志失败属于 I/O，不修改执行事实 |
| 旧 intent / 明确旧操作结束且目标=base HEAD+expected index+worktree一致+无额外untracked / inspect/reconcile | 身份重核→L07 补同 operation APPLIED→提供人工 commit 动作 | 只看 index 不够；操作活动不明继续 UNKNOWN。普通 merge 只能重核、不能再应用。G03 当前未完整接入 |
| 旧 intent / 目标仍 base 或形态不明 / operator | 明确旧操作停止证据+当前目标/批准核验→具名裁定持久化→只在合格裁定下新 operation→原 live apply | 当前 G01 入口未指定；不能删除 merge-plan、重开 workflow、改 FAILED 或自动重试。新裁定不把旧事实抹掉；过期/错 operation/并发/重复 request 必须明确拒绝或同内容幂等 |
| APPLIED、无实际 commit / 人审 target diff | 只读精确 staged/worktree观察→人工在目标 repo 创建 commit（外部操作） | APPLIED 是合法待提交，不是损坏；Dual 不获得自动 commit 权。人工拒绝/暂缓不启动新 node；改动未知保留现场，不 reset/clean |
| 人工 commit 已发生、未 finalize / 人给 SHA / delivery | target lock→actual HEAD/clean/单parent/base/expectedTree→L 原批准+同 operation重核→COMMITTED+event+current L07 | commit 非由 Dual 创建；中断只补同 commit 本地记录，零 apply/commit；精确 commit 允许从 UNKNOWN 补确认，当前投影须补可达性；错误 parent/tree/多parent/非HEAD/dirty拒绝 |
| COMMITTED、响应丢失 / 重复 finalize | 重核原 candidate/commit和当前资格；同 SHA返回同结果 | 不再生成新 commit；target 后续演进保留历史交付，同时明确今日不等同仍是该 HEAD；不能抹历史或授新的 merge |
| target 锁忙/L锁忙/pending/版本不符 | 当前动作不提供资格；相同身份下仅本地补齐；UNKNOWN人工 | target→L 顺序，锁忙不变 FAILED；任何入口不能从局部 plan 看到跨代 success。terminal workflow 永不重开；旧数据不自动升级 |

这张表是实施 oracle，不是这些路径均已通过的声明。程序内部明确 abort/撤销对 delivery operation 的具体承载也随 G01 核对；不拿 workflow active-node abort 改终态。

## 6. 验证设计：用户合法流程与不能绕过

所有新验证仅在自建临时 repo/worktree/ledger 运行；测试中的 base/交付 commit 不等于提交本项目或真实目标。无 provider/原生客户端预算需求。合法与拒绝同等：

| 组 | 用户流程正向 | 拒绝/中断/反证 |
|---|---|---|
| V01 完整正常交付 | 从现有真实程序初始化、作者/自测/独立验收、ReviewGateActions 最终批准到 target apply、测试扮演 operator 实际 commit、finalize；两执行入口/四变体复用 shared harness | 未批准/最新 revise/错 candidate/错工作区/错目标零 apply；不手写 COMMITTED/audit 当整链正向 |
| V02 apply intent 写集 | 正常固定意图后一次真实 Git，APPLIED 后合法等待人工提交 | 各 intent prefix failpoint+真正冷子 JVM：reader一致或明确pending；补完整intent零Git发送；删 durable intent/把补齐当 fresh 必被测试抓住 |
| V03 外部窗口/观测 | 精确已应用且旧操作结束→补 APPLIED；实际 commit已发生→直接确认该SHA；同内容 finalize幂等 | Git前/中/后、输出/观测前、APPLIED/finalize各写点；target仍base、worktree脏、额外untracked、wrong HEAD/parent/tree、不明/活跃子Git零自动重发；不从父进程死推全停 |
| V04 显式裁定 | 旧 operation 身份与停止/目标事实齐备，人明确选择新操作；新操作一次应用，不重开 workflow | 错/旧/跨workstream/跨candidate裁定、同request异内容、未停、漂移、冷重放零新发送；同request同内容不创造第二许可；具体协议冻结后才可实现用例 |
| V05 目标身份与并发 | 两个workstream同target、真实common-dir别名/linked worktree路由；合法目标不被误拒 | barrier固定 intent后、Git前：外部改成干净不同HEAD仍拒；target锁busy，L07失败后释放；身份字段改写拒绝；不扩大成对所有外部程序强隔离 |
| V06 同源投影 | 无intent READY、APPLIED待提交、精确已commit待finalize、历史COMMITTED，两具体实例由相同业务view驱动CLI/API/console/mgt | UNKNOWN/活动未明/target漂移/BUSY/pending无危险动作；各端不填默认retry；共享单元/DOM检查不冒充浏览器视觉验收 |
| V07 主回归与证据 | 本批定点先红后绿后，内容稳定单轮全模块回归、原始XML、source SHA、独立批末判断 | 不拼B05旧绿灯、合成机制与真实临时Git分列；先红仅限真正抓到生产缺陷，不把作者fixture错误报反证 |

测试数量不设目标。对已有充分证据不重复跑笛卡尔积；组合范围以实际控制链/差异为依据，不能缩到单方法断言。kernel I/O 和 D01 时钟 LOW 不因本批与 L07 有关就顺手扩大范围。

## 7. 候选文件与成本止损

拟议改动对账见 [scope-candidates.json](scope-candidates.json)，是语义工作包候选，不是本轮修改授权；实际修改前再次固定 dirty 内容并记录采纳/调整/不改理由。

- 主 owner：DeliveryApplicationService、AcceptedDeliveryCandidateResolver、DeliveryMergePlan/Writer 和 GitPatchMergeService；复用原 L07/target lock。
- G01 入口：既有 delivery CLI/API facade；新增必要 endpoint/参数必须携具名请求，先在具体协议中固定；不把整个 D07 active-node恢复平台搬进来。
- sibling：status/summary、DeliveryViewService、WorkstreamQueryService、两 UI、reference/中文quickstart相关说明与原 delivery tests。仅实际受影响部分迁移，不能遗漏同一动作在另一端的入口。

成本判断是静态判断，无工期/文件数节省承诺。普通正常链已有代码，因此预计主要成本在明确一次外部操作的恢复/裁定与证明；此预测尚未通过实施验证。不能称“两行guard”或因已花成本而硬做。

停止条件：需新增自动 commit/push/PR、重新打开 terminal workflow、新权限/监督/存储平台、任意后台进程全控制，或裁定机制成本显著偏离直接主流程收益，停止并提交具体证据/替代方案；不把全拒绝当成功，也不偷删 D09 义务。旧样本/旧binary恢复不做。

## 8. 六维、自查和未核验范围

| 维度 | 当前事实 / 本批待证明 |
|---|---|
| Declared by | R3 I6、详设 §9、冻结附录 §9、主清单 W06；全部仍有效 |
| Proven by | 旧 B05 临时Git正常/拒绝测试与 B03 本地协议；不能替代 V01–V07，新用例 NOT RUN |
| Consumed by | delivery service→原Git/plan/L07→统一view；G01裁定无消费者，具体接线待定 |
| Enforced before | 当前 intent commit完整返回先于Git；缺批准/候选拒绝已存在；目标最新基准/旧操作停止/完整plan身份仍需强制 |
| Failure route | 原 UNKNOWN零重复apply、本地prepared补齐；G01合法出口与当前投影尚不完整 |
| Cannot-bypass test | 旧 candidate/ref拒绝已证；V01–V07新增整链/前缀/冷启/竞态与删除反证尚未运行 |

作者材料自查：**WARN / 可供范围与入口决定评审，非实施完成或准入 PASS**。Analyze：重建了现有 SoT/实际写序；Modify：仅材料/主清单；Review：暴露 G01–G07并保持区别；Reflect：B03 完成本地会话不等于外部操作恢复闭合，旧单层fixture＋workflow终态不能拼成真实连续交付；Analyze：下一步补G01/G03具体协议后才能进入B06实施。

未核验：本轮未运行测试/故障注入/临时Git实验；未逐项证明 Git 外部活动静默、子进程停止及各观察命令一致性；未深入 DeliveryCandidateWriter/CompletionEvidenceLineageResolver 的所有历史分支或所有旧XML断言；两 UI 无浏览器运行；全部定义/adapter真实E2E尚未运行。任何所列缺口由静态源码和测试正文支撑，不等于已有运行FAIL。

## 9. 下一步动作

先补**交付未知结果裁定的具体协议**：旧操作身份/停止证据、当前目标事实、人的决定、原批准重核、本地写序/一次消费、新操作与回执未知、CLI/API合法入口。与 G02/G04/G05 的核验/投影接线合并冻结受影响范围后，给 CC 一次合并复审；不为每个补记单开轮次。

协议通过且实施范围获授权后，沿上述完整工作包实施与离线验证，再交 CC 批末评审。不得从本预检 WARN 自行继续堆代码。真实 NO-GO、所有总体 W 项和提交权限不变。

## 10. 接线候选后续材料（2026-10-09）

上文保留开工核对时的状态、G01–G07 现状与 NOT RUN 口径。用户随后授权补具体接线，本次形成 [wiring-candidate/protocol.md](wiring-candidate/protocol.md)，含 resolve 的两项决定、程序/人工停止依据、同 L07 的一次新 operation、标准 apply lifecycle 顺序、当前 view 与锁顺序、全部失败前缀及原入口合法续作。字段例子、源码锚点、条件范围、作者自查/文档增量另存同目录。

当前更新为 **设计候选待合并 CC 复审**，不把 G01 的消费者或新强制点称为已实现/已证明。尤其人工停止确认的信任边界需独立判断，观察准确主进程结束不等于测得退出码/任意子树全停。原 preflight manifest 作为历史凭证保留，当前材料使用新 manifest；源码 653 项身份继续核对，产品/测试未修改。

下一步：协议+原预检合并一次受影响范围设计复审；通过且实施获授权后推进 B06 W06 全链实现/离线验证及批末独立判断。无测试/Git实验/原生或 provider 调用、提交；真实 NO-GO 保持。

## 11. 合并设计复审接收（2026-10-09）

用户带回 CC 设计层 PASS、可冻结；原文及两 LOW 处置见 [review-acceptance-20261009/disposition.md](review-acceptance-20261009/disposition.md)。当前协议+冻结补记为 DESIGN_FROZEN，B06 NOT IMPLEMENTED、等待实施授权。前文预检/候选及旧凭证保留其历史身份；不把 G01–G07 的新增消费者、guard 或 V01–V07 称为运行已证。

LOW-1 补明 commit 与停止依据独立；LOW-2 规范序列化成对反证列为 B06 常驻必测义务，尚未执行。不另开材料轮次，不授权提交或真实运行。下一步是获用户授权后实施完整 W06/离线验证→单轮稳定回归→CC 批末判断；不能从本设计 PASS 自动开工。
