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

### P0 Closure Protocol

P0 must close once, with fixed exit criteria. Real-case validation is an
acceptance gate, not an open-ended discovery loop.

Exit criteria:

| Boundary | Required evidence |
|---|---|
| Terminal guard | Completed workstreams reject accidental resume, retry, and approve-run while still allowing trace, summary, and delivery inspection. |
| Goal loop | Acceptance rejection or insufficient evidence creates a bounded semantic repair path instead of a manual rerun. |
| Runtime recovery | External Agent/acpx failures become typed recovery evidence and next actions, not business rejection. |
| Artifact contract | Routing consumes structured artifacts and sidecars before natural-language text. |
| Delivery merge | Accepted worktree changes can be explicitly merged into a clean original repo without automatic commits. |
| Projection | CLI trace/status/summary and API/operator overview answer the same three questions: where is it, why did it stop, what should happen next. |

Closure run:

1. Run deterministic checks: targeted Maven tests, workflow/contract consistency tests, and lifecycle smoke.
2. Run one medium-complexity real case that touches business logic, not only a UI text/page change.
3. Classify any failure as either:
   - `P0 blocker`: fix before closure and rerun only the related checks plus the closure real case.
   - `Post-P0`: record under P1/P2 and do not reopen P0.
4. Write a closure report with command evidence, workstream id, final status, artifact status, delivery merge status, and accepted Post-P0 items.

The current closure report is tracked in
[p0-closure-report.md](testing/p0-closure-report.md). P0 is now
`Done with known limits`: the private business-logic real case reached
`COMPLETED` with `ACCEPT_WITH_NOTES`, two semantic repair loops, and a clean
original repo. Future real cases are regression checks or P1/P2 validation, not
P0 exploration.

| Item | Status | Purpose | Design |
|---|---|---|---|
| Delivery merge | Done for MVP | Apply an accepted worktree diff back to the original repo safely. | [delivery-merge.md](design/delivery-merge.md) |
| Goal-driven repair loop | Done for MVP, keep testing | Let Codex rejection or insufficient evidence trigger bounded repair, not ad hoc reruns. | [goal-driven-workstream-loop.md](design/goal-driven-workstream-loop.md) |
| Human gate reduction | MVP acceptable, P2 polish | Keep human review only at meaningful product, design, acceptance, and delivery boundaries. | [operator-observability.md](design/operator-observability.md) |
| Terminal-state guards | Implemented, keep testing | Completed workstreams allow observation and delivery actions, not accidental resume/retry. | [workflow-state-machine.md](design/workflow-state-machine.md) |
| Harness enforcement | Done for MVP, keep hardening | Do not rely on the LLM to obey discipline; enforce boundaries with contracts, runtime guards, and consistency tests. | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

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
| Real-case validation | Done | `ws-20260619T051122Z-P837` reached `COMPLETED` after two semantic repair loops: one contract/design repair and one implementation/evidence repair. |

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
| Projection read model | Done for CLI/API/UI MVP, keep polishing | `trace`, `status`, overview, and the operator delivery view answer "where is it, why stopped, what next, and how do I deliver" from ledger facts. |
| Real-case boundary loop | Done for P0 closure, keep as discipline | The closure real case produced two verifier rejections and the workflow routed both through semantic repair without manual rerun. |

## P1: Long-Running Reliability

P1 work keeps long agent runs recoverable and affordable.

| Item | Status | Purpose | Design |
|---|---|---|---|
| Execution / AgentTask boundary | Partial, worker MVP added | Separate node instance, concrete execution, and agent dispatch facts. | [execution-instance-model.md](design/execution-instance-model.md) |
| Recovery classification | MVP done, keep adding provider evidence | Distinguish runtime retry, semantic repair, rejected acceptance, and external quota/session failures. | [failure-recovery-and-calibration.md](design/failure-recovery-and-calibration.md) |
| Context budget governance | MVP done, keep measuring | Prevent context packages from growing until the system becomes unusable. | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) |
| Ledger-first memory loop | Phase A MVP done, real-case validation pending | Reuse ledger/artifact evidence as Claude Working Memory and Codex Review Evidence Brief before adding RAG. | [ledger-first-memory-loop.zh-CN.md](design/ledger-first-memory-loop.zh-CN.md) |
| Artifact contract stability | Done for current MVP boundary, keep testing | Route on typed sidecars and compiled stage outcomes, not natural-language guesses. | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) |
| Boundary drift prevention | Partial | Prevent workflow definitions, contract registries, handler registries, and projections from drifting apart. | [boundary-drift-review.zh-CN.md](design/boundary-drift-review.zh-CN.md) |

