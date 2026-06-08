# Contract Boundary Hardening 设计

> 日期：2026-06-06
> 状态：已实现主干
> 关联文档：
> - `docs/design/2026-06-03-artifact-driven-contract-negotiation.md`
> - `docs/design/P5-task-contract-and-contract-compiler.md`
> - `docs/design/P6-failure-arbiter.md`
> - `docs/design/workflow-state-machine.md`

## 0. 实现状态

2026-06-06 已完成主干落地：

- prompt allowed decisions / required control keys 由 `ContractRegistry` 注入。
- self-test / acceptance / handoff 要求 typed `<dual-artifact>`，缺失或非法时严格失败。
- `contract.compile` 默认不再从 Markdown 猜 canonical TaskContract，缺少 `task_contract_proposal` 时失败。
- failure classification 改为读取结构化 failure fields，不再用 review summary keyword 做主分类。
- human review packet / operator guidance 优先读取 structured sidecar 中的 `blocking`、`must_fix`、`missing_evidence`、`open_risks`。
- `AgentEvidenceIndex` 统一读取 validated sidecar，负责发现、排序、解析和 `evidence_state` 暴露；context boundary、human gate、failure classify 不再各自 walk `runs/**/.contract.json`。
- 公开 issue-flow 真实运行中的 self-test、acceptance、handoff 输出已经沉淀为 sanitized fixture regression，用来约束真实 Agent artifact 形态，不依赖 fake `acpx`。

已通过 `mvn -pl dual-cli,dual-server -am test`。真实 case 仍需要继续作为 prompt/schema 兼容性验证，不应通过恢复 Markdown fallback 来绕过 strict failure。

## 1. 背景

dual-orchestrator 的定位已经从“双 Agent 自动化脚本”变成 contract-driven harness。

最近的真实 case 暴露出一个关键事实：如果 orchestrator 继续从 LLM 的 Markdown 中拼字符串、猜标题、靠 `contains()` 判断意图，系统很快会退回到不可维护的 if/else 逻辑。

这不是普通代码整洁问题，而是 harness 的职责边界问题。

harness 的目的不是迁就 LLM 的不稳定输出，而是规范 LLM 的工程行为。如果 Agent 不能输出合法 structured artifact，系统应该停下来找原因：

- contract 边界是否设计错了。
- prompt 是否没有讲清楚。
- artifact schema 是否不合理。
- LLM 当前能力或上下文是否不足。

不应该静默 fallback 到 Markdown 启发式抽取。

## 2. 当前已清晰的边界

当前已经确立的边界：

| 事项 | 归属 |
|------|------|
| workflow current state | orchestrator |
| ledger 写入 | orchestrator |
| worktree / git evidence / run metadata | orchestrator |
| allowed decisions / node output contract | orchestrator |
| `dual-control` decision | LLM 提议，orchestrator 校验 |
| final routing | orchestrator |
| 需求理解、设计、实现、自测、验收判断 | LLM |
| human gate 价值判断与风险接受 | human |

2026-06-05 的修复已经把 `dual-control` 从“自然语言末尾控制块”改成“Agent proposed status”。runtime 读取最后一个顶层状态块，再用 `NodeOutputContract` 校验合法状态，最后才写 ledger 和路由。

这一步把状态边界立住了，但 artifact 边界仍需要补齐。

## 3. 仍然模糊的五个区域

### 3.1 Prompt contract 双份维护

Prompt 中手写 allowed decisions，Java `ContractRegistry` 中也有 allowed decisions。

风险：

- prompt 和 Java contract 漂移。
- Agent 输出一个 prompt 允许但 runtime 不允许的状态。
- 后续新增节点时容易漏改其中一处。

### 3.2 Agent evidence 仍从 Markdown section 抽取

`ArtifactContractValidator` 当前会从 Markdown 标题抽：

- `Checks Run`
- `Checks Skipped`
- `Evidence`
- `Missing Evidence`
- `Must Fix`
- `Acceptance Notes`

风险：

- 标题变化导致抽取失败。
- 中文标题、英文标题、同义标题不断增加。
- 后续继续堆 aliases，变成隐形规则引擎。

