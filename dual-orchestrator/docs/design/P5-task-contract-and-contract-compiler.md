# P5 设计文档：TaskContract + ContractCompiler

> 状态：设计稿
> 日期：2026-05-29
> 依赖：现有 workflow runtime / ledger / context governance
> 非目标：FailureArbiter / CalibrationCandidate / Multi-role Review / Adversarial Test Agent

> 2026-06-03 更新：这份文档记录的是 P5 TaskContract MVP 的初始设计。真实私有 feature-slice benchmark 暴露出 Markdown heuristic 抽取会污染 canonical TaskContract。后续事实来源修正以 `docs/design/2026-06-03-artifact-driven-contract-negotiation.md` 为准：Claude 输出结构化 `task_contract_proposal`，系统做机械校验，Codex 按原则审议，最终再生成 canonical TaskContract。

---

## 1 问题

当前 workflow 中，自然语言需求直接进入 Claude prompt。后续 Codex review 和 human gate 没有一份结构化的、各方共享的任务定义可以对照。

这导致：

- Claude 可能偏离意图（没有明确的 scope boundary）
- Codex review 缺乏验收锚点（不知道该检查什么）
- Human gate 只能凭主观判断（没有 acceptance criteria）
- Revision loop 缺少失败的根因记录（不知道是 scope 错了还是实现错了）

论文（*Meta-Engineering Harnesses for AI-Native Software Production*）把这个问题描述为：**没有 contract layer 的 harness 只能验证执行，不能验证意图。**

---

## 2 目标

让 Claude、Codex、human gate 都基于同一份 `TaskContract` 工作。

```
当前：  需求文本 → Claude 实现 → Codex review → Human gate
目标：  需求文本 → ContractCompiler → TaskContract → Claude 实现 → Codex review → Human gate
                                     ↑_____________|___________|_____________|
                                     三方共享同一份契约
```

**核心原则：** TaskContract 未通过 review，不进入实现。

---

## 3 TaskContract Schema

### 3.1 MVP Schema

```yaml
taskContract:
  # 身份
  id: "tc-001"                          # 唯一标识，由 ContractCompiler 生成
  version: 1                             # 契约版本，calibration 时可提升
  createdAt: "2026-05-29T10:00:00Z"

  # 核心内容
  objective: "给 README 增加一段项目说明"    # 一句话描述要达成什么
  scope:
    included:                             # 明确在范围内的事
      - "README.md 文件的 Project Description section"
      - "不超过 200 字"
    excluded:                             # 明确不在范围内的事
      - "不改 README 的其他 section"
      - "不改代码"

  # 验收
  acceptanceCriteria:                     # 验收标准，Codex review 和 human gate 对照
    - "README.md 包含 Project Description section"
    - "内容准确描述项目定位"
    - "不超过 200 字"
    - "不影响现有 README 结构"

  # 测试
  testRequirements:                       # 测试要求
    - "README.md 可正常渲染"
    - "无拼写/语法错误"

  # 约束
  constraints:                            # 技术约束
    - "使用中文"
    - "不超过 200 字"
  nonGoals:                               # 明确不做的事
    - "不翻译全文"
    - "不改代码注释"

  # 风险与歧义
  riskAreas: []                           # 已知风险
  ambiguityNotes: []                      # 模糊点记录
  humanQuestions: []                      # 需要人确认的问题

  # 状态
  status: "READY_FOR_REVIEW"            # READY_FOR_REVIEW / NEEDS_CLARIFICATION / NEEDS_SCOPE_REDUCTION / REJECTED
  compilationPass: 2                      # 第几轮编译通过
```

### 3.2 设计原则

- **TaskContract 是三方共享的单一真相源。** Claude 实现时读它，Codex review 时对照它，human gate 审批时看它。
- **Artifact contract 约束 agent output shape；TaskContract 约束 task intent / scope / acceptance。两者属于 contract-driven harness 但处在不同层次。**
- **可序列化。** 以 JSON 存储，同时生成人读的 Markdown 版本。
- **不可变。** 一旦 `READY_FOR_REVIEW` 并经 gate 批准，后续节点只能读不能改。如需修改走 revision loop 重新编译。
- **Compiler 负责生成可审查草稿；Gate 负责批准是否进入实现。** ContractCompiler 只产出 draft，真正的 approval 发生在 ContractReviewGate。

