# D01 首版 C：合同资格校验族的完整垂直切片

2026-10-08；P0。用户明确选择 C；用户带回的独立设计复审确认本族候选具备实施条件，LOW-1 枚举守卫已并入下述验证要求。**设计认可不等于本族实现或 D01/W01 完成。**无 native/provider、提交、installer、额外 agent 或下一批授权。此前 B05 停在 ROI 取舍前的材料保持历史状态，不覆盖其基线/判断。

## 1. 选择及完成边界

首族选 `TaskContractProjectionVerifier` 的合同资格校验：当前 GOAL/EXECUTION 解析、EXECUTION 候选批准前校验、实际 dispatch 重核及 routed acceptance 合同绑定。选择依据是批准前与 dispatch 已共享 `validateExecutionInputs`，不是最高频事故统计。旧 HHZL 属输出枚举拒绝，不能仅因相似把它当本族历史证明。

族内包括：candidate 的等待 gate/subject/compiler/字节检查；GOAL 的批准/身份/谱系；EXECUTION 的完成 gate/批准/compile/不可变合同及当前验证条件；读取 node/gate/event 的异常；acceptance 的 routed context/合同绑定。每个族内拒绝都须有具名 typed 前置或明确的不可观测原因，不能有未标识的族内失败直接降成“其他族”。

`AuthoredStageLineageResolver`、`TaskContractRevisionContextResolver`、`ScopeRevisionRequestGuard` 和动作语义不在首版逐规则采集范围；本族委托它们时发生的拒绝，也只能按实际来源标未覆盖/UNKNOWN，不能把包装异常当本族已证明的矛盾。它们的严格校验仍运行，原 reason/code 不变。

**C 的结束条件**是本族实际 producer→决定→拒绝配对→四类恢复解释→合法补足/重批及失败前缀/冷启动/兄弟投影通过；不是仅实现分类器。其余族 UNKNOWN、本族 I/O/关联不明也 UNKNOWN。不能凭单族 C 宣布整个 I1.a、D01、W01 或真实主流程完成。

受审增量仅把“首版全五族逐规则自动诊断”改为上述单族完整、其余明确 UNKNOWN。原五族完整覆盖保留为未完成；重新投入须依据真实反复阻塞与可观察损失重新判断，不自动平移分类器或启动下一族。保留合法用户流程、strict identity/scope/approval/evidence、独立复评、终态保护及外部结果不明人工裁定的全部验收。

## 2. 现有 owner 与接线（不加平台）

| 阶段 | 生产/消费位置与增量 | 不可冒称 |
|---|---|---|
| 实际检查 | 原 `TaskContractProjectionVerifier` 在执行条件分支时产出本族 typed rule/check snapshot；明确语义常量，不解析 message，不运行旧 JSON 枚举 | 同类、同错误码不等于同一规则；异常包装不是规则身份 |
| 人工决定 | `ReviewGateActions.resolve` 在现有 L 写会话内核验 candidate，携带程序生成的 typed snapshot；`RoutingEngine` 再核 gate/source/subject/action 绑定，将快照写进既有 gate.result 与 node.result 的 `decision_checks`，随 L04 | 快照不产生批准；模型、UI、CLI/API 请求不能提交或覆盖这些字段 |
| 后继准备 | `AgentRunGovernanceContext.ensureQualified`、`AgentRunCommitProtocol.qualifyDispatch` 携带实际本族 Failure 的 typed 诊断；`AcpxAgentTaskHandler` 所有相关 catch 进入同一封闭结果构造 | 不能只接第一次准备而漏掉 R01 后、R02 前重核；其他 qualification 异常不能被冒认本族 |
| 配对与保存 | `AgentExecutionResultFacts` 扩展合法构造/封闭核验；R03 固定原失败及 `continuation_failure`，L03 同次提交 node/task/execution，沿原 D06/D07 | 任意附加 map、sidecar、旁路日志不获可信身份；配对缺失不能宣称完整记录 |
| 恢复/投影 | `RecoveryAssessment` 核对固定来源和 typed 输入，唯一分类；已有 attention/query/CLI/API/UI/report 消费它 | 不从自然语言、latest、全目录 SHA、UI 标签推断分类或批准 |

不移动整个 checker 到 ledger/core，不引入跨模块反向依赖、ThreadLocal 全局追踪器、通用规则解释器、权限/诊断服务或另一份账本。携带的是既有结果/异常的诊断值及现有决定方法的 typed 参数；规则仍只由原 checker 执行，RoutingEngine 不再次发明合同校验。

