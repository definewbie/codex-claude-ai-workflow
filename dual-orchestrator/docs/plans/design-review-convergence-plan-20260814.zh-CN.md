# Design Review Convergence 修复计划

日期：2026-08-14
状态：`COMMITTED / DETERMINISTIC PASS / PROJECTED + VALIDATED / DIFF REVIEW PASS / P2-1 DELTA REVIEW PASS / NOT PROVEN`

## 1. 唯一目标

本 checkpoint 只解决 captcha-auth v13 暴露的 design review 串行发现问题：

```text
Claude 对最新 must-fix 做局部加强
→ 新机制产生新的 failure mode
→ Codex 下一轮才发现
→ 3 次 semantic revision 耗尽
→ 实现尚未开始，workflow 进入 WAITING_FOR_RECOVERY
```

它直接解除当前 P0 产品交付阻塞，不是新的 memory、review 或 workflow 平台。

验收目标不是“让 reviewer 更宽松”，而是：

1. Claude revision 产出一份完整替换设计，而不是在旧设计上追加局部补丁；
2. Codex 在当前轮对完整 typed design 与新引入机制做一次边界完整、成本成比例的审查；
3. context 中实际可用的 prior must-fix 是审查起点而非上限；即使没有 cumulative verdict，当前可识别的 blocking set 也应从完整当前设计中一次性给出；
4. 不依赖 persistent session、额外 Read 工具或空的 workstream-memory 字段才能理解当前设计；
5. 不提高 revision budget，不用更多调用掩盖 convergence 缺口。

## 2. Captured 事实

Workstream：`ws-20260814T103857Z-AEHF`
Workflow：`agent-lifecycle-terminal-delivery@4`
结果：`WAITING_FOR_RECOVERY / semantic_revision_limit`
产品实现：未开始，worktree clean。

四轮 design review：

| Review run | 结果 | 本轮主要缺口 |
|---|---|---|
| `run-1786707623259` | `NEEDS_REVISION` | OTP/Captcha 原子消费、限流唯一 guard、delivery failure 补偿缺失 |
| `run-1786707787324` | `NEEDS_REVISION` | OTP 状态谓词与状态机矛盾、guard 重复/顺序错误、resend 语义不完整 |
| `run-1786707907696` | `NEEDS_REVISION` | 并发 resend 可产生多个 ACTIVE OTP、登录 alias 可绕过限流 |
| `run-1786708014731` | `NEEDS_REVISION` | 新增 partial unique index 后，崩溃遗留 PENDING 会永久阻止 resend |

12 个 Agent run 全部 `completed / exitCode=0`。不存在 timeout、permission、parser、correction、session closure 或 runtime retry 根因。

### 2.1 已确认的 context projection 缺口

1. `workstream-memory.json` 中 `priorVerdicts=[]`、`openIssues=[]`、`lastStageOutcome={}`、`decisions=[]`。
2. `WorkstreamMemoryProjection.appendReviewEvidenceBrief()` 能渲染 prior verdict/must-fix，但本次没有 producer 写入这些字段。
3. 尽管 memory 为空，投影仍写出 `Incremental acceptance from orchestrator memory`，造成 projection 语义过度声明。
4. architecture-review context 对当前 `design-claude.contract.json` 只投影 summary、must-fix、open-risks、changed-files、verification；真正承载架构方案的 `checks` 与 `evidence` 未进入 prompt，只提供 sidecar/raw-output 路径。
5. 本次 Codex 依靠 persistent session 和额外读取 raw sidecar 完成审查；这属于可用行为，不是稳定的 context boundary。
6. `architecture_review` profile 没有 `iteration_history`；session 重连后 reviewer 只看得到当前 design，无法从 authoritative iteration documents 看见前序 revision sequence。
7. revision design prompt 内嵌的是 prior review contract，不是上一版 design 全文；上一版 design 当前只可由 persistent session 保留。因此“完整替换”是输出纪律，不得描述成 fresh session 下已经具备完整 prior-design 输入的 deterministic guarantee。
8. prior review contract 也不在下一轮 Codex review node 的 routed input refs 中；在不建设 memory producer、不修改 workflow graph 的约束下，fresh reviewer 只能从完整当前 design 与 bounded iteration reasons 重新推导 blocker。prior must-fix 仅在实际可用时作为 seed，不是必达事实。

