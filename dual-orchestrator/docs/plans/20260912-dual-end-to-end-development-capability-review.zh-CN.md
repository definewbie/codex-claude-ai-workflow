# Dual 端到端开发能力独立评审

日期：2026-09-12（两轮：初审；用户对"未验证部分"追问后二次核定并更正两处初版误断）。
评审者：CC（只读评审；基线 `8708eb76`；未调 provider、未恢复样本、未改 production 代码、
未启动 agent；离线测试与离线回放除外）。既有报告仅作线索，所有关键结论由我对代码、测试
与账本原始工件的直接取证得出；二手来源在文中单独标注证据等级。

修订归属：2026-09-12 用户同意 Codex 复核勘误后，由主 agent 修正本稿的事实、因果与建议
口径；不是 CC 的第三次独立复审。CC 原主张与本轮更正依据保留于 §10。原有全量测试与
GCTW 离线回放仍归属 CC；本轮仅重读代码和账本、复算统计，未重新运行该测试套件或回放。
本稿是能力评估，不是实施计划或新样本启动授权。

## 0. 总判定

**历史上已具备完整交付能力，且有使用相同 Claude 后端的一手成功记录。** Dual 的多 agent 协调基座
（workstream / workflow 状态机 / 文件账本 / acpx 双 agent 集成 / 类型化契约 / 人门 / 恢复
模型）有完整开发路径：全生命周期 27 类节点覆盖
intake→contract→design→plan→implement→self-test→acceptance→delivery，契约校验在 runtime
唯一执行路径上双层落地（acquisition + re-validation），路由决策只消费 orchestrator 写出
的 contract sidecar，人门与语义失败均不可被 scheduler/autorun 绕过。离线测试 1493/0/0/0
（本机复跑核实）。**一手端到端 COMPLETED 账本在盘**（本日补验，初版误断为"不在盘上"）：
`ws-20260828T054556Z-AHBV`（2026-08-28，claude=zhipu/glm-5.3 + codex=openai/GPT-5.6-Sol）
经 acceptance REJECT→`IMPLEMENTATION_BUG` 分类→回实现修复→二次验收
`ACCEPT_WITH_NOTES`→delivery（pr-body/merge.patch/commit-message + worktree 真实 commit
+571/−237）→人门 complete，16 runs / $4.88；另有 `ws-20260818T011340Z-WA53`（39 runs /
$29.09，多 provider）与 cancer-clinic 系列 8 个 COMPLETED。历史 gate 文档（T5 v3、A10）
与之一致。上述金额为账本金额字段汇总，不是已核验的实际账单；其中 Claude 的
subscription_token_only 不代表按此金额实际计费。历史成功不等于当前版本、工作流与
双模型组合已完成自然回归，更不证明所有失败续作分支均闭合。

**当前变更组合在 OKX 系列上的稳定交付：未证明（NOT PROVEN）。** 2026-08-29 起11个
workstream 均未 COMPLETED；当前账面为7个 WAITING_FOR_RECOVERY、3个 WAITING_FOR_HUMAN、
1个 RUNNING（SPM9，历史留存，非本次正在执行）。其中 SPM9 已执行1次验收 REJECT，BXWV
已执行4次验收 REJECT，不能将“未完成”写成“未到验收”。累计154次 agent run；约1.87M
输出 token、$96.7 保留为 CC 原统计，金额只代表 usage 字段汇总，不作实际花费或方案收益证明。

这些样本混合了不同 harness 版本、工作流/合同、权限环境、模型组合和 operator 决策；
6KVE 的 Claude 后端为 DeepSeek-V4-Pro，Codex 也历经 GPT-5.6-Sol、gpt-5.4、GPT-6-Astra。
因此不是“同一模型配置、只改变复杂度”的对照。34路径是 KPPZ 合同，14条义务是 GCTW
修订时的事实，不能拼成所有11个样本的统一条件。**任务/契约复杂度是待验证的影响因素，
不是已证明的决定变量；当前也不能证明哪一类因素贡献最大。**

**一句话结论：Dual 有历史完整交付实绩；当前 OKX 系列仍未完成，拒绝能力与续作能力必须
分别判断。** 已观察到模型输出错误、harness/环境阻碍和操作止损共同影响推进；不把历史
成功外推为当前可靠，不把当前失败归结为架构完全不可用或模型能力不足。

## 1. 评审范围与证据等级

