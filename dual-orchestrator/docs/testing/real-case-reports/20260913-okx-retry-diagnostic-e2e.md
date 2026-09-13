# OKX Fresh E2E：人工需求修订在调用前被拒

日期：2026-09-13。主agent执行及自查，未启动额外agent；不是独立评审报告。

## 结论

**FAIL，非INVALID，非模型输出失败。** 唯一样本`ws-20260913T004742Z-6NX5`在合法人工修订后，
被Dual自身的上下文资格规则拒绝；恢复诊断又把调用前失败误归因为模型没有返回JSON。
前置和冻结身份有效，因此这是实际流程缺陷，而不是无效测试。未进入合同编译、设计或产品实现。

- authoritative current：`WAITING_FOR_RECOVERY / evt-000014`。
- 失败节点：`node-intake-claude_brainstorm-012`，`iter-003`，`runRef=null`。
- human gate 011已正常持久化`revise`与完整note；没有篡改ledger或绕过合法动作。
- 本轮四个业务provider run均完成、exit0；失败节点没有第五个provider run。
- 同节点retry不能改变resolver必败的前置条件，故不执行；不换JAR、不恢复旧样本、不创建第二样本。
- 三个业务ACP session已关闭，产品工作树clean。没有原target apply/commit/push。
- 新增retry诊断分支、引用纠错、外部证据、产品验收、accepted candidate均`NOT EXERCISED`。
  已有确定性测试结论不撤回，也不因此提升自然E2E成熟度。

## 前置与身份

沿用[既有Gate](../verification-gates/20260909-okx-human-reviewed-scope-fresh-e2e.md)的2026-09-13节，未新建Gate或检测平台。

| 项目 | 实测 |
|---|---|
| 代码checkpoint | `6df47cff3ed1bfc97719d36360f128d56eacb90b`；Gate文档后代`9370834` |
| JAR SHA256 | `b9147829d793dc79d5c3f4c60daa12a4b894b53536c25bd8e4a6f9149ff0aa26` |
| small workflow SHA256 | `3ea9ea6c567608f4593bfef65f5868d7cbbd1be0925315c650abd8ec3f1896d5`；28/51/8/6 |
| target HEAD/tree | `c84807946493a603c789ee40c6a4c615aa846c3b` / `81520ac179f17c35c7c358c2a02102007cbe7cba` |
| REQUIREMENT/check SHA | `9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a` / `c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23` |
| 执行链 | reviewed fork `4f7b7b8e...` / v2；Claude ACP0.70.0、Codex ACP1.10.0；Doctor OK |
| 模型 | 用户配置Claude GLM-5.3、Codex gpt-6-astra/medium；未写死或切换 |
| 健康检查 | Claude单Read nonce、Codex无工具JSON均PASS；probe结束即关 |
| 工作区前置 | source原检查PASS；fresh start不带run，effective capture/verify PASS后才业务执行 |

执行环境统一`CODEX_HOME=/Users/kriswang/.codex`、reviewed `DUAL_ACPX_EXECUTABLE`、timeout=0。
时长不作失败判据。源码、JAR、需求、workflow及模型配置在终止审计中复核未漂移。
提交前双方全量1508/0/0/0沿checkpoint证据；本轮只重新package，未冒称再跑全量。

## 实际流程

```text
intake.claude_brainstorm-002：完整提案
  -> intake.codex_product_review-004：NEEDS_REVISION（CO-1/2/3）
  -> intake.decision：正常语义返工
  -> intake.claude_brainstorm-006：revision patch，生成完整有效提案
  -> intake.codex_product_review-009：READY_FOR_PRODUCT_REVIEW
  -> gate.human_requirement_review-011：允许approve/revise
  -> 主agent依据用户委托选择revise并留note
  -> iter-003、intake.claude_brainstorm-012
  -> 上下文resolver只接受BLOCKED评审：review_count=0
  -> handler返回TASK_CONTRACT_REVISION_CONTEXT_INVALID，零provider调用
  -> ArtifactContractValidator无输出可验，改写为AGENT_RESULT_MISSING
  -> WAITING_FOR_RECOVERY，提示“让模型输出JSON后retry”
```

四个正常业务run依次为1789260534648、1789260759505、1789260819142、1789260933290。
失败节点开始/结束为01:00:22.268068Z / 01:00:22.306233Z；只是约38ms的本地失败，非超时或quota。

人工修订的理由：CO-1九字段映射、CO-2安全摘要均应保留；CO-3从本地stub验证扩大为TR-9强制
全JVM ProxySelector/Socket出站拦截与自检，超出原需求“mvn test不依赖公开OKX”。
note只要求实际应用HTTP客户端使用本地stub，覆盖启动/调度、检查实际目标与请求，并隔离opt-in live smoke；
不再声称这些证据证明JVM所有出口安全，不新增文件/网络层，保留其他条款。
这是对自然提案的正常人工审议，不是为了触发retry而人为制造错误。不能为了让流程继续就批准范围扩张。

## 缺陷与边界

