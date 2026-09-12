# Authored 引用形状错误的单次纠错

状态：IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT；provider 未运行，GCTW 保持只读。

## 目标与裁定

P0 主线可靠性：design/plan 已有完整回应、仅引用字符串形状损坏时，允许既有同 run/session 的一次无工具 correction 补正；不以降低身份校验换取续作。成功仍只是候选发布，必须继续内容评审与人工裁定。不是任务完成或自然 E2E 证明。

采用 CC Part 1 的有界方向，附加收紧：只有原本形状损坏的非空 ID 槽位可变，合法 ID 全部锁住。原始回应条数、顺序、disposition、evidence、正文与其余字段经既有 typed JSON 结构全等检查保持；这是字段值保全，不宣称 JSON 空白/键序字节全等或语义归属已被机器证明。

不采纳同批扩展：自动 retry、义务关闭、索引引用协议、model/network 层、当前样本恢复兼容。输入身份相同不能单独证明重试安全，尚须考虑已发生的外部副作用。CC Part 2 的架构整体结论不作为本批的已验证事实。

评审精度勘误：GCTW 错误 SHA 段是 64→65，原 `...ac771b...` 变为 `...ac77a7b...`，不能逐字称为单字符插入。`sha256:` 加 64 位是 71 字符，加 `#human-note:0` 的完整 ID 是 84 字符。active obligations 由 handler 的已验证 Snapshot 持有，改动前 acquirer 没有该输入，本次显式传入，不能从 prompt 文本解析。数组目标和接线/反证的实际成本以 diff 为准，不承诺“几十行即全部完成”。

## Owner 与最小范围

- Definition：既有 design/plan 输出类型及 workflow 不变。
- Instance/Gate：resolved gate、review sidecar 是 active obligation 的来源；既有 E3 resolver 选择，Snapshot/manifest 绑定该 run。
- Execution：acquirer 只消费 handler 传入的 Snapshot ID 列表；仍用 SCHEMA_FAILURE、一次 correction、contractCorrection profile。不新增持久化 authority。
- Contract：AgentResultEnvelopeValidator 登记条件性数组元素目标并验证不变字段；AuthoredRevisionValidator 共用 ID 形状、资格和精确覆盖规则。
- Publication：既有 AuthoredExecutionContext.validatePublication 重建 active 集并重验 manifest/lineage/coverage，未通过不得发布 sidecar。
- Projection：既有 acquisition 原始/纠错候选与 errors 记录；targets 写在 correction prompt 内，可由原候选和 Snapshot 重算，不新增 CLI/UI 状态或元数据 schema。

原定生产范围：AuthoredRevisionValidator、AgentResultEnvelopeValidator、AcpxAgentResultAcquirer、AcpxAgentTaskHandler。完整序列发现下述强制消费缺口后，增加既有 AgentExecutionResultFacts 和 ArtifactContractValidator 的拒绝接线，未增加新类/状态。测试覆盖 runtime validator、adapter acquisition 和真实 runtime 发布/路由序列。其他 provider、schema、workflow、ledger、recovery 分类不改。

## 完整链路复核新增发现

CC F1 所称“全部安全原语已在位”不能直接推为 ENFORCED。现有 validateCorrection 确实拒绝语义字段变化，但 acquirer.correctionValidation 只进入 run 元数据；handler 返回 COMPLETED，ArtifactContractValidator 只重验最终候选本身。格式合法的变更可绕过原来的“非目标字段不变”检查。

反证实跑：`shapeValidCorrectionThatChangesEvidenceMustNotPublishOrRoute` 在 design 修订中只改变 correction evidence，旧接线实际调用了下一 reviewer（预期 calls=3，实测=4），而不是拒绝。这是本批扩展前必须闭合的同一校验边界，不是另开 recovery 项目。

最小收口：handler 将无效 correction 转为既有 `agent_execution_result_contract` 的 invalid fact；ArtifactContractValidator 在解析/发布前强制消费 invalid fact，沿既有 contract diagnostic、finalizer 和 MANUAL_ONLY 失败路径。有效 fact 不能跳过正常校验。inline/worker 共用同一消费点。普通 schema correction 的同类缺口一并修复，否则仍保留 sibling 旁路。

