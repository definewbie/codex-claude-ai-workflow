# W09：固定方法与验证细节的有界离线核对

日期：2026-10-27。范围：只读核对与设计材料；没有修改产品、operator 脚本、当前账本、模型产物或停止记录，没有启动 Java、原生客户端或模型请求。

**建议：PROCEED_WITH_GUARDS。先补齐现有契约的提示投影，不改 schema，不放宽 method 相等检查，不新增解析/填充机制。**本报告不是实施或续跑授权。候选修正尚未实施、验证或证明模型会遵守。

主线归属：P0，解除合法计划到 EXECUTION 的当前阻塞；不是 P1 token 优化。P0 完整 Java MODIFY E2E 尚未完成，本报告不使任何阶段晋级。

## 1. 先把题目缩准

资深工程师应额外检查三件事：这究竟是缺少表达能力，还是两个现有字段分工未说明；方法是否参与批准比较，能否安全覆盖；当前案例是否还有合法续作入口及足够储备。不能只看到一个字符串不等就删除检查，也不能直接再花一次模型请求试运气。

原始材料：正式 readiness 请求及登记记录、当前计划原始结构化文件和实际输入提示、GOAL 合同、冻结源码、现有生命周期测试、固定用量与停止报告。报告和预算表仅用于定位，不替代这些事实。

具体方法：逐字段/字节比较；按 producer→typed plan→独立复评→plan.decision→编译→EXECUTION 批准→dispatch→程序观察→独立验收追链；同边界 sibling 走查；写入前缀与冷读反例设计。工具限于本地搜索、源码阅读、JSON/SHA-256 对照。没有新增在线实验。

修订后的任务：**在不改变原批准与证据要求的前提下，确定 readiness.method、plan.validation_plan 与执行 verification_checks 的唯一职责；判断现有字段是否足够，列出最小提示投影修正及端到端验证义务，并单列当前案例的预算和冻结身份限制。**

## 2. 已确认的事实与证据上限

- 正式声明覆盖 AC-1..AC-5；当前计划的 AC 编号、登记 ref/SHA、executor、evidence_kind、detail_owner 已与声明对应。不是把之前三项缺陷重复报错。
- 五个 `binding.method` 都以完整正式方法为前缀，再追加“本条目逐项绑定 AC-X：…”及具体检查细节；因此全部不满足 `request.method().equals(binding.method())`。
- 计划已有 `validation_plan: List<String>`，且当前文件已在此列出 AC 样例、DECIMAL128 原值、展示不影响建议、不可用情形、完整回归、说明文档和范围检查。表达空间并未缺失。
- 真实输入同时给出登记方法和通用例值 `observable assertion and execution method`；readiness 投影只说条件要 match，未明确“method 原文不得追加，细节转 validation_plan”。本次人工反馈要求“精确消费”，也未明确这个字段分工。**指令投影不充分是有源码/输入支持的判断；不能据此证明模型在清晰提示下必然不会再错。**
- 精确相等不是孤立检查：`sameVerificationBoundary` 也比较 method，编译将 bindings 写入 EXECUTION，后续资格消费它。不能把改变 method 仅当措辞变化。
- `VerificationEvidence` 强制逐 AC 的实际工具证据、来源合同和源码身份、必要的 actual exit；**它不解析 method 或 validation_plan 的自然语言来证明测试语义。**语义充分性仍由自测/独立验收及人门负责，不能声称程序已逐字落实测试方案。
- 本次发现是原始材料与源码谓词的离线对照，不是已调用 Java qualifier 得到的新 primaryReason。原计划已发布；当前新复评尚未发生。

## 3. 职责边界

