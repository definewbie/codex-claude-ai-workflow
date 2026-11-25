# dual orchestrator 闭环失败与 token 消耗根因审计

2026-11-01 定稿入口：[最终核定与五步总验收表](final-verdict-and-acceptance.zh-CN.md)。它收编本报告后续原始证据复核、消费义务、当前实现核对与 grader 基线；下文 2026-10-29 数据和测试保持其原日期及证明范围。新版本运行旧输入的既有切面检查不视为旧 binary 的历史行为复现。

审计日期：2026-10-29。结论适用于可恢复的历史证据及本地未提交工作区，不代表某个已发行版本的统计性能。审计只新增本目录的分析材料，未修改生产代码、纪律文件或历史 ledger，未调用真实 provider。

**核心判断：项目不是完全不能闭环，而是闭环能力尚不稳定，且为取得一个可交付结果付出了很高的重复执行成本。近期小任务主要暴露控制面输入的脆弱性；历史 OKX 大任务的主要 token 消耗在实现与自测循环。两者不能混为一个根因。**

当前最值得做的不是取消校验或继续增加纪律条款，而是让程序消费自己拥有的身份和验证方法事实，减少模型必须原样转写的控制字段；同时把已有预算判定接到唯一执行入口。身份、安全和真实产品错误的拒绝必须保留。

## 审计口径与证据范围

将原题中的“E2E 无法闭环”改为可证伪命题：**哪些任务在什么版本和验证前提下，未能到达产品验收、交付应用与提交；失败主要来自产品质量、控制面契约、外部执行条件还是实验自身无效？每取得一个有效交付结果，消耗多少可归属 token、实际费用和人工处理？**

专家需要补充的工作包括样本去重、运行版本分层、未完成样本的删失处理、失败节点与根因的区别、成本归属完整性，以及保留成功和正确拒绝的反例。原始材料优先级为：事件行与节点记录 → 失败运行的输入/输出/进程事实 → 每个 run 的 usage 与 occurrence → 当时运行工件身份 → Git 中纪律规则的实际 diff。已有总结仅用于发现文件路径。

本轮读取了 registry 指向的现存项目 ledger 和仓库内 W09 运行快照，按 workstream ID 去重、按 run ID 去重。完整清单见 [census.json](census.json)，输入散列见 [source-sha256.json](source-sha256.json)，复算程序见 [recompute.py](recompute.py)。

| 可发现队列 | case | run | 标为 PROVIDER_RUN_USAGE 且非 cumulative 的 run | workflow COMPLETED | 本轮发现 delivery_committed |
|---|---:|---:|---:|---:|---:|
| W09，10 月 | 9 | 44 | 40 | 1 | 1 |
| OKX K 线，8–9 月 | 12 | 158 | 135 | 0 | 0 |
| cancer-clinic 历史库存 | 129 | 712 | 191 | 8 | 0 |
| captcha-auth-app 历史库存 | 4 | 60 | 54 | 2 | 0 |
| darwin-skill-optimizer 历史库存 | 1 | 9 | 8 | 0 | 0 |

后面三组只用于反例和保存完整性检查，未逐例做根因审阅，不能据此给出全项目因果排序。旧 schema 没有 delivery_committed 不等于没有交付。registry 中另有 83 个项目入口未发现现存 ledger；其临时目录/历史样本不能凭报告补入原始证据统计。captcha 的 WA53 有一个重复 event ID，需要在未来重放时检验其幂等语义，不能直接按行数计算事件数。

W09 由八个换行函数尝试和一个新的 OKX 阈值任务组成。新阈值任务与旧 K 线任务目标不同，不能为了增加样本量混合估计成功率。当前 W09 有五个 WAITING_FOR_RECOVERY、三个 RUNNING、一个 COMPLETED。三个 RUNNING 的 active node 实际是 READY；这是快照中的待续状态，不能直接称为失败或“进程仍在运行”。

本轮统计的是**账本中可加总的 provider usage 声明**，并非全部经供应商账单核销的实际消耗。存在缺失与归属异常，因此下文统一称“已记账 token”；不能把漏记项当零，也不能把这些总额一概称为真实消耗下界。