**直接 ledger/兄弟入口：**未调用本族 checker 的合法内部决定仍遵守现有 gate guard；新记录必须明确 `family_coverage=NOT_INVOKED`，不能写 PASSED，不能借用别处 snapshot。它产生的后继诊断只能 UNKNOWN（不冒充一次本族已核验的决定）。常规 contract approve 经原 runtime 入口采集，不能以此例外跳过现有 candidate 校验；测试须覆盖直接入口、CLI/server wrapper、collect/submit evidence、revise/clarify/非批准动作。新 covered 路径缺快照或绑定失配，零决定写入；未覆盖入口不是缺失新记录的兼容 fallback。

## 3. 规则与输入身份：具体约束

1. `rule_id` 是 `TaskContractProjectionVerifier + 具名语义条件`。同一 Failure.reason 在不同条件/方法下出现必须用不同 id；candidate waiting 与 execution completed 是不同规则。只有真实共用条件才复用 id。
2. `rule_version` 使用本次实际加载 checker 内容身份，并与已有 `frozenRuntimeIdentity` 绑定；不能用开发者手填版本或源码行号充当实测。本族调用的委托 checker 不因本族版本相同就被证明相同。旧/异运行身份按 D07 现有规则，不新开兼容路径。
3. 每条检查记录 `input_refs`（实际 ref+SHA）、固定字段的 `input_values` 和 coverage/result。`input_values` 只由 checker 读取/计算，不允许通用 selector、任意 JSONPath 或用户声明。没有足够依据证明输入可比时为 UNKNOWN，不用空集合 SHA 声称输入相同。
4. 接受决定时已执行、且该条件确实适用，才能记录 PASSED。留待 completed gate、未来 target/current verification bindings 或实际 dispatch 才检查的规则明确 NOT_CHECKED 及原因；族未调用、历史无记录、采集失败或规则不认识均不是可信 NOT_CHECKED。

| 本族条件 | 实际需要绑定的输入 | 正常推进时的比较口径 |
|---|---|---|
| 候选/current gate | 当前 occurrence、active/status、候选 source、subject refs、requested/canonical action | 此条件只适用决定，不拿等待状态与批准后 completed 状态比较 |
| 候选与已批准合同的 shape/hash/identity | contract/resolution 的实际字节 ref+SHA；同一 compile occurrence 及 id/version/epoch/phase、output refs、固定 SHA 字段 | 共享条件可比；合同字节或 compiler 固定字段变化才是有据差异，不忽略缺文件 |
| GOAL/EXECUTION 关系 | 两个不可变合同 ref+SHA；goal source/epoch/条款、约束与禁止范围的实际字段 | 不把 GOAL 变成 EXECUTION 视为同一条件；同一执行合同绑定的 GOAL 改变须明确列差异 |
| nonessential declarations | 合同中的声明；实际冻结 definition 中相关 node.config，记录 definition ref+SHA 与实际字段 | 不从模型 available/optional 描述补字段，也不改首版全量声明规则 |
| 独立复评与路由 | compile、reviewed candidate 的固定 occurrence/ref+SHA，实际使用的 routed event ID/sequence 和该记录内容 | 不把整个 events.jsonl SHA 当历史身份；正常追加无关事件不抹掉已固定材料。委托 authored checker 的规则不在本族覆盖里 |
| 批准后 gate/事件与更新编译 | 已 resolved 的 gate/node/ref+SHA、批准 route；实际已完成 compile 的 epoch/时间/身份集合 | 这些 future 条件在 candidate 决定时 NOT_CHECKED；后来出现新 completed compile 是实际资格变化，不能默认继续使用旧批准 |
| readiness | 当次适用 bindings、要求字段、声明 ref+SHA、有效/撤销/替换状态、期限、检查时刻、真实环境身份与依赖观察，沿 D05 的现有规则取得 | candidate 检查的是候选绑定；dispatch 若消费后续技术计划的新绑定，必须重验。相同文件也可能已过期/撤销；不因时钟正常前进就判漂移，必须证明跨越实际有效期条件 |
| routed acceptance | 当前 target/context 的 ref+SHA、声明的 contract SHA/compile ref 与实际 qualified 结果 | target identity 必须相同才比较；未来 acceptance context 未生成不是已经 PASS |
| 文件读失败/缺失 | 实际 ref、可观察的 missing/unreadable 状态，已有 I/O 原因；不可测的字节不填 SHA | 能证实际缺失与旧记录不同才列差异；读不明就 UNKNOWN，不断言未执行或契约矛盾 |

事件记录身份的保存形式：诊断中明确 `kind=ROUTE_EVENT`、文件 ref、event_id/sequence 及该条实际记录字节的 SHA；它不是“整个文件同 SHA”的 FILE 引用。核验由现有事件 reader 按唯一 ID 读实际条目；重复/异内容、缺条目或无法核验均诊断不足。不新增事件存储或通用片段寻址服务。若实施发现此表示不能在已有 reader 内有界承载，应停止评估，不改用整体文件哈希凑通过。

