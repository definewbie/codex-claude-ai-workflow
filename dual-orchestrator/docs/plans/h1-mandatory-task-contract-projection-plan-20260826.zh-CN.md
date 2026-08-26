# H1 Mandatory Task-Contract Projection Implementation Plan

日期：2026-08-26
状态：INDEPENDENT DIFF REVIEW PASS / READY FOR SELECTIVE COMMIT

## 1. 唯一目标

当 agent acceptance context 中存在由当前 workstream 的成功 `contract.compile` 产出的 qualified
`task-contract.json` 时，Dual 必须在 provider dispatch 前证明其身份、内容和产生链，并把同一字节快照代表的
完整 typed contract 恰好投影一次。合同超过 section soft budget 或 package total budget时只记录 typed warning
与真实 token 成本，不得 summary、truncate、drop 或让 context node 因经验预算失败。

本 checkpoint 修复 H1 的 context projection correctness，不验证 provider/model 的 context capacity，也不调用
provider。

## 2. 边界与 owner

| 事实 | 唯一 owner | 消费方 |
|---|---|---|
| canonical TaskContract 业务语义 | `TaskContract` + `TaskContractValidator` | compile、acceptance projection、change-scope validation |
| 本次 artifact 由哪个 compile occurrence 产生 | completed `contract.compile` node result + output refs | acceptance qualification verifier |
| artifact 内容身份 | compile result 的 `task_contract_sha256` | acceptance qualification verifier |
| acceptance 是否全文保留 | acceptance `SectionRequirement` 的显式 overflow policy | `ContextBuildPackageHandler` |
| soft/total budget 超限事实 | context package metadata | trace/query/metrics 与人工审计 |
| provider context capacity | provider/adapter typed metadata；当前不存在 | 未来 pre-dispatch guard，不属于本 checkpoint |

Definition / Instance / Execution 区分：profile policy 是 definition；completed compile node 是 durable instance
事实；具体 agent/process run 不参与 TaskContract qualification。context markdown 与 metadata 都是 projection，不能反向
成为合同 SoT。

## 3. 已确认的代码事实

1. `ContextProfiles.acceptance()` 当前将 `task_contract` 配为 `FULL / FAIL / 2500`；超过 soft ceiling
   在 `ContextBuildPackageHandler.renderGovernedSectionOrFailure()` 直接使 context node FAILED。
2. package total 超限时 Phase 5 只丢弃非 `FAIL` section；仍超限就 FAILED。因此把 `2500` 换成更大常量
   只会推迟同一缺陷。
3. `ContextProfiles.intake()` 也有 `task_contract`，但语义为 `SUMMARY / DEGRADE / 500`。
4. `ContractCompileHandler` 已在同一 node result 写 `contract_id/status/version` 和 canonical output refs，
   尚未记录 task-contract SHA。
5. `ContextPackageSectionRenderer.appendTaskContract()` 会重新读文件，且只投影字段子集；它不能证明“完整、
   同一快照、恰好一次”。
6. `TaskContractValidator` 没有合同规模上限；合法合同可确定性超过 2500 tokens。
7. `AcpxProviderMetadata` 没有 authoritative context-window/capacity 字段。本 checkpoint 不发明经验 hard cap。

## 4. Profile sibling 裁决

H1 只修改 acceptance profile。intake 保持 `SUMMARY / DEGRADE / 500`，理由如下：

1. acceptance reviewer 对完整合同承担最终验收责任，缺失任何 acceptance criteria、test requirement、constraint、
   non-goal、risk 或 evidence ref 都可能改变结论；
2. intake 阶段的 authoritative 业务输入仍是 requirement 与 validated proposal/revision context，task contract section
   是辅助历史上下文；
3. 全局改成全文必达会扩大 token 成本和 blast radius，却没有真实 intake drift 证据。

守卫测试必须同时断言 intake 配置未改变，防止未来把 acceptance policy 误推广到 sibling profile。

