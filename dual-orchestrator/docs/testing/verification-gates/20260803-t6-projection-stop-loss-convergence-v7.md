# Verification Gate: T6 Projection and Stop-Loss Convergence v7

Status: `READY / NOT AUTHORIZED`

- Target: Run exactly one full pinned cancer-clinic workflow from a clean Dual
  SHA containing `7f75509b` and `331df2f7`. The primary target is full workflow
  completion with final `ACCEPT` or non-regressive `ACCEPT_WITH_NOTES`. The
  mandatory A sub-target is byte-faithful projection of the verified typed task
  contract proposal into the actual Codex review prompt. The conditional C
  sub-target is quiescent ledger closure if and only if a real stop-loss occurs.
- Source of truth: The committed Dual and target Git SHAs; task SHA-256;
  generated `preflight.json`; verified `TaskContractProposalReference` and its
  resolved sidecar; the complete context package; the actual persisted Codex
  review prompt; every `runs/*/run.json`; `workflow/current.json`; node instances;
  process liveness; strict output sidecars and lineage; change-scope evaluation;
  final acceptance; target Git diff; backend pytest and miniprogram build
  evidence; generated report and summary.
- Trigger precondition: This gate and the active-plan update are committed on a
  clean Dual SHA containing `7f75509b` and `331df2f7`; full reactor is `1158/0`;
  A and C cannot-bypass tests pass; `run-real-case.test.sh` passes; target is
  clean at `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`; task SHA-256 is
  `776750d5ed1d044d1414da5351159238edeeba409f4a9e57584ae771a3518af3` and
  contains `/pages/treatment-record/index`; the pinned Python environment and
  isolated `cancer_clinic_test` PostgreSQL are healthy; backend baseline is
  `191 passed`; miniprogram build passes; a fresh Dual package is built; Claude
  and Codex probes pass through the actual acpx runner paths; and the user has
  explicitly authorized exactly one v7 sample after seeing the preflight report.
- How precondition is checked before run: Verify Dual/target/task SHAs and Git
  cleanliness; run the focused A/C tests, runner behavior test and full reactor;
  verify the task literal before provider execution; verify Python dependency
  pins and PostgreSQL database identity; run backend pytest and miniprogram
  build; package a fresh Dual jar; after separate authorization, run minimal
  Claude and Codex probes. Do not create a workstream if any check fails.
- PASS: The workflow reaches `COMPLETED` with final `ACCEPT` or non-regressive
  `ACCEPT_WITH_NOTES`; every accepted Agent result is strict JSON with valid
  contract and lineage; change scope, backend verification, miniprogram build
  and business-safety checks pass; no execution fact is contradictory or left
  non-terminal. For A, the canonical wrapped proposal resolved from the verified
  reference appears exactly once in the persisted Codex review prompt, including
  the exact `/pages/treatment-record/index` literal; untyped workspace-external
  paths remain redacted; no review rejection may be caused by a projection
  mutation. Bounded correction remains at most one per Agent run, semantic
  revision at most one per stage, Agent runs at most 15, and elapsed time at most
  35 minutes. If stop-loss occurs, C passes only when autorun/provider processes
  are dead, all affected runs are terminal, the active node is `FAILED`, and the
  workflow is `WAITING_FOR_RECOVERY`; without stop-loss, C is `NOT_TRIGGERED`.
  EOF correction is independently `PROVEN` only if observed and successful,
  otherwise `NOT_TRIGGERED`.
- FAIL: The workflow does not complete or final acceptance rejects; the verified
  proposal and actual review prompt differ; the route literal is removed,
  rewritten or duplicated; an untrusted marker or untyped outside-workspace path
  bypasses sanitization; strict contract, lineage, change-scope, backend,
  frontend or business-safety checks fail; semantic revision exceeds one per
  stage; correction fails or exceeds one; an Agent process is non-zero or a typed
  provider/permission failure occurs. If stop-loss occurs, any live autorun or
  provider process, non-terminal run, active node not `FAILED`, workflow not
  `WAITING_FOR_RECOVERY`, fabricated occurrence/usage/correction, or recovery
  failure is also FAIL. More than 15 Agent runs or 35 minutes is FAIL.
- INVALID: Dual/target/task identity, cleanliness, dependency pins, database
  identity, deterministic tests, fresh artifact proof, runner/provider/model
  identity, either probe, required prompt/sidecar/run fields or report freshness
  is missing or incomparable. C or EOF not being triggered is only a sub-verdict
  of `NOT_TRIGGERED`; it does not invalidate an otherwise complete sample.
- Stop-loss: Stop on the first projection mismatch, second semantic revision in
  one stage, failed/second correction, non-zero process, permission denial, typed
  provider failure, execution-fact contradiction, target contamination,
  out-of-scope change, prohibited medical behavior, more than 15 Agent runs, or
  more than 35 minutes. Use the runner's interrupt closure once, then inspect the
  terminal ledger. Do not retry, resume, tune prompt/parser/model/timeouts/budgets
  or launch a second v7 sample.
- Run artifact freshness: `run-real-case.sh` must Maven-package from the clean
  authorized SHA after the latest JVM source mtime. Generated `preflight.json`
  must report `buildMode=maven_package`, `artifactFreshnessProven=true`, matching
  expected/provider-probe runner kind and
  `providerProbePathMatchesExpected=true`; otherwise the run is `INVALID`.

This gate does not combine independent conclusions. Full workflow completion is
the primary verdict. A must be exercised by this pinned task. C and typed EOF
correction are conditional sub-verdicts and remain `NOT_TRIGGERED` when their
runtime conditions do not occur. Deterministic evidence supports `ENFORCED`;
only the authorized real-case can support `PROVEN`.
