# 工作流优先收敛：实施核验记录

基线 `3362eb42`，2026-09-13。主 agent 自行核验；不代表独立评审。
保留接手时全部 dirty 文档、playwright 与 output 材料；不恢复历史 case。

## Boundary Review（修改前）

- Target phase: P0；当前已实现但本次两个反例未 Verified，更未 Promoted。
- 可验证目标：合法人工修订到同一作者、复评及重新批准；前置拒绝到最终 node/current/recovery 原因不变、零 provider、零业务 sidecar；非法身份仍拒绝。
- Problem type：人工决定消费者资格漂移、执行与产物阶段混淆。
- Definition：bundled full/contract/terminal-delivery 与 external small JSON；LiteFlow 选边。
- Instance / Domain state：current、node、gate、iteration、events；RoutingEngine 提交决定。
- Execution / Execution state：node execution 是本地执行，provider run/attempt 是外部执行；目录不代表调用。
- Artifact ownership：已有 resolver 校验输入身份；acquisition/ArtifactContractValidator 校验和发布输出。
- Projection surfaces：RecoveryAssessment -> WorkstreamAttention、CLI、server/UI；不增加第二 read model。
- New domain object needed：否。现有 NodeResult / execution facts 足够；不增加持久化 authority。
- Decision：先在真实 handler/runtime/ledger 上运行反证，再修改现有 owner。保留严格 subject/SHA/iteration、批准和 scope。

## 最小设计 delta（实现前）

既有 outcome 字符串无法唯一确定失败阶段（同一资格也会在发布阶段重验）。
在 NodeResult 的程序结果中记录封闭的 `input_preparation_failure` 事实，由
AgentExecutionResultFacts 的失败构造入口生成：仅允许 FAILED、空 outputRefs，固定 reason/code/details、
providerInvoked=false、retryPolicy=MANUAL_ONLY。没有任意 map passthrough；LLM schema 不接收该事实。
validator 与 finalizer 必须共同消费同一事实并拒绝与候选/成功/执行 metadata 矛盾的结果；
RecoveryAssessment 消费其持久化值。此事实只说明本次输入拒绝，不产生批准、权限或新的流程状态。
调用前抛异常转为相同封闭失败，保留异常类与详情；调用后的异常仍沿原执行清理路径，不能虚构 provider outcome。
没有字段的旧失败不猜阶段；历史 case 不在本批续作支持范围，不添加兼容解释。

## Transition table / 失败切面

### 组合回归发现的既有分类边缺陷（修改前核对）

真实 handler/validator/LiteFlow 的 transport 替身反证：合法 `TOOL_LIMITATION`，没有产品修改项、仅缺验证记录，
仍自动二次调用实现并最终到 acceptance gate；inline/worker 均红。
`CONTEXT_INSUFFICIENT` 具备同一合法输入及同一错误目的地，作为兄弟路径验证。
沿既有四份 workflow 将这两类前置条件交给现有 implementation repair 人工门（normal，不新建 iteration）。
人可补证据或明确 revise；collect 仍绑定源码/合同基准并经 self-test、acceptance。
不增加动作/authority，不改变具体 IMPLEMENTATION_BUG 自动修复、VERIFICATION_INSUFFICIENT 自测补采和 REVIEW_NOISE 复评。
同步更新既有分类建议、定义与预算/对偶测试及 smoke 断言；不是凭边名统一重写分类。

| Pre-state / owner | Action / 执行者 | durable writes / 副作用顺序 | Post-state / 唯一消费者 | 失败、重启与拒绝 |
|---|---|---|---|---|
| 当前 open gate + verified review/subject，ledger | 人工 revise/clarify + note | 核验 -> resolved gate -> 完成 gate node -> iteration/后继 -> route events -> current | 新作者 READY；resolver 消费 trigger、subject、note | 非法动作首写前零写；各写间失败不得重复决定或消费孤立后继；不自动补事务 |
| 作者 READY，node/iteration | runtime/worker 准备输入 | agent task -> node execution -> RUNNING -> 输入资格 -> provider（若可达） | 候选供 validator；前置拒绝 FAILED 供路由 | 输入拒绝不进入模型产物解析；目录/claim 不作为调用事实；throw 保留原异常 |
| 实际 provider attempt | adapter 执行 | prepared/run -> attempt -> output candidate -> acquisition -> publication -> finalize -> task/execution -> node/events/current | 经验证 sidecar 才能供 reviewer/正常边 | 执行失败、缺输出、纠错拒绝各自保真；候选存在不等于已发布 |
| WAITING_FOR_RECOVERY + FAILED | 人工 retry / abort | 核验 -> 同节点 READY/诊断 snapshot -> event -> current -> 新 execution | 当前批准重新供作者消费 | 不产生语义 iteration；部分 retry 写入不能调用；不继承过期 grant |
| repair gate，源码/合同基准 | collect / prepare / submit | baseline -> 材料副本/ref/SHA -> self-test -> acceptance | 同材料由自测和验收消费 | 源码或材料变化拒绝；孤立文件无 authority；材料不是 ACCEPT |
| 合法 acceptance，固定 candidate | 人工最终决定 | report/audit/candidate -> gate decision -> terminal | 完成 read model；apply 重验 candidate | 终态 run/retry/approve 零新执行；错误 bytes/缺证据拒绝 |

