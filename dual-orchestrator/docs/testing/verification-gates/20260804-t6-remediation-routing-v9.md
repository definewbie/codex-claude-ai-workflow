# Verification Gate: T6 Remediation Routing v9

Status: `AUTHORIZED`

- Target: Run one new full cancer-clinic workflow with the current remediation-routing
  change. The primary result is a terminal workflow with a final `ACCEPT` or
  `ACCEPT_WITH_NOTES`, valid strict sidecars, and a PASS change-scope evaluation.
  The conditional result is checked only if acceptance emits
  `verification_remediation=TEST_CODE_CHANGE`.
- Source of truth: Fresh `preflight.json`; `events.jsonl`; the validated
  acceptance sidecar; `failure-classification.json`; node/instance state; the
  persisted implementation context package and prompt; final acceptance sidecar;
  change-scope evaluation; and `run.json` occurrence facts.
- Trigger precondition: The target repository is clean; task hash and expected
  runner kind are recorded; the current Dual jar is built after the changed JVM
  sources; the local full reactor has passed; and the Claude acpx probe passes.
  A `TEST_CODE_CHANGE` review cannot be predicted before a model run, so it is
  a conditional observation, not a promised trigger.
- How precondition is checked before run: Package `dual-cli` and `dual-server`
  from this worktree without rerunning tests; require artifact freshness in
  `preflight.json`; then use the real-case script's Claude probe on
  `acpx_claude_session` before it creates a workstream.
- PASS: Primary PASS requires `COMPLETED`, final `ACCEPT` or
  `ACCEPT_WITH_NOTES`, valid strict sidecars/lineage, terminal execution facts,
  and PASS change scope. If the conditional typed remediation appears, its
  classification must be `IMPLEMENTATION_BUG`, its workflow edge must enter
  `context.implement_claude`, and the implementation context must retain the
  validated acceptance sidecar and its Must Fix.
- FAIL: Any malformed/invalid sidecar, failed lineage or change scope,
  non-terminal execution, provider failure, permission denial, workflow stop,
  or a `TEST_CODE_CHANGE` classification routed to self-test/evidence collection.
- INVALID: Missing fresh artifact proof, failed probe, dirty target repository,
  wrong runner kind, or absent required ledger artifacts. If no
  `TEST_CODE_CHANGE` occurs, the primary workflow conclusion remains valid but
  the conditional routing conclusion is `NOT_TRIGGERED`, not proven.
- Stop-loss: Stop at the first primary FAIL or conditional misroute. Do not
  resume, retry, tune prompts, change parser behavior, or launch another sample.
- Run artifact freshness: Build the current CLI/server jar after the latest JVM
  source timestamp. `preflight.json` must report a fresh jar and a passed
  `acpx_claude_session` probe; otherwise do not create the workstream.
