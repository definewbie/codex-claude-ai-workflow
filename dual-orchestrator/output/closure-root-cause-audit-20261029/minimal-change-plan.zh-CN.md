# 闭环审计论断复核与最小改动方案

2026-11-01：当前核定以[最终结论与总验收表](final-verdict-and-acceptance.zh-CN.md)为准；本文件保留此前方案及工作量估计，不将其解释为尚需从零建设全部机制，也不将既有批次 PASS 外推为五步已闭环。

2026-10-29。范围：用户提出的 12 个旧 OKX case / 158 run，及当前工作区。以下为方案，不是已经实施的改动；本轮没有启动 provider 或改动生产代码。原始逐项复核见 [claim-check.json](claim-check.json)。沿用 boundary-first-engineering：先明确执行事实与恢复权，再决定是否可以自动执行。

## 必须修正的前提

1. **18 次不能成功，不是 20 次失败。** 138 SUCCESS，2 SUCCESS_WITH_WARNINGS 且 nodeMaySucceed=true，12 OUTPUT_INCOMPLETE，4 RECOVERABLE_ADAPTER_FAILURE，1 TIMEOUT，1 CONTRACT_FAILED。节点允许成功比例为 140/158；这仍不是一次通过率，因为包含重试和语义返工，且节点类型与版本不一致。
2. **存在恢复与格式纠正。** 原 ledger 有 14 条 node_retry_requested，其中 9BZG 有 10 条。9BZG 的 implement 12 run 分布是 7 次结果缺失/无效、2 次 session bootstrap failure、3 次成功。最后一次 retry 事件为 2026-09-03T12:13:37.490674Z；随后 run-1788437649906 于 12:14:14 启动并成功。全样本还有 27 个 run 记录 correctionAttempted=true，其中 25 个最终 nodeMaySucceed=true。不能将“外层自动 retry 为零”写成“没有任何自动补救机制”。
3. **最贵的运行已使用 run session。** 65 个 workstream session run 对应 5,750,964 已记账 token；93 个 run session run 对应 60,490,013。后者占总量 91.32%，包含 54,323,712 cachedRead。BXWV 的 run-1789001311367 独立 session 名含 run ID，但 cachedRead 仍为 6,294,656。把全部缓存归因跨节点全历史累积，被这些记录直接反驳；单 run 内多次工具交互和跨请求前缀缓存还需分开归因。
4. **USD 97.517015 是 raw costAmount 求和，不能当核销账单。** 包含订阅 token-only、缺 provider/billing 和可疑 model 归属；零交付时单位成本未定义。66.24M 是既定过滤规则下的账本 usage，不是完全核销的真实总消耗。
5. **9BZG 的 605 秒 case 未证明只是最终格式错误。** run-1788009030832 的 command 包含 --timeout 600，process 记录 exitCode=0/timedOut=false，但 acquisition 为 SKIPPED_NOT_COMPLETED，transcript 最后停在 thinking。这是外层退出成功与 ACP 完成不一致的执行边界问题，不能从 exit 0 推导任务已经完成。其它 missing-result 运行有 201/221ms 的快速失败，不能全按“做了十分钟而漏记”归因。
6. **现有 policy 缺口真实，但不能倒推历史归因。** 当前 AgentExecutionOutcomeResolver 的这些失败分支统一 MANUAL_ONLY；WorkflowRuntime 的自动 retry 要求 AUTO_ALLOWED，因此不能只改 maxRetries。另一方面，当前未提交的 L4 规则不能据此被断言为八九月全部失败的原因；需当时生效版本、合法重试前提和副作用证据。
7. **串联公式没有证明必败或 85% 改善。** 87.3% 混合了不同节点、版本、同 case 相关事件和补救后的结果。设每段独立同分布、链固定 10 段才能得到约 26%；26% 本身也不是数学必败。每节点 2 次额外重试在同样独立假设下甚至给出约 98% 的整链数字，而非实测收益。两种数字都不应成为实施承诺。