## 状态链与权威边界

| 领域 | 权威所有者/事实 | 消费者及限制 |
|---|---|---|
| Definition | 每个 case 保存的 workflow/definition.json；当前 loader、contract registry | runtime 路由；审计不把不同 definition 的节点数直接混用 |
| Instance | workflow/current.json、nodes、iterations、gates，经 ledger commit | WorkflowRuntime、RoutingEngine；workstream.json 的 created 不能覆盖 workflow 的 COMPLETED |
| Execution | execution claim、固定 prepared/intent/outcome/completion 与原生进程观察 | AgentRunCommitProtocol、AgentExecutionCompletion；当前 run.json 是可变观察材料，不是完成批准来源 |
| 产物与批准 | typed artifact、绑定的 GOAL/EXECUTION contract、当前 gate decision | qualifier、发布与副作用入口；模型正文不授予批准 |
| Readiness | operator 登记的 request、环境依赖、epoch 和 ref/SHA | VerificationReadiness；它证明声明前提，不证明工具执行或测试成功 |
| Delivery | 候选身份、应用意图/进程/观察、最终 commit 事实 | DeliveryApplicationService；workflow 完成与交付提交分开核验 |
| Projection | metrics、timeline、UI/CLI read model | 只用于观察；不能从界面绿灯推导业务完成 |

当前执行链可以沿以下持久化顺序检查，不能简化成“先跑模型再校验”：

| authoritative pre-state / action | 写入与副作用顺序 | post-state 与下一消费者 | 中断、重试、重放含义 |
|---|---|---|---|
| 可执行 node，runtime/worker 持有合法 claim | 生成输入；写固定 R01 prepared；ledger 绑定 claim；再次 qualification | 固定输入绑定，adapter 才能尝试 dispatch | 未绑定 R01 的孤立字节不授予执行；改变输入不得沿用 claim |
| 已绑定 prepared，当前 executor | 写 R02 external intent；native command intent；外部调用；结果观察 | 原始 IO/typed outcome 进入验证 | intent 后失联是 external effect UNKNOWN；不能用退出码 0 或自动重发假装无副作用 |
| 外部结果存在 | artifact 验证/发布证据；固定 outcome 与 R03 completion；L03 消费完成结果 | node result → routing 或 recovery | completion 落盘而 ledger 尚未消费时重放固定结果，不能再次调用 provider |
| plan 等待人类，operator 记录 readiness | request bytes、subject ref、事件在 ledger mutation 内提交；计划引用通过验证后才获 EXECUTION 批准 | approved execution → implement | 过期、撤回、异 epoch/goal 或未注册引用拒绝；readiness 不代替批准 |
| acceptance 已批准 | workflow terminal；独立的 delivery candidate/apply intent/process/observation/commit | 目标仓库真实提交 | workflow COMPLETED 本身不足以证明交付；不确定应用结果不得盲重试 |

这是当前代码的边界地图，并非声称所有历史版本都遵循同一协议。对应代码： [AgentRunCommitProtocol.java](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/AgentRunCommitProtocol.java:82)、[AcpxAgentTaskHandler.java](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java:406)、[VerificationReadiness.java](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/VerificationReadiness.java:133)。

## 原始证据支持的根因

### 1 系统已知事实的模型转写曾经直接阻断闭环，且仍有兄弟路径

**MBC2：已确认的历史根因，当前已有方向正确的修复。** run-1791728580831 输入提示第 295 行给定 base_proposal_sha256，模型返回值只把其中 `...f239f...` 抄成 `...f237f...`。exitCode=0、envelope 有效，但 artifact 以 REVISION_BASE_PROPOSAL_SHA256_MISMATCH 拒绝，节点转 WAITING_FOR_RECOVERY。该 run 已记账 13,852 token。

原始 [输入](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fresh-fifth-gate-20261011/stopped-ledger-snapshot/runs/run-1791728580831/prompts/intake-claude-brainstorm.md:295)、[输出](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fresh-fifth-gate-20261011/stopped-ledger-snapshot/runs/run-1791728580831/outputs/intake-claude-brainstorm.json)、[节点诊断](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fresh-fifth-gate-20261011/stopped-ledger-snapshot/nodes/node-intake-claude_brainstorm-006.json)形成完整链。SHA 拒绝本身正确；不必要的是让生成模型成为系统已知 SHA 的搬运者。

