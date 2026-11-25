# B03：逐项验证证据接线检查点

状态：IN_PROGRESS。本文记录用户确认“实际退出码按验收项要求”后的实现增量；不是 B03 整批收口，不解除真实运行 NO-GO。沿现有 TaskContract、AgentResultEnvelope、AgentExecutionEvidence、R01/R02/R03 和证据消费者接线，没有新 runner/DSL/权限平台，没有额外 agent、native/provider 调用或 Git 提交。

## 对总体目标的贡献

合法研发流程不再因缺少**未要求**的退出码被统一拒绝；要求真实退出码的验收继续严格拒绝原生合成值。程序记录工具观察，模型只能提交逐项断言和材料引用；独立 reviewer 仍负责判断这些结果是否实际满足验收要求。程序来源校验不能证明任意自然语言断言为真，本轮没有实现这种语义裁判。

当前 scope 是本次已批准语义所依赖的生产—固定—消费链及必要兄弟路径。它推进 W04/D05 与 W05 的接口，但不能代替 W02 的权限精确关联、W03 的实际原生配置身份或 W08 整体完成。

## 已实施

- verification binding 显式记录 actual_exit_required 与可选 expected_exit_code；缺声明不按 false 解释，非机器执行证据不能要求进程退出码。计划/编译/资格共用校验；要求改变参与授权边界比较，不能结果出来后静默删掉。
- ACP 当前 prompt/session 的 tool_call/update 汇成程序观察，保留输入、结果、原始更新、原生状态、reported exit、实际 exit 及具名来源。仅已核源码的 Claude bash_code_execution_result.return_code 分支标为实际值，普通 terminal_exit 保留 reported。历史、错 session、无创建、冲突终态、缺结果不充本次完成事实；流式输出保留。
- R01 固定批准合同与源码基准；R02 后在现有 run 锁下固定 verification-observations.json；R03 固定其引用/SHA 与结果。模型 verification_checks 只提供 criterion、assertion、execution_ref、tool_call_ids、material_refs 候选，不能生产观察。
- 未要求 exit 的项目核对实际输入/结果、身份、批准合同、材料 SHA 和源码基准。要求 exit 的项目还须真实执行结果及期望值；读取报告没有进程退出码，可与执行证据一起出现，但不能替代执行证据。空 tool_call_ids 选择该固定执行的全部有效结果，不代表免证据；需要消除多工具歧义时显式选定调用。
- 自测成功、ACCEPT、ACCEPT_WITH_NOTES 均消费相同规则；context 准备、agent 准备和每个非清理原生命令意图前重核实际 routed 自测。报告/交付准备继续由 CompletionEvidenceLineageResolver 核对已固定完成。历史显示不会因今天源码变化被抹掉。
- 发布计划保存候选 ref/SHA；恢复发布固定字节前重新核对候选和适用源码。漂移时阻止发布，不覆写原执行判定、不重跑 provider。已冻结交付 candidate 的后续消费者保持其既有身份规则，不把任意活工作区漂移扩成对冻结 candidate 的全量失效。
- sidecar/read model/context 携带逐项断言及执行材料引用，明确它仍需独立语义复评。旧测试中用伪造 self-test/ACCEPT 代表真实证明的正向夹具改为真实批准流程加离线 Java 文件观察；仅分类器测试明确局限为投影语义测试。

## 控制链、写入失败与兄弟检查

详见 transition-plan.md。R01→R02→观察固定→验证→固定 outcome/publication→sidecar→R03→L03 沿原 owner；不产生第二控制原因。观察写失败或无 R03 不授成功采纳；R02 后不明仍走 D07，不能从没有 exit 推导没有执行。已有固定 publication 只续完本地提交，源码/材料漂移须拒绝。新格式缺失字段不会静默作为旧协议成功材料。

| 六维 | 本次落点与证据边界 |
|---|---|
| Declared by | 用户逐项 exit 决定、D05/W04、D06/D07；不改变已批准任务标准 |
| Proven by | 逐项合同、ACP 协议切片、真实 Java 文件/进程观察的离线生命周期测试；不是 native/provider 能力实测 |
| Consumed by | compiler/qualification、VerificationEvidence、validator、ReviewDecisionHandler、context/dispatch、CompletionEvidenceLineageResolver、原 publication 恢复 |
| Enforced before | 批准/调用、成功发布、复评决定、报告与交付准备前；不声称阻止外部文件副作用 |
| Failure route | 缺/错证据保留 typed 失败；已固定执行不重判，恢复发布失败不重发外部调用；模型失败与本地 I/O 分开 |
| Cannot-bypass | 缺声明、合成 exit、错调用、源码/材料漂移、三处调用前漂移、固定发布重读、合法无 exit/同身份复用成对；既有 inline/worker/UNKNOWN/返修继续回归 |

