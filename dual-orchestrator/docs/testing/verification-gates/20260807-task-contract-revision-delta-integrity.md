# Verification Gate: Task Contract Revision Delta Integrity

日期：2026-08-07

状态：`REVIEW PASS / READY / NOT ACTIVE`。本文件只定义一次受控 revision
continuation；独立只读同行评审已 PASS，尚未授权 provider probe、retry、resume 或
real-case 执行。

## 1. 为什么不启动新的完整 workflow

新 workflow 是否进入 `NEEDS_REVISION` 无法在调用 provider 前证明。为了验证本
checkpoint 而启动完整样本，可能直接得到 `READY`，消耗 token 后仍无法证明 revision
delta boundary。

本 Gate 使用 PHZX 已持久化、可校验的 revision 起点：

```text
iter-004
  triggerAction = NEEDS_REVISION
  parentIterationId = iter-003
  triggerNode = node-intake-decision-015
  targetNode = node-intake-claude_brainstorm-016
  obligations = CO-7..CO-11
  workflow = WAITING_FOR_RECOVERY
  recovery action = retry
```

历史 worktree 只作为 immutable source。执行时必须复制到 disposable detached Git
worktree，不得修改原 PHZX ledger。

## 2. Frozen source

- Dual checkpoint：`6fb51608` (`fix(contract): enforce revision delta integrity`)。
- Source worktree：
  `/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260807T050827Z-PHZX`。
