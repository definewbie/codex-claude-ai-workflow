# Definition / Instance / Execution Model

> Status: P0 domain-model foundation
> Updated: 2026-06-15

This document defines the execution-instance model for `dual-orchestrator`.
The goal is not to introduce a heavier workflow framework. The goal is to make
the repeated state-boundary failures explicit: what is a definition, what is an
instance, and what is one concrete execution.

The model borrows the useful separation from Spring Batch: `Job`,
`JobInstance`, `JobExecution`, `Step`, `StepExecution`, and `ExecutionContext`.
`dual-orchestrator` does not adopt Spring Batch directly because this project is
not a data batch processor; it is a long-running agent engineering harness with
human gates, artifact contracts, recovery, and delivery boundaries.

## 1. Why This Model Exists

Several concepts have been overloaded:

- the workflow graph looked like both a definition and the product lifecycle;
- `nodes/*.json` carried both node-instance state and process/runtime facts;
- `runs/` was both an agent-run ledger and a source for node execution state;
- retry, resume, repair, and iteration could drift into each other;
- UI, CLI, and API sometimes explained the same state from different places.

The fix is a clearer model, not another patch.

## 2. Core Layers

```text
WorkflowDefinition
  -> WorkflowInstance
       -> WorkflowExecution
            -> NodeInstance
                 -> AgentTask
                 -> NodeExecution
            -> ExecutionContext

Workstream
  -> Goal
  -> Iteration
  -> Artifact
  -> Gate
  -> CompletionContract
```

`WorkflowExecution` and `NodeExecution` are the model terms. Do not introduce
`Attempt`, `Run`, or `Job` as synonyms for them.

## 3. Object Definitions

| Object | Definition | Main ledger path | Does not own |
| --- | --- | --- | --- |
| `WorkflowDefinition` | Template with nodes, edges, gates, loops, completion paths. | `workflow/definition.json` | Whether one execution finished. |
| `WorkflowInstance` | One workflow instance started for a workstream. | `workflow/instance.json` | Single command execution details. |
| `WorkflowExecution` | One session that advances a workflow instance. | `executions/workflow/<id>.json` | Business goal satisfaction. |
| `NodeInstance` | One logical occurrence of a node in a workflow instance. | `nodes/<node_instance_id>.json` | Full process history. |
| `AgentTask` | A local dispatch work item that assigns one node instance to a concrete agent / tool / session. | `queue/agent-tasks/<id>.json` | Routing, agent-internal ReAct, MCP call orchestration. |
| `NodeExecution` | One concrete execution of a node instance. | `executions/nodes/<id>.json` | Goal-level status. |
| `ExecutionContext` | Recoverable context for a workflow instance. | `workflow/execution-context.json` | Hidden business state. |
| `Iteration` | Semantic revision or repair thread. | `iterations/<iteration_id>.json` | Ordinary runtime retry. |

`WorkflowExecution` records who triggered an advancement session, when it
started and ended, how many steps were allowed, which `NodeExecution` records it
created, and why it stopped. One command finishing is not the same as the
workflow finishing.

`NodeExecution` records handler, agent/session/command, PID, stdout/stderr
refs, output refs, exit code, failure classification, recoverability, stale
state, timestamps, and duration.

For agent calls:

```text
NodeExecution
  -> tool_type: acpx
  -> agent_run_ref: runs/<agent_run_id>
```

`runs/` remains the tool-specific ledger. `executions/nodes/` is the execution
model ledger.

### AgentTask

`AgentTask` is the AI-specific dispatch layer that traditional batch models do
not need. A batch `StepExecution` usually invokes stable Java code. An agent
step also needs a durable assignment:

- which agent should execute it;
- which session should be used;
- which prompt template should be rendered;
- which context package should be attached;
- which artifact contract is expected;
- which permission profile / tool allowlist is active;
- whether the work item is `QUEUED`, `RUNNING`, `SUCCEEDED`, or `FAILED`.

The boundary is:

```text
NodeInstance
  -> AgentTask        who should execute this node and with which dispatch inputs
  -> NodeExecution    what actually happened during this execution
```

The permission profile is an `AgentTask` dispatch fact. It is not workflow
state and it is not a UI concern. The workflow definition declares a stable
`permission_profile`; the adapter translates it to concrete tool arguments such
as acpx `--allowed-tools` or `--approve-all`. This lets intake/design/review
observe the repository safely while only implementation phases can mutate code.

