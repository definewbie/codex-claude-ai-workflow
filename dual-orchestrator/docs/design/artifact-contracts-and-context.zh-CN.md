# Artifact Contract 与上下文装配

这份文档说明 dual-orchestrator 当前的契约与上下文模型。它替代早期按日期或内部阶段编号组织的迭代记录。

## 问题

长周期 Agent 工作流常见两类失败：

- 下一个 Agent 拿到的上下文太少，只能猜；
- orchestrator 把自然语言 Markdown 当成可靠的机器状态来读。

Markdown 对人有用，但不适合作为工作流路由边界。运行时需要稳定事实：任务范围、验收标准、自测证据、最终验收决策、交接状态和已知缺口。

## 设计原则

Agent 输出分成两层：

| 层 | 面向谁 | 运行时作用 |
|---|---|---|
| Markdown report | 人类 reviewer | 解释推理、取舍和证据叙述。 |
| Typed artifact | Orchestrator 和下游 Agent | 提供可校验、可路由、可装配上下文的机器事实。 |

Orchestrator 不从 Markdown 标题、关键词或摘要里推断关键状态。某个节点需要结构化 artifact 时，缺失或非法 artifact 会让节点失败，并生成契约诊断。

## 输出捕获边界

acpx stdout 是执行证据，不是 workflow artifact。一个 acpx stream 里可能同时
包含 client frame、tool call、tool input/output、provider 消息、skill 文本，
以及最终 Agent 回答。因此 adapter 对完成的 Agent execution 写两类文件：

| 文件 | 含义 | 是否可以进入后续 context |
|---|---|---|
| `outputs/<prompt>.transcript.log` | 原始 acpx 执行 transcript，用于调试和 failure classification。 | 不可以。 |
| `outputs/<prompt>.md` | 抽取后的最终业务 artifact / terminal control block。 | 可以。 |

完成节点的 `NodeResult.outputRefs` 指向最终 `outputs/<prompt>.md`，不指向
transcript。失败节点可以把 transcript 和 stderr 作为 recovery evidence 暴露。
Context renderer 即使遇到旧数据或手工 inputRefs 指向 `*.transcript.log`，也只
显示指针，不展开内容。

这是当前最重要的 token-control 边界：工具日志和文件读取仍然可追溯，但不会变成
下一个 Agent 的 prompt。

## 核心 Artifact

| Artifact | 生产者 | 作用 |
|---|---|---|
| `task_contract_proposal` | Claude intake | 提出范围、非目标、约束、验收标准和测试要求。 |
| `task-contract.json` | Orchestrator | 机械校验和人工 resolution 后的 canonical contract。 |
| `contract-resolution.json` | Human gate / orchestrator | 记录开放问题或范围冲突如何被解决。 |
| `agent_output_contract` | Claude / Codex 中间阶段 | 记录 design、plan、implementation、review 路由所需的阶段决策、摘要、证据、缺失证据、风险和 failure 字段。 |
| `self_test_report` | Claude self-test | 记录执行了哪些检查、跳过哪些检查、证据、缺失证据和残余风险。 |
| `acceptance_review` | Codex verifier | 记录最终决策、阻塞缺口、must-fix、acceptance notes 和置信度。 |
| `stage_handoff` | Claude handoff | 记录已完成工作、关键决策、变更文件、开放风险和推荐下一阶段。 |

## Contract 生命周期

1. 用户需求从自然语言开始。
2. Claude 输出 `task_contract_proposal`。
3. Orchestrator 校验结构和可执行边界。
4. Contract review gate 解决开放问题，或要求修订。
5. Orchestrator 写入 canonical `task-contract.json` / `.md`。
6. 后续阶段把 canonical contract 当作可信 harness fact 使用。

Canonical contract 不从 Codex review 复制。Codex 可以引用、质疑或批注 proposal，但 proposal 来源仍然是 Claude intake 加 orchestrator 校验。

## 校验边界

Contract validation 故意保持机械化：

- 必填字段存在；
- included / excluded scope 不冲突；
- acceptance criteria 具体到可以验证；
- test requirements 存在；
- ledger 或 context metadata 不会被误认为任务范围；
- status 和 decision 字段内部一致。
- `READY` 表示 artifact 可以安全交给下一个 workflow 角色继续判断。它不能携带 `blocking=true` 或 `must_fix` 这类明确阻塞决策证据，但可以携带 `missing_evidence`，由下游 verifier 判断这个缺口是否可接受；
- 最终 `ACCEPTED` 表示 verifier 没有发现实质性证据缺口，因此不能携带 `blocking=true`、`must_fix` 或实质性 `missing_evidence`；
- `ACCEPTED_WITH_NOTES` 可以携带非阻塞的缺失证据或残余说明，但不能携带 `blocking=true` 或 `must_fix`；
- `NEEDS_REVISION`、`BLOCKED`、`REJECTED` 这类负向状态必须携带至少一条明确的阻塞原因、缺失证据、must-fix 或 failure reason。

Validator 不判断产品质量。产品质量由 Codex 和 human 判断。Orchestrator 只判断 artifact 是否安全到足以驱动路由。