当前 [ContractRevisionBinding](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRevisionBinding.java:10) 已把 baseline 身份绑定到程序 R01，模型只提交 base proposal ID。RevisionIdentityWireTest 还会拒绝旧模型 SHA 字段。不能把历史 bug 当作今天仍需从头修复的问题，也不应再次添加 SHA 转写提示词。

**7SNA：当前仍存在的同类风险。** run-1793048679201 的五条 verification binding 都以 operator 登记的 method 为完整前缀，然后追加对应 AC 的具体断言。当前 validator 使用 `request.method().equals(binding.method())`，追加内容即拒绝。该 plan run 已记账 306,062 token；整个 case 当时已记账 2,034,803 token，尚未进入 implement。不能把整个累计消耗都归因于这一条拒绝。

本轮用原始 GOAL、readiness request 和 [plan](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/runs/run-1793048679201/outputs/plan-claude.plan.json)，在隔离 fixture 调用当前 production validator：五条原方法均被拒绝，换成原登记 method 后通过。见 [historical-replay.json](historical-replay.json)。这证明拒绝机制，未证明所有追加说明语义都正确，也不授权恢复旧 case。

10 月 28 日已有修复要求模型逐字复制 method、把细节写入 validation_plan；它减少歧义，但仍保留生成式复制环节。最小结构修正是让模型选择已登记声明，程序解引用出原 method，模型只写该 criterion 的新增细节。不能把 equals 改为 startsWith，后者可能接受改变授权方法的恶意后缀。

### 2 输出契约噪声、证据类型混淆与执行中断必须分开

| 样本 | 原始输入/输出与失败 | 可支持的结论 |
|---|---|---|
| 8SVF / run-1791622684632 | 空 tracked inventory；模型将程序提供的 repo-map.json/binding.json 写入 evidence_refs；修正调用后仍被 TASK_CONTRACT_EVIDENCE_TRACEABILITY_INVALID 拒绝 | 产品树证据与程序 artifact 引用的类型边界没有在当时生成契约中表达清楚；不是代码产品不合格 |
| RN9X / run-1792021385197 | revision_responses 增加 `response` 字段；原输出和一次 correction 都保留该字段；parser 报 unknown field | 格式修正机制存在但未纠正该输入；usage unavailable，不得按零成本处理。耗尽原有纠正预算后不能凭当前 TRANSIENT 文案无限重试 |
| GCTW / run-1789171318596 | 设计原始 IO 已生成；AUTHORED_RESPONSE_ID_INVALID，151,770 token | authored identity/control contract 阻断；是否仍在当前修正范围需按版本区分 |
| KPPZ / run-1789202097910 | planned path 含 `src/main/java/com/example/okxkline/okx/okx 占位`；DESIGN_SCOPE_INVALID，315,723 token | 必须保留 scope 拒绝；不能将其全部归为“无意义格式” |
| FZ8K / run-1788956758608 | transcript 有实际 Write 调用，最后为 `[done] cancelled`；run exitCode=0、output_complete=false，3,909,833 token | 首要证据是执行被取消且代码副作用已发生；不能只看 agent_result_missing 就说 JSON 校验太严或安全自动重试 |

原始节点诊断及其路径已逐项写入 census 的 runs.parseError/nodePath；失败 IO 位于对应 runPath 同级 outputs/prompts。FZ8K 的 [transcript](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T113923Z-FZ8K/.agent/workstreams/ws-20260909T113923Z-FZ8K/runs/run-1788956758608/outputs/implement-claude.transcript.log)是“缺失最终结果不等于没有工作”的直接反例。

### 3 历史大头是实现和自测反复执行，且部分根因不在实现

