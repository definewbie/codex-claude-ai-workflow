# 首个完整 E2E Harness 边界复盘

日期：2026-08-18  
状态：`CAPTURED E2E PROVEN / RESIDUAL RISKS IDENTIFIED`  
Dual 基线：`68c4f95`  
Captured workstream：`ws-20260818T011340Z-WA53`

## 1. 结论

本轮最重要的产物不是 captcha-auth 示例项目，而是 Dual 首次以真实、完整、带人工决策的
workstream 证明了以下主链：

```text
typed task contract
-> design / revision / human decision
-> implementation
-> PostgreSQL-backed self-test
-> artifact validation
-> typed acceptance
-> human acceptance
-> workflow COMPLETED
-> completion audit ready / ENDED / gaps=[]
```

真实运行同时暴露了五个 harness 缺陷。它们都不是“模型不够听话”的同义词，而是
上下文投影、typed fact 优先级、recovery、gate action ownership 和 terminal command
composition 的边界问题。五项均已在 `68c4f95` 中关闭。

当前最高的账本完整性风险是 ledger 多文件状态转换的崩溃一致性。这里的“最高”描述
正确性影响，不自动等于下一项最高 ROI 的代码改造，也不代表已授权引入事务框架。

## 2. Evidence Baseline

Captured ledger 的终态事实：

- `workflow/current.json.status=COMPLETED`；
- `activeNodeInstanceIds=[]`；
- `domain/completion-audit.json.status=ready`；
- `lifecycleState=ENDED`；
- `finalNodeDefinitionId=gate.human_acceptance`；
- `finalOutcome=approve`；
- `gaps=[]`；
- self-test 与 acceptance 均记录 `99 tests / 0 failures / 0 errors / 0 skipped`；
- artifact validation 为 `PASS`，scope violations、missing declared、hygiene warnings 均为 `0`；
- acceptance 为 `ACCEPT_WITH_NOTES`、`blocking=false`、`must_fix=[]`、
  `missing_evidence=[]`、`verification_remediation=NONE`；
- 本轮 Claude/DeepSeek 与 Codex ACP session 均已关闭。

这些 facts 的 SoT 是 captured ledger 与 typed sidecar，不是最终报告中的自然语言总结。

## 3. E2E 暴露并关闭的 Harness 缺陷

| # | 边界 | 真实症状 | 根因 | 关闭方式 | 成熟度 |
|---|---|---|---|---|---|
| H1 | acceptance context projection | reviewer 获得的 task contract 存在不必要降级风险 | acceptance profile 的 task_contract soft ceiling 偏低 | `2000 -> 2500`，总预算、overflow 与 accounting 不变 | `ENFORCED`，本 case `PROVEN` |
| H2 | typed failure classification | 实现缺陷被路由为 `CONTRACT_GAP` | 自由文本 `contract_gaps` 覆盖 closed typed `verification_remediation=TEST_CODE_CHANGE` | `FailureArbiter` 先消费 typed remediation | `ENFORCED`，该错误形态 `PROVEN` |
| H3 | deterministic recovery | 错误分类持久化后只能停在 recovery，缺少不篡改历史的重分类路径 | recovery 只有 retry/stale 等执行语义，没有 deterministic decision compensation | 新增严格 lineage-bound `recovery replay-classification`；错误节点保留为 `SKIPPED`，正式 RoutingEngine 重新路由 | 精确错误形态 `ENFORCED + PROVEN` |
| H4 | human gate action ownership | 用户输入 `approve`，CLI/server 预转换为 `approved`，terminal gate 拒绝 | CLI、API 与 ledger 同时解释 action | CLI、autorun、server/UI/API 均提交 `approve`，canonicalization 唯一归 ledger | `ENFORCED`，CLI real-case `PROVEN`，server deterministic `VALIDATED` |
| H5 | terminal command composition | `approve + run=true` 已完成 workflow 后仍调用 resume，ledger 正确但 API 报错 | command 组合假设所有批准动作都有后续节点 | terminal approve 直接返回完成边界并关闭 session，不 resume 已结束 workflow | `ENFORCED / VALIDATED` |

### 3.1 H2 的 authoritative chain

```text
AcceptanceReviewResult.verification_remediation
-> FailureArbiter
-> FailureClassifyHandler typed artifact
-> RoutingEngine
-> implementation repair iteration
```