`profile=acceptance` 不允许通过 `include_sections` 切换到 legacy builder。当前 bundled definitions 没有该 override；
custom definition 若需要自定义 section 应使用其他 profile。存在 acceptance section override 时，context node 以
`MANDATORY_CONTEXT_SECTION_OVERRIDE_REJECTED` fail-closed，不能借 legacy path 删除或降级 task contract。

## 5. 原子设计

### 5.1 在既有 compile result 建立内容绑定

1. `TaskContractArtifactWriter.writeJson()` 完成后，对实际 `task-contract.json` 计算 SHA-256。
2. `ContractCompileHandler` 在既有 result map 写入 `task_contract_sha256`；不新增 sidecar、event、node、transition
   或 binding artifact。
3. 不把该字段加入 `ContractRegistry.requiredKeys/optionalKeys`。`NodeOutputContract` 约束的是
   `acpx.agent_run` 的 `AgentResultEnvelope`，`ArtifactContractValidator` 对 deterministic
   `contract.compile` handler 直接跳过；把 SHA 放入该 registry 会形成没有真实消费者的伪强制声明。
4. compile handler 必写 SHA 由直接 handler test 证明，acceptance verifier 再独立强制存在与匹配；既有
   `contract_id/status/version` 保持不变。
5. hash helper 只做 artifact bytes -> lowercase SHA-256，不承载生命周期或验证规则。优先提取一个
   package-private、可由 writer/verifier 共用的小 helper；不得复用名称和责任都属于 agent sidecar 的
   `AgentOutputContractSidecar.sha256()`。

### 5.2 Acceptance qualification 使用单一字节快照

增加 package-private `TaskContractProjectionVerifier`，只负责回答“当前 artifact 是否具备 acceptance 必达资格”：

1. 先读取 durable compile occurrences。若 `task-contract.json` 不存在且从未有 completed `contract.compile`，返回
   absent，保持 thin/custom workflow 的既有 placeholder；若已有 completed compile 却 artifact 缺失，则 fail-closed。
2. 若文件存在，读取一次 bytes，并在该 bytes 上同时完成 SHA 与 JSON parse；后续渲染不得重新读取文件。
3. 运行 `TaskContractValidator`，拒绝 parse 或 semantic errors。
4. 从 workstream `nodes/` 选择 `finishedAt` 最新的 completed `contract.compile` node，要求：
   - `status=COMPLETED`；
   - output refs 包含当前 canonical `task-contract.json` ref；
   - result 的 `task_contract_sha256` 与快照一致；
   - result 的 `contract_id/status/version` 与 parsed contract 一致。
5. completed compile 的 `finishedAt` 缺失、任一 node JSON 无法读取，或存在同一最新时刻的多个候选时，不允许
   静默跳过。分别归入 `COMPILE_NODE_INVALID`、`NODE_LEDGER_UNREADABLE` 或
   `AMBIGUOUS_COMPILE_BINDING` 并 fail-closed。
6. 若 artifact 存在但没有候选、最新候选字段缺失、SHA/identity/output-ref 任一不匹配，
   返回 outcome `TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED`，并携带 bounded reason（例如
   `COMPILE_BINDING_MISSING`、`ARTIFACT_MISSING`、`HASH_MISMATCH`、`IDENTITY_MISMATCH`、
   `CONTRACT_INVALID`）；context node 在写 package 和 provider dispatch 前 fail-closed。
7. verifier 返回不可变 `QualifiedTaskContract`（artifact 原始 UTF-8 JSON、parsed contract、SHA、source node
   id/ref）；renderer 只消费该对象，不扫描 ledger、不再次读文件。

这里的 lineage 是“当前 artifact bytes -> durable compile occurrence -> canonical output ref”的产生链。compile handler
已经负责 proposal/gate lineage；acceptance verifier 不重复实现 proposal validator，也不创建第二个 lineage owner。

### 5.3 完整 typed 投影恰好一次

1. `ContextBuildPackageHandler` 先拒绝 acceptance `include_sections` override；随后仅在 acceptance profile 且 task
   contract 存在时先 qualification，再构建 evidence manifest、repo-scope governance、预算和正文。
