# Agent Working Principles

## Decision Principle

做设计和决策时必须权衡长期价值和短期成本。很多功能都可以做，但开工前必须判断它是否是通往最终目标的必要工作；如果拿不准，先向用户确认，不要擅自开工。

## Agent Delegation Cost Discipline

默认由主 agent 完成设计、实现、测试和自查；不得把“继续”“进入下一步”解释为启动或扩充多 agent 委派的授权。完整链路评估是质量要求，不等于多人重复读取和评估同一条链路。

启动独立 agent 前，必须先说明其不可替代的独立检查目标、与主任务不重叠的范围，以及预期收益是否超过重复上下文和协调成本。小修默认自行核验；跨层状态或授权边界确需独立评审时，默认最多一个只读 reviewer，不自动建立多人实现团队。

用户明确禁止额外 agent 时，不得新增、重启或向已有 agent 追加实质任务；只收回已有结果并停止继续委派，由主 agent 完成整合。不得以节省 token 为由降低必要的测试、完整 transition 与失败切面检查标准，也不得把自查冒充独立评审。

## Robustness Principle

遇到流程语义冲突、状态边界模糊、Agent 输出不稳定、artifact contract 漂移、human gate 语义不清、recovery 分类缺口、UI/CLI/ledger 状态不一致等问题时，不要先给局部 if-else 补丁方案。

必须优先从鲁棒性、完整性、职责边界和状态模型角度重新审视设计；只有确认是局部实现缺陷时，才做小范围修复。

## End-to-End State Transition Closure Principle

涉及 workflow、human gate、revision、retry、recovery、delivery 或其他多步状态转换时，不得把单个方法正确、字段存在、局部 diff 合理或单元测试绿色视为边界闭合。分析、设计、实现和评审必须沿真实生命周期从触发条件走到下一消费者、外部副作用和恢复出口；review agent 必须自行重建这条控制链，不能只核对实现说明列出的局部断言。

修改前必须明确并记录完整 transition table：

- authoritative pre-state 及其 source of truth；
- 触发 action、decision owner 和允许执行者；
- 每一次 durable write / event append / external side effect 的顺序；
- 成功后的 authoritative post-state，以及唯一消费它的下一组件；
- 在每个写入点之前、之间和之后失败时会留下什么状态；
- retry、resume、abort、replay 和进程重启分别如何解释该状态；
- 新旧 binary、旧 ledger 或旧 artifact 是否仍在支持范围，若不支持必须显式判为 INVALID，而不是静默兼容。

边界评审必须检查完整 end-to-end invariant：`Declared by`、`Proven by`、`Consumed by`、`Enforced before`、`Failure route`、`Cannot-bypass test`。缺少任一项时只能标记为未闭合，不得因局部测试通过而给出 PASS。测试至少覆盖正常序列、拒绝序列、关键 durable-write 间失败、冷启动重放，以及同边界 sibling；只验证当前函数返回值的测试不足以证明状态转换。

真实 case 发现问题后，必须先区分：

- root-cause forward fix：修复所有未来合法状态；
- backward recovery：处理仍受支持的历史状态；
- single-case rescue：只为挽救当前样本而增加的兼容解释。

不得把 single-case rescue 默认写入 production。只有该历史状态仍被正式支持、存在重复事故证据，且永久复杂度低于可观察损失时，才允许实施 backward recovery。若恢复代码接近或超过根因修复的复杂度，必须主动止损，比较“保留事故证据并启动 fresh case”“一次性离线迁移”“永久 production 兼容”三种选择；Agent 必须在实现前提出该判断，不能等待用户发现方向已经偏离。

同行评审 prompt 必须要求评审完整 transition 和失败切面，但不得预填期望结论或给出逐行答案，以保留独立发现问题的空间。

## Failure Severity Triage

任何失败必须先分类，再决定处置仪式级别。分类以失败的唯一 typed primaryReason 及其 authoritative evidence 为依据，不由失败是否被用户注意到、改动行数或模块数量决定。