自由文本仍可作为说明和次级 evidence，但不再覆盖 closed typed fact。

### 3.2 H3 的 compensation 语义

`replay-classification` 不是 runtime retry，也不是人工重写 ledger：

- 原 classifier execution 已经完成，不能伪装成“从未发生”；
- 误路由的 `contract.compile` 仍保留，并标记为带 evidence 的 `SKIPPED`；
- classifier 被重新置为 `READY`；
- recovery gate 被 resolved；
- classifier 再次产生 typed decision；
- RoutingEngine 是唯一创建下一 semantic iteration 的 owner。

因此它表达的是 deterministic decision compensation，而不是 execution retry。

## 4. 本轮不是 Harness 缺陷的事实

以下问题不能为了“归因统一”而归到 orchestrator：

1. OTP 首次发送被 resend interval 拒绝，是 captcha-auth 实现缺陷；
2. 设计经过多次 human revise，不自动等于 workflow 缺陷。human gate 在本轮阻止了不完整
   设计进入实现，属于预期价值；
3. 某个 provider 生成业务方案的质量属于模型行为，只有当 harness 丢失 typed fact、错误
   投影上下文或错误路由时，才构成 Dual 缺陷；
4. 产品是否立即提交不影响 captured ledger 已经成立的 E2E 证明。

## 5. 已证明有效的 Harness 机制

- structured output 经 strict parser、schema、semantic 和 artifact validator 后才进入 routing；
- human decision 通过 gate allowed actions 和 ledger owner 执行；
- acceptance 可以发现真实实现缺陷，且 repair 后重新经过 self-test、artifact validation 与
  acceptance；
- recovery compensation 保留历史，不靠删除失败节点获得“绿色结果”；
- completion 由 workflow ledger 与 completion audit 共同证明；
- session cleanup 不改变 terminal business truth。

这说明 Dual 的主要问题不是“整个架构不可用”，而是少数跨边界 owner 在真实长链中仍有
漂移。完整 E2E 的价值正是把这些漂移从推测变成可定位事实。

## 6. 剩余风险与优先级

| 优先级 | 风险 | 当前事实 | 影响 | 建议 |
|---|---|---|---|---|
| R1 | ledger 多文件转换崩溃一致性 | 单文件替换原子，多文件 transition 非原子 | 可能产生互相矛盾且无法重试的 authoritative facts | 下一轮先做边界设计和 failpoint 验证 |
| R2 | replay 守卫 cannot-bypass 覆盖不完整 | happy path、reason 与后置条件已覆盖，部分前置守卫仅靠代码 | 回归时可能放宽 eligibility | 在 R1 协议确定后补精准 guard matrix |
| R3 | legacy `FileLedgerStore.approveDesign` | 当前生产不可达，仍自写旧 action/state | 未来误接线时形成第二 owner | 单独删除式清理 |
| R4 | replay 仅有 operator CLI | 有意限制高风险 compensation 的入口 | UI/API 不对称，但不影响当前闭环 | 保持，除非出现真实 operator API 需求 |
| R5 | replay 覆写失败节点 `finishedAt` | 原失败时间仍在 events，node 快照变为补偿时间 | 历史投影精度下降 | 与 R1 一并定义 snapshot/event 时间语义 |

没有证据支持继续增加 prompt、Agent retry、workflow phase、provider sample 或通用 recovery
endpoint。它们都不解决 R1 的 source-of-truth 一致性。

## 7. 为什么 R1 的完整性风险最高

### 7.1 它直接威胁 authoritative ledger

其余风险主要影响覆盖率、入口对称性或历史展示。R1 影响的是 workflow 当前事实本身。
一旦 ledger 文件彼此矛盾，CLI、UI、API、recovery 和 report 即使全部忠实读取，也只能忠实
展示一个错误或不可执行的状态。

### 7.2 它不是 replay 专属问题

当前 `replayDeterministicClassification()` 依次写入：

```text
failed node -> classifier node -> recovery gate -> events.jsonl
-> workflow/current.json -> workflow instance
```

`AtomicJsonWriter` 对每一个 JSON 文件使用临时文件替换，保证单文件不会留下半截 JSON；
`WorkstreamLock` 防止两个进程同时修改同一 workstream。但二者都不能保证上述六个持久化
动作作为一个状态转换整体提交。