空证据必须用空字段或空数组表达。`must_fix` 或 `missing_evidence` 里出现
“none”、“no blocking issue”、“N/A” 这类占位文案，不会被系统归一化为空；
它们会被当成真实条目。`must_fix` 会与正向路由状态冲突；`missing_evidence`
则作为面向 verifier 的证据限制继续传递，除非该 artifact 正在声明最终
`ACCEPTED`。

## Stage Outcome

结构化 Agent artifact 可以包含 Agent 自己声明的 `lifecycle_status`，但这个
字段本身不是运行时 source of truth。Validator 会把 artifact 交给 stage
outcome compiler：

```text
Agent decision + declared lifecycle_status
  -> StageOutcomeCompiler
  -> canonical stage_outcome + stage_outcome_source
```

编译后的 `stage_outcome` 是 contract sidecar 里的 canonical 语义状态。后续
failure classification、context package、review packet、report、CLI 和 UI
projection 如果需要阶段语义，应消费 `stage_outcome`。它们不能重新从 Markdown
正文或原始 `lifecycle_status` 推断生命周期含义。

Workflow definition 会声明结构化节点期望哪种 artifact：

```json
{
  "expected_artifact": "acceptance_review",
  "artifact_compiler": "acceptance_review_compiler",
  "allowed_stage_outcomes": [
    "ACCEPTED",
    "ACCEPTED_WITH_NOTES",
    "REJECTED",
    "INSUFFICIENT_EVIDENCE",
    "BLOCKED"
  ]
}
```

这样 artifact 边界是明确的：workflow JSON 声明期望 artifact，validator
校验形状和语义一致性，compiler 产出 canonical outcome。Handler 局部的字符串
规则不能变成第二套 stage model。

## 决策路由边界

Decision node 只消费 inputRefs 里的最新 `*.contract.json` sidecar。它不解析
Markdown，也不会回退到终端 `dual-control` block 来决定路由。`dual-control`
仍然是 artifact validation 中的终端输出一致性检查，但不是 runtime decision
source。

如果 decision node 找不到 sidecar，会以 `MISSING_CONTRACT_SIDECAR` 失败。
如果当前 sidecar 存在但格式错误或缺少 decision，会以
`INVALID_CONTRACT_SIDECAR` 失败；runtime 不能继续向前搜索旧 artifact，找一个
“能用”的答案。最新 sidecar 才是当前 typed fact。

## Report 投影边界

Final report 和 operator projection 应该总结结构化 sidecar，例如
`implement-claude-self-test.contract.json` 和
`acceptance-codex-review.contract.json`。原始 Agent Markdown 仍然作为 artifact
指针可追溯，但 report generation 不应该把 tool transcript、raw stdout 或未经
校验的自然语言片段嵌入成验收事实。

## Context Package

每个 Agent step 前，运行时会为目标角色生成 context package。Context package 有 profile 和 token budget：

| Profile | 典型消费者 |
|---|---|
| product review | Codex intake/product review |
| architecture review | Codex design review |
| plan review | Codex plan review |
| implementation | Claude implementation |
| acceptance | Codex final verifier |
| handoff | Claude next-stage handoff |

每个 package 会区分：

- 可信 harness facts，例如 TaskContract 和 Git evidence；
- human decisions，例如 gate approval 和 revision；
- Agent proposed states，例如 self-test / acceptance sidecar；
- pending gaps，例如 contract diagnostics 或 missing evidence。

这样长上下文仍然可用，但旧 Markdown、复制示例、被引用的 Agent 文本不会变成 workflow truth。

每次生成 context package 都有两层：

```text
.agent/workstreams/<workstream_id>/context/<node_instance_id>.md
.agent/workstreams/<workstream_id>/context/<node_instance_id>.json
```

Markdown package 面向 Agent。JSON sidecar 面向 harness 和投影层，记录：

- profile 和 target node；
- 估算 token 数和 token budget；
- 渲染后的 package 是否仍在 budget 内；
- 每个 section 是否可用、是否降级、是否被丢弃、soft budget 和渲染后 token 数。

Trace、API overview 和 Operator Panel 如果要展示 budget evidence，应该读 JSON sidecar，不能把 Markdown governance table 当成 source of truth。

Agent run 的 artifact metadata sidecar 也会记录 prompt、最终 output、
transcript、stderr 的 byte size 和本地估算 token 数。Provider usage 如果可用，
仍然单独记录；本地估算用于比较 transcript 和最终 artifact 的大小，及时发现
上下文膨胀回归。

## 失败模式

必需 artifact 非法时，节点以 `AGENT_OUTPUT_CONTRACT_VIOLATION` 失败。运行时写入诊断：

```text
.agent/workstreams/<workstream_id>/artifacts/contract-failures/
```

诊断包含源输出、contract shape、解析错误、建议 owner 和 recovery hint。CLI、API 和 operator overview 都消费同一份诊断数据。

## 当前取舍

系统对机器事实保持严格，但仍允许 Agent 写适合人读的 Markdown。这是关键平衡：harness 应该约束状态迁移和证据边界，而不是把所有人类解释都压平成僵硬表单。
