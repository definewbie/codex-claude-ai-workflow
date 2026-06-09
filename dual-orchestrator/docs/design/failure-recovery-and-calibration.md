# Failure Recovery And Calibration

This document describes how dual-orchestrator handles failures after Codex
acceptance, Agent execution errors, and reusable lessons.

## Problem

An Agent lifecycle should not treat every failure as "ask Claude to try again."
Different failures need different routes:

- implementation bug: return to Claude implementation;
- contract gap: return to contract clarification;
- missing context: rebuild context and rerun the verifier;
- review noise: rerun or ask a human to inspect the verifier output;
- tool or quota problem: wait for recovery or retry later.

Blind retry wastes quota and can corrupt the task boundary.

## Runtime Recovery

Runtime failures are external or mechanical:

- `acpx` session missing;
- rate limit or quota;
- Agent reconnect;
- context window overflow;
- invalid structured output;
- command exit failure.

These failures stop the workflow in a recoverable state when possible. The
ledger writes:

```text
recovery/current-failure.md
recovery/current-failure.json
```

`dualctl recovery show`, `dualctl trace`, the API, and the operator panel all
read this same recovery model.

## Semantic Failure

Semantic failure happens after the verifier inspects the implementation and
evidence. Codex emits a structured acceptance review. The orchestrator reads the
validated fields and classifies the failure into stable categories:

| Category | Route |
|---|---|
| `IMPLEMENTATION_BUG` | Return to Claude implementation. |
| `CONTRACT_GAP` | Return to contract compile / clarification. |
| `CONTRACT_AMBIGUITY` | Return to contract compile / clarification. |
| `CONTEXT_INSUFFICIENT` | Rebuild context and return to implementation or verifier. |
| `TOOL_LIMITATION` | Preserve evidence and surface recovery guidance. |
| `REVIEW_NOISE` | Rebuild verifier context or ask a human to inspect. |

`REJECT` creates a repair route. `ACCEPT_WITH_NOTES` records classification data
but can still proceed to reporting when the notes are non-blocking.

## Repair Loop

The full lifecycle includes a bounded repair loop:

```text
Codex acceptance
  -> acceptance decision
  -> failure classification
  -> route to contract / implementation / verifier
  -> self-test
  -> acceptance again
```

The loop is intentionally bounded by revision limits and human gates. The goal
is not infinite autonomous repair. The goal is to let the system take the next
obvious route while preserving enough evidence for a human to intervene.

## Calibration Candidate

Some failures are reusable. When a classified failure looks like a pattern, the
runtime can write a calibration candidate:

```text
calibration/candidates/<candidate_id>.json
calibration/candidates/<candidate_id>.md
```

The first implementation keeps promotion conservative:

1. A human lists and reads candidates.
2. A human marks a candidate as accepted, rejected, or deferred.
3. The system generates a reviewable apply plan.
4. Only accepted contract-template candidates can be promoted.
5. Promotion appends to project-level principles under `.agent/templates/`.

The runtime does not mutate built-in prompts, workflow definitions, context
rules, or tests automatically.

## Why This Is Not A Rule Engine

The current design centralizes rules without turning them into a generic rule
engine. That is deliberate.

The workflow still needs simple, testable Java components for:

- allowed decisions;
- required artifact fields;
- lifecycle status consistency;
- failure categories;
- routing outcomes.

A general rule engine would add configuration power before the product shape is
stable. The better near-term boundary is typed artifacts plus centralized
validators.

## Operator View

Every failure should answer three questions:

1. What failed?
2. Why did the system stop here?
3. What command or human decision comes next?

That answer must be consistent across CLI, API, and UI. The recovery model,
timeline, metrics, and review packet are the read models that make this possible.