---

## 4 ContractCompiler

### 4.1 Two-Pass 流程

```
原始需求文本
    │
    ▼
┌─────────────────────────────────┐
│ Pass 1: Completeness            │
│                                 │
│ 从原始任务提取：                  │
│  - objective                    │
│  - scope (included / excluded)  │
│  - acceptanceCriteria           │
│  - testRequirements             │
│  - constraints                  │
│  - nonGoals                     │
│                                 │
│ 输出：DRAFT TaskContract         │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│ Pass 2: Ambiguity               │
│                                 │
│ 检查：                           │
│  - scope 不清                    │
│  - 验收标准缺失                   │
│  - 模块不明确                    │
│  - 约束冲突                      │
│  - 需要 human 澄清的问题          │
│                                 │
│ 填充：                           │
│  - riskAreas                    │
│  - ambiguityNotes               │
│  - humanQuestions               │
│                                 │
│ 输出：                           │
│  - READY_FOR_REVIEW（无歧义）    │
│  - NEEDS_CLARIFICATION          │
│  - NEEDS_SCOPE_REDUCTION        │
│  - REJECTED                     │
└─────────────────────────────────┘
```

### 4.2 输出状态

| 状态 | 含义 | 后续路由 |
|------|------|---------|
| `READY_FOR_REVIEW` | 契约完整、无歧义 | → `contract.review` gate（人审）→ `context.build_package` → Claude 实现 |
| `NEEDS_CLARIFICATION` | 有 `humanQuestions` 需要回答 | → 回到 intake，带上问题重新编译 |
| `NEEDS_SCOPE_REDUCTION` | scope 过大，需要拆分 | → 回到 intake，建议拆分为多个 sub-task |
| `REJECTED` | 需求不可实现或表述不清 | → 回到 intake，附拒绝原因 |

### 4.3 Handler 设计

```text
Handler type: contract.compile
Node kind: TASK
输入: requirement text（从 requirement.md 读取）
输出:
  - artifacts/task-contract.json  ← 结构化契约
  - artifacts/task-contract.md    ← 人读版本
  - NodeResult(COMPLETED, outputRefs=[task-contract.json, task-contract.md])
```

实现策略（MVP）：
- Pass 1 和 Pass 2 都由 Claude agent 完成（通过 acpx 调用）
- 使用专门的 prompt template（`contract-compile-pass1.md`、`contract-compile-pass2.md`）
- 输出通过 `dual-control` block 解析为结构化 JSON
- 利用现有 `ContractRegistry` 校验输出 schema

### 4.4 三阶段演进模型

当前 P5 MVP 使用 Claude-based ContractCompiler，这只是 MVP，不是最终形态。

#### Stage 1: MVP（P5 实现）

```
Raw Request
  → Claude-based ContractCompiler
  → JSON schema validation
  → ContractReviewGate
  → Approved TaskContract
```

MVP 阶段允许 Claude 生成 contract draft，但输出必须经过 schema validation 和 ContractReviewGate。

**MVP 风险：** Claude 编译 contract，Claude 又基于 contract 实现，存在独立性不足风险。如果 contract 一开始遗漏需求，后续实现和 review 都可能沿着错误 contract 继续推进。

**兜底机制：**

1. ContractCompiler 输出必须通过 schema validation
2. ContractReviewGate 必须对照 raw request 和 TaskContract
3. Codex / human 可以拒绝、缩小范围或要求澄清
4. Codex review 阶段仍应能标记 `CONTRACT_GAP`

**核心原则：LLM 可以生成 contract draft，但不能单独拥有 contract approval authority。**

#### Stage 2: Recommended（后续迭代）

