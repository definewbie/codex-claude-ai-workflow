# Workstream Domain Model and Completion Contract

> Status: design guardrail for implementation
> Last updated: after final-gate boundary review

This document defines the domain model behind a `dual-orchestrator` workstream.
It exists because the workflow graph alone is not the product lifecycle. The
graph says which node runs next; the domain model says whether a workstream has
actually been delivered.

## 1. Background

A real run exposed two important issues:

- The final next-stage gate could accidentally route back to intake and open a
  new iteration after the workstream had already reached final acceptance.
- The operator trace treated `reports/workstream-summary.md` as a required
  completion artifact, but the lifecycle did not guarantee that file existed.

Both issues have the same root cause: completion was inferred from workflow
routing instead of being enforced as a domain contract.

The fix is not to add more scattered `if` checks. The project needs a clear
domain boundary:

```text
Workflow graph = execution plan
Workstream domain = lifecycle truth
```

## 2. Core Principle

A workstream is the aggregate root for one long-running engineering lifecycle.

The workflow runtime may execute nodes, open gates, retry failures, and route
edges, but only the workstream domain contract can answer:

- Is this lifecycle complete?
- Is it deliverable?
- Which evidence supports that state?
- Which artifact is missing, optional, or intentionally skipped?
- Which role or system component owns each state transition?

`WorkflowStatus.COMPLETED` must mean more than "no outgoing edge matched".
It must mean the workstream completed according to its completion contract.

## 3. Domain Objects

| Domain object | Meaning | Not responsible for |
|---|---|---|
| `Workstream` | The aggregate root for one AI-assisted engineering lifecycle. | Low-level process execution. |
| `Phase` | A domain stage such as intake, contract, design, plan, implement, self-test, acceptance, delivery, handoff. | Runtime edge routing. |
| `Iteration` | A revision or repair thread with a trigger and reason. | A generic retry attempt. |
| `GateDecision` | A human decision with allowed actions, note, evidence, and actor. | Running the next task. |
| `Evidence` | A durable fact used for review or acceptance. | Human-readable narrative only. |
| `Artifact` | A file or sidecar with an owner, purpose, requiredness, and validation rule. | Temporary stdout/stderr logs unless explicitly promoted. |
| `Delivery` | The handoff or PR-ready result of the workstream. | Tool-specific PR creation details. |
| `Recovery` | A classified interruption with safe next actions. | Semantic repair decisions without evidence. |

## 4. Execution State vs Domain State

The system must separate runtime state from domain state.

| Layer | Examples | Source of truth | Purpose |
|---|---|---|---|
| Execution state | node `READY`, `RUNNING`, `FAILED`; open gate ids; PID guard | `workflow/current.json`, `nodes/*.json`, `gates/*.json` | Drive runtime safely. |
| Domain state | phase complete, accepted with notes, deliverable, completed with missing optional artifact | workstream completion contract and artifact registry | Explain lifecycle truth. |
| Projection state | timeline, metrics, operator overview, trace output | generated read models | Make state easy to inspect. |

Rules:

- UI and CLI may display execution state, but must not invent domain meaning.
- A node completing successfully does not automatically mean a phase is complete.
- A workflow reaching no outgoing edge does not automatically mean the
  workstream is deliverable.
- Projection files are read models. They can be regenerated and must not be the
  only owner of lifecycle truth.

## 5. Completion Contract

The completion contract defines what must be true before a workstream can be
considered complete.

Minimum completion fields:

```json
{
  "workstream_id": "ws-...",
  "domain_status": "completed",
  "completion_decision": "complete",
  "final_acceptance": "ACCEPT|ACCEPT_WITH_NOTES",
  "verification_confidence": "HIGH|MEDIUM|LOW|UNKNOWN",
  "required_artifacts": [],
  "optional_artifacts": [],
  "missing_artifacts": [],
  "blocking_gaps": [],
  "completed_at": "..."
}
```

Initial completion statuses:

| Status | Meaning |
|---|---|
| `in_progress` | Lifecycle is still executing or waiting. |
| `waiting_for_human` | A domain decision is required. |
| `waiting_for_recovery` | Execution or semantic recovery is required. |
| `completed` | Required artifacts exist and no blocking gap remains. |
| `completed_with_notes` | Completion is allowed but non-blocking notes remain. |
| `incomplete` | Workflow stopped at an apparent terminal point, but required domain conditions are missing. |
| `failed` | Lifecycle is aborted or unrecoverable. |

`WorkflowStatus.COMPLETED` should only be written after the completion contract
is evaluated. If that is not implemented yet, any completed workflow must still
be followed by a deterministic completion audit that can mark missing required
artifacts as a visible gap.

## 6. Required Artifacts

The current full lifecycle should treat these artifacts as required unless a
workflow variant explicitly marks them optional with a reason.

| Artifact | Path | Owner | Required | Validation |
|---|---|---|---|---|
| Final report | `reports/latest-final-report.md` | `report.generate_final` | Yes | File exists and references acceptance / verification evidence. |
| Workstream summary | `reports/workstream-summary.md` | deterministic orchestrator summary writer | Yes | File exists and matches current ledger status. |
| Handoff JSON | `artifacts/next-stage/handoff.json` | `stage.claude_handoff` + contract parser | Yes | Valid JSON sidecar with known fields. |
| Next-stage prompt | `artifacts/next-stage/next-stage-prompt.md` | `prompt.generate_next_stage` | Yes | File exists and is non-empty. |
| Delivery PR body | `delivery/pr-body.md` | `delivery.prepare_pr` | Yes for delivery-enabled lifecycle | File exists or delivery is explicitly skipped. |
| Git evidence | `artifacts/git-evidence/*` | `artifact.validate` | Yes after implementation | Status/diff evidence exists or Git unavailable is explicit. |
| Current failure summary | `recovery/current-failure.md` | failure/recovery writer | Only when recovering | File exists when status is recovery/failure. |

