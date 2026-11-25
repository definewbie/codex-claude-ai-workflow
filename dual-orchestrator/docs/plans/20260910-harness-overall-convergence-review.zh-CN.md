# Dual Harness 整体收敛能力评估（独立只读评审）

日期：2026-09-10。评审者：CC（独立复核，不采信任何自评）。
范围：dual-orchestrator @ `331257cf`（Gate checkpoint）+ `6a868174`（implementation checkpoint）下的真实样本
`ws-20260909T151234Z-BXWV` 全部原始工件，与 production 代码交叉核对。
未修改代码、未调用 provider、未启动 agent、未恢复 real-case。

## 1. 方法与证据基线

以下结论全部来自我本人对磁盘工件的直接读取：workflow/current、4 个 iteration、30 个节点、
10 个 run.json、全部 validated sidecar（含 must_fix 与 revision_responses 全文）、
两轮已解决 gate 的完整 note、gate-030 packet 投影、作者 prompt manifest 的 authoredContext、
real-case 报告与 0909 Gate 文档。代码侧核对：RevisionTracker 预算语义、工作流 definition 配置、
E3/E4 义务投递与发布守卫（沿用 E1–E4 四轮已逐行评审的结论，本轮抽查 BXWV 实际工件是否兑现）。

## 2. BXWV 事实重建

intake 一轮修订后 requirement gate 批准 → compile → contract gate 批准 → 设计三轮：

| 轮 | 设计 run | 回应数 | 评审 run | 评审结论 | 剩余 must_fix | 人工动作 |
|---|---|---|---|---|---|---|
| 1 | run-…7352861 | 0（initial） | run-…8320018 | NEEDS_REVISION | 8（AR-1..8，逐条锚定 AC/C/TR） | gate-018 revise（接受八项） |
| 2 | run-…8497371 | 9 | run-…9476432 | NEEDS_REVISION | 4（AR-5 未解决、AR-3/1/8 部分解决） | gate-024 revise（限范围四点） |
| 3 | run-…9747885 | 14（全 ADDRESSED） | run-…71120630 | NEEDS_REVISION | 2（AR-3 快照一致性、AR-5 部分唯一索引） | gate-030 **WAITING_FOR_HUMAN（未决定）** |

机制兑现情况（逐项核实）：
- 作者第三轮 prompt manifest `authoredContext.obligations = 14`，与 8+1+4+1 累计精确一致——E3 义务投递真实发生。
- gate-030 packet：activeObligations=14、revisionResponses=14、newMustFix=2——人看到的就是真实状态。
- 回应 exact-coverage 由 E3/E4 发布守卫强制；sidecar、gate、iteration、events 相互一致；**未见任何 authority 绕过**。
- 磁盘 FAIL 结论仍在：`docs/testing/real-case-reports/20260909-okx-bxwv-e2e.md`（FAIL / DESIGN CONVERGENCE STOP-LOSS）与 0909 Gate 头部。账本诚实保留 WAITING_FOR_HUMAN。

成本：10 个 run 共 65.8 provider 分钟（远低于 120 分钟止损）；设计 16–23 分钟/轮；评审 token 161K→278K→421K 随历史线性增长。

## 3. 止损依据的独立裁定

**裁定：样本级止损前提不成立；条目级事实成立。报告的 FAIL 结论依据不足，应降级为"待人工在 gate-030 决定"。**

- 条目级：AR-3 快照一致性确在两轮中为同一语义要求；我核对了作者第三轮候选正文——它确实保留了
  "lastSuccessAt 单调跨源安全"的字段级例外，与自身"单轮次来源"规则及自身测试断言矛盾，
  同时 revision_responses 对该项标 ADDRESSED。reviewer 两次拒绝该回应**是正确的**，
  这不是评审措辞问题，是作者侧真实的语义矛盾（模型错误）。
- 样本级：Gate 止损条文是"同 blocker **无新证据**连续两轮"。两轮间候选包含大量新事实
  （剩余项 8→4→2，12/14 项被真实闭合，reviewer 自己在条目上标注"部分解决"），
  样本处于单调收敛而非循环。把"一个顽固条目"等价于"样本不收敛"，是条文粒度未定义导致的
  操作者从严解读。
- 更廉价的出口当时都存在且都未用：第三轮仅针对 AR-3/AR-5 的窄修订；approve 并显式接受剩余风险；
  把 AR-5（运行期结构检查的 partial-index 反例）显式移交实现/自测阶段处理。