The MVP implementation keeps the relation simple:

```text
one NodeInstance
  -> one AgentTask
  -> one NodeExecution
```

The first worker implementation is local and single-task:

```text
dualctl run --dispatch-mode WORKER
  -> enqueue one AgentTask and leave the NodeInstance RUNNING

dualctl worker run-once
  -> claim one QUEUED AgentTask
  -> create one NodeExecution
  -> execute the handler
  -> return the NodeResult to workflow routing
```

This is deliberately not a multi-worker platform. A later queue expansion may
add leases, heartbeats, multiple workers, fork/join branches, or multiple agent
tasks per node instance, but that is not the default meaning. A workflow node
should still represent one clear lifecycle stage. If Claude and Codex need to
collaborate over multiple rounds, model that with workflow graph edges, loops,
and gates rather than hiding multiple agents inside one node.

`AgentTask` explicitly does not:

- orchestrate an agent's internal ReAct loop;
- schedule individual MCP / function calls;
- decide the next workflow transition;
- judge goal satisfaction;
- replace `NodeExecution` as runtime evidence.

Its value is a stable local dispatch boundary: queueable, observable,
recoverable, replayable, and ready for future fork/join, gist-summary, and
worker execution without turning dual into an agent runtime platform.

## 4. Spring Batch Mapping

| Spring Batch | dual-orchestrator | Borrowed idea | Not copied because |
| --- | --- | --- | --- |
| `Job` | `WorkflowDefinition` | Template definition. | dual needs human gates, artifact contracts, and agent roles. |
| `JobInstance` | `WorkflowInstance` | One concrete workflow instance. | dual's aggregate root is Workstream. |
| `JobExecution` | `WorkflowExecution` | Each launch/resume gets an execution record. | dual can pause at gates or recovery boundaries. |
| `Step` | `NodeDefinition` | Step template. | dual nodes include agents, gates, decisions, and artifact handlers. |
| `StepExecution` | `NodeExecution` | One concrete step execution. | dual also records acpx sessions, PIDs, stderr/stdout, and contract refs. |
| `ExecutionContext` | `ExecutionContext` | Restartable context. | dual must be replayable from a file ledger. |

The discipline is:

```text
Definition = what it is
Instance = this one
Execution = this time it ran
```

## 4.1 Spring Batch Does Not Own The Goal Loop

The Spring Batch model only informs execution layering. It does not replace the
project's goal loop.

```text
WorkflowExecution
  owns how one advancement session ran, stopped, and recovered.

Goal Loop
  owns whether the goal is satisfied, whether repair/revision is needed, and
  when to stop.
```

Introducing `WorkflowExecution` must not turn the system into:

```text
run workflow once -> completed
```

The correct semantics are:

```text
run one workflow execution
  -> produce artifacts and decisions
  -> CompletionContract evaluates goal satisfaction
  -> SATISFIED / SATISFIED_WITH_NOTES / NEEDS_REPAIR / NEEDS_REDESIGN / BLOCKED / REJECTED
  -> maybe create a new semantic Iteration
```

`WorkflowExecution` records how this session ran. `CompletionContract` plus
verifier/human artifacts decide whether the goal loops again. LiteFlow is the
same: it may return transition candidates, but it must not own goal
satisfaction.

## 5. Runtime Rules

Runtime retry means:

```text
same Workstream
same WorkflowInstance
same NodeInstance
new WorkflowExecution
new NodeExecution
same Iteration
```

Semantic repair means:

```text
same Workstream
same WorkflowInstance
new Iteration
new NodeInstance(s) for the repair path
new WorkflowExecution when execution resumes
new NodeExecution for each concrete run
```

When a workflow instance is ended, `run`, `resume`, `review approve --run`, and
scheduler advancement must refuse or skip it. UI actions must become read-only.

## 6. Ledger Plan

Target structure:

```text
.agent/workstreams/<workstream_id>/
  workflow/
    definition.json
    instance.json
    current.json
    execution-context.json
  queue/
    agent-tasks/
      <agent_task_id>.json
  executions/
    workflow/
      <workflow_execution_id>.json
    nodes/
      <node_execution_id>.json
  nodes/
    <node_instance_id>.json
  gates/
  iterations/
  runs/
  artifacts/
  reports/
  events.jsonl
  timeline.json
```

