# 验证边界与 sibling 对照

本批新增测试以现有 runtime/adapter 测试族承载，无新生产类型。完整测试数量和结果以 test-results.json 为准；本文件不替代 XML。

| 义务 | 证据/消费链 | 限制 |
|---|---|---|
| 分工输入实际出现 | AgentResultPromptContractTest.planPromptSeparatesRegisteredMethodFromCriterionDetails；VerificationReadinessTest.readinessContextSeparatesFixedMethodFromDetailsWithoutInventingReadiness；原实现上两项先红 | 证明完整契约渲染/context 生成，不证明真实模型遵守 |
| 返修、复评与实现仍收到细节 | PhasedContractContinuationTest.criterionDetailsReachReviewAndImplementationButMethodSuffixCannotAdvance，真实 runtime/ledger、固定输出、自原 gate revise 到新复评、compile、当前 EXECUTION approve、implement | provider 为测试输出；不是原生 Java 项目 E2E |
| 无声明仍有合法出口 | 同序列首轮进入 gate.plan；原 missingReadinessCanBeRecordedRevisedReviewedApprovedAndConsumed 的 approve/revise 两支 | 沿既有登记/返修入口；不授新权限 |
| method 相等保持严格 | usefulSuffixAndWhitespaceStillCannotChangeTheRegisteredMethod 的有益后缀/空格/换行；上述完整拒绝序列；源文件比较 | 不是语义相似性算法；真实当前计划未被写回 |
| 一次 correction 不增次 | AcpxAgentResultAcquirerTest.planCorrectionRetainsFixedMethodAndDetailResponsibilitiesWithoutExtraAttempts，初次坏 JSON→第二次好/仍坏；真实 acquirer 写 correction 输入 | ScriptedTransport；不是新模型请求；不把 method 身份失配当格式噪声自动纠正 |
| 错 ref、少 AC、伪登记、撤回、过期、环境漂移 | 原 VerificationReadinessTest、PhasedContractContinuationTest 相关用例重跑 | 旧机制回归，不冒称本批新增机制 |
| 批准、actual exit、证据隔离 | 原 PhasedContractContinuationTest.weakeningApprovedExitRequirementReturnsToAuthorReviewAndNewExecutionApproval、VerificationEvidenceSequenceTest 等全模块回归；生产消费者不改 | 程序不执行自然语言方案；语义充分性仍由验收负责 |
| R01/R03/写点与冷启动 | 原 AgentRunCommitProtocolTest、PhasedContractContinuationTest.coldJvmCompletesFixedPublicationWithoutProviderOrNewBytes、ContractQualificationSequenceTest 写点族等全模块回归 | 复用原固定字节协议，不新增写点、不外推全部故障注入 |
| inline/worker、四变体与终态 | 原 ContractQualificationSequenceTest/WorkflowFirstLifecycleSequenceTest 等常驻整链全模块回归 | 不把合成输出称为真实客户端或目标 Git 交付 |
| 守卫不能绕过 | 当前正常类路径先通过；隔离副本中移提示/改 equals 为 startsWith，分别出现目标 AssertionFailedError | counterproof-result.json 单列，不计入 Maven 绿灯；未修改工作区生产文件 |
| 历史形态对照 | ReadOnlyMethodProof：原 GOAL/声明/计划只读；隔离新 fixture 重绑 GOAL/环境/登记 refs，真实 validateBindings 对五种旧 method 分别拒绝，纯原文组合通过 | 仅真实文本 + 隔离身份的消费证明；不证明原 case 可恢复，不产生新 authority |
| 发行与现场保全 | release-identity.json、preservation-result.json | 当前组合的原生兼容性、真实模型输出、E2E、预算续作尚未验证/授权 |

现有 sibling 共用 GOAL readiness context（作者与 reviewer）及 plan 契约渲染（含格式修正）。design/intake 不增加 plan 字段义务。runtime/worker/CLI/API 继续消费原领域检查，本批不增加旁路。

成熟度：新输入投影 OFFLINE_VALIDATED/PROVEN；严格资格消费通过离线正反序列证明；真实模型遵守与完整 OKX E2E NOT_RUN。不把输入投影当作模型行为的硬保证。
