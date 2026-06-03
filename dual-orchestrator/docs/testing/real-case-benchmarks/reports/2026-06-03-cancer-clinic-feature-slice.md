# Real-case Benchmark Report

## Metadata

| 字段 | 值 |
|------|----|
| Date | 2026-06-03 |
| Case | `cancer-clinic-feature-slice` |
| Repo | `/Users/kriswang/work/projects/ai/cancer-clinic` |
| Workflow variant | `p6` / `agent-lifecycle-p6@3` |
| Workstream | `ws-20260603T045849Z-RGEL` |
| Effective workdir | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260603T045849Z-RGEL` |
| Ledger | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260603T045849Z-RGEL/.agent/workstreams/ws-20260603T045849Z-RGEL` |
| Started at | 2026-06-03T04:58:48Z |
| Finished at | 2026-06-03T05:15:33Z |
| Duration | about 16m 45s |
| Final status | `COMPLETED` |

## Summary JSON

```text
dual-orchestrator/docs/testing/real-case-reports/20260603T045848Z/summary.json
```

## Result Summary

| 字段 | 值 |
|------|----|
| Completed | yes |
| Agent run count | 12 |
| Gate rounds | 6 |
| Timeline nodes | 35 |
| Iteration starts | 1 |
| Codex acceptance decisions | `ACCEPT_WITH_NOTES` |
| Reject count | 0 acceptance rejects |
| Repair loop count | 1 design revision loop |
| Failure classifications | `REVIEW_NOISE` as non-blocking acceptance notes |
| Original repo clean | yes |
| Scope matched TaskContract | yes; one changed file matched `scope.included` |

## Artifact Checklist

| Artifact | Status | Notes |
|----------|--------|-------|
| TaskContract | present and clean | Generated from `task_contract_proposal`, `source.createdBy=task_contract_proposal` |
| Final report | present | `reports/latest-final-report.md` |
| Handoff JSON | present | `artifacts/next-stage/handoff.json` |
| Next-stage prompt | present | `artifacts/next-stage/next-stage-prompt.md` |
| Delivery PR body | present | `delivery/pr-body.md` |
| Delivery commit message | present | `delivery/commit-message.txt` |
| Git evidence | present | `artifacts/git-evidence/*` |
| Review packets | present | 6 human gates auto-approved by benchmark script |
| Failure summary | not applicable | Workflow completed; no blocking recovery |
| Calibration candidate | not applicable | No blocking reject classification |

## Git Evidence

Original repo:

```text
<clean>
```

Worktree:

```text
 M miniprogram/src/pages/treatment-record/index.vue
```

Diff stat:

```text
miniprogram/src/pages/treatment-record/index.vue | 81 +++++++++++++++++++-----
1 file changed, 64 insertions(+), 17 deletions(-)
```

## Acceptance / Repair Timeline

| Round | Decision | Failure classification | Repair action | Notes |
|-------|----------|------------------------|---------------|-------|
| 1 | design review `NEEDS_REVISION` | n/a | Claude design revision | Codex found a naming conflict risk in the initial design |
| 2 | design review `READY_FOR_ARCH_REVIEW` | n/a | proceed | Revised design passed |
| 3 | plan review `READY_TO_IMPLEMENT` | n/a | proceed | Human plan gate auto-approved |
| 4 | acceptance `ACCEPT_WITH_NOTES` | `REVIEW_NOISE` as non-blocking notes | no repair | Manual WeChat DevTools/device verification remains recommended |

## Human Review Observations

- Evidence quality: strong. TaskContract, Git evidence, self-test, acceptance review, final report, and handoff all exist.
- Scope control: good. Only `miniprogram/src/pages/treatment-record/index.vue` changed, matching TaskContract scope.
- Over-implementation risk: low. No backend, schema, event-chain, AI summary, or multi-page expansion.
- Missing tests / checks: manual WeChat DevTools/device verification remains outside automated benchmark.
- Operator pain points: final report previously labeled `ACCEPT_WITH_NOTES` classification as “Failure Classification”; this was fixed after the run so future reports render it as acceptance notes.

## Root Cause Follow-up From Previous Failed Run

The earlier 2026-06-02 run stopped at recovery because the canonical TaskContract was under-specified. The fix changed the contract flow from Markdown heuristic extraction to artifact-driven proposal:

- Claude outputs `<dual-artifact type="task_contract_proposal" version="1">`.
- `contract.compile` validates the proposal mechanically.
- Canonical `task-contract.json` is generated from the proposal.
- Invalid proposals fail strictly instead of falling back to Markdown guessing.

This rerun confirms the fix in a real business repository.

## Verdict

```text
PASS-WARN
```

`PASS-WARN` rather than pure `PASS` because Codex accepted with notes: runtime visual behavior still needs WeChat DevTools/device verification before business delivery.

## Follow-up

- Keep `cancer-clinic-feature-slice` in the recurring benchmark set.
- Use `summary.json` for future trend comparison: completion rate, gate count, agent run count, revision count, acceptance decisions, and artifact presence.
- Add 1-2 more medium real cases before investing further in multi-role review or complex policy machinery.
