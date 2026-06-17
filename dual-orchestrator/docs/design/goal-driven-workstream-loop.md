# Goal-Driven Workstream Loop

> Status: P0 design contract
> Last updated: 2026-06-15

This document defines the main product loop for unattended work. A workstream is
not just a pipeline that reaches the last node. It is a durable loop around one
user goal.

## 1. Goal

The system should be able to take one engineering goal and keep iterating until
one of these domain outcomes is reached:

| Outcome | Meaning |
| --- | --- |
| `SATISFIED` | The verifier accepted the result and required completion artifacts exist. |
| `SATISFIED_WITH_NOTES` | The goal is acceptable but non-blocking notes remain. |
| `REJECTED` | The verifier or human rejected the result and no automatic repair should continue. |
| `BLOCKED` | Progress needs external recovery, missing context, quota, reconnect, or human decision. |
| `ABANDONED` | The operator intentionally stopped the goal. |

`WorkflowStatus.COMPLETED` is an execution fact. Goal satisfaction is a domain
fact. They should agree at the end, but they are not the same concept.

### 1.1 Goal Loop Is Not A Pipeline

The system must not collapse into a simple pipeline:

```text
intake -> design -> plan -> implement -> self-test -> acceptance -> done
```

That sequence is only the main path of one workflow execution. Unattended work
comes from a goal loop:

```text
Goal
  -> build task contract
  -> run the current lifecycle iteration
  -> collect artifacts / self-test / verifier review / human gate decision
  -> evaluate goal satisfaction
      -> SATISFIED: enter completion / handoff / delivery
      -> SATISFIED_WITH_NOTES: enter completion with non-blocking notes
      -> NEEDS_REPAIR: create a repair iteration from evidence and return to implementation or verification setup
      -> NEEDS_REDESIGN: create a revision iteration from evidence and return to design / plan
      -> BLOCKED: enter recovery or human decision
      -> REJECTED: stop with evidence and do not continue automatically
```

The workflow graph answers "how does this round advance?" The goal loop answers
"is the goal satisfied, and should the system carry evidence into another
round?"

This boundary is owned by `Goal / Workstream / CompletionContract / Iteration`.
`WorkflowExecution`, LiteFlow, or any routing kernel must not decide goal
satisfaction by itself.

### 1.2 Two Loop Types

| Loop | Trigger | Creates | Must not create |
| --- | --- | --- | --- |
| Runtime loop | quota, reconnect, dead PID, context window, transient external tool failure | New `WorkflowExecution` + new `NodeExecution` | New `Iteration` |
| Goal loop | verifier reject, human revise, weak acceptance evidence, unmet requirement or design | New semantic `Iteration` + related repair/revision nodes | New Workstream |

Runtime loop answers whether this execution ran reliably. Goal loop answers
whether the user goal is actually satisfied.

## 2. Domain Model

```text
Goal
  -> Workstream
       -> WorkflowInstance
            -> WorkflowExecution
                 -> NodeInstance
                      -> NodeExecution
       -> Iteration
       -> Artifact
       -> Gate
       -> Decision
```

| Object | Owns | Does not own |
| --- | --- | --- |
| Goal | User intent, acceptance basis, final domain outcome. | Process PIDs or edge routing. |
| Workstream | Workspace, ledger, lifecycle history for one goal. | Low-level tool invocation details. |
| WorkflowInstance | One workflow definition instance inside the workstream. | Single command execution details. |
| WorkflowExecution | One `run` / `resume` / scheduler tick advancement session. | Business acceptance. |
| NodeInstance | One logical node occurrence inside a workflow instance. | Process history. |
| NodeExecution | One concrete execution of a node instance with PID, stdout, stderr, exit code. | Goal-level status. |
| Iteration | A revision or repair loop with a trigger reason. | Ordinary runtime retry. |
| Artifact | Structured evidence and human-readable output. | Hidden state. |
| Gate | Human or recovery decision point with evidence and allowed actions. | Ad hoc workflow validation. |

## 3. Current P0 Contract

P0 is not feature expansion. P0 means the single-workstream lifecycle is
closed enough to trust on real work.

Required behavior:

- Terminal workflows cannot create new nodes through CLI, API, UI, or scheduler.
- A dead `RUNNING` process is recovery, not implicit re-execution.
- `retry` reuses the same active node and does not create a new iteration.
- `revise` and semantic repair create a new iteration with a reason.
- Human gates expose evidence and allowed actions; historical gates are read-only.
- CLI trace and Operator overview answer the same three questions:
  - where is it now;
  - why did it stop or continue;
  - what is the safe next action.
- Agent decisions must enter typed artifacts. The orchestrator must not infer
  lifecycle decisions from prose.

## 4. Recovery vs Repair

| Type | Examples | Correct behavior |
| --- | --- | --- |
| Runtime recovery | quota, reconnect, missing session, context window, dead process | keep same active node, open recovery, retry after external cause is fixed |
| Semantic repair | verifier reject, missing requirement, scope violation, weak self-test | create repair/revision iteration with explicit input artifacts |

Do not use a single generic retry path for both. Runtime recovery is about
`NodeExecution` reliability. Semantic repair is about goal satisfaction.

### 4.1 WorkstreamAttention

`WorkstreamAttention` is the operator-facing read model for one question:

```text
Why is this workstream running or stopped, is retry safe, and what should the
operator inspect or do next?
```

It does not own state. It reads authoritative ledger facts:

- `workflow/current.json`
- the active `nodes/*.json`
- `domain/goal-satisfaction.json`
- recovery summaries / assessments
- evidence refs

CLI trace, Operator Panel overview, API overview, and `current-failure.md`
should consume the same attention result instead of computing lifecycle
semantics independently.

Core categories:

| Category | Meaning | Retry posture |
| --- | --- | --- |
| `RUNTIME_RECOVERY` | Runtime reliability issue such as quota, reconnect, session state, dead PID. | Retry after the external cause is fixed. |
| `ARTIFACT_CONTRACT_FAILURE` | Agent output did not satisfy the typed artifact contract. | Fix prompt / contract output, then retry. |
| `SEMANTIC_REVISION_LIMIT` | Design or planning revisions are exhausted; the goal did not converge. | Blind retry is unsafe. |
| `GOAL_REPAIR_REQUIRED` | Verifier or acceptance evidence says implementation repair is needed. | Enter the goal repair loop. |
| `GOAL_REDESIGN_REQUIRED` | Review evidence says the design or plan must be revised. | Enter the design / plan loop. |

`revision_limit_reached` must not be rendered as a generic external recovery
problem. It means the goal/design loop failed to converge and the operator
should inspect scope, test policy, task contract, and design evidence.

## 5. LiteFlow Boundary

LiteFlow owns execution graph evaluation:

```text
which node should become active next
```

LiteFlow does not own:

- goal satisfaction;
- artifact validation;
- human approval semantics;
- recovery classification;
- workstream completion contract;
- UI/CLI projection.

This boundary lets the graph grow later without turning graph routing into the
domain model.

## 6. Context Hierarchy

Long-running goal loops require compact context.

Planned context layers:

| Layer | Purpose |
| --- | --- |
| Raw artifacts | Full stdout, reports, diffs, review packets. |
| Stage summaries | Summary of one lifecycle stage. |
| Goal gist | Compact cross-stage memory for the whole goal. |
| Current packet | Minimal input for the next agent task. |

The first version should add `goal-gist.md` and `goal-gist.json` as typed
artifacts. They should point to raw evidence instead of copying everything.

## 7. Later Extensions

These extensions are important but should build on the P0 contract.

| Capability | Dependency |
| --- | --- |
| Task Queue | Needs clear `NodeInstance` / `NodeExecution` ownership. |
| Fork / Join | First version has transition metadata and `all_terminal` ledger joins; true parallel worker execution still needs Task Queue. |
| Compensation | Needs separated recovery and repair semantics. |
| Parallel agents | Needs queue ownership and artifact conflict policy. |

## 8. Validation Case

A real cancer-clinic run on 2026-06-15 exposed the P0 gap:

```text
node-intake-claude_brainstorm-001
  status: RUNNING
  result.running_pid: 11243
  stderr: agent needs reconnect
  process: gone
```

Before the P0 fix, the workflow stayed `RUNNING` and could be misunderstood by
the UI. The required behavior is:

```text
RUNNING + dead pid
  -> node_stale_running_detected
  -> node_stale_running_recovered
  -> node FAILED
  -> workflow WAITING_FOR_RECOVERY
  -> recovery/current-failure.md
```

This case is now part of the P0 regression boundary.