| 证据 | 等级 | 说明 |
|---|---|---|
| 代码与测试（372 主类 / 1493 测试） | 一手 | 本机离线复跑 `mvn -o test`，8 模块全绿（1493/0/0/0），与 `60725e64` 记录一致 |
| OKX 系列 11 个 workstream 账本 | 一手 | `/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-*`，直读 `workflow/current.json`、`nodes/`、`runs/*/run.json`、`events.jsonl`、`gates/`、`prompts/`、原始 agent 输出 |
| 双日最新样本（GCTW、KPPZ）逐字段追踪 | 一手 | 含 prompt 规范义务 ID 与 agent 输出的逐字符比对 |
| 一手端到端 COMPLETED 账本（本日补验） | 一手 | `captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV`（glm-5.3 主控，含修复回路与 delivery 工件）、`ws-20260818T011340Z-WA53`、cancer-clinic 系列 8 个 COMPLETED；A10 的 /tmp 目录已被系统清理 |
| GCTW 失败的离线回放（本日补验） | 一手 | 将 GCTW 原始输出经当前 HEAD 校验器重放：判 `SCHEMA_REPAIRABLE`，correction target = `result.revision_responses.13.obligation_id`——`10cd2fbe` 修复对该真实失败前置可达 |
| 历史 PASS（T5 v3 / A10 gate 文档 / public benchmark 18 PASS） | 二手 | A10/T5 原始目录已清理；采信 gate 文档与 benchmark-index 的交叉一致性 |
| 既有失败报告（real-case-reports） | 线索 | 关键主张均已被我以一手证据独立复核（GCTW、KPPZ、9BZG 死点已复核；HHZL/FZ8K/BXWV/245D 死点从账本 current/nodes 复核） |

方法：不预设问题归属（模型 / harness / 环境），从代码执行链 → 状态机 → 样本状态转换 →
失败续作路径四层交叉验证。

## 2. 架构取证

### 2.1 执行链与不可绕过点

Runtime 唯一推进路径（`WorkflowRuntime.executeAndRecordNode`，dual-runtime
`WorkflowRuntime.java:304-340`）：

```text
handler 执行（EvidenceCollection.execute 包裹）
  -> ArtifactContractValidator.validate      ← 契约层 2（runtime 重校验）
  -> AgentRunMetadataFinalizer.finalizeNodeResult
  -> ledger.completeAndRoute（edge 路由）
```

关键不可绕过事实（代码核证）：

- **双层校验**：agent 结果在 acpx adapter 内先经 acquisition 校验
  （`AcpxAgentResultAcquirer`，含 presentation 恢复、strict parse、envelope 校验、至多一次
  同会话纠错）；节点完成后 runtime 再次从落盘产物重校验
  （`ArtifactContractValidator.validateAgentOutput`），且 acquisition 侧更严的 diff 拒绝不
  会被重校验冲掉（`ArtifactContractValidator.java:71-77`）。
- **决策只读 sidecar**：`ReviewDecisionHandler` 经 `AgentEvidenceIndex.latestDecisionFromRefs`
  只读 `.contract.json`（orchestrator 校验后写出），带 lineage 校验、symlink/路径逃逸防护
  （`AgentEvidenceIndex.java:145-166`）。markdown 正文中的 `dual-control` 块不进入路由
  ——note.md 记载的注入链在当前实现下不可达（`dual-runtime` 集成测试覆盖）。
- **权限分层**：`AcpxPermissionProfile` 按节点声明翻译为 acpx 进程参数
  （`--permission-policy` deny-default + `--allowed-tools Read,Grep,Glob`）。样本核对：
  GCTW design run 实际命令即 `Read,Grep,Glob`；当前 implement profile 的 approveAll=false，
  按 read/search/edit/execute kinds 自动批准、fetch escalate，且受 allowed-tools 与执行
  policy 约束。不能把 implement 可执行 Bash 等同于 approve-all。
- **人门不可绕过**：`HumanGateActionSemantics` 在定义层校验动作可见性合法性（非法
  配置直接抛异常拒绝装载）；scheduler 仅对 `recoverable_agent_runtime + RETRY_AFTER_
  EXTERNAL_FIX` 自动恢复，人门与 artifact contract failure 一律停（`SchedulerPolicy` +
  状态机文档 §7，与代码一致）。
- **scope 治理**：design/plan 的 planned_files 逐路径对照已批准 TaskContract
  （`DesignScopeValidator.scopeErrors`）；task contract proposal 仅允许 intake 节点产出，
  revision 必须以 patch 形态合并且过 obligation coverage / delta / evidence traceability /
  repository fact binding 五道校验（`ArtifactContractValidator.resolveProposalCandidate`）。

