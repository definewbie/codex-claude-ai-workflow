# Mainline Roadmap

中文版本：[mainline-roadmap.zh-CN.md](mainline-roadmap.zh-CN.md)

This roadmap tracks the remaining mainline work for making dual-orchestrator run
a complete agent-assisted development lifecycle with bounded human judgment.

It is intentionally short. Detailed design belongs in the linked design docs.

## Principle

The mainline is not "add every useful feature." The mainline is the smallest set
of capabilities required to let a workstream move from user goal to safe
delivery:

```text
goal -> safe worktree execution -> verified artifacts -> human decision ->
delivery merge -> traceable completion
```

## Design Coverage

This roadmap is the planning index. Each P0-P2 item should point to a stable
design document before implementation is considered complete. A feature can be
implemented incrementally, but the domain boundary, source of truth, and
projection surfaces must be documented up front.

| Priority | Design area | Design document |
|---|---|---|
| P0 | Goal loop, semantic repair, completion contract | [goal-driven-workstream-loop.md](design/goal-driven-workstream-loop.md) |
| P0 | Workflow states and terminal guards | [workflow-state-machine.md](design/workflow-state-machine.md) |
| P0 | Delivery merge from accepted worktree to original repo | [delivery-merge.md](design/delivery-merge.md) |
| P1 | Definition / Instance / Execution and AgentTask boundary | [execution-instance-model.md](design/execution-instance-model.md) |
| P1 | Artifact contracts and context budget governance | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) |
| P1 | Recovery classification and calibration | [failure-recovery-and-calibration.md](design/failure-recovery-and-calibration.md) |
| P2 | Operator overview, trace, API, and UI projection consistency | [operator-observability.md](design/operator-observability.md) |
| P2 | Public benchmark and release evidence | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) |
| P2 | Open-source release readiness | [release-checklist.md](release-checklist.md) |

If an item in the tables below does not have a corresponding design document,
that is a planning gap, not an implementation shortcut.

## P0: Lifecycle Closure

P0 work blocks reliable product use.

| Item | Status | Purpose | Design |
|---|---|---|---|
| Delivery merge | Done for MVP | Apply an accepted worktree diff back to the original repo safely. | [delivery-merge.md](design/delivery-merge.md) |
| Goal-driven repair loop | Partial | Let Codex rejection or insufficient evidence trigger bounded repair, not ad hoc reruns. | [goal-driven-workstream-loop.md](design/goal-driven-workstream-loop.md) |
| Human gate reduction | Partial | Keep human review only at meaningful product, design, acceptance, and delivery boundaries. | [operator-observability.md](design/operator-observability.md) |
| Terminal-state guards | Implemented, keep testing | Completed workstreams allow observation and delivery actions, not accidental resume/retry. | [workflow-state-machine.md](design/workflow-state-machine.md) |
| Harness enforcement | Partial | Do not rely on the LLM to obey discipline; enforce boundaries with contracts, runtime guards, and consistency tests. | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

### P0 Delivery Merge Checklist

Delivery Merge has been implemented as an explicit post-completion delivery
action. It is not part of workflow `resume` or background scheduling.

| Area | Status | Evidence |
|---|---|---|
| Design | Done | [delivery-merge.md](design/delivery-merge.md) defines the boundary, preconditions, ledger shape, CLI, and tests. |
| CLI | Done | `dualctl delivery merge --workdir <source_worktree> --target <original_repo> <workstream_id>` |
| Git adapter | Done | `GitPatchMergeService` generates and applies a tracked-file patch while excluding ledger/build artifacts. |
| Ledger model | Done | `delivery/merge-plan.json`, `merge.patch`, changed/excluded file lists, and apply logs. |
| Projection | Done for CLI/read model | `trace`, `summary`, and `status` expose merge status and switch the next action after `APPLIED`; a UI action button remains optional P2 work. |
| Tests | Done | `GitPatchMergeServiceTest` and `DeliveryMergeCommandTest` cover accepted merge, rejected acceptance, dirty target, excluded paths, and unsupported untracked business files. |
| Review | Done | Boundary review: delivery is an explicit command; completed workflow remains terminal; target repo mutation is not automatic. |
| Refactor | Acceptable for MVP | The first version supports tracked business files only. Untracked business files fail clearly instead of being silently merged. |
| Delivery validation | Done | 2026-06-17: replayed a completed real-acpx contract smoke workstream (`ws-20260617T005016Z-N8LF`) and applied its accepted worktree diff to a temporary clean target repo. `merge-plan.json` reached `APPLIED`, target staged `README.md` and `main.py`, the original smoke repo stayed clean, and `status` / `summary` / `trace` projected the applied merge. |

