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

## Verification Boundary Discipline

真实 case、benchmark、A/B 实验或 provider 行为验证，必须先把“要证明什么”转成可执行的前置条件和中途止损条件，不能只把它写成事后观察项。

每次启动真实运行前，必须先写出并通过下面的 `Verification Gate`。没有这个 gate，或者 gate 中任一项无法回答，不允许启动真实 provider / real-case / benchmark / A/B：

```text
Verification Gate:
- Target:
- Source of truth:
- Trigger precondition:
- How precondition is checked before run:
- PASS:
- FAIL:
- INVALID:
- Stop-loss:
- Run artifact freshness:
```

字段含义：

- `Target`: 本次唯一主目标，必须是可验证事实，例如 `implement.claude_implement.sessionCompactionEnabled=true`，不是“看看效果”。
- `Source of truth`: 用哪个 typed artifact / run.json / metrics / ledger 字段判定结果。
- `Trigger precondition`: 启动前必须满足的触发条件，例如阈值、provider/model、billing mode、session state、前序 token。
- `How precondition is checked before run`: 启动前用什么命令或 read model 证明会触发。不能写“预计”“应该”。
- `PASS`: 成功必须同时满足哪些字段和质量条件。
- `FAIL`: 哪些字段或质量条件不满足时算失败。
- `INVALID`: 哪些情况说明本轮不能证明目标，例如旧 jar、旧 server、`measure_only`、`pricing_unknown`、字段缺失、触发条件未满足。
- `Stop-loss`: 何时必须中断，避免继续消耗 token 或污染判断。
- `Run artifact freshness`: 如何证明运行工件包含本轮代码，例如 build 成功、jar 时间、server 重启、worker 版本或脚本未使用 `--skip-build`。

例如要验证 `implement.claude_implement.sessionCompactionEnabled=true`、`seedKind=agent_handoff`、token 下降或质量不退化，启动前必须确认：

- 运行工件已经包含本轮代码，不能因为 `--skip-build`、旧 jar、旧 server 或旧 worker 导致复测无效。
- 触发条件在当前 case 中必然可达；如果依赖阈值、provider metadata、pricing、billing mode、session state 或历史 token，必须先用 ledger/read model 计算并确认会触发。不能靠“应该会触发”启动真实 provider 消耗。
- 验收字段必须在 run metadata / metrics / typed artifact 中有明确字段；如果字段为 `null`、`measure_only`、`pricing_unknown`、`not_applicable` 或 fallback reason，必须视为未验证，不得解释为成功。
- 中途一旦发现核心验收字段不满足，必须停止该 case 并报告根因；不能继续跑完整流程来弥补前置验证失败。
- 如果同一类验证连续失败两次，必须回到模型边界分析，重新定义 trigger / owner / SoT / projection；不能继续调阈值或补 prompt。

真实验证的结论必须区分三类：

- `PASS`: 目标字段已触发，质量与边界验收同时通过。
- `FAIL`: 目标字段未触发、边界不满足、质量退化或 stop-loss 触发。
- `INVALID`: 测试工件、触发前提、provider 配置或观测链路错误，导致本轮不能证明目标。

不得把 `INVALID` 说成“部分成功”；必须先修复验证链路。

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
