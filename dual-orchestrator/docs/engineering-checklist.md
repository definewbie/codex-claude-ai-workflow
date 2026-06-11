# Engineering Checklist

Use this checklist before changing workflow, recovery, artifact contract, human
gate, delivery, or observability code.

## Boundary First

- Identify which boundary owns the problem before editing code:
  - Agent runtime adapter
  - Agent terminal output contract
  - Artifact contract
  - Workflow transition
  - Workstream lifecycle
  - Human gate
  - Ledger projection / UI / CLI
- Do not patch workflow routing, UI, or ledger projections to compensate for an
  adapter or artifact boundary problem.
- If the boundary is unclear, stop and clarify the model before implementing.

## Structured State First

- Prefer typed artifacts, node result fields, edge metadata, or gate metadata.
- Avoid deriving domain state from natural-language summaries.
- Text matching is acceptable only as an adapter compatibility fallback, and it
  must live behind a named semantic model or catalog.

## Centralize Rules

- Do not scatter `contains`, `startsWith`, or node-id naming conventions across
  handlers, projections, CLI, and UI.
- Add or update a small semantic object instead:
  - failure signal catalog
  - terminal output contract
  - gate purpose metadata
  - iteration transition semantics
  - timeline display semantics
- New provider-specific wording belongs in an adapter catalog, not workflow
  code.

## Completion And Recovery

- Terminal workstreams must not start new nodes through CLI, API, or UI.
- Recoverable runtime failures must preserve enough evidence for retry:
  stdout, stderr, exit code, command, run metadata.
- A retry path must distinguish external recovery from semantic repair.
- If a failure is due to quota, reconnect, session state, timeout, or context
  window, record it as runtime recovery, not business rejection.

## Human Gate Semantics

- Gate behavior should be driven by metadata such as purpose, allowed actions,
  and terminal-gate status.
- UI labels and CLI hints should explain:
  - why the workflow stopped;
  - what evidence to inspect;
  - what each allowed action will do next.
- Do not infer gate type from display text or ad hoc node-id substring checks.

## Observability

- Projections are read models, not sources of truth.
- Timeline, trace, operator panel, and API should consume the same ledger facts.
- If display semantics differ from raw node IDs, keep both raw ID and display
  name.

## Technical Debt

- Default to fixing technical debt when it affects the main lifecycle,
  correctness, stability, domain boundaries, artifact contracts, tests, or future
  development speed.
- Keep debt only when there is a clear reason:
  - future direction is genuinely uncertain;
  - fixing now would distract from the main lifecycle;
  - current cost is clearly disproportionate to near-term value;
  - the debt is isolated behind a named boundary.
- Every accepted debt item must document:
  - why it is being kept;
  - why it does not block the main workflow;
  - what signal should trigger revisiting it.
- Do not keep compatibility code that preserves an unsafe or deprecated path
  unless it is required for real users and guarded by explicit tests.

## Tests

- Add a focused regression test for every new boundary rule.
- For runtime adapter changes, test stdout and stderr variants.
- For workflow transitions, test both the expected route and the terminal
  boundary.
- Run at least one controlled lifecycle smoke after changing routing, recovery,
  gates, or projections.
