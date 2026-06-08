# cc-codex Harness：短期版本与长期版本功能对比

## 0. 对比目标

目标是把当前 `cc-codex / dual-orchestrator` 从：

```text
Dual-Agent Workflow 当前实现版
```

升级为：

```text
通用 AI-Native Engineering Harness
```

可以拆成两个阶段：

```text
短期版本：Contract-Driven Harness Upgrade
长期版本：General AI-Native Engineering Harness Platform
```

## 0.1 2026-06-02 状态更新

这份对比最初用于规划 short-term contract-driven upgrade。现在短期版本的主干已经基本落地：

- TaskContract / ContractCompiler MVP 已实现。
- ContractReviewGate 已进入 p5 / p6 lifecycle。
- FailureArbiter MVP 已实现。
- CalibrationCandidate MVP 已实现。
- Review packet、timeline、failure summary、artifact sidecar 已作为读模型落地。
- 私有真实 p6 长 case 已跑通，并实际触发两轮 Codex `REJECT` → failure classification → Claude repair → final acceptance。

因此下一步不再是“是否要做 contract-driven harness”，而是把它从可运行 MVP 推向可交付 MVP：

1. 用 2-3 个真实仓库任务建立 benchmark loop。
2. 让人工 review 能看懂证据、风险和当前 gate，而不是只看到按钮。
3. 把 failure classification / calibration candidate 从 artifact 变成可评估、可提升的工程闭环。
4. 等主线稳定后，再考虑多角色 review、对抗测试、policy engine 和后台调度。

---

## 1. 总体定位对比

| 维度 | 短期版本 | 长期版本 |
|---|---|---|
| 定位 | 把当前 dual-orchestrator 升级为 contract-driven harness | 变成通用 AI-native engineering harness 平台 |
| 目标 | 让任务定义更清楚、失败分类更准、review 更有效 | 支持多领域、多工作流、多 Agent、多角色、多知识演进 |
| 核心价值 | 降低“任务说不清 / review 不准 / 反复返工” | 建立可复用、可扩展、可度量、可进化的 AI 工程系统 |
| 改造方式 | 在现有流程上加 contract / arbiter / calibration artifact | 抽象 workflow template、specialization、metrics、knowledge registry |
| 风险 | 低到中 | 中到高 |
| 成本 | 约 1–2 周 | 约 1–2 个月起步，持续演进 |

---

## 2. 功能对比总表

| 功能模块 | 当前已有 | 短期版本要补 | 长期版本要补 |
|---|---:|---|---|
| Workflow Runtime | 已有 Task / Decision / Gate | 保持不大改 | 支持更多 workflow template |
| Run Ledger / Trace | 已有 events.jsonl / current.json | 增加 contract / failure / calibration 事件 | 支持跨 run 查询、指标、回放 |
| Artifact Contract | 已有 node output contract + TaskContract MVP | 强化真实 case 中的字段质量 | 通用 artifact schema registry |
| Context Governance | 已有 profile / budget / degradation | 接入 TaskContract 上下文 | 支持 domain-specific context profiles |
| Claude Implement | 已有 | 保持 | 变成可插拔 implementation agent |
| Codex Review | 已有 | 增加 failureClass 输出 | 多角色 review / adversarial test agent |
| Human Gate | 已有 | 增加 contract review gate | policy-driven gate / approval workflow |
| Failure Handling | 已有执行失败处理 + FailureArbiter MVP | 强化真实 failure evidence 和 repair route | 失败进入 calibration loop |
| Specialization | 可能还弱 | 暂不强做 | Specialization Registry |
| Metrics | 基础 ledger 可推导 | 简单 run summary | harness-level metrics dashboard / CLI |
| Knowledge Evolution | final report / handoff + CalibrationCandidate MVP | 评估和提升候选规则 | Versioned knowledge registry |

---

# 3. 短期版本：Contract-Driven Harness Upgrade

## 3.1 短期目标（已基本达成）

短期不要重做平台。目标是把当前系统从：

```text
自然语言任务 → Claude 设计 → Codex review → Claude 实现 → Codex 验收
```

升级为：

```text
自然语言任务
  → TaskContract 编译
  → Contract Review / Gate
  → Claude 实现
  → Codex Review + Failure Classification
  → 修复 / 校准 / 验收
```

核心变化是：

```text
LLM 不只是执行任务，而是先把任务编译成可验证 contract。
```

---

## 3.2 短期新增能力

| 模块 | 功能 | 为什么重要 |
|---|---|---|
| TaskContract | 显式化目标、范围、非目标、约束、验收标准 | 避免 Claude / Codex 各自理解任务 |
| ContractCompiler | 把自然语言任务编译成结构化 contract | 发挥 LLM 做需求澄清和规格化的能力 |
| ContractReviewGate | Codex 或人先审 contract，再进入实现 | 防止“需求没讲清就开始写代码” |
| FailureArbiter | 区分 bug / contract gap / noise / ambiguity | 避免所有失败都让 Claude 盲改 |
| CalibrationCandidate | 把重复问题沉淀成模板 / checklist / regression 候选 | 让 harness 越用越好 |