本次 [rule-candidates.json](rule-candidates.json) 固定本族当前源码、67 个 `Failure.of`/显式 I/O/参数拒绝位置、拟议语义名和输入 profile，供本轮对码防漏。它不是 67 个完成的规则，不是测试数量/成本单位，也不运行时加载；原始条件与实际 operand 的逐项接线仍需实施时显式确认，不能按异常文案派发。candidate 的 bare throw、读取 catch、重复 code 与 `resolveRoutedAcceptance` 已纳入候选索引。

还须覆盖两条桥接拒绝：`AgentRunGovernanceContext` 在 resolution absent 时的 `APPROVAL_BINDING_MISSING` / `CONTEXT_BINDING_MISSING`。它们属于本族结果消费者的具名缺失检查，索引中不是本类 `Failure.of`；分别绑定 required_phase 与 target/input refs。`VERIFICATION_EVIDENCE_INVALID`、authored prepare 失败等其他来源不因同一个 QualificationException 外壳就纳入本族。

D05 readiness 提供的是已有校验实际使用的声明/期限/环境观察，而非模型“available”或另做环境认证。仅为携带这些观察而给现有方法增加 typed 返回值，不能改其批准/有效期/依赖校验；若拿不到实际观察就 UNKNOWN，不补造证据。时间比较针对该具体期限谓词，不能加通用时间 DSL。

## 4. 配对与分类

`decision_checks` 仍使用详设 §4.1 的字段，加本族 coverage 与逐规则实际输入值。`continuation_failure` 保留固定 decision ref+SHA、action/source/target/subject、consumer/phase、原 failure_code 和具名 rule/version、当前观察。

从**当前 target 的已核验路由/合同谱系**关联决定；本族 resolver 选定的合同 occurrence 只是候选关联，必须再核 target 与该批准的关系。不得任意搜最近批准、凭 note/action 文本认领旧 gate。无 resolving 来源的普通准备错误不造 pair；适用但来源核验不了的拒绝，产生明确诊断不足，不静默省略配对。

| 可信证据 | RecoveryAssessment 解释/动作 |
|---|---|
| 同一适用 rule/version，输入值及实际材料身份相同，决定记录为 PASSED，却被拒 | 契约/实现矛盾。保留原失败，普通自动 retry 不可用；定位 checker/consumer，不产生新许可 |
| 固定输入中有实际可核差异 | 漂移。列变更对象；失效批准不能复用，按对象走既有补材料/返修/新批准 |
| 同一适用规则在可信决定 coverage 中明确 NOT_CHECKED，消费时确有缺项 | 消费阶段缺项。列实际 owner 与已存在的合法入口；若该入口尚未证明，不显示可执行命令 |
| 未覆盖族/入口、规则不相同、调用语义不可比、缺/错 pair、规则身份或 SHA 不可信、I/O 不明 | UNKNOWN/诊断不足。原 reason/code 保真，人工核对，零自动重发、无安全 retry 声明 |

分类顺序不覆盖外部 UNKNOWN、非法 authority 拒绝或原决定性原因。解释不是第二控制原因；分类器不授予 retry/revise/approve，不伪造 providerInvoked=false。只对本族明确在调用前的拒绝附准备失败诊断；R02 后的不明外部结果继续 D07。

## 5. transition、写入失败与恢复

| 前置/触发/owner | durable 顺序与下一消费者 | 每个失败前缀及冷启动 |
|---|---|---|
| 当前 contract gate；用户 action；原人工入口 | L 内检查→实际快照→RoutingEngine 绑定→L04 同 prepared 写 gate/node/route/event/current | 非法 action/subject 或 covered snapshot 不完整，零决定；提交未完成 reader pending，恢复补固定字节，不重做决定/检查，也不宣称缺快照已经补齐 |
| 实际 target 准备/dispatch；本族 checker | 重核实际规则和输入→合法才 R01/R02；被拒则原 typed Failure 携诊断→封闭准备失败 | 无/错合法来源不造批准；R01 后 R02 前重核同样拦截；不能通过另一入口或 worker 借旧 PASS |
| 拒绝结果；Facts/R03/L03 | 原原因+完整 pair（或明确诊断不足）进入同一 R03 固定完成材料；L03 写 node/task/execution/恢复事件 | R03 写不完不发布完整失败；R03 固定、L03 未完只续本地，零 provider；所有前缀与冷 JVM核对固定字节，不回填历史 decision |
| 当前 recovery 查询；统一 read model | 核对来源/输入→分类与真实既有动作→各投影 | UNKNOWN 不猜矛盾；历史只展示，不为终态提供当前动作；BUSY/pending 与“失败记录完整”区分 |
| 合法补足或返修；既有入口 | 同输入补材料后重核，或作者→独立复评→新 candidate/批准→新 occurrence | 不编辑旧批准；代码/合同/验收要求改变仍按 D05/D03 重测或重批；合法正向与缺项拒绝同时证明 |

