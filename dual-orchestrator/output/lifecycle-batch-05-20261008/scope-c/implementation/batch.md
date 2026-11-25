# B05 · D01 首版 C 合同资格族垂直切片

状态（2026-10-09）：用户带回 CC 批末独立 PASS，B05 首版 C 按单族 CLOSED / PASS，见 [收口处置](../../closure-20261009/disposition.md)。LOW-1 时钟证明未常驻已登记；不关闭整体 D01/I1.a/W01 或其他 W 项。

## 目标与范围

P0：在合法人工批准后，合同资格消费者拒绝时保留实际决定检查与拒绝依据，使“矛盾／输入漂移／消费前置未检查／UNKNOWN”可核验，同时守住正常作者→自测→独立验收→人工交付。它增加诊断精度，不增加批准、重试或执行权限。

依据是用户选择 C 与带回的本族设计复审，见 [受审设计](../design.md)。首族为 TaskContractProjectionVerifier，另含已列的 Governance 两项桥接；其余守卫族继续执行严格校验但不冒称逐规则诊断。没有第二族、规则解释器、ThreadLocal、服务、新锁或新存储。原 dirty 工作区保留，实际增量以 production-before-* 对账，不以 HEAD diff 冒认本批。

## authoritative facts 与消费者

| 事实 | owner / 唯一消费 | 强制点与边界 |
|---|---|---|
| 实际 rule/input/version/result | 原 checker + 局部 ContractQualificationTrace | 只记录真正运行的条件；未来条件明确 NOT_CHECKED；异常、未采集不推成 PASSED |
| decision_checks | ReviewGateActions 采集；RoutingEngine 在 L04 绑定 gate/compiler/subject/runtime 并写 gate/node | 正常批准缺快照零写；未采集的内部决定明确 NOT_INVOKED，不借其解释矛盾 |
| continuation_failure + 原失败 | 原消费者；native 通过封闭 Facts 和固定 input-rejection 完成记录→L03；本地任务随原 L03 | 原 reason/code/outcome 保真；不新增发布资格或伪造本地任务的 providerInvoked=false |
| 四分类解释 | ContractQualificationRecovery 校验固定来源；RecoveryAssessment 唯一解释；CLI/API/attention/console 消费 | 不从正文/latest/整文件事件 SHA 推断；所有解释 retrySafe=false，外部 UNKNOWN 优先 |

规则版本测量实际加载的 checker/collector/值类型与 readiness 类字节，并绑定现有 frozen runtime identity。事件身份使用具体条目原始字节、event id/sequence/ref；正常追加事件不等于旧条目漂移。只剔除 readiness 生产者的检查时刻及覆盖元数据，不忽略任意输入中的同名字段。配对沿当前 target 的 SHA 验证输入链，核 gate/node/compiler/subject 与原失败码，禁止找最近批准凑 pair。

## 完整 transition 与失败切面

| 前置→动作 / 允许者 | durable 顺序 / 下一消费者 | 失败、重放与成功后状态 |
|---|---|---|
| 当前 contract gate→用户批准；现有人工入口 | L 中实际检查→RoutingEngine 绑定→L04 prepared gate/node/event/current | before manifest 零决定；其后 pending 禁读，冷 JVM 只补固定字节；后继读取同一批准，不重做用户决定 |
| 后继合法输入→作者 / 原 executor | 实际资格→R01 固定准备→dispatch 重核→R02 外部意图 | 首次准备或 R01 后拒绝仍保留原规则；固定输入/claim 等其他守卫保持原拒绝，不冒成本族已覆盖 |
| 本族拒绝、尚无 R02 | 封闭原失败+诊断→现有 R 目录固定 input-rejection→L03 node/task/execution | R03 写失败不宣称完整；L03 八个故障前缀只补固定完成、零 provider；诊断不产生 R01/R02 资格 |
| 本地 context/scope/classify/report 资格拒绝 | 原结果+同一诊断→现有 L03 | 不造 native run；共享 D06 补齐规则，不把直接 sibling 调用测试包装成四条独立真实 E2E |
| R02 已存在 / 外部结果不明 | 原 D07 固定外部事实→人工裁定 | 不准把随后资格失败改成“未执行”；原在途／停止语义保持，不自动重发 |
| 查询失败→现有恢复 read model | 固定 ref/SHA/target/rule/code 核验→解释 | 错/缺证据 UNKNOWN；显示不是权限；历史和终态不生成当前动作 |
| 同输入恢复／需求改变 | 原人工 retry 重核，或作者→独立复评→新批准 | 保留实际合法恢复对照；合同、scope、验收要求变化仍走既有重批，不能编辑旧批准或以诊断放行 |

新旧运行身份仍由 D06/D07 拒绝异版；不支持历史诊断回填。直接未采集决定只标 NOT_INVOKED；缺历史记录只是 UNKNOWN，不伪造 NOT_CHECKED。

## 验证与证据边界

先红证据 red-sequence.log / red-reports 是实际批准没有 decision_checks 的失败；其他编译或 fixture 作者错误单列在各日志，不当作生产反证。

