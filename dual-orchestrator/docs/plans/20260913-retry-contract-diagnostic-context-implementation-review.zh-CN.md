# 独立评审归档：人工 retry 携带合同拒绝诊断

归档日期：2026-09-13。以下为用户提供的CC独立评审原文；由主agent归档，不代表主agent新增一次独立复跑。测试与结论保留评审者原始归属。

评审完成。以下为独立评审报告。

---

「人工 retry 携带合同拒绝诊断」实现评审（独立只读）

基线 8708eb76 + 工作树未提交变更。评审对象：NodeStateWriter.java（+39）、AcpxAgentTaskHandler.java（+50）两个生产文件及 5 个测试文件（3 新 2 扩），排除 20260909-okx-profile-aligned-fresh-e2e.md（本实现开工前已存在的无关变更，checkpoint 已正确声明排除）。

0. 总判定

实现与设计核对稿一致，且采纳的是上轮设计评审建议的最小变体（有界内嵌快照，非 execution 引用链）；生产面 89 行单一生产者/单一消费者；未发现阻塞缺陷。全量回归独立复跑 1508/0/0/0 与 checkpoint 声明逐模块吻合。

结论：CAN COMMIT（选择性提交两处 production + 5 个测试 + 设计/checkpoint 两文档），条件与未尽边界见 §5。

1. 链路独立重建（拒绝对后继的每一跳均有代码+测试双重锚点）

┌───────────────┬─────────────────────────────────────────────────────────────────────────┬─────────────────────────────────────────────────────────────┐
│      跳       │                            代码事实（一手）                             │                          测试锚点                           │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│ 合同拒绝 →    │ ArtifactContractValidator 未改动（不在 diff）；拒绝 result 含           │                                                             │
│ FAILED        │ reason=AGENT_OUTPUT_CONTRACT_VIOLATION + 结构化 parse_error（GCTW/KPPZ  │ 序列测试以 scope 拒绝真实触发                               │
│               │ 账本实测同形，acquisition 层与 runtime 层同写者）                       │                                                             │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│               │ NodeStateWriter:219-229 门（eventType=node_retry_requested ∧ FAILED ∧   │                                                             │
│ 人工 retry →  │ acpx.agent_run ∧ 合同拒绝 reason ∧ 前态 WFR ∧ parse_error               │ ledger 测试：损坏诊断/前态不完整 → node/current/events      │
│ READY+快照    │ 结构完整）；快照五字段与 READY 同一次 node.json                         │ 字节不变（assertArrayEquals 原文比对）                      │
│               │ 写；任何前置不满足在写入前抛出                                          │                                                             │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│ 事件/current  │ 事件不复制诊断（diff 确认 eventDetails 未加快照）                       │ 半写测试：破坏 event 文件/current 原子写 tmp → retry        │
│ 后续写        │                                                                         │ 中途抛出 → 冷读零 provider                                  │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│ 新执行        │ markQueuedForAgentTask/markRunning 均 mergedResult 保留快照；新         │ 序列测试删除 execution json + 稳定诊断文件后新 ledger       │
│               │ execution 更新 latest 不影响快照（内容在 node.result，非外部文件）      │ 实例照常走通                                                │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│               │ retryFailureInstruction 位于 ensureQualified(L193) 之后、grant          │ 六种损坏变体（含伪造 approve_all 键）→ REQUIRED_AGENT_EVIDE │
│ 快照/现状校验 │ claim(L222)/provider 之前(L208)；五字段精确键集 + schema_version=1 +    │ NCE_MISSING/RETRY_FAILURE_CONTEXT_INVALID，零 provider、零  │
│               │ ≤8192 UTF-8 + retry_of_status=FAILED + 非 auto_retry；current 须        │ sidecar、resume 不自动重试                                  │
│               │ RUNNING+active+无 open gate                                             │                                                             │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│ prompt/manife │ section 以 "> " 逐行引用诊断文本，声明“非指令非授权”；进                │ 断言 prompt 文件==transport 所读、manifest 含 section       │
│ st            │ PromptSection(manifest required=true) 与落盘 prompt                     │                                                             │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│ 原校验 →      │ validator/routing 零改动；真实栈（runtime+worker+handler+acquirer+route │ reviewer prompt 无 section；corrections==0（节点 retry 不占 │
│ reviewer/gate │ r+reviewer+gate）序列走完 design/plan × claude/codex ×                  │  schema 纠错预算）                                          │
│               │ inline/worker，过两个人门后 COMPLETED                                   │                                                             │
├───────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────┤
│ 后继隔离      │ 完成时 RoutingEngine 整体替换 result（既有行为，未改）→ 快照消失        │ completed 节点 result 无 KEY；implement prompt 无 section   │
└───────────────┴─────────────────────────────────────────────────────────────────────────┴─────────────────────────────────────────────────────────────┘

再次失败：第二次拒绝后 retry 只携第二次诊断（unapproved/second.txt 在、first.txt 不在）——快照每次由当次失败 result 重建。授权隔离：坏快照/current 三种故障下 transport.lastExecutionPolicy 为 null 且已批准的 network grant 文件字节不变（“invalid input must not consume authority”）；prepareOnly 同守卫且策略网络域为空。Sibling：auto-retry 整体替换 result（无快照、置 auto_retry=true，handler 侧再拒双标志并存——纵深防御）；WebSearch 批准走 public_websearch_approved eventType 不匹配门；BLOCKED 不进门；collect/submit 走 resolveOpenGate 不经 retryActiveNode；CLI 与 server 同调 ledger.retryActiveNode（各自测试断言无 provider 启动）。