### P1 Artifact / Context Checklist

| Area | Status | Evidence |
|---|---|---|
| Typed artifact contracts | Done, keep testing | `ArtifactContractValidator`, `ContractRegistry`, and node output contracts validate Agent sidecars before routing. |
| Intermediate stage contracts | Done, keep testing | Design, plan, implementation, and Codex review nodes declare `agent_output_contract` in workflow JSON and prompts. |
| Decision routing | Done, keep testing | `ReviewDecisionHandler` consumes the latest `*.contract.json` sidecar only; Markdown and `dual-control` are not routing fallbacks. |
| Report projection | Done, keep testing | Final report summaries consume self-test and acceptance sidecars, not raw Agent Markdown or transcripts. |
| Context budgets | Done, keep testing | `ContextProfiles` defines profile budgets; `ContextBuildPackageHandler` enforces estimated token limits and degrades/drops sections. |
| Context package metadata | Done | Every context package now writes a JSON sidecar with profile, token budget, estimated tokens, and per-section degradation facts. |
| Ledger-first memory loop | Phase A MVP done, real-case validation pending | `EvidenceManifest` writes `memory/evidence-manifest.json`; Claude implementation context renders `Working Memory`; Codex review profiles render `Review Evidence Brief`; context metadata exposes evidence refs and evidence counts. |
| Prompt language policy | Done | Workstream `settings.json` stores `promptLanguage`; Agent prompts localize human-readable prose while preserving machine-readable contract keys, enums, and markers. |
| Projection | MVP done, keep testing | `workstream-metrics.json` includes tokenMetrics by run, node, agent, and total. `runs` and `trace` display per-step token source as `acpx_stream` or `artifact_estimate`; API overview and Operator Console consume the same ledger projection. |
| Tests | Done | `ContextBuildPackageHandlerTest` covers budget enforcement, degradation, and metadata sidecar creation. |
| Real-case validation | Done for closure case | The 2026-06-19 private business-logic run completed with bounded prompt/context behavior and no context-window recovery. |

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
| Operator Console / Management Diagnostics | MVP implemented, keep polishing | The default task view answers where it is, why it stopped, and what to do next; diagnostics preserve RCA, CLI commands, raw facts, and token/cost. The default entry is `/`, `/console.html` remains compatible, and the old management panel moved to `/mgt/index.html`. The workstream list discovers the default `<repo-name>-agent-runs` pool so CLI/autorun-created tasks are visible from the repository root. | [operator-observability.md](design/operator-observability.md), [operator-console-implementation-plan.zh-CN.md](design/operator-console-implementation-plan.zh-CN.md) |
| Delivery view | Partial | Show delivery merge readiness and result in the completed workstream view. | [delivery-merge.md](design/delivery-merge.md) |
| Public benchmark | Partial | Provide repeatable public evidence without private project files. | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) |
| Open-source docs cleanup | Partial | Keep docs readable, current, and not tied to internal run history. | [release-checklist.md](release-checklist.md) |

## Current Next Step

P0 lifecycle closure is complete for the MVP boundary. Do not continue
open-ended private P0 real-case exploration. P1 closure must finish before more
P2 product-experience work.

Recommended order:

1. Finish P1 memory-loop validation first: run a simple acpx case to verify package generation, evidence manifest creation, and token metadata, then run a medium cancer-clinic case to compare repeated reads and token breakdown.
2. If memory-loop validation passes, continue P2 with delivery view, public benchmark hardening, and Operator Console simplification.
3. Keep open-source docs cleanup incremental and current; do not move private run history back into public docs.
4. Use private real cases only as targeted regression checks or P1/P2 validation, not as an open-ended P0 discovery loop.
2. Keep open-source docs cleanup incremental and current; do not reintroduce
   private run history into public-facing docs.
3. Use private real cases only as targeted regression checks, not as P0
   discovery loops.
