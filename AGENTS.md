# Agent Working Principles

## Decision Principle

做设计和决策时必须权衡长期价值和短期成本。很多功能都可以做，但开工前必须判断它是否是通往最终目标的必要工作；如果拿不准，先向用户确认，不要擅自开工。

## Robustness Principle

遇到流程语义冲突、状态边界模糊、Agent 输出不稳定、artifact contract 漂移、human gate 语义不清、recovery 分类缺口、UI/CLI/ledger 状态不一致等问题时，不要先给局部 if-else 补丁方案。

必须优先从鲁棒性、完整性、职责边界和状态模型角度重新审视设计；只有确认是局部实现缺陷时，才做小范围修复。

## Mandatory Checklist

修改 workflow、orchestrator、agent lifecycle、artifact contract、human gate、recovery、delivery lifecycle、UI/CLI/ledger projection 相关代码前，必须先阅读并遵守：

- `dual-orchestrator/docs/engineering-checklist.md`

核心要求：

- 先定义领域语义，再改代码路径。
- 状态只由 authoritative ledger / domain object 产生，UI/CLI 只能投影。
- LLM 输出必须进入结构化 artifact contract，orchestrator 不直接猜自然语言正文。
- 新规则优先放入语义类、catalog 或 contract validator，不散落在 handler、CLI、UI 中。
- completion/recovery/human gate 这类边界状态必须有测试覆盖。