### 3.3 Contract compile 仍有启发式 fallback

`ContractCompileHandler` 已优先读取 `task_contract_proposal`，但缺失 proposal 时仍会从 requirement markdown 中提取 objective / scope / tests / acceptance criteria。

风险：

- orchestrator 又开始理解需求。
- context metadata、示例、历史输出可能污染 canonical contract。
- 合同一旦污染，后续设计、实现、验收都会围绕错误事实运行。

### 3.4 Failure classification 仍读 review summary keyword

`FailureArbiter` 会读取 `reviewSummary` 中的关键词，例如 `unclear`、`missing context`、`non-blocking`。

风险：

- review summary 是人读文本，不是机器协议。
- LLM 表达稍变，分类规则就失效。
- 失败修复路线由字符串猜测驱动。

### 3.5 Human review guidance 仍有 contains 推断

`HumanReviewGateHandler` 根据 decision string 和 Markdown section 生成推荐动作。

风险：

- human gate 推荐动作被自然语言 section 影响。
- review packet 读模型和机器事实来源不一致。
- UI / CLI 后续会继承同样的模糊判断。

## 4. 设计目标

### 4.1 总目标

把 Agent 输出分成两层：

```text
Markdown report: 给人读
Structured artifact: 给 orchestrator 校验、记账、路由、展示
```

orchestrator 不再从 Markdown 抽机器事实。

### 4.2 具体目标

1. **Prompt contract single source of truth**
   - allowed decisions 由 `ContractRegistry` 提供。
   - prompt 渲染时注入 allowed decisions。
   - prompt 不再手写一份状态枚举。

2. **Structured artifact first**
   - self-test、acceptance、handoff 输出 structured artifact。
   - artifact 通过 schema / required fields / enum 校验。
   - Markdown section 抽取不参与主事实；如确需兼容旧样本，只能通过显式 legacy flag 启用，默认关闭。

3. **Strict failure**
   - 缺少合法 structured artifact 时，节点失败。
   - 不 fallback 到 Markdown semantic extraction。
   - failure summary 明确指出是 artifact contract failure。

4. **Failure classification field-driven**
   - Codex acceptance 输出结构化 failure fields。
   - FailureArbiter 基于字段和固定优先级分类。
   - summary keyword 只保留为 diagnostics，不参与主分类。

5. **Human review packet field-driven**
   - 推荐动作来自 structured sidecar / review packet JSON。
   - Markdown 只作为 evidence display。
   - UI / CLI 消费同一份结构化 overview。

## 5. 非目标

本阶段不做：

- Spring StateMachine。
- 通用规则引擎。
- JSON Schema 引擎全量引入。
- 多角色 reviewer。
- 自动修 prompt。
- 自动提升 calibration candidate。
- 数据库化 ledger。
- schema migration。

这些可以以后做，但不是当前主线防腐的必要条件。

## 6. 核心模型

### 6.1 Agent output envelope

沿用现有 artifact envelope：

```text
<dual-artifact type="acceptance_review" version="1">
{
  "artifact_type": "acceptance_review",
  "schema_version": 1,
  "decision": "ACCEPT",
  "summary": "The implementation satisfies the contract.",
  "blocking": false,
  "checks": [],
  "evidence": [],
  "missing_evidence": [],
  "must_fix": [],
  "acceptance_notes": [],
  "failure": {
    "category_hint": "",
    "contract_gaps": [],
    "out_of_scope_files": [],
    "missing_tests": [],
    "reason": ""
  }
}
</dual-artifact>
```

原则：

- envelope 是机器事实来源。
- Markdown 正文可以自由表达。
- 多个同类型 artifact 时，第一版读取最后一个，并记录 `candidate_count`。
- invalid JSON / missing required fields / invalid enum 均为 hard failure。
- 不从 Markdown 补缺失字段。

### 6.2 Artifact 类型

第一批支持：

