# TaskContract Revision Target Uniqueness 收敛计划

日期：2026-08-15  
状态：`INDEPENDENT DIFF REVIEW PASS / ENFORCED / NOT PROVEN / READY FOR SELECTIVE COMMIT`

## 1. Mainline Decision Gate

- Target phase：`P0`。
- Current phase status：artifact contract 主路径已 `Promoted`；本次是 v14 real-case 暴露的 P0 projection completeness blocker。
- Upstream promotion evidence：strict `AgentResultEnvelope`、lineage-bound revision patch、durable proposal validation 已由既有 deterministic tests 与历史 real-case 强制。
- Boundary owner：revision patch 的全局 mutation-target 语义。
- Source of truth being strengthened：`TaskContractRevisionPatchResult.obligation_patches` 到 durable `TaskContractProposal` 的 typed merge contract。
- Drift path being removed：Agent prompt 只声明 obligation 唯一，但 merger 隐式强制 clause/scope/evidence target 全局唯一。
- Explicit non-goals：不改 workflow、parser acceptance、correction 次数、retry、revision budget、模型、provider、human gate、durable proposal schema；不自动去重；不新增 phase/module/registry/engine/framework。
- Proof required：captured invalid patch 被 Agent boundary 精确拒绝；只删除第二个共享 evidence ref 后 merge PASS；durable merger 仍独立阻止绕过；full reactor PASS。
- Remaining gaps：实现后只可标 `ENFORCED / NOT PROVEN`；fresh captcha-auth real-case 通过前不能标 `PROVEN` 或产品完成。

## 2. 已确认根因

v14 的 revision patch 在 CO-1 和 CO-3 中各加入一次
`src/main/resources/application.yml`。基线全局 `evidence_refs` 只有 `README.md`，因此该路径作为新增 evidence 合法；重复只发生在
同一 patch 的两个 obligation item 之间。

当前模型存在一个投影断层：

```text
AgentResultPromptContract
  只说明 obligation id exactly once
  未说明 mutation target 跨 obligation 全局唯一
        ↓
TaskContractRevisionPatchResult
  evidence_ref_additions 嵌套在 obligation patch 中
        ↓
TaskContractProposalRevisionMerger
  clause id / scope path / evidence ref 使用全局 Set 判重
        ↓
TaskContractProposalValidator
  durable evidence_refs 再次要求唯一
```

同一个 evidence ref 可以在业务上支持多个 obligation，但 patch 的作用是修改一个全局 proposal，而不是记录多对多证据关系。
因此 mutation target 必须只出现一次；多个 obligation 仍分别通过 `resolved_by_clause_ids` 证明覆盖。自动去重会掩盖两个 patch
对同一 target 可能携带冲突意图，不能采用。

## 3. Existing model

- Definition：`AgentResultEnvelope.TaskContractRevisionPatchResult` 定义 patch shape。
- Instance：一个 intake revision iteration 产生一个 lineage-bound patch occurrence。
- Execution：acpx run 只负责产生候选 JSON；本次 exit 0/end_turn。
- Artifact：strict envelope 是 Agent-facing artifact；merged `TaskContractProposal` 是 durable artifact。
- Iteration：review obligations 与 previous proposal reference 绑定当前 semantic revision。
- Projection：`AgentResultPromptContract` 把 authoritative shape/规则投影给 Agent。

Problem ownership：全局 target uniqueness 属于 revision patch semantic validation，不属于 prompt 文案或 handler 路由。

New concept needed：需要一个很小的 package-private semantic validator，复用现有 `TaskContractRevisionPatchResult`，不引入新持久化对象或生命周期概念。

## 4. 原子实现

### 4.1 单一 semantic owner

新增 package-private `TaskContractRevisionPatchTargetValidator`，输入一个 typed revision patch，输出稳定的 typed violation：

- `DUPLICATE_CLAUSE_PATCH_TARGET: <clause-id>`；
- `DUPLICATE_SCOPE_PATCH_TARGET: <path>`；
- `DUPLICATE_EVIDENCE_PATCH_TARGET: <ref>`。