### 2.2 authored obligation lineage（GCTW 停点机制）

design/plan 阶段的 codex must-fix 与人工修订 note 被固化为内容寻址义务
（`sha256:<源工件摘要>#must_fix:<n>` / `#human-note:0`，`AuthoredStageLineageResolver`）。
下一轮 authored 输出必须在 `revision_responses` 中**逐字引用**每个活动义务 ID 并给出
disposition + evidence。这保证"模型自称已处理反馈"不能凭空成立——代价是要求模型逐字
复制 64 位十六进制串。

### 2.3 恢复模型（失败续作）

`RecoveryAssessment` 按类型给出恢复建议。执行失败是否能自动恢复需同时满足具体类型、
状态与策略，不能仅凭“runtime 类”断言全部可自动 retry。GCTW/KPPZ 的产物契约失败
进入人工恢复；KPPZ 的 executionOutcome.retryPolicy=MANUAL_ONLY，实际未走自动语义返工。
这不等于所有语义不合格都只有 retry/abort：合法 typed NEEDS_REVISION、REJECT 等结果已有
声明的修订/分类路径，AHBV 和 KPPZ intake 均有实际记录。

同会话纠错中，11个OKX样本有27次PARSE_FAILURE触发，25次最终候选
contractSchemaValid=true。该统计只说明解析纠错后的schema结果，不证明语义正确、
完整发布/交付成功率或设计返工收益。SCHEMA_FAILURE及`10cd2fbe`新增引用纠错在该样本集
未自然触发；引用纠错的具体前置可达性另见§3.1。

## 3. 近期真实运行停顿图谱（一手账本）

| workstream | 当前账面状态（非全为终态） | 停留节点 | 原评审归因（历史线索） | 原评审节点数/usage金额（非账单） |
|---|---|---|---|---|
| 9BZG 0829 | WAITING_FOR_RECOVERY | implement.artifact_validation-058 | `CHANGE_SCOPE_VIOLATION`（实现越出批准范围，正确拒绝） | 34 节点 / $6.05 |
| HHZL 0903 | WAITING_FOR_RECOVERY | intake.codex_product_review-004 | 合同枚举投影缺陷（harness bug，后续修复） | 4 / $0.38 |
| 3B2P 0904 | WAITING_FOR_RECOVERY | gate.human_design_approval-038 | 修订边界（人为止损） | 32 / $4.56 |
| PNBV 0904 | WAITING_FOR_HUMAN | gate.human_design_approval-038 | 同上，弃置 | 32 / $4.34 |
| SPM9 0906 | RUNNING（弃置） | contract.compile-070 | 环境阻塞（native config）后弃置 | 59 / $13.59 |
| 6KVE 0908 | WAITING_FOR_HUMAN | gate.human_requirement_review-011 | 权限边界止损（readonly bash denial，gate 验证在案） | 10 / $1.12 |
| FZ8K 0909 | WAITING_FOR_RECOVERY | implement.claude_implement-026 | `AGENT_OUTPUT_CONTRACT_VIOLATION` | 24 / $8.60 |
| BXWV 0909 | WAITING_FOR_RECOVERY | implement.claude_implement-098 | 证据续作缺口（补证据需回实现返工）+ 合同违反 | 85 / $38.85 |
| 245D 0910 | WAITING_FOR_HUMAN | gate.human_implementation_repair-050 | `submit_evidence` 未实现死端（`b197ce90` 已补） | 43 / $11.82 |
| GCTW 0911 | WAITING_FOR_RECOVERY | design.claude_design-036 | `AUTHORED_RESPONSE_ID_INVALID`（见 §3.1） | 32 / $5.86 |
| KPPZ 0912 | WAITING_FOR_RECOVERY | design.claude_design-014 | `DESIGN_SCOPE_INVALID`（见 §3.2） | 13 / $1.55 |

合计：154 次 agent run / 原评审记录单 workstream 最多12轮迭代（BXWV）/ 约$96.71账本
金额字段汇总 / **0 COMPLETED；SPM9、BXWV两个样本已执行合计5次验收，均REJECT**。
表中停留节点和当前状态不能代替此前的完整执行历史；“等待/历史留存”也不自动等于
产品失败的同一类别。原归因列不作为模型或任务复杂度的控制变量。

### 3.1 GCTW：完整状态转换与失败续作追踪（一手）

