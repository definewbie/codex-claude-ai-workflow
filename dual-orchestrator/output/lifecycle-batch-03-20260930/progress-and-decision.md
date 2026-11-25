# B03 联合实施中间状态与待明确入口

2026-09-30 起的历史进展。当前（2026-10-07）**B03 CLOSED / PASS**，见 [合并批末处置](closure-20261007/disposition.md)；以下中间状态按其记录时点解释。收口限定本批实现和离线验证，不代表真实运行准入或完整产品交付。

## 当前实现与验证边界

已开始 v2 GOAL/EXECUTION DTO、显式 ledger/session、固定本地写集及恢复、认领/完成分组、上下文与查询消费者显式传入 ledger、reviewed candidate 和四变体路由迁移。新增生产代码编译通过（compile-joint-contract-fixed.log），但生产装配/完整合同编译及运行桥接还没有全部迁完，不能据此使用真实流程。

LedgerSessionTest / WorkstreamLockTest / WorkflowCommitSequenceTest 本次重核 12 tests、0 failure/error/skip，包含实际初始化→人工决定→后继、逐写入中断及固定补齐、冷 JVM 和身份错误拒绝；原始报告存 ledger-sequences-recheck-reports。它们仅证明相应本地协议切片，不证明完整 B03。

普通增量 test-compile 报 Nothing to compile，旧测试仍使用已删除签名，故该结果不能作为测试源码通过。随后 clean test-compile 如预期在 runtime 测试编译失败（clean-test-compile-status.log）：旧测试构造器/合同 fixture 尚待迁移。全模块回归尚未通过；不得引用 B01/B02 的 1559 项作为本批绿色。

in-progress-changed-files.json / in-progress-incremental.patch 对比批前真实 dirty 内容；不以 HEAD diff 混算 B01/B02 或用户已有改动。未提交、未调用 provider、未运行 native 实验、未启动额外 agent。

## 接线中发现的具体未决项

D05 已决定：EXECUTION 编译前，各必需验收项必须有实际环境/可行性引用；可以是 operator 可用性声明或已有程序环境事实。已定义其语义，尚未指定新增声明的具体生产入口和字段。不能用模型自己写的 available=true 或任意已存在文件的 SHA 代替真实来源。

证据：
- freeze-details.md §5 明确环境可行性在 EXECUTION 前落实；examples/environment-example.json 明确为 SYNTHETIC_ENVIRONMENT_REFERENCE，不能作为生产资格。
- ExternalVerificationEvidence.prepare → EvidenceCollection.captureBaseline：要求等待中的可补证据人工门，随后必须解析已批准的 EXECUTION 合同。该入口是实施后证据提交，不能直接作为此前环境声明入口。
- ReviewSubmitEvidenceCommand 的 --environment 属已发生执行的人工 Declaration，与“以后具备执行条件”不同；不得改名复用。
- 已核对 CLI/server/gate record 的相关入口，未找到现成的结构化可用性声明生产路径。此结论限于这些已核验入口，不声称任何程序日志都无法提供环境事实。

若仅把 plan 新增字段写到 EXECUTION，准入只能在“全部拒绝”与“把模型材料当已证”之间选，两者都不符合本批目标。因此暂停该项选择，不填虚构引用，也不恢复 legacy fallback。

## 候选接线（尚未决定，不是实施承诺）

沿用已有人工决定权限，增加显式、结构化的环境可用性声明；记录具体验收项、环境身份、责任人、适用执行方式、声明内容和材料引用，并由既有 ledger owner 固定 ref/SHA。它只证明 operator 声明，不证明测试执行成功，不增加网络/tool grant，也不能满足本来要求机器执行的最终验收。

不强制目标阶段就声明齐备：详设 §3 已允许 GOAL 中环境未知，应允许计划明确后、执行批准前补齐。最初异步问题提出“在目标审批中增加”过窄，本候选对时点作此修正。究竟由哪个已有人工入口承接，以及缺少声明时如何到达该入口，须明确后再接编译/消费；不能制造一个没有合法补齐途径的前置条件。

替代方向是先选具体已有程序环境事实生产者，核验其真实字段与适用性后复用；不能把 doctor 的客户端版本检查泛化为任意项目测试环境可用。无证据时不默认创建新 runner 或环境平台。

## 仍需完成，未因上述待决项消失

EXECUTION 真实编译与两 reviewed 事实强制消费、当前 occurrence/epoch 解析、同授权技术修订、运行 identity 装配和隐式构造删除、R01–R03 不可变准备/意图/完成与 D07 裁定、所有查询/补证据/交付会话入口、四变体及 inline/worker 的合法与拒绝序列、原测试 fixture 迁移、整链失败切面和全模块回归、批末独立评审。当前修改不满足开跑/合入条件。

六维上限：Declared 存在；部分本地切片 Proven；全链 Consumed/Enforced before/Failure route/Cannot-bypass 尚未全部成立。W03/W04/W05/W08 继续未关闭，真实运行 NO-GO 不变。

## 2026-10-06 更新：接线决定已形成，尚未实施