| 内容 | owner / source of truth | 模型可以做什么 | 不得被解释为什么 |
|---|---|---|---|
| 正式 method、执行者、证据类别、环境/期限/GOAL 绑定 | operator 经 `record-readiness` 登记，固定 ref/SHA + 原事件 | 对选用声明原文引用；不扩写该 method | 不证明测试已执行、不授 EXECUTION 写权限 |
| 各 AC 的样例、断言、文件/报告检查和步骤 | 作者的 typed `plan.validation_plan`，随完整计划固定并独立复评 | 按 AC-ID 编写具体细节；无需新字段 | 不得更换工具/环境/执行者或减弱已批验收；不产生观察事实 |
| actual_exit_required / expected_exit_code | 计划提出，按批准标准核验并随 EXECUTION 批准 | 明确列出；明确要求的必须保留 | readiness 不能提供实际退出码；不统一强设为 true |
| 实际观察与逐 AC 结果 | 程序固定的 verification-observations + self-test/acceptance typed checks | 写 assertion，选择真实 execution/tool/material refs | 不能以计划、声明、模型正文或报告路径冒充执行成功 |
| 写授权、验收与交付 | 原 GOAL / EXECUTION / 独立验收 / 交付门及账本 | 经原入口提出候选 | method 相等、计划 READY 或有测试说明均不是授权/交付 |

“固定”指一次有效登记的内容身份，不是方法永久不可更改。真正需要换方法时，走既有正式声明替换及受影响复评/批准；不能把变化藏入 validation_plan。只添加不改变既有义务的具体测试细节，仍由独立评审判断其充分性和一致性。

Definition 是既有 terminal-delivery@5 及契约；Instance 是本 workstream 与节点 occurrence；Execution 是九次固定 run、后续实际执行及观察。当前分析文档不新增任何 authoritative 状态。

## 4. 最小改动候选

### 建议先做：两个现有提示投影落点

1. `AgentResultPromptContract.verificationInstruction(PlanResult)`：明确 method 来自所绑定有效声明，JSON 解码后的字符串必须完整相同，不追加 AC 说明、不缩写、不归一化空白；AC 细节放现有 validation_plan，并逐条标明 AC-ID。通用示例仅示意形状，不是可行性证据。
2. `VerificationReadiness.context`：对所有收到 GOAL/readiness 上下文的作者和 reviewer，给出同一分工；没有当前声明时如实报告缺口，不造 ref，不要求模型复制占位方法凑合格。

分工指令在现有类中只定义一份，两个投影复用，避免新建两段会漂移的规则正文；不为此增加新类或 registry。

推荐指令要点：

> When binding a current readiness declaration, copy its method exactly as a JSON string value; do not append, paraphrase or normalize it. Put criterion-specific examples, assertions and report/code checks in plan.validation_plan, labeled with approved criterion IDs. These details cannot change the declared verification prerequisites or approved acceptance requirements. If a different method is necessary, report the missing readiness; do not silently substitute it. Readiness is not execution proof or approval.

保留所有 parser、DTO、publisher、validateBindings、sameVerificationBoundary、compiler、dispatch、证据消费者。**这是修正硬契约的模型侧投影；硬契约已经存在并强制消费，不是用 prompt 代替 guard。**正式渲染测试必须覆盖实际完整输入，不能只断言一段静态字符串。

### 为什么暂不做更大的改动

| 方案 | 判断与成本 |
|---|---|
| 不改输入分工，直接再试模型 | 不建议。已有一次明确返修仍追加 method；重复成本可观察，且储备不足。 |
| startsWith / 语义相似即可通过 | 拒绝。会放行“原文 + 改用其他环境/跳过测试”等改变方法的后缀，破坏批准比较。 |
| 程序把当前输出 method 裁短/覆盖成登记原文 | 拒绝。静默丢弃模型提出的内容，可能洗掉实际方法变化，并改变原始产物含义。 |
| 模型仅报 readiness ref，程序解析/填充 method | 后备方向，当前不实施。能减少复制负担，但需分开模型 wire 与完整领域 binding，强制选择/歧义拒绝、publication/correction/lineage 与历史契约处理；不是两行接线。 |
| 每个 AC 重新登记一份恰好匹配旧输出的声明 | 不作为根因修复；要重新人工确认真实可用性与合法登记时点，再让新计划引用新 SHA，不能修改旧记录或追认旧输出。 |

ROI：先修两个现有投影是当前最低永久复杂度的前向修正。收益是消除可见字段职责歧义；不承诺 token 节省或一次跑通。若明确投影后的合法输出仍反复被转录负担阻断，再拿真实材料评估 ref-only；不先建设通用绑定/方法解析器。

## 5. 完整转换与失败切面（候选保持原协议）