1. intake→requirement gate-016 approve→contract gate-018 approve→design 三轮人工修订
   （gate-023/029/035 revise，账本 gates/ 全在）→ node-036 第六设计迭代。
2. run-1789171318596：claude（实为 zhipu **glm-5.3**，usage.providerId 一手）exit 0 /
   end_turn / 357s / 43k in / 31.5k out / $1.04；envelope 解码成功、schema 其余全过。
3. prompt 的 Required Authored Context 给出 14 个规范义务 ID；agent 输出 14 条
   revision_responses，其中 13 条逐字吻合，第 13 号把 gate-035 human-note 的 64 位 SHA
   抄错：hex 第 43 位的 `1` 被写成 `a7`（规范 `...9ac771bfc2e` → 抄成 `...9ac77a7bfc2`，
   64 位变 65 位，整串 len=85；字符级 diff 已复核）。
4. 运行时代码（`b197ce90`）的 `shapeErrors` 产出裸 `AUTHORED_RESPONSE_ID_INVALID` 并
   **reject**（当时不存在引用纠错），`contractCorrectionDisposition=NOT_REPAIRABLE`、
   `correctionAttempted=false`（run.json agentResultAcquisition 一手）→ 节点 FAILED →
   recovery gate [retry, abort] → `retrySafe=false` → 按止损协议人工停。
5. 时间线核对：运行发生 2026-09-12 00:02Z（本地 08:02），HEAD=`b197ce90`；引用纠错
   机制 `10cd2fbe` 11:17 才提交——**修复晚于失败，因果清晰**。
6. 修复本身有离线测试闭环（`AuthoredReferenceCorrectionSequenceTest`：纠错发布并流经
   review、纠错改 evidence 不得发布、仍畸形一次为限、发布前源变更 fail-closed），且
   **本评审以离线回放直接证明**：把 GCTW 的真实原始输出（含 65 位 ID）喂给当前 HEAD
   的校验器，判定为 `SCHEMA_REPAIRABLE`、修复目标正是 idx13 的 `obligation_id`——修复
   路径对该真实失败前置可达。仍属 provider 依赖的部分只剩：模型收到纠错 prompt 后能否
   一次改对（离线不可证）。
7. **附带实证**：我在构造回放程序时把 canonical 义务 ID 的一处 `e2e7c97` 手抄成
   `e9e7c97`（len 82→80），导致校验器对该输入判 NOT_REPAIRABLE、修复目标清空——与
   GLM 在 GCTW 的失败同类。审查者亲自复现了"手抄内容寻址 ID 极易错"这一失败模式，
   支持减少手抄风险的动机，但不证明 §6.2 的相似度替换能保持身份语义。

### 3.2 KPPZ：修复后新样本的不同死因（一手）

代码 checkpoint `10cd2fbe`（post-fix）。intake 一轮合法修订→requirement gate-011→
contract compile/approve→首次 design：exit 0 / end_turn / 307s，`revision_responses=[]`
（首次设计合法形态，引用纠错 `NOT_NEEDED`）。但 design.planned_files 仅 8 项：7 项授权
路径 + 字面占位符 `src/main/java/com/example/okxkline/okx/okx 占位`（CREATE），已批准
TaskContract 含 34 路径（task-contract.json 一手）。`DesignScopeValidator` 命中
`DESIGN_SCOPE_NOT_AUTHORIZED` → 发布前拒绝、无 sidecar、无 reviewer 后继 → 正确拒绝，
本次拒绝后未进入携诊断的自动设计返工，而是停损。另有27个授权路径未出现在该清单中；
这是输出内容观察，不是本次validator逐项完整性检查的证明：该守卫检查声明路径是否为
授权集合的子集，实际拒绝点是未授权占位路径。是否应存在合法返工出口，仍需按§6.1核定。

### 3.3 停顿的结构性解读

- 每类新失败 = 发现 → 停损 → 修复提交 → 新 fresh 样本验证。9 月的提交序列
  （`6a868174`→`b197ce90`→`10cd2fbe`）即该循环的足迹。停止中包含执行纪律和operator
  决策的影响，但没有量化依据支持“一半”等比例，也不证明所有停止都必要。
- **已知具体限制**：GCTW当时缺引用纠错；当前修复已离线证明该前置可达。KPPZ设计内容
  缺失/越界在发布前被拒，本次未进入携诊断的自动语义返工。不能把二者和已有正常修订链
  混为“所有语义失败均无续作”。同prompt重跑的复犯概率、模型切换前后失败密度、任务
  复杂度的因果贡献均未由这批混合版本样本证明。

