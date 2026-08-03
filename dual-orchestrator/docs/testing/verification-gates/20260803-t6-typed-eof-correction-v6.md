# Verification Gate: T6 Typed EOF Correction v6

Status: `CONSUMED / FAIL / STOP-LOSS ENFORCED`

- Target: Run one full pinned cancer-clinic workflow from a clean Dual SHA
  containing `3786a3ca`. The primary target is full workflow completion with
  strict typed results and no execution-fact regression. The secondary target
  is typed EOF correction: if `UNEXPECTED_END_OF_INPUT` occurs, the correction
  prompt must carry that typed diagnostic and the single correction must pass;
  if EOF does not occur, record `NOT_TRIGGERED` rather than claiming the EOF
  behavior `PROVEN`.
- Source of truth: The committed Dual and pinned target Git SHAs; generated
  `preflight.json`; `workflow/current.json`; every `runs/*/run.json`, strict
  output sidecar, correction prompt and correction output; ACP stream/session
  metadata when correction occurs; task contract, typed lineage and change
  scope evaluation; final acceptance; target Git diff; backend pytest and
  miniprogram build evidence; generated report and summary.
- Trigger precondition: This gate and the active-plan update are committed on a
  clean Dual SHA containing `3786a3ca`; full reactor is `1147/0`; targeted
  parser/acquirer tests are `12/0` and `16/0`; target is clean pinned SHA
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`; the pinned Python environment and
  isolated `cancer_clinic_test` PostgreSQL are healthy; backend baseline is
  `191 passed`; miniprogram build passes; a fresh Dual package is built; Claude
  and Codex minimal probes pass through their actual acpx runner paths; and the
  user authorization for exactly one v6 sample is recorded.
- How precondition is checked before run: Verify Git SHAs and cleanliness; run
  the targeted parser/acquirer tests and full reactor; verify the target and
  task hashes; verify Python dependency pins and PostgreSQL database identity;
  run backend pytest and miniprogram build; invoke `run-real-case.sh` without
  `--skip-build` so it builds a fresh jar and runs the matching acpx provider
  probe before creating a workstream; separately run a minimal Codex acpx probe.
  Do not create a workstream if any precondition fails.
- PASS: The full workflow reaches `COMPLETED` with final `ACCEPT` or
  non-regressive `ACCEPT_WITH_NOTES`; every Agent result accepted by workflow is
  strict JSON with valid contract and lineage; change scope, backend verification
  and miniprogram build pass; no run remains `prepared` or has contradictory
  process/attempt/outcome facts; bounded correction is at most one per Agent run;
  semantic revision is at most one per stage; Agent runs are at most 15; elapsed
  time is at most 35 minutes. EOF sub-verdict is `PROVEN` only when an observed
  `UNEXPECTED_END_OF_INPUT` produces a diagnostic-bearing correction prompt and
  that correction parses and validates; otherwise it is `NOT_TRIGGERED`.
- FAIL: The workflow does not complete or final acceptance rejects; any strict
  contract, lineage, change-scope, backend, frontend or business-safety check
  fails; any non-zero Agent process, permission denial or typed provider failure
  occurs after workstream creation; an EOF diagnostic is absent or wrong when
  Jackson reports EOF; raw malformed output is copied into the correction prompt;
  a correction fails, a third prompt/second correction occurs, or semantic
  revision exceeds one per stage; any execution fact remains `prepared` or
  contradicts its typed outcome; Agent runs exceed 15 or elapsed time exceeds
  35 minutes.
- INVALID: Dual/target SHA, cleanliness, dependency pins, database identity,
  backend/frontend baseline, fresh artifact proof, runner/provider/model identity,
  either minimal probe, required run fields or report freshness are missing or
  not comparable. No observed EOF means only the EOF sub-verdict is
  `NOT_TRIGGERED`; it does not invalidate an otherwise complete full workflow.
- Stop-loss: Stop on the first non-zero process, permission denial, typed provider
  failure, failed correction, second correction, second semantic revision in one
  stage, execution-fact contradiction, target contamination, out-of-scope change,
  prohibited medical behavior, more than 15 Agent runs, or more than 35 minutes.
  Do not retry, resume, tune prompts/models/timeouts/budgets or launch a second v6
  sample.
- Run artifact freshness: `run-real-case.sh` must Maven-package from the clean
  authorized SHA after the latest JVM source mtime. Generated `preflight.json`
  must report `buildMode=maven_package`, `artifactFreshnessProven=true`, matching
  expected/provider-probe runner kind and
  `providerProbePathMatchesExpected=true`; otherwise the run is `INVALID`.

This gate consumes exactly one user-authorized real-case sample. Deterministic
evidence proves the typed EOF diagnostic boundary is `ENFORCED`; only an actual
EOF followed by a successful correction can promote that model-facing behavior
to `PROVEN`.

## Result

- Verdict: `FAIL / STOP-LOSS ENFORCED`.
- EOF sub-verdict: `NOT_TRIGGERED`; no completed Agent run attempted bounded
  correction, so this sample neither proves nor disproves model-facing EOF
  correction.
- Gate SHA: `d7de368f8cbcc97f98e7379e07b7234747532d38`.
- Target SHA: `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`.
- Workstream: `ws-20260803T092510Z-UAXP`.
- Report: `docs/testing/real-case-reports/20260803T092130Z/report.md`.
- Preflight: backend `191/0`, miniprogram build PASS, isolated PostgreSQL PASS,
  fresh reactor `1147/0`, Claude probe PASS, Codex probe PASS.
- Stop-loss: after two completed Claude proposals and two completed Codex
  reviews, workflow started a second Claude semantic revision in the intake
  stage. The run was interrupted immediately; no retry, resume, prompt/model
  tuning or second sample was performed.
- First revision cause: the persisted typed proposal contained the literal app
  route `/pages/treatment-record/index`, but the Codex review prompt projected it
  as `[outside-workspace path removed]`. Codex therefore rejected a projection
  defect rather than a missing contract value.
- Second revision cause: the revised proposal restored the route but omitted the
  self-test report requirement that the original proposal had already preserved.
  Codex correctly rejected that semantic loss.
- Execution facts: the first four Agent runs exited zero, produced strict valid
  contracts and `SUCCESS`, with no correction. The fifth run was stopped by the
  gate after it had started; provider subprocesses were explicitly terminated.
  It had durably reached `executionAttempt.status=PROCESS_EXITED`, but the outer
  runner termination prevented finalizer/reconciler completion. Because this gate
  forbids resume, `workflow/current.json` and the fifth run remain non-terminal
  `RUNNING`. This is the documented abandoned-run boundary, not a closed ledger.

This authorization is consumed. Do not retry or resume this workstream and do
not run a second v6 sample. The next change, if authorized, must address the two
proven preservation boundaries without weakening strict parsing or review:
route-like literal projection and revision contract preservation. Separately,
assess whether the stop-loss runner should invoke the existing reconciler before
exit; do not introduce a new recovery framework.