### P0 Goal Loop / Human Gate Checklist

| Area | Status | Evidence |
|---|---|---|
| Goal satisfaction | Done, keep testing | `GoalSatisfactionWriter` records domain outcomes such as `SATISFIED`, `NEEDS_REPAIR`, and `NEEDS_REDESIGN`. |
| Repair loop routing | Done, keep testing | Full and contract workflow variants route acceptance `REJECT` through `acceptance.classify_failure` before starting a semantic repair iteration. |
| Runtime retry vs semantic repair | Done, keep testing | Runtime recovery keeps the same active node; loop transitions create a new semantic iteration with a loop reason. |
| Human gate action semantics | Done | Ledger normalizes human `approve` to the workflow outcome `approved` when needed, so CLI/API/UI do not each implement their own translation. |
| Gate reduction policy | Partial | Real product runs still stop at meaningful gates. Benchmark `autorun` can auto-resolve gates, but scheduler does not bypass human decisions. |
| Tests | Done | Ledger and workflow definition tests cover classified repair loops, revision limits, final gate completion, terminal guards, and gate action normalization. |
| Real-case validation | Pending | Run a medium real case and verify that rejection or insufficient evidence enters repair instead of manual rerun. |

### P0 Harness Enforcement Checklist

LLMs may propose, generate, and explain, but the main lifecycle must not depend
on the LLM voluntarily following project discipline. P0 turns discipline into
hard harness constraints.

| Area | Status | Evidence / Next step |
|---|---|---|
| Terminal guard | Done, keep testing | Runtime prevents completed workstreams from continuing through resume/retry/approve. Keep covering it with smoke and real cases. |
| Human gate action boundary | Done, keep testing | Gate actions are normalized by the ledger; CLI/API/UI should not translate `approve/approved` themselves. |
| Recovery typed action | Done, keep testing | `RecoveryAssessment` maps typed runtime reasons to explicit operator actions; `StatusCommand`, `SummaryCommand`, and next-action hints consume the shared attention/recovery read model. |
| Definition/Contract consistency | Done, keep testing | `ContractRegistryTest` loads bundled workflow JSON and checks node/contract existence plus decision/classifier routing outcomes. |
| Definition/Handler consistency | Done, keep testing | `AcpxWorkflowHandlerRegistryFactoryTest` resolves every bundled workflow node through the centralized handler capability catalog. |
| Projection read model | Partial | "Where is it, why stopped, what next" must come from the shared read model; trace/status/API/UI must not infer separately. |
| Real-case boundary loop | Pending | When real cases expose boundary drift, run Analyze -> Modify -> Review -> Reflect instead of adding one more string rule. |

## P1: Long-Running Reliability

P1 work keeps long agent runs recoverable and affordable.

| Item | Status | Purpose | Design |
|---|---|---|---|
| Execution / AgentTask boundary | Partial, worker MVP added | Separate node instance, concrete execution, and agent dispatch facts. | [execution-instance-model.md](design/execution-instance-model.md) |
| Recovery classification | Partial | Distinguish runtime retry, semantic repair, rejected acceptance, and external quota/session failures. | [failure-recovery-and-calibration.md](design/failure-recovery-and-calibration.md) |
| Context budget governance | Partial | Prevent context packages from growing until the system becomes unusable. | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) |
| Artifact contract stability | Partial | Route on typed artifacts and states, not natural-language guesses. | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) |
| Boundary drift prevention | Partial | Prevent workflow definitions, contract registries, handler registries, and projections from drifting apart. | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