同类顺序写还存在于 routing、stale recovery、abort、completion 等 sibling transitions。
因此先只给 replay 加一个局部 try/catch，会再次制造特殊路径，而不会关闭 ledger 边界。

### 7.3 崩溃后可能失去自恢复入口

以 replay 为例：

| 崩溃点 | 可能留下的 durable state |
|---|---|
| failed node 已写 `SKIPPED`，classifier 尚未 `READY` | current 仍指向不再 FAILED 的 active node，第二次 replay eligibility 失败 |
| classifier 已 `READY`，gate 尚未 resolved | READY classifier 不在 current active set，workflow 仍 waiting recovery |
| gate 已 resolved，event/current 尚未写 | workflow waiting recovery，但 openGateIds 与 gate document 冲突 |
| event 已 append，current 尚未写 | history 宣称 replay requested，current 仍停在旧状态 |
| current 已 RUNNING，instance 尚未同步 | 两个 workflow projection 对 lifecycle 状态意见不同 |

这种状态不是普通失败返回，而是 recovery 自己制造了新的 recovery 问题。

### 7.4 发生概率低，但影响面和修复成本高

机器重启、SIGKILL、磁盘错误并不常见；但一旦发生：

- workstream 可能无法通过现有命令继续；
- operator 只能人工理解并修复多个文件；
- 审计链失去可证明性；
- 后续所有自动化都不能安全判断下一动作。

它符合“低频、高破坏、基础层”的优先级特征。

### 7.5 它是其他 recovery 能力的可信基础

如果 recovery transition 自身不具备崩溃后可判定、可继续或可回滚的语义，再增加更多
recovery action 只会扩大不一致状态空间。先明确这个边界，长期上能减少后续每个恢复命令
各自实现补偿逻辑的成本。

## 8. 下一步不是直接建设事务框架

R1 的高价值不等于应立即引入数据库、event store、通用 transaction engine 或新 module。
下一步应先完成一个 bounded architecture assessment：

1. 清点所有一次命令写多个 ledger facts 的 transition；
2. 标注每个 transition 的 authoritative commit point；
3. 区分 snapshot、history event、derived projection 和 business fact；
4. 对 replay 在每个写入边界注入故障，证明当前不可恢复状态；
5. 比较最小可行协议：
   - commit marker / operation journal；
   - event-first + deterministic projection rebuild；
   - generation directory + pointer swap；
   - transition-specific idempotent reconciliation；
6. 只有 sibling sweep 证明问题跨多个 transition 后，才允许抽取共享机制。

明确拒绝：

- 仅在 replay 外层 catch exception；
- 失败后反向覆盖已经写入的文件并称为 rollback；
- 依赖 `WorkstreamLock` 宣称 crash safe；
- 只增加 happy-path 测试；
- 用人工 runbook 替代可判定的 durable protocol；
- 未证明需要就迁移到数据库或新 event-store module。

## 9. R1 的目标闭环

未来方案必须回答：

| 维度 | 必答问题 |
|---|---|
| Declared by | 哪个 durable fact 声明 transition 已提交？ |
| Proven by | 重启后如何证明是旧状态、新状态或待恢复状态？ |
| Consumed by | loadCurrent/routing/recovery 读取哪个唯一 committed view？ |
| Enforced before | 未提交 transition 如何阻止 resume、retry、approve 等副作用？ |
| Failure route | 每个写入点崩溃后如何幂等完成或安全回到旧状态？ |
| Cannot-bypass test | 哪些 failpoint/restart tests 证明不存在混合状态？ |
| Residual path | events、node、gate、current、instance 是否仍能各自绕过 commit protocol？ |

成熟度纪律：

- 当前风险：`CONFIRMED BY CODE / NOT REMEDIATED`；
- 设计和测试存在但尚未强制：最多 `VALIDATED`；
- 所有 mutation/load 路径受 commit protocol 约束后：`ENFORCED`；
- 真实 kill/restart slice test 成功后：`PROVEN`。

## 10. 决策

1. captcha-auth 产品提交不是当前最高优先级；captured ledger 已完成 E2E 证明；
2. 不再制造新的 provider sample 来寻找相同类别问题；
3. R1 必须进入 bounded architecture assessment，不直接编码；
4. assessment 必须优先寻求删除、集中或复用现有写入路径，不能借机建设新平台；
5. 在 R1 方案通过独立架构评审前，不声明 ledger recovery 达到通用 crash-safe。