## 4. 六维核对表（Declared / Proven / Consumed / Enforced / Failure route / Cannot-bypass）

| 能力腿 | Declared | Proven（真实） | Consumed | Enforced | Failure route | Cannot-bypass |
|---|---|---|---|---|---|---|
| A. 多 agent 编排基座（acpx 双角色、session 治理、token 落账） | ✓ | ✓ 154 真实 run | ✓ refs→context→prompt（分节 sha256 manifest） | ✓ runtime 唯一路径 + 进程级权限参数 | ✓ acpx failure catalog→RecoveryAssessment（0903 identity-deadline 恢复在案） | ✓ |
| B. 需求→合同（proposal/revision patch/人门） | ✓ | ✓ KPPZ 走完（含一轮合法语义修订） | ✓ contract 为后续 scope 治理单一事实源 | ✓ 五道 proposal 校验 | KPPZ intake合法修订；不以GCTW设计修订代证合同返工 | 按对应测试与样本判定 |
| C. 设计/计划（authored obligations） | ✓ | 部分：正常修订链走通；KPPZ首版失败，未估算统一版本首发失败率 | ✓ sidecar→reviewer→gate | ✓ ID 正则 + 覆盖集相等 + scope 逐路径 | KPPZ无自动语义返工；引用纠错前置离线可达、自然分支未触发 | ✓（发布前拒绝实证 ×2） |
| D. 实现/自测（implement+self-test+artifact validation） | ✓ | ✓ 9BZG 到达（Surefire 证据、E1 沙箱）；FZ8K/BXWV 到达 implement | ✓ typed self_test_report→decision | ✓ CHANGE_SCOPE 正确拒绝（9BZG 死点即证明） | 部分：证据缺口续作当时缺失（`b197ce90` 已补，未证） | ✓ |
| E. 验收/交付（acceptance→report→prepare_pr→human） | ✓ | 历史完整链：AHBV、WA53等；OKX两个样本执行5次验收REJECT、0 COMPLETED | ✓ | decision/分类与交付约束有实现及历史证据；不外推所有版本/分支 | REJECT→修复有AHBV实证；当前OKX交付未证明 | 按具体测试/历史样本，不作全局完备性承诺 |
| F. 失败续作（correction/recovery/continuation） | ✓ | 解析纠错25/27候选schema通过；GCTW修复前置离线可达 | ✓ | 一次为限、冻结及发布拒绝有确定性测试 | KPPZ产物失败停人工恢复；schema/引用纠错、submit_evidence自然路径未触发 | 限于已有测试覆盖的具体边界 |

**判定式说明**：不以上表的"正确拒绝"（KPPZ/9BZG/GCTW 的 Enforced ✓）替代交付能力证明
——拒绝证明不是交付证明。E行的历史ACCEPT有一手等级（AHBV），OKX为0/11完成；二者
跨越任务、版本、模型组合和operator等多因素，不能据此识别“复杂度档位”的因果作用。

## 5. 关键问题（按影响排序）

**P1-1 发布前产物不合格的返工覆盖待评估（KPPZ已证限制）。** 本次DESIGN_SCOPE_INVALID
没有自动语义返工，不能据此断言整个系统无修订能力或认定它是所有样本停顿的主因。
KPPZ应描述为7项授权路径、1项占位、27项合同路径未列；删除占位并不能补齐设计职责。
GCTW引用修复已有离线前置证明，不能再按未修复状态与KPPZ合并设计一种通用重试。

**P1-2 交付可靠性不足，归因尚不充分。** OKX系列154次run仍无COMPLETED值得优先处理，
但它不是单一模型、版本、合同或连续可比较的实验。历史glm-5.3成功与当前失败同时成立；
不能从金额/次数推导模型能力阈值。先按阶段和故障类型区分可修实现缺口、操作停止与
模型输出负担，再决定改什么；不直接要求换模型、扩模型层或重构编排器。

**P2-3 内容寻址义务 ID 要求模型手抄。** obligation_id 设计（sha256 前缀 64 hex）把
"防伪造响应"的正确要求变成了"抄写考试"。`10cd2fbe` 的有界纠错是补救（其前置可达性已
由本评审离线回放证明）；是否能减少重复抄写仍须保持精确身份，§6.2的近似匹配建议已不采纳。
**审查者自身在回放实验中把一个 canonical ID 抄错一位（`e2e7c97`→`e9e7c97`），直接导致
修复路径对被测输入失效**——这是一次手抄错误记录，支持自动读取可信输入，不证明普遍
错误率，也不授权近似匹配。

