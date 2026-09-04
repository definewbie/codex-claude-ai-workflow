# AgentResult Verification Remediation Projection - 独立 Diff Review

日期：2026-09-04  
评审范围：`AgentResultPromptContract`、三层 prompt/correction 测试、active plan 与 OKX fresh real-case 结果记录。  
方式：独立代码级控制流审查；复跑 focused tests、full reactor 与 `git diff --check`；未调用 provider，未恢复失败 workstream。

## 结论

- P0：无。
- P1：1 项。
- P2：1 项。
- Boundary Review：`WARN`。
- 选择性提交：`NOT READY`，关闭 P1 后可做一次短 delta review。

## P1-1：枚举语法已投影，但节点适用语义仍可绕过

当前改动在 `AgentResultPromptContract.java:54-65` 对所有节点投影完整枚举：

```text
[EVIDENCE_COLLECTION, NONE, TEST_CODE_CHANGE]
```

这关闭了 captured `REVISE_PROPOSAL` 的未知枚举解析失败，但把 acceptance review 专属的两个修复动作同时广告给了
`intake.codex_product_review`、plan 与其他使用通用 `Failure` 的节点。`verificationRemediationInstruction(...)` 只对
`acceptance.codex_verifier_review` 解释使用条件，其他节点没有“必须为 NONE”的约束。

更关键的是，这不只是 prompt 精度问题。`AgentResultEnvelopeValidator.java:136-162` 对 `AgentOutputResult` 和
`PlanResult` 只调用通用 `validateEvidencePayload(...)`；节点适用性校验仅存在于
`AcceptanceReviewResult` 分支（`:172-177`、`:394-412`）。因此 product review 若把原来的 `REVISE_PROPOSAL`
改成语法合法的 `TEST_CODE_CHANGE`，parser 与 validator 均可能接受该 typed fact。后续
`FailureClassifyHandler.java:77-82` 会把它交给 `FailureArbiter`，而后者在 `:81-86` 优先路由为 implementation bug。
这会让错误节点拥有 acceptance verification remediation 的路由权。

现有测试只能证明：

- enum 来源未复制；
- initial 与 correction 都能看到三个值；
- captured 非法字符串不在列表中。

它们不能证明非 acceptance 节点无法产出并持久化合法但越权的 remediation。

### 必修最小动作

1. Prompt 投影改为节点适用集合：`acceptance.codex_verifier_review` 保留三个值；其他带 `failure` 的节点只允许
   `NONE`，并明确写出该约束。
2. Validator 增加同一 deterministic 守卫：非 acceptance result 的 remediation 非空且非 `NONE` 时拒绝；不能只依赖
   模型遵循 prompt。
3. 增加 product-review 反证测试：`TEST_CODE_CHANGE` 必须 parse 成功但 validation 失败，证明节点权限边界由 validator
   强制。
4. correction prompt 测试改为断言 product review 只看到 `[NONE]`；acceptance renderer 测试继续断言完整三值与现有
   `REJECT + missing_tests` 条件。

该动作仍属于同一 `AgentResultEnvelope` contract boundary，不需要修改 workflow、parser、recovery 或 failure routing。

## P2-1：结果文档对 Codex 模型兼容性的归因过强

real-case 结果第 11 行称全局 `gpt-5.6-sol` 与冻结 `codex-acp 0.16.0` “不兼容”。已确认事实是：adapter 缺少该模型的
metadata，继承默认模型的调用失败，而显式 `gpt-5.4` 调用成功；但 adapter 声明会使用 fallback metadata，尚未证明
metadata 缺失就是 internal error 的唯一原因。建议改为“继承 `gpt-5.6-sol` 的 probe 失败；隔离配置固定
`gpt-5.4` 后通过”，避免把相关性写成已证因果。

## 边界闭环核验

- Declared by：`AgentResultEnvelope.VerificationRemediation` typed enum。
- Proven by：parser 证明值属于 enum；validator 应证明值适用于当前 result/node。
- Consumed by：`FailureClassifyHandler` / `FailureArbiter`。
- Enforced before：artifact publication 与 failure routing。
- Failure route：未知值进入 parse failure；已知但越权值应进入 typed validation failure。
- Cannot-bypass test：当前缺“product review 使用 `TEST_CODE_CHANGE` 必须失败”的反证测试。

所以当前成熟度只能标为 `ENUM VALUE PROJECTION VALIDATED / NODE APPLICABILITY NOT ENFORCED / REAL-CASE NOT PROVEN`，
不能标为 `CONTRACT ENUM PROJECTION ENFORCED`。

## 独立验证

- focused：`AgentResultPromptContractTest` 10、`AcpxAgentResultAcquirerTest` 27、`AgentPromptRendererTest` 36，合计
  `73/0/0/0`。
- full reactor：`1324/0/0/0`，`BUILD SUCCESS`。
- `git diff --check`：clean。

测试全绿证明现有实现未引入普通回归，但不消除 P1 所述的 semantic authorization 缺口。

## Delta Review Closure

独立 delta review 已确认 P0/P1 均为零，原 P1 已关闭：

- acceptance 与非 acceptance 的节点适用值同时由 prompt 投影和 validator 守卫；
- product-review 与 plan 的已知但越权值均在 parse 成功后被 validation 拒绝；
- correction prompt 对非 acceptance 节点只投影 `[NONE]`；
- enum owner、parser、workflow、recovery 与 failure routing 均未扩张。

独立评审结论：`PASS / ALLOWED TO COMMIT`。P2 仅包括类型/节点文案基准的未来一致性注记、提交前 full reactor
惯例以及 ignored real-case report 的同步确认，均不阻塞。本地已完成 full reactor `1325/0/0/0` 与
`git diff --check` clean。