2. 六维核对

┌────────────────┬──────────────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│      维度      │       判定       │                                                        依据                                                        │
├────────────────┼──────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Declared by    │ ✅               │ 创建资格五条件 + WFR 前态，写入侧强制；不含业务/模型/错误文本特判                                                  │
├────────────────┼──────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Proven by      │ ✅               │ 快照与 READY 同文件原子写（代码结构）；落盘 prompt/manifest 实测含诊断与来源标签                                   │
├────────────────┼──────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Consumed by    │ ✅               │ grep 全 main 源码：retry_failure_context                                                                           │
│                │                  │ 单生产者（NodeStateWriter:230）单消费者（AcpxAgentTaskHandler:73/76）；CLI/server 只是入口                         │
├────────────────┼──────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Enforced       │ ✅               │ 守卫在资格后、grant claim 与 provider 前；malformed → FAILED 零 provider                                           │
│ before         │                  │                                                                                                                    │
├────────────────┼──────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Failure route  │ ✅               │ 复用 REQUIRED_AGENT_EVIDENCE_MISSING 人工恢复出口；无 auto-retry（resume 复测零 provider）；abort/terminal         │
│                │                  │ 守卫未动                                                                                                           │
├────────────────┼──────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Cannot-bypass  │ ✅（离线范围内） │ 六损坏变体、双半写、冷读删源、再失败隔离、后继隔离、grant 不消费、auto-retry 隔离、双执行模式/双 agent             │
│                │                  │ 参数化；enforcement 终点仍是未改动的发布校验                                                                       │
└────────────────┴──────────────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

3. 分级 Findings

P3-1（事实，cosmetic）：守卫失败时 runDir 与 compaction seed 文件先于守卫落盘（既有顺序，L145-208 之间），留下空 run 目录与孤儿 seed 文件。与既有 ensureQualified 失败同残留类，无状态影响（inputRefs 未加、无 provider）。不阻塞。

P3-2（事实，可选加固）：section 头部 Historical run/execution: 两行是未加 "> " 引用的裸文本（诊断本体已逐行引用）。宿主手改 ledger 可注入换行标题——但宿主改账本明确在威胁模型外（checkpoint 已声明不抵抗）。一行引用化即可闭合，非必需。

P3-3（观察）：序列测试只走 runtime 层拒绝（DESIGN/PLAN_SCOPE_INVALID）；acquisition 层拒绝（GCTW 类）未做序列覆盖，但其 result 形状由同一 contractViolation 写出（空 details Map 亦过形状检查，静态核证）。可接受的残留。

P3-4（诚实性核对，通过）：checkpoint 各数字全部对上——89 行（39+50）、15 项新测试（5+8+1+1）、模块增量（ledger 134→139、acpx 381→389、cli 86→87、server 71→72）、1508 总数。“接线突变”实验无法由我复现（已还原），但其静态等价物（序列测试的 provider 计数断言）在位。

4. 更小方案 / 范围扩张判定

- 更小方案：不存在。实现即上轮设计评审 F1 的有界内嵌副本（无 execution ref、无 SHA 验证器、无事件交叉校验——设计稿的三个最重部件全部正确省去），并按 F2 建议放弃了事件镜像。89 行中约半数是消费侧守卫（Enforced-before 维度），删守卫即破边界，不属于可再减部分。
- 范围扩张：无。git diff --stat 仅两处 production；validator、routing、workflow JSON、ContractRegistry、权限、grant、CLI/server 命令面零改动，与设计 §5 “不改”清单逐一相符。

5. 按风险复跑与提交结论

独立复跑：mvn -o test（我方环境未触发 checkpoint 所述沙箱 sysctl 问题）→ 1508 / 0 / 0 / 0，8 模块与 checkpoint 表逐项一致；三个新测试类 XML 在 surefire 报告中实存。新文件无行尾空白。

CAN COMMIT：两处 production + RetryFailureContextTest / RetryFailureContextSequenceTest / RetryCommandTest + 两处测试扩展 + 设计与 checkpoint 两文档。不携带 20260909-okx-profile-aligned-fresh-e2e.md 与其他工作树遗留。

尚未证明的边界（不因测试绿而消失）：
1. 真实 provider 返工成功率：诊断携带是输入完整性，不是效果证明（替身 provider 全程）。
2. acquisition 层拒绝的序列行为（P3-3，仅静态形状核证）。
3. 跨 OS 进程的冷启动：冷读测试用新对象而非新进程（checkpoint 已自认）；磁盘状态自足性由删源文件测试间接证明。
4. 宿主改账本 / R1 liveness：明确不在本批威胁模型内。
5. 旧样本（KPPZ/GCTW）不因此能力可 resume——按既有纪律需重建运行工件另行验证。

如需，我可将本报告落盘为 docs/plans/20260913-retry-contract-diagnostic-context-implementation-review.zh-CN.md 供提交序列引用。
