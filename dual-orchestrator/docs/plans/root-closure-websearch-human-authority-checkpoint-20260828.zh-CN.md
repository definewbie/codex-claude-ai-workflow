# Root Closure ETL 与 WebSearch Human Authority Checkpoint

日期：2026-08-28  
状态：`IMPLEMENTED / DETERMINISTIC PASS / AWAITING INDEPENDENT DIFF REVIEW`

## 1. 目标与边界

本 checkpoint 只关闭两个已被 Mailpit real-case 暴露的边界：

1. final assistant message 仅缺 root JSON 最后一个 `}` 时，在 strict parser 前执行一次可审计、有限的 presentation ETL；
2. ACP 当前 occurrence 请求 `toolKind=fetch + toolName=WebSearch` 时，默认拒绝并投影为 human recovery；operator 对公开仓库
   做显式 attestation 后，只给该 node 的下一次 execution 一次 `WebSearch` tool auto-approval。

不解决、也不宣称解决：

- Bash、docker、git、Maven、npm 等子进程的总网络出口；
- WebSearch query/domain 的逐请求强制过滤；
- WebFetch 或任意 fetch 自动批准；
- 通用 JSON 修复、parser 放宽、correction/retry 增加；
- workflow graph、business contract 或产品代码。

总网络 authority 已独立进入
`docs/plans/post-real-case-common-boundary-convergence-plan-20260828.zh-CN.md` 的 E1 capability assessment；本 checkpoint
不得冒充 E1。

## 2. Captured 证据

workstream：`ws-20260828T054556Z-AHBV`。

两个自然 occurrence：

- `run-1787910442949`：`presentationDisposition=ROOT_OBJECT_CLOSURE_RECOVERED`，strict parse/schema/semantic/artifact
  validation 通过，node result 为 `READY_FOR_PLAN_REVIEW`；
- `run-1787910664384`：同一 disposition，strict contract 通过并产生 typed `BLOCKED`；permission denial 被保留为
  secondary signals，没有覆盖完整业务 contract。

两次 raw output 均由 parser 先判 `UNEXPECTED_END_OF_INPUT @ exact EOF`，ETL 追加一个 root `}` 后再进入同一 parser。
没有第二 parser、schema fallback 或自然语言路由。

WebSearch 人工批准链在本 real-case 中没有自然执行，因此只能标 `DETERMINISTIC ENFORCED / REAL-CASE NOT PROVEN`。

## 3. Root Closure owner 与算法

Owner 保持不变：

- raw assistant message：ACP stream occurrence；
- presentation normalization：`AcpxAgentResultAcquirer`；
- JSON grammar：`AgentResultEnvelopeParser`；
- schema/semantic/artifact：既有 validator 链。

只有以下条件全部成立才尝试追加一个 `}`：

1. strict parser 返回 `UNEXPECTED_END_OF_INPUT`；
2. diagnostic offset 等于 candidate 长度；
3. 第一个非空白字符是 `{`；
4. 扫描时 string/escape 状态闭合；
5. delimiter stack 恰好只剩 root `{`；
6. 不存在 mismatched/extra closing delimiter；
7. 最后一个非空白字符不是 `,`、`:`、`{`、`[`；
8. presentation 不是 ambiguous multi-document/multi-fence；
9. 追加后同一 strict parser 必须完整通过，否则保留原始失败。

恢复后的 candidate 仍经过 duplicate key、unknown field、trailing token、schema、semantic、artifact 与 lineage validator。
occurrence 同时保存 raw/candidate SHA 与 typed presentation disposition。

## 4. WebSearch human authority

### 4.1 Declared 与 projected

- permission profile 把 `fetch` 放入 ACP `escalate`，`defaultAction=deny`；
- session stream projector 将 permission request 与同 call id 的 tool name 关联；
- transport 只对 `fetch + WebSearch` 写入 `authorityDisposition=ESCALATION_REQUIRED`；
- `RecoveryAssessment` 仅在 typed `agent_permission_blocked` 且 exact escalation evidence 存在时投影：
  `agent_permission_approval_required` 与 actions `approve_public_websearch | abort`。

### 4.2 Human enforcement

CLI、API、UI 最终都调用 `WorkflowFileLedger.approvePublicWebSearch`。ledger 在持锁条件下重验：