Compatibility:

- keep existing `runs/` as tool-specific ledger;
- keep existing `nodes/*.json` and gradually move process details into
  `NodeExecution`;
- keep `workflow/current.json` for current pointers, but reduce projection logic
  that interprets it alone.

## 7. Complete Implementation Plan

This is the full blueprint, not a minimal patch plan. Each phase must leave the
system runnable.

### Phase A: Terminology And Documentation

- Replace `Attempt` / `ExecutionAttempt` with `NodeExecution`.
- Define `WorkflowExecution` as one run/resume/scheduler advancement session.
- Update state-machine, goal-loop, architecture, and engineering checklist docs.
- Make Definition / Instance / Execution ownership checks mandatory.

### Phase B: Ledger Structure

- Add `NodeExecutionDocument`.
- Add `WorkflowExecutionDocument`.
- Add `executions/nodes/` and `executions/workflow/` paths.
- Add execution writer/repository.
- Link `NodeInstance` to `latestExecutionId`.
- Link acpx agent run metadata via `agentRunRef`.

### Phase C: Runtime Boundary Migration

- Read latest `NodeExecution` for `RUNNING` guards.
- Mark dead PID as `NodeExecution.STALE`.
- Move the related `NodeInstance` to failure/recovery.
- Retry creates a new `NodeExecution`, not a new `Iteration`.
- Terminal workflow refuses new `WorkflowExecution` or records a terminal
  refusal.

### Phase C2: Goal Loop Evaluation Boundary

Goal: prevent the implementation from stopping at execution layering and
quietly becoming a one-shot pipeline.

- Define `CompletionContract` inputs: verifier artifact, human gate decision,
  self-test evidence, and required artifact status.
- Add or strengthen a goal-satisfaction read model.
- Map `SATISFIED`, `SATISFIED_WITH_NOTES`, `NEEDS_REPAIR`,
  `NEEDS_REDESIGN`, `BLOCKED`, and `REJECTED` to explicit domain outcomes.
- `NEEDS_REPAIR` / `NEEDS_REDESIGN` must create a new `Iteration` with reason
  and evidence refs.
- Runtime retry may only create a new `NodeExecution`; it must not masquerade as
  a goal loop.

Acceptance:

- acceptance rejection is not treated as runtime failure;
- runtime reconnect does not create a semantic iteration;
- each goal-loop return has evidence and reason;
- trace / API / Operator Panel explain whether a stop is runtime retry or goal
  repair loop.

### Phase D: Projection Unification

- Strengthen `ExecutionOverview`.
- Aggregate WorkflowInstance, latest WorkflowExecution, active NodeInstance,
  latest NodeExecution, and CompletionAudit.
- Make `dualctl trace`, API overview, and Operator Panel read the same model.

### Phase E: Completion / End Separation

- Persist `domain/completion-audit.json` when routing reaches workflow
  completion.
- Treat legacy `WorkflowStatus.COMPLETED` as the compatible current-state
  representation, and expose `lifecycleState=ENDED` through the completion
  audit read model.
- Give summary, metrics, delivery evidence, and final report explicit owners.
- Keep post-completion observation read-only: trace, summary, metrics, and
  delivery inspection must not create new workflow nodes.

### Phase F: Loop / Fork / Join Expansion

- Add `TransitionMetadata` to workflow edges so loop/fork/join semantics live
  in `WorkflowDefinition`, not in node-id conventions.
- Standardize loop transitions with reason, trigger, source node/artifact, and
  new semantic iteration evidence.
- Add fork branch group id, requiredness, and purpose; successor nodes and
  `node_routed` events carry the transition metadata. Successor nodes store
  inbound transition evidence in `NodeInstance.inputTransition`, not in handler
  `result`.
- Add join strategy; first version supports `all_terminal` by deferring join
  activation until all active nodes in the branch group have terminated.
- Keep LiteFlow as a routing kernel returning transition candidates only.

## 8. Anti-Drift Rule

Every workflow/state/recovery/gate/projection change must answer:

```text
Definition:
Instance:
Execution:
Iteration:
Artifact:
Gate:
Goal / Workstream:

Which layer owns this change?
Is a new concept being introduced?
If yes, why can no existing object own it?
Do UI/CLI/API read from the same read model?
Which terminal/recovery/loop tests cover the boundary?
```

If the answers are unclear, do not start coding.