**P2-4 新续作机制累积了三个"已实现未实证"。** schema 纠错、authored 引用纠错、
external evidence continuation（`b197ce90`）均无真实 provider 触发记录。按本仓库自己的
标准（NATURAL E2E NOT PROVEN 应如实登记），这些在能力矩阵上只能记 NOT EXERCISED。

**P3-5 观测面小缺陷已在案。** run.executionAttempt.retrySafe=true 与 recovery 摘要
Retry Safe=false 的投影差异（KPPZ 报告登记，非本次独立发现，账本可复核）。CLI动作
硬编码是历史R1发现，external evidence checkpoint已记录修复，不能仅引用旧评审当作
当前未修缺陷；若怀疑复发须另给当前代码/运行反例。

## 6. 最小改进方向

**6.1 携诊断的有限语义返工：候选方向，不是实施READY。** 原建议“沿用parse纠错冻结
规则补写设计”不采纳。格式修复、引用修复和补写设计是不同改动权限；schema合法不代表
内容已正确，也不能仅修被诊断点名的路径后宣称设计完整。25/27是解析纠错候选schema
统计，不能预测此方案收益。先只读核对现有修订/人工恢复入口能否承载完整闭环：拒绝产物
保留 -> 合法发起返工 -> 作者完成替代设计 -> 原合同/全部发布守卫重验 -> 正常reviewer/
gate。需要明确改动权限、重试上限及失败/冷启动出口后，才讨论最小实施；不能按宽泛错误码
统一自动retry，也不恢复当前冻结样本来试错。

**6.2 编辑距离自动替换身份：不采纳。** 原提案“编辑距离≤2且唯一邻近”只能证明字符串
相似，不能证明回应归属于该义务；集合等值和冻结evidence并不能单独证明语义配对。
当前已评审的精确active集合+有限引用纠错保持不变；不新增相似度、别名或模糊fallback。

**6.3 就绪规则：复用，不再新增Gate。** 既有复用Gate的2026-09-12节已经要求必需出口
可执行，并包含prepare/submit/self-test/acceptance链。保留检查，不把“再次写规则”列为
新开发任务；存在运行反例时再核定具体缺口。

**6.4 模型选择：未验证备选，不作为当前开工项。** 模型仍从配置读取，不按本次混合样本
统计断言换模型收益，也不在workflow写死后端或新增model层。只有确需比较且输入、版本、
权限及输出要求可控时，才另行定义有界验证；本稿不启动该实验。

**不建议**的方向：放宽 scope 校验、允许自动 approve 人门、或为"跑通"而绕过止损纪律
——那会破坏本系统最有价值的资产（已验证的 Cannot-bypass 性质）。

## 7. 未验证部分（2026-09-12 二次核定后重写）

初版 §7 有两处判断错误，本节如实更正并区分"约束下不可验"与"可验但未验"：

**已补验（初版误标为未验证）：**
- **历史端到端账本**：初版断言"原始账本不在盘上"是**错的——是没找，不是没有**。按
  `~/.dual/operator-workspaces.json` registry 与全盘搜索找到：captcha-auth-app 系列
  registry 记录 21 条、盘上存留 4 个账本（其中 **2 个 COMPLETED**，含 AHBV 完整交付
  链）、cancer-clinic 系列盘上 129 个（8 个 COMPLETED）。已升为一手证据（§0、§4）。
- **引用纠错修复对 GCTW 的可达性**：离线回放（单文件 Java + 当前 HEAD classes，未改
  仓库）证明该真实失败判 `SCHEMA_REPAIRABLE` 且修复目标正确指向 idx13（§3.1）。

**约束下不可验（维持）：**
- provider 侧行为：模型收到纠错/反馈 prompt 后的一次通过率——需要真实 agent 调用，
  违反本次"不调 provider"约束。
- 成本对账：~$96.7 来自账本 `usage` 字段，无 provider 账单访问权限。
- 跨平台 E1 沙箱语义（本机仅 darwin）。
- 已被系统清理的历史目录（A10 的 /private/tmp 载体等）——按约不重跑即不可复原，
  维持二手等级。

