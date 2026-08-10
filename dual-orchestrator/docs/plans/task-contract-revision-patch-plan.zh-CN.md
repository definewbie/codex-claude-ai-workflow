# TaskContract Revision Patch 收敛计划

日期：2026-08-10

状态：`ENFORCED / INDEPENDENT DIFF REVIEW PASS / REAL-CASE NOT PROVEN`

主线分类：`P0`。它直接解除 captcha-auth 主流程在 contract revision 边界上的重复失败，修复的
是 revision input contract，而不是新增 P1/P2/P3 能力。当前 P0 未完成项仍是：合法 revision
进入 review、完整 workflow terminal completion、产品实现与 acceptance；本设计不得在实现后仅凭
deterministic tests 宣称这些目标完成。

## 1. 结论

采用一个有限的 `task_contract_revision_patch` 外部输入契约，替代 revision iteration 中
要求 Agent 重新生成完整 `TaskContractProposal v3` 的做法。

这不是：

- `TaskContractProposal v4`；
- JSON Patch；
- 通用 contract diff engine；
- 自动修复或宽松 parser；
- 新 workflow node、phase、registry 或平台层。

它只改变 revision Agent 的输入边界：Agent 声明本次允许修改的有限 delta；Dual 使用已验证
baseline 进行确定性合并，得到完整、不可变的 `TaskContractProposal v3`。Codex review、compile、
`TaskContract.scope` 和 change-scope enforcement 继续只消费完整 v3 Proposal。

## 2. 为什么现在值得做

Captcha-auth completion v4 已证明：

```text
verified baseline + typed obligations
  -> Agent 重新生成完整 proposal
  -> 正确增加 obligation 要求的 exact scope entry
  -> 同时无意改写未声明 AC-6
  -> revision-delta validator fail closed
  -> reviewer/compile/implementation 均未启动
```

该失败不是 parser、schema、lineage 或 validator 缺陷。现有 validator 正确阻断了静默漂移。
问题是 revision 协议把 Agent 的任务定义为：

```text
修改少数目标
+
逐字符重写几十个不应修改的字段
```

此前 captured revision 中已经出现过 `C-2`、`AC-12/TR-16` 和本次 `AC-6` 漂移。重复证据足以
证明这是协议形态的系统性脆弱点，不是为单个模型增加特判。

预期收益：

- 未触及字段由 Dual 保留，不再依赖模型逐字符复制；
- revision 输出和 token 明显缩短，但在 real-case 前不声明具体节省比例；
- 现有 strict validator 继续 fail closed；
- 不增加 Agent 调用、workflow node 或 human gate；
- captcha-auth 可以在新 Gate 下继续作为真实业务任务验证，而不是继续消耗在机械保真失败上。

实现成本为中等且边界有限：一个 strict result subtype、一个纯 merger、revision prompt mode
投影和相应 tests。该成本低于继续通过 prompt 要求模型重写完整 Proposal 并反复消耗真实样本。

## 3. Boundary / Owner

### 3.1 Existing model

| 边界 | authoritative owner |
|---|---|
| workflow iteration / revision trigger | ledger `WorkflowIterationDocument` |
| verified baseline + current obligations | `TaskContractRevisionContextResolver` |
| Agent revision intent | strict `task_contract_revision_patch` result |
| deterministic merge | `TaskContractProposalRevisionMerger`（本 checkpoint 唯一新语义类） |
| canonical candidate | merged `TaskContractProposal v3` |
| proposal mechanical validation | `TaskContractProposalValidator.validate` |
| obligation coverage | `TaskContractProposalValidator.validateRevisionCoverage` |
| clause delta defense | `TaskContractProposalValidator.validateRevisionDelta` |
| trusted proposal lineage | `TaskContractProposalReference` |
| semantic adequacy | `intake.codex_product_review` |
| canonical TaskContract | `ContractCompileHandler` |

### 3.2 Invariant

```text
initial iteration
  accepts only a complete task_contract_proposal

revision iteration
  accepts only a task_contract_revision_patch
  bound to the one verified previous proposal and the current obligation set

verified baseline + valid bounded patch
  -> deterministic merged TaskContractProposal v3
  -> existing mechanical + coverage + delta validators
  -> trusted proposal publication
  -> Codex review
```

Patch 不是新的 SoT。完整 merged Proposal 仍是下游唯一候选合同；patch 只是产生该候选的 typed
command。未经 merge 和原有 validator 的 patch 不得发布 proposal reference，也不得进入 reviewer。

### 3.3 End-to-end closure