## 资格与判定

1. 仅 DesignResult/PlanResult；active 集须非空、唯一、形状合法；responses 与 active 集等长、非空且每个条目完整。
2. evidence 非空、disposition 在场；每个 ID 非空；已有合法 ID 必须属于当前 active 集且不重复。仅其余形状错误位置能列入 correction targets。
3. 任一其他不可修错误继续 NOT_REPAIRABLE；引用补正不允许同时修改其他字段。缺失/空 responses、缺失 ID、重复、缺少回应内容不为其制造历史。
4. correction 返回后：严格 parse/schema、无重复、精确覆盖当前 active 集、逐槽位及其余字段不变，全部满足才成为有效候选。
5. 形状合法但错误/旧轮引用仍在发布期拒绝；不迁移所有 coverage 到 envelope，不做近似匹配或程序自动替换。
6. 多个坏 ID 允许一次提交补正，但不宣称能机械证明它们与回应文字的语义配对；沿既有 reviewer/gate 判断。合法 ID 不可被重配。

## Transition 与失败切面

| Pre-state / 动作 owner | 写入及副作用顺序 | Post-state / 下一消费者 | 失败解释 |
|---|---|---|---|
| READY/RUNNING authored node，runtime 准备 | resolver → prompt/manifest → manifest 自验 → prepared run | handler 将同 Snapshot IDs 交 acquirer | 来源/manifest 缺失不启 provider，沿既有失败 |
| 主执行完整退出，acquirer 解析 | 原始输出 → 资格校验 → 写 correction prompt → 同 session 无工具 correction | 最多两个 prompt occurrences | 非完整/非零/adapter 阻断不启纠错；prompt IO 失败不发布 |
| correction 返回，validator 裁定 | 严格 parse → shape → exact coverage → 非目标值全等 → 保存两候选/run 元数据 | handler 返回既有 typed invalid fact 或有效候选 | 无效 fact 在 publisher 入口强制转 diagnostic/FAILED；无第三 prompt |
| 候选校验成功，runtime 发布 | 原始/纠错输出与 run metadata → E3 manifest/active 重验 → sidecar → 既有路由 | 下一 reviewer/context，不跳人工审批 | 来源漂移或部分证据缺失不得发布；已写原始文件不等于批准 |
| 失败/重启/人工恢复 | 沿既有 ledger 和 acquisition 证据解释 | MANUAL_ONLY 不变 | 不重放 run 内 correction、不自动 retry/resume，不对旧 GCTW 加兼容；不承诺 crash liveness |

## Cannot-bypass 验证

- design/plan 双向：单个与多个坏 ID 可补正；非空 active Snapshot 接线真实消费。
- 改 evidence/disposition/正文、条数、顺序或合法 ID 任一必须拒；缺失/空、重复、旧 ID、不完整回应、其他结果类型不获此资格。
- 纠错中旧轮 ID、合法但非当前 ID、重复、仍坏等全部拒；第三 prompt 不发生，零 sidecar，原始候选留痕。
- 正向从真实 gate revise → handler → correction → publication → 下一 reviewer/gate；负向拒绝后 WAITING_FOR_RECOVERY。
- 同一 manifest 及来源在发布前变化仍被既有 E3 拒绝；冷读有效 sidecar 与绑定不变。
- 原有 parse/schema correction 与 E3 missing/coverage 反例维持；full reactor、diff-check。
- 普通 schema correction 偷改 summary 同样不得发布；持久化拒绝经过新 ledger/validator 实例重读仍拒绝，不因最终 JSON 格式合法而翻转。

## Invariant

Declared：既有 active Snapshot + 仅坏 ID 的 typed targets。Proven：双候选/manifest + 精确覆盖及字段保全。Consumed：validator → handler typed result → ArtifactContractValidator → E3 publication → reviewer/gate。Enforced before：sidecar 与流程路由。Failure route：既有 typed violation/MANUAL_ONLY。Cannot-bypass：上列两消费者正反序列。

