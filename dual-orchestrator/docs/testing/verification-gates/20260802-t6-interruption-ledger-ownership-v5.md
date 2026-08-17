# Verification Gate: T6 Interruption Ledger Ownership v5

Status: `CONSUMED / FAIL / STOP-LOSS ENFORCED`

## Result

- Executed from clean Dual SHA `c2340654e2322f3d3cd335a49e001de44fc09251`
  against pinned target SHA `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`.
- Workstream: `ws-20260802T152626Z-QN2R`.
- Report: `docs/testing/real-case-reports/20260802T152244Z/report.md`.
- Fresh Maven package, provider probe, pinned PostgreSQL/backend baseline, and
  miniprogram build preconditions passed. The run created seven Agent runs and
  stopped at `plan.claude_plan` in `WAITING_FOR_RECOVERY`.
- The execution-ledger ownership target held for all seven runs: no run remained
  `prepared`; every occurrence had its observed process facts; terminal status,
  attempt, process result, and outcome did not contradict; no occurrence, usage,
  candidate, or correction was fabricated by reconciliation.
- The full gate failed because `plan.claude_plan` produced malformed JSON in its
  first occurrence and again in its single bounded correction. Both exit codes
  were zero, both outputs ended one root-closing brace early, and strict parsing
  correctly returned `AGENT_RESULT_PARSE_FAILED`. The workflow stopped without
  retry or resume.
- Actual Claude provider metadata was `zhipu / glm-5.2`; the current Claude Code
  UI default did not change the existing acpx workstream session provider.

No second v5 sample is authorized. This result proves the new execution-fact
ownership behavior on the observed normal and contract-failure paths, but the
checkpoint cannot be promoted as a complete full-workflow `PROVEN` result.

## Evidence Correction (2026-08-17)

The original result text above classified both failed outputs as ending one
root-closing brace early. A later independent reconstruction of the surviving
authoritative ACP wire streams refuted that morphology: the retained messages
include fenced or prefixed presentation, balanced multi-document content, and
depth-2 truncation. None is recoverable by appending one root-closing brace.
The original workstream ledger has been deleted, so a full parser/validator/
merger replay is `NOT VERIFIABLE`. The gate's `FAIL / CONSUMED` verdict and its
execution-ledger conclusions remain unchanged; only the historical parse-shape
attribution is corrected.

- Target: Prove clean Dual checkpoint `0c81fbc8` composes with one full pinned
  cancer-clinic workflow without losing or fabricating execution facts across
  normal completion, bounded correction, provider failure, or an externally
  interrupted in-flight run. The workflow quality result is reported
  independently; this gate's unique target is ACP execution-ledger ownership.
- Source of truth: Clean Dual Git SHA containing `0c81fbc8`; generated
  `preflight.json`; every `runs/*/run.json` including top-level status,
  `executionAttempt`, `executionTimeline`, `processResult`,
  `agentResultAcquisition`, usage and `executionOutcome`; workflow current
  state; strict result sidecars; target Git status/diff; backend pytest and
  miniprogram build logs; and the generated real-case report/summary.
- Trigger precondition: The gate and active-plan update are committed on a clean
  Dual SHA containing `0c81fbc8`; full reactor is `1145/0`; deterministic
  interruption tests are `14/0`, handler tests `39/0`, and transport tests
  `27/0`; target worktree is created from clean pinned SHA
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`; pinned Python environment,
  PostgreSQL `cancer_clinic_test`, backend baseline `191 passed`, and
  miniprogram build pass; a fresh Dual package is built; minimal Claude and
  Codex probes pass through the expected acpx runner paths; and this one-run
  authorization is recorded.
- How precondition is checked before run: Commit this v5 gate and active-plan
  update; verify both Git SHAs and cleanliness; create an isolated target
  worktree from the pinned target SHA rather than touching the user's dirty
  repository; run the deterministic ownership tests and full reactor; verify
  dependency hash/environment and database identity; run backend pytest and
  miniprogram build; run one minimal no-tool probe through Claude and Codex;
  then invoke `run-real-case.sh` without `--skip-build`. Its preflight must prove
  artifact freshness and expected provider-probe runner kind before workstream
  creation.
- PASS: Every launched process has a durable non-prepared execution state;
  every completed Agent run has exactly the process/lifecycle facts actually
  observed, one typed terminal outcome, and no fabricated acquisition, usage,
  candidate or correction; prompt lifecycle alone never becomes a parsed
  result occurrence; durable process exit wins over dead-PID inference; top
  run status, typed attempt and outcome do not contradict; event persistence
  failure cannot leave the child process alive. The full workflow reaches
  `COMPLETED` with final `ACCEPT` or non-regressive `ACCEPT_WITH_NOTES`, strict
  contracts/lineage/change scope pass, backend and miniprogram verification
  pass, semantic revisions are at most one per stage, bounded correction is at
  most one per Agent run, Agent runs are at most 15, and elapsed time is at most
  35 minutes.
- FAIL: Any provider execution proven by Dual remains `prepared`; a durable
  process/lifecycle fact is lost; dead PID overrides a durable exit event;
  reconciler creates acquisition/occurrence/candidate/usage/correction not
  present in durable evidence; status/attempt/outcome contradict; child process
  survives event persistence failure; any non-zero Agent process, permission
  denial, typed provider failure, failed correction, second correction in one
  Agent run, second semantic revision in one stage, workflow/acceptance/strict
  contract/lineage/change-scope/target verification failure, more than 15 Agent
  runs, or more than 35 minutes elapsed.
- INVALID: Dual or target SHA/cleanliness, dependency pins, database identity,
  baseline tests, artifact freshness, provider/model/runner identity, either
  minimal probe, required ownership fields, or captured report freshness are
  absent or not comparable. A provider/configuration failure before Dual-owned
  execution facts can be judged is `INVALID`, not partial success.
- Stop-loss: Stop immediately on the first execution-fact contradiction,
  provider execution left at `prepared`, fabricated provider fact, non-zero
  Agent process, permission denial, typed provider failure, failed correction,
  second correction in one Agent run, second semantic revision in one stage,
  more than 15 Agent runs, more than 35 minutes, target contamination,
  out-of-scope change or prohibited medical behavior. Do not retry, resume,
  tune prompts/models/timeouts/budgets or launch a second v5 sample.
- Run artifact freshness: `run-real-case.sh` must Maven-package from the clean
  authorized SHA after the latest JVM source mtime. Generated `preflight.json`
  must report `buildMode=maven_package`, `artifactFreshnessProven=true`, matching
  expected/provider-probe runner kind and
  `providerProbePathMatchesExpected=true`; otherwise the run is `INVALID`.

This gate consumes exactly one authorization. A successful uninterrupted run
proves composition of the new durable ownership path. Hard-kill branches remain
deterministically proven because no killed process can write after SIGKILL; if
an actual interruption occurs, the captured last-known state and one existing
recovery reconciliation may be inspected, but this gate does not authorize a
workflow retry or second provider sample.