```text
Declared by:
  verified baseline reference + typed obligations + strict revision patch

Proven by:
  baseline identity/hash check + finite patch validation + deterministic merge
  + existing full-proposal validation/coverage/delta validation

Consumed by:
  TaskContractProposalReference publishes only the merged full v3 proposal

Enforced before:
  next Codex review, contract compile, design, implementation, or repository change

Failure route:
  typed artifact-contract violation -> node FAILED -> workflow recovery boundary

Cannot bypass:
  wrong baseline, unknown obligation/target, unsupported operation, conflicting edit,
  or invalid merged proposal publishes no trusted proposal reference
```

## 4. Patch Contract

外部 envelope 继续使用 `AgentResultEnvelope.schema_version = 1`。新增一个严格 subtype；下面只展示
`result` payload，envelope 的 workstream/run/node lineage 字段保持不变：

```json
{
  "artifact_type": "task_contract_revision_patch",
  "decision": "READY_FOR_PRODUCT_REVIEW",
  "summary": "Resolved the routed contract obligations.",
  "base_proposal_id": "node-instance:digest-prefix",
  "base_proposal_sha256": "64-lowercase-hex",
  "obligation_patches": [
    {
      "review_obligation_id": "CO-6",
      "resolved_by_clause_ids": ["TR-6"],
      "clause_changes": [],
      "scope_additions": [
        {
          "path": "src/test/java/com/captcha/auth/service/UserServiceTest.java",
          "operation": "CREATE"
        }
      ],
      "evidence_ref_additions": []
    }
  ]
}
```

`clause_changes` 仅允许：

```json
{
  "operation": "REPLACE",
  "section": "ACCEPTANCE_CRITERIA",
  "id": "AC-8",
  "text": "replacement text"
}
```

或：

```json
{
  "operation": "ADD",
  "section": "TEST_REQUIREMENTS",
  "id": "TR-9",
  "text": "new test requirement"
}
```

允许的 clause sections 固定为：

- `ACCEPTANCE_CRITERIA`
- `TEST_REQUIREMENTS`
- `CONSTRAINTS`
- `NON_GOALS`
- `RISK_AREAS`

刻意不支持：

- clause `DELETE` 或 section move；
- scope 删除、目录通配、前缀或 operation 替换；
- `objective`、`scope.excluded`、`open_questions` 修改；
- evidence ref 删除或替换；
- 任意 JSON Pointer / path；
- “复制整个 proposal” fallback。

历史 full-proposal revision 工件只读保留；新 execution 不兼容升级、不自动转换，也不允许用历史
full revision 作为新路径 fallback。

当前真实 obligation 只证明需要 clause ADD/REPLACE、exact scope ADD 和 evidence ref ADD。未来若真实
任务证明必须修改其他字段，单独评估；本 checkpoint 不预建通用 patch 能力。

## 5. Patch Validation

`TaskContractProposalRevisionMerger` 必须是无 I/O 的纯语义类。它消费 resolver 已验证的 context，
不扫描目录、不解析自然语言，也不自行寻找 baseline。

合并前必须验证：

1. 当前 iteration 确实由 `NEEDS_REVISION` 触发；
2. `base_proposal_id` 和 `base_proposal_sha256` 与 verified reference 完全一致；
3. `obligation_patches` 对当前 obligation IDs 精确一对一覆盖，无缺失、未知或重复；
4. 每个 response 的 `resolved_by_clause_ids` 非空，并在 merged Proposal 中存在；
5. `REPLACE` 只能命中 baseline 中同 section、同 ID 的 clause；
6. `ADD` 的 clause ID 在 baseline 与本 patch 中均不存在；
7. 同一 clause、scope entry 或 evidence ref 不得出现冲突/重复 patch；
8. scope addition 的 `operation` 直接反序列化为 `TaskContract.ScopeOperation`，只接受
   exact repository-relative path + uppercase `CREATE`/`MODIFY`；未知或小写 enum value 在
   strict envelope parsing/validation 阶段失败，不由 merger 裸调 `valueOf()` 猜测；
9. evidence ref addition 非空且不重复；
10. strict subtype 不允许任何未声明字段。

一个 obligation 可以只映射到已存在的 supporting clause，同时增加 scope/evidence。保留这一语义，
因为现有 v3 已允许 unchanged supporting target；该映射是否足以满足业务 obligation 仍由 Codex review
判断，Java 不做自然语言蕴含推断。

## 6. Deterministic Merge

固定顺序：

1. 从 verified baseline 构造新的 `TaskContractProposal`；所有 list 使用 `List.copyOf()` 或新的
   encounter-order list，String 逐值复用，不做 trim、大小写、空白或标点 normalization；
