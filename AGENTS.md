# Agent Working Principles

## Decision Principle

做设计和决策时必须权衡长期价值和短期成本。很多功能都可以做，但开工前必须判断它是否是通往最终目标的必要工作；如果拿不准，先向用户确认，不要擅自开工。

## Robustness Principle

遇到流程语义冲突、状态边界模糊、Agent 输出不稳定、artifact contract 漂移、human gate 语义不清、recovery 分类缺口、UI/CLI/ledger 状态不一致等问题时，不要先给局部 if-else 补丁方案。

必须优先从鲁棒性、完整性、职责边界和状态模型角度重新审视设计；只有确认是局部实现缺陷时，才做小范围修复。

## Technical Debt Principle

原则上不保留技术债务。只有在非常有必要时才允许记录技术债，例如未来方向存在真实不确定性、当前修复会明显干扰主流程、或修复成本和当前收益严重不匹配。

如果技术债已经影响主流程稳定性、职责边界、状态一致性、artifact contract、测试可信度或后续开发速度，必须优先修复，而不是写入 TODO 后继续叠功能。

## Objective Preservation Principle

实现或优化过程中不得丢失本轮主目标。开工前必须把当前目标写成可验证口径，例如 token、成本、延迟、质量、闭环状态、边界拒绝能力或 UI 行为；收口时必须逐项验证。

如果目标涉及可观测性或成本归因，不允许因为 provider/model 缺失、价格未知、cache 命中难解释或包月计费不可折算金额，就降级为“不可算”。至少必须保留明确状态：真实 token、provider/model、billing mode、是否可计算金额、是否仅 token-only / subscription-unpriced / pricing-unknown。

发现实现偏离主目标时，必须先修复目标链路，再继续后续优化；不能用新增功能掩盖验收口径缺失。

## Sibling Boundary Sweep

当一次修改迁移、删除或集中某个 source of truth 时，必须同步检查同一边界下的兄弟概念，不能只修用户点名的一个方法、一个 variant、一个 UI 面板或一个测试。

例如：清理一个 workflow variant 的 Java factory 生成路径时，必须同时检查所有内置 variant、loader、CLI 参数、smoke case、文档和测试是否还存在旧定义来源。若兼容路径没有明确长期价值，就删除；不要因为“还能用”而保留，避免后续开发漂移。

## Mandatory Checklist

修改 workflow、orchestrator、agent lifecycle、artifact contract、human gate、recovery、delivery lifecycle、UI/CLI/ledger projection 相关代码前，必须先阅读并遵守：

- `dual-orchestrator/docs/engineering-checklist.md`

如果改动涉及 workflow definition、handler registry、contract registry、failure signal catalog、gate action、recovery action 或 projection read model，还必须对照：

- `dual-orchestrator/docs/design/boundary-drift-review.zh-CN.md`

如果问题表现为状态边界不清、多个同类 evidence 看起来一样、current/history 语义混在一起、UI/CLI/API/ledger 不一致、recovery/resume 行为异常、或又想通过局部 if-else 修补，必须使用本地 skill：

- `boundary-first-engineering`

核心要求：

- 先定义领域语义，再改代码路径。
- 状态只由 authoritative ledger / domain object 产生，UI/CLI 只能投影。
- LLM 输出必须进入结构化 artifact contract，orchestrator 不直接猜自然语言正文。
- 新规则优先放入语义类、catalog 或 contract validator，不散落在 handler、CLI、UI 中。
- completion/recovery/human gate 这类边界状态必须有测试覆盖。
- 技术债默认要修复；只有说明必要性、边界、风险和重新评估条件后，才允许保留。