| Type | 生产者 | 用途 |
|------|--------|------|
| `task_contract_proposal` | Claude intake | 生成 canonical TaskContract |
| `self_test_report` | Claude self-test | 给 acceptance 和 final report 提供 developer evidence |
| `acceptance_review` | Codex acceptance | 验收决策、缺口、分类字段 |
| `stage_handoff` | Claude handoff | 下一阶段上下文和 prompt |

后续可扩展：

| Type | 用途 |
|------|------|
| `design_review` | 设计审查结构化字段 |
| `plan_review` | 计划审查结构化字段 |
| `failure_classification` | LLM-assisted classification proposal |
| `template_candidate` | calibration candidate |

### 6.3 Control block 与 artifact 的关系

`dual-control` 只表达节点状态：

```text
decision: ACCEPT
summary: one sentence
```

structured artifact 表达节点事实：

```json
{
  "decision": "ACCEPT",
  "evidence": [],
  "missing_evidence": []
}
```

校验规则：

- `dual-control.decision` 必须合法。
- artifact 中的 `decision` 必须合法。
- 如果两者都存在，二者必须一致。
- 如果节点要求 artifact，缺失 artifact 失败。

### 6.4 Prompt contract 注入

Prompt 不再手写：

```text
Decision must be one of:
- ACCEPT
- ACCEPT_WITH_NOTES
- REJECT
```

改为模板变量：

```text
Allowed decisions:
{{allowed_decisions}}
```

渲染时由 `ContractRegistry.forNode(nodeId)` 注入。

这样新增或修改状态时，Java contract 是唯一来源。

## 7. 五个风险点的改造方案

### 7.1 Prompt contract 单一来源

改动：

- `AgentPromptRenderer` 增加 `allowed_decisions`、`required_control_keys`、`artifact_contract` 变量。
- prompt 模板使用变量。
- prompt regression test 校验模板不再包含硬编码 decision list。

验收：

- `ContractRegistry` 是 allowed decision 的唯一代码来源。
- prompt 渲染输出中的 allowed decisions 与 registry 一致。

### 7.2 Agent evidence structured artifact

改动：

- 新增 artifact records：
  - `SelfTestReportArtifact`
  - `AcceptanceReviewArtifact`
  - `StageHandoffArtifact`
- 新增 validator：
  - `AgentStructuredArtifactValidator`
- `ArtifactContractValidator` 读取 structured artifact 作为机器事实来源。
- Markdown section extraction 不再参与主事实；如保留 legacy fallback，必须由显式兼容开关启用，strict mode 默认关闭。

验收：

- Agent 缺少 required artifact 时节点失败。
- Agent artifact invalid 时节点失败。
- final report 读取 sidecar structured fields，而不是 Markdown section aliases。

### 7.3 Contract compile 去启发式 fallback

改动：

- `ContractCompileHandler` 不再调用 `compile(sourceText, workstreamId)` 作为语义 fallback。
- 缺少 `task_contract_proposal` 时返回 `TASK_CONTRACT_PROPOSAL_MISSING`。
- mechanical validation failure 保持失败，不生成 canonical contract。
- 可以保留一个显式 legacy flag 供旧 smoke 临时使用，但默认关闭。

验收：

- 没有 proposal 时不会生成 `task-contract.json`。
- invalid proposal 时不会生成 canonical TaskContract。
- failure summary 能指出缺失 proposal。

### 7.4 Failure classification 字段化

改动：

- `AcceptanceReviewArtifact.failure` 成为 FailureArbiter 输入。
- `FailureArbiter` 主分类只看：
  - `contract_gaps`
  - `out_of_scope_files`
  - `missing_tests`
  - `category_hint`
  - `changed_file_count`
  - `review_decision`
  - TaskContract mechanical indicators
- `reviewSummary` 只写入 diagnostics，不作为分类规则。

验收：

- summary 中出现 `unclear` 不会改变分类。
- structured `contract_gaps` 非空稳定分类为 `CONTRACT_GAP`。
- structured `category_hint` 只能作为低优先级 hint，不能覆盖强字段。

### 7.5 Human review guidance 字段化

改动：

- `HumanReviewGateHandler` 从 latest sidecar / review packet JSON 读取：
  - `decision`
  - `blocking`
  - `must_fix`
  - `missing_evidence`
  - `open_risks`
  - `recommended_human_action`（可选）