## 3. 问题分类与 owner

### Existing model

- Definition：bundled workflow 定义 design/review/decision loop 与 `max_revisions=3`。
- Instance：当前 workstream/node/iteration documents。
- Execution：8 个 design/review Agent runs 及其 process/outcome facts。
- Iteration：`iter-003` 到 `iter-005` 持有 semantic revision 事实与 reason。
- Artifact：`design-claude.contract.json` 是当前设计的 typed authoritative artifact；`design-codex-review.contract.json` 是当前 review verdict。
- Projection：context package、Review Evidence Brief 和 prompt 只消费 typed artifacts/iteration ledger。

### Authoritative owners

- 当前设计内容：`design.claude_design` 的 validated `AgentOutputContractDocument`。
- 当前 review verdict：`design.codex_arch_review` 的 validated `AgentOutputContractDocument`。
- revision 次数与路由：现有 iteration documents + `RoutingEngine/RevisionTracker`，本次不改。
- reviewer context：`ContextBuildPackageHandler` + `ContextPackageSectionRenderer`。
- prompt discipline：`design-claude.md` 与 `design-codex-review.md`。

### New concept needed

`No`。

不新增 closure matrix schema、memory entity、review registry、validator engine 或 lifecycle 状态。使用现有 `checks`、`evidence`、`must_fix`、`open_risks`、iteration reason 与 validated sidecar 即可。

## 4. 根因

### R1：当前完整设计没有被直接投影给 reviewer

设计的核心机制被 Claude 写入 typed `checks/evidence`，但 architecture-review context 只给摘要和路径。reviewer 必须自行读取 raw sidecar，context 完整性依赖工具调用与 session 状态。

### R2：revision 被表述为“关闭最新 must-fix”，不是“重新综合完整设计”

Claude prompt 只要求 implementation-ready design，没有要求 revision 替换旧设计、重新评估新机制的完整失败面。结果是每轮添加状态、谓词和索引，却没有同步推演 crash/restart/rollback。

### R3：review directive 明确偏向增量审查

当前 directive：

```text
Confirm prior must-fix against this round's diff and acceptance criteria; surface new risks.
```

它鼓励“先验上一轮，再找下一层”，没有要求：

- 当前可见 blocker 一次性封顶；
- 对本轮新增状态/索引/锁/外部副作用做完整 lifecycle sweep；
- 先判断能否删除复杂机制，而不是继续在该机制内加补丁；
- 不得把 contract 未要求的企业级能力升级为 blocking requirement。

### R4：Review Evidence Brief 对空 memory 作了不真实暗示

projection 能力存在不等于 evidence 存在。空 `priorVerdicts/openIssues` 时仍声称来自 orchestrator memory，会让 prompt 和测试误以为累计 review fact 已闭合。

### 4.1 首轮同行评审闭合

| Finding | 处置 |
|---|---|
| F1 / P1 | `ContextProfiles.java` 与 legacy profile switch 纳入实施清单，`architecture_review` 两条装配路径都增加 `iteration_history`。 |
| F2 / P1 | 当前完整 design 使用 architecture-review 专用、budget-exempt typed subject 投影；禁止落入 `input_artifacts` 的 `SUMMARY` 降级，也禁止修改跨 profile 共用的通用投影。 |
| F3 / P2 | `DECISION_ONLY` 的 bounded projection 保留 reason，并以降级测试钉死。 |
| F4 / P2 | 明确 fresh Claude session 不保证拥有上一版 design 全文；complete replacement 是 prompt discipline，不冒充 deterministic input guarantee。 |
| F5 / P2 | 明确 fresh Codex reviewer 不保证拥有逐条 prior must-fix；从完整当前 design 与 iteration reasons 重新推导 blocker。 |
| F6 / P2 | T4 降级为 soft prompt regression；只有 T1–T3/T5 属于可强制的 projection/cannot-bypass 证据。 |

## 5. 单一原子实现

### 5.1 完整投影当前 typed architecture review subject

修改：

- `dual-runtime/.../ContextPackageSectionRenderer.java`
- `dual-runtime/.../ContextBuildPackageHandler.java`
- `dual-runtime/.../ContextProfiles.java`

行为：

