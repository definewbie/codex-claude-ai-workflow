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

- acpx / ACP session bootstrap or resume failure;
- rate limit or quota;
- Agent reconnect;
- Agent tool permission blocked by a non-interactive permission policy;
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

Runtime classification reads both Agent stdout and stderr. This matters because
different ACP adapters and model providers do not agree on where runtime
diagnostics are written: a quota or HTTP 429 error may appear in stdout while
the acpx connection message remains in stderr. The recovery evidence should
therefore preserve both streams when a run fails.

The adapter keeps runtime failure matching behind a small signal catalog:

- `AcpxFailureEvidence` owns the execution evidence boundary: stdout, stderr,
  and exit code.
- `AcpxFailureSignalCatalog` owns provider / adapter signal patterns such as
  reconnect, missing session, rate limit, and context window.
- `AcpxFailureClassifier` only maps evidence to a signal, or falls back to the
  raw `ACPX_EXIT_<code>` result.

New provider-specific wording should be added to the catalog as another signal
matcher, not scattered through workflow routing, ledger projection, or UI code.

Some ACP adapters can write a clear recoverable signal to stderr without
exiting the outer process promptly. For example, `agent needs reconnect` can
otherwise leave an Agent run stuck in `RUNNING`. This is an adapter execution
boundary, not a UI or human-review concern.

`AcpxAgentTransport` polls stderr for catalog signals during the run. The
runtime boundary is deliberately narrow: process exit, a complete terminal
contract, or a typed recoverable failure. Plain stdout progress is not lifecycle
state because a stuck ACP process can still emit client frames, heartbeats, or
partial tool output.

When a recoverable signal persists for a bounded grace period, the adapter
terminates the acpx process tree and returns a classifiable exit result so the
workflow can move to `WAITING_FOR_RECOVERY`. The grace defaults to 30 seconds
and can be configured with `DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS`. When
the Java process environment exposes `API_TIMEOUT_MS`, the default grace is
derived from that timeout plus a small buffer. If the timeout is only injected
inside the Claude ACP child process, set the dual grace explicitly so the two
timeouts do not fight each other.

There is a second, separate timeout for accepted prompts that stop producing
trusted progress. A reconnect can recover enough to create a new ACP session and
accept `session/prompt`, then still stall without assistant, tool, usage, or
terminal-contract events. That is classified as
`RECOVERABLE_AGENT_OUTPUT_INTERRUPTED`, not as business rejection. The default
progress timeout is 120 seconds and can be configured with
`DUAL_ACPX_AGENT_PROGRESS_TIMEOUT_SECONDS`.

The terminal contract wins over reconnect diagnostics, but only when it is
complete: a closed `dual-control` fence with required fields followed by
`[done] end_turn`. Partial or stale-looking contract fragments are not accepted
as completion.

### acpx Lifecycle Signal Model

Text such as `agent needs reconnect`, `Resource not found`, HTTP 401/429/529,
or tool permission blocked is only an external adapter signal. It is not a
workflow state and it is not a business failure. `Resource not found` is the
important example: it only means one `session/resume` attempt failed. It does
not prove that the current Agent execution failed.

The main lifecycle treats each Agent node as an isolated execution with a
run-scoped session: `acpx <agent> -s <base>-<run_id> -f <prompt>`. The workflow
context is the prompt, context package, and ledger, not a long-lived ACP
session. Session resume remains part of the adapter signal model because older
runs, custom acpx config, or provider wrappers may still surface those
diagnostics, but dual should not require cross-node session memory for
correctness.

The adapter should therefore convert raw logs into a small lifecycle model:

```text
stdout / stderr / acpx stream
  -> AgentRuntimeSignal
  -> AgentLifecycleEvent
  -> AgentExecutionHealth
  -> NodeExecution result
```

Suggested semantics:

| Model | Meaning | Does not own |
|---|---|---|
| `AgentRuntimeSignal` | Raw runtime signal observed from stdout, stderr, or the acpx stream, such as reconnect, auth failed, or rate limit. | Workflow routing. |
| `AgentLifecycleEvent` | acpx / Agent lifecycle event, such as process started, session resume failed, session new created, prompt accepted, tool result, or terminal contract seen. | Business completion. |
| `AgentExecutionHealth` | Current execution health, such as healthy, recovering, stalled, recoverable failure, or terminal success. | Raw log storage or NodeExecution ownership. |

Benefits:

- Avoid repeatedly scanning full stderr and turning a historical reconnect
  warning into a current failure.
- Let slow models continue when there is current lifecycle activity or a final
  terminal contract.
- Let truly unrecovered reconnect, permission, auth, quota, or overload signals
  reach recovery without hanging indefinitely.
- Preserve deeper provider causes such as 401, 429, 529, and context-window
  errors instead of exposing only an acpx wrapper reconnect message.
- Give `interruptRunningProcess` real meaning: it applies only to a current,
  unrecovered runtime signal, not to every historical diagnostic line.

Boundary:

- The adapter judges execution health only. It does not decide whether the
  workstream goal is satisfied.
- The adapter does not perform semantic repair and does not create business
  iterations.
- The adapter does not automatically replay `implement` profile prompts after
  reconnect because the previous process may have partially written files.
- The recovery model may reference runtime signals and lifecycle events, but UI
  and CLI must not infer state directly from stderr.

Risks:

- If lifecycle extraction is too narrow, a real recovery event may be missed and
  a slow Agent may still be killed.
- If extraction is too broad, meaningless heartbeat output may be treated as
  progress and delay recovery.
- If the model reads only acpx stderr, it may still miss provider-level 401/529
  evidence in the session stream.
- Without offsets or timestamps, historical signals can still be consumed more
  than once.

The first implementation should stay small and testable:

- Each signal / event must carry source plus offset or observed time.
- The same historical signal can be consumed only once.
- `session/new success`, prompt accepted, tool result, and complete terminal
  contract can supersede earlier reconnect signals.
- Clear provider stream failures can be promoted to current failure.
- Complete terminal contract always wins over recoverable diagnostics.

Coverage expectations:

| Problem | Covered | Explanation |
|---|---|---|
| `session/resume -> Resource not found -> session/new success` is misclassified as failure | Yes | The resume failure is superseded by session new / Agent activity. |
| GLM / DeepSeek is slow but still working | Partially | Old stderr will not kill the run when there is lifecycle activity or terminal contract; a truly inactive process still enters recovery. |
| acpx process hangs with no recovery event | Yes | A current unrecovered recoverable signal still reaches recovery after bounded grace. |
| provider 401 / quota / overload | Yes | stdout/stderr plus acpx stream classify the deeper runtime failure. |
| implementation phase reconnect after partial writes | Partially | The adapter will not auto-replay; recovery remains visible for operator or semantic repair. |
| OS process tree cleanup is incomplete | Not fully | This is a process-isolation issue and needs later process group / cleanup work. |

Acceptance criteria:

- Replay tests prove historical reconnect does not repeatedly trigger current
  failure.
- Replay tests prove `Resource not found` followed by `session/new success` does
  not enter recovery.
- Replay tests prove unrecovered reconnect eventually becomes
  `RECOVERABLE_AGENT_RECONNECT`.
- Complete terminal contract wins.
- Provider stream 401/529 can override wrapper reconnect.
- A real case with a slow model is not killed by stale stderr.

Read-only observe/review profiles may pass `--prompt-retries 1` to acpx. This
is intentionally limited to read-only or reviewer-style Agent calls, where
replaying the same prompt should not duplicate repository writes.
Implementation profiles do not use prompt-level retries; write-phase recovery
must stay visible as an execution failure or a bounded semantic repair route.

Tool permission failure belongs to the same adapter execution boundary. dual
does not use `approve-all` for non-implementation phases. Workflow nodes declare
a `permission_profile`, and the acpx adapter translates it to concrete tool
arguments:

| Profile | Used by | Permission semantics |
|---|---|---|
| `observe` | Claude intake / design / plan / handoff | Allows Read/Grep/Glob only. It does not auto-approve ACP `execute`; planning and design stages must not run validation commands. |
| `review` | Codex review / acceptance | Similar to `observe`; used for external review and acceptance without shell execution authority. |
| `implement` | Claude implementation / self-test | Explicitly allows implementation and test execution; currently maps to acpx `approve-all`. |

This restriction is intentional. ACP `execute` is a broad permission class, so
trying to allow only "safe Bash commands" at the Claude Code tool-name layer is
not a reliable boundary. Build, typecheck, pytest, npm, npx, and similar
commands belong to implementation/self-test execution, not to intake, design,
plan, or review.

If an agent asks for a tool outside the profile in non-interactive mode, the
adapter normalizes `Tool permission request failed` / `Tool use aborted` into
`RECOVERABLE_AGENT_PERMISSION_BLOCKED`. That is not a business rejection and
must not trigger a semantic repair loop. The operator should inspect whether
the node profile is too narrow or whether the agent attempted to cross the
phase boundary.

## Automatic Recovery Scheduling

Automatic recovery is deliberately narrow.

The scheduler reads `RecoveryAssessment`. It may run `retry + resume` only when
the active failure is classified as:

```text
classification = recoverable_agent_runtime
retryReadiness = RETRY_AFTER_EXTERNAL_FIX
```

That covers external runtime failures such as quota windows, reconnects, or
session state after the external cause has been fixed. The scheduler does not
retry:

- artifact contract failures;
- semantic revision limits;
- manual recovery cases;
- human gates;
- completed or failed workstreams.

This keeps the domain boundary clean: failure classifiers explain the failure,
the scheduler advances only safe runtime cases, and humans still own product or
contract judgment.

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

`REJECT` creates a repair route and is displayed as failure classification.
`ACCEPT_WITH_NOTES` records notes classification data and can still proceed to
reporting when the notes are non-blocking.

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