```
Raw Request
  → Deterministic Extractor
  → LLM Contract Draft Compiler
  → Deterministic Contract Checker
  → Codex / Human ContractReviewGate
  → Approved TaskContract
```

各层职责：

**Deterministic Extractor：**
1. 抽取 raw request 中显式提到的文件、模块、任务项
2. 抽取用户明确写出的非目标、约束、验收条件
3. 抽取附件、报告、diff、commit、worktree 等引用
4. 保留原始请求与抽取字段的 trace

目的：防止 LLM 遗漏用户明确要求。

**LLM Contract Draft Compiler：**
1. 把自然语言整理为结构化 TaskContract
2. 补充隐含约束
3. 识别歧义
4. 生成 acceptance criteria
5. 生成 humanQuestions
6. 生成 riskAreas / testRequirements

**Deterministic Contract Checker：**
1. 检查 required fields 是否齐全
2. 检查 acceptanceCriteria 是否为空
3. 检查 nonGoals 是否缺失
4. 检查 scope.included / scope.excluded 是否冲突
5. 检查 raw request 中的强约束是否被遗漏
6. 检查是否包含 unsupported requirement
7. 检查 schema version / artifact path / required references

**ContractReviewGate：** Codex / human 对照 raw request 与 TaskContract，决定是否批准进入实现。

#### Stage 3: Long-term（路线图）

```
Raw Request
  → Multi-role Contract Compiler
      ├── Requirement Extractor
      ├── Product Scope Reviewer
      ├── Architecture Constraint Reviewer
      ├── Testability Reviewer
      └── Risk Reviewer
  → Contract Aggregator
  → ContractReviewGate
```

长期版本将 contract compilation 拆成多个专业角色，P5 不实现，只作为 roadmap。

各角色职责：

| 角色 | 负责字段 |
|------|---------|
| Requirement Extractor | objective / scope / non-goals |
| Product Scope Reviewer | 用户价值、边界、验收标准 |
| Architecture Constraint Reviewer | affected areas、架构约束、集成风险 |
| Testability Reviewer | acceptanceCriteria、testRequirements、可验证性 |
| Risk Reviewer | riskAreas、rollback、安全和数据风险 |
| Contract Aggregator | 合并为最终 TaskContract draft |

---

## 5 ContractReviewGate

### 5.1 设计

```text
Handler type: human.contract_review
Node kind: GATE
输入:
  - artifacts/task-contract.md（人读版本）
  - artifacts/task-contract.json（结构化版本）
Allowed actions:
  - approve     → 契约通过，进入实现
  - clarify     → 回到 ContractCompiler，带上澄清信息
  - reduce_scope → 回到 ContractCompiler，要求缩小范围
  - reject      → 终止，不进入实现
```

**核心原则：TaskContract 未通过 review，不进入实现。**

### 5.2 Gate Document

```json
{
  "schemaVersion": 1,
  "gateId": "gate-tc-review-001",
  "nodeInstanceId": "node-contract-review-001",
  "handlerType": "human.contract_review",
  "status": "open",
  "allowedActions": ["approve", "clarify", "reduce_scope", "reject"],
  "createdAt": "2026-05-29T10:01:00Z",
  "resolvedAt": null,
  "result": {
    "taskContractPath": "artifacts/task-contract.md",
    "objective": "...",
    "acceptanceCriteriaCount": 4,
    "humanQuestionsCount": 0
  }
}
```

---

## 6 现有系统集成

### 6.1 复用现有机制

| 现有机制 | 复用方式 |
|---------|---------|
| **Task / Decision / Gate runtime** | `contract.compile` 是 TASK，`contract.review` 是 GATE，走现有 `WorkflowRuntime.resume` |
| **events.jsonl** | 新增 4 个事件类型（见 6.2） |
| **current.json** | 不改结构，contract 节点就是普通的 active node |
| **Artifact contract** | 不改。TaskContract 是 task-level contract，与现有的 node output contract 并存 |
| **Context governance** | `context.build_package` 渲染时引用 `task-contract.json` 作为新 section |
| **Human gate** | `contract.review` 复用现有 gate 审批流程（`dualctl review approve/revise`） |
| **Decision handler** | 不改。contract.compile 的结果通过 `dual-control` block 解析 |
| **Revision loop** | contract 被拒绝 → revision → 重新编译 → 新 iteration |
| **LedgerMigrator** | MVP 阶段不迁移旧账本。旧 workstream 按 legacy 策略处理（见 6.5） |