2. `REPLACE` 在原 section/原位置替换 text；
3. `ADD` 按 patch encounter order 追加到指定 section；
4. scope additions 按 encounter order 追加；
5. evidence additions 按 encounter order 追加；
6. untouched objective、scope exclusions、clauses、evidence、open questions 逐值保留；
7. 从 obligation patches 生成 canonical v3 `revision_responses`；
8. 对 merged Proposal 依次执行现有 mechanical、coverage、delta validators；
9. 只有全部通过才生成 canonical sidecar/reference。

未触及 clause 的 `id` 和 `text` 来自 baseline 对象本身，不经过模型输出，因此 AC-6 之类的无意
重写在结构上不再可能。

## 7. Prompt / Dispatch Mode

当前 `AgentResultPromptContract` 只按 node ID 渲染，同一 intake node 在 initial/revision iteration
得到相同 full-proposal schema。这一投影必须改为消费现有
`TaskContractRevisionContextResolver` 的 typed result。

具体 API 边界：

1. `TaskContractRevisionContextResolver` 暴露只读 typed `Context`，包含
   `revision`、verified baseline proposal/reference identity 和 obligations；
2. `AgentResultPromptContract` 增加 immutable `ProposalEmissionContext`，只保留渲染所需的
   `mode`、base proposal ID/SHA 和 obligations，不把 ledger document 暴露给 prompt renderer；
3. `AcpxAgentTaskHandler` 在写 prompt 和调用 provider 前，以当前 `nodeInstanceId` 调用 resolver
   一次；intake context 无法验证时直接返回 typed node failure，不创建 ACPX request；
4. handler 用同一个 emission context 只渲染一次 `agentResultContract` 字符串；
5. 同一字符串同时传给 `AgentPromptRenderer.renderWithSections(...)` 和
   `AcpxAgentResultAcquirer.acquire(...)`，bounded correction 直接复用，不重新解析 ledger 或猜 mode；
6. 非 intake node 使用 `NOT_APPLICABLE` emission context，保持现有 result subtype。

渲染规则：

- initial：只渲染完整 `task_contract_proposal` example；
- revision：只渲染 `task_contract_revision_patch` example，并投影 exact baseline ID/SHA 与 obligations；
- revision context 无法验证：在 provider 调用前 fail closed；
- bounded correction 使用同一次 dispatch 已确定的 contract string，不重新推断 mode。

`intake-claude-brainstorm.md` 中当前硬编码的“emit task_contract_proposal”、
`result.scope.included` 和 full-proposal revision 文案必须改为 mode-neutral 的业务要求；
initial/full 与 revision/patch 的字段名、禁止字段和操作集只由动态
`agent_result_contract` 投影。无需新增第二个 template 或 template variable，避免 static template
成为第二个 output schema owner。

不得通过 iteration number、input filename、prompt text、`NEEDS_REVISION` 字符串搜索或目录扫描推断
mode。resolver 仍是唯一 lineage owner。

`ContractRegistry` 可以声明 intake node 能产生两种 external artifact type，但这只是 capability
集合，不决定当前 iteration 的合法 shape。当前合法 shape 由 typed revision context 决定，并由
artifact validator 再次强制。

## 8. Publication / Lineage

Agent 原始 patch 保留在既有 run output / occurrence evidence 中。成功合并后：

- canonical sidecar 的 `source_artifact_type` 如实保留为
  `task_contract_revision_patch`，不得把 Agent patch 伪装为 Agent 直接生成的 full proposal；
- `task_contract_proposal` 字段存储完整 merged v3 Proposal；
- `TaskContractProposalReference` 继续绑定 canonical sidecar hash；
- sidecar 增加 typed `proposal_derivation`：
  - `kind = revision_patch_merge`
  - base proposal ID/SHA
  - patch source output ref
- downstream review/compile 不消费 raw patch，也不重新 merge。

`TaskContractProposalReference.publish/resolve` 只允许两种封闭形态：

1. initial sidecar：`source_artifact_type=task_contract_proposal`，直接包含 full v3 Proposal；
2. revision sidecar：`source_artifact_type=task_contract_revision_patch`，包含 merged full v3 Proposal，
   且 derivation 必须与 verified baseline identity 和 raw patch ref 一致。

这不是 source-type fallback。reference validator 必须按两种明确形态分别 fail closed；缺少或伪造
derivation 的 patch sidecar不能成为 trusted proposal reference。`proposal_derivation` 是 lineage
proof，不决定业务语义或授权。真正业务 authority 仍是 verified baseline、strict patch、merger 和
canonical full Proposal。

