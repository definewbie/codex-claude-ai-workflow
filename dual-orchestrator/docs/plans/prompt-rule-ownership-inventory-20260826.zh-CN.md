# Prompt / Rule Ownership Inventory

日期：2026-08-26
状态：`DELETION CHECKPOINT COMPLETE / INDEPENDENT DIFF REVIEW PASS / VALIDATED`

## 1. 目标与结论

本清单覆盖 10 个 production prompt、1 个共享 review template、`AgentResultPromptContract`、对应 parser / validator，
并复核 retrospective 中优先级最高的 D1 legacy owner。目标不是按重复行数删 prompt，而是回答每条规则由谁声明、谁
强制、删除后什么行为会改变。

结论：

1. **当前没有一组 D2/D3 prompt 规则满足直接删除条件。** strict parser / validator 能拒绝错误结果，但不能告诉模型
   应生成什么；permission profile 也没有对所有节点形成完整文件写保护。删除这些生成协议会减少提示词，却会提高无效
   occurrence 风险，且没有 same-case 或 deterministic counterfactual 证明质量不退化。
2. **D1 的原范围过窄。** `FileLedgerStore.approveDesign()` 不是唯一遗留写入口；同类 legacy design-loop 的 prepare、
   complete、fail、approve、revise 共 11 个 public method 全部 production-unreachable，只在
   `FileLedgerStoreTest` 中被调用。只删 `approveDesign()` 会留下同一旧状态机的其余 writer。
3. **下一纯删除 checkpoint 应删除整个 legacy design-loop writer family，而不是先删 prompt。** 该边界还包括仅由
   这些入口消费的 dead DTO/control types 与 legacy document factory/mutator；保留生产仍使用的
   `initializeWorkstream()`、`loadStatus()`、`appendTimeline()`，以及它们的初始化/兼容查询模型和测试。

## 2. 清点范围

### 2.1 Prompt source

| Source | 行数 | 节点角色 | 主要规则族 |
|---|---:|---|---|
| `acceptance-codex-review.md` | 42 | acceptance reviewer | ledger ownership、只读 review、evidence hierarchy、stdout contract |
| `design-claude.md` | 42 | design producer | ledger ownership、禁止实现、revision completeness、lifecycle coverage、read reuse |
| `design-codex-review.md` | 43 | architecture advisory | ledger ownership、完整 blocker 扫描、scope restraint、evidence gaps |
| `implement-claude-self-test.md` | 46 | self-test producer | ledger ownership、验证选择、依赖限制、hygiene、typed report |
| `implement-claude.md` | 51 | implementation producer | ledger ownership、变更边界、验证分工、依赖限制、hygiene |
| `intake-claude-brainstorm.md` | 43 | contract producer | deny-all intake、obligation coverage、typed scope、revision convergence |
| `intake-codex-product-review.md` | 44 | contract reviewer | product completeness、protocol exclusion、evidence hierarchy |
| `plan-claude.md` | 43 | plan producer | 只读规划、禁止执行验证、scope restraint、read reuse |
| `plan-codex-review.md` | 34 | plan reviewer | 只读 review、plan completeness、evidence hierarchy |
| `stage-claude-handoff.md` | 28 | handoff producer | ledger ownership、fresh-session continuation contract |
| `contract-review-principles.md` | 12 | shared intake review template | intent、coverage、scope authority、human boundary |
| **合计** | **428** | | |

`AgentResultPromptContract.java` 另负责所有节点共用的 strict envelope 示例、closed fields、allowed decisions、scope、
revision target uniqueness、reread reason 与 acceptance remediation 投影。

### 2.2 机械 owner

| Boundary | Declared / enforced by | 成熟度 |
|---|---|---|
| JSON grammar | `AgentResultEnvelopeParser`：duplicate detection、unknown property、trailing token、scalar coercion、EOF typed diagnostic | `ENFORCED + PROVEN` |
| Envelope / node semantics | `AgentResultEnvelopeValidator`、`ContractRegistry`、proposal/revision validators | `ENFORCED + PROVEN` |
| Artifact lineage / scope | validated sidecar/reference、TaskContract validators、change-scope evaluation | `ENFORCED + PROVEN` |
| Intake no-tool execution | `contract_discovery` / correction `denyAll` | `ENFORCED` |
| Observe/review automatic permission | ACP read/search auto-approval、default deny policy | `ENFORCED` for ACP permission decisions, **not a tool-visibility whitelist** |
| Implementation authority facts | `ChangeBoundaryAuthoritySnapshot` fingerprints selected task-contract/workstream/workspace facts | `ENFORCED` for the selected facts only |
| Entire `.agent/` tree write protection | 无完整 filesystem policy / post-run tree guard | `NOT MODELED` |
| Prompt generation quality | prompt wording + model behavior | `PROJECTED`; cannot be upgraded by parser rejection alone |