## 4. 五个核心问题

**Q1 是否重复设计、逐轮加码、细节升级为阻塞？**
无标准漂移证据：reviewer 全程使用稳定 AR 编号并锚定合同条款；条目数单调下降；
第二、三轮还主动**删除**过度规范（负 epoch 限制、2000–2100 范围、"不新增迁移引擎/平台"约束）。
唯一边界案例是 AR-5 第三轮残余（要求 PRAGMA index_list 排除 partial 唯一索引并配反例测试）——
它是 AC-10 幂等性的真实设计不变量，但已贴近实现细节，本来可以合法地延后到 self-test 阶段闭合。
另有一个成本信号：gate revise note 越写越长（018/024 的 note 本身在深入做设计决策），
人工/代理被拉入共同设计——这正是 0906 Gate 止损条文警告的"再写更长 note"模式。

**Q2 作者是否收到并消费合同、历史与未关闭反馈？谁裁定 ADDRESSED？**
收到：manifest 实证 14 条义务（含两轮人工 note 原文与全部历史 must_fix）。
消费：回应逐条具体，reviewer 每轮据实重裁（"未解决/部分解决"评级证明它逐条读了候选正文）。
裁定 owner = reviewer 逐轮重裁 + human gate 终裁。BXWV 中该机制**工作正常**：
作者的虚假 ADDRESSED 被连续两次抓住。代价是每次虚假 ADDRESSED 烧掉一整轮作者+评审周期。

**Q3 多轮未收敛的归因分布？**
- 模型错误（主因）：初版设计 8 项真实语义缺口；第三轮对 AR-3 的 ADDRESSED 与正文矛盾。
- reviewer 漂移：未检出（见 Q1）。
- 上下文：次要——评审成本随轮线性增长（161K→421K token），但尚未失控。
- harness 缺口：止损粒度无定义且无机械化的"同条目跨轮"追踪（义务机器 id 是
  sha(source)#index，跨轮可比性靠人读 AR 编号）；packet 有各轮计数但无收敛趋势呈现。
- 操作者：过早止损（§3）；以及"代理审批"模式——requirement/contract/design 四个 gate 由
  主 agent 依用户预授权代批（报告自述），设计 revise note 由 agent 撰写，形式上满足
  human authority、实质上是 agent 指导 agent 的循环。

**Q4 approve/revise、预算与止损支持合理推进吗？**
支持。每轮都停在 human gate，approve（含显式接受剩余项）始终是合法出口；
design.decision 未配 max_revisions（无限轮但每轮人工把关），intake=3、self-test/acceptance=1
（超限走 recovery gate，ee72c654 已修复该生命周期）。收敛失败不在机制缺出口，
而在操作者没有使用更便宜的出口。

**Q5 同类风险是否影响 plan/self-test/acceptance/scope repair/delivery？**
- plan：同一 reviewed 拓扑与 E3 机制，风险同构（长评审环 + 止损歧义 + 虚假 ADDRESSED 成本）。
- self-test/acceptance：预算=1 + recovery gate，形态有界，不同类。
- scope repair / delivery：BXWV 未到达（NOT EXERCISED），6a868174 的新增范围审批链无自然样本。

## 5. 六维

| 维度 | BXWV 实证 |
|---|---|
| Declared by | ✓ 需求/合同/AC-C-TR 锚定的评审条目；义务与回应 typed |
| Proven by | ✓ sidecar/manifest/gate/iteration/events 全链一致；我逐项复算 |
| Consumed by | ✓ 作者实际消费 14 条义务；packet 与人视图一致 |
| Enforced before | ✓ 未批准设计未进入实现；effective workspace clean，零产品写入 |
| Failure route | ✗ 环境阻塞→语义环的教训（SPM9）已由 Phase A 修复；本轮暴露的是**操作层止损路由**的歧义 |
| Cannot-bypass | ✓（权限/权威面）；收敛面无机械反证（本评估发现点） |

## 6. Findings（按严重度）

- **F1（P1，已证·操作层）过早止损**：收敛样本（8→4→2）被按"同 blocker 两轮"停止；
  FAIL 报告自身的执行记录即反驳其"未收敛"定性。后果：放弃 65.8 分钟/约 1.4M token 的单调进展，
  E2E 完成能力继续未证明。证据：三份 sidecar must_fix 计数、报告 §8 vs §止损依据。