具体 publication API：

- `publishInitial(...)` 只接受 `source_artifact_type=task_contract_proposal`；
- `publishRevisionDerived(...)` 只接受
  `source_artifact_type=task_contract_revision_patch`，并要求 typed derivation 的 kind、base ID/SHA、
  patch source ref 与当前 merge result 一致；
- 两者可委托一个 private writer 计算 sidecar digest/reference path，但 public entry 不接受调用方传入
  可任意切换 source type 的 boolean/enum；
- `resolveArtifact/resolveProposalFromRefs` 按 source type 分支验证同样两种封闭形态，绝不使用
  “有 task_contract_proposal 字段就接受”的 fallback。

## 9. Enforcement Points

### 9.1 Before provider

Prompt mode 必须由 verified revision context 决定。context 不完整时不调用 provider。

### 9.2 Before proposal publication

`ArtifactContractValidator` 是 merge + full validation 的强制入口。任何 patch failure 都不得写入
trusted proposal reference，也不得路由到 Codex review。

该入口明确分两段：

1. raw stage：strict envelope subtype validation + revision mode/base/obligation/patch operation validation；
2. canonical stage：merger 生成 full v3 后，复用现有 mechanical/coverage/delta validation。

只有 canonical stage 通过才进入 sidecar/reference publication。现有
`instanceof TaskContractProposalResult` 分支不能扩展为第二套 validator；它应消费一个内部
`ResolvedProposalCandidate`（initial direct 或 revision derived），之后统一走一次 canonical validation。

### 9.3 Compile defense-in-depth

`ContractCompileHandler` 继续只读取 canonical full v3 Proposal，并复用现有 mechanical、coverage、
delta validator。它不认识 patch、不执行第二次 merge。

这保持单一 merge owner，同时保留 compile 对 crafted/bypassed canonical candidate 的最终防线。

## 10. Required Tests

### 10.1 Strict shape

| Case | Expected |
|---|---|
| initial + full Proposal | PASS |
| initial + revision patch | FAIL |
| revision + patch | eligible for merge |
| revision + full Proposal | FAIL；无兼容 fallback |
| patch 含未知字段/operation/section | strict parse/schema FAIL |
| patch scope operation 为小写或未知值 | strict parse/schema FAIL；不进入 merger |

### 10.2 Identity / coverage

| Case | Expected |
|---|---|
| base ID/SHA 精确匹配 | PASS |
| stale/tampered/cross-iteration base | FAIL |
| obligation exact coverage | PASS |
| obligation 缺失/重复/未知 | FAIL |

### 10.3 Merge cannot-bypass

| Case | Expected |
|---|---|
| declared clause REPLACE | merged full Proposal PASS |
| declared clause ADD | merged full Proposal PASS |
| exact scope ADD | merged scope 包含 path+operation |
| evidence ref ADD | merged evidence 包含新增 ref |
| unknown clause REPLACE | FAIL |
| duplicate/conflicting patch target | FAIL |
| unsupported delete/move/field modification | FAIL |
| untouched clauses | 与 baseline character-for-character 相同 |
| merged Proposal mechanical invalid | FAIL before publication |
| merge failure | no proposal ref / no Codex review dispatch |
| revision-derived sidecar 缺少/伪造 derivation | reference publication/resolution FAIL |
| raw patch 被交给 compile | FAIL；compile 只接受 canonical full v3 |
| correction occurrence | 使用 initial dispatch 的同一 prompt mode，不重新解析 |

Clause ID 是 opaque stable identity。不得通过 `AC-`、`TR-`、`C-` 等前缀猜 section；
`section` 字段本身是唯一 typed owner。`section=ACCEPTANCE_CRITERIA,id=TR-3` 只要 ID 在对应
ADD/REPLACE 规则下合法，就不能因字符串前缀失败。新增前缀规则会重引入 validator-private
命名协议，与项目删除语言、文件名和自然语言 heuristic 的方向冲突。

### 10.4 Captured replay

使用 completion v4 的 verified baseline、CO-1..CO-7 和 revision intent 构造 deterministic replay：

- `UserServiceTest.java + CREATE` 被加入 typed scope；
- obligation 要求的 clause changes 被应用；
- baseline `AC-6` 逐字符保持；
- merged v3 mechanical/coverage/delta 全 PASS；
- published review subject 是 merged full Proposal，不是 raw patch；
- 不调用 provider。

### 10.5 Sibling sweep