唯一性范围是整个 `obligation_patches` 数组，不是单个 obligation。比较规则保持 production 现状，不顺带增加大小写折叠、路径规范化或
NL 分类。该现状必须冻结为显式契约：clause target 在比较前 `trim()`；scope path 与 evidence ref 按原始字符串比较。
focused tests 必须覆盖前后空白变体，证明 early validator 与 durable merger 对三类 target 使用完全相同的比较规则。

`TaskContractProposalRevisionMerger` 删除自己的三组判重循环，委托该 validator；lineage、obligation coverage、ADD/REPLACE 与
scope resolution 逻辑不变。这样 semantic owner 只有一处，merger 仍在 durable sidecar 发布前强制调用，不能被 Agent 边界绕过。

### 4.2 Agent boundary 提前拒绝

`AgentResultEnvelopeValidator.validateRevisionPatch` 在完成 shape/null 检查后调用同一个 target validator，把 violation 作为精确
validation error 返回。它不读取 previous proposal、filesystem 或 ledger，也不决定 workflow 状态。

这会让错误在 strict Agent result contract boundary 被识别，而不是延迟到 artifact merge。本错误属于
`NOT_REPAIRABLE` semantic validation；现有 acquirer 只有在 `schemaCorrectionAllowed()` 为 true 时才进入
`SCHEMA_FAILURE` bounded correction，因此该前移不会增加 correction 或 Agent 调用。本 checkpoint 不改 reason/disposition、次数、预算，
也不对失败结果做自动修复。测试与 diff review 必须确认该错误保持 `NOT_REPAIRABLE`，并且 direct merger 的 durable failure route 不变。

### 4.3 Prompt 完整投影

`AgentResultPromptContract.contractRevisionInstruction` 增加一条有界规则：

- `clause_changes[].id`、`scope_additions[].path`、`evidence_ref_additions[]` 分别在整个
  `result.obligation_patches` 中全局唯一；
- 一个共享 evidence ref 只在一个最相关 obligation patch 中 ADD 一次；其他 obligation 通过各自
  `resolved_by_clause_ids` 表达覆盖；
- 不要为每个使用该 evidence 的 obligation 重复 ADD。

该文字只是 authoritative semantic validator 的投影，不作为成熟度证据。

### 4.4 Captured deterministic replay

把 v14 revision output 作为最小、去敏后的 exact-shape fixture 固化在 runtime tests，保留四个 obligations、两个相同
`application.yml` additions 与 lineage fields。

必须证明：

1. 原始 captured shape 通过 JSON parser，但 target validator/Agent envelope validation 精确返回
   `DUPLICATE_EVIDENCE_PATCH_TARGET`。
2. 即使绕过 Agent boundary 直接调用 merger，durable merge 仍被同一 semantic owner 拒绝。
3. 只删除 CO-3 中第二个 `application.yml` 后，merge PASS；最终 evidence refs 恰含一次 `README.md` 和一次
   `application.yml`。
4. CO-1..CO-4 各恰好一个 revision response；所有 clause changes、base proposal id/SHA 与 scope 保真。
5. clause target 与 scope target 的跨-obligation duplicate 也有参数化/并列回归，防止只修 evidence sibling。
6. 空白变体 parity：clause id 的前后空白按既有 `trim()` 语义冲突；scope path/evidence ref 的原始字符串差异按既有语义不在本 owner
   中折叠。early validator 与 direct merger 结果必须一致。

## 5. 文件范围

Production：

- `dual-runtime/.../handler/TaskContractRevisionPatchTargetValidator.java`（新增，小型 semantic owner）；
- `dual-runtime/.../handler/TaskContractProposalRevisionMerger.java`；
- `dual-runtime/.../handler/AgentResultEnvelopeValidator.java`；
- `dual-runtime/.../handler/AgentResultPromptContract.java`。

Tests/fixture：

- `TaskContractProposalRevisionMergerTest.java`；
- `AgentResultEnvelopeValidatorTest.java` 或现有等价 validator test owner；
- `AgentResultPromptContractTest.java`；
- 一个 v14 captured revision patch JSON fixture（若现有 test convention 使用 inline fixture，则不新建资源文件）。