2. `ContextPackageSectionRenderer` 新增接收 `QualifiedTaskContract` 的渲染入口，使用
   `TypedContextPayloadBoundary.wrap(...)` 包裹 verifier 已验证的 artifact 原始 JSON，输出与 SHA 绑定的全字段内容；
   不用 `wrapJson(parsedContract)` 重新序列化，因此不存在 `createdAt` timestamp/ISO mapper 差异。删除 acceptance
   路径对 `appendTaskContract(Path)` 的二次读取和字段子集投影。
3. `contract-resolution.json` 保持既有独立投影；它不是 TaskContract 的一部分。
4. 同一完整 typed payload 在 context package 中必须恰好出现一次。State & Evidence Boundary 中 objective/status 的
   compact fact 可以保留，但不得再次嵌入完整 payload。
5. `ContextStateEvidenceBoundaryRenderer` 的 compact contract fact 与 `RepoScopeGovernance` 的 scope paths 必须消费
   同一个 `QualifiedTaskContract`；acceptance 路径不得再从磁盘读取合同。intake/其他 legacy profile 的现有读取语义
   不在本 checkpoint 中迁移。
6. `EvidenceManifest.rebuild` 增加接受 optional qualified snapshot 的重载。agent acceptance context 必须从该
   snapshot 构造 `task_contract` evidence item 的 claims/summary/source ref，不再调用 `addJsonArtifact` 读取合同；
   默认调用点继续使用无 snapshot 的既有重载；acceptance `AgentRunGovernanceContext` 按 §5.4 重新验证同一 identity
   后显式传入 snapshot，不冒充 context-build 时的 Java 对象。

### 5.4 跨节点 dispatch binding

context build 与 acceptance agent run 是两个 node，不能靠同一个 Java 对象跨越 durable 边界：

1. acceptance context metadata 在既有文件中记录 `taskContractSha256` 与 `taskContractCompileNodeRef`；不新增 binding
   文件。`ContextBuildPackageHandler.buildLegacyResult()` 必须仅在 `profile=acceptance` 且 metadata 已生成时，把
   metadata ref 显式加入 context node `outputRefs`。其他 profile 的 output refs 集合保持不变。
2. `AgentRunGovernanceContext.ensure()` 从 routed input refs 解析唯一一个 `profile=acceptance` 且 `targetNode` 等于当前
   node id 的 context metadata；缺失或多份匹配都 fail-closed。三个 bundled acceptance agent 均已有
   `expected_artifact=acceptance_review`，该 config 作为 consistency assertion，而不是触发 owner；custom definition
   只要消费 acceptance metadata 也必须走同一 binding，不允许静默回到 legacy 路径。
3. context metadata 是 machine-only routed artifact。`AgentPromptRenderer` 必须识别 canonical context metadata ref，
   不把它列入 Direct Input Artifacts、context正文或 read-reuse 文本；它只供 dispatch binding 消费。该过滤不读取
   metadata，也不根据其内容路由。renderer 同时只把 verifier 重新证明的 wrapped contract 加入 trusted typed
   payload 集合；否则既有 free-text sanitizer 会按不受信 marker 丢弃合同正文。
4. dispatch 前再次调用同一个 `TaskContractProjectionVerifier`，要求当前 snapshot 的 SHA 与 compile node ref 同
   routed metadata 一致。该检查不是第二 owner：compile result 仍声明事实，verifier 仍是唯一证明者，metadata 只携带
   上一 node 已消费的 binding。
5. `AgentRunGovernanceContext` 的 `EvidenceManifest` 与 `RepoScopeGovernance` 均消费这份重新验证且 identity 相同的
   snapshot，不再自行读取 task-contract。若合同在 context build 与 agent run 之间改变，handler 在调用 ACP transport
   前失败，不允许把不一致的两个投影同时发送给模型。
6. 非 acceptance agent run 保持既有 output refs、overload 和读取路径；本 checkpoint 不把 H1 扩张为所有 profile
   的合同治理。

### 5.5 Soft warning，不把经验预算伪装成 capacity

