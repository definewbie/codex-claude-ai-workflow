# Verification Gate: T6 Full Lifecycle v8

Status: `READY / AUTHORIZED AFTER PREFLIGHT`

- Target: Run exactly one full pinned cancer-clinic workflow from a clean Dual
  SHA containing `7f75509b`, `331df2f7`, and `39ef0299`. The primary verdict is
  full workflow completion with final `ACCEPT` or non-regressive
  `ACCEPT_WITH_NOTES`. The mandatory A sub-target is byte-faithful projection of
  the verified typed task-contract proposal into the persisted Codex review
  prompt.
- Source of truth: The committed Dual and target Git SHAs; task SHA-256;
  generated `preflight.json`; verified `TaskContractProposalReference` and
  resolved sidecar; complete context package; actual persisted Codex review
  prompt; every `runs/*/run.json`; workflow and node-instance state; process
  liveness; strict output sidecars and lineage; change-scope evaluation; final
  acceptance; target Git diff; backend and miniprogram evidence; generated
  report and summary. If triggered, permission handling is read from
  `AdapterSignal.PERMISSION_BLOCKED` and `executionOutcome.primaryReason`; stop
  loss session closure is read from the session-quiescence log and ledger.
- Trigger precondition: This gate and active-plan update are committed on a clean
  Dual SHA containing the listed checkpoints; full reactor and focused boundary
  tests pass; `run-real-case.test.sh` passes; target is clean at
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`; task SHA-256 is
  `776750d5ed1d044d1414da5351159238edeeba409f4a9e57584ae771a3518af3` and
  contains `/pages/treatment-record/index`; pinned Python dependencies and the
  isolated `cancer_clinic_test` PostgreSQL are healthy; backend baseline is
  `191 passed`; miniprogram build passes; a fresh Dual package is built; and
  Claude/Codex probes pass through their actual acpx runner paths.
- How precondition is checked before run: Verify Dual/target/task SHAs and Git
  cleanliness; run deterministic tests and runner behavior test; verify the task
  literal; verify dependency pins and database identity; run backend pytest and
  miniprogram build; Maven-package the clean Dual SHA; then run minimal Claude
  and Codex probes. Do not create a workstream if any check fails.
- PASS: The workflow reaches `COMPLETED` with final `ACCEPT` or non-regressive
  `ACCEPT_WITH_NOTES`; every accepted Agent result is strict JSON with valid
  contract and lineage; change scope, backend verification, miniprogram build
  and business-safety checks pass; and no execution fact is contradictory or
  left non-terminal. For A, the canonical wrapped proposal resolved from the
  verified reference appears exactly once in the persisted Codex review prompt,
  including `/pages/treatment-record/index`; untyped workspace-external paths
  remain redacted; no review rejection may be caused by projection mutation.
  Bounded correction is at most once per Agent run, semantic revision at most
  once per stage, Agent runs at most 15, and elapsed time at most 35 minutes.
- Conditional sub-verdicts: If acpx exits `5`, it must yield active
  `PERMISSION_BLOCKED`, primary reason `permission_blocked`, no correction, and
  stop the workflow; otherwise permission handling is `NOT_TRIGGERED`. If a
  real stop-loss occurs, all affected autorun/provider processes must be dead,
  all affected runs terminal, the active node `FAILED`, workflow
  `WAITING_FOR_RECOVERY`, and explicit session quiescence must succeed; otherwise
  it is `NOT_TRIGGERED`. EOF correction is `PROVEN` only when observed and
  successful, otherwise `NOT_TRIGGERED`. Conditional `NOT_TRIGGERED` does not
  invalidate an otherwise successful primary sample.
- FAIL: Any primary PASS condition fails; any strict contract, lineage,
  change-scope, backend, frontend or business-safety check fails; a projection
  mismatch, second semantic revision, failed/second correction, non-zero
  process, permission denial, typed provider failure, execution-fact
  contradiction, target contamination, out-of-scope change, more than 15 Agent
  runs, or more than 35 minutes occurs. If a conditional branch is triggered,
  its required outcome is mandatory; session-close or recovery failure is FAIL.
- INVALID: Dual/target/task identity or cleanliness, dependency pins, database
  identity, deterministic tests, fresh artifact proof, runner/provider/model
  identity, either probe, required persisted fields, or report freshness is
  missing or incomparable.
- Stop-loss: Stop on the first primary or triggered conditional FAIL. Use the
  runner interrupt closure once, inspect terminal ledger and session-quiescence
  evidence, and do not retry, resume, tune prompt/parser/model/timeouts/budgets,
  or launch a second v8 sample.
- Run artifact freshness: `run-real-case.sh` must Maven-package from the clean
  authorized SHA after the latest JVM source mtime. `preflight.json` must report
  `buildMode=maven_package`, `artifactFreshnessProven=true`, matching expected
  and provider-probe runner kind, and `providerProbePathMatchesExpected=true`.
  Otherwise the sample is `INVALID`.

This gate has one primary lifecycle verdict. Permission and stop-loss closure
remain conditional observations: deterministic tests prove them `ENFORCED`; a
normal run must not claim them `PROVEN` when they were not triggered.
