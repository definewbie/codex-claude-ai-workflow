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

## Core Artifacts

| Artifact | Producer | Purpose |
|---|---|---|
| `task_contract_proposal` | Claude intake | Proposes scope, non-goals, constraints, acceptance criteria, and test requirements. |
| `task-contract.json` | Orchestrator | Canonical contract after mechanical validation and human resolution. |
| `contract-resolution.json` | Human gate / orchestrator | Records how open questions or scope conflicts were resolved. |
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

The validator does not judge product quality. That remains Codex and human
work. The orchestrator only decides whether the artifact is safe enough to route
the workflow.

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