## 反证与修复结果

本轮测试期望先取自合法用户操作，外部替身只替 provider transport。新增的
HumanRequirementContinuationSequenceTest / WorkflowFirstLifecycleSequenceTest 使用真实 handler、
输入准备、validator/finalizer、文件账本和 LiteFlow；不能把既有局部 fixture 全部描述成同等覆盖。

| 实际反例 | 最小修复与证据 |
|---|---|
| READY 人工 revise 后作者资格拒绝，最终误报 JSON 缺失 | 人工 READY/BLOCKED 同等允许；完整到有效 patch、复评、两次批准及下一作者资格；inline/worker 成对 |
| READY 下无 note 的 revise/clarify 可以持久化 | core 统一 note 规则，删除 ledger 四处重复规则；全部账本文件字节不变反证 |
| 只保留 completed gate node、删除 resolved gate 仍可调用 | 复用现有 resolved gate/event/subject/SHA 身份链；错 action/note/iteration/subject、缺 gate、篡改 proposal 均零 provider |
| clarify 复编译覆盖稳定 resolution，旧轮 clarification 无法重验 | 改读原 compiled occurrence 和对应批准链；自动 NEEDS_REVISION -> clarify -> 第二次完整批准实际通过 |
| 输入准备存储异常逸出，节点留 RUNNING；typed 前置失败被后处理改写 | 封闭输入失败事实统一 producer；proposal、批准合同、authored manifest、存储、retry 诊断均到最终 ledger/recovery；CLI/API 同源 |
| provider 限流但返回有效候选时，先发布 sidecar 再变 FAILED | 发布前只读消费已有 execution finalizer；失败保留原执行原因，无业务 sidecar，artifact 事实明确未发布 |
| 仅缺上下文/工具前置仍自动调用实现 | 四份定义进入既有人门；人工 collect/revise 各有完整自测和复评，collect 零额外实现；不改其他分类 |

## 十二场景消费链核对

| 设计场景 | 本次使用的证据与边界 |
|---|---|
| 1 正常完整产品链 / terminal | 新 LifecycleSequence：small、full、contract、terminal-delivery × inline/worker，逐门到 COMPLETED；终态 resume/worker/retry/approve 无新增执行或文件改写。既有 DeliveryApplicationServiceTest、DeliveryCandidateWriterTest 验证 audit/candidate bytes/交付拒绝 |
| 2–3 人工需求修订及非法身份 | 新 HumanRequirementContinuationSequence，READY/BLOCKED、note、subject/SHA、resolved gate/iteration 与复评再批准 |
| 4 自动 / clarify / design-plan / 晚期合同返工 | 新自动+clarify 实际序列；既有 AuthoredReferenceCorrectionSequenceTest（真实 adapter，两模式）、AuthoredStageSequenceTest、AcceptanceRepairSequenceTest（后两者使用受控 handler，验证领域消费链） |
| 5 具体实现与缺证据分流 | 新 LifecycleSequence 的 TOOL/CONTEXT 人工处置×四变体×两模式；既有 AcceptanceRepairSequenceTest.evidenceOnlyAndMixedRepairUseDifferentRealProducers、SelfTestContinuationSequenceTest 真实条件边 |
| 6 外部材料 | 既有 ExternalVerificationEvidenceTest：prepare/submit 固化同材料 ->真实 runtime 自测与验收 sidecar；源码/材料改动、摘要冒充 ref、冷 JVM、写间失败拒绝；EvidenceCollectionSequenceTest 双执行模式与异常后校验 |
| 7 scope 与未知请求 | 既有 AcceptanceRepairSequenceTest：申请不是授权，两次新批准后再 Git 验证，篡改首写前拒绝；现有 request_action 无路由、仍等待人，定义和接口测试 |
| 8 前置拒绝与投影 | 新 / 加强 AuthoredManifestDispatchTest、RetryFailureContextSequenceTest、LifecycleSequence、HumanRequirementContinuationSequence，真实最终 ledger；RecoveryShowCommandTest、WorkstreamApiControllerTest 共用 RecoveryAssessment |
| 9 执行 / 输出 / correction 拒绝 | 新有效候选+rate-limit 发布拒绝；既有 AcpxAgentTaskHandlerTest、AuthoredReferenceCorrectionSequenceTest、ArtifactContractValidatorTest、AgentExecutionFinalizerTest 保留空输出、有限纠错、偷改、权限/quota 分类 |
| 10 同节点 retry | 既有 RetryFailureContextSequenceTest 诊断进入真实新执行/后继；加强损坏快照到最终原因。EvidenceCollectionSequenceTest 保留原基准，既有 grant/recovery 测试不继承授权 |
| 11 写间失败 / 冷读 | 新 gate、node、iteration、后继、event、current 写入故障；新 sidecar 已写但节点完成失败，冷 resume/worker 不调用 reviewer/重复 provider；既有 AcceptanceRepairInterruptionTest 与外部材料/交付写间失败 |
| 12 未知矛盾事实 | 现有 FailureClassifyHandlerTest 机械分类组合、伪造/未知 remediation 冷读拒绝；新增输入阶段标记与 success、候选 refs、run metadata 矛盾时 validator/finalizer 同时拒绝 |

