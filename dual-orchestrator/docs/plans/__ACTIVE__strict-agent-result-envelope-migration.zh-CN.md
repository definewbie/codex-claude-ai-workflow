# __ACTIVE__ Strict AgentResultEnvelope Migration

## Objective

Target phase: P0 agent output boundary.

Replace workflow control based on `dual-control` Markdown and `<dual-artifact>`
text envelopes with one strict JSON `AgentResultEnvelope`. ACP `stopReason`
remains execution evidence and never becomes a workflow decision.

## Source Of Truth

```text
ACP session/prompt request + matching response
  -> current-turn final assistant message
  -> strict AgentResultEnvelope
  -> node output contract
  -> workflow routing
```

The envelope owns lineage and the one workflow result. Markdown is optional
human-readable content inside typed fields only; it is never parsed for state.

## Schema Shape

```json
{
  "schema_version": 1,
  "workstream_id": "ws-...",
  "run_id": "run-...",
  "node_id": "plan.claude_plan",
  "result": {
    "artifact_type": "plan_result",
    "decision": "READY_FOR_PLAN_REVIEW",
    "summary": "short factual summary",
    "blocking": false,
    "checks": [],
    "evidence": [],
    "missing_evidence": [],
    "must_fix": [],
    "acceptance_notes": [],
    "open_risks": [],
    "changed_files": [],
    "verification_summary": "...",
    "failure": {},
    "plan": {
      "implementation_steps": [],
      "edit_files": [],
      "reference_files": [],
      "file_level_changes": [],
      "validation_plan": [],
      "rollback_notes": "...",
      "known_risks": [],
      "human_review_checklist": []
    }
  }
}
```

`result` is a finite polymorphic DTO selected by `artifact_type`:

- `task_contract_proposal`
- `agent_output_contract`
- `plan_result`
- `self_test_report`
- `acceptance_review`
- `stage_handoff`

The `plan` field is allowed and required only for `plan.claude_plan`. No result
variant duplicates `decision` or `summary` in a second control object.

## Ownership

- ACP wire decoder: parse each NDJSON line once.
- ACP current-turn projector: correlate prompt request/response and coalesce
  assistant delta/snapshot events.
- Adapter handler: persist the projected final assistant message unchanged as
  the candidate JSON output.
- Runtime envelope parser: strict JSON syntax, duplicate keys, trailing tokens,
  unknown fields, and typed payload construction.
- Node output contract: lineage, allowed decision, required payload type, and
  node-specific semantic validation.
- Ledger/router: consume only the validated decision.

## Implementation Checkpoints

### A. Typed ACP result fact

- Project current-turn assistant message and matching `stopReason`.
- Carry the fact in `AcpxRunResult`.
- Do not parse workflow JSON in the adapter.

Acceptance:

- tool events cannot enter the final message;
- delta + snapshot is coalesced once;
- persistent sessions return only the current prompt occurrence;
- process completion and workflow result remain separate.

### B. Strict envelope contract

- Add strict DTOs and one parser owner.
- Reject duplicate keys, trailing text, unknown/missing fields, wrong types,
  stale lineage, unsupported artifact type, and invalid decisions.
- Generate existing sidecars from the validated DTO without Markdown parsing.

### C. Cutover

- Update every ACP agent prompt to emit exactly one JSON document.
- Persist `.json` candidate output.
- Route only from the validated DTO.
- Delete `dual-control`, `<dual-artifact>`, and Markdown evidence extraction
  control paths.

### D. Proof

- Captured ACP replay.
- Fake-process transport test.
- Adapter/runtime/core full suites.
- Independent architecture review using:
  Declared by / Proven by / Consumed by / Enforced before / Failure route /
  Cannot-bypass test / Residual raw path.
- Controlled real-case only after deterministic proof passes.

## Cannot-Bypass Matrix

- prose before or after JSON -> reject;
- Markdown fenced JSON -> reject;
- duplicate key -> reject;
- unknown or missing field -> reject;
- wrong primitive type -> reject;
- stale `workstream_id`, `run_id`, or `node_id` -> reject;
- decision outside the node contract -> reject;
- tool-result JSON without a matching prompt completion -> reject;
- prior persistent-session turn -> reject;
- malformed/quoted/legacy dual-control output -> reject;
- valid envelope -> sidecar + routing decision come from the same DTO.

## Non-Goals

- permission or capability enforcement;
- timeout/budget changes;
- handler restructuring;
- run.json redesign;
- generic contract framework;
- provider or real-case tuning.

## Completion

This migration is complete only when the old Markdown control parsers are
physically absent from production control paths and deterministic tests prove
that invalid JSON cannot cause routing or process termination.

