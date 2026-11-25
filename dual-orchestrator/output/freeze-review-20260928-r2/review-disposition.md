# 冻结候选复审处置（2026-09-28）

依据用户提供的 [CC 原文](cc-review.txt)：PASS，D01/D02/D03/D05/D06/D07/D08/D09/D10 可冻结，2 LOW 随冻结采纳；D04 保留技术阻塞。本文件是原 [freeze-details](../freeze-review-20260928/freeze-details.md) 的两项规范性补记，与详细设计 §13/§15.1 一起消费；不另建工作清单。原包与 JSON 样例保留受审字节，当前三份文档的受审版本在 before/，可与原 review-package.json 对照。

原文部分表格/句子存在截断；仅采信清楚可读的总判定、LOW 建议及边界，不补造缺失的逐项评语。以下是作者采纳及核验，不冒充 CC 对本补记的再次复审。设计冻结是方案基线；没有任何产品能力因本文升为 ENFORCED/PROVEN。

## LOW-1：消除内核隐式默认，保留依赖方向

选择评审提出的“删除无参 builtin 默认”路径：实施时删除 WorkflowFileLedger() 与仅接收 AtomicJsonWriter 的隐式 builtin 构造，只保留显式 FlowOrchestrationKernel 注入（含故障注入 writer+kernel 构造）。不在 ledger 内引用 FlowKernelProvider：dual-flow-liteflow 已依赖 dual-ledger-file，反向引用会形成模块环。

CLI/ServerLedgerFactory 继续作为配置装配入口，经现有 FlowKernelProvider 决定实际 kernel；配置缺省仍为 LiteFlow。下游使用该装配所提供的 ledger/同一 D06 读会话，不自行另选默认内核。显式 builtin 保留给明确配置和对照测试；不是删除 builtin 引擎。工具类和只读解析器也不能以“不会路由”为理由另建默认 ledger，从而旁路 D06 一致读会话。

[构造入口扫描](constructor-sweep.json)记录当前 Java 词法点及文件 SHA，不冒充完整调用图或批准修改范围。生产入口处置：

| 当前入口 | 实施迁移约束 |
|---|---|
| CliLedgerFactory / ServerLedgerFactory | 保留显式 provider 装配，记录 configured/effective kernel；不能新增另一默认策略 |
| ProjectionsRefreshCommand | 消费 CLI 装配和 D06 读会话，不直接无参构造 |
| ExecutionNetworkGrantStore | 注入既有 ledger/会话，含内部创建 store 的路径；不在低层引入 provider，不改网络授权语义或重开 Phase B |
| AcceptedDeliveryCandidateResolver / DeliveryApplicationService | 显式接收应用装配的 ledger；默认组合构造不能重新隐藏 builtin |
| AgentOutputLineageVerifier / AuthoredStageLineageResolver / CompletionEvidenceLineageResolver | 沿既有上下文传入已装配 ledger/读会话，不以便捷局部构造跳过统一 reader |
| 测试、fixture、冷启动路径 | 显式选择 builtin/LiteFlow；故障 writer 构造同样显式。产品默认路径测试必须走真实 factory，不由 fixture 默认代替 |

D08 的 holder class monitor、close 与异常释放、同 JVM 禁并发的临时约束维持。验收补充：模块依赖仍单向；隐式构造不存在；CLI/server/worker/投影/恢复等装配及内部消费者均使用已选择 kernel；显式 builtin 与 LiteFlow 对照保留；删掉显式注入或绕过 D06 会话的反证必须失败。上述均是实施验收义务，本轮未改产品或跑产品测试。若迁移实际需扩大为新依赖注入框架，按成本收益止损，不以 LOW 为由开新平台。

## LOW-2：非必需声明进入被批准的 EXECUTION 合同

