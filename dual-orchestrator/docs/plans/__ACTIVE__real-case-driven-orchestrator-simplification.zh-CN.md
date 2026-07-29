# __ACTIVE__ Real-Case-Driven Orchestrator Simplification

## 总体目标

1. 简化 Dual Orchestrator。
2. 让 node executor/handler 只负责编排，领域规则归属唯一 domain owner。
3. LLM workflow 结果只通过 strict `AgentResultEnvelope` 进入状态流转；Markdown 仅供人阅读，不参与控制。

当前成熟度：Phase A 的 strict agent-result、typed lineage 与完整小案例已达到 `ENFORCED + PROVEN`；Phase B 的 read-governance subtraction 与 typed change-boundary enforcement 已由 deterministic tests 和 controlled real-case 达到 `ENFORCED + PROVEN`；Phase C 的 typed context boundary 已由 deterministic tests、README 与 A3 controlled real-case 达到 `ENFORCED + PROVEN`；Phase D 已通过四个删除式 slice 收敛已证明的 handler 重复 owner，并达到 `ENFORCED + PROVEN`。

## Current Active Step: Post-Phase-D T5 v3 — AUTHORIZED FOR ONE RUN

> Phase A-D 已关闭，不再继续建模或拆 handler。
>
> T1 已在 clean SHA `0e0d77ee` 上通过 A3 full real-case：
> `COMPLETED / ACCEPT`、10 Agent runs、0 revisions、0 protocol corrections、
> change scope PASS、target tests 9/9 PASS。
>
> T2 已从该运行的 `runs/*/run.json` 与
> `metrics/workstream-metrics.json` 提取只读效率 baseline。调用、token、cache、
> context 与 latency baseline 已成立；whole-run monetary cost 因 OpenAI cost
> reason 缺失及 GLM `subscription_token_only` 的金额可靠性未闭合而保持
> `NOT_PROVEN`。
>
> 2026-07-29 优先级决策：金额计算延后。T2 的调用、token、cache 与 latency
> attribution 已足以支持 T3 只读瓶颈选择；monetary cost 继续保持
> `DEFERRED / NOT_PROVEN`，不得用于成本节省或 ROI 声明。
>
> T3 已完成只读价值审查，唯一候选是从 full workflow 的 6 个 active context
> profile 中删除重复的 `requirement` section，保留 `AgentPromptRenderer` 对
> authoritative `requirement.md` 的唯一注入。self-test、stage handoff 和 6 个
> context-build 节点本身均保留。
>
> T4 已按显式授权完成唯一 subtraction：6 个 active context profile 不再重复投影
> `requirement.md`；`AgentPromptRenderer` 继续作为 full-workflow 唯一 requirement
> 注入 owner。显式 `full` profile 与自定义 section 仍保留 requirement 能力。
>
> Focused tests、sibling integration tests 与 9/9 module full reactor 已通过，
> T4 deterministic boundary 保持 `PASS`。
>
> T5 已使用同一 A3 case 启动一次 full real-case。fresh build、runner kind 以及
> Claude Code/GLM-5.2、Codex/OpenAI 最小 probe 均通过；运行在
> `design.claude_design` 首次出现 protocol correction。Strict parser 正确拒绝
> “自然语言前缀 + JSON”，bounded correction 成功，但 T5 Verification Gate
> 明确要求 correction=0，因此按 stop-loss 中断。T5=`FAIL`，不是 provider
> `INVALID`；T4 的真实 token、cache 与 latency 收益仍为 `NOT PROVEN`。
> T6 保持 `BLOCKED`。
>
> 随后的只读 gate 复核确认 bounded correction 是已有的、最多一次的 same-session
> 可靠性机制，不应被等同为功能质量失败。修订 gate 允许最多一次 correction，
> 但要求额外 prompt occurrence 的 token、cache 与 duration 全部计入；primary
> benefit metric 是六个 generated context package 的 estimated tokens。修订 gate
> 已通过结构校验，并在明确授权后执行一次。
>
> Attempt 2 的 fresh build 与 probes 均通过，前两个 Agent node correction=0；
> 但 Codex 发现 Claude proposal 把“非对象”擅自扩成“数组也必须拒绝”，返回 typed
> `NEEDS_REVISION`。ledger 记录 `revisionCount=1` 后，v2 stop-loss 中断运行。
> 该 revision 有真实 contract evidence，但与 T4 没有已证明的因果关系。两次 T5
> 均未形成可比较的完整样本，因此 T4 实际 token/cache/latency benefit 仍为
> `NOT PROVEN`，禁止第三次自动重跑，T6 继续 `BLOCKED`。
>
> Attempt 2 暴露的 interruption-report projection gap 已由 `f04cdedb` 修复。
> 中断路径现在先复用既有 `parse_started_workstream()` 恢复 captured start
> identity，再收集 ledger evidence 和生成 report；synthetic interruption test
> 证明不再错误投影 `workstream=unknown / 0 runs`。
>
> 下一次 A3 的 v3 gate 已固化：semantic revision 是合法质量机制，允许最多一次，
> 第一次 revision 不再触发 stop-loss；protocol correction 仍最多一次。所有额外
> calls/token/cache/duration 必须计入 after 样本。唯一收益 PASS 口径是完整 A3
> 质量闭环且全部 provider-reported total tokens 低于 T1 的 `577,528`。Gate
> 已通过结构校验，用户已明确授权一次 A3 full real-case；workstream 创建后授权
> 即消耗，禁止自动重跑。
>
> 当前架构：
> `docs/design/dual-orchestrator-current-architecture.zh-CN.md`。
>
> 后续优化计划与任务门：
> `docs/plans/post-phase-d-integrated-verification-and-efficiency-plan.zh-CN.md`。
>
> T2 baseline：
> `docs/plans/post-phase-d-t2-efficiency-baseline-20260728.zh-CN.md`。
>
> T3 value review：
> `docs/plans/post-phase-d-t3-bottleneck-selection-20260729.zh-CN.md`。
>
> T4 deterministic closure：
> `docs/plans/post-phase-d-t4-context-requirement-subtraction-20260729.zh-CN.md`。
>
> T5 comparison：
> `docs/plans/post-phase-d-t5-a3-comparison-20260729.zh-CN.md`。
>
> Full workflow 的节点、Agent 调用、gate、context 与 revision 价值证明：
> `docs/plans/full-workflow-complexity-value-proof.zh-CN.md`。
>
> 禁止新增 workflow/policy/context/artifact/evidence/capability/metrics 平台；
> 禁止因 handler LOC 较大继续拆分；禁止未获一次性显式授权就运行 T5 v3、
> 调 prompt/model/timeout 或提前启动 T6；禁止把 deferred monetary cost 或
> T4 实际收益解释为已证明。

