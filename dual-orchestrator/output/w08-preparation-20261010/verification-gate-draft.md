# W09 首个 fresh 主流程 Verification Gate 草案

2026-10-10 修订；**DRAFT / AWAITING_AFFECTED_REVIEW_AND_G05 / NOT AUTHORIZED / NOT RUN**。本页不授权任何provider调用、安装、目标Git修改或自动人批准。前提缺项必须实际补齐；草案通过评审也不能当执行记录。

## Verification Gate

- **Target**：一个全新、本地标准库Python任务，在明确GOAL与EXECUTION批准、Claude客户端作者/Codex客户端独立复评、真实测试证据和最后用户批准之后，指定candidate被应用到新目标，人工实际commit被Dual确认COMMITTED。每一步均消费当前批准与证据；只证明此冻结组合的这一合法主流程，不承诺80%成功率或全变体真实能力。
- **Source of truth**：原definition/schema/runtime identity；GOAL/EXECUTION合同与gate/node occurrence及ref/SHA；R01/R02/R03、verification-observations、唯一execution outcome与有效sidecar；source baseline及completion/accepted candidate；原delivery operation/events/plan加实际Git HEAD/parent/tree/index/clean；run.json里的usage/providerModelIdentity。UI/summary和模型正文只作投影/语义证据，不产生许可。
- **Trigger precondition**：本轮G01修正已通过当轮单轮回归及受影响评审；G02实际受审acpx元数据/接口及显式runner配置已核、最终workspace中重新核路径与配置；G03每条验收已有方法和已实现补齐/消费出口；开跑前核宿主必需条件，真实readiness引用在新GOAL后的原等待门登记，不提前虚构；G04新发行JAR/definition/templates/runtime行为输入与本轮凭证一致；G05用户批准新任务、隔离target、预算及各道人门责任。新workspace/session/workstream，target唯一初始base；没有其他writer/旧活执行；没有旧样本、未知pending或隐式resume。
- **How precondition is checked before run**：完成下表P01–P08的开跑前部分并写实际结果/ref/SHA；开跑前必需项未齐即不启动。P06的当前occurrence/GOAL绑定须在其真实产生后的EXECUTION批准前完成，不能先造ledger或假引用。元数据读取与业务provider分别计数，不把版本/doctor当读写/测试能力实证。不能只凭“本机CLI能进入”推断ACP路径与GLM连接可用。
- **PASS**：全部前提有效；批准谱系无漂移；作者实现/实际自测/另一客户端独立验收逐条满足批准要求；最后人批准绑定当前candidate；delivery APPLIED后人实际commit、finalize精确核对COMMITTED；原失败/警告/缺证据无伪覆盖，cost观测如实；总预算/停止纪律符合。自然合法修订或补证据可在同预算内走原流程，不必须一次直通。
- **FAIL**：有效组合中产品验收不满足、权限决定错关联/重复发送、未批准调用/交付、必需证据缺失被采纳、scope违规被当合法、失败归因被改写、终态新调用、真实主流程死端或止损触发。合法revise/BLOCKED本身不是FAIL，只有无合法出口/无法在本预算内完成等结果才分类。
- **INVALID**：使用旧JAR/旧server/不同冻结输入、错误执行路径/配置、fresh不成立、必需观测链装配错误、前提未实际通过、执行者手改ledger/harness或运行中改需求/合同以凑成功。配置可用性错误在业务前发现即记录前置INVALID、零业务调用；不说部分成功。运行中外部结果未知保留UNKNOWN，不从缺日志推出零执行，最终Gate分类与执行状态分列。
- **Stop-loss**：预算表任一触发、同根因无新证据重复两次、scope/批准/证据身份漂移、部分耐久状态被误读、原生回复结果不明/旧执行停止不明、需要新authority/扩大目标/引擎安全研究时停止新dispatch。先记录原因，再关闭新许可并核原执行；不能靠重跑完整流程弥补前提失败，不能删除plan/ledger、reset/clean或另启动重叠attempt。
- **Run artifact freshness**：不是HEAD单值。冻结当前源码/资源/schema/template/config与发行JAR SHA、acpx选定入口/实际build-info、选定kernel及启动后实际code-source/loader；启动只使用本次绝对JAR路径，新process/session/run；`start`不带`--run`，先核实际runtime身份再分段run。任何实质变化使旧冻结失效；已批准兼容范围中的客户端版本变化不自动判不兼容，但实际握手/协议/行为要核实并记录，不承诺未来升级全兼容。

## 1. 前提核验表（现在均不可冒填PASS）

