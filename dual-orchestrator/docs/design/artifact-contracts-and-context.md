# Artifact Contracts And Context

This document describes the current contract and context model used by
dual-orchestrator. It replaces earlier iteration notes that were organized by
implementation date or internal phase number.

## Problem

Long-running Agent workflows fail in two common ways:

- the next Agent receives too little context and starts guessing;
- the orchestrator reads natural-language Markdown as if it were reliable
  machine state.

Markdown is useful for humans, but it is a weak boundary for workflow routing.
The runtime needs stable facts: task scope, acceptance criteria, self-test
evidence, final verifier decision, handoff state, and known gaps.

## Design Principle

Agent output has two layers:

| Layer | Audience | Runtime role |
|---|---|---|
| Markdown report | Human reviewer | Explains reasoning, tradeoffs, and narrative evidence. |
| Typed artifact | Orchestrator and downstream Agents | Provides machine-readable facts for validation, routing, context, and reports. |

The orchestrator does not infer critical state from Markdown headings,
keywords, or summaries. When a node requires a structured artifact, missing or
invalid artifacts fail the node and produce contract diagnostics.

## Output Capture Boundary

acpx stdout is execution evidence, not the workflow artifact. A single acpx
stream may contain client frames, tool calls, tool input/output, provider
messages, skill text, and the final Agent answer. The adapter therefore writes
two files for a completed Agent execution:

| File | Meaning | May feed later context? |
|---|---|---|
| `outputs/<prompt>.transcript.log` | Raw acpx execution transcript for debugging and failure classification. | No. |
| `outputs/<prompt>.md` | Extracted final business artifact / terminal control block. | Yes. |

`NodeResult.outputRefs` for a completed Agent node points to the final
`outputs/<prompt>.md`, not the transcript. Failed nodes may reference the
transcript and stderr as recovery evidence. Context rendering also refuses to
expand `*.transcript.log` if old or manual input references point at one.

This boundary is the main token-control rule: tool logs and file reads remain
auditable, but they do not become the next Agent's prompt.

## Core Artifacts

| Artifact | Producer | Purpose |
|---|---|---|
| `task_contract_proposal` | Claude intake | Proposes scope, non-goals, constraints, acceptance criteria, and test requirements. |
| `task-contract.json` | Orchestrator | Canonical contract after mechanical validation and human resolution. |
| `contract-resolution.json` | Human gate / orchestrator | Records how open questions or scope conflicts were resolved. |
| `agent_output_contract` | Claude / Codex intermediate stages | Records the stage decision, summary, evidence, missing evidence, risks, and failure fields for design, plan, implementation, and review routing. |
| `self_test_report` | Claude self-test | Records checks run, checks skipped, evidence, missing evidence, and residual risks. |
| `acceptance_review` | Codex verifier | Records final decision, blocking gaps, must-fix items, acceptance notes, and confidence. |
| `stage_handoff` | Claude handoff | Records completed work, important decisions, changed files, open risks, and recommended next stage. |

## Contract Lifecycle

1. The user requirement starts as natural language.
2. Claude produces a `task_contract_proposal`.
3. The orchestrator validates shape and executable boundaries.
4. The contract review gate resolves open questions or asks for revision.
5. The orchestrator writes the canonical `task-contract.json` / `.md`.
6. Later stages consume the canonical contract as trusted harness fact.

The canonical contract is not copied from Codex review output. Codex may quote or
criticize a proposal, but the proposal source remains Claude intake plus
orchestrator validation.

## Validation Boundary

Contract validation is intentionally mechanical:

- required fields exist;
- included and excluded scope do not conflict;
- acceptance criteria are concrete enough to verify;
- test requirements are present;
- ledger or context metadata is not mistaken for user task scope;
- status and decision fields are internally consistent.
- `READY` means the artifact is safe to hand to the next workflow role. It must
  not carry explicit blocking decision evidence such as `blocking=true` or
  `must_fix`, but it may carry `missing_evidence` so the downstream verifier can
  judge whether the gap is acceptable;
- final `ACCEPTED` means the verifier found no material evidence gaps, so it
  must not carry `blocking=true`, `must_fix`, or material `missing_evidence`;
- `ACCEPTED_WITH_NOTES` may carry non-blocking missing evidence or residual
  notes, but not `blocking=true` or `must_fix`;
- negative states such as `NEEDS_REVISION`, `BLOCKED`, or `REJECTED` carry at
  least one concrete blocking reason, missing evidence item, must-fix item, or
  failure reason.

The validator does not judge product quality. That remains Codex and human
work. The orchestrator only decides whether the artifact is safe enough to route
the workflow.