> B0 Rebase Assessment: **COMMITTED** (`86af5ddb`)。
> B1 Subtraction: **COMMITTED** (`3f2ee81f`)。
> Strict change-scope paths: **COMMITTED** (`f2410e97`)。
>
> **B1 subtraction checkpoint 已完成；B4 删除收敛的 deterministic 验收已通过。**
> - handler、validator 与 metadata writer 不再把 path-level read policy 映射为
>   node failure、contract violation、recovery 或 workflow status。
> - `ExplorationPlan`、`ExplorationPolicyEvaluator`、`ExplorationReadKindPolicy`
>   及其 production projection/protocol 已删除，不再保留
>   `explorationPolicyResult` 或 `scope_expansion_request` 兼容字段。
> - production sibling sweep 对 `readScope`、`allowedDirectReadFiles`、
>   `ExplorationPlan`、`ExplorationPolicy`、`scope_expansion_request`、
>   `READ_OUTSIDE_EXPLORATION_SCOPE` 与 `DIRECT_READ_BUDGET_EXCEEDED` 为 0。
> - recommendation、actual read/write/tool-usage telemetry 与 read-reuse policy
>   保留；`REREAD_POLICY_DENIED` 是独立 token/read-reuse contract，不是 path
>   read authority。
>
> **B2 small slice = ENFORCED + PROVEN，尚未提交。**
> - `TaskContract.scope` 声明 `CREATE` / `MODIFY` 变更意图。
> - workspace `baseCommit` 到当前 worktree 的 Git `--name-status -z` 产生 typed
>   actual changes；NUL framing 保留 Unicode、Tab 与换行路径。
> - `artifact.validate` 在返回成功前执行唯一的 change-boundary comparison。
> - unexpected change、operation mismatch、DELETE/RENAME/UNKNOWN、缺失 contract、
>   Git 不可用或缺失可信 base commit 均 fail closed。
> - `approve_all` implementation agent 运行前以内存 fingerprint 固定当前
>   `task-contract.json`、`workstream.json` 与 `workspaces/`；运行中篡改或新增这些
>   change-boundary control facts 会在 node success 前失败。
> - 声明但未发生的变更仅记录为 observation，不导致失败。
> - ignored files 不属于 Git deliverable change set，因此不进入 B2 actual changes；
>   B2 是 workflow change governance，不声明 OS/filesystem containment。
> - 未新增 phase、module、registry、state machine 或 policy engine。
> - handler cannot-bypass 与 workflow/ledger cannot-bypass 均证明 violation
>   无法激活 acceptance context；strict fixture sibling smoke 通过。
>
> **Controlled README real-case = PASS。**
> - 报告：`docs/testing/real-case-reports/20260726T032753Z/report.md`；
>   workstream：`ws-20260726T033037Z-YW6K`。
> - fresh Maven package 与 provider probe 均通过；full workflow 最终状态
>   `COMPLETED`，acceptance=`ACCEPT`，benchmark grade=`PASS`，
>   acceptance reject=0，repair loop=0。
> - 12 个 agent run 均产生 strict `.contract.json` sidecar；最终 report、
>   handoff、next-stage prompt 与 delivery PR body 完整。
> - `change-scope-evaluation.json` 为 `PASS`；typed actual change 只有
>   `README.md/MODIFY`，missing declared 与 violations 均为空；
>   `artifact.validate` ledger result 消费同一事实并记录
>   `change_scope_status=PASS`。
> - 目标 worktree 仅修改 `README.md`，`node --test` 通过。
> - implementation actual report 仍记录 `actualReadFiles=[README.md]`、
>   `actualWriteFiles=[README.md]` 及 typed tool usage；workstream 中不存在
>   legacy exploration plan/policy artifact。
>
> **Phase B = PASS / ENFORCED + PROVEN。Checkpoint `e1c094b`。**
>
> **Phase C = PASS / ENFORCED + PROVEN。**
> - full reactor：9/9 modules PASS（runtime 439、adapter 300、CLI 68、server 53）；
> - independent boundary review：PASS；
> - workspace scan、requirement/filename scoring、seed content digest 与 raw input-ref
>   expansion 已删除；
> - validated agent sidecar 内容由 node + execution ledger 中的 SHA-256 绑定；
> - unknown refs 仅保留 pointer，canonical context/direct artifact 在读取前执行
>   exact path、real-path containment 与 symlink 检查；
> - README 报告：`docs/testing/real-case-reports/20260726T111504Z/report.md`；
>   workstream：`ws-20260726T111801Z-CM6B`；
> - 失败前 7 个 Agent 节点的 sidecar 当前 hash、node occurrence hash 与
>   node execution hash 全部一致；prompt 中未发现 workspace/filename heuristic
>   或 seed content digest，未知 transcript/stderr 仅保留 pointer；
> - 历史首次 README 运行在 `implement.claude_self_test_report` 停止：Claude 返回可解码的纯 JSON，
>   ACP `end_turn` 且 exit code=0，但缺少必需字段 `result.missing_evidence`；
> - 当时 bounded correction 仅接受 `AGENT_RESULT_PARSE_FAILED`，因此
>   `contractDecodable=true` 的 schema contract violation 未触发 correction；
> - 该次失败当时不能作为 Phase C `PROVEN`；此中间状态已由后续
>   schema-level bounded correction、README rerun 和 A3 PASS 取代，不是当前限制。
> - 独立 owner assessment：**PASS / IMPLEMENTATION READY**。Schema truth 保持在
>   `AgentResultEnvelopeValidator`；`AcpxAgentResultAcquirer` 只执行最多一次同 session
>   correction；`ArtifactContractValidator` / `AgentWorker` 保持最终唯一准入。
> - assessment：
>   `docs/plans/__CHECKPOINT__schema-level-bounded-correction-assessment.zh-CN.md`。
> - schema-level bounded correction：**PASS / ENFORCED + DETERMINISTICALLY PROVEN**。
>   Strict parser 未放宽；validator 返回 typed correction targets；acquirer 最多执行一次
>   same-session、deny-all correction；第二份结果改变 decision/summary/evidence 或其他
>   非 target 字段时直接拒绝。Targeted tests 与完整 reactor 均通过。
> - schema-correction checkpoint：`542187a7`；
> - README rerun 报告：
>   `docs/testing/real-case-reports/20260726T143936Z/report.md`；workstream：
>   `ws-20260726T144218Z-SP43`；
> - rerun 达到 `COMPLETED + ACCEPT + benchmark PASS`，10 次 Agent run、0 repair
>   loop；目标 worktree 仅修改 `README.md`，`npm test` 4/4；
> - `implement.claude_self_test_report` 首份 occurrence 为
>   `contractDecodable=true + contractSchemaValid=true`，因此本次未触发 correction；
>   缺字段 correction 的 cannot-bypass 证据仍来自 deterministic integration tests；
> - Phase C README 边界达到 `ENFORCED + PROVEN`；
> - A3 首次运行报告：
>   `docs/testing/real-case-reports/20260726T144933Z/report.md`；workstream：
>   `ws-20260726T145216Z-QKJJ`。该运行暴露 `ProcessUsability` 在 handler 与
>   metadata writer 间重复推断，属于 execution fact owner 漂移；
> - `bacd51dd` 收敛 authoritative `ProcessUsability` 后，同一 A3 重跑报告：
>   `docs/testing/real-case-reports/20260726T153645Z/report.md`；workstream：
>   `ws-20260726T153926Z-2H4Q`；
> - A3 重跑达到 `COMPLETED + ACCEPT + benchmark PASS`，20 次 Agent run，
>   目标 worktree 只修改声明的三个文件，最终 report、handoff 与 delivery
>   artifacts 完整；
> - A3 证明多文件 typed context、lineage、strict result、change boundary 与
>   workflow routing 可以完整闭环。该运行没有实际触发“非零 ACP process exit
>   但 strict typed result 完整”的特殊分支，因此该特殊分支只达到
>   `ENFORCED + DETERMINISTICALLY PROVEN`，不得描述为 real-case proven；
> - Phase D 仅激活只读 assessment。完成 assessment review 前，不得修改
>   production handler。
> 评估结论与单次减法范围见
> `docs/plans/__CHECKPOINT__phase-c-context-boundary-assessment.zh-CN.md`。