- TRANSIENT：批准、身份与状态依据仍可信，能够在不改变既有 workflow definition、contract schema 或状态模型的前提下恢复，且重试不会重复不安全的外部副作用。典型：满足上述条件的模型输出格式噪声（JSON 前后多余文字、未知非关键字段、非关键 artifact 字段缺失）、会话/网络瞬断、仅影响观测且不参与预算止损或控制决策的计量脚本缺陷。
  处置：允许有界自动重试（默认每节点 2 次，deny-all 格式修正算 1 次）；不触发 boundary review，不冻结发行，不请求用户批准，不重跑全模块回归（跑该模块 targeted tests + lifecycle smoke 即可）。
  记录：写入 ledger transient-failures/，含 typed reason、重试次数、最终结果。记录即视为处置完毕。
  升级规则：同一 TRANSIENT 根因在 3 个不同 case 重复出现，或耗尽允许的重试次数后仍失败 → 升级为 BOUNDARY；升级后不能仅以已记录宣称处置完成。
- BOUNDARY：触及 source of truth、状态模型、contract schema、ledger 写序、权限边界的失败，包括批准/身份/SHA 校验或消费错误；即使仅修改局部条件、无需变更 schema，也不能降为 TRANSIENT。外部副作用是否已发生或重试是否安全无法确认时，停止自动重试，按 BOUNDARY 处置，不靠重试探测状态。
  处置：走原有全套仪式——transition table 重建、前向修复、全模块离线回归、重冻发行、CC 独立评审、用户批准。
- 禁止：把 TRANSIENT 当 BOUNDARY 处置（仪式膨胀），也禁止把 BOUNDARY 当 TRANSIENT 重试掩盖（纪律漂移）。

格式修正和重试仍须通过原有 typed artifact contract、身份/SHA、批准、scope、权限与 terminal guard。字段是否非关键由既有 contract 定义，agent 不得临时豁免必填字段或猜测批准事实。此处 ledger 记录是诊断证据，不成为第二状态来源；重试仅使用既有合法执行入口，不重开终态。

## Regression Scope Proportionality

修复后的回归与重冻范围必须与改动的爆炸半径成比例；BOUNDARY 档优先，不能因改动只在一个模块而降低边界验证要求：

- 单模块改动，且不属于 BOUNDARY、不触碰 workflow definition、contract schema/registry、source of truth、状态模型、ledger 写序、权限边界 → targeted tests（该模块+直接依赖）+ 一次 lifecycle smoke 即可重冻发行；作者自查 + 成对回归即可，不强制 CC 独立评审。
- BOUNDARY 级修复，或触碰上述流程、契约、状态、账本、权限边界 → 保留全模块离线回归 + CC 独立评审 + 用户批准。
- 纯文档记录（docs、测试记录、状态报告）不适用 Verification Gate，不触发重冻。

## Technical Debt Principle

技术债按影响分级处置：

- 第一类：触碰 source of truth、状态一致性、artifact contract、权限边界的债。仍然默认必须修复，不允许登记后延后。
- 第二类：仅影响观测、报告、文档措辞、投影文案，不影响控制流的债。登记后可延后，在下一次触及同模块的批次里顺带解决，不单开批次、不阻塞交付。
- 禁止：用"延后"处理第一类债；用"必须修"让第二类债阻塞交付或真实运行。

记录技术债时必须标注类别；未标注视为第一类。

## Acceptance Outcomes

验收节点的合法结果包括以下三种；它们不是 workstream 的三种终态：

- ACCEPTED：全部契约通过。
- ACCEPT_WITH_NOTES：主链产物有效，验收意见为非阻塞性备注（文档措辞、命名、非关键路径小缺陷）。这是允许进入交付的合法验收结果，notes 随 delivery merge 一并交付。流程层不得因"存在 notes"拒绝进入 delivery，也不得将其描述为"带病收口"。
- REJECTED：产物不满足验收标准，经既有合法转换进入 repair loop，不因此把 workstream 标为终态。

产品验收判定权在验收节点（Codex 验收 / human gate）；流程层消费经校验的 typed 结果，不另行凭 notes 改判。ACCEPTED / ACCEPT_WITH_NOTES 仍须经过既有交付资格、必要批准与状态提交，才能进入 workstream 终态。已经终态的 workstream 仍受 terminal guard 保护，不因本节而获得重开、retry 或 repair 权限。

## Standing Authorization Envelope

