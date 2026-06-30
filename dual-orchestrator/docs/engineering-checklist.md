# Engineering Checklist

Use this checklist before changing workflow, recovery, artifact contract, human
gate, delivery, or observability code.

For boundary-sensitive changes, first apply the stricter engineering discipline
in `docs/design/boundary-first-engineering-discipline.zh-CN.md`. The checklist
below is not enough by itself when the issue involves lifecycle semantics,
external Agent adapters, recovery, human gates, or projection disagreement.

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

## Phase Promotion Discipline

Before changing runtime execution, provider/token attribution, repo scope
governance, provider-cache-aware behavior, session compaction, real-case
scripts, or benchmark gates, classify the target phase and status:

```text
Target phase: P0 / P0.5 / P1 / P2 / P3
Current phase status: Implemented / Verified / Promoted
Upstream promotion evidence:
```

Definitions:

- `Implemented`: code exists and local tests may pass. This is not enough to
  unlock downstream phases.
- `Verified`: the phase has been proven against its source of truth by targeted
  tests, historical replay, offline calibration, or a controlled smoke.
- `Promoted`: the phase has passed its required gates and can be used as an
  upstream dependency for lower-priority work.

Ordering rule:

- Current phase lock allows only P0 and P0.5 work. P1/P2/P3 are hard-stopped
  until this lock is explicitly removed.
- P0 remains the highest priority. P0.5 work is allowed only for
  provider/model/token/cost/cache attribution, GLM token-only reconciliation,
  pricing catalog calibration, and blockers that directly affect P0/P0.5
  proof.
- P1/P2/P3 work is not allowed during the current lock, even if local code,
  tests, or historical evidence already exist. Do not implement, expand,
  benchmark, switch defaults, or claim benefits for provider-cache-aware,
  session compaction, repo-scope governance, memory-loop, complex real-case, or
  keep-alive work.
- P1/P2/P3 findings may only be recorded as visible P0/P0.5 gaps or blockers.
  They must not turn into feature work.
- After the lock is removed, P0 and P0.5 must both be promoted before starting
  P1/P2/P3 feature work, default-strategy changes, complex real-case benefit
  claims, or token-saving conclusions.
- Lower-priority work is allowed before promotion only when it is explicitly a
  direct fix for the currently locked higher-priority blocker and is gated as
  that higher-priority phase.

Do not use "tests green", "implemented locally", "measure_only", "warning
visible", or "ran one case" as promotion evidence. Promotion evidence must name
the source of truth, the command or replay/calibration used, and the PASS /
FAIL / INVALID criteria.

The executable gate in `scripts/lib/mainline-decision-gate.sh` must reject any
current `Target phase` outside P0/P0.5. If the script and this checklist
disagree, treat that as a boundary drift bug and fix the gate before continuing
feature work.

## Model Ownership Check

Before changing workflow, state, recovery, retry, scheduler, human gate,
artifact contract, or projection code, replay the existing model first:

```text
Existing model:
- Definition:
- Instance:
- Execution:
- Iteration:
- Artifact:
- Gate:
- Goal / Workstream:

Problem ownership:
- Which existing object should own this fact or transition?

New concept needed:
- No / Yes. If yes, why can no existing object carry it?
```

Rules:

- Prefer the existing `Definition / Instance / Execution` vocabulary.
- Do not introduce synonyms such as `attempt`, `run`, `job`, `step`, or `task`
  when an existing model object already fits.
- `Execution` owns concrete runtime facts such as process id, command, stdout,
  stderr, exit code, interruption, stale process, and retry evidence.
- `AgentTask` owns dispatch facts: which agent/tool/session/prompt/context
  package/expected contract should execute one node instance. It does not own
  workflow routing, goal satisfaction, agent-internal ReAct, or MCP/function
  call orchestration.
- `Instance` owns the durable occurrence of a workflow or node in one
  workstream.
- `Iteration` owns semantic revision or repair loops. A runtime retry is not an
  iteration.
- `Goal / Workstream` owns user intent and completion truth. Workflow completion
  alone is not goal satisfaction.

If the same kind of state boundary breaks more than once, assume a model object
is missing or overloaded. Do not add another handler-local `if` until the model
ownership check is written down.

## New Concept Admission

Adding a domain object or new lifecycle term requires answering:

- Which repeated failure does it solve?
- Why can the current model not own this state?
- Where is its source of truth in the ledger?
- Who creates it?
- Who mutates it?
- Who reads it?
- Which projection surfaces must display it?
- Which tests prove the boundary?

If these answers are unclear, do not add the concept. Clarify the model or keep
the change as an explicitly labeled stopgap.

## Stopgap vs Model Fix

When a production or real-case bug needs immediate containment, label the
change explicitly:

```text
Stopgap fix:
- What is contained:
- Why this is not the final model:
- Follow-up model fix:
- Revisit trigger:
```

Do not describe a stopgap as a lifecycle or P0 closure. P0 closure requires the
source-of-truth model, projection behavior, and regression tests to agree.

## Structured State First

- Prefer typed artifacts, node result fields, edge metadata, or gate metadata.
- Avoid deriving domain state from natural-language summaries.
- Text matching is acceptable only as an adapter compatibility fallback, and it
  must live behind a named semantic model or catalog.

## Harness Enforcement

