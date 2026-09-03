# Change-Scope Violation Repair Routing Bounded Comparison

日期：2026-09-03  
状态：`ASSESSED / AWAITING REVIEW`

## 1. 当前事实

OKX workstream 在 `implement.artifact_validation` 机械失败：53 个实际变化中有 16 个
`UNEXPECTED_ACTUAL_CHANGE`，同时有 12 个 declared path 未出现。失败发生在产品测试与 self-test
均完成之后，workflow 进入 `WAITING_FOR_RECOVERY / evt-000059`。

失败包含两类，不能混为一谈：

1. **未授权路径漂移**：测试类被放到根 package，fixture 改了目录/文件名，并新增 3 个 support helper；
   这些都与 TaskContract 的 exact paths 不一致，validator 拒绝正确。
2. **人工 repair 触发的实现选择**：`CandleCommitService.java` 是落实“独立 Spring 协作者”的方式，
   但 human revise 的自由文本不拥有 TaskContract scope，不能自动把该文件变成授权路径。Agent 应改为在
   已声明文件内实现等价协作者；若业务需求确实必须扩 scope，应回到正式 requirement/task-contract revision，
   不能由 implementation repair 偷渡。

因此本轮不修改 TaskContract、不自动接受当前 diff，也不把 validator 放宽为目录或 package 匹配。

## 2. 真正的 Harness 缺口

`ArtifactValidateHandler` 已产出完整 typed evaluation，但对任何 violation 都返回 `NodeStatus.FAILED`。
`RoutingEngine` 对非 COMPLETED 节点直接进入 recovery，不消费 workflow edge。当前三个 bundled lifecycle
与一个 small-plan experiment 中，
`implement.artifact_validation` 也只声明 `completed -> acceptance context`，没有 scope violation 的 repair route。

结果是：

- Dual 能发现错误，却没有合法动作把证据送回既有 implementation repair loop；
- `retry` 只会重跑同一 deterministic validator，无法改变 workspace；
- operator 只能越过 ledger 手工改文件，或放弃 workstream；
- 错误直到昂贵 self-test 后才显形。最后一点是效率问题，前三点是恢复完整性问题。

## 3. 候选比较

| 候选 | 做法 | 判定 |
|---|---|---|
| A. 人工改 workspace 后 retry | operator 直接移动/删除文件，再重跑 validator | `REJECT`：修改没有 AgentResult、iteration 与 human decision lineage |
| B. human note 自动扩 TaskContract | 从 revise note 推导 scope delta | `REJECT`：自由文本成为第二 scope owner，绕过 compiled contract |
| C. typed violation 路由到既有 repair gate | validator 在 definition 明确启用时把 scope violation 作为已完成的 typed outcome；edge 路由到现有 human implementation repair gate；evidence unavailable 仍 FAILED | `RECOMMENDED`：复用现有 gate、note、iteration 与 implement loop，不新增 action/schema/node family |
| D. 同时重排 validation 到 self-test 前 | 改 graph 并约束 self-test 不得写产品 | `DEFER`：能省 provider 成本，但需要新的 write-boundary 证明；不是当前恢复闭环的必要条件 |

## 4. 推荐边界

### 4.1 Source of truth

- scope authority：现有 compiled `TaskContract.scope`，不变；
- actual changes：现有 `git-evidence` 与 `change-scope-evaluation.json`；
- repair authority：现有 human implementation repair Gate 的 typed action + note；
- workspace mutation：下一 implementation Agent occurrence，operator 不直接改产品。

### 4.2 最小行为

1. `artifact.validate` 增加一个 definition-owned 的显式 disposition 配置；未配置时保持当前
   violation=`FAILED/recovery` 兼容语义。
2. 配置开启时，只有 `ChangeScopeEvaluation.Status.FAIL` 返回
   `COMPLETED / CHANGE_SCOPE_VIOLATION`；`UNAVAILABLE` 仍 fail-closed 为 FAILED。
3. 三个 bundled lifecycle 加一个 small-plan experiment sibling 的 artifact-validation node 均启用该 disposition，并声明
   `CHANGE_SCOPE_VIOLATION -> gate.human_implementation_repair`；success route 不变。
4. `ArtifactValidateHandler` 将本次完整 git evidence 写到 node-instance-scoped occurrence 目录，
   并把 occurrence refs 随 node output 路由；稳定 `artifacts/git-evidence/*` 只能保留为 latest projection，
   不再作为 Gate authority。`HumanReviewGateHandler` 只能从 routed occurrence ref 解析 evaluation，
   显示 `violations/missingDeclared` 明细，禁止目录扫描与 stable-path fallback。
5. human 只能 `revise`。`RoutingEngine` 对 `gate_purpose=implementation_repair` 的 revise 一律要求非空 note，
   不再只在 source outcome=`BLOCKED` 时强制。revise 后进入现有
   context/implement loop，TaskContract 不变，Agent 必须让 exact path evaluation PASS。
6. repair edge 的 `loopReason` 改为通用的 human-authorized implementation repair，不再声称只来自 blocked self-test。
7. 不新增 scope delta、action、schema、handler family、fallback 或 OKX 路径特判。

## 5. Cannot-Bypass Tests

1. configured violation：validator node COMPLETED，outcome 精确为 `CHANGE_SCOPE_VIOLATION`，
   gate 打开且 packet 含 routed occurrence 的 violations/missingDeclared evidence；target workspace 不被修改。
2. sibling validator 覆盖 stable projection 后，既有 Gate packet 仍能逐字节读取自己的 occurrence evidence。
3. 删除/tamper occurrence evaluation，或只留下完整 stable projection：Gate 必须 fail-closed。
4. operator revise：空 note 拒绝；非空 note 创建新 iteration 指向既有 implement context，note 与 evidence lineage 保留。
5. unconfigured/custom workflow：保持 FAILED/recovery，不能静默完成。
6. evidence unavailable：即使配置开启也必须 FAILED/recovery。
7. PASS：仍只走 acceptance，不打开 repair gate。
8. sibling sweep：三个 bundled definition 与 small-plan experiment 的 edge、handler、outcome 一致。
9. 删除 violation edge 或 disposition 配置时，definition consistency test 必须失败。

## 6. 当前样本处置

当前 `node-implement-artifact_validation-058` 保持 FAILED，不执行 retry。该 workstream 的
`gate.human_implementation_repair -> context.implement_claude` revision budget=`1`，并已在 `evt-000047`
消费；persisted definition 又不存在新 disposition/edge。因此当前样本**不可安全迁移或恢复，结论为 FAIL**。
不得提高限额、修改 frozen definition、手工改 ledger/workspace 或把 stable evidence 解释为授权。

上述 checkpoint 只服务后续 fresh workstream；它经独立设计评审、focused/full tests 与独立 diff review
PASS 后，才能用 fresh real-case 证明新 route。

## 7. 成熟度

- exact change-scope detection：`ENFORCED / NATURAL REAL-CASE PROVEN`；
- scope violation repair routing：`NOT MODELED`；
- occurrence-scoped change-scope review evidence：`NOT MODELED`；
- human note 修改 TaskContract scope：`FORBIDDEN`；
- validation-before-self-test optimization：`DEFERRED`。
