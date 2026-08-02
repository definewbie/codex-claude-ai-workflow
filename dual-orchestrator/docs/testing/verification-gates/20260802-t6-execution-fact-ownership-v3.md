# Verification Gate: T6 ACP Execution Fact Ownership v3

Status: `AUTHORIZED_ONCE / NOT YET CONSUMED`

- Target: Prove the clean ACP execution-fact ownership checkpoint completes one
  full pinned cancer-clinic workflow without any contradiction between the
  transport-owned process result and adapter signals, the resolved execution
  outcome, correction disposition, terminal run metadata, and workflow state.
- Source of truth: The clean Dual Git SHA containing checkpoint `909317a2`;
  generated `preflight.json`; `workflow/current.json`; every
  `runs/*/run.json`, especially per-occurrence `processResult`,
  `activeAdapterSignals`, `executionAttempt`, `executionOutcome`, and
  `agentResultAcquisition.correctionDisposition`; strict result sidecars;
  `task-contract.json`; `change-scope-evaluation.json`; acceptance contract;
  target Git status/diff; backend pytest and miniprogram build logs; and
  `metrics/workstream-metrics.json`.
- Trigger precondition: Dual HEAD is clean, contains `909317a2`, and includes
  only this gate/documentation after that implementation checkpoint; the
  deterministic execution-fact matrix is `122/0`, source architecture gate is
  `3/0`, and full reactor is `1130/0`; pinned target HEAD is
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927` and clean; the Python 3.13.1
  ephemeral environment contains the pinned requirements plus
  `python-multipart==0.0.32`; `cancer_clinic_test` PostgreSQL is healthy;
  backend baseline is `191 passed`; miniprogram build passes; a fresh Dual
  package is built; minimal Claude and Codex probes both pass; and the user's
  one-run provider authorization is recorded.
- How precondition is checked before run: Record Dual HEAD/cleanliness and
  target HEAD/cleanliness; run the focused deterministic matrix and source
  architecture gate; run the full reactor or verify the just-completed
  `1130/0` result against the same implementation SHA; record requirements
  hash and `pip freeze`; query the test database identity/health; run all 191
  backend tests and the miniprogram build; execute one minimal no-tool probe
  through Claude and Codex; then invoke `run-real-case.sh` without
  `--skip-build` and require its preflight to prove artifact freshness, expected
  runner kind, and provider-probe path match before workstream creation.
- PASS: Workflow reaches `COMPLETED`; final acceptance is `ACCEPT` or a
  non-regressive `ACCEPT_WITH_NOTES`; strict result, lineage, seven-file change
  scope, backend pytest, miniprogram build, business-safety, and usage
  reconciliation checks pass; every Agent occurrence has one transport-owned
  process result and typed adapter-signal set; terminal execution outcome and
  workflow status agree with those facts; no successful outcome coexists with
  an active non-superseded blocking signal; correction disposition agrees with
  process/provider/completion evidence; semantic revisions are at most one per
  stage; bounded correction is at most one per Agent run; Agent runs are at
  most 15 and elapsed workflow time is at most 35 minutes.
- FAIL: Any process/signal/outcome/terminal metadata contradiction; non-zero
  Agent process; permission denial; typed provider failure; failed correction;
  correction of a non-zero or provider-failed occurrence; more than one
  correction in an Agent run; more than one semantic revision in a stage; or
  failure of workflow completion, acceptance, strict contracts, lineage,
  seven-file change scope, business behavior, target tests/build, usage
  reconciliation, Agent-run cap, or elapsed-time cap.
- INVALID: Dual or target SHA/cleanliness, dependency pins, database identity,
  baseline tests, artifact freshness, provider/model/runner identity, either
  minimal probe, usage source, or generated ownership fields are absent or not
  comparable. Provider/configuration failure before Dual-owned execution facts
  can be judged is `INVALID`, not partial success.
- Stop-loss: Stop immediately on the first non-zero Agent process, permission
  denial, typed provider failure, failed correction, execution-fact
  contradiction, second correction in one Agent run, second semantic revision
  in one stage, more than 15 Agent runs, more than 35 minutes elapsed,
  out-of-scope change, prohibited medical behavior, missing usage, or target
  contamination. Do not retry, resume, tune prompts/models/timeouts/budgets, or
  launch a second T6 sample.
- Run artifact freshness: `run-real-case.sh` must perform a Maven package from
  the clean authorized SHA after the latest JVM source mtime. Generated
  `preflight.json` must report `buildMode=maven_package`,
  `artifactFreshnessProven=true`, matching expected/provider-probe runner kind,
  and `providerProbePathMatchesExpected=true`; otherwise the run is `INVALID`.

This gate consumes exactly one T6 authorization. Deterministic failure-path
proof remains authoritative for branches that a successful provider run does
not trigger; this real-case proves their composition with the full workflow,
not every synthetic failure branch again.
