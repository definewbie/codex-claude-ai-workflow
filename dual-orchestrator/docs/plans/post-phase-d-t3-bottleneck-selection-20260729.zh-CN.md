# T3 只读瓶颈价值审查

日期：2026-07-29
状态：`PASS / ONE CANDIDATE SELECTED`
Production 修改：`0`

## 1. 目标与约束

本次只读比较三个对象：

1. `implement.claude_self_test_report`
2. `stage.claude_handoff`
3. 6 个 `context.build_package` 节点及重复 requirement/context

最多选择一个 subtraction 候选。不新增 module、framework、registry、engine 或平台，
不修改 production，不把金额作为选择依据。

基线：

- Dual SHA：`0e0d77ee3a7a1b78cca69e64e1c46623c4f6cd5d`
- Workstream：`ws-20260728T153012Z-JH74`
- 结果：`COMPLETED / ACCEPT`
- Agent runs：10
- Revisions / corrections：0 / 0
- Baseline：
  `docs/plans/post-phase-d-t2-efficiency-baseline-20260728.zh-CN.md`

## 2. 最终结论

唯一候选：

> 从 full workflow 使用的 6 个 context profile 中删除重复的 `requirement` section，
> 保留 `AgentPromptRenderer` 对同一 `requirement.md` 的唯一 prompt 注入。

不删除 context-build 节点。其他两个对象不进入优化：

- `implement.claude_self_test_report`：`KEEP`
- `stage.claude_handoff`：`KEEP / NOT SAFE TO SUBTRACT`

T3 只完成候选选择。T4 尚未激活，不能据此直接修改 production。

## 3. 候选对比

| 对象 | Baseline 贡献 | 控制职责与替代条件 | 裁决 |
| --- | --- | --- | --- |
| `implement.claude_self_test_report` | 1 run；21.1s；114,719 provider tokens | 产生 acceptance 前必需的 typed self-test evidence；无替代 owner | `KEEP` |
| `stage.claude_handoff` | 1 run；14.8s；43,512 provider tokens | 部分字段可派生，但完整 handoff owner 与 revise feedback 链未闭合 | `KEEP` |
| 6 个 context-build 节点 | 6 nodes；合计约 143ms | evidence/lineage preflight 与 bounded projection | `KEEP` |
| context 内重复 requirement | 467 estimated tokens × 6 = 2,802 | 同一文件已由 `AgentPromptRenderer` 再注入一次 | `SELECT` |

候选的理论结构收益：

- Agent calls：不变，10；
- context estimated tokens：17,348 -> 14,546，减少约 16.15%；
- 全部 input tokens 的理论上限：减少 2,802 / 77,000，约 3.64%；
- strict contract、lineage、gate、revision 与 context preflight 不变。

这是 estimated context 上限，不是 provider 实际 token 节省；T5 必须用同一 A3 case 验证。

## 4. 为什么 self-test 不能删

### Authoritative fact

```text
implement.claude_self_test_report
  -> AgentResultEnvelope.SelfTestResult
  -> implement-claude-self-test.contract.json
  -> SELF_TEST_COMPLETE + checks/evidence/verificationSummary
```
该节点实际运行完整 `npm test` 和 workspace hygiene 检查；A3 中 implementation 节点只
运行了 `node --check`，不能替代完整自测。

### Consumers 与副作用

- `ArtifactValidateHandler` 转发 validated sidecar；
- acceptance context 明确要求 `SELF_TEST_COMPLETE`；
- `ContextBuildPackageHandler.missingRequiredAgentEvidence()` 在缺失时阻止
  `acceptance.codex_verifier_review` 启动；
- verification failure 路由回 self-test；
- final report 和 next-stage verification 投影消费该事实。

删除它必须先建立新的 deterministic test-execution owner。当前没有现成替代 owner，
因此不是单纯 subtraction。

## 5. 为什么 stage handoff 当前不能删

### 可量化，但 owner 不完整

`PromptGenerateNextStageHandler` 已能从 typed facts 覆盖 verification，并在
`changed_files` 缺失时回退到 Git evidence。这证明 handoff Agent 存在重复投影，但
不能证明它可以直接删除。

当前没有现成 deterministic owner 权威产生：

- completed work；
- important decisions；
- open risks；
- recommended next stage；
- suggested next prompt。

canonical `handoff.json` 还被以下 production consumer 使用：

