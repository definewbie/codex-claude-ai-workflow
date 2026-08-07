# Task Contract Revision Delta Integrity Plan

**Status:** ENFORCED (deterministic) - implementation, 1192-test reactor, and
independent code-level diff review PASS; real-case PROVEN pending.

## 1. Objective

Close one proven contract-revision boundary defect:

> A revised `task_contract_proposal` may silently change an existing clause that
> is not named by the current `revision_responses`, causing an earlier accepted
> contract decision to regress and forcing a later Codex review to rediscover it.

The checkpoint must reject that silent change before the next Codex review is
started. It must not add a workflow node, phase, module, registry, generic diff
engine, natural-language matcher, or model-specific rule.

This is a P0 main-lifecycle correctness fix. The target is not to make GLM,
DeepSeek, or Claude more obedient; the target is to prevent an external model
from silently changing contract clauses outside its declared revision response.

## 2. Captured Evidence

Workstream:

```text
ws-20260807T050827Z-PHZX
```

The first revision declared the clauses used to answer `CO-1..CO-4`. A later
revision declared only the current obligations but also changed previously
stabilized normalization clauses, including `C-2`. Codex subsequently raised
the same semantic family again as `CO-7/CO-8`.

Confirmed current behavior:

- `TaskContractProposalValidator.validateRevisionCoverage(...)` proves that all
  current obligation IDs have responses and that referenced clause IDs exist.
- It does not compare the revised proposal with the proposal reviewed in the
  parent iteration.
- `ArtifactContractValidator` validates and publishes the revised proposal
  before the next Codex review, but currently validates only the candidate in
  isolation.
- `ContractCompileHandler` can resolve the exact rejected review through typed
  iteration lineage, but compile occurs after Codex review and therefore cannot
  prevent the wasted review call by itself.

## 3. Boundary And Ownership

| Fact | Owner | Consumer |
|---|---|---|
| Previous accepted/rejected-review subject proposal | verified `TaskContractProposalReference` reached through current iteration trigger lineage | revision delta validation |
| Current review obligations | verified rejected intake review sidecar | revision coverage validation |
| Candidate revised proposal | current Claude `AgentResultEnvelope` after strict parse/schema validation | proposal validator |
| Actual clause delta | `TaskContractProposalValidator.validateRevisionDelta(previous, current)` deterministic comparison | `ArtifactContractValidator`, then compile defense-in-depth |
| Semantic adequacy of declared changes | Codex product review | intake decision |

No new domain fact is required. `revision_responses` already declares the
clauses associated with each review obligation. The missing behavior is
deterministic validation of that declaration against the actual proposal delta.

The lineage resolver must have one implementation shared by early artifact
validation and contract compilation. Existing lineage rules in
`ContractCompileHandler.resolveRevisionContext(...)` should be extracted or
delegated, not copied into a second owner.

## 4. Revision Baseline Resolution

For `intake.claude_brainstorm`, determine revision mode only from durable typed
lineage:

```text
current node instance
  -> current WorkflowIterationDocument
  -> triggerAction == NEEDS_REVISION
  -> triggerNodeInstanceId == completed intake.decision in parent iteration
  -> exactly one verified rejected intake review sidecar
  -> review_subject TaskContractProposalReference
  -> exact previous TaskContractProposal
  -> current review obligations
```

Initial iteration (`triggerAction == workflow_started`) has no baseline delta
check and still requires `revision_responses=[]`.

Missing, ambiguous, stale, tampered, cross-workstream, or wrong-iteration
lineage fails closed. Prohibited baseline sources:

- prompt text;
- natural-language Must Fix matching;
- latest-file or directory scans;
- timestamps;
- model output claims;
- an unverified proposal JSON path.

## 5. Deterministic Clause Delta

Build a map for each proposal across these clause sections:

- `acceptance_criteria`
- `test_requirements`
- `constraints`
- `non_goals`
- `risk_areas`