## 执行纪律

每个 real-case 失败都先按以下顺序处理：

```text
typed failure fact
  -> 定位唯一 owner
  -> 区分 local defect / missing domain owner
  -> 最小修复或抽取 domain service
  -> 重跑同一个 case
```

不得因为单个失败立即增加通用 framework、policy engine 或新的兼容解析器。只有同一规则被多个 executor 消费、且现有 owner 无法承载时，才允许抽取 domain service。

每个 phase 必须独立完成 Analyze -> Modify -> Review -> Reflect -> Analyze；未达到本 phase 的 PASS，不激活下一 phase。

## Phase A：完整小案例

### A1 README Full Lifecycle（PASS-WARN，等待 checkpoint）

目标：在 `full` variant 中证明：

```text
intake -> contract -> design -> plan -> implement
-> self-test -> acceptance -> delivery
```

PASS：

- 最终状态 `COMPLETED`；
- acceptance 最终决策为 `ACCEPT` 或 `ACCEPT_WITH_NOTES`；
- Claude/Codex 最终消息均通过 strict `AgentResultEnvelope`；
- 无 Markdown/自然语言控制路由；
- 无手工修改 ledger、artifact 或 worktree；
- 目标仓库只发生 contract 允许的 README 变更；
- fresh build、provider probe 和运行工件完整。

