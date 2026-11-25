# 本族消费者与未覆盖边界

本表为当前源码自查，不是独立评审或全五族覆盖。

| 消费者 | 本批携带与拒绝时点 | 未覆盖口径 |
|---|---|---|
| ReviewGateActions / executionCandidate | 当前 contract approve 的真实检查快照→L04；缺/错绑定零写 | revise/clarify/collect/submit 沿原守卫，不伪造批准时检查 |
| AgentRunGovernanceContext | 准备前实际 GOAL/EXECUTION/routed acceptance Failure→封闭原失败+pair | authored/verification evidence 等其它守卫仍按原 cause；无本族 check 不判矛盾 |
| AgentRunCommitProtocol | R01 后实际 dispatch 资格重核；复用当前 scoped ledger，不重新取 L | prepared hash、claim、原生命令配置等 D06/D04 守卫不冒成本族规则 |
| ContextBuildPackageHandler | 后继 context 的真实资格失败→原 cause+typed pair→L03；未产出上下文 | 两入口删除真实合同、固定失败、同字节恢复与独立验收正向已验证 |
| ArtifactValidateHandler / FailureClassifyHandler / ReportGenerateFinalHandler | 原资格拒绝 result 增加同一 typed 诊断；产物采集/分类发布/报告前 | 值携带由直接 sibling 调用证明，共享 L03 补齐由 D06 与整模块回归承载；不声称分别跑过三条原生 E2E |
| RoutingEngine / 直接 ledger 决定 | 实际参数核 gate/source/subject/action/runtime；直接未采集入口写 NOT_INVOKED | 直接入口不产生检查；零 fake PASSED，后继诊断 UNKNOWN |
| RecoveryAssessment / ContractQualificationRecovery | 核目标输入链、固定来源、gate/compiler、rule/version、原 code→唯一四分类 | 缺/错、I/O、未覆盖、不可比→UNKNOWN；不更改主原因或授许可 |
| CLI recovery / server query / attention / console | 原统一 read model 的分类、原因及建议投影 | 全套既有 CLI/server 测试与源码消费点核验；没有本次浏览器视觉证明 |
| HumanReviewGateHandler / ReviewDecisionHandler | 既有 gate 建立与 decision readiness 前置保留 | 此入口尚无 contract resolving source 时不虚构 L04；wrapper 失败不借未来批准解释矛盾 |
| ContractCompileHandler / VerificationReadiness.record / EvidenceCollection | 既有 compile/operator 声明/补证据准备守卫保留 | 不是新的 diagnostic decision producer；未获消费点证据的拒绝不提升成熟度 |
| AuthoredStageLineageResolver / TaskContractRevisionContextResolver / DesignScopeValidator / PlanScopeValidator / VerificationEvidence / PermissionEvidence / ExecutionRecoveryAdjudication / WorkstreamMemoryProjection | 原严校与委托消费者保留 | 逐规则采集不在首版；外壳、同 code 或借用本族调用不能称本族矛盾已证 |

程序工件的路径命中不证明写入者。本批不恢复已废弃的 sandbox/全目录防篡改目标；上述事实消费与诊断完整性不包装成模型写前的 OS 防护。