### 6.2 新增事件

```text
contract_compilation_started    — ContractCompiler 开始
contract_compilation_completed  — 编译完成，附 status + artifact paths
contract_review_completed       — Human review 完成
contract_approved               — 契约通过，准备进入实现
```

### 6.3 新增 Artifact

```text
artifacts/
  task-contract.json    ← 结构化契约（machine-readable）
  task-contract.md      ← 人读版本（human-readable）
```

### 6.4 Workflow 定义变更

#### 策略：contract_review 替代原 requirement review（方案 A）

P5-enabled workflow 中，`contract.review` 替代原来的 `gate.human_requirement_review`，成为进入实现前的主 gate。

**原因：** P5 的目标是让 TaskContract 成为进入实现前的共同契约基准。如果保留两个相邻的人审 gate（requirement review + contract review），容易增加流程摩擦。contract review 已经包含了对需求意图和 scope 的审查，不需要在其前面再加一层 requirement review。

```text
intake.claude_brainstorm
  → context.intake_codex_product_review
    → intake.codex_product_review
      → intake.decision
        → gate.human_requirement_review ──(approved)──→
contract.compile              ← 新增：编译 TaskContract
  → gate.contract_review      ← 替代原 requirement review，审查 TaskContract
    ──(approved)──→
design.claude_design
  ...
```

**Legacy workflow 兼容：** 旧 workflow 定义（不含 contract 节点）仍可正常运行，`gate.human_requirement_review` 继续作为进入实现前的审批点。P5-enabled workflow 只在新 workstream 中默认启用。

#### 可选方案 B（不推荐）：两者共存

如果未来需要分层审查，可以让 `gate.human_requirement_review` 只判断原始需求是否值得进入 contract 编译（是否属于支持场景、是否需要拒绝），`gate.contract_review` 审查 TaskContract 是否可执行、可验证、scope 是否明确。但 P5 阶段不推荐，因为增加流程摩擦。

### 6.5 Legacy Workstream 策略

MVP 阶段优先支持新 workstream；旧 ledger migration 后置。

**Existing workstreams without `task-contract.json` should be treated as legacy runs.**

Legacy runs 的处理策略：

1. 旧 workstream 可以继续按旧流程查看和 replay
2. 不强制 retroactively 生成 TaskContract
3. 如果用户要继续旧 workstream，可选择手动触发 `contract.bootstrap`
4. P5-enabled workflow 只对新 workstream 默认启用
5. Context profile 在旧 workstream 中使用 `MissingPolicy.WARN`，不 fail

**LedgerMigrator 后置：** 当前项目 MVP 阶段不应为 P5 增加过高迁移成本。只要新建 workstream 可以正确生成 contract artifacts、events 和 current snapshot，即可满足 P5 MVP。旧 ledger migration 可在 P7（CalibrationCandidate）阶段随 schema 升级一起处理。

---

## 7 Codex Review 如何使用 TaskContract

### 7.1 Context Package 增强

`context.build_package` 在为 Codex review 节点渲染上下文时，新增一个 section：

```text
## TaskContract

**Objective:** {objective}

**Scope:**
- Included: {scope.included}
- Excluded: {scope.excluded}

**Acceptance Criteria:**
{acceptanceCriteria}

**Non-Goals:**
{nonGoals}

**Constraints:**
{constraints}

**Risk Areas:**
{riskAreas}
```

这通过在 context profile 中增加一个 `task_contract` section 实现：

