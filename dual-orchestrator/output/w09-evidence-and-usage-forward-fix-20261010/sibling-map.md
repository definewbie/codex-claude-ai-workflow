# 受影响兄弟与消费地图

| 边界 | producer → 唯一consumer | 前置/拒绝与证据 |
|---|---|---|
| 产品引用 | 原repo-map/RepositoryFactBinding → TaskContractProposalValidator → ArtifactContractValidator → 唯一outcome | 非空inventory仍必须tracked fact；空inventory可空；.agent/.git拒绝。生产validator/核心canonical源码与批前相同 |
| 模型提示 | template + AgentResultPromptContract → renderer/acquirer correction → typed proposal | 初始/修订patch/复评/格式修正同规则；实际渲染成对与correction实际输入断言；不将prompt称为硬防护 |
| 活completion身份 | AgentLifecycleEventReader当前baseline → AcpxSessionStreamProjector → AcpxPromptCompletion → handler | 当前请求的ACP session，不是engine内部ID，不复制recordId；原流baseline、必需身份校验不改 |
| 单次usage | record stream + completion(session,request) → AcpxUsageReader | 同session多个prompt、异session同id、重复同session/id、无completion、延迟/缺usage；零latest/text fallback |
| 聚合与持久化 | 每次AcpxRunResult/reader usage → handler/acquisition → run/R03/L03 → 原MetricsProjectionWriter | 只有完整可加总双occurrence才MULTI_PROMPT_SUM；部分不是总量；PROVIDER_RUN_USAGE/SNAPSHOT/UNAVAILABLE分类型，原写序/D06/D07不改 |
| 恢复/预算 | 固定run/唯一outcome/metrics → 原恢复投影及Gate分段核验 | 不以usage缺失推零执行；不授新retry/dispatch；未知归因止新Gate调用，由用户决定fresh |

支持范围：本批新binary/fresh实例；旧两个binary绑定失败实例不恢复或用新binary续作。旧EXACT_PROMPT_MATCH字符串仅在历史或显式typed测试transport里，不是生产reader兼容路径。