原附录 §3 的第二通道只能消费已批准 EXECUTION 合同内的声明，不能直接读取节点 config 的“可选”标记。沿现有合同 DTO 增加 `nonessential_operation_declarations` 数组；缺省/空数组表示无第二通道，不影响有独立完成证据的第一通道。既有 17 份受审样例未声明此字段，含义明确为无第二通道，不据这些样例宣称该通道已测试。

每条声明字段：`id`、`node_definition_id`、`node_contract_ref`（不可变 ref+SHA）、`operation_kind`（现有 typed tool kind）、`operation_parameters_sha256`（实际操作规范化参数身份）、`criterion_ids`、`rationale`、`rule_identity`（规则语义 id+版本）。只支持可确定参数和真实 ACP 事件精确关联的操作，不引入命令模式、自然语言匹配或 shell DSL；动态参数无法匹配时该通道不可用。声明表达该额外尝试对哪些必需义务无影响，不删除或豁免 criterion。

生产/消费链固定如下：

1. ContractCompileHandler 从冻结 node contract 收集声明，校验节点/规则/义务身份及与 D05 必需证据生产者无冲突，固化入 EXECUTION 合同；模型不能自行提供一个 verified 声明。GOAL 不提供该通道。
2. TaskContractProjectionVerifier 与执行批准的 CLI/API/UI/report 展示同一合同快照，明确显示声明、适用节点/动作、受影响义务与理由；无声明也明确可见，不能只展示摘要而隐藏该判断。批准 gate 在合同外绑定完整 SHA，仍无 hash 循环。
3. validator 的 permission_assessments 记录所消费声明 id、合同 ref/SHA、规则身份及实际 operation_ref；唯一 resolver 核验同一 signal 的关联。合同及声明版本漂移不能回读最新 config 修补，须新 EXECUTION occurrence/复评及执行批准。
4. 相同声明只有在过程可信、envelope/必需产物有效且原有不可降级边界均满足时才有资格进入 contained 判断。批准声明不授予被拒工具执行权限，不允许重试绕过拒绝，不把人体判断改成工具成功事实；受保护区写入、只读违规、身份/authority 漂移仍阻断。

反证成对：真实批准的精确声明+同动作拒绝+完整可信结果可 SUCCESS_WITH_WARNINGS；改声明/参数/规则/合同、仅 config 标记可选、隐藏于批准投影、跨 signal、缺实际关联证据均不得走第二通道。第一通道有独立完成证据时仍按其独立前置核验，不能因第二通道缺失而一律拒绝，也不能借第一通道绕过不可降级清单。E02 常驻反证与完整成功对照维持。

## 六维与剩余状态

| 决策 | Declared by | Proven by（尚待实施） | Consumed by | Enforced before | Failure route | Cannot-bypass |
|---|---|---|---|---|---|---|
| D08 | 本补记/原 §8 的显式装配和持有者互斥约束 | 默认/显式 kernel 装配与并发序列 | factories → ledger/D06 会话 → kernel | 路由、清理及一致读结论 | 缺注入拒绝；异常释放；pending/BUSY 原语义 | 无隐式构造、内部便捷路径和跨实例 close 反证 |
| D02 | 已批准不可变 EXECUTION 声明，真实 ACP signal | 合法 contained 与缺证明/漂移/违规拒绝序列 | validator → 唯一 resolver → 统一投影 | 降为 warning / 发布 / 成功路由 | 证据不足保持原阻断，不覆盖执行失败原因 | 隐藏声明、config 替代、错 signal 拒绝；合法通道仍成功 |

九项按 CC 判定及上述 LOW 采纳记为 DESIGN_FROZEN。冻结只固定设计；D01–D10 对应 W 项均未因此验收完成。D04 仍 DESIGN GAP：Claude 实际必要读取/限制被认证问题阻断，工具宿主、额外配置/读取范围与 scratch 上限还缺可行性证据；不是只差登录或 adapter 接线。首错止损探针须先离线修正，后续真实调用依既有 Gate、授权/预算及止损要求，本文不触发补测。全案未 Implementation Ready，实施、合入、真实产品运行授权独立。
