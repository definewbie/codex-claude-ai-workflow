# B03 · D05 事前可行性证据接线决定

2026-10-06。设计补记；未实施、未运行，不是 B03 PASS。用户要求进入下一步，本轮完成此前约定的接线决定，不改产品代码。CC 对 B03 中间状态的评审支持该顺序；本补记本身尚未经独立复审，随 B03 批末受影响范围一并审查，不另设评审轮次。

## 1. 决定与边界

保留 D05 已冻结的“执行批准前落实必需验收出口”。选择：既有程序事实符合条件时复用；没有适用程序事实、但 operator 能承担具名环境/责任时，沿已有 review 人工操作面登记**事前可用性声明**。新增的是现有 artifact/ref 与 ledger mutation 的负载和操作，不是环境注册表、权限类型、测试 runner 或认证服务。

三类事实严格分开：可用性声明说明未来执行条件与责任；执行记录说明实际发生的操作；验收说明操作与断言是否满足目标。事前声明可为 AGENT 的机器验证路径说明“本地测试环境已准备”，不把最终 evidence_kind 改成人工、不替代实际工具记录和断言。涉及账户/网络/tool authority 仍引用原批准/grant，声明不能授予权限。

本补记解决的缺口是生产入口与消费规则，不宣称程序能侦测任意设备、远程服务或账户状态变化。不把历史实验、合成样例或此次源码核验变成产品环境证明。

## 2. 已有生产者正向清单（LOW-2）

源码身份见 [source-index.json](d05-wiring-evidence/source-index.json)。以下为静态核对，不是现场环境检查。

| 生产者/候选 | 已有事实及可支持的用途 | 不足与首版处理 |
|---|---|---|
| AgentRunMetadataWriter 的完成记录、原始 transcript/stdout/stderr | 外层 acpx command/exit、session/run 和原始材料来源；可帮助定位确实发生过的调用 | acpx exit 不是内部测试退出；run.json 可变。只有 D07 固定完成引用及适用 typed 工具结果齐备，才复用其中**对应环境与操作**的事实；不以整个 run 成功代证 |
| AcpxToolUsage / AcpxTranscriptToolUsageReader | 工具计数、tool id、命令与文件路径，可定位候选事件 | 当前没有逐调用结果/exit 的完整关联，不能直接证明工具成功或环境可用。D05 已冻结的 raw event→typed input/result/exit 采集仍须实施；不把这项成本写成零 |
| AcpxStreamEvidence | session-ready、prompt-stop、协议错误与 pending control | 能说明原生调用边界，不携带工具结果；只能支持相应客户端会话事实，不能证明 Python/设备/浏览器能力 |
| EvidenceCollectionSource.capture / verifySource | 工作区根、HEAD/index、相关文件实际字节与源码基准 | 可支持“相同输入/配置文件仍存在且字节未变”这一部分身份；不证明依赖安装、服务可用。源码整体 hash 不当作环境 identity，正常改源码不应无条件废弃环境证明 |
| ExternalVerificationEvidence.Declaration 与材料 | 人工申报的 command/environment/times/exit 和明确材料，owner/提交来源可追溯 | 可为后续同环境可用性声明提供背景，信任仍为 OPERATOR_ATTESTED；现有 prepare 依赖已批准 EXECUTION，不可作为首次事前入口 |
| SelfTestResult / AcceptanceReviewResult | 模型 checks/evidence 与语义评审 | 只沿引用取真实执行事实；字符串 checks_run 或 ACCEPT 不能升级成程序环境证明 |
| 构建报告/原生工具日志 | 同实际工程、同执行环境的既有程序日志可能支持工具/依赖可用 | 当前未核到可直接通用消费的项目构建记录入口。用户上传日志不自动获得 PROGRAM_OBSERVED 身份；没有受支持生产者/版本和运行身份时，只作声明附件。无需先建设通用构建平台 |
| DoctorCommand | Git/acpx 版本、仓库、adapter 配置等诊断 | 当前输出为诊断，不是通用不可变环境证明；可用于诊断缺项，不能拿 OK 支撑任意验收条款 |

**准入选择：** 首版自动复用仅接受 program-owned、已完成且不可变引用可验证的记录，以及同一记录能支持的明确事实；当前工具结果采集缺口未补齐前，不启用该复用分支的成功结论。人工声明路径负责已冻结的 operator 可用性语义，不能伪造程序生产者。现存生产者只提供部分事实时，可与声明分别引用，不合并成更高可信等级。没有可落实的出口就停止该计划，不默认增加探测矩阵。

## 3. 承载入口与唯一 owner（LOW-1）

具体选择：在既有 CLI `review` 命令组增加 `record-readiness --subject <occurrence> --request-id <id> --file <payload>`；API 同步增加 `POST /{workstreamId}/review/readiness`。这是拟实施接口，不是当前可用命令。命名可随同批风格调整，行为不可改变。

