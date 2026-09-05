# E1 Approved TaskContract Qualification Checkpoint Result

状态：INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT
日期：2026-09-05
基线：`dc3c1a99`

## 1. 目标与结论

本 checkpoint 只解决 approved TaskContract 在 contract approval 之后失去权威连续性的问题：resolved
`gate.contract_review` 对唯一 `contract.compile` occurrence 的批准，现在由一个 resolver 重建为 immutable in-process snapshot，所有声明需要
approved contract 的后续 consumer 必须消费该 snapshot。

当前可声明：`CONTRACT QUALIFICATION ENFORCED / REAL-CASE NOT PROVEN`。

未实现 E2 typed design scope、E3 semantic revision obligations、E4 canonical plan subtraction，也未修改 recovery、网络、provider 或业务工程。

## 2. 实施结果

### 2.1 Authority producer

- `RoutingEngine` 在 gate node、`GateDocument` 与 `node_routed` event 三处写入同一
  `source_node_instance_id`；human resolution 保留该 machine-owned lineage。
- `ContractCompileHandler` 同时持久化 TaskContract 与 Requirement Resolution 的 SHA-256。
- 没有新增 durable binding artifact 或 schema。

### 2.2 Unique resolver

`TaskContractProjectionVerifier` 按 workflow event 数值顺序选择最后一个 contract gate occurrence，并验证：

- gate/node 已 resolved/completed，human action 为 approved；
- event、gate、node 的 source occurrence 一致且指向 completed `contract.compile`；
- gate subject、node inputs 与 compile outputs 绑定相同 contract/resolution refs；
- stable bytes 的 SHA 与 contract identity 匹配 compile result；
- 后续若已有 completed recompile 但没有新的完整 approval，立即拒绝；
- clarify/reject/open/revised/corrupt/missing/tampered 均不回退旧 approval。

成功后返回一次读取形成的 immutable snapshot；同一 execution 的下游不再重读 stable bytes。

### 2.3 Definition enforcement

三个 bundled lifecycle 与 small-plan experiment 的 post-contract 节点均显式声明
`requires_approved_task_contract=true`。Loader 仅依据 contract gate purpose、approved edge 与图可达性区分 negotiation/post-contract region；
acceptance 回流到 negotiation region 会停止资格传播，下一轮必须重新完成 contract approval。缺失、非 boolean、区域重叠或 custom definition
漏声明均在加载时失败。

### 2.4 Consumer convergence

Agent/context、artifact validation、failure classification、post-contract human gate、memory projection 与 final report 均收敛到同一 qualified snapshot。
Configured consumer 在 provider、Git evidence、classification、review packet、memory publication 或 final report 写入前失败。

`contract-resolution.json` 统一展示为 Requirement Resolution，不再冒充 compiled-contract approval。Legacy workflow 无 contract gate 时仍可使用集中式
legacy projection，但明确不具备 qualification；旧 ledger 若存在 contract gate 却缺 source identity，则 fail closed，不猜测 source。

## 3. End-to-End Invariant

| Dimension | Result |
|---|---|
| Declared by | workflow `requires_approved_task_contract` + resolved contract gate action |
| Proven by | event-ordered gate/source-compile lineage + contract/resolution refs/SHA |
| Consumed by | `TaskContractProjectionVerifier` 返回的 immutable snapshot |
| Enforced before | configured provider、Git evidence、classification、human packet、memory 与 terminal report side effects |
| Failure route | `TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED` + typed reason；无旧 approval fallback |
| Cannot-bypass | recompile/reapprove、gate crash slices、graph validation、stable-byte tamper、configured consumer zero-side-effect tests |

## 4. 验证

- Focused：`BuiltinHandlerTest`、`ReportGenerateFinalHandlerTest`、`TaskContractProjectionVerifierTest`、
  `WorkflowDefinitionLoaderTest` 合计 `98/0/0/0`。
- CLI dependency reactor：`mvn -pl dual-cli -am test`，`1272/0/0/0`，`BUILD SUCCESS`；此命令不含 dual-server，前版称为 full reactor 不准确。
  独立评审全模块复跑为 `1337/0/0/0`（154 份 fresh surefire XML）；提交前逐模块 XML 聚合核对一致，差额为 dual-server 的 `65/0/0/0`。
  首次文件沙箱内运行仅因 macOS `ProcessHandle.descendants()` 的系统读取权限导致两个既有测试
  error；未修改测试，按原命令在受控权限外复跑后全绿。
- Package：`mvn -pl dual-cli -am -DskipTests package` 成功。
- Workflow validation：contract、full、terminal-delivery 与 small-plan-human-review experiment 四个 definition 均由新 JAR 验证通过；
  experiment 经独立评审与提交前复核均通过。
- Direct-reader subtraction sweep：production stable contract/resolution 引用只剩 compile writer、唯一 verifier、pre-approval revision-history selector，
  以及 adapter 的 protected-path/prompt-filter 判断；不存在第二个 post-contract authority reader。
- `git diff --check`：clean。
- Provider/real-case：未调用；PNBV 未恢复。

## 5. Reflect

实施过程暴露并关闭了三个会被局部测试遗漏的问题：

1. 对 legacy projection 全局加 schema validation 会改变兼容语义。最终只在原本负责验证的 `ArtifactValidateHandler` 保留 validation，其他 legacy
   presentation 保持旧行为；configured path 仍严格 qualification。
2. 把完整 TaskContract 同时写入 context package 会突破既有 context budget并复制 authority。最终 package 只保留 bounded projection，Agent 的完整
   payload 由当前 `PreparedContext` snapshot 独立注入。
3. routing-only synthetic integration fixture 从 workflow 中段直接起跑，无法构造 contract approval。没有削弱 production rule，而是明确把该 fixture
   限定为 plan-gate routing 测试；真实 definition parity 与 qualification 由 loader、resolver 和 consumer tests 负责。

这三项说明边界变更不能只看 resolver 单测：必须同时验证 legacy、预算、synthetic fixture 与真实 workflow graph。

## 6. Residual

- stable contract/resolution 文件物理上仍可变；configured consumer 每次进入时重建并校验 qualification，成功后只消费该 execution 的 immutable
  snapshot。不存在跨 execution 的锁或事务隔离声明。
- newer compile 的 crash guard 使用 durable node completion time 判断“批准后又产生 completed compile”；authority 的选择本身仍只按 event order。
- legacy no-contract-gate workflow 是显式 unqualified compatibility，不可用于证明 E1。
- E2/E3/E4 和新的 natural real-case proof 仍未完成。

## 7. 下一步

独立代码级 review 为 PASS（P0=0/P1=0/P2=2），确认六维 invariant、全部 consumer 与四 workflow sibling，允许选择性提交。
两项 P2 的测试统计口径与 experiment 验证记录已修正。本 checkpoint 提交后，下一步起草 E2 实施计划并独立评审；
E2/E3/E4 的生产实现与整体 real-case 收敛尚未完成，不由 E1 的 PASS 代替。