1. 在既有 `OverflowPolicy` 增加语义明确的 `PRESERVE_FULL`，只应用于 acceptance `task_contract`。
2. Phase 3 遇到 `PRESERVE_FULL` 时不计入 `allocated` 规划值，不设置 `degraded`、不 drop、也不 fail；它的成本只按
   Phase 4 渲染后的 actual tokens 进入最终 package accounting。当前唯一使用者是 acceptance `task_contract`。
3. section 超过 `softBudget` 时保持全文，设置 `softBudgetExceeded=true`；`degraded=false`、`dropped=false`，
   `originalTokens=renderedTokens=actual`。
4. Phase 5 继续优先执行其他 section 的既有 DEGRADE/DROP 策略；`PRESERVE_FULL` section 不参与 drop。
5. 只有在所有非 preserved section 已按既有策略处理后，且加入既有 budget-exempt required typed subjects 前的
   `governedTokens - preservedRenderedTokens <= totalBudget`，剩余 governed-body overage 才可归因于 preserved contract，
   context build 才允许完成并写 `withinBudget=false`。既有 required review subject 继续完整保留并计入 metadata/header，
   不因本 checkpoint 改变其 budget-exempt 语义。普通 profile 或没有 preserved section 的总预算超限仍按既有规则失败。
6. metadata 增加 package-level `budgetWarnings` 和 section-level `softBudgetExceeded`；warning 至少包含 section、soft
   budget、actual tokens、package total、estimated total。总 token 记账包含完整 payload。
7. header 可以投影 warning，但生命周期与路由不得解析 Markdown；metadata 是唯一 machine-readable warning owner。

### 5.6 Acceptance human gate packet 的范围声明

`HumanReviewGateHandler` 在更晚的 gate-open 时刻独立读取 TaskContract，并只投影字段子集。它不参与本次 agent
acceptance context build，不能复用本次 snapshot，因此本 checkpoint 不修改它，也不把 agent context 的
`ENFORCED` 扩大解释为“human packet 全字段完整”。该不对称记录为独立的 human-review projection residual；完成 H1
后与 H4 action visibility 分开评估，不能顺手混入本 checkpoint。

### 5.7 历史在途 workstream 迁移

checkpoint 之前 completed 的 `contract.compile` node 没有 `task_contract_sha256`。不做 hash backfill，也不根据当前文件
反推历史结果；这类在途 workstream 到达 acceptance 时以 `COMPILE_BINDING_MISSING` fail-closed，operator 必须从
contract compile/revision 边界重新生成合同后再继续。fresh workstream 不受影响。

### 5.8 Hard capacity 明确不实施

当前没有 provider/model authoritative capacity source，因此不增加 pre-dispatch 数字阈值，不提高 timeout，不把
经验 token ceiling 改名为 hard cap。真实 provider 超窗继续由 adapter typed failure 记录。未来只有 adapter 提供可验证
capacity metadata 时，才另立 checkpoint 把 `estimatedTokens <= authoritativeCapacity` 变成 pre-dispatch guard。

## 6. 修改文件

Production：

1. `dual-runtime/.../handler/ContractCompileHandler.java`
2. `dual-runtime/.../handler/TaskContractArtifactWriter.java`
3. `dual-runtime/.../handler/TaskContractProjectionVerifier.java`（新增 package-private owner）
4. `dual-runtime/.../handler/ContextProfiles.java`
5. `dual-runtime/.../handler/OverflowPolicy.java`
6. `dual-runtime/.../handler/ContextBuildPackageHandler.java`
7. `dual-runtime/.../handler/ContextPackageSectionRenderer.java`
8. `dual-runtime/.../handler/ContextStateEvidenceBoundaryRenderer.java`
9. `dual-runtime/.../handler/RepoScopeGovernance.java`
10. `dual-runtime/.../handler/EvidenceManifest.java`
11. `dual-runtime/.../handler/AgentRunGovernanceContext.java`
12. `dual-adapter-acpx/.../AgentPromptRenderer.java`（过滤 machine-only metadata ref，并只信任 verifier 证明的合同 payload）
13. `dual-adapter-acpx/.../AcpxAgentTaskHandler.java`（仅把 qualification exception 转为稳定 typed failed NodeResult；不改 transport）