CLI 与 WorkstreamActionService 调用同一 runtime 语义入口 `VerificationReadiness`（拟新增的 DTO/校验与应用类，不建 model 模块）。它校验 GOAL、条款、来源及环境依赖；WorkflowFileLedger 是持久化 owner。TaskContractProjectionVerifier 统一消费已提交记录；context、RecoveryAssessment、CLI/API/UI/report 只投影。模型输出不能调用此入口产生人工声明，payload 中自报的姓名/producer 也不产生可信身份。

入口仅接受当前可操作 subject：已批准 GOAL 后的 plan/contract 人工门，或因 readiness 缺项/漂移而失败的本地编译或执行准备 occurrence。不允许历史/终态/已被替换 subject，不在任意 RUNNING task 中补换输入；未知在途先走 D07。GOAL 阶段可记录未知，但不要求声明齐备。可在计划明确后、首次 EXECUTION 编译前补齐。

程序记录由可信 producer 注册引用；人工入口不能把上传字节声明为 PROGRAM_OBSERVED。人工提交可以请求复用某个程序引用，但 service 必须独立核验生产者、固定完成引用、适用环境及事实范围，否则拒绝该类别。

`request_action` 仍仅记录需要什么，不能代替实际材料；`submit_evidence` 仍用于事后执行材料，不改名复用。`record-readiness` 只保存材料，**不 resolve gate、不 retry、不发 provider、不产生批准或 READY**。用户随后使用既有 revise/retry/approve 出口，其前置由同一消费者核验。

## 4. 负载、身份及复用规则

事前记录存为 `artifacts/verification-readiness/<request_id>.json`；request id 遵循既有安全字符约束。记录绑定：schema、request_id、workstream、subject occurrence、GOAL ref/SHA 与 epoch、criterion_ids、method/executor/evidence_kind、environment identity、dependency refs、statement、detail_owner、适用期限。service 加入 received_at、操作来源、适用 checker identity 与结果；不采信用户伪造的程序 owner/检查结果。重复同 id 同规范化用户负载返回原记录（不重新生成 received_at）；同 id 异内容 INVALID。非敏感身份入账，不保存凭据。

environment identity 分两部分，不用一个 available=true 代替：

| 身份部分 | 必需内容/来源 | 复核含义 |
|---|---|---|
| 适用对象 | 当前 workstream、canonical workspace、GOAL/criterion、执行主体、方法和证据种类 | 不能跨目标或跨工作区借权；不是授权文件写入 |
| 可观察依赖 | 与该方法实际相关的现有配置/lockfile/executable 或程序环境记录 refs+SHA；不存在的预定文件明确标 absent | 只检查具名依赖；实际配置/工作区改变可检出。不得自动把全工作区、所有环境变量、所有 provider/model 都列为必需 |
| 人工负责的对象 | 设备/环境的明确标识、负责人、可用性及已知限制；声明有效到何时 | program-owned 接收记录证明谁经本机人工入口作了什么声明，不证明物理设备一直在线；账号只存非敏感标识 |

人工声明的期限必须明确：当前授权 epoch 内至显式 expires_at，或当前授权 epoch 内持续有效直至撤回/具名依赖变化（payload 必须选择，不能静默默认永不过期）。到期检查发生在消费时，无后台定时器。计划要求更强实时环境保证时，声明不能降格该要求，须有适用程序事实或原处置出口。

撤回使用同一入口的 `withdraw` 负载引用原记录，原记录保持不可变；重新确认使用新 request id 和显式 supersedes ref。同一条款/方法存在冲突声明且未指定替代关系时不得任取“最新”。当前有效引用来自已提交的 subject 记录及替代链，不能扫目录猜测。程序事实失效也保留历史，不改写成当时未发生。

复用须满足 GOAL/条款、方法、执行主体、证据种类、环境标识及实际依赖仍适用，来源未撤回/过期；单纯换源码不使环境事实全失效，但影响依赖文件时必须失效。环境事实可复用不代表旧 self-test/ACCEPT 可复用；完成证据仍由 D05 的源码身份规则决定。

同一真实记录可同时出现在 environment_refs 与 feasibility_refs，但必须分别足以支撑环境身份和该方法可行性；两个列表非空本身不算成立。首版校验固定字段、引用与批准方法对应关系，不解析任意 shell 语义或让 LLM 认证来源。

## 5. 缺项补齐到合法续作

