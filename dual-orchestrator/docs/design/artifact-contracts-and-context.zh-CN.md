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

## 核心 Artifact

| Artifact | 生产者 | 作用 |
|---|---|---|
| `task_contract_proposal` | Claude intake | 提出范围、非目标、约束、验收标准和测试要求。 |
| `task-contract.json` | Orchestrator | 机械校验和人工 resolution 后的 canonical contract。 |
| `contract-resolution.json` | Human gate / orchestrator | 记录开放问题或范围冲突如何被解决。 |
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

Validator 不判断产品质量。产品质量由 Codex 和 human 判断。Orchestrator 只判断 artifact 是否安全到足以驱动路由。

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

## 失败模式

必需 artifact 非法时，节点以 `AGENT_OUTPUT_CONTRACT_VIOLATION` 失败。运行时写入诊断：

```text
.agent/workstreams/<workstream_id>/artifacts/contract-failures/
```

诊断包含源输出、contract shape、解析错误、建议 owner 和 recovery hint。CLI、API 和 operator overview 都消费同一份诊断数据。

## 当前取舍

系统对机器事实保持严格，但仍允许 Agent 写适合人读的 Markdown。这是关键平衡：harness 应该约束状态迁移和证据边界，而不是把所有人类解释都压平成僵硬表单。