Empty evidence must be represented as an empty field or empty array. Placeholder
phrases such as "none", "no blocking issue", or "N/A" inside `must_fix` or
`missing_evidence` are not normalized away; they are treated as actual items.
`must_fix` conflicts with positive routing states, while `missing_evidence` is
carried forward as a verifier-facing evidence limitation unless the artifact is
claiming final `ACCEPTED`.

## Stage Outcome

Structured Agent artifacts may contain an Agent-declared `lifecycle_status`,
but that field is not the runtime source of truth by itself. The validator
passes the artifact through a stage outcome compiler:

```text
Agent decision + declared lifecycle_status
  -> StageOutcomeCompiler
  -> canonical stage_outcome + stage_outcome_source
```

The compiled `stage_outcome` is the canonical semantic state carried by the
contract sidecar. Downstream failure classification, context packages, review
packets, reports, CLI, and UI projections should consume `stage_outcome` when
they need stage-level meaning. They should not re-derive lifecycle meaning from
Markdown prose or from the raw declared `lifecycle_status`.

The workflow definition declares which artifact a structured node expects:

```json
{
  "expected_artifact": "acceptance_review",
  "artifact_compiler": "acceptance_review_compiler",
  "allowed_stage_outcomes": [
    "ACCEPTED",
    "ACCEPTED_WITH_NOTES",
    "REJECTED",
    "INSUFFICIENT_EVIDENCE",
    "BLOCKED"
  ]
}
```

This keeps the artifact boundary explicit: workflow JSON declares the expected
artifact, the validator checks shape and semantic consistency, and the compiler
produces the canonical outcome. Handler-local string rules are not allowed to
become a second stage model.

## Decision Routing Boundary

Decision nodes consume the latest `*.contract.json` sidecar in their input
references. They do not parse Markdown or fall back to terminal
`dual-control` blocks for routing. `dual-control` remains a terminal-output
sanity check inside artifact validation, but it is not the runtime decision
source.

If a decision node sees no sidecar, it fails with `MISSING_CONTRACT_SIDECAR`.
If the current sidecar exists but is malformed or missing a decision, it fails
with `INVALID_CONTRACT_SIDECAR`; the runtime must not search older artifacts
for a usable answer. The newest sidecar is the current typed fact.

## Report Projection Boundary

Final reports and operator projections should summarize typed sidecars such as
`implement-claude-self-test.contract.json` and
`acceptance-codex-review.contract.json`. Raw Agent Markdown remains available
as an artifact pointer, but report generation must not embed tool transcripts,
raw stdout, or unvalidated natural-language snippets as acceptance truth.

## Context Package

Before each Agent step, the runtime builds a context package for the target
role. Context packages are budgeted and profile-specific:

| Profile | Typical consumer |
|---|---|
| product review | Codex intake/product review |
| architecture review | Codex design review |
| plan review | Codex plan review |
| implementation | Claude implementation |
| acceptance | Codex final verifier |
| handoff | Claude next-stage handoff |

Each package separates:

- trusted harness facts, such as TaskContract and Git evidence;
- human decisions, such as gate approvals and revisions;
- proposed Agent states, such as self-test and acceptance sidecars;
- pending gaps, such as contract diagnostics or missing evidence.

This keeps long context useful without letting old Markdown, copied examples, or
quoted Agent text become workflow truth.

Each generated context package has two layers:

```text
.agent/workstreams/<workstream_id>/context/<node_instance_id>.md
.agent/workstreams/<workstream_id>/context/<node_instance_id>.json
```

The Markdown package is the Agent-facing context. The JSON sidecar is the
harness-facing governance record. It records:

- profile and target node;
- estimated token count and token budget;
- whether the rendered package stayed within budget;
- per-section availability, degradation, drop status, soft budget, and rendered
  token count.

Trace, API overview, and Operator Panel projections should read the JSON sidecar
when they need budget evidence. They must not parse the Markdown governance
table as source of truth.

Agent run artifact metadata sidecars also record byte size and a local
estimated-token count for prompt, final output, transcript, and stderr files.
Provider usage remains separate when available; local estimates exist to compare
transcript size against final artifact size and catch context bloat regressions.

## Failure Mode

When a required artifact is invalid, the node fails with
`AGENT_OUTPUT_CONTRACT_VIOLATION`. The runtime writes diagnostics under:

```text
.agent/workstreams/<workstream_id>/artifacts/contract-failures/
```

Those diagnostics include the source output, contract shape, parse error,
recommended owner, and recovery hint. CLI, API, and operator overview consume
the same diagnostic data.

## Current Tradeoff

The system is strict about machine facts but still lets Agents write expressive
Markdown for people. This is the important balance: harnesses should constrain
state transitions and evidence boundaries, not flatten every human explanation
into rigid forms.
