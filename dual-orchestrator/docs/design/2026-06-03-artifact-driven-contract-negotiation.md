# Artifact-driven Contract Negotiation 设计与评估

> 日期：2026-06-03
> 状态：设计稿
> 背景文档：
> - `docs/long-term-plan/gap-analysis-harness-paper.md`
> - `docs/long-term-plan/cc-codex-harness-short-vs-long-term-comparison.md`
> - `docs/design/P5-task-contract-and-contract-compiler.md`

> 2026-06-03 实现进度：已落地 P0 最小修复。`ContractCompileHandler` 现在优先读取可信 Claude intake 输出中的 `<dual-artifact type="task_contract_proposal" version="1">`，通过机械校验后生成 canonical TaskContract；invalid proposal 会失败而不是 fallback 到 Markdown。Claude intake prompt 已要求输出 proposal，Codex intake prompt 已注入 contract review principles，原则模板支持内置资源和项目 `.agent/templates/` 补充。

---

## 1. 背景

dual-orchestrator 的目标已经从早期的“双 Agent 自动化脚本”演进为一个 AI engineering harness。

它要解决的不是“Claude 或 Codex 会不会写代码”，而是：

- 需求能不能被稳定地转成可审查、可验收的共同事实。
- Claude、Codex、Human 是否都围绕同一份 contract 工作。
- 上下文是否按阶段治理，而不是把所有历史输出都塞给模型。
- 失败后能不能判断是实现错、契约缺口、上下文不足、review noise，还是工具失败。
- 真实失败能不能反过来改进 contract template、review principles 和 context rules。

论文 *Meta-Engineering Harnesses for AI-Native Software Production* 的核心结论是：

> Harness is not a model, prompt, or agent. It is the surrounding system that makes agent outputs more reliable.

也就是说，可靠性不能只靠某一次 prompt，也不能只靠模型能力，而要靠外层 harness：contract、workflow、ledger、verification、failure arbiter、calibration。

当前 dual-orchestrator 已经具备 workflow runtime、ledger、context governance、TaskContract MVP、FailureArbiter MVP、CalibrationCandidate seed、review packet、timeline 和 real-case benchmark。它已经不是裸 Agent 调用工具。

但真实 `cancer-clinic-feature-slice` benchmark 暴露了一个关键问题：

> 当前 TaskContract 的来源仍然不可靠。它主要由 `ContractCompileHandler` 从 LLM Markdown 和 context package 中用 heuristic / regex 抽取，结果可能把历史 README 示例、context metadata、旧页面描述混入 canonical `task-contract.json`。

这说明 P5 TaskContract MVP 的方向是对的，但“ContractCompiler”的实现方式需要修正。

---

## 2. 当前问题

### 2.1 事实来源错误

当前链路近似是：

```text
LLM Markdown output
  -> Java heuristic extraction
  -> artifacts/task-contract.json
  -> design / plan / implementation / acceptance all depend on it
```

这存在结构性风险：

- Markdown 是给人读的，不是稳定数据协议。
- LLM 输出格式会随模型、上下文、语言、阶段而变化。
- context package 中包含 ledger metadata、历史输出、human feedback、tool traces，容易污染抽取结果。
- 一旦错误字段进入 canonical `task-contract.json`，后续阶段会把错误当事实。

真实失败中出现的问题包括：

- `scope.included` 被错误编译成 `README.md`。
- acceptance criteria 混入 `Workstream:`、`Builder Node:`、`Generated At:` 等 metadata。
- 旧 record page 能力被误当成当前任务验收项。
- Codex design review 正确指出 canonical TaskContract 不可信，导致 revision loop 到达上限。

### 2.2 Hardcoded 规则不可持续

继续给 `ContractCompileHandler` 增加 regex 或过滤规则，只能解决当前样本。

长期会继续遇到：

- 标题改了，规则失效。
- 中英文混写，规则失效。
- Agent 输出顺序变化，规则失效。
- context package 新增 section，规则误抓。
- 真实需求里包含示例代码块，解析器误判。

这不是“规则不够多”，而是“系统不该从自然语言里猜 canonical fact”。

### 2.3 Human gate 不应该做流程质检

一个直觉修复是：让 human approve contract。

但这不符合 dual 的目标。Human 不应该检查 JSON 字段、路径污染、schema 是否合规、上下文 metadata 是否混入。这些是系统和 Agent review 应承担的事。

Human gate 应该只处理：