FAIL：任何 workflow/recovery/contract/scope/acceptance/delivery 边界失败。

INVALID：provider、配置、旧 jar、探针或外部运行时导致本轮无法证明代码目标。

Stop-loss：首次出现 recovery 即停止；不调预算、不切模型、不顺手修 Scope/Context/handler。

当前证据：

- `20260722T075423Z` 首次 A1 运行在 `intake.codex_product_review` 连续两次返回 `NEEDS_REVISION`，随后 Claude session reconnect；主因是 review context 只携带 proposal reference/summary，完整 typed proposal 未进入 prompt。
- 直接边界修复：workflow context 节点声明 `required_input_artifact=task_contract_proposal`；context builder 在 reviewer 启动前验证 reference/hash/lineage，并投影完整 proposal；完整 context package 不再被 adapter 的 6000 字符 preview 截断。
- deterministic closure：篡改 reference/sidecar 时 context 节点失败且 reviewer 启动数为零；`full`/`contract` sibling definitions 一致；full reactor 绿色。
- `20260722T083641Z` A1 重跑已通过 intake、contract 和 human requirement review，在 `design.claude_design` 停止。ACP prompt 正常完成（exit 0），但 final assistant message 为 prose + JSON，唯一 strict `AgentResultEnvelopeParser` 正确拒绝并产生 `AGENT_RESULT_PARSE_FAILED`。
- 当前 checkpoint：只实现一次有界 agent-result contract correction。ACP adapter 负责同一 session 的第二次 prompt；唯一 strict parser 决定 wire contract 是否可解码；`ArtifactContractValidator` 继续唯一负责 lineage、node contract 和 semantic validation。不得提取 prose 中的 JSON，不得增加通用 execution pipeline、`TaskHandler.reinvoke()` 或 workflow retry。
- `20260722T134655Z` controlled real-case（workstream `ws-20260722T134943Z-Z5FK`）完成 `full` lifecycle：最终状态 `COMPLETED`，最终 acceptance `ACCEPT`，16 个 Agent run 全部 `SUCCESS`，目标 worktree 仅修改 `README.md`，`node --test` 通过，fresh build 与 provider probe 均通过。
- 同一 real-case 真实触发一次 bounded correction：`design.claude_design` occurrence 1 为 `AGENT_RESULT_PARSE_FAILED`，occurrence 2 在相同 `claude-main-ws-20260722T134943Z-Z5FK` session 中 strict decode 成功；没有第三次调用，最终 usage 为两个 prompt occurrence 的显式聚合。
- 运行中未出现 `RECOVERABLE_RATE_LIMIT`、`PROCESS_STALLED` 或 reconnect。此前将 session UUID 中的 `429b` 误判为 HTTP 429 的 matcher 已改为仅接受显式 429 形态，并由 catalog、health cannot-bypass tests 与本次 replay 证明。
- 本次成熟度为 `PASS-WARN`：A1 的 typed result、lineage 与 full lifecycle 边界达到 `ENFORCED + PROVEN`；两次 acceptance `REJECT_CONTEXT_INSUFFICIENT` 经既有 repair loop 后收敛为 `ACCEPT`。

#### A1.1 Acceptance Verification Evidence Lineage（PASS）

目标：收口 A1 暴露的 acceptance evidence 路由与失败分类缺口，不扩张为通用 Context Engine。

Authoritative fact：已通过 `ArtifactContractValidator` 且绑定 durable node/execution result 的 self-test contract sidecar，其中 `node_id=implement.claude_self_test_report`、`source_artifact_type=self_test_report`、`decision=SELF_TEST_COMPLETE`，并包含 `checks_run`、`evidence` 与 `verification_summary`。

唯一消费链：

```text
self-test validated sidecar
  -> ArtifactValidateHandler（与 Git evidence 聚合并转发）
  -> ContextBuildPackageHandler（启动 reviewer 前要求 SELF_TEST_COMPLETE）
  -> acceptance prompt typed projection
```

分类规则：当 acceptance review 同时声明 `missing_tests` 与泛化 `context_gaps`，`FailureArbiter` 必须优先分类为 `VERIFICATION_INSUFFICIENT`，并由既有 workflow edge 路由回 `implement.claude_self_test_report`，不得回到 implementation。

Cannot-bypass：

- 已验证 self-test sidecar 经 artifact validation 后仍存在于 output refs，并进入 acceptance context；
- 缺少 `SELF_TEST_COMPLETE` 时 context node 失败，acceptance reviewer 启动数为零；
- `missing_tests + context_gaps` 选择 `REJECT_VERIFICATION_INSUFFICIENT`；
- `MISSING_TEST_EVIDENCE` 是精确 typed alias，不依赖 summary 正文；
- full workflow 的 verification edge 仍只路由到 self-test report。