| pre-state/触发 | owner、后继及成功条件 | 不允许 |
|---|---|---|
| GOAL 环境未知 | GOAL 编译允许；design/plan 继续只读提出方法和缺项 | 提前要求 EXECUTION 或假引用 |
| plan reviewer BLOCKED，缺项明确 | 保留真实 review→既有 plan 人工门；登记 readiness→revise→作者/独立复评→新 decision | 人工补材料直接把 BLOCKED 改 READY |
| reviewer READY，但程序核验 readiness 缺失 | ReviewDecisionHandler 保留真实 review/谱系，输出独立的 readiness 缺项原因，路由既有 plan 人工门；补齐后走作者/复评。新增 typed outcome `VERIFICATION_READINESS_REQUIRED` 与定义/registry 同批对齐，不伪造模型 BLOCKED | 正常 READY 且材料齐全时不得实例化该门；不能用所有请求等待人工来通过 |
| READY 之后、EXECUTION 编译时引用漂移/缺失 | ContractCompileHandler 失败，保留当前 GOAL/两 reviewed refs 和缺项；既有 recovery 展示 record-readiness，再 retry **本地编译**。只有新材料仍精确支持已评方法/身份、候选未改时可复用 review；改变方法/环境约定回 plan 作者/复评 | 创建成功的空 EXECUTION，或为了补编译调用模型 |
| 材料齐全且生产者可信 | compiler 从当前谱系及显式 readiness refs 编译 EXECUTION，冻结完整 bindings→既有 contract gate 批准 | 编译器自行发明方法、扩 scope 或凭材料代替批准 |
| 首次/后继 implement 准备发现失效 | AgentRunGovernanceContext→TaskContractProjectionVerifier 在 R02/transport 前失败，typed reason 保真；当前 occurrence 的 recovery 提供补齐/修订动作 | 模型 JSON 错误替代准备失败，静默重试旧输入 |
| 原环境恢复、原材料仍有效且字节未变 | 重验后沿既有 retry；已发生外部 intent 的旧 attempt 仍按 D07 裁定 | 用环境恢复证明旧外部动作未发生 |
| 新证明替代旧证明 | 不覆盖 EXECUTION 中旧 ref。若同环境/方法/权限/必需验收与 evidence_kind 不变，沿 D03 同授权技术修订：新 plan→独立复评→新 task 的当前 reviewed refs/R01 绑定补充事实，qualification 同时核验旧合同边界和新证据；原记录无效不被“复活” | latest 指针自动替换资格，或跳过复评 |
| 环境约定、执行主体、证据标准/权限等实质变化 | 回适用 plan/复评/EXECUTION 批准；目标变化再回 GOAL | 以“只是换证明”绕过原批准 |

从编译/dispatch recovery 回 plan，具体承接在既有 `review revise` / API `review/revise`：增加 expected subject 参数，允许当前 readiness 失败且不存在未决外部执行的 occurrence；WorkstreamActionService 与 CLI 调同一 runtime 计划修订入口，由 L06 固定旧失败/claim 关闭、返修义务、新的 plan context→作者 occurrence、event/current。不把失败节点改成人工 gate，不伪造 BLOCKED review，不复用要求当前 gate 的 request-scope-revision 来绕过入口前置。复评后按 D03 判断同授权继续或重新批准。该入口扩展尚待实施，是本补记选定的迁移，不是现有能力；普通 retry 只重做未发生外部副作用的相同输入准备/本地编译，不替代语义修订。预算沿现有有界修订规则，超限走已有人工处置，不借补材料重置次数。该迁移属于 D03/D07 联合范围，不能宣称与合同链无依赖。

## 6. 写序、dispatch 复查和失败切面

登记时，runtime 先只读解析 payload/显式来源，进入同一 L 写会话后重验 subject、GOAL/epoch/材料；由 D06 L04 固定 readiness bytes、subject 的 readiness_refs、request 结果和事件 after-state，再提交。gate 的 open/failed 状态不变。记录与引用一起提交，不直接修改业务源码或执行日志；程序生产者原始材料若先发布而本地提交失败，孤立材料不授资格。

若撤回/替换影响已批准执行资格，L04 记录与 D06/L06 关闭旧 claim 的 permission eligibility 在同一 mutation 固定；不得先可见新材料而旧资格仍开放。C 已提交的在途一次回复仍遵循已接受的 C/X 语义，不能追回的外部效果按 D07，补材料不发新许可。

| 故障窗口 | 恢复与读者行为 |
|---|---|
| manifest 前失败 | 旧事实不变，零后继/外部调用 |
| manifest 后任一写入/事件之间 | pending；普通读不拼接新旧事实；同版按固定 after-state 补齐，零 provider/Git |
| 全部 after 已写、pending 未删 | 校验后只删除 pending；同 request 查询原结果，不重新登记/决定 |
| 错身份、坏 manifest、字节非 before/after | INVALID，保留现场；不覆盖外来变化 |
| 保存成功但 CLI/API 响应丢失 | 同 request 幂等取回；保存不等于 gate 已决定/已执行 |
| 材料登记后、批准前变化 | 编译/批准重验拒绝；不对旧 hash 的合同批准新字节 |
| 批准后、dispatch 前变化 | R01 记录实际核验的 readiness refs、依赖观察及规则；R02 前重验当前资格/refs/可观察依赖，失败零 provider。inline/worker/correction/session 重建共用入口 |
| R02 后变化或停止不明 | 不声称启动前检查持续保证环境；取消/停止沿 D07，验证与交付拒绝不适用证据 |

