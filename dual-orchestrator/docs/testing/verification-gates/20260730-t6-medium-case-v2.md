# Verification Gate: T6 Cancer-Clinic Medium Case v2

Status: `READY_AFTER_CHECKPOINT / NOT AUTHORIZED`

- Target: Prove one full workflow can close the pinned cancer-clinic
  plan-scoped recent-completed-cycle gap while preserving strict typed results,
  the seven-file change boundary, target quality, and business safety.
- Source of truth: `workflow/current.json`; every `runs/*/run.json`, including
  `agentResultAcquisition.correctionReason`, `correctionDisposition`, process
  exit and permission evidence; strict result sidecars; per-stage typed review
  decisions; `task-contract.json`; `change-scope-evaluation.json`; acceptance
  contract; backend pytest and miniprogram build logs;
  `metrics/workstream-metrics.json`; pinned target Git HEAD and diff.
- Trigger precondition: A clean Dual checkpoint containing the typed correction
  disposition projection; pinned target
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927` and clean status; Python 3.13.1
  ephemeral environment with requirements SHA-256
  `412637f581208f7a64fac57a705c1c4a2ef868d954c16c7924bddb1a4cc1a391` plus
  `python-multipart==0.0.32`; healthy `cancer_clinic_test` PostgreSQL; backend
  191-test baseline PASS; miniprogram build PASS; fresh Dual reactor PASS;
  Claude and Codex probes PASS; new explicit provider authorization.
- How precondition is checked before run: Record the clean Dual checkpoint SHA
  and target SHA/status; run deterministic correction-disposition tests; record
  pip freeze, database identity/health, backend pytest count, frontend build,
  fresh package evidence, runner kind, and both provider probes before
  workstream creation. Create no workstream on any mismatch.
- PASS: Workflow `COMPLETED`; acceptance `ACCEPT` or non-regressive
  `ACCEPT_WITH_NOTES`; strict results valid; change-scope PASS; backend full
  pytest and miniprogram build PASS after change; no prohibited business
  behavior or wording; at most one semantic revision in each stage; at most one
  bounded correction in each Agent run; every correction disposition agrees
  with process/provider/completion evidence; all usage reconciled; at most 15
  Agent runs and 35 minutes elapsed.
- FAIL: Any non-zero Agent process exit; permission denial; typed provider
  failure; failed correction; more than one correction in an Agent run; more
  than one semantic revision in any stage; workflow, acceptance, strict
  contract, target tests/build, seven-file change boundary, business behavior,
  usage reconciliation, Agent-run cap, or elapsed-time cap failure.
- INVALID: Target or Dual SHA, dependency pin, database, baseline, build
  freshness, provider/model, runner, probe, usage source, or test environment
  is absent or not comparable. `INVALID` is not partial success.
- Stop-loss: Stop immediately on any non-zero Agent process, permission denial,
  typed provider failure, failed correction, second correction in the same
  Agent run, second semantic revision in the same stage, more than 15 Agent
  runs, more than 35 minutes elapsed, out-of-scope change, prohibited medical
  conclusion or state mutation, missing usage, or target worktree
  contamination. Do not retry, resume, correct a non-zero process, tune
  prompts/models/timeouts/budgets, or reinterpret a skipped correction as
  budget exhaustion.
- Run artifact freshness: Require a fresh Maven package from the clean
  checkpoint SHA recorded before authorization; prove jar/source mtimes,
  `artifactFreshnessProven=true`, expected acpx runner kind, and
  `providerProbePathMatchesExpected=true` before workstream creation.

Correction ownership remains per Agent run. Semantic revision accounting is per
workflow stage. This gate does not change parser, prompt, correction, retry, or
workflow behavior.