| pre-state / SoT | action、owner 与顺序 | post-state / 下一消费者 | 写点、重试与中断解释 |
|---|---|---|---|
| 已批 GOAL + 当前等待的合法 subject | operator 原登记入口：核 GOAL/epoch/环境/期限 → 原 L 写会话固定声明和事件 | 已登记 ref/SHA；readiness context 消费，零批准/派发 | 原 prepared mutation 失败补固定字节；无 event 的 orphan 不算登记；同 id 异内容拒绝 |
| 作者 occurrence READY + 原路由义务 | 原准备链构造提示；R01/claim 固定后重核资格，再 R02 intent → 模型 | 新 raw 与 typed plan 候选，method 原文、细节在 validation_plan | R01/claim 前缀不产派发资格；R02 后不明沿 D07，禁自动重复；此次不增加重试 |
| 有效模型输出 | strict parser → shape/scope 校验 → 唯一 outcome/发布 → R03 固定 → L03 消费 | 新计划身份，原 context 和独立 reviewer 消费 | 不裁短/改写 raw；发布/完成中断沿 D06/R03；不能仅凭 sidecar 推进 |
| reviewer 当前 exact subject | 独立复评 → plan.decision 原 readiness 核验 | READY 经合法 gate/compile；缺 readiness 返回原等待/补齐出口 | 不能让“无声明的首份计划”在发布时直接失败而失去登记入口；不新增此类 guard |
| 已复评计划 + 当前 GOAL | compiler 再核 binding、范围、谱系 → 固定 EXECUTION 候选 | 当前人门消费精确候选；无 approve 仍零实现 | 新候选不是旧批准；核验失败不发布合格合同；不得由本报告或 operator 标志代批 |
| 已批 EXECUTION | qualification 再核声明活性/环境/内容身份 → 原 R01/R02 → 实现和自测 | 源码与程序观察，由唯一 evidence 消费 | 撤回/过期/环境漂移应在新副作用前拦截；外部未知走 D07；旧 binary 不借新行为证明 |
| 自测及独立验收 | 实际工具观察固定 → 每 AC verification_checks → 原 evidence + 语义验收 | ACCEPTED 或 ACCEPT_WITH_NOTES 再走交付门；REJECTED 走原返修 | 计划说明不能替代观察；明确要求 actual exit 时缺值仍拒；无测试/跳测试不得报通过 |
| 交付资格成立 | 原批准 → candidate/apply → commit/finalize | 原终态与只读历史 | D09 外部窗口原样；未执行环节不可记 PROVEN；终态不得重开 |

retry/resume/abort/replay 均保持原入口、C/X 和 D06/D07。新增提示不能为旧计划添加新含义；冷启动只能读固定原字节。历史 raw、旧 sidecar、停止记录不修补。修正发行不能临场换进当前冻结 workstream：是否支持新旧运行身份，必须通过既有检查明确决定；不得自动做 backward compatibility 或救样本。

## 6. 验证方案（尚未执行）

优先复用 `AgentResultPromptContractTest`、`VerificationReadinessTest`、`PhasedContractContinuationTest`；只有现有族不能承载时才补最小测试文件。

1. **真实形态成对**：正式登记覆盖多 AC；计划原文 method + AC 细节只在 validation_plan，可经真实 plan.decision→编译→当前 EXECUTION 批准→dispatch。原文后追加看似有益后缀仍拒，改一个字/换声明 SHA/错 AC/缺声明均拒。使用本次真实形态构造离线 fixture，绝不改原计划。
2. **完整渲染 sibling**：初次作者、返修作者、一次 deny-all 格式修正、另一客户端 reviewer 的实际输入都含一致分工；例值不能被误解为登记声明。GOAL 无 readiness 时允许原登记/补齐回路；不误改 intake/design 输出契约。
3. **细节不丢失**：额外 AC 断言在 typed plan、固定 plan projection 与 reviewer/后续 context 中保持；仅复制 method 但没有验证计划不能过现有必填检查。没有实际工具结果时即使说明完整也不能自测/验收成功。
4. **批准与证据**：方法/执行者/环境/证据类型/退出码要求变化仍触发原失效与重批；details 中提出跳过 Docker 或降低已批标准，独立复评必须拒绝，不把文本放到 validation_plan 当豁免。明确 actual exit 的正反用例保留。
5. **写点/冷读/入口**：复用原 readiness prepared mutation、R01-claim、R03-L03 的关键失败前缀与冷子 JVM；正常追加不改历史身份，孤立文件不能补造资格；inline/worker、相关四变体消费相同契约。只改投影无需创造新写入协议。
6. **删除式反证**：移除 verbatim/细节去向规则，完整渲染断言必须失败；把 equality 改为 startsWith，则“原文+改变方法后缀”负例必须失败。反证与常规绿灯分列，不凑数。