- 产品意图是否正确。
- 范围取舍是否接受。
- 风险是否需要人负责。
- 机器无法裁决的业务歧义。
- 是否授权进入实现或交付。

如果每次 contract 都需要人做流程质检，dual 只是把 copy/paste 变成点按钮，没有真正减少盯守成本。

---

## 3. 设计目标

### 3.1 总目标

把 TaskContract 从“Java 从 Markdown 猜出来的字段”改成：

```text
Claude contract proposal
  -> system mechanical validation
  -> Codex principle review
  -> Claude revision if needed
  -> canonical TaskContract
```

### 3.2 具体目标

1. **Artifact-driven**
   - 系统事实来源必须是结构化 artifact。
   - Markdown 继续给人看，但不作为状态机事实来源。

2. **LLM 做原则性判断**
   - Claude 负责提出规格化理解。
   - Codex 按 review principles 审查意图、范围、验收、证据和风险。
   - 系统不替模型理解需求。

3. **系统做机械校验**
   - 解析 artifact envelope。
   - 校验 JSON / schema / required fields / path evidence / metadata contamination。
   - 失败时阻断流程或进入 repair，不带错合同继续跑。

4. **Human 只处理真实决策**
   - `NEEDS_HUMAN`、revision limit、产品歧义、高风险授权时才进入 human gate。
   - Human 不做 JSON 或流程校验。

5. **预留模板提取能力**
   - 不 upfront 设计专业模板库。
   - 但失败可以生成 template candidate，后续经人审提升为 principle/template file。

---

## 4. 非目标

本阶段不做：

- 完整规则引擎。
- 大而全的专业模板库。
- 自动应用模板更新。
- 多角色 reviewer。
- Adversarial test agent。
- Knowledge registry 完整产品化。
- Schema migration 框架。
- 数据库化 ledger。
- 后台常驻 scheduler 的复杂策略。

这些能力和论文匹配度有关，但短期不是主线阻塞点。

---

## 5. 核心设计

### 5.1 Agent Artifact Envelope

新增统一结构化输出边界：

```text
<dual-artifact type="task_contract_proposal" version="1">
{
  "...": "json"
}
</dual-artifact>
```

设计原则：

- 系统只解析 `<dual-artifact>` 内 JSON。
- Markdown 正文可以自由表达，不影响状态机事实。
- 如果有多个同类型 block，第一版只接受最后一个 terminal block，并记录 warning。
- block 缺失或 JSON invalid 时，不 fallback 到 Markdown 抽取。
- 对 `task_contract_proposal`，第一版只信任 Claude intake 输出。Codex review Markdown 里可能引用 Claude 的 artifact、带行号摘录或复述 JSON，这些内容不能作为 canonical proposal 来源。

第一版支持：

- `task_contract_proposal`

接口要预留后续扩展：

- `stage_decision`
- `self_test_report`
- `acceptance_review`
- `handoff`
- `failure_classification`
- `template_candidate`

### 5.2 TaskContract Proposal

Claude 输出的是 proposal，不是 canonical contract。

```json
{
  "artifact_type": "task_contract_proposal",
  "schema_version": 1,
  "objective": "在治疗安排详情页添加标记完成和延期状态操作",
  "scope": {
    "included": [
      "miniprogram/src/pages/treatment-event/index.vue"
    ],
    "excluded": [
      "不改后端 API",
      "不改数据库 schema"
    ]
  },
  "acceptance_criteria": [
    "用户可以点击标记完成并刷新页面状态",
    "用户可以选择延期日期并提交延期",
    "不出现医疗判断类禁用文案"
  ],
  "constraints": [],
  "non_goals": [],
  "risk_areas": [],
  "test_requirements": [],
  "evidence_refs": [],
  "open_questions": []
}
```

### 5.3 Mechanical Validation

系统只检查确定性条件：

- envelope 是否存在。
- JSON 是否能解析。
- `artifact_type == task_contract_proposal`。
- `schema_version == 1`。
- `objective` 非空。
- `scope.included` 非空。
- `acceptance_criteria` 非空。
- list 字段类型正确。
- scope 路径格式合理。
- 既有文件必须存在；新文件必须明确声明为 new file。
- 字段不得包含明显 ledger/context metadata：
  - `Workstream:`
  - `Builder Node:`
  - `Target Node:`
  - `Generated At:`
  - `Context Package`
  - `Context Governance`