复查只读具名本地事实和已有原生配置身份，不发任意探测命令、不访问远程服务。无法观察的设备/服务状态保留 OPERATOR_ATTESTED 及期限/责任，不能伪装成机器“未漂移”。源码稳定性与环境可用性分别核验。

## 7. 实施落点、验证与收口

实施落点：ReviewCommand/新子命令、WorkstreamApiController/ActionService 共用 VerificationReadiness；WorkflowFileLedger/RoutingEngine 的 L04/L06；TaskContractProjectionVerifier 与 AgentRunGovernanceContext 的唯一资格消费；ReviewDecisionHandler/ContractCompileHandler/四份定义；AuthoredStageLineageResolver 的当前补充 refs；RecoveryAssessment/统一 query/read model 的缺项与可执行动作。D05 raw tool evidence 增量按既有 adapter owner 实施，不加新 runner。以上是具名范围，不是本轮已改文件。

必须按用户流程证明：

1. GOAL 未知→计划明确→具名程序证据复用→正常 READY 零中间门→执行批准→合法 dispatch；有适用记录时不要求额外声明。
2. 设备/本地条件仅由 operator 知道→缺项等待→登记→作者/复评或适用本地重试→批准→合法执行；最终要求机器证据的条款仍不能以声明通过。
3. 多验收项分别绑定、重复提交同/异内容、缺条款/跨 GOAL/错 subject/错 SHA/伪程序 producer 拒绝；全拒绝不能作为测试通过。
4. 同环境复用、无关源码修改的正向对照，与依赖变化、过期、撤回、冲突替代的拒绝对照；同授权新证明经过复评可继续，实质变更必须重批。
5. 写入各前缀、冷 JVM 补齐、响应丢失；read model 区分已保存/待复评/待批准/可执行以及 BUSY/pending/INVALID。
6. inline/worker、四变体、correction/session、新旧 claim、C/X 失效关联；外部未知零自动重发。移除资格消费、错用 latest、把 toolUsage 或人工声明冒充机器事实时，反证必须失败。

六维：Declared=本补记与 D03/D05；Proven=仅源码核对，以上测试全部 NOT RUN；Consumed=指定 compiler/qualification/验收/交付 owner，接线未完成；Enforced before=目标为批准及 R02/dispatch 前，不宣称现已强制；Failure route=上述 gate/recovery 与 D06/D07；Cannot-bypass=上述正反序列待实施。当前不能称闭环。

收益是让首次任务有真实合法出口，同时保留独立评审和最终证据要求。成本集中在同一材料入口、引用/身份校验、资格消费和现有回归；不预估工期或称成本近零。若需要持续监控、通用 parser/命令 DSL、环境 registry、新权限类型、runner 或改变验收标准，停止并提交取舍，不扩大本决定。

B03 仍须完成 progress-and-decision.md 的全部剩余项；本地存储切片可推进，不把依赖合同/epoch 的 D07 当独立闭合。此次不运行 native/provider、不恢复旧样本、不提交、不解除 NO-GO；用户既有联合实施授权不撤销。本补记随 B03 批末一起评审，无须单独调用 CC。

## 8. 实施进度补记（2026-10-06，非设计收口）

以上“未实施/NOT RUN”为接线决定产出时点。用户批准继续后，operator 声明入口、登记写集、plan 缺项分支与 EXECUTION 编译/批准/准备消费已有实现；限定离线续作序列通过。程序事实自动复用、失败 recovery 回计划及同授权补充证明等仍未完成，不能把一个合法 plan 门路径扩称全部义务闭合。详见 [检查点](implementation-20261006/checkpoint.md) 与明确未通过的全模块测试状态。设计补记随 B03 批末评审，未授权真实运行或提交。

## 2026-10-06 接线实施边界更新

人工 readiness 登记、不可变 L04 引用、编译/批准/准备消费、撤回/替换/依赖漂移及失败返修的代码和离线序列已经落地，详见 [continuation 检查点](continuation-20261006/checkpoint.md)。候选正向清单中的程序工具事实复用尚未完成；本机固定 Claude ACP 部分工具退出字段不是实际进程 exit，此事实与待确认方案见 [D05 evidence decision](continuation-20261006/d05-evidence-decision.md)。本补记不把 operator 声明升级为程序观察，不改冻结验收，不申请真实实验。
