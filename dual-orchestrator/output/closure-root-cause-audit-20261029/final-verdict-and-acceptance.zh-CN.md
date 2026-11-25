# 闭环失败审计：最终核定与总验收表

核定日期：2026-11-01（Asia/Shanghai）。历史统计采用 2026-10-29 审计快照；本次对照当前工作区源码，不把工作区等同发行版。本表收编既有 D02/D06/D07、B01/B04、A11、P0/P0.5 的验收义务，不创建新状态机、授权账本或实施批次。

后续实施增补（2026-11-01）：本表第 2 行的枚举诊断缺口已完成前向修复，165 项离线测试通过；[实施与证据记录](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/enum-diagnostic-forward-fix-20261101/report.zh-CN.md)区分历史首输出的正文前缀与纠正后纯 JSON 的枚举错误。本表原核定内容及 finalization-check.json 保留为实施前快照，不以此增补宣称整行、恢复机制或五项总验收完成。

2026-11-02 因果勘误：HHZL 的 correction 提示针对 occurrence 1 的正文前缀，而 run.json:218 的枚举错误属于 occurrence 2。两者不能配成“已识别枚举错误被纠正提示降格”的因果证据。下表相应行已纠正。精确枚举诊断的局部实现与离线测试仍成立，但其对原序列的解阻效果及作为首项修法的优先级未被证明；五项清单不构成已验证的实施排序。

## 最终结论与证据边界

**在已核验的历史案例中，上游错误与验收信息未被后续诊断、路由和恢复机制完整、强制地消费，造成可纠正错误重复失败、证据缺口被转成实现返工，以及失败后依赖操作员介入。这是已确认的重要闭环阻断机制；各机制对全样本失败率及成本的贡献仍需核销和对照验证。**

这里首先是**已有事实的强制消费缺口**：相关原始错误、验收输出和路由事件已经存在，应补足消费者义务。它不意味着记录链完整无缺；usage 缺失、归属异常和历史运行身份不足仍须单独保留。不能为消费问题再建一套记录来源，也不能用“已有记录”宣称控制面已闭环。

| 已确认事实 | 原始证据 / 允许的结论 |
|---|---|
| 旧 OKX 12 case、158 run，无 workflow_completed | [逐根目录复核](supplement-recheck.json)：1,479 条原始事件；末事件 node_failed 7、node_routed 2、revision_limit_reached / iteration_started / human_action_requested 各 1。此为限定快照，不是全项目成功率。 |
| BXWV 验收输出收敛，随后缺证再次进入实现 | 四轮 must_fix=3→2→1→0；[最后验收](../../../../okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/runs/run-1789003988271/outputs/acceptance-codex-review.json:27)要求补采证据；[ledger 第 318 行](../../../../okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/events.jsonl:318)进入自测，第 327 行再回实现。不能据此把四轮成本全部归为浪费或全部归为必要成本。 |
| HHZL 在一次纠正内依次遇到正文前缀和非法枚举 | [run.json 第 66 行](../../../../okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/run.json:66)开始记录首次输出，正文前缀对应[纠正输入第 3 行](../../../../okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/prompts/intake-codex-product-review.contract-correction.md:3)的 INVALID_JSON；第 120 行开始记录纠正输出，第 218 行保留其非法枚举和白名单。不能以第二次输出的错误倒推第一次纠正提示丢失该错误；也不是 Jackson 自身 bug。 |
| 9BZG/261888 已正确标为 bootstrap failure | [原始 run](../../../../okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG/.agent/workstreams/ws-20260829T122452Z-9BZG/runs/run-1788344261888/run.json:120)为不支持的 CLI 参数；不能将此例用作顶层信封误分类或 policy conflict 的证据。 |
| 历史恢复与无人值守恢复须分开 | 有 14 条 retry_requested，9BZG 占 10 条；standing authorization note 证明操作员引用过授权，不证明 runtime 验证过授权对象。该样本未观察到满足本表资格判据的自主恢复。 |
| token 汇总尚未经全量核销 | 已记账 66,240,977 token / raw costAmount USD 97.517015；漏记和可疑归属同时存在，不能称账单或严格上下界。91.3% 已记账 token 位于 run scope，不能承诺降 scope 省 8 倍。相同 token 数本身不是重复归属证明。 |

“12 条链零闭环是上述缺陷的必然结果”“模型不服从仅占固定 2/12”“全面自动续传即可解决”均不作为定稿结论。当前已有交付反例见 [原报告](report.zh-CN.md)，不因本表限定旧 OKX 样本而删除。

## 既有机制与五步总验收表

本表的 **Declared / Proven / Consumed / Enforced / Failure route / Cannot-bypass** 沿用工程纪律六维。MODELED、PROJECTED、VALIDATED、ENFORCED、PROVEN 沿用现有成熟度；单个子路径有强制检查，不等于整行已闭环。以下测试链接证明已有验证落点，**本次未执行这些测试**；旧测试通过记录不自动代表当前源码已通过。

