# E4 Plan Scope 与旧 Plan 投影减法：最小设计及通用性评估

状态：INDEPENDENT DIFF REVIEW PASS / CHECKPOINT COMPLETE；未调用 provider。
基线：E3 checkpoint `1e9723ad`。本计划中的 E1 指 Contract Qualification，不是历史网络控制项目。

## 1. 目标与开工判断

唯一目标：使 plan 的拟修改路径不能扩大已批准 TaskContract 的文件范围，并删除 canonical plan 对重复读取指标的越权豁免。
这是 E1 合同资格、E2 设计范围、E3 authored revision/approval 链的最后一个既定缺口，不以修复某个 OKX 样本为目标。
canonical plan 在候选发布时、人工批准前就被覆写，因此未批准或已被取代的候选也能改变 metrics 豁免来源；删除是纠正错误事实来源，不是可选清理。

选择两个动作：
1. 在 PlanResult 发布任何 sidecar 前，强制 `edit_files` 规范、唯一、属于当前 qualified contract 的 included paths。
2. 删除 stable `artifacts/plan/plan_artifact.json` writer 和 metrics reader/豁免；保留 occurrence sidecar 与 E3 approved-plan 消费链。

不新增 plan schema、operation 字段、approval resolver、binding artifact、状态、workflow flag、恢复路径、网络机制或测试平台。
不要求 plan 等于合同全部路径，不新增 plan 必须是 design 路径子集的规则；业务完整性仍交 reviewer/human。
采用后续 producer-consumer sweep §6.2 的精确边界：PlanResult 无 operation，不能声称此处检查了 ADD/MODIFY/DELETE。

## 2. 代码事实与职责

以下由本轮只读核对得到，不以旧评审结论替代代码事实：

| 面 | 当前事实 / 本轮处置 |
|---|---|
| AgentResultEnvelopeValidator 的 PlanResult 分支 | `editFiles` 仅 requiredList；补规范/唯一 shape 检查，保持既有非空要求 |
| ArtifactContractValidator.validateAgentRunResult | 已有 DesignScopeValidator 分支，缺 plan 对合同的包含检查；增加同层发布前守卫 |
| TaskContractProjectionVerifier.resolve | 既有 gate + compile lineage 资格 owner；复用，不从 stable contract 或名称猜批准 |
| AuthoredExecutionContext.validatePublication | 写 sealed sidecar 前重验 authored manifest/批准身份；保持，不能由路径子集检查替代 |
| writePlanArtifactSidecars | 写 per-output `.plan.json` 后还写 canonical 文件；只删后者及其专用参数/helper |
| MetricsProjectionWriter.planAuthorizedFiles | canonical reader；读取 edit/reference files，为事后重复读取标签提供豁免，非 adapter 运行时读取准入 |
| AuthoredStageLineageResolver | 消费 occurrence sealed sidecar 中的 plan；继续唯一选择已批准 plan，不接入 metrics |
| AgentRunGovernanceContext.ensureQualified | implementation/self-test 走资格及 authored prepare；acceptance 走 routed contract 资格；本轮保持 |
| ArtifactValidateHandler / ChangeScopeEvaluator | qualified contract + 实际 Git changes 判断实际路径/操作；不是由 plan 清单证明真实改动合法 |

全 production 搜索：canonical 路径引用仅上述 validator writer 与 metrics reader；`planAuthorized/plan_authorized` 无其他 production 消费者。
`AgentOutputContractDocument`、`AgentResultDocumentProjector`、`PlanArtifactDocument` 的 occurrence plan 读取不是删除对象。
既有非 qualification legacy 分支不在本轮升级支持范围；不得据此宣称任意旧 workflow 已具有 E1-E4 authority。

## 3. 最小实现

新增一个 package-private `PlanScopeValidator`，职责与 DesignScopeValidator 对称，但不抽取通用规则框架：
- shape 复用 `TaskContractValidator.isCanonicalPath`，拒绝重复项，不替 Agent 自动规范化路径；内部空格合法与否遵从同一既有谓词。
- qualified contract 复用 E1 resolver，逐项精确匹配 `scope.included.path`；不要求文件现存，合同允许的新文件必须可计划。
- shape 在 envelope validator 调用；root/workstream 相关检查在 ArtifactContractValidator 的 PlanResult 分支调用，早于 withResultSidecars。
- plan scope shape 错误一律 `errors.reject`，结果为 `NOT_REPAIRABLE`，不得进入 schema correction 自行改写路径；与 E2/E3 的语义守卫保持一致。这不禁止后续经既有合法 revision 产生新计划。
- 发布期子集/资格拒绝沿既有 `contractViolation` 返回 typed `PLAN_SCOPE_INVALID`；shape 错误先由 envelope 拒绝，保留 `AGENT_RESULT_CONTRACT_VIOLATION` 与 `PLAN_SCOPE_*` 细分消息，不增加 recovery 路由。
- prompt 按 PlanResult 类型说明同一规则，只作指引；拒绝由 validator 决定，不能靠 prompt 或 reviewer 代守。