## 3. Rule-family 判定

### R-P1 Ledger write policy

重复文本：不得写 `.agent/`、只有 orchestrator 写 ledger、结果写 stdout。

- intake correction 的工具副作用由 deny-all 机械阻止；observe/review 的 execute permission 默认拒绝。
- implementation/self-test 使用 `implement` profile（approve-all）。现有 snapshot 只保护少数 change-boundary facts，
  不保护整个 `.agent/` 树。
- “结果写 stdout”是 ACP assistant-message transport 与 orchestrator artifact persistence 的私有协议，不能由模型常识推导。

判定：`DUAL_L2 + UNENFORCED_SAFETY`，**保留**。未来只有完整 `.agent/` write guard 达到 `ENFORCED` 后，才可按
profile 删除已机械覆盖的句子；不能现在全局去重。

### R-P2 Stage side-effect boundary

包括 design/plan/review 不修改文件、plan 不执行 build、implementation 不跑 broad verification、self-test 不安装依赖。

- permission profile 限制一部分 execute 行为，但 `--allowed-tools` 是免确认集合，不是工具可见性白名单。
- 这些规则还表达 workflow node ownership：plan 与 self-test 的职责分工、implementation 与 verification 的成本边界。
- 当前没有 typed postcondition 能证明 design/plan 完全未改业务文件，也没有 counterfactual 证明删除后不增加副作用。

判定：`DUAL_L2 + UNENFORCED_SAFETY`，**保留**。

### R-P3 Review and evidence discipline

包括完整 blocker 集、不得扩大企业级需求、优先 evidence brief、缺证据写 `missing_evidence`、self-test 不是最终证明。

- typed fields 与 validators 保证字段形状，不保证 blocker 集完整或判断质量。
- captcha-auth captured replay 已反证“完整上下文 + 强 prompt 可保证单 turn blocker completeness”；human authority 已成为
  最终 owner，但 advisory review 仍需知道评价目标。

判定：`DUAL_L2 + HUMAN_L3`，**保留**；不能把 human final authority 误解为 reviewer 无需任务说明。

### R-P4 Contract proposal / revision semantics

包括 binding field coverage、typed scope exhaustive、obligation mapping、revision replacement/patch discipline、target uniqueness。

- validator / merger 是最终 enforcement owner。
- prompt 是同一 typed interface 的 producer projection。只保留 validator 而删除字段语义，会把可预防的 provider 失败
  推迟到后置拒绝；这不是 subtraction，而是把生成成本变成 correction/recovery 成本。

判定：`DUAL_L2`，**保留**。可以优化渲染方式，但不能以“validator 已强制”为由删除 interface 说明。

### R-P5 Strict final JSON presentation

包括恰好一个 JSON object、无 prose/fence、closed objects、exact keys/types、allowed decisions 与 pretty-printed example。

- parser 是 grammar authority，single-fence normalization 只处理已批准的 presentation 形态。
- ACP 只提供 assistant message，没有可依赖的 native structured-output channel。
- v15 已在明确 closed-object 提示下仍输出 root EOF；这证明 soft wording 不能强制合规，不证明删除 wording 没有成本。

判定：核心“输出渠道 + schema example”是 `DUAL_L2`，**保留**。重复的自然语言可成为未来 A/B 候选，但当前无
反事实证据，不进入纯删除 checkpoint。

### R-P6 Read reuse / repository hygiene

包括优先 Working Memory、重复读取需理由、清理 test byproducts、不得创建独立报告文件。

- reread reason 有 typed contract 和 telemetry，但合法重复读取不再直接令节点失败；规则主要用于成本与证据质量。
- hygiene/change-scope 能发现部分 byproduct，不能证明所有 prompt 句子已被完整替代。

判定：`DUAL_L2 + partially ENFORCED`，**保留并等待单独 counterfactual**；不与 D1 混合。

### R-P7 Public knowledge / eval-only

本轮未找到可以独立删除且不改变 profile 目标的整组 `PUBLIC_KNOWLEDGE` 或 `EVAL_ONLY` 文本。诸如“保持 scoped”、
“避免 unrelated refactor”看似通用，但它们同时投影 TaskContract/change-scope 的项目边界。没有 owner 映射时，不按文风
判断为冗余。

## 4. D3 `AgentResultPromptContract` 判定

`AgentResultPromptContract` 同时承担两个不同职责：

1. **可删候选表面**：parser/validator 已机械拒绝 unknown field、错误 primitive、非法 enum、重复 target；
2. **不可删 producer interface**：模型必须知道 key、type、closed item fields、allowed enum、revision base identity 和
   obligation target 规则，ACP 不会从 Java record 自动获得 native schema。