- `open_questions` 非空时，不能自动 canonicalize。

校验失败时：

```text
MISSING_PROPOSAL
INVALID_JSON
SCHEMA_INVALID
SCOPE_INVALID
METADATA_CONTAMINATION
OPEN_QUESTIONS
```

这些是流程问题或信息缺口，不是业务 review。

### 5.4 Principle Review

Codex 不再从长 Markdown 中猜 contract，而是审查 proposal：

> This proposal is the candidate shared fact for the next stage. Is it sufficient, faithful, scoped, verifiable, and evidence-grounded?

Codex 按原则判断：

- **Intent fidelity**：是否忠实反映用户目标。
- **Scope discipline**：范围是否克制，是否避免无关重构。
- **Non-goal clarity**：非目标是否足以防止 scope creep。
- **Verifiability**：验收标准是否可观察、可测试、可 review。
- **Evidence grounding**：是否引用用户需求、repo 文件、既有 artifact。
- **Risk coverage**：是否覆盖关键业务/架构风险。
- **Human decision boundary**：是否存在必须由人裁决的歧义。

Codex 输出：

```text
PASS
NEEDS_REVISION
NEEDS_HUMAN
BLOCKED
```

语义：

| Decision | 含义 | 路由 |
|---|---|---|
| `PASS` | proposal 足以成为 canonical contract | `contract.finalize` |
| `NEEDS_REVISION` | Claude 应修订 proposal | 回 Claude revise，最多 2 轮 |
| `NEEDS_HUMAN` | 存在产品/范围/风险裁决 | human decision gate |
| `BLOCKED` | 工具、上下文或 repo 事实不足 | recovery |

### 5.5 Contract Negotiation Loop

建议流程：

```text
intake.claude_brainstorm
  -> contract.extract_proposal
  -> contract.mechanical_validate
  -> context.contract_codex_review
  -> contract.codex_review
  -> contract.decision
       PASS -> contract.finalize -> design.claude_design
       NEEDS_REVISION -> contract.claude_revise
       NEEDS_HUMAN -> gate.human_contract_decision
       BLOCKED -> recovery
```

第一版可以合并 handler，但概念上要保持这些边界。

### 5.6 Canonical TaskContract

只有 Codex principle review `PASS` 后，系统才写：

```text
artifacts/task-contract.json
artifacts/task-contract.md
```

canonical contract 必须带来源信息：

```json
{
  "source": {
    "proposal_ref": "...",
    "review_ref": "...",
    "created_by": "contract.finalize",
    "review_decision": "PASS"
  }
}
```

后续 design / plan / implement / acceptance 只能对照 canonical contract。

---

## 6. Principle Template 与专业模板能力

### 6.1 为什么不 upfront 做专业模板库

论文中的专业模板更像成熟组织沉淀下来的工程资产：

- 支付系统模板。
- 医疗安全 checklist。
- API invariants。
- Release / rollback policy。
- QA test matrix。

这些模板不是凭空设计出来的，而是从大量真实失败、事故、review、测试经验中沉淀。

当前阶段直接做大而全的模板库，容易变成空泛 checklist，短期成本高，且未必改善真实 run。

### 6.2 当前阶段的正确做法

采用：

```text
通用原则模板
  + 项目轻量 profile
  + 真实失败驱动的 template candidate
```

### 6.3 Principle Template 文件

内置模板：

```text
templates/contract-review-principles.md
templates/acceptance-review-principles.md
templates/handoff-principles.md
```

项目可补充：

```text
.agent/templates/domain-principles.md
.agent/templates/contract-review-principles.md
.agent/templates/acceptance-review-principles.md
```

这些模板注入 prompt，给 Codex 做原则性判断。Java 不逐条执行这些原则。

### 6.4 Template Extraction / Candidate

从真实失败中生成候选：

```json
{
  "candidate_type": "TEMPLATE_UPDATE",
  "target_template": ".agent/templates/contract-review-principles.md",
  "operation": "append_principle",
  "proposed_text": "涉及 TreatmentEvent.status 的任务必须明确状态变更触发者：用户手动、医生安排、系统初始化或自动规则。",
  "evidence_refs": [
    ".agent/workstreams/ws-xxx/recovery/current-failure.md",
    ".agent/workstreams/ws-xxx/runs/run-xxx/outputs/acceptance-codex-review.md"
  ],
  "reason": "Codex acceptance detected ambiguity around status mutation authority.",
  "status": "DRAFT"
}
```

