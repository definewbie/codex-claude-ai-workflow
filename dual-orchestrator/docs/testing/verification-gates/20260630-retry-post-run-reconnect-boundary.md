# Verification Gate: retry post-run reconnect boundary

- Target: Retry the existing failed public benchmark node `intake.codex_product_review` and verify that a run with complete terminal control plus required `agent_output_contract` is not failed solely because stderr contains `agent needs reconnect`.
- Source of truth: The retried node run.json `executionAttempt`, `executionOutcome`, `adapterSignals`, `artifactContractResults`, workflow current state, and recovery/current-failure.md under workstream `ws-20260630T144326Z-WUDW`.
- Trigger precondition: The workstream is currently `WAITING_FOR_RECOVERY` at `intake.codex_product_review` with prior failure reason `AGENT_RECONNECT_REQUIRED`; the dual-cli jar has been rebuilt after commit `468b08968`.
- How precondition is checked before run: `mvn -pl dual-cli -am package` completed successfully in this turn; prior `current-failure.md` and run.json showed active node `intake.codex_product_review`, complete artifact contract, and reconnect failure.
- PASS: The retried node either completes and workflow advances past `intake.codex_product_review`, or if stderr again contains `agent needs reconnect` while terminal/artifact contract is complete, run.json does not set `executionOutcome.status=RECOVERABLE_ADAPTER_FAILURE` solely from that reconnect warning.
- FAIL: A retried run with complete terminal control and valid required artifact is again routed to `WAITING_FOR_RECOVERY` only because of `AGENT_RECONNECT_REQUIRED`.
- INVALID: Provider/preflight/session fails before producing a retried run, the active node is no longer `intake.codex_product_review` before retry, the jar is stale, or no new run is created.
- Stop-loss: Run only one retry of the active node. Do not launch a second benchmark or complex case in this verification.
- Run artifact freshness: `mvn -pl dual-cli -am package` succeeded immediately before retry; use `dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar` from this build.

## Result

- Verdict: PASS for workflow advancement; NOT a full real-case reproduction of the post-run reconnect stderr branch.
- Retried run: `run-1782832521162`.
- Effective workdir: `/private/tmp/dual-public-issue-flow-20260630T144133Z-agent-runs/ws-20260630T144326Z-WUDW`.
- Workflow state after retry: `RUNNING`, active node `intake.decision`.
- Run evidence:
  - `executionAttempt.status=COMPLETED`
  - `executionAttempt.failureSignal=null`
  - `executionOutcome.status=SUCCESS`
  - `executionOutcome.secondarySignals=[]`
  - `adapterSignals=[]`
  - `artifactContractResults[0].contractType=agent_output_contract`
  - `artifactContractResults[0].valid=true`
  - `explorationPolicyResult.status=pass`
  - stderr contained `agent connected`, not `agent needs reconnect`
- Contract evidence:
  - `artifact_type=agent_output_contract`
  - `decision=READY_FOR_PRODUCT_REVIEW`
  - `blocking=false`
  - `missing_evidence=[]`
  - `must_fix=[]`
  - `open_risks=[]`

Interpretation:

- This run proves that the existing failed workstream can advance past `intake.codex_product_review` after the fix and fresh jar.
- This run does not independently prove the exact stderr path where a new run contains `agent needs reconnect` after complete typed output, because the retried run did not emit that reconnect warning.
- The exact reconnect suppression branch remains covered by unit tests and by the prior failed run's root-cause evidence, not by this retry's stderr.