- 删除 decision string `contains()` 推断。
- `operatorGuidance` 明确标注依据字段。

验收：

- guidance 推荐 revise 只由 structured blocking fields 触发。
- Markdown 中出现 “Must Fix” 字样但 artifact 为空，不触发机器推荐。
- UI / CLI overview 与 review packet 使用同一份结构化字段。

## 8. 错误处理

新增或统一错误码：

| Code | 含义 |
|------|------|
| `STRUCTURED_ARTIFACT_MISSING` | 节点要求 artifact 但未输出 |
| `STRUCTURED_ARTIFACT_PARSE_FAILED` | JSON 解析失败 |
| `STRUCTURED_ARTIFACT_CONTRACT_VIOLATION` | schema / enum / required field 失败 |
| `CONTROL_ARTIFACT_DECISION_MISMATCH` | `dual-control` 与 artifact decision 不一致 |
| `TASK_CONTRACT_PROPOSAL_MISSING` | contract.compile 缺少 proposal |
| `TASK_CONTRACT_PROPOSAL_INVALID` | proposal 机械校验失败 |

这些错误进入现有 recovery summary，不自动 fallback。

## 9. 实现顺序

### P0：状态与 prompt 单一来源

1. Renderer 注入 allowed decisions。
2. prompt 模板去硬编码 decision list。
3. prompt regression 覆盖 registry 与渲染一致性。

### P1：structured artifact validator

1. 实现通用 envelope parse + artifact type dispatch。
2. self-test / acceptance / handoff 支持 structured artifact。
3. `ArtifactContractValidator` 改为 artifact first strict mode。

### P2：contract.compile strict

1. 移除默认 Markdown semantic fallback。
2. 缺少 proposal 直接失败。
3. 更新 smoke / prompt，确保 intake 必产 proposal。

### P3：failure arbiter 字段化

1. acceptance artifact 提供 failure fields。
2. FailureArbiter 不再用 summary keyword 做主规则。
3. 更新 classification tests。

### P4：human guidance 字段化

1. review packet 读 structured fields。
2. 删除 decision contains / Markdown section guidance。
3. 更新 CLI / API / operator panel tests。

## 10. 测试策略

- Unit tests：
  - envelope parser。
  - artifact validator。
  - decision mismatch。
  - missing artifact strict failure。
  - contract compile no fallback。
  - failure arbiter ignores summary keywords。
  - human guidance ignores Markdown-only Must Fix。

- Integration tests：
  - fake ledger + fake agent output。
  - workflow can stop at artifact contract failure。
  - retry after corrected artifact can continue。

- Real-case validation：
  - 用 `cancer-clinic` 小任务跑 full variant。
  - 关注是否还有因为 structured artifact 缺失导致的 prompt 问题。
  - 如果失败，优先修 prompt / artifact schema，而不是加 Markdown fallback。

## 11. 决策

本设计采用 strict mode：

> 如果 Agent 没有输出合法 structured artifact，节点失败。系统不从 Markdown 猜机器事实。

原因：

- harness 的价值就是规范 LLM 行为。
- silent fallback 会掩盖 prompt、schema 或职责边界问题。
- 早失败比带着错误 contract / evidence 继续跑更安全。

这会让短期真实 case 更容易暴露 prompt 问题，但长期能防止代码腐化。

## 12. 成功标准

完成后应满足：

1. Prompt allowed decisions 来自 `ContractRegistry`。
2. self-test / acceptance / handoff 的机器事实来自 structured artifact。
3. 缺少合法 artifact 会失败并生成可读 recovery summary。
4. contract.compile 默认不再从 Markdown 猜 canonical TaskContract。
5. FailureArbiter 不再靠 review summary keyword 做主分类。
6. Human review guidance 不再靠 Markdown section / decision contains 推断。
7. Markdown 仍保留给人读，不承担机器事实来源。
8. 真实 full lifecycle case 可以在 strict mode 下跑通，或者明确暴露 prompt/schema 问题。