完成条件：targeted tests、`dual-runtime` full suite、`dual-adapter-acpx` full suite、`git diff --check` 与独立架构 review 全部 PASS。通过后提交 A1.1 checkpoint，再评估 A2；A2 未满足条件时不得启动 real-case。

关闭证据：

- `ArtifactValidateHandler` 只从显式 input refs 转发通过 `AgentOutputLineageVerifier` 的 sidecar；不扫描 runs。
- `AgentOutputLineageVerifier` 将 sidecar decision 同时绑定 node 与 node-execution durable result；发布后篡改 cannot-bypass test 通过。
- acceptance requirement 绑定 `implement.claude_self_test_report + self_test_report + SELF_TEST_COMPLETE`，错误 producer/type/decision 均不能满足。
- evidence 缺失在 reviewer 前产生 typed `REQUIRED_AGENT_EVIDENCE_MISSING`；显式缺测优先分类为 `VERIFICATION_INSUFFICIENT` 并路由回 self-test。
- targeted tests、`dual-runtime` full suite、`dual-adapter-acpx` full suite、`git diff --check` 均通过；第二次独立架构 review 为 `PASS`。

### A2 小 Bug（PASS）

目标：证明 source-code 修改、定向测试、A1.1 self-test evidence lineage 和 acceptance 闭环。

当前 case：`issue-flow-app` 的 `summarizeIssues` 对非数组输入泄漏迭代器 `TypeError`。限定只修改 `src/dashboard.js` 与 `test/issueWorkflow.test.js`，要求在函数边界抛出精确的 `issues must be an array`，并保持现有行为与 `node --test` 全绿。

Readiness：A1.1 为 `ENFORCED + PROVEN` 且已提交为 `16fe713`；目标仓库干净、现有测试通过、缺陷由失败的精确断言复现；Verification Gate 已锁定 source of truth、PASS/FAIL/INVALID 与 stop-loss。允许启动一次 controlled `full` real-case。

关闭证据：

- controlled run `ws-20260722T150030Z-MP3G`，报告 `docs/testing/real-case-reports/20260722T145746Z/report.md`；fresh build 与 provider probe 均通过。
- workflow 一次完成 `COMPLETED`，final acceptance=`ACCEPT`，acceptance reject=0，repair loop=0，benchmark grade=`PASS`。
- `ArtifactValidateHandler` output refs 保留 ledger-bound self-test sidecar；acceptance context 明确投影 `SELF_TEST_COMPLETE`、`node --test` 5/5 与 verification summary。
- 目标 worktree 只修改 `src/dashboard.js` 和 `test/issueWorkflow.test.js`；新增边界校验与 focused test，`node --test` 5/5 通过。
- 未触发 `REQUIRED_AGENT_EVIDENCE_MISSING`、自然语言控制 fallback、manual ledger/worktree repair 或范围外修改。

### A3 简单应用 API（PASS）

目标：使用外部 `issue-flow-app` JavaScript 测试项目，证明 Dual 能组织 Claude Code 与 Codex 完成一个有限多文件、跨模块的应用 API 任务，并守住 contract、scope、self-test evidence 和 acceptance 闭环。该 JavaScript 项目只是受控 real-case 目标，不是 Dual 的运行时组件或依赖。

当前 case：保持 `src/issueWorkflow.js` 中的 `ALLOWED_TRANSITIONS` 为唯一状态迁移 SoT；新增 `availableTransitions(status)` 查询和 `src/issueApi.js` 的 `getIssueState(issue)` 稳定 DTO projection，并在独立测试中验证各状态、非法状态及返回数组隔离。限定只修改 `src/issueWorkflow.js`、`src/issueApi.js` 与 `test/issueApi.test.js`；不引入 HTTP server、网络、第三方依赖或第二份迁移规则。

Readiness：A2 controlled real-case 已达到 `COMPLETED + ACCEPT + PASS`；目标仓库干净、基线测试通过，且 precheck 已证明 `issueApi.js` 与 `availableTransitions` 尚不存在。允许使用固化的 Verification Gate 启动一次 controlled `full` real-case。

当前证据：