因此，“validator 已强制”只证明错误不会越过边界，不证明相关 prompt 可以删除。D3 需要先把每段文本与 rendered
schema 的信息增量做字段级 diff，再用 captured fixture 或 same-case counterfactual 验证 invalid occurrence、correction、
token 与语义质量均不退化。本轮不授权 D3 删除。

## 5. D1 sibling sweep：真实删除边界

### 5.1 Production call graph

`FileLedgerStore` 的 production 消费者只调用：

- `StartCommand -> initializeWorkstream()`；
- `StatusCommand`、`WorkstreamQueryService`、`OperatorWorkspaceRegistry -> loadStatus()`；
- `initializeWorkstream()` 内部及兼容路径使用 `appendTimeline()`。

以下 11 个 public methods 在 `dual-cli`、`dual-server`、`dual-runtime`、`dual-ledger-file/src/main` 中均无调用，
全仓 Java 调用只存在于 `FileLedgerStoreTest`：

1. `prepareClaudeDesignRun`
2. `loadPreparedClaudeRun`
3. `prepareClaudeDesignRevisionRun`
4. `prepareCodexDesignReviewRun`
5. `loadPreparedCodexReviewRun`
6. `completeClaudeDesignRun`
7. `failClaudeDesignRun`
8. `completeCodexDesignReviewRun`
9. `approveDesign`
10. `requestDesignRevision`
11. `failCodexDesignReviewRun`

它们连同 `openHumanDesignGate()`、legacy prompt builders、artifact-manifest/control helpers，构成同一旧 design-loop
state machine。正式 workflow 的 gate action owner 已是 `RoutingEngine.resolveOpenGate()` + `WorkflowFileLedger`；保留这些
writer 只会让同一 human/design state 有第二套可调用语义。

继续沿类型引用做 sibling sweep 后，以下生产类型也只服务该旧 state machine：

- `PreparedRunView`
- `RunDocument`
- `ArtifactManifest`
- `CodexReviewControl`
- `HumanGateDocument`

`CheckpointDocument`、`StageDocument`、`IterationDocument`、`WorkstreamDocument` 仍被 initialization/status compatibility
读取或创建，不能删除类型本身；但除 `CheckpointDocument.initial()`、`StageDocument.initialDesign()`、
`IterationDocument.initialDesign()` 与基础 workstream construction 外，其 legacy design transition factory/mutator 只有上述
dead methods 消费，也应在同一 checkpoint 删除。对应的 `CodexReviewControlTest`、
`CodexReviewControlRegressionTest`、`CheckpointDocumentTest` 中 legacy-only cases 也不应继续维护第二套语义。

### 5.2 为什么不能只删 `approveDesign()`

`approveDesign()` 与 `requestDesignRevision()` 对同一个 legacy human gate 写不同 transition；上游 prepare/complete
方法负责创建该 gate，下游 fail methods 写同一 checkpoint/timeline family。只删 approve 会：

- 保留 revise 与 review-completion 两条旧 writer；
- 保留 legacy prompt/artifact protocol；
- 让测试继续维护 production 不可达状态机；
- 不能满足 sibling owner closure。

因此 D1 的最小**完整**边界是 legacy design-loop writer family，而不是单一 method。

## 6. 下一纯删除 checkpoint

### 6.1 Proposed deletion scope

只删除：

- `FileLedgerStore` 中第 5.1 节的 11 个 public methods；
- 仅由这些 methods 消费的 private helpers、legacy prompt strings 与 imports；
- `WorkstreamPaths.currentHumanGateJson()`；
- 完全失去生产消费者的 `PreparedRunView`、`RunDocument`、`ArtifactManifest`、`CodexReviewControl`、
  `HumanGateDocument`；
- `CheckpointDocument`、`StageDocument`、`IterationDocument`、`WorkstreamDocument` 中仅由 legacy design-loop 消费的
  factory/mutator，保留 initialization/load 所需 record shape；
- `FileLedgerStoreTest` 中仅验证 legacy design-loop 的测试/fixtures；
- 完全服务 `CodexReviewControl` 的两组测试，以及 `CheckpointDocumentTest` 中 legacy-only factory tests。

明确保留：

- `initializeWorkstream()`、`loadStatus()`、`appendTimeline()`；
- 初始化、prompt-language settings、CLI/server compatibility status tests；
- `WorkflowFileLedger`、`RoutingEngine`、human gate production tests；
- 10 个 prompt、共享 template、`AgentResultPromptContract` 与 validators，全部不动。

### 6.2 Mechanical PASS / FAIL

PASS 必须同时满足：

1. production 与 tests 中 11 个 method 名零命中；五个 dead type 名零命中且源文件已删除；
2. legacy `CheckpointDocument` factories 与 stage/iteration/workstream transition mutators 零命中并已删除，
   initialization/load record shape 保持兼容；
3. legacy prompt marker（`DUAL_ARTIFACT_MANIFEST`、`human_design_review` 的旧 builder 路径）不再由
   `FileLedgerStore` 产生；