## 执行结果

已完成旧行为红灯：原有入口的 authored-reference 正例 7/1/0/0（failure 是仍 NOT_REPAIRABLE），以及上述发布旁路反例。新 fixture 调试期间的导入/路径/字段名错误已修正，不计作 production 缺陷或红灯证明。

聚焦已通过：runtime contract 7 项、acquirer 31 项、真实 inline/worker sequence 10 项；handler 49 项及既有 authored boundary/stage sequence 14/12 项同样全绿。序列覆盖 design/plan，两类 author 各自从已批准合同 fixture 前态出发，后续 gate/review/发布/路由均走真实 runtime，transport 为离线替身；未对执行中的账本做 ID 重命名手术。冷读检查使用持久化工件及新 ledger/runtime/validator 实例，不冒充独立 JVM 重启证明。

全 reactor：`mvn -q test` exit 0；178 份 surefire XML 独立聚合为 **1493/0/0/0**。分模块：core 117、ledger 134、flow 12、runtime 686、adapter-acpx 381、adapter-git 6、CLI 86、server 71，全部零失败/错误/跳过。`git diff --check` clean。

首次全量运行在执行沙箱中遇到两项既有进程树测试的 `sysctl` 权限错误（AgentExecutionAttemptReconcilerTest、StaleRunningRecoveryTest）；未跳过或改动这两项测试，在获准宿主进程权限后原样重跑整个 reactor，结果如上。没有以聚焦测试代替全量通过。

本次只执行 test，未 package。冻结 CLI JAR SHA 仍为 `c8584379a5115b7e2249da4ac57d4452084162349ed72fbb5aaee961e8aebfe1`，**不包含本次修复**，不得用它宣称新能力或启动验证。

## 自查与下一步

自查已沿 Snapshot → 初始输出 → 一次 correction → typed 拒绝/有效候选 → 发布 → reviewer/gate 重走正反路径。改动范围为六个既有生产文件、两个既有测试文件、一个新增序列测试及本文；工作树其余文档和输出不属本 checkpoint。没有新增 failure 分类、元数据 schema、workflow、模型特判或自动恢复动作。

独立 diff review 必须重点检查新增的强制消费接线，而不能仅重验数组字段 diff：在合法 JSON 中偷改 evidence/summary 时，为什么既有 correction 拒绝能阻止 sidecar 和下一 reviewer；inline/worker 与普通 schema correction 是否同样受守卫。其余检查包括可信 active 集来源、仅坏槽位可改、完整集合等值、来源漂移、拒绝后的持久化重读，以及上述完整 invariant 与失败切面。

成熟度：**BOUNDED AUTHORED REFERENCE CORRECTION / CORRECTION REJECTION CONSUMPTION: ENFORCED + DETERMINISTIC SEQUENCE PROVEN**；**NATURAL E2E NOT PROVEN**。字段值保全不证明 evidence 与 obligation 的语义配对，该判断仍归 reviewer/human。

独立 CC diff review 已通过：P0/P1 为零，亲跑 AuthoredReferenceCorrectionSequenceTest 10/0/0/0、AcpxAgentResultAcquirerTest 31/0/0/0、AuthoredRevisionContractTest 7/0/0/0。评审自行追踪完整消费链，确认普通 schema correction、inline/worker、design/plan 与持久化拒绝重读均受守卫；未独立复跑全 reactor，不将主 agent 的1493项复跑写成独立证明。

五项 P3 保留为边界说明而非新增开发：语义配对仍归 reviewer/human；合法但错误的 ID 不在本轮 correction 范围；既有 invalid fact 后续生产者同受发布约束；AgentExecutionResultFacts 的最小公共 API 为跨模块接线所需；真实模型纠错是否成功仍待自然样本。独立评审允许按上述六生产文件、两测试修改、新序列测试及本文选择性提交，排除其他工作树变更。

下一步：选择性提交及重建 JAR → 按既有可复用 Gate 更新运行身份与前置检查后验证。GCTW 保持只读，不做兼容救援。未启动额外 agent、未消耗 provider、未恢复/新建 real-case。
