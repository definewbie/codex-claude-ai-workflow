# Real-case Benchmark Report

## Metadata

| 字段 | 值 |
|------|----|
| Date | 2026-06-03 |
| Case | `cancer-clinic-state-model-complex` |
| Repo | `/Users/kriswang/work/projects/ai/cancer-clinic` |
| Workflow variant | `p6` / `agent-lifecycle-p6@3` |
| Workstream | `ws-20260603T080441Z-NDK2` |
| Effective workdir | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260603T080441Z-NDK2` |
| Ledger | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260603T080441Z-NDK2/.agent/workstreams/ws-20260603T080441Z-NDK2` |
| Started at | 2026-06-03T08:04:41Z |
| Finished at | 2026-06-03T08:24:27Z |
| Duration | about 19m 46s |
| Final status | `COMPLETED` |

## Summary JSON

```text
dual-orchestrator/docs/testing/real-case-reports/20260603T080441Z/summary.json
```

## Result Summary

| 字段 | 值 |
|------|----|
| Completed | yes |
| Agent run count | 10 |
| Gate rounds | 6 human gates auto-approved |
| Timeline nodes | 31 |
| Iterations | 0 revision iterations |
| Codex acceptance decisions | `ACCEPT_WITH_NOTES` |
| Reject count | 0 acceptance rejects |
| Repair loop count | 0 implementation repair loops |
| Recovery count | 0 recovery gates |
| Failure classifications | `REVIEW_NOISE` for non-blocking acceptance notes |
| Original repo clean | yes |
| Scope matched TaskContract | yes; one changed file matched `scope.included` |

## Artifact Checklist

| Artifact | Status | Notes |
|----------|--------|-------|
| TaskContract | present and clean | Scope limited to `miniprogram/src/pages/record/index.vue` |
| Final report | present | `reports/latest-final-report.md` |
| Handoff JSON | present but weak | File exists, but extracted arrays are empty despite rich Markdown handoff |
| Next-stage prompt | present | `artifacts/next-stage/next-stage-prompt.md` |
| Delivery PR body | present | `delivery/pr-body.md` |
| Delivery commit message | present | `delivery/commit-message.txt` |
| Git evidence | present | `artifacts/git-evidence/*` |
| Review packets | present | 6 human gates auto-approved by autorun |
| Recovery summary | not used | No failed node in this run |

## Git Evidence

Original repo:

```text
<clean>
```

Worktree:

```text
 M miniprogram/src/pages/record/index.vue
```

Diff stat:

```text
miniprogram/src/pages/record/index.vue | 27 +++++++++++++++++++++++++++
1 file changed, 27 insertions(+)
```

## State-model Timeline

This run was used to validate `docs/design/workflow-state-machine.md`.

| Observation | Result | Notes |
|-------------|--------|-------|
| `start --run` reached first human gate | pass | `gate.human_requirement_review` opened with an open gate id |
| Human gates had explicit open gate ids | pass | 6 gates: requirement, contract, design, plan, acceptance, next-stage |
| Autorun could route gate actions to the next node | pass | Each approved gate resumed the workflow without manual command chaining |
| Active node remained inspectable during long Agent calls | pass | `status` showed `RUNNING`, active node definition, active node id, and running PID |
| PID guard semantics were visible | pass | Long-running `plan.codex_plan_review` and `implement.claude_self_test_report` stayed on the same active node |
| Recovery path was needed | not exercised | No `WAITING_FOR_RECOVERY` in this run |
| Revision iteration was needed | not exercised | No Codex rejection or human revise; `iteration_count=0` |
| Completion cleared active nodes | pass | Final status `COMPLETED`, active nodes `[]`, open gates `[]` |

## Acceptance Timeline

| Stage | Decision / Status | Notes |
|-------|-------------------|-------|
| Intake | ready for product review | Claude produced a bounded B4 contract proposal |
| Product review | approved | Codex agreed scope was bounded |
| Contract review | approved | Contract included one frontend file and explicit non-goals |
| Design review | approved | No architecture must-fix |
| Plan review | approved | Codex warned not to over-interpret grep exact counts |
| Implementation | completed | Single-file `record/index.vue` change |
| Self-test | completed with skipped build | Grep and diff evidence passed; build/typecheck skipped because `node_modules` was absent |
| Final acceptance | `ACCEPT_WITH_NOTES` | Accepted; build/runtime verification deferred |
| Next-stage gate | complete | Autorun completed workflow |

## Human Review Observations

- State model readability is good enough for operator use. During long Agent calls, `status` made it clear which node was active and which PID owned it.
- The run did not test recovery or revision loops. It confirms linear P6 state flow, gate routing, PID visibility, artifact generation, and completion semantics.
- Agent runtime still has latency and session-tail behavior: one Claude intake step wrote useful output before the acpx process returned later. The workflow remained inspectable, so this is tolerable for now.
- Handoff extraction is the weakest artifact. The Markdown handoff was useful, but `handoff.json` had empty `completedWork`, `changedFiles`, `openRisks`, and `verificationState`. This should be treated as a P1 mainline follow-up because it affects automatic next-stage continuity.
- Verification adequacy was acceptable for this UI-only case, but not ideal: build/typecheck and WeChat runtime UI checks were not run in the worktree environment.

## Verdict

```text
PASS-WARN
```

`PASS-WARN` because the lifecycle completed and state model behaved correctly, but two limitations remain: structured handoff extraction is weak, and build/runtime verification was deferred.

## Follow-up

- Improve structured handoff extraction or require a stricter handoff artifact block.
- Keep state-model validation in future real cases, especially when testing recovery and revision loops.
- Do not spend more quota on this B4 case unless the business change is going to be merged and needs runtime validation.