4. `StartCommand` initialization、`StatusCommand`、server status focused tests 通过；
5. workflow human approve/revise/reject/clarify/terminal actions 的现有 focused tests 通过；
6. full reactor 通过，`git diff --check` clean；
7. diff 只有删除与必要 import/test cleanup，不新增 fallback、compatibility adapter 或 replacement owner。

FAIL：任一 production caller 仍依赖 legacy method；初始化/状态查询退化；删除迫使新增 replacement；正式 human gate
行为或 frozen workflow load 退化。

### 6.3 回滚条件

如果 focused/full tests 证明仍有真实 production consumer，停止删除并恢复该 checkpoint；先把 consumer 分类为正式
workflow 还是仍受支持的 legacy compatibility surface。不能为让删除通过而增加另一层 adapter。

## 7. End-to-End Invariant

| 维度 | D1 deletion boundary |
|---|---|
| Declared by | 正式 human/design transition 由 `WorkflowDefinition`、`RoutingEngine.resolveOpenGate()` 声明 |
| Proven by | production call-graph 零 legacy design-loop 调用；正式 workflow gate tests |
| Consumed by | CLI/UI/API 均经 `WorkstreamActionService` / workflow ledger 路由 |
| Enforced before | gate action 在 ledger transition 与 successor side effect 前校验 |
| Failure route | unsupported/hidden action fail-closed；不回落 `FileLedgerStore` |
| Cannot-bypass test | workflow action visibility、human decision、autorun/historical frozen definition tests |
| Residual raw path | `FileLedgerStore` 仅保留 initialization/status/timeline compatibility，不再写 design/human transition |

## 8. 决策与下一步

架构评审已 PASS，D1 family 已按第 6 节作为单一纯删除 checkpoint 实施并完成 deterministic verification。下一步仅允许
独立代码级 diff review；PASS 前不提交。D1 提交前不做 D2/D3 prompt deletion，不调用 provider/real-case。

## 9. Implementation evidence

实现严格限于删除旧 owner 与必要的 retained-record/test cleanup：

- 删除 `FileLedgerStore` 的 11 个 legacy design-loop public methods、私有 prompt/gate helpers 和专属 imports；
- 删除 5 个失去消费者的 DTO/control types，以及 `CodexReviewControl` 两组专属测试；
- 保留 4 个 compatibility record 的字段 shape，只删除旧 factory/mutator；
- 删除 `WorkstreamPaths.currentHumanGateJson()`，关闭独立评审指出的 sibling path；
- `FileLedgerStoreTest` 与 `CheckpointDocumentTest` 只保留 initialization/status compatibility 用例。

机械检查结果：

1. 11 个 legacy method 名在 main/test 中零命中；
2. 5 个 dead type 与 `currentHumanGateJson()` 在 main/test 中零命中；
3. legacy checkpoint factories 与 stage/iteration/workstream transition mutators 零命中；
4. diff 为 16 个 tracked files、56 insertions / 2130 deletions；其中 production 新增仅为 import/类型名 cleanup，
   其余新增为 retained initialization/checkpoint 测试强化；无 adapter、fallback、replacement owner 或 prompt 变更；
5. `git diff --check` clean。

测试结果：

- focused initialization/status/workflow-gate slice：`53/0/0/0`；
- 非沙箱 full Maven reactor：9/9 modules、`1236/0/0/0`；
- 沙箱 full run 的两项错误均为环境拒绝 `ProcessHandle.descendants()` 所依赖的 `sysctl`；同一代码在非沙箱 full run
  全绿，因此不计为实现失败。

Boundary self-review：

- authoritative human/design transition owner 仍唯一为 `RoutingEngine.resolveOpenGate()` + `WorkflowFileLedger`；
- projection 未创建新状态，legacy writer 也未被 replacement 包装复活；
- Definition / Instance / Execution 边界未改变，frozen record shape 保持可读；
- sibling sweep 已覆盖 path helper、dead types、factories/mutators 与专属测试；
- cannot-bypass 仍由正式 workflow gate/action tests 消费真实 ledger transition 证明。

独立 diff review：`PASS（P0=0 / P1=0 / P2=0）`。评审独立复现核心 focused `51/0/0/0`、full reactor
`1236/0/0/0` 与 clean diff-check，并确认没有遗漏 sibling、replacement owner 或 frozen-ledger 读取退化。

成熟度：D1 dead-owner subtraction 为 `VALIDATED`；底层正式 human/design transition 单 owner 继续为 `ENFORCED`。
dead code 没有可运行的 real-case 表面，因此不标 `PROVEN`，也不为它单独调用 provider。本 checkpoint 不把 D2/D3
prompt 规则称为可删；下一步只能先做单一候选的信息增量与 counterfactual 评估。