12 个旧 OKX case 的已记账 token 为 66,240,977。其中 implement.claude_implement 为 29,804,293，自测为 25,084,767，合计 **82.86%**；design author+review 合计 7,892,889，约 11.92%。仅 BXWV 就消耗 36,641,792，占 55.32%。因此在这组样本中，“双 agent 设计评审是最大 token 来源”不成立。

同组中 nodeMaySucceed=false 的运行仅对应 5,047,486 已记账 token，约 7.62%。只统计红色节点会遗漏大量“本节点成功、但整个产品仍在返工”的支出。反过来，也不能把成功节点消耗全部算作浪费；可避免比例还需要成对反事实验证。

BXWV 的 run-1788997906573 单次自测记账 12,640,442 token，其中 cachedRead 12,377,792，output 86,282；原始 transcript 有 238 个工具条目，最终结果称修复生产代码和测试问题并多次运行全量测试。正文中的“已修复”不能单独当事实证明，但工具规模与执行路径说明它绝非只生成一份测试报告。其 [原始 IO](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/runs/run-1788997906573/outputs/implement-claude-self-test.transcript.log)可用于进一步逐命令归因。

更关键的是 BXWV 的 iteration 011 → 012：SELF_TEST_INCOMPLETE 的原因明确写着缺真实浏览器验收和 live smoke 官方直连证据，却路由回 context.implement_claude。此前还有工作区 `.m2repo` 的范围问题和环境网络授权修复。这条历史链支持：**部分失败把验证条件不足转成了重新实现，导致已有产物重新进入高成本执行。** 不能据此称所有修复循环无价值；其它 acceptance 迭代确实报告过实现缺陷。

当前有 external evidence/readiness 等后续修复，历史事件证明的是旧路由的浪费机制，未证明最新版本仍会重演全部路径。优先检查的 invariant 应是：仅缺环境/外部证据且源码无需变更时，不得自动派发写代码的执行；证据续接仍必须绑定原候选、测试来源和批准。

### 4 成功反例既证明能力，也暴露成本

9YYF 的 [events](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/events.jsonl)在 10 月 26 日记录 workflow_completed，随后 delivery_committed 指向 `f8ec3d41cbe1cac554a78c3131372af434440691`。本轮独立读取目标仓库 HEAD，匹配该提交。函数不是仅在 ledger 中“完成”。

该 case 有 37 个完成节点、13 次模型运行、2 次 revision，已记账 2,077,707 token。实施前 intake/design/plan 为 848,242，约 40.83%。第一次设计返工指出混合换行期望值错误、stderr 收集遗漏，这是有产品价值的反例，不能简单删掉 review。第二次 plan author+review 用于接入已经登记的 readiness ref/SHA，消耗 284,326 token，占成功 case 的 13.68%；这是更直接的可避免重复生成候选。

284,326 是相关调用的观测成本，不是已经实现的净节省；确定性装配后仍需验证输入绑定和批准，且是否能完全省掉独立复评必须由语义不变测试证明。

## 串联可靠性与每闭环经济核算

正确的串联模型首先是条件概率链式法则：

`P(交付) = P(进入设计) × P(进入计划 | 已到设计) × P(进入实现 | 已到计划) × … × P(提交 | 已验收)`