- **F2（P1，已证·治理层）代理审批**：用户预授权下主 agent 代批四个 gate 并撰写设计 revise note。
  非代码缺陷，但使 human authority 退化为形式，且代理每轮选择"全量重修"而非更便宜的出口，
  直接推高轮数与 F1 的发生概率。证据：报告 line 3/5、gate note 风格与 reviewer 条目高度重写。
- **F3（P2，已证·harness）止损无机械支撑**："同 blocker 无新证据两轮"的粒度（条目 vs 样本）
  未定义；义务 id 跨轮不可比；packet 无趋势呈现。操作者只能凭全文阅读裁量。证据：manifest 义务 id 形态、
  packet 字段。
- **F4（P2，已证·成本）ADDRESSED 无中间态**：作者自报，reviewer 每轮全量重裁；
  评审 token 随累计义务线性增长。BXWV 代价可承受，长环场景会放大。证据：三轮 token 计数。
- **F5（P3，边界判断）AR-5 第三轮形态贴近实现细节**：合法的设计不变量 + 可延后闭合的边界案例，
  被留在设计环内。证据：R3-MF[1]。
- **F6（P3）design.decision 无预算**：无限轮本身可接受（每轮人工把关），但与 F3 叠加时环长无声增长。
- **F7（P3，观察）packet 体量线性增长**（131K JSON @ 轮 3）：E3 渲染规则有界，暂不处理。

## 7. 整体判断

**安全拒绝能力：已证明。** 四轮 E1–E4 + SPM9（拒绝未授权网络）+ BXWV（两次拒绝虚假 ADDRESSED、
未放行矛盾设计、账本诚实）反复实证：该 harness 不会让错误设计/未授权行为通过。
**任务完成能力：尚未证明，但没有结构性障碍的证据。** 历次样本中止原因各异
（SPM9 环境/权限→已修；6KVE intake 权限边界；BXWV 收敛止损），均非同一机制反复失败；
BXWV 是首次纯粹因"收敛成本判断"而非 harness 缺陷停止的样本，且其收敛轨迹本身证明
评审-修订机器在正常工作。当前瓶颈在操作层判断支持（F1/F3）与治理模式（F2），不在权威链。

## 8. BXWV 处置裁定：**继续——限范围修订**（次选：approve 并显式记录接受的剩余项）

依据：样本单调收敛（8→4→2）；剩余两项定义清晰且均源自第一轮原始条目；账本停在
WAITING_FOR_HUMAN 正是设计中的暂停点；继续成本（一轮作者+评审，约 20–40 分钟）远低于
新样本（重跑 intake→contract→design 全链）。具体动作：
1. 用户本人（非代理）读 gate-030 packet（14 义务/14 回应/2 新发现）；
2. 发第三轮**窄修订**，note 仅含两点：AR-3 删除"lastSuccessAt 跨源安全"例外、
   健康读取一次性取单一来源快照；AR-5 保留"排除 partial 唯一索引"的结构门要求；
3. 或将 AR-5 显式移交 self-test 阶段闭合（approve + note 记录该移交），进一步压缩本轮范围；
4. 修正磁盘状态：FAIL 报告加更正附注（止损依据不足，样本继续），0909 Gate 状态改为
   RESUMED/awaiting human decision——不删除原始记录，只追加更正。

## 9. 优先改进项（≤3，减法优先）

1. **Gate 止损条文消歧（纯文档，零代码）**：把"同 blocker 无新证据连续两轮"定义为
   *条目级*（同一义务 id/语义跨轮且该轮回应无新事实），并要求止损裁决引用 packet 的
   义务计数趋势作为"无新证据"的举证。必要性：直接消除 F1 复发路径；成本：一段文字；
   验证：用本报告 §3 的 BXWV 事实重演该条文，应得出"不触发"。
2. **Packet 呈现收敛趋势（小改，E3 resolver 既有数据）**：authoredStage 投影增加
   各轮 newMustFix 计数序列（如 `8→4→2`）——数据已在 ledger，仅投影展示，不新增评审层或模型。
   必要性：把"是否收敛"从全文阅读降为机械可读，支撑 approve-vs-revise 决定；成本：
   resolver 一处投影 + 一条既有测试扩展；验证：BXWV gate-030 packet 重渲染后应显示 8→4→2。
3. **操作规程明确两个合法更廉价出口（纯文档）**：(a) approve-with-explicit-residual
   （批准并逐条记录接受的剩余项/移交阶段）；(b) 窄修订 note 模板（只列条目差量，不重写设计）。
   必要性：给代理/操作者比"全量重修"和"止损"更便宜的收敛出口，抑制 note 膨胀（Q1 成本信号）；
   成本：规程一段；验证：BXWV 第三轮按模板执行。

