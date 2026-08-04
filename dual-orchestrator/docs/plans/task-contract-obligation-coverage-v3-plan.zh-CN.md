# Task Contract Obligation Coverage v3 Plan

**Status:** ENFORCED - deterministic implementation and cannot-bypass gates PASS. Not yet PROVEN by real-case.

The final read-only review confirmed that the current iteration's typed trigger
lineage can identify the exact rejected intake review without output-ref echo,
history scans, timestamps, or prompt reconstruction. The single v3 checkpoint is
implemented; a real-case has not been run for this checkpoint.

## Implementation Result

- `task_contract_proposal.schema_version` is 3; v2 success fixtures and smoke expectations were removed, while explicit v2 rejection fixtures remain.
- Proposal clauses and intake review obligations carry stable typed IDs.
- `contract.compile` derives revision mode and the exact rejected review only from current iteration trigger lineage.
- Revision coverage fails closed for missing, multiple, omitted, duplicate, unknown, or unrelated obligation mappings.
- Proposal `evidence_refs` are preserved in canonical `TaskContract` and rendered from that contract into downstream context.
- No compatibility fallback, history scan, timestamp heuristic, prompt-text inference, registry, engine, or new workflow phase was added.

Deterministic proof includes focused compiler/validator/context tests and the full nine-module Maven reactor. This establishes `ENFORCED`; `PROVEN` still requires a separately gated real-case that actually takes the `NEEDS_REVISION -> revision -> READY -> compile` route.

## 1. Objective

Make an intake-contract revision mechanically prove that every obligation in the verified `NEEDS_REVISION` review which caused that revision has an explicit contract-clause landing point. Preserve proposal `evidence_refs` in the canonical `TaskContract` and render those canonical refs into downstream agent context.

This checkpoint does not prove that a clause semantically satisfies an obligation. Codex remains the independent semantic reviewer.

## 2. Confirmed Current Gap

`TaskContractProposal` contains optional string `evidence_refs`, but `ContractCompileHandler.toTaskContract` drops them because `TaskContract` has no corresponding field. Current Codex `mustFix` values and revision proposals are free-text lists: there is no stable obligation identifier and no checked obligation-to-contract mapping. The revision prompt includes the Must Fix text, but prompt visibility is not deterministic coverage.

The current compile input normally contains the current READY review. The preceding NEEDS_REVISION review which triggered the revision is not necessarily in that input. A compile-time coverage gate must therefore receive an explicit, typed reference to that exact rejected review; it must not scan history or use the most recent file by timestamp.

## 3. Non-Goals

- No evidence registry, graph, engine, module, workflow phase, or natural language rule engine.
- No automatic upgrade, default clause ID, string matching, or fallback for v1/v2 task-contract proposals.
- No hash/lineage/path-existence evidence verification in this checkpoint. This work preserves and projects evidence refs; it does not claim that a ref proves a business assertion.
- No migration of generic `mustFix` fields emitted by non-intake-review nodes.
- No change to compiled `TaskContract` narrative clause consumers from `List<String>` to typed clauses.

## 4. Authoritative Facts and Owners

| Fact | Owner | Consumer |
|---|---|---|
| Proposal clause IDs and text | intake Claude proposal | proposal validator and compiler |
| Review obligation IDs and text | intake Codex product review | revision route and compiler |
| Exact rejected review for a revision | current iteration's typed trigger lineage | compiler |
| Obligation coverage / target existence | compiler and proposal validator | compile decision |
| Canonical evidence refs | compiler projection to `TaskContract` | canonical contract context renderer |
| Semantic adequacy of the clause | Codex review | intake decision / revision route |

`revision_obligations_ref` is not copied through agent `outputRefs`, because agent handlers do not echo arbitrary input refs. It is not selected by directory scan, workstream memory, prompt text, or a "latest rejection" heuristic. The compiler derives it from its own iteration ID: load that iteration document, require `triggerAction == NEEDS_REVISION`, resolve `triggerNodeInstanceId` to the decision node, and resolve exactly one verified rejected intake-review sidecar from that decision node's input refs.

## 5. Data Model

### 5.1 Proposal v3

Upgrade only `task_contract_proposal.schema_version` from 2 to 3.

```java
record ContractClause(String id, String text) {}

record RevisionResponse(
        String reviewObligationId,
        List<String> resolvedByClauseIds
) {}
```

Proposal narrative lists become `List<ContractClause>`:

- `acceptance_criteria`
- `test_requirements`
- `constraints`
- `non_goals`
- `risk_areas`

`scope` remains the existing typed `path + operation` model. A scope-related review obligation must still have an explicit narrative clause landing point; Codex decides whether the actual scope change is semantically adequate.

The proposal retains `List<String> evidence_refs` for this checkpoint.

### 5.2 Intake review obligation

Only the intake Codex product-review result gains:

```java
record ReviewObligation(String id, String text) {}
```

For `intake.codex_product_review` with `NEEDS_REVISION` and contract gaps, `contract_obligations` is required, nonempty, and has unique nonblank IDs. Existing `mustFix` remains a display field. Other `AgentOutputResult` producers must not be required to emit obligations.

