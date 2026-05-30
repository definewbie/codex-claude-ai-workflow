# Gap Analysis: dual-orchestrator vs Meta-Engineering Harnesses for AI-Native Software Production

> 论文：*Meta-Engineering Harnesses for AI-Native Software Production — A Contract-Driven Adversarial Verification Architecture with Early Deployment Report*
> 作者：Satadru Sengupta, Tamunokorite Briggs, Ivan Myshakivskyi (HireNimbus)
> 分析日期：2026-05-29

---

## 1 论文核心思想

### 1.1 核心论点

AI 能不能一次性生成代码已经不是问题了。**真正的问题是：一个系统能不能持续地、可靠地跨多个业务场景生产、验证、部署、维护和升级软件。**

> "A harness is not a model, prompt, or agent. It is the surrounding system that makes agent outputs more reliable."
> 可靠性应该在 **harness 层**评估，而不是单个模型调用层。

### 1.2 论文分层架构

论文的核心分层更接近：

```
Model layer                    ← LLM / 生成能力
Role / orchestration layer     ← 角色专业化、分工编排
Contract layer                 ← 需求编译为显式契约
Context / memory layer         ← 持久化记忆 + 专业化记录
Execution layer                ← 从契约实现
Verification layer             ← 独立验证 + 对抗验证 + review + QA
Calibration layer              ← 失败分类 → 回馈到契约模板、review gate
```

Review / QA / Deployment 是 verification 与 production pipeline 中的重要机制，但不应被误写成论文唯一标准分层。

### 1.3 关键机制

| 机制 | 说明 |
|------|------|
| Two-pass contract compilation | 需求不是直接给 agent，而是先编译成显式契约（API spec、invariants、side effects、acceptance criteria） |
| Persistent markdown memory | 持久化记忆 + 专业化记录，作为后续 context |
| 双机制验证 | 独立性验证（不同 agent 从同一契约独立实现和测试）+ 注意力验证（角色分离的 reviewer 检查不同风险维度） |
| Four-way failure arbiter | 四路失败仲裁器，分类失败根因 |
| Outer-loop calibration | 外环校准：将失败分类回馈到契约模板、review checklist 和 context rule |

### 1.4 论文案例启示

支付系统部署后发现：行为正确性不够——线下存款、折扣计算等业务逻辑不在契约中。这直接驱动了契约细化、review gate 增强和 harness 级度量。**论文的核心方法论：用真实失败驱动系统改进，而不是预设完美流程。**

---

## 2 dual-orchestrator 已有能力

### 2.1 已对齐论文的 harness 级能力

| 能力 | dual-orchestrator 实现 | 对齐论文哪个机制 |
|------|----------------------|----------------|
| Harness 级定位 | Workflow engine 包围 agent，不是裸调 agent | Harness 级抽象 |
| 角色分离 | Claude（主控实现）+ Codex（审视验证）+ Human（gate） | Role / orchestration layer |
| 结构化验证 | `dual-control` block → Decision handler 解析 → PASS / REJECT / NEEDS_REVISION | Verification layer（雏形） |
| 审计追踪 | `events.jsonl` 审计事件流 | 可审计性 |
| 文件账本 | `current.json` + `nodes/*.json` + `gates/*.json` + `iterations/*.json` | Context / memory layer |
| Context 治理 | 6 profile × token budget × priority degradation × per-section overflow policy | Context / memory layer（比论文描述更精细） |
| Iteration 记录 | Revision loop：trigger node / action / reason / parent iteration | Calibration data basis |
| Artifact contract | `ContractRegistry`：decision 值域、node output schema、required keys | Contract layer（雏形 — 约束 agent output shape；TaskContract 约束 task intent / scope / acceptance，两者不同层次） |
| Recovery 机制 | Rate limit / session missing / agent reconnect / context window 恢复 + auto-retry + 指数退避 | Execution failure recovery |
| Gate 审批 | Human gate：approve / revise / complete，带 feedback file | Review / QA mechanism |
| 并行节点 | Fan-out edge routing + sequential multi-node resume | Orchestration layer |
| 条件路由 | EdgeCondition 接口 + ConfigEqualsCondition | Orchestration layer |

### 2.2 dual 的三方 review 结构

dual 当前是 **Claude implement + Codex review + Human gate** 的三方结构：

- **Claude（主控实现）**：作为 developer / architect 执行实现
- **Codex（审视验证）**：作为外部 reviewer / verifier 审视产出
- **Human gate**：人工审批卡点

差距不是"没有 agent review"——Codex review 和 acceptance 已经是 agent 审视。**论文启发的是进一步扩展为多角色专业化 review**（product / architecture / security / QA / deploy 等维度）。

---

## 3 Gap Analysis

### 3.1 核心差距