`reference_files` 不变：读取参考不等于修改授权；不对它施加 edit scope 子集，也不再用它豁免读取指标。

删除 metrics 的 `planAuthorizedFiles`、对应参数/状态，以及 `ReadFileMetric.planAuthorized` JSON 投影字段和 `plan_authorized` 标签。
这是有意的派生展示字段减法，不宣称“输出字节/schema 完全不变”。保留 required/recorded/missing/not_required 的既有读取事实逻辑。
现存旧 metrics 不迁移；下次重算可能增加 missingRereadReasonCount，反映豁免消失，不代表 provider 新增了读取。
不删除旧 workstream 磁盘文件；即使 stable plan 存在、变化或损坏，新计算都不消费它。其他 read-reuse telemetry 来源不扩入本轮。

## 4. 完整转换与失败切面

| 前态 / 触发者 | 写入及检查顺序 | 后态 / 下一消费者 | 失败与冷启动解释 |
|---|---|---|---|
| qualified 合同；Agent 产生 plan | raw output 已存在 → envelope shape → E4 subset → E3 publication identity → sealed sidecar → derived per-output plan → 既有 execution/routing | 有效 occurrence 交既有 reviewer/approval 链 | shape/subset/identity 拒绝不发布 sidecar，不启动 reviewer/gate/implementation；raw 文本不是批准事实 |
| subset 通过、发布未完成 | 延续既有多文件写入顺序；不新增 canonical write | 仅完整 E3 lineage 可被消费 | 写入前/间/后 IO 失败保持既有 IO 分类；残余文件不能因名字或 mtime 成为 authority；不宣称跨文件原子性或 R1 liveness |
| 有效 plan 待审核 | 既有 reviewer → human/automatic 声明批准 → E3 resolver | implementation/self-test 按现有 approved contract + plan identity 执行 | revise/new approval/retry 使用 E3 身份规则；旧候选不因路径仍合法而重新获得批准 |
| 实现已有工作树改动 | 实际 Git evidence → 既有 ChangeScopeEvaluator → 验收/交付链 | 仅满足各自守卫才能继续 | plan 合法不豁免实际越界；E4 不声称在工具写文件前拦截所有真实改动 |
| metrics refresh | run/tool facts + 既有显式 reread facts → metrics 写入 | UI/报告消费派生指标 | canonical plan 任意内容均不改变分类；失败不反向改变 workflow 权限 |

retry/resume/replay/进程重启复用 E1/E3 校验，不重建或回填缺失批准事实；abort/terminal 约束不变。
无旧 ledger 迁移、无 single-case rescue；新旧 binary 混用不能作为本 checkpoint 的验证样本，必须另判验证 INVALID。

## 5. 通用性独立判断

判断：PROCEED WITH GUARDS。设计具有 harness 通用性；实现后的通用性仍须下列测试证明，而非已 PROVEN。

- 输入只有 typed PlanResult、qualified TaskContract 和 occurrence identity，没有产品、域名、框架或文件名白名单。
- 路径均视为不透明合同标识，业务相关性仍归 Agent/reviewer/human。转换器与内容站点能使用同一条规则。
- 对两个非 OKX fixture 同时验证正例与拒绝；一致重命名合同/plan/后继文件后结果不变，覆盖含空格路径及尚不存在的新文件。
- shape 按 result 类型，publication 不靠某个业务 nodeId；approval 支持面沿用 E3 已声明的三种 plan 拓扑，不声称支持任意自定义角色图。
- 下游不是另建判断者：E4 守计划发布、E3 守批准 lineage、Git 守实际操作，三者必须在序列测试中同时存在。

不会解决：模型语义遗漏、业务设计振荡、实际测试质量、所有任意自定义 workflow 的闭环。此类目标不能靠路径包含检查冒充完成。

## 6. 文件范围与不可绕过测试

生产范围仅五处：新 PlanScopeValidator；AgentResultEnvelopeValidator；ArtifactContractValidator；AgentResultPromptContract；MetricsProjectionWriter。
既有 E3 resolver、workflow、schema record、Git evaluator、CLI/server/UI 不预设改动。若实际发现下游还有 authority 缺口，停止并报告，不在本 checkpoint 偷加机制。

测试沿用现有 fixture/测试类；可加一个聚焦 PlanScopeValidator 测试类，不新增依赖或 harness：