沿现有 L/R 顺序，不增加锁/outbox/恢复 action；不持 R 获取 L。新诊断必须参加原 prepared/R03 内容身份，不能靠一份后来追加的日志解释原失败为已完整记录。

## 6. 必需验证与停止条件

- 正常：实际执行批准→作者/自测→独立验收/最终门；READY/BLOCKED revise、clarify 与重新批准的原序列继续，不在下一 READY 停断言。
- 矛盾：用**测试自身的错误 checker**运行实际决定记录与后继消费，保持规则身份和声明输入相同却错误拒绝，证明机制分类；不新增生产 fault hook，不靠手填 PASSED map 冒充 producer。该证据仅证明诊断机制，不能宣称生产 checker 存在该 bug。
- 漂移：改变已记录的真实合同/声明/环境依据，经过真实消费者拒绝，旧批准不复用；正常 route/event 追加、完成 gate 和无关文件变化是反向对照。
- 未检查：未来 target 的实际验证条件不具备时经过真实 consumer 拒绝，决定明确 NOT_CHECKED；用已存在、经核验的 readiness/作者返修入口补齐，回到重核/复评/批准。不能用 fabricated unknown coverage 代替。
- UNKNOWN：其他族真实拒绝、直接未采集入口、missing/伪 pair、I/O 不明和不可比输入，均保留原因与保守处置；正常零 resolving 来源不误报非法 gate。
- 反证：删除 pair 或误标 PASSED/NOT_CHECKED、同 code 混 rule、只比较整个 events 文件、跳 target 关联，序列必须失败；同时保留合法正向，不能以全部拒绝通过。
- 持久化：L04/R03/L03 各写入前缀、固定补齐、冷子 JVM；覆盖初次准备与 R01 后重核、inline/worker、四变体及 CLI/API/UI/report 同源。使用现有 failpoint，不新增通用模拟平台。
- 枚举漂移（CC LOW-1）：测试 JDK 的 Java AST 离线枚举本族的 Failure.of、直接 Failure 构造与显式 throw；实际拒绝点必须有索引或具名未覆盖依据，索引与源码内容身份同批更新。正向核验现有源码，新增结果拒绝/异常拒绝须触发失败，注释/字符串不误报。索引不在运行时加载，登记不等于规则实现；实施中须同步核验其到实际 producer 的映射。
- 最终：内容稳定的单轮全模块离线回归，批末按 R3 §7.1 独立评审。真实 native/provider 与真实 Gate 仍另列，不挪用 B04 1701 项。

若为这个首族仍须做全家族自动仪表化、通用输入依赖图/规则 DSL、额外服务/存储/锁，或三类实证只能由手填事实骗过，则停止并提交具体证据与替代；不因选择 C 就强行证明。没有“成本为 1/5”承诺、固定测试数量目标或自动开启第二族。

## 7. 六维与当前状态

2026-10-09：本族已实施／离线自查与最终回归完成／用户带回 CC 批末独立 PASS，单族 CLOSED / PASS，见 [收口处置](../closure-20261009/disposition.md)。详见 [实际批次材料](implementation/batch.md)、[当前消费者边界](implementation/consumer-map.md)和 [最终凭证](implementation/test-results.json)。下述状态替代此前“仅设计、待落码”，不回写旧成本或候选证据。

Declared：用户 C 与本族受审设计；Proven：真实离线批准/后继/恢复序列、三类与 UNKNOWN 分层对照、写入前缀/冷 JVM、两入口四变体、单轮 1,731 项全绿及额外时钟隔离反证；Consumed：原 checker→L04→原 native/本地任务消费→固定完成/L03→RecoveryAssessment；Enforced before：批准写入、dispatch、失败完整性采纳与后续路由，不称 OS 写前；Failure route：D06 固定补齐、D07 外部 UNKNOWN；Cannot-bypass：缺/错 pair/身份/规则、枚举、失败前缀及合法正向对照。

native 准备拒绝未有 R01 时仍在现有 runs/outputs 固定本次 execution 的 input-rejection 完成，绝不产生准备/外部发送资格；本地任务不造 R03。固定失败后原发送者不能再建 R02；R02 已存在则不得记录零调用失败。此为现有 writer 接线，不是新 store/authority。

本族可比证据切片达到离线 PROVEN；未覆盖委托、无法观察的 I/O 与无可信关联仍 UNKNOWN。本族已完成独立判断并收口；时钟判别尚未常驻的 LOW 已登记，不因此关闭完整 D01/W01/I1.a、真实 NO-GO 或启动第二族。来源定义与停止条件维持。

收尾格式变化与最终整轮源码凭证分列，1,109 class 字节未变且索引守卫通过，见 implementation/format-proof.json。