| # | 差距 | 论文机制 | dual 现状 | 性质 |
|---|------|---------|----------|------|
| 1 | **TaskContract / ContractCompiler** | 需求先编译为显式契约（objective、scope、constraints、acceptance criteria、risk areas、ambiguity notes） | 需求直接进 prompt，无结构化 feature contract | 架构缺口 |
| 2 | **Adversarial Test Agent** | 独立 tester agent 从契约生成测试，与 implementer 解耦 | Codex review 是审视不是测试，不生成对抗测试 | 角色缺口 |
| 3 | **Semantic FailureArbiter** | 四路仲裁器分类失败根因 | dual 有 execution failure recovery，但缺少语义失败分类 | 验证缺口 |
| 4 | **Outer-loop Calibration** | 失败分类 → 模式检测 → 校准候选 → 人审 → 版本化提升 → 更新契约模板 | 有 iteration 记录作为数据基础，但没有 calibration layer | 闭环缺口 |
| 5 | **Multi-role Review** | Product / Arch / Security / QA / Deploy 多维度角色分离审查 | Claude + Codex + Human 三方，尚未多角色专业化 | 角色扩展 |

### 3.2 Execution Failure vs Semantic Failure

dual 已经有完善的 **execution failure recovery**，这不应被低估：

**已覆盖（Execution Failure Recovery）：**
- `RECOVERABLE_RATE_LIMIT` — 指数退避重试
- `SESSION_MISSING` — 自动创建 session
- `AGENT_RECONNECT` — recovery gate + retry
- `CONTEXT_WINDOW` — degradation pipeline
- `ARTIFACT_VALIDATION_FAILED` — Git evidence 采集失败
- `INVALID_DUAL_CONTROL` / `MISSING_DUAL_CONTROL` — prompt contract 违约
- `CONTROL_REPAIR` — 非终止 block 等格式问题

**未覆盖（Semantic Failure Arbiter）：**
- `IMPLEMENTATION_BUG` — 实现正确但不符合意图
- `CONTRACT_GAP` — 契约遗漏了业务逻辑（论文支付案例的核心失败）
- `REVIEW_NOISE` — 审视者误判
- `CONTRACT_AMBIGUITY` — 契约描述模糊导致歧义实现
- `CONTEXT_INSUFFICIENT` — 上下文不足导致 agent 猜测
- `TOOL_LIMITATION` — 工具返回成功但证据不足、能力不足或上下文不完整（注意：acpx / git 调用级失败已走 execution recovery；此处指工具能力边界的语义问题）

**结论：** dual 现有 recovery 解决的是**执行失败**（agent runtime 问题）；论文启发要补的是**语义失败分类与后续路由**（产出质量问题）。

### 3.3 ContractRegistry 的定位

dual 当前的 `ContractRegistry` 主要是 **workflow / agent output contract**：

- decision 值域（如 `intake.decision` → `READY_FOR_PRODUCT_REVIEW | NEEDS_REVISION | BLOCKED`）
- node output schema（required keys: `decision` + `summary`）
- prompt template 与 workflow edge 的 `on` 值一致性

它是契约驱动基础设施的**雏形**，但还不是论文意义上的完整 TaskContract / FeatureContract。关键区分：**Artifact contract 约束的是 agent output shape；TaskContract 约束的是 task intent / scope / acceptance。两者都属于 contract-driven harness，但处在不同层次。**

完整 TaskContract 应补充：

```text
objective          — 本任务要达成什么
scope              — 涉及哪些文件 / 模块 / API
non-goals          — 明确不在范围内的事
assumptions        — 前置假设
constraints        — 技术约束（框架、版本、兼容性）
affected_areas     — 影响范围
acceptance_criteria — 验收标准
test_requirements  — 测试要求
risk_areas         — 风险区域
ambiguity_notes    — 模糊点记录
human_questions    — 需要人确认的问题
```

### 3.4 Revision Loop 与 Calibration 的关系

dual 已有 revision loop 和完整的 ledger 记录（iteration trigger / action / reason / parent），因此**具备 outer-loop calibration 的数据基础**。

但还没有真正的 calibration layer。真正的 calibration layer 应包括：

```
1. failure classification       — 分类每次失败的根因
2. recurring pattern detection   — 检测跨 iteration / workstream 的重复模式
3. calibration candidate         — 生成校准候选（改契约模板 / 改 review checklist / 改 context rule）
4. human review                  — 人审校准候选
5. versioned promotion           — 版本化提升校准
6. apply                         — 更新 contract template / review checklist / context rule / regression test
```

---

## 4 对比总结表