以上为 9 月 30 日中间状态，保留其时点与证据。用户带回的 CC 独立评审支持先补 D05 决定、独立迁移切片继续；补充 LOW-1（身份/dispatch/合法补齐路径）和 LOW-2（具名程序事实正向清单）。用户随后要求进入下一步，本轮完成 [D05 事前可行性接线决定](d05-readiness-wiring.md)，并同步详设 §3.2、冻结附录 §5 和主清单 §12。

当前决定：复用有适用性与生产者证明的程序事实；必要时沿现有 review 面登记 operator readiness。新材料由 runtime 核验、ledger L04 固定，影响执行资格时与 L06 关闭同次提交；编译/批准/准备消费同一引用。缺项沿既有 plan gate 或 readiness recovery 补齐，语义变化回作者/复评；不依赖未来 EXECUTION、不把声明当实际执行、不新增通用环境认证。具体拟议 CLI/API、字段、环境可观察边界、复用/撤回/替换及失败写序见决定。

新增设计尚未独立复审，随 B03 批末合并；本轮不编码、不跑测试/native/provider、不启动 agent、不提交。旧 12 项仅为既有本地切片证据，不因本补记新增 Proven。本目录 verification.json 为开工前历史快照，不代表当前状态；ledger-sequences-recheck-reports 包含非本次执行的其他 XML，12 项以 ledger-sequences-recheck.log 指定三类及对应 XML 为准，不汇总整个目录冒称本轮执行。

后续按既有联合范围继续：测试 fixture/构造迁移和适用本地协议切片可推进；EXECUTION 编译、资格/epoch 与相关 D07 裁定仍须联合接线，不因本地存储可独立测试就称全部恢复工作独立。原“仍需完成”清单一项不减，加本决定的正反与失败序列；整链及全模块回归、批末独立判断前，B03 仍 IN_PROGRESS、真实运行 NO-GO。

## 2026-10-06 后续：联合实施已推进，B03 仍 IN_PROGRESS

用户在接线决定后批准继续。本轮新增 readiness 人工登记及 L04 保存、plan 缺项路由、EXECUTION 编译/批准/准备消费，并修复实际续作中暴露的 reviewed 事实整数表示误判与批准页误用计划 iteration 两项迁移缺陷。完整范围、transition、失败记录、六维与剩余项见 [本轮检查点](implementation-20261006/checkpoint.md)。前一节“尚未实施”保留为当时时点，不能再作为本轮末状态。

本轮全模块生产编译通过；core 123 / ledger 151 Maven 测试通过；runtime 定向 13 项经选定源码编译/JUnit 通过，包含从 fixture-approved GOAL 到补齐、作者/独立复评、EXECUTION 批准及实现的离线序列。旧 runtime 测试迁移未完成，clean test-compile 失败记录保留，**全模块测试仍未通过**。详细计数与边界见 implementation-20261006/verification.json；不挪用 B01/B02 历史绿色，不称真实模型验证。

剩余工作不减：程序工具事实、readiness recovery 返修/同授权补充引用、当前 occurrence/epoch、R01–R03/外部未知裁定、运行身份/隐式构造/全读写会话、完整测试迁移和兄弟整链。CLI/API 新入口虽编译通过，实际 factory 的运行身份装配仍待完成，当前工作区不可作为可运行交付物。未提交、零真实调用、零额外 agent；批末 CC 条件尚未满足，既有实施授权继续有效，NO-GO 不变。

## 2026-10-06 本次续作：运行身份、执行协议、返修与消费者

此前“旧测试未迁/身份及 R01–R03 尚未完成”的段落是历史中间状态。当前实现与逐项证据以 [continuation 检查点](continuation-20261006/checkpoint.md) 和其 verification.json 为准。本轮完成 Dual Java 运行内容身份装配、L00/唯一 claim、R01–R03 固定准备/意图/完成与本地恢复、UNKNOWN 明确裁定、readiness 失败回作者/复评、合同/查询/补证据/交付消费者及测试迁移。已删除无调用的 mutable-run 猜测恢复路径，未恢复旧合同 fallback。当前全模块离线覆盖为 1,604 项、零失败/错误/跳过（完整扫描 + 定点修复和 CLI/server 补跑，生产源码相同；不是单次全绿），具体逐 suite 证据与来源见该检查点。新 CLI/server 包的独立 JVM factory/L00 跨入口读取通过；不是运行服务/真实 provider 证明。不引用 B01/B02 旧数字。

B03 **继续 IN_PROGRESS**。仍需用户确认的 D05 内部工具执行证据语义见 [具体事实与取舍](continuation-20261006/d05-evidence-decision.md)：原生适配器的部分 exit 字段是合成成功/失败状态；不能冒充真实子进程退出码。依赖它的程序事实复用及机器验收消费未完成。运行身份也不冒称已覆盖全部安装 native/agent command/配置；D04 原生权限桥接没有因本轮本地协议自动完成。W03/W04/W05/W08 未关闭，真实运行 NO-GO、未提交及零额外 agent 不变。独立 CC 应审查已实施完整链及这些未竟项，不能用全绿或作者自查替代批次完成判断。