**可验但未验（诚实登记为投入取舍，非不可验）：**
- 大类逐行审读（`ContextBuildPackageHandler`、`RepoScopeGovernance`、
  `HumanReviewGateHandler`、`AcpxAgentTransport`）——只核对关键执行点；如需可继续。
- LiteFlow 与内置 kernel 的路由等价性——测试在 1493 内全绿但未逐条审读断言强度。
- 多 workstream 并发调度（AgentWorker 队列模式）——未写并发离线测试。
- `dual-server` 前端只抽样。

**方法论教训（写给自己与后续评审）**：初版把"历史账本不在盘上"当事实写进了报告，实际
只查了一个目录。本次被用户追问后 15 分钟内找到一手反例。**"未验证"必须先穷尽廉价的
验证手段再登记**——否则未验证清单本身会携带错误断言。

## 8. 证据索引（关键一手文件）

- GCTW：`okx-btc-k-line-demo-agent-runs/ws-20260911T231749Z-GCTW/.agent/workstreams/
  ws-20260911T231749Z-GCTW/` 下 `runs/run-1789171318596/{run.json,outputs/design-claude.json,
  prompts/design-claude.md}`、`gates/`、`nodes/node-design-claude_design-036.json`。
- KPPZ：同结构 `ws-20260912T081920Z-KPPZ/.../runs/run-1789202097910/`、
  `artifacts/task-contract.json`（34 路径）。
- AHBV（一手端到端交付）：`captcha-auth-app-agent-runs/ws-20260828T054556Z-AHBV/
  .agent/workstreams/ws-20260828T054556Z-AHBV/`——`nodes/node-acceptance-*{033 REJECT,
  043 ACCEPT_WITH_NOTES}`、`nodes/node-acceptance-classify_failure-035（IMPLEMENTATION_BUG）`、
  `nodes/node-delivery-prepare_pr-046`、`delivery/{pr-body.md,merge.patch}`、worktree commit
  `de59e98`（+571/−237）、`reports/latest-final-report.md`；16 runs / $4.88 /
  claude=zhipu glm-5.3 + codex=openai GPT-5.6-Sol。
- 纠错统计：11 个 OKX workstream 全部 `runs/*/run.json` 的 `agentResultAcquisition` 扫描。
- 离线回放：`/tmp/ReplayGctw.java`、`/tmp/diag/.../DiagRepair{,2}.java`（当前 HEAD
  classes；含审查者自身抄错 canonical ID 的失败复现记录）。
- 系列状态分布：cancer-clinic 盘上 129 个（8 COMPLETED / 88 WFR / 26 RUNNING / 6 WFH /
  1 FAILED）、captcha-auth registry 21 条 / 盘上 4 个（2 COMPLETED）、OKX 11 个
  （0 COMPLETED）——`~/.dual/operator-workspaces.json` + 各 `workflow/current.json`。
- 代码：`WorkflowRuntime.java:304-340`、`ArtifactContractValidator.java:39-161`、
  `AgentResultEnvelopeValidator.java`、`AuthoredRevisionValidator.java`、
  `AcpxAgentResultAcquirer.java`、`AgentEvidenceIndex.java:145-166`、
  `DesignScopeValidator.java`、`RecoveryAssessment.java`、`HumanGateActionSemantics.java`。
- 测试：本机 `mvn -o test` 全量 1493/0/0/0（8 模块 surefire 聚合）；
  `AuthoredReferenceCorrectionSequenceTest`（`10cd2fbe`）。

## 9. 给复核者（Codex）的高价值核验点

本评审的关键主张均可独立证伪。建议按成本从低到高抽查：

1. **GCTW 抄写错误**（§3.1）：对 `runs/run-1789171318596/outputs/design-claude.json` 的
   `result.revision_responses[13].obligation_id` 与 `prompts/design-claude.md` 第 655 行
   附近的规范 ID 做字符级 diff——应得 hex 第 43 位 `1` vs `a7`、长度 84 vs 85。
2. **修复前置可达**（§3.1.6）：用当前 HEAD 的
   `AgentResultEnvelopeValidator.validate(envelope, ws, run, contract, activeIds)`（5 参，
   activeIds 取 prompt 中 14 个规范 ID）重放同一输出——应得 `SCHEMA_REPAIRABLE`、
   target=`result.revision_responses.13.obligation_id`。注意：activeIds 任一抄错即得
   NOT_REPAIRABLE（审查者已亲身踩坑，§3.1.7）。