第一版只生成 candidate，不自动应用。

后续可扩展：

```bash
dualctl calibration list
dualctl calibration show <candidate-id>
dualctl calibration decide <candidate-id>
dualctl calibration plan <candidate-id>
```

`plan` 只生成人工可审的 apply plan，不自动修改模板。真正的 `promote` 需要等真实候选积累后再评估。

---

## 7. 模块改造计划

### 7.1 P0：修正主线事实来源

目标：`task-contract.json` 不再来自 Markdown heuristic。

改造项：

1. 新增 `AgentArtifactEnvelopeParser`
   - 解析 `<dual-artifact>`。
   - 支持 type / version / json body。
   - 处理 missing、duplicate、malformed、non-terminal。

2. 新增 `TaskContractProposal`
   - proposal 数据模型。
   - 和 canonical `TaskContract` 分开。

3. 新增 `TaskContractProposalValidator`
   - 机械校验。
   - 输出 validation result。

4. 改造 `ContractCompileHandler`
   - 从 proposal 生成 candidate。
   - 不再从 Markdown 贪婪抽取。
   - 不 fallback 猜测。

5. 新增/改造 contract review prompt
   - Codex 按 principles 审 proposal。
   - 输出 `PASS / NEEDS_REVISION / NEEDS_HUMAN / BLOCKED`。

6. 新增 contract finalize
   - PASS 后写 canonical `task-contract.json/md`。

7. 回归真实 case
   - `cancer-clinic-feature-slice`。
   - 验证 contract 不污染，设计阶段不再因 canonical contract 错误而 revision-limit。

### 7.2 P1：Artifact-first 后续阶段

目标：acceptance / failure / handoff 也减少自然语言猜测。

改造项：

1. Acceptance review artifact
   - Codex acceptance 输出结构化 artifact。
   - 包含 decision、must_fix、notes、contract_gaps、out_of_scope_files、missing_tests。

2. Failure classification artifact
   - classifier 读取 acceptance artifact。
   - 避免 `ACCEPT_WITH_NOTES` 被误判为 `REJECT`。

3. Handoff artifact 正式化
   - handoff JSON 包含 completed、current_state、decisions、open_questions、risks、next_stage_prompt、evidence_refs。
   - Markdown 给人看，JSON 给 context builder。

4. Context package artifact-first
   - 优先注入 canonical contract、latest structured review、git evidence、self-test artifact。
   - recent agent outputs 降级为 supplemental。

### 7.3 P1：Principle Template Seed

目标：开始接入模板提取能力，但不做完整模板系统。

改造项：

1. 新增 `TemplateProvider`
   - 读取内置 templates。
   - 读取项目 `.agent/templates/*.md`。
   - 合并注入 review prompt。

2. 扩展 CalibrationCandidate
   - 支持 `PRINCIPLE_TEMPLATE` / `TEMPLATE_UPDATE`。
   - 写入 `.agent/calibration/template-candidates/`。

3. final report / recovery summary 引用 template candidate。

### 7.4 P2：Promotion 与知识库

等真实 case 产生 2-3 个候选后再做：

- `dualctl calibration list/show/decide/plan` 先把候选变成可审阅 apply plan
- 是否增加 `promote` 命令需要重新评估，避免在规则还不稳定时自动改模板
- append 到 `.agent/templates/*`
- template version metadata
- replay 验证新模板是否改善历史 run

---

## 8. 与论文的匹配度再评估

### 8.1 当前匹配度

当前 dual-orchestrator 大约匹配论文核心思想的 **70%**。

已对齐：

- Harness 级定位。
- Claude / Codex / Human 角色分离。
- Workflow runtime。
- Ledger / events / timeline。
- Context governance。
- TaskContract MVP。
- FailureArbiter MVP。
- CalibrationCandidate seed。
- Delivery pipeline。

主要偏差：

- TaskContract 来源不稳定，仍依赖 Markdown heuristic。
- ContractCompiler 不是确定性组件 + 专业模板，也不是稳定的 Agent negotiation layer。
- Review 输出仍主要依赖 `dual-control` 字符串。
- Handoff / acceptance / failure classification 仍有自然语言推断。
- Template extraction / promotion 还没有闭环。

### 8.2 本方案完成后的匹配度

P0 + P1 完成后，预计匹配度提升到 **85%** 左右。

原因：