### P1 Artifact / Context Checklist

| Area | Status | Evidence |
|---|---|---|
| Typed artifact contracts | Done, keep testing | `ArtifactContractValidator`, `ContractRegistry`, and node output contracts validate Agent sidecars before routing. |
| Context budgets | Done, keep testing | `ContextProfiles` defines profile budgets; `ContextBuildPackageHandler` enforces estimated token limits and degrades/drops sections. |
| Context package metadata | Done | Every context package now writes a JSON sidecar with profile, token budget, estimated tokens, and per-section degradation facts. |
| Projection | Partial | API overview exposes the latest context package metadata artifact; richer CLI/UI budget summaries can follow. |
| Tests | Done | `ContextBuildPackageHandlerTest` covers budget enforcement, degradation, and metadata sidecar creation. |
| Real-case validation | Pending | Re-run a medium real case and confirm generated prompts no longer grow unexpectedly beyond the governed package preview. |

### P1 AgentTask / Worker Checklist

| Area | Status | Evidence |
|---|---|---|
| AgentTask dispatch ledger | Done | `queue/agent-tasks/<id>.json` records `QUEUED`, `RUNNING`, `SUCCEEDED`, and `FAILED`. |
| Inline compatibility | Done | Default `dualctl run` still executes nodes inline. Existing lifecycle behavior is unchanged. |
| Worker dispatch mode | MVP done | `dualctl run --dispatch-mode WORKER` queues one AgentTask and pauses with the node `RUNNING`. |
| Local worker | MVP done | `dualctl worker run-once --workdir <effective_workdir> <workstream_id>` claims one queued task, creates one `NodeExecution`, executes the handler, and routes through the ledger. |
| Multi-worker platform | Not planned for MVP | No leases, heartbeats, distributed scheduler, or multi-task fan-out yet. Add only if real unattended runs need it. |
| Tests | Done | `WorkflowRuntimeTest` covers queue-only dispatch and worker run-once routing. |

## P2: Operator Usability

P2 work makes the system understandable enough for real use and open-source
evaluation.

| Item | Status | Purpose | Design |
|---|---|---|---|
| Operator Panel consistency | Partial | UI, API, and CLI answer the same question: where is it, why stopped, next action. | [operator-observability.md](design/operator-observability.md) |
| Delivery view | Partial | Show delivery merge readiness and result in the completed workstream view. | [delivery-merge.md](design/delivery-merge.md) |
| Public benchmark | Partial | Provide repeatable public evidence without private project files. | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) |
| Open-source docs cleanup | Partial | Keep docs readable, current, and not tied to internal run history. | [release-checklist.md](release-checklist.md) |

## Current Next Step

Do not keep blindly running real cases yet. First close the P0 harness
enforcement gaps so each real case does not rediscover the same boundary
problems.

Medium-complexity cancer-clinic real-case result on 2026-06-17:

- The intake prompt was about 6.3KB, so context budget growth was not reproduced.
- After `acpx`/Claude wrote `agent needs reconnect` to stderr, the workflow now
  converges from `RUNNING` to `WAITING_FOR_RECOVERY` instead of hanging
  indefinitely.
- Business progress is currently blocked by external Claude/acpx session
  reconnect. The orchestrator-side recovery classification, CLI next action,
  and ledger evidence are replayable.

P0-1 Delivery Merge is closed for the current MVP boundary. It remains an
explicit post-completion command; it does not reopen or resume a completed
workflow.

After the external Agent channel is healthy again:

1. Re-run a medium cancer-clinic real case and observe context budget, recovery,
   artifact contracts, and the goal loop together.
2. If that private case is accepted and the original repo is intentionally ready
   to receive staged changes, run explicit delivery merge again as private
   production evidence:

```bash
dualctl delivery merge --workdir <source_worktree> --target <original_repo> <workstream_id>
dualctl trace --workdir <source_worktree> <workstream_id>
```

Delivery Merge remains an explicit post-completion action. It is not part of
automatic scheduling.