“模型是否收到过约束”不是正确的证伪标准。模型收到约束仍可能出错；该事实既不能证明应放宽验证，也不能证明控制面设计没有问题。应验证：在同一合法业务事实下，去掉机器事实转写是否减少失败，同时所有身份/权限/终态负例仍拒绝。

## 修正后的主目标

P0：已经完成但呈现格式错误的输出可以有界纠正；执行不确定性不得冒充格式失败；合法补救不得重跑已发生的产品副作用。P0.5：每个纠正/重试请求都有准确 token 归属、统一限额、明确 billing 状态。

原始 IO、固定执行记录及 ledger 是 SoT；run.json 中历史 recoverable/retrySafe 不能单独覆盖 typed outcome 和 external-effect 事实。保留现有 Definition / Instance / Execution；不再引入并列恢复状态机。

## 第一步 收敛结果采集与结构化收尾

**做什么：改已有 acquisition，不新造一套收尾 agent。**

- 保留 AcpxAgentResultAcquirer 现有 presentation 解析和 correction；将 `ACP_COMPLETED + 确认可纠正呈现错误` 与 `ACP 未完成/调用仍活动/外部结果不确定` 分开。
- 程序拥有的 baseline SHA、固定 method、scope 引用从 prepared binding/登记对象解析；不要求模型重新复制长字符串。MBC2 的 revision SHA 已这样修复，重点是剩余 readiness method 等兄弟路径。
- correction 只输出候选结构化产物，沿既有 deny-all 权限执行；不重新运行 implement，不允许补写批准、改身份/范围或根据模型正文制造测试通过。
- 保留原候选、纠正候选、request ID、各自 usage 与诊断；原材料不被覆盖为“从来没有失败”。持久化完成后不能回头改原 execution 的结论。

**预计改动量：** 4–7 个生产类/契约点，扩充约 8–12 个测试场景；涉及 AcpxAgentResultAcquirer、AgentResultEnvelope/Parser、AgentResultPromptContract、VerificationReadiness、必要的 compiler/projector。估计需在实现设计时收紧，不能承诺只改数十行。

**转移与写序：** 已证明 ACP 完成且当前 execution 未固化 completion → 记录候选/错误 → 预留本节点纠正额度 → 写纠正 command intent → deny-all 调用 → 保存 raw IO/usage → typed 验证 → 原 commit protocol 发布/固化/消费。intent 后中断而无可信结果：进入现有 UNKNOWN 判断，不能再自动发送。已有固定 outcome/completion 时，只重放已有事实或走显式新恢复执行，不能重开旧 run。

**不可绕过验证：** 原业务结果合法但呈现损坏能修复；相同材料的错 workstream/run/baseline/epoch、伪造批准、未完成 ACP、权限失败都不能被纠正成成功；纠正阶段零产品文件写入；在每个 durable-write 点注入中断，冷启动不重复发送。历史 payload 经当前契约重放时，旧 schema 可以明确 INVALID，不能为救样本静默兼容。

## 第二步 把安全补救策略接到当前 outcome 与 runtime

**做什么：按已证明的失败切面开放 AUTO_ALLOWED，不能统一替换 MANUAL_ONLY。**

- `OUTPUT_INCOMPLETE` 目前是终端信号桶，包含呈现错误、未完成运行、provider 前拒绝等不同根因。使用现有 typed evidence 补足可机判的细分原因，再决定允许的操作是解析、只补信封、重放固定结果还是新 execution。
- 已完成且无权限/身份问题的呈现错误：优先第一步的有限纠正；不自动重新实现。
- 可证明 provider 未启动且外部副作用未发生的临时失败：经过当前批准、claim、输入、预算和 terminal guard 复核后才允许新合法执行。未知参数/不兼容 binary 不是盲重试能解决的临时失败。
- session/网络中断：只有能证明重复执行安全或存在已实现的幂等/续接语义才允许重试；UNKNOWN、权限、SHA、scope、契约身份失败继续人工处理。
- 默认每节点合计 2 次补救，deny-all correction 也计入；重启、内外两层 retry 和 correction 不得各自获得新额度。沿现有 durable execution/occurrence 与 node retry 事实计数，投影不能重置预算。
- 三 case 同根因升级必须用稳定 typed cause，不能按报错字符串计数；没有完整原因身份前，不宣称该规则已 ENFORCED。