Each clause snapshot is:

```text
clause id -> (section, text.trim())
```

Comparison is case-sensitive. JSON byte order and list order are not semantic.
Moving an ID to another section is a modification even when its text is
unchanged.

Compute:

```text
modifiedExistingIds
addedIds
deletedIds
declaredTargetIds = union(revision_responses.resolved_by_clause_ids)
```

Rules:

1. Every modified existing ID must occur in `declaredTargetIds`.
2. Every added ID must occur in `declaredTargetIds`.
3. Deleting an existing clause during a revision is rejected. Stable clause IDs
   must be preserved; replacement is expressed by modifying the existing ID or
   adding a separately declared clause without deleting the old one.
4. A response may reference an unchanged supporting clause. This
   keeps `resolved_by_clause_ids` as an obligation-to-contract mapping rather
   than falsely redefining it as an exact diff list.
5. Existing clause IDs outside `declaredTargetIds` must retain the same section
   and trimmed, case-sensitive text.

Stable error codes should distinguish at least:

```text
UNDECLARED_CONTRACT_CLAUSE_CHANGE
UNDECLARED_CONTRACT_CLAUSE_ADDITION
CONTRACT_CLAUSE_DELETION_NOT_ALLOWED
REVISION_BASELINE_UNRESOLVABLE
```

Do not derive decisions from error-message text.

## 6. Enforcement Points

### 6.1 Before Codex review

`ArtifactContractValidator` must run the shared revision-context resolution and
`TaskContractProposalValidator.validateRevisionDelta(...)` after strict envelope
and single-proposal validation, but before publishing the proposal reference and
before the workflow can route to `context.intake_codex_product_review`.

Failure returns the existing typed artifact-contract failure path. It must not
publish a trusted proposal reference and therefore prevents the next Codex call.

### 6.2 Compile defense-in-depth

`ContractCompileHandler` must consume the same resolved revision context and the
same delta validator. It must not maintain a second comparison implementation.
This guards compilation if a future workflow path bypasses the normal reviewer
route.

The early validator is the cost-saving enforcement point; compile is the final
contract-integrity guard. Both consume one semantic owner.

## 7. Prompt Change

Update `AgentResultPromptContract` only to explain the enforced contract:

```text
During a revision, preserve every existing clause not named by the current
revision_responses. List every added or modified clause ID in at least one
resolved_by_clause_ids entry. Do not delete existing clause IDs.
```

This prompt reduces model rejection frequency. It is not proof and must not be
the only implementation.

## 8. Explicit Scope Boundary

This checkpoint validates the stable-ID clause model already introduced by
proposal v3. It does **not** claim deterministic revision authorization for:

- `objective`
- `scope.included` / `scope.excluded`
- `evidence_refs`
- `open_questions`

Those fields do not currently have revision target identities in
`revision_responses`. Quietly inventing synthetic IDs or a string protocol would
repeat the validator-private-protocol mistake that proposal v2/v3 removed.

Peer review must decide whether this bounded clause closure is sufficient for
the captured failure. If deterministic control of those fields is required now,
the correct answer is an explicit future schema revision with typed targets,
not hidden inference inside this checkpoint. Do not expand to that schema
without separate evidence and authorization.

Codex still reviews all proposal semantics, including those non-clause fields.

## 9. Cannot-Bypass Tests

Required deterministic matrix:

| Case | Expected |
|---|---|
| initial iteration + empty responses | PASS; no baseline lookup |
| revision + exact unchanged clauses outside declared targets | PASS |
| declared existing clause changes text | PASS |
| declared clause moves section | PASS as a declared modification |
| declared new clause | PASS |
| one unchanged supporting target plus one changed target | PASS |
| undeclared existing clause changes | FAIL before proposal publication |
| undeclared new clause | FAIL before proposal publication |
| existing clause deleted | FAIL |
| response targets only unchanged supporting clauses | PASS; semantic adequacy remains Codex-owned |
| list/JSON order changes only | PASS |
| missing/tampered/cross-iteration baseline | FAIL closed |
| prompt mentions previous text but typed baseline is absent | FAIL closed; no inference |
| early validation failure | no proposal reference and no Codex review route |
| compile receives a crafted bypass candidate | same shared delta validator rejects |