## 2026-10-07 更新：逐项证据与精确权限关联

上节“D05 待用户确认”已被用户决定及 10 月 6 日接线取代，保留历史时点。当前已批准证据逐项检查，实际退出码仅在明确要求时强制；本轮补专门“弱化要求→作者修订→独立复评→新批准→合法执行”序列。精确 permission signal/固定工具观察、程序非必需评估、合同声明及审批/报告同源投影已实施；不能据此声明整个 D02/W02/B03 完成。实际状态、完整链和验证以 [本轮检查点](boundaries-20261007/checkpoint.md) 为准。

实际 native/adapter/有效配置身份仍未完整接入 R01/dispatch/recovery，已核对 acpx 现有生产入口作为复用候选，不另造 Java 包解析器。旧 smoke 迁移或退役及活动引用、原清单其他义务仍待完成。B03 IN_PROGRESS、真实 NO-GO 保持；完整 D04 C/X 桥接不自动扩入。此次未提交、未运行原生/provider、未启动额外 agent，源码增量与此前 dirty 内容分开记录。


## 2026-10-07 主流程优先处置

用户接受当前全量声明规则作为首版限制，只有局部声明频繁造成真实合法阻塞才重新评估精细支持；不把“80%”写成已测成功率。前轮 CC LOW-1 的重复 RPC / 多请求分项决定已新增离线协议测试；LOW-2 的限制与出口证据已登记主清单 §14。运行中补全声明的入口未被证明，不借已有退出码重批序列冒称已闭合。

旧 smoke 显式退役并清理活动使用指南，保留历史字节及合法 intake emitter 测试。实际 native/config identity producer 仍缺，继续工作必须以真实 config/launch owner 的具体增量为依据，不能由版本常量或 Java 第二解析器代替。材料见 [检查点](mainline-20261007/checkpoint.md)，B03 保持 IN_PROGRESS；无真实运行或提交。

本轮最终单轮 clean test 通过：1,631 tests / 198 suites，零失败/错误/跳过；637 个构建/测试输入前后相同，8 项旧入口退役检查通过。原生身份 producer 未接，不据此收口 B03；本次 LOW 与策略处置纳入批次合并判断。


## 2026-10-07 W03 接线前：实际身份生产与装配选择

已完成[具名入口核对及 7 项离线原 resolver 检查](native-identity-20261007/assessment.md)。没有产品/patch/安装/配置改动、native/provider 或额外 agent。现有默认 npx 与环境脚本启动不能直接消费 builtin installed 内容身份；不能用 launcher/config 常量补成实际 adapter/native 同版。

B03 仍 IN_PROGRESS。停止受影响实现并交具体取舍：推荐固定本地安装和直接启动，保留现有 GLM 环境加载及客户端分工；支持范围与成本尚需用户确认。若保留全部动态启动，则要么显式收窄同版保证，要么承担额外解析/握手成本，不能擅自选。完整候选生产/绑定/dispatch/活连接/恢复/投影/回归工作包在报告中，未称为可开工字段规格。

真实运行 NO-GO；当前不是 B03 合并收口评审材料，旧测试绿灯不挪用。无须单独重审两项已完成文档 LOW，也不把本次检查点变为 D04 底层安全实验。

## 2026-10-07 当前：用户确认按兼容接口继续，W03 接线已实施

用户已同意环境变量及既有入口使用，客户端版本变化不单独阻断，具体不兼容发生后处理。这一决定替代上节固定安装/完整 native 字节保证的未实施候选；Dual 自身代码/批准/scope/证据/UNKNOWN 禁止重发继续严格。当前规范为详设 §7.4、主清单 §16。

已将程序选择的 invocation 接到 R01、命令启动前核对与精确 stop 消费；原生会话上次握手通过既有 sessions show 返回，白名单事实随每命令结果固定，版本缺失明确 UNKNOWN，不假测量。首轮、rebuild、correction 均核对记录身份/schema/open/协议，错误形成 adapter_incompatible 到唯一判定与 RecoveryAssessment。上次握手不是 live 实际引擎/包内容证明，未来实际接口不兼容仍须定位；D05 明确 exit 要求不降低。

离线合法/拒绝/记录失败与恢复序列已过；普通发送拒绝 released 执行者，合法 cleanup 在 policy 失效后仍可达。整链测试同时发现并修复完成写入丢 native 诊断与 pre-prompt 失败虚构 transcript 引用。首次全模块运行因兄弟自查发现 cleanup 错误而主动终止，登记 INVALID、不作为测试总数；其真实 fixture 迁移失败也单列，不挪用残留 XML。最终新源码单轮回归与独立判断见 [本轮检查点](compatibility-20261007/checkpoint.md) 及 [B03 合并评审索引](b03-review-index.md)。

B03 保持 IN_PROGRESS 待合并独立判断，原 W 项和真实 NO-GO 不自动关闭；零 native/provider/额外 agent、无提交。完整 C/X/真实客户端/浏览器/目标 Git 交付不扩入本批。不为本补记再开材料评审，不恢复历史样本。
