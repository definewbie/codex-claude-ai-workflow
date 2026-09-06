# E4 Plan Scope 与 Canonical Plan 减法实施记录

状态：INDEPENDENT DIFF REVIEW PASS / CHECKPOINT COMPLETE；允许选择性提交。
基线：`1e9723ad`；设计见同目录 `post-contract-authority-continuity-e4-plan-scope-subtraction-implementation-plan-20260906.zh-CN.md`。

## 1. 目标与实际范围

本件推进 P0 主线的 post-contract authority 连续性，不推进历史 E1 网络或新的 token 治理能力。
生产恰五处：PlanScopeValidator（新增 package-private）、AgentResultEnvelopeValidator、ArtifactContractValidator、AgentResultPromptContract、MetricsProjectionWriter。
无新状态、领域文档、schema record、workflow、resolver、恢复、网络或产品规则；E3 与 Git scope owner 未改。

- plan shape 使用既有 canonical path 谓词与去重，错误 reject/NOT_REPAIRABLE；acquirer 测试证明零 correction 调用。
- shape 沿既有 envelope `AGENT_RESULT_CONTRACT_VIOLATION` 携 `PLAN_SCOPE_*` 细分消息；发布期子集/资格错误为 `PLAN_SCOPE_INVALID`，不另建 failure 路由。计划文字已按这一既有分层同步。
- plan 发布前经既有 qualified contract resolver 检查 edit_files 子集；无自动规范化、无文件存在性要求、无 operation/plan-subset-design 新约束。
- reference_files 仍可引用 edit scope 外的阅读材料，不能授权修改，也不能再豁免指标。
- canonical plan writer/helper 与 metrics reader/参数/字段/标签删除，per-output `.plan.json` 与 sealed `.contract.json` 内 plan 保留。
- `planAuthorized` 投影字段与 `plan_authorized` 标签消失；旧指标刷新后可能显示更多 missing，不声称 provider 行为改变。

## 2. 红灯与回归处置

生产修改前，两个 metrics 反例均得到旧 `plan_authorized`，非期望 missing；TypedPlanScopeTest 三项红灯分别为越界计划进入 WAITING_FOR_HUMAN、canonical 文件仍存在、prompt 规则缺失。
修改后聚焦通过。首次完整 reactor 仅 runtime 两个旧 SmallWorkflowProfileGateIntegrationTest 失败：候选沿用 prompt 示例 `path/to/file.ext`，不在 fixture 批准合同中。
只修测试候选使 edit_files 来自 fixture 已批准 included paths；不改合同、不反转审批/revision 正例、不放宽守卫。
新增测试中的 NodeDefinition accessor 编译错误已修正为 handlerType，未作为任何边界证明计数。

## 3. 全链证据映射

| 断言 | 测试/既有链 |
|---|---|
| 两业务、三 plan 拓扑拒绝 | TypedPlanScopeTest：每个业务/拓扑执行越界、非规范、重复、空清单；raw 留存，sealed/derived 不发布、reviewer/implementation 不调用、plan gate 不创建 |
| scope shape 不消耗 correction | AcpxAgentResultAcquirerTest：非法/重复/空 edit_files 均 NOT_REPAIRABLE，transport 仅初始一次、correction prompt 不写；初始与 correction 共用同一 PlanResult 文案 |
| 合法子集与真实资格 | TypedPlanScopeTest：两业务合同另含未计划文件，子集通过，reference 可越 edit scope；合同删除/篡改、批准 gate 删除后发布拒绝 |
| 新文件、空格、改名通用性 | TypedPlanScopeTest + E3 既有一致改名正例；CREATE 无需预先存在，批准后 implementation/self-test 共用 plan identity |
| 三拓扑冷启动正向消费 | 新 JVM 调既有 ColdRead，snapshot 全等；继续完整 fixture 至 implementation/self-test 终态，无真实 provider |
| 物理写入失败 | 两业务在 sealed 写入前、sealed 后 derived 写入前用目录制造真实 IOException；不改标语义违约；冷 JVM 不接受残留文件，无后继 |
| 实际 Git 越界不被 plan 豁免 | 两业务经真实审批/实施/自测 fixture 后，真实 Git 合法 CREATE 为 PASS；加 unauthorized 文件后 ArtifactValidateHandler 为 scope FAIL |
| metrics 无 stable fallback | absent/edit/reference/无关/损坏/删除文件，toolMetrics 全等；missing/recorded/not_required 三态保持，无 planAuthorized 字段 |
| writer 减法 | ArtifactContractValidatorTest 与 TypedPlanScopeTest 保留 occurrence 正例并断言 canonical 不生成 |
| 同边界旧能力保持 | 完整 E1/E2/E3、四 workflow、全部 implementation/self-test 再入和 acceptance 资格测试随 reactor 复跑；不另建测试框架 |

这不是一次真实产品 E2E。Git 反例验证实际改动的 scope owner，不能将其外推为所有业务验收语义正确。

## 4. 转换、故障与成熟度边界