- Treat AGENTS, skills, and prompts as soft discipline only. They can guide an
  agent, but they do not prove the system is safe or correct.
- A mainline boundary is considered enforced only when the harness can reject or
  block invalid behavior:
  - schema / typed artifact validation rejects malformed agent output;
  - runtime guards reject invalid lifecycle transitions;
  - recovery assessment maps typed failures to explicit next actions;
  - human gates accept only allowed actions;
  - consistency tests detect definition / registry drift;
  - projections consume a shared read model instead of inferring state.
- Do not mark a discipline item done just because it was added to a prompt,
  skill, or documentation. Mark it done only after there is code-level
  validation, runtime rejection, or a regression test.
- If the harness cannot yet enforce the rule, record it as a gap in the roadmap
  with owner and next verification step.

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

## Registry And Definition Drift

- Treat workflow JSON as the source of truth for lifecycle structure. Java code
  may load, validate, or execute the definition, but must not silently recreate
  the same graph in another factory or projection.
- A code registry is acceptable only when it is a named boundary, such as a
  handler capability catalog, failure signal catalog, or contract validator.
  It is not acceptable when it becomes a second workflow definition.
- Every registry that references workflow node ids, handler types, or decision
  outcomes must have a consistency test against the loaded workflow definition:
  - every executable workflow node has a resolvable handler;
  - every agent / decision / classifier node has an output contract;
  - every contract decision that drives routing is compatible with outgoing edge
    outcomes;
  - registry entries do not refer to removed workflow nodes unless explicitly
    documented as compatibility debt.
- Do not fix registry drift by adding one more entry only for the failing case.
  Sweep sibling variants and tests in the same boundary.
- If a new rule belongs to `Definition`, put it in the workflow/contract
  resource or loader validation. If it belongs to `Execution`, put it in
  execution metadata. If it belongs to `Projection`, put it in the shared read
  model.

## Completion And Recovery

- Terminal workstreams must not start new nodes through CLI, API, or UI.
- Recoverable runtime failures must preserve enough evidence for retry:
  stdout, stderr, exit code, command, run metadata.
- External agent adapter versions must be pinned before real runs. Do not rely
  on floating npm ranges, implicit latest resolution, or provider defaults when
  the adapter participates in lifecycle state.
- Adapter wrappers must surface the deepest typed provider cause available. If a
  wrapper says "reconnect" but the provider/session log contains quota,
  overload, context-window, permission, or server-side failure evidence, classify
  the provider cause and keep the wrapper message as supporting evidence.
- Model names exposed by an agent adapter are adapter/session state, not dual
  workflow domain state. Do not make model names the default orchestration path;
  classify stale or incompatible model state as a recoverable adapter failure
  with an explicit session rebuild action.
- A retry path must distinguish external recovery from semantic repair.
- If a failure is due to quota, reconnect, session state, timeout, or context
  window, record it as runtime recovery, not business rejection.
- Do not collapse distinct typed runtime failures into one generic operator
  message. `RECOVERABLE_AGENT_RECONNECT`, `RECOVERABLE_SESSION_MISSING`,
  `RECOVERABLE_RATE_LIMIT`, `RECOVERABLE_AGENT_OVERLOADED`, and
  `RECOVERABLE_CONTEXT_WINDOW` need distinct recovery readiness and next
  actions.

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
- Before implementing token, cost, latency, quality, or operator-observability
  work, write the acceptance metric first and verify it at the end. Do not let a
  run fall back to an ambiguous "unknown" state when a typed partial fact exists.
  For token/cost attribution this means preserving provider, model, billing
  mode, real token counts, and whether money can be calculated separately
  (`official_pricing`, `subscription_unpriced`, `token_only`, or
  `pricing_unknown`).
- Operator-facing "why stopped / next action / retry safety" must come from
  `WorkstreamAttention` or an equivalent centralized read model. Do not
  recompute this separately in CLI, API, UI, or markdown summaries.
- If display semantics differ from raw node IDs, keep both raw ID and display
  name.
- Before changing UI / CLI projection, write down the user question first:
  "Where is it?", "Why did it stop?", "What evidence supports the decision?",
  "What should I do next?", or "What happened in this historical node?"
- Do not render a projection by simply dumping the largest available object.
  Put object-specific evidence before shared context:
  - Gate evidence: gate decision, recommendation, reason, latest agent decision,
    missing evidence, must-fix items first; task contract second.
  - Node details: node status, kind, iteration, timestamps, outputs first;
    workflow-wide summary second.
  - Completion views: completion audit and final artifacts first; historical
    timeline second.
- Current-state panels and historical read-only panels must be separate modes.
  Current human gates may expose actions; historical gates must show evidence
  without approval/revise controls.
- If a list contains multiple similar objects, each item must show its
  differentiating fields in the first screen. If every card looks identical,
  the projection is wrong even when the backing data is correct.
- Validate at least two distinct instances of the same projection type before
  calling UI observability work done. For example, inspect two different human
  gates and confirm their first-screen evidence differs.

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
- When a real-case run exposes new provider wording, add the captured wording to
  adapter-level regression tests before calling the recovery fix complete.
- For workflow transitions, test both the expected route and the terminal
  boundary.
- Run at least one controlled lifecycle smoke after changing routing, recovery,
  gates, or projections.