### 5.3 Canonical contract projection

`TaskContract` gains `List<String> evidenceRefs`. The compiler projects typed proposal clauses to their `text` values, so all existing downstream readers continue to consume `List<String>`.

## 6. Explicit Routing Contract

When an intake Codex product review returns `NEEDS_REVISION`, the workflow iteration already records the decision node which triggered the revision. That iteration lineage is the authoritative carrier:

```text
NEEDS_REVISION review sidecar
  -> decision node input ref
  -> WorkflowIterationDocument.triggerNodeInstanceId
  -> WorkflowIterationDocument.triggerAction == NEEDS_REVISION
  -> revision Claude / subsequent READY review / gate / contract.compile
     retain the same iterationId
```

`contract.compile` resolves two distinct typed roles:

1. the current approved-review/proposal reference, used by existing compile validation;
2. the rejected review sidecar reached through its own iteration's typed trigger lineage, used only to check that this revision answered the rejection that caused it.

The compiler must not implement this by passing the ref through every node's `outputRefs`; that path is not durable across agent handlers. It must verify that the iteration, trigger decision, rejected review, revised proposal, and current READY review form one revision lineage. In a revision iteration, zero or multiple matching review refs, an unverified or malformed review, or an unrelated review is a compile failure.

## 7. Compile Rules

For every proposal:

- schema version must be 3; v1/v2 are rejected;
- clause IDs are nonblank and unique across all proposal narrative clauses;
- a revision response has a nonblank obligation ID and one or more clause IDs;
- every response target exists in the proposal.

Compile mode is selected only from the current iteration's typed trigger facts:

- an initial workflow iteration with no `NEEDS_REVISION` trigger permits no obligations ref and empty `revision_responses`;
- an iteration with `triggerAction == NEEDS_REVISION` is a revision iteration and must resolve the trigger decision's verified rejected review;
- any ambiguous or inconsistent trigger state fails closed. Compile must not infer mode from whether `revision_responses` is empty.

For a revision iteration:

- `revision_responses` is required;
- each obligation ID appears in exactly one response;
- every obligation in the verified sidecar appears in a response;
- a response may point to one or more clauses;
- no response may refer to an obligation absent from that sidecar.

Failure is a typed proposal/compile rejection before `TaskContract` becomes ready for downstream execution. The compiler persists `evidence_refs` to the canonical `TaskContract` without claiming semantic evidence validation.

## 8. Handoff Projection

The canonical TaskContract context section renders `TaskContract.evidenceRefs` for downstream agent nodes. It must read only the canonical contract, not the raw proposal or prior prompt. This is a preservation/projection guarantee only; it does not authorize file access or establish business truth.

## 9. Implementation Order

1. Add proposal v3 records and strict validators; update parser, projector, prompt contract, contract-document reader/writer, and fixtures.
2. Add typed intake-review obligations with validator rules scoped to `intake.codex_product_review` and `NEEDS_REVISION`.
3. Resolve `revision_obligations_ref` in the compiler from its current `WorkflowIterationDocument`: `iterationId -> triggerNodeInstanceId -> decision inputRefs -> exactly one verified rejected intake review`. Prohibit output-ref echo, history scans, timestamps, and prompt-text recovery.
4. Extend compiler validation for coverage and add `TaskContract.evidenceRefs`. Project clause text to the unchanged downstream contract lists.
5. Render canonical evidence refs in the contract context section.
6. Run focused tests, sibling-path sweep, full Maven reactor, and diff check.
7. Only after deterministic PASS, define a real-case gate. A real case may claim PROVEN only when it exercises NEEDS_REVISION -> revision -> READY -> compile and demonstrates the carried ref, coverage, and canonical handoff.

## 10. Cannot-Bypass Matrix

| Case | Expected result |
|---|---|
| v1/v2 proposal | strict rejection |
| duplicate/blank clause ID | rejection |
| initial iteration, no `NEEDS_REVISION` trigger, empty responses | allowed |
| revision iteration, trigger review missing or unresolvable | rejection |
| revision iteration, zero or multiple matching rejected review refs | rejection |
| verified obligations ref, omitted responses | rejection |
| one obligation omitted | rejection |
| duplicate response for one obligation | rejection |
| unknown obligation or clause ID | rejection |
| prompt contains Must Fix but no verified ref | no inferred obligation source |
| non-revision mode inferred only because responses are empty | prohibited; use iteration trigger facts |
| unrelated verified ref | rejection by lineage/role validation |
| all obligations covered by real clauses | compile succeeds |
| proposal evidence refs | equal canonical TaskContract evidence refs |
| canonical context | renders canonical evidence refs, not raw proposal refs |
| existing downstream readers | continue receiving List<String> clauses |

## 11. Maturity and Residual Risk

Deterministic PASS establishes `ENFORCED`: a routed, verified review obligation cannot be silently omitted from the next compiled contract. It does not prove a model will emit valid IDs, nor that a mapped clause semantically resolves the obligation. Invalid model IDs fail closed; semantic adequacy remains subject to the independent Codex review and a new real-case gate.