Declared by：qualified TaskContract included paths；待验 PlanResult 无自行扩权资格。
Proven by：E1 qualification + E4 子集守卫 + E3 sealed occurrence/批准身份；真实操作另取 Git。
Consumed by：发布→既有审批→E3 唯一 approved-plan resolver→implementation/self-test；acceptance 保持 routed qualification 与实际 scope 链。
Enforced before：无效计划不发布有效 sidecar、不进入后继审批/实施；Git 检查不因合法 plan 放宽。
Failure route：plan typed contract violation；物理写入仍为 IO；冷读拒绝不完整 lineage，禁 stable/latest fallback。
Cannot-bypass：§3 拒绝序列、冷读、真实写入失败、Git 反例和减法测试；突变与最终验证结果待 §5 记录。

写入顺序仍是 raw→shape→subset→E3 identity→sealed→derived→既有 execution/routing。没有跨文件事务或新恢复语义。
旧 ledger 不迁移，不救援历史样本，不用新旧 binary 混跑证明本件；旧 canonical 文件无需删除，因为 reader 已消失。
业务完整性、模型语义收敛、R1 liveness、E1-E4 自然 real-case 均未由本件证明。

## 5. 最终验证

四个独立突变均先编译成功、再由行为断言失败；每次测试结束即恢复，不在突变态运行 provider：

| 突变 | Maven 选择器（均配 `-am -Dsurefire.failIfNoSpecifiedTests=false test`） | 红灯 |
|---|---|---|
| 禁用 ArtifactContractValidator 的 PlanResult 发布检查 | `-pl dual-runtime -Dtest=TypedPlanScopeTest#invalidPlansCannotPublishOrReachAnyPlanSuccessorInEitherBusiness` | WAITING_FOR_HUMAN 而不是 WAITING_FOR_RECOVERY |
| 重新生成 canonical plan 文件 | `-pl dual-runtime -Dtest=TypedPlanScopeTest#approvedPlanKeepsOccurrenceProjectionWithoutPublishingCanonicalPlan` | 禁止存在的 canonical 文件出现 |
| 重放基线完整 MetricsProjectionWriter | `-pl dual-ledger-file -Dtest=MetricsProjectionWriterTest` | 3 个失败：edit/reference 仍 plan_authorized，已删除字段复活 |
| 删除 envelope 的 PlanScopeValidator.shapeErrors 接线 | `-pl dual-adapter-acpx -Dtest=AcpxAgentResultAcquirerTest#planScopeShapeNeverStartsCorrectionOrWritesACorrectionPrompt` | 非法路径 initialValidation 被接受 |

突变前后五文件 SHA 完全一致：PlanScopeValidator `64a50cf4…`、EnvelopeValidator `4d97ab4d…`、ArtifactContractValidator `3a26fff9…`、PromptContract `89711f68…`、MetricsProjectionWriter `8bd69431…`。
2026-09-06 17:43 +08:00 恢复后 `mvn -q -fae package` exit 0；162 份 surefire XML 独立聚合 `1397/0/0/0`：
core 116、Git adapter 6、ledger 123、flow 12、runtime 634、acpx adapter 361、CLI 80、server 65。
相对 E3 最终提交 `1e9723ad` 的 1388 净增 9 次 JUnit invocation（TypedPlanScopeTest 7 + metrics 1 + acquirer 1）；矩阵循环不重复计数。E3 计划 §20 记录最终 1388，§19.4 的 1381 是补齐双业务对称测试前的中间值，不是本件基线。
新 JAR 对 contract/full/terminal-delivery/small-plan-human-review-experiment 四份 `workflow validate` 均 exit 0，形状分别 34/56、34/56、31/52、28/47。
全 production 搜索 `planAuthorized|plan_authorized|canonicalPlanArtifactPath|artifacts/plan/plan_artifact.json|resolve("plan_artifact.json")` 零命中；occurrence readers 保留。
`git diff --check` clean。CLI JAR SHA256：`6a28fcce8b94f95f87cd7d39887f716f55a81cfbe1b79977088032af0e9e4a18`。

自查 Boundary Review：PASS，限本实现及确定性测试；没有替代独立评审。五处生产面与批准设计一致；测试增量另含 adapter correction 和旧 small-workflow fixture 修正，均属计划既定消费者覆盖。
CC 独立 diff review 已完成，见 §6；REAL-CASE NOT PROVEN。
未调用 provider，未创建/恢复 real-case，排除旧 OKX Gate 与 output/。

## 6. 独立评审与收口

用户提供的 E4 独立 diff review：P0/P1 无，允许提交。评审自行追踪全链、读全部生产/测试 diff、复算五文件 SHA 与 subtraction sweep，独立复跑 TypedPlanScopeTest 7、MetricsProjectionWriterTest 21、AcpxAgentResultAcquirerTest 28，全部通过。
CC 未复跑完整 reactor 与四 workflow validate；§5 的 1397 与四定义结果为实施方验证，不归称 CC 复现。
P3 基线记账已由 §5 对齐 E3 §20。ColdPublicationRead 的宽捕获仅证明不可用，不用于精确故障归因；空清单双重诊断不改变拒绝语义；均不新增生产修补。
原评审“五处、净 -39 行”未计新文件：-39 为四个已跟踪生产文件 diff 的净值；范围仍为四处修改加新增 PlanScopeValidator。不采用该数值描述全部生产增量。
结论：PLAN SCOPE ENFORCED / CANONICAL PLAN AUTHORITY REMOVED（确定性证据）；E1-E4 已声明边界的确定性链收口，不等于真实业务语义必然收敛。
下一独立验证点是自然 fresh E2E Gate；本 checkpoint 不自动授权 provider 或 real-case，不启动第二类治理设计。
