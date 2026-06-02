# Real-case Benchmark Report

## Metadata

| 字段 | 值 |
|------|----|
| Date | 2026-06-03 |
| Case | `cancer-clinic-feature-slice` |
| Repo | `/Users/kriswang/work/projects/ai/cancer-clinic` |
| Workflow variant | `p6` / `agent-lifecycle-p6@3` |
| Workstream | `ws-20260602T211852Z-WCBQ` |
| Effective workdir | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260602T211852Z-WCBQ` |
| Ledger | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260602T211852Z-WCBQ/.agent/workstreams/ws-20260602T211852Z-WCBQ` |
| Started at | 2026-06-02T21:18:51Z |
| Finished at | 2026-06-02T21:28:04Z |
| Duration | about 9m 13s |
| Final status | `WAITING_FOR_RECOVERY` |

## Result Summary

| 字段 | 值 |
|------|----|
| Completed | no |
| Agent run count | 5 observed before recovery |
| Gate rounds | 2 auto-approved gates before recovery |
| Codex acceptance decisions | not reached |
| Reject count | 0 acceptance rejects; design review requested revision twice |
| Repair loop count | 1 design revision loop |
| Failure classifications | not reached |
| Original repo clean | yes |
| Scope matched TaskContract | no; TaskContract artifact was under-specified |

## Artifact Checklist

| Artifact | Status | Notes |
|----------|--------|-------|
| TaskContract | present but incomplete | `scope.included`, `scope.excluded`, `constraints`, `non_goals`, `risk_areas`, `test_requirements` were empty |
| Final report | missing | workflow stopped before implementation |
| Handoff JSON | missing | workflow stopped before stage handoff |
| Next-stage prompt | missing | workflow stopped before stage handoff |
| Delivery PR body | missing | workflow stopped before delivery |
| Delivery commit message | missing | workflow stopped before delivery |
| Git evidence | no business diff | implementation was not reached |
| Review packets | present for first two gates | requirement and contract gates auto-approved |
| Failure summary | missing | `recovery/current-failure.md` was not generated for this decision recovery |
| Calibration candidate | not applicable | failure classification stage was not reached |

## Git Evidence

Original repo:

```text
<clean>
```

Worktree:

```text
<clean>
```

## Acceptance / Repair Timeline

| Round | Decision | Failure classification | Repair action | Notes |
|-------|----------|------------------------|---------------|-------|
| 1 | design review `NEEDS_REVISION` | n/a | Claude design revision | Codex found empty TaskContract fields and a Vue `v-if/v-else` design issue |
| 2 | design review `NEEDS_REVISION` | n/a | blocked by revision limit | Vue design issue was fixed, but actual `task-contract.json` still had empty scope/constraints/non-goals/test fields |

## Human Review Observations

- Evidence quality: strong enough to identify a real workflow weakness before implementation.
- Scope control: good; no business code was changed.
- Over-implementation risk: low because workflow stopped before implementation.
- Missing tests / checks: no final implementation verification because design/contract did not pass.
- UI / operator pain points: recovery summary was missing, so the user must inspect node JSON and Codex output manually to understand why recovery happened.

## Root Cause

The benchmark exposed a TaskContract materialization gap.

Claude/design produced detailed scope, non-goals, constraints, risks, and test strategy in Markdown, but the actual TaskContract artifact used by downstream nodes remained sparse:

```json
"scope" : {
  "included" : [ ],
  "excluded" : [ ]
},
"test_requirements" : [ ],
"constraints" : [ ],
"non_goals" : [ ],
"risk_areas" : [ ]
```

Codex correctly blocked implementation because future acceptance would compare diff/evidence against an incomplete contract.

## Verdict

```text
FAIL
```

This is a useful failure. It shows the p6 workflow correctly prevented implementation when the contract artifact was not good enough, but it also shows the orchestrator needs a stronger contract revision/materialization path.

## Follow-up

- Strengthen `contract.compile` so it materializes `scope.included`, `scope.excluded`, `constraints`, `non_goals`, `risk_areas`, and `test_requirements`.
- When Codex contract/design review rejects due to TaskContract fields, route back to contract revision/materialization instead of only design revision.
- Generate `recovery/current-failure.md` for revision-limit recovery on decision nodes.
- Re-run `cancer-clinic-feature-slice` after the contract materialization fix.
