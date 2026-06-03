# Real-case Benchmark Report

## Metadata

| 字段 | 值 |
|------|----|
| Date | 2026-06-03 |
| Case | `cancer-clinic-rejection-prone` |
| Repo | `/Users/kriswang/work/projects/ai/cancer-clinic` |
| Workflow variant | `p6` / `agent-lifecycle-p6@3` |
| Workstream | `ws-20260603T065831Z-R255` |
| Effective workdir | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260603T065831Z-R255` |
| Ledger | `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260603T065831Z-R255/.agent/workstreams/ws-20260603T065831Z-R255` |
| Started at | 2026-06-03T06:58:30Z |
| Finished at | 2026-06-03T07:30:35Z |
| Duration | about 32m 05s |
| Final status | `COMPLETED` |

## Summary JSON

```text
dual-orchestrator/docs/testing/real-case-reports/20260603T065830Z/summary.json
```

## Result Summary

| 字段 | 值 |
|------|----|
| Completed | yes |
| Agent run count | 10 |
| Gate rounds | 5 human gates auto-approved |
| Timeline nodes | 31 |
| Iterations | 1 |
| Codex acceptance decisions | `ACCEPT_WITH_NOTES` |
| Reject count | 0 acceptance rejects |
| Repair loop count | 0 implementation repair loops |
| Recovery count | 1 contract.compile retry after orchestration bug fix |
| Failure classifications | `REVIEW_NOISE` as non-blocking acceptance notes |
| Original repo clean | yes |
| Scope matched TaskContract | yes; one changed file matched `scope.included` |

## Artifact Checklist

| Artifact | Status | Notes |
|----------|--------|-------|
| TaskContract | present and clean | Generated from trusted Claude `task_contract_proposal` |
| Final report | present | `reports/latest-final-report.md` |
| Handoff JSON | present | `artifacts/next-stage/handoff.json` |
| Next-stage prompt | present | `artifacts/next-stage/next-stage-prompt.md` |
| Delivery PR body | present | `delivery/pr-body.md` |
| Delivery commit message | present | `delivery/commit-message.txt` |
| Git evidence | present | `artifacts/git-evidence/*` |
| Review packets | present | 5 human gates auto-approved by autorun |
| Failure summary | present | Initial contract.compile recovery recorded |
| Calibration candidate | not applicable | No blocking reject classification |

## Git Evidence

Original repo:

```text
<clean>
```

Worktree:

```text
 M miniprogram/src/pages/retro/index.vue
```

Diff stat:

```text
miniprogram/src/pages/retro/index.vue | 49 ++++++++++++++++++++++++++++++++++-
1 file changed, 48 insertions(+), 1 deletion(-)
```

## Acceptance / Recovery Timeline

| Round | Decision | Classification | Action | Notes |
|-------|----------|----------------|--------|-------|
| 1 | contract.compile failed | `TASK_CONTRACT_PROPOSAL_PARSE_FAILED` | fixed orchestrator and retried | Compiler was reading a malformed proposal quote from Codex review output |
| 2 | contract review approved | n/a | proceed | Canonical TaskContract generated from trusted Claude proposal |
| 3 | design review passed | n/a | proceed | Human design gate auto-approved |
| 4 | plan review passed | n/a | proceed | Human plan gate auto-approved |
| 5 | acceptance `ACCEPT_WITH_NOTES` | `REVIEW_NOISE` | no repair | Build passed; Weixin runtime tap/navigation not exercised |

## Human Review Observations

- Evidence quality: good. TaskContract, Git evidence, self-test, independent Codex verification, final report, handoff, and delivery artifacts all exist.
- Scope control: good. Only `miniprogram/src/pages/retro/index.vue` changed, matching TaskContract scope.
- Product fit: good. The implementation stayed as a lightweight post-save guide and did not become a notification system or multi-step wizard.
- Verification: `npm run build:mp-weixin` passed in Codex acceptance. Actual Weixin DevTools tap/navigation remains manual.
- Main bug found: `contract.compile` must treat Claude proposal output as trusted source and not parse reviewer Markdown quotes as proposal artifacts.

## Verdict

```text
PASS-WARN
```

`PASS-WARN` rather than pure `PASS` because the workflow initially exposed and required fixing an orchestration bug, and Codex accepted with notes due to missing Weixin DevTools runtime verification.

## Follow-up

- Keep this case in the recurring benchmark set because it caught a real contract-source bug.
- Consider adding one future case that reliably exercises `REJECT -> failure classification -> repair`, but do not over-invest in deterministic fake rejection yet.
- If npm network resolution appears again with `npx`, prefer local project scripts such as `npm run build:mp-weixin` during verifier prompts.