## Deterministic Proof

- `dual-runtime` full suite: 409 tests, 0 failures.
- `dual-adapter-acpx` full suite: 283 tests, 0 failures.
- Fake-process transport proof
  `returnsCurrentPromptFinalAssistantJsonFromTypedSessionStream`: a tool payload
  contains a competing JSON object, but only the current prompt's final
  `agent_message_chunk` is returned after the matching `session/prompt`
  response.
- Production search: no `dual-control`, `dual-artifact`, or terminal-control
  parser/control path remains in core, runtime, or ACP adapter sources.
- Net diff removes substantially more code than it adds; the migration removes the
  prior Markdown grammar and extraction stack instead of retaining a fallback.
- Adapter run metadata does not declare an assistant candidate valid before
  runtime DTO validation; the first terminal contract outcome is written by
  `ArtifactContractValidator` plus `AgentRunMetadataFinalizer`.
- JSON output artifact metadata uses `application/json`.
- `contract.compile` consumes only its routed, validated intake sidecar; it
  neither scans historical runs nor reparses raw assistant JSON. The sidecar
  carries workstream/run/node-instance/source lineage and the typed proposal.
- Review and next-stage control consumers reject sidecars whose workstream,
  run occurrence, node instance, or source path lineage is invalid.
- `AgentOutputLineageVerifier` resolves that lineage against the authoritative
  completed `NodeInstanceDocument` and succeeded `NodeExecutionDocument`;
  self-consistent but forged sidecars without a ledger occurrence are rejected.
- `AgentEvidenceIndex` has no filesystem-wide run scan and accepts only routed
  refs whose sidecars pass the same ledger occurrence verifier.
- Context input/recent-output sections render only routed `AgentEvidenceIndex`
  entries. An un-routed sidecar with a newer mtime cannot enter an agent prompt.
- Human review artifact summaries use the same typed document and occurrence
  verifier; a forged adjacent sidecar prevents packet publication.
- Summary-degraded input artifacts use the same routed typed evidence path;
  raw agent bodies are never used as the summary, and a sidecar without a
  matching ledger occurrence fails before prompt publication.
- `EvidenceManifest` rebuilds agent evidence only from the current node's
  routed input refs. It no longer scans historical run sidecars, so an
  un-routed `changed_files` claim cannot expand governance read candidates;
  routed ledger-bound claims remain available.
- ACP prompt completion requires a prompt request and the exact matching
  response observed after process start. Historical bootstrap bytes and an
  unmatched stale `end_turn` response cannot complete the current run.
- Fake-process production proof
  `processStartBaselineRejectsStalePromptResponseUntilCurrentRequestCompletes`
  starts with a completed historical `id=20`, observes current request `id=21`,
  then observes a stale post-start response for `id=20`; completion remains
  false until the exact `id=21` assistant message and response arrive.
- Companion fake-process proof
  `processStartBaselineAndStaleResponseCannotCompleteCurrentPrompt` exits after
  current request `id=21` plus stale response `id=20` only, and observes an
  incomplete prompt result.

## Architecture Review

Status: `PASS — ENFORCED + PROVEN`. The same independent reviewer rechecked
the production call paths after the final fixes and confirmed that both the
summary-projection bypass and historical-sidecar scope-expansion bypass are
closed. Deterministic suites and cannot-bypass tests are green.

- Declared by: `AgentResultEnvelope`, `AgentResultPromptContract`, and the
  node-specific `ContractRegistry` entry.
- Proven by: strict parser/validator tests, workflow integration tests,
  current-prompt projector tests, and the fake-process transport proof.
- Consumed by: `ArtifactContractValidator`, which projects the validated DTO
  into existing task-contract, output-contract, plan, review, and handoff read
  models.
- Enforced before: `markAgentTaskFinished` and `finishNodeExecution` in both
  workflow execution paths.
- Failure route: parse, lineage, payload, artifact-type, or decision violations
  become a failed `NodeResult`; they cannot publish a routing decision.
- Cannot-bypass test: prose/fences, duplicate or unknown keys, wrong primitive
  types, stale or blank lineage, wrong artifact/decision, tool payload JSON,
  and prior-session content are rejected or ignored before routing.
- Residual raw path: none in production for the removed Markdown control
  protocols. Non-agent context artifacts may still be rendered as text, but
  agent results reach prompts and governance only through routed, ledger-bound
  typed evidence. Re-reading a candidate, where required, uses the same strict
  parser and validator rather than a second grammar.

Real-case status: not run. The deterministic gate now permits a separately
defined controlled real-case; provider behavior is not part of this closure.