- `EvidenceManifest`
- `ContextBuildPackageHandler` / `ContextPackageSectionRenderer`
- `CompletionAuditWriter`
- `WorkstreamQueryService` completion audit

### Revise 语义未闭合

workflow 存在：

```text
gate.human_next_stage_approval --revise--> stage.claude_handoff
```

但 gate note 只在 gate node result 中，successor 只继承 output refs；stage prompt
只注入 requirement 和 context package。当前代码和历史 real-case 都不能证明 handoff
Agent 收到过 next-stage revise note。

因此现在删除 handoff 会同时要求：

1. 新建/指定完整 handoff fact owner；
2. 修复 gate feedback 传递；
3. 修改 workflow、contract、adapter special cases 和 completion consumers。

这已经超过不可再拆的 subtraction。T3 不选择它，也不为它新建后续 phase。

## 6. 重复 requirement 的真实调用链

### Source of truth

```text
.agent/workstreams/<workstreamId>/requirement.md
```

`FileLedgerStore` 在 workstream 创建时写入。当前 prompt 有两条读取同一事实的路径：

```text
Path A
requirement.md
  -> ContextBuildPackageHandler
  -> context package "User Requirement"
  -> AgentPromptRenderer {{context_package}}

Path B
requirement.md
  -> AgentPromptRenderer
  -> template {{requirement}}
```

A3 acceptance prompt 的 captured evidence 同时出现：

```text
line 46  ## User Request
line 86  ## Context Package
line 196 ## User Requirement
```

这不是两个不同语义，而是同一 `requirement.md` 的双重注入。

### 删除后的唯一 owner

```text
FileLedgerStore
  -> requirement.md
  -> AgentPromptRenderer
  -> prompt template {{requirement}}
```

full workflow 的 intake、product review、architecture review、plan review、implementation
和 acceptance Agent templates 都具有 `{{requirement}}` 插槽。

T4 只删除这 6 个 active `ContextProfiles` 中的默认 `requirement` section。保留：

- context-build 节点本身；
- proposal/self-test evidence preflight；
- input artifacts、human feedback、recent outputs、Git evidence、ledger pointers；
- `ContextBuildPackageHandler` 对显式/custom `requirement` section 的通用支持；
- `AgentPromptRenderer` 的唯一 requirement 注入。

这样不是把 context 逻辑移入 Agent handler，也不创建新的 owner。

## 7. 被阻止的副作用

候选不改变 workflow side effect：

- proposal 缺失仍阻止 product review；
- self-test evidence 缺失仍阻止 acceptance；
- context budget/metadata 仍由 `ContextBuildPackageHandler` 产生；
- strict `AgentResultEnvelope`、lineage、gate 和 routing 不变。

唯一变化是同一 Agent prompt 中 requirement 正文从两份变为一份。

## 8. T4 Cannot-Bypass Gate

T4 只有在显式激活后才能实施，并必须一次性证明：

1. 6 个 full-workflow target prompt 中 requirement 正文恰好出现一次。
2. 6 个 active profile 生成的 context package 不再含 `User Requirement` section。
3. `AgentPromptRenderer` 仍从 authoritative `requirement.md` 注入完整正文。
4. proposal 缺失仍阻止 product review Agent 启动。
5. self-test evidence 缺失仍阻止 acceptance Agent 启动。
6. 其他 context sections、metadata、budget 和 source refs 保持一致。
7. explicit/custom context 若请求 `requirement` section，现有通用能力仍可工作。
8. 不新增 module、framework、registry、owner 或兼容 fallback。
9. full reactor 通过后，使用同一 A3 case 做 before/after；actual input/token
   下降才算 `PASS`，未下降算 `FAIL`，provider/config 异常算 `INVALID`。

## 9. Boundary Review

```text
Boundary Review: PASS for T3 selection; T4 READY, NOT ACTIVE

Declared by:
  FileLedgerStore writes authoritative requirement.md.

Proven by:
  ContextBuildPackageHandler and AgentPromptRenderer independently read the same file;
  captured A3 prompts contain both User Request and User Requirement.

Consumed by:
  T4 may remove only the default requirement section from six active context profiles.

Enforced before:
  No production change until T4 is explicitly activated.

Failure route:
  Evidence preflight remains before context rendering and Agent start.

Cannot-bypass:
  Defined in section 8; not yet executed.

Residual raw path:
  requirement.md remains human-authored task content, injected once and never parsed for routing.
```