```java
// P5-enabled workflow：TaskContract 是 CRITICAL context section
new SectionRequirement("task_contract",
    MissingPolicy.FAIL,   // P5-enabled workflow 中 TaskContract 缺失应 fail
    Priority.CRITICAL,    // 契约是 review 的核心输入
    new DegradationPolicy(Degradation.FULL, 0),
    OverflowPolicy.FAIL,
    2_000)
```

**Legacy workflow 兼容：** 旧 workflow 不含 `task-contract.json`。对于旧 workstream，context profile 使用 `MissingPolicy.WARN`，允许继续运行。

**降级策略：** TaskContract 是 CRITICAL section，不应被 context degradation 裁剪。如果 context budget 不足，应裁剪低优先级历史输出。Raw user request 可以降级为 pointer-only，但 TaskContract 不应被降级为 pointer-only。

### 7.2 Codex Review Checklist

Codex review prompt 应指示 Codex 对照 TaskContract 检查：

```text
Review the implementation against the TaskContract:

1. Does the implementation satisfy each acceptance criterion?
2. Does the implementation violate any non-goal?
3. Are there out-of-scope modifications (files changed beyond scope.included)?
4. Are required tests present and adequate?
5. Is there a contract gap — acceptance criteria that don't cover the actual behavior?
```

### 7.3 Review Output

Codex review 的 `dual-control` block 扩展：

~~~~text
```dual-control
decision: PASS
summary: Implementation satisfies all acceptance criteria
contract_gaps: []
out_of_scope_files: []
missing_tests: []
```dual-control
~~~~

新字段（向后兼容，缺失时不影响现有解析）：
- `contract_gaps` — 发现的契约遗漏
- `out_of_scope_files` — 超出 scope 的文件变更
- `missing_tests` — 缺失的测试

这些字段为 P6（FailureArbiter）提供输入，但 P5 阶段只记录不分类。

---

## 8 Final Report 增强

`report.generate_final` 在报告中新增 TaskContract 段：

```text
## TaskContract

**Objective:** ...
**Status:** Approved
**Acceptance Criteria:** 4 items, all satisfied
**Contract Gaps Found:** 0
**Out-of-scope Changes:** 0
```

---

## 9 非目标

本轮不做：

```text
FailureArbiter（P6）            — 只记录 contract_gaps，不分类
CalibrationCandidate（P7）      — 只积累数据，不检测模式
Multi-role Review（P8）         — Codex review 仍是单一角色
Adversarial Test Agent（P8）    — 不新增 tester agent
SpecializationRegistry         — 不新增角色注册
Web UI                         — 不改 UI
数据库化 ledger                — 仍是文件账本
Codex 写业务代码               — Codex 仍是 reviewer
```

---

## 10 实现估算

| 组件 | 类型 | 估算 |
|------|------|------|
| `TaskContract` record | dual-core | 新建 1 个 record |
| `contract.compile` handler | dual-runtime | 新建 1 个 handler，2 个 prompt template |
| `human.contract_review` gate | 不需要新 handler | 复用 `human.review`，配新的 `allowedActions` |
| Prompt templates | dual-adapter-acpx | `contract-compile-pass1.md`、`contract-compile-pass2.md` |
| Context profile 更新 | dual-runtime | 增加 `task_contract` section |
| Workflow definition 更新 | dual-runtime | 插入 2 个新节点 |
| LedgerMigrator | dual-ledger-file | MVP 不做，旧账本 migration 后置 |
| Test | 各模块 | ~15 个新测试 |
| Docs | docs/ | 更新 features.md、architecture、README |

---

## 11 风险

| 风险 | 缓解 |
|------|------|
| ContractCompiler prompt 可能不稳定 | 复用 `dual-control` block + `ContractRegistry` 校验输出 |
| 增加一次 agent 调用（编译） | 可选：小任务跳过编译，直接 `READY_FOR_REVIEW` |
| Human 多审一轮（contract review） | 这是设计意图——前期多审，减少后期返工 |
| 与现有 workflow 不兼容 | 新节点插入 intake 后，不改动 design/plan/implement 阶段 |