- Workstream：`ws-20260807T050827Z-PHZX`。
- Target HEAD：`f37485877dd42a1b69c378cdfb20e78cfa6c357e`。
- Target tree：`cdd03a619b52b255e5435f03d03dbfc95c30ec90`。
- Requirement SHA-256：
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`。
- Workflow definition SHA-256：
  `53ff9473de296685523451f4c604ced7a351fa3d36386db6f95f5049ed879e6c`。
- Workflow current SHA-256：
  `e3b3e3a8299e20a3ff43de04b50ccb78585400e08758e2533a1a702350583a4b`。
- `iter-004.json` SHA-256：
  `10fd9f40c0b31f9e58c5815048673bf06389ba3d338a5e1ee1f96aa9a4f926b9`。
- Active node SHA-256：
  `8759c05ec53da6f3ca9662933dfb27c96b471a6eefc8ccd95f83e75c53abdc38`。
- Trigger decision SHA-256：
  `1f4515e9779d531eb828c5b830a09d855ab050129cd3fb940f1c0a41247f28e5`。
- Rejected review sidecar SHA-256：
  `b79881c479514698a9e7ee4cd0c01f48621133728605e58fa328f30c653bd82c`。
- Review subject proposal artifact SHA-256 declared by the verified reference：
  `ff1b0c903c68d1106c20f20255aed412f0da99d08c09cb4c27cd3f976e488952`。

Any source hash or typed identity mismatch makes the run `INVALID` before a
provider is called.

## 3. Verification Gate

- **Target:** From the frozen typed `iter-004` revision boundary, execute one
  Claude revision attempt and prove exactly one of two valid outcomes:
  1. an undeclared clause modification/addition/deletion is rejected before a
     trusted proposal reference is published and before Codex review dispatch;
     or
  2. a mechanically valid declared revision publishes a verified proposal
     reference and reaches exactly one Codex product-review execution.

- **Source of truth:** The cloned workstream's `workflow/current.json`,
  `iterations/iter-004.json`, trigger decision node, verified rejected review
  sidecar, verified `TaskContractProposalReference`, previous proposal artifact,
  retried intake node execution, its strict result and contract sidecar,
  `artifact_contract_results`, newly published proposal reference set, node and
  execution documents, and all post-cutover `runs/*/run.json` occurrences.

- **Trigger precondition:** Before provider execution, the disposable clone must
  exactly preserve the frozen hashes above; current workflow status must be
  `WAITING_FOR_RECOVERY`; the only active node must be
  `node-intake-claude_brainstorm-016`; that node must be failed/recoverable in
  `iter-004`; `iter-004` must be triggered by `NEEDS_REVISION` from completed
  `node-intake-decision-015` in `iter-003`; its rejected review must contain
  non-empty unique obligations `CO-7..CO-11`; its `review_subject` must resolve
  to the exact previous proposal; the recovery gate must allow `retry`; the
  durable stale PID must not be live. Dual HEAD must be clean and contain
  `6fb51608`.

- **How precondition is checked before run:** Create a detached target worktree
  at the frozen target commit; copy the historical `.agent` directory into it
  without changing typed lineage files; verify every hash and typed field above;
  record a cutover manifest containing all existing run IDs, node execution IDs,
  proposal reference paths, Codex review occurrences, Git SHA/tree and file
  hashes. Run the focused revision tests and fresh Maven package from clean Dual
  HEAD. Before probes, record whether each workstream-scoped acpx session exists,
  its status and last known Agent exit information. Session reuse is allowed and
  must be reported because it preserves PHZX conversation history; do not force a
  new session merely to influence which PASS branch the model produces. Run one
  minimal Claude runner probe and one minimal Codex runner probe. Any mismatch
  stops before `dualctl retry`.

- **PASS A - invalid delta blocked:** The retried Claude process exits zero and
  emits a strict parseable schema-v3 proposal, but the actual clause delta
  contains an undeclared modification/addition or any deletion. The intake node
  fails with `TASK_CONTRACT_REVISION_DELTA_INVALID`; errors identify the typed
  delta code and clause ID; no new trusted proposal reference is published; no
  post-cutover `intake.codex_product_review` node execution or provider
  occurrence exists; workflow stops at the typed artifact-contract failure
  boundary. This proves early rejection.

- **PASS B - valid revision reviewed:** The retried Claude process exits zero
  and emits a strict parseable schema-v3 proposal; revision coverage and delta
  validation both pass; a new proposal reference with matching hash/lineage is
  published. Execute only the deterministic context-build node(s) required to
  make `intake.codex_product_review` active, then execute exactly one Codex
  product review. The review's `review_subject` must resolve to that exact new
  proposal. The Codex decision may be `READY_FOR_PRODUCT_REVIEW`,
  `NEEDS_REVISION`, or `BLOCKED`; this Gate proves legal dispatch and subject
  integrity, not semantic acceptance.

- **FAIL:** Claude or Codex process exits non-zero; strict parse/schema fails;
  bounded correction occurs or fails; permission/session/provider failure
  occurs; an invalid delta publishes a proposal reference or starts Codex; a
  valid delta cannot publish/resolve its reference; Codex reviews a different
  proposal; lineage is ambiguous; more than one Claude revision occurrence or
  more than one post-cutover Codex review occurrence is created; the workflow
  advances beyond the first Codex review result; or terminal execution facts are
  incomplete.

- **INVALID:** Frozen source, clone, target tree, Dual SHA, fresh package,
  runner-kind, probe, cutover manifest or typed trigger precondition is absent or
  mismatched; the historical source is modified; old jars/workers are used; the
  retry executes a node other than `node-intake-claude_brainstorm-016`; or the
  source recovery boundary cannot be safely cloned. `INVALID` consumes no
  replacement sample and cannot be reported as partial PASS.

- **Stop-loss:** Do not use `autorun`. Execute one CLI boundary at a time. After
  `retry`, allow exactly one resume that can invoke Claude, then inspect the
  resulting artifact decision before any further resume. PASS A stops
  immediately. For PASS B, allow only the deterministic context-build resumes
  needed to activate the reviewer and one resume that invokes Codex; stop as soon
  as that review is terminal. Stop immediately on correction, provider failure,
  unexpected node, second Claude occurrence, second Codex occurrence, ambiguous
  lineage, scope/write activity, or 20 minutes total. Do not retry, resume past
  the first review, approve a human gate, compile, change prompt/parser/model, or
  start a second sample.

- **Run artifact freshness:** Fresh Maven package must complete after the latest
  JVM source mtime. Persist in the gate report: clean Dual HEAD, checkpoint
  ancestry, package start/end, jar path/mtime/hash, target HEAD/tree, all frozen
  hashes, disposable clone path, cutover counts, runner kinds, workstream-scoped
  session existence/status/last-exit facts and probe results. Missing freshness
  evidence makes the run `INVALID`.

## 4. Counting and branch decision

Counts are deltas from the pre-provider cutover manifest, not totals from the
historical workstream:

```text
new Claude occurrences = post-cutover intake.claude_brainstorm occurrences
new Codex occurrences = post-cutover intake.codex_product_review occurrences
new proposal references = post-cutover verified .ref.json files
```

Branch selection is made only from typed validation results:

```text
TASK_CONTRACT_REVISION_DELTA_INVALID -> evaluate PASS A
validated proposal reference published -> evaluate PASS B
anything else -> FAIL or INVALID according to the rules above
```

Natural-language output, prompt text, filenames, mtimes and model claims do not
select the branch.

## 5. Maturity rule

- Current checkpoint maturity remains `ENFORCED`.
- PASS A promotes only the early invalid-delta rejection boundary to `PROVEN`.
- PASS B promotes only the valid-revision-to-review boundary to `PROVEN`.
- One branch does not prove the other.
- Neither branch proves semantic quality, model obedience across providers,
  full workflow completion, compile success, token savings or all future schema
  fields.

## 6. Independent review

Independent read-only review: `PASS`.

- no P0/P1 findings;
- frozen PHZX typed trigger facts, stale PID and retry gate were confirmed;
- detached clone, CLI single-step execution, PASS A/B and stop-loss were judged
  executable;
- full frozen hashes must still be recomputed by preflight before any provider
  call;
- session reuse is an observed model-context variable, not a mechanical-boundary
  failure and not a reason to create a fresh session.