| ID | 运行前的实际核验 / owner | 当前状态 |
|---|---|---|
| P01 需求/批准 | 用户批准 fresh-requirement-candidate 或替换；GOAL不锁精确文件，实现前批准实际EXECUTION。最终交付必须用户或具名授权者决定；不借旧Gate代理权限 | PENDING_USER；本候选无authority |
| P02 提示分期 | 真实qualified GOAL设计/计划/复评及EXECUTION实现渲染成对，intake/共享字段也已纠正；严格消费者未改 | 修正及离线验证见本轮凭证；PENDING_AFFECTED_REVIEW |
| P03 发行/拓扑 | 修正后必要测试/package、CLI workflow validate；记录实际JAR/definition/template SHA。显式 `DUAL_FLOW_KERNEL=liteflow`，单INLINE runner/单JVM路由/单ClassLoader；读侧不成为第二执行者 | 本轮发行工件见 artifact-verification.json / release-identity.json；受影响评审待决，最终启动时再核实际身份 |
| P04 native装配 | 显式 `DUAL_ACPX_EXECUTABLE` 为受审本仓wrapper，核实际 `build-info --json` 与 config show；使读取结果匹配当前patch/协议。CLI参数/help核原permission-replies/reply支持。Claude使用当前环境的GLM或用户配置的后端，provider/model实际来源分别记录；Codex采用有效配置，native版本不是领域路由条件 | 实际本仓wrapper的 build-info/config/help 已核，patch=1517f828，两个 permission-reply 入口存在。配置默认approve-all不作运行授权，Dual按具名profile显式覆盖；最终workspace复核。真实ACP握手/模型连接未运行 |
| P05 桥接/停止 | 同一runner显式 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=600`，transport符合资格的正常ACP角色选择onlinePermissions，contract_discovery的simple Claude路径不启桥接，correction保持deny-all。核native实际offered once请求与原session/generation身份，若出现ask保留C/X/ACK/后续结果；查原cancel/close及PID/start核对方法。没有ask记NOT_EXERCISED，不故意借fetch/网络制造请求 | 源码/本地peers已有，实际客户端NOT_PROVEN。正常未ask可完成主流程，但不得宣称桥接真实行为全已证 |
| P06 必需环境/证据 | 每个criterion落入EXECUTION verification_bindings，Python/实际测试报告/源码读取所需env和authority有可核来源。operator可用性声明仅证明声明；若选operator材料出口，核原readiness record与事后prepare/submit实际可达。native缺退出码时按批准条款处理，不能临时降级 | 宿主Python/stdlib已实测；逐criterion方法/绑定/原record-readiness生产出口已列 verification-exits.md。真实工具能力未证，当前GOAL/ref/epoch在新运行内产生后登记；没有引用缺项开EXECUTION。无通用浏览器/DB/公网前提 |
| P07 target/fresh | 经批准后才创建独立新target与effective worktree；记录canonical repo/common-dir/base HEAD/tree/clean。只检查这个任务的target，不要求清理当前Dual dirty源码。新ID与nonce、无旧未知执行或原生session借权 | 目标提案：/private/tmp/dual-w09-newlines-20261010/target；effective workspace：同父目录/worktree；branch=codex/w09-newlines-20261010。尚未创建，须用户批准并核路径新鲜 |
| P08 观测/限额 | 填以下明确预算；每次dispatch前读原node/execution/session、调用数、elapsed及已完成实际usage，运行中30s检查已知进展。金额不可折算标token_only/subscription_unpriced/pricing_unknown，未知token不填0。启动前确定谁能执行停止与关键人批准 | PROPOSED_BUDGET，不是已授权额度 |

必要的客户端连接前置若无法由纯metadata证实，允许在**批准后的同一总预算**内最多两个独立最小读取前缀（Claude一个/Codex一个），记录新nonce、工具观察和provider状态；读失败即止，不重复安排底层权限/委派/内核实验。不要求它们先证明所有产品行为；真实测试能力在首个实际必要消费点检查，能力缺失走已核出口或停止。

## 2. 预算提案与真实限制

| 项 | 候选上限/观测方式 | 触发处置 |
|---|---|---|
| 样本 | 1个fresh任务，1个terminal-delivery变体，INLINE；正常9个agent节点（无需handoff）。先跑主流程，worker/其他变体不安排同批真实运行 | 不自动开第二case或扩大矩阵 |
| 顶层调用 | 最多18次：最多2次连接读取前缀，加正常9次；每节点至多一次已有格式correction且必须在剩余池内。若两次连接前缀均使用，则只剩7次额外调用容量，不能保证9个节点都可correction。本候选若先产生缺readiness的计划，再按 verification-exits.md 登记并revise，计划作者/复评重跑通常另用2次；两次连接前缀也使用时，9+2+2后只余5次。所有实际额外修订/恢复调用也占同一个18次池，不另加隐藏额度 | dispatch前计算余额；本次可能包含correction时预留2次，否则不启动。native内部重试/子任务数不是可保证的顶层总量，保留实际可见计数与未知，不宣称18是全部内部调用绝对硬限 |
| 时间 | 每次原生命令600s有限预算；累计模型/工具运行窗口60min，人工业务审批停顿另记、不当模型时间。原生ask人工回复目标≤180s且必须仍在真实request有效期内，不能伪延长请求 | 30s观测，临近总窗不再dispatch；超窗关闭许可并核停止。单次timeout是现有机制，总窗为operator监测，不能冒称已实现平台级抢占/全部子任务物理停止 |
| token | 可见实际累计token 160000为止损线，120000预警；run.json的实际usage与source分别记录，不使用artifact_estimate当计费事实，不用顶层冒充内部总量 | 达线停止新dispatch；首个已完成调用仍无实际usage，保留unavailable原因并停下核观测链，不能无界跑后续。该线不是原生内部绝对硬限，未知超支风险须用户在批准预算时知悉 |
| 金额 | 本轮不报价或换算美元。GLM/订阅等按实际billing mode记状态；价格未知不填0 | 若改为需额外购买/付费额度或成本偏差，先停交用户；不能引用历史估价当账单 |
| 修订/外部恢复 | 最多2个有新证据的语义修订循环，仍占上述预算；外部UNKNOWN不自动retry | 无新证据同类两次即止；未知且旧执行未停止禁止新attempt |

上述数字是首个小任务的ROI提案，**未经用户批准**。如果实际不可观察内部总量，不能给绝对成本保证；也不为保证这些提案新建监督/预算平台。若用户不接受此观测边界，先修改Gate范围/预算，不启动。

## 3. 实际执行顺序与证据索引

1. CC受影响范围评审与P01–P08开跑前部分核齐；用户批准任务/目标/预算和人门责任。留完整冻结清单，不从旧输出目录启动。
2. 创建一个新target/effective worktree，`start`不自动run；保存新runtime/definition身份。每段`run --max-steps 1`，每段前后从原status/trace/runs/summary核状态与预算；禁止autorun/批量auto-approve。
3. GOAL、EXECUTION与最后交付门分别展示实际材料给批准者；计划缺readiness时先依 verification-exits.md 在原等待门登记真实引用、作者修订/独立复评，再编译/批准EXECUTION；revise走原作者→复评→新门。原生permission决定仅原offered once、不提升业务scope；批准保存/提交/ACK/实际工具执行分开记录。
4. 完成真实自测/独立验收和最终candidate批准；先保存产品源码/有效证据基准。delivery merge实际应用后核APPLIED；由用户或此次明确授权的operator实际commit新target；finalize核COMMITTED。Gate批准不等于自动获准替用户commit，必须在执行授权中具名。
5. 输出可复核的运行清单：每个occurrence/run、actor客户端与实际provider/model/billing/usage状态、gate决定/干预、R01/R03和产物ref+SHA、source baseline、candidate/批准、delivery operation/start/stop/exit/target观察/最终commit。原events条目按event id与条目身份引用；终局快照文件SHA仅作本次归档，不用于在运行中比较整个可追加event文件。

复用已有CLI/API/read model输出及原账本；本轮不预称已有一键导出器，也不新增长期运行清单平台。人工整理的索引只定位原authoritative材料，不补造任何程序事实/批准。

停止顺序：先停止新调度和原permission新提交（原permissions cancel只说明关闭许可）；对实际当前session使用原cancel/close通道并核PID/start与结果，不推断子树全停。失联/不明留UNKNOWN，已停止且有具名决定才允许以后新attempt。本Gate一旦需要超预算或新的副作用，停止并报告，不能换工具/模式绕过拒绝或默认重发。只read status不等于已停止。

## 4. 明确未证明的范围

一次CLI/INLINE fresh成功不会证明全部workflow/WORKER真实能力、浏览器视觉、所有中断窗口、默认部署跨ClassLoader隔离、内核I/O故障专项、全部诊断族、成本下降或真实原生permission必已行使。自然未触发ask、拒绝、返修、UNKNOWN等标NOT_EXERCISED，保留相应离线回归，不将其填PASS。

当前下一步是合并评审本轮G01修正及Gate材料，随后向用户提交G05任务/目标/预算与批准责任；**本页仍无真实运行/预算授权。不能用准备批次的批准替代G05。**

## 5. 当次授权提案与冻结说明

- 需求：本目录 task-requirement.md 的 AC-1–AC-3（候选解释见 fresh-requirement-candidate.md）；具体文件操作只在首次实现前由EXECUTION批准。
- 目标：新建临时独立Git repo + linked effective worktree（以上路径/分支），保留用户原项目脏工作区。用户还需批准新目标初始化/初始提交；本轮未创建。
- 预算：维持18顶层调用/60分钟主动运行/160000可见实际token候选上限，不增额度；正向主链9个agent节点（修正旧草案误写8），未可见的内部调用和总token不保证绝对上限。
- 关键及最终门：建议用户本人决定GOAL、EXECUTION及交付；原生once许可也由用户逐请求决定。执行者可提交材料、分段运行和停止，不能自批。若授权代理须具名，不能沿用历史代理权限。
- 实际commit：建议由用户在新target人工执行；Gate执行者只apply/finalize，不默认代提交。初始fixture提交与最后产品提交的许可需分别明确。
- 环境：见 gate-launch-env.sh（仅保存未执行）；三项显式选择均进入内容身份，其他既有设置保持不变并在启动时记录；不因本Gate改变默认策略或宣称P1/P2收益。本轮核 metadata 时只对子进程设置前3项，不改用户持久shell环境。
- release-identity.json 固定本轮候选发行，不承诺启动后loader/PID已知；Gate start 后核原runtime身份。任何代码/配置/模板变化重核相关结论。

计数修正和 readiness 时序澄清是本轮案头自查发现，须随本次评审核对；没有改业务验收或已有controller协议。