1. 两业务对称矩阵：合同子集通过；非规范/重复/未授权路径拒绝；未 qualified 拒绝；合法新文件、空格和一致改名通过。保持既有非空约束与 non-Plan sibling。plan scope shape 错误必须断言 `NOT_REPAIRABLE`，并证明不会启动 correction；不能仅断言 validation.valid=false。
2. 真实 runtime sequence：非法 plan 原始输出存在但没有 main/derived sidecar，后继 reviewer、gate 和 implementation 零新增调用；删发布前 subset 守卫必须红。
3. 三种 E3 plan 拓扑的合法 plan 经批准至 implementation；冷进程重读仍同 identity。批准期切换、全部 implementation/self-test 再入复跑既有 E3 序列，不另造路由。
4. 发布写入失败与冷读复用 E3 现有故障 fixture：覆盖 E4 检查后 sealed 写入前、sealed 后 derived plan 写入失败；不允许残余文件构成批准。不改变 IO 为物理失败的分类。
5. ArtifactContractValidatorTest 保留 per-output/embedded plan 断言并断言 canonical 不产生；恢复 stable writer 必红。
6. metrics 两个原 canonical 豁免正例改为反证：edit/reference 清单均不能消除 missing；absent/存在/替换/损坏 stable 文件分类一致；显式 reread reason 仍 recorded，非 required 仍 not_required。恢复 reader/豁免必红；已删除字段/tag 不再输出。
7. downstream parity：在已 qualified/approved 的合法 plan 后注入实际 Git 越界，既有 scope 检查仍拒绝；正常 self-test/acceptance 使用同一 qualified contract。复用已有角色/边 fixture，不调用真实 provider，不把合成样本当 real-case。
8. 全仓 subtraction sweep + 四 workflow validate + 完整 reactor；确认零 canonical writer/reader、无新业务字面量，occurrence plan readers 保持。prompt/correction 的 PlanResult 规则与机械守卫一致。

## 7. 六维不变量

| 维度 | 证据要求 |
|---|---|
| Declared by | 已批准合同 included paths；PlanResult.edit_files 只是待验计划 |
| Proven by | E1 qualified resolution + E4 精确包含检查 + E3 sealed occurrence/批准链；真实改动另以 Git 证明 |
| Consumed by | 既有 plan 发布/审批链，再由 E3 唯一 resolver 交 implementation/self-test；metrics 无 plan authority |
| Enforced before | 无效计划不得发布有效 sidecar或启动后继；实际 scope 由既有 Git 检查在继续验收/交付前守住 |
| Failure route | typed plan contract violation；IO 维持 IO；旧 stable 不 fallback；不新增自动修复 |
| Cannot-bypass test | §6 发布序列、冷读/失败切面、实际越界反例、两项减法反证及守卫突变 |

## 8. 顺序、止损与成熟度

独立设计 review → failing tests → 最小实现/删除 → focused → full package + 四 workflow validate → subtraction sweep/diff-check → 独立全链 diff review → 选择性提交。
若需新增领域文档、审批状态、第二 resolver、兼容恢复或将 metrics 变执行策略，停止回到必要性判断；不得以“完整性”为名扩大范围。
设计阶段仅 ASSESSED；实施及完整反证后才可称 PLAN SCOPE ENFORCED / CANONICAL PLAN AUTHORITY REMOVED。
E1-E4 的真实业务收敛仍 NOT PROVEN。之后另立一个自然 fresh E2E Gate，不要求人为制造 scope/revision 缺陷，不复活旧失败样本。

## 9. 独立评审吸收记录

2026-09-06：用户提供的 CC 独立只读评审结论为 Implementation READY，P0/P1 无；F1 为 correction 资格规格缺口，F2 为必要性表述补充，F3 无需动作。
已对照 `AgentResultEnvelopeValidator.ValidationErrors` 与 DesignResult 分支核实 F1：reject 会使结果 NOT_REPAIRABLE，不能用 repairable 替代；§3/§6 已补规则与不可绕过断言。
§1 已补未批准候选覆写豁免的时序；per-output plan 投影及其 AgentEvidenceIndex/ContextPackageSectionRenderer 消费者保留，不新增生产改动面。
这是设计评审收口，不是实施或 real-case 证明；下一步按 §8 从 failing tests 开始，最终仍需全链 diff review。

## 10. 实施进度

2026-09-06 17:43 +08:00：按用户授权完成五处生产改动与既定确定性覆盖，详见同目录
`post-contract-authority-continuity-e4-plan-scope-subtraction-checkpoint-20260906.zh-CN.md`。
生产前红灯、四项守卫/减法突变、两业务及三拓扑、发布 IO 与冷读、实际 Git 越界和 correction 零调用均有实测。
恢复后 full package `1397/0/0/0`，四 workflow validate 和 diff-check 通过；无新增机制、无 real-case。
当前下一步仅独立全链 diff review，不以本实施方自查替代 CC 评审，不提前提交或启动 provider。

随后 CC 独立全链 diff review 已通过，P0/P1 无，允许选择性提交。记录见 checkpoint §6；E3 基线计数明确引用最终 §20 的 1388。本件在确定性边界收口处停止，下一自然 fresh E2E 验证另立 Gate，不自动开始运行。