| 维度 | 论文 Harness | dual-orchestrator | Gap |
|------|-------------|-------------------|-----|
| **核心定位** | AI 软件生产系统（contract-driven verification harness） | Agent 生命周期编排器（workflow execution harness） | harness 方向一致，但 dual 偏执行层 |
| **契约层** | Two-pass contract compilation → 显式契约（API spec、invariants、side effects） | ContractRegistry（output schema + decision 值域）| 有雏形，缺 TaskContract / ContractCompiler |
| **角色编排** | 多角色专业化（implementer、tester、reviewer、QA、deploy） | Claude + Codex + Human 三方 | 基础角色已分离，缺多维度专业化 |
| **验证** | 双机制：独立性验证 + 对抗验证 + 多角色 review | Codex review + Decision handler + Human gate | 有 agent review，缺对抗测试和多维度 |
| **执行失败恢复** | 论文未深入 | 完善的 rate limit / session / reconnect / context window / artifact recovery | dual 这方面**领先** |
| **语义失败分类** | Four-way failure arbiter | 无 | **缺失** |
| **Context 治理** | 契约 + 专业化记录作为 context | 6 profile × token budget × priority degradation × per-section overflow | dual 这方面**领先**（比论文描述更精细） |
| **审计追踪** | Harness 级可审计 | `events.jsonl` + 文件账本 + run metadata | **已对齐** |
| **外环校准** | 完整闭环：分类 → 检测 → 候选 → 人审 → 提升 → 应用 | 有数据基础（iteration 记录），无校准机制 | 有基础，缺闭环 |
| **部署** | 部署是 harness 的一环 | `delivery.prepare_pr` + `delivery.commit` + `delivery.create_pr` | 基础交付管线已有 |

---

## 5 核心结论

论文是 dual-orchestrator 的**方向性验证和升级路线图**。

dual 已经走在 harness 方向上：它具备 workflow runtime、角色分离（Claude + Codex + Human）、文件账本、事件追踪、artifact contract、context governance、human gate 和 revision loop。它的 execution failure recovery 和 context governance 甚至比论文描述的更具体。

但它当前仍偏 **workflow execution harness**，尚未完全成为 **contract-driven verification harness**。

主要缺口按优先级排序：

1. **TaskContract / ContractCompiler** — 后续验证和校准的输入基础
2. **FailureArbiter** — 让失败具备可路由语义
3. **CalibrationCandidate** — 最小外环
4. **Adversarial Test Agent / Multi-role Review** — 成本更高，适合后置

---

## 6 P5–P8 路线建议

```
P5：TaskContract + ContractCompiler
    - 定义 TaskContract schema（objective / scope / constraints / acceptance criteria / risk areas）
    - 实现 ContractCompiler：从需求 + workflow context 编译为结构化契约
    - 契约作为 agent prompt 的结构化输入，替代纯文本需求
    - 依赖：无（独立基础设施）

P6：FailureArbiter
    - 扩展失败分类：execution（已有）+ semantic（新增）
    - 语义分类：CONTRACT_GAP / IMPLEMENTATION_BUG / REVIEW_NOISE / CONTEXT_INSUFFICIENT / CONTRACT_AMBIGUITY
    - 分类结果写入 events.jsonl + iteration record
    - 不同分类路由到不同恢复策略
    - 依赖：P5（TaskContract 作为分类输入）

P7：CalibrationCandidate
    - 跨 iteration / workstream 的重复失败模式检测
    - 生成校准候选：建议修改 contract template / review checklist / context rule
    - 人审校准候选 → 版本化提升
    - 依赖：P6（失败分类作为检测输入）

P8：Adversarial Test Agent / Multi-role Review
    - 独立 tester agent 从 TaskContract 生成对抗测试
    - Codex review 扩展为多维度（product / architecture / security / QA）
    - 依赖：P5（TaskContract，reviewer 需要对照契约）
    - 可利用 P6 的 failure classification 做 review quality 反馈，但不是硬依赖
```

路线排序理由：

1. ContractCompiler 是后续验证和校准的**输入基础**，没有结构化契约，后续验证没有锚点
2. FailureArbiter 让失败具备**可路由语义**，是校准的前置条件
3. CalibrationCandidate 是最小外环，**数据基础已具备**（ledger 记录），只需分类 + 检测
4. Adversarial / Multi-role review **成本更高**（更多 agent 调用、更多 prompt 设计），适合后置

---

## 7 是否建议进入短期版 Contract-Driven Harness 设计实现

**建议：是。**

理由：
- P5（TaskContract + ContractCompiler）是独立基础设施，不依赖现有代码改造
- 它可以在现有 intake 阶段注入，不破坏已有 workflow
- 它为 P6-P8 提供锚点，没有它后续验证和校准缺乏结构化输入
- 实现成本可控：定义 schema + compiler + 在 agent prompt 中引用

建议下一步：**写 P5 的详细设计文档**（TaskContract schema、ContractCompiler 算法、与现有 intake 节点的集成方式）。