| 步骤与 Declared by | authoritative fact / owner | Consumed by / Enforced before | Failure route | Proven by / Cannot-bypass 验收 | 当前核定与采纳成本 |
|---|---|---|---|---|---|
| **1 保持失败语义**；A11 / D05，P0 | 已发布且程序验证的 acceptance/self_test typed artifact、产物谱系与当前合同。AgentContractSemantics / VerificationEvidence / ReviewDecisionHandler 拥有语义检查；FailureArbiter 消费验收分类。 | 验收分类→workflow 分支→自测决策→取证与 dispatch。无经校验的新代码缺陷或合法修订决定，不得重新获得产品代码修改资格；约束能力和副作用，不按 implement.* 名称禁用。 | 缺证进入既有取证/人工门；明确缺陷进入原 scope repair；来源、身份、证据不合法则拒绝。 | [AcceptanceRepairSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/AcceptanceRepairSequenceTest.java:144)、[SelfTestContinuationSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/SelfTestContinuationSequenceTest.java:64)：有效取证可继续、混合缺陷可 repair、纯缺证不可自动重写，冷启动及 sibling 仍成立；再核 inline/worker/retry/resume 全程不能借模型正文新增写资格。 | [ReviewDecisionHandler](../../dual-runtime/src/main/java/dev/dual/runtime/handler/ReviewDecisionHandler.java:70)已有 continuation_kind；[full 定义](../../dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json:924)按条件分流，[EvidenceCollection](../../dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java:43)有基线验证。已检查子路径达到 ENFORCED；本表完整能力与副作用判据尚未 PROVEN。复用现有族补证，不另建路由器。 |
| **2 启动检查与精确契约诊断**；B01 / D02，P0 | process / adapterSignal / completion、parser/validator 的 typed diagnostic；执行结果和业务验收分类各有 owner，不混用两个枚举域。 | AgentExecutionOutcomeResolver 在唯一 outcome 形成前消费阻断信号；AcpxAgentResultAcquirer 在既有 deny-all correction 前消费具体诊断。输入含字段路径、实际值、允许值、错误码；不复制原始错误全文，不凭正文改 reason。 | 不兼容启动配置先拒绝；仅合法呈现错误有界纠正；身份、批准、scope 错误仍拒绝；执行不明转 D07。 | [AgentExecutionOutcomeResolverTest](../../dual-core/src/test/java/dev/dual/core/execution/AgentExecutionOutcomeResolverTest.java)、[AcpxAgentResultAcquirerTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentResultAcquirerTest.java)：注入 unknown-option、policy、非法枚举、截断，分别检查 primaryReason、具体诊断和纠正输入；错误优先级不被 envelope 缺失覆盖。HHZL 原字节作为枚举诊断回归候选。 | 已有优先级和未知字段/截断诊断；但 [parser catch-all](../../dual-runtime/src/main/java/dev/dual/runtime/handler/AgentResultEnvelopeParser.java:46)仍把枚举失败归 INVALID_JSON，[纠正渲染](../../dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentResultAcquirer.java:289)无枚举字段/实际值/允许值专门路径。此项有已确认剩余实现缺口，不能按 B01 PASS 宣称本条完成。 |
| **3 授权内恢复与请求唯一记账**；B04 / D06 / P0.5 | 现有 claim、prepared、批准/撤销、重试次数、预算许可及唯一 session/request usage。业务门授权不由技术 retry 代决；correction 与 retry 共用适用限额。 | WorkflowRuntime→现有执行入口→AgentRunCommitProtocol；每次 provider/correction 副作用前检查资格并唯一消费。触发时限应引用已核定的既有预算/调度 owner，不新增计时体系。 | 授权、预算、执行器或安全前提不满足时留明确拒绝原因；UNKNOWN 不重发；达到限额依既有 triage 升级/人工。 | [NativePermissionSequenceTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/NativePermissionSequenceTest.java)、[AcpxUsageReaderTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxUsageReaderTest.java:273)、[AcpxUsageWireCompletenessTest](../../dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxUsageWireCompletenessTest.java)。还须证明：合格 TRANSIENT 在现有服务时限内只产生一次恢复；并发、重启不重置次数、不复用旧许可；相同 token 不误去重，异 request 不借 usage。 | [C/X](../../dual-runtime/src/main/java/dev/dual/runtime/handler/NativePermissionRequests.java:59)是权限回复资格，非自动恢复授权。[UsageReader](../../dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxUsageReader.java:9)已按请求身份归属。可是 [resolver](../../dual-core/src/main/java/dev/dual/core/execution/AgentExecutionOutcomeResolver.java:25)的失败分支仍 MANUAL_ONLY，[runtime](../../dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java:456)要求 AUTO_ALLOWED。请求归属有 ENFORCED 子路径；本行恢复闭环未达 ENFORCED/PROVEN。“储备式准入”到此次恢复的具体 owner/字段未核定，不能记为已有接线。 |
| **4 安全续作与不确定执行裁定**；D07，P0 | 固定 R01/R02/R03、executor 所有权、native command intent/result、当前合同 epoch、显式恢复决定。RecordedExecutionRecovery / ExecutionRecoveryAdjudication 拥有裁定。 | inline/worker/显式恢复共用既有协议；在新 attempt dispatch 或采纳固定 completion 前核身份、旧执行停止、授权与副作用情况。 | 已完成只补本地消费；仍活跃不抢占；UNKNOWN 无条件禁止自动重发；新 attempt/abort/adopt 走当前合法决定，不改写历史 UNKNOWN。 | [AgentRunCommitProtocolTest](../../dual-runtime/src/test/java/dev/dual/runtime/handler/AgentRunCommitProtocolTest.java:123)：旧 executor 活跃、错/旧 stop receipt、错 frozen identity、重复裁定、固定完成采纳、写入中断/冷启动。停止证据须覆盖所用 adapter 的活动执行与相关工具；父退出和文件 diff 不能单独证明全部停止或副作用幂等。 | [D07 实现](../../dual-runtime/src/main/java/dev/dual/runtime/handler/ExecutionRecoveryAdjudication.java:48)已有停止回执及身份/epoch/重复决定检查；[RecordedExecutionRecovery](../../dual-runtime/src/main/java/dev/dual/runtime/RecordedExecutionRecovery.java:51)不调用 provider。已检查协议路径 ENFORCED，不证明任意后台任务或外部请求都可安全续传；本表不另建 checkpoint 平台。 |
| **5 核销后决定 revision / 上下文策略**；P0.5，后续优化依现有阶段门 | 原生 request usage、provider/model、billingMode、可核销账单；保留缺失/可疑归属，与 token_only / subscription_unpriced / pricing_unknown 两个维度。计量不因无法计价而填零。 | AcpxUsageReader→run/统一 metrics/read model；ProviderCostAttribution / ProviderBillingCsvCalibration 消费可信输入。缺价不取消真实 token；核销和预算执行是两个义务。 | 缺 identity/usage 保留 unavailable 原因；可疑归属不进已核销总额；未定价不捏造现金成本。优化决策先报告覆盖率及未知量。 | [ProviderBillingCsvCalibrationTest](../../dual-core/src/test/java/dev/dual/core/provider/ProviderBillingCsvCalibrationTest.java)、[CalibrationCommandTest](../../dual-cli/src/test/java/dev/dual/cli/CalibrationCommandTest.java)、上述 request tests。token vector、重复/缺失、订阅/未知价格均有反例；对旧 158 run 尚需可追溯核销，不能把 CSV 工具通过当成旧样本已对账。 | [ProviderCostAttribution](../../dual-core/src/main/java/dev/dual/core/provider/ProviderCostAttribution.java:13)及 billing-csv 入口已存在；本批历史 token 和现金完整核销仍未证明。[既有主线](../../docs/design/mainline-stability-and-token-governance-plan.zh-CN.md:127)也标 partially verified。复用核销入口与数据；不据缓存占比启动新上下文架构。 |

