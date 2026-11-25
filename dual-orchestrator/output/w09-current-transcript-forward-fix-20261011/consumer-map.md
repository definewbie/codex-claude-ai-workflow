# 唯一事实与兄弟消费者（本轮自查）

- 执行来源：AcpxAgentTransport 的实际进程 stdout 固定到 request.stdoutPath；AcpxAgentTaskHandler 使用本次上下文生成的 transcriptPath，correction 使用独立 correctionTranscriptPath。它不是任意全会话查询或模型正文。
- 协议语法唯一 owner：AcpxStreamWireLine。Reader 不二次解析原始 JSON；native 提供的 input/output/total 均须非负整型，optional cache 未提供可为0，已提供的畸形值不可隐去。总量消费实际 totalTokens，不推导统一计费加法公式。
- 领域/观测分离：AcpxUsageReader 只产生 typed usage，不决定业务 outcome；Handler 固定到各 occurrence 与原 run，总量仍由原 AcpxUsage.aggregatePromptOccurrences 强制完整、同来源才相加。
- 持久化：原 AgentRunCommitProtocol R03/L03，完成写失败不凭解析成功宣称固定完成；原冷启动续完只消费固定材料，不再次调用模型/重算全会话历史。
- 主调用/correction/两客户端共用四参 reader；旧三参入口已删除。合法无 result.sessionId 用实际 bounded completion.sessionId，绝不把请求号当全历史唯一键。
- 原生 transport completion 由当前 stdout/stderr 及调用前 shared-stream offset 的 EventReader 产生，沿 session/prompt/RPC 边界；本轮无 transport/acpx patch、permission lane、scope/验收/批准变更。
- 整会话日志：仅当前 transcript 无完成帧时沿既有唯一匹配/有界等待出口；其重复依然 UNAVAILABLE。完整当前记录即使共享历史重复，也不撤销本调用 token；货币仅严格同数值/唯一基准时可补充，固定记录不随今日原生文件重算金额。
- CLI/API/metrics：消费原固定 run/occurrence；不另加用量来源。旧四例继续保留其当时固定值，不用新 reader 重写。
- Gate operator observer：新副本核对 R03固定usage/acquisition及输出hash、run+occurrence、唯一当前日志及 processResult 路径，再按原预算规则判断；它不是新的运行 SoT/生产调度器。当前结果错误不得借共享 stream 放行；无当前完成帧时唯一延迟出口与生产 reader 同向。纯 stream-only 且跨代重用仍为已知未支持组合，真实发生时停止，不预建 B 案。

失败时点：transcript/metadata 读取失败→不可测；错/重/部分完成→UNAVAILABLE；R03写失败→原D06/D07；Gate不可核→零下一派发。都不从缺usage推零执行，不把预算失败改为模型失败。Definition、Instance、权限批准与终态规则保持原 owner。

新证据：常驻本族正反、Handler真实离线合同/两occurrence固定；既有AgentRunCommitProtocol/transport及全模块失败前缀/冷JVM/权限/验收序列由本轮回归承接。八个旧真实调用仅只读材料对照（6个当前日志、2个原严格共享出口），非新真实能力证明。新observer为运行候选，未启动第五例。
