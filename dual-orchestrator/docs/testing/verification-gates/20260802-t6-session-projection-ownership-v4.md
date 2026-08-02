# Verification Gate: T6 Session and Projection Ownership v4

Status: `CONSUMED / FAIL / STOP-LOSS ENFORCED`

Execution result (2026-08-02): The one authorized sample created workstream
`ws-20260802T101230Z-DLRW`. All deterministic and provider preconditions passed,
and the first four ledger-completed Agent runs preserved the
session/process/outcome ownership invariants. The second
`intake.codex_product_review` nevertheless returned a second `NEEDS_REVISION`
in the `intake` stage. A later audit of acpx record
`84d2f90c-1315-4fa6-8f53-cc8838185b50` proved that the prepared third Claude
intake did execute a provider prompt and produce an assistant response before
the external stop completed, while its `run.json` remained `prepared` without
process, usage or outcome facts. This is an execution-fact ownership
contradiction, so the ownership checkpoint is not real-case proven. No retry,
resume or second sample is authorized. See
`docs/testing/real-case-reports/20260802T100848Z/report.md`.

- Target: Prove the clean `dc08cf2e` session/projection ownership checkpoint
  completes one full pinned cancer-clinic workflow. Claude and Codex named
  sessions must be available before each first prompt; transport-owned process
  facts, typed adapter signals, execution outcome, correction disposition,
  recovery projection and workflow state must remain consistent.
- Source of truth: The clean Dual Git SHA containing `dc08cf2e`; generated
  `preflight.json`; `workflow/current.json`; every `runs/*/run.json`, including
  each occurrence `processResult`, `activeAdapterSignals`, `executionAttempt`,
  `executionOutcome`, `agentResultAcquisition.correctionDisposition`, usage and
  provider identity; strict result sidecars; `task-contract.json`;
  `change-scope-evaluation.json`; acceptance contract; target Git status/diff;
  backend pytest and miniprogram build logs; and
  `metrics/workstream-metrics.json`.
- Trigger precondition: Dual HEAD is clean and contains `dc08cf2e`; deterministic
  session/recovery/finalizer tests and the full reactor pass; pinned target HEAD
  is `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927` and clean; the Python 3.13.1
  ephemeral environment contains requirements SHA-256
  `412637f581208f7a64fac57a705c1c4a2ef868d954c16c7924bddb1a4cc1a391` plus
  `python-multipart==0.0.32`; `cancer_clinic_test` PostgreSQL is healthy;
  backend baseline is `191 passed`; miniprogram build passes; a fresh Dual
  package is built; minimal Claude and Codex probes pass through their expected
  acpx paths; and this one-run provider authorization is recorded.
- How precondition is checked before run: Commit this gate/active-plan update to
  obtain a clean gate-only SHA; record Dual and target SHA/status; run the
  deterministic ownership tests and full reactor; record requirements hash and
  `pip freeze`; query database identity/health; run all backend tests and the
  miniprogram build; run one minimal no-tool probe through Claude and Codex;
  then invoke `run-real-case.sh` without `--skip-build`. Its preflight must prove
  artifact freshness, expected runner kind and provider-probe path before
  workstream creation.
- PASS: Workflow reaches `COMPLETED`; final acceptance is `ACCEPT` or a
  non-regressive `ACCEPT_WITH_NOTES`; strict results, typed lineage, seven-file
  change scope, backend pytest, miniprogram build, business safety and usage
  reconciliation pass; first Claude and Codex prompt paths have successful
  proactive session bootstrap; no active `SESSION_BOOTSTRAP_FAILED` remains;
  every occurrence has one transport-owned process result and typed signal set;
  outcome and terminal workflow state agree; outcome JSON contains
  `retryPolicy` and does not contain derived outcome-level `retrySafe`; provider
  identity is bound to the current agent and Codex never inherits a
  Claude-specific fallback; correction disposition agrees with process,
  provider and completion evidence; semantic revisions are at most one per
  stage; bounded correction is at most one per Agent run; Agent runs are at
  most 15 and elapsed workflow time is at most 35 minutes.
- FAIL: Any `SESSION_BOOTSTRAP_FAILED`; prompt starts before successful session
  bootstrap; process/signal/outcome/projection contradiction; outcome-level
  derived `retrySafe`; cross-agent provider identity fallback; non-zero Agent
  process; permission denial; typed provider failure; failed correction;
  correction of a non-zero or provider-failed occurrence; more than one
  correction in an Agent run; more than one semantic revision in a stage; or
  failure of workflow completion, acceptance, strict contracts, lineage,
  seven-file change scope, business behavior, target tests/build, usage
  reconciliation, Agent-run cap or elapsed-time cap.
- INVALID: Dual or target SHA/cleanliness, dependency pins, database identity,
  baseline tests, artifact freshness, provider/model/runner identity, either
  minimal probe, usage source or required generated ownership fields are absent
  or not comparable. Provider/configuration failure before Dual-owned execution
  facts can be judged is `INVALID`, not partial success.
- Stop-loss: Stop immediately on the first session bootstrap failure, non-zero
  Agent process, permission denial, typed provider failure, failed correction,
  execution-fact/projection contradiction, second correction in one Agent run,
  second semantic revision in one stage, more than 15 Agent runs, more than 35
  minutes elapsed, out-of-scope change, prohibited medical behavior, missing
  usage or target contamination. Do not retry, resume, tune
  prompts/models/timeouts/budgets or launch a second T6 sample.
- Run artifact freshness: `run-real-case.sh` must perform a Maven package from
  the clean authorized gate-only SHA after the latest JVM source mtime.
  Generated `preflight.json` must report `buildMode=maven_package`,
  `artifactFreshnessProven=true`, matching expected/provider-probe runner kind
  and `providerProbePathMatchesExpected=true`; otherwise the run is `INVALID`.

This gate consumes exactly one T6 authorization. Deterministic failure-path
proof remains authoritative for branches that a successful provider run does
not trigger. This run proves composition with the full workflow and does not
authorize retry, resume or another sample.