不修改其他 module、workflow JSON、contract registry、parser、acquirer、routing、recovery、UI/CLI/API。

## 6. End-to-end invariant

- Declared by：`TaskContractRevisionPatchTargetValidator`。
- Proven by：captured invalid/valid pair 与 clause/scope sibling tests。
- Consumed by：`AgentResultEnvelopeValidator`（早期）和 `TaskContractProposalRevisionMerger`（durable side-effect 前）。
- Enforced before：correction policy/contract acceptance 与 durable proposal/reference 发布。
- Failure route：精确 typed contract validation failure；仍由既有 finalizer/outcome/recovery 链处理。
- Cannot-bypass：直接调用 merger 的测试证明跳过 Agent validation 仍不能发布重复 target。
- Residual raw path：无自然语言解析；prompt 不是 owner；不从 summary/must_fix 猜 target。

## 7. Sibling sweep

- clause target、scope target、evidence target 必须一起迁移到共享 owner。
- obligation id uniqueness 仍由 envelope validator/merger 的 obligation coverage 语义负责，不与 mutation target 混合。
- durable proposal 自身的 clause/scope/evidence uniqueness validators 保留，作为最终 artifact invariant，不删除。
- initial proposal 不使用 revision patch target validator，不改变其行为。
- full/contract/terminal-delivery 共用同一 handler/contract，不需要 workflow variant 分支。

## 8. 验收顺序

1. focused target-validator、envelope-validator、merger、prompt contract tests PASS。
2. captured invalid/valid replay PASS。
3. `git diff --check` PASS。
4. full Maven reactor PASS。
5. 独立代码级 diff review 按 Declared/Proven/Consumed/Enforced/Failure/Cannot-bypass 核实并 PASS。
6. 选择性提交 clean SHA 后才制定新的 captcha-auth Gate；不复用 v14 workstream、worktree 或 session。

## 9. Stop conditions

- 若实现需要修改 parser acceptance、自动去重、correction/retry 次数、workflow 或 durable schema，停止并回到设计评审。
- 若 captured valid pair 仍不能 merge，说明不止 target uniqueness 一个缺口，停止原子 checkpoint，不继续叠补丁。
- 若 reviewer 发现第二个 target owner 或绕过 merger 的 sidecar 发布路径，判定 `FAIL`，不得提交。

## 10. Implementation evidence

- 架构评审：`PASS（P0=0 / P1=0 / P2=4）`；P2-1 比较规则与 parity test 已补，P2-2 经真实代码核实修正为
  `NOT_REPAIRABLE`，不会进入 correction。
- shared owner：`TaskContractRevisionPatchTargetValidator`。
- early consumer：`AgentResultEnvelopeValidator`。
- durable consumer：`TaskContractProposalRevisionMerger`；旧三组 target Set 已删除。
- prompt projection：三类 target 跨整个 `obligation_patches` 全局唯一，共享 evidence 只 ADD 一次。
- focused tests：18/0/0/0。
- full Maven reactor：1253/0/0/0。
- `git diff --check`：PASS。
- provider/real-case：未运行。

独立 diff review：`PASS（P0=0 / P1=0 / P2=3 非阻塞观察项）`。接受并记录：

1. captured fixture 保留真实四 obligation、两个重复 evidence、lineage、invalid/valid pair 与最终 coverage，但将原始 12 个
   clause changes 缩为 4 个 REPLACE；ADD/scope/multi-id 行为由共享 owner sibling tests 和既有 merger tests 覆盖。它是
   captured-shape boundary replay，不声明逐字节 exact replay。
2. null obligation-id 的 target 现在也参与共享判重，较旧 merger 更 fail-closed；合法输入已在 envelope boundary 拒绝该形态。
3. merger violation 顺序因共享 owner 后置聚合而变化，错误集合与控制结果不变；无下游按错误顺序路由。

以上不改变 source of truth、合法输入语义、correction 调用数或 durable publication boundary，不增加实现范围。