这不要求独立；只有把各项替换成固定的无条件组件成功率时，才需要独立性等假设。NIST 的串联系统说明可作基础参照，但 agent 共享上下文、provider、版本和重试历史，不能机械套用独立模型。[NIST reliability function](https://www.itl.nist.gov/div898/handbook/apr/section1/apr122.htm)

W09 截面到达数为 intake 9 → design 4 → plan 2 → implement 1 → acceptance 1 → delivery 1。相乘得 1/9 只是此库存截至快照的到达比例，不是最新版本成功概率；包括三条尚待继续的 case、不同任务与不同修复版本。八个换行尝试的对应链为 8 → 3 → 1 → 1 → 1 → 1，观测交付率 1/8。旧 OKX 为 12 → 9 → 5 → 5 → 2 → 0。

作为敏感性示例，若 37 个必要检查点各自有独立 99% 通过率，全部通过仅约 68.94%；每点 98% 时约 47.35%。这两个数不是本项目实测值。它们说明减少“必须由模型精确复制系统事实”的失败机会，比在每个失败点后附加一个新检查更可能提高串联系统可靠性。应删掉不必要的随机步骤，而非删掉保护授权的必要检查。

无 repair 的固定串联链，其每尝试预期 token 为 `Σ c_i × P(到达 i)`，每成功预期消耗为该值除以 P(成功)。有修复和有界 retry 时，应按真实转移图计算期望访问次数；不能把每一次 retry 当新独立 case 或使用 `1-(1-p)^r` 假设同一错 prompt 会随机自愈。

本次使用更少假设的已观察口径：

`U_token = 同目标 campaign 内全部可加总 usage / 已证明的交付数`

| 口径 | 分子 | 分母 | 可作出的结论 |
|---|---:|---:|---|
| 成功的 9YYF 本身 | 2,077,707 token | 1 个交付 | 单次成功样本的账本成本 |
| 八个换行尝试 campaign | 2,797,125 token，35 run 中 31 个有可加总 usage | 1 个交付 | 已观察 campaign 每交付约 2.80M token，含前面未交付尝试；不可作为长期预测 |
| 旧 OKX K 线 campaign | 66,240,977 token，158 run 中 135 个可加总 | 0 个完成 | 每成功成本未定义；不能填零，也不能把观察窗内零成功解释为未来成本必然无穷 |
| 新 OKX 阈值 7SNA | 2,034,803 token | 尚未交付 | 在制品累计成本，不能与换行任务混算单位经济 |

三项重要审计限制：

1. 旧 OKX 中 cachedRead 占 88.86%。缓存读取与新输入、输出的资源价格不同；总 token 高不等于同比美元高。必须同时报告 input/output/cache vector。
2. 旧 OKX 有 16 个已记账 run 的 provider 或 billingMode 缺失，覆盖 35,446,651 token；model 曾出现 `token`、`documents`、`in` 等不可信归属值。W09 成功 case 的 implement run 也出现 provider 空、model=`in`，覆盖 918,786 token。不能据此做可靠的模型归因或费用优化结论。
3. 8SVF 的 top-level usage 标 EXACT_REQUEST_MATCH，但两个 occurrence 分别是 LATEST_PROMPT_IN_SESSION 与 EXACT_PROMPT_MATCH，且数值完全相同（26,548）；顶层为 53,096。原始 request ID 是 3、4，不可仅凭相等就删一半，也不能认定 occurrence 归属已经对账。它是需要原生 request usage 复核的异常。本文保留账本值，并不把它作为精确节省收益。

现金口径应为 `Σ 可核销的请求费用 / 有效交付数`；订阅使用标记 subscription_token_only/subscription_unpriced，价格缺失标记 pricing_unknown，同时保留 token。9YYF metrics 中的 USD 4.708557 不能直接称为实际账单。全生命周期经济成本还应加上工程修复/独立评审的模型费用、人工处理时间和机器费用；这些并不在 workstream ledger 中，本轮不能量化“仪式成本占总成本几成”。完整派生数值见 [economics.json](economics.json)。

## 对两项假说的可证伪判决

| 假说 | 判决 | 支持证据 | 反例及推翻条件 |
|---|---|---|---|
| 所有控制都是生成后拒绝，因此不能闭环 | **否决其全称版本** | 历史 MBC2、8SVF、GCTW 与 7SNA 都有付费生成后契约拒绝 | 当前 qualifyDispatch 位于调用前；prepared/intent 防绕过测试通过；9YYF 有真实提交和外部程序 grader |
| 让模型复写程序已知控制事实是重要失败源 | **支持，限于已定位切面** | 一字符 SHA 错误；method 前缀相同仍被拒；readiness 补引用触发整个 plan 重做 | 若移除转写后，相同历史输入仍因同一必要业务条件拒绝，则对应归因被推翻；必须保持改 epoch/goal/引用/权限仍拒绝 |
| 失败驱动纪律只增不减，并使成本最高 | **前半部分有历史支持，后半部分未证实** | AGENTS/checklist 累积增长；连续冻结/复冻提交；当前 method 修复仍补 prompt 指令 | 当前新增 TRANSIENT 分级、成比例回归、第二类债延期是反棘轮措施；没有会话费用/工时/对照组，不能断言纪律是最大成本源 |
| 减少模型 reviewer 能显著降低总闭环成本且不降质量 | **证据不足** | 某些机械接线导致额外复评 | 成功 case 的 reviewer 捕获真实产品错误；历史 OKX 82.86% 在实现/自测。必须比较每有效交付成本，而非单次调用数 |

Git 原始证据：AGENTS.md 从 cf547053 的 1,393 bytes/25 行，到最后提交版本 57867384 的 11,419 bytes/145 行，当前工作区为 16,969 bytes/197 行。工程 checklist 从 2,695 bytes 到提交版本 14,840 bytes，当前 15,558 bytes。字节增长证明规则增多，不等于有效开销或坏设计。

关键变更为 2a9300b3 加 Verification Gate、0a981120 加 phase promotion、b5f73ac2 锁定 P0/P0.5、a1a70d9d 加完整 transition/review 闭环。9 月又多次出现 freeze/reuse/refreeze 文档提交。详见 [discipline-history.json](discipline-history.json)、[当前未提交纪律 diff](discipline-worktree.diff)。**不能给未提交的 10 月规则编造 Git 引入日期。**

H2 的后续因果检验应固定失败严重性与改动范围，对比采纳现有 triage 前后的：每事故 provider 调用、人工分钟、完整 reactor 次数、批准次数、复发率和逃逸缺陷。若开销增长对应显著减少的安全逃逸和返工，棘轮负面判决应撤回；若只是文案/未知非关键字段也持续触发相同全面仪式，则保留该判决。不能用禁用必要 SHA/批准校验制造“成功”对照。

## 最小改动顺序与不可退让的不变量

本次只给方案，不扩大为重写项目。阶段归属为 P0（闭环和边界）及 P0.5（用量归属）；不以审计为由启动 P1/P2/P3 缓存、compaction、memory-loop 功能。

**第一步：沿已有程序 revision binding 的模式，收回 method 和引用的转写职责。**

- owner 仍为 VerificationReadiness 与 contract compiler；模型返回 criterion ID、已存在声明的选择，以及 validation_plan 细节。程序从绑定到此次 execution 的唯一声明解引用 method/environment/executor/evidence kind；缺失或歧义显式拒绝，不从“最新文件”猜测。
- 修改边界集中在 VerificationReadiness、PlanArtifactDocument/AgentResultEnvelope 对应字段、AgentResultPromptContract、ContractCompileHandler 与相关 validator。现有 gate 决策、delivery 与 UI 状态模型不因此另开一套。
- 若只是补齐相同声明引用，系统生成确定性 binding delta，保留内容身份和既有审批；只有测试证明语义完全不变时才省去 author 重写。环境、方法、scope、goal 或 epoch 改变仍走现有批准路径。
- sibling sweep：GOAL/EXECUTION 编译、plan author/reviewer、evidence continuation、CLI readiness action、所有内置 variant、旧 schema 拒绝测试一起检查。不能只修一个 prompt。MBC2 的 SHA 工作已在当前代码中完成，保留其不可绕过测试即可。

**第二步：把环境不足与产品修复的现有分类打通到下一消费者。**

- 复用 failure catalog / retry policy / readiness/evidence continuation，明确“没有产品代码变化需求”的缺证分支不得回到写代码执行；否则 BXWV 类循环继续存在。
- RN9X 类格式问题只在既有 schema 明确为非关键、权限/身份仍有效、无不确定副作用时允许有限修正。不要全局 ignore unknown fields，更不能降低 HASH/授权/范围要求。
- 当前文档已有 TRANSIENT/BOUNDARY 分级；在检查过的 runtime/case runner 中未发现对应 transient-failures 记录及跨 case 三次升级机制的完整证据。它目前不能仅凭 AGENTS 文案宣称 ENFORCED。先将规则映射到既有 typed primaryReason 与执行入口，不再写一套更长纪律。

**第三步：把已有预算与用量归属集中到一次调用的资格判定。**

- 现有 W09 runner 有调用前预算与调用后 stop；不是“完全没有预算”。但检查过的 WorkflowRuntime → AcpxAgentTaskHandler → AgentRunCommitProtocol 主链没有 token campaign budget 检查，原生 permission timeout 不是 token 上限。当前证据只支持 runner 入口的控制，不能宣称全入口不可绕过。
- 让已有 prepared binding 引用预算许可，在 beginExternalCall 前消费一次；current CLI、worker、resume、correction 共用既有用量事实与限额，不能另立 report.json 为第二 SoT。对 correction 按 provider request 计数；缺 usage 不是零。
- 先做 request 级计量与 provider/model/billing 身份修复，再讨论美元预算。对长调用的超额保留明确策略：若 provider 无可靠流式限制，按保守 reservation 与既有时间上限止损，不能声称只有调用前检查就能保证绝不超额。
- 该步骤先覆盖造成已观察重复消耗的入口，不引入通用预算平台。历史事故无需永久 rescue；保留旧 ledger，在离线 fixture 做验证。

| end-to-end invariant | Declared by / Proven by | Consumed by / Enforced before | Failure route / Cannot-bypass test |
|---|---|---|---|
| 程序身份不由模型重写 | prepared R01 与 hash；绑定输入证明 | qualifier + compiler / provider 调用与发布前 | 输入变化拒绝；错 claim、错 baseline、异 epoch 都零调用，冷启动不能取 latest |
| 验证方法从批准声明唯一取得 | readiness request + criterion 选择 | compiler / EXECUTION 批准前 | 缺失/多义转现有 readiness；合法细节扩展可保留，改变方法必须新声明 |
| 缺外部证据不自动重写产品 | typed failure 与候选/code identity | repair router / implement dispatch 前 | 续接证据或人工处理；删除浏览器证据仍不得触发源码写入，真代码缺陷仍进入 repair |
| 预算对每次请求强制且不重复扣计 | 唯一 request usage + campaign limit/预留 | dispatch/correction 共用入口 / external intent 前 | 超额/未知状态停止；直接 CLI/worker/resume/correction 都不能绕过，崩溃不重复发请求 |
| workflow 完成不冒充交付 | acceptance、delivery observation、commit | completion audit/grader | 提交缺失时不能记有效闭环；伪造 COMPLETED、换 candidate/commit 必须失败 |

这些是建议变更的验收标准，不是本轮已实现能力。只有实际达到 ENFORCED/PROVEN 才允许称闭环；当前不能用 prompt 修复或 report 中有字段替代执行保证。

## 已执行验证与独立基准方案

本轮离线执行现有八个测试类，**77 tests、0 failures、0 errors、0 skipped**。包括 ContractQualificationSequenceTest 的 durable-write 前后失败/冷 JVM 重放、AgentRunCommitProtocolTest 的重复外部调用与不确定意图保护、WorkflowCommitSequenceTest、ExecutionClaimSequenceTest、VerificationReadinessTest、RevisionIdentityWireTest、AcpxUsageWireCompletenessTest、AuthoredReferenceCorrectionSequenceTest。见 [测试结果](invariants-results.json)。这只是审计需要的定向检查，不是本轮全模块发行验收，也不是 CI 服务已运行的声明。

确定性历史重放：原 7SNA 三个输入 SHA 与已有记录一致；五个原 method 拒绝、exact variant 通过；原文件 bytes 不变、模型调用 0。fixture 的身份是新建的，故这是**真实历史操作数的 production validator 切面重放**，不是旧 case 完整恢复或 E2E 重放。MBC2 的旧模型 SHA schema 在当前版本明确 INVALID，不能借迁移之名注入批准。

harness 外的确定性 grader：对已交付 normalize_newlines 在长度 0–5、六字符字母表上穷举 9,331 个输入，使用逐字符状态机 oracle 检查行为与幂等，全部通过；no-op mutant 失败 5,425 例，先替换 CR 的错误 mutant 失败 966 例。见 [external-grader.json](external-grader.json)。这是本次审计编写的独立执行程序，不是第三方盲评，也不覆盖 OKX、全部 README 语义或真实浏览器。

要独立验证改善后的收益，应固定一个小型分层任务集，包含：纯函数、已有仓库修改、真实外部证据依赖、授权/身份负例；使用相同模型、输入仓库 SHA、预算、环境和任务顺序的成对实验。冻结 grader，代码作者不可改 grader；先证明 empty/no-op patch 失败、参考 patch 通过，再评分候选。报告有效交付率、每有效交付 input/output/cache、现金可计算性、人工干预和 INVALID 数，不删除失败尝试美化分母。

SWE-bench 的外部评估方式是将候选 patch 应用于固定仓库并在容器中跑测试，且区分 resolved、unresolved 和基础设施 errors，适合作为这种隔离评估的参考；同一 instance 的不同 patch 要用不同 run_id 避免缓存旧判决。本轮没有启动该基准或向外部服务提交代码。[SWE-bench 官方 evaluation guide](https://www.swebench.com/SWE-bench/guides/evaluation/)

未来真实对照运行的 Verification Gate 至少应固定：Target=减少 method/identity 转写引起的失败且不降低有效交付率；SoT=固定请求 usage、ledger commit、外部 grader；Trigger=新旧工件和依赖可执行、测试任务不泄漏答案、预算已授权；PASS=危险负例全拒绝且绑定类失败减少、产品判决不退化；FAIL=绕过授权/质量下降/预算止损；INVALID=旧 binary、缺 request usage、grader 环境错误；Stop-loss=预算/次数耗尽、BOUNDARY 或未知副作用立即停止。没有该门槛不能以“验证审计结论”为由重新烧 provider token。

## 收口判定

**Boundary Review：WARN；Architecture decision：DESIGN_GAP。** 当前有实质性的调用前守卫与不可重复执行保护，已由定向测试验证部分切面；历史与当前 method 绑定则证明，控制事实仍有不必要的生成式复制。审计证明了已定位失败机制和一个真实交付反例，没有证明稳定成功率、节省金额、完整历史恢复或第三方基准收益。

观察 → 修改假说 → 反例复核 → 重新分析的结果是：从“所有生成后校验都应删除”收敛为“保留验证，减少生成模型对已知控制事实的转写”；从“纪律导致一切”收敛为“规则增长可见，因果成本尚未对账”。本轮修改的是审计假说和证据模型，不是生产状态模型。

仍未完成的目标：可靠的复杂任务交付率、request 级原生 token 全量核销、可信 provider/model/billing 归属、研发与评审成本记账、全入口预算强制、独立冻结任务集的成对验证。不能将任何一个写成已闭环，也不能用新增流程条款代替它们。

复算命令（仓库根目录）：

```bash
rtk proxy python3 dual-orchestrator/output/closure-root-cause-audit-20261029/recompute.py
rtk proxy python3 dual-orchestrator/output/closure-root-cause-audit-20261029/validate_offline.py
```

定向 Maven 命令（dual-orchestrator 目录）：

```bash
rtk proxy mvn -o -q -pl dual-runtime -am -Dtest=VerificationReadinessTest,ContractQualificationSequenceTest,AgentRunCommitProtocolTest,WorkflowCommitSequenceTest,ExecutionClaimSequenceTest -Dsurefire.failIfNoSpecifiedTests=false test
rtk proxy mvn -o -q -pl dual-adapter-acpx -am -Dtest=RevisionIdentityWireTest,AcpxUsageWireCompletenessTest,AuthoredReferenceCorrectionSequenceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

复算需要原始 ledger 仍可访问；脚本会写本目录的派生结果。历史 validator 重放还依赖本地 Maven 编译产物和保存的 classpath，因此已附结果与输入散列，但尚不是可移植、完全自包含的公共 CI 包。

收口完整性检查覆盖 3,674 个 census 输入文件，未发现本轮审计改变原文件；报告本地链接全部可解析。额外的原始 IO 与当前源代码散列保存在 [examined-io-and-current-source-sha256.json](examined-io-and-current-source-sha256.json)，测试 XML 保存在 test-evidence/，检查结果见 [audit-integrity.json](audit-integrity.json)。