用户可一次性签发授权包：{预算上限，case 数上限，失败处置策略}。
包内每次新 case / Verification Gate 的启动不再单独请求批准，仍须通过既有 Verification Gate 的技术前置检查。该授权不自动代替需求、scope、产品验收或交付等业务 human gate 的决定；agent 代决必须有用户显式授予的范围，并经既有 action 归一化、批准和权限校验。

出现以下任一情况立即停止并回到人工：

- 预算累计消耗或 case 数达到上限；
- 出现 BOUNDARY 级失败；
- 单 case 内 TRANSIENT 失败超过上限；
- 任何需要扩大包条款的情况。

授权包条款只能由用户显式修改，agent 不得扩张解释。

## Objective Preservation Principle

实现或优化过程中不得丢失本轮主目标。开工前必须把当前目标写成可验证口径，例如 token、成本、延迟、质量、闭环状态、边界拒绝能力或 UI 行为；收口时必须逐项验证。

如果目标涉及可观测性或成本归因，不允许因为 provider/model 缺失、价格未知、cache 命中难解释或包月计费不可折算金额，就降级为“不可算”。至少必须保留明确状态：真实 token、provider/model、billing mode、是否可计算金额、是否仅 token-only / subscription-unpriced / pricing-unknown。

发现实现偏离主目标时，必须先修复目标链路，再继续后续优化；不能用新增功能掩盖验收口径缺失。

不得把“更容易通过测试的局部折中”替代为主线目标完成。例如：用 projection warning 替代 execution source of truth 收口、用 prompt 文案替代 artifact contract、用 token 观测替代 token 治理、用 synthetic fixture 替代已要求的 real-case proof、用兼容 fallback 替代删除漂移 source of truth。若某个方案只是 trade-off，必须先说明它为何仍然推进最终目标；否则停止实现，回到模型和边界分析。

每次进入 `mainline-stability-and-token-governance-plan.zh-CN.md` 相关开发前，必须先回答：

- 本次改动推进的是 P0 / P0.5 / P1 / P2 / P3 中哪一个主线目标？
- 它修复的是哪个 source of truth 或边界，而不是哪个表面症状？
- 它会删除、集中或强化哪个旧的漂移路径？
- 哪个测试或 replay 能证明该边界，而不是只证明当前实现细节？
- 哪些目标仍未完成，不能在收口报告里声称完成？

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
- 如果同一类 BOUNDARY 验证连续失败两次，必须回到模型边界分析，重新定义 trigger / owner / SoT / projection；不能继续调阈值或补 prompt。TRANSIENT 按 Failure Severity Triage 的重试与升级规则处置，不以本条提前触发全套仪式。

真实验证的结论必须区分三类：

- `PASS`: 目标字段已触发，质量与边界验收同时通过。
- `FAIL`: 目标字段未触发、边界不满足、质量退化或 stop-loss 触发。
- `INVALID`: 测试工件、触发前提、provider 配置或观测链路错误，导致本轮不能证明目标。

不得把 `INVALID` 说成“部分成功”；必须先修复验证链路。

## Sibling Boundary Sweep

当一次修改迁移、删除或集中某个 source of truth 时，必须同步检查同一边界下的兄弟概念，不能只修用户点名的一个方法、一个 variant、一个 UI 面板或一个测试。

例如：清理一个 workflow variant 的 Java factory 生成路径时，必须同时检查所有内置 variant、loader、CLI 参数、smoke case、文档和测试是否还存在旧定义来源。若兼容路径没有明确长期价值，就删除；不要因为“还能用”而保留，避免后续开发漂移。

## Mandatory Checklist

失败处置先遵守 Failure Severity Triage，修复验证遵守 Regression Scope Proportionality；满足条件的 TRANSIENT 不进入全套边界审查流程，BOUNDARY 保留 End-to-End State Transition Closure 的全部要求。以下清单与 skill 的适用范围服从此分级，不能用宽泛触发词重新扩大 TRANSIENT 的处置仪式；纯文档修改不触发 Verification Gate。

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
- 技术债按 Technical Debt Principle 标注并分级：第一类必须修复，第二类登记后搭车解决，不阻塞交付；未标注视为第一类。
