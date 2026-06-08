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
- Real public benchmark artifact shapes for `SELF_TEST_COMPLETE`,
  `ACCEPT_WITH_NOTES`, and `HANDOFF_READY`, sanitized from the public
  issue-flow sample run without private paths or bulky logs.

## Updated MVP Gap

The main lifecycle is functionally closed. As of 2026-06-08, the P1 mainline
closure items have first-pass implementations:

- Contract compiler quality closure: READY contracts now require executable
  scope, acceptance criteria, test requirements, and explicit boundaries.
  Open proposal questions are no longer a raw validator failure; they must be
  resolved through the requirement human gate and are recorded as a
  `contract-resolution` artifact before canonical TaskContract generation.
- Calibration candidate to template proposal: `dualctl calibration plan` now
  emits a reviewable `templateProposal` without applying it automatically.
- Handoff/context continuity: `handoff.json` and context packages preserve
  important decisions and recommended next-stage state for long-run resume.
- Contract failure diagnostics: strict artifact failures now produce
  `artifacts/contract-failures/<node_instance>.json` / `.md`, including
  category, source output, recommended owner, and recovery hint.
- Context state/evidence boundary: context packages now separate trusted
  harness facts, human decisions, agent proposed states, and pending
  contract/evidence gaps.
- Agent evidence read model: `AgentEvidenceIndex` centralizes validated
  sidecar discovery, chronological ordering, structured document parsing, and
  `evidence_state` access. Context packages, human review guidance, and failure
  classification now consume this shared read model instead of walking
  `runs/**/.contract.json` independently.
- Contract recovery visibility: when a node fails because of
  `AGENT_OUTPUT_CONTRACT_VIOLATION`, `recovery/current-failure.md` now
  highlights the contract diagnostic JSON/Markdown refs and parse error details
  instead of burying them in generic output refs.
- Recovery assessment read model: CLI `dualctl recovery show`, API
  `/recovery/current`, and operator overview now consume the same structured
  `RecoveryAssessment`. Contract failures surface classification, retry
  readiness, diagnostic refs, and the harness recovery hint consistently.
- Principle template seed: prompt rendering reads built-in and project
  `.agent/templates/*.md` principles; real failure classification can generate
  calibration candidates; `dualctl calibration plan` turns accepted candidates
  into a human-reviewable `templateProposal` without auto-mutating templates.
- Controlled promotion loop: `dualctl calibration promote` can explicitly
  promote an accepted `CONTRACT_TEMPLATE` apply plan into project-level
  `.agent/templates/contract-review-principles.md`. This closes the first
  knowledge loop while keeping built-in prompts, workflow definitions, context
  rules, and tests untouched.
- Provider metrics MVP: `metrics/workstream-metrics.json` now includes
  `providerMetrics` aggregated from `runs/*/run.json`, covering run count,
  completed/failed/prepared count, recoverable failures, total duration, and
  average duration per Claude/Codex provider. Token and cost metrics remain
  deferred until the adapter exposes raw usage data.

Release-quality MVP still needs:

1. A public, repeatable real-case benchmark run that does not depend on the
   private `cancer-clinic` project.
   - First sample/case now exists: `examples/issue-flow-app/` plus
     `cases/public-issue-flow-review-state.md`.
   - 2026-06-06: latest real `acpx` run completed with `PASS`: 6 human gates,
     10 Agent runs, 30 timeline nodes, final `ACCEPT`, all key artifacts
     present, original repo clean. The same run verified that
     `contract-resolution` is visible in acceptance context, human review
     packets, PR body, and the final report.
   - 2026-06-06: follow-up real `acpx` run completed with `PASS` after adding
     contract failure diagnostics and context `State & Evidence Boundary`.
     The healthy-path run produced no contract failure diagnostics and showed
     the boundary section in generated context packages and downstream prompts.
   - 2026-06-08: the public benchmark path now has a single command:
     `./scripts/run-public-benchmark.sh`. It prepares the disposable sample repo
     and runs the public issue-flow case through `run-real-case.sh`.
   - 2026-06-08: the one-command public benchmark was rerun with real `acpx` and
     completed as `PASS-WARN`: 6 human gates, 10 Agent runs, 31 timeline nodes,
     final `ACCEPT_WITH_NOTES`, all key artifacts present, original repo clean.
2. A short install-to-first-run path that does not reference private benchmark
   cases.
   - 2026-06-08: `README.md` and `docs/quickstart.md` now lead with the public
     benchmark command for the first real lifecycle validation path.
3. Continued regression fixtures for real structured artifacts whenever a
   real-case run exposes a new stable artifact shape.
   - 2026-06-08: added sanitized public issue-flow fixtures for developer
     self-test, Codex acceptance, and Claude stage handoff. These validate the
     same strict artifact contract path used by live runs without introducing
     fake `acpx`.
4. Operator timeline consistency across CLI/API/UI.
   - 2026-06-08: new timeline projections are written in chronological order,
     and the server normalizes existing `timeline.json` files before serving
     `/timeline` and `/overview`.
   - 2026-06-08: `dualctl trace` and `dualctl timeline` now apply the same
     chronological normalization when reading existing ledger files directly.