3. **AHBV 端到端交付**（§0/§8）：读其 `nodes/node-acceptance-codex_verifier_review-{033,043}`、
   `node-acceptance-classify_failure-035`、`node-delivery-prepare_pr-046`、
   `delivery/merge.patch` 与 worktree commit `de59e98`——应证 REJECT→IMPLEMENTATION_BUG→
   修复→ACCEPT_WITH_NOTES→delivery→COMPLETED 全链，且 usage.providerId 为
   zhipu/glm-5.3（claude）与 openai/GPT-5.6-Sol（codex）。
4. **纠错统计**（§2.3）：扫 11 个 OKX workstream 的 `runs/*/run.json`
   `agentResultAcquisition`——应得 correctionAttempted=27（全部 PARSE_FAILURE），
   其中 25 个修正后 contractSchemaValid=true、0 个 SCHEMA_FAILURE。
5. **KPPZ 范围拒绝**（§3.2）：`runs/run-1789202097910/outputs/design-claude.json` 的
   planned_files 应为 8 项（含 `okx 占位`），`artifacts/task-contract.json` 的
   scope.included 应为 34 项。
6. **OKX 完成状态与验收历史**（§3 表）：11个 `workflow/current.json` 无一COMPLETED；
   另读SPM9的 `node-acceptance-codex_verifier_review-067.json` 与BXWV的同名前缀
   `060/070/080/090.json`，应见5个COMPLETED节点、decision均为REJECT。节点完成不等于
   验收通过，也不能因工作流未完成便断言从未进入验收。

若以上任一核验失败，相应结论应降级或撤回；本评审接受该约束。

## 10. 主 Agent 勘误记录（2026-09-12，非新增独立评审）

用户同意复核后，本轮仅重读既有代码与持久化账本并修订本报告。没有修改production、
启动provider、恢复旧workstream或创建新样本；没有重新执行CC的1493项测试与GCTW Java
回放。原报告中的这两项实证继续明确归属CC，不冒充本轮复跑。

| 原主张或建议（本轮撤回/收紧） | 更正及依据 |
|---|---|
| OKX未到验收、全为等待态 | 0个工作流COMPLETED，但2个样本有5次验收REJECT；当前状态为7 WFR、3 WFH、1历史RUNNING。依据全部11份current及上述5份验收节点。 |
| 同模型配置下对比、复杂度是决定变量 | AHBV与部分OKX共享Claude GLM-5.3，但Codex、harness、权限、合同和人工干预不同；6KVE为DeepSeek。34路径属于KPPZ，14义务属于GCTW。只能提出复杂度假说，不能从混合历史样本识别决定性原因。 |
| 27次纠错有25次通过，可推广返工成功率 | 复算154份run得到27次correctionAttempted，其中25次最终候选schema有效；不等于语义、发布、验收或交付成功。 |
| 所有语义错误均无续作 | 正常typed NEEDS_REVISION/REJECT已有修订链；本轮待评估的是KPPZ这类发布前拒绝之后的合法返工出口。 |
| 用编辑距离不超过2自动修正义务ID | 不采纳。近似度不证明身份；保留既有可信active集合、限定槽位和冻结非目标字段的纠错边界。 |
| 语义返工直接沿用parse纠错冻结策略 | 不成立：合法语义修改本就可能改变正文。须先核定既有作者返工路径的权限、输入、预算与失败出口，不先增加通用重试。 |
| implement使用approveAll | 当前工厂明确approveAll=false，只配置工具/kind批准；依据AcpxPermissionProfile.implement()。 |
| CLI动作提示仍硬编码 | 已有外部证据checkpoint改为消费HumanGateActionSemantics；不能继续作为未修当前缺陷。 |
| 金额即真实成本，以及“一半”“大概率”等归因 | usage金额未与账单对账，且含subscription_token_only；比例与复犯概率无合格分母/对照，撤回定量推断。 |

复算范围：`/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/` 下本报告列明的
11个样本，各自 `.agent/workstreams/<id>/workflow/current.json`、`nodes/*.json` 与
`runs/*/run.json`；不修改这些工件。各样本run数依§3顺序为
`22, 2, 13, 13, 24, 4, 9, 32, 17, 13, 5`，合计154。§3节点数不是run数。

当前行动边界：保留历史交付证据，承认当前OKX稳定交付未证明；本稿不授权§6候选方案
实施。下一步只对既有“发布拒绝→合法作者返工→重新校验→review/gate”出口做有界只读
判断，先回答能否复用以及必要改动在哪里；不预设增加机制，不以新E2E替代该判断。