---

## 3.3 短期推荐数据结构

### TaskContract

```yaml
taskContract:
  objective: string
  scope:
    included: []
    excluded: []
  assumptions: []
  constraints: []
  affectedAreas: []
  acceptanceCriteria: []
  testRequirements: []
  riskAreas: []
  nonGoals: []
  ambiguityNotes: []
  humanQuestions: []
```

### FailureArbiter 输出

```yaml
failure:
  class: IMPLEMENTATION_BUG | CONTRACT_GAP | REVIEW_NOISE | CONTRACT_AMBIGUITY | TOOL_FAILURE | CONTEXT_INSUFFICIENT
  reason: string
  recommendedAction: FIX_IMPLEMENTATION | REFINE_CONTRACT | RETRY_TOOL | ASK_HUMAN | UPDATE_CHECKLIST
```

### CalibrationCandidate

```yaml
calibrationCandidate:
  sourceRunId: string
  failureClass: string
  proposedUpdateType: CONTRACT_TEMPLATE | REVIEW_CHECKLIST | REGRESSION_TEST | CONTEXT_RULE | PROMPT_TEMPLATE
  proposal: string
  evidenceRefs: []
  requiresHumanApproval: true
  status: DRAFT
```

---

## 3.4 短期版本流程

```text
1. 用户输入任务
2. ContractCompiler 生成 TaskContract
3. Codex / Human 审 TaskContract
4. Claude 基于 TaskContract 实现
5. 自测与 evidence 收集
6. Codex 基于 TaskContract 验收
7. FailureArbiter 分类失败
8. 需要时进入 revision / contract refinement / calibration candidate
9. 生成 final report
```

---

## 3.5 短期版本收益

| 收益 | 说明 |
|---|---|
| 减少返工 | 需求先 contract 化，减少“做完才发现理解错” |
| Review 更准 | Codex 不再凭聊天上下文 review，而是对照 contract |
| 失败更可解释 | REJECT 不只是失败，而是知道失败类型 |
| 知识可沉淀 | 失败能变成 checklist / template / regression 候选 |
| 对现有系统侵入低 | 不需要重写 runtime / ledger / handler |

---

## 3.6 短期版本不做什么

短期不要做：

```text
1. 不做完整 Web UI
2. 不做数据库化 ledger
3. 不做多租户
4. 不做复杂 workflow marketplace
5. 不让 Codex 写业务代码
6. 不做完整 specialization registry
7. 不做大规模 metrics dashboard
```

短期的关键词是：

```text
contract first
failure classification
calibration seed
```

这些能力目前已经进入实现主线。后续短期工作不再是“补概念”，而是围绕真实 run 打磨证据质量、人工审阅体验和可重复评估。

---

# 4. 长期版本：General AI-Native Engineering Harness Platform

## 4.1 长期目标

长期目标不是服务一个 Claude/Codex 固定流程，而是成为通用 harness：

```text
任何工程任务
  → contract compiler
  → workflow template
  → specialized agents
  → artifact validation
  → independent verification
  → failure arbiter
  → knowledge calibration
  → measurable improvement
```

它可以支持：

```text
代码开发
代码 review
测试生成
文档生成
架构设计
SRE RCA
平台工程变更
安全审查
运维自动化
```

---

## 4.2 长期新增能力

| 模块 | 功能 |
|---|---|
| Workflow Template System | 多种 harness workflow：开发、review、bugfix、docs、SRE RCA |
| Specialization Registry | 不同领域注入专属 contract、checklist、context profile |
| Multi-role Review | 产品 / 架构 / 安全 / 测试 / 发布多视角 review |
| Adversarial Test Agent | 专门生成反例和边界测试 |
| Review Aggregator | 汇总多 reviewer 输出，形成统一 decision |
| Knowledge Registry | versioned contract templates / review checklist / regression scenarios |
| Metrics Engine | 统计 cycle time、human gate、failure class、review quality |
| Replay / Simulation | 基于 ledger 复放历史 run，比较新规则效果 |
| Policy Engine | 决定什么时候自动推进，什么时候 human gate |
| Tool Governance | 工具权限、dry-run、approval、execution trace |

---

## 4.3 长期版本流程

```text
Raw Request
  → Contract Compiler
  → Specialization Selector
  → Workflow Template Selection
  → Context Package Builder
  → Agent Tasks
  → Independent Verification
  → Multi-role Review
  → Failure Arbiter
  → Human Gate if needed
  → Final Artifact
  → Calibration Candidate
  → Knowledge Registry Promotion
```

---

# 5. 短期 vs 长期：功能分层对比

## 5.1 Contract 层

| 能力 | 短期版本 | 长期版本 |
|---|---|---|
| TaskContract | 有，MVP | 完整 schema versioning |
| ContractCompiler | 两轮提示词 / handler | 多领域 compiler + ambiguity detection |
| ContractReview | Codex / human gate | policy-driven + domain reviewer |
| Contract Template | 简单模板 | Versioned template registry |
| Contract Gap 处理 | 生成 failure class | 自动生成 template update candidate |