核对 `full`、`contract`、`terminal-delivery` 三个 bundled workflow：

- 都通过同一 intake handler、artifact validator 和 compile handler；
- 无 revised full-proposal fallback；
- 无 patch 直达 reviewer/compile 路径；
- correction contract、prompt manifest、sidecar/reference lineage 与 fixtures 同步；
- v1/v2 proposal 继续明确拒绝，不新增 migration fallback。

## 11. Files / Classes Boundary

预计修改：

- `AgentResultEnvelope`：新增 strict revision patch subtype 和有限 records/enums；
- `AgentResultEnvelopeValidator`：patch closed-shape/required-field validation；
- `AgentResultPromptContract`：initial/revision 两种明确 schema projection；
- `TaskContractRevisionContextResolver`：在现有 context 中保留 verified baseline reference identity；
- `TaskContractProposalRevisionMerger`：唯一新语义类，纯 merge；
- `ArtifactContractValidator`：revision patch merge 后再走原 canonical validation/publication；
- `AgentResultDocumentProjector`：只投影 merged canonical Proposal + derivation evidence；
- `AgentOutputContractDocument` / `TaskContractProposalReference`：读取并验证封闭的
  initial-direct / revision-derived 两种 proposal lineage；
- `AgentPromptRenderer` / `AcpxAgentTaskHandler`：传递 resolver 决定的 typed emission mode，correction 复用；
- 对应 focused tests、captured replay 和 contract/registry consistency tests。

明确不修改：

- workflow JSON 的 node/edge/gate 数量；
- `TaskContractProposal.SCHEMA_VERSION = 3`；
- `TaskContract` schema；
- `ChangeScopeEvaluator` / Git evidence；
- strict JSON parser acceptance；
- bounded correction 次数；
- retry/recovery/human gate；
- provider/model 配置；
- captcha-auth target code。

## 12. Rejected Alternatives

- 继续强化“逐字符复制完整 Proposal”的 prompt；
- 自动忽略或回滚 full proposal 中 undeclared changes；
- 放宽 revision-delta validator；
- 自动追加缺失 scope/evidence；
- JSON Patch / JSON Pointer；
- LLM semantic merge、embedding diff 或关键词推断；
- revision patch 直接成为 reviewer/compile SoT；
- full Proposal 与 patch 同时接受并自动猜 mode；
- 新 workflow phase/node、generic patch engine、registry 或 artifact graph。

## 13. Stop Conditions

出现以下任一项，停止实现并回到设计评审：

- 必须支持任意字段 patch 才能完成当前真实 obligation；
- merger 需要解析 obligation 自然语言决定操作；
- prompt mode 只能靠文件名、node iteration 数字或文本猜测；
- downstream reviewer/compile 必须理解 raw patch；
- canonical Proposal v3 无法承载合并结果；
- 为兼容历史输出必须保留 revision full-proposal fallback；
- 需要新增 workflow node、module、registry、engine 或 provider retry。

## 14. Acceptance / Maturity

实现可标 `ENFORCED` 仅当：

- initial/revision 输出 shape 由 typed context 唯一决定；
- patch 与 verified baseline 和 obligation set 精确绑定；
- merger 是唯一 delta application owner；
- untouched facts 来自 baseline 而不是模型重写；
- merged full v3 通过所有现有 validator 后才发布；
- cannot-bypass、captured replay、full reactor 和 sibling sweep 全 PASS；
- 独立代码级 review 无 P0/P1。

只有新的、单独授权 captcha-auth full real-case 证明合法 patch 能进入 reviewer 并最终完成产品，才可标
`PROVEN`。deterministic PASS 不等于模型语义质量或产品交付已证明。

## 15. 下一步

1. CC 对本设计的只读架构评审已 `PASS`；
2. mandatory C-1/C-2 与 recommended copy/test 约束已写入本文；C-4 prefix inference 因违反
   opaque-ID 语义明确拒绝；
3. 单一 checkpoint 已一次性实施：revision 只接受 typed patch，Dual 确定性合并并发布
   canonical Proposal v3；
4. focused matrix `197/0/0`，full Maven reactor `1206/0/0`，`git diff --check` clean；
5. CC 独立代码级 diff review 已 `PASS`；复核确认无 P0/P1，review 中两项标为 P1 的观察
   分别属于 record 值相等语义误读和已覆盖的 missing-iteration 测试场景，不构成阻塞；
6. 当前达到 `ENFORCED`，尚未达到 `PROVEN`；
7. 选择性提交并获得 clean SHA 后，制定新的 captcha-auth completion Gate，再运行 fresh full workstream。