## 10. 无需修改的机制

E1–E4 权威链（合同资格、设计/计划 scope、authored revision/approval、manifest 绑定与发布守卫）；
义务累计投递与 exact-coverage 强制；reviewer 对 ADDRESSED 的逐轮重裁（它两次抓住了虚假 ADDRESSED——
这是特性不是缺陷）；gate packet 投影；无限轮但每轮人工把关的设计环（配合上述文档修复后维持现状）；
self-test/acceptance 预算与 recovery gate 升级；scope-addition 人工审批路由（保持待自然样本验证）；
账本 fail-closed 与冷读语义。

## 11. 三分清单

- **已证缺陷**：F1（过早止损·操作层）、F2（代理审批·治理层）、F3（止损无机械支撑）、F4（ADDRESSED 无中间态的成本结构）。
- **合理风险（接受即可）**：F5（AR-5 细节边界——用出口 (b) 化解）、F6/F7（无预算环长与 packet 增长——有界且可观察）、评审 token 线性增长。
- **未验证假设**：harness 端到端完成能力（任何样本都尚未走完实现→验收→delivery）；
  scope-addition 审批链的自然行为（NOT EXERCISED）；Phase A 网络授权之后的 self-test 依赖解析路径（SPM9 后未再触发）。

## 12. 结论

不预设"必须修 harness"或"只是模型问题"的定论在此成立：权威与正确性面是健康的，
本样本的失败点是操作层判断缺乏机械证据支撑 + 治理模式把人工判断变成了代理循环。
按 §8 继续样本、按 §9 做两个文档级修复与一个小投影，成本最低且不动已证明的安全面。

## 13. 20260910 更正（用户裁定后收紧，保留上文原样）

用户确认核心结论（止损裁定错误、反馈链正常、继续同一样本、缩短修订指令、不重构 harness），
并驳回以下表述。经复核，裁定成立，按更正后口径执行：

1. **F2"代理审批=已证治理缺陷"降级为"合法授权下的执行质量问题"**。代批经用户明确授权，
   不构成越权；E3 设计本就不认证真人身份。已证事实保留：代理的决策模式（长 note 共同设计、
   每轮选全量重修、过早止损）质量不足——这正是本轮要修的对象。要求本人逐门操作不解决判断质量，
   修决策规则才解决。
2. **§7"不会让错误设计通过"改为分层表述**：deterministic 守卫（E1–E4/scope/coverage/identity）
   经突变与反证测试机械证明 fail-closed；模型语义评审仅在已观察错误上证明了检出，
   无完备性保证（E3 计划本身声明"不能保证 LLM 语义一定正确"）。两者必须分开评价。
3. **"任务完成能力尚未证明"限定范围**：历史上 captcha-auth/Mailpit 样本已记录
   workflow COMPLETED + audit ready（20260827 gate review）。准确表述是：**post-contract-authority
   变更集（E1–E4 及之后）上的完整自然 E2E 尚未证明；scope-addition 审批路径尚未行使**。
4. **8→4→2 仅为辅助证据**：收敛判定还需检查条目严重性、已修项是否复发、是否新增关键问题。
   据此改进项 2（计数趋势投影）从"立即执行"降为"暂不做"——单纯计数不能机械防止错误止损；
   多因子要求并入改进项 1 的止损条文（计数趋势只是举证因子之一）。
5. **F4"ADDRESSED 无中间态"移出缺陷清单**：作者声明 + reviewer 逐轮重裁是被评审过的设计分工
   （E3 计划明确"不新增逐项评分状态"），BXWV 恰好证明其价值（两次抓住虚假 ADDRESSED）。
   token 线性增长保留为已接受成本观察，不为此开发机制。

更正后的最小处理顺序（用户裁定，本报告认可）：
1. 给 BXWV FAIL 报告追加更正（保留原文）：准确状态为等待设计修订，E2E 未完成；
2. 不改生产代码、不加趋势面板，先继续 BXWV；
3. 下一轮仅两句差量：AR-3 健康读取只消费一个完整快照、删除再次读取数据库字段的例外；
   AR-5 现有唯一索引检查排除 partial 索引、不扩大结构检查范围；
4. 返回后按实际正文判断（差量是否落实、是否有新增关键项、已修项是否复发），
   不要求 CC 再审批，不因编号再现自动止损。