**预计改动量：** 5–8 个生产类，约 12–18 个序列/参数化测试场景；重点为 AgentExecutionOutcomeResolver、现有 evidence/失败 catalog、RetryPolicyResolver/RetryStrategies、WorkflowRuntime、AgentWorker 与 ledger 计数/记录。第一步与第二步有共享修改，文件数不可直接相加。

**转移与写序：** 固定失败事实 → 核验 typed cause 与 effect disposition → 原子记入 retry 额度/新 execution claim → 再校验批准/预算/terminal → prepared/intent → 外部调用。若额度已占用但尚无 dispatch，重启按既有事实恢复；若已有 intent，不能因为计数“还没满”就重复调用。耗尽额度记录最终失败与升级，不再开隐式循环。

**不可绕过验证：** 前两次合法补救可执行、第三次零调用；一次 correction 加一次 retry 已耗尽；多 worker/CLI 竞争只消费一次；进程重启不清零；UNKNOWN、过期批准、终态始终零调用。真实外部证据缺失不进入代码修复，真正实现缺陷仍进入 semantic repair。包括 source mutation、失败写序与冷启动切面，不能只测 resolver 返回 AUTO_ALLOWED。

## 第三步 保留 run session 基线，先定位单 run 的消耗放大

**不实施“session 全降级即可省 89%”这一方案。** 当前默认与最贵的历史 Claude run 已经隔离；再改 session 范围不能解释 60.49M token。

- 先按 `(case, node occurrence, provider session, request)` 分别统计输入/输出/cache、工具轮次、同一工具结果再次读取、correction 与实际代码差量。区分 token 高但有产物进展、缺证反复验证、重复读取和合法缓存命中。
- 保存 provider/model/billing 的可信来源，缺失明确为未知；不能将 `model=token/in` 当可靠模型标签，更不能用订阅的 raw costAmount 当账单。
- 将现有 case runner 的 admission/stop 检查接入唯一 dispatch 与 correction 入口，读同一预算事实；有界调用时长/工具轮次按实际 adapter 能力使用。没有流式 token enforcement 时，明确 reservation 与残余超额风险。
- 节点间仅传摘要/选择性上下文属于进一步策略实验，必须先证明没有丢失 acceptance/scope/evidence；当前 P1/P2/P3 锁未解除，不在本轮默认实施。

**预计改动量：** 计量对账 2–4 个生产类/采集点与约 6–10 个场景；如果确认预算跨入口确有缺口，再集中 3–5 个已有 dispatch/ledger 点。session policy 生产改动为 0，先保护现有 run 隔离不回退。

**验证：** request usage 不重复、不漏算 correction；session snapshot 不能当增量；错 model/订阅计价不得产出可信现金成本；直接 CLI/worker/recovery 均不能绕过预算。先对固定历史输入做离线重放，之后才在已批准 Verification Gate 下做同模型同任务成对实验。指标是每个外部 grader 接受并真实交付的成本，不是 output token 数或 cachedRead 比例。

## 取舍与验收

本方案优先复用结果采集、typed outcome、执行记录与 dispatch 边界，删除重复的控制事实转写，不新增通用工作流平台。未知副作用的自动重放风险高于减少一次人工操作的收益；相反，让已完成的纯呈现错误整段重跑，长期成本高于一次受限的结构纠正。

Boundary Review：WARN，方案待实现。第一、二步触及状态/权限/契约边界，属于 BOUNDARY，实施后须按项目要求做全模块离线回归、独立评审和相应用户批准；本轮的方案产物不构成真实 provider 验证授权。

最终 PASS 必须同时证明：格式补救确实减少因控制转写而失败；产物副作用不重复；危险负例全拒绝；补救总额度不可绕过；外部 grader 产品质量不退化；每交付 token 的改善来自有效成对样本。没有这些证据，不承诺“成功率 85%+”或“66M→8M”。