- `20260723T024557Z` 首次 A3 运行在 `intake.claude_brainstorm` 停止。严格 `AgentResultEnvelope` 正确拒绝了无法表达“修改现有文件”和“创建新文件”区别的 proposal；旧模型只能依赖 `new:` 字符串前缀，CREATE/MODIFY 语义没有进入 typed contract。
- `TaskContract.ScopeEntry(path, operation)` 现为 proposal 与 canonical TaskContract 共享的唯一 scope value object，`operation` 仅允许 `MODIFY|CREATE`。`MODIFY` 必须已存在，`CREATE` 必须尚不存在；旧 `new:` 前缀被显式拒绝。
- `task_contract_proposal` schema 已升级为 v2。旧 v1 sidecar 与“schema v2 但 included 仍为 string[]”均 fail closed，不提供 compatibility fallback，也不会生成 `task-contract.json`。
- proposal -> review -> human approval -> contract.compile -> context/memory/governance/report 的 production consumers 已迁移到 typed `ScopeEntry`；mechanical proposal failure 保留 valid AgentResult，并以 artifact contract failure 路由，不伪装成 output missing。
- controlled lifecycle smoke 以真实 ACP JSON-RPC 形态注入 strict `AgentResultEnvelope`，31 个节点、6 个 gate、11 个 agent run 全部完成；显式证明 intake proposal schema v2、最终 scope typed shape，以及 `main.py/MODIFY + README.md/MODIFY` 精确保真。
- semantic smoke 的 missing plan body、review prose noise、cross-stage reread 三个场景均通过；正式 smoke 不再生成 `dual-artifact` / `dual-control` Markdown。
- `20260723T074957Z` A3 重跑通过 intake、contract.compile 与 design，在 `plan.codex_plan_review` 返回 typed `NEEDS_REVISION` 后按 stop-loss 中止。Claude 生成的 `plan-claude.plan.json` 内容完整，但 review context 只信任 `plan-claude.contract.json` 的摘要；renderer 将同一 validated `PlanResult` 的派生 plan projection 误标为 `unvalidated agent result omitted from context`，导致 Codex 缺少实施步骤与验收映射。
- 修复不新增 evidence module 或 artifact state machine：`AgentResultDocumentProjector` 将已经由 `AgentResultEnvelopeValidator` 验证的 typed plan body 写入现有 `agent_output_contract` sidecar；`AgentEvidenceIndex` 将 payload 完整性纳入 required-evidence 判断；summary/full context renderer 从同一 validated entry 渲染并去重，伪造 derived plan 不能覆盖它。
- reviewer 启动门禁已收口：`source_artifact_type=plan_result` 但 typed plan body 缺失或不完整时，context build 返回 `REQUIRED_AGENT_EVIDENCE_MISSING` 且不发布 reviewer context。characterization/cannot-bypass tests、full Maven reactor（9/9 模块）与 `git diff --check` 均通过。
- `20260723T100647Z` A3 重跑证明 typed proposal -> Codex review lineage 正常，但首轮 proposal 被 typed `NEEDS_REVISION` 拒绝：它遗漏了用户明确要求的 strict `AgentResultEnvelope` 执行协议，并把 JavaScript `object` 的数组边界自行收紧为“必须拒绝”。该结果是有效的合同质量拒绝，不是 evidence 丢失；运行按零 revision-loop stop-loss 中止。
- intake producer 与 Codex reviewer 现共享同一保真语义：所有用户明确的业务目标、scope、行为、验收和验证要求必须进入 binding proposal fields；`risk_areas`、`open_questions`、`evidence_refs` 不能代替绑定要求；agent 不得基于语言边界或保守偏好擅自扩大/收紧可观察行为。旧的“choose the conservative default”冲突规则已删除。
- `AgentResultEnvelope`、JSON-only output 与 workflow routing 是 harness node Output Contract 的职责，不属于业务 task proposal。它们由 prompt contract、strict parser 与 validator 独立强制；producer 不复制、reviewer 不因 proposal 未重复这些协议而拒绝，避免形成第二个协议 SoT。
- `20260723T112922Z-8P57` 进一步暴露确定性 projection bug：absolute-path sanitizer 的 ASCII `\p{Alnum}` 负向判断把“缺失/非对象”“修改/新增”等中文 slash phrase 当作 POSIX 绝对路径，导致 reviewer context 出现 `[outside-workspace path removed]`。sanitizer 改为 Unicode character class；真实外部绝对路径仍脱敏，中文合同原文保持不变。
- 该边界不新增 requirement registry、evidence registry 或状态机。机械 schema validator 继续负责 shape；独立 Codex review 负责语义保真并 fail closed。prompt contract tests、adapter full suite 与 sibling rule sweep 已通过。
- `20260723T114300Z-TMHV` 使用同一 A3 Verification Gate 完整通过：workflow=`COMPLETED`、final acceptance=`ACCEPT`、acceptance reject=0、repair loop=0、benchmark grade=`PASS`。fresh Maven build、provider probe 与 runner-kind 检查均通过。
- A3 目标 worktree 仅修改 `src/issueWorkflow.js`、`src/issueApi.js` 与 `test/issueApi.test.js`；`ALLOWED_TRANSITIONS` 保持唯一迁移规则，`issueApi.js` 复用 `availableTransitions`，`npm test` 通过。
- intake review 只发现并收敛了 proposal 内真实的字段拼写冲突；此前错误的 harness protocol 重复要求与中文 slash phrase 脱敏污染均未复现。self-test 以 typed `SELF_TEST_COMPLETE` sidecar 进入 acceptance context，Codex 一次返回 `ACCEPT`。
- 关闭报告：`docs/testing/real-case-reports/20260723T114021Z/report.md`。

Phase A 完成条件：A1/A2/A3 各有一个可复现的 `COMPLETED + ACCEPT` real-case，且失败修复没有引入自然语言控制 fallback。

## Phase B：Scope Boundary

仅在 Phase A 暴露 scope owner 缺口或 A3 PASS 后显式激活。

### B0 Scope Boundary Characterization（COMPLETE）

目标：不修改 production，先证明当前 `RepoScopeGovernance` 中每一类 scope 规则的来源、owner、消费者和准入影响，建立 Phase B 的删除基线。

本 checkpoint 只允许：

- 区分 typed `TaskContract.scope`、通用路径安全规则、语言/框架 convention、文件名或目录结构猜测；
- 追踪哪些规则真正影响 read admission、scope expansion 或 agent 启动；
- 固化现有越界读取 cannot-bypass 测试及其命令；
- 给出保留清单、删除清单、目标 owner 和 B1 readiness 结论。