## 测试及自查状态

本轮专门场景见 PhasedContractTest、AcpxToolObservationsTest、VerificationEvidenceSequenceTest、ExternalVerificationEvidenceTest 与 ContextBuildPackageHandlerTest。WorkflowFirstLifecycleSequenceTest、AuthoredReferenceCorrectionSequenceTest、RetryFailureContextSequenceTest 覆盖现有变体/双入口的合法返修与复评。VerificationEvidenceSequenceTest 的真实退出码正向来自本地 Java Process.waitFor 测得的退出码，原生协议测试使用合成固定消息；不能合并宣称某个真实客户端已提供该值。

专门的“弱化已批准退出码要求→重新批准门”整链用例尚未补齐：授权等价比较已包含两字段，通用返修/重新批准回归仍通过，但本轮不把静态接线当该场景的专门动态证明。

最终去重结果为 **1,617 tests / 197 suites / 0 failure / 0 error / 0 skipped**。证据来自 reactor-15 的全模块 clean test（其中服务器一个旧正向夹具失败，整次 exit 1）与 server-18 的定点复跑（runtime 3 个受影响类和 server 全部测试，exit 0），不是一次全绿 clean reactor。两次间仅更改两个 runtime 测试夹具、server 测试及 test-jar 依赖；所有生产源码/资源保持一致。634 个受测文件身份和逐 suite 来源均可复核。

服务器原来的手写成功样例保留为“摘要不能制造通过”反例，另用真实批准→程序文件观察→独立验收流程补正向 API/status 投影；未删掉正向通过义务。共享 fixture 的修订上下文读取改用传入账本，避免偷偷换默认测试身份。server-16/17 的迁移失败与 reactor-15 失败均保留，最终以 server-18 为准。过程日志包括旧夹具不满足新证据要求的失败；没有放宽生产校验去适配这些夹具。core-red.log/red-reports 是本轮显式退出码要求的先红证据，不冒称所有新增场景都做了旧代码反证。

离线打包成功；新 CLI 产物初始化 L00、新 server 产物读取、再由新 CLI 产物读取的三个独立 JVM 检查通过，运行身份一致（packaged-identity/results.json）。这仅证明实际 factory/发行工件的本地身份装配与读取，不证明全流程冷恢复或安装的 native/config 身份。git diff --check 通过。

本次相对真实 dirty 基线有 54 个文件增量（26 个生产源码/资源、28 个测试/构建文件，其中 4 个新增）。受测身份与增量相对本轮 before-source.json 的真实 dirty 内容；不把之前数百个未提交文件混成本轮改动，不把前轮 1,604 测试数字挪来证明新源码。最终数字、XML 和内容身份见 verification.json；合成协议证据与真实模型能力严格分开。

## 未竟项与下一段顺序

1. W02/D02 的 permission operation_ref → 独立义务完成证据 → PROVEN_NONESSENTIAL 尚未接通。现有 contained-permission 仍是旧逻辑，不能宣称已满足精确关联；本次工具观察仅是其生产基础。
2. W03/D07 的实际 acpx/adapter/有效配置身份仍未装配。ReviewedAcpxBuildIdentity 的声明常量不是安装内容测量；不以本轮记录当前工具观察代替启动/恢复的同版检查。
3. readiness 的机器事实可用性复用与 operator 声明仍有未完成部分。当前代码的实际观察复用针对固定执行的验证证据，不是通用环境认证，不能用程序读取文件冒充运行任意测试。
4. operator 事后补证据已补逐项消费正反序列：inline/worker 从实际批准 OPERATOR_DECLARATION_WITH_MATERIAL 的合同到 prepare/submit、自测和验收；声明与复制材料保持原类型，错引用/材料漂移拒绝。它仍不证明 operator 实际执行了所声明的测试。
5. 命令行 smoke 脚本仍依赖旧的纯模拟成功材料，尚未按新事实生产要求迁移，本轮未运行，不能把 Maven 全绿扩称所有 smoke 已过。W08 的 UI/report 全面同源审查、整批 B03 独立判断仍待完成；D04 全部 C/X 原生桥接按既有范围另行实施，不因此扩入本批。

下一段先完成精确 permission 关联与原生身份两个既定缺口，再作 B03 全清单收口；成本或 authority 超出已冻结方向时才上交用户取舍。本检查点可以受影响范围复审，但评审 PASS 不能自动关闭上述未竟项。本轮为作者自查，未冒称独立评审。