1. 为 `architecture_review` 增加专用的 `Required Architecture Review Subject` 投影；它只解析当前 routed、hash/lineage validated design sidecar，并完整输出 decision、summary、checks、evidence、must-fix、open-risks、verification 与 sidecar ref；
2. 该 typed subject 与现有 `Required Review Subject` 采用相同的 budget-exempt 必达语义：不进入 `input_artifacts` 的 1,000-token `SUMMARY` 降级，也不受 governed section 的 soft/total allocation 剥夺；其实际 token 成本必须计入 context metadata/header，不能把必达证据伪装成零成本；
3. 专用投影只在 `architecture_review` profile 接线，不修改被 implementation/acceptance/plan-review/product-review 共用的 `appendAgentContractFields()` 语义；
4. 只展开当前 review subject，不展开全部历史 raw output，也不投影 transcript/stderr/unvalidated candidate；
5. `ContextProfiles.architectureReview()` 与 `ContextBuildPackageHandler` legacy fallback 同步加入既有 bounded `iteration_history` section，避免 governed/legacy profile 漂移；
6. bounded `DECISION_ONLY` iteration projection 保留 id/trigger/target/**reason**，最多 5 条；若预算压力触发降级，reason 仍不得消失。该语义同步适用于使用 `DECISION_ONLY` 的 sibling profiles，避免同一 projection 在不同 profile 漂移；
7. 不增加新配置键，不改变 iteration ledger owner，不把历史 output 全量塞入 context。

这不是“更多 context 默认 dump”。它只把当前 typed review subject 从 pointer-only 修正为完整 validated projection，并给 session 重连提供 bounded revision sequence。

### 5.2 Claude revision replacement discipline

修改：

- `dual-adapter-acpx/.../prompts/design-claude.md`

新增约束：

1. 若输入包含 `NEEDS_REVISION`，输出必须是当前设计的完整替换版本，不能只描述 delta；
2. 实际输入中存在 prior must-fix 时，它是最低修复集合，不能成为审查边界；若 fresh session 没有上一版 design 全文，必须依据 TaskContract、当前 review evidence 与可用 iteration reason 重建一份自洽的完整设计，不得假装看到了未提供的历史内容；
3. 每当新增或改变 state、index、lock、transaction、queue、cache、external side effect 或 compensation，必须在现有 `checks/evidence` 中覆盖适用的：
   - normal completion；
   - concurrency；
   - timeout/provider failure；
   - process interruption/crash；
   - restart/recovery；
   - transaction rollback/conflict；
   - cannot-bypass test；
4. 优先删除不必要的状态/机制；不得为了关闭一条 finding 自动增加平台能力；
5. 对本任务不适用的维度应说明为什么不适用，不得编造实现要求。

不新增 JSON 字段；上述内容进入已有 `checks/evidence/open_risks`。

### 5.3 Codex complete-current-design review discipline

修改：

- `dual-adapter-acpx/.../prompts/design-codex-review.md`
- `dual-runtime/.../WorkstreamMemoryProjection.java`

行为：

1. review subject 是当前完整 typed design，不是 latest diff；
2. prior must-fix 在 context 中实际可用时仅作为 closure seed，不能限制本轮审查范围；fresh reviewer 不得假设 memory 中一定存在 prior verdict，而应从完整当前 typed design 与 bounded iteration reasons 重新推导 blocker；
3. 在返回 `NEEDS_REVISION` 前，对当前设计新增/改变的机制完成同一 lifecycle sweep；
4. 当前能够从已提供 evidence 推导的 blocking findings 应一次性列出，不故意留到下一轮；
5. 必须评估是否有删除机制/简化状态模型的更小方案；
6. finding 必须锚定 TaskContract 或当前设计自称的不变量，不能把未授权的多实例、分布式、灾备或企业级要求升级为 blocker；
7. 若 evidence 不足，使用现有 `missing_evidence`，不得假装已验证；
8. memory 没有 prior verdict/open issue 时，Review Evidence Brief 必须明确写 `no cumulative review verdict recorded`，不得声称已有 incremental acceptance memory；
9. memory 有事实时继续投影，但 memory 不成为当前设计的第二 owner。

## 6. 明确不改

- 不改三个 workflow graph/version/edge。
- 不改 `max_revisions=3`、`RevisionTracker` 或 `RoutingEngine`。
- 不改 Agent result JSON schema、strict parser、validator 或 bounded correction。
- 不新增 memory writer、memory platform、review registry、generic checklist engine。
- 不新增 retry、resume、human override 或 recovery action。
- 不把 v13 consumed workstream 原地 resume。
- 不更换、识别或比较 provider 后端模型。

`workstream-memory.priorVerdicts` 没有 producer 是已确认事实；本 checkpoint 只修 projection honesty，不顺势建设 memory 写入链。因为当前 P0 所需 facts 已存在于 current typed design 与 iteration ledger，扩建 memory producer 不是完成产品的必要条件。由此保留的明确边界是：fresh Claude revision 不保证收到上一版 design 全文，fresh Codex reviewer 不保证收到逐条 prior must-fix；二者必须依赖本轮完整 replacement design、TaskContract、当前 review evidence 与 bounded iteration reasons 收敛。若后续 captured replay 证明这些事实仍不足，再回到输入边界分析，不能在本 checkpoint 偷建 memory producer。

## 7. Cannot-bypass 与 deterministic tests

### T1：current design full typed projection

在 `ContextBuildPackageHandlerTest` 构造 validated design sidecar：

- summary 很短；
- `checks/evidence` 总量必须超过 architecture-review `input_artifacts` 的 1,000-token soft budget，并含只有完整 sidecar 才有的 concurrency/crash/rollback facts；
- architecture-review context 必须逐字包含这些 typed facts；
- metadata/header 必须记录该必达 subject 的真实 token 成本，不能把预算豁免伪装成零成本；
- production `design.codex_arch_review` 缺少 validated design subject 时，context build 必须 fail-closed，reviewer 不得启动；
- 不包含 transcript/stderr/unvalidated candidate 内容；
- sidecar ref 保留。

若实现仍是 summary-only，该测试必须失败。

### T2：bounded revision history resilience

构造三条 design revision iteration documents，断言 architecture-review package 包含 iteration id、trigger、target 与 reason；再触发 `DECISION_ONLY` 降级，证明最多 5 条且 reason 仍保留。governed profile 与 legacy fallback 都必须声明该 section。

### T3：empty-memory projection honesty

`priorVerdicts=[]/openIssues=[]` 时：

- 不出现 `Incremental acceptance from orchestrator memory`；
- 明确出现 `no cumulative review verdict recorded`；
- acceptance criteria 仍可作为 contract baseline；
- 不伪造 prior must-fix。

### T4：soft prompt discipline regression assertions

在 `AgentPromptRendererTest` / `PromptTemplateConsistencyTest` 断言：

- Claude revision 是 complete replacement；
- Codex review 是 complete current design review；
- 两者均包含新增机制 lifecycle sweep；
- reviewer 包含 proportionality/simplification 和 contract-grounding；
- 不出现提高 revision 次数、自动 retry 或放宽 strict contract 的文案。

T4 只证明模板纪律未回归，不是 semantic enforcement，也不得单独计入 cannot-bypass 成熟度。

### T5：captured deterministic context replay

使用 v13 captured final design 的等价 fixture与三条 iteration reason，离线渲染最终 architecture-review prompt，证明：

- partial unique index 的完整 typed evidence 已直接进入 prompt；
- 三轮 revision sequence 可见；
- reviewer 被要求检查 crash/restart/rollback；
- 不需要 provider、不读取原始 session 目录、不修改 captured ledger。

### Verification

1. focused tests；
2. full Maven reactor；
3. `git diff --check`；
4. sibling sweep：`design-claude` / `design-codex-review` 在 contract/full/terminal-delivery 三个 workflow 中共用同一模板，不能出现 variant 漂移；
5. 独立代码级 diff review PASS 后才提交。

## 8. 成熟度与真实验证

- 文档：`DESIGNED / PEER REVIEW PASS`。
- 实现：完成，尚未提交。
- deterministic prompt/context tests：`PROJECTED / VALIDATED`。
- 不能把 prompt 文案称为 `ENFORCED`；harness 只能强制结构与路由，不能确定性证明架构推理正确。
- 独立 diff review PASS 后，先对 v13 final design 做一次且仅一次 captured exact review replay；目标是 Codex 在第一轮同时识别 partial-index stale-PENDING 与其他当前可推导 blocker，或给出 contract-grounded 更小方案。
- replay PASS 只证明 captured boundary；新的 fresh captcha-auth full workstream 到达 Design Approval 后，才能把 convergence 对该产品样本标为 `PROVEN`。
- v13 workstream 保持 `FAIL / CONSUMED`，不 resume，不创建隐藏的第五次 design revision。

### 8.1 实施验证结果

- focused：`ContextBuildPackageHandlerTest`、`WorkstreamMemoryProjectionTest`、`PromptTemplateConsistencyTest`、`AgentPromptRendererTest` 全部通过；
- full Maven reactor：`1245 tests / 0 failures / 0 errors / 0 skipped`，9/9 modules `BUILD SUCCESS`；
- `git diff --check`：clean；
- T1 使用超过 12KB 的 validated checks/evidence，证明 1,000-token summary budget 无法剥夺必达 subject；
- T1 反向测试证明缺少 validated design subject 时 reviewer context fail-closed；
- T2 以超过 500-token soft budget 的真实 governed fixture 触发 `DECISION_ONLY`，并由治理表断言 `decision_only / yes`；最多 5 条且 reason 保留，sibling code sweep 确认 legacy section list 同步声明 iteration history；
- T3 证明只有 acceptance criteria、没有 prior verdict 时不会冒充 incremental acceptance memory；
- T4 只作为 prompt discipline regression；
- T5 使用 12KB captured-scale context 离线渲染最终 reviewer prompt，typed mechanism evidence、三轮 iteration sequence 与 lifecycle directives 均在场。

当前成熟度只到 `PROJECTED / VALIDATED`。首轮独立代码级 diff review 为 `PASS / P0=0 / P1=0 / P2=3`；其中 P2-1 揭示原测试未真正触发降级。核对生产路径后确认仅加长 fixture 不足以触发 `DECISION_ONLY`，因此补充了单节超预算时按既有 `DECISION_ONLY` renderer 重渲染的最小 delta，并用治理表断言真实分支。该 delta 独立复审为 `PASS / P0=0 / P1=0`，选择性提交条件满足；captured exact review replay PASS 后才能声明该 captured boundary 已证明，fresh captcha-auth workstream 通过后才能对产品样本声明 `PROVEN`。

## 9. Stop Conditions

出现任一条件立即停止实现并回到设计：

1. 需要新增 JSON schema/validator 才能“检查模型是否真的思考”；
2. 需要提高 revision budget 或增加 Agent 调用；
3. 需要扫描 session/run 目录猜测 historical verdict；
4. 需要新 memory producer、review entity、registry、engine 或通用 checklist 平台；
5. context 变成历史 output 全量 dump；
6. reviewer 规则开始要求 TaskContract 未授权的分布式/企业级能力。

## 10. End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | 当前 validated `design-claude.contract.json` + iteration documents |
| Proven by | sidecar hash/lineage validation、context deterministic projection、prompt contract tests |
| Consumed by | `design.codex_arch_review` 当前 turn |
| Enforced before | review verdict 生成前，当前完整 typed design 已进入 prompt；结构化 verdict 仍经 strict parser/validator |
| Failure route | evidence 不足→`missing_evidence`；设计存在 blocker→`NEEDS_REVISION`；budget 仍由既有 semantic limit fail-closed |
| Cannot-bypass test | T1–T3、T5 证明 typed projection、bounded history 与空-memory honesty 不可绕过；T4 仅为 soft prompt discipline 字符串回归，不声称强制模型完成 lifecycle reasoning |
| Residual raw path | raw sidecar 可用于 spot-check，但不再是理解当前设计的唯一途径；transcript/stderr 不参与路由 |

## 11. 实施顺序

1. 首轮 CC 只读架构评审：`WARN / P0=0 / P1=2 / P2=4`；
2. 本修订关闭 F1–F6 后，执行一次严格限定为 delta 的 CC 只读复审；
3. 复审 PASS 后，作为一个 checkpoint 实施 5.1–5.3 与 T1–T5；
4. focused + reactor + diff-check；
5. CC 代码级 diff review；
6. 选择性提交 clean SHA；
7. 制定 captured exact review replay Gate；
8. replay PASS 后再制定新的 captcha-auth completion Gate。