## 11. 模型能力增强后的 Harness Subtraction 对照

### 11.1 参考来源与适用边界

参考文章：[模型越来越强，harness 该留下什么？](https://www.bestblogs.dev/article/4d67f7f025)
（腾讯云开发者，2026-08-06）。

文章报告了一个团队实践：根指令减少 61%、skills 减少 40%、Agent 从 10 个减少到
6 个，并将方法归纳为：

1. 从规定路径转向定义成功判据；
2. 从前置全量上下文转向渐进式披露；
3. 从手写示例转向接口和可执行引用物；
4. 先建立评测，再删除或增加 harness 内容；
5. 将公开知识和平台通用能力移出团队 harness，只保留组织特有流程与不可转移的人类
   责任。

这些比例是该团队的案例，不是 Dual 的删除目标或行业基准；文章转述的模型厂商 prompt
删减比例也不能替代 Dual 自己的 counterfactual evidence。Dual 应采用它的判断方法，
不能照抄它的数字。

### 11.2 四层框架与 Dual 映射

| 层 | 文章定义 | Dual 当前内容 | 判定 |
|---|---|---|---|
| L0 | 公开知识，强模型通常已具备 | “保持改动 scoped”“避免无关重构”、角色人格、通用代码审查常识 | 优先删除候选 |
| L1 | 平台或通用 agent 已提供的能力 | 工具调用、session、基础 read/search、通用 plan/self-test 循环、格式 presentation | 能委托平台的删除；ACP 未提供的能力暂留 adapter |
| L2 | 项目/组织特有流程与事实 | TaskContract、scope authority、lineage、evidence refs、workflow profiles、change-scope | 保留并尽量 typed/enforced |
| L3 | 责任、授权和后果 | human gates、allowed actions、权限边界、stop-loss、terminal acceptance | 必须保留，模型增强不替代 |

核心判断：模型能力增强主要消灭 L0 和部分 L1；它不会消灭 L2 的私有事实，也不会接管
L3 的责任。

### 11.3 Dual 与文章方向一致的部分

| 文章方向 | Dual 现状 | 结论 |
|---|---|---|
| 判据优先 | strict parser、schema/semantic validator、artifact validator、真实测试与 completion audit | 强一致，应保留 |
| 渐进披露 | 7 个 `ContextProfiles` 按 section priority、budget 和 degradation 投影上下文 | 方向正确，但 profile 重复可继续收敛 |
| 可执行引用物 | hash/lineage validated sidecar、typed refs、Git/test evidence | 强一致，应保留 |
| 人类责任 | requirement/contract/design/plan/acceptance gates，LLM review 为 advisory | 已与文章 L3 结论对齐 |
| 先评测后删除 | A1-A10 value validation、A10 same-case `10 -> 9` | 已有真实 subtraction 先例 |
| 平台能力回收 | terminal-delivery 已删除无消费者的 handoff；ACP adapter 处理 session/process | 部分完成，仍需定期检查平台能力是否已替代本地补丁 |

尤其是 A10：Dual 没有因为“handoff 看起来多余”就全局删除，而是先证明 terminal profile
没有 next-stage consumer，再将固定调用从 10 降到 9。这个过程符合文章提出的“先评测，
后删除”。

### 11.4 可以进入删除 checkpoint 的候选

以下是评估结论，不是本文件对代码修改的授权。

#### D1. 删除 production-unreachable 的旧 action owner

`FileLedgerStore.approveDesign()` 仍自行写入旧 `approved` 语义，但当前生产不可达。它既不
属于模型能力，也不属于组织流程，而是已经被 ledger gate owner 替代的旧路径。

判定：`DELETE NOW CANDIDATE`。先做 production call graph 与 test migration，再删除方法和
只服务该路径的 fixture。

#### D2. 删除 prompt 中的 L0 通用指导

当前共有 10 个 prompt 文件（416 行）和 1 个共享 template（12 行），合计 428 行，包含
角色描述、通用编码建议、通用审查建议和多处重复否定句。强模型已经具备的公开软件工程
常识不应长期占用每次调用的高注意力区域。

判定：`SUBTRACTION INVENTORY REQUIRED`。每句话必须先标记为：

- `PUBLIC_KNOWLEDGE`：删除；
- `PLATFORM_ENFORCED`：从 prompt 删除，保留平台机制；
- `DUAL_L2`：保留或迁移到 typed contract；
- `HUMAN_L3`：保留为简短明确边界；
- `UNENFORCED_SAFETY`：不能直接删除，先补 deterministic enforcement；
- `EVAL_ONLY`：移入测试，不进入 production prompt。

例如 “Keep the change scoped” 本身是 L0；真正不可替代的是 typed scope 和
`ArtifactValidateHandler` 的机械验收。

同一句规则不能脱离 profile 统一分类。当前 10 个 prompt 都重复声明不得修改 `.agent/`：

- `contractDiscovery` 与 contract correction 使用 `denyAll`，平台已阻止工具副作用，该句属于
  `PLATFORM_ENFORCED`，可进入删除清单；
- implementation profile 使用 `approveAll`，当前没有文件系统策略或 post-run 完整性校验阻止
  Agent 修改 `.agent/`，Git evidence 也不会覆盖未跟踪 ledger；该句在此 profile 下属于
  `UNENFORCED_SAFETY`，在建立 deterministic owner 前不得删除。

因此，重复的 `.agent/` 禁写块可以进入集中化/强制化评估，但不能以“十处重复”为理由一次性
删除。后续 inventory 必须按 profile 记录 owner 与 fail-closed 能力。

#### D3. 删除已被 validator 强制的 prompt 重复规则

`AgentResultPromptContract` 有 368 行。它同时承担：

- 输出 shape 示例；
- enum/closed-field 说明；
- scope canonicalization 说明；
- revision target uniqueness 说明；
- remediation 语义说明。

其中多项已经由 `AgentResultEnvelopeValidator`、`TaskContractProposalValidator`、
revision merger 和 artifact validator 强制。继续在 prompt 逐条重复会形成第二份易漂移
规格。

判定：`MOVE FROM RULES TO INTERFACE`。目标不是删除 strict contract，而是让生成的 closed
schema/typed constraint 成为短而唯一的接口说明；validator 继续终审。由于 Claude ACP
当前只承诺 assistant message、没有可依赖的 native structured-output 扩展，strict parser、
单 fence presentation normalization 和 typed validators 不能删除。

#### D4. 继续按 applicability 删除 terminal handoff

terminal-delivery 已经证明 A10 不适用。full/contract 仍保留 A10，因为其 definition 声明
next-stage handoff consumer。

判定：`KEEP CONDITIONAL`。不得把 terminal profile 的结论偷换成全局删除；只有某 profile
没有真实下游 consumer 时，才删除对应 handoff。

### 11.5 需要实验而非直接删除的调用

当前 terminal-delivery 固定有 9 次 Agent 调用、6 个 context package 节点和 6 个人工
gate。模型增强意味着固定调用不应再按“阶段仪式”自动存在，但 captured evidence 也证明
其中多项产生了真实价值。

| 候选 | 当前证据 | 优化方向 | 当前判定 |
|---|---|---|---|
| A1+A2 intake producer/reviewer | 历史 T6 的 contract obligations 与 revision 有真实收益 | small-task profile 可试单 producer + deterministic validator + human gate | `PROFILE EXPERIMENT` |
| A3 design + A5 plan | 中等任务已证明分阶段价值 | 小任务允许单次 design-and-plan artifact；复杂任务保留分阶段 | `PROFILE EXPERIMENT` |
| A4 architecture review | 本次 captcha-auth 单一 case 中多轮发现 OTP、事务、并发与容量边界；这是 `n=1` 的复杂任务证据 | 继续作为复杂任务 advisory，不做最终 authority；不能外推为所有 profile 的普遍收益 | `KEEP FOR COMPLEX / PROFILE EVIDENCE` |
| A6 plan review | 历史 T6 抓到会进入代码的 typed 偏差 | 小任务可条件关闭，复杂任务保留 | `PROFILE EXPERIMENT` |
| A8 self-test Agent | 当前是独立测试 evidence owner，但主要工作是运行确定性命令 | 优先评估 deterministic test executor；仅失败/歧义时调用模型解释 | `HIGH-VALUE EXPERIMENT` |
| A9 acceptance | 本次发现真实 OTP 实现缺陷 | 保留 independent verifier + human authority | `KEEP PROVEN` |

这里最值得实验的是 A8，不是因为验证不重要，而是验证很重要且应尽量确定性。实验只适用于
存在 canonical test 入口的仓库；若测试命令、exit code、surefire 报告和 build artifact 可以
由 runner 直接产生，模型不应固定充当 shell 命令转发器。模型只在失败归因、缺少 canonical
测试入口或 evidence 冲突时介入。

### 11.6 可以优化但暂不删除的结构

#### O1. Context build 从固定 workflow ceremony 收敛为平台能力

6 个 `context.build_package` 节点不调用模型，但增加 graph、routing 和 failure surface。
context package 本身是可审计 L2 artifact，必须保留；“每个 agent 前都先经过一个显式
workflow node”则更像 L1 实现方式。

候选方向：将 context build 变成 `acpx.agent_run` 的强制 precondition，同时继续持久化
context artifact、budget report 和 failure。只有证明 node-level observability 没有独特消费
者后才能删除 graph nodes。

判定：`ARCHITECTURE EXPERIMENT / NOT READY`。

#### O2. 固定 workflow 改为用户选择的 profile

当前 9-call terminal profile 对复杂 captcha-auth 合理，不代表 README、配置修改和小 Bug
都需要同一链路。模型增强提高了单次调用可承担的范围，因此 profile 应按任务治理需求选择，
而不是按模型是否“聪明”自动猜测。

最小方向：保留现有 governed profile，再增加经验证的 small profile；由用户/调用方明确
选择，不新增模型分类器或隐式复杂度评分。

判定：`PROFILE DESIGN READY FOR ASSESSMENT`。

### 11.7 随模型增强仍必须保留的 Harness Kernel

以下机制不应因为模型变强而删除：

1. typed TaskContract、scope authority、evidence refs、hash/lineage；
2. strict parser、schema/semantic/artifact validators；
3. permission、workspace、secret、process/session closure；
4. workflow ledger、terminal guard、recovery/compensation 与 R1 crash consistency；
5. human allowed actions、approval/revision/rejection 和责任记录；
6. deterministic test/build/change-scope evidence；
7. completion audit、usage/token/cost accounting 和不可绕过测试。

理由不是“模型仍不够聪明”，而是这些能力分别拥有外部事实、安全、状态、责任和验证。
模型没有这些事实的天然 authority，也不能继承副作用的后果。

### 11.8 与 H1-H5、R1 的重新对照

| 复盘项 | 文章带来的新解释 |
|---|---|
| H1 context budget | 不是继续扩大 prompt，而是删 L0/L1 噪声，把预算留给 typed L2 evidence |
| H2 typed fact 被 prose 覆盖 | 典型的规则文本侵入机械判据；修复方向正确 |
| H3 deterministic recovery | 与模型强弱无关，属于状态与责任 kernel，必须保留 |
| H4 action owner 漂移 | 与模型强弱无关，属于 L3 action semantics，必须唯一化 |
| H5 terminal resume | 与模型强弱无关，属于 lifecycle kernel |
| R1 crash consistency | 模型越能长时间自主运行，durable state 越重要；不因 prompt 瘦身降级 |

因此，文章没有推翻本复盘，而是修正了优先级表达：

- R1 仍是最高的 correctness/integrity 风险；
- prompt/rule subtraction 是更低成本、更快回报的 optimization track；
- 两者不能互相替代，也不应被揉成一个大型 checkpoint。

### 11.9 更新后的执行顺序

1. 先做只读 `Prompt/Rule Ownership Inventory`，覆盖 10 个 prompt 与
   `AgentResultPromptContract`，不给模型调用；
2. 选择一个纯删除 checkpoint：优先 D1，或一组已有 deterministic owner 的 D2/D3 规则；
3. 为 A8 deterministic self-test replacement 制定 counterfactual Gate，不直接替换；
4. 继续 R1 multi-file mutation inventory 与 crash failpoint assessment；
5. 基于自然任务而非人为重复 captcha-auth，验证 small profile 或 A8 实验；
6. 每次模型升级后重新运行固定调用价值矩阵，而不是永久保留当前 9-call 结论。

删除纪律：没有 owner 映射、counterfactual、机械 PASS/FAIL 和回滚条件的“瘦身”，不得进入
production。反过来，已经被平台或 validator 完整替代的规则也不得因为历史上有用而永久
保留。