---

## 5.2 Agent 层

| 能力 | 短期版本 | 长期版本 |
|---|---|---|
| Claude implementation | 保持现有 | 可插拔 writer agent |
| Codex review | 增加 failureClass | 多 reviewer role |
| Adversarial test | 可选 | 标准角色 |
| Role separation | 初步 | 严格 role contract |
| Agent output validation | 保持现有 | 通用 artifact contract registry |

---

## 5.3 Verification 层

| 能力 | 短期版本 | 长期版本 |
|---|---|---|
| Self-test | 保持现有 | 标准 verification task |
| Codex acceptance | 保持并增强 | 多角色 review + aggregator |
| Artifact validation | 保持现有 | 通用 schema validation |
| Failure classification | 新增 MVP | 成为核心 decision layer |
| Regression promotion | candidate | 自动建议 + human promotion |

---

## 5.4 Context / Memory 层

| 能力 | 短期版本 | 长期版本 |
|---|---|---|
| Context profiles | 保持现有 | domain-specific profiles |
| Handoff sidecar | 保持 | 标准 Run Summary |
| Memory promotion | 暂不完整 | Knowledge Registry |
| Context drift 处理 | 手工 notes | 规则版本、过期、owner、coverage |
| Replay | 基础 ledger 可查 | 一键 replay / simulation |

---

## 5.5 Metrics 层

| 能力 | 短期版本 | 长期版本 |
|---|---|---|
| Run report | 有 | 标准化 |
| Failure class stats | 简单统计 | 趋势分析 |
| Cycle time | 从 ledger 推导 | CLI / dashboard |
| Human gate count | 从 ledger 推导 | 作为效率指标 |
| Review quality | 手工判断 | false positive / recurring failure |
| Calibration metrics | 无或弱 | promoted candidates / prevented regressions |

---

# 6. 成本对比

| 阶段 | 时间成本 | 代码改动 | 风险 | 价值 |
|---|---:|---:|---|---|
| 短期 P0：TaskContract + Compiler | 3–5 天 | 中 | 中低 | 很高 |
| 短期 P1：FailureArbiter | 2–3 天 | 中 | 中低 | 高 |
| 短期 P2：CalibrationCandidate | 1–2 天 | 低 | 低 | 中高 |
| 短期合计 | 1–2 周 | 中 | 中 | 高 |
| 长期 Template System | 1–2 周 | 中高 | 中 | 高 |
| 长期 Specialization Registry | 1–2 周 | 中 | 中 | 高 |
| 长期 Multi-role Review | 1–2 周 | 中 | 中 | 高 |
| 长期 Metrics / Replay | 2–4 周 | 中高 | 中高 | 中高 |
| 长期合计 | 1–2 月起 | 高 | 中高 | 很高 |

---

# 7. 推荐 Roadmap

## V0.2：Contract-Driven Harness Upgrade

```text
1. TaskContract
2. ContractCompiler
3. ContractReviewGate
4. FailureArbiter
5. CalibrationCandidate
6. README 定位升级
```

目标：

```text
从 dual-agent runner 升级为 contract-driven harness。
```

---

## V0.3：Verification-Driven Harness

```text
1. Adversarial Test Agent
2. Role-specific Review
3. Review Aggregator
4. FailureClass 路由
5. Regression Candidate
```

目标：

```text
让 LLM 不只是实现和 review，而是做多视角验证。
```

---

## V0.4：Specialized Harness

```text
1. SpecializationRegistry
2. Activation confidence
3. Domain-specific contract additions
4. Domain review checklist
5. Domain context profile
```

目标：

```text
让通用 harness 能带领域知识。
```

---

## V0.5：Calibration & Metrics Harness

```text
1. Harness-level metrics
2. Calibration promotion workflow
3. Knowledge registry
4. Replay / simulation
5. Drift management
```

目标：

```text
让 harness 可度量、可复盘、可持续进化。
```

---

# 8. 版本能力摘要

| 版本 | 一句话定位 | 主要能力 |
|---|---|---|
| 当前版 | Dual-Agent Workflow Engine | Claude 实现、Codex review、ledger、trace、artifact contract、handoff |
| 短期版 V0.2 | Contract-Driven Harness | TaskContract、ContractCompiler、FailureArbiter、CalibrationCandidate |
| 中期版 V0.3–V0.4 | Verification + Specialization Harness | adversarial test、多角色 review、specialization registry |
| 长期版 V0.5+ | General AI-Native Engineering Harness | 多 workflow、知识演进、metrics、replay、policy、tool governance |

---

# 9. 最关键区别

短期版本解决的是：

```text
怎么让当前 Claude/Codex 流程更稳？
```

长期版本解决的是：

```text
怎么把它变成可迁移到任何工程任务的 AI harness？
```

所以短期重心是：

```text
TaskContract + FailureArbiter
```

长期重心是：

```text
Workflow Template + Specialization Registry + Calibration Loop + Metrics
```

建议先做短期版本，不要直接跳长期。短期版本做完，系统定位就已经明显升级，而且不会扰乱当前稳定的 workflow runtime。