### F1：人工修订权被评审结果错误限制

`TaskContractRevisionContextResolver:123-161`核验已完成requirement gate、同iteration、revise和note后，
在148行却仅以`Set.of("BLOCKED")`筛选评审。当前有效评审是READY，故零baseline。
workflow同时声明READY和BLOCKED都进入该gate，且gate均允许revise；人工决定与消费方资格不一致。

这不是应该放宽SHA、lineage或允许任意旧proposal的问题。现有`verifiedBaselines:841-888`
仍承担sidecar、review subject、proposal SHA和同iteration校验，修复必须保留这些边界。
人工可以不同意正向评审，不能要求模型先否定自己才允许人修订。

更关键的是测试错误：`ContractCompileHandlerTest:205`的
`humanRequirementRevisionCannotUseNonBlockedReviewAsBaseline`明确断言这种合法场景应失败。
本轮复跑该测试为1/0/0/0。这是用局部实现推导测试语义，而非以完整人工流程检验实现。

### F2：调用前失败被后续校验覆盖为模型输出缺失

`AcpxAgentTaskHandler:119-131,609-628`在创建run目录和provider调用前返回typed上下文失败。
`WorkflowRuntime:323`与`AgentWorker:117`均继续进入`ArtifactContractValidator`；其45-80行没有对此
调用前失败保真，159行走无候选分支，481行生成AGENT_RESULT_MISSING。
原始errors尚留在node result，但outcome、parse_error、diagnostic owner/recovery hint均指向agent输出。

`AcpxAgentTaskHandlerTest.invalidRevisionContextStopsBeforeProviderDispatch`本轮1/0/0/0，
证明handler返回正确且零run；它止于handler，不验证后续runtime改写。
本次真实节点与恢复诊断提供了完整下游反证。inline/worker共享同一调用形态是代码事实，
本轮不冒称两个运行模式都进行了自然复现。

## 状态与失败切面

| 切面 | 持久化事实 / 下一消费者 / 处置 |
|---|---|
| 决定前 | READY review+proposal refs，open requirement gate；人可approve或revise |
| 决定后 | gate resolved且node COMPLETED，note写入gate result；iter-003绑定trigger gate |
| 路由后 | 新author READY继承review refs；runtime创建通用node execution后执行handler |
| provider前 | resolver拒绝；无provider run/output，新node execution最终FAILED |
| 发布校验后 | typed错误被改写；没有新proposal sidecar、reviewer或compile后继 |
| 冷读 | current/iteration/node/diagnostic一致保留上述现场；冷读不产生成功或新批准 |
| retry/resume | 同一输入仍命中BLOCKED-only条件；诊断加入prompt也到不了provider，盲重试无效 |
| 历史兼容 | 本轮只保留证据。是否支持旧状态续作须另行判断，不加入single-case rescue |

Declared：JSON gate动作与人工作出的决定。Proven：resolved gate、iteration、review subject/SHA。
Consumed：revision resolver，当前错误拒绝。Enforced before：provider前，但限制本身错误。
Failure route：错误地落到agent-result recovery。Cannot-bypass：当前反证是合法路径不能通过，
后续必须用完整成功序列和伪造/缺失/错轮拒绝共同验证，不能只把旧单测改绿。
本链Boundary Review：**FAIL**。

## 下一步最小范围

先锁定“评审READY或BLOCKED -> 人工带note revise -> 同一提案合法修订 -> 复评 -> 两门批准 -> compile”的
完整矩阵，再修消费方资格；缺失、篡改、错轮和歧义仍拒绝。自动NEEDS_REVISION、clarify与晚期合同返工
作为同边界sibling核对，不增加新动作或新平台。
同时保留provider前typed失败及其真实owner；不得用“所有FAILED一律跳过校验”重新打开无效候选发布旁路。
测试须走到runtime/worker最终账本与恢复提示，再覆盖正常最终候选仍受严格校验。

这是通用human authority与execution/artifact阶段边界，不含OKX业务特判。
本报告只诊断与归档，不在冻结样本运行中修改production，不自动转为实施或启动另一轮。

## 工件与自查

- effective workspace：`/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260913T004742Z-6NX5`。
- ledger：该工作区`.agent/workstreams/ws-20260913T004742Z-6NX5`。
- evidence：`output/real-cases/20260913-okx-retry-diagnostic-e2e/`。
- `final-audit.json`：冻结身份复核、四run/sidecar SHA、两proposal binding各八artifacts SHA、review subject验证、三个closed session、源码clean及stop快照SHA。
- `stop-snapshot/`：current、gate node、failed node、iteration、packet、diagnostic、recovery及events的固化副本。
- `focused-check/`：上述两项既有测试的本轮XML；只用于核实错误测试与handler行为，不是修复通过证明。
- `SHA256SUMS`：证据目录排序manifest，不包含manifest自身。

没有将provider报告金额当作实际账单：原run usage连同provider/model/billingMode保留在audit中，
GLM标记subscription_token_only；不把缺失金额记作零成本。
