# dual-orchestrator Architecture and Execution Flow

## 1. Not Just a Two-Agent Script

`dual-orchestrator` started from a practical pain: developers were manually
copying work products between Claude Code and Codex. The deeper problem is not
how to call two agents. The real need is an engineering harness that can hold an
AI-assisted software lifecycle together.

The harness must answer:

- Is the requirement clear enough?
- Was the design independently reviewed?
- Does implementation include self-test evidence?
- Is acceptance checked against the same contract?
- If it fails, is it an implementation bug, contract gap, context issue, tool
  failure, or review noise?
- Can the system preserve enough memory to continue after long runs?

So the project is a contract-driven, auditable, recoverable AI engineering
harness. Claude, Codex, and acpx are replaceable adapters around that core.

## 2. Core Ideas

### Role Separation

| Role | Responsibility | Boundary |
|---|---|---|
| Claude | Developer / architect: clarify, design, plan, implement, self-test, handoff | May modify business code |
| Codex | External reviewer / verifier: product, architecture, plan, acceptance | Does not modify business code |
| Human | Gate owner: approve, revise, block, or complete | Final accountability |

The point is not model theater. The point is to separate builder and verifier
while preserving a human decision point at important gates.

### Contract First

The lifecycle centers on a canonical `TaskContract`:

```text
natural-language request
  -> task_contract_proposal
  -> contract review
  -> canonical TaskContract
  -> implementation
  -> self-test evidence
  -> Codex acceptance against the contract
  -> failure arbitration or delivery
```

Claude proposes structured artifacts. The orchestrator performs deterministic
checks such as JSON shape, schema, file references, and metadata. Codex reviews
the proposal by principle. Only then is the canonical contract created.

Markdown remains for humans. Machine routing uses typed artifacts such as
`task_contract_proposal`, `self_test_report`, `acceptance_review`, and
`stage_handoff`.

### Ledger as System Memory

Each workstream owns a file ledger:

```text
.agent/workstreams/<workstream_id>/
  workflow/current.json
  workflow/definition.json
  nodes/*.json
  gates/*.json
  iterations/*.json
  events.jsonl
  timeline.json
  metrics/workstream-metrics.json
  runs/*/
  reports/
```

This ledger makes recovery, audit, replay, metrics, and later calibration
possible.

### Workflow Definition Source

The default full lifecycle is loaded from a classpath JSON resource:

```text
dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json
```

`WorkflowDefinitionLoader` is the single default-definition entry point. When a
workstream starts, the resolved definition is copied into
`workflow/definition.json`; resume, trace, and replay use that ledger copy.
Both bundled variants are loaded from classpath JSON resources through `WorkflowDefinitionLoader`.

### Agent Permission Boundary

Default agent runs are read-only. Only implementation-owned Claude nodes enable
write permissions:

- `implement.claude_implement`
- `implement.claude_self_test_report`

Intake, design, plan, review, acceptance, and handoff nodes must produce
structured evidence, not mutate the target repository. acpx token and cost
usage is recorded as run metadata for observation only; it does not route the
workflow.

### Context by Purpose

The system does not dump all history into every prompt. Context packages are
assembled by profile: product review, architecture review, implementation,
acceptance, handoff, and so on.

Sections have budgets and degradation rules. When context grows too large, the
system prefers trusted facts, decisions, contract fields, evidence summaries,
and pointers over raw transcript stuffing.

### Failures Are Classified

Execution failures and semantic failures are different.

Execution failures include rate limits, missing sessions, reconnect problems,
or context-window issues. These may be retried.

Semantic failures include implementation bugs, contract gaps, ambiguity, review
noise, insufficient context, and tool limitations. These should route to repair,
contract revision, human review, or calibration.

### Scheduler Is Not a Second State Machine

The scheduler periodically reads the authoritative ledger and invokes existing
workflow actions. It does not decide whether the product work is complete, parse
agent prose, or replace human review.

Current policy:

- `RUNNING`: may be resumed until the next gate, recovery boundary, or completion.
- `WAITING_FOR_HUMAN`: must stop for a person to read the review packet and
  approve, revise, or complete.
- `WAITING_FOR_RECOVERY`: stops by default. When `autoRecover` is explicitly
  enabled, the scheduler only retries failures whose `RecoveryAssessment` says
  `recoverable_agent_runtime + RETRY_AFTER_EXTERNAL_FIX`.
- `artifact_contract_failure`, `semantic_revision_limit`, and
  `manual_recovery_required`: never auto-retried, because these are not simply
  “run it again” problems.
- `COMPLETED`, `FAILED`, and `BLOCKED`: terminal or boundary states; no automatic
  next action is allowed.

The boundary is: runtime owns transitions, `RecoveryAssessment` explains
recovery, scheduler advances safe cases, and CLI/UI only project the read model.

## 3. Implemented Modules

| Module | Purpose |
|---|---|
| `dual-core` | Workflow, contract, decision, and domain primitives |
| `dual-ledger-file` | File-backed ledger, locks, timeline, metrics, recovery read models |
| `dual-runtime` | Workflow runtime, handlers, context package builder, contract compiler |
| `dual-adapter-acpx` | acpx integration and prompt rendering |
| `dual-adapter-git` | Git and worktree operations |
| `dual-cli` | `dualctl` command line |
| `dual-server` | Local API and operator panel support |

The current mainline can run from intake to contract, review, implementation,
self-test, acceptance, failure classification or delivery, and handoff.

## 4. Current Status

The main lifecycle is functionally closed. The strongest current boundary is
the artifact contract: the orchestrator no longer treats free-form Markdown as
the machine source of truth. Agents may write rich human-readable reports, but
routing depends on typed structured artifacts and explicit lifecycle state.

P0 status is `Done with known limits`; see
[testing/p0-closure-report.md](testing/p0-closure-report.md). Current validated
capabilities:

- safe worktree execution with the original repository kept clean;
- typed artifacts before routing;
- semantic repair loops after Codex rejection;
- runtime recovery separated from product/domain rejection;
- shared ledger read models for trace, summary, and Operator Panel;
- terminal workflows guarded against accidental resume or approval.

Known limits accepted after P0:

- final acceptance may be `ACCEPT_WITH_NOTES` when live app smoke evidence is
  unavailable;
- the `full` lifecycle still has many human gates;
- context/token governance is observable but still needs continued tightening;
- delivery merge is explicit and conservative, with no auto-commit or auto-merge;
- the public benchmark is reusable, while complex private business cases remain
  maintainer-local evidence.

## 5. Roadmap

Near-term work should stay on the mainline:

- improve artifact and context contracts;
- make long real-case runs easier to observe;
- make human gates easier to review;
- keep public benchmarks repeatable and sanitized;
- avoid premature rule engines or schema migration until the workflow surface
  stabilizes.

The long-term direction is a general AI-native engineering harness with workflow
templates, specialization registries, multi-role review, adversarial testing,
knowledge calibration, replay, and operational metrics.