Direct tests：

1. `ContractCompileHandlerTest.java`
2. `ContextTaskContractSectionTest.java`
3. `ContextBuildPackageHandlerTest.java`
4. 新增或合并 `TaskContractProjectionVerifierTest.java`
5. `dual-adapter-acpx/.../AcpxAgentTaskHandlerTest.java`（只证明 dispatch 前拒绝，不调用 provider）
6. `dual-adapter-acpx/.../AgentPromptRendererTest.java`

不修改 workflow JSON、ACP transport/provider protocol、TaskContract schema、ledger schema、UI/CLI/API、R1 mutation
路径或 H4 action 语义。adapter 侧 production 变化仅为 prompt projector 的 metadata 过滤/trusted payload 消费，以及
handler 对 qualification exception 的窄 typed 投影；ACP transport/provider protocol 不变。

## 7. Cannot-bypass tests

T1. compile 写入的 `task_contract_sha256` 与落盘 bytes 一致；直接 handler test 断言每次 completed result 必含该字段，
且不依赖只约束 ACP envelope 的 `ContractRegistry`。
T2. 构造超过 2500 tokens 的合法合同和真实 completed compile node；acceptance context COMPLETED，完整 typed payload
逐字出现一次，所有 criteria/tests/constraints/non-goals/risks/evidence refs 均存在。
T3. T2 metadata 断言 `softBudgetExceeded=true`、warning 字段完整、`withinBudget=false`（当 fixture 总量确实超过
6500）、actual token 计入 estimated total，section 未 degraded/drop。
T4. 同一 fixture 中低优先级 section 仍按既有 DEGRADE/DROP 执行，合同不因给其他 section 腾空间而丢失。
T5. 篡改合同 bytes 后 SHA 不匹配，context node FAILED 且不写 context package。
T6. 合同 parse/semantic invalid、compile node 缺失、output ref 错误、identity/version/status 不匹配、旧 compile hash 与
最新 artifact 不匹配、历史 compile 无 SHA、completed node 无 `finishedAt`、node JSON 损坏，分别以 bounded reason
fail-closed。
T7. 无 compile occurrence 且合同文件缺失时 thin/custom workflow 仍输出 placeholder；已有 completed compile 但文件缺失，
或存在文件但无可信 binding 时，不得降级为 placeholder。
T8. intake profile 仍为 `SUMMARY / DEGRADE / 500`，超限仍使用既有有界策略。
T9. acceptance `include_sections` override 在任何 artifact/package 写入前以
`MANDATORY_CONTEXT_SECTION_OVERRIDE_REJECTED` 失败；implementation/full 等 sibling profile 的 override 与
FAIL/DEGRADE/DROP 行为保持不变。
T10. 单快照测试：qualification 后替换磁盘文件也不能改变本次 typed payload、State & Evidence compact fact、
repo-scope paths 或 EvidenceManifest claims；agent acceptance context build 的四个消费者不得二次读取合同文件。
T11. 跨节点 dispatch：routed acceptance metadata 的 SHA/compile ref 与当前 verified snapshot 一致时 governance context
正常构建；在两节点之间替换合同、传入陈旧/歧义 metadata 或缺失 binding 时，`AcpxAgentTaskHandler` 在 transport 调用
前失败。测试必须先断言 acceptance context node 的 `outputRefs` 确实包含 metadata ref；还要断言该 ref 不出现在最终
prompt 的 Direct Input Artifacts/context/read-reuse 正文。非 acceptance context 的 output refs 与 agent prompt 保持不变。

## 8. 实施顺序

1. 先为 compile result 加 SHA，跑 compile focused tests；不得改 `ContractRegistry` 冒充 deterministic handler result
   enforcement。
