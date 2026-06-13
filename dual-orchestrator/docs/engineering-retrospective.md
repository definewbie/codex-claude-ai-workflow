# Engineering Retrospective

This document records recurring implementation failures and the engineering
rules they produced. It is intentionally practical: every entry should help
future work avoid the same class of mistake.

## 2026-06-11: Operator Panel Gate Evidence Drift

### Symptom

Operator Panel fixes around lifecycle progress and human gate evidence worked
locally, but repeated issues appeared:

- pipeline stages were visible but not drillable;
- historical human gate packets were hidden;
- historical gate evidence used a narrow approval layout;
- multiple gate evidence views looked identical because shared task contract
  content appeared before gate-specific decision content.

### Root Cause

The implementation treated the workflow graph and available JSON payloads as
the product model. It built UI by dumping reachable data instead of first
answering:

- Is this current state or historical evidence?
- Which object owns the action?
- Which evidence differentiates this gate from another gate?
- Which content is shared context and should therefore be secondary?

The underlying ledger data was mostly correct. The projection layer lost the
domain semantics.

### Why Local Fixes Were Not Enough

Each local fix solved the visible symptom:

- add click handler;
- add packet endpoint;
- widen layout;
- add another field block.

But those fixes were not governed by a projection contract. Without a checklist,
the next UI change could again put shared context before object-specific
evidence, or mix current actions with historical read-only views.

### Rule Added

Before changing workflow, gate, recovery, artifact, or observability code:

1. Name the boundary: domain state, execution state, artifact contract, human
   gate, recovery, delivery, projection, or adapter.
2. Define the user question the view or command must answer.
3. Separate current actionable state from historical read-only evidence.
4. Put object-specific evidence before shared context.
5. Validate at least two distinct objects of the same type.

This rule is now captured in:

- `dual-orchestrator/docs/engineering-checklist.md`
- local Codex skill: `boundary-first-engineering`

### Future Design Implication

Operator Panel should not be a loose collection of API payload renderers. It
needs a small projection contract for each first-screen block:

- lifecycle progress: stage status and current location;
- node inspector: node-local execution facts;
- human gate evidence: gate decision first, task contract second;
- recovery panel: failure classification and next recovery action;
- completion panel: completion audit and final artifacts.

When adding new panels, start from the projection contract, not from the
available JSON shape.
