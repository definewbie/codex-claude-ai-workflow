# 读写入口对账（设计意见，不是实施授权）

原 116 候选逐项处置，补充 26 个关键兄弟入口，共 142 文件。类别表示协议归属，不等于批准修改；词法方法锚点非 AST 或穷尽调用图。

| 文件 | 类别 | 处置 |
|---|---|---|
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTransport.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxPermissionProfile.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxProviderMetadata.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxRunRequest.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxSessionLifecycle.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxStreamWireLine.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxUsageReader.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AgentPromptRenderer.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AgentRunMetadataWriter.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/ClaudeExecutionPolicy.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/ContextGovernanceActualReportWriter.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/PromptManifestWriter.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/ReviewedAcpxBuildIdentity.java | NO_PROTOCOL_CHANGE | 安装包 build-info 是独立版本输入，不进入 ledger 多文件提交；D07 在 run/实例固定其校验结果，D04 要求的 provider/native 身份另外完整记录。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/SessionCompactionSignal.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/WorkstreamMemoryUpdater.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-adapter-git/src/main/java/dev/dual/adapter/git/GitPatchMergeService.java | DELIVERY_EXTERNAL | ledger prepared 与目标 Git 分边界；固定 target common-dir 锁/意图/观测，不能把 Git 副作用放进本地提交重放。 |
| dual-cli/src/main/java/dev/dual/cli/AutoRunCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/CalibrationBillingCsvCommand.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-cli/src/main/java/dev/dual/cli/CalibrationDecideCommand.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-cli/src/main/java/dev/dual/cli/CalibrationListCommand.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-cli/src/main/java/dev/dual/cli/CalibrationPlanCommand.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-cli/src/main/java/dev/dual/cli/CalibrationPromoteCommand.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-cli/src/main/java/dev/dual/cli/CalibrationShowCommand.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-cli/src/main/java/dev/dual/cli/CliLedgerFactory.java | KERNEL_LIFECYCLE | 记录 configured/effective kernel；与 FlowKernelProvider 的空配置 LiteFlow 选择一致，核验 holder ClassLoader/code-source；并非单独增加一把实例锁。 |
| dual-cli/src/main/java/dev/dual/cli/DoctorCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/EventsCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/InspectCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ProjectionsRefreshCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryAbortCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryApproveWebSearchCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryCloseMistakenIterationCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryNetworkCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryReplayClassificationCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryShowCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RecoveryStaleCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ResumeCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RetryCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewApproveCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewCompleteCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewDecideCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewPacketCliHints.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewRequestActionCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewReviseCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/ReviewShowCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RunCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/RunMetadataReader.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-cli/src/main/java/dev/dual/cli/RunUsageProjection.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-cli/src/main/java/dev/dual/cli/SessionsReapCommand.java | RUN_OWNER | terminal snapshot 只决定清理资格；关闭 session 是外部副作用，先记录精确 session/binding 身份，不在读会话或 pending 重放中执行；未知关闭保留诊断。 |
| dual-cli/src/main/java/dev/dual/cli/StartCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/StatusCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/SummaryCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/TimelineCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/TraceCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/WatchCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-cli/src/main/java/dev/dual/cli/WorkerRunOnceCommand.java | CLI_SESSION | 读取命令把一次查询包在一个会话；动作命令调用写会话应用入口，移除重复外层获取；长调用/等待不持锁。纯 run 日志单独显示，不与 ledger 拼成已确认完成。 |
| dual-core/src/main/java/dev/dual/core/contract/TaskContract.java | CONTRACT_SEMANTICS | 扩展既有 typed 合同/动作/阶段事实；没有文件 I/O，会话由边界调用者持有；不新增 model 层。 |
| dual-core/src/main/java/dev/dual/core/contract/TaskContractValidator.java | CONTRACT_SEMANTICS | 扩展既有 typed 合同/动作/阶段事实；没有文件 I/O，会话由边界调用者持有；不新增 model 层。 |
| dual-core/src/main/java/dev/dual/core/execution/AdapterSignal.java | CONTRACT_SEMANTICS | 扩展既有 typed 合同/动作/阶段事实；没有文件 I/O，会话由边界调用者持有；不新增 model 层。 |
| dual-core/src/main/java/dev/dual/core/execution/AgentExecutionEvidence.java | CONTRACT_SEMANTICS | 扩展既有 typed 合同/动作/阶段事实；没有文件 I/O，会话由边界调用者持有；不新增 model 层。 |
| dual-core/src/main/java/dev/dual/core/execution/AgentExecutionOutcomeResolver.java | CONTRACT_SEMANTICS | 扩展既有 typed 合同/动作/阶段事实；没有文件 I/O，会话由边界调用者持有；不新增 model 层。 |
| dual-core/src/main/java/dev/dual/core/provider/ProviderPricingCatalog.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-core/src/main/java/dev/dual/core/workflow/HumanGateActionSemantics.java | CONTRACT_SEMANTICS | 扩展既有 typed 合同/动作/阶段事实；没有文件 I/O，会话由边界调用者持有；不新增 model 层。 |
| dual-flow-liteflow/src/main/java/dev/dual/flow/liteflow/FlowKernelProvider.java | KERNEL_LIFECYCLE | 只在原模块增加覆盖 holder 全生命周期的同进程互斥；同一 JVM 禁止 holder 类重复加载，入口核验 classloader/code-source 身份。 |
| dual-flow-liteflow/src/main/java/dev/dual/flow/liteflow/LiteFlowPrototypeKernel.java | KERNEL_LIFECYCLE | 只在原模块增加覆盖 holder 全生命周期的同进程互斥；同一 JVM 禁止 holder 类重复加载，入口核验 classloader/code-source 身份。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/AgentTaskLedgerWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/AtomicJsonWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/CompletionAuditWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/DeliveryCandidateWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/DeliveryMergePlanWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/EdgeConditionModule.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/EvidenceCollectionBaseline.java | CONTRACT_MIGRATION | 当前 constructor 把 contractRef 限死为稳定 task-contract.json；改为当前 EXECUTION occurrence 引用及 SHA/phase/epoch 校验，与 EvidenceCollection.verifySource 共同迁移，不允许旧 latest fallback。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/ExecutionLedgerWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/ExecutionNetworkGrantStore.java | PREPARED_WRITER | approve/cancel/claim 变更 workstream 内 grant 与 node 引用，纳入 L06/L02 写集及会话；移除内部无参 ledger 造成的独立读路径，保留现有网络授权语义，不重开网络实验。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/FailureSummaryWriter.java | DERIVED_PROJECTION | 统一 committed read session 生成，带来源 sequence；投影写失败不逆转 authoritative commit，不得供 qualification 当授权事实。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/FileLedgerStore.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/GoalSatisfactionWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/HumanActionRequest.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/LedgerEventLog.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/LedgerMigrator.java | LEGACY_REFUSE | 新协议不迁移旧样本；隔离旧迁移入口，不得绕过新版本/冻结身份校验为旧 ledger 赋权。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/MetricsProjectionWriter.java | DERIVED_PROJECTION | 统一 committed read session 生成，带来源 sequence；投影写失败不逆转 authoritative commit，不得供 qualification 当授权事实。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/NodeStateWriter.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/OperatorWorkspaceRegistry.java | REGISTRY_BOUNDARY | 跨 workstream 的导航索引，不参与单 workstream prepared；只在初始化提交后登记，指向未完成实例只显示诊断，不产生运行资格。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/RecoveryAssessment.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/RevisionTracker.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/RoutingEngine.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/ScopeRevisionRequestGuard.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/TimelineProjectionWriter.java | DERIVED_PROJECTION | 统一 committed read session 生成，带来源 sequence；投影写失败不逆转 authoritative commit，不得供 qualification 当授权事实。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowFileLedger.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkstreamAttentionEvaluator.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkstreamLock.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkstreamPaths.java | PREPARED_WRITER | 现有领域 writer 先规划确定 after-state，由唯一 ledger 写会话提交；原子 writer 禁止非原子 fallback；禁止公共入口绕会话直接写。 |
| dual-runtime/src/main/java/dev/dual/runtime/AgentWorker.java | M6_BRIDGE | queue/claim/complete 的 ledger 写集与独立 run 意图/完成引用桥接，完成路由同次提交；进程结果未知禁止盲重放。 |
| dual-runtime/src/main/java/dev/dual/runtime/StaleRunningRecovery.java | M6_BRIDGE | queue/claim/complete 的 ledger 写集与独立 run 意图/完成引用桥接，完成路由同次提交；进程结果未知禁止盲重放。 |
| dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java | M6_BRIDGE | queue/claim/complete 的 ledger 写集与独立 run 意图/完成引用桥接，完成路由同次提交；进程结果未知禁止盲重放。 |
| dual-runtime/src/main/java/dev/dual/runtime/delivery/AcceptedDeliveryCandidateResolver.java | DELIVERY_EXTERNAL | ledger prepared 与目标 Git 分边界；固定 target common-dir 锁/意图/观测，不能把 Git 副作用放进本地提交重放。 |
| dual-runtime/src/main/java/dev/dual/runtime/delivery/DeliveryApplicationService.java | DELIVERY_EXTERNAL | ledger prepared 与目标 Git 分边界；固定 target common-dir 锁/意图/观测，不能把 Git 副作用放进本地提交重放。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentExecutionAttemptReconciler.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentOutputContractDocument.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentOutputContractSidecar.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentOutputLineageVerifier.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultEnvelope.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultEnvelopeParser.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultEnvelopeValidator.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultPromptContract.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AgentRunMetadataFinalizer.java | RUN_OWNER | run/adapter/prompt 的独立 owner；不将可变运行文件放入 ledger 写集。独立原子写+不可变完成引用；跨 ledger 读取得快照并在提交时重核。D02/D04/D07 逐入口落点见附录。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactContractValidator.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactValidateHandler.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AuthoredExecutionContext.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AuthoredRevisionValidator.java | CONTRACT_MIGRATION | 严格保留 obligation_id/disposition/evidence 语义，无新增文件会话；与上下文 snapshot 同一待回应义务集合。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/AuthoredStageLineageResolver.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ChangeScopeEvaluationArtifact.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/CompletionEvidenceLineageResolver.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ContextBuildPackageHandler.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ContextPackageSectionRenderer.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ContextStateEvidenceBoundaryRenderer.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ContractCompileHandler.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRegistry.java | CONTRACT_MIGRATION | 同步四变体 phase、产物 schema 和 required refs；small 补 plan reviewer/decision 合同，不另建 workflow 定义。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/DeliveryPrepareHandler.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/DesignScopeValidator.java | CONTRACT_MIGRATION | GOAL 阶段 planned_files 是拟议范围：检查 canonical/禁区/操作和目标约束，不要求属于空 included；EXECUTION 后才强制批准子集。只读 dispatch 仍不能写提案路径。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceManifest.java | DERIVED_PROJECTION | 统一 committed read session 生成，带来源 sequence；投影写失败不逆转 authoritative commit，不得供 qualification 当授权事实。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ExternalVerificationEvidence.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/HumanReviewGateHandler.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/PlanArtifactDocument.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/RepoScopeGovernance.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ReportGenerateFinalHandler.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/RepositoryFactBinding.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/ReviewDecisionHandler.java | CONTRACT_MIGRATION | READY 固化真实 reviewed_candidate，持久化引用及路由属于 L03，不写人工 approved。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/StageHandoffDocument.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractArtifactWriter.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractProjectionVerifier.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractProposalReference.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractProposalValidator.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractRevisionContextResolver.java | SNAPSHOT_CONSUMER | 解析/验证/上下文需消费统一 ledger snapshot；不可变产物在固定引用下校验 SHA。若产物由本类发布，先存不可变字节，再将引用加入所属完成写集；不把 projection 作为授权源。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/TokenEstimator.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-runtime/src/main/java/dev/dual/runtime/handler/WorkstreamMemoryProjection.java | DERIVED_PROJECTION | 统一 committed read session 生成，带来源 sequence；投影写失败不逆转 authoritative commit，不得供 qualification 当授权事实。 |
| dual-runtime/src/main/java/dev/dual/runtime/lifecycle/WorkflowDefinitionLoader.java | NO_PROTOCOL_CHANGE | definition 的静态文件解析不进入 prepared 写集；仍参与 D03 phase/四变体校验及 D07 冻结身份，不能据本分类宣称无需产品改动。 |
| dual-server/src/main/java/dev/dual/server/ServerLedgerFactory.java | KERNEL_LIFECYCLE | 记录 configured/effective kernel；与 FlowKernelProvider 的空配置 LiteFlow 选择一致，核验 holder ClassLoader/code-source；并非单独增加一把实例锁。 |
| dual-server/src/main/java/dev/dual/server/WorkspaceBrowseController.java | NO_PROTOCOL_CHANGE | 命中为独立校准/文件浏览/静态配置、版本或缓存读取，不拥有 workflow 多文件提交；其输入若来自 ledger，调用方必须先提供已提交快照。保留当前功能，不迁移其私有存储。 |
| dual-server/src/main/java/dev/dual/server/WorkstreamActionService.java | API_MUTATION | 改用应用入口读写会话；移除当前覆盖长 provider 调用的外层锁。身份/epoch 在完成会话重核。 |
| dual-server/src/main/java/dev/dual/server/WorkstreamQueryService.java | API_READ_SESSION | 每个响应同一 committed snapshot；list 每 workstream 独立状态，可 BUSY/pending，不混代拼装。 |