步骤 1 的“新代码缺陷事实”仅指经现有 typed artifact、身份/谱系和语义验证形成的事实，不能由模型自然语言正文、note 或步骤 2 的一个技术 primaryReason 单独授予写权限。程序验证结构与一致性也不等于独立证明产品缺陷属实；后者由测试、反例和外部验收核验。

**依赖与成本结论：** 已有机制可复用，确实无需从零建设；但“真正新增仅 grader 基线与一张表”不成立。枚举诊断增量、合格 transient 的 runtime 恢复资格/统一限额及预算消费仍有待证或实现缺口。步骤 2 是选择性恢复的输入前提，步骤 3 是自动续作资格前提；D07 现有安全保护不必等未来自动化才能保留。只在现有 owner 内补缺口，不把五步解释成五个新子系统。本文不授权实施、外部运行、提交或历史 rescue。

## 三层证明与冻结身份

1. **历史复现**：原始输入 SHA、当时 binary/classpath、schema、validator、workflow definition、配置、adapter/patch 与环境身份均可恢复时，在该冻结组合运行同一输入。复用 [FrozenRuntimeIdentity](../../dual-runtime/src/main/java/dev/dual/runtime/FrozenRuntimeIdentity.java:17) 与既有 prepared/contract ref/SHA；该类不自动证明所有外部 adapter/环境身份，仍要绑定已有运行材料。身份不全标 INVALID/不可复现，不以当前 validator 代替旧 validator。
2. **新版本回归 / 故障注入**：固定候选 frozenRuntimeIdentity，运行历史操作数及合成失败切面；记录与历史的身份差异。新代码跑旧字节只能叫历史输入回归或切面重放，不能冒称历史行为复现。覆盖正常、拒绝、durable-write 间中断、冷启动、inline/worker/retry/resume 与 sibling；CI 任务实际执行并保留结果后才称 CI 已证明。
3. **外部 grader 对照**：以下基线先冻结，再授权运行。已有 normalize_newlines 的 9,331 输入及 mutants 只是本审计自编的 harness 外功能检查，不是第三方盲评，也不是上述机制修复的收益证明。