本 checkpoint 禁止：

- 修改 `RepoScopeGovernance` 或其他 production 代码；
- 新增 Scope Engine、policy DSL、repository index framework 或通用 capability framework；
- 运行 provider / real-case；
- 提前启动 Phase C Context Boundary 或 Phase D Handler Convergence。

关闭结论：

- canonical `TaskContract.scope` 是业务 scope SoT，但当前 effective read scope 还混入 input refs、evidence、git changed-files、requirement tokenization 和 repo-map filename scoring；
- heuristic/observed candidates 会进入 `ExplorationPlan.allowedDirectReadFiles`，因此会影响运行后节点验收，不只是 prompt 建议；
- 当前 enforcement 能阻止违规 run 成为成功节点，不能在 tool call 前物理阻止读取；
- fixed extension allowlist、acceptance prose path extraction、filename scoring 和重复 stage policy owner 均进入 B1 删除清单；
- deterministic baseline：runtime scope tests 30/0、adapter boundary tests 9/0、heuristic characterization 1/0；
- 完整分析、删除清单、目标 owner 与 B1 tests：`docs/plans/phase-b0-scope-boundary-characterization.zh-CN.md`。

### B1 Typed Scope Subtraction — **SUPERSEDED**

> ⚠️ **SUPERSEDED by Direction B.** 旧 B1 描述的是 read-authorization 语义
> （canonical scope 作为 read authorization，ExplorationPolicyEvaluator 作为 read
> decision owner）。Direction B 已决定 read governance 降级为 telemetry。
>
> - 旧 B1 的 PASS / `ENFORCED + PROVEN` 结论**已失效**。
> - 旧 B1 的目标结构、测试数字、验收条件和”允许提交 checkpoint”许可**均不再有效**。
> - 历史内容（实施细节、关闭证据、cannot-bypass test 清单）见：
>   `docs/plans/phase-b0-scope-boundary-characterization.zh-CN.md`、
>   `docs/plans/__CHECKPOINT__direction-b-b0-rebase-assessment.md`、
>   Git history（branch `codex/recovery-origin-requirements`）。
> - **不得作为 Direction B / B1 的实现或验收依据。**

## Phase C：Context Boundary

Assessment、subtraction implementation、deterministic gates、README 与 A3
controlled real-case 均已完成并通过。

目标结构：

```text
ledger facts + typed artifact refs + verified lineage
-> bounded ArtifactIndex / LineageQuery
-> ContextPackage
```

filesystem 只验证已知 artifact 的存在和 hash，不通过目录扫描猜测业务 evidence。不得建设通用 graph platform。

Assessment 结论：

- `AgentEvidenceIndex` + `AgentOutputLineageVerifier` 已是 routed typed evidence 的正确内核；
- 当前缺口是 `RepoScopeGovernance` 在 context build 中扫描 workspace、按 requirement
  与文件名猜测候选、读取 seed content 并注入 prompt；
- 下一步只允许一次 subtraction implementation：删除这条未路由 context source，
  收紧 generic raw ref 展开，同时保留 ledger facts、typed artifacts、actual telemetry
  与 read-reuse contract；
- 不新增 Artifact Graph、Evidence Registry、Context Engine 模块、query DSL 或状态机；
- implementation 已完成，deterministic gates 与独立 review 均 PASS；
- README 首次运行暴露 schema-level result 缺口；bounded correction 保持 strict
  parser、禁止默认补字段和语义 retry，并已由 deterministic tests 证明；
- README 重跑与 A3 多文件 controlled real-case 均达到
  `COMPLETED + ACCEPT + benchmark PASS`；
- Phase C context boundary 为 `PASS / ENFORCED + PROVEN`。

## Phase D：Handler 收敛

Phase C 已 PASS。Phase D 已完成只读 assessment、四个 bounded subtraction slice、
历史真实 REJECT sidecar replay 和全部 12 个 concrete handler 复核。

目标不是机械地把“68 个类”改成固定数量 executor，而是：

- node executor 只取 typed input、调用 domain service、写 typed result；
- contract、scope、context、review/gate、validation、delivery 各有唯一 owner；
- handler package 不再承载大块 policy、filesystem discovery 或 projection；
- 删除的旧逻辑多于新增 glue code；
- workflow/contract/handler consistency tests 和 full reactor 保持绿色。

Assessment 与首切片：

- checkpoint：
  `docs/plans/__CHECKPOINT__phase-d-handler-convergence-assessment.zh-CN.md`；
- 当前有 12 个 concrete handler；assessment 不预设最终数量；
- 首个已证明重复 owner 是 `HumanReviewGateHandler` 的本地 decision polarity
  集合与既有 `StageOutcome`；
- 本地 positive/negative 集合已删除，handler 只消费
  `StageOutcome.fromDecision`；
- `PASS -> READY` 与详细拒绝 `REJECT_CONTEXT_INSUFFICIENT -> revise`
  均有 regression/cannot-bypass test；
- targeted tests 63/0、`dual-runtime` 443/0、full reactor 9/9 modules /
  1,131 tests / 0 failures；
- production 净减少 4 LOC；
- 首个 slice 已提交为 `437b6e2`。

第二个 bounded subtraction slice：

