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

1. Codex acceptance can identify missing runtime/build evidence, but the summary
   should keep surfacing that as verification confidence instead of hiding it
   behind a generic `PASS-WARN`.
2. Context package and handoff are sufficient for this bounded case, but longer
   lifecycle runs still need better evidence retention around decisions, changed
   files, self-test results, and unresolved risks.
3. Repair loops work for at least one verifier rejection, but the benchmark set
   should continue tracking repair loop count and final acceptance confidence so
   regressions are visible.

## 2026-06-05 Evidence Contract Update

The self-test evidence gap has a first implementation pass:

- Claude self-test prompts now ask for `Checks Run`, `Checks Skipped`,
  `Evidence`, `Missing Evidence`, repository hygiene, and residual risk.
- Codex acceptance prompts now ask for explicit verification adequacy, missing
  evidence, and acceptance notes.
- Agent output contract sidecars extract structured checks, evidence, missing
  evidence, and acceptance notes.
- Final reports render a `Verification Evidence` section so reviewers can see
  what was tested and what remains unverified without digging through every raw
  Agent output.

This does not force a script-level test command. Claude still chooses the right
validation method, Codex judges whether the evidence is enough, and the
orchestrator preserves the evidence for review.

## Next Mainline Step

The next useful implementation step is to rerun a bounded internal real case and
verify that the new structured evidence improves the final report and human
review packet without making the Agent behavior brittle.

## 2026-06-06 Semantic Artifact Boundary

The artifact boundary now has a first semantic consistency layer:

- Structured agent artifacts may declare `lifecycle_status`.
- Runtime derives canonical status from `decision` and rejects unknown or
  conflicting declared status.
- Contract sidecars include `lifecycle_status` and `evidence_state`.
- Final acceptance `ACCEPT` / `ACCEPT_WITH_NOTES` cannot carry blocking
  evidence, `must_fix`, or material `missing_evidence`.
- Final acceptance `REJECT` must include blocking evidence, missing evidence,
  must-fix items, or a failure reason.

This keeps the harness aligned with the paper's central point: the model may
judge the work, but the surrounding system validates, records, and routes
explicit state and evidence.

The first public, non-domain fixture regression set now covers:

- `ACCEPT_WITH_NOTES` with non-blocking notes.
- `REJECT` with blocking missing evidence.
- `SELF_TEST_INCOMPLETE` with insufficient developer evidence.

## Updated MVP Gap

The main lifecycle is functionally closed. As of 2026-06-06, the remaining P1
mainline closure items have first-pass implementations:

- Contract compiler quality closure: READY contracts now require executable
  scope, acceptance criteria, test requirements, and explicit boundaries.
- Calibration candidate to template proposal: `dualctl calibration plan` now
  emits a reviewable `templateProposal` without applying it automatically.
- Handoff/context continuity: `handoff.json` and context packages preserve
  important decisions and recommended next-stage state for long-run resume.

Release-quality MVP still needs:

1. A public, repeatable real-case benchmark run that does not depend on the
   private `cancer-clinic` project.
   - First sample/case now exists: `examples/issue-flow-app/` plus
     `cases/public-issue-flow-review-state.md`.
   - 2026-06-06: first real `acpx` run completed with `PASS-WARN`: 6 human
     gates, 10 Agent runs, 31 timeline nodes, final `ACCEPT_WITH_NOTES`, all
     key artifacts present, original repo clean.
2. A short install-to-first-run path that does not reference private benchmark
   cases.
3. Continued regression fixtures for real structured artifacts whenever a
   real-case run exposes a new stable artifact shape.
