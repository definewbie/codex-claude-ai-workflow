# 受影响兄弟入口核对

| 边界 | owner / 入口 | 处理与证明 |
|---|---|---|
| 必需 ACP 身份 | AcpxSessionCompatibility → AcpxAgentTransport.run | record/ACP/protocol/schema/name/canonical cwd/open 强制保留；缺席 engine ID 单独记录 |
| correction | runAgentResultCorrection → 同一 run/compatibility | 两客户端无 engine ID 正向；错误必需身份零 prompt |
| reconnect/rebuild/stop | AcpxAgentTransport → 原 command intent/result | 全模块沿无 engine ID 共享 sessions-show 夹具执行既有用例；不修改 generation、stop 或重试决策 |
| native permission | NativePermissionRequests / transport callback | 关联字段 requestKey/sessionId/promptId/ownerGeneration/rpcId/rawSha/deadline，均不消费 agentSessionId；本地 peer 改为合法缺席形态 |
| 失败摘要/动态摘要 | FailureSummaryWriter / WorkflowFileLedger.failureSummarySnapshot | 共用 render；存在 typed outcome 时 primaryReason 唯一，原 parse_error 降为次级诊断 |
| CLI/server/attention | RecoveryShow/Summary/Trace / WorkstreamQueryService / WorkstreamAttentionEvaluator | 既有消费者读取摘要或 RecoveryAssessment；无新增控制原因/权限。原历史报告不主动回写 |
| 固定完成/重启 | R03/L03 → 原 LedgerSession 与 RecoveryAssessment | 复用原协议，inline/worker 真持久化链 + 冷 JVM 重读；兼容性两写点失败仍零 prompt |

全仓 dual-*/src 搜索：生产中 agentSessionId 的直接消费者只有 AcpxSessionCompatibility。原生 extractor 标可选并对缺 meta 返回 undefined；没有证据要求 Dual 用这个内部 ID 路由。

上述本地 shell/协议/模拟 adapter 证明保持离线层级，不能外推为真实客户端完成主流程。生产拒绝规则仅在可选字段合法缺席处改变；有字段但类型非法/空串不声明为合法 producer 输出。