- `ReviewDecisionHandler` 不再自行扫描、解析和校验 routed contract sidecar；
- `AgentEvidenceIndex.latestDecisionFromRefs` 成为 routed decision evidence 的唯一
  read owner，并返回 typed missing/parse/lineage/missing-decision failure；
- invalid latest routed sidecar fail closed，不回退较旧 sidecar；
- `ReviewDecisionHandler` production LOC 减少 53 行；
- targeted routing tests 75/0，full reactor 9/9 modules /
  1,132 tests / 0 failures；
- 第二个 slice 已提交为 `2f26cf5`；
- README controlled real-case
  `docs/testing/real-case-reports/20260727T140143Z/report.md`
  在干净 SHA 上达到 `COMPLETED + ACCEPT + benchmark PASS`；
- real-case 中 `design.decision` 从 strict sidecar 路由为
  `READY_FOR_ARCH_REVIEW`，`plan.decision` 实际覆盖
  `NEEDS_REVISION -> NEEDS_REVISION -> READY_TO_IMPLEMENT`，
  `acceptance.decision` 为 `ACCEPT`；所有结果均记录
  `source=contract-sidecar`，无 `CONTRACT_SIDECAR_*` failure；
- target diff 仅 `README.md`，复验 `npm test` 通过；
- 第二个 slice 达到 `ENFORCED + PROVEN`。不得据此自动启动第三个 slice。

第三个 bounded subtraction slice 已达到 deterministic PASS：

- 目标 handler：`FailureClassifyHandler`；
- acceptance failure facts 的唯一来源必须是 ledger-routed strict contract
  sidecar，经 `AgentEvidenceIndex.latestDecisionFromRefs` 解析和 lineage 校验；
- 删除 `NodeInstance.result` 中 `decision`、`summary`、`contract_gaps`、
  `out_of_scope_files`、`missing_tests`、`context_gaps` 与 `category_hint`
  对分类的 authority；
- 缺失或无效 sidecar 必须 fail closed，不能默认成 `REJECT`；
- `FailureArbiter`、workflow definition、classification 规则和 artifact
  writer 均不修改；
- cannot-bypass tests 已证明：
  - legacy result map 不能在缺少 sidecar 时驱动 classification；
  - legacy result map 不能覆盖有效 sidecar；
  - sidecar 缺少 decision 时返回 `INVALID_CONTRACT_SIDECAR`，且不会调用
    classification artifact writer；
- `FailureClassifyHandler` production LOC 从 308 降至 297；
- targeted tests 43/0，full reactor 9/9 modules / 1,135 tests / 0 failures；
- deterministic maturity 为 `ENFORCED`：缺失或无效 typed evidence 会在
  `FailureArbiter` 与 workflow classification route 前失败；
- 历史真实 REJECT sidecar
  `acceptance-reject-20260709.contract.json` 已通过当前 ledger publication、
  `ReviewDecisionHandler`、`FailureClassifyHandler` 与 workflow edge 重放；
- replay 产生 typed `VERIFICATION_INSUFFICIENT`，激活
  `implement.claude_self_test_report` / `iter-002`，第三个 slice 达到
  `ENFORCED + PROVEN`；未为概率制造 REJECT 消耗 provider token。

第四个也是最后一个 bounded subtraction slice：

- `HumanReviewGateHandler` 的 input artifact summary 不再直接 parse 和验证
  routed sidecar，而是消费 `AgentEvidenceIndex.validatedDocument`；
- tampered published sidecar 在 review packet/recommendation 写入前 fail closed；
- targeted tests 93/0，full reactor 9/9 modules / 1,137 tests / 0 failures；
- production 净减少 5 LOC，未新增 module、registry、engine 或 framework。

最终 12-handler 复核：

- `ArtifactValidateHandler`、三个 delivery handler、`FailureClassifyHandler`、
  `HumanReviewGateHandler`、`ReviewDecisionHandler` 未发现残余重复 authority；
- `ContextBuildPackageHandler`、`ContractCompileHandler`、
  `PromptGenerateNextStageHandler`、`ReportGenerateFinalHandler` 与
  `AcpxAgentTaskHandler` 仍有规模或专用读取复杂度，但没有证据证明存在第二控制
  owner，不因 LOC 或“可以统一”继续拆分；
- handler 数量保持 12。Phase D 的目标是 owner 和职责收敛，不是达到预设数量；
- Phase D 状态：`PASS / ENFORCED + PROVEN`。当前未激活新的 phase。

## 历史锁定范围（旧 B1 checkpoint 时代的许可，现已失效）

> ⚠️ **SUPERSEDED**: 以下内容是旧 B1 checkpoint（read-authorization 语义）时代的
> 许可范围。**该许可现已全部失效。** 不得据此启动或提交 Direction B/B1。
>
> 当时的许可：审查并提交旧 B1 checkpoint（read-authorization 语义下的）；独立评估下一 phase readiness。
>
> 当时的禁止：在独立 readiness assessment 前启动 Phase C Context Boundary 或 Phase D handler
> 重构，以及多轮 review、多 Agent proposal merge、自动 rollback、policy engine、预算/provider
> 优化、HTTP/runtime 扩张、tool-call 前 broker 或 write-scope lifecycle。
>
> 以上仅为历史许可记录；当前 objective 以本文顶部的 Phase C assessment
> 状态为准。