Important ownership rule:

```text
Trace, UI, and status commands must not be artifact owners.
They can report missing artifacts, but they should not be the only way to create
required completion artifacts.
```

## 7. Artifact Ownership

Each artifact needs one owner and one validation policy.

Allowed owners:

- Agent task handler: produces raw Markdown and typed artifacts from Claude or
  Codex.
- Deterministic handler: produces facts that should not depend on model wording,
  such as Git evidence, reports from ledger data, metrics, and summaries.
- Human gate handler: records human decisions and review packets.
- Delivery handler: prepares commit / PR artifacts.

Anti-patterns:

- A CLI command creates a required artifact only when an operator happens to run
  it.
- A UI hides a missing artifact by displaying a fallback string.
- A report parser guesses lifecycle status from natural-language Markdown.
- Multiple handlers can overwrite the same canonical artifact without ownership
  rules.

## 8. Projection, UI, and CLI Boundaries

Read models should be derived from domain and execution state.

| Surface | Reads | Must not do |
|---|---|---|
| `dualctl trace` | current workflow, timeline, completion contract, artifact registry | Decide completion by itself. |
| `dualctl summary` | ledger, metrics, completion audit | Be the only creator of required summary. |
| Operator Panel | API overview and review/recovery packets | Interpret raw ledger differently from CLI. |
| API overview | shared projection / completion audit | Invent hidden state not visible to CLI. |
| Timeline | node and event history | Claim delivery completeness. |
| Metrics | durations and counts | Decide business success. |

CLI and UI should answer the same three questions from the same read model:

```text
Where is it now?
Why did it stop or complete?
What is the safe next action?
```

## 9. Guardrails Against Design Drift

Implementation must be constrained by executable guardrails, not just by this
document.

### 9.1 Completion Invariant Tests

Add tests that start from a realistic completed ledger and assert:

- `WorkflowStatus.COMPLETED` has no active nodes and no open gates.
- final next-stage gate cannot use `approve`.
- required artifacts exist or have explicit skip reasons.
- `trace`, API overview, and operator panel status agree on the same status.

### 9.2 Artifact Registry

Required artifacts should be defined in one registry, for example:

```text
ArtifactDefinition(id, path, owner, requiredWhen, validator, missingSeverity)
```

`trace`, `summary`, API overview, and tests must read this registry instead of
duplicating required artifact lists.

### 9.3 Completion Auditor

Introduce a deterministic completion auditor:

```text
CompletionAuditor.evaluate(paths, definition, current)
  -> CompletionAudit
```

The auditor should not call an LLM. It only checks ledger facts, artifact
presence, and explicit skip reasons.

### 9.4 Runtime Boundary

When routing would complete the workflow, the runtime should either:

1. run the completion auditor before writing `WorkflowStatus.COMPLETED`, or
2. write `COMPLETED` and immediately persist a completion audit that can surface
   `incomplete` gaps.

Option 1 is stricter. Option 2 is lower risk for the current implementation.
The project should start with option 2, then move to option 1 when the
completion contract stabilizes.

### 9.5 Documentation Sync Check

Any change to lifecycle gates, required artifacts, or completion semantics must
update:

- this document;
- `workflow-state-machine.md`;
- `architecture-and-execution-flow.md`;
- workflow JSON / factory definitions;
- tests that assert completion invariants.

If a change touches only UI/CLI but changes lifecycle meaning, that is a design
drift warning.

## 10. Implementation Plan

The next implementation should be deliberately small.

### Step 1: Centralize Required Artifacts

Create a small artifact definition registry for full lifecycle artifacts. Use it
from trace and API overview first.

Do not build a generic rules engine.

### Step 2: Deterministic Workstream Summary Writer

Move `reports/workstream-summary.md` generation behind a deterministic service
that can be called by both:

- `dualctl summary --write`;
- lifecycle completion / completion audit.

The summary remains generated from ledger facts, not by an Agent.

### Step 3: Completion Audit Projection

Persist:

```text
.agent/workstreams/<workstream_id>/reports/completion-audit.json
```

This should record required artifact status, domain completion status, and safe
next action.

### Step 4: Use Audit in CLI and UI

`dualctl trace`, API overview, and the operator panel should display completion
status from the same audit.

### Step 5: Regression Test Realistic Completion

Add a test fixture that simulates a full completed workstream and checks:

- final gate complete;
- no next iteration opened;
- summary exists or audit marks it missing;
- UI/API/trace read the same completion status.

## 11. General Design Review Checklist

Use this checklist beyond `dual-orchestrator` when a system manages a
long-lived business object.

Trigger this review when:

- a user goal includes a lifecycle from creation to completion;
- the system has a long-lived core object;
- multiple roles act on the same object;
- state names have business meaning, not just UI loading meaning;
- decisions need evidence or auditability;
- several surfaces interpret the same status;
- words like completed, closed, delivered, accepted, rejected, or recovered
  appear in product requirements;
- local patches keep accumulating around status or artifact gaps.

Review questions:

- What is the aggregate root?
- What states are execution states, and what states are domain states?
- What does completed mean?
- Who can change that state?
- What evidence supports the state?
- Which artifacts are required?
- Which artifacts are projections?
- Which component owns each artifact?
- Which invariants are covered by tests?

## 12. Current Known Gap

`reports/workstream-summary.md` is currently treated by trace and documentation
as a key artifact, but the lifecycle does not yet guarantee it. The next mainline
task should close this gap by adding deterministic ownership and a completion
audit, not by hiding the warning.