ContractQualificationSequenceTest 覆盖实际批准、依赖漂移及原输入恢复、未来 compiler 条件、测试自身错误 checker 的矛盾机制、直接未采集 UNKNOWN、删 pair / 错 target / rule / version / code / decision SHA、缺合同、事件追加与具体条目变化、L04/L03 八类前缀及冷子 JVM、R03 写失败、covered null 零写，以及四个本地消费者的值携带。

矛盾用例测量真实输入，但拒绝来自测试自身错误 checker：仅证明分类机制，不宣称生产 checker 有该 bug。未检查用例中的额外未批准 compiler 是测试注入，移除只移除测试故障，不冒充生产恢复历史。协议/native/provider 不在这些证明内。

WorkflowFirstLifecycleSequenceTest 原有完整合法序列改用实际 ReviewGateActions：inline/worker × 四变体，作者、自测、另一客户端验收及交付门全部到终态；删除实际合同经过 context 失败与 L03，恢复同字节后可完成独立验收。PhasedContractContinuationTest 的真实人工补 readiness→返修→复评→新批准序列继续运行。

机械守卫使用测试 JDK AST；当前实际拒绝位点 71 个，必须是具名映射或明确未覆盖依据，索引内容身份绑定源码。添加可执行拒绝点会失败；注释/字符串不触发。原 67 项候选索引保留历史，不冒认当前映射。索引不在运行时加载。

第一轮 reactor-01 单轮 1,727 项、206 suites、零失败/错误/跳过；随后发现 sibling 缺口并修正，因此该数字只证明先前版本，不是最终收口凭证。最终凭证为 reactor-final 单轮 clean：1,731 tests / 206 suites / 0 failures / 0 errors / 0 skipped / exit 0，653 个 source/resource/pom 的前后 SHA 完全相同；XML 均在本轮生成并存于 final-reports，见 test-results.json。不拼接先前数字。所有运行为离线、本地 Java/文件/模拟协议测试，native/provider 调用零。

R01 后 dispatch 重核补测已通过：先以合法输入实际准备，随后变化真实 readiness 依赖，仍能固定本族失败及配对；R03 固定后原 sender 不能再创建 R02。R02 已存在时禁止保存声称未执行的准备失败，释放本地 invocation 后恢复为外部 UNKNOWN。此用例在 focused-14 暴露重取 L 的接线错误（原本有据漂移降为 UNKNOWN），focused-15 复用原 scoped 会话后通过；不是通过放宽漂移判据转绿。

ClockComparisonProof 是独立离线补证，使用实际批准与 readiness 观察，确认检查时刻不同而实际输入未变；测试故意错误拒绝时应解释为矛盾，撤掉测试错误后小型 phased fixture 完成。临时副本撤掉时间元数据处理产生预期失败；产品源码不改。第一次补证因 fixture 没有显式 ledger 会话而 INVALID，原日志保留。此补证不计入 Maven 数字、不冒称常驻测试或完整原生 E2E，见 clock-comparison-results.json / clock-counterproof-results.json。

## Review → Reflect → Analyze

自查发现并补齐：四个本地任务 consumer 遗失 typed check；covered API null 可能降为未采集决定；比较器不应递归忽略所有同名字段；原失败码也须进入关联验证；固定准备失败后不能再创建外部意图；dispatch 诊断必须复用已持有的 scoped 会话。修正保持原 owner、原因与写序，见 self-review-01.md 与 consumer-map.md；不以自查冒充独立评审。

统一投影源码核验：RecoveryShowCommand、WorkstreamQueryService、WorkstreamAttentionEvaluator、console.js 都消费 RecoveryAssessment。最终全部既有 CLI/server 回归仍须通过；未做浏览器视觉验证。

## 六维及未完成目标

Declared by：用户 C、受审设计及原 I1.a/D01；Proven by：上述分层离线序列和最终单轮回归；Consumed by：实际 checker→L04→原消费失败→固定完成/L03→唯一 RecoveryAssessment；Enforced before：批准写入、dispatch、失败完整性采纳与后续路由；Failure route：D06 固定补齐、D07 外部 UNKNOWN、无自动重发；Cannot-bypass test：正常与拒绝对照、配对/身份/枚举反证、写入前缀、冷 JVM、两入口四变体。

稳定源码离线回归与主 agent 自查已完成；本族批末独立 PASS 已接收，按单族收口。不能关闭全 D01/I1.a/W01，也不关闭其他 W 项或改变真实 NO-GO。没有提交、installer、真实客户端运行、额外 agent 或后续批次授权。

收尾格式核对：清除本批 Verifier 的 13 处行尾空格并同步仅测试的 source-bound 索引后，重新编译及索引守卫通过；1,109 个已编译 class 字节全部与最终整轮回归相同。整轮 653 源码前后 SHA 凭证对应其运行期间，当前源码的格式变化另由 post-format-source-sha256.json / format-proof.json 记录，不伪称回归之后未改源码、不拼接测试数字。git diff --check 剩余告警属于此前 acpx patch 的上下文空格，本批没有修改该补丁。