## 六维闭环与成熟度

| Invariant / Declared by | Proven by | Consumed by | Enforced before | Failure route | Cannot-bypass |
|---|---|---|---|---|---|
| 人工合法意见，core 动作 + workflow | resolved gate/node/events、精确 subject 及有效 patch | 原 revision resolver、reviewer、compile/approval | provider 及 proposal 发布 | 输入失败 -> WFR / 人工检查 | 正反修订序列、删除 gate/SHA、关键写入故障 |
| 输入准备拒绝，machine-owned NodeResult | 原资格 failure 或异常类/details | validator + finalizer；ledger -> RecoveryAssessment | provider/产物解析/发布 | FIX_INPUTS_BEFORE_MANUAL_RETRY，retrySafe=false | 两模式各 producer；伪造/矛盾 marker 不可绕过 |
| 执行失败不可发布，既有 execution finalizer | process/signals + validated candidate | publication assessor 与最终 run metadata | sidecar 写入及正常路由 | 原 execution 原因 | 有效 JSON + rate-limit、已有 correction 拒绝 |
| 前置条件交人处置，四份 workflow | canonical 分类 +当前 gate 决定 | LiteFlow/ledger，collect 的源码基准守卫 | 再调用实现或材料发布 | 既有人门/原 recovery | collect/revise 对偶、scope/材料兄弟序列 |
| 文件不等于完成，ledger/交付领域 | completed node/event 与 audit/candidate | reviewer、terminal 与 delivery service | 复评/完成/交付副作用 | 安全停止，不自动救援 | sidecar/节点完成故障、终态零副作用、交付 bytes 篡改 |

上述已通过定向序列的边界达到受控 `PROVEN`；不等于自然真实产品 E2E 已证明。
多文件失败只能证明安全停止，不承诺事务原子性或任意 crash 自动恢复。

## Review -> Reflect -> Analyze

主 agent 自查，未启动 reviewer，不能称独立评审。复核目的地/失败阶段/持久化消费者，保留 scope、批准、身份和证据规则。
同一动作原本被多个 owner 重新解释；本轮将 note 收回 core，把输入拒绝共同交给两个后处理消费者，
把 clarification 的历史依据从稳定投影移回原 occurrence，并让发布消费原 execution 判定。
分类边的问题通过合法输入走到真实实现才成立；因此只改两类前置条件的已声明目的地及兄弟定义。
没有新增模型层、DSL、权限/恢复平台，没有改 LiteFlow 内核，没有为历史 case 增加兼容。

旧的孤立 gate node/稳定 resolution 伪装历史批准不再受支持，明确 INVALID；历史失败样本保持只读。
自然分支未发生一律 NOT EXERCISED；不以 synthetic 序列外推模型完成原 OKX 产品的能力。

## 最终执行结果

全 reactor package、真实 CLI 受控 lifecycle 与真实 E2E Gate 评估待本节补齐实际结果。