Add a deterministic replay using the captured proposal pair that changed `C-2`
without declaring it. The replay must produce
`UNDECLARED_CONTRACT_CLAUSE_CHANGE: C-2` without invoking a provider.

## 10. Sibling Sweep

Review all three bundled workflow profiles (`full`, `contract`, and
`terminal-delivery`) because they share the intake revision route. Confirm:

- all use the same artifact validator and compile handler;
- no profile has a direct revised-proposal-to-review or compile bypass;
- no alternate validator publishes proposal references;
- proposal v3 fixtures, prompt contract tests, compiler tests, and semantic
  smoke fixtures remain aligned;
- no legacy v1/v2 fallback or string-based clause matching is introduced.

## 11. Rejected Alternatives

- whole-JSON byte diff;
- Levenshtein, embeddings, or LLM semantic diff;
- prompt-only preservation rules;
- automatically reverting undeclared changes;
- allowing the candidate model to self-authorize arbitrary changes without
  comparing the actual delta;
- scanning historical proposal directories for a baseline;
- adding a revision workflow node, diff engine, registry, phase, or platform;
- changing provider/model as the correctness mechanism.

## 12. Acceptance And Maturity

Implementation may be marked `ENFORCED` only when:

- typed lineage yields one exact baseline and obligation set;
- one semantic validator computes the clause delta;
- undeclared changes are rejected before trusted proposal publication and the
  next Codex call;
- compile reuses the same validator;
- cannot-bypass replay and full reactor pass;
- independent code-level review confirms no sibling bypass.

It may be marked `PROVEN` only after a separately gated real case reaches a
revision and shows that an undeclared regression is blocked before reviewer
dispatch, or that a valid declared revision reaches Codex review without quality
regression.

This checkpoint does not prove that the model will produce good semantics. It
proves that silent clause drift cannot pass as an undeclared revision.

## 13. Implementation Checkpoint

Implemented as one bounded checkpoint:

- `TaskContractRevisionContextResolver` is the shared owner for exact revision
  lineage resolution used by early artifact validation and compilation.
- `TaskContractProposalValidator.validateRevisionDelta(previous, current)` is
  the single owner for deterministic clause-delta comparison.
- `ArtifactContractValidator` enforces revision coverage and delta integrity
  before publishing a trusted proposal reference, preventing downstream review
  dispatch when the candidate silently drifts.
- `ContractCompileHandler` consumes the same resolver and validator as a
  defense-in-depth boundary; its previous private lineage implementation was
  removed.
- `AgentResultPromptContract` describes the enforced preservation rule without
  becoming an authority source.

Implementation review refined one rule from the earlier draft: a revision
response is not required to point to an actually changed clause. An unchanged
supporting clause may legitimately satisfy an obligation, especially for a
scope-only correction. The deterministic boundary is therefore:

```text
every actual modified or added clause must be declared
every existing clause ID must be preserved
```

It is deliberately not:

```text
every declared target must appear in the actual diff
```

Deterministic evidence:

- captured `C-2` silent-change regression is rejected before proposal reference
  publication;
- a crafted compile bypass is rejected by the same delta validator;
- declared changes, declared additions, section moves, supporting unchanged
  clauses, and order-only changes pass;
- undeclared changes, undeclared additions, and deletions fail closed;
- full Maven reactor: 9/9 modules, 1192 tests, 0 failures, 0 errors;
- `git diff --check`: clean.
- independent code-level boundary review: PASS, with no P0/P1 findings.

Current maturity is `ENFORCED`, not `PROVEN`. No provider or real-case was run
for this checkpoint.