- Contract 不再是从 Markdown 猜出来，而是 Agent proposal + Codex principle review + system canonicalization 的显式产物。
- Context package 会更 artifact-first，减少污染。
- Failure classification 会有更稳定输入。
- Template candidate seed 会开始接入论文的 outer-loop calibration。

### 8.3 仍然未覆盖的论文能力

即使完成本方案，仍不等于完全复现论文。

| 能力 | 当前计划 | 差距 |
|---|---|---|
| Adversarial Test Agent | 不做 | 缺少独立 tester agent 从 contract 生成反例 |
| Multi-role Review | 不做 | Codex 仍是综合 reviewer，不拆 product/security/QA/deploy |
| Knowledge Registry | seed | 只有 template candidate，没有版本化知识库 |
| Metrics / Replay | ledger 基础 | 缺少 run-level 指标、历史 replay、策略 A/B 对比 |
| Policy Engine | 不做 | 没有复杂审批策略和组织权限模型 |
| 专业模板库 | 不 upfront 做 | 只做 principle seed 和项目 profile |

这些差距是长期路线，不是当前主线阻塞。

---

## 9. 风险与应对

### 9.1 Prompt 漂移

风险：Claude 不输出 `<dual-artifact>` 或 JSON 格式漂移。

应对：

- 缺 artifact 直接进入 repair/recovery。
- 不 fallback 到 Markdown 猜。
- prompt regression test 覆盖结构化输出。

### 9.2 LLM proposal 内容错误

风险：Claude 结构化 JSON 格式正确，但理解错需求。

应对：

- Codex principle review 对照 raw request / repo evidence 审查。
- `NEEDS_REVISION` 回 Claude 修订。
- `NEEDS_HUMAN` 只在真实歧义时找人。

### 9.3 过度设计

风险：为了贴近论文，一次性引入模板库、规则引擎、多角色、知识库。

应对：

- 当前只做 envelope、proposal、validator、principle templates、candidate seed。
- Promotion、knowledge registry、multi-role review 后置。

### 9.4 Human gate 退化为流程质检

风险：系统把格式或 schema 问题抛给人。

应对：

- 格式/schema/path/metadata 问题由系统处理。
- Human 只接收产品/范围/风险裁决问题。

### 9.5 Artifact 过多导致上下文复杂

风险：结构化 artifact 增多后 context builder 又变得臃肿。

应对：

- artifact-first 不等于 artifact-all。
- 每个 profile 明确只注入当前阶段必要 artifact。
- recent outputs 降级为 pointers / supplemental。

---

## 10. 验收标准

### 10.1 单元与集成

- `AgentArtifactEnvelopeParser` 覆盖 missing / malformed / duplicate / terminal / injected block。
- `TaskContractProposalValidator` 覆盖 empty fields / invalid paths / metadata contamination / open questions。
- `ContractCompileHandler` 不再从 Markdown heuristic 生成 canonical contract。
- prompt regression 确认 Claude contract prompt 要求输出 artifact。
- Codex contract review prompt 包含 principles。

### 10.2 真实 case

使用：

```bash
./scripts/run-real-case.sh \
  --repo /Users/kriswang/work/projects/ai/cancer-clinic \
  --task-file docs/testing/real-case-benchmarks/cases/cancer-clinic-feature-slice.md \
  --variant p6
```

预期：

- `task-contract.json.scope.included` 是真实业务文件，不是 README。
- acceptance criteria 不包含 context metadata。
- design review 不再因为 canonical contract 污染而反复 revision。
- 如果失败，失败原因应是产品/设计/实现/工具问题，而不是 contract 抽取污染。
- recovery summary 能清楚说明失败分类。
- 若出现可复用经验，生成 template candidate。

---

## 11. 结论

这次改造不是继续增强 `ContractCompileHandler`，而是修正 contract layer 的事实来源。

正确方向是：

```text
Structured artifacts carry facts.
LLM reviewers apply principles.
The harness validates, records, routes, and calibrates.
```

这比“用更多规则约束 LLM”更贴近论文核心思想，也更符合 dual-orchestrator 的目标。

短期实现只需要完成：

- Agent artifact envelope。
- TaskContract proposal。
- Mechanical validation。
- Codex principle review。
- Canonical TaskContract。
- Principle template injection seed。
- Template candidate seed。

这样可以解决当前真实 benchmark 暴露的主线问题，同时给专业模板、outer-loop calibration、knowledge registry 留出自然演进路径。