这是身份/批准边界的前向修正候选，保留 BOUNDARY 级实施复审要求：若获批实施，运行必要聚焦与生命周期用例及一次全模块离线回归，重冻后交 CC 独立复审；不因生产 diff 小就自动降为 TRANSIENT。当前纯材料工作不运行回归，不沿用旧绿灯为本候选背书。

## 7. 六维审视与成熟度

| 维度 | 当前结论 |
|---|---|
| Declared by | 正式 readiness / GOAL / 既有 plan 与 EXECUTION 契约；候选说明补齐固定与自由内容的分工 |
| Proven by | 本次原始字节与源码对照；现有测试源码仅证明已有设计承载点，未重跑；候选模型遵守和 E2E 未证 |
| Consumed by | plan.decision、compiler、qualification 唯一调用 readiness 校验；validation_plan 经 plan projector/context；实际结果走 VerificationEvidence |
| Enforced before | 现有 readiness 强制位于 EXECUTION 编译/批准资格/dispatch 前；尚未给当前新计划做 JVM 拒绝 replay，不声称已在该运行触发 |
| Failure route | 既有 readiness 补齐/返修、人门、D06/D07；当前 operator 停止继续生效，无自动追加请求 |
| Cannot-bypass test | 上节完整成对与反证为待实施义务；旧守卫仍不能靠后缀相似、说明完整或人工分析替代 |

边界分工为设计判断；最小修正为 **MODELED，NOT_IMPLEMENTED / NOT_RUN**，不能称已闭环。没有 Java/acpx/schema/预算/reader/新服务/新锁改动需求。若实施发现必须创建解析器、新 DTO/历史迁移或改写原模型输出才能成立，停下重新比 ROI，不自动扩大范围。

## 8. 当前进展、累计账目与后续动作

- workstream：`ws-20261026T142203Z-7SNA`；仍是 RUNNING，`node-plan-codex_plan_review-025` READY；无 EXECUTION 批准、无实现；本轮没有推进节点。
- 已固定消耗 **2,034,803 token / 9 请求 / 1,979.9747878720627 活动秒**。上限仍 **4,000,000 / 18 / 3,600**，不清零；本次离线核对不新增案例执行回执或 provider/run 时间事实。
- 原固定报告：余额 **1,965,197 token / 9 请求 / 约 1,620.025 秒**；一次额外计划返修+复评连同尾链与修正储备需 **2,025,000**，差 **59,803**。这是规划储备差额，不是预测下一次必然超支，也不是建议自动加到某个新上限。
- 两份停止记录及当前 raw/账本/用量永久保留。方法核对正确不代表当前预算已经允许返修；人工离线重写计划再交复评也不是合法替代。

**下一步最小动作：先审定“原文 method + validation_plan 细节”的最小方案，再决定是否授权该投影修正。当前案例怎样继续、是否改预算、是否换冻结发行是独立待决事项；本报告不代决、不启动任何模型请求。**

如交 CC 复审，应请其独立重建整条转换，重点反驳：两字段是否确实足够；更小方案是否存在；是否把语义更改藏进细节；是否低估冻结身份/储备成本。不得预填 PASS。

自查结论：分析 → 候选材料 → 对照消费链及失败切面 → 反思过度改造成本 → 回到边界分析，形成上述最小方案；没有独立 agent 评审，也不把自查称为独立复审。过程中两次定位不存在的候选文件名、一次资源 glob 无匹配及一次过宽搜索输出截断，均为案头定位问题，已用实际存在文件的精确锚点替代；不计为生产反证或 Gate 故障。

附：`source-index.json` 为精确源文件/SHA/行锚；`readonly-comparison.json` 为本次有限字节比较；`preservation-before.json` / `preservation-result.json` 为保全核对。它们均为分析材料，不是新的业务 SoT。
