# B03 continuation — implementation preflight

User authorization: complete runtime assembly, preparation/execution/recovery, failed-readiness revision and remaining consumers; then supply an independent CC review prompt. Existing B03 completion criteria remain unchanged. No provider/native runs, commits or additional agents.

Source baseline: `before-source.json` (614 source/resource/POM files, including pre-existing uncommitted content). This document is an implementation plan, not proof of completion.

| Transition | Authority / owner / next consumer | Ordered writes and effects | Failure / restart / sibling obligations |
|---|---|---|---|
| Assembly → initialization | Actual release contents and behavior configuration → WorkstreamDocument frozen identity → every ledger session | Verify the executing entrypoint against the same CLI/server release contents; then L00 | Different executable or missing identity rejects. CLI/server/worker belonging to the same frozen release must continue each other's work; no HEAD/version-string substitute. Development classpath must be complete, not only application classes. |
| Ready/queued → unique execution | Current node/task/input + frozen identity; execution writer owns claim | L02 claim, executor PID + start identity and input binding together | Duplicate claim and terminal/stale node reject. Worker lookup and claim share L. A dead PID alone is not evidence of no external effect. |
| Claim → prepared → external intent | Run owner owns immutable preparation; execution owns its prepared reference | R01 bytes → release run lock → short L bind → R02 durable intent → external call | Unbound preparation has no dispatch authority. Intent without completion is UNKNOWN, never automatic resend. No nested run/L locks. |
| Actual result → local route | R03 immutable result/process/outcome/input identity; L03 consumes exactly that claim | Persist fixed completion → release run lock → L recheck claim/input → task/execution/node/events/successors | Interruption after R03 does not call provider again. Invalidated claim retains historical result without success routing. Duplicate same completion is idempotent; different completion rejects. |
| Readiness failure → repair | Current failed occurrence + explicit user subject/note; existing revision owner | L06 closes old eligibility, records obligation and new plan occurrence/current/events | No fake gate/reviewer verdict. No unresolved execution may be superseded. Author → independent review → same-authorization check or renewed execution approval. |
| Query/evidence/delivery/recovery | One explicit ledger session; existing domain owners | Read a coherent snapshot or prepare one local write set; external actions remain outside replay | BUSY / PENDING / INVALID remain distinct. CLI/API/report consume the same facts. Old ledger/new binary are not silently migrated. |

Verification must include legal continuation as well as rejection, local write prefixes/cold restart, and inline/worker/CLI/API siblings. Self-check is not independent CC review. Scope/identity/approval/evidence requirements are unchanged; discovered design decisions not covered by the frozen contract must be reported before implementing them.

## Recovery sibling completion (2026-10-06 continuation)

- Pre-GOAL UNKNOWN: exact current failed occurrence/task/claim + immutable R01 without a contract is legal only when that definition does not require a contract. Epoch is explicitly 0, not a fabricated approval. Existing stop/risk checks and the same L06 retry/abort mutation still apply. Partial or required-but-missing contract bindings reject.
- ADOPT_VERIFIED_RESULT: explicit current UNKNOWN decision + original fixed R03 completion, exact input/output/process identity; no native dispatch, no new attempt and no reopening permission replies. L06 decision and L03 task/execution/node/route are one prepared mutation. Prior UNKNOWN observation is retained. Normal terminal-completion APIs remain closed; only this typed adjudication admits the original late result. Before manifest no state change; pending finishes the fixed mutation; repeated same decision is idempotent; abort/retry/replacement/stale event/wrong bytes reject.
- This implements the frozen D07 dispositions, not history recovery or pasted-result import. Six-dimensional proof and CLI/API/inline/worker siblings remain required before completion.

## 查询消费者补迁移

CLI summary/timeline/inspect/review show 与 server overview 消费同一短 L read session。SoT 为已提交 current/node/gate；timeline 从该快照构造，metrics 缓存只有 commitSequence 与 current 一致才可展示。无写入、无外部执行；BUSY/PENDING/INVALID 不回退到旧缓存，不生成推进建议。导出报告是非 authority，生成于快照后可写出且保留源序号。验证：正常节点及门可展示、过期缓存不冒充当前、pending/busy 拒绝、refresh 不改变 domain 状态；不得把本轮 fixture 迁移当旧 ledger 兼容。

## 残余稳定合同投影迁移（消费者族）

前态：GOAL/EXECUTION 由 compile occurrence + ref/SHA + 实际批准拥有；artifacts/task-contract.json 不再有 authority。触发：context/evidence/report/gate/scope/failure-classification 读取。唯一消费资格：TaskContractProjectionVerifier 的 phase 解析；必需消费者缺 phase/缺批准返回失败，纯展示缺合同显示不可用。无任何 durable write 或外部调用先于资格。成功展示同一个不可变 ref；失败不得回读旧稳定路径。兄弟：HumanReviewGateHandler、ContextBuildPackageHandler、EvidenceManifest、RepoScopeGovernance、ReportGenerateFinalHandler、ArtifactValidateHandler、FailureClassifyHandler，删除 readLegacyProjection 与专用 renderer。旧账本不提供兼容解释；新测试保留旧路径诱饵并证明不能取得 authority。

### Authored route integrity found during consumer regression
The approved phased chain always creates authored consumers through a route. A missing route event must not fall back to the latest authority. The resolver owns semantic lineage; it must require exactly one creation route, and each predecessor link must preserve the completed producer's output refs and declared edge. This blocks author/self-test dispatch and context publication before provider use. Legal unchanged retries retain the original route. Negative evidence: consumer-39, missing-route and extra review at a human gate; positive obligation: ordinary author/review/approval plus repair siblings and cold reader.

Refinement from the positive repair regression: readiness-repair successors consume failed-source input refs plus explicitly recorded repair evidence. They do not consume failed outputs and have no ordinary definition edge. Verify the matching typed repair decision, route payload, parent SHA, target and refs; do not require successful parent status for this named recovery transition. consumer-40 caught the over-broad initial success condition.

### Terminal no-op and projection refresh
Four-variant terminal regression (consumer-50) found that an empty local-recovery session rewrote metrics timestamps even with no authoritative mutation. Session.commit now refreshes only after actually applying a prepared mutation (including finish-forward). Explicit projection refresh remains its existing operation. Terminal resume/retry/approve/worker must keep all domain/evidence bytes and projection bytes unchanged; test snapshots use SHA rather than dumping full base64 artifacts. No terminal reopening or relaxed assertions.