- active node 为 FAILED；
- current recovery assessment 允许该 action；
- run ref 位于当前 workstream；
- timeline 存在 exact `fetch + WebSearch + ESCALATION_REQUIRED`；
- tool call id/query 非空；
- operator note 非空且 `public_repository_attested=true`。

通过后，ledger 把 source run ref、tool call id、query SHA、note、批准时间写入 node retry result/event；handler 只在该 READY
node execution 中给 permission profile 增加 `WebSearch`，不增加 `WebFetch`。该 execution 完成时 routing 用新的 result 覆盖 retry
result，后续普通 retry 不再携带 grant。cannot-bypass 测试已直接钉住这一点。

### 4.3 精确语义

这里的“exact”指 **exact tool (`WebSearch`) + one node execution**，不是 query/domain allowlist。operator attestation 表达“该公开
仓库任务允许下一次 execution 使用 WebSearch”；prompt 约束 query 不包含 secrets 只是 soft guidance。若需要对 Bash 子进程网络或
WebSearch domain 做机械限制，必须由 E1 OS sandbox/provider policy 处理，不能扩张本 approval 的含义。

## 5. End-to-end invariant

| 维度 | Root closure | WebSearch approval |
|---|---|---|
| Declared by | presentation normalization conditions | permission profile + recovery action |
| Proven by | parser diagnostic + delimiter scan + second strict parse | current run timeline exact request + operator attestation |
| Consumed by | same parser/validator chain | handler occurrence permission profile |
| Enforced before | typed envelope projection/routing | retry execution starts |
| Failure route | original parse failure/correction policy | FAILED recovery or abort；无 grant |
| Cannot bypass | malformed/truncated/multi-doc/fence/semantic tests | wrong tool/kind/run/node/attestation、second retry tests |
| Residual | 数字字面量中途截断可能在追加 `}` 后仍形成合法值；其语义完整性只能由 schema/semantic/artifact owner 兜底，raw/candidate SHA 与 typed disposition 必须保留审计 | query/domain 与 Bash network 不在本 checkpoint |

## 6. 文件范围

- adapter：result acquisition、permission profile、stream projection、transport health、handler prompt projection；
- ledger：recovery assessment、approval/retry transition、projection refresh；
- CLI/server/UI：同一 ledger action 的入口与展示；
- runtime capability catalog：named permission profile 优先于 legacy `approve_all`；
- 对应 focused/cannot-bypass tests。

零 workflow JSON、contract schema、provider、product、Git delivery production 变更。另一个未跟踪文档
`post-real-case-common-boundary-convergence-plan-20260828.zh-CN.md` 不属于本 production checkpoint，可独立文档提交。

## 7. 验证结果

- focused：adapter `105/0/0/0`、ledger `58/0/0/0`、runtime `5/0/0/0`、CLI `1/0/0/0`、server `13/0/0/0`；
- one-retry grant consumption 单测：`1/0/0/0`；
- full reactor：`1260/0/0/0`，BUILD SUCCESS；
- `git diff --check`：待独立评审前最终复核；
- provider/新 real-case：未调用。

## 8. 独立评审门槛

评审必须独立核实：

1. root closure 是否可能接受多文档、深层截断、未闭合 string、trailing garbage 或 parser 非 EOF failure；
2. recovered candidate 是否无条件回到唯一 strict parser + validator 链；
3. permission request 的 toolName/callId/query 是否来自同一 occurrence；
4. CLI/API/UI 是否都经过 ledger boundary，是否存在直接重试绕过；
5. grant 是否只活过一次 node execution；
6. named profile 与 legacy `approve_all` 的 sibling 行为是否一致；
7. 文档是否诚实区分 WebSearch tool authority 与 total network authority。

独立 diff review PASS 前不得提交 production，不实施 D1/D2/E1，不调用 provider。

## 9. 成熟度

| 能力 | 状态 |
|---|---|
| strict root-object closure ETL | `ENFORCED / NATURAL REAL-CASE PROVEN (2 occurrences)` |
| WebSearch one-execution human authority | `DETERMINISTIC ENFORCED / REAL-CASE NOT PROVEN` |
| WebSearch query/domain filtering | `NOT MODELED` |
| Bash/subprocess total network authority | `NOT MODELED` |