2. 实现 verifier 与 binding failure tests。
3. 增加 `PRESERVE_FULL` 和 metadata warning，保持 sibling profile tests 绿色。
4. 把 acceptance task-contract renderer 改为 qualified snapshot，并补 >2500/single-occurrence tests。
5. 写入 context metadata binding，仅由 acceptance context 路由；让 `AgentPromptRenderer` 过滤 machine-only ref，并让
   acceptance `AgentRunGovernanceContext` 在 transport 前重验同一 identity。
6. 跑 focused runtime/adapter tests、full reactor、`git diff --check`。
7. 做一次独立代码级 diff review；WARN/FAIL 回到边界分析，不启动 provider/real-case。

## 9. End-to-end invariant

| 维度 | 证明 |
|---|---|
| Declared by | completed `contract.compile` result 声明 artifact identity/SHA；acceptance profile 声明 `PRESERVE_FULL` |
| Proven by | 单字节快照 SHA + parse + `TaskContractValidator` + compile-node identity/output-ref |
| Consumed by | context-build 四个读取者与 acceptance run governance 只消费 verified snapshot；acceptance prompt 消费 identity 一致的 context refs |
| Enforced before | qualification failure 在 package write 前终止 context node；cross-node binding mismatch 在 ACP transport 前终止 agent run |
| Failure route | no compile/no artifact -> thin placeholder；compile/artifact 不一致 -> typed FAILED；budget overage -> typed warning，不伪装失败 |
| Cannot-bypass | T1-T11，尤其篡改、陈旧、单次投影、单快照、跨节点 dispatch 和 sibling regression |
| Residual raw/legacy | intake summary 明确保留；human acceptance packet 子集投影单独记录；无 provider capacity source；旧 `appendTaskContract(Path)` 仅可留给非-acceptance legacy 路径且不得成为 acceptance bypass |

## 10. 成熟度与退出条件

完成 deterministic checkpoint 后只能声明：

```text
qualified full projection + soft warning: ENFORCED
provider hard-capacity preflight: NOT MODELED（authoritative source absent）
real-case value: NOT PROVEN
```

若实现必须新增 binding 文件、workflow node/schema、通用 budget engine，或无法用单一快照同时完成校验与渲染，停止
编码并回到架构评审；不得用更大的常量或 handler-local `task_contract` 字符串分支替代该计划。

## 11. 实施结果（2026-08-26）

计划已按单一 checkpoint 实施，未修改 workflow JSON、schema、module、registry、provider 配置或 Agent 调用数。
实现建立了 compile result SHA、latest completed compile qualification、单一字节快照、acceptance-only durable
metadata binding、dispatch 前重验、machine-only prompt filtering 与 acceptance override fail-before-write。

deterministic 验证结果：

- focused runtime + adapter：`83/0/0/0`；
- full Maven reactor：9/9 modules，`1270/0/0/0`；
- `git diff --check`：clean。

cannot-bypass 测试在实施中发现 Phase 5 total-budget reverse-drop 仍可能丢弃 `PRESERVE_FULL` section；该旁路已关闭，
Phase 3 soft-budget degradation 与 Phase 5 package-budget drop 现在都明确跳过 mandatory contract。另将 verifier 收敛为
package-private owner，只通过 `AgentRunGovernanceContext` 暴露 adapter 所需的窄 façade。

当前仅可声明实现已通过 deterministic enforcement 验证，仍须独立代码级 diff review 后才满足选择性提交条件；
provider hard-capacity 仍为 `NOT MODELED`，real-case value 仍为 `NOT PROVEN`。

独立代码级 diff review 结论为 `PASS（P0=0 / P1=0 / P2=3）`，并独立复现 full reactor
`1270/0/0/0` 与 `git diff --check` clean。P2-1 是 bounded reason guard-matrix 的后续覆盖增强，不影响当前单一
verifier 的 fail-closed 机制；P2-2 所指文件清单偏差在最终计划 §6 中已闭合，adapter production 清单明确包含
`AcpxAgentTaskHandler`；P2-3 是已批准的“任一 node ledger 损坏即阻断 acceptance”保守语义。当前满足选择性提交条件。
