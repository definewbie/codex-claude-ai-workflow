# B03 合并批末评审入口（2026-10-07）

当前 B03 **CLOSED / PASS**：已收到用户带回的合并批末 CC 独立判断，见 [收口处置](closure-20261007/disposition.md)。本索引仍保留受审对象与证据导航，不代替原评审。用户授权联合合同生产/消费、准备/执行/恢复与剩余迁移；当前原生入口语义以主清单 §16、详设 §7.4 的最新用户决定为准。版本不同/未知不单独拒绝，不承诺具体版本区间。真实运行 NO-GO；无提交或启动额外 agent 授权。

## 评审对象与当前证据

审查 B03 的联合链，不能仅给最后 W03 类/字段 PASS。开工目标及全部 transition 为 [batch.md](batch.md)，当前取舍/历史进展为 [progress-and-decision](progress-and-decision.md)，最后接线、六维与失败前缀为 [当前检查点](compatibility-20261007/checkpoint.md)。旧段落是当时状态；以最新规范/源码核对，不把过去的未竟项逐字重开，也不把历史绿色当当前源码证明。

最终全模块凭证只取 [verification.json](compatibility-20261007/verification.json)、reactor-clean.log、同目录 reactor-reports/ 原 XML 和前后内容身份。所有中间失败/INVALID 保留，不拼接为单轮。协议/模型消息合成，Java/本地 Git/文件和独立 JVM 是真实离线执行；零 acpx/native/provider，不证明真实原生审批能力。

## 联合范围与导航（请独立重建，表格不是评审答案）

| 用户链 | 现有 owner / 当前实施入口 | 序列证据入口 | 不可外推 |
|---|---|---|---|
| 目标→GOAL→作者/独立复评→EXECUTION 精确批准 | TaskContract、compile/projection verifier、ReviewDecisionHandler、AuthoredStageLineageResolver、四 definition | PhasedContractContinuation、ReadinessRoutingDefinition、AuthoredStage、HumanRequirementContinuation；implementation/continuation 检查点 | reviewer READY 不代人批准，模型/内部子任务不产 authority |
| 准备→唯一执行→固定结果→路由 | WorkflowFileLedger 会话/claim、AgentRunCommitProtocol、runtime/worker、AgentExecutionCompletion | ExecutionClaimSequence、AgentRunCommitProtocol、WorkflowFirstLifecycle、AcpxNativeCommandSequence | R02 不产二次发送资格，mutable run 不是完成 SoT |
| 本地写间中断→同版续完 | FileLedgerStore/AtomicJsonWriter/WorkstreamLock/LedgerSession | WorkflowCommitSequence、LedgerSession、冷 JVM/pending/并发 reader/append 前缀族 | 不重算 route/event，不重跑 provider；旧/错 binary INVALID |
| 必需验证可行→实际观察→逐项自测/独立验收→补证据或返修 | VerificationReadiness/Evidence、ExternalVerificationEvidence、completion lineage/contract consumers | VerificationEvidenceSequence、Readiness/AcceptanceRepairSequence/Interruption、退出码弱化重批专门序列 | 逐项证据要求，声明/模型断言不升格程序观察；explicit actual exit 不放宽 |
| 权限拒绝→精确非必需证明或停止 | projector/OperationRef→PermissionEvidence→唯一 outcome→RecoveryAssessment | 同 turn A allow/B deny、重复/重用 id、完整声明/独立完成正向与局部拒绝；boundaries/mainline 检查点 | C/X 实时回复桥接仍批外；运行中补局部声明入口未证明 |
| 原生入口→记录身份检查→prompt/correction/stop→冷读 | transport→R01/per-command→唯一 outcome/恢复；详设 §7.4 | AcpxCompatibilitySequence、NativeCommandSequence、真实 handler 三种失败与独立 JVM；最后检查点 | sessions show 是上次握手，不是 live queue 或原生字节冻结；版本范围/真实客户端 NOT RUN |
| 查询/报告/CLI/API/交付准备 | 显式装配 ledger/read session、唯一 projection/已批准材料消费者 | factory/LegacyContractConsumerRejection、当前验收报告/终态/同源投影等全模块 | 目标 Git 实际交付/未知外部窗口不以本批收口；B02 loader/JVM 部署限制仍在 |

## 增量来源与范围纪律

- [B03 累计源码/资源/支持脚本清单](b03-cumulative-changed-files.json) 与 [累计 patch](b03-cumulative.patch) 对实际 dirty 前态；不是相对 HEAD 的所有未提交改动。
- 初始 src 基线来自原 before-source-text/sha256；支持 POM/脚本缺初始文本时取最早实际切片 pre-state并逐条标明来源，不伪称 batch-start。源内容缺失的条目显式列出，不能靠重构冒充实测。
- 本次 18 文件增量有独立 before-workspace/changed-files/incremental.patch；前轮被评审切片的范围与失败证据保留各目录，不能因为某切片 PASS 外推本批。
- 定点必需决定（D05 producer、逐项 exit、首版全量声明、W03 按接口兼容性接入）由用户或已批准方案边界支持，合并本次受影响评审，无额外材料轮次。

## 剩余边界，不能由本次评审消失

W03/W04/W05/W08 等仍是总体工作，不随本批自动关闭。完整 native permission C/X、真实客户端/工具能力、真实 E2E/Gate、浏览器 runtime、目标 Git 交付/最终用户批准、受限部署外的 holder 使用者与 kernel 内部 I/O 专项注入仍未证明或批外。首版全量声明规则按用户决定保留；具体运行中补声明入口未证，真实反复阻塞才重评。

如发现当前批次遗漏承诺、错误来源/失败处置或合法死端，应分级指出，不以“主流程优先”免除已批准验收；也不要把撤销的原生全量字节/沙箱目标重新加入。请同时评估正常流程可达与非法拒绝/放行，而非只看失败安全。
