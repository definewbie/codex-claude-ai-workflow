# MVP Mainline Validation

This note records the current evidence for the dual-orchestrator MVP path: a real
requirement should move from intake to delivery artifacts with Claude, Codex,
human gates, recovery, and benchmark evidence connected.

## 2026-06-04 Real Case

- Case: `cancer-clinic-feature-slice`
- Variant: `p6`
- Target repo: `/Users/kriswang/work/projects/ai/cancer-clinic`
- Workstream: `ws-20260604T102338Z-X9E4`
- Effective workdir: `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260604T102338Z-X9E4`
- Final status: `COMPLETED`
- Benchmark grade: `PASS-WARN`
- Gate rounds: 6
- Agent runs: 15
- Timeline nodes: 43
- Acceptance decisions: `REJECT`, then `ACCEPT_WITH_NOTES`
- Final acceptance decision: `ACCEPT_WITH_NOTES`
- Repair loop count: 1
- Verification confidence: `MEDIUM`
- Original repo clean: `true`

## What This Validated

- `start --run` can enter the first human gate after real Claude/Codex intake.
- `autorun` can cross the requirement, contract, design, plan, acceptance, and
  next-stage human gates.
- A Codex final acceptance `REJECT` does not stop the lifecycle permanently. The
  workflow classified the failure, routed back to Claude implementation, reran
  self-test and acceptance, and completed with `ACCEPT_WITH_NOTES`.
- Delivery artifacts were generated:
  - `reports/latest-final-report.md`
  - `reports/workstream-summary.md`
  - `artifacts/next-stage/handoff.json`
  - `artifacts/next-stage/next-stage-prompt.md`
  - `delivery/pr-body.md`
  - `delivery/commit-message.txt`
- The original repository stayed clean; business changes only happened in the
  generated worktree.

## Issues Found And Fixed

The lifecycle itself completed, but `scripts/run-real-case.sh` failed while
writing the benchmark report:

- Empty artifact-missing lists triggered `missing[*]: unbound variable` under
  `set -u`.
- Acceptance reject counting double-counted the same contract because it matched
  both the top-level `decision` and nested `control.decision`.

Both issues were fixed in the benchmark script. The completed workstream was then
used to regenerate the ignored local report and the tracked benchmark index.

## Current Conclusion

The MVP mainline is now functionally closed for a real, bounded case: the system
can drive a complete lifecycle, survive one verifier rejection, produce delivery
artifacts, and leave the original repo clean.

The result is still `PASS-WARN`, not a final product-quality `PASS`, because
Codex accepted with notes about unavailable build/runtime verification.

## Remaining Mainline Gaps

These are still on the main path. They should be handled before spending more
time on UI, notification, or scheduler polish:

1. Self-test evidence is too dependent on what Claude chooses to report. The
   workflow should make it easier for Claude to state exactly which checks were
   run, which were skipped, and why.
2. Codex acceptance can identify missing runtime/build evidence, but the summary
   should keep surfacing that as verification confidence instead of hiding it
   behind a generic `PASS-WARN`.
3. Context package and handoff are sufficient for this bounded case, but longer
   lifecycle runs still need better evidence retention around decisions, changed
   files, self-test results, and unresolved risks.
4. Repair loops work for at least one verifier rejection, but the benchmark set
   should continue tracking repair loop count and final acceptance confidence so
   regressions are visible.

## Next Mainline Step

The next useful implementation step is to strengthen self-test and acceptance
evidence contracts without over-specifying how agents test. The orchestrator
should ask for structured evidence and expose confidence, while Claude and Codex
remain responsible for professional judgment.