此前 [historical-replay.json](historical-replay.json) 所记录的是当前 validator + 旧 7SNA 操作数 + 新 fixture 身份。保留原文件和标题作为历史材料，其证明范围以本段为准；本次没有重跑，也不将其提升为旧 binary 的完整复现。

## 外部 grader 的可比较基线

**先选匹配任务，而非把异构历史样本归一化成一个成功率。** 每个比较单元固定任务/验收合同 SHA、输入仓库 commit、数据和依赖快照、外部服务 fixture、授权范围/业务门策略、预算与停止时限、模型/provider 配置、grader 版本与镜像；原文档路径只作发现入口，实际身份用内容 SHA。旧基线与候选分别固定自己的 frozenRuntimeIdentity、definition/schema/validator/adapter 身份，仅放开待验证的 harness 差异。若这些条件无法恢复，该历史案例只作机制证据，不进入因果收益分母；基线须另在预先冻结任务上受控运行。

| 比较项目 | 冻结与判定 |
|---|---|
| 分组与对照 | 按任务配对运行两个冻结版本，预先固定尝试数、顺序/随机化规则、重复次数、人工策略和预算；不得挑选失败基线与成功候选。外部网络/服务不可固定时，单列该层并记录不可比因素。真实运行须另通过现有 Verification Gate 和授权。 |
| grader 独立性 | grader 消费目标仓库最终 commit/patch 及任务所需外部证据，不消费 harness 的“成功”自报作为产品判决；与作者执行上下文隔离，评分期不改测试。基准设计含参考实现通过、空/no-op 与具名错误 mutant 失败；此设计本身不是第三方独立执行证明。 |
| 主要结果 | 固定观察窗内的合格交付数/全部计划尝试数；另列 workflow 完成、实际交付、grader 通过、人工介入、预算止损与 INVALID。业务门确实阻断时如实记录，不替换成 agent 自动批准。 |
| 单位经济 | campaign 已唯一归属 input/output/cache-read/cache-write 之和 ÷ 合格交付数，失败/重试/纠正消耗均入分子；分母为 0 时未定义。归属未核销则报告账本汇总、缺失与可疑数量，不能称精确真实单位成本；订阅未定价只报 token，不编造现金。未定价与 usage 缺失不可混为一类。 |
| 机制指标 | 具名同型输入的诊断信息保真、缺证→写执行违规次数、合格恢复触发/唯一执行、unsafe duplicate effect、每节点次数与 token。机制改善和总体交付改善分别判断，不能以前者代替后者。 |
| 比较结论 | 同任务配对差异与各层样本量/不确定性；小样本不报稳定提升承诺。可按预先定义权重汇总分层结果；只归一化 token/任务规模不能消除环境、版本、授权差异的混杂。 |
| FAIL / INVALID | 授权绕过、证据不足冒充通过、重复危险副作用、质量退化属于 FAIL；grader/工件身份/触发条件/归属链错误属于对应目标的 INVALID。两者都保留在尝试清单；INVALID 不可静默删除或当零成本。 |

所有真实测试继续使用 AGENTS.md 既有 Target / SoT / Trigger / PASS / FAIL / INVALID / Stop-loss / Freshness，不新增批准仪式或预算时钟。本表是证据导航和剩余义务清单，尚非全五步 PROVEN 或外部基准已通过的证明。

## 本次核定与未完成项

本次只做文档收编和只读源码核对，未修改产品/测试代码、原 ledger 或原 IO，未运行 Maven、provider、外部 grader，也未作独立评审。既有 77 项测试是 2026-10-29 的具名定向结果，不升级为本次回归。六维检查后，总验收为 **未闭合**：已定位消费缺口并找到现有 owner；枚举诊断、runtime 恢复资格与额度、全量历史核销、冻结基线对照等仍有缺口。后续实施应继续 Analyze→Modify→Review→Reflect→Analyze，并按现有严重性与爆炸半径决定验证范围。

本次材料引用及完整性检查见 [finalization-check.json](finalization-check.json)。旧审计输入与本次所引源码分别记录 SHA；旧输入变化应明确报告，不通过重写旧 manifest 掩盖。未知不填零，未执行不写 PASS。
